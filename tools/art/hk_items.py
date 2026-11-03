"""Item sprites (16x16, item/generated in the GUI) and the 3D held models (forge:separate_transforms: the sprite in the
GUI, a box model in the hands / on the ground / in frames) of HITORI's ritual items (DESIGN §7 Items), plus
`phone_light` (the phone with its flashlight on: the game swaps the item id to it, §9)."""
import math

import numpy as np

from common import C, ramp, pal, rng, grid, texture
from hk_kit import Atlas, Model, ITEM_MODELS, NS
from hk_lib import (blank, from_idx, noise, dl, tint, over, rect, px, hline, vline, line, frame, bevel, wear, specks,
                    disc_mask, ring_mask, bitmap, m_brushed, m_fabric, m_plastic)
import hk_mats as M
from hk_common import STEELW, CHARCOAL, solid, lit, mat, painted, disc_strips, ref


def sprite(rows, legend):
    a = blank(16, 16)
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch in legend:
                a[y, x] = C(legend[ch])
    return a


def item(name, cutout=True):
    return texture('item/' + name, size=(16, 16), cutout=cutout)


# ================================================================================================ the plush and its stages
RAB = {'K': '#4a463e', 'w': '#eeede8', 's': '#c8c4bb', 'S': '#a29e95', 'k': '#121212', 'g': '#8a8a8a',
       'p': '#d8a0a4', 'm': '#9a6a70', 'f': '#ffffff', 'F': '#dddad2', 'r': '#c41a1a', 'R': '#ff3a2a',
       'q': '#f4f2ea', 'Q': '#cfcab8', 'G': '#8f8b82', 'H': '#77736a', 'x': '#2a2622'}

PLUSH_ROWS = ['....KK....KK....',
              '...KwpK..KpwK...',
              '...KwpK..KpwK...',
              '...KwpK..KpwK...',
              '...KwsK..KswK...',
              '....KwKKKKwK....',
              '...KwwwwwwwsK...',
              '..KwwkwwwwkwsK..',
              '..KwwgwppwgwsK..',
              '..KwwwwmmwwwsK..',
              '...KKwwwwwwKK...',
              '..KwKwwwwwwKsK..',
              '..KwKwwwwwwKsK..',
              '..KKwwwwwwwwsK..',
              '..KwwsKKKKswsK..',
              '...KKK....KKK...']


@item('plush')
def plush_sprite():
    """The clean plush rabbit from the shelf: worn white pile, black button eyes, pink inner ears."""
    return sprite(PLUSH_ROWS, RAB)


@item('plush_open')
def plush_open_sprite():
    """Cut open down the belly with the scissors: the stuffing bursting out of the slit."""
    rows = list(PLUSH_ROWS)
    rows[10] = '...KKwwwwwwKK...'
    rows[11] = '..KwKwxffxwKsK..'
    rows[12] = '..KwKxfFffxKsK.f'
    rows[13] = '..KKwxffFfxwsKff'
    rows[14] = '..KwwsKffKswsKfF'
    rows[15] = '...KKK.fF..KKK..'
    a = sprite(rows, RAB)
    for (x, y) in ((14, 11), (15, 13), (13, 15)):
        px(a, x, y, '#ffffff')
    return a


@item('plush_rice')
def plush_rice_sprite():
    """Filled with rice: heavier, lumpy and greyer, the slit gaping with grains."""
    rows = list(PLUSH_ROWS)
    rows[10] = '...KKwsswwwKK...'
    rows[11] = '..KwKsxqqxsKsK..'
    rows[12] = '.KwwxqQqqQxwsK..'
    rows[13] = '.KwwsxqQqxssssK.'
    rows[14] = '..KwssKKKKsssKq.'
    rows[15] = '...KKK..q.KKK.q.'
    a = sprite(rows, RAB)
    tint(a, '#8a8676', 0.08, a[..., 3] > 0)
    return a


