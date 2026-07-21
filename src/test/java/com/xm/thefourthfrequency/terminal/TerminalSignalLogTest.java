package com.xm.thefourthfrequency.terminal;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class TerminalSignalLogTest {
	@Test
	void preservesLegacyWireIdsWithoutExposingDialStations() {
		assertEquals(0, SignalBand.WEATHER.wireId());
		assertEquals(1, SignalBand.MINING.wireId());
		assertEquals(2, SignalBand.UNKNOWN.wireId());
		assertEquals(3, SignalBand.PUBLIC.wireId());
		for (SignalBand band : SignalBand.values()) assertEquals(band, SignalBand.fromWire(band.wireId()));
		assertEquals(SignalBand.UNKNOWN, SignalBand.fromWire(99));
	}

	@Test
	void formatsMinecraftDayClockWithSixAmAtZero() {
		assertEquals("06:00", SignalClock.format(0));
		assertEquals("12:00", SignalClock.format(6_000));
		assertEquals("18:00", SignalClock.format(12_000));
		assertEquals("00:00", SignalClock.format(18_000));
		assertEquals("06:00", SignalClock.format(24_000));
	}

}
