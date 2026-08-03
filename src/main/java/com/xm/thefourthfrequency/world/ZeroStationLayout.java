package com.xm.thefourthfrequency.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

import java.util.ArrayList;
import java.util.List;

/**
 * The shape of Relay Station Zero: the one building the story hands the player before it hands
 * them anything else.
 *
 * <p>The plan is a pure function of the station centre. The build is spread over many ticks and a
 * shutdown can interrupt it, so a resumed build has to regenerate a byte-identical sequence from
 * the persisted cursor. Nothing here may read the world, and every weathering decision comes from
 * a hash of the local offset rather than a random source.</p>
 *
 * <p>Ordering is load-bearing rather than cosmetic. The floor is written first so a player who
 * connects mid-build always has ground under them; the envelope is cleared before any masonry so
 * terrain can never survive inside a wall; the two-block fixtures - bed, door - come last, once the
 * shell that supports them exists.</p>
 *
 * <p>The station reads as still powered and already abandoned, which is the only claim the opening
 * needs to make: its lamps are lit, its mast still stands, its terminal rack is empty, and one bay
 * has come down. Nothing here is a puzzle and nothing here is loot.</p>
 */
public final class ZeroStationLayout {
	public record Placement(BlockPos position, BlockState state) {
	}

	/** Half-width along X. The station is {@code 2 * HALF_WIDTH + 1} blocks across. */
	public static final int HALF_WIDTH = 5;
	/** Half-depth along Z. The player wakes facing the equipment wall at {@code -HALF_DEPTH}. */
	public static final int HALF_DEPTH = 4;
	/** Floor course, one below the standing surface at the station centre. */
	public static final int FLOOR = -1;
	/** Lowest foundation course; deep enough that a slope never leaves the platform floating. */
	public static final int FOUNDATION_BOTTOM = -4;
	/** Highest wall course. Interior headroom is {@code WALL_TOP + 1} blocks. */
	public static final int WALL_TOP = 3;
	private static final int ROOF = 4;
	private static final int PARAPET = 5;
	private static final int MAST_TOP = 8;
	/** Terrain is cleared this high so a tree rooted in the footprint cannot leave a floating trunk. */
	private static final int ENVELOPE_TOP = 6;
	private static final int PORCH_NEAR = HALF_DEPTH + 1;
	private static final int PORCH_FAR = HALF_DEPTH + 2;
	private static final int PORCH_HALF_WIDTH = 1;
	private static final int DOOR_X = 0;
	private static final int MAST_Z = -2;

	private static final BlockState AIR = Blocks.AIR.defaultBlockState();
	/** Waxed so the grille the collapse left behind stays the colour it was found in. */
	private static final BlockState AGED_COPPER_BARS = Blocks.COPPER_BARS.waxedExposed().defaultBlockState();

	private ZeroStationLayout() {
	}

	/**
	 * A wall column the layout always fills with plain masonry - no window, no lamp, no doorway.
	 * Persistence proofs break this block to show a completed station is never rebuilt.
	 */
	public static BlockPos solidWallSample(BlockPos center) {
		return center.offset(-HALF_WIDTH, 1, 0);
	}

	public static List<Placement> create(BlockPos center) {
		List<Placement> placements = new ArrayList<>();
		addFloor(placements, center);
		addEnvelopeClearing(placements, center);
		addFoundation(placements, center);
		addWalls(placements, center);
		addRoof(placements, center);
		addMast(placements, center);
		addFixtures(placements, center);
		return List.copyOf(placements);
	}

	private static void addFloor(List<Placement> placements, BlockPos center) {
		for (int x = -HALF_WIDTH; x <= HALF_WIDTH; x++) {
			for (int z = -HALF_DEPTH; z <= HALF_DEPTH; z++) {
				placements.add(at(center, x, FLOOR, z, floorState(x, z)));
			}
		}
		// The porch keeps the doorway usable when the station lands on the high side of a slope.
		for (int x = -PORCH_HALF_WIDTH; x <= PORCH_HALF_WIDTH; x++) {
			placements.add(at(center, x, FLOOR, PORCH_NEAR, masonry(x, FLOOR, PORCH_NEAR)));
			placements.add(at(center, x, FLOOR, PORCH_FAR, Blocks.STONE_BRICK_SLAB.defaultBlockState()));
		}
	}

