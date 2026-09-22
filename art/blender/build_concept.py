"""Signal Room: editable first concept, run inside Blender."""
import bpy
import math
from mathutils import Vector

scene = bpy.context.scene
kit = bpy.data.collections.get('SIGNAL ROOM - concept 01')
if kit is None:
    kit = bpy.data.collections.new('SIGNAL ROOM - concept 01')
    scene.collection.children.link(kit)
for obj in list(kit.objects):
    bpy.data.objects.remove(obj, do_unlink=True)

def material(name, color, metal=0, emission=0):
    m = bpy.data.materials.get(name) or bpy.data.materials.new(name)
    m.diffuse_color = (*color, 1)
    m.use_nodes = True
    n = next(n for n in m.node_tree.nodes if n.type == 'BSDF_PRINCIPLED')
    n.inputs['Base Color'].default_value = (*color, 1)
    n.inputs['Roughness'].default_value = .48
    n.inputs['Metallic'].default_value = metal
    n.inputs['Emission Color'].default_value = (*color, 1)
    n.inputs['Emission Strength'].default_value = emission
    return m

ink = material('Ink blue', (.012, .022, .052))
black = material('Charcoal wool', (.018, .021, .03))
ivory = material('Ivory cloth', (.68, .61, .48))
cyan = material('Electric cyan', (.025, .65, .85), emission=3)
coral = material('Signal coral', (.65, .075, .052), emission=1.8)
skin = material('Pale warm skin', (.63, .45, .35))
hair = material('Blue black hair', (.009, .012, .02))
silver = material('Brushed silver', (.25, .3, .34), .75)
wine = material('Oxblood satin', (.18, .018, .032))

def mesh(name, verts, faces, mat, bevel=0):
    data = bpy.data.meshes.new(name)
    data.from_pydata(verts, [], faces)
    data.update()
    ob = bpy.data.objects.new(name, data)
    kit.objects.link(ob)
    ob.data.materials.append(mat)
    if bevel:
        mod = ob.modifiers.new('Soft edges', 'BEVEL')
        mod.width = bevel
        mod.segments = 3
        ob.modifiers.new('Weighted normals', 'WEIGHTED_NORMAL')
    return ob

def box(name, loc, size, mat, bevel=.025):
    x,y,z = (v/2 for v in size)
    verts = [(-x,-y,-z),(x,-y,-z),(x,y,-z),(-x,y,-z),(-x,-y,z),(x,-y,z),(x,y,z),(-x,y,z)]
    faces = [(0,3,2,1),(4,5,6,7),(0,1,5,4),(1,2,6,5),(2,3,7,6),(3,0,4,7)]
    ob = mesh(name, verts, faces, mat, bevel)
    ob.location = loc
    return ob

def tapered(name, loc, bottom, top, height, mat):
    bx,by = bottom
    tx,ty = top
    v = [(-bx,-by,0),(bx,-by,0),(bx,by,0),(-bx,by,0),(-tx,-ty,height),(tx,-ty,height),(tx,ty,height),(-tx,ty,height)]
    ob = mesh(name,v,[(0,3,2,1),(4,5,6,7),(0,1,5,4),(1,2,6,5),(2,3,7,6),(3,0,4,7)],mat,.035)
    ob.location=loc
    return ob

def ellipsoid(name, loc, scale, mat, segments=16, rings=10):
    verts=[]
    faces=[]
    for j in range(rings+1):
        theta=math.pi*j/rings
        for i in range(segments):
            phi=2*math.pi*i/segments
            verts.append((scale[0]*math.sin(theta)*math.cos(phi),scale[1]*math.sin(theta)*math.sin(phi),scale[2]*math.cos(theta)))
    for j in range(rings):
        for i in range(segments):
            a=j*segments+i; b=j*segments+(i+1)%segments
            faces.append((a,b,b+segments,a+segments))
    ob=mesh(name,verts,faces,mat)
    ob.location=loc
    for p in ob.data.polygons: p.use_smooth=True
    return ob

def limb(name,a,b,width,depth,mat):
    a,b=Vector(a),Vector(b)
    ob=box(name,(a+b)/2,(width,depth,(b-a).length),mat,.035)
    ob.rotation_euler=(b-a).to_track_quat('Z','Y').to_euler()
    return ob

box('Studio floor',(0,0,-.16),(14,12,.2),ink)
box('Shallow performance platform',(0,0,.03),(5.8,3.4,.25),black)
box('Stage front trim',(0,-1.71,.06),(5.7,.035,.055),cyan,.008)
box('Rear screen housing',(0,1.5,1.85),(5.5,.2,3.4),black)
box('Rear screen glass',(0,1.37,1.85),(5.25,.04,3.15),ink)
for x in [-2.85,2.85]: box('Frame upright',(x,1.17,1.9),(.035,.055,3.55),cyan,.008)
for z in [.14,3.68]: box('Frame crossbar',(0,1.17,z),(5.74,.055,.035),cyan,.008)
for i in range(9):
    ob=box('Screen diagonal %02d'%i,(-2.05+i*.51,1.30,1.95),(.016,.025,2.7),coral,.003)
    ob.rotation_euler.y=-.4
for x in [-2.3,2.3]:
    box('Floor monitor',(x,-1.15,.33),(.65,.45,.35),black)
    for z in [.23,.29,.35,.41]: box('Monitor grille',(x,-1.385,z),(.54,.025,.012),silver,.004)

