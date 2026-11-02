#!/usr/bin/env python3
"""Writes the custom-shaped prop block models and the 3D held item models of SOUNAN.

  models/block/<prop>.json   (facing north; blockstates rotate them by `facing`)  <- PROPS below
  models/item/<item>.json    (forge:separate_transforms: 3D model in hand, 16x16 sprite in the GUI) <- s_held.py

UV rectangles come from s_props.REG so the models always match the painted texture atlases.
usage: python3 tools/art/gen_models.py [--preview]
"""
import json
import os
import sys
sys.dont_write_bytecode = True
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from common import MODEL_ROOT  # noqa: E402
import s_props  # noqa: E402
from s_props import REG  # noqa: E402

NS = 'sounan'
MODELS = {}       # name -> dict  (block models)
ITEM_MODELS = {}  # name -> dict


def T(name):
    return f'{NS}:block/{name}'


def uv(tex, reg, sub=None, flip=False):
    u0, v0, u1, v1 = REG[tex][reg]
    if sub:  # sub-rectangle in region-local pixels
        a, b, c, d = sub
        u0, v0, u1, v1 = u0 + a, v0 + b, u0 + c, v0 + d
    if flip:
        u0, u1 = u1, u0
    return [u0, v0, u1, v1]


def F(ref, uvr, rot=0, glow=False, cull=None):
    f = {'texture': ref, 'uv': [round(c, 4) for c in uvr]}
    if rot:
        f['rotation'] = rot
    if glow:
        f['forge_data'] = {'block_light': 15, 'sky_light': 15}
    if cull:
        f['cullface'] = cull
    return f


def E(fr, to, faces, rot=None, shade=True, name=None):
    e = {'from': [round(c, 4) for c in fr], 'to': [round(c, 4) for c in to], 'faces': faces}
    if rot:
        axis, angle, origin = rot
        e['rotation'] = {'origin': origin, 'axis': axis, 'angle': angle}
    if not shade:
        e['shade'] = False
    if name:
        e['name'] = name
    return e


def model(name, tex_map, elements, particle, cutout=False, ao=True):
    m = {}
    if cutout:
        m['render_type'] = 'minecraft:cutout'
    if not ao:
        m['ambientocclusion'] = False
    m['textures'] = dict(particle=particle, **tex_map)
    m['elements'] = elements
    MODELS[name] = m
    return m


# ================================================================================================ props
def lost_bag(open_=False):
    t = 'lost_bag'
    els = []
    els.append(E([3, 0, 5], [13, 6, 11], {
        'north': F('#t', uv(t, 'body')), 'south': F('#t', uv(t, 'body', flip=True)), 'up': F('#t', uv(t, 'body')),
        'down': F('#t', uv(t, 'bottom')), 'east': F('#t', uv(t, 'end')),
        'west': F('#t', uv(t, 'lining' if open_ else 'end'))}))
    els.append(E([4, 0.5, 3.5], [12, 5.5, 5], {
        'north': F('#t', uv(t, 'pocket')), 'up': F('#t', uv(t, 'pocket_side', (0, 0, 2, 1.5), )),
        'down': F('#t', uv(t, 'pocket_side', (0, 0, 2, 1.5))),
        'east': F('#t', uv(t, 'pocket_side')), 'west': F('#t', uv(t, 'pocket_side'))}))
    for x0 in (5, 9.5):
        els.append(E([x0, 0.5, 11], [x0 + 1.5, 5.5, 12], {
            'south': F('#t', uv(t, 'strap')), 'east': F('#t', uv(t, 'strap')), 'west': F('#t', uv(t, 'strap')),
            'up': F('#t', uv(t, 'strap', (0, 0, 2, 1)))}))
    if not open_:
        els.append(E([1.5, 0.5, 5.5], [3, 5.5, 10.5], {
            'west': F('#t', uv(t, 'lid')), 'north': F('#t', uv(t, 'pocket_side')), 'south': F('#t', uv(t, 'pocket_side')),
            'up': F('#t', uv(t, 'pocket_side', (0, 0, 2, 1))), 'down': F('#t', uv(t, 'pocket_side', (0, 0, 2, 1)))}))
    else:
        # lid flung open and lying flat beside the opening; the dark inside shows
        els.append(E([-2.5, 0, 5.5], [2.5, 1.5, 10.5], {
            'up': F('#t', uv(t, 'lid'), rot=90), 'north': F('#t', uv(t, 'pocket_side', (0, 0, 2, 1.5))),
            'south': F('#t', uv(t, 'pocket_side', (0, 0, 2, 1.5))), 'west': F('#t', uv(t, 'pocket_side', (0, 0, 2, 1.5))),
            'down': F('#t', uv(t, 'lid'))}, rot=('z', 22.5, [2.5, 1.5, 8])))
        # drawstring collar
        els.append(E([2, 0.5, 5.5], [3, 6, 10.5], {
            'north': F('#t', uv(t, 'pocket_side')), 'south': F('#t', uv(t, 'pocket_side')),
            'up': F('#t', uv(t, 'pocket_side', (0, 0, 2, 1))), 'west': F('#t', uv(t, 'lining'))}))
    return model('lost_bag_open' if open_ else 'lost_bag', {'t': T(t)}, els, T(t))


