package com.xm.thefourthfrequency.world;

final class ResourceScanRefreshPolicy {
	private ResourceScanRefreshPolicy() {
	}

	static boolean contains(int originX, int originZ, int targetX, int targetZ, int radius) {
		int safeRadius = Math.max(0, radius);
		long dx = Math.abs((long) targetX - originX);
		long dz = Math.abs((long) targetZ - originZ);
		return dx <= safeRadius && dz <= safeRadius;
	}
}
