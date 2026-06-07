"""Generate ChainReaction logo: 1124x300, transparent bg, player colors, solid orb."""
from PIL import Image, ImageDraw, ImageFont, ImageFilter

W, H = 1124, 300
OUT = r"D:\ChainReaction\src\main\resources\view\assets\logo.png"

# Player colors (CodinGame defaults: player1=red, player2=blue)
RED  = (255,  50,  50)   # player 1
BLUE = ( 34, 161, 228)   # player 2

# ── Font ──────────────────────────────────────────────────────────────────────
FONT_PATH = "C:/Windows/Fonts/AGENCYB.TTF"
FONT_SIZE = 210
font = ImageFont.truetype(FONT_PATH, FONT_SIZE)

# ── Measure ───────────────────────────────────────────────────────────────────
# "ChainReaction": 'o' is at index 11
# Split: "Chain" (blue) + "Reacti" (red) + [orb] + "n" (red)
CHAIN   = "Chain"
REACTI  = "Reacti"
AFTER   = "n"

_tmp_img  = Image.new("RGBA", (W, H), (0, 0, 0, 0))
_tmp_draw = ImageDraw.Draw(_tmp_img)

def bbox(txt):
    b = _tmp_draw.textbbox((0, 0), txt, font=font)
    return b  # (left, top, right, bottom)

def bw(txt):
    b = bbox(txt)
    return b[2] - b[0]

full_bb   = bbox("ChainReaction")
full_h    = full_bb[3] - full_bb[1]
full_yoff = full_bb[1]            # top bearing (pixels above baseline)

chain_w  = bw(CHAIN)
reacti_w = bw(REACTI)
after_w  = bw(AFTER)

# Orb: match lowercase height so it sits in the x-height region
o_bb  = bbox("o")
o_h   = o_bb[3] - o_bb[1]
ORB_D = int(o_h * 1.10)
ORB_R = ORB_D // 2

total_w = chain_w + reacti_w + ORB_D + after_w
start_x = (W - total_w) // 2
text_y  = (H - full_h) // 2 - full_yoff   # center vertically

# Positions
chain_x  = start_x
reacti_x = start_x + chain_w
orb_cx   = reacti_x + reacti_w + ORB_R
after_x  = orb_cx + ORB_R
# Orb center: align with lowercase vertical midpoint (lowered)
o_mid_y  = text_y + o_bb[1] + o_h // 2
orb_cy   = o_mid_y + 8   # shift down a few px

# ── Glow for text only ────────────────────────────────────────────────────────
def text_glow(color, parts_pos, radius):
    """parts_pos: list of (text, x) tuples."""
    layer = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    r, g, b = color
    for txt, x in parts_pos:
        d.text((x, text_y), txt, font=font, fill=(r, g, b, 160))
    return layer.filter(ImageFilter.GaussianBlur(radius=radius))

glow_blue = text_glow(BLUE, [(CHAIN, chain_x)], 12)
glow_red  = text_glow(RED,  [(REACTI, reacti_x), (AFTER, after_x)], 12)

# ── Compose on transparent base ───────────────────────────────────────────────
canvas = Image.new("RGBA", (W, H), (0, 0, 0, 0))
canvas = Image.alpha_composite(canvas, glow_blue)
canvas = Image.alpha_composite(canvas, glow_red)

# ── Main text ─────────────────────────────────────────────────────────────────
txt_layer = Image.new("RGBA", (W, H), (0, 0, 0, 0))
td = ImageDraw.Draw(txt_layer)
td.text((chain_x,  text_y), CHAIN,  font=font, fill=(*BLUE, 255))
td.text((reacti_x, text_y), REACTI, font=font, fill=(*RED,  255))
td.text((after_x,  text_y), AFTER,  font=font, fill=(*RED,  255))
canvas = Image.alpha_composite(canvas, txt_layer)

# ── Orb: solid red fill + white dot (no glow) ─────────────────────────────────
orb_layer = Image.new("RGBA", (W, H), (0, 0, 0, 0))
od = ImageDraw.Draw(orb_layer)

R, G, B = RED
# Solid red circle
od.ellipse(
    [orb_cx - ORB_R, orb_cy - ORB_R, orb_cx + ORB_R, orb_cy + ORB_R],
    fill=(*RED, 255),
    outline=(255, 120, 120, 255),
    width=3,
)
# White orb dot at center
DOT_R = max(8, ORB_R // 4)
od.ellipse(
    [orb_cx - DOT_R, orb_cy - DOT_R, orb_cx + DOT_R, orb_cy + DOT_R],
    fill=(255, 255, 255, 255),
)
canvas = Image.alpha_composite(canvas, orb_layer)

# ── Save as RGBA PNG (transparent bg) ─────────────────────────────────────────
canvas.save(OUT, "PNG")
print(f"Saved {OUT}")
print(f"orb_cx={orb_cx} orb_cy={orb_cy} ORB_R={ORB_R} text_y={text_y}")
