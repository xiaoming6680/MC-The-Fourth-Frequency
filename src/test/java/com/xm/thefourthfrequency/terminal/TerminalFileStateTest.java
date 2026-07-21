package com.xm.thefourthfrequency.terminal;

import com.xm.thefourthfrequency.narrative.NarrativeFileCatalog;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class TerminalFileStateTest {
	@Test
	void catalogHasTheTwelveFixedFilesInStoryOrder() {
		assertEquals(12, NarrativeFileCatalog.definitions().size());
		assertEquals("maintenance_handoff", NarrativeFileCatalog.definitions().getFirst().id());
		assertEquals("encrypted_witness_file", NarrativeFileCatalog.definitions().get(6).id());
		assertEquals("permanent_aftermath_record", NarrativeFileCatalog.definitions().getLast().id());
	}

}
