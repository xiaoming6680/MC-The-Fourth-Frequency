package com.xm.thefourthfrequency.terminal;

import com.xm.thefourthfrequency.content.TerminalData;
import com.xm.thefourthfrequency.networking.TerminalAnomalyLoggedS2C;
import com.xm.thefourthfrequency.world.FrequencyWorldData;
import com.xm.thefourthfrequency.world.StoryProgressService;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;

public final class TerminalAnomalyLogService {
	private TerminalAnomalyLogService() {
	}

	public static void record(ServerPlayer player, String type, int variant, int severity,
			int durationTicks, boolean present) {
		record(player, type, Math.max(1, severity), variant, severity, durationTicks, present,
				player.level().getGameTime() ^ player.getUUID().getLeastSignificantBits());
	}

	public static void record(ServerPlayer player, String type, int tier, int variant, int severity,
			int durationTicks, boolean present, long stableSeed) {
		FrequencyWorldData data = FrequencyWorldData.get(player.level().getServer());
		StoryProgressService.recordAnomaly(player, type);
		long now = player.level().getGameTime();
		data.updateTerminalRecord(player.getUUID(), tag -> {
			TerminalSignalLog.append(tag, SignalBand.UNKNOWN, type, now, player.level().getDayTime(),
					player.level().dimension().identifier().toString(), player.blockPosition().asLong(),
					variant, severity, true);
			tag.putString(TerminalData.ACTIVE_ANOMALY_ID, type);
			tag.putLong(TerminalData.ACTIVE_ANOMALY_UNTIL, now + durationTicks);
		});
		TerminalRuntimeService.synchronizeProjection(player);
		TerminalRuntimeService.refresh(player);
	}

	/** Writes the single authoritative terminal entry after the active presentation has restored state. */
	public static void recordCompleted(ServerPlayer player, ActiveAnomaly anomaly) {
		FrequencyWorldData data = FrequencyWorldData.get(player.level().getServer());
		StoryProgressService.recordAnomaly(player, anomaly.anomalyId());
		long now = player.level().getGameTime();
		data.updateTerminalRecord(player.getUUID(), tag -> {
			TerminalSignalLog.append(tag, SignalBand.UNKNOWN, anomaly.anomalyId(), now,
					player.level().getDayTime(), player.level().dimension().identifier().toString(),
					player.blockPosition().asLong(), anomaly.variant(), Math.min(2, Math.max(1, anomaly.tier() - 1)), true);
			tag.putString(TerminalData.ACTIVE_ANOMALY_ID, "none");
			tag.putLong(TerminalData.ACTIVE_ANOMALY_UNTIL, 0L);
		});
		ServerPlayNetworking.send(player, new TerminalAnomalyLoggedS2C(anomaly.instanceId()));
		TerminalRuntimeService.synchronizeProjection(player);
		TerminalRuntimeService.refresh(player);
	}
}
