package com.xm.thefourthfrequency.networking;

import com.xm.thefourthfrequency.bootstrap.TheFourthFrequency;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record DebugOpenPayload() implements CustomPacketPayload {
	public static final Type<DebugOpenPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(
			TheFourthFrequency.MOD_ID, "debug_open"));
	public static final StreamCodec<RegistryFriendlyByteBuf, DebugOpenPayload> CODEC = StreamCodec.of(
			(buf, value) -> buf.writeBoolean(true), buf -> { buf.readBoolean(); return new DebugOpenPayload(); });
	@Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
