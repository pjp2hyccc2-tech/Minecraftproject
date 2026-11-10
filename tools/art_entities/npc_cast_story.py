"""The story characters of the capital (lilia, gustav, nox, laszlo, wolfram, adel) and the quest givers of the other
areas (neris, khalid, helga, ilsa, selene, gunter). See docs/STORY.md sections 2.1 and 2.2."""
from erpaint import lighten, mix, shade
from npc_cast import (BROAD, GOLD, NO_LEGS, ROYAL, ROYAL_DARK, SLIM, WHITE, apron_stains, bandage_wrap, crest, earring,
                      eyepatch, look, mail, palette, pants_and_boots, pendant, plate, round_glasses, sleeves_cuff,
                      stripes, tabard)
from npc_cast_town import cap, helmet, mustache_plane
from npc_paint import (CROSS, DIAMOND, STAR, Face, arm_parts, bald_head, bare_arms, belt, boots, buttons, clear_part, col,
                       emblem, faces_of, fill_face, glow_fill, hair, hair_piece, hands, legs, mask_alpha, open_front,
                       page_lines, rows_color, sash, shell_face_opening, strand_texture, torso_part, trim_col, trim_row,
                       vneck)


def lilia():
    """The princess in the Hall of the Star Gate: priestess gown in the royal colours, tiara, star scepter."""
    skin = '#f6d8c4'
    hair_c = '#ecc66a'
    face = Face(skin, eye='#3a6ad0', style='tall', brows='#c49448', mouth='smile', blush=True)

    def paint(p):
        face.paint(p)
        hair(p, hair_c, style='long', bangs=[2, 3, 2, 2, 3, 2, 3, 2], long_len=8, side_front=8,
             highlight=(255, 240, 180))
        hair_piece(p, 'hair_back', hair_c, ragged=True)
        hb = p.face('hair_back', 'back')
        for y in range(0, hb.h, 3):
            hb.px(4, y, lighten(col(hair_c), 0.3))
        # tiara with a blue star stone
        for name in ('front', 'back'):
            tf = p.face('tiara', name)
            mask_alpha(tf, lambda x, y: y == 2 or (y == 1 and x in (1, 2, 3, 4)) or (y == 0 and x in (2, 3)))
        tf = p.face('tiara', 'front')
        tf.px(2, 1, (90, 150, 250))
        tf.px(3, 1, (130, 180, 255))
        g = p.face('tiara', 'front', glow=True)
        g.px(2, 1, (90, 150, 250))
        g.px(3, 1, (130, 180, 255))
        # bodice: royal blue with gold lacing over white, star pendant that glows
        t = torso_part(p)
        f = p.face(t, 'front')
        for y in range(f.h):
            for x in range(1, f.w - 1):
                if 1 <= x <= f.w - 2 and y >= 1:
                    f.px(x, y, shade(col(ROYAL), 1.04 - 0.05 * y))
        for y in range(2, f.h, 2):
            f.px(f.w // 2 - 1, y, (226, 194, 96))
            f.px(f.w // 2, y, (226, 194, 96))
        f.hline(0, f.w - 1, 0, (244, 242, 240))
        pendant(p, (120, 180, 255), glow=(120, 180, 255), y=2)
        for side in ('right', 'left'):
            upper, fore = arm_parts(p, side)
            rows_color(p, fore, 3, 3, GOLD)
            rows_color(p, upper, 0, 1, '#f4f2f0')
        hands(p, skin)
        belt(p, 0, '#c8a040', None)
        # gown: white with a royal blue front panel and gold hems
        for i in range(2):
            sf = p.face('skirt_long', 'front', index=i)
            cx = sf.w // 2
            for y in range(sf.h):
                for x in range(cx - 1 - i, cx + 1 + i):
                    sf.px(x, y, shade(col(ROYAL), 1.02 - 0.03 * y))
                sf.px(cx - 2 - i, y, col(GOLD))
                sf.px(cx + 1 + i, y, col(GOLD))
        trim_row(p, 'skirt_long', 7, GOLD, index=1)
        trim_row(p, 'skirt_long', 6, shade(col(ROYAL), 0.9), index=1)
        # royal cape: blue, gold edge, the crest
        cf = p.face('cape', 'back')
        crest(cf, cf.w // 2, 5, GOLD)
        for fc in faces_of(p, 'cape', sides_only=True):
            fc.hline(0, fc.w - 1, fc.h - 1, col(GOLD))
        for y in range(cf.h):
            cf.px(0, y, col(GOLD))
            cf.px(cf.w - 1, y, col(GOLD))
        sg = p.face('scepter', 'front', index=1, glow=True)
        for y in range(sg.h):
            for x in range(sg.w):
                sg.px(x, y, (110, 170, 255))
    pal = palette(skin=skin, hair=hair_c, tunic='#f4f2f0', sleeve='#f4f2f0', sleeve2='#f4f2f0', belly='#f4f2f0',
                  skirt={'base': '#f2f0f4', 'style': 'cloth'}, skirt2={'base': '#eeecf2', 'style': 'cloth'},
                  cape={'base': ROYAL, 'style': 'cloth'}, tiara='#ecc850', scepter='#e8c850',
                  scepter_gem={'base': '#7ab4ff', 'style': 'gem'})
    return look('lilia', builds=(SLIM, NO_LEGS),
                show={'hair_cap', 'hair_back', 'tiara', 'skirt_long', 'cape', 'scepter'},
                poses={'right_arm': dict(rot=(-12, 0, 6)), 'right_forearm': dict(rot=(-74, 0, 0)),
                       'left_arm': dict(rot=(-16, 0, 12)), 'left_forearm': dict(rot=(-50, 14, 0)),
                       'cape': dict(scale=(1.0, 0.95, 1.0))},
                pal=pal, paint=paint, glow=True, work='work_pray', talk='talk_bow', scale=0.95,
                portrait=dict(bg=('#4a78d8', '#14204a'), hide={'scepter'}, dy=-0.5))


def nox():
    skin = '#f0dcd0'
    hair_c = '#d8dce8'
    face = Face(skin, eye='#7a5ad0', style='sleepy', brows='#8a8ea8', mouth='smirk', mouth_w=2)

    def paint(p):
        face.paint(p)
        hair(p, hair_c, bangs=[3, 4, 3, 2, 2, 3, 2, 1], back_len=7, side_front=5, highlight=(250, 250, 255))
        for name in ('front', 'right', 'left', 'back', 'top'):
            fc = p.face('hair_cap', name)
            for y in range(fc.h):
                for x in range(fc.w):
                    if fc.img[fc.v + y, fc.u + x, 3] > 0 and (x * 3 + y) % 5 == 0:
                        fc.px(x, y, (168, 172, 196))
        hair_piece(p, 'ponytail', hair_c)
        hair_piece(p, 'ponytail_tail', hair_c, ragged=True)
        for fc in faces_of(p, 'ponytail'):
            if fc.face not in ('top', 'bottom'):
                fc.hline(0, fc.w - 1, 1, (60, 60, 110))
        # monocle on his left eye (viewer's right) with a chain
        clear_part(p, 'glasses')
        gf = p.face('glasses', 'front')
        gold = (220, 180, 80)
        for (x, y) in ((5, 1), (6, 1), (4, 2), (7, 2), (5, 3), (6, 3)):
            gf.px(x, y, gold)
        side = p.face('head', 'left')
        for y in range(4, 8):
            side.px(min(7, y - 3), y, gold)
        # high collar, robe with a constellation, glowing clockwork pendant
        cf = p.face('collar', 'front')
        trim_row(p, 'collar', 0, (210, 170, 80))
        t = torso_part(p)
        f = p.face(t, 'front')
        f.px(3, 3, (120, 230, 255))
        f.px(4, 3, (120, 230, 255))
        f.px(3, 4, (80, 200, 240))
        f.px(4, 4, (80, 200, 240))
        f.px(3, 2, (200, 170, 80))
        g = p.face(t, 'front', glow=True)
        for (x, y) in ((3, 3), (4, 3), (3, 4), (4, 4)):
            g.px(x, y, (120, 230, 255))
        trim_col(f, 1, (200, 170, 80), 0, f.h - 1)
        trim_col(f, 6, (200, 170, 80), 0, f.h - 1)
        stars = [(1, 2), (3, 5), (6, 3), (2, 8), (5, 10), (7, 12), (1, 12), (4, 13), (6, 7), (8, 9), (3, 1)]
        for fc in faces_of(p, 'robe_long', sides_only=True):
            gl = p.face('robe_long', fc.face, glow=True)
            for (x, y) in stars:
                if x < fc.w and y < fc.h:
                    fc.px(x, y, (230, 240, 255))
                    gl.px(x, y, (170, 200, 255))
            fc.hline(0, fc.w - 1, fc.h - 1, (200, 170, 80))
        for side in ('right', 'left'):
            upper, fore = arm_parts(p, side)
            rows_color(p, fore, 3, 3, '#c8a050')
        hands(p, skin, rows=2)
        # the floating book: star chart pages
        bt = p.face('book_float', 'top')
        page_lines(bt, ink=(60, 60, 120), paper=(236, 230, 214))
        bt.vline(bt.w // 2, 0, bt.h - 1, (120, 90, 60))
        gb = p.face('book_float', 'top', glow=True)
        for (x, y) in ((1, 1), (2, 3), (5, 2), (5, 4)):
            bt.px(x, y, (90, 140, 230))
            gb.px(x, y, (120, 170, 255))
        for fname in ('top', 'bottom'):
            page_lines(p.face('book_page', fname), ink=(60, 60, 120), paper=(240, 234, 220))
    pal = palette(skin=skin, hair=hair_c, tunic='#1e2448', sleeve='#1e2448', sleeve2='#1e2448', belly='#1e2448',
                  collar={'base': '#1e2448', 'style': 'cloth'}, robe={'base': '#1e2448', 'style': 'cloth'},
                  book='#3a2a5a', candy={'base': '#f070b0', 'style': 'gem'})
    return look('nox', builds=(SLIM, NO_LEGS),
                show={'hair_cap', 'ponytail', 'glasses', 'collar', 'robe_long', 'book_float'},
                poses={'ponytail': dict(rot=(30, 0, 0), pos=(0, -1.5, 0)), 'left_arm': dict(rot=(-6, 0, -4))},
                pal=pal, paint=paint, glow=True, work='work_read', talk='talk_scholar', scale=1.0,
                portrait=dict(bg=('#3a3a8a', '#0c0c26')))




def gustav():
    """The guild master: a big hearty ex-adventurer with an eyepatch, ladle and a bowl of his infamous stew."""
    skin = '#d8a07a'
    hair_c = '#6a5040'
    face = Face(skin, eye='#3a3020', style='normal', brows='#4a3a2c', brow_style='thick', nose='big', mouth='none',
                scar=[(5, 2), (6, 3), (6, 5)])

    def paint(p):
        face.paint(p)
        hair(p, hair_c, bangs=[1, 2, 1, 1, 1, 2, 1, 1], back_len=5, side_front=4, highlight=(150, 130, 110))
        eyepatch(p, side='left')
        for i in range(len(p.rig.parts['beard_big'].cubes)):
            hair_piece(p, 'beard_big', '#6e5444', index=i)
        bf = p.face('beard_big', 'front')
        for x in (3, 4, 5):
            bf.clear(x, 0)
        bf.hline(3, 5, 2, (60, 30, 26))
        bf.hline(3, 5, 3, (150, 70, 60))
        for y in range(1, bf.h):
            bf.px(1, y, mix(bf.get(1, y), (170, 160, 150), 0.45))
            bf.px(7, y, mix(bf.get(7, y), (170, 160, 150), 0.45))
        # brown leather vest over a cream shirt with rolled sleeves, guild badge, cook's apron
        t = torso_part(p)
        f = p.face(t, 'front')
        open_front(f, '#efe4cc', width=4, rows=f.h - 1)
        f.px(1, 2, (230, 190, 80))
        f.px(1, 3, (190, 150, 60))
        for side in ('right', 'left'):
            upper, fore = arm_parts(p, side)
            rows_color(p, upper, 0, 5, '#efe4cc')
            rows_color(p, fore, 0, 0, '#e2d4b8')
            rows_color(p, fore, 1, 5, skin)
        hands(p, skin)
        bare = p.face(arm_parts(p, 'right')[1], 'front')
        bare.px(2, 2, shade(col(skin), 0.8))
        apron_stains(p, 'apron', (120, 90, 120), ((2, 2), (5, 5), (3, 7), (6, 8)))
        belt(p, 0, '#3a2414', '#c8a050', buckle_w=3)
        pants_and_boots(p, '#4a3a30', '#3a2818', rows=3)
        # the stew: grey-violet with something green floating in it
        bt = p.face('bowl', 'top')
        bt.rect(1, 1, bt.w - 2, bt.h - 2, (130, 110, 140))
        bt.px(2, 2, (110, 160, 80))
        bt.px(3, 3, (160, 140, 90))
    pal = palette(skin=skin, hair=hair_c, beard='#6e5444', tunic='#7a5034', sleeve='#efe4cc', sleeve2='#efe4cc',
                  belly='#7a5034', apron={'base': '#e8e0cc', 'style': 'cloth'}, ladle='#6a4a2a',
                  tool={'base': '#9aa0a8', 'style': 'metal'}, bowl='#7a5030')
    return look('gustav', show={'hair_cap', 'beard_big', 'belly', 'apron', 'ladle', 'bowl'}, broad=True,
                poses={'right_arm': dict(rot=(-26, 0, 6)), 'right_forearm': dict(rot=(-42, 0, 0)),
                       'left_arm': dict(rot=(-20, 0, -6)), 'left_forearm': dict(rot=(-52, 0, 0))},
                pal=pal, paint=paint, work='work_ladle', talk='talk_hearty', scale=1.08,
                portrait=dict(bg=('#a8743a', '#3a2410')))


def laszlo():
    """The former bandit king, washing dishes in the guild kitchen to pay off his debt (shackle included)."""
    skin = '#d8a884'
    hair_c = '#1e1c22'
    face = Face(skin, eye='#6a4a20', style='narrow', brows='#1e1c22', brow_style='worried', mouth='smirk',
                stubble='#3a3036', scar=[(1, 3), (1, 4)])

    def paint(p):
        face.paint(p)
        hair(p, hair_c, bangs=[2, 3, 1, 3, 2, 1, 3, 2], back_len=6, side_front=4, highlight=(70, 70, 90))
        for name in ('right', 'left', 'back'):
            fc = p.face('hair_cap', name)
            for x in range(1, fc.w, 3):
                fc.clear(x, 0)
        # red bandana with tails
        for fc in faces_of(p, 'band'):
            if fc.face in ('top', 'bottom'):
                if fc.face == 'top':
                    fill_face(fc, (176, 40, 40), shade_rows=False)
                    for (x, y) in ((1, 1), (4, 2), (6, 5), (2, 6)):
                        fc.px(x, y, (236, 220, 200))
                else:
                    mask_alpha(fc, lambda x, y: False)
                continue
            for y in range(fc.h):
                for x in range(fc.w):
                    if y < 2:
                        fc.px(x, y, (186, 44, 44) if (x + y) % 4 else (236, 220, 200))
                    else:
                        fc.clear(x, y)
        for fc in faces_of(p, 'headband_tails'):
            fill_face(fc, (176, 40, 40))
        earring(p, GOLD, side='right', row=5)
        # striped sailor-ish shirt, sleeves rolled, a stained apron; ankle shackle with a chain
        t = torso_part(p)
        stripes(p, t, '#efe8dc', '#3a4a7a', period=2)
        for side in ('right', 'left'):
            upper, fore = arm_parts(p, side)
            stripes(p, upper, '#efe8dc', '#3a4a7a', period=2)
            rows_color(p, fore, 0, 5, skin)
        hands(p, skin)
        apron_stains(p, 'apron', (150, 140, 110), ((1, 2), (5, 4), (2, 7), (6, 9)))
        apron_stains(p, 'apron_bib', (150, 140, 110), ((1, 1), (4, 4)))
        belt(p, 0, '#3a2a20', '#a0a0a0')
        pants_and_boots(p, '#3a3440', '#2a2020', rows=2)
        pt = p.face('plate', 'top')
        pt.px(2, 2, (200, 220, 240))
        pt.px(3, 3, (230, 240, 250))
    pal = palette(skin=skin, hair=hair_c, tunic='#efe8dc', sleeve='#efe8dc', sleeve2=skin, belly='#3a3440',
                  apron={'base': '#e4ddd0', 'style': 'cloth'}, rag='#c8d8e0', plate='#f2f2ee')
    return look('laszlo', builds=(SLIM,), show={'hair_cap', 'band', 'headband_tails', 'apron', 'apron_bib', 'rag',
                                               'plate', 'shackle_ankle'},
                poses={'right_arm': dict(rot=(-28, 0, -4)), 'right_forearm': dict(rot=(-46, -10, 0)),
                       'left_arm': dict(rot=(-24, 0, -4)), 'left_forearm': dict(rot=(-44, 0, 0)),
                       'headband_tails': dict(rot=(10, 0, 0))},
                pal=pal, paint=paint, work='work_dish', talk='talk_nervous', scale=1.0,
                portrait=dict(bg=('#8a6a4a', '#2a1e14')))


def wolfram():
    """The Silver Wolf, freed from the Ice Queen's spell, recovering in the guild infirmary."""
    skin = '#e2c2ae'
    hair_c = '#c8ccd4'
    face = Face(skin, eye='#7ab0d8', style='narrow', brows='#9aa0aa', brow_style='flat', nose='long', mouth='line',
                stubble='#a8a8b0', scar=[(5, 1), (5, 2), (6, 3), (6, 5)])

    def paint(p):
        face.paint(p)
        hair(p, hair_c, style='long', bangs=[3, 4, 2, 3, 4, 3, 2, 4], long_len=6, side_front=6,
             highlight=(245, 245, 250))
        hair_piece(p, 'hair_back', hair_c, ragged=True)
        # bandaged chest and arms, a silver wolf pelt on the shoulders, the broken shackle
        t = torso_part(p)
        f = p.face(t, 'front')
        for fc in faces_of(p, t, sides_only=True):
            for y in range(fc.h):
                for x in range(fc.w):
                    fc.px(x, y, shade(col(skin), 1.0 - 0.04 * y))
        bandage_wrap(p, t, rows=(2, 3, 5))
        f.px(2, 4, (190, 100, 90))
        for side in ('right', 'left'):
            upper, fore = arm_parts(p, side)
            rows_color(p, upper, 0, 5, skin)
            rows_color(p, fore, 0, 5, skin)
            bandage_wrap(p, fore, rows=(0, 1, 2, 3))
        bandage_wrap(p, arm_parts(p, 'left')[0], rows=(2, 3))
        hands(p, skin)
        mf = p.face('mantle', 'front')
        mf.px(2, 1, (40, 40, 48))
        mf.px(3, 1, (40, 40, 48))
        belt(p, 0, '#2a2a30', '#8a8a90')
        pants_and_boots(p, '#5a5c66', '#2a2a2e', rows=2)
    pal = palette(skin=skin, hair=hair_c, tunic=skin, sleeve=skin, sleeve2=skin, belly='#5a5c66',
                  mantle={'base': '#c8ccd2', 'style': 'fur'}, iron='#6a6e76')
    return look('wolfram', show={'hair_cap', 'hair_back', 'mantle', 'shackle_r'},
                poses={'right_arm': dict(rot=(-4, 0, 4)), 'left_arm': dict(rot=(-4, 0, -4)),
                       'hair_back': dict(scale=(1.0, 0.8, 1.0))},
                pal=pal, paint=paint, idle='idle_still', work='work_shoulder', talk='talk_gruff', scale=1.07,
                portrait=dict(bg=('#7a8ab0', '#1e2434')))


def adel():
    """Captain of the knights: polished plate, the royal tabard and cape, greatsword planted in front of her."""
    skin = '#f0ccb2'
    hair_c = '#e08a54'
    face = Face(skin, eye='#3a6a9a', style='tall', brows='#b0623a', brow_style='angry', mouth='line')

    def paint(p):
        face.paint(p)
        hair(p, hair_c, bangs=[2, 3, 3, 2, 1, 2, 3, 2], back_len=6, side_front=5, highlight=(250, 180, 120))
        hair_piece(p, 'ponytail', hair_c)
        hair_piece(p, 'ponytail_tail', hair_c, ragged=True)
        for fc in faces_of(p, 'ponytail'):
            if fc.face not in ('top', 'bottom'):
                fc.hline(0, fc.w - 1, 0, col(GOLD))
        # a thin gold circlet with the crest
        for fc in faces_of(p, 'band'):
            if fc.face in ('top', 'bottom'):
                mask_alpha(fc, lambda x, y: False)
                continue
            for y in range(fc.h):
                for x in range(fc.w):
                    if y == 1:
                        fc.px(x, y, shade(col(GOLD), 1.08 if x % 2 else 0.92))
                    else:
                        fc.clear(x, y)
        p.face('band', 'front').px(3, 1, (120, 170, 250))
        p.face('band', 'front').px(4, 1, (120, 170, 250))
        t = torso_part(p)
        plate(p, t, '#b8c0cc')
        tabard(p, ROYAL, back=False)
        for side in ('right', 'left'):
            upper, fore = arm_parts(p, side)
            plate(p, fore, '#b0b8c4')
            rows_color(p, upper, 0, 5, '#2a3a6a')
        hands(p, '#3a3040', rows=2, gloves=True)
        cf = p.face('cape', 'back')
        crest(cf, cf.w // 2, 6, GOLD)
        for fc in faces_of(p, 'cape', sides_only=True):
            fc.hline(0, fc.w - 1, fc.h - 1, col(GOLD))
        belt(p, 0, '#2a2030', '#e0c050', buckle_w=3)
        legs(p, '#2a3050')
        boots(p, '#b8c0cc', rows=4, top_trim='#e0c050')
        for part in ('right_shin', 'left_shin'):
            plate(p, part, '#b8c0cc', faces=('front',), rivet=False)
    pal = palette(skin=skin, hair=hair_c, tunic='#b8c0cc', sleeve='#2a3a6a', sleeve2='#b0b8c4', belly='#2a3050',
                  armor={'base': '#c4ccd8', 'style': 'metal'}, apron=ROYAL, cape={'base': ROYAL, 'style': 'cloth'},
                  blade={'base': '#d4dce4', 'style': 'metal'}, guard={'base': '#e0c050', 'style': 'gold'},
                  grip='#3a2a3a', boots={'base': '#b8c0cc', 'style': 'metal'})
    return look('adel', show={'hair_cap', 'ponytail', 'band', 'pauldron_r', 'pauldron_l', 'apron', 'apron_bib', 'cape',
                              'greatsword'},
                poses={'right_arm': dict(rot=(-24, 0, -22)), 'right_forearm': dict(rot=(-24, 0, 0)),
                       'left_arm': dict(rot=(-24, 0, 22)), 'left_forearm': dict(rot=(-24, 0, 0)),
                       'greatsword': dict(pos=(0, 2.5, 0)), 'cape': dict(scale=(1.0, 0.98, 1.0))},
                pal=pal, paint=paint, idle='idle_still', work='work_watch', talk='talk_point', scale=1.0,
                portrait=dict(bg=('#3a62c0', '#121e44')))


def neris():
    """The sage of the Elder Forest: an elf over three hundred years old, flower wreath and a budding staff."""
    skin = '#f2dcc6'
    hair_c = '#d8e4d0'
    face = Face(skin, eye='#3a9a5a', style='tall', brows='#a8b8a0', mouth='smile')

    def paint(p):
        face.paint(p)
        hair(p, hair_c, style='long', bangs=[2, 2, 3, 1, 1, 3, 2, 2], long_len=8, side_front=8, part_x=4,
             highlight=(250, 255, 245))
        hair_piece(p, 'hair_back', hair_c, ragged=True)
        for fc in faces_of(p, 'ear_r') + faces_of(p, 'ear_l'):
            fill_face(fc, col(skin))
        wt = p.face('wreath', 'top')
        for (x, y) in ((1, 1), (4, 0), (7, 2), (2, 7), (6, 6), (0, 4), (8, 5)):
            wt.px(x, y, (250, 200, 220) if (x + y) % 2 else (250, 240, 150))
        for fc in faces_of(p, 'wreath', sides_only=True):
            for x in range(1, fc.w, 3):
                fc.px(x, 0, (250, 210, 230))
        # green robe with leaf embroidery and a golden sash
        t = torso_part(p)
        f = p.face(t, 'front')
        vneck(f, '#e8f0dc', depth=3)
        for fc in faces_of(p, 'robe_long', sides_only=True):
            for (x, y) in ((1, 3), (3, 7), (6, 5), (2, 11), (7, 12), (5, 9)):
                if x < fc.w and y < fc.h:
                    fc.px(x, y, (150, 200, 120))
            fc.hline(0, fc.w - 1, fc.h - 1, col(GOLD))
        sleeves_cuff(p, '#e8f0dc', row=3)
        hands(p, skin)
        belt(p, 0, '#c8a040', None)
        glow_fill(p, 'staff_bud', (240, 140, 200))
    pal = palette(skin=skin, hair=hair_c, ear=skin, tunic='#3e7a4a', sleeve='#3e7a4a', sleeve2='#3e7a4a',
                  belly='#3e7a4a', robe={'base': '#3e7a4a', 'style': 'cloth'},
                  wreath={'base': '#5a9a40', 'style': 'leaf', 'flowers': (250, 200, 220)},
                  staff={'base': '#7a5a3a', 'style': 'bark', 'moss': (100, 140, 70)},
                  bud={'base': '#f4a8d0', 'style': 'gem'})
    return look('neris', builds=(SLIM, NO_LEGS), show={'hair_cap', 'hair_back', 'ear_r', 'ear_l', 'wreath',
                                                       'robe_long', 'staff_nature'},
                poses={'right_arm': dict(rot=(-14, 0, 4)), 'right_forearm': dict(rot=(-20, 0, 0)),
                       'left_arm': dict(rot=(-8, 0, -6))},
                pal=pal, paint=paint, glow=True, work='work_staff', talk='talk_scholar', scale=0.97,
                portrait=dict(bg=('#5a9a5a', '#14301a')))


def khalid():
    """The cheerful caravan master of Sandria: turban with a ruby, sand robes, a red sash and a lucky coin."""
    skin = '#a8704a'
    beard_c = '#2c2018'
    face = Face(skin, eye='#3a2414', style='normal', brows='#1e1814', brow_style='raised', mouth='none', nose='big')

    def paint(p):
        face.paint(p)
        for i in range(len(p.rig.parts['beard'].cubes)):
            hair_piece(p, 'beard', beard_c, index=i)
        bf = p.face('beard', 'front')
        for x in (0, 1, 2, 3, 4, 5, 6, 7):
            bf.clear(x, 0)                            # nose and cheeks stay visible
        for x in (0, 7):
            bf.clear(x, 1)
        bf.hline(2, 5, 1, (60, 24, 20))
        bf.hline(3, 4, 1, (236, 230, 220))
        for y in range(2, bf.h):
            bf.px(2, y, mix(bf.get(2, y), (120, 90, 70), 0.35))
        hf = p.face('head', 'front')
        hf.hline(1, 6, 5, mix(col(skin), col(beard_c), 0.55))
        tu = p.face('turban', 'front', index=0)
        for x in range(tu.w):
            tu.px(x, 1, (200, 190, 170) if x % 3 == 0 else tu.get(x, 1))
        # sand coat over a white tunic, red sash with gold, gold bangles
        t = torso_part(p)
        f = p.face(t, 'front')
        open_front(f, '#f0ebe0', width=2, lapel=(200, 160, 70), rows=f.h - 1)
        for fc in faces_of(p, 'hips', sides_only=True):
            for y in range(0, 3):
                fc.hline(0, fc.w - 1, y, (170, 34, 40) if y != 1 else (210, 60, 60))
        hfc = p.face('hips', 'front')
        hfc.px(5, 1, col(GOLD))
        hfc.px(5, 2, (200, 150, 50))
        for side in ('right', 'left'):
            _, fore = arm_parts(p, side)
            rows_color(p, fore, 3, 3, GOLD)
        hands(p, skin)
        legs(p, '#e8dcc0')
        boots(p, '#7a4a2a', rows=2, top_trim='#c8a050')
    pal = palette(skin=skin, beard=beard_c, tunic='#d4b88a', sleeve='#d4b88a', sleeve2='#d4b88a', belly='#d4b88a',
                  turban={'base': '#f2ece0', 'style': 'cloth'}, jewel={'base': '#d0202a', 'style': 'gem'},
                  pouch='#8a5a30')
    return look('khalid', show={'beard', 'turban', 'coin', 'coin_pouch'}, broad=True,
                poses={'right_arm': dict(rot=(-24, 0, 4)), 'right_forearm': dict(rot=(-56, 0, 0)),
                       'left_arm': dict(rot=(-8, 0, -6)), 'left_forearm': dict(rot=(-30, 0, 0))},
                pal=pal, paint=paint, work='work_coinflip', talk='talk_hearty', scale=1.03,
                portrait=dict(bg=('#d8a050', '#4a2a10')))


def helga():
    """The chieftain of Nordia: red braids, winged helm, fur mantle and her horn of fire-spirits."""
    skin = '#f0d0bc'
    hair_c = '#c8502a'
    face = Face(skin, eye='#4a8ad0', style='normal', brows='#a8401e', brow_style='thick', mouth='grin', teeth=True,
                freckles=True, mouth_w=3)

    def paint(p):
        face.paint(p)
        hair(p, hair_c, bangs=[1, 2, 2, 1, 1, 2, 2, 1], back_len=7, side_front=6, highlight=(240, 140, 90))
        for part in ('braid_r', 'braid_l'):
            hair_piece(p, part, hair_c, braid=True)
            for fc in faces_of(p, part, sides_only=True):
                fc.hline(0, fc.w - 1, fc.h - 2, (200, 180, 120))
        # blue war paint under the eyes
        f = p.face('head', 'front')
        for x in (1, 2, 5, 6):
            f.px(x, 5, (60, 100, 200))
        # iron helm band with the wings
        for fc in faces_of(p, 'band'):
            if fc.face == 'bottom':
                mask_alpha(fc, lambda x, y: False)
                continue
            if fc.face == 'top':
                fill_face(fc, (130, 134, 142), shade_rows=False)
                continue
            for y in range(fc.h):
                for x in range(fc.w):
                    if y < 2:
                        fc.px(x, y, (140, 144, 152) if y == 0 else (100, 104, 112))
                    else:
                        fc.clear(x, y)
        p.face('band', 'front').px(3, 1, (220, 190, 90))
        p.face('band', 'front').px(4, 1, (220, 190, 90))
        # wool dress in Nordic blue with a knotted pattern, fur mantle and cuffs
        t = torso_part(p)
        tf = p.face(t, 'front')
        for x in range(tf.w):
            if x % 2 == 0:
                tf.px(x, 1, (230, 220, 200))
            tf.px(x, 2, (200, 60, 50))
        hands(p, skin)
        belt(p, 0, '#3a2a1c', '#c0a050', buckle_w=3)
        legs(p, '#4a3a2c')
        boots(p, '#5a4030', rows=3)
    pal = palette(skin=skin, hair=hair_c, tunic='#3a5a8a', sleeve='#3a5a8a', sleeve2='#3a5a8a', belly='#3a5a8a',
                  mantle={'base': '#8a6a4a', 'style': 'fur'}, cuff={'base': '#d8cdb8', 'style': 'fur'},
                  wing='#f0f0f0', horn='#e8d8b0')
    return look('helga', show={'hair_cap', 'band', 'braid_r', 'braid_l', 'helm_wing_r', 'helm_wing_l', 'mantle',
                               'fur_cuff_r', 'fur_cuff_l', 'drink_horn'}, broad=True,
                poses={'right_arm': dict(rot=(-20, 0, 6)), 'right_forearm': dict(rot=(-40, 0, 0)),
                       'left_arm': dict(rot=(-6, 0, -20)), 'left_forearm': dict(rot=(-30, 0, 0))},
                pal=pal, paint=paint, work='work_drink', talk='talk_hearty', scale=1.04,
                portrait=dict(bg=('#6a8ab8', '#182234'), dy=-1.5))


def ilsa():
    """The old dragonsmith: Goran's teacher, goggles up, dragon-scale gauntlet, a hammer as tall as she is."""
    skin = '#c89a7a'
    hair_c = '#e8e4e0'
    face = Face(skin, eye='#a0441e', style='narrow', brows='#e0dcd8', brow_style='thick', mouth='smirk',
                wrinkles=True, mouth_w=2)

    def paint(p):
        face.paint(p)
        hair(p, hair_c, bangs=[1, 1, 0, 1, 1, 0, 1, 1], back_len=6, side_front=5, highlight=(255, 255, 255))
        hair_piece(p, 'bun_back', hair_c)
        f = p.face('head', 'front')
        f.px(6, 6, shade(col(skin), 0.7))            # soot
        f.px(1, 2, shade(col(skin), 0.75))
        for i in range(2):
            gf = p.face('goggles', 'front', index=i)
            fill_face(gf, (240, 150, 60))
            gf.px(0, 0, (255, 220, 160))
        # leather apron over a soot-grey dress, sleeves rolled, the dragon-scale gauntlet
        t = torso_part(p)
        for side in ('right', 'left'):
            upper, fore = arm_parts(p, side)
            rows_color(p, fore, 1, 5, skin)
        hands(p, skin)
        af = p.face('apron', 'front')
        af.rect(2, 3, 5, 4, (110, 76, 46))
        af.px(5, 8, (40, 30, 24))
        belt(p, 0, '#2a1a10', '#a08060')
        trim_row(p, 'skirt_long', 7, '#2a2420', index=1)
    pal = palette(skin=skin, hair=hair_c, tunic='#5a5450', sleeve='#5a5450', sleeve2='#5a5450', belly='#5a5450',
                  skirt={'base': '#4e4844', 'style': 'cloth'}, skirt2={'base': '#4a4440', 'style': 'cloth'},
                  apron={'base': '#7a5232', 'style': 'leather'}, gauntlet='#b03a24', goggle='#8a6a40',
                  tool={'base': '#4a4e56', 'style': 'metal'}, haft='#5a3a20')
    return look('ilsa', builds=(NO_LEGS,), show={'hair_cap', 'bun_back', 'goggles', 'apron', 'apron_bib',
                                                 'skirt_long', 'gauntlet_l', 'hammer_big'},
                poses={'right_arm': dict(rot=(-10, 0, 8)), 'right_forearm': dict(rot=(-14, 0, 0)),
                       'chest': dict(rot=(6, 0, 0)), 'head': dict(rot=(-6, 0, 0))},
                pal=pal, paint=paint, work='work_bighammer', talk='talk_gruff', scale=0.92,
                portrait=dict(bg=('#d06a2a', '#3a1408')))


def selene():
    """The oracle automaton of Celestia: porcelain face, glowing eyes, a halo and the armillary sphere."""
    skin = '#f4eee8'
    hair_c = '#a8c4e0'
    face = Face(skin, eye='#60e0ff', style='glow', brows=None, mouth='small', glow_eyes=(120, 240, 255),
                lips=(170, 160, 170))

    def paint(p):
        face.paint(p)
        hair(p, hair_c, bangs=[3, 3, 3, 3, 3, 3, 3, 3], back_len=6, side_front=6, highlight=(240, 250, 255))
        f = p.face('head', 'front')
        for y in (5, 6, 7):                           # porcelain seams
            f.px(0, y, shade(col(skin), 0.8))
            f.px(7, y, shade(col(skin), 0.8))
        f.px(2, 5, (200, 210, 230))
        f.px(5, 5, (200, 210, 230))
        g = p.face('halo', 'front', glow=True)
        hf = p.face('halo', 'front')
        for name in ('front', 'back'):
            fc = p.face('halo', name)
            mask_alpha(fc, lambda x, y: 4.2 <= ((x - 5.5) ** 2 + (y - 5.5) ** 2) ** 0.5 <= 6.0)
        for y in range(hf.h):
            for x in range(hf.w):
                if 4.2 <= ((x - 5.5) ** 2 + (y - 5.5) ** 2) ** 0.5 <= 6.0:
                    g.px(x, y, (250, 220, 120))
        # white and gold robe with blue panels; joints at the wrists
        t = torso_part(p)
        tf = p.face(t, 'front')
        tf.vline(tf.w // 2, 1, tf.h - 1, col(GOLD))
        pendant(p, (110, 230, 255), glow=(110, 230, 255), y=3)
        for fc in faces_of(p, 'robe_long', sides_only=True):
            fc.hline(0, fc.w - 1, fc.h - 1, col(GOLD))
            for y in range(0, fc.h, 4):
                fc.px(fc.w // 2, y, (120, 170, 230))
        rf = p.face('robe_long', 'front')
        for y in range(rf.h):
            rf.px(rf.w // 2, y, (110, 160, 220))
        for side in ('right', 'left'):
            _, fore = arm_parts(p, side)
            rows_color(p, fore, 3, 3, (200, 200, 210))
        hands(p, skin)
        glow_fill(p, 'armillary', (140, 240, 255))
    pal = palette(skin=skin, hair=hair_c, tunic='#f2f0ec', sleeve='#f2f0ec', sleeve2='#f2f0ec', belly='#f2f0ec',
                  robe={'base': '#f2f0ec', 'style': 'cloth'}, halo={'base': '#f0d070', 'style': 'gold'},
                  ring='#e8c860')
    return look('selene', builds=(SLIM, NO_LEGS), show={'hair_cap', 'halo', 'robe_long', 'armillary'},
                poses={'right_arm': dict(rot=(-30, 0, -10)), 'right_forearm': dict(rot=(-30, 0, 0)),
                       'left_arm': dict(rot=(-30, 0, 10)), 'left_forearm': dict(rot=(-30, 0, 0))},
                pal=pal, paint=paint, glow=True, idle='idle_hover', work='work_orb', talk='talk_mech', scale=0.96,
                portrait=dict(bg=('#6ab0e0', '#142a40'), dy=-1.5))


def gunter():
    """The watcher of the Tower of the End: hooded cloak, ancestral mail, a long beard and the lantern staff."""
    skin = '#d8b098'
    beard_c = '#d8d4cc'
    face = Face(skin, eye='#5a6a7a', style='narrow', brows='#e0dcd4', brow_style='thick', mouth='none', wrinkles=True)

    def paint(p):
        face.paint(p)
        for i in range(len(p.rig.parts['beard_big'].cubes)):
            hair_piece(p, 'beard_big', beard_c, index=i)
        bf = p.face('beard_big', 'front')
        for x in (3, 4, 5):
            bf.clear(x, 0)
        bf.hline(3, 5, 2, (90, 70, 60))
        # dark hood with a worn edge
        for fc in faces_of(p, 'shell'):
            if fc.face == 'bottom':
                mask_alpha(fc, lambda x, y: False)
        shell_face_opening(p, 'shell', left=1, right=1, top=2, bottom=0, rim=(70, 66, 74))
        mail(p, torso_part(p), '#6a7078')
        for side in ('right', 'left'):
            upper, fore = arm_parts(p, side)
            mail(p, fore, '#6a7078', rows=(0, 1, 2, 3))
        hands(p, '#4a3a30', rows=2, gloves=True)
        for fc in faces_of(p, 'cape', sides_only=True):
            for x in range(0, fc.w, 2):
                fc.clear(x, fc.h - 1)
        belt(p, 0, '#2a2420', '#8a8a90')
        legs(p, '#3a3a40')
        boots(p, '#2a2420', rows=3)
        glow_fill(p, 'lantern', (255, 200, 110), index=0, faces=('front', 'back', 'right', 'left'))
    pal = palette(skin=skin, beard=beard_c, tunic='#4a4652', sleeve='#4a4652', sleeve2='#4a4652', belly='#3a3640',
                  shell={'base': '#4a4652', 'style': 'cloth'}, cape={'base': '#3e3a46', 'style': 'cloth'},
                  lantern={'base': '#f0c070', 'style': 'glowcore'}, iron='#4a4e56')
    return look('gunter', show={'shell', 'beard_big', 'cape', 'lantern_staff'},
                poses={'right_arm': dict(rot=(-14, 0, 6)), 'right_forearm': dict(rot=(-26, 0, 0))},
                pal=pal, paint=paint, glow=True, idle='idle_still', work='work_lantern', talk='talk_gruff', scale=1.0,
                portrait=dict(bg=('#5a5470', '#16141e')))

def cast():
    return [lilia(), gustav(), nox(), laszlo(), wolfram(), adel(), neris(), khalid(), helga(), ilsa(), selene(), gunter()]
