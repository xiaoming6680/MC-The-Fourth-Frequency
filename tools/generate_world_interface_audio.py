#!/usr/bin/env python3
"""Create and validate the original 44.1 kHz world-interface sound library.

Almost everything here is mono, and has to be: Minecraft will not apply 3D attenuation to a
stereo buffer, so any cue the player is meant to locate in the arena must stay single
channel. The three `ambient_form_*` phase beds are the exception. They are played by
WorldInterfacePresentationController as non-positional loops with attenuation disabled, so
they were never being positioned in the first place - mono only guaranteed they collapsed
into the centre of the player's head for the entire fight.

Those three are also the only sounds here that run continuously. At the original 6.0s they
repeated a couple of hundred times over a three-phase encounter, which is far past the point
where a loop stops being ambience and starts being a sample. They are now long enough, and
detuned per-tick by the client, that the wrap stops being something you can count.

Every cue used to be two to four lines of "sine + white noise + envelope", peaking at -7 dBFS
and sitting around -13 dBFS RMS. That gap is crest factor, and crest factor - not timbre - is
why the fight had no weight: a bare sine spends most of its time far below its own peak, so
it measures loud and sounds thin. The layering below exists to fill that gap. Each cue is
built from transient / sub / body / tear layers, put through a soft saturator *before*
normalisation so the RMS comes up while the peak does not, and placed in a room by a
Schroeder reverb so it stops sounding like it is inside the player's headphones.

Dependencies are numpy and soundfile only, loaded from build/tff-audio-tooling:

    pip install --target build/tff-audio-tooling numpy soundfile

Deliberately no scipy: every primitive below is expressible in numpy, and the ones that look
like they need an IIR solver (the comb and allpass sections) have a block-parallel exact form
because their feedback distance is a constant D. Those are exact, not approximations.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import math
import os
import subprocess
import sys
import tempfile
import time
from pathlib import Path

TOOLING = Path(__file__).resolve().parents[1] / "build" / "tff-audio-tooling"
sys.path.insert(0, str(TOOLING))

import numpy as np  # type: ignore  # installed only into build tooling
import soundfile as sf  # type: ignore


RATE = 44_100
ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT / "src/main/resources/assets/thefourthfrequency/sounds/world_interface"
MANIFEST = ROOT / "docs/art/world_interface/audio_manifest.json"

# The phase beds: non-positional, so they may be stereo, and long-running, so they must be
# long. Every other group keeps its mono contract because the arena has to place it.
AMBIENT_SECONDS: dict[str, float] = {
    "ambient_form_1": 18.7,
    "ambient_form_2": 22.9,
    "ambient_form_3": 26.3,
}

# ---------------------------------------------------------------------------------------
# The summon ceremony's clock.
#
# WorldInterfaceSummonTimeline drives a 260-tick entrance on the server. The rise cue is 6.5s
# long and its fifth layer is a single downbeat, so where that downbeat falls decides whether
# the ceremony reads as scored or as two things happening near each other. It is stated here
# in absolute seconds rather than as a fraction of the duration, because the tick it has to
# land on is fixed by the timeline and the duration is not.
#
# Both sides are asserted equal by WorldInterfaceSummonTimelineTest, which parses this file.
# Changing either number alone is a test failure rather than a silent drift.
#
# Every variant in the `summon` group shares this duration and this downbeat, and that is a
# hard requirement rather than a convenience: Minecraft picks a variant at random from
# sounds.json, so the server cannot know which one it started. Variants may differ in timbre;
# they may not differ in where the beat lands.
# ---------------------------------------------------------------------------------------
SUMMON_SECONDS = 6.5
SUMMON_DOWNBEAT_SECONDS = 5.5  # == WorldInterfaceSummonTimeline.GROUND_BREAK / 20.0

# Group -> (kind, variant count). This table is the single source of truth for the library:
# the manifest, the file-count check and ResourceContractTest all derive from it rather than
# restating a total. Variant counts must match sounds.json exactly.
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
    # The five groups below shipped as orphan assets: files on disk that no generator could
    # reproduce, and - not coincidentally - the five that carry the most force in the fight.
    "hurt": ("hurt", 3),
    "death": ("death", 1),
    "laser_fire": ("discharge", 3),
    "impact": ("impact", 3),
    "form_shift": ("shift", 2),
    # Beats that previously played nothing at all. See ModSounds.
    "shockwave": ("impact", 3),
    "combat_start": ("rise", 1),
    "eviction": ("mental", 2),
    "lance": ("warning", 3),
    "lance_impact": ("impact", 3),
    "flight": ("shift", 2),
}

# Fallback length per kind, overridden per group below. Stating it per group is what lets
# `arrow` and `laser` share a synth while staying different lengths.
KIND_SECONDS: dict[str, float] = {
    "pulse": 1.60, "device": 1.30, "chime": 2.40, "rise": 2.20, "tear": 4.00,
    "warning": 1.40, "impact": 1.60, "mental": 2.60, "hurt": 0.55, "death": 7.00,
    "discharge": 2.20, "shift": 2.40, "loop": 6.00,
}
GROUP_SECONDS: dict[str, float] = {
    "terminal": 1.40,
    "success": 4.50,
    "failure": 5.00,
    "arrow": 1.10,
    "hotbar": 1.40,
    "grab": 1.60,
    "expulsion": 3.00,
    "eviction": 3.00,
    "shockwave": 2.20,
    "lance_impact": 1.90,
    "combat_start": 2.80,
    "flight": 2.00,
    "summon": SUMMON_SECONDS,
}

# Loops stay at -7 dBFS because they run under everything else for an entire phase. One-shot
# combat cues go to -3 dBFS: they are the events, and they were being mixed as if they were
# background. The validation branch reads this same table.
PEAK_BY_KIND: dict[str, float] = {kind: 10.0 ** (-3.0 / 20.0) for kind in KIND_SECONDS}
PEAK_BY_KIND["loop"] = 10.0 ** (-7.0 / 20.0)

# The machine-checkable form of "this has impact". A cue that drifts back towards a thin sine
# fails here rather than shipping. Only asserted for the kinds whose whole job is force.
MIN_RMS_DBFS: dict[str, float] = {"impact": -18.0, "discharge": -18.0, "death": -18.0}

# Vorbis is lossy, and a lossy codec does not preserve peak: the decoded signal overshoots
# what was encoded, by one to two decibels here and most on the steepest transients - so the
# rise cues, whose whole point is a hard downbeat, overshoot worst. PEAK_BY_KIND is a
# statement about what the player hears, so it is enforced against the *decoded* file and the
# encoder is driven to hit it (see `encode_to_peak`) rather than being given an allowance to
# drift inside. This remains only as the tolerance on that convergence.
# Half a decibel: well under the ~1 dB a listener can pick out, and tight enough that the
# library stays level with itself. Overshoot is not quite proportional - the encoder's bit
# allocation shifts with level, so a pure ratio correction can oscillate on the steepest
# transients - hence the damping factor and the best-of-attempts fallback in `encode_to_peak`.
PEAK_TOLERANCE_DB = 0.5
ENCODE_ATTEMPTS = 6
ENCODE_DAMPING = 0.8

# Saturation drive per kind. tanh(x*d)/tanh(d) raises RMS without raising peak, which is
# where loudness actually comes from.
DRIVE_BY_KIND: dict[str, float] = {
    "pulse": 1.4, "device": 1.3, "chime": 1.2, "rise": 1.9, "tear": 2.0,
    "warning": 1.5, "impact": 1.6, "mental": 1.5, "hurt": 1.7, "death": 2.4,
    "discharge": 2.2, "shift": 1.8, "loop": 1.0,
}


def stable_seed(name: str, variant: int) -> int:
    return int.from_bytes(hashlib.sha256(f"{name}:{variant}".encode()).digest()[:8], "big")


def seconds_for(name: str, kind: str) -> float:
    if kind == "loop":
        return AMBIENT_SECONDS.get(name, KIND_SECONDS["loop"])
    return GROUP_SECONDS.get(name, KIND_SECONDS[kind])


# =======================================================================================
# Primitives. Pure numpy, no scipy.
# =======================================================================================


def envelope(count: int, attack: float, release: float) -> np.ndarray:
    attack_samples = max(1, min(count, round(RATE * attack)))
    release_samples = max(1, min(count, round(RATE * release)))
    env = np.ones(count, dtype=np.float64)
    env[:attack_samples] *= np.linspace(0.0, 1.0, attack_samples, endpoint=True)
    env[-release_samples:] *= np.linspace(1.0, 0.0, release_samples, endpoint=True)
    return env


def swept_sine(count: int, f_start: float, f_end: float, tau: float,
               phase: float = 0.0) -> np.ndarray:
    """Exponentially swept sine by phase integration - exact and O(N), no per-sample loop.

    Frequency is what decays, not amplitude: this is the falling pitch of something heavy
    arriving, and integrating it with cumsum is what keeps the phase continuous across the
    sweep. Summing sin(2*pi*f(t)*t) instead - the shape the old generator used - produces a
    chirp whose instantaneous frequency is wrong by a factor of two at the end.
    """
    t = np.arange(count, dtype=np.float64) / RATE
    frequency = f_end + (f_start - f_end) * np.exp(-t / max(1e-6, tau))
    return np.sin(math.tau * np.cumsum(frequency) / RATE + phase)


def one_pole_lp(x: np.ndarray, cutoff: float) -> np.ndarray:
    """One-pole lowpass as a truncated exponential kernel, convolved in numpy's C layer."""
    coefficient = math.exp(-math.tau * max(1.0, cutoff) / RATE)
    length = max(2, min(len(x), int(5.0 / max(1e-9, 1.0 - coefficient))))
    kernel = (1.0 - coefficient) * coefficient ** np.arange(length, dtype=np.float64)
    return np.convolve(x, kernel, mode="full")[:len(x)]


