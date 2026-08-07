package com.xm.thefourthfrequency.client_ui;

import com.xm.thefourthfrequency.terminal.SkyInstrumentPolicy;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.state.SkyRenderState;

/**
 * What the weather tool is actually measuring.
 *
 * <p>The four channels come from {@link SkyRenderState}, pushed in by
 * {@code SkyRendererAnomalyMixin} on the frame the sky is drawn. That is deliberate and it is the
 * only correct source: 1.21 moved sky rendering behind an extracted render state, so the values
 * the dome is actually painted with live there and nowhere else. Re-deriving them from
 * {@code ClientLevel} would produce a second opinion that drifts away from the picture the player
 * is looking at, which is exactly the bug this whole feature exists to fix.</p>
 *
 * <p>The history buffer only advances while somebody is looking at the tool. {@link #observe} runs
 * on the render thread once per frame for every player in every world, so it does the least
 * possible work: store four floats and return.</p>
 */
public final class SkyInstrumentSampler {
	/** Samples kept for the trace. At {@link #SAMPLE_INTERVAL_TICKS} this is a four-second window. */
	public static final int HISTORY = 40;
	/**
	 * Two ticks per sample.
	 *
	 * <p>A red horizon takes seventy ticks to reach full strength. Four seconds of history is
	 * enough for that arrival to be visibly a climb rather than a step, which is what lets a player
	 * read the trace as a warning instead of as a state that was simply always there.</p>
	 */
	public static final int SAMPLE_INTERVAL_TICKS = 2;
	/** How long the buffer keeps advancing after the last frame that asked for it. */
	private static final int OBSERVATION_GRACE_TICKS = 40;

	private static final float[][] TRACE =
			new float[SkyInstrumentPolicy.Channel.values().length][HISTORY];

	private static volatile float zenith;
	private static volatile float horizon;
	private static volatile float stars;
	private static volatile float phase;

	private static int writeIndex;
	private static int filled;
	private static int sampleCooldown;
	private static int observedGrace;
	private static boolean initialized;

	private SkyInstrumentSampler() {
	}

	public static void initialize() {
		if (initialized) return;
		initialized = true;
		ClientTickEvents.END_CLIENT_TICK.register(SkyInstrumentSampler::tick);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> reset());
	}

	/**
	 * Called from the sky renderer with the state the dome is about to be drawn from.
	 *
	 * <p>Render-thread hot path: four field writes and no allocation. Everything that costs
	 * anything - history, decay, whether a channel is saturated - happens on the client tick.</p>
	 */
	public static void observe(SkyRenderState state) {
		if (state == null) return;
		zenith = luminance(state.skyColor);
		horizon = Math.max(AnomalyPresentationController.redHorizonStrength(),
				state.sunriseAndSunsetColor == 0 ? 0.0F : alpha(state.sunriseAndSunsetColor));
		stars = Math.clamp(state.starBrightness, 0.0F, 1.0F);
		// Half a turn is as wrong as a sky can be; beyond that it is coming back round.
		phase = Math.clamp(AnomalyPresentationController.celestialPhaseError() * 2.0F, 0.0F, 1.0F);
	}

	/** Tells the sampler the tool is on screen this frame, so the trace keeps advancing. */
	public static void markObserved() {
		observedGrace = OBSERVATION_GRACE_TICKS;
	}

	public static float current(SkyInstrumentPolicy.Channel channel) {
		return switch (channel) {
			case ZENITH -> zenith;
			case HORIZON -> horizon;
			case STARS -> stars;
			case PHASE -> phase;
		};
	}

	/** Samples oldest to newest. Shorter than {@link #HISTORY} until the buffer has filled once. */
	public static float trace(SkyInstrumentPolicy.Channel channel, int index) {
		if (index < 0 || index >= filled) return 0.0F;
		return TRACE[channel.ordinal()][Math.floorMod(writeIndex - filled + index, HISTORY)];
	}

	public static int traceLength() {
		return filled;
	}

	private static void tick(Minecraft client) {
		if (client.level == null) {
			reset();
			return;
		}
		if (observedGrace <= 0) return;
		observedGrace--;
		if (sampleCooldown-- > 0) return;
		sampleCooldown = SAMPLE_INTERVAL_TICKS - 1;
		for (SkyInstrumentPolicy.Channel channel : SkyInstrumentPolicy.Channel.values()) {
			TRACE[channel.ordinal()][writeIndex] = current(channel);
		}
		writeIndex = (writeIndex + 1) % HISTORY;
		if (filled < HISTORY) filled++;
	}

	private static void reset() {
		writeIndex = 0;
		filled = 0;
		sampleCooldown = 0;
		observedGrace = 0;
		zenith = 0.0F;
		horizon = 0.0F;
		stars = 0.0F;
		phase = 0.0F;
	}

	/** Rec. 601 luma. The dome losing brightness is as legible a fault as it gaining colour. */
	private static float luminance(int argb) {
		float red = (argb >> 16 & 255) / 255.0F;
		float green = (argb >> 8 & 255) / 255.0F;
		float blue = (argb & 255) / 255.0F;
		return Math.clamp(0.299F * red + 0.587F * green + 0.114F * blue, 0.0F, 1.0F);
	}

	private static float alpha(int argb) {
		return Math.clamp((argb >>> 24) / 255.0F, 0.0F, 1.0F);
	}
}
