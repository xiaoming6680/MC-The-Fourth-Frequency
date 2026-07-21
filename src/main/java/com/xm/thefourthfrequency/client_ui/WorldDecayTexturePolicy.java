package com.xm.thefourthfrequency.client_ui;

public final class WorldDecayTexturePolicy {
	private static final String MOD_NAMESPACE = "thefourthfrequency";
	private static final String ENTITY_TEXTURE_PREFIX = "textures/entity/";

	private WorldDecayTexturePolicy() {
	}

	public static boolean shouldCorrupt(int stage, String namespace, String path) {
		if (stage < 1 || MOD_NAMESPACE.equals(namespace)) return false;
		// Entity renderers request standalone textures after the resource stack has resolved them.
		// Replacing one here bypasses Golden Days, Programmer Art, and vanilla fallback entirely.
		if (path.startsWith(ENTITY_TEXTURE_PREFIX)) return false;
		if (!(path.startsWith("textures/item/") || path.startsWith("textures/painting/")
				|| path.startsWith("textures/particle/"))) return false;
		int divisor = switch (stage) {
			case 1 -> 37;
			case 2 -> 23;
			case 3 -> 11;
			case 4 -> 5;
			default -> 3;
		};
		return Math.floorMod((namespace + ":" + path).hashCode(), divisor) == 0;
	}
}
