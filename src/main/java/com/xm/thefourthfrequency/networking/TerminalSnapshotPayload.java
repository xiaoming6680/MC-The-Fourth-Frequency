package com.xm.thefourthfrequency.networking;

import com.xm.thefourthfrequency.bootstrap.TheFourthFrequency;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.List;

public record TerminalSnapshotPayload(
		int protocolVersion,
		int publicStationMask,
		int mode,
		int tuning,
		int visualStage,
		int bandStage,
		int cacheVariant,
		boolean secondCacheUnlocked,
		int secondCacheVariant,
		int personality,
		boolean continuityLearned,
		int continuityConfidence,
		int portalTransitions,
		int bodyProgress,
		int bodyStage,
		int capabilityMask,
		int shelterEvidence,
		int warehouseEvidence,
		int mineEvidence,
		int observationEvidence,
		boolean localFileUnlocked,
		boolean riftLocated,
		int riftDx,
		int riftDz,
		int riftY,
		int endingVersion,
		int endingOutcome,
		boolean terminalCaptured,
		long gameTime,
		int unreadCount,
		List<TerminalLogEntryPayload> signalEvents,
		String activeAnomalyId,
		int activeAnomalyTicks,
		List<TerminalFilePayload> files,
		int reminderBand,
		String objectiveId,
		int objectiveProgress,
		int objectiveTarget
) implements CustomPacketPayload {
	public static final int CURRENT_PROTOCOL_VERSION = 5;
	public static final Type<TerminalSnapshotPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(
			TheFourthFrequency.MOD_ID, "terminal_snapshot"));
	public static final StreamCodec<RegistryFriendlyByteBuf, TerminalSnapshotPayload> CODEC = StreamCodec.of(
			TerminalSnapshotPayload::write, TerminalSnapshotPayload::read);

	private static void write(RegistryFriendlyByteBuf buf, TerminalSnapshotPayload value) {
		buf.writeVarInt(value.protocolVersion);
		buf.writeVarInt(value.publicStationMask);
		buf.writeVarInt(value.mode);
		buf.writeVarInt(value.tuning);
		buf.writeVarInt(value.visualStage);
		buf.writeVarInt(value.bandStage);
		buf.writeVarInt(value.cacheVariant);
		buf.writeBoolean(value.secondCacheUnlocked);
		buf.writeVarInt(value.secondCacheVariant);
		buf.writeVarInt(value.personality);
		buf.writeBoolean(value.continuityLearned);
		buf.writeVarInt(value.continuityConfidence);
		buf.writeVarInt(value.portalTransitions);
		buf.writeVarInt(value.bodyProgress);
		buf.writeVarInt(value.bodyStage);
		buf.writeVarInt(value.capabilityMask);
		buf.writeVarInt(value.shelterEvidence);
		buf.writeVarInt(value.warehouseEvidence);
		buf.writeVarInt(value.mineEvidence);
		buf.writeVarInt(value.observationEvidence);
		buf.writeBoolean(value.localFileUnlocked);
		buf.writeBoolean(value.riftLocated);
		buf.writeVarInt(value.riftDx);
		buf.writeVarInt(value.riftDz);
		buf.writeVarInt(value.riftY);
		buf.writeVarInt(value.endingVersion);
		buf.writeVarInt(value.endingOutcome);
		buf.writeBoolean(value.terminalCaptured);
		buf.writeVarLong(value.gameTime);
		buf.writeVarInt(value.unreadCount);
		buf.writeVarInt(value.signalEvents.size());
		for (TerminalLogEntryPayload entry : value.signalEvents) TerminalLogEntryPayload.write(buf, entry);
		buf.writeUtf(value.activeAnomalyId, 64);
		buf.writeVarInt(value.activeAnomalyTicks);
		buf.writeVarInt(value.files.size());
		for (TerminalFilePayload file : value.files) TerminalFilePayload.write(buf, file);
		buf.writeVarInt(value.reminderBand);
		buf.writeUtf(value.objectiveId, 32);
		buf.writeVarInt(value.objectiveProgress);
		buf.writeVarInt(value.objectiveTarget);
	}

	private static TerminalSnapshotPayload read(RegistryFriendlyByteBuf buf) {
		return new TerminalSnapshotPayload(
				buf.readVarInt(), buf.readVarInt(),
				buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
				buf.readBoolean(), buf.readVarInt(), buf.readVarInt(), buf.readBoolean(), buf.readVarInt(),
				buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
				buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
				buf.readBoolean(), buf.readBoolean(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
				buf.readVarInt(), buf.readVarInt(), buf.readBoolean(),
				buf.readVarLong(), buf.readVarInt(), readLogs(buf), buf.readUtf(64), buf.readVarInt(),
				readFiles(buf), buf.readVarInt(), buf.readUtf(32), buf.readVarInt(), buf.readVarInt());
	}

	private static List<TerminalLogEntryPayload> readLogs(RegistryFriendlyByteBuf buf) {
		int size = Math.clamp(buf.readVarInt(), 0, 128);
		java.util.ArrayList<TerminalLogEntryPayload> result = new java.util.ArrayList<>(size);
		for (int i = 0; i < size; i++) result.add(TerminalLogEntryPayload.read(buf));
		return List.copyOf(result);
	}

	private static List<TerminalFilePayload> readFiles(RegistryFriendlyByteBuf buf) {
		int size = Math.clamp(buf.readVarInt(), 0, 12);
		java.util.ArrayList<TerminalFilePayload> result = new java.util.ArrayList<>(size);
		for (int i = 0; i < size; i++) result.add(TerminalFilePayload.read(buf));
		return List.copyOf(result);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
