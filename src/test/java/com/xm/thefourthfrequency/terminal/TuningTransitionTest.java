package com.xm.thefourthfrequency.terminal;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class TuningTransitionTest {
	@Test
	void interpolatesLinearlyAndClampsAtBothEnds() {
		TuningTransition transition = new TuningTransition(20.0D, 160L);
		assertTrue(transition.retarget(80.0D, 1_000L));
		assertEquals(20.0D, transition.valueAt(900L), 0.0001D);
		assertEquals(20.0D, transition.valueAt(1_000L), 0.0001D);
		assertEquals(50.0D, transition.valueAt(1_080L), 0.0001D);
		assertEquals(80.0D, transition.valueAt(1_160L), 0.0001D);
		assertEquals(80.0D, transition.valueAt(2_000L), 0.0001D);
	}

	@Test
	void retargetsFromTheDisplayedValueWithoutJumping() {
		TuningTransition transition = new TuningTransition(10.0D, 160L);
		transition.retarget(90.0D, 1_000L);
		assertEquals(50.0D, transition.valueAt(1_080L), 0.0001D);

		assertTrue(transition.retarget(30.0D, 1_080L));
		assertEquals(50.0D, transition.valueAt(1_080L), 0.0001D);
		assertEquals(40.0D, transition.valueAt(1_160L), 0.0001D);
		assertEquals(30.0D, transition.valueAt(1_240L), 0.0001D);
	}

	@Test
	void identicalTargetDoesNotRestartTheTransition() {
		TuningTransition transition = new TuningTransition(0.0D, 160L);
		transition.retarget(100.0D, 1_000L);
		assertFalse(transition.retarget(100.0D, 1_080L));
		assertEquals(100.0D, transition.valueAt(1_160L), 0.0001D);
	}
}
