"""The people of Astoria for the NPC rig (rig_npc.py): shared palette, builds and costume helpers, and the list of
every appearance (designed from docs/STORY.md section 2 and the kingdom concept art: white stone, royal blue and gold).

Appearance ids: the 22 speakers of the town and story NPCs (ids of com.eternalrealm.story.Speaker; casts in
npc_cast_town.py and npc_cast_story.py), the townsfolk, guards and clergy of the capital (npc_cast_folk.py), the
dungeon 'warden' and 'citizen' (default for anyone else). Every appearance picks a body build, optional parts, rest
poses for held props, a palette, a painted face and costume, its idle/walk/work/talk animations and its portrait.
"""
from erpaint import lighten, mix, shade
from ermodel import Variant
from npc_paint import (CROSS, DIAMOND, SHIELD, STAR, Face, arm_parts, bald_head, bare_arms, belt, boots, bottom_color,
                       buttons, circle_mask, clear_part, col, emblem, faces_of, fill_face, glow_fill, hair, hair_piece,
                       hands, legs, mask_alpha, open_front, page_lines, rows_color, sash, shell_face_opening, shell_rows,
                       band_rows, strand_texture, tint_face, top_color, torso_part, trim_col, trim_row, vneck)

BASE = {
    'skin': '#d9a77f', 'fx': '#000000', 'hair': '#5a3a24', 'band': '#8a2a2a', 'shell': '#5a4a3a', 'ear': '#d9a77f',
    'beard': '#5a3a24', 'glass': '#303030', 'goggle': '#a07a40', 'stick': '#f0f0f0', 'candy': '#f070b0',
    'tiara': '#e0c050', 'crown': '#c8b070', 'hat': '#2a2028', 'hat2': '#2a2028', 'turban': '#f0ead8',
    'jewel': {'base': '#d02030', 'style': 'gem'}, 'plume': '#b03030', 'wing': '#f4f4f4', 'lamp': '#6a6e76',
    'halo': '#e0c050', 'haft': '#6b4c2a', 'tool': '#7a7f88', 'quill': '#f0f0f0', 'coin': '#f0c040', 'rag': '#d8d0c0',
    'ladle': '#7a5a3a', 'handle': '#6b4c2a', 'gavel': '#5a3a24', 'brass': '#d0a040', 'grip': '#4a3020',
    'guard': '#c0a050', 'blade': '#c8d0d8', 'horn': '#e0d0b0', 'paper': '#f0e8d8', 'pole': '#6b4c2a',
    'iron': '#5d6168', 'lantern': '#3a3a40', 'staff': '#6a4a2a', 'leafy': '#5a9a40', 'bud': '#f0a0c8',
    'chain': '#7a7f88', 'book': '#6a3a2a', 'pouch': '#7a5030', 'plate': '#f0f0ea', 'bowl': '#7a5030',
    'page': '#f4ecd8', 'gauntlet': '#a03020', 'sleeve2': '#6b4a35', 'bracer': '#6a4a2a', 'sleeve': '#6b4a35',
    'armor': '#9aa2ad', 'tunic': '#6b4a35', 'collar': '#3a3a5a', 'scarf': '#e0b030', 'mantle': '#9a9890',
    'cape': '#7a2222', 'apron': '#7a5a3a', 'potion_r': {'base': '#e03040', 'style': 'gem'},
    'potion_b': {'base': '#3070e0', 'style': 'gem'}, 'potion_g': {'base': '#40c050', 'style': 'gem'},
    'pigeon': '#a8a8b0', 'pigeon_tail': '#6a6a74', 'pigeon_head': '#8a8a96', 'beak': '#e0a040',
    'pigeon_wing': '#9898a4', 'carpet': '#a02a2a', 'shield': '#8a5a30', 'bow': '#6a4a2a',
    'orb': {'base': '#a0f0ff', 'style': 'glowcore'}, 'ring': '#e0c050', 'pants': '#4a3b30', 'boots': '#3b2a20',
    'cuff': '#d8d4cc', 'belly': '#5d4030', 'skirt': '#2c3e66', 'skirt2': '#2c3e66', 'robe': '#2a2a4a',
    'coat': '#6a1e30', 'scabbard': '#3a2a20', 'satchel': '#7a5030', 'keys': '#8a8070',
    'straw': {'base': '#d8b860', 'style': 'rope'}, 'straw2': {'base': '#d0ae58', 'style': 'rope'}, 'mitre': '#f2efe4',
    'pipe': '#6a4020', 'pipe_bowl': '#4a2c18', 'wreath': '#5a9a40', 'veil': '#22263a',
    'potion_hand': {'base': '#e03848', 'style': 'gem'}, 'cork': '#a07040', 'holder': '#c8a850', 'wax': '#f2ecdc',
    'candle_flame': {'base': '#ffc850', 'style': 'flame'}, 'scepter': '#e0c050',
    'scepter_gem': {'base': '#70b0ff', 'style': 'gem'}, 'cane': '#5a3a20', 'bristle': {'base': '#c8a860', 'style': 'rope'},
    'basket': {'base': '#b08850', 'style': 'rope'}, 'basket_handle': '#8a6a40', 'fan': '#e8c0d0', 'longbow': '#7a5030',
    'kite': '#2c4fa0', 'lute': '#a86c34', 'lute_neck': '#4a3020', 'lute_head': '#4a3020', 'quiver': '#6a4a2a',
    'fletch': '#f0f0ea', 'pack': '#7a5a3a', 'bedroll': '#5a6a3a', 'sack': {'base': '#c8b490', 'style': 'cloth'},
}

