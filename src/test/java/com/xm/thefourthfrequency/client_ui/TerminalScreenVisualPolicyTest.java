package com.xm.thefourthfrequency.client_ui;

import com.xm.thefourthfrequency.terminal.TerminalNavigationVisualPolicy;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class TerminalScreenVisualPolicyTest {
	@Test
	void sideRouteGlitchStartsEveryTwoSecondsAndLastsHalfASecond() {
		assertFalse(TerminalNavigationVisualPolicy.sideRouteGlitchActive(39.99D));
		assertTrue(TerminalNavigationVisualPolicy.sideRouteGlitchActive(40.0D));
		assertTrue(TerminalNavigationVisualPolicy.sideRouteGlitchActive(49.99D));
		assertFalse(TerminalNavigationVisualPolicy.sideRouteGlitchActive(50.0D));
		assertFalse(TerminalNavigationVisualPolicy.sideRouteGlitchActive(79.99D));
		assertTrue(TerminalNavigationVisualPolicy.sideRouteGlitchActive(80.0D));
	}

	@Test
	void sideRouteGlitchChangesOnlyOneOrTwoCharactersAndIsStableForTheCycle() {
		String original = "废弃矿井";
		String corrupted = TerminalNavigationVisualPolicy.corruptNavigationName(original, 2, 3L);
		assertEquals(corrupted, TerminalNavigationVisualPolicy.corruptNavigationName(original, 2, 3L));
		int[] before = original.codePoints().toArray();
		int[] after = corrupted.codePoints().toArray();
		int changed = 0;
		for (int index = 0; index < before.length; index++) {
			if (before[index] != after[index]) changed++;
		}
		assertTrue(changed >= 1 && changed <= 2);
	}

	@Test
	void oneSecondNeedleFlashAndProbeDotsRemainBounded() {
		assertFalse(TerminalNavigationVisualPolicy.navigationNeedleFlashActive(-0.01D));
		assertTrue(TerminalNavigationVisualPolicy.navigationNeedleFlashActive(0.0D));
		assertTrue(TerminalNavigationVisualPolicy.navigationNeedleFlashActive(19.99D));
		assertFalse(TerminalNavigationVisualPolicy.navigationNeedleFlashActive(20.0D));
		assertTrue(TerminalNavigationVisualPolicy.navigationNeedleFlashVisible(0.0D));
		assertFalse(TerminalNavigationVisualPolicy.navigationNeedleFlashVisible(2.0D));
		assertTrue(TerminalNavigationVisualPolicy.navigationNeedleFlashVisible(4.0D));
		assertFalse(TerminalNavigationVisualPolicy.navigationNeedleFlashVisible(18.0D));
		assertTrue(TerminalNavigationVisualPolicy.navigationNeedleFlashVisible(20.0D));
		assertEquals(1, TerminalNavigationVisualPolicy.animatedProbeDots(0.0D));
		assertEquals(2, TerminalNavigationVisualPolicy.animatedProbeDots(5.0D));
		assertEquals(3, TerminalNavigationVisualPolicy.animatedProbeDots(10.0D));
		assertEquals(1, TerminalNavigationVisualPolicy.animatedProbeDots(15.0D));
	}
}
