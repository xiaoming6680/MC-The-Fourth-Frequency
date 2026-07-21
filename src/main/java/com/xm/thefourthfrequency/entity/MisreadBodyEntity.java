package com.xm.thefourthfrequency.entity;

import com.xm.thefourthfrequency.ending.FinalConfrontationService;
import com.xm.thefourthfrequency.ending.EndBossDifficulty;
import com.xm.thefourthfrequency.ending.EndBossEncounterService;
import com.xm.thefourthfrequency.world.FrequencyWorldData;
import net.minecraft.network.chat.Component;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

public final class MisreadBodyEntity extends Monster {
	private static final EntityDataAccessor<Integer> PHENOTYPE = SynchedEntityData.defineId(
			MisreadBodyEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> MASS_STAGE = SynchedEntityData.defineId(
			MisreadBodyEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> ABSORBED = SynchedEntityData.defineId(
			MisreadBodyEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> ADAPTATION_ACTION = SynchedEntityData.defineId(
			MisreadBodyEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> ADAPTATION_TICKS = SynchedEntityData.defineId(
			MisreadBodyEntity.class, EntityDataSerializers.INT);
	private final ServerBossEvent bossEvent = (ServerBossEvent) new ServerBossEvent(
			Component.translatable("bossbar.thefourthfrequency.fourth_frequency"),
			BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.NOTCHED_10)
			.setDarkenScreen(true).setCreateWorldFog(true);
	private int capturedStrength = -1;
	private int disruptedTicks;
	private int adaptationCooldown = 100;
	private int adaptationCycle;
	private double adaptationTargetX;
	private double adaptationTargetY;
	private double adaptationTargetZ;
	private boolean defeatHandled;
	private boolean endEncounter;
	private int endAnchors = -1;
	private int endParticipants = 1;
	private float endDamageTakenMultiplier = 1.0F;
	private double endNavigationSpeed = 0.90;
	private int endAttackInterval = 10;
	private int endAdaptationCooldown = 120;
	private float endAdaptationDamage = 6.0F;

	public MisreadBodyEntity(EntityType<? extends MisreadBodyEntity> type, Level level) {
		super(type, level);
		xpReward = 40;
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes()
				.add(Attributes.MAX_HEALTH, 280.0)
				.add(Attributes.MOVEMENT_SPEED, 0.30)
				.add(Attributes.FOLLOW_RANGE, 128.0)
				.add(Attributes.ATTACK_DAMAGE, 9.0)
				.add(Attributes.KNOCKBACK_RESISTANCE, 0.90)
				.add(Attributes.STEP_HEIGHT, 1.5);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(PHENOTYPE, 3).define(MASS_STAGE, 0).define(ABSORBED, 0)
				.define(ADAPTATION_ACTION, 0).define(ADAPTATION_TICKS, 0);
	}

	@Override
	protected void registerGoals() {
		// Its behavior is assembled from the observed player records below.
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		bossEvent.setProgress(Math.clamp(getHealth() / Math.max(1.0F, getMaxHealth()), 0.0F, 1.0F));
		if (EndBossEncounterService.isEncounterBoss(this)) {
			tickEndEncounter(level);
			return;
		}
		if (capturedStrength < 0 || tickCount % 40 == 0) {
			applyCapturedStrength(FinalConfrontationService.capturedTerminalCount(FrequencyWorldData.get(level.getServer())));
		}
		if (disruptedTicks > 0) {
			disruptedTicks--;
			clearAdaptation();
		} else if (tickCount % 30 == 0) absorb(FinalConfrontationService.absorbNearby(this));

		ServerPlayer target = FinalConfrontationService.selectTarget(this).orElse(null);
		if (adaptationTicks() > 0) {
			tickAdaptation(level, target);
			return;
		}
		if (target == null) {
			setTarget(null);
			getNavigation().stop();
			return;
		}
		setTarget(target);
		if (disruptedTicks <= 0 && --adaptationCooldown <= 0 && distanceToSqr(target) <= 48.0 * 48.0) {
			int action = phenotype() == 3 ? 1 + Math.floorMod(adaptationCycle++, 3) : phenotype() + 1;
			beginAdaptation(target, action);
			return;
		}
		Vec3 predicted = FinalConfrontationService.predictedDestination(target);
		double speed = 0.90 + capturedStrength * 0.06 + (target.isUsingItem() ? 0.12 : 0.0)
				+ FinalConfrontationService.learnedTimingBonus(target);
		if (phenotype() == 0) speed += 0.12;
		getNavigation().moveTo(predicted.x, predicted.y, predicted.z, speed);
		getLookControl().setLookAt(target, 45.0F, 45.0F);
		if (distanceToSqr(target) <= 4.2 + massStage() && tickCount % 10 == 0) {
			if (!FinalConfrontationService.tryCapture(this, target) && tickCount % 20 == 0) doHurtTarget(level, target);
		}
	}

	private void tickEndEncounter(ServerLevel level) {
		if (disruptedTicks > 0) {
			disruptedTicks--;
			clearAdaptation();
		}
		ServerPlayer target = EndBossEncounterService.selectTarget(this).orElse(null);
		if (adaptationTicks() > 0) {
			tickAdaptation(level, target);
			return;
		}
		if (target == null) {
			setTarget(null);
			getNavigation().stop();
			return;
		}
		setTarget(target);
		if (disruptedTicks <= 0 && --adaptationCooldown <= 0 && distanceToSqr(target) <= 56.0 * 56.0) {
			int action = phenotype() == 3 ? 1 + Math.floorMod(adaptationCycle++, 3) : phenotype() + 1;
			beginAdaptation(target, action);
			return;
		}
		Vec3 predicted = FinalConfrontationService.predictedDestination(target);
		double speed = endNavigationSpeed + (target.isUsingItem() ? 0.10 : 0.0)
				+ FinalConfrontationService.learnedTimingBonus(target);
		getNavigation().moveTo(predicted.x, predicted.y, predicted.z, speed);
		getLookControl().setLookAt(target, 55.0F, 55.0F);
		if (distanceToSqr(target) <= 4.2 + massStage() && tickCount % endAttackInterval == 0) {
			doHurtTarget(level, target);
		}
	}

	public void configureEndEncounter(EndBossDifficulty.Profile profile) {
		float healthRatio = getHealth() / Math.max(1.0F, getMaxHealth());
		boolean profileChanged = !endEncounter || endAnchors != profile.anchors()
				|| endParticipants != profile.participantScale();
		int previousAnchors = endAnchors;
		endEncounter = true;
		endAnchors = profile.anchors();
		endParticipants = profile.participantScale();
		endDamageTakenMultiplier = profile.damageTakenMultiplier();
		endNavigationSpeed = profile.navigationSpeed();
		endAttackInterval = profile.attackInterval();
		endAdaptationCooldown = profile.adaptationCooldown();
		endAdaptationDamage = profile.adaptationDamage();
		getAttribute(Attributes.MAX_HEALTH).setBaseValue(profile.maxHealth());
		getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(profile.movementSpeed());
		getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(profile.attackDamage());
		getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(profile.anchors() == 0 ? 0.99 : 0.92);
		if (profileChanged) {
			setHealth(Math.clamp((float) (getMaxHealth() * healthRatio), 1.0F, getMaxHealth()));
			if (previousAnchors < 0 || profile.anchors() < previousAnchors) {
				adaptationCooldown = Math.min(adaptationCooldown, endAdaptationCooldown);
			}
		}
		bossEvent.setName(Component.translatable("bossbar.thefourthfrequency.end_boss",
				profile.anchors(), profile.participantScale()));
	}

	public void setPhenotype(int phenotype) {
		entityData.set(PHENOTYPE, Math.clamp(phenotype, 0, 3));
		applyCombatProfile();
	}

	public int phenotype() { return entityData.get(PHENOTYPE); }
	public int massStage() { return entityData.get(MASS_STAGE); }
	public int absorbedBlocks() { return entityData.get(ABSORBED); }
	public int adaptationAction() { return entityData.get(ADAPTATION_ACTION); }
	public int adaptationTicks() { return entityData.get(ADAPTATION_TICKS); }
	public void inheritGrowth(int absorbed) {
		entityData.set(ABSORBED, Math.clamp(absorbed, 0, 384));
		int stage = absorbed >= 72 ? 3 : absorbed >= 36 ? 2 : absorbed >= 12 ? 1 : 0;
		entityData.set(MASS_STAGE, stage);
		refreshDimensions();
		applyCombatProfile();
		setHealth(getMaxHealth());
	}

	public void disrupt(int ticks) {
		disruptedTicks = Math.max(disruptedTicks, ticks);
		clearAdaptation();
		adaptationCooldown = Math.max(adaptationCooldown, 80);
	}

	public boolean beginAdaptation(ServerPlayer target, int action) {
		if (action < 1 || action > 3 || adaptationTicks() > 0 || target.level() != level()) return false;
		Vec3 predicted = FinalConfrontationService.predictedDestination(target);
		Vec3 targetPoint = action == 3 ? target.position() : predicted;
		adaptationTargetX = targetPoint.x;
		adaptationTargetY = targetPoint.y;
		adaptationTargetZ = targetPoint.z;
		entityData.set(ADAPTATION_ACTION, action);
		entityData.set(ADAPTATION_TICKS, 24);
		getNavigation().stop();
		target.displayClientMessage(Component.translatable(switch (action) {
			case 1 -> "message.thefourthfrequency.boss.adaptation.operator";
			case 2 -> "message.thefourthfrequency.boss.adaptation.builder";
			default -> "message.thefourthfrequency.boss.adaptation.miner";
		}), true);
		if (level() instanceof ServerLevel serverLevel) {
			com.xm.thefourthfrequency.audio.AudioService.play(serverLevel, blockPosition(),
					com.xm.thefourthfrequency.audio.AudioService.Cue.MISREAD_ADAPTATION);
		}
		return true;
	}

	private void tickAdaptation(ServerLevel level, ServerPlayer target) {
		getNavigation().stop();
		getLookControl().setLookAt(adaptationTargetX, adaptationTargetY + 1.0, adaptationTargetZ, 60.0F, 60.0F);
		if (adaptationTicks() % 4 == 0) {
			var particle = switch (adaptationAction()) {
				case 1 -> ParticleTypes.END_ROD;
				case 2 -> ParticleTypes.SMOKE;
				default -> ParticleTypes.CRIT;
			};
			level.sendParticles(particle, adaptationTargetX, adaptationTargetY + 0.25, adaptationTargetZ,
					6, 0.35, 0.15, 0.35, 0.02);
		}
		int remaining = adaptationTicks() - 1;
		entityData.set(ADAPTATION_TICKS, Math.max(0, remaining));
		if (remaining <= 0) resolveAdaptation(level, target);
	}

	private void resolveAdaptation(ServerLevel level, ServerPlayer target) {
		int action = adaptationAction();
		Vec3 marked = new Vec3(adaptationTargetX, adaptationTargetY, adaptationTargetZ);
		if (action == 1) {
			Vec3 dash = marked.subtract(position()).multiply(1.0, 0.0, 1.0);
			if (dash.lengthSqr() > 0.01) setDeltaMovement(dash.normalize().scale(1.35).add(0.0, 0.08, 0.0));
		} else if (action == 2) {
			FinalConfrontationService.placeAdaptationBarrier(this, marked);
		} else if (action == 3 && target != null
				&& distanceToSegmentSqr(target.position(), position(), marked) <= 4.0) {
			target.hurtServer(level, damageSources().mobAttack(this),
					endEncounter ? endAdaptationDamage : 6.0F + massStage());
			target.knockback(1.1, getX() - target.getX(), getZ() - target.getZ());
		}
		clearAdaptation();
		adaptationCooldown = endEncounter ? endAdaptationCooldown : 120 + getRandom().nextInt(61);
	}

	private void clearAdaptation() {
		entityData.set(ADAPTATION_ACTION, 0);
		entityData.set(ADAPTATION_TICKS, 0);
	}

	private static double distanceToSegmentSqr(Vec3 point, Vec3 start, Vec3 end) {
		Vec3 segment = end.subtract(start);
		double length = segment.lengthSqr();
		if (length < 1.0e-6) return point.distanceToSqr(start);
		double progress = Math.clamp(point.subtract(start).dot(segment) / length, 0.0, 1.0);
		return point.distanceToSqr(start.add(segment.scale(progress)));
	}

	private void absorb(int amount) {
		if (amount <= 0) return;
		int total = Math.min(384, absorbedBlocks() + amount);
		entityData.set(ABSORBED, total);
		int stage = total >= 72 ? 3 : total >= 36 ? 2 : total >= 12 ? 1 : 0;
		if (stage != massStage()) {
			entityData.set(MASS_STAGE, stage);
			refreshDimensions();
			float oldMax = getMaxHealth();
			applyCombatProfile();
			setHealth(Math.min(getMaxHealth(), getHealth() + Math.max(0.0F, getMaxHealth() - oldMax)));
		}
	}

	public void applyCapturedStrength(int captured) {
		captured = Math.max(0, captured);
		if (captured == capturedStrength) return;
		capturedStrength = captured;
		applyCombatProfile();
	}

	private void applyCombatProfile() {
		if (level().isClientSide() || endEncounter) return;
		double health = switch (phenotype()) { case 0 -> 240.0; case 1 -> 320.0; case 2 -> 280.0; default -> 300.0; };
		double attack = switch (phenotype()) { case 0 -> 8.0; case 1 -> 9.0; case 2 -> 12.0; default -> 10.0; };
		double movement = switch (phenotype()) { case 0 -> 0.35; case 1 -> 0.27; case 2 -> 0.30; default -> 0.31; };
		int captured = Math.max(0, capturedStrength);
		getAttribute(Attributes.MAX_HEALTH).setBaseValue(health + massStage() * 40.0 + captured * 24.0);
		getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(attack + massStage() * 1.5 + captured * 2.0);
		getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(Math.min(0.43, movement + captured * 0.018));
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		if (EndBossEncounterService.isEncounterBoss(this)) {
			amount = Math.max(0.01F, amount * endDamageTakenMultiplier);
		}
		return super.hurtServer(level, source, amount);
	}

	@Override
	protected EntityDimensions getDefaultDimensions(Pose pose) {
		return super.getDefaultDimensions(pose).scale(1.0F + massStage() * 0.24F);
	}

	@Override
	public void startSeenByPlayer(ServerPlayer player) {
		super.startSeenByPlayer(player);
		bossEvent.addPlayer(player);
	}

	@Override
	public void stopSeenByPlayer(ServerPlayer player) {
		super.stopSeenByPlayer(player);
		bossEvent.removePlayer(player);
	}

	@Override
	public void die(DamageSource source) {
		super.die(source);
		if (!defeatHandled && !level().isClientSide()) {
			defeatHandled = true;
			bossEvent.removeAllPlayers();
			ServerPlayer victor = source.getEntity() instanceof ServerPlayer player ? player : null;
			if (EndBossEncounterService.isEncounterBoss(this)) EndBossEncounterService.onBossDefeated(this, victor);
			else FinalConfrontationService.onBodyDefeated(this, victor);
		}
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putInt("phenotype", phenotype());
		output.putInt("mass_stage", massStage());
		output.putInt("absorbed_blocks", absorbedBlocks());
		output.putInt("captured_strength", capturedStrength);
		output.putInt("disrupted_ticks", disruptedTicks);
		output.putInt("adaptation_action", adaptationAction());
		output.putInt("adaptation_ticks", adaptationTicks());
		output.putInt("adaptation_cooldown", adaptationCooldown);
		output.putInt("adaptation_cycle", adaptationCycle);
		output.putDouble("adaptation_target_x", adaptationTargetX);
		output.putDouble("adaptation_target_y", adaptationTargetY);
		output.putDouble("adaptation_target_z", adaptationTargetZ);
		output.putBoolean("end_encounter", endEncounter);
		output.putInt("end_anchors", endAnchors);
		output.putInt("end_participants", endParticipants);
		output.putFloat("end_damage_taken", endDamageTakenMultiplier);
		output.putDouble("end_navigation_speed", endNavigationSpeed);
		output.putInt("end_attack_interval", endAttackInterval);
		output.putInt("end_adaptation_cooldown", endAdaptationCooldown);
		output.putFloat("end_adaptation_damage", endAdaptationDamage);
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		entityData.set(PHENOTYPE, Math.clamp(input.getIntOr("phenotype", 3), 0, 3));
		entityData.set(MASS_STAGE, Math.clamp(input.getIntOr("mass_stage", 0), 0, 3));
		entityData.set(ABSORBED, Math.max(0, input.getIntOr("absorbed_blocks", 0)));
		capturedStrength = Math.max(0, input.getIntOr("captured_strength", 0));
		disruptedTicks = Math.max(0, input.getIntOr("disrupted_ticks", 0));
		entityData.set(ADAPTATION_ACTION, Math.clamp(input.getIntOr("adaptation_action", 0), 0, 3));
		entityData.set(ADAPTATION_TICKS, Math.clamp(input.getIntOr("adaptation_ticks", 0), 0, 24));
		adaptationCooldown = Math.max(0, input.getIntOr("adaptation_cooldown", 100));
		adaptationCycle = Math.max(0, input.getIntOr("adaptation_cycle", 0));
		adaptationTargetX = input.getDoubleOr("adaptation_target_x", getX());
		adaptationTargetY = input.getDoubleOr("adaptation_target_y", getY());
		adaptationTargetZ = input.getDoubleOr("adaptation_target_z", getZ());
		endEncounter = input.getBooleanOr("end_encounter", false);
		endAnchors = input.getIntOr("end_anchors", -1);
		endParticipants = Math.max(1, input.getIntOr("end_participants", 1));
		endDamageTakenMultiplier = Math.max(0.001F, input.getFloatOr("end_damage_taken", 1.0F));
		endNavigationSpeed = Math.max(0.1, input.getDoubleOr("end_navigation_speed", 0.90));
		endAttackInterval = Math.max(1, input.getIntOr("end_attack_interval", 10));
		endAdaptationCooldown = Math.max(1, input.getIntOr("end_adaptation_cooldown", 120));
		endAdaptationDamage = Math.max(1.0F, input.getFloatOr("end_adaptation_damage", 6.0F));
		applyCombatProfile();
	}

	@Override
	public boolean removeWhenFarAway(double distanceToClosestPlayer) {
		return false;
	}
}
