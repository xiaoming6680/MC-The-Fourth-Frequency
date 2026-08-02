#!/usr/bin/env python3
"""Generate the analog-horror signal palette: carrier, static, hiss, dead air and cues.

The mod's existing 77 sound effects are almost entirely *event* sounds - a laser fires, a
lock engages, a terminal key clicks. Analog horror is carried by the opposite thing: the
sound of the medium itself. Tape hiss, mains hum, a carrier tone that was always there
until it stops. Those are continuous, so unlike a four-second event they give unease
somewhere to live during the long quiet stretches between anomalies.

Every "_loop" recipe here is built to be seamless. Periodic partials are placed on exact
integer multiples of 1/duration so they phase-align across the wrap, and the noise beds are
cross-faded head-to-tail by `seamless()` so the join has no click.

Two properties of the loops matter as much as their content:

*Length.* A bed runs for as long as a session does, so its loop point is heard hundreds of
times. The four bed lengths are chosen to be mutually coprime - 11.30s, 13.70s, 17.90s and
19.10s, which are 113, 137, 179 and 191 tenths of a second, all prime - so that layers
running together do not realign and re-announce the wrap. Two of them used to be exactly
8.0s, which meant they restarted in lockstep forever.

*Width.* These layers are played non-positionally, so unlike every 3D cue in the mod they
are not required to be mono, and a mono bed collapses to a point in the middle of the
player's head. The noisy recipes render two decorrelated channels instead. `alert` stays
mono deliberately: it is a warning, and a warning should come from one place.

The recipes below are unchanged from the mono revision - only the lengths, channel count and
encoder quality moved - so the previous assets remain exactly reproducible. To restore them:
render channel 0 only, at CARRIER 8.0s / STATIC 6.0s / TAPE_HISS 8.0s / DEAD_AIR 10.0s, with
dead air's tick list trimmed to its first four entries, and encode `-ac 1 -q:a 5`. That has
been verified to decode sample-for-sample identical to the files it replaced.

Usage:
    python tools/generate_signal_bed_audio.py --self-check
    python tools/generate_signal_bed_audio.py --ffmpeg C:/path/to/ffmpeg.exe \\
        --output src/main/resources/assets/thefourthfrequency/sounds/signal
"""

from __future__ import annotations

import argparse
import math
import random
import struct
import subprocess
import sys
import tempfile
import wave
from pathlib import Path

RATE = 44_100

# Bed loops sit far below the event sounds on purpose: they must be deniable. A player
# should notice them only once they stop, or once a second layer joins them.
BED_PEAK = 10 ** (-24.0 / 20.0)
DEAD_AIR_PEAK = 10 ** (-32.0 / 20.0)
CUE_PEAK = 10 ** (-9.0 / 20.0)
ALERT_PEAK = 10 ** (-6.0 / 20.0)

# Broadband noise is the hardest thing Vorbis has to encode, and these are the files the
# player hears for longest, so the beds get more bits than the one-shots do. At q5 the hiss
# developed an audible pumping as the encoder ran out of room for the noise floor.
BED_QUALITY = 7
CUE_QUALITY = 5

# Mutually coprime in tenths of a second: 113, 137, 179, 191 are all prime. See module docs.
CARRIER_SECONDS = 11.65
STATIC_SECONDS = 14.05
TAPE_HISS_SECONDS = 18.25
DEAD_AIR_SECONDS = 19.60


def channel_seed(base: int, channel: int) -> int:
    """Decorrelates a recipe's noise between the two channels.

    Feeding both channels the same seed would produce two identical signals, which is a mono
    file that costs twice as much to store. Different noise in each ear is what makes hiss
    sound like a space rather than a dot.
    """
    return base + channel * 7_919


def smoothstep(edge0: float, edge1: float, value: float) -> float:
    progress = max(0.0, min(1.0, (value - edge0) / (edge1 - edge0)))
    return progress * progress * (3.0 - 2.0 * progress)


def seamless(samples: list[float], fade_seconds: float = 0.35) -> list[float]:
    """Cross-fade the tail over the head so the loop wraps without a click.

    Returns a list shortened by the fade length: the removed tail has been mixed into the
    new head, so the last sample now flows directly into the first.
    """
    total = len(samples)
    fade = int(RATE * fade_seconds)
    if fade <= 0 or fade * 2 >= total:
        return list(samples)
    blended = []
    for index in range(fade):
        progress = index / fade
        blended.append(samples[total - fade + index] * (1.0 - progress)
                       + samples[index] * progress)
    blended.extend(samples[fade:total - fade])
    return blended


