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

	/**
	 * True once the encounter has been decided, whichever way it went.
	 *
	 * <p>{@link #backgroundSystemsAllowed} deliberately opens back up at {@link
	 * WorldInterfaceStage#COMPLETE}, which is correct for ambient anomalies and world decay: the
	 * world keeps living afterwards. It is not correct for pursuits. A player who walks out of the
	 * exit portal into the overworld has finished the mainline, and any pursuit still marked pending
	 * from an earlier story gate - the stronghold milestone alone raises the allowed form to its
	 * maximum - fired at them the moment they arrived, as an epilogue nobody wrote.</p>
	 */
	public static boolean concluded(FrequencyWorldData data) {
		WorldInterfaceState.Snapshot snapshot = WorldInterfaceState.snapshot(data);
		return snapshot.valid() && snapshot.present()
				&& snapshot.stage().wireId() >= WorldInterfaceStage.SUCCESS_RESOLUTION.wireId();
	}

	public static boolean succeeded(FrequencyWorldData data) {
		WorldInterfaceState.Snapshot snapshot = WorldInterfaceState.snapshot(data);
		return snapshot.valid() && snapshot.present()
				&& snapshot.outcome() == WorldInterfaceState.Outcome.SUCCESS;
	}
}
