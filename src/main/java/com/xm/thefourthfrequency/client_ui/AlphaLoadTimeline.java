package com.xm.thefourthfrequency.client_ui;

import java.util.List;

public final class AlphaLoadTimeline {
	/*
	 * The real first-entry progress is already capped at 50% before the session is
	 * claimed. Keep only the one-second pause here instead of replaying 0 -> 50%.
	 */
	public static final int NORMAL_PROGRESS_END_TICK = 0;
	public static final int NORMAL_PAUSE_TICKS = 20;
	public static final int GLITCH_START_TICK = NORMAL_PROGRESS_END_TICK + NORMAL_PAUSE_TICKS;
	public static final int FAILURE_TICK = GLITCH_START_TICK + 24;
	public static final int OBSERVER_MESSAGE_START_TICK = FAILURE_TICK + 24;
	public static final int OBSERVER_MESSAGE_END_TICK = FAILURE_TICK + 48;
	public static final int MAX_FAILURE_COPIES = 28;
	public static final int FLOOD_START_TICK = FAILURE_TICK + 52;
	public static final int ACTIVE_FAILURE_WALL_TICKS = 28;
	public static final int FROZEN_FAILURE_WALL_TICKS = 12;
	public static final int FLOOD_COMPLETE_TICK = FLOOD_START_TICK + ACTIVE_FAILURE_WALL_TICKS;
	public static final int FREEZE_START_TICK = FLOOD_COMPLETE_TICK;
	public static final int BLACKOUT_START_TICK =
			FREEZE_START_TICK + FROZEN_FAILURE_WALL_TICKS;
	public static final int BLACKOUT_TICKS = 40;
	public static final int LEGACY_RECOVERY_START_TICK = BLACKOUT_START_TICK + BLACKOUT_TICKS;
	public static final int MIN_LOADING_SCREEN_TICKS = LEGACY_RECOVERY_START_TICK + 50;
	public static final int MAX_RESOURCE_RELOAD_WAIT_TICKS = MIN_LOADING_SCREEN_TICKS + 64;
	public static final int VERSION_STEP_TICKS = 28;
	private static final List<String> DOWNGRADE_VERSIONS = List.of(
			"1.20.1", "1.16.5", "1.12.2", "1.8.9", "1.7.3", "1.0.0");

	private AlphaLoadTimeline() {
	}

	public static int versionStage(int screenTicks) {
		return Math.clamp(Math.max(0, screenTicks) / VERSION_STEP_TICKS, 0, finalVersionStage());
	}

	public static int finalVersionStage() {
		return DOWNGRADE_VERSIONS.size();
	}

	public static String versionAt(int stage, String launchedVersion) {
		int safeStage = Math.clamp(stage, 0, finalVersionStage());
		return safeStage == 0 ? launchedVersion : DOWNGRADE_VERSIONS.get(safeStage - 1);
	}

	public static String windowContextKey(boolean singleplayer) {
		return singleplayer
				? "window.thefourthfrequency.alpha_load.singleplayer"
				: "window.thefourthfrequency.alpha_load.multiplayer";
	}

	public static boolean initialNormalFrame(int screenTicks) {
		return screenTicks < GLITCH_START_TICK;
	}

	public static boolean observerMessageVisible(int screenTicks) {
		if (screenTicks < OBSERVER_MESSAGE_START_TICK
				|| screenTicks >= OBSERVER_MESSAGE_END_TICK) return false;
		int age = screenTicks - OBSERVER_MESSAGE_START_TICK;
		return age < 8 || Math.floorMod(age, 7) != 1;
	}

	public static float initialNormalProgress(int screenTicks) {
		return 0.5F;
	}

	public static int copiedFailureLines(int screenTicks) {
		if (screenTicks < FAILURE_TICK) return 0;
		float progress = Math.clamp((screenTicks - FAILURE_TICK)
				/ (float) (FLOOD_START_TICK - FAILURE_TICK), 0.0F, 1.0F);
		return 1 + Math.round((float) Math.sqrt(progress) * (MAX_FAILURE_COPIES - 1));
	}

	public static boolean fullScreenFailureWall(int screenTicks) {
		return screenTicks >= FLOOD_START_TICK
				&& screenTicks < BLACKOUT_START_TICK;
	}

	public static boolean frozenFailureFrame(int screenTicks) {
		return screenTicks >= FREEZE_START_TICK && screenTicks < BLACKOUT_START_TICK;
	}

	public static boolean blackoutFrame(int screenTicks) {
		return screenTicks >= BLACKOUT_START_TICK
				&& screenTicks < LEGACY_RECOVERY_START_TICK;
	}

	public static boolean legacyRecoveryFrame(int screenTicks) {
		return screenTicks >= LEGACY_RECOVERY_START_TICK;
	}

	public static int failureMotionTick(int screenTicks) {
		return Math.min(screenTicks, FREEZE_START_TICK);
	}

	public static boolean mayCloseLoadingScreen(int screenTicks, boolean resourceReloadFinished,
			boolean viewportFlooded) {
		if (screenTicks < MIN_LOADING_SCREEN_TICKS) return false;
		if (!viewportFlooded && screenTicks < MAX_RESOURCE_RELOAD_WAIT_TICKS) return false;
		return resourceReloadFinished || screenTicks >= MAX_RESOURCE_RELOAD_WAIT_TICKS;
	}
}
