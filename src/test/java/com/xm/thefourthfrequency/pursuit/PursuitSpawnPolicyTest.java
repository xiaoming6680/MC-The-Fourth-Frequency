package com.xm.thefourthfrequency.pursuit;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class PursuitSpawnPolicyTest {
	@Test
	void everyProbeStaysInsideTheDistanceBandAndOffThePlayersOwnColumn() {
		for (long seed : new long[]{0L, 1L, -7L, 918_273_645L}) {
			List<PursuitSpawnPolicy.Offset> offsets = PursuitSpawnPolicy.spawnOffsets(seed);
			assertFalse(offsets.isEmpty());
			Set<PursuitSpawnPolicy.Offset> unique = new HashSet<>(offsets);
			assertEquals(offsets.size(), unique.size(), "probe columns must not repeat");
			for (PursuitSpawnPolicy.Offset offset : offsets) {
				assertFalse(offset.x() == 0 && offset.z() == 0);
				double distance = Math.hypot(offset.x(), offset.z());
				assertTrue(distance >= 25.0D && distance <= 42.0D, "distance=" + distance);
			}
		}
	}

	/**
	 * The rear-hemisphere rule is gone on purpose, so the thing worth pinning is that the front is
	 * genuinely reachable - not merely legal. A ring that technically allows forward bearings but
	 * always tries the rear ones first would pass a weaker test and change nothing in play.
	 */
	@Test
	void theFirstProbeTriedLandsInFrontAboutAsOftenAsBehind() {
		int inFront = 0;
		int behind = 0;
		for (long seed = 0L; seed < 400L; seed++) {
			PursuitSpawnPolicy.Offset first = PursuitSpawnPolicy.spawnOffsets(seed).getFirst();
			// The player faces +Z here; the sign of the probe's own Z is which side it is on.
			if (first.z() > 0) inFront++;
			else if (first.z() < 0) behind++;
		}
		assertTrue(inFront > 120, "forward first-probes=" + inFront);
		assertTrue(behind > 120, "rearward first-probes=" + behind);
	}

	/** Bearing is randomised; distance is not. A near arrival must never be tried after a far one. */
	@Test
	void probeOrderWalksOutwardRingByRing() {
		for (long seed : new long[]{42L, 7L, -3L}) {
			int previousRing = -1;
			for (PursuitSpawnPolicy.Offset offset : PursuitSpawnPolicy.spawnOffsets(seed)) {
				int ring = nearestRing(Math.hypot(offset.x(), offset.z()));
				assertTrue(ring >= previousRing, "probe stepped back inward to ring " + ring);
				previousRing = ring;
			}
		}
	}

	@Test
	void sameSeedProducesTheSameProbesAndDifferentSeedsDoNot() {
		assertEquals(PursuitSpawnPolicy.spawnOffsets(5L), PursuitSpawnPolicy.spawnOffsets(5L));
		assertNotEquals(PursuitSpawnPolicy.spawnOffsets(5L), PursuitSpawnPolicy.spawnOffsets(6L));
	}

	private static int nearestRing(double distance) {
		double[] rings = {26.0D, 30.0D, 34.0D, 38.0D, 41.0D};
		int best = 0;
		for (int index = 1; index < rings.length; index++) {
			if (Math.abs(rings[index] - distance) < Math.abs(rings[best] - distance)) best = index;
		}
		return best;
	}
}
