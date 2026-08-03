package com.xm.thefourthfrequency.ending;

/** Pure numeric and resolution rules for the world-interface encounter. */
public final class WorldInterfacePolicy {
	public static final int MIN_ROSTER_SIZE = 1;
	public static final int MAX_ROSTER_SIZE = 8;
	public static final int TOTAL_ANCHORS = 10;
	/**
	 * Six minutes, and the only clock the fight runs on.
	 *
	 * <p>Cutting an anchor used to spend a slice of this, which made the one action the encounter
	 * is built around read as a cost: the reward for opening the interface up was less time to use
	 * it in, and a roster that cut all ten had halved the fight before landing a hit. The deadline
	 * is now fixed, and what an anchor buys is stated in the one place the player is already
	 * looking - the gauge flares when the interface stops resisting damage as hard.</p>
	 */
	public static final int COLLAPSE_DURATION_TICKS = 7_200;
	public static final int MAX_PERMANENT_TERRAIN_EDITS = 8_192;
	public static final int MAX_TERRAIN_EDITS_PER_TICK = 32;

	private static final double HEALTH_PER_PLAYER = 600.0D;
	/**
	 * How much of the interface's damage resistance each fallen anchor takes with it.
	 *
	 * <p>At nine percent, tearing down all ten left the interface taking a tenth of every hit -
	 * a ninety percent reduction, which made the whole board of anchors an argument nobody could
	 * win by pulling. Five percent bottoms out at half damage: still a real wall, still worth the
	 * trip out to break them, and no longer a number that decides the fight on its own.</p>
	 */
	private static final double DAMAGE_MULTIPLIER_LOSS_PER_ANCHOR = 0.05D;
	/**
	 * Fraction of maximum health each surviving anchor returns per second.
	 *
	 * <p>Raised: at a hundredth of a percent the healing was arithmetically real and practically
	 * invisible, so leaving the anchors up cost nothing a table could feel. It has to be a rate the
	 * damage log argues with, or "break them or don't" is not a decision.</p>
	 */
	private static final double HEALING_PER_SECOND_PER_ANCHOR = 0.00025D;
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

	/** Returns collapse progress clamped to the HUD domain [0, 1]. */
	public static double collapseProgress(long elapsedTicks) {
		if (elapsedTicks < 0L) throw new IllegalArgumentException("Elapsed ticks cannot be negative");
		return Math.min(1.0D, elapsedTicks / (double) COLLAPSE_DURATION_TICKS);
	}

	/** Collapse fraction below which combat shows no erosion at all. */
	public static final double EROSION_START_COLLAPSE = 0.40D;
	/**
	 * How far erosion may go while the fight is still winnable. The End has to stay readable
	 * enough to fight in, so combat never approaches the full missing-texture wash that the
	 * failure resolution ends on.
	 */
	public static final float COMBAT_EROSION_CEILING = 0.55F;

	/**
	 * How far the world has visibly stopped being able to describe itself, in [0, 1].
	 *
	 * <p>This used to key off the failure resolution clock alone, which meant the whole fight
	 * rendered at exactly zero and only a lost encounter ever showed the erosion - for six seconds,
	 * after the outcome was already decided. Winning players never saw it at all. Driving it from
	 * the collapse timer instead turns the countdown from a number into something visible: the
	 * island degrades as the deadline approaches, which is the pressure the fight was missing.</p>
	 *
	 * <p>Erosion holds at zero until {@link #EROSION_START_COLLAPSE} so the opening minutes stay
	 * clean, then accelerates quadratically to {@link #COMBAT_EROSION_CEILING}. A lost fight
	 * continues from that ceiling to a full wash across the resolution clock; a won fight returns
	 * to zero on the spot, so cutting the interface visibly restores the world's materials.</p>
	 */
	public static float presentationErosionProgress(WorldInterfaceStage stage, long elapsedTicks,
			long resolutionTick, long gameTime, int durationTicks) {
		if (stage == null) throw new IllegalArgumentException("Stage cannot be null");
		if (durationTicks <= 0) throw new IllegalArgumentException("Presentation duration must be positive");
		if (stage == WorldInterfaceStage.FAILURE_RESOLUTION) {
			if (resolutionTick < 0L || gameTime <= resolutionTick) return COMBAT_EROSION_CEILING;
			long age = gameTime - resolutionTick;
			float completion = Math.min(age, durationTicks) / (float) durationTicks;
			return COMBAT_EROSION_CEILING + (1.0F - COMBAT_EROSION_CEILING) * completion;
		}
		if (!stage.isCombat()) return 0.0F;
		double collapse = collapseProgress(Math.max(0L, elapsedTicks));
		if (collapse <= EROSION_START_COLLAPSE) return 0.0F;
		double advance = (collapse - EROSION_START_COLLAPSE) / (1.0D - EROSION_START_COLLAPSE);
		return (float) (COMBAT_EROSION_CEILING * advance * advance);
	}

	public static int remainingCollapseTicks(long elapsedTicks) {
		if (elapsedTicks < 0L) throw new IllegalArgumentException("Elapsed ticks cannot be negative");
		return (int) Math.max(0L, COLLAPSE_DURATION_TICKS - elapsedTicks);
	}

	public static boolean hasTimedOut(long elapsedTicks) {
		if (elapsedTicks < 0L) throw new IllegalArgumentException("Elapsed ticks cannot be negative");
		return elapsedTicks >= COLLAPSE_DURATION_TICKS;
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
	 * the clock is strictly below 100%.
	 */
	public static TickVerdict resolveTick(long elapsedTicks, boolean lethalDamage) {
		if (hasTimedOut(elapsedTicks)) return TickVerdict.FAILURE;
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

	/**
	 * Horizontal reach of the failure erosion, for both the client's render-time replacement and the
	 * server's durable commit.
	 *
	 * <p>These were two different numbers - a hundred and sixty blocks of rendering over forty
	 * blocks of committed world - so the moment the encounter cleared and the render stopped, the
	 * outer four fifths of the damage evaporated and the island visibly healed itself. What a losing
	 * table watched spread has to be what a losing table is left with, which means one radius.</p>
	 *
	 * <p>A hundred and sixty is what the render always showed. The commit reaches it by running
	 * across the whole fight instead of the resolution, and by only ever touching end stone.</p>
	 */
	public static final int EROSION_RADIUS_BLOCKS = 160;
	/** Surface layers the erosion reaches down each column. */
	public static final int EROSION_DEPTH = 6;

	/**
	 * The failure erosion's per-position threshold, shared by the client's render-time replacement
	 * and the server's durable commit.
	 *
	 * <p>Both sides have to agree exactly. The client has been showing this erosion since the
	 * collapse timer started moving; when the loss is finally locked, the server writes the same
	 * set of positions for real. If the two used different hashes the world would visibly rearrange
	 * itself at the moment of failure, and the damage a player watched spread would not be the
	 * damage they were left with.</p>
	 */
	public static float erosionThreshold(long value) {
		long mixed = value;
		mixed ^= mixed >>> 33;
		mixed *= 0xff51afd7ed558ccdL;
		mixed ^= mixed >>> 33;
		mixed *= 0xc4ceb9fe1a85ec53L;
		mixed ^= mixed >>> 33;
		return (mixed >>> 40) / (float) 0xFFFFFF;
	}

	/** Whether a block position is eroded at the given progress, for one encounter. */
	public static boolean erodesAt(java.util.UUID encounterId, long packedPosition, float progress) {
		if (encounterId == null) return false;
		return erosionThreshold(encounterId.getMostSignificantBits()
				^ encounterId.getLeastSignificantBits() ^ packedPosition) <= progress;
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
