package com.xm.thefourthfrequency.terminal;

import com.xm.thefourthfrequency.pursuit.PursuitActivityProof;
import com.xm.thefourthfrequency.world.SurvivalMilestone;
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
	void progressionCeilingUsesBroadRoutesAndMainlineMilestones() {
		assertEquals(0, AnomalyIntensity.progressionCeiling(false, 1, 0, 3,
				PursuitActivityProof.MINING.mask(), 0L, true));
		assertEquals(1, AnomalyIntensity.progressionCeiling(true, 0, 0, 0, 0, 0L, false));
		assertEquals(2, AnomalyIntensity.progressionCeiling(true, 0, 0, 0,
				PursuitActivityProof.EXPLORATION.mask(), 0L, false));
		assertEquals(3, AnomalyIntensity.progressionCeiling(true, 0,
				SurvivalMilestone.IRON.mask(), 0, 0, 0L, false));
		assertEquals(4, AnomalyIntensity.progressionCeiling(true, 0,
				SurvivalMilestone.RETURNED_NETHER.mask() | SurvivalMilestone.COLLECTED_BLAZE_RODS.mask(),
				0, 0, 0L, false));
		assertEquals(5, AnomalyIntensity.progressionCeiling(true, 0, 0, 1, 0, 0L, false));
	}

	@Test
	void stageCatchesUpOneStepOnlyAfterExposureAndTwoSuccesses() {
		assertEquals(1, AnomalyIntensity.progressedStage(0, 5, 0L, 0));
		assertEquals(1, AnomalyIntensity.progressedStage(1, 5,
				AnomalyIntensity.MIN_STAGE_EXPOSURE_TICKS - 1, 2));
		assertEquals(1, AnomalyIntensity.progressedStage(1, 5,
				AnomalyIntensity.MIN_STAGE_EXPOSURE_TICKS, 1));
		assertEquals(2, AnomalyIntensity.progressedStage(1, 5,
				AnomalyIntensity.MIN_STAGE_EXPOSURE_TICKS, 2));
	}

	@Test
	void intervalsUseTheApprovedSlowBoundsWithoutHeatCompression() {
		assertTrue(between(AnomalyIntensity.intervalTicks(1, 0, 0, true), 4 * 60, 7 * 60));
		assertTrue(between(AnomalyIntensity.intervalTicks(1, 100, 41, false), 8 * 60, 14 * 60));
		assertTrue(between(AnomalyIntensity.intervalTicks(3, 100, 9, false), 7 * 60, 12 * 60));
		assertTrue(between(AnomalyIntensity.intervalTicks(5, 0, 17, false), 5 * 60, 9 * 60));
	}

	@Test
	void strongCooldownsStayBounded() {
		assertEquals(0L, AnomalyIntensity.strongCooldownTicks(3, 1));
		assertTrue(between(AnomalyIntensity.strongCooldownTicks(4, 2), 20 * 60, 30 * 60));
		assertTrue(between(AnomalyIntensity.strongCooldownTicks(5, 3), 20 * 60, 30 * 60));
	}

	private static boolean between(long ticks, int minimumSeconds, int maximumSeconds) {
		return ticks >= minimumSeconds * 20L && ticks <= maximumSeconds * 20L;
	}
}
