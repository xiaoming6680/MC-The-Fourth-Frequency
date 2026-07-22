package com.xm.thefourthfrequency.terminal;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class TerminalUiLayoutTest {
	@Test
	void everyTextAndInteractionRegionStaysInsideItsPanel() {
		var display = TerminalUiLayout.DISPLAY;
		for (var bounds : List.of(TerminalUiLayout.HOME_TAB, TerminalUiLayout.TOOLS_TAB,
				TerminalUiLayout.RECORDS_TAB, TerminalUiLayout.FILES_TAB, TerminalUiLayout.PAGE_BODY,
				TerminalUiLayout.HOME_TASK, TerminalUiLayout.HOME_QUICK_PRIMARY,
				TerminalUiLayout.HOME_QUICK_SECONDARY, TerminalUiLayout.HOME_TOOL_DETAIL,
				TerminalUiLayout.HOME_TOOL_CLOSE, TerminalUiLayout.HOME_RECENT,
				TerminalUiLayout.TOOLS_GRID, TerminalUiLayout.TOOL_HEADER, TerminalUiLayout.TOOL_DETAIL,
				TerminalUiLayout.RECORDS_BODY, TerminalUiLayout.FILE_BODY, TerminalUiLayout.FILE_LIST,
				TerminalUiLayout.FILE_DIVIDER, TerminalUiLayout.FILE_CONTENT, TerminalUiLayout.FOOTER,
				TerminalUiLayout.TOOL_BACK, TerminalUiLayout.KEYPAD)) {
			assertTrue(display.contains(bounds), () -> bounds + " escaped " + display);
		}
		for (var bounds : List.of(TerminalUiLayout.TOOL_OPTION_ONE, TerminalUiLayout.TOOL_OPTION_TWO,
				TerminalUiLayout.TOOL_OPTION_THREE, TerminalUiLayout.TOOL_ACTION_PRIMARY,
				TerminalUiLayout.TOOL_ACTION_SECONDARY, TerminalUiLayout.TOOL_ACTION_FULL)) {
			assertTrue(TerminalUiLayout.TOOL_DETAIL.contains(bounds));
		}
		for (var hardware : List.of(TerminalUiLayout.SCOPE, TerminalUiLayout.COMPASS,
				TerminalUiLayout.RECEIVER_SLIDER, TerminalUiLayout.RECEIVER_LCD, TerminalUiLayout.CLOSE_HINT)) {
			assertTrue(TerminalUiLayout.HARDWARE_SAFE.contains(hardware),
					() -> hardware + " escaped " + TerminalUiLayout.HARDWARE_SAFE);
			assertFalse(display.contains(hardware));
		}
		assertTrue(TerminalUiLayout.RECEIVER_LCD.contains(TerminalUiLayout.LCD_LINE_ONE));
		assertTrue(TerminalUiLayout.RECEIVER_LCD.contains(TerminalUiLayout.LCD_LINE_TWO));
	}

	@Test
	void expandedCompassRemainsCenteredAndSeparatedFromAdjacentHardware() {
		var compass = TerminalUiLayout.COMPASS;
		assertEquals(42, compass.width());
		assertEquals(42, compass.height());
		assertEquals((TerminalUiLayout.SCOPE.left() + TerminalUiLayout.SCOPE.right()) / 2,
				(compass.left() + compass.right()) / 2);
		assertTrue(compass.top() - TerminalUiLayout.SCOPE.bottom() >= 5);
		assertTrue(TerminalUiLayout.RECEIVER_SLIDER.top() - compass.bottom() >= 5);
	}

	@Test
	void hintAndScrollClamp() {
		assertEquals(255, TerminalUiLayout.hintAlpha(40));
		assertEquals(128, TerminalUiLayout.hintAlpha(50));
		assertEquals(0, TerminalUiLayout.hintAlpha(60));
		assertEquals(0, TerminalUiLayout.scroll(0, -3, 8));
		assertEquals(8, TerminalUiLayout.scroll(7, 9, 8));
	}

	@Test
	void unreadAlertStartsLitAndStopsFlashingAfterTwoSeconds() {
		assertTrue(TerminalUiLayout.unreadFlashOn(0.0D));
		assertTrue(TerminalUiLayout.unreadFlashOn(9.99D));
		assertFalse(TerminalUiLayout.unreadFlashOn(10.0D));
		assertFalse(TerminalUiLayout.unreadFlashOn(19.99D));
		assertTrue(TerminalUiLayout.unreadFlashOn(20.0D));
		assertFalse(TerminalUiLayout.unreadFlashOn(30.0D));
		assertFalse(TerminalUiLayout.unreadFlashOn(40.0D));
	}

	@Test
	void horizontalSliderMapsAndClampsBothEndpoints() {
		assertEquals(0, TerminalUiLayout.sliderTuning(400));
		assertEquals(100, TerminalUiLayout.sliderTuning(484));
		assertEquals(0, TerminalUiLayout.sliderTuning(-200));
		assertEquals(100, TerminalUiLayout.sliderTuning(900));
		assertEquals(400, TerminalUiLayout.sliderX(0));
		assertEquals(484, TerminalUiLayout.sliderX(100));
		assertEquals(442, TerminalUiLayout.sliderX(50));
	}

	@Test
	void toolGridKeepsSixFixedSlotsWhenSomeToolsAreHidden() {
		assertEquals(3, TerminalUiLayout.TOOL_COLUMNS);
		assertEquals(2, TerminalUiLayout.TOOL_ROWS);
		for (int slot = 0; slot < 6; slot++) {
			var cell = TerminalUiLayout.toolCell(slot);
			assertTrue(TerminalUiLayout.TOOLS_GRID.contains(cell));
			assertEquals(slot, TerminalUiLayout.toolSlotAt(cell.left() + 1, cell.top() + 1));
			assertEquals(slot, TerminalTool.fromSlot(slot).slot());
		}
		assertEquals(TerminalUiLayout.toolCell(5), TerminalUiLayout.toolCell(99));
	}

	@Test
	void fourClientPagesKeepTheTwoExistingWireModes() {
		assertEquals(4, TerminalPage.values().length);
		assertEquals(TerminalPage.HOME, TerminalPage.values()[0]);
		assertEquals(TerminalPage.TOOLS, TerminalPage.values()[1]);
		assertEquals(TerminalPage.RECORDS, TerminalPage.values()[2]);
		assertEquals(TerminalPage.FILES, TerminalPage.values()[3]);
		assertEquals(TerminalControlPolicy.Mode.SIGNAL.ordinal(), TerminalPage.HOME.wireMode());
		assertEquals(TerminalControlPolicy.Mode.SIGNAL.ordinal(), TerminalPage.TOOLS.wireMode());
		assertEquals(TerminalControlPolicy.Mode.SIGNAL.ordinal(), TerminalPage.RECORDS.wireMode());
		assertEquals(TerminalControlPolicy.Mode.FILES.ordinal(), TerminalPage.FILES.wireMode());
		assertEquals(TerminalPage.HOME, TerminalPage.initialPage(TerminalControlPolicy.Mode.SIGNAL.ordinal()));
		assertEquals(TerminalPage.FILES, TerminalPage.initialPage(TerminalControlPolicy.Mode.FILES.ordinal()));
	}

	@Test
	void fileListAndContentUseIndependentThirtySeventyRegions() {
		assertTrue(TerminalUiLayout.FILE_BODY.contains(TerminalUiLayout.FILE_LIST));
		assertTrue(TerminalUiLayout.FILE_BODY.contains(TerminalUiLayout.FILE_CONTENT));
		assertTrue(TerminalUiLayout.FILE_LIST.right() <= TerminalUiLayout.FILE_DIVIDER.left());
		assertTrue(TerminalUiLayout.FILE_DIVIDER.right() <= TerminalUiLayout.FILE_CONTENT.left());
		double listShare = TerminalUiLayout.FILE_LIST.width()
				/ (double) (TerminalUiLayout.FILE_LIST.width() + TerminalUiLayout.FILE_CONTENT.width());
		assertTrue(listShare >= 0.28D && listShare <= 0.32D, () -> "list share=" + listShare);
		assertEquals(6, TerminalUiLayout.FILE_LIST_VISIBLE_ROWS);
		assertEquals(6, TerminalUiLayout.fileMaxScrollRow(12));
		assertEquals(0, TerminalUiLayout.fileMaxScrollRow(6));
		for (int row = 0; row < TerminalUiLayout.FILE_LIST_VISIBLE_ROWS; row++) {
			var bounds = TerminalUiLayout.fileListRow(row);
			assertTrue(TerminalUiLayout.FILE_LIST.contains(bounds));
			assertEquals(row, TerminalUiLayout.fileIndexAt(bounds.left() + 1, bounds.top() + 1, 0, 12));
		}
		var last = TerminalUiLayout.fileListRow(5);
		assertEquals(11, TerminalUiLayout.fileIndexAt(last.left() + 1, last.top() + 1, 6, 12));
		assertEquals(-1, TerminalUiLayout.fileIndexAt(TerminalUiLayout.FILE_CONTENT.left() + 1,
				TerminalUiLayout.FILE_CONTENT.top() + 1, 0, 12));
	}
}
