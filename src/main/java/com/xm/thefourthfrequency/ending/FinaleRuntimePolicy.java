package com.xm.thefourthfrequency.ending;

import com.xm.thefourthfrequency.world.FrequencyWorldData;

/** Current-world runtime gates derived exclusively from the World Interface state. */
public final class FinaleRuntimePolicy {
	private FinaleRuntimePolicy() {
	}

	/**
	 * Whether ambient anomalies, empty segments and the rest of the background may still fire.
	 *
	 * <p>This used to open back up at {@link WorldInterfaceStage#COMPLETE}, on the reasoning that
	 * the world keeps living after the encounter. It does not keep living like <em>that</em>. Every
	 * one of those systems exists to make the world feel like it is losing its grip on itself, and
	 * the whole mainline is the story of finding out why and putting a stop to it. Firing them at
	 * someone who has walked back out of the exit portal tells them the thing they just ended did
	 * not end - which is not an epilogue, it is the ending being taken back.</p>
	 *
	 * <p>So the gate closes at the resolution and stays closed. It is the same instant {@link
	 * #concluded} names, and for the same reason it was introduced for pursuits: once the encounter
	 * is decided, nothing that was pressure during the run should still be arriving.</p>
	 */
	public static boolean backgroundSystemsAllowed(FrequencyWorldData data) {
		return backgroundSystemsAllowed(WorldInterfaceState.snapshot(data));
	}

	public static boolean pressureActive(FrequencyWorldData data) {
		return pressureActive(WorldInterfaceState.snapshot(data));
	}

	// The four gates below are pure reads of a snapshot. They are split out so the thresholds can be
	// exercised directly: reaching them through FrequencyWorldData needs a running server, which is
	// why every one of these decisions went untested while being consulted from both ending/ and
	// pursuit/.
	static boolean backgroundSystemsAllowed(WorldInterfaceState.Snapshot snapshot) {
		if (!snapshot.valid() || !snapshot.present()) return true;
		return snapshot.stage().wireId() < WorldInterfaceStage.SUCCESS_RESOLUTION.wireId();
	}

	static boolean pressureActive(WorldInterfaceState.Snapshot snapshot) {
		return snapshot.valid() && snapshot.present()
				&& snapshot.stage().wireId() >= WorldInterfaceStage.SUMMONING.wireId()
				&& snapshot.stage() != WorldInterfaceStage.COMPLETE;
	}

	/**
	 * True once the encounter has been decided, whichever way it went.
	 *
	 * <p>Pursuits were the first system to need this instant rather than {@link
	 * WorldInterfaceStage#COMPLETE}: a player who walks out of the exit portal into the overworld
	 * has finished the mainline, and any pursuit still marked pending from an earlier story gate -
	 * the stronghold milestone alone raises the allowed form to its maximum - fired at them the
	 * moment they arrived, as an epilogue nobody wrote. {@link #backgroundSystemsAllowed} has since
	 * been closed at the same instant, for the same reason, so the two now agree.</p>
	 */
	public static boolean concluded(FrequencyWorldData data) {
		return concluded(WorldInterfaceState.snapshot(data));
	}

	public static boolean succeeded(FrequencyWorldData data) {
		return succeeded(WorldInterfaceState.snapshot(data));
	}

	static boolean concluded(WorldInterfaceState.Snapshot snapshot) {
		return snapshot.valid() && snapshot.present()
				&& snapshot.stage().wireId() >= WorldInterfaceStage.SUCCESS_RESOLUTION.wireId();
	}

	static boolean succeeded(WorldInterfaceState.Snapshot snapshot) {
		return snapshot.valid() && snapshot.present()
				&& snapshot.outcome() == WorldInterfaceState.Outcome.SUCCESS;
	}
}
