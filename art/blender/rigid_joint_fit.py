"""Damped joint fitting for the existing rigid-mesh performer rigs."""


def fit_tracks(chain, records, goals, axes, label, weights=None, max_step=.7):
    from mathutils import Matrix, Euler

    fitted, theta, difference, previous_weight = [], None, None, 0.0
    worst = 0.0
    for f, ((root, base, tip), goal) in enumerate(zip(records, goals)):
        authored = [base[o][a] for o, a in axes]
        weight = weights[f] if weights is not None else 1.0
        if weights is not None:
            ratio = min(1, weight / previous_weight) if previous_weight else 0
            theta = [v + (d * ratio if difference else 0) for v, d in zip(authored, difference or [0] * len(axes))]
        elif theta is None:
            theta = authored[:]

        def forward(values):
            angles = [x[:] for x in base]
            for (o, a), value in zip(axes, values):
                angles[o][a] = value
            world = root.copy()
            for obj, angle in zip(chain, angles):
                world = world @ obj.matrix_parent_inverse @ Matrix.LocRotScale(
                    obj.location, Euler(angle, "XYZ").to_quaternion(), obj.scale)
            return world @ tip

        if weight > 0:
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
            raise ValueError(f"Unreachable {label} at frame {f + 1}: {gap:.3f}m")
        fitted.append(theta[:])
        difference = [v - a for v, a in zip(theta, authored)]
        previous_weight = weight
    speed = max(abs(b[k] - a[k]) for a, b in zip(fitted, fitted[1:]) for k in range(len(axes)))
    if speed > max_step:
        frame, axis = max(((f, k) for f in range(1, len(fitted)) for k in range(len(axes))),
                          key=lambda item: abs(fitted[item[0]][item[1]] - fitted[item[0]-1][item[1]]))
        raise ValueError(f"Contact correction jumps: {label}: {speed:.3f} rad/frame at {frame+1}, axis {axes[axis]}, "
                         f"{fitted[frame-1][axis]:.3f} -> {fitted[frame][axis]:.3f}")
    print("CONTACT_FIT", label, "max gap", round(worst, 6), "max angular step", round(speed, 4), flush=True)
    return fitted


def key_rotations(chain, fitted, axes):
    from bpy_extras.anim_utils import animdata_get_channelbag_for_assigned_slot
    for frame, values in enumerate(fitted, 1):
        for (obj_index, axis), value in zip(axes, values):
            obj = chain[obj_index]
            obj.rotation_euler[axis] = value
            obj.keyframe_insert(data_path="rotation_euler", index=axis, frame=frame)
    for obj in chain:
        bag = animdata_get_channelbag_for_assigned_slot(obj.animation_data)
        if bag:
            for curve in bag.fcurves:
                if curve.data_path == "rotation_euler":
                    for key in curve.keyframe_points:
                        key.interpolation = "LINEAR"
