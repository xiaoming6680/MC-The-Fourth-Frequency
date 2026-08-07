package com.xm.thefourthfrequency.test;

import com.xm.thefourthfrequency.content.TerminalData;
import com.xm.thefourthfrequency.terminal.TerminalSignalLog;
import com.xm.thefourthfrequency.terminal.TerminalTaskService;
import com.xm.thefourthfrequency.terminal.TerminalToolService;
import com.xm.thefourthfrequency.world.FrequencyWorldData;
import com.xm.thefourthfrequency.world.StoryProgressService;
import com.xm.thefourthfrequency.world.SurvivalMilestone;
import com.xm.thefourthfrequency.world.SurvivalProgressService;
import com.xm.thefourthfrequency.world.WorldDecayService;
import net.fabricmc.fabric.api.gametest.v1.CustomTestMethodInvoker;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;

import java.lang.reflect.Method;

public final class SurvivalReworkGameTests implements CustomTestMethodInvoker {
	@GameTest
	public void openingTaskAcceptsMixedWoodFamiliesAndPlanks(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		FrequencyWorldData data = FrequencyWorldData.get(helper.getLevel().getServer());
		player.getInventory().add(new ItemStack(Items.BIRCH_PLANKS, 5));
		player.getInventory().add(new ItemStack(Items.CRIMSON_PLANKS, 7));
		helper.assertValueEqual(SurvivalProgressService.collectedWood(player),
				SurvivalProgressService.REQUIRED_WOOD,
				"The opening task accepts planks from mixed Overworld and Nether wood families");
		SurvivalProgressService.updatePlayer(player, data);
		int milestones = data.terminalRecord(player.getUUID()).orElseThrow()
				.getIntOr(TerminalData.SURVIVAL_MILESTONE_MASK, 0);
		helper.assertTrue(SurvivalMilestone.MINED_LOGS.present(milestones),
				"Any accepted wood material completes the compatibility milestone");
		helper.succeed();
	}

	@GameTest
	public void legacyHomeMilestoneDoesNotAffectProgression(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		FrequencyWorldData data = FrequencyWorldData.get(helper.getLevel().getServer());
		data.updateTerminalRecord(player.getUUID(), record -> {
			record.putInt(TerminalData.SURVIVAL_MILESTONE_MASK, SurvivalMilestone.HOME.mask());
			record.putInt(TerminalData.ANOMALY_TIER, 0);
			record.putBoolean(TerminalData.BOUND, false);
		});

		StoryProgressService.update(player, data);
		var record = data.terminalRecord(player.getUUID()).orElseThrow();
		helper.assertFalse(record.getBooleanOr(TerminalData.BOUND, false),
				"A legacy home bit must not bind the terminal or advance the story");
		helper.assertValueEqual(StoryProgressService.objective(record, data).id(), "mine_logs",
				"A legacy home bit must not skip the opening survival objective");
		helper.assertValueEqual(WorldDecayService.stage(data, record), 0,
				"A legacy home bit must not raise the world-decay stage");
		helper.succeed();
	}

