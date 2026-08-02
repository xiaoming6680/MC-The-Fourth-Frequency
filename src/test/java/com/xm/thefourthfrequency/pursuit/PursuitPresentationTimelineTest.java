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
}
