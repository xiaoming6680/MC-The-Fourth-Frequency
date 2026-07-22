package com.xm.thefourthfrequency.mixin;

import com.xm.thefourthfrequency.client_ui.AnomalyPresentationController;
import com.xm.thefourthfrequency.client_ui.EndBossIntrusionClient;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Replaces only the client-visible name of slots participating in the organ-misread lease. */
@Mixin(ItemStack.class)
public abstract class ItemStackAnomalyMixin {
	@Inject(method = "getHoverName", at = @At("HEAD"), cancellable = true)
	private void thefourthfrequency$misreadItemName(CallbackInfoReturnable<Component> callback) {
		if (AnomalyPresentationController.isMisread((ItemStack) (Object) this)
				|| EndBossIntrusionClient.isLocked((ItemStack) (Object) this))
			callback.setReturnValue(Component.literal("I SEE YOU...."));
	}
}
