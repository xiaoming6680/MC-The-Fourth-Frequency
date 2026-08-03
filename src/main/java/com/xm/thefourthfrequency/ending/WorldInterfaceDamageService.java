package com.xm.thefourthfrequency.ending;

import com.xm.thefourthfrequency.bootstrap.TheFourthFrequency;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.GameType;

/**
 * Applies world-interface damage on the encounter's own clock, but through the player's armour.
 *
 * <p>This used to reconcile the delivered damage back up to the protocol figure, which meant armour
 * and resistance were subtracted and then handed straight back: the listed number was net hit points
 * off the bar and a full set of enchanted netherite counted for exactly nothing. That is a defensible
 * way to build a scripted set piece and a bad way to end a progression, because everything a player
 * did to prepare for the fight stopped mattering the moment it started.
 *
 * <p>Two things are still not the player's to negotiate, and both are handled here rather than by
 * reconciling afterwards:
 *
 * <ul>
 *   <li><b>World difficulty.</b> Vanilla scales any mob-caused hit by difficulty, which zeroes it
 *       outright on Peaceful — the finale would be unloseable there and swing by half in either
 *       direction between Easy and Hard. The encounter therefore uses its own damage type, declared
 *       with {@code "scaling": "never"}, so the figures the attack service tunes are the figures
 *       that reach the armour calculation on every world.</li>
 *   <li><b>The hurt cooldown.</b> Vanilla swallows any second hit inside ten ticks, and the
 *       encounter schedules on its own intervals — the laser reapplies its burn every five — so
 *       leaving the cooldown in place would silently halve attacks that are paced deliberately,
 *       without that ever showing up in the numbers anyone tunes.</li>
 * </ul>
 *
 * <p>Timing and difficulty are the encounter's to decide; how much of a hit gets through is the
 * player's, and that is exactly what armour, resistance and protection now decide.
 */
public final class WorldInterfaceDamageService {
	private static final ResourceKey<DamageType> WORLD_INTERFACE = ResourceKey.create(
			Registries.DAMAGE_TYPE,
			Identifier.fromNamespaceAndPath(TheFourthFrequency.MOD_ID, "world_interface"));

	private WorldInterfaceDamageService() {
	}

	/** The encounter's own source: reads as a mob kill, but never scales with world difficulty. */
	public static DamageSource source(ServerLevel level, Entity attacker) {
		return new DamageSource(level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE)
				.getOrThrow(WORLD_INTERFACE), attacker);
	}

	/**
	 * Deals {@code amount} through armour, resistance and protection, ignoring the hurt cooldown.
	 *
	 * @return whether the hit landed at all; a fully mitigated hit still counts as landed
	 */
	public static boolean apply(ServerLevel level, DamageSource source,
			ServerPlayer player, float amount) {
		GameType gameMode = player.gameMode.getGameModeForPlayer();
		if (amount <= 0.0F || !player.isAlive()
				|| gameMode == GameType.CREATIVE || gameMode == GameType.SPECTATOR) return false;

		player.invulnerableTime = 0;
		return player.hurtServer(level, source, amount);
	}
}
