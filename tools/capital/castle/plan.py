"""Castle anchors (capital-local coordinates) shared by the exterior and interior builders and the previews."""
from __future__ import annotations

PY = 26          # plateau surface (top block)
TY = 32          # inner terrace / palace floor (top block)

# viaduct / grand stair: deck x -5..5, parapets x = +-6, from the north avenue (z=-47) to the gate (z=-89)
VIADUCT_X = 5
VIADUCT_FLIGHTS = [  # (z_first_step, steps, y_after) flights of stairs, then a landing until the next flight
    (-47, 6), (-57, 6), (-67, 6), (-77, 6), (-85, 2),
]
VIADUCT_TOP_Z = -87

# gatehouse
GATE = dict(z_front=-89, z_back=-102, half=7, tower_x=11, tower_z=-93, tower_r=5.5, passage=3, top=46)

# curtain wall polygon (clockwise from the gate's west side) and towers at the vertices
WALL_PTS = [(-16, -92), (-68, -97), (-95, -126), (-92, -158), (-66, -180), (-18, -187),
            (18, -187), (66, -180), (92, -158), (95, -126), (68, -97), (16, -92)]
WALL_TOP = 41
TOWERS = [  # (x, z, r, top_y, cone_h)
    (-68, -97, 6.5, 52, 22), (-95, -126, 5.5, 50, 19), (-92, -158, 5.5, 50, 19), (-66, -180, 6.5, 53, 22),
    (66, -180, 6.5, 53, 22), (92, -158, 5.5, 50, 19), (95, -126, 5.5, 50, 19), (68, -97, 6.5, 52, 22),
    (-42, -95, 4.5, 47, 15), (42, -95, 4.5, 47, 15),
]

# inner terrace (raised ward) rectangle and its front stair
TERRACE = dict(x0=-60, x1=66, z0=-108, z1=-188)
TERRACE_STAIR = dict(x0=-8, x1=8, z_bottom=-102)

# palace buildings (outer wall faces)
HALL = dict(x0=-13, x1=13, z0=-114, z1=-150, eave=55, floor=TY)
COURT = dict(x0=-13, x1=13, z0=-151, z1=-159)
KEEP = dict(x0=-17, x1=17, z0=-160, z1=-188, top=80)
CHAPEL = dict(x0=28, x1=62, z0=-120, z1=-136, eave=50)
LIBRARY = dict(x0=-58, x1=-30, z0=-116, z1=-146, eave=50)
OBSERVATORY = dict(x=-50, z=-155, r=6.5, top=86)
BARRACKS = dict(x0=-89, x1=-69, z0=-128, z1=-158, eave=40)
YARD = dict(x0=-90, x1=-62, z0=-100, z1=-125)
GARDEN = dict(x0=58, x1=90, z0=-100, z1=-172)

# the Hall of the Star Gate (星門の間) beneath the Star Court
STARGATE = dict(cx=0, cz=-155, r=13, floor=8, wall_top=18, dome_ry=10, ring_z=-163, ring_cy=16, ring_r=5)

# preview cameras for interiors (pos, look in world coords)
VIEW_GREAT_HALL = dict(pos=(0.5, 38.0, -116.5), look=(0.5, 41.0, -150.0), fov=78,
                       sun=None, volumetric=20, vol_res=3, exposure=1.25,
                       label="Great hall (大広間): east/west windows, chandeliers, lantern oculus")
VIEW_THRONE = dict(pos=(0.5, 38.0, -162.5), look=(0.5, 39.0, -188.0), fov=76, volumetric=20, vol_res=3,
                   exposure=1.3, label="Throne room (玉座の間)")
VIEW_STAR_GATE = dict(pos=(0.5, 12.5, -144.0), look=(0.5, 14.0, -165.0), fov=80, volumetric=20, vol_res=3,
                      exposure=1.6, label="Hall of the Star Gate (星門の間)")
VIEW_LIBRARY = dict(pos=(-31.5, 39.0, -118.5), look=(-50.0, 37.0, -140.0), fov=80, volumetric=16, vol_res=3,
                    exposure=1.3, label="Royal library (王立図書館)")
VIEW_CHAPEL = dict(pos=(31.0, 38.0, -128.5), look=(62.0, 38.0, -128.5), fov=76, volumetric=16, vol_res=3,
                   exposure=1.3, label="Chapel (礼拝堂)")
VIEW_SECTION = dict(pos=(70.0, 70.0, -40.0), look=(0.0, 40.0, -140.0), fov=55,
                    clip=[(0, 0, True)], ambient_boost=1.6,
                    label="Section through the castle at x=0 (east half removed)")
