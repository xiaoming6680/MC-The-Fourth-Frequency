package com.xm.thefourthfrequency.client_ui;

import org.junit.jupiter.api.Test;

import static com.xm.thefourthfrequency.client_ui.EndingScoreHandoff.State;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class EndingScoreHandoffTest {
	/** The carried track is still sounding. */
	private static final boolean PLAYING = true;
	/** It has run out - the music channel is empty. */
	private static final boolean FINISHED = false;

	@Test
	void nothingHoldsTheScoreUntilARunHasEnded() {
		assertEquals(State.OFF, EndingScoreHandoff.next(State.OFF, false, true, false, PLAYING));
		assertEquals(State.OFF, EndingScoreHandoff.next(State.OFF, false, false, false, PLAYING),
				"an ordinary title screen is the menu theme's");
		assertEquals(State.OFF, EndingScoreHandoff.next(State.OFF, false, false, true, PLAYING),
				"an ordinary load stays silent");
		assertEquals(State.OFF, EndingScoreHandoff.next(State.OFF, false, false, false, FINISHED),
				"a silent channel is not what arms a hold either");
		assertFalse(EndingScoreHandoff.holdsScore(State.OFF));
		assertFalse(EndingScoreHandoff.keepsSoundsAcrossDisconnect(State.OFF));
	}

	/**
	 * The success cleanup restores the resource packs, and that reload puts a loading overlay up
	 * while the player is still nowhere. Arming there would score the reload with ordinary play,
	 * which is the same fault this class prevents, only pointed the other way.
	 */
	@Test
	void theHoldWaitsForTheWorldToComeBack() {
		assertEquals(State.OFF, EndingScoreHandoff.next(State.OFF, true, false, true, PLAYING));
		assertEquals(State.OFF, EndingScoreHandoff.next(State.OFF, true, false, false, PLAYING));
		assertEquals(State.OFF, EndingScoreHandoff.next(State.OFF, true, true, true, PLAYING));
		assertEquals(State.ARMED, EndingScoreHandoff.next(State.OFF, true, true, false, PLAYING));
	}

	/**
	 * The whole exit, tick by tick: playing on after the ending, the saving screen, the title
	 * screen, and then joining something again.
	 */
	@Test
	void theScoreFollowsThePlayerOutAndIsHandedBackOnTheNextJoin() {
		State state = EndingScoreHandoff.next(State.OFF, true, true, false, PLAYING);
		assertEquals(State.ARMED, state);
		// Still playing. The hold is already in force, because the disconnect gives no warning.
		state = EndingScoreHandoff.next(state, false, true, false, PLAYING);
		assertEquals(State.ARMED, state);
		assertTrue(EndingScoreHandoff.keepsSoundsAcrossDisconnect(state),
				"the engine stops every sound inside the disconnect, so the hold has to be armed first");
		// "Saving world": no level any more, but this is the exit rather than the far side of it.
		state = EndingScoreHandoff.next(state, false, false, true, PLAYING);
		assertEquals(State.ARMED, state);
		// Arrived.
		state = EndingScoreHandoff.next(state, false, false, false, PLAYING);
		assertEquals(State.HOLDING, state);
		assertTrue(EndingScoreHandoff.holdsScore(state), "the menu theme must not take the title screen");
		assertFalse(EndingScoreHandoff.keepsSoundsAcrossDisconnect(state),
				"a teardown from the menu is not the exit this hold was armed for");
		state = EndingScoreHandoff.next(state, false, false, false, PLAYING);
		assertEquals(State.HOLDING, state, "sitting at the menu does not end the hold while it still plays");
		// Joining a world ends it, so the ordinary world-entry fade runs again.
		state = EndingScoreHandoff.next(state, false, false, true, PLAYING);
		assertEquals(State.OFF, state);
	}

	/**
	 * The hold covers one track, not the rest of the session.
	 *
	 * <p>Its job is to stop the menu theme cutting the track the player finished on. Once that track
	 * has played there is nothing left to carry, and staying held would leave the title screen on the
	 * ordinary-play playlist - which paces itself for a world underneath it and leaves minutes
	 * between songs, so the music appeared to stop after one and never come back.
	 */
	@Test
	void theHoldEndsWhenTheCarriedTrackDoes() {
		State state = EndingScoreHandoff.next(State.OFF, true, true, false, PLAYING);
		state = EndingScoreHandoff.next(state, false, false, false, PLAYING);
		assertEquals(State.HOLDING, state);

		state = EndingScoreHandoff.next(state, false, false, false, FINISHED);
		assertEquals(State.OFF, state, "the track it was carrying has finished");
		assertFalse(EndingScoreHandoff.holdsScore(state),
				"the title screen is an ordinary menu again, so the menu playlist takes it");

		// And it does not re-arm itself when the menu theme it just handed over to starts playing.
		state = EndingScoreHandoff.next(state, false, false, false, PLAYING);
		assertEquals(State.OFF, state);
	}

	/** One ending arms one exit. A later return to the menu is the menu theme's again. */
	@Test
	void aSpentEndingDoesNotArmASecondTime() {
		// The caller clears the pending flag on the tick it arms, so every tick after that sees false.
		State state = EndingScoreHandoff.next(State.OFF, true, true, false, PLAYING);
		assertEquals(State.ARMED, state);
		state = EndingScoreHandoff.next(state, false, false, false, PLAYING);
		assertEquals(State.HOLDING, state);
		state = EndingScoreHandoff.next(state, false, false, true, PLAYING);
		assertEquals(State.OFF, state);
		state = EndingScoreHandoff.next(state, false, true, false, PLAYING);
		assertEquals(State.OFF, state, "the new run must be scored normally");
		state = EndingScoreHandoff.next(state, false, false, false, PLAYING);
		assertEquals(State.OFF, state);
	}
}
