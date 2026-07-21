package com.xm.thefourthfrequency.terminal;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class AnomalyLogBook {
	private AnomalyLogBook() { }

	public static List<Entry> newestFirst(List<Entry> entries) {
		ArrayList<Entry> sorted = new ArrayList<>(entries);
		sorted.sort(Comparator.comparingInt(Entry::sequence).reversed());
		if (sorted.size() > TerminalAnomalyLog.MAX_ENTRIES) {
			sorted.subList(TerminalAnomalyLog.MAX_ENTRIES, sorted.size()).clear();
		}
		return List.copyOf(sorted);
	}

	public static List<Entry> append(List<Entry> entries, Entry entry) {
		ArrayList<Entry> next = new ArrayList<>(entries);
		next.add(entry);
		return newestFirst(next);
	}

	public static List<Entry> markRead(List<Entry> entries, int sequence) {
		return entries.stream().map(entry -> entry.sequence == sequence ? entry.read() : entry).toList();
	}

	public static List<Entry> markAllRead(List<Entry> entries) {
		return entries.stream().map(Entry::read).toList();
	}

	public static int unreadCount(List<Entry> entries) {
		return (int) entries.stream().filter(Entry::unread).count();
	}

	public record Entry(int sequence, long gameTime, boolean unread) {
		public Entry read() { return unread ? new Entry(sequence, gameTime, false) : this; }
	}
}
