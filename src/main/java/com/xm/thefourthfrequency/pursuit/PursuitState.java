package com.xm.thefourthfrequency.pursuit;

import com.xm.thefourthfrequency.content.TerminalData;
import net.minecraft.nbt.CompoundTag;

/**
 * Typed view over the per-player pursuit fields stored with the bound terminal.
 * World-level correction history is deliberately not imported here.
 */
public record PursuitState(
		int resolvedChases,
		int allowedForm,
		int tutorialDemoMask,
		int tutorialWarningMask,
		int tutorialArchiveMask,
		boolean pending,
		long nextEligibleTick,
		long effectiveActivityTicks,
		int activityProofMask,
		boolean active,
		String sessionId,
		String sessionPhase,
		int sessionForm,
		String sourceDimension,
		long sourcePosition,
		double sourceYaw,
		double sourcePitch,
		String mirrorDimension,
		int mirrorSlot,
		long sessionStartedTick
) {
	public PursuitState {
		resolvedChases = Math.clamp(resolvedChases, 0, PursuitProgressPolicy.FORM_COUNT);
		allowedForm = Math.clamp(allowedForm, 0, PursuitProgressPolicy.FORM_COUNT);
		int tutorialMask = PursuitTutorialPolicy.KNOWN_FORM_MASK;
		tutorialDemoMask &= tutorialMask;
		tutorialWarningMask &= tutorialMask;
		tutorialArchiveMask &= tutorialMask;
		nextEligibleTick = Math.max(0L, nextEligibleTick);
		effectiveActivityTicks = Math.max(0L, effectiveActivityTicks);
		activityProofMask &= PursuitActivityProof.knownMask();
		sessionId = normalized(sessionId);
		sessionPhase = normalizedPhase(sessionPhase);
		sessionForm = Math.clamp(sessionForm, 0, PursuitProgressPolicy.FORM_COUNT);
		sourceDimension = normalized(sourceDimension);
		sourceYaw = Double.isFinite(sourceYaw) ? sourceYaw : 0.0D;
		sourcePitch = Double.isFinite(sourcePitch) ? sourcePitch : 0.0D;
		mirrorDimension = normalized(mirrorDimension);
		mirrorSlot = Math.max(-1, mirrorSlot);
		sessionStartedTick = Math.max(0L, sessionStartedTick);
		if (!active) {
			sessionId = "";
			sessionPhase = "none";
			sessionForm = 0;
			mirrorDimension = "";
			mirrorSlot = -1;
			sessionStartedTick = 0L;
		}
	}

	public static PursuitState empty() {
		return new PursuitState(0, 0, 0, 0, 0, false,
				0L, 0L, 0, false, "", "none", 0,
				"", 0L, 0.0D, 0.0D, "", -1, 0L);
	}

	public static PursuitState read(CompoundTag tag) {
		return new PursuitState(
				tag.getIntOr(TerminalData.PURSUIT_RESOLVED_CHASES, 0),
				tag.getIntOr(TerminalData.PURSUIT_ALLOWED_FORM, 0),
				tag.getIntOr(TerminalData.PURSUIT_TUTORIAL_DEMO_MASK, 0),
				tag.getIntOr(TerminalData.PURSUIT_TUTORIAL_WARNING_MASK, 0),
				tag.getIntOr(TerminalData.PURSUIT_TUTORIAL_ARCHIVE_MASK, 0),
				tag.getBooleanOr(TerminalData.PURSUIT_PENDING, false),
				tag.getLongOr(TerminalData.PURSUIT_NEXT_ELIGIBLE_TICK, 0L),
				tag.getLongOr(TerminalData.PURSUIT_EFFECTIVE_ACTIVITY_TICKS, 0L),
				tag.getIntOr(TerminalData.PURSUIT_ACTIVITY_PROOF_MASK, 0),
				tag.getBooleanOr(TerminalData.PURSUIT_ACTIVE, false),
				tag.getStringOr(TerminalData.PURSUIT_SESSION_ID, ""),
				tag.getStringOr(TerminalData.PURSUIT_SESSION_PHASE, "none"),
				tag.getIntOr(TerminalData.PURSUIT_SESSION_FORM, 0),
				tag.getStringOr(TerminalData.PURSUIT_SOURCE_DIMENSION, ""),
				tag.getLongOr(TerminalData.PURSUIT_SOURCE_POSITION, 0L),
				tag.getDoubleOr(TerminalData.PURSUIT_SOURCE_YAW, 0.0D),
				tag.getDoubleOr(TerminalData.PURSUIT_SOURCE_PITCH, 0.0D),
				tag.getStringOr(TerminalData.PURSUIT_MIRROR_DIMENSION, ""),
				tag.getIntOr(TerminalData.PURSUIT_MIRROR_SLOT, -1),
				tag.getLongOr(TerminalData.PURSUIT_SESSION_STARTED_TICK, 0L));
	}

	public void writeTo(CompoundTag tag) {
		tag.putInt(TerminalData.PURSUIT_RESOLVED_CHASES, resolvedChases);
		tag.putInt(TerminalData.PURSUIT_ALLOWED_FORM, allowedForm);
		tag.putInt(TerminalData.PURSUIT_TUTORIAL_DEMO_MASK, tutorialDemoMask);
		tag.putInt(TerminalData.PURSUIT_TUTORIAL_WARNING_MASK, tutorialWarningMask);
		tag.putInt(TerminalData.PURSUIT_TUTORIAL_ARCHIVE_MASK, tutorialArchiveMask);
		tag.putBoolean(TerminalData.PURSUIT_PENDING, pending);
		tag.putLong(TerminalData.PURSUIT_NEXT_ELIGIBLE_TICK, nextEligibleTick);
		tag.putLong(TerminalData.PURSUIT_EFFECTIVE_ACTIVITY_TICKS, effectiveActivityTicks);
		tag.putInt(TerminalData.PURSUIT_ACTIVITY_PROOF_MASK, activityProofMask);
		tag.putBoolean(TerminalData.PURSUIT_ACTIVE, active);
		tag.putString(TerminalData.PURSUIT_SESSION_ID, sessionId);
		tag.putString(TerminalData.PURSUIT_SESSION_PHASE, sessionPhase);
		tag.putInt(TerminalData.PURSUIT_SESSION_FORM, sessionForm);
		tag.putString(TerminalData.PURSUIT_SOURCE_DIMENSION, sourceDimension);
		tag.putLong(TerminalData.PURSUIT_SOURCE_POSITION, sourcePosition);
		tag.putDouble(TerminalData.PURSUIT_SOURCE_YAW, sourceYaw);
		tag.putDouble(TerminalData.PURSUIT_SOURCE_PITCH, sourcePitch);
		tag.putString(TerminalData.PURSUIT_MIRROR_DIMENSION, mirrorDimension);
		tag.putInt(TerminalData.PURSUIT_MIRROR_SLOT, mirrorSlot);
		tag.putLong(TerminalData.PURSUIT_SESSION_STARTED_TICK, sessionStartedTick);
	}

	public PursuitState withProgress(int resolved, int allowed, boolean hasPending, long nextTick) {
		return new PursuitState(resolved, allowed, tutorialDemoMask, tutorialWarningMask, tutorialArchiveMask,
				hasPending, nextTick, effectiveActivityTicks, activityProofMask, active, sessionId, sessionPhase,
				sessionForm, sourceDimension, sourcePosition, sourceYaw, sourcePitch, mirrorDimension, mirrorSlot,
				sessionStartedTick);
	}

	public PursuitState withTutorial(int demoMask, int warningMask, int archiveMask) {
		return new PursuitState(resolvedChases, allowedForm, demoMask, warningMask, archiveMask,
				pending, nextEligibleTick, effectiveActivityTicks, activityProofMask, active, sessionId, sessionPhase,
				sessionForm, sourceDimension, sourcePosition, sourceYaw, sourcePitch, mirrorDimension, mirrorSlot,
				sessionStartedTick);
	}

	private static String normalized(String value) {
		return value == null ? "" : value;
	}

	private static String normalizedPhase(String value) {
		return value == null || value.isBlank() ? "none" : value;
	}
}
