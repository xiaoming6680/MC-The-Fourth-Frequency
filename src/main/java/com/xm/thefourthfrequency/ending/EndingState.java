package com.xm.thefourthfrequency.ending;

import com.xm.thefourthfrequency.content.TerminalData;
import com.xm.thefourthfrequency.world.FrequencyWorldData;
import net.minecraft.nbt.CompoundTag;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

public final class EndingState {
	public static final int VERSION = 2;
	public static final String END_BOSS_ENCOUNTER = "end_boss";
	private static final String KEY = "ending";

	private EndingState() {
	}

	public static CompoundTag get(FrequencyWorldData data) {
		return data.narrativeState().getCompoundOrEmpty(KEY).copy();
	}

	public static boolean started(FrequencyWorldData data) {
		return get(data).getIntOr("version", 0) > 0;
	}

	public static EndingOutcome outcome(FrequencyWorldData data) {
		return EndingOutcome.fromId(get(data).getStringOr("outcome", EndingOutcome.ACTIVE.id()));
	}

	public static boolean endBossEncounter(FrequencyWorldData data) {
		return END_BOSS_ENCOUNTER.equals(get(data).getStringOr("encounter_type", ""));
	}

	public static boolean endBossDefeated(FrequencyWorldData data) {
		return get(data).getBooleanOr("boss_defeated", false);
	}

	public static boolean endBossDeadlineExpired(FrequencyWorldData data) {
		return get(data).getBooleanOr("deadline_expired", false);
	}

	public static long endBossRemainingTicks(FrequencyWorldData data) {
		return Math.max(0L, get(data).getLongOr("remaining_ticks", 0L));
	}

	public static int endBossParticipantScale(FrequencyWorldData data) {
		return Math.max(1, get(data).getIntOr("participant_scale", 1));
	}

	public static Set<UUID> endBossParticipants(FrequencyWorldData data) {
		Set<UUID> result = new LinkedHashSet<>();
		for (String value : get(data).getStringOr("participant_ids", "").split(";")) {
			if (value.isBlank()) continue;
			try {
				result.add(UUID.fromString(value));
			} catch (IllegalArgumentException ignored) {
				// Legacy or manually edited values are not authoritative.
			}
		}
		return Set.copyOf(result);
	}

	public static void begin(FrequencyWorldData data, UUID bodyId, long bodyPosition, int terminalCount, long tick) {
		if (started(data)) return;
		beginState(data, bodyId, bodyPosition, terminalCount, tick, "", 0L, false, 0);
	}

	public static boolean beginAltar(FrequencyWorldData data, UUID bodyId, long bodyPosition, int terminalCount,
			long tick, String dimension, long altarCenter, boolean truthRead, int anchorsInitial) {
		CompoundTag current = get(data);
		if (current.getIntOr("version", 0) > 0
				&& EndingOutcome.fromId(current.getStringOr("outcome", EndingOutcome.ACTIVE.id())) != EndingOutcome.ACTIVE) {
			return false;
		}
		beginState(data, bodyId, bodyPosition, terminalCount, tick, dimension, altarCenter,
				truthRead, anchorsInitial);
		return true;
	}

	/** Starts the new End encounter, replacing any retired pre-End altar state. */
	public static void beginEndBoss(FrequencyWorldData data, UUID bodyId, long bodyPosition, long tick,
			long durationTicks, int anchorsInitial, Collection<UUID> participants) {
		CompoundTag state = new CompoundTag();
		state.putInt("version", VERSION);
		state.putString("encounter_type", END_BOSS_ENCOUNTER);
		state.putString("outcome", EndingOutcome.ACTIVE.id());
		state.putString("body_uuid", bodyId.toString());
		state.putLong("body_position", bodyPosition);
		state.putLong("awakened_tick", tick);
		state.putLong("duration_ticks", Math.max(1L, durationTicks));
		state.putLong("remaining_ticks", Math.max(1L, durationTicks));
		state.putInt("anchors_initial", Math.max(0, anchorsInitial));
		state.putInt("anchors_remaining", Math.max(0, anchorsInitial));
		state.putInt("participant_scale", Math.max(1, participants.size()));
		state.putString("participant_ids", joinParticipants(participants));
		state.putBoolean("boss_defeated", false);
		state.putBoolean("deadline_expired", false);
		state.putBoolean("body_active", true);
		state.putBoolean("active_anomalies", true);
		state.putString("altar_dimension", "minecraft:the_end");
		state.putLong("altar_center", 0L);
		state.putString("world_event_table", "end_altar_opened;final_entity_awakened");
		data.updateNarrativeState(root -> root.put(KEY, state));
	}

