#!/usr/bin/env python3
"""Build the figure's 64x64 skin and its eye-only emissive mask.

Original pixel art, generated the same way every other entity in this mod is. The urban legend it
draws on is "an ordinary player standing where nobody should be, with blank lit eyes", and that
description is what is reproduced here -- not any existing skin file. Mojang's default player skin
and the fan-made variants of it are both somebody else's work and neither is shipped or sampled.

Standard player layout, so the vanilla humanoid mesh in HimModel maps onto it unchanged.
"""

from __future__ import annotations

import hashlib
import random
from pathlib import Path

from PIL import Image, ImageDraw


ROOT = Path(__file__).resolve().parents[1]
ASSET_DIR = ROOT / "src/main/resources/assets/thefourthfrequency/textures/entity"
SIZE = 64
SEED = 0x48494D

# Muted rather than bright. At twenty to forty blocks the figure has to read as a person who is
# simply there, and saturated clothing reads as a player wearing a costume -- which invites a second
# look, when the whole point is that a second look finds nothing.
PALETTE = {
    "hair": (58, 40, 26),
    "skin": (183, 137, 106),
    "shirt": (36, 110, 108),
    "sleeve": (36, 110, 108),
    "trousers": (54, 58, 118),
    "shoe": (70, 60, 52),
}

# Per-face value split, as everywhere else in this mod: cuboids are lit by the texture, and without
# it a stationary figure at distance collapses into one flat blob.
FACE_SHADE = {"up": 1.16, "north": 1.00, "west": 0.90, "east": 0.90, "south": 0.84, "down": 0.76}
UPRIGHT_FACES = ("north", "south", "west", "east")
EDGE_AO = 0.86

# name -> (u, v, width, height, depth) in the standard player skin layout.
PARTS = {
    "head": (0, 0, 8, 8, 8),
    "body": (16, 16, 8, 12, 4),
    "right_arm": (40, 16, 4, 12, 4),
    "left_arm": (32, 48, 4, 12, 4),
    "right_leg": (0, 16, 4, 12, 4),
    "left_leg": (16, 48, 4, 12, 4),
}

# Which material each part is painted in, top band first where a part has two.
PART_MATERIALS = {
    "head": ("hair", "skin", 2),      # top two rows of the upright faces are hair
    "body": ("shirt", "shirt", 0),
    "right_arm": ("sleeve", "skin", 8),
    "left_arm": ("sleeve", "skin", 8),
    "right_leg": ("trousers", "shoe", 10),
    "left_leg": ("trousers", "shoe", 10),
}

EYE_COLOUR = (255, 255, 255)
EYE_EMISSIVE_ALPHA = 235


def clamp(value: int) -> int:
    return max(0, min(255, value))


def face_rects(u: int, v: int, w: int, h: int, d: int) -> dict[str, tuple[int, int, int, int]]:
    """Minecraft cuboid UV cross; rectangles use an exclusive max bound."""
    return {
        "up": (u + d, v, u + d + w, v + d),
        "down": (u + d + w, v, u + d + 2 * w, v + d),
        "west": (u, v + d, u + d, v + d + h),
        "north": (u + d, v + d, u + d + w, v + d + h),
        "east": (u + d + w, v + d, u + 2 * d + w, v + d + h),
        "south": (u + 2 * d + w, v + d, u + 2 * d + 2 * w, v + d + h),
    }


def paint_part(image: Image.Image, name: str, rng: random.Random) -> None:
    u, v, w, h, d = PARTS[name]
    upper, lower, split = PART_MATERIALS[name]
    for face, (x0, y0, x1, y1) in face_rects(u, v, w, h, d).items():
        shade = FACE_SHADE[face]
        upright = face in UPRIGHT_FACES
        for y in range(y0, y1):
            # `split` counts rows down from the top of the upright faces. Up/down caps take the
            # upper material outright: the top of a leg is trousers, the top of a head is hair.
            row = y - y0
            material = upper if (not upright or row < split) else lower
            base = PALETTE[material]
            for x in range(x0, x1):
                factor = shade
                if (x == x0 or x == x1 - 1 or y == y0 or y == y1 - 1) and (x1 - x0) > 2 and (y1 - y0) > 2:
                    factor *= EDGE_AO
                grain = rng.randint(-6, 6)
                image.putpixel((x, y), tuple(
                    clamp(int(channel * factor) + grain) for channel in base) + (255,))


def eye_rects() -> list[tuple[int, int, int, int]]:
    """The two blank eyes, on the head's front face only.

    Deliberately rectangles with nothing in them. A pupil makes it a character looking at you; two
    empty lit slots make it something that has no eyes and is facing you anyway, which is the read
    the legend actually has.
    """
    u, v, w, h, d = PARTS["head"]
    x0, y0, _, _ = face_rects(u, v, w, h, d)["north"]
    # Row 4 of an 8-tall face: level with where a player's eyes sit.
    top = y0 + 4
    return [(x0 + 1, top, x0 + 3, top + 2), (x0 + 5, top, x0 + 7, top + 2)]


def build_skin() -> Image.Image:
    rng = random.Random(SEED)
    image = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    for name in PARTS:
        paint_part(image, name, rng)
    draw = ImageDraw.Draw(image)
    for x0, y0, x1, y1 in eye_rects():
        draw.rectangle((x0, y0, x1 - 1, y1 - 1), fill=EYE_COLOUR + (255,))
    return image


def build_emissive() -> Image.Image:
    """Eyes only. Everything else is lit by the world like any other body standing in it."""
    image = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    draw = ImageDraw.Draw(image)
    for x0, y0, x1, y1 in eye_rects():
        draw.rectangle((x0, y0, x1 - 1, y1 - 1), fill=EYE_COLOUR + (EYE_EMISSIVE_ALPHA,))
    return image


def validate(skin: Image.Image, emissive: Image.Image) -> int:
    assert skin.size == (SIZE, SIZE) and emissive.size == (SIZE, SIZE)
    allowed = eye_rects()
    lit = 0
    emissive_pixels = emissive.load()
    for y in range(SIZE):
        for x in range(SIZE):
            if emissive_pixels[x, y][3] == 0:
                continue
            lit += 1
            assert any(x0 <= x < x1 and y0 <= y < y1 for x0, y0, x1, y1 in allowed), \
                f"emissive pixel outside the eyes at {(x, y)}"
    assert lit == sum((x1 - x0) * (y1 - y0) for x0, y0, x1, y1 in allowed), lit
    # Every modelled face must be opaque, or the figure renders with holes in it.
    skin_pixels = skin.load()
    for name in PARTS:
        for x0, y0, x1, y1 in face_rects(*PARTS[name]).values():
            for y in range(y0, y1):
                for x in range(x0, x1):
                    assert skin_pixels[x, y][3] == 255, f"{name} transparent at {(x, y)}"
    return lit


def save(image: Image.Image, path: Path) -> str:
    path.parent.mkdir(parents=True, exist_ok=True)
    image.save(path, optimize=True)
    digest = hashlib.sha256(path.read_bytes()).hexdigest()[:16]
    return f"{path.relative_to(ROOT)} {image.size[0]}x{image.size[1]} {path.stat().st_size:>7} {digest}"


def main() -> None:
    skin = build_skin()
    emissive = build_emissive()
    lit = validate(skin, emissive)
    print(save(skin, ASSET_DIR / "him.png"))
    print(save(emissive, ASSET_DIR / "him_emissive.png"))
    print(f"emissive lit pixels={lit} (eyes only)")


if __name__ == "__main__":
    main()
