"""Bright 20-second Signal Room cue for listening approval.

Original composition, 120 BPM, A minor, 2-bar intro + 8-bar phrase. Every sound is
synthesized here; no samples or reference audio. Writes a wet and a dry WAV.
"""

from __future__ import annotations

import argparse
import json
import math
import wave
from pathlib import Path

import numpy as np
from scipy.signal import butter, fftconvolve, lfilter, resample_poly, sosfilt


RATE = 48000
BPM = 120
BEAT = 60 / BPM
BAR = 4 * BEAT
STEP = BEAT / 4
FRAMES = 960_000  # exactly 20.000 s
RNG = np.random.default_rng(2026)

# Am F | Am F C G | Am F G Am  (bars 1-2 intro, 3-10 phrase)
CHORDS = ["Am", "F", "Am", "F", "C", "G", "Am", "F", "G", "Am"]
PAD = {"Am": [57, 60, 64, 69], "F": [57, 60, 65, 69],
       "C": [55, 60, 64, 67], "G": [55, 59, 62, 67]}
ROOT = {"Am": 33, "F": 29, "C": 36, "G": 31}

# Hook as (step, length in 16ths, MIDI note) per bar. Same rhythm on every full bar so
# the ear locks onto it; pitches follow the chord.
HOOK_RHYTHM = [(0, 3), (3, 3), (6, 2), (8, 4), (12, 2), (14, 2)]
HOOK = {
    3: [76, 72, 74, 76, 79, 76],
    4: [77, 76, 72, 69, 72, 74],
    5: [79, 76, 74, 72, 74, 76],
    6: [(0, 3, 74), (3, 3, 71), (6, 2, 74), (8, 8, 79)],
    7: [(0, 6, 76), (6, 2, 74), (8, 8, 72)],
    8: [81, 77, 79, 81, 84, 81],
    9: [79, 74, 79, 83, 81, 79],
    10: [(0, 8, 81), (8, 4, 81)],
}


def hz(note: float) -> np.ndarray | float:
    return 440 * 2 ** ((np.asarray(note) - 69) / 12)


def at(bar: int, step: float = 0) -> float:
    """Seconds for 1-based bar and 16th step."""
    return (bar - 1) * BAR + step * STEP


# --- oscillators -----------------------------------------------------------

def _blep(phase: np.ndarray, dt: np.ndarray) -> np.ndarray:
    out = np.zeros_like(phase)
    a = phase < dt
    t = phase[a] / dt[a]
    out[a] = t + t - t * t - 1
    b = phase > 1 - dt
    t = (phase[b] - 1) / dt[b]
    out[b] = t * t + t + t + 1
    return out


def saw(freq: np.ndarray, phase0: float = 0.0) -> np.ndarray:
    """PolyBLEP band-limited saw; freq is per-sample Hz."""
    dt = np.asarray(freq, dtype=float) / RATE
    phase = (phase0 + np.cumsum(dt)) % 1.0
    return 2 * phase - 1 - _blep(phase, dt)


def pulse(freq: np.ndarray, width: float = .5, phase0: float = 0.0) -> np.ndarray:
    return .5 * (saw(freq, phase0) - saw(freq, (phase0 + width) % 1.0))


def drift(n: int, cents: float) -> np.ndarray:
    """Slow independent pitch wander (below 1 Hz) as a frequency multiplier, like an analog oscillator."""
    if cents <= 0:
        return np.ones(n)
    wander = sosfilt(butter(2, .7, fs=RATE, output="sos"), RNG.standard_normal(n + RATE))[RATE:]
    return 2 ** (cents * wander / (np.std(wander) + 1e-12) / 1200)


def supersaw(freq: np.ndarray, voices: int = 7, cents: float = 18,
             wander: float = 0.0) -> tuple[np.ndarray, np.ndarray]:
    """Detuned saw stack spread across stereo. Returns (left, right)."""
    left = np.zeros(len(freq))
    right = np.zeros(len(freq))
    spread = np.linspace(-1, 1, voices)
    for s in spread:
        voice = saw(freq * 2 ** (s * cents / 1200) * drift(len(freq), wander), RNG.random())
        pan = .5 + .45 * s
        g = 1.0 if s == 0 else .78
        left += voice * g * math.sqrt(1 - pan)
        right += voice * g * math.sqrt(pan)
    return left / voices * 1.6, right / voices * 1.6


# --- filters ---------------------------------------------------------------

def low(x: np.ndarray, cutoff: float, order: int = 2) -> np.ndarray:
    return sosfilt(butter(order, min(cutoff, RATE * .45), fs=RATE, output="sos"), x, axis=0)


def high(x: np.ndarray, cutoff: float, order: int = 2) -> np.ndarray:
    return sosfilt(butter(order, cutoff, btype="highpass", fs=RATE, output="sos"), x, axis=0)


def band(x: np.ndarray, lo: float, hi: float, order: int = 2) -> np.ndarray:
    return sosfilt(butter(order, [lo, min(hi, RATE * .45)], btype="bandpass", fs=RATE, output="sos"), x, axis=0)


