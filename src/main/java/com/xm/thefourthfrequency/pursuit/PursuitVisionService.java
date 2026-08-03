package com.xm.thefourthfrequency.pursuit;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

/**
 * Keeps the pursued player's own eyes working for the length of a chase.
 *
 * <p>The mirror is entered from wherever the player happened to be standing - a cave, a night-time
 * overworld, the nether - and the running presentation then drops the picture to black and white at
 * a low colour depth behind a mosaic. That filter costs far more in a dark scene than a lit one,
 * because there is barely any contrast left for it to quantise. A chase lost because the player
 * could not find the floor is not the chase this is meant to be; the threat is the thing behind
 * them, not the darkness. Night vision restores enough of the frame that the proximity bands stay
 * legible as a threat cue rather than reading as a blackout.</p>
 */
public final class PursuitVisionService {
	/**
	 * Held well clear of the two hundred ticks at which the client starts strobing night vision
	 * toward its expiry, so a top-up is never visible as a flicker.
	 */
	private static final int DURATION_TICKS = 600;
	private static final int REFRESH_BELOW_TICKS = 300;

	private PursuitVisionService() {
	}

	public static void apply(ServerPlayer player) {
		// Ambient, no particles, no HUD icon: this is the chase's lighting, not a status the player
		// earned, and it should not read as one.
		player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, DURATION_TICKS, 0,
				true, false, false));
	}

	/**
	 * Deliberately a bounded effect that gets topped up rather than an infinite one. Every teardown
	 * path calls {@link #clear}, but if one is ever missed the worst outcome is half a minute of
	 * leftover night vision instead of a player who can see in the dark for the rest of the save.
	 */
	public static void maintain(ServerPlayer player) {
		MobEffectInstance active = player.getEffect(MobEffects.NIGHT_VISION);
		if (active != null
				&& (active.isInfiniteDuration() || active.getDuration() >= REFRESH_BELOW_TICKS)) return;
		apply(player);
	}

	public static void clear(ServerPlayer player) {
		player.removeEffect(MobEffects.NIGHT_VISION);
	}
}
