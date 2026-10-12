"""Timeline for the twenty-second monochrome performance."""

from dataclasses import dataclass


FPS = 24
BPM = 120
FRAMES_PER_BEAT = FPS * 60 // BPM
BEATS_PER_BAR = 4
DISPLAYED_FRAMES = 20 * FPS


@dataclass(frozen=True)
class Cue:
    name: str
    beam_power: float
    rim_scale: float
    sweep_width: float


def beat_frame(beat):
    if beat < 0:
        raise ValueError(beat)
    return 1 + beat * FRAMES_PER_BEAT


def bar_frame(bar):
    return beat_frame(bar * BEATS_PER_BAR)


def cue_at(frame):
    if not 1 <= frame <= DISPLAYED_FRAMES:
        raise ValueError(frame)
    if frame < bar_frame(2):
        return Cue("intro", 0.0, 0.35, 0.0)
    if frame < bar_frame(4):
        return Cue("reveal", 0.55, 0.60, 0.45)
    if frame < bar_frame(6):
        return Cue("groove", 0.65, 0.72, 0.75)
    if frame < bar_frame(8):
        return Cue("search", 0.82, 0.86, 1.15)
    return Cue("finale", 1.0, 1.0, 1.40)
