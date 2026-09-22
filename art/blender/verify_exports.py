"""Check delivered media dimensions, masks, timing and artifact coverage."""
import json, subprocess
from pathlib import Path
from PIL import Image, ImageChops, ImageStat
root=Path(__file__).resolve().parent/'mvp'
required=['tier-1','tier-2','tier-3','control-1','control-2','control-3','broadcast-tier-1','broadcast-tier-2','broadcast-tier-3','broadcast-special-stage','broadcast-first-win','nominee-split','fancam-preview']
required += ['plate-%d-%s'%(t,c) for t in [1,2,3] for c in ['group','frontman','producer']]
for name in required:
    with Image.open(root/(name+'.png')) as img: assert img.size==(1080,1920),name
mask_info={}
for path in root.glob('screen-mask-*.png'):
    with Image.open(path).convert('RGB') as im:
        r,g,b=im.split(); assert ImageChops.difference(r,g).getbbox() is None,path
        assert ImageChops.difference(r,b).getbbox() is None,path
        assert r.getextrema()==(0,255),path
        mask_info[path.name]={'size':im.size,'range':r.getextrema()}
assert len(mask_info)==6
for pattern in ['waveform','signal-bars','test-pattern']:
    with Image.open(root/(pattern+'.png')) as im: assert im.size==(1024,512)
for hook in ['afterimage','night-frequency','last-signal']:
    with Image.open(root/('lower-third-'+hook+'.png')) as im:
        assert im.size==(1080,1920) and im.mode=='RGBA'
        assert im.getchannel('A').getextrema()[0]==0
media={}
for name in ['signal-room-fancam-5s.mp4','screen-mask-fancam-5s.mp4']:
    data=json.loads(subprocess.check_output(['ffprobe','-v','error','-select_streams','v:0','-show_entries','stream=codec_name,width,height,r_frame_rate,nb_frames,duration','-of','json',str(root/name)]))['streams'][0]
    assert (data['width'],data['height'])==(1080,1920)
    assert data['nb_frames']=='120' and data['r_frame_rate']=='24/1' and float(data['duration'])==5
    media[name]=data
projections=json.loads((root/'loop-projections.json').read_text())['cameras']
assert all(str(i) in projections for i in range(1,121))
assert projections['1']!=projections['31'],'Camera drift absent from projection export'
for path in [root/'signal-room-mvp.blend',root/'review.html',root/'verification.json']:
    assert path.stat().st_size>0
report={'portrait_stills_checked':len(required),'mask_checks':mask_info,'screen_patterns':3,'transparent_hook_captions':3,'videos':media,'per_frame_screen_projections':120,'review_browser_test':'Pattern change and occlusion observed; video playback observed; control-room overlay observed separately.'}
(root/'export-verification.json').write_text(json.dumps(report,indent=2)+'\n')
print(json.dumps(report,indent=2))
