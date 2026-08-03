package com.xm.thefourthfrequency.content;

import com.mojang.serialization.MapCodec;
import com.xm.thefourthfrequency.ending.AltarShape;
import com.xm.thefourthfrequency.ending.WorldInterfaceRitualService;
import com.xm.thefourthfrequency.ending.WorldInterfaceState;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
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
	 * Inserting a terminal is a held-item interaction, not a button.
	 *
	 * <p>The sacrifice used to be confirmed in a screen, and the screen reached into the player's
	 * inventory to find the terminal on their behalf. Putting the surrender back on the hand means
	 * the last thing that happens before the finale is the player taking out the object the whole
	 * mod has been about and pushing it into the altar themselves. Anything else in hand - or an
	 * empty hand - still opens the screen, which is where the roster and everyone else's progress
	 * are read.</p>
	 *
	 * <p>Every path that is not an insertion returns {@link InteractionResult#TRY_WITH_EMPTY_HAND}
	 * rather than {@code PASS}. This hook runs for an empty hand too, and {@code PASS} ends the
	 * interaction outright: returning it meant {@link #useWithoutItem} was never reached and the
	 * altar could not be opened at all.</p>
	 */
	@Override
	protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos position,
			Player player, InteractionHand hand, BlockHitResult hit) {
		if (!TerminalData.isBound(stack)) return InteractionResult.TRY_WITH_EMPTY_HAND;
		if (level.isClientSide()) return InteractionResult.SUCCESS;
		if (!(player instanceof ServerPlayer serverPlayer)) return InteractionResult.TRY_WITH_EMPTY_HAND;
		// A bound terminal that this ritual will not take (wrong stage, wrong altar, not on the
		// roster) should still get the player the screen that explains why.
		return WorldInterfaceRitualService.insertHeldTerminal(serverPlayer, position)
				? InteractionResult.SUCCESS_SERVER : InteractionResult.TRY_WITH_EMPTY_HAND;
	}

	/**
	 * Builds the terrace described by {@link AltarShape} around {@code corePosition}, which stands on
	 * the top platform. The shape is shared with the arena preparation rather than restated, so the
	 * two builders cannot produce two different altars. Calls are idempotent.
	 *
	 * @return number of block states changed by this call
	 */
	public static int buildAltar(ServerLevel level, BlockPos corePosition) {
		int changed = 0;
		BlockPos floorCenter = AltarShape.centerFromCore(corePosition);
		int flags = Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS;
		for (int x = -AltarShape.RADIUS; x <= AltarShape.RADIUS; x++) {
			for (int z = -AltarShape.RADIUS; z <= AltarShape.RADIUS; z++) {
				int top = AltarShape.topOffset(x, z);
				for (int y = 0; y <= top; y++) {
					BlockState desired = AltarShape.state(x, y, z, top);
					BlockPos target = floorCenter.offset(x, y, z);
					if (!level.getBlockState(target).equals(desired)
							&& level.setBlock(target, desired, flags)) changed++;
				}
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
}
