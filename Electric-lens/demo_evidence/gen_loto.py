#!/usr/bin/env python3
# Generate deterministic, high-contrast LOTO evidence images the on-device VLM
# can OCR (breaker ID + OFF state, lock+tag). 512x512 RGB, dark industrial.
from PIL import Image, ImageDraw, ImageFont

OUT = "/mnt/c/AI/Projects/ElectricSafe"
SIZE = 512

def font(sz):
    for p in ["/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf",
              "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"]:
        try:
            return ImageFont.truetype(p, sz)
        except Exception:
            pass
    return ImageFont.load_default()

def center(d, y, text, f, fill):
    w = d.textbbox((0, 0), text, font=f)[2]
    d.text(((SIZE - w) / 2, y), text, font=f, fill=fill)

def breaker(name, code, state):
    im = Image.new("RGB", (SIZE, SIZE), (12, 14, 20))
    d = ImageDraw.Draw(im)
    d.rectangle([30, 30, SIZE - 30, SIZE - 30], outline=(90, 96, 110), width=3)
    center(d, 60, "CIRCUIT BREAKER", font(34), (200, 205, 215))
    # big ID label
    center(d, 150, code, font(120), (90, 220, 120))
    # handle box with OFF/ON
    box_col = (220, 70, 60) if state == "OFF" else (90, 200, 120)
    d.rectangle([140, 320, 372, 430], outline=box_col, width=5)
    center(d, 345, state, font(90), box_col)
    center(d, 450, "PowerFlex 753 - MCC-2", font(24), (150, 156, 168))
    im.save(f"{OUT}/{name}.png")
    print("wrote", name, code, state)

def locktag():
    im = Image.new("RGB", (SIZE, SIZE), (12, 14, 20))
    d = ImageDraw.Draw(im)
    d.rectangle([30, 30, SIZE - 30, SIZE - 30], outline=(90, 96, 110), width=3)
    center(d, 55, "ISOLATION POINT", font(30), (200, 205, 215))
    center(d, 110, "B-201", font(90), (90, 220, 120))
    # padlock shaft + body
    d.arc([216, 200, 296, 280], start=180, end=360, fill=(230, 200, 90), width=12)
    d.rectangle([206, 250, 306, 340], fill=(230, 200, 90))
    center(d, 360, "PADLOCK: APPLIED", font(28), (90, 220, 120))
    center(d, 400, "DANGER TAG: APPLIED", font(28), (220, 70, 60))
    im.save(f"{OUT}/lock_tag.png")
    print("wrote lock_tag")

breaker("breaker_b201_off", "B-201", "OFF")
breaker("breaker_b205_off", "B-205", "OFF")
locktag()
