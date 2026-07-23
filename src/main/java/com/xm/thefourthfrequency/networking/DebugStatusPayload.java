package com.xm.thefourthfrequency.networking;

import com.xm.thefourthfrequency.bootstrap.TheFourthFrequency;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record DebugStatusPayload(int protocolVersion, boolean allowed, String playerName, int plotStage,
		int bandStage, boolean bound, int discoveredFiles, int unlockedFiles,
		int bodyProgress, int bodyStage, int decayStage, boolean decayAuto,
		int anomalyTier, int anomalyCeiling, int anomalyHeat,
		String activeAnomaly, int activeSeconds, int nextSeconds, int strongCooldownSeconds,
		int compositeCooldownSeconds, boolean anomaliesSuspended, String message) implements CustomPacketPayload {
	public static final int CURRENT_PROTOCOL_VERSION = 3;
	public static final Type<DebugStatusPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(
			TheFourthFrequency.MOD_ID, "debug_status"));
	public static final StreamCodec<RegistryFriendlyByteBuf, DebugStatusPayload> CODEC = StreamCodec.of(
			DebugStatusPayload::write, DebugStatusPayload::read);

	private static void write(RegistryFriendlyByteBuf buf, DebugStatusPayload value) {
		buf.writeVarInt(value.protocolVersion); buf.writeBoolean(value.allowed); buf.writeUtf(value.playerName, 64);
		buf.writeVarInt(value.plotStage); buf.writeVarInt(value.bandStage); buf.writeBoolean(value.bound);
		buf.writeVarInt(value.discoveredFiles); buf.writeVarInt(value.unlockedFiles);
		buf.writeVarInt(value.bodyProgress); buf.writeVarInt(value.bodyStage);
		buf.writeVarInt(value.decayStage); buf.writeBoolean(value.decayAuto);
		buf.writeVarInt(value.anomalyTier); buf.writeVarInt(value.anomalyCeiling); buf.writeVarInt(value.anomalyHeat);
		buf.writeUtf(value.activeAnomaly, 64); buf.writeVarInt(value.activeSeconds); buf.writeVarInt(value.nextSeconds);
		buf.writeVarInt(value.strongCooldownSeconds); buf.writeVarInt(value.compositeCooldownSeconds);
		buf.writeBoolean(value.anomaliesSuspended); buf.writeUtf(value.message, 200);
	}

	private static DebugStatusPayload read(RegistryFriendlyByteBuf buf) {
		return new DebugStatusPayload(buf.readVarInt(), buf.readBoolean(), buf.readUtf(64), buf.readVarInt(),
				buf.readVarInt(), buf.readBoolean(), buf.readVarInt(), buf.readVarInt(),
				buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readBoolean(),
				buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readUtf(64),
				buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
				buf.readVarInt(), buf.readBoolean(), buf.readUtf(200));
	}

	@Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
