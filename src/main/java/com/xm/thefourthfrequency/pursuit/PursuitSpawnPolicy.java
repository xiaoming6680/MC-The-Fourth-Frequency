package com.xm.thefourthfrequency.pursuit;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Produces initial spawn probes in the player's rear hemisphere. */
public final class PursuitSpawnPolicy {
	/**
	 * Widest angle, in degrees, a probe may sit off the straight-behind line.
	 *
	 * <p>Kept under the 87 degrees where {@link #outsideForwardHemisphere} starts rejecting, with
	 * enough margin that rounding a polar offset to whole blocks cannot push one over.</p>
	 */
	private static final double MAX_FAN_DEGREES = 70.0D;
	private static final double[] FAN_DISTANCES = {16.0D, 20.0D, 24.0D, 28.0D, 31.0D};

	private PursuitSpawnPolicy() {
	}

	/**
	 * Candidate columns to try, nearest-behind first.
	 *
	 * <p>The first six are the original hand-picked probes and stay first, so on open ground the
	 * corrector still arrives from the same places it always did. Everything after them is a
	 * fallback fan, and it exists because six columns is not enough: each one is only usable if
	 * that exact spot has a floor, and on a hillside, a shoreline, or inside a cave all six can
	 * miss at once. When they did, the chase had no spawn, and no spawn aborts the whole session -
	 * the player gets pulled into the mirror and pushed straight back out with nothing hunting
	 * them. Widening the fan keeps the rule the fiction cares about (behind the player, 15-32
	 * blocks, never in view) while making it very hard for the terrain to veto a chase.</p>
	 */
	public static List<Offset> hiddenOffsets(double lookX, double lookZ) {
		double length = Math.sqrt(lookX * lookX + lookZ * lookZ);
		double forwardX = length < 1.0e-5D ? 0.0D : lookX / length;
		double forwardZ = length < 1.0e-5D ? 1.0D : lookZ / length;
		double behindX = -forwardX;
		double behindZ = -forwardZ;
		double rightX = -forwardZ;
		double rightZ = forwardX;
		Set<Offset> unique = new LinkedHashSet<>();
		add(unique, behindX, behindZ, 22.0D, rightX, rightZ, 0.0D);
		add(unique, behindX, behindZ, 22.0D, rightX, rightZ, 10.0D);
		add(unique, behindX, behindZ, 22.0D, rightX, rightZ, -10.0D);
		add(unique, behindX, behindZ, 28.0D, rightX, rightZ, 7.0D);
		add(unique, behindX, behindZ, 28.0D, rightX, rightZ, -7.0D);
		add(unique, behindX, behindZ, 16.0D, rightX, rightZ, 0.0D);
		// Fanned outward in widening pairs so a failure walks away from straight-behind gradually
		// rather than jumping to the player's flank on the second try.
		for (double degrees = 15.0D; degrees <= MAX_FAN_DEGREES; degrees += 15.0D) {
			for (double distance : FAN_DISTANCES) {
				double radians = Math.toRadians(degrees);
				double along = distance * Math.cos(radians);
				double lateral = distance * Math.sin(radians);
				add(unique, behindX, behindZ, along, rightX, rightZ, lateral);
				add(unique, behindX, behindZ, along, rightX, rightZ, -lateral);
			}
		}
		return new ArrayList<>(unique);
	}

	public static boolean outsideForwardHemisphere(double lookX, double lookZ,
			double deltaX, double deltaZ) {
		double lookLength = Math.sqrt(lookX * lookX + lookZ * lookZ);
		double deltaLength = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);
		if (lookLength < 1.0e-5D || deltaLength < 1.0e-5D) return false;
		double dot = (lookX * deltaX + lookZ * deltaZ) / (lookLength * deltaLength);
		return dot < -0.05D;
	}

	private static void add(Set<Offset> offsets, double behindX, double behindZ, double distance,
			double rightX, double rightZ, double lateral) {
		int x = (int) Math.round(behindX * distance + rightX * lateral);
		int z = (int) Math.round(behindZ * distance + rightZ * lateral);
		if (x == 0 && z == 0) return;
		// Rounding to whole blocks can carry a polar probe outside the band the fiction promises,
		// so the band is enforced here rather than assumed at the call sites.
		double rounded = Math.sqrt((double) x * x + (double) z * z);
		if (rounded < 15.0D || rounded > 32.0D) return;
		offsets.add(new Offset(x, z));
	}

	public record Offset(int x, int z) {
	}
}
