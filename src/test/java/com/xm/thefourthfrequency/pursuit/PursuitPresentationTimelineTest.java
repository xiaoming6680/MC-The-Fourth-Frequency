package com.xm.thefourthfrequency.pursuit;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class PursuitPresentationTimelineTest {
	@Test
	void preludeProvidesFourSecondsForTerminalThenWarningAndTwoSecondFreeze() {
		assertEquals(PursuitPresentationTimeline.Stage.TERMINAL_WARNING,
				PursuitPresentationTimeline.stageAt(0));
		assertEquals(PursuitPresentationTimeline.Stage.TERMINAL_WARNING,
				PursuitPresentationTimeline.stageAt(
						PursuitPresentationTimeline.TERMINAL_WARNING_TICKS - 1));
		assertEquals(PursuitPresentationTimeline.Stage.WARNING,
				PursuitPresentationTimeline.stageAt(
						PursuitPresentationTimeline.VISUAL_WARNING_START_TICKS));
		assertEquals(PursuitPresentationTimeline.Stage.WARNING,
				PursuitPresentationTimeline.stageAt(
						PursuitPresentationTimeline.FREEZE_START_TICKS - 1));
		assertEquals(PursuitPresentationTimeline.Stage.FROZEN,
				PursuitPresentationTimeline.stageAt(
						PursuitPresentationTimeline.FREEZE_START_TICKS));
		assertEquals(80, PursuitPresentationTimeline.TERMINAL_WARNING_TICKS);
		assertEquals(80, PursuitPresentationTimeline.WARNING_TICKS);
		assertEquals(40, PursuitPresentationTimeline.FREEZE_TICKS);
		assertEquals(200, PursuitPresentationTimeline.PRELUDE_TICKS);
		assertEquals(PursuitPresentationTimeline.Stage.BLACKOUT,
				PursuitPresentationTimeline.stageAt(PursuitPresentationTimeline.PRELUDE_TICKS));
	}

	@Test
	void warningFrameCadenceOnlyGetsSlower() {
		long previous = 0L;
		for (int tick = PursuitPresentationTimeline.VISUAL_WARNING_START_TICKS;
				tick <= PursuitPresentationTimeline.FREEZE_START_TICKS; tick++) {
			long interval = PursuitPresentationTimeline.frameIntervalNanos(tick);
			assertTrue(interval >= previous);
			previous = interval;
		}
		assertTrue(PursuitPresentationTimeline.frameIntervalNanos(
				PursuitPresentationTimeline.FREEZE_START_TICKS) >= 200_000_000L);
	}

	@Test
	void heartbeatAcceleratesAsCorrectorApproaches() {
		assertTrue(PursuitPresentationTimeline.heartbeatIntervalTicks(3.0D)
				< PursuitPresentationTimeline.heartbeatIntervalTicks(24.0D));
		assertTrue(PursuitPresentationTimeline.heartbeatIntervalTicks(24.0D)
				< PursuitPresentationTimeline.heartbeatIntervalTicks(64.0D));
		assertEquals(6, PursuitPresentationTimeline.heartbeatIntervalTicks(0.0D));
		assertEquals(36, PursuitPresentationTimeline.heartbeatIntervalTicks(100.0D));
	}

	@Test
	void proximityGradeTightensAsTheCorrectorClosesIn() {
		var distant = PursuitPresentationTimeline.ProximityGrade.DISTANT;
		assertEquals(distant, PursuitPresentationTimeline.proximityGrade(80.0D, distant));
		assertEquals(PursuitPresentationTimeline.ProximityGrade.NEAR,
				PursuitPresentationTimeline.proximityGrade(34.0D, distant));
		assertEquals(PursuitPresentationTimeline.ProximityGrade.CLOSE,
				PursuitPresentationTimeline.proximityGrade(18.0D, distant));
		assertEquals(PursuitPresentationTimeline.ProximityGrade.CONTACT,
				PursuitPresentationTimeline.proximityGrade(8.0D, distant));
		// No corrector in range at all must read as the calmest band, never as contact.
		assertEquals(distant, PursuitPresentationTimeline.proximityGrade(Double.MAX_VALUE, distant));
	}

	@Test
	void proximityGradeStepsUpImmediatelyButNeedsTheExitDistanceToRelax() {
		var near = PursuitPresentationTimeline.ProximityGrade.NEAR;
		var close = PursuitPresentationTimeline.ProximityGrade.CLOSE;
		var contact = PursuitPresentationTimeline.ProximityGrade.CONTACT;

		// Closing in is always honoured on the spot.
		assertEquals(contact, PursuitPresentationTimeline.proximityGrade(5.0D, near));

		// Backing off inside the hysteresis band holds the current grade; only past the exit
		// distance does it relax. Without this a player sitting on a boundary would rebuild the
		// post-effect chain on every scan.
		assertEquals(close, PursuitPresentationTimeline.proximityGrade(19.0D, close));
		assertEquals(close, PursuitPresentationTimeline.proximityGrade(22.0D, close));
		assertEquals(near, PursuitPresentationTimeline.proximityGrade(22.5D, close));
		assertEquals(contact, PursuitPresentationTimeline.proximityGrade(12.0D, contact));
		assertEquals(close, PursuitPresentationTimeline.proximityGrade(12.5D, contact));

		// A grade may fall several bands at once when the corrector is suddenly far away.
		assertEquals(PursuitPresentationTimeline.ProximityGrade.DISTANT,
				PursuitPresentationTimeline.proximityGrade(90.0D, contact));
	}

	@Test
	void proximityGradeToleratesAMissingPreviousGrade() {
		assertEquals(PursuitPresentationTimeline.ProximityGrade.CONTACT,
				PursuitPresentationTimeline.proximityGrade(4.0D, null));
		assertEquals(PursuitPresentationTimeline.ProximityGrade.DISTANT,
				PursuitPresentationTimeline.proximityGrade(70.0D, null));
	}
}
