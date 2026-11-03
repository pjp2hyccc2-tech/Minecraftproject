"""Item sprites (16x16, item/generated in the GUI) and the 3D held models (forge:separate_transforms) of HITORI:
the 霊写機 instant camera (normal / raised to the finder) and the flashlight (off / on). Also flat sprites for the door
block items (the world's item models may point at item/<door>)."""
import math

import numpy as np

from common import C, ramp, pal, rng, grid, texture
from rs_kit import Atlas, Model, ITEM_MODELS, NS
from rs_lib import (blank, from_idx, noise, dl, tint, over, rect, px, hline, vline, line, frame, bevel, wear, specks,
                    disc_mask, ring_mask, bitmap, sun_small, m_brushed, m_fabric, m_plastic, CHROME, STEEL, ALU)
import rs_mats as M
from rs_waiting import solid, lit


def sprite(rows, legend):
    h = len(rows)
    w = max(len(r) for r in rows)
    a = blank(16, 16)
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch in legend:
                a[y, x] = C(legend[ch])
    return a


def item(name, cutout=True):
    return texture('item/' + name, size=(16, 16), cutout=cutout)


CAM = {'K': '#160e08', 'b': '#5a3420', 'B': '#3e2414', 'h': '#7c4c2c', 'c': '#e2e5e6', 'C': '#a2a8ac', 'D': '#5c6266',
       'L': '#0e141a', 'l': '#8ab0c8', 'g': '#26323e', 'w': '#f6f6ee', 'y': '#e6dca8', 'r': '#d02a1c', 's': '#050505',
       'k': '#2a2422', 'm': '#121212'}


@item('reisha_camera')
def reisha_camera_sprite():
    rows = ['................',
            '..KKKKK.........',
            '..KwwyK.....KK..',
            '..KwyyK....KrrK.',
            '.KKkkkKKKKKKCCKK',
            '.KcccccccccccccK',
            '.KhbbbbKKKKbbhbK',
            '.KbbbbKCccCKbbBK',
            '.KbhbKCglgDCKbBK',
            '.KbbKCgLlLgCKbBK',
            '.KbbKCgLLLgDKbBK',
            '.KbbbKCgggDKbbBK',
            '.KbbbbKDDDKbbbBK',
            '.KDDDDDsssDDDDDK',
            '.KKKKKKKKKKKKKKK',
            '................']
    return sprite(rows, CAM)


@item('reisha_camera_finder')
def reisha_camera_finder_sprite():
    # the camera raised: seen from behind, the eyepiece and the red "ready" lamp lit
    rows = ['................',
            '.........KKKKK..',
            '..KK.....KwwyK..',
            '.KrrK....KyywK..',
            'KKCCKKKKKKkkkKK.',
            'KcccccccccccccK.',
            'KbhbKKKKKbbbbhK.',
            'KbbKmmmmKbbbbbK.',
            'KbbKmllmKbhbbbK.',
            'KbbKmmmmKbbbbbK.',
            'KbbbKKKKbbbbbbK.',
            'KbbbbbbbbbbrbbK.',
            'KbbbbbbbbbbbbbK.',
            'KDDDDDDDDDDDDDK.',
            'KKKKKKKKKKKKKKK.',
            '................']
    return sprite(rows, CAM)


@item('film_pack')
def film_pack_sprite():
    L = {'K': '#0e0e12', 'd': '#22242c', 'D': '#30343e', 'e': '#464c58', 'w': '#f2f0e8', 'W': '#d8d6cc',
         'r': '#b8241a', 'R': '#e04a32', 'y': '#e8c060'}
    rows = ['................',
            '................',
            '...KKKKKKKKKKK..',
            '..KeeeeeeeeeeK..',
            '..KDwwwwwwwwDK..',
            '..KDWWWWWWWWDK..',
            '..KDDDDDDDDDDK..',
            '..KDrrrrrrrrDK..',
            '..KDrRyRRyRrDK..',
            '..KDrrrrrrrrDK..',
            '..KDDDDDDDDDDK..',
            '..KdDDDDDDDDdK..',
            '..KdddddddddeK..',
            '...KKKKKKKKKK...',
            '................',
            '................']
    return sprite(rows, L)


