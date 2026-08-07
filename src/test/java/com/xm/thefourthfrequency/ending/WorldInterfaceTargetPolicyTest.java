package com.xm.thefourthfrequency.ending;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the two properties the multiplayer target rule exists to provide, plus the determinism the
 * rest of the encounter is built on.
 *
 * <p>The comparisons here are against the rule this replaced - {@code (seed ^ sequence) mod n} - and
 * not against an abstract ideal, because the old rule was genuinely good in the clean case and the
 * whole argument for changing it is what happens when the eligible set churns. A test that only
 * measured the clean case would say the change was a regression, which for the share it is.
 */
class WorldInterfaceTargetPolicyTest {
	private static final long SEED = 0x4F52_4947_494EL;
	/** Attacks simulated per table; a ten-minute fight is well under this at every phase. */
	private static final int ATTACKS = 600;
	/** Roughly a second-phase cadence, so the memory window holds a handful of picks at a time. */
	private static final int ATTACK_INTERVAL_TICKS = 90;

	@Test
	void weightFallsWithRecentPicksAndFlattensAtTheCap() {
		assertTrue(WorldInterfaceTargetPolicy.weight(0) > WorldInterfaceTargetPolicy.weight(1));
		for (int picks = 0; picks < WorldInterfaceTargetPolicy.MAX_TRACKED_PICKS; picks++) {
			assertTrue(WorldInterfaceTargetPolicy.weight(picks)
							> WorldInterfaceTargetPolicy.weight(picks + 1),
					"weight must fall with every tracked pick");
		}
		// A player already at the cap cannot be pushed further down, however long the fight runs.
		assertEquals(WorldInterfaceTargetPolicy.weight(WorldInterfaceTargetPolicy.MAX_TRACKED_PICKS),
				WorldInterfaceTargetPolicy.weight(WorldInterfaceTargetPolicy.MAX_TRACKED_PICKS + 50));
		// Half as likely after one pick is the figure the class documents; it is load-bearing for how
		// quickly the correction acts, so it is pinned rather than left to the arithmetic.
		assertEquals(WorldInterfaceTargetPolicy.weight(0), WorldInterfaceTargetPolicy.weight(1) * 2);
		assertThrows(IllegalArgumentException.class, () -> WorldInterfaceTargetPolicy.weight(-1));
	}

	@Test
	void selectionIsDeterministicAndAlwaysNamesARealCandidate() {
		int[] picks = {0, 3, 1, 7};
		long[] stamps = {WorldInterfaceTargetPolicy.NEVER_PICKED, 40L, 120L, 10L};
		for (long sequence = 0L; sequence < 200L; sequence++) {
			int chosen = WorldInterfaceTargetPolicy.selectIndex(picks, stamps, SEED, sequence);
			assertEquals(chosen, WorldInterfaceTargetPolicy.selectIndex(picks, stamps, SEED, sequence),
					"the same ledger and sequence must always name the same player");
			assertTrue(chosen >= 0 && chosen < picks.length);
			// Index 2 holds the newest stamp, so it is held out while anyone else is available.
			assertTrue(chosen != 2, "the most recently named player must not be named again");
		}
	}

	@Test
	void aSoloTableIsStillAlwaysTheTarget() {
		assertEquals(0, WorldInterfaceTargetPolicy.selectIndex(new int[]{6}, new long[]{500L}, SEED, 3L));
		// And a table where everyone shares the newest stamp - a volley that named them all on one
		// tick - still yields a target rather than leaving the encounter with nothing to attack.
		int chosen = WorldInterfaceTargetPolicy.selectIndex(
				new int[]{2, 2, 2}, new long[]{80L, 80L, 80L}, SEED, 9L);
		assertTrue(chosen >= 0 && chosen < 3);
	}

	@Test
	void rejectsMismatchedOrEmptyLedgers() {
		assertThrows(IllegalArgumentException.class, () -> WorldInterfaceTargetPolicy.selectIndex(
				new int[]{0, 1}, new long[]{0L}, SEED, 0L));
		assertThrows(IllegalArgumentException.class, () -> WorldInterfaceTargetPolicy.selectIndex(
				new int[0], new long[0], SEED, 0L));
	}

	/**
	 * The guarantee the old rule could not make: nobody is named twice running while somebody else
	 * could have been.
	 *
	 * <p>Checked with the eligible set churning, which is the case that matters - strong-control
	 * immunity, deaths and the arena radius all rebuild that list between attacks, and it is exactly
	 * there that an index-based round-robin loses its alignment.
	 */
	@Test
	void noPlayerIsNamedTwiceRunningWhileAnyoneElseIsEligible() {
		for (int roster : new int[]{2, 3, 4, 8}) {
			Run churned = simulate(roster, true, true);
			assertEquals(1, churned.longestRunWithAlternativesAvailable, "roster " + roster
					+ ": a player was named twice running while the fight had somebody else to name");
			Run clean = simulate(roster, false, true);
			assertEquals(1, clean.longestRunWithAlternativesAvailable, "roster " + roster);
		}
	}