def vlow(x: np.ndarray, cutoff: np.ndarray, q: float = .9, block: int = 32) -> np.ndarray:
    """Resonant low-pass whose cutoff follows a per-sample array (block-updated biquad)."""
    out = np.empty_like(x)
    zi = np.zeros((1, 2))
    for start in range(0, len(x), block):
        fc = float(np.clip(cutoff[min(start + block // 2, len(x) - 1)], 30, RATE * .45))
        w = 2 * math.pi * fc / RATE
        alpha = math.sin(w) / (2 * q)
        cw = math.cos(w)
        a0 = 1 + alpha
        b = np.array([(1 - cw) / 2, 1 - cw, (1 - cw) / 2]) / a0
        a = np.array([1, -2 * cw / a0, (1 - alpha) / a0])
        sos = np.concatenate([b, a])[None, :]
        out[start:start + block], zi = sosfilt(sos, x[start:start + block], zi=zi)
    return out


# --- envelopes and placement -----------------------------------------------

def adsr(n: int, a: float, d: float, s: float, r: float, gate: float) -> np.ndarray:
    t = np.arange(n) / RATE
    env = np.where(t < a, t / max(a, 1e-4), s + (1 - s) * np.exp(-(t - a) / max(d, 1e-4)))
    after = t > gate
    if after.any():
        level = env[min(int(gate * RATE), n - 1)]
        env[after] = level * np.exp(-(t[after] - gate) / max(r, 1e-4))
    return env


def put(bus: np.ndarray, sound: np.ndarray, start: float, gain: float = 1., pan: float = 0.) -> None:
    i = round(start * RATE)
    if i >= len(bus) or i < 0:
        return
    if sound.ndim == 1:
        sound = np.stack([sound * math.sqrt((1 - pan) / 2), sound * math.sqrt((1 + pan) / 2)], 1) * math.sqrt(2)
    end = min(i + len(sound), len(bus))
    bus[i:end] += sound[:end - i] * gain


def bus() -> np.ndarray:
    return np.zeros((FRAMES + RATE * 3, 2))


# --- effects ---------------------------------------------------------------

def make_ir(seconds: float, rt60: float, predelay: float, damp: float, seed: int) -> np.ndarray:
    """Stereo decorrelated noise IR; high bands die faster, like a plate."""
    rng = np.random.default_rng(seed)
    n = int(seconds * RATE)
    t = np.arange(n) / RATE
    ir = np.zeros((n, 2))
    bands = [(20, 500, 1.0), (500, 2500, .85), (2500, 7000, .55), (7000, 16000, .32)]
    for lo, hi, rt_scale in bands:
        decay = np.exp(-6.91 * t / (rt60 * rt_scale * damp if lo > 500 else rt60 * rt_scale))
        ir += band(rng.standard_normal((n, 2)), lo, hi) * decay[:, None]
    ir *= np.clip(t / .012, 0, 1)[:, None]  # soft onset
    ir = np.concatenate([np.zeros((int(predelay * RATE), 2)), ir])
    return ir / np.sqrt(np.sum(ir ** 2) / 2)


def gated_ir(seconds: float = .24, seed: int = 5) -> np.ndarray:
    rng = np.random.default_rng(seed)
    n = int(seconds * RATE)
    shape = np.ones(n)
    shape[-int(.03 * RATE):] = np.linspace(1, 0, int(.03 * RATE)) ** 2
    ir = high(low(rng.standard_normal((n, 2)), 9000), 300) * shape[:, None]
    return ir / np.sqrt(np.sum(ir ** 2) / 2)


def convolve(x: np.ndarray, ir: np.ndarray) -> np.ndarray:
    out = np.stack([fftconvolve(x[:, c], ir[:, c])[:len(x)] for c in range(2)], 1)
    return out


def chorus(x: np.ndarray, depth: float = .0025, base: float = .011, rate: float = .55, mix: float = .5) -> np.ndarray:
    n = len(x)
    t = np.arange(n) / RATE
    out = x.copy()
    for c, offset in ((0, 0.0), (1, math.pi / 2)):
        delay = base + depth * np.sin(2 * math.pi * rate * t + offset)
        out[:, c] = (1 - mix) * x[:, c] + mix * np.interp(np.arange(n) - delay * RATE, np.arange(n), x[:, 1 - c])
    return out


def pingpong(x: np.ndarray, delay: float, feedback: float, tone: float = 3500) -> np.ndarray:
    d = round(delay * RATE)
    mono = low(x.mean(1), tone)
    out = np.zeros_like(x)
    level = 1.0
    for k in range(1, 7):
        if d * k >= len(x):
            break
        out[d * k:, (k - 1) % 2] += mono[:-d * k] * level
        level *= feedback
    return out


def duck_curve(kicks: list[float], depth: float, release: float = .16) -> np.ndarray:
    g = np.ones(FRAMES + RATE * 3)
    t = np.arange(int(BEAT * RATE)) / RATE
    shape = np.minimum(t / .004, 1) * 0 + 1  # instant-ish duck
    shape = 1 - depth * np.exp(-t / release) * np.clip(1 - t / BEAT, 0, 1)
    shape[:int(.003 * RATE)] = np.linspace(1, shape[int(.003 * RATE)], int(.003 * RATE))
    for k in kicks:
        i = round(k * RATE)
        end = min(i + len(shape), len(g))
        g[i:end] = np.minimum(g[i:end], shape[:end - i])
    return g[:, None]


# --- drums -----------------------------------------------------------------

def kick() -> np.ndarray:
    t = np.arange(int(.55 * RATE)) / RATE
    f = 47 + 150 * np.exp(-t * 34) + 40 * np.exp(-t * 300)
    body = np.sin(2 * math.pi * np.cumsum(f) / RATE) * np.exp(-t * 9)
    click = band(RNG.standard_normal(len(t)), 1800, 7000) * np.exp(-t * 260)
    k = np.tanh(1.6 * body) * .9 + .3 * click
    return k * np.clip(1 - (t - .45) / .1, 0, 1)


def snare() -> np.ndarray:
    t = np.arange(int(.32 * RATE)) / RATE
    body = (np.sin(2 * math.pi * 185 * t) + .6 * np.sin(2 * math.pi * 330 * t)) * np.exp(-t * 28)
    noise = band(RNG.standard_normal(len(t)), 1200, 11000) * np.exp(-t * 15)
    clap = np.zeros(len(t))
    for k, off in enumerate((0, .009, .019)):
        i = int(off * RATE)
        burst = band(RNG.standard_normal(len(t) - i), 900, 3200) * np.exp(-(t[:len(t) - i]) * (90 if k < 2 else 22))
        clap[i:] += burst
    return np.tanh(.45 * body + .55 * noise + .35 * clap)


HAT_FREQS = np.array([205.3, 304.4, 369.6, 522.7, 540.0, 800.0]) * 1.9


def hat(seconds: float) -> np.ndarray:
    n = int(seconds * RATE)
    t = np.arange(n) / RATE
    metal = sum(np.sign(np.sin(2 * math.pi * f * t + RNG.random() * 6)) for f in HAT_FREQS)
    tone = band(metal + .6 * RNG.standard_normal(n), 7000, 16000, order=3)
    return tone * np.exp(-t / (seconds / 5.5)) * .55


def crash() -> np.ndarray:
    n = int(2.2 * RATE)
    t = np.arange(n) / RATE
    metal = sum(np.sign(np.sin(2 * math.pi * f * 1.37 * t + RNG.random() * 6)) for f in HAT_FREQS)
    x = high(.5 * metal + RNG.standard_normal(n), 3500)
    env = np.exp(-t * 1.9) * np.clip(t / .002, 0, 1)
    return np.stack([x, np.roll(x, 37)], 1) * env[:, None] * .45


def riser(seconds: float) -> np.ndarray:
    n = int(seconds * RATE)
    t = np.arange(n) / RATE
    fc = 500 * (16 ** (t / seconds))
    noise = RNG.standard_normal((n, 2))
    out = np.stack([vlow(noise[:, c], fc, q=2.2) for c in range(2)], 1)
    out = high(out, 250)
    return out * ((t / seconds) ** 2)[:, None] * .5


def impact() -> np.ndarray:
    t = np.arange(int(1.4 * RATE)) / RATE
    f = 32 + 40 * np.exp(-t * 9)
    return np.sin(2 * math.pi * np.cumsum(f) / RATE) * np.exp(-t * 2.6) * .9


def tom(note: int) -> np.ndarray:
    t = np.arange(int(.35 * RATE)) / RATE
    f = hz(note) * (1 + .5 * np.exp(-t * 25))
    return np.sin(2 * math.pi * np.cumsum(f) / RATE) * np.exp(-t * 9) + .15 * band(RNG.standard_normal(len(t)), 800, 5000) * np.exp(-t * 40)


# --- synth parts -----------------------------------------------------------

def mono_line(events: list[tuple[float, float, int]], total: int, glide: float = .012) -> tuple[np.ndarray, np.ndarray]:
    """Per-sample frequency (with portamento) and a list of note-on indices."""
    freq = np.full(total, hz(events[0][2]))
    for start, _, note in events:
        freq[round(start * RATE):] = hz(note)
    # one-pole glide
    a = math.exp(-1 / (glide * RATE))
    freq = lfilter([1 - a], [1, -a], freq, zi=[freq[0] * a])[0]
    return freq, np.array([round(s * RATE) for s, _, _ in events])


POLISH = False  # analog drift, accents, ladder filter, saturation, ensemble chorus, less fizz


def lead_events() -> list[tuple[float, float, int]]:
    events = []
    for bar, spec in HOOK.items():
        notes = spec if isinstance(spec[0], tuple) else [(s, l, n) for (s, l), n in zip(HOOK_RHYTHM, spec)]
        for s, l, n in notes:
            nudge = RNG.normal(0, .004) if POLISH and s % 4 else 0.0
            events.append((at(bar, s) + nudge, l * STEP, n))
    return events


def accent(start: float, dur: float) -> float:
    """0.6-1.0: downbeats and long notes played harder, with small random variation."""
    on_beat = abs(start / BEAT - round(start / BEAT)) < .02
    return float(np.clip(.76 + .14 * on_beat + .08 * (dur >= 4 * STEP) + RNG.normal(0, .04), .6, 1))


def mono_gate(events, total: int, attack: float, decay: float, sustain: float, release: float):
    """Gate envelope plus per-sample time-since-note-on and note length for a mono voice."""
    gate = np.zeros(total)
    since = np.full(total, 10.0)
    length = np.zeros(total)
    vel = np.ones(total)
    for start, dur, _ in events:
        i = round(start * RATE)
        held = dur * .92
        n = int((held + release * 3) * RATE)
        end = min(i + n, total)
        v = accent(start, dur) if POLISH else 1.0
        gate[i:end] = adsr(n, attack, decay, sustain, release, held)[:end - i] * v ** 1.5
        since[i:end] = np.arange(end - i) / RATE
        length[i:end] = dur
        vel[i:end] = v
    return gate, since, length, vel


def vibrato(since: np.ndarray, length: np.ndarray, depth: float, rate: float = 5.4) -> np.ndarray:
    t = np.arange(len(since)) / RATE
    amount = np.clip((since - .2) / .3, 0, 1) * (length >= 4 * STEP)
    return 1 + depth * amount * np.sin(2 * math.pi * rate * t)


def lead_supersaw(events, total, voices: int = 5, cents: float = 9, base: float = 2800):
    """First-render supersaw kept bright, without the slide and slow resonant sweep."""
    freq, _ = mono_line(events, total, glide=.004)
    gate, since, length, vel = mono_gate(events, total, .005, .25, .8, .09)
    f = freq * vibrato(since, length, .004)
    cut = base + 3200 * np.exp(-since / .05)
    left, right = supersaw(f, voices=voices, cents=cents, wander=4 if POLISH else 0)
    under = saw(f / 2)
    if POLISH:
        cut = cut * (.7 + .45 * vel)
        return np.stack([ladder(x + .3 * under, cut) for x in (left, right)], 1) * gate[:, None]
    return np.stack([vlow(x + .3 * under, cut, .7) for x in (left, right)], 1) * gate[:, None]


def ladder(x: np.ndarray, cut: np.ndarray) -> np.ndarray:
    """Driven 4-pole low-pass: two cascaded biquads after soft saturation."""
    return vlow(vlow(np.tanh(1.4 * x) / 1.4, cut, .54), cut, .75)


def pwm(freq: np.ndarray, width: np.ndarray, phase0: float = 0.0) -> np.ndarray:
    dt = freq / RATE
    a = (phase0 + np.cumsum(dt)) % 1.0
    b = (a + width) % 1.0
    return .5 * ((2 * a - 1 - _blep(a, dt)) - (2 * b - 1 - _blep(b, dt)))


def lead_square(events, total):
    """Hollow pulse lead with slow pulse-width movement, two detuned layers left and right."""
    freq, _ = mono_line(events, total, glide=.003)
    gate, since, length, vel = mono_gate(events, total, .004, .3, .85, .12)
    f = freq * vibrato(since, length, .0045)
    t = np.arange(total) / RATE
    cut = 3000 + 1200 * np.exp(-since / .08)
    out = []
    for cents, phase in ((-5, 0.0), (5, 1.7)):
        width = .5 + .17 * np.sin(2 * math.pi * .7 * t + phase)
        x = pwm(f * 2 ** (cents / 1200), width, phase / 7)
        out.append(vlow(x + .25 * np.sin(2 * math.pi * np.cumsum(f / 2) / RATE), cut, .7))
    return np.stack(out, 1) * gate[:, None]


def lead_brass(events, total):
    """Polyphonic synth brass: each note's filter opens with a 'bwah' and settles, never closes to dull."""
    out = np.zeros((total, 2))
    for start, dur, note in events:
        held = dur * .95
        n = int((held + .3) * RATE)
        k = np.arange(n) / RATE
        cut = 800 + 3000 * (1 - np.exp(-k / .045)) - 900 * (1 - np.exp(-k / .25))
        f = np.full(n, hz(note)) * (1 + .003 * np.clip((k - .25) / .3, 0, 1) * np.sin(2 * math.pi * 5.2 * k))
        left, right = supersaw(f, voices=6, cents=9)
        body = saw(f / 2, RNG.random())
        env = adsr(n, .018, .3, .85, .14, held)
        put(out, np.stack([vlow(x + .35 * body, cut, .65) for x in (left, right)], 1) * env[:, None], start)
    return out


def lead_sync(events, total, ratio: float = 2.1, sweep: float = .5, cutoff: float = 6500,
              cents: tuple[float, ...] = (0, 7), octave: int = 0):
    """Hard-sync saw lead rendered at 2x rate then decimated, for a bright cutting edge.

    With more than two detunes the voices spread across the stereo field like a supersaw.
    """
    freq, _ = mono_line(events, total, glide=.003)
    gate, since, length, vel = mono_gate(events, total, .003, .25, .8, .09)
    f = np.repeat(freq * 2 ** octave * vibrato(since, length, .004), 2)
    shine = (.75 + .4 * vel) if POLISH else 1.0
    slave_ratio = np.repeat(ratio + sweep * shine * np.exp(-since / .07), 2)
    out = np.zeros((total, 2))
    for index, c in enumerate(cents):
        voice_f = f * np.repeat(drift(total, 3), 2) if POLISH else f
        phase = (np.cumsum(voice_f) / (2 * RATE) * 2 ** (c / 1200) + RNG.random()) % 1.0
        x = resample_poly(2 * (phase * slave_ratio % 1.0) - 1, 1, 2)[:total]
        x = low(np.tanh(1.3 * x), cutoff * .85, order=4) if POLISH else low(x, cutoff)
        pan = .5 if len(cents) == 1 else index / (len(cents) - 1)
        out[:, 0] += x * math.sqrt(1 - pan)
        out[:, 1] += x * math.sqrt(pan)
    return out / math.sqrt(len(cents)) * gate[:, None]


def phrase_rms(x: np.ndarray) -> float:
    return float(np.sqrt(np.mean(x[round(at(3) * RATE):round(at(7) * RATE)] ** 2)))


def blend(*parts: tuple[float, np.ndarray]) -> np.ndarray:
    """Mix layers at given weights after matching each layer's phrase level."""
    return sum(w * x / phrase_rms(x) for w, x in parts)


def lead_hybrid(events, total):
    return blend((.6, lead_supersaw(events, total)), (.4, lead_sync(events, total)))


def lead_supersync(events, total):
    return lead_sync(events, total, cents=(-11, -5, 0, 5, 11))


def lead_sparkle(events, total):
    return blend((.75, lead_supersaw(events, total)), (.25, lead_sync(events, total, cutoff=9000, octave=1)))


def lead_widesaw(events, total):
    return lead_supersaw(events, total, voices=7, cents=14, base=3400)


def lead_warmsync(events, total):
    soft = lead_sync(events, total, ratio=1.7, sweep=.3, cutoff=4200)
    return blend((.6, soft), (.4, lead_supersaw(events, total)))


def lead_choir(events, total):
    """Fairlight-style choir: detuned saws through 'ah' formants, slow attack, breath."""
    out = np.zeros((total, 2))
    formants = ((800, 110, 1.0), (1150, 130, .6), (2900, 200, .28), (3900, 250, .12))
    for start, dur, note in events:
        held = dur * .98
        n = int((held + .5) * RATE)
        k = np.arange(n) / RATE
        f = np.full(n, hz(note)) * (1 + .005 * np.clip((k - .15) / .3, 0, 1) * np.sin(2 * math.pi * 5 * k + RNG.random() * 6))
        left, right = supersaw(f, voices=5, cents=12)
        breath = RNG.standard_normal(n) * .08
        env = adsr(n, .05, .5, .9, .3, held)
        voices = []
        for x in (left, right):
            src = x + breath
            voices.append(sum(g * band(src, fc - bw, fc + bw) for fc, bw, g in formants))
        put(out, np.stack(voices, 1) * env[:, None], start)
    return out


LEADS = {"supersaw": lead_supersaw, "square": lead_square, "brass": lead_brass, "sync": lead_sync, "choir": lead_choir,
         "hybrid": lead_hybrid, "supersync": lead_supersync, "sparkle": lead_sparkle,
         "widesaw": lead_widesaw, "warmsync": lead_warmsync}
LEAD = "supersaw"
LEAD_WAV: Path | None = None  # dry lead rendered elsewhere (surge_lead.py), replaces the in-house lead


def read_wav(path: Path, frames: int) -> np.ndarray:
    with wave.open(str(path)) as f:
        if f.getframerate() != RATE or f.getnchannels() != 2:
            raise ValueError(f"{path}: expected {RATE} Hz stereo")
        audio = np.frombuffer(f.readframes(f.getnframes()), "<i2").reshape(-1, 2) / 32768
    out = np.zeros((frames, 2))
    out[:min(frames, len(audio))] = audio[:frames]
    return out


def finish_lead(x: np.ndarray) -> np.ndarray:
    """Tape-style saturation, fizz roll-off, 3 kHz dip, ensemble chorus."""
    level = float(np.sqrt(np.mean(x ** 2))) + 1e-12
    y = np.tanh(x * .3 / level) * level / .3
    y = low(y, 7000)
    y = y - .2 * band(y, 2500, 4000)
    return chorus(y, depth=.0018, base=.007, rate=.5, mix=.5)


def lead_part(dest: np.ndarray, doubling: np.ndarray) -> None:
    events = lead_events()
    if LEAD_WAV:
        voice = read_wav(LEAD_WAV, len(dest))
    else:
        voice = LEADS[LEAD](events, len(dest))
    if POLISH:
        voice = finish_lead(voice)
    # level-match every lead variant on bars 3-6
    phrase = voice[round(at(3) * RATE):round(at(7) * RATE)]
    dest += voice * (10 ** (-16.5 / 20) / np.sqrt(np.mean(phrase ** 2)))
    for start, dur, note in events:  # lower-octave doubling on the re-entry
        if at(8) <= start < at(10):
            n = int((dur + .2) * RATE)
            env = adsr(n, .01, .3, .8, .1, dur)
            dl, dr = supersaw(np.full(n, hz(note - 12)), voices=3, cents=6)
            put(doubling, np.stack([low(dl, 1500), low(dr, 1500)], 1) * env[:, None], start, .5)


ARP_ORDER = [0, 1, 2, 3, 2, 1, 0, 2, 3, 2, 1, 3, 0, 1, 2, 3]


def arp_part(dest: np.ndarray, every: int = 1, open_breakdown: bool = False) -> None:
    """every=2 plays every other 16th; open_breakdown keeps the arpeggio bright through bar 7."""
    for bar, name in enumerate(CHORDS, 1):
        tones = PAD[name]
        for step in range(16):
            if step % every:
                continue
            when = at(bar, step)
            # intro opens from dark to bright over bars 1-2; breakdown thins it
            if bar <= 2:
                bright = 1100 + 3400 * ((bar - 1) * 16 + step) / 32
            elif bar == 7 and not open_breakdown:
                bright = 1600 if step < 8 else 1600 + 2800 * (step - 8) / 8
            else:
                bright = 5200
            note = tones[ARP_ORDER[step]] + (12 if ARP_ORDER[step] < 2 and step % 4 == 2 else 0)
            dur = STEP * 1.6
            n = int(dur * RATE)
            k = np.arange(n) / RATE
            f = np.full(n, hz(note))
            x = .6 * saw(f, RNG.random()) + .4 * pulse(f * 1.002, .3, RNG.random())
            x = vlow(x, 500 + bright * np.exp(-k / .07), q=1.6) * np.exp(-k / .12)
            vel = (.85 if step % 4 == 0 else .7) * (1 + .08 * RNG.standard_normal())
            put(dest, x, when, .5 * vel, pan=-.25 if step % 2 else .25)


PAD_WAV: Path | None = None  # pad chords rendered elsewhere (surge_lead.py --part pad)
PAD_MOTION = False  # low-pass swell over each bar
PAD_CLEAN = False  # no kick pumping, low-mid cut, less reverb on the pad
# open voicing: root and fifth low, then 10th/9th/7th on top, kept under the lead (A4 and up)
PAD_OPEN = {"Am": [45, 52, 60, 64], "F": [41, 48, 57, 64], "C": [48, 55, 62, 64], "G": [43, 50, 59, 62]}


def pad_swell(x: np.ndarray) -> np.ndarray:
    """Close the filter at each chord change and open it over the bar; wider in the drop bars."""
    t = np.arange(len(x)) / RATE
    phase = (t % BAR) / BAR
    bar = np.minimum((t // BAR).astype(int) + 1, len(CHORDS))
    top = np.where((bar >= 3) & (bar != 7), 5200.0, 2600.0)
    cutoff = 700 + (top - 700) * np.sin(np.pi / 2 * np.minimum(phase / .8, 1))
    return np.stack([vlow(x[:, 0], cutoff, .8), vlow(x[:, 1], cutoff, .8)], 1)


def pad_part(dest: np.ndarray) -> None:
    if PAD_WAV:
        voice = read_wav(PAD_WAV, len(dest))
        if PAD_MOTION:
            voice = pad_swell(voice)
        phrase = voice[round(at(3) * RATE):round(at(7) * RATE)]
        dest += voice * (10 ** (-19.3 / 20) / np.sqrt(np.mean(phrase ** 2)))  # in-house pad level
        return
    for bar, name in enumerate(CHORDS, 1):
        dur = BAR + 1.1
        n = int(dur * RATE)
        k = np.arange(n) / RATE
        env = adsr(n, .25 if bar > 1 else .9, .8, .82, .7, BAR)
        if bar == 7:
            env *= 1.25  # breakdown: pads carry the bar
        left = np.zeros(n)
        right = np.zeros(n)
        for note in PAD[name]:
            l, r = supersaw(np.full(n, hz(note)), voices=7, cents=20)
            left += l
            right += r
        cut = 3200 + 1400 * np.sin(math.pi * k / dur)
        sound = np.stack([vlow(left, cut, .7), vlow(right, cut, .7)], 1) * env[:, None]
        put(dest, sound, at(bar), .34 if bar <= 2 else .3)


def bass_events(style: str = "full") -> list[tuple[float, float, int]]:
    """style: "full" (the approved cue), "sparse" (root and fifth on beats 1 and 3) or
    "driving" (adds 16th octave pickups before beats 3 and 1)."""
    events: list[tuple[float, float, int]] = []
    for bar, name in enumerate(CHORDS, 1):
        root = ROOT[name]
        if bar == 1:
            continue
        if bar in (2, 7):  # held, swelling
            events.append((at(bar), BAR, root))
            continue
        if style == "sparse":
            events.append((at(bar), STEP * 7, root))
            if bar < 10:
                events.append((at(bar, 8), STEP * 7, root + 7))
            continue
        if style == "driving":
            for step in (7, 15) if bar not in (6, 9, 10) else (7,):
                events.append((at(bar, step), STEP * .8, root + 12))
        for step in range(0, 16, 2):
            note = root + (12 if step % 4 == 2 else 0)
            events.append((at(bar, step), STEP * (1.6 if step % 4 == 0 else 1.2), note))
        if bar in (6, 9):  # push into next bar
            events[-1] = (at(bar, 14), STEP, root + 12)
            events.append((at(bar, 15), STEP, root + 10 if bar == 6 else root + 14))
    return sorted(events)


def bass_part(dest: np.ndarray, style: str = "full") -> None:
    events = bass_events(style)
    total = len(dest)
    freq, _ = mono_line(events, total, glide=.012)
    gate = np.zeros(total)
    filt = np.full(total, 200.0)
    for start, length, note in events:
        i = round(start * RATE)
        n = int((length + .08) * RATE)
        held = length >= BAR * .9
        env = adsr(n, .004 if not held else .6, .2, .9 if held else .55, .05, length)
        end = min(i + n, total)
        gate[i:end] = env[:end - i] * (.55 if held else 1.0)
        k = np.arange(end - i) / RATE
        filt[i:end] = (300 + 1400 * (k / length)) if held else 260 + 1900 * np.exp(-k / .07)
    body = .5 * saw(freq) + .5 * pulse(freq * 1.003, .5)
    tone = vlow(body, filt, q=1.3)
    sub = np.sin(2 * math.pi * np.cumsum(freq) / RATE)
    mono = (np.tanh(2.2 * tone) * .95 + .5 * sub) * gate
    dest += mono[:, None] * .5


# --- arrangement -----------------------------------------------------------

def drum_events(style: str = "full", fills: bool = False) -> list[tuple[str, float, float]]:
    """(kind, time, gain) for every drum hit, in the order drum_parts plays them.

    Kinds: kick, snare, roll (bar 7 crescendo and bar 2 fill), ghost, tom, hat, open_hat.
    Times are on the grid; drum_parts adds a few ms of humanising jitter to ghosts and hats.
    style: "full" (the approved cue), "sparse" (kick on beats 1 and 3, offbeat hats only, no ghosts)
    or "driving" (16th hats in the intro too). fills adds snare and tom fills in bars 4, 8 and 9
    and open hats in the sparse style.
    """
    events: list[tuple[str, float, float]] = []
    for bar in range(2, 11):
        if bar == 7:
            continue
        beats = range(4) if bar < 10 else (0, 1, 2)
        for b in beats:
            if style == "sparse" and b % 2:
                continue
            events.append(("kick", at(bar, b * 4), .72 if bar > 2 else .55))
        if bar in (5, 9) and style != "sparse":  # extra push before the bar end
            events.append(("kick", at(bar, 14), .55))
    for bar in range(3, 11):
        if bar == 10:
            events.append(("snare", at(bar, 4), .8))
            break
        if bar == 7:
            for i in range(8):  # crescendo roll into re-entry
                events.append(("roll", at(7, 8 + i), .2 + .07 * i))
            continue
        for b in (1, 3):
            events.append(("snare", at(bar, b * 4), .8))
        if style == "sparse":
            continue
        for ghost in (7, 15) if bar % 2 else (10,):
            events.append(("ghost", at(bar, ghost), .12))
    # bar 2 fill: snare 16ths then low tom
    for i, step in enumerate((12, 13, 14)):
        events.append(("roll", at(2, step), .3 + .15 * i))
    events.append(("tom", at(2, 15), .5))
    # tom fill at the end of bar 6
    for step in (12, 13, 14, 15):
        events.append(("tom", at(6, step), .45))
    if fills:
        for i, step in enumerate((12, 13, 14, 15)):  # snare run into bar 5
            events.append(("roll", at(4, step), .35 + .1 * i))
        for step in (12, 13, 14, 15):  # toms into bar 9
            events.append(("tom", at(8, step), .5))
        for i, step in enumerate(range(8, 16)):  # snare and toms into the ending
            events.append(("tom" if step % 2 else "roll", at(9, step), .4 + .05 * i))
    for bar in range(2, 11):
        for step in range(16):
            if style == "sparse" and step % 4 != 2:
                continue
            if bar == 2 and step % 2 and style != "driving":
                continue
            if bar == 7 and step % 4 != 2:
                continue
            if bar == 10 and step >= 8:
                break
            accent = 1.0 if step % 4 == 2 else (.55 if step % 2 == 0 else .38)
            opened = step % 8 == 6 and bar not in (2, 7) and (style != "sparse" or fills)
            events.append(("open_hat" if opened else "hat", at(bar, step), accent))
    return events


TOM_NOTES = {at(2, 15): 43, at(6, 12): 50, at(6, 13): 47, at(6, 14): 43, at(6, 15): 40,
             at(8, 12): 50, at(8, 13): 47, at(8, 14): 43, at(8, 15): 40,
             at(9, 9): 50, at(9, 11): 47, at(9, 13): 43, at(9, 15): 40}


def drum_parts(dry: np.ndarray, snare_bus: np.ndarray, hat_bus: np.ndarray | None = None,
               style: str = "full", fills: bool = False) -> list[float]:
    hat_bus = dry if hat_bus is None else hat_bus
    kicks: list[float] = []
    k = kick()
    s = snare()
    hats = None
    for kind, when, gain in drum_events(style, fills):
        if kind == "kick":
            put(dry, k, when, gain)
            kicks.append(when)
        elif kind == "snare":
            put(snare_bus, s, when, gain)
        elif kind == "roll":
            put(snare_bus, snare(), when, gain)
        elif kind == "ghost":
            put(dry, snare(), when + RNG.normal(0, .003), gain)
        elif kind == "tom":
            step = round((when % BAR) / STEP)
            pan = -.3 if when < at(3) else float(np.clip(.35 - .2 * (step - 12), -.5, .5))
            put(dry, tom(TOM_NOTES[when]), when, gain, pan)
        else:
            if hats is None:
                hats = hat(.06), hat(.32)
            step = round((when % BAR) / STEP)
            jitter = RNG.normal(0, .0035)
            vel = gain * (1 + .12 * RNG.standard_normal())
            if kind == "open_hat":
                put(hat_bus, hats[1], when + jitter, .26 * vel, .3)
            else:
                put(hat_bus, hats[0], when + jitter, .4 * vel, .3 if step % 2 else -.15)
    return kicks


def counter_part(dest: np.ndarray) -> None:
    """Soft half-note counterline on chord tones under the hook, for the layered arrangement."""
    for bar, name in enumerate(CHORDS, 1):
        if bar < 3 or bar in (7, 10):
            continue
        for half, index in ((0, 2), (8, 1)):
            note = PAD[name][index]
            n = int((BAR / 2 + .3) * RATE)
            env = adsr(n, .06, .4, .75, .25, BAR / 2 * .95)
            left, right = supersaw(np.full(n, hz(note)), voices=3, cents=8)
            put(dest, np.stack([low(left, 2200), low(right, 2200)], 1) * env[:, None], at(bar, half), .35)


# Keytar answers: short phrases in the gaps of the hook (bar 2 pickup, held notes in bars 6 and 7).
ANSWERS = [(2, 8, 2, 72), (2, 10, 2, 77), (2, 12, 2, 81), (2, 14, 2, 84),
           (6, 10, 1, 83), (6, 11, 1, 86), (6, 12, 2, 83), (6, 14, 2, 81),
           (7, 10, 2, 76), (7, 12, 2, 79), (7, 14, 2, 81)]


def answers_part(dest: np.ndarray) -> None:
    """Hollow pulse voice, panned right, a little under the lead's level."""
    events = [(at(bar, step), length * STEP, note) for bar, step, length, note in ANSWERS]
    voice = lead_square(events, len(dest))
    active = np.abs(voice).max(1) > 1e-4
    voice *= 10 ** (-18 / 20) / np.sqrt(np.mean(voice[active] ** 2))
    dest += voice * np.array([.6, 1.0])


def octave_part(dest: np.ndarray) -> None:
    """Thin supersaw an octave above the hook on the re-entry (bars 8-9)."""
    for start, dur, note in lead_events():
        if at(8) <= start < at(10):
            n = int((dur + .2) * RATE)
            env = adsr(n, .008, .3, .7, .1, dur)
            left, right = supersaw(np.full(n, hz(note + 12)), voices=3, cents=7)
            put(dest, np.stack([low(left, 6000), low(right, 6000)], 1) * env[:, None], start, .22)


PARTS = ("drums", "hats", "snares", "bass", "pads", "arp", "lead", "double", "counter", "answers", "octave", "fx")


def render_parts(variant: dict | None = None) -> dict[str, np.ndarray]:
    """Every part on its own bus, before effects.

    variant changes what is played: "drums" and "bass" styles, "fills", "arp_every", "arp_open",
    "answers" and "octave". With no variant this is the approved cue; the counterline, answers and
    octave parts are then silent or absent.
    """
    global RNG
    RNG = np.random.default_rng(2026)
    v = variant or {}
    parts = {name: bus() for name in PARTS}

    parts["kicks"] = np.array(drum_parts(parts["drums"], parts["snares"], parts["hats"],
                                         v.get("drums", "full"), v.get("fills", False)))
    fx = parts["fx"]
    for bar in (3, 8):
        put(fx, crash(), at(bar), .55)
        put(fx, impact(), at(bar), .55)
    put(fx, crash(), at(10, 8), .7)
    put(fx, impact(), at(10, 8), .6)
    put(fx, riser(BAR * .75), at(2, 4), .55)
    put(fx, riser(BAR / 2), at(7, 8), .6)

    bass_part(parts["bass"], v.get("bass", "full"))
    pad_part(parts["pads"])
    arp_part(parts["arp"], v.get("arp_every", 1), v.get("arp_open", False))
    lead_part(parts["lead"], parts["double"])
    # final stab on bar 10 beat 3: whole band hits the A minor chord
    stab_n = int(1.2 * RATE)
    k = np.arange(stab_n) / RATE
    stab_l = np.zeros(stab_n)
    stab_r = np.zeros(stab_n)
    for note in (45, 57, 64, 69, 72, 76):
        l, r = supersaw(np.full(stab_n, hz(note)), 7, 22)
        stab_l += l
        stab_r += r
    stab = np.stack([vlow(stab_l, 800 + 5000 * np.exp(-k / .25), 1), vlow(stab_r, 800 + 5000 * np.exp(-k / .25), 1)], 1)
    put(parts["lead"], stab * np.exp(-k / .5)[:, None], at(10, 8), .12)
    counter_part(parts["counter"])
    if v.get("answers"):
        answers_part(parts["answers"])
    if v.get("octave"):
        octave_part(parts["octave"])
    return parts


def mix_parts(parts: dict[str, np.ndarray], wet: bool, gains: dict[str, float] | None = None) -> np.ndarray:
    """Effects and master on scaled parts. gains: per-part level (default 1), plus "verb" for the plate return."""
    gains = gains or {}
    g = {name: parts[name] * gains.get(name, 1.0) for name in PARTS}
    drums = g["drums"] + g["hats"]
    snares, bass, pads, arp, lead, double, fx = (g[name] for name in ("snares", "bass", "pads", "arp", "lead", "double", "fx"))
    lead = lead + g["answers"] + g["octave"]
    kicks = list(parts["kicks"])
    ducked = duck_curve(kicks, .55)
    light_duck = duck_curve(kicks, .3)

    pads = chorus(high(pads, 150))
    pad_duck, pad_send = ducked, .45
    if PAD_CLEAN:
        # low-mid cut (-4 dB around 250-500 Hz) and a 2-5 kHz dip for the lead; light ducking; half the reverb
        pads = high(pads, 200) - .37 * band(pads, 250, 500) - .25 * band(pads, 2000, 5000)
        pad_duck, pad_send = light_duck, .22
    bass_stereo = bass * light_duck
    if gains.get("counter", 0):
        double = double + g["counter"]
    if not wet:
        mix = drums + snares + bass_stereo + pads * pad_duck + arp * ducked + lead + double * .5 + fx * .5
    else:
        plate = make_ir(2.4, 1.9, .025, .7, 11)
        room = gated_ir()
        arp_echo = pingpong(arp, BEAT * .75, .42, 3000)
        lead_echo = pingpong(lead, BEAT * .75, .32, 3800)
        sends = high(.55 * lead + .35 * arp + pad_send * pads + .25 * snares + .2 * double + .2 * arp_echo, 300)
        verb = convolve(sends, plate)
        verb = low(verb, 7000)
        mix = (drums + snares + .55 * convolve(snares, room)
               + bass_stereo
               + pads * pad_duck
               + (arp + .5 * arp_echo) * ducked
               + lead + .3 * lead_echo + double * .5
               + .5 * fx
               + .38 * gains.get("verb", 1.0) * verb * ducked)
    return master(mix)


def render(wet: bool) -> np.ndarray:
    return mix_parts(render_parts(), wet)


def master(mix: np.ndarray) -> np.ndarray:
    mix = high(mix, 28)
    # keep everything under 140 Hz centered
    mid = (mix[:, 0] + mix[:, 1]) / 2
    side = high((mix[:, 0] - mix[:, 1]) / 2, 140)
    mix = np.stack([mid + side, mid - side], 1)
    # glue compressor: 2:1 above threshold, 10 ms attack / 120 ms release on the mono level
    level = np.abs(mix).max(1)
    att, rel = math.exp(-1 / (.010 * RATE)), math.exp(-1 / (.12 * RATE))
    env = lfilter([1 - rel], [1, -rel], level)
    env = np.maximum(env, lfilter([1 - att], [1, -att], level))
    thresh = np.percentile(env[:FRAMES], 90)
    over = np.maximum(env / thresh, 1)
    gain = over ** (1 / 2 - 1)
    mix = mix * gain[:, None]
    mix = mix[:FRAMES]
    fade = int(.6 * RATE)
    mix[-fade:] *= np.linspace(1, 0, fade)[:, None] ** 1.5
    mix[:int(.005 * RATE)] *= np.linspace(0, 1, int(.005 * RATE))[:, None]
    mix /= np.max(np.abs(mix)) / .7
    return np.tanh(mix * 1.1) / math.tanh(1.1) * .9


def write(path: Path, audio: np.ndarray) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with wave.open(str(path), "wb") as out:
        out.setnchannels(2)
        out.setsampwidth(2)
        out.setframerate(RATE)
        out.writeframes(np.rint(np.clip(audio, -1, 1) * 32767).astype("<i2").tobytes())


def performance_events() -> dict:
    """Timings the Blender performance animates to (seconds from the start of the cue)."""
    return {
        "bpm": BPM,
        "seconds": FRAMES / RATE,
        "chords": [{"bar": bar, "time": at(bar), "chord": name} for bar, name in enumerate(CHORDS, 1)],
        "drums": [{"kind": kind, "time": round(when, 4), "gain": round(gain, 3)} for kind, when, gain in drum_events()],
        "crashes": [at(3), at(8), at(10, 8)],
        "lead": [{"time": round(t, 4), "dur": round(d, 4), "note": n} for t, d, n in lead_events()],
        "bass": [{"time": round(t, 4), "dur": round(d, 4), "note": n} for t, d, n in bass_events()],
        "sections": {"intro": [1, 2], "drop": [3, 6], "breakdown": [7, 7], "reentry": [8, 9], "ending": [10, 10]},
        "final_stab": at(10, 8),
    }


def main() -> None:
    global LEAD, POLISH, LEAD_WAV, PAD_WAV, PAD_MOTION, PAD_CLEAN
    parser = argparse.ArgumentParser()
    parser.add_argument("--out", type=Path, default=Path("/private/tmp/signal-room-bright-cue"))
    parser.add_argument("--lead", choices=sorted(LEADS), default=LEAD)
    parser.add_argument("--wet-only", action="store_true")
    parser.add_argument("--lead-wav", type=Path, help="use a pre-rendered dry lead instead of --lead")
    parser.add_argument("--pad-wav", type=Path, help="use pre-rendered pad chords instead of the in-house pad")
    parser.add_argument("--pad-motion", action="store_true", help="low-pass swell over each bar on a --pad-wav pad")
    parser.add_argument("--pad-clean", action="store_true", help="no kick pumping, low-mid cut, less reverb on the pad")
    parser.add_argument("--events", type=Path, help="write performance timings as JSON and exit")
    parser.add_argument("--name", help="output file stem (default from the lead)")
    parser.add_argument("--polish", action="store_true", help="analog drift, accents, ladder, saturation, chorus, less fizz")
    args = parser.parse_args()
    if args.events:
        args.events.write_text(json.dumps(performance_events(), indent=1) + "\n")
        print(args.events)
        return
    LEAD = args.lead
    POLISH = args.polish
    LEAD_WAV = args.lead_wav
    PAD_WAV = args.pad_wav
    PAD_MOTION = args.pad_motion
    PAD_CLEAN = args.pad_clean
    for wet in ((True,) if args.wet_only else (True, False)):
        name = args.name or f"bright-cue-{args.lead_wav.stem if LEAD_WAV else LEAD}" + ("-polish" if POLISH else "")
        path = args.out / (f"{name}.wav" if wet else f"{name}-dry.wav")
        write(path, render(wet))
        print(path)


if __name__ == "__main__":
    main()
