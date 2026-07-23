package com.xm.thefourthfrequency.content;

import com.xm.thefourthfrequency.bootstrap.RuntimeServices;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.CustomModelData;
import com.xm.thefourthfrequency.terminal.TerminalControlPolicy;
import com.xm.thefourthfrequency.terminal.SignalBand;
import com.xm.thefourthfrequency.terminal.TerminalSignalLog;
import com.xm.thefourthfrequency.terminal.TerminalTaskService;
import com.xm.thefourthfrequency.narrative.HiddenFilePolicy;
import com.xm.thefourthfrequency.narrative.TerminalFileState;
import com.xm.thefourthfrequency.world.SurvivalMilestone;
import com.xm.thefourthfrequency.world.SurvivalProgressService;

import java.util.List;
import java.util.UUID;

public final class TerminalData {
	public static final String SCHEMA_VERSION = "schema_version";
	public static final String TERMINAL_ID = "terminal_id";
	public static final String WORLD_ID = "world_id";
	public static final String COPY_GENERATION = "copy_generation";
	public static final String OWNER_ID = "owner_id";
	public static final String OWNER_NAME = "owner_name";
	public static final String ISSUED_GAME_TIME = "issued_game_time";
	public static final String PERSONALITY_SEED = "personality_seed";
	public static final String PERSONALITY_TEMPLATE = "personality_template";
	public static final String STOCK = "stock";
	public static final String BOUND = "bound";
	public static final String BAND_STAGE = "band_stage";
	public static final String PLOT_STAGE = "plot_stage";
	public static final String CACHE_VARIANT = "cache_variant";
	public static final String SECOND_CACHE_UNLOCKED = "second_cache_unlocked";
	public static final String SECOND_CACHE_VARIANT = "second_cache_variant";
	public static final String MINED_BLOCKS = "mined_blocks";
	public static final String PLACED_BLOCKS = "placed_blocks";
	public static final String DEVICE_INTERACTIONS = "device_interactions";
	public static final String CRAFTED_ITEMS = "crafted_items";
	public static final String LAST_CRAFTED_ITEM = "last_crafted_item";
	public static final String LAST_ACTIVITY = "last_activity";
	public static final String LAST_ACTIVITY_GAME_TIME = "last_activity_game_time";
	public static final String TARGET_KIND = "target_kind";
	public static final String TARGET_ITEM = "target_item";
	public static final String TARGET_BLOCK = "target_block";
	public static final String TARGET_LOCATED = "target_located";
	public static final String TARGET_POSITION = "target_position";
	public static final String TARGET_DIMENSION = "target_dimension";
	public static final String GUIDANCE_UPDATED_GAME_TIME = "guidance_updated_game_time";
	public static final String GUIDANCE_OBJECTIVE_ID = "guidance_objective_id";
	public static final String GUIDANCE_OBJECTIVE_PROGRESS = "guidance_objective_progress";
	public static final String GUIDANCE_STALLED_TICKS = "guidance_stalled_ticks";
	public static final String ACCEPTED_ADVICE = "accepted_advice";
	public static final String VISITED_DIMENSIONS = "visited_dimensions";
	public static final String RECOVERY_COUNT = "recovery_count";
	public static final String LOCAL_FILE_UNLOCKED = "local_file_unlocked";
	public static final String LOCAL_FILE_VERSION = "local_file_version";
	public static final String LOCAL_FILE_HASH = "local_file_hash";
	public static final String RIFT_LOCATED = "rift_located";
	public static final String RIFT_POSITION = "rift_position";
	public static final String RIFT_DIMENSION = "rift_dimension";
	public static final String RIFT_OBSERVED = "rift_observed";
	public static final String EMPTY_SEGMENT_ACTIVE = "empty_segment_active";
	public static final String EMPTY_SEGMENT_EVENT = "empty_segment_event";
	public static final String EMPTY_SEGMENT_RETURN_POS = "empty_segment_return_pos";
	public static final String EMPTY_SEGMENT_COUNT = "empty_segment_count";
	public static final String EMPTY_SEGMENT_GAP_FROM = "empty_segment_gap_from";
	public static final String EMPTY_SEGMENT_GAP_TO = "empty_segment_gap_to";
	public static final String PORTAL_TRANSITIONS = "portal_transitions";
	public static final String CONTINUITY_LEARNED = "continuity_learned";
	public static final String CONTINUITY_CONFIDENCE = "continuity_confidence";
	public static final String LAST_PORTAL_ORIGIN = "last_portal_origin";
	public static final String LAST_PORTAL_DESTINATION = "last_portal_destination";
	public static final String NETHER_RIFT_OBSERVED = "nether_rift_observed";
	public static final String BODY_PROGRESS = "body_progress";
	public static final String BODY_STAGE = "body_stage";
	public static final String BODY_PROGRESS_ANCHOR_TICK = "body_progress_anchor_tick";
	public static final String TERMINAL_CAPABILITIES = "terminal_capabilities";
	public static final String PRIVATE_ANOMALY_SEED = "private_anomaly_seed";
	public static final String PRIVATE_ANOMALY_VARIANT = "private_anomaly_variant";
	public static final String PRIVATE_ANOMALY_COUNT = "private_anomaly_count";
	public static final String PREFERRED_WEAPON = "preferred_weapon";
	public static final String WEAPON_SAMPLE_COUNT = "weapon_sample_count";
	public static final String ESCAPE_AXIS = "escape_axis";
	public static final String LAST_PATTERN_POSITION = "last_pattern_position";
	public static final String FOOD_USE_PHASE = "food_use_phase";
	public static final String ARMOR_SIGNATURE = "armor_signature";
	public static final String ARMOR_CHANGE_COUNT = "armor_change_count";
	public static final String MULTIPLAYER_ROLE = "multiplayer_role";
	public static final String TERMINAL_CAPTURED = "terminal_captured";
	public static final String TERMINAL_CAPTURED_TICK = "terminal_captured_tick";
	public static final String PURSUIT_GRACE_UNTIL = "pursuit_grace_until";
	public static final String ANOMALY_LOGS = "anomaly_logs";
	public static final String ANOMALY_LOG_SEQUENCE = "anomaly_log_sequence";
	public static final String UNREAD_ANOMALY_COUNT = "unread_anomaly_count";
	public static final String SIGNAL_EVENTS = "signal_events";
	public static final String SIGNAL_EVENT_SEQUENCE = "signal_event_sequence";
	public static final String UNREAD_SIGNAL_COUNT = "unread_signal_count";
	public static final String FILE_STATES = "file_states";
	public static final String ONLINE_SURVIVAL_TICKS = "online_survival_ticks";
	public static final String LAST_SIGNAL_WEATHER = "last_signal_weather";
	public static final String LAST_SIGNAL_DIMENSION = "last_signal_dimension";
	public static final String NEXT_AMBIENT_ANOMALY_TICK = "next_ambient_anomaly_tick";
	public static final String ANOMALY_TIER = "anomaly_tier";
	public static final String ANOMALY_STORY_CEILING = "anomaly_story_ceiling";
	public static final String ANOMALY_TIER_ONLINE_TICKS = "anomaly_tier_online_ticks";
	public static final String ANOMALY_HEAT = "anomaly_heat";
	public static final String ANOMALY_SEEN_MASK = "anomaly_seen_mask";
	public static final String ANOMALY_RECENT_IDS = "anomaly_recent_ids";
	public static final String ANOMALY_STAGE_SUCCESSES = "anomaly_stage_successes";
	public static final String NEXT_STRONG_ANOMALY_TICK = "next_strong_anomaly_tick";
	public static final String NEXT_COMPOSITE_ANOMALY_TICK = "next_composite_anomaly_tick";
	public static final String ANOMALY_LEGACY_RAMP = "anomaly_legacy_ramp";
	public static final String ANOMALY_LEGACY_RAMP_TICKS = "anomaly_legacy_ramp_ticks";
	public static final String ANOMALIES_SUSPENDED = "anomalies_suspended";
	public static final String ANOMALY_LEASES = "anomaly_leases";
	public static final String ANOMALY_PERMANENT_BLOCK_LOSS = "anomaly_permanent_block_loss";
	public static final String ANOMALY_PERMANENT_ITEM_LOSS = "anomaly_permanent_item_loss";
	public static final String ACTIVE_ANOMALY_ID = "active_anomaly_id";
	public static final String ACTIVE_ANOMALY_UNTIL = "active_anomaly_until";
	public static final String LAST_AMBIENT_DIMENSION = "last_ambient_dimension";
	public static final String DEBUG_ENABLED = "debug_enabled";
	public static final String CALIBRATED_BANDS_MASK = "calibrated_bands_mask";
	public static final String NIGHT_ENTERED = "night_entered";
	public static final String NIGHT_WITNESSED = "night_witnessed";
	public static final String PRELUDE_ANOMALY_MASK = "prelude_anomaly_mask";
	public static final String WATCHER_WITNESSED = "watcher_witnessed";
	public static final String PROOF_ROUTE = "proof_route";
	public static final String AUTO_TUNING = "auto_tuning";
	public static final String ACTIVE_GUIDANCE_TOOL = "active_guidance_tool";
	public static final String SELECTED_RESOURCE = "selected_resource";
	public static final String MINERAL_SCAN_READY_GAME_TIME = "mineral_scan_ready_game_time";
	public static final String TOOLS_DISABLED_UNTIL = "tools_disabled_until";
	public static final String HOME_POSITION = "home_position";
	public static final String HOME_DIMENSION = "home_dimension";
	public static final String LAST_PORTAL_POSITION = "last_portal_position";
	public static final String LAST_PORTAL_DIMENSION = "last_portal_dimension";
	public static final String EYE_SAMPLE_COUNT = "eye_sample_count";
	public static final String STRONGHOLD_POSITION = "stronghold_position";
	public static final String STRONGHOLD_DIMENSION = "stronghold_dimension";
	public static final String SURVIVAL_MILESTONE_MASK = "survival_milestone_mask";
	public static final String WOOD_MINED_COUNT = "wood_mined_count";
	public static final String IRON_SAMPLE_COUNT = "iron_sample_count";
	public static final String BLAZE_ROD_SAMPLE_COUNT = "blaze_rod_sample_count";
	public static final String CRAFTED_EYE_COUNT = "crafted_eye_count";
	public static final String TERMINAL_PAGE_VISIT_MASK = "terminal_page_visit_mask";
	public static final String TASK_REWARD_CLAIMED_MASK = "task_reward_claimed_mask";
	public static final String TASK_COMPLETION_NOTIFIED_MASK = "task_completion_notified_mask";
	public static final String UNREAD_ALERT_ACTIVE = "unread_alert_active";
	public static final String BREACH_MASK = "breach_mask";
	public static final String SIGNATURE_SCENE_MASK = "signature_scene_mask";
	public static final String TRUTH_READ = "truth_read";
	public static final String PORTAL_ROOM_FOUND = "portal_room_found";
	public static final String PORTAL_ROOM_POSITION = "portal_room_position";
	public static final String PORTAL_ROOM_DIMENSION = "portal_room_dimension";
	public static final String COMPLETED_STRUCTURE_TARGETS_MASK = "completed_structure_targets_mask";
	public static final String NAVIGATION_COMPLETION_ACTIVE = "navigation_completion_active";
	public static final String NAVIGATION_COMPLETION_UNREAD = "navigation_completion_unread";
	public static final String NAVIGATION_COMPLETION_POSITION = "navigation_completion_position";
	public static final String NAVIGATION_COMPLETION_DIMENSION = "navigation_completion_dimension";
	public static final String NAVIGATION_COMPLETION_DIRECTION = "navigation_completion_direction";
	public static final String PURSUIT_RESOLVED_CHASES = "pursuit_resolved_chases";
	public static final String PURSUIT_ALLOWED_FORM = "pursuit_allowed_form";
	public static final String PURSUIT_TUTORIAL_DEMO_MASK = "pursuit_tutorial_demo_mask";
	public static final String PURSUIT_TUTORIAL_WARNING_MASK = "pursuit_tutorial_warning_mask";
	public static final String PURSUIT_TUTORIAL_ARCHIVE_MASK = "pursuit_tutorial_archive_mask";
	public static final String PURSUIT_PENDING = "pursuit_pending";
	public static final String PURSUIT_NEXT_ELIGIBLE_TICK = "pursuit_next_eligible_tick";
	public static final String PURSUIT_EFFECTIVE_ACTIVITY_TICKS = "pursuit_effective_activity_ticks";
	public static final String PURSUIT_EXPLORATION_DISTANCE = "pursuit_exploration_distance";
	public static final String PURSUIT_ACTIVITY_PROOF_MASK = "pursuit_activity_proof_mask";
	public static final String PURSUIT_ACTIVE = "pursuit_active";
	public static final String PURSUIT_SESSION_ID = "pursuit_session_id";
	public static final String PURSUIT_SESSION_PHASE = "pursuit_session_phase";
	public static final String PURSUIT_SESSION_FORM = "pursuit_session_form";
	public static final String PURSUIT_SOURCE_DIMENSION = "pursuit_source_dimension";
	public static final String PURSUIT_SOURCE_POSITION = "pursuit_source_position";
	public static final String PURSUIT_SOURCE_YAW = "pursuit_source_yaw";
	public static final String PURSUIT_SOURCE_PITCH = "pursuit_source_pitch";
	public static final String PURSUIT_MIRROR_DIMENSION = "pursuit_mirror_dimension";
	public static final String PURSUIT_MIRROR_SLOT = "pursuit_mirror_slot";
	public static final String PURSUIT_SESSION_STARTED_TICK = "pursuit_session_started_tick";
	public static final String PURSUIT_REFUND_LEDGER = "pursuit_refund_ledger";
	public static final String PURSUIT_RECOVERY_QUEUE = "pursuit_recovery_queue";

