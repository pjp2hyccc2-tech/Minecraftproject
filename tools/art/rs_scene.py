#!/usr/bin/env python3
"""In-context previews of HIDAMARI (not shipped): small block scenes built from the surface textures and the prop
models, rendered in perspective in neutral light and at night lit only by the restaurant's own light sources.

usage: python3 tools/art/rs_scene.py [scene ...]   -> tools/art/preview/scene_<name>[_night].png
"""
import math
import os
import sys
sys.dont_write_bytecode = True
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import numpy as np
from PIL import Image

import render3d as R  # noqa: E402
import rs_render as RR  # noqa: E402
import rs_modules  # noqa: E402,F401
from common import PREVIEW  # noqa: E402

FACES = ('north', 'south', 'east', 'west', 'up', 'down')
NB = {'north': (0, 0, -1), 'south': (0, 0, 1), 'east': (1, 0, 0), 'west': (-1, 0, 0), 'up': (0, 1, 0), 'down': (0, -1, 0)}


class Scene:
    def __init__(self):
        self.cubes = {}     # (x, y, z) -> dict face -> tex ref, mode
        self.models = []    # (name, x, y, z, facing)
        self.lights = []    # (pos, color, intensity, radius)
        self.decals = []    # (x, y, z, tex, mode): a flat 1 px layer on the block below

    def decal(self, x, y, z, tex, mode='translucent'):
        self.decals.append((x, y, z, tex, mode))

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

    def render(self, eye, target, w=960, h=540, fov=70, night=False, ambient=(0.035, 0.04, 0.055), exposure=1.0,
               bg=(16, 18, 24), fog=None):
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
        for x, y, z, t, mode in self.decals:
            pts = np.array([(0, 1, 0), (16, 1, 0), (16, 1, 16), (0, 1, 16)], float) / 16 + [x, y, z]
            cv.quad(pts, R._uv_corners([0, 0, 16, 16]), RR.tex(t), shade=1.0, mode=mode)
        cv.finish()
        if night:
            cv.light(self.lights, ambient=ambient, exposure=exposure, fog=fog)
        return np.clip(cv.color, 0, 255)


def save(arr, name):
    os.makedirs(PREVIEW, exist_ok=True)
    p = os.path.join(PREVIEW, name + '.png')
    Image.fromarray(arr.astype(np.uint8)).save(p)
    return p


def pair(a, b, name):
    out = np.concatenate([a, b], axis=1)
    return save(out, name)


# ================================================================================================ scenes
def room_shell(S, x0, x1, z0, z1, floor='hitori:block/dining_floor', wall='hitori:block/wallpaper',
               lower='hitori:block/wainscot', ceiling='hitori:block/ceiling_tile', height=4, open_sides=('north',)):
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            S.cube(x, -1, z, floor)
            S.cube(x, height, z, ceiling)
    for y in range(height):
        t = lower if y == 0 else wall
        for x in range(x0 - 1, x1 + 2):
            if 'south' not in open_sides:
                S.cube(x, y, z1 + 1, t)
            if 'north' not in open_sides:
                S.cube(x, y, z0 - 1, t)
        for z in range(z0, z1 + 1):
            if 'west' not in open_sides:
                S.cube(x0 - 1, y, z, t)
            if 'east' not in open_sides:
                S.cube(x1 + 1, y, z, t)


