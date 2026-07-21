package com.xm.thefourthfrequency.terminal;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class AnomalyCatalogTest {
	@Test
	void catalogContainsSixteenStableUniqueIdsInFiveTiers() {
		assertEquals(16, AnomalyCatalog.definitions().size());
		assertEquals(16, AnomalyCatalog.definitions().stream().map(AnomalyDefinition::id).distinct().count());
		assertEquals(3, AnomalyCatalog.definitions().stream().filter(value -> value.tier() == 1).count());
		assertEquals(4, AnomalyCatalog.definitions().stream().filter(value -> value.tier() == 2).count());
		assertEquals(4, AnomalyCatalog.definitions().stream().filter(value -> value.tier() == 3).count());
		assertEquals(2, AnomalyCatalog.definitions().stream().filter(value -> value.tier() == 4).count());
		assertEquals(3, AnomalyCatalog.definitions().stream().filter(value -> value.tier() == 5).count());
		assertTrue(AnomalyCatalog.require("door_cascade").destructive());
		assertFalse(AnomalyCatalog.require("local_rule_collapse").destructive());
		assertFalse(AnomalyCatalog.contains("rework_probe"));
		assertFalse(AnomalyCatalog.contains("hostile_echo"));
		assertFalse(AnomalyCatalog.contains("disconnected_base"));
		assertFalse(AnomalyCatalog.contains("watcher_orbit"));
		assertEquals(4, AnomalyCatalog.pageCount(4));
		assertThrows(IllegalArgumentException.class, () -> AnomalyCatalog.require("arbitrary_command"));
	}

	@Test
	void unlockedSetOnlyContainsReachedTiers() {
		assertEquals(0, AnomalyCatalog.unlocked(0).size());
		assertEquals(3, AnomalyCatalog.unlocked(1).size());
		assertEquals(7, AnomalyCatalog.unlocked(2).size());
		assertEquals(16, AnomalyCatalog.unlocked(5).size());
	}
}
