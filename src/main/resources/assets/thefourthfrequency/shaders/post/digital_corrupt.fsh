#version 330

// The mod's digital-corruption filter: the picture arriving through a pipeline that is failing.
//
// This is the language the *rules* break in - the correction's pursuit, and the world interface's
// hold on the player. Its opposite number is analog_signal.fsh, which is the language the *medium*
// breaks in. Keeping the two apart is deliberate: a player should be able to tell "the recording is
// damaged" from "the thing rendering this is coming apart" without being told which is which.
//
// What this replaces was minecraft:post/bits - a flat mosaic plus a posterise, applied evenly to
// every pixel of every frame. Evenness is exactly what made it read as a filter *switch* rather
// than as damage: real corruption is patchy in space and held in time.
//
// #moj_import <minecraft:globals.glsl> gives GameTime and ScreenSize.
//
// It is worth writing down why that import is safe here, because the sibling shader
// world_interface_edge.fsh carries a warning about the opposite case. A post pass's RenderPipeline
// is built from RenderPipelines.POST_PROCESSING_SNIPPET plus SamplerInfo plus whatever uniform
// blocks the chain json names - and POST_PROCESSING_SNIPPET names none of the built-ins. Globals is
// still bound, because GlProgram carries its own BUILT_IN_UNIFORMS set - {Projection, Lighting,
// Fog, Globals} - and binds any active block it finds there whether the pipeline declared it or
// not. SamplerInfo is the one to distrust: it is declared for every post pass but only *filled* for
// some, and an unfilled std140 block reads as zeroes with no error anywhere. So sizes come from
// Globals.ScreenSize, never from SamplerInfo.
#moj_import <minecraft:globals.glsl>

uniform sampler2D InSampler;

in vec2 texCoord;

layout(std140) uniform CorruptConfig {
    // Master fader. 0 leaves the frame untouched, pixel for pixel.
    float Strength;
    // Ticks one corruption pattern survives before it re-rolls.
    //
    // This is a safety ceiling as much as a look. The world bible caps flicker at 3 Hz, and this is
    // the rate at which everything coherent in this shader changes, so every chain that uses it
    // holds for at least seven ticks (20/7 = 2.86 Hz) and DigitalCorruptChainTest asserts it.
    // It is also simply what corruption looks like: a sequence of frames that each survive a
    // moment. A pattern that re-rolls every frame is a shimmer, and a shimmer reads as an effect.
    float HoldTicks;
    // Height of one damaged band, in pixels.
    float BandHeight;
    // How far a displaced band travels, as a fraction of screen width.
    float BandShift;
    // Share of bands that lose their line entirely and hold one row instead.
    float BandLoss;
    // Per-band red/blue misalignment, in pixels. Hard and per-band on purpose - a smooth radial
    // separation is a lens, and a lens is the other shader's job.
    float ChannelSplit;
    // Macroblock edge, in pixels. Below 2 the block stage is off.
    float BlockSize;
    // Share of macroblocks that actually give out. This is the difference between compression
    // failing in patches and a mosaic laid over the whole picture.
    float BlockShare;
    // Colour quantisation steps per channel. Below 2 the stage is off.
    float Levels;
    // Pull towards luminance. The pursuit's identity is that it sees the player in less colour
    // than the world has.
    float Desaturate;
    // Radius where the treatment starts, and where it reaches full strength. Set EdgeRadius at or
    // below CenterClear for a full-frame treatment; set them apart for an edge-only warning that
    // leaves the middle of the screen to play in.
    float CenterClear;
    float EdgeRadius;
    // The raster and the tube.
    //
    // These used to be drawn over the pursuit as GUI rectangles - a one-pixel fill every three rows
    // and four dark bars around the edge - which is the same approximation this whole shader exists
    // to stop making. A cosine trough is a raster that never quite fills in; a one-pixel line is a
    // grid laid on top of the picture. Depth below 0 or pitch below 1 switches the raster off.
    float ScanDepth;
    float ScanPitch;
    float Vignette;
    // Pull towards luminance applied to the *finished* frame, after the damage has been blended back
    // over the original at Strength.
    //
    // Desaturate above cannot do this. It acts inside the treated copy, and the last line of this
    // shader mixes that copy back over the untouched picture by `amount` - so at Strength 0.8 a
    // fifth of the original colour survives however hard Desaturate was pushed, and the pursuit,
    // whose whole identity is that it sees the world in less colour, came out merely tinted. This
    // one lands after that mix and therefore means what it says.
    //
    // It also keeps the block a multiple of four floats, which the field it replaced existed for: a
    // vec4 aligns to sixteen bytes in std140, and any other count makes the padding before Tint
    // something the json side has to agree about by accident rather than by construction.
    float FinalDesaturate;
    // rgb is the cast the damage carries, a is how much of it lands.
    vec4 Tint;
};

