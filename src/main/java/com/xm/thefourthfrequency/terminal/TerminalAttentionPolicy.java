package com.xm.thefourthfrequency.terminal;

/** Pure edge-trigger and delay rules for task-completion and unread attention feedback. */
public final class TerminalAttentionPolicy {
	public static final long UNREAD_REMINDER_DELAY_TICKS = 60L * 20L;

	private TerminalAttentionPolicy() {
	}

	public static int completionToNotify(int taskIndex, boolean claimable, int taskCount, int notifiedMask) {
		if (!claimable || taskIndex < 0 || taskIndex >= taskCount) return -1;
		return (notifiedMask & 1 << taskIndex) == 0 ? taskIndex : -1;
	}

	public static int markCompletionNotified(int notifiedMask, int taskIndex) {
		return taskIndex < 0 || taskIndex >= Integer.SIZE ? notifiedMask : notifiedMask | 1 << taskIndex;
	}

	public static boolean unreadStarted(boolean hasUnread, boolean latched) {
		return hasUnread && !latched;
	}

	public static boolean unreadReminderDue(int unreadCount, long unreadSince, long now, boolean alreadySent) {
		return unreadCount > 0 && !alreadySent && now - unreadSince >= UNREAD_REMINDER_DELAY_TICKS;
	}

	/**
	 * Whether the terminal is currently asking for the player's attention.
	 *
	 * <p>The single definition behind both amber lamps - the one baked into the item's six forms
	 * and the one on the open panel's hardware column. They are the same statement seen from two
	 * places, so they may not be two pieces of arithmetic: an approximation on the UI side would
	 * eventually disagree with the item in the player's own hand about whether anything is
	 * waiting.</p>
	 *
	 * <p>Four sources, and they are an OR because the lamp answers one question - "is there
	 * something here for me" - not "which of these four is it". The panel behind it is what says
	 * which.</p>
	 *
	 * @param unreadSignals              unread entries in the signal log
	 * @param unreadFiles                files discovered but never opened
	 * @param navigationCompletionUnread a navigation run finished and was never acknowledged
	 * @param claimableReward            the current task is complete and its reward is unclaimed
	 */
	public static boolean attentionActive(int unreadSignals, int unreadFiles,
			boolean navigationCompletionUnread, boolean claimableReward) {
		return unreadSignals > 0 || unreadFiles > 0 || navigationCompletionUnread || claimableReward;
	}
}
