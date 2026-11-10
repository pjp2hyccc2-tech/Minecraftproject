"""Looks of the 25 field bosses (FieldBosses: ten calamities with three forms, fifteen wanderers with two).

Each is built on a dungeon boss of the same rig (its parts, paint and animations, so its actions are timed like that
boss's), then made its own: the parts it shows and hides, its proportions (part poses) and size, and its colours,
a three-tone ramp mapped onto the painted shading (dark, middle and light tones of the boss's palette), with its own
glow colour. Later forms add glowing veins and cracks over the body (boss_forms) in the glow colour.

generate.py calls extend(rig) for every boss rig before painting.
"""
import numpy as np

import boss_forms
import ermodel

# id: rig, base look, ramp (dark, middle, light), glow, parts shown, parts hidden, size factor, poses, forms
LOOKS = {
    # ---- calamities
    'c_meadow': ('queen', 'b5', ((16, 10, 26), (64, 40, 104), (176, 150, 226)), (196, 120, 255),
                 {'hood'}, {'crown', 'scepter'}, 1.0, {'gown': dict(scale=(1.25, 1.1, 1.25))}, 3),
    # a crowned giant tree: wide silver canopy, great antlers
    'c_moon_forest': ('treant', 'b2', ((22, 24, 40), (90, 96, 130), (206, 214, 236)), (210, 230, 255),
                      {'crystal'}, set(), 1.05, {'canopy': dict(scale=(1.3, 1.2, 1.3)),
                                                 'antler_r': dict(scale=(1.6, 1.6, 1.6)),
                                                 'antler_l': dict(scale=(1.6, 1.6, 1.6))}, 3),
    'c_iron_mine': ('colossus', 'b7', ((34, 20, 14), (120, 64, 34), (214, 150, 96)), (255, 150, 60),
                    {'cannon_l', 'cannon_r'}, {'antenna_l', 'antenna_r', 'ring'}, 0.72, {}, 3),
    # a robed keeper in a flowing gown of falling water
    'c_sacred_falls': ('queen', 'b4', ((10, 30, 46), (52, 120, 160), (200, 236, 250)), (150, 230, 255),
                       {'collar', 'shard_ring', 'gown', 'hair'},
                       {'axe', 'apron', 'left_leg', 'right_leg', 'pauldron_l', 'pauldron_r'}, 0.92,
                       {'gown': dict(scale=(1.15, 1.15, 1.3))}, 3),
    # a proud fire drake: swept horns, broad wings, head held high
    'c_ancient_ruins': ('dragon', 'b6', ((40, 8, 4), (170, 54, 20), (255, 196, 90)), (255, 170, 60),
                        set(), set(), 0.9, {'horn_r': dict(scale=(1.5, 1.5, 1.8)), 'horn_l': dict(scale=(1.5, 1.5, 1.8)),
                                            'right_wing': dict(scale=(1.2, 1.0, 1.15)),
                                            'left_wing': dict(scale=(1.2, 1.0, 1.15)),
                                            'neck1': dict(rot=(-8, 0, 0))}, 3),
    # broad shoulders and a small head: a mountain walking
    'c_summit': ('colossus', 'b3', ((30, 32, 36), (110, 114, 120), (232, 236, 240)), (170, 220, 255),
                 {'crystal'}, {'nemes', 'false_beard'}, 0.85, {'shoulder_r': dict(scale=(1.5, 1.4, 1.5)),
                                                             'shoulder_l': dict(scale=(1.5, 1.4, 1.5)),
                                                             'head': dict(scale=(0.85, 0.85, 0.85))}, 3),
    'c_end_forest': ('king', 'final', ((8, 4, 14), (40, 24, 70), (150, 120, 200)), (170, 90, 255),
                     {'halo', 'horn_l', 'horn_r'}, {'crown'}, 0.85, {}, 3),
    # a withered, long-armed root: the canopy shrunk to a few dry tufts
    'c_farm': ('treant', 'b2', ((30, 22, 10), (104, 86, 40), (196, 180, 120)), (190, 220, 80),
               {'thorns_l', 'thorns_r'}, set(), 0.95, {'canopy': dict(scale=(0.6, 0.55, 0.6)),
                                                      'right_arm': dict(scale=(1.1, 1.3, 1.1)),
                                                      'left_arm': dict(scale=(1.1, 1.3, 1.1))}, 3),
    # a heavy, nearly wingless jaw-beast of the lake bed: broad body, thick tail, short neck
    'c_lakeside': ('dragon', 'b6', ((6, 26, 30), (30, 104, 104), (150, 214, 196)), (120, 255, 220),
                   set(), set(), 0.82, {'right_wing': dict(scale=(0.5, 1.0, 0.55)), 'left_wing': dict(scale=(0.5, 1.0, 0.55)),
                                        'body': dict(scale=(1.18, 1.0, 1.08)), 'tail1': dict(scale=(1.3, 1.25, 1.1)),
                                        'neck1': dict(scale=(1.15, 1.15, 0.8)), 'jaw': dict(scale=(1.3, 1.2, 1.1)),
                                        'horn_r': dict(scale=(0.6, 0.6, 0.6)), 'horn_l': dict(scale=(0.6, 0.6, 0.6))}, 3),
    'c_harbour': ('king', 'b8', ((10, 14, 30), (40, 56, 100), (200, 190, 120)), (255, 236, 110),
                  {'floating_crown', 'core'}, {'helm'}, 0.8, {}, 3),
    # ---- wanderers
    'w_bramble_knight': ('king', 'b1', ((20, 30, 14), (70, 104, 50), (210, 170, 170)), (255, 110, 150),
                         {'helm', 'spikes_l', 'spikes_r'}, {'crown', 'beard'}, 1.0, {}, 2),
    'w_moonmoth_witch': ('queen', 'b5', ((30, 26, 40), (130, 116, 160), (236, 226, 246)), (230, 210, 255),
                         {'ice_wings'}, {'crown'}, 0.95, {'ice_wings': dict(scale=(1.3, 1.2, 1.0))}, 2),
    # hunched over huge fists, a molten core in its chest
    'w_slag_titan': ('colossus', 'b3', ((16, 14, 14), (70, 56, 50), (230, 120, 40)), (255, 120, 30),
                     {'core'}, {'nemes', 'false_beard'}, 0.68, {'torso': dict(rot=(14, 0, 0)),
                                                              'right_hand': dict(scale=(1.5, 1.5, 1.5)),
                                                              'left_hand': dict(scale=(1.5, 1.5, 1.5))}, 2),
    # slender, with very broad wings and a long thin neck and tail
    'w_cascade_wyvern': ('dragon', 'b6', ((20, 40, 60), (90, 150, 190), (236, 248, 255)), (170, 236, 255),
                         set(), set(), 0.7, {'right_wing': dict(scale=(1.45, 1.0, 1.3)),
                                             'left_wing': dict(scale=(1.45, 1.0, 1.3)),
                                             'body': dict(scale=(0.85, 0.9, 0.95)),
                                             'tail1': dict(scale=(0.8, 0.8, 1.25)),
                                             'neck1': dict(scale=(0.85, 0.85, 1.2))}, 2),
    # bent with age under long hanging sleeves
    'w_mist_hermit': ('queen', 'b4', ((30, 40, 36), (110, 140, 126), (226, 236, 226)), (180, 255, 220),
                      {'hair', 'sleeve_l', 'sleeve_r'}, {'axe', 'pauldron_l', 'pauldron_r'}, 0.85,
                      {'torso': dict(rot=(12, 0, 0)), 'sleeve_r': dict(scale=(1.2, 1.5, 1.2)),
                       'sleeve_l': dict(scale=(1.2, 1.5, 1.2))}, 2),
    # a tall slab of inscribed stone on legs
    'w_walking_glyph': ('colossus', 'b7', ((50, 40, 24), (170, 140, 90), (240, 220, 160)), (255, 220, 120),
                        {'visor'}, {'antenna_l', 'antenna_r', 'back_vents'}, 0.66,
                        {'torso': dict(scale=(1.0, 1.2, 0.8)), 'head': dict(scale=(0.8, 0.8, 0.8))}, 2),
    'w_ash_herald': ('king', 'b8', ((22, 20, 20), (90, 84, 80), (220, 140, 90)), (255, 140, 60),
                     {'core'}, {'horn_l', 'horn_r'}, 0.78, {}, 2),
    # a great bird of storms: huge wings, short neck, a fan of tail feathers
    'w_thunder_roc': ('dragon', 'b6', ((30, 26, 20), (120, 100, 70), (240, 230, 190)), (255, 250, 140),
                      set(), set(), 0.78, {'right_wing': dict(scale=(1.6, 1.0, 1.4)), 'left_wing': dict(scale=(1.6, 1.0, 1.4)),
                                           'neck1': dict(scale=(1.15, 1.15, 0.7)), 'tail2': dict(scale=(1.0, 1.0, 0.6)),
                                           'tail_tip': dict(scale=(2.4, 1.0, 1.4))}, 2),
    # a ring of glacier ice borne behind its back, ice ridges along the spine
    'w_glacier_bearer': ('colossus', 'b3', ((24, 40, 60), (110, 150, 190), (236, 246, 255)), (180, 230, 255),
                         {'crystal', 'loincloth', 'ring', 'back_vents'}, {'nemes'}, 0.75,
                         {'ring': dict(scale=(1.5, 1.5, 1.5)), 'back_vents': dict(scale=(1.6, 1.8, 1.6)),
                          'torso': dict(rot=(8, 0, 0))}, 2),
    # a pilgrim's tall, ragged cloak
    'w_hollow_walker': ('king', 'b1', ((12, 10, 14), (110, 104, 100), (236, 230, 216)), (160, 110, 255),
                        {'halo'}, {'beard'}, 1.05, {'cape': dict(scale=(0.9, 1.5, 1.0))}, 2),
    # drooping moss and a crown of grave mushrooms
    'w_gravebloom': ('treant', 'b2', ((26, 26, 30), (100, 100, 110), (226, 220, 236)), (220, 200, 255),
                     {'crystal', 'thorns_l'}, set(), 1.0, {'mushrooms': dict(scale=(1.5, 1.5, 1.5)),
                                                          'moss_r': dict(scale=(1.0, 1.7, 1.0)),
                                                          'moss_l': dict(scale=(1.0, 1.7, 1.0)),
                                                          'canopy': dict(rot=(10, 0, 0))}, 2),
    'w_scarecrow_king': ('king', 'b1', ((40, 26, 14), (150, 112, 60), (230, 200, 130)), (255, 160, 50),
                         {'crown'}, {'beard'}, 1.0, {'head': dict(scale=(1.15, 1.2, 1.15))}, 2),
    # a serpent: long neck and tail, wings shrunk to fins
    'w_reed_serpent': ('dragon', 'b6', ((20, 30, 12), (80, 110, 50), (200, 210, 140)), (200, 255, 120),
                       set(), set(), 0.75, {'right_wing': dict(scale=(0.3, 0.6, 0.3)), 'left_wing': dict(scale=(0.3, 0.6, 0.3)),
                                            'neck1': dict(scale=(0.8, 0.8, 1.45)), 'neck2': dict(scale=(0.95, 0.95, 1.3)),
                                            'tail1': dict(scale=(0.8, 0.8, 1.35)), 'body': dict(scale=(0.8, 0.85, 1.15))}, 2),
    # a long captain's coat
    'w_sunken_captain': ('king', 'b1', ((10, 26, 26), (40, 90, 84), (200, 180, 110)), (120, 255, 210),
                         {'helm'}, {'crown'}, 1.05, {'cape': dict(scale=(1.25, 1.35, 1.0))}, 2),
    'w_tide_singer': ('queen', 'b5', ((10, 30, 40), (40, 120, 140), (240, 200, 190)), (140, 255, 240),
                      {'shard_ring'}, {'scepter'}, 0.95, {}, 2),
}
IDS = tuple(LOOKS)


