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

/** Server-authoritative task order, completion checks, and reward delivery. */
public final class TerminalTaskService {
	public static final int PAGE_COUNT = 4;
	public static final int ALL_PAGES_MASK = (1 << PAGE_COUNT) - 1;

	private static final List<TaskDefinition> TASKS = List.of(
			new TaskDefinition("learn_terminal", PAGE_COUNT, Items.BREAD, 6),
			new TaskDefinition("mine_logs", SurvivalProgressService.REQUIRED_WOOD, Items.STONE_AXE, 1),
			new TaskDefinition("bring_iron", SurvivalProgressService.REQUIRED_IRON, Items.TORCH, 24),
			new TaskDefinition("enter_nether", 1, Items.COOKED_BEEF, 8),
			// Between arriving and the rods, because the rods are inside the thing this one asks for.
			// A player told to collect blaze rods with nothing in between has to already know that
			// blazes only spawn in fortresses; the terminal is supposed to be the reason they know it.
			new TaskDefinition("find_fortress", 1, Items.GOLDEN_CARROT, 4),
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

	/**
	 * Delivers every reward the player has completed but not yet been given, newest task last.
	 *
	 * <p>This is the only path that hands out a task reward. It used to share the job with a manual
	 * "claim" button on the home card, and the two could not agree on when a reward was owed: rewards
	 * sat undelivered until the player happened to open a page or trip a signal event, so pressing
	 * the card was sometimes the only way to get them and sometimes did nothing at all. Completion
	 * and its reward are one moment - so the trigger belongs wherever progress changes, which is what
	 * {@code SurvivalProgressService} now calls.</p>
	 *
	 * <p>The loop is for catching up (an old save, or several milestones crossed in one tick), not for
	 * chaining off a player action.</p>
	 */
	public static boolean notifyIfCompleted(ServerPlayer player) {
		FrequencyWorldData data = FrequencyWorldData.get(player.level().getServer());
		if (data.terminalRecord(player.getUUID()).isEmpty()) return false;
		boolean delivered = false;
		while (true) {
			CompoundTag record = data.terminalRecord(player.getUUID()).orElse(null);
			if (record == null) break;
			TaskSnapshot task = current(record);
			if (!task.claimable() || task.rewardCount() <= 0 || task.index() >= TASKS.size()) break;
			ItemStack reward = rewardStack(task.index());
			Component rewardName = reward.getHoverName();
			int rewardCount = reward.getCount();
			deliverReward(player, reward);
			int[] completed = {-1};
			data.updateTerminalRecord(player.getUUID(), tag -> {
				int taskBit = 1 << task.index();
				completed[0] = consumeCompletionAlert(tag);
				tag.putInt(TerminalData.TASK_REWARD_CLAIMED_MASK,
						tag.getIntOr(TerminalData.TASK_REWARD_CLAIMED_MASK, 0) | taskBit);
			});
			TerminalNoticeService.rewardClaimed(player, taskName(task), rewardName,
					rewardCount, completed[0] >= 0);
			delivered = true;
		}
		if (!delivered) return false;
		TerminalRuntimeService.synchronizeProjection(player, data);
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
		data.updateTerminalRecord(player.getUUID(), tag -> {
			tag.putInt(TerminalData.TERMINAL_PAGE_VISIT_MASK, newMask);
			latchOnboarding(tag, newMask);
		});
		CompoundTag after = data.terminalRecord(player.getUUID()).orElse(before);
		if (attentionBefore != hasClaimableReward(after)) {
			TerminalRuntimeService.synchronizeAttentionProjection(player, data);
		}
		notifyIfCompleted(player);
		return true;
	}

	/**
	 * Closes the first-boot walkthrough the moment all four tabs have been visited.
	 *
	 * <p>The walkthrough and {@code learn_terminal} finish at the same instant by construction: it
	 * has no completion signal of its own, it simply walks the player through the four visits the
	 * task already counts. So the client never reports "I finished" - it only ever sends the same
	 * page visits a player clicking the tabs themselves would send, and this latch closes behind
	 * them on the server.</p>
	 *
	 * <p>Static and tag-level so {@code TerminalTaskServiceTest} can pin it without a server.</p>
	 */
	static void latchOnboarding(CompoundTag tag, int visitMask) {
		if ((visitMask & ALL_PAGES_MASK) == ALL_PAGES_MASK) {
			tag.putBoolean(TerminalData.ONBOARDING_DONE, true);
		}
	}

	/**
	 * Compatibility entry for the {@code CLAIM_TASK_REWARD} packet, which current clients no longer
	 * send. Rewards are delivered the moment a task completes, so by the time any claim arrives the
	 * task it names is already paid for and the honest answer is {@link ClaimResult#STALE}.
	 *
	 * <p>It deliberately delivers nothing. When it did, a single press paid out the pressed task and
	 * then ran the catch-up loop, dumping every other finished task's reward at once and stacking a
	 * notice for each - and an older client that retried the packet could make that happen twice.</p>
	 */
	public static ClaimResult claim(ServerPlayer player, int expectedTaskIndex) {
		FrequencyWorldData data = FrequencyWorldData.get(player.level().getServer());
		if (data.terminalRecord(player.getUUID()).isEmpty()) return ClaimResult.INVALID;
		// Anything still owed is owed because a trigger was missed, not because this packet arrived.
		notifyIfCompleted(player);
		CompoundTag settled = data.terminalRecord(player.getUUID()).orElse(null);
		if (settled == null) return ClaimResult.INVALID;
		// Nothing is claimable once the pass above has run, so the current task is simply unfinished.
		// A different index means the named task was already paid for before this packet arrived.
		return current(settled).index() == expectedTaskIndex ? ClaimResult.NOT_READY : ClaimResult.STALE;
	}

	public static int taskCount() {
		return TASKS.size();
	}

	/** Index {@code find_fortress} was inserted at. Everything from here on shifted up by one. */
	public static final int FORTRESS_TASK_INDEX = 4;

	/**
	 * Rewrites a per-task bit mask written before {@code find_fortress} existed.
	 *
	 * <p>{@code TASK_REWARD_CLAIMED_MASK} and {@code TASK_COMPLETION_NOTIFIED_MASK} store one bit per
	 * task <em>index</em>, so inserting a task in the middle silently re-points every bit above it at
	 * the wrong task. Left alone, a save that had claimed through {@code collect_blaze_rods} would
	 * read as having claimed {@code find_fortress} instead and be asked to collect the rods a second
	 * time - which pays their reward out again, because the rod count that satisfies the task is
	 * still in the record.
	 *
	 * <p>The new bit is filled from the old {@code collect_blaze_rods} bit rather than left clear:
	 * blazes only spawn in fortresses, so a player who claimed the rods has been inside one, and
	 * sending them back to the Nether to look for a building they have already looted would be the
	 * same mistake pointed the other way. A player who had <em>not</em> got that far keeps an unset
	 * bit and simply picks the new objective up where they are.
	 *
	 * <p>Pure and public so {@code TerminalTaskServiceTest} can pin the shift against the real task
	 * list rather than against a copy of these numbers.
	 */
	public static int migrateMaskForFortressInsert(int legacyMask) {
		int low = legacyMask & ((1 << FORTRESS_TASK_INDEX) - 1);
		int high = (legacyMask & ~((1 << FORTRESS_TASK_INDEX) - 1)) << 1;
		boolean claimedRods = (legacyMask & 1 << FORTRESS_TASK_INDEX) != 0;
		return low | high | (claimedRods ? 1 << FORTRESS_TASK_INDEX : 0);
	}

	/**
	 * The objective line as it reads at the instant the task is finished, progress included.
	 *
	 * <p>The key is assembled from the task id, the same way {@code TerminalSnapshot#objectiveLine}
	 * builds the card's own line - the eleven of them are the one family where a per-case switch
	 * would be eleven copies of the same string.</p>
	 */
	public static Component completedObjectiveLine(String id, int target) {
		return Component.translatable("terminal.thefourthfrequency.objective." + id, target, target);
	}

	/**
	 * The task's name on its own - no instruction, no counter.
	 *
	 * <p>It exists for the completion notice, which is what answers "why did I just get this" when
	 * the terminal is closed. A reward arriving unprompted names an item and nothing else, and the
	 * first task pays out while the player is still inside the first-boot walkthrough, so six bread
	 * appeared for what looked from their side like clicking four tabs to dismiss an animation.</p>
	 *
	 * <p>A separate string rather than the objective line, because the objective line carries the
	 * instruction and the progress with it - "认识终端：点击四个顶部标签 4/4" is a fine thing to read
	 * on a card you are already looking at, and far too long for a line that has to fit above the
	 * hotbar next to the item and its count.</p>
	 *
	 * <p>Written out per case rather than assembled from the id, so the contract test that checks
	 * every translation key exists can see them; a key built at runtime would slip past it and could
	 * go missing without anything failing. Falls back to the full objective line for the terminal
	 * task, which has no reward and therefore never reaches a notice.</p>
	 */
	public static Component taskName(TaskSnapshot task) {
		String key = switch (task.id()) {
			case "learn_terminal" -> "terminal.thefourthfrequency.task.name.learn_terminal";
			case "mine_logs" -> "terminal.thefourthfrequency.task.name.mine_logs";
			case "bring_iron" -> "terminal.thefourthfrequency.task.name.bring_iron";
			case "enter_nether" -> "terminal.thefourthfrequency.task.name.enter_nether";
			case "find_fortress" -> "terminal.thefourthfrequency.task.name.find_fortress";
			case "collect_blaze_rods" -> "terminal.thefourthfrequency.task.name.collect_blaze_rods";
			case "return_from_nether" -> "terminal.thefourthfrequency.task.name.return_from_nether";
			case "craft_eye" -> "terminal.thefourthfrequency.task.name.craft_eye";
			case "record_eye" -> "terminal.thefourthfrequency.task.name.record_eye";
			case "find_stronghold" -> "terminal.thefourthfrequency.task.name.find_stronghold";
			case "enter_end" -> "terminal.thefourthfrequency.task.name.enter_end";
			case "defeat_boss" -> "terminal.thefourthfrequency.task.name.defeat_boss";
			default -> null;
		};
		return key == null ? completedObjectiveLine(task.id(), task.target()) : Component.translatable(key);
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
			case "find_fortress" -> completed(milestones, SurvivalMilestone.FOUND_FORTRESS);
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

	private static void deliverReward(ServerPlayer player, ItemStack reward) {
		player.getInventory().add(reward);
		if (!reward.isEmpty()) player.drop(reward, false);
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
