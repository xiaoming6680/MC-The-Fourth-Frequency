#!/usr/bin/env python3
"""Build the Watcher's exact 128->256 UV guide, opaque base, and sparse eye mask."""

from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path
import random

from PIL import Image, ImageDraw, ImageFont


ROOT = Path(__file__).resolve().parents[1]
ASSET_DIR = ROOT / "src/main/resources/assets/thefourthfrequency/textures/entity"
ART_DIR = ROOT / "docs/art/watcher"
SCALE = 2
SIZE = 256
SEED = 0x57415443484552

# Cuboids are lit by the texture, not by the world: without a per-face value split every box in
# a black cave collapses into one flat silhouette and all the modeled anatomy is wasted.
FACE_SHADE = {
    "up": 1.42,
    "north": 1.00,
    "west": 0.74,
    "east": 0.74,
    "south": 0.58,
    "down": 0.40,
}
# Only the four upright faces carry a top-to-bottom falloff; up/down span depth, not height.
UPRIGHT_FACES = ("north", "south", "west", "east")
GRADIENT_TOP = 1.15
GRADIENT_BOTTOM = 0.62
EDGE_AO = 0.55

PALETTE = {
    "skin": (46, 36, 30),
    "skin_blood": (50, 37, 30),
    "skin_fascia": (58, 47, 40),
    "fascia": (96, 88, 72),
    "fascia_blood": (84, 71, 58),
    "bone": (88, 80, 66),
    "socket": (14, 11, 10),
    "sclera": (120, 114, 98),
    "iris": (92, 88, 72),
    "pupil": (2, 2, 2),
}

EMISSIVE_ISLANDS = {"sclera_a", "sclera_b", "iris_top", "iris_bottom", "iris_left", "iris_right"}
SCLERA_EMISSIVE = (232, 224, 194, 114)
IRIS_EMISSIVE = (240, 232, 198, 118)


@dataclass(frozen=True)
class Island:
    name: str
    u: float
    v: float
    width: float
    height: float
    depth: float
    material: str


# These texOffs and dimensions mirror WatcherModel.createBodyLayer exactly.
ISLANDS = [
    Island("torso_ribcage", 0, 0, 6.2, 6.4, 3.7, "skin_blood"),
    Island("torso_midriff", 20, 0, 5.0, 3.6, 3.1, "skin"),
    Island("torso_waist", 37, 0, 4.2, 4.0, 2.7, "skin"),
    Island("neck", 51, 0, 2.2, 5.8, 2.2, "skin_fascia"),
    Island("head", 60, 0, 5.2, 6.6, 4.4, "skin"),
    Island("sclera_a", 80, 0, 3.90, 3.90, 0.60, "sclera"),
    Island("sclera_b", 90, 0, 3.30, 3.30, 0.48, "sclera"),
    Island("iris_top", 98, 0, 2.10, 0.55, 0.38, "iris"),
    Island("iris_bottom", 98, 1, 2.10, 0.55, 0.38, "iris"),
    Island("iris_left", 105, 0, 0.55, 1.70, 0.38, "iris"),
    Island("iris_right", 105, 3, 0.55, 1.70, 0.38, "iris"),
    Island("pupil", 108, 0, 1.74, 1.74, 0.24, "pupil"),
    Island("brow", 112, 0, 5.4, 1.00, 0.65, "skin"),
    Island("left_cheek", 112, 2, 0.65, 3.90, 0.5, "skin"),
    Island("right_cheek", 115, 2, 0.65, 3.90, 0.5, "skin"),
    Island("socket", 118, 2, 4.30, 3.90, 0.35, "socket"),
    Island("left_upper_arm", 0, 24, 1.5, 12.5, 1.6, "skin"),
    Island("left_forearm", 7, 24, 1.26, 13.0, 1.36, "skin_blood"),
    Island("left_hand", 13, 24, 1.44, 3.55, 1.52, "skin"),
    Island("left_finger_1", 19, 24, 0.26, 2.50, 0.34, "skin"),
    Island("left_finger_2", 21, 24, 0.26, 2.90, 0.34, "skin"),
    Island("left_finger_3", 23, 24, 0.26, 2.30, 0.34, "skin"),
    Island("left_finger_4", 25, 24, 0.26, 2.10, 0.34, "skin"),
    Island("right_upper_arm", 28, 24, 1.5, 12.5, 1.6, "skin_blood"),
    Island("right_forearm", 35, 24, 1.26, 13.0, 1.36, "skin"),
    Island("right_hand", 41, 24, 1.44, 3.55, 1.52, "skin"),
    Island("right_finger_1", 47, 24, 0.26, 2.50, 0.34, "skin"),
    Island("right_finger_2", 49, 24, 0.26, 2.90, 0.34, "skin"),
    Island("right_finger_3", 51, 24, 0.26, 2.30, 0.34, "skin"),
    Island("right_finger_4", 53, 24, 0.26, 2.10, 0.34, "skin"),
    Island("left_thigh", 0, 46, 1.56, 9.5, 1.68, "skin"),
    Island("left_lower_leg", 7, 46, 1.34, 10.5, 1.44, "skin_blood"),
    Island("left_foot", 13, 46, 1.50, 0.8, 2.15, "skin"),
    Island("right_thigh", 21, 46, 1.56, 9.5, 1.68, "skin_blood"),
    Island("right_lower_leg", 28, 46, 1.34, 10.5, 1.44, "skin"),
    Island("right_foot", 34, 46, 1.50, 0.8, 2.15, "skin"),
    Island("left_scapula", 0, 66, 2.4, 5.4, 0.52, "fascia"),
    Island("right_scapula", 6, 66, 2.4, 5.4, 0.52, "fascia"),
    Island("chest_fascia", 48, 66, 4.7, 5.4, 0.34, "fascia_blood"),
    Island("left_acromion", 60, 66, 1.15, 1.15, 2.0, "bone"),
    Island("right_acromion", 67, 66, 1.15, 1.15, 2.0, "bone"),
    Island("pelvis", 75, 66, 4.8, 4.0, 2.9, "skin"),
]

