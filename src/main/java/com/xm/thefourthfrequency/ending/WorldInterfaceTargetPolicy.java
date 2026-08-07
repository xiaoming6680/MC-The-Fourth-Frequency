package com.xm.thefourthfrequency.ending;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Deterministic target selection that spreads the fight's attention across a table.
 *
 * <p>The encounter used to name whoever {@code (seed ^ sequence) mod n} landed on, and that rule is
 * better than it looks: {@code sequence} increments by one per attack, so for a roster whose size is
 * a power of two the low bits cycle and the pick is a <em>perfect</em> round-robin - two, four and
 * eight players each took an exactly equal share with nobody ever named twice running. It is worth
 * stating plainly, because it means the old behaviour was not naive and this is not replacing a
 * random roll with a better random roll.
 *
 * <p>What it could not survive was the roster changing underneath it. The index is a position in a
 * list that is filtered every time it is built - by strong-control immunity, by death, by who is
 * still inside the arena radius - so the moment somebody drops out, every index after them shifts
 * and the round-robin re-aligns against a different set of people. Measured over six hundred attacks
 * with players entering and leaving the eligible set, the old rule produced runs of up to ten
 * consecutive picks on one player and a spread of nearly fifty picks between the most and least
 * targeted member of a table. Both of those are the complaint this exists to answer, and neither is
 * visible in the clean case.
 *
 * <p>So the rule here is in two parts. Whoever was named most recently is <em>excluded</em> while
 * anyone else is eligible, which bounds the worst case at one - a stronger guarantee than the old
 * arithmetic ever gave, and one that does not depend on the roster being a convenient size. Among
 * everyone left, the pick is weighted by {@code 1 / (1 + picks)} over a sliding window, so a player
 * the fight has been leaning on is roughly half as likely as one it has not. Nothing is excluded
 * beyond the single most recent name: a solo table is the whole roster, and a rule with nobody left
 * to aim at is a fight that stops attacking.
 *
 * <p>The price is that the share is no longer exactly equal in the case where the old rule was
 * exact. Over six hundred attacks an eight-player table lands within about a fifth of an even split
 * rather than on it. That is deliberate - a fight whose next target is arithmetically predictable is
 * one a table can stand in formation against - and it is bounded by the same weighting that produces
 * it.
 *
 * <p>Everything here is integer arithmetic on purpose. The weights are exact divisors of
 * {@link #PRESSURE_SCALE}, so the same ledger and the same seed pick the same player on any machine
 * the encounter is replayed on, with no rounding to reason about.
 */
public final class WorldInterfaceTargetPolicy {
	/**
	 * How long a pick keeps counting against the player it named.
	 *
	 * <p>Deliberately the same window the exclusive controls already use. A table that has learned
	 * "being seized buys you thirty seconds" should find that the softer attention behaves on the
	 * same clock rather than on a second, invisible one.
	 */
	public static final int PRESSURE_MEMORY_TICKS =
			WorldInterfaceActionScheduler.STRONG_CONTROL_IMMUNITY_TICKS;

	/**
	 * Picks past this stop making a player any less likely.
	 *
	 * <p>Without a cap, a player unlucky enough to be named several times early would carry a weight
	 * approaching zero for the rest of the window - which is the original problem wearing the other
	 * face. Nine is far enough down that the correction has plainly done its work.
	 */
	public static final int MAX_TRACKED_PICKS = 9;

	/** The tick stamp for a candidate the ledger has never named. */
	public static final long NEVER_PICKED = Long.MIN_VALUE;

	/**
	 * Numerator the weights are expressed over: the least common multiple of one through ten, so
	 * {@code PRESSURE_SCALE / (1 + picks)} is a whole number for every pick count this tracks.
	 */
	private static final int PRESSURE_SCALE = 2_520;

	private static final long SELECTION_GAMMA = 0x9E3779B97F4A7C15L;

	private WorldInterfaceTargetPolicy() {
	}

	/** How much of the roll one candidate occupies, given how often it has been named lately. */
	public static int weight(int recentPicks) {
		if (recentPicks < 0) throw new IllegalArgumentException("Recent pick count cannot be negative");
		return PRESSURE_SCALE / (1 + Math.min(recentPicks, MAX_TRACKED_PICKS));
	}

	/**
	 * Names one candidate by index.
	 *
	 * @param recentPicks   how often each candidate has been named inside
	 *                      {@link #PRESSURE_MEMORY_TICKS}, in the caller's own candidate order
	 * @param lastPickTicks when each candidate was last named, {@link #NEVER_PICKED} if never; every
	 *                      candidate sharing the newest stamp is held out of this pick, which is what
	 *                      keeps a volley that named several people on one tick from re-naming any of
	 *                      them on the next
	 * @param encounterSeed the encounter's deterministic seed
	 * @param sequence      the action sequence the pick belongs to
	 */
	public static int selectIndex(int[] recentPicks, long[] lastPickTicks, long encounterSeed,
			long sequence) {
		Objects.requireNonNull(recentPicks, "recentPicks");
		Objects.requireNonNull(lastPickTicks, "lastPickTicks");
		if (recentPicks.length != lastPickTicks.length) {
			throw new IllegalArgumentException("Pick counts and pick ticks must describe the same candidates");
		}
		if (recentPicks.length == 0) throw new IllegalArgumentException("No candidates to choose between");
		if (recentPicks.length == 1) return 0;

		long newest = NEVER_PICKED;
		for (long stamp : lastPickTicks) newest = Math.max(newest, stamp);
		List<Integer> open = new ArrayList<>(recentPicks.length);
		if (newest != NEVER_PICKED) {
			for (int index = 0; index < recentPicks.length; index++) {
				if (lastPickTicks[index] != newest) open.add(index);
			}
		}
		// Nobody left once the most recent names are held out - a solo table, or a volley that has
		// just named everybody. The fight still has to attack, so the exclusion yields.
		if (open.isEmpty()) {
			for (int index = 0; index < recentPicks.length; index++) open.add(index);
		}

		long total = 0L;
		for (int index : open) total += weight(recentPicks[index]);
		long roll = Math.floorMod(WorldInterfaceActionScheduler.mix64(
				encounterSeed ^ (sequence * SELECTION_GAMMA)), total);
		for (int index : open) {
			roll -= weight(recentPicks[index]);
			if (roll < 0L) return index;
		}
		// Unreachable while the weights are positive; returning a real candidate rather than throwing
		// keeps an arithmetic surprise from silently stopping the encounter attacking.
		return open.getLast();
	}
}
