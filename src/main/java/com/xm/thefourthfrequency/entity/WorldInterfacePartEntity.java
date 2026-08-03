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
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Ephemeral hit proxy owned by exactly one {@link WorldInterfaceEntity}.
 *
 * <p>One proxy per limb the model draws, each a column standing on that limb from tip to root. The
 * parent's own collision box grows upward from its position and so only ever covers the mass; the
 * limbs hang {@link WorldInterfaceAnatomy#tentacleDrop} blocks the other way, and they are the
 * entire part of the interface a player on the ground can see near them. The proxies used to be
 * bunched above the position alongside the body, which left that whole underside outside every
 * hitbox in the encounter and gave a melee-only player nothing to swing at.</p>
 *
 * <p>Forms below the third draw fewer limbs than there are proxies. The surplus parks on the body
 * axis inside the parent's box rather than standing in open air, so no proxy is ever hittable
 * somewhere nothing is drawn.</p>
 */
public final class WorldInterfacePartEntity extends Entity {
	public static final int PART_COUNT = 10;
	private static final EntityDataAccessor<Integer> PARENT_ID = SynchedEntityData.defineId(
			WorldInterfacePartEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> PART_INDEX = SynchedEntityData.defineId(
			WorldInterfacePartEntity.class, EntityDataSerializers.INT);
	/**
	 * Mirrored off the parent rather than read through it: dimensions are resolved on both sides and
	 * the client cannot rely on the parent already having been tracked in.
	 */
	private static final EntityDataAccessor<Integer> FORM = SynchedEntityData.defineId(
			WorldInterfacePartEntity.class, EntityDataSerializers.INT);

	public WorldInterfacePartEntity(EntityType<? extends WorldInterfacePartEntity> type, Level level) {
		super(type, level);
		noPhysics = true;
		setNoGravity(true);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(PARENT_ID, -1).define(PART_INDEX, 0)
				.define(FORM, WorldInterfaceEntity.FORM_LISTENING);
	}

	public void attach(WorldInterfaceEntity parent, int index) {
		entityData.set(PARENT_ID, parent.getId());
		entityData.set(PART_INDEX, Math.clamp(index, 0, PART_COUNT - 1));
		entityData.set(FORM, Math.clamp(parent.form(), 0, WorldInterfaceAnatomy.FORM_COUNT - 1));
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

	public int form() {
		return Math.clamp(entityData.get(FORM), 0, WorldInterfaceAnatomy.FORM_COUNT - 1);
	}

	@Override
	public void tick() {
		super.tick();
		WorldInterfaceEntity parent = parent();
		if (parent == null || parent.isRemoved()) {
			if (!level().isClientSide() && tickCount > 20) discard();
			return;
		}
		int form = Math.clamp(parent.form(), 0, WorldInterfaceAnatomy.FORM_COUNT - 1);
		if (!level().isClientSide() && form != entityData.get(FORM)) entityData.set(FORM, form);
		snapTo(parent.position().add(offset(form, partIndex(), parent.tickCount)),
				parent.getYRot(), parent.getXRot());
		setDeltaMovement(Vec3.ZERO);
	}

	/**
	 * Where this proxy sits relative to the interface's position.
	 *
	 * <p>Deliberately at the parent's own altitude rather than down at the limb tip. A proxy is a
	 * column tens of blocks tall, and Minecraft files an entity into the section its <em>position</em>
	 * lands in, not the sections its box covers; {@code getEntities} then only visits sections within
	 * a couple of blocks of the query. Standing this on the tip filed it some twenty-eight blocks
	 * under the arena floor, so a player swinging at a limb right in front of them queried their own
	 * sections, never reached the one holding the proxy, and hit nothing at all. The box still hangs
	 * the full length of the limb -- see {@link #makeBoundingBox} -- it is only the bookkeeping point
	 * that moved up to where the players are.
	 */
	private static Vec3 offset(int form, int index, int tick) {
		int limbs = WorldInterfaceAnatomy.tentacleCount(form);
		if (index >= limbs) return new Vec3(0.0D, WorldInterfaceAnatomy.tentacleRootLift(form), 0.0D);
		double radius = WorldInterfaceAnatomy.tentacleRadius(form);
		double angle = tick * 0.005D + index * (Math.PI * 2.0D / limbs);
		return new Vec3(Math.cos(angle) * radius, 0.0D, Math.sin(angle) * radius);
	}

	@Override
	public EntityDimensions getDimensions(Pose pose) {
		int form = form();
		if (partIndex() >= WorldInterfaceAnatomy.tentacleCount(form)) {
			return EntityDimensions.fixed(2.0F, 2.0F);
		}
		return EntityDimensions.fixed((float) WorldInterfaceAnatomy.tentacleHitWidth(form),
				(float) (WorldInterfaceAnatomy.tentacleDrop(form)
						+ WorldInterfaceAnatomy.tentacleRootLift(form)));
	}

	/**
	 * The limb column, hung from the root down to the tip around a position that sits at the body.
	 *
	 * <p>{@link EntityDimensions} can only grow a box upward from the position it is given, which is
	 * why this is spelled out instead: the limbs hang, but the proxy has to be filed up here to be
	 * findable. Surplus proxies keep the plain centred box; they park on the body axis and are never
	 * the thing anyone is aiming at.
	 */
	@Override
	protected AABB makeBoundingBox(Vec3 position) {
		int form = form();
		if (partIndex() >= WorldInterfaceAnatomy.tentacleCount(form)) return super.makeBoundingBox(position);
		double half = WorldInterfaceAnatomy.tentacleHitWidth(form) * 0.5D;
		return new AABB(position.x - half, position.y - WorldInterfaceAnatomy.tentacleDrop(form),
				position.z - half, position.x + half,
				position.y + WorldInterfaceAnatomy.tentacleRootLift(form), position.z + half);
	}

	@Override
	public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
		super.onSyncedDataUpdated(key);
		if (PART_INDEX.equals(key) || FORM.equals(key)) refreshDimensions();
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
