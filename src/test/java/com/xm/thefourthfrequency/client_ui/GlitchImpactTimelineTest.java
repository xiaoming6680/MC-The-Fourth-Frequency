package com.xm.thefourthfrequency.client_ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The burst has to be four distinguishable beats rather than one ramp, and it has to be over on
 * a definite frame. Both are properties of the arithmetic, so both are checked here instead of
 * being judged by eye against a running client.
 */
class GlitchImpactTimelineTest {
	private static final int WIDTH = 640;
	private static final int HEIGHT = 360;

	@Test
	void burstIsBoundedAndSilentOutsideItself() {
		assertFalse(GlitchImpactTimeline.active(-1));
		assertTrue(GlitchImpactTimeline.active(0));
		assertTrue(GlitchImpactTimeline.active(GlitchImpactTimeline.IMPACT_TICKS - 1));
		assertFalse(GlitchImpactTimeline.active(GlitchImpactTimeline.IMPACT_TICKS));
		for (int tick : new int[]{-4, -1, GlitchImpactTimeline.IMPACT_TICKS,
				GlitchImpactTimeline.IMPACT_TICKS + 6}) {
			assertEquals(0, GlitchImpactTimeline.bleachAlpha(tick), "bleach at " + tick);
			assertEquals(0, GlitchImpactTimeline.dropoutAlpha(tick), "dropout at " + tick);
			assertEquals(0.0F, GlitchImpactTimeline.tearStrength(tick), "tear at " + tick);
			assertEquals(0, GlitchImpactTimeline.scanlineAlpha(tick), "scanlines at " + tick);
			assertEquals(0, GlitchImpactTimeline.collapseAlpha(tick), "collapse at " + tick);
			assertEquals(0.0F, GlitchImpactTimeline.debrisStrength(tick), "debris at " + tick);
			assertEquals(Integer.MIN_VALUE, GlitchImpactTimeline.rollBandTop(tick, HEIGHT),
					"roll band at " + tick);
		}
	}

	@Test
	void flashIsOneStruckFrameRatherThanAHeldWhiteScreen() {
		int peak = GlitchImpactTimeline.bleachAlpha(0);
		assertTrue(peak > 190, "the hit must actually blow out: " + peak);
		assertTrue(GlitchImpactTimeline.bleachAlpha(1) < peak / 2,
				"a squared falloff must halve well before the second tick");
		assertEquals(0, GlitchImpactTimeline.bleachAlpha(GlitchImpactTimeline.HIT_END_TICK));
	}

	@Test
	void pictureIsLostBetweenTheHitAndTheGhost() {
		assertEquals(0, GlitchImpactTimeline.dropoutAlpha(0), "the flash owns the first frames");
		int darkest = GlitchImpactTimeline.dropoutAlpha(5);
		assertTrue(darkest > 190, "carrier loss must read as dark: " + darkest);
		assertTrue(GlitchImpactTimeline.dropoutAlpha(GlitchImpactTimeline.LOSS_END_TICK) < darkest,
			"the dark has to be receding once the ghost beat starts");
		assertEquals(0, GlitchImpactTimeline.dropoutAlpha(GlitchImpactTimeline.GHOST_END_TICK));
	}

	@Test
	void signalIsStruckASecondTimeOnTheWayDown() {
		float beforeSecondHit = GlitchImpactTimeline.tearStrength(GlitchImpactTimeline.SECOND_HIT_TICK - 1);
		float atSecondHit = GlitchImpactTimeline.tearStrength(GlitchImpactTimeline.SECOND_HIT_TICK);
		assertTrue(atSecondHit > beforeSecondHit,
				"a burst that only ever decays is a fade: " + beforeSecondHit + " -> " + atSecondHit);
		assertTrue(atSecondHit < GlitchImpactTimeline.tearStrength(0),
				"the second strike must stay weaker than the first");
		assertEquals(0.0F, GlitchImpactTimeline.tearStrength(GlitchImpactTimeline.GHOST_END_TICK));
	}

	@Test
	void mediumShowsThroughOnlyOnceThePictureIsWeak() {
		assertEquals(0, GlitchImpactTimeline.scanlineAlpha(0));
		assertTrue(GlitchImpactTimeline.scanlineAlpha(GlitchImpactTimeline.LOSS_END_TICK)
				> GlitchImpactTimeline.scanlineAlpha(GlitchImpactTimeline.HIT_END_TICK));
		assertTrue(GlitchImpactTimeline.scanlineAlpha(GlitchImpactTimeline.IMPACT_TICKS - 1)
				< GlitchImpactTimeline.scanlineAlpha(GlitchImpactTimeline.GHOST_END_TICK),
				"the medium must be fading again across the closing beat");
	}

	@Test
	void chromaSeparationClosesUpAsTheTearHeals() {
		assertTrue(GlitchImpactTimeline.chromaOffset(0) > 6.0F);
		assertTrue(GlitchImpactTimeline.chromaOffset(6) < GlitchImpactTimeline.chromaOffset(0));
		assertEquals(0.0F, GlitchImpactTimeline.chromaOffset(GlitchImpactTimeline.GHOST_END_TICK));
	}

