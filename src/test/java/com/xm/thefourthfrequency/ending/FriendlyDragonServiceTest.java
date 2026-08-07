package com.xm.thefourthfrequency.ending;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The ending dragon's flight path, checked where a screenshot cannot check it.
 *
 * <p>One property matters above the rest and it is the one that broke: <b>consecutive ticks have to
 * be adjacent</b>. The orbit angle used to be {@code (gameTime % period) / period} - the fractional
 * part of {@code gameTime / period} - and the descent sweeps the period from six hundred ticks down
 * to a hundred and ninety. On a world whose clock has reached six figures that quotient runs from
 * about 167 revolutions to about 526 across the descent, so the fractional part cycles some three
 * hundred and sixty times: better than two whole orbits per tick. The dragon was thrown to a
 * different point of a seventy-two block circle on every single tick, starting on the tick after it
 * appeared, because the descent begins immediately.
 *
 * <p>Nothing about that is visible in a still frame, and it is arithmetic, so it is asserted here.
 */
final class FriendlyDragonServiceTest {
	/**
	 * Blocks a dragon may cover in one tick.
	 *
	 * <p>Generous against what the path actually asks for - the tangential speed peaks below one
	 * block a tick, and the two radial legs add well under one more - and still four orders of
	 * magnitude tighter than the failure it exists to catch, which moved the body up to a hundred and
	 * forty-four blocks between ticks.
	 */
	private static final double MAX_STEP_BLOCKS = 4.0D;

	@Test
	void theFlightPathIsContinuousAcrossTheWholeDescent() {
		// Swept over a range of descent lengths rather than the one the encounter happens to use, so
		// the property is about the path rather than about a single schedule.
		for (int descentTicks : new int[]{60, 120, 160, 240, 300}) {
			int total = FriendlyDragonService.EMERGE_TICKS + descentTicks;
			double angle = 0.0D;
			Vec3 previous = null;
			for (int age = 0; age <= total; age++) {
				double approach = Math.clamp(
						(age - FriendlyDragonService.EMERGE_TICKS) / (double) descentTicks, 0.0D, 1.0D);
				Vec3 offset = FriendlyDragonService.orbitOffset(angle, approach, age);
				if (previous != null) {
					double step = previous.distanceTo(offset);
					assertTrue(step < MAX_STEP_BLOCKS, "descent " + descentTicks + " tick " + age
							+ ": the dragon moved " + step + " blocks in one tick");
				}
				previous = offset;
				angle = FriendlyDragonService.advanceAngle(angle, 1,
						FriendlyDragonService.orbitPeriod(approach));
			}
		}
	}

	/**
	 * The same property, but flown on the schedule the encounter actually runs.
	 *
	 * <p>The sweep above hands the path a descent that begins only once the emergence has finished,
	 * and the runtime does not do that: {@code approach} is counted from the tick the dragon is added,
	 * which is the same tick the spiral out of the altar starts, so the two overlap for the whole of
	 * the emergence. It also stops at the bottom of the descent, and the flight does not - the dragon
	 * has to get back off the exit it just opened.
	 *
	 * <p>Both gaps hid the same defect. The descent used to be gated on the encounter still being in
	 * its success resolution, and the exit opening is the tick that ends that stage, so the approach
	 * fell from one to zero between two ticks and the body was thrown some sixty blocks - from the low
	 * working circle back to the resting orbit - in a single step. A sweep that never leaves the
	 * descent cannot see the tick the descent ends on.
	 */
	@Test
	void theWholeArrivalDescentAndReturnIsFlownRatherThanJumped() {
		int workTicks = 160;
		int returnTicks = FriendlyDragonService.RETURN_TICKS;
		// Well past the point the climb has finished, so a schedule that steps at its far end fails
		// here rather than in a screenshot.
		int total = workTicks + returnTicks + 400;
		double angle = 0.0D;
		Vec3 previous = null;
		for (int age = 0; age <= total; age++) {
			double approach = FriendlyDragonService.approach(age, workTicks, returnTicks);
			Vec3 offset = FriendlyDragonService.orbitOffset(angle, approach, age);
			if (previous != null) {
				double step = previous.distanceTo(offset);
				assertTrue(step < MAX_STEP_BLOCKS, "tick " + age + " (approach " + approach
						+ "): the dragon moved " + step + " blocks in one tick");
			}
			previous = offset;
			angle = FriendlyDragonService.advanceAngle(angle, 1,
					FriendlyDragonService.orbitPeriod(approach));
		}
	}

	/** The descent and the climb back are one continuous schedule, not two gated on a stage. */
	@Test
	void theApproachRisesToTheExitAndComesBackWithoutAStep() {
		int workTicks = 160;
		int returnTicks = FriendlyDragonService.RETURN_TICKS;
		assertEquals(0.0D, FriendlyDragonService.approach(-40L, workTicks, returnTicks), 1.0E-9D,
				"before the dragon exists there is nothing to descend");
		assertEquals(0.0D, FriendlyDragonService.approach(0L, workTicks, returnTicks), 1.0E-9D,
				"it arrives on the resting orbit");
		assertEquals(1.0D, FriendlyDragonService.approach(workTicks, workTicks, returnTicks), 1.0E-9D,
				"it is on the working circle on the tick the exit opens");
		assertEquals(0.0D, FriendlyDragonService.approach(workTicks + returnTicks, workTicks, returnTicks),
				1.0E-9D, "and back on the resting orbit once the climb has run");
		assertEquals(0.0D, FriendlyDragonService.approach(1_000_000L, workTicks, returnTicks), 1.0E-9D,
				"where it stays: the schedule must not wrap");

		double previous = 0.0D;
		for (long age = 0L; age <= workTicks + returnTicks + 200L; age++) {
			double approach = FriendlyDragonService.approach(age, workTicks, returnTicks);
			assertTrue(approach >= -1.0E-9D && approach <= 1.0D + 1.0E-9D,
					"approach escaped [0, 1] at " + age + ": " + approach);
			// The tightest leg is the descent, at one part in workTicks per tick. Anything larger is a
			// step, and a step here is the sixty-block jump this test exists for.
			assertTrue(Math.abs(approach - previous) <= 1.0D / Math.min(workTicks, returnTicks) + 1.0E-9D,
					"the approach stepped at " + age + ": " + previous + " -> " + approach);
			previous = approach;
		}
	}

