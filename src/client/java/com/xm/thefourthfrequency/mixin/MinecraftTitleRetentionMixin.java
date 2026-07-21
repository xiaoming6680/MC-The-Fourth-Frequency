package com.xm.thefourthfrequency.mixin;

import com.xm.thefourthfrequency.client_ui.AlphaLoadSessionController;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class MinecraftTitleRetentionMixin {
	@Inject(method = "updateTitle", at = @At("TAIL"))
	private void thefourthfrequency$retainAlphaTitle(CallbackInfo ci) {
		AlphaLoadSessionController.retainFinalWindowTitle((Minecraft) (Object) this);
	}
}
