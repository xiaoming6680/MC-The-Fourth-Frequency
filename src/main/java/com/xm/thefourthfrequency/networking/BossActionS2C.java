package com.xm.thefourthfrequency.networking;

import com.xm.thefourthfrequency.bootstrap.TheFourthFrequency;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Deterministic action envelope; reconnecting clients can resume from startTick and seed. */
public record BossActionS2C(
		UUID encounterId,
		long sequence,
		int actionId,
		long startTick,
		int duration,
		List<UUID> targetIds,
		long seed,
		int flags
) implements CustomPacketPayload {
	public static final int PROTOCOL_VERSION = WorldInterfaceProtocol.VERSION;
	public static final Type<BossActionS2C> TYPE = new Type<>(Identifier.fromNamespaceAndPath(
			TheFourthFrequency.MOD_ID, "world_interface_boss_action_v1"));
	public static final StreamCodec<RegistryFriendlyByteBuf, BossActionS2C> CODEC = StreamCodec.of(
			BossActionS2C::write, BossActionS2C::read);

	public BossActionS2C {
		Objects.requireNonNull(encounterId, "encounterId");
		WorldInterfaceProtocol.requireSequence(sequence);
		WorldInterfaceProtocol.BossAction.fromWireId(actionId);
		if (startTick < 0L || duration < 0) {
			throw new IllegalArgumentException("Action start tick and duration must be non-negative");
		}
		targetIds = WorldInterfaceProtocol.copyBounded(targetIds,
				WorldInterfaceProtocol.MAX_PARTICIPANTS, "boss action targets");
	}

	public WorldInterfaceProtocol.BossAction action() {
		return WorldInterfaceProtocol.BossAction.fromWireId(actionId);
	}

	private static void write(RegistryFriendlyByteBuf buffer, BossActionS2C value) {
		buffer.writeUUID(value.encounterId);
		buffer.writeVarLong(value.sequence);
		buffer.writeVarInt(value.actionId);
		buffer.writeVarLong(value.startTick);
		buffer.writeVarInt(value.duration);
		buffer.writeVarInt(value.targetIds.size());
		for (UUID targetId : value.targetIds) buffer.writeUUID(targetId);
		buffer.writeLong(value.seed);
		buffer.writeVarInt(value.flags);
	}

	private static BossActionS2C read(RegistryFriendlyByteBuf buffer) {
		UUID encounterId = buffer.readUUID();
		long sequence = buffer.readVarLong();
		int actionId = buffer.readVarInt();
		long startTick = buffer.readVarLong();
		int duration = buffer.readVarInt();
		int targetCount = WorldInterfaceProtocol.readBoundedSize(buffer,
				WorldInterfaceProtocol.MAX_PARTICIPANTS, "boss action targets");
		List<UUID> targetIds = new ArrayList<>(targetCount);
		for (int index = 0; index < targetCount; index++) targetIds.add(buffer.readUUID());
		return new BossActionS2C(encounterId, sequence, actionId, startTick, duration,
				targetIds, buffer.readLong(), buffer.readVarInt());
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
