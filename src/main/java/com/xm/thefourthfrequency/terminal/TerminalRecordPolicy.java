package com.xm.thefourthfrequency.terminal;

import java.util.Set;

/** Keeps operational telemetry in its dedicated tool instead of flooding the player-facing record page. */
public final class TerminalRecordPolicy {
	private static final String FRAGMENT_CANDIDATE_PREFIX = "fragment_candidate_";
	private static final Set<String> HIDDEN_TELEMETRY = Set.of(
			"environment_initialized",
			"resource_monitor_initialized",
			"weather_changed",
			"dimension_changed");

	private TerminalRecordPolicy() { }

	public static boolean retainedInLog(String type) {
		if (type == null || type.isBlank() || HIDDEN_TELEMETRY.contains(type)) return false;
		return !type.startsWith("resource_");
	}

	public static boolean visibleInRecords(String type) {
		if (!retainedInLog(type) || type.startsWith("fragment_marker_")) return false;
		if (!type.startsWith(FRAGMENT_CANDIDATE_PREFIX)) return true;
		String candidate = type.substring(FRAGMENT_CANDIDATE_PREFIX.length());
		int slotSeparator = candidate.indexOf('_');
		return slotSeparator < 0 || candidate.substring(slotSeparator + 1).equals("0");
	}
}
