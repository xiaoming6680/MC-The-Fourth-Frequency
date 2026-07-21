package com.xm.thefourthfrequency.test;

import com.xm.thefourthfrequency.content.TerminalData;
import com.xm.thefourthfrequency.content.ModItems;
import com.xm.thefourthfrequency.terminal.AmbientAnomalyService;
import com.xm.thefourthfrequency.terminal.AnomalyCompletionStatus;
import com.xm.thefourthfrequency.terminal.AnomalyConditions;
import com.xm.thefourthfrequency.terminal.AnomalyGameTestBridge;
import com.xm.thefourthfrequency.terminal.AnomalyLeaseService;
import com.xm.thefourthfrequency.terminal.AnomalyRuntimeService;
import com.xm.thefourthfrequency.terminal.AnomalyServerEffects;
import com.xm.thefourthfrequency.terminal.TerminalAnomalyLogService;
import com.xm.thefourthfrequency.terminal.SignalBand;
import com.xm.thefourthfrequency.terminal.TerminalSignalLog;
import com.xm.thefourthfrequency.terminal.TerminalSignalService;
import com.xm.thefourthfrequency.terminal.TerminalRuntimeService;
import com.xm.thefourthfrequency.terminal.TerminalResource;
import com.xm.thefourthfrequency.terminal.TerminalToolService;
import com.xm.thefourthfrequency.networking.TerminalControlPayload;
import com.xm.thefourthfrequency.networking.AnomalyCompleteC2S;
import com.xm.thefourthfrequency.networking.DebugActionPayload;
import com.xm.thefourthfrequency.narrative.TerminalFileState;
import com.xm.thefourthfrequency.world.FrequencyWorldData;
import com.xm.thefourthfrequency.world.ResourceGuidanceService;
import com.xm.thefourthfrequency.world.StoryProgressService;
import com.xm.thefourthfrequency.world.DebugPanelService;
import com.xm.thefourthfrequency.world.TerminalLifecycleService;
import com.xm.thefourthfrequency.world.WorldDecayService;
import net.fabricmc.fabric.api.gametest.v1.CustomTestMethodInvoker;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

import java.lang.reflect.Method;

public final class TerminalAnomalyGameTests implements CustomTestMethodInvoker {
	@GameTest
	public void bindingRevealsTheMaintenanceHandoffWithoutCalibration(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		FrequencyWorldData data = FrequencyWorldData.get(helper.getLevel().getServer());
		CompoundTag initial = data.terminalRecord(player.getUUID()).orElseThrow();
		var initialFiles = TerminalRuntimeService.visibleFiles(initial);
		helper.assertValueEqual(initialFiles.size(), 0,
				"A new terminal waits for a real stage or facility before receiving files");

		TerminalSignalService.updatePlayerForTesting(player);
		helper.assertFalse(TerminalRuntimeService.visibleFiles(data.terminalRecord(player.getUUID()).orElseThrow())
				.stream().anyMatch(file -> file.id().equals("maintenance_handoff")),
				"The maintenance handoff stays absent before personal binding");
		data.updateTerminalRecord(player.getUUID(), record -> record.putBoolean(TerminalData.BOUND, true));
		TerminalSignalService.updatePlayerForTesting(player);
		var boundFiles = TerminalRuntimeService.visibleFiles(data.terminalRecord(player.getUUID()).orElseThrow());
		var handoff = boundFiles.stream().filter(file -> file.id().equals("maintenance_handoff"))
				.findFirst().orElseThrow(() -> new AssertionError("Binding did not reveal the maintenance handoff"));
		helper.assertTrue(handoff.unlocked(), "The handoff is immediately readable");
		helper.assertTrue(boundFiles.stream().allMatch(file -> file.discovered()
				&& TerminalFileState.discovered(data.terminalRecord(player.getUUID()).orElseThrow(), file.id())),
				"Every visible file has a real discovery record instead of a locked placeholder");
		helper.succeed();
	}

