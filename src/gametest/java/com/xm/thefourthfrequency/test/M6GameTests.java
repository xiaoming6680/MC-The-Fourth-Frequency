package com.xm.thefourthfrequency.test;

import com.xm.thefourthfrequency.content.TerminalData;
import com.xm.thefourthfrequency.terminal.TerminalSignalLog;
import com.xm.thefourthfrequency.world.FrequencyWorldData;
import com.xm.thefourthfrequency.world.SurvivalMilestone;
import com.xm.thefourthfrequency.world.TerminalLifecycleService;
import net.fabricmc.fabric.api.gametest.v1.CustomTestMethodInvoker;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import java.lang.reflect.Method;
import java.util.Set;

public final class M6GameTests implements CustomTestMethodInvoker {
	@GameTest(maxTicks = 100)
	public void portalContinuityRecordsBothCrossingsWithoutPhysicalFractures(GameTestHelper helper) {
		var server = helper.getLevel().getServer();
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		FrequencyWorldData data = FrequencyWorldData.get(server);
		prepareEligible(player, data);
		ServerLevel nether = server.getLevel(Level.NETHER);
		helper.assertTrue(nether != null, "Nether must exist for the M6 continuity flow");
		helper.assertTrue(player.teleportTo(nether, 0.5, 64.0, 0.5, Set.of(), 0.0F, 0.0F, true),
				"Real server dimension transition into the Nether");

		helper.runAfterDelay(8, () -> {
			CompoundTag record = data.terminalRecord(player.getUUID()).orElseThrow();
			helper.assertTrue(record.getBooleanOr(TerminalData.CONTINUITY_LEARNED, false),
					"Overworld-to-Nether transition must teach persistent cross-carrier identity");
			helper.assertValueEqual(record.getIntOr(TerminalData.PORTAL_TRANSITIONS, 0), 1,
					"Exactly one qualifying portal transition");
			helper.assertTrue(record.getStringOr(TerminalData.LAST_PORTAL_DIMENSION, "")
					.equals("minecraft:the_nether"), "The portal tool must save the real destination dimension");
			helper.assertValueEqual(record.getLongOr(TerminalData.LAST_PORTAL_POSITION, 0L),
					player.blockPosition().asLong(), "The portal tool must save the real arrival position");
			helper.assertTrue(SurvivalMilestone.ENTERED_NETHER.present(
					record.getIntOr(TerminalData.SURVIVAL_MILESTONE_MASK, 0)),
					"A real qualifying transition must record the Nether survival milestone");
			helper.assertTrue(TerminalSignalLog.containsType(record, "continuity"),
					"The crossing must appear in RECORDS without creating an extra file");
			helper.assertTrue(player.teleportTo(server.overworld(), 0.5, 64.0, 0.5,
					Set.of(), 0.0F, 0.0F, true), "Return continuity transition");
		});

		helper.runAfterDelay(14, () -> {
			CompoundTag returned = data.terminalRecord(player.getUUID()).orElseThrow();
			helper.assertValueEqual(returned.getIntOr(TerminalData.PORTAL_TRANSITIONS, 0), 2,
					"Nether-to-Overworld return must increase the same persistent model");
			helper.assertValueEqual(returned.getIntOr(TerminalData.CONTINUITY_CONFIDENCE, 0), 50,
					"Continuity confidence must be monotonic and derived from real transitions");
			helper.assertTrue(returned.getStringOr(TerminalData.LAST_PORTAL_DESTINATION, "")
					.equals("minecraft:overworld"), "Last transition destination must survive in the ledger");
			helper.assertTrue(returned.getStringOr(TerminalData.LAST_PORTAL_DIMENSION, "")
					.equals("minecraft:overworld"), "The portal tool must update to the latest real arrival");
			helper.assertTrue(SurvivalMilestone.RETURNED_NETHER.present(
					returned.getIntOr(TerminalData.SURVIVAL_MILESTONE_MASK, 0)),
					"A real return must complete the Nether round-trip milestone");
			helper.assertTrue(TerminalSignalLog.containsType(returned, "return"),
					"The return must be appended to the same RECORDS ledger");
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 100)
	public void multiplayerPrivateModelsDifferAndRejectUnrelatedDimensions(GameTestHelper helper) {
		var server = helper.getLevel().getServer();
		FrequencyWorldData data = FrequencyWorldData.get(server);
		ServerPlayer first = helper.makeMockServerPlayerInLevel();
		ServerPlayer second = helper.makeMockServerPlayerInLevel();
		prepareEligible(first, data);
		prepareEligible(second, data);
		ServerLevel nether = server.getLevel(Level.NETHER);
		ServerLevel end = server.getLevel(Level.END);
		helper.assertTrue(nether != null && end != null, "Both vanilla destination dimensions must exist");
		first.teleportTo(nether, 32.5, 64.0, 0.5, Set.of(), 0.0F, 0.0F, true);
		second.teleportTo(nether, 36.5, 64.0, 0.5, Set.of(), 0.0F, 0.0F, true);
		CompoundTag firstCrossing = data.terminalRecord(first.getUUID()).orElseThrow();
		CompoundTag secondCrossing = data.terminalRecord(second.getUUID()).orElseThrow();
		helper.assertTrue(firstCrossing.getIntOr(TerminalData.PRIVATE_ANOMALY_VARIANT, -1)
				!= secondCrossing.getIntOr(TerminalData.PRIVATE_ANOMALY_VARIANT, -1),
				"Adjacent grant records must receive distinct private anomaly testimony");
		helper.assertValueEqual(firstCrossing.getIntOr(TerminalData.PRIVATE_ANOMALY_COUNT, 0), 1,
				"First player's private anomaly count");
		helper.assertValueEqual(secondCrossing.getIntOr(TerminalData.PRIVATE_ANOMALY_COUNT, 0), 1,
				"Second player's private anomaly count must remain isolated");

		int beforeUnrelated = firstCrossing.getIntOr(TerminalData.PORTAL_TRANSITIONS, 0);
		long portalBeforeUnrelated = firstCrossing.getLongOr(TerminalData.LAST_PORTAL_POSITION, 0L);
		String portalDimensionBeforeUnrelated =
				firstCrossing.getStringOr(TerminalData.LAST_PORTAL_DIMENSION, "");
		first.teleportTo(end, 0.5, 80.0, 0.5, Set.of(), 0.0F, 0.0F, true);
		CompoundTag afterUnrelated = data.terminalRecord(first.getUUID()).orElseThrow();
		helper.assertValueEqual(afterUnrelated.getIntOr(TerminalData.PORTAL_TRANSITIONS, 0), beforeUnrelated,
				"Nether-to-End teleport must not masquerade as portal continuity learning");
		helper.assertValueEqual(afterUnrelated.getLongOr(TerminalData.LAST_PORTAL_POSITION, 0L),
				portalBeforeUnrelated, "Unrelated dimension travel must not overwrite the portal navigation target");
		helper.assertTrue(afterUnrelated.getStringOr(TerminalData.LAST_PORTAL_DIMENSION, "")
				.equals(portalDimensionBeforeUnrelated),
				"Unrelated dimension travel must preserve the portal target dimension");
		first.teleportTo(server.overworld(), 40.5, 64.0, 0.5, Set.of(), 0.0F, 0.0F, true);
		for (int index = 0; index < 4; index++) {
			ServerLevel destination = first.level().dimension() == Level.NETHER ? server.overworld() : nether;
			first.teleportTo(destination, 40.5 + index, 64.0, 0.5, Set.of(), 0.0F, 0.0F, true);
		}
		CompoundTag modeled = data.terminalRecord(first.getUUID()).orElseThrow();
		helper.assertTrue(SurvivalMilestone.RETURNED_NETHER.present(
				modeled.getIntOr(TerminalData.SURVIVAL_MILESTONE_MASK, 0)),
				"Repeated real crossings must retain the vanilla survival milestones");
		helper.succeed();
	}

	private static void prepareEligible(ServerPlayer player, FrequencyWorldData data) {
		data.updateTerminalRecord(player.getUUID(), record -> {
			record.putBoolean(TerminalData.BOUND, true);
			record.putInt(TerminalData.PLOT_STAGE, 4);
			record.putInt(TerminalData.SURVIVAL_MILESTONE_MASK,
					SurvivalMilestone.PREPARED_NETHER.mask());
		});
		TerminalLifecycleService.ensureCarried(player, false);
	}

	@Override
	public void invokeTestMethod(GameTestHelper helper, Method method) throws ReflectiveOperationException {
		method.invoke(this, helper);
	}
}
