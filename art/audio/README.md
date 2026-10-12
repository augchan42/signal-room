# Signal Room music

The app's twelve mixes come from the approved bright cue (`probe/final/bright-cue-candidate.mp3`): 120 BPM, A minor, a two-bar intro and an eight-bar phrase, 20.000 seconds. `bright_cue.py` is the composition and the in-house voices (drums, bass, arpeggio, effects). The lead and pads are Surge XT factory patches rendered by `surge_lead.py`. `SOUND_DIRECTION_NOTES.md` records how the cue was chosen. No reference recording, sample pack, voice, celebrity likeness or generative music service is used.

`render_mixes.py` renders the cue's parts once and mixes them twelve ways: three arrangements × four featured performers. Every mix keeps the cue's timing, so the Blender performance stays in sync with each of them.

- **Sparse:** the lead is exposed. The kit and hats are light, the pads are almost gone, the arpeggio is thin, and the plate reverb is larger.
- **Driving:** the kit, hats, bass and arpeggio are forward, and the pads are pulled back.
- **Layered:** the pads are forward, the lower-octave lead doubling is louder, and a soft half-note counterline plays chord tones under the hook.
- **Featured performer:** that performer's parts are raised by about 3.2 dB. Frontman: the lead and its doubling. Keyboardist: pads, arpeggio and the counterline (which the keyboardist also gets in Sparse and Driving). Keytar: the bass line, which the keytar player plays on stage. Drummer: the kit.

A peak limiter then brings each mix's true peak to −3 dBTP at −14 LUFS, because AAC encoding adds up to about 1.2 dB of peak. Runtime files are AAC-LC M4A in `app/src/main/assets/demo/audio/`; WAV masters and the Surge stems are kept in ignored `art/audio/masters/`.

## Reproduce

Use Python 3.14, NumPy 2.4.3, SciPy and FFmpeg 9.0.1 or compatible versions. Render the Surge stems first (needs Surge XT and the `.venv-surge` environment described in `SOUND_DIRECTION_NOTES.md`):

```sh
art/audio/.venv-surge/bin/python art/audio/surge_lead.py "Brass/JX-10 Double Brass" --out jx10.wav
art/audio/.venv-surge/bin/python art/audio/surge_lead.py "Polysynths/Anthemish 1" --out anthemish.wav
# layer: normalize each to its bars 3-6 RMS, sum, peak-normalize to 0.9 -> art/audio/masters/stems/lead-a7.wav
art/audio/.venv-surge/bin/python art/audio/surge_lead.py "Polysynths/Juno-60 Strings" --part pad --voicing open --out art/audio/masters/stems/pad-juno60-open.wav
```

Then:

```sh
art/audio/.venv/bin/python -m unittest discover -s art/audio/tests -v
art/audio/.venv/bin/python art/audio/render_mixes.py --all
art/audio/.venv/bin/python art/audio/finish_audio.py --all
python3 tools/validate_demo_assets.py --root app/src/main/assets/demo --report app/build/reports/demo-assets.json --write-hashes
```

`render_mixes.py` takes about 35 seconds for all twelve, most of it loudness measurement for the limiter. The finish step applies two-pass FFmpeg `loudnorm`, encodes AAC-LC and remeasures the decoded output. It fails when decoded duration differs from 20.000 seconds by more than 50 ms, loudness differs from −14 LUFS by more than 1 LU, or true peak exceeds −1 dBTP. Per-file measurements are in `measurements.json`. On this render all twelve decoded files measured exactly 960,000 frames, −14.11 to −14.08 LUFS, and −3.05 to −2.08 dBTP.

## Audition gate

Serve the repository root locally and open `art/audio/review.html` to compare a matched bar at the same timeline position. Automated tests confirm every decoded file is the expected length and that arrangement and member choices change the waveform over the same 8 seconds (the smallest difference between two mixes is 26% of the signal level; the test requires 10%). Those tests cannot establish whether the differences sound good or are obvious to a player. Human listening and gameplay feedback remain required before approving the music as fun.
