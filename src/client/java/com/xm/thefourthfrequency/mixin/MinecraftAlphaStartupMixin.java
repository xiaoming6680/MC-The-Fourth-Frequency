package com.xm.thefourthfrequency.mixin;

import com.xm.thefourthfrequency.client_ui.AlphaLoadSessionController;
import net.minecraft.client.Minecraft;
import net.minecraft.client.main.GameConfig;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class MinecraftAlphaStartupMixin {
	@Inject(method = "<init>(Lnet/minecraft/client/main/GameConfig;)V", at = @At(value = "FIELD",
			target = "Lnet/minecraft/client/Minecraft;options:Lnet/minecraft/client/Options;",
			opcode = Opcodes.PUTFIELD, shift = At.Shift.AFTER))
	private void thefourthfrequency$preparePersistentAlphaPacks(GameConfig config,
			CallbackInfo callback) {
		AlphaLoadSessionController.preparePersistentPackSelectionBeforeInitialReload(
				(Minecraft) (Object) this);
	}
}
