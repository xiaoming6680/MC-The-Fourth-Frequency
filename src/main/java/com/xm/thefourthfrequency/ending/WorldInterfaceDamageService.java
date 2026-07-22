package com.xm.thefourthfrequency.ending;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.level.GameType;

/** Applies protocol-defined world-interface damage without difficulty or armor drift. */
public final class WorldInterfaceDamageService {
	private WorldInterfaceDamageService() {
	}

	public static boolean applyExact(ServerLevel level, DamageSource source,
			ServerPlayer player, float amount) {
		GameType gameMode = player.gameMode.getGameModeForPlayer();
		if (amount <= 0.0F || !player.isAlive()
				|| gameMode == GameType.CREATIVE || gameMode == GameType.SPECTATOR) return false;

		float healthBefore = player.getHealth();
		float absorptionBefore = player.getAbsorptionAmount();
		player.hurtServer(level, source, amount);

		// Keep the vanilla event and attribution, then reconcile armor, resistance,
		// hurt cooldown and the short post-login immunity window to the protocol value.
		float delivered = Math.max(0.0F,
				(healthBefore + absorptionBefore) - (player.getHealth() + player.getAbsorptionAmount()));
		float remaining = Math.min(Math.max(0.0F, amount - delivered),
				player.getHealth() + player.getAbsorptionAmount());
		if (remaining <= 0.0F || !player.isAlive()) return delivered > 0.0F;

		float absorbed = Math.min(player.getAbsorptionAmount(), remaining);
		if (absorbed > 0.0F) {
			player.setAbsorptionAmount(player.getAbsorptionAmount() - absorbed);
			remaining -= absorbed;
		}
		if (remaining > 0.0F) {
			player.setHealth(Math.max(0.0F, player.getHealth() - remaining));
			if (player.getHealth() <= 0.0F) player.die(source);
		}
		return true;
	}
}
