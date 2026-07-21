package com.xm.thefourthfrequency.world;

public enum SurvivalMilestone {
	HOME(0),
	IRON(1),
	PREPARED_NETHER(2),
	ENTERED_NETHER(3),
	RETURNED_NETHER(4),
	CRAFTED_EYE(5),
	THREW_EYE(6),
	FOUND_STRONGHOLD(7),
	MINED_LOGS(8),
	COLLECTED_BLAZE_RODS(9),
	ENTERED_END(10),
	DEFEATED_BOSS(11);

	private final int bit;

	SurvivalMilestone(int bit) {
		this.bit = bit;
	}

	public int mask() {
		return 1 << bit;
	}

	public boolean present(int milestones) {
		return (milestones & mask()) != 0;
	}

	public static int knownMask() {
		int mask = 0;
		for (SurvivalMilestone milestone : values()) mask |= milestone.mask();
		return mask;
	}
}
