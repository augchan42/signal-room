#!/usr/bin/env python3
"""Validate every bundled demo asset before Android merges app assets."""

import argparse
import hashlib
import json
import os
import re
import shutil
import struct
import subprocess
import sys
import tempfile
from pathlib import Path, PurePosixPath


ARRANGEMENTS = ("SPARSE", "DRIVING", "LAYERED")
MEMBERS = ("FRONTMAN", "KEYBOARDIST", "KEYTAR", "DRUMMER")
CONCEPTS = ("MONOCHROME", "WARM", "ELECTRIC")
HEX_SHA256 = re.compile(r"[0-9a-f]{64}\Z")
# The stage export's own reader and writer, so the check reads files exactly as they were written.
sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "art" / "blender"))
from live_motion import read_glb, read_motion  # noqa: E402

SCREEN_MATERIAL = "REAR SCREEN | replaceable feed"
LIVE_KINDS = {"glb": b"glTF", "motion": b"SRM1", "png": b"\x89PNG\r\n\x1a\n", "json": None}


def stage_node_names(path: Path) -> set[str]:
    gltf, _ = read_glb(path)
    return {node["name"] for node in gltf.get("nodes", []) if "name" in node}


def live_file_failures(path: Path, kind: str, stage_nodes: set[str] | None = None) -> list[str]:
    """The live stage's files are not media; check that each is present and readable as the app reads it."""
    if not path.is_file():
        return [f"missing file: {path}"]
    data = path.read_bytes()
    if kind == "json":
        try:
            content = json.loads(data)
        except json.JSONDecodeError as error:
            return [f"{path}: invalid JSON: {error}"]
        if not isinstance(content, dict) or content.get("fps") != 24 or content.get("frames") != 480:
            return [f"{path}: live data must be 24 fps, 480 frames"]
        return []
    signature = LIVE_KINDS[kind]
    if not data.startswith(signature):
        return [f"{path}: not a {'glTF' if kind == 'glb' else kind} file"]
    if kind == "glb":
        if int.from_bytes(data[4:8], "little") != 2:
            return [f"{path}: glTF version must be 2"]
        try:
            gltf, _ = read_glb(path)
        except (ValueError, struct.error, json.JSONDecodeError) as error:
            return [f"{path}: unreadable glTF: {error}"]
        screen = [m for m in gltf.get("materials", []) if m.get("name") == SCREEN_MATERIAL]
        if not screen or "emissiveTexture" not in screen[0]:
            return [f"{path}: '{SCREEN_MATERIAL}' must keep its emissive texture (the concept screen)"]
    if kind == "motion":
        try:
            fps, frames, nodes = read_motion(path)
        except (ValueError, KeyError, struct.error, UnicodeDecodeError) as error:
            return [f"{path}: unreadable motion: {error}"]
        if fps != 24 or frames != 480:
            return [f"{path}: motion must be 24 fps, 480 frames"]
        if stage_nodes is not None:
            unknown = sorted(name for name, *_ in nodes if name not in stage_nodes)
            if unknown:
                return [f"{path}: motion names nodes the stage lacks: {', '.join(unknown[:5])}"]
    return []


def _run(*command: str) -> subprocess.CompletedProcess:
    return subprocess.run(command, capture_output=True, text=True)


def _json_probe(path: Path, stream: str, entries: str, *, count_frames: bool = False) -> dict:
    command = ["ffprobe", "-v", "error"]
    if stream:
        command += ["-select_streams", stream]
    if count_frames:
        command.append("-count_frames")
    command += ["-show_entries", entries, "-of", "json", str(path)]
    result = _run(*command)
    if result.returncode:
        raise ValueError(f"ffprobe failed: {result.stderr.strip()[:240]}")
    return json.loads(result.stdout)


def _single_stream(path: Path, stream: str, entries: str, *, count_frames: bool = False) -> dict:
    streams = _json_probe(path, stream, entries, count_frames=count_frames).get("streams", [])
    if len(streams) != 1:
        raise ValueError(f"expected one {stream[0]} stream, got {len(streams)}")
    return streams[0]


