"""The travelling adventurers (docs/modules/travellers.md): four original travellers who turn up now and then in a
single-player world, hunting in the fields, shopping in the capital and resting at the inn.

  traveller_bold   Bold, the map-maker: a dwarf explorer, brass goggles, copper beard, olive coat, a pack of rolled maps
                  and a brass compass staff.
  traveller_mirka  Mirka, the falconer: small and quick, forest-green feathered cloak, a hawk on her shoulder, quiver and
                  longbow.
  traveller_graus  Graus, the bell knight: verdigris bronze plate, a cream tabard with a bell, a halberd and a great bronze
                  bell slung on his back.
  traveller_sele   Sele, the tide-singer: a coastal bard in sea-foam and coral, long wavy dark-rose hair with a shell pin,
                  and a pearl-white lute.
"""
from erpaint import lighten, mix, shade
from npc_cast import GOLD, SLIM, look, mail, palette, pants_and_boots, pendant, plate, sleeves_cuff
from npc_cast_town import helmet
from npc_paint import (Face, arm_parts, band_rows, belt, boots, buttons, col, faces_of, fill_face, glow_fill, hair,
                       hair_piece, hands, legs, mask_alpha, rows_color, sash, torso_part, trim_row, vneck)


def bold():
    """Bold, the map-maker dwarf: goggles pushed up, copper beard, olive explorer's coat, rolled maps, compass staff."""
    skin = '#d9a27c'
    beard_c = '#c46a2c'
    coat = '#5c6c3a'
    face = Face(skin, eye='#3c6a44', style='normal', brows='#a4521c', brow_style='thick', nose='big', mouth='none',
                cheek=(226, 140, 120))

    def paint(p):
        face.paint(p)
        hair(p, beard_c, bangs=[1, 2, 1, 1, 2, 1, 2, 1], back_len=5, side_front=3, highlight=(236, 150, 80))
        hair_piece(p, 'beard', beard_c)
        bf = p.face('beard', 'front')
        for x in (3, 4):
            bf.clear(x, 0)                     # the nose shows above the moustache
        bf.hline(3, 4, 1, (70, 34, 20))        # mouth
        for y in range(2, bf.h):
            bf.px(1, y, shade(bf.get(1, y), 0.85))
            bf.px(bf.w - 2, y, shade(bf.get(bf.w - 2, y), 0.85))
        for x in range(0, bf.w, 3):
            bf.clear(x, bf.h - 1)              # a forked, ragged end
        # brass goggles pushed up on the forehead, blue-green lenses
        for i in range(2):
            gf = p.face('goggles', 'front', index=i)
            for y in range(gf.h):
                for x in range(gf.w):
                    gf.px(x, y, (196, 156, 72))
            gf.px(1, 0, (150, 210, 214))
            gf.px(1, 1, (110, 170, 180))
        # explorer's coat: olive, brass buttons, a leather strap across, map-case buckle
        t = torso_part(p)
        f = p.face(t, 'front')
        vneck(f, '#d8c8a0', depth=2)
        buttons(f, f.w // 2 - 2, (3, 5), (220, 180, 90))
        sash(f, '#6a4424', slope=1, width=1, offset=1)
        sleeves_cuff(p, '#8a6a3a', row=3)
        for side in ('right', 'left'):
            upper, fore = arm_parts(p, side)
            rows_color(p, upper, 0, 0, shade(col(coat), 1.12))
        hands(p, '#6a4a2a', rows=2, gloves=True)
        for fc in faces_of(p, 'coat_tails'):
            if fc.face in ('back', 'front'):
                fc.hline(0, fc.w - 1, fc.h - 1, shade(col(coat), 0.7))
                fc.vline(fc.w // 2, 2, fc.h - 1, shade(col(coat), 0.75))
        # the pack: rolled maps (cream, red ribbons) on the bedroll strap
        for fc in faces_of(p, 'backpack', index=1):
            for y in range(fc.h):
                for x in range(fc.w):
                    fc.px(x, y, (232, 220, 186) if (x + y) % 3 else (214, 198, 160))
            if fc.face in ('back', 'front'):
                fc.vline(2, 0, fc.h - 1, (176, 52, 44))
                fc.vline(fc.w - 3, 0, fc.h - 1, (176, 52, 44))
        pb = p.face('backpack', 'back')
        pb.rect(2, 2, pb.w - 3, 2, (90, 62, 36))
        pb.px(pb.w // 2, 3, (220, 180, 90))
        belt(p, 0, '#3a2414', '#d0a050')
        pants_and_boots(p, '#5a4632', '#3a2818', rows=3, trim='#7a5a38')
        # the compass jewel glows
        glow_fill(p, 'staff_bud', (130, 220, 230))
    pal = palette(skin=skin, hair=beard_c, beard=beard_c, tunic=coat, sleeve=coat, sleeve2=coat, belly='#4a5630',
                  coat={'base': coat, 'style': 'cloth'}, goggle='#c49c48',
                  pack={'base': '#7a5634', 'style': 'leather'}, bedroll='#e0d2ae',
                  satchel={'base': '#6a4a2a', 'style': 'leather'},
                  staff={'base': '#b88c3c', 'style': 'metal'}, leafy='#d4a84c',
                  bud={'base': '#78d0dc', 'style': 'gem'})
    return look('traveller_bold', show={'hair_cap', 'beard', 'goggles', 'coat_tails', 'backpack', 'satchel',
                                       'staff_nature'}, broad=True,
                poses={'right_arm': dict(rot=(-14, 0, 6)), 'right_forearm': dict(rot=(-26, 0, 0)),
                       'left_arm': dict(rot=(-4, 0, -6))},
                pal=pal, paint=paint, glow=True, work='work_staff', talk='talk_hearty', scale=0.84,
                portrait=dict(bg=('#d0903c', '#3a2210'), hide={'staff_nature'}))


def mirka():
    """Mirka, the falconer: auburn curls under a green headband, a feathered forest-green cloak, a hawk on her
    shoulder, leather jerkin, quiver and longbow."""
    skin = '#efc6a2'
    hair_c = '#9c4a22'
    cloak = '#2f5c32'
    face = Face(skin, eye='#4a8c3a', style='tall', brows='#7a3a1a', mouth='grin', freckles=True, blush=True)

    def paint(p):
        face.paint(p)
        hair(p, hair_c, bangs=[2, 3, 2, 1, 2, 3, 2, 2], back_len=5, side_front=5, highlight=(214, 120, 70))
        band_rows(p, (1, 2), '#4a8a3a')
        for fc in faces_of(p, 'band', sides_only=True):
            if fc.face == 'left':
                fc.px(2, 1, (236, 226, 200))   # a white feather tucked in
                fc.px(3, 1, (180, 120, 60))
        # leather jerkin over a green shirt, laced
        t = torso_part(p)
        f = p.face(t, 'front')
        for y in range(f.h):
            for x in range(1, f.w - 1):
                f.px(x, y, shade(col('#8a6234'), 1.04 - 0.04 * y))
        for y in range(1, f.h, 2):
            f.px(f.w // 2 - 1, y, (226, 206, 160))
        f.hline(0, f.w - 1, 0, col('#4a8a3a'))
        for side in ('right', 'left'):
            upper, fore = arm_parts(p, side)
            rows_color(p, upper, 0, 3, '#4a8a3a')
        hands(p, '#7a5230', rows=2, gloves=True)
        # the cloak: forest green, a hem of brown and cream feathers with a ragged edge
        for fc in faces_of(p, 'cape', sides_only=True):
            for y in range(fc.h - 3, fc.h):
                for x in range(fc.w):
                    feather = ((x // 2) + y) % 2 == 0
                    fc.px(x, y, (232, 220, 190) if feather else (150, 96, 48))
                    if y == fc.h - 1 and x % 2 == 1:
                        fc.clear(x, y)
            if fc.face == 'back':
                for (x, y) in ((3, 4), (8, 6), (5, 9), (2, 12), (9, 13)):
                    if x < fc.w and y < fc.h - 3:
                        fc.px(x, y, lighten(col(cloak), 0.18))
        # the hawk: golden-brown back, pale speckled head, dark wing tips, yellow beak
        for i in range(len(p.rig.parts['pigeon'].cubes)):
            for fc in faces_of(p, 'pigeon', index=i):
                for y in range(fc.h):
                    for x in range(fc.w):
                        if (x + 2 * y) % 4 == 0:
                            fc.px(x, y, shade(fc.get(x, y), 0.8))
        for wing in ('pigeon_wing_r', 'pigeon_wing_l'):
            for fc in faces_of(p, wing):
                for x in range(fc.w):
                    fc.px(x, fc.h - 1, (60, 40, 26))
        for name in ('right', 'left'):
            hf = p.face('pigeon_head', name)
            hf.px(0, 0, (30, 24, 20))          # a dark eye on each side, a pale face
        # quiver feathers
        for fc in faces_of(p, 'quiver', index=1):
            for x in range(fc.w):
                fc.px(x, 0, (236, 228, 210) if x % 2 == 0 else (160, 100, 50))
        belt(p, 0, '#4a2c16', '#c0a050')
        pants_and_boots(p, '#9a7a52', '#4a3018', rows=4, trim='#6a4a28')
    pal = palette(skin=skin, hair=hair_c, tunic='#4a8a3a', sleeve='#8a6234', sleeve2='#8a6234', belly='#6a4c2a',
                  cape={'base': cloak, 'style': 'cloth'}, band='#4a8a3a',
                  pigeon='#9a6a38', pigeon_tail='#5c3a20', pigeon_head='#d8c09a', beak='#e8b838',
                  pigeon_wing='#7a4e2a', quiver={'base': '#6a4626', 'style': 'leather'}, fletch='#ece4d2',
                  longbow={'base': '#7a5030', 'style': 'wood'}, bracer='#6a4626')
    return look('traveller_mirka', builds=(SLIM,),
                show={'hair_cap', 'band', 'cape', 'pigeon', 'quiver', 'longbow', 'bracer_r', 'bracer_l'},
                poses={'left_arm': dict(rot=(-6, 0, -4))},
                pal=pal, paint=paint, work='work_pigeon', talk='talk_point', scale=0.8,
                portrait=dict(bg=('#62a24e', '#122a10')))


def graus():
    """Graus, the bell knight: an open bronze helm with a cream crest, verdigris plate, a cream tabard bearing a bell,
    a halberd, and a great bronze bell on his back."""
    skin = '#caa080'
    bronze = '#6e8c70'
    face = Face(skin, eye='#4a5a6c', style='stern', brows='#8a8478', brow_style='thick', mouth='line',
                stubble=(150, 140, 130))

    def paint(p):
        face.paint(p)
        helmet(p, bronze, left=1, right=1, top=2, rim=(150, 110, 60))
        hc = p.face('helm_crest', 'front')
        for y in range(hc.h):
            for x in range(hc.w):
                hc.px(x, y, (236, 226, 200) if y % 2 == 0 else (214, 202, 172))
        t = torso_part(p)
        plate(p, t, bronze)
        for part in ('pauldron_r', 'pauldron_l'):
            plate(p, part, bronze)
            for fc in faces_of(p, part, sides_only=True):
                fc.hline(0, fc.w - 1, fc.h - 1, (176, 132, 64))    # brass rims
        # cream tabard with a bronze bell on the chest
        for part in ('apron_bib', 'apron'):
            for fc in faces_of(p, part):
                fill_face(fc, '#e6dcc2')
                if fc.face in ('front', 'back'):
                    fc.vline(0, 0, fc.h - 1, (176, 132, 64))
                    fc.vline(fc.w - 1, 0, fc.h - 1, (176, 132, 64))
        bib = p.face('apron_bib', 'front')
        icon = ('..X..', '.XXX.', '.XXX.', 'XXXXX', '..X..')   # the bell
        x0 = bib.w // 2 - 2
        for dy, row in enumerate(icon):
            for dx, mark in enumerate(row):
                if mark == 'X' and 0 <= x0 + dx < bib.w and dy + 1 < bib.h:
                    bib.px(x0 + dx, dy + 1, (176, 132, 64) if dy < 4 else (110, 80, 40))
        af = p.face('apron', 'front')
        af.hline(0, af.w - 1, af.h - 1, (176, 132, 64))
        for side in ('right', 'left'):
            upper, fore = arm_parts(p, side)
            mail(p, upper, '#7a8478', rows=(0, 1, 2))
            plate(p, fore, bronze)
        hands(p, '#5a4a3a', rows=2, gloves=True)
        # the great bell on his back: a bronze bell shape with a darker mouth and a clapper
        bell = p.face('shield_back', 'back')
        cxb = bell.w // 2
        # rows of the bell seen from behind: the hanging loop, the crown, the waist, the flared lip, the clapper
        shape = ('...XXX...', '...X.X...', '..XXXXX..', '..XXXXX..', '..XXXXX..', '.XXXXXXX.', '.XXXXXXX.',
                 'XXXXXXXXX', '....X....')

        def inside(x, y):
            return y < len(shape) and x < len(shape[y]) and shape[y][x] == 'X'
        mask_alpha(bell, inside)
        for y in range(bell.h):
            for x in range(bell.w):
                if inside(x, y):
                    k = 1.28 - 0.05 * y + (0.2 if x == cxb - 1 else 0.0) - (0.12 if x == cxb + 2 else 0.0)
                    bell.px(x, y, shade(col('#b07a3a'), k))
        bell.hline(0, bell.w - 1, 7, (120, 82, 40))      # the lip band
        bell.hline(2, bell.w - 3, 4, (150, 108, 52))     # a cast ring
        bell.px(cxb, 8, (64, 46, 28))
        for name in ('front', 'right', 'left', 'top', 'bottom'):
            fc = p.face('shield_back', name)
            mask_alpha(fc, lambda x, y: name in ('right', 'left') and y >= 6)
            for y in range(fc.h):
                for x in range(fc.w):
                    if name in ('right', 'left') and y >= 6:
                        fc.px(x, y, (140, 96, 50))
        belt(p, 0, '#3a2c20', '#b08440')
        for part in ('right_leg', 'left_leg'):
            mail(p, part, '#7a8478')
        for part in ('right_shin', 'left_shin'):
            plate(p, part, bronze)
        boots(p, '#3a3028', rows=2)
    pal = palette(skin=skin, tunic=bronze, sleeve=bronze, sleeve2=bronze, belly='#5a7458',
                  shell={'base': bronze, 'style': 'metal'}, armor={'base': bronze, 'style': 'metal'},
                  plume='#e6dcc2', apron={'base': '#e6dcc2', 'style': 'cloth'},
                  shield={'base': '#b07a3a', 'style': 'metal'},
                  pole='#4a3220', blade={'base': '#c8ccd0', 'style': 'metal'})
    return look('traveller_graus',
                show={'shell', 'helm_crest', 'pauldron_r', 'pauldron_l', 'apron', 'apron_bib', 'shield_back',
                      'halberd'}, broad=True,
                poses={'right_arm': dict(rot=(-12, 0, 8)), 'right_forearm': dict(rot=(-22, 0, 0))},
                pal=pal, paint=paint, work='work_guard', talk='talk_salute', scale=1.06,
                portrait=dict(bg=('#4e8a76', '#0e2420'), hide={'halberd'}, dy=-0.5))


def sele():
    """Sele, the tide-singer: long wavy dark-rose hair with a shell pin, a sea-foam bodice with coral waves, a gown
    fading to deep teal with a foam hem, a coral scarf and a pearl-white lute."""
    skin = '#f2d2ba'
    hair_c = '#8c3c4c'
    foam = '#7cc8b8'
    face = Face(skin, eye='#2a8c8c', style='tall', brows='#6a2a36', mouth='smile', blush=True, lash=(70, 30, 40))

    def paint(p):
        face.paint(p)
        hair(p, hair_c, style='long', bangs=[2, 3, 3, 2, 2, 3, 3, 2], long_len=8, side_front=7,
             highlight=(196, 110, 126))
        hair_piece(p, 'hair_back', hair_c, ragged=True)
        hb = p.face('hair_back', 'back')
        for y in range(hb.h):
            shift = (y // 2) % 2
            for x in range(shift, hb.w, 3):
                hb.px(x, y, lighten(col(hair_c), 0.22))     # waves
        # a shell pin above the right ear: cream and coral
        side = p.face('hair_cap', 'right')
        side.px(5, 2, (246, 232, 214))
        side.px(6, 2, (240, 150, 130))
        side.px(5, 3, (240, 150, 130))
        # sea-foam bodice with a coral wave across, a pearl pendant
        t = torso_part(p)
        f = p.face(t, 'front')
        for y in range(f.h):
            for x in range(f.w):
                f.px(x, y, shade(col(foam), 1.05 - 0.04 * y))
        for x in range(f.w):
            f.px(x, 4 + (x // 2) % 2, (232, 132, 106))
        pendant(p, (246, 242, 236), glow=(220, 240, 250), y=2)
        for side_name in ('right', 'left'):
            upper, fore = arm_parts(p, side_name)
            rows_color(p, fore, 3, 3, '#e8846a')
        hands(p, skin)
        # the gown: foam to deep teal, a white foam hem in waves
        for i in range(2):
            for fc in faces_of(p, 'skirt_long', index=i, sides_only=True):
                for y in range(fc.h):
                    k = (y + 7 * i) / 14.0
                    base = mix(col(foam), col('#1e6a74'), min(1.0, k))
                    for x in range(fc.w):
                        fc.px(x, y, shade(base, 1.0 - 0.04 * ((x + y) % 2)))
        for fc in faces_of(p, 'skirt_long', index=1, sides_only=True):
            for x in range(fc.w):
                fc.px(x, fc.h - 1, (240, 246, 244))
                if x % 3 == 1:
                    fc.px(x, fc.h - 2, (226, 240, 238))
        # the pearl lute with a teal rosette
        lf = p.face('lute', 'front', index=0)
        lf.px(2, 2, (40, 120, 124))
        lf.px(2, 1, (90, 170, 170))
        belt(p, 0, '#a4545a', '#e8d8b0')
        boots(p, '#6a3a3e', rows=2)
    pal = palette(skin=skin, hair=hair_c, tunic=foam, sleeve=foam, sleeve2=foam, belly='#5aa89a',
                  skirt={'base': foam, 'style': 'cloth'}, skirt2={'base': '#2e8a8a', 'style': 'cloth'},
                  scarf='#e8846a', lute='#ece4d6', lute_neck='#7a5a4a', lute_head='#7a5a4a')
    return look('traveller_sele', builds=(SLIM,), show={'hair_cap', 'hair_back', 'skirt_long', 'scarf', 'lute'},
                poses={'right_arm': dict(rot=(-30, 20, 10)), 'right_forearm': dict(rot=(-30, 0, 0)),
                       'left_arm': dict(rot=(-40, -20, -20)), 'left_forearm': dict(rot=(-60, 0, 0))},
                pal=pal, paint=paint, glow=True, work='work_lute', talk='talk_flourish', scale=0.96,
                portrait=dict(bg=('#3c9cac', '#0a2430')))


def cast():
    return [bold(), mirka(), graus(), sele()]
