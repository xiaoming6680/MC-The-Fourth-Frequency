package com.xm.thefourthfrequency.body;

import com.xm.thefourthfrequency.content.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

public final class NetherRiftLayout {
	private NetherRiftLayout() {
	}

	public static List<Placement> create(BlockPos origin) {
		List<Placement> placements = new ArrayList<>();
		for (int x = -3; x <= 3; x++) {
			for (int z = -3; z <= 3; z++) {
				BlockState floor = (Math.abs(x) == 3 || Math.abs(z) == 3)
						? Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState()
						: Blocks.BASALT.defaultBlockState();
				placements.add(new Placement(origin.offset(x, -1, z), floor));
			}
		}
		for (int y = 0; y <= 3; y++) {
			for (int x : new int[] {-3, 3}) {
				placements.add(new Placement(origin.offset(x, y, 0), Blocks.CRYING_OBSIDIAN.defaultBlockState()));
			}
		}
		for (int x = -2; x <= 2; x++) {
			placements.add(new Placement(origin.offset(x, 4, 0), Blocks.OBSIDIAN.defaultBlockState()));
		}
		placements.add(new Placement(origin, ModBlocks.NETHER_RULE_FRACTURE_CORE.defaultBlockState()));
		return List.copyOf(placements);
	}

	public record Placement(BlockPos position, BlockState state) {
	}
}
