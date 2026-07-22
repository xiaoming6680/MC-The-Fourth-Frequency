package com.xm.thefourthfrequency.mixin;

import com.xm.thefourthfrequency.client_ui.AnomalyPresentationController;
import com.xm.thefourthfrequency.client_ui.AtmosphericFogProfile;
import com.xm.thefourthfrequency.client_ui.DimensionViewDistanceController;
import com.xm.thefourthfrequency.client_ui.DimensionViewDistancePolicy;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogRenderer;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.material.FogType;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Composes the fixed-range atmosphere with the existing red-horizon presentation. */
@Mixin(FogRenderer.class)
public abstract class FogRendererAnomalyMixin {
	@Unique private AtmosphericFogProfile thefourthfrequency$atmosphericFog =
			AtmosphericFogProfile.fixedDistanceOnly(DimensionViewDistancePolicy.OVERWORLD_CHUNKS);
	@Unique private ResourceKey<Level> thefourthfrequency$lastDimension;
	@Unique private long thefourthfrequency$lastFogTick = Long.MIN_VALUE;

	@Inject(method = "setupFog", at = @At("HEAD"))
	private void thefourthfrequency$sampleAtmosphere(Camera camera, int viewDistance,
			DeltaTracker deltaTracker, float darkness, ClientLevel level,
			CallbackInfoReturnable<Vector4f> callback) {
		float partialTick = deltaTracker.getGameTimeDeltaPartialTick(false);
		float skyExposure = level.getBrightness(LightLayer.SKY, camera.blockPosition()) / 15.0F;
		boolean atmospheric = Level.OVERWORLD.equals(level.dimension())
				&& camera.getFluidInCamera() == FogType.NONE
				&& !thefourthfrequency$hasObscuringEffect(camera);
		AtmosphericFogProfile target = AtmosphericFogProfile.sample(skyExposure,
				level.getRainLevel(partialTick), level.getThunderLevel(partialTick),
				AtmosphericFogProfile.nightFactor(level.getDayTime()),
				(float) camera.position().y, atmospheric,
				DimensionViewDistanceController.atmosphericChunks(
						level.dimension().identifier().toString(), viewDistance));
		long tick = level.getGameTime();
		if (!level.dimension().equals(thefourthfrequency$lastDimension)
				|| thefourthfrequency$lastFogTick == Long.MIN_VALUE
				|| tick < thefourthfrequency$lastFogTick
				|| tick - thefourthfrequency$lastFogTick > 20L) {
			thefourthfrequency$atmosphericFog = target;
		} else if (tick != thefourthfrequency$lastFogTick) {
			thefourthfrequency$atmosphericFog = thefourthfrequency$atmosphericFog.blendToward(target, 0.18F);
		}
		thefourthfrequency$lastDimension = level.dimension();
		thefourthfrequency$lastFogTick = tick;
	}

	@ModifyArg(method = "setupFog", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/renderer/fog/FogRenderer;updateBuffer(Ljava/nio/ByteBuffer;ILorg/joml/Vector4f;FFFFFF)V"),
			index = 2)
	private Vector4f thefourthfrequency$tintWorldFogUniform(Vector4f original) {
		return thefourthfrequency$tintRed(thefourthfrequency$tintAtmosphere(original));
	}

	@ModifyArg(method = "setupFog", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/renderer/fog/FogRenderer;updateBuffer(Ljava/nio/ByteBuffer;ILorg/joml/Vector4f;FFFFFF)V"),
			index = 5)
	private float thefourthfrequency$bringRedFogStartCloser(float original) {
		float atmospheric = thefourthfrequency$atmosphericFog.clampRenderStart(original);
		float strength = AnomalyPresentationController.redSkyStrength();
		return thefourthfrequency$mix(atmospheric, Math.min(atmospheric, 6.0F), strength);
	}

	@ModifyArg(method = "setupFog", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/renderer/fog/FogRenderer;updateBuffer(Ljava/nio/ByteBuffer;ILorg/joml/Vector4f;FFFFFF)V"),
			index = 6)
	private float thefourthfrequency$bringRedFogEndCloser(float original) {
		float atmospheric = thefourthfrequency$atmosphericFog.clampRenderEnd(original);
		float strength = AnomalyPresentationController.redSkyStrength();
		return thefourthfrequency$mix(atmospheric, Math.min(atmospheric, 28.0F), strength);
	}

	@Inject(method = "setupFog", at = @At("RETURN"), cancellable = true)
	private void thefourthfrequency$tintAtmosphericHorizon(Camera camera, int viewDistance,
			DeltaTracker deltaTracker, float darkness, ClientLevel level,
			CallbackInfoReturnable<Vector4f> callback) {
		Vector4f tinted = thefourthfrequency$tintRed(
				thefourthfrequency$tintAtmosphere(callback.getReturnValue()));
		if (tinted != callback.getReturnValue()) callback.setReturnValue(tinted);
	}

	@Unique
	private Vector4f thefourthfrequency$tintAtmosphere(Vector4f original) {
		AtmosphericFogProfile.FogColor color = thefourthfrequency$atmosphericFog.tint(
				original.x, original.y, original.z);
		return new Vector4f(color.red(), color.green(), color.blue(), original.w);
	}

	@Unique
	private static Vector4f thefourthfrequency$tintRed(Vector4f original) {
		float strength = AnomalyPresentationController.redSkyStrength();
		if (strength <= 0.0F) return original;
		return new Vector4f(
				thefourthfrequency$mix(original.x, 0.58F, strength),
				thefourthfrequency$mix(original.y, 0.025F, strength),
				thefourthfrequency$mix(original.z, 0.045F, strength),
				original.w);
	}

	@Unique
	private static boolean thefourthfrequency$hasObscuringEffect(Camera camera) {
		return camera.entity() instanceof LivingEntity living
				&& (living.hasEffect(MobEffects.BLINDNESS) || living.hasEffect(MobEffects.DARKNESS));
	}

	@Unique
	private static float thefourthfrequency$mix(float from, float to, float amount) {
		return from + (to - from) * amount;
	}
}
