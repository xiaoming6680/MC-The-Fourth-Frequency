package com.xm.thefourthfrequency.client_ui;

/**
 * When the ending poem may be skipped, and how the refusal notice fades.
 *
 * <p>The poem is the only place the run's outcome is actually said, and Escape is directly under a
 * player's thumb from the fight they have just finished. Holding the exit for the first few seconds
 * is enough to stop the ending being dismissed before it is read as an ending - and no longer,
 * because an exit that is gone rather than briefly withheld is one of the things this mod promises
 * never to do.
 *
 * <p>The refusal has to say why. A key that silently does nothing is indistinguishable from a key
 * that is broken, so the press is answered with the number of seconds still to wait.
 *
 * <p>Pure and on the common side, so the whole schedule can be asserted without a client.
 */
public final class PoemSkipNoticeTimeline {
	/** How long the poem holds the exit shut, in milliseconds. */
	public static final long SKIP_LOCKED_MILLIS = 10_000L;

	/** Notice fade-in, in milliseconds. Short: it is answering a keypress and must feel immediate. */
	public static final long FADE_IN_MILLIS = 220L;
	/** How long it stays fully legible after fading in. */
	public static final long HOLD_MILLIS = 3_000L;
	/** Fade-out. Longer than the fade-in, so it leaves rather than blinks off. */
	public static final long FADE_OUT_MILLIS = 600L;
	/** Total life of one notice. */
	public static final long TOTAL_MILLIS = FADE_IN_MILLIS + HOLD_MILLIS + FADE_OUT_MILLIS;

	/** Peak opacity. Deliberately short of solid - it is an aside, not the poem. */
	public static final float PEAK_ALPHA = 0.72F;

	private PoemSkipNoticeTimeline() {
	}

	/** Whether Escape is allowed to close the poem, {@code age} milliseconds after it opened. */
	public static boolean allowsSkip(long ageMillis) {
		return ageMillis >= SKIP_LOCKED_MILLIS;
	}

	/**
	 * Seconds still to wait, as the notice should say them.
	 *
	 * <p>Rounded up, so the last partial second reads as "1" rather than "0": a notice that says
	 * zero while the key is still refused is worse than no notice.
	 */
	public static int secondsRemaining(long ageMillis) {
		long remaining = SKIP_LOCKED_MILLIS - Math.max(0L, ageMillis);
		if (remaining <= 0L) return 0;
		return (int) ((remaining + 999L) / 1_000L);
	}

	/**
	 * Notice opacity in [0, {@link #PEAK_ALPHA}], {@code age} milliseconds after the refused press.
	 *
	 * @param ageMillis negative or past {@link #TOTAL_MILLIS} means nothing is drawn
	 */
	public static float noticeAlpha(long ageMillis) {
		if (ageMillis < 0L || ageMillis >= TOTAL_MILLIS) return 0.0F;
		if (ageMillis < FADE_IN_MILLIS) {
			return PEAK_ALPHA * (ageMillis / (float) FADE_IN_MILLIS);
		}
		if (ageMillis < FADE_IN_MILLIS + HOLD_MILLIS) return PEAK_ALPHA;
		long into = ageMillis - FADE_IN_MILLIS - HOLD_MILLIS;
		return PEAK_ALPHA * (1.0F - into / (float) FADE_OUT_MILLIS);
	}
}
