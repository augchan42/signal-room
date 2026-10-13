# ADR 0004: Fit playing joints to instrument surfaces

- Status: in progress (contact corrections implemented; video export and device check remain)
- Date: 2026-10-13
- Scope: performance export in `art/blender/render_mix_performances.py`

## Problem

The motion curves put key poses on the audio frame but did not prove physical contact. A coarse
inspection of all twelve mix motions found 1,503 of 1,575 drumstick contacts more than 5 cm from
every pad. The new assigned-pad check failed on the existing scene with an 18.8 cm gap.

## Drum correction

`performance_contacts.py` fits the shoulder's three rotation axes, elbow and wrist to a stick-tip
trajectory. The rigid meshes stay attached to their joints, so the hand retains its grip. Torso,
head, lighting and audio motion/timing are unchanged. The original performance scene remains the
source; the correction runs after each mix's authored motion is applied, before rendering.

The left home pad is snare, the right home pad hi-hat. Rolls use the hands' home pads; toms move
between the home and outer pads on each side. Crashes use the outer pads. When audio events
assign simultaneous sounds to one hand, they share one electronic-pad strike: the more prominent
sound chooses the pad, and the coincident kinds are recorded. This yields 1,504 physical strikes.

The existing anticipation/impact/rebound easing and gain-scaled lift drive the target path. A
damped numerical joint fit follows that path on each of the 480 frames. It fails export if a target
is more than 8 mm away or a joint changes more than 0.7 radians per frame. These are engineering
guards, not research claims.

## Verification and current assets

The geometry test checks the actual transformed stick tip against the assigned pad at every hit
for all twelve motions. All 1,504 physical strikes pass, with a maximum measured target-fit error
under 1 mm. The target is deliberately 1 mm above the pad face, so the measured physical surface
clearance can be up to 2 mm; this is not a claim of sub-millimetre surface contact.
Three rendered stills were inspected, and one warm/driving/drummer review video was rendered
separately from the app assets.

```sh
/Applications/Blender.app/Contents/MacOS/Blender --background /Users/auchan/projects/signal-room/art/blender/demo/signal-room-performance.blend --threads 1 --python-exit-code 1 --python /Users/auchan/projects/signal-room/art/blender/tests/verify_drum_contacts.py
```

The bundled 36 videos have not yet been regenerated. After the remaining contact details are
checked, regenerate them one at a time, validate all media before writing manifest hashes, then
build and test the APK. Main remains `d984f1d`; playtest diagnostics are in draft PR #6.

## Keyboard and keytar hands

`performance_hands.py` adds three two-segment fingers and a thumb to each playing/grip hand.
The fingers flex on note events. The middle fingertip is fitted to the selected physical key;
the two neighbouring fingers follow with smaller flex. This is a stylized playing hand, not
independent piano fingering or a full chord fingering system.

The BORG right hand follows the hook and featured arpeggio. Its left hand plays chord roots,
with no playing contact during the raised-hand gesture. That gesture now returns by 16 seconds,
before the bar-9 chord; the original 16.3-second return caused an abrupt corrective arm movement.
Contact correction fades in and out around playing, preserving the authored idle poses. The
finger holds the current key, then lifts and glides to the next one. Contiguous phrases release
for the last two displayed frames before the next note, with at least one displayed frame held.
Synth audio tails can continue after finger release; the audio files are not shortened.

The keytar's smaller key range folds high notes down by octaves while retaining pitch class.
Its keys hand follows bass/answer events, and its neck hand stays on the touch strip. The keytar
moves 12 cm toward the keys hand so its highest keys are reachable. This changes staging, not audio.

The targets are the actual key mesh faces (BORG top Z, keytar front Y), not an offset from them.
All 1,541 key-note contacts across the twelve motions pass with measured gaps under 1 mm.
The verifier also checks 12,866 held-key frames, 5,760 neck-grip frames and the corrected
raised-hand pose in each motion. A separate pure test covers continuity at the travel/lift boundary.
The same five-axis joint fit is shared with drums in `rigid_joint_fit.py`. It checks the target
error and frame-to-frame angular change before export. `verify_key_contacts.py` checks the actual
transformed fingertip at every assigned note, rather than checking only joint-angle peaks.

## Pedal feet

`performance_pedals.py` adds a toe plate under the drummer's kick and hi-hat feet and the
keyboardist's sustain foot. It hides the old sustain pedal, which was in front of the boot.
Each boot and its trim rotate about a fixed heel; its sole stays on the hinged plate.

Kick presses and hi-hat openings follow the mix's actual `kick` and `open_hat` events. Each closed
`hat` event returns the pedal to its closed position by that strike, rather than using a fixed
open duration. Sustain
holds follow the chord events. Presses use the same floored audio frame as hand contacts and
release between events. `verify_pedal_contacts.py` checks heel position and sole/plate contact
on all 480 frames of all twelve motions, requires movement where there are events, and checks
event-frame poses. Sparse mixes with no open-hi-hat events keep that pedal closed.

These checks establish geometry and timeline alignment in exported frames. They do not establish
the separate 50 ms physical audio/video output-sync target on a phone.

The pedal geometry run checked 17,280 sole/heel frame combinations and 104 open-hi-hat events.
The first geometry test failed because the kick pedal was missing; a separate event test caught
the incorrect `openhat` spelling before export. The corrected test also covers sparse mixes with
no open-hi-hat events instead of requiring movement for notes that are not played.
