package com.xm.thefourthfrequency.test;

import com.xm.thefourthfrequency.content.ModBlocks;
import com.xm.thefourthfrequency.content.TerminalData;
import com.xm.thefourthfrequency.correction.CorrectionState;
import com.xm.thefourthfrequency.terminal.TerminalResource;
import com.xm.thefourthfrequency.terminal.TerminalSignalLog;
import com.xm.thefourthfrequency.terminal.TerminalToolService;
import com.xm.thefourthfrequency.world.FrequencyWorldData;
import com.xm.thefourthfrequency.world.StoryProgressService;
import com.xm.thefourthfrequency.world.SurvivalMilestone;
import com.xm.thefourthfrequency.world.SurvivalProgressService;
import net.fabricmc.fabric.api.gametest.v1.CustomTestMethodInvoker;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

import java.lang.reflect.Method;

public final class SurvivalReworkGameTests implements CustomTestMethodInvoker {
	@GameTest
	public void legacyMaximumProgressMigratesWithoutInventingEyeProgress(GameTestHelper helper) {
		CompoundTag legacy = new CompoundTag();
		legacy.putInt(TerminalData.SCHEMA_VERSION, 4);
		legacy.putInt(TerminalData.BODY_PROGRESS, 1_000);
		legacy.putInt(TerminalData.BODY_STAGE, 4);
		legacy.putInt(TerminalData.PORTAL_TRANSITIONS, 2);
		legacy.putBoolean(TerminalData.BOUND, true);
		CompoundTag migrated = TerminalData.migrateRecord(legacy);
		int mask = migrated.getIntOr(TerminalData.SURVIVAL_MILESTONE_MASK, 0);
		helper.assertTrue(SurvivalMilestone.HOME.present(mask) && SurvivalMilestone.IRON.present(mask)
				&& SurvivalMilestone.PREPARED_NETHER.present(mask)
				&& SurvivalMilestone.ENTERED_NETHER.present(mask)
				&& SurvivalMilestone.RETURNED_NETHER.present(mask),
				"Legacy progress migrates to the completed vanilla survival nodes");
		helper.assertFalse(SurvivalMilestone.CRAFTED_EYE.present(mask)
				|| SurvivalMilestone.THREW_EYE.present(mask)
				|| SurvivalMilestone.FOUND_STRONGHOLD.present(mask),
				"Migration must direct the player toward the stronghold instead of inventing Eye progress");
		helper.assertValueEqual(migrated.getIntOr(TerminalData.BODY_PROGRESS, 0), 1_000,
				"The legacy field remains readable after migration");
		helper.succeed();
	}

	@GameTest
	public void openingTaskAcceptsMixedWoodFamiliesAndPlanks(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		FrequencyWorldData data = FrequencyWorldData.get(helper.getLevel().getServer());
		player.getInventory().add(new ItemStack(Items.BIRCH_PLANKS, 3));
		player.getInventory().add(new ItemStack(Items.CRIMSON_PLANKS, 4));
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
		helper.assertTrue(TerminalToolService.setHome(player), "The player can save a real home");
		SurvivalProgressService.updatePlayer(player, data);
		var homeObjective = StoryProgressService.objective(
				data.terminalRecord(player.getUUID()).orElseThrow(), data);
		helper.assertValueEqual(homeObjective.id(), "bring_iron", "Saving a home advances the survival objective");

		player.getInventory().add(new ItemStack(Items.RAW_IRON));
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
		helper.assertFalse(TerminalToolService.selectResource(player, TerminalResource.IRON.wireId()),
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
	public void speedrunNetherTransitionCompensatesEarlierMilestonesWithoutLegacyProgress(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		FrequencyWorldData data = FrequencyWorldData.get(helper.getLevel().getServer());
		SurvivalProgressService.recordPortalTransition(player, helper.getLevel(),
				helper.getLevel().getServer().getLevel(Level.NETHER));
		var record = data.terminalRecord(player.getUUID()).orElseThrow();
		int mask = record.getIntOr(TerminalData.SURVIVAL_MILESTONE_MASK, 0);
		helper.assertTrue(SurvivalMilestone.HOME.present(mask) && SurvivalMilestone.IRON.present(mask)
				&& SurvivalMilestone.PREPARED_NETHER.present(mask) && SurvivalMilestone.ENTERED_NETHER.present(mask),
				"A real Nether speedrun cannot miss the earlier core milestones");
		helper.assertValueEqual(record.getIntOr(TerminalData.BODY_PROGRESS, 0), 0,
				"Speedrun compensation never writes the retired timer");
		helper.succeed();
	}

	@Override
	public void invokeTestMethod(GameTestHelper helper, Method method) throws ReflectiveOperationException {
		method.invoke(this, helper);
	}
}
