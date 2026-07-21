package com.xm.thefourthfrequency.state;

import com.xm.thefourthfrequency.content.TerminalData;
import net.minecraft.nbt.CompoundTag;

/** Typed view of the stable schema-v4 story keys stored on a personal terminal. */
public record StoryState(
		boolean bound,
		int bandStage,
		int plotStage,
		int calibratedBandsMask,
		boolean secondCacheUnlocked,
		boolean nightEntered,
		boolean nightWitnessed,
		int preludeAnomalyMask,
		boolean watcherWitnessed,
		String proofRoute,
		String facilityEvidence,
		boolean localFileUnlocked,
		boolean riftObserved,
		boolean continuityLearned,
		int bodyProgress,
		int bodyStage,
		int endingVersion
) {
	public StoryState {
		bandStage = Math.clamp(bandStage, 0, 3);
		plotStage = Math.max(0, plotStage);
		calibratedBandsMask &= 0b111;
		preludeAnomalyMask &= 0b1111;
		proofRoute = safe(proofRoute, "none");
		facilityEvidence = safe(facilityEvidence, "");
		bodyProgress = Math.clamp(bodyProgress, 0, 1000);
		bodyStage = Math.clamp(bodyStage, 0, 3);
		endingVersion = Math.max(0, endingVersion);
	}

	public static StoryState read(CompoundTag tag) {
		return new StoryState(
				tag.getBooleanOr(TerminalData.BOUND, false),
				tag.getIntOr(TerminalData.BAND_STAGE, 0),
				tag.getIntOr(TerminalData.PLOT_STAGE, 1),
				tag.getIntOr(TerminalData.CALIBRATED_BANDS_MASK, 0),
				tag.getBooleanOr(TerminalData.SECOND_CACHE_UNLOCKED, false),
				tag.getBooleanOr(TerminalData.NIGHT_ENTERED, false),
				tag.getBooleanOr(TerminalData.NIGHT_WITNESSED, false),
				tag.getIntOr(TerminalData.PRELUDE_ANOMALY_MASK, 0),
				tag.getBooleanOr(TerminalData.WATCHER_WITNESSED, false),
				tag.getStringOr(TerminalData.PROOF_ROUTE, "none"),
				tag.getStringOr(TerminalData.FACILITY_EVIDENCE, ""),
				tag.getBooleanOr(TerminalData.LOCAL_FILE_UNLOCKED, false),
				tag.getBooleanOr(TerminalData.RIFT_OBSERVED, false),
				tag.getBooleanOr(TerminalData.CONTINUITY_LEARNED, false),
				tag.getIntOr(TerminalData.BODY_PROGRESS, 0),
				tag.getIntOr(TerminalData.BODY_STAGE, 0),
				tag.getIntOr(TerminalData.ENDING_VERSION, 0));
	}

	public void writeTo(CompoundTag tag) {
		tag.putBoolean(TerminalData.BOUND, bound);
		tag.putInt(TerminalData.BAND_STAGE, bandStage);
		tag.putInt(TerminalData.PLOT_STAGE, plotStage);
		tag.putInt(TerminalData.CALIBRATED_BANDS_MASK, calibratedBandsMask);
		tag.putBoolean(TerminalData.SECOND_CACHE_UNLOCKED, secondCacheUnlocked);
		tag.putBoolean(TerminalData.NIGHT_ENTERED, nightEntered);
		tag.putBoolean(TerminalData.NIGHT_WITNESSED, nightWitnessed);
		tag.putInt(TerminalData.PRELUDE_ANOMALY_MASK, preludeAnomalyMask);
		tag.putBoolean(TerminalData.WATCHER_WITNESSED, watcherWitnessed);
		tag.putString(TerminalData.PROOF_ROUTE, proofRoute);
		tag.putString(TerminalData.FACILITY_EVIDENCE, facilityEvidence);
		tag.putBoolean(TerminalData.LOCAL_FILE_UNLOCKED, localFileUnlocked);
		tag.putBoolean(TerminalData.RIFT_OBSERVED, riftObserved);
		tag.putBoolean(TerminalData.CONTINUITY_LEARNED, continuityLearned);
		tag.putInt(TerminalData.BODY_PROGRESS, bodyProgress);
		tag.putInt(TerminalData.BODY_STAGE, bodyStage);
		tag.putInt(TerminalData.ENDING_VERSION, endingVersion);
	}

	public int preludeExposure() {
		return (nightWitnessed ? 1 : 0)
				+ Math.min(2, Integer.bitCount(preludeAnomalyMask))
				+ (watcherWitnessed ? 1 : 0);
	}

	public boolean preludeReady() {
		return Integer.bitCount(preludeAnomalyMask) >= 1 && preludeExposure() >= 3;
	}

	private static String safe(String value, String fallback) {
		return value == null ? fallback : value;
	}
}
