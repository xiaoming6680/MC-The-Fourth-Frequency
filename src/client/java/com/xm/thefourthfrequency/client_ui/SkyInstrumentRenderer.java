package com.xm.thefourthfrequency.client_ui;

import com.xm.thefourthfrequency.terminal.SkyInstrumentPolicy;
import com.xm.thefourthfrequency.terminal.TerminalUiLayout;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import static com.xm.thefourthfrequency.client_ui.TerminalVisualTheme.AMBER;
import static com.xm.thefourthfrequency.client_ui.TerminalVisualTheme.DARK_BORDER;
import static com.xm.thefourthfrequency.client_ui.TerminalVisualTheme.DIM;
import static com.xm.thefourthfrequency.client_ui.TerminalVisualTheme.GREEN;
import static com.xm.thefourthfrequency.client_ui.TerminalVisualTheme.HOT;

/**
 * The weather tool's sky monitor, and the medium it is failing through.
 *
 * <p>Two things are drawn here and they answer to different rules. The <em>readings</em> are an
 * instrument: four channels sampled from the sky the player is actually being shown, free to
 * saturate, drop to nothing and disagree with each other, because nothing survival-critical hangs
 * on them. The <em>fault flood, tearing and snow</em> are the medium carrying those readings
 * failing, and they are bounded - by stage, by burst window, by a scissor rectangle - so that the
 * page can be left at any moment and comes back clean when the anomaly ends.</p>
 *
 * <p>Timing is entirely {@link SkyInstrumentPolicy}, which is plain arithmetic and unit tested.
 * Nothing here decides <em>when</em>; it only decides what a given tick looks like. That split is
 * what lets the 3 Hz flicker ceiling be a test rather than a promise.</p>
 */
public final class SkyInstrumentRenderer {
	/**
	 * Top of the band the monitor owns, below the weather tool's text lines and above its pin
	 * button. Public because {@code TerminalScreen} has to stop wrapping detail text here: without
	 * that ceiling a summary that happens to take two lines in one language - and one in another -
	 * would silently print over the readings.
	 */
	public static final int INSTRUMENT_TOP = 128;
	private static final TerminalUiLayout.Bounds INSTRUMENT =
			new TerminalUiLayout.Bounds(50, INSTRUMENT_TOP, 342, 163);
	private static final int CHANNEL_COLUMN_RIGHT = 198;
	private static final int CHANNEL_ROW_HEIGHT = 8;
	private static final int CHANNEL_FIRST_Y = 129;
	private static final TerminalUiLayout.Bounds TRACE =
			new TerminalUiLayout.Bounds(204, 128, 342, 163);
	private static final float LABEL_SCALE = 0.72F;
	private static final int FLOOD_BOTTOM = 162;
	private static final int FLOOD_LINE_HEIGHT = 9;
	private static final int SNOW_CELLS = 220;
	private static final int SCANLINE_SPACING = 3;
	/** What {@code SkyInstrumentPolicy.scanlineAlpha} peaks at, as a 0-to-1 strength for AnalogFilter. */
	private static final int MAX_SCANLINE_ALPHA = 58;
	private static final int FLOOD_BACKDROP = 0xE60A0304;
	private static final int FAULT_COLOR = 0xFFE28A86;
	private static final String KEY = "terminal.thefourthfrequency.tool.weather.";
	/**
	 * The fault pool. Length must stay equal to {@link SkyInstrumentPolicy#FAULT_MESSAGE_COUNT};
	 * the resource contract test asserts every one of these has a key in both languages.
	 */
	private static final String[] FAULTS = {
			"no_carrier", "rejected", "saturated", "phase_lost",
			"resync", "dome_timeout", "star_underflow", "clock_mismatch"};

	private SkyInstrumentRenderer() {
	}

	/** Current envelope for the running anomaly, or zero under an undisturbed sky. */
	public static float instability() {
		String id = AnomalyPresentationController.activeId();
		if (!SkyInstrumentPolicy.isSkyAnomaly(id)) return 0.0F;
		int total = AnomalyPresentationController.totalTicks();
		int remaining = AnomalyPresentationController.remainingTicks();
		return SkyInstrumentPolicy.instability(id, total - remaining, remaining, total);
	}

