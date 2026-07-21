package com.xm.thefourthfrequency.networking;

import com.xm.thefourthfrequency.bootstrap.TheFourthFrequency;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record TerminalOpenPayload(int hand) implements CustomPacketPayload {
	public static final Type<TerminalOpenPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(
			TheFourthFrequency.MOD_ID, "terminal_open"));
	public static final StreamCodec<RegistryFriendlyByteBuf, TerminalOpenPayload> CODEC = StreamCodec.of(
			(buf, payload) -> buf.writeVarInt(payload.hand),
			buf -> new TerminalOpenPayload(buf.readVarInt()));

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
