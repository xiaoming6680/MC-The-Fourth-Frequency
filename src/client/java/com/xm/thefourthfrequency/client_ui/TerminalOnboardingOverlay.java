package com.xm.thefourthfrequency.client_ui;

import com.xm.thefourthfrequency.terminal.TerminalMotion;
import com.xm.thefourthfrequency.terminal.TerminalOnboardingPolicy;
import com.xm.thefourthfrequency.terminal.TerminalPage;
import com.xm.thefourthfrequency.terminal.TerminalUiLayout;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * Draws the first-boot walkthrough: the self test, and the pointer that walks the player through
 * the four tabs.
 *
 * <p>Holds no authority. Which phase is current, what it is waiting for and whether it blocks the
 * exit are all decided by {@code TerminalOnboardingPolicy}; this only decides what that looks
 * like.</p>
 */
final class TerminalOnboardingOverlay {
	/**
	 * The self-test lines, as literal keys.
	 *
	 * <p>Written out rather than built by concatenating a suffix, because the contract test that
	 * checks every translation key exists only recognises literal arguments. A key assembled at
	 * runtime would slip past it and could go missing without anything failing.</p>
	 */
	private static final String[] BOOT_KEYS = {
			"terminal.thefourthfrequency.boot.line.power",
			"terminal.thefourthfrequency.boot.line.memory",
			"terminal.thefourthfrequency.boot.line.receiver",
			"terminal.thefourthfrequency.boot.line.archive",
			"terminal.thefourthfrequency.boot.line.bind",
			"terminal.thefourthfrequency.boot.line.ready"};
	/** The line that names the holder, and so is the one that takes an argument. */
	private static final int BIND_LINE = 4;
	private static final int BOOT_LINE_HEIGHT = 11;
	private static final int ARROW_SIZE = 5;

	private final String[] bootFull = new String[TerminalOnboardingPolicy.BOOT_LINE_COUNT];
	private final String[] bootShown = new String[TerminalOnboardingPolicy.BOOT_LINE_COUNT];
	private final int[] bootShownChars = new int[TerminalOnboardingPolicy.BOOT_LINE_COUNT];
	private boolean bootTextReady;
	/** How many self-test lines have already been sounded, so each is heard exactly once. */
	private int bootLinesHeard;

	/**
	 * Resolves the self-test lines once.
	 *
	 * <p>{@code getString()} walks the language table, so doing it per frame would resolve six lines
	 * sixty times a second to print the same text.</p>
	 */
	void prepareBootText(String holder) {
		if (bootTextReady) return;
		for (int line = 0; line < BOOT_KEYS.length; line++) {
			bootFull[line] = line == BIND_LINE
					? Component.translatable(BOOT_KEYS[line], holder).getString()
					: Component.translatable(BOOT_KEYS[line]).getString();
			bootShown[line] = "";
			bootShownChars[line] = 0;
		}
		bootTextReady = true;
	}

	/**
	 * The power-on self test.
	 *
	 * <p>Draws no panel of its own. The page is not rendered at all during boot - the terminal has
	 * not finished starting, so there is nothing behind this to hide - and the text sits directly on
	 * the display glass the way every other line in the terminal does. An opaque plate here read as
	 * a sticker laid over the device rather than as the device's own screen.</p>
	 */
	void drawBoot(GuiGraphics graphics, Font font, long elapsedMillis, int accent) {
		var body = TerminalUiLayout.PAGE_BODY;
		graphics.drawString(font, Component.translatable("terminal.thefourthfrequency.boot.title"),
				body.left() + 9, body.top() + 8, accent, false);
		graphics.fill(body.left() + 9, body.top() + 20, body.right() - 9, body.top() + 21,
				TerminalVisualTheme.DARK_BORDER);

		int visible = TerminalOnboardingPolicy.visibleBootLines(elapsedMillis);
		// Each check answers as it lands. Driven off the line count rather than off a clock of its
		// own, so the sound is on the frame the line appears however the boot was resumed, and the
		// comparison is what keeps it to once per line instead of once per frame. The loop catches
		// up if several lines became visible between two frames.
		while (bootLinesHeard < visible) {
			if (bootLinesHeard == TerminalOnboardingPolicy.BOOT_LINE_COUNT - 1) {
				TerminalClientAudio.bootComplete();
			} else {
				TerminalClientAudio.bootLine(bootLinesHeard);
			}
			bootLinesHeard++;
		}
		int y = body.top() + 28;
		for (int line = 0; line < visible; line++) {
			String text = typedPrefix(line, elapsedMillis);
			graphics.drawString(font, text, body.left() + 9, y, TerminalVisualTheme.GREEN, false);
			// A block cursor on whichever line is still printing. A fill rather than a character, so
			// the caret costs no string building.
			if (text.length() < bootFull[line].length()) {
				int caret = body.left() + 9 + font.width(text) + 1;
				graphics.fill(caret, y, caret + 4, y + font.lineHeight - 1, TerminalVisualTheme.GREEN);
			}
			y += BOOT_LINE_HEIGHT;
		}

		int percent = TerminalOnboardingPolicy.bootProgressPercent(elapsedMillis);
		int trackLeft = body.left() + 9;
		int trackRight = body.right() - 9;
		int trackY = body.bottom() - 16;
		graphics.fill(trackLeft, trackY, trackRight, trackY + 4, TerminalVisualTheme.PROGRESS_TRACK);
		graphics.fill(trackLeft, trackY, trackLeft + (trackRight - trackLeft) * percent / 100, trackY + 4, accent);
		Component readout = Component.translatable("terminal.thefourthfrequency.boot.progress", percent);
		graphics.drawString(font, readout, trackRight - font.width(readout), trackY - 11,
				TerminalVisualTheme.DIM, false);
	}

