package com.xm.thefourthfrequency.ending;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/** Stable IDs and unlock rules for the eight world-interface attacks. */
public enum WorldInterfaceAction {
	LASER_SWEEP(1, "laser_sweep", WorldInterfaceStage.PHASE_1, false),
	ENERGY_ORB(2, "energy_orb", WorldInterfaceStage.PHASE_1, false),
	// Wire id 3 was the grab-slam, which is retired. The id is left unused rather than reassigned:
	// a saved encounter may still name it, and silently turning an old slam into a different attack
	// is worse than not recognising it at all - see fromWireIdOrEmpty.
	SKY_LANCE(4, "sky_lance", WorldInterfaceStage.PHASE_1, false),
	CHARGE_WEAPON_STEAL(5, "charge_weapon_steal", WorldInterfaceStage.PHASE_2, true),
	GRAB_THROW(6, "grab_throw", WorldInterfaceStage.PHASE_2, true),
	GAZE_HOTBAR_CLEAR(7, "gaze_hotbar_clear", WorldInterfaceStage.PHASE_2, true),
	TENDRIL_LASH(8, "tendril_lash", WorldInterfaceStage.PHASE_3, false),
	FORCED_EVICTION(9, "forced_eviction", WorldInterfaceStage.PHASE_3, true);

	private final int wireId;
	private final String serializedName;
	private final WorldInterfaceStage unlockStage;
	private final boolean exclusiveControl;

	WorldInterfaceAction(int wireId, String serializedName, WorldInterfaceStage unlockStage,
			boolean exclusiveControl) {
		this.wireId = wireId;
		this.serializedName = serializedName;
		this.unlockStage = unlockStage;
		this.exclusiveControl = exclusiveControl;
	}

	public int wireId() {
		return wireId;
	}

	public String serializedName() {
		return serializedName;
	}

	public WorldInterfaceStage unlockStage() {
		return unlockStage;
	}

	public boolean requiresExclusiveControl() {
		return exclusiveControl;
	}

	public boolean isUnlockedAt(WorldInterfaceStage stage) {
		return stage != null && stage.isCombat() && stage.wireId() >= unlockStage.wireId();
	}

	public static List<WorldInterfaceAction> unlockedAt(WorldInterfaceStage stage) {
		if (stage == null || !stage.isCombat()) return List.of();
		return Arrays.stream(values()).filter(value -> value.isUnlockedAt(stage)).toList();
	}

	public static WorldInterfaceAction fromWireId(int wireId) {
		return fromWireIdOrEmpty(wireId)
				.orElseThrow(() -> new IllegalArgumentException("Unknown world-interface action ID: " + wireId));
	}

	/**
	 * Lenient lookup, for the one place a retired id can legitimately turn up: a saved encounter's
	 * record of the last attack it ran. An id nothing answers to simply means "no previous action",
	 * which is the same thing a fresh encounter reports.
	 */
	public static Optional<WorldInterfaceAction> fromWireIdOrEmpty(int wireId) {
		return Arrays.stream(values()).filter(value -> value.wireId == wireId).findFirst();
	}
}
