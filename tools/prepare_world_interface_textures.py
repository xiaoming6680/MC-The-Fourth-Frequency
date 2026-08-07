#!/usr/bin/env python3
"""Paint the world interface's base, emissive and impact sheets from the packed UV layout.

The old generator sprayed one uniform noise field across the whole sheet and drew a single eye on
it. Every one of the model's several hundred cuboids therefore sampled the same purple static, so
no part could be told from any other and a body forty blocks tall had no volume to it at all.

This replaces that with the pipeline the Watcher already uses: one island per part bucket, painted
per face, with a directional value split and a one-texel ambient-occlusion border. Parts the model
tumbles freely are painted isotropically -- a baked "top is bright" is worse than no shading at all
once the box is upside down.

Run tools/world_interface_uv.py --emit-java after changing the layout; the model's texOffs table
must be regenerated with it.
"""

from __future__ import annotations

import hashlib
import math
import random
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

from world_interface_uv import (
    EMISSIVE_ISLANDS, PNG_HEIGHT, PNG_WIDTH, SCALE, UV_HEIGHT, UV_WIDTH, Island, face_rects,
    layout,
)


ROOT = Path(__file__).resolve().parents[1]
ENTITY = ROOT / "src/main/resources/assets/thefourthfrequency/textures/entity"
ART_DIR = ROOT / "docs/art/world_interface"
SEED = 0x574F524C44494E54

# Cuboids are lit by the texture, not by the world: without a per-face value split, several hundred
# boxes stacked into one mass read as a single flat purple silhouette from any distance.
# Pitched at the ender dragon rather than at a lit diorama. Vanilla bakes almost no directional
# value into the dragon: it is near-black hide with soft mottling, and the model carries the shape.
# Going that flat outright does not survive here -- the dragon is a dozen large parts and this is
# several hundred small ones, which merge into one silhouette without some split -- so the range is
# compressed hard rather than removed, and the edge AO does most of the separating.
FACE_SHADE = {
    "up": 1.16,
    "north": 1.00,
    "west": 0.90,
    "east": 0.90,
    "south": 0.82,
    "down": 0.74,
}
UPRIGHT_FACES = ("north", "south", "west", "east")
GRADIENT_TOP = 1.05
GRADIENT_BOTTOM = 0.92
# The cheapest thing that separates a hundred touching boxes into a hundred readable boxes.
# Applied to isotropic parts too, where it is the only relief they get.
EDGE_AO = 0.74
# Tumbled parts still need to not be flat, but the variation has to be direction-free.
ISOTROPIC_SHADE = 0.94
# Soft low-frequency blotching, the way dragon hide varies. Cell is in texels.
PATCH_CELL = 3
PATCH_DEPTH = 0.26


# Dark charcoal throughout, the way the reference is, but no longer *uniformly* dark.
#
# The three materials that carry the silhouette used to sit within eight values of each other
# (swallowed 52, plating 44, root 32), and at forty blocks that is one shape: a black blob. The
# spread below is deliberate - plating runs about 40% brighter than the swallowed terrain it is
# bolted to, bone carries a warm offset so the skulls separate from both, and `vein` is the cold
# under-colour the fracture network glows out of. Those are the three things a player has to be
# able to tell apart at range: what it ate, what it armoured itself with, and what is looking
# at them.
FORM_PALETTES = [
    {  # Nascent: still mostly the world it ate. Stone grey, bone not yet gone dark.
        "swallowed": (48, 46, 52),
        "plating": (78, 76, 86),
        "bone": (104, 98, 84),
        "root": (30, 28, 34),
        "flesh": (44, 39, 46),
        "vein": (26, 24, 44),
        "core": (226, 172, 84),
        "eye": (232, 196, 120),
        "seam": (23, 22, 26),
        "ore": (66, 62, 76),
        "accent": (84, 68, 104),
    },
    {  # Grown: the grey has gone cold and the bone is going with it.
        "swallowed": (40, 38, 48),
        "plating": (66, 62, 80),
        "bone": (92, 86, 78),
        "root": (26, 24, 31),
        "flesh": (44, 35, 46),
        "vein": (24, 20, 46),
        "core": (222, 108, 236),
        "eye": (238, 158, 250),
        "seam": (19, 18, 23),
        "ore": (62, 55, 76),
        "accent": (96, 62, 122),
    },
    {  # Terminal: hide black enough that the eyes and the fractures are the only colour on it.
        "swallowed": (31, 29, 38),
        "plating": (54, 50, 68),
        "bone": (78, 72, 66),
        "root": (21, 19, 26),
        "flesh": (38, 28, 40),
        "vein": (22, 17, 48),
        "core": (214, 76, 238),
        "eye": (240, 140, 252),
        "seam": (15, 14, 18),
        "ore": (54, 46, 68),
        "accent": (82, 48, 110),
    },
]

