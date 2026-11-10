"""The ten service NPCs of the capital (NpcRole -> Speaker ids): mina, pim, goran, hagen, luce, chico, marta, gant, zain,
chloe. See docs/STORY.md section 2.3 for who they are."""
from erpaint import lighten, mix, shade
from npc_cast import (BROAD, GOLD, NO_LEGS, ROYAL, ROYAL_DARK, SLIM, WHITE, apron_stains, crest, earring, look, mail,
                      palette, pants_and_boots, plate, round_glasses, sleeves_cuff, tabard)
from npc_paint import (Face, arm_parts, bald_head, bare_arms, belt, boots, buttons, clear_part, col, faces_of, fill_face,
                       hair, hair_piece, hands, legs, mask_alpha, open_front, page_lines, rows_color, shell_face_opening,
                       torso_part, trim_col, trim_row, vneck)


# ------------------------------------------------------------------------------------------------- helpers

def cap(p, c, rows=3, part='band', band=None):
    """A soft cap on a head shell: the crown and the upper rows of the four sides."""
    c = col(c)
    for fc in faces_of(p, part):
        if fc.face == 'bottom':
            mask_alpha(fc, lambda x, y: False)
            continue
        if fc.face == 'top':
            fill_face(fc, lighten(c, 0.06), shade_rows=False)
            continue
        for y in range(fc.h):
            for x in range(fc.w):
                if y < rows:
                    fc.px(x, y, shade(c, (1.05 - 0.08 * y) * (0.94 if x in (0, fc.w - 1) else 1.0)))
                else:
                    fc.clear(x, y)
        if band is not None:
            fc.hline(0, fc.w - 1, rows - 1, col(band))


