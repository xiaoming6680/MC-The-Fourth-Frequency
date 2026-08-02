package com.xm.thefourthfrequency.world;

public final class MineralSurveyPolicy {
	public static final int RANGE = 5;
	public static final int ARRIVAL_RADIUS = 1;
	public static final int CHANCE_PERCENT = 30;
	public static final int MANUAL_SCAN_FAILURE_PERCENT = 60;

	private MineralSurveyPolicy() {
	}

	public static boolean withinRange(int dx, int dy, int dz) {
		long distanceSquared = (long) dx * dx + (long) dy * dy + (long) dz * dz;
		return distanceSquared <= (long) RANGE * RANGE;
	}

	public static boolean shouldReveal(int roll) {
		return roll >= 0 && roll < CHANCE_PERCENT;
	}

	public static boolean manualScanFails(int roll) {
		return roll >= 0 && roll < MANUAL_SCAN_FAILURE_PERCENT;
	}

	public static boolean arrived(int dx, int dy, int dz) {
		long distanceSquared = (long) dx * dx + (long) dy * dy + (long) dz * dz;
		return distanceSquared <= (long) ARRIVAL_RADIUS * ARRIVAL_RADIUS;
	}
}
