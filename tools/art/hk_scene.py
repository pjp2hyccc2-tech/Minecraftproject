#!/usr/bin/env python3
"""In-context previews of コーポ白樺 203 (not shipped): the rooms of DESIGN §2 built from the surface textures and the
prop models (local coordinates: x = X - 22, y = Y - 70, z = Z; floor tops at y 0, ceiling blocks at y 3), rendered in
perspective in neutral light and at night lit only by the apartment's own light sources (the TV, the street lamp
through the curtains, the laptop), plus the GUI overlays composited over the views they belong to.

usage: python3 tools/art/hk_scene.py [scene ...]   -> tools/art/preview/scene_<name>.png
"""
import math
import os
import sys
sys.dont_write_bytecode = True
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import numpy as np
from PIL import Image

import render3d as R  # noqa: E402
import hk_render as RR  # noqa: E402
import hk_modules  # noqa: E402,F401
from common import PREVIEW, TEX_ROOT  # noqa: E402

FACES = ('north', 'south', 'east', 'west', 'up', 'down')
NB = {'north': (0, 0, -1), 'south': (0, 0, 1), 'east': (1, 0, 0), 'west': (-1, 0, 0), 'up': (0, 1, 0), 'down': (0, -1, 0)}
H = 'hitori:block/'
ROT = {'north': 0, 'east': 90, 'south': 180, 'west': 270}


class Scene:
    def __init__(self):
        self.cubes = {}     # (x, y, z) -> (dict face -> tex ref, mode)
        self.models = []    # (name, x, y, z, facing)
        self.extra = []     # (model dict, matrix)
        self.lights = []

    def cube(self, x, y, z, tex, mode='cutout', **faces):
        d = {f: tex for f in FACES}
        d.update(faces)
        self.cubes[(x, y, z)] = (d, mode)

    def fill(self, x0, y0, z0, x1, y1, z1, tex, **faces):
        for x in range(min(x0, x1), max(x0, x1) + 1):
            for y in range(min(y0, y1), max(y0, y1) + 1):
                for z in range(min(z0, z1), max(z0, z1) + 1):
                    self.cube(x, y, z, tex, **faces)

    def model(self, name, x, y, z, facing='north'):
        self.models.append((name, x, y, z, facing))

    def light(self, pos, color, intensity, radius):
        self.lights.append((pos, color, intensity, radius))

    def render(self, eye, target, w=880, h=495, fov=70, night=False, ambient=(0.03, 0.034, 0.045), exposure=1.0,
               bg=(10, 11, 14), fog=None):
        cv = RR.LitCanvas(w, h, R.look_at(eye, target), fov=fov, bg=bg)
        solid = {p for p, (d, mode) in self.cubes.items() if mode == 'cutout'}
        for (x, y, z), (d, mode) in self.cubes.items():
            for f in FACES:
                dx, dy, dz = NB[f]
                if (x + dx, y + dy, z + dz) in solid and mode == 'cutout':
                    continue
                t = d.get(f)
                if t is None:
                    continue
                pts = np.array(R._face_corners((0, 0, 0), (16, 16, 16), f), float) / 16 + [x, y, z]
                uv = R._uv_corners(R._default_uv((0, 0, 0), (16, 16, 16), f))
                cv.quad(pts, uv, RR.tex(t), shade=R.FACE_SHADE[f], mode=mode, double=False)
        for name, x, y, z, facing in self.models:
            RR.draw_model(cv, RR.model_json(name), RR.place(x, y, z, facing))
        for m, M in self.extra:
            RR.draw_model(cv, m, M)
        cv.finish()
        if night:
            cv.light(self.lights, ambient=ambient, exposure=exposure, fog=fog)
        return np.clip(cv.color, 0, 255)


def place_deg(x, y, z, deg):
    a = -math.radians(deg)
    c = np.array([0.5, 0.5, 0.5])
    return R.affine(None, [x, y, z]) @ R.affine(None, c) @ R.affine(R.rot_y(a)) @ R.affine(None, -c)


