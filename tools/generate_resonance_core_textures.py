"""Generate the pixel-art texture set for the End resonance altar core.

The source-of-truth is deliberately code-generated: every mark lands on the
native 16x16 grid and the three cube faces keep one restrained palette.
"""

from __future__ import annotations

from pathlib import Path
from random import Random

from PIL import Image, ImageColor, ImageDraw, ImageFont


ROOT = Path(__file__).resolve().parents[1]
TEXTURE_DIR = ROOT / "src/main/resources/assets/thefourthfrequency/textures/block"
ART_DIR = ROOT / "docs/art/end_altar"

SIZE = 16

INK = ImageColor.getrgb("#05040a")
OBSIDIAN_0 = ImageColor.getrgb("#0a0810")
OBSIDIAN_1 = ImageColor.getrgb("#100c18")
OBSIDIAN_2 = ImageColor.getrgb("#171020")
OBSIDIAN_3 = ImageColor.getrgb("#21152d")
PURPLE_DARK = ImageColor.getrgb("#351746")
PURPLE = ImageColor.getrgb("#642177")
MAGENTA = ImageColor.getrgb("#b53bc5")
MAGENTA_HI = ImageColor.getrgb("#e06cef")
CYAN = ImageColor.getrgb("#82dfe6")
CYAN_HI = ImageColor.getrgb("#d9ffff")
COPPER_DARK = ImageColor.getrgb("#4c211c")
COPPER = ImageColor.getrgb("#8a3e2f")
COPPER_HI = ImageColor.getrgb("#c36b4c")
BRASS_DARK = ImageColor.getrgb("#4b3b1d")
BRASS = ImageColor.getrgb("#856a2f")
BRASS_HI = ImageColor.getrgb("#c4a24e")
FRAME = ImageColor.getrgb("#2a2430")
FRAME_HI = ImageColor.getrgb("#49404d")


def texture(seed: int, *, fourfold: bool = False) -> Image.Image:
    """Create restrained, symmetric obsidian grain without smooth shading."""
    image = Image.new("RGB", (SIZE, SIZE), OBSIDIAN_0)
    pixels = image.load()
    rng = Random(seed)
    palette = [OBSIDIAN_0, OBSIDIAN_1, OBSIDIAN_1, OBSIDIAN_2, OBSIDIAN_2, OBSIDIAN_3]

    if fourfold:
        for y in range(8):
            for x in range(y, 8):
                color = rng.choice(palette)
                orbit = {
                    (x, y), (y, x),
                    (15 - x, y), (15 - y, x),
                    (x, 15 - y), (y, 15 - x),
                    (15 - x, 15 - y), (15 - y, 15 - x),
                }
                for px, py in orbit:
                    pixels[px, py] = color
    else:
        for y in range(SIZE):
            for x in range(8):
                color = rng.choice(palette)
                pixels[x, y] = color
                pixels[15 - x, y] = color
    return image


def mirror4(points: list[tuple[int, int]]) -> set[tuple[int, int]]:
    result: set[tuple[int, int]] = set()
    for x, y in points:
        result.update({(x, y), (15 - x, y), (x, 15 - y), (15 - x, 15 - y)})
    return result


def paint(image: Image.Image, points: set[tuple[int, int]] | list[tuple[int, int]], color: tuple[int, int, int]) -> None:
    pixels = image.load()
    for x, y in points:
        pixels[x, y] = color


def draw_top() -> Image.Image:
    image = texture(4404, fourfold=True)
    draw = ImageDraw.Draw(image)

    # Broken containment ring and aged corner clamps make the core read as a
    # machine embedded in the altar, without turning it into a bright portal.
    paint(image, mirror4([(1, 1), (2, 1), (3, 1), (1, 2), (1, 3)]), BRASS_DARK)
    paint(image, mirror4([(2, 1), (1, 2)]), BRASS)
    paint(image, mirror4([(2, 2)]), BRASS_HI)
    paint(image, mirror4([(3, 3)]), BRASS)

    paint(image, mirror4([(3, 5), (4, 5), (4, 6), (2, 7), (3, 7), (4, 7)]), PURPLE_DARK)
    paint(image, mirror4([(4, 6), (3, 7), (4, 7)]), PURPLE)
    paint(image, mirror4([(1, 7), (2, 7)]), MAGENTA)
    paint(image, mirror4([(3, 4), (4, 3)]), PURPLE_DARK)

    # Four cold sensors identify the socket as an active terminal reader.
    paint(image, mirror4([(4, 4)]), CYAN)
    paint(image, mirror4([(4, 5)]), CYAN_HI)

    # The terminal slot: raised stone frame, empty black channel, copper contacts.
    draw.rectangle((5, 3, 10, 12), fill=FRAME)
    draw.line((5, 3, 10, 3), fill=FRAME_HI)
    draw.line((5, 3, 5, 12), fill=FRAME_HI)
    draw.line((5, 12, 10, 12), fill=INK)
    draw.line((10, 3, 10, 12), fill=INK)
    draw.rectangle((6, 4, 9, 11), fill=INK)
    draw.line((7, 4, 8, 4), fill=OBSIDIAN_2)
    draw.line((7, 11, 8, 11), fill=OBSIDIAN_2)

    for y in (5, 6, 9, 10):
        image.putpixel((6, y), COPPER if y in (6, 10) else COPPER_HI)
        image.putpixel((9, y), COPPER if y in (6, 10) else COPPER_HI)
    image.putpixel((7, 7), COPPER_DARK)
    image.putpixel((8, 8), COPPER_DARK)

    # Resonance buses leave the socket on all four axes.
    paint(image, [(7, 0), (8, 0), (7, 1), (8, 1), (7, 2), (8, 2),
                  (7, 13), (8, 13), (7, 14), (8, 14), (7, 15), (8, 15),
                  (0, 7), (0, 8), (1, 7), (1, 8), (2, 7), (2, 8),
                  (13, 7), (13, 8), (14, 7), (14, 8), (15, 7), (15, 8)], PURPLE)
    paint(image, [(7, 0), (8, 15), (0, 8), (15, 7)], MAGENTA_HI)
    return image


