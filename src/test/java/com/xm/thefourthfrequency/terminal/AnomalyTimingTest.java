package com.xm.thefourthfrequency.terminal;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

final class AnomalyTimingTest {
	/** Anomalies meant to be lived through rather than witnessed; see AnomalyCatalog. */
	private static final Set<String> SUSTAINED =
			Set.of("silent_world", "temporal_drift", "metric_drift");
	private static final int MAX_EVENT_TICKS = 800;
	private static final int MIN_SUSTAINED_TICKS = 2_400;
	private static final int MAX_SUSTAINED_TICKS = 6_000;

	@Test
	void everyCatalogEntryHasBoundedActiveTiming() {
		for (AnomalyDefinition definition : AnomalyCatalog.definitions()) {
			int duration = AnomalyTiming.durationTicks(definition.id(), 123456789L);
			if (SUSTAINED.contains(definition.id())) {
				// These exist specifically to outlast the short events. Holding them to the same
				// ceiling would reinstate the gap the sustained tier was added to close.
				assertTrue(duration >= MIN_SUSTAINED_TICKS && duration <= MAX_SUSTAINED_TICKS,
						definition.id() + " must stay in the sustained band, was " + duration);
			} else {
				assertTrue(duration >= 1 && duration <= MAX_EVENT_TICKS, definition.id());
			}
		}
		assertEquals(800, AnomalyTiming.durationTicks("red_horizon", 0L));
		assertEquals(300, AnomalyTiming.durationTicks("channel_override", 0L));
		assertEquals(240, AnomalyTiming.durationTicks("peripheral_residue", 0L));
		assertEquals(80, AnomalyTiming.durationTicks("window_pulse", 0L));
		assertEquals(100, AnomalyTiming.durationTicks("experience_gap", 0L));
		assertEquals(160, AnomalyTiming.durationTicks("local_rule_collapse", 0L));
	}

	@Test
	void sustainedAnomaliesOutlastEveryShortEventAcrossTheWholeSeedRange() {
		// The whole point of the sustained tier is duration, so no seed may let one of them come
		// in shorter than the longest ordinary event.
		for (long seed : new long[]{0L, 1L, -1L, Long.MAX_VALUE, Long.MIN_VALUE, 987654321L}) {
			for (String id : SUSTAINED) {
				int duration = AnomalyTiming.durationTicks(id, seed);
				assertTrue(duration > MAX_EVENT_TICKS,
						id + " at seed " + seed + " was only " + duration + " ticks");
				assertTrue(duration <= MAX_SUSTAINED_TICKS,
						id + " at seed " + seed + " overran at " + duration + " ticks");
			}
		}
	}
}
