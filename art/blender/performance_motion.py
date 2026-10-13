"""Per-frame joint angles for the twenty-second performance, driven by the cue's note and drum timings.

Plain Python (no bpy) so the motion can be unit-tested. `channels(events)` returns
{control name: {axis: [angle per frame]}} for frames 1..480; prepare_performance.py keys them.

Angles are radians, offsets from the rest pose. Axis signs were measured in the scene:
elbow x+ drops the hand (strike), shoulder z+ moves the hand toward +x, torso and head x+ lean
toward the camera (which barely shows from the front), torso and head y rock sideways, and on the
singer's free arm shoulder y+ raises the arm out to the side (2.5 is overhead). Wrist x+ tips the
hand (and stick) down, like the elbow.

Timing rules are in docs/adr/0001-performer-motion.md: contacts land on the frame at or before the
audio, strokes accelerate into contact and rebound out of it, and lift follows loudness.
"""

import json
import math
from pathlib import Path


FPS = 24
FRAMES = 480
BEAT = 0.5
BAR = 2.0
EVENTS = Path(__file__).resolve().parent / "demo" / "performance-events.json"
# Singer's new-wave hair: short sides (the original cap) and a top swept up and over to camera left,
# with one long peak at the front. Each tuft: base (x, y) on the crown relative to the head centre
# (camera is toward -y), lean (sideways, backward) in radians from straight up, and a length scale.
# Also: segment lengths, base width and thickness, taper toward the tip, curl back per segment.
HAIR = dict(
    tufts=((0.07, -0.06, -0.55, -0.2, 1.0), (0.0, -0.075, -0.7, -0.25, 1.4), (-0.07, -0.06, -0.85, -0.15, 1.2),
           (0.07, 0.01, -0.5, 0.3, 0.9), (0.0, 0.0, -0.65, 0.25, 1.05), (-0.07, 0.01, -0.8, 0.3, 0.95),
           (0.04, 0.07, -0.45, 0.8, 0.7), (-0.04, 0.07, -0.6, 0.8, 0.75)),
    segments=(0.08, 0.07, 0.05), width=0.115, thick=0.1, taper=0.68, curl=0.3)
HAIR_TUFTS = HAIR["tufts"]
HAIR_SEGMENTS = HAIR["segments"]
LOUD = 1.33  # rock and nod gain in the loud sections; the research favours more motion on upbeat music


def load_events(path=EVENTS):
    return json.loads(Path(path).read_text())


def at(bar, step=0):
    return (bar - 1) * BAR + step * BEAT / 4


def times():
    return [(frame - 1) / FPS for frame in range(1, FRAMES + 1)]


