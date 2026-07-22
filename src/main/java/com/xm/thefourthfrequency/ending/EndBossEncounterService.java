package com.xm.thefourthfrequency.ending;

import com.xm.thefourthfrequency.audio.AudioService;
import com.xm.thefourthfrequency.audio.ModSounds;
import com.xm.thefourthfrequency.content.ModBlocks;
import com.xm.thefourthfrequency.content.ModEntities;
import com.xm.thefourthfrequency.content.ResonanceCoreBlockEntity;
import com.xm.thefourthfrequency.content.TerminalData;
import com.xm.thefourthfrequency.entity.MisreadBodyEntity;
import com.xm.thefourthfrequency.entity.WorldInterfaceEntity;
import com.xm.thefourthfrequency.entity.WorldInterfacePartEntity;
import com.xm.thefourthfrequency.networking.AltarActionC2S;
import com.xm.thefourthfrequency.networking.AltarSnapshotS2C;
import com.xm.thefourthfrequency.networking.BossActionS2C;
import com.xm.thefourthfrequency.networking.PoemCompleteC2S;
import com.xm.thefourthfrequency.networking.PoemStartS2C;
import com.xm.thefourthfrequency.networking.WorldInterfaceProtocol;
import com.xm.thefourthfrequency.networking.WorldInterfaceSnapshotS2C;
import com.xm.thefourthfrequency.terminal.SignalBand;
import com.xm.thefourthfrequency.terminal.TerminalSignalService;
import com.xm.thefourthfrequency.world.FrequencyWorldData;
import com.xm.thefourthfrequency.world.SurvivalMilestone;
import com.xm.thefourthfrequency.world.SurvivalProgressService;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * Authoritative orchestrator for the dedicated final boss, its ritual and both endings.
 *
 * <p>The old End-specific {@link MisreadBodyEntity} path is intentionally retired.  This class is
 * the only owner of encounter stage transitions, the virtual health pool and the collapse clock;
 * entities, blocks and packets are projections or narrowly validated inputs.</p>
 */
public final class EndBossEncounterService {
	private static final UUID NIL_UUID = new UUID(0L, 0L);
	private static final int SUMMON_DURATION_TICKS = 100;
	private static final int RESOLUTION_DURATION_TICKS = 120;
	private static final int SNAPSHOT_INTERVAL_TICKS = 10;
	private static final int HEAL_INTERVAL_TICKS = 20;
	private static final int MAX_MUTATION_RETRIES = 5;
	private static final double ENCOUNTER_VISIBILITY_RADIUS_SQR = 256.0D * 256.0D;
	private static final String PART_TAG_PREFIX = "thefourthfrequency.world_interface_part.";

	private static final Map<MinecraftServer, Map<UUID, Long>> CLIENT_SEQUENCES =
			Collections.synchronizedMap(new WeakHashMap<>());
	private static final Map<MinecraftServer, Set<UUID>> ALTAR_VIEWERS =
			Collections.synchronizedMap(new WeakHashMap<>());
	private static final Map<MinecraftServer, Map<UUID, Long>> PROJECTILE_HITS =
			Collections.synchronizedMap(new WeakHashMap<>());
	private static final Map<MinecraftServer, Map<UUID, Long>> MELEE_HITS =
			Collections.synchronizedMap(new WeakHashMap<>());
	private static final Set<MinecraftServer> RECOVERED_SERVERS =
			Collections.newSetFromMap(new WeakHashMap<>());
	private static boolean initialized;

	private EndBossEncounterService() {
	}

