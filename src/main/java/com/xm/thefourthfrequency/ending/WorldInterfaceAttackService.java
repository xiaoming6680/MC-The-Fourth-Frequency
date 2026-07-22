package com.xm.thefourthfrequency.ending;

import com.mojang.serialization.DynamicOps;
import com.xm.thefourthfrequency.audio.AudioService;
import com.xm.thefourthfrequency.audio.ModSounds;
import com.xm.thefourthfrequency.entity.WorldInterfaceEnergyOrbEntity;
import com.xm.thefourthfrequency.entity.WorldInterfaceEntity;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Server-authoritative transient executor for the nine world-interface actions. */
public final class WorldInterfaceAttackService {
	private static final int LASER_WARNING_TICKS = 45;
	private static final int ORB_TRACKING_TICKS = 100;
	private static final int GRAB_WARNING_TICKS = 30;
	private static final int GRAB_HOLD_TICKS = 40;
	private static final int MENTAL_WARNING_TICKS = 40;
	private static final int MENTAL_EFFECT_TICKS = 80;
	private static final int WEAPON_WARNING_TICKS = 35;
	private static final int WEAPON_CUSTODY_TICKS = 160;
	private static final int HOTBAR_WARNING_TICKS = 40;
	private static final int HOTBAR_STEP_TICKS = 8;
	private static final int HOTBAR_SLOTS = 9;
	private static final int DROP_PROTECTION_TICKS = 600;
	private static final int ARROW_VOLLEY_INTERVAL = 40;
	private static final int REFLECTED_ARROW_COUNT = 20;
	private static final int REFLECTED_ARROW_TTL = 80;
	private static final int MAX_REFLECTED_ARROWS = 40;
	private static final double REFLECTED_ARROW_DAMAGE = 0.5D;
	private static final String REFLECTED_ARROW_TAG = "thefourthfrequency.world_interface_reflected";
	private static final String CAPTURED_ARROW_TAG = "thefourthfrequency.world_interface_captured";
	private static final String RECOVERY_ITEM_TAG_PREFIX = "thefourthfrequency.recovery.";
	private static final Component EVICTION_REASON = Component.literal("给   我   滚   开");

	private static final Map<UUID, AttackRuntime> ACTIVE = new ConcurrentHashMap<>();
	private static final Map<UUID, ProjectileLease> PROJECTILES = new ConcurrentHashMap<>();
	private static final Map<UUID, DropLease> DROPS = new ConcurrentHashMap<>();
	private static final Set<MinecraftServer> DROP_LEASES_RECONCILED = ConcurrentHashMap.newKeySet();
	private static boolean initialized;

	private WorldInterfaceAttackService() {
	}