lost_bag(False)
lost_bag(True)

# ------------------------------------------------------------------------------------------------ paper decals
model('memo', {'t': T('memo')}, [
    E([1.5, 0.05, 1.5], [14.5, 0.3, 14.5], {'up': F('#t', [0, 0, 16, 16]), 'down': F('#t', [0, 0, 16, 16])},
      rot=('y', 22.5, [8, 0, 8]))], T('memo'), cutout=True, ao=False)

model('memo_wall', {'t': T('memo_wall'), 'n': T('rope_anchor')}, [
    E([2, 1, 15.6], [14, 15, 15.6], {'north': F('#t', uv('memo_wall', 'sheet')),
                                     'south': F('#t', uv('memo_wall', 'sheet', flip=True))}),
    E([7.5, 12.5, 15.0], [8.5, 13.5, 15.6], {'north': F('#n', [0, 0, 1, 1]), 'east': F('#n', [0, 0, 1, 1]),
                                             'west': F('#n', [0, 0, 1, 1]), 'up': F('#n', [0, 0, 1, 1])}),
], T('memo_wall'), cutout=True, ao=False)

# ------------------------------------------------------------------------------------------------ logbook
t = 'logbook'
model('logbook', {'t': T(t)}, [
    E([2.5, 0, 3.5], [13.5, 0.5, 12.5], {'up': F('#t', uv(t, 'cover')), 'down': F('#t', uv(t, 'cover')),
                                         'north': F('#t', uv(t, 'cover_edge')), 'south': F('#t', uv(t, 'cover_edge')),
                                         'east': F('#t', uv(t, 'cover_edge')), 'west': F('#t', uv(t, 'cover_edge'))}),
    E([3, 0.5, 4], [8, 1.75, 12], {'up': F('#t', uv(t, 'page_l')), 'north': F('#t', uv(t, 'edges', (0, 0, 5, 1.25))),
                                   'south': F('#t', uv(t, 'edges', (0, 0, 5, 1.25))), 'west': F('#t', uv(t, 'edges', (0, 0, 8, 1.25)))}),
    E([8, 0.5, 4], [13, 1.25, 12], {'up': F('#t', uv(t, 'page_r')), 'north': F('#t', uv(t, 'edges', (5, 0, 10, 0.75))),
                                    'south': F('#t', uv(t, 'edges', (5, 0, 10, 0.75))), 'east': F('#t', uv(t, 'edges', (0, 0, 8, 0.75)))}),
    E([13.9, 0, 4.5], [14.9, 0.8, 12.5], {'up': F('#t', uv(t, 'pencil'), rot=90), 'east': F('#t', uv(t, 'pencil')),
                                          'west': F('#t', uv(t, 'pencil')), 'north': F('#t', uv(t, 'pencil', (0, 0, 1, 1))),
                                          'south': F('#t', uv(t, 'pencil', (7, 0, 8, 1)))},
      rot=('y', -22.5, [14.4, 0, 8.5])),
], T(t))