def vanilla_door(tex, hinge='left'):
    """minecraft:block/door_bottom_left / _right (closed): panel at x 0..3 at rest (facing east)."""
    t = 'hitori:block/' + tex
    left = hinge == 'left'
    return {'textures': {'b': t, 'particle': t}, 'render_type': 'minecraft:translucent' if (
        'bath_door' in tex or 'balcony' in tex) else 'minecraft:cutout', 'elements': [{
            'from': [0, 0, 0], 'to': [3, 16, 16],
            'faces': {'down': {'uv': [13, 0, 16, 16], 'texture': '#b'}, 'up': {'uv': [13, 0, 16, 16], 'texture': '#b'},
                      'north': {'uv': [3, 0, 0, 16], 'texture': '#b'}, 'south': {'uv': [0, 0, 3, 16], 'texture': '#b'},
                      'west': {'uv': [0, 0, 16, 16] if left else [16, 0, 0, 16], 'texture': '#b'},
                      'east': {'uv': [16, 0, 0, 16] if left else [0, 0, 16, 16], 'texture': '#b'}}}]}


def door(S, name, x, y, z, facing, hinge='left'):
    """A closed vanilla-style door (blockstate y rotation of gen_assets: east 0, south 90, west 180, north 270)."""
    deg = {'east': 0, 'south': 90, 'west': 180, 'north': 270}[facing]
    for k, half in enumerate(('bottom', 'top')):
        S.extra.append((vanilla_door(f'{name}_{half}', hinge), place_deg(x, y + k, z, deg)))


def stairs_model(tex):
    t = 'hitori:block/' + tex
    return {'textures': {'t': t, 'particle': t}, 'elements': [
        {'from': [0, 0, 0], 'to': [16, 8, 16], 'faces': {f: {'texture': '#t'} for f in FACES}},
        {'from': [8, 8, 0], 'to': [16, 16, 16], 'faces': {f: {'texture': '#t'} for f in FACES}}]}


def slab_model(tex, top=False):
    t = 'hitori:block/' + tex
    y0, y1 = (8, 16) if top else (0, 8)
    return {'textures': {'t': t, 'particle': t}, 'elements': [
        {'from': [0, y0, 0], 'to': [16, y1, 16], 'faces': {f: {'texture': '#t'} for f in FACES}}]}


def save(arr, name):
    os.makedirs(PREVIEW, exist_ok=True)
    p = os.path.join(PREVIEW, name + '.png')
    Image.fromarray(arr.astype(np.uint8)).save(p)
    return p


def pair(a, b, name):
    return save(np.concatenate([a, b], axis=1), name)


def overlay(img, gui_name, rect=None):
    """Composite a GUI texture (stretched, nearest) over a rendered view."""
    g = Image.open(os.path.join(TEX_ROOT, 'gui', gui_name + '.png')).convert('RGBA')
    h, w = img.shape[:2]
    if rect is None:
        rect = (0, 0, w, h)
    x0, y0, x1, y1 = rect
    g = np.asarray(g.resize((x1 - x0, y1 - y0), Image.NEAREST), np.float32)
    out = img.astype(np.float32).copy()
    a = g[..., 3:4] / 255.0
    out[y0:y1, x0:x1] = out[y0:y1, x0:x1] * (1 - a) + g[..., :3] * a
    return out


# ================================================================================================ the apartment
PLAN = ['##D#########',
        '#ggghh#t#bb#',
        '#ggghhdt#bb#',
        '####hh###d##',
        '#kkkhh#wwww#',
        '#kkkhhdwwww#',
        '#kkkhh######',
        '#kkkhhdyyyy#',
        '##ff#f#yyyy#',
        '#jjjjj#yyyy#',
        '#Ojjjj#yyyc#',
        '#Ojjjj#yyyc#',
        '#Ojjjj#yyyc#',
        '##WWW###WW##']
