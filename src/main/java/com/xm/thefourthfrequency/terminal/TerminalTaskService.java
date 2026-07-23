package com.xm.thefourthfrequency.terminal;

import com.xm.thefourthfrequency.content.TerminalData;
import com.xm.thefourthfrequency.world.FrequencyWorldData;
import com.xm.thefourthfrequency.world.SurvivalMilestone;
import com.xm.thefourthfrequency.world.SurvivalProgressService;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;

/** Server-authoritative task order, completion checks, rewards, and claim transaction. */
public final class TerminalTaskService {
	public static final int PAGE_COUNT = 4;
	public static final int ALL_PAGES_MASK = (1 << PAGE_COUNT) - 1;

	private static final List<TaskDefinition> TASKS = List.of(
			new TaskDefinition("learn_terminal", PAGE_COUNT, Items.BREAD, 6),
			new TaskDefinition("mine_logs", SurvivalProgressService.REQUIRED_WOOD, Items.STONE_AXE, 1),
			new TaskDefinition("bring_iron", SurvivalProgressService.REQUIRED_IRON, Items.TORCH, 24),
			new TaskDefinition("enter_nether", 1, Items.COOKED_BEEF, 8),
			new TaskDefinition("collect_blaze_rods", SurvivalProgressService.REQUIRED_BLAZE_RODS,
					Items.GOLDEN_CARROT, 8),
			new TaskDefinition("return_from_nether", 1, Items.ENDER_PEARL, 4),
			new TaskDefinition("craft_eye", SurvivalProgressService.REQUIRED_CRAFTED_EYES, Items.ENDER_PEARL, 2),
			new TaskDefinition("record_eye", SurvivalProgressService.REQUIRED_EYE_SAMPLES, Items.COOKED_BEEF, 8),
			new TaskDefinition("find_stronghold", 1, Items.GOLDEN_APPLE, 2),
			new TaskDefinition("enter_end", 1, Items.ARROW, 32),
			new TaskDefinition("defeat_boss", 1, Items.DIAMOND, 4));

	private TerminalTaskService() {
	}

	public static TaskSnapshot current(CompoundTag tag) {
		int claimed = tag.getIntOr(TerminalData.TASK_REWARD_CLAIMED_MASK, 0);
		for (int index = 0; index < TASKS.size(); index++) {
			if ((claimed & 1 << index) != 0) continue;
			TaskDefinition definition = TASKS.get(index);
			int progress = Math.clamp(progress(definition.id(), tag), 0, definition.target());
			return new TaskSnapshot(index, definition.id(), progress, definition.target(),
					BuiltInRegistries.ITEM.getKey(definition.reward()).toString(),
					definition.rewardCount(), progress >= definition.target());
		}
		return new TaskSnapshot(TASKS.size(), "complete", 1, 1, "minecraft:air", 0, false);
	}

	public static boolean hasClaimableReward(CompoundTag tag) {
		return current(tag).claimable();
	}

	public static int consumeCompletionAlert(CompoundTag tag) {
		TaskSnapshot task = current(tag);
		int mask = tag.getIntOr(TerminalData.TASK_COMPLETION_NOTIFIED_MASK, 0);
		int completed = TerminalAttentionPolicy.completionToNotify(
				task.index(), task.claimable(), TASKS.size(), mask);
		if (completed < 0) return -1;
		tag.putInt(TerminalData.TASK_COMPLETION_NOTIFIED_MASK,
				TerminalAttentionPolicy.markCompletionNotified(mask, completed));
		return completed;
	}

	public static boolean notifyIfCompleted(ServerPlayer player) {
		FrequencyWorldData data = FrequencyWorldData.get(player.level().getServer());
		if (data.terminalRecord(player.getUUID()).isEmpty()) return false;
		int[] completed = {-1};
		data.updateTerminalRecord(player.getUUID(), tag -> completed[0] = consumeCompletionAlert(tag));
		if (completed[0] < 0) return false;
		TerminalRuntimeService.synchronizeAttentionProjection(player, data);
		TerminalNoticeService.taskComplete(player);
		TerminalRuntimeService.refresh(player);
		return true;
	}

	public static boolean visitPage(ServerPlayer player, int pageIndex) {
		if (pageIndex < 0 || pageIndex >= PAGE_COUNT) return false;
		FrequencyWorldData data = FrequencyWorldData.get(player.level().getServer());
		CompoundTag before = data.terminalRecord(player.getUUID()).orElse(null);
		if (before == null) return false;
		int oldMask = before.getIntOr(TerminalData.TERMINAL_PAGE_VISIT_MASK, 0) & ALL_PAGES_MASK;
		int newMask = oldMask | 1 << pageIndex;
		if (newMask == oldMask) return true;
		boolean attentionBefore = hasClaimableReward(before);
		data.updateTerminalRecord(player.getUUID(), tag ->
				tag.putInt(TerminalData.TERMINAL_PAGE_VISIT_MASK, newMask));
		CompoundTag after = data.terminalRecord(player.getUUID()).orElse(before);
		if (attentionBefore != hasClaimableReward(after)) {
			TerminalRuntimeService.synchronizeAttentionProjection(player, data);
		}
		notifyIfCompleted(player);
		return true;
	}

