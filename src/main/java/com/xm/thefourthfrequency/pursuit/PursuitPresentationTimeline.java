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

	public enum Stage {
		TERMINAL_WARNING,
		WARNING,
		FROZEN,
		BLACKOUT
	}
}
