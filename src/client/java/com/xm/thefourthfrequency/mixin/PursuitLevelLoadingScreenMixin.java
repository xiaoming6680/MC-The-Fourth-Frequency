package com.xm.thefourthfrequency.mixin;

import com.xm.thefourthfrequency.client_ui.PursuitPresentationClient;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = LevelLoadingScreen.class, priority = 1_200)
public abstract class PursuitLevelLoadingScreenMixin {
	@Inject(method = "render", at = @At("HEAD"), cancellable = true)
	private void thefourthfrequency$hidePursuitWorldLoad(GuiGraphics graphics, int mouseX,
			int mouseY, float partialTick, CallbackInfo callback) {
		if (!PursuitPresentationClient.shouldCoverLoadingScreen()) return;
		graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(), 0xFF000000);
		callback.cancel();
	}
}
