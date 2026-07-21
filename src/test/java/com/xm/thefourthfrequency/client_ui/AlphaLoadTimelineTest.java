package com.xm.thefourthfrequency.client_ui;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class AlphaLoadTimelineTest {
	@Test
	void resourceStackKeepsUserOrderThenAppliesThreeBasesLowToHigh() {
		List<String> result = AlphaResourcePackPlan.selectionForSession(
				List.of("vanilla", "file/custom.zip", "programmer_art", "thefourthfrequency:golden_days_alpha"),
				List.of("vanilla", "file/custom.zip", "programmer_art",
						"thefourthfrequency:golden_days_base", "thefourthfrequency:golden_days_alpha"));
		assertEquals(List.of("vanilla", "file/custom.zip", "programmer_art",
				"thefourthfrequency:golden_days_base", "thefourthfrequency:golden_days_alpha"), result);
		assertEquals(3, AlphaResourcePackPlan.SESSION_BASES_LOW_TO_HIGH.size());
		for (String packId : AlphaResourcePackPlan.SESSION_BASES_LOW_TO_HIGH) {
			assertTrue(AlphaResourcePackPlan.isHiddenFromSelectionScreen(packId));
		}
	}

	@Test
	void titleFallsInVisibleStepsAndEndsAtAlphaOnePointZero() {
		assertEquals("1.21.11", AlphaLoadTimeline.versionAt(0, "1.21.11"));
		assertEquals(0, AlphaLoadTimeline.versionStage(AlphaLoadTimeline.VERSION_STEP_TICKS - 1));
		assertEquals(1, AlphaLoadTimeline.versionStage(AlphaLoadTimeline.VERSION_STEP_TICKS));
		assertEquals("Alpha 1.0.0", AlphaLoadTimeline.versionAt(
				AlphaLoadTimeline.finalVersionStage(), "1.21.11"));
		assertEquals(AlphaLoadTimeline.finalVersionStage(), AlphaLoadTimeline.versionStage(10_000));
	}

	@Test
	void titleContextTracksSingleplayerAndMultiplayer() {
		assertEquals("window.thefourthfrequency.alpha_load.singleplayer",
				AlphaLoadTimeline.windowContextKey(true));
		assertEquals("window.thefourthfrequency.alpha_load.multiplayer",
				AlphaLoadTimeline.windowContextKey(false));
	}

	@Test
	void persistentLegacyPresentationStartsBeforeAWorldConnectionButNotDuringFirstCorruption() {
		assertFalse(AlphaLoadingPresentationPolicy.usePersistentLegacyPresentation(false, false));
		assertFalse(AlphaLoadingPresentationPolicy.usePersistentLegacyPresentation(true, true));
		assertTrue(AlphaLoadingPresentationPolicy.usePersistentLegacyPresentation(true, false));
	}

	@Test
	void firstEntryNormalPreludeStopsAtHalfForExactlyOneSecond() {
		assertEquals(20, AlphaLoadTimeline.NORMAL_PAUSE_TICKS);
		assertTrue(AlphaLoadTimeline.initialNormalFrame(0));
		assertEquals(0.0F, AlphaLoadTimeline.initialNormalProgress(0));
		assertEquals(0.5F, AlphaLoadTimeline.initialNormalProgress(
				AlphaLoadTimeline.NORMAL_PROGRESS_END_TICK));
		assertEquals(0.5F, AlphaLoadTimeline.initialNormalProgress(
				AlphaLoadTimeline.GLITCH_START_TICK - 1));
		assertFalse(AlphaLoadTimeline.initialNormalFrame(AlphaLoadTimeline.GLITCH_START_TICK));
	}

	@Test
	void terrainFailurePastesDownwardThenSmallThenLargeWithoutHoldingForever() {
		assertEquals(0, AlphaLoadTimeline.copiedFailureLines(AlphaLoadTimeline.FAILURE_TICK - 1));
		assertEquals(1, AlphaLoadTimeline.copiedFailureLines(AlphaLoadTimeline.FAILURE_TICK));
		assertEquals(AlphaLoadTimeline.MAX_FAILURE_COPIES,
				AlphaLoadTimeline.copiedFailureLines(10_000));
		assertTrue(AlphaLoadTimeline.MAX_FAILURE_COPIES > 12);
		int downwardMid = (AlphaLoadTimeline.FAILURE_TICK + AlphaLoadTimeline.FLOOD_START_TICK) / 2;
		int earlyDownwardCopies = AlphaLoadTimeline.copiedFailureLines(downwardMid) - 1;
		int lateDownwardCopies = AlphaLoadTimeline.MAX_FAILURE_COPIES
				- AlphaLoadTimeline.copiedFailureLines(downwardMid);
		assertTrue(lateDownwardCopies > earlyDownwardCopies);
		assertEquals(0, AlphaLoadTimeline.smallFailureCopies(AlphaLoadTimeline.FLOOD_START_TICK - 1));
		assertEquals(6, AlphaLoadTimeline.smallFailureCopies(AlphaLoadTimeline.FLOOD_START_TICK));
		assertEquals(AlphaLoadTimeline.MAX_SMALL_FAILURE_COPIES,
				AlphaLoadTimeline.smallFailureCopies(AlphaLoadTimeline.SMALL_PASTE_COMPLETE_TICK));
		assertTrue(AlphaLoadTimeline.MAX_SMALL_FAILURE_COPIES > 96);
		assertEquals(0, AlphaLoadTimeline.largeFailureCopies(
				AlphaLoadTimeline.LARGE_PASTE_START_TICK - 1));
		assertEquals(1, AlphaLoadTimeline.largeFailureCopies(
				AlphaLoadTimeline.LARGE_PASTE_START_TICK));
		assertTrue(AlphaLoadTimeline.smallFailureCopies(AlphaLoadTimeline.LARGE_PASTE_START_TICK)
				< AlphaLoadTimeline.MAX_SMALL_FAILURE_COPIES);
		assertEquals(AlphaLoadTimeline.MAX_LARGE_FAILURE_COPIES,
				AlphaLoadTimeline.largeFailureCopies(AlphaLoadTimeline.FREEZE_START_TICK));
		assertTrue(AlphaLoadTimeline.MAX_LARGE_FAILURE_COPIES > 48);
		assertFalse(AlphaLoadTimeline.frozenFailureFrame(AlphaLoadTimeline.FREEZE_START_TICK - 1));
		assertTrue(AlphaLoadTimeline.frozenFailureFrame(AlphaLoadTimeline.FREEZE_START_TICK));
		assertFalse(AlphaLoadTimeline.legacyRecoveryFrame(
				AlphaLoadTimeline.LEGACY_RECOVERY_START_TICK - 1));
		assertTrue(AlphaLoadTimeline.legacyRecoveryFrame(
				AlphaLoadTimeline.LEGACY_RECOVERY_START_TICK));
		assertEquals(AlphaLoadTimeline.FREEZE_START_TICK,
				AlphaLoadTimeline.failureMotionTick(10_000));
		assertFalse(AlphaLoadTimeline.mayCloseLoadingScreen(
				AlphaLoadTimeline.MIN_LOADING_SCREEN_TICKS - 1, true, true));
		assertFalse(AlphaLoadTimeline.mayCloseLoadingScreen(
				AlphaLoadTimeline.MIN_LOADING_SCREEN_TICKS, false, true));
		assertFalse(AlphaLoadTimeline.mayCloseLoadingScreen(
				AlphaLoadTimeline.MIN_LOADING_SCREEN_TICKS, true, false));
		assertTrue(AlphaLoadTimeline.mayCloseLoadingScreen(
				AlphaLoadTimeline.MIN_LOADING_SCREEN_TICKS, true, true));
		assertTrue(AlphaLoadTimeline.mayCloseLoadingScreen(
				AlphaLoadTimeline.MAX_RESOURCE_RELOAD_WAIT_TICKS, false, false));
	}
}
