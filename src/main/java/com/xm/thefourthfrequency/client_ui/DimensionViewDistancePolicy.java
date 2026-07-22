package com.xm.thefourthfrequency.client_ui;

/** Pure dimension policy shared by client enforcement, fog rendering and tests. */
public final class DimensionViewDistancePolicy {
	public static final String OVERWORLD_ID = "minecraft:overworld";
	public static final String NETHER_ID = "minecraft:the_nether";
	public static final String END_ID = "minecraft:the_end";
	public static final int OVERWORLD_CHUNKS = 3;
	public static final int NETHER_CHUNKS = 6;
	public static final int END_CHUNKS = 12;
	public static final int SUCCESS_RETURN_CHUNKS = 16;

	private DimensionViewDistancePolicy() {
	}

	public static int lockedChunks(String dimensionId) {
		if (NETHER_ID.equals(dimensionId)) return NETHER_CHUNKS;
		if (END_ID.equals(dimensionId)) return END_CHUNKS;
		return OVERWORLD_CHUNKS;
	}
}
