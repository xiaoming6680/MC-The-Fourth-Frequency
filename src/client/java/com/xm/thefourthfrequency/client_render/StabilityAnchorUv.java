package com.xm.thefourthfrequency.client_render;

/**
 * The stability anchor's texture layout: eight uniform 32x32 material islands on a 128x128 sheet.
 *
 * <p>Deliberately materials rather than per-part unwraps. The structure is thirty-odd boxes made of
 * six substances, and painting a bespoke island for each box would have spent the whole sheet
 * restating "this is obsidian" thirty times over. Every island is a flat, self-similar patch, so a
 * box may unwrap anywhere inside its own island and still sample the right material.
 *
 * <p>The one constraint that has to hold is arithmetic: a box of size {@code (w, h, d)} unwraps into
 * a footprint {@code 2(d + w)} wide by {@code d + h} tall, and that footprint has to fit inside the
 * 32-unit island. {@link #fits} states it so the model's own contract test can check every box
 * rather than trusting the eye.
 */
public final class StabilityAnchorUv {
	public static final int SHEET_WIDTH = 128;
	public static final int SHEET_HEIGHT = 128;
	public static final int ISLAND = 32;

	/** Near-black obsidian: the torso column, its plinth and its cap. */
	public static final int OBSIDIAN_U = 0;
	public static final int OBSIDIAN_V = 0;
	/** Cold bone ash: the four radial claws' arms and forearms. */
	public static final int CLAW_U = 32;
	public static final int CLAW_V = 0;
	/** Dim violet: every pivot and wrist axle. */
	public static final int JOINT_U = 64;
	public static final int JOINT_V = 0;
	/** Restrained ancient gold: seams only, and the only non-core material allowed any glow. */
	public static final int GOLD_U = 96;
	public static final int GOLD_V = 0;
	/** Platinum white: the chest core and the bare relay core. */
	public static final int CORE_U = 0;
	public static final int CORE_V = 32;
	/** Calibration petals: pale metal flecked with gold. */
	public static final int PETAL_U = 32;
	public static final int PETAL_V = 32;
	/** The thin axle under the relay core. */
	public static final int SPINDLE_U = 64;
	public static final int SPINDLE_V = 32;
	/** The gripping pads that wrap the bedrock cap; darker and rougher than the arms. */
	public static final int FOOT_U = 96;
	public static final int FOOT_V = 32;

	private StabilityAnchorUv() {
	}

	/** Whether a box of this size unwraps inside a single 32x32 island placed at the island origin. */
	public static boolean fits(float width, float height, float depth) {
		if (!Float.isFinite(width) || !Float.isFinite(height) || !Float.isFinite(depth)
				|| width < 0.0F || height < 0.0F || depth < 0.0F) {
			throw new IllegalArgumentException("Box dimensions must be finite and non-negative");
		}
		return 2.0F * (depth + width) <= ISLAND && depth + height <= ISLAND;
	}
}
