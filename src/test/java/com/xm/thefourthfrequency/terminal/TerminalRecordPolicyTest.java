package com.xm.thefourthfrequency.terminal;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class TerminalRecordPolicyTest {
	@Test
	void recordsKeepStoryEventsAndOnlyOneSummaryPerCandidateGroup() {
		assertTrue(TerminalRecordPolicy.visibleInRecords("terminal_issued"));
		assertTrue(TerminalRecordPolicy.visibleInRecords("fragment_shared_0"));
		assertTrue(TerminalRecordPolicy.visibleInRecords("fragment_candidate_2_0"));
		assertTrue(TerminalRecordPolicy.visibleInRecords("fragment_near_2"));
		assertTrue(TerminalRecordPolicy.retainedInLog("fragment_candidate_2_1"));
		assertFalse(TerminalRecordPolicy.visibleInRecords("fragment_candidate_2_1"));
		assertFalse(TerminalRecordPolicy.visibleInRecords("fragment_candidate_2_2"));
		assertFalse(TerminalRecordPolicy.visibleInRecords("fragment_marker_2"));
	}

	@Test
	void routineEnvironmentAndToolTelemetryStayOutOfRecords() {
		assertFalse(TerminalRecordPolicy.visibleInRecords("weather_changed"));
		assertFalse(TerminalRecordPolicy.visibleInRecords("dimension_changed"));
		assertFalse(TerminalRecordPolicy.visibleInRecords("resource_target_located"));
		assertFalse(TerminalRecordPolicy.visibleInRecords("resource_advice_accepted"));
		assertFalse(TerminalRecordPolicy.visibleInRecords("environment_initialized"));
		assertFalse(TerminalRecordPolicy.visibleInRecords("resource_monitor_initialized"));
		assertFalse(TerminalRecordPolicy.retainedInLog("weather_changed"));
		assertFalse(TerminalRecordPolicy.retainedInLog("resource_target_located"));
	}
}
