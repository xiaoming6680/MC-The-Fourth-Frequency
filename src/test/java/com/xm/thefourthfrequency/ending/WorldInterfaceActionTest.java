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
	void exactlyNineAttacksHaveStableExplicitIds() {
		assertEquals(9, WorldInterfaceAction.values().length);
		for (int wireId = 1; wireId <= 9; wireId++) {
			WorldInterfaceAction action = WorldInterfaceAction.values()[wireId - 1];
			assertEquals(wireId, action.wireId());
			assertEquals(action, WorldInterfaceAction.fromWireId(wireId));
		}
		assertThrows(IllegalArgumentException.class, () -> WorldInterfaceAction.fromWireId(0));
		assertThrows(IllegalArgumentException.class, () -> WorldInterfaceAction.fromWireId(10));
	}

	@Test
	void actionsUnlockCumulativelyAcrossTheThreeCombatPhases() {
		assertEquals(List.of(), WorldInterfaceAction.unlockedAt(WorldInterfaceStage.SUMMONING));
		assertEquals(4, WorldInterfaceAction.unlockedAt(WorldInterfaceStage.PHASE_1).size());
		assertEquals(7, WorldInterfaceAction.unlockedAt(WorldInterfaceStage.PHASE_2).size());
		assertEquals(9, WorldInterfaceAction.unlockedAt(WorldInterfaceStage.PHASE_3).size());
		assertFalse(WorldInterfaceAction.FORCED_EVICTION.isUnlockedAt(WorldInterfaceStage.PHASE_2));
		assertTrue(WorldInterfaceAction.FORCED_EVICTION.isUnlockedAt(WorldInterfaceStage.PHASE_3));
	}

	@Test
	void grabWeaponHotbarAndEvictionShareTheExclusiveControlLane() {
		Set<WorldInterfaceAction> expected = Set.of(
				WorldInterfaceAction.GRAB_SLAM,
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
