package com.xm.thefourthfrequency.client_ui;

/** Pure fog policy shared by the client mixin and deterministic unit tests. */
public record AtmosphericFogProfile(
		float renderStart,
		float renderEnd,
		float redScale,
		float greenScale,
		float blueScale,
		float desaturation) {
	public static final int FIXED_RENDER_DISTANCE_CHUNKS = 3;
	public static final float FIXED_RENDER_DISTANCE_BLOCKS = FIXED_RENDER_DISTANCE_CHUNKS * 16.0F;
	public static final float EDGE_HIDING_FOG_END = FIXED_RENDER_DISTANCE_BLOCKS - 5.0F;
	private static final float FIXED_ONLY_FOG_START = 32.0F;

	public static AtmosphericFogProfile fixedDistanceOnly() {
		return new AtmosphericFogProfile(FIXED_ONLY_FOG_START, EDGE_HIDING_FOG_END,
				1.0F, 1.0F, 1.0F, 0.0F);
	}

	public static AtmosphericFogProfile sample(float skyExposure, float rain, float thunder,
			float night, float cameraY, boolean enhanceAtmosphere) {
		if (!enhanceAtmosphere) return fixedDistanceOnly();

		float sky = clamp01(skyExposure);
		float rainAmount = clamp01(rain);
		float thunderAmount = clamp01(thunder);
		float nightAmount = clamp01(night);
		float underground = 1.0F - smoothstep(48.0F, 96.0F, cameraY);
		float cave = (1.0F - sky) * underground;
		float voidDepth = 1.0F - smoothstep(-48.0F, -24.0F, cameraY);
		float cloudLayer = smoothstep(184.0F, 208.0F, cameraY)
				* (1.0F - smoothstep(240.0F, 272.0F, cameraY)) * sky;

		float density = max(rainAmount * 0.55F, thunderAmount * 0.90F,
				cave * 0.75F, voidDepth * 0.95F, cloudLayer * 0.65F, nightAmount * 0.18F);
		float start = lerp(24.0F, 4.0F, density);
		float end = lerp(EDGE_HIDING_FOG_END, 27.0F, density);

		float baseScale = 1.0F - rainAmount * 0.12F - thunderAmount * 0.18F
				- cave * 0.20F - voidDepth * 0.30F - nightAmount * 0.06F;
		float red = clamp(baseScale * (1.0F - rainAmount * 0.04F) + cloudLayer * 0.08F,
				0.45F, 1.15F);
		float green = clamp(baseScale * (1.0F - rainAmount * 0.02F) + cloudLayer * 0.08F,
				0.45F, 1.15F);
		float blue = clamp(baseScale * (1.0F + rainAmount * 0.06F + nightAmount * 0.04F)
				+ cloudLayer * 0.06F, 0.45F, 1.15F);
		float muted = clamp01(rainAmount * 0.12F + thunderAmount * 0.18F + cave * 0.16F
				+ voidDepth * 0.22F + cloudLayer * 0.20F);
		return new AtmosphericFogProfile(start, Math.max(start + 4.0F, end),
				red, green, blue, muted);
	}

	public static float nightFactor(long dayTime) {
		float tick = Math.floorMod(dayTime, 24_000L);
		float dusk = smoothstep(12_000.0F, 14_000.0F, tick);
		float dawn = 1.0F - smoothstep(22_000.0F, 24_000.0F, tick);
		return dusk * dawn;
	}

	public AtmosphericFogProfile blendToward(AtmosphericFogProfile target, float amount) {
		float step = clamp01(amount);
		return new AtmosphericFogProfile(
				lerp(renderStart, target.renderStart, step),
				lerp(renderEnd, target.renderEnd, step),
				lerp(redScale, target.redScale, step),
				lerp(greenScale, target.greenScale, step),
				lerp(blueScale, target.blueScale, step),
				lerp(desaturation, target.desaturation, step));
	}

	public float clampRenderStart(float vanillaStart) {
		return Math.min(vanillaStart, renderStart);
	}

	public float clampRenderEnd(float vanillaEnd) {
		return Math.min(vanillaEnd, renderEnd);
	}

	public FogColor tint(float red, float green, float blue) {
		float luminance = red * 0.2126F + green * 0.7152F + blue * 0.0722F;
		return new FogColor(
				clamp01(lerp(red, luminance, desaturation) * redScale),
				clamp01(lerp(green, luminance, desaturation) * greenScale),
				clamp01(lerp(blue, luminance, desaturation) * blueScale));
	}

	public record FogColor(float red, float green, float blue) {
	}

	private static float smoothstep(float edge0, float edge1, float value) {
		float progress = clamp01((value - edge0) / (edge1 - edge0));
		return progress * progress * (3.0F - 2.0F * progress);
	}

	private static float lerp(float from, float to, float amount) {
		return from + (to - from) * amount;
	}

	private static float max(float... values) {
		float result = values[0];
		for (int index = 1; index < values.length; index++) result = Math.max(result, values[index]);
		return result;
	}

	private static float clamp01(float value) {
		return clamp(value, 0.0F, 1.0F);
	}

	private static float clamp(float value, float minimum, float maximum) {
		return Math.max(minimum, Math.min(maximum, value));
	}
}

