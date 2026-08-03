package com.xm.thefourthfrequency.ending;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The one description of the central altar's geometry.
 *
 * <p>It used to be a flat eleven-by-eleven patterned floor written independently by the arena
 * preparation and by the resonance core, which meant two chequerboards that had to be kept in step
 * by hand and a centrepiece that read as a rug rather than as the place the whole ending happens.
 * The shape is a terrace now - an eleven-wide base, a seven-wide step, a three-wide platform and
 * four corner pillars framing it - and both builders read it from here.</p>
 *
 * <p>Every query is a pure function of the offset from the altar centre, so writing the altar is
 * idempotent: building it twice produces the same blocks in the same places.</p>
 */
public final class AltarShape {
	/** Half-width of the base course. The altar is {@code 2 * RADIUS + 1} blocks across. */
	public static final int RADIUS = 5;
	/** Highest offset any part of the altar reaches, the corner pillars included. */
	public static final int MAX_OFFSET = 4;
	/** Air kept clear above each column, so terrain can never bury the terrace. */
	public static final int HEADROOM = 5;
	/** The core stands on the top platform rather than being set into it. */
	private static final int CORE_OFFSET = 3;
	private static final int PILLAR_EDGE = 3;
	private static final int PLATFORM_EDGE = 1;
	private static final int STEP_EDGE = 3;

	private AltarShape() {
	}

	/** Where the resonance core sits, given the altar's floor centre. */
	public static BlockPos corePosition(BlockPos center) {
		return center.above(CORE_OFFSET);
	}

	/** The altar floor centre, given where the resonance core sits. */
	public static BlockPos centerFromCore(BlockPos corePosition) {
		return corePosition.below(CORE_OFFSET);
	}

	/**
	 * Topmost solid offset above the altar centre for this column, or {@code -1} for a column the
	 * altar does not cover.
	 */
	public static int topOffset(int dx, int dz) {
		int edge = Math.max(Math.abs(dx), Math.abs(dz));
		if (edge > RADIUS) return -1;
		if (isPillar(dx, dz)) return MAX_OFFSET;
		if (edge <= PLATFORM_EDGE) return 2;
		if (edge <= STEP_EDGE) return 1;
		return 0;
	}

	/** The four columns that carry the corner pillars, on the outer corners of the middle step. */
	public static boolean isPillar(int dx, int dz) {
		return Math.abs(dx) == PILLAR_EDGE && Math.abs(dz) == PILLAR_EDGE;
	}

	/**
	 * @param top the column's {@link #topOffset}, passed in so callers that already walked a column
	 *            do not recompute it per block
	 */
	public static BlockState state(int dx, int dy, int dz, int top) {
		int edge = Math.max(Math.abs(dx), Math.abs(dz));
		// The pillar shaft leaves the terrace behind above the step it stands on.
		if (isPillar(dx, dz) && dy > 1) {
			return dy == MAX_OFFSET ? Blocks.AMETHYST_BLOCK.defaultBlockState()
					: Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
		}
		// Risers are never seen from above; only the tread of each step carries a material.
		if (dy < top) return Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
		if (edge <= PLATFORM_EDGE) return Blocks.CRYING_OBSIDIAN.defaultBlockState();
		if (edge <= STEP_EDGE) return Blocks.PURPUR_BLOCK.defaultBlockState();
		if (edge == RADIUS) return Blocks.OBSIDIAN.defaultBlockState();
		return Blocks.END_STONE_BRICKS.defaultBlockState();
	}
}
