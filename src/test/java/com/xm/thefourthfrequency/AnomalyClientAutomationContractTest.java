package com.xm.thefourthfrequency;

import com.xm.thefourthfrequency.test.AnomalyClientScenario;
import com.xm.thefourthfrequency.test.AnomalyTestTimeline;
import com.xm.thefourthfrequency.test.ClientGameTestSelection;
import com.xm.thefourthfrequency.terminal.AnomalyCatalog;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class AnomalyClientAutomationContractTest {
	@Test
	void clientScenarioRegistryExactlyMatchesCatalogInOrder() {
		AnomalyClientScenario.assertCatalogCoverage();
		List<String> catalog = AnomalyCatalog.definitions().stream().map(value -> value.id()).toList();
		List<String> scenarios = AnomalyClientScenario.definitions().stream().map(value -> value.id()).toList();
		assertEquals(16, scenarios.size());
		assertEquals(catalog, scenarios);
		assertEquals(16, scenarios.stream().distinct().count());
		assertEquals(16, AnomalyClientScenario.definitions().stream().map(value -> value.seed()).distinct().count());
	}

	@Test
	void acceleratedTimelinesPreserveOrderedPeakAndCleanup() {
		AnomalyTestTimeline.assertCatalogCoverage();
		for (AnomalyClientScenario scenario : AnomalyClientScenario.definitions()) {
			var timeline = scenario.timeline();
			assertTrue(timeline.acceleratedTicks() >= 4, scenario.id());
			assertTrue(timeline.peakTick() >= 2 && timeline.peakTick() < timeline.acceleratedTicks(), scenario.id());
			assertTrue(timeline.orderedPhases().size() >= 3, scenario.id());
			assertTrue(Set.of("restore", "cleanup", "remove").contains(timeline.orderedPhases().getLast()), scenario.id());
			assertEquals(timeline.orderedPhases().size(), timeline.orderedPhases().stream().distinct().count(), scenario.id());
		}
	}

	@Test
	void suiteAndSingleAnomalyFiltersAreStrict() {
		var defaults = ClientGameTestSelection.parse("all", "");
		assertTrue(defaults.runsMainline());
		assertTrue(defaults.runsAnomalies());
		assertFalse(defaults.runsAlphaRelaunch());
		assertFalse(defaults.runsMetaSmoke());
		assertTrue(defaults.runsReworkForms());
		assertTrue(defaults.runsWatcherModel());
		assertTrue(defaults.runsToolsUi());
		assertFalse(defaults.runsNoticeEntry());
		assertTrue(defaults.runsWorldInterface());

		var noticeEntry = ClientGameTestSelection.parse("notice-entry", "");
		assertTrue(noticeEntry.runsNoticeEntry());
		assertFalse(noticeEntry.runsMainline());
		assertFalse(noticeEntry.runsAnomalies());

		var relaunch = ClientGameTestSelection.parse("alpha-relaunch", "");
		assertTrue(relaunch.runsAlphaRelaunch());
		assertFalse(relaunch.runsMainline());
		assertFalse(relaunch.runsAnomalies());

		var single = ClientGameTestSelection.parse("anomalies", "phantom_echo");
		assertFalse(single.runsMainline());
		assertTrue(single.runsAnomalies());
		assertEquals("phantom_echo", single.anomalyId().orElseThrow());

		var smoke = ClientGameTestSelection.parse("anomaly-meta-smoke", "");
		assertTrue(smoke.runsMetaSmoke());
		assertFalse(smoke.runsAnomalies());
		var reworkForms = ClientGameTestSelection.parse("rework-forms", "");
		assertTrue(reworkForms.runsReworkForms());
		assertFalse(reworkForms.runsMainline());
		assertFalse(reworkForms.runsAnomalies());
		var toolsUi = ClientGameTestSelection.parse("tools-ui", "");
		assertTrue(toolsUi.runsMainline());
		assertTrue(toolsUi.runsToolsUi());
		assertFalse(toolsUi.runsAnomalies());
		var watcherModel = ClientGameTestSelection.parse("watcher-model", "");
		assertTrue(watcherModel.runsWatcherModel());
		assertFalse(watcherModel.runsMainline());
		assertFalse(watcherModel.runsAnomalies());
		assertFalse(watcherModel.runsReworkForms());
		var worldInterface = ClientGameTestSelection.parse("world-interface", "");
		assertTrue(worldInterface.runsWorldInterface());
		assertFalse(worldInterface.runsMainline());
		assertFalse(worldInterface.runsAnomalies());
		assertThrows(IllegalArgumentException.class,
				() -> ClientGameTestSelection.parse("mainline", "phantom_echo"));
		assertThrows(IllegalArgumentException.class,
				() -> ClientGameTestSelection.parse("watcher-model", "dark_watcher"));
		assertThrows(IllegalArgumentException.class,
				() -> ClientGameTestSelection.parse("anomalies", "not_a_stable_id"));
		assertThrows(IllegalArgumentException.class,
				() -> ClientGameTestSelection.parse("unknown", ""));
	}
}
