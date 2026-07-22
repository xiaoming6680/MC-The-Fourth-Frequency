package com.xm.thefourthfrequency.ending;

import java.util.Arrays;
import java.util.List;

/**
 * Stable, one-way lifecycle for the world-interface encounter.
 *
 * <p>{@link #UNPREPARED} is a pre-encounter sentinel. The ten values after it are the formal
 * encounter lifecycle and their wire IDs must never be reordered.</p>
 */
public enum WorldInterfaceStage {
	UNPREPARED(0, "unprepared"),
	ARENA_READY(1, "arena_ready"),
	WAITING_TERMINALS(2, "waiting_terminals"),
	SUMMONING(3, "summoning"),
	PHASE_1(4, "phase_1"),
	PHASE_2(5, "phase_2"),
	PHASE_3(6, "phase_3"),
	SUCCESS_RESOLUTION(7, "success_resolution"),
	FAILURE_RESOLUTION(8, "failure_resolution"),
	PORTAL_OPEN(9, "portal_open"),
	COMPLETE(10, "complete");

	private static final int FORMAL_ENCOUNTER_STAGE_COUNT = 10;

	private final int wireId;
	private final String serializedName;

	WorldInterfaceStage(int wireId, String serializedName) {
		this.wireId = wireId;
		this.serializedName = serializedName;
	}

	public int wireId() {
		return wireId;
	}

	public String serializedName() {
		return serializedName;
	}

	public static int formalEncounterStageCount() {
		return FORMAL_ENCOUNTER_STAGE_COUNT;
	}

	public boolean isCombat() {
		return this == PHASE_1 || this == PHASE_2 || this == PHASE_3;
	}

	public boolean isResolution() {
		return this == SUCCESS_RESOLUTION || this == FAILURE_RESOLUTION;
	}

	/** Returns only direct, legal successors. Self-transitions are intentionally excluded. */
	public List<WorldInterfaceStage> allowedNext() {
		return switch (this) {
			case UNPREPARED -> List.of(ARENA_READY);
			case ARENA_READY -> List.of(WAITING_TERMINALS);
			case WAITING_TERMINALS -> List.of(SUMMONING);
			case SUMMONING -> List.of(PHASE_1);
			case PHASE_1 -> List.of(PHASE_2);
			case PHASE_2 -> List.of(PHASE_3);
			case PHASE_3 -> List.of(SUCCESS_RESOLUTION, FAILURE_RESOLUTION);
			case SUCCESS_RESOLUTION, FAILURE_RESOLUTION -> List.of(PORTAL_OPEN);
			case PORTAL_OPEN -> List.of(COMPLETE);
			case COMPLETE -> List.of();
		};
	}

	public boolean canTransitionTo(WorldInterfaceStage next) {
		return next != null && allowedNext().contains(next);
	}

	public static WorldInterfaceStage fromWireId(int wireId) {
		return Arrays.stream(values())
				.filter(value -> value.wireId == wireId)
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException("Unknown world-interface stage ID: " + wireId));
	}

	/** Maps the authoritative virtual-health ratio to its combat phase. */
	public static WorldInterfaceStage forHealthRatio(double healthRatio) {
		if (!Double.isFinite(healthRatio)) {
			throw new IllegalArgumentException("Health ratio must be finite");
		}
		double clamped = Math.clamp(healthRatio, 0.0D, 1.0D);
		if (clamped > 0.70D) return PHASE_1;
		if (clamped > 0.35D) return PHASE_2;
		return PHASE_3;
	}

	/** Healing cannot move a combat phase backwards. */
	public static WorldInterfaceStage advanceCombatStage(WorldInterfaceStage current, double healthRatio) {
		if (current == null || !current.isCombat()) {
			throw new IllegalArgumentException("Current stage must be a combat phase");
		}
		WorldInterfaceStage requested = forHealthRatio(healthRatio);
		return requested.wireId > current.wireId ? requested : current;
	}
}
