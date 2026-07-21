package com.xm.thefourthfrequency.ending;

import com.xm.thefourthfrequency.audio.AudioService;
import com.xm.thefourthfrequency.bootstrap.RuntimeServices;
import com.xm.thefourthfrequency.content.TerminalData;
import com.xm.thefourthfrequency.entity.MisreadBodyEntity;
import com.xm.thefourthfrequency.mixin.EndDragonFightAccessor;
import com.xm.thefourthfrequency.terminal.SignalBand;
import com.xm.thefourthfrequency.terminal.TerminalRuntimeService;
import com.xm.thefourthfrequency.terminal.TerminalSignalService;
import com.xm.thefourthfrequency.world.FrequencyWorldData;
import com.xm.thefourthfrequency.world.SurvivalMilestone;
import com.xm.thefourthfrequency.world.SurvivalProgressService;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EndPortalFrameBlock;
import net.minecraft.world.level.dimension.end.EndDragonFight;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Server-authoritative End altar, stability-anchor and two-ending encounter. */
public final class EndBossEncounterService {
	private static final BlockPos ARENA_CENTER = new BlockPos(0, 64, 0);
	private static final AABB ARENA_BOUNDS = new AABB(-256.0, -128.0, -256.0, 256.0, 512.0, 256.0);
	private static final AABB DRAGON_BOUNDS = new AABB(-1_024.0, -128.0, -1_024.0, 1_024.0, 512.0, 1_024.0);
	private static final double PARTICIPATION_RADIUS_SQR = 192.0 * 192.0;
	private static final long NORMAL_DURATION_TICKS = 7_200L;
	private static final long ACCELERATED_DURATION_TICKS = 600L;
	private static final Map<MinecraftServer, ServerBossEvent> TIMER_EVENTS = new HashMap<>();
	private static boolean initialized;

	private EndBossEncounterService() {
	}

