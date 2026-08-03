#!/usr/bin/env python3
"""Single source of truth for the world interface's UV layout.

The model bakes several hundred cuboids from a handful of procedural generators, so a hand-written
island table like the Watcher's would go stale the moment a generator parameter moved. Instead this
module re-derives every cuboid the model will build -- mirroring ``WorldInterfaceModel`` exactly,
including its scatter hash -- groups them into size buckets, and packs one island per bucket.

Both consumers read this module: ``prepare_world_interface_textures.py`` paints the islands, and
``--emit-java`` prints the constants block that ``WorldInterfaceUv.java`` holds. Regenerate both
together or the model will sample another part's material.
"""

from __future__ import annotations

import math
from dataclasses import dataclass, field


# UV canvas. The authored model declares LayerDefinition.create(mesh, UV_WIDTH, UV_HEIGHT).
# Wide and short because that is the shape the packer actually fills: the islands shelf out to
# roughly ninety rows, and a square canvas would be two thirds dead sheet paid for in every PNG.
UV_WIDTH = 256
UV_HEIGHT = 128
# Texel density. The third form is scaled 16x and stands some forty blocks tall, so at the old 1x
# a single texel covered a whole block. 4x puts it at a quarter block, which is what makes the
# surface survive being stood next to.
SCALE = 4
PNG_WIDTH = UV_WIDTH * SCALE
PNG_HEIGHT = UV_HEIGHT * SCALE

CORE_Y = -13.0


def hash_scatter(seed: int) -> float:
    """Mirror of WorldInterfaceScatter.hash: int overflow semantics matter, hence the masking."""
    value = (seed * 374761393 + 668265263) & 0xFFFFFFFF
    value = ((value ^ (value >> 13)) * 1274126177) & 0xFFFFFFFF
    return ((value ^ (value >> 16)) & 0xFFFF) / 65535.0


def centred(seed: int) -> float:
    return hash_scatter(seed) - 0.5


def inside_core_socket(x: float, y: float, z: float, half_width: float, half_height: float) -> bool:
    return z < 0.0 and abs(x) < half_width and abs(y - CORE_Y) < half_height


# --------------------------------------------------------------------------------------------
# Cuboid inventory, re-derived from the model's generators.
# --------------------------------------------------------------------------------------------

@dataclass(frozen=True)
class Cuboid:
    """One box the model will bake. Dimensions are in model units, which are also UV units."""
    part: str
    width: float
    height: float
    depth: float
    # The scalar the model buckets on. Kept explicit so the Java side can bucket on the same
    # quantity it already has in hand rather than recomputing a UV footprint.
    key: float


# addMass(count, top, step, base, swell, depth, drift, seed)
MASS_FORMS = [
    (14, -21.0, 2.3, 2.6, 5.0, 0.82, 1.6, 11),
    (20, -24.0, 1.8, 3.4, 7.4, 0.80, 2.5, 41),
    (26, -26.0, 1.5, 4.2, 9.4, 0.78, 3.4, 97),
]
# addPlating(count, top, span, radius, minSize, maxSize, socketWidth, socketHeight, seed)
PLATING_FORMS = [
    (42, -22.0, 28.0, 6.2, 0.55, 1.5, 7.0, 6.5, 61),
    (96, -26.0, 34.0, 9.0, 0.75, 2.4, 9.5, 8.0, 101),
    (168, -29.0, 40.0, 11.5, 0.95, 3.2, 12.5, 10.5, 181),
]
# addDebris(count, top, span, radius, minSize, maxSize, socketWidth, socketHeight, seed)
DEBRIS_FORMS = [
    (16, -19.0, 23.0, 7.8, 0.85, 1.8, 7.0, 6.5, 31),
    (34, -23.0, 29.0, 11.4, 1.2, 3.0, 9.5, 8.0, 67),
    (56, -27.0, 35.0, 14.0, 1.5, 4.2, 12.5, 10.5, 127),
]
# addRibs(count, top, step, radius, length, seed)
RIB_FORMS = [
    (10, -17.0, 2.9, 7.4, 7.0, 23),
    (22, -21.0, 2.7, 11.0, 10.0, 53),
    (32, -25.0, 2.3, 13.6, 13.0, 109),
]
# addRoots(count, radius, length, seed)
ROOT_FORMS = [
    (9, 4.0, 6.5, 47),
    (16, 5.2, 9.5, 83),
    (28, 6.4, 12.5, 149),
]
# addSkulls(scale, neckSegments, horns) per form; the two flanking skulls run at 0.84 of centre.
SKULL_FORMS = [
    (0.62, 3, False),
    (0.88, 2, True),
    (1.35, 1, True),
]
# addRingSegments(count, radius, width, length) per ring band.
RING_BANDS = [
    ("ring_fragment", 8, 1.4, 1.8),
    ("ring_segment", 16, 1.9, 2.4),
    ("ring_outer", 24, 2.6, 3.2),
    ("ring_inner", 16, 1.5, 1.9),
]
# addTentacle(thick, length) per form; length varies by row, thickness does not.
TENDRIL_FORMS = [(0.85, 9.0, 4), (1.25, 11.5, 5), (1.75, 14.0, 6)]
# addCoreSocket(frontZ, backZ, outerWidth, outerHeight, innerWidth, innerHeight) per form.
CORE_SOCKET_FORMS = [
    (-6.7, -11.7, 7.0, 6.5, 5.0, 5.0),
    (-11.9, -15.2, 9.5, 8.0, 7.0, 5.4),
    (-12.4, -17.0, 12.5, 10.5, 9.2, 7.4),
]
CORE_SOCKET_RINGS = 3
CORE_SOCKET_BAR = 1.6


