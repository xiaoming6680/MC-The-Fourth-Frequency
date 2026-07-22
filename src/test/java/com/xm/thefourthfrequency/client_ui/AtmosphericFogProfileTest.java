package com.xm.thefourthfrequency.client_ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class AtmosphericFogProfileTest {
	private static final float EPSILON = 0.0001F;

	@Test
	void fixedDistanceHidesEachDimensionBoundary() {
		for (int chunks : new int[] {DimensionViewDistancePolicy.OVERWORLD_CHUNKS,
				DimensionViewDistancePolicy.NETHER_CHUNKS, DimensionViewDistancePolicy.END_CHUNKS}) {
			AtmosphericFogProfile profile = AtmosphericFogProfile.fixedDistanceOnly(chunks);
			float boundary = chunks * 16.0F;
			assertTrue(profile.renderStart() < profile.renderEnd());
			assertEquals(boundary - 5.0F, profile.renderEnd(), EPSILON);
			assertEquals(profile.renderEnd(), profile.clampRenderEnd(boundary + 64.0F), EPSILON);
			assertEquals(20.0F, profile.clampRenderEnd(20.0F), EPSILON,
					"environmental fog that is already closer must win");
		}
	}

	@Test
	void rainThunderAndCavesOnlyBringFogCloser() {
		AtmosphericFogProfile clear = AtmosphericFogProfile.sample(1.0F, 0.0F, 0.0F,
				0.0F, 80.0F, true, DimensionViewDistancePolicy.OVERWORLD_CHUNKS);
		AtmosphericFogProfile rain = AtmosphericFogProfile.sample(1.0F, 1.0F, 0.0F,
				0.0F, 80.0F, true, DimensionViewDistancePolicy.OVERWORLD_CHUNKS);
		AtmosphericFogProfile thunder = AtmosphericFogProfile.sample(1.0F, 1.0F, 1.0F,
				0.0F, 80.0F, true, DimensionViewDistancePolicy.OVERWORLD_CHUNKS);
		AtmosphericFogProfile cave = AtmosphericFogProfile.sample(0.0F, 0.0F, 0.0F,
				0.0F, 20.0F, true, DimensionViewDistancePolicy.OVERWORLD_CHUNKS);
		assertTrue(rain.renderEnd() < clear.renderEnd());
		assertTrue(thunder.renderEnd() < rain.renderEnd());
		assertTrue(cave.renderEnd() < clear.renderEnd());
		assertTrue(thunder.redScale() < rain.redScale());
	}

	@Test
	void atmosphericColorKeepsBiomeColorWhileCoolingBadWeather() {
		AtmosphericFogProfile clear = AtmosphericFogProfile.sample(1.0F, 0.0F, 0.0F,
				0.0F, 80.0F, true, DimensionViewDistancePolicy.OVERWORLD_CHUNKS);
		AtmosphericFogProfile storm = AtmosphericFogProfile.sample(1.0F, 1.0F, 1.0F,
				0.0F, 80.0F, true, DimensionViewDistancePolicy.OVERWORLD_CHUNKS);
		AtmosphericFogProfile.FogColor original = clear.tint(0.70F, 0.55F, 0.40F);
		AtmosphericFogProfile.FogColor tinted = storm.tint(0.70F, 0.55F, 0.40F);
		assertEquals(0.70F, original.red(), EPSILON);
		assertEquals(0.55F, original.green(), EPSILON);
		assertEquals(0.40F, original.blue(), EPSILON);
		assertTrue(tinted.red() < original.red());
		assertTrue(tinted.blue() / tinted.red() > original.blue() / original.red());
	}

	@Test
	void nightAndProfileTransitionsAreContinuous() {
		assertEquals(0.0F, AtmosphericFogProfile.nightFactor(6_000L), EPSILON);
		assertEquals(1.0F, AtmosphericFogProfile.nightFactor(18_000L), EPSILON);
		assertEquals(0.0F, AtmosphericFogProfile.nightFactor(24_000L), EPSILON);
		AtmosphericFogProfile clear = AtmosphericFogProfile.sample(1.0F, 0.0F, 0.0F,
				0.0F, 80.0F, true, DimensionViewDistancePolicy.OVERWORLD_CHUNKS);
		AtmosphericFogProfile storm = AtmosphericFogProfile.sample(1.0F, 1.0F, 1.0F,
				1.0F, 80.0F, true, DimensionViewDistancePolicy.OVERWORLD_CHUNKS);
		AtmosphericFogProfile halfway = clear.blendToward(storm, 0.5F);
		assertTrue(halfway.renderEnd() < clear.renderEnd());
		assertTrue(halfway.renderEnd() > storm.renderEnd());
	}
}
