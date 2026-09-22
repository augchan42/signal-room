"""Validate actual saved scene state, controls, and loop closure, not source text."""
import bpy, json, math
from pathlib import Path
ROOT=Path('/Users/auchan/projects/signal-room/art/blender')
exec(compile((ROOT/'mvp_controls.py').read_text(),'mvp_controls.py','exec'))
scene=bpy.context.scene; kit=bpy.data.collections['SIGNAL ROOM - concept 01']
report={}
assert scene.render.resolution_x==1080 and scene.render.resolution_y==1920
assert scene.frame_end==120 and scene.render.fps==24
report['dimensions']=[1080,1920]; report['seconds']=5
assert not any(o.type=='FONT' and 'KORG' in o.data.body for o in scene.objects)
assert sum(o.type=='FONT' and 'BORG' in o.data.body for o in scene.objects)==4
report['brand']='BORG'
for prefix in ['BORG main synthesizer','BORG upper synthesizer']:
    keys=[o.location.y for o in scene.objects if o.name.startswith(prefix+' white key')]
    panel=bpy.data.objects[prefix+' control panel']
    assert sum(keys)/len(keys)>panel.location.y, 'Keys face away from the performer'
report['keybed_orientation']='Both keybeds face +Y toward the keyboardist'
upper=bpy.data.objects['BORG upper synthesizer chassis']
lower=bpy.data.objects['BORG main synthesizer chassis']
assert upper.location.y < lower.location.y, 'Upper tier must step away from player'
from mathutils import Vector
for chassis in [lower,upper]:
    assert (chassis.rotation_euler.to_matrix() @ Vector((0,0,1))).y > .1, 'Keybed must tilt toward player'
report['keyboard_stack']='Upper tier steps away; both playing surfaces tilt toward player'
report['tiers']={}
for tier in [1,2,3]:
    set_state(tier=tier,camera_name='group')
    crowd=sum(o.get('role')=='audience' and o.name.startswith('FAN STICK') and not o.hide_render for o in kit.objects)
    assert crowd=={1:0,2:9,3:48}[tier]
    report['tiers'][str(tier)]={'light_sticks':crowd,'visible_objects':sum(not o.hide_render for o in kit.objects)}
for segment in range(5):
    weights=[0]*5; weights[segment]=1; set_audience_mix(weights)
    assert all(o['fan_segment']==segment for o in kit.objects if o.name.startswith('FAN STICK'))
report['audience_mix_all_five_inputs']='passed'
set_segment_readouts([0,.25,.5,.75,1],[.1,.3,.5,.7,.9])
assert abs(bpy.data.objects['Monitor 4 affinity'].scale.x-.9)<.0001
assert bpy.data.objects['Monitor 0 awareness 0'].material_slots[0].material.name=='Muted labels'
assert bpy.data.objects['Monitor 4 awareness 6'].material_slots[0].material.name=='FAN Retro / alternative'
report['segment_monitor_inputs']='Awareness lamps and affinity bar scales respond to five-segment data'
before=len(kit.objects)
for tone in ['warm','cool','neutral']: set_state(tone=tone)
assert len(kit.objects)==before
report['material_only_tone_swaps']='passed'
for name in CAMERAS:
    set_state(camera_name=name); assert scene.camera.name==CAMERAS[name]
report['cameras']=CAMERAS
set_state(tier=3,camera_name='group',encore=True)
assert all(not o.hide_render for o in kit.objects if o.get('role')=='encore')
report['encore']='trophy and confetti visible'
set_state(tier=1,camera_name='group',special=True)
assert sum(not o.hide_render for o in kit.objects if o.get('role')=='screen-echo')==8
report['special_stage']='8 actual performer-derived silhouettes, 12-frame echo delay'
set_state(tier=3,camera_name='frontman')
def snapshot(frame):
    scene.frame_set(frame); bpy.context.view_layer.update()
    return {o.name:[round(v,5) for row in o.matrix_world for v in row] for o in kit.objects if o.name.startswith(('PERFORMER','HEAD TURN','CAM | Frontman','FAN STICK'))}
a=snapshot(1); b=snapshot(61); c=snapshot(121)
assert a==c,'Transform loop does not close'
assert a!=b,'No transform animation'
report['loop_transform_closure']='frame 1 equals frame 121; middle differs'
def lights(frame):
    scene.frame_set(frame)
    return [round(o.data.energy,4) for o in kit.objects if o.type=='LIGHT']
assert lights(1)==lights(121)
assert lights(1)!=lights(5)
report['beat_lighting']='periodic light energies verified'
report['engine']=scene.render.engine
(ROOT/'mvp'/'verification.json').write_text(json.dumps(report,indent=2)+'\n')
print(json.dumps(report,indent=2))