def validate_file(path: Path) -> list[str]:
    """Check presence, container probing, and full media decoding."""
    if not path.is_file():
        return [f"missing file: {path}"]
    if path.stat().st_size == 0:
        return [f"empty file: {path}"]
    probe = _run("ffprobe", "-v", "error", "-show_entries", "format=format_name", "-of", "json", str(path))
    if probe.returncode:
        return [f"probe failed for {path}: {probe.stderr.strip()[:240]}"]
    decoded = _run("ffmpeg", "-xerror", "-v", "error", "-i", str(path), "-f", "null", "-")
    if decoded.returncode:
        return [f"decode failed for {path}: {decoded.stderr.strip()[:240]}"]
    return []


def _loudness(path: Path) -> tuple[float, float]:
    result = _run(
        "ffmpeg", "-hide_banner", "-nostats", "-i", str(path),
        "-af", "loudnorm=I=-14:TP=-1:LRA=11:print_format=json", "-f", "null", "-",
    )
    if result.returncode:
        raise ValueError(f"loudness measurement failed: {result.stderr.strip()[:240]}")
    matches = re.findall(r'\{\s*"input_i"\s*:.*?\}', result.stderr, flags=re.DOTALL)
    if not matches:
        raise ValueError("loudness measurement missing from FFmpeg output")
    measurement = json.loads(matches[-1])
    return float(measurement["input_i"]), float(measurement["input_tp"])


def inspect_audio(path: Path) -> dict:
    stream = _single_stream(path, "a:0", "stream=codec_name,profile,sample_rate,channels")
    all_streams = _json_probe(path, "", "stream=codec_type").get("streams", [])
    frames = _json_probe(path, "a:0", "frame=nb_samples").get("frames", [])
    if not frames or any("nb_samples" not in frame for frame in frames):
        raise ValueError("decoded presentation samples unavailable")
    lufs, peak = _loudness(path)
    return {
        "codec": stream.get("codec_name"),
        "profile": stream.get("profile"),
        "sampleRate": int(stream["sample_rate"]),
        "channels": int(stream["channels"]),
        "videoStreams": sum(item.get("codec_type") == "video" for item in all_streams),
        "decodedFrames": sum(int(frame["nb_samples"]) for frame in frames),
        "integratedLufs": lufs,
        "truePeakDbtp": peak,
        "bytes": path.stat().st_size,
    }


def audio_metric_failures(metrics: dict) -> list[str]:
    failures = []
    if metrics["codec"] != "aac" or metrics["profile"] != "LC":
        failures.append("audio must be AAC-LC")
    if metrics["sampleRate"] != 48000 or metrics["channels"] != 2:
        failures.append("audio must be 48 kHz stereo")
    if metrics.get("videoStreams", 0):
        failures.append("audio mix must not contain video")
    duration = metrics["decodedFrames"] / metrics["sampleRate"]
    if abs(duration - 20.0) > .05:
        failures.append(f"decoded duration {duration:.6f}s is outside 20.000 ±0.050s")
    if abs(metrics["integratedLufs"] + 14.0) > 1.0:
        failures.append(f"integrated loudness {metrics['integratedLufs']:.3f} LUFS is outside -14 ±1")
    if metrics["truePeakDbtp"] > -1.0:
        failures.append(f"true peak {metrics['truePeakDbtp']:.3f} dBTP exceeds -1")
    return failures


def inspect_image(path: Path) -> dict:
    stream = _single_stream(path, "v:0", "stream=codec_name,width,height")
    return {
        "codec": stream.get("codec_name"),
        "width": int(stream["width"]),
        "height": int(stream["height"]),
        "bytes": path.stat().st_size,
    }


def image_metric_failures(metrics: dict) -> list[str]:
    failures = []
    if metrics["codec"] != "webp":
        failures.append("still must be WebP")
    if (metrics["width"], metrics["height"]) != (1080, 1920):
        failures.append("still must be 1080 x 1920")
    return failures


