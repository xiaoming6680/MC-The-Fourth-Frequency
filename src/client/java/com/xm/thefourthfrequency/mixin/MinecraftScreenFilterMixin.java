package com.xm.thefourthfrequency.mixin;

import com.xm.thefourthfrequency.client_ui.ScreenFilterDriver;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The one moment a whole frame exists and has not been shown yet.
 *
 * <p>{@code runTick} renders the level, the HUD and whatever screen is open into the main render
 * target, and then blits that target to the window. Between those two is the only place a
 * post-effect chain can reach a <em>screen</em>: {@code GameRenderer}'s own post-effect slot runs
 * inside the level render and never sees the GUI at all.
 *
 * <p>Injected at the call rather than at the tail of the method, which puts it inside vanilla's own
 * {@code if (!window.isMinimized())} guard. A minimised window skips the blit, and filtering a frame
 * that is not going to be shown is a full-screen pass spent on nothing.
 */
@Mixin(Minecraft.class)
public abstract class MinecraftScreenFilterMixin {
	@Inject(method = "runTick", at = @At(value = "INVOKE",
			target = "Lcom/mojang/blaze3d/pipeline/RenderTarget;blitToScreen()V"))
	private void thefourthfrequency$filterTheFinishedFrame(boolean renderLevel, CallbackInfo callback) {
		ScreenFilterDriver.applyBeforeBlit((Minecraft) (Object) this);
	}
}