# The failure ending swaps the base sheet for this: same geometry, all the light gone out of it.
BLACK_PALETTE = {
    "swallowed": (15, 14, 17), "plating": (24, 23, 28), "bone": (34, 32, 29),
    "root": (10, 9, 12), "flesh": (16, 13, 17), "vein": (8, 7, 14),
    "core": (56, 6, 22), "eye": (62, 8, 26), "seam": (7, 7, 9),
    "ore": (24, 21, 28), "accent": (60, 10, 40),
}

# How much of the *emissive islands* the fracture network is meant to light, per form.
#
# Stated against the emissive islands rather than against the canvas, because the canvas figure is
# not a measure of anything a player sees. An atlas island is shared by every cuboid that samples
# it: the `mass` islands are 2.6% of the sheet and are sampled by all thirty body slabs, so
# lighting 70% of `mass` lights 70% of the storm's hull. Filling every emissive island solid would
# still only be 3.2% of the canvas, which is why a canvas-relative target of 9% - written when the
# sheet also carried a central eyeball, a halo and a debris field - cannot be met by any amount of
# glow and would only ever have been reached by painting somewhere it does not belong.
EMISSIVE_FILL_TARGET = (0.28, 0.50, 0.74)
# Hard ceiling on canvas coverage, purely as a runaway guard.
EMISSIVE_CEILING = (0.015, 0.025, 0.035)
EMISSIVE_ALPHA = 196
EMISSIVE_CORE_ALPHA = 232
# The fracture network on the shell. Lower than the apertures on purpose: light leaking through a
# split is not the same event as something looking at you, and they must not read as equals.
EMISSIVE_FRACTURE_ALPHA = (120, 220)
# Fracture density per form. The shell is progressively less able to hold itself shut.
FRACTURE_DENSITY = (1.0, 2.4, 6.4)
HIT_COLOR = (255, 42, 88)


def clamp(value: int) -> int:
    return max(0, min(255, value))


def noisy(base: tuple[int, int, int], rng: random.Random, spread: int) -> tuple[int, int, int, int]:
    delta = rng.randint(-spread, spread)
    return tuple(clamp(channel + delta) for channel in base) + (255,)


