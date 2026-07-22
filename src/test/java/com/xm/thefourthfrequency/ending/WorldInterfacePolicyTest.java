package com.xm.thefourthfrequency.ending;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldInterfacePolicyTest {
	private static final double EPSILON = 0.000_000_1D;

	@Test
	void frozenRosterScalesHealthLinearlyFromOneThroughEightPlayers() {
		for (int players = 1; players <= 8; players++) {
			assertEquals(600.0D * players, WorldInterfacePolicy.maxHealth(players), EPSILON);
		}
		assertThrows(IllegalArgumentException.class, () -> WorldInterfacePolicy.maxHealth(0));
		assertThrows(IllegalArgumentException.class, () -> WorldInterfacePolicy.maxHealth(9));
	}

	@Test
	void authoritativeAnchorFormulasMatchEveryEndpoint() {
		double maximumHealth = WorldInterfacePolicy.maxHealth(8);
		for (int destroyed = 0; destroyed <= 10; destroyed++) {
			assertEquals(1.0D - 0.09D * destroyed,
					WorldInterfacePolicy.damageTakenMultiplier(destroyed), EPSILON);
			assertEquals(0.55D + 0.045D * destroyed,
					WorldInterfacePolicy.movementMultiplier(destroyed), EPSILON);
			assertEquals(1.50D - 0.075D * destroyed,
					WorldInterfacePolicy.attackCooldownMultiplier(destroyed), EPSILON);
		}
		assertEquals(maximumHealth * 0.0001D * 10 / 20.0D,
				WorldInterfacePolicy.healingPerTick(maximumHealth, 10), EPSILON);
		assertEquals(0.0D, WorldInterfacePolicy.healingPerTick(maximumHealth, 0), EPSILON);
		assertEquals(10.0D, WorldInterfacePolicy.adjustedIncomingDamage(100.0D, 10), EPSILON);
		assertThrows(IllegalArgumentException.class,
				() -> WorldInterfacePolicy.damageTakenMultiplier(11));
	}

	@Test
	void eachDestroyedAnchorAddsFivePercentAndPenaltyCapsAtFiftyPercent() {
		assertEquals(0, WorldInterfacePolicy.anchorPenaltyTicks(0));
		assertEquals(600, WorldInterfacePolicy.anchorPenaltyTicks(1));
		assertEquals(6_000, WorldInterfacePolicy.anchorPenaltyTicks(10));
		assertEquals(0.05D, WorldInterfacePolicy.collapseProgress(0L, 1), EPSILON);
		assertEquals(0.50D, WorldInterfacePolicy.collapseProgress(0L, 10), EPSILON);
		assertEquals(1.0D, WorldInterfacePolicy.collapseProgress(6_000L, 10), EPSILON);
		assertEquals(6_000, WorldInterfacePolicy.remainingCollapseTicks(0L, 10));
		assertEquals(0, WorldInterfacePolicy.remainingCollapseTicks(6_000L, 10));
	}

	@Test
	void failureErosionUsesOnlyTheFailureResolutionClock() {
		assertEquals(0.0F, WorldInterfacePolicy.failurePresentationProgress(
				WorldInterfaceStage.PHASE_3, 1_000L, 1_120L, 120), EPSILON);
		assertEquals(0.0F, WorldInterfacePolicy.failurePresentationProgress(
				WorldInterfaceStage.SUCCESS_RESOLUTION, 1_000L, 1_120L, 120), EPSILON);
		assertEquals(0.0F, WorldInterfacePolicy.failurePresentationProgress(
				WorldInterfaceStage.PORTAL_OPEN, 1_000L, 1_120L, 120), EPSILON);
		assertEquals(0.0F, WorldInterfacePolicy.failurePresentationProgress(
				WorldInterfaceStage.FAILURE_RESOLUTION, -1L, 1_120L, 120), EPSILON);

		assertEquals(0.0F, WorldInterfacePolicy.failurePresentationProgress(
				WorldInterfaceStage.FAILURE_RESOLUTION, 1_000L, 1_000L, 120), EPSILON);
		assertEquals(0.5F, WorldInterfacePolicy.failurePresentationProgress(
				WorldInterfaceStage.FAILURE_RESOLUTION, 1_000L, 1_060L, 120), EPSILON);
		assertEquals(1.0F, WorldInterfacePolicy.failurePresentationProgress(
				WorldInterfaceStage.FAILURE_RESOLUTION, 1_000L, 1_120L, 120), EPSILON);
		assertEquals(1.0F, WorldInterfacePolicy.failurePresentationProgress(
				WorldInterfaceStage.FAILURE_RESOLUTION, 1_000L, 2_000L, 120), EPSILON);
	}

	@Test
	void timerOnlyAdvancesWhenAFrozenRosterMemberIsOnlineAnywhere() {
		assertFalse(WorldInterfacePolicy.timerAdvances(8, 0));
		assertTrue(WorldInterfacePolicy.timerAdvances(8, 1));
		assertTrue(WorldInterfacePolicy.timerAdvances(8, 8));
		assertThrows(IllegalArgumentException.class, () -> WorldInterfacePolicy.timerAdvances(3, 4));
	}

	@Test
	void timeoutWinsBeforeLethalDamageOnTheSameTick() {
		assertEquals(WorldInterfacePolicy.TickVerdict.ONGOING,
				WorldInterfacePolicy.resolveTick(11_999L, 0, false));
		assertEquals(WorldInterfacePolicy.TickVerdict.SUCCESS,
				WorldInterfacePolicy.resolveTick(11_999L, 0, true));
		assertEquals(WorldInterfacePolicy.TickVerdict.FAILURE,
				WorldInterfacePolicy.resolveTick(12_000L, 0, false));
		assertEquals(WorldInterfacePolicy.TickVerdict.FAILURE,
				WorldInterfacePolicy.resolveTick(12_000L, 0, true));
		assertEquals(WorldInterfacePolicy.TickVerdict.SUCCESS,
				WorldInterfacePolicy.resolveTick(11_399L, 1, true));
		assertEquals(WorldInterfacePolicy.TickVerdict.FAILURE,
				WorldInterfacePolicy.resolveTick(11_400L, 1, true));
	}

	@Test
	void forcedEvictionAndTerrainBudgetsUseExactCeilAndCaps() {
		assertEquals(0, WorldInterfacePolicy.forcedEvictionTargetCount(0));
		assertEquals(0, WorldInterfacePolicy.forcedEvictionTargetCount(2));
		assertEquals(1, WorldInterfacePolicy.forcedEvictionTargetCount(3));
		assertEquals(2, WorldInterfacePolicy.forcedEvictionTargetCount(4));
		assertEquals(3, WorldInterfacePolicy.forcedEvictionTargetCount(8));
		assertEquals(8, WorldInterfacePolicy.terrainEditBudgetThisTick(0));
		assertEquals(3, WorldInterfacePolicy.terrainEditBudgetThisTick(2_045));
		assertEquals(0, WorldInterfacePolicy.terrainEditBudgetThisTick(2_048));
	}
}
