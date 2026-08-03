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
	/**
	 * One lap, in ticks.
	 *
	 * <p>Two thousand four hundred was two real minutes for a single circuit of a seventy-two block
	 * orbit - about a fifth of a block per tick. Over the handful of seconds the dragon is actually
	 * on screen that is not slow flight, it is a stationary model. Thirty seconds a lap reads as a
	 * dragon crossing the sky.</p>
	 */
	public static final int ORBIT_PERIOD_TICKS = 600;
	/**
	 * Where the dragon flies while it is prising the exit open.
	 *
	 * <p>The portal used to well up out of the altar on its own while the dragon kept its distance,
	 * which made the way out something the arena produced and the dragon something that happened to
	 * be in the sky at the time. It comes down to here instead, close enough that the particles and
	 * the sound are visibly attached to it, and high enough to stay clear of the players standing on
	 * the altar it is opening.</p>
	 */
	public static final double WORK_RADIUS = 17.0D;
	public static final double WORK_HEIGHT = 15.0D;
	/** A tighter circle has to be flown faster, or the descent reads as the dragon stalling. */
	public static final int WORK_PERIOD_TICKS = 190;

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
		return tick(level, persistedUuid, center, 0.0D);
	}

	/**
	 * @param approach 0 for the high resting orbit, 1 for the low circle it flies while opening the
	 *                 exit. Values between the two are interpolated, so the descent is a flight
	 *                 path rather than a teleport.
	 */
	public static boolean tick(ServerLevel level, UUID persistedUuid, BlockPos center, double approach) {
		Optional<EnderDragon> recovered = recover(level, persistedUuid);
		if (recovered.isEmpty()) return false;
		tick(level, recovered.get(), center, approach);
		return true;
	}

	public static void tick(ServerLevel level, EnderDragon dragon, BlockPos center) {
		tick(level, dragon, center, 0.0D);
	}

	public static void tick(ServerLevel level, EnderDragon dragon, BlockPos center, double approach) {
		Objects.requireNonNull(level, "level");
		Objects.requireNonNull(dragon, "dragon");
		Objects.requireNonNull(center, "center");
		requireEnd(level);
		if (!isFriendly(dragon)) {
			throw new IllegalArgumentException("Dragon is not owned by the successful ending");
		}
		configure(dragon, center);
		positionOnOrbit(level, dragon, center, approach);
		EndBossArenaService.suppressVanillaFight(level);
	}

	/** Smoothstep, so the dragon eases out of the high orbit instead of snapping onto the descent. */
	public static double approachEase(double approach) {
		double clamped = Math.clamp(approach, 0.0D, 1.0D);
		return clamped * clamped * (3.0D - 2.0D * clamped);
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
		// Deliberately NOT setNoAi(true).
		//
		// EnderDragon#aiStep checks isNoAi() before it runs the phase tick, and everything that
		// makes a dragon look like a dragon lives behind that check: the wing beat, the body
		// segment history the neck and tail are drawn from, and the part positioning. With no AI
		// the ending spawned a perfectly rigid dragon-shaped object. Hostility is already handled
		// without it - the phase is pinned to HOVERING, which only holds station, the fight
		// registration is null so there is no bar or crystal bookkeeping, the dragon is
		// invulnerable, and this service overwrites its position every tick anyway.
		dragon.setNoAi(false);
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
		positionOnOrbit(level, dragon, center, 0.0D);
	}

	private static void positionOnOrbit(ServerLevel level, EnderDragon dragon, BlockPos center,
			double approach) {
		double eased = approachEase(approach);
		double radius = ORBIT_RADIUS + (WORK_RADIUS - ORBIT_RADIUS) * eased;
		double height = ORBIT_HEIGHT + (WORK_HEIGHT - ORBIT_HEIGHT) * eased;
		double period = ORBIT_PERIOD_TICKS + (WORK_PERIOD_TICKS - ORBIT_PERIOD_TICKS) * eased;
		// The vertical weave flattens as it comes down: a five block bob is a wingbeat at forty-eight
		// blocks up and a collision with the altar at fifteen.
		double bob = 5.0D * (1.0D - eased) + 1.2D * eased;
		double uuidOffset = Math.floorMod(dragon.getUUID().getLeastSignificantBits(), ORBIT_PERIOD_TICKS)
				/ (double) ORBIT_PERIOD_TICKS;
		double turns = (level.getGameTime() % period) / period + uuidOffset;
		double angle = turns * Math.PI * 2.0D;
		double x = center.getX() + 0.5D + Math.cos(angle) * radius;
		double y = center.getY() + height + Math.sin(angle * 2.0D) * bob;
		double z = center.getZ() + 0.5D + Math.sin(angle) * radius;
		double angularSpeed = Math.PI * 2.0D / period;
		Vec3 tangent = new Vec3(-Math.sin(angle) * radius * angularSpeed,
				Math.cos(angle * 2.0D) * bob * 2.0D * angularSpeed,
				Math.cos(angle) * radius * angularSpeed);
		// The dragon does not use the same yaw convention as everything else. Vanilla's own aiStep
		// steers it toward `180 - atan2(dx, dz)`, where an ordinary entity uses `-atan2(dx, dz)`:
		// its yaw is half a turn out from the standard, because the model faces the other way. Aimed
		// with the ordinary formula it flew its whole orbit tail first. The pitch inverts with it —
		// flipping the yaw flips the local axis the pitch turns about — so that sign goes too.
		float yaw = 180.0F + (float) Math.toDegrees(Math.atan2(-tangent.x, tangent.z));
		float pitch = (float) Math.toDegrees(Math.atan2(tangent.y,
				Math.sqrt(tangent.x * tangent.x + tangent.z * tangent.z)));
		// The previous position has to survive the write, or the client interpolates every frame
		// from the new position to itself and the dragon renders as if it were nailed in place even
		// while its coordinates change.
		dragon.xo = dragon.getX();
		dragon.yo = dragon.getY();
		dragon.zo = dragon.getZ();
		dragon.setPos(x, y, z);
		dragon.setDeltaMovement(tangent);
		dragon.setYRot(yaw);
		dragon.setYHeadRot(yaw);
		dragon.setYBodyRot(yaw);
		dragon.setXRot(pitch);
	}
}
