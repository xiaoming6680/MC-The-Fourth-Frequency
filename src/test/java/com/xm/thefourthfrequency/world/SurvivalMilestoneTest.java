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
		assertEquals(0x1FFF, mask);
	}

	/**
	 * The bit indices are the persisted format, so they are append-only.
	 *
	 * <p>{@code FOUND_FORTRESS} reads last despite belonging next to {@code ENTERED_NETHER} in the
	 * story, and that is the reason: renumbering to put it in reading order would re-point every mask
	 * already saved, handing existing players a different set of completed milestones than the one
	 * they earned. Pinned by index rather than by ordinal so a future insertion fails here.
	 */
	@Test
	void milestoneBitIndicesAreAppendOnly() {
		assertEquals(1 << 0, SurvivalMilestone.HOME.mask());
		assertEquals(1 << 3, SurvivalMilestone.ENTERED_NETHER.mask());
		assertEquals(1 << 7, SurvivalMilestone.FOUND_STRONGHOLD.mask());
		assertEquals(1 << 9, SurvivalMilestone.COLLECTED_BLAZE_RODS.mask());
		assertEquals(1 << 11, SurvivalMilestone.DEFEATED_BOSS.mask());
		assertEquals(1 << 12, SurvivalMilestone.FOUND_FORTRESS.mask());
	}
}
