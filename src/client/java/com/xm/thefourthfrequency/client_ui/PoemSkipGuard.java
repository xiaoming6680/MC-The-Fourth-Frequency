package com.xm.thefourthfrequency.client_ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * Holds the ending poem's exit shut for its opening seconds, and answers the presses it refuses.
 *
 * <p>State only, plus the one line it draws. Every rule about <em>when</em> lives in
 * {@link PoemSkipNoticeTimeline}, on the common side where it can be asserted without a client.
 *
 * <p>Scoped to one poem: {@link #begin} is called when the authored win screen opens and
 * {@link #end} when it closes, so nothing here can outlive the screen it belongs to and no other
 * screen can inherit a countdown from it.
 */
public final class PoemSkipGuard {
	private static final int MARGIN = 8;

	private static boolean active;
	private static long openedAtMillis;
	private static long refusedAtMillis = Long.MIN_VALUE;

	private PoemSkipGuard() {
	}

	/** The authored poem has opened; the exit is held from here. */
	public static void begin() {
		active = true;
		openedAtMillis = now();
		refusedAtMillis = Long.MIN_VALUE;
	}

	/** The screen is going away, by any route. */
	public static void end() {
		active = false;
		refusedAtMillis = Long.MIN_VALUE;
	}

	/** Whether Escape may close the poem this instant. True whenever no authored poem is up. */
	public static boolean allowsSkip() {
		return !active || PoemSkipNoticeTimeline.allowsSkip(now() - openedAtMillis);
	}

	/**
	 * Records a press that was refused, restarting the notice.
	 *
	 * <p>Restarting rather than ignoring a second press: a player who presses again is asking again,
	 * and the honest answer is the current number of seconds, held for its full time from now.
	 */
	public static void refuse() {
		refusedAtMillis = now();
	}

	/**
	 * Draws the refusal in the top-right corner, if one is still fading.
	 *
	 * <p>Top-right because the poem scrolls up the middle of the screen: an aside about the exit
	 * must not sit in the text it is asking the player to stay for.
	 */
	public static void renderNotice(GuiGraphics graphics, Font font, int screenWidth) {
		if (!active || refusedAtMillis == Long.MIN_VALUE) return;
		float alpha = PoemSkipNoticeTimeline.noticeAlpha(now() - refusedAtMillis);
		if (alpha <= 0.0F) return;
		int seconds = PoemSkipNoticeTimeline.secondsRemaining(now() - openedAtMillis);
		// The wait ended while the notice was still on screen. Nothing left to say, and saying "0
		// seconds" next to a key that now works would be worse than saying nothing.
		if (seconds <= 0) return;
		Component text = Component.translatable(
				"hud.thefourthfrequency.poem.skip_locked", seconds);
		int colour = (Math.round(alpha * 255.0F) << 24) | 0x00E6D9A8;
		graphics.drawString(font, text, screenWidth - font.width(text) - MARGIN, MARGIN, colour, false);
		// The way forward, under the way out. A player told they cannot leave yet should be told in
		// the same breath what they can do instead, or the notice is only a refusal.
		Component hint = Component.translatable("hud.thefourthfrequency.poem.speed_hint");
		int hintColour = (Math.round(alpha * 0.8F * 255.0F) << 24) | 0x00BFB894;
		graphics.drawString(font, hint, screenWidth - font.width(hint) - MARGIN,
				MARGIN + font.lineHeight + 2, hintColour, false);
	}

	private static long now() {
		return System.nanoTime() / 1_000_000L;
	}
}