	@GameTest
	public void miningMilestoneAdvancesWithoutAForcedCorrectionScene(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		FrequencyWorldData data = FrequencyWorldData.get(helper.getLevel().getServer());
		player.getInventory().add(new ItemStack(Items.OAK_PLANKS, SurvivalProgressService.REQUIRED_WOOD));
		SurvivalProgressService.updatePlayer(player, data);
		var homeObjective = StoryProgressService.objective(
				data.terminalRecord(player.getUUID()).orElseThrow(), data);
		helper.assertValueEqual(homeObjective.id(), "bring_iron", "Collected wood advances the survival objective");

		player.getInventory().add(new ItemStack(Items.RAW_IRON, SurvivalProgressService.REQUIRED_IRON));
		BlockPos origin = player.blockPosition();
		BlockPos[] candidates = {origin.offset(6, 0, 0), origin.offset(-6, 0, 0),
				origin.offset(0, 0, 6), origin.offset(0, 0, -6), origin.offset(4, 1, 4)};
		for (BlockPos candidate : candidates) {
			helper.getLevel().setBlockAndUpdate(candidate, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
			helper.getLevel().setBlockAndUpdate(candidate.above(), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
		}
		SurvivalProgressService.updatePlayer(player, data);
		var record = data.terminalRecord(player.getUUID()).orElseThrow();
		helper.assertTrue(SurvivalMilestone.IRON.present(
				record.getIntOr(TerminalData.SURVIVAL_MILESTONE_MASK, 0)), "Real carried iron records the mining milestone");
		for (BlockPos candidate : candidates) {
			helper.assertTrue(helper.getLevel().getBlockState(candidate).isAir(),
					"Completing the mining task must not place retired correction blocks");
		}
		helper.assertFalse(TerminalToolService.toolsDisabled(record, player.level().getGameTime()),
				"Completing the mining task must not disable terminal tools");
		helper.assertFalse(TerminalSignalLog.containsType(record, "signature_anomaly")
						|| TerminalSignalLog.containsType(record, "signature_correction")
						|| TerminalSignalLog.containsType(record, "signature_explained"),
				"Completing the mining task must not create signature-scene records");

		StoryProgressService.update(player, data);
		StoryProgressService.update(player, data);
		var advanced = data.terminalRecord(player.getUUID()).orElseThrow();
		helper.assertValueEqual(advanced.getIntOr(TerminalData.BAND_STAGE, 0), 1,
				"Removing the forced scene must not block the fourth-band reveal");
		helper.assertValueEqual(StoryProgressService.objective(advanced, data).id(), "enter_nether",
				"Mining completion still advances the survival objective");
		helper.succeed();
	}

	@GameTest
	public void netherTransitionRecordsOnlyTheObservedMilestone(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		FrequencyWorldData data = FrequencyWorldData.get(helper.getLevel().getServer());
		SurvivalProgressService.recordPortalTransition(player, helper.getLevel(),
				helper.getLevel().getServer().getLevel(Level.NETHER));
		var record = data.terminalRecord(player.getUUID()).orElseThrow();
		int mask = record.getIntOr(TerminalData.SURVIVAL_MILESTONE_MASK, 0);
		helper.assertTrue(SurvivalMilestone.ENTERED_NETHER.present(mask),
				"A real Nether transition records the observed transition");
		helper.assertFalse(SurvivalMilestone.HOME.present(mask) || SurvivalMilestone.IRON.present(mask)
				|| SurvivalMilestone.PREPARED_NETHER.present(mask),
				"Unobserved earlier milestones are never fabricated");
		helper.succeed();
	}

	@GameTest
	public void completedTerminalTaskAutomaticallyDeliversOneReward(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		FrequencyWorldData data = FrequencyWorldData.get(helper.getLevel().getServer());
		data.updateTerminalRecord(player.getUUID(), record ->
				record.putInt(TerminalData.TERMINAL_PAGE_VISIT_MASK, TerminalTaskService.ALL_PAGES_MASK));
		var ready = TerminalTaskService.current(data.terminalRecord(player.getUUID()).orElseThrow());
		helper.assertValueEqual(ready.id(), "learn_terminal", "The first task is learning the four terminal tabs");
		helper.assertTrue(ready.claimable(), "Visiting all four tabs completes the task");

		int breadBefore = player.getInventory().countItem(Items.BREAD);
		helper.assertTrue(TerminalTaskService.notifyIfCompleted(player),
				"Completion automatically advances the task and delivers its reward");
		helper.assertValueEqual(player.getInventory().countItem(Items.BREAD) - breadBefore, 6,
				"The first task grants its displayed reward");
		helper.assertValueEqual(TerminalTaskService.claim(player, ready.index()),
				TerminalTaskService.ClaimResult.STALE, "A repeated packet cannot claim the next task");
		helper.assertValueEqual(player.getInventory().countItem(Items.BREAD) - breadBefore, 6,
				"The repeated claim never duplicates the reward");

		// A claim packet from an older client delivers nothing of its own. While it did, one press
		// paid the named task and then ran the catch-up loop over every other finished task, so a
		// player who had quietly completed several came back to a burst of rewards at once.
		int axeBefore = player.getInventory().countItem(Items.STONE_AXE);
		data.updateTerminalRecord(player.getUUID(), record ->
				record.putInt(TerminalData.WOOD_MINED_COUNT, SurvivalProgressService.REQUIRED_WOOD));
		helper.assertValueEqual(TerminalTaskService.claim(player, ready.index()),
				TerminalTaskService.ClaimResult.STALE,
				"A stale claim stays stale even with a later task finished");
		helper.assertValueEqual(player.getInventory().countItem(Items.STONE_AXE) - axeBefore, 1,
				"The catch-up pass delivers each finished task exactly once");
		helper.assertValueEqual(player.getInventory().countItem(Items.BREAD) - breadBefore, 6,
				"Catching up on a later task never re-pays an earlier one");
		helper.succeed();
	}

	@GameTest
	public void fullInventoryDropsCompletedRewardNearby(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.gameMode.changeGameModeForPlayer(GameType.SURVIVAL);
		BlockPos loadedTestPos = helper.absolutePos(new BlockPos(1, 2, 1));
		player.snapTo(loadedTestPos.getX() + 0.5D, loadedTestPos.getY(),
				loadedTestPos.getZ() + 0.5D, 0.0F, 0.0F);
		FrequencyWorldData data = FrequencyWorldData.get(helper.getLevel().getServer());
		data.updateTerminalRecord(player.getUUID(), record ->
				record.putInt(TerminalData.TERMINAL_PAGE_VISIT_MASK, TerminalTaskService.ALL_PAGES_MASK));
		for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
			if (player.getInventory().getItem(slot).isEmpty()) {
				player.getInventory().setItem(slot, new ItemStack(Items.COBBLESTONE, 64));
			}
		}
		player.getInventory().setChanged();

		helper.assertTrue(TerminalTaskService.notifyIfCompleted(player),
				"A full inventory still completes automatic reward delivery");
		var record = data.terminalRecord(player.getUUID()).orElseThrow();
		helper.assertTrue((record.getIntOr(TerminalData.TASK_REWARD_CLAIMED_MASK, 0) & 1) != 0,
				"The completed task advances even when inventory is full");
		helper.runAfterDelay(1, () -> {
			int droppedBread = helper.getLevel().getEntitiesOfClass(ItemEntity.class,
							player.getBoundingBox().inflate(2.0D)).stream()
					.filter(entity -> entity.getItem().is(Items.BREAD))
					.mapToInt(entity -> entity.getItem().getCount())
					.sum();
			helper.assertValueEqual(droppedBread, 6,
					"The reward remainder is dropped at the player's feet");
			helper.succeed();
		});
	}

	@Override
	public void invokeTestMethod(GameTestHelper helper, Method method) throws ReflectiveOperationException {
		method.invoke(this, helper);
	}
}
