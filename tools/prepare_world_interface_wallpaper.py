#!/usr/bin/env python3
"""Prepare the approved World Interface failure art as an exact 16:10 PNG resource."""

from __future__ import annotations

import argparse
import hashlib
from pathlib import Path

from PIL import Image


TARGET_SIZE = (2560, 1600)


def crop_to_ratio(image: Image.Image, ratio: float) -> Image.Image:
    current = image.width / image.height
    if abs(current - ratio) < 1.0e-9:
        return image
    if current > ratio:
        width = round(image.height * ratio)
        left = (image.width - width) // 2
        return image.crop((left, 0, left + width, image.height))
    height = round(image.width / ratio)
    top = (image.height - height) // 2
    return image.crop((0, top, image.width, top + height))


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()

    with Image.open(args.source) as source:
        prepared = crop_to_ratio(source.convert("RGB"), TARGET_SIZE[0] / TARGET_SIZE[1])
        prepared = prepared.resize(TARGET_SIZE, Image.Resampling.LANCZOS)
        args.output.parent.mkdir(parents=True, exist_ok=True)
        prepared.save(args.output, format="PNG", optimize=True)

    digest = hashlib.sha256(args.output.read_bytes()).hexdigest().upper()
    with Image.open(args.output) as result:
        if result.size != TARGET_SIZE or result.mode != "RGB":
            raise RuntimeError(f"Unexpected wallpaper output: mode={result.mode}, size={result.size}")
    print(f"{args.output}\t{TARGET_SIZE[0]}x{TARGET_SIZE[1]}\tsha256={digest}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
