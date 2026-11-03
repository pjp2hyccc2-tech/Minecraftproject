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
                cv.quad(pts, uv, t if isinstance(t, np.ndarray) else RR.tex(t), shade=R.FACE_SHADE[f], mode=mode,
                        double=False)
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


def two_face_door(outer, inner, hinge='left', opened=False):
    """The world's front_door model (gen_assets.two_face_door): the vanilla 3 px panel with #outer on its west face
    (outside when closed) and #inner on its east face. Opened, the panel's faces swap (the blockstate turns the
    panel a quarter the other way round its hinge), so #outer goes east and #inner west."""
    left = (hinge == 'left') != opened
    ou = [0, 0, 16, 16] if left else [16, 0, 0, 16]
    iu = [16, 0, 0, 16] if left else [0, 0, 16, 16]
    w, e = ('#i', '#o') if opened else ('#o', '#i')
    to, ti = 'hitori:block/' + outer, 'hitori:block/' + inner
    return {'textures': {'o': to, 'i': ti, 'particle': to}, 'render_type': 'minecraft:cutout', 'elements': [{
        'from': [0, 0, 0], 'to': [3, 16, 16],
        'faces': {'west': {'uv': ou, 'texture': w}, 'east': {'uv': iu, 'texture': e},
                  'north': {'uv': [0, 0, 3, 16], 'texture': '#o'}, 'south': {'uv': [13, 0, 16, 16], 'texture': '#o'},
                  'up': {'uv': [0, 0, 3, 16], 'texture': '#o'}, 'down': {'uv': [13, 0, 16, 16], 'texture': '#o'}}}]}


def door(S, name, x, y, z, facing, hinge='left', opened=False):
    """A vanilla-style door (blockstate y rotation of gen_assets: east 0, south 90, west 180, north 270; opened +90
    for a left hinge, +270 for a right one). front_door has two faces (the world's two_face_door); unit_door is the
    world's door of the other flats (apartment_door_metal on both halves)."""
    deg = {'east': 0, 'south': 90, 'west': 180, 'north': 270}[facing]
    if opened:
        deg = (deg + (90 if hinge == 'left' else 270)) % 360
    for k, half in enumerate(('bottom', 'top')):
        if name == 'front_door':
            m = two_face_door(f'front_door_{half}', f'front_door_inner_{half}', hinge, opened)
        elif name == 'unit_door':
            m = vanilla_door('apartment_door_metal', hinge)
        else:
            m = vanilla_door(f'{name}_{half}', hinge)
        S.extra.append((m, place_deg(x, y + k, z, deg)))


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
        '##jjjV#yyy##',
        '#Ojjjj#yyyc#',
        '#Ojjjj#yyyc#',
        '#Ojjjj#yyyc#',
        '##WWW###WW##']
