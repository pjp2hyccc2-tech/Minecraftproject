"""Preview cameras of the estate designs (rendered with the capital's ray caster into tools/estates/previews/).

The estates dimension has a fixed late-afternoon sun in the west (fixed time 10000: about 30 degrees up).
"""
from __future__ import annotations

import os

from capital import preview as P

HERE = os.path.dirname(os.path.abspath(__file__))
OUT_DIR = os.path.join(HERE, "previews")
SUN = (30.0, 180.0)   # elevation, azimuth from +x towards +z: the west

FY = 12

VIEWS = {
    ("room", "garret"): {
        "garret_in": dict(pos=(1.6, FY + 2.6, 2.2), look=(-7.0, FY + 2.6, -0.6), fov=78, volumetric=12, vol_res=3,
                          exposure=1.35, label="屋根裏の小部屋 (garret): from the door"),
        "garret_back": dict(pos=(-5.4, FY + 2.6, 2.4), look=(3.0, FY + 2.0, -0.5), fov=80, exposure=1.35,
                            label="屋根裏の小部屋 (garret): towards the door"),
        "garret_view": dict(pos=(-5.0, FY + 2.6, 0.5), look=(-40.0, FY + 1.0, 0.5), fov=70, exposure=1.2,
                            label="屋根裏の小部屋 (garret): the view from the window"),
        "garret_out": dict(pos=(-30.0, 26.0, 18.0), look=(-4.0, FY + 2.0, 0.0), fov=60,
                           label="The inn's west face from across the street"),
    },
    ("room", "canal"): {
        "canal_in": dict(pos=(4.4, FY + 2.6, 1.6), look=(-9.0, FY + 2.2, -0.4), fov=80, volumetric=12, vol_res=3,
                         exposure=1.3, label="運河の見える部屋 (canal): from the door"),
        "canal_back": dict(pos=(-6.6, FY + 2.6, -1.6), look=(5.0, FY + 2.0, 1.2), fov=82, exposure=1.35,
                           label="運河の見える部屋 (canal): from the bay window"),
        "canal_view": dict(pos=(-7.4, FY + 2.6, 0.5), look=(-30.0, FY - 4.0, 5.0), fov=72, exposure=1.2,
                           label="運河の見える部屋 (canal): the Grand Canal"),
        "canal_out": dict(pos=(-15.0, 9.0, 22.0), look=(-6.0, FY + 1.0, 0.0), fov=64,
                          label="The manor's canal front from the far quay"),
    },
    ("room", "library"): {
        "library_in": dict(pos=(5.4, FY + 2.6, 2.6), look=(-7.0, FY + 4.5, -1.5), fov=82, volumetric=12, vol_res=3,
                           exposure=1.3, label="書斎の間 (library): from the door"),
        "library_gallery": dict(pos=(5.4, FY + 6.6, -4.4), look=(-6.0, FY + 3.0, 3.0), fov=82, exposure=1.35,
                                label="書斎の間 (library): from the gallery"),
        "library_view": dict(pos=(-5.6, FY + 2.6, -3.0), look=(-30.0, 2.0, -3.0), fov=72, exposure=1.2,
                             label="書斎の間 (library): the walled garden"),
        "library_out": dict(pos=(-28.0, 14.0, 18.0), look=(-7.0, FY + 4.0, 0.0), fov=62,
                            label="The library wing from the garden"),
    },
    ("room", "tower"): {
        "tower_in": dict(pos=(0.5, FY + 2.6, 4.6), look=(-1.0, FY + 2.2, -6.0), fov=84, exposure=1.35,
                         label="星見の塔の部屋 (tower): from the door"),
        "tower_up": dict(pos=(3.6, FY + 8.6, 3.6), look=(-4.0, FY + 8.0, -2.0), fov=84, exposure=1.35,
                         label="星見の塔の部屋 (tower): the observatory"),
        "tower_view": dict(pos=(5.0, FY + 2.6, 2.6), look=(40.0, 2.0, 22.0), fov=72, exposure=1.2,
                           label="星見の塔の部屋 (tower): over the roofs"),
        "tower_out": dict(pos=(-34.0, 34.0, 30.0), look=(0.0, FY + 6.0, 0.0), fov=60,
                          label="The star tower over the town"),
    },
    ("room", "grand"): {
        "grand_salon": dict(pos=(6.4, FY + 2.6, -1.4), look=(-9.0, FY + 2.2, -1.0), fov=84, volumetric=12,
                            vol_res=3, exposure=1.3, label="貴賓のスイート (grand): the salon"),
        "grand_bed": dict(pos=(-1.0, FY + 2.6, 4.4), look=(4.0, FY + 2.0, 12.0), fov=84, exposure=1.35,
                          label="貴賓のスイート (grand): the bedchamber"),
        "grand_armoury": dict(pos=(0.0, FY + 2.6, -8.2), look=(-6.0, FY + 2.0, -13.0), fov=84, exposure=1.35,
                              label="貴賓のスイート (grand): the armoury"),
        "grand_out": dict(pos=(-32.0, 18.0, 4.0), look=(-8.0, FY + 3.0, -1.0), fov=62,
                          label="The grand suite's balcony over the canal"),
    },
}