	@Test
	void mistrackedBandSweepsTheRecoveredPictureAndStaysOnScreen() {
		assertEquals(Integer.MIN_VALUE,
				GlitchImpactTimeline.rollBandTop(GlitchImpactTimeline.LOSS_END_TICK - 1, HEIGHT));
		int previous = Integer.MAX_VALUE;
		boolean wrapped = false;
		for (int tick = GlitchImpactTimeline.LOSS_END_TICK;
				tick < GlitchImpactTimeline.IMPACT_TICKS; tick++) {
			int top = GlitchImpactTimeline.rollBandTop(tick, HEIGHT);
			assertTrue(top > -HEIGHT && top <= HEIGHT, "band left the viewport at " + tick + ": " + top);
			if (top > previous) wrapped = true;
			previous = top;
		}
		assertTrue(wrapped, "the band must run more than a single pass up the screen");
	}

	@Test
	void burstClosesOnADefiniteFrame() {
		assertFalse(GlitchImpactTimeline.collapsing(GlitchImpactTimeline.GHOST_END_TICK - 1));
		assertTrue(GlitchImpactTimeline.collapsing(GlitchImpactTimeline.GHOST_END_TICK));
		int open = GlitchImpactTimeline.collapseHalfHeight(GlitchImpactTimeline.GHOST_END_TICK, HEIGHT);
		int closing = GlitchImpactTimeline.collapseHalfHeight(GlitchImpactTimeline.IMPACT_TICKS - 1, HEIGHT);
		assertTrue(closing < open, "the slot has to squeeze shut: " + open + " -> " + closing);
		assertEquals(0, GlitchImpactTimeline.collapseInset(GlitchImpactTimeline.GHOST_END_TICK, WIDTH),
				"the line starts at full width");
		assertTrue(GlitchImpactTimeline.collapseInset(GlitchImpactTimeline.IMPACT_TICKS - 1, WIDTH)
				> WIDTH / 8, "and is pulled well in by the last frame");
		int pinch = GlitchImpactTimeline.collapsePinchHeight(GlitchImpactTimeline.IMPACT_TICKS - 1, HEIGHT);
		assertTrue(pinch > GlitchImpactTimeline.collapsePinchHeight(
				GlitchImpactTimeline.GHOST_END_TICK, HEIGHT), "the dark has to close in, not open out");
		assertTrue(pinch * 2 < HEIGHT / 4,
				"the pinch must never grow into a second blackout: " + pinch + " of " + HEIGHT);
		assertFalse(GlitchImpactTimeline.collapsing(GlitchImpactTimeline.IMPACT_TICKS));
	}

	@Test
	void debrisOutlivesNeitherTheHitNorItself() {
		assertEquals(1.0F, GlitchImpactTimeline.debrisStrength(0));
		assertTrue(GlitchImpactTimeline.debrisStrength(1) < 1.0F);
		assertEquals(0.0F, GlitchImpactTimeline.debrisStrength(GlitchImpactTimeline.DEBRIS_TICKS));
		assertTrue(GlitchImpactTimeline.DEBRIS_TICKS <= GlitchImpactTimeline.HIT_END_TICK,
				"remnants must not survive the beat that is tearing them");
	}

	@Test
	void slicesTileTheViewportExactly() {
		assertEquals(0, GlitchImpactTimeline.sliceTop(0, HEIGHT));
		assertEquals(HEIGHT, GlitchImpactTimeline.sliceTop(GlitchImpactTimeline.SLICES, HEIGHT));
		int previous = 0;
		for (int slice = 1; slice <= GlitchImpactTimeline.SLICES; slice++) {
			int top = GlitchImpactTimeline.sliceTop(slice, HEIGHT);
			assertTrue(top >= previous, "slice tops must not go backwards at " + slice);
			previous = top;
		}
	}

	@Test
	void sliceDisplacementIsBoundedDeterministicAndFollowsStrength() {
		int strong = 0;
		int weak = 0;
		for (int slice = 0; slice < GlitchImpactTimeline.SLICES; slice++) {
			int full = GlitchImpactTimeline.sliceShift(slice, 0, WIDTH, 1.0F);
			assertEquals(full, GlitchImpactTimeline.sliceShift(slice, 0, WIDTH, 1.0F),
					"the same tick must draw the same tear twice");
			assertTrue(Math.abs(full) <= WIDTH, "slice dragged off any plausible screen: " + full);
			strong += Math.abs(full);
			weak += Math.abs(GlitchImpactTimeline.sliceShift(slice, 0, WIDTH, 0.1F));
		}
		assertTrue(weak < strong, "a weaker tear must displace less: " + weak + " vs " + strong);
	}

	@Test
	void lostSlicesScaleWithStrengthAndNeverTakeEverything() {
		int atFullStrength = 0;
		int atLowStrength = 0;
		for (int tick = 0; tick < GlitchImpactTimeline.IMPACT_TICKS; tick++) {
			int lostThisTick = 0;
			for (int slice = 0; slice < GlitchImpactTimeline.SLICES; slice++) {
				if (GlitchImpactTimeline.sliceLost(slice, tick, 1.0F)) {
					atFullStrength++;
					lostThisTick++;
				}
				if (GlitchImpactTimeline.sliceLost(slice, tick, 0.15F)) atLowStrength++;
			}
			assertTrue(lostThisTick < GlitchImpactTimeline.SLICES,
					"a fully blank frame is indistinguishable from a crash, at tick " + tick);
		}
		assertTrue(atLowStrength < atFullStrength,
				"dropouts must thin out with the tear: " + atLowStrength + " vs " + atFullStrength);
	}
}
