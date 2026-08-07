package com.xm.thefourthfrequency.pursuit;

/** Alternative authoritative escape routes in addition to surviving the full timer. */
public final class PursuitEscapePolicy {
	public static final double DISTANCE_ESCAPE_BLOCKS = 42.0D;
	public static final int DISTANCE_ESCAPE_TICKS = 5 * 20;
	public static final double HIDDEN_ESCAPE_MIN_BLOCKS = 18.0D;
	public static final int HIDDEN_ESCAPE_TICKS = 8 * 20;

	/**
	 * How close the corrector has to get before escaping is a thing that can be done.
	 *
	 * <p>Both routes below describe a player who has broken away from something. Neither of them can
	 * tell that apart from a player who was simply never reached - and the encounter opens with
	 * exactly that, because the corrector spawns somewhere on a ring 25 to 42 blocks out. Standing
	 * still, a player met both conditions immediately: forty-two blocks is inside the spawn ring, and
	 * eighteen-without-line-of-sight describes almost any spawn behind cover. The pursuit announced
	 * that they had escaped before they had done anything at all.
	 *
	 * <p>So the counters do not start until contact has been made once. Deliberately distance only,
	 * not line of sight: in the open the corrector can see the player from its spawn ring, and
	 * "it can see me from thirty blocks away" is not being caught up with.
	 */
	public static final double CONTACT_BLOCKS = HIDDEN_ESCAPE_MIN_BLOCKS;

	private PursuitEscapePolicy() {
	}

	public static Counters advance(Counters previous, double distance, boolean correctorHasLineOfSight) {
		boolean contacted = previous.contacted() || distance <= CONTACT_BLOCKS;
		// Nothing to break away from yet. Held at zero rather than merely not incremented, so an
		// approach that stalls out and restarts does not resume a part-built escape.
		if (!contacted) return new Counters(0, 0, false);
		int distanceTicks = distance >= DISTANCE_ESCAPE_BLOCKS
				? previous.distanceTicks + 1 : Math.max(0, previous.distanceTicks - 3);
		int hiddenTicks = distance >= HIDDEN_ESCAPE_MIN_BLOCKS && !correctorHasLineOfSight
				? previous.hiddenTicks + 1 : Math.max(0, previous.hiddenTicks - 4);
		return new Counters(distanceTicks, hiddenTicks, true);
	}

	public static boolean escaped(Counters counters) {
		return counters.contacted()
				&& (counters.distanceTicks >= DISTANCE_ESCAPE_TICKS
						|| counters.hiddenTicks >= HIDDEN_ESCAPE_TICKS);
	}

	public record Counters(int distanceTicks, int hiddenTicks, boolean contacted) {
		public Counters {
			distanceTicks = Math.max(0, distanceTicks);
			hiddenTicks = Math.max(0, hiddenTicks);
		}

		public static Counters empty() {
			return new Counters(0, 0, false);
		}
	}
}
