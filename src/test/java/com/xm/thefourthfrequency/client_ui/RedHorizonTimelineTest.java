package com.xm.thefourthfrequency.client_ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What this anomaly used to be - a constant - would pass every test that only checked its
 * bounds. So the checks here are about the properties that constant did not have: it arrives
 * over time, it never sits still while it is held, the horizon is worse than the sky above it,
 * and the world closes in after the colour rather than with it.
 */
class RedHorizonTimelineTest {
	private static final int DURATION = RedHorizonTimeline.CANONICAL_DURATION_TICKS;

	private static float horizonAt(int elapsed) {
		return RedHorizonTimeline.horizonStrength(elapsed, DURATION - elapsed, DURATION);
	}

	private static float fogAt(int elapsed) {
		return RedHorizonTimeline.fogTightness(elapsed, DURATION - elapsed, DURATION);
	}

	@Test
	void arrivesOverTimeInsteadOfOnOneFrame() {
		assertEquals(0.0F, horizonAt(0), 0.001F, "the anomaly must not be at full power on frame one");
		int enter = RedHorizonTimeline.enterTicks(DURATION);
		assertTrue(horizonAt(enter / 4) < 0.35F,
				"the first quarter of the arrival has to stay deniable: " + horizonAt(enter / 4));
		assertTrue(horizonAt(enter / 2) > horizonAt(enter / 4), "the arrival must be monotonic");
		assertTrue(horizonAt(enter) > 0.9F, "and must actually get there: " + horizonAt(enter));
	}

	@Test
	void neverExceedsFullStrengthAnywhere() {
		for (int elapsed = 0; elapsed <= DURATION; elapsed++) {
			float strength = horizonAt(elapsed);
			assertTrue(strength >= 0.0F && strength <= 1.0F, "out of range at " + elapsed + ": " + strength);
			float fog = fogAt(elapsed);
			assertTrue(fog >= 0.0F && fog <= 1.0F, "fog out of range at " + elapsed + ": " + fog);
		}
	}

	@Test
	void heldMinutesAreNeverOneFlatValue() {
		int enter = RedHorizonTimeline.enterTicks(DURATION);
		int fadeStart = DURATION - RedHorizonTimeline.fadeTicks(DURATION);
		float lowest = 1.0F;
		float highest = 0.0F;
		for (int elapsed = enter; elapsed < fadeStart; elapsed++) {
			float strength = horizonAt(elapsed);
			lowest = Math.min(lowest, strength);
			highest = Math.max(highest, strength);
		}
		assertTrue(highest - lowest > 0.02F,
				"the held stretch is still a constant: " + lowest + " to " + highest);
		assertTrue(lowest > 0.85F, "the modulation must stay a swell, not a pulse: " + lowest);
	}

	@Test
	void breathingFreezesRatherThanReleasingWhenTheAnomalyStartsToLeave() {
		int fade = RedHorizonTimeline.fadeTicks(DURATION);
		float atFadeStart = RedHorizonTimeline.breathing(DURATION - fade, fade, DURATION);
		for (int elapsed = DURATION - fade; elapsed <= DURATION; elapsed++) {
			assertEquals(atFadeStart, RedHorizonTimeline.breathing(elapsed, DURATION - elapsed, DURATION),
					0.0001F, "the swell must hold still, not restart, at " + elapsed);
		}
		assertTrue(RedHorizonTimeline.breathing(300, DURATION - 300, DURATION) < 1.0F,
				"but it must be breathing while it is held");
	}

	@Test
	void leavesSmoothlyAndCompletely() {
		int fade = RedHorizonTimeline.fadeTicks(DURATION);
		float previous = horizonAt(DURATION - fade);
		for (int elapsed = DURATION - fade; elapsed <= DURATION; elapsed++) {
			float strength = horizonAt(elapsed);
			assertTrue(strength <= previous + 0.0001F, "the fade went back up at " + elapsed);
			previous = strength;
		}
		assertEquals(0.0F, horizonAt(DURATION), 0.001F);
	}

	@Test
	void horizonIsWorseThanTheSkyDirectlyOverhead() {
		assertTrue(RedHorizonTimeline.skyDomeShare() > 0.0F && RedHorizonTimeline.skyDomeShare() < 1.0F);
		for (int elapsed : new int[]{40, 200, 600, 780}) {
			float horizon = horizonAt(elapsed);
			float dome = RedHorizonTimeline.skyDomeStrength(elapsed, DURATION - elapsed, DURATION);
			assertTrue(dome < horizon || horizon == 0.0F,
					"the dome must stay lighter than the horizon at " + elapsed);
		}
	}

	@Test
	void worldClosesInAfterTheColourRatherThanWithIt() {
		int enter = RedHorizonTimeline.enterTicks(DURATION);
		assertEquals(0.0F, fogAt(0), 0.001F);
		assertTrue(fogAt(enter) < horizonAt(enter),
				"the fog must still be arriving once the colour is there: "
						+ fogAt(enter) + " vs " + horizonAt(enter));
		assertTrue(fogAt(400) > 0.9F, "but it does have to get there: " + fogAt(400));
		assertEquals(0.0F, fogAt(DURATION), 0.001F);
	}

	@Test
	void acceleratedRunsStillGetAVisibleArrivalAndDeparture() {
		int shortDuration = 40;
		assertTrue(RedHorizonTimeline.enterTicks(shortDuration) >= 1);
		assertTrue(RedHorizonTimeline.enterTicks(shortDuration) < shortDuration / 2,
				"an accelerated run must still spend most of itself at strength");
		assertEquals(0.0F, RedHorizonTimeline.horizonStrength(0, shortDuration, shortDuration), 0.001F);
		int peak = shortDuration / 2;
		assertTrue(RedHorizonTimeline.horizonStrength(peak, shortDuration - peak, shortDuration) > 0.8F,
				"the client GameTest samples the middle and must find the anomaly at strength");
		assertEquals(0.0F, RedHorizonTimeline.horizonStrength(shortDuration, 0, shortDuration), 0.001F);
	}
}
