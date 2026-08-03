package com.xm.thefourthfrequency.entity;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * The one place that knows where the interface's parts actually sit in the world.
 *
 * <p>Everything the boss emits — the laser, the anchor tethers, the orb feed, the debris storm —
 * used to start at {@code getEyePosition()}, which is the middle of the collision box: several
 * blocks behind and below the glowing core the player is aiming at. At third form that is an
 * eleven-block error, and the beam visibly left the body out of empty plating.
 *
 * <p>The model bakes the core at a fixed offset, so the offset is published here instead of being
 * guessed independently on each side. Server damage geometry and client beam geometry then agree by
 * construction rather than by coincidence; if the model moves, this file moves with it and both
 * follow.
 */
public final class WorldInterfaceAnatomy {
	/**
	 * Per-form model scale. {@code WorldInterfaceRenderer} reads this rather than restating it.
	 *
	 * <p>The third form ran at sixteen, which put it thirty-three blocks across and some forty tall.
	 * At that size it stopped being a thing in the arena and became the arena's ceiling: it filled
	 * the screen from any distance a player could still see their own feet, and the parts of it a
	 * player could reach were a small fraction of what they were looking at. Twelve keeps it half
	 * again the second form and unmistakably the largest thing in the fight without taking the sky.
	 */
	public static final float[] FORM_SCALE = {5.8F, 10.0F, 12.0F};
	public static final int FORM_COUNT = FORM_SCALE.length;

	/** Vanilla drops every living model by this much before drawing it. */
	private static final double MODEL_ORIGIN_LIFT = 1.501D;
	private static final double UNITS_PER_BLOCK = 16.0D;
	/** Core centre in model units off the layer root: body(0,12,0) plus eye(0,-13,-11). */
	private static final double CORE_Y_UNITS = -1.0D;
	private static final double CORE_Z_UNITS = -11.0D;
	/** Half-width of the mass alone, tentacles excluded; the storm orbits this. */
	private static final double[] MASS_HALF_WIDTH_UNITS = {9.0D, 13.0D, 16.5D};
	/** Radius of the glowing disc itself, for muzzle bloom and orb feed anchoring. */
	private static final double[] CORE_RADIUS_UNITS = {4.2D, 6.2D, 8.4D};

	/** Limbs {@code WorldInterfaceModel} actually draws per form. The hit proxies stand on these. */
	private static final int[] TENTACLE_COUNT = {4, 6, 10};
	/** Where the limbs leave the body: {@code buildTendrils} hangs them off body(12) at -4. */
	private static final double TENTACLE_ROOT_UNITS = 8.0D;
	/**
	 * Where the longest limb ends, in model units off the layer root. Each limb is a three-link
	 * chain that curls as it descends, so this is the chain length already projected back onto the
	 * vertical rather than the sum of the link lengths.
	 */
	private static final double[] TENTACLE_TIP_UNITS = {40.0D, 46.0D, 53.0D};
	/** How far off the body axis the limbs hang. */
	private static final double TENTACLE_RADIUS_UNITS = 11.5D;
	/** Half-thickness of a limb's first link. */
	private static final double[] TENTACLE_THICKNESS_UNITS = {0.85D, 1.25D, 1.75D};
	/** A hit column is this much wider than the limb it stands on; see {@link #tentacleHitWidth}. */
	private static final double TENTACLE_HIT_SLACK = 2.5D;
	/**
	 * Blocks between a hunted player's feet and the underside of the drawn mass, per form.
	 *
	 * <p>This used to be stated as the altitude of the entity's own origin, which is also where the
	 * collision box started - and the drawn mass does not start there. It floats {@link
	 * #massBottomLift} blocks higher, eight and a half of them at third form, so the box claimed a
	 * storey of empty air under the body and the body itself sat that far above everything a player
	 * could see themselves hitting.</p>
	 *
	 * <p>Stated against the mass instead, the number means what it looks like: how far over your
	 * head the thing hanging in the sky actually hangs.</p>
	 *
	 * <p>The first two forms stay inside a swing from the ground - eye height plus three blocks of
	 * reach is about four and a half - so melee keeps hitting the body itself. The third still does
	 * not, deliberately: it looms rather than hangs, and melee has the ten limb proxies for that.
	 * The clearance tracks the body size, though. Nine blocks was set against a thirty-three block
	 * body; holding it there once the body came down to twenty-five would have left the third form
	 * further out of reach than the size cut was meant to leave it.
	 */
	private static final double[] COMBAT_MASS_CLEARANCE = {1.8D, 3.8D, 6.5D};

	private WorldInterfaceAnatomy() {
	}

	public static int tentacleCount(int form) {
		return TENTACLE_COUNT[Math.clamp(form, 0, FORM_COUNT - 1)];
	}

	/** Blocks between the entity position and the tentacle tips. Always positive: the limbs hang. */
	public static double tentacleDrop(int form) {
		return formScale(form) * (TENTACLE_TIP_UNITS[Math.clamp(form, 0, FORM_COUNT - 1)]
				/ UNITS_PER_BLOCK - MODEL_ORIGIN_LIFT);
	}

