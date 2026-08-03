package com.xm.thefourthfrequency.client_render;

/**
 * The one scatter function the world interface is allowed to use.
 *
 * <p>Two callers depend on this returning the same value for the same seed: {@link
 * WorldInterfaceModel} bakes the mass, plating and debris positions from it, and {@link
 * WorldInterfaceBeamBatchRenderer} places the storm's motes and halos around that same geometry.
 * They used to carry byte-identical private copies, which meant a change to one silently drifted
 * the drawn storm off the modelled body. Keeping a single definition is what stops that.
 *
 * <p>The values must also be stable frame to frame — the mass is baked once and the storm is
 * rebuilt every frame, so anything time-dependent here would make the body shimmer against its
 * own debris.
 */
final class WorldInterfaceScatter {
	private WorldInterfaceScatter() {
	}

	/** Stable per-index scatter in {@code [0, 1]}. */
	static float hash(int seed) {
		int value = seed * 374761393 + 668265263;
		value = (value ^ (value >>> 13)) * 1274126177;
		return ((value ^ (value >>> 16)) & 0xFFFF) / 65535.0F;
	}

	/** Stable per-index scatter in {@code [-0.5, 0.5]}. */
	static float centred(int seed) {
		return hash(seed) - 0.5F;
	}
}
