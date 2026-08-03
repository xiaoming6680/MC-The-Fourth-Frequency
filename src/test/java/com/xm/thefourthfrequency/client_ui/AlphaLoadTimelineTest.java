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
		assertEquals("1.7.3", AlphaLoadTimeline.versionAt(
				AlphaLoadTimeline.finalVersionStage() - 1, "1.21.11"));
		assertEquals("1.0.0", AlphaLoadTimeline.versionAt(
				AlphaLoadTimeline.finalVersionStage(), "1.21.11"));
		assertEquals(AlphaLoadTimeline.finalVersionStage(), AlphaLoadTimeline.versionStage(10_000));
	}

	@Test
	void everyTitleStepLandsOnAnEventTheScreenIsAlreadyShowing() {
		assertEquals(0, AlphaLoadTimeline.versionStage(0));
		assertEquals(0, AlphaLoadTimeline.versionStage(AlphaLoadTimeline.GLITCH_START_TICK - 1));
		assertEquals(1, AlphaLoadTimeline.versionStage(AlphaLoadTimeline.GLITCH_START_TICK));
		assertEquals(2, AlphaLoadTimeline.versionStage(AlphaLoadTimeline.FAILURE_TICK));
		assertEquals(3, AlphaLoadTimeline.versionStage(
				AlphaLoadTimeline.OBSERVER_MESSAGE_START_TICK));
		assertEquals(4, AlphaLoadTimeline.versionStage(AlphaLoadTimeline.FLOOD_START_TICK));
		assertEquals(5, AlphaLoadTimeline.versionStage(AlphaLoadTimeline.FREEZE_START_TICK));

		// The step the whole sequence exists to deliver has to be readable when it happens, so it
		// lands on the frame the picture returns rather than anywhere inside the dead air.
		assertEquals(AlphaLoadTimeline.finalVersionStage(), AlphaLoadTimeline.versionStage(
				AlphaLoadTimeline.LEGACY_RECOVERY_START_TICK));
		assertEquals(AlphaLoadTimeline.LEGACY_RECOVERY_START_TICK,
				AlphaLoadTimeline.versionStageTick(AlphaLoadTimeline.finalVersionStage()));
		assertFalse(AlphaLoadTimeline.blackoutFrame(
				AlphaLoadTimeline.versionStageTick(AlphaLoadTimeline.finalVersionStage())));

		// Steps must be strictly ordered, and there must be exactly one per declared version.
		int previous = -1;
		for (int stage = 0; stage <= AlphaLoadTimeline.finalVersionStage(); stage++) {
			int tick = AlphaLoadTimeline.versionStageTick(stage);
			assertTrue(tick > previous, "Title stage " + stage + " must advance the clock");
			assertEquals(stage, AlphaLoadTimeline.versionStage(tick));
			previous = tick;
		}
	}

	@Test
	void mediumLayersRampAcrossTransitionsAndFreezeWithThePicture() {
		// Nothing before the first glitch: the prelude has to be an ordinary loading screen.
		assertEquals(0, AlphaLoadTimeline.scanlineAlpha(AlphaLoadTimeline.GLITCH_START_TICK - 1));
		assertEquals(0.0F, AlphaLoadTimeline.chromaOffset(AlphaLoadTimeline.GLITCH_START_TICK - 1));
		assertFalse(AlphaLoadTimeline.trackingBandVisible(AlphaLoadTimeline.GLITCH_START_TICK - 1));
		assertFalse(AlphaLoadTimeline.timecodeVisible(AlphaLoadTimeline.GLITCH_START_TICK - 1));

		// Layers arrive over a ramp rather than on one frame.
		assertTrue(AlphaLoadTimeline.scanlineAlpha(AlphaLoadTimeline.GLITCH_START_TICK
				+ AlphaLoadTimeline.LAYER_FADE_TICKS / 2)
				< AlphaLoadTimeline.scanlineAlpha(AlphaLoadTimeline.GLITCH_START_TICK
						+ AlphaLoadTimeline.LAYER_FADE_TICKS));
		assertTrue(AlphaLoadTimeline.scanlineAlpha(AlphaLoadTimeline.FLOOD_START_TICK)
				> AlphaLoadTimeline.scanlineAlpha(AlphaLoadTimeline.FAILURE_TICK));
		assertTrue(AlphaLoadTimeline.chromaOffset(AlphaLoadTimeline.FLOOD_START_TICK)
				> AlphaLoadTimeline.chromaOffset(AlphaLoadTimeline.GLITCH_START_TICK + 1));

		// A frozen frame must be frozen in every layer, not just the one that remembered to check.
		assertEquals(AlphaLoadTimeline.trackingBandTop(AlphaLoadTimeline.FREEZE_START_TICK, 200),
				AlphaLoadTimeline.trackingBandTop(AlphaLoadTimeline.FREEZE_START_TICK + 7, 200));
		assertEquals(AlphaLoadTimeline.timecodeText(AlphaLoadTimeline.FREEZE_START_TICK),
				AlphaLoadTimeline.timecodeText(AlphaLoadTimeline.FREEZE_START_TICK + 7));

		// Dead air is an open channel, not an absence: the picture collapses to a line and what
		// replaces it is never fully black.
		assertEquals(0, AlphaLoadTimeline.deadAirNoiseAlpha(AlphaLoadTimeline.FREEZE_START_TICK));
		assertTrue(AlphaLoadTimeline.deadAirNoiseAlpha(AlphaLoadTimeline.BLACKOUT_START_TICK
				+ AlphaLoadTimeline.BLACKOUT_COLLAPSE_TICKS) > 0);
		assertEquals(1.0F, AlphaLoadTimeline.blackoutCollapseProgress(
				AlphaLoadTimeline.BLACKOUT_START_TICK));
		assertEquals(0.0F, AlphaLoadTimeline.blackoutCollapseProgress(
				AlphaLoadTimeline.BLACKOUT_START_TICK + AlphaLoadTimeline.BLACKOUT_COLLAPSE_TICKS));
		// Dead air stays dead: nothing is allowed to give the lost picture back before recovery.
		assertEquals(0, AlphaLoadTimeline.deadAirNoiseAlpha(
				AlphaLoadTimeline.LEGACY_RECOVERY_START_TICK));

		// The picture returns unlocked and settles, and never returns to a clean baseline.
		assertEquals(1.0F, AlphaLoadTimeline.recoveryLockStrength(
				AlphaLoadTimeline.LEGACY_RECOVERY_START_TICK));
		assertEquals(0.0F, AlphaLoadTimeline.recoveryLockStrength(
				AlphaLoadTimeline.LEGACY_RECOVERY_START_TICK + AlphaLoadTimeline.RECOVERY_LOCK_TICKS));
		assertTrue(AlphaLoadTimeline.scanlineAlpha(AlphaLoadTimeline.LEGACY_RECOVERY_START_TICK
				+ AlphaLoadTimeline.RECOVERY_LOCK_TICKS) > 0);
	}

	@Test
	void rampIsBoundedAndMonotonic() {
		assertEquals(0.0F, AlphaLoadTimeline.ramp(4, 10, 8));
		assertEquals(1.0F, AlphaLoadTimeline.ramp(18, 10, 8));
		assertEquals(1.0F, AlphaLoadTimeline.ramp(9_000, 10, 8));
		float previous = -1.0F;
		for (int tick = 10; tick <= 18; tick++) {
			float value = AlphaLoadTimeline.ramp(tick, 10, 8);
			assertTrue(value >= previous, "ramp must not go backwards at tick " + tick);
			assertTrue(value >= 0.0F && value <= 1.0F);
			previous = value;
		}
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
	void thePreludeIsLongEnoughToBeBelievedAndItsProgressBarLosesGround() {
		assertEquals(0, AlphaLoadTimeline.NORMAL_PROGRESS_END_TICK);
		// The whole sequence depends on the player settling into an ordinary wait first, which
		// one second never bought. Guard the floor rather than the exact value.
		assertTrue(AlphaLoadTimeline.NORMAL_PAUSE_TICKS >= 40,
				"The prelude must be long enough for the stalled bar to become a question");
		assertTrue(AlphaLoadTimeline.initialNormalFrame(0));
		assertFalse(AlphaLoadTimeline.initialNormalFrame(AlphaLoadTimeline.GLITCH_START_TICK));

		// It creeps up, so that what follows registers as a loss rather than as a value.
		assertEquals(0.5F, AlphaLoadTimeline.initialNormalProgress(0));
		float peak = AlphaLoadTimeline.initialNormalProgress(
				AlphaLoadTimeline.PROGRESS_REGRESSION_TICK);
		assertTrue(peak > 0.5F, "The bar must gain ground before it loses any: " + peak);

		// Then it slides back, and the slide is the point: a bar that snaps to a lower value
		// reads as one bad frame and is forgiven. Never rising, and never dropping far enough in
		// a single tick to be mistaken for a glitch.
		float previous = peak;
		for (int tick = AlphaLoadTimeline.PROGRESS_REGRESSION_TICK + 1;
				tick < AlphaLoadTimeline.GLITCH_START_TICK; tick++) {
			float value = AlphaLoadTimeline.initialNormalProgress(tick);
			assertTrue(value <= previous, "The bar must not recover at tick " + tick);
			assertTrue(previous - value < 0.05F,
					"The bar must slide rather than snap at tick " + tick + ": " + (previous - value));
			previous = value;
		}
		// It has to land exactly where the corruption phase pins the bar. Anything else turns the
		// handoff into the single jump this whole slide exists to avoid.
		assertEquals(0.5F, previous,
				"The prelude must hand the bar over at the value the corruption phase holds");
		assertTrue(peak > previous, "The bar must end lower than it reached: " + peak);

		// The slide has to finish, and be held, while the screen still looks ordinary - the whole
		// effect is that this happens before anything has visibly corrupted.
		assertTrue(AlphaLoadTimeline.PROGRESS_REGRESSION_TICK
				+ AlphaLoadTimeline.PROGRESS_REGRESSION_SLIDE_TICKS
				< AlphaLoadTimeline.GLITCH_START_TICK,
				"The bar must finish losing ground before the first glitch");
	}

	@Test
	void theRecordingAdmitsItSeesThePlayerOnceMoreOnAFrameThatHasStopped() {
		// Not the instant the frame locks: the freeze has to be established as a freeze first.
		assertFalse(AlphaLoadTimeline.frozenObserverVisible(
				AlphaLoadTimeline.FREEZE_START_TICK));
		assertTrue(AlphaLoadTimeline.frozenObserverVisible(AlphaLoadTimeline.FREEZE_START_TICK
				+ AlphaLoadTimeline.FROZEN_OBSERVER_DELAY_TICKS));
		// It belongs to the frozen wall and must not survive it.
		assertFalse(AlphaLoadTimeline.frozenObserverVisible(
				AlphaLoadTimeline.BLACKOUT_START_TICK));
		assertFalse(AlphaLoadTimeline.frozenObserverVisible(
				AlphaLoadTimeline.OBSERVER_MESSAGE_START_TICK));
		// Every other layer is locked while it shows, which is the entire point.
		int shown = AlphaLoadTimeline.FREEZE_START_TICK
				+ AlphaLoadTimeline.FROZEN_OBSERVER_DELAY_TICKS;
		assertEquals(AlphaLoadTimeline.failureMotionTick(AlphaLoadTimeline.FREEZE_START_TICK),
				AlphaLoadTimeline.failureMotionTick(shown));
	}

	@Test
	void deadAirTakesOverTheWindowTitleAndTheRecoveredBarGivesItselfAway() {
		// The title bar is the only channel that survives dead air, so it carries it alone.
		assertFalse(AlphaLoadTimeline.deadAirWindowTitle(
				AlphaLoadTimeline.BLACKOUT_START_TICK - 1));
		assertTrue(AlphaLoadTimeline.deadAirWindowTitle(AlphaLoadTimeline.BLACKOUT_START_TICK));
		assertFalse(AlphaLoadTimeline.deadAirWindowTitle(
				AlphaLoadTimeline.LEGACY_RECOVERY_START_TICK),
				"1.0.0 must own the frame the picture returns, not a dead-air placeholder");

		// The fault lands after the screen has had time to look trustworthy again, and before
		// the player is allowed to leave - a fault nobody is still watching is not a fault.
		assertTrue(AlphaLoadTimeline.recoveryFaultTick() > AlphaLoadTimeline.LEGACY_RECOVERY_START_TICK
				+ AlphaLoadTimeline.RECOVERY_LOCK_TICKS,
				"The bar must fail only after the tracking has settled");
		assertTrue(AlphaLoadTimeline.recoveryFaultTick() < AlphaLoadTimeline.MIN_LOADING_SCREEN_TICKS,
				"The fault must be visible before the screen may close");
		assertFalse(AlphaLoadTimeline.recoveryProgressFault(
				AlphaLoadTimeline.recoveryFaultTick() - 1));
		assertTrue(AlphaLoadTimeline.recoveryProgressFault(AlphaLoadTimeline.recoveryFaultTick()));
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
		// Dead air is a beat, not a wait. With nothing rendered inside it, past about a second
		// and a half it stops reading as a lost signal and starts reading as a hang.
		assertTrue(AlphaLoadTimeline.BLACKOUT_TICKS >= 20 && AlphaLoadTimeline.BLACKOUT_TICKS <= 32,
				"Dead air length: " + AlphaLoadTimeline.BLACKOUT_TICKS);
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
