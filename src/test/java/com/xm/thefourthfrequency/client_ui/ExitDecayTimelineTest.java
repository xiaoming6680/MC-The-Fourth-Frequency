package com.xm.thefourthfrequency.client_ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The exit control's decay, checked against the one promise it could break.
 *
 * <p>This draws over a button a player may need to press, on a screen they opened to get out. The
 * safety notice caps flashing at 3 Hz, and nothing here is allowed near it.
 */
class ExitDecayTimelineTest {
	private static final float EPSILON = 0.0001F;

	@Test
	void everyRateStaysWellUnderTheFlashCeiling() {
		double rollHz = 1_000.0D / ExitDecayTimeline.ROLL_PERIOD_MILLIS;
		double grainHz = 1_000.0D / ExitDecayTimeline.GRAIN_PERIOD_MILLIS;
		assertTrue(rollHz < ExitDecayTimeline.MAX_FLASH_HZ, "roll bar at " + rollHz + " Hz");
		assertTrue(grainHz < ExitDecayTimeline.MAX_FLASH_HZ, "grain at " + grainHz + " Hz");
		// Not merely under it: an effect that sat just below the ceiling would still read as a
		// strobe over a control. These are an order of magnitude below.
		assertTrue(rollHz < 1.0D);
		assertTrue(grainHz < 1.0D);
		assertTrue(ExitDecayTimeline.ROLL_SWEEP_MILLIS < ExitDecayTimeline.ROLL_PERIOD_MILLIS,
				"the bar must finish crossing before the next pass starts");
	}

	@Test
	void theBarCrossesOnceAPeriodAndIsAbsentBetweenPasses() {
		assertEquals(0.0F, ExitDecayTimeline.rollProgress(0L), EPSILON);
		assertTrue(ExitDecayTimeline.rollProgress(ExitDecayTimeline.ROLL_SWEEP_MILLIS - 1L) > 0.99F);
		assertEquals(-1.0F, ExitDecayTimeline.rollProgress(ExitDecayTimeline.ROLL_SWEEP_MILLIS), EPSILON);
		assertEquals(-1.0F,
				ExitDecayTimeline.rollProgress(ExitDecayTimeline.ROLL_PERIOD_MILLIS - 1L), EPSILON);
		// And it repeats rather than running once.
		assertEquals(0.0F, ExitDecayTimeline.rollProgress(ExitDecayTimeline.ROLL_PERIOD_MILLIS), EPSILON);
	}

	/** The bar fades in and out; a pass that popped on at full strength is the cheap version. */
	@Test
	void theBarEntersAndLeavesRatherThanSwitchingOn() {
		assertEquals(0.0F, ExitDecayTimeline.rollStrength(0.0F), EPSILON);
		assertEquals(0.0F, ExitDecayTimeline.rollStrength(1.0F), EPSILON);
		assertEquals(1.0F, ExitDecayTimeline.rollStrength(0.5F), EPSILON);
		assertEquals(0.0F, ExitDecayTimeline.rollStrength(-1.0F), EPSILON, "no bar means no bar");

		float previous = -1.0F;
		for (float progress = 0.0F; progress <= 0.5F; progress += 0.02F) {
			float strength = ExitDecayTimeline.rollStrength(progress);
			assertTrue(strength >= previous - EPSILON, "the entry must not dip at " + progress);
			previous = strength;
		}
	}

	/** Grain never switches off and never saturates: it is a noise floor, not an event. */
	@Test
	void grainBreathesWithoutEverClearingOrSaturating() {
		for (long millis = 0L; millis < ExitDecayTimeline.GRAIN_PERIOD_MILLIS * 3L; millis += 37L) {
			float strength = ExitDecayTimeline.grainStrength(millis);
			assertTrue(strength > 0.05F, "grain cleared at " + millis + ": " + strength);
			assertTrue(strength < 0.75F, "grain saturated at " + millis + ": " + strength);
		}
		assertTrue(ExitDecayTimeline.scanlineStrength() > 0.0F);
		assertTrue(ExitDecayTimeline.scanlineStrength() <= 1.0F);
	}
}
