package com.xm.thefourthfrequency.ending;

import java.util.Arrays;

/** Stable IDs shared by persistence, entity animation and battlefield resolution. */
public enum EndBossAction {
	NONE(0, "none"),
	OPERATOR_DASH(1, "operator_dash"),
	BUILDER_BRACE(2, "builder_brace"),
	MINER_TRENCH(3, "miner_trench"),
	ARENA_RUPTURE(4, "arena_rupture"),
	ORGAN_DISPLACEMENT(5, "organ_displacement");

	private final int id;
	private final String serializedName;

	EndBossAction(int id, String serializedName) {
		this.id = id;
		this.serializedName = serializedName;
	}

	public int id() { return id; }
	public String serializedName() { return serializedName; }

	public static EndBossAction fromId(int id) {
		return Arrays.stream(values()).filter(value -> value.id == id).findFirst().orElse(NONE);
	}
}
