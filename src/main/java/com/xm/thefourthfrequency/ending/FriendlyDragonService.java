package com.xm.thefourthfrequency.ending;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.enderdragon.phases.EnderDragonPhase;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.Objects;
import java.util.Optional;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Owns the non-hostile, fight-detached dragon used by the successful ending. */
public final class FriendlyDragonService {
	public static final String FRIENDLY_DRAGON_TAG = "thefourthfrequency.friendly_ending_dragon";
	public static final double ORBIT_RADIUS = 72.0D;
	public static final double ORBIT_HEIGHT = 48.0D;
	public static final int ORBIT_PERIOD_TICKS = 2_400;

	private static final Set<UUID> FRIENDLY_IDS = ConcurrentHashMap.newKeySet();
	/** Same-tick fallback while the vanilla visible UUID/dragon indexes catch up. */
	private static final Map<UUID, EnderDragon> LOADED_FRIENDLY_DRAGONS = new ConcurrentHashMap<>();

	private FriendlyDragonService() {
	}

	public static EnderDragon spawn(ServerLevel level, BlockPos center) {
		return spawn(level, center, UUID.randomUUID());
	}

	/**
	 * Creates or recovers the dragon using the persisted UUID. It is deliberately never registered
	 * with EndDragonFight, so no hostile boss bar, portal or gateway bookkeeping is attached.
	 */
	public static EnderDragon spawn(ServerLevel level, BlockPos center, UUID persistedUuid) {
		Objects.requireNonNull(level, "level");
		Objects.requireNonNull(center, "center");
		Objects.requireNonNull(persistedUuid, "persistedUuid");
		requireEnd(level);
		Optional<EnderDragon> recovered = recover(level, persistedUuid);
		if (recovered.isPresent()) {
			EnderDragon dragon = recovered.get();
			configure(dragon, center);
			return dragon;
		}

		Entity collision = level.getEntity(persistedUuid);
		if (collision != null) {
			throw new IllegalStateException("Friendly dragon UUID is already owned by " + collision.getType());
		}
		EnderDragon dragon = EntityType.ENDER_DRAGON.create(level, EntitySpawnReason.EVENT);
		if (dragon == null) throw new IllegalStateException("Unable to construct the friendly Ender Dragon");
		dragon.setUUID(persistedUuid);
		dragon.addTag(FRIENDLY_DRAGON_TAG);
		FRIENDLY_IDS.add(persistedUuid);
		configure(dragon, center);
		positionOnOrbit(level, dragon, center);
		if (!level.addFreshEntity(dragon)) {
			FRIENDLY_IDS.remove(persistedUuid);
			throw new IllegalStateException("Unable to add the friendly Ender Dragon to the End");
		}
		rememberLoaded(level, dragon);
		EndBossArenaService.suppressVanillaFight(level);
		return dragon;
	}

	/** Finds a loaded persisted dragon by UUID and reasserts its friendly runtime contract. */
	public static Optional<EnderDragon> recover(ServerLevel level, UUID persistedUuid) {
		if (level == null || persistedUuid == null) return Optional.empty();
		if (level.dimension() != Level.END) return Optional.empty();
		Entity direct = level.getEntity(persistedUuid);
		Optional<EnderDragon> resolved = friendlyInLevel(level, persistedUuid, direct);
		if (resolved.isPresent()) return resolved;
		resolved = friendlyInLevel(level, persistedUuid, LOADED_FRIENDLY_DRAGONS.get(persistedUuid));
		if (resolved.isPresent()) return resolved;
		for (EnderDragon dragon : level.getDragons()) {
			resolved = friendlyInLevel(level, persistedUuid, dragon);
			if (resolved.isPresent()) return resolved;
		}
		for (Entity entity : level.getAllEntities()) {
			resolved = friendlyInLevel(level, persistedUuid, entity);
			if (resolved.isPresent()) return resolved;
		}
		return Optional.empty();
	}

	/** Returns false while the persisted entity's chunk is not loaded; callers should retry later. */
	public static boolean tick(ServerLevel level, UUID persistedUuid, BlockPos center) {
		Optional<EnderDragon> recovered = recover(level, persistedUuid);
		if (recovered.isEmpty()) return false;
		tick(level, recovered.get(), center);
		return true;
	}