	public static ClaimResult claim(ServerPlayer player, int expectedTaskIndex) {
		FrequencyWorldData data = FrequencyWorldData.get(player.level().getServer());
		CompoundTag record = data.terminalRecord(player.getUUID()).orElse(null);
		if (record == null) return ClaimResult.INVALID;
		TaskSnapshot task = current(record);
		if (task.index() != expectedTaskIndex) return ClaimResult.STALE;
		if (!task.claimable() || task.rewardCount() <= 0 || task.index() >= TASKS.size()) {
			return ClaimResult.NOT_READY;
		}
		ItemStack reward = rewardStack(task.index());
		if (!canFit(player, reward)) return ClaimResult.INVENTORY_FULL;
		Component rewardName = reward.getHoverName();
		int rewardCount = reward.getCount();
		if (!player.getInventory().add(reward) || !reward.isEmpty()) return ClaimResult.INVENTORY_FULL;
		data.updateTerminalRecord(player.getUUID(), tag -> tag.putInt(TerminalData.TASK_REWARD_CLAIMED_MASK,
				tag.getIntOr(TerminalData.TASK_REWARD_CLAIMED_MASK, 0) | 1 << task.index()));
		TerminalRuntimeService.synchronizeProjection(player, data);
		TerminalNoticeService.send(player, Component.translatable(
				"message.thefourthfrequency.task.reward_claimed", rewardName, rewardCount));
		notifyIfCompleted(player);
		return ClaimResult.CLAIMED;
	}

	public static int taskCount() {
		return TASKS.size();
	}

	public static ItemStack rewardStack(int taskIndex) {
		if (taskIndex < 0 || taskIndex >= TASKS.size()) return ItemStack.EMPTY;
		TaskDefinition task = TASKS.get(taskIndex);
		return new ItemStack(task.reward(), task.rewardCount());
	}

	private static int progress(String id, CompoundTag tag) {
		int milestones = tag.getIntOr(TerminalData.SURVIVAL_MILESTONE_MASK, 0);
		return switch (id) {
			case "learn_terminal" -> Integer.bitCount(
					tag.getIntOr(TerminalData.TERMINAL_PAGE_VISIT_MASK, 0) & ALL_PAGES_MASK);
			case "mine_logs" -> tag.getIntOr(TerminalData.WOOD_MINED_COUNT, 0);
			case "bring_iron" -> tag.getIntOr(TerminalData.IRON_SAMPLE_COUNT, 0);
			case "enter_nether" -> completed(milestones, SurvivalMilestone.ENTERED_NETHER);
			case "collect_blaze_rods" -> tag.getIntOr(TerminalData.BLAZE_ROD_SAMPLE_COUNT, 0);
			case "return_from_nether" -> completed(milestones, SurvivalMilestone.RETURNED_NETHER);
			case "craft_eye" -> tag.getIntOr(TerminalData.CRAFTED_EYE_COUNT, 0);
			case "record_eye" -> tag.getIntOr(TerminalData.EYE_SAMPLE_COUNT, 0);
			case "find_stronghold" -> completed(milestones, SurvivalMilestone.FOUND_STRONGHOLD);
			case "enter_end" -> completed(milestones, SurvivalMilestone.ENTERED_END);
			case "defeat_boss" -> completed(milestones, SurvivalMilestone.DEFEATED_BOSS);
			default -> 0;
		};
	}

	private static int completed(int milestones, SurvivalMilestone milestone) {
		return milestone.present(milestones) ? 1 : 0;
	}

	private static boolean canFit(ServerPlayer player, ItemStack reward) {
		int capacity = player.getInventory().getFreeSlot() >= 0 ? reward.getMaxStackSize() : 0;
		for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
			ItemStack current = player.getInventory().getItem(slot);
			if (ItemStack.isSameItemSameComponents(current, reward)) {
				capacity += Math.max(0, current.getMaxStackSize() - current.getCount());
			}
		}
		return capacity >= reward.getCount();
	}

	private record TaskDefinition(String id, int target, Item reward, int rewardCount) {
	}

	public record TaskSnapshot(
			int index,
			String id,
			int progress,
			int target,
			String rewardItemId,
			int rewardCount,
			boolean claimable
	) {
		public double fraction() {
			return target <= 0 ? 0.0D : Math.clamp(progress / (double) target, 0.0D, 1.0D);
		}
	}

	public enum ClaimResult {
		CLAIMED,
		NOT_READY,
		INVENTORY_FULL,
		STALE,
		INVALID
	}
}