def scene_booth():
    """A south-window booth (table 7): two benches facing each other, partitions, table set, pendant lamp, blinds."""
    S = Scene()
    room_shell(S, -3, 6, -2, 3, open_sides=('north', 'east'))
    # the window wall (south): wainscot band, shop windows above with blinds behind (inside)
    for x in range(-4, 8):
        S.cube(x, 0, 4, 'hitori:block/wainscot')
        for y in (1, 2, 3):
            S.cube(x, y, 4, 'hitori:block/shop_window', mode='translucent')
    for x in range(-3, 7):
        for y in (2, 3):
            S.model('window_blind', x, y, 3, 'north')
    S.model('window_blind_closed', -3, 1, 3, 'north')
    # booth pair along x (table 2 long), seats facing each other (east-west axis)
    for z in (1, 2):
        S.model('booth_seat', 0, 0, z, 'east')      # sitter faces east
        S.model('booth_seat', 3, 0, z, 'west')
        S.model('booth_partition', 0, 1, z, 'east')
        S.model('booth_partition', 3, 1, z, 'west')
        S.model('restaurant_table', 1, 0, z)
        S.model('restaurant_table', 2, 0, z)
    S.model('menu_stand', 1, 1, 2, 'east')
    S.model('call_button_lit', 2, 1, 2, 'west')
    S.model('water_glass_2', 2, 1, 1)
    S.model('pendant_lamp_lit', 1, 3, 2)
    S.model('pendant_lamp_lit', 2, 3, 1)
    # the next booth, a planter divider and a fallen chair in the aisle
    for z in (1, 2):
        S.model('booth_seat', 4, 0, z, 'east')
        S.model('booth_partition', 4, 1, z, 'east')
    for x in range(-3, 4):
        S.model('planter', x, 0, -2)
    S.model('restaurant_chair_fallen', 5, 0, -1, 'west')
    S.model('menu_scattered_3', 4, 0, -1)
    S.model('exit_sign_hanging', 5, 3, 0, 'west')
    S.model('pillar', 6, 0, -2) if False else None
    S.light((1.5, 3.4, 2.5), (1.0, 0.72, 0.42), 2.6, 1.6)
    S.light((2.5, 3.4, 1.5), (1.0, 0.72, 0.42), 2.2, 1.6)
    S.light((5.5, 3.4, 0.5), (0.35, 1.0, 0.5), 0.6, 1.2)
    S.light((1.0, 2.0, 6.0), (1.0, 0.55, 0.2), 1.4, 4.0)      # sodium light from the lot through the blinds
    eye = (6.0, 1.62, -1.4)
    tgt = (1.6, 0.7, 2.4)
    day = S.render(eye, tgt)
    night = S.render(eye, tgt, night=True, exposure=1.2)
    return pair(day, night, 'scene_booth')


def scene_kitchen():
    S = Scene()
    room_shell(S, -4, 4, -1, 2, floor='hitori:block/kitchen_floor', wall='hitori:block/white_tile_dirty',
               lower='hitori:block/stainless', ceiling='hitori:block/ceiling_tile_stained', open_sides=('north',))
    line_ = ['kitchen_counter', 'fryer_on', 'gas_range_lit', 'gas_range', 'griddle', 'kitchen_counter', 'kitchen_sink',
             'dishwasher_running', 'kitchen_counter']
    for i, n in enumerate(line_):
        S.model(n, -4 + i, 0, 2, 'north')
    for x in range(-3, 2):
        S.model('exhaust_hood', x, 3, 2, 'north')
    S.model('pot_shelf', 2, 2, 2, 'north')
    S.model('dish_rack', 3, 1, 2, 'north')
    S.model('prep_table', -1, 0, 0)
    S.model('prep_table', 0, 0, 0)
    S.model('cutting_board', 0, 1, 0, 'south')
    S.model('kitchen_trash', 3, 0, -1)
    S.model('broken_plate', 2, 0, 0)
    S.light((-2.5, 1.3, 1.5), (0.4, 0.6, 1.0), 1.0, 1.0)     # gas flames
    S.light((-3.5, 1.2, 1.5), (1.0, 0.6, 0.25), 0.5, 1.0)    # fryer glow
    S.light((0.0, 3.6, -2.5), (0.85, 0.95, 1.0), 1.0, 3.0)   # one tube on in the next room
    eye = (1.2, 1.62, -0.9)
    tgt = (-2.6, 0.6, 2.4)
    return pair(S.render(eye, tgt), S.render(eye, tgt, night=True, exposure=1.3), 'scene_kitchen')


