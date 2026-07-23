package com.xm.thefourthfrequency.pursuit;

/** Safe demonstration -> terminal warning -> formal pursuit -> archive. */
public final class PursuitTutorialPolicy {
	public static final int KNOWN_FORM_MASK = (1 << PursuitProgressPolicy.FORM_COUNT) - 1;

	private PursuitTutorialPolicy() {
	}

	public static int formMask(int form) {
		if (form < 1 || form > PursuitProgressPolicy.FORM_COUNT) return 0;
		return 1 << (form - 1);
	}

	public static boolean demonstrated(int demoMask, int form) {
		return (demoMask & formMask(form)) != 0;
	}

	public static boolean warned(int warningMask, int form) {
		return (warningMask & formMask(form)) != 0;
	}

	public static boolean archived(int archiveMask, int form) {
		return (archiveMask & formMask(form)) != 0;
	}

	public static boolean readyForFormalPursuit(int demoMask, int warningMask, int form) {
		int bit = formMask(form);
		return bit != 0 && (demoMask & bit) != 0 && (warningMask & bit) != 0;
	}

	public static int mark(int mask, int form) {
		return (mask | formMask(form)) & KNOWN_FORM_MASK;
	}

	public static Status status(int demoMask, int warningMask, int archiveMask, int form) {
		if (archived(archiveMask, form)) return Status.ARCHIVED;
		if (readyForFormalPursuit(demoMask, warningMask, form)) return Status.READY;
		if (warned(warningMask, form)) return Status.WARNED;
		if (demonstrated(demoMask, form)) return Status.DEMONSTRATED;
		return Status.UNSEEN;
	}

	public enum Status {
		UNSEEN,
		DEMONSTRATED,
		WARNED,
		READY,
		ARCHIVED
	}
}