ROOMS = {
    'g': dict(floor='genkan_tile', wall='plaster_wall', ceil='ceiling_board', low=-1),
    'h': dict(floor='old_flooring', wall='plaster_wall', ceil='ceiling_board'),
    't': dict(floor='bath_tile_floor', wall='plaster_wall_stained', lower='bath_tile_wall', ceil='ceiling_board_stained'),
    'b': dict(floor='bath_tile_floor', wall='bath_tile_wall', ceil='ceiling_board_stained'),
    'w': dict(floor='vinyl_floor', wall='wallpaper_old', ceil='ceiling_board'),
    'k': dict(floor='vinyl_floor', wall='plaster_wall_stained', ceil='ceiling_board_stained'),
    'y': dict(floor='old_flooring', wall='wallpaper_old', lower='wood_wainscot', ceil='ceiling_board'),
    'j': dict(floor='tatami', wall='plaster_wall', ceil='ceiling_board'),
    'O': dict(floor='oshiire_floor', wall='oshiire_floor', ceil='oshiire_floor'),
    'c': dict(floor='old_flooring', wall='oshiire_floor', ceil='ceiling_board'),
}
OUTSIDE = 'apartment_siding'


def cell(x, z):
    if 0 <= z < len(PLAN) and 0 <= x < len(PLAN[0]):
        return PLAN[z][x]
    return None


def room_of(ch):
    if ch in ROOMS:
        return ROOMS[ch]
    return None


def apartment(S, x0=0, x1=11, z0=0, z1=13, ceiling=True, tatami_axis=None):
    """Floors, walls (each face takes the texture of the room it faces), ceilings of 203 within the window."""
    for z in range(z0, z1 + 1):
        for x in range(x0, x1 + 1):
            ch = cell(x, z)
            rm = room_of(ch)
            if rm is not None or ch == 'V':
                rm = rm or ROOMS['j']
                low = rm.get('low', 0)
                ft = H + rm['floor']
                S.cube(x, low - 1, z, ft)
                if low < 0:
                    # the step down: the step's riser shows flooring from the hall side
                    pass
                if ceiling:
                    S.cube(x, 3, z, H + rm['ceil'])
                continue
            # wall-ish cells: build 3 high, each face facing a room gets that room's wall texture
            openings = {'d': 2, 'D': 2, 'f': 2, 'W': 2}
            hole = openings.get(ch, 0)
            for y in range(-1, 4):
                if y >= 0 and y < hole:
                    continue
                faces = {}
                for f, (dx, _, dz) in NB.items():
                    if f in ('up', 'down'):
                        faces[f] = H + OUTSIDE if y == 3 else H + 'plaster_wall'
                        continue
                    nb = cell(x + dx, z + dz)
                    nrm = room_of(nb) or (ROOMS['j'] if nb == 'V' else None)
                    if nrm is None:
                        faces[f] = H + (OUTSIDE if nb is None else 'plaster_wall')
                    else:
                        t = nrm.get('lower') if (y == 0 and nrm.get('lower')) else nrm['wall']
                        faces[f] = H + t
                S.cube(x, y, z, H + 'plaster_wall', **faces)
    # floors under the wall openings
    for z in range(z0, z1 + 1):
        for x in range(x0, x1 + 1):
            if cell(x, z) in 'dDfW':
                S.cube(x, -1, z, H + 'old_flooring')


