package com.xm.thefourthfrequency.terminal;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static com.xm.thefourthfrequency.terminal.TerminalOnboardingPolicy.Phase;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class TerminalOnboardingPolicyTest {
	@Test
	void aSettledPlayerNeverSeesTheWalkthrough() {
		assertEquals(Phase.DONE, TerminalOnboardingPolicy.initial(false, 0));
		assertEquals(Phase.DONE, TerminalOnboardingPolicy.initial(false, 4));
		// A server that still says "required" while the task has moved past learn_terminal is a state
		// that should not happen; treating it as done beats handing out a walkthrough with no end.
		assertEquals(Phase.DONE, TerminalOnboardingPolicy.initial(true, 4));
		assertEquals(Phase.DONE, TerminalOnboardingPolicy.initial(true, 9));
	}

	@Test
	void onlyATrulyFirstOpenPlaysTheSelfTest() {
		assertEquals(Phase.BOOT, TerminalOnboardingPolicy.initial(true, 0));
		// One tab already visited is proof the self test has been seen, so a reconnect resumes at the
		// walking steps instead of replaying it. This is what makes "not repeatable" hold across a
		// disconnect without needing a second flag on the wire.
		assertEquals(Phase.STEP_2, TerminalOnboardingPolicy.initial(true, 1));
		assertEquals(Phase.STEP_3, TerminalOnboardingPolicy.initial(true, 2));
		assertEquals(Phase.STEP_4, TerminalOnboardingPolicy.initial(true, 3));
	}

	@Test
	void bootHoldsUntilItsLinesHaveAllPrinted() {
		assertEquals(Phase.BOOT, TerminalOnboardingPolicy.afterBoot(Phase.BOOT, 0L));
		assertEquals(Phase.BOOT, TerminalOnboardingPolicy.afterBoot(Phase.BOOT,
				TerminalOnboardingPolicy.BOOT_TOTAL_MILLIS - 1L));
		assertEquals(Phase.STEP_1, TerminalOnboardingPolicy.afterBoot(Phase.BOOT,
				TerminalOnboardingPolicy.BOOT_TOTAL_MILLIS));
		// It only ever moves BOOT along; every other phase is left where it was.
		assertEquals(Phase.STEP_3, TerminalOnboardingPolicy.afterBoot(Phase.STEP_3, 999_999L));
		assertEquals(Phase.DONE, TerminalOnboardingPolicy.afterBoot(Phase.DONE, 999_999L));
	}

	@Test
	void walkthroughEndsOnHomeWhereTheTaskCardIs() {
		assertEquals(TerminalPage.TOOLS, TerminalOnboardingPolicy.target(Phase.STEP_1));
		assertEquals(TerminalPage.RECORDS, TerminalOnboardingPolicy.target(Phase.STEP_2));
		assertEquals(TerminalPage.FILES, TerminalOnboardingPolicy.target(Phase.STEP_3));
		// Home last: the terminal already opens there, so leading with it would make the first
		// instructed click a same-page click that visibly does nothing.
		assertEquals(TerminalPage.HOME, TerminalOnboardingPolicy.target(Phase.STEP_4));
		assertNull(TerminalOnboardingPolicy.target(Phase.BOOT));
		assertNull(TerminalOnboardingPolicy.target(Phase.RELEASED));
		assertNull(TerminalOnboardingPolicy.target(Phase.DONE));
	}

	@Test
	void everyTabIsAskedForExactlyOnceAndTheRunEndsDone() {
		List<TerminalPage> asked = new ArrayList<>();
		Phase phase = Phase.STEP_1;
		for (int step = 0; step < TerminalOnboardingPolicy.stepCount(); step++) {
			TerminalPage target = TerminalOnboardingPolicy.target(phase);
			assertNotNull(target, "step " + step + " had nothing to ask for");
			asked.add(target);
			phase = TerminalOnboardingPolicy.advance(phase, target);
		}
		assertEquals(Phase.DONE, phase);
		assertEquals(TerminalPage.values().length, asked.size());
		assertEquals(asked.size(), Set.copyOf(asked).size(), "a tab was asked for twice: " + asked);
	}

	/**
	 * The brief always describes what is on screen, and a full run describes all four pages.
	 *
	 * <p>It used to describe the tab being pointed at, which meant the line under the pointer talked
	 * about a page the player could not see while the one they were looking at went unexplained -
	 * "six field tools" printed over the home card. Both halves matter: naming the current page is
	 * the fix, and the four-of-four count is what says nothing was lost by it.</p>
	 */
	@Test
	void theBriefDescribesThePageOnScreenAndCoversAllFour() {
		// The terminal opens on Home and the walkthrough starts there once the self test is done.
		TerminalPage onScreen = TerminalPage.HOME;
		Phase phase = Phase.STEP_1;
		List<TerminalPage> described = new ArrayList<>();
		for (int step = 0; step < TerminalOnboardingPolicy.stepCount(); step++) {
			TerminalPage subject = TerminalOnboardingPolicy.briefSubject(phase, onScreen);
			assertEquals(onScreen, subject, () -> "the brief described a page the player was not on");
			described.add(subject);
			// The pointer aims somewhere else, which is the whole reason the two used to disagree.
			TerminalPage target = TerminalOnboardingPolicy.target(phase);
			assertNotNull(target);
			assertNotEquals(target, subject, "the brief and the pointer must not name the same page");
			onScreen = target;
			phase = TerminalOnboardingPolicy.advance(phase, target);
		}
		assertEquals(TerminalPage.values().length, described.size());
		assertEquals(described.size(), Set.copyOf(described).size(),
				"a page was described twice and another never was: " + described);

		// Nothing to point at is nothing to describe: no brief during the self test, after the
		// damage failsafe, or once the walkthrough is over.
		for (Phase quiet : new Phase[]{Phase.BOOT, Phase.RELEASED, Phase.DONE}) {
			assertNull(TerminalOnboardingPolicy.briefSubject(quiet, TerminalPage.HOME), quiet.toString());
		}
	}

	@Test
	void aWrongTabDoesNotAdvanceTheWalkthrough() {
		for (TerminalPage page : TerminalPage.values()) {
			Phase advanced = TerminalOnboardingPolicy.advance(Phase.STEP_1, page);
			if (page == TerminalOnboardingPolicy.target(Phase.STEP_1)) assertEquals(Phase.STEP_2, advanced);
			else assertEquals(Phase.STEP_1, advanced, () -> "the walkthrough moved on " + page);
		}
		// Phases with nothing to wait for absorb any visit rather than falling off the end.
		assertEquals(Phase.BOOT, TerminalOnboardingPolicy.advance(Phase.BOOT, TerminalPage.HOME));
		assertEquals(Phase.DONE, TerminalOnboardingPolicy.advance(Phase.DONE, TerminalPage.HOME));
	}

	@Test
	void theExitIsHeldOnlyWhileTheWalkthroughIsRunning() {
		for (Phase phase : new Phase[]{Phase.BOOT, Phase.STEP_1, Phase.STEP_2, Phase.STEP_3, Phase.STEP_4}) {
			assertTrue(TerminalOnboardingPolicy.locksExit(phase), () -> phase + " must hold the exit");
		}
		// The damage failsafe and the finished state must both let go, or the terminal cannot be
		// closed at all - the one outcome the safety rule exists to prevent.
		assertFalse(TerminalOnboardingPolicy.locksExit(Phase.RELEASED));
		assertFalse(TerminalOnboardingPolicy.locksExit(Phase.DONE));
	}

	@Test
	void aHeldWalkthroughAllowsExactlyOnePage() {
		for (Phase phase : new Phase[]{Phase.STEP_1, Phase.STEP_2, Phase.STEP_3, Phase.STEP_4}) {
			int allowed = 0;
			for (TerminalPage page : TerminalPage.values()) {
				if (TerminalOnboardingPolicy.allowsPage(phase, page)) allowed++;
			}
			assertEquals(1, allowed, () -> phase + " allowed " + "a number of pages other than one");
		}
		for (TerminalPage page : TerminalPage.values()) {
			assertFalse(TerminalOnboardingPolicy.allowsPage(Phase.BOOT, page),
					"the self test takes no page input");
			assertTrue(TerminalOnboardingPolicy.allowsPage(Phase.RELEASED, page));
			assertTrue(TerminalOnboardingPolicy.allowsPage(Phase.DONE, page));
		}
	}

	@Test
	void selfTestPrintsItsLinesInOrderAndItsProgressStaysInRange() {
		assertEquals(0, TerminalOnboardingPolicy.visibleBootLines(-5L));
		assertEquals(1, TerminalOnboardingPolicy.visibleBootLines(0L));
		assertEquals(2, TerminalOnboardingPolicy.visibleBootLines(TerminalOnboardingPolicy.BOOT_LINE_MILLIS));
		assertEquals(TerminalOnboardingPolicy.BOOT_LINE_COUNT,
				TerminalOnboardingPolicy.visibleBootLines(TerminalOnboardingPolicy.BOOT_TOTAL_MILLIS));
		assertEquals(TerminalOnboardingPolicy.BOOT_LINE_COUNT,
				TerminalOnboardingPolicy.visibleBootLines(999_999L));

		// A later line has not started while an earlier one is still printing.
		assertEquals(0, TerminalOnboardingPolicy.typedCharacters(20, 100L, 3));
		assertEquals(0, TerminalOnboardingPolicy.typedCharacters(20, 0L, 0));
		assertEquals(20, TerminalOnboardingPolicy.typedCharacters(20, 999_999L, 5));

		int previous = -1;
		for (long elapsed = 0L; elapsed <= TerminalOnboardingPolicy.BOOT_TOTAL_MILLIS + 500L; elapsed += 37L) {
			int percent = TerminalOnboardingPolicy.bootProgressPercent(elapsed);
			assertTrue(percent >= 0 && percent <= 100, "progress left 0..100 at " + elapsed);
			assertTrue(percent >= previous, "progress went backwards at " + elapsed);
			previous = percent;
		}
		assertEquals(0, TerminalOnboardingPolicy.bootProgressPercent(0L));
		assertEquals(100, TerminalOnboardingPolicy.bootProgressPercent(TerminalOnboardingPolicy.BOOT_TOTAL_MILLIS));
	}

	@Test
	void dimmedBandsCoverEverythingExceptTheHighlightedTab() {
		var display = TerminalUiLayout.DISPLAY;
		for (TerminalPage page : TerminalPage.values()) {
			var hole = tabOf(page);
			var bands = TerminalOnboardingPolicy.dimBands(display, hole);

			int covered = 0;
			for (var band : bands) {
				assertTrue(display.contains(band), () -> band + " escaped the display");
				covered += Math.max(0, band.width()) * Math.max(0, band.height());
				// The highlighted tab keeps every pixel it owns: its label, its background and its
				// unread flash all have to stay readable through the walkthrough.
				assertTrue(disjoint(band, hole), () -> band + " overlapped the highlighted tab " + hole);
			}
			for (int first = 0; first < bands.length; first++) {
				for (int second = first + 1; second < bands.length; second++) {
					assertTrue(disjoint(bands[first], bands[second]),
							"dim bands overlapped each other and would double-darken");
				}
			}
			int displayArea = display.width() * display.height();
			int holeArea = hole.width() * hole.height();
			assertEquals(displayArea - holeArea, covered,
					() -> "the dimmed bands plus " + page + "'s tab did not tile the display");
		}
	}

	private static TerminalUiLayout.Bounds tabOf(TerminalPage page) {
		return switch (page) {
			case HOME -> TerminalUiLayout.HOME_TAB;
			case TOOLS -> TerminalUiLayout.TOOLS_TAB;
			case RECORDS -> TerminalUiLayout.RECORDS_TAB;
			case FILES -> TerminalUiLayout.FILES_TAB;
		};
	}

	private static boolean disjoint(TerminalUiLayout.Bounds first, TerminalUiLayout.Bounds second) {
		if (first.width() <= 0 || first.height() <= 0 || second.width() <= 0 || second.height() <= 0) return true;
		return first.right() <= second.left() || second.right() <= first.left()
				|| first.bottom() <= second.top() || second.bottom() <= first.top();
	}
}
