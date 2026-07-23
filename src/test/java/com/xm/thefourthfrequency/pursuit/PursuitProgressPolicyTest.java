package com.xm.thefourthfrequency.pursuit;

import com.xm.thefourthfrequency.world.SurvivalMilestone;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class PursuitProgressPolicyTest {
	@Test
	void formOneUsesMultipleActivityRoutesAndNeverDependsOnIron() {
		assertFalse(PursuitProgressPolicy.earlyFormEligible(false, 1,
				PursuitActivityProof.MINING.mask(), 0L));
		assertFalse(PursuitProgressPolicy.earlyFormEligible(true, 0,
				PursuitActivityProof.MINING.mask(), 0L));
		assertTrue(PursuitProgressPolicy.earlyFormEligible(true, 1,
				PursuitActivityProof.EXPLORATION.mask(), 0L));
		assertTrue(PursuitProgressPolicy.earlyFormEligible(true, 1, 0,
				PursuitProgressPolicy.FORM_ONE_ACTIVITY_FALLBACK_TICKS));
	}

	@Test
	void derivesFiveStoryPermissionsWithoutSkippingTheActualForm() {
		int enteredNether = SurvivalMilestone.ENTERED_NETHER.mask();
		int returnedWithRods = enteredNether
				| SurvivalMilestone.RETURNED_NETHER.mask()
				| SurvivalMilestone.COLLECTED_BLAZE_RODS.mask();
		assertEquals(1, PursuitProgressPolicy.allowedForm(0, 0, true));
		assertEquals(2, PursuitProgressPolicy.allowedForm(enteredNether, 0, true));
		assertEquals(3, PursuitProgressPolicy.allowedForm(returnedWithRods, 0, true));
		assertEquals(4, PursuitProgressPolicy.allowedForm(returnedWithRods, 3, true));
		assertEquals(5, PursuitProgressPolicy.allowedForm(
				returnedWithRods | SurvivalMilestone.FOUND_STRONGHOLD.mask(), 0, false));

		assertEquals(1, PursuitProgressPolicy.actualForm(0));
		assertEquals(2, PursuitProgressPolicy.actualForm(1));
		assertEquals(5, PursuitProgressPolicy.actualForm(4));
		assertEquals(5, PursuitProgressPolicy.actualForm(5));
	}

	@Test
	void aLargeStoryJumpCreatesOnePendingPursuitAndNoCatchUpDebt() {
		assertTrue(PursuitProgressPolicy.pendingAfterAllowedFormUpdate(false, 0, 4, 0));
		assertFalse(PursuitProgressPolicy.pendingAfterAllowedFormUpdate(false, 4, 4, 1));
		assertTrue(PursuitProgressPolicy.pendingAfterAllowedFormUpdate(true, 4, 5, 0));
		assertEquals(1, PursuitProgressPolicy.resolvedAfterSuccess(0));
		assertEquals(5, PursuitProgressPolicy.resolvedAfterSuccess(5));
		assertTrue(PursuitProgressPolicy.pendingAfterSuccess(1, 4));
		assertFalse(PursuitProgressPolicy.pendingAfterSuccess(1, 1));
		assertFalse(PursuitProgressPolicy.pendingAfterSuccess(5, 5));
	}

	@Test
	void formalStartRequiresPendingPermissionTutorialAndCooldown() {
		assertFalse(PursuitProgressPolicy.canStart(false, 1, 0, true, 100L, 0L));
		assertFalse(PursuitProgressPolicy.canStart(true, 0, 0, true, 100L, 0L));
		assertFalse(PursuitProgressPolicy.canStart(true, 1, 0, false, 100L, 0L));
		assertFalse(PursuitProgressPolicy.canStart(true, 1, 0, true, 99L, 100L));
		assertTrue(PursuitProgressPolicy.canStart(true, 1, 0, true, 100L, 100L));
		assertFalse(PursuitProgressPolicy.canStart(true, 5, 5, true, 100L, 0L));
	}

	@Test
	void terminalStagesUsePersonalPursuitAndLateStoryState() {
		assertEquals(0, PursuitProgressPolicy.terminalVisualStage(0, 5, 5));
		assertEquals(1, PursuitProgressPolicy.terminalVisualStage(1, 5, 5));
		assertEquals(1, PursuitProgressPolicy.terminalVisualStage(3, 3, 5));
		assertEquals(1, PursuitProgressPolicy.terminalVisualStage(3, 4, 3));
		assertEquals(2, PursuitProgressPolicy.terminalVisualStage(3, 4, 4));
	}
}
