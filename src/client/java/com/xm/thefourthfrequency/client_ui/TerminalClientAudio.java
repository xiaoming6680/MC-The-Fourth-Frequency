package com.xm.thefourthfrequency.client_ui;

import com.xm.thefourthfrequency.audio.ModSounds;
import com.xm.thefourthfrequency.bootstrap.RuntimeServices;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

public final class TerminalClientAudio {
	private static TuningLoop tuningLoop;
	private static long nextContactTick;
	private static int loopStarts;
	private static int lockPlays;
	private static int anomalyPlays;
	private static int noticeOpeningPlays;
	private static int noticeStablePlays;

	private TerminalClientAudio() {
	}

	public static void click() {
		playContact(ModSounds.TERMINAL_CLICK, 0.92F, 0.50F);
	}

	public static void passwordKey() {
		playContact(ModSounds.TERMINAL_PASSWORD, 1.14F, 0.32F);
	}

	public static void tuningInput() {
		Minecraft client = Minecraft.getInstance();
		if (client.level == null) return;
		if (tuningLoop == null || tuningLoop.isStopped()) {
			tuningLoop = new TuningLoop(baseVolume(0.20F));
			client.getSoundManager().play(tuningLoop);
			loopStarts++;
		}
		tuningLoop.requestInput();
	}

	public static void endTuningInput() {
		if (tuningLoop != null) tuningLoop.releaseInput();
	}

	public static void tick() {
		if (tuningLoop != null && tuningLoop.isStopped()) tuningLoop = null;
	}

	public static void lock() {
		lockPlays++;
		play(ModSounds.TERMINAL_LOCK, 0.82F, 0.48F);
	}

	public static void fault() {
		play(ModSounds.TERMINAL_FAULT, 0.64F, 0.42F);
	}

	/** One-shot first-run notice startup; deliberately separate from terminal tuning statistics. */
	public static void noticeOpening() {
		noticeOpeningPlays++;
		play(ModSounds.TERMINAL_FAULT, 0.58F, 0.42F);
	}

	/** One-shot first-run notice lock; deliberately does not call {@link #lock()}. */
	public static void noticeStable() {
		noticeStablePlays++;
		play(ModSounds.TERMINAL_LOCK, 0.82F, 0.48F);
	}

	public static void anomaly() {
		anomalyPlays++;
		play(ModSounds.TERMINAL_ANOMALY, 1.0F, 0.58F);
	}

	private static void playContact(SoundEvent event, float pitch, float relativeVolume) {
		Minecraft client = Minecraft.getInstance();
		long now = client.level == null ? 0L : client.level.getGameTime();
		if (now < nextContactTick) return;
		nextContactTick = now + 1L;
		play(event, pitch, relativeVolume);
	}

	private static void play(SoundEvent event, float pitch, float relativeVolume) {
		float volume = baseVolume(relativeVolume);
		if (volume <= 0.0F) return;
		Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(event, pitch, volume));
	}

	private static float baseVolume(float relativeVolume) {
		return (float) Math.clamp(
				RuntimeServices.config().meta().peakVolume() * relativeVolume, 0.0D, 1.0D);
	}

	public static int loopStartsForTesting() { return loopStarts; }
	public static boolean loopActiveForTesting() { return tuningLoop != null && !tuningLoop.isStopped(); }
	public static float loopVolumeForTesting() { return tuningLoop == null ? 0.0F : tuningLoop.currentVolume(); }
	public static int lockPlaysForTesting() { return lockPlays; }
	public static int anomalyPlaysForTesting() { return anomalyPlays; }
	public static int noticeOpeningPlaysForTesting() { return noticeOpeningPlays; }
	public static int noticeStablePlaysForTesting() { return noticeStablePlays; }
	public static void resetTuningForTesting() {
		if (tuningLoop != null) tuningLoop.forceStop();
		tuningLoop = null;
	}

	private static final class TuningLoop extends AbstractTickableSoundInstance {
		private static final int RELEASE_TICKS = 4;
		private static final int FADE_TICKS = 4;
		private final float fullVolume;
		private int ticksSinceInput;
		private boolean released;

		private TuningLoop(float volume) {
			super(ModSounds.TERMINAL_TUNE, SoundSource.AMBIENT, RandomSource.create());
			this.fullVolume = volume;
			this.volume = volume;
			this.pitch = 0.82F;
			this.looping = true;
			this.relative = true;
			this.attenuation = Attenuation.NONE;
		}

		private void requestInput() {
			ticksSinceInput = 0;
			released = false;
			volume = fullVolume;
		}

		private void releaseInput() {
			released = true;
		}

		private float currentVolume() {
			return volume;
		}

		private void forceStop() {
			stop();
		}

		@Override
		public void tick() {
			ticksSinceInput++;
			if (!released && ticksSinceInput <= RELEASE_TICKS) return;
			int fadeAge = ticksSinceInput - RELEASE_TICKS;
			volume = fullVolume * Math.clamp(1.0F - fadeAge / (float) FADE_TICKS, 0.0F, 1.0F);
			if (fadeAge >= FADE_TICKS) stop();
		}
	}
}