	private static BlockState floorState(int x, int z) {
		// The wake-up tile: a grate over a lamp that is still burning underneath the plate. It is the
		// first thing the player looks down at, and the whole station's claim in one block.
		if (x == 0 && z == 0) {
			return Blocks.WAXED_EXPOSED_COPPER_GRATE.defaultBlockState();
		}
		if (Math.abs(x) <= 1 && Math.abs(z) <= 1) {
			return Blocks.POLISHED_ANDESITE.defaultBlockState();
		}
		if (isCollapsedBay(x, z)) {
			return (grain(x, FLOOR, z) & 1) == 0
					? Blocks.COBBLESTONE.defaultBlockState()
					: Blocks.GRAVEL.defaultBlockState();
		}
		return masonry(x, FLOOR, z);
	}

	private static void addEnvelopeClearing(List<Placement> placements, BlockPos center) {
		for (int y = 0; y <= ENVELOPE_TOP; y++) {
			for (int x = -HALF_WIDTH; x <= HALF_WIDTH; x++) {
				for (int z = -HALF_DEPTH; z <= HALF_DEPTH; z++) {
					placements.add(at(center, x, y, z, AIR));
				}
			}
		}
		for (int y = 0; y <= 2; y++) {
			for (int x = -PORCH_HALF_WIDTH; x <= PORCH_HALF_WIDTH; x++) {
				placements.add(at(center, x, y, PORCH_NEAR, AIR));
				placements.add(at(center, x, y, PORCH_FAR, AIR));
			}
		}
		// The mast reaches above the cleared envelope, so its own column gets the same treatment.
		for (int y = ENVELOPE_TOP + 1; y <= MAST_TOP; y++) {
			for (int x = -1; x <= 1; x++) {
				for (int z = MAST_Z - 1; z <= MAST_Z + 1; z++) {
					placements.add(at(center, x, y, z, AIR));
				}
			}
		}
	}

	private static void addFoundation(List<Placement> placements, BlockPos center) {
		for (int y = FOUNDATION_BOTTOM; y < FLOOR; y++) {
			for (int x = -HALF_WIDTH; x <= HALF_WIDTH; x++) {
				for (int z = -HALF_DEPTH; z <= HALF_DEPTH; z++) {
					placements.add(at(center, x, y, z, foundationState(x, y, z)));
				}
			}
		}
		for (int x = -PORCH_HALF_WIDTH; x <= PORCH_HALF_WIDTH; x++) {
			placements.add(at(center, x, FLOOR - 1, PORCH_NEAR, foundationState(x, FLOOR - 1, PORCH_NEAR)));
			placements.add(at(center, x, FLOOR - 1, PORCH_FAR, foundationState(x, FLOOR - 1, PORCH_FAR)));
		}
	}

	private static BlockState foundationState(int x, int y, int z) {
		// The lamp beneath the wake-up grate. It sits in the foundation so no interior block has to
		// carry it, and being waxed it will still be this colour a hundred in-game days from now.
		if (x == 0 && z == 0 && y == FLOOR - 1) {
			return Blocks.WAXED_COPPER_BULB.defaultBlockState().setValue(BlockStateProperties.LIT, true);
		}
		return switch (grain(x, y, z) % 5) {
			case 0 -> Blocks.CRACKED_DEEPSLATE_TILES.defaultBlockState();
			case 1, 2 -> Blocks.TUFF.defaultBlockState();
			default -> Blocks.DEEPSLATE_TILES.defaultBlockState();
		};
	}

