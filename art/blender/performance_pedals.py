"""Event-driven heel-down foot pedals for the rigid performer meshes."""


def pedal_tracks(events):
    import performance_motion as motion
    kicks = [(d["time"], .09, 1) for d in events["drums"] if d["kind"] == "kick"]
    sustain = [(c["time"], 1.8, 1) for c in events["chords"]]
    hat_states = sorted((motion.contact(d["time"]), -.22 if d["kind"] == "open_hat" else -.10)
                        for d in events["drums"] if d["kind"] in ("hat", "open_hat"))
    hat_keys = [(-1, -.10)]
    for when, angle in hat_states:
        if when-.08 > hat_keys[-1][0]:
            hat_keys.append((when-.08, hat_keys[-1][1]))
        if when > hat_keys[-1][0]:
            hat_keys.append((when, angle))
    return {
        "kick": [-.22 + .12*p for p in motion.presses(kicks, 1)],
        "hi-hat": motion.sample(hat_keys),
        "sustain": [-.22 + .12*p for p in motion.presses(sustain, 1)],
    }


def add_pedals(events):
    import bpy
    from mathutils import Vector
    from bpy_extras.anim_utils import animdata_get_channelbag_for_assigned_slot

    scene = bpy.context.scene
    scene.frame_set(1)
    bpy.context.view_layer.update()
    old = bpy.data.objects["Synth sustain pedal"]
    old.hide_render = old.hide_viewport = True
    material = bpy.data.materials["Brushed silver"]
    specs = (("kick", "04 Sleeveless", ""),
             ("hi-hat", "04 Sleeveless", ".001"),
             ("sustain", "02 Long vest", ""))
    tracks = pedal_tracks(events)
    for kind, prefix, suffix in specs:
        boot = bpy.data.objects[prefix + " boot" + suffix]
        lo = Vector([min(v.co[i] for v in boot.data.vertices) for i in range(3)])
        hi = Vector([max(v.co[i] for v in boot.data.vertices) for i in range(3)])
        world = boot.matrix_world.copy()
        heel = world @ Vector((0, hi.y, lo.z))
        pivot = bpy.data.objects.new("PERFORMANCE | " + kind + " heel", None)
        scene.collection.objects.link(pivot)
        pivot.location = heel
        bpy.context.view_layer.update()
        for obj in (boot, bpy.data.objects[prefix + " boot trim" + suffix]):
            matrix = obj.matrix_world.copy()
            obj.parent = pivot
            obj.matrix_world = matrix

        # A small hinged plate beneath the toe, with its top on the boot's sole plane.
        mesh = bpy.data.meshes.new(kind + " pedal plate")
        verts = [(x,y,z) for z in (-.0045,.0045) for y in (-.06,.06) for x in (-.08,.08)]
        mesh.from_pydata(verts, [], [(0,2,3,1),(4,5,7,6),(0,1,5,4),(2,6,7,3),(0,4,6,2),(1,3,7,5)])
        mesh.materials.append(material)
        pedal = bpy.data.objects.new("PERFORMANCE | " + kind + " pedal", mesh)
        scene.collection.objects.link(pedal)
        pedal.location = world @ Vector((0, lo.y+.06, lo.z-.0045))
        pedal.rotation_euler = world.to_euler()
        bpy.context.view_layer.update()
        matrix = pedal.matrix_world.copy()
        pedal.parent = pivot
        pedal.matrix_world = matrix
        bevel = pedal.modifiers.new("Pedal edges", "BEVEL")
        bevel.width, bevel.segments = .002, 2

        for frame, angle in enumerate(tracks[kind], 1):
            pivot.rotation_euler.x = angle
            pivot.keyframe_insert(data_path="rotation_euler", index=0, frame=frame)
        bag = animdata_get_channelbag_for_assigned_slot(pivot.animation_data)
        for curve in bag.fcurves:
            for key in curve.keyframe_points:
                key.interpolation = "LINEAR"
    scene.frame_set(1)
    bpy.context.view_layer.update()