@item('evidence_photo')
def evidence_photo_sprite():
    L = {'K': '#3a3830', 'w': '#f4f2ea', 'W': '#dcdad0', 'd': '#1e2620', 'D': '#2c3a30', 'f': '#7a8a80', 'F': '#a8b8ac',
         'r': '#c01c14', 'x': '#0e1410'}
    rows = ['................',
            '..KKKKKKKKKKK...',
            '..KwwwwwwwwwK...',
            '..KwdddDdddwK...',
            '..KwdDfFfDdwK...',
            '..KwdfxFxfdwK...',
            '..KwdDfffDdwK...',
            '..KwddDfDddwK...',
            '..KwdddddddwK...',
            '..KwwwwwwwwwK...',
            '..KwrrwrwrrwK...',
            '..KwwrrwrrwwK...',
            '..KWwwwwwwwWK...',
            '..KKKKKKKKKKK...',
            '................',
            '................']
    return sprite(rows, L)


FL = {'K': '#0a0a0c', 'b': '#26262a', 'B': '#3a3a40', 'h': '#5a5a62', 's': '#a8acb0', 'S': '#d8dcde',
      'g': '#9ab0b8', 'G': '#d8e8ee', 'y': '#fff2b0', 'Y': '#ffd860', 'o': '#ffb030'}


@item('flashlight')
def flashlight_sprite():
    rows = ['................',
            '...........KKK..',
            '..........KSgsK.',
            '.........KsgGgK.',
            '........KbsggsK.',
            '.......KbhbsKK..',
            '......KbhbBK....',
            '.....KbhbBK.....',
            '....KbhsBK......',
            '...KbhbBK.......',
            '..KbhbBK........',
            '.KshbBK.........',
            '.KSsBK..........',
            '..KKK...........',
            '................',
            '................']
    return sprite(rows, FL)


@item('flashlight_on')
def flashlight_on_sprite():
    rows = ['.............o..',
            '...........KKKoY',
            '..........KSYYKY',
            '.........KsYyYKo',
            '........KbsYYsK.',
            '.......KbhbsKK..',
            '......KbhbBK....',
            '.....KbhbBK.....',
            '....KbhsBK......',
            '...KbhbBK.......',
            '..KbhbBK........',
            '.KshbBK.........',
            '.KSsBK..........',
            '..KKK...........',
            '................',
            '................']
    return sprite(rows, FL)


@item('ofuda')
def ofuda_sprite():
    L = {'K': '#3a2a18', 'p': '#efe6cc', 'P': '#d8ccaa', 'r': '#b8241a', 'R': '#e04a32', 'k': '#141010'}
    rows = ['.........KKK....',
            '........KpprK...',
            '........KrRrK...',
            '.......KprrpK...',
            '.......KpkppK...',
            '......KppkkK....',
            '......KpkpPK....',
            '.....KppkpK.....',
            '.....KpkkpK.....',
            '....KppkpPK.....',
            '....KpkppK......',
            '...KpPkpPK......',
            '...KpprpK.......',
            '...KrRrPK.......',
            '...KKKKK........',
            '................']
    return sprite(rows, L)


KEY = {'K': '#1a1408', 'g': '#c8a040', 'G': '#e8c868', 'd': '#8a6a20', 'o': '#e8721f', 'O': '#f69a3c', 's': '#c9cdd0',
       'S': '#eef0f2', 'D': '#7a7e82', 'b': '#2a5ab0', 'B': '#4a7ad8', 'w': '#f4f2ea', 'y': '#fcc03a'}


@item('shop_key')
def shop_key_sprite():
    rows = ['................',
            '..KKKK..........',
            '.KOOOOK.........',
            '.KOyyOK.........',
            '.KOyyOK.........',
            '.KoOOoK.........',
            '..KooKK.........',
            '...KKGGKK.......',
            '....KgdGgK......',
            '....KGKKgK......',
            '.....KggGgK.....',
            '......KKKggK....',
            '.........KgdKK..',
            '..........KgggK.',
            '...........KgdK.',
            '............KK..']
    return sprite(rows, KEY)


@item('office_key')
def office_key_sprite():
    rows = ['................',
            '..KKKKK.........',
            '.KBBBBBK........',
            '.KBwwwBK........',
            '.KBBBBBK........',
            '.KbbbbbK........',
            '..KbbbK.........',
            '...KSSKK........',
            '....KsDSK.......',
            '....KSKKsK......',
            '.....KssSsK.....',
            '......KKKssK....',
            '.........KsDKK..',
            '..........KsssK.',
            '...........KsDK.',
            '............KK..']
    return sprite(rows, KEY)


