package com.xm.thefourthfrequency.entity;

import com.xm.thefourthfrequency.bootstrap.TheFourthFrequency;
import com.xm.thefourthfrequency.ending.WorldInterfaceDamageService;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ItemSupplier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/** Player-detonatable, no-griefing projectile for the world interface's energy-orb attack. */
public final class WorldInterfaceEnergyOrbEntity extends Entity implements ItemSupplier {
	public static final Identifier TYPE_ID = Identifier.fromNamespaceAndPath(
			TheFourthFrequency.MOD_ID, "world_interface_energy_orb");
	public static final int GROW_TICKS = 60;
	public static final int MAX_TRACKING_TICKS = 100;
	public static final float MIN_SCALE = 1.0F;
	public static final float MAX_SCALE = 6.0F;
	public static final double IMPACT_RADIUS = 5.0D;
	public static final float IMPACT_DAMAGE = 14.0F;

	private static final EntityDataAccessor<Float> SCALE = SynchedEntityData.defineId(
			WorldInterfaceEnergyOrbEntity.class, EntityDataSerializers.FLOAT);

	private UUID encounterId;
	private UUID ownerId;
	private UUID targetId;
	private int ageTicks;
	private boolean loadedFromDisk;

	public WorldInterfaceEnergyOrbEntity(EntityType<? extends WorldInterfaceEnergyOrbEntity> type, Level level) {
		super(type, level);
		noPhysics = true;
		setNoGravity(true);
	}

	/** Resolves the separately registered type without coupling this class to ModEntities initialization order. */
	public static WorldInterfaceEnergyOrbEntity create(ServerLevel level, WorldInterfaceEntity owner,
			UUID encounterId, ServerPlayer target) {
		EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getValue(TYPE_ID);
		if (type == null) throw new IllegalStateException("World-interface energy orb entity type is not registered");
		Entity created = type.create(level, EntitySpawnReason.EVENT);
		if (!(created instanceof WorldInterfaceEnergyOrbEntity orb)) {
			throw new IllegalStateException("Registered energy orb type has the wrong entity factory");
		}
		orb.bind(encounterId, owner.getUUID(), target.getUUID());
		orb.setPos(owner.getX(), owner.getEyeY(), owner.getZ());
		Vec3 initial = target.getEyePosition().subtract(orb.position()).normalize().scale(0.16D);
		orb.setDeltaMovement(initial);
		if (!level.addFreshEntity(orb)) throw new IllegalStateException("Unable to spawn energy orb");
		return orb;
	}

	public void bind(UUID encounterId, UUID ownerId, UUID targetId) {
		this.encounterId = java.util.Objects.requireNonNull(encounterId, "encounterId");
		this.ownerId = java.util.Objects.requireNonNull(ownerId, "ownerId");
		this.targetId = java.util.Objects.requireNonNull(targetId, "targetId");
	}

	public UUID encounterId() {
		return encounterId;
	}

	public UUID ownerId() {
		return ownerId;
	}

	public UUID targetId() {
		return targetId;
	}

	public float orbScale() {
		return entityData.get(SCALE);
	}

	@Override
	public ItemStack getItem() {
		return Items.ENDER_EYE.getDefaultInstance();
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(SCALE, MIN_SCALE);
	}

