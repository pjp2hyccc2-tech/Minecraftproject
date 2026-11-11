"""The townsfolk, guards and clergy of the capital (placed at the crowd.* and guard.* spots, see
com.eternalrealm.npc.Townsfolk), the dungeon warden and the default 'citizen'."""
from erpaint import lighten, mix, shade
from npc_cast import (BROAD, GOLD, NO_LEGS, ROYAL, ROYAL_DARK, SLIM, WHITE, apron_stains, blush_dots, crest, look, mail,
                      palette, pants_and_boots, pendant, plate, sleeves_cuff, stripes, tabard)
from npc_cast_town import cap, helmet, mustache_plane
from npc_paint import (CROSS, Face, arm_parts, bald_head, bare_arms, belt, boots, buttons, clear_part, col, faces_of,
                       page_lines,
                       fill_face, hair, hair_piece, hands, legs, mask_alpha, open_front, page_lines, rows_color, sash,
                       shell_face_opening, torso_part, trim_col, trim_row, vneck)


def citizen():
    skin = '#d9a77f'
    face = Face(skin, eye='#4a3020', brows='#4a3020', mouth='smile')

    def paint(p):
        face.paint(p)
        hair(p, '#6a4428', bangs=[2, 2, 1, 2, 2, 2, 1, 1], back_len=6)
        t = torso_part(p)
        f = p.face(t, 'front')
        vneck(f, '#e8dcc0', depth=2)
        belt(p, 0, '#4a3020', '#c0a050')
        hands(p, skin)
        sleeves_cuff(p, '#e8dcc0')
        pants_and_boots(p, '#5a4a3a', '#3b2a20')
    return look('citizen', show={'hair_cap', 'pouch'}, pal=palette(skin=skin, tunic='#5a7a4a', sleeve='#5a7a4a',
                                                                     sleeve2='#5a7a4a', belly='#4a5a3a'),
                paint=paint, portrait=dict(dy=0))




# --------------------------------------------------------------------------------------------- shared painters

def headscarf(p, c, dots=None, rows=4, part='band'):
    """A kerchief over the hair: crown, the upper rows and a knot at the back."""
    c = col(c)
    for fc in faces_of(p, part):
        if fc.face == 'bottom':
            mask_alpha(fc, lambda x, y: False)
            continue
        if fc.face == 'top':
            fill_face(fc, c, shade_rows=False)
        else:
            depth = rows if fc.face != 'front' else 2
            for y in range(fc.h):
                for x in range(fc.w):
                    if y < depth or (fc.face == 'back' and y < rows + 2 and 2 <= x <= 5):
                        fc.px(x, y, shade(c, 1.04 - 0.06 * y))
                    else:
                        fc.clear(x, y)
        if dots is not None:
            for y in range(fc.h):
                for x in range(fc.w):
                    if (x + 2 * y) % 4 == 0 and fc.img[fc.v + y, fc.u + x, 3] > 0:
                        fc.px(x, y, col(dots))


