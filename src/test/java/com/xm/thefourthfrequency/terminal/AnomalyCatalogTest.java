package com.xm.thefourthfrequency.terminal;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

final class AnomalyCatalogTest {
	@Test
	void catalogContainsNineteenStableUniqueIdsInFiveTiers() {
		assertEquals(19, AnomalyCatalog.definitions().size());
		assertEquals(19, AnomalyCatalog.definitions().stream().map(AnomalyDefinition::id).distinct().count());
		assertEquals(4, AnomalyCatalog.definitions().stream().filter(value -> value.tier() == 1).count());
		assertEquals(6, AnomalyCatalog.definitions().stream().filter(value -> value.tier() == 2).count());
		assertEquals(5, AnomalyCatalog.definitions().stream().filter(value -> value.tier() == 3).count());
		assertEquals(2, AnomalyCatalog.definitions().stream().filter(value -> value.tier() == 4).count());
		assertEquals(2, AnomalyCatalog.definitions().stream().filter(value -> value.tier() == 5).count());
		assertTrue(AnomalyCatalog.require("door_cascade").destructive());
		assertEquals(AnomalyDefinition.Scope.SHARED, AnomalyCatalog.require("light_dropout").scope());
		assertFalse(AnomalyCatalog.require("local_rule_collapse").destructive());
		assertFalse(AnomalyCatalog.contains("rework_probe"));
		assertFalse(AnomalyCatalog.contains("hostile_echo"));
		assertFalse(AnomalyCatalog.contains("disconnected_base"));
		assertFalse(AnomalyCatalog.contains("watcher_orbit"));
		assertThrows(IllegalArgumentException.class, () -> AnomalyCatalog.require("arbitrary_command"));
	}

	@Test
	void sustainedAnomaliesAreSpreadAcrossTheEarlyAndMiddleTiers() {
		// One long-form anomaly per early tier, so the quiet stretches between the short events
		// have something to fill them from the very first stage onward.
		assertEquals(1, AnomalyCatalog.require("silent_world").tier());
		assertEquals(2, AnomalyCatalog.require("temporal_drift").tier());
		assertEquals(3, AnomalyCatalog.require("metric_drift").tier());
		for (String id : new String[]{"silent_world", "temporal_drift", "metric_drift"}) {
			assertFalse(AnomalyCatalog.require(id).strong(),
					id + " must not consume the strong-interface cooldown");
			assertFalse(AnomalyCatalog.require(id).destructive(), id + " must not alter the world");
			assertEquals(AnomalyDefinition.Scope.PRIVATE, AnomalyCatalog.require(id).scope());
		}
	}

	@Test
	void slidingPoolsRetireOldContentWithoutShrinkingTheMiddleGame() {
		assertEquals(0, AnomalyCatalog.unlocked(0).size());
		assertEquals(4, AnomalyCatalog.unlocked(1).size());
		assertEquals(10, AnomalyCatalog.unlocked(2).size());
		assertEquals(11, AnomalyCatalog.unlocked(3).size());
		assertEquals(7, AnomalyCatalog.unlocked(4).size());
		assertEquals(6, AnomalyCatalog.unlocked(5).size());
		assertFalse(AnomalyCatalog.unlocked(3).stream().anyMatch(value -> value.id().equals("phantom_echo")));
		assertTrue(AnomalyCatalog.unlocked(5).stream().anyMatch(value -> value.id().equals("experience_gap")));
	}

	@Test
	void recentThreeAreExcludedAndNewStageContentHasTripleWeight() {
		var weighted = AnomalyCatalog.weightedPool(2,
				Set.of("phantom_echo", "light_dropout", "surface_fracture"), true);
		// Seven survivors after the recent-three exclusion: silent_world at weight 1 (tier below
		// the requested stage) plus six tier-2 entries at the triple new-content weight.
		assertEquals(19, weighted.size());
		assertEquals(3, weighted.stream().filter(value -> value.id().equals("organ_misread")).count());
		assertFalse(weighted.stream().anyMatch(value -> value.id().equals("phantom_echo")));
	}
}
