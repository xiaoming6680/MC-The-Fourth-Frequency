package com.xm.thefourthfrequency.client_ui;

import com.xm.thefourthfrequency.audio.ModSounds;
import com.xm.thefourthfrequency.bootstrap.RuntimeServices;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

import java.util.EnumMap;
import java.util.Map;

/**
 * The continuous noise floor under the first-entry corruption.
 *
 * <p>Before this existed the sequence had two one-shots and silence between them, which is the
 * sound of a cutscene. What sells a tape is the medium being audible the entire time: hiss that
 * was always there, static that joins when the picture goes, dead air that is unmistakably an
 * open channel with nothing on it.</p>
 *
 * <p>Separate from {@link SignalBedController} on purpose - that one requires a live level and
 * player, and stops itself the moment either is missing, which is exactly the situation the
 * loading screen is. These beds are owned by the loading screen and die with it.</p>
 */
public final class AlphaCorruptionAudio {
	private static final float HISS_VOLUME = 0.42F;
	private static final float STATIC_VOLUME = 0.50F;
	private static final float DEAD_AIR_VOLUME = 0.66F;
	private static final float CARRIER_LOST_VOLUME = 0.62F;
	private static final float VOLUME_EASE = 0.12F;

	private static final Map<Bed, TapeLoop> ACTIVE = new EnumMap<>(Bed.class);
	private static boolean carrierLostPlayed;

	private AlphaCorruptionAudio() {
	}

	/**
	 * Drives every bed from the screen tick, so the mix cannot drift out of step with the
	 * picture even if a frame is dropped.
	 */
	public static void tick(Minecraft client, int screenTicks) {
		setTarget(client, Bed.TAPE_HISS, hissTarget(screenTicks));
		setTarget(client, Bed.STATIC, staticTarget(screenTicks));
		setTarget(client, Bed.DEAD_AIR, deadAirTarget(screenTicks));
		if (!carrierLostPlayed && screenTicks >= AlphaLoadTimeline.LEGACY_RECOVERY_START_TICK) {
			carrierLostPlayed = true;
			client.getSoundManager().play(SimpleSoundInstance.forUI(
					ModSounds.SIGNAL_CARRIER_LOST, 1.0F, CARRIER_LOST_VOLUME));
		}
	}

	/**
	 * Hard stop for every bed. Must run when the loading screen goes away by any route - a bed
	 * that outlives its screen would follow the player into a world that has no idea why it is
	 * hissing.
	 */
	public static void stopAll() {
		for (TapeLoop loop : ACTIVE.values()) loop.forceStop();
		ACTIVE.clear();
		carrierLostPlayed = false;
	}

	private static float hissTarget(int screenTicks) {
		if (screenTicks < AlphaLoadTimeline.GLITCH_START_TICK) return 0.0F;
		// Survives the blackout and the recovery: the tape is still running the whole time.
		float arrival = AlphaLoadTimeline.ramp(screenTicks, AlphaLoadTimeline.GLITCH_START_TICK,
				AlphaLoadTimeline.LAYER_FADE_TICKS);
		return HISS_VOLUME * arrival;
	}

	private static float staticTarget(int screenTicks) {
		if (screenTicks >= AlphaLoadTimeline.BLACKOUT_START_TICK) {
			return STATIC_VOLUME * (1.0F - AlphaLoadTimeline.ramp(screenTicks,
					AlphaLoadTimeline.BLACKOUT_START_TICK,
					AlphaLoadTimeline.BLACKOUT_COLLAPSE_TICKS));
		}
		return STATIC_VOLUME * AlphaLoadTimeline.ramp(screenTicks,
				AlphaLoadTimeline.FLOOD_START_TICK, AlphaLoadTimeline.FLOOD_WIPE_TICKS);
	}

	private static float deadAirTarget(int screenTicks) {
		if (!AlphaLoadTimeline.blackoutFrame(screenTicks)) return 0.0F;
		return DEAD_AIR_VOLUME * AlphaLoadTimeline.ramp(screenTicks,
				AlphaLoadTimeline.BLACKOUT_START_TICK, AlphaLoadTimeline.BLACKOUT_COLLAPSE_TICKS);
	}

	private static void setTarget(Minecraft client, Bed bed, float target) {
		TapeLoop loop = ACTIVE.get(bed);
		if (loop != null && loop.isStopped()) {
			ACTIVE.remove(bed);
			loop = null;
		}
		if (loop == null) {
			if (target <= 0.0F) return;
			loop = new TapeLoop(bed.cue());
			ACTIVE.put(bed, loop);
			client.getSoundManager().play(loop);
		}
		loop.target(target);
	}

	private enum Bed {
		TAPE_HISS,
		STATIC,
		DEAD_AIR;

		SoundEvent cue() {
			return switch (this) {
				case TAPE_HISS -> ModSounds.SIGNAL_TAPE_HISS;
				case STATIC -> ModSounds.SIGNAL_STATIC;
				case DEAD_AIR -> ModSounds.SIGNAL_DEAD_AIR;
			};
		}
	}

	private static final class TapeLoop extends AbstractTickableSoundInstance {
		private float target;

		private TapeLoop(SoundEvent cue) {
			// MASTER, matching the signal beds: this noise is not part of the world, and must not
			// be silenced by a category that governs the world.
			super(cue, SoundSource.MASTER, RandomSource.create());
			this.volume = 0.0F;
			this.pitch = 1.0F;
			this.looping = true;
			this.relative = true;
			this.attenuation = Attenuation.NONE;
		}

		private void target(float value) {
			target = Math.clamp(value, 0.0F, 1.0F);
		}

		private void forceStop() {
			stop();
		}

		@Override
		public boolean canStartSilent() {
			return true;
		}

		@Override
		public void tick() {
			float scaled = (float) (target * RuntimeServices.config().meta().effectiveBedVolume());
			// Eased rather than stepped, so a bed arriving cannot be heard arriving.
			volume += (scaled - volume) * VOLUME_EASE;
			if (target <= 0.0F && volume < 0.004F) stop();
		}
	}
}
