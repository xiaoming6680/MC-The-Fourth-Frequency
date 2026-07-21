package com.xm.thefourthfrequency.terminal;

/** Minecraft-free menu stage mapping for unit tests and network projection. */
public final class MenuErosionRules {
	private MenuErosionRules() { }

	public static int stageFor(int effectiveCeiling, boolean restored) {
		if (restored) return 4;
		int ceiling = Math.clamp(effectiveCeiling, 1, 5);
		return ceiling >= 5 ? 3 : ceiling >= 3 ? 2 : 1;
	}
}
