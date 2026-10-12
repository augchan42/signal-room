"""Generate debug-only sample-zero click and frame-zero flash assets."""

from pathlib import Path
import subprocess


ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "app/src/debug/assets/demo/sync"


def run(*args: str) -> None:
    subprocess.run(args, check=True)


def main() -> None:
    OUT.mkdir(parents=True, exist_ok=True)
    run(
        "ffmpeg", "-hide_banner", "-loglevel", "error", "-y",
        "-f", "lavfi", "-i",
        "aevalsrc=if(lt(mod(t\\,4)\\,0.01)\\,0.8*cos(2*PI*1000*t)\\,0):s=48000:d=20",
        "-ac", "2", "-c:a", "aac", "-b:a", "192k", "-movflags", "+faststart",
        str(OUT / "click.m4a"),
    )
    run(
        "ffmpeg", "-hide_banner", "-loglevel", "error", "-y",
        "-f", "lavfi", "-i", "color=c=black:s=360x640:r=24:d=4",
        "-vf", "drawbox=c=white:t=fill:enable='lt(t,0.041667)'",
        "-c:v", "libx264", "-pix_fmt", "yuv420p", "-r", "24", "-frames:v", "96",
        "-an", "-movflags", "+faststart", str(OUT / "flash.mp4"),
    )
    run(
        "ffmpeg", "-hide_banner", "-loglevel", "error", "-y",
        "-stream_loop", "4", "-i", str(OUT / "flash.mp4"),
        "-i", str(OUT / "click.m4a"),
        "-map", "0:v:0", "-map", "1:a:0", "-c", "copy", "-t", "20",
        "-movflags", "+faststart", str(OUT / "calibration.mp4"),
    )


if __name__ == "__main__":
    main()
