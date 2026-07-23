#!/usr/bin/env python3
"""Synthesize short, original entity cues and encode them as Ogg Vorbis."""

from __future__ import annotations

import argparse
import math
import random
import struct
import subprocess
import tempfile
import wave
from pathlib import Path


RATE = 44_100
PEAK = 10 ** (-5.0 / 20.0)


def envelope(t: float, duration: float, attack: float, release: float) -> float:
    return min(1.0, t / attack) * min(1.0, (duration - t) / release)


def rework_joint(seed: int, duration: float) -> list[float]:
    rng = random.Random(700 + seed)
    result: list[float] = []
    filtered = 0.0
    for index in range(round(RATE * duration)):
        t = index / RATE
        filtered = filtered * 0.91 + (rng.random() * 2.0 - 1.0) * 0.09
        impact = math.exp(-t * (17.0 + seed))
        stone = math.sin(2.0 * math.pi * (62.0 + seed * 7.0) * t) * impact
        hinge = math.sin(2.0 * math.pi * (310.0 + seed * 23.0) * t + math.sin(t * 29.0))
        scrape = filtered * (0.45 + 0.55 * math.sin(2.0 * math.pi * 12.0 * t) ** 2)
        result.append((stone * 0.72 + hinge * 0.22 + scrape * 0.34)
                      * envelope(t, duration, 0.003, 0.11))
    return result


def write_wave(path: Path, samples: list[float]) -> None:
    maximum = max(1.0e-9, max(abs(value) for value in samples))
    scale = PEAK / maximum
    pcm = b"".join(struct.pack("<h", round(max(-1.0, min(1.0, value * scale)) * 32767.0))
                   for value in samples)
    with wave.open(str(path), "wb") as stream:
        stream.setnchannels(1)
        stream.setsampwidth(2)
        stream.setframerate(RATE)
        stream.writeframes(pcm)


def encode(ffmpeg: Path, destination: Path, samples: list[float], temporary: Path) -> None:
    destination.parent.mkdir(parents=True, exist_ok=True)
    wave_path = temporary / (destination.stem + "-" + destination.parent.name + ".wav")
    write_wave(wave_path, samples)
    subprocess.run([str(ffmpeg), "-y", "-hide_banner", "-loglevel", "error", "-i", str(wave_path),
                    "-ac", "1", "-ar", str(RATE), "-c:a", "libvorbis", "-q:a", "5", str(destination)],
                   check=True)
    if destination.read_bytes()[:4] != b"OggS":
        raise RuntimeError(f"invalid OGG header: {destination}")
    print(f"{destination}\t{destination.stat().st_size} bytes")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--ffmpeg", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()
    with tempfile.TemporaryDirectory(prefix="tff-entity-audio-") as temporary_name:
        temporary = Path(temporary_name)
        encode(args.ffmpeg, args.output / "rework/joint/01.ogg", rework_joint(1, 0.72), temporary)
        encode(args.ffmpeg, args.output / "rework/joint/02.ogg", rework_joint(2, 0.84), temporary)


if __name__ == "__main__":
    main()