def patch(x: int, y: int, seed: int, cell_x: int, cell_y: int) -> float:
    """Blocky low-frequency value noise in [0, 1]; the blotching dragon hide has instead of lines."""
    value = ((x // cell_x) * 73856093) ^ ((y // cell_y) * 19349663) ^ seed
    value = (value * 2654435761) & 0xFFFFFFFF
    return ((value >> 16) & 0xFF) / 255.0


def paint_material(tile: Image.Image, material: str, palette: dict, rng: random.Random,
                   face: str) -> None:
    """Paint one face at full value in local tile coordinates; relief is applied afterwards.

    Everything is mottling rather than draughtsmanship. The old pass drew block seams on a grid,
    lengthwise brushing on the plating and outlines around the bone, which at a distance turned the
    body into a technical drawing. The dragon has none of that: dark hide, soft blotches, and one
    lit eye. Materials separate by value and by the shape of their blotching, not by linework.
    """
    width, height = tile.size
    base = palette[material]
    seed = (hash(material) ^ (hash(face) << 3)) & 0xFFFFFF
    cell_x, cell_y = PATCH_CELL, PATCH_CELL
    depth = PATCH_DEPTH
    if material == "root":
        # Fibre runs the length of the strand, so the blotches stretch with it.
        cell_x, cell_y = 1, PATCH_CELL * 3
    elif material in ("eye", "core"):
        depth = 0.10

    for y in range(height):
        for x in range(width):
            factor = 1.0 - depth * 0.5 + depth * patch(x, y, seed, cell_x, cell_y)
            if material == "eye":
                # Brightest at the centre of the aperture, falling off to its rim.
                factor *= 0.55 + 0.45 * (1.0 - abs(y / max(1, height - 1) - 0.5) * 2.0)
            elif material == "core":
                factor *= 0.55 + 0.45 * (1.0 - abs(y / max(1, height - 1) - 0.5) * 2.0)
            grain = rng.randint(-3, 3)
            tile.putpixel((x, y), tuple(
                clamp(int(channel * factor) + grain) for channel in base) + (255,))

    draw = ImageDraw.Draw(tile)
    if material == "swallowed":
        # Sparse mineral flecks are all that is left of the terrain it ate. Kept to single texels
        # and barely off the hide value: at boss scale anything stronger tiles visibly.
        ore = palette["ore"]
        for _ in range(max(1, width * height // 70)):
            draw.point((rng.randrange(width), rng.randrange(height)),
                       fill=tuple(clamp(c + rng.randint(-8, 8)) for c in ore) + (255,))
    elif material == "bone":
        # One soft highlight along the top, no outline. The outline was what made the skulls read
        # as drawn boxes rather than as bone.
        if height >= 4:
            draw.line((1, 1, width - 2, 1),
                      fill=tuple(clamp(int(c * 1.14)) for c in base) + (255,))
    elif material == "flesh":
        for _ in range(max(1, width * height // 110)):
            sx, sy = rng.randrange(width), rng.randrange(height)
            draw.line((sx, sy, sx, min(height - 1, sy + rng.randint(1, 2))),
                      fill=tuple(clamp(int(c * 0.82)) for c in base) + (255,))
    elif material == "vein":
        # The cold under-colour the fractures glow out of, so a crack has something behind it.
        for _ in range(max(1, width * height // 90)):
            sx, sy = rng.randrange(width), rng.randrange(height)
            draw.point((sx, sy), fill=tuple(clamp(int(c * 1.35)) for c in base) + (255,))


def apply_relief(tile: Image.Image, face: str, directional: bool) -> None:
    """Directional face value plus vertical falloff, or a flat knock-down for tumbled parts.

    Either way every face gets the one-texel AO border, which is what stops a hundred touching
    boxes from merging into one silhouette.
    """
    width, height = tile.size
    shade = FACE_SHADE[face] if directional else ISOTROPIC_SHADE
    vertical = directional and face in UPRIGHT_FACES
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
            tile.putpixel((x, y), (clamp(int(red * factor)), clamp(int(green * factor)),
                                   clamp(int(blue * factor)), 255))


def build_base(islands: list[Island], palette: dict, seed: int) -> Image.Image:
    rng = random.Random(seed)
    # Unused UV space is opaque, quiet and matte. Anything sampling it by accident should look
    # like dead material rather than like a mistake.
    dead = palette["seam"]
    row = bytes()
    for x in range(PNG_WIDTH):
        noise = ((x * 17) % 5) - 2
        row += bytes((clamp(dead[0] + noise), clamp(dead[1] + noise), clamp(dead[2] + noise), 255))
    image = Image.frombytes("RGBA", (PNG_WIDTH, PNG_HEIGHT), row * PNG_HEIGHT)

    for island in islands:
        island_rng = random.Random(
            seed ^ sum(ord(char) << (index % 8) for index, char in enumerate(island.name)))
        for face, (x0, y0, x1, y1) in face_rects(island).items():
            if x1 <= x0 or y1 <= y0:
                continue
            tile = Image.new("RGBA", (x1 - x0, y1 - y0), (0, 0, 0, 255))
            paint_material(tile, island.material, palette, island_rng, face)
            apply_relief(tile, face, island.directional)
            image.paste(tile, (x0, y0))
    return image


def emissive_rects(islands: list[Island]) -> list[tuple[str, tuple[int, int, int, int]]]:
    """Which rectangles the glow is allowed to touch, and nothing else.

    Front-facing parts glow on their north face only; parts seen from every angle glow on the four
    upright faces but never on up/down, because a box lit on all six sides just advertises that it
    is a box.
    """
    allowed = []
    for island in islands:
        if island.part not in EMISSIVE_ISLANDS:
            continue
        rects = face_rects(island)
        # The eye is a front-facing aperture; everything else is seen from any angle. Never up or
        # down: a box lit on all six sides just advertises that it is a box.
        faces = ("north",) if island.part == "eye" else UPRIGHT_FACES
        for face in faces:
            allowed.append((island.part, rects[face]))
    return allowed


def fracture_walk(image: Image.Image, rect: tuple[int, int, int, int],
                  colour: tuple[int, int, int], rng: random.Random, density: float) -> None:
    """A branching crack network inside one face rectangle.

    The old sheet lit the shell by filling rectangles, which reads as panels with lamps behind
    them. What the body is actually doing is splitting: this walks a polyline across the face,
    turning by small random amounts and occasionally forking, so the light arrives as something
    escaping through a break. It is also far cheaper on coverage than a filled rectangle for the
    same visible brightness, which is what lets the third form glow across most of its hull
    without the sheet becoming a lamp.
    """
    x0, y0, x1, y1 = rect
    width, height = x1 - x0, y1 - y0
    if width < 4 or height < 4:
        return
    # Drawn into a tile the size of the face and composited back, rather than straight onto the
    # sheet. A two-texel stroke that ends on the last row of a face would otherwise put a pixel
    # one row outside it, which is a glow texel on another part's island - the exact failure the
    # whitelist in validate() exists to catch.
    tile = Image.new("RGBA", (width, height), (0, 0, 0, 0))
    draw = ImageDraw.Draw(tile)

    def reflect(value: float, limit: float) -> float:
        """Fold a coordinate back inside the face instead of ending the crack at the edge.

        A walk that stops at the boundary spends most of its length near wherever it started, so
        raising the seed count just piles more cracks into the same corner and the far side of the
        face stays dark. Reflecting keeps every crack the length it was given and lets a handful of
        long ones cover a face evenly - which is also what a real fracture does when it meets the
        edge of a plate.
        """
        span = max(1.0, limit - 1.0)
        folded = math.fmod(abs(value), 2.0 * span)
        return folded if folded <= span else 2.0 * span - folded

    def crack(x: float, y: float, angle: float, steps: int, alpha_scale: float, allow_fork: bool):
        forks = []
        for step in range(steps):
            angle += rng.uniform(-0.55, 0.55)
            nx = reflect(x + math.cos(angle) * rng.uniform(1.2, 3.0), width)
            ny = reflect(y + math.sin(angle) * rng.uniform(1.2, 3.0), height)
            # Skip the segment that spans a fold, or it draws a chord across the whole face.
            if abs(nx - x) < width * 0.5 and abs(ny - y) < height * 0.5:
                alpha = int(rng.randrange(*EMISSIVE_FRACTURE_ALPHA) * alpha_scale)
                draw.line((round(x), round(y), round(nx), round(ny)),
                          fill=colour + (max(1, min(255, alpha)),),
                          width=2 if rng.random() < 0.22 else 1)
            x, y = nx, ny
            if allow_fork and rng.random() < 0.14 and step > 2:
                forks.append((x, y, angle + rng.choice((-1.0, 1.0)) * rng.uniform(0.6, 1.3)))
        return forks

    # Seed count scales with face area and the form's density; the walks are long enough that a
    # face is covered by a few crossing cracks rather than by many short scratches.
    seeds = max(1, int(width * height * density / 320.0))
    for _ in range(seeds):
        forks = crack(rng.uniform(0, width - 1), rng.uniform(0, height - 1),
                      rng.uniform(0.0, math.tau),
                      rng.randrange(8, 10 + int(max(width, height) * 1.4)), 1.0, True)
        for fx, fy, fangle in forks[:3]:
            crack(fx, fy, fangle, rng.randrange(4, 12), 0.55, False)
    image.alpha_composite(tile, (x0, y0))


def build_emissive(islands: list[Island], colour: tuple[int, int, int],
                   density: float, seed: int) -> Image.Image:
    image = Image.new("RGBA", (PNG_WIDTH, PNG_HEIGHT), (0, 0, 0, 0))
    draw = ImageDraw.Draw(image)
    rng = random.Random(seed)
    for part, (x0, y0, x1, y1) in emissive_rects(islands):
        if x1 <= x0 or y1 <= y0:
            continue
        if part == "eye":
            # The aperture: filled, brightest thing on the sheet, and the only thing on the model
            # that is allowed to be. Everything else is light escaping from something.
            draw.rectangle((x0, y0, x1 - 1, y1 - 1), fill=colour + (EMISSIVE_CORE_ALPHA,))
        elif part in ("mass", "plate"):
            # The shell, splitting. Density rises per form: it is progressively less able to hold.
            fracture_walk(image, (x0, y0, x1, y1), colour, rng, density)
        elif part == "kernel_glow":
            # The buried interface. A lattice, not a lamp - it is a readout, not a face.
            draw.rectangle((x0, y0, x1 - 1, y1 - 1), fill=colour + (EMISSIVE_ALPHA // 2,))
            for offset in range(0, max(1, x1 - x0), max(2, SCALE)):
                draw.line((x0 + offset, y0, x0 + offset, y1 - 1), fill=colour + (EMISSIVE_ALPHA,))
            for offset in range(0, max(1, y1 - y0), max(2, SCALE)):
                draw.line((x0, y0 + offset, x1 - 1, y0 + offset), fill=colour + (EMISSIVE_ALPHA,))
        else:
            # Tendril nodes: a band across the middle of the upright faces, so the limb reads as
            # lit from inside rather than as a glowing cube on a stick.
            mid = (y0 + y1) // 2
            band = max(1, (y1 - y0) // 3)
            draw.rectangle((x0, max(y0, mid - band), x1 - 1, min(y1 - 1, mid + band)),
                           fill=colour + (EMISSIVE_ALPHA,))
    return image


def build_hit(islands: list[Island], seed: int) -> Image.Image:
    """Damage flash. Unlike the glow this covers the whole body, so it paints every island.

    Sparse scribble was what this used to be, and at sixty blocks - which is most of where this
    fight happens - a scribble is invisible. Every face now gets a bright rim plus a wash across
    its interior, so a hit registers as the whole silhouette flaring rather than as speckle. The
    renderer already submits the entire model for this pass, so the cost is unchanged and the
    gain is immediate.
    """
    rng = random.Random(seed)
    image = Image.new("RGBA", (PNG_WIDTH, PNG_HEIGHT), (0, 0, 0, 0))
    draw = ImageDraw.Draw(image)
    for island in islands:
        for x0, y0, x1, y1 in face_rects(island).values():
            if x1 - x0 < 2 or y1 - y0 < 2:
                continue
            # Interior wash first, then the rim over the top of it.
            draw.rectangle((x0, y0, x1 - 1, y1 - 1), fill=HIT_COLOR + (108,))
            draw.rectangle((x0, y0, x1 - 1, y1 - 1), outline=HIT_COLOR + (238,), width=1)
            # A few brighter fractures through the wash so the flash has some structure in it.
            for _ in range(max(1, (x1 - x0) * (y1 - y0) // 600)):
                cx, cy = rng.randrange(x0, x1), rng.randrange(y0, y1)
                points = [(cx, cy)]
                for _ in range(rng.randrange(2, 5)):
                    cx = max(x0, min(x1 - 1, cx + rng.randrange(-3, 4)))
                    cy = max(y0, min(y1 - 1, cy + rng.randrange(-2, 5)))
                    points.append((cx, cy))
                draw.line(points, fill=HIT_COLOR + (rng.randrange(200, 256),), width=1)
    # The eyes take the flash hardest, so they stay the thing you are aiming at even mid-hit.
    for part, (x0, y0, x1, y1) in emissive_rects(islands):
        if part == "eye" and x1 > x0 and y1 > y0:
            draw.rectangle((x0, y0, x1 - 1, y1 - 1), fill=HIT_COLOR + (208,))
    return image


def build_guide(islands: list[Island]) -> Image.Image:
    guide = Image.new("RGBA", (PNG_WIDTH, PNG_HEIGHT), (12, 13, 15, 255))
    draw = ImageDraw.Draw(guide)
    font = ImageFont.load_default()
    colours = {
        "swallowed": (128, 108, 150, 255), "plating": (150, 132, 178, 255),
        "bone": (206, 194, 160, 255), "root": (128, 82, 110, 255),
        "flesh": (176, 96, 148, 255), "vein": (70, 60, 132, 255),
        "core": (248, 196, 96, 255), "eye": (255, 214, 128, 255),
    }
    for grid in range(0, PNG_WIDTH, 16 * SCALE):
        draw.line((grid, 0, grid, PNG_HEIGHT - 1), fill=(26, 29, 34, 255))
    for grid in range(0, PNG_HEIGHT, 16 * SCALE):
        draw.line((0, grid, PNG_WIDTH - 1, grid), fill=(26, 29, 34, 255))
    for index, island in enumerate(sorted(islands, key=lambda i: (i.part, i.bucket)), start=1):
        rects = face_rects(island)
        for x0, y0, x1, y1 in rects.values():
            draw.rectangle((x0, y0, x1 - 1, y1 - 1), outline=colours[island.material], width=1)
        left = min(rect[0] for rect in rects.values())
        top = min(rect[1] for rect in rects.values())
        draw.text((left + 2, top + 2), f"{index}", fill=(238, 238, 238, 255), font=font)
        legend_top = 16 * SCALE * 6
        rows = max(1, (PNG_HEIGHT - legend_top) // 10)
        column, row = divmod(index - 1, rows)
        draw.text((6 + column * 168, legend_top + row * 10), f"{index:02d} {island.name}",
                  fill=colours[island.material], font=font)
    return guide


def validate(islands: list[Island], base: Image.Image, emissive: Image.Image,
             hit: Image.Image, emissive_ceiling: float) -> tuple[int, int]:
    assert base.size == (PNG_WIDTH, PNG_HEIGHT), base.size
    assert emissive.size == base.size and hit.size == base.size

    alphas = base.getchannel("A").getextrema()
    assert alphas == (255, 255), f"base texture must be fully opaque, got alpha range {alphas}"

    allowed = [rect for _, rect in emissive_rects(islands)]
    emissive_pixels = emissive.load()
    nontransparent = 0
    maximum_alpha = 0
    for y in range(PNG_HEIGHT):
        for x in range(PNG_WIDTH):
            alpha = emissive_pixels[x, y][3]
            if alpha == 0:
                continue
            nontransparent += 1
            maximum_alpha = max(maximum_alpha, alpha)
            assert any(x0 <= x < x1 and y0 <= y < y1 for x0, y0, x1, y1 in allowed), (
                f"emissive pixel outside an emissive island at {(x, y)}"
            )
    assert nontransparent > 0
    # Per-form, because the shell is meant to be visibly further open at each stage. The old flat
    # 2% is what held the emissive sheet to 2.4KB and left the boss looking unlit; the whitelist
    # check above is the constraint that actually matters and it stays exactly as strict - glow
    # still cannot land anywhere outside a declared emissive island.
    assert nontransparent <= int(PNG_WIDTH * PNG_HEIGHT * emissive_ceiling), (
        f"emissive coverage {nontransparent / (PNG_WIDTH * PNG_HEIGHT) * 100:.2f}% "
        f"exceeds {emissive_ceiling * 100:.1f}%")

    island_rects = [rect for island in islands for rect in face_rects(island).values()]
    hit_pixels = hit.load()
    for y in range(0, PNG_HEIGHT, 3):
        for x in range(0, PNG_WIDTH, 3):
            if hit_pixels[x, y][3] == 0:
                continue
            assert any(x0 <= x < x1 and y0 <= y < y1 for x0, y0, x1, y1 in island_rects), (
                f"impact pixel on dead sheet at {(x, y)}"
            )
    assert base.tobytes() != emissive.tobytes()
    return nontransparent, maximum_alpha


def assert_islands_disjoint(islands: list[Island]) -> None:
    """A silent UV overlap paints one part with another's material.

    Faces thinner than a texel unavoidably share a column with their own neighbouring face, so
    only cross-island collisions are errors; a same-island bleed just swaps one face-shade value
    for another inside a single material.
    """
    seen: dict[tuple[int, int], str] = {}
    for island in islands:
        for x0, y0, x1, y1 in face_rects(island).values():
            for y in range(y0, y1):
                for x in range(x0, x1):
                    owner = seen.get((x, y))
                    assert owner is None or owner == island.name, (
                        f"UV overlap at {(x, y)}: {island.name} collides with {owner}"
                    )
                    seen[(x, y)] = island.name


def save(image: Image.Image, path: Path) -> str:
    path.parent.mkdir(parents=True, exist_ok=True)
    image.save(path, optimize=True)
    digest = hashlib.sha256(path.read_bytes()).hexdigest()[:16]
    return f"{path.relative_to(ROOT)} {image.size[0]}x{image.size[1]} " \
           f"{path.stat().st_size:>8} {digest}"


def main() -> None:
    islands = layout()
    assert_islands_disjoint(islands)
    reports = []
    for form, palette in enumerate(FORM_PALETTES, start=1):
        base = build_base(islands, palette, SEED + form)
        emissive = build_emissive(islands, palette["core"], FRACTURE_DENSITY[form - 1],
                                  SEED + 200 + form)
        hit = build_hit(islands, SEED + 100 + form)
        nontransparent, maximum_alpha = validate(islands, base, emissive, hit,
                                                 EMISSIVE_CEILING[form - 1])
        reports.append(save(base, ENTITY / f"world_interface_form_{form}.png"))
        reports.append(save(emissive, ENTITY / f"world_interface_form_{form}_emissive.png"))
        reports.append(save(hit, ENTITY / f"world_interface_form_{form}_hit.png"))
        island_area = sum(max(0, x1 - x0) * max(0, y1 - y0)
                          for _, (x0, y0, x1, y1) in emissive_rects(islands))
        fill = nontransparent / max(1, island_area)
        print(f"form {form}: emissive lit={nontransparent}px "
              f"fill={fill * 100:.1f}% of emissive islands "
              f"(target {EMISSIVE_FILL_TARGET[form - 1] * 100:.0f}%), "
              f"canvas={nontransparent / (PNG_WIDTH * PNG_HEIGHT) * 100:.2f}% "
              f"max_alpha={maximum_alpha}")
    reports.append(save(build_base(islands, BLACK_PALETTE, SEED + 999),
                        ENTITY / "world_interface_form_3_black.png"))
    reports.append(save(build_guide(islands),
                        ART_DIR / "world_interface_uv_template.png"))
    print(f"islands={len(islands)} uv={UV_WIDTH}x{UV_HEIGHT} png={PNG_WIDTH}x{PNG_HEIGHT}")
    for report in reports:
        print(report)


if __name__ == "__main__":
    main()
