"""FLYER enemy rig: harpy, ice_wraith, storm_spirit, sky_sentinel, fire_spirit.
Hovering torso with arms, optional wings, bird legs, ghostly tail, orbiting puffs/embers, halo and helm."""
from ermodel import C, P, Rig, Variant, anim
from erpaint import lighten, rgb, shade

SLOTS = ['idle', 'walk', 'attack1', 'attack2', 'cast', 'hurt', 'spawn', 'death']


def geometry():
    head = P('head', (0, 1, -0.5), C(-3, -5, -3.5, 6, 6, 6, 'skin:face'),
             P('crest', (0, -5, -0.5), C(-0.5, -9, -2, 1, 4, 7, 'feather:crest'), rot=(-15, 0, 0), opt=True),
             P('hood', (0, -2, -0.5), C(-3, -5, -3.5, 6, 6, 6, 'cloth:hood', inflate=0.6), C(-1.5, -6, 2.5, 3, 4, 2, 'cloth:hood'),
               opt=True),
             P('ice_crown', (0, -5, -0.5), C(-3, -9, -3.5, 1, 4, 1, 'crystal:crown'), C(-0.5, -11, -3.8, 1, 6, 1, 'crystal:crown'),
               C(2, -9, -3.5, 1, 4, 1, 'crystal:crown'), opt=True),
             P('helm', (0, -2, -0.5), C(-3, -5, -3.5, 6, 6, 6, 'metal:helm', inflate=0.5), C(-0.5, -8, -3, 1, 3, 6, 'metal:fin'),
               opt=True),
             P('flame_hair', (0, -5, -0.5), C(-3, -10, -2.5, 6, 5, 6, 'flame:hair'), opt=True),
             P('halo', (0, -8, -0.5), C(-4, -8.5, -4.5, 8, 1, 1, 'gold:halo'), C(-4, -8.5, 3.5, 8, 1, 1, 'gold:halo'),
               C(-4, -8.5, -3.5, 1, 1, 7, 'gold:halo'), C(3, -8.5, -3.5, 1, 1, 7, 'gold:halo'), opt=True))
    right_arm = P('right_arm', (-4.5, 2, 0), C(-6, 1, -1, 2, 6, 2, 'skin:arm', tag='arm'),
                  P('right_forearm', (-5, 7, 0), C(-6, 7, -1, 2, 5, 2, 'skin:forearm', tag='forearm'),
                    P('claws_r', (-5, 12, 0), C(-6.5, 11.5, -2, 3, 2, 1, 'bone:claw', tag='claw'), opt=True)),
                  P('plate_r', (-5, 1, 0), C(-7, 0, -2, 3, 3, 4, 'metal:plate', tag='plate'), opt=True))
    left_arm = P('left_arm', (4.5, 2, 0), C(4, 1, -1, 2, 6, 2, 'skin:arm', share='arm', mirror=True),
                 P('left_forearm', (5, 7, 0), C(4, 7, -1, 2, 5, 2, 'skin:forearm', share='forearm', mirror=True),
                   P('claws_l', (5, 12, 0), C(3.5, 11.5, -2, 3, 2, 1, 'bone:claw', share='claw', mirror=True), opt=True)),
                 P('plate_l', (5, 1, 0), C(4, 0, -2, 3, 3, 4, 'metal:plate', share='plate', mirror=True), opt=True))
    right_wing = P('right_wing', (-2, 3, 2), C(-14, 0, 2, 12, 9, 0, 'feather:wing', tag='wing'),
                   P('right_wing_tip', (-14, 1, 2), C(-24, 1, 2, 10, 8, 0, 'feather:wingtip', tag='tip')), rot=(0, 25, 0), opt=True)
    left_wing = P('left_wing', (2, 3, 2), C(2, 0, 2, 12, 9, 0, 'feather:wing', share='wing', mirror=True),
                  P('left_wing_tip', (14, 1, 2), C(14, 1, 2, 10, 8, 0, 'feather:wingtip', share='tip', mirror=True)),
                  rot=(0, -25, 0), opt=True)
    tail = P('tail1', (0, 9, 0), C(-3, 9, -1.5, 6, 5, 3, 'ghost:tail'),
             P('tail2', (0, 14, 0), C(-2, 14, -1, 4, 4, 2, 'ghost:tail2'),
               P('tail3', (0, 18, 0), C(-1, 18, -0.5, 2, 4, 1, 'ghost:tail3'))), opt=True)
    right_leg = P('right_leg', (-2, 9, 0), C(-3, 9, -1.5, 2, 5, 3, 'feather:thigh', tag='thigh'),
                  P('right_talon', (-2, 14, 0), C(-2.5, 14, -0.5, 1, 6, 1, 'bone:shin', tag='shin'),
                    C(-3.5, 20, -2.5, 3, 1, 4, 'bone:talon', tag='talon')), opt=True)
    left_leg = P('left_leg', (2, 9, 0), C(1, 9, -1.5, 2, 5, 3, 'feather:thigh', share='thigh', mirror=True),
                 P('left_talon', (2, 14, 0), C(1.5, 14, -0.5, 1, 6, 1, 'bone:shin', share='shin', mirror=True),
                   C(0.5, 20, -2.5, 3, 1, 4, 'bone:talon', share='talon', mirror=True)), opt=True)
    orbit = P('orbit', (0, 5, 0), C(-10, 4, -1.5, 3, 3, 3, 'cloud:puff', tag='puff'), C(7, 2, -1.5, 3, 3, 3, 'cloud:puff', share='puff'),
              C(-1.5, 6, 7, 3, 3, 3, 'cloud:puff', share='puff'), opt=True)
    body = P('body', (0, 9, 0), C(-3.5, 1, -2, 7, 8, 4, 'feather:body'),
             head, right_arm, left_arm, right_wing, left_wing, tail, right_leg, left_leg, orbit,
             P('core', (0, 4, -2), C(-1.5, 3, -2.6, 3, 3, 1, 'glowcore:core'), opt=True),
             P('robe_tatters', (0, 9, 0), C(-3.5, 8, -2, 7, 5, 4, 'ghost:robe', inflate=0.4), opt=True))
    return [P('base', (0, 24, 0), body)]


