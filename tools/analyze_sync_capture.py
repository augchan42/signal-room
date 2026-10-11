"""Locate click and flash onsets in an Android Device-audio screen recording.

This measures the captured output, not Media3 position estimates. Screen capture
samples video sparsely, so each flash onset has a frame-interval uncertainty.
"""

import argparse
import json
from pathlib import Path
import subprocess

import numpy as np


def command(*args: str) -> bytes:
    return subprocess.run(args, check=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE).stdout


def video_events(path: Path) -> tuple[list[float], list[float]]:
    probe = json.loads(command("ffprobe", "-v", "error", "-select_streams", "v:0",
        "-show_frames", "-show_entries", "frame=best_effort_timestamp_time", "-of", "json", str(path)))
    times = [float(frame["best_effort_timestamp_time"]) for frame in probe["frames"]]
    pixels = np.frombuffer(command("ffmpeg", "-v", "error", "-i", str(path),
        "-map", "0:v:0", "-vf", "crop=64:64:(iw-64)/2:(ih-64)/2,scale=1:1:flags=area,format=gray",
        "-fps_mode", "passthrough", "-f", "rawvideo", "-"), dtype=np.uint8)
    if len(times) != len(pixels):
        raise ValueError(f"{len(times)} video PTS but {len(pixels)} decoded frames")
    events = [times[i] for i, pixel in enumerate(pixels) if pixel > 180 and
        (i == 0 or pixels[i - 1] <= 180)]
    intervals = [times[i] - times[i - 1] if i else 0.0 for i, pixel in enumerate(pixels)
        if pixel > 180 and (i == 0 or pixels[i - 1] <= 180)]
    return events, intervals


def audio_events(path: Path) -> tuple[list[float], float]:
    samples = np.frombuffer(command("ffmpeg", "-v", "error", "-i", str(path),
        "-map", "0:a:0", "-ac", "1", "-ar", "44100", "-f", "f32le", "-"), dtype="<f4")
    sample_rate = 44100
    width = 220  # approximately five milliseconds
    count = len(samples) // width
    peaks = np.max(np.abs(samples[:count * width].reshape(count, width)), axis=1)
    threshold = max(0.01, float(np.max(peaks)) * 0.15)
    active = np.flatnonzero(peaks > threshold)
    events: list[float] = []
    for index in active:
        time = float(index * width / sample_rate)
        if not events or time - events[-1] > 0.2:
            events.append(time)
    return events, threshold


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("capture", type=Path)
    args = parser.parse_args()
    flashes, frame_intervals = video_events(args.capture)
    clicks, threshold = audio_events(args.capture)
    print(f"flash_onsets_s={flashes}")
    print(f"flash_preceding_frame_intervals_s={frame_intervals}")
    print(f"click_onsets_s={clicks} threshold={threshold:.4f}")
    for flash, interval in zip(flashes, frame_intervals):
        if not clicks:
            break
        click = min(clicks, key=lambda value: abs(value - flash))
        if abs(click - flash) < 0.5:
            print(f"pair flash={flash:.3f}s click={click:.3f}s "
                f"audio_minus_video={1000 * (click - flash):+.1f}ms "
                f"video_frame_interval={1000 * interval:.1f}ms")


if __name__ == "__main__":
    main()