	/**
	 * Reuses the previously rendered prefix whenever the visible length has not changed, so only the
	 * one line currently printing ever builds a string.
	 */
	private String typedPrefix(int line, long elapsedMillis) {
		String full = bootFull[line];
		if (full == null) return "";
		int total = full.codePointCount(0, full.length());
		int want = TerminalOnboardingPolicy.typedCharacters(total, elapsedMillis, line);
		if (want == bootShownChars[line] && bootShown[line] != null) return bootShown[line];
		bootShownChars[line] = want;
		bootShown[line] = want >= total ? full : full.substring(0, full.offsetByCodePoints(0, want));
		return bootShown[line];
	}

	/**
	 * Dims everything except the tab being asked for, and points at it.
	 *
	 * <p>The target tab is not covered at all - see {@code dimBands}. Its own drawing, including the
	 * unread flash, carries on underneath.</p>
	 */
	void drawStep(GuiGraphics graphics, Font font, TerminalOnboardingPolicy.Phase phase,
			TerminalPage currentPage, double renderAge, int accent) {
		TerminalPage target = TerminalOnboardingPolicy.target(phase);
		if (target == null) return;
		var tab = tabBounds(target);
		for (var band : TerminalOnboardingPolicy.dimBands(TerminalUiLayout.DISPLAY, tab)) {
			if (band.width() <= 0 || band.height() <= 0) continue;
			graphics.fill(band.left(), band.top(), band.right(), band.bottom(), TerminalVisualTheme.ONBOARD_DIM);
		}

		// A raised cosine, not a blink. A continuous curve has no state change to hold for a minimum
		// duration, and at one cycle per two seconds it is nowhere near the flicker ceiling.
		double pulse = TerminalMotion.breathe(renderAge, TerminalOnboardingPolicy.PULSE_PERIOD_TICKS);
		int ring = TerminalMotion.lerpColor(accent, TerminalVisualTheme.ONBOARD_ACCENT, pulse);
		graphics.renderOutline(tab.left() - 1, tab.top() - 1, tab.width() + 2, tab.height() + 2, ring);

		int arrowX = (tab.left() + tab.right()) / 2;
		int arrowY = tab.top() - 4 - (int) Math.round(pulse * 2.0D);
		for (int row = 0; row < ARROW_SIZE; row++) {
			graphics.fill(arrowX - row, arrowY - row, arrowX + row + 1, arrowY - row + 1, ring);
		}

		drawBrief(graphics, font, TerminalOnboardingPolicy.briefSubject(phase, currentPage));

		// The standing status strip stands down for the walkthrough rather than being covered over,
		// so this writes into an empty band instead of onto a plate laid across the readout.
		var strip = TerminalUiLayout.STATUS_BAR;
		Component instruction = Component.translatable(stepKey(target));
		graphics.drawString(font, instruction, strip.left() + 4, strip.top() + 2, ring, false);
		Component counter = Component.translatable("terminal.thefourthfrequency.onboarding.counter",
				TerminalOnboardingPolicy.stepIndex(phase), TerminalOnboardingPolicy.stepCount());
		graphics.drawString(font, counter, strip.right() - 4 - font.width(counter), strip.top() + 2,
				TerminalVisualTheme.DIM, false);
	}

