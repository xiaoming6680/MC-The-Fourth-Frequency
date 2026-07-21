package com.xm.thefourthfrequency.terminal;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class AnomalyIntensityTest {
	@Test
	void storyCeilingFollowsBindingAndStoryEvidence() {
		assertEquals(0, AnomalyIntensity.storyCeiling(false, 3, true, true, 1000, true));
		assertEquals(1, AnomalyIntensity.storyCeiling(true, 0, false, false, 0, false));
		assertEquals(2, AnomalyIntensity.storyCeiling(true, 1, false, false, 0, false));
		assertEquals(3, AnomalyIntensity.storyCeiling(true, 1, true, false, 0, false));
		assertEquals(4, AnomalyIntensity.storyCeiling(true, 1, true, true, 1, false));
		assertEquals(5, AnomalyIntensity.storyCeiling(true, 1, true, true, 750, false));
	}

	@Test
	void heatReachesOneHundredAfterFifteenOnlineMinutes() {
		assertEquals(0, AnomalyIntensity.heatPercent(0));
		assertEquals(50, AnomalyIntensity.heatPercent(AnomalyIntensity.HEAT_RAMP_TICKS / 2));
		assertEquals(100, AnomalyIntensity.heatPercent(AnomalyIntensity.HEAT_RAMP_TICKS));
		assertEquals(100, AnomalyIntensity.heatPercent(Long.MAX_VALUE));
	}

	@Test
	void intervalsTightenByTierAndHeatWithinApprovedBounds() {
		assertTrue(between(AnomalyIntensity.intervalTicks(1, 0, 0, true), 60, 90));
		assertTrue(between(AnomalyIntensity.intervalTicks(1, 0, 41, false), 180, 300));
		assertTrue(between(AnomalyIntensity.intervalTicks(3, 100, 9, false), 90, 90));
		assertTrue(between(AnomalyIntensity.intervalTicks(5, 0, 17, false), 45, 90));
	}

	@Test
	void strongCooldownsStayBounded() {
		assertTrue(between(AnomalyIntensity.strongCooldownTicks(3, 1), 12 * 60, 18 * 60));
		assertTrue(between(AnomalyIntensity.strongCooldownTicks(4, 2), 8 * 60, 12 * 60));
		assertTrue(between(AnomalyIntensity.strongCooldownTicks(5, 3), 5 * 60, 8 * 60));
	}

	private static boolean between(long ticks, int minimumSeconds, int maximumSeconds) {
		return ticks >= minimumSeconds * 20L && ticks <= maximumSeconds * 20L;
	}
}
