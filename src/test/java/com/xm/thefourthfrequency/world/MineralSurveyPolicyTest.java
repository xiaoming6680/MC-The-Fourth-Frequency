package com.xm.thefourthfrequency.world;

import com.xm.thefourthfrequency.terminal.TerminalResource;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
	void mineralArrivalUsesARealOneBlockSphere() {
		assertTrue(MineralSurveyPolicy.arrived(1, 0, 0));
		assertTrue(MineralSurveyPolicy.arrived(0, -1, 0));
		assertFalse(MineralSurveyPolicy.arrived(1, 1, 0));
		assertFalse(MineralSurveyPolicy.arrived(0, 0, 2));
	}

	@Test
	void rarerOreMustBeCloserToBeHeard() {
		assertTrue(MineralSurveyPolicy.probeRadius(TerminalResource.COAL)
				> MineralSurveyPolicy.probeRadius(TerminalResource.IRON));
		assertTrue(MineralSurveyPolicy.probeRadius(TerminalResource.IRON)
				> MineralSurveyPolicy.probeRadius(TerminalResource.GOLD));
		assertTrue(MineralSurveyPolicy.probeRadius(TerminalResource.GOLD)
				> MineralSurveyPolicy.probeRadius(TerminalResource.DIAMOND));
		assertEquals(0, MineralSurveyPolicy.probeRadius(TerminalResource.NONE));
	}

	@Test
	void sweepCeilingNarrowsAsRarerFindingsRuleOutTheRest() {
		int all = mask(TerminalResource.COAL, TerminalResource.IRON,
				TerminalResource.GOLD, TerminalResource.DIAMOND);
		assertEquals(MineralSurveyPolicy.probeRadius(TerminalResource.COAL),
				MineralSurveyPolicy.rarerCeiling(all, TerminalResource.NONE));
		assertEquals(MineralSurveyPolicy.probeRadius(TerminalResource.GOLD),
				MineralSurveyPolicy.rarerCeiling(all, TerminalResource.IRON));
		assertEquals(MineralSurveyPolicy.probeRadius(TerminalResource.DIAMOND),
				MineralSurveyPolicy.rarerCeiling(all, TerminalResource.GOLD));
		assertEquals(0, MineralSurveyPolicy.rarerCeiling(all, TerminalResource.DIAMOND),
				"Nothing outranks diamond, so finding it ends the sweep immediately");
	}

	@Test
	void lockedOreNeverWidensTheSweep() {
		int early = mask(TerminalResource.COAL, TerminalResource.IRON);
		assertEquals(MineralSurveyPolicy.probeRadius(TerminalResource.COAL),
				MineralSurveyPolicy.rarerCeiling(early, TerminalResource.NONE));
		assertEquals(0, MineralSurveyPolicy.rarerCeiling(early, TerminalResource.IRON),
				"With gold and diamond still locked, iron is already the best possible reading");
		assertFalse(MineralSurveyPolicy.unlocked(early, TerminalResource.DIAMOND));
	}

	@Test
	void readingIsExactOnlyInsideTwelveBlocks() {
		assertTrue(MineralSurveyPolicy.exactReading(12, 0, 0));
		assertTrue(MineralSurveyPolicy.exactReading(6, 6, 6));
		assertFalse(MineralSurveyPolicy.exactReading(12, 1, 0));
		assertFalse(MineralSurveyPolicy.exactReading(0, 0, 13));
	}

	@Test
	void distanceBandAlwaysBracketsTheTrueDistance() {
		for (int distance : new int[]{13, 20, 28, 32}) {
			int minimum = MineralSurveyPolicy.bandMinimum(distance);
			int maximum = MineralSurveyPolicy.bandMaximum(distance);
			assertTrue(minimum >= 1 && minimum <= distance, "band floor for " + distance);
			assertTrue(maximum > distance, "band ceiling for " + distance);
		}
		assertTrue(MineralSurveyPolicy.bandMaximum(0) > MineralSurveyPolicy.bandMinimum(0));
	}

	@Test
	void bearingSnapsToTheEightNamedCompassPoints() {
		MineralSurveyPolicy.Bearing east = MineralSurveyPolicy.quantizeBearing(30, 2);
		assertEquals(0, east.dz());
		assertTrue(east.dx() > 0);
		MineralSurveyPolicy.Bearing northeast = MineralSurveyPolicy.quantizeBearing(20, 19);
		assertEquals(northeast.dx(), northeast.dz(), "A 45 degree bearing must stay diagonal");
		assertEquals(0, MineralSurveyPolicy.quantizeBearing(0, 0).dx());
	}

	@Test
	void chargesRefillOneIntervalAtATimeAndStopAtTheCap() {
		MineralSurveyPolicy.ChargeState empty = new MineralSurveyPolicy.ChargeState(0, 1_000L);
		assertEquals(0, MineralSurveyPolicy.charges(empty.charges(), empty.nextRechargeTick(), 999L).charges());
		assertEquals(1, MineralSurveyPolicy.charges(empty.charges(), empty.nextRechargeTick(), 1_000L).charges());
		assertEquals(2, MineralSurveyPolicy.charges(empty.charges(), empty.nextRechargeTick(),
				1_000L + MineralSurveyPolicy.CHARGE_RECHARGE_TICKS).charges());
		MineralSurveyPolicy.ChargeState full = MineralSurveyPolicy.charges(0, 1_000L, 1_000_000L);
		assertEquals(MineralSurveyPolicy.MAX_PROBE_CHARGES, full.charges());
		assertEquals(0L, full.nextRechargeTick(), "A full bank has no charge in flight");
	}

	@Test
	void spendingStartsTheClockOnceAndNeverRestartsIt() {
		MineralSurveyPolicy.ChargeState full =
				new MineralSurveyPolicy.ChargeState(MineralSurveyPolicy.MAX_PROBE_CHARGES, 0L);
		MineralSurveyPolicy.ChargeState first = MineralSurveyPolicy.spend(full, 500L);
		assertEquals(MineralSurveyPolicy.MAX_PROBE_CHARGES - 1, first.charges());
		assertEquals(500L + MineralSurveyPolicy.CHARGE_RECHARGE_TICKS, first.nextRechargeTick());
		MineralSurveyPolicy.ChargeState second = MineralSurveyPolicy.spend(first, 800L);
		assertEquals(first.nextRechargeTick(), second.nextRechargeTick(),
				"Pressing again must not push back the charge already coming");
		assertEquals(0, MineralSurveyPolicy.spend(
				new MineralSurveyPolicy.ChargeState(0, 900L), 800L).charges());
	}

	@Test
	void aTimerFromAnotherClockIsRestartedRatherThanTrusted() {
		MineralSurveyPolicy.ChargeState restored = MineralSurveyPolicy.charges(0,
				10_000L + MineralSurveyPolicy.CHARGE_RECHARGE_TICKS * 5L, 10_000L);
		assertEquals(0, restored.charges());
		assertEquals(10_000L + MineralSurveyPolicy.CHARGE_RECHARGE_TICKS, restored.nextRechargeTick());
	}

	private static int mask(TerminalResource... resources) {
		int mask = 0;
		for (TerminalResource resource : resources) mask |= 1 << resource.wireId();
		return mask;
	}
}
