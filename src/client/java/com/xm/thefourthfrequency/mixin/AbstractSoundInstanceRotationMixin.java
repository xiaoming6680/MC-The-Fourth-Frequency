package com.xm.thefourthfrequency.mixin;

import com.xm.thefourthfrequency.audio.MusicRotationPolicy;
import com.xm.thefourthfrequency.bootstrap.TheFourthFrequency;
import net.minecraft.client.resources.sounds.AbstractSoundInstance;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Re-draws a background track that would follow itself.
 *
 * <p>This is the only place the choice is made. {@code AbstractSoundInstance#resolve} is where an
 * event id becomes a concrete file - {@code this.sound = events.getSound(this.random)} - and it is
 * the last point at which the pick is still changeable; by the time the engine sees the instance it
 * is holding a {@code Sound}, not a pool. {@code WeighedSoundEvents} itself is the wrong hook
 * because it does not carry its own id, so a mixin there could not tell the score apart from the
 * eight attack cues that are supposed to draw freely.
 *
 * <p>Scoped twice over: to this mod's namespace, and to the score events
 * {@link MusicRotationPolicy#rotates} names. Every other sound in the game, including this mod's
 * own, resolves exactly as it did.
 *
 * <p>Re-drawing rather than picking an index keeps vanilla's weighting intact. The pool entries may
 * carry weights; choosing "some other entry" by hand would quietly flatten them, whereas drawing
 * again and rejecting a repeat leaves the relative odds of everything else untouched.
 */
@Mixin(AbstractSoundInstance.class)
public abstract class AbstractSoundInstanceRotationMixin {
	@Shadow protected Sound sound;
	@Shadow @Final protected Identifier identifier;
	@Shadow protected RandomSource random;

	@Inject(method = "resolve", at = @At("RETURN"))
	private void thefourthfrequency$avoidBackToBackTrack(SoundManager manager,
			CallbackInfoReturnable<WeighedSoundEvents> callback) {
		if (!TheFourthFrequency.MOD_ID.equals(identifier.getNamespace())
				|| !MusicRotationPolicy.rotates(identifier.getPath())) {
			return;
		}
		WeighedSoundEvents events = callback.getReturnValue();
		if (events == null || sound == null || random == null) return;
		String event = identifier.getPath();
		for (int attempt = 0; attempt < MusicRotationPolicy.MAX_REROLLS
				&& MusicRotationPolicy.repeats(event, sound.getLocation().toString()); attempt++) {
			Sound redrawn = events.getSound(random);
			if (redrawn == null) break;
			sound = redrawn;
		}
		// Recorded after the loop settles, so the memory is what the player actually hears rather
		// than the first draw - including the case where the ceiling was reached and a repeat stands.
		MusicRotationPolicy.remember(event, sound.getLocation().toString());
	}
}
