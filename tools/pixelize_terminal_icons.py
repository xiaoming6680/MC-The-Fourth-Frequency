from __future__ import annotations

from pathlib import Path
import sys

from PIL import Image


ROOT = Path(__file__).resolve().parents[1]
WORK = ROOT / "tmp/imagegen"
OUTPUT = ROOT / "src/main/resources/assets/thefourthfrequency/textures/item"
SIZE = 32


def fitted(index: int) -> Image.Image:
    source = Image.open(WORK / f"terminal_slider_alpha_{index}.png").convert("RGBA")
    alpha = source.getchannel("A")
    box = alpha.point(lambda value: 255 if value >= 32 else 0).getbbox()
    if box is None:
        raise RuntimeError(f"generated source {index} has no opaque subject")
    left, top, right, bottom = box
    pad = max(2, round(max(right - left, bottom - top) * 0.025))
    box = (max(0, left - pad), max(0, top - pad), min(source.width, right + pad), min(source.height, bottom + pad))
    subject = source.crop(box)
    subject.thumbnail((30, 30), Image.Resampling.LANCZOS)
    canvas = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    canvas.alpha_composite(subject, ((SIZE - subject.width) // 2, (SIZE - subject.height) // 2))
    return canvas


def shared_palette(first: Image.Image, second: Image.Image) -> Image.Image:
    strip = Image.new("RGB", (SIZE * 2, SIZE), (0, 0, 0))
    strip.paste(first.convert("RGB"), (0, 0))
    strip.paste(second.convert("RGB"), (SIZE, 0))
    return strip.quantize(colors=24, method=Image.Quantize.MAXCOVERAGE, dither=Image.Dither.NONE)


def quantize(image: Image.Image, palette: Image.Image, unread: bool) -> Image.Image:
    alpha = image.getchannel("A").point(lambda value: 255 if value >= 92 else 0)
    rgb = image.convert("RGB").quantize(palette=palette, dither=Image.Dither.NONE).convert("RGB")
    result = Image.merge("RGBA", (*rgb.split(), alpha))
    pixels = result.load()
    if unread:
        # Fixed status-lamp pixels make the unread state survive every resource pack scale.
        for x, y, color in ((25, 7, (176, 14, 10, 255)), (26, 7, (255, 55, 34, 255)), (25, 8, (255, 88, 58, 255))):
            if pixels[x, y][3] != 0:
                pixels[x, y] = color
    return result


def main() -> int:
    OUTPUT.mkdir(parents=True, exist_ok=True)
    sources = [fitted(index) for index in range(6)]
    for stage in range(3):
        palette = shared_palette(sources[stage * 2], sources[stage * 2 + 1])
        for unread in range(2):
            index = stage * 2 + unread
            output = quantize(sources[index], palette, bool(unread))
            path = OUTPUT / f"old_terminal_{index}.png"
            output.save(path, format="PNG", optimize=True)
            colors = len(output.getcolors(maxcolors=SIZE * SIZE) or [])
            opaque = sum(1 for value in output.getchannel("A").getdata() if value)
            print(f"{path.relative_to(ROOT)}\t{output.size}\tRGBA\t{colors} colors\t{opaque} opaque")
    return 0


if __name__ == "__main__":
    sys.exit(main())
