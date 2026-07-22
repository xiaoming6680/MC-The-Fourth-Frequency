package com.xm.thefourthfrequency.ending;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Source-level guard for the one-way handoff from the retired Misread-body finale. */
class LegacyFinaleIsolationTest {
	@Test
	void worldInterfacePresenceOwnsEveryLegacyEntryAndRecoveryPath() throws IOException {
		String legacy = Files.readString(Path.of(
				"src/main/java/com/xm/thefourthfrequency/ending/FinalConfrontationService.java"),
				StandardCharsets.UTF_8);
		String encounter = Files.readString(Path.of(
				"src/main/java/com/xm/thefourthfrequency/ending/EndBossEncounterService.java"),
				StandardCharsets.UTF_8);

		assertTrue(legacy.contains("return WorldInterfaceState.snapshot(data).present();"),
				"even a rejected world_interface payload must own the finale and forbid legacy fallback");
		assertTrue(legacy.contains("if (worldInterfaceOwnsFinale(data)) {"));
		assertTrue(legacy.contains("return !worldInterfaceOwnsFinale(data)"),
				"the public legacy-altar predicate must be closed once the new root exists");
		assertTrue(encounter.contains("FinalConfrontationService.retireLegacyAltar(server);"),
				"both new activation and restart recovery must retire loaded legacy entities");
		assertTrue(legacy.contains("entity instanceof MisreadBodyEntity body"),
				"retirement must also remove unbound Misread bodies left in the End");
	}
}