	public static void updateEndBoss(FrequencyWorldData data, UUID bodyId, long bodyPosition,
			long remainingTicks, int anchorsRemaining, int participantScale, Collection<UUID> participants) {
		data.updateNarrativeState(root -> {
			CompoundTag state = root.getCompoundOrEmpty(KEY).copy();
			if (!END_BOSS_ENCOUNTER.equals(state.getStringOr("encounter_type", ""))) return;
			state.putString("body_uuid", bodyId.toString());
			state.putLong("body_position", bodyPosition);
			state.putLong("remaining_ticks", Math.max(0L, remainingTicks));
			state.putInt("anchors_remaining", Math.max(0, anchorsRemaining));
			state.putInt("participant_scale", Math.max(state.getIntOr("participant_scale", 1), participantScale));
			Set<UUID> owners = parseParticipants(state.getStringOr("participant_ids", ""));
			owners.addAll(participants);
			state.putString("participant_ids", joinParticipants(owners));
			root.put(KEY, state);
		});
	}

	public static boolean lockEndBossFailure(FrequencyWorldData data, long tick) {
		data.updateNarrativeState(root -> {
			CompoundTag state = root.getCompoundOrEmpty(KEY).copy();
			if (!END_BOSS_ENCOUNTER.equals(state.getStringOr("encounter_type", ""))) return;
			state.putLong("remaining_ticks", 0L);
			state.putBoolean("deadline_expired", true);
			root.put(KEY, state);
		});
		return resolve(data, EndingOutcome.FAILED, tick);
	}

	/** Marks combat completion. A previously locked failure is deliberately never rewritten. */
	public static EndingOutcome completeEndBoss(FrequencyWorldData data, long tick) {
		EndingOutcome before = outcome(data);
		if (before == EndingOutcome.ACTIVE) resolve(data, EndingOutcome.SUCCESS, tick);
		data.updateNarrativeState(root -> {
			CompoundTag state = root.getCompoundOrEmpty(KEY).copy();
			if (!END_BOSS_ENCOUNTER.equals(state.getStringOr("encounter_type", ""))) return;
			state.putBoolean("boss_defeated", true);
			state.putBoolean("body_active", false);
			state.putLong("boss_defeated_tick", tick);
			state.putString("world_event_table", state.getBooleanOr("deadline_expired", false)
					? "intervention_window_missed;final_entity_defeated"
					: "intervention_succeeded;final_entity_defeated");
			root.put(KEY, state);
		});
		return outcome(data);
	}

	private static void beginState(FrequencyWorldData data, UUID bodyId, long bodyPosition, int terminalCount,
			long tick, String dimension, long altarCenter, boolean truthRead, int anchorsInitial) {
		CompoundTag state = new CompoundTag();
		state.putInt("version", VERSION);
		state.putString("outcome", EndingOutcome.ACTIVE.id());
		state.putString("body_uuid", bodyId.toString());
		state.putLong("body_position", bodyPosition);
		state.putLong("awakened_tick", tick);
		state.putInt("terminal_count", Math.max(1, terminalCount));
		state.putInt("captured_count", 0);
		state.putBoolean("body_active", true);
		state.putBoolean("active_anomalies", true);
		state.putString("altar_dimension", dimension);
		state.putLong("altar_center", altarCenter);
		state.putBoolean("truth_read_at_start", truthRead);
		state.putInt("anchors_initial", Math.max(0, anchorsInitial));
		state.putString("world_event_table", "stronghold_altar_opened;final_entity_awakened");
		data.updateNarrativeState(root -> root.put(KEY, state));
	}

