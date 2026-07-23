package com.xm.thefourthfrequency.pursuit;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/** Finds a safe return point near the recorded source without loading an unbounded area. */
public final class PursuitReturnLocator {
	private static final int HORIZONTAL_RADIUS = 8;
	private static final int VERTICAL_RADIUS = 4;

	private PursuitReturnLocator() {
	}

	public static BlockPos find(ServerLevel level, BlockPos preferred) {
		if (safe(level, preferred)) return preferred;
		for (int radius = 1; radius <= HORIZONTAL_RADIUS; radius++) {
			for (int dy = -VERTICAL_RADIUS; dy <= VERTICAL_RADIUS; dy++) {
				for (int dx = -radius; dx <= radius; dx++) {
					for (int dz = -radius; dz <= radius; dz++) {
						if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) continue;
						BlockPos candidate = preferred.offset(dx, dy, dz);
						if (safe(level, candidate)) return candidate;
					}
				}
			}
		}
		BlockPos spawn = level.getRespawnData().pos();
		return safe(level, spawn) ? spawn : level.getHeightmapPos(
				net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, spawn);
	}

	private static boolean safe(ServerLevel level, BlockPos feet) {
		if (feet.getY() <= level.getMinY() || feet.getY() + 1 >= level.getMaxY()) return false;
		if (!level.hasChunkAt(feet)) level.getChunkAt(feet);
		return level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()
				&& level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty()
				&& !level.getBlockState(feet.below()).getCollisionShape(level, feet.below()).isEmpty();
	}
}
