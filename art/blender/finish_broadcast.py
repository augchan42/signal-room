"""Apply the authored broadcast finish with ffmpeg; no effect is baked into geometry."""
from pathlib import Path
import subprocess
ROOT=Path(__file__).resolve().parent/'mvp'
FONT='/System/Library/Fonts/Supplemental/DIN Alternate Bold.ttf'
MONO='/System/Library/Fonts/Supplemental/Courier New.ttf'
def run(args):
    subprocess.run(['ffmpeg','-hide_banner','-loglevel','error','-y',*args],check=True)
def text(body,x,y,size=32,color='0xE6DDC9',font=FONT):
    return f"drawtext=fontfile='{font}':text='{body}':x={x}:y={y}:fontsize={size}:fontcolor={color}"
def treatment(tier,caption=True,special=False):
    filters=[]
    if tier==1:
        filters+=['lenscorrection=k1=0.012:k2=0.004','rgbashift=rh=2:bh=-2','drawgrid=w=iw:h=4:t=1:c=black@0.12','vignette=angle=0.45', 'scale=1000:1778','pad=1080:1920:40:71:color=0x07101C']
    elif tier==2:
        filters+=['rgbashift=rh=1:bh=-1','drawgrid=w=iw:h=5:t=1:c=black@0.045','vignette=angle=0.28']
    else: filters+=['vignette=angle=0.18']
    if caption:
        filters+=['drawbox=x=62:y=90:w=7:h=58:color=0x53DEEE:t=fill',text('SIGNAL ROOM',86,87,43),text('SPECIAL STAGE' if special else ['LATE NIGHT / CH 01','MIDWEEK / CH 02','FLAGSHIP / CH 03'][tier-1],88,139,20,font=MONO)]
        filters+=['drawbox=x=62:y=1600:w=650:h=150:color=0x081422@0.87:t=fill','drawbox=x=62:y=1600:w=7:h=150:color=0x53DEEE:t=fill',text('SYNTHWAVE',87,1618,22,'0x75DCE8'),text('AFTERIMAGE',84,1652,50),text('FIRST TRANSMISSION  /  120 BPM',88,1712,18,font=MONO)]
    return ','.join(filters)
for tier in [1,2,3]:
    run(['-i',str(ROOT/f'tier-{tier}.png'),'-vf',treatment(tier),'-frames:v','1',str(ROOT/f'broadcast-tier-{tier}.png')])
run(['-i',str(ROOT/'special-stage.png'),'-vf',treatment(1,special=True),'-frames:v','1',str(ROOT/'broadcast-special-stage.png')])
run(['-i',str(ROOT/'first-win.png'),'-vf',treatment(3)+',drawbox=x=160:y=210:w=760:h=165:color=0x101928@0.9:t=fill,'+text('FIRST MUSIC SHOW WIN',195,238,43)+','+text('NO. 1  /  SYNTHWAVE',286,302,30,'0xFFD166'),'-frames:v','1',str(ROOT/'broadcast-first-win.png')])
# Nominee split screen is deliberately graphic: rival identity is a placeholder.
nominee=["drawbox=x=60:y=520:w=465:h=640:color=0x113A4C:t=fill","drawbox=x=555:y=520:w=465:h=640:color=0x29273D:t=fill",text('SIGNAL ROOM',62,100,50),text('THIS WEEK / FINAL NOMINEES',62,179,24,font=MONO),text('SYNTHWAVE',90,580,42),text('AFTERIMAGE',90,640,25),text('NIGHT INDEX',581,580,42),text('GLASSHOUSE',581,640,25),text('01',170,760,150,'0x53DEEE'),text('02',670,760,150,'0xAE86FF'),text('RETRO / ALTERNATIVE',89,1060,22),text('PERFORMANCE / VOCAL',581,1060,22),text('THE SIGNAL IS YOURS',195,1420,50),text('CHART RECAP / RESULTS REVEALED NEXT',150,1500,25,font=MONO)]
run(['-f','lavfi','-i','color=c=0x08111E:s=1080x1920:d=1','-vf',','.join(nominee),'-frames:v','1',str(ROOT/'nominee-split.png')])
for hook in ['AFTERIMAGE','NIGHT FREQUENCY','LAST SIGNAL']:
    caption=['drawbox=x=62:y=1600:w=850:h=150:color=0x081422@0.90:t=fill','drawbox=x=62:y=1600:w=7:h=150:color=0x53DEEE:t=fill',text('SYNTHWAVE',87,1618,22,'0x75DCE8'),text(hook,84,1652,50),text('FIRST TRANSMISSION  /  120 BPM',88,1712,18,font=MONO)]
    run(['-f','lavfi','-i','color=c=black@0:s=1080x1920:d=1,format=rgba','-vf',','.join(caption),'-frames:v','1',str(ROOT/('lower-third-'+hook.lower().replace(' ','-')+'.png'))])
if (ROOT/'frames'/'f-0120.png').exists():
    run(['-framerate','24','-i',str(ROOT/'frames'/'f-%04d.png'),'-vf',treatment(3)+','+text('FRONTMAN / FANCAM',610,140,24),'-frames:v','120','-c:v','libx264','-crf','20','-pix_fmt','yuv420p','-movflags','+faststart',str(ROOT/'signal-room-fancam-5s.mp4')])
    # Preview still matches the motion clip and caption placement.
    run(['-i',str(ROOT/'frames'/'f-0001.png'),'-vf',treatment(3)+','+text('FRONTMAN / FANCAM',610,140,24),'-frames:v','1',str(ROOT/'fancam-preview.png')])
if (ROOT/'mask-frames'/'m-0120.png').exists():
    run(['-framerate','24','-i',str(ROOT/'mask-frames'/'m-%04d.png'),'-frames:v','120','-c:v','libx264','-crf','0','-pix_fmt','yuv420p','-movflags','+faststart',str(ROOT/'screen-mask-fancam-5s.mp4')])
print('Broadcast finishing complete')