def scene_register():
    S = Scene()
    room_shell(S, -3, 4, -1, 2, ceiling='hitori:block/ceiling_tile', open_sides=('north',))
    for x in range(-3, 5):
        S.cube(x, 4, 0, 'hitori:block/ceiling_tile', down='hitori:block/downlight_lit' if x in (0, 1) else
               'hitori:block/ceiling_tile')
    for x in (-1, 0, 1):
        S.model('counter', x, 0, 1, 'north')
    S.model('register', 0, 1, 1, 'north')
    S.model('service_bell', -1, 1, 1, 'north')
    S.model('business_phone_ringing', 1, 1, 1, 'north')
    S.model('reservation_book', -1, 1, 1, 'north') if False else None
    S.model('cake_case', 2, 0, 1, 'north')
    S.model('capsule_machine', 4, 0, 2, 'north')
    S.model('call_display_7', 0, 3, 2, 'north')
    S.model('exit_sign', -2, 3, 2, 'north')
    S.model('waiting_bench', -3, 0, -1, 'east')
    S.model('magazine_rack', -3, 0, 0, 'east')
    S.model('umbrella_stand', 3, 0, -1, 'west')
    S.model('notice_board', -3, 1, 2, 'north')
    S.light((0.5, 3.5, 0.5), (1.0, 0.82, 0.55), 1.4, 1.6)
    S.light((1.5, 3.5, 0.5), (1.0, 0.82, 0.55), 1.4, 1.6)
    S.light((2.5, 1.0, 1.2), (0.85, 0.95, 1.0), 1.2, 1.2)    # cake case
    S.light((-1.5, 3.3, 1.8), (0.3, 1.0, 0.45), 0.6, 1.2)    # exit sign
    eye = (1.5, 1.62, -3.4)
    tgt = (0.6, 1.0, 1.6)
    return pair(S.render(eye, tgt), S.render(eye, tgt, night=True, exposure=1.2), 'scene_register')


def scene_restroom():
    S = Scene()
    room_shell(S, -2, 3, -1, 1, floor='hitori:block/restroom_floor', wall='hitori:block/white_tile',
               lower='hitori:block/white_tile', ceiling='hitori:block/ceiling_tile_stained', open_sides=('north', 'south'))
    # the wall with the dark mirror: the cavity behind it (stained floor, a dead sink)
    for x in range(-3, 5):
        for y in range(4):
            if not (y == 1 and x in (-1, 0, 1)):
                S.cube(x, y, 2, 'hitori:block/white_tile')
    for x in (-1, 0, 1):
        S.model('mirror' if x != 0 else 'mirror_cracked', x, 1, 2, 'south' if False else 'north')
    for x in range(-2, 4):
        S.cube(x, -1, 3, 'hitori:block/restroom_floor_dark')
        S.cube(x, -1, 4, 'hitori:block/restroom_floor_dark')
        S.cube(x, 4, 3, 'hitori:block/ceiling_tile_stained')
    S.model('restroom_sink', 0, 0, 3, 'south')
    for x in (-1, 0, 1):
        S.model('restroom_sink_running' if x == 0 else 'restroom_sink', x, 0, 1, 'north')
    S.model('hand_dryer_running', 3, 1, 1, 'west') if False else None
    S.model('paper_towel', 2, 1, 1, 'north')
    S.model('hand_dryer', 3, 1, 0, 'west')
    S.model('stall_wall', -2, 0, -1, 'east') if False else None
    S.cube(0, 4, 0, 'hitori:block/ceiling_tile', down='hitori:block/ceiling_light_lit')
    S.light((0.5, 3.4, 0.5), (0.85, 1.0, 0.9), 1.6, 2.4)
    S.light((0.5, 1.5, 3.6), (0.4, 0.5, 0.7), 0.25, 1.0)
    eye = (0.5, 1.62, -2.2)
    tgt = (0.5, 1.2, 2.5)
    return pair(S.render(eye, tgt), S.render(eye, tgt, night=True, exposure=1.2), 'scene_restroom')


