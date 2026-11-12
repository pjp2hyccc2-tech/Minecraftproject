"""The four legendary travellers on the hero rig (rig_hero.py; docs/modules/travellers.md). Original designs:

  traveller_rosalind  流離の魔女ロザリンド: a tall, graceful woman in her late twenties; long wavy wine-violet hair,
                      amber eyes under heavy lashes, a beauty mark, rose lips; a midnight-blue witch's hat with a
                      gold star band; a deep-blue dress with a starry hem, a side slit, a gold corset lacing and bell
                      sleeves; the armillary staff and a floating grimoire.
  traveller_siegwald  竜退けの騎士ジークヴァルト: a broad knight in his thirties; swept-back dark-gold hair, steel-grey
                      eyes, a scar across the left brow, stubble; dented steel plate over royal blue, a tabard torn by
                      three claw marks, a fur-collared crimson cape, the dragon-scale pauldron and the iron sword.
  traveller_kaylen    踏破の魔剣士ケイレン: a lean swordsman in his twenties; ash-white hair falling over one eye, a
                      long low tail tied with a red cord, one eye gold and one cyan; a deep-teal high-collared coat
                      with silver trim and uneven tails, black gloves, seven conquest seals, the rune longsword.
  traveller_mariel    救国のシスターマリエル: a gentle woman in her late twenties; long honey-blonde hair under a white
                      veil, soft blue drooping eyes, a calm smile; a white habit with gold embroidery and a pale-blue
                      sash, wide sleeves, the saint's staff with its teardrop crystal, a chained prayer book, a halo.
"""
from ermodel import Variant

BASE = {
    'skin': '#f2d2bc', 'fx': '#000000', 'hair': '#5a3a24', 'bangs': '#5a3a24', 'lock': '#5a3a24',
    'hair_long': '#5a3a24', 'tail': '#5a3a24', 'earring': {'base': '#d04070', 'style': 'gem'},
    'hat_brim': '#1c2040', 'hat': '#1c2040', 'charm': '#e0c060', 'charm_star': {'base': '#ffe080', 'style': 'gem'},
    'hood': '#f2efe8', 'veil': '#f2efe8', 'halo': '#f0d070',
    'staff': '#4a2e22', 'staff_foot': '#d8b04a', 'staff_collar': '#d8b04a', 'core': {'base': '#b0d8ff', 'style': 'glowcore'},
    'ring': '#e0c060', 'grip': '#3a2418', 'pommel': '#7a7f88', 'guard': '#8a8f98', 'blade': '#b8bec6',
    'grip_rune': '#1c1c24', 'pommel_gem': {'base': '#40e0e0', 'style': 'gem'}, 'guard_rune': '#c8ccd8',
    'rune_blade': '#9aa6b8', 'holy_pole': '#e8d080', 'sun_ring': '#f0d070', 'tear': {'base': '#a8f0ff', 'style': 'gem'},
    'ribbon': '#7ab0e0', 'sleeve2': '#6b4a35', 'gauntlet': '#8a9098', 'cuff': '#1c2040', 'bracer': '#3a2a20',
    'sleeve': '#6b4a35', 'pauldron': '#8a9098', 'dragon': {'base': '#7a1e1a', 'style': 'scale'},
    'horn': '#e8dcc0', 'pauldron_s': '#c8ccd8', 'phantom': '#80f0ff', 'tunic': '#6b4a35', 'bust': '#6b4a35',
    'cuirass': '#8a9098', 'collar': '#1e4a4c', 'mantle': '#8a7a6a', 'shawl': '#f2efe8', 'cape': '#8a1e24',
    'tome': '#4a1e3a', 'page': '#f4ecd8', 'pants': '#3a3040', 'boots': '#2a2026', 'greave': '#8a9098',
    'knee': '#8a9098', 'waist': '#6b4a35', 'dress': '#1c2448', 'dress2': '#1c2448', 'robe': '#f2efe8',
    'robe2': '#f2efe8', 'faulds': '#8a9098', 'tabard': '#24408a', 'coat_tail': '#1e4a4c', 'scabbard': '#1e1e26',
    'seal_a': {'base': '#e05050', 'style': 'gem'}, 'seal_b': {'base': '#50a0e0', 'style': 'gem'},
    'seal_c': {'base': '#e0c040', 'style': 'gem'}, 'seal_d': {'base': '#60d070', 'style': 'gem'},
    'seal_e': {'base': '#b070e0', 'style': 'gem'}, 'holybook': '#e8e0cc', 'chain': '#c8b070',
}