# ------------------------------------------------------------------------------------------------ notice board
t = 'notice_board'
post = lambda x0: E([x0, 0, 9], [x0 + 1.5, 9, 10.5], {d: F('#t', uv(t, 'post')) for d in ('north', 'south', 'east', 'west')})
model('notice_board', {'t': T(t)}, [
    post(2), post(12.5),
    E([1, 4, 10], [15, 13, 11], {'north': F('#t', uv(t, 'board')), 'south': F('#t', uv(t, 'back')),
                                 'up': F('#t', uv(t, 'edge', (0, 0, 14, 1))), 'down': F('#t', uv(t, 'edge', (0, 0, 14, 1))),
                                 'east': F('#t', uv(t, 'edge', (0, 0, 1, 9)), rot=0), 'west': F('#t', uv(t, 'edge', (0, 0, 1, 9)))}),
    E([0, 13, 8], [16, 14, 12.5], {'up': F('#t', uv(t, 'roof')), 'down': F('#t', uv(t, 'roof')),
                                   'north': F('#t', uv(t, 'edge')), 'south': F('#t', uv(t, 'edge')),
                                   'east': F('#t', uv(t, 'edge', (0, 0, 4.5, 1))), 'west': F('#t', uv(t, 'edge', (0, 0, 4.5, 1)))},
      rot=('x', -22.5, [8, 13.5, 10.5])),
], T(t))

# ------------------------------------------------------------------------------------------------ signpost
t = 'signpost'
board = lambda y0, reg: E([1, y0, 6], [15, y0 + 3.5, 7], {
    'north': F('#t', uv(t, reg)), 'south': F('#t', uv(t, reg, flip=True)),
    'up': F('#t', uv(t, 'board_edge')), 'down': F('#t', uv(t, 'board_edge'))})
model('signpost', {'t': T(t)}, [
    E([7, 0, 7], [9, 15, 9], {d: F('#t', uv(t, 'post')) for d in ('north', 'south', 'east', 'west')}),
    E([6.5, 15, 6.5], [9.5, 16, 9.5], {d: F('#t', uv(t, 'cap', (0, 0, 3, 1))) for d in ('north', 'south', 'east', 'west')}
      | {'up': F('#t', uv(t, 'cap', (0, 1, 3, 4))), 'down': F('#t', uv(t, 'cap', (0, 1, 3, 4)))}),
    board(11, 'board_l'), board(6.5, 'board_r'),
], T(t), cutout=True)

