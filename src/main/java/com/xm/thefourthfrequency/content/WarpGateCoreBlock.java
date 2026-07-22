package com.xm.thefourthfrequency.content;

import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;

/** Inert center used by the twenty decorative vanilla-shaped warp gates. */
public final class WarpGateCoreBlock extends Block {
	public static final MapCodec<WarpGateCoreBlock> CODEC = simpleCodec(WarpGateCoreBlock::new);

	public WarpGateCoreBlock(BlockBehaviour.Properties properties) {
		super(properties);
	}

	@Override
	protected MapCodec<? extends Block> codec() {
		return CODEC;
	}
}
