package com.xm.thefourthfrequency.terminal;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
final class TerminalResourceTest {
	@Test
	void publishedMineralsHaveStableServerOnlyWireValues() {
		assertEquals(TerminalResource.IRON, TerminalResource.fromWire(0));
		assertEquals(TerminalResource.EMERALD, TerminalResource.fromWire(1));
		assertEquals(TerminalResource.DIAMOND, TerminalResource.fromWire(2));
		assertEquals(TerminalResource.NONE, TerminalResource.fromWire(3));
		assertEquals(TerminalResource.COAL, TerminalResource.fromWire(4));
		assertEquals(TerminalResource.GOLD, TerminalResource.fromWire(5));
		assertEquals(TerminalResource.NONE, TerminalResource.fromWire(99));
		for (int value = -1; value <= 6; value++) {
			assertEquals(false, TerminalResource.isSelectableWire(value));
		}
	}

	@Test
	void everyMineralRoundTripsThroughItsIdentifier() {
		for (TerminalResource resource : TerminalResource.values()) {
			assertEquals(resource, TerminalResource.fromId(resource.id()));
			assertEquals(resource, TerminalResource.fromWire(resource.wireId()));
		}
	}
}
