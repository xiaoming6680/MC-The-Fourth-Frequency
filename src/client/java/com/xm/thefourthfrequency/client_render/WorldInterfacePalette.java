package com.xm.thefourthfrequency.client_render;

import com.xm.thefourthfrequency.ending.WorldInterfaceActionScheduler;
import com.xm.thefourthfrequency.networking.WorldInterfaceProtocol;
import net.minecraft.util.Mth;

/**
 * Single source for the encounter's purple to gold to red escalation language.
 *
 * <p>The gateway states already spoke this palette; routing the eye glow, the laser and the anchor
 * tethers through the same three bands keeps "it is getting worse" readable without a HUD string.</p>
 */
public final class WorldInterfacePalette {
	public static final int PHASE_BAND_COUNT = 3;

	private static final float[] RED = {0.64F, 1.00F, 1.00F};
	private static final float[] GREEN = {0.18F, 0.72F, 0.22F};
	private static final float[] BLUE = {0.96F, 0.24F, 0.28F};
	/** Radians per tick for the idle glow breath; the interface grows more agitated per band. */
	private static final float[] BREATH = {0.13F, 0.19F, 0.27F};

	private WorldInterfacePalette() {
	}

	/**
	 * Maps a stage onto one of the three escalation bands. Pre-combat stages read as band 0 and both
	 * resolutions hold band 2, so the palette never snaps back to calm once the fight has ended.
	 */
	public static int band(WorldInterfaceProtocol.Stage stage) {
		if (stage == null) return 0;
		return switch (stage) {
			case PHASE_2 -> 1;
			case PHASE_3, SUCCESS_RESOLUTION, FAILURE_RESOLUTION, PORTAL_OPEN, COMPLETE -> 2;
			default -> 0;
		};
	}

	/**
	 * A sawtooth on the third form's volley clock: zero just after one salvo, one just before the
	 * next.
	 *
	 * <p>The third phase fires on a fixed interval, and nothing on the body said so - the volleys
	 * arrived out of a body that looked exactly the same a second earlier, which is most of why the
	 * last phase read as random rather than as fast. Ramping the emissive brightness along this
	 * makes the boss visibly charge before every salvo, so the rhythm becomes something a player can
	 * play against instead of something that happens to them.
	 *
	 * <p>Deliberately a modulation rather than a fourth palette band: {@code PHASE_BAND_COUNT} is 3
	 * and stays 3.
	 */
	public static float volleyRamp(float ageInTicks) {
		float interval = WorldInterfaceActionScheduler.VOLLEY_INTERVAL_TICKS;
		float phase = (ageInTicks % interval) / interval;
		// Eased so the last third of the wind-up is where most of the brightening happens.
		return phase * phase;
	}

	/**
	 * The same volley clock as {@link #volleyRamp}, but continuous.
	 *
	 * <p><b>Anything covering a large part of the screen must use this one.</b> {@code volleyRamp} is
	 * a sawtooth: it climbs to 1 and drops to 0 between two consecutive ticks, every two seconds.
	 * That discontinuity is invisible on a small piece of geometry - the kernel lattice reads it as
	 * "charged, then discharged", which is exactly right - but driving a full-screen parameter with
	 * it means the whole frame changes at once on that tick.
	 *
	 * <p>It did. The atmosphere controller fed the sawtooth into fog distance and sky tint, both of
	 * which cover the entire view, so every two seconds a large area of the screen shifted colour
	 * and visibility in a single frame. Measured at up to 38% of the frame changing solidly between
	 * ticks; with this substituted it drops to under 3%, which is ordinary animation.
	 *
	 * <p>A raised cosine has the same period and peak but no discontinuity anywhere, including at
	 * the wrap.
	 */
	public static float volleyBreath(float ageInTicks) {
		float interval = WorldInterfaceActionScheduler.VOLLEY_INTERVAL_TICKS;
		float phase = (ageInTicks % interval) / interval;
		return 0.5F - 0.5F * Mth.cos(phase * Mth.TWO_PI);
	}

	/**
	 * The third form's red shift, applied on top of a band rather than as a band of its own.
	 *
	 * <p>{@code PHASE_BAND_COUNT} is asserted at three, and correctly: a fourth band would mean
	 * every consumer of this palette had to learn a new state. Berserk is not a new state, it is
	 * the third one pushed harder.
	 */
	public static float redBerserk(int band, float intensity) {
		float base = RED[clampBand(band)];
		return Math.min(1.0F, base + (1.0F - base) * Math.clamp(intensity, 0.0F, 1.0F));
	}

	public static float red(int band) {
		return RED[clampBand(band)];
	}

	public static float green(int band) {
		return GREEN[clampBand(band)];
	}

	public static float blue(int band) {
		return BLUE[clampBand(band)];
	}

	public static float breathSpeed(int band) {
		return BREATH[clampBand(band)];
	}

	public static int red255(int band) {
		return Math.round(red(band) * 255.0F);
	}

	public static int green255(int band) {
		return Math.round(green(band) * 255.0F);
	}

	public static int blue255(int band) {
		return Math.round(blue(band) * 255.0F);
	}

	private static int clampBand(int band) {
		return Math.clamp(band, 0, PHASE_BAND_COUNT - 1);
	}
}