def scene_exterior():
    """The south façade at night: brick base, stucco, shop windows with blinds, fascia and tiled roof with the lit
    letters, the entrance, the lot with lines, wheel stops and puddles, vending machines, the pole sign, lamps."""
    S = Scene()
    W0, W1 = -8, 10
    zf = 6
    for x in range(W0, W1 + 1):
        S.cube(x, 0, zf, 'hitori:block/facade_brick')
        S.cube(x, 4, zf, 'hitori:block/fascia')
        for y in (1, 2, 3):
            if -1 <= x <= 1:
                continue
            if x in (W0, W1) or x % 6 == 0:
                S.cube(x, y, zf, 'hitori:block/exterior_wall')
            else:
                S.cube(x, y, zf, 'hitori:block/shop_window', mode='translucent')
                S.model('window_blind' if (x + y) % 3 else 'window_blind_closed', x, y, zf + 1, 'south')
        S.cube(x, 5, zf + 1, 'hitori:block/roof_tile')
        S.cube(x, 6, zf + 2, 'hitori:block/roof_tile')
        for zz in range(zf + 1, zf + 4):
            S.cube(x, -1, zz, 'hitori:block/dining_floor')
            S.cube(x, 4, zz, 'hitori:block/ceiling_tile', down='hitori:block/ceiling_light_lit' if x % 4 == 0 and zz == zf + 2 else 'hitori:block/ceiling_tile')
    for k in range(8):
        S.model(f'roof_sign_tile_{k}_lit', 4 - k, 7, zf + 2, 'north')
    # entrance: brick sill, the automatic door rail, the key switch, the mat
    for x in (-1, 0, 1):
        S.model('auto_door_rail', x, 3, zf, 'north')
    S.model('door_switch_on', 2, 1, zf - 1, 'north')
    # the lot
    for x in range(W0 - 2, W1 + 3):
        for z in range(-12, zf):
            t = 'hitori:block/asphalt'
            if z in (-1, 0, 1, 2, 3) and (x % 3 == 1) and x not in (-1, 0, 1):
                t = 'hitori:block/parking_line'
            if z >= zf - 1:
                t = 'hitori:block/sidewalk'
            S.cube(x, -1, z, t)
    for x in range(W0, W1 + 1):
        if x % 3 != 1 and x not in (-1, 0, 1):
            S.model('wheel_stop', x, 0, 3, 'south')
    for (px_, pz) in ((-3, -2), (-2, -2), (-2, -3), (4, -5), (5, -5)):
        S.decal(px_, 0, pz, 'hitori:block/puddle')
    S.model('vending_machine_lower', 6, 0, zf - 1, 'north')
    S.model('vending_machine_upper', 6, 1, zf - 1, 'north')
    S.model('vending_machine_lower', 7, 0, zf - 1, 'north')
    S.model('vending_machine_upper', 7, 1, zf - 1, 'north')
    S.model('trash_station', 8, 0, zf - 1, 'north')
    S.model('menu_board_1', -3, 0, zf - 2, 'north')
    S.model('outdoor_planter', -5, 0, zf - 1, 'north')
    S.model('bike_rack', -7, 0, zf - 1, 'north')
    # pole sign (4x4 box on a 6-high pole), street lamp
    for t in range(16):
        c, r = t % 4, t // 4
        S.model(f'pole_sign_tile_{t}_lit', -9 + 3 - c, 9 - r, -4, 'north')
    for y in range(0, 6):
        S.model('pole_sign_pole_lit', -8, y, -4, 'north')
    for y in range(0, 5):
        S.model('street_lamp_pole', 9, y, -3, 'north')
    S.model('street_lamp_head_lit', 9, 5, -3, 'south')
    S.light((0.5, 2.0, zf + 1.5), (1.0, 0.9, 0.75), 2.4, 4.0)          # the lit interior behind the windows
    S.light((-4.5, 2.0, zf + 1.5), (1.0, 0.9, 0.75), 1.8, 4.0)
    S.light((6.5, 1.5, zf - 2.0), (0.9, 0.95, 1.0), 2.2, 2.5)          # vending machines
    S.light((9.5, 5.2, -2.0), (1.0, 0.55, 0.18), 3.0, 6.0)             # sodium lamp
    S.light((-7.5, 7.5, -3.0), (1.0, 0.8, 0.5), 2.0, 5.0)              # pole sign
    S.light((0.5, 7.5, zf + 1.5), (1.0, 0.7, 0.3), 1.2, 4.0)           # roof letters
    eye = (4.5, 1.7, -9.5)
    tgt = (-0.5, 3.4, zf)
    day = S.render(eye, tgt, fov=72)
    night = S.render(eye, tgt, fov=72, night=True, exposure=1.3, ambient=(0.025, 0.03, 0.045),
                     fog=((0.04, 0.05, 0.07), 34.0))
    return pair(day, night, 'scene_exterior')


