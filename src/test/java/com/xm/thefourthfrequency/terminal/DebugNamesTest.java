package com.xm.thefourthfrequency.terminal;

import com.xm.thefourthfrequency.narrative.NarrativeFileCatalog;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;

final class DebugNamesTest {
	@Test
	void everyDebugIdentifierHasAReadableChineseName() {
		for (AnomalyDefinition definition : AnomalyCatalog.definitions()) readable(DebugNames.anomaly(definition.id()));
		for (NarrativeFileCatalog.Definition definition : NarrativeFileCatalog.definitions()) readable(DebugNames.file(definition.id()));
		for (String facility : List.of("surface_shelter", "field_observation", "underground_mine_station",
				"abandoned_warehouse", "transport_node")) readable(DebugNames.facility(facility));
		for (String ending : List.of("unresolved", "active", "undiscovered_truth", "prevention_failed",
				"prevention_succeeded")) readable(DebugNames.ending(ending));
	}

	private static void readable(String value) {
		assertFalse(value.contains("_"));
		assertFalse(value.startsWith("未知"));
	}
}
