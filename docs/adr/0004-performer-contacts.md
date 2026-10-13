# ADR 0004: Fit playing joints to instrument surfaces

- Status: in progress (drum correction verified; keyboard contact, pedal feet and fingers remain)
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
for all twelve motions. All 1,504 physical strikes pass, with a maximum measured contact gap under
1 mm. Three rendered stills were inspected, and one warm/driving/drummer review video was rendered
separately from the app assets.

```sh
/Applications/Blender.app/Contents/MacOS/Blender --background /Users/auchan/projects/signal-room/art/blender/demo/signal-room-performance.blend --threads 1 --python-exit-code 1 --python /Users/auchan/projects/signal-room/art/blender/tests/verify_drum_contacts.py
```

The bundled 36 videos have not yet been regenerated. After the remaining contact details are
checked, regenerate them one at a time, validate all media before writing manifest hashes, then
build and test the APK. Main remains `d984f1d`; playtest diagnostics are in draft PR #6.
