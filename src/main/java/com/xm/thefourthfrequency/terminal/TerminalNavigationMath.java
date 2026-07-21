package com.xm.thefourthfrequency.terminal;

public final class TerminalNavigationMath {
	private TerminalNavigationMath() { }

	public static boolean navigable(int targetKind, boolean toolsDisabled, boolean located, boolean sameDimension) {
		return targetKind > 0 && !toolsDisabled && located && sameDimension;
	}

	public static double northNeedleDegrees(float playerYaw) {
		return wrapDegrees(180.0D - playerYaw);
	}

	public static double targetNeedleDegrees(int dx, int dz, float playerYaw) {
		double targetYaw = Math.toDegrees(Math.atan2(-dx, dz));
		return wrapDegrees(targetYaw - playerYaw);
	}

	public static double interpolateDegrees(double current, double target, double amount) {
		return wrapDegrees(current + wrapDegrees(target - current) * Math.clamp(amount, 0.0D, 1.0D));
	}

	public static double wrapDegrees(double degrees) {
		double wrapped = degrees % 360.0D;
		if (wrapped >= 180.0D) wrapped -= 360.0D;
		if (wrapped < -180.0D) wrapped += 360.0D;
		return wrapped;
	}

	public static String direction(int dx, int dz) {
		if (Math.abs(dx) <= Math.max(2, Math.abs(dz) / 2)) return dz >= 0 ? "south" : "north";
		if (Math.abs(dz) <= Math.max(2, Math.abs(dx) / 2)) return dx >= 0 ? "east" : "west";
		return (dz >= 0 ? "south" : "north") + (dx >= 0 ? "east" : "west");
	}

	public static int distance(int dx, int dz) {
		return (int) Math.round(Math.hypot(dx, dz));
	}
}
