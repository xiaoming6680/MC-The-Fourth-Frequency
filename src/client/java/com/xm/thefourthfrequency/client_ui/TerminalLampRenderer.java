package com.xm.thefourthfrequency.client_ui;

import com.xm.thefourthfrequency.terminal.TerminalMotion;
import com.xm.thefourthfrequency.terminal.TerminalUiLayout;
import net.minecraft.client.gui.GuiGraphics;

/**
 * The amber unread lamp on the hardware column.
 *
 * <p>The panel's half of a statement whose other half is in the player's hand: the same
 * {@code attentionActive} the server used to pick which of the six item forms the terminal is
 * currently drawn as arrives on the snapshot, and this draws it. Nothing here re-derives that
 * answer from the unread counts sitting next to it on the same payload.</p>
 *
 * <p>Unlit it is a dark lens in a brass housing - present, obviously an indicator, obviously off.
 * Lit it breathes at 0.42 Hz on a raised cosine from {@link TerminalUiLayout#unreadLampIntensity},
 * which is continuous and seven times under the project's 3 Hz flicker ceiling. It never blinks:
 * this lamp can be lit for a whole session, and a blink is the one thing on this screen that would
 * be pulling at a photosensitive player's eye the entire time.</p>
 *
 * <p>A separate file rather than another method on {@link TerminalScreen} because that class is
 * pinned by source-text contract assertions and cannot currently be split, so new drawing goes
 * beside it. Unlike {@link TerminalChrome} this one is allowed to read the tick clock - the rule
 * there is about the standing CRT shell, not about instruments that report state.</p>
 */
final class TerminalLampRenderer {
	/** Housing thickness. Two pixels, so the lamp still reads as machined at the panel's scale. */
	private static final int HOUSING = 2;
	/**
	 * Gap between the lens and the filament inside it.
	 *
	 * <p>Three rather than one. At one, the bright core covered the lens almost entirely and the lit
	 * lamp read as a solid amber square - a filled swatch, not a bulb. Leaving a visible ring of
	 * lens around it is what makes the breathing look like something glowing inside glass.</p>
	 */
	private static final int LENS_INSET = 3;
	/** Radius of the soft halo around a lit core, in pixels. */
	private static final int HALO = 2;
	private static final int HALO_MAX_ALPHA = 90;

	private TerminalLampRenderer() {
	}

	/**
	 * Draws the lamp.
	 *
	 * @param active     whether anything is waiting for the player
	 * @param renderAge  the screen's age in ticks, fractional; only read when {@code active}
	 */
	static void drawUnreadLamp(GuiGraphics graphics, boolean active, double renderAge) {
		var lamp = TerminalUiLayout.UNREAD_LAMP;
		graphics.fill(lamp.left(), lamp.top(), lamp.right(), lamp.bottom(),
				TerminalVisualTheme.INSTRUMENT_WELL);
		graphics.renderOutline(lamp.left(), lamp.top(), lamp.width(), lamp.height(),
				TerminalVisualTheme.BRASS_BEZEL);
		int left = lamp.left() + HOUSING;
		int top = lamp.top() + HOUSING;
		int right = lamp.right() - HOUSING;
		int bottom = lamp.bottom() - HOUSING;
		if (!active) {
			// A dark lens and its rim, and nothing else. The housing above is what tells the player
			// there is an indicator here at all when it has nothing to say.
			graphics.fill(left, top, right, bottom, TerminalVisualTheme.LAMP_DARK);
			graphics.renderOutline(left, top, right - left, bottom - top,
					TerminalVisualTheme.withAlpha(TerminalVisualTheme.BRASS_BEZEL, 120));
			return;
		}
		double intensity = TerminalUiLayout.unreadLampIntensity(renderAge);
		int lens = TerminalMotion.lerpColor(TerminalVisualTheme.LAMP_DARK,
				TerminalVisualTheme.LAMP_AMBER, intensity);
		graphics.fill(left, top, right, bottom, lens);
		// The filament: a smaller, brighter square that carries most of the breathing, so the lamp
		// reads as a bulb warming and cooling rather than as a rectangle changing colour.
		int core = TerminalMotion.lerpColor(TerminalVisualTheme.LAMP_AMBER,
				TerminalVisualTheme.LAMP_AMBER_CORE, intensity);
		graphics.fill(left + LENS_INSET, top + LENS_INSET,
				right - LENS_INSET, bottom - LENS_INSET, core);
		for (int ring = 1; ring <= HALO; ring++) {
			int alpha = (int) Math.round(HALO_MAX_ALPHA * intensity / (ring + 1.0D));
			graphics.renderOutline(left - ring, top - ring,
					right - left + ring * 2, bottom - top + ring * 2,
					TerminalVisualTheme.withAlpha(TerminalVisualTheme.LAMP_AMBER, alpha));
		}
	}
}