def draw_side() -> Image.Image:
    image = texture(4405)
    draw = ImageDraw.Draw(image)

    # Heavy upper/lower seams visually lock the lit core into crying obsidian.
    draw.line((0, 0, 15, 0), fill=INK)
    draw.line((0, 1, 15, 1), fill=PURPLE_DARK)
    draw.line((0, 14, 15, 14), fill=PURPLE_DARK)
    draw.line((0, 15, 15, 15), fill=INK)
    for x in (1, 14):
        draw.line((x, 3, x, 12), fill=BRASS_DARK)
        image.putpixel((x, 4), BRASS)
        image.putpixel((x, 11), BRASS)

    # A contained vertical resonance conduit, not a second insertion opening.
    draw.rectangle((5, 2, 10, 13), fill=PURPLE_DARK)
    draw.line((5, 2, 10, 2), fill=PURPLE)
    draw.line((5, 13, 10, 13), fill=INK)
    draw.rectangle((6, 3, 9, 12), fill=INK)
    draw.line((7, 3, 8, 12), fill=PURPLE)
    for y in (4, 7, 10):
        image.putpixel((7, y), MAGENTA_HI)
        image.putpixel((8, y), MAGENTA)

    for y in (5, 10):
        draw.line((3, y, 5, y), fill=COPPER_DARK)
        draw.line((10, y, 12, y), fill=COPPER_DARK)
        image.putpixel((4, y), COPPER_HI)
        image.putpixel((11, y), COPPER_HI)

    draw.line((0, 7, 4, 7), fill=PURPLE_DARK)
    draw.line((11, 7, 15, 7), fill=PURPLE_DARK)
    image.putpixel((4, 7), CYAN)
    image.putpixel((11, 7), CYAN)
    image.putpixel((7, 7), CYAN_HI)
    image.putpixel((8, 7), CYAN)
    return image


def draw_bottom() -> Image.Image:
    image = texture(4406, fourfold=True)
    draw = ImageDraw.Draw(image)

    paint(image, mirror4([(2, 2), (3, 2), (2, 3)]), BRASS_DARK)
    paint(image, mirror4([(3, 3)]), BRASS)
    paint(image, mirror4([(4, 5), (5, 4), (5, 5)]), PURPLE_DARK)

    # A low-energy anchoring seal: visually related to the top, deliberately dimmer.
    draw.rectangle((5, 5, 10, 10), fill=PURPLE_DARK)
    draw.rectangle((6, 6, 9, 9), fill=INK)
    draw.rectangle((7, 7, 8, 8), fill=PURPLE)
    draw.line((7, 2, 8, 4), fill=PURPLE_DARK)
    draw.line((7, 11, 8, 13), fill=PURPLE_DARK)
    draw.line((2, 7, 4, 8), fill=PURPLE_DARK)
    draw.line((11, 7, 13, 8), fill=PURPLE_DARK)
    image.putpixel((7, 7), MAGENTA)
    image.putpixel((8, 8), MAGENTA)
    return image


def save_preview(textures: dict[str, Image.Image]) -> None:
    ART_DIR.mkdir(parents=True, exist_ok=True)
    scale = 16
    tile = SIZE * scale
    margin = 28
    label_height = 28
    width = margin * 4 + tile * 3
    height = margin * 2 + tile + label_height
    preview = Image.new("RGB", (width, height), "#08080c")
    draw = ImageDraw.Draw(preview)
    font = ImageFont.load_default()

    for index, (label, image) in enumerate(textures.items()):
        x = margin + index * (tile + margin)
        y = margin
        enlarged = image.resize((tile, tile), Image.Resampling.NEAREST)
        preview.paste(enlarged, (x, y))
        draw.rectangle((x - 1, y - 1, x + tile, y + tile), outline="#67546f", width=1)
        draw.text((x, y + tile + 8), label.upper(), fill="#d8cedf", font=font)

    preview.save(ART_DIR / "resonance_core_texture_preview.png", optimize=True)


def main() -> None:
    TEXTURE_DIR.mkdir(parents=True, exist_ok=True)
    ART_DIR.mkdir(parents=True, exist_ok=True)
    textures = {
        "top": draw_top(),
        "side": draw_side(),
        "bottom": draw_bottom(),
    }
    for name, image in textures.items():
        image.save(TEXTURE_DIR / f"resonance_core_{name}.png", optimize=True)
    save_preview(textures)


if __name__ == "__main__":
    main()
