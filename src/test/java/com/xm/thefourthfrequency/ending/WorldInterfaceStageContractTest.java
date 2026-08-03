package com.xm.thefourthfrequency.ending;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/** Guards the current three-stage World Interface combat contract. */
class WorldInterfaceStageContractTest {
	@Test
	void combatHasExactlyThreeMonotonicHealthPhases() {
		assertEquals(List.of(
				WorldInterfaceStage.PHASE_1,
				WorldInterfaceStage.PHASE_2,
				WorldInterfaceStage.PHASE_3),
				java.util.Arrays.stream(WorldInterfaceStage.values())
						.filter(WorldInterfaceStage::isCombat).toList());
		assertEquals(WorldInterfaceStage.PHASE_2,
				WorldInterfaceStage.advanceCombatStage(WorldInterfaceStage.PHASE_1, 0.70D));
		assertEquals(WorldInterfaceStage.PHASE_2,
				WorldInterfaceStage.advanceCombatStage(WorldInterfaceStage.PHASE_2, 0.99D));
		assertEquals(WorldInterfaceStage.PHASE_3,
				WorldInterfaceStage.advanceCombatStage(WorldInterfaceStage.PHASE_2, 0.35D));
	}

	@Test
	void nineActionsAndTheBoundedTerrainBudgetMatchTheWorldInterfaceContract() {
		// Eight live attacks behind nine wire ids: 3 is the retired grab-slam and stays unused.
		assertEquals(8, WorldInterfaceAction.values().length);
		assertEquals(9, WorldInterfaceAction.FORCED_EVICTION.wireId());
		assertFalse(WorldInterfaceAction.FORCED_EVICTION
				.isUnlockedAt(WorldInterfaceStage.PHASE_2));
		assertEquals(8_192, WorldInterfacePolicy.MAX_PERMANENT_TERRAIN_EDITS);
		assertEquals(32, WorldInterfacePolicy.terrainEditBudgetThisTick(0));
		assertEquals(3, WorldInterfacePolicy.terrainEditBudgetThisTick(8_189));
		assertEquals(0, WorldInterfacePolicy.terrainEditBudgetThisTick(8_192));
	}
}
