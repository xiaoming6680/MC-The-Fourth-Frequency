package com.xm.thefourthfrequency.terminal;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class TerminalMotionTest {
	@Test
	void progressClampsAtBothEndsAndTreatsTheFutureAsNotStarted() {
		assertEquals(0.0D, TerminalMotion.progress(1_000L, 1_000L, 160L), 1e-9D);
		assertEquals(0.5D, TerminalMotion.progress(1_000L, 1_080L, 160L), 1e-9D);
		assertEquals(1.0D, TerminalMotion.progress(1_000L, 1_160L, 160L), 1e-9D);
		assertEquals(1.0D, TerminalMotion.progress(1_000L, 9_999L, 160L), 1e-9D);
		// A clock that reads earlier than the start instant must not produce negative progress.
		assertEquals(0.0D, TerminalMotion.progress(1_000L, 995L, 160L), 1e-9D);
	}

	@Test
	void zeroOrNegativeDurationsAreRejectedRatherThanDividedBy() {
		assertThrows(IllegalArgumentException.class, () -> TerminalMotion.progress(0L, 1L, 0L));
		assertThrows(IllegalArgumentException.class, () -> TerminalMotion.elapsedProgress(1L, -5L));
		assertThrows(IllegalArgumentException.class, () -> TerminalMotion.catchUp(0.0D, 1.0D, 16L, 0.0D));
		assertThrows(IllegalArgumentException.class, () -> TerminalMotion.typedCharacters(4, 10L, 0L));
		assertThrows(IllegalArgumentException.class, () -> TerminalMotion.breathe(0.0D, 0.0D));
	}

	/**
	 * The property the whole render-driven animation layer rests on: how the elapsed time is chopped
	 * into frames must not change where the value lands. Without it, a 144 Hz machine and a 30 Hz
	 * machine disagree on how fast the terminal scrolls.
	 */
	@Test
	void catchUpLandsOnTheSameValueRegardlessOfHowTheFramesAreSplit() {
		double single = TerminalMotion.catchUp(0.0D, 100.0D, 100L, 70.0D);

		double twoFrames = 0.0D;
		for (int frame = 0; frame < 2; frame++) twoFrames = TerminalMotion.catchUp(twoFrames, 100.0D, 50L, 70.0D);

		double tenFrames = 0.0D;
		for (int frame = 0; frame < 10; frame++) tenFrames = TerminalMotion.catchUp(tenFrames, 100.0D, 10L, 70.0D);

		assertEquals(single, twoFrames, 1e-9D);
		assertEquals(single, tenFrames, 1e-9D);
		// And it actually moved most of the way, so the test is not passing on three identical zeros.
		assertTrue(single > 70.0D && single < 100.0D, "expected real movement, got " + single);
	}

	@Test
	void catchUpHoldsStillWithNoElapsedTimeAndNeverOvershoots() {
		assertEquals(5.0D, TerminalMotion.catchUp(5.0D, 90.0D, 0L, 70.0D), 1e-9D);
		assertEquals(5.0D, TerminalMotion.catchUp(5.0D, 5.0D, 100L, 70.0D), 1e-9D);
		double approached = TerminalMotion.catchUp(0.0D, 10.0D, 100L, 70.0D);
		assertTrue(approached < 10.0D, "an exponential follower must never reach or pass its target");
		double descending = TerminalMotion.catchUp(10.0D, 0.0D, 100L, 70.0D);
		assertTrue(descending > 0.0D && descending < 10.0D);
	}

	/**
	 * A frame that carried the whole time the window spent in the background would otherwise snap
	 * every follower to its target, which is exactly the jump the followers exist to remove.
	 */
	@Test
	void catchUpClampsAnOversizedFrameDeltaToTheCeiling() {
		double atCeiling = TerminalMotion.catchUp(0.0D, 100.0D, TerminalMotion.MAX_FRAME_DELTA_MILLIS, 70.0D);
		double wayPast = TerminalMotion.catchUp(0.0D, 100.0D, 100_000L, 70.0D);
		assertEquals(atCeiling, wayPast, 1e-9D);
		assertTrue(wayPast < 100.0D, "clamping must still leave the target unreached");
	}

	@Test
	void easingCurvesPinTheirEndpointsAndMidpoint() {
		assertEquals(0.0D, TerminalMotion.linear(0.0D), 1e-9D);
		assertEquals(1.0D, TerminalMotion.linear(1.0D), 1e-9D);
		assertEquals(0.0D, TerminalMotion.linear(-3.0D), 1e-9D);
		assertEquals(1.0D, TerminalMotion.linear(7.0D), 1e-9D);

		assertEquals(0.0D, TerminalMotion.easeOutQuad(0.0D), 1e-9D);
		assertEquals(1.0D, TerminalMotion.easeOutQuad(1.0D), 1e-9D);
		assertTrue(TerminalMotion.easeOutQuad(0.5D) > 0.5D, "ease-out must lead the linear ramp");
	}

	@Test
	void colourInterpolationHitsBothEndpointsAndCarriesAlphaIndependently() {
		int from = 0x00102030;
		int to = 0xFF405060;
		assertEquals(from, TerminalMotion.lerpColor(from, to, 0.0D));
		assertEquals(to, TerminalMotion.lerpColor(from, to, 1.0D));
		assertEquals(from, TerminalMotion.lerpColor(from, to, -2.0D));
		assertEquals(to, TerminalMotion.lerpColor(from, to, 5.0D));

		int half = TerminalMotion.lerpColor(0x00000000, 0xFFFFFFFF, 0.5D);
		assertEquals(0x80, half >>> 24);
		assertEquals(0x80, half >>> 16 & 0xFF);
		assertEquals(0x80, half >>> 8 & 0xFF);
		assertEquals(0x80, half & 0xFF);

		// Alpha must move on its own: fading a colour out may not drag its hue toward black.
		int fadedOnly = TerminalMotion.lerpColor(0xFF3399CC, 0x003399CC, 0.5D);
		assertEquals(0x33, fadedOnly >>> 16 & 0xFF);
		assertEquals(0x99, fadedOnly >>> 8 & 0xFF);
		assertEquals(0xCC, fadedOnly & 0xFF);
	}


	@Test
	void typewriterIsMonotonicAndClampedAtBothEnds() {
		assertEquals(0, TerminalMotion.typedCharacters(10, 0L, 26L));
		assertEquals(0, TerminalMotion.typedCharacters(10, -50L, 26L));
		assertEquals(0, TerminalMotion.typedCharacters(0, 5_000L, 26L));
		assertEquals(1, TerminalMotion.typedCharacters(10, 26L, 26L));
		assertEquals(10, TerminalMotion.typedCharacters(10, 10_000L, 26L));

		int previous = 0;
		for (long elapsed = 0L; elapsed <= 400L; elapsed += 7L) {
			int typed = TerminalMotion.typedCharacters(12, elapsed, 26L);
			assertTrue(typed >= previous, "typewriter went backwards at " + elapsed);
			assertTrue(typed <= 12);
			previous = typed;
		}
	}

	/**
	 * The pulse is a continuous curve, not a two-state blink dressed up as one. Bounding the
	 * per-tick step is the testable form of that claim: a square wave would jump the full range in a
	 * single tick and fail here, which is what the flicker rules are actually guarding against.
	 */
	@Test
	void breathePulseStaysInRangeAndNeverJumpsBetweenTicks() {
		double period = 40.0D;
		double previous = TerminalMotion.breathe(0.0D, period);
		assertEquals(0.0D, previous, 1e-9D);
		for (int tick = 1; tick <= 1_200; tick++) {
			double value = TerminalMotion.breathe(tick, period);
			assertTrue(value >= 0.0D && value <= 1.0D, "out of range at tick " + tick + ": " + value);
			assertTrue(Math.abs(value - previous) < 0.2D,
					"pulse jumped " + Math.abs(value - previous) + " at tick " + tick);
			previous = value;
		}
		// One full cycle every 40 ticks is 0.5 Hz, comfortably under the 3 Hz ceiling.
		assertEquals(1.0D, TerminalMotion.breathe(period / 2.0D, period), 1e-9D);
		assertEquals(0.0D, TerminalMotion.breathe(period, period), 1e-9D);
	}
}
