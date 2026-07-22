package com.xm.thefourthfrequency.mixin;

import com.xm.thefourthfrequency.ending.EndBossEncounterService;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.EndPortalBlock;
import net.minecraft.world.level.portal.TeleportTransition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Redirects only a prepared stronghold entrance; every unrelated vanilla portal remains untouched. */
@Mixin(EndPortalBlock.class)
public abstract class EndPortalBlockMixin {
	@Inject(method = "getPortalDestination", at = @At("HEAD"), cancellable = true)
	private void thefourthfrequency$redirectPreparedEntrance(ServerLevel level, Entity entity, BlockPos pos,
			CallbackInfoReturnable<TeleportTransition> callback) {
		EndBossEncounterService.createPortalTransition(level, entity, pos)
				.ifPresent(callback::setReturnValue);
	}
}
