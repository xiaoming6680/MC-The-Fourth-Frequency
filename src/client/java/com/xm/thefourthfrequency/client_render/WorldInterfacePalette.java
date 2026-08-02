package com.xm.thefourthfrequency.client_render;

import com.xm.thefourthfrequency.networking.WorldInterfaceProtocol;

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
