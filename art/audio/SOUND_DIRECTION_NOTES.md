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
2. Keep 120 BPM for this pass. The app currently ends playback at 20 seconds, and the existing 480-frame Blender performance is timed to the 120 BPM mix. Its 2-bar intro plus 8-bar phrase already fills 20 seconds. A change to 96 or 100 BPM would require a separate audio/video timing decision; seamless audio looping is not currently a requirement.
3. Improve musical arrangement and drum/bass interplay before another isolated oscillator tweak. Keep a recognizable hook; use accents, fills, and breakdown/re-entry rather than uniform activity.
4. Add an effects architecture to the single cue: overlapping pad releases and stereo chorus, a short gated snare room, filtered plate/delay returns on melodic parts, and moderate kick-triggered ducking. Compare dry and wet renders; avoid masking the hook with the effects.
5. Make the bass audible on phone speakers with upper harmonics while keeping the low end centered. Vary hat accents and non-kick timing slightly, but keep kick/snare timing stable.
6. Review level-matched excerpts on phone speaker, earbuds, and headphones. Check the loop seam only if the game is changed to loop audio. Check mono compatibility and true peak as well as the existing duration/loudness gates.
7. Get explicit listening approval for that one cue. Only then produce stems, derive sparse/driving/layered arrangements and member emphases, measure all twelve, and connect beat events to the Blender lighting/motion.

## User-supplied Claude review

The user pasted a Claude production review on 2026-10-12. The review explicitly says Claude had not heard the references, shipped cue, or palette probe; its diagnosis is based on our description and general DSP knowledge. The actionable recommendations incorporated above are: prioritize space between hits, gated snare reverb, overlapping chorus pads, delay/plate returns, sidechain movement, low-mid and phone-audible bass, groove variation, level-matched listening, and approving one cue before scaling to twelve mixes.

Treat these as hypotheses to audition, not established causes. In particular, the claim that aliasing makes the current oscillator sound poor is unverified: `render_theme.py` uses harmonic synthesis with a frequency-dependent harmonic ceiling rather than a naive unbandlimited saw. PolyBLEP, oversampling, a more advanced reverb, commercial plugins, and licensed one-shots are possible later experiments, not prerequisites for the next cue. Verify software capabilities and asset licenses before adopting them.

The Claude review also proposed 96 BPM for an eight-bar seamless 20-second loop, or 100 BPM for 19.2 seconds. Those are musically coherent options but conflict with the current 120 BPM audio/video timing and one-shot playback, so neither is accepted for the immediate pass.

The user originally asked for an Opus 5.5 CLI consultation. A direct `claude --model claude-opus-5-5` CLI request returned `Not logged in` before sending it (zero input/output tokens). The pasted review is user-supplied Claude text, not a successful CLI response. Retry the CLI only after its authentication is available.

## Bright cue candidate (Claude, 2026-10-12)

`bright_cue.py` is a new standalone renderer for the single cue in step 1 above. It does not change `render_theme.py`, `theme.json` or the app audio.

- Composition: original, 120 BPM, A minor, 10 bars = 20.000 s (960,000 frames at 48 kHz). Bars 1–2 intro (pads, arpeggio opening from dark to bright, kick and hats enter in bar 2, snare/tom fill, noise riser). Bar 3 drop with crash and sub impact. Bars 3–6 Am–F–C–G with the hook. Bar 6 tom fill. Bar 7 breakdown: no kick, held bass swell, sparse hats, snare roll and riser. Bar 8 re-entry with crash, hook up a third, lower-octave doubling. Bar 10 ends on a full-band A-minor stab at beat 3 and a 0.6 s fade.
- Hook: one rhythm (16th steps 0, 3, 6, 8, 12, 14) repeated on every full bar, pitches following the chord, so it is recognizable on second hearing.
- Sound: PolyBLEP band-limited saws and pulses; 5–7 voice supersaws spread in stereo; block-updated resonant low-pass with per-note filter envelopes; mono lead and bass with portamento; 808-style metallic hats (six square oscillators); kick with pitch sweep, click and saturation; snare with body, noise and a three-burst clap.
- Effects: stereo convolution plate (2.4 s IR, highs decay faster), gated room on the snare, chorus on pads, dotted-eighth ping-pong delays on arpeggio and lead, kick-triggered ducking on pads, arpeggio and reverb return (lighter on bass), sides high-passed at 140 Hz so the low end stays centered, 2:1 bus compression and soft clipping.
- Outputs: `probe/bright-cue.mp3` (wet) and `probe/bright-cue-dry.mp3` (same render without plate, gated room, delays). Both −14.0 LUFS; true peak −1.0 dBFS wet, −1.1 dBFS dry (dry needed a limiter). Rendering both WAVs takes about 3 minutes.
- Checks done by measurement and spectrogram, not by ear: bar 1 sits about 8 dB below the full bars; 16th arpeggio and hats are continuous through the full bars; no silent gaps after bar 1.

