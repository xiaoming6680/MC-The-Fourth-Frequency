package com.xm.thefourthfrequency.networking;

import com.xm.thefourthfrequency.bootstrap.TheFourthFrequency;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record EmptySegmentPayload(String eventId, int durationTicks, int cameraEntityId) implements CustomPacketPayload {
	public static final Type<EmptySegmentPayload> TYPE = new Type<>(
			Identifier.fromNamespaceAndPath(TheFourthFrequency.MOD_ID, "empty_segment_event"));
	public static final StreamCodec<RegistryFriendlyByteBuf, EmptySegmentPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.stringUtf8(32), EmptySegmentPayload::eventId,
			ByteBufCodecs.VAR_INT, EmptySegmentPayload::durationTicks,
			ByteBufCodecs.VAR_INT, EmptySegmentPayload::cameraEntityId,
			EmptySegmentPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
