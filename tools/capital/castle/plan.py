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
GATE = dict(z_front=-89, z_back=-99, half=7, tower_x=11, tower_z=-93, tower_r=5.5, passage=3, top=46)

# curtain wall polygon (clockwise from the gate's west side) and towers at the vertices
WALL_PTS = [(-16, -92), (-68, -97), (-95, -126), (-92, -158), (-66, -180), (-18, -187),
            (18, -187), (66, -180), (92, -158), (95, -126), (68, -97), (16, -92)]
WALL_TOP = 41
TOWERS = [  # (x, z, r, top_y, cone_h)
    (-68, -97, 6.5, 58, 30), (-95, -126, 5.5, 55, 26), (-92, -158, 5.5, 57, 27), (-66, -180, 6.5, 62, 32),
    (66, -180, 6.5, 62, 32), (92, -158, 5.5, 57, 27), (95, -126, 5.5, 55, 26), (68, -97, 6.5, 58, 30),
    (-42, -95, 4.5, 50, 20), (42, -95, 4.5, 50, 20),
]

# inner terrace (raised ward) rectangle and its front stair
TERRACE = dict(x0=-58, x1=62, z0=-110, z1=-172)
TERRACE_STAIR = dict(x0=-8, x1=8, z_bottom=-104)

# palace buildings (outer wall faces)
HALL = dict(x0=-13, x1=13, z0=-114, z1=-150, eave=62, floor=TY, win_y=39, win_h=18, spring=57, vault_k=0.55)
COURT = dict(x0=-13, x1=13, z0=-151, z1=-159)
KEEP = dict(x0=-17, x1=17, z0=-160, z1=-188, top=84)
CHAPEL = dict(x0=28, x1=62, z0=-120, z1=-136, eave=47)
LIBRARY = dict(x0=-58, x1=-30, z0=-116, z1=-146, eave=50)
OBSERVATORY = dict(x=-50, z=-155, r=6.5, top=86)
BARRACKS = dict(x0=-89, x1=-69, z0=-128, z1=-158, eave=40)
YARD = dict(x0=-96, x1=-60, z0=-108, z1=-126)
GARDEN = dict(x0=64, x1=96, z0=-108, z1=-170)

# the Hall of the Star Gate (星門の間) beneath the Star Court
STARGATE = dict(cx=0, cz=-155, r=13, floor=8, wall_top=18, dome_ry=10, ring_z=-163, ring_cy=16, ring_r=5)

# preview cameras for interiors (pos, look in world coords)
MORNING = (42.0, 12.0)     # (elevation, azimuth from east towards south) -- the sun path runs east-west
NOON = (84.0, 10.0)
AFTERNOON = (34.0, 168.0)
VIEW_GREAT_HALL = dict(pos=(0.5, 37.0, -117.0), look=(0.5, 42.0, -150.0), fov=80,
                       sun=MORNING, volumetric=20, vol_res=3, exposure=1.25,
                       label="Great hall (大広間): east/west windows, chandeliers, lantern oculus")
VIEW_THRONE = dict(pos=(0.5, 38.0, -162.5), look=(0.5, 39.0, -188.0), fov=76, sun=MORNING, volumetric=20, vol_res=3,
                   exposure=1.3, label="Throne room (玉座の間)")
VIEW_STAR_GATE = dict(pos=(0.5, 12.5, -145.0), look=(0.5, 14.5, -165.0), fov=80, sun=NOON, volumetric=24, vol_res=3,
                      exposure=1.6, label="Hall of the Star Gate (星門の間)")
VIEW_LIBRARY = dict(pos=(-34.5, 38.0, -119.5), look=(-50.0, 37.0, -140.0), fov=80, sun=AFTERNOON, volumetric=16, vol_res=3,
                    exposure=1.3, label="Royal library (王立図書館)")
VIEW_CHAPEL = dict(pos=(31.0, 38.0, -128.5), look=(62.0, 38.0, -128.5), fov=76, sun=MORNING, volumetric=16, vol_res=3,
                   exposure=1.3, label="Chapel (礼拝堂)")
VIEW_SECTION = dict(pos=(62.0, 52.0, -112.0), look=(0.0, 34.0, -150.0), fov=62, sun=NOON, fog=2400,
                    clip=[(0, 0, True)], ambient_boost=1.8, exposure=1.3,
                    label="Section through the castle at x=0 (east half removed)")
VIEW_BARRACKS = dict(pos=(-79.5, 29.5, -131.0), look=(-79.5, 28.5, -158.0), fov=80, sun=AFTERNOON, exposure=1.4,
                     label="Knights' barracks (騎士団兵舎): captain Adel's office, mess hall, armoury")
VIEW_OBSERVATORY = dict(pos=(-47.5, 79.5, -158.0), look=(-50.0, 79.0, -150.0), fov=85, sun=NOON, exposure=1.4,
                        label="Nox's observatory (天文台)")
VIEW_HALL_NORTH = dict(pos=(7.5, 39.0, -117.5), look=(-1.0, 41.0, -141.0), fov=74, sun=NOON, volumetric=20, vol_res=3,
                       exposure=1.3, label="Great hall: the high table and the oculus beam at noon")
