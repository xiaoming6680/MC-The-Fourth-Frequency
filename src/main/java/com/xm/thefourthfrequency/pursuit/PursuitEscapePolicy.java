package com.xm.thefourthfrequency.pursuit;

/** Alternative authoritative escape routes in addition to surviving the full timer. */
public final class PursuitEscapePolicy {
	public static final double DISTANCE_ESCAPE_BLOCKS = 42.0D;
	public static final int DISTANCE_ESCAPE_TICKS = 5 * 20;
	public static final double HIDDEN_ESCAPE_MIN_BLOCKS = 18.0D;
	public static final int HIDDEN_ESCAPE_TICKS = 8 * 20;

	private PursuitEscapePolicy() {
	}

	public static Counters advance(Counters previous, double distance, boolean correctorHasLineOfSight) {
		int distanceTicks = distance >= DISTANCE_ESCAPE_BLOCKS
				? previous.distanceTicks + 1 : Math.max(0, previous.distanceTicks - 3);
		int hiddenTicks = distance >= HIDDEN_ESCAPE_MIN_BLOCKS && !correctorHasLineOfSight
				? previous.hiddenTicks + 1 : Math.max(0, previous.hiddenTicks - 4);
		return new Counters(distanceTicks, hiddenTicks);
	}

	public static boolean escaped(Counters counters) {
		return counters.distanceTicks >= DISTANCE_ESCAPE_TICKS
				|| counters.hiddenTicks >= HIDDEN_ESCAPE_TICKS;
	}

	public record Counters(int distanceTicks, int hiddenTicks) {
		public Counters {
			distanceTicks = Math.max(0, distanceTicks);
			hiddenTicks = Math.max(0, hiddenTicks);
		}

		public static Counters empty() {
			return new Counters(0, 0);
		}
	}
}