	private static void addWalls(List<Placement> placements, BlockPos center) {
		for (int y = 0; y <= WALL_TOP; y++) {
			for (int x = -HALF_WIDTH; x <= HALF_WIDTH; x++) {
				for (int z = -HALF_DEPTH; z <= HALF_DEPTH; z++) {
					if (!isPerimeter(x, z)) {
						continue;
					}
					BlockState state = wallState(x, y, z);
					if (state != null) {
						placements.add(at(center, x, y, z, state));
					}
				}
			}
		}
	}

	/** The wall block for a perimeter column, or {@code null} where the wall is simply not there. */
	private static BlockState wallState(int x, int y, int z) {
		if (isDoorway(x, y, z)) {
			return null;
		}
		if (isWallTear(x, y, z)) {
			// What is left of the east bay after it came down: an opening you can see and hear
			// through, still grilled, so the first night does not walk in through the damage.
			return AGED_COPPER_BARS;
		}
		if (isCorner(x, z) || isDoorJamb(x, y, z)) {
			return Blocks.CHISELED_STONE_BRICKS.defaultBlockState();
		}
		if (isLamp(x, y, z)) {
			return Blocks.WAXED_EXPOSED_COPPER_BULB.defaultBlockState()
					.setValue(BlockStateProperties.LIT, true);
		}
		if (isWindow(x, y, z)) {
			// The glass on the collapsed side is gone and only its grille is left. Three walls of
			// intact panes are what make that one wall read as damage rather than as style.
			return x == HALF_WIDTH
					? AGED_COPPER_BARS
					: Blocks.GLASS_PANE.defaultBlockState();
		}
		// A dark base and a dark cornice around a lighter body: the silhouette has to read as a
		// building from across a field, before any detail is close enough to matter.
		if (y == 0 || y == WALL_TOP) {
			return (grain(x, y, z) % 4) == 0
					? Blocks.CRACKED_DEEPSLATE_TILES.defaultBlockState()
					: Blocks.DEEPSLATE_TILES.defaultBlockState();
		}
		return masonry(x, y, z);
	}

	private static void addRoof(List<Placement> placements, BlockPos center) {
		for (int x = -HALF_WIDTH; x <= HALF_WIDTH; x++) {
			for (int z = -HALF_DEPTH; z <= HALF_DEPTH; z++) {
				if (isRoofBreach(x, z)) {
					continue;
				}
				BlockState state = (grain(x, ROOF, z) % 5) == 0
						? Blocks.CRACKED_DEEPSLATE_TILES.defaultBlockState()
						: Blocks.DEEPSLATE_TILES.defaultBlockState();
				placements.add(at(center, x, ROOF, z, state));
			}
		}
		for (int x = -HALF_WIDTH; x <= HALF_WIDTH; x++) {
			for (int z = -HALF_DEPTH; z <= HALF_DEPTH; z++) {
				if (!isPerimeter(x, z) || isRoofBreach(x, z)) {
					continue;
				}
				// Two beacons on the parapet. They exist so the roof is lit enough that nothing
				// spawns on it and drops through the breach, and they read from a long way off.
				if (z == -HALF_DEPTH && x == -2 || z == HALF_DEPTH && x == 2) {
					placements.add(at(center, x, PARAPET, z, Blocks.WAXED_COPPER_BULB.defaultBlockState()
							.setValue(BlockStateProperties.LIT, true)));
					continue;
				}
				// Erosion is allowed to eat the runs but never the four corner posts: a parapet that
				// loses its corners stops reading as damage and starts reading as sloppy geometry.
				if (!isCorner(x, z) && (grain(x, PARAPET, z) % 7) == 0) {
					continue;
				}
				placements.add(at(center, x, PARAPET, z, Blocks.DEEPSLATE_TILE_WALL.defaultBlockState()));
			}
		}
	}

