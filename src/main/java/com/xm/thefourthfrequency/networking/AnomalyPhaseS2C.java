package com.xm.thefourthfrequency.networking;

import com.xm.thefourthfrequency.bootstrap.TheFourthFrequency;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.UUID;

/** Monotonic server phase update for a running v3 anomaly. */
public record AnomalyPhaseS2C(UUID instanceId, int sequence, String phase, boolean blackout,
		int remainingTicks, boolean hasAnchor, String anchorDimension, long anchorPosition)
		implements CustomPacketPayload {
	public static final Type<AnomalyPhaseS2C> TYPE = new Type<>(Identifier.fromNamespaceAndPath(
			TheFourthFrequency.MOD_ID, "anomaly_phase_v3"));
	public static final StreamCodec<RegistryFriendlyByteBuf, AnomalyPhaseS2C> CODEC = StreamCodec.of(
			AnomalyPhaseS2C::write, AnomalyPhaseS2C::read);

	private static void write(RegistryFriendlyByteBuf buf, AnomalyPhaseS2C value) {
		buf.writeUUID(value.instanceId);
		buf.writeVarInt(value.sequence);
		buf.writeUtf(value.phase, 48);
		buf.writeBoolean(value.blackout);
		buf.writeVarInt(value.remainingTicks);
		buf.writeBoolean(value.hasAnchor);
		if (value.hasAnchor) {
			buf.writeUtf(value.anchorDimension, 128);
			buf.writeLong(value.anchorPosition);
		}
	}

	private static AnomalyPhaseS2C read(RegistryFriendlyByteBuf buf) {
		UUID instanceId = buf.readUUID();
		int sequence = buf.readVarInt();
		String phase = buf.readUtf(48);
		boolean blackout = buf.readBoolean();
		int remaining = buf.readVarInt();
		boolean hasAnchor = buf.readBoolean();
		String dimension = hasAnchor ? buf.readUtf(128) : "";
		long position = hasAnchor ? buf.readLong() : 0L;
		return new AnomalyPhaseS2C(instanceId, sequence, phase, blackout, remaining,
				hasAnchor, dimension, position);
	}

	@Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