	/**
	 * It leaves from the altar, which is the thing the six-second summon spends its whole length
	 * pointing at. Placed straight onto the ring it arrived seventy-two blocks from where everyone
	 * was looking.
	 */
	@Test
	void itStartsAtTheCentreAndSpiralsOutOntoTheRing() {
		Vec3 start = FriendlyDragonService.orbitOffset(0.0D, 0.0D, 0);
		assertTrue(start.horizontalDistance() < 2.0D,
				"the dragon must be added at the arena centre, not on the orbit: " + start);
		assertEquals(FriendlyDragonService.ORBIT_HEIGHT, start.y, 6.0D,
				"it leaves at the resting orbit's altitude");

		Vec3 arrived = FriendlyDragonService.orbitOffset(0.0D, 0.0D, FriendlyDragonService.EMERGE_TICKS);
		assertEquals(FriendlyDragonService.ORBIT_RADIUS, arrived.horizontalDistance(), 0.5D,
				"the spiral must finish on the ring the ceremony drew");

		// And it is monotone on the way out: a spiral, not a wobble.
		double previous = -1.0D;
		for (int age = 0; age <= FriendlyDragonService.EMERGE_TICKS; age++) {
			double radius = FriendlyDragonService.orbitOffset(0.0D, 0.0D, age).horizontalDistance();
			assertTrue(radius >= previous - 1.0E-6D, "the emergence must not double back at " + age);
			previous = radius;
		}
	}

	/** The descent ends on the low working circle, or the dragon never reaches the exit it opens. */
	@Test
	void theDescentEndsOnTheWorkingCircle() {
		Vec3 resting = FriendlyDragonService.orbitOffset(0.0D, 0.0D, FriendlyDragonService.EMERGE_TICKS);
		Vec3 working = FriendlyDragonService.orbitOffset(0.0D, 1.0D, FriendlyDragonService.EMERGE_TICKS);
		assertEquals(FriendlyDragonService.WORK_RADIUS, working.horizontalDistance(), 0.5D);
		assertTrue(working.y < resting.y, "the working circle is below the resting orbit");
		assertTrue(FriendlyDragonService.orbitPeriod(1.0D) < FriendlyDragonService.orbitPeriod(0.0D),
				"a tighter circle has to be flown faster or the descent reads as a stall");
	}

	/**
	 * The runtime must not go back to deriving the angle from the world clock and the period.
	 *
	 * <p>Asserted against the source, because the composition the other tests exercise -
	 * {@code advanceAngle} feeding {@code orbitOffset} - is only the runtime's behaviour for as long
	 * as the runtime keeps using it. The defect was not a wrong constant, it was a wrong <em>shape</em>
	 * of expression, and the shape is what this refuses.
	 */
	@Test
	void theOrbitAngleIsNeverDerivedFromTheWorldClockAndThePeriod() throws Exception {
		String source = java.nio.file.Files.readString(java.nio.file.Path.of(
						"src/main/java/com/xm/thefourthfrequency/ending/FriendlyDragonService.java"),
				java.nio.charset.StandardCharsets.UTF_8);
		// Comments stripped first: the class documents the defect it was fixed for, and the prose
		// naturally quotes the very expression this refuses.
		String code = source.replaceAll("(?s)/\\*.*?\\*/", " ").replaceAll("//[^\\n]*", " ");
		assertTrue(code.contains("private static Orbit advanceOrbit"),
				"the orbit accumulator has been renamed or removed");
		assertTrue(code.contains("advanceAngle(previous.angle()"),
				"the orbit angle must be accumulated from the previous angle");
		assertTrue(!code.contains("% period"),
				"the orbit angle must not be the fractional part of gameTime / period: that expression"
						+ " is discontinuous in the period, and the descent sweeps the period");
	}

	/** The angle only ever advances, and wraps rather than growing without bound. */
	@Test
	void theAngleAccumulatesAndWraps() {
		double angle = 0.0D;
		for (int tick = 0; tick < FriendlyDragonService.ORBIT_PERIOD_TICKS * 3; tick++) {
			double next = FriendlyDragonService.advanceAngle(angle, 1,
					FriendlyDragonService.ORBIT_PERIOD_TICKS);
			assertTrue(next >= 0.0D && next < Math.PI * 2.0D + 1.0E-9D, "angle escaped its range: " + next);
			angle = next;
		}
		// A gap is caught up in one call rather than skipped.
		assertTrue(FriendlyDragonService.advanceAngle(0.0D, 10, 600.0D)
				> FriendlyDragonService.advanceAngle(0.0D, 1, 600.0D));
	}
}