# finishes as the world's ApartmentBuilder gives them (genkan / hall / toilet: wainscot low, sand plaster above;
# DK, washroom, tatami: plaster; the bath: tiles; the western room: the old floral wallpaper; closets: plywood)
ROOMS = {
    'g': dict(floor='genkan_tile', wall='plaster_wall', lower='wood_wainscot', ceil='ceiling_board', low=-1),
    'h': dict(floor='old_flooring', wall='plaster_wall', lower='wood_wainscot', ceil='ceiling_board'),
    't': dict(floor='bath_tile_floor', wall='plaster_wall', lower='wood_wainscot', ceil='ceiling_board_stained'),
    'b': dict(floor='bath_tile_floor', wall='bath_tile_wall', ceil='ceiling_board_stained'),
    'w': dict(floor='vinyl_floor', wall='plaster_wall_stained', ceil='ceiling_board'),
    'k': dict(floor='vinyl_floor', wall='plaster_wall', ceil='ceiling_board_stained'),
    'y': dict(floor='old_flooring', wall='wallpaper_old', ceil='ceiling_board'),
    'j': dict(floor='tatami', wall='plaster_wall', ceil='ceiling_board'),
    'O': dict(floor='oshiire_floor', wall='oshiire_floor', ceil='oshiire_floor'),
    'c': dict(floor='oshiire_floor', wall='oshiire_floor', ceil='oshiire_floor'),
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
                if rm['floor'] == 'tatami':
                    # mats two blocks long: the north row (z 9) and the south row's east part run along x
                    col = x
                    axis_x = (z == 9) or (z == 12 and col >= 2)
                    worn = ((x * 7 + z * 13) % 5) == 0
                    t = RR.tex(H + ('tatami_worn' if worn else 'tatami'))
                    top = np.rot90(t, -1).copy() if axis_x else t
                    S.cube(x, low - 1, z, t, up=top)
                else:
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
            lo = -1 if ch == 'D' else 0             # the front door stands on the genkan's level, a step down
            for y in range(-1, 4):
                if lo <= y < lo + hole:
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
                        t = nrm.get('lower') if (y == nrm.get('low', 0) and nrm.get('lower')) else nrm['wall']
                        faces[f] = H + t
                S.cube(x, y, z, H + 'plaster_wall', **faces)
    # floors under the wall openings
    for z in range(z0, z1 + 1):
        for x in range(x0, x1 + 1):
            if cell(x, z) in 'dfW':
                S.cube(x, -1, z, H + 'old_flooring')
            elif cell(x, z) == 'D':
                S.cube(x, -2, z, H + 'genkan_tile')


def balcony_pane(S, x, y, z):
    """The world's sliding balcony door (closed): a glass pane on the wall's centre plane (texture
    balcony_door_bottom / _top)."""
    for k, half in enumerate(('bottom', 'top')):
        t = 'hitori:block/balcony_door_' + half
        m = {'textures': {'p': t, 'particle': t}, 'render_type': 'minecraft:translucent', 'elements': [{
            'from': [0, 0, 9], 'to': [16, 16, 10],
            'faces': {f: {'uv': [0, 0, 16, 16], 'texture': '#p'} for f in ('north', 'south')}}]}
        S.extra.append((m, place_deg(x, y + k, z, 0)))


def halves(S, name, x, z, facing, op=0):
    S.model(f'{name}_lower_{op}', x, 0, z, facing)
    S.model(f'{name}_upper_{op}', x, 1, z, facing)


def furnish203(S, tv_on=True, lamp_lit=False, laptop_on=True):
    """Room 203 furnished as the world's ApartmentBuilder.furnish203 does (local x = X - 22, y = Y - 70)."""
    # doors and openings
    door(S, 'front_door', 2, -1, 0, 'south')
    door(S, 'toilet_door', 6, 0, 2, 'east')
    door(S, 'interior_door', 6, 0, 5, 'east')
    door(S, 'interior_door', 6, 0, 7, 'east')
    door(S, 'bath_door', 9, 0, 3, 'north')
    for x in (2, 3, 5):
        halves(S, 'fusuma', x, 8, 'north')
    for x in (2, 3, 4, 8, 9):
        balcony_pane(S, x, 0, 13)
        S.model('rain_window', x, 2, 13, 'north')
    # genkan
    S.model('shoe_cabinet', 1, -1, 2, 'east')
    S.model('umbrella', 1, -1, 1, 'east')
    S.model('shoes', 2, -1, 2, 'north')
    S.model('shoes', 2, -1, 1, 'west')
    S.model('intercom', 1, 0, 2, 'east')
    S.model('breaker_panel', 1, 1, 1, 'east')
    # hall, toilet
    S.model('mirror_small', 4, 1, 3, 'east')
    S.model('calendar', 5, 1, 6, 'west')
    S.model('toilet_seat', 7, 0, 1, 'south')
    # bath
    S.model('bathtub_head_2', 10, 0, 1, 'north')
    S.model('bathtub_foot_2', 10, 0, 2, 'north')
    S.model('bath_tap', 10, 1, 1, 'south')
    S.model('shower', 9, 1, 1, 'east')
    S.model('bath_stool', 9, 0, 1, 'east')
    S.model('bucket', 9, 0, 2, 'north')
    S.model('rain_window', 10, 2, 0, 'north')
    # washroom
    S.model('washing_machine', 7, 0, 4, 'south')
    S.model('washbasin', 10, 0, 5, 'west')
    S.model('mirror_small', 10, 1, 5, 'west')
    # DK
    S.model('fridge_old', 1, 0, 4, 'east')
    S.model('microwave', 1, 1, 4, 'east')
    S.model('kitchen_sink', 1, 0, 5, 'east')
    S.model('cup_glass', 1, 1, 5, 'east')
    S.model('gas_stove', 1, 0, 6, 'east')
    S.model('knife_rack_has_knife', 1, 1, 6, 'east')
    S.model('drawer', 1, 0, 7, 'east')
    S.model('rice_cooker', 1, 1, 7, 'east')
    S.model('drawer', 2, 0, 4, 'south')
    S.model('salt_shaker', 2, 1, 4, 'south')
    S.model('rice_bag', 3, 0, 4, 'south')
    S.model('dining_table', 3, 0, 5, 'north')
    S.model('cup_glass', 3, 1, 5, 'north')
    S.model('dining_chair', 4, 0, 5, 'west')
    S.model('dining_chair', 3, 0, 6, 'north')
    S.model('wall_clock', 2, 2, 4, 'south')
    S.model('calendar', 1, 2, 4, 'east')
    S.model('cardboard_box', 4, 0, 4, 'west')
    wmodel(S, 'trash_bag', 4, 0, 7, 'west')
    S.model('fluorescent_ring', 2, 2, 5, 'north')
    # tatami room
    S.model('tv_stand', 5, 0, 9, 'west')
    S.model('crt_tv_on_0' if tv_on else 'crt_tv', 5, 1, 9, 'west')
    S.model('chabudai', 3, 0, 11, 'north')
    S.model('zabuton', 3, 0, 10, 'south')
    S.model('zabuton', 4, 0, 11, 'west')
    S.model('hanging_lamp_lit' if lamp_lit else 'hanging_lamp', 3, 2, 11, 'north')
    S.model('pull_switch', 3, 1, 11, 'north')
    S.model('altar_shelf', 5, 0, 12, 'west')
    for yy in (0, 1, 2):
        for x in (2, 3, 4):
            S.model('curtain_closed', x, yy, 12, 'north')
    for z in (10, 11, 12):
        halves(S, 'oshiire_fusuma', 1, z, 'east')
    S.model('futon_folded', 1, 2, 10, 'east')
    S.model('futon_folded', 1, 2, 11, 'east')
    S.model('cardboard_box', 1, 2, 12, 'east')
    # western room
    for x in (8, 9, 10):
        S.model('desk', x, 0, 7, 'south')
    S.model('desk', 10, 0, 8, 'west')
    S.model('printout', 8, 1, 7, 'south')
    S.model('sewing_box', 9, 1, 7, 'south')
    S.model('desk_lamp_lit', 10, 1, 7, 'west')
    S.model('laptop_block_open_on' if laptop_on else 'laptop_block_open', 10, 1, 8, 'west')
    S.model('desk_chair', 9, 0, 8, 'east')
    S.model('camera_bag', 7, 0, 9, 'east')
    S.model('shelf', 7, 0, 10, 'east')
    S.model('plush_doll_present', 7, 1, 10, 'east')
    S.model('bed_head', 7, 0, 11, 'north')
    S.model('bed_foot', 7, 0, 12, 'north')
    S.model('poster', 7, 1, 7, 'south')
    S.model('poster', 9, 2, 7, 'south')
    S.model('fluorescent_ring_lit', 8, 2, 9, 'north')
    for yy in (0, 1, 2):
        S.model('curtain', 8, yy, 12, 'north')
        S.model('curtain_closed', 9, yy, 12, 'north')
    # washing hung up indoors on a rainy night (部屋干し)
    S.model('laundry_pole', 8, 2, 11, 'north')
    S.model('laundry_pole', 9, 2, 11, 'north')
    wmodel(S, 'laundry_hanging', 8, 1, 11, 'north')
    wmodel(S, 'laundry_hanging', 9, 1, 11, 'south')
    for z in (10, 11, 12):
        halves(S, 'oshiire_fusuma', 10, z, 'west')


# ================================================================================================ scenes
def full203(**kw):
    vanilla_standins()
    S = Scene()
    apartment(S, 0, 11, 0, 13)
    furnish203(S, **kw)
    return S


TV_LIGHT = ((4.75, 0.85, 9.5), (0.55, 0.72, 1.0), 2.6, 2.0)
SODIUM = ((3.0, 1.2, 14.6), (1.0, 0.6, 0.28), 1.0, 3.0)


def scene_tatami():
    """The tatami room as furnished by the world: from the balcony corner (the DK fusuma at the back, the closet on
    the left, the TV glowing on the right) and from in front of the TV toward the closet."""
    S = full203()
    S.light(*TV_LIGHT)
    S.light(*SODIUM)
    eye, tgt = (4.55, 1.55, 12.45), (1.9, 0.55, 9.0)
    pair(S.render(eye, tgt), S.render(eye, tgt, night=True, exposure=1.35), 'scene_tatami')
    eye2, tgt2 = (5.35, 1.6, 10.15), (1.4, 0.75, 11.6)
    return pair(S.render(eye2, tgt2, fov=74), S.render(eye2, tgt2, fov=74, night=True, exposure=1.4),
                'scene_tatami_closet')


def scene_peek():
    """From inside the 押し入れ, crouched: the night view toward the TV, then the same through the fusuma_slit."""
    S = full203()
    S.light(*TV_LIGHT)
    S.light(*SODIUM)
    eye, tgt = (1.5, 1.05, 11.0), (5.2, 0.85, 9.6)
    night = S.render(eye, tgt, night=True, exposure=1.45, fov=70)
    return pair(night, overlay(night, 'fusuma_slit'), 'scene_peek')


def scene_closet():
    """Inside the closet: the shelf board and futon edge above, the box on the shelf, the light of the crack on the
    fusuma; day, and night with the closet_dark vignette."""
    S = full203()
    S.light((1.95, 1.0, 11.0), (0.5, 0.62, 0.85), 0.5, 0.45)
    eye, tgt = (1.35, 1.0, 10.25), (1.75, 1.35, 11.7)
    day = S.render(eye, tgt, fov=80)
    night = S.render(eye, tgt, night=True, exposure=1.6, fov=80)
    return pair(day, overlay(night, 'closet_dark'), 'scene_closet')


def scene_bath():
    """The bathroom from its doorway (the deep tiled tub full along the east wall, the tap above it, the shower, stool
    and basin) and the washroom (twin-tub washer, the washbasin and its mirror)."""
    S = full203()
    S.extra = [e for e in S.extra if 'bath_door' not in e[0]['textures']['b']] if False else S.extra
    S.light((10.0, 2.6, 1.5), (0.85, 0.9, 1.0), 0.8, 2.2)
    S.light((10.5, 2.5, -0.6), (1.0, 0.62, 0.3), 0.7, 2.0)
    eye, tgt = (9.5, 1.62, 2.75), (10.4, 0.35, 0.9)
    pair(S.render(eye, tgt, fov=86), S.render(eye, tgt, night=True, exposure=1.6, fov=86), 'scene_bath')
    S.light((8.5, 2.6, 4.5), (0.9, 0.95, 1.0), 0.6, 2.5)
    eye2, tgt2 = (7.4, 1.6, 5.6), (10.0, 0.7, 4.3)
    return pair(S.render(eye2, tgt2, fov=78), S.render(eye2, tgt2, night=True, exposure=1.6, fov=78),
                'scene_washroom')


def scene_kitchen():
    """The DK: fridge (microwave on top), sink with the glass on its drainboard, the gas stove and knife holder,
    the drawer cabinet with the rice cooker along the west wall; the gingham table and chairs, rice bag, clock."""
    S = full203()
    S.light((2.0, 2.6, 5.5), (0.9, 0.95, 1.0), 0.35, 3.0)
    S.light((4.6, 1.0, 9.5), (0.55, 0.72, 1.0), 0.6, 2.5)
    eye, tgt = (3.55, 1.6, 7.75), (1.3, 0.8, 4.6)
    return pair(S.render(eye, tgt, fov=76), S.render(eye, tgt, night=True, exposure=1.5, fov=76), 'scene_kitchen')


def scene_western():
    """The western room: the L of desks (printout, sewing box, the lit desk lamp, the laptop with the camera app),
    the chair, the colour box with the plush rabbit, the pipe bed, the camera bag, the poster."""
    S = full203()
    S.light((9.55, 1.2, 8.5), (0.6, 0.8, 0.7), 1.0, 1.3)              # the laptop
    S.light((10.4, 1.35, 7.3), (1.0, 0.95, 0.85), 1.1, 1.6)           # the desk lamp
    S.light((8.5, 2.6, 9.0), (1.0, 0.97, 0.9), 0.9, 3.0)              # the ring light (on at the start)
    S.light((8.5, 1.2, 14.6), (1.0, 0.6, 0.28), 0.8, 3.0)             # the street lamp through the curtain's gap
    # from beside the bed toward the desks; then from the desks toward the window (the washing hung up indoors)
    eye, tgt = (7.6, 1.5, 9.4), (10.3, 0.9, 7.6)
    out = pair(S.render(eye, tgt, fov=82), S.render(eye, tgt, night=True, exposure=1.5, fov=82), 'scene_western')
    eye2, tgt2 = (9.2, 1.62, 7.7), (7.6, 0.6, 11.6)
    pair(S.render(eye2, tgt2, fov=82), S.render(eye2, tgt2, night=True, exposure=1.5, fov=82), 'scene_western_bed')
    return out


def scene_genkan():
    """The genkan from the hall: the steel front door, shoe cabinet, shoes on the tiles a step down, the wet
    umbrella, the intercom and the breaker panel high on the wall; the hall's mirror."""
    S = full203()
    S.light((4.5, 2.6, 2.5), (1.0, 0.9, 0.75), 0.7, 3.0)
    S.light((2.6, 1.3, 1.6), (1.0, 0.92, 0.8), 0.5, 2.0)          # the genkan's bare bulb
    # standing on the genkan tiles with your back to the step, facing the door (its inner face: the chain is on)
    eye, tgt = (3.45, 0.66, 2.8), (1.9, 0.05, 0.2)
    return pair(S.render(eye, tgt, fov=80), S.render(eye, tgt, night=True, exposure=1.6, fov=80), 'scene_genkan')


# ================================================================================================ the building
# コーポ白樺 as the world's ApartmentBuilder / Terrain build it, in this file's local coordinates (x = X - 22,
# y = Y - 70): four units a floor (201..204 over 101..104), the corridor along the north side, the balconies on the
# south side over the yard, the street south of the yard with its poles, wires and the old street lamp before 203.
UNITS_X0 = (-22, -11, 0, 11)            # each unit's west wall (world x 0, 11, 22, 33)
ROW0, ROW13 = '##D#########', '##WWW###WW##'
F1, F2, G2 = -5, 0, -1                  # feet: ground floor, upper floor, upper genkan = corridor walk
BX1, BX2 = -22, 22
POLES_X = (-30, -13, 4, 21)             # utility poles on the street's north edge (world x -8, 9, 26, 43), z 18


def vanilla_standins():
    """Flat stand-ins for the vanilla textures of the world's own models (previews only - no vanilla jar here)."""
    def flat(c, n, seed):
        rgb = np.array([int(c[i:i + 2], 16) for i in (1, 3, 5)], float)
        a = np.empty((16, 16, 4))
        a[..., 3] = 255
        a[..., :3] = rgb * (1 + np.random.default_rng(seed).normal(0, n, (16, 16, 1)))
        return np.clip(a, 0, 255)
    for k, (name, c) in enumerate((('iron_block', '#c4c6c8'), ('white_wool', '#e2e0d8'), ('light_blue_wool', '#6a9ec4'),
                                   ('gray_wool', '#4c5054'), ('white_concrete_powder', '#d4d4cc'),
                                   ('yellow_concrete_powder', '#d8b43a'), ('black_concrete', '#0c0d0f'),
                                   ('scene_rail', '#3e3834'), ('scene_post', '#3a3a40'), ('scene_step', '#6e5440'),
                                   ('scene_pot', '#7a4426'), ('scene_dead', '#6a5a3e'))):
        RR._TEX.setdefault('block/' + name, flat(c, 0.06, 900 + k))


def world_model(name):
    """A model the world's tools/gen_assets.py writes: from this tree once merged, else from origin/hk-world."""
    import json
    import subprocess
    rel = 'src/main/resources/assets/hitori/models/block/' + name + '.json'
    p = os.path.join(os.path.dirname(TEX_ROOT), 'models', 'block', name + '.json')
    if os.path.isfile(p):
        return json.load(open(p))
    try:
        out = subprocess.run(['git', 'show', 'origin/hk-world:' + rel], capture_output=True, text=True,
                             cwd=os.path.dirname(os.path.abspath(__file__)), timeout=20)
        return json.loads(out.stdout) if out.returncode == 0 else None
    except Exception:
        return None


def _box(fr, to, t):
    return {'from': fr, 'to': to, 'faces': {f: {'texture': t} for f in FACES}}


# vanilla blocks the world uses, as simple shapes: iron bars (balcony railings, downpipes), polished deepslate walls
# (the posts), the stairs' steel steps, a potted dead bush
RAILING = {'textures': {'r': 'minecraft:block/scene_rail'}, 'elements': [
    _box([0, 14.6, 7.2], [16, 16, 8.8], '#r'), _box([0, 0.4, 7.6], [16, 1.4, 8.4], '#r')] +
    [_box([x, 1.4, 7.7], [x + 0.7, 14.6, 8.3], '#r') for x in (1.0, 3.3, 5.6, 7.9, 10.2, 12.5, 14.8)]}
DOWNPIPE = {'textures': {'r': 'minecraft:block/scene_rail'}, 'elements': [_box([6.5, 0, 6.5], [9.5, 16, 9.5], '#r')]}
POST = {'textures': {'p': 'minecraft:block/scene_post'}, 'elements': [_box([5, 0, 5], [11, 16, 11], '#p')]}
STEP = {'textures': {'s': 'minecraft:block/scene_step'}, 'elements': [
    _box([0, 7, 0], [16, 8, 16], '#s'), _box([8, 15, 0], [16, 16, 16], '#s'), _box([8, 8, 0], [9, 15, 16], '#s'),
    _box([0, 0, 0], [16, 1, 1], '#s'), _box([0, 0, 15], [16, 1, 16], '#s')]}
DEAD_PLANT = {'textures': {'p': 'minecraft:block/scene_pot', 'd': 'minecraft:block/scene_dead'}, 'elements': [
    _box([5, 0, 5], [11, 6, 11], '#p'),
    {'from': [8, 6, 4], 'to': [8, 14, 12], 'rotation': {'axis': 'y', 'angle': 45, 'origin': [8, 8, 8]},
     'faces': {'east': {'texture': '#d'}, 'west': {'texture': '#d'}}}]}


def wmodel(S, name, x, y, z, facing='north'):
    m = world_model(name)
    if m is not None:
        S.extra.append((m, place_deg(x, y, z, ROT[facing])))


def building(S, xa=BX1, xb=BX2, north=True, south=True):
    """The shell of コーポ白樺 between local x xa..xb: the corridor façade with its doors and windows, the balcony
    façade with its sliding doors (curtains drawn behind them), slabs, balconies, the corridor, the roof."""
    sid, con, cc = H + 'apartment_siding', H + 'concrete', H + 'corridor_concrete'
    for x in range(max(xa, BX1), min(xb, BX2) + 1):
        u = max(i for i, x0 in enumerate(UNITS_X0) if x0 <= x) if x < BX2 else 3
        col = x - UNITS_X0[u]
        end = x in (BX1, BX2)
        n_ch, s_ch = ('#', '#') if end else (ROW0[col], ROW13[col])
        # openings of the two façades: 2F walls y 0..3 over its slab at -1, 1F walls -5..-2 over the footing at -6;
        # the upper front door stands on the genkan's level (-1, 0), the windows are rain_window blocks in the wall
        n_open, n_win, s_open = set(), set(), set()
        if n_ch == 'D':
            n_open |= {G2, G2 + 1, F1, F1 + 1}
        if not end and col == 1:
            n_win |= {G2 + 2, F1 + 2}
        if not end and col in (7, 9, 10):
            n_win |= {F2 + 2, F1 + 2}
        if s_ch == 'W':
            s_open |= {F2, F2 + 1, F1, F1 + 1}
        for y in range(F1 - 1, 4):
            t = con if y == F1 - 1 else sid
            if north and y not in n_open and y not in n_win:
                S.cube(x, y, 0, t)
            if south and y not in s_open:
                S.cube(x, y, 13, t)
        if north:
            for y in n_win:
                S.model('rain_window', x, y, 0, 'north')
    # the building's ends (gable walls, end windows of the DK / the western room)
    for x in (BX1, BX2):
        if not (xa <= x <= xb):
            continue
        for z in range(1, 13):
            for y in range(F1 - 1, 4):
                S.cube(x, y, z, sid if y >= F1 else con)
    # rooms behind the glass: floors, a back wall, ceilings, the curtains (202 upstairs is empty)
    for x0 in UNITS_X0:
        for feet in (F1, F2):
            for col in (2, 3, 4, 8, 9):
                x = x0 + col
                if not (xa <= x <= xb) or not south:
                    continue
                fl = H + ('tatami' if col < 6 else 'old_flooring')
                for z in (10, 11, 12):
                    S.cube(x, feet - 1, z, fl)
                    S.cube(x, feet + 3, z, H + 'ceiling_board')
                for y in range(feet, feet + 3):
                    S.cube(x, y, 9, H + 'plaster_wall')
                if x0 == 0 and feet == F2:
                    closed = col != 8
                elif x0 == -11 and feet == F2:
                    continue
                else:
                    closed = True
                for y in range(feet, feet + 3):
                    S.model('curtain_closed' if closed else 'curtain', x, y, 12, 'north')
                S.extra.append((BALCONY_DOOR_LOWER, place_deg(x, feet, 13, 0)))
                S.extra.append((BALCONY_DOOR_UPPER, place_deg(x, feet + 1, 13, 0)))
    if south:
        for x in range(max(xa, BX1), min(xb, BX2) + 1):
            for z in (14, 15):
                S.cube(x, F1 - 1, z, con)
                S.cube(x, F2 - 1, z, con)
                S.cube(x, 3, z, cc)
            S.cube(x, 3, 16, cc)
            S.extra.append((RAILING, place_deg(x, F2, 15, 0)))
            S.cube(x, F1, 15, H + 'block_wall')
        for x in (-22, -11, 0, 11, 22):
            if xa <= x <= xb:
                for z in (14, 15):
                    for y in (F2, F2 + 1):
                        S.cube(x, y, z, sid)
                    for y in (F1, F1 + 1):
                        S.cube(x, y, z, H + 'block_wall')
        for x0 in UNITS_X0:
            if xa <= x0 + 10 <= xb:
                if x0 != -11:
                    S.model('ac_outdoor_unit', x0 + 10, F2, 14, 'south')
                S.model('ac_outdoor_unit', x0 + 10, F1, 14, 'south')
    if north:
        for x in range(max(xa, BX1 - 1), min(xb, BX2) + 1):
            for z in (-3, -2, -1):
                S.cube(x, G2 - 1, z, cc, down=con)
                S.cube(x, F1 - 1, z, con)
            for z in (-4, -3, -2, -1):
                S.cube(x, 3, z, cc)
            S.cube(x, G2, -3, sid)
        for x in list(range(-23, 23, 7)) + [22]:
            if xa <= x <= xb:
                for y in range(F1, G2 - 1):
                    S.extra.append((POST, place_deg(x, y, -3, 0)))
        for x0 in UNITS_X0:
            if xa <= x0 + 4 <= xb:
                S.model('fluorescent_ring_lit', x0 + 4, 2, -2, 'north')
                S.model('fluorescent_ring_lit', x0 + 4, G2 - 2, -2, 'north')
    # the roof: slate slabs in courses from the eaves (z -4, 16) to the ridge (z 6), the ridge cap
    top, bottom = slab_model('roof_slate', top=True), slab_model('roof_slate')
    for x in range(max(xa, BX1 - 2), min(xb, BX2 + 2) + 1):
        for z in range(-4, 17):
            i = z + 4 if z <= 6 else 16 - z
            S.extra.append((bottom if i % 2 == 0 else top, place_deg(x, 4 + i // 2, z, 0)))
        S.cube(x, 9, 6, H + 'roof_slate')
        for z in range(1, 13):
            if x in (BX1, BX2):
                i = z + 4 if z <= 6 else 16 - z
                for y in range(4, 4 + i // 2):
                    S.cube(x, y, z, sid)


BALCONY_DOOR_LOWER = {'textures': {'p': H + 'balcony_door_bottom'}, 'render_type': 'minecraft:translucent',
                      'elements': [_box([0, 0, 7.5], [16, 16, 8.5], '#p'), _box([0, 0, 7], [16, 0.5, 10.5], '#p')]}
BALCONY_DOOR_UPPER = {'textures': {'p': H + 'balcony_door_top'}, 'render_type': 'minecraft:translucent',
                      'elements': [_box([0, 0, 7.5], [16, 16, 8.5], '#p'), _box([0, 15.5, 7], [16, 16, 10.5], '#p')]}


def street(S, xa, xb, za=14, zb=27):
    """The ground south of the building: the yard's concrete, the wet asphalt street (z 19..24) with puddles, the
    poles on its north edge with their wires, the old street lamp before 203, the vending machine by the gate, a
    neighbour's block wall across the street."""
    for x in range(xa, xb + 1):
        for z in range(za, zb + 1):
            t = 'asphalt_wet' if 19 <= z <= 24 else ('concrete' if z <= 18 else 'concrete')
            S.cube(x, F1 - 1, z, H + t)
        for y in (F1, F1 + 1):
            if (x * 5) % 13 != 0:
                S.cube(x, y, 26, H + 'block_wall')
    for (px_, pz_) in ((1, 20), (2, 20), (-3, 22), (7, 21), (8, 21), (12, 23), (-9, 19), (-8, 23)):
        if xa <= px_ <= xb:
            S.model('puddle', px_, F1, pz_, 'north')
    for i, x in enumerate(POLES_X):
        if xa - 2 <= x <= xb + 2:
            for y in range(F1, 4):
                S.model('utility_pole', x, y, 18, 'east')
            S.model('utility_pole_top', x, 4, 18, 'east')
        if i + 1 < len(POLES_X):
            for wx in range(x + 1, POLES_X[i + 1]):
                if xa <= wx <= xb:
                    wmodel(S, 'power_wire', wx, 4, 18, 'east')
    S.model('street_lamp_old_lit', 4, 1, 17, 'north')
    if xa <= -22 <= xb:
        S.model('vending_machine_small', -22, F1, 18, 'south')


def balcony203(S):
    """203's balcony as furnish203 leaves it: the laundry pole, the forgotten washing, a dead plant, puddles."""
    for x in range(2, 9):
        S.model('laundry_pole', x, F2 + 2, 14, 'north')
    wmodel(S, 'laundry_hanging', 5, F2 + 1, 14, 'north')
    wmodel(S, 'laundry_hanging', 7, F2 + 1, 14, 'south')
    S.extra.append((DEAD_PLANT, place_deg(1, F2, 14, 0)))
    S.model('puddle', 5, F2, 15, 'north')
    S.model('puddle', 9, F2, 14, 'north')


def corridor_side(S, xa, xb):
    """The corridor's furniture: the doors (203's steel door, the others' unit doors), nameplates, 202's vacancy
    sign, 201's broken umbrella and rubbish, puddles blown in by the rain, the stairs at the west end."""
    for u, x0 in enumerate(UNITS_X0):
        if not (xa <= x0 + 2 <= xb):
            continue
        door(S, 'front_door' if x0 == 0 else 'unit_door', x0 + 2, G2, 0, 'south')
        door(S, 'unit_door', x0 + 2, F1, 0, 'south')
        S.model(f'nameplate_{4 + u}', x0 + 3, G2 + 1, -1, 'north')
        S.model(f'nameplate_{u}', x0 + 3, F1 + 1, -1, 'north')
    if xa <= -10 <= xb:
        S.model('vacancy_sign', -10, G2 + 1, -1, 'north')
    if xa <= -21 <= xb:
        S.model('umbrella', -21, G2, -1, 'north')
        wmodel(S, 'trash_bag', -19, G2, -1, 'north')
    for (px_, pz_) in ((-4, -2), (-3, -2), (6, -2), (-15, -2), (9, -1)):
        if xa <= px_ <= xb:
            S.model('puddle', px_, G2, pz_, 'north')
    # the steel stairs at the west end rising east to the landing, rails both sides
    for i in range(4):
        x, y = -27 + i, F1 + i
        if xa - 6 <= x <= xb:
            for z in (-2, -1):
                S.extra.append((STEP, place_deg(x, y, z, 90)))
            S.model('stairs_rail', x, y, -3, 'east')
            S.model('stairs_rail', x, y, 0, 'east')
    S.model('stairs_rail', -23, G2, 0, 'north')


def scene_exterior():
    """コーポ白樺 at 3 a.m. from the street: the balconies (203's with its washing and the street lamp before it, the
    western room's curtain open a crack on its lit lamp), the air conditioners, the poles and wires, the vending
    machine, the wet asphalt; then the upstairs corridor in front of 203, looking west toward 202 and the stairs."""
    vanilla_standins()
    S = Scene()
    building(S, -22, 22, north=False)
    street(S, -24, 24)
    balcony203(S)
    S.light((4.0, 1.05, 16.6), (1.0, 0.62, 0.28), 3.0, 6.5)          # the sodium lamp (under its shade)
    S.light((4.0, -4.6, 18.8), (1.0, 0.6, 0.3), 0.9, 3.5)            # its pool on the asphalt (the world's light 9)
    S.light((-22.0, -4.0, 19.2), (0.75, 0.88, 1.0), 2.2, 2.6)        # the vending machine
    S.light((8.6, 1.4, 11.2), (1.0, 0.95, 0.85), 1.6, 2.6)           # 203's western room: the lamp is on
    S.light((3.0, 0.9, 12.6), (0.5, 0.68, 1.0), 0.7, 1.2)            # the TV behind the tatami room's curtains
    S.light((14.0, 1.0, 11.0), (0.5, 0.66, 1.0), 0.5, 1.6)           # a TV's glow behind 204's
    eye, tgt = (-1.5, F1 + 1.8, 25.2), (4.5, -0.3, 13.0)
    day = S.render(eye, tgt, fov=70, w=960, h=540)
    night = S.render(eye, tgt, fov=70, w=960, h=540, night=True, exposure=1.7, ambient=(0.016, 0.02, 0.03),
                     fog=((0.03, 0.035, 0.05), 40.0))
    out = pair(day, night, 'scene_exterior')
    # the corridor upstairs, in front of 203's door, looking west toward 202 (the vacancy sign) and the stairs
    C = Scene()
    building(C, -24, 14, south=False)
    corridor_side(C, -24, 14)
    for x in range(-24, 15):
        for z in range(-8, -3):
            C.cube(x, F1 - 1, z, H + 'concrete')
    for x0 in UNITS_X0:
        C.light((x0 + 4.5, 2.3, -1.5), (0.85, 0.95, 1.0), 1.7, 3.6)    # the fluorescent ring over each door
    eye2, tgt2 = (6.0, G2 + 1.62, -2.2), (-6.0, 0.2, -0.6)
    cday = C.render(eye2, tgt2, fov=74, w=960, h=540)
    cnight = C.render(eye2, tgt2, fov=74, w=960, h=540, night=True, exposure=1.7, ambient=(0.016, 0.02, 0.03),
                      fog=((0.03, 0.035, 0.05), 30.0))
    pair(cday, cnight, 'scene_corridor')
    return out


def scene_camera_app():
    """The camera app: the bathtub seen by a fixed camera in grey-green IR (desaturated, noisy) under the cctv
    overlay, and the laptop frame with the same feed in its viewport."""
    S = full203()
    S.light((9.6, 2.2, 3.0), (1.0, 1.0, 1.0), 1.8, 2.5)          # the IR illuminator of the camera
    eye = (9.3, 2.55, 2.85)
    tgt = (10.3, 0.4, 1.0)
    img = S.render(eye, tgt, fov=82, night=True, exposure=1.8)
    lum = img @ np.array([0.3, 0.59, 0.11])
    rng_ = np.random.default_rng(5)
    lum = np.clip(lum * 1.15 + rng_.normal(0, 9, lum.shape), 0, 255)
    ir = np.stack([lum * 0.78, lum * 0.92, lum * 0.82], -1)
    from PIL import ImageDraw
    from common import font as _font, FONT_LABEL as _FL
    h, w = ir.shape[:2]
    gs = w / 640.0                      # the GUI scale of a 1080p screen at gui scale 3 (640 x 360 scaled units)

    def texts(img, x0, y0, x1, y1):
        """The game's CameraApp text, drawn where it draws it (scaled units * gs)."""
        im = Image.fromarray(np.clip(img, 0, 255).astype(np.uint8))
        d = ImageDraw.Draw(im)
        f = _font(_FL, int(9 * gs))
        pad = max(6, (x1 - x0) / gs / 40) * gs
        d.rectangle([x0 + pad, y0 + pad + gs, x0 + pad + 6 * gs, y0 + pad + 7 * gs], fill=(224, 32, 32))
        d.text((x0 + pad + 9 * gs, y0 + pad), 'REC', fill=(240, 240, 240), font=f)
        d.text((x0 + pad, y0 + pad + 12 * gs), 'CAM1  浴室' if False else 'CAM1  bath', fill=(240, 240, 240), font=f)
        d.text((x0 + pad, y0 + pad + 24 * gs), 'IR', fill=(184, 224, 184), font=f)
        d.text((x1 - pad - 110 * gs, y1 - pad - 9 * gs), '2026/11/03 03:21:07', fill=(240, 240, 240), font=f)
        d.text((x1 - pad - 52 * gs, y0 + pad), '62%', fill=(240, 240, 240), font=f)
        d.rectangle([x1 - pad - 30 * gs, y0 + pad, x1 - pad - 12 * gs, y0 + pad + 9 * gs], outline=(240, 240, 240))
        for k in range(3):
            tx = x0 + pad + k * 17 * gs
            d.rectangle([tx, y1 - pad - 11 * gs, tx + 14 * gs, y1 - pad], fill=(224, 224, 224) if k == 0 else (40, 40, 40))
        return np.asarray(im, np.float32)
    # the laptop: the feed in the game's rect, the CCTV frame over it, the laptop frame over everything
    x0, y0, x1, y1 = int(w / 22), int(h / 14), int(w - w / 22), int(h - h / 9)
    lap = np.zeros_like(ir)
    lap[:] = ir
    feed = overlay(lap, 'cctv', (x0, y0, x1, y1))
    feed = texts(feed, x0, y0, x1, y1)
    lap = overlay(feed, 'laptop_frame')
    # the phone: the feed rect 0.58 w, centred a little high, the dark around it, the phone frame over the body
    fw = int(w * 0.58)
    fh = fw * 9 // 16
    px0 = (w - fw) // 2
    py0 = (h - fh) // 2 - h // 20
    ph = np.zeros_like(ir)
    ph[:] = ir
    ph = overlay(ph, 'cctv', (px0, py0, px0 + fw, py0 + fh))
    ph = texts(ph, px0, py0, px0 + fw, py0 + fh)
    m14, m10 = int(14 * gs), int(10 * gs)
    dark = np.array([5, 5, 6], np.float32)
    mask = np.ones(ph.shape[:2], bool)
    mask[py0 - m10:py0 + fh + m10, px0 - m14:px0 + fw + m14] = False
    ph[mask] = ph[mask] * (1 - 0.956) + dark * 0.956
    ph = overlay(ph, 'phone_frame', (px0 - m14, py0 - m10, px0 + fw + m14, py0 + fh + m10))
    save(ph, 'scene_camera_phone')
    return pair(ir, lap, 'scene_camera_app')


def scene_title():
    """The title screen as it might look: the logo over a dim, slightly blurred night view of the tatami room."""
    from PIL import ImageFilter
    S = full203(lamp_lit=False)
    S.light(*TV_LIGHT)
    S.light(*SODIUM)
    eye, tgt = (4.55, 1.55, 12.45), (1.9, 0.55, 9.0)
    bg = S.render(eye, tgt, w=960, h=540, night=True, exposure=1.1)
    im = Image.fromarray(bg.astype(np.uint8)).filter(ImageFilter.GaussianBlur(1.6))
    bg = np.asarray(im, np.float32) * 0.75
    logo = Image.open(os.path.join(TEX_ROOT, 'gui', 'title_logo.png')).convert('RGBA').resize((768, 384), Image.BILINEAR)
    lg = np.asarray(logo, np.float32)
    x0, y0 = (960 - 768) // 2, 40
    a = lg[..., 3:4] / 255.0
    bg[y0:y0 + 384, x0:x0 + 768] = bg[y0:y0 + 384, x0:x0 + 768] * (1 - a) + lg[..., :3] * a
    return save(bg, 'scene_title')


def scene_doors():
    """Every vanilla-style door of 203 side by side (as the world's door_* models draw them), seen from the hall, with
    the fusuma and the closet fusuma beside them."""
    S = Scene()
    for x in range(-1, 9):
        S.cube(x, -1, 0, H + 'old_flooring')
        S.cube(x, -1, -1, H + 'old_flooring')
        for y in range(0, 3):
            S.cube(x, y, 1, H + 'plaster_wall', north=H + ('wood_wainscot' if y == 0 else 'plaster_wall'))
    # 203's steel door from the corridor (its outer face) and from the genkan (its inner face), then the others
    entries = [('front_door', 'south', 13 / 16), ('front_door', 'north', 0), ('interior_door', 'north', 0),
               ('toilet_door', 'north', 0), ('bath_door', 'north', 0)]
    for i, (d, f, dz) in enumerate(entries):
        door(S, d, i * 1.2 - 0.9, 0, dz, f)
    balcony_pane(S, 6.5, 0, -0.55)
    S.model('fusuma_lower_0', 7.5, 0, -0.45, 'north') if False else None
    for k, (nm, x) in enumerate((('fusuma', 7.0), ('oshiire_fusuma', 8.0))):
        S.model(f'{nm}_lower_0', x, 0, 0, 'north')
        S.model(f'{nm}_upper_0', x, 1, 0, 'north')
    S.light((3.5, 2.6, -2.5), (1.0, 0.92, 0.8), 1.4, 5.0)
    eye, tgt = (3.6, 1.2, -5.6), (3.6, 0.95, 0.5)
    return pair(S.render(eye, tgt, w=960, h=420, fov=62), S.render(eye, tgt, w=960, h=420, fov=62, night=True,
                                                                  exposure=1.5), 'scene_doors')


SCENES = {'tatami': scene_tatami, 'peek': scene_peek, 'closet': scene_closet, 'bath': scene_bath,
          'kitchen': scene_kitchen, 'western': scene_western, 'genkan': scene_genkan, 'exterior': scene_exterior,
          'camera_app': scene_camera_app, 'title': scene_title, 'doors': scene_doors}


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
