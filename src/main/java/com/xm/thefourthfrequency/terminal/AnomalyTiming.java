package com.xm.thefourthfrequency.terminal;

/** Canonical active presentation durations. Persistent client traces are deliberately excluded. */
public final class AnomalyTiming {
	private AnomalyTiming() { }

	public static int durationTicks(String id, long seed) {
		return switch (id) {
			case "phantom_echo" -> 200 + Math.floorMod((int) seed, 121);
			case "light_dropout" -> 100 + Math.floorMod((int) (seed >>> 8), 301);
			case "surface_fracture" -> 100;
			case "peripheral_residue" -> 240;
			case "watcher_alignment", "dark_watcher" -> 400;
			case "action_echo" -> 80;
			case "viewpoint_separation" -> 100;
			case "door_cascade" -> 80;
			case "organ_misread" -> 240;
			case "experience_gap" -> 100;
			case "local_rule_collapse" -> 160;
			case "red_horizon" -> 800;
			case "window_pulse" -> 80;
			case "desktop_presence" -> 160;
			case "channel_override" -> 300;
			default -> throw new IllegalArgumentException("Unknown anomaly timing: " + id);
		};
	}
}
