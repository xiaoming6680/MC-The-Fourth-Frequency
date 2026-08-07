package com.xm.thefourthfrequency.terminal;

/**
 * The first-boot walkthrough: what stage it is in, what it is waiting for, and what it blocks.
 *
 * <p>Pure and on the common side so the whole state machine can be asserted without a client. The
 * drawing lives in {@code TerminalOnboardingOverlay}; nothing here knows what any of it looks
 * like.</p>
 *
 * <p>The walkthrough has no completion signal of its own. It only ever asks the player to make the
 * four tab visits the first task already counts, and those go to the server as ordinary page
 * visits. So finishing it and finishing {@code learn_terminal} are the same event, and the client
 * never has a way to advance the task by claiming it played an animation.</p>
 */
public final class TerminalOnboardingPolicy {
	public enum Phase {
		/** The power-on self test. Nothing is interactive; the exit is held. */
		BOOT,
		STEP_1, STEP_2, STEP_3, STEP_4,
		/** Released early by the damage failsafe. Progress is kept; the exit is free. */
		RELEASED,
		DONE
	}

	public static final long BOOT_LINE_MILLIS = 520L;
	public static final int BOOT_LINE_COUNT = 6;
	public static final long BOOT_TOTAL_MILLIS = BOOT_LINE_COUNT * BOOT_LINE_MILLIS;
	/** Typewriter cadence. Fast enough to read as a machine reporting, not as a message being typed. */
	public static final long BOOT_CHAR_MILLIS = 26L;
	/**
	 * Highlight pulse period, in ticks. Two seconds is 0.5 Hz, well under the 3 Hz ceiling - and the
	 * pulse is a continuous curve anyway, so it has no state change to hold for the minimum duration.
	 */
	public static final double PULSE_PERIOD_TICKS = 40.0D;
	public static final long RELEASED_HINT_MILLIS = 2_400L;
	/**
	 * Ticks the last step lingers before the walkthrough lets go, so the objective bar has time to
	 * fill to 4/4 and the reward notice has time to be read. Finishing is the payoff; cutting to a
	 * bare terminal the instant the fourth tab lands throws it away.
	 */
	public static final int DONE_LINGER_TICKS = 40;

	/**
	 * The order the walkthrough visits the tabs in.
	 *
	 * <p>Home is last rather than first. The terminal opens on Home, so leading with it would make
	 * the player's first instructed action a same-page click: the visit is still recorded, but
	 * nothing on screen moves, and the first thing the walkthrough ever asks for appears to do
	 * nothing. Ending on Home instead lands them on the page that holds the task card, in time to
	 * watch the bar reach 4/4 and the reward arrive.</p>
	 */
	private static final TerminalPage[] ORDER = {
			TerminalPage.TOOLS, TerminalPage.RECORDS, TerminalPage.FILES, TerminalPage.HOME};

	private TerminalOnboardingPolicy() {
	}

	/**
	 * Where a freshly opened terminal starts.
	 *
	 * @param required     whether the server still owes this player the walkthrough
	 * @param visitedTabs  how many tabs they have already visited, which for {@code learn_terminal}
	 *                     is exactly the objective's progress. Enough on its own because the order
	 *                     is fixed, so no extra wire field is needed to resume after a disconnect.
	 */
	public static Phase initial(boolean required, int visitedTabs) {
		if (!required) return Phase.DONE;
		if (visitedTabs >= ORDER.length) return Phase.DONE;
		if (visitedTabs <= 0) return Phase.BOOT;
		// Any tab already visited means the self test has been seen. It never plays twice.
		return step(visitedTabs + 1);
	}

	public static Phase afterBoot(Phase phase, long bootElapsedMillis) {
		if (phase != Phase.BOOT) return phase;
		return bootElapsedMillis >= BOOT_TOTAL_MILLIS ? Phase.STEP_1 : Phase.BOOT;
	}

	/** The tab this phase is waiting for, or null when it is not waiting for one. */
	public static TerminalPage target(Phase phase) {
		int index = stepIndex(phase);
		return index <= 0 ? null : ORDER[index - 1];
	}