def _safe_path(root: Path, value: object) -> bool:
    if not isinstance(value, str) or not value or "\\" in value:
        return False
    relative = PurePosixPath(value)
    if relative.is_absolute() or any(part in (".", "..") for part in relative.parts):
        return False
    if relative.as_posix() != value:
        return False
    return (root / value).resolve().is_relative_to(root.resolve())


def _manifest_entries(manifest: dict) -> list[tuple[str, str, str]]:
    """Return (relative path, expected type, claimed digest) in manifest order."""
    entries = []
    for item in manifest.get("audio", []):
        if isinstance(item, dict):
            entries.append((item.get("path"), "audio", item.get("sha256", "")))
    for item in manifest.get("concepts", []):
        if isinstance(item, dict):
            entries.append((item.get("stillPath"), "image", item.get("stillSha256", "")))
    for item in manifest.get("members", []):
        if isinstance(item, dict):
            entries.append((item.get("stillPath"), "image", item.get("sha256", "")))
    live = manifest.get("live")
    if isinstance(live, dict):
        entries.append((live.get("stagePath"), "glb", live.get("stageSha256", "")))
        entries.append((live.get("infoPath"), "json", live.get("infoSha256", "")))
        for item in live.get("screens", []):
            if isinstance(item, dict):
                entries.append((item.get("path"), "png", item.get("sha256", "")))
        for item in live.get("mixes", []):
            if isinstance(item, dict):
                entries.append((item.get("motionPath"), "motion", item.get("motionSha256", "")))
                entries.append((item.get("tracksPath"), "json", item.get("tracksSha256", "")))
    entries.append((manifest.get("controlRoomPath"), "image", manifest.get("controlRoomSha256", "")))
    return entries


def _live_structure_failures(live: object) -> list[str]:
    if not isinstance(live, dict):
        return ["manifest must describe the live stage"]
    failures = []
    if live.get("stagePath") != "live/stage.glb" or live.get("infoPath") != "live/stage.json":
        failures.append("live stage path must be live/stage.glb with live/stage.json")
    screens, mixes = live.get("screens"), live.get("mixes")
    if not isinstance(screens, list) or {(s.get("concept"), s.get("path")) for s in screens if isinstance(s, dict)} != \
            {(c, f"live/screen-{c.lower()}.png") for c in CONCEPTS} or len(screens) != 3:
        failures.append("manifest must list 3 live screens, one per concept")
    expected = {(a, m, f"live/mix/{a.lower()}-{m.lower()}.motion", f"live/mix/{a.lower()}-{m.lower()}.tracks.json")
                for a in ARRANGEMENTS for m in MEMBERS}
    if not isinstance(mixes, list) or len(mixes) != 12 or {
            (x.get("arrangement"), x.get("member"), x.get("motionPath"), x.get("tracksPath"))
            for x in mixes if isinstance(x, dict)} != expected:
        failures.append("manifest must list 12 live mixes, one per arrangement and member")
    return failures


