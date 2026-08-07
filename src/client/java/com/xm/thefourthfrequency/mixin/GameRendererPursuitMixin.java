package com.xm.thefourthfrequency.mixin;

import com.xm.thefourthfrequency.client_ui.FrameHoldArbiter;
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
		// The hold decision is made at the clear site in MinecraftPursuitFrameHoldMixin, which runs
		// earlier in the same frame; this only carries it out.
		//
		// Both halves must consult the same arbiter. Cancelling the render for one source while
		// the clear was skipped for another - or vice versa - draws a frame against a depth buffer
		// that was never cleared, and every piece of world geometry fails the depth test.
		if (FrameHoldArbiter.skipRenderFrame()) callback.cancel();
	}
}
