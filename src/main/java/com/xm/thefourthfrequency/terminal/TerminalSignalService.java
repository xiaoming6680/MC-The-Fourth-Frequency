package com.xm.thefourthfrequency.terminal;

import com.xm.thefourthfrequency.content.TerminalData;
import com.xm.thefourthfrequency.correction.CorrectionState;
import com.xm.thefourthfrequency.facility.FacilityService;
import com.xm.thefourthfrequency.narrative.TerminalFileState;
import com.xm.thefourthfrequency.world.FrequencyWorldData;
import com.xm.thefourthfrequency.world.TerminalLifecycleService;
import com.xm.thefourthfrequency.world.FragmentInvestigationService;
import com.xm.thefourthfrequency.world.SurvivalMilestone;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class TerminalSignalService {
	private static final Map<String, String> FACILITY_FILES = Map.of(
			"surface_shelter", "surface_shelter_record",
			"field_observation", "field_observation_record",
			"underground_mine_station", "underground_mine_record",
			"abandoned_warehouse", "abandoned_warehouse_record",
			"transport_node", "encrypted_witness_file");
	private static boolean initialized;

	private TerminalSignalService() { }

	public static void initialize() {
		if (initialized) return;
		initialized = true;
		ServerTickEvents.END_SERVER_TICK.register(TerminalSignalService::tick);
	}

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % 20 != 0) return;
		FrequencyWorldData data = FrequencyWorldData.get(server);
		for (ServerPlayer player : server.getPlayerList().getPlayers()) updatePlayer(player, data);
	}

	private static void updatePlayer(ServerPlayer player, FrequencyWorldData data) {
		CompoundTag before = data.terminalRecord(player.getUUID()).orElse(null);
		if (before == null) return;
		long now = player.level().getGameTime();
		long dayTime = player.level().getDayTime();
		List<String> fileNotifications = new ArrayList<>();
		List<FragmentInvestigationService.SharedReceipt> sharedReceipts = new ArrayList<>();
		boolean[] projectionChanged = {false};
		data.updateTerminalRecord(player.getUUID(), tag -> {
			if (player.isAlive() && !player.isSpectator()) {
				long survived = Math.max(0L, tag.getLongOr(TerminalData.ONLINE_SURVIVAL_TICKS, 0L)) + 20L;
				tag.putLong(TerminalData.ONLINE_SURVIVAL_TICKS, survived);
			}

			int weather = player.level().isThundering() ? 2 : player.level().isRaining() ? 1 : 0;
			int previousWeather = tag.getIntOr(TerminalData.LAST_SIGNAL_WEATHER, weather);
			if (previousWeather >= 0 && weather != previousWeather) {
				append(tag, player, SignalBand.WEATHER, "weather_changed", weather, 1, true);
				projectionChanged[0] = true;
			}
			tag.putInt(TerminalData.LAST_SIGNAL_WEATHER, weather);
			String dimension = player.level().dimension().identifier().toString();
			String previousDimension = tag.getStringOr(TerminalData.LAST_SIGNAL_DIMENSION, dimension);
			if (!previousDimension.isBlank() && !previousDimension.equals(dimension)) {
				append(tag, player, SignalBand.WEATHER, "dimension_changed", 0, 1, true);
				projectionChanged[0] = true;
			}
			tag.putString(TerminalData.LAST_SIGNAL_DIMENSION, dimension);

			if (tag.getBooleanOr(TerminalData.BOUND, false))
				ensureFile(tag, "maintenance_handoff", true, now, dayTime, fileNotifications);
			if (tag.getBooleanOr(TerminalData.SECOND_CACHE_UNLOCKED, false))
				ensureFile(tag, "recovered_fragment", true, now, dayTime, fileNotifications);
			for (var entry : data.narrativeState().contains("facilities") ? FACILITY_FILES.entrySet() : Set.<Map.Entry<String, String>>of()) {
				if (insideCompletedFacility(player, data, entry.getKey())) {
					boolean unlocked = !entry.getKey().equals("transport_node")
							|| tag.getBooleanOr(TerminalData.LOCAL_FILE_UNLOCKED, false);
					ensureFile(tag, entry.getValue(), unlocked, now, dayTime, fileNotifications);
					String event = "facility_" + entry.getKey();
					if (!TerminalSignalLog.containsType(tag, event)) {
						append(tag, player, SignalBand.PUBLIC, event, 0, 1, true);
						projectionChanged[0] = true;
					}
				}
			}
			projectionChanged[0] |= FragmentInvestigationService.synchronizeSharedFiles(tag, player, data, sharedReceipts);
			projectionChanged[0] |= FragmentInvestigationService.ensureSignalMarkers(tag, player);
			projectionChanged[0] |= FragmentInvestigationService.appendCandidateLogs(tag, player, data);
			if (tag.getBooleanOr(TerminalData.LOCAL_FILE_UNLOCKED, false)
					&& TerminalFileState.discovered(tag, "encrypted_witness_file"))
				ensureFile(tag, "encrypted_witness_file", true, now, dayTime, fileNotifications);
			if (CorrectionState.active(data))
				ensureFile(tag, "correction_response_record", true, now, dayTime, fileNotifications);
			if (tag.getBooleanOr(TerminalData.RIFT_OBSERVED, false))
				ensureFile(tag, "overworld_fracture_record", true, now, dayTime, fileNotifications);
			if (tag.getBooleanOr(TerminalData.CONTINUITY_LEARNED, false)
					&& tag.getBooleanOr(TerminalData.NETHER_RIFT_OBSERVED, false))
				ensureFile(tag, "continuity_report", true, now, dayTime, fileNotifications);
			if (SurvivalMilestone.THREW_EYE.present(
					tag.getIntOr(TerminalData.SURVIVAL_MILESTONE_MASK, 0)))
				ensureFile(tag, "body_mapping_warning", true, now, dayTime, fileNotifications);
			if (tag.getIntOr(TerminalData.ENDING_VERSION, 0) > 0)
				ensureFile(tag, "permanent_aftermath_record", true, now, dayTime, fileNotifications);

			recordStageEvents(tag, player, projectionChanged);
			if (!fileNotifications.isEmpty()) projectionChanged[0] = true;
		});
		if (projectionChanged[0]) {
			TerminalLifecycleService.ensureCarried(player, false);
			TerminalRuntimeService.synchronizeProjection(player);
			TerminalRuntimeService.refresh(player);
		}
		for (String id : fileNotifications) player.displayClientMessage(
				Component.translatable("message.thefourthfrequency.file.discovered",
						Component.translatable("terminal.thefourthfrequency.file." + id + ".title")), true);
		for (FragmentInvestigationService.SharedReceipt receipt : sharedReceipts) player.displayClientMessage(
				receipt.own() ? Component.translatable("message.thefourthfrequency.fragment.shared", receipt.fragment())
						: Component.translatable("message.thefourthfrequency.fragment.received",
							receipt.discovererName(), receipt.fragment()), true);
	}

	public static void updatePlayerForTesting(ServerPlayer player) {
		updatePlayer(player, FrequencyWorldData.get(player.level().getServer()));
	}

	public static void revealFromAnomaly(ServerPlayer player) {
		// Kept as a source-compatible hook: anomalies now feed the prelude gate instead of revealing the band.
	}

	public static void record(ServerPlayer player, SignalBand band, String type, int variant,
			int severity, boolean unread) {
		FrequencyWorldData data = FrequencyWorldData.get(player.level().getServer());
		if (data.terminalRecord(player.getUUID()).isEmpty()) return;
		data.updateTerminalRecord(player.getUUID(), tag -> append(tag, player, band, type, variant, severity, unread));
		TerminalRuntimeService.synchronizeProjection(player);
		TerminalRuntimeService.refresh(player);
	}

	private static void recordStageEvents(CompoundTag tag, ServerPlayer player, boolean[] changed) {
		int bandStage = tag.getIntOr(TerminalData.BAND_STAGE, 0);
		if (bandStage >= 2) changed[0] |= appendOnce(tag, player, SignalBand.UNKNOWN, "terminal_bound", 0);
		if (tag.getIntOr(TerminalData.PLOT_STAGE, 1) >= 3)
			changed[0] |= appendOnce(tag, player, SignalBand.UNKNOWN, "investigation_stage", 0);
		if (CorrectionState.active(FrequencyWorldData.get(player.level().getServer())))
			changed[0] |= appendOnce(tag, player, SignalBand.UNKNOWN, "correction_active", 0);
		if (tag.getBooleanOr(TerminalData.RIFT_OBSERVED, false))
			changed[0] |= appendOnce(tag, player, SignalBand.UNKNOWN, "overworld_fracture_observed", 0);
		if (tag.getBooleanOr(TerminalData.CONTINUITY_LEARNED, false))
			changed[0] |= appendOnce(tag, player, SignalBand.UNKNOWN, "continuity_learned", 0);
		int milestones = tag.getIntOr(TerminalData.SURVIVAL_MILESTONE_MASK, 0);
		if (SurvivalMilestone.HOME.present(milestones))
			changed[0] |= appendOnce(tag, player, SignalBand.PUBLIC, "survival_home", 0);
		if (SurvivalMilestone.IRON.present(milestones))
			changed[0] |= appendOnce(tag, player, SignalBand.MINING, "survival_iron", 0);
		if (SurvivalMilestone.RETURNED_NETHER.present(milestones))
			changed[0] |= appendOnce(tag, player, SignalBand.WEATHER, "survival_nether_return", 0);
		if (SurvivalMilestone.THREW_EYE.present(milestones))
			changed[0] |= appendOnce(tag, player, SignalBand.UNKNOWN, "survival_eye", 0);
		if (SurvivalMilestone.FOUND_STRONGHOLD.present(milestones))
			changed[0] |= appendOnce(tag, player, SignalBand.UNKNOWN, "survival_stronghold", 0);
		if (tag.getIntOr(TerminalData.ENDING_VERSION, 0) > 0)
			changed[0] |= appendOnce(tag, player, SignalBand.UNKNOWN, "ending_recorded", 0);
	}

	private static boolean appendOnce(CompoundTag tag, ServerPlayer player, SignalBand band, String type, int variant) {
		if (TerminalSignalLog.containsType(tag, type)) return false;
		append(tag, player, band, type, variant, 1, true);
		return true;
	}

	private static void append(CompoundTag tag, ServerPlayer player, SignalBand band, String type,
			int variant, int severity, boolean unread) {
		TerminalSignalLog.append(tag, band, type, player.level().getGameTime(), player.level().getDayTime(),
				player.level().dimension().identifier().toString(), player.blockPosition().asLong(),
				variant, severity, unread);
	}

	private static void ensureFile(CompoundTag tag, String id, boolean unlocked, long now, long dayTime,
			List<String> notifications) {
		boolean existed = TerminalFileState.discovered(tag, id);
		if (TerminalFileState.discover(tag, id, now, dayTime, unlocked) && !existed) notifications.add(id);
	}

	private static boolean insideCompletedFacility(ServerPlayer player, FrequencyWorldData data, String id) {
		if (player.level() != player.level().getServer().overworld()) return false;
		CompoundTag state = FacilityService.facilityState(data, id).orElse(null);
		if (state == null || !state.getBooleanOr("complete", false)) return false;
		BlockPos origin = FacilityService.facilityPosition(data, id).orElse(null);
		return origin != null && player.distanceToSqr(origin.getCenter()) <= 14.0D * 14.0D;
	}
}
