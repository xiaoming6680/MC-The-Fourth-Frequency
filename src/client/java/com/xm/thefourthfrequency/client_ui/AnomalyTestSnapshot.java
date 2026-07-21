package com.xm.thefourthfrequency.client_ui;

import java.util.Set;
import java.util.UUID;

/** Immutable client observation surface for automated GameTest assertions. */
public record AnomalyTestSnapshot(UUID instanceId, String anomalyId, String phase, int remainingTicks,
		Set<String> overlays, int dedicatedSoundCount, int ambientSoundCount, int hiddenLightCount,
		int misreadItemCount, int fractureStage, boolean cameraSeparated, boolean inputLocked,
		boolean audioLocked, int temporaryEntityCount, int persistentTraceCount, boolean metaDegraded) {
	public AnomalyTestSnapshot {
		overlays = Set.copyOf(overlays);
	}

	public boolean active() {
		return instanceId != null;
	}
}
