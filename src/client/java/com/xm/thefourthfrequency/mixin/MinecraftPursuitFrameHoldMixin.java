package com.xm.thefourthfrequency.mixin;

import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.textures.GpuTexture;
import com.xm.thefourthfrequency.client_ui.FrameHoldArbiter;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Lets the pursuit presentation hold the last frame on screen instead of publishing black ones.
 *
 * <p>{@code runTick} clears the main render target to opaque black, renders into it, and blits the
 * result to the screen. The pursuit collapse and the capture freeze work by cancelling the render,
 * which on its own leaves the black clear to reach the screen - so what should read as a stalled
 * video feed read as flashing instead. Skipping the clear alongside the render leaves the previous
 * frame in the target, and the blit puts it back up untouched.</p>
 *
 * <p>This hook is also where the per-frame decision is taken, because it is the first point in the
 * frame that can still change the outcome; the render cancel downstream only consumes it.</p>
 *
 * <p>The class name is historical: it now serves two callers, not one. The boss fight's hit-stop
 * freezes frames by the same mechanism, and two {@code @Redirect}s on a single instruction is a
 * load-time failure - so both go through {@link FrameHoldArbiter} and this redirect stays single.
 * Renaming it would mean touching {@code thefourthfrequency.mixins.json} and the pursuit contract
 * test for no behavioural gain.</p>
 */
@Mixin(Minecraft.class)
public abstract class MinecraftPursuitFrameHoldMixin {
	@Redirect(method = "runTick", at = @At(value = "INVOKE",
			target = "Lcom/mojang/blaze3d/systems/CommandEncoder;clearColorAndDepthTextures"
					+ "(Lcom/mojang/blaze3d/textures/GpuTexture;ILcom/mojang/blaze3d/textures/GpuTexture;D)V"))
	private void thefourthfrequency$holdPreviousFrame(CommandEncoder encoder, GpuTexture colorTexture,
			int clearColor, GpuTexture depthTexture, double clearDepth) {
		if (FrameHoldArbiter.beginFrame(System.nanoTime())) return;
		encoder.clearColorAndDepthTextures(colorTexture, clearColor, depthTexture, clearDepth);
	}
}