	@Override
	public void tick() {
		super.tick();
		if (level().isClientSide()) return;
		ServerLevel level = (ServerLevel) level();
		if (loadedFromDisk) {
			discard();
			return;
		}
		ageTicks++;
		float nextScale = MIN_SCALE + (MAX_SCALE - MIN_SCALE)
				* Math.min(1.0F, ageTicks / (float) GROW_TICKS);
		if (Math.abs(nextScale - orbScale()) > 0.001F) {
			entityData.set(SCALE, nextScale);
			refreshDimensions();
		}

		ServerPlayer target = targetId == null ? null : level.getServer().getPlayerList().getPlayer(targetId);
		if (target != null && target.isAlive() && target.level() == level) {
			Vec3 toTarget = target.getEyePosition().subtract(position());
			if (toTarget.lengthSqr() <= Math.max(1.0D, orbScale() * 0.5D) * Math.max(1.0D, orbScale() * 0.5D)) {
				detonate(level, true);
				return;
			}
			double speed = 0.16D + Math.min(0.18D, ageTicks * 0.0018D);
			Vec3 desired = toTarget.normalize().scale(speed);
			setDeltaMovement(getDeltaMovement().scale(0.82D).add(desired.scale(0.18D)));
		}
		setPos(position().add(getDeltaMovement()));
		if ((ageTicks & 1) == 0) {
			level.sendParticles(ParticleTypes.REVERSE_PORTAL, getX(), getY(), getZ(),
					Math.max(2, (int) orbScale()), orbScale() * 0.10D, orbScale() * 0.10D,
					orbScale() * 0.10D, 0.02D);
		}
		if (ageTicks >= MAX_TRACKING_TICKS) discard();
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		if (amount <= 0.0F || !isPlayerSource(source)) return false;
		detonate(level, false);
		return true;
	}

	public void detonate(ServerLevel level, boolean damaging) {
		if (isRemoved()) return;
		level.sendParticles(ParticleTypes.EXPLOSION, getX(), getY(), getZ(), 8,
				1.4D, 1.4D, 1.4D, 0.08D);
		level.sendParticles(ParticleTypes.REVERSE_PORTAL, getX(), getY(), getZ(), 80,
				IMPACT_RADIUS * 0.35D, IMPACT_RADIUS * 0.35D, IMPACT_RADIUS * 0.35D, 0.14D);
		if (damaging) {
			Entity owner = ownerId == null ? null : level.getEntity(ownerId);
			DamageSource source = owner instanceof WorldInterfaceEntity boss
					? level.damageSources().indirectMagic(this, boss)
					: level.damageSources().magic();
			AABB bounds = getBoundingBox().inflate(IMPACT_RADIUS);
			for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, bounds,
					candidate -> candidate.isAlive() && !candidate.isSpectator())) {
				if (player.position().distanceToSqr(position()) <= IMPACT_RADIUS * IMPACT_RADIUS) {
					WorldInterfaceDamageService.applyExact(level, source, player, IMPACT_DAMAGE);
				}
			}
		}
		discard();
	}

	private static boolean isPlayerSource(DamageSource source) {
		if (source.getEntity() instanceof Player || source.getDirectEntity() instanceof Player) return true;
		return source.getDirectEntity() instanceof Projectile projectile && projectile.getOwner() instanceof Player;
	}

	@Override
	public EntityDimensions getDimensions(Pose pose) {
		return EntityDimensions.scalable(orbScale(), orbScale());
	}

	@Override public boolean isPickable() { return true; }
	@Override public boolean canBeHitByProjectile() { return true; }
	@Override public boolean isPushable() { return false; }
	@Override public boolean shouldRenderAtSqrDistance(double distance) { return distance < 256.0D * 256.0D; }

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		if (encounterId != null) output.putString("encounter_id", encounterId.toString());
		if (ownerId != null) output.putString("owner_id", ownerId.toString());
		if (targetId != null) output.putString("target_id", targetId.toString());
		output.putInt("age_ticks", ageTicks);
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		loadedFromDisk = true;
		encounterId = parseUuid(input.getStringOr("encounter_id", ""));
		ownerId = parseUuid(input.getStringOr("owner_id", ""));
		targetId = parseUuid(input.getStringOr("target_id", ""));
		ageTicks = Math.clamp(input.getIntOr("age_ticks", 0), 0, MAX_TRACKING_TICKS);
		float restoredScale = MIN_SCALE + (MAX_SCALE - MIN_SCALE)
				* Math.min(1.0F, ageTicks / (float) GROW_TICKS);
		entityData.set(SCALE, restoredScale);
		setNoGravity(true);
		noPhysics = true;
		refreshDimensions();
	}

	private static UUID parseUuid(String encoded) {
		try {
			return encoded.isBlank() ? null : UUID.fromString(encoded);
		} catch (IllegalArgumentException ignored) {
			return null;
		}
	}
}
