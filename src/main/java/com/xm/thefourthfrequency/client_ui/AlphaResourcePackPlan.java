package com.xm.thefourthfrequency.client_ui;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class AlphaResourcePackPlan {
	public static final String PROGRAMMER_ART_PACK_ID = "programmer_art";
	public static final String GOLDEN_DAYS_BASE_PACK_ID = "thefourthfrequency:golden_days_base";
	public static final String GOLDEN_DAYS_ALPHA_PACK_ID = "thefourthfrequency:golden_days_alpha";
	public static final List<String> SESSION_BASES_LOW_TO_HIGH = List.of(
			PROGRAMMER_ART_PACK_ID,
			GOLDEN_DAYS_BASE_PACK_ID,
			GOLDEN_DAYS_ALPHA_PACK_ID);
	private static final Set<String> HIDDEN_PACK_IDS = Set.copyOf(SESSION_BASES_LOW_TO_HIGH);

	private AlphaResourcePackPlan() {
	}

	public static List<String> selectionForSession(Collection<String> originalLowToHigh,
			Collection<String> availablePackIds) {
		LinkedHashSet<String> result = new LinkedHashSet<>(originalLowToHigh);
		result.removeAll(HIDDEN_PACK_IDS);
		for (String packId : SESSION_BASES_LOW_TO_HIGH) {
			if (availablePackIds.contains(packId)) result.add(packId);
		}
		return List.copyOf(result);
	}

	public static boolean isHiddenFromSelectionScreen(String packId) {
		return HIDDEN_PACK_IDS.contains(packId);
	}
}