def scene_back():
    S = Scene()
    room_shell(S, -3, 3, -1, 2, floor='hitori:block/dining_floor_worn', wall='hitori:block/wallpaper_stained',
               lower='hitori:block/wainscot', ceiling='hitori:block/ceiling_tile_stained', open_sides=('north',))
    for x in (-3, -2, -1):
        S.model('locker_lower' if x != -2 else 'locker_lower_open', x, 0, 2, 'north')
        S.model('locker_upper' if x != -2 else 'locker_upper_open', x, 1, 2, 'north')
    S.model('time_recorder', 0, 1, 2, 'north')
    S.model('bulletin_board', 1, 1, 2, 'north')
    S.model('crt_tv_on', 3, 0, 2, 'north')
    S.model('staff_table', 1, 0, 0)
    S.model('folding_chair', 0, 0, 0, 'east')
    S.model('folding_chair', 2, 0, 0, 'west')
    S.model('security_monitors_on', -3, 2, 1, 'east') if False else None
    S.model('cardboard_boxes', -3, 0, 0, 'east')
    S.cube(0, 4, 0, 'hitori:block/ceiling_tile', down='hitori:block/ceiling_light')
    S.light((3.5, 0.9, 1.5), (0.7, 0.8, 1.0), 1.0, 1.4)
    eye = (3.2, 1.62, -2.6)
    tgt = (-0.5, 0.9, 2.0)
    return pair(S.render(eye, tgt), S.render(eye, tgt, night=True, exposure=1.3), 'scene_staff_room')


def scene_office():
    S = Scene()
    room_shell(S, -2, 2, -1, 2, floor='hitori:block/dining_floor_worn', wall='hitori:block/wallpaper',
               lower='hitori:block/wainscot', ceiling='hitori:block/ceiling_tile', open_sides=('north',))
    for x in (-1, 0):
        S.model('security_monitors_on' if x == -1 else 'security_monitors_on_alert', x, 1, 2, 'north')
        S.model('filing_cabinet', x, 0, 2, 'north') if x == 0 else None
    S.model('office_desk', 1, 0, 1, 'north')
    S.model('pc', 1, 1, 1, 'south')
    S.model('office_chair', 1, 0, 0, 'south')
    S.model('safe', 2, 0, 2, 'north')
    S.model('breaker_panel_open', -2, 1, 1, 'east')
    S.model('security_camera_on_seen', 0, 3, 0, 'south')
    S.light((-0.5, 1.6, 1.4), (0.6, 0.9, 0.7), 1.2, 1.4)
    S.light((1.5, 1.2, 0.6), (0.5, 0.6, 1.0), 0.6, 1.0)
    eye = (2.2, 1.62, -2.4)
    tgt = (-0.6, 1.1, 2.0)
    return pair(S.render(eye, tgt), S.render(eye, tgt, night=True, exposure=1.3), 'scene_office')


