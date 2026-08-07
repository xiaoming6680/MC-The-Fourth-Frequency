package com.xm.thefourthfrequency.terminal;

/**
 * Easing curves and timing constants for the terminal display.
 *
 * <p>Lives in the common source set for the same reason {@link TerminalUiLayout} does: it has no
 * Minecraft dependency, so JUnit can assert "given this elapsed time, return this value" directly
 * instead of inferring the curve from a screenshot.</p>
 *
 * <p>Two families of clock live here and they are not interchangeable:</p>
 *
 * <ul>
 * <li>{@link #progress} drives transitions with a definite start instant - a page change, a button
 * press, a boot line. They are linear so the duration constant below means what it says.</li>
 * <li>{@link #catchUp} drives values whose target moves while they are in flight - an objective bar
 * that gains ground every server tick, a hover that follows the pointer. Restarting a fixed
 * transition on every new target makes those stutter; an exponential follower just tracks.</li>
 * </ul>
 *
 * <p>Neither family measures time in ticks. Tick-derived durations are pinned to the tick rate, so
 * a server hitch changes how fast the animation plays. Pulses are the one exception and stay on the
 * tick clock, because the flicker-safety rules are reasoned about in ticks.</p>
 */
public final class TerminalMotion {
	/** Page slide. Long enough to read as movement, short enough to spam the tab keys through. */
	public static final long PAGE_TRANSITION_MILLIS = 160L;
	public static final long TAB_INDICATOR_MILLIS = 140L;
	/** Press feedback rises fast and falls slower, so a click reads as a strike rather than a blink. */
	public static final long PRESS_ATTACK_MILLIS = 90L;
	public static final long PRESS_RELEASE_MILLIS = 120L;
	/** Roughly how long a hover takes to settle. Hover follows the pointer, so it is a follower. */
	public static final long HOVER_MILLIS = 110L;

	public static final double PROGRESS_TAU_MILLIS = 110.0D;
	/**
	 * Hover time constant. An exponential follower covers about 95% of its distance in three time
	 * constants, so this is {@link #HOVER_MILLIS} divided by three rather than equal to it.
	 */
	public static final double HOVER_TAU_MILLIS = HOVER_MILLIS / 3.0D;
	/**
	 * Ceiling on a single frame's delta.
	 *
	 * <p>Without it, the first frame after the window regains focus carries the whole time it spent
	 * in the background and every follower snaps to its target in one step - which looks exactly like
	 * the jump the followers exist to remove.</p>
	 */
	public static final long MAX_FRAME_DELTA_MILLIS = 100L;

	private TerminalMotion() {
	}

	/** Clamped 0..1 progress of a transition started at {@code startedAtMillis}. */
	public static double progress(long startedAtMillis, long nowMillis, long durationMillis) {
		if (durationMillis <= 0L) throw new IllegalArgumentException("durationMillis must be positive");
		return elapsedProgress(nowMillis - startedAtMillis, durationMillis);
	}

	/** Clamped 0..1 progress from an already-measured elapsed time. Negative elapsed reads as 0. */
	public static double elapsedProgress(long elapsedMillis, long durationMillis) {
		if (durationMillis <= 0L) throw new IllegalArgumentException("durationMillis must be positive");
		return Math.clamp(elapsedMillis / (double) durationMillis, 0.0D, 1.0D);
	}

	public static double linear(double t) {
		return Math.clamp(t, 0.0D, 1.0D);
	}

	public static double easeOutQuad(double t) {
		double clamped = linear(t);
		return 1.0D - (1.0D - clamped) * (1.0D - clamped);
	}

	/** Per-channel colour interpolation including alpha. */
	public static int lerpColor(int from, int to, double t) {
		double clamped = linear(t);
		int a = lerpChannel(from >>> 24, to >>> 24, clamped);
		int r = lerpChannel(from >>> 16 & 0xFF, to >>> 16 & 0xFF, clamped);
		int g = lerpChannel(from >>> 8 & 0xFF, to >>> 8 & 0xFF, clamped);
		int b = lerpChannel(from & 0xFF, to & 0xFF, clamped);
		return a << 24 | r << 16 | g << 8 | b;
	}

	private static int lerpChannel(int from, int to, double t) {
		return (int) Math.round(from + (to - from) * t);
	}

	/**
	 * Moves {@code current} toward {@code target} by a frame-rate independent exponential step.
	 *
	 * <p>Splitting one 100 ms frame into ten 10 ms frames lands on the same value, because the
	 * remaining distance is multiplied by {@code exp(-dt/tau)} either way and the exponents add. That
	 * property is what makes this safe to drive from render: the animation does not run faster on a
	 * faster machine.</p>
	 *
	 * <p>{@code deltaMillis} is clamped to {@link #MAX_FRAME_DELTA_MILLIS} first, so the identity
	 * above only holds for frames inside that ceiling - which is the point of the ceiling.</p>
	 */
	public static double catchUp(double current, double target, long deltaMillis, double tauMillis) {
		if (tauMillis <= 0.0D) throw new IllegalArgumentException("tauMillis must be positive");
		long delta = Math.clamp(deltaMillis, 0L, MAX_FRAME_DELTA_MILLIS);
		if (delta == 0L) return current;
		return target + (current - target) * Math.exp(-delta / tauMillis);
	}


	/**
	 * How many code points of a {@code total}-long line are visible after {@code elapsedMillis}.
	 *
	 * <p>Monotonic and clamped at both ends, so a caller can cache the rendered prefix and only
	 * rebuild it when this number actually changes.</p>
	 */
	public static int typedCharacters(int total, long elapsedMillis, long perCharMillis) {
		if (perCharMillis <= 0L) throw new IllegalArgumentException("perCharMillis must be positive");
		if (total <= 0 || elapsedMillis <= 0L) return 0;
		long typed = elapsedMillis / perCharMillis;
		return (int) Math.clamp(typed, 0L, total);
	}

	/**
	 * Continuous 0..1 pulse on the tick clock, one full cycle per {@code periodTicks}.
	 *
	 * <p>A raised cosine rather than a square wave or a sawtooth. Anything that alternates between
	 * two values has to answer the minimum-hold rule; a continuous curve has no state change to hold
	 * in the first place. The same reasoning is spelled out at length on the boss-fight palette,
	 * which measured the frame-to-frame jump a sawtooth put on a full-screen parameter.</p>
	 */
	public static double breathe(double ageTicks, double periodTicks) {
		if (periodTicks <= 0.0D) throw new IllegalArgumentException("periodTicks must be positive");
		return (1.0D - Math.cos(ageTicks * 2.0D * Math.PI / periodTicks)) * 0.5D;
	}
}
