package com.xm.thefourthfrequency.terminal;

import java.util.Locale;

public final class SignalClock {
	private SignalClock() { }

	public static String format(long dayTime) {
		long ticks = Math.floorMod(dayTime, 24_000L);
		int totalMinutes = (int) Math.floorMod(360L + ticks * 1_440L / 24_000L, 1_440L);
		return String.format(Locale.ROOT, "%02d:%02d", totalMinutes / 60, totalMinutes % 60);
	}
}