def flap(t0, period, amp_up, amp_down, n, side=1, tip=False):
    keys = []
    for i in range(n + 1):
        t = t0 + i * period
        keys.append((round(t, 4), 0, 0, side * (amp_up if i % 2 == 0 else -amp_down)))
    return keys


def animations():
    A = []
    A.append(anim('idle', 2.0, loop=True,
                  base=dict(pos=[(0, 0, 0, 0), (1.0, 0, 1.4, 0), (2.0, 0, 0, 0)]),
                  body=dict(rot=[(0, 4, 0, 0), (1.0, 0, 0, 0), (2.0, 4, 0, 0)]),
                  right_wing=dict(rot=flap(0, 0.25, 30, 25, 8, 1)),
                  left_wing=dict(rot=flap(0, 0.25, 30, 25, 8, -1)),
                  right_wing_tip=dict(rot=[(0, 0, 0, 0)] + [(round(0.12 + i * 0.25, 3), 0, 0, (20 if i % 2 == 0 else -15)) for i in range(7)] + [(2.0, 0, 0, 0)]),
                  left_wing_tip=dict(rot=[(0, 0, 0, 0)] + [(round(0.12 + i * 0.25, 3), 0, 0, -(20 if i % 2 == 0 else -15)) for i in range(7)] + [(2.0, 0, 0, 0)]),
                  right_arm=dict(rot=[(0, -10, 0, 8), (1.0, -16, 0, 12), (2.0, -10, 0, 8)]),
                  left_arm=dict(rot=[(0, -10, 0, -8), (1.0, -16, 0, -12), (2.0, -10, 0, -8)]),
                  right_forearm=dict(rot=[(0, -20, 0, 0), (1.0, -30, 0, 0), (2.0, -20, 0, 0)]),
                  left_forearm=dict(rot=[(0, -20, 0, 0), (1.0, -30, 0, 0), (2.0, -20, 0, 0)]),
                  tail1=dict(rot=[(0, 8, 0, 4), (0.5, 12, 0, 0), (1.0, 8, 0, -4), (1.5, 12, 0, 0), (2.0, 8, 0, 4)]),
                  tail2=dict(rot=[(0, 10, 0, -6), (0.5, 15, 0, 0), (1.0, 10, 0, 6), (1.5, 15, 0, 0), (2.0, 10, 0, -6)]),
                  tail3=dict(rot=[(0, 12, 0, 8), (0.5, 18, 0, 0), (1.0, 12, 0, -8), (1.5, 18, 0, 0), (2.0, 12, 0, 8)]),
                  right_leg=dict(rot=[(0, 15, 0, 0), (1.0, 22, 0, 0), (2.0, 15, 0, 0)]),
                  left_leg=dict(rot=[(0, 18, 0, 0), (1.0, 12, 0, 0), (2.0, 18, 0, 0)]),
                  orbit=dict(rot=[(0, 0, 0, 0), (2.0, 0, 360, 0)], lin=True),
                  halo=dict(rot=[(0, 0, 0, 0), (2.0, 0, -180, 0)], lin=True),
                  flame_hair=dict(scale=[(0, 1, 1, 1), (0.5, 1.1, 1.25, 1.1), (1.0, 0.95, 1.0, 0.95), (1.5, 1.1, 1.3, 1.1), (2.0, 1, 1, 1)]),
                  core=dict(scale=[(0, 1, 1, 1), (1.0, 1.3, 1.3, 1), (2.0, 1, 1, 1)]),
                  head=dict(rot=[(0, 0, 0, 0), (1.0, -3, 0, 0), (2.0, 0, 0, 0)])))
    A.append(anim('walk', 1.0, loop=True,
                  body=dict(rot=[(0, 16, 0, 0), (0.5, 18, 0, 0), (1.0, 16, 0, 0)]),
                  head=dict(rot=[(0, -14, 0, 0), (1.0, -14, 0, 0)]),
                  right_wing=dict(rot=flap(0, 0.25, 15, 15, 4, 1)),
                  left_wing=dict(rot=flap(0, 0.25, 15, 15, 4, -1)),
                  tail1=dict(rot=[(0, 25, 0, 0), (0.5, 30, 0, 0), (1.0, 25, 0, 0)]),
                  right_leg=dict(rot=[(0, 35, 0, 0), (1.0, 35, 0, 0)]),
                  left_leg=dict(rot=[(0, 35, 0, 0), (1.0, 35, 0, 0)]),
                  right_arm=dict(rot=[(0, 20, 0, 0), (1.0, 20, 0, 0)]),
                  left_arm=dict(rot=[(0, 20, 0, 0), (1.0, 20, 0, 0)])))
    A.append(anim('attack1', 0.8, impact=0.45,
                  base=dict(pos=[(0, 0, 0, 0), (0.25, 0, 2.5, 0.8), (0.45, 0, -1.6, -2.4), (0.8, 0, 0, 0)]),
                  body=dict(rot=[(0, 0, 0, 0), (0.25, -22, 0, 0), (0.45, 32, 0, 0), (0.8, 0, 0, 0)]),
                  right_arm=dict(rot=[(0, 0, 0, 0), (0.25, -160, 0, 20), (0.45, -40, 0, -5), (0.8, 0, 0, 0)]),
                  left_arm=dict(rot=[(0, 0, 0, 0), (0.25, -160, 0, -20), (0.45, -40, 0, 5), (0.8, 0, 0, 0)]),
                  right_wing=dict(rot=[(0, 0, 0, 0), (0.25, 0, -15, 45), (0.45, 0, 10, -30), (0.8, 0, 0, 0)]),
                  left_wing=dict(rot=[(0, 0, 0, 0), (0.25, 0, 15, -45), (0.45, 0, -10, 30), (0.8, 0, 0, 0)]),
                  right_leg=dict(rot=[(0, 0, 0, 0), (0.45, -65, 0, 0), (0.8, 0, 0, 0)]),
                  left_leg=dict(rot=[(0, 0, 0, 0), (0.45, -60, 0, 0), (0.8, 0, 0, 0)]),
                  head=dict(rot=[(0, 0, 0, 0), (0.25, -10, 0, 0), (0.45, -20, 0, 0), (0.8, 0, 0, 0)])))
    A.append(anim('attack2', 0.8, impact=0.4,
                  base=dict(rot=[(0, 0, 0, 0), (0.4, 0, 200, 0), (0.8, 0, 360, 0)], pos=[(0, 0, 0, 0), (0.4, 0, 0.6, -1.0), (0.8, 0, 0, 0)],
                            rot_interp='linear'),
                  right_arm=dict(rot=[(0, 0, 0, 0), (0.15, -20, 0, 85), (0.65, -20, 0, 85), (0.8, 0, 0, 0)]),
                  left_arm=dict(rot=[(0, 0, 0, 0), (0.15, -20, 0, -85), (0.65, -20, 0, -85), (0.8, 0, 0, 0)]),
                  tail1=dict(rot=[(0, 0, 0, 0), (0.4, 30, 0, 25), (0.8, 0, 0, 0)]),
                  right_wing=dict(rot=[(0, 0, 0, 0), (0.4, 0, -20, 10), (0.8, 0, 0, 0)]),
                  left_wing=dict(rot=[(0, 0, 0, 0), (0.4, 0, 20, -10), (0.8, 0, 0, 0)])))
    A.append(anim('cast', 1.2, impact=0.7,
                  right_arm=dict(rot=[(0, 0, 0, 0), (0.45, -135, 0, 30), (0.7, -80, 0, 8), (0.95, -75, 0, 5), (1.2, 0, 0, 0)]),
                  left_arm=dict(rot=[(0, 0, 0, 0), (0.45, -135, 0, -30), (0.7, -80, 0, -8), (0.95, -75, 0, -5), (1.2, 0, 0, 0)]),
                  body=dict(rot=[(0, 0, 0, 0), (0.45, -16, 0, 0), (0.7, 12, 0, 0), (1.2, 0, 0, 0)]),
                  head=dict(rot=[(0, 0, 0, 0), (0.45, -20, 0, 0), (0.7, 5, 0, 0), (1.2, 0, 0, 0)]),
                  core=dict(scale=[(0, 1, 1, 1), (0.45, 1.8, 1.8, 1.4), (0.7, 0.6, 0.6, 1), (1.2, 1, 1, 1)]),
                  orbit=dict(rot=[(0, 0, 0, 0), (1.2, 0, 720, 0)], scale=[(0, 1, 1, 1), (0.45, 0.6, 1, 0.6), (0.7, 1.4, 1, 1.4), (1.2, 1, 1, 1)],
                             rot_interp='linear'),
                  base=dict(pos=[(0, 0, 0, 0), (0.45, 0, 1.2, 0.6), (0.7, 0, 0, -1.0), (1.2, 0, 0, 0)])))
    A.append(anim('hurt', 0.4,
                  body=dict(rot=[(0, 0, 0, 0), (0.08, -18, 0, 6), (0.4, 0, 0, 0)]),
                  head=dict(rot=[(0, 0, 0, 0), (0.08, -16, 0, 0), (0.4, 0, 0, 0)]),
                  base=dict(pos=[(0, 0, 0, 0), (0.08, 0, 0.4, 1.2), (0.4, 0, 0, 0)]),
                  right_wing=dict(rot=[(0, 0, 0, 0), (0.08, 0, 0, 40), (0.4, 0, 0, 0)]),
                  left_wing=dict(rot=[(0, 0, 0, 0), (0.08, 0, 0, -40), (0.4, 0, 0, 0)])))
    A.append(anim('spawn', 1.2,
                  base=dict(scale=[(0, 0.05, 0.05, 0.05), (0.7, 1.15, 1.15, 1.15), (0.9, 0.95, 0.95, 0.95), (1.2, 1, 1, 1)],
                            rot=[(0, 0, -360, 0), (0.8, 0, 0, 0), (1.2, 0, 0, 0)],
                            pos=[(0, 0, 4, 0), (0.8, 0, 0, 0), (1.2, 0, 0, 0)]),
                  right_wing=dict(rot=[(0, 0, 0, 0), (0.7, 0, 0, 60), (1.2, 0, 0, 0)]),
                  left_wing=dict(rot=[(0, 0, 0, 0), (0.7, 0, 0, -60), (1.2, 0, 0, 0)])))
    A.append(anim('death', 1.6,
                  base=dict(rot=[(0, 0, 0, 0), (1.3, 0, 540, 0), (1.6, 0, 600, 0)],
                            pos=[(0, 0, 0, 0), (0.3, 0, 1.5, 0), (1.3, 0, -9, 0), (1.6, 0, -10, 0)],
                            scale=[(0, 1, 1, 1), (1.1, 1, 1, 1), (1.6, 0.25, 0.25, 0.25)], rot_interp='linear'),
                  body=dict(rot=[(0, 0, 0, 0), (0.3, -25, 0, 0), (1.3, 45, 0, 0), (1.6, 50, 0, 0)]),
                  right_wing=dict(rot=[(0, 0, 0, 0), (0.3, 0, 0, 50), (1.3, 0, 0, -60), (1.6, 0, 0, -65)]),
                  left_wing=dict(rot=[(0, 0, 0, 0), (0.3, 0, 0, -50), (1.3, 0, 0, 60), (1.6, 0, 0, 65)]),
                  head=dict(rot=[(0, 0, 0, 0), (0.3, -30, 0, 0), (1.6, 30, 0, 0)]),
                  right_arm=dict(rot=[(0, 0, 0, 0), (0.3, -60, 0, 50), (1.6, -20, 0, 30)]),
                  left_arm=dict(rot=[(0, 0, 0, 0), (0.3, -60, 0, -50), (1.6, -20, 0, -30)])))
    return A


