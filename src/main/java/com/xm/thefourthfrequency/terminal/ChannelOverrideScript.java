package com.xm.thefourthfrequency.terminal;

/**
 * Pure typing script; the owning anomaly deliberately holds the final sentence after tick 240.
 *
 * <p>Text is supplied by the caller (localized client-side via
 * {@code message.thefourthfrequency.anomaly.channel_override.line_1/2/3}) rather than hardcoded
 * here, so this class stays a locale-agnostic function of tick and string content: the shared
 * "I ..." prefix between the second and third lines is derived from whatever text is passed in
 * instead of assumed to be a fixed one-character prefix.</p>
 */
public final class ChannelOverrideScript {
	private ChannelOverrideScript() { }

	public static String textAt(int tick, String first, String second, String finalLine) {
		int time = Math.max(0, tick);
		if (time < 50) return prefix(first, time, 50);
		if (time < 70) return first;
		if (time < 95) return prefix(first, 95 - time, 25);
		if (time < 150) return prefix(second, time - 95, 55);
		if (time < 170) return second;
		int sharedPrefix = commonPrefixLength(second, finalLine);
		if (time < 205) {
			int removable = second.length() - sharedPrefix;
			int retained = second.length() - Math.min(removable, (time - 170) * removable / 35);
			return second.substring(0, retained);
		}
		int extra = finalLine.length() - sharedPrefix;
		int retained = sharedPrefix + Math.min(extra, (time - 205) * extra / 35);
		return finalLine.substring(0, retained);
	}

	private static String prefix(String value, int elapsed, int duration) {
		int length = Math.clamp(elapsed * value.length() / Math.max(1, duration), 0, value.length());
		return value.substring(0, length);
	}

	private static int commonPrefixLength(String a, String b) {
		int max = Math.min(a.length(), b.length());
		int length = 0;
		while (length < max && a.charAt(length) == b.charAt(length)) length++;
		return length;
	}
}
