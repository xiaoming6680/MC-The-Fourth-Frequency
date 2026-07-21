package com.xm.thefourthfrequency.correction;

import com.xm.thefourthfrequency.content.ModBlocks;
import com.xm.thefourthfrequency.ending.FinalConfrontationService;
import com.xm.thefourthfrequency.facility.FacilityDefinition;
import com.xm.thefourthfrequency.facility.FacilityLayout;
import com.xm.thefourthfrequency.facility.FacilityService;
import com.xm.thefourthfrequency.world.FrequencyWorldData;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public final class CorrectionTargetService {
	private CorrectionTargetService() {
	}

	public static void ensureActivated(MinecraftServer server) {
		FrequencyWorldData data = FrequencyWorldData.get(server);
		if (!CorrectionState.anomalyTracePositions(data).isEmpty()
				|| CorrectionState.terminalFacilityPosition(data).isPresent()
				|| !FinalConfrontationService.activeAnchorPositions(data, server.overworld()).isEmpty()) {
			CorrectionState.activate(data);
		}
	}

	public static List<CorrectionTarget> blockTargets(ServerLevel level) {
		FrequencyWorldData data = FrequencyWorldData.get(level.getServer());
		List<CorrectionTarget> targets = new ArrayList<>();
		for (BlockPos position : CorrectionState.anomalyTracePositions(data)) {
			if (level.getBlockState(position).is(ModBlocks.NASCENT_BODY_ORGAN)) {
				targets.add(new CorrectionTarget(CorrectionTarget.Kind.ANOMALY_TRACE, position, 0));
			}
		}
		CorrectionState.terminalFacilityPosition(data).or(() -> transportLock(data)).ifPresent(position -> {
			if (level.getBlockState(position).is(ModBlocks.ARCHIVE_LOCK)) {
				targets.add(new CorrectionTarget(CorrectionTarget.Kind.SIGNAL_SHELL, position, 1));
			}
		});
		for (BlockPos position : FinalConfrontationService.activeAnchorPositions(data, level)) {
			targets.add(new CorrectionTarget(CorrectionTarget.Kind.GROUNDING_ANCHOR, position, 2));
		}
		targets.sort(Comparator.comparingInt(CorrectionTarget::priority));
		return List.copyOf(targets);
	}

	public static Optional<CorrectionTarget> nearestTarget(ServerLevel level, BlockPos origin, int radius) {
		long radiusSquared = (long) radius * radius;
		return blockTargets(level).stream()
				.filter(target -> target.position().distSqr(origin) <= radiusSquared)
				.min(Comparator.comparingDouble((CorrectionTarget target) -> target.position().distSqr(origin))
						.thenComparingInt(CorrectionTarget::priority));
	}

	public static void setOrganForTest(MinecraftServer server, BlockPos position) {
		server.overworld().setBlockAndUpdate(position, ModBlocks.NASCENT_BODY_ORGAN.defaultBlockState());
		CorrectionState.setOrganPosition(FrequencyWorldData.get(server), position);
	}

	public static void setTerminalFacilityForTest(MinecraftServer server, BlockPos position) {
		server.overworld().setBlockAndUpdate(position, ModBlocks.ARCHIVE_LOCK.defaultBlockState());
		CorrectionState.setTerminalFacilityPosition(FrequencyWorldData.get(server), position);
	}

	private static Optional<BlockPos> transportLock(FrequencyWorldData data) {
		Optional<BlockPos> origin = FacilityService.facilityPosition(data, "transport_node");
		FacilityDefinition definition = FacilityService.definitions().stream()
				.filter(value -> value.id().equals("transport_node")).findFirst().orElse(null);
		return definition == null ? Optional.empty()
				: origin.map(position -> FacilityLayout.markerPosition(definition, position, 0));
	}
}
