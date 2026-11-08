"""Master plan of the royal capital 王都アルセリア: anchors, roads, district regions and default spots.

Every district region a later builder fills is listed in ``DISTRICTS`` (union of boxes, minus optional
circles). Coordinates are capital-local (x east, z south, y up; plaza floor top at y = 0).
"""
from __future__ import annotations

from dataclasses import dataclass, field

import numpy as np

from . import world as W

# ---------------------------------------------------------------------------------------------- anchors
GROUND_Y = 0
PLAZA_R = 22            # paved plaza proper
PLAZA_RING_R = 26       # plaza + ring road (the plaza region)
FOUNTAIN_R = 10         # reserved for the fountain / angel statue

PLATEAU_Y = 26          # castle plateau surface (top block)
PLATEAU = dict(cx=0.0, cz=-139.0, a=106.0, b=51.0, n=3.0)   # superellipse of the plateau top
TERRACE_Y = 32          # raised inner ward of the castle (palace floor top)

VIADUCT = dict(x0=-6, x1=6, z0=-47, z1=-89)   # grand stair / viaduct from the north avenue to the gate
GATE_Z = -89            # outer face of the castle gatehouse (top of the cliff)

LAKE = dict(cx=28.0, cz=126.0, a=124.0, b=33.0, n=4.0, water_y=-2)   # harbour lake (water top block y=-2)
RIVER_PTS = [(127, -86), (124, -62), (129, -34), (126, -8), (122, 22), (127, 52), (125, 80), (124, 100)]
RIVER_HALF = 4.0
WATER_Y = -2
WATERFALL = dict(x0=118, x1=133, top_y=61, lip_z=-106, pool=(126, -96), pool_r=11)
MOUNTAIN = dict(cx=136.0, cz=-150.0)

FOOTPRINT_CORNER_R = 40  # rounded footprint corners (outside = void)
EDGE_BAND = 16           # keep this band at y = 0


@dataclass
class Road:
    name: str
    pts: list
    width: int            # total width including kerbs
    kind: str = "street"  # avenue | street | lane | bridge
    y: int = 0
    kerb: bool = True


ROADS = [
    Road("avenue_north", [(0, -26), (0, -47)], 11, "avenue"),
    Road("avenue_south", [(0, 26), (0, 93)], 11, "avenue"),
    Road("road_west", [(-26, 0), (-108, 0)], 11, "avenue"),
    Road("road_west_out", [(-108, 0), (-176, 0)], 7, "street"),
    Road("road_east", [(26, 0), (108, 0)], 11, "avenue"),
    Road("road_east_out", [(108, 0), (142, 0), (176, 26)], 7, "street"),
    Road("road_south_out", [(0, 158), (0, 176)], 7, "street"),
    Road("street_n1", [(-95, -43), (95, -43)], 7, "street"),
    Road("street_s1", [(-95, 43), (95, 43)], 7, "street"),
    Road("street_w1", [(-43, -84), (-43, 92)], 7, "street"),
    Road("street_e1", [(43, -84), (43, 92)], 7, "street"),
]
LAKE_BRIDGE = dict(x0=-4, x1=4, z0=90, z1=160, deck_y=4)
RIVER_BRIDGE = dict(z0=-4, z1=4, x0=112, x1=140, deck_y=3)


@dataclass
class District:
    name: str
    boxes: list                     # [(x0, z0, x1, z1), ...] inclusive
    ground_y: int = 0
    owner: str = "free"             # "capital" = built by the framework, "free" = for a later builder
    title: str = ""
    notes: str = ""
    exclude_circles: list = field(default_factory=list)   # [(cx, cz, r)] removed from the boxes
    circle: tuple | None = None     # (cx, cz, r): region is this disc (intersected with boxes)

    def bbox(self):
        xs0 = min(b[0] for b in self.boxes)
        zs0 = min(b[1] for b in self.boxes)
        xs1 = max(b[2] for b in self.boxes)
        zs1 = max(b[3] for b in self.boxes)
        return xs0, zs0, xs1, zs1

    def mask(self) -> np.ndarray:
        """(NX, NZ) boolean column mask in world-array space."""
        m = np.zeros((W.NX, W.NZ), bool)
        for (x0, z0, x1, z1) in self.boxes:
            m[max(x0, W.X0) - W.X0:min(x1, W.X1) - W.X0 + 1, max(z0, W.Z0) - W.Z0:min(z1, W.Z1) - W.Z0 + 1] = True
        X, Z = np.meshgrid(np.arange(W.X0, W.X1 + 1), np.arange(W.Z0, W.Z1 + 1), indexing="ij")
        for (cx, cz, r) in self.exclude_circles:
            m &= (X - cx) ** 2 + (Z - cz) ** 2 > r * r
        if self.circle:
            cx, cz, r = self.circle
            m &= (X - cx) ** 2 + (Z - cz) ** 2 <= r * r
        return m

    def contains(self, x, z) -> bool:
        if not any(b[0] <= x <= b[2] and b[1] <= z <= b[3] for b in self.boxes):
            return False
        if any((x - cx) ** 2 + (z - cz) ** 2 <= r * r for (cx, cz, r) in self.exclude_circles):
            return False
        if self.circle:
            cx, cz, r = self.circle
            return (x - cx) ** 2 + (z - cz) ** 2 <= r * r
        return True


