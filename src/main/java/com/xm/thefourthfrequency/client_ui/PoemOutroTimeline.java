package com.xm.thefourthfrequency.client_ui;

/**
 * How the ending poem stops.
 *
 * <p>Vanilla already parks the last line near the middle of the screen - the roll pins it once it
 * crosses the centre so the closing quote can be read - but its exit condition keeps counting scroll
 * for another screen and a half beyond that. The text is motionless the whole time. From the
 * player's side the credits simply stop and nothing happens for the better part of a minute, and
 * then the world reappears with no transition at all.
 *
 * <p>So the settle becomes the ending: hold the quote long enough to read, fade the whole frame to
 * black, and hand the world back from black rather than cutting to it.
 *
 * <p>Pure and on the common side, so the schedule can be asserted without a client.
 */
public final class PoemOutroTimeline {
	/** How long the settled quote is held before anything starts happening to it. */
	public static final long HOLD_MILLIS = 2_600L;
	/** The fade itself. Slow: this is the last thing the story does. */
	public static final long FADE_MILLIS = 2_400L;
	/** Full black is held briefly before the world comes back, so the cut is never visible. */
	public static final long BLACK_MILLIS = 500L;
	/** Total from the moment the quote settles to handing the world back. */
	public static final long TOTAL_MILLIS = HOLD_MILLIS + FADE_MILLIS + BLACK_MILLIS;

	private PoemOutroTimeline() {
	}

	/**
	 * Blackout opacity in [0, 1], {@code age} milliseconds after the quote settled.
	 *
	 * <p>Smoothstepped rather than linear: a linear fade to black has a visible start, and the whole
	 * point of this one is that the player should not be able to name the moment it began.
	 */
	public static float blackoutAlpha(long ageMillis) {
		if (ageMillis <= HOLD_MILLIS) return 0.0F;
		if (ageMillis >= HOLD_MILLIS + FADE_MILLIS) return 1.0F;
		float progress = (ageMillis - HOLD_MILLIS) / (float) FADE_MILLIS;
		return progress * progress * (3.0F - 2.0F * progress);
	}

	/** Whether the world should be handed back now. */
	public static boolean finished(long ageMillis) {
		return ageMillis >= TOTAL_MILLIS;
	}

	/**
	 * Whether the screen is already fully black.
	 *
	 * <p>The hand-back has to happen behind full black, or the last frame of the poem is visible
	 * underneath the world loading in.
	 */
	public static boolean fullyBlack(long ageMillis) {
		return ageMillis >= HOLD_MILLIS + FADE_MILLIS;
	}
}