# ------------------------------------------------------------------------------------------------ jizo
t = 'jizo'
model('jizo', {'t': T(t)}, [
    E([4, 0, 5], [12, 2, 11], {'up': F('#t', uv(t, 'base_top')), 'down': F('#t', uv(t, 'base_top')),
                               'north': F('#t', uv(t, 'base_side')), 'south': F('#t', uv(t, 'base_side')),
                               'east': F('#t', uv(t, 'base_side', (0, 0, 6, 2))), 'west': F('#t', uv(t, 'base_side', (0, 0, 6, 2)))}),
    E([5, 2, 6], [11, 9, 10], {'north': F('#t', uv(t, 'robe_front')), 'south': F('#t', uv(t, 'robe', flip=True)),
                               'east': F('#t', uv(t, 'robe')), 'west': F('#t', uv(t, 'robe')),
                               'up': F('#t', uv(t, 'robe', (0, 0, 4, 4)))}),
    E([5.5, 9, 6.5], [10.5, 12.5, 9.5], {'north': F('#t', uv(t, 'face', (0, 0, 5, 3.5))),
                                         'south': F('#t', uv(t, 'head', (0, 0, 5, 3.5))),
                                         'east': F('#t', uv(t, 'head', (0, 0, 3, 3.5))), 'west': F('#t', uv(t, 'head', (2, 0, 5, 3.5)))}),
    E([5.25, 11.5, 6.25], [10.75, 14, 9.75], {'north': F('#t', uv(t, 'cap')), 'south': F('#t', uv(t, 'cap')),
                                              'east': F('#t', uv(t, 'cap', (0, 0, 3.5, 3))), 'west': F('#t', uv(t, 'cap', (2.5, 0, 6, 3))),
                                              'up': F('#t', uv(t, 'cap_top')), 'down': F('#t', uv(t, 'cap_top'))}),
    E([7.5, 14, 7.5], [8.5, 14.75, 8.5], {d: F('#t', uv(t, 'cap', (0, 0, 1, 1))) for d in ('north', 'south', 'east', 'west', 'up')}),
    E([5.25, 4.5, 5.6], [10.75, 9, 5.6], {'north': F('#t', uv(t, 'bib')), 'south': F('#t', uv(t, 'bib', flip=True))}),
], T(t), cutout=True)


# ------------------------------------------------------------------------------------------------ hokora
def hokora(offered):
    t = 'hokora'
    els = [
        E([2, 0, 3], [14, 3, 13], {'north': F('#t', uv(t, 'base')), 'south': F('#t', uv(t, 'base')),
                                   'east': F('#t', uv(t, 'base', (0, 0, 10, 3))), 'west': F('#t', uv(t, 'base', (2, 0, 12, 3))),
                                   'up': F('#t', uv(t, 'base_top')), 'down': F('#t', uv(t, 'base_top'))}),
        E([4, 3, 5], [12, 10, 11], {'north': F('#t', uv(t, 'front')), 'south': F('#t', uv(t, 'side', flip=True)),
                                    'east': F('#t', uv(t, 'side')), 'west': F('#t', uv(t, 'side'))}),
        # roof: two slopes meeting at a ridge parallel to the front, eaves overhanging
        E([2.5, 10, 2.5], [13.5, 11, 8.2], {'up': F('#t', uv(t, 'roof', (0, 0, 11, 3))), 'down': F('#t', uv(t, 'roof', (0, 0, 11, 3))),
                                            'north': F('#t', uv(t, 'roof_edge', (0, 0, 11, 1))),
                                            'east': F('#t', uv(t, 'roof_edge', (0, 0, 5.7, 1))), 'west': F('#t', uv(t, 'roof_edge', (0, 0, 5.7, 1)))},
          rot=('x', -22.5, [8, 11, 8])),
        E([2.5, 10, 7.8], [13.5, 11, 13.5], {'up': F('#t', uv(t, 'roof', (5, 0, 16, 3))), 'down': F('#t', uv(t, 'roof', (0, 0, 11, 3))),
                                             'south': F('#t', uv(t, 'roof_edge', (0, 0, 11, 1))),
                                             'east': F('#t', uv(t, 'roof_edge', (0, 0, 5.7, 1))), 'west': F('#t', uv(t, 'roof_edge', (0, 0, 5.7, 1)))},
          rot=('x', 22.5, [8, 11, 8])),
        E([2.5, 11.4, 7.5], [13.5, 12.3, 8.5], {d: F('#t', uv(t, 'roof_edge', (0, 0, 11, 1))) for d in ('north', 'south', 'up')}
          | {'east': F('#t', uv(t, 'roof_edge', (0, 0, 1, 1))), 'west': F('#t', uv(t, 'roof_edge', (0, 0, 1, 1)))}),
        # posts at the front corners
        E([4, 3, 4.5], [5, 10, 5.5], {d: F('#t', uv(t, 'wood', (0, 0, 1, 7))) for d in ('north', 'south', 'east', 'west')}),
        E([11, 3, 4.5], [12, 10, 5.5], {d: F('#t', uv(t, 'wood', (1, 0, 2, 7))) for d in ('north', 'south', 'east', 'west')}),
    ]
    if offered:
        els += [
            E([4.6, 3, 3.4], [6.4, 6.5, 5.2], {d: F('#t', uv(t, 'bottle', (0, 1, 2, 5))) for d in ('north', 'south', 'east', 'west')}
              | {'up': F('#t', uv(t, 'bottle', (0, 1, 2, 2)))}),
            E([5.1, 6.5, 3.9], [5.9, 7.6, 4.7], {d: F('#t', uv(t, 'bottle', (0, 0, 1, 1))) for d in ('north', 'south', 'east', 'west', 'up')}),
            E([10.0, 3, 3.6], [11.0, 5.4, 4.6], {d: F('#t', uv(t, 'candle')) for d in ('north', 'south', 'east', 'west')}
              | {'up': F('#t', uv(t, 'candle', (0, 0, 1, 1)))}),
            E([10.5, 5.4, 3.6], [10.5, 7.4, 4.6], {'east': F('#t', uv(t, 'flame'), glow=True), 'west': F('#t', uv(t, 'flame'), glow=True)}, shade=False),
            E([10.0, 5.4, 4.1], [11.0, 7.4, 4.1], {'north': F('#t', uv(t, 'flame'), glow=True), 'south': F('#t', uv(t, 'flame'), glow=True)}, shade=False),
        ]
    return model('hokora_offered' if offered else 'hokora', {'t': T(t)}, els, T(t), cutout=True)


