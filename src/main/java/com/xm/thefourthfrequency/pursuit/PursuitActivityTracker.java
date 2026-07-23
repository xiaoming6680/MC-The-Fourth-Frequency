package com.xm.thefourthfrequency.pursuit;

import com.xm.thefourthfrequency.content.TerminalData;
import com.xm.thefourthfrequency.terminal.TerminalRuntimeService;
import com.xm.thefourthfrequency.world.FrequencyWorldData;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Persists broad early-game proof without requiring iron, a bed or a fixed home. */
public final class PursuitActivityTracker {
	private static final int SAMPLE_TICKS = 20;
	private static final double EXPLORATION_ROUTE_DISTANCE = 128.0D;
	private static final Map<UUID, Sample> LAST = new HashMap<>();
	private static boolean initialized;

	private PursuitActivityTracker() {
	}

	public static void initialize() {
		if (initialized) return;
		initialized = true;
		ServerTickEvents.END_SERVER_TICK.register(PursuitActivityTracker::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> LAST.clear());
	}

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % SAMPLE_TICKS != 0) return;
		FrequencyWorldData data = FrequencyWorldData.get(server);
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			var record = data.terminalRecord(player.getUUID()).orElse(null);
			if (record == null || PursuitDimensions.isMirror(player.level())) continue;
			String dimension = player.level().dimension().identifier().toString();
			Sample previous = LAST.put(player.getUUID(), new Sample(dimension, player.position()));
			double moved = previous == null || !previous.dimension.equals(dimension)
					? 0.0D : Math.min(16.0D, previous.position.distanceTo(player.position()));
			boolean recentlyActed = player.level().getGameTime()
					- record.getLongOr(TerminalData.LAST_ACTIVITY_GAME_TIME, 0L) <= 10L * 20L;
			boolean effective = player.isAlive() && !player.isSpectator() && !player.isSleeping()
					&& !TerminalRuntimeService.isOpen(player)
					&& (moved >= 0.05D || recentlyActed || player.containerMenu instanceof MerchantMenu);
			int proofMask = record.getIntOr(TerminalData.PURSUIT_ACTIVITY_PROOF_MASK, 0);
			if (record.getIntOr(TerminalData.MINED_BLOCKS, 0) > 0) {
				proofMask |= PursuitActivityProof.MINING.mask();
			}
			if (record.getIntOr(TerminalData.PLACED_BLOCKS, 0) > 0) {
				proofMask |= PursuitActivityProof.BUILDING.mask();
			}
			if (record.getIntOr(TerminalData.DEVICE_INTERACTIONS, 0) > 0) {
				proofMask |= PursuitActivityProof.LOOTING.mask();
			}
			if (player.containerMenu instanceof MerchantMenu) {
				proofMask |= PursuitActivityProof.TRADING.mask();
			}
			double exploration = Math.max(0.0D,
					record.getDoubleOr(TerminalData.PURSUIT_EXPLORATION_DISTANCE, 0.0D)) + moved;
			if (exploration >= EXPLORATION_ROUTE_DISTANCE) {
				proofMask |= PursuitActivityProof.EXPLORATION.mask();
			}
			int storedProofMask = proofMask;
			data.updateTerminalRecord(player.getUUID(), tag -> {
				tag.putInt(TerminalData.PURSUIT_ACTIVITY_PROOF_MASK, storedProofMask);
				tag.putDouble(TerminalData.PURSUIT_EXPLORATION_DISTANCE, exploration);
				if (effective) tag.putLong(TerminalData.PURSUIT_EFFECTIVE_ACTIVITY_TICKS,
						tag.getLongOr(TerminalData.PURSUIT_EFFECTIVE_ACTIVITY_TICKS, 0L) + SAMPLE_TICKS);
			});
		}
	}

	private record Sample(String dimension, Vec3 position) {
	}
}
