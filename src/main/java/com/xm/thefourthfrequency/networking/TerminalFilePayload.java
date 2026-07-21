package com.xm.thefourthfrequency.networking;

import net.minecraft.network.RegistryFriendlyByteBuf;

public record TerminalFilePayload(String id, boolean discovered, boolean unlocked, long discoveredGameTime, long discoveredDayTime,
		long unlockedGameTime, long unlockedDayTime, int variant) {
	static void write(RegistryFriendlyByteBuf buf, TerminalFilePayload value) {
		buf.writeUtf(value.id, 48);
		buf.writeBoolean(value.discovered);
		buf.writeBoolean(value.unlocked);
		buf.writeVarLong(value.discoveredGameTime);
		buf.writeVarLong(value.discoveredDayTime);
		buf.writeVarLong(value.unlockedGameTime);
		buf.writeVarLong(value.unlockedDayTime);
		buf.writeVarInt(value.variant);
	}

	static TerminalFilePayload read(RegistryFriendlyByteBuf buf) {
		return new TerminalFilePayload(buf.readUtf(48), buf.readBoolean(), buf.readBoolean(), buf.readVarLong(), buf.readVarLong(),
				buf.readVarLong(), buf.readVarLong(), buf.readVarInt());
	}
}