	private static void addMast(List<Placement> placements, BlockPos center) {
		placements.add(at(center, 0, ROOF, MAST_Z, Blocks.WAXED_EXPOSED_CUT_COPPER.defaultBlockState()));
		BlockState deck = Blocks.WAXED_EXPOSED_COPPER_GRATE.defaultBlockState();
		placements.add(at(center, -1, ROOF, MAST_Z, deck));
		placements.add(at(center, 1, ROOF, MAST_Z, deck));
		placements.add(at(center, 0, ROOF, MAST_Z - 1, deck));
		placements.add(at(center, 0, ROOF, MAST_Z + 1, deck));

		for (int y = PARAPET; y < MAST_TOP; y++) {
			placements.add(at(center, 0, y, MAST_Z, AGED_COPPER_BARS));
		}
		BlockState armX = Blocks.COPPER_CHAIN.waxedExposed().defaultBlockState()
				.setValue(BlockStateProperties.AXIS, Direction.Axis.X);
		BlockState armZ = Blocks.COPPER_CHAIN.waxedExposed().defaultBlockState()
				.setValue(BlockStateProperties.AXIS, Direction.Axis.Z);
		placements.add(at(center, -1, MAST_TOP - 2, MAST_Z, armX));
		placements.add(at(center, 1, MAST_TOP - 2, MAST_Z, armX));
		placements.add(at(center, 0, MAST_TOP - 2, MAST_Z - 1, armZ));
		placements.add(at(center, 0, MAST_TOP - 2, MAST_Z + 1, armZ));
		placements.add(at(center, 0, MAST_TOP, MAST_Z, Blocks.LIGHTNING_ROD.defaultBlockState()
				.setValue(BlockStateProperties.FACING, Direction.UP)));
	}

