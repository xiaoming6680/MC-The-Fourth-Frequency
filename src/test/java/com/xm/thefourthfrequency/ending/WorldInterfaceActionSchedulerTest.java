package com.xm.thefourthfrequency.ending;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldInterfaceActionSchedulerTest {
	@Test
	void deterministicShuffleNeverRepeatsAdjacentActions() {
		for (WorldInterfaceStage stage : List.of(
				WorldInterfaceStage.PHASE_1,
				WorldInterfaceStage.PHASE_2,
				WorldInterfaceStage.PHASE_3)) {
			WorldInterfaceAction previous = null;
			WorldInterfaceAction previousStateless = null;
			for (long sequence = 0; sequence < 200; sequence++) {
				WorldInterfaceAction selected = WorldInterfaceActionScheduler.nextAction(
						stage, 0x4F52_4947_494EL, sequence, previous);
				assertEquals(selected, WorldInterfaceActionScheduler.nextAction(
						stage, 0x4F52_4947_494EL, sequence, previous));
				if (previous != null) assertNotEquals(previous, selected);
				assertTrue(selected.isUnlockedAt(stage));
				previous = selected;
				WorldInterfaceAction stateless = WorldInterfaceActionScheduler.nextAction(
						stage, 0x4F52_4947_494EL, sequence);
				if (previousStateless != null) assertNotEquals(previousStateless, stateless);
				previousStateless = stateless;
			}
		}
	}

	@Test
	void phaseIntervalsStayInsideTheirExactInclusiveRanges() {
		assertIntervals(WorldInterfaceStage.PHASE_1, 140, 180);
		assertIntervals(WorldInterfaceStage.PHASE_2, 100, 140);
		assertIntervals(WorldInterfaceStage.PHASE_3, 70, 110);

		int base = WorldInterfaceActionScheduler.baseIntervalTicks(WorldInterfaceStage.PHASE_2, 91L, 4L);
		assertEquals(Math.round(base * 1.50D),
				WorldInterfaceActionScheduler.scaledIntervalTicks(WorldInterfaceStage.PHASE_2, 91L, 4L, 0));
		assertEquals(Math.round(base * 0.75D),
				WorldInterfaceActionScheduler.scaledIntervalTicks(WorldInterfaceStage.PHASE_2, 91L, 4L, 10));
	}

	@Test
	void exclusiveControlsShareOneLaneAndRespectSixHundredTickTargetImmunity() {
		assertFalse(WorldInterfaceActionScheduler.canStartExclusiveControl(
				WorldInterfaceAction.GRAB_THROW, WorldInterfaceAction.GRAB_SLAM));
		assertTrue(WorldInterfaceActionScheduler.canStartExclusiveControl(
				WorldInterfaceAction.LASER_SWEEP, WorldInterfaceAction.GRAB_SLAM));
		assertTrue(WorldInterfaceActionScheduler.canStartAction(
				WorldInterfaceAction.GRAB_THROW, null, 999L, -1L));
		assertFalse(WorldInterfaceActionScheduler.canStartAction(
				WorldInterfaceAction.GRAB_THROW, null, 1_599L, 1_000L));
		assertTrue(WorldInterfaceActionScheduler.canStartAction(
				WorldInterfaceAction.GRAB_THROW, null, 1_600L, 1_000L));
		assertTrue(WorldInterfaceActionScheduler.isStrongControlTargetEligible(
				WorldInterfaceAction.MENTAL_ASSAULT, 1L, 1L));
	}

	@Test
	void forcedEvictionHasStrictCooldownAndNeverSelectsIntegratedHost() {
		assertFalse(WorldInterfaceActionScheduler.isForcedEvictionReady(1_000L, -1L, 2));
		assertTrue(WorldInterfaceActionScheduler.isForcedEvictionReady(1_000L, -1L, 3));
		assertFalse(WorldInterfaceActionScheduler.isForcedEvictionReady(4_599L, 1_000L, 8));
		assertTrue(WorldInterfaceActionScheduler.isForcedEvictionReady(4_600L, 1_000L, 8));

		List<UUID> roster = new ArrayList<>();
		for (int value = 1; value <= 8; value++) {
			roster.add(new UUID(0L, value));
		}
		UUID host = roster.get(3);
		List<UUID> first = WorldInterfaceActionScheduler.selectForcedEvictionTargets(
				roster, host, 0x4556_4943_54L, 7L);
		Collections.reverse(roster);
		List<UUID> reordered = WorldInterfaceActionScheduler.selectForcedEvictionTargets(
				roster, host, 0x4556_4943_54L, 7L);
		assertEquals(3, first.size());
		assertEquals(first, reordered);
		assertFalse(first.contains(host));
	}

	private static void assertIntervals(WorldInterfaceStage stage, int minimum, int maximum) {
		assertEquals(minimum, WorldInterfaceActionScheduler.intervalBounds(stage).minimumTicks());
		assertEquals(maximum, WorldInterfaceActionScheduler.intervalBounds(stage).maximumTicks());
		for (long sequence = 0L; sequence < 500L; sequence++) {
			int interval = WorldInterfaceActionScheduler.baseIntervalTicks(stage, 55L, sequence);
			assertTrue(interval >= minimum && interval <= maximum);
			assertEquals(interval, WorldInterfaceActionScheduler.baseIntervalTicks(stage, 55L, sequence));
		}
	}
}
