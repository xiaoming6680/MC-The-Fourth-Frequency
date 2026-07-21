package com.xm.thefourthfrequency.networking;

import com.xm.thefourthfrequency.bootstrap.TheFourthFrequency;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record TerminalClosedPayload(int reason) implements CustomPacketPayload {
	public static final int REQUESTED = 0;
	public static final int INVALID_ITEM = 1;
	public static final int UNAVAILABLE = 2;

	public static final Type<TerminalClosedPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(
			TheFourthFrequency.MOD_ID, "terminal_closed"));
	public static final StreamCodec<RegistryFriendlyByteBuf, TerminalClosedPayload> CODEC = StreamCodec.of(
			(buf, payload) -> buf.writeVarInt(payload.reason),
			buf -> new TerminalClosedPayload(buf.readVarInt()));

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
