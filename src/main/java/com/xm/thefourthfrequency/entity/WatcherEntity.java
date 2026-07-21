package com.xm.thefourthfrequency.entity;

import com.xm.thefourthfrequency.world.StoryProgressService;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

public final class WatcherEntity extends Monster {
	private UUID observedPlayer;
	private int gazeTicks;
	private int maximumLifetime = 400;

	public WatcherEntity(EntityType<? extends WatcherEntity> type, Level level) {
		super(type, level);
		xpReward = 0;
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes()
				.add(Attributes.MAX_HEALTH, 20.0)
				.add(Attributes.MOVEMENT_SPEED, 0.0)
				.add(Attributes.FOLLOW_RANGE, 64.0)
				.add(Attributes.KNOCKBACK_RESISTANCE, 1.0);
	}

	public void observe(ServerPlayer player, int lifetimeTicks) {
		observedPlayer = player.getUUID();
		maximumLifetime = Math.min(900, Math.max(20, lifetimeTicks));
		setInvulnerable(true);
		setPersistenceRequired();
		setNoGravity(true);
		noPhysics = true;
		setDeltaMovement(Vec3.ZERO);
	}

	public boolean observes(UUID playerId) {
		return playerId.equals(observedPlayer);
	}

	@Override
	protected void registerGoals() {
		// It watches. Pathfinding would make it feel like an ordinary predator.
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		if (observedPlayer == null) {
			discard();
			return;
		}
		ServerPlayer player = level.getServer().getPlayerList().getPlayer(observedPlayer);
		if (player == null || player.level() != level || !player.isAlive() || distanceToSqr(player) > 4096.0
				|| tickCount >= maximumLifetime) {
			discard();
			return;
		}
		getNavigation().stop();
		setNoGravity(true);
		setDeltaMovement(Vec3.ZERO);
		getLookControl().setLookAt(player, 360.0F, 360.0F);
		Vec3 delta = player.position().subtract(position());
		setYRot((float) Math.toDegrees(Math.atan2(delta.z, delta.x)) - 90.0F);
		setYHeadRot(getYRot());

		if (tickCount > 15 && playerCanSee(player)) gazeTicks++; else gazeTicks = 0;
		if (gazeTicks >= 20) {
			StoryProgressService.recordWatcher(player);
			discard();
		}
	}

	private boolean playerCanSee(ServerPlayer player) {
		Vec3 towardWatcher = position().add(0.0, getBbHeight() * 0.72, 0.0)
				.subtract(player.getEyePosition()).normalize();
		double alignment = player.getViewVector(1.0F).dot(towardWatcher);
		return alignment > (distanceToSqr(player) < 64.0 ? 0.72 : 0.93) && player.hasLineOfSight(this);
	}

	@Override
	public boolean removeWhenFarAway(double distanceToClosestPlayer) {
		return false;
	}

	@Override public boolean isPushable() { return false; }
	@Override public boolean isPickable() { return false; }
}