def scene_road():
    """The curve: guardrail with the 献花, chevrons, the curve mirror, utility poles, the blinking signal."""
    S = Scene()
    for x in range(-8, 12):
        for z in range(-6, 8):
            t = 'hitori:block/asphalt'
            if z == 1:
                t = 'hitori:block/road_line'
            if z >= 5:
                t = 'hitori:block/rice_paddy'
            S.cube(x, -1, z, t)
            if z == 4:
                S.cube(x, -1, z, 'hitori:block/curb', up='hitori:block/curb_top')
    for x in range(-8, 12):
        S.model('guardrail', x, 0, 4, 'south')
    S.model('flower_offering', 2, 0, 3, 'north')
    for x in (-4, 0, 4, 8):
        S.model('chevron_sign', x, 0, 5, 'north')
    S.model('curve_mirror', 6, 0, 5, 'north')
    for y in range(0, 7):
        S.model('utility_pole_pole' if y < 6 else 'utility_pole_top', -6, y, 6, 'east')
    for y in range(0, 5):
        S.model('street_lamp_pole', 10, y, 6, 'north')
    S.model('traffic_light_lit', 10, 5, 5, 'north')
    S.light((10.5, 5.5, 5.0), (1.0, 0.75, 0.2), 1.2, 3.0)
    S.light((2.5, 1.6, -3.0), (0.9, 0.9, 1.0), 1.0, 6.0)        # the player's flashlight-ish
    eye = (1.5, 1.62, -4.5)
    tgt = (2.5, 0.8, 4.0)
    return pair(S.render(eye, tgt), S.render(eye, tgt, night=True, exposure=1.4, fog=((0.04, 0.05, 0.07), 26.0)),
                'scene_road')


def vanilla_door(half_tex, hinge='left'):
    """minecraft:block/door_bottom_left / _right geometry (closed, facing east -> panel at x 0..3) with the given
    texture: the west face shows the texture as is for hinge=left (mirrored for hinge=right)."""
    t = 'hitori:block/' + half_tex
    left = hinge == 'left'
    return {'textures': {'b': t, 'particle': t}, 'elements': [{
        'from': [0, 0, 0], 'to': [3, 16, 16],
        'faces': {'down': {'uv': [13, 0, 16, 16], 'texture': '#b'},
                  'up': {'uv': [13, 0, 16, 16], 'texture': '#b'},
                  'north': {'uv': [3, 0, 0, 16], 'texture': '#b'},
                  'south': {'uv': [0, 0, 3, 16], 'texture': '#b'},
                  'west': {'uv': [0, 0, 16, 16] if left else [16, 0, 0, 16], 'texture': '#b'},
                  'east': {'uv': [16, 0, 0, 16] if left else [0, 0, 16, 16], 'texture': '#b'}}}]}


