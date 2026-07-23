package com.xm.thefourthfrequency.pursuit;

import com.xm.thefourthfrequency.world.SurvivalMilestone;

/** Pure progression rules shared by the director, terminal and persistence tests. */
public final class PursuitProgressPolicy {
	public static final int FORM_COUNT = 5;
	public static final int REQUIRED_EYE_SAMPLES = 3;
	public static final long FORM_ONE_ACTIVITY_FALLBACK_TICKS = 20L * 60L * 20L;
	public static final long MIN_CHASE_GAP_TICKS = 20L * 60L * 20L;
	public static final long MAX_CHASE_GAP_TICKS = 30L * 60L * 20L;

	private PursuitProgressPolicy() {
	}

	public static boolean earlyFormEligible(boolean bound, int successfulAnomalies,
			int activityProofMask, long effectiveActivityTicks) {
		boolean activityRoute = PursuitActivityProof.any(activityProofMask)
				|| effectiveActivityTicks >= FORM_ONE_ACTIVITY_FALLBACK_TICKS;
		return bound && successfulAnomalies > 0 && activityRoute;
	}

	public static int allowedForm(int milestones, int eyeSamples, boolean earlyFormEligible) {
		if (SurvivalMilestone.FOUND_STRONGHOLD.present(milestones)) return 5;
		if (Math.max(0, eyeSamples) >= REQUIRED_EYE_SAMPLES) return 4;
		if (SurvivalMilestone.RETURNED_NETHER.present(milestones)
				&& SurvivalMilestone.COLLECTED_BLAZE_RODS.present(milestones)) return 3;
		if (SurvivalMilestone.ENTERED_NETHER.present(milestones)) return 2;
		return earlyFormEligible ? 1 : 0;
	}

	public static int actualForm(int resolvedChases) {
		return Math.min(Math.clamp(resolvedChases, 0, FORM_COUNT) + 1, FORM_COUNT);
	}

	public static boolean complete(int resolvedChases) {
		return resolvedChases >= FORM_COUNT;
	}

	/**
	 * A story jump creates at most one pending pursuit. Further pursuits require a later
	 * story-threshold transition, so returning players never receive catch-up debt.
	 */
	public static boolean pendingAfterAllowedFormUpdate(boolean alreadyPending, int previousAllowedForm,
			int newAllowedForm, int resolvedChases) {
		if (alreadyPending) return true;
		int previous = Math.clamp(previousAllowedForm, 0, FORM_COUNT);
		int current = Math.clamp(newAllowedForm, 0, FORM_COUNT);
		return current > previous && !complete(resolvedChases) && actualForm(resolvedChases) <= current;
	}

	public static boolean canStart(boolean pending, int allowedForm, int resolvedChases,
			boolean tutorialReady, long now, long nextEligibleTick) {
		return pending
				&& !complete(resolvedChases)
				&& actualForm(resolvedChases) <= Math.clamp(allowedForm, 0, FORM_COUNT)
				&& tutorialReady
				&& now >= Math.max(0L, nextEligibleTick);
	}

	public static int resolvedAfterSuccess(int resolvedChases) {
		return Math.min(Math.max(0, resolvedChases) + 1, FORM_COUNT);
	}

	/**
	 * A player who outran several story gates keeps only the immediately next pursuit.
	 * The long cooldown and that form's tutorial still apply, so this is not a catch-up queue.
	 */
	public static boolean pendingAfterSuccess(int resolvedChases, int allowedForm) {
		return !complete(resolvedChases)
				&& actualForm(resolvedChases) <= Math.clamp(allowedForm, 0, FORM_COUNT);
	}

	public static int terminalVisualStage(int resolvedChases, int allowedForm, int anomalyStage) {
		if (resolvedChases >= 3 && allowedForm >= 4 && anomalyStage >= 4) return 2;
		return resolvedChases >= 1 ? 1 : 0;
	}
}
