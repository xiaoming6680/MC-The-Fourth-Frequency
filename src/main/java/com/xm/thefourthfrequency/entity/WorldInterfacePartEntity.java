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
 * <p>One proxy per part of the storm a player can actually see near them: the three heads, and the
 * business end of each drawn limb. Together with the parent's own box over the mass, that is eight
 * damageable parts at first form, ten at second and fourteen at third, and every one of them stands
 * where {@link WorldInterfaceAnatomy} says the drawn geometry is. The model reads the same
 * functions to place its bones, so "hittable" and "visible" cannot drift apart.
 *
 * <p><b>What this replaces.</b> A proxy used to be a single column spanning an entire limb, root to
 * tip - tens of blocks of invisible hitbox standing in air the tentacle merely swings through. It
 * made melee both unfair and unsatisfying at once: swinging anywhere near a limb connected whether
 * or not the blade went near it, and the heads, which are the thing a player is looking at, could
 * not be hit at all. Damage still routes to the parent either way; what changed is where a hit has
 * to land to count.
 *
 * <p>Forms below the third draw fewer limbs than there are proxies. The surplus parks on the body
 * axis inside the parent's box rather than standing in open air, so no proxy is ever hittable
 * somewhere nothing is drawn.
 */
public final class WorldInterfacePartEntity extends Entity {
	/**
	 * Three heads, then two proxies on each of their necks, then one per limb. Index order is fixed:
	 * the wire carries the index.
	 */
	public static final int HEAD_PARTS = WorldInterfaceAnatomy.HEAD_COUNT;
	public static final int NECK_PARTS = WorldInterfaceAnatomy.HEAD_COUNT
			* WorldInterfaceAnatomy.NECK_SEGMENTS_PER_HEAD;
	public static final int LIMB_PARTS = 10;
	public static final int PART_COUNT = HEAD_PARTS + NECK_PARTS + LIMB_PARTS;
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
	/** A head box is this much wider than the drawn skull, so a near miss on a moving head lands. */
	private static final double HEAD_HIT_SLACK = WorldInterfaceAnatomy.HEAD_HIT_SLACK;
	/** Where a parked surplus proxy sits, and how big it is. Never the thing anyone aims at. */
	private static final float PARKED_SIZE = 2.0F;

	/**
	 * The box for this tick, in world space, recomputed from the posed skeleton.
	 *
	 * <p>Held rather than derived from the position because a neck or a limb is a <em>segment</em>,
	 * not a point: a clip can lay a neck out sideways, and a box grown symmetrically around the
	 * middle of it would be a cube of mostly empty air. What is stored is the half-extent of the
	 * axis-aligned box that actually contains the bone, which is only knowable once the bone has been
	 * posed - so {@link #tick} computes it and {@link #makeBoundingBox} spends it.
	 *
	 * <p>Three primitives rather than a {@code Vec3}, and not for tidiness: {@code Entity}'s
	 * constructor calls {@code setPos}, which calls {@link #makeBoundingBox}, and a subclass's field
	 * initialisers have not run at that point. A reference field is still null there and the entity
	 * dies in its own constructor; a primitive is simply zero, which makes the box the empty one
	 * vanilla would have had anyway until the first tick poses it.
	 */
	private double halfX;
	private double halfY;
	private double halfZ;

	public WorldInterfacePartEntity(EntityType<? extends WorldInterfacePartEntity> type, Level level) {
		super(type, level);
		noPhysics = true;
		setNoGravity(true);
		setHalfExtents(PARKED_SIZE * 0.5D, PARKED_SIZE * 0.5D, PARKED_SIZE * 0.5D);
	}