def scene_doors():
    """The vanilla-style doors in a corridor wall (seen from the corridor side), the auto door panels and the kitchen
    swing doors as the block entity renderers would draw them."""
    S = Scene()
    room_shell(S, -6, 6, -2, 1, floor='hitori:block/kitchen_floor', wall='hitori:block/wallpaper',
               lower='hitori:block/wainscot', ceiling='hitori:block/ceiling_tile', open_sides=('north',))
    doors = ['staff_door', 'office_door', 'restroom_door_m', 'restroom_door_w', 'back_door', 'fridge_door', 'stall_door',
             'stall_door_occupied']
    extra = []
    for i, d in enumerate(doors):
        x = -6 + i * 1.6
        for half, y in (('bottom', 0), ('top', 1)):
            m = vanilla_door(f'{d}_{half}')
            # facing east, closed: panel at x 0..3 of its block, front (west face) toward -x; turn it to face north
            extra.append((m, RR.place(0, 0, 0, 'north') @ np.eye(4), (x, y, 1.0)))
    S.extra = extra
    S.light((0.0, 3.0, -1.0), (1.0, 0.95, 0.85), 2.0, 5.0)
    eye = (-0.5, 1.62, -3.6)
    tgt = (-0.5, 1.0, 1.5)
    cv = RR.LitCanvas(1280, 540, R.look_at(eye, tgt), fov=75, bg=(16, 18, 24))
    for (x, y, z), (d, mode) in S.cubes.items():
        for f in FACES:
            dx, dy, dz = NB[f]
            if (x + dx, y + dy, z + dz) in S.cubes:
                continue
            pts = np.array(R._face_corners((0, 0, 0), (16, 16, 16), f), float) / 16 + [x, y, z]
            uv = R._uv_corners(R._default_uv((0, 0, 0), (16, 16, 16), f))
            cv.quad(pts, uv, RR.tex(d[f]), shade=R.FACE_SHADE[f], mode=mode, double=False)
    for i, d in enumerate(doors):
        x = -6 + i * 1.6
        for half, y in (('bottom', 0), ('top', 1)):
            m = vanilla_door(f'{d}_{half}')
            # rotate the east-facing door (panel at x 0..3, texture readable on its west face) so its readable face
            # looks north toward the camera: rotate by -90 about y around the block centre
            M = RR.place(x, y, 0.8, 'west') if False else RR.place(x, y, 0.8, 'north')
            rot = R.affine(None, [x + 0.5, y + 0.5, 0.8 + 0.5]) @ R.affine(R.rot_y(-math.pi / 2)) @ \
                R.affine(None, [-0.5, -0.5, -0.5])
            RR.draw_model(cv, m, rot)
    # auto door panels (16x48 drawn on 1x3 quads) and a swing door (16x32 on 1x2)
    for k, x in enumerate((5.0, 6.0)):
        pts = np.array([(x + 1, 3, 1.4), (x, 3, 1.4), (x, 0, 1.4), (x + 1, 0, 1.4)], float)
        cv.quad(pts, R._uv_corners([0, 0, 16, 16]), RR.tex('hitori:block/auto_door_panel'), shade=0.9,
                mode='translucent')
    pts = np.array([(4.6, 2, 1.2), (3.6, 2, 1.2), (3.6, 0, 1.2), (4.6, 0, 1.2)], float)
    cv.quad(pts, R._uv_corners([0, 0, 16, 16]), RR.tex('hitori:block/swing_door'), shade=0.9, mode='translucent')
    cv.finish()
    return save(np.clip(cv.color, 0, 255), 'scene_doors')


