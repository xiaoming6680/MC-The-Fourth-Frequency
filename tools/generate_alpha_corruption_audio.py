#!/usr/bin/env python3
"""Generate original non-looping analogue-horror cues for the first world load.

Both cues ship as numbered variants rather than as a single file. They are not one-off
set dressing: the freeze that opens a pursuit replays the warning every five ticks, the
capture resolution does it again for three seconds, and a player who is on their fifth
attempt has heard the failure land many times over. One sample for all of that stops
being a machine failing and becomes a sound effect with a name - it is recognised, and a
recognised sound is one the player has already filed away as scripted. The variants are
deliberately different *kinds* of hang rather than the same hang re-rolled, because the
fiction is a device losing its grip in whatever way it happens to lose it that time.
"""

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
    """Warning 01 - failing mains, three isolated contacts, three brief dropouts."""
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
    """Collapse 01 - the audio buffer locks on a 74 ms slice, then dead air."""
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


def warning_relay_chatter(duration: float = 3.05) -> list[float]:
    """Warning 02 - the same failing mains, but the contacts arrive as one burst.

    Where 01 spreads three contacts across the whole cue, this one holds still and then
    chatters five times inside half a second, which reads as a relay that cannot decide
    whether it is closed. It ends on a single long dropout instead of several short ones.
    """
    rng = random.Random(0x52434841)
    samples: list[float] = []
    slow_noise = 0.0
    tape_noise = 0.0
    phase = 0.0
    contacts = (1.62, 1.79, 1.88, 2.04, 2.11)
    dropouts = ((0.63, 0.04), (2.47, 0.2))
    for index in range(int(RATE * duration)):
        time = index / RATE
        slow_noise = slow_noise * 0.997 + (rng.random() * 2.0 - 1.0) * 0.003
        tape_noise = tape_noise * 0.68 + (rng.random() * 2.0 - 1.0) * 0.32
        wow = 1.0 + math.sin(2.0 * math.pi * 0.23 * time) * 0.011 + slow_noise * 0.021
        phase += 2.0 * math.pi * 49.6 * wow / RATE
        mains = math.sin(phase) * 0.44
        mains += math.sin(phase * 3.007 + 1.3) * 0.13
        distant_tone = math.sin(2.0 * math.pi * (1_240.0 + 26.0 * math.sin(time * 2.3)) * time)
        distant_tone *= 0.018 + 0.032 * smoothstep(1.4, 2.6, time)
        contact = 0.0
        for contact_time in contacts:
            age = time - contact_time
            if 0.0 <= age < 0.07:
                contact += (tape_noise * 0.9 + math.sin(2.0 * math.pi * 184.0 * age) * 0.3)
                contact *= math.exp(-age * 52.0)
        gate = 1.0
        for dropout_time, dropout_width in dropouts:
            gate *= 1.0 - 0.93 * pulse(time, dropout_time, dropout_width)
        envelope = smoothstep(0.0, 0.28, time) * (1.0 - smoothstep(duration - 0.42, duration, time))
        samples.append((mains + distant_tone + tape_noise * 0.06 + contact * 0.5)
                       * gate * envelope)
    return samples


