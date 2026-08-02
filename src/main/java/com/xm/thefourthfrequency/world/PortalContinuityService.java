package com.xm.thefourthfrequency.world;

import com.xm.thefourthfrequency.audio.AudioService;
import com.xm.thefourthfrequency.content.TerminalData;
import com.xm.thefourthfrequency.networking.PrivateAnomalyPayload;
import com.xm.thefourthfrequency.terminal.SignalBand;
import com.xm.thefourthfrequency.terminal.TerminalSignalService;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

/** Records a bound terminal's uninterrupted identity across real Overworld/Nether travel. */
public final class PortalContinuityService {
	private static boolean initialized;

	private PortalContinuityService() {
	}

	public static void initialize() {
		if (initialized) return;
		initialized = true;
		ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register(
				PortalContinuityService::afterPlayerChangeWorld);
	}

	private static void afterPlayerChangeWorld(ServerPlayer player, ServerLevel origin, ServerLevel destination) {
		boolean enteringNether = origin.dimension() == Level.OVERWORLD
				&& destination.dimension() == Level.NETHER;
		boolean returningOverworld = origin.dimension() == Level.NETHER
				&& destination.dimension() == Level.OVERWORLD;
		if (!enteringNether && !returningOverworld) return;

		FrequencyWorldData data = FrequencyWorldData.get(destination.getServer());
		CompoundTag record = data.terminalRecord(player.getUUID()).orElse(null);
		if (!eligible(record)) return;

		String originId = origin.dimension().identifier().toString();
		String destinationId = destination.dimension().identifier().toString();
		data.updateTerminalRecord(player.getUUID(), tag -> {
			int transitions = tag.getIntOr(TerminalData.PORTAL_TRANSITIONS, 0) + 1;
			tag.putInt(TerminalData.PORTAL_TRANSITIONS, transitions);
			tag.putBoolean(TerminalData.CONTINUITY_LEARNED, true);
			tag.putInt(TerminalData.CONTINUITY_CONFIDENCE, Math.min(100, transitions * 25));
			tag.putString(TerminalData.LAST_PORTAL_ORIGIN, originId);
			tag.putString(TerminalData.LAST_PORTAL_DESTINATION, destinationId);
		});
		TerminalLifecycleService.recordCurrentDimension(player);
		TerminalLifecycleService.ensureCarried(player, false);
		String event = enteringNether ? "continuity" : "return";
		TerminalSignalService.record(player, SignalBand.UNKNOWN, event, 0, 1, true);
		sendPrivatePresentation(player, event);
	}

	private static boolean eligible(CompoundTag record) {
		return record != null && record.getBooleanOr(TerminalData.BOUND, false)
				&& SurvivalMilestone.PREPARED_NETHER.present(
						record.getIntOr(TerminalData.SURVIVAL_MILESTONE_MASK, 0));
	}

	private static void sendPrivatePresentation(ServerPlayer player, String event) {
		FrequencyWorldData data = FrequencyWorldData.get(player.level().getServer());
		CompoundTag before = data.terminalRecord(player.getUUID()).orElseThrow();
		int count = before.getIntOr(TerminalData.PRIVATE_ANOMALY_COUNT, 0) + 1;
		int variant = Math.floorMod(before.getIntOr(TerminalData.CACHE_VARIANT, 0) + count - 1, 4);
		data.updateTerminalRecord(player.getUUID(), tag -> {
			tag.putInt(TerminalData.PRIVATE_ANOMALY_COUNT, count);
			tag.putInt(TerminalData.PRIVATE_ANOMALY_VARIANT, variant);
		});
		ServerPlayNetworking.send(player, new PrivateAnomalyPayload(event, variant));
		AudioService.play(player.level(), player.blockPosition(), AudioService.Cue.FOURTH_BAND);
	}

}