	private TerminalData() {
	}

	public static ItemStack createFor(ServerPlayer player, int cacheVariant) {
		return stackFromRecord(createRecord(player, cacheVariant, "untracked"));
	}

	public static CompoundTag createRecord(ServerPlayer player, int cacheVariant, String worldId) {
		UUID ownerId = player.getUUID();
		CompoundTag tag = new CompoundTag();
		tag.putInt(SCHEMA_VERSION, RuntimeServices.PERSISTENCE_SCHEMA_VERSION);
		tag.putString(TERMINAL_ID, UUID.randomUUID().toString());
		tag.putString(WORLD_ID, worldId);
		tag.putInt(COPY_GENERATION, 0);
		tag.putString(OWNER_ID, ownerId.toString());
		tag.putString(OWNER_NAME, player.getGameProfile().name());
		tag.putLong(ISSUED_GAME_TIME, player.level().getGameTime());
		long personalitySeed = ownerId.getMostSignificantBits() ^ ownerId.getLeastSignificantBits();
		tag.putLong(PERSONALITY_SEED, personalitySeed);
		tag.putString(PERSONALITY_TEMPLATE, personalityTemplate(personalitySeed));
		tag.putInt(STOCK, 0);
		tag.putBoolean(BOUND, false);
		tag.putInt(BAND_STAGE, 0);
		tag.putInt(PLOT_STAGE, 1);
		tag.putInt(CACHE_VARIANT, Math.floorMod(cacheVariant, 4));
		tag.putBoolean(SECOND_CACHE_UNLOCKED, false);
		tag.putInt(SECOND_CACHE_VARIANT, Math.floorMod(cacheVariant + 1, 4));
		tag.putInt(MINED_BLOCKS, 0);
		tag.putInt(PLACED_BLOCKS, 0);
		tag.putInt(DEVICE_INTERACTIONS, 0);
		tag.putInt(CRAFTED_ITEMS, 0);
		tag.putString(LAST_CRAFTED_ITEM, "");
		tag.putString(LAST_ACTIVITY, "idle");
		tag.putLong(LAST_ACTIVITY_GAME_TIME, player.level().getGameTime());
		tag.putString(TARGET_KIND, "unresolved");
		tag.putString(TARGET_ITEM, "");
		tag.putString(TARGET_BLOCK, "");
		tag.putBoolean(TARGET_LOCATED, false);
		tag.putLong(TARGET_POSITION, 0L);
		tag.putString(TARGET_DIMENSION, "");
		tag.putLong(GUIDANCE_UPDATED_GAME_TIME, 0L);
		tag.putString(GUIDANCE_OBJECTIVE_ID, "");
		tag.putInt(GUIDANCE_OBJECTIVE_PROGRESS, 0);
		tag.putLong(GUIDANCE_STALLED_TICKS, 0L);
		tag.putString(ACCEPTED_ADVICE, "");
		tag.putString(VISITED_DIMENSIONS, player.level().dimension().identifier().toString());
		tag.putInt(RECOVERY_COUNT, 0);
		tag.putBoolean(LOCAL_FILE_UNLOCKED, false);
		tag.putInt(LOCAL_FILE_VERSION, 1);
		tag.putString(LOCAL_FILE_HASH, "TFF-WF-01-A91C");
		tag.putBoolean(RIFT_LOCATED, false);
		tag.putLong(RIFT_POSITION, 0L);
		tag.putString(RIFT_DIMENSION, "minecraft:overworld");
		tag.putBoolean(RIFT_OBSERVED, false);
		tag.putInt(PORTAL_TRANSITIONS, 0);
		tag.putBoolean(CONTINUITY_LEARNED, false);
		tag.putInt(CONTINUITY_CONFIDENCE, 0);
		tag.putString(LAST_PORTAL_ORIGIN, "");
		tag.putString(LAST_PORTAL_DESTINATION, "");
		tag.putBoolean(NETHER_RIFT_OBSERVED, false);
		tag.putInt(BODY_PROGRESS, 0);
		tag.putInt(BODY_STAGE, 0);
		tag.putLong(BODY_PROGRESS_ANCHOR_TICK, player.level().getGameTime());
		tag.putString(TERMINAL_CAPABILITIES,
				"environment;life;activity;resource_guidance;relation_inference");
		tag.putLong(PRIVATE_ANOMALY_SEED, personalitySeed ^ 0x5446464D364C4F4EL);
		tag.putInt(PRIVATE_ANOMALY_VARIANT, Math.floorMod(cacheVariant, 4));
		tag.putInt(PRIVATE_ANOMALY_COUNT, 0);
		tag.putString(PREFERRED_WEAPON, "");
		tag.putInt(WEAPON_SAMPLE_COUNT, 0);
		tag.putString(ESCAPE_AXIS, "none");
		tag.putLong(LAST_PATTERN_POSITION, player.blockPosition().asLong());
		tag.putInt(FOOD_USE_PHASE, -1);
		tag.putString(ARMOR_SIGNATURE, "");
		tag.putInt(ARMOR_CHANGE_COUNT, 0);
		tag.putString(MULTIPLAYER_ROLE, "unresolved");
		tag.putBoolean(TERMINAL_CAPTURED, false);
		tag.putLong(TERMINAL_CAPTURED_TICK, 0L);
		tag.putLong(PURSUIT_GRACE_UNTIL, 0L);
		tag.put(ANOMALY_LOGS, new ListTag());
		tag.putInt(ANOMALY_LOG_SEQUENCE, 0);
		tag.putInt(UNREAD_ANOMALY_COUNT, 0);
		tag.putLong(NEXT_AMBIENT_ANOMALY_TICK, 0L);
		tag.putInt(ANOMALY_TIER, 0);
		tag.putInt(ANOMALY_STORY_CEILING, 0);
		tag.putLong(ANOMALY_TIER_ONLINE_TICKS, 0L);
		tag.putInt(ANOMALY_HEAT, 0);
		tag.putLong(ANOMALY_SEEN_MASK, 0L);
		tag.put(ANOMALY_RECENT_IDS, new ListTag());
		tag.putInt(ANOMALY_STAGE_SUCCESSES, 0);
		tag.putLong(NEXT_STRONG_ANOMALY_TICK, 0L);
		tag.putLong(NEXT_COMPOSITE_ANOMALY_TICK, 0L);
		tag.putBoolean(ANOMALY_LEGACY_RAMP, false);
		tag.putLong(ANOMALY_LEGACY_RAMP_TICKS, 0L);
		tag.putBoolean(ANOMALIES_SUSPENDED, false);
		tag.put(ANOMALY_LEASES, new ListTag());
		tag.putInt(ANOMALY_PERMANENT_BLOCK_LOSS, 0);
		tag.putInt(ANOMALY_PERMANENT_ITEM_LOSS, 0);
		tag.putString(ACTIVE_ANOMALY_ID, "none");
		tag.putLong(ACTIVE_ANOMALY_UNTIL, 0L);
		tag.putString(LAST_AMBIENT_DIMENSION, player.level().dimension().identifier().toString());
		tag.putBoolean(DEBUG_ENABLED, false);
		tag.putInt(CALIBRATED_BANDS_MASK, 0);
		tag.putBoolean(NIGHT_ENTERED, false);
		tag.putBoolean(NIGHT_WITNESSED, false);
		tag.putInt(PRELUDE_ANOMALY_MASK, 0);
		tag.putBoolean(WATCHER_WITNESSED, false);
		tag.putString(PROOF_ROUTE, "none");
		tag.putBoolean(AUTO_TUNING, false);
		tag.putInt(ACTIVE_GUIDANCE_TOOL, 6);
		tag.putInt(SELECTED_RESOURCE, 3);
		tag.putLong(MINERAL_SCAN_READY_GAME_TIME, 0L);
		tag.putLong(TOOLS_DISABLED_UNTIL, 0L);
		tag.putLong(HOME_POSITION, 0L);
		tag.putString(HOME_DIMENSION, "");
		tag.putLong(LAST_PORTAL_POSITION, 0L);
		tag.putString(LAST_PORTAL_DIMENSION, "");
		tag.putInt(EYE_SAMPLE_COUNT, 0);
		tag.putLong(STRONGHOLD_POSITION, 0L);
		tag.putString(STRONGHOLD_DIMENSION, "");
		tag.putInt(SURVIVAL_MILESTONE_MASK, 0);
		tag.putInt(WOOD_MINED_COUNT, 0);
		tag.putInt(IRON_SAMPLE_COUNT, 0);
		tag.putInt(BLAZE_ROD_SAMPLE_COUNT, 0);
		tag.putInt(CRAFTED_EYE_COUNT, 0);
		tag.putInt(TERMINAL_PAGE_VISIT_MASK, 0);
		tag.putInt(TASK_REWARD_CLAIMED_MASK, 0);
		tag.putInt(TASK_COMPLETION_NOTIFIED_MASK, 0);
		tag.putBoolean(UNREAD_ALERT_ACTIVE, false);
		tag.putInt(BREACH_MASK, 0);
		tag.putInt(SIGNATURE_SCENE_MASK, 0);
		tag.putBoolean(TRUTH_READ, false);
		tag.putBoolean(PORTAL_ROOM_FOUND, false);
		tag.putLong(PORTAL_ROOM_POSITION, 0L);
		tag.putString(PORTAL_ROOM_DIMENSION, "");
		tag.putInt(COMPLETED_STRUCTURE_TARGETS_MASK, 0);
		tag.putBoolean(NAVIGATION_COMPLETION_ACTIVE, false);
		tag.putBoolean(NAVIGATION_COMPLETION_UNREAD, false);
		tag.putLong(NAVIGATION_COMPLETION_POSITION, 0L);
		tag.putString(NAVIGATION_COMPLETION_DIMENSION, "");
		tag.putInt(NAVIGATION_COMPLETION_DIRECTION, 0);
		tag.putInt(PURSUIT_RESOLVED_CHASES, 0);
		tag.putInt(PURSUIT_ALLOWED_FORM, 0);
		tag.putInt(PURSUIT_TUTORIAL_DEMO_MASK, 0);
		tag.putInt(PURSUIT_TUTORIAL_WARNING_MASK, 0);
		tag.putInt(PURSUIT_TUTORIAL_ARCHIVE_MASK, 0);
		tag.putBoolean(PURSUIT_PENDING, false);
		tag.putLong(PURSUIT_NEXT_ELIGIBLE_TICK, 0L);
		tag.putLong(PURSUIT_EFFECTIVE_ACTIVITY_TICKS, 0L);
		tag.putDouble(PURSUIT_EXPLORATION_DISTANCE, 0.0D);
		tag.putInt(PURSUIT_ACTIVITY_PROOF_MASK, 0);
		tag.putBoolean(PURSUIT_ACTIVE, false);
		tag.putString(PURSUIT_SESSION_ID, "");
		tag.putString(PURSUIT_SESSION_PHASE, "none");
		tag.putInt(PURSUIT_SESSION_FORM, 0);
		tag.putString(PURSUIT_SOURCE_DIMENSION, "");
		tag.putLong(PURSUIT_SOURCE_POSITION, 0L);
		tag.putDouble(PURSUIT_SOURCE_YAW, 0.0D);
		tag.putDouble(PURSUIT_SOURCE_PITCH, 0.0D);
		tag.putString(PURSUIT_MIRROR_DIMENSION, "");
		tag.putInt(PURSUIT_MIRROR_SLOT, -1);
		tag.putLong(PURSUIT_SESSION_STARTED_TICK, 0L);
		tag.put(PURSUIT_REFUND_LEDGER, new ListTag());
		tag.put(PURSUIT_RECOVERY_QUEUE, new ListTag());
		tag.put(SIGNAL_EVENTS, new ListTag());
		tag.putInt(SIGNAL_EVENT_SEQUENCE, 0);
		tag.putInt(UNREAD_SIGNAL_COUNT, 0);
		tag.put(FILE_STATES, new ListTag());
		TerminalFileState.discover(tag, HiddenFilePolicy.COMPLETE_FILE_ID,
				player.level().getGameTime(), player.level().getDayTime(), false);
		tag.putLong(ONLINE_SURVIVAL_TICKS, 0L);
		tag.putInt(LAST_SIGNAL_WEATHER, player.level().isThundering() ? 2 : player.level().isRaining() ? 1 : 0);
		tag.putString(LAST_SIGNAL_DIMENSION, player.level().dimension().identifier().toString());
		long now = player.level().getGameTime();
		long dayTime = player.level().getDayTime();
		TerminalSignalLog.append(tag, SignalBand.PUBLIC, "terminal_issued", now, dayTime,
				player.level().dimension().identifier().toString(), player.blockPosition().asLong(), 0, 0, false);
		return tag;
	}

