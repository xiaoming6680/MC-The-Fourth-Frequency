#!/usr/bin/env python3
"""Build the Watcher's exact 128->256 UV guide, opaque base, and sparse eye mask."""

from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path
import math
import random

from PIL import Image, ImageDraw, ImageFont


ROOT = Path(__file__).resolve().parents[1]
ASSET_DIR = ROOT / "src/main/resources/assets/thefourthfrequency/textures/entity"
ART_DIR = ROOT / "docs/art/watcher"
SCALE = 2
SIZE = 256
SEED = 0x57415443484552


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
    Island("torso", 0, 0, 6.0, 14.0, 3.4, "skin_blood"),
    Island("pelvis", 22, 0, 4.6, 4.0, 3.2, "skin"),
    Island("neck", 42, 0, 2.2, 5.0, 2.2, "skin_fascia"),
    Island("head", 56, 0, 6.5, 7.4, 3.8, "skin"),
    Island("sclera_a", 80, 0, 5.2, 3.8, 1.55, "sclera"),
    Island("sclera_b", 98, 0, 4.4, 4.6, 1.28, "sclera"),
    Island("iris", 112, 0, 3.5, 3.5, 0.34, "iris"),
    Island("pupil", 122, 0, 1.56, 2.04, 0.28, "pupil"),
    Island("left_upper_arm", 0, 24, 1.5, 12.5, 1.6, "skin"),
    Island("left_forearm", 9, 24, 1.26, 13.0, 1.36, "skin_blood"),
    Island("left_hand", 18, 24, 1.44, 3.55, 1.52, "skin"),
    Island("left_finger_1", 28, 24, 0.28, 1.75, 0.34, "skin"),
    Island("left_finger_2", 31, 24, 0.28, 1.95, 0.34, "skin"),
    Island("left_finger_3", 34, 24, 0.28, 1.65, 0.34, "skin"),
    Island("right_upper_arm", 44, 24, 1.5, 12.5, 1.6, "skin_blood"),
    Island("right_forearm", 53, 24, 1.26, 13.0, 1.36, "skin"),
    Island("right_hand", 62, 24, 1.44, 3.55, 1.52, "skin"),
    Island("right_finger_1", 72, 24, 0.28, 1.75, 0.34, "skin"),
    Island("right_finger_2", 75, 24, 0.28, 1.95, 0.34, "skin"),
    Island("right_finger_3", 78, 24, 0.28, 1.65, 0.34, "skin"),
    Island("left_thigh", 0, 46, 1.56, 9.5, 1.68, "skin"),
    Island("left_lower_leg", 9, 46, 1.34, 10.5, 1.44, "skin_blood"),
    Island("left_foot", 18, 46, 1.64, 0.8, 2.75, "skin"),
    Island("right_thigh", 32, 46, 1.56, 9.5, 1.68, "skin_blood"),
    Island("right_lower_leg", 41, 46, 1.34, 10.5, 1.44, "skin"),
    Island("right_foot", 50, 46, 1.64, 0.8, 2.75, "skin"),
    Island("left_scapula", 0, 66, 2.4, 6.4, 0.52, "fascia"),
    Island("right_scapula", 10, 66, 2.4, 6.4, 0.52, "fascia"),
    Island("chest_fascia", 48, 66, 4.7, 6.6, 0.34, "fascia_blood"),
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