# ================================================================================================ scenes
def scene_tatami():
    """The tatami room seen from the balcony side: the closet on the left with its fusuma, the chabudai and cushions,
    the TV glowing on its stand (right), the fusuma to the DK at the back, the hanging lamp."""
    S = Scene()
    apartment(S, 0, 7, 7, 13)
    for z in (10, 11, 12):
        S.model('oshiire_fusuma_lower_1' if z == 11 else 'oshiire_fusuma_lower_0', 1, 0, z, 'east')
        S.model('oshiire_fusuma_upper_1' if z == 11 else 'oshiire_fusuma_upper_0', 1, 1, z, 'east')
        S.model('futon_folded', 1, 2, z, 'east')
    S.model('fusuma_lower_0', 2, 0, 8, 'north')
    S.model('fusuma_upper_0', 2, 1, 8, 'north')
    S.model('fusuma_lower_1', 3, 0, 8, 'south')
    S.model('fusuma_upper_1', 3, 1, 8, 'south')
    S.model('fusuma_lower_0', 5, 0, 8, 'north')
    S.model('fusuma_upper_0', 5, 1, 8, 'north')
    S.model('tv_stand', 5, 0, 9, 'west')
    S.model('crt_tv_on_0', 5, 1, 9, 'west')
    S.model('chabudai', 3, 0, 10, 'north')
    S.model('zabuton', 3, 0, 11, 'north')
    S.model('zabuton', 2, 0, 10, 'east')
    S.model('altar_shelf', 5, 0, 12, 'west')
    S.model('hanging_lamp', 3, 2, 10, 'north')
    for x in (2, 3, 4):
        S.model('curtain_closed', x, 0, 12, 'north')
        S.model('curtain_closed', x, 1, 12, 'north')
    S.model('light_switch', 4, 1, 9, 'south') if False else None
    for x in (2, 3, 4):
        door(S, 'balcony_door', x, 0, 13, 'north')
    S.light((4.9, 0.85, 9.5), (0.55, 0.72, 1.0), 2.4, 1.8)            # the TV screen
    S.light((3.0, 1.0, 14.5), (1.0, 0.6, 0.28), 1.1, 3.0)            # sodium light through the curtains
    eye = (4.55, 1.55, 12.45)
    tgt = (1.9, 0.55, 9.0)
    day = S.render(eye, tgt)
    night = S.render(eye, tgt, night=True, exposure=1.35)
    return pair(day, night, 'scene_tatami')


def scene_peek():
    """From inside the 押し入れ, crouched: the view through the cracked fusuma toward the TV (the fusuma_slit overlay
    over the night render), and the same view without the overlay."""
    S = Scene()
    apartment(S, 0, 7, 7, 13)
    S.model('tv_stand', 5, 0, 9, 'west')
    S.model('crt_tv_on_0', 5, 1, 9, 'west')
    S.model('chabudai', 3, 0, 10, 'north')
    S.model('zabuton', 3, 0, 11, 'north')
    S.model('fusuma_lower_0', 2, 0, 8, 'north')
    S.model('fusuma_upper_0', 2, 1, 8, 'north')
    S.model('fusuma_lower_0', 3, 0, 8, 'south')
    S.model('fusuma_upper_0', 3, 1, 8, 'south')
    S.model('hanging_lamp', 3, 2, 10, 'north')
    S.light((4.9, 0.85, 9.5), (0.55, 0.72, 1.0), 2.4, 1.8)
    S.light((3.0, 1.0, 14.5), (1.0, 0.6, 0.28), 0.8, 3.0)
    eye = (1.45, 1.05, 11.2)
    tgt = (5.0, 0.75, 9.6)
    night = S.render(eye, tgt, night=True, exposure=1.4, fov=70)
    peek = overlay(night, 'fusuma_slit')
    return pair(night, peek, 'scene_peek')


def scene_closet():
    """Inside the closet looking up/out: the futon edge above, the box, the cracked fusuma's light, with the
    closet_dark vignette composited."""
    S = Scene()
    apartment(S, 0, 6, 8, 13)
    for z in (10, 11, 12):
        S.model('oshiire_fusuma_lower_1' if z == 11 else 'oshiire_fusuma_lower_0', 1, 0, z, 'east')
        S.model('oshiire_fusuma_upper_1' if z == 11 else 'oshiire_fusuma_upper_0', 1, 1, z, 'east')
        S.model('futon_folded', 1, 2, z, 'east')
    S.model('cardboard_box', 1, 0, 12, 'east')
    S.model('tv_stand', 5, 0, 9, 'west')
    S.model('crt_tv_on_0', 5, 1, 9, 'west')
    S.light((1.95, 1.0, 11.0), (0.5, 0.62, 0.85), 0.5, 0.45)       # the slit of TV light at the crack
    eye = (1.3, 1.0, 10.15)
    tgt = (1.9, 1.15, 11.6)
    day = S.render(eye, tgt, fov=80)
    night = S.render(eye, tgt, night=True, exposure=1.6, fov=80)
    return pair(day, overlay(night, 'closet_dark'), 'scene_closet')


