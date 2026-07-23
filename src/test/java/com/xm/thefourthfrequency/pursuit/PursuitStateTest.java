package com.xm.thefourthfrequency.pursuit;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

final class PursuitStateTest {
	@Test
	void retainsPersonalProgressAndSessionRecoveryFields() {
		PursuitState state = new PursuitState(2, 4, 0b111111, 0b10, 0b1,
				true, 90L, 1_200L, PursuitActivityProof.TRADING.mask(),
				true, "session-a", "running", 3, "minecraft:the_nether", 42L,
				91.0D, -12.0D, "thefourthfrequency:correction_nether_0", 0, 55L);
		assertEquals(2, state.resolvedChases());
		assertEquals(4, state.allowedForm());
		assertEquals(PursuitTutorialPolicy.KNOWN_FORM_MASK, state.tutorialDemoMask());
		assertEquals("session-a", state.sessionId());
		assertEquals("minecraft:the_nether", state.sourceDimension());
		assertEquals("thefourthfrequency:correction_nether_0", state.mirrorDimension());
		assertEquals(91.0D, state.sourceYaw());
	}

	@Test
	void inactiveStateCannotRetainAStaleMirrorLease() {
		PursuitState state = new PursuitState(-1, 99, 0, 0, 0,
				false, -1L, -2L, -1, false, "stale", "running", 5,
				"minecraft:overworld", 1L, Double.NaN, Double.POSITIVE_INFINITY,
				"thefourthfrequency:correction_overworld_0", 8, -5L);
		assertEquals(0, state.resolvedChases());
		assertEquals(5, state.allowedForm());
		assertFalse(state.active());
		assertEquals("", state.sessionId());
		assertEquals("none", state.sessionPhase());
		assertEquals("", state.mirrorDimension());
		assertEquals(-1, state.mirrorSlot());
		assertEquals(0L, state.nextEligibleTick());
	}
}
