package com.xm.thefourthfrequency.test;

import com.xm.thefourthfrequency.content.ModBlocks;
import com.xm.thefourthfrequency.content.TerminalData;
import com.xm.thefourthfrequency.correction.CorrectionState;
import com.xm.thefourthfrequency.terminal.TerminalSignalLog;
import com.xm.thefourthfrequency.terminal.TerminalTaskService;
import com.xm.thefourthfrequency.terminal.TerminalToolService;
import com.xm.thefourthfrequency.world.FrequencyWorldData;
import com.xm.thefourthfrequency.world.StoryProgressService;
import com.xm.thefourthfrequency.world.SurvivalMilestone;
import com.xm.thefourthfrequency.world.SurvivalProgressService;
import net.fabricmc.fabric.api.gametest.v1.CustomTestMethodInvoker;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
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
	public void vanillaMilestonesStartARecoverableCorrectionScene(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		FrequencyWorldData data = FrequencyWorldData.get(helper.getLevel().getServer());
		player.getInventory().add(new ItemStack(Items.OAK_PLANKS, SurvivalProgressService.REQUIRED_WOOD));
		SurvivalProgressService.updatePlayer(player, data);
		var homeObjective = StoryProgressService.objective(
				data.terminalRecord(player.getUUID()).orElseThrow(), data);
		helper.assertValueEqual(homeObjective.id(), "bring_iron", "Collected wood advances the survival objective");

		player.getInventory().add(new ItemStack(Items.RAW_IRON, SurvivalProgressService.REQUIRED_IRON));
		BlockPos origin = player.blockPosition();
		for (BlockPos candidate : new BlockPos[] {origin.offset(6, 0, 0), origin.offset(-6, 0, 0),
				origin.offset(0, 0, 6), origin.offset(0, 0, -6), origin.offset(4, 1, 4)}) {
			helper.getLevel().setBlockAndUpdate(candidate, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
			helper.getLevel().setBlockAndUpdate(candidate.above(), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
		}
		SurvivalProgressService.updatePlayer(player, data);
		var record = data.terminalRecord(player.getUUID()).orElseThrow();
		helper.assertTrue(SurvivalMilestone.IRON.present(
				record.getIntOr(TerminalData.SURVIVAL_MILESTONE_MASK, 0)), "Real carried iron records the mining milestone");
		BlockPos trace = CorrectionState.anomalyTracePositions(data).stream()
				.filter(position -> position.distSqr(origin) <= 100.0)
				.filter(position -> helper.getLevel().getBlockState(position).is(ModBlocks.NASCENT_BODY_ORGAN))
				.findFirst().orElseThrow();
		helper.assertTrue(helper.getLevel().getBlockState(trace).is(ModBlocks.NASCENT_BODY_ORGAN),
				"The deterministic signature scene places one recoverable anomaly trace in empty space");
		helper.assertTrue(TerminalToolService.toolsDisabled(record, player.level().getGameTime()),
				"The signature scene temporarily interrupts convenience tools");
		long savedHome = record.getLongOr(TerminalData.HOME_POSITION, 0L);
		helper.assertFalse(TerminalToolService.requestRescan(player),
				"A disabled tool rejects a forced control attempt");
		var breached = data.terminalRecord(player.getUUID()).orElseThrow();
		helper.assertTrue((breached.getIntOr(TerminalData.BREACH_MASK, 0) & 1) != 0,
				"The rejected attempt is recorded for the correction entity's hostility boundary");
		helper.assertValueEqual(breached.getLongOr(TerminalData.HOME_POSITION, 0L), savedHome,
				"Tool interruption never deletes a saved home");

		data.updateTerminalRecord(player.getUUID(), value ->
				value.putLong(TerminalData.TOOLS_DISABLED_UNTIL, player.level().getGameTime()));
		SurvivalProgressService.updatePlayer(player, data);
		var recovered = data.terminalRecord(player.getUUID()).orElseThrow();
		helper.assertFalse(TerminalToolService.toolsDisabled(recovered, player.level().getGameTime()),
				"Waiting naturally restores the convenience tools");
		helper.assertTrue(TerminalSignalLog.containsType(recovered, "signature_anomaly")
				&& TerminalSignalLog.containsType(recovered, "signature_correction")
				&& TerminalSignalLog.containsType(recovered, "signature_explained"),
				"The terminal records anomaly, correction, and explanation as one complete scene");
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
		helper.assertValueEqual(record.getIntOr(TerminalData.BODY_PROGRESS, 0), 0,
				"The transition never writes the retired timer");
		helper.succeed();
	}

	@GameTest
	public void completedTerminalTaskWaitsForOneManualRewardClaim(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		FrequencyWorldData data = FrequencyWorldData.get(helper.getLevel().getServer());
		data.updateTerminalRecord(player.getUUID(), record ->
				record.putInt(TerminalData.TERMINAL_PAGE_VISIT_MASK, TerminalTaskService.ALL_PAGES_MASK));
		var ready = TerminalTaskService.current(data.terminalRecord(player.getUUID()).orElseThrow());
		helper.assertValueEqual(ready.id(), "learn_terminal", "The first task is learning the four terminal tabs");
		helper.assertTrue(ready.claimable(), "Visiting all four tabs completes the task without auto-claiming");

		int breadBefore = player.getInventory().countItem(Items.BREAD);
		helper.assertValueEqual(TerminalTaskService.claim(player, ready.index()),
				TerminalTaskService.ClaimResult.CLAIMED, "The completed card accepts one explicit claim");
		helper.assertValueEqual(player.getInventory().countItem(Items.BREAD) - breadBefore, 6,
				"The first task grants its displayed reward");
		helper.assertValueEqual(TerminalTaskService.claim(player, ready.index()),
				TerminalTaskService.ClaimResult.STALE, "A repeated packet cannot claim the next task");
		helper.assertValueEqual(player.getInventory().countItem(Items.BREAD) - breadBefore, 6,
				"The repeated claim never duplicates the reward");
		helper.succeed();
	}

	@GameTest
	public void fullInventoryKeepsCompletedRewardUnclaimed(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		FrequencyWorldData data = FrequencyWorldData.get(helper.getLevel().getServer());
		data.updateTerminalRecord(player.getUUID(), record ->
				record.putInt(TerminalData.TERMINAL_PAGE_VISIT_MASK, TerminalTaskService.ALL_PAGES_MASK));
		for (int slot = 0; slot < 36; slot++) {
			if (player.getInventory().getItem(slot).isEmpty()) {
				player.getInventory().setItem(slot, new ItemStack(Items.COBBLESTONE, 64));
			}
		}
		player.getInventory().setChanged();

		helper.assertValueEqual(TerminalTaskService.claim(player, 0),
				TerminalTaskService.ClaimResult.INVENTORY_FULL,
				"A full inventory rejects the claim without dropping the reward");
		var record = data.terminalRecord(player.getUUID()).orElseThrow();
		helper.assertValueEqual(record.getIntOr(TerminalData.TASK_REWARD_CLAIMED_MASK, 0), 0,
				"The reward remains available after the failed claim");
		helper.assertTrue(TerminalTaskService.current(record).claimable(),
				"The completed card stays in its claimable state");
		helper.succeed();
	}

	@Override
	public void invokeTestMethod(GameTestHelper helper, Method method) throws ReflectiveOperationException {
		method.invoke(this, helper);
	}
}
