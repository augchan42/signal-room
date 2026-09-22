"""Build Signal Room's offline MVP asset set. Safe to rebuild its named collection.

Run inside Blender using exec(compile(...)) or blender --python this_file.
All geometry is original procedural work. No downloaded assets or services.
"""
import bpy, math, random, json
from pathlib import Path
from mathutils import Vector

ROOT=Path('/Users/auchan/projects/signal-room/art/blender')
OUT=ROOT/'mvp'
OUT.mkdir(exist_ok=True)
for filename in ['build_concept.py','add_instruments.py']:
    exec(compile((ROOT/filename).read_text(),str(ROOT/filename),'exec'))
scene=bpy.context.scene
scene.name='SIGNAL ROOM | broadcast MVP'
scene.render.engine='BLENDER_EEVEE'
scene.render.resolution_percentage=100
scene.render.fps=24
scene.frame_start=1; scene.frame_end=120
scene.render.image_settings.file_format='PNG'
scene.render.film_transparent=False
scene['bpm']=120
scene['broadcast_tier']=3
scene['stage_emphasis']='frontman'
scene['hook_title']='AFTERIMAGE'
scene['hook_options']='AFTERIMAGE | NIGHT FREQUENCY | LAST SIGNAL'
scene['band_name']='SYNTHWAVE'
scene['screen_pattern']='waveform'
scene['tone']='cool'
scene['audience_mix']='[0.20, 0.15, 0.20, 0.15, 0.30]'
scene['loop_seconds']=5

SEGMENTS=[('Performance',(0.05,.68,1.0),'#36CFFF'),('Vocal',(.93,.60,.12),'#FFD166'),('Fashion / concept',(.9,.12,.43),'#F75B9A'),('Story / personality',(.32,.88,.49),'#83E6A7'),('Retro / alternative',(.46,.21,1.0),'#AE86FF')]
segment_mats=[material('FAN '+name,rgb,emission=4) for name,rgb,_ in SEGMENTS]
warm=material('Amber panel labels',(.75,.43,.13),emission=.4)
panel=material('Console enamel',(.032,.051,.065),metal=.25)
glass=material('CRT phosphor glass',(.005,.025,.023),emission=.15)
muted=material('Muted labels',(.31,.36,.39))

# A minimal circular prism, also used for knobs, audience light sticks and fixtures.
def cylinder(name,loc,radius,depth,mat,segments=16):
    verts=[(radius*math.cos(k*math.tau/segments),radius*math.sin(k*math.tau/segments),z) for z in [-depth/2,depth/2] for k in range(segments)]
    faces=[tuple(reversed(range(segments))),tuple(range(segments,2*segments))]+[(k,(k+1)%segments,(k+1)%segments+segments,k+segments) for k in range(segments)]
    ob=mesh(name,verts,faces,mat,.008); ob.location=loc
    return ob

def empty(name,loc=(0,0,0)):
    ob=bpy.data.objects.new(name,None); kit.objects.link(ob); ob.location=loc
    return ob

def parent_keep(ob,parent):
    world=ob.matrix_world.copy(); ob.parent=parent; ob.matrix_world=world

def key(ob,path,frame,value):
    setattr(ob,path,value); ob.keyframe_insert(data_path=path,frame=frame)

def tag_new(before,**props):
    for ob in set(kit.objects)-before:
        for k,v in props.items(): ob[k]=v

