package com.xm.thefourthfrequency.pursuit;

import com.xm.thefourthfrequency.content.TerminalData;
import com.xm.thefourthfrequency.world.FrequencyWorldData;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.Set;
import java.util.UUID;

/** Authoritative source/mirror teleport transaction and disconnect recovery. */
public final class PursuitSessionService {
	private static final long INTERRUPTED_RETRY_TICKS = 5L * 60L * 20L;
	private static boolean initialized;

	private PursuitSessionService() {
	}

	public static void initialize() {
		if (initialized) return;
		initialized = true;
		ServerPlayerEvents.JOIN.register(PursuitSessionService::recoverOnJoin);
		ServerPlayerEvents.LEAVE.register(PursuitSessionService::deferDisconnectedSession);
	}

	public static boolean enterEmptyMirror(ServerPlayer player, PursuitSlotManager.Lease lease, int form) {
		if (PursuitDimensions.isMirror(player.level()) || !lease.playerId().equals(player.getUUID())) return false;
		ServerLevel source = (ServerLevel) player.level();
		ServerLevel mirror = source.getServer().getLevel(lease.dimension());
		if (mirror == null) return false;
		var family = PursuitDimensions.sourceFamily(source.dimension());
		if (family.isEmpty() || family.get() != lease.family()) return false;

		BlockPos origin = player.blockPosition();
		String sessionId = UUID.randomUUID().toString();
		FrequencyWorldData data = FrequencyWorldData.get(source.getServer());
		data.updateTerminalRecord(player.getUUID(), record -> {
			record.putBoolean(TerminalData.PURSUIT_ACTIVE, true);
			record.putString(TerminalData.PURSUIT_SESSION_ID, sessionId);
			record.putString(TerminalData.PURSUIT_SESSION_PHASE, "copying");
			record.putInt(TerminalData.PURSUIT_SESSION_FORM, Math.clamp(form, 1, 5));
			record.putString(TerminalData.PURSUIT_SOURCE_DIMENSION, source.dimension().identifier().toString());
			record.putLong(TerminalData.PURSUIT_SOURCE_POSITION, origin.asLong());
			record.putDouble(TerminalData.PURSUIT_SOURCE_YAW, player.getYRot());
			record.putDouble(TerminalData.PURSUIT_SOURCE_PITCH, player.getXRot());
			record.putString(TerminalData.PURSUIT_MIRROR_DIMENSION, lease.dimension().identifier().toString());
			record.putInt(TerminalData.PURSUIT_MIRROR_SLOT, lease.slot());
			record.putLong(TerminalData.PURSUIT_SESSION_STARTED_TICK, source.getGameTime());
		});
		boolean accepted = PursuitSnapshotBuilder.start(source.getServer(), player.getUUID(), source.dimension(),
				lease.dimension(), origin, success -> completeTransfer(source.getServer(), player.getUUID(),
						sessionId, lease, success));
		if (accepted) return true;
		data.updateTerminalRecord(player.getUUID(), record -> {
			record.putBoolean(TerminalData.PURSUIT_ACTIVE, false);
			record.putString(TerminalData.PURSUIT_SESSION_PHASE, "snapshot_rejected");
		});
		PursuitSlotManager.release(player.getUUID());
		return false;
	}

	public static boolean returnToSource(ServerPlayer player, String resolution) {
		PursuitFormController.interrupt(player);
		PursuitSnapshotBuilder.cancel(player.getUUID());
		FrequencyWorldData data = FrequencyWorldData.get(player.level().getServer());
		var record = data.terminalRecord(player.getUUID()).orElse(null);
		if (record == null) return false;
		ServerLevel source = PursuitDimensions.sourceLevel(player.level().getServer(),
				record.getStringOr(TerminalData.PURSUIT_SOURCE_DIMENSION, ""))
				.orElse(player.level().getServer().overworld());
		BlockPos preferred = BlockPos.of(record.getLongOr(TerminalData.PURSUIT_SOURCE_POSITION,
				source.getRespawnData().pos().asLong()));
		BlockPos safe = PursuitReturnLocator.find(source, preferred);
		float yaw = (float) record.getDoubleOr(TerminalData.PURSUIT_SOURCE_YAW, player.getYRot());
		float pitch = (float) record.getDoubleOr(TerminalData.PURSUIT_SOURCE_PITCH, player.getXRot());
		player.teleportTo(source, safe.getX() + 0.5D, safe.getY(), safe.getZ() + 0.5D,
				Set.of(), yaw, pitch, true);
		PursuitVisibilityService.restore(player);
		PursuitRecoveryLedger.settleAndDeliver(player);
		clearSession(data, player, resolution);
		return true;
	}

	private static void deferDisconnectedSession(ServerPlayer player) {
		FrequencyWorldData data = FrequencyWorldData.get(player.level().getServer());
		var record = data.terminalRecord(player.getUUID()).orElse(null);
		if (record == null || !record.getBooleanOr(TerminalData.PURSUIT_ACTIVE, false)) return;
		data.updateTerminalRecord(player.getUUID(), value -> {
			value.putBoolean(TerminalData.PURSUIT_ACTIVE, false);
			value.putString(TerminalData.PURSUIT_SESSION_PHASE, "recovery_pending");
			value.putBoolean(TerminalData.PURSUIT_PENDING, true);
			value.putLong(TerminalData.PURSUIT_NEXT_ELIGIBLE_TICK,
					player.level().getGameTime() + INTERRUPTED_RETRY_TICKS);
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
		mirror.getChunkAt(player.blockPosition());
		player.teleportTo(mirror, player.getX(), player.getY(), player.getZ(), Set.of(),
				player.getYRot(), player.getXRot(), true);
		data.updateTerminalRecord(playerId,
				value -> value.putString(TerminalData.PURSUIT_SESSION_PHASE, "running"));
		if (!PursuitFormController.begin(player, sessionId,
				record.getIntOr(TerminalData.PURSUIT_SESSION_FORM, 1))) {
			returnToSource(player, "entity_unavailable");
		}
	}

	private static void clearSession(FrequencyWorldData data, ServerPlayer player, String resolution) {
		PursuitSnapshotBuilder.cancel(player.getUUID());
		long retryAt = player.level().getGameTime() + INTERRUPTED_RETRY_TICKS;
		data.updateTerminalRecord(player.getUUID(), record -> {
			record.putBoolean(TerminalData.PURSUIT_ACTIVE, false);
			record.putString(TerminalData.PURSUIT_SESSION_ID, "");
			record.putString(TerminalData.PURSUIT_SESSION_PHASE,
					resolution == null || resolution.isBlank() ? "returned" : resolution);
			record.putInt(TerminalData.PURSUIT_SESSION_FORM, 0);
			record.putString(TerminalData.PURSUIT_MIRROR_DIMENSION, "");
			record.putInt(TerminalData.PURSUIT_MIRROR_SLOT, -1);
			record.putLong(TerminalData.PURSUIT_SESSION_STARTED_TICK, 0L);
			if (!"success".equals(resolution)) {
				record.putBoolean(TerminalData.PURSUIT_PENDING, true);
				record.putLong(TerminalData.PURSUIT_NEXT_ELIGIBLE_TICK, retryAt);
			}
		});
		PursuitSlotManager.release(player.getUUID());
	}
}
