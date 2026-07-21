package com.xm.thefourthfrequency.terminal;

/** Pure typing script; the owning anomaly deliberately holds the final sentence after tick 240. */
public final class ChannelOverrideScript {
	private static final String FIRST = "有人能看到吗？";
	private static final String SECOND = "我没有断开。";
	private static final String PREFIX = "我";
	private static final String FINAL = "我正在看着你。";

	private ChannelOverrideScript() { }

	public static String textAt(int tick) {
		int time = Math.max(0, tick);
		if (time < 50) return prefix(FIRST, time, 50);
		if (time < 70) return FIRST;
		if (time < 95) return prefix(FIRST, 95 - time, 25);
		if (time < 150) return prefix(SECOND, time - 95, 55);
		if (time < 170) return SECOND;
		if (time < 205) {
			int removable = SECOND.length() - PREFIX.length();
			int retained = SECOND.length() - Math.min(removable, (time - 170) * removable / 35);
			return SECOND.substring(0, retained);
		}
		int extra = FINAL.length() - PREFIX.length();
		int retained = PREFIX.length() + Math.min(extra, (time - 205) * extra / 35);
		return FINAL.substring(0, retained);
	}

	private static String prefix(String value, int elapsed, int duration) {
		int length = Math.clamp(elapsed * value.length() / Math.max(1, duration), 0, value.length());
		return value.substring(0, length);
	}
}