def mass_cuboids() -> list[Cuboid]:
    out = []
    for count, _top, step, base, swell, depth, _drift, seed in MASS_FORMS:
        for index in range(count):
            span = min(1.0, (index + 0.5) / count * 1.22)
            half_width = base + swell * math.sin(span * math.pi) * (0.70 + hash_scatter(seed + index) * 0.55)
            half_depth = half_width * depth * (0.72 + hash_scatter(seed + index * 9) * 0.58)
            out.append(Cuboid("mass", half_width * 2.0, step * 1.24, half_depth * 2.0, half_width))
    return out


def plating_cuboids() -> list[Cuboid]:
    out = []
    for count, top, span, radius, min_size, max_size, sw, sh, seed in PLATING_FORMS:
        for index in range(count):
            height = hash_scatter(seed + index * 5)
            shell = radius * (0.62 + math.sin(min(1.0, height * 1.22) * math.pi) * 0.68)
            angle = hash_scatter(seed + index * 5 + 1) * math.tau
            size = min_size + hash_scatter(seed + index * 5 + 2) * (max_size - min_size)
            lift = 0.92 + hash_scatter(seed + index * 5 + 3) * 0.26
            px = math.cos(angle) * shell * lift
            py = top + span * height
            pz = math.sin(angle) * shell * 0.78 * lift
            if inside_core_socket(px, py, pz, sw, sh):
                continue
            out.append(Cuboid("plate", size * 2.0, size * 0.84, size * 1.44, size))
    return out


def debris_cuboids() -> list[Cuboid]:
    out = []
    for count, top, span, radius, min_size, max_size, sw, sh, seed in DEBRIS_FORMS:
        for index in range(count):
            angle = hash_scatter(seed + index * 3) * math.tau
            distance = radius * (0.62 + hash_scatter(seed + index * 3 + 1) * 0.58)
            size = min_size + hash_scatter(seed + index * 3 + 2) * (max_size - min_size)
            px = math.cos(angle) * distance
            py = top + span * hash_scatter(seed + index * 7)
            pz = math.sin(angle) * distance * 0.74
            if inside_core_socket(px, py, pz, sw, sh):
                continue
            out.append(Cuboid("debris", size * 2.0, size * 2.0, size * 2.0, size))
    return out