hokora(False)
hokora(True)

# ------------------------------------------------------------------------------------------------ wara ningyo
model('wara_ningyo', {'t': T('wara_ningyo'), 'n': T('rope_anchor')}, [
    E([4, 3.5, 15.5], [12, 11.5, 15.5], {'north': F('#t', [0, 0, 16, 16]), 'south': F('#t', [16, 0, 0, 16])}),
    E([7.6, 6.9, 14.6], [8.4, 7.7, 15.5], {d: F('#n', [0, 0, 1, 1]) for d in ('north', 'east', 'west', 'up', 'down')}),
], T('wara_ningyo'), cutout=True, ao=False)


# ------------------------------------------------------------------------------------------------ rope anchor
def rope_anchor(roped):
    t = 'rope_anchor'
    tilt = ('x', 22.5, [8, 0, 8])
    iron = {d: F('#t', uv(t, 'iron', (0, 0, 1, 4))) for d in ('north', 'south', 'east', 'west')}
    ring = lambda w, h: {d: F('#t', uv(t, 'ring', (0, 0, w, h))) for d in ('north', 'south', 'east', 'west', 'up', 'down')}
    els = [
        E([5.5, 0, 5.5], [10.5, 0.4, 10.5], {'up': F('#t', uv(t, 'plate', (1, 0, 6, 5))), 'north': F('#t', uv(t, 'plate', (0, 0, 5, 0.5))),
                                             'south': F('#t', uv(t, 'plate', (0, 0, 5, 0.5))), 'east': F('#t', uv(t, 'plate', (0, 0, 5, 0.5))),
                                             'west': F('#t', uv(t, 'plate', (0, 0, 5, 0.5)))}),
        E([7.5, 0, 7.5], [8.5, 4.2, 8.5], iron, rot=tilt),
        E([6.5, 4.0, 7.6], [9.5, 4.8, 8.4], ring(3, 1), rot=tilt),
        E([6.5, 6.9, 7.6], [9.5, 7.7, 8.4], ring(3, 1), rot=tilt),
        E([6.5, 4.8, 7.6], [7.3, 6.9, 8.4], ring(1, 2), rot=tilt),
        E([8.7, 4.8, 7.6], [9.5, 6.9, 8.4], ring(1, 2), rot=tilt),
    ]
    if roped:
        rope = {d: F('#t', uv(t, 'rope', (0, 0, 11, 1))) for d in ('east', 'west', 'up', 'down')} | \
            {'north': F('#t', uv(t, 'rope', (0, 0, 1, 1))), 'south': F('#t', uv(t, 'rope', (0, 0, 1, 1)))}
        els += [
            E([6.9, 4.3, 7.2], [9.1, 6.3, 8.8], {d: F('#t', uv(t, 'knot', (0, 0, 2, 2))) for d in ('north', 'south', 'east', 'west', 'up', 'down')}, rot=tilt),
            E([7.5, 4.6, 0], [8.5, 5.6, 10.4], rope, rot=('x', -22.5, [8, 5.1, 10.4])),
            E([7.5, -0.5, 0], [8.5, 1.6, 1], {d: F('#t', uv(t, 'rope', (0, 0, 1, 2))) for d in ('north', 'south', 'east', 'west')}),
        ]
    return model('rope_anchor_roped' if roped else 'rope_anchor', {'t': T(t)}, els, T(t))