def carrier_loop(duration: float = CARRIER_SECONDS, channel: int = 0) -> list[float]:
    """A mains-frequency hum with its harmonics: the sound of equipment being powered.

    This is the bed that should be running whenever the terminal is doing something the
    player cannot see. It is almost featureless by design - the horror is that it never
    quite stops.
    """
    rng = random.Random(channel_seed(0x43415252, channel))
    samples: list[float] = []
    drift = 0.0
    # Integer cycle counts over the loop length keep every partial phase-aligned at the wrap.
    base_cycles = round(49.5 * duration)
    # A tonal bed cannot be widened by decorrelating its noise, because the tone dominates.
    # A few degrees of phase between the channels is what gives a steady hum its body.
    skew = channel * 0.11
    for index in range(int(RATE * duration)):
        time = index / RATE
        drift = drift * 0.9995 + (rng.random() * 2.0 - 1.0) * 0.0005
        phase = 2.0 * math.pi * base_cycles * time / duration + skew
        signal = math.sin(phase) * 0.60
        signal += math.sin(phase * 2.0 + 0.7) * 0.22
        signal += math.sin(phase * 3.0 + 1.9) * 0.10
        signal += math.sin(phase * 5.0 + 0.3) * 0.04
        # A slow breathing envelope, three cycles per loop, so it never sits perfectly still.
        breath = 1.0 + 0.10 * math.sin(2.0 * math.pi * 3.0 * time / duration)
        samples.append((signal * breath + drift * 0.35))
    return seamless(samples)


def static_loop(duration: float = STATIC_SECONDS, channel: int = 0) -> list[float]:
    """Filtered white noise with slow swells - an empty channel between stations."""
    rng = random.Random(channel_seed(0x53544154, channel))
    samples: list[float] = []
    low = 0.0
    high = 0.0
    for index in range(int(RATE * duration)):
        time = index / RATE
        white = rng.random() * 2.0 - 1.0
        # Two one-pole filters in series shape the raw white noise into something closer to
        # the band-limited hiss of an analogue receiver rather than a digital noise burst.
        low = low * 0.55 + white * 0.45
        high = high * 0.86 + low * 0.14
        band = low - high * 0.72
        swell = 0.70 + 0.30 * math.sin(2.0 * math.pi * 2.0 * time / duration + 0.4)
        swell *= 0.88 + 0.12 * math.sin(2.0 * math.pi * 5.0 * time / duration)
        samples.append(band * swell)
    return seamless(samples)


def tape_hiss_loop(duration: float = TAPE_HISS_SECONDS, channel: int = 0) -> list[float]:
    """High-frequency hiss with wow and flutter: the sound of a tape that is still running."""
    rng = random.Random(channel_seed(0x54415045, channel))
    samples: list[float] = []
    hiss = 0.0
    for index in range(int(RATE * duration)):
        time = index / RATE
        white = rng.random() * 2.0 - 1.0
        hiss = hiss * 0.28 + white * 0.72
        # Wow (slow) and flutter (fast) are what separate tape from plain noise. Both are
        # placed on integer cycle counts so the modulation itself also loops cleanly.
        wow = math.sin(2.0 * math.pi * 2.0 * time / duration) * 0.055
        flutter = math.sin(2.0 * math.pi * 47.0 * time / duration) * 0.018
        # A faint transport whine rides on top, pitch-modulated by the same wow.
        whine_cycles = round(3_150.0 * duration)
        whine = math.sin(2.0 * math.pi * whine_cycles * time / duration * (1.0 + wow * 0.02))
        samples.append(hiss * (0.80 + wow + flutter) + whine * 0.020)
    return seamless(samples)


def dead_air_loop(duration: float = DEAD_AIR_SECONDS, channel: int = 0) -> list[float]:
    """Not silence - the sound of a live channel carrying nothing.

    Paired with the silent_world anomaly, where the world's own voice is cut. Leaving true
    silence would read as a bug or a muted game; a barely-present room tone with occasional
    settling ticks reads as something still being transmitted.
    """
    rng = random.Random(channel_seed(0x44454144, channel))
    samples: list[float] = []
    rumble = 0.0
    # Left where they are in both channels on purpose. The rumble around them is decorrelated,
    # so the ticks read as something settling in the middle of a room that has width.
    ticks = ((1.7, 0.004), (4.3, 0.003), (6.1, 0.005), (8.8, 0.003),
             (12.4, 0.004), (15.9, 0.003), (18.2, 0.005))
    for index in range(int(RATE * duration)):
        time = index / RATE
        rumble = rumble * 0.9990 + (rng.random() * 2.0 - 1.0) * 0.0010
        hum_cycles = round(24.0 * duration)
        signal = math.sin(2.0 * math.pi * hum_cycles * time / duration) * 0.18
        signal += rumble * 0.85
        for tick_time, tick_width in ticks:
            age = time - tick_time
            if 0.0 <= age < tick_width * 6.0:
                signal += math.exp(-age / tick_width) * (rng.random() * 2.0 - 1.0) * 0.30
        samples.append(signal)
    return seamless(samples, 0.5)