def one_pole_hp(x: np.ndarray, cutoff: float) -> np.ndarray:
    return x - one_pole_lp(x, cutoff)


def sweep_lp(x: np.ndarray, f_start: float, f_end: float, blocks: int = 48) -> np.ndarray:
    """Lowpass whose cutoff glides across the buffer, done blockwise.

    Blocks are crossfaded rather than butted together, because a hard cut between two
    different filter states is itself a click - and a click is exactly what a sweep is
    supposed to be smoothing away.
    """
    count = len(x)
    if count < blocks * 4:
        return one_pole_lp(x, (f_start + f_end) * 0.5)
    out = np.zeros(count, dtype=np.float64)
    weight = np.zeros(count, dtype=np.float64)
    edges = np.linspace(0, count, blocks + 1).astype(int)
    for index in range(blocks):
        start, stop = edges[index], edges[index + 1]
        pad = min(start, (stop - start))
        progress = index / max(1, blocks - 1)
        # Geometric interpolation: pitch and cutoff are both perceived logarithmically.
        cutoff = f_start * (f_end / max(1e-6, f_start)) ** progress
        # Filtering from `pad` samples early gives the one-pole its history back, so the block
        # starts from the state it would have had rather than from silence.
        out[start:stop] += one_pole_lp(x[start - pad:stop], cutoff)[pad:]
        weight[start:stop] += 1.0
    return out / np.maximum(1e-9, weight)