rope_anchor(False)
rope_anchor(True)

# ------------------------------------------------------------------------------------------------ climbing rope
model('climbing_rope', {'t': T('climbing_rope')}, [
    E([0, 0, 14.5], [16, 16, 14.5], {'north': F('#t', [0, 0, 16, 16]), 'south': F('#t', [16, 0, 0, 16])}),
    E([8, 0, 12.5], [8, 16, 16], {'east': F('#t', [6, 0, 9.5, 16]), 'west': F('#t', [9.5, 0, 6, 16])}),
], T('climbing_rope'), cutout=True, ao=False)

# ------------------------------------------------------------------------------------------------ public phone
t = 'public_phone'
model('public_phone', {'t': T(t)}, [
    E([1, 7, 7], [15, 8, 16], {'up': F('#t', uv(t, 'shelf')), 'down': F('#t', uv(t, 'shelf')),
                               'north': F('#t', uv(t, 'shelf', (0, 0, 8, 1))), 'east': F('#t', uv(t, 'shelf', (0, 0, 8, 1))),
                               'west': F('#t', uv(t, 'shelf', (0, 0, 8, 1)))}),
    E([2, 3.5, 14.5], [3, 7, 16], {d: F('#t', uv(t, 'shelf', (0, 1, 2, 4))) for d in ('north', 'east', 'west', 'down')}),
    E([13, 3.5, 14.5], [14, 7, 16], {d: F('#t', uv(t, 'shelf', (0, 1, 2, 4))) for d in ('north', 'east', 'west', 'down')}),
    E([4, 8, 9], [12, 16, 15], {'north': F('#t', uv(t, 'front', (0, 0, 8, 8))), 'south': F('#t', uv(t, 'side', (0, 0, 6, 8))),
                                'east': F('#t', uv(t, 'side', (0, 0, 6, 8))), 'west': F('#t', uv(t, 'side', (0, 0, 6, 8))),
                                'up': F('#t', uv(t, 'top'))}),
    E([12, 9.5, 9.5], [13.5, 16, 11.5], {'north': F('#t', uv(t, 'handset', (0, 0, 2, 6.5))), 'south': F('#t', uv(t, 'handset', (0, 0, 2, 6.5))),
                                         'east': F('#t', uv(t, 'handset', (0, 0, 2, 6.5))), 'west': F('#t', uv(t, 'handset', (0, 0, 2, 6.5))),
                                         'up': F('#t', uv(t, 'handset', (0, 0, 2, 2))), 'down': F('#t', uv(t, 'handset', (0, 0, 2, 2)))}),
    E([12.3, 8, 10], [13.2, 9.5, 13], {d: F('#t', uv(t, 'cord')) for d in ('north', 'south', 'east', 'west', 'up')}),
], T(t))

