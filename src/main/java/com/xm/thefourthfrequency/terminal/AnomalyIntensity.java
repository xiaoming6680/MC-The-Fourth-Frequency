package com.xm.thefourthfrequency.terminal;

public final class AnomalyIntensity {
	public static final long HEAT_RAMP_TICKS = 15L * 60L * 20L;
	public static final long LEGACY_TIER_RAMP_TICKS = 10L * 60L * 20L;

	private AnomalyIntensity() { }

	public static int storyCeiling(boolean bound, int bandStage, boolean archiveOrRift,
			boolean continuityOrNetherRift, int bodyProgress, boolean endingActive) {
		if (!bound) return 0;
		if (bodyProgress >= 750 || endingActive) return 5;
		if (continuityOrNetherRift || bodyProgress > 0) return 4;
		if (archiveOrRift) return 3;
		if (bandStage > 0) return 2;
		return 1;
	}

	public static int survivalStoryCeiling(boolean bound, int bandStage, boolean archiveOrRift,
			boolean continuityOrNetherRift, int milestoneMask, boolean endingActive) {
		if (!bound) return 0;
		if ((milestoneMask & (1 << 7)) != 0 || endingActive) return 5;
		if ((milestoneMask & ((1 << 3) | (1 << 4))) != 0 || continuityOrNetherRift) return 4;
		if ((milestoneMask & ((1 << 1) | (1 << 2))) != 0 || archiveOrRift) return 3;
		if (bandStage > 0 || (milestoneMask & 1) != 0) return 2;
		return 1;
	}

	public static int heatPercent(long tierOnlineTicks) {
		if (tierOnlineTicks <= 0L) return 0;
		if (tierOnlineTicks >= HEAT_RAMP_TICKS) return 100;
		return (int) (tierOnlineTicks * 100L / HEAT_RAMP_TICKS);
	}

	public static long intervalTicks(int tier, int heatPercent, int randomValue, boolean first) {
		if (tier <= 0) return Long.MAX_VALUE;
		if (first && tier == 1) return randomSeconds(60, 90, randomValue);
		int[] cold = {0, 300, 240, 180, 120, 90};
		int[] hot = {0, 180, 120, 90, 60, 45};
		int clampedTier = Math.clamp(tier, 1, 5);
		int heat = Math.clamp(heatPercent, 0, 100);
		int maximum = cold[clampedTier] - (cold[clampedTier] - hot[clampedTier]) * heat / 100;
		int minimum = hot[clampedTier];
		return randomSeconds(minimum, Math.max(minimum, maximum), randomValue);
	}

	public static long strongCooldownTicks(int tier, int randomValue) {
		return switch (Math.clamp(tier, 0, 5)) {
			case 3 -> randomSeconds(12 * 60, 18 * 60, randomValue);
			case 4 -> randomSeconds(8 * 60, 12 * 60, randomValue);
			case 5 -> randomSeconds(5 * 60, 8 * 60, randomValue);
			default -> 0L;
		};
	}

	private static long randomSeconds(int minimum, int maximum, int randomValue) {
		int seconds = minimum + Math.floorMod(randomValue, maximum - minimum + 1);
		return seconds * 20L;
	}
}
