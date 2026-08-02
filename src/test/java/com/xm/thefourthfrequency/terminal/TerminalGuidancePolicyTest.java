package com.xm.thefourthfrequency.terminal;

import com.xm.thefourthfrequency.world.SurvivalMilestone;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class TerminalGuidancePolicyTest {
	@Test
	void refreshCanChooseEveryPublishedMineralAsSoonAsTheToolUnlocks() {
		int logs = SurvivalMilestone.MINED_LOGS.mask();
		int initial = TerminalGuidancePolicy.availableResourcesMask(logs, 0);
		assertTrue(TerminalGuidancePolicy.resourceAvailable(initial, TerminalResource.IRON));
		assertTrue(TerminalGuidancePolicy.resourceAvailable(initial, TerminalResource.COAL));
		assertTrue(TerminalGuidancePolicy.resourceAvailable(initial, TerminalResource.GOLD));
		assertTrue(TerminalGuidancePolicy.resourceAvailable(initial, TerminalResource.DIAMOND));
		assertFalse(TerminalGuidancePolicy.resourceAvailable(0, TerminalResource.IRON));
		assertFalse(TerminalGuidancePolicy.resourceAvailable(initial, TerminalResource.NONE));
	}

	@Test
	void strongholdToolUnlocksOnlyAfterThreeEyesOfEnderWereObtained() {
		int unrelatedProgress = SurvivalMilestone.CRAFTED_EYE.mask()
				| SurvivalMilestone.FOUND_STRONGHOLD.mask()
				| SurvivalMilestone.ENTERED_END.mask();
		int twoEyes = TerminalGuidancePolicy.availableToolsMask(unrelatedProgress, false, 2);
		assertFalse(TerminalGuidancePolicy.available(twoEyes, TerminalTool.STRONGHOLD));

		int threeEyes = TerminalGuidancePolicy.availableToolsMask(0, false, 3);
		assertTrue(TerminalGuidancePolicy.available(threeEyes, TerminalTool.STRONGHOLD));
	}

	@Test
	void homeOnlyReceivesASecondSuggestionAfterStallOrImmediateShelterNeed() {
		int milestones = SurvivalMilestone.MINED_LOGS.mask() | SurvivalMilestone.IRON.mask();
		int tools = TerminalGuidancePolicy.availableToolsMask(milestones, false, 0);
		var focused = TerminalGuidancePolicy.recommendations("enter_nether", tools,
				TerminalToolService.NO_TOOL, true, 2_000L, false, 0);
		assertEquals(TerminalTool.NAVIGATION.slot(), focused.primary());
		assertEquals(TerminalToolService.NO_TOOL, focused.secondary());

		var stalled = TerminalGuidancePolicy.recommendations("enter_nether", tools,
				TerminalToolService.NO_TOOL, true, 2_000L, false, 1);
		assertEquals(TerminalTool.MINERALS.slot(), stalled.secondary());

		var nightWithoutHome = TerminalGuidancePolicy.recommendations("enter_nether", tools,
				TerminalToolService.NO_TOOL, false, 12_000L, false, 0);
		assertEquals(TerminalTool.HOME.slot(), nightWithoutHome.secondary());
	}
}
