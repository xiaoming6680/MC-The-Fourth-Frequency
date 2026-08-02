package com.xm.thefourthfrequency.client_ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * The analog-horror layer drawn over the first-entry loading screen.
 *
 * <p>The failure text underneath this is the message; everything here is the medium carrying it.
 * Scanlines, chroma bleed, a tracking band, a running timecode and dead air are what make the
 * sequence read as a recording of a failure rather than as a mod drawing red words - and a
 * recording is far harder to dismiss, because it implies the failure already happened to someone
 * else before it happened to you.</p>
 *
 * <p>Timing lives entirely in {@link AlphaLoadTimeline}, which is plain arithmetic and unit
 * tested. Nothing here decides <em>when</em>; it only decides what a given tick looks like.</p>
 */
public final class AlphaCorruptionRenderer {
	private static final String TIMECODE_KEY = "screen.thefourthfrequency.alpha_loading.timecode";
	private static final String TIMECODE_LOST = "--:--:--";
	private static final int SCANLINE_COLOR = 0x000306;
	private static final int CHROMA_RED = 0xFF2A2A;
	private static final int CHROMA_CYAN = 0x22E0FF;
	private static final int MAX_CHROMA_ALPHA = 150;
	private static final int TIMECODE_COLOR = 0xFFD8D2C6;
	private static final int TIMECODE_DOT_COLOR = 0xFFB4322C;
	private static final int TIMECODE_MARGIN = 6;
	private static final int DEAD_AIR_SPECKS = 96;
	private static final int COLLAPSE_CORE_COLOR = 0xE6E2D8;

	private AlphaCorruptionRenderer() {
	}

	/**
	 * Every layer that belongs on top of a picture that still exists.
	 *
	 * <p>Called after the failure contents so the damage sits over them, never under.</p>
	 */
	public static void drawMediumLayers(GuiGraphics graphics, int screenTicks) {
		drawTrackingBand(graphics, screenTicks);
		drawScanlines(graphics, screenTicks);
		drawTimecode(graphics, screenTicks);
	}

	public static void drawScanlines(GuiGraphics graphics, int screenTicks) {
		int alpha = AlphaLoadTimeline.scanlineAlpha(screenTicks);
		if (alpha <= 0) return;
		int width = graphics.guiWidth();
		int color = alpha << 24 | SCANLINE_COLOR;
		// Anchored to the viewport, not to the tick: scanlines that crawl look like an animation,
		// and the whole point is that they look like the screen itself.
		for (int y = 0; y < graphics.guiHeight(); y += AlphaLoadTimeline.SCANLINE_SPACING) {
			graphics.fill(0, y, width, y + 1, color);
		}
	}

	/**
	 * A horizontal band of lost tracking, scrolling slowly upward.
	 *
	 * <p>Drawn as displaced streaks plus a blown-out top edge rather than as a re-render of the
	 * shifted picture: the streaks carry the read at a fraction of the cost, and at this band
	 * height the difference is not visible.</p>
	 */
	public static void drawTrackingBand(GuiGraphics graphics, int screenTicks) {
		int top = AlphaLoadTimeline.trackingBandTop(screenTicks, graphics.guiHeight());
		if (top == Integer.MIN_VALUE) return;
		int width = graphics.guiWidth();
		int height = AlphaLoadTimeline.trackingBandHeight(screenTicks);
		int shift = AlphaLoadTimeline.trackingBandShift(screenTicks);
		int bottom = Math.min(graphics.guiHeight(), top + height);
		if (bottom <= 0 || top >= graphics.guiHeight()) return;

		graphics.fill(0, Math.max(0, top), width, bottom, 0x2CFFFFFF);
		for (int y = Math.max(0, top); y < bottom; y++) {
			int seed = AlphaLoadTimeline.noise(y * 0x9E3779B9
					+ AlphaLoadTimeline.failureMotionTick(screenTicks));
			int streakLeft = Math.floorMod(seed, Math.max(1, width)) + shift;
			int streakWidth = 8 + Math.floorMod(seed >>> 11, 46);
			int streakAlpha = 22 + Math.floorMod(seed >>> 19, 44);
			graphics.fill(Math.max(0, streakLeft), y,
					Math.min(width, streakLeft + streakWidth), y + 1,
					streakAlpha << 24 | 0xC8C4BC);
		}
		if (top >= 0) graphics.fill(0, top, width, top + 1, 0x66FFFFFF);
		if (bottom <= graphics.guiHeight() - 1) {
			graphics.fill(0, bottom - 1, width, bottom, 0x59000000);
		}
	}