# The picture the innkeeper shows of each room (textures/gui/inn/<design>.png, 228x96, see InnScreen).
THUMBNAILS = {
    ("room", "garret"): "garret_in",
    ("room", "canal"): "canal_in",
    ("room", "library"): "library_in",
    ("room", "tower"): "tower_out",
    ("room", "grand"): "grand_out",
}
THUMB_SIZE = (228, 96)
GUI_DIR = os.path.join(HERE, "..", "..", "src", "main", "resources", "assets", "eternalrealm", "textures", "gui",
                       "inn")


def thumbnail(w, kind, design):
    """Renders the room's chosen view and writes the innkeeper's picture of it: cropped to the card, scaled down
    smoothly, warmed a little like an old print, with a soft dark edge."""
    name = THUMBNAILS.get((kind, design))
    if name is None:
        return
    render(w, kind, design, names=[name], scale=1.0)
    from PIL import Image, ImageEnhance, ImageFilter
    img = Image.open(os.path.join(OUT_DIR, name + ".png")).convert("RGB")
    width, height = img.size
    height -= 22                                           # the label strip under every preview
    tw, th = THUMB_SIZE
    crop_h = int(width * th / tw)
    top = max(0, (height - crop_h) // 2)
    img = img.crop((0, top, width, top + crop_h)).resize(THUMB_SIZE, Image.LANCZOS)
    img = ImageEnhance.Color(img).enhance(1.08)
    img = img.filter(ImageFilter.UnsharpMask(radius=1.2, percent=60, threshold=2))
    px = img.load()
    for y in range(th):
        for x in range(tw):
            edge = min(x, y, tw - 1 - x, th - 1 - y)
            k = 1.0 if edge >= 6 else 0.72 + 0.28 * edge / 6.0
            r, g, b = px[x, y]
            px[x, y] = (min(255, int(r * k * 1.03)), min(255, int(g * k * 0.99)), min(255, int(b * k * 0.92)))
    os.makedirs(GUI_DIR, exist_ok=True)
    img.save(os.path.join(GUI_DIR, design + ".png"))


def render(w, kind, design, names=None, scale=1.0):
    views = VIEWS.get((kind, design), {})
    chosen = [n for n in (names or views) if n in views]
    if not chosen:
        return
    os.makedirs(OUT_DIR, exist_ok=True)
    old = P.OUT_DIR
    P.OUT_DIR = OUT_DIR
    try:
        scene = P.Scene(w)
        for name in chosen:
            kw = dict(views[name])
            kw.setdefault("sun", P.sun_dir(*SUN))
            kw.setdefault("fog", 600)
            P.render(scene, name, scale=scale, **kw)
    finally:
        P.OUT_DIR = old


from . import blocks_ext  # noqa: E402,F401  (the estate blocks' preview colours)