def comb(x: np.ndarray, delay: int, gain: float) -> np.ndarray:
    """Feedback comb, y[n] = x[n] + g*y[n-D].

    Exact, not approximate: the dependency distance is exactly D, so a block of D samples has
    no internal self-dependency and can be added vectorised against the block before it.
    """
    y = x.astype(np.float64, copy=True)
    for start in range(delay, len(y), delay):
        stop = min(start + delay, len(y))
        y[start:stop] += gain * y[start - delay:stop - delay]
    return y


def allpass(x: np.ndarray, delay: int, gain: float) -> np.ndarray:
    """Schroeder allpass, y[n] = -g*x[n] + x[n-D] + g*y[n-D]. Same blocking argument."""
    y = np.zeros_like(x, dtype=np.float64)
    head = min(delay, len(x))
    y[:head] = -gain * x[:head]
    for start in range(delay, len(x), delay):
        stop = min(start + delay, len(x))
        y[start:stop] = (-gain * x[start:stop] + x[start - delay:stop - delay]
                         + gain * y[start - delay:stop - delay])
    return y


def reverb(x: np.ndarray, t60: float, wet: float) -> np.ndarray:
    """Four parallel combs into two series allpasses - the classic Schroeder topology.

    This single function is why the library stops sounding like it is inside the player's
    head. Delays are prime sample counts so the comb resonances never line up into a ringing
    pitch, and the four gains are solved from the requested T60 rather than guessed.
    """
    if wet <= 0.0:
        return x
    combs = (1237, 1381, 1607, 1789)
    wet_signal = np.zeros_like(x, dtype=np.float64)
    for delay in combs:
        gain = 10.0 ** (-3.0 * delay / (RATE * max(1e-3, t60)))
        wet_signal += comb(x, delay, min(0.92, gain))
    wet_signal /= len(combs)
    for delay, gain in ((225, 0.7), (556, 0.7)):
        wet_signal = allpass(wet_signal, delay, gain)
    return x * (1.0 - wet) + wet_signal * wet


def transient(count: int, rng, milliseconds: float = 3.0, cutoff: float = 2000.0) -> np.ndarray:
    """Full-band noise spike. On a laptop speaker this is the only part that is audible."""
    length = max(1, min(count, round(RATE * milliseconds / 1000.0)))
    burst = np.zeros(count, dtype=np.float64)
    burst[:length] = rng.normal(0.0, 1.0, length) * np.linspace(1.0, 0.0, length)
    return one_pole_hp(burst, cutoff)


def sub_impact(count: int, f_start: float, f_end: float, tau: float,
               phase: float = 0.0) -> np.ndarray:
    """The chest layer: a sine falling from f_start to f_end. Every old impact lacked this."""
    t = np.arange(count, dtype=np.float64) / RATE
    return swept_sine(count, f_start, f_end, tau, phase) * np.exp(-t / max(1e-6, tau * 2.4))


def metal_body(count: int, f0: float, decays: tuple[float, ...],
               ratios: tuple[float, ...] = (1.0, 1.71, 2.34, 3.07, 4.19),
               phase: float = 0.0) -> np.ndarray:
    """Inharmonic partial stack. The ratios are deliberately not integers: an integer stack
    reads as a musical note, and this has to read as tens of metres of struck metal."""
    t = np.arange(count, dtype=np.float64) / RATE
    out = np.zeros(count, dtype=np.float64)
    for index, ratio in enumerate(ratios[:len(decays)]):
        out += (np.sin(math.tau * f0 * ratio * t + phase / (index + 1.0))
                * np.exp(-t * decays[index]) / (1.0 + index * 0.55))
    return out


