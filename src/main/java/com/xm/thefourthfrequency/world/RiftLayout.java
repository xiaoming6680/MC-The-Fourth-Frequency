package com.xm.thefourthfrequency.world;

import com.xm.thefourthfrequency.content.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

public final class RiftLayout {
	private RiftLayout() {
	}

	public static List<Placement> create(BlockPos entrance, int depth) {
		List<Placement> plan = new ArrayList<>();
		for (int step = 0; step <= depth; step++) {
			BlockPos path = entrance.offset(0, -step, step);
			plan.add(new Placement(path, Blocks.AIR.defaultBlockState()));
			plan.add(new Placement(path.above(), Blocks.AIR.defaultBlockState()));
			plan.add(new Placement(path.below(), Blocks.OBSIDIAN.defaultBlockState()));
			plan.add(new Placement(path.east(),
					(step & 1) == 0 ? Blocks.CRYING_OBSIDIAN.defaultBlockState() : Blocks.TUFF.defaultBlockState()));
			plan.add(new Placement(path.west(), Blocks.OBSIDIAN.defaultBlockState()));
		}
		BlockPos core = corePosition(entrance, depth);
		plan.add(new Placement(core, ModBlocks.RULE_FRACTURE_CORE.defaultBlockState()));
		plan.add(new Placement(core.north(), Blocks.AMETHYST_BLOCK.defaultBlockState()));
		plan.add(new Placement(core.south(), Blocks.SCULK.defaultBlockState()));
		return List.copyOf(plan);
	}

	public static BlockPos corePosition(BlockPos entrance, int depth) {
		return entrance.offset(0, -depth, depth);
	}

	public record Placement(BlockPos position, BlockState state) {
	}
}
