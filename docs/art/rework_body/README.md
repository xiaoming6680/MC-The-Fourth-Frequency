# Rework Body five-form art sources

The two source boards in this directory were generated on 2026-07-21 with Codex's built-in
`imagegen` mode. They are production references, not runtime textures. The deterministic
UV finishing script is [`tools/prepare_rework_body_art.py`](../../../tools/prepare_rework_body_art.py);
it samples the material board into the model's semantic UV islands, adds stage-specific
bruising, fascia, necrosis, bone and facial details, and writes the seven 256×256 runtime PNGs.

## Orthographic concept prompt

```text
Use case: stylized-concept
Asset type: production concept sheet for a Minecraft Fabric Java entity remodel
Primary request: Create one unified five-stage orthographic creature evolution sheet for the fictional "Rework Body". It begins as an extremely thin elongated humanoid and progressively becomes a taller, more mutilated asymmetric flesh construct. Show stages 1 through 5 left-to-right. For every stage show a full-body front view and a full-body rear view side-by-side, all standing on the same baseline and at a consistent orthographic scale.
Subject: Every stage has two normal long dragging arms plus an exact number of articulated back arms: stage 1 has 2, stage 2 has 4, stage 3 has 6, stage 4 has 8, stage 5 has 10. Every back arm visibly has upper arm, forearm, and claw joints. Heights progress approximately 2.45, 2.60, 2.75, 2.95, and 3.15 Minecraft blocks. Stage 1: gaunt humanoid, long neck, sunken eye sockets, needle teeth. Stage 2: shoulder blades and ribs turn outward, torn skin and exposed dark-red fascia, mouth corners split toward ears. Stage 3: asymmetric back arms, raised spine, twisted torso, necrotic black patches, protruding bone, collapsed eye sockets, displaced jaw. Stage 4: eight three-segment spider-like back arms, peeled face, double mouth parts, faint dark-red and bone-white glow only inside eyes and mouth. Stage 5: ten-arm malformed limb crown, fully exposed spine, face split vertically, layered gums and teeth, faint glow only in mouth, spine, and subcutaneous cracks.
Style/medium: grounded realistic horror creature concept art translated into clearly segmented cuboid-friendly anatomy suitable for a native Minecraft Java model; detailed rotten flesh materials but readable silhouettes, no painterly fog.
Scene/backdrop: flat neutral charcoal-gray studio sheet, even lighting, no environment, no floor shadows.
Color palette: gray-white corpse skin, bruised purple joints, dried brown-red blood, dark-red fascia, necrotic black, dirty bone-white; stage 4-5 glow is dim and never neon.
Composition/framing: wide landscape sheet, ten non-overlapping full bodies in a strict five-column orthographic lineup, generous padding, every hand/arm/claw entirely in frame. Front and rear views must match each stage's anatomy.
Constraints: exact back-arm counts 2/4/6/8/10; preserve two normal arms on all stages; clear progressive deterioration; no labels or text; no watermark; no weapons; no clothing; no extra creatures; no perspective view; no dramatic pose; no bright glow.
```

Output: `rework_body_five_stage_orthographic_concept.png` (1774×887).

## Material reference prompt

```text
Use case: stylized-concept
Asset type: production material reference board for five 256x256 Minecraft entity UV textures
Primary request: Create a front-facing five-stage decayed-flesh material progression board, arranged as five equal vertical swatch panels from least damaged to most damaged. This is a texture and color reference, not a creature illustration.
Subject/material progression: Panel 1 gray-white corpse skin with subtle pores, bruised-purple joint tissue, sparse dried brown-red blood. Panel 2 torn pale skin exposing dark-red fascia and stretched tendons. Panel 3 necrotic black patches mixed with dirty protruding bone and dehydrated muscle. Panel 4 peeled facial-type tissue, dark cavities, dirty bone-white tooth material, with only a few very dim dark-red and warm bone-white internal luminous fissures. Panel 5 fully degraded layered flesh, exposed spine-like dirty bone texture, vertical tears, layered gum and tooth material, with sparse low-amplitude internal glow in dark-red and bone-white cracks.
Style/medium: grounded photorealistic practical-effects horror material photography, high-frequency texture readable after downscaling to 256 pixels, matte surfaces, no glossy plastic look.
Composition/framing: square image, five equal non-overlapping vertical panels, perfectly front-on and evenly lit, material fills every panel edge-to-edge; no perspective, no objects, no limbs, no face, no background beyond the material surfaces.
Lighting/mood: neutral soft diffuse reference lighting; stage 4-5 luminous areas remain dim and localized, never neon, never bloom-heavy.
Color palette: gray-white, cold bruised purple, dried brown-red, deep dark-red, charcoal necrosis, dirty ivory bone; avoid saturated crimson and bright orange.
Constraints: clear progressive difference across all five panels; no labels, no text, no borders, no watermark, no scene, no cast shadows, no gore splatter on a background, no bright emission.
```

Output: `rework_body_material_reference.png` (1254×1254).

## Runtime UV contract

- Five base maps are RGB/opaque 256×256 PNGs.
- Stage 4 and 5 emissive maps are sparse RGBA 256×256 PNGs with a transparent background.
- The model has a 128×128 virtual UV canvas, so each model texel maps to 2×2 resource pixels.
- `rework_body_uv_guide.png` records the semantic island envelopes used by the finishing script.
