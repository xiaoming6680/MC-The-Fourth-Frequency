package com.xm.thefourthfrequency.terminal;

import com.xm.thefourthfrequency.content.TerminalData;
import com.xm.thefourthfrequency.ending.EndingState;
import com.xm.thefourthfrequency.networking.AnomalyCompleteC2S;
import com.xm.thefourthfrequency.networking.AnomalyPhaseS2C;
import com.xm.thefourthfrequency.networking.AnomalyStartS2C;
import com.xm.thefourthfrequency.world.FrequencyWorldData;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Owns the one-active-anomaly-per-player invariant and all completion validation. */
public final class AnomalyRuntimeService {
	private static final int TIMEOUT_GRACE_TICKS = 200;
	private static final Map<ServerPlayer, RuntimeEntry> ACTIVE = new HashMap<>();
	private static boolean initialized;

	private AnomalyRuntimeService() { }

	public static void initialize() {
		if (initialized) return;
		initialized = true;
		ServerTickEvents.END_SERVER_TICK.register(AnomalyRuntimeService::tick);
		ServerPlayerEvents.LEAVE.register(player -> interrupt(player, false));
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> interrupt(oldPlayer, false));
		ServerPlayerEvents.JOIN.register(AnomalyRuntimeService::clearStaleProjection);
	}

	public static boolean start(ServerPlayer player, AnomalyDefinition definition, int variant, long seed,
			int durationTicks, Anchor anchor, Runnable cleanup) {
		if (ACTIVE.containsKey(player) || durationTicks < 1) return false;
		long now = player.level().getGameTime();
		ActiveAnomaly anomaly = new ActiveAnomaly(UUID.randomUUID(), player.getUUID(), definition.id(),
				definition.tier(), variant, seed, now, durationTicks, TIMEOUT_GRACE_TICKS);
		ACTIVE.put(player, new RuntimeEntry(anomaly, cleanup == null ? () -> { } : cleanup));
		FrequencyWorldData.get(player.level().getServer()).updateTerminalRecord(player.getUUID(), tag -> {
			tag.putString(TerminalData.ACTIVE_ANOMALY_ID, definition.id());
			tag.putLong(TerminalData.ACTIVE_ANOMALY_UNTIL, now + durationTicks);
		});
		ServerPlayNetworking.send(player, new AnomalyStartS2C(anomaly.instanceId(), anomaly.anomalyId(),
				anomaly.tier(), anomaly.variant(), anomaly.seed(), now, durationTicks, anchor != null,
				anchor == null ? "" : anchor.dimension(), anchor == null ? 0L : anchor.position().asLong()));
		anomaly.markRunning();
		return true;
	}

	public static boolean phase(ServerPlayer player, String phase, boolean blackout, int remainingTicks, Anchor anchor) {
		RuntimeEntry entry = ACTIVE.get(player);
		if (entry == null || entry.anomaly.stage() == ActiveAnomaly.Stage.COMPLETED) return false;
		ServerPlayNetworking.send(player, new AnomalyPhaseS2C(entry.anomaly.instanceId(),
				entry.anomaly.nextPhaseSequence(), phase, blackout, Math.max(0, remainingTicks), anchor != null,
				anchor == null ? "" : anchor.dimension(), anchor == null ? 0L : anchor.position().asLong()));
		return true;
	}

	public static boolean complete(ServerPlayer player, AnomalyCompleteC2S payload) {
		RuntimeEntry entry = ACTIVE.get(player);
		if (entry == null) return false;
		if (!entry.anomaly.targetPlayerId().equals(player.getUUID())
				|| !entry.anomaly.instanceId().equals(payload.instanceId())) return false;
		long now = player.level().getGameTime();
		if (now < entry.anomaly.earliestCompletionTick()) return false;
		cleanup(entry);
		if (!entry.anomaly.acceptCompletion(player.getUUID(), payload.instanceId(), now, payload.status())) return false;
		finalizeEntry(player, entry);
		return true;
	}

	public static void interrupt(ServerPlayer player, boolean clearSuspension) {
		RuntimeEntry entry = ACTIVE.remove(player);
		if (entry != null) {
			cleanup(entry);
			entry.anomaly.interrupt();
		}
		FrequencyWorldData data = FrequencyWorldData.get(player.level().getServer());
		data.updateTerminalRecord(player.getUUID(), tag -> {
			tag.putString(TerminalData.ACTIVE_ANOMALY_ID, "none");
			tag.putLong(TerminalData.ACTIVE_ANOMALY_UNTIL, 0L);
			if (clearSuspension) tag.putBoolean(TerminalData.ANOMALIES_SUSPENDED, true);
		});
	}

	public static void interruptAll(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) interrupt(player, false);
		ACTIVE.clear();
	}

	public static ActiveAnomaly active(ServerPlayer player) {
		RuntimeEntry entry = ACTIVE.get(player);
		return entry == null ? null : entry.anomaly;
	}

	private static void tick(MinecraftServer server) {
		if (!EndingState.activeAnomaliesAllowed(FrequencyWorldData.get(server))) {
			interruptAll(server);
			return;
		}
		for (var mapEntry : Map.copyOf(ACTIVE).entrySet()) {
			ServerPlayer player = mapEntry.getKey();
			RuntimeEntry entry = mapEntry.getValue();
			if (player.isRemoved()) {
				cleanup(entry);
				entry.anomaly.interrupt();
				ACTIVE.remove(player);
				continue;
			}
			long now = player.level().getGameTime();
			if (now >= entry.anomaly.earliestCompletionTick()) cleanup(entry);
			if (now >= entry.anomaly.timeoutTick()) {
				entry.anomaly.interrupt();
				ACTIVE.remove(player);
				clearProjection(player);
			}
		}
	}

	private static void cleanup(RuntimeEntry entry) {
		if (!entry.anomaly.beginCleanup()) return;
		entry.cleanup.run();
	}

	private static void finalizeEntry(ServerPlayer player, RuntimeEntry entry) {
		ACTIVE.remove(player);
		clearProjection(player);
		if (entry.anomaly.markTerminalRecorded())
			TerminalAnomalyLogService.recordCompleted(player, entry.anomaly);
	}

	private static void clearProjection(ServerPlayer player) {
		FrequencyWorldData.get(player.level().getServer()).updateTerminalRecord(player.getUUID(), tag -> {
			tag.putString(TerminalData.ACTIVE_ANOMALY_ID, "none");
			tag.putLong(TerminalData.ACTIVE_ANOMALY_UNTIL, 0L);
		});
	}

	private static void clearStaleProjection(ServerPlayer player) {
		if (!ACTIVE.containsKey(player)) clearProjection(player);
	}

	public record Anchor(String dimension, BlockPos position) {
		public Anchor {
			if (dimension == null || dimension.isBlank()) throw new IllegalArgumentException("anchor dimension");
			if (position == null) throw new IllegalArgumentException("anchor position");
		}
	}

	private static final class RuntimeEntry {
		private final ActiveAnomaly anomaly;
		private final Runnable cleanup;
		private RuntimeEntry(ActiveAnomaly anomaly, Runnable cleanup) {
			this.anomaly = anomaly;
			this.cleanup = cleanup;
		}
	}
}
