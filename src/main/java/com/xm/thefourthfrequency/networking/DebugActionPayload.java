package com.xm.thefourthfrequency.networking;

import com.xm.thefourthfrequency.bootstrap.TheFourthFrequency;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record DebugActionPayload(String action, String target, int value) implements CustomPacketPayload {
	public static final Type<DebugActionPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(
			TheFourthFrequency.MOD_ID, "debug_action"));
	public static final StreamCodec<RegistryFriendlyByteBuf, DebugActionPayload> CODEC = StreamCodec.of(
			(buf, value) -> { buf.writeUtf(value.action, 32); buf.writeUtf(value.target, 64); buf.writeVarInt(value.value); },
			buf -> new DebugActionPayload(buf.readUtf(32), buf.readUtf(64), buf.readVarInt()));
	@Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
