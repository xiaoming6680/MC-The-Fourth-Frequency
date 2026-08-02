package com.xm.thefourthfrequency.client_ui;

/** Palette sampled for the independently generated notice UI, without importing terminal screen assets or constants. */
final class FirstRunNoticePalette {
	static final int GREEN = 0xFF9CC692;
	static final int CYAN = 0xFF55BCC4;
	static final int DIM = 0xFF647A63;
	static final int AMBER = 0xFFD1A94D;
	static final int HOT = 0xFFD15353;
	static final int DISABLED_RAIL = 0xFF566554;
	/** Letterbox behind the metal shell, and the unlit CRT glass the copy fades in and out of. */
	static final int SHELL_BACKDROP = 0xFF080D0A;
	static final int GLASS_BLACKOUT = 0xFF020A06;
	/** Striking phosphor outruns every lit text colour, so power-on needs its own near-white. */
	static final int PHOSPHOR_FLASH = 0xFFE8F4E4;

	private FirstRunNoticePalette() {
	}

	static int withAlpha(int color, int alpha) {
		return Math.max(0, Math.min(255, alpha)) << 24 | color & 0x00FFFFFF;
	}
}
