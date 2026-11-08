"""Template for a district builder (files starting with "_" are not run).

Copy to ``tools/capital/districts/<district>.py``, set DISTRICT to a name from ``layout.DISTRICTS`` and
implement ``build``. All writes are clipped to the district region (boxes minus excluded circles), so you can
not damage roads or neighbours; ``w.clipped_writes`` counts what was dropped (build.py logs it).
"""
from capital import blocks as B  # noqa: F401
from capital import deco, shapes  # noqa: F401
from capital.world import Frame  # noqa: F401

DISTRICT = "_example"   # e.g. "market"


def build(w, district, terrain):
    x0, z0, x1, z1 = district.bbox()
    gy = district.ground_y               # top block of the ground (players stand at gy + 1)
    # a house rotated so that its local south (+z) faces west:
    f = Frame(w, origin=(x0 + 4, gy, z0 + 4), rot=1)
    shapes.hollow_box(f, 0, 1, 0, 8, 6, 10, B.full("white_plaster"), floor=False, ceiling=False)
    shapes.gable_roof(f, 0, 0, 8, 10, 7, "pale_roof_tiles", axis="z", overhang=1, gable_state=B.full("white_plaster"))
    lo, hi = B.door_states("spruce", facing="north")
    f.set(4, 1, 10, lo)
    f.set(4, 2, 10, hi)
    deco.lamp_post(w, x0 + 2, gy, z0 + 2)
    w.spot("npc.item_shop", (x0 + 6, gy + 1, z0 + 6), 180.0)
