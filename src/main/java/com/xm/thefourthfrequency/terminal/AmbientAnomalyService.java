package com.xm.thefourthfrequency.terminal;

import com.xm.thefourthfrequency.bootstrap.RuntimeServices;
import com.xm.thefourthfrequency.content.TerminalData;
import com.xm.thefourthfrequency.ending.EndingState;
import com.xm.thefourthfrequency.state.AnomalyState;
import com.xm.thefourthfrequency.state.StoryState;
import com.xm.thefourthfrequency.world.FrequencyWorldData;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/** Server-authoritative single-anomaly scheduler. The historical class name is retained for compatibility. */
public final class AmbientAnomalyService {
	public static final String[] TYPES = AnomalyCatalog.definitions().stream().map(AnomalyDefinition::id)
			.toArray(String[]::new);
	private static final int CHECK_INTERVAL = 20;
	private static boolean initialized;

	private AmbientAnomalyService() { }

	public static void initialize() {
		if (initialized) return;
		initialized = true;
		ServerTickEvents.END_SERVER_TICK.register(AmbientAnomalyService::tick);
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			ServerPlayer player = handler.getPlayer();
			FrequencyWorldData data = FrequencyWorldData.get(server);
			if (data.terminalRecord(player.getUUID()).isPresent()) data.updateTerminalRecord(player.getUUID(), tag -> {
				tag.putLong(TerminalData.NEXT_AMBIENT_ANOMALY_TICK, player.level().getGameTime() + 200L);
				tag.putString(TerminalData.LAST_AMBIENT_DIMENSION, player.level().dimension().identifier().toString());
			});
		});
	}

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % CHECK_INTERVAL != 0) return;
		FrequencyWorldData data = FrequencyWorldData.get(server);
		for (ServerPlayer player : server.getPlayerList().getPlayers()) updatePlayer(player, data);
	}

	private static void updatePlayer(ServerPlayer player, FrequencyWorldData data) {
		CompoundTag record = data.terminalRecord(player.getUUID()).orElse(null);
		if (record == null) return;
		StoryState story = StoryState.read(record);
		AnomalyState anomaly = AnomalyState.read(record);
		if (!story.bound() || !EndingState.activeAnomaliesAllowed(data) || anomaly.suspended()) return;
		long now = player.level().getGameTime();
		boolean endingActive = EndingState.endingPressureActive(data);
		int ceiling = AnomalyIntensity.survivalStoryCeiling(story.bound(), story.bandStage(),
				story.localFileUnlocked() || story.riftObserved(),
				story.continuityLearned() || record.getBooleanOr(TerminalData.NETHER_RIFT_OBSERVED, false),
				record.getIntOr(TerminalData.SURVIVAL_MILESTONE_MASK, 0), endingActive);
		int oldTier = anomaly.tier();
		boolean legacy = record.getBooleanOr(TerminalData.ANOMALY_LEGACY_RAMP, false);
		long legacyTicks = Math.max(0L, record.getLongOr(TerminalData.ANOMALY_LEGACY_RAMP_TICKS, 0L));
		int tier = legacy ? Math.min(ceiling, 1 + (int) (legacyTicks / AnomalyIntensity.LEGACY_TIER_RAMP_TICKS)) : ceiling;
		long tierTicks = tier == oldTier ? anomaly.tierOnlineTicks() + CHECK_INTERVAL : 0L;
		int heat = AnomalyIntensity.heatPercent(tierTicks);
		long storedLegacyTicks = legacyTicks + CHECK_INTERVAL;
		data.updateTerminalRecord(player.getUUID(), tag -> {
			new AnomalyState(tier, ceiling, tierTicks, heat, anomaly.nextAmbientTick(),
					anomaly.suspended(), anomaly.activeId(), anomaly.activeUntil()).writeTo(tag);
			if (legacy) tag.putLong(TerminalData.ANOMALY_LEGACY_RAMP_TICKS, storedLegacyTicks);
			if (legacy && tier >= ceiling) tag.putBoolean(TerminalData.ANOMALY_LEGACY_RAMP, false);
		});

		String dimension = player.level().dimension().identifier().toString();
		if (!dimension.equals(record.getStringOr(TerminalData.LAST_AMBIENT_DIMENSION, ""))) {
			data.updateTerminalRecord(player.getUUID(), tag -> {
				tag.putString(TerminalData.LAST_AMBIENT_DIMENSION, dimension);
				tag.putLong(TerminalData.NEXT_AMBIENT_ANOMALY_TICK, now + 200L);
			});
			return;
		}
		long next = anomaly.nextAmbientTick();
		if (next <= 0L || oldTier == 0 && tier >= 1) {
			schedule(data, player, now, Math.max(1, tier), heat, true);
			return;
		}
		if (now < next) return;
		if (!eligible(player, record)) {
			data.updateTerminalRecord(player.getUUID(), tag ->
					tag.putLong(TerminalData.NEXT_AMBIENT_ANOMALY_TICK, now + 200L));
			return;
		}
		triggerSelected(player, record, Math.max(1, tier), now);
		schedule(data, player, now, Math.max(1, tier), heat, false);
	}

	private static void triggerSelected(ServerPlayer player, CompoundTag record, int tier, long now) {
		boolean strongAllowed = tier >= 5 && now >= record.getLongOr(TerminalData.NEXT_STRONG_ANOMALY_TICK, 0L);
		List<AnomalyDefinition> candidates = AnomalyCatalog.unlocked(tier).stream()
				.filter(value -> strongAllowed || !value.strong()).toList();
		if (candidates.isEmpty()) return;
		long baseSeed = record.getLongOr(TerminalData.PERSONALITY_SEED, 0L) ^ now
				^ record.getIntOr(TerminalData.ANOMALY_LOG_SEQUENCE, 0);
		int start = Math.floorMod((int) baseSeed, candidates.size());
		for (int offset = 0; offset < candidates.size(); offset++) {
			AnomalyDefinition selected = candidates.get((start + offset) % candidates.size());
			long seed = baseSeed + offset * 0x9E3779B97F4A7C15L;
			if (!trigger(player, selected.id(), false, seed)) continue;
			if (selected.strong()) {
				FrequencyWorldData.get(player.level().getServer()).updateTerminalRecord(player.getUUID(), tag ->
						tag.putLong(TerminalData.NEXT_STRONG_ANOMALY_TICK,
								now + AnomalyIntensity.strongCooldownTicks(tier, (int) (seed >>> 32))));
			}
			return;
		}
	}

	public static boolean trigger(ServerPlayer player, String id, boolean maximum) {
		return triggerDetailed(player, id, maximum).started();
	}

	public static TriggerResult triggerDetailed(ServerPlayer player, String id, boolean maximum) {
		long seed = player.getUUID().getMostSignificantBits() ^ player.level().getGameTime() ^ id.hashCode();
		return attempt(player, id, maximum, seed, null);
	}

	private static boolean trigger(ServerPlayer player, String id, boolean maximum, long seed) {
		return attempt(player, id, maximum, seed, null).started();
	}

	/** Package-private by design: the GameTest source-set bridge is the only external caller. */
	static boolean triggerForGameTest(ServerPlayer player, String id, long seed, int acceleratedDurationTicks) {
		if (acceleratedDurationTicks < 4) throw new IllegalArgumentException("GameTest duration must render at least two frames");
		return attempt(player, id, false, seed, acceleratedDurationTicks).started();
	}

	private static TriggerResult attempt(ServerPlayer player, String id, boolean maximum, long seed,
			Integer durationOverride) {
		AnomalyDefinition definition = AnomalyCatalog.require(id);
		if (AnomalyRuntimeService.active(player) != null) return TriggerResult.rejected(TriggerFailure.ALREADY_ACTIVE);
		AnomalyConditions.Prepared prepared = AnomalyConditions.prepare(player, definition, seed);
		if (prepared == null) return TriggerResult.rejected(TriggerFailure.PRECONDITION_UNMET);
		int variant = maximum ? 7 : Math.floorMod((int) seed, 7);
		int duration = durationOverride == null ? AnomalyTiming.durationTicks(id, seed) : durationOverride;
		AnomalyServerEffects.EffectLease effect = AnomalyServerEffects.begin(player, definition, duration,
				seed, prepared.anchor());
		if (effect == null) return TriggerResult.rejected(TriggerFailure.EFFECT_UNAVAILABLE);
		boolean started = AnomalyRuntimeService.start(player, definition, variant, seed, duration,
				prepared.anchor(), effect::cleanup);
		if (!started) effect.cleanup();
		return started ? TriggerResult.STARTED : TriggerResult.rejected(TriggerFailure.RUNTIME_REJECTED);
	}

	public enum TriggerFailure {
		NONE,
		ALREADY_ACTIVE,
		PRECONDITION_UNMET,
		EFFECT_UNAVAILABLE,
		RUNTIME_REJECTED
	}

	public record TriggerResult(boolean started, TriggerFailure failure) {
		private static final TriggerResult STARTED = new TriggerResult(true, TriggerFailure.NONE);
		private static TriggerResult rejected(TriggerFailure failure) {
			return new TriggerResult(false, failure);
		}
	}

	public static void stop(ServerPlayer player) {
		FrequencyWorldData data = FrequencyWorldData.get(player.level().getServer());
		AnomalyRuntimeService.interrupt(player, true);
		data.updateTerminalRecord(player.getUUID(), tag -> AnomalyState.read(tag).suspended(true, 0L).writeTo(tag));
	}

	public static void resume(ServerPlayer player) {
		FrequencyWorldData data = FrequencyWorldData.get(player.level().getServer());
		data.updateTerminalRecord(player.getUUID(), tag -> AnomalyState.read(tag)
				.suspended(false, player.level().getGameTime() + 200L).writeTo(tag));
	}

	private static boolean eligible(ServerPlayer player, CompoundTag record) {
		return player.isAlive() && !player.isSpectator() && !player.isSleeping()
				&& !TerminalRuntimeService.isOpen(player)
				&& !record.getBooleanOr(TerminalData.EMPTY_SEGMENT_ACTIVE, false)
				&& AnomalyRuntimeService.active(player) == null;
	}

	private static void schedule(FrequencyWorldData data, ServerPlayer player, long now, int tier, int heat,
			boolean first) {
		long delay = RuntimeServices.config().pacing().developerAcceleration() ? (first ? 100L : 200L)
				: AnomalyIntensity.intervalTicks(tier, heat, player.getRandom().nextInt(), first);
		data.updateTerminalRecord(player.getUUID(), tag -> AnomalyState.read(tag).scheduled(now + delay).writeTo(tag));
	}

}
