# HIM art pipeline

| File | Bytes | SHA-256 (first 16) |
|---|---|---|
| `him.png` | 3,106 | `F89229EF0F8647A2` |
| `him_emissive.png` | 112 | `79160F7089CC3C85` |

Regenerate from the repository root with a Python environment that has Pillow:

```bash
python tools/prepare_him_textures.py
```

## Provenance

The figure draws on the Minecraft urban legend usually written as HIM or Herobrine: an ordinary-looking player standing somewhere nobody should be, with blank white eyes that carry their own light. That *description* is what is reproduced here. Nothing is sampled from an existing file.

This matters because the obvious shortcut is not available. The classic look is the default player skin with the eyes painted out — but that default skin is Mojang's own asset, and the fan-made Herobrine variants of it are third-party work. Neither can be shipped in a mod. So the skin below is generated from scratch in the same deterministic way as every other entity in this project, and reads as the legend without being anybody's file.

## Layout

Standard 64×64 player layout, so vanilla's humanoid mesh maps onto it unchanged and `HimModel` needs no custom geometry at all. The silhouette being *exactly* Steve's is the point: inside the fifth of a second the figure is on screen, anything with its own proportions resolves as a custom mob rather than as a person.

| Part | UV | Materials |
|---|---|---|
| head | `(0, 0)` 8×8×8 | hair on the top two rows of the upright faces, skin below |
| body | `(16, 16)` 8×12×4 | shirt |
| arms | `(40, 16)` / `(32, 48)` 4×12×4 | sleeve down to row 8, skin below |
| legs | `(0, 16)` / `(16, 48)` 4×12×4 | trousers down to row 10, shoe below |

Relief follows the same rule as the rest of the mod — a per-face value split (up 1.16 down to down 0.76) plus a one-texel ×0.86 ambient-occlusion border. Cuboids here are lit by the texture as much as by the world, and a stationary figure at thirty blocks collapses into one flat blob without it.

The palette is deliberately muted. At the distance this thing is placed, saturated clothing reads as a player in a costume, and a costume invites a second look — which is exactly what must not happen, because a second look is going to find nothing there.

## Emissive contract

**Eight pixels.** Two 2×2 blank rectangles on the head's north face and nothing else; `validate()` fails the build if a lit pixel lands anywhere outside them.

They are empty on purpose. A pupil makes it a character looking at you; two lit slots with nothing in them make it something that has no eyes and is facing you anyway, which is the read the legend actually has.

Unlike the watcher's eye, this one is not gated on being looked at. That figure ramps its glow in over a two-second stare; this one is given a fifth of a second in total, and a reveal spread over that window would never finish arriving.
