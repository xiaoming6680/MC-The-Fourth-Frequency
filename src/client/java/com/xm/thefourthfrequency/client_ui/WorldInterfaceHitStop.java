package com.xm.thefourthfrequency.client_ui;

import com.xm.thefourthfrequency.bootstrap.RuntimeServices;
import net.minecraft.client.Minecraft;

/**
 * Hit-stop: a few frames of frozen picture on the heaviest impacts.
 *
 * <p>Purely a rendering trick, and it has to be. The server tick is the authoritative clock for the
 * entire encounter - the stage machine, the collapse window, the virtual health pool all hang off
 * it - so anything that actually stopped ticking would desynchronise the fight and, in multiplayer,
 * desynchronise it differently on every client. What happens instead is the frame already in the
 * render target is blitted again: {@code Minecraft.runTick} still ticks, the network still runs, and
 * only the picture waits.
 *
 * <p><b>Pair it with shake.</b> Freeze first, then release the shake impulse on the frame the freeze
 * ends. That ordering is the whole effect; either half on its own reads as half an impact - a freeze
 * with no release is a stutter, and a shake with no freeze has nothing to push against.
 */
public final class WorldInterfaceHitStop {
	/** Two frames at 60fps. Enough to register, short enough not to read as a hitch. */
	public static final long LIGHT_MILLIS = 33L;
	public static final long MEDIUM_MILLIS = 50L;
	public static final long HEAVY_MILLIS = 70L;
	/** Hard ceiling, so no caller can ask for a freeze long enough to feel like a freeze. */
	public static final long MAX_MILLIS = 70L;
	/**
	 * Minimum gap between freezes.
	 *
	 * <p>The third phase runs a volley lane alongside its scheduled attacks, so without this a
	 * player taking sustained fire would be shown a slideshow. The cooldown means the effect marks
	 * the notable hits rather than every hit.
	 */
	public static final long COOLDOWN_MILLIS = 250L;

	private static volatile long holdUntilNanos;
	private static volatile long lastTriggerNanos;
	private static volatile ScreenShakeController.Grade pendingRelease;
	private static volatile boolean holding;

	private WorldInterfaceHitStop() {
	}

	/**
	 * Requests a freeze, with the shake that will fire when it lifts.
	 *
	 * @return whether the freeze was accepted; refused inside the cooldown or a forbidden window.
	 */
	public static boolean trigger(long millis, ScreenShakeController.Grade release) {
		if (!isPermitted()) return false;
		long now = System.nanoTime();
		if (now - lastTriggerNanos < COOLDOWN_MILLIS * 1_000_000L) return false;
		long clamped = Math.max(1L, Math.min(MAX_MILLIS, millis));
		lastTriggerNanos = now;
		holdUntilNanos = now + clamped * 1_000_000L;
		pendingRelease = release;
		return true;
	}

	/**
	 * Situations a freeze must never happen in.
	 *
	 * <p>The lock window is the one that matters most. Every telegraphed attack gives the player a
	 * warning period to dodge in, and freezing the picture during it takes the dodge away - trading
	 * a moment of impact for a death the player could not avoid, which is not a trade worth making
	 * at any strength.
	 */
	private static boolean isPermitted() {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null || client.level == null) return false;
		if (client.screen != null) return false;
		if (PursuitPresentationClient.isHoldingFrame()) return false;
		if (WorldInterfacePresentationController.isLocalPlayerLocked()) return false;
		return RuntimeServices.config().presentation().hitStopEnabled();
	}

	/**
	 * Called once per frame through {@link FrameHoldArbiter}.
	 *
	 * <p>Consumes the hold state whether or not the caller ends up using the answer, and fires the
	 * paired shake on the frame the hold lifts.
	 */
	public static boolean beginFrame(long nowNanos) {
		if (holdUntilNanos == 0L) return false;
		if (nowNanos < holdUntilNanos) {
			holding = true;
			return true;
		}
		holdUntilNanos = 0L;
		holding = false;
		// Release on the frame the picture starts moving again: the shake pushes off the freeze.
		ScreenShakeController.Grade release = pendingRelease;
		pendingRelease = null;
		if (release != null) ScreenShakeController.impulse(release);
		return false;
	}

	/**
	 * Whether the world render must be skipped this frame, consumed by {@link FrameHoldArbiter}.
	 *
	 * <p>Has to agree with what {@link #beginFrame} returned for the same frame. Skipping the clear
	 * without also skipping the render leaves the renderer drawing against a stale depth buffer, and
	 * the whole world disappears for that frame.
	 */
	public static boolean skipRenderFrame() {
		return holding;
	}

	public static boolean isHolding() {
		return holding;
	}

	public static void reset() {
		holdUntilNanos = 0L;
		lastTriggerNanos = 0L;
		pendingRelease = null;
		holding = false;
	}
}
