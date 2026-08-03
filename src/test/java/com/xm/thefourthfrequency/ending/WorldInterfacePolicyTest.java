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
			assertEquals(1.0D - 0.05D * destroyed,
					WorldInterfacePolicy.damageTakenMultiplier(destroyed), EPSILON);
			assertEquals(0.55D + 0.045D * destroyed,
					WorldInterfacePolicy.movementMultiplier(destroyed), EPSILON);
			assertEquals(1.50D - 0.075D * destroyed,
					WorldInterfacePolicy.attackCooldownMultiplier(destroyed), EPSILON);
		}
		assertEquals(maximumHealth * 0.00025D * 10 / 20.0D,
				WorldInterfacePolicy.healingPerTick(maximumHealth, 10), EPSILON);
		assertEquals(0.0D, WorldInterfacePolicy.healingPerTick(maximumHealth, 0), EPSILON);
		// Every anchor down bottoms the wall out at half damage, not a tenth of it.
		assertEquals(50.0D, WorldInterfacePolicy.adjustedIncomingDamage(100.0D, 10), EPSILON);
		assertEquals(0.50D, WorldInterfacePolicy.damageTakenMultiplier(10), EPSILON);
		assertThrows(IllegalArgumentException.class,
				() -> WorldInterfacePolicy.damageTakenMultiplier(11));
	}

	@Test
	void theCollapseClockIsSixFixedMinutesThatNoAnchorCanShorten() {
		assertEquals(7_200, WorldInterfacePolicy.COLLAPSE_DURATION_TICKS);
		assertEquals(0.0D, WorldInterfacePolicy.collapseProgress(0L), EPSILON);
		assertEquals(0.50D, WorldInterfacePolicy.collapseProgress(3_600L), EPSILON);
		assertEquals(1.0D, WorldInterfacePolicy.collapseProgress(7_200L), EPSILON);
		assertEquals(1.0D, WorldInterfacePolicy.collapseProgress(9_000L), EPSILON);
		assertEquals(7_200, WorldInterfacePolicy.remainingCollapseTicks(0L));
		assertEquals(0, WorldInterfacePolicy.remainingCollapseTicks(7_200L));
		assertThrows(IllegalArgumentException.class, () -> WorldInterfacePolicy.collapseProgress(-1L));
	}

	@Test
	void combatErosionStaysCleanEarlyThenAcceleratesWithTheCollapseTimer() {
		// Nothing outside the fight erodes, and the opening minutes stay clean so the island only
		// starts failing once the deadline is genuinely close.
		assertEquals(0.0F, WorldInterfacePolicy.presentationErosionProgress(
				WorldInterfaceStage.SUMMONING, 6_600L, -1L, 0L, 120), EPSILON);
		assertEquals(0.0F, WorldInterfacePolicy.presentationErosionProgress(
				WorldInterfaceStage.PHASE_1, 0L, -1L, 0L, 120), EPSILON);
		assertEquals(0.0F, WorldInterfacePolicy.presentationErosionProgress(
				WorldInterfaceStage.PHASE_1, 2_880L, -1L, 0L, 120), EPSILON);

		// Past the start point it climbs, and is still climbing right up to the deadline.
		float quarter = WorldInterfacePolicy.presentationErosionProgress(
				WorldInterfaceStage.PHASE_2, 4_320L, -1L, 0L, 120);
		float late = WorldInterfacePolicy.presentationErosionProgress(
				WorldInterfaceStage.PHASE_3, 6_480L, -1L, 0L, 120);
		assertTrue(quarter > 0.0F);
		assertTrue(late > quarter);
		assertEquals(WorldInterfacePolicy.COMBAT_EROSION_CEILING,
				WorldInterfacePolicy.presentationErosionProgress(
						WorldInterfaceStage.PHASE_3, 7_200L, -1L, 0L, 120), EPSILON);
	}

	@Test
	void losingContinuesFromTheCombatCeilingWhileWinningRestoresTheWorld() {
		// Failure picks up where combat left off rather than snapping back to zero first.
		assertEquals(WorldInterfacePolicy.COMBAT_EROSION_CEILING,
				WorldInterfacePolicy.presentationErosionProgress(
						WorldInterfaceStage.FAILURE_RESOLUTION, 7_200L, 1_000L, 1_000L, 120), EPSILON);
		assertEquals(WorldInterfacePolicy.COMBAT_EROSION_CEILING,
				WorldInterfacePolicy.presentationErosionProgress(
						WorldInterfaceStage.FAILURE_RESOLUTION, 7_200L, -1L, 1_120L, 120), EPSILON);
		float half = WorldInterfacePolicy.presentationErosionProgress(
				WorldInterfaceStage.FAILURE_RESOLUTION, 7_200L, 1_000L, 1_060L, 120);
		assertTrue(half > WorldInterfacePolicy.COMBAT_EROSION_CEILING && half < 1.0F);
		assertEquals(1.0F, WorldInterfacePolicy.presentationErosionProgress(
				WorldInterfaceStage.FAILURE_RESOLUTION, 7_200L, 1_000L, 1_120L, 120), EPSILON);
		assertEquals(1.0F, WorldInterfacePolicy.presentationErosionProgress(
				WorldInterfaceStage.FAILURE_RESOLUTION, 7_200L, 1_000L, 2_000L, 120), EPSILON);

		// Winning cuts the interface, and the materials come back immediately.
		assertEquals(0.0F, WorldInterfacePolicy.presentationErosionProgress(
				WorldInterfaceStage.SUCCESS_RESOLUTION, 7_200L, 1_000L, 1_120L, 120), EPSILON);
		assertEquals(0.0F, WorldInterfacePolicy.presentationErosionProgress(
				WorldInterfaceStage.PORTAL_OPEN, 7_200L, 1_000L, 1_120L, 120), EPSILON);
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
				WorldInterfacePolicy.resolveTick(7_199L, false));
		assertEquals(WorldInterfacePolicy.TickVerdict.SUCCESS,
				WorldInterfacePolicy.resolveTick(7_199L, true));
		assertEquals(WorldInterfacePolicy.TickVerdict.FAILURE,
				WorldInterfacePolicy.resolveTick(7_200L, false));
		assertEquals(WorldInterfacePolicy.TickVerdict.FAILURE,
				WorldInterfacePolicy.resolveTick(7_200L, true));
	}

	@Test
	void forcedEvictionAndTerrainBudgetsUseExactCeilAndCaps() {
		assertEquals(0, WorldInterfacePolicy.forcedEvictionTargetCount(0));
		assertEquals(0, WorldInterfacePolicy.forcedEvictionTargetCount(2));
		assertEquals(1, WorldInterfacePolicy.forcedEvictionTargetCount(3));
		assertEquals(2, WorldInterfacePolicy.forcedEvictionTargetCount(4));
		assertEquals(3, WorldInterfacePolicy.forcedEvictionTargetCount(8));
		assertEquals(32, WorldInterfacePolicy.terrainEditBudgetThisTick(0));
		assertEquals(3, WorldInterfacePolicy.terrainEditBudgetThisTick(8_189));
		assertEquals(0, WorldInterfacePolicy.terrainEditBudgetThisTick(8_192));
	}
}
