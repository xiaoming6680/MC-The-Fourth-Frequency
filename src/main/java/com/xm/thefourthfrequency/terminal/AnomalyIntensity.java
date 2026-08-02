package com.xm.thefourthfrequency.terminal;

import com.xm.thefourthfrequency.pursuit.PursuitActivityProof;
import com.xm.thefourthfrequency.pursuit.PursuitProgressPolicy;
import com.xm.thefourthfrequency.world.SurvivalMilestone;

public final class AnomalyIntensity {
	public static final long HEAT_RAMP_TICKS = 20L * 60L * 20L;
	public static final long LEGACY_TIER_RAMP_TICKS = 10L * 60L * 20L;
	public static final long MIN_STAGE_EXPOSURE_TICKS = 20L * 60L * 20L;
	public static final int REQUIRED_STAGE_SUCCESSES = 2;
	public static final long LOGIN_GRACE_TICKS = 3L * 60L * 20L;
	public static final long DIMENSION_GRACE_TICKS = 90L * 20L;
	public static final long SIGNATURE_LEAD_TICKS = 30L * 20L;

	private AnomalyIntensity() { }

	public static int progressionCeiling(boolean bound, int bandStage, int milestoneMask, int eyeSamples,
			int activityProofMask, long effectiveActivityTicks, boolean endingActive) {
		if (!bound) return 0;
		if (endingActive || eyeSamples > 0 || SurvivalMilestone.FOUND_STRONGHOLD.present(milestoneMask)) return 5;
		if (SurvivalMilestone.RETURNED_NETHER.present(milestoneMask)
				&& SurvivalMilestone.COLLECTED_BLAZE_RODS.present(milestoneMask)) return 4;
		if (SurvivalMilestone.IRON.present(milestoneMask)
				|| SurvivalMilestone.PREPARED_NETHER.present(milestoneMask)
				|| SurvivalMilestone.ENTERED_NETHER.present(milestoneMask)) return 3;
		if (bandStage > 0 || PursuitActivityProof.any(activityProofMask)
				|| effectiveActivityTicks >= PursuitProgressPolicy.FORM_ONE_ACTIVITY_FALLBACK_TICKS) return 2;
		return 1;
	}

	public static int progressedStage(int currentStage, int ceiling, long stageExposureTicks,
			int successfulAnomalies) {
		int current = Math.clamp(currentStage, 0, 5);
		int limit = Math.clamp(ceiling, 0, 5);
		if (limit == 0) return 0;
		if (current == 0) return 1;
		if (current >= limit) return current;
		boolean ready = stageExposureTicks >= requiredExposureTicks(current, limit)
				&& successfulAnomalies >= REQUIRED_STAGE_SUCCESSES;
		return ready ? current + 1 : current;
	}

	/**
	 * A stage lagging two or more levels behind the mainline ceiling would otherwise pace the whole
	 * catalogue out of a normal playthrough, so the exposure requirement is halved until it catches up.
	 */
	public static long requiredExposureTicks(int currentStage, int ceiling) {
		return ceiling - currentStage >= 2 ? MIN_STAGE_EXPOSURE_TICKS / 2 : MIN_STAGE_EXPOSURE_TICKS;
	}

	public static int heatPercent(long tierOnlineTicks) {
		if (tierOnlineTicks <= 0L) return 0;
		if (tierOnlineTicks >= HEAT_RAMP_TICKS) return 100;
		return (int) (tierOnlineTicks * 100L / HEAT_RAMP_TICKS);
	}

	public static long intervalTicks(int tier, int heatPercent, int randomValue, boolean first) {
		if (tier <= 0) return Long.MAX_VALUE;
		if (first) return randomSeconds(4 * 60, 7 * 60, randomValue);
		int[] minimum = {0, 8 * 60, 8 * 60, 7 * 60, 6 * 60, 5 * 60};
		int[] maximum = {0, 14 * 60, 13 * 60, 12 * 60, 10 * 60, 9 * 60};
		int clampedTier = Math.clamp(tier, 1, 5);
		return randomSeconds(minimum[clampedTier], maximum[clampedTier], randomValue);
	}

	public static long strongCooldownTicks(int tier, int randomValue) {
		return tier < 4 ? 0L : randomSeconds(20 * 60, 30 * 60, randomValue);
	}

	private static long randomSeconds(int minimum, int maximum, int randomValue) {
		int seconds = minimum + Math.floorMod(randomValue, maximum - minimum + 1);
		return seconds * 20L;
	}
}
