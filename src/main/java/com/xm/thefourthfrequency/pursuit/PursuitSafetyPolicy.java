package com.xm.thefourthfrequency.pursuit;

import com.xm.thefourthfrequency.content.TerminalData;
import com.xm.thefourthfrequency.correction.EmptySegmentService;
import com.xm.thefourthfrequency.ending.FinaleRuntimePolicy;
import com.xm.thefourthfrequency.terminal.AnomalyRuntimeService;
import com.xm.thefourthfrequency.terminal.TerminalRuntimeService;
import com.xm.thefourthfrequency.world.FrequencyWorldData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;

public final class PursuitSafetyPolicy {
	private PursuitSafetyPolicy() {
	}

	public static boolean canBegin(ServerPlayer player, CompoundTag record, FrequencyWorldData data) {
		float safeHealthFloor = Math.max(6.0F, player.getMaxHealth() * 0.4F);
		if (!player.isAlive() || player.isSpectator() || player.isSleeping()
				|| player.getAbilities().flying || player.isFallFlying() || player.isPassenger()
				|| player.isOnFire() || player.isInLava() || player.fallDistance > 3.0F
				|| player.getHealth() <= safeHealthFloor) return false;
		if (PursuitDimensions.isMirror(player.level())
				|| PursuitDimensions.sourceFamily(player.level().dimension()).isEmpty()) return false;
		// The End mirror is registered for recovery symmetry; dragon/finale pursuit remains disabled in v1.
		if (player.level().dimension().equals(Level.END)) return false;
		if (!player.level().getEntitiesOfClass(Monster.class, player.getBoundingBox().inflate(12.0D),
				monster -> monster.isAlive() && monster.getTarget() == player).isEmpty()) return false;
		return FinaleRuntimePolicy.backgroundSystemsAllowed(data)
				&& !FinaleRuntimePolicy.pressureActive(data)
				&& !TerminalRuntimeService.isOpen(player)
				&& AnomalyRuntimeService.active(player) == null
				&& !EmptySegmentService.isActive(player)
				&& !record.getBooleanOr(TerminalData.PURSUIT_ACTIVE, false)
				&& !record.getBooleanOr(TerminalData.EMPTY_SEGMENT_ACTIVE, false);
	}
}
