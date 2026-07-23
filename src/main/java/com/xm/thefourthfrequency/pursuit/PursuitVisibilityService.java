package com.xm.thefourthfrequency.pursuit;

import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/** Removes tab-list presence in both directions while dimension separation handles entity visibility. */
public final class PursuitVisibilityService {
	private PursuitVisibilityService() {
	}

	public static void isolate(ServerPlayer target) {
		for (ServerPlayer other : target.level().getServer().getPlayerList().getPlayers()) {
			if (other == target) continue;
			other.connection.send(new ClientboundPlayerInfoRemovePacket(List.of(target.getUUID())));
			target.connection.send(new ClientboundPlayerInfoRemovePacket(List.of(other.getUUID())));
		}
	}

	public static void restore(ServerPlayer target) {
		List<ServerPlayer> others = target.level().getServer().getPlayerList().getPlayers().stream()
				.filter(player -> player != target)
				.filter(player -> !PursuitDimensions.isMirror(player.level()))
				.toList();
		for (ServerPlayer other : others) {
			other.connection.send(ClientboundPlayerInfoUpdatePacket.createPlayerInitializing(List.of(target)));
		}
		if (!others.isEmpty()) {
			target.connection.send(ClientboundPlayerInfoUpdatePacket.createPlayerInitializing(others));
		}
	}
}
