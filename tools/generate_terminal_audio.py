#!/usr/bin/env python3
"""Generate the original, short terminal-device sound set and encode it as Ogg Vorbis."""

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
# Leave codec headroom so the decoded Vorbis waveform also remains at or below -3 dBFS.
PEAK = 10 ** (-4.0 / 20.0)


def envelope(t: float, duration: float, attack: float, release: float) -> float:
    return min(1.0, t / attack) * min(1.0, (duration - t) / release)


def click(seed: int, duration: float) -> list[float]:
    rng = random.Random(seed)
    values = []
    for index in range(int(RATE * duration)):
        t = index / RATE
        decay = math.exp(-t * (42.0 + seed * 1.7))
        metal = math.sin(2 * math.pi * (760 + seed * 31) * t)
        edge = math.sin(2 * math.pi * (2280 + seed * 47) * t) * 0.32
        impulse = (rng.random() * 2 - 1) * math.exp(-t * 90.0) * 0.22
        values.append((metal + edge) * decay * 0.72 + impulse)
    return values


def static_pulse(seed: int, duration: float) -> list[float]:
    rng = random.Random(100 + seed)
    values = []
    low = 0.0
    for index in range(int(RATE * duration)):
        t = index / RATE
        low = low * 0.82 + (rng.random() * 2 - 1) * 0.18
        carrier = math.sin(2 * math.pi * (180 + seed * 13 + t * 520) * t) * 0.24
        gate = 0.55 + 0.45 * math.sin(2 * math.pi * (17 + seed) * t) ** 2
        values.append((low * 0.82 + carrier) * gate * envelope(t, duration, 0.008, 0.055))
    return values


def lock(seed: int, duration: float) -> list[float]:
    values = []
    base = 392.0 + seed * 34.0
    for index in range(int(RATE * duration)):
        t = index / RATE
        env = envelope(t, duration, 0.012, 0.19) * math.exp(-t * 2.2)
        tone = math.sin(2 * math.pi * base * t) * 0.58
        tone += math.sin(2 * math.pi * base * 1.5 * t) * 0.31
        tone += math.sin(2 * math.pi * base * 2.02 * t) * 0.11
        values.append(tone * env)
    return values


def fault(seed: int, duration: float) -> list[float]:
    rng = random.Random(300 + seed)
    values = []
    filtered = 0.0
    for index in range(int(RATE * duration)):
        t = index / RATE
        filtered = filtered * 0.94 + (rng.random() * 2 - 1) * 0.06
        swell = math.sin(math.pi * min(1.0, t / duration)) ** 1.6
        beat = math.sin(2 * math.pi * (92 + seed * 7) * t) * math.sin(2 * math.pi * 4.3 * t)
        detuned = math.sin(2 * math.pi * (247 + seed * 11) * t + math.sin(t * 31) * 0.8)
        values.append((beat * 0.42 + detuned * 0.24 + filtered * 0.55) * swell
                      * envelope(t, duration, 0.025, 0.12))
    return values


def write_wave(path: Path, samples: list[float]) -> None:
    maximum = max(1e-9, max(abs(value) for value in samples))
    scale = PEAK / maximum
    pcm = b"".join(struct.pack("<h", int(max(-1.0, min(1.0, value * scale)) * 32767))
                   for value in samples)
    with wave.open(str(path), "wb") as stream:
        stream.setnchannels(1)
        stream.setsampwidth(2)
        stream.setframerate(RATE)
        stream.writeframes(pcm)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--ffmpeg", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()
    recipes = []
    recipes.extend(("click", i + 1, click(i, 0.075 + i * 0.011)) for i in range(4))
    recipes.extend(("tune", i + 1, static_pulse(i, 0.20 + i * 0.035)) for i in range(4))
    recipes.extend(("lock", i + 1, lock(i, 0.36 + i * 0.07)) for i in range(2))
    recipes.extend(("fault", i + 1, fault(i, 0.52 + i * 0.10)) for i in range(2))
    with tempfile.TemporaryDirectory(prefix="tff-terminal-audio-") as temporary:
        temporary = Path(temporary)
        for family, number, samples in recipes:
            destination = args.output / family / f"{number:02d}.ogg"
            destination.parent.mkdir(parents=True, exist_ok=True)
            wav_path = temporary / f"{family}-{number:02d}.wav"
            write_wave(wav_path, samples)
            subprocess.run([
                str(args.ffmpeg), "-y", "-hide_banner", "-loglevel", "error",
                "-i", str(wav_path), "-ac", "1", "-ar", str(RATE),
                "-c:a", "libvorbis", "-q:a", "4", str(destination),
            ], check=True)
            print(destination)


if __name__ == "__main__":
    main()
