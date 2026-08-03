package com.xm.thefourthfrequency.client_ui;

import com.xm.thefourthfrequency.bootstrap.TheFourthFrequency;
import com.xm.thefourthfrequency.mixin.GameRendererPostEffectInvoker;
import com.xm.thefourthfrequency.networking.BossActionS2C;
import com.xm.thefourthfrequency.networking.WorldInterfaceProtocol;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

import java.util.List;

/**
 * Installs the encounter's screen-space treatments for the two actions that claim the player's
 * senses rather than their hit points.
 *
 * <p>The pursuit already owns a post-effect chain, so ownership here is strict in both directions:
 * this controller never installs over a chain it does not recognise, and never clears one either.</p>
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
	private static final List<Identifier> OWNED = List.of(LOCK, LOCK_PEAK, EXPULSION);

	private WorldInterfacePostEffectController() {
	}

	private static Identifier effect(String path) {
		return Identifier.fromNamespaceAndPath(TheFourthFrequency.MOD_ID, path);
	}

	public static void tick(Minecraft client, WorldInterfaceClientState.Projection projection) {
		Identifier wanted = wantedEffect(client, projection);
		if (wanted == null) {
			clearOwned(client);
			return;
		}
		install(client, wanted);
	}

	/** Called from every encounter teardown path so a shader can never outlive the fight. */
	public static void clearOwned(Minecraft client) {
		if (client == null || client.gameRenderer == null) return;
		if (isOwned(client.gameRenderer.currentPostEffect())) client.gameRenderer.clearPostEffect();
	}

	public static boolean isOwned(Identifier active) {
		return active != null && OWNED.contains(active);
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
		// No screen treatment for a lock, at any point in the window.
		//
		// The violet wash and blur were the last thing left of the idea that being targeted should
		// feel like the screen itself degrading. In practice a lock is the moment the player most
		// needs to read the arena and move through it, and tinting the whole frame purple fought
		// the dodge it existed to warn about. The reticle and countdown say "you are the one"
		// without taking the world away to say it. Only the expulsion above still wears a chain,
		// because that one is not something a player is meant to play through.
		return null;
	}

	private static void install(Minecraft client, Identifier wanted) {
		if (client.gameRenderer == null) return;
		Identifier active = client.gameRenderer.currentPostEffect();
		if (wanted.equals(active)) return;
		// A chain we do not own is somebody else's presentation - a pursuit, or a vanilla effect.
		// Losing our treatment is strictly better than stealing theirs and never giving it back.
		if (active != null && !isOwned(active)) return;
		((GameRendererPostEffectInvoker) client.gameRenderer).thefourthfrequency$setPostEffect(wanted);
	}
}
