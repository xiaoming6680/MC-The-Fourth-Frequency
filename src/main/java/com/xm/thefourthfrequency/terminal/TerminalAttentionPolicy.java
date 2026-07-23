package com.xm.thefourthfrequency.terminal;

/** Pure edge-trigger rules for task-completion and unread attention feedback. */
public final class TerminalAttentionPolicy {
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
}
