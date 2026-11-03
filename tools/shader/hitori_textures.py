"""Stand-in 16x16 textures for the preview of room 203 (tools/shader/apartment_scene.py) where the
art's textures do not exist (yet): tatami, old varnished planks, plaster, fusuma paper, pale blue
bath tiles, the CRT TV, curtains, the street ... Only for judging the pack's light and looks -
the art's real textures replace them automatically when $HITORI_ASSETS / the repo has them.
"""
import numpy as np

_GEN = {}


def gen(*names):
    def deco(f):
        for n in names:
            _GEN[n] = f
        return f
    return deco


def _rgb(t, c, mask=None, jitter=0.0, rng=None):
    c = np.array(c, np.float64)
    if mask is None:
        mask = np.ones(t.shape[:2], bool)
    v = c[None, None, :] * np.ones(t.shape, np.float64)
    if jitter and rng is not None:
        v = v * (1.0 - jitter + 2 * jitter * rng.random(t.shape[:2] + (1,)))
    t[mask] = v[mask]


def tile(name, rng):
    """-> 16x16x4 uint8 or None if this module has no stand-in for the name"""
    f = _GEN.get(name)
    if f is None:
        return None
    t = np.zeros((16, 16, 3), np.float64)
    a = np.full((16, 16), 255.0)
    out = f(t, a, rng, name)
    if out is not None:
        t, a = out
    res = np.zeros((16, 16, 4), np.uint8)
    res[..., :3] = np.clip(t, 0, 255).astype(np.uint8)
    res[..., 3] = np.clip(a, 0, 255).astype(np.uint8)
    return res


Y, X = np.mgrid[0:16, 0:16]


@gen("tatami", "tatami_worn")
def _tatami(t, a, rng, name):
    # yellowed rush: fine weave lines along the mat, a dark cloth edge (heri) along one side
    base = np.array([168, 150, 92]) if name == "tatami" else np.array([150, 132, 82])
    weave = 0.88 + 0.12 * ((X % 2) == 0) + rng.random((16, 16)) * 0.08
    t[:] = base[None, None] * weave[..., None]
    t[(Y % 4) == 0] *= 0.93
    edge = X <= 1
    t[edge] = np.array([46, 42, 38]) * (0.9 + 0.2 * rng.random((16, 16, 1)))[edge]
    if name == "tatami_worn":
        worn = rng.random((16, 16)) < 0.12
        t[worn] *= 0.82


