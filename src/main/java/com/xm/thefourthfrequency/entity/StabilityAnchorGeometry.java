package com.xm.thefourthfrequency.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

/**
 * The one place the stability anchor's shape and timed collapse presentation are stated.
 *
 * <p>Three separate consumers have to agree about where the exposed relay core is: the model builds
 * geometry around it, {@code WorldInterfaceBeamBatchRenderer} starts the interface's tether there,
 * and the collapse effects implode into it. While that offset was a literal {@code +0.7} in the beam
 * renderer it described the old {@code EndCrystal} and nothing else, so the tether left the anchor
 * out of blank air well below the emitter. Everything reads {@link #RELAY_CORE_HEIGHT} now.
 *
 * <p>Model units are the usual sixteen to the block. The entity origin sits at the bottom face of
 * the air block above the spike's single bedrock cap, so model {@code y = 0} is the top of that cap:
 * positive model Y is downward, which is where the claws reach to wrap it.
 */
public final class StabilityAnchorGeometry {
	/** Collision width. The drawn claws span the same 28 model units, well inside two blocks. */
	public static final float WIDTH = 1.75F;
	public static final float HEIGHT = 2.75F;
	public static final float EYE_HEIGHT = 1.6F;

	public static final float UNITS_PER_BLOCK = 16.0F;
	/** Lowest and highest drawn model Y; the difference is the authored 44-unit total height. */
	public static final float LOWEST_MODEL_Y = 10.0F;
	public static final float HIGHEST_MODEL_Y = -34.0F;
	public static final float TOTAL_MODEL_HEIGHT = LOWEST_MODEL_Y - HIGHEST_MODEL_Y;
	/** Widest drawn extent, claw tip to claw tip, in model units. */
	public static final float TOTAL_MODEL_WIDTH = 28.0F;

	/** Model-space centre of the bare relay core, and of the chest core underneath it. */
	public static final float RELAY_CORE_MODEL_Y = -32.0F;
	public static final float CHEST_CORE_MODEL_Y = -13.0F;
	/** Blocks above the entity origin. Shared endpoint semantics for tether and collapse. */
	public static final double RELAY_CORE_HEIGHT = -RELAY_CORE_MODEL_Y / UNITS_PER_BLOCK;
	public static final double CHEST_CORE_HEIGHT = -CHEST_CORE_MODEL_Y / UNITS_PER_BLOCK;

	/** Total length of the destruction performance; the anchor is already destroyed at tick zero. */
	public static final int COLLAPSE_TICKS = 16;
	public static final int COLLAPSE_FRACTURE_END = 2;
	public static final int COLLAPSE_TETHER_END = 5;
	public static final int COLLAPSE_IMPLOSION_END = 11;
	/**
	 * Hard ceiling on client particles for one whole collapse. Ten anchors can fall inside a couple
	 * of seconds, so this is a budget rather than a taste call: at most 1000 motes arena-wide, which
	 * is the most {@code StabilityAnchorGeometryTest} will let the ten of them share.
	 */
	public static final int MAX_COLLAPSE_PARTICLES = 100;
	/** Per-tick ceiling, so no single frame can be handed the whole budget. */
	public static final int MAX_COLLAPSE_PARTICLES_PER_TICK = 24;

	private StabilityAnchorGeometry() {
	}

	/** The relay core in world space, from the anchor's authoritative block position. */
	public static Vec3 relayCore(BlockPos anchorPosition) {
		if (anchorPosition == null) throw new IllegalArgumentException("Anchor position is required");
		return new Vec3(anchorPosition.getX() + 0.5D, anchorPosition.getY() + RELAY_CORE_HEIGHT,
				anchorPosition.getZ() + 0.5D);
	}

	/** The chest core in world space, from the anchor's authoritative block position. */
	public static Vec3 chestCore(BlockPos anchorPosition) {
		if (anchorPosition == null) throw new IllegalArgumentException("Anchor position is required");
		return new Vec3(anchorPosition.getX() + 0.5D, anchorPosition.getY() + CHEST_CORE_HEIGHT,
				anchorPosition.getZ() + 0.5D);
	}

	/** The relay core in world space, from a live anchor entity's origin. */
	public static Vec3 relayCore(Vec3 origin) {
		if (origin == null) throw new IllegalArgumentException("Origin is required");
		return new Vec3(origin.x, origin.y + RELAY_CORE_HEIGHT, origin.z);
	}

	public static Vec3 chestCore(Vec3 origin) {
		if (origin == null) throw new IllegalArgumentException("Origin is required");
		return new Vec3(origin.x, origin.y + CHEST_CORE_HEIGHT, origin.z);
	}

	/** Which beat of the destruction performance a given age belongs to. */
	public static CollapsePhase collapsePhase(float ageTicks) {
		if (ageTicks < 0.0F) return CollapsePhase.NONE;
		if (ageTicks < COLLAPSE_FRACTURE_END) return CollapsePhase.FRACTURE;
		if (ageTicks < COLLAPSE_TETHER_END) return CollapsePhase.TETHER_SNAP;
		if (ageTicks < COLLAPSE_IMPLOSION_END) return CollapsePhase.IMPLOSION;
		if (ageTicks < COLLAPSE_TICKS) return CollapsePhase.RESIDUE;
		return CollapsePhase.DONE;
	}

	/**
	 * How far the claws have folded in, 0 to 1. Nothing moves during the fracture beat - the first
	 * two ticks are light only - and the fold then runs to the end of the implosion.
	 */
	public static float collapseFold(float ageTicks) {
		if (ageTicks <= COLLAPSE_FRACTURE_END) return 0.0F;
		float span = COLLAPSE_IMPLOSION_END - COLLAPSE_FRACTURE_END;
		return Math.clamp((ageTicks - COLLAPSE_FRACTURE_END) / span, 0.0F, 1.0F);
	}

	/**
	 * How much of the structure is still drawn, 0 to 1. Holds at full through the fracture and the
	 * tether snap, pixelates away across the implosion, and is gone before the residue beat.
	 */
	public static float collapsePresence(float ageTicks) {
		if (ageTicks < 0.0F) return 1.0F;
		if (ageTicks <= COLLAPSE_TETHER_END) return 1.0F;
		if (ageTicks >= COLLAPSE_IMPLOSION_END) return 0.0F;
		float span = COLLAPSE_IMPLOSION_END - COLLAPSE_TETHER_END;
		return Math.clamp(1.0F - (ageTicks - COLLAPSE_TETHER_END) / span, 0.0F, 1.0F);
	}

	/**
	 * The retracting tether's remaining reach toward the interface, 1 down to 0.
	 *
	 * <p>The band gathers to a point at the relay core over the fracture beat and is then hauled back
	 * along its own line. It is deliberately not a fade: the light has to visibly go somewhere.
	 */
	public static float collapseTetherReach(float ageTicks) {
		if (ageTicks < 0.0F) return 0.0F;
		if (ageTicks <= COLLAPSE_FRACTURE_END) return 1.0F;
		if (ageTicks >= COLLAPSE_TETHER_END) return 0.0F;
		float span = COLLAPSE_TETHER_END - COLLAPSE_FRACTURE_END;
		float progress = (ageTicks - COLLAPSE_FRACTURE_END) / span;
		// Squared, so the snap accelerates away rather than sliding back at a constant rate.
		return Math.clamp(1.0F - progress * progress, 0.0F, 1.0F);
	}

	public enum CollapsePhase {
		NONE, FRACTURE, TETHER_SNAP, IMPLOSION, RESIDUE, DONE
	}
}
