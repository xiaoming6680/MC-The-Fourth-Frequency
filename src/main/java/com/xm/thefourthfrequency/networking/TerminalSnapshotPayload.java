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
		int initialPage,
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
		boolean localFileUnlocked,
		boolean terminalCaptured,
		long gameTime,
		int unreadCount,
		int unreadFileCount,
		List<TerminalLogEntryPayload> signalEvents,
		String activeAnomalyId,
		int activeAnomalyTicks,
		List<TerminalFilePayload> files,
		int reminderBand,
		String objectiveId,
		int objectiveProgress,
		int objectiveTarget,
		int objectiveIndex,
		boolean objectiveClaimable,
		String objectiveRewardItem,
		int objectiveRewardCount,
		/**
		 * Whether this player still owes the first-boot walkthrough.
		 *
		 * <p>Phrased as "required" rather than mirroring the stored "done" flag so the client never
		 * has to negate it, and appended at the end rather than inserted beside {@code initialPage}
		 * because {@link #read} is positional - a boolean slipped into the middle silently shifts
		 * every varint after it.</p>
		 */
		boolean onboardingRequired,
		/**
		 * Whether the terminal is asking for the player's attention right now.
		 *
		 * <p>Decided by {@code TerminalData.attentionActive}, the same call that picks which of the
		 * six item forms the player sees in their hand, so the amber lamp on the panel and the amber
		 * lamp on the device can never disagree. Sent as one settled boolean rather than left to the
		 * client to re-derive: three of its four sources are already on the wire, but the fourth -
		 * an unacknowledged navigation completion - is not, and a UI that approximated the rule from
		 * what it happened to have would drift from the item.</p>
		 *
		 * <p>Appended at the end for the same reason {@link #onboardingRequired} was: {@link #read}
		 * is positional, so a field inserted in the middle silently shifts every varint after it.</p>
		 */
		boolean attentionActive
) implements CustomPacketPayload {
	public static final int CURRENT_PROTOCOL_VERSION = 13;
	public static final Type<TerminalSnapshotPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(
			TheFourthFrequency.MOD_ID, "terminal_snapshot"));
	public static final StreamCodec<RegistryFriendlyByteBuf, TerminalSnapshotPayload> CODEC = StreamCodec.of(
			TerminalSnapshotPayload::write, TerminalSnapshotPayload::read);

	private static void write(RegistryFriendlyByteBuf buf, TerminalSnapshotPayload value) {
		buf.writeVarInt(value.protocolVersion);
		buf.writeVarInt(value.publicStationMask);
		buf.writeVarInt(value.mode);
		buf.writeVarInt(value.initialPage);
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
		buf.writeBoolean(value.localFileUnlocked);
		buf.writeBoolean(value.terminalCaptured);
		buf.writeVarLong(value.gameTime);
		buf.writeVarInt(value.unreadCount);
		buf.writeVarInt(value.unreadFileCount);
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
		buf.writeVarInt(value.objectiveIndex);
		buf.writeBoolean(value.objectiveClaimable);
		buf.writeUtf(value.objectiveRewardItem, 128);
		buf.writeVarInt(value.objectiveRewardCount);
		buf.writeBoolean(value.onboardingRequired);
		buf.writeBoolean(value.attentionActive);
	}

	private static TerminalSnapshotPayload read(RegistryFriendlyByteBuf buf) {
		return new TerminalSnapshotPayload(
				buf.readVarInt(), buf.readVarInt(),
				buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
				buf.readVarInt(),
				buf.readBoolean(), buf.readVarInt(), buf.readVarInt(), buf.readBoolean(), buf.readVarInt(),
				buf.readVarInt(),
				buf.readBoolean(), buf.readBoolean(),
				buf.readVarLong(), buf.readVarInt(), buf.readVarInt(), readLogs(buf), buf.readUtf(64), buf.readVarInt(),
				readFiles(buf), buf.readVarInt(), buf.readUtf(32), buf.readVarInt(), buf.readVarInt(),
				buf.readVarInt(), buf.readBoolean(), buf.readUtf(128), buf.readVarInt(),
				buf.readBoolean(), buf.readBoolean());
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
