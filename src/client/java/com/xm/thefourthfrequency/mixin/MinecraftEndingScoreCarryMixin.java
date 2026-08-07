package com.xm.thefourthfrequency.mixin;

import com.xm.thefourthfrequency.client_ui.MusicDirector;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.sounds.SoundManager;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Lets the score outlive the world it was playing in, once the run has ended.
 *
 * <p>Tearing a level down calls {@code soundManager.stop()}, which is the engine's "stop
 * everything" - it clears every channel in one go, music included, and it is not a fade anything
 * can lengthen. That is right for every ordinary exit: the sounds belong to a world the player is
 * leaving. It is wrong exactly once, at the end of a finished run, where the track playing is the
 * one the player is meant to be walking away to and the title screen is where it is going. See
 * {@code EndingScoreHandoff}.</p>
 *
 * <p>The redirect is on the call rather than on the method, so everything else the teardown does -
 * dropping the camera entity, the pending connection and the three render engines' level - happens
 * exactly as before. And it only ever fires while the hold is armed <em>and</em> the level being
 * installed is null: entering a world runs the same line, and the menu theme's fade-out there is a
 * deliberate piece of timing this must not touch.</p>
 */
@Mixin(Minecraft.class)
public abstract class MinecraftEndingScoreCarryMixin {
	@Redirect(method = "updateLevelInEngines(Lnet/minecraft/client/multiplayer/ClientLevel;Z)V",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/client/sounds/SoundManager;stop()V"))
	private void thefourthfrequency$carryScoreOut(SoundManager sounds, @Nullable ClientLevel level,
			boolean stopSounds) {
		if (level != null || !MusicDirector.keepsScoreAcrossDisconnect()) {
			sounds.stop();
			return;
		}
		MusicDirector.stopEverythingButTheScore(sounds);
	}
}
