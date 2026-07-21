package com.xm.thefourthfrequency.networking;

import net.minecraft.network.RegistryFriendlyByteBuf;

public record TerminalLogEntryPayload(
		int sequence,
		int band,
		String type,
		long gameTime,
		long dayTime,
		String dimension,
		long position,
		int variant,
		int severity,
		boolean unread
) {
	static void write(RegistryFriendlyByteBuf buf, TerminalLogEntryPayload value) {
		buf.writeVarInt(value.sequence);
		buf.writeVarInt(value.band);
		buf.writeUtf(value.type, 64);
		buf.writeVarLong(value.gameTime);
		buf.writeVarLong(value.dayTime);
		buf.writeUtf(value.dimension, 128);
		buf.writeLong(value.position);
		buf.writeVarInt(value.variant);
		buf.writeVarInt(value.severity);
		buf.writeBoolean(value.unread);
	}

	static TerminalLogEntryPayload read(RegistryFriendlyByteBuf buf) {
		return new TerminalLogEntryPayload(buf.readVarInt(), buf.readVarInt(), buf.readUtf(64), buf.readVarLong(),
				buf.readVarLong(), buf.readUtf(128), buf.readLong(), buf.readVarInt(), buf.readVarInt(), buf.readBoolean());
	}
}
