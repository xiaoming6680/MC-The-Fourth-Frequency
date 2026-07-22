package com.xm.thefourthfrequency.entity;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

/** Ephemeral hit proxy owned by exactly one {@link WorldInterfaceEntity}. */
public final class WorldInterfacePartEntity extends Entity {
	private static final EntityDataAccessor<Integer> PARENT_ID = SynchedEntityData.defineId(
			WorldInterfacePartEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> PART_INDEX = SynchedEntityData.defineId(
			WorldInterfacePartEntity.class, EntityDataSerializers.INT);

	public WorldInterfacePartEntity(EntityType<? extends WorldInterfacePartEntity> type, Level level) {
		super(type, level);
		noPhysics = true;
		setNoGravity(true);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(PARENT_ID, -1).define(PART_INDEX, 0);
	}

	public void attach(WorldInterfaceEntity parent, int index) {
		entityData.set(PARENT_ID, parent.getId());
		entityData.set(PART_INDEX, Math.clamp(index, 0, 9));
		refreshDimensions();
		tick();
	}

	public WorldInterfaceEntity parent() {
		Entity entity = level().getEntity(entityData.get(PARENT_ID));
		return entity instanceof WorldInterfaceEntity parent ? parent : null;
	}

	public int partIndex() {
		return entityData.get(PART_INDEX);
	}

	@Override
	public void tick() {
		super.tick();
		WorldInterfaceEntity parent = parent();
		if (parent == null || parent.isRemoved()) {
			if (!level().isClientSide() && tickCount > 20) discard();
			return;
		}
		Vec3 offset = offset(parent.form(), partIndex(), parent.tickCount);
		snapTo(parent.position().add(offset), parent.getYRot(), parent.getXRot());
		setDeltaMovement(Vec3.ZERO);
	}

	private static Vec3 offset(int form, int index, int tick) {
		double radius = switch (form) {
			case WorldInterfaceEntity.FORM_CONSUMING -> 7.0;
			case WorldInterfaceEntity.FORM_INTERFACE -> 11.0;
			default -> 4.5;
		};
		double height = switch (form) {
			case WorldInterfaceEntity.FORM_CONSUMING -> 11.0;
			case WorldInterfaceEntity.FORM_INTERFACE -> 17.0;
			default -> 6.0;
		};
		if (index == 0) return new Vec3(0.0, height, 0.0);
		double angle = tick * 0.005 + (index - 1) * (Math.PI * 2.0 / 9.0);
		return new Vec3(Math.cos(angle) * radius, Math.max(2.0, height - index * 0.65),
				Math.sin(angle) * radius);
	}

	@Override
	public EntityDimensions getDimensions(Pose pose) {
		return partIndex() == 0 ? EntityDimensions.fixed(5.0F, 5.0F)
				: EntityDimensions.fixed(3.5F, 7.0F);
	}

	@Override
	public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
		super.onSyncedDataUpdated(key);
		if (PART_INDEX.equals(key)) refreshDimensions();
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		WorldInterfaceEntity parent = parent();
		return parent != null && parent.hurtServer(level, source, amount);
	}

	@Override public boolean hurtClient(DamageSource source) { return true; }
	@Override public boolean isPickable() { return true; }
	@Override public boolean canBeHitByProjectile() { return true; }
	@Override public boolean isPushable() { return false; }
	@Override protected void readAdditionalSaveData(ValueInput input) { }
	@Override protected void addAdditionalSaveData(ValueOutput output) { }
}
