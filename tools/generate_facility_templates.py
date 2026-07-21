#!/usr/bin/env python3
"""Generate the ten bounded vanilla structure templates used by predecessor facilities."""

from __future__ import annotations

import gzip
import json
import struct
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
DEFINITIONS = ROOT / "src/main/resources/data/thefourthfrequency/facilities/facilities.json"
OUTPUT = ROOT / "src/main/resources/data/thefourthfrequency/structure/facility"


def _utf(value: str) -> bytes:
    encoded = value.encode("utf-8")
    return struct.pack(">H", len(encoded)) + encoded


def _named(tag_type: int, name: str, payload: bytes) -> bytes:
    return bytes((tag_type,)) + _utf(name) + payload


def _string(name: str, value: str) -> bytes:
    return _named(8, name, _utf(value))


def _int(name: str, value: int) -> bytes:
    return _named(3, name, struct.pack(">i", value))


def _int_list(name: str, values: tuple[int, ...]) -> bytes:
    return _named(9, name, bytes((3,)) + struct.pack(">i", len(values))
                  + b"".join(struct.pack(">i", value) for value in values))


def _compound_payload(entries: list[bytes]) -> bytes:
    return b"".join(entries) + b"\x00"


def _compound_list(name: str, compounds: list[bytes]) -> bytes:
    return _named(9, name, bytes((10,)) + struct.pack(">i", len(compounds)) + b"".join(compounds))


def _side(side: int, distance: int, y: int) -> tuple[int, int, int]:
    return ((0, y, -distance), (distance, y, 0), (0, y, distance), (-distance, y, 0))[side % 4]


def build(definition: dict, variant: int) -> dict[tuple[int, int, int], str]:
    width, length, height = definition["width"], definition["length"], definition["height"]
    half_x, half_z = width // 2, length // 2
    floor, wall, roof = definition["floorBlock"], definition["wallBlock"], definition["roofBlock"]
    blocks: dict[tuple[int, int, int], str] = {}

    def put(x: int, y: int, z: int, block: str) -> None:
        blocks[(x, y, z)] = block

    for y in range(1, height):
        for x in range(-half_x + 1, half_x):
            for z in range(-half_z + 1, half_z):
                put(x, y, z, "minecraft:air")
    for x in range(-half_x, half_x + 1):
        for z in range(-half_z, half_z + 1):
            pattern = (x * 31 + z * 17 + variant * 11) % 19
            weathered = floor
            if pattern in (0, 7):
                weathered = {
                    "shelter": "minecraft:mossy_cobblestone" if pattern == 0 else "minecraft:spruce_planks",
                    "observation": "minecraft:cracked_stone_bricks" if pattern == 0 else "minecraft:mossy_stone_bricks",
                    "mine_station": "minecraft:cracked_deepslate_bricks",
                    "warehouse": "minecraft:cracked_stone_bricks" if pattern == 0 else "minecraft:gravel",
                    "transport": "minecraft:tuff",
                }[definition["category"]]
            put(x, 0, z, weathered)
    for y in range(1, height):
        for x in range(-half_x, half_x + 1):
            put(x, y, -half_z, wall)
            put(x, y, half_z, wall)
        for z in range(-half_z + 1, half_z):
            put(-half_x, y, z, wall)
            put(half_x, y, z, wall)
    if definition["category"] != "observation":
        for x in range(-half_x, half_x + 1):
            for z in range(-half_z, half_z + 1):
                gap = (x * 13 + z * 29 + variant * 7) % 17
                if gap != 0 and not (definition["category"] == "warehouse" and gap == 5):
                    put(x, height, z, roof)

    category = definition["category"]
    if category == "shelter":
        sign = -1 if variant == 0 else 1
        put(-2 * sign, 1, 0, "minecraft:red_bed" if variant == 0 else "minecraft:blue_bed")
        put(2 * sign, 1, 0, "minecraft:crafting_table")
        put(2 * sign, 1, sign, "minecraft:furnace")
        put(-3 * sign, 1, 2 * sign, "minecraft:bookshelf")
        put(-3 * sign, 2, 2 * sign, "minecraft:cobweb")
        put(sign, 3, -2 * sign, "minecraft:chain")
        put(sign, 2, -2 * sign, "minecraft:soul_lantern")
        for y in range(1, 4):
            put(3 * sign, y, 2 * sign, "minecraft:air")
        put(3 * sign, 0, 2 * sign, "minecraft:mossy_cobblestone")
        put(4 * sign, 0, 3 * sign, "minecraft:cobblestone")
    elif category == "observation":
        for x in range(-1, 2):
            for z in range(-1, 2):
                put(x, 1, z, "minecraft:polished_deepslate")
        put(0, 3, 0, "minecraft:lightning_rod")
        pylons = [(-3, -3), (3, -3), (3, 3), (-3, 3)]
        for index, (x, z) in enumerate(pylons):
            top = 2 + ((index + variant) & 1)
            for y in range(1, top + 1):
                put(x, y, z, "minecraft:cut_copper" if variant == 0 else "minecraft:exposed_cut_copper")
            put(x, top + 1, z, "minecraft:lightning_rod")
        put(-2, 1, 0, "minecraft:redstone_wire")
        put(2, 1, 0, "minecraft:redstone_wire")
        if variant == 1:
            put(0, 0, -3, "minecraft:calcite")
            put(1, 0, 3, "minecraft:calcite")
    elif category == "mine_station":
        rail_x = -1 if variant == 0 else 1
        for z in range(-7, 8):
            put(rail_x, 0, z, "minecraft:deepslate_tiles")
            put(rail_x, 1, z, "minecraft:rail")
        supports = (-3, 2) if variant == 0 else (-2, 3)
        for z in supports:
            for y in range(1, 4):
                put(-3, y, z, "minecraft:stripped_dark_oak_log")
                put(3, y, z, "minecraft:stripped_dark_oak_log")
            for x in range(-3, 4):
                put(x, 3, z, "minecraft:stripped_dark_oak_log")
        put(3 if variant == 0 else -3, 2, -2, "minecraft:chain")
        put(3 if variant == 0 else -3, 1, -2, "minecraft:soul_lantern")
        put(-3 if variant == 0 else 3, 2, 3, "minecraft:cobweb")
        put(2 if variant == 0 else -2, 1, 1, "minecraft:blast_furnace")
    elif category == "warehouse":
        aisle_x = (-3, 0, 3) if variant == 0 else (-4, -1, 2)
        for x in aisle_x:
            for z in range(-2, 3, 2):
                put(x, 1, z, "minecraft:barrel")
                if (x + z + variant) & 1 == 0:
                    put(x, 2, z, "minecraft:barrel")
        for x in ((-4, 4) if variant == 0 else (-2, 4)):
            put(x, 4, 0, "minecraft:chain")
            put(x, 3, 0, "minecraft:soul_lantern")
        put(2 if variant == 0 else -2, 1, 3, "minecraft:chest")
        put(-1 if variant == 0 else 1, 1, -3, "minecraft:cobweb")
    elif category == "transport":
        platform_z = -2 if variant == 0 else 2
        for x in range(-7, 8):
            put(x, 0, platform_z, "minecraft:polished_andesite")
            put(x, 1, platform_z, "minecraft:rail")
        mirror = 1 if variant == 0 else -1
        for z in range(0, 4):
            put(-3 * mirror, 1, z * mirror, "minecraft:iron_bars")
            if z != 1:
                put(3 * mirror, 1, z * mirror, "minecraft:iron_bars")
        put(-2 * mirror, 1, 2 * mirror, "minecraft:lever")
        put(-2 * mirror, 1, 3 * mirror, "minecraft:redstone_lamp")
        put(0, 3, 0, "minecraft:chain")
        put(0, 2, 0, "minecraft:soul_lantern")
        put(2 * mirror, 1, 2 * mirror, "minecraft:chest")
    return blocks