	public static CompoundTag migrateRecord(CompoundTag source) {
		CompoundTag record = source.copy();
		int sourceSchema = Math.max(1, record.getIntOr(SCHEMA_VERSION, 1));
		if (!record.contains(ANOMALY_LOGS)) record.put(ANOMALY_LOGS, new ListTag());
		if (!record.contains(ANOMALY_LOG_SEQUENCE)) record.putInt(ANOMALY_LOG_SEQUENCE, 0);
		if (!record.contains(UNREAD_ANOMALY_COUNT)) record.putInt(UNREAD_ANOMALY_COUNT, 0);
		if (!record.contains(NEXT_AMBIENT_ANOMALY_TICK)) record.putLong(NEXT_AMBIENT_ANOMALY_TICK, 0L);
		if (!record.contains(ANOMALY_TIER)) record.putInt(ANOMALY_TIER,
				record.getBooleanOr(BOUND, false) ? 1 : 0);
		if (!record.contains(ANOMALY_STORY_CEILING)) record.putInt(ANOMALY_STORY_CEILING,
				record.getBooleanOr(BOUND, false) ? 1 : 0);
		if (!record.contains(ANOMALY_TIER_ONLINE_TICKS)) record.putLong(ANOMALY_TIER_ONLINE_TICKS, 0L);
		if (!record.contains(ANOMALY_HEAT)) record.putInt(ANOMALY_HEAT, 0);
		if (!record.contains(ANOMALY_SEEN_MASK)) record.putLong(ANOMALY_SEEN_MASK, 0L);
		if (!record.contains(ANOMALY_RECENT_IDS)) record.put(ANOMALY_RECENT_IDS, new ListTag());
		if (!record.contains(ANOMALY_STAGE_SUCCESSES)) record.putInt(ANOMALY_STAGE_SUCCESSES, 0);
		if (!record.contains(NEXT_STRONG_ANOMALY_TICK)) record.putLong(NEXT_STRONG_ANOMALY_TICK, 0L);
		if (!record.contains(NEXT_COMPOSITE_ANOMALY_TICK)) record.putLong(NEXT_COMPOSITE_ANOMALY_TICK, 0L);
		if (!record.contains(ANOMALY_LEGACY_RAMP)) record.putBoolean(ANOMALY_LEGACY_RAMP,
				sourceSchema < 4 && record.getBooleanOr(BOUND, false));
		if (!record.contains(ANOMALY_LEGACY_RAMP_TICKS)) record.putLong(ANOMALY_LEGACY_RAMP_TICKS, 0L);
		if (!record.contains(ANOMALIES_SUSPENDED)) record.putBoolean(ANOMALIES_SUSPENDED, false);
		if (!record.contains(ANOMALY_LEASES)) record.put(ANOMALY_LEASES, new ListTag());
		if (!record.contains(ANOMALY_PERMANENT_BLOCK_LOSS)) record.putInt(ANOMALY_PERMANENT_BLOCK_LOSS, 0);
		if (!record.contains(ANOMALY_PERMANENT_ITEM_LOSS)) record.putInt(ANOMALY_PERMANENT_ITEM_LOSS, 0);
		if (!record.contains(ACTIVE_ANOMALY_ID)) record.putString(ACTIVE_ANOMALY_ID, "none");
		if (!record.contains(ACTIVE_ANOMALY_UNTIL)) record.putLong(ACTIVE_ANOMALY_UNTIL, 0L);
		if (!record.contains(LAST_AMBIENT_DIMENSION)) record.putString(LAST_AMBIENT_DIMENSION, "");
		if (!record.contains(DEBUG_ENABLED)) record.putBoolean(DEBUG_ENABLED, false);
		if (!record.contains(CALIBRATED_BANDS_MASK)) record.putInt(CALIBRATED_BANDS_MASK,
				record.getBooleanOr(BOUND, false) ? 0b111 : 0);
		if (!record.contains(NIGHT_ENTERED)) record.putBoolean(NIGHT_ENTERED, record.getIntOr(BAND_STAGE, 0) > 0);
		if (!record.contains(NIGHT_WITNESSED)) record.putBoolean(NIGHT_WITNESSED, record.getIntOr(BAND_STAGE, 0) > 0);
		if (!record.contains(PRELUDE_ANOMALY_MASK)) record.putInt(PRELUDE_ANOMALY_MASK,
				record.getIntOr(BAND_STAGE, 0) > 0 ? 0b11 : 0);
		if (!record.contains(WATCHER_WITNESSED)) record.putBoolean(WATCHER_WITNESSED, record.getIntOr(BAND_STAGE, 0) > 0);
		if (!record.contains(PROOF_ROUTE)) record.putString(PROOF_ROUTE,
				record.getBooleanOr(BOUND, false) ? "legacy" : "none");
		if (!record.contains(AUTO_TUNING)) record.putBoolean(AUTO_TUNING, false);
		if (!record.contains(ACTIVE_GUIDANCE_TOOL)) record.putInt(ACTIVE_GUIDANCE_TOOL,
				isLegacyNavigation(record.getStringOr(TARGET_KIND, "unresolved")) ? 1 : 6);
		if (!record.contains(SELECTED_RESOURCE)) record.putInt(SELECTED_RESOURCE,
				resourceWire(record.getStringOr(TARGET_KIND, "unresolved")));
		if (!record.contains(MINERAL_SCAN_READY_GAME_TIME)) record.putLong(MINERAL_SCAN_READY_GAME_TIME, 0L);
		if (!record.contains(GUIDANCE_OBJECTIVE_ID)) record.putString(GUIDANCE_OBJECTIVE_ID, "");
		if (!record.contains(GUIDANCE_OBJECTIVE_PROGRESS)) record.putInt(GUIDANCE_OBJECTIVE_PROGRESS, 0);
		if (!record.contains(GUIDANCE_STALLED_TICKS)) record.putLong(GUIDANCE_STALLED_TICKS, 0L);
		if (!record.contains(TOOLS_DISABLED_UNTIL)) record.putLong(TOOLS_DISABLED_UNTIL, 0L);
		if (!record.contains(HOME_POSITION)) record.putLong(HOME_POSITION, 0L);
		if (!record.contains(HOME_DIMENSION)) record.putString(HOME_DIMENSION, "");
		if (!record.contains(LAST_PORTAL_POSITION)) record.putLong(LAST_PORTAL_POSITION, 0L);
		if (!record.contains(LAST_PORTAL_DIMENSION)) record.putString(LAST_PORTAL_DIMENSION, "");
		if (!record.contains(EYE_SAMPLE_COUNT)) record.putInt(EYE_SAMPLE_COUNT, 0);
		if (!record.contains(STRONGHOLD_POSITION)) record.putLong(STRONGHOLD_POSITION, 0L);
		if (!record.contains(STRONGHOLD_DIMENSION)) record.putString(STRONGHOLD_DIMENSION, "");
		if (!record.contains(SURVIVAL_MILESTONE_MASK)) record.putInt(SURVIVAL_MILESTONE_MASK,
				legacySurvivalMilestones(record));
		if (!record.contains(WOOD_MINED_COUNT)) record.putInt(WOOD_MINED_COUNT,
				record.getIntOr(SURVIVAL_MILESTONE_MASK, 0) == 0 ? 0 : 7);
		if (!record.contains(IRON_SAMPLE_COUNT)) record.putInt(IRON_SAMPLE_COUNT,
				SurvivalMilestone.IRON.present(record.getIntOr(SURVIVAL_MILESTONE_MASK, 0))
						? SurvivalProgressService.REQUIRED_IRON : 0);
		if (!record.contains(BLAZE_ROD_SAMPLE_COUNT)) record.putInt(BLAZE_ROD_SAMPLE_COUNT,
				(record.getIntOr(SURVIVAL_MILESTONE_MASK, 0) & (1 << 5 | 1 << 6 | 1 << 7)) == 0 ? 0 : 6);
		if (!record.contains(CRAFTED_EYE_COUNT)) record.putInt(CRAFTED_EYE_COUNT,
				SurvivalMilestone.CRAFTED_EYE.present(record.getIntOr(SURVIVAL_MILESTONE_MASK, 0))
						? SurvivalProgressService.REQUIRED_CRAFTED_EYES : 0);
		if (!record.contains(TERMINAL_PAGE_VISIT_MASK)) record.putInt(TERMINAL_PAGE_VISIT_MASK, 0);
		if (!record.contains(TASK_REWARD_CLAIMED_MASK)) record.putInt(TASK_REWARD_CLAIMED_MASK, 0);
		if (!record.contains(TASK_COMPLETION_NOTIFIED_MASK)) record.putInt(TASK_COMPLETION_NOTIFIED_MASK, 0);
		if (!record.contains(UNREAD_ALERT_ACTIVE)) record.putBoolean(UNREAD_ALERT_ACTIVE, false);
		if (!record.contains(BREACH_MASK)) record.putInt(BREACH_MASK, 0);
		if (!record.contains(SIGNATURE_SCENE_MASK)) record.putInt(SIGNATURE_SCENE_MASK, 0);
		if (!record.contains(TRUTH_READ)) record.putBoolean(TRUTH_READ,
				record.getBooleanOr(LOCAL_FILE_UNLOCKED, false));
		if (!record.contains(PORTAL_ROOM_FOUND)) record.putBoolean(PORTAL_ROOM_FOUND, false);
		if (!record.contains(PORTAL_ROOM_POSITION)) record.putLong(PORTAL_ROOM_POSITION, 0L);
		if (!record.contains(PORTAL_ROOM_DIMENSION)) record.putString(PORTAL_ROOM_DIMENSION, "");
		if (!record.contains(COMPLETED_STRUCTURE_TARGETS_MASK)) record.putInt(COMPLETED_STRUCTURE_TARGETS_MASK, 0);
		if (!record.contains(NAVIGATION_COMPLETION_ACTIVE)) record.putBoolean(NAVIGATION_COMPLETION_ACTIVE, false);
		if (!record.contains(NAVIGATION_COMPLETION_UNREAD)) record.putBoolean(NAVIGATION_COMPLETION_UNREAD, false);
		if (!record.contains(NAVIGATION_COMPLETION_POSITION)) record.putLong(NAVIGATION_COMPLETION_POSITION, 0L);
		if (!record.contains(NAVIGATION_COMPLETION_DIMENSION)) record.putString(NAVIGATION_COMPLETION_DIMENSION, "");
		if (!record.contains(NAVIGATION_COMPLETION_DIRECTION)) record.putInt(NAVIGATION_COMPLETION_DIRECTION, 0);
		if (!record.contains(PURSUIT_RESOLVED_CHASES)) record.putInt(PURSUIT_RESOLVED_CHASES, 0);
		if (!record.contains(PURSUIT_ALLOWED_FORM)) record.putInt(PURSUIT_ALLOWED_FORM, 0);
		if (!record.contains(PURSUIT_TUTORIAL_DEMO_MASK)) record.putInt(PURSUIT_TUTORIAL_DEMO_MASK, 0);
		if (!record.contains(PURSUIT_TUTORIAL_WARNING_MASK)) record.putInt(PURSUIT_TUTORIAL_WARNING_MASK, 0);
		if (!record.contains(PURSUIT_TUTORIAL_ARCHIVE_MASK)) record.putInt(PURSUIT_TUTORIAL_ARCHIVE_MASK, 0);
		if (!record.contains(PURSUIT_PENDING)) record.putBoolean(PURSUIT_PENDING, false);
		if (!record.contains(PURSUIT_NEXT_ELIGIBLE_TICK)) record.putLong(PURSUIT_NEXT_ELIGIBLE_TICK, 0L);
		if (!record.contains(PURSUIT_EFFECTIVE_ACTIVITY_TICKS)) record.putLong(PURSUIT_EFFECTIVE_ACTIVITY_TICKS, 0L);
		if (!record.contains(PURSUIT_EXPLORATION_DISTANCE)) record.putDouble(PURSUIT_EXPLORATION_DISTANCE, 0.0D);
		if (!record.contains(PURSUIT_ACTIVITY_PROOF_MASK)) record.putInt(PURSUIT_ACTIVITY_PROOF_MASK, 0);
		if (!record.contains(PURSUIT_ACTIVE)) record.putBoolean(PURSUIT_ACTIVE, false);
		if (!record.contains(PURSUIT_SESSION_ID)) record.putString(PURSUIT_SESSION_ID, "");
		if (!record.contains(PURSUIT_SESSION_PHASE)) record.putString(PURSUIT_SESSION_PHASE, "none");
		if (!record.contains(PURSUIT_SESSION_FORM)) record.putInt(PURSUIT_SESSION_FORM, 0);
		if (!record.contains(PURSUIT_SOURCE_DIMENSION)) record.putString(PURSUIT_SOURCE_DIMENSION, "");
		if (!record.contains(PURSUIT_SOURCE_POSITION)) record.putLong(PURSUIT_SOURCE_POSITION, 0L);
		if (!record.contains(PURSUIT_SOURCE_YAW)) record.putDouble(PURSUIT_SOURCE_YAW, 0.0D);
		if (!record.contains(PURSUIT_SOURCE_PITCH)) record.putDouble(PURSUIT_SOURCE_PITCH, 0.0D);
		if (!record.contains(PURSUIT_MIRROR_DIMENSION)) record.putString(PURSUIT_MIRROR_DIMENSION, "");
		if (!record.contains(PURSUIT_MIRROR_SLOT)) record.putInt(PURSUIT_MIRROR_SLOT, -1);
		if (!record.contains(PURSUIT_SESSION_STARTED_TICK)) record.putLong(PURSUIT_SESSION_STARTED_TICK, 0L);
		if (!record.contains(PURSUIT_REFUND_LEDGER)) record.put(PURSUIT_REFUND_LEDGER, new ListTag());
		if (!record.contains(PURSUIT_RECOVERY_QUEUE)) record.put(PURSUIT_RECOVERY_QUEUE, new ListTag());
		if (!record.contains(SIGNAL_EVENTS)) migrateLegacyAnomalyLogs(record);
		if (!record.contains(SIGNAL_EVENT_SEQUENCE)) record.putInt(SIGNAL_EVENT_SEQUENCE, 0);
		if (!record.contains(UNREAD_SIGNAL_COUNT))
			record.putInt(UNREAD_SIGNAL_COUNT, TerminalSignalLog.unreadCount(record));
		boolean legacyFiles = !record.contains(FILE_STATES);
		if (legacyFiles) record.put(FILE_STATES, new ListTag());
		if (!record.contains(ONLINE_SURVIVAL_TICKS)) record.putLong(ONLINE_SURVIVAL_TICKS,
				Math.max(0L, record.getLongOr(GUIDANCE_UPDATED_GAME_TIME, 0L)
						- record.getLongOr(ISSUED_GAME_TIME, 0L)));
		if (!record.contains(LAST_SIGNAL_WEATHER)) record.putInt(LAST_SIGNAL_WEATHER, -1);
		if (!record.contains(LAST_SIGNAL_DIMENSION)) record.putString(LAST_SIGNAL_DIMENSION,
				record.getStringOr(LAST_AMBIENT_DIMENSION, ""));
		if (legacyFiles) migrateLegacyFiles(record);
		boolean grandfatherUnlocked = sourceSchema < 7 && (record.getBooleanOr(LOCAL_FILE_UNLOCKED, false)
				|| TerminalFileState.unlocked(record, HiddenFilePolicy.COMPLETE_FILE_ID));
		long issued = Math.max(0L, record.getLongOr(ISSUED_GAME_TIME, 0L));
		TerminalFileState.discover(record, HiddenFilePolicy.COMPLETE_FILE_ID, issued,
				Math.floorMod(issued, 24_000L), grandfatherUnlocked);
		TerminalFileState.migrateReadState(record, grandfatherUnlocked);
		record.putInt(SCHEMA_VERSION, RuntimeServices.PERSISTENCE_SCHEMA_VERSION);
		return record;
	}

