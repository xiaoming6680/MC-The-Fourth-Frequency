package com.xm.thefourthfrequency.content;

import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;

/** Unbreakable visual cage around one of the ten authoritative crystals. */
public final class StabilityAnchorCageBlock extends IronBarsBlock {
	public static final MapCodec<StabilityAnchorCageBlock> CODEC = simpleCodec(StabilityAnchorCageBlock::new);

	public StabilityAnchorCageBlock(BlockBehaviour.Properties properties) {
		super(properties);
	}

	@Override
	public MapCodec<StabilityAnchorCageBlock> codec() {
		return CODEC;
	}
}
