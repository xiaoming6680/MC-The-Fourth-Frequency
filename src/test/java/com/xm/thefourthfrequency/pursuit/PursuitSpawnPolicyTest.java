package com.xm.thefourthfrequency.pursuit;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class PursuitSpawnPolicyTest {
	@Test
	void everyInitialProbeIsBehindThePlayersView() {
		for (double[] look : new double[][]{{0.0D, 1.0D}, {1.0D, 0.0D},
				{-0.4D, 0.8D}, {0.7D, -0.2D}}) {
			var offsets = PursuitSpawnPolicy.hiddenOffsets(look[0], look[1]);
			assertFalse(offsets.isEmpty());
			for (PursuitSpawnPolicy.Offset offset : offsets) {
				assertTrue(PursuitSpawnPolicy.outsideForwardHemisphere(
						look[0], look[1], offset.x(), offset.z()));
				double distance = Math.hypot(offset.x(), offset.z());
				assertTrue(distance >= 15.0D && distance <= 32.0D);
			}
		}
	}

	@Test
	void frontAndZeroOffsetsNeverQualifyAsHidden() {
		assertFalse(PursuitSpawnPolicy.outsideForwardHemisphere(0.0D, 1.0D, 0.0D, 12.0D));
		assertFalse(PursuitSpawnPolicy.outsideForwardHemisphere(0.0D, 1.0D, 0.0D, 0.0D));
	}
}
