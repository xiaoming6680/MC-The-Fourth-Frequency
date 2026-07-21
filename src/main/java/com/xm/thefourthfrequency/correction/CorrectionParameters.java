package com.xm.thefourthfrequency.correction;

import net.minecraft.world.level.block.Block;

import java.util.Set;

public record CorrectionParameters(
		int trendSampleIntervalTicks,
		int trendSampleBudget,
		int indexRefreshBudget,
		int trendSearchRadius,
		int animalStopDistance,
		int villagerStopDistance,
		int hostileStopDistance,
		int reworkSearchRadius,
		int reworkContactTicks,
		int reworkSpawnIntervalTicks,
		int emptySegmentMinIntervalTicks,
		Set<Block> excavationBlocks) {
}
