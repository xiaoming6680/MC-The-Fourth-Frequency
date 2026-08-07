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

# The kernel recess the model keeps clear of plating; mirrors insideKernelWell.
KERNEL_Y = -13.0


def hash_scatter(seed: int) -> float:
    """Mirror of WorldInterfaceScatter.hash: int overflow semantics matter, hence the masking."""
    value = (seed * 374761393 + 668265263) & 0xFFFFFFFF
    value = ((value ^ (value >> 13)) * 1274126177) & 0xFFFFFFFF
    return ((value ^ (value >> 16)) & 0xFFFF) / 65535.0


def centred(seed: int) -> float:
    return hash_scatter(seed) - 0.5


def inside_kernel_well(x: float, y: float, z: float) -> bool:
    return z < 0.0 and abs(x) < KERNEL_HALF * 1.9 and abs(y - KERNEL_Y) < KERNEL_HALF * 1.9


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


# ============================================================================================
# Cuboid inventory, re-derived from WorldInterfaceModel's generators.
#
# The model no longer bakes three independent form trees. `shell_base` is drawn for the whole
# fight and each morph reveals one more accretion layer over it, so what is enumerated here is
# three shell layers rather than three whole bodies - and the heads and limbs appear once each,
# because they are one shared bone chain scaled per form rather than three baked copies.
# ============================================================================================

# addMass(count, top, step, base, swell, depth, drift, seed), one entry per shell layer.
MASS_LAYERS = [
    (14, -21.0, 2.3, 2.6, 5.0, 0.82, 1.6, 11),
    (8, -24.0, 2.6, 6.2, 4.4, 0.80, 2.5, 41),
    (8, -27.0, 3.0, 9.4, 3.6, 0.78, 3.4, 97),
]
# addPlating(count, top, span, radius, minSize, maxSize, seed). Counts are what accretionBudget
# lets through rather than what the call site asks for: the budget is enforced now, so mirroring
# the requested count would enumerate islands for plates the model is never allowed to build.
# Fixed parts per form: 34 / 100 / 148. Budget: 160 / 224 / 320.
PLATING_LAYERS = [
    (min(52, 160 - 34), -22.0, 28.0, 6.2, 0.55, 1.5, 61),
    (min(24, 224 - 100), -26.0, 34.0, 9.6, 0.75, 2.4, 101),
    (min(30, 320 - 148), -29.0, 40.0, 12.2, 0.95, 3.2, 181),
]
# addRibs(count, top, step, radius, length, seed)
RIB_LAYERS = [
    (10, -17.0, 2.9, 7.4, 7.0, 23),
    (6, -21.0, 3.4, 11.0, 10.0, 53),
]
# addRoots(count, radius, length, seed) - only the base shell trails roots.
ROOT_LAYERS = [
    (10, 4.0, 6.5, 47),
]
# addStormKnot(x, y, z, radius, seed) - the two subordinate masses at terminal form.
STORM_KNOTS = [
    (15.5, -26.0, 4.0, 5.2, 211),
    (-15.5, -23.0, 6.5, 4.6, 233),
]
# The head chain, built once at two scales: centre head at 1.0, the two flanks at 0.78.
HEAD_SCALES = [1.0, 0.78]
NECK_SEGMENT = 6.4
TEETH_PER_JAW = 4
# addTentacle(thick, length): one chain, ten limbs, rows 0..4 with two limbs each.
TENDRIL_THICK = 1.45
TENDRIL_BASE_LENGTH = 11.0
# buildKernel: the recessed lattice that replaced the body's central eye.
KERNEL_HALF = 4.4
KERNEL_FRAME_RINGS = 3
KERNEL_BAR = 0.9


def mass_cuboids() -> list[Cuboid]:
    out = []
    for count, _top, step, base, swell, depth, _drift, seed in MASS_LAYERS:
        for index in range(count):
            span = min(1.0, (index + 0.5) / count * 1.22)
            half_width = base + swell * math.sin(span * math.pi) * (0.70 + hash_scatter(seed + index) * 0.55)
            half_depth = half_width * depth * (0.72 + hash_scatter(seed + index * 9) * 0.58)
            out.append(Cuboid("mass", half_width * 2.0, step * 1.24, half_depth * 2.0, half_width))
    for _x, _y, _z, radius, seed in STORM_KNOTS:
        for index in range(4):
            size = radius * (0.42 + hash_scatter(seed + index * 3) * 0.36)
            out.append(Cuboid("mass", size * 2.0, size * 1.56, size * 1.72, size))
    return out


def plating_cuboids() -> list[Cuboid]:
    out = []
    for count, top, span, radius, min_size, max_size, seed in PLATING_LAYERS:
        for index in range(count):
            height = hash_scatter(seed + index * 5)
            shell = radius * (0.62 + math.sin(min(1.0, height * 1.22) * math.pi) * 0.68)
            angle = hash_scatter(seed + index * 5 + 1) * math.tau
            size = min_size + hash_scatter(seed + index * 5 + 2) * (max_size - min_size)
            lift = 0.92 + hash_scatter(seed + index * 5 + 3) * 0.26
            px = math.cos(angle) * shell * lift
            py = top + span * height
            pz = math.sin(angle) * shell * 0.78 * lift
            if inside_kernel_well(px, py, pz):
                continue
            out.append(Cuboid("plate", size * 2.0, size * 0.84, size * 1.44, size))
    return out


