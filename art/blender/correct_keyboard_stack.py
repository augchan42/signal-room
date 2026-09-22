"""Apply once to the instrument kit, including existing saved MVP scenes."""
import bpy
import math
from mathutils import Matrix, Vector

for prefix, tilt, offset in [
    ('BORG main synthesizer', 8, (0, 0, 0)),
    ('BORG upper synthesizer', 20, (0, -.60, 0)),
]:
    chassis = bpy.data.objects[prefix+' chassis']
    if chassis.get('player_facing_stack_v2'):
        continue
    pivot = chassis.location.copy()
    rotation = Matrix.Rotation(math.radians(-tilt), 3, 'X')
    for ob in [o for o in bpy.data.objects if o.name.startswith(prefix)]:
        ob.location = pivot + rotation @ (ob.location-pivot) + Vector(offset)
        ob.rotation_euler = (rotation @ ob.rotation_euler.to_matrix()).to_euler()
    chassis['player_facing_stack_v2'] = True

for ob in bpy.data.objects:
    if ob.name.startswith('Synth upper tier support') and not ob.get('player_facing_stack_v2'):
        ob.location.y -= .60
        ob['player_facing_stack_v2'] = True
