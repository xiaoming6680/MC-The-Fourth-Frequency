#!/usr/bin/env python3
"""Create and validate the original 44.1 kHz mono world-interface sound library."""

from __future__ import annotations

import hashlib
import math
import os
import sys
from pathlib import Path

TOOLING = Path(__file__).resolve().parents[1] / "build" / "tff-audio-tooling"
sys.path.insert(0, str(TOOLING))

import numpy as np  # type: ignore  # installed only into build tooling
import soundfile as sf  # type: ignore


RATE = 44_100
TARGET_PEAK = 10.0 ** (-7.0 / 20.0)
ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT / "src/main/resources/assets/thefourthfrequency/sounds/world_interface"

GROUPS: dict[str, tuple[str, int]] = {
    "altar": ("pulse", 4),
    "terminal": ("device", 4),
    "anchor": ("chime", 3),
    "gateway_purple": ("loop", 1),
    "gateway_gold": ("loop", 1),
    "gateway_red": ("loop", 1),
    "summon": ("rise", 3),
    "ambient_form_1": ("loop", 1),
    "ambient_form_2": ("loop", 1),
    "ambient_form_3": ("loop", 1),
    "morph": ("tear", 4),
    "laser": ("warning", 3),
    "orb": ("rise", 3),
    "grab": ("impact", 3),
    "mental": ("mental", 3),
    "weapon": ("device", 3),
    "throw": ("impact", 3),
    "hotbar": ("device", 3),
    "arrow": ("warning", 3),
    "expulsion": ("mental", 3),
    "success": ("chime", 3),
    "failure": ("tear", 4),
}


def stable_seed(name: str, variant: int) -> int:
    return int.from_bytes(hashlib.sha256(f"{name}:{variant}".encode()).digest()[:8], "big")


def envelope(count: int, attack: float, release: float) -> np.ndarray:
    attack_samples = max(1, round(RATE * attack))
    release_samples = max(1, round(RATE * release))
    env = np.ones(count, dtype=np.float64)
    env[:attack_samples] *= np.linspace(0.0, 1.0, attack_samples, endpoint=True)
    env[-release_samples:] *= np.linspace(1.0, 0.0, release_samples, endpoint=True)
    return env


