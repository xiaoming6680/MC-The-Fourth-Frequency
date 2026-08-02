package com.xm.thefourthfrequency.terminal;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class AnomalyScriptTest {
	@Test
	void menuStageMapsCeilingAndSuccessExactly() {
		assertEquals(1, MenuErosionRules.stageFor(1, false));
		assertEquals(1, MenuErosionRules.stageFor(2, false));
		assertEquals(2, MenuErosionRules.stageFor(3, false));
		assertEquals(2, MenuErosionRules.stageFor(4, false));
		assertEquals(3, MenuErosionRules.stageFor(5, false));
		assertEquals(4, MenuErosionRules.stageFor(5, true));
	}

	@Test
	void fauxChatTypesDeletesSuffixAndNeverSendsAnything() {
		// textAt() no longer hardcodes its text (it is supplied client-side, localized), so this
		// exercises it with the same Chinese strings the mod ships by default and verifies the
		// shared "我" prefix between line 2 and line 3 is still derived correctly from them.
		String first = "有人能看到吗？";
		String second = "我没有断开。";
		String finalLine = "我正在看着你。";
		assertEquals("", ChannelOverrideScript.textAt(0, first, second, finalLine));
		assertEquals("有人能看到吗？", ChannelOverrideScript.textAt(50, first, second, finalLine));
		assertEquals("", ChannelOverrideScript.textAt(95, first, second, finalLine));
		assertEquals("我没有断开。", ChannelOverrideScript.textAt(150, first, second, finalLine));
		assertEquals("我", ChannelOverrideScript.textAt(205, first, second, finalLine));
		assertEquals("我正在看着你。", ChannelOverrideScript.textAt(240, first, second, finalLine));
		assertEquals("我正在看着你。", ChannelOverrideScript.textAt(300, first, second, finalLine));
	}

	@Test
	void fauxChatDerivesSharedPrefixFromWhicheverStringsAreSupplied() {
		// A differently shaped (English) prefix must still resolve correctly since the shared
		// prefix is computed from the actual strings, not assumed to be a single character. Here
		// "I have not disconnected." and "I am watching you." share a two-character prefix ("I ").
		String first = "Can anyone see this?";
		String second = "I have not disconnected.";
		String finalLine = "I am watching you.";
		assertEquals("I ", ChannelOverrideScript.textAt(205, first, second, finalLine));
		assertEquals("I am watching you.", ChannelOverrideScript.textAt(240, first, second, finalLine));
	}

	@Test
	void doorSelectionRequiresMultipleDoorsAndCapsTheFarToNearCascade() {
		assertEquals(0, AnomalySelectionRules.doorCount(0, 1));
		assertEquals(0, AnomalySelectionRules.doorCount(1, 2));
		for (long seed = -12; seed <= 12; seed++) {
			int count = AnomalySelectionRules.doorCount(20, seed);
			assertEquals(6, count);
		}
		assertEquals(2, AnomalySelectionRules.doorCount(2, 1));
		assertEquals(5, AnomalySelectionRules.doorCount(5, Long.MIN_VALUE));
	}

	@Test
	void caveRequiresNoDirectSkyLowSkyLightAndFourEnclosingDirections() {
		assertTrue(AnomalySelectionRules.caveLike(false, 4, 4));
		assertFalse(AnomalySelectionRules.caveLike(true, 0, 6));
		assertFalse(AnomalySelectionRules.caveLike(false, 5, 6));
		assertFalse(AnomalySelectionRules.caveLike(false, 0, 3));
	}
}
