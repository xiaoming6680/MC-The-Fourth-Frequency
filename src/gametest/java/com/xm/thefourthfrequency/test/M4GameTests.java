package com.xm.thefourthfrequency.test;

import com.xm.thefourthfrequency.content.ModBlocks;
import com.xm.thefourthfrequency.content.TerminalData;
import com.xm.thefourthfrequency.narrative.HiddenFilePolicy;
import com.xm.thefourthfrequency.narrative.TerminalFileState;
import com.xm.thefourthfrequency.narrative.WitnessArchive;
import com.xm.thefourthfrequency.networking.TerminalControlPayload;
import com.xm.thefourthfrequency.terminal.SignalBand;
import com.xm.thefourthfrequency.terminal.TerminalRuntimeService;
import com.xm.thefourthfrequency.terminal.TerminalSignalLog;
import com.xm.thefourthfrequency.terminal.TerminalSignalService;
import com.xm.thefourthfrequency.terminal.TerminalTool;
import com.xm.thefourthfrequency.terminal.TerminalToolService;
import com.xm.thefourthfrequency.world.FragmentInvestigationService;
import com.xm.thefourthfrequency.world.FrequencyWorldData;
import com.xm.thefourthfrequency.world.RiftArchiveService;
import com.xm.thefourthfrequency.world.SurvivalMilestone;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.gametest.v1.CustomTestMethodInvoker;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;

