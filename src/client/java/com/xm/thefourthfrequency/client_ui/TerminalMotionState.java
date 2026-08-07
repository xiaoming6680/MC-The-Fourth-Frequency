package com.xm.thefourthfrequency.client_ui;

import com.xm.thefourthfrequency.terminal.TerminalMotion;
import com.xm.thefourthfrequency.terminal.TerminalPage;
import com.xm.thefourthfrequency.terminal.TerminalUiLayout;
import com.xm.thefourthfrequency.terminal.TuningTransition;

import java.util.EnumMap;

/**
 * Every in-flight transition the terminal display is currently running.
 *
 * <p>Split out of {@link TerminalScreen} so that adding an animated control does not mean adding a
 * field: press and hover both live in tables keyed by {@link Control}.</p>
 *
 * <p>The curves themselves are in {@code TerminalMotion} on the common side, where JUnit can reach
 * them. This class only remembers when things started.</p>
 */
final class TerminalMotionState {
	/**
	 * Every control that can be pressed or hovered.
	 *
	 * <p>An enum rather than the {@code Bounds} record it draws into: {@code PAGE_BODY},
	 * {@code RECORDS_BODY} and {@code FILE_BODY} are all {@code (42, 70, 350, 199)}, so a record
	 * keyed map would let three different surfaces overwrite each other's entry. {@code EnumMap} is
	 * also array-backed, so a per-frame lookup costs no hashing and no boxing.</p>
	 */
	enum Control {
		TAB_HOME, TAB_TOOLS, TAB_RECORDS, TAB_FILES,
		RECEIVER_SLIDER,
		HOME_QUICK_PRIMARY, HOME_QUICK_SECONDARY, HOME_TOOL_DETAIL, HOME_TOOL_CLOSE,
		TOOL_BACK,
		TOOL_CELL_0, TOOL_CELL_1, TOOL_CELL_2, TOOL_CELL_3, TOOL_CELL_4, TOOL_CELL_5,
		FILE_ROW_0, FILE_ROW_1, FILE_ROW_2, FILE_ROW_3, FILE_ROW_4, FILE_ROW_5;

		private static final Control[] TOOL_CELLS = {
				TOOL_CELL_0, TOOL_CELL_1, TOOL_CELL_2, TOOL_CELL_3, TOOL_CELL_4, TOOL_CELL_5};
		private static final Control[] FILE_ROWS = {
				FILE_ROW_0, FILE_ROW_1, FILE_ROW_2, FILE_ROW_3, FILE_ROW_4, FILE_ROW_5};

		static Control toolCell(int slot) {
			return slot < 0 || slot >= TOOL_CELLS.length ? null : TOOL_CELLS[slot];
		}

		static Control fileRow(int visibleRow) {
			return visibleRow < 0 || visibleRow >= FILE_ROWS.length ? null : FILE_ROWS[visibleRow];
		}

		static Control tab(TerminalPage page) {
			return switch (page) {
				case HOME -> TAB_HOME;
				case TOOLS -> TAB_TOOLS;
				case RECORDS -> TAB_RECORDS;
				case FILES -> TAB_FILES;
			};
		}
	}

	private long lastFrameMillis;
	private long frameDeltaMillis;

	private long transitionStartedAtMillis = -1L;
	private int transitionDirection;
	private long pageEnteredAtMillis;

	private final TuningTransition tabIndicator =
			new TuningTransition(TerminalUiLayout.HOME_TAB.left(), TerminalMotion.TAB_INDICATOR_MILLIS);

	private final EnumMap<Control, Long> pressedAt = new EnumMap<>(Control.class);
	private final EnumMap<Control, Double> hover = new EnumMap<>(Control.class);

	/**
	 * Opens a frame. Call once at the top of {@code render}.
	 *
	 * <p>The delta is clamped by {@code TerminalMotion.catchUp}; the first frame reports zero so a
	 * screen that has just opened does not inherit however long the previous one was on screen.</p>
	 */
	void beginFrame(long nowMillis) {
		frameDeltaMillis = lastFrameMillis == 0L ? 0L : Math.max(0L, nowMillis - lastFrameMillis);
		lastFrameMillis = nowMillis;
	}

	long frameDelta() {
		return frameDeltaMillis;
	}

	void beginPageTransition(TerminalPage from, TerminalPage to, long nowMillis) {
		transitionDirection = Integer.signum(to.ordinal() - from.ordinal());
		transitionStartedAtMillis = nowMillis;
		pageEnteredAtMillis = nowMillis;
		clearTransient();
	}

	/** 0..1, where 1 means settled. A screen that has never changed page reads as settled. */
	double pageProgress(long nowMillis) {
		if (transitionStartedAtMillis < 0L) return 1.0D;
		return TerminalMotion.progress(transitionStartedAtMillis, nowMillis, TerminalMotion.PAGE_TRANSITION_MILLIS);
	}

	int transitionDirection() {
		return transitionDirection;
	}


	void retargetTabIndicator(TerminalUiLayout.Bounds tab, long nowMillis) {
		tabIndicator.retarget(tab.left(), nowMillis);
	}

	double tabIndicatorX(long nowMillis) {
		return tabIndicator.valueAt(nowMillis);
	}

	void press(Control control, long nowMillis) {
		if (control != null) pressedAt.put(control, nowMillis);
	}

	/**
	 * 0..1 press feedback: up over {@code PRESS_ATTACK_MILLIS}, back down over
	 * {@code PRESS_RELEASE_MILLIS}. Entries drop out of the table once they reach zero.
	 */
	float pressAmount(Control control, long nowMillis) {
		Long started = control == null ? null : pressedAt.get(control);
		if (started == null) return 0.0F;
		long elapsed = nowMillis - started;
		if (elapsed < 0L) return 0.0F;
		if (elapsed < TerminalMotion.PRESS_ATTACK_MILLIS) {
			return (float) TerminalMotion.elapsedProgress(elapsed, TerminalMotion.PRESS_ATTACK_MILLIS);
		}
		long releasing = elapsed - TerminalMotion.PRESS_ATTACK_MILLIS;
		if (releasing >= TerminalMotion.PRESS_RELEASE_MILLIS) {
			pressedAt.remove(control);
			return 0.0F;
		}
		return (float) (1.0D - TerminalMotion.elapsedProgress(releasing, TerminalMotion.PRESS_RELEASE_MILLIS));
	}

	/**
	 * Advances the whole hover table toward the one control under the pointer.
	 *
	 * <p>Called once per frame with the single hovered control rather than per control as it is
	 * drawn: a control that stops being drawn - a tool cell after opening its detail page - would
	 * otherwise freeze at whatever hover value it last had and light up again on return.</p>
	 */
	void updateHover(Control hovered) {
		for (Control control : Control.values()) {
			double current = hover.getOrDefault(control, 0.0D);
			double target = control == hovered ? 1.0D : 0.0D;
			if (current == target) continue;
			double next = TerminalMotion.catchUp(current, target, frameDeltaMillis, TerminalMotion.HOVER_TAU_MILLIS);
			if (Math.abs(target - next) < 0.004D) next = target;
			if (next == 0.0D) hover.remove(control);
			else hover.put(control, next);
		}
	}

	float hoverAmount(Control control) {
		return control == null ? 0.0F : hover.getOrDefault(control, 0.0D).floatValue();
	}

	/** Drops press and hover state. Used when the page changes under the pointer. */
	void clearTransient() {
		pressedAt.clear();
		hover.clear();
	}

}
