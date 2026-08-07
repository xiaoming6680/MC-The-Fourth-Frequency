package com.xm.thefourthfrequency.mixin;

import com.xm.thefourthfrequency.client_ui.TerminalHandheldAnimator;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Narrows the field of view slightly while the terminal is up.
 *
 * <p>The device does not move the player and the arm can only reach so far, so on its own the
 * raise reads as "the item got bigger". Closing the lens a little at the same time is what makes
 * it read as the player leaning in to the CRT instead.</p>
 *
 * <p>Three limits, all of them deliberate:</p>
 *
 * <ul>
 * <li><b>First person only.</b> {@code getFov} also serves the third-person and spectator cameras;
 * the terminal is a private object and must not move anyone else's view, including the holder's
 * own when they have stepped the camera out.</li>
 * <li><b>Multiplicative.</b> The player's own FOV setting, sprinting, and every other modifier
 * vanilla already folded into the returned value survive untouched.</li>
 * <li><b>Mild and continuous.</b> {@code TerminalHandheldPose} caps the change at 12% and drives
 * it from the same eased openness as the travel, so there is no step for the eye to catch.</li>
 * </ul>
 */
@Mixin(GameRenderer.class)
public abstract class GameRendererTerminalFovMixin {
	@Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
	private void thefourthfrequency$leanIntoTerminal(Camera camera, float partialTick,
			boolean useFovSetting, CallbackInfoReturnable<Float> callback) {
		float scale = TerminalHandheldAnimator.fovScale();
		if (scale == 1.0F) return;
		Minecraft client = Minecraft.getInstance();
		if (client.options == null || !client.options.getCameraType().isFirstPerson()) return;
		callback.setReturnValue(callback.getReturnValueF() * scale);
	}
}
