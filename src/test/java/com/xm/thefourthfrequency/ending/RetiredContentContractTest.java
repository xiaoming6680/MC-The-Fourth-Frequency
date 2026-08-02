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
				"src/main/java/com/xm/thefourthfrequency/correction/CorrectionOrganService.java",
				"src/main/java/com/xm/thefourthfrequency/correction/CorrectionTargetService.java",
				"src/main/java/com/xm/thefourthfrequency/correction/TrendSwarmService.java",
				"src/main/resources/assets/thefourthfrequency/textures/entity/misread_body.png",
				"src/main/resources/assets/thefourthfrequency/blockstates/nascent_body_organ.json",
				"src/main/resources/assets/thefourthfrequency/blockstates/rework_scar.json",
				"src/main/resources/assets/thefourthfrequency/blockstates/rework_brace.json",
				"src/main/resources/assets/thefourthfrequency/models/block/nascent_body_organ.json",
				"src/main/resources/assets/thefourthfrequency/models/block/rework_scar.json",
				"src/main/resources/assets/thefourthfrequency/models/block/rework_brace.json",
				"src/main/resources/data/thefourthfrequency/recipe/termination_spike.json",
				"src/main/resources/data/thefourthfrequency/facilities/facilities.json",
				"src/main/resources/data/thefourthfrequency/correction/organs.json"
		}) {
			assertFalse(Files.exists(Path.of(path)), path);
		}

		String blocks = Files.readString(Path.of(
				"src/main/java/com/xm/thefourthfrequency/content/ModBlocks.java"),
				StandardCharsets.UTF_8);
		String english = Files.readString(Path.of(
				"src/main/resources/assets/thefourthfrequency/lang/en_us.json"),
				StandardCharsets.UTF_8);
		String chinese = Files.readString(Path.of(
				"src/main/resources/assets/thefourthfrequency/lang/zh_cn.json"),
				StandardCharsets.UTF_8);
		for (String retiredBlock : new String[] {
				"nascent_body_organ", "rework_scar", "rework_brace"
		}) {
			assertFalse(blocks.contains(retiredBlock), retiredBlock);
			assertFalse(english.contains("block.thefourthfrequency." + retiredBlock), retiredBlock);
			assertFalse(chinese.contains("block.thefourthfrequency." + retiredBlock), retiredBlock);
		}

		String eyeMixin = Files.readString(Path.of(
				"src/main/java/com/xm/thefourthfrequency/mixin/EnderEyeItemMixin.java"),
				StandardCharsets.UTF_8);
		assertTrue(eyeMixin.contains("StrongholdPortalService.findPortalRingNear"));
		assertTrue(eyeMixin.contains("EndBossEncounterService.prepareFromActivatedPortal"));
	}
}
