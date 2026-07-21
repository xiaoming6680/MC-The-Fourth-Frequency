package com.xm.thefourthfrequency.mixin;

import com.xm.thefourthfrequency.client_ui.AnomalyPresentationController;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec2;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Clears movement before LocalPlayer can turn physical key state into control packets. */
@Mixin(KeyboardInput.class)
public abstract class KeyboardInputAnomalyMixin extends ClientInput {
	@Inject(method = "tick", at = @At("RETURN"))
	private void thefourthfrequency$lockAnomalyMovement(CallbackInfo callback) {
		if (!AnomalyPresentationController.isInputLocked()) return;
		keyPresses = Input.EMPTY;
		moveVector = Vec2.ZERO;
	}
}