	public static void tick(ServerLevel level, EnderDragon dragon, BlockPos center) {
		Objects.requireNonNull(level, "level");
		Objects.requireNonNull(dragon, "dragon");
		Objects.requireNonNull(center, "center");
		requireEnd(level);
		if (!isFriendly(dragon)) {
			throw new IllegalArgumentException("Dragon is not owned by the successful ending");
		}
		configure(dragon, center);
		positionOnOrbit(level, dragon, center);
		EndBossArenaService.suppressVanillaFight(level);
	}

	public static boolean isFriendly(Entity entity) {
		if (!(entity instanceof EnderDragon) || !entity.getTags().contains(FRIENDLY_DRAGON_TAG)) return false;
		FRIENDLY_IDS.add(entity.getUUID());
		return true;
	}

	public static boolean isFriendly(UUID uuid) {
		return uuid != null && FRIENDLY_IDS.contains(uuid);
	}

	private static void requireEnd(ServerLevel level) {
		if (level.dimension() != Level.END) {
			throw new IllegalArgumentException("The friendly ending dragon can only exist in the End");
		}
	}

	private static Optional<EnderDragon> friendlyInLevel(ServerLevel level, UUID persistedUuid,
			Entity candidate) {
		if (!(candidate instanceof EnderDragon dragon) || dragon.isRemoved() || dragon.level() != level
				|| !persistedUuid.equals(dragon.getUUID()) || !isFriendly(dragon)) return Optional.empty();
		rememberLoaded(level, dragon);
		return Optional.of(dragon);
	}

	private static void rememberLoaded(ServerLevel level, EnderDragon dragon) {
		if (level.dimension() == Level.END && dragon.level() == level && !dragon.isRemoved()) {
			LOADED_FRIENDLY_DRAGONS.put(dragon.getUUID(), dragon);
		}
	}

	private static void configure(EnderDragon dragon, BlockPos center) {
		dragon.addTag(FRIENDLY_DRAGON_TAG);
		FRIENDLY_IDS.add(dragon.getUUID());
		dragon.setInvulnerable(true);
		dragon.setNoAi(true);
		dragon.setNoGravity(true);
		dragon.setPersistenceRequired();
		dragon.setTarget(null);
		dragon.setDragonFight(null);
		dragon.setFightOrigin(center);
		dragon.getPhaseManager().setPhase(EnderDragonPhase.HOVERING);
		dragon.nearestCrystal = null;
		dragon.inWall = false;
		dragon.noPhysics = true;
		dragon.setHealth(dragon.getMaxHealth());
	}

	private static void positionOnOrbit(ServerLevel level, EnderDragon dragon, BlockPos center) {
		double uuidOffset = Math.floorMod(dragon.getUUID().getLeastSignificantBits(), ORBIT_PERIOD_TICKS)
				/ (double) ORBIT_PERIOD_TICKS;
		double turns = (level.getGameTime() % ORBIT_PERIOD_TICKS) / (double) ORBIT_PERIOD_TICKS + uuidOffset;
		double angle = turns * Math.PI * 2.0D;
		double x = center.getX() + 0.5D + Math.cos(angle) * ORBIT_RADIUS;
		double y = center.getY() + ORBIT_HEIGHT + Math.sin(angle * 2.0D) * 5.0D;
		double z = center.getZ() + 0.5D + Math.sin(angle) * ORBIT_RADIUS;
		double angularSpeed = Math.PI * 2.0D / ORBIT_PERIOD_TICKS;
		Vec3 tangent = new Vec3(-Math.sin(angle) * ORBIT_RADIUS * angularSpeed,
				Math.cos(angle * 2.0D) * 10.0D * angularSpeed,
				Math.cos(angle) * ORBIT_RADIUS * angularSpeed);
		float yaw = (float) Math.toDegrees(Math.atan2(-tangent.x, tangent.z));
		float pitch = (float) -Math.toDegrees(Math.atan2(tangent.y,
				Math.sqrt(tangent.x * tangent.x + tangent.z * tangent.z)));
		dragon.setPos(x, y, z);
		dragon.setDeltaMovement(tangent);
		dragon.setYRot(yaw);
		dragon.yRotO = yaw;
		dragon.setYHeadRot(yaw);
		dragon.setYBodyRot(yaw);
		dragon.setXRot(pitch);
		dragon.xRotO = pitch;
	}
}
