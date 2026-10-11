#!/usr/bin/env python3
"""Render the original Signal Room instrumental theme to deterministic WAV masters."""

import argparse
import json
import math
import time
import wave
from pathlib import Path

import numpy as np


HERE = Path(__file__).resolve().parent
ARRANGEMENTS = ("sparse", "driving", "layered")
MEMBERS = ("frontman", "keyboardist", "keytar", "drummer")


def frame_count(seconds: float, sample_rate: int) -> int:
    return round(seconds * sample_rate)


def tick_seconds(theme: dict, tick: int) -> float:
    return tick * 60.0 / (theme["bpm"] * theme["ppq"])


def validate_theme(theme: dict) -> list[str]:
    issues = []
    ticks_per_bar = theme["ppq"] * theme["meter"][0]
    final_tick = ticks_per_bar * theme["bars"]
    if theme["bars"] != 10 or theme["bpm"] != 120 or theme["meter"] != [4, 4]:
        issues.append("Theme must be ten bars of 4/4 at 120 BPM")
    if len(theme["chords"]) != theme["bars"]:
        issues.append("One chord event is required per bar")
    for index, chord in enumerate(theme["chords"]):
        if chord["tick"] != index * ticks_per_bar or chord["duration"] != ticks_per_bar:
            issues.append(f"chords[{index}] does not occupy exactly one bar")
        if len(chord["notes"]) < 3:
            issues.append(f"chords[{index}] has fewer than three notes")
        if not all(0 <= value <= 127 for value in chord["notes"] + [chord["bass"]]):
            issues.append(f"chords[{index}] has an invalid MIDI note")
    for track in ("melody", "answers"):
        for index, event in enumerate(theme[track]):
            if event["tick"] < 0 or event["duration"] <= 0 or event["tick"] + event["duration"] > final_tick:
                issues.append(f"{track}[{index}] falls outside the ten-bar excerpt")
            if not 0 <= event["note"] <= 127:
                issues.append(f"{track}[{index}] has an invalid MIDI note")
    return issues


def midi_hz(note: int) -> float:
    return 440.0 * 2.0 ** ((note - 69) / 12.0)


def _envelope(t: np.ndarray, held_seconds: float, attack: float, release: float) -> np.ndarray:
    rising = np.minimum(1.0, t / max(attack, 1e-5))
    falling = np.clip((held_seconds + release - t) / release, 0.0, 1.0)
    return rising * falling