public final class M4GameTests implements CustomTestMethodInvoker {
	@GameTest(maxTicks = 400)
	public void vanillaStructuresUnlockSharedFragmentsAndRealRift(GameTestHelper helper) {
		ServerPlayer discoverer = helper.makeMockServerPlayerInLevel();
		ServerPlayer teammate = helper.makeMockServerPlayerInLevel();
		FrequencyWorldData data = FrequencyWorldData.get(helper.getLevel().getServer());
		for (ServerPlayer player : List.of(discoverer, teammate)) data.updateTerminalRecord(player.getUUID(), record -> {
			record.putBoolean(TerminalData.BOUND, true);
			record.putInt(TerminalData.BAND_STAGE, 2);
			record.putInt(TerminalData.PLOT_STAGE, 3);
			record.putInt(TerminalData.SURVIVAL_MILESTONE_MASK, SurvivalMilestone.MINED_LOGS.mask()
					| SurvivalMilestone.IRON.mask() | SurvivalMilestone.ENTERED_NETHER.mask());
			record.putString(TerminalData.GUIDANCE_OBJECTIVE_ID, "collect_blaze_rods");
			record.putInt(TerminalData.GUIDANCE_OBJECTIVE_PROGRESS, 0);
			record.putLong(TerminalData.GUIDANCE_STALLED_TICKS, 6_000L);
		});

		int legacyFacilitiesBefore = data.narrativeState().getListOrEmpty("facilities").size();
		RiftArchiveService.updateForTesting(helper.getLevel().getServer());
		helper.assertValueEqual(data.narrativeState().getListOrEmpty("facilities").size(), legacyFacilitiesBefore,
				"The current mainline must not allocate predecessor facilities");

		BlockPos origin = discoverer.blockPosition();
		List<FragmentInvestigationService.Candidate> candidates = List.of(
				candidate(0, FragmentInvestigationService.Group.MINESHAFT, origin.offset(600, -15, 200)),
				candidate(0, FragmentInvestigationService.Group.SHIPWRECK, origin.offset(-900, 0, 400)),
				candidate(1, FragmentInvestigationService.Group.DESERT_PYRAMID, origin.offset(1200, 0, -500)),
				candidate(1, FragmentInvestigationService.Group.IGLOO, origin.offset(-1500, 0, -700)),
				candidate(2, FragmentInvestigationService.Group.TRIAL_CHAMBERS, origin.offset(1800, -25, 800)),
				candidate(2, FragmentInvestigationService.Group.OCEAN_MONUMENT, origin.offset(-2100, 0, 1000)),
				candidate(3, FragmentInvestigationService.Group.ANCIENT_CITY, origin.offset(2400, -35, -1200)),
				candidate(3, FragmentInvestigationService.Group.RUINED_PORTAL, origin.offset(-2700, 0, -1400)));
		FragmentInvestigationService.setCandidatesForTesting(data, candidates);
		ServerPlayer early = helper.makeMockServerPlayerInLevel();
		TerminalSignalService.updatePlayerForTesting(early);
		var earlyRecord = data.terminalRecord(early.getUUID()).orElseThrow();
		early.setItemInHand(InteractionHand.MAIN_HAND, TerminalData.stackFromRecord(earlyRecord));
		TerminalRuntimeService.open(early, 0);
		TerminalRuntimeService.control(early, TerminalControlPayload.READ_HIDDEN_FILE, 0);
		helper.assertValueEqual(HiddenFilePolicy.readCount(data.terminalRecord(early.getUUID()).orElseThrow()), 0,
				"A client cannot forge a read for a hidden file that has not been discovered");
		TerminalRuntimeService.control(early, TerminalControlPayload.CLOSE, 0);
		for (int fragment = 0; fragment < 4; fragment++) {
			SignalBand expectedBand = FragmentInvestigationService.bandForFragment(fragment);
			String markerType = "fragment_marker_" + (fragment + 1);
			helper.assertTrue(TerminalSignalLog.entries(earlyRecord, expectedBand).stream()
					.anyMatch(entry -> entry.type().equals(markerType) && entry.position() == 0L),
					"Each public/fourth band receives one position-free unrecorded marker");
		}
		helper.assertFalse(TerminalSignalLog.entries(earlyRecord).stream()
				.anyMatch(entry -> entry.type().startsWith("fragment_candidate_")),
				"Candidate structure positions remain absent before the investigation gate");
		TerminalSignalService.updatePlayerForTesting(discoverer);
		TerminalSignalService.updatePlayerForTesting(teammate);
		long distinctCoordinates = candidates.stream().map(FragmentInvestigationService.Candidate::position).distinct().count();
		helper.assertValueEqual((int) distinctCoordinates, candidates.size(),
				"One concrete structure coordinate cannot satisfy two fragments");
		var discovererRecord = data.terminalRecord(discoverer.getUUID()).orElseThrow();
		long candidateLogs = TerminalSignalLog.entries(discovererRecord).stream()
				.filter(entry -> entry.type().startsWith("fragment_candidate_")).count();
		helper.assertValueEqual((int) candidateLogs, candidates.size(),
				"The terminal receives every selected vanilla-structure coordinate without a chest or block target");
		for (int fragment = 0; fragment < 4; fragment++) {
			String prefix = "fragment_candidate_" + (fragment + 1) + "_";
			SignalBand expectedBand = FragmentInvestigationService.bandForFragment(fragment);
			helper.assertTrue(TerminalSignalLog.entries(discovererRecord, expectedBand).stream()
					.anyMatch(entry -> entry.type().startsWith(prefix)),
					"Each fragment exposes its candidates only inside its assigned band");
		}

		helper.assertTrue(FragmentInvestigationService.selectCandidate(discoverer, 3),
				"Player can choose the first candidate for fragment two");
		helper.assertTrue(TerminalToolService.startGuidance(discoverer, TerminalTool.NAVIGATION.slot()),
				"The signal tool can take control of the compass for a located candidate");
		var navigation = TerminalRuntimeService.navigationSnapshot(discoverer);
		helper.assertValueEqual(navigation.targetKind(), 4, "Unstable-source navigation retains wire kind four in v5");
		helper.assertTrue(navigation.navigable(), "Bound terminals retain personal structure navigation");

		helper.assertTrue(FragmentInvestigationService.discoverForTesting(discoverer, candidates.getFirst()),
				"One of several candidates completes fragment one");
		helper.assertFalse(FragmentInvestigationService.discoverForTesting(discoverer, candidates.get(1)),
				"Other candidates stop after fragment one is complete");
		var onlineAfterFirst = data.terminalRecord(teammate.getUUID()).orElseThrow();
		helper.assertTrue(TerminalFileState.discovered(onlineAfterFirst, "surface_shelter_record"),
				"An online teammate immediately receives the same shared fragment");
		helper.assertFalse(TerminalSignalLog.containsType(onlineAfterFirst, "fragment_action_1"),
				"The discoverer's behavior card remains private");

		ServerPlayer later = helper.makeMockServerPlayerInLevel();
		data.updateTerminalRecord(later.getUUID(), record -> record.putInt(TerminalData.BAND_STAGE, 1));
		TerminalSignalService.updatePlayerForTesting(later);
		var laterAfterFirst = data.terminalRecord(later.getUUID()).orElseThrow();
		helper.assertTrue(TerminalFileState.discovered(laterAfterFirst, "surface_shelter_record"),
				"A later terminal receives the world-shared fragment");
		helper.assertTrue(TerminalSignalLog.containsType(laterAfterFirst, "fragment_received_1"),
				"A later terminal records who shared the file");

		for (int fragment = 1; fragment < 4; fragment++) {
			helper.assertTrue(FragmentInvestigationService.discoverForTesting(discoverer, candidates.get(fragment * 2)),
					"One of several candidates completes fragment " + (fragment + 1));
			helper.assertFalse(FragmentInvestigationService.discoverForTesting(discoverer, candidates.get(fragment * 2 + 1)),
					"Other candidates stop after the fragment is complete");
		}

		for (ServerPlayer player : List.of(discoverer, teammate, later)) {
			var record = data.terminalRecord(player.getUUID()).orElseThrow();
			for (String file : HiddenFilePolicy.FILE_IDS)
				helper.assertTrue(TerminalFileState.discovered(record, file), "Every hidden file is world-shared");
			helper.assertFalse(TerminalFileState.unlocked(record, HiddenFilePolicy.COMPLETE_FILE_ID),
					"Discovery alone must leave the complete diary locked");
			helper.assertFalse(record.getBooleanOr(TerminalData.LOCAL_FILE_UNLOCKED, false),
					"Discovery alone must not synchronize complete witness prose");
			helper.assertValueEqual(HiddenFilePolicy.readCount(record), 0,
					"Shared discovery must not leak one player's read state to another");
		}
		var teammateRecord = data.terminalRecord(teammate.getUUID()).orElseThrow();
		helper.assertTrue(TerminalSignalLog.containsType(teammateRecord, "fragment_received_1"),
				"Teammate receives the named shared-file record");
		helper.assertFalse(TerminalSignalLog.containsType(teammateRecord, "fragment_action_1"),
				"Discoverer's private behavior card is not shared");

		readHiddenFiles(discoverer, data, helper, 3);
		var atSeventyFive = data.terminalRecord(discoverer.getUUID()).orElseThrow();
		helper.assertValueEqual(HiddenFilePolicy.readPercent(atSeventyFive), 75,
				"Three personal reads produce exactly 75 percent");
		helper.assertFalse(TerminalFileState.unlocked(atSeventyFive, HiddenFilePolicy.COMPLETE_FILE_ID),
				"The full recovered title can coexist with a still-locked diary at 75 percent");

		TerminalRuntimeService.control(discoverer, TerminalControlPayload.READ_HIDDEN_FILE, 3);
		TerminalRuntimeService.control(discoverer, TerminalControlPayload.READ_HIDDEN_FILE, 3);
		TerminalRuntimeService.control(discoverer, TerminalControlPayload.CLOSE, 0);
		var discovererUnlocked = data.terminalRecord(discoverer.getUUID()).orElseThrow();
		helper.assertValueEqual(HiddenFilePolicy.readPercent(discovererUnlocked), 100,
				"The fourth read reaches 100 percent and duplicate opens stay idempotent");
		helper.assertTrue(TerminalFileState.unlocked(discovererUnlocked, HiddenFilePolicy.COMPLETE_FILE_ID)
				&& discovererUnlocked.getBooleanOr(TerminalData.LOCAL_FILE_UNLOCKED, false),
				"The first 4/4 reader unlocks only their complete diary");
		var narrative = data.narrativeState();
		helper.assertTrue(narrative.getBooleanOr("archive_unlocked", false)
				&& narrative.contains("rift_entrance") && narrative.contains("rift_core"),
				"The first 4/4 reader creates or reuses the one retained Overworld fracture");
		long firstRiftCore = narrative.getLongOr("rift_core", 0L);

		var teammateBeforeRead = data.terminalRecord(teammate.getUUID()).orElseThrow();
		helper.assertValueEqual(HiddenFilePolicy.readCount(teammateBeforeRead), 0,
				"A teammate retains independent unread state");
		helper.assertFalse(TerminalFileState.unlocked(teammateBeforeRead, HiddenFilePolicy.COMPLETE_FILE_ID),
				"A shared rift does not bypass the teammate's personal reading requirement");
		readHiddenFiles(teammate, data, helper, 4);
		TerminalRuntimeService.control(teammate, TerminalControlPayload.CLOSE, 0);
		var teammateUnlocked = data.terminalRecord(teammate.getUUID()).orElseThrow();
		helper.assertTrue(TerminalFileState.unlocked(teammateUnlocked, HiddenFilePolicy.COMPLETE_FILE_ID),
				"The teammate unlocks only after personally reading all four files");
		helper.assertValueEqual(data.narrativeState().getLongOr("rift_core", 0L), firstRiftCore,
				"A later 4/4 reader must not allocate or move the shared rift");
		helper.assertFalse(TerminalFileState.unlocked(data.terminalRecord(later.getUUID()).orElseThrow(),
				HiddenFilePolicy.COMPLETE_FILE_ID),
				"An unread later terminal remains locked after other players finish");

		BlockPos rift = BlockPos.of(data.terminalRecord(discoverer.getUUID()).orElseThrow()
				.getLongOr(TerminalData.RIFT_POSITION, 0L));
		loadFixtureChunks(helper, rift);
		for (int iteration = 0; iteration < 8; iteration++) {
			RiftArchiveService.updateForTesting(helper.getLevel().getServer());
		}
		var record = data.terminalRecord(discoverer.getUUID()).orElseThrow();
		WitnessArchive archive = WitnessArchive.get();
		helper.assertValueEqual(record.getIntOr(TerminalData.LOCAL_FILE_VERSION, 0), archive.version(),
				"Complete file version remains immutable");
		helper.assertValueEqual(record.getStringOr(TerminalData.LOCAL_FILE_HASH, ""), archive.contentHash(),
				"Complete file hash remains immutable");
		helper.assertTrue(helper.getLevel().getBlockState(rift).is(ModBlocks.RULE_FRACTURE_CORE),
				"The revealed coordinate points to a physical fracture core");
		teleport(discoverer, rift);
		InteractionResult result = UseBlockCallback.EVENT.invoker().interact(discoverer, helper.getLevel(),
				InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(rift), Direction.UP, rift, false));
		helper.assertValueEqual(result, InteractionResult.SUCCESS, "Physical fracture interaction remains the next step");
		helper.succeed();
	}

	private static FragmentInvestigationService.Candidate candidate(int fragment,
			FragmentInvestigationService.Group group, BlockPos position) {
		return new FragmentInvestigationService.Candidate(fragment, group, position, "minecraft:overworld");
	}

	private static void readHiddenFiles(ServerPlayer player, FrequencyWorldData data, GameTestHelper helper, int count) {
		ItemStack terminal = TerminalData.stackFromRecord(data.terminalRecord(player.getUUID()).orElseThrow());
		player.setItemInHand(InteractionHand.MAIN_HAND, terminal);
		TerminalRuntimeService.open(player, 0);
		for (int index = 0; index < count; index++) {
			TerminalRuntimeService.control(player, TerminalControlPayload.READ_HIDDEN_FILE, index);
		}
		helper.assertValueEqual(HiddenFilePolicy.readCount(data.terminalRecord(player.getUUID()).orElseThrow()), count,
				"Each acquired hidden file contributes exactly one personal read");
	}

	private static void teleport(ServerPlayer player, BlockPos position) {
		player.teleportTo(player.level(), position.getX() + 0.5, position.getY() + 1.0,
				position.getZ() + 0.5, Set.of(), 0.0F, 0.0F, true);
	}

	private static void loadFixtureChunks(GameTestHelper helper, BlockPos center) {
		int centerChunkX = center.getX() >> 4;
		int centerChunkZ = center.getZ() >> 4;
		for (int chunkX = centerChunkX - 1; chunkX <= centerChunkX + 1; chunkX++)
			for (int chunkZ = centerChunkZ - 1; chunkZ <= centerChunkZ + 1; chunkZ++)
				helper.getLevel().getChunk(chunkX, chunkZ);
	}

	@Override
	public void invokeTestMethod(GameTestHelper helper, Method method) throws ReflectiveOperationException {
		method.invoke(this, helper);
	}
}
