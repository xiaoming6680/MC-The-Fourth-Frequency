package com.xm.thefourthfrequency.ending;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Regression coverage for the fixed-roster world-interface difficulty contract. */
class EndBossDifficultyTest {
	private static final double EPSILON = 0.000_000_1D;

	@Test
	void frozenRosterSizeIsTheOnlyInputToMaximumVirtualHealth() {
		for (int rosterSize = 1; rosterSize <= WorldInterfacePolicy.MAX_ROSTER_SIZE; rosterSize++) {
			assertEquals(600.0D * rosterSize, WorldInterfacePolicy.maxHealth(rosterSize), EPSILON);
		}
		assertThrows(IllegalArgumentException.class, () -> WorldInterfacePolicy.maxHealth(0));
		assertThrows(IllegalArgumentException.class,
				() -> WorldInterfacePolicy.maxHealth(WorldInterfacePolicy.MAX_ROSTER_SIZE + 1));
	}

	@Test
	void theSameTickBoundaryHasTwoDistinctTerminalVerdicts() {
		assertEquals(WorldInterfacePolicy.TickVerdict.SUCCESS,
				WorldInterfacePolicy.resolveTick(11_999L, 0, true));
		assertEquals(WorldInterfacePolicy.TickVerdict.FAILURE,
				WorldInterfacePolicy.resolveTick(12_000L, 0, true));
		assertEquals("SUCCESS", WorldInterfacePolicy.TickVerdict.SUCCESS.name());
		assertEquals("FAILURE", WorldInterfacePolicy.TickVerdict.FAILURE.name());
	}
}