	public static void initialize() {
		if (initialized) return;
		initialized = true;
		ServerTickEvents.END_SERVER_TICK.register(EndBossEncounterService::tick);
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (!(player instanceof ServerPlayer serverPlayer) || !(level instanceof ServerLevel serverLevel)
					|| !player.getItemInHand(hand).is(Items.ENDER_EYE)) return InteractionResult.PASS;
			var state = level.getBlockState(hit.getBlockPos());
			if (!(state.getBlock() instanceof EndPortalFrameBlock)
					|| state.getValue(EndPortalFrameBlock.HAS_EYE)) return InteractionResult.PASS;
			FinalConfrontationService.findPortalRingNear(level, hit.getBlockPos(), 4)
					.filter(center -> FinalConfrontationService.eyeCount(level, center) == 11)
					.ifPresent(center -> recordAltarOpening(serverPlayer, serverLevel, center));
			return InteractionResult.PASS;
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			ServerBossEvent event = TIMER_EVENTS.remove(server);
			if (event != null) event.removeAllPlayers();
		});
	}

	private static void recordAltarOpening(ServerPlayer player, ServerLevel level, BlockPos center) {
		FrequencyWorldData data = FrequencyWorldData.get(level.getServer());
		var before = data.terminalRecord(player.getUUID()).orElse(null);
		if (before == null || before.getBooleanOr(TerminalData.ALTAR_STARTED, false)) return;
		data.updateTerminalRecord(player.getUUID(), record -> {
			record.putBoolean(TerminalData.ALTAR_STARTED, true);
			record.putBoolean(TerminalData.PORTAL_ROOM_FOUND, true);
			record.putLong(TerminalData.PORTAL_ROOM_POSITION, center.asLong());
			record.putString(TerminalData.PORTAL_ROOM_DIMENSION, level.dimension().identifier().toString());
			record.putInt(TerminalData.SURVIVAL_MILESTONE_MASK,
					record.getIntOr(TerminalData.SURVIVAL_MILESTONE_MASK, 0)
							| SurvivalMilestone.FOUND_STRONGHOLD.mask());
		});
		TerminalSignalService.record(player, SignalBand.UNKNOWN, "altar_started", 0, 2, true);
		player.displayClientMessage(Component.translatable("message.thefourthfrequency.end_altar.opened"), false);
		AudioService.play(level, center, AudioService.Cue.FOURTH_BAND);
	}

	private static void tick(MinecraftServer server) {
		ServerLevel level = server.getLevel(Level.END);
		if (level == null) return;
		FrequencyWorldData data = FrequencyWorldData.get(server);
		if (!EndingState.endBossEncounter(data)) {
			List<ServerPlayer> arrivals = currentParticipants(level);
			if (arrivals.isEmpty()) return;
			startEncounter(level, data, arrivals);
		}
		if (EndingState.endBossEncounter(data)) hideVanillaDragonBar(level);
		if (server.getTickCount() % 20 != 0) return;
		List<ServerPlayer> current = currentParticipants(level);

		freezeVanillaDragon(level);
		if (EndingState.endBossDefeated(data)) {
			completeVanillaDragonFight(level);
			clearTimer(server);
			return;
		}

		MisreadBodyEntity boss = findBoss(level, data).orElse(null);
		if (boss == null && !current.isEmpty()) {
			boss = spawnBoss(level);
			EndingState.updateEndBoss(data, boss.getUUID(), boss.blockPosition().asLong(),
					EndingState.endBossRemainingTicks(data), countStabilityAnchors(level),
					EndingState.endBossParticipantScale(data), current.stream().map(Entity::getUUID).toList());
		}
		if (boss == null) {
			synchronizeTimer(server, current, data);
			return;
		}

		for (ServerPlayer player : current) markParticipant(player, data);
		int previousAnchors = EndingState.get(data).getIntOr("anchors_remaining", -1);
		int anchors = nameAndCountStabilityAnchors(level);
		int participantScale = Math.max(EndingState.endBossParticipantScale(data), Math.max(1, current.size()));
		long remaining = EndingState.endBossRemainingTicks(data);
		if (EndingState.outcome(data) == EndingOutcome.ACTIVE && !current.isEmpty()) {
			remaining = Math.max(0L, remaining - 20L);
		}

		EndBossDifficulty.Profile profile = EndBossDifficulty.forEncounter(anchors, participantScale);
		boss.configureEndEncounter(profile);
		if (profile.healingPerSecond() > 0.0F && boss.isAlive()) boss.heal(profile.healingPerSecond());
		EndingState.updateEndBoss(data, boss.getUUID(), boss.blockPosition().asLong(), remaining, anchors,
				participantScale, current.stream().map(Entity::getUUID).toList());
		if (previousAnchors >= 0 && anchors != previousAnchors) anchorsChanged(server, data, anchors, previousAnchors);

		if (remaining <= 0L && EndingState.outcome(data) == EndingOutcome.ACTIVE) {
			EndingState.lockEndBossFailure(data, level.getGameTime());
			broadcast(server, "message.thefourthfrequency.end_boss.failure_locked");
			refreshOnline(server);
		}
		synchronizeTimer(server, current, data);
	}

	private static void startEncounter(ServerLevel level, FrequencyWorldData data, List<ServerPlayer> current) {
		FinalConfrontationService.retireLegacyAltar(level.getServer());
		freezeVanillaDragon(level);
		int anchors = nameAndCountStabilityAnchors(level);
		MisreadBodyEntity boss = spawnBoss(level);
		long duration = RuntimeServices.config().pacing().developerAcceleration()
				? ACCELERATED_DURATION_TICKS : NORMAL_DURATION_TICKS;
		EndingState.beginEndBoss(data, boss.getUUID(), boss.blockPosition().asLong(), level.getGameTime(), duration,
				anchors, current.stream().map(Entity::getUUID).toList());
		boss.configureEndEncounter(EndBossDifficulty.forEncounter(anchors, Math.max(1, current.size())));
		for (ServerPlayer player : current) markParticipant(player, data);
		broadcast(level.getServer(), "message.thefourthfrequency.end_boss.started", anchors);
		AudioService.play(level, boss.blockPosition(), AudioService.Cue.MISREAD_BODY);
		refreshOnline(level.getServer());
	}

	private static MisreadBodyEntity spawnBoss(ServerLevel level) {
		int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, 0, 12);
		return FinalConfrontationService.spawnBody(level, new BlockPos(0, Math.max(62, y), 12));
	}

	private static void markParticipant(ServerPlayer player, FrequencyWorldData data) {
		SurvivalProgressService.mark(player, SurvivalMilestone.ENTERED_END);
		if (data.terminalRecord(player.getUUID()).isEmpty()) return;
		data.updateTerminalRecord(player.getUUID(), record -> record.putBoolean(TerminalData.ALTAR_STARTED, true));
	}

	private static List<ServerPlayer> currentParticipants(ServerLevel level) {
		return level.players().stream().filter(player -> player.isAlive() && !player.isSpectator())
				.filter(player -> player.distanceToSqr(ARENA_CENTER.getCenter()) <= PARTICIPATION_RADIUS_SQR).toList();
	}

	public static boolean isEncounterBoss(MisreadBodyEntity body) {
		if (!(body.level() instanceof ServerLevel level) || level.dimension() != Level.END) return false;
		FrequencyWorldData data = FrequencyWorldData.get(level.getServer());
		if (!EndingState.endBossEncounter(data) || EndingState.endBossDefeated(data)) return false;
		String expected = EndingState.get(data).getStringOr("body_uuid", "");
		return expected.equals(body.getUUID().toString());
	}

	public static Optional<ServerPlayer> selectTarget(MisreadBodyEntity body) {
		if (!(body.level() instanceof ServerLevel level) || !isEncounterBoss(body)) return Optional.empty();
		return currentParticipants(level).stream().min(Comparator.comparingDouble(body::distanceToSqr));
	}

	private static Optional<MisreadBodyEntity> findBoss(ServerLevel level, FrequencyWorldData data) {
		String encoded = EndingState.get(data).getStringOr("body_uuid", "");
		if (!encoded.isBlank()) {
			try {
				Entity entity = level.getEntity(UUID.fromString(encoded));
				if (entity instanceof MisreadBodyEntity boss && boss.isAlive()) return Optional.of(boss);
			} catch (IllegalArgumentException ignored) {
				// Corrupt UUIDs are recovered by spawning a new authoritative body below.
			}
		}
		return level.getEntitiesOfClass(MisreadBodyEntity.class, ARENA_BOUNDS, Entity::isAlive).stream()
				.filter(EndBossEncounterService::isEncounterBoss).findFirst();
	}

	private static int nameAndCountStabilityAnchors(ServerLevel level) {
		List<EndCrystal> anchors = level.getEntitiesOfClass(EndCrystal.class, ARENA_BOUNDS, Entity::isAlive);
		for (EndCrystal anchor : anchors) {
			anchor.setCustomName(Component.translatable("entity.thefourthfrequency.stability_anchor"));
			anchor.setCustomNameVisible(true);
		}
		return anchors.size();
	}

	private static int countStabilityAnchors(ServerLevel level) {
		return level.getEntitiesOfClass(EndCrystal.class, ARENA_BOUNDS, Entity::isAlive).size();
	}

	private static void anchorsChanged(MinecraftServer server, FrequencyWorldData data, int after, int before) {
		for (UUID owner : data.terminalOwnerIds()) {
			data.updateTerminalRecord(owner, record -> record.putInt(TerminalData.GROUNDING_ANCHORS_REMAINING, after));
		}
		if (after < before) {
			broadcast(server, after == 0 ? "message.thefourthfrequency.end_boss.anchor_zero"
					: "message.thefourthfrequency.end_boss.anchor_lost", after);
		} else {
			broadcast(server, "message.thefourthfrequency.end_boss.anchor_restored", after);
		}
		refreshOnline(server);
	}

	private static void freezeVanillaDragon(ServerLevel level) {
		hideVanillaDragonBar(level);
		for (EnderDragon dragon : level.getEntitiesOfClass(EnderDragon.class, DRAGON_BOUNDS, Entity::isAlive)) {
			dragon.setNoAi(true);
			dragon.setSilent(true);
			dragon.setInvisible(true);
			dragon.setInvulnerable(true);
			dragon.setNoGravity(true);
			dragon.setDeltaMovement(Vec3.ZERO);
			dragon.snapTo(0.5, 300.0, 0.5, 0.0F, 0.0F);
		}
	}

	private static void hideVanillaDragonBar(ServerLevel level) {
		EndDragonFight fight = level.getDragonFight();
		if (fight == null) return;
		ServerBossEvent event = ((EndDragonFightAccessor) fight).thefourthfrequency$dragonEvent();
		event.setVisible(false);
		event.removeAllPlayers();
	}

	private static void completeVanillaDragonFight(ServerLevel level) {
		EndDragonFight fight = level.getDragonFight();
		if (fight == null) return;
		List<EnderDragon> dragons = new ArrayList<>(
				level.getEntitiesOfClass(EnderDragon.class, DRAGON_BOUNDS, entity -> true));
		if (!fight.hasPreviouslyKilledDragon()) {
			UUID expected = fight.getDragonUUID();
			EnderDragon token = null;
			if (expected != null) {
				for (EnderDragon dragon : dragons) {
					if (expected.equals(dragon.getUUID())) {
						token = dragon;
						break;
					}
				}
			}
			if (token == null) {
				token = EntityType.ENDER_DRAGON.create(level, EntitySpawnReason.EVENT);
				if (token == null) return;
				if (expected == null) {
					expected = token.getUUID();
					((EndDragonFightAccessor) fight).thefourthfrequency$setDragonUuid(expected);
				}
				token.setUUID(expected);
			}
			fight.setDragonKilled(token);
		}
		for (EnderDragon dragon : dragons) dragon.discard();
	}

	public static void onBossDefeated(MisreadBodyEntity body, ServerPlayer victor) {
		if (!(body.level() instanceof ServerLevel level) || !isEncounterBoss(body)) return;
		FrequencyWorldData data = FrequencyWorldData.get(level.getServer());
		EndingOutcome outcome = EndingState.completeEndBoss(data, level.getGameTime());
		Set<UUID> completed = new HashSet<>(EndingState.endBossParticipants(data));
		if (victor != null) completed.add(victor.getUUID());
		for (UUID owner : completed) {
			if (data.terminalRecord(owner).isEmpty()) continue;
			data.updateTerminalRecord(owner, record -> record.putInt(TerminalData.SURVIVAL_MILESTONE_MASK,
					record.getIntOr(TerminalData.SURVIVAL_MILESTONE_MASK, 0) | SurvivalMilestone.DEFEATED_BOSS.mask()));
		}
		completeVanillaDragonFight(level);
		clearTimer(level.getServer());
		AudioService.play(level, body.blockPosition(), AudioService.Cue.TERMINATION);
		broadcast(level.getServer(), outcome == EndingOutcome.SUCCESS
				? "message.thefourthfrequency.end_boss.success"
				: "message.thefourthfrequency.end_boss.defeated_after_failure");
		refreshOnline(level.getServer());
	}

	private static void synchronizeTimer(MinecraftServer server, List<ServerPlayer> current, FrequencyWorldData data) {
		ServerBossEvent event = TIMER_EVENTS.computeIfAbsent(server, ignored -> new ServerBossEvent(
				Component.translatable("bossbar.thefourthfrequency.intervention_timer", "6:00"),
				BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.PROGRESS));
		Set<ServerPlayer> wanted = new HashSet<>(current);
		for (ServerPlayer player : List.copyOf(event.getPlayers())) if (!wanted.contains(player)) event.removePlayer(player);
		for (ServerPlayer player : wanted) if (!event.getPlayers().contains(player)) event.addPlayer(player);
		long duration = Math.max(1L, EndingState.get(data).getLongOr("duration_ticks", NORMAL_DURATION_TICKS));
		long remaining = EndingState.endBossRemainingTicks(data);
		if (EndingState.outcome(data) == EndingOutcome.FAILED) {
			event.setName(Component.translatable("bossbar.thefourthfrequency.intervention_failed"));
			event.setProgress(0.0F);
		} else {
			long seconds = (remaining + 19L) / 20L;
			String clock = String.format(Locale.ROOT, "%d:%02d", seconds / 60L, seconds % 60L);
			event.setName(Component.translatable("bossbar.thefourthfrequency.intervention_timer", clock));
			event.setProgress(Math.clamp((float) remaining / duration, 0.0F, 1.0F));
		}
		event.setVisible(!current.isEmpty());
	}

	private static void clearTimer(MinecraftServer server) {
		ServerBossEvent event = TIMER_EVENTS.remove(server);
		if (event != null) event.removeAllPlayers();
	}

	private static void broadcast(MinecraftServer server, String key, Object... arguments) {
		Component message = Component.translatable(key, arguments);
		for (ServerPlayer player : server.getPlayerList().getPlayers()) player.displayClientMessage(message, false);
	}

	private static void refreshOnline(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (FrequencyWorldData.get(server).terminalRecord(player.getUUID()).isPresent()) {
				TerminalRuntimeService.synchronizeProjection(player);
				TerminalRuntimeService.refresh(player);
			}
		}
	}
}
