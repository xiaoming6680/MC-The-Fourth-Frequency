package com.xm.thefourthfrequency.pursuit;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class PursuitEscapePolicyTest {
	@Test
	void sustainedDistanceCreatesAnEarlyEscape() {
		var counters = PursuitEscapePolicy.Counters.empty();
		for (int tick = 0; tick < PursuitEscapePolicy.DISTANCE_ESCAPE_TICKS; tick++) {
			counters = PursuitEscapePolicy.advance(counters,
					PursuitEscapePolicy.DISTANCE_ESCAPE_BLOCKS, true);
		}
		assertTrue(PursuitEscapePolicy.escaped(counters));
	}

	@Test
	void breakingLineOfSightAtUsefulDistanceCreatesAnEarlyEscape() {
		var counters = PursuitEscapePolicy.Counters.empty();
		for (int tick = 0; tick < PursuitEscapePolicy.HIDDEN_ESCAPE_TICKS; tick++) {
			counters = PursuitEscapePolicy.advance(counters,
					PursuitEscapePolicy.HIDDEN_ESCAPE_MIN_BLOCKS, false);
		}
		assertTrue(PursuitEscapePolicy.escaped(counters));
	}

	@Test
	void briefSeparationDoesNotBankPermanentProgress() {
		var counters = PursuitEscapePolicy.Counters.empty();
		for (int tick = 0; tick < 30; tick++) {
			counters = PursuitEscapePolicy.advance(counters, 64.0D, false);
		}
		for (int tick = 0; tick < 15; tick++) {
			counters = PursuitEscapePolicy.advance(counters, 4.0D, true);
		}
		assertFalse(PursuitEscapePolicy.escaped(counters));
		assertTrue(counters.distanceTicks() < 30);
		assertTrue(counters.hiddenTicks() < 30);
	}
}
