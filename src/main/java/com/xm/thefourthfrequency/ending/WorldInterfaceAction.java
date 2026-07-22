package com.xm.thefourthfrequency.ending;

import java.util.Arrays;
import java.util.List;

/** Stable IDs and unlock rules for the nine world-interface attacks. */
public enum WorldInterfaceAction {
	LASER_SWEEP(1, "laser_sweep", WorldInterfaceStage.PHASE_1, false),
	ENERGY_ORB(2, "energy_orb", WorldInterfaceStage.PHASE_1, false),
	GRAB_SLAM(3, "grab_slam", WorldInterfaceStage.PHASE_1, true),
	MENTAL_ASSAULT(4, "mental_assault", WorldInterfaceStage.PHASE_1, false),
	CHARGE_WEAPON_STEAL(5, "charge_weapon_steal", WorldInterfaceStage.PHASE_2, true),
	GRAB_THROW(6, "grab_throw", WorldInterfaceStage.PHASE_2, true),
	GAZE_HOTBAR_CLEAR(7, "gaze_hotbar_clear", WorldInterfaceStage.PHASE_2, true),
	ARROW_REFLECTION(8, "arrow_reflection", WorldInterfaceStage.PHASE_3, false),
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
		return Arrays.stream(values())
				.filter(value -> value.wireId == wireId)
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException("Unknown world-interface action ID: " + wireId));
	}
}
