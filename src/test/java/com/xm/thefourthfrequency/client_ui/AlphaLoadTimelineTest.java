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
	void titleFallsInVisibleStepsAndEndsAtOnePointZeroWithoutEraLabels() {
		assertEquals("1.21.11", AlphaLoadTimeline.versionAt(0, "1.21.11"));
		assertEquals(0, AlphaLoadTimeline.versionStage(AlphaLoadTimeline.VERSION_STEP_TICKS - 1));
		assertEquals(1, AlphaLoadTimeline.versionStage(AlphaLoadTimeline.VERSION_STEP_TICKS));
		assertEquals("1.7.3", AlphaLoadTimeline.versionAt(
				AlphaLoadTimeline.finalVersionStage() - 1, "1.21.11"));
		assertEquals("1.0.0", AlphaLoadTimeline.versionAt(
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
	void firstEntryUsesTheAlreadyVisibleHalfProgressAndOnlyKeepsThePause() {
		assertEquals(0, AlphaLoadTimeline.NORMAL_PROGRESS_END_TICK);
		assertEquals(20, AlphaLoadTimeline.NORMAL_PAUSE_TICKS);
		assertTrue(AlphaLoadTimeline.initialNormalFrame(0));
		assertEquals(0.5F, AlphaLoadTimeline.initialNormalProgress(0));
		assertEquals(0.5F, AlphaLoadTimeline.initialNormalProgress(
				AlphaLoadTimeline.GLITCH_START_TICK - 1));
		assertFalse(AlphaLoadTimeline.initialNormalFrame(AlphaLoadTimeline.GLITCH_START_TICK));
	}

	@Test
	void terrainFailureEscalatesFromSparseRecognitionToImmediateFullScreenWall() {
		assertEquals(AlphaLoadTimeline.GLITCH_START_TICK + 24, AlphaLoadTimeline.FAILURE_TICK);
		assertFalse(AlphaLoadTimeline.observerMessageVisible(
				AlphaLoadTimeline.OBSERVER_MESSAGE_START_TICK - 1));
		assertTrue(AlphaLoadTimeline.observerMessageVisible(
				AlphaLoadTimeline.OBSERVER_MESSAGE_START_TICK));
		assertFalse(AlphaLoadTimeline.observerMessageVisible(
				AlphaLoadTimeline.OBSERVER_MESSAGE_END_TICK));
		assertEquals(0, AlphaLoadTimeline.copiedFailureLines(AlphaLoadTimeline.FAILURE_TICK - 1));
		assertEquals(1, AlphaLoadTimeline.copiedFailureLines(AlphaLoadTimeline.FAILURE_TICK));
		assertEquals(AlphaLoadTimeline.MAX_FAILURE_COPIES,
				AlphaLoadTimeline.copiedFailureLines(10_000));
		assertTrue(AlphaLoadTimeline.MAX_FAILURE_COPIES >= 24);
		assertTrue(AlphaLoadTimeline.MAX_FAILURE_COPIES <= 32);
		int downwardMid = (AlphaLoadTimeline.FAILURE_TICK + AlphaLoadTimeline.FLOOD_START_TICK) / 2;
		int earlyDownwardCopies = AlphaLoadTimeline.copiedFailureLines(downwardMid) - 1;
		int lateDownwardCopies = AlphaLoadTimeline.MAX_FAILURE_COPIES
				- AlphaLoadTimeline.copiedFailureLines(downwardMid);
		assertTrue(earlyDownwardCopies > lateDownwardCopies);
		assertFalse(AlphaLoadTimeline.fullScreenFailureWall(
				AlphaLoadTimeline.FLOOD_START_TICK - 1));
		assertTrue(AlphaLoadTimeline.fullScreenFailureWall(
				AlphaLoadTimeline.FLOOD_START_TICK));
		assertTrue(AlphaLoadTimeline.fullScreenFailureWall(
				AlphaLoadTimeline.FREEZE_START_TICK));
		assertFalse(AlphaLoadTimeline.fullScreenFailureWall(
				AlphaLoadTimeline.BLACKOUT_START_TICK));
		assertEquals(40, AlphaLoadTimeline.LEGACY_RECOVERY_START_TICK
				- AlphaLoadTimeline.FLOOD_START_TICK
				- AlphaLoadTimeline.BLACKOUT_TICKS);
		assertFalse(AlphaLoadTimeline.frozenFailureFrame(AlphaLoadTimeline.FREEZE_START_TICK - 1));
		assertTrue(AlphaLoadTimeline.frozenFailureFrame(AlphaLoadTimeline.FREEZE_START_TICK));
		assertFalse(AlphaLoadTimeline.frozenFailureFrame(AlphaLoadTimeline.BLACKOUT_START_TICK));
		assertFalse(AlphaLoadTimeline.blackoutFrame(AlphaLoadTimeline.BLACKOUT_START_TICK - 1));
		assertTrue(AlphaLoadTimeline.blackoutFrame(AlphaLoadTimeline.BLACKOUT_START_TICK));
		assertTrue(AlphaLoadTimeline.blackoutFrame(
				AlphaLoadTimeline.LEGACY_RECOVERY_START_TICK - 1));
		assertFalse(AlphaLoadTimeline.blackoutFrame(
				AlphaLoadTimeline.LEGACY_RECOVERY_START_TICK));
		assertEquals(40, AlphaLoadTimeline.BLACKOUT_TICKS);
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