Reproduce:

```sh
art/audio/.venv/bin/python art/audio/bright_cue.py --out /private/tmp/signal-room-bright-cue
```

Then gain each WAV to −14 LUFS (measured with FFmpeg `ebur128`) and encode with `libmp3lame -q:a 1`.

Status: awaiting listening approval. The twelve mixes are on hold until then.

### Lead revision (same day)

Listener feedback on the first render: the cue was "much better", but the lead entering at 4 s sounded whiny, with overdone "yows". The user asked for something more atmospheric or orchestral, not a cheap-sounding keyboard.

Causes found in the code: each lead note's filter opened to about 6.7 kHz and closed to 1.5 kHz within about 0.2 s with resonance; 12 ms portamento made every note scoop; detune and vibrato were each about 11 cents in a high register; and the master had a 2–5 kHz boost.

Replaced the mono lead with an orchestral synth voice: 7-voice supersaw at 7 cents, 35 ms attack and 0.32 s release, legato overlap with no portamento, a low-pass that opens slowly from 1.6 to about 2.9 kHz on held notes (about 0.45 kHz higher from bar 8), soft octave-below body, quiet octave-above sine, delayed vibrato of about 4 cents, ensemble chorus, and a plate send raised from 0.55 to 0.8. The master 2–5 kHz boost was halved. Both MP3s still measure −14.0 LUFS, 20.000 s, true peak ≤ −1.0 dBFS. Awaiting the user's listen.

### Keys revision (same day)

Listener feedback on the orchestral lead: "much better" but a bit subdued; reference point is the keyboard sound of Level 42's "Something About You" (timbre and role only; no notes or audio from that recording are used).

Changes: a two-operator FM electric-piano voice (`fm_key`: 1:1 body plus a fast-decaying 14:1 tine) is layered on every lead note for a glassy attack; the lead filter opens from 2.4 to about 4 kHz; the lead is 1.6 dB louder in the mix. A new `keys_part` plays FM chord stabs on offbeats (16th steps 2, 6, 10, 13) in bars 3–6 and 8–9, a held chord in bar 7 and one hit in bar 10, with chorus and a plate send. The arpeggio is 2 dB quieter. Both MP3s: −14.0 LUFS, 20.000 s, true peak −1.5 / −1.4 dBFS.

Review files in `probe/review/`: `bright-cue-v1.mp3` (first render), `bright-cue-v2.mp3` (orchestral lead), `lead-before-after.mp3` (v1 then v2, 4–12 s), `keys-before-after.mp3` (v2 then current, 4–12 s), `bright-cue-performance.mp4` (current cue under the Blender video).

### Five lead variants (same day)

Listener feedback on the FM keys version: of everything heard, the first render's lead (first half of `review/lead-before-after.mp3`) sounds best, but it is still not right. The user asked for five very different tries to choose from.

The FM stabs were removed and the backing reverted to the first render (arpeggio level 0.5, master 2–5 kHz boost 0.2). `bright_cue.py --lead NAME` now selects the lead; each lead is level-matched to −16.5 dB RMS over bars 3–6 before mixing. Files are in `probe/leads/`, each −14.0 LUFS (choir −14.1), true peak ≤ −1.1 dBFS:

1. `lead-1-supersaw.mp3` — first-render supersaw without the slide or slow resonant sweep; octave-below saw layer.
2. `lead-2-square.mp3` — two detuned pulse waves with slow pulse-width movement; hollow tone.
3. `lead-3-brass.mp3` — polyphonic synth brass; filter opens on each note and settles.
4. `lead-4-sync.mp3` — hard-sync saw rendered at 2× and decimated; bright and cutting.
5. `lead-5-choir.mp3` — Fairlight-style choir; saws through "ah" formant filters with breath noise.

`leads-compare.mp3` plays 4–12 s of each, in order, with 1 s of silence between (45 s total). Awaiting the user's choice.

### Lead round 2 (same day)