# Adult silhouettes: seven-ish heads tall, narrow waists, wider shoulders.
# Keep all facial pieces together around their head pivot.
roots=[]
for i,(x,y) in enumerate([(0,-1.08),(-1.55,.28),(1.45,-.42),(0,.73)]):
    prefix=['01 Cropped jacket','02 Long vest','03 Oversized blazer','04 Sleeveless'][i]
    lift=.57 if i==3 else 0
    head_parts=['head','nose','brow','mouth','hair','fringe']
    for ob in list(kit.objects):
        if not ob.name.startswith(prefix): continue
        localz=ob.location.z-lift
        if any(p in ob.name for p in head_parts):
            ob.location=Vector((x+(ob.location.x-x)*.82,y+(ob.location.y-y)*.82,lift+2.08+(localz-1.88)*.82))
            ob.scale*=.82
        elif 'neck' in ob.name:
            ob.location.z+=.17
        else:
            ob.location.z=lift+.15+(localz-.15)*1.115
            ob.scale.z*=1.115
            if any(p in ob.name for p in ['garment','shoulder','lapel']): ob.scale.x*=1.1
        if any(p in ob.name for p in ['garment','upper arm','forearm','lapel']):
            if len(ob.data.materials) and ob.data.materials[0]!=skin:
                base=ob.data.materials[0]
                m=bpy.data.materials.get('SUIT %d'%i)
                if not m:
                    m=base.copy(); m.name='SUIT %d'%i
                ob.data.materials[0]=m
    root=empty('PERFORMER %d | %s'%(i+1,prefix),(x,y,.15+lift))
    bpy.context.view_layer.update()
    for ob in list(kit.objects):
        if ob.name.startswith(prefix): parent_keep(ob,root)
    roots.append(root)
    origin=root.location.copy()
    for f in range(1,122,12):
        t=(f-1)/120*math.tau
        root.location=origin+Vector((.018*math.sin(t),0,.010*math.sin(t*2)))
        root.rotation_euler=(0,.013*math.sin(t),.018*math.sin(t))
        root.keyframe_insert(data_path='location',frame=f)
        root.keyframe_insert(data_path='rotation_euler',frame=f)

# Recessed rig, an arched broadcast identifier, and modular tier additions.
for ob in list(kit.objects):
    if ob.name.startswith('Screen diagonal'): bpy.data.objects.remove(ob,do_unlink=True)
    elif ob.name=='Studio floor': ob.scale.y=1.7
    elif ob.name.startswith('Warm portrait key'): ob.data.energy=520
    elif ob.name.startswith('Cool soft fill'): ob.data.energy=220
before=set(kit.objects)
for x in [-3.1,3.1]:
    box('T2 vertical tower',(x,.4,2.10),(.16,.25,4.2),black)
    for z in [.3,.9,1.5,2.1,2.7,3.3,3.9]:
        ob=box('T2 tower light',(x,.25,z),(.055,.055,.29),cyan,.01)
    for dx in [-.12,.12]: limb('T2 truss rail',(x+dx,.5,.2),(x+dx,.5,4.2),.025,.025,silver)
tag_new(before,tier_min=2)
before=set(kit.objects)
for x in [-3.5,3.5]:
    box('T3 side screen housing',(x,1.1,2.0),(.55,.17,3.6),black)
    for n in range(12): box('T3 side signal bar',(x,.99,.5+n*.27),(.40,.03,.07),cyan if n%3 else coral,.008)
for y in [.6,1.05]: limb('T3 overhead truss',(-3.7,y,4.3),(3.7,y,4.3),.07,.07,silver)
for x in [-3.6+i*.45 for i in range(17)]:
    limb('T3 truss diagonal',(x,.6,4.3),(x+.35,1.05,4.3),.026,.026,silver)
for x in [-2.6,-1.3,0,1.3,2.6]:
    cylinder('T3 downlight casing',(x,.75,4.18),.14,.23,black)
    cylinder('T3 downlight lens',(x,.75,4.05),.11,.018,cyan)
    data=bpy.data.lights.new('T3 beam %.1f'%x,'SPOT'); data.energy=330; data.color=(.12,.55,1); data.spot_size=.6; data.spot_blend=.5
    ob=bpy.data.objects.new(data.name,data); kit.objects.link(ob); ob.location=(x,.75,4.00)
    ob.rotation_euler=(Vector((x*.65,-1.7,.05))-ob.location).to_track_quat('-Z','Y').to_euler()
