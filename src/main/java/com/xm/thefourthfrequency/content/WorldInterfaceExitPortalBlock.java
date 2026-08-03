package com.xm.thefourthfrequency.content;

import com.mojang.serialization.MapCodec;
import com.xm.thefourthfrequency.ending.EndBossEncounterService;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Portal;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Encounter-owned End exit that preserves the vanilla credits and respawn protocol.
 *
 * <p>It used to be drawn as an ordinary cube wearing a hand-painted texture, which put a flat purple
 * block where the End's exit belongs. It carries a block entity now for no reason other than
 * appearance: that is what lets vanilla's own end-portal renderer draw it, so the exit looks like
 * the portal it is and ships no texture of its own.</p>
 */
public final class WorldInterfaceExitPortalBlock extends BaseEntityBlock implements Portal {
	public static final MapCodec<WorldInterfaceExitPortalBlock> CODEC =
			simpleCodec(WorldInterfaceExitPortalBlock::new);

	public WorldInterfaceExitPortalBlock(BlockBehaviour.Properties properties) {
		super(properties);
	}

	@Override
	protected MapCodec<? extends BaseEntityBlock> codec() {
		return CODEC;
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos position, BlockState state) {
		return new WorldInterfaceExitPortalBlockEntity(position, state);
	}

	@Override
	protected VoxelShape getShape(BlockState state, net.minecraft.world.level.BlockGetter level,
			BlockPos position, CollisionContext context) {
		return Shapes.empty();
	}

	@Override
	protected void entityInside(BlockState state, Level level, BlockPos position, Entity entity,
			InsideBlockEffectApplier effects, boolean intersects) {
		if (!entity.canUsePortal(false)) return;
		if (!level.isClientSide() && level.dimension() == Level.END
				&& entity instanceof ServerPlayer player && !player.seenCredits) {
			EndBossEncounterService.startPoem(player);
			player.showEndCredits();
			return;
		}
		entity.setAsInsidePortal(this, position);
	}

	@Override
	public TeleportTransition getPortalDestination(ServerLevel level, Entity entity, BlockPos position) {
		return ((Portal) Blocks.END_PORTAL).getPortalDestination(level, entity, position);
	}

	/** The block entity renderer draws the portal; a block model would draw a lid over it. */
	@Override
	protected RenderShape getRenderShape(BlockState state) {
		return RenderShape.INVISIBLE;
	}
}