	/** The running counter that tells the player they are watching this rather than doing it. */
	public static void drawTimecode(GuiGraphics graphics, int screenTicks) {
		if (!AlphaLoadTimeline.timecodeVisible(screenTicks)) return;
		Font font = Minecraft.getInstance().font;
		String elapsed = AlphaLoadTimeline.timecodeCorrupted(screenTicks)
				? TIMECODE_LOST : AlphaLoadTimeline.timecodeText(screenTicks);
		String text = Component.translatable(TIMECODE_KEY, elapsed).getString();
		int y = graphics.guiHeight() - TIMECODE_MARGIN - font.lineHeight;
		drawChromaString(graphics, font, text, TIMECODE_MARGIN, y, TIMECODE_COLOR, screenTicks);
		// The record dot keeps its own slow blink, independent of everything else on screen.
		if (Math.floorMod(screenTicks, 20) < 13) {
			graphics.fill(TIMECODE_MARGIN, y + 2, TIMECODE_MARGIN + 3, y + 5, TIMECODE_DOT_COLOR);
		}
	}

	/**
	 * Dead air: a collapsing picture, then noise that is never quite black.
	 *
	 * <p>Two seconds of true black reads as a bug. Two seconds of a screen that is still clearly
	 * powered on and receiving nothing does not.</p>
	 */
	public static void drawDeadAir(GuiGraphics graphics, int screenTicks) {
		int width = graphics.guiWidth();
		int height = graphics.guiHeight();
		float collapse = AlphaLoadTimeline.blackoutCollapseProgress(screenTicks);
		if (collapse > 0.0F) {
			int centerY = height / 2;
			int lineHeight = Math.max(1, Math.round(height * 0.5F * collapse));
			int inset = Math.round(width * 0.5F * (1.0F - collapse));
			int alpha = Math.round(Math.clamp(collapse, 0.0F, 1.0F) * 235.0F);
			graphics.fill(inset, centerY - lineHeight, width - inset, centerY + lineHeight,
					alpha << 24 | COLLAPSE_CORE_COLOR);
		}
		int noiseAlpha = AlphaLoadTimeline.deadAirNoiseAlpha(screenTicks);
		if (noiseAlpha <= 0) return;
		for (int speck = 0; speck < DEAD_AIR_SPECKS; speck++) {
			int seed = AlphaLoadTimeline.noise(speck * 0x85EBCA6B + screenTicks * 0xC2B2AE35);
			int x = Math.floorMod(seed, Math.max(1, width));
			int y = Math.floorMod(seed >>> 9, Math.max(1, height));
			int size = 1 + Math.floorMod(seed >>> 21, 2);
			graphics.fill(x, y, x + size, y + size, noiseAlpha << 24 | 0xBFBAB0);
		}
	}

	/**
	 * The picture coming back, but not cleanly.
	 *
	 * <p>Cutting straight from dead air to a stable Alpha loading screen would hand the player
	 * the reassurance that the worst is over. Instead the picture arrives still rolling and
	 * settles over a second or so, and the scanlines it settles onto never leave - the recovery
	 * is real, but it is a recovery to a worse baseline than the one the session started from.</p>
	 */
	public static void drawRecoveryLock(GuiGraphics graphics, int screenTicks) {
		float strength = AlphaLoadTimeline.recoveryLockStrength(screenTicks);
		if (strength > 0.0F) {
			int width = graphics.guiWidth();
			int height = graphics.guiHeight();
			int rollHeight = Math.max(2, Math.round(26 * strength));
			int rollTop = height - Math.floorMod(screenTicks * 7, height + rollHeight);
			int alpha = Math.round(120 * strength);
			graphics.fill(0, Math.max(0, rollTop), width,
					Math.min(height, rollTop + rollHeight), alpha << 24 | 0xD6D2C8);
		}
		drawScanlines(graphics, screenTicks);
	}

	/** Text with the red and cyan ghosts a worn tape leaves either side of every edge. */
	public static void drawChromaString(GuiGraphics graphics, Font font, String text, int x, int y,
			int color, int screenTicks) {
		int offset = Math.round(AlphaLoadTimeline.chromaOffset(screenTicks));
		if (offset > 0) {
			int ghostAlpha = chromaGhostAlpha(screenTicks);
			graphics.drawString(font, text, x - offset, y, ghostAlpha << 24 | CHROMA_RED, false);
			graphics.drawString(font, text, x + offset, y, ghostAlpha << 24 | CHROMA_CYAN, false);
		}
		graphics.drawString(font, text, x, y, color, false);
	}

	public static void drawChromaCenteredString(GuiGraphics graphics, Font font, String text,
			int centerX, int y, int color, int screenTicks) {
		drawChromaString(graphics, font, text, centerX - font.width(text) / 2, y, color, screenTicks);
	}

	private static int chromaGhostAlpha(int screenTicks) {
		float strength = Math.clamp(AlphaLoadTimeline.chromaOffset(screenTicks) / 3.0F, 0.0F, 1.0F);
		return Math.round(strength * MAX_CHROMA_ALPHA);
	}
}
