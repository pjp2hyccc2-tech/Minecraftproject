"""GOLEM enemy rig: treant, sand_golem, ice_golem, lava_golem, ruin_construct.
Heavy biped (about 2.5 blocks at scale 1): wide chest, sunken head, long arms with big fists, short thick legs."""
from ermodel import C, P, Rig, Variant, anim
from erpaint import lighten, rgb, shade

SLOTS = ['idle', 'walk', 'attack1', 'attack2', 'cast', 'hurt', 'spawn', 'death']


def geometry():
    head = P('head', (0, -7, -4), C(-4, -15, -9, 8, 9, 8, 'stone:head'),
             P('canopy', (0, -15, -5), C(-7, -21, -11, 14, 7, 12, 'leaf:canopy'), opt=True),
             P('headdress', (0, -15, -5), C(-5, -16, -8, 10, 10, 9, 'cloth:nemes', inflate=0.3), opt=True),
             P('ice_crown', (0, -15, -5), C(-3.5, -19, -6, 2, 4, 2, 'crystal:ice'), C(-1, -21, -6.5, 2, 6, 2, 'crystal:ice'),
               C(1.5, -19, -6, 2, 4, 2, 'crystal:ice'), opt=True),
             P('rune_halo', (0, -15, -5), C(-5, -18, -2, 10, 1, 1, 'glowcore:rune'), opt=True))
    right_arm = P('right_arm', (-10, -6, 0), C(-14, -8, -3, 5, 13, 6, 'stone:arm', tag='arm'),
                  P('shoulder_r', (-11.5, -8, 0), C(-15, -11, -4, 7, 4, 8, 'stone:plate', tag='plate'), opt=True),
                  P('branch_r', (-12, -10, 0), C(-13, -18, -1, 2, 9, 2, 'bark:branch', tag='branch'), rot=(-10, 0, -20), opt=True),
                  P('crystal_r', (-12, -10, 0), C(-13.5, -17, -1.5, 3, 8, 3, 'crystal:ice', tag='crys'), rot=(-15, 0, -25), opt=True),
                  P('right_forearm', (-11.5, 5, 0), C(-15, 5, -3.5, 7, 12, 7, 'stone:fist', tag='fist')))
    left_arm = P('left_arm', (10, -6, 0), C(9, -8, -3, 5, 13, 6, 'stone:arm', share='arm', mirror=True),
                 P('shoulder_l', (11.5, -8, 0), C(8, -11, -4, 7, 4, 8, 'stone:plate', share='plate', mirror=True), opt=True),
                 P('branch_l', (12, -10, 0), C(11, -18, -1, 2, 9, 2, 'bark:branch', share='branch', mirror=True), rot=(-10, 0, 20), opt=True),
                 P('crystal_l', (12, -10, 0), C(10.5, -17, -1.5, 3, 8, 3, 'crystal:ice', share='crys', mirror=True), rot=(-15, 0, 25), opt=True),
                 P('left_forearm', (11.5, 5, 0), C(8, 5, -3.5, 7, 12, 7, 'stone:fist', share='fist', mirror=True)))
    chest = P('chest', (0, 7, 0), C(-9, -9, -6, 18, 16, 11, 'stone:body'),
              head, right_arm, left_arm,
              P('core', (0, -2, -6), C(-2.5, -4, -6.6, 5, 5, 1, 'glowcore:core'), opt=True),
              P('back_spikes', (0, -6, 4), C(-6, -12, 3, 3, 6, 3, 'stone:spike'), C(-1.5, -14, 3.5, 3, 8, 3, 'stone:spike'),
                C(3, -12, 3, 3, 6, 3, 'stone:spike'), rot=(-20, 0, 0), opt=True),
              P('moss_beard', (0, -6, -6), C(-5, -6, -6.4, 10, 7, 0, 'leaf:moss'), opt=True))
    right_leg = P('right_leg', (-3.5, 10, 0), C(-6.5, 9, -3, 6, 8, 6, 'stone:leg', tag='thigh'),
                  P('right_shin', (-3.5, 17, 0), C(-7, 17, -3.5, 7, 7, 7, 'stone:foot', tag='foot')))
    left_leg = P('left_leg', (3.5, 10, 0), C(0.5, 9, -3, 6, 8, 6, 'stone:leg', share='thigh', mirror=True),
                 P('left_shin', (3.5, 17, 0), C(0, 17, -3.5, 7, 7, 7, 'stone:foot', share='foot', mirror=True)))
    pelvis = P('pelvis', (0, 9, 0), C(-6, 6, -3.5, 12, 5, 7, 'stone:pelvis'), chest, right_leg, left_leg)
    return [P('base', (0, 24, 0), pelvis)]