@item('ritual_doll')
def ritual_doll_sprite():
    """Sewn shut with red thread and the rest wound round and round its body; a loose end of thread trailing."""
    rows = ['....KK....KK....',
            '...KwpK..KpwK...',
            '...KspK..KpsK...',
            '...KwsK..KswK...',
            '....KwKKKKwK....',
            '...KwwwwwwwsK...',
            '..KwwkwwwwkwsK..',
            '..KrRrrrrrrRrK..',
            '..KwwwwmmwwwsK..',
            '...KKrrrrrrKK...',
            '..KwKwRwwRwKsK..',
            '..KrrrrrrrrrrK..',
            '..KKwwRrrRwwsK..',
            '..KrrrrrrrrrrK.r',
            '..KwwsKKKKswsKr.',
            '...KKK....KKKr..']
    a = sprite(rows, RAB)
    tint(a, '#7a7668', 0.1, (a[..., 3] > 0) & (np.abs(a[..., 0] - a[..., 1]) < 30))
    return a


# ================================================================================================ tools
@item('scissors')
def scissors_sprite():
    """Sewing scissors (裁ちばさみ): long silver blades, black handles."""
    L = {'K': '#1a1a1c', 'h': '#3a3a40', 's': '#dfe3e5', 'S': '#a8aeb2', 'd': '#6a7074', 'b': '#e8b84a'}
    rows = ['................',
            '..............K.',
            '.............KsK',
            '............KsSK',
            '...........KsSK.',
            '..........KsSK..',
            '.........KsSKK..',
            '........KsSKdK..',
            '..KK...KsbKdK...',
            '.KhhK.KSKdSK....',
            'KhKKhKdKdSK.....',
            'KhK.KhKdSK......',
            'KhK.KhKSK.......',
            '.KhhhKKhhK......',
            '..KKK.KhKhK.....',
            '.......KKK......']
    return sprite(rows, L)


@item('rice_scoop')
def rice_scoop_sprite():
    """The rice bin's measuring cup, heaped with polished rice."""
    L = {'K': '#5a5650', 'c': '#e8e6de', 'C': '#c6c3b8', 'D': '#9a968c', 'q': '#fbfaf4', 'Q': '#dedad0',
         'l': '#8ab0c8'}
    rows = ['................',
            '................',
            '................',
            '.....KKKKK......',
            '...KKqQqqqKK....',
            '..KqqQqqQqqqK...',
            '..KKqqqQqqqKK...',
            '..KcKKKKKKKCK...',
            '..KcccclccCCKKK.',
            '..KcccclccCCKcK.',
            '..KcccclccCDKcK.',
            '..KccccccCCDKK..',
            '...KcccccCDK....',
            '...KCCCCCDDK....',
            '....KKKKKKK.....',
            '................']
    return sprite(rows, L)


@item('red_thread')
def red_thread_sprite():
    """A wooden spool of red thread with a needle stuck through the winding."""
    L = {'K': '#2a1a10', 'w': '#c8a070', 'W': '#e0bc8a', 'd': '#8a6040', 'r': '#c41a1a', 'R': '#f04a3a',
         'q': '#8a0e0e', 'n': '#e8eaec', 'N': '#9aa0a4'}
    rows = ['................',
            '...........n....',
            '....KKKKKKKnK...',
            '...KWwwwwwwnwK..',
            '...KKKKKKKKnKK..',
            '....KrRrrrrnK...',
            '....KrRrrrqnK...',
            '....KrRrrrqNK...',
            '....KrRrrrqrK...',
            '....KrRrrrqrK...',
            '....KrRrrrqrK...',
            '...KKKKKKKKKKK..',
            '...KWwwwwwwwdK..',
            '....KKKKKKKKKR..',
            '..............R.',
            '...............R']
    return sprite(rows, L)


@item('nail_clipper')
def nail_clipper_sprite():
    """A chrome nail clipper with its lever up, and three crescent clippings."""
    L = {'K': '#1e2022', 's': '#e2e6e8', 'S': '#aab0b4', 'd': '#6a7074', 'n': '#efe4d4'}
    rows = ['................',
            '................',
            '................',
            '.........KKKKK..',
            '.......KKsssSK..',
            '.....KKssSSKK...',
            '...KKsSSKKK.....',
            '..KssKKK........',
            '..KsKKKKKKKKKK..',
            '..KsssssssssSdK.',
            '..KSSSSSSSSSddK.',
            '...KKKKKKKKKKK..',
            '................',
            '..n....nn...n...',
            '...n..........n.',
            '................']
    return sprite(rows, L)


