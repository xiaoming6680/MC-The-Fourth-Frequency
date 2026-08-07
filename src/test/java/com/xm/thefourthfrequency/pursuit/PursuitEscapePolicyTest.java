package com.xm.thefourthfrequency.pursuit;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class PursuitEscapePolicyTest {
	/** Walks the corrector in to contact range, which is what arms either escape route. */
	private static PursuitEscapePolicy.Counters afterContact() {
		return PursuitEscapePolicy.advance(PursuitEscapePolicy.Counters.empty(),
				PursuitEscapePolicy.CONTACT_BLOCKS, true);
	}

	@Test
	void sustainedDistanceCreatesAnEarlyEscape() {
		var counters = afterContact();
		for (int tick = 0; tick < PursuitEscapePolicy.DISTANCE_ESCAPE_TICKS; tick++) {
			counters = PursuitEscapePolicy.advance(counters,
					PursuitEscapePolicy.DISTANCE_ESCAPE_BLOCKS, true);
		}
		assertTrue(PursuitEscapePolicy.escaped(counters));
	}

	@Test
	void breakingLineOfSightAtUsefulDistanceCreatesAnEarlyEscape() {
		var counters = afterContact();
		for (int tick = 0; tick < PursuitEscapePolicy.HIDDEN_ESCAPE_TICKS; tick++) {
			counters = PursuitEscapePolicy.advance(counters,
					PursuitEscapePolicy.HIDDEN_ESCAPE_MIN_BLOCKS, false);
		}
		assertTrue(PursuitEscapePolicy.escaped(counters));
	}

	@Test
	void briefSeparationDoesNotBankPermanentProgress() {
		var counters = afterContact();
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

	/**
	 * The bug this guards: a player who has not been reached cannot have escaped.
	 *
	 * <p>The corrector spawns on a ring 25 to 42 blocks out, which satisfies both escape conditions
	 * on its own - forty-two blocks is inside the spawn ring, and "at least eighteen blocks away and
	 * cannot see me" describes practically any spawn behind cover. Standing perfectly still, a player
	 * was told they had escaped about eight seconds into a pursuit they had not begun.
	 */
	@Test
	void aPursuitCannotBeEscapedBeforeItHasCaughtUp() {
		// The distance route, at the far edge of the spawn ring, for four times as long as it needs.
		var counters = PursuitEscapePolicy.Counters.empty();
		for (int tick = 0; tick < PursuitEscapePolicy.DISTANCE_ESCAPE_TICKS * 4; tick++) {
			counters = PursuitEscapePolicy.advance(counters, 42.0D, false);
			assertFalse(PursuitEscapePolicy.escaped(counters),
					"escaped at tick " + tick + " without ever being reached");
		}
		assertFalse(counters.contacted());

		// The hidden route, just outside contact range and out of sight, likewise.
		counters = PursuitEscapePolicy.Counters.empty();
		for (int tick = 0; tick < PursuitEscapePolicy.HIDDEN_ESCAPE_TICKS * 4; tick++) {
			counters = PursuitEscapePolicy.advance(counters,
					PursuitEscapePolicy.CONTACT_BLOCKS + 0.5D, false);
			assertFalse(PursuitEscapePolicy.escaped(counters),
					"escaped at tick " + tick + " without ever being reached");
		}
		assertFalse(counters.contacted());
	}

	/**
	 * Line of sight alone is not contact.
	 *
	 * <p>In the open the corrector can see the player from the moment it spawns, so treating that as
	 * "it caught up with me" would put the whole hole straight back.
	 */
	@Test
	void beingSeenFromAcrossTheFieldIsNotBeingCaughtUpWith() {
		var counters = PursuitEscapePolicy.Counters.empty();
		for (int tick = 0; tick < 200; tick++) {
			counters = PursuitEscapePolicy.advance(counters, 30.0D, true);
		}
		assertFalse(counters.contacted());
		assertFalse(PursuitEscapePolicy.escaped(counters));
	}

	/** Once contact happens it stays happened, so a re-approach does not disarm the escape. */
	@Test
	void contactIsRememberedForTheRestOfThePursuit() {
		var counters = afterContact();
		assertTrue(counters.contacted());
		for (int tick = 0; tick < PursuitEscapePolicy.DISTANCE_ESCAPE_TICKS; tick++) {
			counters = PursuitEscapePolicy.advance(counters, 200.0D, false);
			assertTrue(counters.contacted());
		}
		assertTrue(PursuitEscapePolicy.escaped(counters),
				"a player who broke contact and ran should still be able to escape");
	}
}
