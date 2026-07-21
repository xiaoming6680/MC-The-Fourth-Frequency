package com.xm.thefourthfrequency.terminal;

/**
 * Small time-based linear transition used by the terminal display. The target may be changed while
 * the transition is running without introducing a discontinuity.
 */
public final class TuningTransition {
	private final long durationMillis;
	private double from;
	private double target;
	private long startedAtMillis;

	public TuningTransition(double initialValue, long durationMillis) {
		if (durationMillis <= 0L) throw new IllegalArgumentException("durationMillis must be positive");
		this.durationMillis = durationMillis;
		this.from = initialValue;
		this.target = initialValue;
	}

	public double valueAt(long nowMillis) {
		return lerp(from, target, progressAt(nowMillis));
	}

	public double progressAt(long nowMillis) {
		return Math.clamp((nowMillis - startedAtMillis) / (double) durationMillis, 0.0D, 1.0D);
	}

	public double target() {
		return target;
	}

	public boolean retarget(double newTarget, long nowMillis) {
		if (Double.compare(newTarget, target) == 0) return false;
		from = valueAt(nowMillis);
		target = newTarget;
		startedAtMillis = nowMillis;
		return true;
	}

	private static double lerp(double start, double end, double progress) {
		return start + (end - start) * progress;
	}
}
