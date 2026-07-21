package com.xm.thefourthfrequency.world;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SurvivalMilestoneTest {
	@Test
	void milestoneBitsAreStableAndIndependent() {
		int mask = 0;
		for (SurvivalMilestone milestone : SurvivalMilestone.values()) {
			assertFalse(milestone.present(mask));
			mask |= milestone.mask();
			assertTrue(milestone.present(mask));
		}
		assertEquals(SurvivalMilestone.knownMask(), mask);
		assertEquals(0xFFF, mask);
	}
}
