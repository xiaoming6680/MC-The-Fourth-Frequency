package com.xm.thefourthfrequency.terminal;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The anomaly's whole read is in the pacing, so the pacing is checked here rather than by
 * watching torches in a world: the lights leave from the outside in, they come back the other
 * way and slower, and nothing is ever left extinguished once the sequence has run.
 */
class LightDropoutSequenceTest {
	private static final int PRODUCTION_MIN = 100;
	private static final int PRODUCTION_MAX = 400;
	private static final int ACCELERATED = 24;

	@Test
	void nothingGoesOutOnTheStartingFrame() {
		for (int duration : new int[]{12, ACCELERATED, PRODUCTION_MIN, PRODUCTION_MAX}) {
			assertEquals(0, LightDropoutSequence.extinguishedBy(0, 6, duration),
					"instant blackout at duration " + duration);
			assertTrue(LightDropoutSequence.extinguishedBy(1, 6, duration) > 0,
					"the sequence must start moving immediately after, at duration " + duration);
		}
	}

	@Test
	void everyLightIsOutBeforeTheWindowCloses() {
		for (int duration : new int[]{12, ACCELERATED, PRODUCTION_MIN, PRODUCTION_MAX}) {
			int window = LightDropoutSequence.extinguishWindow(duration);
			assertEquals(9, LightDropoutSequence.extinguishedBy(window, 9, duration),
					"lights left lit at the end of the window, duration " + duration);
			assertEquals(9, LightDropoutSequence.extinguishedBy(window + 50, 9, duration));
		}
	}

	@Test
	void extinguishingIsMonotonicAndNeverOvershoots() {
		int count = 7;
		int previous = 0;
		for (int tick = 0; tick <= PRODUCTION_MAX; tick++) {
			int out = LightDropoutSequence.extinguishedBy(tick, count, PRODUCTION_MAX);
			assertTrue(out >= previous, "a light came back on during the extinguish phase at " + tick);
			assertTrue(out <= count, "extinguished more lights than exist at " + tick);
			previous = out;
		}
		assertEquals(count, previous);
	}

	@Test
	void darkIsHeldBetweenTheTwoWindows() {
		for (int duration : new int[]{12, ACCELERATED, PRODUCTION_MIN, PRODUCTION_MAX}) {
			int start = LightDropoutSequence.restoreStartTick(duration);
			assertTrue(start >= LightDropoutSequence.extinguishWindow(duration),
					"lights started returning before they had all left, at duration " + duration);
			assertEquals(0, LightDropoutSequence.restoredBy(start, 5, duration),
					"restoration began early at duration " + duration);
		}
	}

	@Test
	void everythingIsBackByTheTimeTheAnomalyEnds() {
		for (int duration : new int[]{12, ACCELERATED, PRODUCTION_MIN, PRODUCTION_MAX}) {
			assertEquals(5, LightDropoutSequence.restoredBy(duration, 5, duration),
					"a light was still out when the anomaly ended, duration " + duration);
		}
	}

	@Test
	void comingBackIsSlowerThanLeaving() {
		for (int duration : new int[]{ACCELERATED, PRODUCTION_MIN, PRODUCTION_MAX}) {
			assertTrue(LightDropoutSequence.restoreWindow(duration)
					> LightDropoutSequence.extinguishWindow(duration),
					"the dark must lift more slowly than it arrived, at duration " + duration);
		}
	}

	@Test
	void restorationWalksBackFromTheNearestLight() {
		int count = 4;
		// Index 0 is the farthest and went out first, so it has to be the last thing to return.
		assertEquals(3, LightDropoutSequence.restoreIndex(0, count));
		assertEquals(2, LightDropoutSequence.restoreIndex(1, count));
		assertEquals(0, LightDropoutSequence.restoreIndex(3, count));
		boolean[] seen = new boolean[count];
		for (int step = 0; step < count; step++) seen[LightDropoutSequence.restoreIndex(step, count)] = true;
		for (int index = 0; index < count; index++)
			assertTrue(seen[index], "light " + index + " would never be restored");
	}

	@Test
	void singleLightAndEmptySetsStayInBounds() {
		assertEquals(0, LightDropoutSequence.extinguishedBy(5, 0, PRODUCTION_MIN));
		assertEquals(0, LightDropoutSequence.restoredBy(PRODUCTION_MIN, 0, PRODUCTION_MIN));
		assertEquals(0, LightDropoutSequence.restoreIndex(0, 1));
		assertEquals(0, LightDropoutSequence.restoreIndex(9, 1));
		assertEquals(1, LightDropoutSequence.extinguishedBy(1, 1, PRODUCTION_MAX));
		assertEquals(1, LightDropoutSequence.restoredBy(PRODUCTION_MAX, 1, PRODUCTION_MAX));
	}
}
