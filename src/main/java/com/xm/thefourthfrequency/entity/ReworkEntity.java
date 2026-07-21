package com.xm.thefourthfrequency.entity;

import com.xm.thefourthfrequency.audio.AudioService;
import com.xm.thefourthfrequency.content.ModBlocks;
import com.xm.thefourthfrequency.correction.CorrectionState;
import com.xm.thefourthfrequency.correction.CorrectionTarget;
import com.xm.thefourthfrequency.correction.CorrectionTargetService;
import com.xm.thefourthfrequency.correction.ReworkCollisionProfile;
import com.xm.thefourthfrequency.correction.ReworkFormStage;
import com.xm.thefourthfrequency.correction.TrendSwarmService;
import com.xm.thefourthfrequency.world.FrequencyWorldData;
import com.xm.thefourthfrequency.world.SurvivalProgressService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.Comparator;
import java.util.Optional;
import java.util.UUID;

public final class ReworkEntity extends Monster {
	public static final int MIN_FORM_STAGE = ReworkFormStage.MIN_STAGE;
	public static final int MAX_FORM_STAGE = ReworkFormStage.MAX_STAGE;
	public static final int MORPH_DURATION_TICKS = 40;
	public static final int MORPH_SWITCH_TICK = 20;
	private static final int BREACH_STUCK_TICKS = 8;
	private static final int BREACH_RETRY_TICKS = 4;
	private static final int BREACH_PROGRESS_SAMPLE_TICKS = 8;
	private static final double BREACH_MIN_PROGRESS_SQR = 0.18 * 0.18;
	private static final double BREACH_PROBE_REACH = 0.75;
	private static final EntityDataAccessor<Integer> FORM_STAGE = SynchedEntityData.defineId(
			ReworkEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> MORPH_TARGET_STAGE = SynchedEntityData.defineId(
			ReworkEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> MORPH_TICKS = SynchedEntityData.defineId(
			ReworkEntity.class, EntityDataSerializers.INT);
	private CorrectionTarget blockTarget;
	private BlockPos workingPosition;
	private int contactTicks;
	private BlockPos obstaclePosition;
	private int obstacleTicks;
	private UUID hostilePlayer;
	private ServerPlayer hostilePlayerEntity;
	private int hostileTicks;
	private UUID obstructingPlayer;
	private int obstructionTicks;
	private Vec3 breachSamplePosition;
	private int breachSampleTicks;
	private int blockedTravelTicks;
	private int breachRetryTicks;
	private boolean formStageInitialized;

	public ReworkEntity(EntityType<? extends ReworkEntity> type, Level level) {
		super(type, level);
		xpReward = 0;
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes()
				.add(Attributes.MAX_HEALTH, 36.0)
				.add(Attributes.MOVEMENT_SPEED, 0.27)
				.add(Attributes.FOLLOW_RANGE, 64.0)
				.add(Attributes.ATTACK_DAMAGE, 5.0)
				.add(Attributes.KNOCKBACK_RESISTANCE, 0.65)
				.add(Attributes.STEP_HEIGHT, 1.0);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(FORM_STAGE, MIN_FORM_STAGE)
				.define(MORPH_TARGET_STAGE, MIN_FORM_STAGE)
				.define(MORPH_TICKS, 0);
	}

	public static int formStageForDismantleCount(int dismantleCount) {
		return ReworkFormStage.forDismantleCount(dismantleCount);
	}

	public int formStage() {
		return entityData.get(FORM_STAGE);
	}

	public int morphTargetStage() {
		return entityData.get(MORPH_TARGET_STAGE);
	}

	public int morphTicks() {
		return entityData.get(MORPH_TICKS);
	}

	public boolean isMorphing() {
		return morphTicks() > 0;
	}

	/** Applies world-authoritative progress without replaying a compensating morph. */
	public void initializeFormStageFromWorld(FrequencyWorldData data) {
		int stage = formStageForDismantleCount(CorrectionState.dismantleCount(data));
		entityData.set(FORM_STAGE, stage);
		entityData.set(MORPH_TARGET_STAGE, stage);
		entityData.set(MORPH_TICKS, 0);
		refreshDimensions();
		formStageInitialized = true;
	}

	@Override
	protected EntityDimensions getDefaultDimensions(Pose pose) {
		ReworkCollisionProfile profile = ReworkCollisionProfile.forStage(formStage());
		return EntityDimensions.fixed(profile.width(), profile.height()).withEyeHeight(profile.eyeHeight());
	}

	@Override
	public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
		super.onSyncedDataUpdated(key);
		if (FORM_STAGE.equals(key)) refreshDimensions();
	}

	@Override
	protected void registerGoals() {
		// This body has a purpose-built state machine and never uses predator goals.
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		if (!formStageInitialized) {
			initializeFormStageFromWorld(FrequencyWorldData.get(level.getServer()));
		}
		if (hostileTicks > 0) hostileTicks--;
		if (tickMorph(level)) return;
		openAdjacentDoor(level);
		ServerPlayer hostile = hostileTarget(level);
		if (hostile != null) {
			setTarget(hostile);
			boolean pathStarted = getNavigation().moveTo(hostile, 1.05);
			tickObstacleBreach(level, hostile.position(), pathStarted);
			if (distanceToSqr(hostile) <= 3.0 && tickCount % 20 == 0) doHurtTarget(level, hostile);
			return;
		}
		if (tickCount % 10 == 0 || blockTarget == null || !targetStillValid(level, blockTarget)) {
			blockTarget = CorrectionTargetService.nearestTarget(level, blockPosition(),
					TrendSwarmService.parameters().reworkSearchRadius()).orElse(null);
			resetWorkIfTargetChanged();
		}

		if (blockTarget != null) {
			setTarget(null);
			workTowardBlock(level, blockTarget);
			return;
		}
		setTarget(null);
		getNavigation().stop();
		resetObstacleBreach();
	}

	private void workTowardBlock(ServerLevel level, CorrectionTarget target) {
		BlockPos position = target.position();
		getLookControl().setLookAt(position.getX() + 0.5, position.getY() + 0.5, position.getZ() + 0.5,
				45.0F, 45.0F);
		if (distanceToSqr(position.getCenter()) <= 8.0) {
			getNavigation().stop();
			resetObstacleBreach();
			contactTicks++;
			level.destroyBlockProgress(getId(), position,
					Math.min(9, contactTicks * 10 / TrendSwarmService.parameters().reworkContactTicks()));
			if (contactTicks >= TrendSwarmService.parameters().reworkContactTicks()) {
				completeDismantle(level, target);
			}
			return;
		}
		contactTicks = 0;
		level.destroyBlockProgress(getId(), position, -1);
		boolean pathStarted = getNavigation().moveTo(position.getX() + 0.5, position.getY(), position.getZ() + 0.5, 0.9);
		tickObstacleBreach(level, position.getCenter(), pathStarted);
		if (!pathStarted || getNavigation().isDone()) {
			detectBlockingPlayer(level);
		} else {
			obstructingPlayer = null;
			obstructionTicks = 0;
		}
	}

	private void completeDismantle(ServerLevel level, CorrectionTarget target) {
		BlockPos position = target.position();
		level.destroyBlockProgress(getId(), position, -1);
		if (target.kind() == CorrectionTarget.Kind.GROUNDING_ANCHOR) {
			// The altar device itself is the whole target. Do not scar or alter the stronghold around it.
			level.setBlockAndUpdate(position, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
		} else {
			level.setBlockAndUpdate(position, ModBlocks.REWORK_SCAR.defaultBlockState());
			for (Direction direction : Direction.Plane.HORIZONTAL) {
				BlockPos brace = position.relative(direction);
				if (level.getBlockState(brace).isAir()) {
					level.setBlockAndUpdate(brace, ModBlocks.REWORK_BRACE.defaultBlockState());
					break;
				}
			}
		}
		if (target.kind() == CorrectionTarget.Kind.ANOMALY_TRACE)
			SurvivalProgressService.completeCorrectionScene(level.getServer(), position);
		FrequencyWorldData data = FrequencyWorldData.get(level.getServer());
		CorrectionState.recordDismantle(data, target.kind(), position);
		int unlockedStage = formStageForDismantleCount(CorrectionState.dismantleCount(data));
		if (!beginMorph(level, unlockedStage)) {
			AudioService.play(level, position, AudioService.Cue.REWORK_JOINT);
		}
		blockTarget = null;
		workingPosition = null;
		contactTicks = 0;
	}

	private boolean beginMorph(ServerLevel level, int unlockedStage) {
		if (isMorphing() || formStage() >= MAX_FORM_STAGE || unlockedStage <= formStage()) return false;
		int nextStage = Math.min(formStage() + 1, Math.clamp(unlockedStage, MIN_FORM_STAGE, MAX_FORM_STAGE));
		entityData.set(MORPH_TARGET_STAGE, nextStage);
		entityData.set(MORPH_TICKS, MORPH_DURATION_TICKS);
		getNavigation().stop();
		setTarget(null);
		AudioService.play(level, blockPosition(), AudioService.Cue.REWORK_JOINT);
		return true;
	}

	private boolean tickMorph(ServerLevel level) {
		int remaining = morphTicks();
		if (remaining <= 0) return false;
		getNavigation().stop();
		resetObstacleBreach();
		setTarget(null);
		if (workingPosition != null) level.destroyBlockProgress(getId(), workingPosition, -1);
		int next = remaining - 1;
		if (remaining == MORPH_SWITCH_TICK + 1) {
			entityData.set(FORM_STAGE, morphTargetStage());
			refreshDimensions();
			AudioService.play(level, blockPosition(), AudioService.Cue.REWORK_JOINT);
		}
		entityData.set(MORPH_TICKS, next);
		if (next == 0) entityData.set(MORPH_TARGET_STAGE, formStage());
		return true;
	}

	private void tickObstacleBreach(ServerLevel level, Vec3 destination, boolean pathStarted) {
		if (breachRetryTicks > 0) breachRetryTicks--;
		Vec3 horizontalTarget = destination.subtract(position()).multiply(1.0, 0.0, 1.0);
		if (horizontalTarget.lengthSqr() <= 2.25 || !level.getGameRules().get(GameRules.MOB_GRIEFING)) {
			resetObstacleBreach();
			return;
		}

		Vec3 current = position();
		if (breachSamplePosition == null) {
			breachSamplePosition = current;
			breachSampleTicks = 0;
			blockedTravelTicks = 0;
			return;
		}

		breachSampleTicks++;
		boolean hardBlocked = horizontalCollision || !pathStarted || getNavigation().isDone();
		if (hardBlocked) blockedTravelTicks++;
		if (breachSampleTicks >= BREACH_PROGRESS_SAMPLE_TICKS) {
			double movedX = current.x - breachSamplePosition.x;
			double movedZ = current.z - breachSamplePosition.z;
			if (movedX * movedX + movedZ * movedZ < BREACH_MIN_PROGRESS_SQR) {
				blockedTravelTicks = Math.max(blockedTravelTicks, BREACH_STUCK_TICKS);
			} else if (!hardBlocked) {
				blockedTravelTicks = 0;
			}
			breachSamplePosition = current;
			breachSampleTicks = 0;
		}

		if (blockedTravelTicks < BREACH_STUCK_TICKS || breachRetryTicks > 0) return;
		if (tryBreakBlockingObstacle(level, horizontalTarget.normalize())) {
			getNavigation().stop();
			blockedTravelTicks = 0;
		}
		breachRetryTicks = BREACH_RETRY_TICKS;
	}

	private boolean tryBreakBlockingObstacle(ServerLevel level, Vec3 direction) {
		AABB body = getBoundingBox();
		AABB probe = body.expandTowards(direction.x * BREACH_PROBE_REACH, 0.0,
				direction.z * BREACH_PROBE_REACH).inflate(0.04, 0.0, 0.04);
		BlockPos minimum = BlockPos.containing(probe.minX + 1.0e-5, probe.minY + 1.0e-5,
				probe.minZ + 1.0e-5);
		BlockPos maximum = BlockPos.containing(probe.maxX - 1.0e-5, probe.maxY - 1.0e-5,
				probe.maxZ - 1.0e-5);
		Vec3 focus = position().add(direction.scale(BREACH_PROBE_REACH * 0.6))
				.add(0.0, getBbHeight() * 0.58, 0.0);
		CollisionContext collisionContext = CollisionContext.of(this);
		BlockPos selected = null;
		double selectedDistance = Double.MAX_VALUE;

		for (BlockPos mutable : BlockPos.betweenClosed(minimum, maximum)) {
			BlockPos candidate = mutable.immutable();
			if (!canBreakObstacle(level, candidate)) continue;
			BlockState state = level.getBlockState(candidate);
			VoxelShape collision = state.getCollisionShape(level, candidate, collisionContext);
			if (collision.isEmpty() || !collision.bounds().move(candidate).intersects(probe)) continue;
			double distance = candidate.getCenter().distanceToSqr(focus);
			if (distance < selectedDistance) {
				selected = candidate;
				selectedDistance = distance;
			}
		}

		return selected != null && level.destroyBlock(selected, false, this, 512);
	}

	private boolean canBreakObstacle(ServerLevel level, BlockPos position) {
		if (!level.hasChunkAt(position) || !level.getWorldBorder().isWithinBounds(position)) return false;
		if (workingPosition != null && workingPosition.equals(position)) return false;
		BlockState state = level.getBlockState(position);
		return !state.isAir()
				&& !(state.getBlock() instanceof DoorBlock)
				&& !state.hasBlockEntity()
				&& level.getBlockEntity(position) == null
				&& !state.is(BlockTags.WITHER_IMMUNE)
				&& state.getDestroySpeed(level, position) >= 0.0F;
	}

	private void resetObstacleBreach() {
		breachSamplePosition = null;
		breachSampleTicks = 0;
		blockedTravelTicks = 0;
		breachRetryTicks = 0;
	}

	private void detectBlockingPlayer(ServerLevel level) {
		ServerPlayer nearest = level.players().stream()
				.filter(player -> player.isAlive() && distanceToSqr(player) <= 2.25)
				.min(Comparator.comparingDouble(this::distanceToSqr)).orElse(null);
		if (nearest == null) {
			obstructingPlayer = null;
			obstructionTicks = 0;
			return;
		}
		if (!nearest.getUUID().equals(obstructingPlayer)) {
			obstructingPlayer = nearest.getUUID();
			obstructionTicks = 0;
		}
		if (++obstructionTicks >= 40) becomeHostile(nearest);
	}

	private void openAdjacentDoor(ServerLevel level) {
		for (Direction direction : Direction.Plane.HORIZONTAL) {
			BlockPos position = blockPosition().relative(direction);
			BlockState state = level.getBlockState(position);
			if (state.getBlock() instanceof DoorBlock door && !door.isOpen(state)) {
				door.setOpen(this, level, state, position, true);
				return;
			}
		}
	}

	private ServerPlayer hostileTarget(ServerLevel level) {
		if (hostilePlayerEntity != null && hostileTicks > 0 && hostilePlayerEntity.level() == level
				&& hostilePlayerEntity.isAlive() && !hostilePlayerEntity.isRemoved()) return hostilePlayerEntity;
		if (hostilePlayer != null && hostileTicks > 0) {
			ServerPlayer player = level.getServer().getPlayerList().getPlayer(hostilePlayer);
			if (player != null && player.level() == level && player.isAlive()) return player;
		}
		double radiusSquared = (double) TrendSwarmService.parameters().reworkSearchRadius()
				* TrendSwarmService.parameters().reworkSearchRadius();
		return level.players().stream()
				.filter(Entity::isAlive)
				.filter(player -> FrequencyWorldData.get(level.getServer()).terminalRecord(player.getUUID())
						.map(record -> (record.getIntOr(com.xm.thefourthfrequency.content.TerminalData.BREACH_MASK, 0) & 1) != 0
								&& record.getLongOr(com.xm.thefourthfrequency.content.TerminalData.TOOLS_DISABLED_UNTIL, 0L)
								> level.getGameTime()).orElse(false))
				.filter(player -> distanceToSqr(player) <= radiusSquared)
				.min(Comparator.comparingDouble(this::distanceToSqr)).orElse(null);
	}

	private void becomeHostile(ServerPlayer player) {
		hostilePlayer = player.getUUID();
		hostilePlayerEntity = player;
		hostileTicks = 20 * 20;
	}

	private boolean targetStillValid(ServerLevel level, CorrectionTarget target) {
		return switch (target.kind()) {
			case ANOMALY_TRACE -> level.getBlockState(target.position()).is(ModBlocks.NASCENT_BODY_ORGAN);
			case SIGNAL_SHELL -> level.getBlockState(target.position()).is(ModBlocks.ARCHIVE_LOCK);
			case GROUNDING_ANCHOR -> level.getBlockState(target.position()).is(ModBlocks.ALTAR_ANCHOR);
		};
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		if (source.getEntity() instanceof ServerPlayer player) becomeHostile(player);
		return super.hurtServer(level, source, amount);
	}

	@Override
	public void die(DamageSource source) {
		if (level() instanceof ServerLevel level) {
			FrequencyWorldData data = FrequencyWorldData.get(level.getServer());
			CorrectionTarget trace = blockTarget != null
					&& blockTarget.kind() == CorrectionTarget.Kind.ANOMALY_TRACE
					&& targetStillValid(level, blockTarget) ? blockTarget : null;
			if (trace != null) {
				CorrectionTarget target = trace;
				BlockPos position = target.position();
				if (level.getBlockState(position).is(ModBlocks.NASCENT_BODY_ORGAN))
					level.setBlockAndUpdate(position, ModBlocks.REWORK_SCAR.defaultBlockState());
				SurvivalProgressService.completeCorrectionScene(level.getServer(), position);
				CorrectionState.recordDismantle(data, target.kind(), position);
			}
		}
		super.die(source);
	}

	private void resetWorkIfTargetChanged() {
		BlockPos next = blockTarget == null ? null : blockTarget.position();
		if (next == null || !next.equals(workingPosition)) {
			workingPosition = next;
			contactTicks = 0;
		}
	}

	@Override
	public boolean removeWhenFarAway(double distanceToClosestPlayer) {
		return false;
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putInt("form_stage", formStage());
		output.putInt("morph_target_stage", morphTargetStage());
		output.putInt("morph_ticks", morphTicks());
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		int savedStage = Math.clamp(input.getIntOr("form_stage", MIN_FORM_STAGE),
				MIN_FORM_STAGE, MAX_FORM_STAGE);
		entityData.set(FORM_STAGE, savedStage);
		entityData.set(MORPH_TARGET_STAGE, savedStage);
		// Reloads adopt the world-unlocked form immediately and never replay a compensating morph.
		entityData.set(MORPH_TICKS, 0);
		refreshDimensions();
		formStageInitialized = false;
	}

}
