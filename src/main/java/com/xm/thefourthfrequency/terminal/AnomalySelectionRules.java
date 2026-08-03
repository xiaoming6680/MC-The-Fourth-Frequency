package com.xm.thefourthfrequency.terminal;

/** Minecraft-free bounded selection helpers. */
public final class AnomalySelectionRules {
	private AnomalySelectionRules() { }

	public static int doorCount(int candidates, long seed) {
		if (candidates < 2) return 0;
		return Math.min(candidates, 6);
	}

	public static boolean caveLike(boolean directSky, int skyLight, int enclosedDirections) {
		return !directSky && skyLight <= 4 && enclosedDirections >= 4;
	}

	/**
	 * Night as the story already counts it in StoryProgressService: full dark, excluding the sunset
	 * and sunrise ramps where the sky still does the lighting.
	 */
	public static boolean night(long dayTime) {
		long day = Math.floorMod(dayTime, 24_000L);
		return day >= 13_000L && day <= 23_000L;
	}
}
