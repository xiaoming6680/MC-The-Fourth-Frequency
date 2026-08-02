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
	private static final List<String> DOWNGRADE_VERSIONS = List.of(
			"1.20.1", "1.16.5", "1.12.2", "1.8.9", "1.7.3", "1.0.0");
	/**
	 * The tick each window-title version becomes current, indexed by stage.
	 *
	 * <p>An evenly spaced countdown would drift out of phase with what the screen is doing, and
	 * the arithmetic that produced it put the final step - the only one the whole sequence exists
	 * to deliver - inside the blackout, where nobody could read it. Every step is pinned to the
	 * event the player is already looking at instead, and 1.0.0 lands on the frame the picture
	 * comes back, so the title and the world agree about what happened at the same instant.</p>
	 */
	private static final int[] TITLE_STAGE_TICKS = {
			0,
			GLITCH_START_TICK,
			FAILURE_TICK,
			OBSERVER_MESSAGE_START_TICK,
			FLOOD_START_TICK,
			FREEZE_START_TICK,
			LEGACY_RECOVERY_START_TICK
	};

	private AlphaLoadTimeline() {
	}

	public static int versionStage(int screenTicks) {
		for (int stage = TITLE_STAGE_TICKS.length - 1; stage > 0; stage--) {
			if (screenTicks >= TITLE_STAGE_TICKS[stage]) return Math.min(stage, finalVersionStage());
		}
		return 0;
	}

	/** The tick at which {@code stage} becomes the current window title. */
	public static int versionStageTick(int stage) {
		return TITLE_STAGE_TICKS[Math.clamp(stage, 0, TITLE_STAGE_TICKS.length - 1)];
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

	/*
	 * ---------------------------------------------------------------------------------------
	 * The medium layer.
	 *
	 * Everything below describes the tape rather than the message: scanlines, chroma bleed, a
	 * tracking band, a timecode, dead air. They are pure functions of the screen tick so the
	 * whole sequence can be verified without a running client, and so a frozen frame is frozen
	 * in every layer at once rather than only in the one that remembered to check.
	 *
	 * Each layer ramps across a transition instead of switching on. A hard cut announces that
	 * something was drawn on top of the picture; a ramp reads as the picture itself degrading,
	 * which is the entire effect. The ramps deliberately overlap the events they belong to, so
	 * no two layers arrive on the same frame.
	 * ---------------------------------------------------------------------------------------
	 */

	/** How long a layer takes to reach full strength once its event fires. */
	public static final int LAYER_FADE_TICKS = 10;
	/** The wall does not appear, it wipes outward from the middle of the screen. */
	public static final int FLOOD_WIPE_TICKS = 6;
	/** Old sets collapsed the picture to a bright line before losing it altogether. */
	public static final int BLACKOUT_COLLAPSE_TICKS = 5;
	/** After dead air the picture does not simply exist again; it has to find its lock. */
	public static final int RECOVERY_LOCK_TICKS = 24;
	public static final int SCANLINE_SPACING = 3;
	private static final int PEAK_SCANLINE_ALPHA = 66;
	private static final int GLITCH_SCANLINE_ALPHA = 28;
	private static final int RESIDUAL_SCANLINE_ALPHA = 16;
	private static final float PEAK_CHROMA_OFFSET = 3.0F;
	private static final int TRACKING_BAND_SPEED = 3;
	private static final int TRACKING_BAND_MIN_HEIGHT = 5;
	private static final int TRACKING_BAND_MAX_HEIGHT = 19;
	private static final int TRACKING_BAND_MAX_SHIFT = 13;
	private static final int TICKS_PER_TIMECODE_SECOND = 20;
	private static final int TIMECODE_FRAMES_PER_SECOND = 30;

	/** 0 before {@code startTick}, 1 once {@code lengthTicks} have passed, smoothstep between. */
	public static float ramp(int screenTicks, int startTick, int lengthTicks) {
		if (lengthTicks <= 0) return screenTicks >= startTick ? 1.0F : 0.0F;
		float linear = Math.clamp((screenTicks - startTick) / (float) lengthTicks, 0.0F, 1.0F);
		return linear * linear * (3.0F - 2.0F * linear);
	}

	/** Deterministic per-tick noise shared by every layer, so one seed drives the whole frame. */
	public static int noise(int value) {
		value ^= value >>> 16;
		value *= 0x7FEB352D;
		value ^= value >>> 15;
		value *= 0x846CA68B;
		return value ^ value >>> 16;
	}

	public static int scanlineAlpha(int screenTicks) {
		if (screenTicks < GLITCH_START_TICK) return 0;
		if (screenTicks >= LEGACY_RECOVERY_START_TICK) {
			// The tape never becomes clean again; it settles onto a floor it keeps from here on.
			float settled = ramp(screenTicks, LEGACY_RECOVERY_START_TICK, RECOVERY_LOCK_TICKS);
			return Math.round(lerp(PEAK_SCANLINE_ALPHA, RESIDUAL_SCANLINE_ALPHA, settled));
		}
		if (screenTicks >= BLACKOUT_START_TICK) {
			return Math.round(PEAK_SCANLINE_ALPHA
					* (1.0F - ramp(screenTicks, BLACKOUT_START_TICK, BLACKOUT_COLLAPSE_TICKS)));
		}
		float arrival = ramp(screenTicks, GLITCH_START_TICK, LAYER_FADE_TICKS);
		float escalation = ramp(screenTicks, FAILURE_TICK, FLOOD_START_TICK - FAILURE_TICK);
		return Math.round(GLITCH_SCANLINE_ALPHA * arrival
				+ (PEAK_SCANLINE_ALPHA - GLITCH_SCANLINE_ALPHA) * escalation);
	}

	/** Horizontal separation, in pixels, between the red and cyan ghosts of any drawn text. */
	public static float chromaOffset(int screenTicks) {
		if (screenTicks < GLITCH_START_TICK) return 0.0F;
		if (screenTicks >= LEGACY_RECOVERY_START_TICK) {
			return PEAK_CHROMA_OFFSET * 0.25F
					* (1.0F - ramp(screenTicks, LEGACY_RECOVERY_START_TICK, RECOVERY_LOCK_TICKS));
		}
		if (screenTicks >= BLACKOUT_START_TICK) return 0.0F;
		float arrival = ramp(screenTicks, GLITCH_START_TICK, LAYER_FADE_TICKS);
		float escalation = ramp(screenTicks, FAILURE_TICK, FLOOD_START_TICK - FAILURE_TICK);
		return PEAK_CHROMA_OFFSET * (0.34F * arrival + 0.66F * escalation);
	}

	public static boolean trackingBandVisible(int screenTicks) {
		return screenTicks >= GLITCH_START_TICK && screenTicks < BLACKOUT_START_TICK;
	}

	public static int trackingBandHeight(int screenTicks) {
		float growth = ramp(screenTicks, GLITCH_START_TICK, FLOOD_START_TICK - GLITCH_START_TICK);
		return Math.round(lerp(TRACKING_BAND_MIN_HEIGHT, TRACKING_BAND_MAX_HEIGHT, growth));
	}

	/**
	 * Top edge of the band, scrolling upward. Uses the frozen motion tick so a locked frame locks
	 * the band with it - a tracking error that kept moving over a still picture would read as an
	 * overlay rather than as damage to the picture.
	 */
	public static int trackingBandTop(int screenTicks, int viewportHeight) {
		if (!trackingBandVisible(screenTicks) || viewportHeight <= 0) return Integer.MIN_VALUE;
		int travel = Math.max(0, failureMotionTick(screenTicks) - GLITCH_START_TICK)
				* TRACKING_BAND_SPEED;
		int span = viewportHeight + TRACKING_BAND_MAX_HEIGHT;
		return viewportHeight - Math.floorMod(travel, span);
	}

	/** How far the picture inside the band is dragged sideways. */
	public static int trackingBandShift(int screenTicks) {
		if (!trackingBandVisible(screenTicks)) return 0;
		int motion = failureMotionTick(screenTicks);
		float strength = ramp(screenTicks, GLITCH_START_TICK, FLOOD_START_TICK - GLITCH_START_TICK);
		int wobble = Math.floorMod(noise(motion / 2 + 0x2545F491), 2 * TRACKING_BAND_MAX_SHIFT + 1)
				- TRACKING_BAND_MAX_SHIFT;
		return Math.round(wobble * strength);
	}

	public static boolean timecodeVisible(int screenTicks) {
		return screenTicks >= GLITCH_START_TICK && screenTicks < BLACKOUT_START_TICK;
	}

	/** Frames elapsed on the counter, at a tape frame rate rather than the game's tick rate. */
	public static int timecodeFrames(int screenTicks) {
		int motion = Math.max(0, failureMotionTick(screenTicks));
		return motion * TIMECODE_FRAMES_PER_SECOND / TICKS_PER_TIMECODE_SECOND;
	}

	public static String timecodeText(int screenTicks) {
		int frames = timecodeFrames(screenTicks);
		int seconds = frames / TIMECODE_FRAMES_PER_SECOND;
		return String.format("%02d:%02d:%02d", seconds / 60, seconds % 60,
				frames % TIMECODE_FRAMES_PER_SECOND);
	}

	/** The counter stops trusting itself once the wall is up, and starts dropping digits. */
	public static boolean timecodeCorrupted(int screenTicks) {
		return screenTicks >= FLOOD_START_TICK
				&& Math.floorMod(noise(failureMotionTick(screenTicks) / 2), 5) == 0;
	}

	/** 0 while the wall is still wiping outward from the middle, 1 once it covers the viewport. */
	public static float floodWipeProgress(int screenTicks) {
		return ramp(screenTicks, FLOOD_START_TICK, FLOOD_WIPE_TICKS);
	}

	/** 1 while the picture is still collapsing toward a line, 0 once it is gone. */
	public static float blackoutCollapseProgress(int screenTicks) {
		if (!blackoutFrame(screenTicks)) return 0.0F;
		return 1.0F - ramp(screenTicks, BLACKOUT_START_TICK, BLACKOUT_COLLAPSE_TICKS);
	}

	public static int deadAirNoiseAlpha(int screenTicks) {
		if (!blackoutFrame(screenTicks)) return 0;
		// Dead air is not an absence of picture, it is a picture of nothing. Never fully still.
		float settled = ramp(screenTicks, BLACKOUT_START_TICK, BLACKOUT_COLLAPSE_TICKS);
		return Math.round((9 + Math.floorMod(noise(screenTicks), 12)) * settled);
	}

	/** 1 on the frame the picture returns, decaying to 0 as the tracking finds its lock. */
	public static float recoveryLockStrength(int screenTicks) {
		if (screenTicks < LEGACY_RECOVERY_START_TICK) return 0.0F;
		return 1.0F - ramp(screenTicks, LEGACY_RECOVERY_START_TICK, RECOVERY_LOCK_TICKS);
	}

	private static float lerp(float from, float to, float progress) {
		return from + (to - from) * Math.clamp(progress, 0.0F, 1.0F);
	}

	public static boolean mayCloseLoadingScreen(int screenTicks, boolean resourceReloadFinished,
			boolean viewportFlooded) {
		if (screenTicks < MIN_LOADING_SCREEN_TICKS) return false;
		if (!viewportFlooded && screenTicks < MAX_RESOURCE_RELOAD_WAIT_TICKS) return false;
		return resourceReloadFinished || screenTicks >= MAX_RESOURCE_RELOAD_WAIT_TICKS;
	}
}