def warning_tape_dip(duration: float = 3.4) -> list[float]:
    """Warning 03 - the transport drags.

    The pitch falls about a fifth over a sixth of a second and comes back slowly, which
    is the one failure a listener cannot mistake for their own speakers: playback speed
    is not something a room does. The noise floor keeps rising after it recovers, so the
    cue ends further from healthy than it started.
    """
    rng = random.Random(0x54415045)
    samples: list[float] = []
    slow_noise = 0.0
    tape_noise = 0.0
    phase = 0.0
    contacts = (0.72, 2.61)
    dropouts = ((1.14, 0.03), (1.52, 0.028), (2.98, 0.06))
    for index in range(int(RATE * duration)):
        time = index / RATE
        slow_noise = slow_noise * 0.998 + (rng.random() * 2.0 - 1.0) * 0.0027
        tape_noise = tape_noise * 0.75 + (rng.random() * 2.0 - 1.0) * 0.25
        # Down fast, back slowly: a capstan losing grip and then being dragged back up to
        # speed, rather than a pitch envelope someone drew.
        drag = 0.19 * (smoothstep(1.08, 1.24, time) - smoothstep(1.55, 1.94, time))
        wow = 1.0 - drag + math.sin(2.0 * math.pi * 0.27 * time) * 0.012 + slow_noise * 0.017
        phase += 2.0 * math.pi * 43.1 * wow / RATE
        mains = math.sin(phase) * 0.5
        mains += math.sin(phase * 2.006 + 0.4) * 0.14
        distant_tone = math.sin(2.0 * math.pi * (742.0 + 8.0 * math.sin(time * 1.1)) * time)
        distant_tone *= (0.022 + 0.02 * smoothstep(0.6, 2.2, time)) * (1.0 - drag * 3.6)
        contact = 0.0
        for contact_time in contacts:
            age = time - contact_time
            if 0.0 <= age < 0.13:
                contact += (tape_noise * 0.7 + math.sin(2.0 * math.pi * 96.0 * age) * 0.28)
                contact *= math.exp(-age * 27.0)
        gate = 1.0
        for dropout_time, dropout_width in dropouts:
            gate *= 1.0 - 0.85 * pulse(time, dropout_time, dropout_width)
        floor = 0.05 + 0.08 * smoothstep(2.3, 3.35, time)
        envelope = smoothstep(0.0, 0.36, time) * (1.0 - smoothstep(duration - 0.55, duration, time))
        samples.append((mains + distant_tone + tape_noise * floor + contact * 0.4)
                       * gate * envelope)
    return samples


def collapse_driver_stall(duration: float = 2.05) -> list[float]:
    """Collapse 02 - the device deadlocks on a fraction of a buffer.

    Nine milliseconds instead of 01's seventy-four, so the repetition rate lands around
    106 Hz and the ear stops hearing a stutter and starts hearing a tone. This is the
    buzz a machine makes when it stops being a machine, and it arrives with a DC step at
    the seam - the rail is left wherever the converter was when it stopped.
    """
    rng = random.Random(0x44525652)
    stuck_start = 0.34
    stuck_end = 1.79
    buffer_samples = round(RATE * 0.0094)
    stuck_buffer: list[float] = []
    held_noise = 0.0
    for index in range(buffer_samples):
        held_noise = held_noise * 0.55 + (rng.random() * 2.0 - 1.0) * 0.45
        signal = math.sin(2.0 * math.pi * 1.0 * index / buffer_samples) * 0.62
        signal += math.sin(2.0 * math.pi * 3.0 * index / buffer_samples + 1.1) * 0.27
        signal += math.sin(2.0 * math.pi * 7.0 * index / buffer_samples) * 0.14
        signal += held_noise * 0.34
        signal = max(-0.96, min(0.96, signal))
        stuck_buffer.append(round(signal * 8.0) / 8.0)

    samples: list[float] = []
    prelude_noise = 0.0
    stuck_origin = round(stuck_start * RATE)
    for index in range(int(RATE * duration)):
        time = index / RATE
        if time < stuck_start:
            white = rng.random() * 2.0 - 1.0
            prelude_noise = prelude_noise * 0.9 + white * 0.1
            signal = math.sin(2.0 * math.pi * 46.0 * time) * 0.34
            signal += math.sin(2.0 * math.pi * 132.0 * time + 0.9) * 0.1
            signal += prelude_noise * (0.12 + smoothstep(0.0, stuck_start, time) * 0.55)
            samples.append(signal * smoothstep(0.0, 0.02, time))
        elif time < stuck_end:
            offset = index - stuck_origin
            cycle = offset // buffer_samples
            # Never bit-identical between repetitions: a perfectly static buzz is a test
            # tone, and a test tone is the one thing a broken device never produces.
            gain = 0.9 + ((cycle * 23) % 11) * 0.006
            bias = 0.12 * (1.0 - smoothstep(stuck_start, stuck_start + 0.35, time))
            signal = stuck_buffer[offset % buffer_samples] * gain + bias
            signal += math.sin(2.0 * math.pi * 46.0 * time) * 0.08
            samples.append(max(-0.97, min(0.97, signal)))
        else:
            samples.append(0.0)
    return samples