def alert(duration: float = 2.6, channel: int = 0) -> list[float]:
    """The two-tone attention signal, at the real Emergency Alert System frequencies.

    853 Hz and 960 Hz together are the sound people already associate with "stop what you
    are doing and listen", which is exactly the authority the terminal should borrow when
    it wants the player's attention.

    Rendered mono while the beds around it are stereo. Width would place it in the room, and
    this cue is not in the room - it interrupts.
    """
    samples: list[float] = []
    for index in range(int(RATE * duration)):
        time = index / RATE
        gate = smoothstep(0.06, 0.14, time) * (1.0 - smoothstep(duration - 0.30, duration - 0.05, time))
        signal = math.sin(2.0 * math.pi * 853.0 * time) * 0.5
        signal += math.sin(2.0 * math.pi * 960.0 * time) * 0.5
        # A trace of distortion keeps it from sounding like a clean synthesised sine pair.
        signal += math.sin(2.0 * math.pi * 1_706.0 * time) * 0.035
        samples.append(signal * gate)
    return samples


def carrier_lost(duration: float = 1.8, channel: int = 0) -> list[float]:
    """A steady tone collapsing into noise and then into nothing.

    The intended use is the moment something stops transmitting - which, in this mod, should
    feel worse than something starting to.
    """
    rng = random.Random(channel_seed(0x4C4F5354, channel))
    samples: list[float] = []
    collapse_at = 0.62
    noise = 0.0
    for index in range(int(RATE * duration)):
        time = index / RATE
        white = rng.random() * 2.0 - 1.0
        noise = noise * 0.70 + white * 0.30
        if time < collapse_at:
            signal = math.sin(2.0 * math.pi * 440.0 * time) * 0.45
            signal += math.sin(2.0 * math.pi * 880.0 * time + 0.5) * 0.10
            signal += noise * 0.04
            samples.append(signal * smoothstep(0.0, 0.05, time))
        else:
            age = time - collapse_at
            # The tone slides down as it dies rather than simply fading, so it reads as the
            # transmitter failing instead of the volume being turned down.
            sag = max(0.0, 1.0 - age * 2.4)
            signal = math.sin(2.0 * math.pi * 440.0 * sag * time) * 0.45 * sag
            signal += noise * (0.55 * math.exp(-age * 2.2))
            samples.append(signal * math.exp(-age * 1.6))
    return samples


def tuning_sweep(duration: float = 3.2, channel: int = 0) -> list[float]:
    """Sweeping the dial past several stations that are not quite there.

    Each 'station' is a resonant peak the sweep passes through, none of them resolving into
    anything. It is the sound of looking for a signal and finding the band occupied by
    things that will not identify themselves.
    """
    rng = random.Random(channel_seed(0x54554E45, channel))
    samples: list[float] = []
    stations = (0.44, 0.97, 1.63, 2.28, 2.81)
    noise = 0.0
    for index in range(int(RATE * duration)):
        time = index / RATE
        white = rng.random() * 2.0 - 1.0
        noise = noise * 0.62 + white * 0.38
        signal = noise * 0.22
        for order, station_time in enumerate(stations):
            distance = abs(time - station_time)
            if distance > 0.20:
                continue
            presence = math.exp(-(distance / 0.07) ** 2)
            tone = 320.0 + order * 190.0
            signal += math.sin(2.0 * math.pi * tone * time) * presence * 0.30
            # A partial that is deliberately not harmonically related keeps each station
            # from sounding musical.
            signal += math.sin(2.0 * math.pi * tone * 1.37 * time + 0.9) * presence * 0.12
            signal += noise * presence * 0.35
        envelope = smoothstep(0.0, 0.10, time) * (1.0 - smoothstep(duration - 0.22, duration, time))
        samples.append(signal * envelope)
    return samples


# name, recipe, peak, is_loop, channels, quality
RECIPES = (
    ("carrier_loop", carrier_loop, BED_PEAK, True, 2, BED_QUALITY),
    ("static_loop", static_loop, BED_PEAK, True, 2, BED_QUALITY),
    ("tape_hiss_loop", tape_hiss_loop, BED_PEAK, True, 2, BED_QUALITY),
    ("dead_air_loop", dead_air_loop, DEAD_AIR_PEAK, True, 2, BED_QUALITY),
    ("alert", alert, ALERT_PEAK, False, 1, CUE_QUALITY),
    ("carrier_lost", carrier_lost, CUE_PEAK, False, 2, CUE_QUALITY),
    ("tuning_sweep", tuning_sweep, CUE_PEAK, False, 2, CUE_QUALITY),
)


