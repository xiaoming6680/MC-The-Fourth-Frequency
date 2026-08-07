package com.xm.thefourthfrequency.client_ui;

import com.xm.thefourthfrequency.mixin.GameRendererPostEffectInvoker;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

import java.util.EnumMap;
import java.util.Map;

/**
 * The one thing in this mod that installs a screen-space chain.
 *
 * <p>There is exactly one post-effect slot on {@code GameRenderer}, and two subsystems that want it:
 * a pursuit, and the world interface's target lock. They used to negotiate by hand and in opposite
 * directions - the pursuit installed unconditionally, the encounter refused to install over anything
 * it did not recognise - which is two different answers to the same question and neither of them is
 * "who should win". Whoever ticked last decided, and whoever released last could clear a chain
 * somebody else had just installed.
 *
 * <p>So the claims are held here instead of the outcome. Each owner says what it wants, or that it
 * wants nothing, and this decides: the highest-priority live claim is what gets installed, and the
 * moment that claim is dropped the next one down takes the screen without anybody having to know
 * the other exists. A chain nobody here claimed - vanilla's, or another mod's - is never installed
 * over and never cleared.
 */
public final class PostEffectArbiter {
	/**
	 * Declaration order is priority order, highest first.
	 *
	 * <p>The pursuit outranks the lock because it is the one of the two that is also a <em>rule</em>:
	 * the treatment is how the player is told they are being hunted, and losing it for a second
	 * because an encounter action fired would read as the pursuit ending.
	 *
	 * <p>Both of these filter the level and stop at the glass, which is the line between this slot
	 * and {@link ScreenFilterDriver}'s: a treatment the player still has to play through must leave
	 * the HUD and the terminal's notices readable. A treatment where the whole screen is meant to be
	 * failing belongs to the driver.
	 */
	public enum Owner {
		PURSUIT,
		WORLD_INTERFACE
	}

	private static final Map<Owner, Identifier> CLAIMS = new EnumMap<>(Owner.class);
	/** What this class last installed, so it can tell its own chain from anybody else's. */
	private static Identifier installed;

	private PostEffectArbiter() {
	}

	/** Asks for the screen on this owner's behalf. Idempotent; call it every tick. */
	public static void claim(Minecraft client, Owner owner, Identifier chain) {
		if (owner == null) return;
		if (chain == null) {
			release(client, owner);
			return;
		}
		if (chain.equals(CLAIMS.get(owner))) {
			// Still re-applied: another mod, a resource reload or a vanilla effect can have taken the
			// slot since the last tick, and an owner whose claim has not changed is exactly the owner
			// that would otherwise never notice.
			apply(client);
			return;
		}
		CLAIMS.put(owner, chain);
		apply(client);
	}

	/** Drops this owner's claim. Safe to call from a teardown path that never claimed anything. */
	public static void release(Minecraft client, Owner owner) {
		if (owner == null || CLAIMS.remove(owner) == null) return;
		apply(client);
	}

	/** Drops every claim and takes the chain down. For leaving a world, and for tests. */
	public static void releaseAll(Minecraft client) {
		if (CLAIMS.isEmpty() && installed == null) return;
		CLAIMS.clear();
		apply(client);
	}

	/** The chain this arbiter currently has installed, or null. */
	public static Identifier active() {
		return installed;
	}

	public static boolean isInstalled(Identifier chain) {
		return chain != null && chain.equals(installed);
	}

	private static Identifier winner() {
		for (Owner owner : Owner.values()) {
			Identifier chain = CLAIMS.get(owner);
			if (chain != null) return chain;
		}
		return null;
	}

	private static void apply(Minecraft client) {
		if (client == null || client.gameRenderer == null) return;
		Identifier wanted = winner();
		Identifier current = client.gameRenderer.currentPostEffect();
		if (wanted == null) {
			// Only ever takes down a chain this class put up. Losing our treatment is strictly better
			// than clearing somebody else's and never giving it back.
			if (installed != null && installed.equals(current)) client.gameRenderer.clearPostEffect();
			installed = null;
			return;
		}
		if (wanted.equals(current)) {
			installed = wanted;
			return;
		}
		// Somebody outside this mod holds the slot. Wait for them rather than fighting: the claim
		// stays live, so the treatment arrives the moment they let go.
		if (current != null && (installed == null || !installed.equals(current))) return;
		((GameRendererPostEffectInvoker) client.gameRenderer).thefourthfrequency$setPostEffect(wanted);
		installed = wanted;
	}
}