	/** Whether the tool's own actionable line is unreadable this frame. */
	public static boolean readoutLost(double renderAge) {
		return SkyInstrumentPolicy.readoutLost(renderAge, instability());
	}

	/**
	 * Draws the monitor into the weather tool's detail card.
	 *
	 * <p>The scissor rectangle is given in the terminal's own 512x256 coordinate space, not in
	 * screen pixels: {@code GuiGraphics#enableScissor} runs the rectangle through the current pose
	 * itself, and this is called from inside the translate-and-scale the whole terminal face is
	 * drawn under. Converting to screen pixels first would have the panel transform applied twice
	 * and put the clip somewhere off-screen, which silently blanks the entire card.</p>
	 *
	 * <p>Clipping is not cosmetic here. Torn rows are displaced horizontally and snow is scattered
	 * across the card, so without it both spill over the page tabs and the close hint - the
	 * controls the player needs in order to leave this page.</p>
	 */
	public static void draw(GuiGraphics graphics, Font font, double renderAge, double floodTicks) {
		SkyInstrumentSampler.markObserved();
		float instability = instability();
		int stage = SkyInstrumentPolicy.stage(instability, renderAge);

		var card = TerminalUiLayout.TOOL_DETAIL;
		graphics.enableScissor(card.left(), card.top(), card.right(), card.bottom());

		drawChannels(graphics, font, instability, stage, renderAge);
		drawTrace(graphics, font, stage, renderAge);
		if (stage >= 2) {
			drawTornRows(graphics, stage, renderAge);
			drawRollBar(graphics, stage, renderAge);
			drawFlood(graphics, font, stage, renderAge, floodTicks);
		}
		if (stage >= 3) drawSnow(graphics, renderAge);
		drawScanlines(graphics, stage);

		graphics.disableScissor();
	}

	private static void drawChannels(GuiGraphics graphics, Font font, float instability, int stage,
			double renderAge) {
		graphics.fill(INSTRUMENT.left(), INSTRUMENT.top(), CHANNEL_COLUMN_RIGHT, INSTRUMENT.bottom(),
				0xFF080D09);
		SkyInstrumentPolicy.Channel[] channels = SkyInstrumentPolicy.Channel.values();
		for (int index = 0; index < channels.length; index++) {
			SkyInstrumentPolicy.Channel channel = channels[index];
			int y = CHANNEL_FIRST_Y + index * CHANNEL_ROW_HEIGHT;
			float sample = SkyInstrumentSampler.current(channel);
			boolean saturated = SkyInstrumentPolicy.saturated(sample);
			int color = saturated ? HOT : instability > 0.0F ? AMBER : GREEN;

			drawScaled(graphics, font, Component.translatable(
							KEY + "channel." + channel.name().toLowerCase(java.util.Locale.ROOT)),
					INSTRUMENT.left() + 3, y, DIM);
			Component value = saturated
					? Component.translatable(KEY + "channel.saturated")
					: Component.literal(String.format(java.util.Locale.ROOT, "%03d",
							SkyInstrumentPolicy.reading(channel, instability, sample, renderAge,
									channel.ordinal() * 977L)));
			int width = Math.round(font.width(value) * LABEL_SCALE);
			drawScaledChroma(graphics, font, value, CHANNEL_COLUMN_RIGHT - 3 - width, y, color,
					stage, renderAge);
		}
	}

