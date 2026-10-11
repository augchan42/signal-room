"""Render the standalone monochrome performance preview with its original music."""

import bpy
import subprocess
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parent
OUT = ROOT / "demo"
FRAMES = OUT / "frames" / "performance-monochrome"
FRAMES.mkdir(parents=True, exist_ok=True)
TARGET = OUT / "performance-monochrome.webm"
AUDIO = ROOT.parent.parent / "app" / "src" / "main" / "assets" / "demo" / "audio" / "driving-frontman.m4a"
scene = bpy.context.scene
assert scene.frame_start == 1 and scene.frame_end == 480 and scene.render.fps == 24


def render():
    scene.render.engine = "BLENDER_EEVEE"
    scene.render.resolution_percentage = 50
    scene.render.image_settings.file_format = "JPEG"
    scene.render.image_settings.color_mode = "RGB"
    scene.render.image_settings.quality = 90
    scene.render.filepath = str(FRAMES / "f-")
    bpy.ops.render.render(animation=True)
    print("PERFORMANCE_FRAMES_RENDERED", FRAMES)


def encode():
    missing = [frame for frame in range(1, 481) if not (FRAMES / f"f-{frame:04d}.jpg").is_file()]
    if missing:
        raise FileNotFoundError(f"Missing {len(missing)} rendered frames, first: {missing[0]}")
    subprocess.run([
        "ffmpeg", "-hide_banner", "-loglevel", "error", "-y",
        "-framerate", "24", "-start_number", "1", "-i", str(FRAMES / "f-%04d.jpg"),
        "-i", str(AUDIO), "-frames:v", "480", "-map", "0:v:0", "-map", "1:a:0",
        "-c:v", "libvpx-vp9", "-b:v", "0", "-crf", "32",
        "-deadline", "good", "-cpu-used", "4",
        "-c:a", "libopus", "-b:a", "128k", "-shortest", str(TARGET),
    ], check=True)
    print("PERFORMANCE_PREVIEW_ENCODED", TARGET)


if __name__ == "__main__":
    args = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
    if args == ["render"]:
        render()
        encode()
    elif args == ["encode"]:
        encode()
    else:
        raise SystemExit("Expected -- render|encode")