def collapse_bit_decay(duration: float = 2.15) -> list[float]:
    """Collapse 03 - the same slice, quantized harder every repetition.

    Thirty-two levels down to two over roughly thirty passes, so the loop starts as a
    stuck buffer and ends as a bare square wave. It is the slowest of the four and the
    only one that gets *simpler* as it fails, which is what makes it read as something
    running out of resolution rather than something being destroyed.
    """
    rng = random.Random(0x42495444)
    stuck_start = 0.58
    stuck_end = 1.92
    buffer_samples = round(RATE * 0.048)
    base_buffer: list[float] = []
    held_noise = 0.0
    for index in range(buffer_samples):
        held_noise = held_noise * 0.8 + (rng.random() * 2.0 - 1.0) * 0.2
        signal = math.sin(2.0 * math.pi * 7.0 * index / buffer_samples) * 0.5
        signal += math.sin(2.0 * math.pi * 19.0 * index / buffer_samples + 0.3) * 0.26
        signal += math.sin(2.0 * math.pi * 53.0 * index / buffer_samples) * 0.12
        signal += held_noise * 0.3
        base_buffer.append(max(-0.95, min(0.95, signal)))

    samples: list[float] = []
    prelude_noise = 0.0
    stuck_origin = round(stuck_start * RATE)
    for index in range(int(RATE * duration)):
        time = index / RATE
        if time < stuck_start:
            white = rng.random() * 2.0 - 1.0
            prelude_noise = prelude_noise * 0.86 + white * 0.14
            rise = smoothstep(0.0, stuck_start, time)
            signal = math.sin(2.0 * math.pi * 58.0 * time) * 0.26
            signal += math.sin(2.0 * math.pi * 174.0 * time + 0.6) * 0.11
            signal += prelude_noise * (0.14 + rise * 0.7)
            samples.append(signal * smoothstep(0.0, 0.05, time))
        elif time < stuck_end:
            offset = index - stuck_origin
            cycle = offset // buffer_samples
            levels = max(2.0, 32.0 / (1.0 + cycle * 0.42))
            signal = round(base_buffer[offset % buffer_samples] * levels) / levels
            samples.append(max(-0.95, min(0.95, signal * (0.98 - cycle * 0.006))))
        else:
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
    # Each variant carries its own ceiling so that which one the sound engine happens to
    # draw cannot change how loud the failure lands.
    #
    # A fourth collapse - the signal slamming into the rail and grinding down in pitch -
    # was written, generated and cut on listening. It measured fine and it read as damage
    # rather than as a hang, which is the wrong thing for this cue to say. Do not re-add a
    # hard-clipped square variant without auditioning it first; the technical checks in
    # ResourceContractTest cannot catch "sounds bad".
    recipes = (
        ("warning", ((warning, PEAK), (warning_relay_chatter, PEAK),
                     (warning_tape_dip, PEAK))),
        ("collapse", ((collapse, COLLAPSE_PEAK), (collapse_driver_stall, COLLAPSE_PEAK),
                      (collapse_bit_decay, COLLAPSE_PEAK))),
    )
    args.output.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix="tff-alpha-corruption-") as temporary:
        temporary = Path(temporary)
        for name, variants in recipes:
            folder = args.output / name
            folder.mkdir(parents=True, exist_ok=True)
            for index, (recipe, peak) in enumerate(variants, start=1):
                wave_path = temporary / f"{name}_{index:02d}.wav"
                destination = folder / f"{index:02d}.ogg"
                write_wave(wave_path, recipe(), peak)
                subprocess.run([
                    str(args.ffmpeg), "-y", "-hide_banner", "-loglevel", "error",
                    "-i", str(wave_path), "-ac", "1", "-ar", str(RATE),
                    "-c:a", "libvorbis", "-q:a", "5", str(destination),
                ], check=True)
                print(destination)


if __name__ == "__main__":
    main()
