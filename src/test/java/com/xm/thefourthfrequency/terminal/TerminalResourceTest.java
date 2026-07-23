package com.xm.thefourthfrequency.terminal;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
final class TerminalResourceTest {
	@Test
	void fourPublishedMineralsHaveStableServerOnlyWireValues() {
		assertEquals(TerminalResource.IRON, TerminalResource.fromWire(0));
		assertEquals(TerminalResource.DIAMOND, TerminalResource.fromWire(2));
		assertEquals(TerminalResource.NONE, TerminalResource.fromWire(3));
		assertEquals(TerminalResource.COAL, TerminalResource.fromWire(4));
		assertEquals(TerminalResource.GOLD, TerminalResource.fromWire(5));
		assertEquals(TerminalResource.NONE, TerminalResource.fromWire(1));
		assertEquals(TerminalResource.NONE, TerminalResource.fromWire(99));
		for (int value = -1; value <= 6; value++) {
			assertEquals(false, TerminalResource.isSelectableWire(value));
		}
	}

	@Test
	void weightedRefreshUsesThePublishedFiftyThirtyTenTenDistribution() {
		for (int roll = 0; roll < 50; roll++)
			assertEquals(TerminalResource.IRON, TerminalResource.weightedRoll(roll));
		for (int roll = 50; roll < 80; roll++)
			assertEquals(TerminalResource.COAL, TerminalResource.weightedRoll(roll));
		for (int roll = 80; roll < 90; roll++)
			assertEquals(TerminalResource.GOLD, TerminalResource.weightedRoll(roll));
		for (int roll = 90; roll < 100; roll++)
			assertEquals(TerminalResource.DIAMOND, TerminalResource.weightedRoll(roll));
	}
}