	private static boolean isLegacyNavigation(String kind) {
		return kind.equals("iron") || kind.equals("coal") || kind.equals("gold") || kind.equals("diamond");
	}

	private static int resourceWire(String kind) {
		return switch (kind) {
			case "iron" -> 0;
			case "coal" -> 4;
			case "gold" -> 5;
			case "diamond" -> 2;
			default -> 3;
		};
	}

	private static int legacySurvivalMilestones(CompoundTag record) {
		int mask = 0;
		int progress = Math.clamp(record.getIntOr(BODY_PROGRESS, 0), 0, 1000);
		if (record.getBooleanOr(BOUND, false) || progress >= 100) mask |= 1;
		if (record.getStringOr(ACCEPTED_ADVICE, "").contains("iron") || progress >= 250) mask |= 1 << 1;
		if (record.getBooleanOr(RIFT_OBSERVED, false) || progress >= 500) mask |= 1 << 2;
		if (record.getIntOr(PORTAL_TRANSITIONS, 0) > 0 || progress >= 500) mask |= 1 << 3;
		if (record.getIntOr(PORTAL_TRANSITIONS, 0) > 1 || progress >= 750) mask |= 1 << 4;
		return mask;
	}

	private static void migrateLegacyAnomalyLogs(CompoundTag record) {
		record.put(SIGNAL_EVENTS, new ListTag());
		record.putInt(SIGNAL_EVENT_SEQUENCE, 0);
		ListTag legacy = record.getListOrEmpty(ANOMALY_LOGS);
		for (int index = 0; index < legacy.size(); index++) {
			CompoundTag entry = legacy.getCompoundOrEmpty(index);
			long gameTime = Math.max(0L, entry.getLongOr("game_time", 0L));
			TerminalSignalLog.importLegacy(record, entry.getIntOr("sequence", 0), SignalBand.UNKNOWN,
					entry.getStringOr("type", "unknown"), gameTime, Math.floorMod(gameTime, 24_000L),
					entry.getStringOr("dimension", "minecraft:overworld"), entry.getLongOr("position", 0L),
					entry.getIntOr("variant", 0), entry.getIntOr("severity", 0),
					entry.getBooleanOr("unread", false));
		}
	}