def synth(kind: str, name: str, variant: int) -> np.ndarray:
    seed = stable_seed(name, variant)
    rng = np.random.default_rng(seed)
    loop = kind == "loop"
    duration = 6.0 if loop else {
        "pulse": 0.85, "device": 0.48, "chime": 1.55, "rise": 2.2,
        "tear": 1.9, "warning": 1.25, "impact": 0.95, "mental": 1.75,
    }[kind]
    count = round(RATE * duration)
    t = np.arange(count, dtype=np.float64) / RATE
    base = 36.0 + (seed % 43) + variant * 5.0
    phase = (seed % 997) / 997.0 * math.tau
    if kind == "loop":
        # Integer cycle counts make the first and last samples meet without a seam.
        cycles = 180 + seed % 240
        f1, f2, f3 = cycles / duration, (cycles * 2 + 7) / duration, (cycles * 3 + 11) / duration
        signal = (np.sin(math.tau * f1 * t + phase) * 0.46
                  + np.sin(math.tau * f2 * t) * 0.22
                  + np.sin(math.tau * f3 * t + phase * 0.4) * 0.13)
        signal *= 0.72 + 0.20 * np.sin(math.tau * 2.0 * t / duration) ** 2
    elif kind == "pulse":
        signal = np.sin(math.tau * base * t + phase) * np.exp(-t * 4.2)
        signal += np.sin(math.tau * (base * 3.02) * t) * np.exp(-t * 8.0) * 0.35
        signal *= envelope(count, 0.018, 0.28)
    elif kind == "device":
        square = np.sign(np.sin(math.tau * (base * 4.0) * t + phase))
        clicks = (rng.random(count) > 0.992).astype(np.float64) * rng.uniform(-1, 1, count)
        signal = square * np.exp(-t * 10.0) * 0.42 + clicks * 0.32
        signal *= envelope(count, 0.002, 0.14)
    elif kind == "chime":
        signal = sum(np.sin(math.tau * base * ratio * t + phase / ratio)
                     * np.exp(-t * (1.5 + ratio * 0.5)) / ratio
                     for ratio in (1.0, 1.51, 2.04, 2.98))
        signal *= envelope(count, 0.01, 0.42)
    elif kind == "rise":
        frequency = base + (180.0 + variant * 22.0) * (t / duration) ** 2
        signal = np.sin(math.tau * frequency * t + phase)
        signal += np.sin(math.tau * frequency * 0.49 * t) * 0.42
        signal += rng.normal(0.0, 0.18, count) * (t / duration)
        signal *= envelope(count, 0.08, 0.24)
    elif kind == "tear":
        noise = rng.normal(0.0, 1.0, count)
        filtered = np.convolve(noise, np.ones(19) / 19.0, mode="same")
        signal = filtered * (0.55 + np.sin(math.tau * 9.0 * t) ** 8)
        signal += np.sin(math.tau * (base - 12.0 * t) * t) * 0.38
        signal *= envelope(count, 0.025, 0.35)
    elif kind == "warning":
        gate = (np.sin(math.tau * (3.0 + variant * 0.4) * t) > 0.35).astype(np.float64)
        signal = np.sin(math.tau * (base * 4.0 + 65.0 * t) * t + phase) * gate
        signal += np.sin(math.tau * base * t) * 0.34
        signal *= envelope(count, 0.012, 0.2)
    elif kind == "impact":
        signal = np.sin(math.tau * base * t) * np.exp(-t * 8.0)
        signal += rng.normal(0.0, 0.55, count) * np.exp(-t * 15.0)
        signal += np.sin(math.tau * base * 0.43 * t) * np.exp(-t * 3.8) * 0.48
        signal *= envelope(count, 0.002, 0.22)
    else:  # mental
        signal = (np.sin(math.tau * (base * 5.13) * t + np.sin(t * 31.0) * 2.2)
                  + np.sin(math.tau * (base * 5.31) * t + phase)) * 0.32
        signal += np.sin(math.tau * 7.0 * t) ** 9 * 0.28
        signal += rng.normal(0.0, 0.11, count)
        signal *= envelope(count, 0.12, 0.32)
    maximum = max(1.0e-9, float(np.max(np.abs(signal))))
    return (signal * (TARGET_PEAK / maximum)).astype(np.float32)


def main() -> None:
    OUTPUT.mkdir(parents=True, exist_ok=True)
    expected = sum(count for _, count in GROUPS.values())
    generated = 0
    for name, (kind, variants) in GROUPS.items():
        group = OUTPUT / name
        group.mkdir(parents=True, exist_ok=True)
        for variant in range(1, variants + 1):
            destination = group / f"{variant:02d}.ogg"
            sf.write(destination, synth(kind, name, variant), RATE, format="OGG", subtype="VORBIS")
            info = sf.info(destination)
            samples, sample_rate = sf.read(destination, dtype="float32", always_2d=True)
            peak = float(np.max(np.abs(samples)))
            if destination.read_bytes()[:4] != b"OggS" or info.channels != 1 or sample_rate != RATE:
                raise RuntimeError(f"invalid OGG contract: {destination} {info}")
            if peak > TARGET_PEAK * 1.08:
                raise RuntimeError(f"peak too high: {destination} {peak:.5f}")
            generated += 1
            print(f"{destination.relative_to(ROOT)} rate={sample_rate} channels=1 peak={peak:.4f}")
    if generated != expected or generated != 58:
        raise RuntimeError(f"expected 58 sounds, generated {generated}")


if __name__ == "__main__":
    main()
