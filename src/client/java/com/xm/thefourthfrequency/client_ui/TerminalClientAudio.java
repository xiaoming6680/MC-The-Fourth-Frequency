package com.xm.thefourthfrequency.client_ui;

import com.xm.thefourthfrequency.audio.ModSounds;
import com.xm.thefourthfrequency.bootstrap.RuntimeServices;
import com.xm.thefourthfrequency.networking.TerminalNoticePayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Util;

public final class TerminalClientAudio {
	private static final RandomSource JITTER = RandomSource.create();
	private static TuningLoop tuningLoop;
	private static long nextContactTick;
	private static int loopStarts;
	private static int lockPlays;
	private static int noticeOpeningPlays;
	private static int noticeStablePlays;
	private static long nextAttentionMillis;
	private static int attentionPlays;
	private static int signalSweepPlays;
	private static int detentPlays;

	private TerminalClientAudio() {
	}

	public static void click() {
		playContact(ModSounds.TERMINAL_CLICK, 0.92F, 0.50F);
	}

	/**
	 * The lighter contact, for moving through a list rather than choosing something in it.
	 *
	 * <p>Every interaction used to fire the same click at the same weight, so scrolling a file
	 * list sounded exactly as consequential as committing a command. Splitting the two gives the
	 * panel a hierarchy the player can hear without having to look.</p>
	 */
	public static void keypress() {
		playContact(ModSounds.TERMINAL_KEYPRESS, 1.0F, 0.34F);
	}

	/** One notch of the dial. Rides on top of the sweep loop {@link #tuningInput()} maintains. */
	public static void detent() {
		detentPlays++;
		playContact(ModSounds.TERMINAL_DETENT, 0.98F, 0.26F);
	}

	/**
	 * One self-test line landing.
	 *
	 * <p>The pitch climbs a little with each check, which is what turns six identical clicks into a
	 * sequence going somewhere. Small steps: the point is that the machine is working through a
	 * list, not that it is playing a tune.
	 *
	 * @param line zero-based index of the line that just appeared
	 */
	public static void bootLine(int line) {
		playContact(ModSounds.TERMINAL_BOOT_LINE, 0.86F + 0.045F * Math.clamp(line, 0, 5), 0.40F);
	}

	/** The last check. It answers differently, because it is the one that says the device is up. */
	public static void bootComplete() {
		playContact(ModSounds.TERMINAL_BOOT_COMPLETE, 1.0F, 0.46F);
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

	/**
	 * Plays once when the near-field receiver becomes tunable for real, not while the dial is
	 * being dragged - {@link #tuningInput()} already owns that with a loop. This is the moment
	 * the band opens up, and the sweep is the sound of finding it occupied by several things
	 * that will not identify themselves.
	 */
	public static void signalSweep() {
		signalSweepPlays++;
		play(ModSounds.SIGNAL_TUNING_SWEEP, 1.0F, 0.44F);
	}

	public static void fault() {
		play(ModSounds.TERMINAL_FAULT, 0.64F, 0.42F);
	}

	/**
	 * The sky monitor admitting it has lost what it was measuring.
	 *
	 * <p>The carrier-loss cue rather than {@link #fault()} on purpose. A fault is the terminal
	 * having a problem with itself; a lost carrier is the thing it was listening to going away.
	 * On the weather page during a sky anomaly the second one is the true statement, and the
	 * player has already been taught what that sample means by the anomaly system itself.</p>
	 */
	public static void skyCarrierLost() {
		play(ModSounds.SIGNAL_CARRIER_LOST, 0.92F, 0.38F);
	}

	/**
	 * One-shot first-run notice startup; deliberately separate from terminal tuning statistics.
	 *
	 * <p>Pitched well below {@link #fault()} rather than a hair under it. Both used to resolve to
	 * the same sample within 0.06 of the same volume and pitch, so the comment claiming they were
	 * distinct was only true of the statistics counters - nothing about them was audibly
	 * different. This one has to read as the machine coming up for the first time, which is a
	 * slower and heavier gesture than something going wrong.</p>
	 */
	public static void noticeOpening() {
		noticeOpeningPlays++;
		play(ModSounds.TERMINAL_FAULT, 0.44F, 0.52F);
	}

	/**
	 * One-shot first-run notice lock; deliberately does not call {@link #lock()} - and now
	 * actually sounds unlike it, an octave-ish down and louder, because locking onto the first
	 * signal you ever find is not the same event as retuning onto another one later.
	 */
	public static void noticeStable() {
		noticeStablePlays++;
		play(ModSounds.TERMINAL_LOCK, 0.62F, 0.58F);
	}

	public static void attention(int tone) {
		long now = Util.getMillis();
		if (now < nextAttentionMillis) return;
		nextAttentionMillis = now + 300L;
		attentionPlays++;
		if (tone == TerminalNoticePayload.TONE_PURSUIT_WARNING) {
			play(ModSounds.TERMINAL_ANOMALY, 0.72F, 0.66F);
		} else if (tone == TerminalNoticePayload.TONE_TASK_COMPLETE) {
			play(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.0F, 0.92F);
		} else if (tone == TerminalNoticePayload.TONE_DENIED) {
			// A refusal is a dull short fault, never the chime that reads as progress.
			play(ModSounds.TERMINAL_FAULT, 0.88F, 0.34F);
		} else if (tone == TerminalNoticePayload.TONE_ANCHOR) {
			// An anchor falling is the loudest thing the table can cause; it gets the resonant hit.
			play(ModSounds.WORLD_INTERFACE_ANCHOR, 1.0F, 0.52F);
		} else if (tone == TerminalNoticePayload.TONE_ENCOUNTER) {
			play(ModSounds.WORLD_INTERFACE_IMPACT, 0.70F, 0.30F);
		} else if (tone == TerminalNoticePayload.TONE_DRAGON) {
			// The dragon is the only friendly voice in the finale, so it does not share the boss cues.
			play(SoundEvents.ENDER_DRAGON_AMBIENT, 1.25F, 0.34F);
		} else {
			play(SoundEvents.NOTE_BLOCK_CHIME.value(), 1.18F, 0.84F);
		}
	}

	/**
	 * Contact sounds are the ones that fire in bursts - a held dial, a run of keystrokes - so
	 * they are the ones that expose how few variants there are. Four samples at a fixed pitch
	 * start sounding like four samples very quickly; a little jitter on each hit is enough to
	 * keep a run of them sounding like a worn mechanism rather than a short playlist.
	 */
	private static void playContact(SoundEvent event, float pitch, float relativeVolume) {
		Minecraft client = Minecraft.getInstance();
		long now = client.level == null ? 0L : client.level.getGameTime();
		if (now < nextContactTick) return;
		nextContactTick = now + 1L;
		play(event, jitter(pitch, 0.05F), relativeVolume);
	}

	private static float jitter(float pitch, float amount) {
		return Math.clamp(pitch * (1.0F + (JITTER.nextFloat() * 2.0F - 1.0F) * amount), 0.5F, 2.0F);
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
	public static int noticeOpeningPlaysForTesting() { return noticeOpeningPlays; }
	public static int noticeStablePlaysForTesting() { return noticeStablePlays; }
	public static int attentionPlaysForTesting() { return attentionPlays; }
	public static int signalSweepPlaysForTesting() { return signalSweepPlays; }
	public static int detentPlaysForTesting() { return detentPlays; }
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
