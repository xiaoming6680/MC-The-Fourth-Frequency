package com.xm.thefourthfrequency.terminal;

/**
 * The order and pace of light_dropout.
 *
 * <p>The anomaly used to be two frames: every extinguishable light within sixteen blocks was
 * replaced in one server tick, and every one of them came back in one server tick at the end.
 * Nothing physical happens that way, so there was nothing for the player to attribute it to
 * except the mod - and the instant restore told them, precisely, that it was over.</p>
 *
 * <p>Ordered by distance instead, the same set of blocks says something entirely different. The
 * lights go out from the outside in, so what the player watches is the dark closing on them
 * rather than somebody flipping a switch; and they come back the other way, from the nearest
 * outward, more slowly than they left. The farthest light is the last thing to return, which
 * means the anomaly finishes somewhere the player cannot see.</p>
 *
 * <p>Counting rather than scheduling: each tick asks how many lights <em>should</em> be out and
 * how many should be back by now, and the caller applies the difference. That makes the whole
 * sequence a pure function of elapsed time, so a dropped tick or a lagging server catches up
 * instead of losing a light, and the pacing can be verified without a world.</p>
 */
public final class LightDropoutSequence {
	private static final float EXTINGUISH_FRACTION = 0.30F;
	private static final int MAX_EXTINGUISH_TICKS = 60;
	/** Longer than the extinguish window: coming back slowly is worse than coming back fast. */
	private static final float RESTORE_FRACTION = 0.40F;
	private static final int MAX_RESTORE_TICKS = 100;

	private LightDropoutSequence() {
	}

	public static int extinguishWindow(int durationTicks) {
		return window(durationTicks, EXTINGUISH_FRACTION, MAX_EXTINGUISH_TICKS);
	}

	public static int restoreWindow(int durationTicks) {
		return window(durationTicks, RESTORE_FRACTION, MAX_RESTORE_TICKS);
	}

	/** The tick the lights start coming back, leaving a held dark between the two windows. */
	public static int restoreStartTick(int durationTicks) {
		return Math.max(extinguishWindow(durationTicks),
				Math.max(1, durationTicks) - restoreWindow(durationTicks));
	}

	/** How many of {@code count} lights, farthest first, should be out by {@code elapsedTicks}. */
	public static int extinguishedBy(int elapsedTicks, int count, int durationTicks) {
		if (count <= 0 || elapsedTicks <= 0) return 0;
		int window = extinguishWindow(durationTicks);
		if (elapsedTicks >= window) return count;
		return Math.clamp(ceilDiv(count * elapsedTicks, window), 0, count);
	}

	/** How many, nearest first, should be back by {@code elapsedTicks}. */
	public static int restoredBy(int elapsedTicks, int count, int durationTicks) {
		if (count <= 0) return 0;
		int start = restoreStartTick(durationTicks);
		if (elapsedTicks <= start) return 0;
		int window = restoreWindow(durationTicks);
		if (elapsedTicks >= start + window) return count;
		return Math.clamp(ceilDiv(count * (elapsedTicks - start), window), 0, count);
	}

	/**
	 * Index into the ordered candidate list for the {@code step}-th light to come back.
	 *
	 * <p>Index 0 is the farthest and went out first, so restoration walks the list backwards.</p>
	 */
	public static int restoreIndex(int step, int count) {
		return count - 1 - Math.clamp(step, 0, Math.max(0, count - 1));
	}

	private static int window(int durationTicks, float fraction, int cap) {
		return Math.max(1, Math.min(cap, Math.round(Math.max(1, durationTicks) * fraction)));
	}

	private static int ceilDiv(int value, int divisor) {
		return (value + divisor - 1) / divisor;
	}
}