def render(recipe, channels: int) -> list[list[float]]:
    return [recipe(channel=channel) for channel in range(channels)]


def write_wave(path: Path, channels: list[list[float]], peak: float) -> None:
    """Interleave and write. Both channels share one scale factor.

    Normalising each channel to its own peak would silently re-balance the stereo image
    towards whichever side happened to contain the loudest sample.
    """
    maximum = max(1e-9, max(abs(value) for channel in channels for value in channel))
    scale = peak / maximum
    frames = min(len(channel) for channel in channels)
    pcm = b"".join(
        struct.pack("<h", int(max(-1.0, min(1.0, channel[frame] * scale)) * 32767))
        for frame in range(frames)
        for channel in channels
    )
    with wave.open(str(path), "wb") as stream:
        stream.setnchannels(len(channels))
        stream.setsampwidth(2)
        stream.setframerate(RATE)
        stream.writeframes(pcm)


def self_check() -> int:
    """Validate every recipe without needing ffmpeg. Catches NaN, silence and loop seams."""
    failures = 0
    for name, recipe, _peak, is_loop, channels, _quality in RECIPES:
        rendered = render(recipe, channels)
        failed = False
        lengths = {len(channel) for channel in rendered}
        if len(lengths) != 1:
            print(f"FAIL {name}: channels differ in length {sorted(lengths)}")
            failures += 1
            continue
        for channel, samples in enumerate(rendered):
            if not samples:
                print(f"FAIL {name} ch{channel}: produced no samples")
                failed = True
                break
            if any(math.isnan(value) or math.isinf(value) for value in samples):
                print(f"FAIL {name} ch{channel}: contains NaN or infinity")
                failed = True
                break
            if max(abs(value) for value in samples) < 1e-6:
                print(f"FAIL {name} ch{channel}: effectively silent")
                failed = True
                break
            if is_loop:
                # The wrap discontinuity must be no worse than a typical step inside the body,
                # otherwise the loop will click audibly every time it repeats.
                seam = abs(samples[0] - samples[-1])
                window = min(2_000, len(samples) - 1)
                typical = max(abs(samples[i + 1] - samples[i]) for i in range(window))
                if seam > max(typical * 4.0, 0.01):
                    print(f"FAIL {name} ch{channel}: loop seam {seam:.5f} "
                          f"exceeds interior step {typical:.5f}")
                    failed = True
                    break
        if failed:
            failures += 1
            continue
        peak = max(abs(value) for channel in rendered for value in channel)
        detail = f"{len(rendered[0]) / RATE:6.2f}s  {channels}ch  peak {peak:.4f}"
        if channels == 2:
            # Identical channels would mean the stereo render bought nothing but file size.
            spread = sum(abs(a - b) for a, b in zip(rendered[0], rendered[1])) / len(rendered[0])
            if spread < 1e-9:
                print(f"FAIL {name}: channels are identical, stereo is wasted")
                failures += 1
                continue
            detail += f"  spread {spread:.4f}"
        print(f"ok   {name:16s} {detail}")
    return failures


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--self-check", action="store_true",
                        help="validate the generators and exit; needs no ffmpeg")
    parser.add_argument("--ffmpeg", type=Path, help="path to ffmpeg, required to emit .ogg")
    parser.add_argument("--output", type=Path, help="directory to write the .ogg files into")
    parser.add_argument("--wav-only", action="store_true",
                        help="write .wav into --output instead of encoding, for use without ffmpeg")
    args = parser.parse_args()

    if args.self_check:
        sys.exit(self_check())
    if args.output is None:
        parser.error("--output is required unless --self-check is given")
    if not args.wav_only and args.ffmpeg is None:
        parser.error("--ffmpeg is required unless --wav-only or --self-check is given")

    args.output.mkdir(parents=True, exist_ok=True)
    if args.wav_only:
        for name, recipe, peak, _is_loop, channels, _quality in RECIPES:
            destination = args.output / f"{name}.wav"
            write_wave(destination, render(recipe, channels), peak)
            print(destination)
        return

    with tempfile.TemporaryDirectory(prefix="tff-signal-bed-") as temporary:
        temporary = Path(temporary)
        for name, recipe, peak, _is_loop, channels, quality in RECIPES:
            wave_path = temporary / f"{name}.wav"
            destination = args.output / f"{name}.ogg"
            write_wave(wave_path, render(recipe, channels), peak)
            subprocess.run([
                str(args.ffmpeg), "-y", "-hide_banner", "-loglevel", "error",
                "-i", str(wave_path), "-ac", str(channels), "-ar", str(RATE),
                "-c:a", "libvorbis", "-q:a", str(quality), str(destination),
            ], check=True)
            print(destination)


if __name__ == "__main__":
    main()
