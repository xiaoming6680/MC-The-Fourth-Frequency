package com.xm.thefourthfrequency.terminal;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

final class AnomalyLogBookTest {
	@Test
	void capsAtThirtyTwoAndSortsNewestFirst() {
		List<AnomalyLogBook.Entry> entries = new ArrayList<>();
		for (int sequence = 1; sequence <= 40; sequence++) {
			entries = AnomalyLogBook.append(entries, new AnomalyLogBook.Entry(sequence, sequence * 20L, true));
		}
		assertEquals(32, entries.size());
		assertEquals(40, entries.getFirst().sequence());
		assertEquals(9, entries.getLast().sequence());
	}

	@Test
	void supportsPerEntryAndMarkAllRead() {
		List<AnomalyLogBook.Entry> entries = List.of(
				new AnomalyLogBook.Entry(3, 30, true), new AnomalyLogBook.Entry(2, 20, true));
		entries = AnomalyLogBook.markRead(entries, 3);
		assertFalse(entries.getFirst().unread());
		assertEquals(1, AnomalyLogBook.unreadCount(entries));
		entries = AnomalyLogBook.markAllRead(entries);
		assertEquals(0, AnomalyLogBook.unreadCount(entries));
	}
}