	public static void updateBody(FrequencyWorldData data, UUID bodyId, long position) {
		data.updateNarrativeState(root -> {
			CompoundTag state = root.getCompoundOrEmpty(KEY).copy();
			state.putString("body_uuid", bodyId.toString());
			state.putLong("body_position", position);
			root.put(KEY, state);
		});
	}

	public static void recordCapture(FrequencyWorldData data, int capturedCount) {
		data.updateNarrativeState(root -> {
			CompoundTag state = root.getCompoundOrEmpty(KEY).copy();
			state.putInt("captured_count", Math.max(state.getIntOr("captured_count", 0), capturedCount));
			state.putInt("strength_stage", Math.max(0, capturedCount));
			root.put(KEY, state);
		});
	}

	public static boolean resolve(FrequencyWorldData data, EndingOutcome outcome, long tick) {
		CompoundTag before = get(data);
		EndingOutcome current = EndingOutcome.fromId(before.getStringOr("outcome", EndingOutcome.ACTIVE.id()));
		if (current != EndingOutcome.ACTIVE) return false;
		data.updateNarrativeState(root -> {
			CompoundTag state = root.getCompoundOrEmpty(KEY).copy();
			state.putInt("version", VERSION);
			state.putString("outcome", outcome.id());
			state.putLong("resolved_tick", tick);
			state.putBoolean("body_active", false);
			state.putBoolean("active_anomalies", false);
			state.putString("terminal_aftermath", switch (outcome) {
				case UNDISCOVERED -> "betrayal_revealed";
				case FAILED -> "external_senses_stable";
				case SUCCESS -> "fourth_band_cut";
				case ACTIVE -> "terminal_pursuit";
			});
			state.putString("correction_aftermath", "dormant");
			state.putString("sound_aftermath", outcome == EndingOutcome.SUCCESS ? "weak_resonance_only" : "quiet_after_altar");
			state.putString("environment_aftermath", outcome == EndingOutcome.SUCCESS ? "terrain_recovered;faint_scars" : "altar_scars_only");
			state.putString("world_event_table", switch (outcome) {
				case UNDISCOVERED -> "truth_missed;final_entity_defeated";
				case FAILED -> "anchors_lost;final_entity_defeated";
				case SUCCESS -> "fourth_band_cut;active_anomalies_stopped;weak_resonance";
				case ACTIVE -> "stronghold_altar_opened;final_entity_awakened";
			});
			root.put(KEY, state);
		});
		for (UUID owner : data.terminalOwnerIds()) {
			try {
				data.updateTerminalRecord(owner, record -> {
					record.putInt(TerminalData.ENDING_VERSION, VERSION);
					record.putString(TerminalData.ENDING_OUTCOME, outcome.id());
					if (outcome == EndingOutcome.SUCCESS) {
						record.putInt(TerminalData.BAND_STAGE, 3);
						addCapability(record, "weak_resonance");
					}
				});
			} catch (IllegalArgumentException ignored) {
				// Invalid legacy owner records are already non-authoritative.
			}
		}
		return true;
	}

	private static void addCapability(CompoundTag record, String capability) {
		String current = record.getStringOr(TerminalData.TERMINAL_CAPABILITIES, "");
		for (String entry : current.split(";")) if (entry.equals(capability)) return;
		record.putString(TerminalData.TERMINAL_CAPABILITIES,
				current.isBlank() ? capability : current + ";" + capability);
	}

	private static Set<UUID> parseParticipants(String encoded) {
		Set<UUID> result = new LinkedHashSet<>();
		for (String value : encoded.split(";")) {
			if (value.isBlank()) continue;
			try {
				result.add(UUID.fromString(value));
			} catch (IllegalArgumentException ignored) {
				// Ignore non-authoritative legacy data.
			}
		}
		return result;
	}

	private static String joinParticipants(Collection<UUID> participants) {
		return participants.stream().map(UUID::toString).distinct().sorted().reduce((left, right) -> left + ";" + right)
				.orElse("");
	}

	public static boolean activeAnomaliesAllowed(FrequencyWorldData data) {
		return !started(data) || get(data).getBooleanOr("active_anomalies", true);
	}
}
