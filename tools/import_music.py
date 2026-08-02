#!/usr/bin/env python3
"""Import the authored BGM set from lossless masters into the mod's Ogg Vorbis music assets.

The masters are 24-bit/96 kHz FLAC, which Minecraft cannot decode at all: the client only reads
Ogg Vorbis. Resampling to 44.1 kHz stereo and encoding at q4 keeps every track well under the
size a mod jar should carry while staying transparent for streamed background music.

Playback gain is baked in here rather than declared in sounds.json so the shipped files are
already at the intended level no matter which sound event, resource pack or category volume
later references them.
"""

from __future__ import annotations

import argparse
import subprocess
from pathlib import Path

RATE = 44_100
# The masters are mastered loud for streaming platforms; at unity gain they sit far above the
# vanilla music they replace and drown the mod's own signal beds. Everything is imported at 70%.
GAIN = 0.7
QUALITY = "4"

MENU_DIRECTORY = "主菜单BGM"
GAME_DIRECTORY = "游戏内BGM"
ENDING_DIRECTORY = "击败BOSS-终末之诗BGM"

# (category, slug, source directory, source file). The slug becomes the asset path, so it stays
# ASCII: the sound id derived from it is also the "now playing" translation key.
TRACKS = (
    ("menu", "green_to_blue", MENU_DIRECTORY, "Aurenth - green to blue (Sped Up).flac"),
    ("menu", "frutiger_aero", MENU_DIRECTORY, "amirthetrash - frutiger aero.flac"),
    ("menu", "hi_piano", MENU_DIRECTORY, "弹琴老周 - Hi（纯钢琴梦核）.flac"),
    ("menu", "nop", MENU_DIRECTORY, "陈越龙 - nop.flac"),
    ("game", "millennium_dream", GAME_DIRECTORY, "LUSTN - 千禧梦（中式梦核）.flac"),
    ("game", "are_you_lost", GAME_DIRECTORY, "Park Bird - Are You Lost.flac"),
    ("game", "hi", GAME_DIRECTORY, "TEMPOREX - Hi.flac"),
    ("ending", "reverie", ENDING_DIRECTORY, "ILLENIUM; Dana Salah - Reverie.flac"),
)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--ffmpeg", required=True, type=Path)
    parser.add_argument("--source", required=True, type=Path,
                        help="Directory holding the three per-context master folders.")
    parser.add_argument("--output", required=True, type=Path,
                        help="assets/thefourthfrequency/sounds/music")
    args = parser.parse_args()
    for category, slug, directory, filename in TRACKS:
        source = args.source / directory / filename
        if not source.is_file():
            raise SystemExit(f"missing master: {source}")
        destination = args.output / category / f"{slug}.ogg"
        destination.parent.mkdir(parents=True, exist_ok=True)
        subprocess.run([
            str(args.ffmpeg), "-y", "-hide_banner", "-loglevel", "error",
            "-i", str(source), "-vn", "-map_metadata", "-1",
            "-filter:a", f"volume={GAIN}", "-ac", "2", "-ar", str(RATE),
            "-c:a", "libvorbis", "-q:a", QUALITY, str(destination),
        ], check=True)
        print(destination)


if __name__ == "__main__":
    main()
