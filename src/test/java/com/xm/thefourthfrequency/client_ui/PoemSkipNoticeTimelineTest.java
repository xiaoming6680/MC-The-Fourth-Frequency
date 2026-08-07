package com.xm.thefourthfrequency.client_ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The exit is withheld, never taken away - so the release has to actually arrive, and the notice
 * that explains the wait has to actually leave.
 */
class PoemSkipNoticeTimelineTest {
	private static final float EPSILON = 0.001F;

	@Test
	void theExitIsHeldOnlyForTheOpeningSecondsAndThenReleasedForGood() {
		assertFalse(PoemSkipNoticeTimeline.allowsSkip(0L));
		assertFalse(PoemSkipNoticeTimeline.allowsSkip(
				PoemSkipNoticeTimeline.SKIP_LOCKED_MILLIS - 1L));
		assertTrue(PoemSkipNoticeTimeline.allowsSkip(PoemSkipNoticeTimeline.SKIP_LOCKED_MILLIS));
		// And stays released. A poem the player sat through must not become unskippable again.
		assertTrue(PoemSkipNoticeTimeline.allowsSkip(
				PoemSkipNoticeTimeline.SKIP_LOCKED_MILLIS * 100L));
	}

	/**
	 * Rounded up, because the number is a promise about a key that is still refused. Reading "0
	 * seconds" next to a key that does nothing is the exact confusion the notice exists to prevent.
	 */
	@Test
	void theCountdownNeverPromisesZeroWhileTheKeyIsStillRefused() {
		assertEquals(10, PoemSkipNoticeTimeline.secondsRemaining(0L));
		assertEquals(10, PoemSkipNoticeTimeline.secondsRemaining(1L));
		assertEquals(9, PoemSkipNoticeTimeline.secondsRemaining(1_000L));
		assertEquals(1, PoemSkipNoticeTimeline.secondsRemaining(9_001L));
		assertEquals(1, PoemSkipNoticeTimeline.secondsRemaining(
				PoemSkipNoticeTimeline.SKIP_LOCKED_MILLIS - 1L));
		assertEquals(0, PoemSkipNoticeTimeline.secondsRemaining(
				PoemSkipNoticeTimeline.SKIP_LOCKED_MILLIS));

		for (long age = 0L; age < PoemSkipNoticeTimeline.SKIP_LOCKED_MILLIS; age += 37L) {
			assertTrue(PoemSkipNoticeTimeline.secondsRemaining(age) >= 1,
					"a refused key must never be described as available, at " + age);
		}
	}

	@Test
	void theNoticeFadesInHoldsAndFadesFullyOut() {
		assertEquals(0.0F, PoemSkipNoticeTimeline.noticeAlpha(-1L), EPSILON);
		assertEquals(0.0F, PoemSkipNoticeTimeline.noticeAlpha(0L), EPSILON);
		assertEquals(PoemSkipNoticeTimeline.PEAK_ALPHA,
				PoemSkipNoticeTimeline.noticeAlpha(PoemSkipNoticeTimeline.FADE_IN_MILLIS), EPSILON);
		assertEquals(PoemSkipNoticeTimeline.PEAK_ALPHA,
				PoemSkipNoticeTimeline.noticeAlpha(PoemSkipNoticeTimeline.FADE_IN_MILLIS
						+ PoemSkipNoticeTimeline.HOLD_MILLIS - 1L), EPSILON);
		// Reaches exactly nothing, and stays there. An overlay stuck at a hair above zero is a
		// permanent smudge in the corner of every later frame.
		assertEquals(0.0F, PoemSkipNoticeTimeline.noticeAlpha(PoemSkipNoticeTimeline.TOTAL_MILLIS),
				EPSILON);
		assertEquals(0.0F, PoemSkipNoticeTimeline.noticeAlpha(
				PoemSkipNoticeTimeline.TOTAL_MILLIS * 10L), EPSILON);

		// Never overshoots, and both ramps are monotonic in the direction they are going.
		float previous = -1.0F;
		for (long age = 0L; age <= PoemSkipNoticeTimeline.FADE_IN_MILLIS; age += 10L) {
			float alpha = PoemSkipNoticeTimeline.noticeAlpha(age);
			assertTrue(alpha >= previous, "fade-in must not dip at " + age);
			assertTrue(alpha <= PoemSkipNoticeTimeline.PEAK_ALPHA + EPSILON);
			previous = alpha;
		}
		previous = Float.MAX_VALUE;
		for (long age = PoemSkipNoticeTimeline.FADE_IN_MILLIS + PoemSkipNoticeTimeline.HOLD_MILLIS;
				age <= PoemSkipNoticeTimeline.TOTAL_MILLIS; age += 10L) {
			float alpha = PoemSkipNoticeTimeline.noticeAlpha(age);
			assertTrue(alpha <= previous + EPSILON, "fade-out must not rise at " + age);
			assertTrue(alpha >= 0.0F);
			previous = alpha;
		}
	}

	/** The notice is an aside over a poem being read, so it must never reach full opacity. */
	@Test
	void theNoticeStaysTranslucent() {
		assertTrue(PoemSkipNoticeTimeline.PEAK_ALPHA > 0.4F);
		assertTrue(PoemSkipNoticeTimeline.PEAK_ALPHA < 1.0F);
	}
}
