package com.xm.thefourthfrequency.ending;

import com.xm.thefourthfrequency.world.FrequencyWorldData;

/** Current-world runtime gates derived exclusively from the World Interface state. */
public final class FinaleRuntimePolicy {
	private FinaleRuntimePolicy() {
	}

	public static boolean backgroundSystemsAllowed(FrequencyWorldData data) {
		WorldInterfaceState.Snapshot snapshot = WorldInterfaceState.snapshot(data);
		return !snapshot.valid() || !snapshot.present() || snapshot.stage() == WorldInterfaceStage.COMPLETE;
	}

	public static boolean pressureActive(FrequencyWorldData data) {
		WorldInterfaceState.Snapshot snapshot = WorldInterfaceState.snapshot(data);
		return snapshot.valid() && snapshot.present()
				&& snapshot.stage().wireId() >= WorldInterfaceStage.SUMMONING.wireId()
				&& snapshot.stage() != WorldInterfaceStage.COMPLETE;
	}

	public static boolean succeeded(FrequencyWorldData data) {
		WorldInterfaceState.Snapshot snapshot = WorldInterfaceState.snapshot(data);
		return snapshot.valid() && snapshot.present()
				&& snapshot.outcome() == WorldInterfaceState.Outcome.SUCCESS;
	}
}
