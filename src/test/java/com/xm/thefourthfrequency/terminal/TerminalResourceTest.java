package com.xm.thefourthfrequency.terminal;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class TerminalResourceTest {
	@Test
	void onlyTheThreePublishedMineralsAreSelectableControlValues() {
		assertEquals(TerminalResource.IRON, TerminalResource.fromWire(0));
		assertEquals(TerminalResource.REDSTONE, TerminalResource.fromWire(1));
		assertEquals(TerminalResource.DIAMOND, TerminalResource.fromWire(2));
		assertEquals(TerminalResource.NONE, TerminalResource.fromWire(3));
		assertEquals(TerminalResource.NONE, TerminalResource.fromWire(99));
		for (int value = 0; value <= 2; value++) assertTrue(TerminalResource.isSelectableWire(value));
		assertFalse(TerminalResource.isSelectableWire(-1));
		assertFalse(TerminalResource.isSelectableWire(3));
	}
}
