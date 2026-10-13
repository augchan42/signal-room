"""Geometric contact correction for the rigid performer joint controls."""

import math


def drum_contacts(events):
    """Assign the four electronic pads to each authored stroke.

    Left pad 0 is snare; right pad 1 is hi-hat. Rolls use each hand's home pad
    (electronic pads may share a snare patch). Toms move between the home and
    outer pad on each side; crashes use outer pads 3 and 2.
    Hand assignment matches performance_motion.drummer. Simultaneous sounds
    assigned to one hand share one physical strike, with the more prominent
    sound choosing the electronic pad; coincident kinds remain recorded.
    """
    contacts, rolls = [], 0
    toms = {"left": 0, "right": 0}
    for hit in events["drums"]:
        when, gain, kind = hit["time"], hit["gain"], hit["kind"]
        if kind == "kick" or any(abs(when - c) < .06 for c in events["crashes"]):
            continue
        if kind in ("hat", "open_hat"):
            side, lift = "right", .35 + .65 * gain
        elif kind == "snare":
            side, lift = "left", 1.0
        elif kind == "ghost":
            side, lift = "left", .12
        else:
            side = "right" if rolls % 2 else "left"
            rolls += 1
            lift = .45 + .5 * min(gain / .8, 1)
        pad = 0 if side == "left" else 1
        if kind == "tom":
            pad = {"left": (0, 3), "right": (1, 2)}[side][toms[side] % 2]
            toms[side] += 1
        contacts.append(dict(time=when, frame=math.floor(when * 24 + 1e-6) + 1,
                             side=side, pad=pad, kind=kind, lift=lift))
    for when in events["crashes"]:
        for side, pad in (("left", 3), ("right", 2)):
            contacts.append(dict(time=when, frame=math.floor(when * 24 + 1e-6) + 1,
                                 side=side, pad=pad, kind="crash", lift=1.4))
    priority = {"crash": 6, "tom": 5, "snare": 4, "roll": 3, "ghost": 2,
                "open_hat": 1, "hat": 0}
    grouped = {}
    for contact in contacts:
        grouped.setdefault((contact["frame"], contact["side"]), []).append(contact)
    merged = []
    for group in grouped.values():
        ordered = sorted(group, key=lambda c: priority[c["kind"]], reverse=True)
        winner = dict(ordered[0])
        winner["coincident"] = [c["kind"] for c in ordered[1:]]
        winner["lift"] = max(c["lift"] for c in group)
        merged.append(winner)
    return sorted(merged, key=lambda c: (c["frame"], c["side"]))


def correct_drums(events):
    """Fit five rigid-joint angles to each stick-tip trajectory, leaving grips intact.

    The existing torso/head motion stays in place. The target trajectory uses the
    existing stroke timing/easing and loudness, but lands on the pad surface.
    Forward kinematics is evaluated without repeatedly updating Blender's scene.
    """
    import bpy
    from mathutils import Matrix, Vector, Euler
    import performance_motion as motion

    scene = bpy.context.scene
    hits = drum_contacts(events)
    arms = {}
    axes = ((0, 0), (0, 1), (0, 2), (1, 0), (2, 0))
    scene.frame_set(1)
    bpy.context.view_layer.update()
    for side, suffix in (("left", ""), ("right", ".001")):
        chain = [bpy.data.objects[f"PERFORMANCE | 04 Sleeveless{suffix} {name}"]
                 for name in ("shoulder", "elbow", "wrist")]
        stick = bpy.data.objects["Drumstick " + side]
        hand = bpy.data.objects["04 Sleeveless hand" + suffix].matrix_world.translation
        zs = [v.co.z for v in stick.data.vertices]
        tip = max((Vector((0, 0, z)) for z in (min(zs), max(zs))),
                  key=lambda p: (stick.matrix_world @ p - hand).length)
        own_hits = [h for h in hits if h["side"] == side]
        targets = []
        for h in own_hits:
            pad = bpy.data.objects[f"Electronic octagonal pad {h['pad']} rubber"]
            top = max(v.co.z for v in pad.data.vertices)
            targets.append(((h["frame"] - 1) / 24,
                            pad.matrix_world @ Vector((0, 0, top + .001))))
        position = [motion.glide([(t, p[i]) for t, p in targets]) for i in range(3)]
        height = [-v for v in motion.strokes([(t, h["lift"]) for (t, _), h in zip(targets, own_hits)],
                                           down=0, lift=-.18)]
        normal = bpy.data.objects["Electronic octagonal pad 0 rubber"].matrix_world.to_3x3() @ Vector((0, 0, 1))
        goals = [Vector(tuple(position[i][f] for i in range(3))) + normal * height[f]
                 for f in range(480)]
        arms[side] = dict(chain=chain, tip=tip, tail=stick.matrix_parent_inverse @ stick.matrix_basis,
                          goals=goals, records=[], fitted=[])

    # Capture the authored transforms before changing any animation curves.
    for frame in range(1, 481):
        scene.frame_set(frame)
        bpy.context.view_layer.update()
        for arm in arms.values():
            arm["records"].append((arm["chain"][0].parent.matrix_world.copy(),
                                   [list(o.rotation_euler) for o in arm["chain"]]))

    for side, arm in arms.items():
        theta = None
        worst = 0.0
        for (root, base), goal in zip(arm["records"], arm["goals"]):
            if theta is None:
                theta = [base[o][a] for o, a in axes]

            def forward(values):
                angles = [x[:] for x in base]
                for (o, a), value in zip(axes, values):
                    angles[o][a] = value
                world = root.copy()
                for obj, angle in zip(arm["chain"], angles):
                    world = world @ obj.matrix_parent_inverse @ Matrix.LocRotScale(
                        obj.location, Euler(angle, "XYZ").to_quaternion(), obj.scale)
                return world @ arm["tail"] @ arm["tip"]

            for _ in range(50):
                point = forward(theta)
                error = goal - point
                if error.length < .001:
                    break
                jacobian = []
                for k in range(len(theta)):
                    shifted = theta[:]
                    shifted[k] += .001
                    jacobian.append((forward(shifted) - point) / .001)
                normal = Matrix([[sum(c[i] * c[j] for c in jacobian) + (.00015 if i == j else 0)
                                  for j in range(3)] for i in range(3)])
                solved = normal.inverted() @ error
                theta = [v + max(-.12, min(.12, col.dot(solved))) for v, col in zip(theta, jacobian)]
            gap = (goal - forward(theta)).length
            worst = max(worst, gap)
            if gap > .008:
                raise ValueError(f"Unreachable {side} drum tip at frame {len(arm['fitted']) + 1}: {gap:.3f}m")
            arm["fitted"].append(theta[:])
        speed = max(abs(b[k] - a[k]) for a, b in zip(arm["fitted"], arm["fitted"][1:]) for k in range(5))
        if speed > .7:
            raise ValueError(f"Drum contact correction jumps: {side}: {speed:.3f} rad/frame")
        for frame, values in enumerate(arm["fitted"], 1):
            for (obj_index, axis), value in zip(axes, values):
                obj = arm["chain"][obj_index]
                obj.rotation_euler[axis] = value
                obj.keyframe_insert(data_path="rotation_euler", index=axis, frame=frame)
        print("DRUM_CONTACT_FIT", side, "max gap", round(worst, 6), "max angular step", round(speed, 4), flush=True)
    scene.frame_set(1)
    bpy.context.view_layer.update()
