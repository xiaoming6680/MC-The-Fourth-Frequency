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
	public static final Identifier MENTAL = effect("world_interface_mental");
	public static final Identifier MENTAL_PEAK = effect("world_interface_mental_peak");
	public static final Identifier EXPULSION = effect("world_interface_expulsion");
	private static final List<Identifier> OWNED = List.of(MENTAL, MENTAL_PEAK, EXPULSION);

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
		return switch (action.action()) {
			case MENTAL_ATTACK -> elapsed < WorldInterfaceProtocol.MENTAL_WARNING_TICKS ? MENTAL : MENTAL_PEAK;
			case FORCED_EXPULSION -> EXPULSION;
			default -> null;
		};
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
