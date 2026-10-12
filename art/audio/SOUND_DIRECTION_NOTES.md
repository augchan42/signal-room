# Signal Room sound direction — 2026-10-12

## Listener decisions

- The user likes all three selected Pixabay tracks below. These are references, not audio imported into Signal Room.
- Of the three sections in the new procedural palette probe, the user prefers the brighter third section (starts at 0:22.9).
- The current shipped twelve-mix cue is not approved; the user described it as poor despite the prior oscillator change.

## Local references

The source audio and downloaded Pixabay certificates are in the separate `sixlines-shorts` repository under `public/local/music/`. Its `series/music-inventory.json` records track metadata and `music/review.json` records earlier listening decisions. Track names are not proof of the sound; these choices were made by ear in that project.

| Reference | Local file in `sixlines-shorts/public/local/music/` | Tempo / key recorded there | Earlier section choices |
| --- | --- | --- | --- |
| Cinematic Dark Synthwave – Cyberpunk Atmosphere, SenorMusica81 | `pick22-cinematic-dark-synthwave.mp3` | ~100 BPM / G minor | Kept 0:40 and 1:36; chose 2:16 for a swap |
| Synthwave, The_Mountain | `pick13-synthwave-the-mountain.mp3` | 90 BPM / B minor | Kept 0:48 and 2:40 |
| Synthwave 80s Retro Background Music, Tunetank | `pick20-80s-retro-background.mp3` | 120 BPM / C minor | Kept 1:04; chose 2:00 for a swap |

The first two are marked Content ID registered in that repository; the third is also marked Content ID registered. Certificates exist locally, but neither the certificates nor current distribution terms have been reviewed for use in this game. Do not bundle these MP3s on the strength of these notes. No reference audio or melodic passage was copied into the probe.

## Current Signal Room cue

- `theme.json`: original 20-second, 120 BPM, A-minor, 2-bar intro plus 8-bar phrase.
- `render_theme.py`: produces three arrangements by four featured members using hand-coded synth and drum voices. The last change added detuned saws and a closing harmonic filter to the pad/arpeggio but did not change the composition or overall production approach.
- `app/src/main/assets/demo/audio/`: twelve AAC mixes currently used by the app. These are unchanged by the palette probe.
- A spectrogram comparison of ten-second excerpts showed sustained broadband content and effect tails across the selected reference sections. The current game mix has pronounced low-energy gaps and much less continuous high-frequency content. This is a visual measurement, not an assistant listening verdict.

## Disposable palette probe

- `probe_sound.py` generates a temporary WAV; FFmpeg encodes `probe/palette-probe.mp3` from it. The script uses a single original Dm–B♭–F–C phrase at 100 BPM for all three sections so the sound palette can be compared without a changing melody.
- Section starts: 0:00 darker, 0:11.45 more spacious, 0:22.90 brighter/driving. The user chose the third as the useful direction.
- Sounds are generated with detuned saw harmonics, filtered pads, sine/sub-plus-edge bass, synthetic kick/snare/hats, a pluck, stereo delays, and saturation. The probe contains no sampled audio.
- MP3 duration is 33.90 seconds. An FFmpeg check measured −13.5 LUFS integrated and −1.3 dBFS true peak. This checks delivery level, not musical quality.
- This file is for review only. It is not wired into the Android app or synchronized to the Blender performance.

To reproduce from the repository root after installing `art/audio/requirements.txt` in `art/audio/.venv`:

```sh
art/audio/.venv/bin/python art/audio/probe_sound.py
ffmpeg -i /private/tmp/signal-room-sound-probe.wav -af loudnorm=I=-14:TP=-1.5:LRA=11 -c:a libmp3lame -q:a 2 art/audio/probe/palette-probe.mp3
```

## Next audio pass

1. Use the bright third palette as the starting point. Make one original, complete 20-second cue, not twelve variants yet.
2. Improve musical arrangement and drum/bass interplay before another isolated oscillator tweak. Keep a recognizable hook; use accents, fills, and breakdown/re-entry rather than uniform activity.
3. Compare the same phrase with and without effects, and review on phone speakers as well as headphones. Maintain audible bass/hook separation and avoid washing out the lead with delay.
4. Get explicit listening approval for that one cue. Only then derive the sparse/driving/layered arrangements and member emphases, measure all twelve, and connect beat events to the Blender lighting/motion.

## External review status

The user requested advice from Opus 5.5. A direct `claude --model claude-opus-5-5` request was attempted on 2026-10-12, but Claude Code returned `Not logged in` before sending it (zero input/output tokens). No Opus review has occurred. Retry after the user signs in to Claude Code or provides another authorized Opus 5.5 connection; do not attribute the recommendations above to Opus.
