package com.xm.thefourthfrequency.ending;

/** Pure numeric and resolution rules for the world-interface encounter. */
public final class WorldInterfacePolicy {
	public static final int MIN_ROSTER_SIZE = 1;
	public static final int MAX_ROSTER_SIZE = 8;
	public static final int TOTAL_ANCHORS = 10;
	public static final int COLLAPSE_DURATION_TICKS = 12_000;
	public static final int PENALTY_TICKS_PER_DESTROYED_ANCHOR = 600;
	public static final int MAX_ANCHOR_PENALTY_TICKS = 6_000;
	public static final int MAX_PERMANENT_TERRAIN_EDITS = 2_048;
	public static final int MAX_TERRAIN_EDITS_PER_TICK = 8;

	private static final double HEALTH_PER_PLAYER = 600.0D;
	private static final double DAMAGE_MULTIPLIER_LOSS_PER_ANCHOR = 0.09D;
	private static final double HEALING_PER_SECOND_PER_ANCHOR = 0.0001D;
	private static final double BASE_MOVEMENT_MULTIPLIER = 0.55D;
	private static final double MOVEMENT_GAIN_PER_ANCHOR = 0.045D;
	private static final double BASE_ATTACK_COOLDOWN_MULTIPLIER = 1.50D;
	private static final double ATTACK_COOLDOWN_LOSS_PER_ANCHOR = 0.075D;

	private WorldInterfacePolicy() {
	}

	public static double maxHealth(int frozenRosterSize) {
		requireRosterSize(frozenRosterSize);
		return HEALTH_PER_PLAYER * frozenRosterSize;
	}

	/** Multiplier applied to incoming boss damage after {@code destroyedAnchors} authoritative anchors fall. */
	public static double damageTakenMultiplier(int destroyedAnchors) {
		requireDestroyedAnchors(destroyedAnchors);
		return 1.0D - DAMAGE_MULTIPLIER_LOSS_PER_ANCHOR * destroyedAnchors;
	}

	public static double adjustedIncomingDamage(double rawDamage, int destroyedAnchors) {
		if (!Double.isFinite(rawDamage) || rawDamage < 0.0D) {
			throw new IllegalArgumentException("Raw damage must be finite and non-negative");
		}
		return rawDamage * damageTakenMultiplier(destroyedAnchors);
	}

	/** Healing per server tick; twenty invocations equal the specified per-second rule. */
	public static double healingPerTick(double maximumHealth, int aliveAnchors) {
		if (!Double.isFinite(maximumHealth) || maximumHealth <= 0.0D) {
			throw new IllegalArgumentException("Maximum health must be finite and positive");
		}
		requireAliveAnchors(aliveAnchors);
		return maximumHealth * HEALING_PER_SECOND_PER_ANCHOR * aliveAnchors / 20.0D;
	}

	public static double movementMultiplier(int destroyedAnchors) {
		requireDestroyedAnchors(destroyedAnchors);
		return BASE_MOVEMENT_MULTIPLIER + MOVEMENT_GAIN_PER_ANCHOR * destroyedAnchors;
	}

	public static double attackCooldownMultiplier(int destroyedAnchors) {
		requireDestroyedAnchors(destroyedAnchors);
		return BASE_ATTACK_COOLDOWN_MULTIPLIER - ATTACK_COOLDOWN_LOSS_PER_ANCHOR * destroyedAnchors;
	}

	public static int anchorPenaltyTicks(int destroyedAnchors) {
		requireDestroyedAnchors(destroyedAnchors);
		return Math.min(MAX_ANCHOR_PENALTY_TICKS,
				destroyedAnchors * PENALTY_TICKS_PER_DESTROYED_ANCHOR);
	}

	public static long effectiveCollapseTicks(long elapsedTicks, int destroyedAnchors) {
		if (elapsedTicks < 0L) throw new IllegalArgumentException("Elapsed ticks cannot be negative");
		long penalty = anchorPenaltyTicks(destroyedAnchors);
		return elapsedTicks > Long.MAX_VALUE - penalty ? Long.MAX_VALUE : elapsedTicks + penalty;
	}

