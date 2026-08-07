package com.xm.thefourthfrequency.client_ui;

/**
 * The pause menu's exit control failing, as a schedule.
 *
 * <p>Replaces a layer of hexadecimal strings that drifted across the pause screen. That read as
 * text pasted over the menu rather than as the menu itself being in trouble, and it said nothing
 * about the one control it was there to be about - the way out. What is wrong is the door, so the
 * damage belongs on the door.
 *
 * <p>The vocabulary is the mod's existing one - {@code AnalogFilter}'s grain, scanlines and roll
 * bar, the same tape damage the loading screen and the terminal's weather card use - clipped to the
 * button's own rectangle. A player who has met this fault three times elsewhere should recognise it
 * here rather than meet a fourth idea of what is wrong.
 *
 * <p><b>Every rate here is well under the 3 Hz ceiling.</b> The roll bar passes once every
 * {@link #ROLL_PERIOD_MILLIS} (about 0.4 Hz) and the grain breathes at half that; the grain itself
 * is per-pixel noise rather than a state change, so it has no flash rate to speak of. Nothing here
 * is allowed to become a strobe over a button a player may need to press.
 *
 * <p>Pure and on the common side, so the rates can be asserted against the safety ceiling without
 * a client.
 */
public final class ExitDecayTimeline {
	/** One pass of the mistracking bar. 0.38 Hz. */
	public static final long ROLL_PERIOD_MILLIS = 2_600L;
	/** How long one pass takes to cross the control, out of that period. */
	public static final long ROLL_SWEEP_MILLIS = 900L;
	/** Slow breath on the grain, so the noise floor is never quite steady. 0.19 Hz. */
	public static final long GRAIN_PERIOD_MILLIS = 5_200L;

	/** Ceiling the safety notice promises. Nothing in this class may reach it. */
	public static final double MAX_FLASH_HZ = 3.0D;

	private static final float GRAIN_BASE = 0.28F;
	private static final float GRAIN_SWING = 0.12F;
	private static final float SCANLINE_STRENGTH = 0.55F;

	private ExitDecayTimeline() {
	}

	/** Per-pixel noise over the control, breathing slowly. */
	public static float grainStrength(long millis) {
		double phase = Math.floorMod(millis, GRAIN_PERIOD_MILLIS) / (double) GRAIN_PERIOD_MILLIS;
		return (float) (GRAIN_BASE + GRAIN_SWING * Math.sin(phase * 2.0D * Math.PI));
	}

	/** Fixed: a CRT's line structure does not come and go. */
	public static float scanlineStrength() {
		return SCANLINE_STRENGTH;
	}

	/**
	 * How far the mistracking bar has crossed the control, in [0, 1], or -1 between passes.
	 *
	 * <p>Starts above the control and ends below it, so it enters and leaves rather than appearing
	 * in the middle of it.
	 */
	public static float rollProgress(long millis) {
		long into = Math.floorMod(millis, ROLL_PERIOD_MILLIS);
		if (into >= ROLL_SWEEP_MILLIS) return -1.0F;
		return into / (float) ROLL_SWEEP_MILLIS;
	}

	/**
	 * Bar strength across one pass: in and out, so it does not pop at either end.
	 *
	 * @param progress from {@link #rollProgress}; negative means no bar
	 */
	public static float rollStrength(float progress) {
		if (progress < 0.0F || progress > 1.0F) return 0.0F;
		float centred = 1.0F - Math.abs(progress * 2.0F - 1.0F);
		return centred * centred;
	}
}
