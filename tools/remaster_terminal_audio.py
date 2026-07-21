from __future__ import annotations

from pathlib import Path
import sys

import numpy as np
import soundfile as sf


ROOT = Path(__file__).resolve().parents[1]
AUDIO = ROOT / "src/main/resources/assets/thefourthfrequency/sounds/device/terminal"
TARGET_RATE = 44_100


def read(path: Path) -> np.ndarray:
    samples, rate = sf.read(path, always_2d=True, dtype="float32")
    mono = samples.mean(axis=1)
    if rate != TARGET_RATE:
        old = np.linspace(0.0, 1.0, len(mono), endpoint=False)
        new = np.linspace(0.0, 1.0, round(len(mono) * TARGET_RATE / rate), endpoint=False)
        mono = np.interp(new, old, mono).astype(np.float32)
    return mono


def normalize(samples: np.ndarray, peak: float) -> np.ndarray:
    samples = samples.astype(np.float32, copy=False)
    samples -= float(np.mean(samples))
    maximum = float(np.max(np.abs(samples))) if len(samples) else 0.0
    return samples if maximum < 1.0e-7 else samples * (peak / maximum)


def envelope(samples: np.ndarray, attack_ms: float, release_ms: float) -> np.ndarray:
    result = samples.copy()
    attack = min(len(result), max(1, round(TARGET_RATE * attack_ms / 1000.0)))
    release = min(len(result), max(1, round(TARGET_RATE * release_ms / 1000.0)))
    result[:attack] *= np.linspace(0.0, 1.0, attack, dtype=np.float32)
    result[-release:] *= np.linspace(1.0, 0.0, release, dtype=np.float32)
    return result


def contact(samples: np.ndarray, seconds: float, peak: float) -> np.ndarray:
    samples = samples[: min(len(samples), round(seconds * TARGET_RATE))]
    high = np.concatenate(([0.0], np.diff(samples))).astype(np.float32)
    body = np.convolve(samples, np.ones(9, dtype=np.float32) / 9.0, mode="same")
    return normalize(envelope(high * 0.78 + body * 0.22, 1.5, 35.0), peak)


def write(path: Path, samples: np.ndarray) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    sf.write(path, samples, TARGET_RATE, format="OGG", subtype="VORBIS")


def remaster_contacts() -> None:
    for path in sorted((AUDIO / "click").glob("*.ogg")):
        write(path, contact(read(path), 0.11, 0.52))
    password = contact(read(AUDIO / "click/04.ogg"), 0.065, 0.28)
    write(AUDIO / "password/01.ogg", password)


def remaster_locks() -> None:
    for path in sorted((AUDIO / "lock").glob("*.ogg")):
        write(path, contact(read(path), 0.18, 0.62))


def make_tuning_loop() -> None:
    sources = [read(path) for path in sorted((AUDIO / "tune").glob("0*.ogg"))]
    length = min(min(map(len, sources)), round(0.8 * TARGET_RATE))
    mixed = np.mean(np.stack([source[:length] for source in sources]), axis=0)
    high = np.concatenate(([0.0], np.diff(mixed))).astype(np.float32)
    soft = np.convolve(high, np.ones(7, dtype=np.float32) / 7.0, mode="same")
    loop = normalize(soft, 0.34)
    overlap = min(round(0.06 * TARGET_RATE), len(loop) // 4)
    ramp = np.linspace(0.0, 1.0, overlap, dtype=np.float32)
    loop[:overlap] = loop[-overlap:] * (1.0 - ramp) + loop[:overlap] * ramp
    write(AUDIO / "tune/loop.ogg", loop)


def make_double_pulse() -> None:
    source = read(AUDIO / "fault/01.ogg")
    pulse = contact(source, 0.115, 0.58)
    output = np.zeros(round(0.46 * TARGET_RATE), dtype=np.float32)
    for start_seconds, scale in ((0.0, 1.0), (0.19, 0.92)):
        start = round(start_seconds * TARGET_RATE)
        end = min(len(output), start + len(pulse))
        output[start:end] += pulse[: end - start] * scale
    write(AUDIO / "anomaly/01.ogg", normalize(output, 0.60))


def validate() -> None:
    expected = [
        *(AUDIO / "click").glob("*.ogg"),
        *(AUDIO / "lock").glob("*.ogg"),
        AUDIO / "tune/loop.ogg",
        AUDIO / "password/01.ogg",
        AUDIO / "anomaly/01.ogg",
    ]
    for path in expected:
        info = sf.info(path)
        if info.samplerate != TARGET_RATE or info.channels != 1 or info.frames <= 0:
            raise RuntimeError(f"invalid remastered OGG: {path}: {info}")
        print(f"{path.relative_to(ROOT)}\t{info.duration:.3f}s\t{path.stat().st_size} bytes")


def main() -> int:
    remaster_contacts()
    remaster_locks()
    make_tuning_loop()
    make_double_pulse()
    validate()
    return 0


if __name__ == "__main__":
    sys.exit(main())