# ---------------------------------------------------------------------------------------------- door item sprites
def door_sprite(body, dark, trim, extra=None):
    a = blank(16, 16)
    rect(a, 3, 0, 12, 15, dark)
    rect(a, 4, 1, 11, 15, body)
    vline(a, 4, 1, 15, trim)
    px(a, 10, 8, '#d8dcde')
    px(a, 10, 9, '#9aa0a4')
    if extra:
        extra(a)
    return a


@item('staff_door')
def staff_door_item():
    return door_sprite('#7c8286', '#2a2e30', '#9aa0a4', lambda a: (rect(a, 5, 3, 10, 5, '#ecebe4'), hline(a, 6, 9, 4, '#1a1a1a')))


@item('office_door')
def office_door_item():
    return door_sprite('#5e3c23', '#22140a', '#7a5032', lambda a: (rect(a, 6, 2, 9, 4, '#1a2024'), rect(a, 5, 6, 10, 7, '#f0eee6')))


@item('restroom_door_m')
def restroom_door_m_item():
    return door_sprite('#7f5434', '#2a1a0e', '#93653f', lambda a: (rect(a, 5, 3, 10, 8, '#f2f1ec'), rect(a, 7, 4, 8, 7, '#1d4fa8')))


@item('restroom_door_w')
def restroom_door_w_item():
    return door_sprite('#7f5434', '#2a1a0e', '#93653f', lambda a: (rect(a, 5, 3, 10, 8, '#f2f1ec'), rect(a, 7, 4, 8, 7, '#c22a2a')))


@item('back_door')
def back_door_item():
    return door_sprite('#3e4842', '#141a16', '#4c5750', lambda a: (rect(a, 5, 8, 10, 8, '#a5a9ab'), px(a, 6, 12, '#6a3418')))


@item('fridge_door')
def fridge_door_item():
    return door_sprite('#dcdcd4', '#6a6a64', '#ececE6'[:7], lambda a: (rect(a, 9, 6, 10, 10, '#9ca2a6'), rect(a, 5, 3, 8, 4, '#2d5aa0')))


@item('stall_door')
def stall_door_item():
    return door_sprite('#b0a186', '#4a4030', '#c1b296', lambda a: (rect(a, 5, 4, 10, 5, '#2b62b8')))


@item('auto_door')
def auto_door_item():
    a = blank(16, 16)
    rect(a, 1, 0, 14, 15, '#80868b')
    rect(a, 2, 1, 7, 15, C('#7d969a', 120))
    rect(a, 8, 1, 13, 15, C('#7d969a', 120))
    vline(a, 7, 1, 15, '#b3b8bb')
    vline(a, 8, 1, 15, '#9aa0a4')
    over(a, sun_small(7), 4, 5)
    return a


@item('swing_door')
def swing_door_item():
    a = blank(16, 16)
    rect(a, 3, 0, 12, 15, '#331f12')
    rect(a, 4, 1, 11, 15, '#53351f')
    a[disc_mask(16, 16, 8, 4.5, 2.6)] = C('#a9aeb1')
    a[disc_mask(16, 16, 8, 4.5, 1.7)] = C('#5e7a80')
    rect(a, 4, 12, 11, 15, '#b9bdbf')
    return a


# ================================================================================================ 3D held models
A_CAMSKIN = Atlas('held/reisha_camera', kind='item')
A_FLSKIN = Atlas('held/flashlight', kind='item')


def _leather(w, h, face):
    a = m_fabric(w, h, ramp(['#2a160c', '#3e2414', '#52301c', '#663e24', '#7a4c2e'], 5), 14001 + w * 3 + h, base=2.4)
    n = noise(w, h, 14002 + w, beta=0.3, wrap=False)
    dl(a, 0.025, n > 0.8)
    return bevel(a, 0.05, -0.06)


def _cam_front(w, h):
    a = _leather(w, h, 'v')
    hline(a, 0, w - 1, h - 1, '#5c6266')
    rect(a, 3, h - 2, w - 4, h - 2, '#050505')          # the film slot the photo comes out of
    hline(a, 0, w - 1, 0, '#d8dcde')
    return a


def _cam_back(w, h):
    a = _leather(w, h, 'v')
    hline(a, 0, w - 1, 0, '#d8dcde')
    rect(a, 1, 1, 3, 3, '#5c6266')                       # eyepiece
    rect(a, 2, 2, 2, 2, '#050608')
    frame(a, 5, 4, w - 2, h - 2, '#3e2414')              # film door
    px(a, w - 3, 2, '#c8281c')                            # ready lamp
    return a


