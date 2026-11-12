"""Palettes and painting of the four travellers (hero_cast.py), drawn with hero_paint.py at two texels per unit.

Every look is painted part by part in fine pixels: the face (HeroFace), the hair (cap, fringe, locks, long falls),
the costume (gradients, trims, folds, embroidery) and the special equipment, with an emissive layer for what
shines (the witch's star core and starry hem, the knight's ember fissures and the fire line of his sword, the
swordsman's rune channels and conquest seals, the sister's halo and tear crystal).
"""
from erpaint import lighten, mix, shade

import hero_paint as hp
from hero_paint import col, face, faces, put

GOLD = (222, 182, 84)
GOLD_LIGHT = (250, 222, 140)
SILVER = (206, 212, 222)


def arms_of(p):
    slim = p.visible(p.rig.parts['r_upper_slim'])
    return (('r_upper_slim', 'r_fore_slim', 'l_upper_slim', 'l_fore_slim') if slim
            else ('r_upper', 'r_fore', 'l_upper', 'l_fore'))


def torso_of(p):
    return 'torso_broad' if p.visible(p.rig.parts['torso_broad']) else 'torso'


def sleeve(p, c, trim=None, trim_row=None, folds_every=3):
    ru, rf, lu, lf = arms_of(p)
    for part in (ru, rf, lu, lf):
        for fc in faces(p, part):
            if fc.face in ('top', 'bottom'):
                hp.fill(fc, shade(col(c), 0.9), noise=0.02, key=part)
                continue
            hp.fill(fc, c, top=1.04, bottom=0.86, noise=0.03, key=part)
            hp.folds(fc, every=folds_every, f=0.9, phase=1 if 'fore' in part else 0)
            if trim is not None and trim_row is not None and 'fore' in part:
                hp.trim_row(fc, trim_row, trim)


def legs_fill(p, c, boots_c, boot_rows=8, trim=None, heel=False):
    for leg, shin in (('right_leg', 'right_shin'), ('left_leg', 'left_shin')):
        for fc in faces(p, leg):
            hp.fill(fc, c, top=1.0, bottom=0.9, noise=0.02, key=leg)
        for fc in faces(p, shin):
            hp.fill(fc, c, top=0.98, bottom=0.92, noise=0.02, key=shin)
            if fc.face in ('top',):
                continue
            for y in range(fc.fh - boot_rows if fc.face != 'bottom' else 0, fc.fh):
                for x in range(fc.fw):
                    g = 1.06 if y == fc.fh - boot_rows else 1.0 - 0.1 * (y - fc.fh + boot_rows) / max(1, boot_rows)
                    put(fc, x, y, shade(col(boots_c), g))
            if trim is not None and fc.face != 'bottom':
                hp.trim_row(fc, fc.fh - boot_rows, trim)
            if heel and fc.face == 'back':
                for x in range(2, fc.fw - 2):
                    put(fc, x, fc.fh - 1, shade(col(boots_c), 0.6))


# ================================================================================================= Rosalind

ROS_HAIR = (94, 36, 72)
ROS_SHEEN = (196, 120, 168)
ROS_DRESS = (28, 35, 80)
ROS_NIGHT = (40, 24, 74)
ROS_STAR = (255, 236, 170)


def rosalind_palette(palette):
    hair = '#5e2448'
    return palette(skin='#f6dccb', hair=hair, bangs=hair, lock=hair, hair_long=hair,
                   earring={'base': '#e8b04a', 'style': 'gem'}, hat_brim='#1a1c3c', hat='#1a1c3c',
                   tunic='#1c2350', bust='#1c2350', sleeve='#1c2350', sleeve2='#1c2350', cuff='#1c2350',
                   dress='#1c2350', dress2='#1c2350', waist='#1c2350', shawl='#2a1e48', pants='#2a2030',
                   boots='#20182a', tome='#3a1a40', staff='#3a2420', core={'base': '#fff4d0', 'style': 'glowcore'},
                   ring='#e0c060', charm='#e0c060', charm_star={'base': '#ffe080', 'style': 'gem'})