	/**
	 * The horizon channel's recent history.
	 *
	 * <p>Only one channel gets a trace, and it is this one on purpose: the horizon band takes a red
	 * horizon at full strength while the dome overhead takes well under half of it, so this is the
	 * line that moves first. A player who has the tool open watches the trace climb before the sky
	 * they can see has convincingly changed colour, which is the entire claim that this instrument
	 * is worth opening.</p>
	 */
	private static void drawTrace(GuiGraphics graphics, Font font, int stage, double renderAge) {
		graphics.fill(TRACE.left(), TRACE.top(), TRACE.right(), TRACE.bottom(), 0xFF050B08);
		graphics.renderOutline(TRACE.left(), TRACE.top(), TRACE.width(), TRACE.height(), DARK_BORDER);
		drawScaled(graphics, font, Component.translatable(KEY + "instrument"),
				TRACE.left() + 3, TRACE.top() + 2, DIM);

		int plotTop = TRACE.top() + 10;
		int plotBottom = TRACE.bottom() - 3;
		int plotLeft = TRACE.left() + 3;
		int plotRight = TRACE.right() - 3;
		graphics.fill(plotLeft, plotBottom, plotRight, plotBottom + 1, 0x2F2D6A35);

		int length = SkyInstrumentSampler.traceLength();
		if (length < 2) return;
		int span = plotBottom - plotTop;
		int previous = Integer.MIN_VALUE;
		for (int index = 0; index < length; index++) {
			float sample = SkyInstrumentSampler.trace(SkyInstrumentPolicy.Channel.HORIZON, index);
			int x = plotLeft + Math.round((plotRight - plotLeft - 1) * index
					/ (float) (SkyInstrumentSampler.HISTORY - 1));
			int y = plotBottom - Math.round(Math.clamp(sample, 0.0F, 1.0F) * span);
			if (previous == Integer.MIN_VALUE) previous = y;
			graphics.fill(x, Math.min(previous, y), x + 1, Math.max(previous, y) + 1,
					stage >= 2 ? HOT : GREEN);
			previous = y;
		}
	}

	/**
	 * Rows of the card dragged sideways.
	 *
	 * <p>Drawn as displaced streaks of what is already there rather than as a re-render of the
	 * shifted content: at this row height the difference is invisible and the streaks cost a fill
	 * each. The displacement is held for seven ticks at a time by the policy, so a torn row reads
	 * as one piece of damage sitting on the picture instead of as the whole card vibrating.</p>
	 */
	private static void drawTornRows(GuiGraphics graphics, int stage, double renderAge) {
		var card = TerminalUiLayout.TOOL_DETAIL;
		for (int y = card.top() + 2; y < card.bottom() - 2; y += 2) {
			int shift = SkyInstrumentPolicy.tornRowShift(y, renderAge, stage, card.width());
			if (shift == 0) continue;
			int left = Math.max(card.left() + 1, card.left() + 1 + shift);
			int right = Math.min(card.right() - 1, card.right() - 1 + shift);
			if (right <= left) continue;
			graphics.fill(left, y, right, y + 2, 0x3AC8C4BC);
		}
	}

	private static void drawRollBar(GuiGraphics graphics, int stage, double renderAge) {
		var card = TerminalUiLayout.TOOL_DETAIL;
		int top = SkyInstrumentPolicy.rollBarTop(renderAge, card.height(), stage);
		if (top == Integer.MIN_VALUE) return;
		// Through the shared analog treatment, so the bar that crosses this card and the bar that
		// crosses a loading screen are the same bar. It used to be a flat translucent rectangle,
		// which is the one shape a bar of lost tracking never has.
		AnalogFilter.rollBar(graphics, card.left() + 1, card.top(), card.right() - 1, card.bottom(),
				card.top() + top, SkyInstrumentPolicy.rollBarHeight(card.height()), 1.0F);
	}

	/**
	 * Snow.
	 *
	 * <p>Never over a black fill. Two seconds of true black reads as a bug the player should
	 * report; a surface that is clearly still powered and receiving nothing does not, and that
	 * distinction is the whole difference between this landing as horror and landing as a crash.</p>
	 */
	private static void drawSnow(GuiGraphics graphics, double renderAge) {
		var card = TerminalUiLayout.TOOL_DETAIL;
		// How much of the card is receiving nothing, taken from the same per-cell policy the specks
		// used - so the density still breathes on the policy's clock - and then drawn as real noise
		// instead of as however many two-pixel rectangles happened to be switched on this frame.
		int lit = 0;
		for (int cell = 0; cell < SNOW_CELLS; cell++) {
			if (SkyInstrumentPolicy.snowSpeck(cell, renderAge, 3)) lit++;
		}
		if (lit <= 0) return;
		AnalogFilter.grain(graphics, card.left() + 1, card.top() + 1, card.right() - 1,
				card.bottom() - 1, lit / (float) SNOW_CELLS, (int) renderAge);
	}

