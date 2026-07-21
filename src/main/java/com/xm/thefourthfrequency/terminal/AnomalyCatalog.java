package com.xm.thefourthfrequency.terminal;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.xm.thefourthfrequency.terminal.AnomalyDefinition.Scope.PRIVATE;
import static com.xm.thefourthfrequency.terminal.AnomalyDefinition.Scope.SHARED;

public final class AnomalyCatalog {
	private static final List<AnomalyDefinition> DEFINITIONS = List.of(
			new AnomalyDefinition("phantom_echo", 1, PRIVATE, false, false),
			new AnomalyDefinition("light_dropout", 1, PRIVATE, false, false),
			new AnomalyDefinition("surface_fracture", 1, PRIVATE, false, false),
			new AnomalyDefinition("peripheral_residue", 2, PRIVATE, false, false),
			new AnomalyDefinition("watcher_alignment", 2, SHARED, false, false),
			new AnomalyDefinition("dark_watcher", 2, SHARED, false, false),
			new AnomalyDefinition("action_echo", 2, PRIVATE, false, false),
			new AnomalyDefinition("viewpoint_separation", 3, PRIVATE, false, false),
			new AnomalyDefinition("door_cascade", 3, SHARED, false, true),
			new AnomalyDefinition("organ_misread", 3, PRIVATE, false, false),
			new AnomalyDefinition("experience_gap", 3, SHARED, false, false),
			new AnomalyDefinition("local_rule_collapse", 4, PRIVATE, false, false),
			new AnomalyDefinition("red_horizon", 4, PRIVATE, false, false),
			new AnomalyDefinition("window_pulse", 5, PRIVATE, true, false),
			new AnomalyDefinition("channel_override", 5, PRIVATE, true, false),
			new AnomalyDefinition("desktop_presence", 5, PRIVATE, true, false));
	private static final Map<String, AnomalyDefinition> BY_ID = DEFINITIONS.stream()
			.collect(Collectors.toUnmodifiableMap(AnomalyDefinition::id, Function.identity()));

	private AnomalyCatalog() { }

	public static List<AnomalyDefinition> definitions() { return DEFINITIONS; }
	public static AnomalyDefinition require(String id) {
		AnomalyDefinition definition = BY_ID.get(id);
		if (definition == null) throw new IllegalArgumentException("Unknown anomaly id: " + id);
		return definition;
	}
	public static boolean contains(String id) { return BY_ID.containsKey(id); }
	public static List<AnomalyDefinition> unlocked(int tier) {
		int clamped = Math.clamp(tier, 0, 5);
		return DEFINITIONS.stream().filter(value -> value.tier() <= clamped).toList();
	}
	public static int pageCount(int pageSize) {
		if (pageSize < 1) throw new IllegalArgumentException("pageSize must be positive");
		return Math.max(1, (DEFINITIONS.size() + pageSize - 1) / pageSize);
	}
}
