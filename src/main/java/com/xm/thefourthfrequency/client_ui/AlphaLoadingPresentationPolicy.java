package com.xm.thefourthfrequency.client_ui;

/** Decides when the one-time corruption has yielded to the persistent Alpha presentation. */
public final class AlphaLoadingPresentationPolicy {
	private AlphaLoadingPresentationPolicy() {
	}

	public static boolean usePersistentLegacyPresentation(boolean corruptionEverPlayed,
			boolean corruptionInProgress) {
		return corruptionEverPlayed && !corruptionInProgress;
	}
}

