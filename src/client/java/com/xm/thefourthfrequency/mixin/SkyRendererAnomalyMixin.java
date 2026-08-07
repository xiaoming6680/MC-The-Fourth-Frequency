package com.xm.thefourthfrequency.mixin;

import com.xm.thefourthfrequency.client_ui.AnomalyPresentationController;
import com.xm.thefourthfrequency.client_ui.SkyInstrumentSampler;
import com.xm.thefourthfrequency.client_ui.WorldInterfaceAtmosphereController;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SkyRenderer;
import net.minecraft.client.renderer.state.SkyRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SkyRenderer.class)
public abstract class SkyRendererAnomalyMixin {
	@Inject(method = "extractRenderState", at = @At("RETURN"))
	private void thefourthfrequency$tintRedHorizon(ClientLevel level, float partialTick, Camera camera,
			SkyRenderState state, CallbackInfo callback) {
		state.skyColor = WorldInterfaceAtmosphereController.tintSky(
				AnomalyPresentationController.redSkyShaderColor(state.skyColor));
		state.starBrightness = WorldInterfaceAtmosphereController.drainStarBrightness(state.starBrightness);
		if (AnomalyPresentationController.isTemporalDriftActive()) {
			state.sunAngle = AnomalyPresentationController.driftedCelestialAngle(state.sunAngle);
			state.moonAngle = AnomalyPresentationController.driftedCelestialAngle(state.moonAngle);
			state.starAngle = AnomalyPresentationController.driftedCelestialAngle(state.starAngle);
			state.starBrightness =
					AnomalyPresentationController.driftedStarBrightness(state.starBrightness);
		}
		float strength = AnomalyPresentationController.redHorizonStrength();
		if (strength <= 0.0F) {
			SkyInstrumentSampler.observe(state);
			return;
		}
		if (state.sunriseAndSunsetColor != 0)
			state.sunriseAndSunsetColor = AnomalyPresentationController.redHorizonShaderColor(
					state.sunriseAndSunsetColor);
		state.starBrightness *= 1.0F - strength;
		// Sampled after every rewrite above, so the weather tool measures the sky the player is
		// actually being shown rather than the one vanilla would have drawn.
		SkyInstrumentSampler.observe(state);
	}
}
