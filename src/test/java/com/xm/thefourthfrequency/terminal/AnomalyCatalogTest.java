package com.xm.thefourthfrequency.terminal;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

final class AnomalyCatalogTest {
	@Test
	void catalogContainsSixteenStableUniqueIdsInFiveTiers() {
		assertEquals(16, AnomalyCatalog.definitions().size());
		assertEquals(16, AnomalyCatalog.definitions().stream().map(AnomalyDefinition::id).distinct().count());
		assertEquals(3, AnomalyCatalog.definitions().stream().filter(value -> value.tier() == 1).count());
		assertEquals(5, AnomalyCatalog.definitions().stream().filter(value -> value.tier() == 2).count());
		assertEquals(4, AnomalyCatalog.definitions().stream().filter(value -> value.tier() == 3).count());
		assertEquals(2, AnomalyCatalog.definitions().stream().filter(value -> value.tier() == 4).count());
		assertEquals(2, AnomalyCatalog.definitions().stream().filter(value -> value.tier() == 5).count());
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
	void slidingPoolsRetireOldContentWithoutShrinkingTheMiddleGame() {
		assertEquals(0, AnomalyCatalog.unlocked(0).size());
		assertEquals(3, AnomalyCatalog.unlocked(1).size());
		assertEquals(8, AnomalyCatalog.unlocked(2).size());
		assertEquals(9, AnomalyCatalog.unlocked(3).size());
		assertEquals(6, AnomalyCatalog.unlocked(4).size());
		assertEquals(6, AnomalyCatalog.unlocked(5).size());
		assertFalse(AnomalyCatalog.unlocked(3).stream().anyMatch(value -> value.id().equals("phantom_echo")));
		assertTrue(AnomalyCatalog.unlocked(5).stream().anyMatch(value -> value.id().equals("experience_gap")));
	}

	@Test
	void recentThreeAreExcludedAndNewStageContentHasTripleWeight() {
		var weighted = AnomalyCatalog.weightedPool(2,
				Set.of("phantom_echo", "light_dropout", "surface_fracture"), true);
		assertEquals(15, weighted.size());
		assertEquals(3, weighted.stream().filter(value -> value.id().equals("organ_misread")).count());
		assertFalse(weighted.stream().anyMatch(value -> value.id().equals("phantom_echo")));
	}
}
