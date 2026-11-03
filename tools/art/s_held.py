"""3D held item models (forge:separate_transforms): a small element model in the hands / on the ground / in item
frames, the 16x16 sprite (item/generated) in the GUI.

Models are authored with their working end toward NORTH (-z): flashlight lens, flare tip; the phone's screen faces
SOUTH (+z) so that with no rotation it faces the player in first person. Display transforms were tuned with
held_preview.py, which simulates vanilla 1.20.1 ItemInHandRenderer / ItemInHandLayer exactly.
"""
from s_items import HELD_REG

NS = 'reisatsu'


def _uv(item, reg, sub=None, flip=False, tex_size=16):
    u0, v0, u1, v1 = HELD_REG[item][reg]
    if sub:
        a, b, c, d = sub
        u0, v0, u1, v1 = u0 + a, v0 + b, u0 + c, v0 + d
    if flip:
        u0, u1 = u1, u0
    k = 16.0 / tex_size              # model UVs are always in 0..16 regardless of the texture size
    return [round(u0 * k, 4), round(v0 * k, 4), round(u1 * k, 4), round(v1 * k, 4)]


def F(ref, uv, rot=0, glow=False):
    f = {'texture': ref, 'uv': uv}
    if rot:
        f['rotation'] = rot
    if glow:
        f['forge_data'] = {'block_light': 15, 'sky_light': 15}
    return f


def E(fr, to, faces, shade=True, rot=None):
    e = {'from': fr, 'to': to, 'faces': faces}
    if not shade:
        e['shade'] = False
    if rot:
        e['rotation'] = rot
    return e


def zbox(fr, to, ref, side, front=None, back=None, rot_side=90, glow_front=False):
    """Box elongated along z: `side` uv for the four long faces (texture u runs along the length)."""
    faces = {'east': F(ref, side), 'west': F(ref, side), 'up': F(ref, side, rot_side), 'down': F(ref, side, rot_side)}
    if front:
        faces['north'] = F(ref, front, glow=glow_front)
    if back:
        faces['south'] = F(ref, back)
    return E(fr, to, faces)


def T(x, y, z):
    return [round(x, 3), round(y, 3), round(z, 3)]


# ------------------------------------------------------------------------------------------------ flashlight
def flashlight_base(state):
    r = '#s'
    lens = {'off': 'lens_off', 'on': 'lens_on', 'low': 'lens_low'}[state]
    u = lambda reg, sub=None: _uv('flashlight', reg, sub)
    els = [
        zbox([5.5, 5.5, 1.0], [10.5, 10.5, 4.0], r, u('bezel', (0, 0, 3, 4)), front=u(lens), back=u('bezel'),
             glow_front=(state != 'off')),
        zbox([6.0, 6.0, 4.0], [10.0, 10.0, 5.5], r, u('head', (0, 0, 2, 4))),
        zbox([6.5, 6.5, 5.5], [9.5, 9.5, 14.0], r, u('grip', (0, 0, 9, 3))),
        zbox([6.25, 6.25, 14.0], [9.75, 9.75, 15.5], r, u('tail', (0, 0, 2, 3)), back=u('tail')),
        E([7.4, 9.5, 6.6], [8.6, 10.0, 8.2], {d: F(r, u('switch')) for d in ('north', 'south', 'east', 'west', 'up')}),
    ]
    disp = {
        'firstperson_righthand': {'rotation': T(4, 8, 0), 'translation': T(-1.0, 4.0, 1.0), 'scale': T(0.6, 0.6, 0.6)},
        'firstperson_lefthand': {'rotation': T(4, 8, 0), 'translation': T(-1.0, 4.0, 1.0), 'scale': T(0.6, 0.6, 0.6)},
        'thirdperson_righthand': {'rotation': T(80, 0, 0), 'translation': T(0, 3.0, -0.5), 'scale': T(0.55, 0.55, 0.55)},
        'thirdperson_lefthand': {'rotation': T(80, 0, 0), 'translation': T(0, 3.0, -0.5), 'scale': T(0.55, 0.55, 0.55)},
        'ground': {'rotation': T(0, 0, 0), 'translation': T(0, 2, 0), 'scale': T(0.5, 0.5, 0.5)},
        'fixed': {'rotation': T(0, 90, 0), 'translation': T(0, 0, 0), 'scale': T(0.75, 0.75, 0.75)},
        'head': {'rotation': T(0, 0, 0), 'translation': T(0, 10, -4), 'scale': T(0.6, 0.6, 0.6)},
    }
    return {'textures': {'s': f'{NS}:item/held/flashlight', 'particle': f'{NS}:item/held/flashlight'},
            'elements': els, 'display': disp}