# ------------------------------------------------------------------------------------------------ bus stop
t = 'bus_stop'
model('bus_stop', {'t': T(t)}, [
    E([5, 0, 5], [11, 2, 11], {'up': F('#t', uv(t, 'base')), 'north': F('#t', uv(t, 'base', (0, 4, 4, 6))),
                               'south': F('#t', uv(t, 'base', (0, 4, 4, 6))), 'east': F('#t', uv(t, 'base', (0, 4, 4, 6))),
                               'west': F('#t', uv(t, 'base', (0, 4, 4, 6)))}),
    E([7.25, 2, 7.25], [8.75, 16, 8.75], {d: F('#t', uv(t, 'pole', (0, 0, 2, 14))) for d in ('north', 'south', 'east', 'west')}
      | {'up': F('#t', uv(t, 'pole', (0, 0, 2, 2)))}),
    E([3, 6, 6.75], [13, 16, 6.75], {'north': F('#t', uv(t, 'sign')), 'south': F('#t', uv(t, 'sign_back', (0, 0, 6, 6)))}),
    E([6, 2.5, 7.2], [10, 5.5, 7.2], {'north': F('#t', uv(t, 'plate', (0, 0, 4, 3))), 'south': F('#t', uv(t, 'plate', (0, 3, 4, 6)))}),
], T(t), cutout=True)


# ------------------------------------------------------------------------------------------------ tunnel lamp
def tunnel_lamp(lit):
    t = 'tunnel_lamp_lit' if lit else 'tunnel_lamp'
    r = 'tunnel_lamp'
    els = [
        E([3, 6, 15], [13, 12, 16], {'north': F('#t', uv(r, 'plate')), 'east': F('#t', uv(r, 'plate', (0, 0, 1, 6))),
                                     'west': F('#t', uv(r, 'plate', (0, 0, 1, 6))), 'up': F('#t', uv(r, 'plate', (0, 0, 8, 1))),
                                     'down': F('#t', uv(r, 'plate', (0, 0, 8, 1)))}),
        E([3.5, 7, 11], [12.5, 11, 15], {'north': F('#t', uv(r, 'glass', (0, 0, 9, 4)), glow=lit),
                                         'up': F('#t', uv(r, 'housing', (0, 0, 9, 4))), 'down': F('#t', uv(r, 'housing', (0, 0, 9, 4))),
                                         'east': F('#t', uv(r, 'housing_end')), 'west': F('#t', uv(r, 'housing_end'))}, shade=not lit),
        E([7.5, 11, 14.8], [8.5, 16, 15.8], {d: F('#t', uv(r, 'conduit', (0, 0, 1, 5))) for d in ('north', 'east', 'west')}),
    ]
    return model(t, {'t': T(t)}, els, T(t))


tunnel_lamp(False)
tunnel_lamp(True)

# ------------------------------------------------------------------------------------------------ guardrail
t = 'guardrail'
model('guardrail', {'t': T(t)}, [
    E([0, 6, 7], [16, 12, 8.5], {'north': F('#t', uv(t, 'rail')), 'south': F('#t', uv(t, 'rail', flip=True)),
                                 'up': F('#t', uv(t, 'rail_back')), 'down': F('#t', uv(t, 'rail_back'))}),
    E([7, 0, 9.5], [9, 11, 11.5], {d: F('#t', uv(t, 'post', (0, 0, 2, 8))) for d in ('north', 'south')}
      | {d: F('#t', uv(t, 'post_side', (0, 0, 2, 8))) for d in ('east', 'west')} | {'up': F('#t', uv(t, 'post', (0, 0, 2, 2)))}),
    E([7.25, 7.5, 8.5], [8.75, 10.5, 9.5], {d: F('#t', uv(t, 'spacer')) for d in ('east', 'west', 'up', 'down')}),
], T(t))


