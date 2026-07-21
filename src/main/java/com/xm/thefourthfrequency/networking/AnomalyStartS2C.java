package com.xm.thefourthfrequency.networking;

import com.xm.thefourthfrequency.bootstrap.TheFourthFrequency;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.UUID;

/** Version 3 anomaly start envelope. Optional anchors are dimension-qualified block positions. */
public record AnomalyStartS2C(UUID instanceId, String anomalyId, int tier, int variant, long seed,
		long startTick, int expectedDurationTicks, boolean hasAnchor, String anchorDimension, long anchorPosition)
		implements CustomPacketPayload {
	public static final int PROTOCOL_VERSION = 3;
	public static final Type<AnomalyStartS2C> TYPE = new Type<>(Identifier.fromNamespaceAndPath(
			TheFourthFrequency.MOD_ID, "anomaly_start_v3"));
	public static final StreamCodec<RegistryFriendlyByteBuf, AnomalyStartS2C> CODEC = StreamCodec.of(
			AnomalyStartS2C::write, AnomalyStartS2C::read);

	private static void write(RegistryFriendlyByteBuf buf, AnomalyStartS2C value) {
		buf.writeUUID(value.instanceId);
		buf.writeUtf(value.anomalyId, 64);
		buf.writeVarInt(value.tier);
		buf.writeVarInt(value.variant);
		buf.writeLong(value.seed);
		buf.writeLong(value.startTick);
		buf.writeVarInt(value.expectedDurationTicks);
		buf.writeBoolean(value.hasAnchor);
		if (value.hasAnchor) {
			buf.writeUtf(value.anchorDimension, 128);
			buf.writeLong(value.anchorPosition);
		}
	}

	private static AnomalyStartS2C read(RegistryFriendlyByteBuf buf) {
		UUID instanceId = buf.readUUID();
		String anomalyId = buf.readUtf(64);
		int tier = buf.readVarInt();
		int variant = buf.readVarInt();
		long seed = buf.readLong();
		long startTick = buf.readLong();
		int duration = buf.readVarInt();
		boolean hasAnchor = buf.readBoolean();
		String dimension = hasAnchor ? buf.readUtf(128) : "";
		long position = hasAnchor ? buf.readLong() : 0L;
		return new AnomalyStartS2C(instanceId, anomalyId, tier, variant, seed, startTick, duration,
				hasAnchor, dimension, position);
	}

	@Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