	/**
	 * One line saying what the page currently on screen is for.
	 *
	 * <p>The pointer above says which tab to click next, which tells a new player where to put the
	 * cursor and nothing about what they are looking at. This is the other half: it names the page
	 * standing behind the dimming and says what it holds. See
	 * {@code TerminalOnboardingPolicy#briefSubject} for why it is the current page rather than the
	 * one being pointed at.</p>
	 *
	 * <p>The page's own name leads the line, so the two instructions on screen can never be read as
	 * one. "Home: your objective, ..." underneath "select the Tools tab" is unambiguous in a way
	 * that a bare description is not.</p>
	 *
	 * <p>Kept to a single line on purpose. This is a walkthrough a player sees once, while four
	 * other things are already competing for their attention; a paragraph here would be read by
	 * nobody and would push the pointer off the bottom of the page.</p>
	 */
	private static void drawBrief(GuiGraphics graphics, Font font, TerminalPage subject) {
		if (subject == null) return;
		var brief = TerminalUiLayout.ONBOARD_BRIEF;
		// A second pass of the same dim, not a plate. The band already carries one from the highlight,
		// but one is not enough to read a sentence through the card underneath - on Home this line
		// lands straight across the most recent record and the two interleave. Doubling the dim pushes
		// the page back another step while leaving it visibly present, which is the terminal's rule for
		// clearing space; an opaque rectangle here reads as a sticker laid over the screen.
		graphics.fill(brief.left(), brief.top(), brief.right(), brief.bottom(),
				TerminalVisualTheme.ONBOARD_DIM);
		Component text = Component.translatable("terminal.thefourthfrequency.onboarding.brief.current",
				Component.translatable(tabKey(subject)), Component.translatable(briefKey(subject)));
		int room = brief.width() - 8;
		int width = font.width(text);
		int y = brief.top() + (brief.height() - font.lineHeight) / 2;
		if (width <= room) {
			graphics.drawString(font, text, brief.left() + (brief.width() - width) / 2, y,
					TerminalVisualTheme.READING_TEXT, false);
			return;
		}
		// A longer translation shrinks rather than losing its ending. Same rule the rest of the
		// terminal follows: a label that does not fit is scaled, never silently cut.
		float scale = room / (float) width;
		graphics.pose().pushMatrix();
		graphics.pose().translate(brief.left() + 4.0F, y + font.lineHeight * (1.0F - scale) / 2.0F);
		graphics.pose().scale(scale, scale);
		graphics.drawString(font, text, 0, 0, TerminalVisualTheme.READING_TEXT, false);
		graphics.pose().popMatrix();
	}

	/**
	 * The close hint, replaced while the exit is held.
	 *
	 * <p>Leaving "press Esc to close" on screen while Esc does nothing would be the terminal lying
	 * about its own controls, which is the one thing its failure language is not allowed to do.</p>
	 */
	static Component closeHint(TerminalOnboardingPolicy.Phase phase, boolean recentlyReleased) {
		if (TerminalOnboardingPolicy.locksExit(phase)) {
			return Component.translatable("terminal.thefourthfrequency.onboarding.locked_hint");
		}
		if (recentlyReleased) {
			return Component.translatable("terminal.thefourthfrequency.onboarding.released_hint");
		}
		return Component.translatable("terminal.thefourthfrequency.close_hint");
	}

	private static String stepKey(TerminalPage target) {
		return switch (target) {
			case HOME -> "terminal.thefourthfrequency.onboarding.step.home";
			case TOOLS -> "terminal.thefourthfrequency.onboarding.step.tools";
			case RECORDS -> "terminal.thefourthfrequency.onboarding.step.records";
			case FILES -> "terminal.thefourthfrequency.onboarding.step.files";
		};
	}

	/**
	 * The tab strip's own labels, so the name the brief leads with is character for character the
	 * name printed on the tab the player is looking at.
	 */
	private static String tabKey(TerminalPage target) {
		return switch (target) {
			case HOME -> "terminal.thefourthfrequency.tab.home";
			case TOOLS -> "terminal.thefourthfrequency.tab.tools";
			case RECORDS -> "terminal.thefourthfrequency.tab.records";
			case FILES -> "terminal.thefourthfrequency.tab.files";
		};
	}

	/** Written out per case rather than assembled, for the reason given on {@link #BOOT_KEYS}. */
	private static String briefKey(TerminalPage target) {
		return switch (target) {
			case HOME -> "terminal.thefourthfrequency.onboarding.brief.home";
			case TOOLS -> "terminal.thefourthfrequency.onboarding.brief.tools";
			case RECORDS -> "terminal.thefourthfrequency.onboarding.brief.records";
			case FILES -> "terminal.thefourthfrequency.onboarding.brief.files";
		};
	}

	private static TerminalUiLayout.Bounds tabBounds(TerminalPage tab) {
		return switch (tab) {
			case HOME -> TerminalUiLayout.HOME_TAB;
			case TOOLS -> TerminalUiLayout.TOOLS_TAB;
			case RECORDS -> TerminalUiLayout.RECORDS_TAB;
			case FILES -> TerminalUiLayout.FILES_TAB;
		};
	}
}
