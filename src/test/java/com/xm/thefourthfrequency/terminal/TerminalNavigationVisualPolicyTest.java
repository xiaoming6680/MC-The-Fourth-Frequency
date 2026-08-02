package com.xm.thefourthfrequency.terminal;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class TerminalNavigationVisualPolicyTest {
	@Test
	void targetNeedleRequiresExplicitlyActiveGuidance() {
		assertFalse(TerminalNavigationVisualPolicy.targetNeedleVisible(false, true, -100.0D));
		assertFalse(TerminalNavigationVisualPolicy.targetNeedleVisible(false, true, 0.0D));
		assertTrue(TerminalNavigationVisualPolicy.targetNeedleVisible(true, true, -100.0D));
	}

	@Test
	void activeGuidanceMayFlashWhileItsFreshTargetPayloadArrives() {
		assertTrue(TerminalNavigationVisualPolicy.targetNeedleVisible(true, false, 0.0D));
		assertFalse(TerminalNavigationVisualPolicy.targetNeedleVisible(true, false, 2.0D));
		assertFalse(TerminalNavigationVisualPolicy.targetNeedleVisible(true, false, 20.0D));
	}
}