_PL = [(0, 0, PLAZA_RING_R)]
DISTRICTS: dict[str, District] = {d.name: d for d in [
    District("plaza", [(-26, -26, 26, 26)], circle=(0, 0, PLAZA_RING_R), title="中央広場 Central Plaza",
             notes="Paved disc r<=26 (ring road 22..26). Fountain with the angel statue reserved at r<=10; "
                   "spawn just south of it (0, 1, 13)."),
    District("guild", [(6, -39, 39, -6)], exclude_circles=_PL, title="冒険者ギルド Adventurers' Guild",
             notes="Guild hall with the clock tower facing the plaza (north-east of it); Gustav, Mina, Marta."),
    District("shops_nw", [(-39, -39, -6, -6)], exclude_circles=_PL, title="商店 (北西) Shops NW",
             notes="Shop ring around the plaza (item shop, ...)."),
    District("shops_sw", [(-39, 6, -6, 39)], exclude_circles=_PL, title="商店 (南西) Shops SW",
             notes="Shop ring (blacksmith Goran, ...)."),
    District("shops_se", [(6, 6, 39, 39)], exclude_circles=_PL, title="商店 (南東) Shops SE",
             notes="Shop ring (auction house, tavern, ...)."),
    District("upper_town_w", [(-39, -79, -10, -47)], title="城下 (西) Upper town W",
             notes="Foot of the castle cliff west of the grand viaduct (noble houses, gardens). The cliff "
                   "rises north of z~-80."),
    District("upper_town_e", [(10, -79, 39, -47)], title="城下 (東) Upper town E",
             notes="Foot of the castle cliff east of the grand viaduct."),
    District("cathedral", [(-95, -86, -47, -47)], title="大聖堂 Cathedral",
             notes="Cathedral with stained glass (north-west). Cliff/hill rises at the north edge."),
    District("northeast_quarter", [(47, -86, 95, -47)], title="北東区 North-east quarter",
             notes="Hall of fame / job guild / civic buildings."),
    District("residential_west", [(-95, -39, -47, -6)], title="住宅街 (西) Residential W",
             notes="Half-timbered houses."),
    District("market", [(-95, 6, -47, 39)], title="市場 Market", notes="Market square and stalls (south-west)."),
    District("residential_southwest", [(-95, 47, -47, 88)], title="住宅街 (南西) Residential SW",
             notes="Houses/inn towards the lake."),
    District("residential_east", [(47, -39, 95, -6)], title="住宅街 (東) Residential E",
             notes="Half-timbered houses."),
    District("residential_southeast", [(47, 6, 95, 39)], title="住宅街 (南東) Residential SE",
             notes="Houses, inn."),
    District("arena", [(47, 47, 95, 88)], title="闘技場 Arena", notes="Arena (Zain)."),
    District("harbour_west", [(-39, 47, -6, 124)], title="港町 (西) Harbour W",
             notes="Quays on the lake (water top y=-2, lake shore z~93). Piers may extend to z=124."),
    District("harbour_east", [(6, 47, 39, 124)], title="港町 (東) Harbour E", notes="Quays and piers."),
    District("outer_walls", [(-108, -86, -97, 96), (97, -86, 108, 96)], title="外壁 Outer walls",
             notes="Low outer walls with gatehouses on the west and east roads (z=-5..5) and towers at the "
                   "lake shore. The roads through the gates are already drawn."),
    District("west_fields", [(-160, -70, -112, 150)], title="西の畑 West fields",
             notes="Farms, windmill, orchards outside the west gate (road at z=-3..3)."),
    District("east_riverside", [(134, -60, 160, 92)], title="東の川辺 East riverside",
             notes="Fields east of the river (the river itself, x~118..134, is terrain)."),
    District("castle", [(-112, -208, 112, -87), (-9, -88, 9, -46)], ground_y=PLATEAU_Y, owner="capital",
             title="王城 Royal castle", notes="Castle plateau, castle, viaduct (built by the framework)."),
]}