@item('knife')
def knife_sprite():
    """The kitchen knife (三徳): a wide bright blade with a grey edge bevel and a black handle with rivets."""
    L = {'K': '#141416', 'b': '#e6eaec', 'B': '#c4cacd', 'e': '#8a9296', 'h': '#2a2422', 'H': '#4a403a',
         'r': '#c8ccce', 'g': '#f8fafa'}
    rows = ['................',
            '..............KK',
            '.............KgK',
            '............KbbK',
            '...........KbbBK',
            '..........KbbBK.',
            '.........KbbBeK.',
            '........KbbBeK..',
            '.......KbbBeK...',
            '......KKBBeK....',
            '.....KHhKKK.....',
            '....KHrhK.......',
            '...KHhhK........',
            '..KHrhK.........',
            '..KhhK..........',
            '...KK...........']
    return sprite(rows, L)


@item('salt')
def salt_sprite():
    """The table-salt bottle (食卓塩): white plastic, a blue label band, the red shaker cap."""
    L = {'K': '#3a3a3c', 'w': '#f2f2ee', 'W': '#d4d4ce', 'b': '#2a5ac8', 'B': '#4a7ae8', 'r': '#c8281c',
         'R': '#e84a3a', 'd': '#8a1a12', 'q': '#ffffff'}
    rows = ['................',
            '......KKKK......',
            '.....KRRrrK.....',
            '.....KRrrdK.....',
            '.....KKKKKK.....',
            '....KwqwwwWK....',
            '....KwwwwwWK....',
            '....KbBbbbbK....',
            '....KBbbbbbK....',
            '....KbbbbbdK....',
            '....KwwwwwWK....',
            '....KqwwwwWK....',
            '....KwwwwWWK....',
            '....KwwwwWWK....',
            '.....KKKKKK.....',
            '................']
    return sprite(rows, L)


def _glass_rows(fill):
    """A drinking glass; fill 'water' (clear) or 'salt' (cloudy, crystals on the bottom)."""
    L = {'K': '#5a6a70', 'g': '#d8e8ec', 'G': '#a8c0c8', 'h': '#f4fbfc', 'w': '#8ab4c8', 'W': '#6a98b0',
         'c': '#e4e8e6', 'C': '#c8d0ce', 'x': '#ffffff'}
    if fill == 'water':
        body = ['....KKKKKKKK....',
                '....KhggggGK....',
                '....KhggggGK....',
                '....KwwwwwWK....',
                '....KhwwwwWK....',
                '....KhwwwwWK....',
                '....KwwwwwWK....',
                '....KwwwwWWK....',
                '....KwwwwWWK....',
                '....KgggggGK....',
                '.....KKKKKK.....']
    else:
        body = ['....KKKKKKKK....',
                '....KhggggGK....',
                '....KhggggGK....',
                '....KcccccCK....',
                '....KhcccxCK....',
                '....KhccccCK....',
                '....KcxcccCK....',
                '....KccccCCK....',
                '....KcxcxCCK....',
                '....KxxxxxGK....',
                '.....KKKKKK.....']
    rows = ['................'] * 3 + body + ['................'] * 2
    return rows, L


@item('cup_water')
def cup_water_sprite():
    rows, L = _glass_rows('water')
    a = sprite(rows, L)
    return a


@item('salt_water')
def salt_water_sprite():
    rows, L = _glass_rows('salt')
    return sprite(rows, L)


@item('fixed_camera')
def fixed_camera_sprite():
    """A small black action camera on a mini tripod: the lens, the red REC LED, a little screen strip."""
    L = {'K': '#0a0a0c', 'b': '#2a2a2e', 'B': '#3a3a40', 'h': '#55555c', 'l': '#1a2a3a', 'L': '#6a9ac8',
         'r': '#ff2a1a', 'R': '#7a0a08', 's': '#8a8e92', 'S': '#c8ccce'}
    rows = ['................',
            '................',
            '....KKKKKKKKK...',
            '...KhBBBBBBrbK..',
            '...KBbKKKKbbbK..',
            '...KBKlLllKbbK..',
            '...KBKlllLKbbK..',
            '...KBKllllKbbK..',
            '...KbbKKKKbbbK..',
            '...KbbbbbbbbbK..',
            '....KKKKKKKKK...',
            '.......KsK......',
            '......KsSsK.....',
            '.....KsK.KsK....',
            '....KsK...KsK...',
            '....KK.....KK...']
    return sprite(rows, L)


