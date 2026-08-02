package com.xm.thefourthfrequency.client_ui;

import com.xm.thefourthfrequency.audio.ModSounds;
import com.xm.thefourthfrequency.entity.ReworkEntity;
import com.xm.thefourthfrequency.mixin.GameRendererPostEffectInvoker;
import com.xm.thefourthfrequency.networking.PursuitPresentationPayload;
import com.xm.thefourthfrequency.pursuit.PursuitDimensions;
import com.xm.thefourthfrequency.pursuit.PursuitPresentationTimeline;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;

import java.util.Comparator;

/** Client-only pursuit warning, synthetic frame collapse, blackout, filter, and heartbeat. */
public final class PursuitPresentationClient {
	/** The mid band, and the chain installed before any corrector distance is known. */
	private static final Identifier PURSUIT_POST_EFFECT = Identifier.fromNamespaceAndPath(
			"thefourthfrequency", "pursuit_low_res");
	private static final Identifier PURSUIT_POST_EFFECT_DISTANT = Identifier.fromNamespaceAndPath(
			"thefourthfrequency", "pursuit_low_res_distant");
	private static final Identifier PURSUIT_POST_EFFECT_CLOSE = Identifier.fromNamespaceAndPath(
			"thefourthfrequency", "pursuit_low_res_close");
	private static final Identifier PURSUIT_POST_EFFECT_CONTACT = Identifier.fromNamespaceAndPath(
			"thefourthfrequency", "pursuit_low_res_contact");
	private static final int DESTINATION_STABLE_TICKS_AFTER_LOAD = 2;
	private static final int DESTINATION_FALLBACK_STABLE_TICKS = 12;
	/** Four scans a second is well under the hysteresis band, so it cannot cause chain churn. */
	private static final int PROXIMITY_SCAN_INTERVAL_TICKS = 5;
	private static Phase phase = Phase.IDLE;
	private static String sessionId = "";
	private static int form;
	private static int warningTicks;
	private static int heartbeatCooldown;
	private static int proximityScanCooldown;
	private static PursuitPresentationTimeline.ProximityGrade proximityGrade =
			PursuitPresentationTimeline.ProximityGrade.DISTANT;
	private static int resolutionTicks;
	private static long lastPresentedFrameNanos;
	private static boolean frameHeld;
	private static boolean freezeFramePending;
	private static boolean runningRequested;
	private static boolean clearRequested;
	private static boolean noticeQueueLocked;
	private static int destinationStableTicks;
	private static boolean transitionLoadingObserved;
	private static boolean initialized;

	private PursuitPresentationClient() {
	}