# ------------------------------------------------------------------------------------------------ phone
def phone_base(state):
    r = '#s'
    u = lambda reg, sub=None, flip=False: _uv('phone', reg, sub, flip, tex_size=32)
    screen = {'off': 'screen_off', 'light': 'screen_light', 'camera': 'screen_cam'}[state]
    els = [
        E([4.5, 1.0, 7.25], [11.5, 15.0, 8.75], {
            'south': F(r, u(screen, (0.5, 1, 7.5, 15)), glow=(state != 'off')),
            'north': F(r, u('back', (0, 0, 7, 14))),
            'east': F(r, u('frame', (0, 1, 2, 15))), 'west': F(r, u('frame', (0, 1, 2, 15))),
            'up': F(r, u('frame_h', (0, 0, 14, 2))), 'down': F(r, u('frame_h', (0, 0, 14, 2)))}),
        E([5.25, 11.5, 6.85], [7.75, 14.25, 7.25], {
            'north': F(r, u('cam_module')), 'east': F(r, u('frame', (0, 0, 1, 3))), 'west': F(r, u('frame', (0, 0, 1, 3))),
            'up': F(r, u('frame', (0, 0, 3, 1))), 'down': F(r, u('frame', (0, 0, 3, 1)))}),
        E([8.25, 12.75, 7.05], [9.25, 13.75, 7.25], {
            'north': F(r, u('led_on' if state == 'light' else 'led_off'), glow=(state == 'light'))}, shade=(state != 'light')),
    ]
    disp = {
        'firstperson_righthand': {'rotation': T(-6, -20, 2), 'translation': T(-1.0, 5.0, 0.5), 'scale': T(0.45, 0.45, 0.45)},
        'firstperson_lefthand': {'rotation': T(-6, -20, 2), 'translation': T(-1.0, 5.0, 0.5), 'scale': T(0.45, 0.45, 0.45)},
        'thirdperson_righthand': {'rotation': T(70, 0, 0), 'translation': T(0, 2.5, -1.5), 'scale': T(0.42, 0.42, 0.42)},
        'thirdperson_lefthand': {'rotation': T(70, 0, 0), 'translation': T(0, 2.5, -1.5), 'scale': T(0.42, 0.42, 0.42)},
        'ground': {'rotation': T(0, 0, 0), 'translation': T(0, 2, 0), 'scale': T(0.5, 0.5, 0.5)},
        'fixed': {'rotation': T(0, 180, 0), 'translation': T(0, 0, 0), 'scale': T(0.75, 0.75, 0.75)},
        'head': {'rotation': T(0, 0, 0), 'translation': T(0, 10, -4), 'scale': T(0.6, 0.6, 0.6)},
    }
    return {'textures': {'s': f'{NS}:item/held/phone', 'particle': f'{NS}:item/held/phone'}, 'elements': els, 'display': disp}


# ------------------------------------------------------------------------------------------------ flare
def flare_base(lit):
    r = '#s'
    u = lambda reg, sub=None: _uv('flare', reg, sub)
    els = [
        zbox([7.0, 7.0, 2.0], [9.0, 9.0, 8.0], r, u('tube', (0, 0, 6, 2))),
        zbox([7.0, 7.0, 8.0], [9.0, 9.0, 11.0], r, u('label', (0, 0, 3, 2))),
        zbox([7.0, 7.0, 11.0], [9.0, 9.0, 13.0], r, u('tube', (6, 0, 8, 2))),
        zbox([6.75, 6.75, 13.0], [9.25, 9.25, 15.0], r, u('cap_side', (0, 0, 2, 2)), back=u('cap', (0, 0, 2.5, 2.5))),
        E([7.25, 7.25, 1.0], [8.75, 8.75, 2.0], {d: F(r, u('tip_lit' if lit else 'tip'), glow=lit) for d in
                                                ('north', 'east', 'west', 'up', 'down')}, shade=not lit),
    ]
    if lit:
        fl = u('flame')
        els += [
            E([8.0, 4.5, -6.5], [8.0, 11.5, 1.0], {'east': F(r, fl, 90, glow=True), 'west': F(r, fl, 270, glow=True)}, shade=False),
            E([4.5, 8.0, -6.5], [11.5, 8.0, 1.0], {'up': F(r, fl, 0, glow=True), 'down': F(r, fl, 180, glow=True)}, shade=False),
        ]
    disp = {
        'firstperson_righthand': {'rotation': T(30, 10, 0), 'translation': T(-1.0, 4.0, 1.0), 'scale': T(0.6, 0.6, 0.6)},
        'firstperson_lefthand': {'rotation': T(30, 10, 0), 'translation': T(-1.0, 4.0, 1.0), 'scale': T(0.6, 0.6, 0.6)},
        'thirdperson_righthand': {'rotation': T(115, 0, 0), 'translation': T(0, 3.0, -1.0), 'scale': T(0.55, 0.55, 0.55)},
        'thirdperson_lefthand': {'rotation': T(115, 0, 0), 'translation': T(0, 3.0, -1.0), 'scale': T(0.55, 0.55, 0.55)},
        'ground': {'rotation': T(0, 0, 0), 'translation': T(0, 2, 0), 'scale': T(0.5, 0.5, 0.5)},
        'fixed': {'rotation': T(0, 90, 0), 'translation': T(0, 0, 0), 'scale': T(0.75, 0.75, 0.75)},
        'head': {'rotation': T(0, 0, 0), 'translation': T(0, 10, -4), 'scale': T(0.6, 0.6, 0.6)},
    }
    return {'render_type': 'minecraft:cutout', 'textures': {'s': f'{NS}:item/held/flare', 'particle': f'{NS}:item/held/flare'},
            'elements': els, 'display': disp}


def wrap(base, sprite):
    return {
        'loader': 'forge:separate_transforms',
        'base': base,
        'perspectives': {
            'gui': {'parent': 'minecraft:item/generated', 'textures': {'layer0': f'{NS}:item/{sprite}'}},
        },
    }


def item_models():
    return {
        'flashlight': wrap(flashlight_base('off'), 'flashlight'),
        'flashlight_on': wrap(flashlight_base('on'), 'flashlight_on'),
        'flashlight_low': wrap(flashlight_base('low'), 'flashlight_low'),
        'phone': wrap(phone_base('off'), 'phone'),
        'phone_light': wrap(phone_base('light'), 'phone_light'),
        'phone_camera': wrap(phone_base('camera'), 'phone_camera'),
        'flare': wrap(flare_base(False), 'flare'),
        'flare_lit': wrap(flare_base(True), 'flare_lit'),
    }