for index in range(9):
    vertebra_width = 1.2 if index in (2, 3) else 0.92
    ISLANDS.append(Island(
        f"vertebra_{index}", 22 + (index % 3) * 5, 66 + (index // 3) * 4,
        vertebra_width, 0.86, 0.82, "fascia",
    ))


def px(value: float) -> int:
    return int(round(value * SCALE))


def face_rects(island: Island) -> dict[str, tuple[int, int, int, int]]:
    """Minecraft cuboid UV cross; rectangles use an exclusive max bound."""
    u, v = island.u, island.v
    w, h, d = island.width, island.height, island.depth
    raw = {
        "up": (u + d, v, u + d + w, v + d),
        "down": (u + d + w, v, u + d + 2 * w, v + d),
        "west": (u, v + d, u + d, v + d + h),
        "north": (u + d, v + d, u + d + w, v + d + h),
        "east": (u + d + w, v + d, u + 2 * d + w, v + d + h),
        "south": (u + 2 * d + w, v + d, u + 2 * d + 2 * w, v + d + h),
    }
    converted: dict[str, tuple[int, int, int, int]] = {}
    for name, (x0, y0, x1, y1) in raw.items():
        left, top = max(0, px(x0)), max(0, px(y0))
        right, bottom = min(SIZE, max(left + 1, px(x1))), min(SIZE, max(top + 1, px(y1)))
        converted[name] = (left, top, right, bottom)
    return converted


def clamp_channel(value: int) -> int:
    return max(0, min(255, value))


def noisy_color(base: tuple[int, int, int], rng: random.Random, spread: int) -> tuple[int, int, int, int]:
    delta = rng.randint(-spread, spread)
    return tuple(clamp_channel(channel + delta) for channel in base) + (255,)


def paint_material(tile: Image.Image, material: str, rng: random.Random, face: str) -> None:
    """Paint one face at full value in local tile coordinates; relief is applied afterwards."""
    draw = ImageDraw.Draw(tile)
    width, height = tile.size
    base = PALETTE[material]
    spread = 2 if material == "pupil" else 8 if material in ("fascia", "sclera", "iris", "bone") else 6
    for y in range(height):
        for x in range(width):
            tile.putpixel((x, y), noisy_color(base, rng, spread))

    if material.startswith("skin"):
        crack = (10, 7, 7, 255)
        ember = (69, 42, 29, 255)
        for step in range(max(1, width * height // 26)):
            sx = rng.randrange(0, width)
            sy = rng.randrange(0, height)
            length = rng.randint(1, max(2, min(7, width + height)))
            ex = max(0, min(width - 1, sx + rng.choice((-1, 0, 1)) * length))
            ey = max(0, min(height - 1, sy + rng.choice((-1, 1)) * max(1, length // 2)))
            draw.line((sx, sy, ex, ey), fill=crack, width=1)
            if step % 4 == 0:
                draw.point((sx, sy), fill=ember)
        if "blood" in material and height >= 4:
            bx = max(0, width // 2 - 1)
            draw.line((bx, 1, min(width - 1, bx + 1), height - 1), fill=(48, 22, 17, 255), width=1)
    elif material.startswith("fascia") or material == "bone":
        # A single taut highlight strip along the top edge instead of a hatch that fills the whole
        # face: at this island size a full grid is indistinguishable from noise.
        if height >= 3:
            draw.line((0, 1, width - 1, 1), fill=(146, 136, 112, 255))
        draw.rectangle((0, 0, width - 1, height - 1), outline=(44, 36, 30, 255))
        if "blood" in material:
            draw.line((width // 3, 0, width // 2, height - 1), fill=(51, 23, 17, 255), width=1)
    elif material == "socket":
        # Darkest under the brow, opening up toward the bottom of the orbit.
        for y in range(height):
            factor = 0.45 + 0.55 * (y / max(1, height - 1))
            row = tuple(clamp_channel(int(channel * factor)) for channel in base)
            draw.line((0, y, width - 1, y), fill=row + (255,))
    elif material == "sclera":
        vein = (78, 62, 52, 255)
        for offset in range(1, max(2, width), 3):
            draw.line((offset, 0, max(0, offset - 2), height - 1), fill=vein, width=1)
        draw.rectangle((0, 0, width - 1, height - 1), outline=(58, 46, 39, 255))
    elif material == "iris":
        draw.rectangle((0, 0, width - 1, height - 1), outline=(52, 48, 39, 255))
    elif material == "pupil":
        draw.rectangle((0, 0, width - 1, height - 1), fill=(1, 1, 1, 255))
        # An off-center catchlight is what makes a pupil read as aimed at the viewer rather than
        # as a black sticker. Front face only; a highlight on the back of an eyeball is nonsense.
        # Kept dim: on a pupil only a few texels wide a bright dot turns the whole pupil white.
        if face == "north" and width >= 4 and height >= 4:
            draw.point((width // 3, height // 3), fill=(110, 104, 92, 255))


def apply_relief(tile: Image.Image, face: str) -> None:
    """Directional face value, vertical falloff, and a one-texel ambient-occlusion border."""
    width, height = tile.size
    shade = FACE_SHADE[face]
    vertical = face in UPRIGHT_FACES
    bordered = width > 2 and height > 2
    for y in range(height):
        gradient = 1.0
        if vertical and height > 1:
            gradient = GRADIENT_TOP + (GRADIENT_BOTTOM - GRADIENT_TOP) * (y / (height - 1))
        for x in range(width):
            factor = shade * gradient
            if bordered and (x == 0 or y == 0 or x == width - 1 or y == height - 1):
                factor *= EDGE_AO
            red, green, blue, _ = tile.getpixel((x, y))
            tile.putpixel((x, y), (
                clamp_channel(int(red * factor)),
                clamp_channel(int(green * factor)),
                clamp_channel(int(blue * factor)),
                255,
            ))


def build_base() -> Image.Image:
    rng = random.Random(SEED)
    image = Image.new("RGBA", (SIZE, SIZE), (21, 16, 14, 255))
    # Opaque unused UV space is deliberately quiet and matte.
    for y in range(SIZE):
        for x in range(SIZE):
            noise = ((x * 17 + y * 31 + (x ^ y) * 7) % 7) - 3
            image.putpixel((x, y), (21 + noise, 16 + noise, 14 + noise, 255))
    for island in ISLANDS:
        island_rng = random.Random(SEED ^ sum(ord(char) << (index % 8) for index, char in enumerate(island.name)))
        for face, rect in face_rects(island).items():
            x0, y0, x1, y1 = rect
            if x1 <= x0 or y1 <= y0:
                continue
            tile = Image.new("RGBA", (x1 - x0, y1 - y0), (0, 0, 0, 255))
            paint_material(tile, island.material, island_rng, face)
            apply_relief(tile, face)
            image.paste(tile, (x0, y0))
    return image


def build_emissive() -> tuple[Image.Image, list[tuple[int, int, int, int]]]:
    """Front faces only. A glowing rectangle outline on every face advertises the cuboid."""
    image = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    draw = ImageDraw.Draw(image)
    allowed: list[tuple[int, int, int, int]] = []
    for island in ISLANDS:
        if island.name not in EMISSIVE_ISLANDS:
            continue
        rect = face_rects(island)["north"]
        allowed.append(rect)
        x0, y0, x1, y1 = rect
        if x1 <= x0 or y1 <= y0:
            continue
        if island.name.startswith("sclera"):
            # A shallow U along the lower rim: light pooling in the bottom of the eye, never a box.
            draw.line((x0, y1 - 1, x1 - 1, y1 - 1), fill=SCLERA_EMISSIVE)
            if y1 - y0 >= 3:
                draw.point((x0, y1 - 2), fill=SCLERA_EMISSIVE)
                draw.point((x1 - 1, y1 - 2), fill=SCLERA_EMISSIVE)
        else:
            draw.rectangle((x0, y0, x1 - 1, y1 - 1), fill=IRIS_EMISSIVE)
    return image, allowed


def build_guide() -> Image.Image:
    guide = Image.new("RGBA", (SIZE, SIZE), (12, 13, 15, 255))
    draw = ImageDraw.Draw(guide)
    font = ImageFont.load_default()
    colors = {
        "skin": (102, 60, 42, 255),
        "skin_blood": (123, 46, 36, 255),
        "skin_fascia": (134, 103, 73, 255),
        "fascia": (195, 184, 147, 255),
        "fascia_blood": (157, 112, 82, 255),
        "bone": (206, 194, 160, 255),
        "socket": (46, 44, 52, 255),
        "sclera": (220, 213, 179, 255),
        "iris": (183, 177, 139, 255),
        "pupil": (57, 59, 64, 255),
    }
    for grid in range(0, SIZE, 16):
        draw.line((grid, 0, grid, SIZE - 1), fill=(24, 27, 31, 255))
        draw.line((0, grid, SIZE - 1, grid), fill=(24, 27, 31, 255))
    for index, island in enumerate(ISLANDS, start=1):
        rects = face_rects(island)
        for rect in rects.values():
            x0, y0, x1, y1 = rect
            draw.rectangle((x0, y0, x1 - 1, y1 - 1), outline=colors[island.material], width=1)
        bounds = (
            min(rect[0] for rect in rects.values()), min(rect[1] for rect in rects.values()),
            max(rect[2] for rect in rects.values()), max(rect[3] for rect in rects.values()),
        )
        if bounds[2] - bounds[0] >= 8 and bounds[3] - bounds[1] >= 5:
            draw.text((bounds[0] + 1, bounds[1] + 1), str(index), fill=(235, 235, 235, 255), font=font)
        column = (index - 1) // 17
        row = (index - 1) % 17
        draw.text((2 + column * 84, 150 + row * 6), f"{index:02d} {island.name[:10]}",
                  fill=colors[island.material], font=font)
    return guide


def assert_islands_disjoint() -> None:
    """A silent UV overlap would paint one body part with another's material.

    Faces thinner than a texel (0.38 depth at 2x density) unavoidably share a column with their
    own neighbouring face, so only cross-island collisions are errors; a same-island bleed just
    swaps one face-shade value for another within a single material.
    """
    seen: dict[tuple[int, int], str] = {}
    for island in ISLANDS:
        for x0, y0, x1, y1 in face_rects(island).values():
            for y in range(y0, y1):
                for x in range(x0, x1):
                    owner = seen.get((x, y))
                    assert owner is None or owner == island.name, (
                        f"UV overlap at {(x, y)}: {island.name} collides with {owner}"
                    )
                    seen[(x, y)] = island.name


def validate(base: Image.Image, emissive: Image.Image,
             allowed: list[tuple[int, int, int, int]]) -> tuple[int, int]:
    assert base.size == (SIZE, SIZE) and emissive.size == (SIZE, SIZE)
    assert all(base.getpixel((x, y))[3] == 255 for y in range(SIZE) for x in range(SIZE)), (
        "base texture must be fully opaque"
    )
    nontransparent = 0
    maximum_alpha = 0
    for y in range(SIZE):
        for x in range(SIZE):
            alpha = emissive.getpixel((x, y))[3]
            if alpha == 0:
                continue
            nontransparent += 1
            maximum_alpha = max(maximum_alpha, alpha)
            assert any(x0 <= x < x1 and y0 <= y < y1 for x0, y0, x1, y1 in allowed), (
                f"emissive pixel outside eye UV at {(x, y)}"
            )
            # Mirrors the frozen window in ResourceContractTest.
            assert 160 <= x < 240 and y < 16, f"emissive pixel outside the frozen window at {(x, y)}"
    assert nontransparent > 0
    assert nontransparent <= int(SIZE * SIZE * 0.08)
    assert 112 <= maximum_alpha <= 120
    assert base.tobytes() != emissive.tobytes()
    return nontransparent, maximum_alpha


def main() -> None:
    ASSET_DIR.mkdir(parents=True, exist_ok=True)
    ART_DIR.mkdir(parents=True, exist_ok=True)
    assert_islands_disjoint()
    base = build_base()
    emissive, allowed = build_emissive()
    guide = build_guide()
    nontransparent, maximum_alpha = validate(base, emissive, allowed)
    base.save(ASSET_DIR / "watcher.png", optimize=True)
    emissive.save(ASSET_DIR / "watcher_emissive.png", optimize=True)
    guide.save(ART_DIR / "watcher_uv_template.png", optimize=True)
    print(f"watcher.png={base.size[0]}x{base.size[1]} opaque=true")
    print(f"watcher_emissive.png={emissive.size[0]}x{emissive.size[1]} "
          f"nontransparent={nontransparent} max_alpha={maximum_alpha}")
    print(f"uv_template={ART_DIR / 'watcher_uv_template.png'}")


if __name__ == "__main__":
    main()