def noise_tear(count: int, rng, f_start: float, f_end: float) -> np.ndarray:
    """Noise through a sweeping band: tearing, discharge, structural failure."""
    return sweep_lp(rng.normal(0.0, 1.0, count), f_start, f_end)


def saturate(x: np.ndarray, drive: float) -> np.ndarray:
    if drive <= 1.0:
        return x
    return np.tanh(x * drive) / math.tanh(drive)


def finalize(layers: list[np.ndarray], drive: float, peak_target: float) -> np.ndarray:
    """Saturate, then normalise. The order matters and is not interchangeable: normalising
    first and clipping afterwards would push the peak back over target."""
    total = np.zeros(max(len(layer) for layer in layers), dtype=np.float64)
    for layer in layers:
        total[:len(layer)] += layer
    shaped = saturate(total, drive)
    return shaped * (peak_target / max(1e-9, float(np.abs(shaped).max())))


# =======================================================================================
# Recipes.
# =======================================================================================


def synth(kind: str, name: str, variant: int, channel: int = 0) -> np.ndarray:
    """Render one channel, unnormalised and unsaturated at the layer level."""
    seed = stable_seed(name, variant)
    rng = np.random.default_rng(seed)
    duration = seconds_for(name, kind)
    count = round(RATE * duration)
    t = np.arange(count, dtype=np.float64) / RATE
    base = 36.0 + (seed % 43) + variant * 5.0
    phase = (seed % 997) / 997.0 * math.tau
    # A tonal bed cannot be widened by decorrelating noise the way hiss can, because the
    # partials dominate. Offsetting their phase is what gives a steady drone width.
    phase += channel * 0.37
    layers: list[np.ndarray]

    if kind == "loop":
        # Integer cycle counts make the first and last samples meet without a seam. Every
        # layer added here must keep that property: any frequency has to be k/duration.
        cycles = 180 + seed % 240
        f1, f2, f3 = cycles / duration, (cycles * 2 + 7) / duration, (cycles * 3 + 11) / duration
        sub_cycles = max(1, round(38.0 * duration)) / duration
        signal = (np.sin(math.tau * f1 * t + phase) * 0.46
                  + np.sin(math.tau * f2 * t) * 0.22
                  + np.sin(math.tau * f3 * t + phase * 0.4) * 0.13
                  # A whole-cycle sub layer: the beds had no bottom at all, so the arena read
                  # as quiet between cues however loud the mix was.
                  + np.sin(math.tau * sub_cycles * t + phase * 0.7) * 0.30)
        signal *= 0.72 + 0.20 * np.sin(math.tau * 2.0 * t / duration) ** 2
        return signal

    if kind == "pulse":
        layers = [
            transient(count, rng, 3.0, 1800.0) * 0.55,
            sub_impact(count, base * 1.4, base * 0.55, 0.16, phase) * 0.85,
            metal_body(count, base * 2.0, (4.2, 3.0, 2.2, 1.6, 1.1), phase=phase) * 0.32,
        ]
        layers.append(reverb(layers[0] + layers[2], 0.9, 0.26))
        return sum(layers) * envelope(count, 0.004, duration * 0.30)

    if kind == "device":
        # Was a 0.48s click. A mechanism that takes a weapon out of your hands should be
        # audibly a mechanism: it spins up, engages, and releases.
        charge = np.sin(math.tau * (base * 3.0 + 240.0 * t / duration) * t + phase)
        charge *= np.clip(t / (duration * 0.55), 0.0, 1.0) ** 1.6
        gate = (np.sin(math.tau * (7.0 + variant * 1.5 + 18.0 * t / duration) * t) > 0.1)
        clicks = (rng.random(count) > 0.988).astype(np.float64) * rng.uniform(-1, 1, count)
        layers = [
            charge * 0.34 * gate,
            clicks * 0.30,
            transient(count, rng, 4.0, 2600.0) * 0.5,
            metal_body(count, base * 4.0, (9.0, 6.5, 4.5), phase=phase) * 0.26,
            sub_impact(count, 96.0, 44.0, 0.10, phase) * 0.42,
        ]
        body = sum(layers)
        return (body + reverb(body, 0.55, 0.22)) * envelope(count, 0.003, duration * 0.22)

    if kind == "chime":
        # Inharmonic, so the anchors stop sounding like a wind chime and start sounding like
        # a structure ringing.
        tone = metal_body(count, base, (1.4, 1.1, 0.9, 0.7, 0.55),
                          ratios=(1.0, 1.83, 2.41, 3.62, 5.11), phase=phase)
        layers = [
            tone,
            transient(count, rng, 2.0, 3200.0) * 0.30,
            sub_impact(count, base * 0.9, base * 0.42, 0.30, phase) * 0.34,
        ]
        body = sum(layers)
        return reverb(body, min(3.2, duration * 0.75), 0.34) * envelope(count, 0.006, duration * 0.45)

    if kind == "rise":
        # Shepard stack: three octaves rotating through a raised-cosine window, so the tone
        # reads as rising without end rather than as a siren that has to reset.
        rise = np.clip(t / duration, 0.0, 1.0)
        shepard = np.zeros(count, dtype=np.float64)
        for octave in range(3):
            position = (rise + octave / 3.0) % 1.0
            frequency = 42.0 * 2.0 ** (position * 3.0)
            window = 0.5 - 0.5 * np.cos(math.tau * position)
            shepard += np.sin(math.tau * np.cumsum(frequency) / RATE + phase + octave) * window
        drone = swept_sine(count, 24.0, 41.0, duration * 0.7, phase)
        drone *= 1.0 + 0.18 * np.sin(math.tau * 0.7 * t)  # slow beating against itself
        debris = np.zeros(count, dtype=np.float64)
        hits = 40 if duration > 4.0 else 14
        for index in range(hits):
            # Density rises with t, so the approach accelerates rather than merely gets louder.
            at = int(count * (index / hits) ** 1.7)
            length = min(count - at, round(RATE * 0.22))
            if length <= 8:
                continue
            debris[at:at + length] += metal_body(
                length, 90.0 + rng.random() * 220.0, (7.0, 5.0, 3.5)) * (0.25 + 0.5 * index / hits)
        riser = noise_tear(count, rng, 200.0, 6000.0) * rise ** 1.4
        layers = [drone * 0.85, shepard * 0.34, debris * 0.5, riser * 0.28]
        # The downbeat. For `summon` this is the beat the whole entrance is cut to, so it is
        # placed in absolute seconds; for shorter rise cues it lands proportionally.
        beat_at = SUMMON_DOWNBEAT_SECONDS if name == "summon" else duration * 0.85
        beat_sample = min(count - 1, round(RATE * beat_at))
        tail = count - beat_sample
        if tail > 64:
            hit = (sub_impact(tail, 55.0, 20.0, 0.26, phase) * 1.0
                   + transient(tail, rng, 4.0, 1500.0) * 0.8)
            hit = hit + reverb(hit, 2.4, 0.5)
            beat = np.zeros(count, dtype=np.float64)
            beat[beat_sample:] = hit
            layers.append(beat)
        return sum(layers) * envelope(count, 0.06, min(0.30, duration * 0.12))

    if kind == "tear":
        # Reverse swell first: the impact time-reversed, reverbed, and reversed back, which
        # is how you get an inhale that points at a specific moment.
        swell_length = min(count, round(RATE * min(0.6, duration * 0.2)))
        drop = sub_impact(count, 70.0, 18.0, 0.35, phase)
        swell = np.zeros(count, dtype=np.float64)
        seed_piece = drop[:swell_length][::-1]
        swell[:swell_length] = reverb(seed_piece, 1.4, 0.55)[::-1]
        tearing = np.zeros(count, dtype=np.float64)
        for centre in (400.0, 1100.0, 2600.0):
            walk = np.cumsum(rng.normal(0.0, 1.0, count)) / RATE * 40.0
            band = one_pole_hp(one_pole_lp(rng.normal(0.0, 1.0, count), centre * 1.7), centre * 0.55)
            tearing += band * (0.6 + 0.4 * np.sin(walk))
        cracks = np.zeros(count, dtype=np.float64)
        crack_count = 6 + int(rng.random() * 5)
        for index in range(crack_count):
            at = int(count * (0.15 + 0.75 * index / crack_count))
            length = min(count - at, round(RATE * 0.5))
            if length <= 8:
                continue
            # Falling fundamental per crack: shell breaking, not a drum roll.
            cracks[at:at + length] += metal_body(
                length, 210.0 - index * 18.0, (5.5, 3.8, 2.6, 1.9)) * 0.6
        layers = [swell * 0.7, tearing * 0.30, cracks * 0.5, drop * 0.95]
        body = sum(layers)
        return reverb(body, 2.2, 0.42) * envelope(count, 0.01, duration * 0.16)

    if kind == "warning":
        # Stays dry, short and tailless on purpose: this is the information the player dodges
        # by. `discharge` is the one with the tail. Confusing the two is why aiming and firing
        # used to sound the same.
        rate = 3.0 + variant * 0.4
        gate = (np.sin(math.tau * (rate + 9.0 * t / duration) * t) > 0.25).astype(np.float64)
        square = np.sign(np.sin(math.tau * base * 0.75 * t + phase))
        layers = [
            np.sin(math.tau * (base * 4.0 + 65.0 * t) * t + phase) * gate * 0.7,
            square * 0.22 * gate,
            transient(count, rng, 2.0, 3000.0) * 0.35,
            np.sin(math.tau * base * t) * 0.30,
        ]
        return sum(layers) * envelope(count, 0.008, duration * 0.14)

    if kind == "impact":
        layers = [
            transient(count, rng, 3.0, 2000.0) * 1.0,
            sub_impact(count, 78.0, 32.0, 0.09, phase) * 0.95,
            metal_body(count, 140.0, (3.2, 2.1, 1.6, 1.1, 0.8), phase=phase) * 0.35,
            noise_tear(count, rng, 8000.0, 400.0) * 0.5,
        ]
        # Reverb on everything except the sub: putting a room on 30 Hz is how a hit turns
        # into mud.
        return sum(layers) + reverb(layers[0] + layers[2] + layers[3], 1.1, 0.28)

    if kind == "discharge":
        pre = np.zeros(count, dtype=np.float64)
        pre_length = min(count, round(RATE * 0.03))
        pre[:pre_length] = (one_pole_hp(rng.normal(0.0, 1.0, pre_length), 900.0)
                            * np.linspace(0.0, 1.0, pre_length))
        # FM, not a filtered sweep: the sidebands a falling carrier throws off through a
        # collapsing modulation index are what reads as electrical rather than as a whistle.
        zap_length = min(count, round(RATE * 0.18))
        carrier_hz = 90.0 + (620.0 - 90.0) * np.exp(-np.arange(zap_length) / RATE / 0.05)
        modulator = np.sin(math.tau * np.cumsum(carrier_hz * 3.7) / RATE + phase)
        index_env = np.linspace(6.0, 0.0, zap_length)
        zap = np.zeros(count, dtype=np.float64)
        zap[:zap_length] = np.sin(math.tau * np.cumsum(carrier_hz) / RATE
                                  + modulator * index_env)
        gate = (np.sign(np.sin(math.tau * 90.0 * t)) > 0).astype(np.float64)
        crackle = one_pole_hp(rng.normal(0.0, 1.0, count), 3000.0) * gate * np.exp(-t * 3.0)
        layers = [
            pre * 0.6,
            zap * 0.9,
            crackle * 0.42,
            sub_impact(count, 60.0, 24.0, 0.22, phase) * 0.95,
        ]
        body = sum(layers)
        return body + reverb(body - layers[3], 1.6, 0.35)

    if kind == "hurt":
        layers = [
            transient(count, rng, 2.5, 2400.0) * 0.9,
            metal_body(count, 190.0, (7.0, 5.2, 3.8), phase=phase) * 0.55,
            sub_impact(count, 65.0, 40.0, 0.07, phase) * 0.8,
        ]
        body = sum(layers)
        return (body + reverb(body, 0.4, 0.22)) * envelope(count, 0.001, duration * 0.35)

    if kind == "shift":
        # Two resonances travelling apart: a shell separating rather than a single sweep.
        upper = swept_sine(count, 320.0, 1450.0, duration * 0.55, phase)
        lower = swept_sine(count, 300.0, 74.0, duration * 0.55, phase * 0.5)
        inhale = noise_tear(count, rng, 300.0, 5200.0) * np.clip(t / (duration * 0.6), 0, 1) ** 1.5
        close_at = int(count * 0.72)
        closing = np.zeros(count, dtype=np.float64)
        if count - close_at > 64:
            closing[close_at:] = swept_sine(count - close_at, 1400.0, 220.0, 0.35)
        layers = [upper * 0.30, lower * 0.62, inhale * 0.26, closing * 0.34,
                  sub_impact(count, 84.0, 30.0, 0.20, phase) * 0.55]
        body = sum(layers)
        return reverb(body, 1.5, 0.30) * envelope(count, 0.02, duration * 0.22)

    if kind == "death":
        layers = [sub_impact(count, 90.0, 18.0, 0.30, phase) * 1.0]
        # Twelve partial stacks entering 0.4s apart, each falling on its own: a structure
        # coming down section by section rather than one explosion.
        cascade = np.zeros(count, dtype=np.float64)
        for index in range(12):
            at = round(RATE * 0.4 * index)
            length = count - at
            if length <= 64:
                break
            cascade[at:] += metal_body(
                length, 260.0 - index * 16.0,
                (2.4, 1.8, 1.3, 0.9, 0.7), phase=phase + index) * (0.62 - index * 0.03)
        layers.append(cascade * 0.55)
        layers.append(noise_tear(count, rng, 5000.0, 120.0) * 0.34)
        layers.append(swept_sine(count, 40.0, 14.0, duration * 0.8, phase) * 0.45)
        body = sum(layers)
        body = body + reverb(body, 3.5, 0.45)
        # Forced silence at the end: it has to stop, not fade out under the next thing.
        tail = min(count, round(RATE * 0.6))
        body[-tail:] *= np.linspace(1.0, 0.0, tail) ** 1.5
        return body

    # mental: two close partials beating against each other, an infrasonic pulse, and noise
    # shaped by vowel formants - perception being interfered with, not an object being struck.
    formants = np.zeros(count, dtype=np.float64)
    for centre, weight in ((520.0, 1.0), (1180.0, 0.6), (2500.0, 0.35)):
        formants += one_pole_hp(one_pole_lp(rng.normal(0.0, 1.0, count), centre * 1.25),
                                centre * 0.8) * weight
    layers = [
        (np.sin(math.tau * (base * 5.13) * t + np.sin(t * 31.0) * 2.2)
         + np.sin(math.tau * (base * 5.31) * t + phase)) * 0.32,
        np.sin(math.tau * 1.8 * t) ** 9 * 0.34,
        formants * 0.26,
        sub_impact(count, 58.0, 26.0, duration * 0.35, phase) * 0.40,
    ]
    body = sum(layers)
    return (body + reverb(body, 2.0, 0.32)) * envelope(count, 0.10, duration * 0.24)


