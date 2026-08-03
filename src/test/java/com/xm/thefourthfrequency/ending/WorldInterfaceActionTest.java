package com.xm.thefourthfrequency.ending;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldInterfaceActionTest {
	@Test
	void everyAttackHasAStableExplicitIdAndRetiredIdsStayRetired() {
		assertEquals(8, WorldInterfaceAction.values().length);
		int previous = 0;
		for (WorldInterfaceAction action : WorldInterfaceAction.values()) {
			// Ascending but not necessarily contiguous: ids are a wire contract, so a retired
			// attack leaves a hole rather than shifting everything after it onto new ids.
			assertTrue(action.wireId() > previous, "wire ids must ascend: " + action);
			previous = action.wireId();
			assertEquals(action, WorldInterfaceAction.fromWireId(action.wireId()));
		}
		assertThrows(IllegalArgumentException.class, () -> WorldInterfaceAction.fromWireId(0));
		// 3 was the grab-slam. Nothing may claim it.
		assertThrows(IllegalArgumentException.class, () -> WorldInterfaceAction.fromWireId(3));
		assertThrows(IllegalArgumentException.class, () -> WorldInterfaceAction.fromWireId(10));
		assertTrue(WorldInterfaceAction.fromWireIdOrEmpty(3).isEmpty(),
				"a saved encounter naming a retired attack must resolve to nothing, not throw");
	}

	@Test
	void actionsUnlockCumulativelyAcrossTheThreeCombatPhases() {
		assertEquals(List.of(), WorldInterfaceAction.unlockedAt(WorldInterfaceStage.SUMMONING));
		assertEquals(3, WorldInterfaceAction.unlockedAt(WorldInterfaceStage.PHASE_1).size());
		assertEquals(6, WorldInterfaceAction.unlockedAt(WorldInterfaceStage.PHASE_2).size());
		assertEquals(8, WorldInterfaceAction.unlockedAt(WorldInterfaceStage.PHASE_3).size());
		assertFalse(WorldInterfaceAction.FORCED_EVICTION.isUnlockedAt(WorldInterfaceStage.PHASE_2));
		assertTrue(WorldInterfaceAction.FORCED_EVICTION.isUnlockedAt(WorldInterfaceStage.PHASE_3));
	}

	@Test
	void grabWeaponHotbarAndEvictionShareTheExclusiveControlLane() {
		Set<WorldInterfaceAction> expected = Set.of(
				WorldInterfaceAction.CHARGE_WEAPON_STEAL,
				WorldInterfaceAction.GRAB_THROW,
				WorldInterfaceAction.GAZE_HOTBAR_CLEAR,
				WorldInterfaceAction.FORCED_EVICTION);
		Set<WorldInterfaceAction> actual = java.util.Arrays.stream(WorldInterfaceAction.values())
				.filter(WorldInterfaceAction::requiresExclusiveControl)
				.collect(java.util.stream.Collectors.toUnmodifiableSet());
		assertEquals(expected, actual);
	}
}