def rib_cuboids() -> list[Cuboid]:
    out = []
    for count, _top, _step, _radius, length, seed in RIB_LAYERS:
        rows = max(1, count // 2)
        for index in range(count):
            row = index // 2
            grade = 0.66 + math.sin((row + 1) * math.pi / (rows + 1.0)) * 0.62
            thick = 0.62 + hash_scatter(seed + index) * 0.5
            out.append(Cuboid("rib", thick * 2.0, thick * 2.4, length * grade, length * grade))
    return out


def root_cuboids() -> list[Cuboid]:
    out = []
    for count, _radius, length, seed in ROOT_LAYERS:
        for index in range(count):
            grade = 0.66 + hash_scatter(seed + index * 3) * 0.72
            out.append(Cuboid("root", 1.7 * grade, length * grade, 1.7 * grade, grade))
    return out


def head_cuboids() -> list[Cuboid]:
    """The shared head chain at its two scales. Seven part types, no per-form duplication."""
    out = []
    for scale in HEAD_SCALES:
        thick = 2.5 * scale
        out.append(Cuboid("vertebra", thick * 2.0, NECK_SEGMENT * scale, thick * 2.0, thick))
        mid_thick = thick * 0.84
        out.append(Cuboid("vertebra", mid_thick * 2.0, NECK_SEGMENT * scale, mid_thick * 2.0, mid_thick))
        out.append(Cuboid("cranium", 9.2 * scale, 8.0 * scale, 9.2 * scale, scale))
        out.append(Cuboid("brow", 9.8 * scale, 2.6 * scale, 1.3 * scale, scale))
        out.append(Cuboid("eye", 5.2 * scale, 5.2 * scale, 1.8 * scale, scale))
        out.append(Cuboid("jaw", 7.8 * scale, 2.8 * scale, 5.6 * scale, scale))
        out.append(Cuboid("tooth", 1.0 * scale, 2.2 * scale, 1.2 * scale, scale))
        if scale == HEAD_SCALES[0]:  # horns only on the centre head
            out.append(Cuboid("horn", 1.4 * scale, 6.2 * scale, 1.4 * scale, scale))
    return out


def tendril_cuboids() -> list[Cuboid]:
    """One limb chain, five row lengths, plus the glow nodes on their own emissive island."""
    out = []
    thick = TENDRIL_THICK
    for row in range(5):
        length = TENDRIL_BASE_LENGTH + row * 1.2
        out.append(Cuboid("tendril", thick * 2.0, length, thick * 2.0, thick))
        mid_thick = thick * 0.72
        out.append(Cuboid("tendril", mid_thick * 2.0, length * 0.88, mid_thick * 2.0, mid_thick))
        tip_thick = thick * 0.44
        out.append(Cuboid("tendril", tip_thick * 2.0, length * 0.76, tip_thick * 2.0, tip_thick))
    node = thick * 0.44 * 1.25
    for factor in (1.0, 0.8):
        out.append(Cuboid("tendril_glow", node * 2.0 * factor, node * 2.0 * factor,
                          node * 2.0 * factor, node * factor))
    return out


def kernel_cuboids() -> list[Cuboid]:
    """The recessed frame and the lattice inside it."""
    out = []
    for index in range(KERNEL_FRAME_RINGS):
        half = KERNEL_HALF * (1.0 - index * 0.22)
        for _ in range(2):  # top and bottom
            out.append(Cuboid("kernel_frame", (half + KERNEL_BAR) * 2.0, KERNEL_BAR, 1.0, KERNEL_HALF))
        for _ in range(2):  # left and right
            out.append(Cuboid("kernel_frame", KERNEL_BAR, half * 2.0, 1.0, KERNEL_HALF))
    out.append(Cuboid("kernel_glow", KERNEL_HALF * 1.24, KERNEL_HALF * 1.24, 0.6, KERNEL_HALF))
    for _ in range(4):
        out.append(Cuboid("kernel_glow", 0.7, KERNEL_HALF, 0.4, KERNEL_HALF))
    return out


def fixed_cuboids() -> list[Cuboid]:
    """Parts the model gives a single texOffs and a single size: one island each."""
    return [
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
    "rib": [9.0],
    "root": [1.0],
    "cranium": [0.9],
    "brow": [0.9],
    "eye": [0.9],
    "jaw": [0.9],
    "tooth": [0.9],
    "vertebra": [1.8, 2.2],
    "tendril": [0.8, 1.2],
    "tendril_glow": [0.7],
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
    "rib": ("bone", True),
    "root": ("root", True),
    "cranium": ("bone", True),
    "brow": ("bone", True),
    "horn": ("bone", True),
    "vertebra": ("bone", True),
    "jaw": ("bone", True),
    "tooth": ("bone", True),
    # The six eyes are the whole face of this thing, so they get their own material rather than
    # sharing the generic core one with the kernel behind them.
    "eye": ("eye", True),
    "tendril": ("flesh", True),
    "tendril_glow": ("core", False),
    "kernel_frame": ("plating", True),
    "kernel_glow": ("core", True),
    "weapon_haft": ("plating", True),
    "weapon_guard": ("plating", True),
    "weapon_blade": ("plating", True),
    "weapon_fuller": ("core", True),
}

# Only these islands may carry emissive pixels. Everything else is hard-failed by validate(),
# which is what stops the scattered glow specks the old generator sprayed across the whole sheet.
EMISSIVE_ISLANDS = {
    # The eyes, the limb nodes, the buried kernel, and the shell itself - the fracture network
    # painted onto mass and plate is what makes the body read as splitting open under pressure.
    "eye", "tendril_glow", "kernel_glow", "mass", "plate",
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
    cuboids = (mass_cuboids() + plating_cuboids() + rib_cuboids() + root_cuboids()
               + head_cuboids() + tendril_cuboids() + kernel_cuboids() + fixed_cuboids())
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