	private static void migrateLegacyFiles(CompoundTag record) {
		long issued = Math.max(0L, record.getLongOr(ISSUED_GAME_TIME, 0L));
		long dayTime = Math.floorMod(issued, 24_000L);
		TerminalFileState.discover(record, "maintenance_handoff", issued, dayTime, true);
		if (record.getBooleanOr(SECOND_CACHE_UNLOCKED, false))
			TerminalFileState.discover(record, "recovered_fragment", issued, dayTime, true);
		if (record.getBooleanOr(LOCAL_FILE_UNLOCKED, false))
			TerminalFileState.discover(record, "encrypted_witness_file", issued, dayTime,
					record.getBooleanOr(LOCAL_FILE_UNLOCKED, false));
	}

	public static ItemStack stackFromRecord(CompoundTag record) {
		ItemStack stack = new ItemStack(ModItems.OLD_TERMINAL);
		applyProjection(stack, record);
		return stack;
	}

	public static boolean applyProjection(ItemStack stack, CompoundTag record) {
		boolean changed = false;
		CompoundTag projected = record.copy();
		CustomData currentData = stack.get(DataComponents.CUSTOM_DATA);
		if (currentData == null || !currentData.copyTag().equals(projected)) {
			stack.set(DataComponents.CUSTOM_DATA, CustomData.of(projected));
			changed = true;
		}
		return applyAttentionProjection(stack, record) || changed;
	}