	public static void initialize() {
		if (initialized) return;
		initialized = true;
		WorldInterfaceRitualService.registerAltarOpenHandler((player, position) -> openAltar(player));
		ServerTickEvents.START_SERVER_TICK.register(EndBossEncounterService::tickStart);
		ServerLifecycleEvents.SERVER_STARTED.register(EndBossEncounterService::recoverAfterRestart);
		ServerLifecycleEvents.SERVER_STOPPING.register(EndBossEncounterService::pauseForStop);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			CLIENT_SEQUENCES.remove(server);
			ALTAR_VIEWERS.remove(server);
			PROJECTILE_HITS.remove(server);
			MELEE_HITS.remove(server);
			RECOVERED_SERVERS.remove(server);
		});
		ServerPlayerEvents.JOIN.register(EndBossEncounterService::reconcilePlayer);
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
			WorldInterfaceState.snapshot(newPlayer.level().getServer()).encounterId()
					.ifPresent(id -> WorldInterfaceAttackService.onDeath(newPlayer, id));
			reconcilePlayer(newPlayer);
		});
	}

	/** Called only after vanilla successfully places the twelfth eye. */
	public static void prepareFromActivatedPortal(ServerLevel sourceLevel, BlockPos portalCenter,
			ServerPlayer activator) {
		if (sourceLevel.dimension() == Level.END) return;
		MinecraftServer server = sourceLevel.getServer();
		ServerLevel end = server.getLevel(Level.END);
		if (end == null) return;
		FinalConfrontationService.retireLegacyAltar(server);

		WorldInterfaceState.Snapshot existing = WorldInterfaceState.snapshot(server);
		if (!existing.valid()) {
			WorldInterfaceState.clearInvalid(server);
			existing = WorldInterfaceState.snapshot(server);
		}
		if (!existing.present()) {
			EndBossArenaService.PreparedArena prepared = EndBossArenaService.prepare(end);
			WorldInterfaceState.ArenaLayout layout = new WorldInterfaceState.ArenaLayout(1,
					Level.END.identifier().toString(), prepared.center(), prepared.altar(), prepared.safeSpawn(),
					indexedGates(prepared), indexedAnchors(prepared));
			UUID encounterId = UUID.randomUUID();
			long seed = end.getSeed() ^ encounterId.getMostSignificantBits()
					^ encounterId.getLeastSignificantBits() ^ end.getGameTime();
			WorldInterfaceState.MutationResult initializedState =
					WorldInterfaceState.initialize(server, encounterId, layout, seed);
			if (!initializedState.applied()) return;
			existing = initializedState.snapshot();
		}
		if (existing.stage() == WorldInterfaceStage.ARENA_READY) {
			WorldInterfaceState.transition(server, existing.encounterId().orElseThrow(), existing.revision(),
					WorldInterfaceStage.ARENA_READY, WorldInterfaceStage.WAITING_TERMINALS);
		}
		bindCore(end, WorldInterfaceState.snapshot(server));
		recordAltarOpening(activator, sourceLevel, portalCenter);
		AudioService.playBounded(sourceLevel, portalCenter, ModSounds.WORLD_INTERFACE_ALTAR,
				SoundSource.AMBIENT, 0.82F, 1.0F);
	}

	/** Overrides only End-portal travel while a prepared world-interface encounter exists. */
	public static Optional<TeleportTransition> createPortalTransition(ServerLevel sourceLevel, Entity entity,
			BlockPos portalPosition) {
		if (sourceLevel.dimension() == Level.END || !(entity instanceof ServerPlayer player)
				|| player.isSpectator()) return Optional.empty();
		MinecraftServer server = sourceLevel.getServer();
		WorldInterfaceState.Snapshot snapshot = WorldInterfaceState.snapshot(server);
		if (!snapshot.valid() || !snapshot.present() || snapshot.stage() == WorldInterfaceStage.COMPLETE) {
			return Optional.empty();
		}
		ServerLevel end = server.getLevel(Level.END);
		if (end == null) return Optional.empty();
		if (!rememberAndOverrideRespawn(player, snapshot)) return Optional.empty();
		SurvivalProgressService.mark(player, SurvivalMilestone.ENTERED_END);
		Vec3 arrival = Vec3.atBottomCenterOf(snapshot.safeSpawn());
		return Optional.of(new TeleportTransition(end, arrival, Vec3.ZERO, 180.0F, 0.0F,
				TeleportTransition.PLAY_PORTAL_SOUND.then(TeleportTransition.PLACE_PORTAL_TICKET)));
	}

	/** Entity AI callback: movement and rendering projection only; attacks remain server-tick owned. */
	public static void tickBossEntity(ServerLevel level, WorldInterfaceEntity boss) {
		WorldInterfaceState.Snapshot snapshot = WorldInterfaceState.snapshot(level.getServer());
		if (!snapshot.valid() || !snapshot.present()
				|| snapshot.encounterId().filter(id -> id.equals(boss.encounterId())).isEmpty()
				|| snapshot.bossUuid().filter(id -> id.equals(boss.getUUID())).isEmpty()) {
			boss.discard();
			return;
		}
		if (snapshot.stage().wireId() >= WorldInterfaceStage.PORTAL_OPEN.wireId()) {
			boss.discard();
			return;
		}
		int form = formForStage(snapshot.stage());
		boss.setForm(form);
		if (snapshot.stage().isResolution() || snapshot.stage() == WorldInterfaceStage.PORTAL_OPEN) {
			boss.setDeltaMovement(Vec3.ZERO);
			return;
		}
		if (!snapshot.stage().isCombat()) {
			hoverAt(boss, snapshot.arenaCenter(), 18.0D, level.getGameTime());
			return;
		}
		if (snapshot.stage() == WorldInterfaceStage.PHASE_3) {
			hoverAt(boss, snapshot.arenaCenter(), 34.0D, level.getGameTime());
			return;
		}
		ServerPlayer target = nearestArenaParticipant(level, snapshot, boss.position()).orElse(null);
		if (target == null) {
			hoverAt(boss, snapshot.arenaCenter(), snapshot.stage() == WorldInterfaceStage.PHASE_2 ? 25.0D : 18.0D,
					level.getGameTime());
			return;
		}
		double movement = 0.11D * WorldInterfacePolicy.movementMultiplier(snapshot.destroyedAnchorCount());
		Vec3 desired = target.position().add(0.0D,
				snapshot.stage() == WorldInterfaceStage.PHASE_2 ? 13.0D : 9.0D, 0.0D);
		Vec3 delta = desired.subtract(boss.position());
		boss.setDeltaMovement(delta.lengthSqr() < 1.0D ? Vec3.ZERO : delta.normalize().scale(movement));
	}

	/** Routes an accepted player-authored hit into the persisted virtual health pool. */
	public static boolean applyVirtualDamage(WorldInterfaceEntity boss, DamageSource source, float rawAmount) {
		if (!(boss.level() instanceof ServerLevel level) || rawAmount <= 0.0F || !Float.isFinite(rawAmount)
				|| !(source.getEntity() instanceof ServerPlayer attacker)) return false;
		MinecraftServer server = level.getServer();
		Entity direct = source.getDirectEntity();
		if (direct instanceof AbstractArrow arrow
				&& WorldInterfaceAttackService.captureArrow(level, boss, arrow)) return false;
		if (direct != null && direct != attacker && duplicateProjectileHit(server, direct.getUUID(), level.getGameTime())) {
			return false;
		}
		boolean directMelee = direct == null || direct == attacker;
		boolean meleeClaimed = false;

		for (int attempt = 0; attempt < MAX_MUTATION_RETRIES; attempt++) {
			WorldInterfaceState.Snapshot before = WorldInterfaceState.snapshot(server);
			if (!bossMatches(before, boss) || !before.stage().isCombat()
					|| attacker.isSpectator()) return false;
			long elapsed = effectiveActiveTicks(before, level.getGameTime());
			if (WorldInterfacePolicy.resolveTick(elapsed, before.destroyedAnchorCount(), false)
					== WorldInterfacePolicy.TickVerdict.FAILURE) {
				lockFailure(level, before);
				return false;
			}
			if (directMelee && !meleeClaimed) {
				if (duplicateMeleeHit(server, attacker.getUUID(), level.getGameTime())) return false;
				meleeClaimed = true;
			}
			double adjusted = WorldInterfacePolicy.adjustedIncomingDamage(rawAmount,
					before.destroyedAnchorCount());
			double remaining = Math.max(0.0D, before.virtualHealth() - adjusted);
			boolean lethal = remaining <= 0.0D;
			WorldInterfaceState.MutationResult result = WorldInterfaceState.mutate(server,
					before.encounterId().orElseThrow(), before.revision(), state -> {
						long nowElapsed = effectiveActiveTicks(before, level.getGameTime());
						state.setClock(nowElapsed, before.runningSinceGameTime() >= 0L
								? level.getGameTime() : -1L, before.anchorPenaltyTicks());
						state.setVirtualHealth(before.maxVirtualHealth(), remaining);
						advanceToHealthStage(state, remaining / Math.max(1.0D, before.maxVirtualHealth()));
						if (lethal) {
							state.setClock(nowElapsed, -1L, before.anchorPenaltyTicks());
							advanceToPhaseThree(state);
							state.clearCurrentAttack();
							state.clearControlCooldowns();
							state.setGateState(WorldInterfaceGatewayState.GOLD);
							state.transitionTo(WorldInterfaceStage.SUCCESS_RESOLUTION);
							state.setResolution(0, level.getGameTime());
						}
					});
			if (!result.applied()) {
				if ("revision_mismatch".equals(result.reason())) continue;
				return false;
			}
			WorldInterfaceState.Snapshot after = result.snapshot();
			if (lethal) beginResolution(level, after, true);
			else if (after.stage() != before.stage()) phaseChanged(level, before, after);
			return true;
		}
		return false;
	}

	/** Returns empty for ordinary crystals so vanilla remains untouched. */
	public static Optional<Boolean> handleAnchorDamage(ServerLevel level, EndCrystal crystal,
			DamageSource source, float amount) {
		WorldInterfaceState.Snapshot before = WorldInterfaceState.snapshot(level.getServer());
		if (!before.valid() || !before.present()) return Optional.empty();
		WorldInterfaceState.Anchor anchor = before.anchorForCrystal(crystal.getUUID()).orElse(null);
		if (anchor == null) return Optional.empty();
		if (amount <= 0.0F || !Float.isFinite(amount) || !before.stage().isCombat()
				|| !(source.getEntity() instanceof ServerPlayer player)
				|| player.isSpectator()) return Optional.of(false);
		if (anchor.destroyed()) {
			crystal.discard();
			return Optional.of(true);
		}

		for (int attempt = 0; attempt < MAX_MUTATION_RETRIES; attempt++) {
			before = WorldInterfaceState.snapshot(level.getServer());
			anchor = before.anchorForCrystal(crystal.getUUID()).orElse(null);
			if (anchor == null) return Optional.empty();
			if (!before.stage().isCombat()) return Optional.of(false);
			if (anchor.destroyed()) {
				crystal.discard();
				return Optional.of(true);
			}
			int index = anchor.index();
			WorldInterfaceState.MutationResult result = WorldInterfaceState.mutate(level.getServer(),
					before.encounterId().orElseThrow(), before.revision(), state -> state.markAnchorDestroyed(index));
			if (!result.applied()) {
				if ("revision_mismatch".equals(result.reason())) continue;
				return Optional.of(false);
			}
			crystal.discard();
			level.sendParticles(ParticleTypes.REVERSE_PORTAL, anchor.position().getX() + 0.5D,
					anchor.position().getY() + 0.5D, anchor.position().getZ() + 0.5D,
					72, 1.7D, 2.8D, 1.7D, 0.16D);
			AudioService.playBounded(level, anchor.position(), ModSounds.WORLD_INTERFACE_ANCHOR,
					SoundSource.HOSTILE, 0.9F, 0.72F);
			broadcast(level.getServer(), "message.thefourthfrequency.world_interface.anchor_destroyed",
					result.snapshot().aliveAnchorCount());
			if (WorldInterfacePolicy.hasTimedOut(effectiveActiveTicks(result.snapshot(), level.getGameTime()),
					result.snapshot().destroyedAnchorCount())) lockFailure(level, result.snapshot());
			sendEncounterSnapshots(level.getServer(), true);
			return Optional.of(true);
		}
		return Optional.of(false);
	}

	public static void handleAltarAction(ServerPlayer player, AltarActionC2S payload) {
		WorldInterfaceRitualService.RitualResult result = switch (payload.action()) {
			case DEPOSIT -> WorldInterfaceRitualService.deposit(player, payload.encounterId(), payload.expectedRevision());
			case WITHDRAW -> WorldInterfaceRitualService.withdraw(player, payload.encounterId(), payload.expectedRevision());
			case CANCEL -> WorldInterfaceRitualService.cancel(player, payload.encounterId(), payload.expectedRevision());
		};
		if (result.applied() && payload.action() == WorldInterfaceProtocol.AltarAction.DEPOSIT) {
			ServerLevel end = player.level().getServer().getLevel(Level.END);
			if (end != null) {
				for (BlockPos gate : result.snapshot().gates().stream().map(WorldInterfaceState.Gate::position).toList()) {
					end.sendParticles(ParticleTypes.PORTAL, gate.getX() + 0.5D, gate.getY() + 0.5D,
							gate.getZ() + 0.5D, 12, 0.7D, 2.0D, 0.7D, 0.05D);
				}
				AudioService.playBounded(end, result.snapshot().altarCenter(), ModSounds.WORLD_INTERFACE_TERMINAL,
						SoundSource.AMBIENT, 0.85F, result.snapshot().sacrificeCommitted() ? 0.72F : 1.0F);
			}
		}
		sendAltarSnapshots(player.level().getServer(), result.snapshot(), altarStatus(result.reason()));
		if (result.snapshot().stage() == WorldInterfaceStage.SUMMONING) {
			sendEncounterSnapshots(player.level().getServer(), true);
		}
	}

	public static void handlePoemComplete(ServerPlayer player, PoemCompleteC2S payload) {
		MinecraftServer server = player.level().getServer();
		WorldInterfaceState.Snapshot before = WorldInterfaceState.snapshot(server);
		if (!before.valid() || !before.present()
				|| before.encounterId().filter(payload.encounterId()::equals).isEmpty()
				|| before.stage() != WorldInterfaceStage.PORTAL_OPEN) return;
		WorldInterfaceState.PoemLedgerEntry poem = before.poemLedger().get(player.getUUID());
		if (poem == null || !poem.started() || poem.acked() || poem.sequence() != payload.sequence()) return;
		WorldInterfaceState.MutationResult result = WorldInterfaceState.mutate(server,
				payload.encounterId(), before.revision(), state -> state.putPoem(new WorldInterfaceState.PoemLedgerEntry(
						player.getUUID(), poem.sequence(), true, true, true)));
		if (!result.applied()) return;
		prepareVanillaEndReturn(player, result.snapshot());
		completeIfAllPoemsAcknowledged(server);
	}

	/** Opens the dedicated altar UI through a complete server snapshot. */
	public static boolean openAltar(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		WorldInterfaceState.Snapshot snapshot = WorldInterfaceState.snapshot(server);
		if (!snapshot.valid() || !snapshot.present()
				|| snapshot.stage() != WorldInterfaceStage.WAITING_TERMINALS) return false;
		ALTAR_VIEWERS.computeIfAbsent(server, ignored -> new HashSet<>()).add(player.getUUID());
		sendAltarSnapshot(player, snapshot, WorldInterfaceProtocol.AltarStatus.READY);
		return true;
	}

	/** Arms one authored poem immediately before the exit invokes vanilla End credits. */
	public static void startPoem(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		for (int attempt = 0; attempt < MAX_MUTATION_RETRIES; attempt++) {
			WorldInterfaceState.Snapshot before = WorldInterfaceState.snapshot(server);
			if (!before.valid() || !before.present()
					|| (before.stage() != WorldInterfaceStage.PORTAL_OPEN
							&& before.stage() != WorldInterfaceStage.COMPLETE)
					|| before.outcome() == WorldInterfaceState.Outcome.NONE) return;
			WorldInterfaceState.PoemLedgerEntry existing = before.poemLedger().get(player.getUUID());
			prepareVanillaEndReturn(player, before);
			if (before.stage() == WorldInterfaceStage.COMPLETE
					|| !before.frozenRoster().contains(player.getUUID())
					|| existing != null && existing.acked()) {
				if (before.stage() == WorldInterfaceStage.PORTAL_OPEN) {
					completeIfAllPoemsAcknowledged(server);
				}
				return;
			}
			long sequence;
			if (existing != null && existing.started()) {
				sequence = existing.sequence();
			} else {
				sequence = nextClientSequence(player);
				WorldInterfaceState.MutationResult result = WorldInterfaceState.mutate(server,
						before.encounterId().orElseThrow(), before.revision(), state -> state.putPoem(
								new WorldInterfaceState.PoemLedgerEntry(player.getUUID(), sequence, true, false, false)));
				if (!result.applied()) {
					if ("revision_mismatch".equals(result.reason())) continue;
					return;
				}
				before = result.snapshot();
			}
			rememberClientSequence(player, sequence);
			ServerPlayNetworking.send(player, new PoemStartS2C(before.encounterId().orElseThrow(), sequence,
					outcomeWire(before.outcome()), FrequencyWorldData.get(server).worldId()));
			return;
		}
	}

	private static void tickStart(MinecraftServer server) {
		ServerLevel level = server.getLevel(Level.END);
		if (level == null) return;
		WorldInterfaceState.Snapshot snapshot = WorldInterfaceState.snapshot(server);
		if (!snapshot.valid() || !snapshot.present()) return;
		if (!RECOVERED_SERVERS.contains(server)) {
			recoverAfterRestart(server);
			snapshot = WorldInterfaceState.snapshot(server);
		}
		EndBossArenaService.suppressVanillaFight(level);
		if (snapshot.friendlyDragonUuid().isPresent()) {
			UUID dragonId = snapshot.friendlyDragonUuid().orElseThrow();
			if (!FriendlyDragonService.tick(level, dragonId, snapshot.arenaCenter())
					&& snapshot.stage().wireId() >= WorldInterfaceStage.PORTAL_OPEN.wireId()
					&& snapshot.outcome() == WorldInterfaceState.Outcome.SUCCESS) {
				FriendlyDragonService.spawn(level, snapshot.arenaCenter(), dragonId);
			}
		}
		if (snapshot.stage().wireId() >= WorldInterfaceStage.PORTAL_OPEN.wireId()) {
			removeBossProjection(level, snapshot);
			snapshot = clearFinishedBossIdentity(server, snapshot);
		}

		switch (snapshot.stage()) {
			case SUMMONING -> tickSummoning(level, snapshot);
			case PHASE_1, PHASE_2, PHASE_3 -> tickCombat(level, snapshot);
			case SUCCESS_RESOLUTION, FAILURE_RESOLUTION -> tickResolution(level, snapshot);
			case PORTAL_OPEN -> ensureExitOpen(level, snapshot);
			default -> { }
		}
		if (server.getTickCount() % SNAPSHOT_INTERVAL_TICKS == 0) {
			sendEncounterSnapshots(server, false);
			pruneAltarViewers(server);
		}
	}

	private static void tickSummoning(ServerLevel level, WorldInterfaceState.Snapshot before) {
		WorldInterfaceEntity boss = ensureBoss(level, before);
		WorldInterfaceState.Snapshot snapshot = WorldInterfaceState.snapshot(level.getServer());
		if (snapshot.resolutionTick() < 0L) {
			WorldInterfaceState.MutationResult started = WorldInterfaceState.mutate(level.getServer(),
					snapshot.encounterId().orElseThrow(), snapshot.revision(), state ->
						state.setResolution(0, level.getGameTime()));
			if (started.applied()) snapshot = started.snapshot();
			showAction(level, snapshot, WorldInterfaceProtocol.BossAction.SUMMONING, SUMMON_DURATION_TICKS,
					List.of(), snapshot.deterministicSeed());
			AudioService.playBounded(level, snapshot.arenaCenter(), ModSounds.WORLD_INTERFACE_SUMMON,
					SoundSource.HOSTILE, 0.95F, 0.8F);
		}
		if (boss != null) {
			boss.setForm(WorldInterfaceEntity.FORM_LISTENING);
		}
		if (snapshot.resolutionTick() >= 0L
				&& level.getGameTime() - snapshot.resolutionTick() >= SUMMON_DURATION_TICKS) {
			boolean running = onlineFrozenCount(level.getServer(), snapshot) > 0;
			WorldInterfaceState.MutationResult transitioned = WorldInterfaceState.mutate(level.getServer(),
					snapshot.encounterId().orElseThrow(), snapshot.revision(), state -> {
						state.transitionTo(WorldInterfaceStage.PHASE_1);
						state.setClock(0L, running ? level.getGameTime() : -1L, state.anchorPenaltyTicks());
						state.setActionSchedule(0L, 0, 40L);
						state.setResolution(0, -1L);
					});
			if (transitioned.applied()) {
				EndBossArenaService.setAnchorsInvulnerable(level, EndBossArenaService.prepare(level), false);
				if (boss != null) boss.clearAction();
				broadcast(level.getServer(), "message.thefourthfrequency.world_interface.combat_started");
				sendEncounterSnapshots(level.getServer(), true);
			}
		}
	}

	private static void tickCombat(ServerLevel level, WorldInterfaceState.Snapshot initial) {
		WorldInterfaceState.Snapshot snapshot = reconcileClock(level, initial);
		long elapsed = effectiveActiveTicks(snapshot, level.getGameTime());
		if (WorldInterfacePolicy.resolveTick(elapsed, snapshot.destroyedAnchorCount(), false)
				== WorldInterfacePolicy.TickVerdict.FAILURE) {
			lockFailure(level, snapshot);
			return;
		}
		WorldInterfaceEntity boss = ensureBoss(level, snapshot);
		if (boss == null) return;
		boolean running = snapshot.runningSinceGameTime() >= 0L;
		if (running && snapshot.recoveryGraceTicks() > 0) {
			int graceBefore = snapshot.recoveryGraceTicks();
			WorldInterfaceState.MutationResult grace = WorldInterfaceState.mutate(level.getServer(),
					snapshot.encounterId().orElseThrow(), snapshot.revision(), state ->
						state.setRecoveryGraceTicks(graceBefore - 1));
			if (grace.applied()) snapshot = grace.snapshot();
		}
		if (running && level.getServer().getTickCount() % HEAL_INTERVAL_TICKS == 0
				&& snapshot.virtualHealth() > 0.0D && snapshot.aliveAnchorCount() > 0) {
			healBoss(level, snapshot);
			snapshot = WorldInterfaceState.snapshot(level.getServer());
		}
		if (level.getServer().getTickCount() % HEAL_INTERVAL_TICKS == 0) {
			persistTerrainBudget(level, snapshot);
		}
		// The concrete nine-action executor is integrated below this invariant gate.
		if (running && snapshot.recoveryGraceTicks() == 0) tickAttacks(level, boss, snapshot, elapsed);
	}

	private static void tickResolution(ServerLevel level, WorldInterfaceState.Snapshot snapshot) {
		WorldInterfaceEntity boss = ensureBoss(level, snapshot);
		if (boss != null) {
			boss.setForm(WorldInterfaceEntity.FORM_INTERFACE);
			boss.setDeltaMovement(Vec3.ZERO);
		}
		long started = snapshot.resolutionTick() < 0L ? level.getGameTime() : snapshot.resolutionTick();
		if (snapshot.resolutionTick() < 0L) {
			WorldInterfaceState.MutationResult repaired = WorldInterfaceState.mutate(level.getServer(),
					snapshot.encounterId().orElseThrow(), snapshot.revision(), state -> state.setResolution(0, started));
			if (!repaired.applied()) return;
			snapshot = repaired.snapshot();
		}
		synchronizeResolutionAction(level, snapshot, boss, false);
		long age = level.getGameTime() - started;
		if (age >= 20L && snapshot.resolutionStep() < 1) {
			if (snapshot.outcome() == WorldInterfaceState.Outcome.SUCCESS) pointLivingAnchorsSkyward(level, snapshot);
			WorldInterfaceState.MutationResult step = WorldInterfaceState.mutate(level.getServer(),
					snapshot.encounterId().orElseThrow(), snapshot.revision(), state -> state.setResolution(1, started));
			if (step.applied()) snapshot = step.snapshot();
		}
		if (age >= 80L && snapshot.resolutionStep() < 2) {
			if (snapshot.outcome() == WorldInterfaceState.Outcome.SUCCESS) {
				UUID dragonId = snapshot.friendlyDragonUuid().orElse(deterministicEntityUuid(
						"friendly_dragon", snapshot.encounterId().orElseThrow()));
				WorldInterfaceState.MutationResult step = WorldInterfaceState.mutate(level.getServer(),
						snapshot.encounterId().orElseThrow(), snapshot.revision(), state -> {
							state.setFriendlyDragonUuid(dragonId);
							state.setResolution(2, started);
						});
				if (step.applied()) {
					snapshot = step.snapshot();
					FriendlyDragonService.spawn(level, snapshot.arenaCenter(), dragonId);
					broadcast(level.getServer(), "message.thefourthfrequency.world_interface.dragon.thanks");
					broadcast(level.getServer(), "message.thefourthfrequency.world_interface.dragon.return");
				}
			} else {
				WorldInterfaceState.MutationResult step = WorldInterfaceState.mutate(level.getServer(),
						snapshot.encounterId().orElseThrow(), snapshot.revision(), state -> state.setResolution(2, started));
				if (step.applied()) snapshot = step.snapshot();
			}
		}
		if (age >= RESOLUTION_DURATION_TICKS && snapshot.resolutionStep() < 3) {
			removeBossProjection(level, snapshot);
			BlockPos exitPosition = snapshot.altarCenter();
			placeExit(level, exitPosition);
			WorldInterfaceState.MutationResult opened = WorldInterfaceState.mutate(level.getServer(),
					snapshot.encounterId().orElseThrow(), snapshot.revision(), state -> {
						state.clearBossUuid();
						state.setExit(exitPosition, true);
						state.setResolution(3, started);
						state.transitionTo(WorldInterfaceStage.PORTAL_OPEN);
					});
			if (opened.applied()) sendEncounterSnapshots(level.getServer(), true);
		}
	}

	private static void tickAttacks(ServerLevel level, WorldInterfaceEntity boss,
			WorldInterfaceState.Snapshot snapshot, long elapsed) {
		MinecraftServer server = level.getServer();
		if (snapshot.currentAttack().isPresent()) {
			WorldInterfaceState.AttackEnvelope active = snapshot.currentAttack().orElseThrow();
			WorldInterfaceAttackService.AttackTick tick =
					WorldInterfaceAttackService.tick(level, boss, snapshot, elapsed);
			WorldInterfaceState.Snapshot latest = WorldInterfaceState.snapshot(server);
			if (!latest.stage().isCombat() || latest.encounterId().isEmpty()) return;
			for (int attempt = 0; attempt < MAX_MUTATION_RETRIES; attempt++) {
				latest = WorldInterfaceState.snapshot(server);
				WorldInterfaceState.AttackEnvelope stored = latest.currentAttack().orElse(null);
				if (stored == null || stored.sequence() != active.sequence()) return;
				WorldInterfaceState.Snapshot captured = latest;
				WorldInterfaceState.MutationResult result = WorldInterfaceState.mutate(server,
						latest.encounterId().orElseThrow(), latest.revision(), state -> {
							switch (tick.status()) {
								case CONTINUE -> state.setCurrentAttack(tick.replacementEnvelope().orElseThrow());
								case COMPLETE -> state.clearCurrentAttack();
								case CANCELLED_RESTART -> {
									state.clearCurrentAttack();
									state.setRecoveryGraceTicks(WorldInterfaceActionScheduler.RESTART_RECOVERY_TICKS);
									state.setActionSchedule(captured.actionSequence(), captured.lastActionWireId(),
											Math.max(captured.nextActionActiveTick(),
													elapsed + WorldInterfaceActionScheduler.RESTART_RECOVERY_TICKS));
								}
							}
						});
				if (result.applied()) {
					if (tick.status() == WorldInterfaceAttackService.AttackStatus.CANCELLED_RESTART) {
						WorldInterfaceAttackService.cancelAndRestore(server, latest.encounterId().orElseThrow());
					}
					return;
				}
				if (!"revision_mismatch".equals(result.reason())) return;
			}
			return;
		}
		if (elapsed < snapshot.nextActionActiveTick()) return;

		List<ServerPlayer> participants = level.players().stream()
				.filter(player -> player.isAlive() && !player.isSpectator())
				.filter(player -> player.distanceToSqr(snapshot.arenaCenter().getCenter())
						<= ENCOUNTER_VISIBILITY_RADIUS_SQR)
				.sorted(Comparator.comparing(player -> player.getUUID().toString()))
				.limit(WorldInterfacePolicy.MAX_ROSTER_SIZE).toList();
		if (participants.isEmpty()) return;

		WorldInterfaceAction previous = snapshot.lastActionWireId() == 0 ? null
				: WorldInterfaceAction.fromWireId(snapshot.lastActionWireId());
		long choiceSequence = snapshot.actionSequence();
		WorldInterfaceAction selected = null;
		for (int scan = 0; scan < WorldInterfaceAction.values().length * 2; scan++, choiceSequence++) {
			WorldInterfaceAction candidate = WorldInterfaceActionScheduler.nextAction(snapshot.stage(),
					snapshot.deterministicSeed(), choiceSequence, previous);
			if (candidate != WorldInterfaceAction.FORCED_EVICTION
					|| WorldInterfaceActionScheduler.isForcedEvictionReady(elapsed,
							snapshot.lastForcedEvictionTick(), participants.size())) {
				selected = candidate;
				break;
			}
		}
		if (selected == null) return;

		List<ServerPlayer> targets = attackTargets(server, snapshot, participants, selected,
				choiceSequence, elapsed);
		if (selected != WorldInterfaceAction.ARROW_REFLECTION && targets.isEmpty()) return;
		WorldInterfaceAttackService.AttackStart started;
		try {
			started = WorldInterfaceAttackService.begin(level, boss, snapshot, selected, targets,
					elapsed, choiceSequence);
		} catch (IllegalArgumentException | IllegalStateException exception) {
			return;
		}

		WorldInterfaceState.Snapshot latest = WorldInterfaceState.snapshot(server);
		long nextTick = elapsed + WorldInterfaceActionScheduler.scaledIntervalTicks(latest.stage(),
				latest.deterministicSeed(), choiceSequence, latest.destroyedAnchorCount());
		WorldInterfaceAction selectedAction = selected;
		long persistedSequence = choiceSequence;
		WorldInterfaceState.Snapshot captured = latest;
		WorldInterfaceState.MutationResult stored = WorldInterfaceState.mutate(server,
				latest.encounterId().orElseThrow(), latest.revision(), state -> {
					if (state.currentAttack().isPresent()) throw new IllegalStateException("attack_already_stored");
					state.setCurrentAttack(started.envelope());
					state.setActionSchedule(persistedSequence + 1L, selectedAction.wireId(), nextTick);
					if (selectedAction.requiresExclusiveControl()) {
						for (Map.Entry<UUID, Long> cooldown : captured.controlCooldowns().entrySet()) {
							if (cooldown.getValue() <= elapsed) {
								state.removeControlCooldown(cooldown.getKey());
							}
						}
						for (UUID target : started.targets()) {
							state.putControlCooldown(target,
									elapsed + WorldInterfaceActionScheduler.STRONG_CONTROL_IMMUNITY_TICKS);
						}
					}
					if (selectedAction == WorldInterfaceAction.FORCED_EVICTION) {
						state.setLastForcedEvictionTick(elapsed);
					}
				});
		if (!stored.applied()) {
			WorldInterfaceAttackService.cancelAndRestore(server, latest.encounterId().orElseThrow());
			return;
		}
		showAction(level, stored.snapshot(), bossActionWire(selectedAction), started.durationTicks(),
				started.targets().stream().sorted(Comparator.comparing(UUID::toString)).toList(), started.seed());
	}

	private static List<ServerPlayer> attackTargets(MinecraftServer server,
			WorldInterfaceState.Snapshot snapshot, List<ServerPlayer> participants,
			WorldInterfaceAction action, long sequence, long elapsed) {
		if (action == WorldInterfaceAction.ARROW_REFLECTION) return participants;
		if (action == WorldInterfaceAction.FORCED_EVICTION) {
			UUID host = participants.stream().filter(player -> server.isSingleplayerOwner(player.nameAndId()))
					.map(ServerPlayer::getUUID).findFirst().orElse(null);
			List<UUID> selected = WorldInterfaceActionScheduler.selectForcedEvictionTargets(
					participants.stream().map(ServerPlayer::getUUID).toList(), host,
					snapshot.deterministicSeed(), sequence);
			return selected.stream().map(server.getPlayerList()::getPlayer).filter(java.util.Objects::nonNull).toList();
		}
		List<ServerPlayer> eligible = participants;
		if (action.requiresExclusiveControl()) {
			eligible = participants.stream().filter(player ->
					elapsed >= snapshot.controlCooldowns().getOrDefault(player.getUUID(), 0L)).toList();
		}
		if (eligible.isEmpty()) return List.of();
		int first = Math.floorMod((int) (snapshot.deterministicSeed() ^ sequence), eligible.size());
		if (action == WorldInterfaceAction.GRAB_THROW && eligible.size() > 1) {
			return List.of(eligible.get(first), eligible.get((first + 1) % eligible.size()));
		}
		return List.of(eligible.get(first));
	}

	private static WorldInterfaceProtocol.BossAction bossActionWire(WorldInterfaceAction action) {
		return WorldInterfaceProtocol.BossAction.fromWireId(action.wireId());
	}

	private static void healBoss(ServerLevel level, WorldInterfaceState.Snapshot before) {
		double healing = WorldInterfacePolicy.healingPerTick(before.maxVirtualHealth(), before.aliveAnchorCount())
				* HEAL_INTERVAL_TICKS;
		double healed = Math.min(before.maxVirtualHealth(), before.virtualHealth() + healing);
		if (healed <= before.virtualHealth()) return;
		WorldInterfaceState.mutate(level.getServer(), before.encounterId().orElseThrow(), before.revision(),
				state -> state.setVirtualHealth(before.maxVirtualHealth(), healed));
	}

	private static WorldInterfaceState.Snapshot reconcileClock(ServerLevel level,
			WorldInterfaceState.Snapshot before) {
		boolean shouldRun = onlineFrozenCount(level.getServer(), before) > 0;
		if (shouldRun == (before.runningSinceGameTime() >= 0L)) return before;
		long elapsed = effectiveActiveTicks(before, level.getGameTime());
		WorldInterfaceState.MutationResult result = WorldInterfaceState.mutate(level.getServer(),
				before.encounterId().orElseThrow(), before.revision(), state ->
						state.setClock(elapsed, shouldRun ? level.getGameTime() : -1L,
								before.anchorPenaltyTicks()));
		return result.applied() ? result.snapshot() : WorldInterfaceState.snapshot(level.getServer());
	}

	private static void lockFailure(ServerLevel level, WorldInterfaceState.Snapshot before) {
		for (int attempt = 0; attempt < MAX_MUTATION_RETRIES; attempt++) {
			before = WorldInterfaceState.snapshot(level.getServer());
			if (!before.stage().isCombat()) return;
			long elapsed = effectiveActiveTicks(before, level.getGameTime());
			if (!WorldInterfacePolicy.hasTimedOut(elapsed, before.destroyedAnchorCount())) return;
			WorldInterfaceState.Snapshot captured = before;
			WorldInterfaceState.MutationResult result = WorldInterfaceState.mutate(level.getServer(),
					before.encounterId().orElseThrow(), before.revision(), state -> {
						state.setClock(elapsed, -1L, captured.anchorPenaltyTicks());
						advanceToPhaseThree(state);
						state.clearCurrentAttack();
						state.clearControlCooldowns();
						state.setGateState(WorldInterfaceGatewayState.RED);
						state.transitionTo(WorldInterfaceStage.FAILURE_RESOLUTION);
						state.setResolution(0, level.getGameTime());
					});
			if (!result.applied()) {
				if ("revision_mismatch".equals(result.reason())) continue;
				return;
			}
			beginResolution(level, result.snapshot(), false);
			return;
		}
	}

	private static void beginResolution(ServerLevel level, WorldInterfaceState.Snapshot snapshot,
			boolean success) {
		EndBossArenaService.cancelQueuedScars(level);
		WorldInterfaceAttackService.cancelAndRestore(level.getServer(), snapshot.encounterId().orElseThrow());
		WorldInterfaceEntity boss = findBoss(level, snapshot).orElse(null);
		if (boss != null) {
			boss.setForm(WorldInterfaceEntity.FORM_INTERFACE);
			boss.showAction(success ? WorldInterfaceProtocol.BossAction.SUCCESS_DEATH.wireId()
					: WorldInterfaceProtocol.BossAction.FAILURE_ESCAPE.wireId(),
					level.getGameTime(), RESOLUTION_DURATION_TICKS);
			boss.setDeltaMovement(Vec3.ZERO);
		}
		showAction(level, snapshot, success ? WorldInterfaceProtocol.BossAction.SUCCESS_DEATH
				: WorldInterfaceProtocol.BossAction.FAILURE_ESCAPE, RESOLUTION_DURATION_TICKS,
				List.of(), snapshot.deterministicSeed() ^ snapshot.actionSequence());
		AudioService.playBounded(level, snapshot.arenaCenter(), success
				? ModSounds.WORLD_INTERFACE_SUCCESS : ModSounds.WORLD_INTERFACE_FAILURE,
				SoundSource.HOSTILE, 1.0F, success ? 1.0F : 0.62F);
		broadcast(level.getServer(), success ? "message.thefourthfrequency.world_interface.success_locked"
				: "message.thefourthfrequency.world_interface.failure_locked");
		if (success) markDefeatedMilestone(level.getServer(), snapshot.frozenRoster());
		sendEncounterSnapshots(level.getServer(), true);
	}

	private static void synchronizeResolutionAction(ServerLevel level,
			WorldInterfaceState.Snapshot snapshot, WorldInterfaceEntity boss, boolean broadcast) {
		if (snapshot.stage() != WorldInterfaceStage.SUCCESS_RESOLUTION
				&& snapshot.stage() != WorldInterfaceStage.FAILURE_RESOLUTION) return;
		WorldInterfaceProtocol.BossAction action = snapshot.stage() == WorldInterfaceStage.SUCCESS_RESOLUTION
				? WorldInterfaceProtocol.BossAction.SUCCESS_DEATH
				: WorldInterfaceProtocol.BossAction.FAILURE_ESCAPE;
		long started = snapshot.resolutionTick() < 0L ? level.getGameTime() : snapshot.resolutionTick();
		if (boss != null && (boss.actionId() != action.wireId()
				|| boss.actionStartTick() != started || boss.actionDuration() != RESOLUTION_DURATION_TICKS)) {
			boss.showAction(action.wireId(), started, RESOLUTION_DURATION_TICKS);
		}
		if (!broadcast) return;
		for (ServerPlayer player : encounterRecipients(level.getServer(), snapshot)) {
			sendResolutionAction(player, snapshot, action, started);
		}
	}

	private static void sendResolutionAction(ServerPlayer player, WorldInterfaceState.Snapshot snapshot,
			WorldInterfaceProtocol.BossAction action, long started) {
		ServerPlayNetworking.send(player, new BossActionS2C(snapshot.encounterId().orElseThrow(),
				nextClientSequence(player), action.wireId(), started, RESOLUTION_DURATION_TICKS,
				List.of(), snapshot.deterministicSeed() ^ snapshot.actionSequence(), 0));
	}

	private static void phaseChanged(ServerLevel level, WorldInterfaceState.Snapshot before,
			WorldInterfaceState.Snapshot after) {
		WorldInterfaceAttackService.cancelAndRestore(level.getServer(), after.encounterId().orElseThrow());
		WorldInterfaceEntity boss = findBoss(level, after).orElse(null);
		WorldInterfaceProtocol.BossAction action = after.stage() == WorldInterfaceStage.PHASE_2
				? WorldInterfaceProtocol.BossAction.MORPH_TO_SECOND
				: WorldInterfaceProtocol.BossAction.MORPH_TO_THIRD;
		if (boss != null) {
			boss.setForm(formForStage(after.stage()));
			boss.showAction(action.wireId(), level.getGameTime(), 60);
		}
		showAction(level, after, action, 60, List.of(), after.deterministicSeed() ^ after.stage().wireId());
		AudioService.playBounded(level, after.arenaCenter(), ModSounds.WORLD_INTERFACE_MORPH,
				SoundSource.HOSTILE, 0.92F, after.stage() == WorldInterfaceStage.PHASE_2 ? 0.86F : 0.68F);
		broadcast(level.getServer(), "message.thefourthfrequency.world_interface.phase." +
				after.stage().serializedName());
		sendEncounterSnapshots(level.getServer(), true);
	}

	private static WorldInterfaceEntity ensureBoss(ServerLevel level, WorldInterfaceState.Snapshot snapshot) {
		WorldInterfaceEntity found = findBoss(level, snapshot).orElse(null);
		if (found != null) {
			found.bindEncounter(snapshot.encounterId().orElseThrow());
			if (snapshot.bossUuid().filter(found.getUUID()::equals).isEmpty()) {
				WorldInterfaceState.mutate(level.getServer(), snapshot.encounterId().orElseThrow(), snapshot.revision(),
						state -> state.setBossUuid(found.getUUID()));
			}
			ensureParts(level, found);
			return found;
		}
		if (snapshot.stage().wireId() < WorldInterfaceStage.SUMMONING.wireId()
				|| snapshot.stage().wireId() > WorldInterfaceStage.FAILURE_RESOLUTION.wireId()) return null;
		level.getChunkAt(snapshot.arenaCenter());
		WorldInterfaceEntity boss = ModEntities.WORLD_INTERFACE.create(level, EntitySpawnReason.EVENT);
		if (boss == null) return null;
		UUID requested = snapshot.bossUuid().orElseGet(() -> deterministicEntityUuid(
				"boss", snapshot.encounterId().orElseThrow()));
		Entity collision = level.getEntity(requested);
		if (collision != null) return null;
		boss.setUUID(requested);
		boss.bindEncounter(snapshot.encounterId().orElseThrow());
		boss.setForm(formForStage(snapshot.stage()));
		boss.snapTo(snapshot.arenaCenter().getX() + 0.5D, snapshot.arenaCenter().getY() + 18.0D,
				snapshot.arenaCenter().getZ() + 0.5D, 0.0F, 0.0F);
		if (!level.addFreshEntity(boss)) return null;
		if (snapshot.bossUuid().filter(boss.getUUID()::equals).isEmpty()) {
			WorldInterfaceState.mutate(level.getServer(), snapshot.encounterId().orElseThrow(), snapshot.revision(),
					state -> state.setBossUuid(boss.getUUID()));
		}
		ensureParts(level, boss);
		return boss;
	}

	private static Optional<WorldInterfaceEntity> findBoss(ServerLevel level,
			WorldInterfaceState.Snapshot snapshot) {
		if (snapshot.bossUuid().isPresent()) {
			Entity direct = level.getEntity(snapshot.bossUuid().orElseThrow());
			if (direct instanceof WorldInterfaceEntity boss && boss.isAlive()) return Optional.of(boss);
		}
		AABB bounds = new AABB(snapshot.arenaCenter()).inflate(192.0D, 128.0D, 192.0D);
		return level.getEntitiesOfClass(WorldInterfaceEntity.class, bounds, Entity::isAlive).stream()
				.filter(entity -> snapshot.encounterId().filter(id -> id.equals(entity.encounterId())).isPresent())
				.findFirst();
	}

	private static void ensureParts(ServerLevel level, WorldInterfaceEntity boss) {
		String tag = PART_TAG_PREFIX + boss.getUUID();
		AABB bounds = boss.getBoundingBox().inflate(24.0D, 32.0D, 24.0D);
		Set<Integer> present = new HashSet<>();
		for (WorldInterfacePartEntity part : level.getEntitiesOfClass(WorldInterfacePartEntity.class, bounds,
				entity -> entity.getTags().contains(tag))) present.add(part.partIndex());
		for (int index = 0; index < 10; index++) {
			if (present.contains(index)) continue;
			WorldInterfacePartEntity part = ModEntities.WORLD_INTERFACE_PART.create(level, EntitySpawnReason.EVENT);
			if (part == null) continue;
			part.addTag(tag);
			part.attach(boss, index);
			level.addFreshEntity(part);
		}
	}

	private static void hoverAt(WorldInterfaceEntity boss, BlockPos center, double height, long tick) {
		double angle = tick * 0.006D;
		Vec3 desired = new Vec3(center.getX() + 0.5D + Math.cos(angle) * 7.0D,
				center.getY() + height + Math.sin(angle * 1.7D) * 1.5D,
				center.getZ() + 0.5D + Math.sin(angle) * 7.0D);
		boss.setDeltaMovement(desired.subtract(boss.position()).scale(0.08D));
	}

	private static void showAction(ServerLevel level, WorldInterfaceState.Snapshot snapshot,
			WorldInterfaceProtocol.BossAction action, int duration, List<UUID> targets, long seed) {
		WorldInterfaceEntity boss = findBoss(level, snapshot).orElse(null);
		if (boss != null) boss.showAction(action.wireId(), level.getGameTime(), duration);
		for (ServerPlayer player : encounterRecipients(level.getServer(), snapshot)) {
			ServerPlayNetworking.send(player, new BossActionS2C(snapshot.encounterId().orElseThrow(),
					nextClientSequence(player), action.wireId(), level.getGameTime(), duration,
					targets, seed, 0));
		}
	}

	private static void sendEncounterSnapshots(MinecraftServer server, boolean immediate) {
		WorldInterfaceState.Snapshot snapshot = WorldInterfaceState.snapshot(server);
		if (!snapshot.valid() || !snapshot.present()) return;
		ServerLevel level = server.getLevel(Level.END);
		if (level == null) return;
		WorldInterfaceEntity boss = findBoss(level, snapshot).orElse(null);
		int form = boss == null ? formForStage(snapshot.stage()) + 1 : boss.form() + 1;
		if (snapshot.stage().wireId() < WorldInterfaceStage.SUMMONING.wireId()
				|| snapshot.stage() == WorldInterfaceStage.COMPLETE) form = WorldInterfaceProtocol.Form.NONE.wireId();
		long elapsed = effectiveActiveTicks(snapshot, level.getGameTime());
		int anchors = 0;
		for (WorldInterfaceState.Anchor anchor : snapshot.anchors()) if (!anchor.destroyed()) anchors |= 1 << anchor.index();
		int gatewayState = snapshot.gates().isEmpty() ? WorldInterfaceGatewayState.DORMANT.wireId()
				: snapshot.gates().getFirst().state().wireId();
		float progress = WorldInterfacePolicy.failurePresentationProgress(snapshot.stage(),
				snapshot.resolutionTick(), level.getGameTime(), RESOLUTION_DURATION_TICKS);
		for (ServerPlayer player : encounterRecipients(server, snapshot)) {
			ServerPlayNetworking.send(player, new WorldInterfaceSnapshotS2C(WorldInterfaceProtocol.VERSION,
					snapshot.encounterId().orElseThrow(), nextClientSequence(player), snapshot.stage().wireId(), form,
					boss == null ? NIL_UUID : boss.getUUID(), snapshot.arenaCenter(),
					(float) snapshot.maxVirtualHealth(), (float) snapshot.virtualHealth(), anchors,
					elapsed, snapshot.anchorPenaltyTicks(), snapshot.runningSinceGameTime() < 0L,
					Math.max(0L, level.getGameTime()), gatewayState,
					snapshot.gates().stream().map(WorldInterfaceState.Gate::position).toList(),
					outcomeWire(snapshot.outcome()), progress));
		}
	}

	private static void sendAltarSnapshots(MinecraftServer server, WorldInterfaceState.Snapshot snapshot,
			WorldInterfaceProtocol.AltarStatus status) {
		Set<UUID> viewers = ALTAR_VIEWERS.getOrDefault(server, Set.of());
		for (UUID viewerId : List.copyOf(viewers)) {
			ServerPlayer viewer = server.getPlayerList().getPlayer(viewerId);
			if (viewer != null) sendAltarSnapshot(viewer, snapshot, status);
		}
	}

	private static void sendAltarSnapshot(ServerPlayer viewer, WorldInterfaceState.Snapshot snapshot,
			WorldInterfaceProtocol.AltarStatus status) {
		MinecraftServer server = viewer.level().getServer();
		List<UUID> roster = altarRoster(server, snapshot);
		List<String> names = roster.stream().map(id -> {
			ServerPlayer online = server.getPlayerList().getPlayer(id);
			return online == null ? id.toString().substring(0, 8) : online.getGameProfile().name();
		}).toList();
		int mask = 0;
		for (int index = 0; index < roster.size(); index++) {
			WorldInterfaceState.TerminalTransaction transaction = snapshot.terminalTransactions().get(roster.get(index));
			if (transaction != null && (transaction.state() == WorldInterfaceState.TerminalTransactionState.REMOVED
					|| transaction.state() == WorldInterfaceState.TerminalTransactionState.COMMITTED)) mask |= 1 << index;
		}
		ServerPlayNetworking.send(viewer, new AltarSnapshotS2C(WorldInterfaceProtocol.VERSION,
				snapshot.encounterId().orElseThrow(), nextClientSequence(viewer), snapshot.revision(),
				snapshot.stage().wireId(), snapshot.altarCenter(), roster, names, mask,
				roster.contains(viewer.getUUID()) && !viewer.isSpectator(), status.wireId()));
	}

	private static List<UUID> altarRoster(MinecraftServer server, WorldInterfaceState.Snapshot snapshot) {
		if (!snapshot.frozenRoster().isEmpty()) return snapshot.frozenRoster().stream()
				.sorted(Comparator.comparing(UUID::toString)).toList();
		return server.getPlayerList().getPlayers().stream().filter(player -> !player.isSpectator())
				.map(ServerPlayer::getUUID).sorted(Comparator.comparing(UUID::toString)).limit(8).toList();
	}

	private static List<ServerPlayer> encounterRecipients(MinecraftServer server,
			WorldInterfaceState.Snapshot snapshot) {
		LinkedHashSet<ServerPlayer> recipients = new LinkedHashSet<>();
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (snapshot.frozenRoster().contains(player.getUUID()) || player.level().dimension() == Level.END) {
				recipients.add(player);
			}
		}
		return List.copyOf(recipients);
	}

	private static long nextClientSequence(ServerPlayer player) {
		Map<UUID, Long> sequences = CLIENT_SEQUENCES.computeIfAbsent(player.level().getServer(),
				ignored -> new HashMap<>());
		long next = Math.incrementExact(sequences.getOrDefault(player.getUUID(), -1L));
		sequences.put(player.getUUID(), next);
		return next;
	}

	private static void rememberClientSequence(ServerPlayer player, long sequence) {
		if (sequence < 0L) throw new IllegalArgumentException("Sequence must be non-negative");
		CLIENT_SEQUENCES.computeIfAbsent(player.level().getServer(), ignored -> new HashMap<>())
				.merge(player.getUUID(), sequence, Math::max);
	}

	private static void pruneAltarViewers(MinecraftServer server) {
		Set<UUID> viewers = ALTAR_VIEWERS.get(server);
		if (viewers == null) return;
		WorldInterfaceState.Snapshot snapshot = WorldInterfaceState.snapshot(server);
		viewers.removeIf(id -> {
			ServerPlayer player = server.getPlayerList().getPlayer(id);
			return player == null || snapshot.stage() != WorldInterfaceStage.WAITING_TERMINALS
					|| player.level().dimension() != Level.END
					|| player.distanceToSqr(snapshot.altarCenter().getCenter()) > 10.0D * 10.0D;
		});
	}

	private static void recoverAfterRestart(MinecraftServer server) {
		if (!RECOVERED_SERVERS.add(server)) return;
		ServerLevel level = server.getLevel(Level.END);
		WorldInterfaceState.Snapshot before = WorldInterfaceState.snapshot(server);
		if (before.present()) FinalConfrontationService.retireLegacyAltar(server);
		if (level == null || !before.valid() || !before.present()) return;
		EndBossArenaService.PreparedArena arena = EndBossArenaService.prepare(level);
		EndBossArenaService.restoreAuthoritativeAnchors(level, before, !before.stage().isCombat());
		EndBossArenaService.restoreTerrainEditCount(level, before.terrainEditsUsed());
		bindCore(level, before);
		ensureExitOpen(level, before);
		if (before.stage().isCombat()) {
			WorldInterfaceAttackService.onRestart(server, before.encounterId().orElseThrow());
			before = WorldInterfaceState.snapshot(server);
			long recoveredElapsed = effectiveActiveTicks(before, level.getGameTime());
			WorldInterfaceState.Snapshot captured = before;
			WorldInterfaceState.MutationResult recovered = WorldInterfaceState.mutate(server,
					before.encounterId().orElseThrow(), before.revision(), state -> {
						state.setClock(recoveredElapsed, -1L, captured.anchorPenaltyTicks());
						state.clearCurrentAttack();
						state.setRecoveryGraceTicks(WorldInterfaceActionScheduler.RESTART_RECOVERY_TICKS);
						state.setActionSchedule(captured.actionSequence(), captured.lastActionWireId(),
								Math.max(captured.nextActionActiveTick(), recoveredElapsed
										+ WorldInterfaceActionScheduler.RESTART_RECOVERY_TICKS));
					});
			if (recovered.applied()) before = recovered.snapshot();
		}
		if (before.stage().wireId() >= WorldInterfaceStage.SUMMONING.wireId()
				&& before.stage().wireId() <= WorldInterfaceStage.FAILURE_RESOLUTION.wireId()) {
			WorldInterfaceEntity recoveredBoss = ensureBoss(level, before);
			synchronizeResolutionAction(level, before, recoveredBoss, true);
		}
		if (before.friendlyDragonUuid().isPresent()
				&& FriendlyDragonService.recover(level, before.friendlyDragonUuid().orElseThrow()).isEmpty()) {
			FriendlyDragonService.spawn(level, before.arenaCenter(), before.friendlyDragonUuid().orElseThrow());
		}
	}

	private static void pauseForStop(MinecraftServer server) {
		WorldInterfaceState.Snapshot before = WorldInterfaceState.snapshot(server);
		if (!before.valid() || !before.present() || !before.stage().isCombat()
				|| before.runningSinceGameTime() < 0L) return;
		ServerLevel level = server.getLevel(Level.END);
		if (level == null) return;
		long elapsed = effectiveActiveTicks(before, level.getGameTime());
		WorldInterfaceState.mutate(server, before.encounterId().orElseThrow(), before.revision(),
				state -> state.setClock(elapsed, -1L, before.anchorPenaltyTicks()));
	}

	private static void reconcilePlayer(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		WorldInterfaceState.Snapshot snapshot = WorldInterfaceState.snapshot(server);
		if (!snapshot.valid() || !snapshot.present()) return;
		snapshot.encounterId().ifPresent(id -> WorldInterfaceAttackService.restorePendingFor(player, id));
		if (snapshot.stage() == WorldInterfaceStage.SUCCESS_RESOLUTION
				|| snapshot.stage() == WorldInterfaceStage.FAILURE_RESOLUTION) {
			WorldInterfaceProtocol.BossAction action = snapshot.stage() == WorldInterfaceStage.SUCCESS_RESOLUTION
					? WorldInterfaceProtocol.BossAction.SUCCESS_DEATH
					: WorldInterfaceProtocol.BossAction.FAILURE_ESCAPE;
			long started = snapshot.resolutionTick() < 0L
					? player.level().getGameTime() : snapshot.resolutionTick();
			sendResolutionAction(player, snapshot, action, started);
		}
		WorldInterfaceState.PoemLedgerEntry poem = snapshot.poemLedger().get(player.getUUID());
		WorldInterfaceState.RespawnLedgerEntry respawn = snapshot.respawnLedger().get(player.getUUID());
		if (respawn != null && !respawn.restored() && player.level().dimension() != Level.END
				&& snapshot.stage().wireId() >= WorldInterfaceStage.PORTAL_OPEN.wireId()) {
			restoreRespawnAfterVanillaReturn(player, snapshot);
			snapshot = WorldInterfaceState.snapshot(server);
			poem = snapshot.poemLedger().get(player.getUUID());
			respawn = snapshot.respawnLedger().get(player.getUUID());
		}
		if (snapshot.stage() == WorldInterfaceStage.COMPLETE) {
			if (respawn != null && !respawn.restored()) restoreRespawnAndReturn(player, snapshot);
			return;
		}
		if (snapshot.stage() == WorldInterfaceStage.PORTAL_OPEN && poem != null && poem.acked()) {
			if (respawn != null && !respawn.restored()) restoreRespawnAndReturn(player, snapshot);
			completeIfAllPoemsAcknowledged(server);
			return;
		}
		if (snapshot.stage() == WorldInterfaceStage.PORTAL_OPEN && poem != null && poem.started() && !poem.acked()) {
			rememberClientSequence(player, poem.sequence());
			ServerPlayNetworking.send(player, new PoemStartS2C(snapshot.encounterId().orElseThrow(),
					poem.sequence(), outcomeWire(snapshot.outcome()), FrequencyWorldData.get(server).worldId()));
			prepareVanillaEndReturn(player, snapshot);
			if (player.level().dimension() == Level.END && !player.wonGame) {
				// Reopen the real credits after a disconnect that interrupted an unacknowledged poem.
				player.showEndCredits();
			}
			return;
		}
		if (respawn != null && !respawn.restored()) overrideRespawn(player, snapshot.safeSpawn());
	}

	private static boolean rememberAndOverrideRespawn(ServerPlayer player,
			WorldInterfaceState.Snapshot snapshot) {
		MinecraftServer server = player.level().getServer();
		WorldInterfaceState.RespawnLedgerEntry existing = snapshot.respawnLedger().get(player.getUUID());
		if (existing == null) {
			if (snapshot.respawnLedger().size() >= WorldInterfaceState.MAX_ROSTER_SIZE) {
				player.displayClientMessage(Component.translatable(
						"message.thefourthfrequency.world_interface.roster_full"), false);
				return false;
			}
			ServerPlayer.RespawnConfig original = player.getRespawnConfig();
			WorldInterfaceState.RespawnLedgerEntry entry = original == null
					? new WorldInterfaceState.RespawnLedgerEntry(player.getUUID(), false, "", BlockPos.ZERO,
							0.0F, 0.0F, false, false)
					: new WorldInterfaceState.RespawnLedgerEntry(player.getUUID(), true,
							original.respawnData().dimension().identifier().toString(),
							original.respawnData().pos(), original.respawnData().yaw(), original.respawnData().pitch(),
							original.forced(), false);
			WorldInterfaceState.MutationResult result = WorldInterfaceState.mutate(server,
					snapshot.encounterId().orElseThrow(), snapshot.revision(), state -> state.putRespawn(entry));
			if (!result.applied()) return false;
		} else if (existing.restored()) {
			WorldInterfaceState.MutationResult result = WorldInterfaceState.mutate(server,
					snapshot.encounterId().orElseThrow(), snapshot.revision(), state -> state.putRespawn(
						new WorldInterfaceState.RespawnLedgerEntry(existing.playerId(), existing.hasOriginal(),
								existing.dimension(), existing.position(), existing.yaw(), existing.pitch(),
								existing.forced(), false)));
			if (!result.applied()) return false;
		}
		overrideRespawn(player, snapshot.safeSpawn());
		return true;
	}

	private static void overrideRespawn(ServerPlayer player, BlockPos safeSpawn) {
		player.setRespawnPosition(new ServerPlayer.RespawnConfig(
				LevelData.RespawnData.of(Level.END, safeSpawn, 180.0F, 0.0F), true), false);
	}

	private static void prepareVanillaEndReturn(ServerPlayer player,
			WorldInterfaceState.Snapshot snapshot) {
		WorldInterfaceState.RespawnLedgerEntry entry = snapshot.respawnLedger().get(player.getUUID());
		if (entry != null && !entry.restored()) {
			// Vanilla PERFORM_RESPAWN now resolves to the Overworld default instead of the temporary End altar.
			player.setRespawnPosition(null, false);
		}
	}

	private static void restoreRespawnAfterVanillaReturn(ServerPlayer player,
			WorldInterfaceState.Snapshot snapshot) {
		WorldInterfaceState.RespawnLedgerEntry entry = snapshot.respawnLedger().get(player.getUUID());
		if (entry == null || entry.restored()) return;
		restoreRespawnConfiguration(player, entry);
		markRespawnRestored(player.level().getServer(), entry.playerId());
	}

	private static void restoreRespawnAndReturn(ServerPlayer player,
			WorldInterfaceState.Snapshot snapshot) {
		WorldInterfaceState.RespawnLedgerEntry entry = snapshot.respawnLedger().get(player.getUUID());
		if (entry != null) restoreRespawnConfiguration(player, entry);
		MinecraftServer server = player.level().getServer();
		ServerLevel overworld = server.overworld();
		BlockPos spawn = overworld.getRespawnData().pos();
		Entity teleported = player.teleport(new TeleportTransition(overworld, Vec3.atBottomCenterOf(spawn), Vec3.ZERO,
				player.getYRot(), player.getXRot(),
				TeleportTransition.PLAY_PORTAL_SOUND.then(TeleportTransition.PLACE_PORTAL_TICKET)));
		if (teleported != null && entry != null && !entry.restored()) markRespawnRestored(server, entry.playerId());
	}

	private static void restoreRespawnConfiguration(ServerPlayer player,
			WorldInterfaceState.RespawnLedgerEntry entry) {
		if (!entry.hasOriginal()) {
			player.setRespawnPosition(null, false);
			return;
		}
		try {
			ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION,
					Identifier.parse(entry.dimension()));
			player.setRespawnPosition(new ServerPlayer.RespawnConfig(LevelData.RespawnData.of(dimension,
					entry.position(), entry.yaw(), entry.pitch()), entry.forced()), false);
		} catch (RuntimeException exception) {
			player.setRespawnPosition(null, false);
		}
	}

	private static void markRespawnRestored(MinecraftServer server, UUID playerId) {
		for (int attempt = 0; attempt < MAX_MUTATION_RETRIES; attempt++) {
			WorldInterfaceState.Snapshot latest = WorldInterfaceState.snapshot(server);
			if (!latest.valid() || !latest.present() || latest.encounterId().isEmpty()) return;
			WorldInterfaceState.RespawnLedgerEntry current = latest.respawnLedger().get(playerId);
			if (current == null || current.restored()) return;
			WorldInterfaceState.MutationResult result = WorldInterfaceState.mutate(server,
					latest.encounterId().orElseThrow(), latest.revision(), state -> state.putRespawn(
							new WorldInterfaceState.RespawnLedgerEntry(current.playerId(), current.hasOriginal(),
									current.dimension(), current.position(), current.yaw(), current.pitch(),
									current.forced(), true)));
			if (result.applied() || !"revision_mismatch".equals(result.reason())) return;
		}
	}

	private static void completeIfAllPoemsAcknowledged(MinecraftServer server) {
		for (int attempt = 0; attempt < MAX_MUTATION_RETRIES; attempt++) {
			WorldInterfaceState.Snapshot snapshot = WorldInterfaceState.snapshot(server);
			if (snapshot.stage() != WorldInterfaceStage.PORTAL_OPEN) return;
			boolean complete = !snapshot.frozenRoster().isEmpty() && snapshot.frozenRoster().stream()
					.allMatch(id -> Optional.ofNullable(snapshot.poemLedger().get(id))
							.map(WorldInterfaceState.PoemLedgerEntry::acked).orElse(false)
							&& Optional.ofNullable(snapshot.respawnLedger().get(id))
							.map(WorldInterfaceState.RespawnLedgerEntry::restored).orElse(false));
			if (!complete) return;
			WorldInterfaceState.MutationResult result = WorldInterfaceState.transition(server,
					snapshot.encounterId().orElseThrow(), snapshot.revision(), WorldInterfaceStage.PORTAL_OPEN,
					WorldInterfaceStage.COMPLETE);
			if (result.applied()) {
				sendEncounterSnapshots(server, true);
				return;
			}
			if (!"revision_mismatch".equals(result.reason())) return;
		}
	}

	private static void ensureExitOpen(ServerLevel level, WorldInterfaceState.Snapshot snapshot) {
		if (snapshot.exitOpen() && snapshot.stage().wireId() >= WorldInterfaceStage.PORTAL_OPEN.wireId()) {
			placeExit(level, snapshot.exitPosition());
		}
	}

	private static void placeExit(ServerLevel level, BlockPos center) {
		int flags = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS;
		for (int x = -1; x <= 1; x++) {
			for (int z = -1; z <= 1; z++) {
				level.setBlock(center.offset(x, 0, z), ModBlocks.WORLD_INTERFACE_EXIT_PORTAL.defaultBlockState(), flags);
			}
		}
	}

	private static void removeBossProjection(ServerLevel level, WorldInterfaceState.Snapshot snapshot) {
		AABB bounds = new AABB(snapshot.arenaCenter()).inflate(256.0D, 192.0D, 256.0D);
		for (WorldInterfaceEntity entity : level.getEntitiesOfClass(WorldInterfaceEntity.class, bounds,
				candidate -> snapshot.encounterId().filter(id -> id.equals(candidate.encounterId())).isPresent())) {
			entity.discard();
		}
		for (WorldInterfacePartEntity part : level.getEntitiesOfClass(WorldInterfacePartEntity.class, bounds,
				Entity::isAlive)) {
			part.discard();
		}
	}

	private static WorldInterfaceState.Snapshot clearFinishedBossIdentity(MinecraftServer server,
			WorldInterfaceState.Snapshot initial) {
		WorldInterfaceState.Snapshot snapshot = initial;
		for (int attempt = 0; attempt < MAX_MUTATION_RETRIES && snapshot.bossUuid().isPresent(); attempt++) {
			WorldInterfaceState.MutationResult result = WorldInterfaceState.mutate(server,
					snapshot.encounterId().orElseThrow(), snapshot.revision(), WorldInterfaceState.MutableState::clearBossUuid);
			if (result.applied()) return result.snapshot();
			if (!"revision_mismatch".equals(result.reason())) return snapshot;
			snapshot = result.snapshot();
		}
		return snapshot;
	}

	private static void pointLivingAnchorsSkyward(ServerLevel level, WorldInterfaceState.Snapshot snapshot) {
		for (WorldInterfaceState.Anchor anchor : snapshot.anchors()) {
			if (anchor.destroyed() || anchor.crystalUuid().isEmpty()) continue;
			Entity entity = level.getEntity(anchor.crystalUuid().orElseThrow());
			if (entity instanceof EndCrystal crystal) {
				crystal.setInvulnerable(true);
				crystal.setBeamTarget(anchor.position().above(192));
			}
		}
	}

	private static void persistTerrainBudget(ServerLevel level, WorldInterfaceState.Snapshot snapshot) {
		int actual = EndBossArenaService.permanentTerrainEdits(level);
		if (actual == snapshot.terrainEditsUsed()) return;
		WorldInterfaceState.mutate(level.getServer(), snapshot.encounterId().orElseThrow(), snapshot.revision(),
				state -> state.setTerrainEditsUsed(actual));
	}

	private static Optional<ServerPlayer> nearestArenaParticipant(ServerLevel level,
			WorldInterfaceState.Snapshot snapshot, Vec3 origin) {
		return level.players().stream().filter(player -> player.isAlive() && !player.isSpectator())
				.filter(player -> player.distanceToSqr(snapshot.arenaCenter().getCenter())
						<= ENCOUNTER_VISIBILITY_RADIUS_SQR)
				.min(Comparator.comparingDouble(player -> player.distanceToSqr(origin)));
	}

	private static int onlineFrozenCount(MinecraftServer server, WorldInterfaceState.Snapshot snapshot) {
		int online = 0;
		for (UUID playerId : snapshot.frozenRoster()) {
			if (server.getPlayerList().getPlayer(playerId) != null) online++;
		}
		return online;
	}

	private static long effectiveActiveTicks(WorldInterfaceState.Snapshot snapshot, long gameTime) {
		if (snapshot.runningSinceGameTime() < 0L) return snapshot.activeTicks();
		long delta = Math.max(0L, gameTime - snapshot.runningSinceGameTime());
		return snapshot.activeTicks() > Long.MAX_VALUE - delta ? Long.MAX_VALUE
				: snapshot.activeTicks() + delta;
	}

	private static void advanceToHealthStage(WorldInterfaceState.MutableState state, double healthRatio) {
		WorldInterfaceStage startingStage = state.stage();
		WorldInterfaceStage desired = WorldInterfaceStage.advanceCombatStage(state.stage(), healthRatio);
		while (state.stage().wireId() < desired.wireId()) state.transitionTo(nextCombatStage(state.stage()));
		if (desired != state.stage()) throw new IllegalStateException("phase_advance_failed");
		if (state.stage() != startingStage) {
			state.clearCurrentAttack();
			state.setRecoveryGraceTicks(40);
			state.setActionSchedule(state.actionSequence(), state.lastActionWireId(), state.activeTicks() + 40L);
		}
	}

	private static void advanceToPhaseThree(WorldInterfaceState.MutableState state) {
		while (state.stage().wireId() < WorldInterfaceStage.PHASE_3.wireId()) {
			state.transitionTo(nextCombatStage(state.stage()));
		}
	}

	private static WorldInterfaceStage nextCombatStage(WorldInterfaceStage stage) {
		return switch (stage) {
			case PHASE_1 -> WorldInterfaceStage.PHASE_2;
			case PHASE_2 -> WorldInterfaceStage.PHASE_3;
			default -> throw new IllegalStateException("not_advancing_combat");
		};
	}

	private static boolean bossMatches(WorldInterfaceState.Snapshot snapshot, WorldInterfaceEntity boss) {
		return snapshot.valid() && snapshot.present()
				&& snapshot.encounterId().filter(id -> id.equals(boss.encounterId())).isPresent()
				&& snapshot.bossUuid().filter(id -> id.equals(boss.getUUID())).isPresent();
	}

	private static boolean duplicateProjectileHit(MinecraftServer server, UUID projectileId, long tick) {
		Map<UUID, Long> hits = PROJECTILE_HITS.computeIfAbsent(server, ignored -> new HashMap<>());
		Long previous = hits.get(projectileId);
		if (previous != null && previous + 40L >= tick) return true;
		hits.put(projectileId, tick);
		if (hits.size() > 256) hits.entrySet().removeIf(entry -> entry.getValue() + 40L < tick);
		return false;
	}

	private static boolean duplicateMeleeHit(MinecraftServer server, UUID attackerId, long tick) {
		Map<UUID, Long> hits = MELEE_HITS.computeIfAbsent(server, ignored -> new HashMap<>());
		Long previous = hits.put(attackerId, tick);
		if (hits.size() > 256) hits.entrySet().removeIf(entry -> entry.getValue() < tick);
		return previous != null && previous == tick;
	}

	private static UUID deterministicEntityUuid(String role, UUID encounterId) {
		return UUID.nameUUIDFromBytes(("thefourthfrequency:world_interface:" + role + ":" + encounterId)
				.getBytes(StandardCharsets.UTF_8));
	}

	private static int formForStage(WorldInterfaceStage stage) {
		return switch (stage) {
			case PHASE_2 -> WorldInterfaceEntity.FORM_CONSUMING;
			case PHASE_3, SUCCESS_RESOLUTION, FAILURE_RESOLUTION, PORTAL_OPEN, COMPLETE ->
					WorldInterfaceEntity.FORM_INTERFACE;
			default -> WorldInterfaceEntity.FORM_LISTENING;
		};
	}

	private static int outcomeWire(WorldInterfaceState.Outcome outcome) {
		return switch (outcome) {
			case NONE -> WorldInterfaceProtocol.Outcome.NONE.wireId();
			case SUCCESS -> WorldInterfaceProtocol.Outcome.SUCCESS.wireId();
			case FAILURE -> WorldInterfaceProtocol.Outcome.FAILURE.wireId();
		};
	}

	private static List<WorldInterfaceState.Gate> indexedGates(EndBossArenaService.PreparedArena arena) {
		List<WorldInterfaceState.Gate> gates = new ArrayList<>(EndBossArenaService.GATEWAY_COUNT);
		for (int index = 0; index < arena.gatewayCorePositions().size(); index++) {
			gates.add(new WorldInterfaceState.Gate(index, arena.gatewayCorePositions().get(index),
					WorldInterfaceGatewayState.DORMANT));
		}
		return List.copyOf(gates);
	}

	private static List<WorldInterfaceState.Anchor> indexedAnchors(EndBossArenaService.PreparedArena arena) {
		return arena.anchors().stream().sorted(Comparator.comparingInt(EndBossArenaService.AnchorSlot::index))
				.map(anchor -> new WorldInterfaceState.Anchor(anchor.index(), anchor.position(),
						Optional.of(anchor.crystalUuid()), false)).toList();
	}

	private static void bindCore(ServerLevel level, WorldInterfaceState.Snapshot snapshot) {
		if (snapshot.encounterId().isPresent()
				&& level.getBlockEntity(snapshot.altarCenter()) instanceof ResonanceCoreBlockEntity core) {
			core.bind(snapshot.encounterId().orElseThrow(), snapshot.revision());
		}
	}

	private static WorldInterfaceProtocol.AltarStatus altarStatus(String reason) {
		return WorldInterfaceProtocol.AltarStatus.fromReason(reason);
	}

	private static void recordAltarOpening(ServerPlayer player, ServerLevel level, BlockPos center) {
		FrequencyWorldData data = FrequencyWorldData.get(level.getServer());
		if (data.terminalRecord(player.getUUID()).isPresent()) {
			data.updateTerminalRecord(player.getUUID(), record -> {
				record.putBoolean(TerminalData.ALTAR_STARTED, true);
				record.putBoolean(TerminalData.PORTAL_ROOM_FOUND, true);
				record.putLong(TerminalData.PORTAL_ROOM_POSITION, center.asLong());
				record.putString(TerminalData.PORTAL_ROOM_DIMENSION, level.dimension().identifier().toString());
				record.putInt(TerminalData.SURVIVAL_MILESTONE_MASK,
						record.getIntOr(TerminalData.SURVIVAL_MILESTONE_MASK, 0)
								| SurvivalMilestone.FOUND_STRONGHOLD.mask());
			});
		}
		TerminalSignalService.record(player, SignalBand.UNKNOWN, "world_interface_altar", 0, 2, true);
		player.displayClientMessage(Component.translatable(
				"message.thefourthfrequency.world_interface.altar_prepared"), false);
	}

	private static void markDefeatedMilestone(MinecraftServer server, Set<UUID> roster) {
		FrequencyWorldData data = FrequencyWorldData.get(server);
		for (UUID playerId : roster) {
			if (data.terminalRecord(playerId).isEmpty()) continue;
			data.updateTerminalRecord(playerId, record -> record.putInt(TerminalData.SURVIVAL_MILESTONE_MASK,
					record.getIntOr(TerminalData.SURVIVAL_MILESTONE_MASK, 0)
							| SurvivalMilestone.DEFEATED_BOSS.mask()));
		}
	}

	private static void broadcast(MinecraftServer server, String key, Object... arguments) {
		Component message = Component.translatable(key, arguments);
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			player.displayClientMessage(message, false);
		}
	}

	/* Prelude compatibility: the former Misread End boss is deliberately unreachable. */
	@Deprecated public static boolean isEncounterBoss(MisreadBodyEntity body) { return false; }
	@Deprecated public static Optional<ServerPlayer> selectTarget(MisreadBodyEntity body) { return Optional.empty(); }
	@Deprecated public static void delayNextAttack(MisreadBodyEntity body, int ticks) { }
	@Deprecated public static void onBossDefeated(MisreadBodyEntity body, ServerPlayer victor) { }
	@Deprecated public static Optional<MisreadBodyEntity> findEncounterBoss(ServerLevel level) { return Optional.empty(); }
}
