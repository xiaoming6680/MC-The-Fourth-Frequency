package com.xm.thefourthfrequency.client_ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The last thing the story does, and the two ways it could go wrong.
 *
 * <p>Handing the world back before the screen is black shows the player a frame of the poem
 * underneath the world loading in; never handing it back at all strands them on a black screen with
 * no way forward. Both are worse than the long motionless wait this replaces.
 */
class PoemOutroTimelineTest {
	private static final float EPSILON = 0.001F;

	@Test
	void theQuoteIsHeldBeforeAnythingHappensToIt() {
		assertEquals(0.0F, PoemOutroTimeline.blackoutAlpha(0L), EPSILON);
		assertEquals(0.0F, PoemOutroTimeline.blackoutAlpha(PoemOutroTimeline.HOLD_MILLIS), EPSILON,
				"the fade must not have started while the quote is still being held");
		assertFalse(PoemOutroTimeline.finished(PoemOutroTimeline.HOLD_MILLIS));
		assertTrue(PoemOutroTimeline.HOLD_MILLIS >= 1_500L,
				"too short to read the line the whole ending lands on");
	}

	@Test
	void theFadeReachesFullBlackBeforeTheWorldIsHandedBack() {
		long fadeDone = PoemOutroTimeline.HOLD_MILLIS + PoemOutroTimeline.FADE_MILLIS;
		assertEquals(1.0F, PoemOutroTimeline.blackoutAlpha(fadeDone), EPSILON);
		assertTrue(PoemOutroTimeline.fullyBlack(fadeDone));
		assertFalse(PoemOutroTimeline.finished(fadeDone),
				"the hand-back must happen behind black, not on the frame black is reached");
		assertTrue(PoemOutroTimeline.finished(PoemOutroTimeline.TOTAL_MILLIS));
		assertTrue(PoemOutroTimeline.fullyBlack(PoemOutroTimeline.TOTAL_MILLIS),
				"whatever else is true at the hand-back, the screen is black");
	}

	@Test
	void theFadeIsMonotonicAndNeverOvershoots() {
		float previous = -1.0F;
		for (long age = 0L; age <= PoemOutroTimeline.TOTAL_MILLIS + 2_000L; age += 25L) {
			float alpha = PoemOutroTimeline.blackoutAlpha(age);
			assertTrue(alpha >= previous - EPSILON, "the fade brightened again at " + age);
			assertTrue(alpha >= 0.0F && alpha <= 1.0F, "alpha out of range at " + age + ": " + alpha);
			previous = alpha;
		}
		assertEquals(1.0F, PoemOutroTimeline.blackoutAlpha(PoemOutroTimeline.TOTAL_MILLIS * 10L),
				EPSILON, "it must stay black rather than coming back up");
	}

	/** Smoothstepped: the player should not be able to name the frame the fade began on. */
	@Test
	void theFadeEasesInRatherThanStartingAtFullRate() {
		long start = PoemOutroTimeline.HOLD_MILLIS;
		float justAfter = PoemOutroTimeline.blackoutAlpha(start + PoemOutroTimeline.FADE_MILLIS / 10L);
		float linearAtSamePoint = 0.1F;
		assertTrue(justAfter < linearAtSamePoint,
				"the opening of the fade must be gentler than linear, was " + justAfter);
		float middle = PoemOutroTimeline.blackoutAlpha(start + PoemOutroTimeline.FADE_MILLIS / 2L);
		assertEquals(0.5F, middle, 0.05F, "the midpoint should still be halfway");
	}
}