	@GameTest
	public void earlyResourceGuidanceDoesNotSkipNarrativeBinding(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		FrequencyWorldData data = FrequencyWorldData.get(helper.getLevel().getServer());
		BlockPos ore = player.blockPosition().below(2);
		helper.getLevel().setBlockAndUpdate(ore, Blocks.IRON_ORE.defaultBlockState());
		data.updateTerminalRecord(player.getUUID(), record -> {
				record.putLong(TerminalData.ISSUED_GAME_TIME, player.level().getGameTime() - 1_201L);
				record.putInt(TerminalData.BAND_STAGE, 0);
				record.putBoolean(TerminalData.BOUND, false);
		});

		TerminalToolService.selectResource(player, TerminalResource.IRON.wireId());
		ResourceGuidanceService.requestRescan(player);
		ResourceGuidanceService.updatePlayer(player);
		CompoundTag located = data.terminalRecord(player.getUUID()).orElseThrow();
		helper.assertTrue(located.getBooleanOr(TerminalData.TARGET_LOCATED, false),
				"Early terminal observation can locate a real resource before the fourth band appears");
		helper.assertValueEqual(located.getIntOr(TerminalData.BAND_STAGE, 0), 0,
				"Locating an optional resource never reveals the fourth band");

		player.getInventory().add(new ItemStack(Items.RAW_IRON));
		ResourceGuidanceService.updatePlayer(player);
		CompoundTag accepted = data.terminalRecord(player.getUUID()).orElseThrow();
		helper.assertFalse(accepted.getBooleanOr(TerminalData.BOUND, false),
				"Accepting optional resource help does not itself bind the terminal");
		helper.assertValueEqual(accepted.getIntOr(TerminalData.BAND_STAGE, 0), 0,
				"Accepting optional resource help leaves narrative reveal state unchanged");
		helper.assertTrue(accepted.getStringOr(TerminalData.ACCEPTED_ADVICE, "").contains("iron"),
				"The terminal still remembers that its accurate advice was followed");
		helper.succeed();
	}

	@GameTest
	public void heldTerminalRefreshesForNotificationsButNotLifecyclePolling(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		FrequencyWorldData data = FrequencyWorldData.get(helper.getLevel().getServer());
		ItemStack terminal = findTerminal(player);
		long projectedOnlineTicks = TerminalData.copyTag(terminal)
				.getLongOr(TerminalData.ONLINE_SURVIVAL_TICKS, 0L);
		data.updateTerminalRecord(player.getUUID(), record ->
				record.putLong(TerminalData.ONLINE_SURVIVAL_TICKS, projectedOnlineTicks + 1_000L));
		TerminalLifecycleService.ensureCarried(player, false);
		helper.assertValueEqual(TerminalData.copyTag(findTerminal(player))
				.getLongOr(TerminalData.ONLINE_SURVIVAL_TICKS, 0L), projectedOnlineTicks,
				"Routine carried-terminal polling does not rewrite the held item");

		TerminalSignalService.record(player, SignalBand.PUBLIC, "test_notification", 0, 1, true);
		CompoundTag notified = TerminalData.copyTag(findTerminal(player));
		helper.assertValueEqual(notified.getLongOr(TerminalData.ONLINE_SURVIVAL_TICKS, 0L),
				projectedOnlineTicks + 1_000L, "A real notification synchronizes the held item once");
		helper.assertTrue(notified.getIntOr(TerminalData.UNREAD_SIGNAL_COUNT, 0) > 0,
				"The notification projects the unread hand-state marker");
		helper.succeed();
	}