	public static void initialize() {
		if (initialized) return;
		initialized = true;
		ClientPlayNetworking.registerGlobalReceiver(PursuitPresentationPayload.TYPE, (payload, context) ->
				context.client().execute(() -> accept(payload)));
		ClientTickEvents.END_CLIENT_TICK.register(PursuitPresentationClient::tick);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> reset(client));
		HudRenderCallback.EVENT.register((graphics, tickCounter) -> renderOverlay(graphics));
	}

	/**
	 * Decides, once per frame, whether this frame is dropped - and must run before Minecraft
	 * clears the main render target.
	 *
	 * <p>Holding a frame is not the same as drawing nothing. {@code Minecraft.runTick} clears the
	 * main target to opaque black, then renders into it, then blits it to the screen; cancelling
	 * only the render therefore published a black frame, and the collapse ramp - which drops from
	 * sixty frames a second to four - published them alternating with live ones. That is a strobe,
	 * not a failing video feed, and the capture freeze that is supposed to leave the player staring
	 * at the last thing they saw went black instead. Skipping the clear as well leaves the previous
	 * frame sitting in the target, so the blit re-presents it and the picture genuinely holds.</p>
	 *
	 * @return true when the clear and the render must both be skipped this frame
	 */
	public static boolean beginFrame(long nowNanos) {
		frameHeld = evaluateFrameHold(nowNanos);
		return frameHeld;
	}

	/**
	 * Consumed by the render cancel, which disarms it: if the clear-site hook ever stops running,
	 * the worst case is a frame that draws normally rather than a screen frozen forever.
	 */
	public static boolean skipRenderFrame() {
		boolean held = frameHeld;
		frameHeld = false;
		return held;
	}

	private static boolean evaluateFrameHold(long nowNanos) {
		if (phase == Phase.CAPTURE_FREEZE) return true;
		if (phase != Phase.WARNING) return false;
		PursuitPresentationTimeline.Stage localStage = PursuitPresentationTimeline.stageAt(warningTicks);
		if (localStage == PursuitPresentationTimeline.Stage.TERMINAL_WARNING) return false;
		if (localStage == PursuitPresentationTimeline.Stage.FROZEN
				|| localStage == PursuitPresentationTimeline.Stage.BLACKOUT) {
			if (freezeFramePending) {
				freezeFramePending = false;
				lastPresentedFrameNanos = nowNanos;
				return false;
			}
			return true;
		}
		long interval = PursuitPresentationTimeline.frameIntervalNanos(warningTicks);
		if (lastPresentedFrameNanos == 0L || nowNanos - lastPresentedFrameNanos >= interval) {
			lastPresentedFrameNanos = nowNanos;
			return false;
		}
		return true;
	}

	public static boolean shouldCoverLoadingScreen() {
		return phase == Phase.BLACKOUT || clearRequested;
	}

	public static boolean blocksPlayerInput() {
		if (phase == Phase.BLACKOUT || phase == Phase.CAPTURE_FREEZE) return true;
		if (phase != Phase.WARNING) return false;
		PursuitPresentationTimeline.Stage localStage = PursuitPresentationTimeline.stageAt(warningTicks);
		return localStage == PursuitPresentationTimeline.Stage.FROZEN
				|| localStage == PursuitPresentationTimeline.Stage.BLACKOUT;
	}

	public static boolean pursuitHudActive() {
		return phase == Phase.RUNNING;
	}

	public static boolean escapeHudActive() {
		return phase == Phase.ESCAPE_RESOLUTION;
	}

	public static boolean holdsNoticeQueue() {
		return noticeQueueLocked;
	}

	public static boolean locksPauseExit() {
		return phase != Phase.IDLE || clearRequested;
	}

	/**
	 * True for the whole pursuit, warning included. The warning is where the background score has to
	 * start leaving: by the time the picture freezes there should already be nothing under it.
	 */
	public static boolean silencesMusic() {
		return phase != Phase.IDLE || clearRequested;
	}

	/**
	 * True once the feed is gone, where the remains of a fading track would be a loose end rather
	 * than a transition. The warning ahead of it lasts at least eight seconds, so the fade driven by
	 * {@link #silencesMusic()} has already carried the track most of the way down by this point.
	 */
	public static boolean cutsMusic() {
		return phase == Phase.BLACKOUT || phase == Phase.RUNNING
				|| phase == Phase.CAPTURE_FREEZE || phase == Phase.ESCAPE_RESOLUTION;
	}

	private static void accept(PursuitPresentationPayload payload) {
		switch (payload.phase()) {
			case PursuitPresentationPayload.WARNING -> beginWarning(payload);
			case PursuitPresentationPayload.BLACKOUT -> {
				if (!sameSession(payload.sessionId())) return;
				phase = Phase.BLACKOUT;
				warningTicks = PursuitPresentationTimeline.PRELUDE_TICKS;
				runningRequested = false;
				lastPresentedFrameNanos = 0L;
				resetTransitionGate();
				clearOwnedPostEffect(Minecraft.getInstance());
			}
			case PursuitPresentationPayload.RUNNING -> {
				if (!sameSession(payload.sessionId())) return;
				form = payload.form();
				phase = Phase.BLACKOUT;
				runningRequested = true;
				noticeQueueLocked = true;
				clearRequested = false;
				resetTransitionGate();
			}
			case PursuitPresentationPayload.CAPTURE_FREEZE -> {
				if (!sameSession(payload.sessionId())) return;
				phase = Phase.CAPTURE_FREEZE;
				resolutionTicks = 0;
				runningRequested = false;
				noticeQueueLocked = true;
				play(Minecraft.getInstance(), ModSounds.ALPHA_CORRUPTION_COLLAPSE, 0.66F, 0.96F);
			}
			case PursuitPresentationPayload.ESCAPE_RESOLUTION -> {
				if (!sameSession(payload.sessionId())) return;
				phase = Phase.ESCAPE_RESOLUTION;
				resolutionTicks = 0;
				runningRequested = false;
				noticeQueueLocked = true;
			}
			default -> {
				clearRequested = true;
				runningRequested = false;
				resetTransitionGate();
			}
		}
	}

	private static void beginWarning(PursuitPresentationPayload payload) {
		Minecraft client = Minecraft.getInstance();
		clearOwnedPostEffect(client);
		sessionId = payload.sessionId();
		form = payload.form();
		phase = Phase.WARNING;
		warningTicks = 0;
		heartbeatCooldown = 0;
		// Every pursuit opens at the calmest band instead of inheriting the previous one's, so the
		// first frames after the blackout are never already fully degraded.
		proximityScanCooldown = 0;
		proximityGrade = PursuitPresentationTimeline.ProximityGrade.DISTANT;
		resolutionTicks = 0;
		lastPresentedFrameNanos = 0L;
		freezeFramePending = false;
		runningRequested = false;
		clearRequested = false;
		noticeQueueLocked = false;
		resetTransitionGate();
		// The ten-second lead-in was carried entirely by terminal text. The two-tone attention
		// signal is the sound people already read as "stop what you are doing", which is exactly
		// the authority the warning wants before anything is visibly wrong.
		play(client, ModSounds.SIGNAL_ALERT, 1.0F, 0.85F);
	}

	private static boolean sameSession(String candidate) {
		return !sessionId.isBlank() && sessionId.equals(candidate);
	}

	private static void tick(Minecraft client) {
		if (phase == Phase.WARNING) {
			warningTicks++;
			if (warningTicks == PursuitPresentationTimeline.FREEZE_START_TICKS) {
				freezeFramePending = true;
				play(client, ModSounds.ALPHA_CORRUPTION_COLLAPSE, 0.72F, 0.92F);
			}
			if (warningTicks >= PursuitPresentationTimeline.FREEZE_START_TICKS
					&& warningTicks < PursuitPresentationTimeline.PRELUDE_TICKS
					&& (warningTicks - PursuitPresentationTimeline.FREEZE_START_TICKS) % 5 == 0) {
				float pitch = 0.50F + (warningTicks % 4) * 0.07F;
				play(client, ModSounds.ALPHA_CORRUPTION_WARNING, pitch, 0.30F);
			}
		}
		if (runningRequested && readyToRevealMirror(client)) {
			runningRequested = false;
			phase = Phase.RUNNING;
			heartbeatCooldown = 1;
			installPostEffect(client);
		}
		if (phase == Phase.RUNNING) {
			tickProximityGrade(client);
			installPostEffect(client);
			tickHeartbeat(client);
		}
		if (phase == Phase.CAPTURE_FREEZE && resolutionTicks++ < 60
				&& resolutionTicks % 5 == 0) {
			float pitch = 0.48F + (resolutionTicks % 4) * 0.06F;
			play(client, ModSounds.ALPHA_CORRUPTION_WARNING, pitch, 0.34F);
		}
		if (clearRequested && readyToClear(client)) {
			reset(client);
		}
	}

	private static boolean readyToRevealMirror(Minecraft client) {
		return destinationReady(client, true);
	}

	private static boolean readyToClear(Minecraft client) {
		return destinationReady(client, false);
	}

	private static boolean destinationReady(Minecraft client, boolean mirrorDestination) {
		if (worldLoadingVisible(client)) {
			transitionLoadingObserved = true;
			destinationStableTicks = 0;
			return false;
		}
		if (client.level == null || PursuitDimensions.isMirror(client.level) != mirrorDestination) {
			destinationStableTicks = 0;
			return false;
		}
		destinationStableTicks++;
		int requiredStableTicks = transitionLoadingObserved
				? DESTINATION_STABLE_TICKS_AFTER_LOAD : DESTINATION_FALLBACK_STABLE_TICKS;
		return destinationStableTicks >= requiredStableTicks;
	}

	private static boolean worldLoadingVisible(Minecraft client) {
		return client.screen instanceof LevelLoadingScreen || client.getOverlay() != null;
	}

	private static void resetTransitionGate() {
		destinationStableTicks = 0;
		transitionLoadingObserved = false;
	}

	private static void tickHeartbeat(Minecraft client) {
		if (client.level == null || client.player == null) return;
		if (heartbeatCooldown-- > 0) return;
		ReworkEntity closest = client.level.getEntitiesOfClass(ReworkEntity.class,
						client.player.getBoundingBox().inflate(96.0D), ReworkEntity::isAlive)
				.stream().min(Comparator.comparingDouble(client.player::distanceToSqr)).orElse(null);
		if (closest == null) {
			heartbeatCooldown = 10;
			return;
		}
		double distance = Math.sqrt(closest.distanceToSqr(client.player));
		heartbeatCooldown = PursuitPresentationTimeline.heartbeatIntervalTicks(distance);
		float proximity = (float) (1.0D - Math.clamp((distance - 3.0D) / 45.0D, 0.0D, 1.0D));
		play(client, SoundEvents.WARDEN_HEARTBEAT, 0.82F + proximity * 0.30F,
				0.52F + proximity * 0.40F);
	}

	/**
	 * Re-grades how degraded the picture should be from the nearest corrector's distance. This is
	 * the only spatial cue that does not require hearing the heartbeat, so it matters for players
	 * with sound off, and it fits the fiction: the closer the thing gets, the less bandwidth the
	 * world has left to describe itself with.
	 */
	private static void tickProximityGrade(Minecraft client) {
		if (proximityScanCooldown-- > 0) return;
		proximityScanCooldown = PROXIMITY_SCAN_INTERVAL_TICKS;
		proximityGrade = PursuitPresentationTimeline.proximityGrade(
				closestCorrectorDistance(client), proximityGrade);
	}

	/** {@link Double#MAX_VALUE} when nothing is in range, which grades as DISTANT. */
	private static double closestCorrectorDistance(Minecraft client) {
		if (client.level == null || client.player == null) return Double.MAX_VALUE;
		ReworkEntity closest = client.level.getEntitiesOfClass(ReworkEntity.class,
						client.player.getBoundingBox().inflate(96.0D), ReworkEntity::isAlive)
				.stream().min(Comparator.comparingDouble(client.player::distanceToSqr)).orElse(null);
		return closest == null ? Double.MAX_VALUE : Math.sqrt(closest.distanceToSqr(client.player));
	}

	private static Identifier postEffectForGrade() {
		return switch (proximityGrade) {
			case DISTANT -> PURSUIT_POST_EFFECT_DISTANT;
			case CLOSE -> PURSUIT_POST_EFFECT_CLOSE;
			case CONTACT -> PURSUIT_POST_EFFECT_CONTACT;
			default -> PURSUIT_POST_EFFECT;
		};
	}

	private static void installPostEffect(Minecraft client) {
		Identifier wanted = postEffectForGrade();
		if (wanted.equals(client.gameRenderer.currentPostEffect())) return;
		((GameRendererPostEffectInvoker) client.gameRenderer)
				.thefourthfrequency$setPostEffect(wanted);
	}

	private static void clearOwnedPostEffect(Minecraft client) {
		Identifier active = client.gameRenderer.currentPostEffect();
		// Any band we may have installed counts as ours; checking only the mid one would strand
		// the chain whenever the pursuit ended while the corrector was near or far.
		if (PURSUIT_POST_EFFECT.equals(active) || PURSUIT_POST_EFFECT_DISTANT.equals(active)
				|| PURSUIT_POST_EFFECT_CLOSE.equals(active) || PURSUIT_POST_EFFECT_CONTACT.equals(active)) {
			client.gameRenderer.clearPostEffect();
		}
	}

	private static void renderOverlay(GuiGraphics graphics) {
		if (phase == Phase.IDLE) return;
		int width = graphics.guiWidth();
		int height = graphics.guiHeight();
		if (phase == Phase.BLACKOUT || clearRequested) {
			graphics.fill(0, 0, width, height, 0xFF000000);
			return;
		}
		if (phase == Phase.WARNING) return;
		graphics.fill(0, 0, width, height, 0x14000000);
		for (int y = 0; y < height; y += 3) graphics.fill(0, y, width, y + 1, 0x22000000);
		renderInterference(graphics, clientTick(), 3, 38);
		int border = Math.max(2, Math.min(width, height) / 38);
		graphics.fill(0, 0, width, border, 0x8A000000);
		graphics.fill(0, height - border, width, height, 0x8A000000);
		graphics.fill(0, border, border, height - border, 0x8A000000);
		graphics.fill(width - border, border, width, height - border, 0x8A000000);
	}

	private static int clientTick() {
		Minecraft client = Minecraft.getInstance();
		return client.player == null ? warningTicks : client.player.tickCount;
	}

	private static void renderInterference(GuiGraphics graphics, int tick, int bands, int alpha) {
		int width = graphics.guiWidth();
		int height = graphics.guiHeight();
		for (int index = 0; index < bands; index++) {
			int seed = scramble(tick * 0x1F123BB5 + index * 0x6D2B79F5 + form * 71);
			int y = Math.floorMod(seed, Math.max(1, height));
			int bandHeight = 1 + Math.floorMod(seed >>> 11, 5);
			int color = Math.clamp(alpha + Math.floorMod(seed >>> 19, 30), 0, 255) << 24
					| ((seed & 1) == 0 ? 0x000000 : 0x7A7577);
			graphics.fill(0, y, width, Math.min(height, y + bandHeight), color);
		}
	}

	private static int scramble(int value) {
		value ^= value >>> 16;
		value *= 0x7FEB352D;
		value ^= value >>> 15;
		value *= 0x846CA68B;
		return value ^ value >>> 16;
	}

	private static void play(Minecraft client, net.minecraft.sounds.SoundEvent sound,
			float pitch, float volume) {
		client.getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch, volume));
	}

	private static void reset(Minecraft client) {
		clearOwnedPostEffect(client);
		phase = Phase.IDLE;
		sessionId = "";
		form = 0;
		warningTicks = 0;
		heartbeatCooldown = 0;
		proximityScanCooldown = 0;
		proximityGrade = PursuitPresentationTimeline.ProximityGrade.DISTANT;
		resolutionTicks = 0;
		lastPresentedFrameNanos = 0L;
		frameHeld = false;
		freezeFramePending = false;
		runningRequested = false;
		clearRequested = false;
		noticeQueueLocked = false;
		resetTransitionGate();
	}

	private enum Phase {
		IDLE,
		WARNING,
		BLACKOUT,
		RUNNING,
		CAPTURE_FREEZE,
		ESCAPE_RESOLUTION
	}
}
