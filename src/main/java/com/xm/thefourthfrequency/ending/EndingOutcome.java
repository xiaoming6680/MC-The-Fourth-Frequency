package com.xm.thefourthfrequency.ending;

public enum EndingOutcome {
	ACTIVE("active"),
	UNDISCOVERED("undiscovered_truth"),
	FAILED("prevention_failed"),
	SUCCESS("prevention_succeeded");

	private final String id;

	EndingOutcome(String id) {
		this.id = id;
	}

	public String id() {
		return id;
	}

	public static EndingOutcome fromId(String id) {
		for (EndingOutcome value : values()) {
			if (value.id.equals(id)) return value;
		}
		return ACTIVE;
	}
}
