package com.xm.thefourthfrequency.content;

import com.mojang.serialization.MapCodec;
import com.xm.thefourthfrequency.ending.WorldInterfaceRitualService;
import com.xm.thefourthfrequency.ending.WorldInterfaceState;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** Indestructible interaction proxy for the authoritative shared ritual. */
public final class ResonanceCoreBlock extends BaseEntityBlock {
	public static final MapCodec<ResonanceCoreBlock> CODEC = simpleCodec(ResonanceCoreBlock::new);

	public ResonanceCoreBlock(BlockBehaviour.Properties properties) {
		super(properties);
	}

	@Override
	protected MapCodec<? extends BaseEntityBlock> codec() {
		return CODEC;
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos position, BlockState state) {
		return new ResonanceCoreBlockEntity(position, state);
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos position, Player player,
			BlockHitResult hit) {
		if (level.isClientSide()) return InteractionResult.SUCCESS;
		if (!(player instanceof ServerPlayer serverPlayer)) return InteractionResult.PASS;
		WorldInterfaceRitualService.openAltar(serverPlayer, position);
		return InteractionResult.SUCCESS_SERVER;
	}

	/**
	 * Builds the original 11x11 platform around {@code corePosition}. The core
	 * occupies the block above the platform's center. Calls are idempotent.
	 *
	 * @return number of block states changed by this call
	 */
	public static int buildAltar(ServerLevel level, BlockPos corePosition) {
		int changed = 0;
		BlockPos floorCenter = corePosition.below();
		int flags = Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS;
		for (int x = -5; x <= 5; x++) {
			for (int z = -5; z <= 5; z++) {
				BlockState desired = altarFloorState(x, z);
				BlockPos target = floorCenter.offset(x, 0, z);
				if (!level.getBlockState(target).equals(desired) && level.setBlock(target, desired, flags)) changed++;
			}
		}
		if (!level.getBlockState(corePosition).is(ModBlocks.RESONANCE_CORE)
				&& level.setBlock(corePosition, ModBlocks.RESONANCE_CORE.defaultBlockState(), flags)) changed++;
		if (level.getBlockEntity(corePosition) instanceof ResonanceCoreBlockEntity core) {
			WorldInterfaceState.Snapshot snapshot = WorldInterfaceState.snapshot(level.getServer());
			core.bind(snapshot.encounterId().orElse(null), snapshot.revision());
		}
		return changed;
	}

	private static BlockState altarFloorState(int x, int z) {
		int edge = Math.max(Math.abs(x), Math.abs(z));
		if (edge == 5) return Blocks.OBSIDIAN.defaultBlockState();
		if (x == 0 || z == 0) return Blocks.CRYING_OBSIDIAN.defaultBlockState();
		if (Math.abs(x) == Math.abs(z) && edge >= 2) return Blocks.PURPUR_BLOCK.defaultBlockState();
		if ((Math.abs(x) == 2 && Math.abs(z) <= 2) || (Math.abs(z) == 2 && Math.abs(x) <= 2)) {
			return Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
		}
		return Blocks.END_STONE_BRICKS.defaultBlockState();
	}
}
