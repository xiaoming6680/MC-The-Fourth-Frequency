package com.xm.thefourthfrequency.pursuit;

import com.xm.thefourthfrequency.content.TerminalData;
import com.xm.thefourthfrequency.terminal.AnomalyIntensity;
import com.xm.thefourthfrequency.terminal.TerminalRuntimeService;
import com.xm.thefourthfrequency.world.FrequencyWorldData;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** Converts personal mainline transitions into one pending, tutorial-gated pursuit. */
public final class PursuitDirector {
	private static final int CHECK_TICKS = 20;
	private static boolean initialized;

	private PursuitDirector() {
	}

	public static void initialize() {
		if (initialized) return;
		initialized = true;
		ServerTickEvents.END_SERVER_TICK.register(PursuitDirector::tick);
	}

	public static DebugStartResult debugStart(ServerPlayer player, int requestedForm) {
		if (requestedForm < 1 || requestedForm > 5) return DebugStartResult.INVALID_FORM;
		FrequencyWorldData data = FrequencyWorldData.get(player.level().getServer());
		CompoundTag record = data.terminalRecord(player.getUUID()).orElse(null);
		if (record == null) return DebugStartResult.NO_TERMINAL_RECORD;
		if (record.getBooleanOr(TerminalData.PURSUIT_ACTIVE, false)
				|| PursuitDimensions.isMirror(player.level())) return DebugStartResult.ALREADY_ACTIVE;
		var family = PursuitDimensions.sourceFamily(player.level().dimension()).orElse(null);
		if (family == null || family == PursuitDimensions.Family.END) {
			return DebugStartResult.UNSUPPORTED_DIMENSION;
		}
		if (!PursuitSafetyPolicy.canBegin(player, record, data)) return DebugStartResult.UNSAFE;
		var lease = PursuitSlotManager.acquire(player.level().getServer(), player.getUUID(), family).orElse(null);
		if (lease == null) return DebugStartResult.NO_SLOT;
		if (!PursuitSessionService.enterEmptyMirror(player, lease, requestedForm, true)) {
			PursuitSlotManager.release(player.getUUID());
			return DebugStartResult.TRANSFER_REJECTED;
		}
		return DebugStartResult.STARTED;
	}

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % CHECK_TICKS != 0) return;
		FrequencyWorldData data = FrequencyWorldData.get(server);
		for (ServerPlayer player : server.getPlayerList().getPlayers()) update(player, data);
	}

	private static void update(ServerPlayer player, FrequencyWorldData data) {
		CompoundTag record = data.terminalRecord(player.getUUID()).orElse(null);
		if (record == null || !record.getBooleanOr(TerminalData.BOUND, false)
				|| record.getBooleanOr(TerminalData.PURSUIT_ACTIVE, false)) return;
		long now = player.level().getGameTime();
		boolean early = PursuitProgressPolicy.earlyFormEligible(true,
				record.getLongOr(TerminalData.ANOMALY_SEEN_MASK, 0L) == 0L ? 0 : 1,
				record.getIntOr(TerminalData.PURSUIT_ACTIVITY_PROOF_MASK, 0),
				record.getLongOr(TerminalData.PURSUIT_EFFECTIVE_ACTIVITY_TICKS, 0L));
		int previousAllowed = record.getIntOr(TerminalData.PURSUIT_ALLOWED_FORM, 0);
		int resolved = record.getIntOr(TerminalData.PURSUIT_RESOLVED_CHASES, 0);
		int allowed = PursuitProgressPolicy.allowedForm(
				record.getIntOr(TerminalData.SURVIVAL_MILESTONE_MASK, 0),
				record.getIntOr(TerminalData.EYE_SAMPLE_COUNT, 0), early);
		boolean pending = PursuitProgressPolicy.pendingAfterAllowedFormUpdate(
				record.getBooleanOr(TerminalData.PURSUIT_PENDING, false),
				previousAllowed, allowed, resolved);
		if (allowed != previousAllowed || pending != record.getBooleanOr(TerminalData.PURSUIT_PENDING, false)) {
			data.updateTerminalRecord(player.getUUID(), tag -> {
				tag.putInt(TerminalData.PURSUIT_ALLOWED_FORM, allowed);
				tag.putBoolean(TerminalData.PURSUIT_PENDING, pending);
			});
			TerminalRuntimeService.synchronizeAttentionProjection(player, data);
		}
		if (!pending || PursuitProgressPolicy.complete(resolved)) return;
		int form = PursuitProgressPolicy.actualForm(resolved);
		int demoMask = record.getIntOr(TerminalData.PURSUIT_TUTORIAL_DEMO_MASK, 0);
		boolean tutorialReady = PursuitTutorialPolicy.demonstrated(demoMask, form);
		if (!PursuitProgressPolicy.canStart(pending, allowed, resolved, tutorialReady, now,
				record.getLongOr(TerminalData.PURSUIT_NEXT_ELIGIBLE_TICK, 0L))) return;
		if (!PursuitSafetyPolicy.canBegin(player, record, data)) return;
		var family = PursuitDimensions.sourceFamily(player.level().dimension()).orElse(null);
		if (family == null) return;
		var lease = PursuitSlotManager.acquire(player.level().getServer(), player.getUUID(), family).orElse(null);
		if (lease == null) return;
		if (!PursuitSessionService.enterEmptyMirror(player, lease, form)) {
			PursuitSlotManager.release(player.getUUID());
			return;
		}
		data.updateTerminalRecord(player.getUUID(), tag ->
				tag.putLong(TerminalData.NEXT_AMBIENT_ANOMALY_TICK,
						now + AnomalyIntensity.DIMENSION_GRACE_TICKS + 5L * 60L * 20L));
	}

	public enum DebugStartResult {
		STARTED,
		INVALID_FORM,
		NO_TERMINAL_RECORD,
		ALREADY_ACTIVE,
		UNSUPPORTED_DIMENSION,
		UNSAFE,
		NO_SLOT,
		TRANSFER_REJECTED
	}

}
