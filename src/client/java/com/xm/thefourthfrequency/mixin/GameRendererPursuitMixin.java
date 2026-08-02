package com.xm.thefourthfrequency.mixin;

import com.xm.thefourthfrequency.client_ui.PursuitPresentationClient;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class GameRendererPursuitMixin {
	@Inject(method = "render", at = @At("HEAD"), cancellable = true)
	private void thefourthfrequency$simulatePursuitFrameCollapse(DeltaTracker deltaTracker,
			boolean renderLevel, CallbackInfo callback) {
		if (PursuitPresentationClient.skipRenderFrame(System.nanoTime())) callback.cancel();
	}
}
