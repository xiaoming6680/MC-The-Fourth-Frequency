#!/usr/bin/env python3
"""Generate original non-looping analogue-horror cues for the first world load."""

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
PEAK = 10 ** (-7.0 / 20.0)
COLLAPSE_PEAK = 10 ** (-1.5 / 20.0)


def smoothstep(edge0: float, edge1: float, value: float) -> float:
    progress = max(0.0, min(1.0, (value - edge0) / (edge1 - edge0)))
    return progress * progress * (3.0 - 2.0 * progress)


def pulse(time: float, center: float, width: float) -> float:
    distance = abs(time - center) / width
    return math.exp(-(distance * distance) * 5.0)


def warning(duration: float = 3.25) -> list[float]:
    rng = random.Random(0x4F425356)
    samples: list[float] = []
    slow_noise = 0.0
    tape_noise = 0.0
    phase = 0.0
    contacts = (0.41, 1.37, 2.72)
    dropouts = ((0.87, 0.055), (1.93, 0.09), (2.38, 0.035))
    for index in range(int(RATE * duration)):
        time = index / RATE
        slow_noise = slow_noise * 0.997 + (rng.random() * 2.0 - 1.0) * 0.003
        tape_noise = tape_noise * 0.72 + (rng.random() * 2.0 - 1.0) * 0.28
        wow = 1.0 + math.sin(2.0 * math.pi * 0.31 * time) * 0.014 + slow_noise * 0.018
        phase += 2.0 * math.pi * 46.2 * wow / RATE
        mains = math.sin(phase) * 0.48
        mains += math.sin(phase * 2.013 + 0.8) * 0.16
        distant_tone = math.sin(2.0 * math.pi * (938.0 + 11.0 * math.sin(time * 1.7)) * time)
        distant_tone *= 0.025 + 0.025 * smoothstep(1.0, 2.9, time)
        contact = 0.0
        for contact_time in contacts:
            age = time - contact_time
            if 0.0 <= age < 0.11:
                contact += (tape_noise * 0.75 + math.sin(2.0 * math.pi * 127.0 * age) * 0.25)
                contact *= math.exp(-age * 33.0)
        gate = 1.0
        for dropout_time, dropout_width in dropouts:
            gate *= 1.0 - 0.88 * pulse(time, dropout_time, dropout_width)
        envelope = smoothstep(0.0, 0.32, time) * (1.0 - smoothstep(duration - 0.5, duration, time))
        samples.append((mains + distant_tone + tape_noise * 0.055 + contact * 0.42)
                       * gate * envelope)
    return samples


def collapse(duration: float = 1.95) -> list[float]:
    rng = random.Random(0x434F5252)
    stuck_start = 0.52
    stuck_end = 1.82
    buffer_samples = round(RATE * 0.074)
    stuck_buffer: list[float] = []
    held_noise = 0.0
    for index in range(buffer_samples):
        local_time = index / RATE
        held_noise = held_noise * 0.78 + (rng.random() * 2.0 - 1.0) * 0.22
        # Integer-ish cycle counts make the slice recognizably loop while the
        # discontinuous noise edge supplies the harsh "audio buffer got stuck" seam.
        signal = math.sin(2.0 * math.pi * 11.0 * index / buffer_samples) * 0.54
        signal += math.sin(2.0 * math.pi * 37.0 * index / buffer_samples + 0.7) * 0.24
        signal += math.sin(2.0 * math.pi * 83.0 * index / buffer_samples) * 0.09
        signal += math.sin(2.0 * math.pi * 157.0 * index / buffer_samples + 0.2) * 0.16
        signal += math.sin(2.0 * math.pi * 311.0 * index / buffer_samples) * 0.09
        signal += held_noise * (0.31 + local_time * 0.9)
        signal = max(-0.94, min(0.94, signal))
        stuck_buffer.append(round(signal * 16.0) / 16.0)

    samples: list[float] = []
    prelude_noise = 0.0
    for index in range(int(RATE * duration)):
        time = index / RATE
        if time < stuck_start:
            white = rng.random() * 2.0 - 1.0
            prelude_noise = prelude_noise * 0.88 + white * 0.12
            rise = smoothstep(0.0, stuck_start, time)
            signal = math.sin(2.0 * math.pi * 79.0 * time) * 0.22
            signal += math.sin(2.0 * math.pi * 211.0 * time + 0.4) * 0.12
            signal += math.sin(2.0 * math.pi * 2_130.0 * time + 0.1) * 0.08
            signal += prelude_noise * (0.16 + rise * 0.82)
            signal += math.sin(2.0 * math.pi * 46.0 * time) * 0.28
            samples.append(signal * smoothstep(0.0, 0.045, time))
        elif time < stuck_end:
            stuck_index = (index - round(stuck_start * RATE)) % buffer_samples
            cycle = (index - round(stuck_start * RATE)) // buffer_samples
            gain = 0.94 + ((cycle * 17) % 7) * 0.008
            signal = stuck_buffer[stuck_index] * gain
            # Sample-and-hold quantization adds the brittle edge of a frozen digital stream.
            signal += math.sin(2.0 * math.pi * 46.0 * time) * 0.11
            samples.append(max(-0.9, min(0.9, signal)))
        else:
            # The frozen buffer is cut off without a fade, leaving a short dead-air tail.
            samples.append(0.0)
    return samples


def write_wave(path: Path, samples: list[float], peak: float = PEAK) -> None:
    maximum = max(1e-9, max(abs(value) for value in samples))
    scale = peak / maximum
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
    recipes = (
        ("warning", warning(), PEAK),
        ("collapse", collapse(), COLLAPSE_PEAK),
    )
    args.output.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix="tff-alpha-corruption-") as temporary:
        temporary = Path(temporary)
        for name, samples, peak in recipes:
            wave_path = temporary / f"{name}.wav"
            destination = args.output / f"{name}.ogg"
            write_wave(wave_path, samples, peak)
            subprocess.run([
                str(args.ffmpeg), "-y", "-hide_banner", "-loglevel", "error",
                "-i", str(wave_path), "-ac", "1", "-ar", str(RATE),
                "-c:a", "libvorbis", "-q:a", "5", str(destination),
            ], check=True)
            print(destination)


if __name__ == "__main__":
    main()
