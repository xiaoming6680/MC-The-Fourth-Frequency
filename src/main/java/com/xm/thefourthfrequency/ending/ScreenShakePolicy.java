package com.xm.thefourthfrequency.ending;

/**
 * The arithmetic behind camera shake, kept off the client so it can be tested.
 *
 * <p>Same split as {@code PursuitPresentationTimeline}: the decay curves, the distance falloff and
 * the grade-to-amplitude table live here in {@code src/main} where a unit test can reach them, and
 * the client-side controller does nothing but sample them and hand the result to the camera. A
 * shake that never settles back to zero leaves the player's view permanently crooked, and that is a
 * property of the curve rather than of the renderer - so it is checked here.
 *
 * <p>Everything is driven by wall-clock seconds rather than by ticks. Sampling a 13-27 Hz waveform
 * on a 20 Hz tick clock aliases it into a slow wobble or, worse, a strobe; the shake has to run on
 * the frame clock to be a shake at all.
 */
public final class ScreenShakePolicy {
	/**
	 * Three incommensurable frequencies, in hertz.
	 *
	 * <p>Their ratios are deliberately not simple: summed, they never repeat inside the length of an
	 * impulse, so the shake reads as an impact rather than as a vibration. A single sine - or two
	 * that share a factor - is immediately identifiable as a loop.
	 */
	public static final double[] FREQUENCIES_HZ = {13.7D, 19.3D, 27.1D};

	/**
	 * Peak angular displacement in degrees, and duration in seconds, per grade.
	 *
	 * <p>The two middle grades carry the encounter's detonations - the energy bolt's blast above all,
	 * which is the one a player meets most often. They were landing softer than the explosion they
	 * are drawn on top of: a full-screen fireball that moves the camera about a degree reads as a
	 * decal rather than as a hit. Raised on the amplitude only; the durations are untouched, because
	 * a longer shake on a weapon this frequent is the thing that would start costing control rather
	 * than adding weight.
	 */
	public enum Grade {
		LIGHT(0.25D, 0.20D),
		MEDIUM(1.55D, 0.40D),
		HEAVY(2.95D, 0.70D),
		CATACLYSM(3.50D, 2.00D);

		private final double peakDegrees;
		private final double seconds;

		Grade(double peakDegrees, double seconds) {
			this.peakDegrees = peakDegrees;
			this.seconds = seconds;
		}

		public double peakDegrees() {
			return peakDegrees;
		}

		public double seconds() {
			return seconds;
		}
	}

	/** Concurrent impulses. Past this the weakest is replaced, so a barrage cannot stack forever. */
	public static final int MAX_CONCURRENT = 4;

	private ScreenShakePolicy() {
	}

	/**
	 * Envelope at {@code elapsed} seconds into an impulse of {@code duration}, in [0, 1].
	 *
	 * <p>Exponential decay, but scaled so it reaches exactly zero at the end rather than merely
	 * getting small. A bare {@code exp(-t/tau)} never reaches zero, and "never quite zero" on a
	 * camera offset is a view that stays very slightly wrong for the rest of the session.
	 */
	public static double envelope(double elapsed, double duration) {
		if (!(duration > 0.0D) || elapsed < 0.0D || elapsed >= duration) return 0.0D;
		double progress = elapsed / duration;
		double decay = Math.exp(-progress * 4.0D);
		double floor = Math.exp(-4.0D);
		return (decay - floor) / (1.0D - floor);
	}

	/**
	 * Distance falloff for a world-space event, in [0, 1]. Quadratic: an explosion forty blocks off
	 * should be felt, not merely detected.
	 */
	public static double falloff(double distance, double radius) {
        if (!(radius > 0.0D)) return 0.0D;
		if (distance <= 0.0D) return 1.0D;
		if (distance >= radius) return 0.0D;
		double remaining = 1.0D - distance / radius;
		return remaining * remaining;
	}

	/**
	 * Angular displacement in degrees at a point in an impulse.
	 *
	 * <p>{@code seed} and {@code axis} together pick a phase, so pitch, yaw and the two translation
	 * axes never move in lockstep - which would read as the whole world sliding rather than as the
	 * camera being knocked.
	 */
	public static double sample(double elapsed, double duration, double peakDegrees,
			double scale, long seed, int axis) {
		double envelope = envelope(elapsed, duration);
		if (envelope <= 0.0D) return 0.0D;
		double total = 0.0D;
		for (int index = 0; index < FREQUENCIES_HZ.length; index++) {
			double phase = phaseFor(seed, axis, index);
			total += Math.sin(Math.TAU * FREQUENCIES_HZ[index] * elapsed + phase);
		}
		return total / FREQUENCIES_HZ.length * envelope * peakDegrees * clampScale(scale);
	}

	/** Deterministic phase per (seed, axis, component), so a replayed impulse shakes identically. */
	public static double phaseFor(long seed, int axis, int component) {
		long mixed = seed * 0x9E3779B97F4A7C15L + axis * 0xC2B2AE3D27D4EB4FL
				+ component * 0x165667B19E3779F9L;
		mixed = (mixed ^ (mixed >>> 31)) * 0xBF58476D1CE4E5B9L;
		mixed ^= mixed >>> 29;
		return (mixed >>> 11) / (double) (1L << 53) * Math.TAU;
	}

	/**
	 * The user's shake setting, clamped. A scale rather than a toggle on purpose: a player who gets
	 * motion sick usually wants less of this, not none of it, and 0 still means off.
	 */
	public static double clampScale(double scale) {
		if (!Double.isFinite(scale)) return 1.0D;
		return Math.max(0.0D, Math.min(1.0D, scale));
	}

	/**
	 * Which of the concurrent slots a new impulse should take when they are all busy: the one
	 * contributing least right now, so the strongest shake in flight is never the one dropped.
	 */
	public static int weakestSlot(double[] currentAmplitudes) {
		int weakest = 0;
		for (int index = 1; index < currentAmplitudes.length; index++) {
			if (currentAmplitudes[index] < currentAmplitudes[weakest]) weakest = index;
		}
		return weakest;
	}
}