def validate_manifest_structure(manifest: dict, root: Path) -> list[str]:
    failures = []
    if manifest.get("schemaVersion") != 4:
        failures.append("manifest schemaVersion must be 4")
    audio = manifest.get("audio")
    concepts = manifest.get("concepts")
    members = manifest.get("members")
    if not isinstance(audio, list) or len(audio) != 12:
        failures.append("manifest must contain exactly 12 audio entries")
    if not isinstance(concepts, list) or len(concepts) != 3:
        failures.append("manifest must contain exactly 3 concepts")
    if not isinstance(members, list) or len(members) != 4:
        failures.append("manifest must contain exactly 4 members")
    if not all(isinstance(items, list) for items in (audio, concepts, members)):
        return failures
    seen_ids = set()
    for item in audio:
        if not isinstance(item, dict):
            failures.append("audio entry must be an object")
            continue
        pair = (item.get("arrangement"), item.get("member"))
        identifier = f"{str(pair[0]).lower()}-{str(pair[1]).lower()}"
        if pair[0] not in ARRANGEMENTS or pair[1] not in MEMBERS or item.get("id") != identifier:
            failures.append(f"invalid audio ID or pair: {item.get('id')}")
        if item.get("path") != f"audio/{identifier}.m4a":
            failures.append(f"wrong audio path for {identifier}")
        if pair in seen_ids:
            failures.append(f"duplicate audio pair: {pair}")
        seen_ids.add(pair)
    if seen_ids != {(arrangement, member) for arrangement in ARRANGEMENTS for member in MEMBERS}:
        failures.append("audio matrix does not cover all 12 arrangement/member pairs")
    seen_concepts = set()
    for item in concepts:
        if not isinstance(item, dict):
            failures.append("concept entry must be an object")
            continue
        identifier = item.get("concept")
        slug = str(identifier).lower()
        if identifier not in CONCEPTS or item.get("stillPath") != f"visual/concept-{slug}.webp":
            failures.append(f"invalid concept mapping: {identifier}")
        if identifier in seen_concepts:
            failures.append(f"duplicate concept: {identifier}")
        seen_concepts.add(identifier)
    if seen_concepts != set(CONCEPTS):
        failures.append("concept matrix does not cover all 3 concepts")
    seen_members = set()
    for item in members:
        if not isinstance(item, dict):
            failures.append("member entry must be an object")
            continue
        identifier = item.get("member")
        if identifier not in MEMBERS or item.get("stillPath") != f"visual/member-{str(identifier).lower()}.webp":
            failures.append(f"invalid member mapping: {identifier}")
        if identifier in seen_members:
            failures.append(f"duplicate member: {identifier}")
        seen_members.add(identifier)
    if seen_members != set(MEMBERS):
        failures.append("member matrix does not cover all 4 members")
    failures.extend(_live_structure_failures(manifest.get("live")))
    if manifest.get("controlRoomPath") != "visual/control-room.webp":
        failures.append("control-room path must be visual/control-room.webp")
    paths = []
    for value, _, _ in _manifest_entries(manifest):
        if not _safe_path(root, value):
            failures.append(f"unsafe relative path: {value}")
        else:
            paths.append(value)
    if len(paths) != len(set(paths)):
        failures.append("duplicate asset path")
    return failures


def _sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for block in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def validate_manifest(root: Path, *, check_hashes: bool = True) -> dict:
    manifest_path = root / "manifest.json"
    report = {"root": str(root), "manifest": str(manifest_path), "measurements": {}, "failures": []}
    if not manifest_path.is_file():
        report["failures"].append(f"missing manifest: {manifest_path}")
        return report
    try:
        manifest = json.loads(manifest_path.read_text())
    except (OSError, json.JSONDecodeError) as error:
        report["failures"].append(f"invalid manifest JSON: {error}")
        return report
    if not isinstance(manifest, dict):
        report["failures"].append("manifest must be a JSON object")
        report["ok"] = False
        return report
    report["failures"].extend(validate_manifest_structure(manifest, root))
    stage_nodes = None
    live = manifest.get("live")
    stage_path = live.get("stagePath") if isinstance(live, dict) else None
    if isinstance(stage_path, str) and _safe_path(root, stage_path) and not live_file_failures(root / stage_path, "glb"):
        stage_nodes = stage_node_names(root / stage_path)
    for relative, kind, claimed_hash in _manifest_entries(manifest):
        if not _safe_path(root, relative):
            continue
        path = root / relative
        if kind in LIVE_KINDS:
            failures = live_file_failures(path, kind, stage_nodes)
            if not failures:
                digest = _sha256(path)
                report["measurements"][relative] = {"bytes": path.stat().st_size, "sha256": digest}
                if check_hashes and claimed_hash != digest:
                    report["failures"].append(f"{relative}: SHA-256 mismatch")
            report["failures"].extend(failures)
            continue
        failures = validate_file(path)
        if failures:
            report["failures"].extend(failures)
            continue
        try:
            if kind == "audio":
                metrics = inspect_audio(path)
                failures = audio_metric_failures(metrics)
            else:
                metrics = inspect_image(path)
                failures = image_metric_failures(metrics)
        except (ValueError, KeyError, ZeroDivisionError) as error:
            report["failures"].append(f"measurement failed for {relative}: {error}")
            continue
        digest = _sha256(path)
        metrics["sha256"] = digest
        report["measurements"][relative] = metrics
        report["failures"].extend(f"{relative}: {failure}" for failure in failures)
        if check_hashes and (not isinstance(claimed_hash, str) or not HEX_SHA256.fullmatch(claimed_hash) or claimed_hash != digest):
            report["failures"].append(f"{relative}: SHA-256 mismatch")
    expected_files = {relative for relative, _, _ in _manifest_entries(manifest) if _safe_path(root, relative)}
    actual_files = {path.relative_to(root).as_posix() for path in root.rglob("*") if path.is_file()} - {"manifest.json"}
    for extra in sorted(actual_files - expected_files):
        report["failures"].append(f"unlisted packaged asset: {extra}")
    report["ok"] = not report["failures"]
    return report


