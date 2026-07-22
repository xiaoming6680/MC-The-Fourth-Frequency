package com.xm.thefourthfrequency.ending;

import java.util.Arrays;

/** Stable, persisted threat states for the End encounter. IDs must never be reordered. */
public enum EndBossPhase {
	OBSERVATION(1, "observation", 0, 1, 600, 2, 12, 0),
	INVASION(2, "invasion", 1, 2, 440, 3, 24, 900),
	CONSUMPTION(3, "consumption", 2, 3, 280, 4, 36, 500),
	COLLAPSE(4, "collapse", 3, 3, 200, 4, 48, 300);

	private final int id;
	private final String serializedName;
	private final int threatRank;
	private final int massStage;
	private final int ruptureInterval;
	private final int ruptureRadius;
	private final int ruptureBlocks;
	private final int intrusionInterval;

	EndBossPhase(int id, String serializedName, int threatRank, int massStage, int ruptureInterval,
			int ruptureRadius, int ruptureBlocks, int intrusionInterval) {
		this.id = id;
		this.serializedName = serializedName;
		this.threatRank = threatRank;
		this.massStage = massStage;
		this.ruptureInterval = ruptureInterval;
		this.ruptureRadius = ruptureRadius;
		this.ruptureBlocks = ruptureBlocks;
		this.intrusionInterval = intrusionInterval;
	}

	public int id() { return id; }
	public String serializedName() { return serializedName; }
	public int threatRank() { return threatRank; }
	public int massStage() { return massStage; }
	public int ruptureInterval() { return ruptureInterval; }
	public int ruptureRadius() { return ruptureRadius; }
	public int ruptureBlocks() { return ruptureBlocks; }
	public int intrusionInterval() { return intrusionInterval; }
	public boolean hasIntrusion() { return intrusionInterval > 0; }

	public static EndBossPhase fromId(int id) {
		return Arrays.stream(values()).filter(value -> value.id == id).findFirst().orElse(OBSERVATION);
	}

	public static EndBossPhase forHealth(double healthRatio, boolean collapse) {
		if (collapse) return COLLAPSE;
		if (healthRatio <= 0.35) return CONSUMPTION;
		if (healthRatio <= 0.70) return INVASION;
		return OBSERVATION;
	}

	/** Healing and maximum-health changes can never move the encounter backwards. */
	public static EndBossPhase advance(EndBossPhase current, double healthRatio, boolean collapse) {
		EndBossPhase requested = forHealth(healthRatio, collapse);
		return requested.threatRank > current.threatRank ? requested : current;
	}
}
