package com.xm.thefourthfrequency.pursuit;

/** Stable behavior identities for the five personal Corrector forms. */
public final class PursuitFormPolicy {
	private static final Form[] FORMS = {
			new Form(1, "soundseeker", 60 * 20, "silence_crouch_line_of_sight"),
			new Form(2, "router", 75 * 20, "reroute_low_space_multiple_exits"),
			new Form(3, "interceptor", 85 * 20, "backtrack_turn_change_elevation"),
			new Form(4, "boundary_crosser", 95 * 20, "read_lunge_telegraph"),
			new Form(5, "interface_corrector", 110 * 20, "trust_continuous_waveform")
	};

	private PursuitFormPolicy() {
	}

	public static Form forForm(int form) {
		return FORMS[Math.clamp(form, 1, 5) - 1];
	}

	public record Form(int number, String id, int durationTicks, String counterplay) {
	}
}
