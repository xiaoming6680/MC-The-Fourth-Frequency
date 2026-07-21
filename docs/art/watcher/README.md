# Watcher art pipeline

This directory contains project-bound reference art and the exact UV guide for the native Watcher model. The generated references are not loaded by the game and were not pasted into the runtime textures.

## Files

| File | Purpose | SHA-256 |
|---|---|---|
| `watcher_orthographic_concept.png` | Front, three-quarter, and back character reference | `8CEC18F131851968E331039E784F868976EDD914E63578A7159DDB37EE8D4EF7` |
| `watcher_material_reference.png` | Charred skin, fascia, old blood, and eye material board | `9F23736736DCD570C3F192B655184C261F76F0F23B81A438808407652AFA7DFA` |
| `watcher_uv_template.png` | Numbered 256×256 guide exported from the model's exact cuboid UV contract | `BEBF7FD052DF522902657385A936C5B2AA1F9DAF79BF084C305C1F9C23F8DE64` |

The two concept images were generated with the built-in image generation tool in `stylized-concept` mode. The runtime assets are generated deterministically by `tools/prepare_watcher_textures.py` from hand-selected colors, crack/fascia patterns, and the exact cuboid coordinates in `WatcherModel`; they do not sample or resize the generated reference images.

## Orthographic concept prompt

```text
Use case: stylized-concept
Asset type: Minecraft horror-mod character orthographic concept sheet
Primary request: create a production concept sheet showing the exact same Watcher creature in three full-body views: straight front, three-quarter front, and straight back.
Scene/backdrop: flat neutral warm-gray studio background with a faint ground line only.
Subject: one approximately 2.9-block-tall emaciated humanoid, extremely narrow shoulders, thin elongated neck, very long arms hanging naturally to the calves, slender legs, slight forward stoop. The back clearly shows a desiccated spine and shoulder-blade contours. The skin is charred black-brown, corpse-dry and deeply cracked, with only sparse bone-white fascia and restrained dark-brown old blood. The face has absolutely no nose, mouth, or ears. It contains exactly one huge three-dimensional eye occupying roughly 75–80 percent of the face width, with a spherical off-white eyeball, fibrous bone-white iris rim, and a pure black pupil.
Style/medium: high-detail dark survival-horror game character concept art, grounded anatomical design, modeler-friendly surfaces and silhouette, not photoreal gore.
Composition/framing: landscape concept sheet, three evenly spaced full-body figures at identical scale, head-to-toe visible, front / three-quarter / back, neutral hanging pose, no overlap, generous margins.
Lighting/mood: soft neutral studio light that reveals surface planes without making the eye glow brightly.
Color palette: charcoal black, burnt umber, desaturated bone white, old dried-blood brown.
Materials/textures: dry carbonized corpse skin, fine cracking, sparse taut fascia, desiccated vertebrae and scapula ridges.
Constraints: the three views must be recognizably the same single creature and preserve identical proportions; exactly two arms and two legs; one eye only; no eyelids closing; no nose, mouth, ears, extra limbs, tentacles, mandibles, mouthparts, antlers, clothing, props, text, labels, logos, border, or watermark. The eye is a modeled anatomical volume, never a flat floating icon. Keep emission extremely subtle and confined to the eye edge and iris fibers; pure-black pupil; no halo and no environmental light spill.
```

## Material-reference prompt

```text
Use case: stylized-concept
Asset type: horror-game character material reference board
Primary request: create a clean production material-reference board for the Watcher creature, focused on four close-up surface studies: charred corpse skin, dry cracked skin transitioning into sparse bone-white fascia, restrained dark-brown dried blood in creases, and the giant eye's off-white sclera edge with fine bone-white iris fibers around a pure-black pupil.
Scene/backdrop: neutral dark-gray studio board with four large evenly spaced square material studies and no text.
Subject: the exact materials of an emaciated blackened humanoid corpse creature; dry and desiccated, never wet or glossy.
Style/medium: high-detail realistic game-texture reference photography / painted material study, modeler- and texture-artist-friendly, front-lit enough to read cracks and fibers.
Composition/framing: 2-by-2 grid of macro square swatches, each nearly orthographic with minimal perspective, generous separation, no labels.
Lighting/mood: soft neutral raking light revealing relief, restrained contrast; the eye study has only a very faint inner bone-white luminance at the sclera edge and iris fibers.
Color palette: charcoal black, burned umber, desaturated bone white, very dark dried-blood brown, pure black pupil.
Materials/textures: matte carbonized skin with fine and medium cracks; taut fibrous fascia; crusted old blood only in narrow recesses; dry off-white eyeball edge and radial iris fibers.
Constraints: no full creature, no face, no nose, no mouth, no ears, no organs, no fresh red blood, no wet gore, no neon, no white bloom, no light halo, no environmental glow, no text, no labels, no logos, no watermark. Keep each material readable and suitable only as a color/detail reference; do not make it a UV layout or a seamless final game texture.
```

## Runtime UV contract

`WatcherModel.createBodyLayer()` uses a 128-unit virtual texture canvas. The two 256×256 runtime PNGs sample that layout at exactly 2× density. `tools/prepare_watcher_textures.py` mirrors every `texOffs` and cuboid dimension, exports the numbered guide, and fails if the emissive mask leaves the eye UV.

Current runtime audit:

- `watcher.png`: 256×256 RGBA, every pixel Alpha 255, SHA-256 `D10C8FF184683710D39B6F5E56537EB80C8F6CB910008F1A9318B05F4364F0E1`.
- `watcher_emissive.png`: 256×256 RGBA, 439 non-transparent pixels (0.67%), maximum Alpha 120, SHA-256 `06E5B4ED34E282F488FFE5624E9E4C32221309FD39CC9D7A0AFE1CC0F1652C0F`.
- Non-transparent emissive pixels are restricted to the two sclera cuboids and the iris cuboid. The pupil UV is transparent in the emissive image and near-black in the base image.
- The render layer applies a 0.94–1.00 alpha tint and a 0.96–1.00 bone-white strength pulse. This keeps the sparse mask legible against a black night sky without adding block light or expanding emission beyond the eye UV.

Regenerate from the repository root with a Python environment that contains Pillow:

```powershell
python tools/prepare_watcher_textures.py
```

## Frozen legacy eye assets

These assets remain separate and must not be replaced by this pipeline:

- `textures/gui/anomaly/eye_item.png`: `6C7B41872C6A6160D0454E820993F042FAF8C5D25A3FB5E23F3561D3E1F25B69`
- `textures/gui/anomaly/eye_window.png`: `823B30C28054AD5B10D585180FD0DA1A60F0084DFA352BCFD295D9CA84F19B36`
- `tools/assets/anomaly/eye_master.png`: `7FFBA46ADB2F9D7201B57753B2460F2595D27AB254A65CFD9D4EC5B7F3DEAE19`
