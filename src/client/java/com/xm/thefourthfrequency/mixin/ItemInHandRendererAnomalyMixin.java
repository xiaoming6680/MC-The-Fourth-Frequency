package com.xm.thefourthfrequency.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.xm.thefourthfrequency.client_ui.AnomalyPresentationController;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A detached second-person camera must never retain the local first-person hands. */
@Mixin(ItemInHandRenderer.class)
public abstract class ItemInHandRendererAnomalyMixin {
	@Inject(method = "renderHandsWithItems", at = @At("HEAD"), cancellable = true)
	private void thefourthfrequency$hideDetachedHands(float partialTick, PoseStack poseStack,
			SubmitNodeCollector collector, LocalPlayer player, int light, CallbackInfo callback) {
		if (AnomalyPresentationController.isFirstPersonHandHidden()) callback.cancel();
	}
}
