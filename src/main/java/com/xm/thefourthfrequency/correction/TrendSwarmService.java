package com.xm.thefourthfrequency.correction;

import com.xm.thefourthfrequency.world.FrequencyWorldData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.npc.villager.AbstractVillager;

import java.util.Optional;

public final class TrendSwarmService {
	public static final String TREND_TAG = "thefourthfrequency:trend_swarm";
	private static CorrectionParameters parameters;

	private TrendSwarmService() {
	}

	public static void initialize(CorrectionParameters loaded) {
		parameters = loaded;
		CorrectionSpatialIndex.initialize();
	}

	public static int updateServer(MinecraftServer server) {
		if (parameters == null) {
			throw new IllegalStateException("Trend swarm service is not initialized");
		}
		int work = CorrectionSpatialIndex.refresh(parameters.indexRefreshBudget());
		FrequencyWorldData data = FrequencyWorldData.get(server);
		if (!CorrectionState.active(data)
				|| server.getTickCount() % parameters.trendSampleIntervalTicks() != 0) {
			return work;
		}
		ServerLevel level = server.overworld();
		Optional<CorrectionTarget> target = CorrectionTargetService.blockTargets(level).stream().findFirst();
		if (target.isEmpty()) {
			return work;
		}
		BlockPos targetPosition = target.get().position();
		for (Mob mob : CorrectionSpatialIndex.sampleNear(level, targetPosition,
				parameters.trendSearchRadius(), parameters.trendSampleBudget())) {
			applyTrend(mob, targetPosition);
			work++;
		}
		return work;
	}

	public static void applyTrend(Mob mob, BlockPos target) {
		if (parameters == null) {
			throw new IllegalStateException("Trend swarm service is not initialized");
		}
		int stopDistance = stopDistance(mob);
		double distanceSquared = mob.distanceToSqr(target.getCenter());
		mob.addTag(TREND_TAG);
		mob.setTarget(null);
		mob.getLookControl().setLookAt(target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5, 30.0F, 30.0F);
		if (distanceSquared <= (double) stopDistance * stopDistance) {
			mob.getNavigation().stop();
			mob.setDeltaMovement(0.0, mob.getDeltaMovement().y, 0.0);
		} else {
			mob.getNavigation().moveTo(target.getX() + 0.5, target.getY(), target.getZ() + 0.5, 0.72);
		}
	}

	public static int stopDistance(Mob mob) {
		if (mob instanceof Animal) {
			return parameters.animalStopDistance();
		}
		if (mob instanceof AbstractVillager) {
			return parameters.villagerStopDistance();
		}
		if (mob instanceof Monster) {
			return parameters.hostileStopDistance();
		}
		throw new IllegalArgumentException("Entity is not trend-eligible: " + mob.getType());
	}

	public static CorrectionParameters parameters() {
		return parameters;
	}
}