@item('laptop')
def laptop_sprite():
    """The open laptop, the camera app glowing on its screen (a grey-green feed, a red REC dot)."""
    L = {'K': '#121214', 'b': '#3a3c40', 'B': '#55585c', 'h': '#7a7e84', 'g': '#5a7a6a', 'G': '#8aa898',
         'd': '#2a3a32', 'r': '#ff2a1a', 'w': '#d8dcd8', 'k': '#1e1e20'}
    rows = ['................',
            '..KKKKKKKKKKKK..',
            '..KBBBBBBBBBBK..',
            '..KBwwwwwwwwBK..',
            '..KBrgGggggdBK..',
            '..KBgGGgggddBK..',
            '..KBgggddgddBK..',
            '..KBdddddddgBK..',
            '..KBggdddgggBK..',
            '..KBBBBBBBBBBK..',
            '.KKKKKKKKKKKKKK.',
            '.KhbkbkbkbkbkhK.',
            'KhbkbkbkbkbkbbhK',
            'KbbbbbBBBBbbbbbK',
            '.KKKKKKKKKKKKKK.',
            '................']
    return sprite(rows, L)


PHONE_ROWS = ['................',
              '.....KKKKKK.....',
              '....KbbbbbbK....',
              '....KbggggbK....',
              '....KbgrGgbK....',
              '....KbgGGgbK....',
              '....KbggdgbK....',
              '....KbgddgbK....',
              '....KbddggbK....',
              '....KbggggbK....',
              '....KbwwwwbK....',
              '....KbbbbbbK....',
              '....KbbhhbbK....',
              '.....KKKKKK.....',
              '................',
              '................']


@item('phone')
def phone_sprite():
    """Your smartphone, the camera app on its screen."""
    L = {'K': '#0e0e10', 'b': '#2a2c30', 'h': '#55585c', 'g': '#5a7a6a', 'G': '#8aa898', 'd': '#2a3a32',
         'r': '#ff2a1a', 'w': '#d8dcd8'}
    return sprite(PHONE_ROWS, L)


@item('phone_light')
def phone_light_sprite():
    """The phone with its flashlight on: the torch toggle lit on the screen, a glow at the top edge."""
    L = {'K': '#0e0e10', 'b': '#2a2c30', 'h': '#55585c', 'g': '#1a1c22', 'G': '#fff2c0', 'd': '#1a1c22',
         'r': '#1a1c22', 'w': '#fff8d8', 'y': '#fff6c0', 'Y': '#ffe080'}
    rows = list(PHONE_ROWS)
    rows[0] = '......YyyY......'
    a = sprite(rows, L)
    a[0, 6:10, 3] = 170
    return a


@item('lighter')
def lighter_sprite():
    """A 100-yen disposable lighter: translucent pink body with the fuel visible, the chrome hood and wheel."""
    L = {'K': '#2a1a1e', 'p': '#e888a8', 'P': '#c86a8a', 'q': '#f8c0d0', 'f': '#b85a7a', 's': '#d8dcde',
         'S': '#9aa0a4', 'k': '#1a1a1a', 'r': '#c8281c'}
    rows = ['................',
            '......KKKK......',
            '.....KsSSsK.....',
            '.....KsKKSK.....',
            '.....KkkkkK.....',
            '.....KrrrrK.....',
            '.....KqppPK.....',
            '.....KqppPK.....',
            '.....KqppPK.....',
            '.....KpppPK.....',
            '.....KffffK.....',
            '.....KffffK.....',
            '.....KfffPK.....',
            '.....KPPPPK.....',
            '......KKKK......',
            '................']
    return sprite(rows, L)


@item('printout')
def printout_sprite():
    """The forum printout, folded once: printed lines, a pencil tick in the margin."""
    L = {'K': '#4a4a46', 'w': '#f2f1ea', 'W': '#d8d6cc', 't': '#3a3a3a', 'l': '#8a8a84', 'r': '#c8281c'}
    rows = ['................',
            '..KKKKKKKKKKK...',
            '..KwwwwwwwwwWK..',
            '..KwtttttttwWK..',
            '..KwwwwwwwwwWK..',
            '..KrwllllllwWK..',
            '..KwwlllllwwWK..',
            '..KrwllllllwWK..',
            '..KwwlllllwwWK..',
            '..KrwllllllwWK..',
            '..KwwllllwwwWK..',
            '..KwwlllllwwWK..',
            '..KWWWWWWWWWWK..',
            '..KKKKKKKKKKKK..',
            '................',
            '................']
    return sprite(rows, L)