def render(kind: str, name: str, variant: int) -> np.ndarray:
    """Saturate, normalise, and stack into stereo for the groups allowed to be wide.

    Both channels share one scale factor: normalising each to its own peak would quietly
    re-balance the image towards whichever side happened to hold the loudest sample.
    """
    channels = 2 if name in AMBIENT_SECONDS else 1
    peak_target = PEAK_BY_KIND[kind]
    drive = DRIVE_BY_KIND[kind]
    rendered = [saturate(synth(kind, name, variant, channel), drive) for channel in range(channels)]
    maximum = max(1.0e-9, max(float(np.max(np.abs(signal))) for signal in rendered))
    scaled = [(signal * (peak_target / maximum)).astype(np.float32) for signal in rendered]
    return scaled[0] if channels == 1 else np.stack(scaled, axis=-1)


# The phase beds are the only files here long enough to trip it, but libsndfile's bundled
# Vorbis encoder aborts the process outright - no exception, just a native crash - when asked
# to write a stereo OGG past roughly 300k frames. Those go out through ffmpeg instead, which
# also means their bitrate can be stated rather than left to the library default.
AMBIENT_QUALITY = 6


def encode_via_ffmpeg(ffmpeg: Path, samples: np.ndarray, destination: Path) -> None:
    with tempfile.TemporaryDirectory(prefix="tff-world-interface-") as temporary:
        intermediate = Path(temporary) / "bed.wav"
        sf.write(intermediate, samples, RATE, subtype="PCM_16")
        subprocess.run([
            str(ffmpeg), "-y", "-hide_banner", "-loglevel", "error",
            "-i", str(intermediate), "-ar", str(RATE),
            "-c:a", "libvorbis", "-q:a", str(AMBIENT_QUALITY), str(destination),
        ], check=True)