def _oscillator(freq: float, t: np.ndarray, harmonics: int, slope: float) -> np.ndarray:
    signal = np.zeros(t.size, dtype=np.float64)
    ceiling = min(harmonics, max(1, int((0.46 / (t[1] - t[0])) // freq)))
    for harmonic in range(1, ceiling + 1):
        signal += np.sin(2.0 * np.pi * freq * harmonic * t) / harmonic ** slope
    return signal


def _voice(note: int, held_seconds: float, sample_rate: int, kind: str) -> np.ndarray:
    release = {"bass": 0.12, "pad": 0.30, "lead": 0.16, "arp": 0.10, "answer": 0.14}[kind]
    count = frame_count(held_seconds + release, sample_rate)
    t = np.arange(count, dtype=np.float64) / sample_rate
    freq = midi_hz(note)
    if kind == "bass":
        wave = 0.60 * _oscillator(freq, t, 8, 1.65) + 0.40 * np.sin(2 * np.pi * freq * t)
        env = _envelope(t, held_seconds, 0.006, release) * (0.68 + 0.32 * np.exp(-t * 7))
    elif kind == "pad":
        wave = (
            0.52 * np.sin(2 * np.pi * freq * t)
            + 0.24 * np.sin(2 * np.pi * freq * 1.004 * t)
            + 0.16 * np.sin(2 * np.pi * freq * 2 * t)
        )
        env = _envelope(t, held_seconds, 0.14, release)
    elif kind == "lead":
        vibrato = 1.0 + 0.0025 * np.sin(2 * np.pi * 5.3 * t)
        phase = 2 * np.pi * freq * np.cumsum(vibrato) / sample_rate
        wave = 0.70 * np.sin(phase) + 0.20 * np.sin(2 * phase) + 0.10 * np.sin(3 * phase)
        env = _envelope(t, held_seconds, 0.012, release)
    elif kind == "arp":
        wave = _oscillator(freq, t, 7, 1.55)
        env = _envelope(t, held_seconds, 0.004, release) * np.exp(-t * 4.2)
    else:
        wave = _oscillator(freq, t, 5, 1.30)
        env = _envelope(t, held_seconds, 0.007, release) * np.exp(-t * 1.5)
    return (wave * env).astype(np.float32)


def _add(mix: np.ndarray, mono: np.ndarray, start: int, gain: float, pan: float = 0.0) -> None:
    if start >= mix.shape[0] or start + mono.size <= 0:
        return
    source_start = max(0, -start)
    target_start = max(0, start)
    available = min(mono.size - source_start, mix.shape[0] - target_start)
    if available <= 0:
        return
    samples = mono[source_start:source_start + available] * gain
    mix[target_start:target_start + available, 0] += samples * math.sqrt((1.0 - pan) / 2.0)
    mix[target_start:target_start + available, 1] += samples * math.sqrt((1.0 + pan) / 2.0)


def _add_note(mix: np.ndarray, theme: dict, event: dict, kind: str, gain: float,
              sample_rate: int, pan: float = 0.0) -> None:
    held = tick_seconds(theme, event["duration"])
    start = frame_count(tick_seconds(theme, event["tick"]), sample_rate)
    _add(mix, _voice(event["note"], held, sample_rate, kind), start, gain, pan)


def _drum(kind: str, sample_rate: int, rng: np.random.Generator) -> np.ndarray:
    length = {"kick": 0.33, "snare": 0.25, "hat": 0.095}[kind]
    t = np.arange(frame_count(length, sample_rate), dtype=np.float64) / sample_rate
    if kind == "kick":
        frequency = 47.0 + 110.0 * np.exp(-t * 30.0)
        phase = 2.0 * np.pi * np.cumsum(frequency) / sample_rate
        signal = np.sin(phase) * np.exp(-t * 13.0)
        signal += 0.035 * rng.standard_normal(t.size) * np.exp(-t * 100)
    elif kind == "snare":
        noise = rng.standard_normal(t.size)
        bright = np.diff(noise, prepend=noise[0])
        signal = 0.25 * np.sin(2 * np.pi * 185 * t) * np.exp(-t * 23)
        signal += 0.18 * bright * np.exp(-t * 24)
    else:
        noise = rng.standard_normal(t.size)
        signal = 0.13 * np.diff(noise, prepend=noise[0]) * np.exp(-t * 50)
    return signal.astype(np.float32)


def render_pcm(theme: dict, arrangement: str, member: str, *, sample_rate: int | None = None,
               duration_seconds: float | None = None) -> np.ndarray:
    if arrangement not in ARRANGEMENTS or member not in MEMBERS:
        raise ValueError(f"Unknown mix: {arrangement}/{member}")
    sample_rate = sample_rate or theme["sampleRate"]
    duration_seconds = duration_seconds if duration_seconds is not None else theme["bars"] * theme["meter"][0] * 60 / theme["bpm"]
    mix = np.zeros((frame_count(duration_seconds, sample_rate), 2), dtype=np.float32)
    rng = np.random.default_rng(theme["seed"])
    ticks_per_bar = theme["ppq"] * theme["meter"][0]

    pad_gain = {"sparse": 0.035, "driving": 0.045, "layered": 0.075}[arrangement]
    arp_gain = {"sparse": 0.006, "driving": 0.028, "layered": 0.065}[arrangement]
    if member == "keyboardist":
        arp_gain += 0.095

    for bar_index, chord in enumerate(theme["chords"]):
        bar_tick = chord["tick"]
        for index, note in enumerate(chord["notes"]):
            if arrangement == "driving":
                for pulse in (0, 960):
                    _add_note(mix, theme, {"tick": bar_tick + pulse, "duration": 750, "note": note},
                              "pad", pad_gain, sample_rate, pan=(-0.45 + index * 0.45))
            else:
                _add_note(mix, theme, {"tick": bar_tick, "duration": 1680, "note": note},
                          "pad", pad_gain, sample_rate, pan=(-0.45 + index * 0.45))

        if arrangement == "sparse":
            bass_hits = ((0, 720, chord["bass"]), (960, 600, chord["bass"]))
        elif arrangement == "driving":
            bass_hits = tuple((beat, 300, chord["bass"] + (12 if beat in (720, 1680) else 0))
                              for beat in (0, 360, 720, 960, 1320, 1680))
        else:
            bass_hits = ((0, 600, chord["bass"]), (720, 360, chord["bass"] + 7),
                         (1200, 480, chord["bass"]))
        for offset, length, note in bass_hits:
            _add_note(mix, theme, {"tick": bar_tick + offset, "duration": length, "note": note},
                      "bass", 0.115, sample_rate)

        if arp_gain > 0:
            for step in range(8):
                note = chord["notes"][(step + bar_index) % len(chord["notes"])] + 12
                _add_note(mix, theme,
                          {"tick": bar_tick + step * 240, "duration": 180, "note": note},
                          "arp", arp_gain, sample_rate, pan=-0.30)

        intro_scale = 0.52 if bar_index < theme["introBars"] else 1.0
        if arrangement == "sparse":
            pattern = ((0, "kick", 0.17), (960, "snare", 0.14),
                       (0, "hat", 0.38), (480, "hat", 0.27),
                       (960, "hat", 0.38), (1440, "hat", 0.27))
        elif arrangement == "driving":
            pattern = tuple((beat, "kick", 0.16) for beat in (0, 480, 960, 1440)) + (
                (480, "snare", 0.21), (1440, "snare", 0.21),
            ) + tuple((beat, "hat", 0.38 if beat % 480 == 0 else 0.26)
                      for beat in range(0, 1920, 240))
        else:
            pattern = ((0, "kick", 0.18), (720, "kick", 0.12),
                       (960, "kick", 0.16), (1680, "kick", 0.10),
                       (480, "snare", 0.20), (1440, "snare", 0.20)) + tuple(
                           (beat, "hat", 0.35 if beat % 480 == 0 else 0.24)
                           for beat in range(0, 1920, 240))
        for offset, kind, gain in pattern:
            start = frame_count(tick_seconds(theme, bar_tick + offset), sample_rate)
            _add(mix, _drum(kind, sample_rate, rng), start,
                 gain * intro_scale * (1.28 if member == "drummer" else 1.0))
        if member == "drummer" and bar_index >= theme["introBars"]:
            for offset in (1560, 1740):
                start = frame_count(tick_seconds(theme, bar_tick + offset), sample_rate)
                _add(mix, _drum("snare" if offset == 1740 else "hat", sample_rate, rng),
                     start, 0.085)

    melody_gain = {"frontman": 0.205, "keyboardist": 0.115,
                   "keytar": 0.12, "drummer": 0.105}[member]
    for event in theme["melody"]:
        _add_note(mix, theme, event, "lead", melody_gain, sample_rate)
    answer_gain = 0.155 if member == "keytar" else 0.025
    for event in theme["answers"]:
        _add_note(mix, theme, event, "answer", answer_gain, sample_rate, pan=0.34)

    peak = float(np.max(np.abs(mix)))
    if peak > 0:
        mix *= min(1.0, 0.72 / peak)
    edge = min(frame_count(0.012, sample_rate), mix.shape[0])
    mix[:edge] *= np.linspace(0, 1, edge, dtype=np.float32)[:, None]
    tail = min(frame_count(0.30, sample_rate), mix.shape[0])
    mix[-tail:] *= np.linspace(1, 0, tail, dtype=np.float32)[:, None]
    return mix


def render_mix(arrangement: str, member: str, output: Path) -> None:
    theme = json.loads((HERE / "theme.json").read_text())
    issues = validate_theme(theme)
    if issues:
        raise ValueError("; ".join(issues))
    pcm = render_pcm(theme, arrangement, member)
    if not np.isfinite(pcm).all():
        raise ValueError("Non-finite PCM sample")
    output.parent.mkdir(parents=True, exist_ok=True)
    samples = np.rint(np.clip(pcm, -1, 1) * 32767).astype("<i2")
    with wave.open(str(output), "wb") as target:
        target.setnchannels(2)
        target.setsampwidth(2)
        target.setframerate(theme["sampleRate"])
        target.writeframes(samples.tobytes())


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--all", action="store_true", help="Render all twelve WAV masters")
    parser.add_argument("--arrangement", choices=ARRANGEMENTS)
    parser.add_argument("--member", choices=MEMBERS)
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    if args.all:
        for arrangement in ARRANGEMENTS:
            for member in MEMBERS:
                output = HERE / "masters" / f"{arrangement}-{member}.wav"
                started = time.perf_counter()
                render_mix(arrangement, member, output)
                print(f"{output.name}: {time.perf_counter() - started:.2f}s")
    elif args.arrangement and args.member and args.output:
        started = time.perf_counter()
        render_mix(args.arrangement, args.member, args.output)
        print(f"{args.output.name}: {time.perf_counter() - started:.2f}s")
    else:
        parser.error("Use --all or --arrangement, --member and --output")


if __name__ == "__main__":
    main()
