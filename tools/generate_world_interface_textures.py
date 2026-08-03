#!/usr/bin/env python3
"""Generate the world-interface encounter's block textures.

The entity sheets used to be generated here too, from one uniform noise field with a single eye
drawn on it. They now come from tools/prepare_world_interface_textures.py, which paints one island
per part against the packed layout in tools/world_interface_uv.py. Nothing in this file may write
to textures/entity: it would overwrite those sheets with noise that the model's offsets no longer
point at, and ResourceContractTest would fail on the next run.
"""

from __future__ import annotations

import hashlib
from pathlib import Path

import random

from PIL import Image, ImageDraw


ROOT = Path(__file__).resolve().parents[1]
BLOCK = ROOT / "src/main/resources/assets/thefourthfrequency/textures/block"


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
    block_texture(BLOCK / "resonance_core.png", (20, 13, 29), (187, 72, 255), 71)
    block_texture(BLOCK / "stability_anchor_cage.png", (40, 31, 48), (244, 191, 82), 72)
    block_texture(BLOCK / "warp_gate_core.png", (11, 8, 23), (145, 54, 234), 73)
    block_texture(BLOCK / "world_interface_exit_portal.png", (18, 13, 29), (239, 197, 88), 74)
    for path in sorted([*BLOCK.glob("*core.png"), BLOCK / "stability_anchor_cage.png",
                        BLOCK / "world_interface_exit_portal.png"]):
        digest = hashlib.sha256(path.read_bytes()).hexdigest()[:16]
        print(f"{path.relative_to(ROOT)} {digest}")


if __name__ == "__main__":
    main()