def section(t):
    bar = int(t // BAR) + 1
    if bar <= 2:
        return "intro"
    if bar <= 6:
        return "drop"
    if bar == 7:
        return "breakdown"
    return "reentry" if t < at(10, 8) else "ending"


def round_frame(t):
    """Index into a per-frame track for time t."""
    return round(t * FPS)


def contact(t):
    """The frame time at or just before t: a picture slightly early reads as in sync, a late one does not."""
    return math.floor(t * FPS + 1e-6) / FPS


EASES = {
    "smooth": lambda x: 3 * x * x - 2 * x * x * x,
    "in": lambda x: x * x,  # accelerate into an impact
    "out": lambda x: 1 - (1 - x) * (1 - x),  # leave an impact fast, slow at the top
}


def smooth(a, b, x, ease="smooth"):
    x = min(max(x, 0.0), 1.0)
    return a + (b - a) * EASES[ease](x)


def sample(keys, default=0.0):
    """Ease between (time, value[, ease]) keys; hold the first and last value outside them.

    A key's ease shapes the move that arrives at it (smoothstep unless given).
    """
    keys = sorted(keys)
    out = []
    i = 0
    for t in times():
        if not keys:
            out.append(default)
            continue
        while i + 1 < len(keys) and keys[i + 1][0] <= t:
            i += 1
        if t <= keys[0][0]:
            out.append(keys[0][1])
        elif i + 1 >= len(keys):
            out.append(keys[-1][1])
        else:
            (t0, v0, *_), (t1, v1, *ease) = keys[i], keys[i + 1]
            out.append(smooth(v0, v1, (t - t0) / (t1 - t0), *ease))
    return out


def strokes(hits, down, lift, rest=0.0, max_lift=0.16):
    """Stick strokes: wind up, accelerate into each hit, rebound off it, settle after long gaps.

    hits: [(time, gain)]. The lift scales with the hit's gain and the time available.
    """
    keys = [(-1.0, rest)]
    previous = None
    for when, gain in sorted(hits):
        when = contact(when)
        gap = when - previous if previous is not None else 1.0
        if gap > 0.45:
            if previous is not None:
                keys.append((previous + 0.08, rest + lift * 0.4 * previous_gain, "out"))
                keys.append((previous + 0.25, rest))
            keys.append((when - max_lift, rest + lift * gain))
        else:
            keys.append((when - gap / 2, rest + lift * gain * min(1.0, gap / 0.25), "out"))
        keys.append((when, rest + down * gain, "in"))
        previous, previous_gain = when, gain
    if previous is not None:
        keys.append((previous + 0.08, rest + lift * 0.4 * previous_gain, "out"))
        keys.append((previous + 0.25, rest))
    return sample(keys, rest)


def _ordered(keys):
    """Drop keys that would step back in time, so overlapping moves never jump."""
    out = []
    for key in keys:
        if not out or key[0] > out[-1][0] + 1e-4:
            out.append(key)
    return out


def presses(notes, depth, lift=0.0):
    """Key presses held for the note length: (time, duration, gain); the hand comes up between notes."""
    notes = sorted(notes)
    keys = [(-1.0, 0.0)]
    for i, (when, dur, gain) in enumerate(notes):
        following = notes[i + 1][0] if i + 1 < len(notes) else math.inf
        when = contact(when)
        if when - keys[-1][0] > 0.12:
            keys.append((when - 0.08, lift * gain))
        keys.append((when, depth * gain, "in"))
        end = min(when + max(dur - 0.05, 0.06), following - 0.09)
        if end > when + 0.02:
            keys.append((end, depth * gain * 0.8))
        release = min(keys[-1][0] + 0.05, following - 0.09)
        if release > keys[-1][0]:
            keys.append((release, lift * gain, "out"))
    return sample(_ordered(keys))


def glide(targets, lead=0.12):
    """Move to each (time, value) target, arriving just before it and holding until the next one.

    No impact here, so the move eases both ways (a hand changing position between chords).
    """
    keys = []
    for when, value in sorted(targets):
        if keys:
            keys.append((max(when - lead, keys[-1][0] + 0.01), keys[-1][1]))
        keys.append((when - 0.02, value))
    return sample(_ordered(keys))


def pulses(beats, amount, decay=0.2, attack=0.05):
    """Sum of quick lean-in, slow return pulses on the given (time, gain) beats."""
    out = []
    for t in times():
        value = 0.0
        for when, gain in beats:
            dt = t - when
            if -attack <= dt < 0:
                value += amount * gain * smooth(0, 1, (dt + attack) / attack)
            elif 0 <= dt < decay * 5:
                value += amount * gain * math.exp(-dt / decay)
        out.append(value)
    return out


def rock(beats, amount, decay=0.25):
    """Side-to-side rock: like pulses, but alternating direction on each beat."""
    out = [0.0] * FRAMES
    for i, beat in enumerate(beats):
        sign = 1 if i % 2 == 0 else -1
        for frame, value in enumerate(pulses([beat], amount * sign, decay)):
            out[frame] += value
    return out


def add(*tracks):
    return [sum(values) for values in zip(*tracks)]


def scale(track, k):
    return [k * v for v in track]


def shifted(track, frames):
    return [track[0]] * frames + track[:-frames] if frames > 0 else list(track)


def section_beats(levels, every=1, offset=0):
    """(time, gain) per beat, gain from the section level; every=2, offset=1 gives beats 2 and 4."""
    beats = []
    for beat in range(offset, 40, every):
        when = beat * BEAT
        gain = levels.get(section(when), 0.0)
        if gain:
            beats.append((when, gain))
    return beats


def sway(amount_by_section, period=2 * BAR, phase=0.0):
    return [amount_by_section.get(section(t), 0.0) * math.sin(2 * math.pi * (t / period + phase)) for t in times()]


def spotlit(ev, member, levels):
    """Section levels for a member: in a mix that features them, they keep moving in the breakdown
    (bar 7), where the rest of the band is nearly still."""
    if ev.get("featured") != member:
        return levels
    return {**levels, "breakdown": levels["drop"]}


def final_hold(value, ramp=0.12):
    """0 until the final stab, then `value` held to the end."""
    stab = at(10, 8)
    return sample([(stab - ramp, 0.0), (stab, value)])


def drummer(ev):
    drums = [d for d in ev["drums"] if d["kind"] != "kick"]
    crashes = ev["crashes"]
    near_crash = lambda t: any(abs(t - c) < 0.06 for c in crashes)
    # (time, arm gain, wrist gain): ghost notes are mostly wrist, accents use the whole arm
    left, right = [], []
    rolls = 0
    for d in drums:
        t, g, kind = d["time"], d["gain"], d["kind"]
        if near_crash(t):
            continue
        if kind in ("hat", "open_hat"):
            right.append((t, 0.35 + 0.65 * g, 0.7))
        elif kind == "snare":
            left.append((t, 1.0, 1.0))
        elif kind == "ghost":
            left.append((t, 0.12, 0.5))
        else:  # roll and tom strokes alternate hands
            level = 0.45 + 0.5 * min(g / 0.8, 1)
            (right if rolls % 2 else left).append((t, level, 0.8))
            rolls += 1
    for c in crashes:
        left.append((c, 1.4, 1.0))
        right.append((c, 1.4, 1.0))
    arm = lambda hits: [(t, g) for t, g, _ in hits]
    wrist = lambda hits: [(t, w) for t, _, w in hits]
    kicks = [(d["time"], 1.0) for d in ev["drums"] if d["kind"] == "kick"]
    snares = [(d["time"], 1.0) for d in ev["drums"] if d["kind"] == "snare"]
    accents = snares + [(c, 2.0) for c in crashes]
    lean = add(pulses(kicks, 0.035, decay=0.12), pulses(accents, 0.03, decay=0.15), final_hold(0.09))
    if ev.get("featured") == "drummer":  # the drum break in bar 7: the whole body drives it
        lean = add(lean, pulses([(t, g) for t, g in kicks if at(7) <= t < at(8)], 0.03, decay=0.12))
    crash_reach = sample([k for c in crashes for k in ((c - 0.2, 0.0), (c - 0.04, -0.35), (c + 0.25, 0.0))])
    # the snare (left) hand's shoulder drops into each accent, and the body tips toward it
    snare_shoulder = add(crash_reach, pulses(snares, 0.06, decay=0.1, attack=0.08))
    return {
        "04 Sleeveless elbow": {"x": strokes(arm(left), down=0.36, lift=-0.48)},
        "04 Sleeveless.001 elbow": {"x": strokes(arm(right), down=0.33, lift=-0.4)},
        "04 Sleeveless wrist": {"x": strokes(wrist(left), down=0.22, lift=-0.3, max_lift=0.1)},
        "04 Sleeveless.001 wrist": {"x": strokes(wrist(right), down=0.2, lift=-0.26, max_lift=0.1)},
        "04 Sleeveless shoulder": {"x": snare_shoulder},
        "04 Sleeveless.001 shoulder": {"x": crash_reach},
        "04 Sleeveless torso": {"x": lean, "y": rock(snares, 0.03 * LOUD, decay=0.2)},
        "04 Sleeveless nod": {"x": shifted(pulses(kicks, 0.07 * LOUD, decay=0.14), 2),
                              "y": shifted(rock([k for k in kicks if (k[0] / BEAT) % 1 == 0], 0.05 * LOUD), 2)},
    }


# Arm poses found by searching for hand positions in the scene (see the ADR, pass 3).
KEYBOARD_RAISE = {"shoulder": {"x": -2.188, "z": 0.519}, "elbow": {"x": 0.294}}  # free hand up, elbow out
# The keytar sits higher and closer than in the four-second demo so both hands can be in front of it.
KEYTAR_OFFSET = (0.0, 0.07, 0.12)
KEYTAR_KEYS_GRIP = (1.32, -0.79, 1.31)  # keys hand in front of the keys; the keytar tilts about this point
KEYTAR_KEYS_POSE = {"shoulder": {"x": -0.3, "y": -0.333}, "elbow": {"x": 0.281}}
# neck hand on the front of the neck as the keytar tilts: (tilt y, (shoulder x, y, z, elbow x));
# negative tilt raises the neck
KEYTAR_NECK = ((0.0, (-0.191, -0.159, 0.3, -0.052)), (-0.15, (-0.303, -0.051, 0.3, -0.38)),
               (-0.3, (-0.392, 0.347, 0.3, -0.567)), (-0.45, (-0.476, 0.778, 0.15, -0.6)))
FLIPS = (at(3), at(8), at(10, 8))  # drop, re-entry, final stab: the big moments


def keyboardist(ev):
    # in a mix that features the keyboardist, the right hand leaves the hook for the arpeggio in bar 7
    arp = ev.get("arp") or []
    hook = [n for n in ev["lead"] if not (arp and at(7) <= n["time"] < at(8))]
    right = sorted(hook + arp, key=lambda n: n["time"])
    lead = [(n["time"], n["dur"], 1.0 if n["dur"] >= 0.25 else 0.8) for n in right]
    # player's right hand (camera left, -x) plays the hook across the keyboard: high notes toward -x
    # the arpeggio stays in one hand position (around A4), the fingers covering most of its span
    reach = glide([(n["time"], -0.36 * (n["note"] - 76) / 8) for n in hook]
                  + [(n["time"], 0.3 - 0.12 * (n["note"] - 69) / 12) for n in arp] or [(0, 0)])
    # the right hand waits above the keys in the intro and comes down on the first hook note,
    # then leaves the keys with a lift on the final stab
    first = contact(ev["lead"][0]["time"]) if ev["lead"] else at(3)
    entry = sample([(at(2), 0.0), (at(2, 8), -0.45), (first - 0.35, -0.5), (first - 0.08, 0.0, "in")])
    stab = at(10, 8)
    exit_ = sample([(stab + 0.3, 0.0), (stab + 0.55, -0.7, "out")])  # after the stab note sounds
    raise_start, raise_end = at(8), at(9)  # call P: the free hand goes up at the re-entry only
    chord_list = [(c["time"], BAR - 0.08, 1.0) for c in ev["chords"]]
    chord_list += [(c["time"] + BAR / 2, BAR / 2 - 0.08, 0.6) for c in ev["chords"] if section(c["time"]) in ("drop", "reentry")]
    chords = [c for c in chord_list if not raise_start - 0.6 <= c[0] < raise_end]
    held = sample([(raise_start - 0.5, 0.0), (raise_start, 1.0), (raise_end - 0.2, 1.0), (raise_end + 0.3, 0.0)])
    chord_root = {"Am": 0.0, "F": 0.08, "C": -0.05, "G": 0.04}
    levels = spotlit(ev, "keyboardist", {"intro": 0.4, "drop": 1.0, "breakdown": 0.0, "reentry": 1.2, "ending": 1.2})
    beats = section_beats(levels)
    lean = add(pulses(beats, 0.05), sway({"breakdown": 0.04, "intro": 0.02}, period=BAR), final_hold(0.1))
    give = shifted(pulses([(t, g) for t, _, g in chords], 0.1, decay=0.12, attack=0.04), 1)
    pose = lambda joint, axis, base: add(scale(held, KEYBOARD_RAISE[joint].get(axis, 0.0)), scale(base, 1.0))
    return {
        "02 Long vest elbow": {"x": add(presses(lead, 0.2, lift=-0.2), entry, exit_)},
        "02 Long vest shoulder": {"z": reach},
        "02 Long vest.001 elbow": {"x": pose("elbow", "x", presses(chords, 0.12, lift=-0.1))},
        "02 Long vest.001 wrist": {"x": give},
        "02 Long vest.001 shoulder": {"x": pose("shoulder", "x", [0.0] * FRAMES),
                                      "z": pose("shoulder", "z", glide([(c["time"], chord_root[c["chord"]]) for c in ev["chords"]]))},
        "02 Long vest torso": {"x": lean, "y": add(rock(beats, 0.035 * LOUD), sway({"breakdown": 0.03}, period=BAR, phase=0.25))},
        "02 Long vest nod": {"x": shifted(pulses(beats, 0.1 * LOUD), 2), "y": shifted(rock(beats, 0.08 * LOUD), 2)},
    }


def _blend_pose(table, tilt):
    """Linear interpolation of an arm pose table by tilt (table sorted from 0 toward negative)."""
    for (t0, p0), (t1, p1) in zip(table, table[1:]):
        if t1 <= tilt <= t0:
            x = (tilt - t0) / (t1 - t0)
            return [a + (b - a) * x for a, b in zip(p0, p1)]
    return list(table[-1][1] if tilt < table[-1][0] else table[0][1])


def _keys_depth_fix(d):
    """Shoulder x that keeps the keys hand at the same depth when the shoulder z slide changes by d.

    The slide swings the hand in an arc about the shoulder, which would push it into the keys.
    Fitted in the scene: residual under 2 mm for slides within 0.3 rad.
    """
    return -0.45 * d - 0.5 * d * d


def keytarist(ev):
    # the neck goes up on the drop and the re-entry, and stays up from the final stab
    tilt = sample([(at(3) - 0.3, 0.0), (at(3), -0.4), (at(4) - 0.5, -0.4), (at(4), 0.0),
                   (at(8) - 0.3, 0.0), (at(8), -0.4), (at(9) - 0.5, -0.4), (at(9), 0.0),
                   (at(10, 8) - 0.3, 0.0), (at(10, 8), -0.45)])
    neck_pose = [_blend_pose(KEYTAR_NECK, t) for t in tilt]
    free = [1.0 - min(abs(t) / 0.4, 1.0) for t in tilt]  # the keys hand stays near the pivot while tilted
    # the keys hand plays the bass line; in a mix that features the keytar it leaves the bass for the
    # answer phrases (and the bar 7 run), sliding up the keys with their pitch
    answers = ev.get("answers") or []
    busy = [(n["time"] - 0.1, n["time"] + n["dur"] + 0.05) for n in answers]
    playing = [n for n in ev["bass"] if not any(a <= n["time"] < b for a, b in busy)]
    bass = [(n["time"], n["dur"], 1.0 if n["dur"] > 0.3 else 0.75) for n in playing + answers]
    slide = glide([(n["time"], 0.3 * ((n["note"] % 12) - 5) / 6) for n in playing]
                  + [(n["time"], -0.3 + 0.6 * (n["note"] - 69) / 19) for n in answers] or [(0, 0)])
    keys_z = [a * f for a, f in zip(slide, free)]
    neck = {"Am": 0.0, "F": -0.06, "C": 0.05, "G": 0.03}
    chord_slide = glide([(c["time"], neck[c["chord"]]) for c in ev["chords"]])
    levels = spotlit(ev, "keytar", {"intro": 0.3, "drop": 1.0, "breakdown": 0.0, "reentry": 1.3, "ending": 1.3})
    backbeat = section_beats(levels, every=2, offset=1)
    lean = add(pulses(backbeat, 0.07, decay=0.25), sway({"breakdown": 0.05}, period=BAR, phase=0.5), final_hold(0.12))
    return {
        "Keytar tilt": {"y": tilt},
        "03 Oversized blazer elbow": {"x": [v + KEYTAR_KEYS_POSE["elbow"]["x"] for v in presses(bass, 0.1, lift=-0.1)]},
        "03 Oversized blazer shoulder": {"x": [KEYTAR_KEYS_POSE["shoulder"]["x"] + _keys_depth_fix(z - keys_z[0]) for z in keys_z],
                                         "y": [KEYTAR_KEYS_POSE["shoulder"]["y"]] * FRAMES,
                                         "z": keys_z},
        "03 Oversized blazer.001 shoulder": {"x": [p[0] for p in neck_pose], "y": [p[1] for p in neck_pose],
                                             "z": add([p[2] for p in neck_pose], [a * f for a, f in zip(chord_slide, free)])},
        "03 Oversized blazer.001 elbow": {"x": [p[3] for p in neck_pose]},
        "03 Oversized blazer torso": {"x": lean, "y": rock(section_beats(levels), 0.07 * LOUD, decay=0.3),
                                      "z": sway({"drop": 0.04, "reentry": 0.05}, period=2 * BAR)},
        "03 Oversized blazer nod": {"x": shifted(pulses(backbeat, 0.13 * LOUD, decay=0.25), 4),
                                    "y": shifted(rock(section_beats(levels), 0.07 * LOUD), 4)},
    }


def hair_swing(head, kick=(), stiffness=100.0, gravity=40.0, damping=5.0, segments=3):
    """Relative angles for a hanging chain of hair segments following a head angle track.

    Each segment springs toward its parent's angle and gravity pulls it toward hanging straight,
    so the lower segments lag the upper ones (overlapping action). kick: (time, angular velocity)
    pushes on the chain, e.g. when the singer tosses their head.
    """
    steps = 8
    dt = 1.0 / FPS / steps
    angle = [0.0] * segments
    speed = [0.0] * segments
    kicks = sorted(kick)
    out = [[] for _ in range(segments)]
    for frame, t in enumerate(times()):
        while kicks and kicks[0][0] <= t:
            for i in range(segments):
                speed[i] += kicks[0][1] * (0.6 + 0.2 * i)
            kicks.pop(0)
        for _ in range(steps):
            parent = head[frame]
            for i in range(segments):
                force = -stiffness * (angle[i] - parent) - gravity * angle[i] - damping * speed[i]
                speed[i] += force * dt
                angle[i] += speed[i] * dt
                parent = angle[i]
        parent = head[frame]
        for i in range(segments):
            out[i].append(max(-0.6, min(0.6, angle[i] - parent)))
            parent = angle[i]
    return out


def singer(ev):
    # free arm (camera left): shoulder y+ raises it out to the side; poses found by searching
    # for hand positions that read from the front camera (shoulder x, shoulder y, elbow x, elbow y)
    poses = {"open": (0.0, 0.95, 0.0, 0.2), "point": (0.0, 1.45, 0.0, 0.25), "fist": (0.0, 2.5, 0.0, 0.5)}
    gestures = [
        (at(3), at(4, 12), "point"),
        (at(5), at(6, 8), "open"),
        (at(8), at(9, 12), "fist"),
        (at(10, 8), 20.5, "fist"),
    ]
    flips = FLIPS
    if ev.get("featured") == "frontman":  # the breakdown is theirs: arm out and a head toss
        gestures.insert(2, (at(7), at(7, 12), "point"))
        flips = tuple(sorted(FLIPS + (at(7),)))
    keys = {axis: [(-1.0, 0.0)] for axis in range(4)}
    for i, (start, end, name) in enumerate(gestures):
        ramp = 0.55 if name == "fist" else 0.3  # the overhead raise travels furthest
        following = gestures[i + 1][0] if i + 1 < len(gestures) else math.inf
        for axis in range(4):
            if keys[axis][-1][1] == 0.0 and keys[axis][-1][0] < start - ramp:  # arm is down: lift from rest
                keys[axis].append((start - ramp, 0.0))
            keys[axis] += [(start, poses[name][axis]), (end, poses[name][axis])]
            if end + 0.5 < following - 0.6:  # drop the arm only if the next gesture is not close
                keys[axis].append((end + 0.5, 0.0))
    levels = spotlit(ev, "frontman", {"intro": 0.3, "drop": 0.8, "breakdown": 0.0, "reentry": 1.0, "ending": 1.0})
    beats = section_beats(levels)
    lean = add(pulses(section_beats(levels, every=2), 0.04, decay=0.3), final_hold(-0.08))
    # a head toss lands on each big moment, alternating sides; the hair swings out after it
    toss_keys = []
    for i, when in enumerate(flips):
        side = 1 if i % 2 == 0 else -1
        toss_keys += [(when - 0.2, 0.0), (when, 0.22 * side, "in"), (when + 0.35, -0.06 * side, "out"), (when + 0.9, 0.0)]
    toss = sample(toss_keys)
    torso_y = add(rock(beats, 0.035 * LOUD), sway({"breakdown": 0.03, "intro": 0.02}, period=BAR))
    nod_y = add(shifted(rock(beats, 0.09 * LOUD), 3), toss)
    nod_x = add(shifted(pulses(beats, 0.08 * LOUD), 3), final_hold(-0.12))
    channels = {
        "01 Cropped jacket shoulder": {"x": sample(keys[0]), "y": sample(keys[1])},
        "01 Cropped jacket elbow": {"x": sample(keys[2]), "y": sample(keys[3])},
        "01 Cropped jacket torso": {"x": lean, "y": torso_y},
        "01 Cropped jacket nod": {"x": nod_x, "y": nod_y},
    }
    head_y, head_x = add(torso_y, nod_y), add(lean, nod_x)
    for lock in range(len(HAIR_TUFTS)):
        k = 100.0 * (1 + 0.12 * math.sin(lock * 1.7))  # locks differ a little so they fan out
        kicks = [(when, (-3.0 if i % 2 == 0 else 3.0) * (1 + 0.15 * math.cos(lock))) for i, when in enumerate(flips)]
        side = hair_swing(head_y, kicks, stiffness=k, segments=len(HAIR_SEGMENTS))
        front = hair_swing(head_x, stiffness=k * 1.1, segments=len(HAIR_SEGMENTS))
        for segment in range(len(HAIR_SEGMENTS)):
            channels[f"01 Cropped jacket hair {lock} {segment}"] = {"x": front[segment], "y": side[segment]}
    return channels


def channels(ev=None):
    ev = ev or load_events()
    out = {}
    for part in (drummer, keyboardist, keytarist, singer):
        out.update(part(ev))
    return out
