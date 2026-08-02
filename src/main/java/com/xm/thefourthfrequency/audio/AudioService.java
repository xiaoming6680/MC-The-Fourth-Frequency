package com.xm.thefourthfrequency.audio;

import com.xm.thefourthfrequency.bootstrap.RuntimeServices;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

public final class AudioService {
	private AudioService() {
	}

	/**
	 * Plays a narrative cue as the two authored layers it was always meant to be.
	 *
	 * <p>The layering has to happen here rather than in {@code sounds.json}, because the
	 * {@code sounds} array there is a weighted random pool, not a stack: listing two entries
	 * under one event makes the client pick <em>one</em> of them. Four of these cues used to be
	 * written that way and were silently playing half of themselves - a door <em>or</em> a
	 * footstep, never the pair that gives the cue its meaning.</p>
	 */
	public static void play(ServerLevel level, BlockPos position, Cue cue) {
		float volume = (float) Math.clamp(
				RuntimeServices.config().meta().peakVolume() * cue.relativeVolume, 0.0D, 1.0D);
		if (volume <= 0.0F) return;
		level.playSound(null, position, cue.captioned, cue.source, volume, cue.pitch);
		level.playSound(null, position, cue.layer, cue.source, volume * 0.45F,
				Math.min(2.0F, cue.pitch * 1.08F));
	}

	/** Plays an authored encounter cue while honoring the same configured peak-volume ceiling. */
	public static void playBounded(ServerLevel level, BlockPos position, SoundEvent event,
			SoundSource source, float relativeVolume, float pitch) {
		float volume = (float) Math.clamp(RuntimeServices.config().meta().peakVolume()
				* Math.clamp(relativeVolume, 0.0F, 1.0F), 0.0D, 1.0D);
		if (volume <= 0.0F) return;
		level.playSound(null, position, event, source, volume, Math.clamp(pitch, 0.5F, 2.0F));
	}

	public enum Cue {
		EMPTY_VIEWPOINT(ModSounds.EMPTY_VIEWPOINT, ModSounds.LAYER_STONE_STEP,
				SoundSource.AMBIENT, 0.55F, 0.72F),
		EMPTY_BASE(ModSounds.EMPTY_BASE, ModSounds.LAYER_WOODEN_DOOR_CLOSE,
				SoundSource.AMBIENT, 0.62F, 0.78F),
		EMPTY_EXPERIENCE(ModSounds.EMPTY_EXPERIENCE, ModSounds.LAYER_CHEST_CLOSE,
				SoundSource.AMBIENT, 0.50F, 0.60F),
		FOURTH_BAND(ModSounds.FOURTH_BAND, ModSounds.LAYER_BEACON_DEACTIVATE,
				SoundSource.AMBIENT, 0.72F, 0.58F),
		REWORK_JOINT(ModSounds.REWORK_JOINT, ModSounds.LAYER_DEEPSLATE_BREAK,
				SoundSource.HOSTILE, 0.68F, 0.64F),
		ANOMALY_ECHO(ModSounds.ANOMALY_ECHO, ModSounds.LAYER_COMPARATOR_CLICK,
				SoundSource.AMBIENT, 0.46F, 0.68F),
		WINDOW_GLITCH(ModSounds.WINDOW_GLITCH, ModSounds.LAYER_COMPARATOR_CLICK,
				SoundSource.AMBIENT, 0.38F, 0.92F),
		DOOR_CASCADE(ModSounds.DOOR_CASCADE, ModSounds.LAYER_WOODEN_DOOR_CLOSE,
				SoundSource.AMBIENT, 0.66F, 0.61F),
		RULE_COLLAPSE(ModSounds.RULE_COLLAPSE, ModSounds.LAYER_DEEPSLATE_BREAK,
				SoundSource.AMBIENT, 0.82F, 0.48F);

		/** Carries the authored subtitle; this is the layer the player is meant to notice. */
		private final SoundEvent captioned;
		/** Sits underneath at 45%, deliberately without a subtitle of its own. */
		private final SoundEvent layer;
		private final SoundSource source;
		private final float relativeVolume;
		private final float pitch;

		Cue(SoundEvent captioned, SoundEvent layer, SoundSource source,
				float relativeVolume, float pitch) {
			this.captioned = captioned;
			this.layer = layer;
			this.source = source;
			this.relativeVolume = relativeVolume;
			this.pitch = pitch;
		}
	}
}
