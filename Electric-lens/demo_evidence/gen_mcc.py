#!/usr/bin/env python3
from PIL import Image, ImageDraw, ImageFont
OUT = "/mnt/c/AI/Projects/ElectricSafe"; SIZE = 512
def font(sz):
    for p in ["/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"]:
        try: return ImageFont.truetype(p, sz)
        except Exception: pass
    return ImageFont.load_default()
def center(d,y,t,f,fill):
    w=d.textbbox((0,0),t,font=f)[2]; d.text(((SIZE-w)/2,y),t,font=f,fill=fill)
im=Image.new("RGB",(SIZE,SIZE),(12,14,20)); d=ImageDraw.Draw(im)
d.rectangle([30,30,SIZE-30,SIZE-30],outline=(90,96,110),width=3)
center(d,60,"MCC CABINET",font(40),(200,205,215))
# open door depiction
d.rectangle([90,150,300,400],outline=(150,156,168),width=4)      # cabinet body
d.line([300,150,420,120],fill=(150,156,168),width=4)             # open door top
d.line([300,400,420,370],fill=(150,156,168),width=4)             # open door bottom
d.line([420,120,420,370],fill=(150,156,168),width=4)
center(d,430,"DOOR: OPEN",font(56),(90,220,120))
im.save(f"{OUT}/mcc_open.png"); print("wrote mcc_open")
