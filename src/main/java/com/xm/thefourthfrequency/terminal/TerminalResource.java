package com.xm.thefourthfrequency.terminal;

public enum TerminalResource {
	IRON(0, "iron"),
	COAL(4, "coal"),
	GOLD(5, "gold"),
	DIAMOND(2, "diamond"),
	/** Wire id 1 was never issued, so emerald could join without renumbering the others. */
	EMERALD(1, "emerald"),
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
}
