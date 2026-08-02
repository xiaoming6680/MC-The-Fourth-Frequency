package com.xm.thefourthfrequency.world;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class MineralSurveyPolicyTest {
	@Test
	void surveyRangeUsesARealFiveBlockSphere() {
		assertTrue(MineralSurveyPolicy.withinRange(5, 0, 0));
		assertTrue(MineralSurveyPolicy.withinRange(3, 4, 0));
		assertFalse(MineralSurveyPolicy.withinRange(5, 1, 0));
		assertFalse(MineralSurveyPolicy.withinRange(6, 0, 0));
	}

	@Test
	void revealChanceIsExactlyThirtyPercentOfAOneHundredPointRoll() {
		assertTrue(MineralSurveyPolicy.shouldReveal(0));
		assertTrue(MineralSurveyPolicy.shouldReveal(29));
		assertFalse(MineralSurveyPolicy.shouldReveal(30));
		assertFalse(MineralSurveyPolicy.shouldReveal(99));
		assertFalse(MineralSurveyPolicy.shouldReveal(-1));
		assertFalse(MineralSurveyPolicy.shouldReveal(100));
	}

	@Test
	void manualProbeFailureChanceIsExactlySixtyPercent() {
		assertTrue(MineralSurveyPolicy.manualScanFails(0));
		assertTrue(MineralSurveyPolicy.manualScanFails(59));
		assertFalse(MineralSurveyPolicy.manualScanFails(60));
		assertFalse(MineralSurveyPolicy.manualScanFails(99));
		assertFalse(MineralSurveyPolicy.manualScanFails(-1));
		assertFalse(MineralSurveyPolicy.manualScanFails(100));
	}

	@Test
	void mineralArrivalUsesARealOneBlockSphere() {
		assertTrue(MineralSurveyPolicy.arrived(1, 0, 0));
		assertTrue(MineralSurveyPolicy.arrived(0, -1, 0));
		assertFalse(MineralSurveyPolicy.arrived(1, 1, 0));
		assertFalse(MineralSurveyPolicy.arrived(0, 0, 2));
	}
}
