# Signal Room music

The app's twelve mixes come from the approved bright cue (`probe/final/bright-cue-candidate.mp3`): 120 BPM, A minor, a two-bar intro and an eight-bar phrase, 20.000 seconds. `bright_cue.py` is the composition and the in-house voices (drums, bass, arpeggio, effects). The lead and pads are Surge XT factory patches rendered by `surge_lead.py`. `SOUND_DIRECTION_NOTES.md` records how the cue was chosen. No reference recording, sample pack, voice, celebrity likeness or generative music service is used.

`render_mixes.py` mixes the cue twelve ways: three arrangements × four featured performers. Each mix keeps the cue's tempo, chords, hook and length, and changes what the band plays around the hook, so each matches its description in `app/src/main/assets/content/late-night.json`.

- **Sparse** (exposed lead over open bass and pad): half-time drums (kick on beat 1, snare on beat 3, no hi-hats or ghost notes); the bass holds one root per bar; no arpeggio; the pad is quiet; the plate reverb is larger.
- **Driving** (active bass and drums): kick on every beat from bar 1 and through the breakdown, 16th hi-hats throughout, and 16th-note bass alternating root and octave from bar 2; kit, hats, bass and arpeggio are louder, the pad is pulled back and the reverb is smaller.
- **Layered** (pad and counterline around the lead): a soft supersaw a scale third under the hook, a louder pad and lower-octave doubling, a half-note counterline on chord tones, softer drums and a larger reverb.
- **Featured performer:** each one plays something of their own, their parts are 6 dB louder, and they take the breakdown (bar 7, 12–14 s): every other part drops to 20% (the pad to 50%).
  - Frontman: the lead, and the hook doubled an octave up from the breakdown to the end of bar 9.
  - Keyboardist: the arpeggio and the counterline (also in Sparse, which otherwise has neither); the arpeggio stays bright through the breakdown.
  - Keytar: short answer phrases on a hollow pulse voice in the gaps of the hook (bar 2 pickup, end of bar 6), and a 16th-note A minor pentatonic run in the breakdown.
  - Drummer: a snare run in bar 4, a drum break in bar 7 (kick and snare, four toms, a short roll), and tom fills in bars 8 and 9.

With no variation and every level at 1, `bright_cue.py` still renders the approved cue bit for bit, and `--events` still writes the same `performance-events.json`.

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

`render_mixes.py` takes about 2.5 minutes for all twelve, because each mix renders its own parts. The finish step applies two-pass FFmpeg `loudnorm`, encodes AAC-LC and remeasures the decoded output. It fails when decoded duration differs from 20.000 seconds by more than 50 ms, loudness differs from −14 LUFS by more than 1 LU, or true peak exceeds −1 dBTP. Per-file measurements are in `measurements.json`. On this render all twelve decoded files measured exactly 960,000 frames, −14.12 to −14.09 LUFS, and −3.05 to −1.99 dBTP.

## Audition gate

Serve the repository root locally and open `art/audio/review.html` to compare a matched bar at the same timeline position. Automated tests confirm every decoded file is the expected length and that arrangement and member choices change the waveform over the same 8 seconds (over seconds 4–12 the smallest difference between two mixes is 28% of the signal level and the median 52%; the test requires 10%). Those tests cannot establish whether the differences sound good or are obvious to a player. Human listening and gameplay feedback remain required before approving the music as fun.