def dbfs(value: float) -> float:
    return 20.0 * math.log10(max(1e-9, value))


def retry_io(action, attempts: int = 5):
    """Run a filesystem action, retrying the transient failures Windows produces here.

    Writing eighty-odd small files in a tight loop, several times each, reliably trips either
    a bare LibsndfileError "System error" or an OSError EINVAL on this platform - a handle
    from the previous pass, or an on-access scanner, still holding the path. Both clear within
    a few hundred milliseconds. A real fault still surfaces once the attempts are spent.
    """
    for attempt_index in range(attempts):
        try:
            return action()
        except (OSError, sf.LibsndfileError):
            if attempt_index == attempts - 1:
                raise
            time.sleep(0.25 * (attempt_index + 1))
    raise AssertionError("unreachable")


def encode_to_peak(samples: np.ndarray, destination: Path, stereo: bool,
                   peak_target: float, ffmpeg: Path) -> tuple[float, float, object]:
    """Encode until the decoded peak lands on target, and report what the file measures.

    Normalising the buffer and encoding once does not produce a file that peaks where it was
    normalised to; every measurement that matters is taken after the codec has had its say, so
    that is what gets driven. Overshoot is close to proportional, which makes this converge in
    two passes - the loop only exists so an unusually steep transient cannot ship off-target.
    """
    # Every pass writes to a scratch file rather than to `destination`. Repeatedly reopening
    # one path for write on Windows intermittently fails with a bare "System error" while a
    # previous handle is still being released, and a half-written OGG left at the real path
    # would be indistinguishable from a good one on the next run.
    def attempt(scratch: Path, scale: float):
        scaled = (samples * scale).astype(np.float32)
        if stereo:
            encode_via_ffmpeg(ffmpeg, scaled, scratch)
        else:
            sf.write(scratch, scaled, RATE, format="OGG", subtype="VORBIS")
        info = sf.info(scratch)
        decoded, sample_rate = sf.read(scratch, dtype="float32", always_2d=True)
        if sample_rate != RATE:
            raise RuntimeError(f"rate changed on encode: {scratch} {sample_rate}")
        peak = float(np.max(np.abs(decoded)))
        rms = float(np.sqrt(np.mean(np.square(decoded.astype(np.float64)))))
        return peak, rms, info

    # Scratch files live beside the destination rather than in the system temp directory, so
    # the final move is a same-volume rename: atomic, and never a cross-drive copy that an
    # on-access virus scanner can interrupt halfway.
    scratch_dir = destination.parent
    written = []
    try:
        scale = 1.0
        best = None
        for index in range(ENCODE_ATTEMPTS):
            scratch = scratch_dir / f".{destination.stem}.pass{index}.ogg"
            written.append(scratch)
            peak, rms, info = retry_io(lambda: attempt(scratch, scale))
            error = abs(dbfs(peak) - dbfs(peak_target))
            if best is None or error < best[0]:
                best = (error, scratch, peak, rms, info)
            if error <= PEAK_TOLERANCE_DB:
                best = (error, scratch, peak, rms, info)
                break
            correction = peak_target / max(1e-9, peak)
            scale *= 1.0 + (correction - 1.0) * ENCODE_DAMPING
        # Keep the closest attempt, not whichever one the loop happened to end on.
        _, scratch, peak, rms, info = best
        retry_io(lambda: os.replace(scratch, destination))
        written.remove(scratch)
    finally:
        for leftover in written:
            leftover.unlink(missing_ok=True)
    return peak, rms, info


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--ffmpeg", type=Path,
                        help="path to ffmpeg, required to encode the stereo phase beds")
    parser.add_argument("--only", help="regenerate a single group, for iterating on one recipe")
    args = parser.parse_args()
    if args.ffmpeg is None:
        parser.error("--ffmpeg is required: the stereo phase beds cannot go through libsndfile")

    OUTPUT.mkdir(parents=True, exist_ok=True)
    selected = {args.only: GROUPS[args.only]} if args.only else GROUPS
    expected = sum(count for _, count in selected.values())
    generated = 0
    manifest: dict[str, object] = {
        "note": "GENERATED by tools/generate_world_interface_audio.py. Do not edit by hand.",
        "summonSeconds": SUMMON_SECONDS,
        "summonDownbeatSeconds": SUMMON_DOWNBEAT_SECONDS,
        "groups": {},
    }
    for name, (kind, variants) in selected.items():
        group = OUTPUT / name
        group.mkdir(parents=True, exist_ok=True)
        entries = []
        for variant in range(1, variants + 1):
            destination = group / f"{variant:02d}.ogg"
            expected_channels = 2 if name in AMBIENT_SECONDS else 1
            rendered = render(kind, name, variant)
            peak, rms, info = encode_to_peak(rendered, destination, expected_channels == 2,
                                             PEAK_BY_KIND[kind], args.ffmpeg)
            if destination.read_bytes()[:4] != b"OggS" or info.channels != expected_channels:
                raise RuntimeError(f"invalid OGG contract: {destination} {info}")
            if abs(dbfs(peak) - dbfs(PEAK_BY_KIND[kind])) > PEAK_TOLERANCE_DB:
                raise RuntimeError(
                    f"peak off target: {destination} {dbfs(peak):+.2f} dBFS, wanted "
                    f"{dbfs(PEAK_BY_KIND[kind]):+.1f} dBFS "
                    f"(+/-{PEAK_TOLERANCE_DB}) after {ENCODE_ATTEMPTS} encode passes")
            if peak >= 1.0:
                raise RuntimeError(f"clipped: {destination} reached full scale")
            floor = MIN_RMS_DBFS.get(kind)
            if floor is not None and dbfs(rms) < floor:
                raise RuntimeError(
                    f"not enough weight: {destination} rms={dbfs(rms):.2f} dBFS, "
                    f"kind '{kind}' requires >= {floor:.1f}. A cue that measures this thin has "
                    f"drifted back towards a bare sine.")
            entries.append({"variant": variant, "seconds": round(info.duration, 3),
                            "channels": expected_channels,
                            "peakDbfs": round(dbfs(peak), 2), "rmsDbfs": round(dbfs(rms), 2)})
            generated += 1
            print(f"{destination.relative_to(ROOT)} rate={RATE} "
                  f"channels={expected_channels} peak={dbfs(peak):+.2f}dBFS "
                  f"rms={dbfs(rms):+.2f}dBFS seconds={info.duration:.2f}")
        manifest["groups"][name] = {  # type: ignore[index]
            "kind": kind, "variants": variants,
            "seconds": round(seconds_for(name, kind), 3), "files": entries,
        }
    if generated != expected:
        raise RuntimeError(f"expected {expected} sounds, generated {generated}")
    if not args.only:
        manifest["fileCount"] = generated
        manifest["groupCount"] = len(GROUPS)
        MANIFEST.parent.mkdir(parents=True, exist_ok=True)
        MANIFEST.write_text(json.dumps(manifest, indent=2, sort_keys=False) + "\n",
                            encoding="utf-8")
        print(f"\n{len(GROUPS)} groups, {generated} files -> {MANIFEST.relative_to(ROOT)}")


if __name__ == "__main__":
    main()
