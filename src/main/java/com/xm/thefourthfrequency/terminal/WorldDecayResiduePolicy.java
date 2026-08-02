package com.xm.thefourthfrequency.terminal;

/**
 * Turns a lifetime anomaly count into an irreversible floor under the world-decay stage.
 *
 * <p>Decay used to be derived purely from the current anomaly tier and survival milestones, so a
 * player sitting at one tier saw exactly the same world whether they had lived through two
 * anomalies or twenty - each one ended and the world snapped back to how it looked before. Feeding
 * the lifetime total in as a floor is what makes anomalies leave a mark: the count only ever grows,
 * so the world can get worse but never recovers, and the horror accumulates instead of resetting.</p>
 */
public final class WorldDecayResiduePolicy {
	/** Anomalies per decay stage. Roughly one stage per 40-60 minutes of normal pacing. */
	public static final int ANOMALIES_PER_STAGE = 4;
	public static final int MAX_STAGE = 5;
	/** Purely defensive; the counter is not expected to approach this in a real save. */
	public static final int MAX_TRACKED = 9_999;

	private WorldDecayResiduePolicy() {
	}

	public static int accumulate(int completedAnomalies) {
		return Math.min(MAX_TRACKED, Math.max(0, completedAnomalies) + 1);
	}

	public static int residueStage(int completedAnomalies) {
		if (completedAnomalies <= 0) return 0;
		return Math.min(MAX_STAGE, completedAnomalies / ANOMALIES_PER_STAGE);
	}
}
