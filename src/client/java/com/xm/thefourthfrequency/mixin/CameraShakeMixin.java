package com.xm.thefourthfrequency.mixin;

import com.xm.thefourthfrequency.client_ui.ScreenShakeController;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Applies the encounter's camera shake to the rendering camera.
 *
 * <p><b>Correctness: this cannot affect aim.</b> It moves {@link Camera}, not {@code LocalPlayer}.
 * The player's own yRot and xRot are untouched, so the crosshair still points where the player is
 * pointing and every ray trace, attack target and interaction result is exactly what it would have
 * been with no shake at all. The picture moves; the game state does not.
 *
 * <p><b>Why {@code setup} TAIL rather than {@code GameRenderer#renderLevel}.</b> {@code setRotation}
 * recomputes the camera's rotation quaternion <em>and</em> its forwards/up/left vectors, so frustum
 * culling, particle facing and held-item rendering all follow from this one injection and the frame
 * stays internally consistent. Perturbing a matrix in {@code GameRenderer} shakes the world only,
 * and the player's own hands stay nailed to the screen while everything behind them moves.
 *
 * <p><b>Known limitation.</b> {@code Camera} has no roll setter; rolling the view would mean going
 * through the projection matrix. Not done, and not wanted: three axes is already enough to read as
 * an impact, and roll at Minecraft's field of view is the component most likely to make people
 * motion sick.
 */
@Mixin(Camera.class)
public abstract class CameraShakeMixin {
	@org.spongepowered.asm.mixin.Shadow
	protected abstract void setRotation(float yaw, float pitch);

	@org.spongepowered.asm.mixin.Shadow
	protected abstract void move(float forward, float up, float right);

	// Record-style accessors, not JavaBean getters: Camera exposes yRot()/xRot(), and a @Shadow
	// naming them getYRot()/getXRot() fails at class-load time with "was not located in the target
	// class" rather than at compile time.
	@org.spongepowered.asm.mixin.Shadow
	public abstract float yRot();

	@org.spongepowered.asm.mixin.Shadow
	public abstract float xRot();

	@Inject(method = "setup", at = @At("TAIL"))
	private void thefourthfrequency$applyEncounterShake(CallbackInfo callback) {
		ScreenShakeController.Sample sample = ScreenShakeController.sample();
		if (sample == null) return;
		// Pitch is clamped, yaw is not. Yaw wraps and is fine anywhere; pitch past +/-90 flips the
		// camera's up vector, and everything that builds a camera-facing quad - the beams, the
		// halos, every particle - is then expanded along an inverted basis. A player already looking
		// near straight up or straight down is exactly who a heavy shake would push over that edge.
		float pitch = net.minecraft.util.Mth.clamp(xRot() + sample.pitch(), -90.0F, 90.0F);
		setRotation(yRot() + sample.yaw(), pitch);
		// Forward is left alone: pushing the camera along its own view axis reads as a zoom rather
		// than as a knock, and at these amplitudes it would clip through whatever is in front.
		move(0.0F, sample.up(), sample.right());
	}
}
