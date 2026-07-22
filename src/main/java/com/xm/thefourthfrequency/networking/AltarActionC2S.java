package com.xm.thefourthfrequency.networking;

import com.xm.thefourthfrequency.bootstrap.TheFourthFrequency;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.Objects;
import java.util.UUID;

/** Optimistic-concurrency altar action; inventory contents are never trusted from the client. */
public record AltarActionC2S(UUID encounterId, long expectedRevision, int actionId)
		implements CustomPacketPayload {
	public static final int PROTOCOL_VERSION = WorldInterfaceProtocol.VERSION;
	public static final Type<AltarActionC2S> TYPE = new Type<>(Identifier.fromNamespaceAndPath(
			TheFourthFrequency.MOD_ID, "world_interface_altar_action_v1"));
	public static final StreamCodec<RegistryFriendlyByteBuf, AltarActionC2S> CODEC = StreamCodec.of(
			AltarActionC2S::write, AltarActionC2S::read);

	public AltarActionC2S {
		Objects.requireNonNull(encounterId, "encounterId");
		if (expectedRevision < 0L) throw new IllegalArgumentException("Expected revision must be non-negative");
		WorldInterfaceProtocol.AltarAction.fromWireId(actionId);
	}

	public AltarActionC2S(UUID encounterId, long expectedRevision,
			WorldInterfaceProtocol.AltarAction action) {
		this(encounterId, expectedRevision, Objects.requireNonNull(action, "action").wireId());
	}

	public WorldInterfaceProtocol.AltarAction action() {
		return WorldInterfaceProtocol.AltarAction.fromWireId(actionId);
	}

	private static void write(RegistryFriendlyByteBuf buffer, AltarActionC2S value) {
		buffer.writeUUID(value.encounterId);
		buffer.writeVarLong(value.expectedRevision);
		buffer.writeVarInt(value.actionId);
	}

	private static AltarActionC2S read(RegistryFriendlyByteBuf buffer) {
		return new AltarActionC2S(buffer.readUUID(), buffer.readVarLong(), buffer.readVarInt());
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