@gen("old_flooring")
def _flooring(t, a, rng, name):
    # dark varnished planks, 4 px wide, staggered joints, worn lighter in places
    t[:] = np.array([70, 44, 26]) * (0.85 + 0.25 * rng.random((16, 16, 1)))
    grain = (np.sin(Y * 1.7 + (X // 4) * 2.3) * 0.5 + 0.5)[..., None]
    t *= 0.9 + 0.15 * grain
    t[(X % 4) == 3] *= 0.55
    for col in range(4):
        y0 = (col * 5 + 3) % 16
        t[y0, col * 4:col * 4 + 3] *= 0.6


@gen("vinyl_floor")
def _vinyl(t, a, rng, name):
    # beige cushion floor with a faded tile print
    t[:] = np.array([176, 160, 128]) * (0.92 + 0.10 * rng.random((16, 16, 1)))
    t[((X % 8) == 0) | ((Y % 8) == 0)] *= 0.85
    t[((X + Y) % 8) == 4] *= 0.95


@gen("genkan_tile")
def _genkan(t, a, rng, name):
    t[:] = np.array([52, 50, 50]) * (0.85 + 0.25 * rng.random((16, 16, 1)))
    t[((X % 4) == 0) | ((Y % 4) == 0)] = np.array([96, 92, 86])


@gen("bath_tile_floor", "bath_tile_wall")
def _bath(t, a, rng, name):
    # small pale blue tiles, old grey grout
    base = np.array([150, 186, 196]) if name == "bath_tile_wall" else np.array([132, 168, 178])
    t[:] = base * (0.94 + 0.08 * rng.random((16, 16, 1)))
    grout = ((X % 4) == 0) | ((Y % 4) == 0)
    t[grout] = np.array([118, 120, 114]) * (0.9 + 0.2 * rng.random((16, 16, 1)))[grout]


@gen("plaster_wall", "plaster_wall_stained")
def _plaster(t, a, rng, name):
    t[:] = np.array([176, 164, 138]) * (0.90 + 0.14 * rng.random((16, 16, 1)))
    if name.endswith("stained"):
        st = np.exp(-(((X - 9) / 4.0) ** 2 + ((Y - 11) / 5.0) ** 2))
        t *= (1.0 - 0.28 * st)[..., None]


@gen("wallpaper_old")
def _wallpaper(t, a, rng, name):
    t[:] = np.array([170, 160, 140]) * (0.93 + 0.08 * rng.random((16, 16, 1)))
    flower = (((X - 4) ** 2 + (Y - 4) ** 2) < 3) | (((X - 12) ** 2 + (Y - 12) ** 2) < 3)
    t[flower] = np.array([150, 128, 118])


@gen("wood_wainscot", "tv_stand", "chabudai", "desk", "dining_table", "shelf", "altar_shelf", "shoe_cabinet", "interior_door")
def _wood(t, a, rng, name):
    t[:] = np.array([96, 62, 36]) * (0.85 + 0.22 * rng.random((16, 16, 1)))
    t *= (0.92 + 0.10 * np.sin(Y * 0.9 + X * 0.15))[..., None]
    if name in ("tv_stand", "shoe_cabinet", "desk"):
        t[(Y == 0) | (Y == 15) | (X == 0) | (X == 15)] *= 0.6


@gen("ceiling_board", "ceiling_board_stained")
def _ceiling(t, a, rng, name):
    t[:] = np.array([128, 104, 72]) * (0.9 + 0.15 * rng.random((16, 16, 1)))
    t[(X % 8) == 0] *= 0.6
    if name.endswith("stained"):
        st = np.exp(-(((X - 6) / 5.0) ** 2 + ((Y - 7) / 4.0) ** 2))
        t *= (1.0 - 0.35 * st)[..., None]


@gen("oshiire_floor", "oshiire_shelf")
def _plywood(t, a, rng, name):
    t[:] = np.array([150, 120, 82]) * (0.88 + 0.18 * rng.random((16, 16, 1)))
    t *= (0.94 + 0.06 * np.sin(X * 0.7 + Y * 0.2))[..., None]


@gen("fusuma", "oshiire_fusuma")
def _fusuma(t, a, rng, name):
    # faded paper with a pale pattern, a dark lacquered frame, the round pull
    t[:] = np.array([196, 188, 166]) * (0.95 + 0.06 * rng.random((16, 16, 1)))
    pat = (np.sin(X * 0.9) * np.sin(Y * 0.9)) > 0.75
    t[pat] *= 0.92
    fr = (X == 0) | (X == 15)
    t[fr] = np.array([40, 30, 24])
    t[((X - 12) ** 2 + (Y - 8) ** 2) < 2] = np.array([60, 52, 40])


@gen("curtain")
def _curtain(t, a, rng, name):
    # a thin faded green-beige curtain: soft folds
    fold = 0.82 + 0.18 * (np.sin(X * 1.6) * 0.5 + 0.5)
    t[:] = np.array([128, 132, 104]) * fold[..., None] * (0.96 + 0.06 * rng.random((16, 16, 1)))


@gen("rain_window", "balcony_door", "rain_glass")
def _glass(t, a, rng, name):
    # the glass only (the frames are their own elements): faint streaks of water
    t[:] = np.array([120, 138, 150]) * (0.9 + 0.15 * rng.random((16, 16, 1)))
    a[:] = 60
    streak = (rng.random(16) < 0.25)[None, :] & (rng.random((16, 16)) < 0.7)
    t[streak] = np.array([170, 186, 196])
    a[streak] = 90
    return t, a


@gen("bath_door", "frosted_glass")
def _frosted(t, a, rng, name):
    t[:] = np.array([200, 206, 206]) * (0.96 + 0.05 * rng.random((16, 16, 1)))
    a[:] = 175
    fr = (X == 0) | (X == 15) | (Y == 0) | (Y == 15)
    t[fr] = np.array([170, 172, 176])
    a[fr] = 255
    return t, a


@gen("crt_tv_front")
def _tv_front(t, a, rng, name):
    # a dark wood-grain cabinet, the grey-glass screen with static, chrome knobs on the right
    t[:] = np.array([58, 40, 30]) * (0.85 + 0.25 * rng.random((16, 16, 1)))
    scr = (X >= 1) & (X <= 11) & (Y >= 2) & (Y <= 12)
    s = rng.random((16, 16))
    t[scr] = (np.array([150, 158, 166]) * (0.35 + 0.9 * s[..., None]))[scr]
    t[(X >= 13) & (X <= 14) & ((Y == 4) | (Y == 7))] = np.array([200, 200, 196])


@gen("crt_tv_side")
def _tv_side(t, a, rng, name):
    t[:] = np.array([58, 40, 30]) * (0.85 + 0.25 * rng.random((16, 16, 1)))


@gen("crt_tv_off_front")
def _tv_off(t, a, rng, name):
    t[:] = np.array([58, 40, 30]) * (0.85 + 0.25 * rng.random((16, 16, 1)))
    scr = (X >= 1) & (X <= 11) & (Y >= 2) & (Y <= 12)
    t[scr] = np.array([34, 40, 38])
    t[(X >= 13) & (X <= 14) & ((Y == 4) | (Y == 7))] = np.array([200, 200, 196])


@gen("zabuton")
def _zabuton(t, a, rng, name):
    t[:] = np.array([120, 46, 40]) * (0.9 + 0.15 * rng.random((16, 16, 1)))
    t[((X - 8) ** 2 + (Y - 8) ** 2) < 2] *= 0.7


@gen("futon")
def _futon(t, a, rng, name):
    t[:] = np.array([170, 168, 176]) * (0.9 + 0.12 * rng.random((16, 16, 1)))
    t[(Y % 5) == 0] = np.array([120, 90, 120])


@gen("lamp_shade")
def _shade(t, a, rng, name):
    # the hanging lamp's paper shade (lit: drawn full-bright by its model)
    t[:] = np.array([226, 214, 186]) * (0.95 + 0.06 * rng.random((16, 16, 1)))
    t[(Y % 4) == 0] *= 0.85
    t[(Y < 1) | (Y > 14)] = np.array([60, 44, 30])


@gen("lamp_cord")
def _cord(t, a, rng, name):
    t[:] = np.array([30, 28, 26])


@gen("ring_fixture")
def _ring(t, a, rng, name):
    t[:] = np.array([214, 214, 206])
    r = np.sqrt((X - 7.5) ** 2 + (Y - 7.5) ** 2)
    t[(r > 3.0) & (r < 6.0)] = np.array([246, 250, 248])


@gen("street_lamp_head")
def _slamp(t, a, rng, name):
    t[:] = np.array([82, 86, 90]) * (0.85 + 0.2 * rng.random((16, 16, 1)))
    t[(Y > 4) & (Y < 12) & (X > 3) & (X < 12)] = np.array([255, 196, 110])


@gen("utility_pole", "street_lamp_pole")
def _pole(t, a, rng, name):
    t[:] = np.array([120, 118, 112]) * (0.8 + 0.25 * rng.random((16, 16, 1)))


@gen("vending_front")
def _vending(t, a, rng, name):
    t[:] = np.array([200, 205, 210]) * (0.9 + 0.1 * rng.random((16, 16, 1)))
    win = (X > 1) & (X < 14) & (Y > 1) & (Y < 9)
    t[win] = np.array([235, 240, 248])
    cans = win & ((X % 3) != 0) & ((Y % 3) != 0)
    cols = np.array([[200, 40, 40], [40, 90, 200], [230, 200, 40], [40, 160, 80], [220, 120, 30]])
    t[cans] = cols[((X[cans] // 3) + (Y[cans] // 3)) % 5]
    t[(Y >= 11) & (Y <= 12) & (X > 3) & (X < 12)] = np.array([30, 30, 30])


@gen("vending_side")
def _vside(t, a, rng, name):
    t[:] = np.array([180, 40, 36]) * (0.9 + 0.1 * rng.random((16, 16, 1)))


@gen("asphalt_wet")
def _asphalt(t, a, rng, name):
    t[:] = np.array([46, 46, 48]) * (0.6 + 0.6 * rng.random((16, 16, 1)))


@gen("corridor_concrete", "concrete")
def _concrete(t, a, rng, name):
    t[:] = np.array([128, 126, 120]) * (0.8 + 0.3 * rng.random((16, 16, 1)))


@gen("block_wall")
def _blockwall(t, a, rng, name):
    t[:] = np.array([140, 138, 130]) * (0.82 + 0.25 * rng.random((16, 16, 1)))
    t[(Y % 8) == 0] *= 0.6
    t[((X + 8 * ((Y // 8) % 2)) % 16) == 0] *= 0.6


@gen("apartment_siding")
def _siding(t, a, rng, name):
    t[:] = np.array([126, 128, 126]) * (0.92 + 0.08 * rng.random((16, 16, 1)))
    t *= (0.86 + 0.14 * (np.sin(X * TAU_4) * 0.5 + 0.5))[..., None]


TAU_4 = 2 * np.pi / 4.0


@gen("apartment_door_metal", "front_door")
def _door(t, a, rng, name):
    t[:] = np.array([96, 110, 112]) * (0.9 + 0.12 * rng.random((16, 16, 1)))
    t[(X == 0) | (X == 15)] *= 0.7


@gen("roof_slate")
def _roof(t, a, rng, name):
    t[:] = np.array([56, 60, 66]) * (0.85 + 0.25 * rng.random((16, 16, 1)))
    t[(Y % 4) == 0] *= 0.6


@gen("puddle")
def _puddle(t, a, rng, name):
    t[:] = np.array([40, 52, 60])
    a[:] = 170
    return t, a


@gen("enamel_white", "bathtub", "washbasin", "toilet_seat", "fridge_old", "washing_machine")
def _enamel(t, a, rng, name):
    base = np.array([214, 216, 210]) if name != "fridge_old" else np.array([196, 210, 196])
    t[:] = base * (0.95 + 0.05 * rng.random((16, 16, 1)))


@gen("bath_water")
def _water(t, a, rng, name):
    t[:] = np.array([58, 86, 96]) * (0.95 + 0.08 * rng.random((16, 16, 1)))


@gen("laptop_screen")
def _lscreen(t, a, rng, name):
    t[:] = np.array([40, 42, 46])
    scr = (X >= 1) & (X <= 14) & (Y >= 1) & (Y <= 11)
    t[scr] = np.array([70, 110, 170])
    t[(X >= 2) & (X <= 6) & (Y >= 2) & (Y <= 5)] = np.array([110, 120, 112])     # a feed tile
    t[(X >= 8) & (X <= 13) & (Y >= 2) & (Y <= 5)] = np.array([96, 104, 98])
    t[(Y == 10) & scr] = np.array([210, 214, 220])


@gen("plastic_grey", "laptop_body", "intercom", "light_switch")
def _plastic(t, a, rng, name):
    t[:] = np.array([150, 148, 140]) * (0.95 + 0.06 * rng.random((16, 16, 1)))


@gen("bed")
def _bed(t, a, rng, name):
    t[:] = np.array([96, 108, 140]) * (0.9 + 0.12 * rng.random((16, 16, 1)))


@gen("cardboard_box")
def _box(t, a, rng, name):
    t[:] = np.array([150, 116, 74]) * (0.9 + 0.12 * rng.random((16, 16, 1)))
    t[7:9, :] *= 0.8


@gen("house_wall")
def _house(t, a, rng, name):
    t[:] = np.array([150, 140, 122]) * (0.88 + 0.18 * rng.random((16, 16, 1)))


@gen("house_window_lit")
def _hwin(t, a, rng, name):
    t[:] = np.array([255, 214, 150]) * (0.9 + 0.1 * rng.random((16, 16, 1)))
    t[(X == 7) | (Y == 7)] = np.array([60, 50, 40])
    t[(X == 0) | (X == 15) | (Y == 0) | (Y == 15)] = np.array([80, 70, 60])


@gen("window_frame")
def _frame(t, a, rng, name):
    # dull aluminium
    t[:] = np.array([150, 154, 156]) * (0.92 + 0.08 * rng.random((16, 16, 1)))


@gen("puddle_water")
def _pwater(t, a, rng, name):
    t[:] = np.array([49, 59, 66]) * (0.9 + 0.2 * rng.random((16, 16, 1)))
    a[:] = 150
    return t, a
