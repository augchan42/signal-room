"""Render stills / mask assets / a five-second loop from the saved MVP scene."""
import bpy, sys, json, os
from pathlib import Path
from mathutils import Vector
from bpy_extras.object_utils import world_to_camera_view
ROOT=Path('/Users/auchan/projects/signal-room/art/blender')
OUT=ROOT/'mvp'
exec(compile((ROOT/'mvp_controls.py').read_text(),'mvp_controls.py','exec'))
scene=bpy.context.scene
mode=sys.argv[sys.argv.index('--')+1] if '--' in sys.argv else 'drafts'
scene.render.engine='BLENDER_EEVEE'
scene.render.resolution_percentage=50 if mode=='drafts' else 100
scene.render.image_settings.file_format='PNG'
scene.render.image_settings.color_mode='RGBA'

def render(name):
    scene.render.filepath=str(OUT/(name+'.png'))
    bpy.ops.render.render(write_still=True)

if mode in ['drafts','stills']:
    for tier in [1,2,3]:
        set_state(tier=tier,camera_name='group',tone='cool'); scene.frame_set(1)
        render(('draft-' if mode=='drafts' else '')+'tier-%d'%tier)
    for tier in ([3] if mode=='drafts' else [1,2,3]):
        set_state(tier=tier,camera_name='control'); scene.frame_set(1)
        render(('draft-' if mode=='drafts' else '')+'control-%d'%tier)
    if mode=='stills':
        set_state(tier=3,camera_name='group',encore=True); render('first-win')
        set_state(tier=1,camera_name='group',tone='neutral',special=True); render('special-stage')
        set_state(tier=3,camera_name='producer'); render('producer')
elif mode=='plates':
    for tier in [1,2,3]:
        for camera in ['group','frontman','producer']:
            set_state(tier=tier,camera_name=camera,pattern='blank'); scene.frame_set(1)
            render('plate-%d-%s'%(tier,camera))
elif mode=='control-special':
    for tier in [1,2,3]:
        set_state(tier=tier,camera_name='control'); scene.frame_set(1); render('control-%d'%tier)
    set_state(tier=1,camera_name='group',tone='neutral',special=True); render('special-stage')
elif mode=='loop':
    set_state(tier=3,camera_name='frontman',tone='cool')
    folder=OUT/'frames'; folder.mkdir(exist_ok=True)
    scene.render.filepath=str(folder/'f-')
    bpy.ops.render.render(animation=True)
elif mode in ['masks','loop-masks']:
    # Black emission for all geometry, white emission only on the screen.
    # Foreground characters correctly occlude the screen; no light contamination.
    def flat(name,color):
        m=bpy.data.materials.new(name); m.use_nodes=True; nodes=m.node_tree.nodes; nodes.clear()
        e=nodes.new('ShaderNodeEmission'); e.inputs['Color'].default_value=(*color,1)
        o=nodes.new('ShaderNodeOutputMaterial'); m.node_tree.links.new(e.outputs[0],o.inputs['Surface']); return m
    black=flat('MASK BLACK',(0,0,0)); white=flat('MASK WHITE',(1,1,1))
    for ob in scene.objects:
        if ob.type in ['MESH','FONT','CURVE'] and ob.name!='T3 bounded haze':
            for slot in ob.material_slots: slot.link='OBJECT'; slot.material=white if ob.name=='REAR SCREEN | mask target' else black
    bg=next(n for n in scene.world.node_tree.nodes if n.type=='BACKGROUND'); bg.inputs['Strength'].default_value=0
    try: scene.view_settings.view_transform='Standard'
    except TypeError: pass
    scene.view_settings.exposure=0; scene.view_settings.gamma=1
    projections={}
    for camera in (['frontman'] if mode=='loop-masks' else ['group','frontman','producer','keytar','drummer','control']):
        set_state(tier=3,camera_name=camera); scene.frame_set(1)
        for ob in scene.objects:
            if ob.type in ['MESH','FONT','CURVE'] and ob.name!='T3 bounded haze':
                for slot in ob.material_slots:
                    slot.link='OBJECT'; slot.material=white if ob.name=='REAR SCREEN | mask target' else black
            if ob.type=='LIGHT' or ob.name=='T3 bounded haze' or ob.get('role')=='screen-echo': ob.hide_render=True
        render('screen-mask-'+camera)
        target=bpy.data.objects['REAR SCREEN | mask target']
        projections[camera]=[[round(p.x,6),round(1-p.y,6)] for p in [world_to_camera_view(scene,scene.camera,target.matrix_world@v.co) for v in target.data.vertices]]
    if mode=='loop-masks':
        folder=OUT/'mask-frames'; folder.mkdir(exist_ok=True)
        scene.render.filepath=str(folder/'m-'); bpy.ops.render.render(animation=True)
        for f in range(1,121):
            scene.frame_set(f)
            projections[str(f)]=[[round(p.x,6),round(1-p.y,6)] for p in [world_to_camera_view(scene,scene.camera,target.matrix_world@v.co) for v in target.data.vertices]]
    (OUT/('loop-projections.json' if mode=='loop-masks' else 'screen-projections.json')).write_text(json.dumps({'coordinates':'normalized top-left origin; vertices bottom-left, bottom-right, top-right, top-left','cameras':projections},indent=2))
print('RENDER JOB COMPLETE',mode)
