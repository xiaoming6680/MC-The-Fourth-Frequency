package com.xm.thefourthfrequency.facility;

import com.xm.thefourthfrequency.content.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;
import java.util.List;

public final class RiftLayout {
	private RiftLayout() {
	}

	public static List<FacilityLayout.Placement> create(BlockPos entrance, int depth) {
		List<FacilityLayout.Placement> plan = new ArrayList<>();
		for (int step = 0; step <= depth; step++) {
			BlockPos path = entrance.offset(0, -step, step);
			plan.add(new FacilityLayout.Placement(path, Blocks.AIR.defaultBlockState()));
			plan.add(new FacilityLayout.Placement(path.above(), Blocks.AIR.defaultBlockState()));
			plan.add(new FacilityLayout.Placement(path.below(), Blocks.OBSIDIAN.defaultBlockState()));
			plan.add(new FacilityLayout.Placement(path.east(),
					(step & 1) == 0 ? Blocks.CRYING_OBSIDIAN.defaultBlockState() : Blocks.TUFF.defaultBlockState()));
			plan.add(new FacilityLayout.Placement(path.west(), Blocks.OBSIDIAN.defaultBlockState()));
		}
		BlockPos core = corePosition(entrance, depth);
		plan.add(new FacilityLayout.Placement(core, ModBlocks.RULE_FRACTURE_CORE.defaultBlockState()));
		plan.add(new FacilityLayout.Placement(core.north(), Blocks.AMETHYST_BLOCK.defaultBlockState()));
		plan.add(new FacilityLayout.Placement(core.south(), Blocks.SCULK.defaultBlockState()));
		return List.copyOf(plan);
	}

	public static BlockPos corePosition(BlockPos entrance, int depth) {
		return entrance.offset(0, -depth, depth);
	}
}
