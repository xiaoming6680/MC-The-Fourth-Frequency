# World interface art pipeline

The final boss draws from ten sheets: a base, an emissive mask and an impact overlay per form, plus one blackened base for the failure ending. All of them are generated deterministically; nothing here is hand-painted or pasted in.

## Why this exists

The previous generator filled a single 128×128 canvas with uniform random noise, drew one eye on it, and gave every part of the model the same offsets into that noise. Three consequences followed, and all three were visible in game:

- **No part could be told from another.** Mass, plating, debris, ribs, roots and skulls all sampled the same purple static, so the silhouette was the only information the body carried.
- **One texel covered a whole block.** The canvas was 128 units wide and the PNG was 128 pixels wide, so density was 1×. The third form is scaled 16×, which put one texel on one block.
- **The glow was uncontrolled.** The emissive sheet scattered twenty-eight random specks across the whole canvas, so whichever parts happened to sample them lit up for no reason.

## Files

| File | Bytes | SHA-256 (first 32) |
|---|---|---|
| `world_interface_form_1.png` | 291,738 | `DCBA297AF33D29D85B3F9886BC409564` |
| `world_interface_form_1_emissive.png` | 2,400 | `EFAB421C3A2E36B30EB229976CB537C4` |
| `world_interface_form_1_hit.png` | 11,708 | `85F85BAECB00B340DA43F00B327F39EA` |
| `world_interface_form_2.png` | 290,112 | `D0CF53C5B07DDCF002C4225C83C43EF5` |
| `world_interface_form_2_emissive.png` | 2,404 | `35416B532EEC12D3428B39B6FD280340` |
| `world_interface_form_2_hit.png` | 12,322 | `ACD1B8450E75E00E79A5E8288FDE6B5A` |
| `world_interface_form_3.png` | 287,044 | `6F6A9E364D45660D6F29DA3E4701B7E9` |
| `world_interface_form_3_emissive.png` | 2,406 | `7B9C5EFFB089AECECFD3FA40F5A486CC` |
| `world_interface_form_3_hit.png` | 14,060 | `D5CE5FE02CFF930D34394EB798786651` |
| `world_interface_form_3_black.png` | 251,536 | `90A525456752785252B949326EBC2C4B` |
| `world_interface_uv_template.png` | 76,352 | `7FA4840672E1FCC226B28C59B2E3098F` |

The template is a numbered guide, not a runtime asset; the game never loads it.

## UV contract

`WorldInterfaceModel.createLayer()` declares a **256×128** canvas and the PNGs are **1024×512**, so density is **4×** — one texel to a quarter block at the third form's scale. The canvas is wide and short because that is the shape the packer actually fills: the islands shelf out to 89 rows, and a square canvas would have been two thirds dead sheet paid for in every PNG.

The model bakes several hundred cuboids from procedural generators, so a hand-written island table would go stale the moment a generator parameter moved. Instead `tools/world_interface_uv.py` **re-derives every cuboid the model will build** — mirroring the generators and their scatter hash exactly — buckets them by size, packs one island per bucket, and emits the model's offset table. Both the model and the painter read that one module.

- **69 islands, 59.4% canvas utilisation.**
- **All three forms share one layout.** The parts are the same materials at every stage; what changes between forms is the palette, and the three separate PNGs already carry that. Per-form islands would have cost three times the sheet to paint the same six materials.
- **Size bucketing.** A class whose members span 6× in size (mass runs from 5 units across to nearly 28) gets several islands and each cuboid picks one by the same scalar the generator bucketed on. Sharing one island across the class would leave the small members sampling a corner of the large members' material — which is the failure the old sheet had everywhere.
- **Buckets must be contiguous.** `world_interface_uv.py` refuses to run if a bucket band is empty, because the Java table would then be shorter than its bounds array and the model would index past the end.

Regenerate both halves together — an offset table that disagrees with the painted sheet points parts at rectangles nothing was drawn into:

```bash
python tools/world_interface_uv.py --emit-java && python tools/prepare_world_interface_textures.py
```

## Materials and relief

The reference is the vanilla ender dragon: near-black hide carrying almost no hue, soft blotching rather than linework, and one lit eye as the only saturated thing on the sheet.

