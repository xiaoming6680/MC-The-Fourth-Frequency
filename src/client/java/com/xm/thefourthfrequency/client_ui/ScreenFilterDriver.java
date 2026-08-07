package com.xm.thefourthfrequency.client_ui;

import com.mojang.blaze3d.resource.CrossFrameResourcePool;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelTargetBundle;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.resources.Identifier;

import java.util.EnumMap;
import java.util.Map;

/**
 * Runs a post-effect chain over the <em>finished frame</em> - the world, the HUD and the screen.
 *
 * <p>{@code GameRenderer}'s own post-effect slot only ever sees the level render, which is why every
 * corruption this mod drew on a <em>screen</em> - the first-run loading screen, the world loading
 * screen, the layer an anomaly burst draws in front of the world - had to be built out of
 * {@code GuiGraphics.fill}. A rectangle cannot displace, bend or resample the picture underneath it;
 * it can only be painted on top. That is the whole reason those surfaces read as damage drawn onto
 * the image rather than as the image being damaged, and no amount of tuning rectangles fixes it.
 *
 * <p>{@code PostChain.process} is public, though, and {@code Minecraft.runTick} has exactly one
 * moment where the world, the HUD and whatever screen is open have all been composited into the main
 * render target and none of it has reached the window yet: the instant before
 * {@code RenderTarget.blitToScreen}. Filtering there costs one extra pass and needs no new rendering
 * machinery, and everything on the frame is inside the filter rather than sitting on it - including,
 * on a loading screen, the failure text itself, which is exactly where a recording of a failure
 * belongs.
 *
 * <p><b>Requests are frame-scoped by construction.</b> Every request is consumed and cleared by the
 * frame it applies to, so a caller asks from its own render path and a treatment cannot outlive the
 * thing that wanted it - not through a screen closing, an anomaly being interrupted, a disconnect,
 * or an exception on the way out. That removes the entire class of "the shader is stuck on" bug
 * rather than one path into it.
 *
 * <p>What stays on {@link PostEffectArbiter}'s level slot is the other half of a deliberate line:
 * treatments the player still has to <em>play through</em> - the pursuit, the encounter's target
 * lock - must leave the HUD and the terminal's notices readable, so they filter the world and stop
 * at the glass. Treatments where the whole screen is meant to be failing come here.
 */
public final class ScreenFilterDriver {
	/** Declaration order is priority order, highest first. */
	public enum Owner {
		/** A corruption burst. Short, total, and outranks anything a loading screen wants. */
		ANOMALY,
		/** The loading screens' sustained medium damage. */
		LOADING
	}

	private static final Map<Owner, Identifier> REQUESTS = new EnumMap<>(Owner.class);
	/**
	 * Our own pool rather than {@code GameRenderer}'s.
	 *
	 * <p>Vanilla ends its pool's frame inside {@code GameRenderer.render}, which has already finished
	 * by the time this runs; acquiring from it afterwards would be reaching into a frame that is over.
	 * Three frames of retention is what the class is for, and it is only ever populated while a
	 * filter is actually being asked for.
	 */
	private static CrossFrameResourcePool pool;

	private ScreenFilterDriver() {
	}

	/** Asks for a chain over this frame only. Call it from a render path, every frame it applies. */
	public static void request(Owner owner, Identifier chain) {
		if (owner == null || chain == null) return;
		REQUESTS.put(owner, chain);
	}

	/** The chain the last completed frame was filtered through, or null. For tests and the HUD. */
	public static Identifier lastApplied() {
		return lastApplied;
	}

	private static Identifier lastApplied;

	/**
	 * Consumes this frame's requests and filters the frame. Called from the one place that can.
	 *
	 * <p>Clears the requests before it can fail, so a chain that will not compile costs the frame its
	 * treatment and nothing else.
	 */
	public static void applyBeforeBlit(Minecraft client) {
		Identifier wanted = null;
		for (Owner owner : Owner.values()) {
			Identifier requested = REQUESTS.get(owner);
			if (requested != null) {
				wanted = requested;
				break;
			}
		}
		REQUESTS.clear();
		lastApplied = wanted;
		if (wanted == null) {
			// Let the pool age out its targets rather than holding a full-screen buffer for the rest
			// of the session because one loading screen once wanted a filter.
			if (pool != null) pool.endFrame();
			return;
		}
		if (client == null || client.getMainRenderTarget() == null) return;
		PostChain chain = client.getShaderManager()
				.getPostChain(wanted, LevelTargetBundle.MAIN_TARGETS);
		if (chain == null) {
			lastApplied = null;
			return;
		}
		if (pool == null) pool = new CrossFrameResourcePool(3);
		chain.process(client.getMainRenderTarget(), pool);
		pool.endFrame();
	}

	/** Drops the pool's buffers. For leaving a world, and for the client tests. */
	public static void releaseResources() {
		REQUESTS.clear();
		lastApplied = null;
		if (pool != null) pool.clear();
	}
}