def scene_bath():
    """The bathroom from the washroom doorway: the deep tiled tub full of dark water (lid half rolled), the tap and
    shower above it, stool and basin, the frosted window with rain."""
    S = Scene()
    apartment(S, 6, 11, 0, 6)
    S.model('bathtub_head_2', 10, 0, 1, 'east')
    S.model('bathtub_foot_2', 9, 0, 1, 'east')
    S.model('bath_tap_running', 10, 1, 1, 'west') if False else S.model('bath_tap', 10, 1, 1, 'north')
    S.model('shower', 9, 1, 1, 'north')
    S.model('bath_stool', 9, 0, 2, 'north')
    S.model('bucket', 10, 0, 2, 'north')
    S.cube(11, 1, 1, H + 'bath_tile_wall', west=None)
    S.model('rain_window', 11, 1, 1, 'west')
    S.model('washing_machine', 7, 0, 4, 'south')
    S.model('washbasin', 8, 0, 4, 'south')
    S.model('mirror_small', 8, 1, 4, 'south')
    S.light((10.5, 2.6, 1.5), (0.85, 0.9, 1.0), 0.7, 2.0)
    S.light((12.0, 1.5, 1.5), (1.0, 0.62, 0.3), 0.6, 2.0)
    eye = (9.35, 1.62, 3.35)
    tgt = (10.3, 0.25, 1.0)
    day = S.render(eye, tgt, fov=84)
    night = S.render(eye, tgt, night=True, exposure=1.6, fov=84)
    eye2 = (8.25, 1.6, 5.55)
    tgt2 = (10.0, 0.4, 1.4)
    wash = S.render(eye2, tgt2, fov=74)
    pair(wash, S.render(eye2, tgt2, night=True, exposure=1.6, fov=74), 'scene_washroom')
    return pair(day, night, 'scene_bath')


def scene_kitchen():
    """The DK: fridge (microwave on top), sink and gas stove along the north wall, the drawer cabinet with the rice
    cooker, rice bag, the gingham table and chairs."""
    S = Scene()
    apartment(S, 0, 6, 3, 9)
    S.model('fridge_old', 1, 0, 4, 'south')
    S.model('microwave', 1, 1, 4, 'south')
    S.model('kitchen_sink', 2, 0, 4, 'south')
    S.model('gas_stove_lit', 3, 0, 4, 'south')
    S.model('knife_rack_has_knife', 2, 1, 4, 'south')
    S.model('drawer', 1, 0, 5, 'east')
    S.model('rice_cooker', 1, 1, 5, 'east')
    S.model('salt_shaker', 2, 1, 4, 'south') if False else None
    S.model('cup_glass', 2, 1, 4, 'south') if False else None
    S.model('rice_bag', 1, 0, 6, 'east')
    S.model('dining_table', 2, 0, 6, 'north')
    S.model('dining_chair', 3, 0, 6, 'west')
    S.model('dining_chair', 2, 0, 7, 'north')
    S.model('fluorescent_ring', 2, 2, 5, 'north')
    S.model('pull_switch', 2, 2, 6, 'north') if False else None
    S.light((3.4, 1.0, 4.5), (0.4, 0.6, 1.0), 0.8, 1.0)        # the gas flame
    S.light((2.5, 2.6, 5.5), (0.9, 0.95, 1.0), 0.25, 3.0)       # dim spill
    eye = (3.6, 1.6, 8.0)
    tgt = (1.7, 0.7, 4.3)
    day = S.render(eye, tgt)
    night = S.render(eye, tgt, night=True, exposure=1.5)
    return pair(day, night, 'scene_kitchen')