tag_new(before,tier_min=3)
label('Show subtitle','THE WEEKLY SYNTH BROADCAST',(-1.15,1.09,3.07),.071,ivory)
for n in range(4): box('Show signal logo',(-1.11+n*.075,1.08,3.42+n*.018),(.042,.022,.06+n*.035),cyan,.005)

# True flat rear-screen surface with UVs and swappable image patterns.
old=bpy.data.objects.get('Rear screen glass'); old.hide_render=True; old.hide_viewport=True
screen_material=material('REAR SCREEN | replaceable feed',(.005,.015,.025))
screen=mesh('REAR SCREEN | mask target',[(-2.60,1.345,.30),(2.60,1.345,.30),(2.60,1.345,3.40),(-2.60,1.345,3.40)],[(0,1,2,3)],screen_material)
uv=screen.data.uv_layers.new()
for loop,xy in zip(uv.data,[(0,0),(1,0),(1,1),(0,1)]): loop.uv=xy
import numpy as np
w,h=1024,512
yy,xx=np.mgrid[0:h,0:w]; u=xx/w; v=yy/h
patterns={}
for kind in ['signal-bars','test-pattern','waveform']:
    pixels=np.zeros((h,w,4),dtype=np.float32); pixels[:,:,:3]=(.005,.012,.024); pixels[:,:,3]=1
    if kind=='signal-bars':
        for j in range(19):
            height=.18+.48*(.5+.5*math.sin(j*.71))
            selection=(abs(u-(j+.5)/19)<.014)&(abs(v-.5)<height/2)
            pixels[selection,:3]=(.015,.38,.56) if j%3 else (.50,.065,.08)
    elif kind=='test-pattern':
        colors=[(.55,.52,.39),(.03,.38,.53),(.37,.08,.28),(.18,.40,.23),(.44,.09,.07)]
        for j,col in enumerate(colors): pixels[(u>=j/5)&(u<(j+1)/5)&(v>.25)&(v<.70),:3]=col
        pixels[(abs(v-.19)<.01)|(abs(v-.76)<.008),:3]=(.4,.45,.45)
    else:
        for j in range(3):
            wave=.36+j*.15+.10*np.sin(u*math.tau*(2+j)+j)*np.sin(u*math.pi)
            pixels[abs(v-wave)<.0035,:3]=[(.02,.55,.7),(.4,.14,.24),(.36,.37,.26)][j]
        pixels[(xx%64<1)|(yy%64<1),:3]+=.008
    img=bpy.data.images.new('SCREEN '+kind,width=w,height=h,alpha=True)
    img.pixels.foreach_set(pixels.reshape(-1)); img.filepath_raw=str(OUT/(kind+'.png')); img.file_format='PNG'; img.save(); img.pack()
    patterns[kind]=img
nodes=screen_material.node_tree.nodes; nodes.clear()
output=nodes.new('ShaderNodeOutputMaterial'); emission=nodes.new('ShaderNodeEmission'); tex=nodes.new('ShaderNodeTexImage'); tex.image=patterns['waveform']
screen_material.node_tree.links.new(tex.outputs['Color'],emission.inputs['Color']); screen_material.node_tree.links.new(emission.outputs[0],output.inputs['Surface'])
for f in range(1,122,12):
    emission.inputs['Strength'].default_value=.65; emission.inputs['Strength'].keyframe_insert('default_value',frame=f)
    emission.inputs['Strength'].default_value=.26; emission.inputs['Strength'].keyframe_insert('default_value',frame=f+3)

