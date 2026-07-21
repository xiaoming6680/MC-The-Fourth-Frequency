package com.xm.thefourthfrequency.test;

import com.xm.thefourthfrequency.content.ModBlocks;
import com.xm.thefourthfrequency.content.ModItems;
import com.xm.thefourthfrequency.content.TerminalData;
import com.xm.thefourthfrequency.correction.CorrectionTarget;
import com.xm.thefourthfrequency.correction.CorrectionTargetService;
import com.xm.thefourthfrequency.ending.EndingOutcome;
import com.xm.thefourthfrequency.ending.EndingState;
import com.xm.thefourthfrequency.ending.FinalConfrontationService;
import com.xm.thefourthfrequency.entity.MisreadBodyEntity;
import com.xm.thefourthfrequency.narrative.TerminalFileState;
import com.xm.thefourthfrequency.networking.TerminalControlPayload;
import com.xm.thefourthfrequency.terminal.TerminalRuntimeService;
import com.xm.thefourthfrequency.terminal.TerminalSignalLog;
import com.xm.thefourthfrequency.world.FrequencyWorldData;
import com.xm.thefourthfrequency.world.StoryProgressService;
import com.xm.thefourthfrequency.world.TerminalLifecycleService;
import net.fabricmc.fabric.api.gametest.v1.CustomTestMethodInvoker;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EndPortalFrameBlock;

import java.lang.reflect.Method;
import java.util.UUID;

public final class M7GameTests implements CustomTestMethodInvoker {
	private static final int[][] FRAMES = {
			{-1, -2}, {0, -2}, {1, -2}, {-1, 2}, {0, 2}, {1, 2},
			{-2, -1}, {-2, 0}, {-2, 1}, {2, -1}, {2, 0}, {2, 1}
	};

	@GameTest
	public void portalRoomDetectionRequiresACompleteInwardFacingVanillaFrame(GameTestHelper helper) {
		BlockPos center = helper.absolutePos(new BlockPos(8, 3, 8));
		for (int[] offset : FRAMES) {
			helper.getLevel().setBlockAndUpdate(center.offset(offset[0], 0, offset[1]),
					Blocks.END_PORTAL_FRAME.defaultBlockState()
							.setValue(EndPortalFrameBlock.FACING, facing(offset[0], offset[1]))
							.setValue(EndPortalFrameBlock.HAS_EYE, true));
		}
		helper.assertTrue(FinalConfrontationService.validPortalRing(helper.getLevel(), center),
				"The altar entrance must use the complete vanilla End Portal frame state");
		helper.assertValueEqual(FinalConfrontationService.findPortalRingNear(helper.getLevel(), center, 4).orElseThrow(),
				center, "Nearby portal-room detection must recover the real ring center");
		BlockPos wrong = center.offset(-1, 0, -2);
		helper.getLevel().setBlockAndUpdate(wrong, helper.getLevel().getBlockState(wrong)
				.setValue(EndPortalFrameBlock.FACING, Direction.NORTH));
		helper.assertFalse(FinalConfrontationService.validPortalRing(helper.getLevel(), center),
				"A decorative or wrongly oriented frame ring is not a portal room altar");
		helper.succeed();
	}

