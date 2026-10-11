# Signal Room original instrumental demo

`theme.json` is the authored source: MIDI note numbers and tick durations for a two-bar intro and an eight-bar A-minor phrase at 120 BPM. The phrase harmony is Am–F–C–G–Am–F–E–Am. No reference recording, sample pack, voice, celebrity likeness or generative music service is used.

The offline renderer produces twelve complete mixes: three arrangements (`sparse`, `driving`, `layered`) × four featured performers (`frontman`, `keyboardist`, `keytar`, `drummer`). Frontman emphasis is an instrumental melodic lead, not singing. Sparse leaves exposed lead and bass space; Driving adds active bass and four-on-floor drums; Layered adds sustained chords and a counterline. Member variants change the lead, arpeggio, keytar answers or percussion pattern. Runtime files are AAC-LC M4A in `app/src/main/assets/demo/audio/`; uncompressed WAV masters are kept in ignored `art/audio/masters/`.

## Reproduce

Use Python 3.14, NumPy 2.4.3 and FFmpeg 9.0.1 or compatible versions. A local virtual environment avoids global package changes:

```sh
python3 -m venv art/audio/.venv
art/audio/.venv/bin/python -m pip install -r art/audio/requirements.txt
art/audio/.venv/bin/python -m unittest discover -s art/audio/tests -v
art/audio/.venv/bin/python art/audio/render_theme.py --all
art/audio/.venv/bin/python art/audio/finish_audio.py --all
```

The renderer writes 960,000 stereo frames per mix at 48 kHz. The finish step measures each WAV, applies two-pass FFmpeg `loudnorm`, encodes AAC-LC and remeasures decoded output. It fails when decoded duration differs from 20.000 seconds by more than 50 ms, loudness differs from −14 LUFS by more than 1 LU, or true peak exceeds −1 dBTP. Actual per-file measurements are in `measurements.json`; all twelve decoded files measured exactly 960,000 frames, −14.11 to −14.02 LUFS, and −3.02 to −1.24 dBTP on this render.

On the authoring Mac, one 20-second WAV render took 0.19 seconds; the twelve renders took 0.16–0.22 seconds each. Encoding and measurement take longer and depend on FFmpeg and hardware.

## Audition gate

Serve the repository root locally and open `art/audio/review.html` to compare a matched bar at the same timeline position. Automated tests confirm every decoded file is the expected length and that arrangement/member choices alter the phrase waveform. Those tests cannot establish whether the differences sound good or are obvious to a player. Human listening and gameplay feedback remain required before approving the music as fun.
