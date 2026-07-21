package com.xm.thefourthfrequency.correction;

/** Pure world-progress-to-form mapping shared by runtime code and unit tests. */
public final class ReworkFormStage {
	public static final int MIN_STAGE = 1;
	public static final int MAX_STAGE = 5;

	private ReworkFormStage() { }

	public static int forDismantleCount(int dismantleCount) {
		return (int) Math.clamp((long) dismantleCount + 1L, MIN_STAGE, MAX_STAGE);
	}
}