# Linked crowd silhouettes and one shared light-stick mesh per colour.
rng=random.Random(23)
audience=empty('AUDIENCE | five fan segments')
crowd_heads={}; crowd_bodies={}; stick_meshes={}
for row in range(4):
    count=9+row*2
    for j in range(count):
        x=(j-(count-1)/2)*.42 + rng.uniform(-.05,.05)
        y=-2.45-row*.56+rng.uniform(-.07,.07)
        z=.76+rng.uniform(-.12,.12)
        before=set(kit.objects)
        body=ellipsoid('Audience silhouette torso',(x,y,z*.60),(.17,.095,z*.42),black,8,6)
        head=ellipsoid('Audience silhouette head',(x,y,z),(.10,.095,.13),black,8,6)
        # These remain silhouettes, never individual detailed characters.
        if row in crowd_heads: head.data=crowd_heads[row]
        else: crowd_heads[row]=head.data
        if row in crowd_bodies: body.data=crowd_bodies[row]
        else: crowd_bodies[row]=body.data
        segment=(j+row*3)%5
        hand=(x+.18,y,z+.15)
        limb('Audience raised arm',(x+.11,y,z*.75),hand,.055,.065,black)
        stick=cylinder('FAN STICK %02d %02d'%(row,j),(hand[0],y,z+.31),.028,.24,segment_mats[segment],8)
        if segment in stick_meshes: stick.data=stick_meshes[segment]
        else: stick_meshes[segment]=stick.data
        stick['fan_segment']=segment; stick['audience_index']=sum(9+r*2 for r in range(row))+j
        stick['base_z']=stick.location.z
        for f in [1,31,61,91,121]:
            stick.rotation_euler.y=.24*math.sin((f-1)/120*math.tau+j)
            stick.keyframe_insert(data_path='rotation_euler',frame=f)
        tag_new(before,tier_min=2 if row==0 else 3,role='audience')

# Thin volumetric shafts, local to stage: low density keeps faces legible.
before=set(kit.objects)
haze=bpy.data.materials.new('Stage haze'); haze.use_nodes=True
hn=haze.node_tree.nodes; hn.clear(); ho=hn.new('ShaderNodeOutputMaterial'); hv=hn.new('ShaderNodeVolumePrincipled'); hv.inputs['Density'].default_value=.018
haze.node_tree.links.new(hv.outputs['Volume'],ho.inputs['Volume'])
box('T3 bounded haze',(0,-.3,2.1),(6.5,4.3,4.2),haze,0)
tag_new(before,tier_min=3)

# First win: the same stage supports an encore, trophy and confetti.
before=set(kit.objects)
box('First win plinth',(.77,-1.03,.44),(.34,.34,.57),black)
cylinder('First win trophy foot',(.77,-1.03,.76),.12,.06,silver)
limb('First win trophy stem',(.77,-1.03,.79),(.77,-1.03,1.04),.045,.045,silver)
for n in range(4): box('First win trophy signal',(.65+n*.075,-1.03,1.09+n*.04),(.04,.09,.18+n*.07),warm,.008)
label('First win plaque','NO. 1',(.66,-1.21,.48),.06,ivory)
for j in range(65):
    ob=box('Encore confetti %02d'%j,(rng.uniform(-2.65,2.65),rng.uniform(-1.8,.9),rng.uniform(1,3.6)),(.035,.013,.08),segment_mats[j%5],.001)
    ob.rotation_euler=(rng.random()*3,rng.random()*3,rng.random()*3)
tag_new(before,role='encore')