SLIM = {'r_upper_slim', 'r_fore_slim', 'l_upper_slim', 'l_fore_slim'}
STANDARD = {'r_upper', 'r_fore', 'l_upper', 'l_fore'}


def palette(**over):
    pal = dict(BASE)
    pal.update(over)
    return pal


def look(vid, show=(), slim=False, broad=False, poses=None, pal=None, paint=None, glow=True, scale=1.0,
         portrait=None):
    s = set(show)
    h = set()
    if slim:
        s |= SLIM
        h |= STANDARD
    if broad:
        s |= {'torso_broad'}
        h |= {'torso'}
        p = {'right_arm': dict(pos=(-1, 0, 0)), 'left_arm': dict(pos=(1, 0, 0))}
        p.update(poses or {})
        poses = p
    return Variant(vid, show=s, hide=h, poses=poses or {}, palette=pal or palette(), paint=paint, glow=glow,
                   extra=dict(scale=scale, portrait=portrait or {}))


# --------------------------------------------------------------------------------------------- the four

def rosalind():
    import hero_looks
    return look('traveller_rosalind', show={'hair_cap', 'bangs', 'lock_r', 'lock_l', 'hair_long', 'earring_r',
                                            'earring_l', 'hat_brim', 'bust', 'cuff_r', 'cuff_l', 'dress',
                                            'staff_witch', 'grimoire', 'shawl'},
                slim=True, pal=hero_looks.rosalind_palette(palette), paint=hero_looks.paint_rosalind,
                poses={'staff_witch': dict(rot=(30, 0, 0))},
                portrait=dict(bg=('#4a3a78', '#120e24'), dy=-0.8, zoom=0.8,
                              hide={'staff_witch', 'grimoire', 'hat_charm'}))


def siegwald():
    import hero_looks
    return look('traveller_siegwald', show={'hair_cap', 'cuirass', 'pauldron_r', 'pauldron_dragon',
                                            'gauntlet_r', 'gauntlet_l', 'mantle_fur', 'cape', 'faulds', 'tabard_f',
                                            'tabard_b', 'greave_r', 'greave_l', 'knee_r', 'knee_l', 'sword_iron'},
                broad=True, pal=hero_looks.siegwald_palette(palette), paint=hero_looks.paint_siegwald,
                scale=1.06,
                portrait=dict(bg=('#7a3a2a', '#1a0e0c'), dy=0.8, zoom=0.86, hide={'sword_iron'}))


def kaylen():
    import hero_looks
    return look('traveller_kaylen', show={'hair_cap', 'bangs', 'tail', 'collar_high', 'pauldron_s', 'bracer_r',
                                          'bracer_l', 'coat_tail_r', 'coat_tail_l', 'coat_flap_r', 'coat_flap_l',
                                          'scabbard', 'seals', 'sword_rune', 'phantoms'},
                pal=hero_looks.kaylen_palette(palette), paint=hero_looks.paint_kaylen,
                portrait=dict(bg=('#2a5a62', '#081418'), dy=0.8, zoom=0.86, hide={'sword_rune', 'phantoms'}))


def mariel():
    import hero_looks
    return look('traveller_mariel', show={'hair_cap', 'bangs', 'lock_r', 'lock_l', 'hood', 'veil', 'halo', 'bust',
                                          'cuff_r', 'cuff_l', 'robe', 'staff_holy', 'holybook'},
                slim=True, pal=hero_looks.mariel_palette(palette), paint=hero_looks.paint_mariel, scale=0.98,
                poses={'staff_holy': dict(rot=(30, 0, 0)), 'bangs': dict(pos=(0, 0, 0.3)),
                       'lock_r': dict(pos=(-0.55, 0, -0.35)), 'lock_l': dict(pos=(0.55, 0, -0.35))},
                portrait=dict(bg=('#d8c890', '#3a3020'), dy=0.2, zoom=0.84, hide={'staff_holy'}))


def looks():
    return [rosalind(), siegwald(), kaylen(), mariel()]