# ================================================================================================ 3D held models
def T(x, y, z):
    return [round(x, 3), round(y, 3), round(z, 3)]


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


def hold(fp_rot=(0, 0, 0), fp_t=(0, 0, 0), fp_s=0.6, tp_rot=(0, 0, 0), tp_t=(0, 2, 0), tp_s=0.5, ground_s=0.5,
         fixed_rot=(0, 180, 0), fixed_s=0.8, head_t=(0, 6, -4)):
    return {
        'firstperson_righthand': {'rotation': T(*fp_rot), 'translation': T(*fp_t), 'scale': T(fp_s, fp_s, fp_s)},
        'firstperson_lefthand': {'rotation': T(*fp_rot), 'translation': T(*fp_t), 'scale': T(fp_s, fp_s, fp_s)},
        'thirdperson_righthand': {'rotation': T(*tp_rot), 'translation': T(*tp_t), 'scale': T(tp_s, tp_s, tp_s)},
        'thirdperson_lefthand': {'rotation': T(*tp_rot), 'translation': T(*tp_t), 'scale': T(tp_s, tp_s, tp_s)},
        'ground': {'rotation': T(0, 0, 0), 'translation': T(0, 2, 0), 'scale': T(ground_s, ground_s, ground_s)},
        'fixed': {'rotation': T(*fixed_rot), 'translation': T(0, 0, 0), 'scale': T(fixed_s, fixed_s, fixed_s)},
        'head': {'rotation': T(0, 0, 0), 'translation': T(*head_t), 'scale': T(0.6, 0.6, 0.6)},
    }


# ---- knife: blade along -z (away from the player), handle toward +z
A_KNIFE = Atlas('held/knife', kind='item')


def _blade(w, h, face):
    """Blade side: bright steel, a darker edge bevel along the bottom, a polish streak, the tip rounded off."""
    a = m_brushed(w, h, ramp(['#6a7276', '#8a9296', '#aab2b6', '#c8ced1', '#e2e6e8', '#f4f6f7'], 6), 15001 + w,
                  along='x', base=4.0)
    if face != 'h' and h >= 3:
        hline(a, 0, w - 1, h - 1, '#7a8286')
        hline(a, 0, w - 1, 0, '#d8dcde')
    return a


def knife_base():
    m = Model('_held_knife', A_KNIFE, parent=None, register=False)
    B = mat('k_blade', _blade)
    m.box([7.85, 6, 1], [8.15, 9.5, 10], B)                       # blade (3.5 tall, 9 long)
    m.box([7.85, 7.5, 0], [8.15, 9.5, 1], B)                      # the tip
    m.box([7.75, 8.3, 10], [8.25, 9.5, 10.8], M.brushed('k_bolster', STEELW, base=4.4))
    H = painted('k_handle', ramp(['#141210', '#1e1a16', '#2a241e', '#363028', '#433b31'], 5), base=2.4)
    m.box([7.4, 8.1, 10.8], [8.6, 9.7, 16], H)
    for zz in (12.2, 14.4):
        m.box([7.35, 8.7, zz], [8.65, 9.1, zz + 0.4], solid('k_rivet', '#c8ccce'))
    return m


KNIFE_DISPLAY = hold(fp_rot=(10, 8, -6), fp_t=(1.0, 2.5, -1.0), fp_s=0.8, tp_rot=(80, 0, 0), tp_t=(0, 3.0, 1.0),
                     tp_s=0.65, fixed_rot=(0, 90, 0))


# ---- laptop: open, the lid far, the screen facing the player (+z)
A_LAP = Atlas('held/laptop', kind='item')