def animations():
    A = []
    A.append(anim('idle', 4.0, loop=True,
                  chest=dict(rot=[(0, 0, 0, 0), (2.0, -2.5, 0, 0), (4.0, 0, 0, 0)]),
                  head=dict(rot=[(0, 0, 0, 0), (1.0, 2, 10, 0), (2.0, 0, 0, 0), (3.0, 2, -10, 0), (4.0, 0, 0, 0)],
                            pos=[(0, 0, 0, 0), (2.0, 0, 0.4, 0), (4.0, 0, 0, 0)]),
                  right_arm=dict(rot=[(0, 0, 0, 3), (2.0, -3, 0, 5), (4.0, 0, 0, 3)]),
                  left_arm=dict(rot=[(0, 0, 0, -3), (2.0, -3, 0, -5), (4.0, 0, 0, -3)]),
                  right_forearm=dict(rot=[(0, -6, 0, 0), (2.0, -10, 0, 0), (4.0, -6, 0, 0)]),
                  left_forearm=dict(rot=[(0, -6, 0, 0), (2.0, -10, 0, 0), (4.0, -6, 0, 0)]),
                  canopy=dict(rot=[(0, 0, 0, 0), (2.0, 2, 0, 3), (4.0, 0, 0, 0)]),
                  rune_halo=dict(rot=[(0, 0, 0, 0), (4.0, 0, 360, 0)], lin=True),
                  core=dict(scale=[(0, 1, 1, 1), (1.0, 1.15, 1.15, 1), (2.0, 1, 1, 1), (3.0, 1.15, 1.15, 1), (4.0, 1, 1, 1)])))
    A.append(anim('walk', 1.6, loop=True,
                  right_leg=dict(rot=[(0, 22, 0, 0), (0.8, -22, 0, 0), (1.6, 22, 0, 0)]),
                  left_leg=dict(rot=[(0, -22, 0, 0), (0.8, 22, 0, 0), (1.6, -22, 0, 0)]),
                  right_shin=dict(rot=[(0, 10, 0, 0), (0.4, 32, 0, 0), (0.8, 0, 0, 0), (1.6, 10, 0, 0)]),
                  left_shin=dict(rot=[(0, 0, 0, 0), (0.8, 10, 0, 0), (1.2, 32, 0, 0), (1.6, 0, 0, 0)]),
                  right_arm=dict(rot=[(0, -18, 0, 0), (0.8, 18, 0, 0), (1.6, -18, 0, 0)]),
                  left_arm=dict(rot=[(0, 18, 0, 0), (0.8, -18, 0, 0), (1.6, 18, 0, 0)]),
                  chest=dict(rot=[(0, 3, 4, 4), (0.8, 3, -4, -4), (1.6, 3, 4, 4)]),
                  pelvis=dict(rot=[(0, 0, -5, 0), (0.8, 0, 5, 0), (1.6, 0, -5, 0)]),
                  base=dict(pos=[(0, 0, 0, 0), (0.4, 0, 0.9, 0), (0.8, 0, 0, 0), (1.2, 0, 0.9, 0), (1.6, 0, 0, 0)]),
                  head=dict(rot=[(0, 0, 0, -3), (0.8, 0, 0, 3), (1.6, 0, 0, -3)])))
    A.append(anim('attack1', 1.2, impact=0.65,
                  right_arm=dict(rot=[(0, 0, 0, 0), (0.45, -170, 0, 12), (0.65, -35, 0, 4), (0.85, -30, 0, 0), (1.2, 0, 0, 0)]),
                  left_arm=dict(rot=[(0, 0, 0, 0), (0.45, -170, 0, -12), (0.65, -35, 0, -4), (0.85, -30, 0, 0), (1.2, 0, 0, 0)]),
                  right_forearm=dict(rot=[(0, 0, 0, 0), (0.45, -30, 0, 0), (0.65, 0, 0, 0), (1.2, 0, 0, 0)]),
                  left_forearm=dict(rot=[(0, 0, 0, 0), (0.45, -30, 0, 0), (0.65, 0, 0, 0), (1.2, 0, 0, 0)]),
                  chest=dict(rot=[(0, 0, 0, 0), (0.45, -18, 0, 0), (0.65, 28, 0, 0), (0.85, 24, 0, 0), (1.2, 0, 0, 0)]),
                  head=dict(rot=[(0, 0, 0, 0), (0.45, -15, 0, 0), (0.65, 10, 0, 0), (1.2, 0, 0, 0)]),
                  base=dict(pos=[(0, 0, 0, 0), (0.45, 0, 1.2, 0.5), (0.65, 0, -2.0, -1.0), (0.9, 0, -1.6, -1.0), (1.2, 0, 0, 0)]),
                  right_leg=dict(rot=[(0, 0, 0, 0), (0.65, -25, 0, 0), (1.2, 0, 0, 0)]),
                  right_shin=dict(rot=[(0, 0, 0, 0), (0.65, 30, 0, 0), (1.2, 0, 0, 0)]),
                  left_leg=dict(rot=[(0, 0, 0, 0), (0.65, 10, 0, 0), (1.2, 0, 0, 0)]),
                  left_shin=dict(rot=[(0, 0, 0, 0), (0.65, 25, 0, 0), (1.2, 0, 0, 0)])))
    A.append(anim('attack2', 1.0, impact=0.5,
                  right_arm=dict(rot=[(0, 0, 0, 0), (0.3, -50, 0, 75), (0.5, -85, 0, -25), (0.65, -75, 0, -30), (1.0, 0, 0, 0)]),
                  right_forearm=dict(rot=[(0, 0, 0, 0), (0.3, -20, 0, 0), (0.5, -5, 0, 0), (1.0, 0, 0, 0)]),
                  chest=dict(rot=[(0, 0, 0, 0), (0.3, 0, 38, 0), (0.5, 6, -42, 0), (0.65, 6, -38, 0), (1.0, 0, 0, 0)]),
                  left_arm=dict(rot=[(0, 0, 0, 0), (0.3, 15, 0, -20), (0.5, -30, 0, -10), (1.0, 0, 0, 0)]),
                  head=dict(rot=[(0, 0, 0, 0), (0.3, 0, -25, 0), (0.5, 0, 20, 0), (1.0, 0, 0, 0)]),
                  base=dict(pos=[(0, 0, 0, 0), (0.3, 0, 0, 0.6), (0.5, 0, -0.5, -1.4), (1.0, 0, 0, 0)]),
                  pelvis=dict(rot=[(0, 0, 0, 0), (0.3, 0, 10, 0), (0.5, 0, -10, 0), (1.0, 0, 0, 0)])))
    A.append(anim('cast', 1.4, impact=0.8,
                  right_arm=dict(rot=[(0, 0, 0, 0), (0.5, -165, 0, 25), (0.8, -60, 0, 5), (1.0, -55, 0, 5), (1.4, 0, 0, 0)]),
                  left_arm=dict(rot=[(0, 0, 0, 0), (0.5, -165, 0, -25), (0.8, -60, 0, -5), (1.0, -55, 0, -5), (1.4, 0, 0, 0)]),
                  chest=dict(rot=[(0, 0, 0, 0), (0.5, -20, 0, 0), (0.8, 38, 0, 0), (1.0, 34, 0, 0), (1.4, 0, 0, 0)]),
                  head=dict(rot=[(0, 0, 0, 0), (0.5, -25, 0, 0), (0.8, 5, 0, 0), (1.4, 0, 0, 0)]),
                  base=dict(pos=[(0, 0, 0, 0), (0.5, 0, 1.5, 0), (0.8, 0, -3.0, -0.5), (1.0, 0, -2.8, -0.5), (1.4, 0, 0, 0)]),
                  right_leg=dict(rot=[(0, 0, 0, 0), (0.8, -35, 0, 0), (1.4, 0, 0, 0)]),
                  right_shin=dict(rot=[(0, 0, 0, 0), (0.8, 45, 0, 0), (1.4, 0, 0, 0)]),
                  left_leg=dict(rot=[(0, 0, 0, 0), (0.8, -30, 0, 0), (1.4, 0, 0, 0)]),
                  left_shin=dict(rot=[(0, 0, 0, 0), (0.8, 40, 0, 0), (1.4, 0, 0, 0)]),
                  core=dict(scale=[(0, 1, 1, 1), (0.5, 1.6, 1.6, 1.2), (0.8, 0.8, 0.8, 1), (1.4, 1, 1, 1)]),
                  canopy=dict(rot=[(0, 0, 0, 0), (0.8, 12, 0, 0), (1.0, -6, 0, 0), (1.4, 0, 0, 0)])))
    A.append(anim('hurt', 0.4,
                  chest=dict(rot=[(0, 0, 0, 0), (0.08, -10, 0, 4), (0.4, 0, 0, 0)]),
                  head=dict(rot=[(0, 0, 0, 0), (0.08, -14, 0, 0), (0.4, 0, 0, 0)]),
                  base=dict(pos=[(0, 0, 0, 0), (0.08, 0, 0, 0.7), (0.4, 0, 0, 0)])))
    A.append(anim('spawn', 1.6,
                  base=dict(pos=[(0, 0, -30, 0), (0.9, 0, -6, 0), (1.25, 0, 0.8, 0), (1.6, 0, 0, 0)]),
                  right_arm=dict(rot=[(0, -170, 0, 15), (0.9, -120, 0, 20), (1.3, -10, 0, 0), (1.6, 0, 0, 0)]),
                  left_arm=dict(rot=[(0, -170, 0, -15), (0.9, -140, 0, -20), (1.35, -10, 0, 0), (1.6, 0, 0, 0)]),
                  chest=dict(rot=[(0, 20, 0, 0), (1.0, -12, 0, 0), (1.6, 0, 0, 0)]),
                  head=dict(rot=[(0, -30, 0, 0), (1.1, 10, 0, 0), (1.6, 0, 0, 0)])))
    A.append(anim('death', 2.0,
                  right_leg=dict(rot=[(0, 0, 0, 0), (0.7, -45, 0, 0), (1.2, -60, 0, 0), (2.0, -60, 0, 0)]),
                  left_leg=dict(rot=[(0, 0, 0, 0), (0.7, -40, 0, 0), (1.2, -55, 0, 0), (2.0, -55, 0, 0)]),
                  right_shin=dict(rot=[(0, 0, 0, 0), (0.7, 70, 0, 0), (1.2, 90, 0, 0), (2.0, 90, 0, 0)]),
                  left_shin=dict(rot=[(0, 0, 0, 0), (0.7, 65, 0, 0), (1.2, 85, 0, 0), (2.0, 85, 0, 0)]),
                  base=dict(pos=[(0, 0, 0, 0), (0.7, 0, -5, 0), (1.2, 0, -7, -1), (2.0, 0, -9, -2)]),
                  chest=dict(rot=[(0, 0, 0, 0), (0.3, -15, 0, 0), (1.2, 45, 0, 0), (1.6, 75, 0, 0), (2.0, 80, 0, 0)],
                             scale=[(0, 1, 1, 1), (1.4, 1, 1, 1), (2.0, 1.05, 0.75, 1.05)]),
                  head=dict(rot=[(0, 0, 0, 0), (0.3, -25, 0, 0), (1.2, 30, 0, 8), (2.0, 35, 0, 12)]),
                  right_arm=dict(rot=[(0, 0, 0, 0), (0.3, -30, 0, 30), (1.2, -20, 0, 10), (2.0, -70, 0, 15)]),
                  left_arm=dict(rot=[(0, 0, 0, 0), (0.3, -30, 0, -30), (1.2, -20, 0, -10), (2.0, -60, 0, -20)])))
    return A