	@GameTest
	public void unknownBandRequiresNarrativePreludeInsteadOfTimeOrFirstAnomaly(GameTestHelper helper) {
		FrequencyWorldData data = FrequencyWorldData.get(helper.getLevel().getServer());
		ServerPlayer timed = helper.makeMockServerPlayerInLevel();
		data.updateTerminalRecord(timed.getUUID(), record -> {
			record.putInt(TerminalData.BAND_STAGE, 0);
			record.putLong(TerminalData.ONLINE_SURVIVAL_TICKS, 120_000L);
			record.putLong(TerminalData.NEXT_AMBIENT_ANOMALY_TICK, Long.MAX_VALUE);
		});
		TerminalSignalService.updatePlayerForTesting(timed);
		helper.assertValueEqual(data.terminalRecord(timed.getUUID()).orElseThrow()
				.getIntOr(TerminalData.BAND_STAGE, 0), 0, "Online time alone never reveals the unknown band");

		ServerPlayer anomalous = helper.makeMockServerPlayerInLevel();
		data.updateTerminalRecord(anomalous.getUUID(), record ->
				record.putLong(TerminalData.NEXT_AMBIENT_ANOMALY_TICK, Long.MAX_VALUE));
		TerminalAnomalyLogService.record(anomalous, "phantom_echo", 0, 1, 40, false);
		var anomalyRecord = data.terminalRecord(anomalous.getUUID()).orElseThrow();
		helper.assertValueEqual(anomalyRecord.getIntOr(TerminalData.BAND_STAGE, 0), 0,
				"A first anomaly alone does not reveal the unknown band");
		TerminalAnomalyLogService.record(anomalous, "light_dropout", 1, 1, 40, false);
		StoryProgressService.update(anomalous, data);
		helper.assertValueEqual(data.terminalRecord(anomalous.getUUID()).orElseThrow()
				.getIntOr(TerminalData.BAND_STAGE, 0), 0,
				"Repeated ambient anomalies still do not replace the survival signature scene");
		data.updateTerminalRecord(anomalous.getUUID(), record -> {
			record.putBoolean(TerminalData.BOUND, true);
			record.putBoolean(TerminalData.NIGHT_WITNESSED, true);
			record.putInt(TerminalData.SIGNATURE_SCENE_MASK, 1);
		});
		StoryProgressService.update(anomalous, data);
		anomalyRecord = data.terminalRecord(anomalous.getUUID()).orElseThrow();
		helper.assertValueEqual(anomalyRecord.getIntOr(TerminalData.BAND_STAGE, 0), 1,
				"Unknown reveals only after the deterministic survival signature scene begins");
		helper.assertValueEqual(TerminalSignalLog.entries(anomalyRecord, SignalBand.UNKNOWN).size(), 2,
				"Only the two witnessed anomalies are logged");
		helper.succeed();
	}

	@GameTest
	public void openingRecordsClearsUnreadEventsAcrossLegacyBands(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		FrequencyWorldData data = FrequencyWorldData.get(helper.getLevel().getServer());
		TerminalSignalService.record(player, SignalBand.UNKNOWN, "fracture", 0, 2, true);
		TerminalSignalService.record(player, SignalBand.WEATHER, "weather_changed", 1, 1, true);
		player.setItemInHand(InteractionHand.MAIN_HAND, findTerminal(player));
		TerminalRuntimeService.open(player, 0);
		TerminalRuntimeService.control(player, TerminalControlPayload.MARK_RECORDS_READ, 0);
		var record = data.terminalRecord(player.getUUID()).orElseThrow();
		helper.assertFalse(TerminalSignalLog.entries(record).stream().anyMatch(TerminalSignalLog.Entry::unread),
				"The unified records page marks every event read regardless of its legacy wire band");
		helper.assertValueEqual(TerminalSignalLog.unreadCount(record), 0, "Unread count is cleared atomically");
		TerminalRuntimeService.control(player, TerminalControlPayload.CLOSE, 0);
		helper.succeed();
	}