Of the five, the user liked 1 (supersaw) and 4 (sync) best. Five new leads explore between them, same backing and level matching, in `probe/leads/` (−14.0 LUFS, supersync −14.1; true peak ≤ −1.1 dBFS):

6. `lead-6-hybrid.mp3` — supersaw and sync blended 60/40 after matching each layer's level.
7. `lead-7-supersync.mp3` — five hard-sync voices detuned −11 to +11 cents, spread across stereo.
8. `lead-8-sparkle.mp3` — supersaw with a quiet sync layer one octave up (25%).
9. `lead-9-widesaw.mp3` — supersaw with 7 voices at 14 cents and a brighter filter floor (3.4 kHz).
10. `lead-10-warmsync.mp3` — gentler sync (ratio 1.7, smaller sweep, 4.2 kHz low-pass) blended 60/40 with the supersaw.

`leads-compare-round2.mp3` plays 4–12 s of 1, 4, 6, 7, 8, 9, 10 in that order with 1 s gaps (63 s). Awaiting the user's choice.

### Polish pass on 6 and 7 (same day)

The user leaned toward 6 (hybrid) and 7 (supersync) but said the lead still sounds quite cheap. `--polish` adds: independent slow pitch drift per oscillator (about 3–4 cents, below 1 Hz); per-note accents (downbeats and long notes louder and brighter) and ±4 ms timing on off-beat notes; a driven 4-pole low-pass in place of the 2-pole; tape-style saturation, a 7 kHz roll-off, a 2.5–4 kHz dip and ensemble chorus on the lead; master air boost 0.22→0.12 and presence 0.2→0.1.

Files: `probe/leads/lead-6b-hybrid-polished.mp3`, `lead-7b-supersync-polished.mp3` (−14.0 / −14.1 LUFS, true peak ≤ −1.3 dBFS), and `leads-compare-polish.mp3` (6, 6b, 7, 7b; 36 s). Measured on 4–12 s, the 2.5–4 kHz band fell 1.8 dB (6) and 2.5 dB (7) relative to the whole mix; 8–16 kHz barely changed because the hats dominate that band.

Possible next step if this still sounds cheap: render the lead and pads with an established open-source synth (for example Surge XT, GPL-3.0, driven offline from Python through DawDreamer) instead of the in-house oscillators. No synth plugins are installed on this Mac; this needs the user's approval to install. Audio rendered with a GPL synth is not itself GPL-licensed, but verify before shipping.

### Surge XT leads (same day)

The user approved trying Surge XT and installed it with `brew install --cask surge-xt` (version 1.3.4: VST3/AU in `/Library/Audio/Plug-Ins/`, factory patches in `/Library/Application Support/Surge XT/`). DawDreamer 0.9.0 has no Python 3.14 build, so a separate ignored environment holds it: `uv venv --python python3.12 art/audio/.venv-surge` then `uv pip install --python art/audio/.venv-surge/bin/python dawdreamer numpy scipy`.

`surge_lead.py` plays the hook through Surge XT with a factory patch and writes a dry stereo WAV (peak-normalized). DawDreamer's `load_preset` does not read `.fxp` for the VST3; the script instead wraps the patch chunk (bytes from offset 60, starting `sub3`) in JUCE's VST3 state format (`VC2!` header, XML, JUCE base64) and calls `load_state`. Each patch was checked against the default patch by spectral centroid and stereo correlation; all differ. `bright_cue.py --lead-wav PATH` mixes that WAV in place of the in-house lead, level-matched as before; the backing is unchanged.

Files in `probe/leads/surge/` (−14.0/−14.1 LUFS, true peak ≤ −1.1 dBFS): `s1-sync-lead`, `s2-classic-lead-1`, `s3-classic-lead-2`, `s4-moogy-saw`, `s5-very-chorus`, `s6-ob-8-jump`, `s7-jx-10-double-brass`, `s8-anthemish-1` (factory patch names). `surge-compare.mp3` plays 4–12 s of 6, 7, then S1–S8 (90 s). Patch effects inside Surge are kept, so some leads carry their own chorus or reverb on top of the cue's plate. Awaiting the user's choice; pads and other parts could move to Surge next.

Reproduce one:

```sh
art/audio/.venv-surge/bin/python art/audio/surge_lead.py "Leads/Sync Lead" --out /tmp/sync.wav
art/audio/.venv/bin/python art/audio/bright_cue.py --lead-wav /tmp/sync.wav --wet-only --out /tmp/mix
```