# Control-room foreground. Its window is an actual opening onto the live set.
before=set(kit.objects)
box('Booth floor',(0,-7,-.13),(7,5,.15),black)
box('Booth lower wall',(0,-4.75,.68),(7,.20,1.35),panel)
for x in [-3.15,3.15]: box('Booth window jamb',(x,-4.75,2.65),(.3,.22,2.65),black)
for z in [1.38,3.97]: box('Booth window sill',(0,-4.75,z),(6.5,.25,.14),silver)
box('Booth header',(0,-4.75,4.35),(7,.23,.66),panel)
label('Booth broadcast mark','S I G N A L   R O O M',(-2.50,-4.90,4.32),.24,ivory)
label('Booth subtitle','CONTROL 01   /   WEEKLY MUSIC TRANSMISSION',(-2.50,-4.90,4.08),.095,muted)
box('Booth ON AIR enclosure',(2.05,-4.94,4.29),(1.35,.14,.37),black)
label('Booth ON AIR text','ON AIR',(1.49,-5.02,4.21),.23,coral)
box('Mix desk',(0,-6.05,.89),(5.9,1.63,.20),panel,.075)
for x in [-2.94,2.94]: box('Desk walnut end',(x,-6.05,.9),(.12,1.7,.24),wood,.035)
for k,(name,rgb,hexcolor) in enumerate(SEGMENTS):
    x=-2.25+k*1.12
    box('Segment monitor %d housing'%k,(x,-5.43,1.42),(1.02,.43,.75),black,.07)
    box('Segment monitor %d screen'%k,(x,-5.661,1.48),(.88,.032,.49),glass,.045)
    label('Segment monitor %d label'%k,['PERFORMANCE','VOCAL','CONCEPT','STORY','RETRO'][k],(x-.37,-5.686,1.58),.085,segment_mats[k])
    for j in range(7):
        height=.035+(j+1)*.018
        box('Monitor %d awareness %d'%(k,j),(x-.32+j*.092,-5.69,1.39+height/2),(.055,.008,height),segment_mats[k] if j<3+k%4 else muted,.002)
    label('Segment monitor %d readout'%k,'AWARENESS  /  AFFINITY',(x-.37,-5.69,1.30),.041,ivory)
    box('Monitor %d affinity'%k,(x-.18,-5.69,1.24),(.40,.01,.018),segment_mats[k],.003)
    for n in range(3):
        xx=x-.32+n*.29
        box('Fader channel',(xx,-6.15,1.001),(.021,.55,.012),black,.003)
        box('Fader cap',(xx,-6.30+.09*((k+n)%4),1.035),(.13,.075,.045),ivory,.008)
        for yy in [-5.87,-5.99]: cylinder('Console knob',(xx,yy,1.04),.045,.06,silver,12)
        box('Channel colour key',(xx,-6.50,1.014),(.12,.06,.025),segment_mats[k],.006)
    label('Desk channel label', ['VOCAL','ARRANGE','STYLE','FOLLOW UP','RETRO'][k],(x-.38,-6.71,1.007),.071,ivory,(0,0,0))
box('Three slot tape unit',(-1.92,-6.95,.83),(1.8,.54,.15),black,.025)
for j in range(3):
    xx=-2.51+j*.56
    box('Schedule cassette %d'%j,(xx,-6.95,.928),(.49,.37,.04),ivory,.013)
    label('Schedule slot label','0%d  %s'%(j+1,['TRAIN','PROMO','REST'][j]),(xx-.20,-7.02,.952),.043,black,(0,0,0))
box('Studio clock housing',(1.91,-6.93,.88),(1.45,.45,.20),black,.025)
label('Studio clock','23:58:40',(1.34,-7.17,.88),.17,led)
box('Talkback base',(.18,-6.92,.98),(.54,.40,.10),black,.035)
limb('Talkback gooseneck',(.18,-6.92,1.03),(.18,-6.86,1.37),.021,.021,silver)
ellipsoid('Talkback capsule',(.18,-6.86,1.40),(.075,.09,.09),black)
label('Talkback label','TALKBACK',(-.035,-7.14,.98),.067,ivory)
light('Booth ceiling softbox',(0,-6.5,4.2),(0,-6,1),(.7,.80,1),450,4)
light('Booth warm practical',(-2.5,-7,2),(-1,-6,1),(1,.40,.15),70,1.4)
tag_new(before,role='control-room')

