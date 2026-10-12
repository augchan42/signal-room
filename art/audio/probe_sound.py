"""Disposable Signal Room synth-timbre probe. No source audio is sampled."""

from __future__ import annotations

import math
import wave
from pathlib import Path

import numpy as np
from scipy.signal import butter, sosfilt


RATE = 32000
BPM = 100
BEAT = 60 / BPM
BAR = 4 * BEAT
LENGTH = 4 * BAR
RNG = np.random.default_rng(118)
DEST = Path("/private/tmp/signal-room-sound-probe.wav")


def hz(midi: int) -> float:
    return 440 * 2 ** ((midi - 69) / 12)


def low(signal: np.ndarray, cutoff: float, order: int = 3) -> np.ndarray:
    return sosfilt(butter(order, cutoff, fs=RATE, output="sos"), signal)


def high(signal: np.ndarray, cutoff: float, order: int = 2) -> np.ndarray:
    return sosfilt(butter(order, cutoff, btype="highpass", fs=RATE, output="sos"), signal)


def notesaw(note: int, seconds: float, detune: float = 0.0) -> np.ndarray:
    size = int(seconds * RATE)
    t = np.arange(size) / RATE
    f = hz(note)
    result = np.zeros(size)
    for harmonic in range(1, min(28, int(14000 / f))):
        amp = 1 / harmonic ** 1.15
        result += amp * (np.sin(2 * np.pi * f * harmonic * t)
                         + 0.55 * np.sin(2 * np.pi * f * (1 + detune) * harmonic * t + 0.27))
    return result / 2.4


def put(bus: np.ndarray, mono: np.ndarray, at: float, gain: float = 1, pan: float = 0) -> None:
    start = round(at * RATE)
    if start >= len(bus):
        return
    end = min(start + len(mono), len(bus))
    if end <= start:
        return
    bus[start:end, 0] += mono[:end-start] * gain * math.sqrt((1 - pan) / 2)
    bus[start:end, 1] += mono[:end-start] * gain * math.sqrt((1 + pan) / 2)


def amp_env(size: int, attack: float, decay: float, sustain: float, release: float) -> np.ndarray:
    t = np.arange(size) / RATE
    held = max(attack + decay, len(t) / RATE - release)
    env = np.where(t < attack, np.clip(t / max(attack, 1e-4), 0, 1),
                   sustain + (1 - sustain) * np.exp(-np.maximum(t - attack, 0) / max(decay, 1e-4)))
    env *= np.clip((len(t) / RATE - t) / max(release, 1e-4), 0, 1)
    return env


def kick() -> np.ndarray:
    t = np.arange(int(.45 * RATE)) / RATE
    f = 46 + 115 * np.exp(-t * 31)
    phase = 2 * np.pi * np.cumsum(f) / RATE
    click = high(RNG.standard_normal(len(t)), 3000) * np.exp(-t * 90)
    return np.sin(phase) * np.exp(-t * 11) + .10 * click


def snare() -> np.ndarray:
    t = np.arange(int(.38 * RATE)) / RATE
    white = RNG.standard_normal(len(t))
    noise = high(low(white, 9500), 900)
    body = np.sin(2 * np.pi * 184 * t) * np.exp(-t * 18)
    return .55 * noise * np.exp(-t * 16) + .23 * body


def hat(opened: bool = False) -> np.ndarray:
    t = np.arange(int((.25 if opened else .09) * RATE)) / RATE
    noise = high(RNG.standard_normal(len(t)), 6000)
    return .24 * noise * np.exp(-t * (19 if opened else 65))


def wet(bus: np.ndarray, delay: float, amount: float, feedback: float = .27) -> np.ndarray:
    out = bus.copy()
    shift = round(delay * RATE)
    for repeat in range(1, 5):
        level = amount * feedback ** (repeat - 1)
        out[shift*repeat:, 0] += bus[:-shift*repeat, 1] * level
        out[shift*repeat:, 1] += bus[:-shift*repeat, 0] * level
    return out


