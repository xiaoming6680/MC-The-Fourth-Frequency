package com.xm.thefourthfrequency.ending;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EndPortalFrameBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/** Geometry-only helpers for recognizing a complete vanilla stronghold portal ring. */
public final class StrongholdPortalService {
	private static final int[][] FRAME_OFFSETS = {
			{-2, 0}, {-2, 1}, {-2, -1},
			{2, 0}, {2, 1}, {2, -1},
			{0, -2}, {1, -2}, {-1, -2},
			{0, 2}, {1, 2}, {-1, 2}
	};

	private StrongholdPortalService() {
	}

	public static Optional<BlockPos> findPortalRingNear(Level level, BlockPos origin, int radius) {
		Set<Long> checked = new HashSet<>();
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
		for (int y = origin.getY() - 4; y <= origin.getY() + 4; y++) {
			for (int x = origin.getX() - radius; x <= origin.getX() + radius; x++) {
				for (int z = origin.getZ() - radius; z <= origin.getZ() + radius; z++) {
					cursor.set(x, y, z);
					if (!(level.getBlockState(cursor).getBlock() instanceof EndPortalFrameBlock)) continue;
					Optional<BlockPos> center = portalCenterFromFrame(level, cursor.immutable());
					if (center.isPresent() && checked.add(center.get().asLong())) return center;
				}
			}
		}
		return Optional.empty();
	}

	public static boolean validPortalRing(Level level, BlockPos center) {
		for (int[] offset : FRAME_OFFSETS) {
			BlockState state = level.getBlockState(center.offset(offset[0], 0, offset[1]));
			if (!(state.getBlock() instanceof EndPortalFrameBlock)
					|| state.getValue(EndPortalFrameBlock.FACING) != expectedFacing(offset[0], offset[1])) {
				return false;
			}
		}
		return true;
	}

	public static int eyeCount(Level level, BlockPos center) {
		int eyes = 0;
		for (int[] offset : FRAME_OFFSETS) {
			BlockState state = level.getBlockState(center.offset(offset[0], 0, offset[1]));
			if (state.getBlock() instanceof EndPortalFrameBlock
					&& state.getValue(EndPortalFrameBlock.HAS_EYE)) eyes++;
		}
		return eyes;
	}

	private static Optional<BlockPos> portalCenterFromFrame(Level level, BlockPos frame) {
		for (int[] offset : FRAME_OFFSETS) {
			BlockPos center = frame.offset(-offset[0], 0, -offset[1]);
			if (validPortalRing(level, center)) return Optional.of(center);
		}
		return Optional.empty();
	}

	private static Direction expectedFacing(int x, int z) {
		if (x == -2) return Direction.EAST;
		if (x == 2) return Direction.WEST;
		if (z == -2) return Direction.SOUTH;
		return Direction.NORTH;
	}
}