def pal(**over):
    p = {'head': '#8a8a86', 'body': '#8a8a86', 'arm': '#7e7e7a', 'fist': '#7a7a76', 'plate': '#6e6e6a',
         'leg': '#7e7e7a', 'foot': '#72726e', 'pelvis': '#767672', 'canopy': '#4f8a3a', 'nemes': '#c8a040',
         'ice': '#bfeaff', 'rune': '#60e0ff', 'branch': '#5a4028', 'core': '#ff8030', 'spike': '#3a3434', 'moss': '#4f7a30'}
    p.update(over)
    return p


def golem_face(p, eye, glow=None, mouth=True):
    f = p.face('head', 'front')
    f.rect(1, 3, 2, 4, (20, 18, 16))
    f.rect(5, 3, 6, 4, (20, 18, 16))
    f.px(1, 3, eye)
    f.px(2, 3, eye)
    f.px(5, 3, eye)
    f.px(6, 3, eye)
    f.hline(1, 6, 2, shade(f.get(3, 1), 0.6))
    if mouth:
        f.hline(2, 5, 7, (30, 26, 24))
    if glow is not None:
        g = p.face('head', 'front', glow=True)
        for x in (1, 2, 5, 6):
            g.px(x, 3, glow)


def runes(p, part, col, sides=('front',), every=3):
    for side in sides:
        g = p.face(part, side, glow=True)
        f = p.face(part, side)
        for y in range(2, g.h - 2, every):
            for x in range(2, g.w - 2, every + 1):
                if (x * 7 + y * 3) % 5 < 3:
                    g.px(x, y, col)
                    f.px(x, y, lighten(col, 0.2))