# the kingdom's colours (concept art: white stone, royal blue roofs and banners, gold trim)
ROYAL = '#2c4fa0'
ROYAL_DARK = '#1e3672'
GOLD = '#d8b04a'
WHITE = '#efece4'


def palette(**over):
    pal = dict(BASE)
    pal.update(over)
    return pal


SLIM = ({'r_upper_slim', 'r_fore_slim', 'l_upper_slim', 'l_fore_slim'}, {'r_upper', 'r_fore', 'l_upper', 'l_fore'})
BROAD = ({'torso_broad'}, {'torso'})
NO_LEGS = (set(), {'right_leg', 'left_leg'})


def body(*builds, show=(), hide=()):
    s, h = set(show), set(hide)
    for b in builds:
        s |= b[0]
        h |= b[1]
    return s, h


def broad_poses(poses=None):
    out = {'right_arm': dict(pos=(-1, 0, 0)), 'left_arm': dict(pos=(1, 0, 0))}
    for k, v in (poses or {}).items():
        if k in out:
            merged = dict(out[k])
            for key, val in v.items():
                if key == 'pos':
                    merged['pos'] = tuple(a + b for a, b in zip(merged['pos'], val))
                else:
                    merged[key] = val
            out[k] = merged
        else:
            out[k] = v
    return out


def look(vid, builds=(), show=(), hide=(), poses=None, pal=None, paint=None, glow=False, idle=None, work=None,
         talk=None, scale=1.0, portrait=None, broad=False):
    """portrait: dict(bg=(top, bottom) backdrop colours, dy (negative: more room above the head), yaw, zoom,
    hide=parts left out of the portrait); see generate_npc.portrait."""
    s, h = body(*builds, show=show, hide=hide)
    if broad:
        s |= BROAD[0]
        h |= BROAD[1]
        poses = broad_poses(poses)
    overrides = {}
    if idle:
        overrides['idle'] = idle
    if work:
        overrides['work'] = work
    if talk:
        overrides['talk'] = talk
    return Variant(vid, show=s, hide=h, poses=poses or {}, palette=pal or palette(), paint=paint, glow=glow,
                   overrides=overrides, extra=dict(scale=scale, portrait=portrait or {}))


# ----------------------------------------------------------------------------------------------- shared bits