An earlier pass went the other way — block seams on a grid, brushed metal on the plating, outlines around the bone, strong baked lighting — and at boss scale that turned the body into a technical drawing. Everything below is mottling; materials separate by value and by the *shape* of their blotching, never by drawn lines.

| Material | Used by | Treatment |
|---|---|---|
| `swallowed` | mass, debris | Hide blotching plus sparse single-texel mineral flecks, barely off the base value — all that is left of the terrain it ate |
| `plating` | plating, halo, shutters, weapon, core socket | Blotching only, a shade darker than the mass |
| `bone` | cranium, brow, maw, horns, vertebrae, ribs, jaws, teeth | Palest material on the sheet, one soft top highlight, no outline |
| `root` | roots | Blotch cells stretched three-to-one along the strand, so the fibre runs its length |
| `flesh` | tentacle links | Blotching with occasional short darker striations |
| `socket` | skull eye sockets | Darkest under the brow, opening toward the bottom of the orbit |
| `core` | pupils, slit, inner halo, tentacle nodes | Cool shell only on the base sheet; the emissive sheet is what makes it burn |
| `sclera` | eyeballs | Pale, faint diagonal veins |

Relief is applied on top of the material. The dragon bakes almost no directional value at all, but going that flat does not survive here — the dragon is a dozen large parts and this is several hundred small ones, which merge into one silhouette without some split. So the range is compressed hard rather than removed, and the AO border does most of the separating:

- **Directional parts** (mass, ribs, roots, skulls, eyes, jaws, weapon) get a per-face factor — up 1.16, north 1.00, west/east 0.90, south 0.82, down 0.74 — and a 1.05 → 0.92 top-to-bottom falloff on the four upright faces.
- **Isotropic parts** (plating, debris, halo, tentacle nodes) get a flat 0.94 instead. The model tumbles these on all three axes, and a baked "top is bright" is worse than no shading at all once the box is upside down.
- **Every face** gets a one-texel ×0.74 ambient-occlusion border. This is the cheapest thing that separates a hundred touching boxes into a hundred readable boxes, and for the isotropic parts it is the only relief they get.

## Emissive contract

Only these islands may carry glow, and `validate()` fails the build if a lit pixel lands anywhere else: `eye_{1,2,3}_pupil`, `eye_3_slit`, `socket`, `tendril_glow`, `ring_inner`.

- Each emissive sheet is **2,220 non-transparent pixels — 0.42%** of the canvas, confined to the packed island rows.
- **Front-facing parts glow on their north face only.** Parts seen from every angle (sockets, tentacle nodes, inner halo) glow on the four upright faces but never on up/down, because a box lit on all six sides just advertises that it is a box.
- Sockets get a shallow pool along the lower rim rather than a filled rectangle, so the skulls read as sockets with something behind them instead of cubes with lamps in them.
- The inner halo band is the only ring on an emissive island. At the third form the halo is large enough that lighting all of it would drown the core it exists to frame.

`WorldInterfaceRenderer` submits **only the bones that carry glow** — the core, the halo, the six skull sockets and the active tentacle nodes — rather than the whole model a second time. At 0.42% coverage, walking the third form's several hundred parts meant nearly every vertex went through a translucent pass to draw nothing and paid for the sort on the way.

The impact overlay is the exception: it has to reach plating and debris the glow never touches, so the damage flash still costs a full second submission. It lasts a few ticks.

## Palettes

The forms separate by value and by how much cold violet has crept into the grey — not by turning progressively more purple. The hide stays charcoal at every stage; escalation is carried by the core and by the emissive palette bands in `WorldInterfacePalette`, which is where a player actually reads phase from.

| Form | Reading | Hide value |
|---|---|---|
| 1 — Nascent | Still mostly the world it ate. Stone grey, bone not yet gone dark. | `(52, 50, 55)` |
| 2 — Grown | The grey has gone cold and the bone is going with it. | `(44, 41, 50)` |
| 3 — Terminal | Hide black enough that the core is the only thing on it with a colour. | `(34, 31, 40)` |
| Black | Failure ending: same geometry, all the light gone out of it. | `(15, 14, 17)` |

Impact overlays are magenta `(255, 42, 88)` on every form.
