package com.xm.thefourthfrequency.pursuit;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Produces initial spawn probes in the player's rear hemisphere. */
public final class PursuitSpawnPolicy {
	private PursuitSpawnPolicy() {
	}

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
		if (x != 0 || z != 0) offsets.add(new Offset(x, z));
	}

	public record Offset(int x, int z) {
	}
}