def scene_western():
    """The western room: the desk against the east wall (laptop with the camera app, printout, sewing box, lamp),
    the chair, the colour box with the plush rabbit, the pipe bed, the camera bag, a poster."""
    S = Scene()
    apartment(S, 5, 11, 6, 13)
    S.model('desk', 10, 0, 8, 'west')
    S.model('laptop_block_open_on', 10, 1, 8, 'west')
    S.model('desk_lamp_lit', 10, 1, 9, 'west') if False else None
    S.model('desk', 10, 0, 9, 'west')
    S.model('printout', 10, 1, 9, 'west')
    S.model('sewing_box_open', 10, 1, 9, 'west') if False else None
    S.model('desk_chair', 9, 0, 8, 'east')
    S.model('shelf', 7, 0, 7, 'south')
    S.model('plush_doll_present', 7, 1, 7, 'south')
    S.model('bed_head', 7, 0, 10, 'north')
    S.model('bed_foot', 7, 0, 11, 'north')
    S.model('camera_bag', 8, 0, 9, 'south')
    S.model('poster', 6, 1, 9, 'east') if False else None
    S.model('poster', 7, 1, 7, 'south') if False else None
    S.model('desk_lamp_lit', 10, 1, 10, 'west') if False else None
    S.model('sewing_box', 10, 1, 9, 'west') if False else None
    S.model('fluorescent_ring', 8, 2, 9, 'north')
    S.light((9.55, 1.2, 8.5), (0.6, 0.8, 0.7), 1.0, 1.3)        # laptop screen
    S.light((8.5, 1.0, 14.5), (1.0, 0.6, 0.28), 0.8, 3.0)
    eye = (7.6, 1.6, 12.4)
    tgt = (10.0, 0.8, 8.0)
    day = S.render(eye, tgt)
    night = S.render(eye, tgt, night=True, exposure=1.5)
    return pair(day, night, 'scene_western')


def scene_genkan():
    """The genkan from the hall: the steel front door (peephole, mail slot), the shoe cabinet, shoes on the tiles a
    step down, the wet umbrella, the breaker panel high up, the intercom; the doors along the hall."""
    S = Scene()
    apartment(S, 0, 7, 0, 6)
    door(S, 'front_door', 2, -1, 0, 'south') if False else None
    S.extra.append((vanilla_door('front_door_inner_bottom'), place_deg(2, -1, 0, 270)))
    S.extra.append((vanilla_door('front_door_inner_top'), place_deg(2, 0, 0, 270)))
    S.model('shoe_cabinet', 1, -1, 1, 'east')
    S.model('shoes', 2, -1, 1, 'north')
    S.model('umbrella', 3, -1, 1, 'north')
    S.model('breaker_panel', 3, 2, 1, 'north') if False else None
    S.model('breaker_panel', 1, 1, 2, 'east')
    S.model('intercom', 4, 1, 1, 'east') if False else None
    S.model('calendar', 6, 1, 4, 'west') if False else None
    S.model('mirror_small', 6, 1, 4, 'west') if False else None
    door(S, 'toilet_door', 6, 0, 2, 'east')
    door(S, 'interior_door', 6, 0, 5, 'east')
    S.light((4.5, 2.6, 3.0), (1.0, 0.9, 0.75), 0.6, 3.0)
    S.light((2.5, 0.5, -0.5), (1.0, 0.62, 0.3), 0.3, 1.5)
    eye = (4.75, 1.62, 2.75)
    tgt = (2.2, -0.2, 0.4)
    day = S.render(eye, tgt, fov=74)
    night = S.render(eye, tgt, night=True, exposure=1.6, fov=74)
    return pair(day, night, 'scene_genkan')


