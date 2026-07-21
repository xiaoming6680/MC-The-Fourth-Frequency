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
		assertEquals("", ChannelOverrideScript.textAt(0));
		assertEquals("有人能看到吗？", ChannelOverrideScript.textAt(50));
		assertEquals("", ChannelOverrideScript.textAt(95));
		assertEquals("我没有断开。", ChannelOverrideScript.textAt(150));
		assertEquals("我", ChannelOverrideScript.textAt(205));
		assertEquals("我正在看着你。", ChannelOverrideScript.textAt(240));
		assertEquals("我正在看着你。", ChannelOverrideScript.textAt(300));
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