def _set_hashes(manifest: dict, measurements: dict) -> None:
    for item in manifest["audio"]:
        item["sha256"] = measurements[item["path"]]["sha256"]
    for item in manifest["concepts"]:
        item["stillSha256"] = measurements[item["stillPath"]]["sha256"]
    for item in manifest["members"]:
        item["sha256"] = measurements[item["stillPath"]]["sha256"]
    live = manifest["live"]
    live["stageSha256"] = measurements[live["stagePath"]]["sha256"]
    live["infoSha256"] = measurements[live["infoPath"]]["sha256"]
    for item in live["screens"]:
        item["sha256"] = measurements[item["path"]]["sha256"]
    for item in live["mixes"]:
        item["motionSha256"] = measurements[item["motionPath"]]["sha256"]
        item["tracksSha256"] = measurements[item["tracksPath"]]["sha256"]
    manifest["controlRoomSha256"] = measurements[manifest["controlRoomPath"]]["sha256"]


def _write_manifest_atomically(path: Path, manifest: dict) -> None:
    handle = tempfile.NamedTemporaryFile(mode="w", encoding="utf-8", dir=path.parent, prefix=".manifest-", suffix=".json", delete=False)
    try:
        with handle:
            json.dump(manifest, handle, indent=2)
            handle.write("\n")
            handle.flush()
            os.fsync(handle.fileno())
        os.replace(handle.name, path)
    finally:
        if os.path.exists(handle.name):
            os.unlink(handle.name)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, required=True)
    parser.add_argument("--report", type=Path, required=True)
    parser.add_argument("--write-hashes", action="store_true", help="Author hashes only after all media checks pass")
    args = parser.parse_args()
    missing_tools = [name for name in ("ffprobe", "ffmpeg") if shutil.which(name) is None]
    if missing_tools:
        print(f"Missing required media tools: {', '.join(missing_tools)}. Install FFmpeg before packaging.")
        return 2
    report = validate_manifest(args.root, check_hashes=not args.write_hashes)
    if args.write_hashes and report.get("ok"):
        path = args.root / "manifest.json"
        manifest = json.loads(path.read_text())
        _set_hashes(manifest, report["measurements"])
        _write_manifest_atomically(path, manifest)
        report = validate_manifest(args.root)
    args.report.parent.mkdir(parents=True, exist_ok=True)
    args.report.write_text(json.dumps(report, indent=2, sort_keys=True) + "\n")
    if report.get("ok"):
        print(f"Validated {len(report['measurements'])} demo assets; report: {args.report}")
        return 0
    for failure in report["failures"]:
        print(f"ERROR: {failure}")
    print(f"Validation failed; report: {args.report}")
    return 1


if __name__ == "__main__":
    raise SystemExit(main())