def write_template(path: Path, blocks: dict[tuple[int, int, int], str]) -> None:
    min_x = min(pos[0] for pos in blocks)
    min_y = min(pos[1] for pos in blocks)
    min_z = min(pos[2] for pos in blocks)
    max_x = max(pos[0] for pos in blocks)
    max_y = max(pos[1] for pos in blocks)
    max_z = max(pos[2] for pos in blocks)
    palette = list(dict.fromkeys(blocks.values()))
    palette_index = {block: index for index, block in enumerate(palette)}
    palette_tags = [_compound_payload([_string("Name", block)]) for block in palette]
    block_tags = []
    for (x, y, z), block in sorted(blocks.items(), key=lambda item: (item[0][1], item[0][0], item[0][2])):
        block_tags.append(_compound_payload([
            _int_list("pos", (x - min_x, y - min_y, z - min_z)),
            _int("state", palette_index[block]),
        ]))
    root = _compound_payload([
        _int("DataVersion", 0),
        _int_list("size", (max_x - min_x + 1, max_y - min_y + 1, max_z - min_z + 1)),
        _compound_list("palette", palette_tags),
        _compound_list("blocks", block_tags),
        _compound_list("entities", []),
    ])
    path.parent.mkdir(parents=True, exist_ok=True)
    with gzip.GzipFile(filename=str(path), mode="wb", mtime=0) as stream:
        stream.write(b"\x0a\x00\x00" + root)


def main() -> None:
    definitions = json.loads(DEFINITIONS.read_text(encoding="utf-8"))
    for definition in definitions:
        templates = definition["templates"]
        if len(templates) != 2:
            raise ValueError(f"{definition['id']} must declare exactly two templates")
        for variant, template in enumerate(templates):
            blocks = build(definition, variant)
            if len(blocks) >= 1_200:
                raise ValueError(f"{template} contains {len(blocks)} placements")
            write_template(OUTPUT / f"{template.rsplit('/', 1)[-1]}.nbt", blocks)
            print(f"{template}: {len(blocks)} placements")


if __name__ == "__main__":
    main()
