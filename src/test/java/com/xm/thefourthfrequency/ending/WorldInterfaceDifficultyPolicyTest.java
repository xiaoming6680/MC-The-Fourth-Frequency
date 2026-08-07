package com.xm.thefourthfrequency.ending;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Regression coverage for the fixed-roster world-interface difficulty contract. */
class WorldInterfaceDifficultyPolicyTest {
	private static final double EPSILON = 0.000_000_1D;

	@Test
	void frozenRosterSizeIsTheOnlyInputToMaximumVirtualHealth() {
		double previous = 0.0D;
		for (int rosterSize = 1; rosterSize <= WorldInterfacePolicy.MAX_ROSTER_SIZE; rosterSize++) {
			double pool = WorldInterfacePolicy.maxHealth(rosterSize);
			// Deliberately a shape assertion, not a restatement of the formula: the pool has to keep
			// growing with the roster - nobody's presence is free - while every player past the first
			// costs strictly less than the one-player baseline, which is what stops a full table from
			// facing a wall the six-minute clock was never sized for.
			assertEquals(rosterSize == 1 ? 600.0D : previous + 300.0D, pool, EPSILON);
			assertTrue(pool > previous);
			assertTrue(pool < 600.0D * rosterSize || rosterSize == 1);
			previous = pool;
		}
		assertThrows(IllegalArgumentException.class, () -> WorldInterfacePolicy.maxHealth(0));
		assertThrows(IllegalArgumentException.class,
				() -> WorldInterfacePolicy.maxHealth(WorldInterfacePolicy.MAX_ROSTER_SIZE + 1));
	}

	@Test
	void theSameTickBoundaryHasTwoDistinctTerminalVerdicts() {
		long deadline = WorldInterfacePolicy.COLLAPSE_DURATION_TICKS;
		assertEquals(WorldInterfacePolicy.TickVerdict.SUCCESS,
				WorldInterfacePolicy.resolveTick(deadline - 1L, true));
		assertEquals(WorldInterfacePolicy.TickVerdict.FAILURE,
				WorldInterfacePolicy.resolveTick(deadline, true));
		assertEquals("SUCCESS", WorldInterfacePolicy.TickVerdict.SUCCESS.name());
		assertEquals("FAILURE", WorldInterfacePolicy.TickVerdict.FAILURE.name());
	}
}
