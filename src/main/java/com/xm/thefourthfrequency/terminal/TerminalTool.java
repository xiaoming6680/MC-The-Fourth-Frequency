package com.xm.thefourthfrequency.terminal;

public enum TerminalTool {
	HOME(0, "home"),
	MINERALS(1, "minerals"),
	PORTAL(2, "portal"),
	WEATHER(3, "weather"),
	NAVIGATION(4, "navigation"),
	STRONGHOLD(5, "stronghold");

	private final int slot;
	private final String id;

	TerminalTool(int slot, String id) {
		this.slot = slot;
		this.id = id;
	}

	public int slot() {
		return slot;
	}

	public String id() {
		return id;
	}

	public static TerminalTool fromSlot(int slot) {
		for (TerminalTool tool : values()) if (tool.slot == slot) return tool;
		return null;
	}
}
