package com.xm.thefourthfrequency.pursuit;

import com.xm.thefourthfrequency.content.TerminalData;
import com.xm.thefourthfrequency.world.FrequencyWorldData;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.LinkedHashMap;
import java.util.Map;

/** Converts temporary placements into a single refund transaction and retries inventory overflow later. */
public final class PursuitRecoveryLedger {
	private static final String ITEM = "item";
	private static final String COUNT = "count";

	private PursuitRecoveryLedger() {
	}

	public static void recordPlacement(ServerPlayer player, ItemStack consumed) {
		if (consumed.isEmpty() || player.getAbilities().instabuild) return;
		FrequencyWorldData data = FrequencyWorldData.get(player.level().getServer());
		if (data.terminalRecord(player.getUUID()).isEmpty()) return;
		String itemId = BuiltInRegistries.ITEM.getKey(consumed.getItem()).toString();
		data.updateTerminalRecord(player.getUUID(), record -> {
			ListTag ledger = record.getListOrEmpty(TerminalData.PURSUIT_REFUND_LEDGER);
			addCount(ledger, itemId, consumed.getCount());
			record.put(TerminalData.PURSUIT_REFUND_LEDGER, ledger);
		});
	}

	public static void settleAndDeliver(ServerPlayer player) {
		FrequencyWorldData data = FrequencyWorldData.get(player.level().getServer());
		if (data.terminalRecord(player.getUUID()).isEmpty()) return;
		Map<String, Integer> delivery = new LinkedHashMap<>();
		data.updateTerminalRecord(player.getUUID(), record -> {
			merge(delivery, record.getListOrEmpty(TerminalData.PURSUIT_RECOVERY_QUEUE));
			merge(delivery, record.getListOrEmpty(TerminalData.PURSUIT_REFUND_LEDGER));
			record.put(TerminalData.PURSUIT_REFUND_LEDGER, new ListTag());
			record.put(TerminalData.PURSUIT_RECOVERY_QUEUE, new ListTag());
		});

		Map<String, Integer> overflow = new LinkedHashMap<>();
		for (var entry : delivery.entrySet()) {
			Item item = resolve(entry.getKey());
			if (item == null || item == Items.AIR || entry.getValue() <= 0) continue;
			ItemStack stack = new ItemStack(item, entry.getValue());
			player.getInventory().add(stack);
			if (!stack.isEmpty()) overflow.put(entry.getKey(), stack.getCount());
		}
		if (!overflow.isEmpty()) data.updateTerminalRecord(player.getUUID(), record -> {
			ListTag queue = new ListTag();
			overflow.forEach((itemId, count) -> addCount(queue, itemId, count));
			record.put(TerminalData.PURSUIT_RECOVERY_QUEUE, queue);
		});
	}

	private static void merge(Map<String, Integer> result, ListTag list) {
		for (int index = 0; index < list.size(); index++) {
			CompoundTag entry = list.getCompoundOrEmpty(index);
			String id = entry.getStringOr(ITEM, "");
			int count = Math.max(0, entry.getIntOr(COUNT, 0));
			if (!id.isBlank() && count > 0) result.merge(id, count, Integer::sum);
		}
	}

	private static void addCount(ListTag list, String itemId, int amount) {
		if (amount <= 0) return;
		for (int index = 0; index < list.size(); index++) {
			CompoundTag entry = list.getCompoundOrEmpty(index);
			if (!itemId.equals(entry.getStringOr(ITEM, ""))) continue;
			entry.putInt(COUNT, entry.getIntOr(COUNT, 0) + amount);
			list.set(index, entry);
			return;
		}
		CompoundTag entry = new CompoundTag();
		entry.putString(ITEM, itemId);
		entry.putInt(COUNT, amount);
		list.add(entry);
	}

	private static Item resolve(String itemId) {
		try {
			return BuiltInRegistries.ITEM.getValue(Identifier.parse(itemId));
		} catch (RuntimeException exception) {
			return Items.AIR;
		}
	}
}
