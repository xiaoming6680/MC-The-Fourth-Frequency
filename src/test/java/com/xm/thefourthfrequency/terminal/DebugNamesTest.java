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
	}

	private static void readable(String value) {
		assertFalse(value.contains("_"));
		assertFalse(value.startsWith("未知"));
	}
}
