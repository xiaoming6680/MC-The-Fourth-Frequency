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
		return find(level, preferred, preferred);
	}

	/**
	 * Finds somewhere to put the player back, trying {@code preferred} first and {@code fallback}
	 * before giving up on the world spawn.
	 *
	 * <p>The two-candidate form exists for chases that end somewhere the player dug to. The mirror
	 * lets them cut through terrain, and those cuts do not exist in the source world, so the spot
	 * they escaped from can be solid rock back home. Falling straight to world spawn in that case
	 * would punish the escape harder than being caught; the entry point is a far better second
	 * choice, and it is what this used to do unconditionally.</p>
	 */
	public static BlockPos find(ServerLevel level, BlockPos preferred, BlockPos fallback) {
		BlockPos near = nearby(level, preferred);
		if (near != null) return near;
		if (!fallback.equals(preferred)) {
			BlockPos alternate = nearby(level, fallback);
			if (alternate != null) return alternate;
		}
		BlockPos spawn = level.getRespawnData().pos();
		return safe(level, spawn) ? spawn : level.getHeightmapPos(
				net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, spawn);
	}

	private static BlockPos nearby(ServerLevel level, BlockPos preferred) {
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
		return null;
	}

	private static boolean safe(ServerLevel level, BlockPos feet) {
		if (feet.getY() <= level.getMinY() || feet.getY() + 1 >= level.getMaxY()) return false;
		if (!level.hasChunkAt(feet)) level.getChunkAt(feet);
		return level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()
				&& level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty()
				&& !level.getBlockState(feet.below()).getCollisionShape(level, feet.below()).isEmpty();
	}
}
