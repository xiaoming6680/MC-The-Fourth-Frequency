package com.xm.thefourthfrequency.mixin;

import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.MusicManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Reaches the music fade's own gain so a track can be made to start from silence, and so a handover
 * between two tracks can be driven faster than the vanilla curve allows.
 *
 * <p>The manager only ever eases this value while something is already playing, so a track that
 * begins while the gain still sits at one simply appears at full volume. Dropping it to zero at the
 * moments where music is about to return - the safety notice being dismissed, a pursuit ending -
 * is what turns those starts into fade-ins rather than entrances.</p>
 *
 * <p>The vanilla curve is tuned for a score that changes a few times an hour: a fade-out multiplies
 * the gain by 0.97 a tick and a fade-in adds at most 0.005, so a full pass either way runs ten to
 * fifteen seconds. That is far too slow to sit between two tracks that swap on a single beat, which
 * is why {@link com.xm.thefourthfrequency.client_ui.MusicDirector} reads the gain back and pushes
 * it along during a handover.</p>
 */
@Mixin(MusicManager.class)
public interface MusicManagerGainAccessor {
	@Accessor("currentGain")
	float thefourthfrequency$currentGain();

	@Accessor("currentGain")
	void thefourthfrequency$setCurrentGain(float gain);

	/** Null while nothing is playing - the only state in which forcing the gain is safe. */
	@Accessor("currentMusic")
	SoundInstance thefourthfrequency$currentMusic();

	/**
	 * Ticks the manager still wants to wait before it starts the next track.
	 *
	 * <p>Every stop adds a hundred to it, and the "constant" music-frequency option floors the gap
	 * at a hundred no matter what the incoming track's own pacing says. Both would turn the silence
	 * in the middle of a handover into five extra seconds of nothing, so the handover clears it.</p>
	 */
	@Accessor("nextSongDelay")
	void thefourthfrequency$setNextSongDelay(int delay);
}
