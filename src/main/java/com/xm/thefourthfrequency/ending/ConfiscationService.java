package com.xm.thefourthfrequency.ending;

import com.xm.thefourthfrequency.terminal.TerminalNoticeService;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;

import java.util.UUID;

/**
 * The visible half of weapon custody.
 *
 * <p>Taking a tool used to leave a hole in the hotbar, which is indistinguishable from having
 * dropped it and says nothing about it coming back. The slot now holds a barrier stack that names
 * the state outright. The player may shuffle it around the inventory - that is deliberate, so the
 * placeholder is not a frozen slot - but it cannot be dropped, thrown or used, and the real stack
 * is still held in the encounter's durable recovery ledger rather than in this item.</p>
 */
public final class ConfiscationService {
	/** Present on every placeholder, holding the ledger entry the real stack is waiting under. */
	public static final String MARKER_KEY = "thefourthfrequency_confiscated";
	private static final Component PLACEHOLDER_NAME = Component.translatable(
			"item.thefourthfrequency.confiscated").withStyle(ChatFormatting.RED);
	private static boolean initialized;

	private ConfiscationService() {
	}

	/**
	 * Blocks every use path a barrier stack has. Dropping is refused in {@code PlayerDropMixin},
	 * which is the only path that is not an interaction callback.
	 */
	public static synchronized void initialize() {
		if (initialized) return;
		initialized = true;
		UseBlockCallback.EVENT.register((player, level, hand, hit) ->
				isPlaceholder(player.getItemInHand(hand)) ? refuse(player) : InteractionResult.PASS);
		UseItemCallback.EVENT.register((player, level, hand) ->
				isPlaceholder(player.getItemInHand(hand)) ? refuse(player) : InteractionResult.PASS);
		UseEntityCallback.EVENT.register((player, level, hand, entity, hit) ->
				isPlaceholder(player.getItemInHand(hand)) ? refuse(player) : InteractionResult.PASS);
	}

	private static InteractionResult refuse(Player player) {
		if (player instanceof ServerPlayer serverPlayer) {
			TerminalNoticeService.denied(serverPlayer,
					"message.thefourthfrequency.world_interface.confiscated_locked");
		}
		return InteractionResult.FAIL;
	}

	public static ItemStack placeholder(UUID recoveryId) {
		ItemStack stack = new ItemStack(Items.BARRIER);
		CompoundTag marker = new CompoundTag();
		marker.putString(MARKER_KEY, recoveryId.toString());
		stack.set(DataComponents.CUSTOM_DATA, CustomData.of(marker));
		stack.set(DataComponents.CUSTOM_NAME, PLACEHOLDER_NAME);
		return stack;
	}

	public static boolean isPlaceholder(ItemStack stack) {
		return recoveryId(stack) != null;
	}

	public static UUID recoveryId(ItemStack stack) {
		if (stack == null || stack.isEmpty() || !stack.is(Items.BARRIER)) return null;
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		if (data == null) return null;
		String encoded = data.copyTag().getStringOr(MARKER_KEY, "");
		if (encoded.isBlank()) return null;
		try {
			return UUID.fromString(encoded);
		} catch (IllegalArgumentException ignored) {
			return null;
		}
	}

	/**
	 * Removes the placeholder for one custody and reports the slot it was occupying, so the real
	 * stack can be handed back to wherever the player had moved it rather than to the slot it was
	 * originally taken from.
	 *
	 * @return the freed slot, or -1 if the player was not holding this placeholder
	 */
	public static int clearPlaceholder(ServerPlayer player, UUID recoveryId) {
		for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
			if (!recoveryId.equals(recoveryId(player.getInventory().getItem(slot)))) continue;
			player.getInventory().setItem(slot, ItemStack.EMPTY);
			return slot;
		}
		return -1;
	}

	/** Sweeps every placeholder, for restarts and teardowns where no ledger entry will resolve. */
	public static int clearPlaceholders(ServerPlayer player) {
		int cleared = 0;
		for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
			if (!isPlaceholder(player.getInventory().getItem(slot))) continue;
			player.getInventory().setItem(slot, ItemStack.EMPTY);
			cleared++;
		}
		return cleared;
	}
}
