"""Render the bright-cue hook (or its pad chords) through Surge XT with a factory patch.

Run with the separate Python 3.12 environment (DawDreamer has no 3.14 build):

    art/audio/.venv-surge/bin/python art/audio/surge_lead.py "Leads/Sync Lead" --out /tmp/x.wav

The WAV is the dry lead only, 48 kHz stereo, and is mixed by `bright_cue.py --lead-wav`.
Surge XT is GPL-3.0 software; it is used as an instrument and no Surge code ships with the game.
"""

from __future__ import annotations

import argparse
import struct
import sys
import tempfile
import wave
from pathlib import Path

import dawdreamer as daw
import numpy as np

sys.path.insert(0, str(Path(__file__).parent))
import bright_cue  # noqa: E402  (shares the hook, tempo and sample rate)

PLUGIN = "/Library/Audio/Plug-Ins/VST3/Surge XT.vst3"
LIBRARY = Path("/Library/Application Support/Surge XT")
PATCHES = LIBRARY / "patches_factory"
JUCE_BASE64 = ".ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+"


def juce_base64(data: bytes) -> str:
    """JUCE MemoryBlock::toBase64Encoding: '<size>.' then 6-bit groups, least significant bits first."""
    number = int.from_bytes(data, "little")
    chars = (len(data) * 8 + 5) // 6
    return f"{len(data)}." + "".join(JUCE_BASE64[(number >> (6 * i)) & 63] for i in range(chars))


def patch_state(fxp: Path) -> bytes:
    """Wrap a Surge .fxp patch chunk as the VST3 state DawDreamer's load_state expects."""
    raw = fxp.read_bytes()
    if raw[:4] != b"CcnK" or raw[60:64] != b"sub3":
        raise ValueError(f"{fxp} is not a Surge patch chunk")
    xml = (f'<?xml version="1.0" encoding="UTF-8"?> <VST3PluginState><IComponent>'
           f"{juce_base64(raw[60:])}</IComponent></VST3PluginState>").encode() + b"\0"
    return b"VC2!" + struct.pack("<I", len(xml)) + xml


def pad_events(voicing: str = "close") -> list[tuple[float, float, int]]:
    """Held chords, one per bar, overlapping slightly into the next bar."""
    chords = bright_cue.PAD_OPEN if voicing == "open" else bright_cue.PAD
    # DawDreamer drops a note that starts at exactly 0 s, so bar 1 starts 1 ms late
    return [(max(bright_cue.at(bar), .001), bright_cue.BAR + .05, note)
            for bar, name in enumerate(bright_cue.CHORDS, 1) for note in chords[name]]


def render(patch: str, out: Path, part: str = "lead", seconds: float = 23.0, voicing: str = "close") -> None:
    engine = daw.RenderEngine(bright_cue.RATE, 256)
    engine.set_bpm(bright_cue.BPM)
    synth = engine.make_plugin_processor("surge", PLUGIN)
    with tempfile.NamedTemporaryFile(suffix=".bin") as state:
        base = LIBRARY if patch.startswith("patches_") else PATCHES  # third-party: "patches_3rdparty/..."
        state.write(patch_state(base / f"{patch}.fxp"))
        state.flush()
        synth.load_state(state.name)
    if part == "pad":
        for start, dur, note in pad_events(voicing):
            synth.add_midi_note(int(note), 90, start, dur)
    else:
        for start, dur, note in bright_cue.lead_events():
            on_beat = abs(start / bright_cue.BEAT - round(start / bright_cue.BEAT)) < .02
            velocity = 108 if on_beat else 92
            synth.add_midi_note(int(note), velocity, start, dur * .95)
    engine.load_graph([(synth, [])])
    engine.render(seconds)
    audio = engine.get_audio().T
    peak = float(np.abs(audio).max())
    if peak < 1e-4:
        raise RuntimeError(f"{patch} rendered silence")
    audio = audio / peak * .9
    with wave.open(str(out), "wb") as f:
        f.setnchannels(2)
        f.setsampwidth(2)
        f.setframerate(bright_cue.RATE)
        f.writeframes(np.rint(audio * 32767).astype("<i2").tobytes())


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("patch", help='factory patch path without .fxp, e.g. "Leads/Sync Lead"')
    parser.add_argument("--out", type=Path, required=True)
    parser.add_argument("--part", choices=("lead", "pad"), default="lead")
    parser.add_argument("--voicing", choices=("close", "open"), default="close", help="pad chord voicing")
    args = parser.parse_args()
    render(args.patch, args.out, args.part, voicing=args.voicing)
    print(args.out)


if __name__ == "__main__":
    main()
