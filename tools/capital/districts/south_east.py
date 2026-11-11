"""宿屋街 (南東) The inn quarter on the Grand Canal: the Starlight Manor (星灯りの館, the inn mansion of the player
houses) facing the canal on the axis of the covered bridge, canal-front houses either side of it with alleys
behind, and the back lane under the south wall with the passage to the harbour postern."""
from __future__ import annotations

from capital import blocks as B
from capital import deco

from . import _civic_kit as K
from . import _mansion as MN
from . import _maze as MZ
from . import _town_kit as TK

DISTRICT = "south_east"
MANSION = (26, 52)          # west wall x, front (north) wall z: x 26..61, z 52..73

VIEWS = {
    "manor": dict(pos=(44.5, 7.0, 30.5), look=(44, 14, 60), fov=74, sun=(40, -150), fog=2600,
                  label="The Starlight Manor across the Grand Canal"),
    "manor_lobby": dict(pos=(37.5, 3.6, 57.5), look=(46, 9, 66), fov=84, sun=(40, -150), fog=900,
                        label="The manor's atrium: reception, galleries and the room doors"),
}


def build(w, district, terrain):
    TK.pave(w, 6, 48, 98, 73, TK.LANE_MIX, seed=601)
    TK.pave(w, 6, 48, 98, 49, TK.QUAY_MIX, seed=602, only_ground=False)
    x0, z0 = MANSION
    # forecourt between the quay and the portico: flagstones, flower beds, lamps
    TK.pave(w, x0, 48, x0 + 35, z0 - 1, TK.SQUARE_MIX, seed=603, only_ground=False)
    for x in (x0 + 2, x0 + 29):
        deco.flower_bed(w, x, 49, x + 4, 50, 0, seed=x)
    for x in (x0 + 9, x0 + 26):
        K.lamp(w, x, 0, 50, height=4)
    MN.inn_mansion(w, x0, z0)
    # canal-front houses west of the manor, the lanes of the east block, the manor's side lanes
    TK.row(w, 6, 48, 24, 57, "north", seed=611, min_w=6, max_w=8, alley_every=2)
    TK.row(w, 6, 58, 24, 65, "south", seed=615, min_w=6, max_w=8, inset=0, end_gap=(0, 0))
    MZ.quarter(w, (63, 48, 98, 73), seed=71, a1=73, a2=80, b=60, bridges=3,
               inset={"north": 2, "south": 2, "west": 1, "east": 1})
    TK.crowd(w, "inn", [(44, 1, 49), (36, 1, 62), (52, 1, 62)], need=10)
    TK.crowd(w, "residential", [(74, 1, 61), (90, 1, 61)], start=4, need=10)
