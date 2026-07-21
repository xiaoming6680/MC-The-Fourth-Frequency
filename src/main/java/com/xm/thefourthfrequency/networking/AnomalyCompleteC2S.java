package com.xm.thefourthfrequency.networking;

import com.xm.thefourthfrequency.bootstrap.TheFourthFrequency;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.UUID;
import com.xm.thefourthfrequency.terminal.AnomalyCompletionStatus;

/** Client acknowledgement sent only after its presentation controller has restored every temporary lease. */
public record AnomalyCompleteC2S(UUID instanceId, AnomalyCompletionStatus status) implements CustomPacketPayload {
	public static final Type<AnomalyCompleteC2S> TYPE = new Type<>(Identifier.fromNamespaceAndPath(
			TheFourthFrequency.MOD_ID, "anomaly_complete_v3"));
	public static final StreamCodec<RegistryFriendlyByteBuf, AnomalyCompleteC2S> CODEC = StreamCodec.of(
			(buf, value) -> {
				buf.writeUUID(value.instanceId);
				buf.writeVarInt(value.status.ordinal());
			},
			buf -> new AnomalyCompleteC2S(buf.readUUID(), AnomalyCompletionStatus.values()[Math.clamp(
					buf.readVarInt(), 0, AnomalyCompletionStatus.values().length - 1)]));

	@Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
