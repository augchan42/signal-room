"""Run after build_concept.py in the same namespace; handcrafted instrument kit."""
def label(name,body,loc,size,mat,rotation=(math.pi/2,0,0)):
    data=bpy.data.curves.new(name,'FONT'); data.body=body; data.size=size; data.extrude=.0005
    ob=bpy.data.objects.new(name,data); kit.objects.link(ob); ob.location=loc; ob.rotation_euler=rotation; data.materials.append(mat)
    return ob

wood=material('Synth walnut cheeks',(.19,.073,.027))
keys=material('Keyboard ivory',(.86,.85,.78))
rubber=material('Drum pad rubber',(.014,.019,.024))
led=material('Display phosphor',(.2,.82,.65),emission=2)

def keyboard(name,x,y,z,width=1.42,white_count=35):
    before=set(kit.objects)
    box(name+' chassis',(x,y,z),(width,.46,.12),black,.025)
    for side in [-1,1]: box(name+' walnut cheek',(x+side*(width/2-.025),y,z+.03),(.06,.46,.17),wood,.015)
    usable=width-.18; step=usable/white_count; left=x-usable/2
    for k in range(white_count):
        box(name+' white key %02d'%k,(left+(k+.5)*step,y-.11,z+.075),(step*.93,.22,.045),keys,.002)
        if k%7 in [0,1,3,4,5] and k<white_count-1:
            box(name+' black key %02d'%k,(left+(k+1)*step,y-.058,z+.111),(step*.56,.12,.032),hair,.002)
    box(name+' control panel',(x,y+.11,z+.075),(width-.13,.17,.045),ink,.005)
    for j in range(13):
        box(name+' knob %02d'%j,(x-width*.40+j*width*.062,y+.12,z+.116),(.027,.026,.038),silver,.006)
    box(name+' green display',(x+.36,y+.105,z+.104),(.14,.09,.01),led,.003)
    label(name+' BORG badge','BORG',(x-width*.40,y-.236,z-.006),.088,keys)
    label(name+' panel legend','POLYPHONIC  /  SIGNAL',(x-.30,y+.08,z+.104),.020,keys,(0,0,0))
    # Performer stands behind the instrument at +Y: keys must be on that side.
    # Rotate the entire chassis/panel/key assembly, then add a rear maker badge.
    from mathutils import Matrix, Vector
    rotation=Matrix.Rotation(math.pi,3,'Z'); pivot=Vector((x,y,z))
    for ob in set(kit.objects)-before:
        ob.location=pivot+rotation@(ob.location-pivot)
        ob.rotation_euler=(rotation@ob.rotation_euler.to_matrix()).to_euler()
    label(name+' audience rear badge','BORG',(x-width*.40,y-.236,z-.006),.088,keys)

keyboard('BORG main synthesizer',-1.55,-.24,1.13)
keyboard('BORG upper synthesizer',-1.55,.11,1.40,1.27,28)
for side in [-1,1]:
    x=-1.55+side*.55
    limb('Synth X stand',(x,-.46,.18),(-1.55-side*.55,-.10,1.09),.035,.035,silver)
    limb('Synth upper tier support',(x,.20,.20),(x,.20,1.40),.028,.03,silver)
box('Synth sustain pedal',(-1.43,-.24,.18),(.12,.24,.035),black,.01)
exec(compile(open('/Users/auchan/projects/signal-room/art/blender/correct_keyboard_stack.py').read(), 'correct_keyboard_stack.py', 'exec'))

# A separate movable keytar assembly, with real individual keys.
keytar_objects=set(kit.objects)
box('Keytar angular body',(1.45,-.70,1.19),(.78,.13,.28),ivory,.05)
box('Keytar black inset',(1.44,-.777,1.2),(.61,.024,.20),black,.014)
for k in range(18):
    box('Keytar white key %02d'%k,(1.16+k*.030,-.798,1.18),(.027,.021,.145),keys,.002)
    if k%7 in [0,1,3,4,5]: box('Keytar black key %02d'%k,(1.175+k*.030,-.815,1.215),(.015,.018,.074),hair,.002)
box('Keytar neck',(1.98,-.70,1.23),(.35,.12,.12),ivory,.025)
box('Keytar touch strip',(1.99,-.77,1.24),(.21,.022,.028),black,.004)
label('Keytar badge','SIGNAL',(1.11,-.787,1.30),.035,black)
pivot=Vector((1.45,-.7,1.19))
from mathutils import Matrix
rot=Matrix.Rotation(-.22,4,'Y')
for ob in set(kit.objects)-keytar_objects:
    ob.location=pivot+rot.to_3x3()@(ob.location-pivot)
    ob.rotation_euler=(rot.to_3x3()@ob.rotation_euler.to_matrix()).to_euler()
limb('Keytar shoulder strap',(1.27,-.57,1.62),(1.82,-.64,1.23),.047,.018,black)

def pad(name,loc,radius):
    verts=[]
    for z in [-.025,.025]:
        for k in range(8):
            a=2*math.pi*k/8
            verts.append((math.cos(a)*radius,math.sin(a)*radius,z))
    faces=[tuple(reversed(range(8))),tuple(range(8,16))]+[(k,(k+1)%8,(k+1)%8+8,k+8) for k in range(8)]
    ob=mesh(name,verts,faces,silver,.008); ob.location=loc; ob.rotation_euler.x=.18
    top=mesh(name+' rubber',[(x*.91,y*.91,z+.012) for x,y,z in verts],faces,rubber,.006); top.location=loc; top.rotation_euler.x=.18

for k,(x,y,z,r) in enumerate([(-.43,.22,1.67,.23),(.1,.14,1.65,.23),(.54,.37,1.8,.24),(-.58,.70,1.99,.24)]):
    pad('Electronic octagonal pad %d'%k,(x,y,z),r)
    limb('Pad chrome upright',(x,y,.70),(x,y,z-.03),.025,.025,silver)
    limb('Pad stand foot',(x-.15,y,.72),(x+.15,y,.72),.025,.025,silver)
limb('Drumstick left',(-.24,.35,1.86),(-.36,.03,1.75),.013,.013,ivory)
limb('Drumstick right',(.24,.35,1.86),(.37,.07,1.78),.013,.013,ivory)
box('Electronic kick unit',(0,.15,1.00),(.39,.15,.45),black,.07)
label('Drum kick insignia','SR',(-.095,.066,1.0),.14,keys)

def mic(name,x,y,height):
    box(name+' base',(x,y,.18),(.33,.27,.04),black,.06)
    limb(name+' stand',(x,y,.20),(x,y,height),.024,.024,silver)
    limb(name+' microphone',(x-.08,y,height),(x+.08,y,height),.045,.045,black)
    ellipsoid(name+' grille',(x-.10,y,height),(.052,.047,.047),silver)
mic('Lead vocal mic',.12,-1.38,1.78)
mic('Backing vocal mic',1.91,-.93,1.77)
box('On air sign housing',(-1.92,1.17,3.35),(1.10,.12,.30),black)
label('ON AIR lettering','ON AIR',(-2.38,1.10,3.28),.19,coral)
label('Music show title','S I G N A L   R O O M',(-.67,1.10,3.30),.14,ivory)
print('Instrument stations added:',len(kit.objects),'objects')
