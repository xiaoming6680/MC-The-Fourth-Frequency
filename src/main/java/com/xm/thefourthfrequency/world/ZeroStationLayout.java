package com.xm.thefourthfrequency.world;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

public final class ZeroStationLayout {
	public record Placement(BlockPos position, BlockState state) {
	}

	private ZeroStationLayout() {
	}

	public static List<Placement> create(BlockPos center) {
		List<Placement> placements = new ArrayList<>();

		// The floor is first so a newly connecting player always has a safe platform.
		for (int x = -4; x <= 4; x++) {
			for (int z = -3; z <= 3; z++) {
				BlockState floor = ((x * 31 + z * 17) & 3) == 0
						? Blocks.CRACKED_STONE_BRICKS.defaultBlockState()
						: Blocks.STONE_BRICKS.defaultBlockState();
				placements.add(at(center, x, -1, z, floor));
			}
		}

		// Clear only the compact station envelope; work is applied in bounded tick batches.
		for (int y = 0; y <= 4; y++) {
			for (int x = -4; x <= 4; x++) {
				for (int z = -3; z <= 3; z++) {
					placements.add(at(center, x, y, z, Blocks.AIR.defaultBlockState()));
				}
			}
		}

		for (int y = 0; y <= 3; y++) {
			for (int x = -4; x <= 4; x++) {
				addWeatheredWall(placements, center, x, y, -3, x + y);
				if (!(y <= 1 && Math.abs(x) <= 1)) {
					addWeatheredWall(placements, center, x, y, 3, x - y);
				}
			}
			for (int z = -2; z <= 2; z++) {
				addWeatheredWall(placements, center, -4, y, z, z + y);
				if (!(y == 2 && z == 1)) {
					addWeatheredWall(placements, center, 4, y, z, z - y);
				}
			}
		}

		// A deliberately incomplete roof and one unexplained terminal relay fixture.
		for (int x = -4; x <= 4; x++) {
			for (int z = -3; z <= 3; z++) {
				if ((x + z * 2) % 7 != 0 && !(x >= 2 && z >= 1)) {
					placements.add(at(center, x, 4, z, Blocks.DEEPSLATE_TILES.defaultBlockState()));
				}
			}
		}
		placements.add(at(center, 0, 0, -2, Blocks.LECTERN.defaultBlockState()));
		placements.add(at(center, 0, 1, -3, Blocks.COPPER_BULB.defaultBlockState()));
		placements.add(at(center, 1, 0, -2, Blocks.CHISELED_BOOKSHELF.defaultBlockState()));
		placements.add(at(center, -1, 0, -2, Blocks.CRAFTING_TABLE.defaultBlockState()));

		return List.copyOf(placements);
	}

	private static void addWeatheredWall(List<Placement> placements, BlockPos center,
			int x, int y, int z, int pattern) {
		if ((pattern & 7) == 3 && y > 0) {
			return;
		}
		BlockState state = (pattern & 3) == 0
				? Blocks.COBBLESTONE.defaultBlockState()
				: Blocks.CRACKED_STONE_BRICKS.defaultBlockState();
		placements.add(at(center, x, y, z, state));
	}

	private static Placement at(BlockPos center, int x, int y, int z, BlockState state) {
		return new Placement(center.offset(x, y, z), state);
	}
}
