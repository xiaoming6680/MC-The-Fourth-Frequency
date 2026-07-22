package com.xm.thefourthfrequency.mixin;

import com.xm.thefourthfrequency.ending.FriendlyDragonService;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Removes only the friendly ending dragon's vanilla damage and block-breaking side effects. */
@Mixin(EnderDragon.class)
public abstract class EnderDragonMixin {
	@Inject(method = "checkWalls", at = @At("HEAD"), cancellable = true)
	private void thefourthfrequency$keepFriendlyDragonFromBreakingBlocks(ServerLevel level, AABB bounds,
			CallbackInfoReturnable<Boolean> callback) {
		if (FriendlyDragonService.isFriendly((Entity) (Object) this)) callback.setReturnValue(false);
	}

	@Inject(method = "hurtServer", at = @At("HEAD"), cancellable = true)
	private void thefourthfrequency$keepFriendlyDragonInvulnerable(ServerLevel level, DamageSource source,
			float amount, CallbackInfoReturnable<Boolean> callback) {
		if (FriendlyDragonService.isFriendly((Entity) (Object) this)) callback.setReturnValue(false);
	}
}
