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

	/**
	 * The lamp lights for any of the four sources on its own, and goes dark only when all four are
	 * clear.
	 *
	 * <p>Each source is checked in isolation rather than in combination, because the failure this
	 * guards against is a source being dropped from the rule - and a dropped source is invisible in
	 * any case where some other source is also active.</p>
	 */
	@Test
	void attentionLightsForEachSourceAloneAndClearsOnlyWhenAllFourAre() {
		assertFalse(TerminalAttentionPolicy.attentionActive(0, 0, false, false));
		assertTrue(TerminalAttentionPolicy.attentionActive(1, 0, false, false), "unread signal");
		assertTrue(TerminalAttentionPolicy.attentionActive(0, 1, false, false), "unread file");
		assertTrue(TerminalAttentionPolicy.attentionActive(0, 0, true, false), "navigation completion");
		assertTrue(TerminalAttentionPolicy.attentionActive(0, 0, false, true), "claimable reward");
		assertTrue(TerminalAttentionPolicy.attentionActive(4, 2, true, true));
		// A negative count is not attention. Counts arrive from records that migrations have touched,
		// and "fewer than nothing unread" must not read as something waiting.
		assertFalse(TerminalAttentionPolicy.attentionActive(-1, -3, false, false));
	}
}
