package com.xm.thefourthfrequency.meta_api;

public enum MetaEvent {
	FINAL_BODY_AWAKENED(0),
	TERMINAL_CAPTURED(1),
	FOURTH_BAND_TERMINATED(2),
	PREVENTION_FAILED(3),
	UNDISCOVERED_BETRAYAL(4);

	private final int wireId;

	MetaEvent(int wireId) {
		this.wireId = wireId;
	}

	public int wireId() {
		return wireId;
	}

	public static MetaEvent fromWireId(int wireId) {
		for (MetaEvent event : values()) if (event.wireId == wireId) return event;
		throw new IllegalArgumentException("Unknown fixed Meta event ID " + wireId);
	}
}
