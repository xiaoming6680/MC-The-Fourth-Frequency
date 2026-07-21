package com.xm.thefourthfrequency.terminal;

public final class TerminalUiLayout {
	public static final Bounds DISPLAY = new Bounds(36, 40, 356, 216);
	public static final Bounds HOME_TAB = new Bounds(42, 45, 116, 62);
	public static final Bounds TOOLS_TAB = new Bounds(119, 45, 193, 62);
	public static final Bounds RECORDS_TAB = new Bounds(196, 45, 270, 62);
	public static final Bounds FILES_TAB = new Bounds(273, 45, 350, 62);
	public static final Bounds PAGE_BODY = new Bounds(42, 70, 350, 199);
	public static final Bounds HOME_TASK = new Bounds(44, 72, 348, 119);
	public static final Bounds HOME_QUICK_PRIMARY = new Bounds(44, 123, 194, 164);
	public static final Bounds HOME_QUICK_SECONDARY = new Bounds(198, 123, 348, 164);
	public static final Bounds HOME_TOOL_DETAIL = new Bounds(44, 123, 348, 164);
	public static final Bounds HOME_TOOL_BACK = new Bounds(50, 129, 96, 158);
	public static final Bounds HOME_RECENT = new Bounds(44, 168, 348, 196);
	public static final Bounds TOOLS_GRID = new Bounds(44, 72, 348, 190);
	public static final Bounds TOOL_HEADER = new Bounds(44, 72, 348, 92);
	public static final Bounds TOOL_DETAIL = new Bounds(44, 96, 348, 196);
	public static final Bounds RECORDS_BODY = new Bounds(42, 70, 350, 199);
	public static final Bounds FILE_BODY = new Bounds(42, 70, 350, 199);
	public static final Bounds FILE_LIST = new Bounds(44, 72, 133, 196);
	public static final Bounds FILE_DIVIDER = new Bounds(134, 72, 136, 196);
	public static final Bounds FILE_CONTENT = new Bounds(137, 72, 348, 196);
	public static final Bounds FOOTER = new Bounds(42, 202, 350, 215);
	public static final Bounds TOOL_BACK = new Bounds(44, 74, 92, 90);
	public static final Bounds TOOL_OPTION_ONE = new Bounds(50, 139, 142, 157);
	public static final Bounds TOOL_OPTION_TWO = new Bounds(147, 139, 239, 157);
	public static final Bounds TOOL_OPTION_THREE = new Bounds(244, 139, 342, 157);
	public static final Bounds TOOL_ACTION_PRIMARY = new Bounds(50, 166, 190, 188);
	public static final Bounds TOOL_ACTION_SECONDARY = new Bounds(202, 166, 342, 188);
	public static final Bounds KEYPAD = new Bounds(149, 123, 336, 184);
	public static final Bounds HARDWARE_SAFE = new Bounds(389, 24, 495, 230);
	public static final Bounds SCOPE = new Bounds(400, 46, 484, 88);
	public static final Bounds COMPASS = new Bounds(425, 98, 459, 132);
	public static final Bounds RECEIVER_SLIDER = new Bounds(400, 141, 484, 163);
	public static final Bounds RECEIVER_LCD = new Bounds(400, 176, 484, 204);
	public static final Bounds LCD_LINE_ONE = new Bounds(404, 180, 480, 190);
	public static final Bounds LCD_LINE_TWO = new Bounds(404, 191, 480, 201);
	public static final Bounds CLOSE_HINT = new Bounds(400, 209, 484, 226);
	public static final int TOOL_COLUMNS = 3;
	public static final int TOOL_ROWS = 2;
	public static final int TOOL_CELL_WIDTH = 98;
	public static final int TOOL_CELL_HEIGHT = 55;
	public static final int TOOL_CELL_GAP_X = 4;
	public static final int TOOL_CELL_GAP_Y = 6;
	public static final int FILE_LIST_ROW_HEIGHT = 18;
	public static final int FILE_LIST_VISIBLE_ROWS = 6;
	public static final int HINT_HOLD_TICKS = 40;
	public static final int HINT_END_TICKS = 60;

	private TerminalUiLayout() { }

	public static int hintAlpha(int ageTicks) {
		if (ageTicks <= HINT_HOLD_TICKS) return 255;
		if (ageTicks >= HINT_END_TICKS) return 0;
		return (int) Math.round(255.0D * (HINT_END_TICKS - ageTicks)
				/ (HINT_END_TICKS - HINT_HOLD_TICKS));
	}

	public static int scroll(int current, int delta, int maximum) {
		return Math.clamp(current + delta, 0, Math.max(0, maximum));
	}

	public static int sliderTuning(double x) {
		return Math.clamp((int) Math.round((x - RECEIVER_SLIDER.left()) * 100.0D / RECEIVER_SLIDER.width()), 0, 100);
	}

	public static int sliderX(int tuning) {
		return RECEIVER_SLIDER.left() + (int) Math.round(RECEIVER_SLIDER.width() * Math.clamp(tuning, 0, 100) / 100.0D);
	}

	public static Bounds toolCell(int slot) {
		int safe = Math.clamp(slot, 0, TOOL_COLUMNS * TOOL_ROWS - 1);
		int column = safe % TOOL_COLUMNS;
		int row = safe / TOOL_COLUMNS;
		int left = TOOLS_GRID.left() + column * (TOOL_CELL_WIDTH + TOOL_CELL_GAP_X);
		int top = TOOLS_GRID.top() + row * (TOOL_CELL_HEIGHT + TOOL_CELL_GAP_Y);
		return new Bounds(left, top, left + TOOL_CELL_WIDTH, top + TOOL_CELL_HEIGHT);
	}

	public static int toolSlotAt(double x, double y) {
		for (int slot = 0; slot < TOOL_COLUMNS * TOOL_ROWS; slot++) if (toolCell(slot).contains(x, y)) return slot;
		return -1;
	}

	public static int fileMaxScrollRow(int fileCount) {
		return Math.max(0, Math.max(0, fileCount) - FILE_LIST_VISIBLE_ROWS);
	}

	public static Bounds fileListRow(int visibleRow) {
		int safe = Math.clamp(visibleRow, 0, FILE_LIST_VISIBLE_ROWS - 1);
		int top = FILE_LIST.top() + safe * FILE_LIST_ROW_HEIGHT;
		return new Bounds(FILE_LIST.left(), top, FILE_LIST.right(), top + FILE_LIST_ROW_HEIGHT);
	}

	public static int fileIndexAt(double x, double y, int scrollRow, int fileCount) {
		if (!FILE_LIST.contains(x, y)) return -1;
		int visibleRow = Math.clamp((int) ((y - FILE_LIST.top()) / FILE_LIST_ROW_HEIGHT), 0, FILE_LIST_VISIBLE_ROWS - 1);
		int index = Math.max(0, scrollRow) + visibleRow;
		return index < Math.max(0, fileCount) ? index : -1;
	}

	public record Bounds(int left, int top, int right, int bottom) {
		public int width() { return right - left; }
		public int height() { return bottom - top; }
		public boolean contains(Bounds child) {
			return child.left >= left && child.top >= top && child.right <= right && child.bottom <= bottom;
		}
		public boolean contains(double x, double y) {
			return x >= left && x < right && y >= top && y < bottom;
		}
	}
}