	private void setHalfExtents(double x, double y, double z) {
		halfX = x;
		halfY = y;
		halfZ = z;
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

	/** True if this proxy stands on one of the three heads rather than on a neck or a limb. */
	public boolean isHead() {
		return partIndex() < HEAD_PARTS;
	}

	/** True if this proxy stands on a neck. The three necks are drawn at every form. */
	public boolean isNeck() {
		int index = partIndex();
		return index >= HEAD_PARTS && index < HEAD_PARTS + NECK_PARTS;
	}

	/** True if the part this proxy stands on is currently drawn. */
	public boolean isActive() {
		int index = partIndex();
		return index < HEAD_PARTS + NECK_PARTS
				|| index - HEAD_PARTS - NECK_PARTS < WorldInterfaceAnatomy.tentacleCount(form());
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
		bindToBone(parent, form);
		setDeltaMovement(Vec3.ZERO);
	}

	/**
	 * Puts this proxy on the bone it stands for, as that bone is posed <em>this tick</em>.
	 *
	 * <p>The proxies used to be placed from a static reproduction of the model's bind pose, which is
	 * where the bones would be if nothing were animating them - and something always is. A clip can
	 * carry the centre skull the better part of twenty blocks at third form, the structural sag bends
	 * every neck further as the pool drains, and the limb proxies orbited the body on a timer the
	 * drawn tentacles never followed at all. {@link WorldInterfaceRig} poses the real skeleton, the
	 * model draws that same pose, and what is left here is reading two points off it.
	 *
	 * <p>Heads take a box around the skull. Necks and limbs take a box around the <em>segment</em>,
	 * because a bone laid out sideways by a clip is not described by a column: the endpoints are
	 * posed, the extents are whatever contains them, and the proxy is filed at the midpoint.
	 */
	private void bindToBone(WorldInterfaceEntity parent, int form) {
		WorldInterfaceRig.Pose pose = parent.rigPose();
		float yaw = parent.yBodyRot;
		int index = partIndex();
		if (index < HEAD_PARTS) {
			double radius = headRadius(pose, index);
			setHalfExtents(radius, radius, radius);
			snapTo(parent.position().add(WorldInterfaceAnatomy.rotate(pose.headOffset(index), yaw)),
					parent.getYRot(), parent.getXRot());
			return;
		}
		if (index < HEAD_PARTS + NECK_PARTS) {
			int neck = index - HEAD_PARTS;
			int head = neck / WorldInterfaceAnatomy.NECK_SEGMENTS_PER_HEAD;
			int joint = neck % WorldInterfaceAnatomy.NECK_SEGMENTS_PER_HEAD;
			span(parent, WorldInterfaceAnatomy.rotate(pose.neckJointOffset(head, joint), yaw),
					WorldInterfaceAnatomy.rotate(pose.neckJointOffset(head, joint + 1), yaw),
					WorldInterfaceAnatomy.neckSegmentRadius(form, head));
			return;
		}
		int limb = index - HEAD_PARTS - NECK_PARTS;
		if (limb >= WorldInterfaceAnatomy.tentacleCount(form)) {
			// Parked inside the parent's own box, where nothing is drawn and nothing is hittable.
			setHalfExtents(PARKED_SIZE * 0.5D, PARKED_SIZE * 0.5D, PARKED_SIZE * 0.5D);
			snapTo(parent.position().add(0.0D, WorldInterfaceAnatomy.tentacleRootLift(form), 0.0D),
					parent.getYRot(), parent.getXRot());
			return;
		}
		span(parent, WorldInterfaceAnatomy.rotate(pose.tendrilTipOffset(limb, false), yaw),
				WorldInterfaceAnatomy.rotate(pose.tendrilTipOffset(limb, true), yaw),
				WorldInterfaceAnatomy.tentacleHitWidth(form) * 0.5D);
	}

	/** Files the proxy at the middle of a posed bone and sizes its box to contain the whole of it. */
	private void span(WorldInterfaceEntity parent, Vec3 near, Vec3 far, double radius) {
		Vec3 centre = near.add(far).scale(0.5D);
		setHalfExtents(Math.abs(far.x - near.x) * 0.5D + radius,
				Math.abs(far.y - near.y) * 0.5D + radius,
				Math.abs(far.z - near.z) * 0.5D + radius);
		snapTo(parent.position().add(centre), parent.getYRot(), parent.getXRot());
	}

	/** Head half-extent for a posed skeleton, so a morph pinch shrinks the box with the skull. */
	private static double headRadius(WorldInterfaceRig.Pose pose, int index) {
		return pose.renderScale() * WorldInterfaceRig.skullHalfUnits(index)
				/ WorldInterfaceRig.UNITS_PER_BLOCK * HEAD_HIT_SLACK;
	}

	/**
	 * Reported dimensions, for the handful of vanilla paths that ask rather than reading the box.
	 *
	 * <p>Derived from the same half-extents the box is built from, so the two cannot disagree; the
	 * box itself is authoritative because a posed bone is not always symmetric about its midpoint in
	 * the way {@link EntityDimensions} assumes.
	 */
	@Override
	public EntityDimensions getDimensions(Pose pose) {
		double width = Math.max(halfX, halfZ) * 2.0D;
		return EntityDimensions.fixed((float) width, (float) (halfY * 2.0D));
	}

	/**
	 * The box, centred on the proxy's own position.
	 *
	 * <p>Unlike vanilla's, this is centred rather than grown upward: a proxy stands on the middle of
	 * a bone, not at the foot of a column. The proxy is also filed at that midpoint rather than at a
	 * limb's tip, which matters for more than tidiness - Minecraft files an entity into the section
	 * its <em>position</em> lands in and {@code getEntities} only visits sections near the query, so
	 * a proxy filed tens of blocks under the arena floor was unreachable by a player swinging at the
	 * limb directly in front of them.
	 */
	@Override
	protected AABB makeBoundingBox(Vec3 position) {
		return new AABB(position.x - halfX, position.y - halfY, position.z - halfZ,
				position.x + halfX, position.y + halfY, position.z + halfZ);
	}

	@Override
	public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
		super.onSyncedDataUpdated(key);
		if (PART_INDEX.equals(key) || FORM.equals(key)) refreshDimensions();
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		WorldInterfaceEntity parent = parent();
		// A parked proxy stands on nothing drawn, so it must not be a damage surface: letting it
		// take hits would put a hittable volume inside the body at forms that draw fewer limbs.
		if (parent == null || !isActive()) return false;
		return parent.hurtServer(level, source, amount);
	}

	@Override public boolean hurtClient(DamageSource source) { return isActive(); }
	@Override public boolean isPickable() { return isActive(); }
	@Override public boolean canBeHitByProjectile() { return isActive(); }
	@Override public boolean isPushable() { return false; }
	@Override protected void readAdditionalSaveData(ValueInput input) { }
	@Override protected void addAdditionalSaveData(ValueOutput output) { }
}
