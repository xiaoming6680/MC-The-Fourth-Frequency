package com.xm.thefourthfrequency.terminal;

import java.util.ArrayList;
import java.util.List;

/** Deterministic timing and text corruption rules for terminal navigation feedback. */
public final class TerminalNavigationVisualPolicy {
	private static final int[] GLITCH_GLYPHS = {'\uFFFD', '\u2593', '\u256B', '\u00A4', '?'};

	private TerminalNavigationVisualPolicy() {
	}

	public static int animatedProbeDots(double ageTicks) {
		return 1 + Math.floorMod((int) Math.floor(ageTicks / 5.0D), 3);
	}

	public static boolean sideRouteGlitchActive(double ageTicks) {
		int tick = Math.max(0, (int) Math.floor(ageTicks));
		return tick >= 40 && Math.floorMod(tick, 40) < 10;
	}

	public static String corruptNavigationName(String name, int targetWireId, long cycle) {
		int[] codePoints = name.codePoints().toArray();
		List<Integer> candidates = new ArrayList<>();
		for (int index = 0; index < codePoints.length; index++) {
			if (Character.isLetterOrDigit(codePoints[index])) candidates.add(index);
		}
		if (candidates.isEmpty()) return name;
		long seed = cycle * 1_103_515_245L + targetWireId * 12_345L;
		int replacements = Math.min(candidates.size(), 1 + Math.floorMod((int) seed, 2));
		for (int offset = 0; offset < replacements; offset++) {
			int choice = Math.floorMod((int) (seed >>> (offset * 7)) + offset * 17, candidates.size());
			int position = candidates.remove(choice);
			codePoints[position] = GLITCH_GLYPHS[Math.floorMod(
					(int) (seed >>> (offset * 5)) + offset, GLITCH_GLYPHS.length)];
		}
		return new String(codePoints, 0, codePoints.length);
	}

	public static boolean navigationNeedleFlashVisible(double elapsedTicks) {
		return elapsedTicks < 0.0D || elapsedTicks >= 20.0D
				|| Math.floorMod((int) Math.floor(elapsedTicks / 2.0D), 2) == 0;
	}

	public static boolean navigationNeedleFlashActive(double elapsedTicks) {
		return elapsedTicks >= 0.0D && elapsedTicks < 20.0D;
	}
}