def fill_material(image: Image.Image, rect: tuple[int, int, int, int], material: str,
                  rng: random.Random, face: str) -> None:
    draw = ImageDraw.Draw(image)
    x0, y0, x1, y1 = rect
    if x1 <= x0 or y1 <= y0:
        return
    palette = {
        "skin": (31, 23, 20),
        "skin_blood": (35, 24, 20),
        "skin_fascia": (42, 33, 28),
        "fascia": (121, 111, 91),
        "fascia_blood": (105, 89, 73),
        "sclera": (151, 145, 124),
        "iris": (111, 107, 88),
        "pupil": (2, 2, 2),
    }
    base = palette[material]
    spread = 2 if material == "pupil" else 8 if material in ("fascia", "sclera", "iris") else 6
    for y in range(y0, y1):
        for x in range(x0, x1):
            image.putpixel((x, y), noisy_color(base, rng, spread))

    width, height = x1 - x0, y1 - y0
    if material.startswith("skin"):
        crack = (10, 7, 7, 255)
        ember = (69, 42, 29, 255)
        for step in range(max(1, width * height // 26)):
            sx = rng.randrange(x0, x1)
            sy = rng.randrange(y0, y1)
            length = rng.randint(1, max(2, min(7, width + height)))
            ex = max(x0, min(x1 - 1, sx + rng.choice((-1, 0, 1)) * length))
            ey = max(y0, min(y1 - 1, sy + rng.choice((-1, 1)) * max(1, length // 2)))
            draw.line((sx, sy, ex, ey), fill=crack, width=1)
            if step % 4 == 0:
                draw.point((sx, sy), fill=ember)
        if "blood" in material and height >= 4:
            bx = x0 + max(0, width // 2 - 1)
            draw.line((bx, y0 + 1, min(x1 - 1, bx + 1), y1 - 1), fill=(48, 22, 17, 255), width=1)
    elif material.startswith("fascia"):
        dark = (65, 52, 43, 255)
        light = (151, 140, 115, 255)
        for offset in range(0, max(width, height), 3):
            draw.line((x0, min(y1 - 1, y0 + offset), min(x1 - 1, x0 + offset), y0), fill=light, width=1)
        draw.rectangle((x0, y0, x1 - 1, y1 - 1), outline=dark)
        if "blood" in material:
            draw.line((x0 + width // 3, y0, x0 + width // 2, y1 - 1), fill=(51, 23, 17, 255), width=1)
    elif material == "sclera":
        vein = (93, 73, 61, 255)
        for offset in range(2, max(3, width), 4):
            draw.line((x0 + offset, y0, max(x0, x0 + offset - 2), y1 - 1), fill=vein, width=1)
        draw.rectangle((x0, y0, x1 - 1, y1 - 1), outline=(72, 57, 48, 255))
    elif material == "iris":
        dark = (45, 40, 33, 255)
        center_x, center_y = (x0 + x1 - 1) // 2, (y0 + y1 - 1) // 2
        for angle in range(0, 360, 30):
            radius = max(width, height)
            ex = center_x + int(math.cos(math.radians(angle)) * radius)
            ey = center_y + int(math.sin(math.radians(angle)) * radius)
            draw.line((center_x, center_y, ex, ey), fill=dark, width=1)
        draw.rectangle((x0, y0, x1 - 1, y1 - 1), outline=(70, 64, 51, 255))
    elif material == "pupil":
        draw.rectangle((x0, y0, x1 - 1, y1 - 1), fill=(1, 1, 1, 255))

    # Slight face-to-face value shifts make seams legible without becoming bright.
    if face in ("up", "north") and material not in ("pupil",):
        overlay = Image.new("RGBA", (width, height), (18, 14, 10, 10))
        crop = image.crop(rect)
        crop.alpha_composite(overlay)
        image.paste(crop, (x0, y0))


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
            fill_material(image, rect, island.material, island_rng, face)
    return image


def build_emissive() -> tuple[Image.Image, list[tuple[int, int, int, int]]]:
    image = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    draw = ImageDraw.Draw(image)
    allowed: list[tuple[int, int, int, int]] = []
    for island in ISLANDS:
        if island.name not in {"sclera_a", "sclera_b", "iris"}:
            continue
        for face, rect in face_rects(island).items():
            allowed.append(rect)
            x0, y0, x1, y1 = rect
            if x1 <= x0 or y1 <= y0:
                continue
            if island.name.startswith("sclera"):
                edge = (226, 219, 190, 116)
                draw.rectangle((x0, y0, x1 - 1, y1 - 1), outline=edge, width=1)
                if x1 - x0 >= 6 and y1 - y0 >= 4 and face in ("north", "south"):
                    draw.arc((x0 + 1, y0 + 1, x1 - 2, y1 - 2), 20, 160,
                             fill=(240, 233, 202, 112), width=1)
                    draw.arc((x0 + 1, y0 + 1, x1 - 2, y1 - 2), 200, 340,
                             fill=(240, 233, 202, 112), width=1)
            else:
                fiber = (238, 230, 194, 120)
                cx, cy = (x0 + x1 - 1) // 2, (y0 + y1 - 1) // 2
                radius = max(x1 - x0, y1 - y0)
                for angle in range(0, 360, 36):
                    ex = max(x0, min(x1 - 1, cx + int(math.cos(math.radians(angle)) * radius)))
                    ey = max(y0, min(y1 - 1, cy + int(math.sin(math.radians(angle)) * radius)))
                    draw.line((cx, cy, ex, ey), fill=fiber, width=1)
                draw.rectangle((x0, y0, x1 - 1, y1 - 1), outline=(218, 211, 181, 112), width=1)
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
        column = (index - 1) // 13
        row = (index - 1) % 13
        draw.text((2 + column * 84, 170 + row * 6), f"{index:02d} {island.name[:10]}",
                  fill=colors[island.material], font=font)
    return guide


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
    assert nontransparent <= int(SIZE * SIZE * 0.08)
    assert 112 <= maximum_alpha <= 120
    assert base.tobytes() != emissive.tobytes()
    return nontransparent, maximum_alpha


def main() -> None:
    ASSET_DIR.mkdir(parents=True, exist_ok=True)
    ART_DIR.mkdir(parents=True, exist_ok=True)
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
