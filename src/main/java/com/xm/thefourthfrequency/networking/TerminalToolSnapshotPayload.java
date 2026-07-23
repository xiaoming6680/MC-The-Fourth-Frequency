package com.xm.thefourthfrequency.networking;

import com.xm.thefourthfrequency.bootstrap.TheFourthFrequency;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record TerminalToolSnapshotPayload(
		int protocolVersion,
		int availableToolsMask,
		int selectedTool,
		int guidanceTool,
		int recommendedPrimaryTool,
		int recommendedSecondaryTool,
		int availableResourcesMask,
		int navigationTargetsMask,
		int selectedNavigationTarget,
		boolean unstableSignalAvailable,
		boolean toolsDisabled,
		int toolsDisabledTicks,
		int selectedResource,
		int mineralScanTicks,
		boolean navigationCompletionAvailable,
		int navigationCompletionDirection,
		int weather,
		long dayTime,
		int ticksUntilLightChange,
		int playerY,
		boolean homeKnown,
		boolean homeUsesBed,
		boolean homeSameDimension,
		int homeDx,
		int homeDz,
		int homeY,
		String homeDimension,
		boolean portalKnown,
		boolean portalSameDimension,
		int portalDx,
		int portalDz,
		int portalY,
		String portalDimension,
		int nearbySignalCount,
		boolean receiverAvailable,
		int receiverTarget,
		int receiverStrength,
		int receiverLockTicks,
		int specialFileCount,
		int storySignalCount,
		int eyeSampleCount,
		boolean strongholdKnown,
		boolean strongholdSameDimension,
		int strongholdDx,
		int strongholdDz,
		String strongholdDimension,
		int strongholdMinDistance,
		int strongholdMaxDistance
) implements CustomPacketPayload {
	public static final int CURRENT_PROTOCOL_VERSION = 4;
	public static final Type<TerminalToolSnapshotPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(
			TheFourthFrequency.MOD_ID, "terminal_tool_snapshot"));
	public static final StreamCodec<RegistryFriendlyByteBuf, TerminalToolSnapshotPayload> CODEC = StreamCodec.of(
			TerminalToolSnapshotPayload::write, TerminalToolSnapshotPayload::read);

	private static void write(RegistryFriendlyByteBuf buf, TerminalToolSnapshotPayload value) {
		buf.writeVarInt(value.protocolVersion);
		buf.writeVarInt(value.availableToolsMask);
		buf.writeVarInt(value.selectedTool);
		buf.writeVarInt(value.guidanceTool);
		buf.writeVarInt(value.recommendedPrimaryTool);
		buf.writeVarInt(value.recommendedSecondaryTool);
		buf.writeVarInt(value.availableResourcesMask);
		buf.writeVarInt(value.navigationTargetsMask);
		buf.writeVarInt(value.selectedNavigationTarget);
		buf.writeBoolean(value.unstableSignalAvailable);
		buf.writeBoolean(value.toolsDisabled);
		buf.writeVarInt(value.toolsDisabledTicks);
		buf.writeVarInt(value.selectedResource);
		buf.writeVarInt(value.mineralScanTicks);
		buf.writeBoolean(value.navigationCompletionAvailable);
		buf.writeVarInt(value.navigationCompletionDirection);
		buf.writeVarInt(value.weather);
		buf.writeVarLong(value.dayTime);
		buf.writeVarInt(value.ticksUntilLightChange);
		buf.writeVarInt(value.playerY);
		buf.writeBoolean(value.homeKnown);
		buf.writeBoolean(value.homeUsesBed);
		buf.writeBoolean(value.homeSameDimension);
		buf.writeVarInt(value.homeDx);
		buf.writeVarInt(value.homeDz);
		buf.writeVarInt(value.homeY);
		buf.writeUtf(value.homeDimension, 128);
		buf.writeBoolean(value.portalKnown);
		buf.writeBoolean(value.portalSameDimension);
		buf.writeVarInt(value.portalDx);
		buf.writeVarInt(value.portalDz);
		buf.writeVarInt(value.portalY);
		buf.writeUtf(value.portalDimension, 128);
		buf.writeVarInt(value.nearbySignalCount);
		buf.writeBoolean(value.receiverAvailable);
		buf.writeVarInt(value.receiverTarget);
		buf.writeVarInt(value.receiverStrength);
		buf.writeVarInt(value.receiverLockTicks);
		buf.writeVarInt(value.specialFileCount);
		buf.writeVarInt(value.storySignalCount);
		buf.writeVarInt(value.eyeSampleCount);
		buf.writeBoolean(value.strongholdKnown);
		buf.writeBoolean(value.strongholdSameDimension);
		buf.writeVarInt(value.strongholdDx);
		buf.writeVarInt(value.strongholdDz);
		buf.writeUtf(value.strongholdDimension, 128);
		buf.writeVarInt(value.strongholdMinDistance);
		buf.writeVarInt(value.strongholdMaxDistance);
	}

	private static TerminalToolSnapshotPayload read(RegistryFriendlyByteBuf buf) {
		return new TerminalToolSnapshotPayload(
				buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
				buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
				buf.readVarInt(), buf.readBoolean(),
				buf.readBoolean(), buf.readVarInt(), buf.readVarInt(),
				buf.readVarInt(), buf.readBoolean(), buf.readVarInt(),
				buf.readVarInt(), buf.readVarLong(), buf.readVarInt(), buf.readVarInt(),
				buf.readBoolean(), buf.readBoolean(), buf.readBoolean(),
				buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readUtf(128),
				buf.readBoolean(), buf.readBoolean(), buf.readVarInt(), buf.readVarInt(),
				buf.readVarInt(), buf.readUtf(128), buf.readVarInt(), buf.readBoolean(),
				buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
				buf.readVarInt(), buf.readVarInt(), buf.readBoolean(), buf.readBoolean(),
				buf.readVarInt(), buf.readVarInt(), buf.readUtf(128), buf.readVarInt(), buf.readVarInt());
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