for i,(x,y) in enumerate([(-1.8,.15),(-.6,-.18),(.6,-.05),(1.8,.25)]):
    prefix=['01 Cropped jacket','02 Long vest','03 Oversized blazer','04 Sleeveless'][i]
    for side in [-1,1]:
        hip=(x+side*.115,y,1.03)
        knee=(x+side*.14,y-.025,.61)
        ankle=(x+side*.19,y+.01,.25)
        limb(prefix+' trouser thigh',hip,knee,.19,.22,black)
        limb(prefix+' trouser calf',knee,ankle,.145,.18,black)
        box(prefix+' boot',(ankle[0],y-.08,.245),(.19,.37,.20),black,.04)
        box(prefix+' boot trim',(ankle[0],y-.255,.27),(.145,.02,.02),silver,.006)
    garment=[wine,black,ivory,black][i]
    bottom_z=[1.05,.86,.91,1.05][i]
    shoulder=[.26,.225,.31,.23][i]
    tapered(prefix+' garment',(x,y,bottom_z),(.18,.105),(shoulder,.13),1.62-bottom_z,garment)
    box(prefix+' shirt',(x,y-.137,1.42),(.16,.025,.32),ivory if i!=2 else black,.009)
    for side in [-1,1]:
        if i!=3:
            lapel=box(prefix+' lapel',(x+side*.095,y-.154,1.46),(.085,.018,.31),black if i==2 else garment,.005)
            lapel.rotation_euler.y=side*.30
        a=(x+side*(shoulder+.025),y,1.53)
        b=(x+side*(shoulder+.09),y-.025,1.23)
        c=(x+side*(shoulder+.06),y-.13,1.02)
        limb(prefix+' upper arm',a,b,.14,.17,skin if i==3 else garment)
        limb(prefix+' forearm',b,c,.105,.135,skin if i in [1,3] else garment)
        ellipsoid(prefix+' hand',(c[0],c[1],.98),(.065,.065,.10),skin)
    box(prefix+' neck',(x,y,1.67),(.13,.14,.18),skin,.03)
    head=ellipsoid(prefix+' head',(x,y-.015,1.88),(.145,.125,.205),skin)
    head.rotation_euler.z=[-.1,.1,-.12,.08][i]
    box(prefix+' nose',(x,y-.145,1.86),(.035,.05,.075),skin,.013)
    for side in [-1,1]:
        brow=box(prefix+' brow',(x+side*.06,y-.134,1.93),(.062,.012,.012),hair,.003)
        brow.rotation_euler.y=side*.12
    box(prefix+' mouth',(x,y-.139,1.79),(.064,.01,.009),wine,.002)
    ellipsoid(prefix+' hair cap',(x,y+.012,2.012),(.155,.13,.11),hair)
    sweep=box(prefix+' swept fringe',(x-.025,y-.063,2.038),(.25,.16,[.10,.14,.08,.18][i]),hair,.04)
    sweep.rotation_euler.y=[-.25,.2,-.1,.32][i]
    if i==1: box(prefix+' long side hair',(x-.137,y+.005,1.9),(.05,.17,.27),hair,.025)
    if i==2: box(prefix+' wide shoulder trim',(x,y-.14,1.61),(.53,.025,.02),silver,.006)
    box(prefix+' belt',(x,y-.122,1.07),(.33,.035,.035),black,.006)
    box(prefix+' buckle',(x,y-.146,1.07),(.055,.02,.045),silver,.005)

def light(name,loc,target,color,power,size):
    data=bpy.data.lights.new(name,'AREA'); data.energy=power; data.color=color; data.shape='DISK'; data.size=size
    ob=bpy.data.objects.new(name,data); kit.objects.link(ob); ob.location=loc
    ob.rotation_euler=(Vector(target)-ob.location).to_track_quat('-Z','Y').to_euler()

light('Warm portrait key',(-3,-4,4),(0,0,1.3),(1,.72,.48),750,4)
light('Cool soft fill',(3,-2,2.8),(0,0,1.3),(.25,.6,1),450,3)
light('Cyan rear rim',(-2,1,3),(0,0,1),(.04,.7,1),950,2)
light('Coral rear rim',(2,1,3),(0,0,1),(1,.12,.07),750,2)
camera_data=bpy.data.cameras.new('Portrait master')
cam=bpy.data.objects.new('Portrait master',camera_data); kit.objects.link(cam)
cam.location=(.15,-10,3.5)
cam.rotation_euler=(Vector((0,0,1.65))-cam.location).to_track_quat('-Z','Y').to_euler()
cam.data.type='ORTHO'; cam.data.ortho_scale=10.9; scene.camera=cam
scene.render.resolution_x=1080; scene.render.resolution_y=1920; scene.render.resolution_percentage=50
try: scene.render.engine='CYCLES'
except TypeError: pass
if scene.render.engine=='CYCLES': scene.cycles.samples=24
scene.world.color=(.025,.025,.025)
scene.world.use_nodes=True
bg=next(n for n in scene.world.node_tree.nodes if n.type=='BACKGROUND')
bg.inputs['Color'].default_value=(.035,.045,.08,1); bg.inputs['Strength'].default_value=.22
for screen in bpy.data.screens:
    for area in screen.areas:
        if area.type=='CONSOLE': area.type='VIEW_3D'
        if area.type=='VIEW_3D' and hasattr(area.spaces.active, 'region_3d'):
            area.spaces.active.region_3d.view_perspective='CAMERA'
            area.spaces.active.shading.color_type='MATERIAL'
            area.spaces.active.overlay.show_overlays=False
            area.spaces.active.region_3d.view_camera_zoom=0
print('Signal Room concept built:',len(kit.objects),'objects')