	/** Blocks between the entity position and where the limbs leave the body. */
	public static double tentacleRootLift(int form) {
		return formScale(form) * (MODEL_ORIGIN_LIFT - TENTACLE_ROOT_UNITS / UNITS_PER_BLOCK);
	}

	public static double tentacleRadius(int form) {
		return formScale(form) * TENTACLE_RADIUS_UNITS / UNITS_PER_BLOCK;
	}

	/**
	 * Width of a limb's hit column, deliberately wider than the drawn limb. The chain curls and
	 * swings as it descends, so a column matched to the bare thickness would be a moving needle.
	 */
	public static double tentacleHitWidth(int form) {
		return Math.max(2.0D, formScale(form) * TENTACLE_THICKNESS_UNITS[Math.clamp(form, 0, FORM_COUNT - 1)]
				* 2.0D / UNITS_PER_BLOCK * TENTACLE_HIT_SLACK);
	}

	/**
	 * Offset from a hunted player's feet to the interface's own origin.
	 *
	 * <p>Derived from {@link #COMBAT_MASS_CLEARANCE} rather than stated, so the clearance a designer
	 * reads is the clearance the player sees. Negative at every form above the first: the origin is
	 * a bookkeeping point under the arena floor and the body it carries is overhead.</p>
	 */
	public static double combatHoverHeight(int form) {
		return COMBAT_MASS_CLEARANCE[Math.clamp(form, 0, FORM_COUNT - 1)] - massBottomLift(form);
	}

	public static float formScale(int form) {
		return FORM_SCALE[Math.clamp(form, 0, FORM_COUNT - 1)];
	}

	/** Server-side core position, using the boss's settled body facing. */
	public static Vec3 coreOrigin(WorldInterfaceEntity boss) {
		return coreOrigin(boss.position(), boss.form(), boss.yBodyRot);
	}

	/**
	 * Core position for an arbitrary foot position and body facing. The model faces its own -Z, so
	 * the forward term rides the body yaw rather than the head yaw: the core is part of the torso and
	 * does not swing when the interface merely looks somewhere.
	 */
	public static Vec3 coreOrigin(Vec3 feet, int form, float bodyYawDegrees) {
		float scale = formScale(form);
		double lift = coreLift(form);
		double forward = scale * (-CORE_Z_UNITS / UNITS_PER_BLOCK);
		float radians = bodyYawDegrees * Mth.DEG_TO_RAD;
		return new Vec3(feet.x - Mth.sin(radians) * forward, feet.y + lift,
				feet.z + Mth.cos(radians) * forward);
	}

	/** Radius of the glowing disc in blocks. */
	public static double coreRadius(int form) {
		return formScale(form) * CORE_RADIUS_UNITS[Math.clamp(form, 0, FORM_COUNT - 1)] / UNITS_PER_BLOCK;
	}

	/** Blocks between the entity position and the centre of the drawn mass. */
	public static double coreLift(int form) {
		return formScale(form) * (MODEL_ORIGIN_LIFT - CORE_Y_UNITS / UNITS_PER_BLOCK);
	}

	/**
	 * Width of the collision box, in blocks: the drawn mass, and nothing else.
	 *
	 * <p>The box used to be three hand-written numbers that had drifted badly out of step with the
	 * model — 10 blocks against a sixteen-block-wide second form, 16 against a thirty-three-block
	 * third form. Half the interface a player could see was not there to hit, and shots that
	 * visibly connected passed through it. Derived from the same half-widths the debris storm
	 * orbits, so the box and the silhouette can no longer disagree.</p>
	 */
	public static float hitboxWidth(int form) {
		return (float) (massRadius(form) * 2.0D);
	}

	/**
	 * Height of the collision box, in blocks: the drawn mass, top to bottom, and nothing else.
	 *
	 * <p>The box used to grow upward from the entity position, which meant it also swallowed the
	 * {@link #massBottomLift} blocks of empty air between that position and the underside of the
	 * body - eight and a half of them at third form. {@link WorldInterfaceEntity} lifts the box off
	 * the position by exactly that much, so what is left here is the body's own extent. The limbs
	 * hanging below are covered by their own hit proxies rather than by this.</p>
	 */
	public static float hitboxHeight(int form) {
		return (float) (massRadius(form) * 2.0D);
	}

	/**
	 * Blocks between the entity position and the underside of the drawn mass.
	 *
	 * <p>The vertical offset the collision box is lifted by. The mass is treated as being as tall as
	 * it is wide, which is the same approximation {@link #hitboxWidth} already makes in the other
	 * direction, so the top of the box lands exactly where it always did and only the floor moves.
	 */
	public static double massBottomLift(int form) {
		return coreLift(form) - massRadius(form);
	}

	/** Half-width of the mass in blocks, excluding tentacles. */
	public static double massRadius(int form) {
		return formScale(form) * MASS_HALF_WIDTH_UNITS[Math.clamp(form, 0, FORM_COUNT - 1)]
				/ UNITS_PER_BLOCK;
	}
}