out vec4 fragColor;

float corruptHash(vec2 seed) {
    vec3 spread = fract(vec3(seed.xyx) * vec3(0.1031, 0.1030, 0.0973));
    spread += dot(spread, spread.yzx + 33.33);
    return fract((spread.x + spread.y) * spread.z);
}

void main() {
    vec2 screen = max(ScreenSize, vec2(1.0));
    vec3 base = texture(InSampler, texCoord).rgb;

    float mask = 1.0;
    if (EdgeRadius > CenterClear) {
        // Radial distance in texture space: 0 in the middle, 1 at the midpoint of each edge, about
        // 1.41 in the corners. Squared so the ramp stays out of the middle third rather than
        // creeping evenly inward.
        float radius = length(texCoord - vec2(0.5)) * 2.0;
        mask = smoothstep(CenterClear, EdgeRadius, radius);
        mask *= mask;
    }
    float amount = clamp(Strength * mask, 0.0, 1.0);
    if (amount <= 0.0) {
        fragColor = vec4(base, 1.0);
        return;
    }

    float slot = floor(GameTime * 24000.0 / max(HoldTicks, 1.0));
    vec2 uv = texCoord;

    // Macroblocking, gated per block. Sampling the whole frame on a coarse grid is a mosaic; doing
    // it to the blocks a hash picks is a codec giving out in patches, which is the thing this is
    // meant to look like.
    vec2 grid = screen / max(BlockSize, 1.0);
    vec2 cell = floor(uv * grid);
    float blockGate = step(2.0, BlockSize)
            * step(corruptHash(cell + vec2(slot * 3.7)), BlockShare * amount);
    uv = mix(uv, (cell + 0.5) / grid, blockGate);

    // Band displacement. Only a minority of bands move at all, and the ones that do move far: an
    // even wobble across every row is a wave, and a wave is not damage.
    float row = floor(texCoord.y * screen.y / max(BandHeight, 1.0));
    float moved = step(0.78, corruptHash(vec2(row, slot)));
    float shift = (corruptHash(vec2(row, slot + 19.0)) - 0.5) * 2.0 * BandShift * amount * moved;
    uv.x = fract(uv.x + shift);

    float split = (corruptHash(vec2(row, slot + 41.0)) - 0.5) * 2.0 * ChannelSplit * amount
            / screen.x;
    vec3 color = vec3(
            texture(InSampler, vec2(uv.x + split, uv.y)).r,
            texture(InSampler, uv).g,
            texture(InSampler, vec2(uv.x - split, uv.y)).b);

    // A lost band holds one row of the picture across its whole height - the single most legible
    // "this is a buffer, and the buffer is wrong" artefact there is.
    //
    // Written as a mix of two samples rather than as a branch around one: sampling inside
    // non-uniform control flow leaves the implicit level of detail undefined, and while these
    // targets carry no mipmaps, the cost of not relying on that is one texture read.
    float lost = step(corruptHash(vec2(row, slot + 71.0)), BandLoss * amount);
    float heldRow = clamp((row * BandHeight + 0.5) / screen.y, 0.0, 1.0);
    color = mix(color, texture(InSampler, vec2(uv.x, heldRow)).rgb, lost);

    float luma = dot(color, vec3(0.2126, 0.7152, 0.0722));
    color = mix(color, vec3(luma), clamp(Desaturate, 0.0, 1.0));

    if (Levels >= 2.0) {
        // Dithered before it is quantised. Undithered, a low step count rings every gradient in the
        // sky into hard contours, and contours are a poster effect rather than a bit depth.
        float dither = (corruptHash(floor(texCoord * screen)) - 0.5) / Levels;
        color = floor(color * Levels + dither * Levels + 0.5) / Levels;
    }

    color = mix(color, Tint.rgb * (0.35 + 0.65 * luma), clamp(Tint.a, 0.0, 1.0));

    if (ScanDepth > 0.0 && ScanPitch >= 1.0) {
        float scan = 0.5 + 0.5 * cos(texCoord.y * screen.y * 6.2831853 / ScanPitch);
        color *= 1.0 - ScanDepth * scan;
    }
    color *= 1.0 - Vignette * smoothstep(0.45, 1.35, length(texCoord - vec2(0.5)) * 2.0);

    // The damage goes back over the untouched picture here, and only then is the whole frame pulled
    // towards luminance. See FinalDesaturate: doing it before this mix leaves `1 - amount` of the
    // original colour in the result, which is why a chain asking for full desaturation still came
    // out with colour in it.
    vec3 finished = mix(base, color, amount);
    float finishedLuma = dot(finished, vec3(0.2126, 0.7152, 0.0722));
    finished = mix(finished, vec3(finishedLuma), clamp(FinalDesaturate, 0.0, 1.0));
    fragColor = vec4(clamp(finished, 0.0, 1.0), 1.0);
}
