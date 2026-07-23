package com.xm.thefourthfrequency.terminal;

public enum TerminalResource {
	IRON(0, "iron"),
	COAL(4, "coal"),
	GOLD(5, "gold"),
	DIAMOND(2, "diamond"),
	NONE(3, "unresolved");

	private final int wireId;
	private final String id;

	TerminalResource(int wireId, String id) {
		this.wireId = wireId;
		this.id = id;
	}

	public int wireId() {
		return wireId;
	}

	public String id() {
		return id;
	}

	public static TerminalResource fromWire(int value) {
		for (TerminalResource resource : values()) if (resource.wireId == value) return resource;
		return NONE;
	}

	public static TerminalResource fromId(String value) {
		for (TerminalResource resource : values()) if (resource.id.equals(value)) return resource;
		return NONE;
	}

	public static boolean isSelectableWire(int value) {
		return false;
	}

	public static TerminalResource weightedRoll(int roll) {
		int value = Math.floorMod(roll, 100);
		if (value < 50) return IRON;
		if (value < 80) return COAL;
		if (value < 90) return GOLD;
		return DIAMOND;
	}
}
