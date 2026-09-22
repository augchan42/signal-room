"""Actual performer silhouette echoes and a monochrome special-stage preset."""
import bpy, math
from mathutils import Vector
scene=bpy.context.scene; scene.frame_set(1); bpy.context.view_layer.update()
kit=bpy.data.collections['SIGNAL ROOM - concept 01']
def emission_material(name,rgb):
    m=bpy.data.materials.new(name); m.use_nodes=True; n=m.node_tree.nodes; n.clear()
    e=n.new('ShaderNodeEmission'); e.inputs['Color'].default_value=(*rgb,1); e.inputs['Strength'].default_value=.7
    o=n.new('ShaderNodeOutputMaterial'); m.node_tree.links.new(e.outputs[0],o.inputs['Surface']); return m
echo_mats=[emission_material('Echo ivory',(.17,.20,.23)),emission_material('Echo blue',(.035,.065,.10))]
prefixes=['01 Cropped jacket','02 Long vest','03 Oversized blazer','04 Sleeveless']
locations=[(0,-1.08,.15),(-1.55,.28,.15),(1.45,-.42,.15),(0,.73,.72)]
for i,(prefix,origin) in enumerate(zip(prefixes,locations)):
    verts=[]; faces=[]
    for source in list(kit.objects):
        if source.type!='MESH' or not source.name.startswith(prefix): continue
        offset=len(verts)
        for vert in source.data.vertices:
            p=source.matrix_world@vert.co
            verts.append(((p.x-origin[0])*.84,0,(p.z-origin[2])*.84))
        faces.extend(tuple(offset+j for j in poly.vertices) for poly in source.data.polygons)
    data=bpy.data.meshes.new('Performer %d silhouette'%i); data.from_pydata(verts,[],faces); data.update(); data.materials.append(echo_mats[0])
    for copy in [0,1]:
        ob=bpy.data.objects.new('SCREEN ECHO %d %d'%(i,copy),data); kit.objects.link(ob)
        ob.location=(-1.8+i*1.20+copy*.10,1.325-copy*.006,.65+copy*.045)
        ob.material_slots[0].link='OBJECT'; ob.material_slots[0].material=echo_mats[copy]
        ob['role']='screen-echo'; ob['delay_frames']=12 if copy else 0
        base=ob.location.copy()
        # The second image lags the same two-beat sway by one 120-BPM beat.
        for f in range(1,122,6):
            phase=(f-1-copy*12)*math.tau/120
            ob.location=base+Vector((.035*math.sin(phase),0,0))
            ob.keyframe_insert('location',frame=f)
        ob.hide_render=True; ob.hide_viewport=True
print('Special-stage echo pattern added')
