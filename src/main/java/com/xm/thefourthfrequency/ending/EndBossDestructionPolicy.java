package com.xm.thefourthfrequency.ending;

/** Minecraft-free budget policy shared by production code and pure tests. */
public final class EndBossDestructionPolicy {
	public static final int WARNING_TICKS = 40;
	public static final int MAX_PERMANENT_EDITS = 768;
	public static final int MAX_EDITS_PER_TICK = 8;

	private EndBossDestructionPolicy() {
	}

	public static int remainingBudget(int alreadyEdited) {
		return Math.max(0, MAX_PERMANENT_EDITS - Math.max(0, alreadyEdited));
	}

	public static int tickBudget(int alreadyEdited) {
		return Math.min(MAX_EDITS_PER_TICK, remainingBudget(alreadyEdited));
	}
}
