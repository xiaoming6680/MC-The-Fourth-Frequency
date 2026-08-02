package com.xm.thefourthfrequency.narrative;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class NarrativeFileCatalogTest {
	@Test
	void catalogContainsOnlyTheSevenConsolidatedPlayerFacingFiles() {
		assertEquals(List.of(
				"maintenance_handoff",
				"surface_shelter_record",
				"field_observation_record",
				"underground_mine_record",
				"abandoned_warehouse_record",
				"encrypted_witness_file",
				"body_mapping_warning"),
				NarrativeFileCatalog.definitions().stream()
						.map(NarrativeFileCatalog.Definition::id)
						.toList());
	}
}