def _ramp(img, ramp):
    """Maps the shading of every opaque pixel onto the three-tone ramp (keeps the painted detail, changes colours)."""
    rgb = img[..., :3].astype(np.float32)
    alpha = img[..., 3] > 0
    lum = (0.299 * rgb[..., 0] + 0.587 * rgb[..., 1] + 0.114 * rgb[..., 2]) / 255.0
    lo, mid, hi = (np.array(c, np.float32) for c in ramp)
    t = np.clip(lum, 0.0, 1.0)[..., None]
    low = lo + (mid - lo) * np.clip(t / 0.5, 0, 1)
    high = mid + (hi - mid) * np.clip((t - 0.5) / 0.5, 0, 1)
    out = np.where(t < 0.5, low, high)
    img[..., :3] = np.where(alpha[..., None], out.clip(0, 255).astype(np.uint8), img[..., :3])


def _glow(img, color):
    """Turns every lit pixel of the glow layer into the boss's glow colour, keeping its brightness."""
    lit = img[..., :3].any(axis=2)
    bright = img[..., :3].max(axis=2).astype(np.float32)[..., None] / 255.0
    col = np.array(color, np.float32)[None, None, :]
    img[..., :3] = np.where(lit[..., None], (col * (0.45 + 0.55 * bright)).clip(0, 255).astype(np.uint8),
                            img[..., :3])