def shirt_collar(p, c, part=None):
    part = part or torso_part(p)
    fc = p.face(part, 'front')
    c = col(c)
    fc.px(fc.w // 2 - 2, 0, c)
    fc.px(fc.w // 2 + 1, 0, c)
    fc.px(fc.w // 2 - 1, 1, shade(c, 0.9))
    fc.px(fc.w // 2, 1, shade(c, 0.9))


def neck_skin(p, skin, depth=1, width=2, part=None):
    part = part or torso_part(p)
    fc = p.face(part, 'front')
    x0 = fc.w // 2 - width // 2
    for y in range(depth):
        for x in range(x0, x0 + width):
            fc.px(x, y, shade(col(skin), 0.92))


def sleeves_cuff(p, c, row=3, sides=('right', 'left')):
    for side in sides:
        _, fore = arm_parts(p, side)
        rows_color(p, fore, row, row, c)


def pants_and_boots(p, pants, boot, rows=3, trim=None):
    legs(p, pants)
    boots(p, boot, rows=rows, top_trim=trim)


# ------------------------------------------------------------------------------------------- costume motifs

# the kingdom's crest (banners, tabards, shields): a winged star, 5x5
CREST = [(0, -2), (-1, -1), (0, -1), (1, -1), (-2, 0), (0, 0), (2, 0), (-1, 1), (0, 1), (1, 1), (0, 2)]
CREST_SMALL = [(0, -1), (-1, 0), (0, 0), (1, 0), (0, 1)]


def crest(fc, cx, cy, c=GOLD, small=False, glow=None):
    pts = CREST_SMALL if small else CREST
    c = col(c)
    for (dx, dy) in pts:
        fc.px(cx + dx, cy + dy, lighten(c, 0.25) if dy < 0 else c)
        if glow is not None:
            glow.px(cx + dx, cy + dy, c)


def eyepatch(p, side='left', c=(34, 26, 26), row=4):
    """Patch over one eye (side as the character sees it; 'left' is the viewer's right) and its strap."""
    f = p.face('head', 'front')
    xs = (5, 6) if side == 'left' else (1, 2)
    for x in xs:
        f.px(x, row - 1, shade(c, 1.2))
        f.px(x, row, c)
    strap = shade(c, 0.9)
    for x in range(8):
        if x not in xs:
            f.px(x, row - 2 if (x < xs[0]) == (side == 'left') else row - 1, strap)
    for name in ('right', 'left', 'back'):
        fc = p.face('head', name)
        for x in range(fc.w):
            fc.px(x, row - 2, strap)


def round_glasses(p, rim=(200, 170, 90), lens=(225, 238, 250), row=4):
    """Light half-rim glasses on the glasses plane: a rim over each eye, the outer hinges and the bridge, a glint in
    each lens. The eyes underneath stay readable (full dark rims read as goggles at this size)."""
    clear_part(p, 'glasses')
    gf = p.face('glasses', 'front')
    y = row - 2           # the plane's rows 0..3 cover head rows 2..5
    rim = col(rim)
    for (x0, x1) in ((0, 3), (4, 7)):
        gf.hline(x0 + 1, x1 - 1, y - 1, rim)
        gf.px(x0, y, shade(rim, 0.8))
        gf.px(x1, y, shade(rim, 0.8))
    gf.px(3, y - 1, rim)
    gf.px(4, y - 1, rim)
    gf.px(2, y - 1, col(lens))
    gf.px(5, y - 1, col(lens))


def stripes(p, part, c1, c2, period=2, faces=('front', 'right', 'back', 'left'), index=0, rows=None):
    c1, c2 = col(c1), col(c2)
    for fc in faces_of(p, part, index):
        if fc.face not in faces:
            continue
        for y in range(fc.h):
            if rows is not None and y not in rows:
                continue
            cc = c1 if (y // max(1, period // 2)) % 2 == 0 else c2
            for x in range(fc.w):
                fc.px(x, y, shade(cc, (1.04 - 0.1 * y / max(1, fc.h - 1)) * (0.94 if x in (0, fc.w - 1) else 1.0)))


def checker(fc, c1, c2, size=1, x0=0, y0=0, x1=None, y1=None):
    x1 = fc.w - 1 if x1 is None else x1
    y1 = fc.h - 1 if y1 is None else y1
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            fc.px(x, y, col(c1) if ((x // size) + (y // size)) % 2 == 0 else col(c2))


def tabard(p, base, trim=GOLD, emblem=GOLD, back=True):
    """A royal tabard on the apron panels: base colour, gold edge, the crest on the chest."""
    for part in ('apron_bib', 'apron') + (('coat_tails',) if back else ()):
        for fc in faces_of(p, part):
            fill_face(fc, base)
            if fc.face in ('front', 'back'):
                fc.vline(0, 0, fc.h - 1, shade(col(trim), 0.9))
                fc.vline(fc.w - 1, 0, fc.h - 1, shade(col(trim), 0.9))
    af = p.face('apron', 'front')
    af.hline(0, af.w - 1, af.h - 1, col(trim))
    for x in range(1, af.w - 1, 2):
        af.px(x, af.h - 2, shade(col(trim), 0.8))
    bf = p.face('apron_bib', 'front')
    crest(bf, bf.w // 2, 2, emblem)
    crest(af, af.w // 2, 3, emblem, small=True)


def plate(p, part, steel, index=0, faces=('front', 'right', 'back', 'left'), rivet=True):
    """Polished steel: bright top band, a highlight column and a dark lower edge."""
    steel = col(steel)
    for fc in faces_of(p, part, index):
        if fc.face not in faces:
            continue
        for y in range(fc.h):
            for x in range(fc.w):
                f = 1.18 - 0.38 * (y / max(1, fc.h - 1))
                if x == 1:
                    f += 0.22
                if x == fc.w - 1:
                    f -= 0.12
                fc.px(x, y, shade(steel, f))
        if rivet and fc.w >= 4:
            fc.px(fc.w - 2, 0, lighten(steel, 0.6))


def mail(p, part, steel, index=0, rows=None):
    steel = col(steel)
    for fc in faces_of(p, part, index, sides_only=True):
        for y in range(fc.h):
            if rows is not None and y not in rows:
                continue
            for x in range(fc.w):
                fc.px(x, y, shade(steel, 1.12 if (x + y) % 2 == 0 else 0.78))


def bandage_wrap(p, part, c=(226, 218, 200), rows=None, index=0):
    c = col(c)
    for fc in faces_of(p, part, index, sides_only=True):
        for y in range(fc.h):
            if rows is not None and y not in rows:
                continue
            for x in range(fc.w):
                fc.px(x, y, shade(c, 0.86 if (x + 2 * y) % 5 == 0 else 1.0))


def lips_color(p, row, c, x0=3, x1=4):
    f = p.face('head', 'front')
    f.hline(x0, x1, row, col(c))


def blush_dots(p, c=(236, 130, 130), row=5, t=0.45):
    f = p.face('head', 'front')
    for x in (1, 6):
        f.px(x, row, mix(f.get(x, row), col(c), t))


def earring(p, c=GOLD, side='right', row=6):
    fc = p.face('head', side)
    fc.px(3 if side == 'right' else 4, row, col(c))


def pendant(p, c, glow=None, part=None, y=3, x=None):
    part = part or torso_part(p)
    f = p.face(part, 'front')
    x = f.w // 2 if x is None else x
    f.px(x - 1, 0, (200, 170, 90))
    f.px(x, 1, (200, 170, 90))
    f.px(x - 1, y - 1, col(c))
    f.px(x, y - 1, col(c))
    f.px(x - 1, y, shade(col(c), 0.8))
    f.px(x, y, lighten(col(c), 0.3))
    if glow is not None:
        g = p.face(part, 'front', glow=True)
        for (xx, yy) in ((x - 1, y - 1), (x, y - 1), (x - 1, y), (x, y)):
            g.px(xx, yy, col(glow))


def apron_stains(p, part='apron', c=(150, 130, 100), pts=((2, 3), (5, 6), (3, 7))):
    fc = p.face(part, 'front')
    for (x, y) in pts:
        if x < fc.w and y < fc.h:
            fc.px(x, y, mix(fc.get(x, y), col(c), 0.6))


def skin_hands(p, skin, rows=2):
    hands(p, skin, rows=rows)


def appearances():
    import npc_cast_folk
    import npc_cast_story
    import npc_cast_town
    return npc_cast_folk.base() + npc_cast_town.cast() + npc_cast_story.cast() + npc_cast_folk.cast()
