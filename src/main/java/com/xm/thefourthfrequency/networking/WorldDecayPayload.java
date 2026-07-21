package com.xm.thefourthfrequency.networking;

import com.xm.thefourthfrequency.bootstrap.TheFourthFrequency;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record WorldDecayPayload(int stage) implements CustomPacketPayload {
	public static final Type<WorldDecayPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(
			TheFourthFrequency.MOD_ID, "world_decay"));
	public static final StreamCodec<RegistryFriendlyByteBuf, WorldDecayPayload> CODEC = StreamCodec.of(
			(buf, value) -> buf.writeVarInt(value.stage), buf -> new WorldDecayPayload(buf.readVarInt()));
	@Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