def rib_cuboids() -> list[Cuboid]:
    out = []
    for count, _top, _step, _radius, length, seed in RIB_FORMS:
        rows = max(1, count // 2)
        for index in range(count):
            row = index // 2
            grade = 0.66 + math.sin((row + 1) * math.pi / (rows + 1.0)) * 0.62
            thick = 0.62 + hash_scatter(seed + index) * 0.5
            out.append(Cuboid("rib", thick * 2.0, thick * 2.4, length * grade, length * grade))
    return out


def root_cuboids() -> list[Cuboid]:
    out = []
    for count, _radius, length, seed in ROOT_FORMS:
        for index in range(count):
            grade = 0.66 + hash_scatter(seed + index * 3) * 0.72
            out.append(Cuboid("root", 1.7 * grade, length * grade, 1.7 * grade, grade))
    return out


def skull_cuboids() -> list[Cuboid]:
    """Six part types at six distinct scales: three forms times centre/flank."""
    out = []
    for scale, neck_segments, horns in SKULL_FORMS:
        for factor in (1.0, 0.84):
            s = scale * factor
            out.append(Cuboid("cranium", 8.0 * s, 7.2 * s, 8.0 * s, s))
            out.append(Cuboid("brow", 8.6 * s, 2.4 * s, 1.2 * s, s))
            out.append(Cuboid("maw", 6.8 * s, 2.6 * s, 5.0 * s, s))
            out.append(Cuboid("socket", 2.3 * s, 2.3 * s, 1.1 * s, s))
            if horns:
                out.append(Cuboid("horn", 1.4 * s, 5.8 * s, 1.4 * s, s))
            for index in range(neck_segments):
                thick = s * (2.4 - index * 0.28)
                out.append(Cuboid("vertebra", thick * 2.0, thick * 2.2, thick * 2.0, thick))
    return out


def ring_cuboids() -> list[Cuboid]:
    """Each band gets its own island; the grade spread inside a band is only 2.4x."""
    out = []
    for name, count, width, length in RING_BANDS:
        for index in range(count):
            grade = 0.62 + hash_scatter(index * 13 + count) * 0.86
            out.append(Cuboid(name, width * grade, length * grade, width * grade, grade))
    return out


def tendril_cuboids() -> list[Cuboid]:
    """Three links per limb plus the glow nodes, which must land on their own emissive island."""
    out = []
    for form, (thick, base_length, _count) in enumerate(TENDRIL_FORMS, start=1):
        for row in range(5):
            length = base_length + row * (1.0 if form == 1 else 1.1 if form == 2 else 1.4)
            out.append(Cuboid(f"tendril_{form}", thick * 2.0, length, thick * 2.0, thick))
            mid_thick = thick * 0.72
            mid_length = length * 0.88
            out.append(Cuboid(f"tendril_{form}", mid_thick * 2.0, mid_length, mid_thick * 2.0, mid_thick))
            tip_thick = thick * 0.44
            out.append(Cuboid(f"tendril_{form}", tip_thick * 2.0, length * 0.76, tip_thick * 2.0, tip_thick))
        node = thick * 0.62
        for factor in (1.0, 0.86, 0.72):
            out.append(Cuboid("tendril_glow", node * 2.0 * factor, node * 2.0 * factor,
                              node * 2.0 * factor, node * factor))
    return out


def core_socket_cuboids() -> list[Cuboid]:
    """The funnel seating the core. Rails and posts are split: one island sized to both would be
    mostly empty for whichever of the two it did not match."""
    out = []
    for front_z, back_z, outer_w, outer_h, inner_w, inner_h in CORE_SOCKET_FORMS:
        depth = abs(back_z - front_z) / (CORE_SOCKET_RINGS - 1) + 0.8
        for index in range(CORE_SOCKET_RINGS):
            progress = index / (CORE_SOCKET_RINGS - 1)
            half_width = outer_w + (inner_w - outer_w) * progress
            half_height = outer_h + (inner_h - outer_h) * progress
            key = max(half_width, half_height)
            for _ in range(2):  # top and bottom
                out.append(Cuboid("core_collar_rail",
                                  (half_width + CORE_SOCKET_BAR) * 2.0, CORE_SOCKET_BAR, depth, key))
            for _ in range(2):  # left and right
                out.append(Cuboid("core_collar_post",
                                  CORE_SOCKET_BAR, half_height * 2.0, depth, key))
    return out


def fixed_cuboids() -> list[Cuboid]:
    """Parts the model already gives a single texOffs and a single size: one island each."""
    return [
        Cuboid("eye_1_ball", 8.4, 8.4, 2.0, 1.0),
        Cuboid("eye_1_pupil", 3.6, 3.6, 1.0, 1.0),
        Cuboid("eye_1_shutter", 1.8, 5.2, 1.4, 1.0),
        Cuboid("eye_2_ball", 12.4, 9.2, 2.2, 1.0),
        Cuboid("eye_2_pupil", 4.8, 4.8, 1.2, 1.0),
        Cuboid("eye_2_aperture", 2.0, 6.4, 1.6, 1.0),
        Cuboid("eye_3_ball", 16.8, 13.2, 2.8, 1.0),
        Cuboid("eye_3_pupil", 6.8, 6.8, 1.5, 1.0),
        Cuboid("eye_3_slit", 1.8, 6.4, 0.8, 1.0),
        Cuboid("eye_3_cage", 2.2, 9.2, 1.8, 1.0),
        Cuboid("jaw_1", 8.8, 2.4, 5.6, 1.0),
        Cuboid("jaw_2", 6.0, 3.4, 7.6, 1.0),
        Cuboid("jaw_3", 16.8, 4.4, 9.8, 1.0),
        Cuboid("tooth", 0.9, 3.7, 1.1, 1.0),
        Cuboid("weapon_haft", 1.5, 16.0, 1.5, 1.0),
        Cuboid("weapon_guard", 6.0, 2.0, 1.0, 1.0),
        Cuboid("weapon_blade", 3.0, 12.0, 1.0, 1.0),
        Cuboid("weapon_fuller", 4.8, 1.2, 0.7, 1.0),
    ]


# --------------------------------------------------------------------------------------------
# Bucketing. Thresholds are on the model's own size scalar, so the Java side buckets on a value
# it already holds instead of recomputing a UV footprint.
# --------------------------------------------------------------------------------------------

# part -> ascending upper bounds on Cuboid.key; a key above the last bound lands in the last bucket.
BUCKETS: dict[str, list[float]] = {
    "mass": [4.0, 6.5, 9.5],
    "plate": [1.2, 2.0],
    "debris": [1.9, 3.0],
    "rib": [9.0, 13.0],
    "root": [1.0],
    "cranium": [0.75, 1.0],
    "brow": [0.75, 1.0],
    "maw": [0.75, 1.0],
    "socket": [0.75, 1.0],
    "horn": [1.0],
    "vertebra": [1.2, 1.9],
    "tendril_1": [0.5],
    "tendril_2": [0.7],
    "tendril_3": [1.0],
    "tendril_glow": [0.5, 0.8],
    "core_collar_rail": [6.6, 9.0],
    "core_collar_post": [6.6, 9.0],
}


def bucket_of(part: str, key: float) -> int:
    bounds = BUCKETS.get(part)
    if not bounds:
        return 0
    for index, bound in enumerate(bounds):
        if key <= bound:
            return index
    return len(bounds)


# Materials drive both the palette and whether directional shading is baked in.
# Parts the model rotates freely (debris, plating) must stay isotropic: a baked "top is bright"
# reads as wrong the moment the box is tumbled upside down, which is exactly what addDebris does.
MATERIAL = {
    "mass": ("swallowed", True),
    "plate": ("plating", False),
    "debris": ("swallowed", False),
    "rib": ("bone", True),
    "root": ("root", True),
    "cranium": ("bone", True),
    "brow": ("bone", True),
    "maw": ("bone", True),
    "socket": ("socket", True),
    "horn": ("bone", True),
    "vertebra": ("bone", True),
    "ring_fragment": ("plating", False),
    "ring_segment": ("plating", False),
    "ring_outer": ("plating", False),
    "ring_inner": ("core", False),
    "tendril_1": ("flesh", True),
    "tendril_2": ("flesh", True),
    "tendril_3": ("flesh", True),
    "tendril_glow": ("core", False),
    "core_collar_rail": ("plating", True),
    "core_collar_post": ("plating", True),
    "eye_1_ball": ("sclera", True),
    "eye_2_ball": ("sclera", True),
    "eye_3_ball": ("sclera", True),
    "eye_1_pupil": ("core", True),
    "eye_2_pupil": ("core", True),
    "eye_3_pupil": ("core", True),
    "eye_1_shutter": ("plating", True),
    "eye_2_aperture": ("plating", True),
    "eye_3_cage": ("plating", True),
    "eye_3_slit": ("core", True),
    "jaw_1": ("bone", True),
    "jaw_2": ("bone", True),
    "jaw_3": ("bone", True),
    "tooth": ("bone", True),
    "weapon_haft": ("plating", True),
    "weapon_guard": ("plating", True),
    "weapon_blade": ("plating", True),
    "weapon_fuller": ("core", True),
}

# Only these islands may carry emissive pixels. Everything else is hard-failed by validate(),
# which is what stops the scattered glow specks the old generator sprayed across the whole sheet.
EMISSIVE_ISLANDS = {
    "eye_1_pupil", "eye_2_pupil", "eye_3_pupil", "eye_3_slit",
    "socket", "tendril_glow", "ring_inner",
}


@dataclass
class Island:
    name: str
    part: str
    bucket: int
    width: float
    height: float
    depth: float
    material: str
    directional: bool
    u: int = 0
    v: int = 0
    members: int = 0
    key_range: tuple[float, float] = field(default=(0.0, 0.0))

    @property
    def uv_width(self) -> float:
        return 2.0 * (self.width + self.depth)

    @property
    def uv_height(self) -> float:
        return self.depth + self.height


def build_islands() -> list[Island]:
    """One island per (part, bucket), sized to the largest member so nothing samples past its own."""
    cuboids = (mass_cuboids() + plating_cuboids() + debris_cuboids() + rib_cuboids()
               + root_cuboids() + skull_cuboids() + ring_cuboids() + tendril_cuboids()
               + core_socket_cuboids() + fixed_cuboids())
    grouped: dict[tuple[str, int], list[Cuboid]] = {}
    for cuboid in cuboids:
        grouped.setdefault((cuboid.part, bucket_of(cuboid.part, cuboid.key)), []).append(cuboid)

    # An empty middle or trailing bucket would leave the Java table shorter than its bounds array,
    # and the model would index past the end the first time a cuboid landed in the missing band.
    for part, bounds in BUCKETS.items():
        present = sorted(b for (p, b) in grouped if p == part)
        expected = list(range(len(bounds) + 1))
        if present != expected:
            raise SystemExit(
                f"{part}: buckets {present} do not cover 0..{len(bounds)}; "
                f"adjust BUCKETS[{part!r}] to match the sizes the model actually builds"
            )

    islands = []
    for (part, bucket), members in sorted(grouped.items()):
        material, directional = MATERIAL[part]
        bounds = BUCKETS.get(part)
        name = part if not bounds else f"{part}_{bucket}"
        islands.append(Island(
            name=name, part=part, bucket=bucket,
            width=max(member.width for member in members),
            height=max(member.height for member in members),
            depth=max(member.depth for member in members),
            material=material, directional=directional,
            members=len(members),
            key_range=(min(m.key for m in members), max(m.key for m in members)),
        ))
    return islands


def pack(islands: list[Island]) -> None:
    """Shelf packer, tallest first. Assigns integer u/v; a texel of slack keeps islands disjoint."""
    ordered = sorted(islands, key=lambda island: -island.uv_height)
    shelf_v = 0
    shelf_height = 0
    cursor = 0
    for island in ordered:
        width = math.ceil(island.uv_width) + 1
        height = math.ceil(island.uv_height) + 1
        if cursor + width > UV_WIDTH:
            shelf_v += shelf_height
            shelf_height = 0
            cursor = 0
        if shelf_v + height > UV_HEIGHT:
            raise SystemExit(f"UV canvas overflow packing {island.name}; raise UV_HEIGHT")
        island.u = cursor
        island.v = shelf_v
        cursor += width
        shelf_height = max(shelf_height, height)


def layout() -> list[Island]:
    islands = build_islands()
    pack(islands)
    return islands


def px(value: float) -> int:
    return int(round(value * SCALE))


def face_rects(island: Island) -> dict[str, tuple[int, int, int, int]]:
    """Minecraft cuboid UV cross, in PNG pixels; rectangles use an exclusive max bound."""
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
    converted = {}
    for name, (x0, y0, x1, y1) in raw.items():
        left, top = max(0, px(x0)), max(0, px(y0))
        right = min(PNG_WIDTH, max(left + 1, px(x1)))
        bottom = min(PNG_HEIGHT, max(top + 1, px(y1)))
        converted[name] = (left, top, right, bottom)
    return converted


def probe(island: Island) -> tuple[int, int]:
    """A pixel guaranteed to be inside the island's painted front face.

    The top-left corner of an island is not painted -- the UV cross leaves that square empty --
    so a drift check has to aim at a face rather than at the offset itself.
    """
    x0, y0, x1, y1 = face_rects(island)["north"]
    return (x0 + x1 - 1) // 2, (y0 + y1 - 1) // 2


JAVA_PATH = "src/client/java/com/xm/thefourthfrequency/client_render/WorldInterfaceUv.java"
# Read by ResourceContractTest, which cannot run this script but can check that the offsets the
# model ships with still land on painted sheet. Regenerating one half without the other is the
# failure it exists to catch.
MANIFEST_PATH = "docs/art/world_interface/layout.txt"

JAVA_HEADER = '''package com.xm.thefourthfrequency.client_render;

/**
 * Where every part of the world interface samples its material from.
 *
 * <p>GENERATED by {@code tools/world_interface_uv.py --emit-java}. Do not hand-edit: the same
 * script packs these islands and paints the sheets, so an edited offset here points the model at
 * a rectangle nothing was painted into.
 *
 * <p>Parts whose size varies get several islands, and pick one by the same size scalar the
 * generator bucketed on. One island for a whole class would mean the small members sampled only
 * the top-left corner of the large member's island, which is how the old sheet ended up with no
 * part distinguishable from any other.
 */
final class WorldInterfaceUv {
\tprivate WorldInterfaceUv() {
\t}

\t/** The authored UV canvas; the PNGs are this times the generator's density factor. */
\tstatic final int UV_WIDTH = %d;
\tstatic final int UV_HEIGHT = %d;

\tprivate static int[] pick(int[][] islands, float[] bounds, float key) {
\t\tfor (int index = 0; index < bounds.length; index++) {
\t\t\tif (key <= bounds[index]) return islands[index];
\t\t}
\t\treturn islands[bounds.length];
\t}
'''


def emit_java(islands: list[Island]) -> str:
    by_part: dict[str, list[Island]] = {}
    for island in islands:
        by_part.setdefault(island.part, []).append(island)

    body = []
    for part in sorted(by_part):
        entries = sorted(by_part[part], key=lambda island: island.bucket)
        constant = part.upper()
        bounds = BUCKETS.get(part)
        if not bounds:
            island = entries[0]
            body.append(f"\tstatic final int {constant}_U = {island.u};")
            body.append(f"\tstatic final int {constant}_V = {island.v};")
            continue
        pairs = ", ".join(f"{{{island.u}, {island.v}}}" for island in entries)
        joined = ", ".join(f"{bound}F" for bound in bounds)
        ranges = "; ".join(f"{i.name} {i.key_range[0]:.2f}..{i.key_range[1]:.2f}" for i in entries)
        body.append(f"\tprivate static final int[][] {constant} = {{{pairs}}};")
        body.append(f"\tprivate static final float[] {constant}_BOUNDS = {{{joined}}};")
        body.append(f"\t/** {ranges} */")
        body.append(f"\tstatic int[] {camel(part)}(float key) {{")
        body.append(f"\t\treturn pick({constant}, {constant}_BOUNDS, key);")
        body.append("\t}")
    return (JAVA_HEADER % (UV_WIDTH, UV_HEIGHT)) + "\n" + "\n".join(body) + "\n}\n"


def camel(part: str) -> str:
    head, *tail = part.split("_")
    return head + "".join(word.capitalize() for word in tail)


def main() -> None:
    import sys
    islands = layout()
    if "--emit-java" in sys.argv:
        root = __import__("pathlib").Path(__file__).resolve().parents[1]
        target = root / JAVA_PATH
        target.write_text(emit_java(islands), encoding="utf-8")
        manifest = root / MANIFEST_PATH
        manifest.parent.mkdir(parents=True, exist_ok=True)
        lines = [
            "# GENERATED by tools/world_interface_uv.py --emit-java.",
            "# name u v probe_x probe_y -- probe is a pixel inside the island's painted front face.",
            f"# canvas {UV_WIDTH}x{UV_HEIGHT} png {PNG_WIDTH}x{PNG_HEIGHT} density {SCALE}",
        ]
        for island in sorted(islands, key=lambda i: (i.part, i.bucket)):
            probe_x, probe_y = probe(island)
            lines.append(f"{island.name} {island.u} {island.v} {probe_x} {probe_y}")
        manifest.write_text("\n".join(lines) + "\n", encoding="utf-8")
        print(f"wrote {target}")
        print(f"wrote {manifest}")
        return
    used = sum((math.ceil(i.uv_width) + 1) * (math.ceil(i.uv_height) + 1) for i in islands)
    tallest = max(i.v + math.ceil(i.uv_height) + 1 for i in islands)
    print(f"islands={len(islands)} canvas={UV_WIDTH}x{UV_HEIGHT} png={PNG_WIDTH}x{PNG_HEIGHT} "
          f"rows_used={tallest} utilisation={used / (UV_WIDTH * UV_HEIGHT) * 100:.1f}%")
    for island in sorted(islands, key=lambda i: (i.part, i.bucket)):
        lo, hi = island.key_range
        print(f"  {island.name:18} u={island.u:3} v={island.v:3} "
              f"{island.uv_width:6.1f}x{island.uv_height:5.1f} n={island.members:3} "
              f"key={lo:.2f}..{hi:.2f} {island.material}"
              f"{'' if island.directional else ' (isotropic)'}")


if __name__ == "__main__":
    main()
