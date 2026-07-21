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
}
