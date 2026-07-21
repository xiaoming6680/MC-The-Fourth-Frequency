package com.xm.thefourthfrequency.client_ui;

/** Palette sampled for the independently generated notice UI, without importing terminal screen assets or constants. */
final class FirstRunNoticePalette {
	static final int GREEN = 0xFF9CC692;
	static final int CYAN = 0xFF55BCC4;
	static final int DIM = 0xFF647A63;
	static final int AMBER = 0xFFD1A94D;
	static final int HOT = 0xFFD15353;
	static final int DISABLED_RAIL = 0xFF566554;

	private FirstRunNoticePalette() {
	}

	static int withAlpha(int color, int alpha) {
		return Math.max(0, Math.min(255, alpha)) << 24 | color & 0x00FFFFFF;
	}
}