	public static synchronized void initialize() {
		if (initialized) return;
		initialized = true;
		ServerTickEvents.END_SERVER_TICK.register(WorldInterfaceAttackService::maintenanceTick);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			WorldInterfaceState.Snapshot snapshot = WorldInterfaceState.snapshot(server);
			snapshot.encounterId().ifPresent(id -> onDisconnect(handler.player, id));
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			WorldInterfaceState.Snapshot snapshot = WorldInterfaceState.snapshot(server);
			snapshot.encounterId().ifPresent(id -> cancelAndRestore(server, id));
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			ACTIVE.clear();
			PROJECTILES.clear();
			DROPS.clear();
			DROP_LEASES_RECONCILED.remove(server);
		});
	}

	public static AttackStart begin(ServerLevel level, WorldInterfaceEntity boss,
			WorldInterfaceState.Snapshot snapshot, WorldInterfaceAction action,
			List<ServerPlayer> proposedTargets, long activeTick, long actionSequence) {
		initialize();
		Objects.requireNonNull(level, "level");
		Objects.requireNonNull(boss, "boss");
		Objects.requireNonNull(snapshot, "snapshot");
		Objects.requireNonNull(action, "action");
		Objects.requireNonNull(proposedTargets, "proposedTargets");
		if (activeTick < 0L || actionSequence < 0L) throw new IllegalArgumentException("Negative attack clock");
		if (!snapshot.present() || !snapshot.valid() || !action.isUnlockedAt(snapshot.stage())) {
			throw new IllegalArgumentException("Action is not valid for the current encounter stage");
		}
		UUID encounterId = snapshot.encounterId().orElseThrow();
		if (boss.encounterId() == null || !encounterId.equals(boss.encounterId())) {
			throw new IllegalArgumentException("Boss is not bound to this encounter");
		}
		if (ACTIVE.containsKey(encounterId)) throw new IllegalStateException("An encounter action is already active");

		List<UUID> targets = proposedTargets.stream()
				.filter(Objects::nonNull)
				.filter(player -> player.isAlive() && !player.isSpectator() && player.level() == level)
				.map(ServerPlayer::getUUID)
				.distinct()
				.sorted(Comparator.comparing(UUID::toString))
				.toList();
		if (requiresTarget(action) && targets.isEmpty()) {
			throw new IllegalArgumentException("Action requires at least one live target");
		}

		long seed = mix(snapshot.deterministicSeed() ^ actionSequence * 0x9E3779B97F4A7C15L
				^ ((long) action.wireId() << 56));
		int duration = durationTicks(action);
		AttackRuntime runtime = new AttackRuntime(encounterId, boss.getUUID(), action, activeTick,
				activeTick + duration, actionSequence, seed, targets, boss.blockPosition(),
				snapshot.arenaCenter(), snapshot.safeSpawn());
		if (action == WorldInterfaceAction.ENERGY_ORB) {
			ServerPlayer target = level.getServer().getPlayerList().getPlayer(targets.getFirst());
			if (target == null) throw new IllegalStateException("Energy-orb target left before spawn");
			WorldInterfaceEnergyOrbEntity orb = WorldInterfaceEnergyOrbEntity.create(level, boss, encounterId, target);
			runtime.orbId = orb.getUUID();
		}
		if (ACTIVE.putIfAbsent(encounterId, runtime) != null) {
			if (runtime.orbId != null && level.getEntity(runtime.orbId) != null) level.getEntity(runtime.orbId).discard();
			throw new IllegalStateException("An encounter action started concurrently");
		}

		boss.showAction(action.wireId(), level.getGameTime(), duration);
		playActionCue(level, boss.blockPosition(), action, 0.65F, 0.82F);
		WorldInterfaceState.AttackEnvelope envelope = runtime.envelope();
		return new AttackStart(envelope, action, duration, Set.copyOf(targets), seed);
	}

	public static AttackTick tick(ServerLevel level, WorldInterfaceEntity boss,
			WorldInterfaceState.Snapshot snapshot, long activeTick) {
		Objects.requireNonNull(snapshot, "snapshot");
		UUID encounterId = snapshot.encounterId().orElse(null);
		if (encounterId == null) return AttackTick.cancelledRestart();
		AttackRuntime runtime = ACTIVE.get(encounterId);
		if (runtime == null) {
			boss.clearAction();
			return AttackTick.cancelledRestart();
		}
		Optional<WorldInterfaceState.AttackEnvelope> stored = snapshot.currentAttack();
		if (stored.isEmpty() || stored.get().sequence() != runtime.sequence
				|| stored.get().actionWireId() != runtime.action.wireId()
				|| !runtime.bossId.equals(boss.getUUID())) {
			cancelRuntime(level.getServer(), runtime, true);
			return AttackTick.cancelledRestart();
		}
		invalidateUnavailableTargets(level, runtime);
		long elapsed = Math.max(0L, activeTick - runtime.startedActiveTick);
		boolean complete = switch (runtime.action) {
			case LASER_SWEEP -> tickLaser(level, boss, runtime, elapsed);
			case ENERGY_ORB -> tickOrb(level, runtime, elapsed);
			case GRAB_SLAM -> tickGrabSlam(level, boss, runtime, elapsed);
			case MENTAL_ASSAULT -> tickMental(level, boss, runtime, elapsed);
			case CHARGE_WEAPON_STEAL -> tickWeaponSteal(level, boss, snapshot, runtime, elapsed);
			case GRAB_THROW -> tickGrabThrow(level, boss, runtime, elapsed);
			case GAZE_HOTBAR_CLEAR -> tickHotbar(level, snapshot, runtime, elapsed);
			case ARROW_REFLECTION -> tickArrowReflection(level, boss, runtime, elapsed);
			case FORCED_EVICTION -> tickEviction(level, runtime, elapsed);
		};
		WorldInterfaceState.AttackEnvelope replacement = runtime.envelope();
		if (!complete && activeTick < runtime.dueActiveTick) {
			return new AttackTick(AttackStatus.CONTINUE, Optional.of(replacement));
		}
		finishRuntime(level.getServer(), runtime, false);
		boss.clearAction();
		return new AttackTick(AttackStatus.COMPLETE, Optional.of(replacement));
	}

	/** Called before virtual damage routing when a player arrow touches the root or one of its parts. */
	public static boolean captureArrow(ServerLevel level, WorldInterfaceEntity boss, AbstractArrow arrow) {
		if (boss.encounterId() == null || arrow == null || arrow.isRemoved()) return false;
		AttackRuntime runtime = ACTIVE.get(boss.encounterId());
		if (runtime == null || runtime.action != WorldInterfaceAction.ARROW_REFLECTION) return false;
		if (!(arrow.getOwner() instanceof ServerPlayer) || arrow.getTags().contains(REFLECTED_ARROW_TAG)) return false;
		if (!runtime.capturedArrows.add(arrow.getUUID())) return true;
		arrow.setDeltaMovement(Vec3.ZERO);
		arrow.setNoGravity(true);
		arrow.setNoPhysics(true);
		arrow.pickup = AbstractArrow.Pickup.DISALLOWED;
		arrow.addTag(CAPTURED_ARROW_TAG);
		arrow.setPos(boss.getX(), boss.getEyeY(), boss.getZ());
		level.sendParticles(ParticleTypes.ENCHANT, arrow.getX(), arrow.getY(), arrow.getZ(), 10,
				0.3D, 0.3D, 0.3D, 0.04D);
		return true;
	}

	public static int cancelAndRestore(MinecraftServer server, UUID encounterId) {
		AttackRuntime runtime = ACTIVE.remove(encounterId);
		if (runtime != null) cancelRuntime(server, runtime, true);
		return restoreRecoveryEntries(server, encounterId, null);
	}

	public static void onDisconnect(ServerPlayer player, UUID encounterId) {
		MinecraftServer server = Objects.requireNonNull(player.level().getServer(), "server");
		AttackRuntime runtime = ACTIVE.get(encounterId);
		if (runtime != null) {
			runtime.unavailableTargets.add(player.getUUID());
			releasePlayerControl(server, runtime, player.getUUID(), true);
			restoreForcedHotbarSlot(runtime, player);
			if (runtime.weapon != null && runtime.weapon.playerId.equals(player.getUUID())) runtime.weapon = null;
		}
		restoreRecoveryEntries(server, encounterId, player.getUUID());
	}

	public static void onDeath(ServerPlayer player, UUID encounterId) {
		MinecraftServer server = Objects.requireNonNull(player.level().getServer(), "server");
		AttackRuntime runtime = ACTIVE.get(encounterId);
		if (runtime != null) {
			runtime.unavailableTargets.add(player.getUUID());
			releasePlayerControl(server, runtime, player.getUUID(), false);
			restoreForcedHotbarSlot(runtime, player);
			if (runtime.weapon != null && runtime.weapon.playerId.equals(player.getUUID())) runtime.weapon = null;
		}
		restoreRecoveryEntries(server, encounterId, player.getUUID());
	}

	public static int onRestart(MinecraftServer server, UUID encounterId) {
		AttackRuntime runtime = ACTIVE.remove(encounterId);
		if (runtime != null) cancelRuntime(server, runtime, true);
		clearRestartTransients(server, encounterId);
		return restoreRecoveryEntries(server, encounterId, null);
	}

	private static void clearRestartTransients(MinecraftServer server, UUID encounterId) {
		ServerLevel level = server.getLevel(Level.END);
		if (level == null) return;
		List<Entity> stale = new ArrayList<>();
		for (Entity entity : level.getAllEntities()) {
			if (entity instanceof AbstractArrow arrow
					&& (arrow.getTags().contains(REFLECTED_ARROW_TAG)
					|| arrow.getTags().contains(CAPTURED_ARROW_TAG))) {
				stale.add(arrow);
			} else if (entity instanceof WorldInterfaceEnergyOrbEntity orb
					&& encounterId.equals(orb.encounterId())) {
				stale.add(orb);
			}
		}
		stale.forEach(Entity::discard);
		PROJECTILES.entrySet().removeIf(entry -> encounterId.equals(entry.getValue().encounterId));
	}

	public static int restorePendingFor(ServerPlayer player, UUID encounterId) {
		return restoreRecoveryEntries(Objects.requireNonNull(player.level().getServer(), "server"),
				encounterId, player.getUUID());
	}

	private static boolean tickLaser(ServerLevel level, WorldInterfaceEntity boss,
			AttackRuntime runtime, long elapsed) {
		if (!runtime.damageApplied && elapsed >= LASER_WARNING_TICKS) {
			ServerPlayer target = onlineTarget(level, runtime, 0);
			Vec3 start = boss.getEyePosition();
			Vec3 end = target == null ? start.add(boss.getLookAngle().scale(48.0D)) : target.getEyePosition();
			if (target != null) damage(level, boss, target, 12.0F);
			AABB sweepBounds = new AABB(start, end).inflate(4.0D);
			for (ServerPlayer nearby : level.getEntitiesOfClass(ServerPlayer.class, sweepBounds,
					candidate -> candidate.isAlive() && !candidate.isSpectator())) {
				if (nearby != target && distanceToSegmentSqr(nearby.getEyePosition(), start, end) <= 16.0D) {
					damage(level, boss, nearby, 6.0F);
				}
			}
			List<BlockPos> scarPath = laserCandidates(level, start, end);
			EndBossArenaService.queueLaserScar(level, scarPath, runtime.seed);
			for (Vec3 burst : directionalLaserBursts(start, end)) {
				level.sendParticles(ParticleTypes.EXPLOSION, burst.x, burst.y, burst.z,
						4, 0.55D, 0.30D, 0.55D, 0.025D);
			}
			playActionCue(level, BlockPos.containing(end), runtime.action, 0.92F, 1.05F);
			runtime.damageApplied = true;
			runtime.cursor = scarPath.size();
		}
		return elapsed >= LASER_WARNING_TICKS + 1L;
	}

	private static boolean tickOrb(ServerLevel level, AttackRuntime runtime, long elapsed) {
		Entity orb = runtime.orbId == null ? null : level.getEntity(runtime.orbId);
		if (orb == null || orb.isRemoved()) return true;
		if (onlineTarget(level, runtime, 0) == null) {
			orb.discard();
			return true;
		}
		if (elapsed >= ORB_TRACKING_TICKS) {
			orb.discard();
			return true;
		}
		return false;
	}

	private static boolean tickGrabSlam(ServerLevel level, WorldInterfaceEntity boss,
			AttackRuntime runtime, long elapsed) {
		ServerPlayer target = onlineTarget(level, runtime, 0);
		if (target == null) return elapsed >= GRAB_WARNING_TICKS;
		if (elapsed >= GRAB_WARNING_TICKS && runtime.control == null) {
			runtime.control = beginControl(level.getServer(), runtime, target);
		}
		if (runtime.control != null && elapsed < GRAB_WARNING_TICKS + GRAB_HOLD_TICKS) {
			holdAtBoss(target, boss, 4.0D);
		}
		if (!runtime.damageApplied && runtime.control != null
				&& elapsed >= GRAB_WARNING_TICKS + GRAB_HOLD_TICKS) {
			Vec3 impact = safeLanding(level, runtime, target.position());
			target.teleportTo(impact.x, impact.y + 0.25D, impact.z);
			finishControl(level.getServer(), runtime, target, false);
			damage(level, boss, target, 12.0F);
			for (ServerPlayer nearby : level.getEntitiesOfClass(ServerPlayer.class,
					new AABB(impact, impact).inflate(4.0D), candidate -> candidate != target
							&& candidate.isAlive() && !candidate.isSpectator())) {
				if (nearby.position().distanceToSqr(impact) <= 16.0D) damage(level, boss, nearby, 6.0F);
			}
			level.sendParticles(ParticleTypes.EXPLOSION, impact.x, impact.y, impact.z, 24,
					1.8D, 0.3D, 1.8D, 0.08D);
			runtime.damageApplied = true;
		}
		return elapsed >= GRAB_WARNING_TICKS + GRAB_HOLD_TICKS + 1L;
	}

	private static boolean tickMental(ServerLevel level, WorldInterfaceEntity boss,
			AttackRuntime runtime, long elapsed) {
		if (!runtime.damageApplied && elapsed >= MENTAL_WARNING_TICKS) {
			for (UUID targetId : runtime.targets) {
				if (runtime.unavailableTargets.contains(targetId)) continue;
				ServerPlayer target = level.getServer().getPlayerList().getPlayer(targetId);
				if (target != null && target.level() == level && target.isAlive()) damage(level, boss, target, 4.0F);
			}
			playActionCue(level, boss.blockPosition(), runtime.action, 0.95F, 0.70F);
			runtime.damageApplied = true;
		}
		return elapsed >= MENTAL_WARNING_TICKS + MENTAL_EFFECT_TICKS;
	}

	private static boolean tickWeaponSteal(ServerLevel level, WorldInterfaceEntity boss,
			WorldInterfaceState.Snapshot snapshot, AttackRuntime runtime, long elapsed) {
		ServerPlayer target = onlineTarget(level, runtime, 0);
		if (!runtime.damageApplied && elapsed >= WEAPON_WARNING_TICKS) {
			if (target != null) {
				damage(level, boss, target, 10.0F);
				runtime.weapon = takeSelectedWeapon(level.getServer(), snapshot, runtime, target);
			}
			runtime.damageApplied = true;
		}
		if (runtime.weapon != null && !runtime.weaponUsed && elapsed >= WEAPON_WARNING_TICKS + 40L) {
			simulateWeaponUse(level, boss, runtime, target);
			runtime.weaponUsed = true;
		}
		if (runtime.weapon != null && elapsed >= WEAPON_WARNING_TICKS + WEAPON_CUSTODY_TICKS) {
			deliverRecovery(level.getServer(), runtime.encounterId, runtime.weapon.recoveryId,
					target == null ? null : target.getUUID());
			runtime.weapon = null;
		}
		return elapsed >= WEAPON_WARNING_TICKS + WEAPON_CUSTODY_TICKS + 1L;
	}

	private static boolean tickGrabThrow(ServerLevel level, WorldInterfaceEntity boss,
			AttackRuntime runtime, long elapsed) {
		ServerPlayer grabbed = onlineTarget(level, runtime, 0);
		if (grabbed == null) return elapsed >= GRAB_WARNING_TICKS;
		if (elapsed >= GRAB_WARNING_TICKS && runtime.control == null) {
			runtime.control = beginControl(level.getServer(), runtime, grabbed);
		}
		if (runtime.control != null && elapsed < GRAB_WARNING_TICKS + 20L) holdAtBoss(grabbed, boss, 5.0D);
		long throwStartTick = GRAB_WARNING_TICKS + 20L;
		long throwEndTick = GRAB_WARNING_TICKS + 30L;
		if (!runtime.damageApplied && runtime.control != null && elapsed >= throwStartTick) {
			ServerPlayer other = onlineTarget(level, runtime, 1);
			if (runtime.throwLanding == null) {
				Vec3 desired = other == null ? runtime.safeSpawn.getCenter() : other.position();
				runtime.throwStart = grabbed.position();
				runtime.throwLanding = safeLanding(level, runtime, desired);
			}
			double progress = Math.clamp((elapsed - throwStartTick)
					/ (double) Math.max(1L, throwEndTick - throwStartTick), 0.0D, 1.0D);
			Vec3 path = runtime.throwStart.add(runtime.throwLanding.subtract(runtime.throwStart).scale(progress))
					.add(0.0D, Math.sin(progress * Math.PI) * 6.0D, 0.0D);
			grabbed.setNoGravity(true);
			grabbed.setDeltaMovement(Vec3.ZERO);
			grabbed.teleportTo(path.x, path.y, path.z);
			if (progress >= 1.0D) {
				grabbed.teleportTo(runtime.throwLanding.x, runtime.throwLanding.y + 0.25D,
						runtime.throwLanding.z);
				grabbed.setDeltaMovement(Vec3.ZERO);
				finishControl(level.getServer(), runtime, grabbed, false);
				damage(level, boss, grabbed, 10.0F);
				if (other != null && other != grabbed
						&& other.position().distanceToSqr(runtime.throwLanding) <= 16.0D) {
					damage(level, boss, other, 10.0F);
				}
				runtime.damageApplied = true;
			}
		}
		return elapsed >= throwEndTick;
	}

	private static boolean tickHotbar(ServerLevel level, WorldInterfaceState.Snapshot snapshot,
			AttackRuntime runtime, long elapsed) {
		ServerPlayer target = onlineTarget(level, runtime, 0);
		if (target != null && runtime.originalSelectedSlot < 0) {
			runtime.originalSelectedSlot = target.getInventory().getSelectedSlot();
		}
		long firstDrop = HOTBAR_WARNING_TICKS + HOTBAR_STEP_TICKS;
		if (target != null && elapsed >= firstDrop && (elapsed - firstDrop) % HOTBAR_STEP_TICKS == 0L
				&& runtime.cursor < HOTBAR_SLOTS) {
			int slot = runtime.cursor++;
			target.getInventory().setSelectedSlot(slot);
			dropHotbarSlot(level, snapshot, runtime, target, slot);
		}
		if (elapsed >= HOTBAR_WARNING_TICKS + HOTBAR_STEP_TICKS * HOTBAR_SLOTS + 1L) {
			if (target != null && runtime.originalSelectedSlot >= 0) {
				target.getInventory().setSelectedSlot(runtime.originalSelectedSlot);
			}
			return true;
		}
		return false;
	}

	private static boolean tickArrowReflection(ServerLevel level, WorldInterfaceEntity boss,
			AttackRuntime runtime, long elapsed) {
		AABB catchment = boss.getBoundingBox().inflate(13.0D, 3.0D, 13.0D);
		for (AbstractArrow arrow : level.getEntitiesOfClass(AbstractArrow.class, catchment,
				candidate -> candidate.isAlive() && !candidate.getTags().contains(REFLECTED_ARROW_TAG))) {
			captureArrow(level, boss, arrow);
		}
		for (UUID arrowId : runtime.capturedArrows) {
			Entity arrow = level.getEntity(arrowId);
			if (arrow != null) arrow.setPos(boss.getX(), boss.getEyeY(), boss.getZ());
		}
		if (elapsed > 0L && elapsed % ARROW_VOLLEY_INTERVAL == 0L
				&& runtime.lastArrowVolley != elapsed && !runtime.capturedArrows.isEmpty()) {
			if (activeReflectedArrowCount(level) <= MAX_REFLECTED_ARROWS - REFLECTED_ARROW_COUNT) {
				spawnReflectedVolley(level, boss, runtime);
				runtime.lastArrowVolley = (int) elapsed;
			}
		}
		return elapsed >= 120L;
	}

	private static boolean tickEviction(ServerLevel level, AttackRuntime runtime, long elapsed) {
		if (!runtime.damageApplied && elapsed >= WorldInterfaceActionScheduler.FORCED_EVICTION_WARNING_TICKS) {
			MinecraftServer server = level.getServer();
			for (UUID targetId : runtime.targets) {
				if (runtime.unavailableTargets.contains(targetId)) continue;
				ServerPlayer target = server.getPlayerList().getPlayer(targetId);
				if (target == null || target.level() != level
						|| server.isSingleplayerOwner(target.nameAndId())) continue;
				target.connection.disconnect(EVICTION_REASON);
			}
			runtime.damageApplied = true;
		}
		return elapsed >= WorldInterfaceActionScheduler.FORCED_EVICTION_WARNING_TICKS + 1L;
	}

	private static ControlLease beginControl(MinecraftServer server, AttackRuntime runtime, ServerPlayer player) {
		UUID id = deterministicRecoveryId(runtime, player.getUUID(), "control", 0);
		CompoundTag payload = new CompoundTag();
		payload.putString("phase", "active");
		payload.putBoolean("original_no_gravity", player.isNoGravity());
		payload.putDouble("x", player.getX());
		payload.putDouble("y", player.getY());
		payload.putDouble("z", player.getZ());
		payload.putDouble("yaw", player.getYRot());
		payload.putDouble("pitch", player.getXRot());
		WorldInterfaceState.RecoveryEntry entry = new WorldInterfaceState.RecoveryEntry(
				id, player.getUUID(), "control", payload, false);
		if (!addRecovery(server, runtime.encounterId, entry)) return null;
		ControlLease lease = new ControlLease(id, player.getUUID(), player.isNoGravity(), player.position(),
				player.getYRot(), player.getXRot());
		player.setNoGravity(true);
		player.setDeltaMovement(Vec3.ZERO);
		return lease;
	}

	private static void holdAtBoss(ServerPlayer player, WorldInterfaceEntity boss, double heightOffset) {
		player.setNoGravity(true);
		player.setDeltaMovement(Vec3.ZERO);
		player.teleportTo(boss.getX(), boss.getY() + heightOffset, boss.getZ());
	}

	private static void finishControl(MinecraftServer server, AttackRuntime runtime,
			ServerPlayer player, boolean restorePosition) {
		ControlLease lease = runtime.control;
		if (lease == null) return;
		player.setNoGravity(lease.originalNoGravity);
		if (restorePosition) {
			player.teleportTo(lease.originalPosition.x, lease.originalPosition.y, lease.originalPosition.z);
			player.setYRot(lease.yaw);
			player.setXRot(lease.pitch);
		}
		removeRecovery(server, runtime.encounterId, lease.recoveryId);
		runtime.control = null;
	}

	private static WeaponLease takeSelectedWeapon(MinecraftServer server,
			WorldInterfaceState.Snapshot snapshot, AttackRuntime runtime, ServerPlayer player) {
		int slot = player.getInventory().getSelectedSlot();
		ItemStack selected = player.getInventory().getItem(slot);
		if (!isValidWeapon(selected)) return null;
		ItemStack exact = selected.copy();
		UUID recoveryId = deterministicRecoveryId(runtime, player.getUUID(), "weapon", slot);
		CompoundTag payload = itemPayload(server, exact, slot, matchingItemCount(player, exact), "prepared");
		payload.putString("weapon_kind", weaponKind(exact));
		WorldInterfaceState.RecoveryEntry prepared = new WorldInterfaceState.RecoveryEntry(
				recoveryId, player.getUUID(), "weapon", payload, false);
		if (!addRecovery(server, runtime.encounterId, prepared)) return null;

		ItemStack removed = player.getInventory().removeItemNoUpdate(slot);
		if (!ItemStack.matches(removed, exact)) {
			restoreStackImmediately(player, slot, removed);
			removeRecovery(server, runtime.encounterId, recoveryId);
			return null;
		}
		payload.putString("phase", "custody");
		WorldInterfaceState.RecoveryEntry custody = new WorldInterfaceState.RecoveryEntry(
				recoveryId, player.getUUID(), "weapon", payload, false);
		if (!replaceRecovery(server, runtime.encounterId, custody)) {
			restoreStackImmediately(player, slot, removed);
			removeRecovery(server, runtime.encounterId, recoveryId);
			return null;
		}
		return new WeaponLease(recoveryId, player.getUUID(), slot, removed, payload.getStringOr("weapon_kind", "tool"));
	}

	private static void simulateWeaponUse(ServerLevel level, WorldInterfaceEntity boss,
			AttackRuntime runtime, ServerPlayer target) {
		if (runtime.weapon == null) return;
		if ("ranged".equals(runtime.weapon.kind) && target != null) {
			Arrow arrow = EntityType.ARROW.create(level, EntitySpawnReason.EVENT);
			if (arrow != null) {
				arrow.setOwner(boss);
				arrow.setPos(boss.getX(), boss.getEyeY(), boss.getZ());
				Vec3 direction = target.getEyePosition().subtract(arrow.position());
				arrow.shoot(direction.x, direction.y, direction.z, 1.55F, 0.0F);
				arrow.setBaseDamage(2.0D);
				arrow.pickup = AbstractArrow.Pickup.DISALLOWED;
				arrow.addTag(REFLECTED_ARROW_TAG);
				if (level.addFreshEntity(arrow)) {
					PROJECTILES.put(arrow.getUUID(), new ProjectileLease(runtime.encounterId,
							level.getGameTime() + REFLECTED_ARROW_TTL));
				}
			}
		} else if (target != null) {
			Vec3 away = target.position().subtract(boss.position());
			target.knockback(0.65D, -away.x, -away.z);
		}
		playActionCue(level, boss.blockPosition(), runtime.action, 0.82F, 1.12F);
	}

	private static void dropHotbarSlot(ServerLevel level, WorldInterfaceState.Snapshot snapshot,
			AttackRuntime runtime, ServerPlayer player, int slot) {
		ItemStack stack = player.getInventory().getItem(slot);
		if (stack.isEmpty()) return;
		ItemStack exact = stack.copy();
		UUID recoveryId = deterministicRecoveryId(runtime, player.getUUID(), "hotbar", slot);
		CompoundTag payload = itemPayload(level.getServer(), exact, slot,
				matchingItemCount(player, exact), "prepared");
		payload.putLong("item_position", player.blockPosition().asLong());
		payload.putString("item_dimension", level.dimension().identifier().toString());
		WorldInterfaceState.RecoveryEntry prepared = new WorldInterfaceState.RecoveryEntry(
				recoveryId, player.getUUID(), "hotbar_drop", payload, false);
		if (!addRecovery(level.getServer(), runtime.encounterId, prepared)) return;

		ItemStack removed = player.getInventory().removeItemNoUpdate(slot);
		if (!ItemStack.matches(removed, exact)) {
			restoreStackImmediately(player, slot, removed);
			removeRecovery(level.getServer(), runtime.encounterId, recoveryId);
			return;
		}
		ItemEntity item = new ItemEntity(level, player.getX(), player.getEyeY(), player.getZ(), removed);
		item.setDeltaMovement(Vec3.ZERO);
		item.setInvulnerable(true);
		item.setTarget(player.getUUID());
		item.setUnlimitedLifetime();
		item.addTag(RECOVERY_ITEM_TAG_PREFIX + recoveryId);
		if (!level.addFreshEntity(item)) {
			restoreStackImmediately(player, slot, removed);
			removeRecovery(level.getServer(), runtime.encounterId, recoveryId);
			return;
		}
		long releaseTick = level.getGameTime() + DROP_PROTECTION_TICKS;
		payload.putString("phase", "world");
		payload.putString("item_entity", item.getUUID().toString());
		payload.putLong("item_position", item.blockPosition().asLong());
		payload.putString("item_dimension", level.dimension().identifier().toString());
		payload.putLong("release_tick", releaseTick);
		WorldInterfaceState.RecoveryEntry world = new WorldInterfaceState.RecoveryEntry(
				recoveryId, player.getUUID(), "hotbar_drop", payload, false);
		if (!replaceRecovery(level.getServer(), runtime.encounterId, world)) {
			item.discard();
			restoreStackImmediately(player, slot, removed);
			removeRecovery(level.getServer(), runtime.encounterId, recoveryId);
			return;
		}
		DROPS.put(recoveryId, new DropLease(runtime.encounterId, recoveryId, player.getUUID(),
				item.getUUID(), releaseTick, level.dimension().identifier().toString(), item.blockPosition()));
		playActionCue(level, player.blockPosition(), runtime.action, 0.72F, 1.0F + slot * 0.025F);
	}

	private static void spawnReflectedVolley(ServerLevel level, WorldInterfaceEntity boss,
			AttackRuntime runtime) {
		for (UUID capturedId : List.copyOf(runtime.capturedArrows)) {
			Entity captured = level.getEntity(capturedId);
			if (captured != null) captured.discard();
		}
		runtime.capturedArrows.clear();
		List<ServerPlayer> liveTargets = runtime.targets.stream()
				.filter(id -> !runtime.unavailableTargets.contains(id))
				.map(level.getServer().getPlayerList()::getPlayer)
				.filter(Objects::nonNull)
				.filter(player -> player.level() == level && player.isAlive() && !player.isSpectator())
				.toList();
		for (int index = 0; index < REFLECTED_ARROW_COUNT; index++) {
			Arrow arrow = EntityType.ARROW.create(level, EntitySpawnReason.EVENT);
			if (arrow == null) throw new IllegalStateException("Unable to create reflected arrow " + index);
			arrow.setOwner(boss);
			double angle = Math.PI * 2.0D * index / REFLECTED_ARROW_COUNT;
			arrow.setPos(boss.getX() + Math.cos(angle) * 2.0D, boss.getEyeY() + 6.0D,
					boss.getZ() + Math.sin(angle) * 2.0D);
			Vec3 target;
			if (liveTargets.isEmpty()) {
				target = runtime.arenaCenter.getCenter().add(Math.cos(angle) * 36.0D, 2.0D,
						Math.sin(angle) * 36.0D);
			} else {
				ServerPlayer player = liveTargets.get(index % liveTargets.size());
				target = player.getEyePosition().add(Math.cos(angle) * 1.5D, 0.0D, Math.sin(angle) * 1.5D);
			}
			Vec3 direction = target.subtract(arrow.position());
			arrow.shoot(direction.x, direction.y, direction.z, 1.65F, 0.0F);
			arrow.setBaseDamage(REFLECTED_ARROW_DAMAGE);
			arrow.setCritArrow(false);
			arrow.pickup = AbstractArrow.Pickup.DISALLOWED;
			arrow.addTag(REFLECTED_ARROW_TAG);
			if (!level.addFreshEntity(arrow)) throw new IllegalStateException("Unable to spawn reflected arrow " + index);
			PROJECTILES.put(arrow.getUUID(), new ProjectileLease(runtime.encounterId,
					level.getGameTime() + REFLECTED_ARROW_TTL));
		}
		playActionCue(level, boss.blockPosition(), runtime.action, 0.88F, 1.08F);
	}

	private static int activeReflectedArrowCount(ServerLevel level) {
		PROJECTILES.entrySet().removeIf(entry -> {
			Entity entity = level.getEntity(entry.getKey());
			return entity == null || entity.isRemoved();
		});
		return PROJECTILES.size();
	}

	private static void finishRuntime(MinecraftServer server, AttackRuntime runtime, boolean restorePosition) {
		ACTIVE.remove(runtime.encounterId, runtime);
		ServerLevel level = server.getLevel(Level.END);
		if (level != null) {
			if (runtime.orbId != null) {
				Entity orb = level.getEntity(runtime.orbId);
				if (orb != null) orb.discard();
			}
			for (UUID arrowId : runtime.capturedArrows) {
				Entity arrow = level.getEntity(arrowId);
				if (arrow != null) arrow.discard();
			}
		}
		if (runtime.control != null) {
			ServerPlayer player = server.getPlayerList().getPlayer(runtime.control.playerId);
			if (player != null) finishControl(server, runtime, player, restorePosition);
		}
		if (runtime.weapon != null) {
			deliverRecovery(server, runtime.encounterId, runtime.weapon.recoveryId, runtime.weapon.playerId);
			runtime.weapon = null;
		}
		if (runtime.originalSelectedSlot >= 0 && !runtime.targets.isEmpty()) {
			ServerPlayer target = server.getPlayerList().getPlayer(runtime.targets.getFirst());
			if (target != null) target.getInventory().setSelectedSlot(runtime.originalSelectedSlot);
		}
	}

	private static void cancelRuntime(MinecraftServer server, AttackRuntime runtime, boolean restorePosition) {
		finishRuntime(server, runtime, restorePosition);
		discardEncounterProjectiles(server, runtime.encounterId);
		ServerLevel level = server.getLevel(Level.END);
		if (level != null && level.getEntity(runtime.bossId) instanceof WorldInterfaceEntity boss) boss.clearAction();
	}

	private static void discardEncounterProjectiles(MinecraftServer server, UUID encounterId) {
		ServerLevel level = server.getLevel(Level.END);
		for (Map.Entry<UUID, ProjectileLease> entry : List.copyOf(PROJECTILES.entrySet())) {
			if (!encounterId.equals(entry.getValue().encounterId)) continue;
			if (level != null) {
				Entity projectile = level.getEntity(entry.getKey());
				if (projectile != null) projectile.discard();
			}
			PROJECTILES.remove(entry.getKey(), entry.getValue());
		}
	}

	private static void releasePlayerControl(MinecraftServer server, AttackRuntime runtime,
			UUID playerId, boolean restorePosition) {
		if (runtime.control == null || !runtime.control.playerId.equals(playerId)) return;
		ServerPlayer player = server.getPlayerList().getPlayer(playerId);
		if (player != null) finishControl(server, runtime, player, restorePosition);
	}

	private static void restoreForcedHotbarSlot(AttackRuntime runtime, ServerPlayer player) {
		if (runtime.originalSelectedSlot < 0 || runtime.targets.isEmpty()
				|| !runtime.targets.getFirst().equals(player.getUUID())) return;
		player.getInventory().setSelectedSlot(runtime.originalSelectedSlot);
		runtime.originalSelectedSlot = -1;
	}

	private static int restoreRecoveryEntries(MinecraftServer server, UUID encounterId, UUID onlyOwner) {
		WorldInterfaceState.Snapshot snapshot = WorldInterfaceState.snapshot(server);
		if (!snapshot.valid() || snapshot.encounterId().filter(encounterId::equals).isEmpty()) return 0;
		int restored = 0;
		List<WorldInterfaceState.RecoveryEntry> entries = new ArrayList<>(snapshot.recoveryLedger());
		// Successive hotbar removals of identical stacks have descending baselines. Restoring the
		// smallest baseline first preserves the exact aggregate without duplicating picked-up drops.
		entries.sort(Comparator
				.comparing((WorldInterfaceState.RecoveryEntry entry) -> entry.ownerId().toString())
				.thenComparingInt(entry -> entry.payload().getIntOr("baseline_count", -1))
				.thenComparing(WorldInterfaceState.RecoveryEntry::kind)
				.thenComparing(entry -> entry.id().toString()));
		for (WorldInterfaceState.RecoveryEntry entry : entries) {
			if (entry.resolved() || (onlyOwner != null && !onlyOwner.equals(entry.ownerId()))) continue;
			ServerPlayer owner = server.getPlayerList().getPlayer(entry.ownerId());
			if (owner == null) continue;
			if ("control".equals(entry.kind())) {
				CompoundTag payload = entry.payload();
				owner.setNoGravity(payload.getBooleanOr("original_no_gravity", false));
				if ("active".equals(payload.getStringOr("phase", ""))) {
					owner.teleportTo(payload.getDoubleOr("x", owner.getX()),
							payload.getDoubleOr("y", owner.getY()), payload.getDoubleOr("z", owner.getZ()));
					owner.setYRot((float) payload.getDoubleOr("yaw", owner.getYRot()));
					owner.setXRot((float) payload.getDoubleOr("pitch", owner.getXRot()));
				}
				if (removeRecovery(server, encounterId, entry.id())) restored++;
			} else if ("weapon".equals(entry.kind()) || "hotbar_drop".equals(entry.kind())) {
				if (deliverRecovery(server, encounterId, entry.id(), owner.getUUID())) restored++;
			}
		}
		return restored;
	}

	private static boolean deliverRecovery(MinecraftServer server, UUID encounterId,
			UUID recoveryId, UUID preferredOwner) {
		WorldInterfaceState.RecoveryEntry entry = recoveryEntry(server, encounterId, recoveryId).orElse(null);
		if (entry == null) return true;
		ServerPlayer owner = server.getPlayerList().getPlayer(entry.ownerId());
		if (owner == null || (preferredOwner != null && !preferredOwner.equals(owner.getUUID()))) return false;
		CompoundTag payload = entry.payload();
		ItemStack stack;
		try {
			stack = decodeItem(server, payload);
		} catch (RuntimeException exception) {
			return false;
		}
		int baseline = payload.getIntOr("baseline_count", stack.getCount());
		Entity worldItem = findRecoveryItemEntity(server, entry.id(), payload);
		if (worldItem == null && "hotbar_drop".equals(entry.kind())
				&& payload.contains("item_position") && matchingItemCount(owner, stack) < baseline) {
			ServerLevel itemLevel = recoveryItemLevel(server, payload);
			if (itemLevel != null) {
				itemLevel.getChunkAt(BlockPos.of(payload.getLongOr("item_position", 0L)));
				worldItem = findRecoveryItemEntity(server, entry.id(), payload);
			}
		}
		if (worldItem != null) worldItem.discard();

		payload.putString("phase", "delivering");
		WorldInterfaceState.RecoveryEntry delivering = new WorldInterfaceState.RecoveryEntry(
				entry.id(), entry.ownerId(), entry.kind(), payload, false);
		if (!replaceRecovery(server, encounterId, delivering)) return false;
		int missingCount = Math.max(0, baseline - matchingItemCount(owner, stack));
		if (missingCount > 0) {
			ItemStack recoveryStack = stack.copyWithCount(Math.min(stack.getCount(), missingCount));
			ItemEntity dropped = giveExact(owner, payload.getIntOr("slot", -1), recoveryStack, entry.id());
			if (dropped != null) {
				payload.putString("phase", "world");
				payload.putString("item_entity", dropped.getUUID().toString());
				payload.putLong("item_position", dropped.blockPosition().asLong());
				payload.putString("item_dimension", dropped.level().dimension().identifier().toString());
				long release = ((ServerLevel) owner.level()).getGameTime() + DROP_PROTECTION_TICKS;
				payload.putLong("release_tick", release);
				WorldInterfaceState.RecoveryEntry world = new WorldInterfaceState.RecoveryEntry(
						entry.id(), entry.ownerId(), entry.kind(), payload, false);
				if (!replaceRecovery(server, encounterId, world)) {
					dropped.discard();
					return false;
				}
				DROPS.put(entry.id(), new DropLease(encounterId, entry.id(), entry.ownerId(),
						dropped.getUUID(), release, dropped.level().dimension().identifier().toString(),
						dropped.blockPosition()));
				return true;
			}
		}
		DROPS.remove(entry.id());
		return removeRecovery(server, encounterId, entry.id());
	}

	private static ItemEntity giveExact(ServerPlayer owner, int preferredSlot, ItemStack stack, UUID recoveryId) {
		if (preferredSlot >= 0 && preferredSlot < owner.getInventory().getContainerSize()
				&& owner.getInventory().getItem(preferredSlot).isEmpty()) {
			owner.getInventory().setItem(preferredSlot, stack);
			return null;
		}
		ItemStack remainder = stack.copy();
		owner.getInventory().add(remainder);
		if (remainder.isEmpty()) return null;
		ItemEntity dropped = owner.drop(remainder, false, true);
		if (dropped != null) {
			dropped.setDeltaMovement(Vec3.ZERO);
			dropped.setInvulnerable(true);
			dropped.setTarget(owner.getUUID());
			dropped.setUnlimitedLifetime();
			dropped.addTag(RECOVERY_ITEM_TAG_PREFIX + recoveryId);
		}
		return dropped;
	}

	private static Entity findRecoveryItemEntity(MinecraftServer server, UUID recoveryId, CompoundTag payload) {
		ServerLevel level = recoveryItemLevel(server, payload);
		if (level == null) return null;
		String encoded = payload.getStringOr("item_entity", "");
		if (!encoded.isBlank()) {
			try {
				Entity direct = level.getEntity(UUID.fromString(encoded));
				if (direct != null) return direct;
			} catch (IllegalArgumentException ignored) {
				// Fall through to tag scan.
			}
		}
		String tag = RECOVERY_ITEM_TAG_PREFIX + recoveryId;
		for (Entity entity : level.getAllEntities()) {
			if (entity instanceof ItemEntity item && item.getTags().contains(tag)) return item;
		}
		return null;
	}

	private static ServerLevel recoveryItemLevel(MinecraftServer server, CompoundTag payload) {
		String dimensionId = payload.getStringOr("item_dimension", Level.END.identifier().toString());
		for (ServerLevel candidate : server.getAllLevels()) {
			if (candidate.dimension().identifier().toString().equals(dimensionId)) return candidate;
		}
		return null;
	}

	private static boolean addRecovery(MinecraftServer server, UUID encounterId,
			WorldInterfaceState.RecoveryEntry entry) {
		for (int attempt = 0; attempt < 3; attempt++) {
			WorldInterfaceState.Snapshot snapshot = WorldInterfaceState.snapshot(server);
			if (!snapshot.valid() || snapshot.encounterId().filter(encounterId::equals).isEmpty()) return false;
			if (snapshot.recoveryLedger().stream().anyMatch(value -> value.id().equals(entry.id()))) return true;
			WorldInterfaceState.MutationResult result = WorldInterfaceState.mutate(server, encounterId,
					snapshot.revision(), state -> state.addRecovery(entry));
			if (result.applied()) return true;
		}
		return false;
	}

	private static boolean replaceRecovery(MinecraftServer server, UUID encounterId,
			WorldInterfaceState.RecoveryEntry replacement) {
		for (int attempt = 0; attempt < 3; attempt++) {
			WorldInterfaceState.Snapshot snapshot = WorldInterfaceState.snapshot(server);
			if (!snapshot.valid() || snapshot.encounterId().filter(encounterId::equals).isEmpty()) return false;
			if (snapshot.recoveryLedger().stream().noneMatch(value -> value.id().equals(replacement.id()))) return false;
			WorldInterfaceState.MutationResult result = WorldInterfaceState.mutate(server, encounterId,
					snapshot.revision(), state -> {
					state.removeRecovery(replacement.id());
					state.addRecovery(replacement);
				});
			if (result.applied()) return true;
		}
		return false;
	}

	private static boolean removeRecovery(MinecraftServer server, UUID encounterId, UUID recoveryId) {
		for (int attempt = 0; attempt < 3; attempt++) {
			WorldInterfaceState.Snapshot snapshot = WorldInterfaceState.snapshot(server);
			if (!snapshot.valid() || snapshot.encounterId().filter(encounterId::equals).isEmpty()) return false;
			if (snapshot.recoveryLedger().stream().noneMatch(value -> value.id().equals(recoveryId))) return true;
			WorldInterfaceState.MutationResult result = WorldInterfaceState.mutate(server, encounterId,
					snapshot.revision(), state -> state.removeRecovery(recoveryId));
			if (result.applied()) return true;
		}
		return false;
	}

	private static Optional<WorldInterfaceState.RecoveryEntry> recoveryEntry(MinecraftServer server,
			UUID encounterId, UUID recoveryId) {
		WorldInterfaceState.Snapshot snapshot = WorldInterfaceState.snapshot(server);
		if (!snapshot.valid() || snapshot.encounterId().filter(encounterId::equals).isEmpty()) return Optional.empty();
		return snapshot.recoveryLedger().stream().filter(value -> value.id().equals(recoveryId)).findFirst();
	}

	private static CompoundTag itemPayload(MinecraftServer server, ItemStack stack,
			int slot, int baselineCount, String phase) {
		CompoundTag payload = new CompoundTag();
		payload.put("item", encodeItem(server, stack));
		payload.putInt("slot", slot);
		payload.putInt("baseline_count", baselineCount);
		payload.putString("phase", phase);
		return payload;
	}

	private static Tag encodeItem(MinecraftServer server, ItemStack stack) {
		DynamicOps<Tag> ops = RegistryOps.create(NbtOps.INSTANCE, server.registryAccess());
		return ItemStack.CODEC.encodeStart(ops, stack).getOrThrow();
	}

	private static ItemStack decodeItem(MinecraftServer server, CompoundTag payload) {
		Tag encoded = payload.get("item");
		if (encoded == null) throw new IllegalArgumentException("Recovery payload has no item");
		DynamicOps<Tag> ops = RegistryOps.create(NbtOps.INSTANCE, server.registryAccess());
		return ItemStack.CODEC.parse(ops, encoded).getOrThrow();
	}

	private static void maintenanceTick(MinecraftServer server) {
		ServerLevel level = server.getLevel(Level.END);
		if (level == null) return;
		long now = level.getGameTime();
		if (DROP_LEASES_RECONCILED.add(server)) reconcilePersistentDrops(server, now);
		PROJECTILES.entrySet().removeIf(entry -> {
			Entity entity = level.getEntity(entry.getKey());
			if (entity == null || entity.isRemoved()) return true;
			if (now < entry.getValue().expiresGameTick) return false;
			entity.discard();
			return true;
		});
		for (DropLease lease : List.copyOf(DROPS.values())) {
			ServerLevel itemLevel = null;
			for (ServerLevel candidate : server.getAllLevels()) {
				if (candidate.dimension().identifier().toString().equals(lease.dimensionId)) {
					itemLevel = candidate;
					break;
				}
			}
			if (itemLevel == null) continue;
			Entity entity = itemLevel.getEntity(lease.itemEntityId);
			if (entity instanceof ItemEntity item) {
				if (itemLevel.getGameTime() >= lease.releaseGameTick) {
					item.setInvulnerable(false);
					item.setTarget(null);
					item.setExtendedLifetime();
					if (removeRecovery(server, lease.encounterId, lease.recoveryId)) {
						DROPS.remove(lease.recoveryId);
					} else {
						item.setInvulnerable(true);
						item.setTarget(lease.ownerId);
						item.setUnlimitedLifetime();
					}
				}
				continue;
			}
			if (!itemLevel.hasChunkAt(lease.lastKnownPosition)) continue;
			ServerPlayer owner = server.getPlayerList().getPlayer(lease.ownerId);
			if (owner != null && deliverRecovery(server, lease.encounterId,
					lease.recoveryId, lease.ownerId)) DROPS.remove(lease.recoveryId);
		}
	}

	private static void reconcilePersistentDrops(MinecraftServer server, long now) {
		WorldInterfaceState.Snapshot snapshot = WorldInterfaceState.snapshot(server);
		UUID encounterId = snapshot.valid() ? snapshot.encounterId().orElse(null) : null;
		if (encounterId == null) return;
		for (WorldInterfaceState.RecoveryEntry entry : snapshot.recoveryLedger()) {
			if (entry.resolved() || !"hotbar_drop".equals(entry.kind())) continue;
			CompoundTag payload = entry.payload();
			if (!"world".equals(payload.getStringOr("phase", ""))
					|| !payload.contains("item_position")) continue;
			String encodedEntity = payload.getStringOr("item_entity", "");
			try {
				UUID itemEntityId = UUID.fromString(encodedEntity);
				long releaseTick = Math.max(0L, payload.getLongOr("release_tick", now));
				String dimensionId = payload.getStringOr("item_dimension", Level.END.identifier().toString());
				BlockPos lastKnownPosition = BlockPos.of(payload.getLongOr("item_position", 0L));
				DROPS.putIfAbsent(entry.id(), new DropLease(encounterId, entry.id(), entry.ownerId(),
						itemEntityId, releaseTick, dimensionId, lastKnownPosition));
			} catch (IllegalArgumentException ignored) {
				// A malformed world lease remains in the durable ledger for owner-login recovery.
			}
		}
	}

	private static List<BlockPos> laserCandidates(ServerLevel level, Vec3 start, Vec3 end) {
		List<BlockPos> result = new ArrayList<>();
		Vec3 delta = end.subtract(start);
		int steps = Math.max(1, Math.min(48, (int) Math.ceil(delta.length())));
		for (int step = 0; step < steps; step++) {
			double progress = step / (double) Math.max(1, steps - 1);
			Vec3 point = start.add(delta.scale(progress));
			int x = (int) Math.floor(point.x);
			int z = (int) Math.floor(point.z);
			int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
			result.add(new BlockPos(x, Math.max(level.getMinY(), y), z));
		}
		return result;
	}

	private static List<Vec3> directionalLaserBursts(Vec3 start, Vec3 end) {
		Vec3 delta = end.subtract(start);
		int count = Math.clamp((int) Math.ceil(delta.length() / 6.0D), 2, 9);
		List<Vec3> result = new ArrayList<>(count);
		for (int index = 0; index < count; index++) {
			double progress = index / (double) (count - 1);
			result.add(start.add(delta.scale(progress)));
		}
		return result;
	}

	private static double distanceToSegmentSqr(Vec3 point, Vec3 start, Vec3 end) {
		Vec3 segment = end.subtract(start);
		double lengthSqr = segment.lengthSqr();
		if (lengthSqr <= 1.0E-8D) return point.distanceToSqr(start);
		double progress = Math.clamp(point.subtract(start).dot(segment) / lengthSqr, 0.0D, 1.0D);
		return point.distanceToSqr(start.add(segment.scale(progress)));
	}

	private static Vec3 safeLanding(ServerLevel level, AttackRuntime runtime, Vec3 desired) {
		Vec3 center = runtime.arenaCenter.getCenter();
		Vec3 horizontal = new Vec3(desired.x - center.x, 0.0D, desired.z - center.z);
		if (horizontal.lengthSqr() > 130.0D * 130.0D) horizontal = horizontal.normalize().scale(130.0D);
		if (horizontal.lengthSqr() < 10.0D * 10.0D) return runtime.safeSpawn.getCenter();
		int x = (int) Math.floor(center.x + horizontal.x);
		int z = (int) Math.floor(center.z + horizontal.z);
		int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
		if (y <= level.getMinY() + 4) return runtime.safeSpawn.getCenter();
		return new Vec3(x + 0.5D, y, z + 0.5D);
	}

	private static void damage(ServerLevel level, WorldInterfaceEntity boss,
			ServerPlayer player, float amount) {
		WorldInterfaceDamageService.applyExact(level, level.damageSources().mobAttack(boss), player, amount);
	}

	private static void invalidateUnavailableTargets(ServerLevel level, AttackRuntime runtime) {
		for (UUID targetId : runtime.targets) {
			if (runtime.unavailableTargets.contains(targetId)) continue;
			ServerPlayer player = level.getServer().getPlayerList().getPlayer(targetId);
			if (player == null || player.level() != level || !player.isAlive() || player.isSpectator()) {
				runtime.unavailableTargets.add(targetId);
			}
		}
	}

	private static ServerPlayer onlineTarget(ServerLevel level, AttackRuntime runtime, int index) {
		if (index < 0 || index >= runtime.targets.size()) return null;
		UUID targetId = runtime.targets.get(index);
		if (runtime.unavailableTargets.contains(targetId)) return null;
		ServerPlayer player = level.getServer().getPlayerList().getPlayer(targetId);
		return player != null && player.level() == level && player.isAlive() && !player.isSpectator()
				? player : null;
	}

	private static boolean requiresTarget(WorldInterfaceAction action) {
		return action != WorldInterfaceAction.ARROW_REFLECTION;
	}

	private static int durationTicks(WorldInterfaceAction action) {
		return switch (action) {
			case LASER_SWEEP -> LASER_WARNING_TICKS + 1;
			case ENERGY_ORB -> ORB_TRACKING_TICKS;
			case GRAB_SLAM -> GRAB_WARNING_TICKS + GRAB_HOLD_TICKS + 1;
			case MENTAL_ASSAULT -> MENTAL_WARNING_TICKS + MENTAL_EFFECT_TICKS;
			case CHARGE_WEAPON_STEAL -> WEAPON_WARNING_TICKS + WEAPON_CUSTODY_TICKS + 1;
			case GRAB_THROW -> GRAB_WARNING_TICKS + 30;
			case GAZE_HOTBAR_CLEAR -> HOTBAR_WARNING_TICKS + HOTBAR_STEP_TICKS * HOTBAR_SLOTS + 1;
			case ARROW_REFLECTION -> 120;
			case FORCED_EVICTION -> WorldInterfaceActionScheduler.FORCED_EVICTION_WARNING_TICKS + 1;
		};
	}

	private static boolean isValidWeapon(ItemStack stack) {
		return !stack.isEmpty() && (stack.is(ItemTags.WEAPON_ENCHANTABLE)
				|| stack.is(ItemTags.SWORDS) || stack.is(ItemTags.AXES) || stack.is(ItemTags.PICKAXES)
				|| stack.is(ItemTags.SHOVELS) || stack.is(ItemTags.HOES)
				|| stack.is(ItemTags.BOW_ENCHANTABLE) || stack.is(ItemTags.CROSSBOW_ENCHANTABLE)
				|| stack.is(ItemTags.TRIDENT_ENCHANTABLE));
	}

	private static String weaponKind(ItemStack stack) {
		return stack.is(ItemTags.BOW_ENCHANTABLE) || stack.is(ItemTags.CROSSBOW_ENCHANTABLE)
				|| stack.is(ItemTags.TRIDENT_ENCHANTABLE) ? "ranged"
				: stack.is(ItemTags.SWORDS) || stack.is(ItemTags.AXES) ? "melee" : "tool";
	}

	private static int matchingItemCount(ServerPlayer player, ItemStack reference) {
		int count = 0;
		for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
			ItemStack candidate = player.getInventory().getItem(slot);
			if (ItemStack.isSameItemSameComponents(reference, candidate)) count += candidate.getCount();
		}
		return count;
	}

	private static void restoreStackImmediately(ServerPlayer player, int slot, ItemStack stack) {
		if (stack.isEmpty()) return;
		if (slot >= 0 && slot < player.getInventory().getContainerSize()
				&& player.getInventory().getItem(slot).isEmpty()) player.getInventory().setItem(slot, stack);
		else player.getInventory().placeItemBackInInventory(stack);
	}

	private static UUID deterministicRecoveryId(AttackRuntime runtime, UUID owner,
			String kind, int index) {
		String value = runtime.encounterId + ":" + runtime.sequence + ":" + owner + ":" + kind + ":" + index;
		return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8));
	}

	private static void playActionCue(ServerLevel level, BlockPos position,
			WorldInterfaceAction action, float volume, float pitch) {
		AudioService.playBounded(level, position, sound(action), SoundSource.HOSTILE, volume, pitch);
	}

	private static SoundEvent sound(WorldInterfaceAction action) {
		return switch (action) {
			case LASER_SWEEP -> ModSounds.WORLD_INTERFACE_LASER;
			case ENERGY_ORB -> ModSounds.WORLD_INTERFACE_ORB;
			case GRAB_SLAM -> ModSounds.WORLD_INTERFACE_GRAB;
			case MENTAL_ASSAULT -> ModSounds.WORLD_INTERFACE_MENTAL;
			case CHARGE_WEAPON_STEAL -> ModSounds.WORLD_INTERFACE_WEAPON;
			case GRAB_THROW -> ModSounds.WORLD_INTERFACE_THROW;
			case GAZE_HOTBAR_CLEAR -> ModSounds.WORLD_INTERFACE_HOTBAR;
			case ARROW_REFLECTION -> ModSounds.WORLD_INTERFACE_ARROW;
			case FORCED_EVICTION -> ModSounds.WORLD_INTERFACE_EXPULSION;
		};
	}

	private static long mix(long value) {
		value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
		value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
		return value ^ (value >>> 31);
	}

	public enum AttackStatus {
		CONTINUE,
		COMPLETE,
		CANCELLED_RESTART
	}

	public record AttackStart(WorldInterfaceState.AttackEnvelope envelope, WorldInterfaceAction action,
			int durationTicks, Set<UUID> targets, long seed) {
		public AttackStart {
			Objects.requireNonNull(envelope, "envelope");
			Objects.requireNonNull(action, "action");
			targets = Set.copyOf(targets);
		}
	}

	public record AttackTick(AttackStatus status,
			Optional<WorldInterfaceState.AttackEnvelope> replacementEnvelope) {
		public AttackTick {
			Objects.requireNonNull(status, "status");
			replacementEnvelope = replacementEnvelope == null ? Optional.empty() : replacementEnvelope;
		}

		private static AttackTick cancelledRestart() {
			return new AttackTick(AttackStatus.CANCELLED_RESTART, Optional.empty());
		}
	}

	private static final class AttackRuntime {
		private final UUID encounterId;
		private final UUID bossId;
		private final WorldInterfaceAction action;
		private final long startedActiveTick;
		private final long dueActiveTick;
		private final long sequence;
		private final long seed;
		private final List<UUID> targets;
		private final BlockPos origin;
		private final BlockPos arenaCenter;
		private final BlockPos safeSpawn;
		private final Set<UUID> capturedArrows = new LinkedHashSet<>();
		private final Set<UUID> unavailableTargets = new LinkedHashSet<>();
		private UUID orbId;
		private int cursor;
		private boolean damageApplied;
		private ControlLease control;
		private WeaponLease weapon;
		private boolean weaponUsed;
		private int originalSelectedSlot = -1;
		private int lastArrowVolley = -1;
		private Vec3 throwStart;
		private Vec3 throwLanding;

		private AttackRuntime(UUID encounterId, UUID bossId, WorldInterfaceAction action,
				long startedActiveTick, long dueActiveTick, long sequence, long seed,
				List<UUID> targets, BlockPos origin, BlockPos arenaCenter, BlockPos safeSpawn) {
			this.encounterId = encounterId;
			this.bossId = bossId;
			this.action = action;
			this.startedActiveTick = startedActiveTick;
			this.dueActiveTick = dueActiveTick;
			this.sequence = sequence;
			this.seed = seed;
			this.targets = List.copyOf(targets);
			this.origin = origin.immutable();
			this.arenaCenter = arenaCenter.immutable();
			this.safeSpawn = safeSpawn.immutable();
		}

		private WorldInterfaceState.AttackEnvelope envelope() {
			return new WorldInterfaceState.AttackEnvelope(action.wireId(), sequence, startedActiveTick,
					dueActiveTick, seed, origin, Set.copyOf(targets), cursor, damageApplied);
		}
	}

	private record ControlLease(UUID recoveryId, UUID playerId, boolean originalNoGravity,
			Vec3 originalPosition, float yaw, float pitch) {
	}

	private record WeaponLease(UUID recoveryId, UUID playerId, int slot, ItemStack stack, String kind) {
	}

	private record ProjectileLease(UUID encounterId, long expiresGameTick) {
	}

	private record DropLease(UUID encounterId, UUID recoveryId, UUID ownerId, UUID itemEntityId,
			long releaseGameTick, String dimensionId, BlockPos lastKnownPosition) {
	}
}