# Cameras are shared by all broadcast tiers, so comparison changes only the set.
def camera(name,loc,target,scale=None,lens=42):
    data=bpy.data.cameras.new(name); ob=bpy.data.objects.new(name,data); kit.objects.link(ob); ob.location=loc
    ob.rotation_euler=(Vector(target)-ob.location).to_track_quat('-Z','Y').to_euler()
    data.type='ORTHO' if scale else 'PERSP'
    if scale: data.ortho_scale=scale
    data.lens=lens
    return ob
group=camera('CAM | Broadcast group',(0,-11,7.0),(0,-1.3,1.1),10.7)
fancam=camera('CAM | Frontman fancam',(.10,-7.5,2.8),(0,-1.08,1.35),3.40)
synthcam=camera('CAM | Producer fancam',(-2.7,-5.4,3.1),(-1.5,0,1.3),3.25)
keycam=camera('CAM | Keytar fancam',(2.6,-5.4,2.9),(1.45,-.42,1.30),3.25)
drumcam=camera('CAM | Drummer fancam',(.2,-4.6,3.4),(0,.65,1.8),3.25)
controlcam=camera('CAM | Control room',(0,-11.4,4.5),(0,-4.75,2.0),8.8)
for cam in [fancam,synthcam,keycam,drumcam]:
    base=cam.location.copy()
    for f in [1,31,61,91,121]:
        cam.location=base+Vector((.045*math.sin((f-1)*math.tau/120),0,0))
        cam.keyframe_insert(data_path='location',frame=f)

# Beat-light curve returns exactly to its initial state at frame 121.
for ob in kit.objects:
    if ob.type=='LIGHT' and ('rim' in ob.name or 'T3 beam' in ob.name):
        base=ob.data.energy
        for f in range(1,122,12):
            ob.data.energy=base; ob.data.keyframe_insert('energy',frame=f)
            ob.data.energy=base*.45; ob.data.keyframe_insert('energy',frame=f+4)
for matname in ['Signal coral','Electric cyan']:
    n=next(n for n in bpy.data.materials[matname].node_tree.nodes if n.type=='BSDF_PRINCIPLED')
    for f in range(1,122,48):
        n.inputs['Emission Strength'].default_value=3.5; n.inputs['Emission Strength'].keyframe_insert('default_value',frame=f)
        n.inputs['Emission Strength'].default_value=.7; n.inputs['Emission Strength'].keyframe_insert('default_value',frame=f+5)

# Settings used by the render driver and editable in Blender's scene properties.
manifest={'show':'Signal Room','band':'Synthwave','hooks':['Afterimage','Night Frequency','Last Signal'],'fps':24,'frames':120,'bpm':120,'segments':[{'id':i,'name':s[0],'hex':s[2]} for i,s in enumerate(SEGMENTS)],'cameras':[c.name for c in [group,fancam,synthcam,keycam,drumcam,controlcam]],'patterns':list(patterns),'screen_object':screen.name,'tiers':{'1':'Late-night cable','2':'Mid-week show','3':'Flagship broadcast'}}
(OUT/'manifest.json').write_text(json.dumps(manifest,indent=2)+'\n')
# Keep the runtime controller as a Blender text block as well as a source file.
textblock=bpy.data.texts.get('Signal Room controls.py') or bpy.data.texts.new('Signal Room controls.py')
textblock.clear(); textblock.write((ROOT/'mvp_controls.py').read_text())
exec(compile((ROOT/'mvp_controls.py').read_text(),'mvp_controls.py','exec'))
set_state(tier=3,camera_name='group',encore=False,tone='cool')
scene.frame_set(1)
for screen_ui in bpy.data.screens:
    for area in screen_ui.areas:
        if area.type=='VIEW_3D' and hasattr(area.spaces.active,'region_3d'):
            area.spaces.active.region_3d.view_perspective='CAMERA'
            area.spaces.active.region_3d.view_camera_zoom=8
bpy.ops.wm.save_as_mainfile(filepath=str(OUT/'signal-room-mvp.blend'))
print('MVP BUILD COMPLETE',len(kit.objects),'objects',scene.render.engine)