def scene_finder():
    """The camera finder HUD (finder + 霊感計 + a developed instant photo with a red name) over the booth at night."""
    from PIL import Image as I
    from common import TEX_ROOT
    import scipy.ndimage as nd
    booth = scene_booth_night()
    bg = I.fromarray(booth.astype(np.uint8)).convert('RGBA').resize((1024, 576))
    fi = I.open(os.path.join(TEX_ROOT, 'gui/finder.png')).convert('RGBA').resize((1024, 576), I.NEAREST)
    bg.alpha_composite(fi)
    # what the game draws on top (client/Finder.java at GUI scale 2): face frame corners, focus ring, meter, film count
    from PIL import ImageDraw as D
    w, h, gs = 1024, 576, 2
    ph = int(h * 0.92)
    pw = int(ph * 4 / 3)
    px_, py_ = (w - pw) // 2, (h - ph) // 2
    fx, fy = int(pw * 0.1), int(ph * 0.1)
    dr = D.Draw(bg)
    c = (232, 238, 240, 90)
    for (cx, cy, sx, sy) in ((px_ + fx, py_ + fy, 1, 1), (px_ + pw - fx, py_ + fy, -1, 1), (px_ + fx, py_ + ph - fy, 1, -1),
                             (px_ + pw - fx, py_ + ph - fy, -1, -1)):
        dr.line([(cx, cy), (cx + sx * 20, cy)], fill=c, width=2)
        dr.line([(cx, cy), (cx, cy + sy * 20)], fill=c, width=2)
    r = int(ph * 0.14)
    dr.ellipse([w // 2 - r, h // 2 - r, w // 2 + r, h // 2 + r], outline=(224, 40, 24, 232), width=2)
    meter = I.open(os.path.join(TEX_ROOT, 'gui/finder_meter.png')).convert('RGBA').resize((96 * gs, 48 * gs), I.NEAREST)
    mx, my = px_ + fx, py_ + ph - fy - 50 * gs
    bg.alpha_composite(meter, (mx, my))
    import math as _m
    ang = _m.radians(-60 + 120 * 0.72)
    pvx, pvy = mx + 48 * gs, my + 48 * gs - 6 * gs
    dr.line([(pvx, pvy), (pvx + _m.sin(ang) * 34 * gs, pvy - _m.cos(ang) * 34 * gs)], fill=(232, 48, 32, 240), width=2)
    dr.text((px_ + pw - fx - 90, py_ + ph - fy + 6), 'のこり 7枚', fill=(224, 232, 224, 230),
            font=__import__('common').font(__import__('common').FONT_SANS_JP_MED, 16))
    # the ejected photo: frame + the booth view shrunk as "the photo" + the red name on the margin
    frame_ = I.open(os.path.join(TEX_ROOT, 'gui/photo_frame.png')).convert('RGBA')
    photo = I.fromarray(booth.astype(np.uint8)).convert('RGBA').resize((112, 84))
    ph = np.asarray(photo, np.float32)
    ph[..., :3] = ph[..., :3] * 0.85 + np.array([40, 26, 10]) * 0.6 + 18
    card = I.new('RGBA', (128, 160), (0, 0, 0, 0))
    card.paste(I.fromarray(np.clip(ph, 0, 255).astype(np.uint8)), (8, 8))
    card.alpha_composite(frame_)
    name = I.open(os.path.join(TEX_ROOT, 'gui/photo_name_visitor.png')).convert('RGBA')
    card.alpha_composite(name, (0, 108))
    card = card.resize((256, 320), I.NEAREST).rotate(-6, expand=True, resample=I.BICUBIC)
    bg.alpha_composite(card, (40, 200))
    out = np.asarray(bg.convert('RGB'))
    return save(out, 'scene_finder_hud')


def scene_booth_night():
    S = Scene()
    room_shell(S, -3, 6, -2, 3, open_sides=('north', 'east'))
    for x in range(-4, 8):
        S.cube(x, 0, 4, 'hitori:block/wainscot')
        for y in (1, 2, 3):
            S.cube(x, y, 4, 'hitori:block/shop_window', mode='translucent')
    for x in range(-3, 7):
        for y in (2, 3):
            S.model('window_blind', x, y, 3, 'north')
    for z in (1, 2):
        S.model('booth_seat', 0, 0, z, 'east')
        S.model('booth_seat', 3, 0, z, 'west')
        S.model('booth_partition', 0, 1, z, 'east')
        S.model('booth_partition', 3, 1, z, 'west')
        S.model('restaurant_table', 1, 0, z)
        S.model('restaurant_table', 2, 0, z)
    S.model('menu_stand', 1, 1, 2, 'east')
    S.model('call_button_lit', 2, 1, 2, 'west')
    S.model('water_glass_1', 1, 1, 1)
    S.model('pendant_lamp_lit', 1, 3, 2)
    S.light((1.5, 3.4, 2.5), (1.0, 0.72, 0.42), 2.6, 1.6)
    S.light((1.0, 2.0, 6.0), (1.0, 0.55, 0.2), 1.4, 4.0)
    return S.render((4.6, 1.62, -1.0), (1.6, 0.9, 2.2), night=True, exposure=1.25)


SCENES = {'booth': scene_booth, 'kitchen': scene_kitchen, 'register': scene_register, 'restroom': scene_restroom,
          'exterior': scene_exterior, 'staff_room': scene_back, 'office': scene_office, 'road': scene_road,
          'doors': scene_doors, 'finder': scene_finder}


def main(args):
    out = []
    for k, fn in SCENES.items():
        if args and k not in args:
            continue
        out.append(fn())
    return out


if __name__ == '__main__':
    for p in main(sys.argv[1:]):
        print(p)
