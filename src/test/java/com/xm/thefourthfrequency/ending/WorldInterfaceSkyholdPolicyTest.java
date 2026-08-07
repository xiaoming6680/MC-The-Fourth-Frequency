package com.xm.thefourthfrequency.ending;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The forty-percent promise, as arithmetic.
 *
 * <p>Everything else about the climb is taste. This is not: a table that cannot reach the boss is a
 * table that is not playing, and the one number that decides how much of the encounter that is
 * happens to be a pure function of two constants. Measured by counting ticks rather than by dividing
 * the constants, so a change to the shape of the window - a second climb per cycle, a longer transit
 * - is caught as well as a change to its length.
 */
final class WorldInterfaceSkyholdPolicyTest {
	@Test
	void theInterfaceIsOutOfReachForUnderFortyPercentOfEveryPhase() {
		for (WorldInterfaceStage stage : WorldInterfaceStage.values()) {
			if (!stage.isCombat()) continue;
			int aloft = 0;
			int ceiling = 0;
			// A whole collapse timer's worth, which is the longest a phase can possibly run.
			for (long tick = 0L; tick < WorldInterfacePolicy.COLLAPSE_DURATION_TICKS; tick++) {
				double fraction = WorldInterfaceSkyholdPolicy.altitudeFraction(stage, tick);
				if (fraction > 0.0D) aloft++;
				if (fraction >= 1.0D) ceiling++;
			}
			double duty = aloft / (double) WorldInterfacePolicy.COLLAPSE_DURATION_TICKS;
			assertTrue(duty < WorldInterfaceSkyholdPolicy.MAX_DUTY_CYCLE,
					stage + ": the interface is aloft " + duty * 100.0D + "% of the fight");
			assertTrue(ceiling <= aloft, stage + ": the ceiling cannot outlast the window");
			if (stage == WorldInterfaceStage.PHASE_1) {
				assertEquals(0, aloft, "the first body never leaves melee range");
			} else {
				assertTrue(ceiling > 0, stage + ": the climb never actually reaches a ceiling");
			}
		}
	}

	/** The first body is fought on the ground, start to finish. */
	@Test
	void onlyTheSecondAndThirdBodiesFly() {
		assertTrue(!WorldInterfaceSkyholdPolicy.applies(WorldInterfaceStage.PHASE_1));
		assertTrue(WorldInterfaceSkyholdPolicy.applies(WorldInterfaceStage.PHASE_2));
		assertTrue(WorldInterfaceSkyholdPolicy.applies(WorldInterfaceStage.PHASE_3));
		for (WorldInterfaceStage stage : WorldInterfaceStage.values()) {
			if (stage.isCombat()) continue;
			assertTrue(!WorldInterfaceSkyholdPolicy.applies(stage),
					stage + " is not a phase anything is allowed to fly during");
		}
	}

	/**
	 * A phase begins, and a morph hands over, with the body on its station.
	 *
	 * <p>Both of those land on active tick zero or close to it, and arriving already halfway up would
	 * mean a form change nobody could reach the new body through.
	 */
	@Test
	void aPhaseOpensOnTheGround() {
		for (long tick = 0L; tick < 100L; tick++) {
			assertEquals(0.0D, WorldInterfaceSkyholdPolicy.altitudeFraction(
					WorldInterfaceStage.PHASE_3, tick), 1.0E-9D, "tick " + tick);
		}
	}

	/** The climb and the descent are continuous: no frame teleports the body. */
	@Test
	void theClimbAndDescentAreContinuousAndReturnToZero() {
		double previous = 0.0D;
		for (long tick = 0L; tick < WorldInterfaceSkyholdPolicy.PERIOD_TICKS * 3L; tick++) {
			double fraction = WorldInterfaceSkyholdPolicy.altitudeFraction(
					WorldInterfaceStage.PHASE_2, tick);
			assertTrue(fraction >= 0.0D && fraction <= 1.0D, "tick " + tick + ": " + fraction);
			double step = Math.abs(fraction - previous) * WorldInterfaceSkyholdPolicy.CEILING_LIFT;
			assertTrue(step < 1.5D,
					"tick " + tick + ": the body moved " + step + " blocks in one tick");
			previous = fraction;
		}
		assertEquals(0.0D, WorldInterfaceSkyholdPolicy.altitudeFraction(
				WorldInterfaceStage.PHASE_2, WorldInterfaceSkyholdPolicy.PERIOD_TICKS * 3L), 1.0E-9D,
				"the cycle must end back on station");
	}

	/** Exactly one ascent cue per cycle, and it lands on the first tick of the climb. */
	@Test
	void theAscentCueFiresOncePerCycle() {
		int cues = 0;
		for (long tick = 0L; tick < WorldInterfaceSkyholdPolicy.PERIOD_TICKS * 4L; tick++) {
			if (!WorldInterfaceSkyholdPolicy.isAscentTick(WorldInterfaceStage.PHASE_2, tick)) continue;
			cues++;
			assertEquals(0.0D, WorldInterfaceSkyholdPolicy.altitudeFraction(
					WorldInterfaceStage.PHASE_2, tick), 1.0E-9D,
					"the cue has to announce the climb, not report it");
			assertTrue(WorldInterfaceSkyholdPolicy.altitudeFraction(
					WorldInterfaceStage.PHASE_2, tick + 1L) > 0.0D);
		}
		assertEquals(4, cues);
		for (long tick = 0L; tick < WorldInterfaceSkyholdPolicy.PERIOD_TICKS * 2L; tick++) {
			assertTrue(!WorldInterfaceSkyholdPolicy.isAscentTick(WorldInterfaceStage.PHASE_1, tick),
					"the first body must never be told to climb");
		}
	}
}
