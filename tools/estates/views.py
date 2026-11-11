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
}


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


def _preview_colours():
    """Colours of the estate blocks for the previews (the capital's table does not know them)."""
    from capital import colors
    colors.BASE.setdefault("room_door", (122, 70, 42))
    colors.BASE.setdefault("ledger_desk", (96, 62, 38))


_preview_colours()
