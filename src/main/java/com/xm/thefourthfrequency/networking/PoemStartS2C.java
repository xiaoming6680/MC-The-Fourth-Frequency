package com.xm.thefourthfrequency.networking;

import com.xm.thefourthfrequency.bootstrap.TheFourthFrequency;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.Objects;
import java.util.UUID;

/** Idempotent instruction that binds one authored outcome to the next vanilla End poem screen. */
public record PoemStartS2C(UUID encounterId, long sequence, int outcomeId, String worldId,
		int destroyedAnchors)
		implements CustomPacketPayload {
	public static final int PROTOCOL_VERSION = WorldInterfaceProtocol.VERSION;
	public static final Type<PoemStartS2C> TYPE = new Type<>(Identifier.fromNamespaceAndPath(
			TheFourthFrequency.MOD_ID, "world_interface_poem_start_v3"));
	public static final StreamCodec<RegistryFriendlyByteBuf, PoemStartS2C> CODEC = StreamCodec.of(
			PoemStartS2C::write, PoemStartS2C::read);

	public PoemStartS2C {
		Objects.requireNonNull(encounterId, "encounterId");
		Objects.requireNonNull(worldId, "worldId");
		if (worldId.length() > 128) throw new IllegalArgumentException("World id is too long");
		if (destroyedAnchors < 0 || destroyedAnchors > WorldInterfaceProtocol.MAX_ANCHORS) {
			throw new IllegalArgumentException("Destroyed anchor count must be between 0 and 10");
		}
		WorldInterfaceProtocol.requireSequence(sequence);
		WorldInterfaceProtocol.Outcome outcome = WorldInterfaceProtocol.Outcome.fromWireId(outcomeId);
		if (outcome == WorldInterfaceProtocol.Outcome.NONE) {
			throw new IllegalArgumentException("A poem must have a resolved outcome");
		}
	}

	public PoemStartS2C(UUID encounterId, long sequence, int outcomeId) {
		this(encounterId, sequence, outcomeId, "", 0);
	}

	public WorldInterfaceProtocol.Outcome outcome() {
		return WorldInterfaceProtocol.Outcome.fromWireId(outcomeId);
	}

	private static void write(RegistryFriendlyByteBuf buffer, PoemStartS2C value) {
		buffer.writeUUID(value.encounterId);
		buffer.writeVarLong(value.sequence);
		buffer.writeVarInt(value.outcomeId);
		buffer.writeUtf(value.worldId, 128);
		buffer.writeVarInt(value.destroyedAnchors);
	}

	private static PoemStartS2C read(RegistryFriendlyByteBuf buffer) {
		return new PoemStartS2C(buffer.readUUID(), buffer.readVarLong(), buffer.readVarInt(),
				buffer.readUtf(128), buffer.readVarInt());
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