	@GameTest
	public void terminalV2RecordMigratesSignalsFilesTimesAndUnreadStateToV4(GameTestHelper helper) {
		CompoundTag legacy = new CompoundTag();
		legacy.putInt(TerminalData.SCHEMA_VERSION, 2);
		legacy.putString(TerminalData.OWNER_ID, java.util.UUID.randomUUID().toString());
		legacy.putBoolean(TerminalData.SECOND_CACHE_UNLOCKED, true);
		legacy.putString(TerminalData.FACILITY_EVIDENCE,
				"surface_shelter=north;field_observation=east;underground_mine_station=2;abandoned_warehouse=south");
		legacy.putBoolean(TerminalData.LOCAL_FILE_UNLOCKED, false);
		ListTag logs = new ListTag();
		CompoundTag anomaly = new CompoundTag();
		anomaly.putInt("sequence", 7);
		anomaly.putString("type", "fracture");
		anomaly.putLong("game_time", 18_000L);
		anomaly.putString("dimension", "minecraft:the_nether");
		anomaly.putLong("position", BlockPos.asLong(1, 2, 3));
		anomaly.putInt("variant", 2);
		anomaly.putInt("severity", 2);
		anomaly.putBoolean("unread", true);
		logs.add(anomaly);
		legacy.put(TerminalData.ANOMALY_LOGS, logs);
		legacy.putInt(TerminalData.ANOMALY_LOG_SEQUENCE, 7);
		legacy.putInt(TerminalData.UNREAD_ANOMALY_COUNT, 1);

		CompoundTag migrated = TerminalData.migrateRecord(legacy);
		helper.assertValueEqual(migrated.getIntOr(TerminalData.SCHEMA_VERSION, 0), 5, "Schema upgraded to v5");
		helper.assertValueEqual(migrated.getIntOr(TerminalData.ANOMALY_TIER, -1), 0,
				"Unbound legacy record has no anomaly tier");
		var entries = TerminalSignalLog.entries(migrated, SignalBand.UNKNOWN);
		helper.assertValueEqual(entries.size(), 1, "Legacy anomaly moved to unknown band");
		helper.assertValueEqual(entries.getFirst().sequence(), 7, "Legacy sequence retained");
		helper.assertValueEqual(entries.getFirst().dayTime(), 18_000L, "Best available day time retained");
		helper.assertValueEqual(TerminalSignalLog.clock(entries.getFirst().dayTime()), "00:00",
				"Migrated display clock");
		helper.assertTrue(entries.getFirst().unread(), "Unread state retained");
		var files = TerminalFileState.states(migrated);
		helper.assertValueEqual(files.size(), 7,
				"Legacy evidence migrates into four fragments and their locked parent without losing old files");
		helper.assertTrue(TerminalFileState.discovered(migrated, "encrypted_witness_file"),
				"The fragment parent is retained after the one-way legacy migration");
		helper.assertFalse(TerminalFileState.unlocked(migrated, "encrypted_witness_file"),
				"Legacy evidence alone does not silently unlock the complete file");
		helper.succeed();
	}

	@GameTest
	public void legacyWireChannelsStayBoundedWhileReadStateIsUnified(GameTestHelper helper) {
		CompoundTag record = new CompoundTag();
		for (int index = 0; index < 40; index++) TerminalSignalLog.append(record, SignalBand.UNKNOWN,
				"unknown_" + index, index, index, "minecraft:overworld", 0L, 0, 1, index == 39);
		for (int index = 0; index < 7; index++) TerminalSignalLog.append(record, SignalBand.WEATHER,
				"weather_" + index, index, index, "minecraft:overworld", 0L, 0, 1, index == 6);
		var unknown = TerminalSignalLog.entries(record, SignalBand.UNKNOWN);
		helper.assertValueEqual(unknown.size(), 32, "Unknown channel keeps 32 entries");
		helper.assertValueEqual(unknown.getFirst().type(), "unknown_39", "Newest unknown event first");
		helper.assertValueEqual(unknown.getLast().type(), "unknown_8", "Old unknown events trimmed");
		helper.assertValueEqual(TerminalSignalLog.entries(record, SignalBand.WEATHER).size(), 7,
				"Weather channel has an independent cap");
		helper.assertValueEqual(TerminalSignalLog.unreadCount(record), 2,
				"Unread events remain unified even when legacy wire ids differ");
		helper.assertTrue(TerminalSignalLog.markAllRead(record), "The records page clears all unread events");
		helper.assertValueEqual(TerminalSignalLog.unreadCount(record), 0, "No unread events remain");
		helper.succeed();
	}

	@GameTest
	public void discoveredLockedFileUnlocksInPlaceAndSortsByCatalog(GameTestHelper helper) {
		CompoundTag record = new CompoundTag();
		helper.assertTrue(TerminalFileState.discover(record, "encrypted_witness_file", 30, 40, false),
				"Witness file discovered locked");
		helper.assertTrue(TerminalFileState.discover(record, "maintenance_handoff", 10, 20, true),
				"Maintenance file discovered");
		helper.assertFalse(TerminalFileState.unlocked(record, "encrypted_witness_file"), "Witness remains locked");
		helper.assertValueEqual(TerminalFileState.states(record).getFirst().id(), "maintenance_handoff",
				"Files sort in catalog order");
		helper.assertTrue(TerminalFileState.discover(record, "encrypted_witness_file", 50, 60, true),
				"Witness unlock changes existing entry");
		helper.assertFalse(TerminalFileState.discover(record, "encrypted_witness_file", 70, 80, true),
				"Repeated unlock is idempotent");
		var witness = TerminalFileState.states(record).get(1);
		helper.assertTrue(witness.unlocked(), "Witness now unlocked");
		helper.assertValueEqual(witness.discoveredGameTime(), 30L, "Discovery time retained");
		helper.assertValueEqual(witness.unlockedGameTime(), 50L, "Unlock time recorded");
		helper.succeed();
	}

