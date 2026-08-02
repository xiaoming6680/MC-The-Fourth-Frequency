package com.xm.thefourthfrequency.networking;

import com.xm.thefourthfrequency.bootstrap.TheFourthFrequency;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record PrivateAnomalyPayload(String anomalyId, int variant) implements CustomPacketPayload {
	public static final Type<PrivateAnomalyPayload> TYPE = new Type<>(
			Identifier.fromNamespaceAndPath(TheFourthFrequency.MOD_ID, "private_anomaly"));
	public static final StreamCodec<RegistryFriendlyByteBuf, PrivateAnomalyPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.stringUtf8(32), PrivateAnomalyPayload::anomalyId,
			ByteBufCodecs.VAR_INT, PrivateAnomalyPayload::variant,
			PrivateAnomalyPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
