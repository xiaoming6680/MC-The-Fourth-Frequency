package com.xm.thefourthfrequency.terminal;

public enum TerminalPage {
	HOME(TerminalControlPolicy.Mode.SIGNAL),
	TOOLS(TerminalControlPolicy.Mode.SIGNAL),
	RECORDS(TerminalControlPolicy.Mode.SIGNAL),
	FILES(TerminalControlPolicy.Mode.FILES);

	private final TerminalControlPolicy.Mode wireMode;

	TerminalPage(TerminalControlPolicy.Mode wireMode) {
		this.wireMode = wireMode;
	}

	public int wireMode() {
		return wireMode.ordinal();
	}

	public static TerminalPage initialPage(int wireMode) {
		return TerminalControlPolicy.Mode.fromWire(wireMode) == TerminalControlPolicy.Mode.FILES ? FILES : HOME;
	}

	public static TerminalPage fromIndex(int index) {
		return values()[Math.clamp(index, 0, values().length - 1)];
	}
}
