"""Signal Room controls. Run this text, then call set_state(...).

Example: set_state(tier=1,camera_name='frontman',tone='warm',pattern='signal-bars')
"""
import bpy, json
CAMERAS={'group':'CAM | Broadcast group','frontman':'CAM | Frontman fancam','producer':'CAM | Producer fancam','keytar':'CAM | Keytar fancam','drummer':'CAM | Drummer fancam','control':'CAM | Control room'}
TONES={'cool':[(.08,.10,.16),(.018,.025,.04),(.46,.51,.53),(.015,.025,.04)],'warm':[(.39,.065,.055),(.19,.07,.06),(.62,.46,.30),(.09,.035,.04)],'neutral':[(.12,.12,.14),(.025,.025,.03),(.57,.54,.46),(.025,.025,.03)]}

def set_audience_mix(mix):
    if len(mix)!=5 or any(v<0 for v in mix) or sum(mix)<=0: raise ValueError('Five non-negative segment weights with positive total required')
    weights=[v/sum(mix) for v in mix]; cumul=[]; acc=0
    for v in weights: acc+=v; cumul.append(acc)
    sticks=sorted((o for o in bpy.data.objects if o.name.startswith('FAN STICK')),key=lambda o:o['audience_index'])
    names=['Performance','Vocal','Fashion / concept','Story / personality','Retro / alternative']
    # Object-linked materials retain shared geometry while allowing colour assignment.
    for k,ob in enumerate(sticks):
        q=((k*17)%len(sticks)+.5)/len(sticks)
        segment=next((j for j,c in enumerate(cumul) if q<c),4)
        ob.material_slots[0].link='OBJECT'; ob.material_slots[0].material=bpy.data.materials['FAN '+names[segment]]
        ob['fan_segment']=segment
    bpy.context.scene['audience_mix']=json.dumps(weights)

def set_state(tier=3,camera_name='group',encore=False,tone='cool',pattern='waveform',mix=None,hook='AFTERIMAGE',special=False):
    if tier not in [1,2,3] or camera_name not in CAMERAS or tone not in TONES: raise ValueError('Invalid tier, camera, or tone')
    scene=bpy.context.scene
    kit=bpy.data.collections['SIGNAL ROOM - concept 01']
    for ob in kit.objects:
        hidden=ob.get('tier_min',1)>tier
        if ob.get('role')=='control-room': hidden=camera_name!='control'
        if ob.get('role')=='encore': hidden=not encore
        if ob.get('role')=='screen-echo': hidden=not special
        if ob.name=='Rear screen glass': hidden=True
        ob.hide_render=hidden; ob.hide_viewport=hidden or ob.name=='T3 bounded haze'
    scene.camera=bpy.data.objects[CAMERAS[camera_name]]
    for i,rgb in enumerate(TONES[tone]):
        m=bpy.data.materials['SUIT %d'%i]; m.diffuse_color=(*rgb,1)
        n=next(n for n in m.node_tree.nodes if n.type=='BSDF_PRINCIPLED'); n.inputs['Base Color'].default_value=(*rgb,1)
    m=bpy.data.materials['REAR SCREEN | replaceable feed']
    next(n for n in m.node_tree.nodes if n.type=='TEX_IMAGE').image=bpy.data.images['SCREEN '+('blank' if special else pattern)]
    key=bpy.data.objects['Warm portrait key'].data
    key.energy={1:260,2:400,3:520}[tier]; key.color=(1,.63,.39) if tier==1 else (1,.80,.64)
    bpy.data.objects['Cool soft fill'].data.energy={1:70,2:150,3:220}[tier]
    for name,color in [('Cyan rear rim',(.04,.7,1)),('Coral rear rim',(1,.12,.07))]:
        bpy.data.objects[name].data.color=(.14,.16,.20) if special else color
    if special:
        key.color=(1,.72,.52); key.energy=240
        bpy.data.objects['Cool soft fill'].data.energy=30
        for i in range(4):
            m=bpy.data.materials['SUIT %d'%i]; rgb=(.02+i*.008,.025+i*.008,.032+i*.008)
            m.diffuse_color=(*rgb,1); next(n for n in m.node_tree.nodes if n.type=='BSDF_PRINCIPLED').inputs['Base Color'].default_value=(*rgb,1)
    scene['broadcast_tier']=tier; scene['stage_emphasis']=camera_name; scene['tone']=tone; scene['screen_pattern']=pattern; scene['hook_title']=hook; scene['encore']=encore; scene['special_stage']=special
    set_audience_mix(mix if mix is not None else ([0,0,.1,.15,.75] if tier<3 else [.20,.15,.20,.15,.30]))
    return {'tier':tier,'camera':scene.camera.name,'visible':sum(not o.hide_render for o in kit.objects)}