### Surge round 2 (same day)

The user liked S6 (OB-8 Jump), S7 (JX-10 Double Brass) and S8 (Anthemish 1), and asked to also try Moog and Roland sounds. `surge_lead.py` now takes `--part pad` (one held chord per bar) and third-party patches by `patches_3rdparty/...` path; `bright_cue.py` takes `--pad-wav` (level-matched to the in-house pad, −19.3 dB RMS over bars 3–6) and `--name`.

Files in `probe/leads/surge/` (−14.0/−14.1 LUFS, true peak ≤ −1.1 dBFS):

- `compare-a-brass-anthem.mp3` (90 s): S6, S7, S8, then `a1-synth-brass-1`, `a2-toto-brass`, `a3-anthemish-2`, `a4-oberheim-dreams`, `a5-separate-ways` (factory), `a6-layer-ob8-anthemish`, `a7-layer-jx10-anthemish` (two patches level-matched and summed).
- `compare-b-roland-moog.mp3` (72 s): `b1-jupiter-8`, `b2-super-jupiter` (factory), `b3-jp8k-supersaw` (Luna), `b4-polymoog` (factory), `b5-moogish`, `b6-mini` (Kuniklo), `b7-moogy-staccato` (Luna), `b8-jx10-anthemish-mks70-pads` (A7's lead with Surge "MKS-70 Warm Pad" replacing the in-house pads).

Patch names refer to the instruments they imitate; they are Surge patches, not samples of those instruments. Awaiting the user's choice.

## Surge band parts

Feedback on Surge round 2: "S6 S7 S8 A6 A7 i like". All five picks are OB-8 Jump, JX-10 Double Brass and Anthemish 1, alone or layered. The lead is treated as settled on that family, and A7 (JX-10 Double Brass + Anthemish 1) is the working lead.

This round tests whether the in-house pads, arpeggio and bass also make the cue sound cheap. Each part was swapped for a Surge XT factory patch, one at a time. Every mix uses the A7 lead.

- `bright_cue.py` has new `--arp-wav` and `--bass-wav` options. An external part is mixed at the RMS the in-house part has on bars 3-6.
- `surge_lead.py` has new `--part arp` and `--part bass` options. They use the same note lists as the in-house parts (`arp_events()`, `bass_events()`).
- DawDreamer drops a note that starts at exactly 0 s. Pad and arp notes on bar 1 now start 1 ms late. B8 (MKS-70 pads) from round 2 was missing its bar-1 chord because of this.

Files in `probe/band/`:

| File | Change from baseline |
| --- | --- |
| `c0-baseline` | A7 lead, in-house band |
| `p1-juno60`, `p2-polymoog`, `p3-superjupiter` | pads: Polysynths/Juno-60 Strings, Polymoog, Super Jupiter |
| `r1-the1980s`, `r2-hasselhoff`, `r3-nicepluck1` | arpeggio: Plucks/The 1980s, Hasselhoff, Nice Pluck 1 |
| `l1-bass1`, `l2-rubber`, `l3-lordsaw` | bass: Basses/Bass 1, Rubber Bass, Lord Sawtooth |
| `f1-all-surge` | Juno-60 Strings pads + The 1980s arpeggio + Bass 1 |

The comparison clips `compare-pads`, `compare-arps` and `compare-bass` each play seconds 4-12 of the baseline and then the three candidates. `compare-all-surge` plays the baseline and then `f1-all-surge`. All mixes measure -14.0 to -14.1 LUFS with true peak -1.1 to -1.4 dBTP and run 20.000 s.

## Pad fixes

The user suspected the pads sound cheap. A web search on why pads sound cheap gave these common fixes: open voicings spread over two or three octaves, slow filter movement, cutting low-mid buildup, less reverb and width, and keeping the pad lower so it doesn't crowd the lead. Sources: iZotope "5 tips for mixing pads", MusicTech "10 tips to create better synth pads", MusicRadar "How to create an '80s Juno-style pad sound", Attack Magazine "Massive chords with ensemble voicings".

Problems found in the cue:

- The pad chords were close-voiced inside one octave (G3-A4, MIDI 55-69). That is the arpeggio's range (55-72), and the top note touches the bottom of the lead (69-84).
- The pad was ducked by the kick at depth 0.55, which pumps it.
- The pad had no filter movement.

New options:

- `surge_lead.py --voicing open` (`PAD_OPEN` in `bright_cue.py`) uses root and fifth low, then the 10th, 9th or 7th on top, all under A4. The chords are Am (A2 E3 C4 E4), Fmaj7 (F2 C3 A3 E4), Cadd9 (C3 G3 D4 E4) and G (G2 D3 B3 D4).
- `bright_cue.py --pad-motion` closes a low-pass to 700 Hz at each chord change. It opens to 5.2 kHz over the bar in the drop bars and to 2.6 kHz in the intro and breakdown.
- `bright_cue.py --pad-clean` adds a 200 Hz high-pass, -4 dB at 250-500 Hz and a small 2-5 kHz dip, uses the light kick ducking (0.3), and halves the reverb send.

Files in `probe/padfix/`, all with the A7 lead and Surge Juno-60 Strings pads, each step adding one fix: `q0-close`, `q1-open`, `q2-open-motion`, `q3-open-motion-clean`. `compare-pad-fixes` plays seconds 4-12 of each in that order. All four measure -14.0 LUFS, true peak -1.1 to -1.2 dBTP, 20.000 s.

## Master boost test

The user asked for all the pad fixes (q3) and also a test of the master boost. The master adds a fixed boost above 9 kHz ("air", 0.22) and at 2-5 kHz ("presence", 0.2) to the whole mix. That can make everything sound fizzy.

New option: `bright_cue.py --master-boost N` scales both boosts. 1 is the current amount, 0 turns them off.

Files in `probe/master/`. Every mix uses the A7 lead and open-voiced Juno-60 Strings pads with `--pad-motion --pad-clean`, which is q3.

| File | Change |
| --- | --- |
| `m0-boost-full` | boost as before (same as q3) |
| `m1-boost-half` | boost at 0.5 |
| `m2-boost-off` | no boost |
| `m3-boost-off-all-surge` | no boost, plus Surge The 1980s arpeggio and Bass 1 |

`compare-master-boost` plays seconds 4-12 of each in that order. All four measure -14.0 LUFS, true peak -1.2 to -1.4 dBTP, 20.000 s.

## Final candidate

The user could not hear a difference between the four master-boost mixes ("i honestly couldn't tell any diff between them"). With loudness matched, turning the boost off changes the 2-5 kHz band by -0.9 dB and the band above 9 kHz by -0.7 dB. Swapping in the Surge arpeggio and bass (m3) was not heard either. The user said: "if there's no difference keep things simple by eliminating things". So these were removed from the code:

- the master air and presence boosts (`--master-boost`, and the polish-mode values)
- `bright_cue.py --arp-wav/--bass-wav` and `surge_lead.py --part arp/bass`. The cue uses the in-house arpeggio and bass.

The `m*` and band files from the earlier rounds were rendered with the code before this cleanup.

The user then said: "ok music is good enough". The final candidate is in `probe/final/`:

- `bright-cue-candidate.mp3` (wet): -14.0 LUFS, -1.4 dBTP, 20.000 s
- `bright-cue-candidate-dry.mp3`: -14.1 LUFS, -1.3 dBTP, 20.000 s
- Recipe:
  - A7 lead (JX-10 Double Brass + Anthemish 1)
  - open-voiced Surge Juno-60 Strings pads with `--pad-motion --pad-clean`
  - in-house arpeggio, bass and drums
  - no master boost
- `bright-cue-candidate-performance.mp4` is the wet mix under `art/blender/demo/performance-monochrome.webm` (480 frames at 24 fps, 20.000 s). It is a review copy only: after AAC encoding it peaks at +0.1 dBTP.

Command:

    art/audio/.venv-surge/bin/python art/audio/surge_lead.py "Brass/JX-10 Double Brass" --out jx10.wav
    art/audio/.venv-surge/bin/python art/audio/surge_lead.py "Polysynths/Anthemish 1" --out anthemish.wav
    # layer: normalize each to its bars 3-6 RMS, sum, peak-normalize to 0.9 -> layer_JX10_Anthemish.wav
    art/audio/.venv-surge/bin/python art/audio/surge_lead.py "Polysynths/Juno-60 Strings" --part pad --voicing open --out pad_juno60_open.wav
    art/audio/.venv/bin/python art/audio/bright_cue.py --lead-wav layer_JX10_Anthemish.wav \
      --pad-wav pad_juno60_open.wav --pad-motion --pad-clean --name bright-cue-candidate

The twelve app mixes (3 arrangements x 4 members) have not been made. App audio in `app/src/main/assets/demo/audio/` is unchanged.
