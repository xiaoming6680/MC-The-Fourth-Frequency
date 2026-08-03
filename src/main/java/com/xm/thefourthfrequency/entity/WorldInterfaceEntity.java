package com.xm.thefourthfrequency.entity;

import com.xm.thefourthfrequency.audio.ModSounds;
import com.xm.thefourthfrequency.ending.EndBossEncounterService;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.AnimationState;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/**
 * Persistent visual and collision root for the final encounter.
 *
 * <p>The entity's vanilla health is deliberately not authoritative. Minecraft's
 * max-health attribute is capped below the encounter's eight-player 4800 HP
 * requirement, so every accepted hit is routed to the saved virtual pool.</p>
 */
public final class WorldInterfaceEntity extends Monster {
	public static final int FORM_LISTENING = 0;
	public static final int FORM_CONSUMING = 1;
	public static final int FORM_INTERFACE = 2;
	private static final EntityDataAccessor<Integer> FORM = SynchedEntityData.defineId(
			WorldInterfaceEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> ACTION = SynchedEntityData.defineId(
			WorldInterfaceEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Long> ACTION_START_TICK = SynchedEntityData.defineId(
			WorldInterfaceEntity.class, EntityDataSerializers.LONG);
	private static final EntityDataAccessor<Integer> ACTION_DURATION = SynchedEntityData.defineId(
			WorldInterfaceEntity.class, EntityDataSerializers.INT);

	private UUID encounterId;
	public final AnimationState idleAnimationState = new AnimationState();
	public final AnimationState actionAnimationState = new AnimationState();
	private int animatedAction = Integer.MIN_VALUE;

	public WorldInterfaceEntity(EntityType<? extends WorldInterfaceEntity> type, Level level) {
		super(type, level);
		xpReward = 0;
		setNoGravity(true);
		// The box is seven to sixteen blocks across and now rides low enough over a hunted player
		// for the island's own relief to reach into it. The encounter service owns the position
		// outright, so terrain collision can only fight it: a storm does not get stopped by a hill.
		noPhysics = true;
		setPersistenceRequired();
	}

	/**
	 * Without these the boss took 1024 points of damage in total silence, so a landed hit was
	 * indistinguishable from a missed one at any range where the health bar is the only feedback.
	 */
	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return ModSounds.WORLD_INTERFACE_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return ModSounds.WORLD_INTERFACE_DEATH;
	}

	@Override
	protected float getSoundVolume() {
		return 1.0F;
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes()
				.add(Attributes.MAX_HEALTH, 1024.0)
				.add(Attributes.MOVEMENT_SPEED, 0.55)
				.add(Attributes.FOLLOW_RANGE, 256.0)
				.add(Attributes.ATTACK_DAMAGE, 12.0)
				.add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
				.add(Attributes.FLYING_SPEED, 0.55);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(FORM, FORM_LISTENING)
				.define(ACTION, 0)
				.define(ACTION_START_TICK, 0L)
				.define(ACTION_DURATION, 0);
	}

	@Override
	protected void registerGoals() {
		// The encounter service owns movement and attacks deterministically.
	}

	@Override
	public void tick() {
		super.tick();
		idleAnimationState.startIfStopped(tickCount);
		int currentAction = actionId();
		if (currentAction != animatedAction) {
			actionAnimationState.stop();
			if (currentAction != 0) actionAnimationState.start(tickCount);
			animatedAction = currentAction;
		}
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		setNoGravity(true);
		noPhysics = true;
		fallDistance = 0.0F;
		EndBossEncounterService.tickBossEntity(level, this);
	}

	public void bindEncounter(UUID id) {
		encounterId = id;
	}

	public UUID encounterId() {
		return encounterId;
	}

	public int form() {
		return entityData.get(FORM);
	}

	public void setForm(int form) {
		int clamped = Math.clamp(form, FORM_LISTENING, FORM_INTERFACE);
		if (clamped == form()) return;
		entityData.set(FORM, clamped);
		refreshDimensions();
	}

	public int actionId() {
		return entityData.get(ACTION);
	}

	public long actionStartTick() {
		return entityData.get(ACTION_START_TICK);
	}

	public int actionDuration() {
		return entityData.get(ACTION_DURATION);
	}

	public void showAction(int actionId, long startTick, int duration) {
		entityData.set(ACTION, Math.max(0, actionId));
		entityData.set(ACTION_START_TICK, Math.max(0L, startTick));
		entityData.set(ACTION_DURATION, Math.max(0, duration));
	}

	public void clearAction() {
		showAction(0, 0L, 0);
	}

	/**
	 * The box is derived from the drawn mass rather than restated, so a form can never again be
	 * visibly wider than the thing a player is allowed to hit. The eye sits at the glowing core,
	 * which is where the interface reads as looking from.
	 */
	@Override
	protected EntityDimensions getDefaultDimensions(Pose pose) {
		int form = form();
		return EntityDimensions
				.fixed(WorldInterfaceAnatomy.hitboxWidth(form), WorldInterfaceAnatomy.hitboxHeight(form))
				.withEyeHeight((float) WorldInterfaceAnatomy.coreLift(form));
	}

	/**
	 * The one entity here whose box does not stand on its own position.
	 *
	 * <p>Vanilla boxes grow upward from the feet, which is right for something that walks. This
	 * floats: the model puts the mass {@link WorldInterfaceAnatomy#massBottomLift} blocks overhead
	 * and hangs its limbs the other way, so a box anchored at the position spent its bottom eight
	 * and a half blocks - at third form - covering air nobody can see anything in, while arrows that
	 * visibly passed under the body registered as hits. Lifting the box by that offset makes the
	 * hittable volume and the silhouette the same volume.</p>
	 */
	@Override
	protected AABB makeBoundingBox(Vec3 position) {
		int form = form();
		double half = WorldInterfaceAnatomy.massRadius(form);
		double bottom = position.y + WorldInterfaceAnatomy.massBottomLift(form);
		return new AABB(position.x - half, bottom, position.z - half,
				position.x + half, bottom + half * 2.0D, position.z + half);
	}

	@Override
	public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
		super.onSyncedDataUpdated(key);
		if (FORM.equals(key)) refreshDimensions();
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		if (amount <= 0.0F || encounterId == null) return false;
		return EndBossEncounterService.applyVirtualDamage(this, source, amount);
	}

	@Override
	public boolean removeWhenFarAway(double distanceToClosestPlayer) {
		return false;
	}

	@Override public boolean isPushable() { return false; }
	@Override public boolean isPickable() { return true; }
	@Override public boolean canBeHitByProjectile() { return true; }

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		if (encounterId != null) output.putString("encounter_id", encounterId.toString());
		output.putInt("form", form());
		output.putInt("action", actionId());
		output.putLong("action_start_tick", actionStartTick());
		output.putInt("action_duration", actionDuration());
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		String encoded = input.getStringOr("encounter_id", "");
		try {
			encounterId = encoded.isBlank() ? null : UUID.fromString(encoded);
		} catch (IllegalArgumentException ignored) {
			encounterId = null;
		}
		entityData.set(FORM, Math.clamp(input.getIntOr("form", FORM_LISTENING),
				FORM_LISTENING, FORM_INTERFACE));
		entityData.set(ACTION, Math.max(0, input.getIntOr("action", 0)));
		entityData.set(ACTION_START_TICK, Math.max(0L, input.getLongOr("action_start_tick", 0L)));
		entityData.set(ACTION_DURATION, Math.max(0, input.getIntOr("action_duration", 0)));
		setNoGravity(true);
		setDeltaMovement(Vec3.ZERO);
		refreshDimensions();
	}
}
