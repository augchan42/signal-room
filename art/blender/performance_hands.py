"""Simple articulated key-playing fingers and instrument-surface contact."""
import math


def key_for_note(note, keytar=False):
    # The BORG spans C2–B6; the smaller keytar transposes higher phrases down.
    while keytar and note > 65:
        note -= 12
    while note < 36:
        note += 12
    octave, pitch = divmod(note - 36, 12)
    white = (0, 2, 4, 5, 7, 9, 11)
    black = pitch not in white
    previous = pitch - 1 if black else pitch
    index = octave * 7 + white.index(previous)
    prefix = "Keytar" if keytar else "BORG main synthesizer"
    return f"{prefix} {'black' if black else 'white'} key {index:02d}"


def playing_notes(events):
    import performance_motion as motion
    arp = events.get("arp") or []
    right = [n for n in events["lead"] if not (arp and 12 <= n["time"] < 14)] + arp
    roots = {"Am": 57, "F": 53, "C": 48, "G": 55}
    left = []
    for chord in events["chords"]:
        when = chord["time"]
        for offset, duration in ((0, 1.92), (1.0, .92)):
            t = when + offset
            if offset and motion.section(when) not in ("drop", "reentry"):
                continue
            if 13.4 <= t < 16:
                continue
            end = min(t + duration, 13.35) if t < 13.4 else t + duration
            if end > t:
                left.append(dict(time=t, dur=end-t, note=roots[chord["chord"]]))
    answers = events.get("answers") or []
    busy = [(n["time"]-.1, n["time"]+n["dur"]+.05) for n in answers]
    keytar = [n for n in events["bass"] if not any(a <= n["time"] < b for a, b in busy)] + answers
    return {"02 Long vest hand": sorted(right, key=lambda n: n["time"]),
            "02 Long vest hand.001": sorted(left, key=lambda n: n["time"]),
            "03 Oversized blazer hand": sorted(keytar, key=lambda n: n["time"])}


def _parent_keep(obj, parent):
    matrix = obj.matrix_world.copy()
    obj.parent = parent
    obj.matrix_world = matrix


def add_fingers(hand, reverse=False):
    import bpy
    from mathutils import Vector
    # Flatten the palm along the instrument normal; retain the shared hand mesh.
    hand.data = hand.data.copy()
    for vertex in hand.data.vertices:
        vertex.co[1 if reverse else 2] *= .45
    material = hand.data.materials[0]

    def segment(name, parent, location, length, width, drop):
        vertices = [(x, y, z - drop * (-y / length))
                    for y in (0, -length) for z in (-.009, .009) for x in (-width/2, width/2)]
        mesh = bpy.data.meshes.new(name)
        mesh.from_pydata(vertices, [], [(0,1,3,2),(4,6,7,5),(0,4,5,1),(2,3,7,6),(0,2,6,4),(1,5,7,3)])
        mesh.materials.append(material)
        obj = bpy.data.objects.new(name, mesh)
        bpy.context.scene.collection.objects.link(obj)
        obj.parent, obj.location = parent, location
        bevel = obj.modifiers.new("Finger edges", "BEVEL")
        bevel.width, bevel.segments = .003, 2
        return obj

    fingers = []
    for i, x in enumerate((0, -.025, .025)):
        joint = bpy.data.objects.new(hand.name + f" finger {i} joint", None)
        bpy.context.scene.collection.objects.link(joint)
        joint.parent = hand
        joint.location = (x, .035 if reverse else -.035, 0)
        joint.rotation_euler.z = math.pi if reverse else 0
        segment(hand.name + f" finger {i} proximal", joint, (0,0,0), .045, .019, .014)
        tip = segment(hand.name + f" finger {i} distal", joint, (0,-.045,-.014), .040, .017, .008)
        fingers.append((joint, tip, Vector((0, -.040, -.017))))
    segment(hand.name + " thumb", hand, (.042, 0, -.004), .040, .026, .014)
    return fingers


def _weight(t, notes):
    # Contact correction fades in/out around playing; the raised-hand gesture survives.
    value = 0.0
    for n in notes:
        contact = math.floor(n["time"] * 24 + 1e-6) / 24
        begin, end = contact - .12, contact + n["dur"] + .10
        if begin <= t <= end:
            value = max(value, min(1, (t-begin)/.12, (end-t)/.10))
    return max(0, value)


def note_pose(t, notes):
    """Hold a key, release for travel, and arrive at the next floored note frame.

    Synth tails may continue after the finger releases. A contiguous phrase gives
    the hand its last two frames to lift/travel, but every note is held for at
    least one displayed frame. Longer gaps preserve the authored idle movement.
    Returns previous/next note indices, travel blend, and normal lift in metres.
    """
    contacts = [math.floor(n["time"] * 24 + 1e-6) / 24 for n in notes]
    current = max((i for i, when in enumerate(contacts) if when <= t), default=0)
    when = contacts[current]
    end = when + notes[current]["dur"]
    if t < when:
        return current, current, 0, .028
    if current == len(notes)-1:
        return current, current, 0, 0 if t <= end else .028 * min(1, (t-end)/.06)
    following = contacts[current+1]
    release = min(end, max(when + 1/24, following - 2/24))
    if t <= release:
        return current, current, 0, 0
    start = max(release, following-.12)
    if t < start:
        return current, current, 0, .028 * min(1, (t-release)/.06)
    progress = min(1, max(0, (t-start)/(following-start)))
    blend = progress*progress*(3-2*progress)
    initial_lift = .028 * min(1, (start-release)/.06)
    lift = initial_lift*(1-blend) + (.028-initial_lift)*math.sin(math.pi*progress)
    return current, current+1, blend, lift


