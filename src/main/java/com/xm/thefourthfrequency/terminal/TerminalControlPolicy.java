package com.xm.thefourthfrequency.terminal;

import com.xm.thefourthfrequency.pursuit.PursuitProgressPolicy;

public final class TerminalControlPolicy {
	public static final int DEFAULT_TUNING = 50;
	public static final int RECEIVER_LOCK_RADIUS = 2;

	private TerminalControlPolicy() {
	}

	public static int mode(int value) {
		return Math.clamp(value, 0, 1);
	}

	public static int tuning(int value) {
		return Math.clamp(value, 0, 100);
	}

	public static boolean validMode(int value) {
		return value >= Mode.SIGNAL.ordinal() && value <= Mode.FILES.ordinal();
	}

	public static boolean validTuning(int value) {
		return value >= 0 && value <= 100;
	}

	public static boolean receiverLocked(int tuning, int target) {
		return Math.abs(tuning(tuning) - tuning(target)) <= RECEIVER_LOCK_RADIUS;
	}

	public static int receiverStrength(int tuning, int target) {
		return Math.clamp(100 - Math.abs(tuning(tuning) - tuning(target)) * 4, 0, 100);
	}

	public static int visualStage(int plotStage, int bodyStage) {
		return bodyStage >= 3 ? 2 : plotStage >= 3 ? 1 : 0;
	}

	public static int pursuitVisualStage(int resolvedChases, int allowedForm, int anomalyStage) {
		return PursuitProgressPolicy.terminalVisualStage(resolvedChases, allowedForm, anomalyStage);
	}

	public enum Mode {
		SIGNAL,
		FILES;

		public static Mode fromWire(int value) {
			return values()[mode(value)];
		}
	}
}
