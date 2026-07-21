package com.xm.thefourthfrequency.terminal;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class AnomalyTimingTest {
	@Test
	void everyCatalogEntryHasBoundedActiveTiming() {
		for (AnomalyDefinition definition : AnomalyCatalog.definitions()) {
			int duration = AnomalyTiming.durationTicks(definition.id(), 123456789L);
			assertTrue(duration >= 1 && duration <= 800, definition.id());
		}
		assertEquals(800, AnomalyTiming.durationTicks("red_horizon", 0L));
		assertEquals(300, AnomalyTiming.durationTicks("channel_override", 0L));
		assertEquals(240, AnomalyTiming.durationTicks("peripheral_residue", 0L));
		assertEquals(80, AnomalyTiming.durationTicks("window_pulse", 0L));
		assertEquals(100, AnomalyTiming.durationTicks("experience_gap", 0L));
		assertEquals(160, AnomalyTiming.durationTicks("local_rule_collapse", 0L));
	}
}
