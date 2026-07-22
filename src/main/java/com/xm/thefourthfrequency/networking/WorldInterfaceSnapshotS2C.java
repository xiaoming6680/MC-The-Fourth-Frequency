package com.xm.thefourthfrequency.networking;

import com.xm.thefourthfrequency.bootstrap.TheFourthFrequency;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Authoritative low-frequency encounter projection used for HUD, portals and reconnects. */
public record WorldInterfaceSnapshotS2C(
		int protocolVersion,
		UUID encounterId,
		long sequence,
		int stageId,
		int formId,
		UUID bossId,
		BlockPos center,
		float maxHealth,
		float currentHealth,
		int anchorAliveMask,
		long elapsedTicks,
		long penaltyTicks,
		boolean timerPaused,
		long serverTick,
		int gatewayStateId,
		List<BlockPos> gatewayPositions,
		int outcomeId,
		float failureProgress
) implements CustomPacketPayload {
	public static final int PROTOCOL_VERSION = WorldInterfaceProtocol.VERSION;
	public static final Type<WorldInterfaceSnapshotS2C> TYPE = new Type<>(Identifier.fromNamespaceAndPath(
			TheFourthFrequency.MOD_ID, "world_interface_snapshot_v1"));
	public static final StreamCodec<RegistryFriendlyByteBuf, WorldInterfaceSnapshotS2C> CODEC = StreamCodec.of(
			WorldInterfaceSnapshotS2C::write, WorldInterfaceSnapshotS2C::read);

	public WorldInterfaceSnapshotS2C {
		WorldInterfaceProtocol.requireVersion(protocolVersion);
		Objects.requireNonNull(encounterId, "encounterId");
		WorldInterfaceProtocol.requireSequence(sequence);
		WorldInterfaceProtocol.Stage.fromWireId(stageId);
		WorldInterfaceProtocol.Form.fromWireId(formId);
		Objects.requireNonNull(bossId, "bossId");
		Objects.requireNonNull(center, "center");
		if (!Float.isFinite(maxHealth) || !Float.isFinite(currentHealth)
				|| maxHealth < 0.0F || currentHealth < 0.0F) {
			throw new IllegalArgumentException("Virtual health values must be finite and non-negative");
		}
		if ((anchorAliveMask & ~WorldInterfaceProtocol.ANCHOR_MASK) != 0) {
			throw new IllegalArgumentException("Anchor mask contains bits outside the ten authoritative anchors");
		}
		if (elapsedTicks < 0L || penaltyTicks < 0L || serverTick < 0L) {
			throw new IllegalArgumentException("Encounter clocks must be non-negative");
		}
		WorldInterfaceProtocol.GatewayState.fromWireId(gatewayStateId);
		gatewayPositions = WorldInterfaceProtocol.copyBounded(gatewayPositions,
				WorldInterfaceProtocol.MAX_GATEWAYS, "gateway positions");
		WorldInterfaceProtocol.Outcome.fromWireId(outcomeId);
		if (!Float.isFinite(failureProgress) || failureProgress < 0.0F || failureProgress > 1.0F) {
			throw new IllegalArgumentException("Failure progress must be within [0, 1]");
		}
	}

	public WorldInterfaceProtocol.Stage stage() { return WorldInterfaceProtocol.Stage.fromWireId(stageId); }
	public WorldInterfaceProtocol.Form form() { return WorldInterfaceProtocol.Form.fromWireId(formId); }
	public WorldInterfaceProtocol.GatewayState gatewayState() {
		return WorldInterfaceProtocol.GatewayState.fromWireId(gatewayStateId);
	}
	public WorldInterfaceProtocol.Outcome outcome() { return WorldInterfaceProtocol.Outcome.fromWireId(outcomeId); }

	private static void write(RegistryFriendlyByteBuf buffer, WorldInterfaceSnapshotS2C value) {
		buffer.writeVarInt(value.protocolVersion);
		buffer.writeUUID(value.encounterId);
		buffer.writeVarLong(value.sequence);
		buffer.writeVarInt(value.stageId);
		buffer.writeVarInt(value.formId);
		buffer.writeUUID(value.bossId);
		buffer.writeBlockPos(value.center);
		buffer.writeFloat(value.maxHealth);
		buffer.writeFloat(value.currentHealth);
		buffer.writeVarInt(value.anchorAliveMask);
		buffer.writeVarLong(value.elapsedTicks);
		buffer.writeVarLong(value.penaltyTicks);
		buffer.writeBoolean(value.timerPaused);
		buffer.writeVarLong(value.serverTick);
		buffer.writeVarInt(value.gatewayStateId);
		buffer.writeVarInt(value.gatewayPositions.size());
		for (BlockPos gatewayPosition : value.gatewayPositions) buffer.writeBlockPos(gatewayPosition);
		buffer.writeVarInt(value.outcomeId);
		buffer.writeFloat(value.failureProgress);
	}

	private static WorldInterfaceSnapshotS2C read(RegistryFriendlyByteBuf buffer) {
		int protocolVersion = buffer.readVarInt();
		UUID encounterId = buffer.readUUID();
		long sequence = buffer.readVarLong();
		int stageId = buffer.readVarInt();
		int formId = buffer.readVarInt();
		UUID bossId = buffer.readUUID();
		BlockPos center = buffer.readBlockPos();
		float maxHealth = buffer.readFloat();
		float currentHealth = buffer.readFloat();
		int anchorAliveMask = buffer.readVarInt();
		long elapsedTicks = buffer.readVarLong();
		long penaltyTicks = buffer.readVarLong();
		boolean timerPaused = buffer.readBoolean();
		long serverTick = buffer.readVarLong();
		int gatewayStateId = buffer.readVarInt();
		int gatewayCount = WorldInterfaceProtocol.readBoundedSize(buffer,
				WorldInterfaceProtocol.MAX_GATEWAYS, "gateway positions");
		List<BlockPos> gatewayPositions = new ArrayList<>(gatewayCount);
		for (int index = 0; index < gatewayCount; index++) gatewayPositions.add(buffer.readBlockPos());
		return new WorldInterfaceSnapshotS2C(protocolVersion, encounterId, sequence, stageId, formId,
				bossId, center, maxHealth, currentHealth, anchorAliveMask, elapsedTicks, penaltyTicks,
				timerPaused, serverTick, gatewayStateId, gatewayPositions, buffer.readVarInt(), buffer.readFloat());
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
