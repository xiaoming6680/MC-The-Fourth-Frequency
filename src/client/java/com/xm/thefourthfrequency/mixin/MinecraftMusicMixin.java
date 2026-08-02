package com.xm.thefourthfrequency.mixin;

import com.xm.thefourthfrequency.client_ui.MusicDirector;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.Music;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Hands the background score to {@link MusicDirector}.
 *
 * <p>Both seams are vanilla's own: the music manager already asks {@code getSituationalMusic} what
 * to play next and already accepts null for "nothing", and it already eases its category gain
 * towards {@code getMusicVolume} so that a dropping target fades the current track out instead of
 * cutting it. Replacing the two answers is therefore enough to install a different score without
 * touching how music is scheduled, faded, or reported to the now-playing toast.</p>
 */
@Mixin(Minecraft.class)
public abstract class MinecraftMusicMixin {
	@Inject(method = "getSituationalMusic", at = @At("HEAD"), cancellable = true)
	private void thefourthfrequency$authoredScore(CallbackInfoReturnable<Music> callback) {
		callback.setReturnValue(MusicDirector.situationalMusic((Minecraft) (Object) this));
	}

	@Inject(method = "getMusicVolume", at = @At("RETURN"), cancellable = true)
	private void thefourthfrequency$fadeAuthoredScore(CallbackInfoReturnable<Float> callback) {
		callback.setReturnValue(
				MusicDirector.musicVolume((Minecraft) (Object) this, callback.getReturnValueF()));
	}
}
