package com.xm.thefourthfrequency.pursuit;

import com.xm.thefourthfrequency.content.TerminalData;
import com.xm.thefourthfrequency.networking.PursuitPresentationPayload;
import com.xm.thefourthfrequency.terminal.SignalBand;
import com.xm.thefourthfrequency.terminal.TerminalNoticeService;
import com.xm.thefourthfrequency.terminal.TerminalRuntimeService;
import com.xm.thefourthfrequency.terminal.TerminalSignalLog;
import com.xm.thefourthfrequency.world.FrequencyWorldData;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Authoritative source/mirror teleport transaction and disconnect recovery. */
public final class PursuitSessionService {
	private static final long INTERRUPTED_RETRY_TICKS = 5L * 60L * 20L;
	private static final Map<UUID, PendingTransfer> PENDING_TRANSFERS = new HashMap<>();
	private static boolean initialized;

	private PursuitSessionService() {
	}

	public static void initialize() {
		if (initialized) return;
		initialized = true;
		ServerPlayerEvents.JOIN.register(PursuitSessionService::recoverOnJoin);
		ServerPlayerEvents.LEAVE.register(PursuitSessionService::deferDisconnectedSession);
		ServerTickEvents.END_SERVER_TICK.register(PursuitSessionService::tickPendingTransfers);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> PENDING_TRANSFERS.clear());
	}

	public static boolean enterEmptyMirror(ServerPlayer player, PursuitSlotManager.Lease lease, int form) {
		return enterEmptyMirror(player, lease, form, false);
	}

	public static boolean enterEmptyMirror(ServerPlayer player, PursuitSlotManager.Lease lease, int form,
			boolean debugSession) {
		if (PursuitDimensions.isMirror(player.level()) || !lease.playerId().equals(player.getUUID())) return false;
		ServerLevel source = (ServerLevel) player.level();
		ServerLevel mirror = source.getServer().getLevel(lease.dimension());
		if (mirror == null) return false;
		var family = PursuitDimensions.sourceFamily(source.dimension());
		if (family.isEmpty() || family.get() != lease.family()) return false;

		BlockPos origin = player.blockPosition();
		String sessionId = UUID.randomUUID().toString();
		int normalizedForm = Math.clamp(form, 1, 5);
		FrequencyWorldData data = FrequencyWorldData.get(source.getServer());
		data.updateTerminalRecord(player.getUUID(), record -> {
			record.putBoolean(TerminalData.PURSUIT_ACTIVE, true);
			record.putString(TerminalData.PURSUIT_SESSION_ID, sessionId);
			record.putString(TerminalData.PURSUIT_SESSION_PHASE, "warning");
			record.putInt(TerminalData.PURSUIT_SESSION_FORM, normalizedForm);
			record.putBoolean(TerminalData.PURSUIT_SESSION_DEBUG, debugSession);
			if (!debugSession) {
				record.putInt(TerminalData.PURSUIT_TUTORIAL_WARNING_MASK, PursuitTutorialPolicy.mark(
						record.getIntOr(TerminalData.PURSUIT_TUTORIAL_WARNING_MASK, 0), normalizedForm));
			}
			record.putBoolean(TerminalData.PURSUIT_WARNING_RECORDS_REDIRECT, true);
			record.putString(TerminalData.PURSUIT_SOURCE_DIMENSION, source.dimension().identifier().toString());
			record.putLong(TerminalData.PURSUIT_SOURCE_POSITION, origin.asLong());
			record.putDouble(TerminalData.PURSUIT_SOURCE_YAW, player.getYRot());
			record.putDouble(TerminalData.PURSUIT_SOURCE_PITCH, player.getXRot());
			record.putString(TerminalData.PURSUIT_MIRROR_DIMENSION, lease.dimension().identifier().toString());
			record.putInt(TerminalData.PURSUIT_MIRROR_SLOT, lease.slot());
			record.putLong(TerminalData.PURSUIT_SESSION_STARTED_TICK, source.getGameTime());
			TerminalSignalLog.append(record, SignalBand.UNKNOWN, "pursuit_warning_" + normalizedForm,
					source.getGameTime(), source.getDayTime(), source.dimension().identifier().toString(),
					origin.asLong(), normalizedForm, 2, true);
		});
		PENDING_TRANSFERS.put(player.getUUID(), new PendingTransfer(player.getUUID(), sessionId,
				normalizedForm, source.dimension(), lease, source.getServer().getTickCount()));
		TerminalNoticeService.pursuitWarning(player);
		TerminalRuntimeService.synchronizeAttentionProjection(player, data);
		TerminalRuntimeService.refresh(player);
		sendPresentation(player, sessionId, PursuitPresentationPayload.WARNING, normalizedForm);
		return true;
	}

	public static boolean returnToSource(ServerPlayer player, String resolution) {
		PENDING_TRANSFERS.remove(player.getUUID());
		PursuitFormController.interrupt(player);
		PursuitSnapshotBuilder.cancel(player.getUUID());
		FrequencyWorldData data = FrequencyWorldData.get(player.level().getServer());
		var record = data.terminalRecord(player.getUUID()).orElse(null);
		if (record == null) return false;
		ServerLevel source = PursuitDimensions.sourceLevel(player.level().getServer(),
				record.getStringOr(TerminalData.PURSUIT_SOURCE_DIMENSION, ""))
				.orElse(player.level().getServer().overworld());
		BlockPos entry = BlockPos.of(record.getLongOr(TerminalData.PURSUIT_SOURCE_POSITION,
				source.getRespawnData().pos().asLong()));
		// The mirror is a block-for-block copy at the same coordinates, so wherever the chase left
		// the player is a real place in the source world. Always returning them to the entry point
		// threw the whole chase away: someone who ran two hundred blocks to break line of sight was
		// put back where they started, which reads as the escape not having counted for anything.
		BlockPos preferred = PursuitDimensions.isMirror(player.level()) ? player.blockPosition() : entry;
		BlockPos safe = PursuitReturnLocator.find(source, preferred, entry);
		float yaw = (float) record.getDoubleOr(TerminalData.PURSUIT_SOURCE_YAW, player.getYRot());
		float pitch = (float) record.getDoubleOr(TerminalData.PURSUIT_SOURCE_PITCH, player.getXRot());
		sendPresentation(player, record.getStringOr(TerminalData.PURSUIT_SESSION_ID, ""),
				PursuitPresentationPayload.BLACKOUT,
				record.getIntOr(TerminalData.PURSUIT_SESSION_FORM, 0));
		player.teleportTo(source, safe.getX() + 0.5D, safe.getY(), safe.getZ() + 0.5D,
				Set.of(), yaw, pitch, true);
		PursuitVisibilityService.restore(player);
		// interrupt() above already clears this for a session that reached the mirror; this also
		// covers a return from the prelude, and a recovery join where no runtime exists any more but
		// the effect was persisted with the player.
		PursuitVisionService.clear(player);
		PursuitRecoveryLedger.settleAndDeliver(player);
		clearSession(data, player, resolution);
		return true;
	}

	private static void deferDisconnectedSession(ServerPlayer player) {
		PENDING_TRANSFERS.remove(player.getUUID());
		FrequencyWorldData data = FrequencyWorldData.get(player.level().getServer());
		var record = data.terminalRecord(player.getUUID()).orElse(null);
		if (record == null || !record.getBooleanOr(TerminalData.PURSUIT_ACTIVE, false)) return;
		boolean debugSession = record.getBooleanOr(TerminalData.PURSUIT_SESSION_DEBUG, false);
		data.updateTerminalRecord(player.getUUID(), value -> {
			value.putBoolean(TerminalData.PURSUIT_ACTIVE, false);
			value.putString(TerminalData.PURSUIT_SESSION_PHASE, "recovery_pending");
			if (!debugSession) {
				value.putBoolean(TerminalData.PURSUIT_PENDING, true);
				value.putLong(TerminalData.PURSUIT_NEXT_ELIGIBLE_TICK,
						player.level().getGameTime() + INTERRUPTED_RETRY_TICKS);
			}
		});
		PursuitFormController.interrupt(player);
		PursuitSnapshotBuilder.cancel(player.getUUID());
		PursuitSlotManager.release(player.getUUID());
	}

	private static void recoverOnJoin(ServerPlayer player) {
		FrequencyWorldData data = FrequencyWorldData.get(player.level().getServer());
		var record = data.terminalRecord(player.getUUID()).orElse(null);
		if (record == null) return;
		if (!record.getListOrEmpty(TerminalData.PURSUIT_RECOVERY_QUEUE).isEmpty()
				|| !record.getListOrEmpty(TerminalData.PURSUIT_REFUND_LEDGER).isEmpty()) {
			PursuitRecoveryLedger.settleAndDeliver(player);
			record = data.terminalRecord(player.getUUID()).orElse(record);
		}
		boolean recoveryPending = record.getStringOr(TerminalData.PURSUIT_SESSION_PHASE, "none")
				.equals("recovery_pending");
		if (!recoveryPending && !record.getBooleanOr(TerminalData.PURSUIT_ACTIVE, false)
				&& !PursuitDimensions.isMirror(player.level())) return;
		returnToSource(player, "recovered");
	}

	private static void completeTransfer(net.minecraft.server.MinecraftServer server, UUID playerId,
			String sessionId, PursuitSlotManager.Lease lease, boolean success) {
		ServerPlayer player = server.getPlayerList().getPlayer(playerId);
		if (player == null) return;
		FrequencyWorldData data = FrequencyWorldData.get(server);
		var record = data.terminalRecord(playerId).orElse(null);
		if (record == null || !record.getBooleanOr(TerminalData.PURSUIT_ACTIVE, false)
				|| !record.getStringOr(TerminalData.PURSUIT_SESSION_ID, "").equals(sessionId)
				|| !record.getStringOr(TerminalData.PURSUIT_SESSION_PHASE, "").equals("copying")) return;
		if (!success) {
			clearSession(data, player, "snapshot_failed");
			return;
		}
		ServerLevel mirror = server.getLevel(lease.dimension());
		if (mirror == null) {
			clearSession(data, player, "mirror_missing");
			return;
		}
		ServerLevel departureLevel = (ServerLevel) player.level();
		BlockPos departurePosition = player.blockPosition();
		mirror.getChunkAt(player.blockPosition());
		player.teleportTo(mirror, player.getX(), player.getY(), player.getZ(), Set.of(),
				player.getYRot(), player.getXRot(), true);
		data.updateTerminalRecord(playerId,
				value -> value.putString(TerminalData.PURSUIT_SESSION_PHASE, "running"));
		notifyNearbyObservers(departureLevel, departurePosition, playerId);
		if (!PursuitFormController.begin(player, sessionId,
				record.getIntOr(TerminalData.PURSUIT_SESSION_FORM, 1),
				record.getBooleanOr(TerminalData.PURSUIT_SESSION_DEBUG, false))) {
			returnToSource(player, "entity_unavailable");
			return;
		}
		sendPresentation(player, sessionId, PursuitPresentationPayload.RUNNING,
				record.getIntOr(TerminalData.PURSUIT_SESSION_FORM, 1));
	}

	/**
	 * Solitude comes from the observer being removed from shared reality, but the removal itself
	 * should not read as a disconnect to the people standing next to them. Nearby bound terminals
	 * record one impersonal line; it names no one and reveals nothing about the chase.
	 */
	private static void notifyNearbyObservers(ServerLevel source, BlockPos origin, UUID departedId) {
		FrequencyWorldData data = FrequencyWorldData.get(source.getServer());
		for (ServerPlayer observer : source.players()) {
			if (observer.getUUID().equals(departedId)
					|| observer.blockPosition().distSqr(origin) > 64.0D * 64.0D
					|| data.terminalRecord(observer.getUUID()).isEmpty()) continue;
			data.updateTerminalRecord(observer.getUUID(), record ->
					TerminalSignalLog.append(record, SignalBand.UNKNOWN, "peer_signal_lost",
							source.getGameTime(), source.getDayTime(),
							source.dimension().identifier().toString(), origin.asLong(), 0, 2, true));
			TerminalRuntimeService.synchronizeAttentionProjection(observer, data);
			TerminalRuntimeService.refresh(observer);
		}
	}

	private static void clearSession(FrequencyWorldData data, ServerPlayer player, String resolution) {
		PENDING_TRANSFERS.remove(player.getUUID());
		PursuitSnapshotBuilder.cancel(player.getUUID());
		long retryAt = player.level().getGameTime() + INTERRUPTED_RETRY_TICKS;
		boolean successfulResolution = successfulResolution(resolution);
		boolean completedResolution = completedResolution(resolution);
		boolean debugSession = data.terminalRecord(player.getUUID())
				.map(record -> record.getBooleanOr(TerminalData.PURSUIT_SESSION_DEBUG, false))
				.orElse(false);
		data.updateTerminalRecord(player.getUUID(), record -> {
			record.putBoolean(TerminalData.PURSUIT_ACTIVE, false);
			record.putString(TerminalData.PURSUIT_SESSION_ID, "");
			record.putString(TerminalData.PURSUIT_SESSION_PHASE,
					resolution == null || resolution.isBlank() ? "returned" : resolution);
			record.putInt(TerminalData.PURSUIT_SESSION_FORM, 0);
			record.putBoolean(TerminalData.PURSUIT_SESSION_DEBUG, false);
			record.putString(TerminalData.PURSUIT_MIRROR_DIMENSION, "");
			record.putInt(TerminalData.PURSUIT_MIRROR_SLOT, -1);
			record.putLong(TerminalData.PURSUIT_SESSION_STARTED_TICK, 0L);
			if (successfulResolution) {
				TerminalSignalLog.removeTypesStartingWith(record, "pursuit_warning_");
			}
			if (completedResolution) {
				record.putBoolean(TerminalData.PURSUIT_WARNING_RECORDS_REDIRECT, true);
				TerminalSignalLog.append(record, SignalBand.UNKNOWN, "pursuit_return_instability",
						player.level().getGameTime(), player.level().getDayTime(),
						player.level().dimension().identifier().toString(), player.blockPosition().asLong(),
						0, 2, true);
			}
			if (!debugSession && !"success".equals(resolution)) {
				record.putBoolean(TerminalData.PURSUIT_PENDING, true);
				record.putLong(TerminalData.PURSUIT_NEXT_ELIGIBLE_TICK, retryAt);
			}
		});
		TerminalRuntimeService.synchronizeAttentionProjection(player, data);
		TerminalRuntimeService.refresh(player);
		PursuitSlotManager.release(player.getUUID());
		sendPresentation(player, "", PursuitPresentationPayload.CLEAR, 0);
	}

	private static boolean successfulResolution(String resolution) {
		return "success".equals(resolution) || "debug_complete".equals(resolution);
	}

	private static boolean completedResolution(String resolution) {
		return successfulResolution(resolution) || "caught".equals(resolution);
	}

	private static void tickPendingTransfers(MinecraftServer server) {
		for (PendingTransfer pending : Map.copyOf(PENDING_TRANSFERS).values()) {
			ServerPlayer player = server.getPlayerList().getPlayer(pending.playerId);
			if (player == null) {
				// Normally ServerPlayerEvents.LEAVE (deferDisconnectedSession) already released
				// this slot before the player list ever loses them. This branch is the defensive
				// fallback for the rare case where that event does not fire before this tick
				// observes the player gone (e.g. a forced removal); release() is idempotent, so
				// this is safe to call even when deferDisconnectedSession already handled it.
				PENDING_TRANSFERS.remove(pending.playerId);
				PursuitSlotManager.release(pending.playerId);
				continue;
			}
			FrequencyWorldData data = FrequencyWorldData.get(server);
			var record = data.terminalRecord(pending.playerId).orElse(null);
			boolean valid = record != null
					&& record.getBooleanOr(TerminalData.PURSUIT_ACTIVE, false)
					&& record.getStringOr(TerminalData.PURSUIT_SESSION_ID, "").equals(pending.sessionId)
					&& record.getStringOr(TerminalData.PURSUIT_SESSION_PHASE, "").equals("warning")
					&& player.level().dimension().equals(pending.sourceDimension);
			if (!valid) {
				PENDING_TRANSFERS.remove(pending.playerId);
				if (record != null && record.getBooleanOr(TerminalData.PURSUIT_ACTIVE, false)) {
					returnToSource(player, "prelude_interrupted");
				} else {
					PursuitSlotManager.release(pending.playerId);
					sendPresentation(player, "", PursuitPresentationPayload.CLEAR, 0);
				}
				continue;
			}
			if (server.getTickCount() - pending.startedAt < PursuitPresentationTimeline.PRELUDE_TICKS) continue;
			PENDING_TRANSFERS.remove(pending.playerId);
			BlockPos origin = player.blockPosition();
			data.updateTerminalRecord(pending.playerId, value -> {
				value.putString(TerminalData.PURSUIT_SESSION_PHASE, "copying");
				value.putLong(TerminalData.PURSUIT_SOURCE_POSITION, origin.asLong());
				value.putDouble(TerminalData.PURSUIT_SOURCE_YAW, player.getYRot());
				value.putDouble(TerminalData.PURSUIT_SOURCE_PITCH, player.getXRot());
			});
			sendPresentation(player, pending.sessionId, PursuitPresentationPayload.BLACKOUT, pending.form);
			boolean accepted = PursuitSnapshotBuilder.start(server, pending.playerId,
					pending.sourceDimension, pending.lease.dimension(), origin,
					success -> completeTransfer(server, pending.playerId, pending.sessionId,
							pending.lease, success));
			if (!accepted) clearSession(data, player, "snapshot_rejected");
		}
	}

	private static void sendPresentation(ServerPlayer player, String sessionId, int phase, int form) {
		if (ServerPlayNetworking.canSend(player, PursuitPresentationPayload.TYPE)) {
			ServerPlayNetworking.send(player, new PursuitPresentationPayload(sessionId, phase, form));
		}
	}

	static void presentResolution(ServerPlayer player, int phase) {
		FrequencyWorldData data = FrequencyWorldData.get(player.level().getServer());
		var record = data.terminalRecord(player.getUUID()).orElse(null);
		if (record == null || !record.getBooleanOr(TerminalData.PURSUIT_ACTIVE, false)) return;
		sendPresentation(player, record.getStringOr(TerminalData.PURSUIT_SESSION_ID, ""), phase,
				record.getIntOr(TerminalData.PURSUIT_SESSION_FORM, 0));
	}

	private record PendingTransfer(UUID playerId, String sessionId, int form,
			ResourceKey<Level> sourceDimension, PursuitSlotManager.Lease lease, long startedAt) {
	}
}
