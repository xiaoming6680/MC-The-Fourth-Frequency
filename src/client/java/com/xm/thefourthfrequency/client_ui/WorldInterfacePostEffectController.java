package com.xm.thefourthfrequency.client_ui;

import com.xm.thefourthfrequency.bootstrap.TheFourthFrequency;
import com.xm.thefourthfrequency.networking.BossActionS2C;
import com.xm.thefourthfrequency.networking.WorldInterfaceProtocol;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

import java.util.List;

/**
 * Installs the encounter's screen-space treatments for the two actions that claim the player's
 * senses rather than their hit points.
 *
 * <p>This decides <em>which</em> treatment the encounter wants and nothing else. Whether it actually
 * reaches the screen is {@link PostEffectArbiter}'s call - a pursuit outranks a lock, and there is
 * only one post-effect slot to go round.</p>
 */
public final class WorldInterfacePostEffectController {
	/** Worn while an action has this player locked but has not yet resolved. */
	public static final Identifier LOCK = effect("world_interface_lock");
	/**
	 * The heavier chain. Nothing installs it any more - every lock now wears the plain treatment,
	 * because the actions that escalated to this are the ones the player most needs to see through.
	 * It stays declared so {@link #clearOwned} still recognises and removes it from a client that
	 * was wearing it when the encounter changed underneath them.
	 */
	public static final Identifier LOCK_PEAK = effect("world_interface_lock_peak");
	public static final Identifier EXPULSION = effect("world_interface_expulsion");
	/** Fraction of a lock window that passes before any screen treatment is worn at all. */
	private static final float PEAK_FRACTION = 0.7F;
	/**
	 * Where the warning treatment hands over to the committed one.
	 *
	 * <p>Two thirds in: past this the attack is effectively locked to its target and the remaining
	 * window is for getting clear rather than for deciding whether you have to. The colour change
	 * from violet to red is what says which of the two the player is in.
	 */
	private static final float LOCK_COMMIT_FRACTION = 0.66F;
	private static final List<Identifier> OWNED = List.of(LOCK, LOCK_PEAK, EXPULSION);

	private WorldInterfacePostEffectController() {
	}

	private static Identifier effect(String path) {
		return Identifier.fromNamespaceAndPath(TheFourthFrequency.MOD_ID, path);
	}

	public static void tick(Minecraft client, WorldInterfaceClientState.Projection projection) {
		PostEffectArbiter.claim(client, PostEffectArbiter.Owner.WORLD_INTERFACE,
				wantedEffect(client, projection));
	}

	/** Called from every encounter teardown path so a shader can never outlive the fight. */
	public static void clearOwned(Minecraft client) {
		PostEffectArbiter.release(client, PostEffectArbiter.Owner.WORLD_INTERFACE);
	}

	public static boolean isOwned(Identifier active) {
		return active != null && OWNED.contains(active);
	}

	/**
	 * Whether one of the two lock chains is currently installed.
	 *
	 * <p>Asked by the HUD vignette, which exists as the fallback for a driver that will not compile
	 * the custom shader. Both were drawing at once, and two edge treatments stacked is not a warning
	 * - it is a wall around the screen. Only one of them may be on at a time.
	 *
	 * <p>Asked of the arbiter rather than of the game renderer: the claim can be live while a
	 * higher-priority owner holds the screen, and in that case the lock is <em>not</em> what the
	 * player is looking at, so the HUD fallback is exactly what should be drawing.
	 */
	public static boolean isLockChainActive(Minecraft client) {
		return PostEffectArbiter.isInstalled(LOCK) || PostEffectArbiter.isInstalled(LOCK_PEAK);
	}

	private static Identifier wantedEffect(Minecraft client, WorldInterfaceClientState.Projection projection) {
		if (client.level == null || client.player == null) return null;
		long now = client.level.getGameTime();
		if (!projection.actionActive(now) || !projection.actionTargets(client.player.getUUID())) return null;
		BossActionS2C action = projection.action();
		long elapsed = now - action.startTick();
		if (action.action() == WorldInterfaceProtocol.BossAction.FORCED_EXPULSION) return EXPULSION;
		// Every locking action shares one treatment, on its own warning clock.
		int warning = WorldInterfaceProtocol.lockWarningTicks(action.action());
		if (warning <= 0) return null;
		// The sky lance keeps its treatment through the charge, which is the stretch where the
		// impact is already fixed and running is the only answer left.
		boolean lance = action.action() == WorldInterfaceProtocol.BossAction.SKY_LANCE;
		int worn = lance ? warning + WorldInterfaceProtocol.SKY_LANCE_CHARGE_TICKS : warning;
		if (elapsed < 0L || elapsed >= worn) return null;
		// The lock wears a screen treatment again, but only at the edges.
		//
		// The chain this replaces was a box blur plus a full-screen violet wash, and switching it
		// off was the right call: a lock is the moment the player most needs to read the arena and
		// move through it, and blurring or tinting the whole frame fought the dodge the warning
		// existed to enable. Turning it off, though, left the treatment as dead code and the lock
		// with nothing but a reticle.
		//
		// Both chains now run digital_corrupt.fsh with its radial mask open: the middle of the
		// screen is untouched - literally, the mask is zero there - and the interference ramps in
		// only towards the corners. The edge carries the warning; the centre stays for playing in.
		// There is no blur anywhere in either chain.
		//
		// The language is deliberate. Being locked is the interface reaching into the rules that
		// decide where the player is allowed to be, so the border comes apart the way a render
		// pipeline does - bands sliding, channels parting, blocks giving out - and not the way a
		// tape does. The tape is the anomalies' voice, and it is a different shader.
		//
		// Two variants rather than one that ramps, because a post chain's own uniforms are fixed
		// when it loads. Same pattern the pursuit uses with its DISTANT/CLOSE/CONTACT trio. What is
		// *not* static is the treatment's motion: the shader reads GameTime out of the Globals
		// block, so a held chain still corrupts differently every few ticks.
		return elapsed >= warning * LOCK_COMMIT_FRACTION ? LOCK_PEAK : LOCK;
	}
}
