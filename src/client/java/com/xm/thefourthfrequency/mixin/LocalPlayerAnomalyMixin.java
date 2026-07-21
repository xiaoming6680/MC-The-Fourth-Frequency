package com.xm.thefourthfrequency.mixin;

import com.xm.thefourthfrequency.client_ui.AnomalyPresentationController;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LocalPlayer.class)
public abstract class LocalPlayerAnomalyMixin {
	@Inject(method = "isControlledCamera", at = @At("RETURN"), cancellable = true)
	private void thefourthfrequency$keepSeparatedBodyControllable(
			CallbackInfoReturnable<Boolean> callback) {
		if (!callback.getReturnValue()
				&& AnomalyPresentationController.shouldControlSeparatedPlayer())
			callback.setReturnValue(true);
	}
}
