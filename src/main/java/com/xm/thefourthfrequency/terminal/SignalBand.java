package com.xm.thefourthfrequency.terminal;

import java.util.Locale;

public enum SignalBand {
	WEATHER(0),
	MINING(1),
	UNKNOWN(2),
	PUBLIC(3);

	private final int wireId;

	SignalBand(int wireId) {
		this.wireId = wireId;
	}

	public int wireId() { return wireId; }
	public String id() { return name().toLowerCase(Locale.ROOT); }

	public static SignalBand fromWire(int value) {
		for (SignalBand band : values()) if (band.wireId == value) return band;
		return UNKNOWN;
	}
}