# ------------------------------------------------------------------------------------------------ key hook
def key_hook(has_key):
    t = 'key_hook' if has_key else 'key_hook_empty'
    els = [
        E([4, 7, 15], [12, 13, 16], {'north': F('#t', uv('key_hook', 'board')), 'up': F('#t', uv('key_hook', 'board_edge')),
                                     'down': F('#t', uv('key_hook', 'board_edge')), 'east': F('#t', uv('key_hook', 'board_edge', (0, 0, 6, 1))),
                                     'west': F('#t', uv('key_hook', 'board_edge', (0, 0, 6, 1)))}),
        E([7.5, 10, 14], [8.5, 11, 15], {d: F('#t', uv('key_hook', 'hook', (0, 0, 1, 1))) for d in ('north', 'east', 'west', 'up', 'down')}),
    ]
    if has_key:
        els += [
            E([6.5, 3.5, 14.4], [9.5, 10.5, 14.4], {'north': F('#t', uv('key_hook', 'key')), 'south': F('#t', uv('key_hook', 'key', flip=True))}),
            E([8.6, 2.0, 14.3], [11.6, 7.0, 14.3], {'north': F('#t', uv('key_hook', 'tag')), 'south': F('#t', uv('key_hook', 'tag', flip=True))}),
        ]
    return model(t, {'t': T(t)}, els, T(t), cutout=True)


key_hook(True)
key_hook(False)

# ------------------------------------------------------------------------------------------------ car door
t = 'car_door'
model('car_door', {'t': T(t)}, [
    E([0, 0, 0], [16, 16, 16], {'north': F('#t', [0, 0, 16, 16], cull='north'), 'south': F('#t', [1, 7, 9, 9], cull='south'),
                                'east': F('#t', [1, 7, 9, 9], cull='east'), 'west': F('#t', [1, 7, 9, 9], cull='west'),
                                'up': F('#t', [1, 7, 9, 9], cull='up'), 'down': F('#t', [1, 13, 15, 15], cull='down')}),
    E([10, 9, -0.6], [14, 10, 0], {'north': F('#t', [10, 9, 14, 10]), 'up': F('#t', [10, 10, 14, 11]),
                                   'down': F('#t', [10, 9, 14, 10]), 'east': F('#t', [13, 9, 14, 10]), 'west': F('#t', [10, 9, 11, 10])}),
], T(t))

# ------------------------------------------------------------------------------------------------ surface decals
model('blue_tarp', {'t': T('blue_tarp')}, [
    E([0, 0, 0], [16, 1, 16], {'up': F('#t', [0, 0, 16, 16]), 'down': F('#t', [0, 0, 16, 16], cull='down'),
                               'north': F('#t', [0, 15, 16, 16]), 'south': F('#t', [0, 15, 16, 16]),
                               'east': F('#t', [0, 15, 16, 16]), 'west': F('#t', [0, 15, 16, 16])})], T('blue_tarp'))

model('fallen_leaves', {'t': T('fallen_leaves')}, [
    E([0, 0.05, 0], [16, 0.3, 16], {'up': F('#t', [0, 0, 16, 16]), 'down': F('#t', [0, 0, 16, 16])})],
    T('fallen_leaves'), cutout=True, ao=False)


# ================================================================================================ write
def write_all():
    import s_held
    ITEM_MODELS.update(s_held.item_models())
    out = []
    for name, m in sorted(MODELS.items()):
        p = os.path.join(MODEL_ROOT, 'block', name + '.json')
        os.makedirs(os.path.dirname(p), exist_ok=True)
        with open(p, 'w', encoding='utf-8') as f:
            json.dump(m, f, indent=2, ensure_ascii=False)
            f.write('\n')
        out.append(p)
    for name, m in sorted(ITEM_MODELS.items()):
        p = os.path.join(MODEL_ROOT, 'item', name + '.json')
        os.makedirs(os.path.dirname(p), exist_ok=True)
        with open(p, 'w', encoding='utf-8') as f:
            json.dump(m, f, indent=2, ensure_ascii=False)
            f.write('\n')
        out.append(p)
    print(f'wrote {len(MODELS)} block models, {len(ITEM_MODELS)} item models')
    return out


if __name__ == '__main__':
    write_all()
    if '--preview' in sys.argv:
        import model_preview
        model_preview.main()
