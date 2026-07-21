#!/usr/bin/env python3
"""Derive deterministic in-game anomaly art from the approved imagegen masters."""

from __future__ import annotations

import argparse
from pathlib import Path

from PIL import Image, ImageEnhance, ImageFilter, ImageOps


PALETTE = [(8, 4, 13), (18, 7, 27), (41, 12, 59), (73, 18, 98), (112, 31, 139)]


def cover(image: Image.Image, size: tuple[int, int]) -> Image.Image:
    ratio = max(size[0] / image.width, size[1] / image.height)
    resized = image.resize((round(image.width * ratio), round(image.height * ratio)), Image.Resampling.LANCZOS)
    left = (resized.width - size[0]) // 2
    top = (resized.height - size[1]) // 2
    return resized.crop((left, top, left + size[0], top + size[1]))


def quantized_eye(master: Image.Image, size: tuple[int, int]) -> Image.Image:
    gray = ImageOps.grayscale(master)
    bounds = gray.point(lambda value: 255 if value >= 36 else 0).getbbox()
    if bounds is not None:
        pad_x = max(2, round((bounds[2] - bounds[0]) * 0.03))
        pad_y = max(2, round((bounds[3] - bounds[1]) * 0.03))
        bounds = (max(0, bounds[0] - pad_x), max(0, bounds[1] - pad_y),
                  min(master.width, bounds[2] + pad_x), min(master.height, bounds[3] + pad_y))
        master = master.crop(bounds)
    eye = ImageOps.grayscale(cover(master, size))
    eye = ImageEnhance.Contrast(eye).enhance(1.22)
    return eye.quantize(colors=8, method=Image.Quantize.MEDIANCUT, dither=Image.Dither.NONE).convert("RGBA")


def detailed_eye(master: Image.Image, size: tuple[int, int]) -> Image.Image:
    alpha = master.getchannel("A")
    bounds = alpha.getbbox()
    if bounds is None:
        raise ValueError("eye master contains no visible alpha")
    eye = master.crop(bounds)
    padding = max(2, round(min(size) * 0.08))
    available = (max(1, size[0] - padding * 2), max(1, size[1] - padding * 2))
    eye = ImageOps.contain(eye, available, Image.Resampling.LANCZOS)
    eye_alpha = eye.getchannel("A")
    gray = ImageEnhance.Contrast(ImageOps.grayscale(eye)).enhance(1.12)
    gray = gray.filter(ImageFilter.UnsharpMask(radius=1.2, percent=125, threshold=3))
    detailed = Image.merge("RGBA", (gray, gray, gray, eye_alpha))
    texture = Image.new("RGBA", size, (0, 0, 0, 0))
    texture.alpha_composite(detailed, ((size[0] - detailed.width) // 2,
                                       (size[1] - detailed.height) // 2))
    return texture


def save_eye_assets(master: Image.Image, root: Path) -> None:
    gui = root / "textures" / "gui" / "anomaly"
    gui.mkdir(parents=True, exist_ok=True)

    detailed_eye(master, (128, 128)).save(gui / "eye_item.png", optimize=True)
    detailed_eye(master, (64, 64)).save(gui / "eye_window.png", optimize=True)


def save_hand_asset(master: Image.Image, root: Path) -> None:
    gui = root / "textures" / "gui" / "anomaly"
    gui.mkdir(parents=True, exist_ok=True)
    bbox = master.getchannel("A").getbbox()
    if bbox is None:
        raise ValueError("single-hand master contains no visible alpha")
    hand = master.crop(bbox)
    target_width = 512
    target_height = max(1, round(hand.height * target_width / hand.width))
    if target_height > 240:
        target_height = 240
        target_width = max(1, round(hand.width * target_height / hand.height))
    hand = hand.resize((target_width, target_height), Image.Resampling.LANCZOS)
    texture = Image.new("RGBA", (512, 256), (0, 0, 0, 0))
    texture.alpha_composite(hand, (0, (256 - target_height) // 2))
    texture.save(gui / "peripheral_hand.png", optimize=True)


def save_purple_assets(root: Path) -> None:
    gui = root / "textures" / "gui" / "anomaly"
    block = root / "textures" / "block"
    gui.mkdir(parents=True, exist_ok=True)
    block.mkdir(parents=True, exist_ok=True)
    trace = Image.new("RGB", (16, 16))
    title = Image.new("RGB", (256, 128))
    for image in (trace, title):
        pixels = image.load()
        for y in range(image.height):
            for x in range(image.width):
                seed = (x * 1103515245 + y * 12345 + (x ^ y) * 97) & 0x7FFFFFFF
                index = 0 if seed % 11 < 6 else 1 if seed % 7 < 4 else 2 if seed % 5 < 4 else 3
                if ((x // 5) ^ (y // 5)) % 13 == 0:
                    index = 4
                pixels[x, y] = PALETTE[index]
    trace.save(block / "purple_trace.png", optimize=True)
    title = title.filter(ImageFilter.GaussianBlur(0.35))
    title.save(gui / "title_corruption.png", optimize=True)


def save_missing_texture(root: Path) -> None:
    block = root / "textures" / "block"
    block.mkdir(parents=True, exist_ok=True)
    missing = Image.new("RGB", (32, 32))
    pixels = missing.load()
    for y in range(32):
        for x in range(32):
            checker = ((x // 8) + (y // 8)) & 1
            pixels[x, y] = (248, 0, 248) if checker == 0 else (12, 0, 12)
    missing.save(block / "missing_texture.png", optimize=True)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--eye-master", required=True, type=Path)
    parser.add_argument("--hands-master", required=True, type=Path)
    parser.add_argument("--resources", required=True, type=Path)
    args = parser.parse_args()
    save_eye_assets(Image.open(args.eye_master).convert("RGBA"), args.resources)
    save_hand_asset(Image.open(args.hands_master).convert("RGBA"), args.resources)
    save_purple_assets(args.resources)
    save_missing_texture(args.resources)


if __name__ == "__main__":
    main()
