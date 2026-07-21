package com.xm.thefourthfrequency.mixin;

import com.xm.thefourthfrequency.client_ui.AnomalyPresentationController;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.world.entity.Avatar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AvatarRenderer.class)
public abstract class AvatarRendererAnomalyMixin {
	@Inject(method = "shouldShowName(Lnet/minecraft/world/entity/Avatar;D)Z",
			at = @At("HEAD"), cancellable = true)
	private void thefourthfrequency$hideAnomalyProxyName(Avatar avatar, double distance,
			CallbackInfoReturnable<Boolean> callback) {
		if (AnomalyPresentationController.isAnonymousProxy(avatar)) callback.setReturnValue(false);
	}
}
