package com.xm.thefourthfrequency.pursuit;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Produces initial spawn probes on a full ring around the player, in a random bearing order. */
public final class PursuitSpawnPolicy {
	/**
	 * Tried nearest first. A near ring is both the more frightening arrival and the more likely one
	 * to land on terrain the mirror has already copied, so walking outward is the right order for
	 * both reasons at once.
	 *
	 * <p>Nearest-first also carries the band past the streamed window's guarantee. The initial
	 * snapshot is {@link PursuitStreamWindow#RADIUS} chunks around the player's own chunk, so the
	 * copied radius is 32 blocks in the worst case - a player standing on a chunk edge - and about 47
	 * in the best. The outer rings can therefore probe columns that are still void, where no floor is
	 * found and the probe is simply skipped. That degrades rather than fails: the two inner rings sit
	 * inside the guarantee at any position in the chunk, and supply 24 candidate columns on their
	 * own.</p>
	 */
	private static final double[] RING_DISTANCES = {26.0D, 30.0D, 34.0D, 38.0D, 41.0D};
	/** Thirty degrees apart, which is about thirteen blocks of separation on the innermost ring. */
	private static final int BEARINGS_PER_RING = 12;
	private static final double MIN_DISTANCE = 25.0D;
	private static final double MAX_DISTANCE = 42.0D;

	private PursuitSpawnPolicy() {
	}

	/**
	 * Candidate columns to try, nearest ring first and in a randomly rotated bearing order.
	 *
	 * <p>The corrector used to be confined to the player's rear hemisphere so that it was never seen
	 * arriving. It is no longer: a ring probe may land dead ahead, in full view, and the player may
	 * watch it appear. That is the point. A thing that is only ever behind you is a thing you can
	 * reason about by turning around, and the whole rear-hemisphere rule quietly taught players that
	 * the direction they were already facing was safe. Nowhere is.</p>
	 *
	 * <p>Each ring starts at a uniformly random bearing, so the first column tried is equally likely
	 * to be in front as behind, and then steps around the circle. Stepping is deliberate rather than
	 * shuffled: when a column has no floor, its neighbour usually does, so a failure drifts a few
	 * degrees instead of jumping to the opposite side of the player.</p>
	 *
	 * @param seed drawn per spawn attempt, so a corrector lost mid-chase comes back from somewhere
	 *		else rather than from the column it was standing in
	 */
	public static List<Offset> spawnOffsets(long seed) {
		Set<Offset> unique = new LinkedHashSet<>();
		double step = 360.0D / BEARINGS_PER_RING;
		long state = seed;
		for (double distance : RING_DISTANCES) {
			state += GOLDEN_GAMMA;
			double rotation = unitInterval(mix(state)) * 360.0D;
			for (int index = 0; index < BEARINGS_PER_RING; index++) {
				add(unique, Math.toRadians(rotation + index * step), distance);
			}
		}
		return new ArrayList<>(unique);
	}

	/**
	 * SplitMix64, rather than handing the seed straight to {@link java.util.Random}.
	 *
	 * <p>That class seeds a 48-bit LCG with almost no mixing, so neighbouring seeds hand back nearly
	 * the same first draw - feed it 0, 1, 2 and every ring starts at practically the same bearing.
	 * The live caller passes a well-distributed value, but a policy whose fairness depends on the
	 * quality of its caller's seed is one bad call site away from silently spawning on one side of
	 * the player again. Mixing here makes the bearing uniform for any seed at all.</p>
	 */
	private static final long GOLDEN_GAMMA = 0x9E3779B97F4A7C15L;

	private static long mix(long value) {
		long z = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
		z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
		return z ^ (z >>> 31);
	}

	/** The top 53 bits as a double in {@code [0, 1)}. */
	private static double unitInterval(long value) {
		return (value >>> 11) * 0x1.0p-53;
	}

	private static void add(Set<Offset> offsets, double radians, double distance) {
		int x = (int) Math.round(Math.sin(radians) * distance);
		int z = (int) Math.round(Math.cos(radians) * distance);
		if (x == 0 && z == 0) return;
		// Rounding to whole blocks can carry a polar probe outside the band the fiction promises,
		// so the band is enforced here rather than assumed at the call sites.
		double rounded = Math.sqrt((double) x * x + (double) z * z);
		if (rounded < MIN_DISTANCE || rounded > MAX_DISTANCE) return;
		offsets.add(new Offset(x, z));
	}

	public record Offset(int x, int z) {
	}
}
