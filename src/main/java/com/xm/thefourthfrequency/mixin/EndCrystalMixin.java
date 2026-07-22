package com.xm.thefourthfrequency.mixin;

import com.xm.thefourthfrequency.ending.EndBossEncounterService;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Protects only the ten UUID-bound encounter anchors; ordinary crystals stay vanilla. */
@Mixin(EndCrystal.class)
public abstract class EndCrystalMixin {
	@Inject(method = "hurtServer", at = @At("HEAD"), cancellable = true)
	private void thefourthfrequency$authorizeStabilityAnchorDamage(ServerLevel level, DamageSource source,
			float amount, CallbackInfoReturnable<Boolean> callback) {
		EndBossEncounterService.handleAnchorDamage(level, (EndCrystal) (Object) this, source, amount)
				.ifPresent(callback::setReturnValue);
	}
}
