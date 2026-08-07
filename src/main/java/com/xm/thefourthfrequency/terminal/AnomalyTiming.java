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
			// One minute. It was forty seconds, which is not long enough for a sky anomaly to be
			// looked at twice - the point of this one is that the player checks the terminal, finds
			// the horizon channel already climbing, and then has to keep standing under it.
			case "red_horizon" -> 20 * 60;
			case "window_pulse" -> 80;
			case "desktop_presence" -> 160;
			case "channel_override" -> 300;
			// Sustained anomalies run for minutes, not seconds. They are meant to be lived
			// through and doubted rather than witnessed, so they are deliberately an order of
			// magnitude longer than everything above.
			case "silent_world" -> 2_400 + Math.floorMod((int) (seed >>> 16), 1_201);
			case "temporal_drift" -> 3_600 + Math.floorMod((int) (seed >>> 24), 2_401);
			case "metric_drift" -> 3_000 + Math.floorMod((int) (seed >>> 32), 1_801);
			default -> throw new IllegalArgumentException("Unknown anomaly timing: " + id);
		};
	}
}
