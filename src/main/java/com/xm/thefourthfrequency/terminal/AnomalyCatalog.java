package com.xm.thefourthfrequency.terminal;

import java.util.List;
import java.util.Map;
import java.util.Set;
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
			new AnomalyDefinition("organ_misread", 2, PRIVATE, false, false),
			new AnomalyDefinition("viewpoint_separation", 3, PRIVATE, false, false),
			new AnomalyDefinition("door_cascade", 3, SHARED, false, true),
			new AnomalyDefinition("experience_gap", 3, SHARED, false, false),
			new AnomalyDefinition("local_rule_collapse", 3, PRIVATE, false, false),
			new AnomalyDefinition("red_horizon", 4, PRIVATE, false, false),
			new AnomalyDefinition("window_pulse", 4, PRIVATE, true, false),
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
		return pool(tier);
	}
	public static List<AnomalyDefinition> pool(int stage) {
		int clamped = Math.clamp(stage, 0, 5);
		if (clamped == 0) return List.of();
		return DEFINITIONS.stream().filter(value -> inPool(value, clamped)).toList();
	}
	public static List<AnomalyDefinition> weightedPool(int stage, Set<String> recentIds, boolean strongAllowed) {
		int clamped = Math.clamp(stage, 0, 5);
		Set<String> excluded = recentIds == null ? Set.of() : recentIds;
		List<AnomalyDefinition> base = pool(clamped).stream()
				.filter(value -> strongAllowed || !value.strong())
				.filter(value -> !excluded.contains(value.id()))
				.toList();
		if (base.isEmpty() && !excluded.isEmpty()) {
			base = pool(clamped).stream().filter(value -> strongAllowed || !value.strong()).toList();
		}
		java.util.ArrayList<AnomalyDefinition> weighted = new java.util.ArrayList<>();
		for (AnomalyDefinition definition : base) {
			int weight = definition.tier() == clamped ? 3 : 1;
			for (int index = 0; index < weight; index++) weighted.add(definition);
		}
		return List.copyOf(weighted);
	}
	public static int indexOf(String id) {
		for (int index = 0; index < DEFINITIONS.size(); index++) {
			if (DEFINITIONS.get(index).id().equals(id)) return index;
		}
		return -1;
	}
	public static int pageCount(int pageSize) {
		if (pageSize < 1) throw new IllegalArgumentException("pageSize must be positive");
		return Math.max(1, (DEFINITIONS.size() + pageSize - 1) / pageSize);
	}

	private static boolean inPool(AnomalyDefinition definition, int stage) {
		return switch (stage) {
			case 1 -> definition.tier() == 1;
			case 2 -> definition.tier() == 1 || definition.tier() == 2;
			case 3 -> definition.tier() == 2 || definition.tier() == 3;
			case 4 -> definition.tier() == 3 || definition.tier() == 4;
			case 5 -> definition.tier() >= 4
					|| definition.id().equals("experience_gap")
					|| definition.id().equals("local_rule_collapse");
			default -> false;
		};
	}
}
