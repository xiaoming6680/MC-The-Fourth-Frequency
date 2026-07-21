package com.xm.thefourthfrequency.networking;

import com.xm.thefourthfrequency.bootstrap.TheFourthFrequency;
import com.xm.thefourthfrequency.meta_api.MetaEvent;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record MetaEventPayload(int eventId) implements CustomPacketPayload {
	public static final Type<MetaEventPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(
			TheFourthFrequency.MOD_ID, "meta_event"));
	public static final StreamCodec<RegistryFriendlyByteBuf, MetaEventPayload> CODEC = StreamCodec.of(
			(buf, payload) -> buf.writeVarInt(payload.eventId),
			buf -> new MetaEventPayload(buf.readVarInt()));

	public MetaEvent event() {
		return MetaEvent.fromWireId(eventId);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