def variants():
    V = []
    bark = {'base': '#6a4a30', 'style': 'bark', 'moss': (80, 120, 50)}

    def deco_treant(p):
        golem_face(p, (255, 200, 60), glow=(255, 190, 40))
        for fc in p.all_faces('chest', sides_only=True):
            fc.vline(fc.w // 2, 2, fc.h - 3, (40, 26, 16))
    V.append(Variant('treant', show={'canopy', 'branch_r', 'branch_l', 'moss_beard'},
                     palette=pal(head=bark, body=bark, arm=bark, fist={'base': '#5c4028', 'style': 'bark'}, leg=bark,
                                 foot={'base': '#5c4028', 'style': 'bark'}, pelvis=bark,
                                 canopy={'base': '#4f8a3a', 'flowers': (230, 120, 160)}, branch={'base': '#5a4028', 'style': 'bark'},
                                 moss={'base': '#4f7a30', 'style': 'leaf'}),
                     paint=deco_treant, glow=True, extra=dict(scale=1.25)))
    sand = {'base': '#d8bf86', 'style': 'sandstone'}

    def deco_sand(p):
        golem_face(p, (60, 200, 255), glow=(60, 200, 255))
        runes(p, 'chest', (60, 200, 255), every=4)
        hf = p.face('headdress', 'front')
        for y in range(3, hf.h):
            for x in range(1, hf.w - 1):
                hf.clear(x, y)
        for fc in p.all_faces('headdress', sides_only=True):
            for y in range(0, fc.h, 2):
                fc.hline(0, fc.w - 1, y, (40, 70, 140))
    V.append(Variant('sand_golem', show={'headdress', 'shoulder_r', 'shoulder_l', 'core'},
                     palette=pal(head=sand, body=sand, arm=sand, fist={'base': '#c8ad74', 'style': 'sandstone'}, leg=sand,
                                 foot={'base': '#c8ad74', 'style': 'sandstone'}, pelvis=sand,
                                 plate={'base': '#b89a5c', 'style': 'sandstone'}, nemes={'base': '#d8b040', 'style': 'gold'},
                                 core={'base': '#40c8ff', 'style': 'glowcore'}),
                     paint=deco_sand, glow=True, extra=dict(scale=1.3)))
    ice = {'base': '#a8d4ee', 'style': 'ice'}

    def deco_ice(p):
        golem_face(p, (220, 250, 255), glow=(160, 230, 255))
    V.append(Variant('ice_golem', show={'ice_crown', 'crystal_r', 'crystal_l', 'shoulder_r', 'shoulder_l'},
                     palette=pal(head=ice, body=ice, arm=ice, fist={'base': '#94c4e4', 'style': 'ice'}, leg=ice,
                                 foot={'base': '#94c4e4', 'style': 'ice'}, pelvis=ice,
                                 plate={'base': '#d8f4ff', 'style': 'crystal'}, ice={'base': '#c8f0ff', 'style': 'crystal'}),
                     paint=deco_ice, glow=True, extra=dict(scale=1.35)))
    rock = {'base': '#3a3230', 'style': 'magma', 'hot': (255, 130, 30)}

    def deco_lava(p):
        golem_face(p, (255, 230, 120), glow=(255, 160, 40), mouth=False)
        f = p.face('head', 'front')
        f.hline(2, 5, 7, (255, 140, 40))
        p.face('head', 'front', glow=True).hline(2, 5, 7, (255, 120, 30))
        for c in p.rig.cubes:
            hot = c.__dict__.get('_hot')
            if not hot or not p.visible(c.part):
                continue
            for (face, w, h), pts in hot.items():
                g = p.face(c.part.name, face, index=c.part.cubes.index(c), glow=True)
                for (x, y) in pts:
                    g.px(x, y, (255, 120, 30))
        for fc in p.all_faces('core', glow=True):
            fc.rect(0, 0, fc.w - 1, fc.h - 1, (255, 150, 50))
    V.append(Variant('lava_golem', show={'back_spikes', 'core', 'shoulder_r', 'shoulder_l'},
                     palette=pal(head=rock, body=rock, arm=rock, fist=rock, leg=rock, foot=rock, pelvis=rock,
                                 plate=rock, spike={'base': '#2a2422', 'style': 'stone'},
                                 core={'base': '#ff8030', 'style': 'glowcore'}),
                     paint=deco_lava, glow=True, extra=dict(scale=1.3)))
    ruin = {'base': '#7a8478', 'style': 'stone'}

    def deco_ruin(p):
        golem_face(p, (120, 255, 220), glow=(100, 255, 210), mouth=False)
        runes(p, 'chest', (100, 255, 210), sides=('front', 'back'))
        runes(p, 'right_arm', (100, 255, 210), sides=('front', 'right'), every=4)
        runes(p, 'left_arm', (100, 255, 210), sides=('front', 'left'), every=4) if False else None
        for fc in p.all_faces('rune_halo', glow=True):
            fc.rect(0, 0, fc.w - 1, fc.h - 1, (100, 255, 210))
        for fc in p.all_faces('core', glow=True):
            fc.rect(0, 0, fc.w - 1, fc.h - 1, (100, 255, 210))
    V.append(Variant('ruin_construct', show={'shoulder_r', 'shoulder_l', 'core', 'rune_halo'},
                     palette=pal(head=ruin, body=ruin, arm=ruin, fist={'base': '#6a7468', 'style': 'stone'}, leg=ruin,
                                 foot={'base': '#6a7468', 'style': 'stone'}, pelvis=ruin,
                                 plate={'base': '#8a7a50', 'style': 'gold'}, rune={'base': '#60ffd0', 'style': 'glowcore'},
                                 core={'base': '#60ffd0', 'style': 'glowcore'}),
                     poses={'head': dict(pos=(0, 2.5, 0))},
                     paint=deco_ruin, glow=True, extra=dict(scale=1.2)))
    return V


def rig():
    return Rig('golem', 'GolemEnemy', geometry(), animations(), variants(), SLOTS)