def paint_rosalind(p):
    skin = col('#f6dccb')
    hp.HeroFace(skin, iris_r=(226, 156, 58), kind='female', eyes='calm', brow=(84, 34, 62), lips=(190, 62, 92),
                blush=(244, 150, 160), beauty_mark=(12, 13), lash=(44, 16, 36)).paint(p)
    # hair: long waves, a side part, the sheen
    hp.hair_cap(p, ROS_HAIR, front_rows=2, frame_cols=3, frame_rows=16, side_rows=16, back_rows=16, ring=(5, 7),
                sheen=0.15, top_part=11)
    hp.bangs(p, ROS_HAIR, [12, 12, 11, 10, 9, 9, 8, 8, 7, 7, 6, 5, 3, 5, 7, 9, 11, 12], ring_row=3, sheen=0.15)
    for part in ('lock_r', 'lock_l', 'lock_r2', 'lock_l2'):
        hp.hair_strip(p, part, ROS_HAIR, ragged=part.endswith('2'), wave=4, ring=(4, 6) if part.endswith(('r',
                                                                                                         'l')) else None,
                      sheen=0.15)
    hp.hair_strip(p, 'hair_long', ROS_HAIR, ragged=False, wave=5, ring=(3, 5), sheen=0.15)
    hp.hair_strip(p, 'hair_long2', ROS_HAIR, ragged=True, wave=5, tips=[2, 4, 1, 3, 5, 2, 0, 3, 4, 1, 2, 5, 3, 1, 4,
                                                                         2])
    # earrings: gold drops with an amber stone
    for side in ('earring_r', 'earring_l'):
        for fc in faces(p, side):
            for y in range(fc.fh):
                for x in range(fc.fw):
                    put(fc, x, y, GOLD if y < 3 else (236, 150, 50))
            put(fc, 0, fc.fh - 2, (255, 220, 140))
            hp.glow(p, side, fc.face, 0, fc.fh - 2, (255, 200, 110))
    # the choker and the neckline: skin at the collarbones above the bodice
    t = torso_of(p)
    f = face(p, t, 'front')
    hp.fill(f, ROS_DRESS, top=1.05, bottom=0.85, noise=0.02, key='rosdress')
    for y in range(0, 4):
        for x in range(f.fw):
            if y < 3 or 4 <= x <= 11:
                put(f, x, y, shade(skin, 0.97 - 0.02 * y))
    hp.hline(f, 5, 10, 0, (30, 18, 30))
    put(f, 7, 1, (240, 170, 60))
    put(f, 8, 1, (240, 170, 60))
    hp.glow(p, t, 'front', 7, 1, (255, 190, 90))
    hp.glow(p, t, 'front', 8, 1, (255, 190, 90))
    # the corset under the bust: plum-black, gold lacing
    for y in range(9, f.fh):
        for x in range(3, 13):
            put(f, x, y, shade((54, 26, 56), 1.05 - 0.04 * (y - 9)))
    hp.lacing(f, 6, 9, 9, 13, GOLD, every=2)
    for y in range(9, f.fh):
        put(f, 3, y, GOLD)
        put(f, 12, y, GOLD)
    for name in ('right', 'left', 'back'):
        fc = face(p, t, name)
        hp.fill(fc, ROS_DRESS, top=1.0, bottom=0.85, noise=0.02, key='rosdress' + name)
        for y in range(0, 3):
            for x in range(fc.fw):
                put(fc, x, y, shade(skin, 0.94))
        for y in range(9, fc.fh):
            for x in range(fc.fw):
                put(fc, x, y, shade((54, 26, 56), 1.0 - 0.04 * (y - 9)))
    hp.fill(face(p, t, 'top'), skin, noise=0.0)
    # the bodice over the bust: a gold edge, a gentle sheen
    for fc in faces(p, 'bust'):
        hp.fill(fc, ROS_DRESS, top=1.15, bottom=0.9, noise=0.02, key='bust')
        if fc.face == 'front':
            hp.trim_row(fc, 0, GOLD)
            for x in (6, 7):
                put(fc, x, 0, shade(skin, 0.9))
            put(fc, 6, 1, GOLD)
            put(fc, 7, 1, GOLD)
            for x in range(2, 5):
                hp.blend(fc, x, 2, (110, 120, 190), 0.25)
    # the capelet: violet night with gold star embroidery, a star clasp that shines
    for fc in faces(p, 'shawl'):
        hp.fill(fc, ROS_NIGHT, top=1.1, bottom=0.85, noise=0.03, key='shawl')
        if fc.face != 'top' and fc.face != 'bottom':
            hp.trim_row(fc, fc.fh - 1, GOLD)
            hp.stars(fc, ROS_STAR, density=0.05, key='shawl', glow_fc=face(p, 'shawl', fc.face, glow=True))
        if fc.face == 'top':
            for x in range(fc.fw):
                for y in range(fc.fh):
                    if 4 <= x <= fc.fw - 5 and 3 <= y <= fc.fh - 4:
                        hp.clear(fc, x, y)
        if fc.face == 'bottom':
            hp.clear_face(fc)
    sf = face(p, 'shawl', 'front')
    for (x, y) in ((9, 2), (10, 2), (9, 3), (10, 3), (8, 3), (11, 3), (9, 4), (10, 4), (9, 1), (10, 1)):
        put(sf, x, y, (255, 226, 130))
        hp.glow(p, 'shawl', 'front', x, y, (255, 220, 120))
    for y in range(0, 2):
        for x in range(3, sf.fw - 3):
            if x not in (9, 10):
                hp.clear(sf, x, y)
    # sleeves and bell cuffs
    sleeve(p, ROS_DRESS)
    for side in ('cuff_r', 'cuff_l'):
        for fc in faces(p, side):
            if fc.face == 'bottom':
                for y in range(fc.fh):
                    for x in range(fc.fw):
                        put(fc, x, y, (64, 30, 80) if 1 <= x < fc.fw - 1 and 1 <= y < fc.fh - 1 else GOLD)
                continue
            if fc.face == 'top':
                hp.clear_face(fc)
                continue
            hp.fill(fc, ROS_DRESS, top=0.95, bottom=1.08, noise=0.03, key=side)
            hp.trim_row(fc, fc.fh - 1, GOLD)
            hp.trim_row(fc, fc.fh - 2, shade(GOLD, 0.75), shine=False)
            hp.stars(fc, ROS_STAR, density=0.06, key=side, glow_fc=face(p, side, fc.face, glow=True))
    ru, rf, lu, lf = arms_of(p)
    hp.hands(p, skin, rf, lf, rows=3)
    for part in (rf, lf):
        for fc in faces(p, part):
            if fc.face not in ('top', 'bottom'):
                for x in range(0, fc.fw, 2):
                    put(fc, x, fc.fh - 1, (90, 30, 70))      # plum nails
    # the dress: midnight at the waist, deepening to a starry violet at the hem; the slit on her left
    for part, top_c, bot_c in (('hips', ROS_DRESS, ROS_DRESS), ('dress', ROS_DRESS, (34, 30, 84)),
                               ('dress2', (34, 30, 84), (52, 26, 86))):
        for fc in faces(p, part):
            if fc.face == 'top' and part != 'hips':
                hp.clear_face(fc)
                continue
            for y in range(fc.fh):
                k = y / max(1, fc.fh - 1)
                for x in range(fc.fw):
                    c = mix(top_c, bot_c, k)
                    put(fc, x, y, shade(c, (1.04 - 0.1 * k) * (0.94 if x in (0, fc.fw - 1) else 1.0)
                                    * (1.0 + 0.03 * (hp.h01(part, fc.face, x, y) - 0.5))))
            if fc.face in ('front', 'back', 'right', 'left'):
                hp.folds(fc, every=4, f=0.86, phase=(2 if part == 'dress2' else 0))
                if part != 'hips':
                    hp.stars(fc, ROS_STAR, density=0.035 if part == 'dress' else 0.07, key=part,
                             glow_fc=face(p, part, fc.face, glow=True))
            if part == 'dress2' and fc.face in ('front', 'back', 'right', 'left'):
                hp.trim_row(fc, fc.fh - 1, GOLD)
                hp.trim_row(fc, fc.fh - 2, shade(GOLD, 0.7), shine=False)
                for x in range(0, fc.fw, 3):
                    put(fc, x, fc.fh - 3, mix(GOLD, (52, 26, 86), 0.4))
            if fc.face == 'bottom':
                hp.fill(fc, (30, 16, 50), noise=0.0)
    hf = face(p, 'hips', 'front')
    for x in range(hf.fw):
        put(hf, x, 2, GOLD if x % 3 else GOLD_LIGHT)               # a thin gold belt chain
    put(hf, 8, 3, (240, 170, 60))
    hp.glow(p, 'hips', 'front', 8, 3, (255, 190, 90))
    d2 = face(p, 'dress2', 'front')
    for y in range(3, d2.fh - 1):
        for x in range(15, 18):
            hp.clear(d2, x, y)                                      # the slit shows her left leg
        put(d2, 14, y, GOLD)
        put(d2, 18, y, GOLD)
    # stockings and heeled boots
    legs_fill(p, (40, 28, 46), (32, 22, 40), boot_rows=6, trim=GOLD, heel=True)
    for leg in ('right_leg', 'left_leg'):
        for fc in faces(p, leg):
            if fc.face in ('front', 'right', 'left', 'back'):
                for x in range(fc.fw):
                    put(fc, x, 0, (52, 30, 56))
                    put(fc, x, 1, GOLD if x % 2 == 0 else (40, 28, 46))   # the garter's lace
    # the hat: midnight felt, a gold star band, scattered stars, a shining star at the tip
    hat_c = (26, 28, 64)
    for part in ('hat_brim', 'hat_cone', 'hat_cone2', 'hat_tip', 'hat_tip2'):
        for fc in faces(p, part):
            hp.fill(fc, hat_c, top=1.08, bottom=0.82, noise=0.04, key=part)
            if part != 'hat_brim' and fc.face in ('front', 'back', 'right', 'left'):
                hp.folds(fc, every=3, f=0.84, phase=1)
                hp.stars(fc, ROS_STAR, density=0.04, key=part, glow_fc=face(p, part, fc.face, glow=True))
    brim = face(p, 'hat_brim', 'top')
    for y in range(brim.fh):
        for x in range(brim.fw):
            d = max(abs(x - 15.5), abs(y - 15.5))
            g = 1.1 - 0.25 * (d / 16.0)
            put(brim, x, y, shade(hat_c, g * (1.0 + 0.04 * (hp.h01('brim', x, y) - 0.5))))
            if d >= 15:
                put(brim, x, y, GOLD if (x + y) % 3 else GOLD_LIGHT)
    hp.stars(brim, ROS_STAR, density=0.03, key='brimtop', glow_fc=face(p, 'hat_brim', 'top', glow=True))
    under = face(p, 'hat_brim', 'bottom')
    for y in range(under.fh):
        for x in range(under.fw):
            put(under, x, y, (46, 22, 70) if max(abs(x - 15.5), abs(y - 15.5)) < 15 else GOLD)
    hp.stars(under, (230, 210, 255), density=0.05, key='brimunder')
    for fc in faces(p, 'hat_brim', sides=True):
        for y in range(fc.fh):
            for x in range(fc.fw):
                put(fc, x, y, GOLD if y == 0 else shade(GOLD, 0.7))
    for fc in faces(p, 'hat_cone', sides=True):
        for y in range(fc.fh - 3, fc.fh):
            for x in range(fc.fw):
                put(fc, x, y, GOLD if y != fc.fh - 2 else GOLD_LIGHT)
    cf = face(p, 'hat_cone', 'front')
    for (x, y) in ((7, 4), (8, 4), (6, 5), (7, 5), (8, 5), (9, 5), (7, 6), (8, 6), (7, 3), (8, 3), (5, 5), (10, 5),
                   (6, 7), (9, 7)):
        put(cf, x, y, (255, 228, 140))
        hp.glow(p, 'hat_cone', 'front', x, y, (255, 222, 120))
    for fc in faces(p, 'hat_tip2'):
        if fc.face in ('front', 'right', 'left', 'back', 'top'):
            for x in range(fc.fw):
                put(fc, x, 0, (255, 236, 160))
                hp.glow(p, 'hat_tip2', fc.face, x, 0, (255, 230, 140))
    for fc in faces(p, 'hat_charm', index=0):
        hp.clear_face(fc)
        if fc.face in ('front', 'back', 'right', 'left'):
            for y in range(fc.fh):
                put(fc, 0 if fc.face in ('front', 'right') else 1, y, GOLD if y % 2 else GOLD_LIGHT)
    for fc in faces(p, 'hat_charm', index=1):
        hp.clear_face(fc)
        if fc.face in ('front', 'back'):
            for (x, y) in ((1, 0), (2, 0), (0, 1), (1, 1), (2, 1), (3, 1), (1, 2), (2, 2), (0, 3), (3, 3)):
                put(fc, x, y, (255, 226, 130))
                hp.glow(p, 'hat_charm', fc.face, x, y, (255, 220, 120), index=1)
    # the armillary staff: dark wood wound with gold, the star core, three rings with runes
    for fc in faces(p, 'staff_witch', index=0):
        for y in range(fc.fh):
            for x in range(fc.fw):
                c = shade((60, 36, 30), 1.0 + 0.1 * (hp.h01('staffw', x, y) - 0.5))
                if (y + x * 2) % 11 in (0, 1):
                    c = GOLD
                put(fc, x, y, c)
    for i in (1, 2):
        for fc in faces(p, 'staff_witch', index=i):
            hp.fill(fc, GOLD, noise=0.0)
    for i in (3, 4, 5, 6):
        for fc in faces(p, 'staff_witch', index=i):
            hp.fill(fc, (70, 42, 34), noise=0.04, key='prong%d' % i)
            put(fc, 0, 0, GOLD)
    for fc in faces(p, 'staff_core'):
        for y in range(fc.fh):
            for x in range(fc.fw):
                put(fc, x, y, (255, 250, 220) if 1 <= x <= 2 and 1 <= y <= 2 else (255, 214, 120))
    hp.glow_part(p, 'staff_core', (255, 236, 170))
    for ring, rune in (('ring_a', (150, 220, 255)), ('ring_b', (255, 210, 120)), ('ring_c', (200, 160, 255))):
        rf_ = face(p, ring, 'front')
        rb = face(p, ring, 'back')
        n = rf_.fw
        cx = (n - 1) / 2.0
        for y in range(n):
            for x in range(n):
                d = ((x - cx) ** 2 + (y - cx) ** 2) ** 0.5
                if cx - 1.4 <= d <= cx + 0.4:
                    c = GOLD if (x + y) % 4 else GOLD_LIGHT
                    put(rf_, x, y, c)
                    put(rb, n - 1 - x, y, shade(c, 0.85))
                    if (x * 7 + y * 3) % 5 == 0:
                        put(rf_, x, y, rune)
                        hp.glow(p, ring, 'front', x, y, rune)
                else:
                    hp.clear(rf_, x, y)
                    hp.clear(rb, n - 1 - x, y)
    # the grimoire: violet leather, gold corners, a shining sigil; the open page with lines of writing
    for fc in faces(p, 'grimoire'):
        hp.fill(fc, (70, 28, 76), noise=0.04, key='grim')
        if fc.face in ('top', 'bottom'):
            for (x, y) in ((0, 0), (fc.fw - 1, 0), (0, fc.fh - 1), (fc.fw - 1, fc.fh - 1), (1, 0), (0, 1),
                           (fc.fw - 2, 0), (fc.fw - 1, 1), (0, fc.fh - 2), (1, fc.fh - 1), (fc.fw - 2, fc.fh - 1),
                           (fc.fw - 1, fc.fh - 2)):
                put(fc, x, y, GOLD)
        else:
            for x in range(fc.fw):
                put(fc, x, 0, (236, 226, 200))
    gt = face(p, 'grimoire', 'bottom')
    cx, cy = gt.fw // 2, gt.fh // 2
    for (dx, dy) in ((0, -3), (0, 3), (-3, 0), (3, 0), (-2, -2), (2, 2), (-2, 2), (2, -2), (0, 0), (0, -1), (0, 1),
                     (-1, 0), (1, 0)):
        put(gt, cx + dx, cy + dy, (255, 210, 120))
        hp.glow(p, 'grimoire', 'bottom', cx + dx, cy + dy, (255, 200, 110))
    for fc in faces(p, 'grimoire_page'):
        hp.fill(fc, (244, 236, 214), noise=0.0)
        for y in range(1, fc.fh - 1, 2):
            for x in range(0, fc.fw - 1):
                put(fc, x, y, (150, 110, 160) if (x + y) % 3 else (244, 236, 214))
    hp.glow_face(p, 'grimoire_page', 'top', (200, 170, 255), mask=lambda x, y: y % 2 == 1)


