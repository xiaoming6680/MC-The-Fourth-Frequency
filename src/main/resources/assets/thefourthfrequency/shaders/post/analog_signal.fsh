#version 330

// The mod's analog-signal filter: the picture arriving through a medium that is failing.
//
// This is the language the *recording* breaks in - the anomalies, and the loading screens that
// share their vocabulary. Its opposite number is digital_corrupt.fsh, which is the language the
// rules break in. Everything here is continuous where that one is discrete: tape stretches and
// tubes bloom, they do not step.
//
// What this replaces on the anomaly burst was a stack of grey rectangles drawn over the frame.
// Rectangles cannot displace the picture, so the burst always read as damage happening *in front
// of* the world rather than to it. Here the world itself is what gets bent, smeared and mistracked.
//
// See digital_corrupt.fsh for why Globals is safe to import and SamplerInfo is not.
#moj_import <minecraft:globals.glsl>

uniform sampler2D InSampler;

in vec2 texCoord;

layout(std140) uniform SignalConfig {
    // Master fader. 0 leaves the frame untouched, pixel for pixel - every term below is scaled by
    // it, so there is no crossfade between a treated and an untreated image to go soft in the
    // middle.
    float Strength;
    // Radial red/blue separation at the frame edge, in pixels. Zero in the middle, growing outward,
    // because that is what a lens and a tube actually do. A uniform split is a registration error.
    float Chroma;
    // Horizontal row wobble, in pixels, and how many rows one cycle of it spans.
    float Wobble;
    float WobblePitch;
    // Scanline depth, and the pixel pitch of one light/dark pair.
    float ScanDepth;
    float ScanPitch;
    // The mistracked bar travelling up the picture: its height in pixels, and its speed in pixels
    // per tick. Height below 1 switches it off.
    float RollHeight;
    float RollSpeed;
    // Zero-mean per-pixel noise. Deliberately re-rolled every tick rather than held to the 3 Hz
    // ceiling the rest of this mod's motion obeys: that ceiling exists for coherent flashes, and
    // grain is the opposite of coherent - it holds mean luminance constant by construction. Held at
    // 3 Hz it stops being grain and becomes three still images a second, which is worse on both
    // counts.
    float Grain;
    // How much the bright parts of the picture bleed into the dark ones.
    float Halation;
    // Corner darkening.
    float Vignette;
    // Pull towards luminance.
    float Desaturate;
    // The picture torn into horizontal bands: how many bands the frame is cut into, how far a torn
    // one is dragged sideways as a fraction of width, and what share of them lose their line
    // altogether. Zero bands switches the whole stage off.
    //
    // This is the layer the overlay used to fake, and faking is all it could do: it drew displaced
    // grey streaks with a red and a cyan rectangle either side of them, because a GUI draw cannot
    // move the picture - only cover it. Here the bands are the picture, dragged.
    float TearBands;
    float TearShift;
    float TearLoss;
    // Ticks one tear pattern survives before it re-rolls. The 3 Hz ceiling applies: a torn band is a
    // coherent change in a large part of the frame, so this may not go below seven.
    float TearHoldTicks;
    // rgb is the cast the medium carries, a is how much of it lands.
    vec4 Tint;
};

out vec4 fragColor;

float signalHash(vec2 seed) {
    vec3 spread = fract(vec3(seed.xyx) * vec3(0.1031, 0.1030, 0.0973));
    spread += dot(spread, spread.yzx + 33.33);
    return fract((spread.x + spread.y) * spread.z);
}

