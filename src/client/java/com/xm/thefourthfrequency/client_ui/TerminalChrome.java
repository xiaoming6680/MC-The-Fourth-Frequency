package com.xm.thefourthfrequency.client_ui;

import com.xm.thefourthfrequency.networking.TerminalSnapshotPayload;
import com.xm.thefourthfrequency.terminal.SignalClock;
import com.xm.thefourthfrequency.terminal.TerminalUiLayout;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * The parts of the terminal that are the casing rather than the page: the CRT overlay, the standing
 * status strip, and the bracket work that makes a card read as a panel bolted to something.
 *
 * <p>Separate from {@link TerminalScreen} because that file cannot currently be split - a contract
 * test asserts on its source text - so new drawing goes beside it instead of into it.</p>
 *
 * <p>Everything here is static and stateless. In particular nothing in this file reads the tick
 * clock: the shell does not move. That is a rule rather than an accident, see {@link #drawCrt}.</p>
 */
final class TerminalChrome {
	/**
	 * Distance between scanlines.
	 *
	 * <p>Three rather than two: at this alpha a two-pixel pitch covers half of every glyph and takes
	 * a visible bite out of the pixel font's brightness. Three reads as a raster and still leaves the
	 * text alone.</p>
	 */
	private static final int SCANLINE_PITCH = 3;
	private static final int SCANLINE_ALPHA = 14;
	private static final int VIGNETTE_RINGS = 6;
	private static final int VIGNETTE_ALPHA = 22;
	private static final int VIGNETTE_FALLOFF = 3;
	private static final int BRACKET_LENGTH = 5;
	/** Below this the pixel font stops being readable, so a value is truncated instead of shrunk. */
	private static final float VALUE_MIN_SCALE = 0.5F;

	private TerminalChrome() {
	}

	/**
	 * The standing CRT overlay: fixed scanlines and a soft vignette over the whole display.
	 *
	 * <p>Two properties here are load-bearing rather than stylistic.</p>
	 *
	 * <p><b>It is neutral, never tinted.</b> An earlier version of this layer was a flat green wash.
	 * It tinted every glyph seen through it and cost more contrast than it bought atmosphere, so it
	 * was removed and a contract test now keeps that exact colour from returning. Darkening carries
	 * no hue of its own and leaves the palette underneath intact.</p>
	 *
	 * <p><b>It does not move.</b> A rolling band across the full readable area is the single most
	 * likely thing in this UI to bother a photosensitive player, and it would be on screen the entire
	 * time the terminal is open. Rolling distortion belongs to the sky monitor, where it means the
	 * instrument is failing and lasts only as long as the fault does.</p>
	 *
	 * <p>Costs 59 fills for the lines plus 6 outlines for the vignette. The display is a rectangle,
	 * so the loop bounds are the clip and no scissor is needed.</p>
	 */
	static void drawCrt(GuiGraphics graphics) {
		var display = TerminalUiLayout.DISPLAY;
		int scanline = TerminalVisualTheme.withAlpha(TerminalVisualTheme.SHADOW, SCANLINE_ALPHA);
		for (int y = display.top(); y < display.bottom(); y += SCANLINE_PITCH) {
			graphics.fill(display.left(), y, display.right(), y + 1, scanline);
		}
		for (int ring = 0; ring < VIGNETTE_RINGS; ring++) {
			int alpha = VIGNETTE_ALPHA - ring * VIGNETTE_FALLOFF;
			if (alpha <= 0) break;
			graphics.renderOutline(display.left() + ring, display.top() + ring,
					display.width() - ring * 2, display.height() - ring * 2,
					TerminalVisualTheme.withAlpha(TerminalVisualTheme.SHADOW, alpha));
		}
	}

	/**
	 * The always-on status strip along the bottom of the display.
	 *
	 * <p>Deliberately nothing here is actionable. It is the device describing itself - who it is
	 * bound to, what time it thinks it is, how far its band has opened, which protocol it speaks -
	 * so an anomaly can scramble any of it without stranding a player who was navigating by it.</p>
	 */
	static void drawStatusBar(GuiGraphics graphics, Font font, TerminalSnapshot snapshot, String holder) {
		var bar = TerminalUiLayout.STATUS_BAR;
		// A rule, not a plate. The strip is part of the same sheet of glass as the page above it, and
		// an opaque band across the bottom read as a bar pasted onto the screen instead of etched
		// into it. Structure here comes from the divider and the cell rules, never from a fill.
		graphics.fill(bar.left(), bar.top(), bar.right(), bar.top() + 1, TerminalVisualTheme.DARK_BORDER);

		Component holderValue = holder == null || holder.isBlank()
				? Component.translatable("terminal.thefourthfrequency.status.holder_unbound")
				: Component.literal(holder);
		drawCell(graphics, font, TerminalUiLayout.STATUS_HOLDER,
				Component.translatable("terminal.thefourthfrequency.status.holder"),
				holderValue, TerminalVisualTheme.GREEN, false);
		drawCell(graphics, font, TerminalUiLayout.STATUS_CLOCK,
				Component.translatable("terminal.thefourthfrequency.status.clock"),
				Component.translatable("terminal.thefourthfrequency.status.clock_value",
						snapshot.worldDay(), SignalClock.format(snapshot.worldDayTime())),
				TerminalVisualTheme.DIM, true);
		drawCell(graphics, font, TerminalUiLayout.STATUS_LINK,
				Component.translatable("terminal.thefourthfrequency.status.link"),
				Component.translatable("terminal.thefourthfrequency.status.link_value",
						snapshot.bandStage(), 3),
				TerminalVisualTheme.DIM, true);
		drawCell(graphics, font, TerminalUiLayout.STATUS_PROTOCOL,
				Component.translatable("terminal.thefourthfrequency.status.protocol"),
				Component.literal(Integer.toString(TerminalSnapshotPayload.CURRENT_PROTOCOL_VERSION)),
				TerminalVisualTheme.MUTED, true);
	}

	/**
	 * One reading: a dim fixed label, then the value in whatever room is left.
	 *
	 * <p>Split so the label never shrinks. Scaling the pair together meant a long holder name
	 * dragged "HOLDER" down with it, and past the scale floor the whole thing simply ran out of the
	 * cell and into its neighbour.</p>
	 */
	private static void drawCell(GuiGraphics graphics, Font font, TerminalUiLayout.Bounds cell,
			Component label, Component value, int valueColor, boolean divider) {
		if (divider) {
			graphics.fill(cell.left() - 2, cell.top() + 2, cell.left() - 1, cell.bottom() - 2,
					TerminalVisualTheme.DARK_BORDER);
		}
		int baseline = cell.top() + (cell.height() - font.lineHeight) / 2;
		graphics.drawString(font, label, cell.left() + 1, baseline, TerminalVisualTheme.MUTED, false);
		int valueLeft = cell.left() + 3 + font.width(label);
		drawFittedValue(graphics, font, value, valueLeft, cell.right() - 1, baseline, valueColor);
	}

	/**
	 * Draws a value inside a hard right edge: full size if it fits, then scaled, then truncated.
	 *
	 * <p>A sixteen-character name is more than twice the width of a short one, so a single scale
	 * floor cannot serve both. Below {@link #VALUE_MIN_SCALE} the pixel font stops being readable, so
	 * anything still too long loses its tail to an ellipsis rather than being shrunk into a smear or
	 * allowed to run past the cell.</p>
	 */
	private static void drawFittedValue(GuiGraphics graphics, Font font, Component value,
			int left, int right, int baseline, int color) {
		int room = right - left;
		if (room <= 0) return;
		int width = font.width(value);
		if (width <= room) {
			graphics.drawString(font, value, left, baseline, color, false);
			return;
		}
		float scale = Math.max(VALUE_MIN_SCALE, room / (float) width);
		// Only resolved once it is known not to fit, so the common case never walks the language table.
		String text = value.getString();
		if (width * scale > room) text = truncate(font, text, Math.round(room / scale));
		graphics.pose().pushMatrix();
		graphics.pose().translate(left, baseline + font.lineHeight * (1.0F - scale) / 2.0F);
		graphics.pose().scale(scale, scale);
		graphics.drawString(font, text, 0, 0, color, false);
		graphics.pose().popMatrix();
	}

	/** Longest code-point prefix that fits in {@code room}, with an ellipsis standing for the rest. */
	private static String truncate(Font font, String text, int room) {
		String ellipsis = "…";
		int budget = room - font.width(ellipsis);
		if (budget <= 0) return "";
		StringBuilder kept = new StringBuilder();
		int used = 0;
		for (int index = 0; index < text.length(); ) {
			int codePoint = text.codePointAt(index);
			String glyph = new String(Character.toChars(codePoint));
			int glyphWidth = font.width(glyph);
			if (used + glyphWidth > budget) break;
			kept.append(glyph);
			used += glyphWidth;
			index += Character.charCount(codePoint);
		}
		return kept + ellipsis;
	}

	/**
	 * Four corner brackets around a card, so it reads as a plate fastened to the casing rather than
	 * a rectangle of a slightly different colour.
	 *
	 * <p>Eight fills. Drawn inside the card's own outline, so it never widens the region and the
	 * layout tests keep meaning what they say.</p>
	 */
	static void drawCornerBrackets(GuiGraphics graphics, TerminalUiLayout.Bounds bounds, int color) {
		int left = bounds.left();
		int top = bounds.top();
		int right = bounds.right();
		int bottom = bounds.bottom();
		int span = Math.min(BRACKET_LENGTH, Math.min(bounds.width(), bounds.height()) / 2);
		if (span <= 1) return;
		graphics.fill(left, top, left + span, top + 1, color);
		graphics.fill(left, top, left + 1, top + span, color);
		graphics.fill(right - span, top, right, top + 1, color);
		graphics.fill(right - 1, top, right, top + span, color);
		graphics.fill(left, bottom - 1, left + span, bottom, color);
		graphics.fill(left, bottom - span, left + 1, bottom, color);
		graphics.fill(right - span, bottom - 1, right, bottom, color);
		graphics.fill(right - 1, bottom - span, right, bottom, color);
	}

}
