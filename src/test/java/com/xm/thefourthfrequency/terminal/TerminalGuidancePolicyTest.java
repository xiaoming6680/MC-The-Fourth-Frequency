package com.xm.thefourthfrequency.terminal;

import com.xm.thefourthfrequency.world.SurvivalMilestone;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class TerminalGuidancePolicyTest {
	@Test
	void resourceTargetsAreDisclosedOneLayerAtATime() {
		int logs = SurvivalMilestone.MINED_LOGS.mask();
		int iron = logs | SurvivalMilestone.IRON.mask();

		int initial = TerminalGuidancePolicy.availableResourcesMask(logs, 0);
		assertTrue(TerminalGuidancePolicy.resourceAvailable(initial, TerminalResource.IRON));
		assertFalse(TerminalGuidancePolicy.resourceAvailable(initial, TerminalResource.REDSTONE));
		assertFalse(TerminalGuidancePolicy.resourceAvailable(initial, TerminalResource.DIAMOND));

		int firstHint = TerminalGuidancePolicy.availableResourcesMask(iron, 1);
		assertTrue(TerminalGuidancePolicy.resourceAvailable(firstHint, TerminalResource.DIAMOND));
		assertFalse(TerminalGuidancePolicy.resourceAvailable(firstHint, TerminalResource.REDSTONE));

		int secondHint = TerminalGuidancePolicy.availableResourcesMask(iron, 2);
		assertTrue(TerminalGuidancePolicy.resourceAvailable(secondHint, TerminalResource.DIAMOND));
		assertTrue(TerminalGuidancePolicy.resourceAvailable(secondHint, TerminalResource.REDSTONE));
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