def _lens_face(w, h):
    a = blank(w, h, '#a2a8ac')
    ring = ring_mask(w, h, w / 2, h / 2, w / 2 - 1.1, w / 2)
    a[ring] = C('#e2e5e6')
    gl = disc_mask(w, h, w / 2, h / 2, w / 2 - 1.1)
    a[gl] = C('#141c24')
    a[disc_mask(w, h, w / 2 - 0.8, h / 2 - 0.8, 0.9)] = C('#8ab0c8')
    return a


def _flash_front(w, h):
    a = blank(w, h, '#1e1e20')
    rect(a, 1, 1, w - 2, h - 2, '#f4f4ec')
    for x in range(1, w - 1, 2):
        vline(a, x, 1, h - 2, '#dcd8c4')
    return a


def camera_base(finder=False):
    if 'front' not in A_CAMSKIN.regions:
        A_CAMSKIN.add('front', 10, 7, _cam_front)
        A_CAMSKIN.add('back', 10, 7, _cam_back)
        A_CAMSKIN.add('lens', 6, 6, _lens_face)
        A_CAMSKIN.add('flash', 4, 2, _flash_front)
    m = Model('_held_camera' + ('_f' if finder else ''), A_CAMSKIN, parent=None, register=False)
    LEATHER = M.custom('leather', _leather)
    CHR = M.brushed('cam_chrome', CHROME, base=2.6)
    m.box([3, 4, 5], [13, 11, 11], LEATHER, faces={'north': 'front', 'south': 'back', 'down': CHR})
    m.box([3, 11, 5.5], [13, 11.5, 10.5], CHR)                                       # top plate
    m.box([5.5, 5, 3], [10.5, 10, 5], CHR, faces={'north': 'lens'})                   # lens barrel
    m.box([3.5, 11.5, 6], [7, 13.5, 9], M.custom('flash_body', lambda w, h, f: bevel(blank(w, h, '#222224'))),
          faces={'north': 'flash'})                                                   # flash unit
    m.box([10, 9, 4.5], [12.5, 10.5, 5], M.custom('finder_win', lambda w, h, f: blank(w, h, '#2a3a48')))
    m.box([11, 11.5, 7], [12, 12, 8], solid('shutter', '#c8281c'))                    # shutter button
    for x0 in (2.5, 13):
        m.box([x0, 9, 7.5], [x0 + 0.5, 10, 8.5], CHR)                                 # strap lugs
    return m


def flashlight_base(on):
    if 'lens' not in A_FLSKIN.regions:
        A_FLSKIN.add('lens', 4, 4, lambda w, h: _fl_lens(w, h, False))
        A_FLSKIN.add('lens_on', 4, 4, lambda w, h: _fl_lens(w, h, True))
    m = Model('_held_fl', A_FLSKIN, parent=None, register=False)
    BODY = M.custom('fl_body', lambda w, h, f: _fl_body(w, h, f))
    HEAD = M.brushed('fl_head', ALU, base=3.4)
    m.box([5.5, 5.5, 1], [10.5, 10.5, 4], HEAD, faces={'north': 'lens_on' if on else 'lens'},
          glow=('north',) if on else ())
    m.box([6, 6, 4], [10, 10, 5.5], HEAD)
    m.box([6.5, 6.5, 5.5], [9.5, 9.5, 14], BODY)
    m.box([6.25, 6.25, 14], [9.75, 9.75, 15.5], HEAD)
    m.box([7.4, 9.5, 6.6], [8.6, 10, 8.2], solid('fl_switch', '#c8281c' if on else '#3a1a14'))
    return m


def _fl_body(w, h, face):
    a = blank(w, h, '#26262a')
    for k in range(0, max(w, h), 2):
        if w > h:
            vline(a, k, 0, h - 1, '#3a3a40')
        else:
            hline(a, 0, w - 1, k, '#3a3a40')
    return bevel(a, 0.05, -0.06)


