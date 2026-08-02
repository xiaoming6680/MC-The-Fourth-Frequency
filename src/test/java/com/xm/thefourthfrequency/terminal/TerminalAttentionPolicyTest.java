package com.xm.thefourthfrequency.terminal;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class TerminalAttentionPolicyTest {
	@Test
	void taskCompletionIsRaisedExactlyOncePerTaskIndex() {
		assertEquals(-1, TerminalAttentionPolicy.completionToNotify(0, false, 11, 0));
		assertEquals(0, TerminalAttentionPolicy.completionToNotify(0, true, 11, 0));
		int notified = TerminalAttentionPolicy.markCompletionNotified(0, 0);
		assertEquals(1, notified);
		assertEquals(-1, TerminalAttentionPolicy.completionToNotify(0, true, 11, notified));
		assertEquals(1, TerminalAttentionPolicy.completionToNotify(1, true, 11, notified));
	}

	@Test
	void unreadToneOnlyStartsOnTheUnreadCycleEdge() {
		assertFalse(TerminalAttentionPolicy.unreadStarted(false, false));
		assertTrue(TerminalAttentionPolicy.unreadStarted(true, false));
		assertFalse(TerminalAttentionPolicy.unreadStarted(true, true));
		assertFalse(TerminalAttentionPolicy.unreadStarted(false, true));
	}

	@Test
	void unreadReminderWaitsOneMinuteAndOnlyFiresOncePerContinuousCycle() {
		long started = 500L;
		assertFalse(TerminalAttentionPolicy.unreadReminderDue(3, started,
				started + TerminalAttentionPolicy.UNREAD_REMINDER_DELAY_TICKS - 1L, false));
		assertTrue(TerminalAttentionPolicy.unreadReminderDue(3, started,
				started + TerminalAttentionPolicy.UNREAD_REMINDER_DELAY_TICKS, false));
		assertFalse(TerminalAttentionPolicy.unreadReminderDue(3, started,
				started + TerminalAttentionPolicy.UNREAD_REMINDER_DELAY_TICKS, true));
		assertFalse(TerminalAttentionPolicy.unreadReminderDue(0, started,
				started + TerminalAttentionPolicy.UNREAD_REMINDER_DELAY_TICKS, false));
	}
}