def _main_parts(rig, count=5):
    """The biggest always-visible parts (veins and cracks of the later forms go on them)."""
    sizes = []
    for name, part in rig.parts.items():
        if part.opt or not part.cubes:
            continue
        volume = sum(c.w * c.h * c.d for c in part.cubes)
        sizes.append((volume, name))
    return tuple(name for _, name in sorted(sizes, reverse=True)[:count])


def extend(rig):
    """Adds the looks of the field bosses built on {rig} (first look and every later form)."""
    for vid, (rig_name, base_id, ramp, glow, show, hide, size, poses, forms) in LOOKS.items():
        if rig_name != rig.name:
            continue
        base = next(v for v in rig.variants if v.id == base_id)
        for part in set(show) | set(hide) | set(poses):
            if part not in rig.parts:
                raise ValueError('%s: unknown part %s of the %s rig' % (vid, part, rig.name))
        parts = _main_parts(rig)
        scale = base.extra.get('scale', 1.0) * size
        w, h = base.extra.get('hitbox', (2.0, 5.0))
        hitbox = (round(w * size, 2), round(max(5.2, h * size), 2))
        for form in range(forms):
            look = vid if form == 0 else '%s_p%d' % (vid, form)

            def paint(p, base=base, ramp=ramp, glow=glow, form=form, parts=parts):
                if base.paint:
                    base.paint(p)
                _ramp(p.img, ramp)
                _glow(p.glow, glow)
                if form >= 1:
                    dark = tuple(int(c * 0.6) for c in glow)
                    boss_forms.veins(p, parts[:3], dark, glow, seed=form)
                    boss_forms.cracks(p, parts[:2], dark, glow, every=48 - 8 * form)
            merged = dict(base.poses)
            merged.update(poses)
            variant = ermodel.Variant(look, show=(set(base.show) | set(show)) - set(hide), hide=base.hide,
                                      poses=merged, palette=base.palette, paint=paint, glow=True,
                                      overrides=dict(base.overrides),
                                      extra=dict(base.extra, scale=round(scale * (1 + 0.04 * form), 3),
                                                 hitbox=hitbox) if form == 0 else dict(base.extra, scale=round(scale, 3)))
            rig.variants.append(variant)
            if base.id in rig.anim_sets:
                rig.anim_sets[look] = dict(rig.anim_sets[base.id])
