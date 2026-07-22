package com.xm.thefourthfrequency.networking;

import com.xm.thefourthfrequency.bootstrap.TheFourthFrequency;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.UUID;

/** Independent v1 lease envelope; it deliberately does not change the terminal snapshot protocol. */
public record EndBossIntrusionS2C(UUID encounterId, int sequence, int lockedSlotMask,
		long expiresAtTick) implements CustomPacketPayload {
	public static final int PROTOCOL_VERSION = 1;
	public static final Type<EndBossIntrusionS2C> TYPE = new Type<>(Identifier.fromNamespaceAndPath(
			TheFourthFrequency.MOD_ID, "end_boss_intrusion_v1"));
	public static final StreamCodec<RegistryFriendlyByteBuf, EndBossIntrusionS2C> CODEC = StreamCodec.of(
			EndBossIntrusionS2C::write, EndBossIntrusionS2C::read);

	private static void write(RegistryFriendlyByteBuf buffer, EndBossIntrusionS2C value) {
		buffer.writeUUID(value.encounterId);
		buffer.writeVarInt(value.sequence);
		buffer.writeVarInt(value.lockedSlotMask);
		buffer.writeLong(value.expiresAtTick);
	}

	private static EndBossIntrusionS2C read(RegistryFriendlyByteBuf buffer) {
		return new EndBossIntrusionS2C(buffer.readUUID(), buffer.readVarInt(), buffer.readVarInt(), buffer.readLong());
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
