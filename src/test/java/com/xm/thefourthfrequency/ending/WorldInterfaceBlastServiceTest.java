package com.xm.thefourthfrequency.ending;

import com.xm.thefourthfrequency.networking.WorldInterfaceBlastS2C;
import com.xm.thefourthfrequency.networking.WorldInterfaceProtocol;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The cooldown that keeps a walking detonation from becoming a strobe, and the envelope it rides in.
 *
 * <p>Both halves are here because both are the same defect seen from two sides: the laser's contact
 * point detonates every other tick for two seconds, and unbounded that is twenty camera impulses
 * fighting over four slots and twenty overlapping explosion samples in a mixer with a fixed number of
 * channels. The rate is the thing being fixed, so the rate is what is pinned.
 */
final class WorldInterfaceBlastServiceTest {
	@Test
	void aSourceThatHasNeverSpokenIsAlwaysAllowed() {
		assertTrue(WorldInterfaceBlastService.permits(Long.MIN_VALUE, 0L,
				WorldInterfaceBlastService.MIN_GAP_TICKS));
		assertTrue(WorldInterfaceBlastService.permits(Long.MIN_VALUE, 1_000_000L, 240));
	}

	@Test
	void oneSweepIsAHandfulOfEventsRatherThanOnePerTick() {
		// Two seconds of laser sweep, asking on every tick the scar walks on.
		int allowed = 0;
		long last = Long.MIN_VALUE;
		for (long tick = 0L; tick < WorldInterfaceProtocol.LASER_SWEEP_TICKS; tick += 2L) {
			if (!WorldInterfaceBlastService.permits(last, tick, WorldInterfaceBlastService.MIN_GAP_TICKS)) {
				continue;
			}
			allowed++;
			last = tick;
		}
		assertTrue(allowed >= 3, "a sweep still has to be heard: " + allowed);
		assertTrue(allowed <= WorldInterfaceProtocol.LASER_SWEEP_TICKS
						/ WorldInterfaceBlastService.MIN_GAP_TICKS + 1,
				"the cooldown is not holding: " + allowed);
	}

	/** A restored save rewinds the game clock, and a rewound clock must not mute a source forever. */
	@Test
	void aRewoundClockDoesNotLockASourceOut() {
		assertTrue(WorldInterfaceBlastService.permits(9_000L, 40L,
				WorldInterfaceBlastService.MIN_GAP_TICKS));
	}

	@Test
	void theGapIsInclusiveAndNeverZero() {
		int gap = WorldInterfaceBlastService.MIN_GAP_TICKS;
		assertTrue(!WorldInterfaceBlastService.permits(100L, 100L + gap - 1, gap));
		assertTrue(WorldInterfaceBlastService.permits(100L, 100L + gap, gap));
		// A caller asking for no gap at all still gets one tick of separation.
		assertTrue(!WorldInterfaceBlastService.permits(100L, 100L, 0));
		assertTrue(WorldInterfaceBlastService.permits(100L, 101L, 0));
	}

	/**
	 * The radius is the receiving side's falloff denominator, so an unbounded one is a shake nobody
	 * on the island can escape - the opposite of what a distance-graded impulse is for.
	 */
	@Test
	void theBlastEnvelopeRefusesAnUnusableRadius() {
		UUID id = UUID.randomUUID();
		assertThrows(IllegalArgumentException.class,
				() -> new WorldInterfaceBlastS2C(id, 0.0D, 0.0D, 0.0D, 0.0F, 0));
		assertThrows(IllegalArgumentException.class,
				() -> new WorldInterfaceBlastS2C(id, 0.0D, 0.0D, 0.0D, -1.0F, 0));
		assertThrows(IllegalArgumentException.class, () -> new WorldInterfaceBlastS2C(id,
				0.0D, 0.0D, 0.0D, WorldInterfaceBlastS2C.MAX_RADIUS + 1.0F, 0));
		assertThrows(IllegalArgumentException.class,
				() -> new WorldInterfaceBlastS2C(id, Double.NaN, 0.0D, 0.0D, 8.0F, 0));
		assertThrows(IllegalArgumentException.class,
				() -> new WorldInterfaceBlastS2C(id, 0.0D, 0.0D, 0.0D, 8.0F, 99));
		assertThrows(NullPointerException.class,
				() -> new WorldInterfaceBlastS2C(null, 0.0D, 0.0D, 0.0D, 8.0F, 0));
	}

	/** Every grade the protocol names has to survive the round trip the payload puts it through. */
	@Test
	void everyGradeSurvivesTheWire() {
		UUID id = UUID.randomUUID();
		for (WorldInterfaceProtocol.BlastGrade grade : WorldInterfaceProtocol.BlastGrade.values()) {
			WorldInterfaceBlastS2C payload =
					new WorldInterfaceBlastS2C(id, 1.0D, 2.0D, 3.0D, 24.0F, grade.wireId());
			assertEquals(grade, payload.grade());
		}
	}
}