	@GameTest
	public void completeAltarFlowIsServerAuthoritativeSafeAndReachableOnEveryRoute(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		FrequencyWorldData data = FrequencyWorldData.get(helper.getLevel().getServer());
		preparePlayer(player, data);
		helper.assertTrue(FinalConfrontationService.debugSetAltarState(player, 0),
				"The fixed debug enum must reset the shared altar fixture before this test");

		data.updateTerminalRecord(player.getUUID(), record -> {
			record.putBoolean(TerminalData.BOUND, true);
			record.putInt(TerminalData.EYE_SAMPLE_COUNT, 2);
			record.putInt(TerminalData.SURVIVAL_MILESTONE_MASK, 0xFF);
			record.putInt(TerminalData.BODY_PROGRESS, 1_000);
			record.putInt(TerminalData.BODY_STAGE, 4);
		});
		long bodiesBefore = countNearbyFinalBodies(helper, player);
		FinalConfrontationService.updateServer(helper.getLevel().getServer());
		helper.assertValueEqual(countNearbyFinalBodies(helper, player), bodiesBefore,
				"Legacy maximum progress cannot spawn the final entity near an ordinary player");
		helper.assertFalse(EndingState.started(data),
				"Legacy maximum progress cannot start an ending without the altar");

		helper.assertValueEqual(StoryProgressService.objective(
				data.terminalRecord(player.getUUID()).orElseThrow(), data).id(), "find_portal_room",
				"Reaching the stronghold must lead to the real portal-room task");
		data.updateTerminalRecord(player.getUUID(), record -> record.putBoolean(TerminalData.PORTAL_ROOM_FOUND, true));
		helper.assertValueEqual(StoryProgressService.objective(
				data.terminalRecord(player.getUUID()).orElseThrow(), data).id(), "open_altar",
				"Finding the portal room must lead to the final Eye altar task");

		ItemStack terminal = TerminalData.stackFromRecord(data.terminalRecord(player.getUUID()).orElseThrow());
		player.setItemInHand(InteractionHand.MAIN_HAND, terminal);
		TerminalRuntimeService.open(player, 0);
		TerminalRuntimeService.control(player, TerminalControlPayload.READ_TRUTH_FILE, 0);
		helper.assertFalse(data.terminalRecord(player.getUUID()).orElseThrow()
				.getBooleanOr(TerminalData.TRUTH_READ, false),
				"A client action cannot claim the truth before the stable witness file is readable");
		long now = player.level().getGameTime();
		data.updateTerminalRecord(player.getUUID(), record -> TerminalFileState.discover(record,
				"encrypted_witness_file", now, player.level().getDayTime(), true));
		TerminalRuntimeService.control(player, TerminalControlPayload.READ_TRUTH_FILE, 0);
		helper.assertTrue(data.terminalRecord(player.getUUID()).orElseThrow()
				.getBooleanOr(TerminalData.TRUTH_READ, false),
				"Reading the unlocked stable file must set the server-authoritative truth state");
		TerminalRuntimeService.control(player, TerminalControlPayload.CLOSE, 0);

		BlockPos center = clearArena(helper, player.blockPosition().offset(0, 0, 8));
		BlockPos protectedBlock = center.offset(5, 0, 0);
		helper.getLevel().setBlockAndUpdate(protectedBlock, Blocks.CHEST.defaultBlockState());
		MisreadBodyEntity unread = FinalConfrontationService.startAltarForTesting(player, helper.getLevel(), center, false);
		helper.assertTrue(unread != null && FinalConfrontationService.altarActive(data),
				"Only the explicit altar flow creates the persistent final entity");
		helper.assertValueEqual(countAnchors(helper, center), 4,
				"Four fixed devices must exist as real, breakable blocks inside the portal ring");
		helper.assertValueEqual(CorrectionTargetService.blockTargets(helper.getLevel()).stream()
				.filter(target -> target.kind() == CorrectionTarget.Kind.GROUNDING_ANCHOR).count(), 4L,
				"The correction body's final obstruction must target each real altar device");
		int terminalsBefore = countTerminals(player);
		unread.snapTo(player.getX(), player.getY(), player.getZ(), 0.0F, 0.0F);
		helper.assertFalse(FinalConfrontationService.tryCapture(unread, player),
				"The final entity must never take a terminal or any other carried item");
		helper.assertValueEqual(countTerminals(player), terminalsBefore,
				"Final combat leaves the player's inventory intact");
		helper.assertValueEqual(FinalConfrontationService.absorbNearby(unread), 0,
				"Final combat cannot absorb nearby terrain or player construction");
		helper.assertValueEqual(FinalConfrontationService.placeAdaptationBarrier(unread, player.position()), 0,
				"Final combat cannot place route barriers through a base");
		helper.assertTrue(helper.getLevel().getBlockState(protectedBlock).is(Blocks.CHEST),
				"A nearby container must remain untouched");
		helper.assertTrue(FinalConfrontationService.followAcrossRules(unread) == null
				&& unread.level() == helper.getLevel(),
				"The final entity cannot follow a player to another dimension or a respawn base");
		FinalConfrontationService.onBodyDefeated(unread, player);
		helper.assertTrue(EndingState.outcome(data) == EndingOutcome.UNDISCOVERED,
				"Not reading the truth must select the preserved undiscovered-truth ending ID");
		helper.assertFalse(EndingState.get(data).getBooleanOr("body_active", true),
				"Every ending closes the encounter instead of restarting pursuit");
		unread.discard();

		MisreadBodyEntity failed = FinalConfrontationService.startAltarForTesting(player, helper.getLevel(), center, true);
		removeAnchors(helper, center, 3);
		FinalConfrontationService.onBodyDefeated(failed, player);
		helper.assertTrue(EndingState.outcome(data) == EndingOutcome.FAILED,
				"Reading the truth with too few surviving devices must select the preserved failure ID");
		failed.discard();

		MisreadBodyEntity success = FinalConfrontationService.startAltarForTesting(player, helper.getLevel(), center, true);
		FinalConfrontationService.onBodyDefeated(success, player);
		helper.assertTrue(EndingState.outcome(data) == EndingOutcome.SUCCESS,
				"Reading the truth with enough surviving devices must select the preserved success ID");
		var record = data.terminalRecord(player.getUUID()).orElseThrow();
		helper.assertValueEqual(record.getStringOr(TerminalData.ENDING_OUTCOME, ""),
				EndingOutcome.SUCCESS.id(), "The authoritative ending must be projected to the terminal record");
		helper.assertTrue(record.getStringOr(TerminalData.TERMINAL_CAPABILITIES, "")
				.contains("resource_guidance"),
				"The successful ending cannot erase terminal abilities already earned in survival");
		helper.assertValueEqual(StoryProgressService.objective(record, data).id(), "complete",
				"Every final route must reach the complete objective after the entity is defeated");
		helper.assertTrue(TerminalSignalLog.containsType(record, "altar_started")
				&& TerminalSignalLog.containsType(record, "altar_entity_awakened")
				&& TerminalSignalLog.containsType(record, "altar_entity_defeated")
				&& TerminalSignalLog.containsType(record, "ending_recorded"),
				"The terminal must record altar start, entity state, and final outcome");
		success.discard();
		helper.assertTrue(FinalConfrontationService.debugSetAltarState(player, 0),
				"The shared altar fixture must be removed before parallel GameTests continue");
		helper.succeed();
	}