def make_section(style: int) -> np.ndarray:
    # One shared original phrase isolates timbre/production differences.
    n = int((LENGTH + 1.4) * RATE)
    drums = np.zeros((n, 2))
    bass = np.zeros((n, 2))
    pads = np.zeros((n, 2))
    hooks = np.zeros((n, 2))
    roots = [38, 34, 41, 36]  # Dm–Bb–F–C; not taken from any reference track.
    chords = [(50, 53, 57), (46, 50, 53), (53, 57, 60), (48, 52, 55)]
    motif = [(0, 65, .75), (1.0, 69, .38), (1.5, 72, .38),
             (2.5, 69, .55), (3.3, 67, .55)]

    for bar, root in enumerate(roots):
        base = bar * BAR
        for note_index, note in enumerate(chords[bar]):
            duration = BAR + .9
            t = np.arange(int(duration * RATE)) / RATE
            voice = notesaw(note, duration, .0032)
            voice = low(voice, [1350, 2100, 3600][style])
            voice *= amp_env(len(t), [.22, .10, .045][style], .55, .67, .65)
            put(pads, voice, base, [.080, .060, .050][style], [-.5, 0, .5][note_index])
        bass_beats = ([0, 2], [0, 1.5, 2.5], [0, .75, 1.5, 2, 2.75, 3.5])[style]
        for beat in bass_beats:
            duration = .36 if style == 2 else .58
            t = np.arange(int(duration * RATE)) / RATE
            glide = 1 + .07 * np.exp(-t * 23)
            phase = 2 * np.pi * hz(root) * np.cumsum(glide) / RATE
            body = .76 * np.sin(phase) + .24 * np.sin(2 * phase)
            edge = low(notesaw(root + 12, duration, .002), [440, 660, 850][style])
            sound = np.tanh((body + .31 * edge) * 1.65) * amp_env(len(t), .006, .18, .57, .10)
            put(bass, sound, base + beat * BEAT, .22)

        # The same hook appears in all three mixes. The patch changes, not the notes.
        for beat, note, held in motif:
            duration = held * BEAT + .32
            t = np.arange(int(duration * RATE)) / RATE
            lead = notesaw(note + (12 if style == 2 else 0), duration, .004)
            lead = low(lead, [2100, 3300, 4600][style])
            lead *= amp_env(len(t), .015, .22, .42, .27)
            put(hooks, lead, base + beat * BEAT, [.075, .088, .064][style], .18)
        if style == 2:
            for step in range(8):
                note = chords[bar][step % 3] + 12
                duration = .22
                t = np.arange(int(duration * RATE)) / RATE
                pluck = low(notesaw(note, duration, .002), 5200) * np.exp(-t * 17)
                put(hooks, pluck, base + step * BEAT / 2, .060, -.36)

        for beat in range(4):
            if style == 2 or beat in (0, 2):
                put(drums, kick(), base + beat * BEAT, .28 if style == 2 else .24)
            if beat in (1, 3):
                put(drums, snare(), base + beat * BEAT, [.18, .23, .25][style])
            put(drums, hat(), base + beat * BEAT, [.10, .12, .13][style], -.28)
            if style > 0:
                put(drums, hat(opened=(style == 2 and beat == 3)), base + (beat + .5) * BEAT,
                    .080 if style == 1 else .12, .24)

    # Continuous stereo ambience and timed echoes fill the gaps in the old renderer.
    pads = wet(pads, .071, .30, .60)
    hooks = wet(hooks, BEAT * .75, [.21, .28, .37][style])
    drums = wet(drums, .091, .10, .20)
    for channel in range(2):
        pads[:, channel] = low(pads[:, channel], [2300, 3700, 4900][style])
    mix = drums + bass + pads + hooks
    mix = np.tanh(mix * 1.55)
    mix[:int(.02*RATE)] *= np.linspace(0, 1, int(.02*RATE))[:, None]
    mix[-int(.6*RATE):] *= np.linspace(1, 0, int(.6*RATE))[:, None]
    return mix


sections = [make_section(style) for style in range(3)]
gap = np.zeros((int(.45 * RATE), 2))
joined = np.concatenate([sections[0], gap, sections[1], gap, sections[2]])
joined /= max(1, np.max(np.abs(joined)) / .89)
with wave.open(str(DEST), "wb") as output:
    output.setnchannels(2)
    output.setsampwidth(2)
    output.setframerate(RATE)
    output.writeframes(np.rint(joined * 32767).astype("<i2").tobytes())
print(f"{DEST}: {len(joined)/RATE:.2f}s, three sections start at 0, {len(sections[0])/RATE+.45:.2f}, {2*(len(sections[0])/RATE+.45):.2f}")
