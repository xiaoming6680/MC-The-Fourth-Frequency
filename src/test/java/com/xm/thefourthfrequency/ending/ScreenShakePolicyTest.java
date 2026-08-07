package com.xm.thefourthfrequency.ending;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The shake curves, checked where they can be checked.
 *
 * <p>The failure mode this exists for is specific and nasty: a decay that gets small without ever
 * reaching zero leaves the rendering camera permanently a fraction of a degree off true, and the
 * player has no way to correct it or even to name what is wrong. It is invisible in a screenshot,
 * survives a relog, and accumulates over a fight. Asserting the envelope terminates is worth more
 * than any amount of looking at it.
 */
final class ScreenShakePolicyTest {
	@Test
	void everyImpulseReturnsExactlyToZeroAndStaysThere() {
		for (ScreenShakePolicy.Grade grade : ScreenShakePolicy.Grade.values()) {
			double duration = grade.seconds();
			assertEquals(0.0D, ScreenShakePolicy.envelope(duration, duration),
					"a shake that never reaches zero leaves the camera permanently crooked");
			assertEquals(0.0D, ScreenShakePolicy.envelope(duration * 2.0D, duration));
			// And the sample, not just the envelope, on every axis.
			for (int axis = 0; axis < 4; axis++) {
				assertEquals(0.0D, ScreenShakePolicy.sample(duration, duration,
						grade.peakDegrees(), 1.0D, 7L, axis));
			}
		}
	}

	@Test
	void theEnvelopeStartsFullAndDecaysMonotonically() {
		double duration = 0.7D;
		assertEquals(1.0D, ScreenShakePolicy.envelope(0.0D, duration), 1.0E-9D);
		double previous = Double.MAX_VALUE;
		for (int step = 0; step <= 40; step++) {
			double value = ScreenShakePolicy.envelope(duration * step / 40.0D, duration);
			assertTrue(value <= previous + 1.0E-9D, "envelope must not rise again at step " + step);
			assertTrue(value >= 0.0D);
			previous = value;
		}
	}

	@Test
	void gradesEscalateInBothAmplitudeAndDuration() {
		ScreenShakePolicy.Grade[] grades = ScreenShakePolicy.Grade.values();
		for (int index = 1; index < grades.length; index++) {
			assertTrue(grades[index].peakDegrees() > grades[index - 1].peakDegrees(),
					grades[index] + " must hit harder than " + grades[index - 1]);
			assertTrue(grades[index].seconds() > grades[index - 1].seconds(),
					grades[index] + " must last longer than " + grades[index - 1]);
		}
		// A shake big enough to be disorienting is still nowhere near a full screen turn.
		assertTrue(grades[grades.length - 1].peakDegrees() < 10.0D);
	}

	@Test
	void distanceFalloffIsQuadraticAndBoundedByItsRadius() {
		assertEquals(1.0D, ScreenShakePolicy.falloff(0.0D, 40.0D));
		assertEquals(0.0D, ScreenShakePolicy.falloff(40.0D, 40.0D));
		assertEquals(0.0D, ScreenShakePolicy.falloff(41.0D, 40.0D));
		// Halfway out keeps a quarter of the strength, not half: the near field dominates.
		assertEquals(0.25D, ScreenShakePolicy.falloff(20.0D, 40.0D), 1.0E-9D);
		double previous = Double.MAX_VALUE;
		for (int step = 0; step <= 20; step++) {
			double value = ScreenShakePolicy.falloff(step * 2.0D, 40.0D);
			assertTrue(value <= previous + 1.0E-9D);
			previous = value;
		}
		assertEquals(0.0D, ScreenShakePolicy.falloff(1.0D, 0.0D), "a zero radius shakes nothing");
	}

	/**
	 * The three components must not share a period, or the shake reads as a vibration rather than
	 * as an impact. Checked as a ratio test rather than by eye.
	 */
	@Test
	void theThreeFrequenciesAreMutuallyIncommensurable() {
		double[] frequencies = ScreenShakePolicy.FREQUENCIES_HZ;
		assertEquals(3, frequencies.length);
		for (int a = 0; a < frequencies.length; a++) {
			for (int b = a + 1; b < frequencies.length; b++) {
				double ratio = frequencies[b] / frequencies[a];
				for (int numerator = 1; numerator <= 4; numerator++) {
					for (int denominator = 1; denominator <= 4; denominator++) {
						assertNotEquals(numerator / (double) denominator, ratio, 0.02D,
								frequencies[a] + " and " + frequencies[b] + " share a simple ratio");
					}
				}
			}
		}
	}

	/** Axes must not move together, or the whole view slides instead of being knocked. */
	@Test
	void axesAreDecorrelatedAndImpulsesAreReproducible() {
		double at = 0.05D;
		double pitch = ScreenShakePolicy.sample(at, 0.7D, 2.2D, 1.0D, 42L, 0);
		double yaw = ScreenShakePolicy.sample(at, 0.7D, 2.2D, 1.0D, 42L, 1);
		assertNotEquals(pitch, yaw, 1.0E-6D, "pitch and yaw must not share a phase");
		// Same seed, same shake: a replayed encounter has to look the same.
		assertEquals(pitch, ScreenShakePolicy.sample(at, 0.7D, 2.2D, 1.0D, 42L, 0));
		assertNotEquals(pitch, ScreenShakePolicy.sample(at, 0.7D, 2.2D, 1.0D, 43L, 0), 1.0E-9D);
	}

	@Test
	void theUserScaleIsRespectedAndZeroMeansSilence() {
		assertEquals(0.0D, Math.abs(ScreenShakePolicy.sample(0.05D, 0.7D, 2.2D, 0.0D, 5L, 0)));
		double full = ScreenShakePolicy.sample(0.05D, 0.7D, 2.2D, 1.0D, 5L, 0);
		double half = ScreenShakePolicy.sample(0.05D, 0.7D, 2.2D, 0.5D, 5L, 0);
		assertEquals(full * 0.5D, half, 1.0E-9D);
		// Out-of-range and non-finite settings must not be able to produce a wilder shake.
		assertEquals(1.0D, ScreenShakePolicy.clampScale(4.0D));
		assertEquals(0.0D, ScreenShakePolicy.clampScale(-1.0D));
		assertEquals(1.0D, ScreenShakePolicy.clampScale(Double.NaN));
	}

	@Test
	void theWeakestConcurrentImpulseIsTheOneReplaced() {
		assertEquals(2, ScreenShakePolicy.weakestSlot(new double[]{3.0D, 1.5D, 0.2D, 2.0D}));
		assertEquals(0, ScreenShakePolicy.weakestSlot(new double[]{0.0D, 0.0D}));
		assertTrue(ScreenShakePolicy.MAX_CONCURRENT >= 2);
	}
}
