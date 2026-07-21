package com.xm.thefourthfrequency.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EndPortalBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** End portal blocks are inert because stronghold portal rooms are the final altar. */
@Mixin(EndPortalBlock.class)
public abstract class EndPortalBlockMixin {
	@Inject(method = "entityInside", at = @At("HEAD"), cancellable = true)
	private void thefourthfrequency$keepPlayersOutOfTheEnd(BlockState state, Level level, BlockPos pos,
			Entity entity, InsideBlockEffectApplier effects, boolean intersects, CallbackInfo callback) {
		callback.cancel();
	}
}
