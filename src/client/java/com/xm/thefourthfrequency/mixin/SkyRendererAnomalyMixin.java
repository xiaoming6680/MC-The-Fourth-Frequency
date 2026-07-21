package com.xm.thefourthfrequency.mixin;

import com.xm.thefourthfrequency.client_ui.AnomalyPresentationController;
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
		state.skyColor = AnomalyPresentationController.redSkyShaderColor(state.skyColor);
		float strength = AnomalyPresentationController.redSkyStrength();
		if (strength <= 0.0F) return;
		if (state.sunriseAndSunsetColor != 0)
			state.sunriseAndSunsetColor = AnomalyPresentationController.redSkyShaderColor(
					state.sunriseAndSunsetColor);
		state.starBrightness *= 1.0F - strength;
	}
}