# Provisional spots (feet positions; later builders override inside their districts).
DEFAULT_SPOTS = {
    "spawn": ((0, 1, 13), 180.0),
    "npc.quest_board": ((20, 1, -22), 45.0),
    "npc.guild_registrar": ((28, 1, -28), 45.0),
    "npc.job_master": ((34, 1, -12), 90.0),
    "npc.item_shop": ((-22, 1, -22), -45.0),
    "npc.auction": ((22, 1, 22), 135.0),
    "npc.blacksmith": ((-22, 1, 22), -135.0),
    "npc.mailbox": ((10, 1, 18), 180.0),
    "npc.gatekeeper": ((90, 1, 9), -90.0),
    "npc.arena_master": ((56, 1, 50), 180.0),
    "npc.hall_of_fame": ((60, 1, -60), 180.0),
    "story.gustav": ((24, 1, -32), 180.0),
    "story.laszlo": ((32, 1, -32), 180.0),
    "story.wolfram": ((36, 1, -36), 180.0),
}
REQUIRED_SPOTS = [
    "spawn", "npc.quest_board", "npc.item_shop", "npc.blacksmith", "npc.job_master", "npc.auction",
    "npc.mailbox", "npc.guild_registrar", "npc.gatekeeper", "npc.arena_master", "npc.hall_of_fame",
    "story.lilia", "story.nox", "story.arcelia_knight_captain", "story.gustav", "story.laszlo", "story.wolfram",
]


def footprint_mask() -> np.ndarray:
    """(NX, NZ) mask of owned columns: the footprint box with rounded corners."""
    X, Z = np.meshgrid(np.arange(W.X0, W.X1 + 1), np.arange(W.Z0, W.Z1 + 1), indexing="ij")
    r = FOOTPRINT_CORNER_R
    cx = np.clip(X, W.X0 + r, W.X1 - r)
    cz = np.clip(Z, W.Z0 + r, W.Z1 - r)
    return (X - cx) ** 2 + (Z - cz) ** 2 <= r * r


def edge_distance() -> np.ndarray:
    """(NX, NZ) distance (blocks) from each column to the footprint edge (rounded-corner aware)."""
    X, Z = np.meshgrid(np.arange(W.X0, W.X1 + 1), np.arange(W.Z0, W.Z1 + 1), indexing="ij")
    r = FOOTPRINT_CORNER_R
    cx = np.clip(X, W.X0 + r, W.X1 - r)
    cz = np.clip(Z, W.Z0 + r, W.Z1 - r)
    d_corner = r - np.sqrt((X - cx) ** 2 + (Z - cz) ** 2)
    d_box = np.minimum.reduce([X - W.X0, W.X1 - X, Z - W.Z0, W.Z1 - Z]).astype(float)
    corner = (np.abs(X - cx) > 0) & (np.abs(Z - cz) > 0)
    return np.where(corner, d_corner, d_box)


def plateau_distance(X, Z, jitter=None):
    """Signed distance-like field to the plateau rim (positive inside)."""
    p = PLATEAU
    dx = np.abs(X - p["cx"])
    dz = np.abs(Z - p["cz"])
    a, b, n = p["a"], p["b"], p["n"]
    if jitter is not None:
        a = a + jitter
        b = b + jitter * 0.6
    f = (dx / a) ** n + (dz / b) ** n
    g = np.power(np.maximum(f, 1e-9), 1.0 / n)
    rho = np.sqrt(dx * dx + dz * dz)
    return rho * (1.0 / g - 1.0)


def lake_distance(X, Z, jitter=None):
    p = LAKE
    a, b, n = p["a"], p["b"], p["n"]
    if jitter is not None:
        b = b + jitter
    dx = np.abs(X - p["cx"])
    dz = np.abs(Z - p["cz"])
    f = (dx / a) ** n + (dz / b) ** n
    g = np.power(np.maximum(f, 1e-9), 1.0 / n)
    rho = np.sqrt(dx * dx + dz * dz)
    return rho * (1.0 / g - 1.0)


def district(name: str) -> District:
    return DISTRICTS[name]


def district_at(x, z):
    for d in DISTRICTS.values():
        if d.contains(x, z):
            return d.name
    return None
