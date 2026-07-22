package com.xm.thefourthfrequency.world;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ResourceScanRefreshPolicyTest {
	@Test
	void automaticRefreshAreaMatchesTheFortyEightBlockHorizontalScan() {
		assertTrue(ResourceScanRefreshPolicy.contains(10, -10, 58, -58, 48));
		assertFalse(ResourceScanRefreshPolicy.contains(10, -10, 59, -10, 48));
		assertFalse(ResourceScanRefreshPolicy.contains(10, -10, 10, 39, 48));
	}

	@Test
	void negativeRadiusCannotCreateAnUnboundedRefreshArea() {
		assertTrue(ResourceScanRefreshPolicy.contains(3, 7, 3, 7, -1));
		assertFalse(ResourceScanRefreshPolicy.contains(3, 7, 4, 7, -1));
	}
}
