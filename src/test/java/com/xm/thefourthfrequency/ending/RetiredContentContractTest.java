package com.xm.thefourthfrequency.ending;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Guards the clean break: retired runtime and resource roots must not return. */
class RetiredContentContractTest {
	@Test
	void retiredFinaleAndFacilityRootsAreAbsentFromTheCurrentBuild() throws IOException {
		for (String path : new String[] {
				"src/main/java/com/xm/thefourthfrequency/entity/MisreadBodyEntity.java",
				"src/main/java/com/xm/thefourthfrequency/ending/FinalConfrontationService.java",
				"src/main/java/com/xm/thefourthfrequency/facility/FacilityService.java",
				"src/main/java/com/xm/thefourthfrequency/content/TerminationSpikeItem.java",
				"src/main/resources/assets/thefourthfrequency/textures/entity/misread_body.png",
				"src/main/resources/data/thefourthfrequency/recipe/termination_spike.json",
				"src/main/resources/data/thefourthfrequency/facilities/facilities.json"
		}) {
			assertFalse(Files.exists(Path.of(path)), path);
		}

		String eyeMixin = Files.readString(Path.of(
				"src/main/java/com/xm/thefourthfrequency/mixin/EnderEyeItemMixin.java"),
				StandardCharsets.UTF_8);
		assertTrue(eyeMixin.contains("StrongholdPortalService.findPortalRingNear"));
		assertTrue(eyeMixin.contains("EndBossEncounterService.prepareFromActivatedPortal"));
	}
}