	/**
	 * The page the one-line brief describes: whichever one is on screen right now.
	 *
	 * <p>It used to describe the tab the pointer was aimed at, which meant every brief talked about
	 * a page the player could not see while the page they <em>could</em> see went unexplained. The
	 * two lines then read as one instruction and contradicted each other: the strip said "open
	 * Tools" while the line under it described Tools as though the player were already there.</p>
	 *
	 * <p>Describing what is on screen also happens to cover all four pages exactly once, because the
	 * walkthrough opens on Home and {@link #ORDER} visits the other three before returning to it -
	 * so the pages standing behind steps one to four are Home, Tools, Records and Files.</p>
	 *
	 * @return the page to describe, or null when this phase is not pointing at anything
	 */
	public static TerminalPage briefSubject(Phase phase, TerminalPage currentPage) {
		return target(phase) == null ? null : currentPage;
	}

	/** Advances only when the player actually landed on the tab being asked for. */
	public static Phase advance(Phase phase, TerminalPage visited) {
		TerminalPage target = target(phase);
		if (target == null || target != visited) return phase;
		int next = stepIndex(phase) + 1;
		return next > ORDER.length ? Phase.DONE : step(next);
	}

	public static boolean locksExit(Phase phase) {
		return phase != Phase.RELEASED && phase != Phase.DONE;
	}

	/** While the walkthrough holds the exit, only the tab it is pointing at may be opened. */
	public static boolean allowsPage(Phase phase, TerminalPage page) {
		if (!locksExit(phase)) return true;
		return target(phase) == page;
	}

	/** 1..4 for the walking phases, 0 for boot, released and done. */
	public static int stepIndex(Phase phase) {
		return switch (phase) {
			case STEP_1 -> 1;
			case STEP_2 -> 2;
			case STEP_3 -> 3;
			case STEP_4 -> 4;
			case BOOT, RELEASED, DONE -> 0;
		};
	}

	public static int stepCount() {
		return ORDER.length;
	}

	private static Phase step(int index) {
		return switch (index) {
			case 1 -> Phase.STEP_1;
			case 2 -> Phase.STEP_2;
			case 3 -> Phase.STEP_3;
			default -> Phase.STEP_4;
		};
	}

	/** How many self-test lines have started printing. */
	public static int visibleBootLines(long elapsedMillis) {
		if (elapsedMillis < 0L) return 0;
		return (int) Math.clamp(elapsedMillis / BOOT_LINE_MILLIS + 1L, 0L, BOOT_LINE_COUNT);
	}

	/** Code points printed so far on the given self-test line. */
	public static int typedCharacters(int totalCodePoints, long elapsedMillis, int lineIndex) {
		return TerminalMotion.typedCharacters(totalCodePoints,
				elapsedMillis - lineIndex * BOOT_LINE_MILLIS, BOOT_CHAR_MILLIS);
	}

	public static int bootProgressPercent(long elapsedMillis) {
		return (int) Math.round(100.0D * TerminalMotion.elapsedProgress(elapsedMillis, BOOT_TOTAL_MILLIS));
	}

	/**
	 * {@code outer} minus {@code hole}, as four bands.
	 *
	 * <p>The walkthrough dims everything except the tab it is pointing at, rather than laying a
	 * screen over the whole terminal and punching through it. The target keeps every one of its own
	 * pixels, so its label, its background and its unread flash all still read normally.</p>
	 *
	 * <p>Bands may come back empty when the hole touches an edge; callers can draw them regardless,
	 * since an empty rectangle fills nothing.</p>
	 */
	public static TerminalUiLayout.Bounds[] dimBands(TerminalUiLayout.Bounds outer,
			TerminalUiLayout.Bounds hole) {
		int left = Math.clamp(hole.left(), outer.left(), outer.right());
		int right = Math.clamp(hole.right(), outer.left(), outer.right());
		int top = Math.clamp(hole.top(), outer.top(), outer.bottom());
		int bottom = Math.clamp(hole.bottom(), outer.top(), outer.bottom());
		return new TerminalUiLayout.Bounds[]{
				new TerminalUiLayout.Bounds(outer.left(), outer.top(), outer.right(), top),
				new TerminalUiLayout.Bounds(outer.left(), bottom, outer.right(), outer.bottom()),
				new TerminalUiLayout.Bounds(outer.left(), top, left, bottom),
				new TerminalUiLayout.Bounds(right, top, outer.right(), bottom)};
	}
}