def pal(**over):
    p = {'face': '#d8b090', 'arm': '#d8b090', 'forearm': '#c09070', 'body': '#8a6a4a', 'crest': '#c05030',
         'hood': '#c8e4f4', 'crown': '#bfeaff', 'helm': '#c8a860', 'fin': '#c8a860', 'hair': '#ff9933',
         'halo': '#ffd860', 'claw': '#2a2420', 'plate': '#c8a860', 'wing': '#8a6a4a', 'wingtip': '#6a4a30',
         'tail': '#c8e4f4', 'tail2': '#b8d8ec', 'tail3': '#a8cce4', 'thigh': '#8a6a4a', 'shin': '#e0c060',
         'talon': '#3a3028', 'puff': '#d8dce8', 'core': '#ffffff', 'robe': '#c8e4f4'}
    p.update(over)
    return p


def variants():
    V = []

    def deco_harpy(p):
        wing_feathers(p)
        f = p.face('head', 'front')
        f.px(1, 2, (250, 220, 60))
        f.px(4, 2, (250, 220, 60))
        f.px(1, 1, (60, 40, 30))
        f.px(4, 1, (60, 40, 30))
        f.hline(2, 3, 4, (120, 60, 50))
        for side in ('top', 'back', 'right', 'left'):
            fc = p.face('head', side)
            for y in range(0, min(fc.h, 4 if side in ('right', 'left') else fc.h)):
                for x in range(fc.w):
                    fc.px(x, y, shade(rgb('#7a5030'), 0.9 + 0.2 * ((x + y) % 2)))
        fc = p.face('head', 'front')
        fc.hline(0, fc.w - 1, 0, rgb('#7a5030'))
    feather = {'base': '#8a6a4a', 'style': 'feather', 'tip': (230, 220, 200), 'notched': True}

    def wing_feathers(p):
        """Coverts (top), secondaries and long pointed primaries, darker roots, pale tips, notched trailing edge."""
        dark, mid, light, tip = rgb('#4a3424'), rgb('#7a5a3a'), rgb('#a8865c'), rgb('#e6dcc8')
        for part in ('right_wing', 'right_wing_tip'):
            for side in ('front', 'back'):
                fc = p.face(part, side)
                w, h = fc.w, fc.h
                for x in range(w):
                    for y in range(h):
                        fc.px(x, y, mid)
                for y in range(0, 3):
                    for x in range(w):
                        fc.px(x, y, light if (x + y) % 2 == 0 else mid)
                    fc.px((y * 2) % w, y, dark)
                for x in range(0, w, 2):
                    length = h - 3 - ((x // 2) % 2) if part == 'right_wing' else h - 2 - ((x * 3) % 3)
                    for y in range(3, min(h, 3 + length)):
                        fc.px(x, y, dark if y < 5 else mid)
                        if x + 1 < w:
                            fc.px(x + 1, y, light if y < h - 2 else tip)
                    for y in range(3 + length, h):
                        fc.clear(x, y)
                        if x + 1 < w and y > 3 + length:
                            fc.clear(x + 1, y)
    V.append(Variant('harpy', show={'crest', 'right_wing', 'left_wing', 'right_leg', 'left_leg', 'claws_r', 'claws_l'},
                     palette=pal(body={'base': '#8a6a4a', 'style': 'feather'}, wing=feather,
                                 wingtip=dict(feather, base='#6a4a30'), thigh={'base': '#7a5a3a', 'style': 'feather'},
                                 crest={'base': '#c05030', 'style': 'feather'}),
                     paint=deco_harpy, extra=dict(scale=1.0)))
    ghost = {'base': '#b8d8ec', 'style': 'ghost'}

    def deco_wraith(p):
        f = p.face('head', 'front')
        f.rect(0, 0, f.w - 1, f.h - 1, (30, 40, 60))
        f.px(1, 2, (200, 240, 255))
        f.px(4, 2, (200, 240, 255))
        g = p.face('head', 'front', glow=True)
        g.px(1, 2, (150, 230, 255))
        g.px(4, 2, (150, 230, 255))
        hf = p.face('hood', 'front')
        for y in range(1, hf.h):
            for x in range(1, hf.w - 1):
                hf.clear(x, y)
        for fc in p.all_faces('ice_crown', glow=True):
            fc.rect(0, 0, fc.w - 1, fc.h - 1, (90, 170, 220))
    V.append(Variant('ice_wraith', show={'hood', 'ice_crown', 'tail1', 'claws_r', 'claws_l', 'robe_tatters'},
                     palette=pal(face=ghost, arm=ghost, forearm=ghost, body=ghost, hood={'base': '#a8c8e0', 'style': 'ghost', 'ragged': False},
                                 tail=ghost, tail2=ghost, tail3=ghost, claw='#e0f4ff',
                                 crown={'base': '#c8f0ff', 'style': 'crystal'}, robe=ghost),
                     paint=deco_wraith, glow=True, extra=dict(scale=1.1, translucent=True)))
    cloud = {'base': '#8a92a8', 'style': 'cloud'}

    def deco_storm(p):
        f = p.face('head', 'front')
        f.rect(1, 2, 4, 2, (40, 44, 60))
        f.px(1, 2, (255, 255, 160))
        f.px(4, 2, (255, 255, 160))
        g = p.face('head', 'front', glow=True)
        g.px(1, 2, (255, 255, 120))
        g.px(4, 2, (255, 255, 120))
        for fc in p.all_faces('core', glow=True):
            fc.rect(0, 0, fc.w - 1, fc.h - 1, (180, 220, 255))
        for fc in p.all_faces('body', glow=True, sides_only=True):
            x = 1
            for y in range(fc.h):
                fc.px(min(fc.w - 1, x), y, (200, 220, 255))
                x += 1 if y % 2 == 0 else -1
                x = max(0, x)
    V.append(Variant('storm_spirit', show={'orbit', 'core', 'tail1'},
                     palette=pal(face=cloud, arm=cloud, forearm=cloud, body=cloud, tail=cloud,
                                 tail2={'base': '#7a8298', 'style': 'cloud'}, tail3={'base': '#6a7288', 'style': 'cloud'},
                                 puff={'base': '#a8b0c4', 'style': 'cloud'}, core={'base': '#d8ecff', 'style': 'glowcore'}),
                     paint=deco_storm, glow=True, extra=dict(scale=1.1)))
    brass = {'base': '#c8a860', 'style': 'mech', 'panel': 3}

    def deco_sentinel(p):
        hf = p.face('helm', 'front')
        hf.hline(1, hf.w - 2, 3, (20, 20, 30))
        g = p.face('helm', 'front', glow=True)
        for x in range(1, g.w - 1):
            g.px(x, 3, (120, 230, 255))
        for fc in p.all_faces('halo', glow=True):
            fc.rect(0, 0, fc.w - 1, fc.h - 1, (255, 230, 140))
        for fc in p.all_faces('core', glow=True):
            fc.rect(0, 0, fc.w - 1, fc.h - 1, (120, 230, 255))
    V.append(Variant('sky_sentinel', show={'helm', 'halo', 'right_wing', 'left_wing', 'plate_r', 'plate_l', 'core', 'tail1'},
                     palette=pal(face=brass, arm={'base': '#8a8070', 'style': 'metal'}, forearm={'base': '#8a8070', 'style': 'metal'},
                                 body=brass, helm={'base': '#d8b870', 'style': 'metal'}, fin={'base': '#e8d8a0', 'style': 'metal'},
                                 wing={'base': '#e8e4d8', 'style': 'metal', 'rivets': False},
                                 wingtip={'base': '#d8d0b8', 'style': 'metal', 'rivets': False},
                                 tail={'base': '#a89060', 'style': 'mech', 'panel': 3}, tail2={'base': '#988050', 'style': 'metal'},
                                 tail3={'base': '#887040', 'style': 'metal'}, core={'base': '#80e0ff', 'style': 'glowcore'},
                                 plate={'base': '#d8b870', 'style': 'metal'}),
                     paint=deco_sentinel, glow=True, extra=dict(scale=1.15)))
    fire = {'base': '#ff8830', 'style': 'flame'}

    def deco_fire(p):
        f = p.face('head', 'front')
        f.rect(0, 0, f.w - 1, f.h - 1, (255, 170, 60))
        f.px(1, 2, (255, 255, 220))
        f.px(4, 2, (255, 255, 220))
        f.hline(2, 3, 4, (180, 40, 10))
        for part in ('head', 'body', 'flame_hair', 'tail1', 'tail2', 'tail3', 'right_arm', 'left_arm',
                     'right_forearm', 'left_forearm', 'orbit', 'core'):
            for fc in p.all_faces(part, glow=True):
                src = p.face(part, fc.face)
                for y in range(fc.h):
                    for x in range(fc.w):
                        if p.img[src.v + y, src.u + x, 3] > 0:
                            c = src.get(x, y)
                            fc.px(x, y, shade(c, 0.8))
    V.append(Variant('fire_spirit', show={'flame_hair', 'orbit', 'core', 'tail1'},
                     palette=pal(face={'base': '#ffb050', 'style': 'skin'}, arm={'base': '#ff9a40', 'style': 'skin'},
                                 forearm={'base': '#ffb050', 'style': 'flame'}, body={'base': '#ff9a40', 'style': 'flame'},
                                 hair=fire, tail=fire, tail2=fire, tail3=fire, puff={'base': '#ffcc60', 'style': 'flame'},
                                 core={'base': '#fff0a0', 'style': 'glowcore'}),
                     paint=deco_fire, glow=True, extra=dict(scale=1.0)))
    return V


def rig():
    return Rig('flyer', 'FlyerEnemy', geometry(), animations(), variants(), SLOTS)