def _lap_kb(w, h, face):
    a = blank(w, h, '#3a3c40')
    if face == 'h' and w >= 8:
        x, y = grid(w, h)
        keys = (y >= 1) & (y < h - 2) & ((x + y) % 2 == 0) & (x >= 1) & (x < w - 1)
        a[keys] = C('#1c1c1e')
        rect(a, w // 2 - 1, h - 2, w // 2 + 1, h - 2, '#2a2c30')
    return a


def laptop_base():
    m = Model('_held_laptop', A_LAP, parent=None, register=False)
    SHELL = M.brushed('hl_shell', ramp(['#2a2c30', '#34363a', '#3e4044', '#484a4e', '#525458'], 5), base=2.0)
    m.box([3, 4, 6], [13, 4.6, 13], SHELL, faces={'up': M.custom('hl_kb', _lap_kb)})
    rot = ('x', -22.5, [8, 4.6, 6])
    m.box([3, 4.6, 5.4], [13, 11.6, 6], SHELL, rot=rot)
    m.extra_tex['screen'] = ref('block/laptop_screen')
    m.box([3.5, 5.0, 6.0], [12.5, 11.2, 6.05], None, faces={'south': {'r': '#screen', 'sub': (0, 0, 16, 10)}},
          glow=('south',), rot=rot)
    return m


LAPTOP_DISPLAY = hold(fp_rot=(15, 0, 0), fp_t=(-2.5, 1.5, -1.0), fp_s=0.75, tp_rot=(60, 0, 0), tp_t=(0, 3.0, -2.0),
                      tp_s=0.55)


# ---- phone: upright slab, the screen facing the player
A_PHONE = Atlas('held/phone', kind='item')


def _phone_screen(light):
    def fn(w, h):
        """Screen (4 x 7 px at 2x: 8 x 14): the camera app (a small feed, REC, the camera buttons) or the torch."""
        a = blank(w, h, '#0c0c10')
        if light:
            rect(a, 1, 1, w - 2, h - 2, '#141820')
            rect(a, 3, 5, 4, 8, '#fff2c0')
            px(a, 3, 4, '#ffe080')
            return a
        rect(a, 1, 2, w - 2, 7, '#5a7a6a')
        rect(a, 1, 5, w - 2, 7, '#2a3a32')
        px(a, 1, 2, '#ff2a1a')
        for k, xx in enumerate((1, 3, 5)):
            rect(a, xx, 9, xx + 1, 10, '#d8dcd8' if k == 0 else '#5a5e62')
        hline(a, 2, w - 3, h - 2, '#5a5e62')
        return a
    return fn


def phone_base(light=False):
    key = 'screen_light' if light else 'screen'
    A_PHONE.add(key, 8, 14, _phone_screen(light))
    m = Model('_held_phone' + ('_light' if light else ''), A_PHONE, parent=None, register=False)
    B = painted('ph_body', ramp(['#141416', '#1c1c20', '#25252a', '#2e2e34'], 4), base=1.6)
    m.box([6, 4, 7.6], [10, 11, 8.4], B, faces={'south': key}, glow=('south',))
    m.box([8.2, 9.6, 7.5], [9.4, 10.6, 7.6], lit('ph_flash_on', '#fff6d0') if light else solid('ph_cam', '#3a3a40'),
          glow=('north',) if light else ())
    return m


PHONE_DISPLAY = hold(fp_rot=(-8, -12, 0), fp_t=(-1.0, 3.0, -1.5), fp_s=0.85, tp_rot=(70, 0, 0), tp_t=(0, 3.5, 0.5),
                     tp_s=0.55)


# ---- salt water: a glass of cloudy water
A_GLASS = Atlas('held/salt_water', kind='item')


def _cloudy(w, h, face):
    """Salt water: faintly milky, a few undissolved crystals catching the light (translucent)."""
    x, y = grid(w, h)
    a = blank(w, h, C('#c8d6d8', 110))
    r = rng(15301 + w + h)
    a[r.random((h, w)) < 0.1] = C('#f4f8f8', 200)
    if face == 'h':
        a[...] = C('#d4dfe0', 130)
        a[r.random((h, w)) < 0.15] = C('#ffffff', 190)
    else:
        hline(a, 0, w - 1, 0, '#e8f0f0')
        a[0, :, 3] = 170
    return a


def _glass_wall(w, h, face):
    a = blank(w, h, C('#d0e2e4', 52))
    if face != 'h':
        vline(a, 0, 0, h - 1, '#f0fafa')
        a[:, 0, 3] = 120
        hline(a, 0, w - 1, 0, '#f4fcfc')
        a[0, :, 3] = 150
    return a


def salt_water_base():
    m = Model('_held_salt_water', A_GLASS, parent=None, register=False)
    GL = mat('hg_glass', _glass_wall)
    W = mat('hg_cloudy', _cloudy)
    disc_strips(m, 8, 8, 2.6, 2.0, 9.6, GL, top_spec=None, bottom=M.glass('hg_base', '#d8ece8', 140), step=1.3)
    disc_strips(m, 8, 8, 2.3, 2.3, 7.6, W, top_spec=W, step=1.15)
    return m


GLASS_DISPLAY = hold(fp_rot=(0, -15, 0), fp_t=(1.0, 3.5, -1.0), fp_s=0.7, tp_rot=(70, 0, 0), tp_t=(0, 3.0, 0.5),
                     tp_s=0.6)


# ---- the ritual doll (3D): the rabbit, greyed, wound with red thread
A_DOLL = Atlas('held/ritual_doll', kind='item')
DOLLW = ramp(['#7a766e', '#928e86', '#aaa69e', '#bcb8b0', '#ccc8c0', '#d8d5ce'], 6)


def _doll_pile(w, h, face):
    r = rng(15401 + w * 7 + h)
    a = from_idx(3.4 + noise(w, h, 15402 + w + h, beta=0.8, wrap=False) * 0.45, DOLLW)
    dl(a, 0.03, r.random((h, w)) < 0.15)
    if face != 'h' and h >= 4:
        # the sewn seam with red stitches down the middle
        vline(a, w // 2, 0, h - 1, '#8a1010')
        for yy in range(0, h, 2):
            px(a, w // 2 - 1, yy, '#c41a1a')
            px(a, w // 2 + 1, yy + 1 if yy + 1 < h else yy, '#c41a1a')
    return a


def _doll_face(w, h, face):
    a = from_idx(3.6 + noise(w, h, 15411, beta=0.8, wrap=False) * 0.4, DOLLW)
    if w >= 4:
        px(a, 1, 1, '#101010')
        px(a, w - 2, 1, '#101010')
        px(a, w // 2, 2, '#b07a80')
        px(a, w // 2, 3, '#7a4a50')
    return a


def ritual_doll_base():
    m = Model('_held_doll', A_DOLL, parent=None, register=False)
    P = mat('doll_pile', _doll_pile)
    F = mat('doll_face', _doll_face)
    RED = solid('doll_thread', '#c41a1a')
    PINK = solid('doll_pink', '#b8868a')
    m.box([5.5, 3, 6], [10.5, 8, 10], P)                                          # body (rice-heavy)
    m.box([5.8, 8, 6.2], [10.2, 12, 9.8], P, faces={'north': F})                  # head
    m.box([6.0, 12, 7.4], [7.2, 15, 8.4], P, faces={'north': PINK}, rot=('z', 22.5, [6.6, 12, 8]))   # ears, one bent
    m.box([8.8, 12, 7.4], [10.0, 14.4, 8.4], P, faces={'north': PINK})
    m.box([4.6, 5, 7], [5.5, 7.6, 8.6], P)                                        # arms
    m.box([10.5, 5, 7], [11.4, 7.6, 8.6], P)
    m.box([6.0, 1.5, 6.5], [7.6, 3, 8.4], P)                                      # legs
    m.box([8.4, 1.5, 6.5], [10.0, 3, 8.4], P)
    # the red thread wound round and round: bands slightly larger than the body, one across the face
    for (y0, dz) in ((3.6, 0.0), (4.9, 0.1), (6.2, 0.0), (7.3, 0.1)):
        m.box([5.4, y0, 5.9 - dz], [10.6, y0 + 0.35, 10.1 + dz], RED)
    m.box([5.7, 9.2, 6.1], [10.3, 9.5, 9.9], RED)
    m.box([7.8, 3, 5.85], [8.2, 12, 5.95], RED, only=('north',))                  # the seam thread over the front
    # the loose end trailing from the hip
    m.box([10.4, 3.0, 6.0], [10.7, 3.3, 8.0], RED)
    m.box([10.4, 0.5, 5.8], [10.7, 3.0, 6.1], RED)
    return m


DOLL_DISPLAY = hold(fp_rot=(0, 180, 0), fp_t=(0.0, 3.0, 0.0), fp_s=0.85, tp_rot=(0, 180, 0), tp_t=(0, 2.5, 1.5),
                    tp_s=0.6, fixed_rot=(0, 0, 0))


# ---- lighter
A_LIGHT = Atlas('held/lighter', kind='item')


def _lighter_body(w, h, face):
    a = blank(w, h, C('#e888a8', 220))
    if face != 'h' and h >= 4:
        rect(a, 0, h // 2, w - 1, h - 1, '#b85a7a')            # the fuel level
        vline(a, 0, 0, h - 1, '#f8c0d0')
    return a


def lighter_base():
    m = Model('_held_lighter', A_LIGHT, parent=None, register=False)
    m.box([7, 4, 7.4], [9.2, 9.5, 8.6], mat('lt_body', _lighter_body))
    m.box([7, 9.5, 7.4], [9.2, 10.2, 8.6], solid('lt_band', '#c8281c'))
    m.box([7.1, 10.2, 7.5], [9.1, 11.6, 8.5], M.brushed('lt_hood', STEELW, base=4.2))
    m.box([7.6, 10.6, 7.3], [8.6, 11.4, 8.7], solid('lt_wheel', '#5a5e62'))
    return m


LIGHTER_DISPLAY = hold(fp_rot=(0, -20, 0), fp_t=(0.0, 3.5, -0.5), fp_s=0.9, tp_rot=(70, 0, 0), tp_t=(0, 3.0, 0.5),
                       tp_s=0.55)


# ---- fixed camera: action cam on its mini tripod, the lens away from the player (-z)
A_CAM = Atlas('held/fixed_camera', kind='item')


def _cam_front(w, h):
    a = blank(w, h, '#1e1e22')
    if w >= 4 and h >= 3:
        x, y = grid(w, h)
        d = np.hypot(x + 0.5 - w * 0.4, y + 0.5 - h / 2)
        a[d < 1.3] = C('#2a3a4a')
        a[d < 0.6] = C('#7aaad8')
        px(a, w - 1, 0, '#ff2a1a')
    return a


def fixed_camera_base():
    A_CAM.add('front', 5, 4, _cam_front)
    m = Model('_held_camera', A_CAM, parent=None, register=False)
    BODY = painted('cam_body', ramp(['#121214', '#1a1a1e', '#232328', '#2c2c32'], 4), base=1.6)
    m.box([5.5, 7, 7], [10.5, 11, 10], BODY, faces={'north': 'front'})
    m.box([9.0, 11, 8.0], [10.0, 11.4, 9.0], lit('cam_rec', '#ff2a1a'), glow=('up', 'north'))
    m.box([6.5, 7.2, 6.4], [8.5, 9.2, 7.0], solid('cam_lens', '#2a3a4a'))
    TRI = M.brushed('cam_tri', STEELW, base=3.6)
    m.box([7.5, 5.5, 8.0], [8.5, 7, 9.0], TRI)
    for ang, (ax, oz) in ((22.5, ('x', 8.5)), (-22.5, ('x', 8.5))):
        m.box([7.6, 1.5, 8.1], [8.4, 5.5, 8.9], TRI, rot=(ax, ang, [8, 5.5, oz]))
    m.box([7.6, 1.5, 8.1], [8.4, 5.5, 8.9], TRI, rot=('z', 22.5, [8, 5.5, 8.5]))
    return m


CAM_DISPLAY = hold(fp_rot=(0, 0, 0), fp_t=(0.0, 2.0, 0.0), fp_s=0.85, tp_rot=(70, 0, 0), tp_t=(0, 3.0, 0.5),
                   tp_s=0.55)


ITEM_MODELS['knife'] = separate(knife_base(), KNIFE_DISPLAY, 'knife')
ITEM_MODELS['laptop'] = separate(laptop_base(), LAPTOP_DISPLAY, 'laptop')
ITEM_MODELS['phone'] = separate(phone_base(False), PHONE_DISPLAY, 'phone')
ITEM_MODELS['phone_light'] = separate(phone_base(True), PHONE_DISPLAY, 'phone_light')
ITEM_MODELS['salt_water'] = separate(salt_water_base(), GLASS_DISPLAY, 'salt_water', render_type='translucent')
ITEM_MODELS['ritual_doll'] = separate(ritual_doll_base(), DOLL_DISPLAY, 'ritual_doll')
ITEM_MODELS['lighter'] = separate(lighter_base(), LIGHTER_DISPLAY, 'lighter', render_type='translucent')
ITEM_MODELS['fixed_camera'] = separate(fixed_camera_base(), CAM_DISPLAY, 'fixed_camera')
for _n in ('plush', 'plush_open', 'plush_rice', 'scissors', 'rice_scoop', 'red_thread', 'nail_clipper', 'salt',
           'cup_water', 'printout'):
    ITEM_MODELS[_n] = {'parent': 'minecraft:item/generated', 'textures': {'layer0': f'{NS}:item/{_n}'}}