	@GameTest
	public void productionAndAcceleratedSchedulesStayInsideTheirContracts(GameTestHelper helper) {
		helper.assertValueEqual(AmbientAnomalyService.intervalTicks(5, 10, 0, true, false), 1_200L,
				"First post-binding random manifestation starts after sixty seconds");
		helper.assertValueEqual(AmbientAnomalyService.intervalTicks(5, 10, 0, false, false), 6_000L,
				"Minimum random interval");
		helper.assertValueEqual(AmbientAnomalyService.intervalTicks(5, 10, -1, false, false), 12_000L,
				"Maximum random interval");
		helper.assertValueEqual(AmbientAnomalyService.intervalTicks(5, 10, 0, true, true), 100L,
				"Accelerated first interval");
		helper.assertValueEqual(AmbientAnomalyService.intervalTicks(5, 10, 0, false, true), 200L,
				"Accelerated recurring interval");
		helper.succeed();
	}

	@GameTest
	public void directedLightAnomaliesArePrivateAndLeaveServerWorldUntouched(GameTestHelper helper) {
		ServerPlayer first = helper.makeMockServerPlayerInLevel();
		ServerPlayer second = helper.makeMockServerPlayerInLevel();
		FrequencyWorldData data = FrequencyWorldData.get(helper.getLevel().getServer());
		data.updateTerminalRecord(first.getUUID(), record -> record.putInt(TerminalData.BAND_STAGE, 1));
		data.updateTerminalRecord(second.getUUID(), record -> record.putInt(TerminalData.BAND_STAGE, 1));

		BlockPos chestPos = first.blockPosition().offset(2, 0, 2);
		helper.getLevel().setBlockAndUpdate(chestPos, Blocks.CHEST.defaultBlockState());
		Container chest = (Container) helper.getLevel().getBlockEntity(chestPos);
		chest.setItem(0, new ItemStack(Items.DIAMOND, 3));
		BlockPos solidPos = chestPos.below();
		helper.getLevel().setBlockAndUpdate(solidPos, Blocks.STONE.defaultBlockState());
		var chestState = helper.getLevel().getBlockState(chestPos);
		var solidState = helper.getLevel().getBlockState(solidPos);
		int signal = helper.getLevel().getBestNeighborSignal(chestPos);
		first.getInventory().setItem(8, new ItemStack(Items.IRON_INGOT, 7));

		for (int variant = 0; variant < AmbientAnomalyService.TYPES.length; variant++) {
			TerminalAnomalyLogService.record(first, AmbientAnomalyService.TYPES[variant], variant, 1, 80, true);
		}

		helper.assertValueEqual(TerminalSignalLog.entries(
				data.terminalRecord(first.getUUID()).orElseThrow(), SignalBand.UNKNOWN).size(),
				AmbientAnomalyService.TYPES.length,
				"Every formal catalog anomaly can be decoded in the target player's terminal history");
		helper.assertValueEqual(TerminalSignalLog.entries(
				data.terminalRecord(second.getUUID()).orElseThrow(), SignalBand.UNKNOWN).size(), 0,
				"A second player receives no shared anomaly record");
		helper.assertValueEqual(helper.getLevel().getBlockState(chestPos), chestState, "Container block state unchanged");
		helper.assertValueEqual(helper.getLevel().getBlockState(solidPos), solidState, "Solid block state unchanged");
		helper.assertValueEqual(helper.getLevel().getBestNeighborSignal(chestPos), signal, "Redstone state unchanged");
		helper.assertValueEqual(chest.getItem(0).getCount(), 3, "Container contents unchanged");
		helper.assertTrue(chest.getItem(0).is(Items.DIAMOND), "Container item identity unchanged");
		helper.assertValueEqual(first.getInventory().getItem(8).getCount(), 7, "Unrelated player items unchanged");
		helper.assertTrue(first.getInventory().getItem(8).is(Items.IRON_INGOT), "Unrelated player item identity unchanged");
		helper.succeed();
	}