# ================================================================================================= Siegwald

SIEG_STEEL = (150, 156, 166)
SIEG_BLUE = (36, 64, 138)
SIEG_CRIMSON = (138, 30, 36)
EMBER = (255, 132, 40)


def siegwald_palette(palette):
    hair = '#9a7434'
    return palette(skin='#e8bea0', hair=hair, sleeve='#24408a', sleeve2='#24408a', tunic='#24408a', waist='#3a2a20',
                   pants='#2a2a34', boots='#2a2420', mantle={'base': '#6a5a4a', 'style': 'fur'})


def paint_siegwald(p):
    skin = col('#e8bea0')
    hair = (154, 116, 52)
    hp.HeroFace(skin, iris_r=(112, 146, 172), kind='male', eyes='sharp', brow=(110, 78, 38), smile=False,
                scar=((12, 3), (10, 13)), stubble=(110, 82, 52)).paint(p)
    hp.hair_cap(p, hair, front_rows=3, frame_cols=1, frame_rows=7, side_rows=7, back_rows=12, ring=(1, 2),
                notch_ear=True, sheen=0.12)
    hf = face(p, 'hair_cap', 'front')
    head = face(p, 'head', 'front')
    for x in range(16):
        depth = 3 + (1 if x in (6, 7, 8, 9) else 0) - (1 if x in (0, 1, 14, 15) else 0)
        for y in range(depth):
            put(hf, x, y, hp.strand(hair, x, y, 16, 'sfront', ring=(1, 1), sheen=0.12))
            put(head, x, y, shade(hair, 0.6))
    for (x, y) in ((10, 3), (10, 4), (11, 4), (11, 5), (11, 6), (12, 6)):
        put(hf, x, y, mix(hair, (232, 200, 130), 0.35))         # one lock falls over the brow
    top = face(p, 'hair_cap', 'top')
    for y in range(top.fh):
        for x in range(top.fw):
            v = hp.strand(hair, x // 2 + y // 3, y, top.fh, 'swtop')
            if (top.fh - 1 - y) <= 3:
                v = mix(v, lighten(hair, 0.45), 0.4)
            put(top, x, y, v)
    # plate: battle-worn steel, a brass edge, dents and scratches
    for part in ('cuirass', 'pauldron_r', 'gauntlet_r', 'gauntlet_l', 'faulds', 'greave_r', 'greave_l', 'knee_r',
                 'knee_l'):
        n = len(p.rig.parts[part].cubes)
        for i in range(n):
            for fc in faces(p, part, i):
                hp.plate(fc, SIEG_STEEL, rivets=part in ('pauldron_r', 'faulds'), scratches=3, dents=2,
                         key=part + str(i))
    for name in ('front', 'back', 'right', 'left'):
        for part in ('faulds',):
            fc = face(p, part, name)
            for y in (3, 7):
                for x in range(fc.fw):
                    put(fc, x, y, shade(SIEG_STEEL, 0.6))
    # the tabard over the cuirass: royal blue, the winged star in white gold, torn by three claws
    cf = face(p, 'cuirass', 'front')
    for y in range(1, cf.fh):
        for x in range(4, 16):
            put(cf, x, y, shade(SIEG_BLUE, 1.06 - 0.12 * y / cf.fh) if x not in (4, 15) else GOLD)
    for x in range(4, 16):
        put(cf, x, 1, GOLD)
    crest = [(0, -3), (-1, -2), (0, -2), (1, -2), (-3, -1), (-2, -1), (-1, -1), (0, -1), (1, -1), (2, -1), (3, -1),
             (-1, 0), (0, 0), (1, 0), (-2, 1), (0, 1), (2, 1), (0, 2), (-4, -2), (4, -2), (-5, -3), (5, -3)]
    for (dx, dy) in crest:
        put(cf, 10 + dx, 7 + dy, (240, 232, 200) if dy < 0 else (224, 200, 140))
    for k, x0 in enumerate((6, 9, 12)):
        for j in range(9):
            x, y = x0 + j // 2 - 1, 3 + j
            put(cf, x, y, SIEG_STEEL if j % 3 else lighten(SIEG_STEEL, 0.3))
            hp.blend(cf, x + 1, y, (20, 20, 30), 0.6)
    for x in range(cf.fw):
        put(cf, x, 0, (186, 150, 70))
    hp.plate(face(p, 'cuirass', 'top'), SIEG_STEEL, key='ctop')
    # the torso and arms under the plate: royal blue gambeson
    t = torso_of(p)
    for fc in faces(p, t):
        hp.fill(fc, SIEG_BLUE, noise=0.03, key='gamb')
    sleeve(p, SIEG_BLUE, folds_every=3)
    ru, rf, lu, lf = arms_of(p)
    for part in (ru, lu):
        for fc in faces(p, part):
            if fc.face in ('front', 'back', 'right', 'left'):
                for y in range(0, fc.fh, 3):
                    for x in range(fc.fw):
                        hp.tint(fc, x, y, 0.85)                  # quilted
    # the dragon-scale pauldron: overlapping crimson scales, ember light between them, a horn
    for i in (0, 1):
        for fc in faces(p, 'pauldron_dragon', i):
            for y in range(fc.fh):
                for x in range(fc.fw):
                    row = y // 2
                    sx = (x + (2 if row % 2 else 0)) % 4
                    c = (124, 30, 26) if sx in (1, 2) else (88, 18, 18)
                    if y % 2 == 0 and sx == 1:
                        c = (170, 56, 40)
                    put(fc, x, y, shade(c, 1.04 - 0.1 * y / max(1, fc.fh - 1)))
                    if sx == 0 and y % 2 == 1:
                        put(fc, x, y, (255, 120, 40))
                        hp.glow(p, 'pauldron_dragon', fc.face, x, y, EMBER, index=i)
    for fc in faces(p, 'pauldron_dragon', 2):
        for y in range(fc.fh):
            for x in range(fc.fw):
                put(fc, x, y, shade((232, 220, 192), 1.1 - 0.4 * (y / max(1, fc.fh - 1)) if fc.face != 'top'
                                    else 1.15))
    # fur mantle, crimson cape scorched by dragon fire, ragged hem
    for fc in faces(p, 'mantle_fur'):
        for y in range(fc.fh):
            for x in range(fc.fw):
                tip = (x * 3 + y) % 4 == 0
                put(fc, x, y, shade((112, 96, 78), (1.15 if tip else 0.95) * (1.05 - 0.15 * y / max(1, fc.fh))))
        if fc.face == 'bottom':
            hp.fill(fc, (70, 58, 46), noise=0.0)
    for part in ('cape', 'cape2'):
        for fc in faces(p, part):
            hp.fill(fc, SIEG_CRIMSON if fc.face != 'front' else shade(SIEG_CRIMSON, 0.7), top=1.05, bottom=0.88,
                    noise=0.04, key=part)
            if fc.face in ('back', 'front'):
                hp.folds(fc, every=5, f=0.82, phase=2)
                for y in range(fc.fh):
                    put(fc, 0, y, GOLD)
                    put(fc, fc.fw - 1, y, GOLD)
    cb = face(p, 'cape2', 'back')
    for x in range(cb.fw):
        cut = int(hp.h01('caperag', x) * 4)
        for d in range(cut):
            hp.clear(cb, x, cb.fh - 1 - d)
        cf2 = face(p, 'cape2', 'front')
        for d in range(cut):
            hp.clear(cf2, cf2.fw - 1 - x, cf2.fh - 1 - d)
    for (x, y) in ((15, 12), (16, 12), (17, 13), (15, 13), (16, 13), (14, 14), (15, 14), (16, 14), (17, 14), (16, 15),
                   (15, 15), (17, 15), (16, 16)):
        put(cb, x, y, (34, 16, 14))
    for (x, y) in ((16, 13), (15, 14), (17, 15)):
        put(cb, x, y, (255, 110, 40))
        hp.glow(p, 'cape2', 'back', x, y, EMBER)
    # tabard panels below the belt: blue, gold edge, torn
    for part in ('tabard_f', 'tabard_b'):
        for fc in faces(p, part):
            hp.fill(fc, SIEG_BLUE, noise=0.04, key=part)
            if fc.face in ('front', 'back'):
                for y in range(fc.fh):
                    put(fc, 0, y, GOLD)
                    put(fc, fc.fw - 1, y, GOLD)
                for x in range(fc.fw):
                    cut = int(hp.h01(part, 'rag', x) * 3)
                    for d in range(cut):
                        hp.clear(fc, x, fc.fh - 1 - d)
    tf = face(p, 'tabard_f', 'front')
    for j in range(8):
        put(tf, 3 + j // 2, 2 + j, (20, 20, 30))
        put(tf, 7 + j // 2, 1 + j, (20, 20, 30))
    # belt, mail and boots
    hips = face(p, 'hips', 'front')
    hp.fill(hips, (58, 40, 30), noise=0.03, key='hips')
    for name in ('right', 'left', 'back'):
        hp.fill(face(p, 'hips', name), (58, 40, 30), noise=0.03, key='hips' + name)
    hp.rect(hips, 7, 3, 12, 6, GOLD)
    hp.rect(hips, 8, 4, 11, 5, (60, 44, 34))
    for leg in ('right_leg', 'left_leg', 'right_shin', 'left_shin'):
        for fc in faces(p, leg):
            for y in range(fc.fh):
                for x in range(fc.fw):
                    put(fc, x, y, (92, 96, 106) if (x + y) % 2 else (54, 56, 64))
    # gauntlets: articulated fingers; the iron sword
    for g in ('gauntlet_r', 'gauntlet_l'):
        for fc in faces(p, g):
            if fc.face in ('front', 'back', 'right', 'left'):
                for x in range(fc.fw):
                    put(fc, x, fc.fh - 3, shade(SIEG_STEEL, 0.6))
                for x in range(0, fc.fw, 2):
                    put(fc, x, fc.fh - 1, shade(SIEG_STEEL, 0.55))
    sword_iron(p)


def sword_iron(p):
    """The nameless iron sword: a leather grip, a plain steel guard and pommel, a long notched blade with a fuller
    in which a thin line of the dragon's fire still glows."""
    for fc in faces(p, 'sword_iron', 0):
        for y in range(fc.fh):
            for x in range(fc.fw):
                put(fc, x, y, (70, 46, 30) if (x + y) % 3 else (44, 28, 18))
    for i in (1, 2):
        for fc in faces(p, 'sword_iron', i):
            hp.plate(fc, (120, 124, 132), key='sguard%d' % i, scratches=1)
    for fc in faces(p, 'sword_iron', 3):
        n = fc.fw
        for y in range(fc.fh):
            for x in range(n):
                if fc.face in ('right', 'left'):
                    edge = y in (0, fc.fh - 1)
                    c = (204, 208, 214) if edge else ((112, 116, 124) if y in (1, 2) and fc.fh > 3 else (156, 160, 168))
                    if fc.fh == 4 and y in (1, 2):
                        c = (128, 132, 140)
                    put(fc, x, y, c)
                else:
                    put(fc, x, y, (196, 200, 206))
        if fc.face in ('right', 'left'):
            tip_at = n - 1 if fc.face == 'right' else 0
            for d in range(3):
                for y in range(fc.fh):
                    if (y == 0 and d < 2) or (y == fc.fh - 1 and d < 1) or d < 1:
                        pass
            for x in range(n):
                if hp.h01('nick', fc.face, x) < 0.12:
                    put(fc, x, 0, (90, 92, 100))           # nicks in the edge
            for x in range(n):
                xx = x if fc.face == 'right' else n - 1 - x
                if 2 <= xx <= n - 6:
                    hp.glow(p, 'sword_iron', fc.face, x, 1 if fc.fh > 2 else 0, (255, 120, 40), index=3,
                            body=(224, 112, 52))
            put(fc, tip_at, 0, (0, 0, 0), 0)
            put(fc, tip_at, fc.fh - 1, (0, 0, 0), 0)


# ================================================================================================= Kaylen

KAY_TEAL = (30, 74, 76)
KAY_LINING = (82, 24, 32)
KAY_HAIR = (216, 220, 226)
CYAN = (90, 236, 246)


def kaylen_palette(palette):
    hair = '#d8dce0'
    return palette(skin='#efd0bc', hair=hair, bangs=hair, tail=hair, sleeve='#1e4a4c', sleeve2='#1e4a4c',
                   tunic='#1e4a4c', waist='#1a1a20', pants='#22222a', boots='#18161c', collar='#1e4a4c',
                   coat_tail='#1e4a4c', bracer='#1a1a20')


def paint_kaylen(p):
    skin = col('#efd0bc')
    hp.HeroFace(skin, iris_r=(236, 190, 60), iris_l=(64, 216, 232), kind='male', eyes='sharp', brow=(150, 156, 172),
                smile=True, lash=(40, 40, 54)).paint(p)
    hp.hair_cap(p, KAY_HAIR, front_rows=3, frame_cols=2, frame_rows=11, side_rows=11, back_rows=13, ring=(3, 4),
                sheen=0.05)
    # the fringe falls over his right eye
    hp.bangs(p, KAY_HAIR, [12, 12, 12, 12, 11, 10, 11, 9, 7, 5, 6, 7, 5, 4, 5, 6, 8, 10], ring_row=3)
    shadow = (138, 150, 178)
    for part in ('hair_cap', 'bangs'):
        for fc in faces(p, part):
            for y in range(fc.fh):
                for x in range(fc.fw):
                    if not hp.alpha(fc, x, y):
                        continue
                    if (x * 5 + y // 3) % 4 == 0:
                        hp.blend(fc, x, y, shadow, 0.55)          # cool shadows between the strands
                    elif (x * 5 + y // 3) % 4 == 2 and fc.face != 'top':
                        hp.blend(fc, x, y, (255, 255, 255), 0.45)
    for name in ('right', 'left', 'back'):
        fc = face(p, 'hair_cap', name)
        for x in range(fc.fw):
            cut = (0, 2, 1, 3, 0, 1, 2, 0)[(x * 3) % 8]
            bottom = max(y for y in range(fc.fh) if hp.alpha(fc, x, y)) if any(
                hp.alpha(fc, x, y) for y in range(fc.fh)) else -1
            for d in range(cut):
                if bottom - d >= 0:
                    hp.clear(fc, x, bottom - d)
    top = face(p, 'hair_cap', 'top')
    for y in range(top.fh):
        for x in range(top.fw):
            dx, dy = x - 8, y - 6
            k = (abs(dx) * 3 + abs(dy) * 2) % 5
            put(top, x, y, shade(KAY_HAIR, (0.86, 0.95, 1.04, 0.92, 1.0)[k]))
    hp.hair_strip(p, 'tail', KAY_HAIR, ragged=False)
    hp.hair_strip(p, 'tail2', KAY_HAIR, ragged=True, taper=True, tips=[2, 1])
    for fc in faces(p, 'tail'):
        if fc.face in ('front', 'back', 'right', 'left'):
            for x in range(fc.fw):
                put(fc, x, fc.fh - 2, (196, 40, 44))
                put(fc, x, fc.fh - 1, (150, 26, 30))
    for fc in faces(p, 'tail2'):
        if fc.face in ('front', 'back', 'right', 'left'):
            for y in (0, 1, 2):
                for x in range(fc.fw):
                    put(fc, x, y, (196, 40, 44) if y != 1 else (232, 70, 70))
            for x in range(fc.fw):
                put(fc, x, 18, SILVER)
                put(fc, x, 19, (150, 156, 170))
    # the coat: deep teal, silver trim down the front, black shirt at the throat, a cross belt
    t = torso_of(p)
    f = face(p, t, 'front')
    hp.fill(f, KAY_TEAL, top=1.08, bottom=0.88, noise=0.03, key='kcoat')
    for y in range(f.fh):
        for x in (7, 8):
            put(f, x, y, (24, 24, 30) if y < 6 else KAY_TEAL)
        put(f, 6, y, SILVER)
        put(f, 9, y, shade(SILVER, 0.8))
    for y in (8, 11):
        put(f, 7, y, SILVER)
        put(f, 8, y, SILVER)
    for j in range(f.fh):
        x = 2 + j
        if x < f.fw:
            put(f, x, j, (30, 26, 30))
            if x + 1 < f.fw:
                put(f, x + 1, j, (48, 40, 44))
    put(f, 6, 6, SILVER)
    for name in ('right', 'left', 'back'):
        fc = face(p, t, name)
        hp.fill(fc, KAY_TEAL, noise=0.03, key='kcoat' + name)
        hp.folds(fc, every=4, f=0.86)
    sleeve(p, KAY_TEAL, folds_every=4)
    ru, rf, lu, lf = arms_of(p)
    for part in (rf, lf):
        for fc in faces(p, part):
            if fc.face in ('front', 'back', 'right', 'left'):
                for y in range(fc.fh - 4, fc.fh):
                    for x in range(fc.fw):
                        put(fc, x, y, (26, 24, 30) if y < fc.fh - 1 else skin)   # fingerless gloves
    for b in ('bracer_r', 'bracer_l'):
        for fc in faces(p, b):
            hp.fill(fc, (28, 26, 32), noise=0.03, key=b)
            if fc.face in ('front', 'back', 'right', 'left'):
                hp.trim_row(fc, 0, SILVER)
                put(fc, fc.fw // 2, 3, SILVER)
    for fc in faces(p, 'collar_high'):
        hp.fill(fc, KAY_TEAL, top=1.12, bottom=0.92, noise=0.02, key='collar')
        if fc.face in ('front', 'right', 'left', 'back'):
            hp.trim_row(fc, 0, SILVER)
        if fc.face == 'top':
            for y in range(2, fc.fh - 2):
                for x in range(2, fc.fw - 2):
                    hp.clear(fc, x, y)
        if fc.face == 'bottom':
            hp.clear_face(fc)
    cf = face(p, 'collar_high', 'front')
    for y in range(1, cf.fh):
        for x in range(7, 11):
            hp.clear(cf, x, y)
    # the silver shoulder guard with a rune
    for fc in faces(p, 'pauldron_s'):
        hp.plate(fc, SILVER, key='ps' + fc.face, scratches=1)
    pt = face(p, 'pauldron_s', 'top')
    for (x, y) in ((5, 4), (6, 5), (6, 6), (5, 7), (6, 8), (7, 7), (7, 5)):
        put(pt, x, y, CYAN)
        hp.glow(p, 'pauldron_s', 'top', x, y, CYAN)
    # coat tails: teal outside, crimson lining, silver edges
    for part in ('coat_tail_r', 'coat_tail_l', 'coat_flap_r', 'coat_flap_l'):
        for fc in faces(p, part):
            outside = fc.face == ('back' if 'tail' in part else 'front')
            hp.fill(fc, KAY_TEAL if outside or fc.face in ('right', 'left') else KAY_LINING, top=1.04, bottom=0.86,
                    noise=0.03, key=part + fc.face)
            if fc.face in ('front', 'back'):
                hp.folds(fc, every=4, f=0.86, phase=1)
                for y in range(fc.fh):
                    put(fc, 0, y, SILVER if outside else shade(SILVER, 0.7))
                    put(fc, fc.fw - 1, y, SILVER if outside else shade(SILVER, 0.7))
                hp.trim_row(fc, fc.fh - 1, SILVER)
    # belt, trousers, boots
    hips = face(p, 'hips', 'front')
    hp.fill(hips, (26, 24, 30), noise=0.02, key='khips')
    for name in ('right', 'left', 'back'):
        hp.fill(face(p, 'hips', name), (26, 24, 30), noise=0.02, key='khips' + name)
    for fc in faces(p, 'hips', sides=True):
        for x in range(fc.fw):
            put(fc, x, 2, (60, 52, 50))
            put(fc, x, 3, (40, 36, 36))
    hp.rect(hips, 7, 1, 9, 4, SILVER)
    put(hips, 8, 2, CYAN)
    hp.glow(p, 'hips', 'front', 8, 2, CYAN)
    legs_fill(p, (32, 32, 40), (22, 20, 26), boot_rows=9, trim=SILVER)
    for shin in ('right_shin', 'left_shin'):
        for fc in faces(p, shin):
            if fc.face == 'front':
                for y in range(fc.fh - 3, fc.fh):
                    for x in range(1, fc.fw - 1):
                        put(fc, x, y, shade(SILVER, 0.9))
                for x in range(fc.fw):
                    put(fc, x, fc.fh - 6, (70, 60, 56))
    # the scabbard: black lacquer, silver fittings, a cyan rune line; the sheathed hilt
    for fc in faces(p, 'scabbard'):
        hp.fill(fc, (24, 22, 30), noise=0.02, key='scab')
        if fc.face in ('right', 'left'):
            for y in range(fc.fh):
                put(fc, 0, y, SILVER)
                put(fc, fc.fw - 1, y, SILVER)
                put(fc, 1, y, SILVER)
                put(fc, fc.fw - 2, y, SILVER)
            for x in range(4, fc.fw - 4):
                if fc.fh >= 4:
                    put(fc, x, fc.fh // 2, CYAN if x % 5 else (200, 255, 255))
                    hp.glow(p, 'scabbard', fc.face, x, fc.fh // 2, CYAN)
    rune_sword_parts(p, 'sheathed', grip=0, guard=1, pommel=2)
    rune_sword_parts(p, 'sword_rune', grip=0, pommel=1, guard=2, blade=3)
    # the conquest seals: small tokens of five colours that shine
    seal_c = [(232, 80, 80), (80, 160, 236), (236, 196, 64), (96, 214, 112), (176, 112, 236)]
    for i, c in enumerate(seal_c):
        for fc in faces(p, 'seals', i):
            for y in range(fc.fh):
                for x in range(fc.fw):
                    put(fc, x, y, SILVER if y == 0 else shade(c, 1.1 - 0.2 * y / fc.fh))
            if fc.face in ('front', 'back', 'right', 'left'):
                hp.glow(p, 'seals', fc.face, 0, fc.fh // 2, c, index=i)
                hp.glow(p, 'seals', fc.face, 1, fc.fh // 2 + 1, c, index=i)
    # the phantom blades: light itself
    for i in range(6):
        part = 'phantom_%d' % (i + 1)
        for fc in faces(p, part):
            for y in range(fc.fh):
                for x in range(fc.fw):
                    core = (230, 255, 255) if x == 0 or fc.fw == 2 and y % 3 == 0 else (120, 236, 250)
                    put(fc, x, y, core)
            if fc.face in ('front', 'back', 'right', 'left'):
                put(fc, 0, 0, (0, 0, 0), 0)
                put(fc, fc.fw - 1, 0, (0, 0, 0), 0)
        hp.glow_part(p, part, (140, 240, 255))


def rune_sword_parts(p, part, grip=None, guard=None, pommel=None, blade=None):
    if grip is not None:
        for fc in faces(p, part, grip):
            for y in range(fc.fh):
                for x in range(fc.fw):
                    put(fc, x, y, (24, 24, 30) if (x + y) % 3 else SILVER)
    if guard is not None:
        for fc in faces(p, part, guard):
            hp.plate(fc, SILVER, key=part + 'g')
            if fc.face in ('front', 'back'):
                put(fc, fc.fw // 2, fc.fh // 2, CYAN)
                hp.glow(p, part, fc.face, fc.fw // 2, fc.fh // 2, CYAN, index=guard)
    if pommel is not None:
        for fc in faces(p, part, pommel):
            hp.fill(fc, (70, 220, 236), noise=0.0)
            put(fc, 0, 0, (220, 255, 255))
        hp.glow_part(p, part, CYAN, index=pommel)
    if blade is not None:
        for fc in faces(p, part, blade):
            n = fc.fw
            for y in range(fc.fh):
                for x in range(n):
                    if fc.face in ('right', 'left'):
                        c = (226, 232, 240) if y in (0, fc.fh - 1) else (84, 96, 116)
                    else:
                        c = (226, 232, 240)
                    put(fc, x, y, c)
            if fc.face in ('right', 'left'):
                for x in range(n):
                    xx = x if fc.face == 'right' else n - 1 - x
                    if 1 <= xx <= n - 4:
                        c = CYAN if (xx // 3) % 3 else (200, 150, 255)
                        for y in (1, 2):
                            if fc.fh > 3 or y == 1:
                                if (xx + y) % 2 == 0 or y == 1:
                                    put(fc, x, y, c)
                                    hp.glow(p, part, fc.face, x, y, c, index=blade)
                tip = n - 1 if fc.face == 'right' else 0
                put(fc, tip, 0, (0, 0, 0), 0)
                put(fc, tip, fc.fh - 1, (0, 0, 0), 0)


# ================================================================================================= Mariel

MAR_WHITE = (244, 241, 234)
MAR_BLUE = (122, 176, 224)
MAR_HAIR = (232, 192, 112)


def mariel_palette(palette):
    hair = '#e8c070'
    return palette(skin='#f8e0d0', hair=hair, bangs=hair, lock=hair, sleeve='#f2efe8', sleeve2='#f2efe8',
                   tunic='#f2efe8', bust='#f2efe8', cuff='#f2efe8', waist='#7ab0e0', pants='#f2efe8',
                   boots='#d8d0c0', core={'base': '#fff4d0', 'style': 'glowcore'})


def paint_mariel(p):
    skin = col('#f8e0d0')
    hp.HeroFace(skin, iris_r=(100, 162, 224), kind='female', eyes='soft', brow=(196, 146, 86), lips=(226, 136, 150),
                blush=(246, 160, 172), lash=(80, 52, 40)).paint(p)
    hp.hair_cap(p, MAR_HAIR, front_rows=3, frame_cols=3, frame_rows=14, side_rows=14, back_rows=14, ring=(4, 5),
                sheen=0.2)
    hp.bangs(p, MAR_HAIR, [11, 10, 9, 8, 7, 8, 7, 6, 7, 8, 7, 6, 7, 8, 7, 8, 10, 11], ring_row=4, sheen=0.2)
    for part in ('lock_r', 'lock_l', 'lock_r2', 'lock_l2'):
        hp.hair_strip(p, part, MAR_HAIR, ragged=part.endswith('2'), wave=6, ring=(5, 7) if not part.endswith('2')
                      else None, sheen=0.2)
    # the hood: white with a gold band around the face, open over the face
    for name in ('top', 'front', 'right', 'left', 'back', 'bottom'):
        fc = face(p, 'hood', name)
        if name == 'bottom':
            hp.clear_face(fc)
            continue
        hp.fill(fc, MAR_WHITE, top=1.04, bottom=0.92, noise=0.02, key='hood' + name)
        if name == 'front':
            for y in range(fc.fh):
                for x in range(fc.fw):
                    if y >= 3 and 2 <= x <= fc.fw - 3:
                        hp.clear(fc, x, y)
            for x in range(1, fc.fw - 1):
                put(fc, x, 2, GOLD)
            for y in range(2, fc.fh):
                put(fc, 1, y, GOLD)
                put(fc, fc.fw - 2, y, GOLD)
        if name in ('right', 'left'):
            front = fc.fw - 1 if name == 'right' else 0
            for y in range(fc.fh):
                put(fc, front, y, GOLD)
                for d in (1, 2, 3):
                    x = front - d if name == 'right' else front + d
                    hp.blend(fc, x, y, (200, 210, 230), 0.15 * (4 - d))
    hf = face(p, 'hood', 'top')
    for (x, y) in ((7, 12), (8, 12), (7, 11), (8, 11), (6, 12), (9, 12), (7, 13), (8, 13), (7, 10), (8, 10)):
        put(hf, x, y, GOLD)
    # the veil: white, gold border, pale blue inner line, scalloped hem
    for part in ('veil', 'veil2'):
        for fc in faces(p, part):
            hp.fill(fc, MAR_WHITE, top=1.02, bottom=0.9, noise=0.02, key=part + fc.face)
            if fc.face in ('back', 'front'):
                hp.folds(fc, every=5, f=0.9, phase=2)
                for y in range(fc.fh):
                    put(fc, 0, y, GOLD)
                    put(fc, fc.fw - 1, y, GOLD)
                    put(fc, 2, y, MAR_BLUE)
                    put(fc, fc.fw - 3, y, MAR_BLUE)
    vb = face(p, 'veil2', 'back')
    vf = face(p, 'veil2', 'front')
    for x in range(vb.fw):
        hp.put(vb, x, vb.fh - 2, GOLD)
        if x % 4 in (0, 3):
            hp.clear(vb, x, vb.fh - 1)
            hp.clear(vf, vf.fw - 1 - x, vf.fh - 1)
        else:
            put(vb, x, vb.fh - 1, MAR_WHITE)
    # the halo: a thin golden ring with twelve sparks
    import math
    hf = face(p, 'halo', 'front')
    hb = face(p, 'halo', 'back')
    n = hf.fw
    c0 = (n - 1) / 2.0
    for y in range(n):
        for x in range(n):
            d = ((x - c0) ** 2 + (y - c0) ** 2) ** 0.5
            ang = math.degrees(math.atan2(y - c0, x - c0)) % 30
            ring = c0 - 1.6 <= d <= c0 - 0.4
            spark = c0 - 3.2 <= d < c0 - 1.6 and (ang < 4 or ang > 26)
            if ring or spark:
                c = (255, 236, 160) if ring else (255, 250, 220)
                put(hf, x, y, c)
                put(hb, n - 1 - x, y, c)
                hp.glow(p, 'halo', 'front', x, y, (255, 230, 150))
                hp.glow(p, 'halo', 'back', n - 1 - x, y, (255, 230, 150))
            else:
                hp.clear(hf, x, y)
                hp.clear(hb, n - 1 - x, y)
    # the habit: white with gold embroidery, the sun and the tear on the breast
    t = torso_of(p)
    f = face(p, t, 'front')
    hp.fill(f, MAR_WHITE, top=1.04, bottom=0.92, noise=0.02, key='habit')
    for x in range(4, 12):
        put(f, x, 0, GOLD)
    for name in ('right', 'left', 'back'):
        fc = face(p, t, name)
        hp.fill(fc, MAR_WHITE, noise=0.02, key='habit' + name)
        hp.folds(fc, every=4, f=0.92)
    for y in range(9, f.fh):
        put(f, 7, y, GOLD)
        put(f, 8, y, GOLD)
    for fc in faces(p, 'bust'):
        hp.fill(fc, MAR_WHITE, top=1.08, bottom=0.94, noise=0.02, key='mbust')
        if fc.face == 'front':
            cx = fc.fw // 2
            for (dx, dy) in ((0, 1), (-1, 2), (0, 2), (1, 2), (0, 3), (-2, 2), (2, 2), (0, 0), (0, 4)):
                put(fc, cx + dx - 1, dy, GOLD if (dx, dy) != (0, 2) else (160, 220, 255))
            hp.glow(p, 'bust', 'front', cx - 1, 2, (170, 230, 255))
    sleeve(p, MAR_WHITE, folds_every=4)
    for side in ('cuff_r', 'cuff_l'):
        for fc in faces(p, side):
            if fc.face == 'bottom':
                for y in range(fc.fh):
                    for x in range(fc.fw):
                        put(fc, x, y, MAR_BLUE if 1 <= x < fc.fw - 1 and 1 <= y < fc.fh - 1 else GOLD)
                continue
            if fc.face == 'top':
                hp.clear_face(fc)
                continue
            hp.fill(fc, MAR_WHITE, top=0.96, bottom=1.04, noise=0.02, key=side)
            hp.folds(fc, every=3, f=0.9)
            hp.trim_row(fc, fc.fh - 1, GOLD)
            hp.trim_row(fc, fc.fh - 2, MAR_BLUE, shine=False)
    ru, rf, lu, lf = arms_of(p)
    hp.hands(p, skin, rf, lf, rows=3)
    # the sash and the robe
    hips = face(p, 'hips', 'front')
    for name in ('front', 'right', 'left', 'back'):
        fc = face(p, 'hips', name)
        hp.fill(fc, MAR_WHITE, noise=0.02, key='mhips' + name)
        for y in range(2, 6):
            for x in range(fc.fw):
                put(fc, x, y, shade(MAR_BLUE, 1.05 - 0.05 * (y - 2)))
        hp.trim_row(fc, 1, GOLD)
        hp.trim_row(fc, 6, GOLD)
    put(hips, 4, 7, GOLD)
    put(hips, 4, 8, GOLD)
    put(hips, 4, 9, (255, 236, 160))
    for part in ('robe', 'robe2'):
        for fc in faces(p, part):
            if fc.face == 'top':
                hp.clear_face(fc)
                continue
            hp.fill(fc, MAR_WHITE, top=1.03, bottom=0.9, noise=0.02, key=part + fc.face)
            if fc.face in ('front', 'back', 'right', 'left'):
                hp.folds(fc, every=4, f=0.9, phase=1)
            if fc.face == 'front':
                cx = fc.fw // 2
                for y in range(fc.fh):
                    put(fc, cx - 1, y, GOLD)
                    put(fc, cx, y, GOLD)
                    if y % 4 == 2:
                        put(fc, cx - 2, y, GOLD)
                        put(fc, cx + 1, y, GOLD)
            if fc.face == 'bottom':
                hp.fill(fc, (220, 214, 200), noise=0.0)
    for fc in faces(p, 'robe2', sides=True):
        for y in range(fc.fh - 4, fc.fh):
            for x in range(fc.fw):
                put(fc, x, y, MAR_BLUE if y < fc.fh - 2 else GOLD)
        for x in range(0, fc.fw, 4):
            put(fc, x, fc.fh - 5, GOLD)
            put(fc, x + 1, fc.fh - 6, GOLD) if x + 1 < fc.fw else None
    legs_fill(p, MAR_WHITE, (226, 218, 200), boot_rows=6, trim=GOLD)
    # the saint's staff: white gold wound with a blue ribbon, the sun ring, the teardrop crystal
    for fc in faces(p, 'staff_holy', 0):
        for y in range(fc.fh):
            for x in range(fc.fw):
                c = (240, 224, 160) if (y + x) % 9 not in (0, 1, 2) else MAR_BLUE
                put(fc, x, y, shade(c, 1.05 if x == 0 else 0.92))
    for i in (1, 2):
        for fc in faces(p, 'staff_holy', i):
            hp.fill(fc, GOLD, noise=0.0)
    sr = face(p, 'staff_holy', 'front', index=3)
    sb = face(p, 'staff_holy', 'back', index=3)
    w_, h_ = sr.fw, sr.fh
    cxs, cys = (w_ - 1) / 2.0, (h_ - 1) / 2.0 - 1.0
    import math
    for y in range(h_):
        for x in range(w_):
            d = ((x - cxs) ** 2 + (y - cys) ** 2) ** 0.5
            ring = 3.6 <= d <= 5.0
            ang = math.degrees(math.atan2(y - cys, x - cxs)) % 45
            ray = 5.0 < d <= 6.6 and (ang < 9 or ang > 36)
            stem = y >= h_ - 3 and abs(x - cxs) <= 1.0
            if ring or ray or stem:
                c = GOLD_LIGHT if ray else GOLD
                put(sr, x, y, c)
                put(sb, w_ - 1 - x, y, c)
                if ray:
                    hp.glow(p, 'staff_holy', 'front', x, y, (255, 236, 170), index=3)
                    hp.glow(p, 'staff_holy', 'back', w_ - 1 - x, y, (255, 236, 170), index=3)
            else:
                hp.clear(sr, x, y)
                hp.clear(sb, w_ - 1 - x, y)
    for fc in faces(p, 'staff_tear'):
        for y in range(fc.fh):
            for x in range(fc.fw):
                inside = not (y == 0 and x in (0, fc.fw - 1))
                if inside:
                    put(fc, x, y, mix((170, 236, 255), (255, 255, 255), 0.5 if (x, y) == (1, 1) else 0.0))
                else:
                    hp.clear(fc, x, y)
    hp.glow_part(p, 'staff_tear', (180, 240, 255))
    for fc in faces(p, 'staff_ribbon'):
        hp.fill(fc, MAR_BLUE, noise=0.0)
        for x in range(fc.fw):
            hp.clear(fc, x, fc.fh - 1) if x == 1 else None
    # the prayer book on its chain
    for fc in faces(p, 'holybook', 0):
        hp.fill(fc, (238, 230, 210), noise=0.02, key='hbook')
        if fc.face in ('right', 'left'):
            for (x, y) in ((0, 0), (fc.fw - 1, 0), (0, fc.fh - 1), (fc.fw - 1, fc.fh - 1)):
                put(fc, x, y, GOLD)
            cx, cy = fc.fw // 2, fc.fh // 2
            for (dx, dy) in ((0, -1), (0, 0), (0, 1), (-1, 0), (1, 0)):
                put(fc, cx + dx, cy + dy, GOLD)
    for fc in faces(p, 'holybook', 1):
        hp.fill(fc, GOLD, noise=0.0)
