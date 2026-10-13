#!/usr/bin/env python3
"""Two-pass loudness finish, AAC encode, and decoded-output measurement."""

import argparse
import json
import re
import subprocess
from pathlib import Path


HERE = Path(__file__).resolve().parent
ARRANGEMENTS = ("sparse", "driving", "layered")
MEMBERS = ("frontman", "keyboardist", "keytar", "drummer")


OUTPUT_DIR = HERE.parents[1] / "app" / "src" / "main" / "assets" / "demo" / "audio"
REPORT = HERE / "measurements.json"
TARGET_LUFS = -14.0
TARGET_TRUE_PEAK = -1.5


def parse_loudnorm(log: str) -> dict:
    matches = re.findall(r'\{\s*"input_i"\s*:.*?\}', log, flags=re.DOTALL)
    if not matches:
        raise ValueError("FFmpeg did not return loudnorm measurements")
    return json.loads(matches[-1])


def _run(command: list[str]) -> subprocess.CompletedProcess:
    return subprocess.run(command, check=True, capture_output=True, text=True)


def _loudnorm_filter(measured: dict | None = None) -> str:
    base = f"loudnorm=I={TARGET_LUFS}:TP={TARGET_TRUE_PEAK}:LRA=11"
    if measured is None:
        return base + ":print_format=json"
    return (
        base
        + f":measured_I={measured['input_i']}"
        + f":measured_TP={measured['input_tp']}"
        + f":measured_LRA={measured['input_lra']}"
        + f":measured_thresh={measured['input_thresh']}"
        + f":offset={measured['target_offset']}"
        + ":linear=true:print_format=json"
    )


def measure_loudness(path: Path) -> dict:
    result = _run([
        "ffmpeg", "-hide_banner", "-nostats", "-i", str(path),
        "-af", _loudnorm_filter(), "-f", "null", "-",
    ])
    return parse_loudnorm(result.stderr)


def decoded_frames(path: Path) -> int:
    process = subprocess.Popen(
        ["ffmpeg", "-v", "error", "-i", str(path), "-f", "f32le", "-acodec", "pcm_f32le", "-ac", "2", "-"],
        stdout=subprocess.PIPE, stderr=subprocess.PIPE,
    )
    count = 0
    assert process.stdout is not None
    while chunk := process.stdout.read(65536):
        count += len(chunk)
    stderr = process.stderr.read().decode() if process.stderr else ""
    if process.wait() != 0:
        raise RuntimeError(f"Could not decode {path}: {stderr}")
    if count % 8:
        raise ValueError(f"Decoded PCM size is not stereo float32: {path}")
    return count // 8


def probe(path: Path) -> dict:
    result = _run([
        "ffprobe", "-v", "error", "-select_streams", "a:0",
        "-show_entries", "stream=codec_name,profile,sample_rate,channels",
        "-of", "json", str(path),
    ])
    streams = json.loads(result.stdout)["streams"]
    if len(streams) != 1:
        raise ValueError(f"Expected one audio stream in {path}")
    stream = streams[0]
    loudness = measure_loudness(path)
    return {
        "codec": stream["codec_name"],
        "profile": stream.get("profile"),
        "sampleRate": int(stream["sample_rate"]),
        "channels": int(stream["channels"]),
        "decodedFrames": decoded_frames(path),
        "integratedLufs": float(loudness["input_i"]),
        "truePeakDbtp": float(loudness["input_tp"]),
        "bytes": path.stat().st_size,
    }


def validate_metrics(metrics: dict) -> list[str]:
    issues = []
    if metrics["codec"] != "aac" or metrics["profile"] != "LC":
        issues.append("audio must be AAC-LC")
    if metrics["sampleRate"] != 48000 or metrics["channels"] != 2:
        issues.append("audio must be 48 kHz stereo")
    duration = metrics["decodedFrames"] / 48000.0
    if abs(duration - 20.0) > 0.05:
        issues.append(f"decoded duration {duration:.3f}s is outside tolerance")
    if abs(metrics["integratedLufs"] - TARGET_LUFS) > 1.0:
        issues.append(f"integrated loudness {metrics['integratedLufs']:.2f} LUFS is outside tolerance")
    if metrics["truePeakDbtp"] > -1.0:
        issues.append(f"true peak {metrics['truePeakDbtp']:.2f} dBTP exceeds -1.0")
    return issues


def finish_mix(arrangement: str, member: str) -> dict:
    identifier = f"{arrangement}-{member}"
    master = HERE / "masters" / f"{identifier}.wav"
    output = OUTPUT_DIR / f"{identifier}.m4a"
    if not master.exists():
        raise FileNotFoundError(master)
    first = measure_loudness(master)
    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
    _run([
        "ffmpeg", "-hide_banner", "-nostats", "-y", "-i", str(master),
        "-af", _loudnorm_filter(first), "-ar", "48000", "-ac", "2",
        "-c:a", "aac", "-profile:a", "aac_low", "-b:a", "192k",
        "-map_metadata", "-1", "-movflags", "+faststart", str(output),
    ])
    metrics = probe(output)
    issues = validate_metrics(metrics)
    if issues:
        raise ValueError(f"{identifier}: {'; '.join(issues)}")
    return metrics


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--all", action="store_true", help="Finish all twelve masters")
    parser.add_argument("--arrangement", choices=ARRANGEMENTS)
    parser.add_argument("--member", choices=MEMBERS)
    args = parser.parse_args()
    if args.all:
        pairs = [(arrangement, member) for arrangement in ARRANGEMENTS for member in MEMBERS]
    elif args.arrangement and args.member:
        pairs = [(args.arrangement, args.member)]
    else:
        parser.error("Use --all or --arrangement and --member")
    report = {}
    for arrangement, member in pairs:
        identifier = f"{arrangement}-{member}"
        report[identifier] = finish_mix(arrangement, member)
        item = report[identifier]
        print(f"{identifier}: {item['decodedFrames']} frames, {item['integratedLufs']:.2f} LUFS, "
              f"{item['truePeakDbtp']:.2f} dBTP")
    if args.all:
        REPORT.write_text(json.dumps(report, indent=2, sort_keys=True) + "\n")


if __name__ == "__main__":
    main()