	@GameTest
	public void strongAndWeakEventsShareOneBoundedNewestFirstLog(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		FrequencyWorldData data = FrequencyWorldData.get(helper.getLevel().getServer());
		data.updateTerminalRecord(player.getUUID(), record -> {
			record.putInt(TerminalData.BAND_STAGE, 1);
			record.putLong(TerminalData.NEXT_AMBIENT_ANOMALY_TICK, Long.MAX_VALUE);
			record.put(TerminalData.SIGNAL_EVENTS, new ListTag());
			record.putInt(TerminalData.UNREAD_SIGNAL_COUNT, 0);
		});
		TerminalAnomalyLogService.record(player, "phantom_echo", 0, 1, 80, false);
		TerminalAnomalyLogService.record(player, "experience_gap", 2, 2, 120, false);
		var record = data.terminalRecord(player.getUUID()).orElseThrow();
		var entries = TerminalSignalLog.entries(record, SignalBand.UNKNOWN);
		helper.assertValueEqual(entries.size(), 2, "Both explicit events share one signal log");
		helper.assertValueEqual(entries.getFirst().type(), "experience_gap", "Newest record first");
		helper.assertValueEqual(entries.getFirst().severity(), 2, "Strong event severity retained");
		helper.assertValueEqual(entries.get(1).severity(), 1, "Light event severity retained");
		helper.assertValueEqual(TerminalSignalLog.unreadCount(record), 2,
				"Unread state shared across strengths");
		helper.succeed();
	}

	@GameTest
	public void terminalV1RecordMigratesToV6SignalsAndFilesWithoutDroppingStoryFields(GameTestHelper helper) {
		CompoundTag legacy = new CompoundTag();
		legacy.putInt(TerminalData.SCHEMA_VERSION, 1);
		legacy.putString(TerminalData.OWNER_ID, java.util.UUID.randomUUID().toString());
		legacy.putBoolean(TerminalData.BOUND, true);
		legacy.putString(TerminalData.FACILITY_EVIDENCE, "surface_shelter=north");
		legacy.putLong(TerminalData.RIFT_POSITION, BlockPos.asLong(12, 34, 56));
		legacy.putString(TerminalData.ENDING_OUTCOME, "prevention_failed");
		CompoundTag migrated = TerminalData.migrateRecord(legacy);
		helper.assertValueEqual(migrated.getIntOr(TerminalData.SCHEMA_VERSION, 0), 6, "Terminal schema v6");
		helper.assertValueEqual(migrated.getIntOr(TerminalData.ANOMALY_TIER, 0), 1,
				"Bound legacy record starts at effective anomaly tier one");
		helper.assertTrue(migrated.getBooleanOr(TerminalData.ANOMALY_LEGACY_RAMP, false),
				"Bound legacy record enables gradual tier catch-up");
		helper.assertValueEqual(TerminalSignalLog.entries(migrated).size(), 0, "Legacy worlds start with empty signals");
		helper.assertTrue(migrated.getBooleanOr(TerminalData.BOUND, false), "Binding retained");
		helper.assertValueEqual(migrated.getStringOr(TerminalData.FACILITY_EVIDENCE, ""),
				"surface_shelter=north", "Evidence retained");
		helper.assertValueEqual(migrated.getLongOr(TerminalData.RIFT_POSITION, 0L), BlockPos.asLong(12, 34, 56),
				"Rift retained");
		helper.assertValueEqual(migrated.getStringOr(TerminalData.ENDING_OUTCOME, ""), "prevention_failed",
				"Ending retained");
		helper.succeed();
	}