	/**
	 * The fault flood.
	 *
	 * <p>Instrument language, never a fabricated stack trace. A fake exception would both stand in
	 * for a rule prompt - which this mod does not do - and convince players the game had actually
	 * crashed. What is frightening here is a device losing the sky and saying so over and over,
	 * not a piece of software breaking.</p>
	 */
	private static void drawFlood(GuiGraphics graphics, Font font, int stage, double renderAge,
			double floodTicks) {
		var card = TerminalUiLayout.TOOL_DETAIL;
		int wanted = SkyInstrumentPolicy.errorLineCount(stage, floodTicks);
		if (wanted <= 0) return;
		int room = (FLOOD_BOTTOM - (card.top() + 2)) / FLOOD_LINE_HEIGHT;
		int lines = Math.min(wanted, room);
		if (lines <= 0) return;
		int top = FLOOD_BOTTOM - lines * FLOOD_LINE_HEIGHT;
		graphics.fill(card.left() + 1, top - 2, card.right() - 1, FLOOD_BOTTOM, FLOOD_BACKDROP);
		String id = AnomalyPresentationController.activeId();
		long seed = id == null ? 0L : id.hashCode();
		for (int row = 0; row < lines; row++) {
			int index = SkyInstrumentPolicy.errorLineIndex(row, seed, floodTicks);
			int channel = SkyInstrumentPolicy.errorChannel(row, seed, floodTicks);
			Component line = Component.translatable(KEY + "fault." + FAULTS[index],
					String.format(java.util.Locale.ROOT, "%02X", channel));
			// The newest line is the brightest; the ones above it have been sitting there.
			int alpha = 150 + 105 * (row + 1) / lines;
			AlphaCorruptionRenderer.drawChromaStringAt(graphics, font, line.getString(),
					card.left() + 6, top + row * FLOOD_LINE_HEIGHT,
					alpha << 24 | FAULT_COLOR & 0x00FFFFFF,
					SkyInstrumentPolicy.chromaOffset(stage));
		}
	}

	/**
	 * Scanlines, anchored to the card rather than to the tick.
	 *
	 * <p>Lines that crawl look like an animation. The point is that they look like the surface the
	 * readings are being displayed on, and a surface does not move.</p>
	 */
	private static void drawScanlines(GuiGraphics graphics, int stage) {
		int alpha = SkyInstrumentPolicy.scanlineAlpha(stage);
		if (alpha <= 0) return;
		var card = TerminalUiLayout.TOOL_DETAIL;
		AnalogFilter.scanlines(graphics, card.left(), card.top(), card.right(), card.bottom(),
				SCANLINE_SPACING, alpha / (float) MAX_SCANLINE_ALPHA, 0);
	}

	private static void drawScaled(GuiGraphics graphics, Font font, Component text, int x, int y,
			int color) {
		graphics.pose().pushMatrix();
		graphics.pose().translate(x, y);
		graphics.pose().scale(LABEL_SCALE, LABEL_SCALE);
		graphics.drawString(font, text, 0, 0, color, false);
		graphics.pose().popMatrix();
	}

	private static void drawScaledChroma(GuiGraphics graphics, Font font, Component text, int x,
			int y, int color, int stage, double renderAge) {
		graphics.pose().pushMatrix();
		graphics.pose().translate(x, y);
		graphics.pose().scale(LABEL_SCALE, LABEL_SCALE);
		AlphaCorruptionRenderer.drawChromaStringAt(graphics, font, text.getString(), 0, 0, color,
				SkyInstrumentPolicy.chromaOffset(stage));
		graphics.pose().popMatrix();
	}
}