	/**
	 * Under a churning eligible set the new rule spreads attention strictly better than the old one,
	 * on both measures a player actually feels: how long a streak can get, and how uneven the totals
	 * end up.
	 */
	@Test
	void churnedRostersAreSpreadBetterThanTheRuleThisReplaced() {
		for (int roster : new int[]{2, 3, 4, 8}) {
			Run policy = simulate(roster, true, true);
			Run legacy = simulate(roster, true, false);
			assertTrue(policy.longestRun <= legacy.longestRun, "roster " + roster
					+ ": longest streak " + policy.longestRun + " is not better than the old rule's "
					+ legacy.longestRun);
			assertTrue(policy.spread <= legacy.spread, "roster " + roster
					+ ": share spread " + policy.spread + " is not better than the old rule's "
					+ legacy.spread);
		}
	}

	/**
	 * And on a stable roster it does not run away, even though the old rule was exact there.
	 *
	 * <p>This is the cost the class documents, held to a number so it cannot drift. The bound is
	 * deliberately wide: the point is that nobody is quietly carrying a third more of the fight than
	 * the person next to them, not that the split is even - an even split is the predictability this
	 * rule is trying not to have.
	 */
	@Test
	void stableRostersStayWithinAThirdOfAnEvenShare() {
		for (int roster : new int[]{2, 3, 4, 8}) {
			Run policy = simulate(roster, false, true);
			int even = ATTACKS / roster;
			assertTrue(policy.spread <= even / 3, "roster " + roster + ": share spread "
					+ policy.spread + " is too wide against an even split of " + even);
		}
	}

	private record Run(int longestRun, int longestRunWithAlternativesAvailable, int spread) {
	}

	/**
	 * Replays the scheduler's selection loop over a table.
	 *
	 * @param churn      whether players drop out of the eligible set between attacks, the way strong
	 *                   control immunity, death and the arena radius make them
	 * @param usePolicy  the rule under test, or the {@code (seed ^ sequence) mod n} it replaced
	 */
	private static Run simulate(int roster, boolean churn, boolean usePolicy) {
		List<Deque<Long>> ledger = new ArrayList<>();
		for (int player = 0; player < roster; player++) ledger.add(new ArrayDeque<>());
		int[] hits = new int[roster];
		int longestRun = 0;
		int longestContestedRun = 0;
		int run = 0;
		int last = -1;
		long tick = 0L;
		for (long sequence = 0L; sequence < ATTACKS; sequence++) {
			tick += ATTACK_INTERVAL_TICKS;
			List<Integer> eligible = new ArrayList<>(roster);
			for (int player = 0; player < roster; player++) {
				if (!churn || !dropped(sequence, player)) eligible.add(player);
			}
			if (eligible.isEmpty()) continue;

			int chosen;
			if (usePolicy) {
				int[] picks = new int[eligible.size()];
				long[] stamps = new long[eligible.size()];
				for (int index = 0; index < eligible.size(); index++) {
					Deque<Long> history = ledger.get(eligible.get(index));
					while (!history.isEmpty()
							&& history.peekFirst() < tick - WorldInterfaceTargetPolicy.PRESSURE_MEMORY_TICKS) {
						history.pollFirst();
					}
					picks[index] = history.size();
					Long newest = history.peekLast();
					stamps[index] = newest == null ? WorldInterfaceTargetPolicy.NEVER_PICKED : newest;
				}
				chosen = eligible.get(WorldInterfaceTargetPolicy.selectIndex(picks, stamps, SEED, sequence));
			} else {
				chosen = eligible.get(Math.floorMod((int) (SEED ^ sequence), eligible.size()));
			}

			ledger.get(chosen).addLast(tick);
			hits[chosen]++;
			if (chosen == last) run++;
			else {
				run = 1;
				last = chosen;
			}
			longestRun = Math.max(longestRun, run);
			// A repeat only counts against the rule when the fight actually had somebody else to name.
			if (eligible.size() > 1) longestContestedRun = Math.max(longestContestedRun, run);
		}
		int most = Arrays.stream(hits).max().orElse(0);
		int fewest = Arrays.stream(hits).min().orElse(0);
		return new Run(longestRun, longestContestedRun, most - fewest);
	}

	/** A deterministic stand-in for immunity, death and the arena radius thinning the roster. */
	private static boolean dropped(long sequence, int player) {
		return Math.floorMod(WorldInterfaceActionScheduler.mix64(
				SEED ^ (sequence * 31L + player)) >>> 13, 5L) == 0L;
	}
}