	@GameTest
	public void preservedEndingIdsResolveOnlyOnceAndCloseActiveEffects(GameTestHelper helper) {
		ServerPlayer fixture = helper.makeMockServerPlayerInLevel();
		for (EndingOutcome outcome : new EndingOutcome[]{EndingOutcome.UNDISCOVERED,
				EndingOutcome.FAILED, EndingOutcome.SUCCESS}) {
			FrequencyWorldData isolated = new FrequencyWorldData();
			isolated.markTerminalIssued(fixture.getUUID());
			isolated.ensureTerminalRecord(fixture);
			EndingState.beginAltar(isolated, UUID.randomUUID(), fixture.blockPosition().asLong(), 1,
					10L, "minecraft:overworld", fixture.blockPosition().asLong(), true, 4);
			helper.assertTrue(EndingState.resolve(isolated, outcome, 20L),
					"The preserved ending must resolve from the active altar state: " + outcome.id());
			helper.assertValueEqual(EndingState.outcome(isolated).id(), outcome.id(),
					"Ending ID compatibility must remain exact");
			helper.assertFalse(EndingState.get(isolated).getBooleanOr("body_active", true)
					|| EndingState.get(isolated).getBooleanOr("active_anomalies", true),
					"Every route must end the entity and active encounter effects");
			helper.assertFalse(EndingState.resolve(isolated, EndingOutcome.SUCCESS, 30L),
					"A recorded ending cannot be overwritten later");
		}
		helper.succeed();
	}

	private static void preparePlayer(ServerPlayer player, FrequencyWorldData data) {
		if (!data.hasTerminalIssued(player.getUUID())) data.markTerminalIssued(player.getUUID());
		data.ensureTerminalRecord(player);
		data.updateTerminalRecord(player.getUUID(), record -> {
			record.putBoolean(TerminalData.BOUND, true);
			record.putInt(TerminalData.SURVIVAL_MILESTONE_MASK, 0xFF);
		});
		TerminalLifecycleService.ensureCarried(player, true);
		player.getInventory().add(new ItemStack(Items.DIAMOND_SWORD));
		player.getInventory().add(new ItemStack(Items.BOW));
		player.getInventory().add(new ItemStack(Items.COOKED_BEEF, 16));
	}

	private static BlockPos clearArena(GameTestHelper helper, BlockPos center) {
		for (int x = -3; x <= 3; x++) for (int z = -3; z <= 3; z++) {
			helper.getLevel().setBlockAndUpdate(center.offset(x, -1, z), Blocks.STONE.defaultBlockState());
			for (int y = 0; y <= 4; y++) helper.getLevel().setBlockAndUpdate(
					center.offset(x, y, z), Blocks.AIR.defaultBlockState());
		}
		return center;
	}

	private static void removeAnchors(GameTestHelper helper, BlockPos center, int count) {
		int[][] anchors = {{-1, -1}, {1, -1}, {-1, 1}, {1, 1}};
		for (int index = 0; index < count; index++) helper.getLevel().setBlockAndUpdate(
				center.offset(anchors[index][0], 0, anchors[index][1]), Blocks.AIR.defaultBlockState());
	}

	private static int countAnchors(GameTestHelper helper, BlockPos center) {
		int count = 0;
		for (int x : new int[]{-1, 1}) for (int z : new int[]{-1, 1}) {
			if (helper.getLevel().getBlockState(center.offset(x, 0, z)).is(ModBlocks.ALTAR_ANCHOR)) count++;
		}
		return count;
	}

	private static int countTerminals(ServerPlayer player) {
		int count = 0;
		for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
			if (player.getInventory().getItem(slot).is(ModItems.OLD_TERMINAL)) count++;
		}
		return count;
	}

	private static long countNearbyFinalBodies(GameTestHelper helper, ServerPlayer player) {
		long count = 0;
		for (Entity entity : helper.getLevel().getAllEntities()) {
			if (entity instanceof MisreadBodyEntity && entity.distanceToSqr(player) <= 64 * 64) count++;
		}
		return count;
	}

	private static Direction facing(int x, int z) {
		if (x == -2) return Direction.EAST;
		if (x == 2) return Direction.WEST;
		if (z == -2) return Direction.SOUTH;
		return Direction.NORTH;
	}

	@Override
	public void invokeTestMethod(GameTestHelper helper, Method method) throws ReflectiveOperationException {
		method.invoke(this, helper);
	}
}