def helmet(p, steel, part='shell', left=1, right=1, top=2, bottom=0, rim=None):
    """A steel helmet on a head shell with an open face."""
    for fc in faces_of(p, part):
        if fc.face == 'bottom':
            mask_alpha(fc, lambda x, y: False)
    plate(p, part, steel, faces=('front', 'right', 'back', 'left'), rivet=False)
    top_fc = p.face(part, 'top')
    fill_face(top_fc, lighten(col(steel), 0.12), shade_rows=False)
    top_fc.vline(top_fc.w // 2, 0, top_fc.h - 1, lighten(col(steel), 0.4))
    shell_face_opening(p, part, left=left, right=right, top=top, bottom=bottom, rim=rim)


def mustache_plane(p, c, curl=True, row=1, gap=False):
    """The 10-wide mustache plane (plane x k = head x k-1): a bar under the nose with curled tips."""
    clear_part(p, 'mustache')
    f = p.face('mustache', 'front')
    c = col(c)
    for x in range(1, 9):
        if gap and x in (4, 5):
            continue
        f.px(x, row, shade(c, 1.1 if x in (2, 7) else 0.95))
    if curl:
        f.px(0, row - 1, shade(c, 0.9))
        f.px(9, row - 1, shade(c, 0.9))
        f.px(1, row, c)
        f.px(8, row, c)


# ------------------------------------------------------------------------------------------------- the guild

def mina():
    skin = '#f0c8a8'
    hair_c = '#c8743a'
    face = Face(skin, eye='#2f8a5a', style='tall', brows='#9a5a2a', mouth='smile', blush=True)

    def paint(p):
        face.paint(p)
        hair(p, hair_c, bangs=[3, 2, 3, 2, 2, 3, 2, 3], back_len=7, side_front=4)
        hair_piece(p, 'ponytail', hair_c)
        hair_piece(p, 'ponytail_tail', hair_c, ragged=True)
        # red ribbon on the ponytail
        for fc in faces_of(p, 'ponytail'):
            if fc.face in ('top', 'bottom'):
                continue
            fc.rect(0, 0, fc.w - 1, 0, (210, 50, 60))
            fc.rect(0, 1, fc.w - 1, 1, (170, 36, 48))
        t = torso_part(p)
        f = p.face(t, 'front')
        # white blouse under a teal vest, red ribbon tie, gold guild badge
        open_front(f, '#f2eee2', width=2)
        f.px(3, 0, (210, 50, 60))
        f.px(4, 0, (210, 50, 60))
        f.px(3, 1, (180, 40, 50))
        f.px(4, 1, (180, 40, 50))
        f.px(1, 2, (240, 200, 90))
        f.px(1, 3, (200, 160, 70))
        buttons(f, 2, (4, 5), (230, 210, 150))
        for side in ('right', 'left'):
            upper, fore = arm_parts(p, side)
            rows_color(p, upper, 0, 5, '#f2eee2')
            rows_color(p, fore, 0, 3, '#f2eee2')
            rows_color(p, fore, 3, 3, '#2e7f86')
        hands(p, skin)
        belt(p, 0, '#2c3e66', None)
        # navy skirt with a white hem, dark stockings, brown boots
        trim_row(p, 'skirt_short', 6, '#e8e4dc')
        legs(p, '#3a3048')
        boots(p, '#6a4028', rows=2)
        # ledger and quill
        lf = p.face('ledger', 'top')
        page_lines(lf, paper=(240, 234, 214))
        lf.vline(0, 0, lf.h - 1, (90, 50, 40))
    pal = palette(skin=skin, hair=hair_c, tunic='#2e7f86', sleeve='#f2eee2', sleeve2='#f2eee2', belly='#2c3e66',
                  skirt={'base': '#2c3e66', 'style': 'cloth'}, book='#8a3a2a', quill='#f4f0e8')
    return look('mina', builds=(SLIM,), show={'hair_cap', 'ponytail', 'skirt_short', 'ledger', 'quill'},
                poses={'left_arm': dict(rot=(-18, 0, -6)), 'left_forearm': dict(rot=(-62, 0, 0)),
                       'right_arm': dict(rot=(-22, -10, -12)), 'right_forearm': dict(rot=(-58, 0, 0))},
                pal=pal, paint=paint, work='work_write', talk='talk_bow', scale=0.95,
                portrait=dict(bg=('#3f8f8a', '#1d3f44')))


def pim():
    skin = '#eab796'
    hair_c = '#6e4528'
    face = Face(skin, eye='#3a2a20', style='normal', brows='#5a3820', brow_style='raised', nose='big', mouth='grin',
                mouth_row=7, teeth=True, cheek='#e8907e', mouth_w=4)

    def paint(p):
        face.paint(p)
        hair(p, hair_c, bangs=[1, 1, 1, 1, 1, 1, 1, 1], back_len=6, side_front=5)
        cap(p, '#3f7a3a', rows=3, band='#d8b04a')
        mustache_plane(p, '#5a3820', curl=True)
        t = torso_part(p)
        f = p.face(t, 'front')
        vneck(f, '#f0e6cc', depth=2)
        # green apron with a pocket and a stitched hem; the potion bandolier on the bib
        for part in ('apron', 'apron_bib'):
            for fc in faces_of(p, part, sides_only=True):
                fc.hline(0, fc.w - 1, fc.h - 1, (40, 80, 40))
        af = p.face('apron', 'front')
        af.rect(2, 2, 5, 4, (60, 112, 56))
        af.hline(2, 5, 2, (44, 88, 42))
        af.px(3, 3, (230, 200, 90))
        bib = p.face('apron_bib', 'front')
        bib.hline(0, bib.w - 1, 2, (110, 70, 40))
        for x in range(0, bib.w, 2):
            bib.px(x, 3, (130, 84, 48))
        # rolled-up ochre sleeves
        for side in ('right', 'left'):
            upper, fore = arm_parts(p, side)
            rows_color(p, fore, 2, 5, skin)
            rows_color(p, fore, 1, 1, '#e8c070')
        hands(p, skin)
        belt(p, 0, '#4a3020', '#d8b04a')
        pants_and_boots(p, '#5a4434', '#3b2a20', rows=2)
        # coin pouch with a gold coin peeking out
        cp = p.face('coin_pouch', 'top')
        cp.px(1, 1, (240, 200, 70))
    pal = palette(skin=skin, hair=hair_c, beard='#5a3820', tunic='#d9a94a', sleeve='#d9a94a', sleeve2='#d9a94a',
                  belly='#d9a94a', apron={'base': '#4c8a46', 'style': 'cloth'}, hat='#3f7a3a',
                  potion_hand={'base': '#e83848', 'style': 'gem'}, pouch='#8a5a30')
    return look('pim', show={'hair_cap', 'band', 'brim', 'mustache', 'belly', 'apron', 'apron_bib', 'potions',
                             'potion_hand', 'coin_pouch'},
                poses={'right_arm': dict(rot=(-38, 0, 6)), 'right_forearm': dict(rot=(-52, 0, 0)),
                       'left_arm': dict(rot=(-6, 0, -6)), 'left_forearm': dict(rot=(-34, 0, 0)),
                       'brim': dict(rot=(-4, 0, 0))},
                pal=pal, paint=paint, work='work_potion', talk='talk_flourish', scale=0.96,
                portrait=dict(bg=('#5f9a52', '#24401f')))


def goran():
    skin = '#b8805a'
    beard_c = '#3e2a1c'
    face = Face(skin, eye='#3a2a20', style='stern', brows='#2a1a10', brow_style='thick', nose='big', mouth='none',
                scar=[(6, 2), (7, 3)])

    def paint(p):
        face.paint(p)
        bald_head(p, skin, fringe=beard_c, fringe_rows=(4, 6))
        for part in ('beard_big', 'beard_braids'):
            for i in range(len(p.rig.parts[part].cubes)):
                hair_piece(p, part, beard_c, braid=(part == 'beard_braids'), index=i)
        bf = p.face('beard_big', 'front')
        # leave the nose and a mouth gap; grey streak
        for x in (3, 4, 5):
            bf.clear(x, 0)
        bf.px(4, 2, (40, 22, 18))
        bf.px(5, 2, (40, 22, 18))
        for y in range(1, bf.h):
            bf.px(2, y, mix(bf.get(2, y), (130, 120, 110), 0.3))
        for i in range(2):
            fc = p.face('beard_braids', 'front', index=i)
            fc.rect(0, 1, fc.w - 1, 1, (150, 150, 160))
            fc.rect(0, 3, fc.w - 1, 3, (150, 150, 160))
        # soot
        f = p.face('head', 'front')
        f.px(1, 2, shade(col(skin), 0.75))
        t = torso_part(p)
        bare_arms(p, skin)
        hands(p, '#4a3020', rows=2, gloves=True)
        tf = p.face(t, 'front')
        for x in range(tf.w):
            tf.px(x, 0, shade(col(skin), 0.9))
        # leather apron with rivets and a scorch mark
        for part in ('apron', 'apron_bib'):
            fc = p.face(part, 'front')
            for (x, y) in ((0, 0), (fc.w - 1, 0)):
                fc.px(x, y, (190, 180, 160))
        af = p.face('apron', 'front')
        af.rect(2, 2, 5, 4, shade(col('#6a4a2e'), 0.8))
        af.hline(2, 5, 2, shade(col('#6a4a2e'), 0.65))
        af.px(5, 7, (40, 30, 24))
        af.px(6, 8, (40, 30, 24))
        belt(p, 0, '#2a1a10', '#8a8a90')
        pants_and_boots(p, '#3a3530', '#2a2420', rows=3)
    pal = palette(skin=skin, beard=beard_c, tunic='#4a4a50', sleeve='#4a4a50', sleeve2=skin, belly='#3a3a40',
                  apron={'base': '#6a4a2e', 'style': 'leather'}, tool={'base': '#5a5e66', 'style': 'metal'},
                  haft='#5a3a20')
    return look('goran', show={'beard_big', 'beard_braids', 'apron', 'apron_bib', 'hammer'}, broad=True,
                poses={'right_arm': dict(rot=(-22, 0, 4)), 'right_forearm': dict(rot=(-38, 0, 0)),
                       'left_arm': dict(rot=(-6, 0, -4))},
                pal=pal, paint=paint, work='work_hammer', talk='talk_gruff', scale=1.04,
                portrait=dict(bg=('#b8562a', '#3a1a10')))


def hagen():
    skin = '#d39a72'
    hair_c = '#b8442a'
    face = Face(skin, eye='#2a3a6a', style='stern', brows='#7a2a1a', brow_style='angry', nose='small', mouth='grin',
                teeth=True, mouth_w=3, stubble='#8a5a44', scar=[(1, 6), (2, 7)])

    def paint(p):
        face.paint(p)
        hair(p, hair_c, bangs=[2, 1, 3, 1, 2, 3, 1, 2], back_len=4, side_front=3, highlight=(240, 140, 90))
        # spikes: ragged top edge on the sides
        for name in ('right', 'left', 'back'):
            fc = p.face('hair_cap', name)
            for x in range(0, fc.w, 2):
                fc.clear(x, 0)
        # red headband with long tails
        for fc in faces_of(p, 'band'):
            if fc.face in ('top', 'bottom'):
                mask_alpha(fc, lambda x, y: False)
                continue
            for y in range(fc.h):
                for x in range(fc.w):
                    if y == 2:
                        fc.px(x, y, (244, 242, 236) if x % 3 else (220, 218, 210))
                    else:
                        fc.clear(x, y)
        bf = p.face('band', 'front')
        bf.px(3, 2, (210, 40, 40))
        bf.px(4, 2, (210, 40, 40))
        for fc in faces_of(p, 'headband_tails'):
            fill_face(fc, (236, 234, 226))
        # royal blue quilted vest (sleeveless) with gold edging, bare strong arms with leather bracers
        t = torso_part(p)
        f = p.face(t, 'front')
        for y in range(f.h):
            for x in range(f.w):
                if (x + y) % 3 == 0:
                    f.px(x, y, shade(f.get(x, y), 0.86))
        vneck(f, skin, depth=2)
        trim_col(f, f.w // 2 - 2, GOLD, 2, f.h - 1)
        trim_col(f, f.w // 2 + 1, GOLD, 2, f.h - 1)
        bare_arms(p, skin)
        hands(p, '#5a3a24', rows=1, gloves=True)
        belt(p, 0, '#3a2414', '#d8b04a', buckle_w=3)
        pants_and_boots(p, '#2a2a34', '#3a2a20', rows=3, trim='#5a4030')
    pal = palette(skin=skin, hair=hair_c, tunic=ROYAL, sleeve=skin, sleeve2=skin, belly='#26304a',
                  bracer='#6a4428', blade={'base': '#a07a4a', 'style': 'wood'}, guard='#7a5a3a', grip='#4a2c1c')
    return look('hagen', show={'hair_cap', 'band', 'headband_tails', 'bracer_r', 'bracer_l', 'practice_sword'},
                broad=True,
                poses={'right_arm': dict(rot=(-14, 0, 6)), 'right_forearm': dict(rot=(-30, 0, 0)),
                       'left_arm': dict(rot=(-4, 0, -8)), 'left_forearm': dict(rot=(-18, 0, 0))},
                pal=pal, paint=paint, work='work_kata', talk='talk_point', scale=1.03,
                portrait=dict(bg=('#3c5fb0', '#16224a')))


def luce():
    skin = '#f2d0b8'
    hair_c = '#8a2a3a'
    face = Face(skin, eye='#c08a2a', style='tall', brows='#5a1a24', brow_style='raised', mouth='smirk',
                lips=(170, 50, 70), beauty_mark=True)

    def paint(p):
        face.paint(p)
        hair(p, hair_c, style='long', bangs=[4, 3, 2, 2, 1, 1, 1, 2], long_len=7, side_front=6,
             highlight=(200, 90, 110))
        hair_piece(p, 'hair_back', hair_c, ragged=True)
        # black beret with a gold pin and a white feather
        bt = p.face('beret', 'front')
        bt.px(bt.w - 3, 0, (230, 200, 90))
        # wine coat: gold buttons, white jabot, lapels
        t = torso_part(p)
        f = p.face(t, 'front')
        open_front(f, '#f4f0e8', width=2, lapel=(110, 24, 40), rows=3)
        f.px(3, 1, (250, 250, 250))
        f.px(4, 1, (230, 230, 230))
        f.px(3, 2, (240, 240, 240))
        f.px(4, 2, (220, 220, 220))
        buttons(f, 2, (3, 5), (240, 205, 90))
        buttons(f, 5, (3, 5), (240, 205, 90))
        sleeves_cuff(p, '#f4f0e8', row=4)
        for side in ('right', 'left'):
            _, fore = arm_parts(p, side)
            rows_color(p, fore, 3, 3, GOLD)
        hands(p, skin, rows=1)
        belt(p, 0, '#2a1418', '#e0c050')
        legs(p, '#24202a')
        boots(p, '#4a2a1c', rows=4, top_trim='#6a3e28')
        trim_row(p, 'coat_tails', 8, GOLD)
    pal = palette(skin=skin, hair=hair_c, tunic='#7a1e34', sleeve='#7a1e34', sleeve2='#7a1e34', belly='#7a1e34',
                  coat={'base': '#7a1e34', 'style': 'cloth'}, hat='#1e1a22', quill='#f4f2ee', gavel='#5a3420',
                  handle='#4a2a18')
    return look('luce', builds=(SLIM,), show={'hair_cap', 'hair_back', 'beret', 'coat_tails', 'gavel'},
                poses={'right_arm': dict(rot=(-18, 0, 8)), 'right_forearm': dict(rot=(-40, 0, 0)),
                       'left_arm': dict(rot=(-24, 0, -10)), 'left_forearm': dict(rot=(-50, 0, 0)),
                       'beret': dict(rot=(0, 0, 4))},
                pal=pal, paint=paint, work='work_gavel', talk='talk_flourish', scale=0.97,
                portrait=dict(bg=('#9a3a52', '#2e0e18'), dy=-1.5))


def chico():
    skin = '#f2c8a0'
    hair_c = '#e0b048'
    face = Face(skin, eye='#3a7ac8', style='round', brows='#b88a30', mouth='grin', freckles=True, mouth_w=2)

    def paint(p):
        face.paint(p)
        hair(p, hair_c, bangs=[2, 3, 1, 2, 3, 2, 1, 2], back_len=5, side_front=4, highlight=(250, 225, 140))
        cap(p, '#2c5aa8', rows=3, band='#d8b04a')
        t = torso_part(p)
        f = p.face(t, 'front')
        # courier jacket: brass buttons, red neckerchief, a post badge
        vneck(f, '#d03a30', depth=2)
        buttons(f, 3, (3, 5), (230, 190, 80))
        buttons(f, 4, (3, 5), (230, 190, 80))
        f.px(6, 2, (240, 210, 100))
        sleeves_cuff(p, '#d8b04a', row=3)
        hands(p, skin)
        belt(p, 0, '#5a3a20', '#c0a050')
        # shorts, bare knees, socks and boots
        for part in ('right_leg', 'left_leg'):
            rows_color(p, part, 0, 3, '#3a3e5a')
            rows_color(p, part, 4, 5, skin)
        for part in ('right_shin', 'left_shin'):
            rows_color(p, part, 0, 2, '#f0ece0')
        boots(p, '#6a4028', rows=3)
        # letter with a red seal
        lt = p.face('letter', 'front')
        lt.px(1, 0, (200, 40, 40))
    pal = palette(skin=skin, hair=hair_c, tunic='#2c5aa8', sleeve='#2c5aa8', sleeve2='#2c5aa8', belly='#2c5aa8',
                  hat='#24489a', satchel='#8a5a30')
    return look('chico', builds=(SLIM,), show={'hair_cap', 'band', 'brim', 'pigeon', 'satchel', 'letter'},
                poses={'right_arm': dict(rot=(-30, 0, 8)), 'right_forearm': dict(rot=(-46, 0, 0)),
                       'brim': dict(rot=(-6, 0, 0))},
                pal=pal, paint=paint, work='work_pigeon', talk='talk_kid', scale=0.8,
                portrait=dict(bg=('#4a7ad0', '#162a58')))


def marta():
    skin = '#ecc8b0'
    hair_c = '#7a6a64'
    face = Face(skin, eye='#4a5a7a', style='normal', brows='#4a3a38', brow_style='flat', mouth='flat',
                wrinkles=True, lips=(150, 80, 80), brow_dy=-1)

    def paint(p):
        face.paint(p)
        hair(p, hair_c, bangs=[1, 1, 1, 0, 0, 1, 1, 1], back_len=6, side_front=5, part_x=3,
             highlight=(170, 160, 156))
        hair_piece(p, 'bun_top', hair_c)
        round_glasses(p, rim=(170, 172, 186), lens=(235, 242, 250))
        # high white collar, navy dress, gold brooch, white cuffs
        for fc in faces_of(p, 'collar'):
            fill_face(fc, (238, 236, 230))
        cf = p.face('collar', 'front')
        for x in range(3, 7):
            cf.clear(x, 2)
        t = torso_part(p)
        f = p.face(t, 'front')
        f.px(3, 1, (230, 190, 80))
        f.px(4, 1, (200, 160, 60))
        buttons(f, 3, (3, 4, 5, 6), (200, 200, 210))
        sleeves_cuff(p, '#eeece6', row=3)
        hands(p, skin)
        belt(p, 0, '#1a1e30', '#a0a0a8')
        trim_row(p, 'skirt_long', 7, '#141828', index=1)
        sf = p.face('skirt_long', 'front', index=1)
        sf.vline(5, 0, sf.h - 2, shade(col('#2a3050'), 0.82))
        pf = p.face('papers', 'top')
        page_lines(pf, ink=(60, 60, 80), paper=(242, 238, 228))
        pf.px(4, 4, (180, 40, 40))
    pal = palette(skin=skin, hair=hair_c, tunic='#2a3050', sleeve='#2a3050', sleeve2='#2a3050', belly='#2a3050',
                  skirt={'base': '#2a3050', 'style': 'cloth'}, skirt2={'base': '#262c4a', 'style': 'cloth'},
                  tool={'base': '#8a5a30', 'style': 'wood'}, handle='#4a2a18')
    return look('marta', builds=(SLIM, NO_LEGS), show={'hair_cap', 'bun_top', 'glasses', 'collar', 'skirt_long',
                                                       'stamp', 'papers'},
                poses={'right_arm': dict(rot=(-20, 0, 6)), 'right_forearm': dict(rot=(-46, 0, 0)),
                       'left_arm': dict(rot=(-16, 0, -6)), 'left_forearm': dict(rot=(-56, 0, 0))},
                pal=pal, paint=paint, work='work_stamp', talk='talk_scholar', scale=0.96,
                portrait=dict(bg=('#4a5a80', '#161c30')))


def gant():
    skin = '#c8946c'
    face = Face(skin, eye='#3a3a40', style='stern', brows='#3a3028', brow_style='thick', nose='long', mouth='none',
                scar=[(2, 3), (3, 4), (4, 5)], mouth_row=7)

    def paint(p):
        face.paint(p)
        helmet(p, '#8e96a2', left=1, right=1, top=2, rim=(70, 74, 82))
        hf = p.face('shell', 'front')
        hf.vline(4, 2, 4, (120, 126, 136))       # nasal guard
        mustache = '#5a4a3c'
        p.face('head', 'front').hline(1, 6, 6, col(mustache))
        p.face('head', 'front').px(1, 7, col(mustache))
        p.face('head', 'front').px(6, 7, col(mustache))
        # mail sleeves, steel pauldrons, the royal tabard
        for side in ('right', 'left'):
            upper, fore = arm_parts(p, side)
            mail(p, upper, '#7a828e')
            mail(p, fore, '#7a828e', rows=(0, 1, 2))
        hands(p, '#4a3a2c', rows=3, gloves=True)
        tabard(p, ROYAL)
        t = torso_part(p)
        mail(p, t, '#7a828e')
        belt(p, 0, '#3a2a1c', '#c8b060', buckle_w=3)
        legs(p, '#4a4a54')
        boots(p, '#3a2a20', rows=3, top_trim='#7a828e')
    pal = palette(skin=skin, tunic='#7a828e', sleeve='#7a828e', sleeve2='#7a828e', belly='#3a3a44',
                  armor={'base': '#9aa2ae', 'style': 'metal'}, shell={'base': '#8e96a2', 'style': 'metal'},
                  apron=ROYAL, coat=ROYAL, blade={'base': '#c8d0d8', 'style': 'metal'}, pole='#5a3a20', keys='#a09070')
    return look('gant', show={'shell', 'pauldron_r', 'pauldron_l', 'apron', 'apron_bib', 'coat_tails', 'halberd',
                              'keyring'}, broad=True,
                poses={'right_arm': dict(rot=(-8, 0, 4)), 'right_forearm': dict(rot=(-24, 0, 0))},
                pal=pal, paint=paint, idle='idle_still', work='work_guard', talk='talk_gruff', scale=1.06,
                portrait=dict(bg=('#5a6a8a', '#1a2030')))


def zain():
    skin = '#c88a5c'
    hair_c = '#2a2024'
    face = Face(skin, eye='#3a2a20', style='normal', brows='#2a2024', brow_style='raised', mouth='grin', teeth=True,
                mouth_row=7, mouth_w=4)

    def paint(p):
        face.paint(p)
        hair(p, hair_c, bangs=[1, 1, 1, 1, 1, 1, 1, 1], back_len=6, side_front=3, highlight=(80, 80, 96))
        hf = p.face('head', 'front')
        for y in (3, 4, 5):
            hf.px(0, y, col(hair_c))
            hf.px(7, y, col(hair_c))
        mustache_plane(p, hair_c, curl=True)
        # red top hat with a gold band
        for i in range(2):
            for fc in faces_of(p, 'top_hat', index=i):
                if fc.face in ('front', 'back', 'left', 'right') and i == 1:
                    fc.hline(0, fc.w - 1, fc.h - 2, (220, 180, 70))
                    fc.hline(0, fc.w - 1, fc.h - 1, (180, 140, 50))
        # red tailcoat over a white shirt, gold buttons and epaulettes
        t = torso_part(p)
        f = p.face(t, 'front')
        open_front(f, '#f4f0e8', width=2, lapel=(240, 200, 90), rows=f.h - 1)
        f.px(f.w // 2 - 1, 0, (30, 26, 30))
        f.px(f.w // 2, 0, (30, 26, 30))
        buttons(f, f.w // 2 - 2, (3, 5), (240, 205, 90))
        buttons(f, f.w // 2 + 1, (3, 5), (240, 205, 90))
        for side in ('right', 'left'):
            upper, fore = arm_parts(p, side)
            rows_color(p, upper, 0, 0, GOLD, faces=('front', 'back', 'right', 'left'))
            rows_color(p, fore, 4, 4, GOLD)
        hands(p, '#f0ece4', rows=1, gloves=True)
        belt(p, 0, '#1a1418', '#e0c050', buckle_w=3)
        legs(p, '#f0ece2')
        boots(p, '#1e1a1e', rows=4, top_trim='#3a3036')
        trim_row(p, 'coat_tails', 8, GOLD)
    pal = palette(skin=skin, hair=hair_c, beard=hair_c, tunic='#b82a2a', sleeve='#b82a2a', sleeve2='#b82a2a',
                  belly='#b82a2a', coat={'base': '#a82424', 'style': 'cloth'}, hat='#a02424', hat2='#a82a2a',
                  brass='#e0b04a')
    return look('zain', show={'hair_cap', 'mustache', 'top_hat', 'coat_tails', 'trumpet'}, broad=True,
                poses={'right_arm': dict(rot=(-24, 0, 10)), 'right_forearm': dict(rot=(-30, 0, 0)),
                       'left_arm': dict(rot=(-10, 0, -18)), 'left_forearm': dict(rot=(-20, 0, 0)),
                       'top_hat': dict(rot=(0, 0, -6))},
                pal=pal, paint=paint, work='work_trumpet', talk='talk_flourish', scale=1.02,
                portrait=dict(bg=('#c0403a', '#3a0e10'), dy=-3.0))


def chloe():
    skin = '#f4dcca'
    hair_c = '#26284a'
    face = Face(skin, eye='#7a6ab0', style='normal', brows='#26284a', mouth='small', blush=True, brow_dy=-1)

    def paint(p):
        face.paint(p)
        hair(p, hair_c, style='long', bangs=[3, 4, 3, 2, 3, 4, 3, 3], long_len=8, side_front=8,
             highlight=(80, 90, 150))
        hair_piece(p, 'hair_back', hair_c, ragged=True)
        hf = p.face('hair_cap', 'front')
        hf.px(6, 2, (230, 200, 90))                 # gold hair pin
        round_glasses(p, rim=(200, 170, 80), lens=(220, 230, 245))
        # burgundy archive robe: gold border, white collar, a key on a chain
        t = torso_part(p)
        f = p.face(t, 'front')
        vneck(f, '#f2eee4', depth=2)
        trim_col(f, f.w // 2 - 1, GOLD, 2, f.h - 1)
        f.px(f.w // 2, 4, (200, 170, 80))
        for fc in faces_of(p, 'robe_long', sides_only=True):
            fc.hline(0, fc.w - 1, fc.h - 1, col(GOLD))
            fc.hline(0, fc.w - 1, fc.h - 2, shade(col('#6a1e2a'), 0.7))
        rf = p.face('robe_long', 'front')
        trim_col(rf, rf.w // 2, GOLD, 0, rf.h - 1)
        sleeves_cuff(p, GOLD, row=3)
        hands(p, skin)
        fr = p.face(arm_parts(p, 'right')[1], 'front')
        fr.px(1, 5, (40, 40, 70))                    # ink on the fingers
        belt(p, 0, '#3a1018', '#d8b04a')
        tp = p.face('tome_page', 'top')
        page_lines(tp, ink=(60, 50, 60), paper=(240, 232, 214))
        tt = p.face('tome', 'top')
        page_lines(tt, ink=(60, 50, 60), paper=(236, 226, 206))
        tt.vline(tt.w // 2, 0, tt.h - 1, (110, 80, 60))
    pal = palette(skin=skin, hair=hair_c, tunic='#6a1e2a', sleeve='#6a1e2a', sleeve2='#6a1e2a', belly='#6a1e2a',
                  robe={'base': '#6a1e2a', 'style': 'cloth'}, book='#3a2a4a', quill='#f4f0e8')
    return look('chloe', builds=(SLIM, NO_LEGS), show={'hair_cap', 'hair_back', 'glasses', 'robe_long', 'tome',
                                                       'quill'},
                poses={'left_arm': dict(rot=(-20, 0, -4)), 'left_forearm': dict(rot=(-58, 0, 0)),
                       'right_arm': dict(rot=(-10, 0, 4)), 'right_forearm': dict(rot=(-30, 0, 0))},
                pal=pal, paint=paint, work='work_book', talk='talk_scholar', scale=0.96,
                portrait=dict(bg=('#5a3a7a', '#1a1028')))


def cast():
    return [mina(), pim(), goran(), hagen(), luce(), chico(), marta(), gant(), zain(), chloe()]