def correct_keys(events):
    import bpy
    from mathutils import Vector
    import performance_motion as motion
    from rigid_joint_fit import fit_tracks, key_rotations
    from bpy_extras.anim_utils import animdata_get_channelbag_for_assigned_slot

    scene = bpy.context.scene
    # Centre the playable range nearer the keys hand; its highest keys were out of reach.
    bpy.data.objects["PERFORMANCE | Keytar tilt"].location.x -= .12
    # The authored gesture returned at 16.3 s, after the bar-9 chord. Return by 16 s.
    old = motion.sample([(13.5,0),(14,1),(15.8,1),(16.3,0)])
    new = motion.sample([(13.5,0),(14,1),(15.5,1),(16,0)])
    for joint, values in motion.KEYBOARD_RAISE.items():
        obj = bpy.data.objects[f"PERFORMANCE | 02 Long vest.001 {joint}"]
        bag = animdata_get_channelbag_for_assigned_slot(obj.animation_data)
        for curve in bag.fcurves:
            axis = "xyz"[curve.array_index]
            gain = values.get(axis,0)
            for point in curve.keyframe_points:
                delta = gain * (new[round(point.co.x)-1] - old[round(point.co.x)-1])
                point.co.y += delta
                point.handle_left.y += delta
                point.handle_right.y += delta
    scene.frame_set(1)
    bpy.context.view_layer.update()
    notes_by_hand = playing_notes(events)
    hands = {}
    for name in (*notes_by_hand, "03 Oversized blazer hand.001"):
        hand = bpy.data.objects[name]
        reverse = name.startswith("03")
        fingers = add_fingers(hand, reverse)
        prefix = name.removesuffix(" hand.001").removesuffix(" hand")
        suffix = ".001" if name.endswith(".001") else ""
        chain = [bpy.data.objects[f"PERFORMANCE | {prefix}{suffix} {joint}"] for joint in ("shoulder", "elbow")]
        if not hand.parent.name.endswith("wrist"):
            wrist = bpy.data.objects.new(f"PERFORMANCE | {prefix}{suffix} contact wrist", None)
            scene.collection.objects.link(wrist)
            wrist.location = hand.matrix_world.translation + Vector((0,0,.035))
            bpy.context.view_layer.update()
            _parent_keep(wrist, hand.parent)
            _parent_keep(hand, wrist)
        chain.append(hand.parent)
        notes = notes_by_hand.get(name)
        if notes is None:
            flex = [.3] * 480
        else:
            flex = motion.presses([(n["time"], n["dur"], 1) for n in notes], depth=.22, lift=.03)
        for index, (joint, _, _) in enumerate(fingers):
            for frame, value in enumerate(flex, 1):
                joint.rotation_euler.x = .13 + value * (1 - index*.15)
                joint.keyframe_insert(data_path="rotation_euler", index=0, frame=frame)
        hands[name] = dict(hand=hand, chain=chain, fingers=fingers, notes=notes,
                           records=[], goals=[], weights=[])

    for frame in range(1, 481):
        scene.frame_set(frame)
        bpy.context.view_layer.update()
        t = (frame - 1) / 24
        for name, entry in hands.items():
            hand, chain = entry["hand"], entry["chain"]
            joint, tip, point = entry["fingers"][0]
            local_tip = chain[-1].matrix_world.inverted() @ (tip.matrix_world @ point)
            original = tip.matrix_world @ point
            notes = entry["notes"]
            if notes is None:
                key = bpy.data.objects["Keytar touch strip"]
                local = Vector((0, min(v.co.y for v in key.data.vertices), 0))
                weight, height = 1, 0
                target = key.matrix_world @ local
            else:
                previous, following, blend, height = note_pose(t, notes)
                points, normals = [], []
                for index in (previous, following):
                    key = bpy.data.objects[key_for_note(notes[index]["note"], name.startswith("03"))]
                    local = Vector((0, min(v.co.y for v in key.data.vertices) if name.startswith("03") else 0,
                                    0 if name.startswith("03") else max(v.co.z for v in key.data.vertices)))
                    points.append(key.matrix_world @ local)
                    normals.append(key.matrix_world.to_3x3() @ Vector((0,-1,0) if name.startswith("03") else (0,0,1)))
                target = points[0].lerp(points[1], blend) + normals[0].lerp(normals[1], blend).normalized() * height
                weight = _weight(t, notes)
            goal = original.lerp(target, weight)
            entry["records"].append((chain[0].parent.matrix_world.copy(), [list(o.rotation_euler) for o in chain], local_tip))
            entry["goals"].append(goal)
            entry["weights"].append(weight)

    axes = ((0,0),(0,1),(0,2),(1,0),(2,0))
    for name, entry in hands.items():
        fitted = fit_tracks(entry["chain"], entry["records"], entry["goals"], axes, name,
                            weights=entry["weights"], max_step=.7)
        key_rotations(entry["chain"], fitted, axes)
    scene.frame_set(1)
    bpy.context.view_layer.update()