	private static void addFixtures(List<Placement> placements, BlockPos center) {
		// The equipment wall, read left to right by a player who wakes up facing it. The rack is
		// two empty shelves: the terminal the station hands over came from here, and the message
		// that accompanies it says there are no spares. The lectern holds no book for the same
		// reason - the terminal is the only thing in this world still willing to explain itself.
		int rack = -HALF_DEPTH + 1;
		placements.add(at(center, -4, 0, rack, facing(Blocks.FURNACE, Direction.SOUTH)));
		placements.add(at(center, -3, 0, rack, Blocks.CRAFTING_TABLE.defaultBlockState()));
		placements.add(at(center, -2, 0, rack, facing(Blocks.CHISELED_BOOKSHELF, Direction.SOUTH)));
		placements.add(at(center, -1, 0, rack, facing(Blocks.CHISELED_BOOKSHELF, Direction.SOUTH)));
		placements.add(at(center, 0, 0, rack, facing(Blocks.LECTERN, Direction.SOUTH)));
		placements.add(at(center, 1, 0, rack, Blocks.BARREL.defaultBlockState()
				.setValue(BlockStateProperties.FACING, Direction.UP)));

		BlockState lantern = Blocks.COPPER_LANTERN.waxedExposed().defaultBlockState()
				.setValue(BlockStateProperties.HANGING, true);
		placements.add(at(center, -2, WALL_TOP, -1, lantern));
		placements.add(at(center, 2, WALL_TOP, 1, lantern));

		// Rubble under the roof breach, weighted towards the corner so the walk from the wake-up
		// tile to the door never crosses it.
		placements.add(at(center, 4, 0, 2, Blocks.COBBLESTONE.defaultBlockState()));
		placements.add(at(center, 4, 1, 2, Blocks.COBBLESTONE.defaultBlockState()));
		placements.add(at(center, 4, 0, 1, Blocks.STONE_BRICK_SLAB.defaultBlockState()));
		placements.add(at(center, 3, 0, 3, Blocks.COBBLESTONE.defaultBlockState()));
		placements.add(at(center, 2, 0, 3, Blocks.STONE_BRICK_SLAB.defaultBlockState()));
		placements.add(at(center, 2, 2, 3, Blocks.COBWEB.defaultBlockState()));

		// Both halves of a bed and both halves of a door are placed back to back, after everything
		// that supports them, so no partial pair is ever left standing across a tick boundary.
		BlockState bed = Blocks.LIGHT_GRAY_BED.defaultBlockState()
				.setValue(BedBlock.FACING, Direction.WEST);
		placements.add(at(center, -3, 0, 0, bed.setValue(BedBlock.PART, BedPart.FOOT)));
		placements.add(at(center, -4, 0, 0, bed.setValue(BedBlock.PART, BedPart.HEAD)));

		BlockState door = Blocks.WAXED_EXPOSED_COPPER_DOOR.defaultBlockState()
				.setValue(DoorBlock.FACING, Direction.NORTH)
				.setValue(DoorBlock.HINGE, DoorHingeSide.LEFT);
		placements.add(at(center, DOOR_X, 0, HALF_DEPTH,
				door.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER)));
		placements.add(at(center, DOOR_X, 1, HALF_DEPTH,
				door.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER)));
	}

	private static boolean isPerimeter(int x, int z) {
		return Math.abs(x) == HALF_WIDTH || Math.abs(z) == HALF_DEPTH;
	}

	private static boolean isCorner(int x, int z) {
		return Math.abs(x) == HALF_WIDTH && Math.abs(z) == HALF_DEPTH;
	}

	private static boolean isDoorway(int x, int y, int z) {
		return z == HALF_DEPTH && x == DOOR_X && y <= 1;
	}

	private static boolean isDoorJamb(int x, int y, int z) {
		return z == HALF_DEPTH && Math.abs(x - DOOR_X) <= 1 && (y <= 1 || x == DOOR_X && y == 2);
	}

	/** The east bay came down here: three courses of the outer wall are gone on two columns. */
	private static boolean isWallTear(int x, int y, int z) {
		return x == HALF_WIDTH && (z == 2 || z == 3) && y <= 2;
	}

	private static boolean isCollapsedBay(int x, int z) {
		return x >= 2 && z >= 1;
	}

	private static boolean isRoofBreach(int x, int z) {
		return switch (x) {
			case 3 -> z == 2 || z == 3;
			case 4 -> z >= 1 && z <= 3;
			case HALF_WIDTH -> z == 2 || z == 3;
			default -> false;
		};
	}

	private static boolean isLamp(int x, int y, int z) {
		if (y != 2) {
			return false;
		}
		return Math.abs(z) == HALF_DEPTH && Math.abs(x) == 3
				|| x == -HALF_WIDTH && z == 0
				|| x == HALF_WIDTH && z == -2;
	}

	private static boolean isWindow(int x, int y, int z) {
		if (y != 2) {
			return false;
		}
		return Math.abs(z) == HALF_DEPTH && Math.abs(x) == 1
				|| Math.abs(x) == HALF_WIDTH && (z == -3 || z == 1);
	}

	private static BlockState masonry(int x, int y, int z) {
		return switch (grain(x, y, z) % 7) {
			case 0, 1 -> Blocks.CRACKED_STONE_BRICKS.defaultBlockState();
			case 2 -> Blocks.MOSSY_STONE_BRICKS.defaultBlockState();
			default -> Blocks.STONE_BRICKS.defaultBlockState();
		};
	}

	private static BlockState facing(net.minecraft.world.level.block.Block block, Direction direction) {
		return block.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, direction);
	}

	/**
	 * Stable per-offset weathering. A hash rather than a {@code RandomSource} because the plan has
	 * to survive being regenerated on a later launch from a persisted cursor.
	 */
	private static int grain(int x, int y, int z) {
		int hash = x * 374_761_393 + y * 668_265_263 + z * 1_274_126_177;
		hash = (hash ^ (hash >>> 13)) * 1_274_126_177;
		return (hash ^ (hash >>> 16)) & Integer.MAX_VALUE;
	}

	private static Placement at(BlockPos center, int x, int y, int z, BlockState state) {
		return new Placement(center.offset(x, y, z), state);
	}
}
