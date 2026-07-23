package com.xm.thefourthfrequency.correction;

import com.xm.thefourthfrequency.content.ModBlocks;
import com.xm.thefourthfrequency.world.FrequencyWorldData;
import net.minecraft.core.BlockPos;
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
		if (!CorrectionState.anomalyTracePositions(data).isEmpty()) CorrectionState.activate(data);
	}

	public static List<CorrectionTarget> blockTargets(ServerLevel level) {
		FrequencyWorldData data = FrequencyWorldData.get(level.getServer());
		List<CorrectionTarget> targets = new ArrayList<>();
		for (BlockPos position : CorrectionState.anomalyTracePositions(data)) {
			if (level.getBlockState(position).is(ModBlocks.NASCENT_BODY_ORGAN)) {
				targets.add(new CorrectionTarget(CorrectionTarget.Kind.ANOMALY_TRACE, position, 0));
			}
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

}
