package com.xm.thefourthfrequency.ending;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldInterfaceStageTest {
	@Test
	void sentinelPlusTenFormalStagesHaveStableWireIds() {
		assertEquals(11, WorldInterfaceStage.values().length);
		assertEquals(10, WorldInterfaceStage.formalEncounterStageCount());
		for (int wireId = 0; wireId < WorldInterfaceStage.values().length; wireId++) {
			WorldInterfaceStage stage = WorldInterfaceStage.values()[wireId];
			assertEquals(wireId, stage.wireId());
			assertEquals(stage, WorldInterfaceStage.fromWireId(wireId));
		}
		assertThrows(IllegalArgumentException.class, () -> WorldInterfaceStage.fromWireId(-1));
		assertThrows(IllegalArgumentException.class, () -> WorldInterfaceStage.fromWireId(11));
	}

	@Test
	void lifecycleOnlyAllowsTheDocumentedDirectSuccessors() {
		assertEquals(List.of(WorldInterfaceStage.ARENA_READY), WorldInterfaceStage.UNPREPARED.allowedNext());
		assertEquals(List.of(WorldInterfaceStage.WAITING_TERMINALS), WorldInterfaceStage.ARENA_READY.allowedNext());
		assertEquals(List.of(WorldInterfaceStage.SUMMONING), WorldInterfaceStage.WAITING_TERMINALS.allowedNext());
		assertEquals(List.of(WorldInterfaceStage.PHASE_1), WorldInterfaceStage.SUMMONING.allowedNext());
		assertEquals(List.of(WorldInterfaceStage.PHASE_2), WorldInterfaceStage.PHASE_1.allowedNext());
		assertEquals(List.of(WorldInterfaceStage.PHASE_3), WorldInterfaceStage.PHASE_2.allowedNext());
		assertEquals(List.of(WorldInterfaceStage.SUCCESS_RESOLUTION, WorldInterfaceStage.FAILURE_RESOLUTION),
				WorldInterfaceStage.PHASE_3.allowedNext());
		assertEquals(List.of(WorldInterfaceStage.PORTAL_OPEN),
				WorldInterfaceStage.SUCCESS_RESOLUTION.allowedNext());
		assertEquals(List.of(WorldInterfaceStage.PORTAL_OPEN),
				WorldInterfaceStage.FAILURE_RESOLUTION.allowedNext());
		assertEquals(List.of(WorldInterfaceStage.COMPLETE), WorldInterfaceStage.PORTAL_OPEN.allowedNext());
		assertTrue(WorldInterfaceStage.COMPLETE.allowedNext().isEmpty());

		for (WorldInterfaceStage from : WorldInterfaceStage.values()) {
			assertFalse(from.canTransitionTo(from));
			for (WorldInterfaceStage to : WorldInterfaceStage.values()) {
				assertEquals(from.allowedNext().contains(to), from.canTransitionTo(to));
				if (to.wireId() < from.wireId()) assertFalse(from.canTransitionTo(to));
			}
		}
	}

	@Test
	void combatPhasesAdvanceAtSeventyAndThirtyFivePercentAndNeverRegress() {
		assertEquals(WorldInterfaceStage.PHASE_1, WorldInterfaceStage.forHealthRatio(1.0D));
		assertEquals(WorldInterfaceStage.PHASE_1, WorldInterfaceStage.forHealthRatio(0.700_001D));
		assertEquals(WorldInterfaceStage.PHASE_2, WorldInterfaceStage.forHealthRatio(0.70D));
		assertEquals(WorldInterfaceStage.PHASE_2, WorldInterfaceStage.forHealthRatio(0.350_001D));
		assertEquals(WorldInterfaceStage.PHASE_3, WorldInterfaceStage.forHealthRatio(0.35D));
		assertEquals(WorldInterfaceStage.PHASE_3, WorldInterfaceStage.forHealthRatio(-5.0D));
		assertEquals(WorldInterfaceStage.PHASE_2,
				WorldInterfaceStage.advanceCombatStage(WorldInterfaceStage.PHASE_1, 0.69D));
		assertEquals(WorldInterfaceStage.PHASE_2,
				WorldInterfaceStage.advanceCombatStage(WorldInterfaceStage.PHASE_2, 0.95D));
		assertEquals(WorldInterfaceStage.PHASE_3,
				WorldInterfaceStage.advanceCombatStage(WorldInterfaceStage.PHASE_2, 0.10D));
		assertThrows(IllegalArgumentException.class,
				() -> WorldInterfaceStage.advanceCombatStage(WorldInterfaceStage.SUMMONING, 0.5D));
	}
}