def _fl_lens(w, h, on):
    a = blank(w, h, '#fff6c8' if on else '#9ab0b8')
    if not on:
        px(a, 1, 1, '#d8e8ee')
    else:
        px(a, w // 2, h // 2, '#ffffff')
    return a


def T(x, y, z):
    return [round(x, 3), round(y, 3), round(z, 3)]


CAM_DISPLAY = {
    'firstperson_righthand': {'rotation': T(5, 20, 0), 'translation': T(1.0, 3.5, 0.0), 'scale': T(0.55, 0.55, 0.55)},
    'firstperson_lefthand': {'rotation': T(5, 20, 0), 'translation': T(1.0, 3.5, 0.0), 'scale': T(0.55, 0.55, 0.55)},
    'thirdperson_righthand': {'rotation': T(75, 0, 0), 'translation': T(0, 2.0, -1.0), 'scale': T(0.6, 0.6, 0.6)},
    'thirdperson_lefthand': {'rotation': T(75, 0, 0), 'translation': T(0, 2.0, -1.0), 'scale': T(0.6, 0.6, 0.6)},
    'ground': {'rotation': T(0, 0, 0), 'translation': T(0, 2, 0), 'scale': T(0.5, 0.5, 0.5)},
    'fixed': {'rotation': T(0, 180, 0), 'translation': T(0, 0, 0), 'scale': T(0.8, 0.8, 0.8)},
    'head': {'rotation': T(0, 0, 0), 'translation': T(0, 6, -6), 'scale': T(0.8, 0.8, 0.8)},
}
# raised to the eye: the finder HUD covers the screen, the camera itself is just below the view
FINDER_DISPLAY = dict(CAM_DISPLAY)
FINDER_DISPLAY.update({
    'firstperson_righthand': {'rotation': T(-10, 0, 0), 'translation': T(-3.0, -12.0, 4.0), 'scale': T(0.85, 0.85, 0.85)},
    'firstperson_lefthand': {'rotation': T(-10, 0, 0), 'translation': T(-3.0, -12.0, 4.0), 'scale': T(0.85, 0.85, 0.85)},
    'thirdperson_righthand': {'rotation': T(0, 0, 0), 'translation': T(0, 4.0, -2.5), 'scale': T(0.6, 0.6, 0.6)},
    'thirdperson_lefthand': {'rotation': T(0, 0, 0), 'translation': T(0, 4.0, -2.5), 'scale': T(0.6, 0.6, 0.6)},
})
FL_DISPLAY = {
    'firstperson_righthand': {'rotation': T(4, 8, 0), 'translation': T(-1.0, 4.0, 1.0), 'scale': T(0.6, 0.6, 0.6)},
    'firstperson_lefthand': {'rotation': T(4, 8, 0), 'translation': T(-1.0, 4.0, 1.0), 'scale': T(0.6, 0.6, 0.6)},
    'thirdperson_righthand': {'rotation': T(80, 0, 0), 'translation': T(0, 3.0, -0.5), 'scale': T(0.55, 0.55, 0.55)},
    'thirdperson_lefthand': {'rotation': T(80, 0, 0), 'translation': T(0, 3.0, -0.5), 'scale': T(0.55, 0.55, 0.55)},
    'ground': {'rotation': T(0, 0, 0), 'translation': T(0, 2, 0), 'scale': T(0.5, 0.5, 0.5)},
    'fixed': {'rotation': T(0, 90, 0), 'translation': T(0, 0, 0), 'scale': T(0.75, 0.75, 0.75)},
    'head': {'rotation': T(0, 0, 0), 'translation': T(0, 10, -4), 'scale': T(0.6, 0.6, 0.6)},
}


def separate(base_model, display, sprite_name, render_type=None):
    def build():
        b = base_model.to_json()
        b.pop('parent', None)
        b['display'] = display
        if render_type:
            b['render_type'] = f'minecraft:{render_type}'
        return {
            'loader': 'forge:separate_transforms',
            'base': b,
            'perspectives': {
                'gui': {'parent': 'minecraft:item/generated', 'textures': {'layer0': f'{NS}:item/{sprite_name}'}},
            },
        }
    return build


ITEM_MODELS['reisha_camera'] = separate(camera_base(False), CAM_DISPLAY, 'reisha_camera')
ITEM_MODELS['reisha_camera_finder'] = separate(camera_base(True), FINDER_DISPLAY, 'reisha_camera_finder')
ITEM_MODELS['flashlight'] = separate(flashlight_base(False), FL_DISPLAY, 'flashlight')
ITEM_MODELS['flashlight_on'] = separate(flashlight_base(True), FL_DISPLAY, 'flashlight_on')
for _n in ('film_pack', 'evidence_photo', 'ofuda', 'shop_key', 'office_key'):
    ITEM_MODELS[_n] = {'parent': 'minecraft:item/generated', 'textures': {'layer0': f'{NS}:item/{_n}'}}