	@GameTest(maxTicks = 80)
	public void doorCascadePermanentlyBreaksBothDoorHalvesWithoutDrops(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		AnomalyRuntimeService.interrupt(player, false);
		var lowerState = Blocks.OAK_DOOR.defaultBlockState()
				.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.LOWER);
		var doors = new java.util.ArrayList<BlockPos>();
		for (int distance : new int[] { 8, 12, 16, 20 }) {
			BlockPos lower = player.blockPosition().relative(Direction.EAST, distance);
			helper.getLevel().setBlockAndUpdate(lower.below(), Blocks.STONE.defaultBlockState());
			helper.getLevel().setBlock(lower, lowerState, 3);
			Blocks.OAK_DOOR.setPlacedBy(helper.getLevel(), lower, lowerState, player, new ItemStack(Items.OAK_DOOR));
			doors.add(lower);
		}
		helper.runAfterDelay(3, () -> {
			helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
					player.getBoundingBox().inflate(22.0)).stream()
					.filter(item -> item.getItem().is(Items.OAK_DOOR)).forEach(net.minecraft.world.entity.Entity::discard);
			helper.assertTrue(AmbientAnomalyService.trigger(player, "door_cascade", false),
					"Multiple ordinary doors within twenty blocks start the cascade");
			helper.runAfterDelay(12, () -> {
				helper.assertTrue(helper.getLevel().getBlockState(doors.getLast()).isAir(),
						"The farthest twenty-block door breaks first");
				helper.assertTrue(helper.getLevel().getBlockState(doors.getFirst()).getBlock() instanceof net.minecraft.world.level.block.DoorBlock,
						"A nearer door remains while the cascade advances inward");
			});
			helper.runAfterDelay(45, () -> {
				for (BlockPos lower : doors) {
					helper.assertTrue(helper.getLevel().getBlockState(lower).isAir(), "Lower half is permanently removed");
					helper.assertTrue(helper.getLevel().getBlockState(lower.above()).isAir(), "Upper half is permanently removed");
				}
				helper.assertFalse(helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
						player.getBoundingBox().inflate(22.0)).stream()
						.anyMatch(item -> item.getItem().is(Items.OAK_DOOR)), "Door produces no oak-door drop");
				AnomalyRuntimeService.interrupt(player, false);
				helper.succeed();
			});
		});
	}

	@GameTest(maxTicks = 40)
	public void localRuleCollapseIsClientOnlyAndLeavesServerStateUntouched(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		AnomalyRuntimeService.interrupt(player, false);
		player.getInventory().setItem(8, new ItemStack(Items.IRON_INGOT, 7));
		// Keep the fixture in this test structure's center. Player-relative edge positions can be
		// overwritten by an adjacent parallel GameTest's bounded world construction.
		BlockPos chestPos = helper.absolutePos(new BlockPos(5, 2, 5));
		player.snapTo(chestPos.getX() + 0.5D, chestPos.getY(), chestPos.getZ() + 3.5D, 180.0F, 0.0F);
		helper.getLevel().setBlockAndUpdate(chestPos, Blocks.BARREL.defaultBlockState());
		Container chest = (Container) helper.getLevel().getBlockEntity(chestPos);
		chest.setItem(0, new ItemStack(ModItems.TERMINATION_SPIKE));
		chest.setItem(1, new ItemStack(Items.DIAMOND, 20));
		helper.assertTrue(AmbientAnomalyService.trigger(player, "local_rule_collapse", false),
				"Client-only trace anomaly starts without a server lease");
		helper.runAfterDelay(8, () -> {
			Container currentChest = (Container) helper.getLevel().getBlockEntity(chestPos);
			helper.assertTrue(helper.getLevel().getBlockState(chestPos).is(Blocks.BARREL), "Container block remains real");
			helper.assertTrue(currentChest.getItem(0).is(ModItems.TERMINATION_SPIKE), "Story item remains untouched");
			helper.assertValueEqual(currentChest.getItem(1).getCount(), 20, "Container contents remain untouched");
			helper.assertValueEqual(player.getInventory().getItem(8).getCount(), 7, "Inventory remains untouched");
			AnomalyRuntimeService.interrupt(player, false);
			helper.succeed();
		});
	}

	@GameTest
	public void surfacePreconditionRejectsEmptyCandidatesAndSelectsExactNeighbor(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		BlockPos origin = player.blockPosition();
		for (Direction direction : new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST})
			helper.getLevel().setBlockAndUpdate(origin.relative(direction), Blocks.AIR.defaultBlockState());
		player.setYRot(0.0F);
		helper.assertTrue(AnomalyConditions.surfaceTarget(helper.getLevel(), player) == null,
				"No exact foot/eye collision target cancels surface fracture");
		BlockPos target = origin.relative(player.getDirection());
		helper.getLevel().setBlockAndUpdate(target, Blocks.STONE.defaultBlockState());
		helper.assertValueEqual(AnomalyConditions.surfaceTarget(helper.getLevel(), player), target,
				"Exact one-block front collision surface is selected");
		helper.succeed();
	}

	@GameTest(maxTicks = 40)
	public void anomalyLogsOnlyAfterValidatedCleanupAndOnlyOnce(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		AnomalyRuntimeService.interrupt(player, false);
		FrequencyWorldData data = FrequencyWorldData.get(helper.getLevel().getServer());
		int before = data.terminalRecord(player.getUUID()).orElseThrow()
				.getIntOr(TerminalData.SIGNAL_EVENT_SEQUENCE, 0);
		helper.assertTrue(AnomalyGameTestBridge.start(player, "local_rule_collapse", 0xDD070DDL, 4),
				"Accelerated anomaly starts");
		var active = AnomalyRuntimeService.active(player);
		helper.assertValueEqual(data.terminalRecord(player.getUUID()).orElseThrow()
				.getIntOr(TerminalData.SIGNAL_EVENT_SEQUENCE, 0), before, "Starting creates no terminal log sequence");
		helper.assertFalse(AnomalyRuntimeService.complete(player,
				new AnomalyCompleteC2S(active.instanceId(), AnomalyCompletionStatus.COMPLETED)),
				"Completion before the earliest tick is rejected");
		helper.runAfterDelay(5, () -> {
			helper.assertTrue(AnomalyRuntimeService.complete(player,
					new AnomalyCompleteC2S(active.instanceId(), AnomalyCompletionStatus.COMPLETED)),
					"Completion after cleanup is accepted");
			helper.assertTrue(active.terminalRecorded(), "Completed instance marks its terminal record exactly once");
			helper.assertFalse(AnomalyRuntimeService.complete(player,
					new AnomalyCompleteC2S(active.instanceId(), AnomalyCompletionStatus.COMPLETED)),
					"Duplicate completion is rejected");
			helper.assertTrue(active.terminalRecorded(), "Duplicate completion cannot clear or repeat the one-time marker");
			helper.succeed();
		});
	}

	@GameTest
	public void debugPanelRejectsArbitraryAnomalyIds(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		FrequencyWorldData data = FrequencyWorldData.get(helper.getLevel().getServer());
		helper.assertTrue(DebugPanelService.setEnabled(player, true), "Debug permission enabled for this player");
		int before = TerminalSignalLog.entries(data.terminalRecord(player.getUUID()).orElseThrow(), SignalBand.UNKNOWN).size();
		DebugPanelService.handle(player, new DebugActionPayload("anomaly", "minecraft:kill", 1));
		int after = TerminalSignalLog.entries(data.terminalRecord(player.getUUID()).orElseThrow(), SignalBand.UNKNOWN).size();
		helper.assertValueEqual(after, before, "Arbitrary anomaly IDs never reach the server dispatcher");
		helper.succeed();
	}

	@GameTest
	public void worldDecayUsesAnomalyTiersAndSupportsStageFiveOverride(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		FrequencyWorldData data = FrequencyWorldData.get(helper.getLevel().getServer());
		data.updateTerminalRecord(player.getUUID(), record -> {
			record.putBoolean(TerminalData.BOUND, true);
			record.putInt(TerminalData.ANOMALY_TIER, 1);
		});
		CompoundTag record = data.terminalRecord(player.getUUID()).orElseThrow();
		helper.assertValueEqual(WorldDecayService.stage(data, record), 1, "Binding-level anomaly starts decay stage one");
		data.updateNarrativeState(tag -> tag.putInt("decay_stage_override", 5));
		helper.assertValueEqual(WorldDecayService.stage(data, record), 5, "Debug override reaches decay stage five");
		data.updateNarrativeState(tag -> tag.remove("decay_stage_override"));
		helper.succeed();
	}

	@Override
	public void invokeTestMethod(GameTestHelper helper, Method method) throws ReflectiveOperationException {
		method.invoke(this, helper);
	}

	private static ItemStack findTerminal(ServerPlayer player) {
		for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
			ItemStack stack = player.getInventory().getItem(slot);
			if (stack.is(ModItems.OLD_TERMINAL)) return stack;
		}
		throw new AssertionError("Mock player had no issued terminal");
	}
}
