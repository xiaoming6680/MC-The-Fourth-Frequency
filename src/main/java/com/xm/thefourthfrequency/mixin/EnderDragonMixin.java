package com.xm.thefourthfrequency.mixin;

import com.xm.thefourthfrequency.ending.FriendlyDragonService;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Removes only the friendly ending dragon's vanilla damage, block-breaking and flight integration.
 *
 * <p>Every injection here is gated on {@link FriendlyDragonService#isFriendly}; the hostile dragon
 * an ordinary world fights is left exactly as vanilla wrote it.
 */
@Mixin(EnderDragon.class)
public abstract class EnderDragonMixin {
	/**
	 * Leaves the ending dragon's position to the service that authors it.
	 *
	 * <p><b>This is the twitch.</b> {@code FriendlyDragonService} writes the body onto its orbit from
	 * {@code START_SERVER_TICK}, which is before the level ticks its entities, and it hands the step
	 * it just took to {@code setDeltaMovement} because that is honestly the dragon's velocity. Then
	 * {@code aiStep} runs and ends with {@code move(SELF, getDeltaMovement())} - unconditionally,
	 * because the hovering phase always reports a fly target once its first server tick has pinned
	 * one. So the step was taken twice.
	 *
	 * <p>Write {@code e} for how far the body ends a tick from where the orbit put it and {@code d}
	 * for one tick of orbit: the service starts from wherever vanilla left the dragon, so
	 * {@code e = d - e_previous}. That alternates - {@code d}, {@code 0}, {@code d}, {@code 0} - and
	 * {@code d} is about three quarters of a block on the resting orbit and better than one and a half
	 * while the dragon is spiralling out of the altar. A body thrown that far back and forth ten times
	 * a second is what a player sees as a convulsion. The rotation goes with it: the service aims the
	 * dragon along the step it actually took, and on every second tick that step is the difference
	 * between two nearly equal numbers, so the heading came out of the noise left over.
	 *
	 * <p>Skipping the integration rather than zeroing the velocity, because zeroing it only shrinks
	 * the overshoot to whatever vanilla's own hover thrust adds - the authored path stays coupled to a
	 * second mover. Everything else {@code aiStep} does still runs: the wing beat, the flight history
	 * the neck and tail are drawn from, and the part positioning. {@code aiStep} calls {@code move} on
	 * itself in both arms of its {@code inWall} branch, so the redirect lands twice and exactly one of
	 * the two runs per tick.
	 *
	 * <p>The {@code target} must name its owner. Loom rewrites Mixin annotations in place at remap
	 * time instead of emitting a refMap, and an owner-less member descriptor leaves it no class whose
	 * mappings to resolve the name against, so it copies the string through verbatim. A dev run never
	 * notices - verbatim is already the right name there. The remapped jar then hunted for a literal
	 * {@code move} among intermediary names, found nothing, and {@code defaultRequire: 1} turned the
	 * empty injection into a crash during bootstrap.
	 */
	@Redirect(method = "aiStep",
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/world/entity/boss/enderdragon/EnderDragon;move(Lnet/minecraft/world/entity/MoverType;Lnet/minecraft/world/phys/Vec3;)V"))
	private void thefourthfrequency$leaveFriendlyDragonWhereTheOrbitPutIt(EnderDragon dragon,
			MoverType type, Vec3 movement) {
		if (FriendlyDragonService.isFriendly(dragon)) return;
		dragon.move(type, movement);
	}

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