	public static boolean applyAttentionProjection(ItemStack stack, CompoundTag record) {
		int stage = TerminalControlPolicy.pursuitVisualStage(
				record.getIntOr(PURSUIT_RESOLVED_CHASES, 0),
				record.getIntOr(PURSUIT_ALLOWED_FORM, 0),
				record.getIntOr(ANOMALY_TIER, 0));
		boolean unread = record.getIntOr(UNREAD_SIGNAL_COUNT, 0) > 0
				|| record.getBooleanOr(NAVIGATION_COMPLETION_UNREAD, false)
				|| TerminalTaskService.hasClaimableReward(record);
		CustomModelData model = new CustomModelData(List.of((float) (stage * 2 + (unread ? 1 : 0))),
				List.of(), List.of(), List.of());
		if (!model.equals(stack.get(DataComponents.CUSTOM_MODEL_DATA))) {
			stack.set(DataComponents.CUSTOM_MODEL_DATA, model);
			return true;
		}
		return false;
	}

	private static String personalityTemplate(long seed) {
		return switch (Math.floorMod(seed, 4)) {
			case 0 -> "cautious";
			case 1 -> "direct";
			case 2 -> "wry";
			default -> "clinical";
		};
	}

	public static CompoundTag copyTag(ItemStack stack) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		return data == null ? new CompoundTag() : data.copyTag();
	}

	public static int cacheVariant(ItemStack stack) {
		return copyTag(stack).getIntOr(CACHE_VARIANT, 0);
	}

	public static boolean isBound(ItemStack stack) {
		return copyTag(stack).getBooleanOr(BOUND, false);
	}

	public static int bandStage(ItemStack stack) {
		return copyTag(stack).getIntOr(BAND_STAGE, 0);
	}

	public static int copyGeneration(ItemStack stack) {
		return copyTag(stack).getIntOr(COPY_GENERATION, 0);
	}

	public static String terminalId(ItemStack stack) {
		return copyTag(stack).getStringOr(TERMINAL_ID, "");
	}

	public static String worldId(ItemStack stack) {
		return copyTag(stack).getStringOr(WORLD_ID, "");
	}

	public static String personalityTemplate(ItemStack stack) {
		return copyTag(stack).getStringOr(PERSONALITY_TEMPLATE, "clinical");
	}

	public static boolean secondCacheUnlocked(ItemStack stack) {
		return copyTag(stack).getBooleanOr(SECOND_CACHE_UNLOCKED, false);
	}

	public static int secondCacheVariant(ItemStack stack) {
		return copyTag(stack).getIntOr(SECOND_CACHE_VARIANT, Math.floorMod(cacheVariant(stack) + 1, 4));
	}

	public static boolean unlockSecondCache(ItemStack stack) {
		if (!stack.is(ModItems.OLD_TERMINAL) || secondCacheUnlocked(stack)) {
			return false;
		}
		CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putBoolean(SECOND_CACHE_UNLOCKED, true));
		return true;
	}

	public static boolean belongsTo(ItemStack stack, UUID ownerId) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		return stack.is(ModItems.OLD_TERMINAL)
				&& data != null
				&& ownerId.toString().equals(data.copyTag().getStringOr(OWNER_ID, ""));
	}
}
