package com.xm.thefourthfrequency.client_ui;

/** Shared pixel-terminal palette. Values stay centralized so terminal-adjacent screens cannot visually drift. */
final class TerminalVisualTheme {
	static final int GREEN = 0xFF91C58D;
	static final int CYAN = 0xFF7FD2D8;
	static final int DIM = 0xFF58725B;
	static final int AMBER = 0xFFD3B56F;
	static final int HOT = 0xFFE06565;
	static final int MUTED = 0xFF405244;
	static final int MUTED_DARK = 0xFF243229;
	static final int GLASS = 0xD907110B;
	static final int SELECTED = 0xFF243A29;
	static final int ALERT_BACKGROUND = 0xFF481B20;
	static final int DARK_BORDER = 0xFF304535;
	static final int LCD_BACKGROUND = 0xFF0A160C;
	static final int LCD_BORDER = 0xFF6F7543;

	private TerminalVisualTheme() {
	}

	static int withAlpha(int color, int alpha) {
		return Math.max(0, Math.min(255, alpha)) << 24 | color & 0x00FFFFFF;
	}
}
