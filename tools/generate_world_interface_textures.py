#!/usr/bin/env python3
"""Generate deterministic, original pixel textures for the world-interface encounter."""

from __future__ import annotations

import hashlib
import random
from pathlib import Path

from PIL import Image, ImageDraw


ROOT = Path(__file__).resolve().parents[1]
ENTITY = ROOT / "src/main/resources/assets/thefourthfrequency/textures/entity"
BLOCK = ROOT / "src/main/resources/assets/thefourthfrequency/textures/block"


def noise_texture(path: Path, seed: int, base: tuple[int, int, int], accent: tuple[int, int, int],
                  cracks: int, eye: tuple[int, int, int]) -> None:
    rng = random.Random(seed)
    image = Image.new("RGBA", (128, 128), (*base, 255))
    pixels = image.load()
    for y in range(128):
        for x in range(128):
            cell = ((x // 8) * 19 + (y // 8) * 31 + rng.randrange(7)) % 17
            grain = rng.randrange(-13, 14) + (5 if cell < 3 else 0)
            pixels[x, y] = tuple(max(0, min(255, channel + grain)) for channel in base) + (255,)
    draw = ImageDraw.Draw(image)
    for x in range(0, 128, 16):
        draw.line((x, 0, x, 127), fill=(*accent, 72), width=1)
    for y in range(0, 128, 16):
        draw.line((0, y, 127, y), fill=(*accent, 58), width=1)
    for _ in range(cracks):
        x, y = rng.randrange(128), rng.randrange(128)
        points = [(x, y)]
        for _ in range(rng.randrange(2, 6)):
            x = max(0, min(127, x + rng.randrange(-14, 15)))
            y = max(0, min(127, y + rng.randrange(3, 14)))
            points.append((x, y))
        draw.line(points, fill=(*accent, 210), width=rng.choice((1, 1, 2)))
    # The eye UV occupies the 72..96 by 0..19 region in the authored model.
    draw.ellipse((72, 0, 95, 19), fill=(18, 8, 25, 255), outline=(*eye, 255), width=2)
    draw.ellipse((79, 4, 88, 15), fill=(*eye, 255))
    draw.rectangle((83, 4, 85, 15), fill=(248, 232, 181, 255))
    path.parent.mkdir(parents=True, exist_ok=True)
    image.save(path, optimize=True)


def emissive(path: Path, color: tuple[int, int, int], seed: int) -> None:
    rng = random.Random(seed)
    image = Image.new("RGBA", (128, 128), (0, 0, 0, 0))
    draw = ImageDraw.Draw(image)
    draw.ellipse((72, 0, 95, 19), fill=(*color, 210), outline=(255, 245, 210, 245), width=2)
    draw.rectangle((82, 3, 86, 16), fill=(255, 250, 222, 255))
    for _ in range(28):
        x = rng.randrange(128)
        y = rng.randrange(128)
        if rng.random() < 0.55:
            draw.point((x, y), fill=(*color, rng.randrange(70, 180)))
    path.parent.mkdir(parents=True, exist_ok=True)
    image.save(path, optimize=True)


def hit_overlay(path: Path, color: tuple[int, int, int], seed: int, density: int) -> None:
    """Create an independent transparent impact layer without modifying the base texture."""
    rng = random.Random(seed)
    image = Image.new("RGBA", (128, 128), (0, 0, 0, 0))
    draw = ImageDraw.Draw(image)
    for _ in range(density):
        x, y = rng.randrange(128), rng.randrange(128)
        points = [(x, y)]
        for _ in range(rng.randrange(2, 5)):
            x = max(0, min(127, x + rng.randrange(-11, 12)))
            y = max(0, min(127, y + rng.randrange(-9, 13)))
            points.append((x, y))
        draw.line(points, fill=(*color, rng.randrange(150, 236)), width=rng.choice((1, 2, 2)))
    draw.ellipse((72, 0, 95, 19), fill=(*color, 176), outline=(255, 232, 242, 244), width=2)
    draw.rectangle((82, 2, 86, 17), fill=(255, 240, 245, 255))
    path.parent.mkdir(parents=True, exist_ok=True)
    image.save(path, optimize=True)


def block_texture(path: Path, base: tuple[int, int, int], glow: tuple[int, int, int], seed: int) -> None:
    rng = random.Random(seed)
    image = Image.new("RGBA", (16, 16), (*base, 255))
    draw = ImageDraw.Draw(image)
    draw.rectangle((0, 0, 15, 15), outline=tuple(max(0, c - 24) for c in base) + (255,))
    draw.rectangle((3, 3, 12, 12), outline=(*glow, 255))
    draw.rectangle((6, 2, 9, 13), fill=(*glow, 190))
    draw.rectangle((2, 6, 13, 9), fill=(*glow, 150))
    for _ in range(12):
        x, y = rng.randrange(1, 15), rng.randrange(1, 15)
        draw.point((x, y), fill=(*glow, rng.randrange(45, 140)))
    path.parent.mkdir(parents=True, exist_ok=True)
    image.save(path, optimize=True)


def main() -> None:
    palettes = [
        ((31, 24, 42), (112, 54, 164), (242, 188, 83), 13),
        ((25, 18, 37), (151, 42, 184), (233, 102, 255), 28),
        ((14, 10, 24), (90, 27, 137), (216, 64, 244), 46),
    ]
    for form, (base, accent, eye, cracks) in enumerate(palettes, start=1):
        noise_texture(ENTITY / f"world_interface_form_{form}.png", 9100 + form,
                      base, accent, cracks, eye)
        emissive(ENTITY / f"world_interface_form_{form}_emissive.png", eye, 9200 + form)
        hit_overlay(ENTITY / f"world_interface_form_{form}_hit.png", (255, 42, 88),
                    9300 + form, 18 + form * 8)
    noise_texture(ENTITY / "world_interface_form_3_black.png", 9991,
                  (2, 1, 5), (91, 0, 104), 64, (207, 0, 70))
    block_texture(BLOCK / "resonance_core.png", (20, 13, 29), (187, 72, 255), 71)
    block_texture(BLOCK / "stability_anchor_cage.png", (40, 31, 48), (244, 191, 82), 72)
    block_texture(BLOCK / "warp_gate_core.png", (11, 8, 23), (145, 54, 234), 73)
    block_texture(BLOCK / "world_interface_exit_portal.png", (18, 13, 29), (239, 197, 88), 74)
    for path in sorted([*ENTITY.glob("world_interface*.png"), *BLOCK.glob("*core.png"),
                        BLOCK / "stability_anchor_cage.png", BLOCK / "world_interface_exit_portal.png"]):
        digest = hashlib.sha256(path.read_bytes()).hexdigest()[:16]
        print(f"{path.relative_to(ROOT)} {digest}")


if __name__ == "__main__":
    main()
