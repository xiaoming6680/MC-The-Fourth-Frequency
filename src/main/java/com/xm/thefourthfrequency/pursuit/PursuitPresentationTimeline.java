package com.xm.thefourthfrequency.pursuit;

/** Shared timing/math contract for the pre-pursuit presentation and proximity pulse. */
public final class PursuitPresentationTimeline {
	public static final int TERMINAL_WARNING_TICKS = 80;
	public static final int WARNING_TICKS = 80;
	public static final int FREEZE_TICKS = 40;
	public static final int VISUAL_WARNING_START_TICKS = TERMINAL_WARNING_TICKS;
	public static final int FREEZE_START_TICKS = VISUAL_WARNING_START_TICKS + WARNING_TICKS;
	public static final int PRELUDE_TICKS = FREEZE_START_TICKS + FREEZE_TICKS;
	private static final long MIN_FRAME_INTERVAL_NANOS = 16_666_667L;
	private static final long MAX_FRAME_INTERVAL_NANOS = 240_000_000L;

	private PursuitPresentationTimeline() {
	}

	public static Stage stageAt(int elapsedTicks) {
		if (elapsedTicks < VISUAL_WARNING_START_TICKS) return Stage.TERMINAL_WARNING;
		if (elapsedTicks < FREEZE_START_TICKS) return Stage.WARNING;
		if (elapsedTicks < PRELUDE_TICKS) return Stage.FROZEN;
		return Stage.BLACKOUT;
	}

	public static float warningProgress(int elapsedTicks) {
		return Math.clamp((elapsedTicks - VISUAL_WARNING_START_TICKS) / (float) WARNING_TICKS,
				0.0F, 1.0F);
	}

	/** Drops presentation-only rendering from about 60 FPS toward four FPS. */
	public static long frameIntervalNanos(int elapsedTicks) {
		float progress = warningProgress(elapsedTicks);
		double eased = progress * progress * progress;
		return MIN_FRAME_INTERVAL_NANOS
				+ Math.round((MAX_FRAME_INTERVAL_NANOS - MIN_FRAME_INTERVAL_NANOS) * eased);
	}

	/** Warden heartbeat cadence: roughly 3.3 Hz at contact and 0.55 Hz when distant. */
	public static int heartbeatIntervalTicks(double distance) {
		double normalized = Math.clamp((distance - 3.0D) / 45.0D, 0.0D, 1.0D);
		return 6 + (int) Math.round(normalized * 30.0D);
	}

	/**
	 * Picks the mosaic/colour-depth band for how close the corrector currently is, giving the
	 * player a spatial channel that does not depend on hearing the heartbeat.
	 *
	 * <p>Switching bands swaps the whole post-effect chain, so the thresholds are hysteretic:
	 * a band is entered at one distance and only left at a further one. Without that, a player
	 * hovering on a boundary would rebuild the chain every scan. Grades only ever step up
	 * immediately (something got close - say so at once) while stepping back down waits for the
	 * exit distance.</p>
	 */
	public static ProximityGrade proximityGrade(double distance, ProximityGrade current) {
		ProximityGrade observed = rawGrade(distance);
		ProximityGrade from = current == null ? ProximityGrade.DISTANT : current;
		if (observed.ordinal() > from.ordinal()) return observed;
		if (observed.ordinal() < from.ordinal() && distance > from.exitDistance()) return observed;
		return from;
	}

	private static ProximityGrade rawGrade(double distance) {
		if (distance <= ProximityGrade.CONTACT.enterDistance()) return ProximityGrade.CONTACT;
		if (distance <= ProximityGrade.CLOSE.enterDistance()) return ProximityGrade.CLOSE;
		if (distance <= ProximityGrade.NEAR.enterDistance()) return ProximityGrade.NEAR;
		return ProximityGrade.DISTANT;
	}

	public enum Stage {
		TERMINAL_WARNING,
		WARNING,
		FROZEN,
		BLACKOUT
	}

	/**
	 * Ordered from farthest to nearest; {@code ordinal()} is meaningful and is what
	 * {@link #proximityGrade} compares, so new entries must preserve that ordering.
	 */
	public enum ProximityGrade {
		DISTANT(Double.MAX_VALUE, Double.MAX_VALUE),
		NEAR(34.0D, 38.0D),
		CLOSE(18.0D, 22.0D),
		CONTACT(8.0D, 12.0D);

		private final double enterDistance;
		private final double exitDistance;

		ProximityGrade(double enterDistance, double exitDistance) {
			this.enterDistance = enterDistance;
			this.exitDistance = exitDistance;
		}

		public double enterDistance() {
			return enterDistance;
		}

		public double exitDistance() {
			return exitDistance;
		}
	}
}
