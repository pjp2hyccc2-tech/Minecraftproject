#!/usr/bin/env python3
"""Generates block/item models, blockstates, loot tables, recipes, tags and lang files for MINECRAFT BESIEGE.

Part models are authored in canonical orientation (part faces UP, attachment face at y=0, twist axis = X) and
rotated by blockstate x/y rotations exactly like com.mcbesiege.part.Orient does for physics.
Run from the repository root:  python3 tools/gen_assets.py
"""
import json
import math
import os

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'src', 'main', 'resources')
NS = 'mcbesiege'
A = os.path.join(ROOT, 'assets', NS)
D = os.path.join(ROOT, 'data', NS)


def write(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w', encoding='utf-8') as f:
        json.dump(obj, f, ensure_ascii=False, indent=1)
        f.write('\n')


# ------------------------------------------------------------------------------------------------------- elements
DIRS = {'n': 'north', 's': 'south', 'e': 'east', 'w': 'west', 'u': 'up', 'd': 'down'}


def inside(v):
    return all(-1e-6 <= c <= 16 + 1e-6 for c in v)


def el(frm, to, tex, faces='nsewud', rot=None, uv=None, tint=None):
    """tex: '#var' or dict face-letter -> '#var'. Faces outside the 0..16 cube get explicit full uv."""
    e = {'from': [round(c, 4) for c in frm], 'to': [round(c, 4) for c in to], 'faces': {}}
    explicit = not (inside(frm) and inside(to)) or uv is not None
    for f in faces:
        if isinstance(tex, dict) and f not in tex:
            continue
        t = tex[f] if isinstance(tex, dict) else tex
        face = {'texture': t}
        if explicit:
            face['uv'] = list(uv) if uv is not None else [0, 0, 16, 16]
        e['faces'][DIRS[f]] = face
    if rot:
        e['rotation'] = {'origin': rot[0], 'axis': rot[1], 'angle': rot[2]}
    return e


def box(x0, y0, z0, x1, y1, z1, tex, faces='nsewud', **kw):
    return el([x0, y0, z0], [x1, y1, z1], tex, faces, **kw)


def cube(side, top=None, bottom=None):
    return [box(0, 0, 0, 16, 16, 16, {'n': side, 's': side, 'e': side, 'w': side, 'u': top or side, 'd': bottom or top or side})]


def disc(cy, r, half, tread, face, steps=None):
    """Round wheel-like disc around the Y axis: stacked strips for the tread plus a square cap with a round cutout texture."""
    els = []
    n = steps or max(3, int(round(r * 2 / 4)))
    edges = [-r + 2 * r * i / n for i in range(n + 1)]
    for i in range(n):
        za, zb = edges[i], edges[i + 1]
        zm = (za + zb) / 2
        w = math.sqrt(max(0.0, r * r - zm * zm))
        w = max(1.0, round(w * 2) / 2)
        els.append(el([8 - w, cy - half, 8 + za], [8 + w, cy + half, 8 + zb], tread, 'nsew', uv=[0, 0, 16, 16]))
    els.append(el([8 - r, cy - half - 0.01, 8 - r], [8 + r, cy + half + 0.01, 8 + r], face, 'ud', uv=[0, 0, 16, 16]))
    return els


def rounded(cx, cy, cz, r, tex):
    """Chunky rounded blob made of three intersecting boxes."""
    s = r * 0.62
    return [
        box(cx - r, cy - s, cz - s, cx + r, cy + s, cz + s, tex),
        box(cx - s, cy - r, cz - s, cx + s, cy + r, cz + s, tex),
        box(cx - s, cy - s, cz - r, cx + s, cy + s, cz + r, tex),
    ]


def twist_el(e):
    """Rotate an element 90 deg around Y in canonical space: (x, z) -> (16 - z, x) (blockstate y=90)."""
    (x0, y0, z0), (x1, y1, z1) = e['from'], e['to']
    ax, az = 16 - z0, x0
    bx, bz = 16 - z1, x1
    ne = dict(e)
    ne['from'] = [min(ax, bx), y0, min(az, bz)]
    ne['to'] = [max(ax, bx), y1, max(az, bz)]
    remap = {'north': 'east', 'east': 'south', 'south': 'west', 'west': 'north', 'up': 'up', 'down': 'down'}
    ne['faces'] = {}
    for k, v in e['faces'].items():
        v = dict(v)
        if k in ('up', 'down'):
            v['rotation'] = 90
        ne['faces'][remap[k]] = v
    if 'rotation' in e:
        r = dict(e['rotation'])
        o = r['origin']
        r['origin'] = [16 - o[2], o[1], o[0]]
        if r['axis'] == 'x':
            r['axis'] = 'z'
        elif r['axis'] == 'z':
            r['axis'] = 'x'
            r['angle'] = -r['angle']
        ne['rotation'] = r
    return ne


def extent(els):
    lo, hi = 0.0, 16.0
    for e in els:
        lo = min(lo, *e['from'])
        hi = max(hi, *e['to'])
    return hi - lo


def model(els, textures, cutout=False, extra=None):
    m = {'parent': 'block/block', 'textures': dict(textures), 'elements': els}
    if cutout:
        m['render_type'] = 'minecraft:cutout'
    ext = extent(els)
    if ext > 16.5:
        s = 16.0 / ext
        m['display'] = {
            'gui': {'rotation': [30, 225, 0], 'translation': [0, 0, 0], 'scale': [0.625 * s, 0.625 * s, 0.625 * s]},
            'ground': {'rotation': [0, 0, 0], 'translation': [0, 3, 0], 'scale': [0.25 * s, 0.25 * s, 0.25 * s]},
            'fixed': {'rotation': [0, 0, 0], 'translation': [0, 0, 0], 'scale': [0.5 * s, 0.5 * s, 0.5 * s]},
            'thirdperson_righthand': {'rotation': [75, 45, 0], 'translation': [0, 2.5, 0], 'scale': [0.375 * s] * 3},
            'firstperson_righthand': {'rotation': [0, 45, 0], 'translation': [0, 0, 0], 'scale': [0.4 * s] * 3},
            'firstperson_lefthand': {'rotation': [0, 225, 0], 'translation': [0, 0, 0], 'scale': [0.4 * s] * 3},
        }
    if extra:
        m.update(extra)
    return m


def T(name):
    return f'{NS}:block/{name}'


# ------------------------------------------------------------------------------------------------------- parts
# each entry: id -> dict(kind='simple'|'joint', full=[els] | base=[els], head=[els], tex={}, cutout=bool, twist=bool, yaw=bool)
P = {}


def simple(pid, els, tex, cutout=False, twist=False, yaw=False):
    P[pid] = dict(kind='simple', full=els, tex=tex, cutout=cutout, twist=twist, yaw=yaw)


def joint(pid, base, head, tex, cutout=False, twist=False):
    P[pid] = dict(kind='joint', base=base, head=head, tex=tex, cutout=cutout, twist=twist, yaw=False)


# basic
simple('starting_block', cube('#side', '#top', '#bottom'),
       {'side': T('starting_block_side'), 'top': T('starting_block_top'), 'bottom': T('starting_block_bottom'), 'particle': T('starting_block_side')}, yaw=True)
simple('wooden_block', cube('#all'), {'all': T('wooden_block'), 'particle': T('wooden_block')})
simple('reinforced_wood', cube('#all'), {'all': T('reinforced_wood'), 'particle': T('reinforced_wood')})
simple('wooden_pole', [box(5, 0, 5, 11, 16, 11, {'n': '#side', 's': '#side', 'e': '#side', 'w': '#side', 'u': '#end', 'd': '#end'})],
       {'side': T('wooden_pole'), 'end': T('wooden_pole_end'), 'particle': T('wooden_pole')})
simple('wooden_panel', [box(0, 0, 0, 16, 4, 16, '#all')], {'all': T('wooden_panel'), 'particle': T('wooden_panel')})
simple('seat', [box(1, 0, 1, 15, 5, 15, {'n': '#side', 's': '#side', 'e': '#side', 'w': '#side', 'u': '#top', 'd': '#side'}),
                box(2, 5, 2, 14, 8, 14, {'n': '#side', 's': '#side', 'e': '#side', 'w': '#side', 'u': '#top', 'd': '#top'}),
                box(2, 8, 13, 14, 15, 15, {'n': '#top', 's': '#side', 'e': '#side', 'w': '#side', 'u': '#side', 'd': '#side'})],
       {'side': T('seat_side'), 'top': T('seat_top'), 'particle': T('seat_side')}, yaw=True)
# armour
simple('iron_block', cube('#all'), {'all': T('iron_block'), 'particle': T('iron_block')})
simple('steel_block', cube('#all'), {'all': T('steel_block'), 'particle': T('steel_block')})
simple('iron_plate', [box(0, 0, 0, 16, 4, 16, '#all')], {'all': T('iron_plate'), 'particle': T('iron_plate')})
simple('stone_block', cube('#all'), {'all': T('stone_block'), 'particle': T('stone_block')})
# locomotion
for wid, r, hw, tread, face in [('wheel', 0.9, 0.2, 'wheel_tread', 'wheel_face'), ('iron_wheel', 0.9, 0.2, 'iron_wheel_tread', 'iron_wheel_face'),
                                ('large_tire', 1.4, 0.3, 'tire_tread', 'tire_face'), ('small_wheel', 0.45, 0.15, 'tire_tread', 'small_wheel_face')]:
    R, H = r * 16, hw * 16
    cy = 6 + H
    joint(wid, [box(6, 0, 6, 10, 6, 10, '#axle')],
          disc(cy, R, H, '#tread', '#face') + [box(6.5, cy - H - 0.5, 6.5, 9.5, cy + H + 0.5, 9.5, '#axle')],
          {'axle': T('axle'), 'tread': T(tread), 'face': T(face), 'particle': T(tread)}, cutout=True)
simple('grip_pad', [box(0, 0, 0, 16, 4, 16, '#all')], {'all': T('grip_pad'), 'particle': T('grip_pad')})
simple('ice_pad', [box(0, 0, 0, 16, 4, 16, '#all')], {'all': T('ice_pad'), 'particle': T('ice_pad')})
# mechanical
hs = {'n': '#side', 's': '#side', 'e': '#side', 'w': '#side', 'u': '#side', 'd': '#side'}
joint('hinge',
      [box(1, 0, 1, 15, 6, 15, '#side'), box(0, 6, 6, 4, 10, 10, '#pin'), box(12, 6, 6, 16, 10, 10, '#pin')],
      [box(1, 10, 1, 15, 16, 15, '#side'), box(5, 6, 6, 11, 10, 10, '#pin'), box(0, 7.5, 7.5, 16, 8.5, 8.5, '#pin')],
      {'side': T('hinge'), 'pin': T('axle'), 'particle': T('hinge')}, twist=True)
# steering hinge: knuckle pin along canonical Z (vertical when mounted on a side face); the twisted variant turns it
joint('steering_hinge',
      [box(1, 0, 1, 15, 6, 15, '#side'), box(6, 6, 0, 10, 10, 4, '#pin'), box(6, 6, 12, 10, 10, 16, '#pin')],
      [box(1, 10, 1, 15, 16, 15, {'n': '#side', 's': '#side', 'e': '#side', 'w': '#side', 'u': '#top', 'd': '#side'}),
       box(6, 6, 5, 10, 10, 11, '#pin'), box(7.5, 7.5, 0, 8.5, 8.5, 16, '#pin')],
      {'side': T('steering_hinge'), 'top': T('steering_hinge_top'), 'pin': T('axle'), 'particle': T('steering_hinge')},
      twist=True)
joint('steering_block', [box(0, 0, 0, 16, 8, 16, '#side')],
      [box(1, 8, 1, 15, 16, 15, {'n': '#side', 's': '#side', 'e': '#side', 'w': '#side', 'u': '#top', 'd': '#side'})],
      {'side': T('steering_block'), 'top': T('steering_block_top'), 'particle': T('steering_block')})
joint('spinning_block', [box(0, 0, 0, 16, 9, 16, '#side')],
      [box(1, 9, 1, 15, 16, 15, {'n': '#axle', 's': '#axle', 'e': '#axle', 'w': '#axle', 'u': '#top', 'd': '#axle'})],
      {'side': T('spinning_block'), 'top': T('spinning_block_top'), 'axle': T('axle'), 'particle': T('spinning_block')})
joint('ball_joint', [box(2, 0, 2, 14, 6, 14, '#base')], rounded(8, 11, 8, 5, '#ball'),
      {'base': T('axle'), 'ball': T('ball_joint'), 'particle': T('ball_joint')})
joint('suspension', [box(1, 0, 1, 15, 4, 15, '#end'), box(4, 4, 4, 12, 12, 12, '#coil')],
      [box(1, 12, 1, 15, 16, 15, '#end'), box(6.5, 4, 6.5, 9.5, 12, 9.5, '#axle')],
      {'end': T('suspension_end'), 'coil': T('suspension'), 'axle': T('axle'), 'particle': T('suspension')})
joint('piston', [box(0, 0, 0, 16, 12, 16, {'n': '#side', 's': '#side', 'e': '#side', 'w': '#side', 'u': '#inner', 'd': '#bottom'})],
      [box(0, 12, 0, 16, 16, 16, {'n': '#side', 's': '#side', 'e': '#side', 'w': '#side', 'u': '#top', 'd': '#bottom'}),
       box(6, -4, 6, 10, 12, 10, '#axle', faces='nsew')],
      {'side': T('piston_side'), 'top': T('piston_top'), 'bottom': T('piston_bottom'), 'inner': T('piston_inner'), 'axle': T('axle'),
       'particle': T('piston_side')})
joint('decoupler', [box(0, 0, 0, 16, 8, 16, '#all')], [box(0, 8, 0, 16, 16, 16, '#all')], {'all': T('decoupler'), 'particle': T('decoupler')})
simple('grabber', [box(2, 0, 2, 14, 8, 14, '#side'),
                   box(2, 8, 2, 5, 13, 14, {'n': '#side', 's': '#side', 'e': '#front', 'w': '#side', 'u': '#front', 'd': '#side'}),
                   box(11, 8, 2, 14, 13, 14, {'n': '#side', 's': '#side', 'e': '#side', 'w': '#front', 'u': '#front', 'd': '#side'})],
       {'side': T('grabber_side'), 'front': T('grabber'), 'particle': T('grabber_side')})
# weapons
simple('cannon', [box(2, 0, 2, 14, 5, 14, '#base'),
                  box(4, 3, 4, 12, 16, 12, {'n': '#barrel', 's': '#barrel', 'e': '#barrel', 'w': '#barrel', 'u': '#muzzle', 'd': '#barrel'}),
                  box(4.6, 3, 4.6, 11.4, 15.9, 11.4, {'n': '#barrel', 's': '#barrel', 'e': '#barrel', 'w': '#barrel', 'u': '#muzzle'},
                      rot=[[8, 8, 8], 'y', 45])],
       {'base': T('cannon_base'), 'barrel': T('cannon_barrel'), 'muzzle': T('cannon_muzzle'), 'particle': T('cannon_barrel')})
simple('crossbow', [box(6, 0, 5, 10, 10, 11, '#stock'), box(1, 7, 7, 15, 10, 9, '#limb'), box(7.5, 9, 7.5, 8.5, 15, 8.5, '#limb'),
                    box(1, 7.5, 9, 15, 8.5, 9.01, '#string', faces='ns')],
       {'stock': T('crossbow_stock'), 'limb': T('crossbow_limb'), 'string': T('crossbow_string'), 'particle': T('crossbow_stock')}, cutout=True)
simple('flamethrower', [box(4, 0, 4, 12, 12, 12, '#tank'),
                        box(6, 12, 6, 10, 16, 10, {'n': '#tank', 's': '#tank', 'e': '#tank', 'w': '#tank', 'u': '#nozzle', 'd': '#tank'})],
       {'tank': T('flamethrower'), 'nozzle': T('flamethrower_nozzle'), 'particle': T('flamethrower')})
simple('bomb', [box(2, 0, 2, 14, 12, 14, {'n': '#side', 's': '#side', 'e': '#side', 'w': '#side', 'u': '#top', 'd': '#bottom'})],
       {'side': T('bomb_side'), 'top': T('bomb_top'), 'bottom': T('bomb_bottom'), 'particle': T('bomb_side')})
simple('powder_barrel', [box(2, 0, 2, 14, 16, 14, {'n': '#side', 's': '#side', 'e': '#side', 'w': '#side', 'u': '#top', 'd': '#top'}),
                         box(3, 0.01, 3, 13, 15.99, 13, {'n': '#side', 's': '#side', 'e': '#side', 'w': '#side'}, rot=[[8, 8, 8], 'y', 45])],
       {'side': T('powder_barrel_side'), 'top': T('powder_barrel_top'), 'particle': T('powder_barrel_side')})
joint('boulder', [box(5, 0, 5, 11, 1, 11, '#mount')], rounded(8, 8, 8, 7.2, '#rock'), {'mount': T('spike_base'), 'rock': T('boulder'), 'particle': T('boulder')})
simple('rocket', [box(5, 0, 5, 11, 13, 11, {'n': '#side', 's': '#side', 'e': '#side', 'w': '#side', 'u': '#top', 'd': '#bottom'}),
                  box(6, 13, 6, 10, 16, 10, '#top'), box(3, 0, 7.5, 13, 4, 8.5, '#side'), box(7.5, 0, 3, 8.5, 4, 13, '#side')],
       {'side': T('rocket_side'), 'top': T('rocket_top'), 'bottom': T('rocket_bottom'), 'particle': T('rocket_side')})
joint('saw_blade', [box(5, 0, 5, 11, 5, 11, '#hub')], disc(6, 12.8, 1, '#hub', '#blade', steps=6) + [box(6, 4, 6, 10, 8, 10, '#hub')],
      {'hub': T('saw_hub'), 'blade': T('saw_blade'), 'particle': T('saw_hub')}, cutout=True)
joint('drill', [box(3, 0, 3, 13, 5, 13, '#base')],
      [box(4, 5, 4, 12, 9, 12, '#bit'), box(5, 9, 5, 11, 13, 11, '#bit'), box(6, 13, 6, 10, 17, 10, '#bit'),
       box(7, 17, 7, 9, 19.5, 9, {'n': '#bit', 's': '#bit', 'e': '#bit', 'w': '#bit', 'u': '#tip', 'd': '#bit'})],
      {'base': T('axle'), 'bit': T('drill_side'), 'tip': T('drill_tip'), 'particle': T('drill_side')})
simple('hammer', [box(6.5, 0, 6.5, 9.5, 10, 9.5, '#handle'), box(2, 10, 4, 14, 16, 12, '#head')],
       {'handle': T('hammer_handle'), 'head': T('hammer_head'), 'particle': T('hammer_head')})
spikes = [box(1, 0, 1, 15, 3, 15, '#base')]
for sx, sz in [(5, 5), (11, 5), (5, 11), (11, 11)]:
    spikes += [box(sx - 2, 3, sz - 2, sx + 2, 6, sz + 2, '#spike'), box(sx - 1, 6, sz - 1, sx + 1, 10, sz + 1, '#spike'),
               box(sx - 0.5, 10, sz - 0.5, sx + 0.5, 14, sz + 0.5, '#spike')]
simple('spike', spikes, {'base': T('spike_base'), 'spike': T('spike'), 'particle': T('spike')})
# flight
simple('propeller', [box(6, 3, 6, 10, 9, 10, '#hub'), box(-8, 6, 6.5, 24, 8, 9.5, '#blade')],
       {'hub': T('propeller_hub'), 'blade': T('propeller_blade'), 'particle': T('propeller_blade')}, twist=True)
simple('wing', [box(0, 0, 0, 16, 2, 16, '#cloth'), box(1, 2, 7, 15, 3, 9, '#rib')], {'cloth': T('wing'), 'rib': T('brace'), 'particle': T('wing')})
simple('flying_block', [box(3, 0, 3, 13, 14, 13, {'n': '#side', 's': '#side', 'e': '#side', 'w': '#side', 'u': '#nozzle', 'd': '#side'}),
                        box(4, 14, 4, 12, 16, 12, {'n': '#side', 's': '#side', 'e': '#side', 'w': '#side', 'u': '#nozzle', 'd': '#side'})],
       {'side': T('flying_block'), 'nozzle': T('flying_block_nozzle'), 'particle': T('flying_block')})
simple('balloon', rounded(8, 9, 8, 7.5, '#cloth') + [box(7.5, 0, 7.5, 8.5, 2, 8.5, '#rope')],
       {'cloth': T('balloon'), 'rope': T('rope'), 'particle': T('balloon')})
simple('ballast', cube('#all'), {'all': T('ballast'), 'particle': T('ballast')})
# automation
for aid, top, side in [('timer', 'timer_top', 'timer_side'), ('altimeter', 'altimeter_top', 'altimeter_side'), ('logic_gate', 'logic_gate_top', 'logic_gate_side')]:
    simple(aid, [box(0, 0, 0, 16, 6, 16, {'n': '#side', 's': '#side', 'e': '#side', 'w': '#side', 'u': '#top', 'd': '#base'})],
           {'top': T(top), 'side': T(side), 'base': T('automation_base'), 'particle': T(side)})
simple('sensor', [box(2, 0, 2, 14, 10, 14, {'n': '#side', 's': '#side', 'e': '#side', 'w': '#side', 'u': '#front', 'd': '#side'})],
       {'side': T('sensor_side'), 'front': T('sensor_front'), 'particle': T('sensor_side')})

PART_ORDER = ['starting_block', 'wooden_block', 'reinforced_wood', 'wooden_pole', 'wooden_panel', 'seat', 'iron_block', 'steel_block', 'iron_plate',
              'stone_block', 'wheel', 'iron_wheel', 'large_tire', 'small_wheel', 'grip_pad', 'ice_pad', 'hinge', 'steering_hinge', 'steering_block',
              'spinning_block', 'ball_joint', 'suspension', 'piston', 'decoupler', 'grabber', 'cannon', 'crossbow', 'flamethrower', 'bomb',
              'powder_barrel', 'boulder', 'rocket', 'saw_blade', 'drill', 'hammer', 'spike', 'propeller', 'wing', 'flying_block', 'balloon',
              'ballast', 'timer', 'sensor', 'altimeter', 'logic_gate']
assert set(PART_ORDER) == set(P.keys()), set(P.keys()) ^ set(PART_ORDER)

FACINGS = ['down', 'up', 'north', 'south', 'west', 'east']
XROT = {'up': 0, 'down': 180, 'north': 90, 'south': 90, 'west': 90, 'east': 90}
YROT = {'up': 0, 'down': 0, 'north': 0, 'south': 180, 'west': 270, 'east': 90}
YAW = {'up': 0, 'down': 0, 'north': 0, 'south': 180, 'west': 270, 'east': 90}


def gen_parts():
    for pid in PART_ORDER:
        p = P[pid]
        tex = p['tex']
        names = {}
        if p['kind'] == 'joint':
            full = p['base'] + p['head']
            variants = {'full': full, 'base': p['base'], 'head': p['head']}
        else:
            variants = {'full': p['full']}
        for vname, els in variants.items():
            mname = pid if p['kind'] == 'simple' else f'{pid}_{vname}'
            write(os.path.join(A, 'models', 'block', mname + '.json'), model(els, tex, p['cutout']))
            names[(vname, False)] = mname
            if p['twist']:
                write(os.path.join(A, 'models', 'block', mname + '_b.json'), model([twist_el(e) for e in els], tex, p['cutout']))
                names[(vname, True)] = mname + '_b'
        # blockstate
        variants_bs = {}
        for f in FACINGS:
            for tw in (False, True):
                for sp in ('full', 'base', 'head'):
                    v = sp if p['kind'] == 'joint' else 'full'
                    m = names.get((v, tw if p['twist'] else False))
                    entry = {'model': f'{NS}:block/{m}'}
                    if p['yaw']:
                        if YAW[f]:
                            entry['y'] = YAW[f]
                    else:
                        if XROT[f]:
                            entry['x'] = XROT[f]
                        if YROT[f]:
                            entry['y'] = YROT[f]
                    variants_bs[f'facing={f},sim_part={sp},twist={"true" if tw else "false"}'] = entry
        write(os.path.join(A, 'blockstates', pid + '.json'), {'variants': variants_bs})
        item_parent = pid if p['kind'] == 'simple' else f'{pid}_full'
        write(os.path.join(A, 'models', 'item', pid + '.json'), {'parent': f'{NS}:block/{item_parent}'})


# ------------------------------------------------------------------------------------------------------- fortress
def cube_all(name, tex):
    write(os.path.join(A, 'models', 'block', name + '.json'), {'parent': 'minecraft:block/cube_all', 'textures': {'all': T(tex)}})


def simple_block(name, model_name=None):
    write(os.path.join(A, 'blockstates', name + '.json'), {'variants': {'': {'model': f'{NS}:block/{model_name or name}'}}})
    write(os.path.join(A, 'models', 'item', name + '.json'), {'parent': f'{NS}:block/{model_name or name}'})


def stairs(name, tex):
    for suffix, parent in [('', 'stairs'), ('_inner', 'inner_stairs'), ('_outer', 'outer_stairs')]:
        write(os.path.join(A, 'models', 'block', name + suffix + '.json'),
              {'parent': f'minecraft:block/{parent}', 'textures': {'bottom': T(tex), 'top': T(tex), 'side': T(tex)}})
    variants = {}
    yface = {'east': 0, 'south': 90, 'west': 180, 'north': 270}
    for facing in ['east', 'west', 'south', 'north']:
        for half in ['bottom', 'top']:
            for shape in ['straight', 'inner_left', 'inner_right', 'outer_left', 'outer_right']:
                m = name if shape == 'straight' else (name + '_inner' if shape.startswith('inner') else name + '_outer')
                y = yface[facing]
                if shape in ('inner_left', 'outer_left'):
                    y = (y + 270) % 360
                x = 0
                if half == 'top':
                    x = 180
                    if shape in ('inner_left', 'outer_left'):
                        y = (y + 90) % 360
                    elif shape in ('inner_right', 'outer_right'):
                        y = (y + 90) % 360
                e = {'model': f'{NS}:block/{m}'}
                if x:
                    e['x'] = x
                if y:
                    e['y'] = y
                if x or y:
                    e['uvlock'] = True
                variants[f'facing={facing},half={half},shape={shape}'] = e
    write(os.path.join(A, 'blockstates', name + '.json'), {'variants': variants})
    write(os.path.join(A, 'models', 'item', name + '.json'), {'parent': f'{NS}:block/{name}'})


def slab(name, tex, full_model):
    write(os.path.join(A, 'models', 'block', name + '.json'),
          {'parent': 'minecraft:block/slab', 'textures': {'bottom': T(tex), 'top': T(tex), 'side': T(tex)}})
    write(os.path.join(A, 'models', 'block', name + '_top.json'),
          {'parent': 'minecraft:block/slab_top', 'textures': {'bottom': T(tex), 'top': T(tex), 'side': T(tex)}})
    write(os.path.join(A, 'blockstates', name + '.json'), {'variants': {
        'type=bottom': {'model': f'{NS}:block/{name}'},
        'type=top': {'model': f'{NS}:block/{name}_top'},
        'type=double': {'model': f'{NS}:block/{full_model}'}}})
    write(os.path.join(A, 'models', 'item', name + '.json'), {'parent': f'{NS}:block/{name}'})


def gen_fortress():
    for n in ['castle_stone', 'castle_bricks', 'timber_frame', 'timber_frame_plain', 'thatch', 'castle_gate']:
        cube_all(n, n)
        simple_block(n)
    stairs('castle_brick_stairs', 'castle_bricks')
    slab('castle_brick_slab', 'castle_bricks', 'castle_bricks')
    stairs('thatch_stairs', 'thatch')
    slab('thatch_slab', 'thatch', 'thatch')
    # siege target
    write(os.path.join(A, 'models', 'block', 'siege_target.json'),
          {'parent': 'minecraft:block/cube_bottom_top', 'textures': {'side': T('siege_target'), 'top': T('siege_target_side'), 'bottom': T('siege_target_side')}})
    simple_block('siege_target')
    # banners (canonical: facing north, hanging on a wall to the south)
    for color in ['blue', 'red']:
        name = f'banner_{color}'
        els = [box(1, -14, 14.6, 15, 16, 15.2, {'n': '#cloth', 's': '#cloth', 'e': '#cloth', 'w': '#cloth', 'u': '#cloth', 'd': '#cloth'}, uv=[0, 0, 16, 16]),
               box(0, 15, 14.4, 16, 16.5, 15.6, '#pole', uv=[0, 0, 16, 2])]
        write(os.path.join(A, 'models', 'block', name + '.json'),
              {'parent': 'block/block', 'render_type': 'minecraft:cutout', 'textures': {'cloth': T(name), 'pole': T('brace'), 'particle': T(name)},
               'elements': els,
               'display': {'gui': {'rotation': [0, 0, 0], 'translation': [0, 4, 0], 'scale': [0.5, 0.5, 0.5]},
                           'fixed': {'rotation': [0, 180, 0], 'translation': [0, 4, 0], 'scale': [0.5, 0.5, 0.5]},
                           'ground': {'rotation': [0, 0, 0], 'translation': [0, 3, 0], 'scale': [0.25, 0.25, 0.25]}}})
        write(os.path.join(A, 'blockstates', name + '.json'), {'variants': {
            'facing=north': {'model': f'{NS}:block/{name}'}, 'facing=east': {'model': f'{NS}:block/{name}', 'y': 90},
            'facing=south': {'model': f'{NS}:block/{name}', 'y': 180}, 'facing=west': {'model': f'{NS}:block/{name}', 'y': 270}}})
        write(os.path.join(A, 'models', 'item', name + '.json'), {'parent': f'{NS}:block/{name}'})
    # mission table
    legs = [box(x, 0, z, x + 3, 12, z + 3, '#side') for x, z in [(1, 1), (12, 1), (1, 12), (12, 12)]]
    write(os.path.join(A, 'models', 'block', 'mission_table.json'),
          {'parent': 'block/block', 'textures': {'top': T('mission_table_top'), 'side': T('mission_table_side'), 'particle': T('mission_table_side')},
           'elements': [box(0, 12, 0, 16, 16, 16, {'n': '#side', 's': '#side', 'e': '#side', 'w': '#side', 'u': '#top', 'd': '#side'})] + legs})
    simple_block('mission_table')
    # render-only connector visuals (built along +Y)
    for name, tex, r in [('brace_visual', 'brace', 2.0), ('spring_visual', 'spring_coil', 2.5), ('rope_visual', 'rope', 1.0)]:
        write(os.path.join(A, 'models', 'block', name + '.json'),
              {'parent': 'block/block', 'textures': {'t': T(tex), 'particle': T(tex)},
               'elements': [box(8 - r, 0, 8 - r, 8 + r, 16, 8 + r, '#t', faces='nsew')]})
        write(os.path.join(A, 'blockstates', name + '.json'), {'variants': {'': {'model': f'{NS}:block/{name}'}}})


def gen_items():
    for n in ['wrench', 'brace_tool', 'spring_tool', 'rope_tool', 'blueprint', 'cannonball']:
        write(os.path.join(A, 'models', 'item', n + '.json'), {'parent': 'minecraft:item/handheld' if n == 'wrench' else 'minecraft:item/generated',
                                                               'textures': {'layer0': f'{NS}:item/{n}'}})


# ------------------------------------------------------------------------------------------------------- data
ALL_BLOCKS = PART_ORDER + ['castle_stone', 'castle_bricks', 'castle_brick_stairs', 'castle_brick_slab', 'timber_frame', 'timber_frame_plain', 'thatch',
                           'thatch_stairs', 'thatch_slab', 'castle_gate', 'banner_blue', 'banner_red', 'siege_target', 'mission_table']
METAL = ['iron_block', 'steel_block', 'iron_plate', 'iron_wheel', 'cannon', 'hammer', 'spike', 'saw_blade', 'drill', 'flamethrower', 'steering_block',
         'spinning_block', 'ballast', 'decoupler', 'grabber', 'flying_block']
STONE = ['stone_block', 'boulder', 'castle_stone', 'castle_bricks', 'castle_brick_stairs', 'castle_brick_slab', 'timer', 'sensor', 'altimeter', 'logic_gate']


def gen_loot():
    for b in ALL_BLOCKS:
        entry = {'type': 'minecraft:item', 'name': f'{NS}:{b}'}
        if b.endswith('_slab'):
            entry['functions'] = [{'function': 'minecraft:set_count', 'count': 2, 'add': False,
                                   'conditions': [{'condition': 'minecraft:block_state_property', 'block': f'{NS}:{b}', 'properties': {'type': 'double'}}]},
                                  {'function': 'minecraft:explosion_decay'}]
        write(os.path.join(D, 'loot_tables', 'blocks', b + '.json'),
              {'type': 'minecraft:block', 'pools': [{'rolls': 1, 'bonus_rolls': 0, 'entries': [entry],
                                                     'conditions': [{'condition': 'minecraft:survives_explosion'}]}]})
    mine = os.path.join(ROOT, 'data', 'minecraft', 'tags', 'blocks', 'mineable')
    write(os.path.join(mine, 'pickaxe.json'), {'replace': False, 'values': [f'{NS}:{b}' for b in METAL + STONE]})
    write(os.path.join(mine, 'axe.json'), {'replace': False, 'values': [f'{NS}:{b}' for b in ALL_BLOCKS if b not in METAL + STONE]})


def I(x):
    return {'tag': x[1:]} if x.startswith('#') else {'item': x if ':' in x else f'{NS}:{x}'}


def shapeless(out, ingredients, count=1):
    write(os.path.join(D, 'recipes', out + '.json'), {'type': 'minecraft:crafting_shapeless', 'category': 'misc',
                                                      'ingredients': [I(i) for i in ingredients], 'result': {'item': f'{NS}:{out}', 'count': count}})


def shaped(out, pattern, key, count=1):
    write(os.path.join(D, 'recipes', out + '.json'), {'type': 'minecraft:crafting_shaped', 'category': 'misc', 'pattern': pattern,
                                                      'key': {k: I(v) for k, v in key.items()}, 'result': {'item': f'{NS}:{out}', 'count': count}})


def gen_recipes():
    M = 'minecraft:'
    shaped('starting_block', ['PPP', 'PRP', 'PIP'], {'P': '#minecraft:planks', 'R': M + 'redstone', 'I': M + 'iron_ingot'})
    shapeless('wooden_block', ['#minecraft:planks', M + 'stick'], 2)
    shapeless('reinforced_wood', ['wooden_block', M + 'iron_nugget'])
    shapeless('wooden_pole', ['wooden_block', M + 'stick'], 3)
    shapeless('wooden_panel', ['wooden_block'], 2)
    shapeless('seat', ['wooden_block', M + 'leather'])
    shapeless('iron_block', ['wooden_block', M + 'iron_ingot', M + 'iron_ingot'])
    shapeless('steel_block', ['iron_block', M + 'coal', M + 'iron_ingot'])
    shapeless('iron_plate', [M + 'iron_ingot', M + 'iron_nugget'], 2)
    shapeless('stone_block', ['wooden_block', M + 'cobblestone'])
    shaped('wheel', [' S ', 'SPS', ' S '], {'S': M + 'stick', 'P': 'wooden_block'})
    shaped('iron_wheel', [' I ', 'IWI', ' I '], {'I': M + 'iron_ingot', 'W': 'wheel'})
    shapeless('large_tire', ['wheel', M + 'black_dye', M + 'leather', M + 'leather'])
    shapeless('small_wheel', [M + 'stick', M + 'stick', M + 'iron_nugget'])
    shapeless('grip_pad', ['wooden_panel', M + 'leather', M + 'slime_ball'])
    shapeless('ice_pad', ['wooden_panel', M + 'packed_ice'])
    shapeless('hinge', ['wooden_block', 'wooden_block', M + 'iron_ingot'])
    shapeless('steering_hinge', ['hinge', M + 'redstone'])
    shapeless('steering_block', ['hinge', M + 'redstone', M + 'iron_ingot'])
    shapeless('spinning_block', ['steering_block', M + 'coal'])
    shapeless('ball_joint', ['hinge', M + 'slime_ball'])
    shapeless('suspension', ['wooden_block', M + 'iron_ingot', M + 'slime_ball'])
    shapeless('piston', ['wooden_block', M + 'piston'])
    shapeless('decoupler', ['wooden_block', M + 'gunpowder', M + 'iron_nugget'])
    shapeless('grabber', ['wooden_block', M + 'iron_ingot', M + 'iron_ingot', M + 'slime_ball'])
    shapeless('cannon', ['wooden_block', M + 'iron_ingot', M + 'iron_ingot', M + 'iron_ingot', M + 'gunpowder'])
    shapeless('crossbow', ['wooden_block', M + 'crossbow'])
    shapeless('flamethrower', [M + 'blaze_powder', M + 'iron_ingot', M + 'flint_and_steel'])
    shapeless('bomb', [M + 'tnt'])
    shapeless('powder_barrel', [M + 'barrel', M + 'gunpowder', M + 'gunpowder', M + 'gunpowder'])
    shapeless('boulder', [M + 'cobblestone', M + 'cobblestone', M + 'cobblestone', M + 'cobblestone', M + 'slime_ball'])
    shapeless('rocket', [M + 'firework_rocket', M + 'firework_rocket', 'wooden_pole'])
    shapeless('saw_blade', [M + 'stonecutter', M + 'iron_ingot', M + 'iron_ingot'])
    shapeless('drill', [M + 'iron_ingot', M + 'iron_ingot', M + 'iron_ingot', M + 'diamond'])
    shapeless('hammer', [M + 'iron_block', M + 'stick'])
    shapeless('spike', ['wooden_panel', M + 'iron_nugget', M + 'iron_nugget', M + 'iron_nugget'])
    shapeless('propeller', ['wooden_panel', 'wooden_panel', M + 'stick'])
    shapeless('wing', ['wooden_panel', M + 'white_wool', M + 'stick'])
    shapeless('flying_block', [M + 'firework_rocket', M + 'iron_ingot', M + 'redstone'])
    shapeless('balloon', [M + 'red_wool', M + 'white_wool', M + 'string'])
    shapeless('ballast', ['wooden_block', M + 'gold_ingot', M + 'iron_ingot'])
    shapeless('timer', [M + 'clock', M + 'redstone', M + 'smooth_stone_slab'])
    shapeless('sensor', [M + 'observer', M + 'redstone'])
    shapeless('altimeter', [M + 'compass', M + 'redstone'])
    shapeless('logic_gate', [M + 'comparator', M + 'redstone'])
    shapeless('castle_stone', [M + 'stone_bricks', M + 'cobblestone'], 2)
    shapeless('castle_bricks', [M + 'stone_bricks', M + 'stone'], 2)
    shaped('castle_brick_stairs', ['B  ', 'BB ', 'BBB'], {'B': 'castle_bricks'}, 4)
    shaped('castle_brick_slab', ['BBB'], {'B': 'castle_bricks'}, 6)
    shapeless('timber_frame', ['#minecraft:planks', M + 'white_dye', M + 'stick'], 2)
    shapeless('timber_frame_plain', ['timber_frame'])
    shapeless('thatch', [M + 'hay_block', M + 'stick'], 4)
    shaped('thatch_stairs', ['T  ', 'TT ', 'TTT'], {'T': 'thatch'}, 4)
    shaped('thatch_slab', ['TTT'], {'T': 'thatch'}, 6)
    shapeless('castle_gate', [M + 'dark_oak_planks', M + 'iron_ingot'], 2)
    shapeless('banner_blue', [M + 'blue_banner'])
    shapeless('banner_red', [M + 'red_banner'])
    shapeless('siege_target', [M + 'target', M + 'red_dye'])
    shapeless('mission_table', [M + 'cartography_table', M + 'map'])
    shapeless('wrench', [M + 'iron_ingot', M + 'iron_ingot', M + 'stick', M + 'redstone'])
    shapeless('brace_tool', [M + 'stick', M + 'stick', 'wooden_pole'])
    shapeless('spring_tool', [M + 'iron_nugget', M + 'iron_nugget', M + 'iron_nugget', M + 'slime_ball'])
    shapeless('rope_tool', [M + 'string', M + 'string', M + 'string', M + 'lead'])
    shapeless('blueprint', [M + 'paper', M + 'paper', M + 'blue_dye'])
    shapeless('cannonball', [M + 'iron_ingot'], 4)


def gen_dimension():
    write(os.path.join(D, 'dimension_type', 'siege_lands.json'), {
        'ultrawarm': False, 'natural': True, 'piglin_safe': False, 'respawn_anchor_works': False, 'bed_works': True, 'has_raids': False,
        'has_skylight': True, 'has_ceiling': False, 'coordinate_scale': 1.0, 'ambient_light': 0.0, 'fixed_time': 5000,
        'logical_height': 256, 'effects': 'minecraft:overworld', 'infiniburn': '#minecraft:infiniburn_overworld',
        'min_y': 0, 'height': 256, 'monster_spawn_light_level': 0, 'monster_spawn_block_light_limit': 0})
    write(os.path.join(D, 'dimension', 'siege_lands.json'), {
        'type': f'{NS}:siege_lands',
        'generator': {'type': 'minecraft:flat', 'settings': {
            'biome': 'minecraft:plains', 'lakes': False, 'features': False, 'structure_overrides': [],
            'layers': [{'block': 'minecraft:bedrock', 'height': 1}, {'block': 'minecraft:stone', 'height': 40},
                       {'block': 'minecraft:dirt', 'height': 3}, {'block': 'minecraft:grass_block', 'height': 1}]}}})


if __name__ == '__main__':
    gen_parts()
    gen_fortress()
    gen_items()
    gen_loot()
    gen_recipes()
    gen_dimension()
    import gen_lang  # noqa: E402  (lang tables live in their own file)
    gen_lang.main(A)
    print('assets generated')
