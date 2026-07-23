package com.xm.thefourthfrequency.state;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class TypedStateBoundaryTest {
	@Test
	void storySnapshotClampsValuesAtTheTypedBoundary() {
		StoryState state = new StoryState(true, 99, 2, 0b1111, true, true, false, 0b11111,
				true, "mining", true, true, true, 1_500, 8);
		assertEquals(3, state.bandStage());
		assertEquals(1_000, state.bodyProgress());
		assertEquals(0b111, state.calibratedBandsMask());
		assertEquals(0b1111, state.preludeAnomalyMask());
	}

	@Test
	void preludeAcceptsDifferentHiddenExposureCombinationsButStillRequiresAnAnomaly() {
		StoryState nightAnomalyWatcher = storyWithPrelude(true, 0b0001, true);
		StoryState nightTwoAnomalies = storyWithPrelude(true, 0b0011, false);
		StoryState twoAnomaliesWatcher = storyWithPrelude(false, 0b0011, true);
		StoryState nightWatcherOnly = storyWithPrelude(true, 0, true);

		assertEquals(3, nightAnomalyWatcher.preludeExposure());
		assertTrue(nightAnomalyWatcher.preludeReady());
		assertTrue(nightTwoAnomalies.preludeReady());
		assertTrue(twoAnomaliesWatcher.preludeReady());
		assertFalse(nightWatcherOnly.preludeReady());
	}

	@Test
	void anomalySchedulingAndSuspensionAreAtomicSnapshots() {
		AnomalyState active = new AnomalyState(4, 5, 1_200, 70, 9_000, false, "door_cascade", 8_000);
		AnomalyState stopped = active.suspended(true, 0L);
		assertTrue(stopped.suspended());
		assertEquals("none", stopped.activeId());
		assertEquals(0L, stopped.activeUntil());
		assertEquals(12_000L, stopped.scheduled(12_000L).nextAmbientTick());
	}

	@Test
	void navigationLocationCanClearWithoutLosingSelectedNeed() {
		NavigationState state = new NavigationState("iron", "minecraft:raw_iron", true,
				"minecraft:iron_ore", 123L, "minecraft:overworld", 120L);
		NavigationState cleared = state.clearLocation();
		assertEquals("iron", cleared.kind());
		assertEquals("minecraft:raw_iron", cleared.itemId());
		assertFalse(cleared.located());
		assertEquals("", cleared.dimension());
	}

	@Test
	void playerPatternRoleIsDerivedFromTypedActivityCounters() {
		PlayerPatternState builder = new PlayerPatternState(4, 20, 3, 2, "", 0, "none", 0L,
				-1, "", 0, "unresolved", "");
		PlayerPatternState miner = new PlayerPatternState(20, 4, 3, 2, "", 0, "none", 0L,
				-1, "", 0, "unresolved", "");
		PlayerPatternState operator = new PlayerPatternState(2, 1, 10, 8, "", 0, "none", 0L,
				-1, "", 0, "unresolved", "");
		assertEquals("builder", builder.inferredRole());
		assertEquals("miner", miner.inferredRole());
		assertEquals("operator", operator.inferredRole());
	}

	private static StoryState storyWithPrelude(boolean night, int anomalyMask, boolean watcher) {
		return new StoryState(true, 0, 2, 0b111, true, night, night, anomalyMask, watcher,
				"mining", false, false, false, 0, 0);
	}
}
