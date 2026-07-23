package com.xm.thefourthfrequency.pursuit;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Pure chunk-window geometry used by the streaming mirror snapshot. */
public final class PursuitStreamWindow {
	public static final int RADIUS = 2;

	private PursuitStreamWindow() {
	}

	public static List<Column> centeredAt(int centerChunkX, int centerChunkZ) {
		List<Column> result = new ArrayList<>((RADIUS * 2 + 1) * (RADIUS * 2 + 1));
		for (int dx = -RADIUS; dx <= RADIUS; dx++) {
			for (int dz = -RADIUS; dz <= RADIUS; dz++) {
				result.add(new Column(centerChunkX + dx, centerChunkZ + dz));
			}
		}
		result.sort(Comparator
				.comparingInt((Column column) -> chebyshevDistance(column, centerChunkX, centerChunkZ))
				.thenComparingInt(column -> manhattanDistance(column, centerChunkX, centerChunkZ))
				.thenComparingInt(Column::chunkX)
				.thenComparingInt(Column::chunkZ));
		return List.copyOf(result);
	}

	public static long key(int chunkX, int chunkZ) {
		return (long) chunkX & 0xffffffffL | ((long) chunkZ & 0xffffffffL) << 32;
	}

	private static int chebyshevDistance(Column column, int centerChunkX, int centerChunkZ) {
		return Math.max(Math.abs(column.chunkX() - centerChunkX), Math.abs(column.chunkZ() - centerChunkZ));
	}

	private static int manhattanDistance(Column column, int centerChunkX, int centerChunkZ) {
		return Math.abs(column.chunkX() - centerChunkX) + Math.abs(column.chunkZ() - centerChunkZ);
	}

	public record Column(int chunkX, int chunkZ) {
		public long key() {
			return PursuitStreamWindow.key(chunkX, chunkZ);
		}
	}
}
