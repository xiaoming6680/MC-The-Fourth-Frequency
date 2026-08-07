package com.xm.thefourthfrequency.ending;

/**
 * When the storm leaves melee range entirely, and for how long.
 *
 * <p>The second and third bodies fly. Not as a mood - the encounter already had that - but as a
 * period where the fight is a different fight: the interface climbs out of every swing, every head
 * and neck goes with it, and what is left is a bow, a crossbow or nothing. It comes back down, and
 * it comes back down on a clock the player can learn.
 *
 * <p><b>The ceiling on that is the whole design and it lives here.</b> A boss that spends most of
 * its time unreachable is not a harder fight, it is a longer one, and a table without a bow simply
 * stops playing. {@link #MAX_DUTY_CYCLE} is the promise - under forty percent of the encounter, at
 * every phase - and {@code WorldInterfaceSkyholdPolicyTest} asserts it against the actual window
 * rather than against a comment, so tuning the window cannot quietly break the promise.
 *
 * <p>Stateless, like {@link WorldInterfaceActionScheduler#VOLLEY_INTERVAL_TICKS}: the encounter's own
 * active-tick counter is the clock, so nothing has to be persisted, a restart resumes wherever the
 * counter says, and a replayed encounter climbs on exactly the same ticks.
 */
public final class WorldInterfaceSkyholdPolicy {
	/** Length of one climb-and-return cycle. */
	public static final int PERIOD_TICKS = 900;
	/**
	 * Ticks of that cycle spent away from the ground, transit included.
	 *
	 * <p>Thirteen seconds: long enough that a table has to actually change weapon and reposition
	 * rather than wait it out behind a pillar, short enough that being caught without arrows is a bad
	 * thirteen seconds and not a lost encounter.
	 */
	public static final int WINDOW_TICKS = 260;
	/** Ticks at each end of the window spent climbing or descending, so neither is a teleport. */
	public static final int TRANSIT_TICKS = 50;
	/** The contract: the interface is out of reach for strictly less than this share of the fight. */
	public static final double MAX_DUTY_CYCLE = 0.40D;
	/**
	 * Blocks added to the combat station at the top of the climb.
	 *
	 * <p>Has to clear a swing from anything a player can stand on, including the ten obsidian spikes,
	 * and has to stay inside the distance an arrow is worth firing. Twenty-six over a station that is
	 * already sixteen or twenty puts the underside of the body at forty-two or forty-six blocks.
	 */
	public static final double CEILING_LIFT = 26.0D;

	private WorldInterfaceSkyholdPolicy() {
	}

	/** Only the two flying forms. The first body is the one that has to be learnable on the ground. */
	public static boolean applies(WorldInterfaceStage stage) {
		return stage == WorldInterfaceStage.PHASE_2 || stage == WorldInterfaceStage.PHASE_3;
	}

	/** Share of a combat phase spent anywhere above the combat station, transit included. */
	public static double dutyCycle() {
		return WINDOW_TICKS / (double) PERIOD_TICKS;
	}

	/** Share spent at the ceiling itself, where nothing but a projectile reaches the interface. */
	public static double unreachableDutyCycle() {
		return Math.max(0, WINDOW_TICKS - 2 * TRANSIT_TICKS) / (double) PERIOD_TICKS;
	}

	/**
	 * Where in the cycle {@code activeTick} falls, measured from the start of the climb.
	 *
	 * <p>Negative while the interface is on station. The window is placed at the end of each period
	 * so that tick zero - the first tick of a phase, and the tick a morph hands over on - is always
	 * on the ground.
	 */
	public static long windowElapsed(long activeTick) {
		if (activeTick < 0L) throw new IllegalArgumentException("Active tick cannot be negative");
		return Math.floorMod(activeTick, (long) PERIOD_TICKS) - (PERIOD_TICKS - WINDOW_TICKS);
	}

	/** Whether this is the exact tick a climb begins, for the one-shot cue that announces it. */
	public static boolean isAscentTick(WorldInterfaceStage stage, long activeTick) {
		return applies(stage) && windowElapsed(activeTick) == 0L;
	}

	public static boolean aloft(WorldInterfaceStage stage, long activeTick) {
		return altitudeFraction(stage, activeTick) > 0.0D;
	}

	/**
	 * How far into the climb the body is, 0 on station and 1 at the ceiling.
	 *
	 * <p>Smoothstepped at both ends rather than linear, so the storm accelerates off its station and
	 * settles at the top instead of riding an elevator. The two transits are inside the window, which
	 * is what keeps {@link #dutyCycle} an honest measure of the time the fight is changed by this.
	 */
	public static double altitudeFraction(WorldInterfaceStage stage, long activeTick) {
		if (!applies(stage)) return 0.0D;
		long elapsed = windowElapsed(activeTick);
		if (elapsed < 0L || elapsed >= WINDOW_TICKS) return 0.0D;
		double progress;
		if (elapsed < TRANSIT_TICKS) {
			progress = elapsed / (double) TRANSIT_TICKS;
		} else if (elapsed >= WINDOW_TICKS - TRANSIT_TICKS) {
			progress = (WINDOW_TICKS - elapsed) / (double) TRANSIT_TICKS;
		} else {
			return 1.0D;
		}
		double clamped = Math.clamp(progress, 0.0D, 1.0D);
		return clamped * clamped * (3.0D - 2.0D * clamped);
	}

	/** Blocks to add to the combat station this tick. */
	public static double lift(WorldInterfaceStage stage, long activeTick) {
		return CEILING_LIFT * altitudeFraction(stage, activeTick);
	}
}
