package com.xm.thefourthfrequency.terminal;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class TerminalControlPolicyTest {
	@Test
	void clampsTwoModesAndTuning() {
		assertEquals(0, TerminalControlPolicy.mode(-9));
		assertEquals(1, TerminalControlPolicy.mode(9));
		assertEquals(0, TerminalControlPolicy.tuning(-1));
		assertEquals(100, TerminalControlPolicy.tuning(101));
	}

	@Test
	void serverControlRangesRejectValuesOutsideTheWireEnums() {
		assertTrue(TerminalControlPolicy.validMode(0));
		assertTrue(TerminalControlPolicy.validMode(1));
		assertFalse(TerminalControlPolicy.validMode(-1));
		assertFalse(TerminalControlPolicy.validMode(2));
		assertTrue(TerminalControlPolicy.validTuning(0));
		assertTrue(TerminalControlPolicy.validTuning(100));
		assertFalse(TerminalControlPolicy.validTuning(-1));
		assertFalse(TerminalControlPolicy.validTuning(101));
	}

	@Test
	void receiverLocksOnlyInsideTheContextualTargetWindow() {
		for (int value = 35; value <= 39; value++) {
			assertTrue(TerminalControlPolicy.receiverLocked(value, 37));
		}
		assertFalse(TerminalControlPolicy.receiverLocked(34, 37));
		assertFalse(TerminalControlPolicy.receiverLocked(40, 37));
	}

	@Test
	void receiverStrengthFallsOffWithoutPublishedStations() {
		assertEquals(100, TerminalControlPolicy.receiverStrength(50, 50));
		assertEquals(96, TerminalControlPolicy.receiverStrength(49, 50));
		assertEquals(92, TerminalControlPolicy.receiverStrength(52, 50));
		assertEquals(0, TerminalControlPolicy.receiverStrength(25, 50));
		assertEquals(50, TerminalControlPolicy.DEFAULT_TUNING);
		assertEquals(2, TerminalControlPolicy.RECEIVER_LOCK_RADIUS);
	}

	@Test
	void derivesNormalCyanAndRedHardwareStagesFromStoryThresholds() {
		assertEquals(0, TerminalControlPolicy.visualStage(1, 0));
		assertEquals(0, TerminalControlPolicy.visualStage(2, 0));
		assertEquals(1, TerminalControlPolicy.visualStage(3, 0));
		assertEquals(1, TerminalControlPolicy.visualStage(4, 2));
		assertEquals(2, TerminalControlPolicy.visualStage(2, 3));
	}

}
