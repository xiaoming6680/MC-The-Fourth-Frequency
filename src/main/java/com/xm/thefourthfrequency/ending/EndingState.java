package com.xm.thefourthfrequency.ending;

import com.xm.thefourthfrequency.content.TerminalData;
import com.xm.thefourthfrequency.world.FrequencyWorldData;
import net.minecraft.nbt.CompoundTag;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class EndingState {
	public static final int VERSION = 3;
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

	public static EndBossPhase endBossPhase(FrequencyWorldData data) {
		return EndBossPhase.fromId(get(data).getIntOr("phase_id", EndBossPhase.OBSERVATION.id()));
	}

	public static boolean endBossCollapse(FrequencyWorldData data) {
		return get(data).getBooleanOr("collapse_active", false);
	}

	public static float endBossHealthRatio(FrequencyWorldData data) {
		return Math.clamp(get(data).getFloatOr("health_ratio", 1.0F), 0.0F, 1.0F);
	}

	public static long endBossDeterministicSeed(FrequencyWorldData data) {
		return get(data).getLongOr("deterministic_seed", 0L);
	}

	public static UUID endBossEncounterId(FrequencyWorldData data) {
		CompoundTag state = get(data);
		String encoded = state.getStringOr("encounter_uuid", state.getStringOr("body_uuid", ""));
		try {
			return UUID.fromString(encoded);
		} catch (IllegalArgumentException ignored) {
			return new UUID(0L, 0L);
		}
	}

	public static long endBossNextRuptureTick(FrequencyWorldData data) {
		return Math.max(0L, get(data).getLongOr("next_rupture_tick", 0L));
	}

	public static long endBossNextAdaptationTick(FrequencyWorldData data) {
		return Math.max(0L, get(data).getLongOr("next_adaptation_tick", 0L));
	}

	public static int endBossAdaptationSequence(FrequencyWorldData data) {
		return Math.max(0, get(data).getIntOr("adaptation_sequence", 0));
	}

	public static long endBossNextIntrusionTick(FrequencyWorldData data) {
		return Math.max(0L, get(data).getLongOr("next_intrusion_tick", 0L));
	}

	public static int endBossIntrusionPhase(FrequencyWorldData data) {
		return Math.max(0, get(data).getIntOr("intrusion_phase_id", 0));
	}

	public static int endBossDestroyedBlocks(FrequencyWorldData data) {
		return Math.clamp(get(data).getIntOr("destroyed_blocks", 0), 0,
				EndBossDestructionPolicy.MAX_PERMANENT_EDITS);
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
			long durationTicks, int anchorsInitial, Collection<UUID> participants, long deterministicSeed) {
		CompoundTag state = new CompoundTag();
		state.putInt("version", VERSION);
		state.putString("encounter_type", END_BOSS_ENCOUNTER);
		state.putString("outcome", EndingOutcome.ACTIVE.id());
		state.putString("body_uuid", bodyId.toString());
		state.putString("encounter_uuid", bodyId.toString());
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
		state.putInt("phase_id", EndBossPhase.OBSERVATION.id());
		state.putBoolean("collapse_active", false);
		state.putFloat("health_ratio", 1.0F);
		state.putLong("next_rupture_tick", 0L);
		state.putLong("next_adaptation_tick", 0L);
		state.putInt("adaptation_sequence", 0);
		state.putLong("next_intrusion_tick", 0L);
		state.putInt("intrusion_phase_id", 0);
		state.putInt("intrusion_sequence", 0);
		state.putLong("deterministic_seed", deterministicSeed);
		state.putInt("destroyed_blocks", 0);
		clearPendingAttackFields(state);
		state.putString("altar_dimension", "minecraft:the_end");
		state.putLong("altar_center", 0L);
		state.putString("world_event_table", "end_altar_opened;final_entity_awakened");
		data.updateNarrativeState(root -> root.put(KEY, state));
	}

	/** Source-compatible overload for fixtures; live encounters always provide a world-derived seed. */
	public static void beginEndBoss(FrequencyWorldData data, UUID bodyId, long bodyPosition, long tick,
			long durationTicks, int anchorsInitial, Collection<UUID> participants) {
		beginEndBoss(data, bodyId, bodyPosition, tick, durationTicks, anchorsInitial, participants,
				bodyId.getMostSignificantBits() ^ bodyId.getLeastSignificantBits() ^ tick);
	}

	/** Migrates only an active End encounter. Permanent outcomes and legacy altar records are untouched. */
	public static void ensureEndBossV3(FrequencyWorldData data, long now, float healthRatio, long deterministicSeed) {
		data.updateNarrativeState(root -> {
			CompoundTag state = root.getCompoundOrEmpty(KEY).copy();
			if (!END_BOSS_ENCOUNTER.equals(state.getStringOr("encounter_type", ""))
					|| state.getBooleanOr("boss_defeated", false)) return;
			EndingOutcome outcome = EndingOutcome.fromId(state.getStringOr("outcome", EndingOutcome.ACTIVE.id()));
			if (state.getIntOr("version", 0) < VERSION && outcome != EndingOutcome.ACTIVE) return;
			boolean expired = state.getBooleanOr("deadline_expired", false);
			EndBossPhase phase = EndBossPhase.fromId(state.getIntOr("phase_id",
					EndBossPhase.forHealth(healthRatio, expired).id()));
			phase = EndBossPhase.advance(phase, healthRatio, expired);
			state.putInt("version", VERSION);
			state.putInt("phase_id", phase.id());
			state.putBoolean("collapse_active", expired || state.getBooleanOr("collapse_active", false));
			state.putFloat("health_ratio", Math.clamp(healthRatio, 0.0F, 1.0F));
			if (!state.contains("next_rupture_tick")) state.putLong("next_rupture_tick", 0L);
			if (!state.contains("next_adaptation_tick")) state.putLong("next_adaptation_tick", 0L);
			if (!state.contains("adaptation_sequence")) state.putInt("adaptation_sequence", 0);
			if (!state.contains("next_intrusion_tick")) state.putLong("next_intrusion_tick", 0L);
			if (!state.contains("intrusion_phase_id")) state.putInt("intrusion_phase_id", 0);
			if (!state.contains("intrusion_sequence")) state.putInt("intrusion_sequence", 0);
			if (!state.contains("deterministic_seed")) state.putLong("deterministic_seed", deterministicSeed);
			if (!state.contains("encounter_uuid"))
				state.putString("encounter_uuid", state.getStringOr("body_uuid", new UUID(0L, 0L).toString()));
			if (!state.contains("destroyed_blocks")) state.putInt("destroyed_blocks", 0);
			if (!state.contains("pending_action_id")) clearPendingAttackFields(state);
			if (expired) {
				state.putBoolean("body_active", true);
				state.putBoolean("active_anomalies", true);
			}
			state.putLong("v3_migrated_tick", state.getLongOr("v3_migrated_tick", now));
			root.put(KEY, state);
		});
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

	public static void updateEndBossCombat(FrequencyWorldData data, float healthRatio,
			EndBossPhase phase, boolean collapse) {
		data.updateNarrativeState(root -> {
			CompoundTag state = root.getCompoundOrEmpty(KEY).copy();
			if (!END_BOSS_ENCOUNTER.equals(state.getStringOr("encounter_type", ""))) return;
			EndBossPhase current = EndBossPhase.fromId(state.getIntOr("phase_id", EndBossPhase.OBSERVATION.id()));
			EndBossPhase advanced = EndBossPhase.advance(current, healthRatio, collapse);
			state.putInt("version", VERSION);
			state.putInt("phase_id", advanced.id());
			state.putBoolean("collapse_active", collapse || state.getBooleanOr("collapse_active", false));
			state.putFloat("health_ratio", Math.clamp(healthRatio, 0.0F, 1.0F));
			root.put(KEY, state);
		});
	}

	public static void setEndBossNextRuptureTick(FrequencyWorldData data, long tick) {
		updateEndBossLong(data, "next_rupture_tick", Math.max(0L, tick));
	}

	public static void setEndBossNextAdaptationTick(FrequencyWorldData data, long tick) {
		updateEndBossLong(data, "next_adaptation_tick", Math.max(0L, tick));
	}

	public static void setEndBossAdaptationSequence(FrequencyWorldData data, int sequence) {
		updateEndBossInt(data, "adaptation_sequence", Math.max(0, sequence));
	}

	public static void setEndBossNextIntrusionTick(FrequencyWorldData data, long tick) {
		updateEndBossLong(data, "next_intrusion_tick", Math.max(0L, tick));
	}

	public static void setEndBossIntrusionPhase(FrequencyWorldData data, EndBossPhase phase) {
		updateEndBossInt(data, "intrusion_phase_id", phase.id());
	}

	public static int nextEndBossIntrusionSequence(FrequencyWorldData data) {
		int[] sequence = {0};
		data.updateNarrativeState(root -> {
			CompoundTag state = root.getCompoundOrEmpty(KEY).copy();
			if (!END_BOSS_ENCOUNTER.equals(state.getStringOr("encounter_type", ""))) return;
			sequence[0] = Math.max(1, state.getIntOr("intrusion_sequence", 0) + 1);
			state.putInt("intrusion_sequence", sequence[0]);
			root.put(KEY, state);
		});
		return sequence[0];
	}

	public static Optional<PendingEndAttack> pendingEndBossAttack(FrequencyWorldData data) {
		CompoundTag state = get(data);
		EndBossAction action = EndBossAction.fromId(state.getIntOr("pending_action_id", 0));
		if (action == EndBossAction.NONE) return Optional.empty();
		return Optional.of(new PendingEndAttack(action,
				state.getLongOr("pending_due_tick", 0L), state.getLongOr("pending_seed", 0L),
				state.getLongOr("pending_origin", 0L), state.getLongOr("pending_target", 0L),
				Math.max(0, state.getIntOr("pending_radius", 0)),
				Math.max(0, state.getIntOr("pending_max_blocks", 0)),
				Math.max(0, state.getIntOr("pending_cursor", 0)),
				state.getBooleanOr("pending_damage_applied", false)));
	}

	public static boolean armEndBossAttack(FrequencyWorldData data, PendingEndAttack attack) {
		if (pendingEndBossAttack(data).isPresent()) return false;
		boolean[] armed = {false};
		data.updateNarrativeState(root -> {
			CompoundTag state = root.getCompoundOrEmpty(KEY).copy();
			if (!END_BOSS_ENCOUNTER.equals(state.getStringOr("encounter_type", ""))
					|| state.getBooleanOr("boss_defeated", false)
					|| state.getIntOr("pending_action_id", 0) != 0) return;
			state.putInt("pending_action_id", attack.action().id());
			state.putLong("pending_due_tick", attack.dueTick());
			state.putLong("pending_seed", attack.seed());
			state.putLong("pending_origin", attack.origin());
			state.putLong("pending_target", attack.target());
			state.putInt("pending_radius", Math.max(0, attack.radius()));
			state.putInt("pending_max_blocks", Math.max(0, attack.maxBlocks()));
			state.putInt("pending_cursor", Math.max(0, attack.cursor()));
			state.putBoolean("pending_damage_applied", attack.damageApplied());
			root.put(KEY, state);
			armed[0] = true;
		});
		return armed[0];
	}

	public static void updatePendingEndBossAttack(FrequencyWorldData data, int cursor,
			boolean damageApplied, int destroyedDelta) {
		data.updateNarrativeState(root -> {
			CompoundTag state = root.getCompoundOrEmpty(KEY).copy();
			if (!END_BOSS_ENCOUNTER.equals(state.getStringOr("encounter_type", ""))
					|| state.getIntOr("pending_action_id", 0) == 0) return;
			state.putInt("pending_cursor", Math.max(state.getIntOr("pending_cursor", 0), cursor));
			state.putBoolean("pending_damage_applied",
					damageApplied || state.getBooleanOr("pending_damage_applied", false));
			state.putInt("destroyed_blocks", Math.clamp(
					state.getIntOr("destroyed_blocks", 0) + Math.max(0, destroyedDelta), 0,
					EndBossDestructionPolicy.MAX_PERMANENT_EDITS));
			root.put(KEY, state);
		});
	}

	public static void clearPendingEndBossAttack(FrequencyWorldData data) {
		data.updateNarrativeState(root -> {
			CompoundTag state = root.getCompoundOrEmpty(KEY).copy();
			if (!END_BOSS_ENCOUNTER.equals(state.getStringOr("encounter_type", ""))) return;
			clearPendingAttackFields(state);
			root.put(KEY, state);
		});
	}

	public static boolean lockEndBossFailure(FrequencyWorldData data, long tick) {
		CompoundTag before = get(data);
		if (!END_BOSS_ENCOUNTER.equals(before.getStringOr("encounter_type", ""))
				|| EndingOutcome.fromId(before.getStringOr("outcome", EndingOutcome.ACTIVE.id())) != EndingOutcome.ACTIVE) {
			return false;
		}
		data.updateNarrativeState(root -> {
			CompoundTag state = root.getCompoundOrEmpty(KEY).copy();
			if (!END_BOSS_ENCOUNTER.equals(state.getStringOr("encounter_type", ""))) return;
			state.putInt("version", VERSION);
			state.putLong("remaining_ticks", 0L);
			state.putBoolean("deadline_expired", true);
			state.putBoolean("collapse_active", true);
			state.putInt("phase_id", EndBossPhase.COLLAPSE.id());
			state.putString("outcome", EndingOutcome.FAILED.id());
			state.putLong("resolved_tick", tick);
			state.putBoolean("body_active", true);
			state.putBoolean("active_anomalies", true);
			applyAftermath(state, EndingOutcome.FAILED, true);
			state.putString("world_event_table", "intervention_window_missed;final_entity_active");
			root.put(KEY, state);
		});
		projectOutcome(data, EndingOutcome.FAILED);
		return true;
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
			state.putBoolean("active_anomalies", false);
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
			boolean endBoss = END_BOSS_ENCOUNTER.equals(state.getStringOr("encounter_type", ""));
			state.putInt("version", VERSION);
			state.putString("outcome", outcome.id());
			state.putLong("resolved_tick", tick);
			state.putBoolean("body_active", false);
			state.putBoolean("active_anomalies", false);
			applyAftermath(state, outcome, endBoss);
			state.putString("world_event_table", switch (outcome) {
				case UNDISCOVERED -> "truth_missed;final_entity_defeated";
				case FAILED -> "anchors_lost;final_entity_defeated";
				case SUCCESS -> "fourth_band_cut;active_anomalies_stopped;weak_resonance";
				case ACTIVE -> "stronghold_altar_opened;final_entity_awakened";
			});
			root.put(KEY, state);
		});
		projectOutcome(data, outcome);
		return true;
	}

	private static void projectOutcome(FrequencyWorldData data, EndingOutcome outcome) {
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
	}

	private static void applyAftermath(CompoundTag state, EndingOutcome outcome, boolean endBoss) {
		state.putString("terminal_aftermath", switch (outcome) {
			case UNDISCOVERED -> "betrayal_revealed";
			case FAILED -> "external_senses_stable";
			case SUCCESS -> "fourth_band_cut";
			case ACTIVE -> "terminal_pursuit";
		});
		state.putString("correction_aftermath", "dormant");
		state.putString("sound_aftermath", outcome == EndingOutcome.SUCCESS
				? "weak_resonance_only" : endBoss ? "collapse_resonance" : "quiet_after_altar");
		state.putString("environment_aftermath", endBoss
				? "end_permanent_erosion"
				: outcome == EndingOutcome.SUCCESS ? "terrain_recovered;faint_scars" : "altar_scars_only");
	}

	private static void updateEndBossLong(FrequencyWorldData data, String key, long value) {
		data.updateNarrativeState(root -> {
			CompoundTag state = root.getCompoundOrEmpty(KEY).copy();
			if (!END_BOSS_ENCOUNTER.equals(state.getStringOr("encounter_type", ""))) return;
			state.putLong(key, value);
			root.put(KEY, state);
		});
	}

	private static void updateEndBossInt(FrequencyWorldData data, String key, int value) {
		data.updateNarrativeState(root -> {
			CompoundTag state = root.getCompoundOrEmpty(KEY).copy();
			if (!END_BOSS_ENCOUNTER.equals(state.getStringOr("encounter_type", ""))) return;
			state.putInt(key, value);
			root.put(KEY, state);
		});
	}

	private static void clearPendingAttackFields(CompoundTag state) {
		state.putInt("pending_action_id", EndBossAction.NONE.id());
		state.putLong("pending_due_tick", 0L);
		state.putLong("pending_seed", 0L);
		state.putLong("pending_origin", 0L);
		state.putLong("pending_target", 0L);
		state.putInt("pending_radius", 0);
		state.putInt("pending_max_blocks", 0);
		state.putInt("pending_cursor", 0);
		state.putBoolean("pending_damage_applied", false);
	}

	public record PendingEndAttack(EndBossAction action, long dueTick, long seed, long origin, long target,
			int radius, int maxBlocks, int cursor, boolean damageApplied) {
		public PendingEndAttack {
			if (action == null || action == EndBossAction.NONE || dueTick < 0L || radius < 0
					|| maxBlocks < 0 || cursor < 0) throw new IllegalArgumentException("Invalid pending End attack");
		}
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
		WorldInterfaceState.Snapshot worldInterface = WorldInterfaceState.snapshot(data);
		if (worldInterface.valid() && worldInterface.present()
				&& worldInterface.stage() != WorldInterfaceStage.COMPLETE) return false;
		return !started(data) || get(data).getBooleanOr("active_anomalies", true);
	}

	/** Timeout records a result, but a v3 collapsed End body keeps full encounter pressure until killed. */
	public static boolean endingPressureActive(FrequencyWorldData data) {
		WorldInterfaceState.Snapshot worldInterface = WorldInterfaceState.snapshot(data);
		if (worldInterface.valid() && worldInterface.present()) {
			return worldInterface.stage().wireId() >= WorldInterfaceStage.SUMMONING.wireId()
					&& worldInterface.stage() != WorldInterfaceStage.COMPLETE;
		}
		if (!started(data)) return false;
		CompoundTag state = get(data);
		if (EndingOutcome.fromId(state.getStringOr("outcome", EndingOutcome.ACTIVE.id())) == EndingOutcome.ACTIVE) {
			return true;
		}
		return END_BOSS_ENCOUNTER.equals(state.getStringOr("encounter_type", ""))
				&& state.getIntOr("version", 0) >= VERSION
				&& state.getBooleanOr("body_active", false)
				&& !state.getBooleanOr("boss_defeated", false);
	}
}