def scene_exterior():
    """コーポ白樺 from the street at night: grey siding, the outdoor corridor on the second floor with its doors,
    nameplates and the vacancy sign on 202, the rusty stairs, mailboxes, the street lamp on its pole, the vending
    machine, wet asphalt with puddles, a block wall across the street."""
    S = Scene()
    xa, xb = -14, 14
    doors2 = {-20 + 11 * k: k for k in range(4)}            # 2F doors at local x -20, -9, 2, 13 (201..204)
    openings = set()
    for ux, k in doors2.items():
        for y in (0, 1):
            openings.add((ux, y))
        for y in (-5, -4):
            openings.add((ux, y))
        for wx in (ux + 4, ux + 5):
            openings.add((wx, 1))
            openings.add((wx, -4))
    for x in range(xa, xb + 1):
        for y in range(-6, 3):
            if (x, y) in openings:
                continue
            S.cube(x, y, 0, H + 'apartment_siding')
        S.cube(x, -6, 0, H + 'concrete')
        # the eave and roof over the corridor
        for z in range(-3, 3):
            S.cube(x, 3, z, H + 'roof_slate', down=H + 'ceiling_board_stained' if z < 0 else H + 'roof_slate')
        # 2F corridor slab with its siding parapet; the covered walkway below
        for z in (-3, -2, -1):
            S.cube(x, -1, z, H + 'corridor_concrete', down=H + 'concrete', north=H + 'concrete')
        S.cube(x, 0, -3, H + 'apartment_siding', up=H + 'concrete')
        for z in range(-5, 0):
            S.cube(x, -6, z, H + 'corridor_concrete' if z > -4 else H + 'concrete')
        for z in range(-12, -5):
            S.cube(x, -6, z, H + 'asphalt_wet')
        for y in (-5, -4):
            S.cube(x, y, -13, H + 'block_wall')
    for x in range(xa, xb + 1, 5):
        for y in range(-5, -1):
            S.cube(x, y, -3, H + 'concrete')
    for ux, k in doors2.items():
        if xa <= ux <= xb:
            door(S, 'front_door', ux, 0, 0, 'north')
            door(S, 'front_door', ux, -5, 0, 'north')
            S.model(f'nameplate_{4 + k}', ux + 1, 1, -1, 'south')
            S.model(f'nameplate_{k}', ux + 1, -4, -1, 'south')
            for wx in (ux + 4, ux + 5):
                S.model('rain_window', wx, 1, 0, 'north')
                S.model('rain_window', wx, -4, 0, 'north')
                S.cube(wx, 1, 1, H + 'plaster_wall')
                S.cube(wx, -4, 1, H + 'plaster_wall')
    S.model('vacancy_sign', -9, 0, -1, 'south')
    S.model('mailboxes', -13, -4, -1, 'south')
    S.model('mailboxes', -12, -4, -1, 'south')
    S.model('bicycle', -6, -5, -2, 'east')
    S.model('bicycle', -4, -5, -2, 'east')
    S.model('ac_outdoor_unit', 7, -5, -1, 'north')
    # stairs at the west end, rising toward the west along the corridor line, with the rusty rail on the outside
    st = stairs_model('corridor_concrete')
    for k in range(5):
        S.extra.append((st, place_deg(xa - 1 - k, -5 + k, -2, 270)))
        S.model('stairs_rail', xa - 1 - k, -5 + k, -3, 'west')
    # the utility pole across the street, its street lamp leaning over the road toward the building
    for y in range(-5, 3):
        S.model('utility_pole', 3, y, -11, 'north')
    S.model('utility_pole_top', 3, 3, -11, 'north')
    S.model('street_lamp_old_lit', 3, 2, -10, 'south')
    S.model('vending_machine_small', 10, -5, -4, 'north')
    for (px_, pz_) in ((0, -8), (1, -8), (-4, -7), (6, -9), (7, -9)):
        S.model('puddle', px_, -5, pz_, 'north')
    S.light((3.5, 1.9, -8.6), (1.0, 0.62, 0.28), 7.0, 6.0)        # sodium lamp
    S.light((10.5, -3.6, -4.8), (0.75, 0.88, 1.0), 2.6, 2.4)      # vending machine
    S.light((3.0, 2.5, -2.0), (0.85, 0.95, 1.0), 1.2, 2.6)        # the corridor's one working light
    S.light((6.5, 1.4, 0.4), (0.5, 0.7, 1.0), 0.7, 1.0)           # TV glow in 203's kitchen window
    eye = (-2.0, -3.4, -12.2)
    tgt = (2.5, -0.6, 0.0)
    day = S.render(eye, tgt, fov=78, w=960, h=540)
    night = S.render(eye, tgt, fov=78, w=960, h=540, night=True, exposure=1.7, ambient=(0.02, 0.024, 0.036),
                     fog=((0.04, 0.05, 0.07), 34.0))
    # on the corridor in front of 203, looking west toward 202 (the vacancy sign) and the stairs
    eye2 = (3.6, 1.62, -1.9)
    tgt2 = (-6.0, 0.9, -1.2)
    cday = S.render(eye2, tgt2, fov=74, w=960, h=540)
    cnight = S.render(eye2, tgt2, fov=74, w=960, h=540, night=True, exposure=1.8, ambient=(0.02, 0.024, 0.036),
                      fog=((0.04, 0.05, 0.07), 30.0))
    pair(cday, cnight, 'scene_corridor')
    return pair(day, night, 'scene_exterior')