void main() {
    vec2 screen = max(ScreenSize, vec2(1.0));
    float amount = clamp(Strength, 0.0, 1.0);
    if (amount <= 0.0) {
        fragColor = vec4(texture(InSampler, texCoord).rgb, 1.0);
        return;
    }
    float ticks = GameTime * 24000.0;
    vec2 centred = texCoord - vec2(0.5);
    float radius = length(centred) * 2.0;

    // Two sine terms whose periods are not multiples of each other, so the wobble never settles
    // into a pattern the eye can predict and start ignoring.
    float rows = texCoord.y * screen.y;
    float wobble = sin(rows / max(WobblePitch, 1.0) + ticks * 0.31)
            * sin(rows / max(WobblePitch * 2.7, 1.0) - ticks * 0.17);
    vec2 uv = vec2(texCoord.x + wobble * Wobble * amount / screen.x, texCoord.y);

    // Tearing happens before the chroma split so the split follows the band, which is what makes a
    // dragged band read as a piece of the picture rather than as a coloured bar laid over it.
    float tearLost = 0.0;
    if (TearBands >= 1.0) {
        float slot = floor(ticks / max(TearHoldTicks, 1.0));
        float band = floor(texCoord.y * TearBands);
        // A minority of bands move, and the ones that do move far. An even displacement across every
        // band is a wave; damage is uneven.
        float moved = step(0.74, signalHash(vec2(band, slot)));
        uv.x = fract(uv.x
                + (signalHash(vec2(band, slot + 23.0)) - 0.5) * 2.0 * TearShift * amount * moved);
        tearLost = step(signalHash(vec2(band, slot + 61.0)), TearLoss * amount);
    }

    vec2 separation = centred * (Chroma * amount * radius) / screen;
    vec3 color = vec3(
            texture(InSampler, uv + separation).r,
            texture(InSampler, uv).g,
            texture(InSampler, uv - separation).b);
    // A band that lost its line keeps one row of itself across its whole height, darkened. Written
    // as a mix of two samples rather than a branch: sampling in non-uniform control flow leaves the
    // implicit level of detail undefined, and one extra read is cheaper than relying on that.
    if (TearBands >= 1.0) {
        float held = clamp((floor(texCoord.y * TearBands) + 0.5) / TearBands, 0.0, 1.0);
        color = mix(color, texture(InSampler, vec2(uv.x, held)).rgb * 0.78, tearLost);
    }

    // Halation: light spreading sideways inside the tube, keyed on what is actually bright. Four
    // taps rather than a blur pass - this only has to read as a glow around highlights, and a
    // second render target for it would double the cost of the whole chain.
    vec2 bleedStep = vec2(2.5) / screen;
    vec3 bleed = texture(InSampler, uv + vec2(bleedStep.x * 2.0, 0.0)).rgb
            + texture(InSampler, uv - vec2(bleedStep.x * 2.0, 0.0)).rgb
            + texture(InSampler, uv + vec2(0.0, bleedStep.y * 2.0)).rgb
            + texture(InSampler, uv - vec2(0.0, bleedStep.y * 2.0)).rgb;
    bleed *= 0.25;
    color += bleed * smoothstep(0.52, 1.0, max(max(bleed.r, bleed.g), bleed.b)) * Halation * amount;

    // Scanlines as a cosine rather than as one-pixel lines. Hard lines are a grid laid over the
    // image; a soft trough is a raster that never quite fills in.
    float scan = 0.5 + 0.5 * cos(rows * 6.2831853 / max(ScanPitch, 1.0));
    color *= 1.0 - ScanDepth * amount * scan;

    if (RollHeight >= 1.0) {
        // Travels upward, wrapping. Distance is measured through the wrap so the bar does not tear
        // itself in half as it crosses the top of the frame.
        float rollCentre = fract(-ticks * RollSpeed / screen.y);
        float distance = abs(fract(texCoord.y - rollCentre + 0.5) - 0.5) * screen.y;
        float roll = 1.0 - smoothstep(0.0, RollHeight, distance);
        color = color * (1.0 + 0.22 * roll * amount) + vec3(0.05 * roll * amount);
    }

    float luma = dot(color, vec3(0.2126, 0.7152, 0.0722));
    color = mix(color, vec3(luma), clamp(Desaturate, 0.0, 1.0) * amount);
    color = mix(color, Tint.rgb * (0.35 + 0.65 * luma), clamp(Tint.a, 0.0, 1.0) * amount);

    color *= 1.0 - Vignette * amount * smoothstep(0.45, 1.35, radius);
    color += (signalHash(floor(texCoord * screen) + vec2(floor(ticks))) - 0.5) * Grain * amount;

    fragColor = vec4(clamp(color, 0.0, 1.0), 1.0);
}
