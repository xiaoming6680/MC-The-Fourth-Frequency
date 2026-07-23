#!/usr/bin/env python3
"""Create deterministic Minecraft entity textures from approved high-resolution material masters."""

from __future__ import annotations

import argparse
from pathlib import Path

from PIL import Image, ImageEnhance


def crop_to_aspect(image: Image.Image, width: int, height: int) -> Image.Image:
    target = width / height
    current = image.width / image.height
    if current > target:
        cropped_width = round(image.height * target)
        left = (image.width - cropped_width) // 2
        return image.crop((left, 0, left + cropped_width, image.height))
    cropped_height = round(image.width / target)
    top = (image.height - cropped_height) // 2
    return image.crop((0, top, image.width, top + cropped_height))


def prepare(source: Path, destination: Path, size: tuple[int, int], colors: int) -> None:
    image = Image.open(source).convert("RGB")
    image = crop_to_aspect(image, *size)
    image = ImageEnhance.Contrast(image).enhance(1.08)
    image = image.resize(size, Image.Resampling.NEAREST)
    image = image.quantize(colors=colors, method=Image.Quantize.MEDIANCUT,
                           dither=Image.Dither.NONE).convert("RGBA")
    destination.parent.mkdir(parents=True, exist_ok=True)
    image.save(destination, optimize=True)
    print(f"{destination}\t{image.width}x{image.height}\t{destination.stat().st_size} bytes")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--rework-master", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()
    prepare(args.rework_master, args.output / "rework_body.png", (64, 32), 28)


if __name__ == "__main__":
    main()