def scene_camera_app():
    """The camera app: the bathtub seen by a fixed camera in grey-green IR (desaturated, noisy) under the cctv
    overlay, and the laptop frame with the same feed in its viewport."""
    S = Scene()
    apartment(S, 6, 11, 0, 6)
    S.model('bathtub_head_2', 10, 0, 1, 'east')
    S.model('bathtub_foot_2', 9, 0, 1, 'east')
    S.model('bath_tap', 10, 1, 1, 'north')
    S.model('shower', 9, 1, 1, 'north')
    S.model('bath_stool', 9, 0, 2, 'north')
    S.light((9.6, 2.2, 3.6), (1.0, 1.0, 1.0), 1.6, 2.5)          # the IR illuminator of the camera
    eye = (9.2, 2.6, 3.7)
    tgt = (10.0, 0.4, 1.2)
    img = S.render(eye, tgt, fov=82, night=True, exposure=1.8)
    lum = img @ np.array([0.3, 0.59, 0.11])
    rng_ = np.random.default_rng(5)
    lum = np.clip(lum * 1.15 + rng_.normal(0, 9, lum.shape), 0, 255)
    ir = np.stack([lum * 0.78, lum * 0.92, lum * 0.82], -1)
    feed = overlay(ir, 'cctv')
    lap = np.zeros_like(ir)
    lap[:] = 20
    h, w = ir.shape[:2]
    import hk_gui
    vx0, vy0, vx1, vy1 = hk_gui.RECTS['laptop_viewport']
    sx, sy = w / 512, h / 288
    small = np.asarray(Image.fromarray(ir.astype(np.uint8)).resize((int((vx1 - vx0 + 1) * sx),
                                                                     int((vy1 - vy0 + 1) * sy))), np.float32)
    lap[int(vy0 * sy):int(vy0 * sy) + small.shape[0], int(vx0 * sx):int(vx0 * sx) + small.shape[1]] = small
    lap = overlay(lap, 'laptop_frame')
    return pair(feed, lap, 'scene_camera_app')


SCENES = {'tatami': scene_tatami, 'peek': scene_peek, 'closet': scene_closet, 'bath': scene_bath,
          'kitchen': scene_kitchen, 'western': scene_western, 'genkan': scene_genkan, 'exterior': scene_exterior,
          'camera_app': scene_camera_app}


def main(args):
    out = []
    for k, fn in SCENES.items():
        if args and k not in args:
            continue
        out.append(fn())
        print(out[-1], flush=True)
    return out


if __name__ == '__main__':
    main(sys.argv[1:])
