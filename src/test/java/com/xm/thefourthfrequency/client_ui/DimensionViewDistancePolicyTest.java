package com.xm.thefourthfrequency.client_ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class DimensionViewDistancePolicyTest {
	@Test
	void mapsVanillaDimensionsToTheirNonConfigurableDistances() {
		assertEquals(3, DimensionViewDistancePolicy.lockedChunks(
				DimensionViewDistancePolicy.OVERWORLD_ID));
		assertEquals(6, DimensionViewDistancePolicy.lockedChunks(
				DimensionViewDistancePolicy.NETHER_ID));
		assertEquals(12, DimensionViewDistancePolicy.lockedChunks(
				DimensionViewDistancePolicy.END_ID));
		assertEquals(3, DimensionViewDistancePolicy.lockedChunks("example:custom_dimension"));
		assertEquals(16, DimensionViewDistancePolicy.SUCCESS_RETURN_CHUNKS);
	}
}
