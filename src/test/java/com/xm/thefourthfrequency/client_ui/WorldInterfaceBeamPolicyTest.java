package com.xm.thefourthfrequency.client_ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The encounter's beams have to hold still at long range, and the sampling fix is arithmetic.
 *
 * <p>Anchor endpoints now come directly from the server snapshot, so this class only needs to lock
 * the remaining product endpoint: a tether does not thin below what a pixel grid can hold.
 */
final class WorldInterfaceBeamPolicyTest {
	/**
	 * Everything the player is close to keeps exactly the width it was authored at.
	 *
	 * <p>The floor exists for the far end of a tether. If it reached the laser's core, the sky
	 * lance or a tether overhead it would be a visual change rather than a fix.
	 */
	@Test
	void theAuthoredWidthWinsAtTheRangesTheFightHappensAt() {
		assertEquals(0.09D, WorldInterfaceBeamPolicy.stableHalfWidth(0.09D, 40.0D), 1.0E-9D);
		assertEquals(0.86D, WorldInterfaceBeamPolicy.stableHalfWidth(0.86D, 160.0D), 1.0E-9D);
		assertEquals(2.4D, WorldInterfaceBeamPolicy.stableHalfWidth(2.4D, 192.0D), 1.0E-9D);
	}

	/**
	 * The crawl, stated as the rule that stops it.
	 *
	 * <p>A tether reaching the body from a spike out on the ring is drawn a tenth of a block across
	 * from a hundred and more blocks away, which is under two pixels. Two is where an additive line
	 * starts having stable coverage from frame to frame; below it the line switches on and off along
	 * its own length as the camera moves by less than a pixel.
	 */
	@Test
	void aDistantTetherStaysWideEnoughToHoldStill() {
		double authored = 0.09D;
		for (double distance : new double[] {96.0D, 128.0D, 160.0D, 192.0D}) {
			double pixels = pixelWidth(WorldInterfaceBeamPolicy.stableHalfWidth(authored, distance),
					distance);
			assertTrue(pixels >= 2.0D,
					"a tether " + distance + " blocks out draws " + pixels
							+ " pixels wide, which is thin enough to crawl");
		}
	}

	/** The floor is a floor: it never shrinks a beam, at any distance. */
	@Test
	void theFloorOnlyEverWidens() {
		for (double distance = 0.0D; distance <= 256.0D; distance += 8.0D) {
			assertTrue(WorldInterfaceBeamPolicy.stableHalfWidth(0.42D, distance) >= 0.42D);
			assertTrue(WorldInterfaceBeamPolicy.stableHalfWidth(0.0D, distance) >= 0.0D);
		}
	}

	/**
	 * Widening a beam must not brighten it.
	 *
	 * <p>This is the half of the fix that keeps it a fix. The floor exists so the tether has pixels
	 * to be sampled in, not so it can be a thicker tether; without the dimming the arena gains ten
	 * bright cords where it had ten threads. Brightness times width is what the eye integrates at
	 * this size, so holding that constant is what makes the widened beam look unchanged.
	 */
	@Test
	void wideningABeamCostsExactlyTheBrightnessItGained() {
		double authored = 0.09D;
		for (double distance : new double[] {96.0D, 128.0D, 192.0D}) {
			double drawn = WorldInterfaceBeamPolicy.stableHalfWidth(authored, distance);
			assertTrue(drawn > authored, "this case is only interesting where the floor bites");
			int alpha = WorldInterfaceBeamPolicy.stableAlpha(128, authored, drawn);
			double before = 128 * authored;
			double after = alpha * drawn;
			assertEquals(before, after, before * 0.02D,
					"a tether " + distance + " blocks out changed apparent brightness");
		}
	}

	/** A beam the floor does not reach keeps its authored alpha exactly. */
	@Test
	void aBeamInsideTheFloorIsLeftAlone() {
		double authored = 0.86D;
		double drawn = WorldInterfaceBeamPolicy.stableHalfWidth(authored, 40.0D);
		assertEquals(authored, drawn, 1.0E-9D);
		assertEquals(255, WorldInterfaceBeamPolicy.stableAlpha(255, authored, drawn));
		assertEquals(46, WorldInterfaceBeamPolicy.stableAlpha(46, authored, drawn));
	}

	@Test
	void negativeWidthsAndDistancesAreRejected() {
		assertThrows(IllegalArgumentException.class,
				() -> WorldInterfaceBeamPolicy.stableHalfWidth(-0.1D, 10.0D));
		assertThrows(IllegalArgumentException.class,
				() -> WorldInterfaceBeamPolicy.stableHalfWidth(0.1D, -10.0D));
		assertThrows(IllegalArgumentException.class,
				() -> WorldInterfaceBeamPolicy.stableHalfWidth(Double.NaN, 10.0D));
		assertThrows(IllegalArgumentException.class,
				() -> WorldInterfaceBeamPolicy.stableAlpha(256, 0.1D, 0.2D));
		assertThrows(IllegalArgumentException.class,
				() -> WorldInterfaceBeamPolicy.stableAlpha(-1, 0.1D, 0.2D));
	}

	/**
	 * Screen width of a beam, in pixels, at Minecraft's default vertical field of view.
	 *
	 * <p>1080 rather than the smallest window the game will open at: the bound is angular, so a
	 * shorter window has fewer pixels for the same apparent thread and a taller one has more. This
	 * is the resolution the constant is calibrated against.
	 */
	private static double pixelWidth(double halfWidth, double distance) {
		double pixelsPerRadian = 1080.0D / (2.0D * Math.tan(Math.toRadians(70.0D) / 2.0D));
		return 2.0D * halfWidth / distance * pixelsPerRadian;
	}
}