	/** Returns collapse progress clamped to the HUD domain [0, 1]. */
	public static double collapseProgress(long elapsedTicks, int destroyedAnchors) {
		return Math.min(1.0D,
				effectiveCollapseTicks(elapsedTicks, destroyedAnchors) / (double) COLLAPSE_DURATION_TICKS);
	}

	/**
	 * Failure-material presentation is a separate resolution clock. The active collapse timer and
	 * its anchor penalty must never leak into normal combat or the successful ending.
	 */
	public static float failurePresentationProgress(WorldInterfaceStage stage, long resolutionTick,
			long gameTime, int durationTicks) {
		if (stage == null) throw new IllegalArgumentException("Stage cannot be null");
		if (durationTicks <= 0) throw new IllegalArgumentException("Presentation duration must be positive");
		if (stage != WorldInterfaceStage.FAILURE_RESOLUTION || resolutionTick < 0L
				|| gameTime <= resolutionTick) return 0.0F;
		long age = gameTime - resolutionTick;
		return Math.min(age, durationTicks) / (float) durationTicks;
	}

	public static int remainingCollapseTicks(long elapsedTicks, int destroyedAnchors) {
		long remaining = COLLAPSE_DURATION_TICKS - effectiveCollapseTicks(elapsedTicks, destroyedAnchors);
		return (int) Math.max(0L, remaining);
	}

	public static boolean hasTimedOut(long elapsedTicks, int destroyedAnchors) {
		return effectiveCollapseTicks(elapsedTicks, destroyedAnchors) >= COLLAPSE_DURATION_TICKS;
	}

	/** The server calls this once per running tick; all-offline frozen rosters pause the timer. */
	public static boolean timerAdvances(int frozenRosterSize, int onlineFrozenMembers) {
		requireRosterSize(frozenRosterSize);
		if (onlineFrozenMembers < 0 || onlineFrozenMembers > frozenRosterSize) {
			throw new IllegalArgumentException("Online frozen-member count is outside the roster");
		}
		return onlineFrozenMembers > 0;
	}

	/**
	 * Failure is evaluated before lethal damage on the same tick. A kill is successful only while
	 * effective progress is strictly below 100%.
	 */
	public static TickVerdict resolveTick(long elapsedTicks, int destroyedAnchors, boolean lethalDamage) {
		if (hasTimedOut(elapsedTicks, destroyedAnchors)) return TickVerdict.FAILURE;
		return lethalDamage ? TickVerdict.SUCCESS : TickVerdict.ONGOING;
	}

	/** Number of real disconnect targets selected by forced eviction. */
	public static int forcedEvictionTargetCount(int islandParticipantCount) {
		if (islandParticipantCount < 0 || islandParticipantCount > MAX_ROSTER_SIZE) {
			throw new IllegalArgumentException("Island participant count must be between 0 and 8");
		}
		if (islandParticipantCount < 3) return 0;
		return (islandParticipantCount * 3 + 9) / 10;
	}

	public static int terrainEditBudgetThisTick(int permanentEditsSoFar) {
		if (permanentEditsSoFar < 0) {
			throw new IllegalArgumentException("Permanent edit count cannot be negative");
		}
		return Math.min(MAX_TERRAIN_EDITS_PER_TICK,
				Math.max(0, MAX_PERMANENT_TERRAIN_EDITS - permanentEditsSoFar));
	}

	private static void requireRosterSize(int frozenRosterSize) {
		if (frozenRosterSize < MIN_ROSTER_SIZE || frozenRosterSize > MAX_ROSTER_SIZE) {
			throw new IllegalArgumentException("Frozen roster size must be between 1 and 8");
		}
	}

	private static void requireDestroyedAnchors(int destroyedAnchors) {
		if (destroyedAnchors < 0 || destroyedAnchors > TOTAL_ANCHORS) {
			throw new IllegalArgumentException("Destroyed anchor count must be between 0 and 10");
		}
	}

	private static void requireAliveAnchors(int aliveAnchors) {
		if (aliveAnchors < 0 || aliveAnchors > TOTAL_ANCHORS) {
			throw new IllegalArgumentException("Alive anchor count must be between 0 and 10");
		}
	}

	public enum TickVerdict {
		ONGOING,
		SUCCESS,
		FAILURE
	}
}
