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
# vanilla music they replace and drown the mod's own signal beds. This is a fraction of the master,
# not of the previous import: re-running always starts from the lossless source.
GAIN = 0.4
QUALITY = "4"

MENU_DIRECTORY = "主菜单BGM"
GAME_DIRECTORY = "游戏内BGM"
PURSUIT_DIRECTORY = "追逐战"
ENCOUNTER_DIRECTORY = "BOSS战"
ENDING_DIRECTORY = "击败BOSS-终末之诗BGM"
FAILURE_ENDING_DIRECTORY = "BOSS战失败-终末之诗BGM"

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
    ("game", "tenshi", GAME_DIRECTORY,
     "NEEDY GIRL OVERDOSE; Aiobahn +81 - 天使は感動する (feat. Aiobahn +81).flac"),
    ("game", "school_rooftop", GAME_DIRECTORY, "hisohkah; WMD - School Rooftop.flac"),
    ("game", "comfort_chain", GAME_DIRECTORY, "instupendo - Comfort Chain.flac"),
    ("pursuit", "level", PURSUIT_DIRECTORY, "niqizhuo,Dapper Husky - level ！.flac"),
    ("encounter", "ncpd_prowl", ENCOUNTER_DIRECTORY, "Marcin Przybyłowicz - NCPD Prowl.flac"),
    ("encounter", "wake_up", ENCOUNTER_DIRECTORY, "MoonDeity - WAKE UP! (Sped Up).flac"),
    ("ending", "reverie", ENDING_DIRECTORY, "ILLENIUM; Dana Salah - Reverie.flac"),
    ("ending", "fallen_down", FAILURE_ENDING_DIRECTORY,
     "The Versions - Fallen Down (Electric Piano Version).flac"),
)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--ffmpeg", required=True, type=Path)
    parser.add_argument("--source", required=True, type=Path,
                        help="Directory holding the per-context master folders.")
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
