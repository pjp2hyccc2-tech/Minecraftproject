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
    S.model('cardboard_box', 2, 0, 7, 'north')
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
        S.model('curtain_closed', 2, yy, 12, 'north')
        S.model('curtain_closed', 3, yy, 12, 'north')
        S.model('curtain', 4, yy, 12, 'north')
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
    S.model('fluorescent_ring_lit', 8, 2, 9, 'north')
    for yy in (0, 1, 2):
        S.model('curtain_closed', 8, yy, 12, 'north')
        S.model('curtain_closed', 9, yy, 12, 'north')
    for z in (10, 11, 12):
        halves(S, 'oshiire_fusuma', 10, z, 'west')


# ================================================================================================ scenes
def full203(**kw):
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
    S.light((9.55, 1.2, 8.5), (0.6, 0.8, 0.7), 1.0, 1.3)
    S.light((10.4, 1.35, 7.3), (1.0, 0.95, 0.85), 1.1, 1.6)
    S.light((8.5, 1.2, 14.6), (1.0, 0.6, 0.28), 0.8, 3.0)
    eye, tgt = (9.45, 1.6, 12.4), (8.4, 0.8, 7.4)
    return pair(S.render(eye, tgt, fov=76), S.render(eye, tgt, night=True, exposure=1.5, fov=76), 'scene_western')


def scene_genkan():
    """The genkan from the hall: the steel front door, shoe cabinet, shoes on the tiles a step down, the wet
    umbrella, the intercom and the breaker panel high on the wall; the hall's mirror."""
    S = full203()
    S.light((4.5, 2.6, 2.5), (1.0, 0.9, 0.75), 0.7, 3.0)
    eye, tgt = (4.7, 1.62, 2.7), (2.0, -0.1, 0.6)
    return pair(S.render(eye, tgt, fov=78), S.render(eye, tgt, night=True, exposure=1.6, fov=78), 'scene_genkan')


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
    names = ['front_door', 'interior_door', 'toilet_door', 'bath_door']
    for i, d in enumerate(names):
        for k, half in enumerate(('bottom', 'top')):
            S.extra.append((vanilla_door(f'{d}_{half}'), place_deg(i * 1.5 - 0.5, k, 0, 270)))
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
