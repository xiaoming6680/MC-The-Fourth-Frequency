package com.xm.thefourthfrequency.terminal;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

final class ActiveAnomalyTest {
	@Test
	void validatesTargetInstanceEarliestTickAndDuplicateCompletion() {
		UUID instance = UUID.randomUUID();
		UUID player = UUID.randomUUID();
		ActiveAnomaly active = new ActiveAnomaly(instance, player, "phantom_echo", 1, 2,
				42L, 100L, 80, 200);
		active.markRunning();
		assertFalse(active.acceptCompletion(player, instance, 179L,
				AnomalyCompletionStatus.COMPLETED));
		assertFalse(active.acceptCompletion(UUID.randomUUID(), instance, 180L,
				AnomalyCompletionStatus.COMPLETED));
		assertFalse(active.acceptCompletion(player, UUID.randomUUID(), 180L,
				AnomalyCompletionStatus.COMPLETED));
		assertTrue(active.beginCleanup());
		assertTrue(active.acceptCompletion(player, instance, 180L,
				AnomalyCompletionStatus.COMPLETED));
		assertFalse(active.acceptCompletion(player, instance, 181L,
				AnomalyCompletionStatus.COMPLETED));
		assertTrue(active.markTerminalRecorded());
		assertFalse(active.markTerminalRecorded());
	}

	@Test
	void interruptedInstanceNeverBecomesATerminalRecord() {
		ActiveAnomaly active = new ActiveAnomaly(UUID.randomUUID(), UUID.randomUUID(), "red_horizon",
				4, 0, 1L, 0L, 800, 200);
		active.markRunning();
		active.interrupt();
		assertEquals(ActiveAnomaly.Stage.COMPLETED, active.stage());
		assertEquals(AnomalyCompletionStatus.INTERRUPTED, active.completionStatus());
		assertFalse(active.markTerminalRecorded());
	}
}
