package com.xm.thefourthfrequency.content;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.TheEndPortalBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Carries nothing. It exists so the exit can be drawn by vanilla's own end-portal renderer instead
 * of by a cube of hand-painted texture, which is the entire reason the block has a block entity.
 */
public final class WorldInterfaceExitPortalBlockEntity extends TheEndPortalBlockEntity {
	public WorldInterfaceExitPortalBlockEntity(BlockPos position, BlockState state) {
		super(WorldInterfaceBlockEntities.EXIT_PORTAL, position, state);
	}
}