def bodice(p, c, blouse='#f2eee4', lace=GOLD):
    t = torso_part(p)
    f = p.face(t, 'front')
    for y in range(f.h):
        for x in range(1, f.w - 1):
            f.px(x, y, shade(col(c), 1.04 - 0.04 * y))
    vneck(f, blouse, depth=2)
    for y in range(3, f.h, 2):
        f.px(f.w // 2 - 1, y, col(lace))
        f.px(f.w // 2, y, col(lace))


def gown_trim(p, c, panel=None):
    for i in range(2):
        for fc in faces_of(p, 'skirt_long', index=i, sides_only=True):
            fc.hline(0, fc.w - 1, fc.h - 1, col(c))
        if panel is not None:
            sf = p.face('skirt_long', 'front', index=i)
            cx = sf.w // 2
            for y in range(sf.h):
                for x in range(cx - 1 - i, cx + 1 + i):
                    sf.px(x, y, shade(col(panel), 1.02 - 0.03 * y))


def basket_fill(p, items):
    bt = p.face('basket', 'top')
    pts = ((1, 1), (3, 1), (2, 2), (1, 3), (3, 3), (2, 0), (0, 2), (4, 2))
    for k, (x, y) in enumerate(pts):
        bt.px(x, y, col(items[k % len(items)]))


def skirt_plain(p, hem, index=1):
    trim_row(p, 'skirt_long', 7, hem, index=index)


def face_of(v, **kw):
    args = dict(eye=v.get('eye', '#3a2a20'), style=v.get('eye_style', 'normal'), brows=v.get('brows', v.get('hair')),
                mouth=v.get('mouth', 'smile'))
    args.update(kw)
    return Face(v['skin'], **args)


SKINS = ('#f2cfb4', '#e3b08e', '#c88a62', '#9c6644', '#f6dcc8')


# ------------------------------------------------------------------------------------------------- townsfolk

def merchant(vid, v):
    face = face_of(v, mouth='smile')

    def paint(p):
        face.paint(p)
        if v.get('woman'):
            hair(p, v['hair'], bangs=[2, 2, 1, 1, 1, 1, 2, 2], back_len=7, side_front=6)
            hair_piece(p, 'bun_back', v['hair'])
            headscarf(p, v['scarf'], dots=v.get('dots'), rows=3)
            bodice(p, v['coat'], lace=v.get('lace', GOLD))
            sleeves_cuff(p, '#f2eee4', row=3)
            skirt_plain(p, shade(col(v['skirt']), 0.7))
            basket_fill(p, ((200, 40, 40), (230, 70, 50), (120, 170, 60)))
        else:
            hair(p, v['hair'], bangs=[1, 1, 1, 1, 1, 1, 1, 1], back_len=5, side_front=4)
            cap(p, v['hat'], rows=3, band=v.get('band', '#3a2a1c'))
            mustache_plane(p, v['hair'], curl=False)
            t = torso_part(p)
            f = p.face(t, 'front')
            open_front(f, '#efe6d0', width=2, lapel=shade(col(v['coat']), 0.8), rows=f.h - 1)
            buttons(f, 2, (2, 4, 6), (220, 190, 90))
            sleeves_cuff(p, shade(col(v['coat']), 0.8), row=4)
            pants_and_boots(p, v.get('pants', '#4a3a2c'), '#3a2818', rows=2)
            bp = p.face('backpack', 'back')
            bp.hline(0, bp.w - 1, 2, (90, 60, 34))
            bp.px(bp.w // 2, 3, (200, 170, 80))
        hands(p, v['skin'])
        belt(p, 0, '#3a2414', '#c8a050')
    woman = v.get('woman')
    pal = palette(skin=v['skin'], hair=v['hair'], beard=v['hair'], tunic=v['coat'], sleeve=v['coat'],
                  sleeve2=v['coat'], belly=v['coat'] if not woman else v['skirt'], hat=v.get('hat', '#5a3a24'),
                  skirt={'base': v.get('skirt', '#5a4030'), 'style': 'cloth'},
                  skirt2={'base': v.get('skirt', '#5a4030'), 'style': 'cloth'}, pouch='#7a4a28')
    show = {'hair_cap', 'band'} | ({'bun_back', 'skirt_long', 'basket'} if woman else
                                   {'brim', 'mustache', 'backpack', 'coin_pouch'})
    return look(vid, builds=(SLIM, NO_LEGS) if woman else (), show=show,
                poses={'left_arm': dict(rot=(-4, 0, -6))}, pal=pal, paint=paint, talk='talk_flourish',
                scale=v.get('scale', 1.0), portrait=dict(bg=('#a8844a', '#33240f')))


def housewife(vid, v):
    face = face_of(v, mouth='smile', blush=True)

    def paint(p):
        face.paint(p)
        hair(p, v['hair'], bangs=[2, 2, 1, 1, 1, 1, 2, 2], back_len=7, side_front=6)
        if v.get('scarf'):
            headscarf(p, v['scarf'], dots=v.get('dots'), rows=3)
        if v.get('bun'):
            hair_piece(p, 'bun_top', v['hair'])
        bodice(p, v['dress'], lace=shade(col(v['dress']), 0.7))
        sleeves_cuff(p, '#f2eee4', row=3)
        hands(p, v['skin'])
        apron_stains(p, 'apron', (200, 190, 170), ((2, 3), (5, 7)))
        belt(p, 0, shade(col(v['dress']), 0.7), None)
        skirt_plain(p, shade(col(v['dress']), 0.7))
        basket_fill(p, v.get('basket', ((220, 170, 90), (200, 150, 70), (240, 210, 140))))
    pal = palette(skin=v['skin'], hair=v['hair'], tunic=v['dress'], sleeve=v['dress'], sleeve2=v['dress'],
                  belly=v['dress'], skirt={'base': v['dress'], 'style': 'cloth'},
                  skirt2={'base': v['dress'], 'style': 'cloth'}, apron={'base': '#f2efe6', 'style': 'cloth'})
    show = {'hair_cap', 'skirt_long', 'apron', 'basket'} | ({'band'} if v.get('scarf') else set()) | \
        ({'bun_top'} if v.get('bun') else set())
    return look(vid, builds=(SLIM, NO_LEGS), show=show, poses={'left_arm': dict(rot=(-4, 0, -4))}, pal=pal,
                paint=paint, talk='talk_bow', scale=0.95, portrait=dict(bg=('#b0806a', '#3a2018')))


def farmer(vid, v):
    face = face_of(v, mouth='grin' if not v.get('woman') else 'smile', freckles=v.get('freckles', False),
                   stubble=v.get('stubble'))

    def paint(p):
        face.paint(p)
        hair(p, v['hair'], bangs=[1, 2, 1, 1, 2, 1, 1, 2], back_len=6 if not v.get('woman') else 7,
             side_front=4 if not v.get('woman') else 7)
        sh = p.face('straw_hat', 'top', index=1)
        for fc in faces_of(p, 'straw_hat', index=1, sides_only=True):
            fc.hline(0, fc.w - 1, fc.h - 1, col(v.get('hatband', '#a03a2a')))
        if v.get('woman'):
            bodice(p, v['dress'], lace=shade(col(v['dress']), 0.7))
            sleeves_cuff(p, '#f2eee4', row=3)
            apron_stains(p, 'apron', (130, 110, 80), ((2, 3), (5, 7), (3, 8)))
            basket_fill(p, ((230, 130, 40), (240, 200, 60), (120, 180, 60), (200, 60, 40)))
            belt(p, 0, '#5a3a20', None)
            trim_row(p, 'skirt_short', 6, shade(col(v['dress']), 0.7))
            legs(p, v['skin'])
            boots(p, '#5a3a22', rows=2)
        else:
            # denim overalls over a shirt
            t = torso_part(p)
            f = p.face(t, 'front')
            for y in range(2, f.h):
                for x in range(1, f.w - 1):
                    f.px(x, y, shade(col(v['overalls']), 1.03 - 0.04 * y))
            f.vline(1, 0, 1, col(v['overalls']))
            f.vline(f.w - 2, 0, 1, col(v['overalls']))
            f.px(2, 2, (220, 200, 120))
            f.px(f.w - 3, 2, (220, 200, 120))
            for fc in faces_of(p, 'hips', sides_only=True):
                fill_face(fc, v['overalls'])
            legs(p, v['overalls'])
            boots(p, '#4a3020', rows=2)
            for part in ('right_leg', 'left_leg'):
                fc = p.face(part, 'front')
                fc.px(1, 3, shade(col(v['overalls']), 0.75))
            sleeves_cuff(p, v['skin'], row=4)
        hands(p, v['skin'])
    woman = v.get('woman')
    pal = palette(skin=v['skin'], hair=v['hair'], tunic=v.get('shirt', '#d8cfb8'), sleeve=v.get('shirt', '#d8cfb8'),
                  sleeve2=v.get('shirt', '#d8cfb8'), belly=v.get('overalls', v.get('dress', '#3a5a8a')),
                  skirt={'base': v.get('dress', '#5a7a3a'), 'style': 'cloth'},
                  apron={'base': '#e8dcc4', 'style': 'cloth'}, pole='#7a5a34')
    if woman:
        pal.update(tunic=v['dress'], sleeve=v['dress'], sleeve2=v['dress'], belly=v['dress'])
    show = {'hair_cap', 'straw_hat'} | ({'skirt_short', 'apron', 'basket'} if woman else {'pitchfork'})
    return look(vid, builds=(SLIM,) if woman else (), show=show,
                poses={'right_arm': dict(rot=(-8, 0, 4)), 'right_forearm': dict(rot=(-20, 0, 0))} if not woman else
                {'left_arm': dict(rot=(-4, 0, -4))},
                pal=pal, paint=paint, talk='talk_hearty' if not woman else 'talk_bow', scale=v.get('scale', 1.0),
                portrait=dict(bg=('#8aa850', '#24300f'), dy=-1.0))


def sailor(vid, v):
    face = face_of(v, mouth='smirk', stubble=v.get('stubble'))

    def paint(p):
        face.paint(p)
        hair(p, v['hair'], bangs=[2, 1, 2, 2, 1, 2, 1, 2], back_len=6, side_front=4)
        if v.get('bandana'):
            for fc in faces_of(p, 'band'):
                if fc.face == 'bottom':
                    mask_alpha(fc, lambda x, y: False)
                    continue
                if fc.face == 'top':
                    fill_face(fc, v['bandana'], shade_rows=False)
                    continue
                for y in range(fc.h):
                    for x in range(fc.w):
                        if y < 2:
                            fc.px(x, y, shade(col(v['bandana']), 1.05 - 0.1 * y))
                        else:
                            fc.clear(x, y)
            for fc in faces_of(p, 'headband_tails'):
                fill_face(fc, v['bandana'])
        else:
            cap(p, v.get('cap', '#1e2a4a'), rows=2, band='#f0ece0')
        t = torso_part(p)
        stripes(p, t, '#f0ece2', v['stripe'], period=2)
        for side in ('right', 'left'):
            upper, fore = arm_parts(p, side)
            stripes(p, upper, '#f0ece2', v['stripe'], period=2)
            rows_color(p, fore, 0, 5, v['skin'])
        f = p.face(t, 'front')
        f.px(f.w // 2 - 1, 0, (40, 40, 60))
        f.px(f.w // 2, 0, (40, 40, 60))
        hands(p, v['skin'])
        belt(p, 0, '#3a2a1c', '#a8a8b0')
        legs(p, v.get('pants', '#2a3450'))
        for part in ('right_shin', 'left_shin'):
            rows_color(p, part, 1, 3, v['skin'])
        boots(p, '#3a2a1c', rows=2)
    show = {'hair_cap', 'band'} | ({'headband_tails'} if v.get('bandana') else set()) | set(v.get('extra', ()))
    pal = palette(skin=v['skin'], hair=v['hair'], tunic='#f0ece2', sleeve='#f0ece2', sleeve2=v['skin'],
                  belly=v.get('pants', '#2a3450'), sack={'base': '#c8b490', 'style': 'cloth'})
    return look(vid, show=show, poses={'left_arm': dict(rot=(-150, 0, -10)), 'left_forearm': dict(rot=(-20, 0, 0)),
                                       'headband_tails': dict(rot=(16, 0, 0))} if 'sack' in v.get('extra', ()) else
                {'headband_tails': dict(rot=(16, 0, 0))},
                pal=pal, paint=paint, work='work_pipe' if 'pipe' in v.get('extra', ()) else None, talk='talk_hearty',
                scale=v.get('scale', 1.0), portrait=dict(bg=('#4a8ac8', '#10243a')))


def noble_lady(vid, v):
    face = face_of(v, style='tall', mouth='smile', lips=v.get('lips', (196, 120, 126)), blush=True,
                   beauty_mark=v.get('mark', False))

    def paint(p):
        face.paint(p)
        hair(p, v['hair'], bangs=[2, 3, 2, 1, 1, 2, 3, 2], back_len=6, side_front=6, highlight=v.get('hl'))
        hair_piece(p, 'bun_top', v['hair'])
        bt = p.face('bun_top', 'front')
        bt.px(1, 1, (240, 240, 250))
        bt.px(2, 1, (220, 200, 240))
        bodice(p, v['gown'], blouse=v.get('lace_c', '#f6f2f8'), lace=GOLD)
        t = torso_part(p)
        f = p.face(t, 'front')
        for x in range(2, f.w - 2):                   # pearl necklace
            if x % 2 == 0:
                f.px(x, 1, (250, 248, 240))
        for side in ('right', 'left'):
            _, fore = arm_parts(p, side)
            rows_color(p, fore, 3, 4, '#f6f2f8')
        hands(p, v['skin'])
        gown_trim(p, GOLD, panel=v.get('panel'))
        ff = p.face('fan', 'top')
        for y in range(ff.h):
            for x in range(ff.w):
                if ((x - 2) ** 2 + (y - 4.5) ** 2) ** 0.5 > 4.6 or y > 4:
                    ff.clear(x, y)
                elif x % 2 == 0:
                    ff.px(x, y, shade(ff.get(x, y), 0.85))
    pal = palette(skin=v['skin'], hair=v['hair'], tunic=v['gown'], sleeve=v['gown'], sleeve2=v['gown'],
                  belly=v['gown'], skirt={'base': v['gown'], 'style': 'cloth'},
                  skirt2={'base': v['gown'], 'style': 'cloth'}, fan=v.get('fan', '#f0d0e0'))
    return look(vid, builds=(SLIM, NO_LEGS), show={'hair_cap', 'bun_top', 'skirt_long', 'fan'},
                poses={'left_arm': dict(rot=(-34, 0, -4)), 'left_forearm': dict(rot=(-70, 0, 0)),
                       'right_arm': dict(rot=(-6, 0, 4))},
                pal=pal, paint=paint, work='work_fan', talk='talk_bow', scale=0.95,
                portrait=dict(bg=v.get('bg', ('#a070b0', '#2a1438'))))


def noble_gent(vid, v):
    face = face_of(v, mouth='line', brow_style='raised')

    def paint(p):
        face.paint(p)
        hair(p, v['hair'], bangs=[1, 1, 1, 1, 1, 1, 1, 1], back_len=6, side_front=4)
        mustache_plane(p, v['hair'], curl=True)
        if v.get('monocle'):
            gf = p.face('head', 'front')
            gf.px(5, 3, (220, 190, 90))
            gf.px(4, 4, (220, 190, 90))
            gf.px(6, 5, (220, 190, 90))
        for fc in faces_of(p, 'top_hat', index=1, sides_only=True):
            fc.hline(0, fc.w - 1, fc.h - 2, col(v.get('hatband', '#7a1e2a')))
        t = torso_part(p)
        f = p.face(t, 'front')
        open_front(f, '#f4f2ec', width=2, lapel=shade(col(v['coat']), 0.75), rows=f.h - 1)
        f.px(f.w // 2 - 1, 1, col(v.get('cravat', '#f8f8f8')))
        f.px(f.w // 2, 1, col(v.get('cravat', '#f8f8f8')))
        f.px(f.w // 2 - 1, 2, shade(col(v.get('cravat', '#f8f8f8')), 0.9))
        buttons(f, f.w // 2 + 1, (3, 5), (220, 190, 90))
        sleeves_cuff(p, '#f4f2ec', row=5)
        hands(p, '#f0ece4', rows=1, gloves=True)
        belt(p, 0, '#1a1418', None)
        legs(p, v.get('pants', '#3a3438'))
        boots(p, '#1a1618', rows=2)
    pal = palette(skin=v['skin'], hair=v['hair'], beard=v['hair'], tunic=v['coat'], sleeve=v['coat'],
                  sleeve2=v['coat'], belly=v['coat'], coat={'base': v['coat'], 'style': 'cloth'},
                  hat=v.get('hat', '#1e1a20'), hat2=v.get('hat', '#1e1a20'), cane='#3a2418')
    return look(vid, show={'hair_cap', 'mustache', 'top_hat', 'coat_tails', 'cane'},
                poses={'right_arm': dict(rot=(-8, 0, 6)), 'right_forearm': dict(rot=(-10, 0, 0)),
                       'top_hat': dict(rot=(0, 0, 0), scale=(0.92, 0.9, 0.92))},
                pal=pal, paint=paint, talk='talk_flourish', scale=1.0, portrait=dict(bg=('#5a5a7a', '#16161e'), dy=-2.0))


def elder(vid, v):
    face = face_of(v, style='narrow', mouth='none' if not v.get('woman') else 'smile', wrinkles=True,
                   brows='#e8e4dc', brow_style='thick')

    def paint(p):
        face.paint(p)
        if v.get('woman'):
            hair(p, '#e4e0da', bangs=[2, 2, 1, 1, 1, 1, 2, 2], back_len=7, side_front=6, highlight=(255, 255, 255))
            hair_piece(p, 'bun_back', '#e4e0da')
            for fc in faces_of(p, 'collar'):
                fill_face(fc, v['shawl'])
                for y in range(fc.h):
                    for x in range(fc.w):
                        if (x + y) % 3 == 0:
                            fc.px(x, y, shade(col(v['shawl']), 0.82))
            skirt_plain(p, shade(col(v['dress']), 0.7))
        else:
            bald_head(p, v['skin'], fringe='#e4e0da', fringe_rows=(4, 6))
            for i in range(len(p.rig.parts['beard'].cubes)):
                hair_piece(p, 'beard', '#e8e4dc', index=i)
            bf = p.face('beard', 'front')
            bf.clear(3, 0)
            bf.clear(4, 0)
            bf.hline(3, 4, 1, (120, 90, 80))
            pants_and_boots(p, '#4a4038', '#3a2a1c', rows=2)
            vneck(p.face(torso_part(p), 'front'), '#d8cdb4', depth=2)
        hands(p, v['skin'])
        belt(p, 0, '#3a2a1c', None)
    woman = v.get('woman')
    pal = palette(skin=v['skin'], hair='#e4e0da', beard='#e8e4dc', tunic=v['dress'], sleeve=v['dress'],
                  sleeve2=v['dress'], belly=v['dress'], skirt={'base': v['dress'], 'style': 'cloth'},
                  skirt2={'base': v['dress'], 'style': 'cloth'}, collar=v.get('shawl', '#8a7a6a'))
    show = {'cane'} | ({'hair_cap', 'bun_back', 'collar', 'skirt_long'} if woman else {'beard'})
    return look(vid, builds=(SLIM, NO_LEGS) if woman else (), show=show,
                poses={'chest': dict(rot=(10, 0, 0)), 'head': dict(rot=(-8, 0, 0)), 'right_arm': dict(rot=(-14, 0, 4)),
                       'right_forearm': dict(rot=(-8, 0, 0))},
                pal=pal, paint=paint, idle='idle_still', work='work_pipe' if not woman else None, talk='talk_gruff',
                scale=0.93, portrait=dict(bg=('#8a7a6a', '#24201a')))


def apprentice(vid, v):
    face = face_of(v, style='round', mouth='grin', freckles=v.get('freckles', True))

    def paint(p):
        face.paint(p)
        hair(p, v['hair'], bangs=[2, 3, 2, 3, 2, 3, 2, 3], back_len=5, side_front=4)
        if v.get('woman'):
            headscarf(p, v['scarf'], rows=2)
            basket_fill(p, ((210, 160, 80), (190, 130, 60), (230, 190, 110)))
        else:
            cap(p, v.get('cap', '#5a4a3a'), rows=2, band='#3a2a1c')
        t = torso_part(p)
        f = p.face(t, 'front')
        vneck(f, '#efe6d0', depth=2)
        sleeves_cuff(p, v['skin'], row=4)
        hands(p, v['skin'])
        apron_stains(p, 'apron', (240, 240, 236) if v.get('woman') else (60, 50, 40), ((1, 2), (4, 5), (6, 8)))
        belt(p, 0, '#3a2a1c', '#a0a0a0')
        pants_and_boots(p, v.get('pants', '#4a4038'), '#3a2818', rows=2)
    woman = v.get('woman')
    pal = palette(skin=v['skin'], hair=v['hair'], tunic=v['shirt'], sleeve=v['shirt'], sleeve2=v['shirt'],
                  belly=v.get('pants', '#4a4038'),
                  apron={'base': v.get('apron', '#7a5232'), 'style': 'cloth' if woman else 'leather'},
                  sack={'base': '#c8b490', 'style': 'cloth'})
    show = {'hair_cap', 'band', 'apron'} | ({'basket'} if woman else {'sack'})
    poses = {'left_arm': dict(rot=(-4, 0, -4))} if woman else {'left_arm': dict(rot=(-150, 0, -10)),
                                                               'left_forearm': dict(rot=(-20, 0, 0))}
    return look(vid, builds=(SLIM,) if woman else (), show=show, poses=poses, pal=pal, paint=paint, talk='talk_kid',
                scale=0.92, portrait=dict(bg=('#b07a4a', '#331e0c')))


def maid(vid, v):
    face = face_of(v, style='tall', mouth='smile', blush=True)

    def paint(p):
        face.paint(p)
        hair(p, v['hair'], bangs=[3, 2, 3, 2, 2, 3, 2, 3], back_len=7, side_front=6)
        hair_piece(p, 'bun_back', v['hair'])
        # white frilled headdress
        for fc in faces_of(p, 'band'):
            if fc.face in ('top', 'bottom'):
                mask_alpha(fc, lambda x, y: False)
                continue
            for y in range(fc.h):
                for x in range(fc.w):
                    if y == 0 and fc.face == 'front':
                        fc.px(x, y, (250, 250, 250) if x % 2 else (226, 226, 232))
                    elif y == 1 and fc.face == 'front':
                        fc.px(x, y, (236, 236, 240))
                    else:
                        fc.clear(x, y)
        bodice(p, v['dress'], blouse='#f8f8fa', lace=v['dress'])
        t = torso_part(p)
        f = p.face(t, 'front')
        f.px(f.w // 2 - 1, 1, (200, 40, 50))
        f.px(f.w // 2, 1, (200, 40, 50))
        for side in ('right', 'left'):
            upper, fore = arm_parts(p, side)
            rows_color(p, upper, 0, 1, '#f4f4f6')
            rows_color(p, fore, 4, 4, '#f4f4f6')
        hands(p, v['skin'])
        for fc in faces_of(p, 'apron', sides_only=True):
            for x in range(0, fc.w, 2):
                fc.px(x, fc.h - 1, (226, 226, 232))
        belt(p, 0, '#f4f4f6', None)
        skirt_plain(p, '#f4f4f6')
    pal = palette(skin=v['skin'], hair=v['hair'], tunic=v['dress'], sleeve=v['dress'], sleeve2=v['dress'],
                  belly=v['dress'], skirt={'base': v['dress'], 'style': 'cloth'},
                  skirt2={'base': v['dress'], 'style': 'cloth'}, apron={'base': '#f6f6f8', 'style': 'cloth'},
                  apron_bib='#f6f6f8', pole='#6a4a2a')
    return look(vid, builds=(SLIM, NO_LEGS), show={'hair_cap', 'bun_back', 'band', 'skirt_long', 'apron', 'apron_bib',
                                                   'broom'},
                poses={'right_arm': dict(rot=(-14, 0, 6)), 'right_forearm': dict(rot=(-16, 0, 0))},
                pal=pal, paint=paint, work='work_sweep', talk='talk_bow', scale=0.94,
                portrait=dict(bg=('#6a6a8a', '#1a1a2a')))


def innkeeper(vid, v):
    """Rosetta, the landlady of the Starlight Manor (星灯りの館, the inn rooms): a wine-red dress with gold lacing, a
    saffron shawl over the shoulders, a cream apron with a stitched star, the brass key ring of the rooms at her belt,
    a silver streak in her bun, and the guest book and quill in her hands."""
    face = face_of(v, style='tall', mouth='smile', blush=True)

    def paint(p):
        face.paint(p)
        hair(p, v['hair'], bangs=[2, 3, 2, 1, 1, 2, 3, 2], back_len=6, side_front=6)
        hair_piece(p, 'bun_top', v['hair'])
        for fc in faces_of(p, 'hair_cap'):
            if fc.face == 'left':
                for y in range(min(5, fc.h)):
                    fc.px(min(fc.w - 2, 5), y, (206, 200, 210))
        for fc in faces_of(p, 'bun_top'):
            if fc.face in ('front', 'left'):
                fc.px(1, 1, (206, 200, 210))
        bodice(p, v['dress'], blouse='#f4ece0', lace=GOLD)
        sleeves_cuff(p, '#f4ece0', row=3)
        hands(p, v['skin'])
        af = p.face('apron', 'front')
        cx, cy = af.w // 2, af.h - 3
        for (dx, dy) in ((0, -1), (-1, 0), (0, 0), (1, 0), (0, 1)):
            if 0 <= cx + dx < af.w and 0 <= cy + dy < af.h:
                af.px(cx + dx, cy + dy, (216, 176, 74))
        for fc in faces_of(p, 'apron', sides_only=True):
            fc.hline(0, fc.w - 1, 0, (226, 214, 190))
        belt(p, 0, '#4a2a1c', GOLD)
        skirt_plain(p, shade(col(v['dress']), 0.62))
        for fc in faces_of(p, 'scarf', sides_only=True):
            for x in range(0, fc.w, 2):
                fc.px(x, fc.h - 1, shade(col(v['shawl']), 0.78))
        lf = p.face('ledger', 'top')
        page_lines(lf, paper=(242, 234, 214))
        lf.vline(0, 0, lf.h - 1, (120, 40, 44))
    pal = palette(skin=v['skin'], hair=v['hair'], tunic=v['dress'], sleeve=v['dress'], sleeve2=v['dress'],
                  belly=v['dress'], skirt={'base': v['dress'], 'style': 'cloth'},
                  skirt2={'base': v['dress'], 'style': 'cloth'}, apron={'base': '#f2ebdc', 'style': 'cloth'},
                  scarf=v['shawl'], keys='#d8b04a', book='#7a2a30', quill='#f4f0e8')
    return look(vid, builds=(SLIM, NO_LEGS), show={'hair_cap', 'bun_top', 'skirt_long', 'apron', 'scarf', 'keyring',
                                                   'ledger', 'quill'},
                poses={'left_arm': dict(rot=(-18, 0, -6)), 'left_forearm': dict(rot=(-62, 0, 0)),
                       'right_arm': dict(rot=(-22, -10, -12)), 'right_forearm': dict(rot=(-58, 0, 0))},
                pal=pal, paint=paint, work='work_write', talk='talk_bow', scale=0.96,
                portrait=dict(bg=('#c8904a', '#3a1e14')))


def bard(vid, v):
    face = face_of(v, style='tall' if v.get('woman') else 'normal', mouth='grin', blush=v.get('woman', False))

    def paint(p):
        face.paint(p)
        hair(p, v['hair'], style='long' if v.get('woman') else 'short', bangs=[2, 3, 2, 1, 1, 2, 3, 2], back_len=7,
             side_front=6, long_len=7)
        if v.get('woman'):
            hair_piece(p, 'hair_back', v['hair'], ragged=True)
        t = torso_part(p)
        f = p.face(t, 'front')
        # split doublet
        for y in range(f.h):
            for x in range(f.w // 2, f.w):
                f.px(x, y, shade(col(v['c2']), 1.03 - 0.04 * y))
        f.vline(f.w // 2 - 1, 0, f.h - 1, col(GOLD))
        for side, c in (('right', v['c1']), ('left', v['c2'])):
            upper, fore = arm_parts(p, side)
            rows_color(p, upper, 0, 5, c)
            rows_color(p, fore, 0, 5, v['c2'] if side == 'right' else v['c1'])
        hands(p, v['skin'])
        lf = p.face('lute', 'front', index=0)
        lf.px(2, 2, (40, 24, 16))
        lf.px(2, 1, (60, 40, 24))
        belt(p, 0, '#3a2414', '#d8b04a')
        for part, c in (('right_leg', v['c2']), ('left_leg', v['c1']), ('right_shin', v['c2']),
                        ('left_shin', v['c1'])):
            rows_color(p, part, 0, 5, c)
        boots(p, '#4a2c1c', rows=2)
    pal = palette(skin=v['skin'], hair=v['hair'], tunic=v['c1'], sleeve=v['c1'], sleeve2=v['c1'], belly=v['c1'],
                  hat=v.get('hat', '#2a4a2a'), quill=v.get('feather', '#e8e0d0'))
    show = {'hair_cap', 'beret', 'lute'} | ({'hair_back'} if v.get('woman') else set())
    return look(vid, builds=(SLIM,) if v.get('woman') else (), show=show,
                poses={'right_arm': dict(rot=(-30, 20, 10)), 'right_forearm': dict(rot=(-30, 0, 0)),
                       'left_arm': dict(rot=(-40, -20, -20)), 'left_forearm': dict(rot=(-60, 0, 0))},
                pal=pal, paint=paint, work='work_lute', talk='talk_flourish', scale=0.97,
                portrait=dict(bg=('#b05a8a', '#301428')))


def child(vid, v):
    face = face_of(v, style='round', mouth='grin', freckles=v.get('freckles', False), blush=True)

    def paint(p):
        face.paint(p)
        if v.get('girl'):
            hair(p, v['hair'], bangs=[3, 3, 2, 3, 3, 2, 3, 3], back_len=6, side_front=6)
            for part in ('braid_r', 'braid_l'):
                hair_piece(p, part, v['hair'], braid=True)
            wt = p.face('wreath', 'top')
            for (x, y) in ((1, 1), (4, 0), (7, 2), (2, 7), (6, 6), (0, 4)):
                wt.px(x, y, (250, 200, 220) if (x + y) % 2 else (250, 240, 150))
            trim_row(p, 'skirt_short', 6, '#f4f0e8')
            basket_fill(p, ((250, 200, 220), (250, 240, 150), (220, 120, 180), (240, 240, 250)))
            legs(p, v['skin'])
            boots(p, '#7a4a2a', rows=2)
        else:
            hair(p, v['hair'], bangs=[2, 3, 1, 3, 2, 3, 1, 2], back_len=5, side_front=4)
            for part in ('right_leg', 'left_leg'):
                rows_color(p, part, 0, 3, v.get('pants', '#4a5a3a'))
                rows_color(p, part, 4, 5, v['skin'])
            for part in ('right_shin', 'left_shin'):
                rows_color(p, part, 0, 2, '#efe8dc')
            boots(p, '#5a3a22', rows=3)
        t = torso_part(p)
        vneck(p.face(t, 'front'), '#f2eee4', depth=2)
        hands(p, v['skin'])
        belt(p, 0, '#5a3a20', None)
    girl = v.get('girl')
    pal = palette(skin=v['skin'], hair=v['hair'], tunic=v['shirt'], sleeve=v['shirt'], sleeve2=v['shirt'],
                  belly=v.get('pants', v['shirt']), skirt={'base': v.get('skirt', v['shirt']), 'style': 'cloth'},
                  blade={'base': '#a8804a', 'style': 'wood'}, guard='#7a5a3a', grip='#5a3a24',
                  wreath={'base': '#5a9a40', 'style': 'leaf', 'flowers': (250, 200, 220)})
    show = {'hair_cap'} | ({'braid_r', 'braid_l', 'wreath', 'skirt_short', 'basket'} if girl else {'practice_sword'})
    poses = {'right_arm': dict(rot=(-40, 0, 6)), 'right_forearm': dict(rot=(-20, 0, 0))} if not girl else \
        {'left_arm': dict(rot=(-4, 0, -4)), 'braid_r': dict(scale=(1.0, 0.7, 1.0)), 'braid_l': dict(scale=(1.0, 0.7, 1.0))}
    return look(vid, builds=(SLIM,), show=show, poses=poses, pal=pal, paint=paint, talk='talk_kid',
                scale=v.get('scale', 0.7), portrait=dict(bg=('#d0a050', '#3a2a10')))


def adventurer(vid, v):
    face = face_of(v, style='tall' if v.get('woman') else 'normal', mouth='smirk' if not v.get('mage') else 'smile',
                   brow_style='angry' if not v.get('mage') else 'flat')

    def paint(p):
        face.paint(p)
        if v.get('mage'):
            hair(p, v['hair'], style='long', bangs=[2, 3, 3, 2, 2, 3, 3, 2], long_len=7, side_front=7)
            hair_piece(p, 'hair_back', v['hair'], ragged=True)
            for fc in faces_of(p, 'wizard_hat', index=1, sides_only=True):
                fc.hline(0, fc.w - 1, fc.h - 1, col(GOLD))
            t = torso_part(p)
            f = p.face(t, 'front')
            vneck(f, '#e8e0f0', depth=3)
            for fc in faces_of(p, 'robe_long', sides_only=True):
                fc.hline(0, fc.w - 1, fc.h - 1, col(GOLD))
                for (x, y) in ((2, 4), (6, 9), (3, 12)):
                    if x < fc.w and y < fc.h:
                        fc.px(x, y, (230, 210, 120))
            tp = p.face('tome_page', 'top')
            page_lines(tp, ink=(80, 60, 120), paper=(240, 232, 214))
            sleeves_cuff(p, GOLD, row=3)
        else:
            hair(p, v['hair'], bangs=[2, 3, 2, 2, 3, 2, 2, 3], back_len=6, side_front=5)
            for fc in faces_of(p, 'scarf') + faces_of(p, 'scarf_tail'):
                pass
            t = torso_part(p)
            f = p.face(t, 'front')
            for y in range(f.h):
                for x in range(f.w):
                    if (x + y) % 4 == 0:
                        f.px(x, y, shade(f.get(x, y), 0.85))
            f.vline(2, 0, f.h - 1, (90, 60, 34))
            pants_and_boots(p, '#3a3430', '#4a3020', rows=4, trim='#6a4830')
            p.face('shield_back', 'back').rect(3, 3, 5, 5, (200, 170, 80))
        hands(p, v['skin'] if v.get('mage') else '#5a3a24', rows=2, gloves=not v.get('mage'))
        belt(p, 0, '#3a2414', '#c8a050')
    mage = v.get('mage')
    pal = palette(skin=v['skin'], hair=v['hair'], tunic=v['body'], sleeve=v['body'], sleeve2=v['body'],
                  belly=v['body'], robe={'base': v['body'], 'style': 'cloth'}, hat=v.get('hat', '#3a2a5a'),
                  hat2=v.get('hat', '#3a2a5a'), scarf=v.get('scarf', '#a03a2a'), book='#5a2a4a',
                  shield={'base': '#7a5030', 'style': 'wood'}, scabbard='#3a2418')
    show = ({'hair_cap', 'hair_back', 'wizard_hat', 'robe_long', 'tome'} if mage else
            {'hair_cap', 'scarf', 'shield_back', 'sword_hip', 'bracer_r', 'bracer_l', 'pouch'})
    poses = {'left_arm': dict(rot=(-20, 0, -4)), 'left_forearm': dict(rot=(-56, 0, 0))} if mage else {}
    return look(vid, builds=(SLIM, NO_LEGS) if mage else ((SLIM,) if v.get('woman') else ()), show=show, poses=poses,
                pal=pal, paint=paint, work='work_read' if False else None, talk='talk_point' if not mage else 'talk_scholar',
                scale=1.0, portrait=dict(bg=('#6a5aa0', '#1a1430') if mage else ('#8a6a3a', '#241a0c')))


# ---------------------------------------------------------------------------------------------- guards, clergy

def guard():
    """Royal gate guard: kettle helmet, mail, the blue tabard with the crest, halberd."""
    skin = '#e0b494'
    face = Face(skin, eye='#3a4a5a', style='stern', brows='#4a3a2c', brow_style='flat', mouth='line')

    def paint(p):
        face.paint(p)
        helmet(p, '#a4acb8', left=1, right=1, top=2, rim=(80, 86, 96))
        hb = p.face('shell', 'front')
        for x in range(hb.w):
            hb.px(x, 1, (200, 170, 80) if x % 2 else (176, 146, 64))
        for side in ('right', 'left'):
            upper, fore = arm_parts(p, side)
            mail(p, upper, '#7e8692')
            mail(p, fore, '#7e8692', rows=(0, 1, 2))
        hands(p, '#5a4030', rows=3, gloves=True)
        mail(p, torso_part(p), '#7e8692')
        tabard(p, ROYAL)
        belt(p, 0, '#3a2a1c', '#c8b060')
        legs(p, '#3a4260')
        boots(p, '#3a2a20', rows=3, top_trim='#7e8692')
    pal = palette(skin=skin, tunic='#7e8692', sleeve='#7e8692', sleeve2='#7e8692', belly='#3a3a44',
                  shell={'base': '#a4acb8', 'style': 'metal'}, apron=ROYAL, coat=ROYAL, armor='#a4acb8',
                  blade={'base': '#c8d0d8', 'style': 'metal'}, pole='#5a3a20', plume='#2c4fa0')
    return look('guard', show={'shell', 'helm_crest', 'pauldron_r', 'pauldron_l', 'apron', 'apron_bib', 'coat_tails',
                               'halberd'},
                poses={'right_arm': dict(rot=(-8, 0, 4)), 'right_forearm': dict(rot=(-24, 0, 0)),
                       'helm_crest': dict(scale=(1.0, 0.7, 0.8))},
                pal=pal, paint=paint, idle='idle_still', work='work_guard', talk='talk_salute', scale=1.02,
                portrait=dict(bg=('#3a5aa0', '#101a34')))


def knight():
    """Castle knight in full plate: closed helm with a blue plume, the royal cape, kite shield and sword."""
    def paint(p):
        for fc in faces_of(p, 'head'):
            fill_face(fc, (60, 64, 72))
        helmet(p, '#c0c8d4', left=0, right=0, top=0, bottom=0)
        hf = p.face('shell', 'front')
        plate(p, 'shell', '#c0c8d4', faces=('front',), rivet=False)
        hf.hline(1, 6, 3, (24, 26, 34))              # eye slit
        hf.vline(4, 4, 6, (40, 44, 54))
        for (x, y) in ((2, 5), (6, 5), (2, 6), (6, 6)):
            hf.px(x, y, (60, 64, 76))
        t = torso_part(p)
        plate(p, t, '#c0c8d4')
        tf = p.face(t, 'front')
        crest(tf, tf.w // 2, 3, GOLD)
        for side in ('right', 'left'):
            upper, fore = arm_parts(p, side)
            plate(p, upper, '#b8c0cc')
            plate(p, fore, '#b8c0cc')
        hands(p, '#8a92a0', rows=2, gloves=True)
        for part in ('hips', 'right_leg', 'left_leg', 'right_shin', 'left_shin'):
            plate(p, part, '#b0b8c4', rivet=False)
        belt(p, 0, '#2a2030', '#e0c050', buckle_w=3)
        cf = p.face('cape', 'back')
        crest(cf, cf.w // 2, 6, GOLD)
        for fc in faces_of(p, 'cape', sides_only=True):
            fc.hline(0, fc.w - 1, fc.h - 1, col(GOLD))
        sf = p.face('shield_arm', 'front')
        for y in range(sf.h):
            for x in range(sf.w):
                half = sf.w / 2.0
                width = half if y < 6 else half * (1.0 - (y - 5) / 6.5)
                if abs(x + 0.5 - half) > width:
                    sf.clear(x, y)
                elif abs(x + 0.5 - half) > width - 1:
                    sf.px(x, y, col(GOLD))
        crest(sf, sf.w // 2, 4, GOLD)
        sb = p.face('shield_arm', 'back')
        for y in range(sb.h):
            for x in range(sb.w):
                half = sb.w / 2.0
                width = half if y < 6 else half * (1.0 - (y - 5) / 6.5)
                if abs(x + 0.5 - half) > width:
                    sb.clear(x, y)
    pal = palette(tunic='#c0c8d4', sleeve='#b8c0cc', sleeve2='#b8c0cc', belly='#b0b8c4',
                  shell={'base': '#c0c8d4', 'style': 'metal'}, armor={'base': '#c8d0dc', 'style': 'metal'},
                  cape={'base': ROYAL, 'style': 'cloth'}, plume='#2c4fa0', kite={'base': ROYAL, 'style': 'cloth'},
                  blade={'base': '#d4dce4', 'style': 'metal'}, guard={'base': '#e0c050', 'style': 'gold'},
                  grip='#3a2a3a', boots={'base': '#b0b8c4', 'style': 'metal'},
                  pants={'base': '#b0b8c4', 'style': 'metal'})
    return look('knight', show={'shell', 'helm_crest', 'pauldron_r', 'pauldron_l', 'cape', 'shield_arm',
                                'practice_sword'},
                poses={'right_arm': dict(rot=(-6, 0, 4)), 'right_forearm': dict(rot=(-10, 0, 0)),
                       'practice_sword': dict(rot=(70, 0, 0)), 'left_arm': dict(rot=(-10, 0, -2)),
                       'left_forearm': dict(rot=(-14, 0, 0))},
                pal=pal, paint=paint, idle='idle_still', work='work_watch', talk='talk_salute', scale=1.04,
                portrait=dict(bg=('#4a6ab8', '#121a38')))


def archer():
    """Wall archer: leather cap, quilted blue-green gambeson, quiver and longbow."""
    skin = '#ecc0a0'
    hair_c = '#5a3a24'
    face = Face(skin, eye='#3a6a4a', style='narrow', brows=hair_c, brow_style='flat', mouth='line', freckles=True)

    def paint(p):
        face.paint(p)
        hair(p, hair_c, bangs=[2, 1, 2, 1, 2, 1, 2, 1], back_len=6, side_front=5)
        cap(p, '#6a4a2c', rows=3, band='#4a301c')
        t = torso_part(p)
        f = p.face(t, 'front')
        for y in range(f.h):
            for x in range(f.w):
                if (x + y) % 3 == 0:
                    f.px(x, y, shade(f.get(x, y), 0.86))
        crest(f, f.w // 2, 3, GOLD, small=True)
        f.vline(1, 0, f.h - 1, (90, 60, 34))      # quiver strap
        hands(p, '#5a3a24', rows=2, gloves=True)
        belt(p, 0, '#3a2414', '#c8a050')
        pants_and_boots(p, '#3a3a2c', '#4a3020', rows=4, trim='#6a4830')
        bow = p.face('longbow', 'left')
        for y in range(bow.h):
            for x in range(bow.w):
                bend = 4 - int(round(3.4 * (1 - ((y - 10.5) / 10.5) ** 2)))
                if x == bend or (y in (0, bow.h - 1) and x == 4):
                    bow.px(x, y, (122, 80, 46))
                elif x == 4:
                    bow.px(x, y, (230, 226, 210))
                else:
                    bow.clear(x, y)
        br = p.face('longbow', 'right')
        for y in range(br.h):
            for x in range(br.w):
                bend = int(round(3.4 * (1 - ((y - 10.5) / 10.5) ** 2)))
                if x == bend or (y in (0, br.h - 1) and x == 0):
                    br.px(x, y, (122, 80, 46))
                elif x == 0:
                    br.px(x, y, (230, 226, 210))
                else:
                    br.clear(x, y)
    pal = palette(skin=skin, hair=hair_c, tunic='#2a5a6a', sleeve='#2a5a6a', sleeve2='#2a5a6a', belly='#2a4a5a',
                  bracer='#6a4428', quiver='#6a4428', hat='#6a4a2c')
    return look('archer', builds=(SLIM,), show={'hair_cap', 'band', 'quiver', 'longbow', 'bracer_r', 'bracer_l'},
                poses={'left_arm': dict(rot=(-6, 0, -4))},
                pal=pal, paint=paint, idle='idle_still', work='work_watch', talk='talk_salute', scale=1.0,
                portrait=dict(bg=('#3a7a7a', '#0e2424')))


def priest():
    """Priest of the cathedral: mitre, white and gold vestments, a blue stole, the book of prayers."""
    skin = '#e8c0a4'
    face = Face(skin, eye='#4a5a7a', style='narrow', brows='#d8d4cc', brow_style='flat', mouth='smile',
                wrinkles=True)

    def paint(p):
        face.paint(p)
        hair(p, '#d8d4cc', bangs=[0, 0, 0, 0, 0, 0, 0, 0], back_len=6, side_front=5, highlight=(250, 250, 250))
        mf = p.face('mitre', 'front')
        mb = p.face('mitre', 'back')
        for fc in (mf, mb):
            for y in range(fc.h):
                for x in range(fc.w):
                    if y < 3 and abs(x - 2.5) > (y + 0.5):
                        fc.clear(x, y)
            fc.vline(2, 1, fc.h - 1, col(GOLD))
            fc.vline(3, 1, fc.h - 1, col(GOLD))
            fc.hline(0, fc.w - 1, fc.h - 1, col(GOLD))
        for fc in faces_of(p, 'mitre'):
            if fc.face in ('right', 'left'):
                for y in range(fc.h):
                    for x in range(fc.w):
                        if y < 3:
                            fc.clear(x, y)
            if fc.face == 'top':
                mask_alpha(fc, lambda x, y: False)
        t = torso_part(p)
        f = p.face(t, 'front')
        for y in range(f.h):                          # the stole
            f.px(2, y, col(ROYAL))
            f.px(f.w - 3, y, col(ROYAL))
        f.px(2, 4, col(GOLD))
        f.px(f.w - 3, 4, col(GOLD))
        rf = p.face('robe_long', 'front')
        for y in range(rf.h):
            rf.px(2, y, col(ROYAL))
            rf.px(rf.w - 3, y, col(ROYAL))
        crest(rf, 2, 11, GOLD, small=True)
        crest(rf, rf.w - 3, 11, GOLD, small=True)
        for fc in faces_of(p, 'robe_long', sides_only=True):
            fc.hline(0, fc.w - 1, fc.h - 1, col(GOLD))
        sleeves_cuff(p, GOLD, row=3)
        hands(p, skin)
        belt(p, 0, '#c8a040', None)
        tt = p.face('tome', 'top')
        fill_face(tt, (120, 30, 40), shade_rows=False)
        crest(tt, tt.w // 2, tt.h // 2, GOLD, small=True)
    pal = palette(skin=skin, hair='#d8d4cc', tunic='#f4f2ec', sleeve='#f4f2ec', sleeve2='#f4f2ec', belly='#f4f2ec',
                  robe={'base': '#f4f2ec', 'style': 'cloth'}, book='#7a1e2a')
    return look('priest', builds=(NO_LEGS,), show={'hair_cap', 'mitre', 'robe_long', 'tome'},
                poses={'left_arm': dict(rot=(-18, 0, -4)), 'left_forearm': dict(rot=(-40, 0, 0)),
                       'tome': dict(rot=(-40, 0, 0))},
                pal=pal, paint=paint, idle='idle_still', work='work_pray', talk='talk_bless', scale=1.0,
                portrait=dict(bg=('#d8c070', '#3a3010'), dy=-3.0))


def nun():
    """Sister of the cathedral: navy habit and veil, white wimple, a lit candle and a rosary."""
    skin = '#f2d4c0'
    face = Face(skin, eye='#5a6a8a', style='tall', brows='#5a4a3a', mouth='smile', blush=True)

    def paint(p):
        face.paint(p)
        for fc in faces_of(p, 'shell'):
            if fc.face == 'bottom':
                mask_alpha(fc, lambda x, y: False)
        shell_face_opening(p, 'shell', left=1, right=1, top=2, bottom=0, rim=(240, 240, 244))
        # white wimple framing the face
        for fc in faces_of(p, 'band'):
            if fc.face in ('top', 'bottom'):
                mask_alpha(fc, lambda x, y: False)
                continue
            for y in range(fc.h):
                for x in range(fc.w):
                    if fc.face == 'front':
                        if y == 1 or (y >= 1 and x in (0, fc.w - 1)) or y == fc.h - 1:
                            fc.px(x, y, (244, 244, 248))
                        else:
                            fc.clear(x, y)
                    elif y >= 1:
                        fc.px(x, y, (236, 236, 242))
                    else:
                        fc.clear(x, y)
        t = torso_part(p)
        f = p.face(t, 'front')
        for x in range(1, f.w - 1):
            f.px(x, 0, (244, 244, 248))
        for (x, y) in ((2, 1), (3, 2), (4, 2), (5, 1)):
            f.px(x, y, (240, 230, 220))
        f.px(4, 3, col(GOLD))
        f.px(4, 4, col(GOLD))
        f.px(3, 4, col(GOLD))
        f.px(5, 4, col(GOLD))
        f.px(4, 5, col(GOLD))
        for fc in faces_of(p, 'robe_long', sides_only=True):
            fc.hline(0, fc.w - 1, fc.h - 1, shade(col('#22263a'), 0.7))
        sleeves_cuff(p, '#f0f0f4', row=4)
        hands(p, skin)
        belt(p, 0, '#16182a', None)
        for i, part in enumerate(('candle',)):
            pass
        g = p.face('candle', 'front', index=2, glow=True)
        g.px(0, 0, (255, 210, 120))
        for name in ('top', 'right', 'left', 'back'):
            gg = p.face('candle', name, index=2, glow=True)
            gg.px(0, 0, (255, 210, 120))
    pal = palette(skin=skin, tunic='#22263a', sleeve='#22263a', sleeve2='#22263a', belly='#22263a',
                  robe={'base': '#22263a', 'style': 'cloth'}, shell={'base': '#22263a', 'style': 'cloth'},
                  veil={'base': '#22263a', 'style': 'cloth'})
    return look('nun', builds=(SLIM, NO_LEGS), show={'shell', 'band', 'veil_back', 'robe_long', 'candle'},
                poses={'right_arm': dict(rot=(-34, 0, -6)), 'right_forearm': dict(rot=(-46, 0, 0)),
                       'candle': dict(rot=(-10, 0, 0)), 'left_arm': dict(rot=(-30, 0, 8)),
                       'left_forearm': dict(rot=(-40, 0, 0))},
                pal=pal, paint=paint, glow=True, idle='idle_still', work='work_pray', talk='talk_bless', scale=0.95,
                portrait=dict(bg=('#4a5070', '#121420')))


# area colours of the dungeon wardens: (hood/cloak, tabard, trim, skin, extra parts)
WARDENS = {
    'd1': ('#2a2e48', ROYAL_DARK, GOLD, '#d8a888', ()),
    'd2': ('#2e4a2a', '#3e6a34', '#c8b060', '#e0b490', ('wreath',)),
    'd3': ('#c8a878', '#a83a2a', GOLD, '#a8704a', ()),
    'd4': ('#3a3e4a', '#4a5a7a', '#c8ccd4', '#e8c8b4', ('mantle',)),
    'd5': ('#a8c4dc', '#3a6a9a', '#e8f4ff', '#f0d8cc', ('mantle',)),
    'd6': ('#2a2224', '#8a2a1a', '#f0a040', '#b88060', ()),
    'd7': ('#f0eee8', '#3a6ab0', GOLD, '#f2dcc8', ()),
    'd8': ('#1e1a26', '#4a2a6a', '#a080d0', '#d8b8a8', ()),
}


def warden(vid='warden', area='d1'):
    """Warden of a dungeon gate: hooded cloak over mail, a short tabard and a glaive, in the colours of the area."""
    hood, tabard_c, trim, skin, extra = WARDENS[area]
    face = Face(skin, eye='#4a3a2a', style='stern', brows='#3a2a20', brow_style='thick', mouth='line',
                stubble=shade(col(skin), 0.6))

    def paint(p):
        face.paint(p)
        for fc in faces_of(p, 'shell'):
            if fc.face == 'bottom':
                mask_alpha(fc, lambda x, y: False)
        shell_face_opening(p, 'shell', left=1, right=1, top=2, bottom=0, rim=shade(col(trim), 0.7))
        mail(p, torso_part(p), '#6e7682')
        for side in ('right', 'left'):
            upper, fore = arm_parts(p, side)
            mail(p, fore, '#6e7682', rows=(0, 1, 2, 3))
        hands(p, '#3a2a20', rows=2, gloves=True)
        tabard(p, tabard_c, trim=trim, emblem=trim, back=False)
        belt(p, 0, '#2a2030', trim)
        legs(p, shade(col(hood), 0.8))
        boots(p, '#2a2020', rows=3)
        for fc in faces_of(p, 'cape', sides_only=True):
            fc.hline(0, fc.w - 1, fc.h - 1, col(trim))
        if 'wreath' in extra:
            wt = p.face('wreath', 'top')
            for (x, y) in ((1, 1), (6, 6), (2, 7)):
                wt.px(x, y, (240, 230, 140))
    pal = palette(skin=skin, tunic='#6e7682', sleeve=hood, sleeve2=hood, belly=shade(col(hood), 0.85),
                  shell={'base': hood, 'style': 'cloth'}, cape={'base': shade(col(hood), 0.92), 'style': 'cloth'},
                  apron=tabard_c, blade={'base': '#c8d0d8', 'style': 'metal'}, pole='#3a2a20',
                  mantle={'base': '#8a7a6a' if area == 'd4' else '#e8ecf0', 'style': 'fur'})
    return look(vid, show={'shell', 'cape', 'apron', 'apron_bib', 'glaive'} | set(extra),
                poses={'right_arm': dict(rot=(-8, 0, 4)), 'right_forearm': dict(rot=(-24, 0, 0))},
                pal=pal, paint=paint, idle='idle_still', work='work_watch', talk='talk_gruff', scale=1.02,
                portrait=dict(bg=('#3a3e60', '#0e1018')))


FOLK = [
    (merchant, 'merchant', dict(skin=SKINS[2], hair='#3a2618', coat='#4a6a3a', hat='#5a3a24', eye='#3a2a20')),
    (merchant, 'merchant2', dict(woman=True, skin=SKINS[0], hair='#7a3e22', coat='#2c4f8a', scarf='#c03a3a',
                                 dots='#f0e6d0', skirt='#5a4030', eye='#3a6a3a')),
    (housewife, 'housewife', dict(skin=SKINS[0], hair='#8a5a34', dress='#6a7a3a', scarf='#f2ece0', dots='#5a7ab0',
                                  eye='#3a5a2a')),
    (housewife, 'housewife2', dict(skin=SKINS[3], hair='#1e1612', dress='#7a3a5a', bun=True, eye='#2a1a10',
                                   basket=((120, 170, 60), (230, 130, 40), (200, 60, 50)))),
    (farmer, 'farmer', dict(skin=SKINS[1], hair='#6a4a2a', overalls='#3a5a8a', shirt='#d8cfb8', stubble='#6a5040',
                            eye='#3a2a20')),
    (farmer, 'farmer2', dict(woman=True, skin=SKINS[0], hair='#c88a3a', dress='#8a6a3a', freckles=True,
                             hatband='#3a6a9a', eye='#4a7a3a')),
    # the capital's stablemaster (mounts): the stable's blue, a gold hatband
    (farmer, 'stable_master', dict(skin=SKINS[2], hair='#3a2618', overalls='#2c3f9c', shirt='#e6dcc4',
                                   stubble='#4a3424', hatband='#d8a43c', eye='#3a2a20')),
    (sailor, 'sailor', dict(skin=SKINS[2], hair='#2a2018', stripe='#2c4f8a', cap='#1e2a4a', stubble='#3a2a20',
                            extra=('pipe',), eye='#2a3a5a')),
    (sailor, 'sailor2', dict(skin=SKINS[3], hair='#1a1410', stripe='#a83a3a', bandana='#2c4f8a', extra=('sack',),
                             pants='#5a4a3a', eye='#2a1a10')),
    (noble_lady, 'noble_lady', dict(skin=SKINS[4], hair='#ecc86a', gown='#8a6ab0', panel='#f2ecf8', hl=(255, 240, 180),
                                    eye='#4a6ad0', fan='#f4e0f0')),
    (noble_lady, 'noble_lady2', dict(skin=SKINS[0], hair='#2a1e24', gown='#2a7a5a', panel='#e8f4ec', mark=True,
                                     eye='#6a3a2a', fan='#f8f0d8', bg=('#3a9a7a', '#0e2a20'))),
    (noble_gent, 'noble_gent', dict(skin=SKINS[0], hair='#8a6a4a', coat='#1e2030', monocle=True, eye='#3a4a5a')),
    (noble_gent, 'noble_gent2', dict(skin=SKINS[2], hair='#2a2420', coat='#5a1e2a', hat='#2a2026', hatband='#d8b04a',
                                     cravat='#d8b04a', pants='#e8e0d0', eye='#3a2a20')),
    (elder, 'elder', dict(skin=SKINS[1], dress='#6a5a48', eye='#5a6a7a')),
    (elder, 'elder2', dict(woman=True, skin=SKINS[0], dress='#4a4a6a', shawl='#a85a4a', eye='#5a5a6a')),
    (apprentice, 'apprentice', dict(skin=SKINS[0], hair='#c88a4a', shirt='#8a7a5a', eye='#3a6a3a', cap='#4a5a6a')),
    (apprentice, 'apprentice2', dict(woman=True, skin=SKINS[1], hair='#3a2418', shirt='#c8a878', scarf='#f2ece4',
                                     apron='#f2efe6', eye='#3a2a20', freckles=False)),
    (maid, 'maid', dict(skin=SKINS[0], hair='#6a4028', dress='#1e1e28', eye='#4a6a3a')),
    (maid, 'maid2', dict(skin=SKINS[4], hair='#e8c070', dress='#24305a', eye='#3a5ab0')),
    (bard, 'bard', dict(skin=SKINS[1], hair='#a0582a', c1='#b8302e', c2='#e0b84a', hat='#2a4a2a', feather='#f0e0c0',
                        eye='#3a5a2a')),
    (bard, 'bard2', dict(woman=True, skin=SKINS[0], hair='#3a4a8a', c1='#2a7a6a', c2='#e8d8b0', hat='#6a2a5a',
                         feather='#f0c040', eye='#6a3a8a')),
    (child, 'child', dict(skin=SKINS[0], hair='#b8783a', shirt='#3a6aa0', pants='#5a4a34', freckles=True,
                          eye='#3a6a3a')),
    (child, 'child2', dict(girl=True, skin=SKINS[2], hair='#2a1a12', shirt='#e0806a', skirt='#c84a4a', eye='#3a2a1a',
                           scale=0.68)),
    (adventurer, 'adventurer', dict(skin=SKINS[1], hair='#3a2a1c', body='#6a4a2e', scarf='#a83a2a', eye='#3a4a2a')),
    (adventurer, 'adventurer2', dict(mage=True, woman=True, skin=SKINS[4], hair='#8a5ac8', body='#3a2a6a',
                                     hat='#2a1e4a', eye='#9a5ad0')),
    # the Starlight Manor's innkeeper (the inn rooms, docs/modules/estates.md)
    (innkeeper, 'innkeeper', dict(skin=SKINS[1], hair='#5a3424', dress='#7a2034', shawl='#e0a83a', eye='#6a4a2a')),
]


def cast():
    return ([fn(vid, v) for fn, vid, v in FOLK] + [guard(), knight(), archer(), priest(), nun(), warden()]
            + [warden('warden_' + area, area) for area in WARDENS])


def base():
    return [citizen()]
