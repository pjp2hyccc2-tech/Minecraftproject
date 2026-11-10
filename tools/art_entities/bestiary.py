"""The bestiary of the field map (2026-11): 100 new field monsters (10 per field) and 30 field lords (ヌシ, 3 per
field), each with its own look on one of the four enemy rigs and its own behaviour.

Single source for both sides:
  * the looks: rig_*.variants() append variants_for('<rig>'), rig_*.rig() calls snap_to_ground(); textures, model
    tables and RigData are then written by generate.py like those of every other enemy
  * the game data: bestiary_gen.py writes the generated blocks of Monsters, DropTables, MonsterBodies, EnemyVariant and
    FieldThreats, and the names in docs/i18n (world.json, entity.json, field_threats.json)

Looks are built from each rig's optional parts (hoods, helmets, weapons, robes, manes, pincers, canopies, wings...),
palettes with material styles, per-variant rest poses (part scale, angle and offset) and painted details (faces,
emblems, glowing eyes and runes), so that every monster has a silhouette and colours of its own.
"""
from ermodel import Variant
from erpaint import human_face, lighten, rgb, shade

# ================================================================================================== the spec

# field -> (slug, area materials (signature, other), equipment tier, level range)
FIELDS = {
    1: ('meadow', ('WOLF_FANG', 'SLIME_GEL'), 1, (5, 15)),
    2: ('moon_forest', ('TREANT_BARK', 'FOREST_SPORE'), 2, (10, 20)),
    3: ('iron_mine', ('DESERT_CRYSTAL', 'SCARAB_SHELL'), 3, (15, 25)),
    4: ('sacred_falls', ('FROST_SHARD', 'YETI_FUR'), 4, (20, 30)),
    5: ('ancient_ruins', ('MAGMA_CORE', 'DRAKE_SCALE'), 5, (30, 40)),
    6: ('soaring_peaks', ('SKY_FEATHER', 'STAR_FRAGMENT'), 6, (40, 50)),
    7: ('end_forest', ('VOID_ESSENCE', 'ABYSS_CRYSTAL'), 7, (50, 50)),
    8: ('farm', ('TREANT_BARK', 'FOREST_SPORE'), 2, (10, 20)),
    9: ('lakeside', ('SCARAB_SHELL', 'DESERT_CRYSTAL'), 3, (15, 25)),
    10: ('harbour', ('YETI_FUR', 'FROST_SHARD'), 4, (20, 30)),
}

SPEC = []


def M(mid, field, rig, lv, ja, en, art, tune=(1.0, 1.0, 1.0, 1.0), bolt=None, particle=None, chance=0.0,
      sound=None, fire=False, lord=None, weight=10, respawn=(10, 14), element=None, moves=None):
    """One monster. lord: (lord id, x, z, territory) of a field lord (an ELITE spawned by the field threats only).
    element: colour and after-effect of its special moves' markers (FIRE, FROST, DARK, MAGIC, HOLY; by default from
    its bolt, else DANGER); moves: its three special moves, signature first (by default from its body)."""
    if lord:
        top = FIELDS[field][3][1]
        lv = (max(FIELDS[field][3][0], top - 2), top)
    SPEC.append(dict(id=mid, field=field, rig=rig, lv=lv, ja=ja, en=en, art=art, tune=tune, bolt=bolt,
                     particle=particle, chance=chance, sound=sound, fire=fire, lord=lord, weight=weight,
                     respawn=respawn, element=element, moves=moves))


# explicit elements of melee monsters whose look says it (the others follow their bolts or stay physical)
ELEMENTS = {
    'FROST': ('f4_crystal_crab', 'f4_snow_ape', 'f6_frost_titanling', 'lord_goura', 'lord_lunar', 'lord_mistweaver',
              'f6_peak_wolf'),
    'FIRE': ('f5_ash_hound', 'f5_magma_lizard', 'f5_lava_tortoise', 'f5_ruin_sentinel', 'f3_ore_golem', 'lord_valka',
             'lord_agnis', 'lord_durn'),
    'DARK': ('f7_void_hound', 'f7_doom_knight', 'f7_shadow_assassin', 'f7_end_scorpion', 'f7_amethyst_colossus',
             'f7_blighted_treant', 'f7_plague_rat', 'lord_black_knight', 'lord_nihil', 'f2_night_stalker',
             'f3_dust_mummy', 'f9_bog_ghoul', 'f10_ghost_sailor', 'f9_fisher_ghost', 'lord_drowned_fisher'),
    'HOLY': ('lord_alvin', 'f6_sky_knight', 'f6_star_sentinel', 'f4_moss_guardian'),
    'MAGIC': ('f6_cloud_giant', 'lord_dolga', 'f2_moonshade_wolf', 'f2_twin_tailed_fox', 'lord_numel', 'f10_coral_golem'),
}


# ---------------------------------------------------------------------------------------- 1 はじまりの草原 (5-15)
M('f1_horned_hare', 1, 'beast', (5, 9), 'ツノウサギ', 'Horned Hare',
  dict(kind='hare', body='#d8ccb8', ear='#e4d8c4', horn='#f0d890', eye=(60, 20, 30), belly='#f4eee4', scale=0.55),
  tune=(0.7, 0.8, 0.6, 1.2), sound='cave_rat', weight=14)
M('f1_wild_boar', 1, 'beast', (6, 11), '荒野のイノシシ', 'Wild Boar',
  dict(kind='boar', body='#5a4434', mane='#3a2a20', horn='#efe6d0', eye=(30, 15, 10), scale=0.95),
  tune=(1.25, 1.0, 1.1, 1.0), sound='wolf', weight=12)
M('f1_bandit_scout', 1, 'humanoid', (6, 12), '山賊の斥候', 'Bandit Scout',
  dict(show={'hood', 'dagger'}, hood='#4a5230', tunic='#5c6a3a', pants='#3e3a2a', face='masked', mask=(70, 60, 40),
       skin='#c49478', boots='#3a2a1e'), sound='bandit', weight=12)
M('f1_goblin_pilferer', 1, 'humanoid', (5, 10), '小鬼の盗人', 'Goblin Pilferer',
  dict(show={'dagger', 'claws_l'}, skin='#7aa050', tunic='#6a4a2a', sleeve2={'base': '#7aa050', 'style': 'skin'},
       pants='#4a3420', face='monster', eye=(255, 220, 60), scale=0.72, imp=True, ears=True, hand='#7aa050'),
  tune=(0.8, 0.9, 0.8, 1.15), sound='bandit')
M('f1_meadow_wisp', 1, 'flyer', (8, 13), '野原の灯火', 'Meadow Wisp',
  dict(kind='wisp', base='#e8f0a0', style='cloud', core='#f8ffc0', eye=(120, 160, 40), glow=(220, 255, 120),
       scale=0.75), tune=(0.75, 1.0, 0.6, 1.05), bolt='NATURE', particle='SPORE_BLOSSOM_AIR', chance=0.15,
  sound='ice_wraith', weight=8)
M('f1_stray_sheepdog', 1, 'beast', (7, 12), 'はぐれ牧羊犬', 'Stray Sheepdog',
  dict(kind='wolf', body='#2a2626', head='#f0ece4', mane='#f4f0ea', tail='#2a2626', tail2='#f0ece4',
       leg='#f0ece4', eye=(60, 40, 20), belly='#f4f0ea', scale=0.9), sound='wolf')
M('f1_living_scarecrow', 1, 'humanoid', (9, 14), '動く案山子', 'Living Scarecrow',
  dict(show={'hood', 'club'}, hood={'base': '#a88848', 'style': 'rope'}, skin='#d8c080', tunic='#7a5a8a',
       pants='#5a6a8a', face='stitched', eye=(40, 30, 20), straw=True, hand='#d8c080', club='#6b4c2a'),
  tune=(1.0, 1.0, 0.9, 0.95), sound='prisoner', weight=9)
M('f1_bandit_crossbowman', 1, 'humanoid', (8, 14), '山賊の弩手', 'Bandit Crossbowman',
  dict(show={'hood', 'crossbow'}, hood='#6a3020', tunic='#7a4a30', pants='#3e2a20', skin='#c08a6a', shoot=True,
       face='human', beard=(70, 45, 30)), tune=(0.85, 1.05, 0.8, 1.0), bolt='ARROW', sound='bandit', weight=9)
M('f1_capling', 1, 'humanoid', (7, 12), 'キノコ帽子', 'Capling',
  dict(show={'leaf_crown'}, crown={'base': '#c83a2a', 'style': 'skin'}, skin='#f0e4cc', tunic='#e8dcc4',
       pants='#c8b8a0', boots='#a89880', face='cute', eye=(30, 20, 20), scale=0.66, cap=True, dots=True),
  tune=(0.8, 0.85, 0.7, 1.05), sound='dryad', weight=11)
M('f1_fallen_squire', 1, 'humanoid', (11, 15), '落ちぶれ従士', 'Fallen Squire',
  dict(show={'helmet', 'blade', 'shield_l'}, helm={'base': '#8a6a50', 'style': 'metal'}, tunic='#6a5a4a',
       armor='#7a6048', shield='#7a5a40', skin='#b08870', face='helm_open', beard=(80, 60, 40)),
  tune=(1.15, 1.05, 1.3, 0.95), sound='jailer', weight=7)
M('lord_garm', 1, 'humanoid', None, '街道荒らしのガルム', 'Garm the Highway Scourge',
  dict(show={'hood', 'greatblade', 'cape', 'pauldron_r', 'pauldron_l'}, hood='#7a1e1a', tunic='#5a3a28',
       cape='#8a2420', armor={'base': '#4a4a50', 'style': 'metal'}, skin='#b88468', beard=(60, 40, 30), scale=1.35,
       face='human', eye=(200, 40, 30), glow=(255, 60, 40), blade='#b8bcc4', guard='#8a2420', scar=True),
  lord=('garm', -468, 564, 28), sound='jailer', respawn=(9, 13))
M('lord_hook', 1, 'humanoid', None, '赤目のフック', 'Red-Eyed Hook',
  dict(show={'hood', 'crossbow', 'cape'}, hood='#3a1a18', tunic='#4a2a24', cape='#2a1412', face='shadow',
       eye=(255, 40, 30), scale=1.25, shoot=True), bolt='ARROW', lord=('hook', -676, 916, 32), sound='bandit',
  respawn=(7, 11))
M('lord_durn', 1, 'beast', None, '大猪ドルン', 'Durn the Great Boar',
  dict(kind='boar', body='#3a2a22', mane='#1e1612', horn='#f4ead4', eye=(255, 60, 30), glow=(255, 40, 20),
       scale=1.7, spines={'base': '#5a3a28', 'style': 'bone'}), lord=('durn', -652, 772, 34), sound='wolf')

# ---------------------------------------------------------------------------------------- 2 月影の森 (10-20)
M('f2_moonshade_wolf', 2, 'beast', (10, 15), '月影オオカミ', 'Moonshade Wolf',
  dict(kind='wolf', body='#6a7890', mane='#a8b8d0', eye=(120, 230, 255), glow=(120, 220, 255), belly='#c8d4e8',
       scale=1.0), sound='wolf', weight=13)
M('f2_twin_tailed_fox', 2, 'beast', (11, 17), '双尾の狐', 'Twin-Tailed Fox',
  dict(kind='fox', body='#d06a28', mane='#f4ece0', tail='#d06a28', tail2='#f4ece0', ear='#2a1a14', leg='#2a1a14',
       eye=(255, 200, 60), glow=(255, 190, 60), belly='#f4ece0', scale=0.75), tune=(0.85, 1.0, 0.8, 1.15),
  sound='wolf')
M('f2_thorn_dryad', 2, 'humanoid', (13, 19), '茨のドライアド', 'Thorn Dryad',
  dict(show={'leaf_crown', 'robe', 'claws_r', 'claws_l'}, skin='#7a9a5a',
       robe={'base': '#3a5a2a', 'style': 'leaf', 'flowers': (180, 60, 160)},
       crown={'base': '#3a5a2a', 'flowers': (180, 60, 160)}, bark='#4a3424', face='human', eye=(200, 60, 200),
       glow=(220, 90, 220), claw='#4a3424'), bolt='NATURE', sound='dryad')
M('f2_moon_moth', 2, 'flyer', (12, 18), '月蛾の妖精', 'Moon Moth Fairy',
  dict(kind='moth', body='#b8a8d8', wing={'base': '#c8b8e8', 'style': 'membrane'}, face='#e8e0f8',
       eye=(80, 40, 160), glow=(200, 160, 255), scale=0.8), bolt='NATURE', particle='END_ROD', chance=0.05,
  sound='ice_wraith', weight=9)
M('f2_fungal_brute', 2, 'golem', (14, 20), '茸の巨人', 'Fungal Brute',
  dict(show={'canopy', 'moss_beard'}, mat={'base': '#c8b8a0', 'style': 'skin'},
       canopy={'base': '#b8302a', 'style': 'skin'}, moss={'base': '#e8e0d0', 'style': 'leaf'}, eye=(40, 30, 20),
       scale=1.0, dots=True), tune=(1.3, 1.0, 1.1, 0.9), sound='treant', weight=8)
M('f2_owl_warden', 2, 'flyer', (13, 19), '梟の守り神', 'Owl Warden',
  dict(kind='bird', body='#8a6a48', wing={'base': '#7a5a3a', 'style': 'feather', 'tip': (230, 220, 200)},
       crest='#5a4028', face='#d8c8a8', eye=(255, 200, 40), glow=(255, 190, 30), beak=(80, 70, 60), owl=True,
       scale=0.95), sound='harpy')
M('f2_night_stalker', 2, 'humanoid', (12, 18), '宵闇の追跡者', 'Night Stalker',
  dict(show={'hood', 'dagger', 'cape'}, hood='#1e2a44', tunic='#26304a', cape='#141c30', pants='#1a2030',
       face='shadow', eye=(160, 200, 255)), tune=(0.9, 1.1, 0.8, 1.1), sound='bandit')
M('f2_bark_lizard', 2, 'beast', (10, 16), '樹皮トカゲ', 'Bark Lizard',
  dict(kind='lizard', body={'base': '#5a4630', 'style': 'bark'}, crest={'base': '#4f8a3a', 'style': 'leaf'},
       eye=(220, 200, 60), scale=0.95), sound='salamander', weight=12)
M('f2_moonbow_hunter', 2, 'humanoid', (14, 20), '月弓の狩人', 'Moonbow Hunter',
  dict(show={'hood', 'crossbow'}, hood='#c8d0e0', tunic='#8a98b0', pants='#4a5468', skin='#d8c0a8',
       face='human', eye=(80, 160, 230), shoot=True, stock='#c8d0e0', limb='#8a98b0'), bolt='ARROW', sound='bandit')
M('f2_will_o_wisp', 2, 'flyer', (15, 20), '迷い火', 'Will-o\'-Wisp',
  dict(kind='wisp', base='#60a8ff', style='flame', core='#d8f0ff', scale=0.7, eye=(255, 255, 255),
       glow=(200, 240, 255)), tune=(0.7, 1.1, 0.5, 1.1),
  bolt='CURSE', particle='SOUL_FIRE_FLAME', chance=0.25, sound='fire_spirit', fire=True, weight=7)
M('lord_nebula', 2, 'humanoid', None, '月影の古老ネブラ', 'Nebula, Elder of the Moonshade',
  dict(show={'leaf_crown', 'robe', 'staff'}, skin='#8aa070',
       robe={'base': '#2a4a5a', 'style': 'leaf', 'flowers': (200, 220, 255)},
       crown={'base': '#3a6a5a', 'flowers': (200, 220, 255)}, bark='#4a3a2a', face='human', beard=(200, 210, 200),
       eye=(120, 220, 255), glow=(140, 230, 255), orb=(160, 230, 255), staff='#4a3a2a', scale=1.4),
  bolt='NATURE', lord=('nebula', -908, -284, 26), sound='dryad', respawn=(9, 13))
M('lord_belladonna', 2, 'humanoid', None, '宵闇の魔女ベラドンナ', 'Belladonna the Dusk Witch',
  dict(show={'hood', 'robe', 'staff'}, hood={'base': '#3a1a4a', 'trim': '#c070e0'},
       robe={'base': '#2a1238', 'trim': '#c070e0'}, tunic='#3a1a4a', face='human', skin='#d8c0d0',
       eye=(200, 60, 220), glow=(220, 90, 255), orb=(220, 90, 255), staff='#2a1a2a', witch_hat=True, scale=1.3),
  bolt='CURSE', lord=('belladonna', -588, -4, 30), sound='cultist', respawn=(7, 11))
M('lord_lunar', 2, 'beast', None, '白銀の狼王ルナール', 'Lunar the Silver Wolf King',
  dict(kind='wolf', body='#c8d0dc', mane='#f4f8ff', eye=(120, 230, 255), glow=(140, 230, 255), scale=1.75,
       spines={'base': '#e0f0ff', 'style': 'crystal'}, belly='#ffffff'), lord=('lunar', -804, -396, 30),
  sound='wolf')

# ---------------------------------------------------------------------------------------- 3 鉄鉱の鉱山 (15-25)
M('f3_kobold_miner', 3, 'humanoid', (15, 20), '坑道のコボルト', 'Kobold Miner',
  dict(show={'helmet', 'club'}, helm={'base': '#c8a040', 'style': 'metal'}, skin='#8a6a50', tunic='#5a4a3a',
       pants='#3a3028', face='monster', eye=(255, 200, 60), scale=0.8, imp=True, lamp=True, hand='#8a6a50',
       club='#5b5b62'), tune=(0.9, 1.0, 1.0, 1.05), sound='prisoner', weight=13)
M('f3_rock_crab', 3, 'beast', (15, 21), '岩ガニ', 'Rock Crab',
  dict(kind='crab', body={'base': '#6a6a6e', 'style': 'stone'}, claw={'base': '#5a5a5e', 'style': 'stone'},
       eye=(30, 30, 30), scale=0.95), tune=(1.2, 1.0, 1.5, 0.9), sound='scorpion', weight=12)
M('f3_ore_golem', 3, 'golem', (17, 23), '鉱石ゴーレム', 'Ore Golem',
  dict(show={'shoulder_r', 'shoulder_l'}, mat={'base': '#5a5650', 'style': 'stone'}, eye=(255, 170, 60),
       glow=(255, 150, 40), ore=(255, 150, 50), scale=1.05), tune=(1.4, 1.0, 1.4, 0.85), sound='sand_golem',
  weight=8)
M('f3_dust_mummy', 3, 'humanoid', (16, 22), '砂塵のミイラ', 'Dust Mummy',
  dict(show={'mummy_strips', 'chest_strips', 'hood'}, wrap='#a89878', hood={'base': '#8a6a40', 'style': 'cloth'},
       face='wrapped', eye=(80, 220, 255), glow=(80, 210, 255), arms_out=True), sound='mummy')
M('f3_giant_cave_bat', 3, 'flyer', (15, 21), '洞窟オオコウモリ', 'Giant Cave Bat',
  dict(kind='bat', body='#3a2a2a', wing={'base': '#4a3434', 'style': 'membrane'}, face='#4a3434',
       ear='#5a4040', eye=(255, 80, 60), glow=(255, 60, 40), scale=0.75), tune=(0.75, 1.0, 0.6, 1.2),
  sound='harpy', weight=12)
M('f3_sun_cultist', 3, 'humanoid', (18, 24), '太陽教団の祭司', 'Sun Cultist',
  dict(show={'hood', 'robe', 'staff'}, hood={'base': '#c88a20', 'trim': '#fff0a0'},
       robe={'base': '#a86818', 'trim': '#fff0a0'}, tunic='#c88a20', face='shadow', eye=(255, 220, 80),
       orb=(255, 200, 60), emblem=(255, 220, 90), staff='#8a6a30'), bolt='FIRE', sound='cultist', weight=8)
M('f3_golden_scarab', 3, 'beast', (17, 23), '黄金スカラベ', 'Golden Scarab',
  dict(kind='beetle', body={'base': '#2a5a4a', 'style': 'chitin'}, shell={'base': '#c8a030', 'style': 'gold'},
       horn='#d8b040', eye=(20, 20, 20), scale=0.85), tune=(1.0, 1.0, 1.6, 0.95), sound='scorpion', weight=9)
M('f3_tunnel_rat', 3, 'beast', (15, 20), '坑道オオネズミ', 'Tunnel Rat',
  dict(kind='rat', body='#5a5248', ear='#a07878', tail={'base': '#c8a0a0', 'style': 'skin'},
       eye=(255, 60, 40), glow=(255, 40, 30), scale=0.78), tune=(0.8, 0.95, 0.7, 1.15), sound='cave_rat',
  weight=13)
M('f3_rust_guard', 3, 'humanoid', (19, 25), '錆びた守衛', 'Rust Guard',
  dict(show={'helmet', 'blade', 'shield_l', 'pauldron_r', 'pauldron_l'}, helm={'base': '#8a5a3a', 'style': 'metal'},
       armor={'base': '#7a5038', 'style': 'metal'}, tunic={'base': '#6a4a34', 'style': 'metal'}, shield='#6a4a30',
       blade='#9a7a60', face='helm', eye=(255, 140, 60), glow=(255, 120, 40), rust=True),
  tune=(1.2, 1.0, 1.4, 0.9), sound='dark_knight', weight=7)
M('f3_gem_wraith', 3, 'flyer', (20, 25), '宝石の怨霊', 'Gem Wraith',
  dict(kind='wraith', base='#a07ab8', crown={'base': '#f070c0', 'style': 'crystal'}, eye=(255, 140, 220),
       glow=(255, 120, 220)), bolt='VOID', particle='WITCH', chance=0.08, sound='ice_wraith', weight=6)
M('lord_dolga', 3, 'golem', None, '坑道の墓守ドルガ', 'Dolga, Warden of the Shafts',
  dict(show={'headdress', 'shoulder_r', 'shoulder_l', 'core'}, mat={'base': '#c8b080', 'style': 'sandstone'},
       nemes={'base': '#3a6ab0', 'style': 'gold'}, core='#60d0ff', eye=(60, 200, 255), glow=(60, 200, 255),
       runes=(60, 200, 255), scale=1.6), lord=('dolga', -1212, 524, 24), sound='sand_golem', respawn=(10, 14))
M('lord_rustbone', 3, 'humanoid', None, '錆骨の射手', 'The Rustbone Archer',
  dict(show={'crossbow', 'cape', 'pauldron_r'}, skin={'base': '#d8ccb0', 'style': 'bone'},
       tunic={'base': '#d8ccb0', 'style': 'bone'}, pants={'base': '#d8ccb0', 'style': 'bone'},
       armor={'base': '#8a5a3a', 'style': 'metal'}, cape='#4a3a2a', face='skull', eye=(255, 160, 60),
       glow=(255, 140, 40), scale=1.3, shoot=True, bones=True), bolt='ARROW', lord=('rustbone', -1196, 804, 30),
  sound='dark_knight', respawn=(8, 12))
M('lord_vein_eater', 3, 'beast', None, '鉱脈喰らいの大ムカデ', 'The Vein-Eater',
  dict(kind='centipede', body={'base': '#7a3a24', 'style': 'chitin'}, claw={'base': '#c8a040', 'style': 'gold'},
       eye=(255, 200, 60), glow=(255, 180, 40), scale=1.7, ore=(255, 200, 80)),
  lord=('vein_eater', -1372, 660, 26), sound='scorpion')

# ---------------------------------------------------------------------------------------- 4 聖なる滝 (20-30)
M('f4_mist_spirit', 4, 'flyer', (20, 26), '霧の精', 'Mist Spirit',
  dict(kind='spirit', base='#e8f0f8', style='cloud', core='#ffffff', eye=(80, 140, 200), glow=(160, 220, 255),
       veil=True, scale=0.9), bolt='FROST', particle='CLOUD', chance=0.08, sound='ice_wraith', weight=9)
M('f4_falls_toad', 4, 'beast', (20, 25), '滝ガエル', 'Falls Toad',
  dict(kind='toad', body={'base': '#3a7a6a', 'style': 'skin'}, crest={'base': '#e8d040', 'style': 'skin'},
       belly='#e8e0a0', eye=(255, 200, 40), scale=0.9), tune=(1.1, 1.0, 0.9, 1.0), sound='salamander', weight=13)
M('f4_kappa', 4, 'humanoid', (21, 27), '河童', 'Kappa',
  dict(show={'cape', 'claws_r', 'claws_l'}, skin='#6a9a5a', tunic={'base': '#6a9a5a', 'style': 'skin'},
       cape={'base': '#5a6a3a', 'style': 'chitin'}, face='beak', eye=(30, 20, 20), scale=0.85, plate=True,
       hand='#6a9a5a', claw='#e8e0c0'), tune=(1.0, 1.0, 1.1, 1.05), sound='ghoul', weight=11)
M('f4_lost_pilgrim', 4, 'humanoid', (22, 28), '迷える巡礼者', 'Lost Pilgrim',
  dict(show={'hood', 'robe', 'staff'}, hood='#e8e4d8', robe='#d8d4c8', tunic='#e8e4d8', skin='#9aa890',
       face='ghoul', eye=(160, 220, 255), glow=(140, 210, 255), orb=(200, 230, 255), staff='#8a7a60'),
  bolt='FROST', sound='ghoul', weight=9)
M('f4_frost_heron', 4, 'flyer', (21, 27), '氷鷺', 'Frost Heron',
  dict(kind='bird', body='#e8f0f8', wing={'base': '#d8e8f4', 'style': 'feather', 'tip': (120, 160, 200)},
       crest='#a0c8e8', face='#f4f8ff', eye=(40, 80, 140), beak=(230, 190, 60), long_neck=True, scale=1.0),
  bolt='FROST', sound='harpy')
M('f4_crystal_crab', 4, 'beast', (23, 29), '氷晶ガニ', 'Crystal Crab',
  dict(kind='crab', body={'base': '#9cc8e4', 'style': 'ice'}, claw={'base': '#c8f0ff', 'style': 'crystal'},
       eye=(40, 60, 100), scale=1.0), tune=(1.2, 1.0, 1.6, 0.9), sound='scorpion', weight=11)
M('f4_frost_monk', 4, 'humanoid', (24, 30), '氷の修行僧', 'Frost Monk',
  dict(show={'hood', 'robe', 'staff'}, hood={'base': '#a8c8e0', 'trim': '#ffffff'},
       robe={'base': '#88a8c8', 'trim': '#ffffff'}, tunic='#a8c8e0', skin='#d8c8c0', face='human',
       eye=(80, 180, 255), glow=(100, 200, 255), orb=(160, 230, 255), staff='#d0e4f0'), bolt='FROST',
  sound='frost_mage', weight=8)
M('f4_moss_guardian', 4, 'golem', (24, 30), '苔むした守護像', 'Moss Guardian',
  dict(show={'moss_beard', 'shoulder_r', 'shoulder_l', 'rune_halo'}, mat={'base': '#7a8478', 'style': 'stone'},
       moss={'base': '#4f7a30', 'style': 'leaf'}, eye=(120, 255, 140), glow=(100, 255, 120), runes=(100, 255, 120),
       mossy=True, scale=1.15), tune=(1.4, 1.0, 1.3, 0.85), sound='ruin_construct', weight=7)
M('f4_snow_ape', 4, 'golem', (22, 28), '雪ザル', 'Snow Ape',
  dict(show=set(), mat={'base': '#e8ecf0', 'style': 'fur'}, face_col='#c89a8a', eye=(40, 20, 20), scale=0.85,
       ape=True), tune=(1.1, 1.1, 0.9, 1.1), sound='ice_golem', weight=10)
M('f4_spray_wraith', 4, 'flyer', (25, 30), '飛沫の亡霊', 'Spray Wraith',
  dict(kind='wraith', base='#7ac0c8', crown={'base': '#c8f8ff', 'style': 'ice'}, eye=(200, 255, 255),
       glow=(160, 255, 255)), bolt='FROST', particle='DRIPPING_WATER', chance=0.15, sound='ice_wraith', weight=7)
M('lord_goura', 4, 'golem', None, '滝壺の暴君ゴウラ', 'Goura, Tyrant of the Plunge Pool',
  dict(show={'ice_crown', 'crystal_r', 'crystal_l', 'shoulder_r', 'shoulder_l'}, mat={'base': '#7aa8c8', 'style': 'ice'},
       ice={'base': '#c8f0ff', 'style': 'crystal'}, eye=(255, 255, 255), glow=(160, 230, 255), ape=True,
       face_col='#5a7a98', scale=1.65), lord=('goura', 556, -148, 28), sound='ice_golem', respawn=(10, 14))
M('lord_mistweaver', 4, 'beast', None, '霧糸の大蜘蛛', 'The Mistweaver',
  dict(kind='spider', body={'base': '#d8e0e8', 'style': 'fur'}, shell={'base': '#b8c8d8', 'style': 'fur'},
       eye=(120, 220, 255), glow=(140, 230, 255), scale=1.5), lord=('mistweaver', 412, -644, 26),
  sound='scorpion', respawn=(8, 12))
M('lord_ord', 4, 'beast', None, '滝守の大亀オルド', 'Ord the Falls Tortoise',
  dict(kind='tortoise', body={'base': '#5a7a5a', 'style': 'scale'}, shell={'base': '#4a5a3a', 'style': 'stone'},
       eye=(255, 220, 120), glow=(255, 220, 120), mossy=True, scale=1.8), tune=(1.2, 1.0, 1.6, 0.8),
  lord=('ord', 380, -492, 26), sound='salamander')

# ---------------------------------------------------------------------------------------- 5 古代遺跡 (30-40)
M('f5_ember_imp', 5, 'humanoid', (30, 35), '熾火の小悪魔', 'Ember Imp',
  dict(show={'horn_r', 'horn_l', 'tail', 'claws_r', 'claws_l', 'hand_flame'}, skin='#e07a2a',
       tunic={'base': '#e07a2a', 'style': 'skin'}, pants='#4a2a18', horn='#3a2014', tail='#e07a2a',
       claw='#3a2014', face='imp', eye=(255, 255, 120), scale=0.7, imp=True), tune=(0.8, 1.1, 0.7, 1.15),
  bolt='FIRE', particle='FLAME', chance=0.15, sound='fire_imp', fire=True, weight=12)
M('f5_ash_hound', 5, 'beast', (31, 37), '灰の猟犬', 'Ash Hound',
  dict(kind='hound', body='#5a5452', mane={'base': '#3a3432', 'style': 'fur'},
       spines={'base': '#ff8030', 'style': 'flame'}, eye=(255, 140, 40), glow=(255, 120, 30), scale=1.1),
  particle='SMOKE', chance=0.1, sound='wolf', weight=12)
M('f5_magma_lizard', 5, 'beast', (32, 38), '溶岩トカゲ', 'Magma Lizard',
  dict(kind='lizard', body={'base': '#3a2a26', 'style': 'magma', 'hot': (255, 120, 30)},
       crest={'base': '#ff9030', 'style': 'flame'}, eye=(255, 200, 60), glow=(255, 160, 40), scale=1.1),
  particle='LAVA', chance=0.03, sound='salamander', fire=True, weight=11)
M('f5_ruin_sentinel', 5, 'golem', (33, 39), '遺跡の番兵', 'Ruin Sentinel',
  dict(show={'shoulder_r', 'shoulder_l', 'core'}, mat={'base': '#c8a878', 'style': 'sandstone'},
       plate={'base': '#c8a040', 'style': 'gold'}, core='#ffb040', eye=(255, 190, 60), glow=(255, 170, 40),
       runes=(255, 190, 60), scale=1.15), tune=(1.4, 1.0, 1.4, 0.85), sound='ruin_construct', weight=7)
M('f5_cinder_wraith', 5, 'flyer', (32, 38), '燃え殻の亡霊', 'Cinder Wraith',
  dict(kind='wraith', base='#4a3430', crown={'base': '#ff7a20', 'style': 'flame'}, eye=(255, 200, 120),
       glow=(255, 140, 40), embers=True, scale=0.95), bolt='FIRE',
  particle='SMOKE', chance=0.2, sound='fire_spirit', fire=True, weight=9)
M('f5_relic_thief', 5, 'humanoid', (30, 36), '遺物泥棒', 'Relic Thief',
  dict(show={'hood', 'dagger', 'claws_l'}, hood='#c8a878', tunic='#a8885a', pants='#6a5a40', skin='#b08060',
       face='masked', mask=(90, 70, 50), claw='#c8c0b0'), tune=(0.9, 1.1, 0.8, 1.15), sound='bandit', weight=11)
M('f5_flame_priest', 5, 'humanoid', (34, 40), '炎の神官', 'Flame Priest',
  dict(show={'hood', 'robe', 'staff'}, hood={'base': '#8a1a14', 'trim': '#f0c040'},
       robe={'base': '#6a1410', 'trim': '#f0c040'}, tunic='#8a1a14', face='shadow', eye=(255, 160, 40),
       orb=(255, 120, 30), emblem=(255, 160, 40), staff='#3a2a20'), bolt='FIRE', particle='FLAME', chance=0.05,
  sound='cultist', fire=True, weight=8)
M('f5_obsidian_guard', 5, 'golem', (35, 40), '黒曜の衛兵', 'Obsidian Guard',
  dict(show={'back_spikes', 'shoulder_r', 'shoulder_l'}, mat={'base': '#2a1e30', 'style': 'crystal'},
       spike={'base': '#4a3060', 'style': 'crystal'}, eye=(200, 120, 255), glow=(190, 100, 255), scale=1.1),
  tune=(1.3, 1.1, 1.6, 0.85), sound='lava_golem', weight=7)
M('f5_scorch_vulture', 5, 'flyer', (31, 37), '焦げハゲワシ', 'Scorch Vulture',
  dict(kind='bird', body='#2a1e1a', wing={'base': '#3a2620', 'style': 'feather', 'tip': (255, 120, 40)},
       crest='#c83a20', face='#c87a6a', eye=(255, 220, 60), beak=(200, 180, 150), scale=1.05), sound='harpy',
  weight=11)
M('f5_lava_tortoise', 5, 'beast', (33, 39), '溶岩ガメ', 'Lava Tortoise',
  dict(kind='tortoise', body={'base': '#4a3a30', 'style': 'scale'},
       shell={'base': '#3a2a26', 'style': 'magma', 'hot': (255, 120, 30)}, eye=(255, 160, 40), glow=(255, 140, 30),
       scale=1.15), tune=(1.4, 1.0, 1.6, 0.8), sound='salamander', fire=True, weight=8)
M('lord_valka', 5, 'humanoid', None, '遺跡の狂戦士ヴァルカ', 'Valka the Ruin Berserker',
  dict(show={'horn_r', 'horn_l', 'greatblade', 'pauldron_r', 'pauldron_l', 'cape'}, skin='#a85a3a',
       tunic={'base': '#a85a3a', 'style': 'skin'}, armor={'base': '#4a3a30', 'style': 'metal'}, cape='#6a1a10',
       pants='#3a2a20', horn='#e8dcc0', face='imp', eye=(255, 220, 80), scale=1.45, warpaint=True,
       blade='#8a8a90', guard='#c8a040'), lord=('valka', 1052, -44, 28), sound='dark_knight', respawn=(11, 15))
M('lord_scorch_eye', 5, 'flyer', None, '灼眼のブレイズ', 'Scorch-Eye',
  dict(kind='flame', base='#ff7020', core='#fff0a0', scale=1.5, eye=(255, 255, 220), cyclops=True), bolt='FIRE',
  particle='FLAME', chance=0.3, lord=('scorch_eye', 948, -196, 26), sound='fire_spirit', fire=True,
  respawn=(9, 13))
M('lord_agnis', 5, 'golem', None, '遺跡の神像アグニス', 'Agnis the Ruin Idol',
  dict(show={'headdress', 'shoulder_r', 'shoulder_l', 'core', 'rune_halo'}, mat={'base': '#d8b070', 'style': 'gold'},
       nemes={'base': '#a82a20', 'style': 'gold'}, core='#ff8030', eye=(255, 140, 40), glow=(255, 120, 30),
       runes=(255, 160, 50), scale=1.7), lord=('agnis', 1228, -252, 24), sound='ruin_construct', fire=True)

# ---------------------------------------------------------------------------------------- 6 天翔の峰 (40-50)
M('f6_thunder_hawk', 6, 'flyer', (40, 46), '雷鷹', 'Thunder Hawk',
  dict(kind='bird', body='#3a5a9a', wing={'base': '#2a4a8a', 'style': 'feather', 'tip': (255, 230, 80)},
       crest='#f0d040', face='#e8e8f0', eye=(255, 230, 60), glow=(255, 240, 120), beak=(240, 200, 60), scale=1.0),
  bolt='STORM', particle='ELECTRIC_SPARK', chance=0.06, sound='harpy', weight=12)
M('f6_sky_knight', 6, 'humanoid', (42, 48), '天空の騎士', 'Sky Knight',
  dict(show={'helmet', 'helm_crest', 'blade', 'shield_l', 'pauldron_r', 'pauldron_l', 'cape'},
       helm={'base': '#c8d4e4', 'style': 'metal'}, armor={'base': '#c8d4e4', 'style': 'metal'},
       tunic={'base': '#b8c4d8', 'style': 'metal'}, cape='#3a5aa8', plume='#f0f4ff', shield='#a8b8d8',
       blade='#e8f0ff', guard='#3a5aa8', face='helm', eye=(160, 220, 255), glow=(140, 210, 255), scale=1.1,
       emblem=(80, 120, 220)), tune=(1.2, 1.05, 1.4, 0.95), sound='ice_knight', weight=8)
M('f6_cloud_giant', 6, 'golem', (44, 50), '雲の巨人', 'Cloud Giant',
  dict(show={'shoulder_r', 'shoulder_l'}, mat={'base': '#e8ecf4', 'style': 'cloud'}, eye=(120, 160, 220),
       glow=(160, 200, 255), plate={'base': '#c8d0e0', 'style': 'cloud'}, scale=1.25), tune=(1.4, 1.0, 1.0, 0.85),
  particle='CLOUD', chance=0.08, sound='ice_golem', weight=7)
M('f6_gale_spirit', 6, 'flyer', (41, 47), '旋風の精', 'Gale Spirit',
  dict(kind='spirit', base='#c8f0d8', style='cloud', core='#f0fff4', eye=(40, 120, 80), glow=(160, 255, 200),
       cloud_wings=True, scale=1.0), bolt='STORM', particle='CLOUD', chance=0.1, sound='storm_spirit', weight=10)
M('f6_peak_wolf', 6, 'beast', (40, 45), '峰の狼', 'Peak Wolf',
  dict(kind='wolf', body='#8a8c94', mane='#c8cad2', eye=(255, 200, 60), belly='#e0e2e8', scale=1.1), sound='wolf',
  weight=13)
M('f6_storm_mage', 6, 'humanoid', (43, 49), '嵐の魔導師', 'Storm Mage',
  dict(show={'hood', 'robe', 'staff'}, hood={'base': '#1e2a5a', 'trim': '#f0d040'},
       robe={'base': '#18224a', 'trim': '#f0d040'}, tunic='#1e2a5a', face='human', skin='#d8c4b0',
       beard=(220, 220, 230), eye=(255, 240, 120), glow=(255, 240, 120), orb=(255, 240, 140), emblem=(240, 210, 80),
       staff='#3a3a4a'), bolt='STORM', sound='frost_mage', weight=8)
M('f6_young_griffin', 6, 'flyer', (42, 48), '若きグリフォン', 'Young Griffin',
  dict(kind='bird', body='#c89a50', wing={'base': '#b8883a', 'style': 'feather', 'tip': (250, 240, 220)},
       crest='#f4ece0', face='#f4ece0', eye=(255, 160, 40), beak=(230, 190, 60), lion_tail=True, scale=1.15),
  tune=(1.1, 1.05, 1.0, 1.05), sound='harpy', weight=9)
M('f6_sky_ram', 6, 'beast', (41, 47), '天の大角羊', 'Sky Ram',
  dict(kind='ram', body={'base': '#f0ece4', 'style': 'cloud'}, head='#d8d0c4', horn='#c8a878', eye=(200, 140, 40),
       scale=1.05), tune=(1.2, 1.0, 1.1, 1.0), sound='wolf', weight=11)
M('f6_star_sentinel', 6, 'flyer', (45, 50), '星の哨兵', 'Star Sentinel',
  dict(kind='sentinel', metal='#d0d8e8', core='#a0d8ff', halo='#c8e8ff', eye=(140, 220, 255), scale=1.1),
  bolt='STORM', particle='END_ROD', chance=0.05, sound='sky_sentinel', weight=7)
M('f6_frost_titanling', 6, 'golem', (44, 50), '氷巨人の子', 'Frost Titanling',
  dict(show={'ice_crown', 'crystal_r'}, mat={'base': '#b8dcf0', 'style': 'ice'}, ice={'base': '#e0f8ff', 'style': 'crystal'},
       eye=(40, 80, 140), scale=1.0), tune=(1.3, 1.05, 1.2, 0.9), sound='ice_golem', weight=8)
M('lord_alvin', 6, 'humanoid', None, '天翔の聖騎士アルヴィン', 'Alvin, Paladin of the Peaks',
  dict(show={'helmet', 'helm_crest', 'blade', 'shield_l', 'pauldron_r', 'pauldron_l', 'cape'},
       helm={'base': '#e8c860', 'style': 'gold'}, armor={'base': '#e8c860', 'style': 'gold'},
       tunic={'base': '#f0f0f4', 'style': 'metal'}, cape='#f4f4ff', plume='#3a5ac8', shield='#e8c860',
       blade='#f4f8ff', guard='#e8c860', face='helm', eye=(255, 240, 160), glow=(255, 240, 160),
       emblem=(255, 230, 120), scale=1.4), lord=('alvin', 300, -676, 24), sound='ice_knight', respawn=(11, 15))
M('lord_peak_sniper', 6, 'humanoid', None, '峰の狙撃手', 'The Peak Sniper',
  dict(show={'hood', 'crossbow', 'cape'}, hood='#e8ecf0', tunic='#c8ccd4', cape='#a8acb8', pants='#8a8e98',
       face='shadow', eye=(120, 220, 255), scale=1.3, shoot=True, stock='#e8ecf0', limb='#5a6a8a'), bolt='ARROW',
  lord=('peak_sniper', 28, -748, 26), sound='bandit', respawn=(9, 13))
M('lord_zephyr', 6, 'flyer', None, '雷翼のゼファー', 'Zephyr the Thunderwing',
  dict(kind='bird', body='#2a3a7a', wing={'base': '#2a3a8a', 'style': 'feather', 'tip': (255, 240, 120)},
       crest='#f0e060', face='#f0f0f8', eye=(255, 255, 160), glow=(255, 255, 140), beak=(240, 210, 80), scale=1.75),
  bolt='STORM', particle='ELECTRIC_SPARK', chance=0.2, lord=('zephyr', 404, -796, 30), sound='harpy')

# ---------------------------------------------------------------------------------------- 7 終焉の森 (50)
M('f7_void_hound', 7, 'beast', (50, 50), '虚無の猟犬', 'Void Hound',
  dict(kind='hound', body='#2a1e3a', mane={'base': '#4a2a6a', 'style': 'fur'},
       spines={'base': '#a050ff', 'style': 'crystal'}, horn='#d0b8ff', eye=(220, 120, 255), glow=(200, 100, 255),
       horns=True, scale=1.2), particle='PORTAL', chance=0.12, sound='wolf', weight=12)
M('f7_blighted_treant', 7, 'golem', (50, 50), '蝕まれた古木', 'Blighted Treant',
  dict(show={'canopy', 'branch_r', 'branch_l'}, mat={'base': '#3a2a30', 'style': 'bark'},
       canopy={'base': '#4a2a5a', 'flowers': (200, 120, 255)}, branch={'base': '#2a1e24', 'style': 'bark'},
       eye=(220, 120, 255), glow=(200, 100, 255), scale=1.3), tune=(1.4, 1.1, 1.2, 0.85), sound='treant', weight=8)
M('f7_doom_knight', 7, 'humanoid', (50, 50), '破滅の騎士', 'Doom Knight',
  dict(show={'helmet', 'horn_r', 'horn_l', 'blade', 'shield_l', 'pauldron_r', 'pauldron_l'},
       helm={'base': '#2a2234', 'style': 'metal'}, armor={'base': '#32283e', 'style': 'metal'},
       tunic={'base': '#2a2234', 'style': 'metal'}, shield='#3a2a4a', horn='#c0a8e0', blade='#6a5a80',
       face='helm', eye=(220, 120, 255), glow=(200, 100, 255), emblem=(160, 80, 220), scale=1.15),
  tune=(1.2, 1.1, 1.4, 0.95), sound='dark_knight', weight=9)
M('f7_hollow_eye', 7, 'flyer', (50, 50), '虚ろな眼', 'Hollow Eye',
  dict(kind='eye', base='#2a1a3a', core='#c060ff', glow=(200, 120, 255), scale=0.9), bolt='VOID',
  particle='PORTAL', chance=0.2, sound='ice_wraith', weight=9)
M('f7_amethyst_colossus', 7, 'golem', (50, 50), '紫晶の巨像', 'Amethyst Colossus',
  dict(show={'crystal_r', 'crystal_l', 'shoulder_r', 'shoulder_l'}, mat={'base': '#6a4a8a', 'style': 'crystal'},
       ice={'base': '#c890ff', 'style': 'crystal'}, eye=(255, 200, 255), glow=(230, 160, 255), scale=1.2),
  tune=(1.4, 1.0, 1.5, 0.85), sound='ice_golem', weight=7)
M('f7_shadow_assassin', 7, 'humanoid', (50, 50), '影の暗殺者', 'Shadow Assassin',
  dict(show={'hood', 'dagger', 'claws_l', 'cape'}, hood={'base': '#14101a', 'style': 'cloth'}, tunic='#1a1622',
       cape='#100c16', pants='#141018', face='shadow', eye=(255, 60, 120), blade='#c0c0d0'),
  tune=(0.9, 1.2, 0.8, 1.15), sound='shade', weight=10)
M('f7_plague_rat', 7, 'beast', (50, 50), '疫病ネズミ', 'Plague Rat',
  dict(kind='rat', body='#4a5a3a', ear='#8a9a6a', tail={'base': '#8a9a6a', 'style': 'skin'}, eye=(200, 255, 80),
       glow=(180, 255, 60), spines={'base': '#a0d050', 'style': 'crystal'}, scale=0.8),
  tune=(0.8, 1.05, 0.7, 1.15), particle='CRIMSON_SPORE', chance=0.08, sound='cave_rat', weight=13)
M('f7_hex_witch', 7, 'humanoid', (50, 50), '呪詛の魔女', 'Hex Witch',
  dict(show={'hood', 'robe', 'staff'}, hood={'base': '#1a0a12', 'trim': '#c02040'},
       robe={'base': '#14080e', 'trim': '#c02040'}, tunic='#1a0a12', face='human', skin='#c8b8c0',
       eye=(255, 40, 80), glow=(255, 40, 80), orb=(255, 60, 90), staff='#2a1a20', witch_hat=True), bolt='CURSE',
  sound='cultist', weight=10)
M('f7_wandering_grudge', 7, 'flyer', (50, 50), '彷徨う怨念', 'Wandering Grudge',
  dict(kind='wraith', base='#5a3a6a', crown={'base': '#a060c0', 'style': 'void'}, eye=(255, 120, 200),
       glow=(255, 100, 200)), bolt='VOID', particle='SOUL', chance=0.06, sound='shade', weight=10)
M('f7_end_scorpion', 7, 'beast', (50, 50), '終焉のサソリ', 'End Scorpion',
  dict(kind='scorpion', body={'base': '#2a1a3a', 'style': 'chitin'}, claw={'base': '#4a2a6a', 'style': 'chitin'},
       eye=(220, 120, 255), glow=(200, 100, 255), scale=1.2), tune=(1.2, 1.1, 1.3, 1.0), sound='scorpion',
  weight=10)
M('lord_xenos', 7, 'humanoid', None, '破滅の先触れゼノス', 'Xenos, Herald of Ruin',
  dict(show={'hood', 'robe', 'staff', 'horn_r', 'horn_l'}, hood={'base': '#24102e', 'trim': '#b070ff'},
       robe={'base': '#1c0c24', 'trim': '#b070ff'}, tunic='#24102e', horn='#e0d0ff', face='shadow',
       eye=(220, 120, 255), orb=(200, 100, 255), emblem=(180, 90, 255), staff='#1e1a24', scale=1.45), bolt='VOID',
  lord=('xenos', -892, -556, 28), sound='void_mage', respawn=(12, 16))
M('lord_black_knight', 7, 'humanoid', None, '終焉の黒騎士', 'The Black Knight of the End',
  dict(show={'helmet', 'horn_r', 'horn_l', 'greatblade', 'pauldron_r', 'pauldron_l', 'cape'},
       helm={'base': '#16121c', 'style': 'metal'}, armor={'base': '#1c1824', 'style': 'metal'},
       tunic={'base': '#16121c', 'style': 'metal'}, cape='#3a0a14', horn='#2a2230', blade='#3a3448',
       guard='#8a1020', face='helm', eye=(255, 40, 40), glow=(255, 30, 30), emblem=(140, 20, 30), scale=1.45),
  lord=('black_knight', -956, -388, 28), sound='dark_knight', respawn=(10, 14))
M('lord_nihil', 7, 'beast', None, '虚無を喰む竜ニヒル', 'Nihil the Void-Gnawing Drake',
  dict(kind='lizard', body={'base': '#2a1a3e', 'style': 'scale'}, crest={'base': '#a050ff', 'style': 'crystal'},
       spines={'base': '#c080ff', 'style': 'crystal'}, horn='#e0d0ff', eye=(255, 120, 255), glow=(240, 100, 255),
       horns=True, scale=1.9), lord=('nihil', -1044, -500, 30), sound='salamander')

# ---------------------------------------------------------------------------------------- 8 豊穣の農場 (10-20)
M('f8_field_crow', 8, 'flyer', (10, 15), '畑荒らしのカラス', 'Field Crow',
  dict(kind='bird', body='#1e1e26', wing={'base': '#24242e', 'style': 'feather', 'tip': (90, 90, 120)},
       crest='#1e1e26', face='#2a2a34', eye=(255, 255, 255), beak=(60, 60, 70), scale=0.65),
  tune=(0.7, 0.9, 0.6, 1.2), sound='harpy', weight=13)
M('f8_giant_mole', 8, 'beast', (10, 16), '大モグラ', 'Giant Mole',
  dict(kind='mole', body='#3a2e28', nose=(230, 150, 160), eye=(20, 20, 20), scale=0.85, claw='#e8d8c8'),
  tune=(1.0, 0.95, 1.0, 0.95), sound='cave_rat', weight=12)
M('f8_pumpkin_head', 8, 'humanoid', (12, 18), 'カボチャ頭', 'Pumpkin Head',
  dict(show={'club', 'leaf_crown'}, skin={'base': '#e07a20', 'style': 'skin'}, crown={'base': '#4a6a2a', 'style': 'leaf'},
       tunic='#5a4a3a', pants='#3a4a6a', face='pumpkin', eye=(255, 210, 60), glow=(255, 200, 50), hand='#6a4a30',
       stem=True, club='#6b4c2a'), sound='prisoner', weight=10)
M('f8_wild_rooster', 8, 'flyer', (11, 16), '暴れ雄鶏', 'Wild Rooster',
  dict(kind='bird', body='#f0ece4', wing={'base': '#e8e0d4', 'style': 'feather', 'tip': (200, 80, 40)},
       crest='#d02a20', face='#f0ece4', eye=(30, 20, 10), beak=(230, 190, 60), comb=True, scale=0.7),
  tune=(0.75, 1.0, 0.7, 1.15), sound='harpy', weight=12)
M('f8_rowdy_calf', 8, 'beast', (11, 17), '暴れ仔牛', 'Rowdy Calf',
  dict(kind='boar', body='#f0ece8', head='#f0ece8', mane='#2a2420', horn='#e8dcc0', eye=(30, 20, 10),
       nose=(230, 160, 160), spots=True, calf=True, scale=0.95), tune=(1.2, 1.0, 1.0, 1.0), sound='wolf',
  weight=11)
M('f8_hay_golem', 8, 'golem', (13, 19), '干し草ゴーレム', 'Hay Golem',
  dict(show={'moss_beard'}, mat={'base': '#d8b850', 'style': 'rope'}, moss={'base': '#c8a840', 'style': 'rope'},
       eye=(40, 30, 20), scale=0.95), tune=(1.2, 0.95, 0.9, 0.95), sound='treant', weight=8)
M('f8_lost_farmhand', 8, 'humanoid', (12, 18), '迷い農夫', 'Lost Farmhand',
  dict(show={'club', 'leaf_crown'}, skin='#8a9a70', tunic='#4a6aa0', pants='#3a5a90', face='ghoul',
       eye=(170, 255, 120), glow=(150, 255, 100), hat=True, crown={'base': '#c8a860', 'style': 'rope'},
       hand='#8a9a70', club='#6b4c2a'), sound='ghoul', weight=10)
M('f8_giant_bee', 8, 'flyer', (13, 19), '大バチ', 'Giant Bee',
  dict(kind='bug', body='#e8b820', stripes=(30, 24, 20), wing={'base': '#d8e8f0', 'style': 'membrane'},
       eye=(20, 20, 20), scale=0.6), tune=(0.7, 1.05, 0.6, 1.2), sound='harpy', weight=11)
M('f8_vine_lurker', 8, 'humanoid', (14, 20), '蔓の潜み', 'Vine Lurker',
  dict(show={'leaf_crown', 'claws_r', 'claws_l'}, skin='#5a8a3a', tunic={'base': '#4a7a30', 'style': 'leaf'},
       crown={'base': '#3a6a2a', 'flowers': (240, 220, 80)}, bark='#4a6a2a', face='monster', eye=(255, 230, 80),
       glow=(255, 220, 60), claw='#3a5a20', hand='#5a8a3a'), bolt='NATURE', sound='dryad', weight=9)
M('f8_windmill_sprite', 8, 'flyer', (15, 20), '風車の妖精', 'Windmill Sprite',
  dict(kind='spirit', base='#d8ecff', style='cloud', core='#fff8e0', eye=(60, 100, 160), glow=(255, 240, 160),
       halo='#f0d890', sprite_wings=True, scale=0.75), bolt='STORM', particle='CLOUD', chance=0.06, sound='storm_spirit', weight=8)
M('lord_brutus', 8, 'beast', None, '暴れ牛ブルータス', 'Brutus the Rampaging Bull',
  dict(kind='bull', body='#2a1e1a', mane='#1a1210', horn='#f0e4c8', eye=(255, 50, 30), glow=(255, 40, 20),
       nose=(80, 60, 60), nose_ring=True, scale=1.8), lord=('brutus', 556, 180, 34), sound='wolf', respawn=(9, 13))
M('lord_harvest_boss', 8, 'humanoid', None, '収穫泥棒の頭目', 'The Harvest Thief Chief',
  dict(show={'hood', 'club', 'pauldron_r'}, hood='#3a4a2a', tunic='#5a6a3a', armor='#5a5a4a', skin='#c09070',
       beard=(90, 60, 40), face='masked', mask=(60, 70, 40), club='#8a8a90', scale=1.3),
  lord=('harvest_boss', 940, 612, 30), sound='bandit', respawn=(7, 11))
M('lord_strom', 8, 'humanoid', None, '案山子の王ストローム', 'Strom the Scarecrow King',
  dict(show={'hood', 'staff', 'cape', 'hand_flame'}, hood={'base': '#a8883a', 'style': 'rope'}, skin='#d8c080',
       tunic='#6a2a3a', cape='#4a1a2a', face='stitched', eye=(255, 160, 40), glow=(255, 150, 30),
       orb=(255, 170, 60), staff='#5a4028', scale=1.5, straw=True, hand='#d8c080', crown_hat=True),
  bolt='FIRE', lord=('strom', 500, -12, 30), sound='prisoner')

# ---------------------------------------------------------------------------------------- 9 湖畔のほとり (15-25)
M('f9_marsh_frog', 9, 'beast', (15, 20), '沼ガエル', 'Marsh Frog',
  dict(kind='toad', body={'base': '#4a6a2a', 'style': 'skin'}, crest={'base': '#c8b040', 'style': 'skin'},
       belly='#d8d8a0', eye=(255, 180, 40), scale=0.8), tune=(0.9, 1.0, 0.8, 1.05), sound='salamander', weight=13)
M('f9_lake_serpent', 9, 'beast', (17, 23), '湖ヘビ', 'Lake Serpent',
  dict(kind='serpent', body={'base': '#2a5a7a', 'style': 'scale'}, crest={'base': '#60c8e0', 'style': 'scale'},
       eye=(255, 220, 60), scale=1.0), tune=(1.0, 1.05, 0.9, 1.05), sound='salamander', weight=11)
M('f9_egret_spirit', 9, 'flyer', (16, 22), '白鷺の精', 'Egret Spirit',
  dict(kind='bird', body='#f8f8fc', wing={'base': '#f0f0f8', 'style': 'feather', 'tip': (200, 220, 240)},
       crest='#ffffff', face='#f8f8fc', eye=(40, 40, 60), glow=(200, 230, 255), beak=(240, 200, 80), long_neck=True,
       scale=0.95), bolt='FROST', sound='harpy', weight=9)
M('f9_bog_ghoul', 9, 'humanoid', (16, 22), '沼の亡者', 'Bog Ghoul',
  dict(show={'claws_r', 'claws_l'}, skin='#5a6a3a', tunic={'base': '#5a6a3a', 'style': 'skin'}, pants='#3a3a2a',
       face='ghoul', eye=(200, 255, 80), glow=(180, 255, 60), mud=True, hand='#5a6a3a'), sound='ghoul', weight=12)
M('f9_reed_witch', 9, 'humanoid', (18, 24), '葦の魔女', 'Reed Witch',
  dict(show={'hood', 'robe', 'staff'}, hood={'base': '#8a9a3a', 'trim': '#e8e0a0'},
       robe={'base': '#6a7a2a', 'trim': '#e8e0a0'}, tunic='#8a9a3a', face='human', skin='#d8c8a8',
       eye=(120, 200, 60), glow=(140, 230, 80), orb=(180, 240, 100), staff='#8a7a40'), bolt='NATURE',
  sound='cultist', weight=8)
M('f9_great_crayfish', 9, 'beast', (17, 23), '大ザリガニ', 'Great Crayfish',
  dict(kind='crayfish', body={'base': '#b83a24', 'style': 'chitin'}, claw={'base': '#d04a2a', 'style': 'chitin'},
       eye=(20, 20, 20), scale=1.0), tune=(1.1, 1.05, 1.4, 0.95), sound='scorpion', weight=11)
M('f9_mud_golem', 9, 'golem', (19, 25), '泥ゴーレム', 'Mud Golem',
  dict(show={'moss_beard'}, mat={'base': '#5a4634', 'style': 'stone'}, moss={'base': '#4a5a2a', 'style': 'leaf'},
       eye=(200, 220, 120), glow=(180, 210, 100), drips=True, scale=1.1), tune=(1.4, 1.0, 1.1, 0.85),
  sound='treant', weight=7)
M('f9_fisher_ghost', 9, 'humanoid', (18, 24), '漁師の幽霊', 'Fisher\'s Ghost',
  dict(show={'hood', 'robe', 'claws_r'}, hood={'base': '#3a5a6a', 'style': 'ghost'},
       robe={'base': '#2a4a5a', 'style': 'ghost'}, tunic={'base': '#3a5a6a', 'style': 'ghost'}, face='shadow',
       eye=(160, 255, 255), translucent=True, legless=True, claw='#cfe8f0'), sound='shade', weight=8)
M('f9_giant_dragonfly', 9, 'flyer', (17, 23), '大トンボ', 'Giant Dragonfly',
  dict(kind='bug', body='#2a8a8a', stripes=(20, 60, 60), wing={'base': '#c8f0f0', 'style': 'membrane'},
       eye=(120, 255, 200), long_tail=True, scale=0.7), tune=(0.7, 1.0, 0.6, 1.25), sound='harpy', weight=10)
M('f9_water_imp', 9, 'humanoid', (20, 25), '水の小鬼', 'Water Imp',
  dict(show={'horn_r', 'horn_l', 'tail', 'claws_r', 'claws_l'}, skin='#3a7ab8',
       tunic={'base': '#3a7ab8', 'style': 'skin'}, pants='#1e3a5a', horn='#c8e8ff', tail='#3a7ab8', claw='#c8e8ff',
       face='imp', eye=(200, 255, 255), scale=0.7, imp=True), tune=(0.8, 1.05, 0.7, 1.15), bolt='FROST',
  sound='fire_imp', weight=10)
M('lord_melusine', 9, 'humanoid', None, '湖の魔女メリュー', 'Melu, Witch of the Lake',
  dict(show={'hood', 'robe', 'staff'}, hood={'base': '#1a5a6a', 'trim': '#a0f0ff'},
       robe={'base': '#124a5a', 'trim': '#a0f0ff'}, tunic='#1a5a6a', face='human', skin='#c8d8d8',
       eye=(120, 240, 255), glow=(120, 240, 255), orb=(140, 240, 255), staff='#2a5a6a', scale=1.35), bolt='FROST',
  lord=('melusine', 484, 660, 26), sound='frost_mage', respawn=(10, 14))
M('lord_drowned_fisher', 9, 'humanoid', None, '沈んだ漁師', 'The Drowned Fisher',
  dict(show={'club', 'shackle_r', 'leaf_crown'}, skin='#5a7a7a', tunic={'base': '#4a6a6a', 'style': 'skin'},
       pants='#2a3a4a', face='ghoul', eye=(160, 255, 220), glow=(140, 255, 200), scale=1.4, hat=True,
       crown={'base': '#5a6a50', 'style': 'rope'}, hand='#5a7a7a', club='#4a5a5a'),
  lord=('drowned_fisher', 716, 732, 30), sound='ghoul', respawn=(8, 12))
M('lord_numel', 9, 'beast', None, '湖底の主ヌメル', 'Numel of the Lakebed',
  dict(kind='serpent', body={'base': '#1a4a5a', 'style': 'scale'}, crest={'base': '#3ac8b8', 'style': 'scale'},
       spines={'base': '#80f0e0', 'style': 'crystal'}, eye=(255, 240, 120), glow=(255, 230, 100), whiskers=True,
       scale=2.0), lord=('numel', 804, 844, 30), sound='salamander')

# ---------------------------------------------------------------------------------------- 10 風の港 (20-30)
M('f10_deck_pirate', 10, 'humanoid', (20, 26), '甲板の海賊', 'Deck Pirate',
  dict(show={'hood', 'dagger'}, hood={'base': '#c02a20', 'style': 'cloth'}, tunic='#e8e0d0', pants='#2a3a5a',
       skin='#c08a6a', face='human', beard=(50, 40, 30), stripes=True), sound='bandit', weight=13)
M('f10_pirate_brute', 10, 'humanoid', (22, 28), '海賊の荒くれ', 'Pirate Brute',
  dict(show={'club'}, skin='#b07a5a', tunic={'base': '#b07a5a', 'style': 'skin'}, pants='#2a2a3a',
       face='human', beard=(40, 30, 20), scale=1.15, tattoo=True, hand='#b07a5a', club='#5b5b62'),
  tune=(1.3, 1.1, 1.0, 0.95), sound='jailer', weight=10)
M('f10_beach_crab', 10, 'beast', (20, 25), '浜ガニ', 'Beach Crab',
  dict(kind='crab', body={'base': '#e07a30', 'style': 'chitin'}, claw={'base': '#e88a40', 'style': 'chitin'},
       eye=(20, 20, 20), scale=0.85), tune=(1.1, 1.0, 1.4, 0.95), sound='scorpion', weight=13)
M('f10_sea_harpy', 10, 'flyer', (21, 27), '海鳥ハーピー', 'Sea Harpy',
  dict(kind='harpy', body='#d8dce4', wing={'base': '#c8ccd8', 'style': 'feather', 'tip': (60, 70, 90)},
       crest='#e8ecf4', face='#e0c8b0', hair='#e8ecf4', eye=(60, 120, 200), scale=1.0), sound='harpy', weight=10)
M('f10_coral_golem', 10, 'golem', (24, 30), '珊瑚の巨像', 'Coral Golem',
  dict(show={'crystal_r', 'crystal_l', 'shoulder_r'}, mat={'base': '#e07a8a', 'style': 'stone'},
       ice={'base': '#f0a0b0', 'style': 'crystal'}, eye=(255, 240, 200), glow=(255, 220, 200), barnacles=True,
       scale=1.15), tune=(1.4, 1.0, 1.3, 0.85), sound='ruin_construct', weight=7)
M('f10_shore_monitor', 10, 'beast', (21, 27), '岸辺のオオトカゲ', 'Shore Monitor',
  dict(kind='lizard', body={'base': '#5a6a5a', 'style': 'scale'}, crest={'base': '#8a9a7a', 'style': 'scale'},
       eye=(255, 200, 40), scale=1.15), tune=(1.2, 1.05, 1.1, 1.0), sound='salamander', weight=11)
M('f10_ghost_sailor', 10, 'humanoid', (23, 29), '幽霊船員', 'Ghost Sailor',
  dict(show={'hood', 'blade'}, hood={'base': '#2a4a4a', 'style': 'ghost'}, tunic={'base': '#3a5a5a', 'style': 'ghost'},
       pants={'base': '#2a4040', 'style': 'ghost'}, face='skull', eye=(120, 255, 200), glow=(100, 255, 180),
       translucent=True, blade='#a0c8c0'), sound='shade', weight=9)
M('f10_smuggler', 10, 'humanoid', (22, 28), '密輸人', 'Smuggler',
  dict(show={'hood', 'crossbow', 'cape'}, hood='#2a2a30', tunic='#3a3a44', cape='#1e1e24', pants='#26262c',
       face='masked', mask=(40, 40, 50), skin='#b08060', shoot=True), bolt='ARROW', sound='bandit', weight=10)
M('f10_tide_spirit', 10, 'flyer', (24, 30), '潮の精', 'Tide Spirit',
  dict(kind='spirit', base='#3a8ac8', style='ice', core='#c8f0ff', eye=(255, 255, 255), glow=(160, 230, 255),
       water_crown=True, veil=True, scale=0.95), bolt='FROST', particle='DRIPPING_WATER', chance=0.2, sound='storm_spirit', weight=8)
M('f10_sea_witch', 10, 'humanoid', (25, 30), '海の魔女', 'Sea Witch',
  dict(show={'hood', 'robe', 'staff'}, hood={'base': '#2a1a4a', 'trim': '#40d0c0'},
       robe={'base': '#1e1238', 'trim': '#40d0c0'}, tunic='#2a1a4a', face='human', skin='#b8c8c8',
       eye=(80, 240, 220), glow=(80, 240, 220), orb=(80, 240, 220), staff='#2a3a4a'), bolt='VOID',
  sound='cultist', weight=7)
M('lord_barbas', 10, 'humanoid', None, '隻眼の船長バルバス', 'One-Eyed Captain Barbas',
  dict(show={'hood', 'blade', 'cape', 'pauldron_r', 'pauldron_l'}, hood={'base': '#1a1a20', 'style': 'cloth'},
       tunic='#7a1a1a', cape='#1a1a20', armor={'base': '#c8a040', 'style': 'gold'}, skin='#b88060',
       face='human', beard=(30, 24, 20), eyepatch=True, blade='#d8dce4', guard='#c8a040', tricorne=True,
       scale=1.35), lord=('barbas', 892, 772, 26), sound='jailer', respawn=(10, 14))
M('lord_dock_brawler', 10, 'humanoid', None, '港の喧嘩屋', 'The Dock Brawler',
  dict(show={'club', 'shackle_r', 'shackle_l'}, skin='#a87050', tunic={'base': '#a87050', 'style': 'skin'},
       pants='#3a3a4a', face='human', beard=(60, 40, 30), scale=1.45, tattoo=True, hand='#a87050',
       club='#5a4a3a'), lord=('dock_brawler', 1020, 884, 24), sound='jailer', respawn=(8, 12))
M('lord_ankara', 10, 'beast', None, '錨喰らいの大ガニアンカラ', 'Ankara the Anchor-Eater',
  dict(kind='crab', body={'base': '#a82a1a', 'style': 'chitin'}, claw={'base': '#5a5a64', 'style': 'metal'},
       eye=(255, 220, 60), glow=(255, 200, 40), scale=1.9, barnacles=True), lord=('ankara', 1284, 804, 28),
  sound='scorpion')


def by_rig(rig):
    return [m for m in SPEC if m['rig'] == rig]


def lords():
    return [m for m in SPEC if m['lord']]


def normals():
    return [m for m in SPEC if not m['lord']]


# ================================================================================================= the looks

def variants_for(rig):
    makers = {'humanoid': _humanoid, 'beast': _beast, 'golem': _golem, 'flyer': _flyer}
    return [makers[rig](m) for m in by_rig(rig)]


FEET = {'humanoid': ('right_shin', 'left_shin'), 'golem': ('right_shin', 'left_shin'),
        'beast': ('front_right_paw', 'front_left_paw', 'hind_right_paw', 'hind_left_paw', 'torso', 'carapace',
                  'mid_leg_r1', 'mid_leg_r2', 'mid_leg_l1', 'mid_leg_l2')}


def snap_to_ground(rig):
    """Lifts or lowers every bestiary look of a walking rig so its lowest foot (or belly) rests on the ground (model
    y = 24) at rest, whatever its rest pose stretched or squashed."""
    import numpy as np
    import ermodel
    if rig.name not in FEET:
        return
    ids = {m['id'] for m in by_rig(rig.name)}
    for v in rig.variants:
        if v.id not in ids or v.extra.get('legless'):
            continue
        mats = ermodel.part_transforms(rig, v, {})
        bottom = None
        for name in FEET[rig.name]:
            part = rig.parts[name]
            if not ermodel.visible_chain(rig, v, part):
                continue
            for c in part.cubes:
                for dx in (0, c.w):
                    for dy in (0, c.h):
                        for dz in (0, c.d):
                            p = mats[name] @ np.array([c.x + dx - part.pivot[0], c.y + dy - part.pivot[1],
                                                       c.z + dz - part.pivot[2], 1.0])
                            bottom = p[1] if bottom is None else max(bottom, p[1])
        if bottom is None:
            continue
        lift = round(bottom - 24.0, 3)
        if abs(lift) > 0.02:
            base = dict(v.poses.get('base', {}))
            pos = list(base.get('pos', (0, 0, 0)))
            pos[1] += lift
            base['pos'] = tuple(pos)
            v.poses['base'] = base


def _hex(c):
    return '#%02x%02x%02x' % tuple(int(max(0, min(255, x))) for x in c)


def _base_rgb(v):
    if isinstance(v, dict):
        return rgb(v['base']) if isinstance(v['base'], str) else tuple(v['base'])
    return rgb(v) if isinstance(v, str) else tuple(v)


def _styled(v, style):
    """A palette entry with a material style (keeps an explicit style)."""
    if isinstance(v, dict):
        return v
    return {'base': v, 'style': style}


def _darker(v, f=0.85):
    if isinstance(v, dict):
        return dict(v, base=_hex(shade(_base_rgb(v), f)))
    return _hex(shade(_base_rgb(v), f))


# -------------------------------------------------------------------------------- humanoid

def _humanoid(m):
    import rig_humanoid as RH
    a = m['art']
    show = set(a.get('show', ()))
    face = a.get('face', 'human')
    skin = a.get('skin', '#c99a78')
    if face == 'skull' and 'skin' not in a:
        skin = {'base': '#d8ccb0', 'style': 'bone'}
    tunic = a.get('tunic', '#6b4a35')
    tunic_style = tunic.get('style') if isinstance(tunic, dict) else None
    ghost = bool(a.get('translucent'))
    gbase = _hex(_base_rgb(tunic)) if ghost else None

    over = {'skin': skin, 'tunic': tunic}
    for key in ('hood', 'helm', 'plume', 'horn', 'leaves', 'armor', 'cape', 'robe', 'tail', 'grip', 'blade', 'guard',
                'haft', 'club', 'staff', 'stock', 'limb', 'bolt', 'claw', 'iron', 'chain', 'shield'):
        if key in a:
            over[key] = a[key]
    if 'crown' in a:
        over['leaves'] = a['crown']
    if a.get('witch_hat') or a.get('tricorne'):
        show.add('leaf_crown')
        brim = _base_rgb(a.get('hood', '#3a2a2a')) if a.get('witch_hat') else (26, 26, 32)
        over['leaves'] = {'base': _hex(shade(brim, 0.85)), 'style': 'cloth'}
    over['sleeve'] = a.get('sleeve', tunic)
    over['sleeve2'] = a.get('sleeve2', tunic)
    if tunic_style == 'metal':
        pants_default = _darker(tunic, 0.8)
        boots_default = _darker(tunic, 0.9)
    elif tunic_style == 'skin':
        pants_default = '#4a3b30'
        boots_default = _styled(_hex(_base_rgb(skin)), 'skin')
    elif tunic_style == 'bone':
        pants_default = tunic
        boots_default = tunic
    else:
        pants_default = '#4a3b30'
        boots_default = '#3b2a20'
    over['pants'] = a.get('pants', pants_default)
    over['boots'] = a.get('boots', boots_default)
    over['belly'] = a.get('belly', over['pants'] if tunic_style != 'skin' else _darker(over['pants'], 0.9))
    if a.get('bark'):
        bark = {'base': a['bark'], 'style': 'bark'}
        for key in ('pants', 'boots', 'belly'):
            if key not in a:
                over[key] = bark
    if a.get('wrap'):
        wrap = {'base': a['wrap'], 'style': 'bandage'}
        for key in ('skin', 'tunic', 'belly', 'sleeve', 'sleeve2', 'pants', 'boots'):
            over[key] = wrap
    if a.get('straw'):
        over['sleeve2'] = {'base': '#d8c080', 'style': 'rope'}
    if ghost:
        for key in ('skin', 'sleeve', 'sleeve2', 'pants', 'boots', 'belly'):
            if key not in a or not isinstance(a.get(key), dict):
                over[key] = {'base': gbase, 'style': 'ghost'} if key != 'skin' or face != 'skull' else over['skin']
    if 'orb' in a:
        over['orb'] = {'base': _hex(a['orb']), 'style': 'gem'}
    pal = RH.base_palette(**over)

    skin_rgb = _base_rgb(skin)
    eye = a.get('eye', (40, 30, 30))
    glow = a.get('glow')
    if 'hand' in a:
        hand = rgb(a['hand'])
    elif face in ('helm', 'helm_open') and 'helmet' in show:
        hand = shade(_base_rgb(a.get('armor', a.get('helm', '#8a929c'))), 0.9)
    elif a.get('wrap'):
        hand = rgb(a['wrap'])
    elif face == 'skull':
        hand = (216, 204, 176)
    elif tunic_style == 'metal':
        hand = shade(_base_rgb(tunic), 0.9)
    else:
        hand = shade(skin_rgb, 0.95)

    poses = {}
    hide = set()
    if a.get('imp'):
        poses['head'] = dict(scale=(1.2, 1.2, 1.2), pos=(0, 0.5, 0))
    if a.get('cap'):
        poses['leaf_crown'] = dict(scale=(1.5, 1.4, 1.5), pos=(0, 0.4, 0))
    if a.get('hat'):
        poses['leaf_crown'] = dict(scale=(1.35, 0.45, 1.35), pos=(0, 0.2, 0))
    if a.get('stem'):
        poses['leaf_crown'] = dict(scale=(0.2, 0.7, 0.2), pos=(0, 1.0, 0))
    if a.get('witch_hat'):
        poses['leaf_crown'] = dict(scale=(1.55, 0.3, 1.55), pos=(0, 0.9, 0))
    if a.get('tricorne'):
        poses['leaf_crown'] = dict(scale=(1.3, 0.5, 1.3), pos=(0, 0.9, 0), rot=(0, 45, 0))
    if face == 'ghoul' or a.get('mud'):
        poses['chest'] = dict(rot=(14, 0, 0))
        poses['head'] = dict(rot=(-12, 0, 0), pos=(0, 0, -0.4))
        poses['right_arm'] = dict(rot=(-20, 0, 6))
        poses['left_arm'] = dict(rot=(-20, 0, -6))
    if a.get('arms_out'):
        poses['right_arm'] = dict(rot=(-70, 0, 4))
        poses['left_arm'] = dict(rot=(-65, 0, -4))
    if a.get('legless'):
        hide |= {'right_leg', 'left_leg'}
        poses['base'] = dict(pos=(0, 2.0, 0))
        poses['robe'] = dict(scale=(1.0, 1.15, 1.0))
    if a.get('plate'):
        poses['cape'] = dict(scale=(1.3, 0.72, 4.5), pos=(0, 0.4, 0), rot=(-6, 0, 0))
        poses['chest'] = dict(rot=(10, 0, 0))

    def deco(p):
        f = p.face('head', 'front')
        gf = p.face('head', 'front', glow=True)
        brows = shade(skin_rgb, 0.5)
        if face == 'human':
            human_face(f, skin_rgb, eye_col=eye, brows=brows, beard=a.get('beard'), glow_fc=gf if glow else None,
                       glow_eyes=glow)
        elif face == 'masked':
            human_face(f, skin_rgb, eye_col=eye, brows=brows)
            mask = a.get('mask', (60, 50, 40))
            f.rect(1, 5, 6, 7, mask)
            f.hline(1, 6, 5, lighten(mask, 0.15))
        elif face == 'shadow':
            RH.shadow_face(p, glow or eye)
        elif face in ('monster', 'imp', 'ghoul'):
            dark = shade(skin_rgb, 0.6)
            if face == 'ghoul':
                f.rect(1, 2, 6, 4, dark)
            f.rect(1, 3, 2, 3, eye)
            f.rect(5, 3, 6, 3, eye)
            if face == 'imp':
                f.px(2, 3, (20, 10, 5))
                f.px(5, 3, (20, 10, 5))
            f.hline(2, 5, 5, (50, 20, 20))
            f.px(2, 6, (235, 230, 210))
            f.px(5, 6, (235, 230, 210))
            if glow or face == 'imp':
                gf.rect(1, 3, 2, 3, glow or eye)
                gf.rect(5, 3, 6, 3, glow or eye)
            if a.get('ears'):
                for side in ('right', 'left'):
                    s = p.face('head', side)
                    s.rect(4, 1, 7, 2, lighten(skin_rgb, 0.12))
                    s.px(7, 0, lighten(skin_rgb, 0.12))
        elif face == 'stitched':
            for (x, y) in ((1, 2), (2, 3), (2, 2), (1, 3), (5, 2), (6, 3), (6, 2), (5, 3)):
                f.px(x, y, (40, 30, 20))
            for x in range(1, 7):
                f.px(x, 5 + (x % 2), (60, 40, 30))
            if glow:
                gf.rect(1, 2, 2, 3, glow)
                gf.rect(5, 2, 6, 3, glow)
        elif face == 'cute':
            f.px(2, 4, eye)
            f.px(5, 4, eye)
            f.px(2, 3, (255, 255, 255))
            f.px(5, 3, (255, 255, 255))
            f.px(1, 5, (240, 150, 150))
            f.px(6, 5, (240, 150, 150))
            f.hline(3, 4, 6, shade(skin_rgb, 0.6))
        elif face == 'skull':
            f.rect(0, 0, 7, 7, (222, 216, 196))
            f.rect(1, 2, 2, 4, (24, 18, 16))
            f.rect(5, 2, 6, 4, (24, 18, 16))
            f.px(3, 5, (50, 40, 36))
            f.px(4, 5, (50, 40, 36))
            for x in range(1, 7):
                f.px(x, 7, (200, 192, 170) if x % 2 else (40, 30, 28))
            for (x, y) in ((1, 3), (2, 3), (5, 3), (6, 3)):
                gf.px(x, y, glow or eye)
        elif face == 'wrapped':
            f.hline(1, 6, 3, (40, 30, 20))
            f.px(2, 3, eye)
            f.px(5, 3, eye)
            if glow:
                gf.px(2, 3, glow)
                gf.px(5, 3, glow)
        elif face == 'beak':
            human_face(f, skin_rgb, eye_col=eye, mouth=False, nose=False)
            f.rect(2, 5, 5, 6, (230, 200, 60))
            f.hline(2, 5, 6, (190, 150, 40))
            top = p.face('head', 'top')
            top.rect(2, 2, 5, 5, (190, 214, 196))
            top.rect(3, 3, 4, 4, (150, 190, 200))
        elif face == 'pumpkin':
            carve = (40, 20, 6)
            for (x, y) in ((1, 2), (2, 2), (5, 2), (6, 2), (1, 3), (6, 3), (3, 4), (4, 4)):
                f.px(x, y, carve)
                gf.px(x, y, glow or (255, 200, 50))
            for x in range(1, 7):
                f.px(x, 5, carve)
                gf.px(x, 5, glow or (255, 200, 50))
            for x in (2, 5):
                f.px(x, 6, carve)
                gf.px(x, 6, glow or (255, 200, 50))
            for side in ('front', 'right', 'back', 'left', 'top'):
                fc = p.face('head', side)
                for x in (0, 3, 7) if side != 'top' else (2, 5):
                    for y in range(fc.h):
                        if fc.get(x, y) != carve:
                            fc.tint(x, y, 0.82)
        if 'helmet' in show and face not in ('helm', 'helm_open'):
            hf = p.face('helmet', 'front')
            for y in range(3, hf.h):
                for x in range(hf.w):
                    hf.clear(x, y)
            hf.hline(0, hf.w - 1, 2, shade(_base_rgb(a.get('helm', '#8a929c')), 0.7))
        if face in ('helm', 'helm_open'):
            hf = p.face('helmet', 'front')
            if face == 'helm':
                hf.hline(1, hf.w - 2, 3, (14, 12, 18))
                hf.vline(hf.w // 2 - 1, 3, 6, (14, 12, 18))
                hf.vline(hf.w // 2, 3, 6, (14, 12, 18))
                if glow:
                    g = p.face('helmet', 'front', glow=True)
                    for x in (2, 5):
                        g.px(x, 3, glow)
            else:
                for y in range(3, hf.h):
                    for x in range(hf.w):
                        hf.clear(x, y)
                hf.hline(0, hf.w - 1, 2, shade(_base_rgb(a.get('helm', '#8a929c')), 0.7))
                human_face(f, skin_rgb, eye_col=eye, brows=brows, beard=a.get('beard'))
        if a.get('eyepatch'):
            f.rect(4, 3, 6, 4, (20, 18, 16))
            f.hline(0, 7, 2, (30, 26, 22))
        if a.get('warpaint'):
            f.vline(1, 2, 6, (240, 230, 220))
            f.vline(6, 2, 6, (240, 230, 220))
        if a.get('scar'):
            for k in range(4):
                f.px(5 + (k % 2), 2 + k, (150, 70, 60))
        if 'hood' in show and face != 'pumpkin':
            RH.hood_opening(p, depth=(1, 1, 1, 1))
        if a.get('witch_hat') and 'hood' in show:
            for fc in p.all_faces('hood', sides_only=False):
                if fc.face == 'top':
                    fc.rect(2, 2, fc.w - 3, fc.h - 3, shade(_base_rgb(a['hood']), 0.7))
        if a.get('tricorne') and 'hood' in show:
            top = p.face('hood', 'top')
            top.rect(0, 0, top.w - 1, top.h - 1, (26, 26, 32))
            top.rect(2, 2, top.w - 3, 3, (200, 170, 70))
        if not a.get('legless') and 'robe' not in show:
            RH.belt(p, col=(58, 38, 24) if not a.get('wrap') else (120, 100, 70), buckle=(208, 176, 80))
        if a.get('emblem'):
            RH.emblem(p, 'chest', a['emblem'], 'diamond')
        if a.get('orb') and 'staff' in show:
            RH.staff_orb_glow(p, a['orb'])
        if 'hand_flame' in show:
            for fc in p.all_faces('hand_flame', glow=True):
                fc.rect(0, 0, fc.w - 1, fc.h - 1, (255, 160, 50))
        if a.get('stripes'):
            for fc in p.all_faces('chest', sides_only=True) + p.all_faces('right_arm', sides_only=True):
                for y in range(0, fc.h, 2):
                    fc.hline(0, fc.w - 1, y, (40, 60, 120))
        if a.get('tattoo'):
            for fc in p.all_faces('chest', sides_only=True):
                if fc.face == 'front':
                    for k in range(2, fc.h - 1):
                        fc.px(1 + (k % 3), k, (40, 60, 90))
                        fc.px(fc.w - 2 - (k % 3), k, (40, 60, 90))
            fc = p.face('right_arm', 'right')
            for y in range(1, fc.h - 1, 2):
                fc.px(1, y, (40, 60, 90))
                fc.px(2, y + 1, (40, 60, 90))
        if a.get('straw'):
            for part in ('chest', 'right_forearm'):
                for fc in p.all_faces(part, sides_only=True):
                    for x in range(0, fc.w, 2):
                        fc.px(x, fc.h - 1, (216, 192, 128))
                        fc.px(x + 1, fc.h - 2, (200, 176, 110))
        if a.get('bones'):
            fc = p.face('chest', 'front')
            fc.vline(fc.w // 2 - 1, 0, fc.h - 1, (70, 62, 50))
            for y in range(1, fc.h - 1, 2):
                fc.hline(1, fc.w - 2, y, (90, 80, 66))
        if a.get('mud'):
            for part in ('chest', 'hips', 'right_leg'):
                for fc in p.all_faces(part, sides_only=True):
                    for x in range(fc.w):
                        for y in range(fc.h - 1 - (x * 7) % 3, fc.h):
                            fc.px(x, y, (62, 50, 34))
        if a.get('rust'):
            for part in ('chest', 'helmet', 'right_arm'):
                for fc in p.all_faces(part, sides_only=True):
                    for y in range(fc.h):
                        for x in range(fc.w):
                            if (x * 5 + y * 3 + len(part)) % 9 == 0:
                                fc.px(x, y, (150, 80, 40))
        if a.get('dots') and 'leaf_crown' in show:
            for fc in p.all_faces('leaf_crown'):
                for y in range(fc.h):
                    for x in range(fc.w):
                        if (x * 3 + y * 5) % 7 == 0:
                            fc.px(x, y, (250, 246, 236))
            bottom = p.face('leaf_crown', 'bottom')
            bottom.rect(0, 0, bottom.w - 1, bottom.h - 1, (236, 226, 206))
            for x in range(0, bottom.w, 2):
                bottom.vline(x, 1, bottom.h - 2, (214, 200, 176))
        if a.get('lamp'):
            hf = p.face('helmet', 'front')
            hf.rect(3, 0, 4, 1, (255, 240, 160))
            g = p.face('helmet', 'front', glow=True)
            g.rect(3, 0, 4, 1, (255, 230, 140))
        if a.get('crown_hat'):
            top = p.face('hood', 'top')
            for x in range(0, top.w, 2):
                top.px(x, 0, (220, 180, 60))
                top.px(x, top.h - 1, (220, 180, 60))
        RH.hands(p, hand)
        boots = a.get('boots')
        if isinstance(boots, str):
            RH.boots(p, rgb(boots))

    overrides = {'cast': 'shoot'} if a.get('shoot') else None
    has_glow = bool(glow or a.get('orb') or face in ('skull', 'imp', 'pumpkin', 'shadow') or 'hand_flame' in show
                    or a.get('lamp'))
    extra = dict(scale=a.get('scale', 1.0))
    if ghost:
        extra['translucent'] = True
    if a.get('legless'):
        extra['legless'] = True
    return Variant(m['id'], show=show, hide=hide, palette=pal, poses=poses, paint=deco, glow=has_glow,
                   overrides=overrides, extra=extra)


# -------------------------------------------------------------------------------- beast

BUGS = ('scorpion', 'crab', 'crayfish', 'beetle', 'centipede', 'spider')
REPTILES = ('lizard', 'serpent', 'tortoise', 'toad')


def _beast(m):
    import rig_beast as RB
    a = m['art']
    kind = a['kind']
    body = a.get('body', '#7a6a58')
    style = body.get('style', 'fur') if isinstance(body, dict) else ('chitin' if kind in BUGS else
                                                                     'scale' if kind in REPTILES else 'fur')
    b = _styled(body, style)
    head = _styled(a.get('head', body), style)
    leg = _styled(a.get('leg', body), style)
    over = dict(leg=leg, paw=_darker(leg, 0.9), head=head, snout=_styled(a.get('snout', a.get('head', body)), style),
                jaw=_darker(head, 0.85), body=b, tail=_styled(a.get('tail', body), style),
                tail2=_styled(a.get('tail2', a.get('tail', body)), style))
    if 'mane' in a:
        over['mane'] = _styled(a['mane'], 'fur')
    if 'ear' in a:
        over['ear'] = _styled(a['ear'], style)
    else:
        over['ear'] = _darker(head, 0.8)
    if 'horn' in a:
        over['horn'] = a['horn']
    if 'crest' in a:
        over['crest'] = a['crest']
    if 'spines' in a:
        over['spine'] = _styled(a['spines'], 'crystal')
    if 'shell' in a:
        over['shell'] = a['shell']
    if 'claw' in a:
        over['claw'] = a['claw']
        over['arm'] = _darker(a['claw'], 0.9) if kind in BUGS else a['claw']
    if kind == 'spider':
        over['tail'] = _styled(a.get('shell', body), 'fur')
    if kind == 'fox':
        over['sting'] = _styled(a.get('tail2', '#f4ece0'), 'fur')
        over['barb'] = _styled(a.get('tail2', '#f4ece0'), 'fur')
    if kind in BUGS:
        over.update(leg=b, paw=_darker(b, 0.85), head=b, jaw=_darker(b, 0.8), tail=b, leg_s=b, sting=_darker(b, 0.7))
        over.setdefault('shell', _darker(b, 1.1))
        over.setdefault('arm', b)
        over.setdefault('claw', b)
    pal = RB.pal(**over)
    eye = a.get('eye', (30, 20, 10))
    glow = a.get('glow')
    kinds = {
        'wolf': {'snout', 'ear_r', 'ear_l', 'mane', 'tail'},
        'fox': {'snout', 'ear_r', 'ear_l', 'mane', 'tail'},
        'hound': {'snout', 'ear_r', 'ear_l', 'mane', 'tail', 'spines'},
        'boar': {'snout', 'ear_r', 'ear_l', 'mane', 'tail', 'horn_r', 'horn_l'},
        'bull': {'snout', 'ear_r', 'ear_l', 'mane', 'tail', 'horn_r', 'horn_l'},
        'ram': {'snout', 'ear_r', 'ear_l', 'tail', 'horn_r', 'horn_l', 'mane'},
        'rat': {'snout', 'ear_r', 'ear_l', 'tail'},
        'hare': {'snout', 'ear_r', 'ear_l', 'tail', 'horn_r'},
        'mole': {'snout', 'tail'},
        'lizard': {'lizard_snout', 'tail', 'crest'},
        'toad': {'lizard_snout', 'crest'},
        'serpent': {'lizard_snout', 'tail', 'crest'},
        'tortoise': {'lizard_snout', 'tail', 'carapace'},
        'scorpion': {'carapace', 'sting1', 'pincer_r', 'pincer_l', 'mid_leg_r1', 'mid_leg_r2', 'mid_leg_l1', 'mid_leg_l2'},
        'crab': {'carapace', 'pincer_r', 'pincer_l', 'mid_leg_r1', 'mid_leg_r2', 'mid_leg_l1', 'mid_leg_l2'},
        'crayfish': {'carapace', 'pincer_r', 'pincer_l', 'mid_leg_r1', 'mid_leg_r2', 'mid_leg_l1', 'mid_leg_l2', 'tail'},
        'beetle': {'carapace', 'mid_leg_r1', 'mid_leg_r2', 'mid_leg_l1', 'mid_leg_l2', 'horn_r'},
        'centipede': {'carapace', 'sting1', 'mid_leg_r1', 'mid_leg_r2', 'mid_leg_l1', 'mid_leg_l2', 'pincer_r', 'pincer_l'},
        'spider': {'tail', 'mid_leg_r1', 'mid_leg_r2', 'mid_leg_l1', 'mid_leg_l2'},
    }
    show = set(kinds[kind])
    if 'spines' in a:
        show.add('spines')
    if a.get('horns'):
        show |= {'horn_r', 'horn_l'}
    poses = {}
    overrides = None
    sprawl = {'front_right_leg': dict(rot=(0, 0, 55)), 'front_left_leg': dict(rot=(0, 0, -55)),
              'hind_right_leg': dict(rot=(0, 0, 55)), 'hind_left_leg': dict(rot=(0, 0, -55)),
              'front_right_paw': dict(rot=(0, 0, -55)), 'front_left_paw': dict(rot=(0, 0, 55)),
              'hind_right_paw': dict(rot=(0, 0, -55)), 'hind_left_paw': dict(rot=(0, 0, 55))}
    bug = {'front_right_leg': dict(rot=(0, 0, 40)), 'front_left_leg': dict(rot=(0, 0, -40)),
           'hind_right_leg': dict(rot=(0, 0, 40)), 'hind_left_leg': dict(rot=(0, 0, -40)),
           'front_right_paw': dict(rot=(0, 0, -40)), 'front_left_paw': dict(rot=(0, 0, 40)),
           'hind_right_paw': dict(rot=(0, 0, -40)), 'hind_left_paw': dict(rot=(0, 0, 40))}
    short_legs = {n: dict(scale=(1, 0.7, 1)) for n in ('front_right_leg', 'front_left_leg', 'hind_right_leg',
                                                      'hind_left_leg')}
    if kind == 'rat':
        poses.update(short_legs)
        poses.update({'ear_r': dict(scale=(1.6, 1.6, 1.0)), 'ear_l': dict(scale=(1.6, 1.6, 1.0)),
                      'tail': dict(scale=(0.5, 0.5, 1.5), rot=(25, 0, 0)), 'tail2': dict(scale=(0.6, 0.6, 1.4)),
                      'snout': dict(scale=(0.8, 0.8, 1.2))})
    elif kind == 'hare':
        poses.update(short_legs)
        poses.update({'ear_r': dict(scale=(1.0, 3.2, 1.0), rot=(-10, 0, 8)),
                      'ear_l': dict(scale=(1.0, 3.2, 1.0), rot=(-10, 0, -8)),
                      'tail': dict(scale=(1.8, 1.8, 0.35), rot=(-30, 0, 0)), 'tail2': dict(scale=(0.1, 0.1, 0.1)),
                      'snout': dict(scale=(0.85, 0.8, 0.7)), 'horn_r': dict(pos=(2.5, 0.0, -3.0), rot=(55, 0, 15),
                                                                            scale=(1.3, 1.5, 1.3)),
                      'hind_right_leg': dict(scale=(1.3, 0.75, 1.5)), 'hind_left_leg': dict(scale=(1.3, 0.75, 1.5)),
                      'torso': dict(scale=(1.15, 1.05, 0.9))})
    elif kind == 'mole':
        poses.update(sprawl)
        poses.update({'snout': dict(scale=(1.0, 0.9, 1.6)), 'torso': dict(scale=(1.3, 1.0, 1.15)),
                      'tail': dict(scale=(0.4, 0.4, 0.4), rot=(20, 0, 0)), 'tail2': dict(scale=(0.1, 0.1, 0.1)),
                      'front_right_paw': dict(rot=(0, 0, -55), scale=(1.8, 1.0, 1.8)),
                      'front_left_paw': dict(rot=(0, 0, 55), scale=(1.8, 1.0, 1.8))})
    elif kind == 'fox':
        if a.get('twin', True):
            show.add('sting1')
            poses.update({'sting1': dict(rot=(-50, 28, 0), scale=(1.5, 1.5, 0.8)), 'sting2': dict(rot=(-28, 0, 0)),
                          'sting3': dict(rot=(-25, 0, 0)), 'stinger': dict(rot=(-35, 0, 0), scale=(1.2, 1.2, 1.2))})
        poses.update({'ear_r': dict(scale=(1.3, 1.8, 1.0)), 'ear_l': dict(scale=(1.3, 1.8, 1.0)),
                      'tail': dict(scale=(1.7, 1.7, 1.4), rot=(30, -22, 0)), 'tail2': dict(scale=(1.4, 1.4, 1.2)),
                      'snout': dict(scale=(0.8, 0.8, 1.3)), 'mane': dict(scale=(0.85, 0.85, 0.85))})
    elif kind in ('boar', 'bull'):
        tusk = kind == 'boar'
        poses.update({'torso': dict(scale=(1.25, 1.15, 1.1)), 'head': dict(scale=(1.15, 1.1, 1.0)),
                      'snout': dict(scale=(1.25, 1.0, 1.05)), 'ear_r': dict(scale=(1.1, 0.8, 1.0)),
                      'ear_l': dict(scale=(1.1, 0.8, 1.0)), 'tail': dict(scale=(0.4, 0.4, 0.6)),
                      'mane': dict(scale=(0.9, 1.1, 1.2), pos=(0, 0.6, 0))})
        if tusk:
            poses['horn_r'] = dict(pos=(0.2, -4.0, -5.4), rot=(25, 0, 0), scale=(1.0, 0.7, 1.0))
            poses['horn_l'] = dict(pos=(-0.2, -4.0, -5.4), rot=(25, 0, 0), scale=(1.0, 0.7, 1.0))
        else:
            poses['horn_r'] = dict(rot=(25, 0, -55), scale=(1.4, 1.3, 1.4))
            poses['horn_l'] = dict(rot=(25, 0, 55), scale=(1.4, 1.3, 1.4))
            poses['torso'] = dict(scale=(1.4, 1.3, 1.15))
            poses['head'] = dict(scale=(1.2, 1.15, 1.0))
        if a.get('calf'):
            poses['horn_r'] = dict(rot=(25, 0, -45), scale=(0.7, 0.5, 0.7))
            poses['horn_l'] = dict(rot=(25, 0, 45), scale=(0.7, 0.5, 0.7))
            poses['mane'] = dict(scale=(0.6, 0.5, 0.5), pos=(0, 2.0, 1.0))
            poses['tail'] = dict(scale=(0.5, 0.5, 1.1), rot=(25, 0, 0))
    elif kind == 'ram':
        poses.update({'torso': dict(scale=(1.3, 1.25, 1.1)), 'horn_r': dict(rot=(40, 0, -70), scale=(1.6, 1.3, 1.6)),
                      'horn_l': dict(rot=(40, 0, 70), scale=(1.6, 1.3, 1.6)), 'tail': dict(scale=(0.5, 0.5, 0.5)),
                      'mane': dict(scale=(1.2, 1.3, 1.25)), 'snout': dict(scale=(0.9, 1.0, 1.0))})
    elif kind == 'hound':
        poses.update({'torso': dict(scale=(1.0, 1.05, 1.15)), 'head': dict(scale=(1.05, 1.0, 1.1))})
    elif kind == 'wolf':
        if m['lord']:
            poses['mane'] = dict(scale=(1.15, 1.2, 1.2))
    if kind in REPTILES:
        poses.update(sprawl)
        poses['torso'] = dict(scale=(1.15, 0.85, 1.25))
        poses['head'] = dict(scale=(1.0, 0.75, 1.1))
        poses['tail'] = dict(scale=(1.6, 1.4, 1.4), rot=(30, 0, 0))
        poses['tail2'] = dict(scale=(1.2, 1.1, 1.5))
        if kind == 'serpent':
            poses['torso'] = dict(scale=(0.95, 0.8, 1.7))
            poses['tail'] = dict(scale=(1.3, 1.2, 2.0), rot=(25, 0, 0))
            poses['tail2'] = dict(scale=(1.1, 1.0, 2.0))
            for leg in ('front_right_leg', 'front_left_leg', 'hind_right_leg', 'hind_left_leg'):
                poses[leg] = dict(rot=poses[leg]['rot'], scale=(0.6, 0.5, 0.6))
        if kind == 'toad':
            poses['torso'] = dict(scale=(1.45, 1.05, 1.0))
            poses['head'] = dict(scale=(1.4, 0.9, 1.0))
            poses['hind_right_leg'] = dict(rot=(-20, 0, 60), scale=(1.4, 1.0, 1.6))
            poses['hind_left_leg'] = dict(rot=(-20, 0, -60), scale=(1.4, 1.0, 1.6))
        if kind == 'tortoise':
            poses['torso'] = dict(scale=(1.3, 1.0, 1.2))
            poses['carapace'] = dict(scale=(1.45, 2.4, 1.3), pos=(0, 0.6, 0))
            poses['tail'] = dict(scale=(0.7, 0.7, 0.6), rot=(30, 0, 0))
            poses['head'] = dict(scale=(0.9, 0.8, 1.0))
    if kind in BUGS:
        poses.update(bug)
        poses['torso'] = dict(scale=(1.25, 0.7, 1.0))
        poses['head'] = dict(scale=(1.1, 0.6, 0.7), pos=(0, -0.5, 0.5))
        overrides = {'attack1': 'scorpion_attack1', 'attack2': 'scorpion_attack2'}
        if kind in ('crab', 'crayfish'):
            poses['torso'] = dict(scale=(1.6, 0.75, 0.9))
            poses['carapace'] = dict(scale=(1.25, 1.6, 1.05), pos=(0, 0.3, 0))
            poses['pincer_r'] = dict(scale=(1.4, 1.4, 1.2), rot=(0, 10, 0))
            poses['pincer_l'] = dict(scale=(1.4, 1.4, 1.2), rot=(0, -10, 0))
            if kind == 'crayfish':
                poses['torso'] = dict(scale=(1.1, 0.75, 1.2))
                poses['tail'] = dict(scale=(2.2, 1.0, 1.1), rot=(5, 0, 0))
                poses['tail2'] = dict(scale=(1.4, 1.0, 1.0), rot=(30, 0, 0))
        if kind == 'beetle':
            poses['carapace'] = dict(scale=(1.2, 1.7, 1.1), pos=(0, 0.4, 0.3))
            poses['horn_r'] = dict(pos=(2.5, -2.0, -5.0), rot=(60, 0, 15), scale=(2.0, 3.5, 2.0))
            overrides = None
        if kind == 'centipede':
            poses['torso'] = dict(scale=(1.0, 0.7, 1.9))
            poses['carapace'] = dict(scale=(1.0, 1.4, 1.0))
            poses['pincer_r'] = dict(scale=(0.8, 0.8, 0.7), rot=(0, 35, 0))
            poses['pincer_l'] = dict(scale=(0.8, 0.8, 0.7), rot=(0, -35, 0))
        if kind == 'spider':
            poses['torso'] = dict(scale=(1.2, 0.85, 0.75))
            poses['tail'] = dict(scale=(4.2, 3.6, 1.6), rot=(32, 0, 0), pos=(0, 0.5, -1.0))
            poses['tail2'] = dict(scale=(0.1, 0.1, 0.1))
            poses['head'] = dict(scale=(1.0, 0.8, 0.9), pos=(0, -0.3, 0.5))
            for n in ('front_right_leg', 'front_left_leg', 'hind_right_leg', 'hind_left_leg'):
                poses[n] = dict(rot=poses[n]['rot'], scale=(1.0, 1.5, 1.0))
            for n in ('mid_leg_r1', 'mid_leg_r2', 'mid_leg_l1', 'mid_leg_l2'):
                poses[n] = dict(scale=(1.5, 1.0, 1.0))
            overrides = None
    if a.get('whiskers'):
        show.add('horn_r')
        show.add('horn_l')
        poses['horn_r'] = dict(pos=(0, -3.0, -6.5), rot=(-90, 0, -60), scale=(0.6, 1.8, 0.6))
        poses['horn_l'] = dict(pos=(0, -3.0, -6.5), rot=(-90, 0, 60), scale=(0.6, 1.8, 0.6))
    if a.get('horns') and kind in REPTILES:
        poses['horn_r'] = dict(rot=(-50, 0, -10), scale=(1.4, 1.6, 1.4), pos=(0, -0.6, 2.0))
        poses['horn_l'] = dict(rot=(-50, 0, 10), scale=(1.4, 1.6, 1.4), pos=(0, -0.6, 2.0))

    def deco(p):
        if kind in ('wolf', 'fox', 'hound', 'boar', 'bull', 'ram', 'rat', 'hare', 'mole'):
            RB.face(p, eye=eye, glow=glow, nose=a.get('nose', (30, 22, 20)),
                    fangs=kind not in ('rat', 'ram', 'hare', 'mole', 'bull'))
            if a.get('belly'):
                RB.belly(p, rgb(a['belly']))
            if kind == 'hare' or kind == 'rat':
                for side in ('front', 'back'):
                    e = p.face('ear_r', side)
                    e.px(0, 1, (230, 160, 170))
                    e.px(1, 1, (230, 160, 170))
            if kind == 'mole':
                sn = p.face('snout', 'front')
                sn.rect(0, 0, sn.w - 1, sn.h - 1, a.get('nose', (230, 150, 160)))
                for side in ('front', 'right', 'left', 'top'):
                    fc = p.face('front_right_paw', side)
                    fc.hline(0, fc.w - 1, fc.h - 1, rgb(a.get('claw', '#e8d8c8')))
            if a.get('spots'):
                for fc in p.all_faces('torso', sides_only=True) + p.all_faces('head', sides_only=True):
                    for y in range(fc.h):
                        for x in range(fc.w):
                            if ((x * 7 + y * 11 + len(fc.face)) % 9) < 2:
                                fc.px(x, y, (40, 34, 30))
            if a.get('nose_ring'):
                sn = p.face('snout', 'front')
                sn.px(1, 2, (230, 200, 80))
                sn.px(1, 1, (230, 200, 80))
            if kind in ('boar', 'bull'):
                sn = p.face('snout', 'front')
                sn.px(0, 1, (30, 20, 18))
                sn.px(2, 1, (30, 20, 18))
        else:
            fc = p.face('head', 'front')
            g = p.face('head', 'front', glow=True)
            if kind in BUGS:
                for x in range(1, fc.w - 1):
                    if x % 2 == 1:
                        fc.px(x, 1, eye)
                        if glow:
                            g.px(x, 1, glow)
                fc.hline(1, fc.w - 2, fc.h - 1, shade(fc.get(1, 1), 0.4))
            else:
                fc.px(0, 1, eye)
                fc.px(fc.w - 1, 1, eye)
                if glow:
                    g.px(0, 1, glow)
                    g.px(g.w - 1, 1, glow)
                sn = p.face('lizard_snout', 'front')
                sn.hline(0, sn.w - 1, 1, shade(_base_rgb(head), 0.45))
                sn.px(1, 0, (30, 20, 16))
                sn.px(sn.w - 2, 0, (30, 20, 16))
            if a.get('belly'):
                RB.belly(p, rgb(a['belly']))
        if a.get('barnacles') or a.get('mossy'):
            col = (220, 214, 200) if a.get('barnacles') else (80, 120, 50)
            for f2 in p.all_faces('carapace'):
                for y in range(f2.h):
                    for x in range(f2.w):
                        if (x * 5 + y * 3) % 7 == 0:
                            f2.px(x, y, col)
        if a.get('ore'):
            for f2 in p.all_faces('carapace'):
                g2 = p.face('carapace', f2.face, glow=True)
                for y in range(f2.h):
                    for x in range(f2.w):
                        if (x * 7 + y * 5) % 11 == 0:
                            f2.px(x, y, a['ore'])
                            g2.px(x, y, a['ore'])
        if 'spines' in a and glow:
            for f3 in p.all_faces('spines', glow=True):
                for y in range(f3.h):
                    for x in range(f3.w):
                        if (x + y) % 2 == 0:
                            f3.px(x, y, glow)
        if kind == 'spider':
            for side in ('top', 'right', 'left'):
                fc = p.face('tail', side)
                for y in range(fc.h):
                    for x in range(fc.w):
                        if (x + y) % 3 == 0:
                            fc.px(x, y, shade(fc.get(x, y), 0.72))
            fc = p.face('tail', 'back')
            fc.rect(0, 0, fc.w - 1, fc.h - 1, shade(fc.get(0, 0), 0.8))
        if a.get('shell') and isinstance(a['shell'], dict) and a['shell'].get('style') == 'gold':
            fc = p.face('carapace', 'top')
            fc.vline(fc.w // 2, 0, fc.h - 1, (90, 60, 20))

    return Variant(m['id'], show=show, palette=pal, poses=poses, paint=deco, glow=bool(glow or a.get('ore')),
                   overrides=overrides, extra=dict(scale=a.get('scale', 1.0)))


# -------------------------------------------------------------------------------- golem

def _golem(m):
    import rig_golem as RG
    a = m['art']
    mat = a['mat']
    darker = _darker(mat, 0.88)
    over = dict(head=mat, body=mat, arm=mat, fist=darker, leg=mat, foot=darker, pelvis=_darker(mat, 0.94),
                plate=a.get('plate', _darker(mat, 0.8)))
    for key in ('canopy', 'moss', 'nemes', 'ice', 'branch', 'spike'):
        if key in a:
            over[key] = a[key]
    if 'core' in a:
        over['core'] = {'base': a['core'], 'style': 'glowcore'}
    if 'runes' in a:
        over['rune'] = {'base': _hex(a['runes']), 'style': 'glowcore'}
    pal = RG.pal(**over)
    eye = a.get('eye', (255, 200, 60))
    glow = a.get('glow')
    show = set(a.get('show', ()))
    poses = {}
    if a.get('ape'):
        poses = {'chest': dict(rot=(18, 0, 0)), 'head': dict(rot=(-14, 0, 0), scale=(0.9, 0.9, 0.9), pos=(0, -0.5, -1.0)),
                 'right_arm': dict(scale=(1.0, 1.12, 1.0), rot=(-22, 0, 4)),
                 'left_arm': dict(scale=(1.0, 1.12, 1.0), rot=(-22, 0, -4)),
                 'right_leg': dict(scale=(1.0, 0.85, 1.0)), 'left_leg': dict(scale=(1.0, 0.85, 1.0))}
    if 'canopy' in show and a.get('dots'):
        poses['canopy'] = dict(scale=(1.15, 1.1, 1.15), pos=(0, -0.5, 0))

    def deco(p):
        RG.golem_face(p, eye, glow=glow)
        if a.get('ape'):
            f = p.face('head', 'front')
            fc = rgb(a.get('face_col', '#c89a8a'))
            f.rect(1, 2, 6, 7, fc)
            f.hline(1, 6, 1, shade(_base_rgb(mat), 0.8))
            f.px(2, 3, eye)
            f.px(5, 3, eye)
            f.rect(3, 5, 4, 5, shade(fc, 0.7))
            f.hline(2, 5, 7, shade(fc, 0.5))
            if glow:
                g = p.face('head', 'front', glow=True)
                g.px(2, 3, glow)
                g.px(5, 3, glow)
        if a.get('runes'):
            RG.runes(p, 'chest', a['runes'], every=4)
        if a.get('ore'):
            for part in ('chest', 'right_arm', 'head', 'right_forearm', 'pelvis'):
                for fc in p.all_faces(part, sides_only=True):
                    g = p.face(part, fc.face, glow=True)
                    for y in range(1, fc.h - 1):
                        for x in range(1, fc.w - 1):
                            if (x * 13 + y * 7 + len(part)) % 11 == 0:
                                fc.px(x, y, a['ore'])
                                g.px(x, y, a['ore'])
        if 'core' in a:
            for fc in p.all_faces('core', glow=True):
                fc.rect(0, 0, fc.w - 1, fc.h - 1, rgb(a['core']))
        if 'rune_halo' in show and a.get('runes'):
            for fc in p.all_faces('rune_halo', glow=True):
                fc.rect(0, 0, fc.w - 1, fc.h - 1, a['runes'])
        if 'headdress' in show:
            hf = p.face('headdress', 'front')
            for y in range(3, hf.h):
                for x in range(1, hf.w - 1):
                    hf.clear(x, y)
            stripe = shade(_base_rgb(a['nemes']), 0.55)
            for fc in p.all_faces('headdress', sides_only=True):
                for y in range(0, fc.h, 2):
                    fc.hline(0, fc.w - 1, y, stripe)
        if a.get('dots') and 'canopy' in show:
            for fc in p.all_faces('canopy'):
                for y in range(fc.h):
                    for x in range(fc.w):
                        if (x * 3 + y * 5) % 7 == 0:
                            fc.px(x, y, (250, 246, 236))
            bottom = p.face('canopy', 'bottom')
            bottom.rect(0, 0, bottom.w - 1, bottom.h - 1, (236, 226, 206))
            for x in range(0, bottom.w, 2):
                bottom.vline(x, 1, bottom.h - 2, (214, 200, 176))
        if a.get('mossy'):
            for part in ('head', 'chest', 'right_arm', 'right_shin'):
                fc = p.face(part, 'top')
                fc.rect(0, 0, fc.w - 1, fc.h - 1, (70, 110, 46))
                for side in ('front', 'right', 'left', 'back'):
                    s = p.face(part, side)
                    for x in range(s.w):
                        for y in range(0, 1 + (x * 5) % 3):
                            s.px(x, y, (80, 122, 52))
        if a.get('drips'):
            for part in ('chest', 'head', 'right_arm'):
                for fc in p.all_faces(part, sides_only=True):
                    for x in range(0, fc.w, 3):
                        for y in range(fc.h - 1 - (x % 4), fc.h):
                            fc.px(x, y, (60, 46, 32))
        if a.get('barnacles'):
            for part in ('chest', 'right_arm', 'head', 'right_leg'):
                for fc in p.all_faces(part, sides_only=True):
                    for y in range(fc.h):
                        for x in range(fc.w):
                            if (x * 5 + y * 3 + len(part)) % 9 == 0:
                                fc.px(x, y, (232, 224, 210))
        if mat.get('style') == 'rope':
            for part in ('chest', 'pelvis'):
                fc = p.face(part, 'front')
                fc.hline(0, fc.w - 1, fc.h // 2, (120, 80, 40))
                fc.hline(0, fc.w - 1, fc.h // 2 + 1, (96, 64, 32))

    return Variant(m['id'], show=show, palette=pal, poses=poses, paint=deco,
                   glow=bool(glow or a.get('runes') or a.get('core') or a.get('ore')),
                   extra=dict(scale=a.get('scale', 1.0)))


# -------------------------------------------------------------------------------- flyer

def _flyer(m):
    import rig_flyer as RF
    a = m['art']
    kind = a['kind']
    eye = a.get('eye', (255, 255, 255))
    glow = a.get('glow')
    extra = dict(scale=a.get('scale', 1.0))
    hide = set()
    poses = {}
    if kind in ('bird', 'harpy', 'bug', 'moth', 'bat'):
        mstyle = {'bug': 'chitin', 'moth': 'fur', 'bat': 'fur'}.get(kind, 'feather')
        body = {'base': a['body'], 'style': mstyle}
        wing = a['wing']
        tip = dict(wing, base=_hex(shade(_base_rgb(wing), 0.82)))
        if kind == 'moth':
            tip = dict(wing, base=_hex(shade(_base_rgb(wing), 0.9)))
        crest = {'base': a.get('crest', a['body']), 'style': mstyle}
        facec = {'base': a.get('face', a['body']), 'style': mstyle if kind not in ('harpy',) else 'skin'}
        pal = RF.pal(body=body, wing=wing, wingtip=tip, thigh=dict(body), crest=crest, face=facec,
                     arm=dict(facec) if kind == 'harpy' else dict(body),
                     forearm=dict(facec) if kind == 'harpy' else dict(body), tail=dict(body), tail2=dict(body),
                     tail3=dict(body), shin=_hex(a.get('beak', (220, 190, 80))) if kind != 'bug' else '#1e1a14',
                     talon='#2a2420', claw='#2a2420')
        show = {'right_wing', 'left_wing', 'right_leg', 'left_leg'}
        if kind in ('bird', 'harpy'):
            show |= {'crest', 'claws_r', 'claws_l'} if kind == 'harpy' else {'crest'}
        if kind in ('bird', 'bug', 'moth', 'bat'):
            hide |= {'right_arm', 'left_arm'}
        if kind == 'bird':
            poses = {'head': dict(scale=(0.95, 0.95, 1.1))}
            if a.get('long_neck'):
                poses['head'] = dict(scale=(0.85, 0.9, 1.1), pos=(0, 2.2, -1.0))
                poses['body'] = dict(rot=(-12, 0, 0))
            if a.get('owl'):
                poses['head'] = dict(scale=(1.25, 1.15, 1.1), pos=(0, -0.4, 0))
                poses['crest'] = dict(scale=(1.0, 0.6, 0.5))
            if a.get('lion_tail'):
                show.add('tail1')
                poses['tail1'] = dict(scale=(0.35, 1.0, 0.45), rot=(35, 0, 0), pos=(0, 0, 1.5))
                poses['tail2'] = dict(scale=(0.6, 1.0, 0.8))
                poses['tail3'] = dict(scale=(2.2, 0.8, 2.2))
        if kind == 'bat':
            poses = {'head': dict(scale=(1.0, 0.9, 0.9)), 'body': dict(scale=(0.9, 0.9, 0.9)),
                     'right_wing': dict(scale=(1.1, 1.2, 1.0)), 'left_wing': dict(scale=(1.1, 1.2, 1.0)),
                     'right_leg': dict(scale=(0.6, 0.6, 0.6)), 'left_leg': dict(scale=(0.6, 0.6, 0.6))}
            show |= {'crest'}
            poses['crest'] = dict(scale=(4.0, 0.7, 0.25), pos=(0, 0.6, 0))
        if kind in ('bug', 'moth'):
            show = {'right_wing', 'left_wing', 'tail1', 'right_leg', 'left_leg'}
            poses = {'tail1': dict(scale=(1.0, 1.25, 1.4)), 'right_leg': dict(scale=(0.6, 0.7, 0.6)),
                     'left_leg': dict(scale=(0.6, 0.7, 0.6)), 'right_wing': dict(scale=(0.7, 0.75, 1.0)),
                     'left_wing': dict(scale=(0.7, 0.75, 1.0))}
            if kind == 'bug':
                poses['body'] = dict(rot=(70, 0, 0))
                poses['head'] = dict(rot=(-70, 0, 0), pos=(0, 0.5, -1.0))
                poses['right_leg'] = dict(rot=(-40, 0, 0), scale=(0.6, 0.7, 0.6))
                poses['left_leg'] = dict(rot=(-40, 0, 0), scale=(0.6, 0.7, 0.6))
            if kind == 'moth':
                poses['right_wing'] = dict(scale=(0.85, 1.1, 1.0))
                poses['left_wing'] = dict(scale=(0.85, 1.1, 1.0))
                poses['body'] = dict(rot=(25, 0, 0))
                poses['head'] = dict(rot=(-25, 0, 0))
            if a.get('long_tail'):
                poses['tail1'] = dict(scale=(0.6, 1.0, 0.8))
                poses['tail2'] = dict(scale=(0.9, 1.6, 1.2))
                poses['tail3'] = dict(scale=(1.4, 2.0, 1.6))
                poses['right_wing'] = dict(scale=(0.85, 0.5, 1.0))
                poses['left_wing'] = dict(scale=(0.85, 0.5, 1.0))

        def deco(p):
            f = p.face('head', 'front')
            g = p.face('head', 'front', glow=True)
            if kind == 'harpy':
                human_face(f, _base_rgb(a.get('face', '#e0c8b0')), eye_col=eye, brows=(90, 90, 100))
                hair = rgb(a.get('hair', '#e8ecf4'))
                for side in ('top', 'back', 'right', 'left'):
                    fc = p.face('head', side)
                    for y in range(0, fc.h if side in ('top', 'back') else 3):
                        for x in range(fc.w):
                            fc.px(x, y, shade(hair, 0.9 + 0.1 * ((x + y) % 2)))
                f.hline(0, f.w - 1, 0, hair)
                return
            if kind == 'bug' or kind == 'moth':
                f.rect(0, 1, 1, 3, eye)
                f.rect(f.w - 2, 1, f.w - 1, 3, eye)
                if glow:
                    g.rect(0, 1, 1, 3, glow)
                    g.rect(g.w - 2, 1, g.w - 1, 3, glow)
                if a.get('stripes'):
                    for part in ('body', 'tail1', 'tail2', 'tail3'):
                        for fc in p.all_faces(part, sides_only=True):
                            for y in range(0, fc.h, 3):
                                fc.hline(0, fc.w - 1, y, a['stripes'])
                if kind == 'moth':
                    for part in ('right_wing', 'right_wing_tip'):
                        for side in ('front', 'back'):
                            fc = p.face(part, side)
                            cx, cy = fc.w // 2, fc.h // 2
                            fc.rect(cx - 1, cy - 1, cx + 1, cy + 1, (90, 70, 140))
                            fc.px(cx, cy, (240, 230, 255))
                            gl = p.face(part, side, glow=True)
                            gl.px(cx, cy, glow or (220, 200, 255))
                return
            if kind == 'bat':
                f.px(1, 2, eye)
                f.px(4, 2, eye)
                f.px(2, 4, (240, 236, 220))
                f.px(3, 4, (240, 236, 220))
                if glow:
                    g.px(1, 2, glow)
                    g.px(4, 2, glow)
                return
            f.px(1, 2, eye)
            f.px(f.w - 2, 2, eye)
            beak = a.get('beak', (230, 190, 60))
            if a.get('owl'):
                f.rect(0, 1, 2, 3, (236, 226, 200))
                f.rect(3, 1, 5, 3, (236, 226, 200))
                f.px(1, 2, eye)
                f.px(4, 2, eye)
                f.rect(2, 3, 3, 4, beak)
            else:
                f.rect(2, 3, f.w - 3, 4, beak)
                f.px(2, 4, shade(beak, 0.75))
            if a.get('comb'):
                for side in ('front', 'right', 'left'):
                    fc = p.face('head', side)
                    fc.rect(0, 5, fc.w - 1, 5, (200, 40, 30)) if side == 'front' else None
            if glow:
                g.px(1, 2, glow)
                g.px(g.w - 2, 2, glow)
        return Variant(m['id'], show=show, hide=hide, palette=pal, poses=poses, paint=deco, glow=bool(glow) or kind == 'moth',
                       extra=extra)
    if kind == 'wisp':
        b = {'base': a['base'], 'style': a.get('style', 'cloud')}
        pal = RF.pal(face={'base': a['base'], 'style': 'glowcore'}, arm=dict(b), forearm=dict(b), body=dict(b),
                     tail=dict(b), tail2=dict(b), tail3=dict(b), puff=dict(b),
                     core={'base': a.get('core', '#ffffff'), 'style': 'glowcore'})
        show = {'orbit', 'tail1'}
        hide |= {'right_arm', 'left_arm'}
        poses = {'head': dict(scale=(1.7, 1.7, 1.7), pos=(0, -6.0, 0)), 'body': dict(scale=(0.7, 0.7, 0.7)),
                 'orbit': dict(scale=(0.6, 0.6, 0.6)), 'tail1': dict(scale=(0.9, 1.0, 0.9)),
                 'tail2': dict(scale=(1.0, 1.2, 1.0)), 'tail3': dict(scale=(1.0, 1.4, 1.0))}

        def deco(p):
            f = p.face('head', 'front')
            base = rgb(a['base'])
            f.px(1, 2, eye)
            f.px(4, 2, eye)
            f.hline(2, 3, 4, shade(base, 0.55))
            g = p.face('head', 'front', glow=True)
            for part in ('head', 'body', 'tail1', 'tail2', 'tail3', 'orbit'):
                for fc in p.all_faces(part, glow=True):
                    src_fc = p.face(part, fc.face)
                    for y in range(fc.h):
                        for x in range(fc.w):
                            if p.img[src_fc.v + y, src_fc.u + x, 3] > 0:
                                fc.px(x, y, shade(src_fc.get(x, y), 0.85 if part == 'head' else 0.6))
            g.px(1, 2, glow or eye)
            g.px(4, 2, glow or eye)
        return Variant(m['id'], show=show, hide=hide, palette=pal, poses=poses, paint=deco, glow=True, extra=extra)
    if kind in ('spirit', 'eye'):
        style = a.get('style', 'cloud') if kind == 'spirit' else 'void'
        b = {'base': a['base'], 'style': style}
        pal = RF.pal(face=dict(b) if kind == 'spirit' else {'base': a['base'], 'style': 'skin'}, arm=dict(b),
                     forearm=dict(b), body=dict(b), tail=dict(b), tail2=dict(b), tail3=dict(b), puff=dict(b),
                     core={'base': a.get('core', '#ffffff'), 'style': 'glowcore'})
        show = {'orbit', 'core', 'tail1'}
        if kind == 'eye':
            hide |= {'right_arm', 'left_arm'}
            poses = {'head': dict(scale=(2.2, 2.2, 2.2), pos=(0, -7.0, 0))}
        if a.get('veil'):
            show.add('robe_tatters')
            pal['robe'] = {'base': a['base'], 'style': style if style != 'ice' else 'ghost'}
            poses['robe_tatters'] = dict(scale=(1.1, 1.6, 1.1))
        if a.get('cloud_wings'):
            show |= {'right_wing', 'left_wing'}
            pal['wing'] = {'base': a['base'], 'style': 'cloud'}
            pal['wingtip'] = {'base': _hex(shade(rgb(a['base']), 0.9)), 'style': 'cloud'}
            poses['right_wing'] = dict(scale=(0.8, 0.9, 1.0))
            poses['left_wing'] = dict(scale=(0.8, 0.9, 1.0))
        if a.get('sprite_wings'):
            show |= {'right_wing', 'left_wing', 'halo'}
            pal['wing'] = {'base': '#e8f4ff', 'style': 'membrane'}
            pal['wingtip'] = {'base': '#d0e4f8', 'style': 'membrane'}
            pal['halo'] = {'base': a.get('halo', '#ffe080'), 'style': 'gold'}
            poses['right_wing'] = dict(scale=(0.55, 0.8, 1.0))
            poses['left_wing'] = dict(scale=(0.55, 0.8, 1.0))
            poses['halo'] = dict(scale=(1.4, 1.0, 1.4), pos=(0, 1.5, 0))
        if a.get('water_crown'):
            show.add('ice_crown')
            pal['crown'] = {'base': '#a0e8ff', 'style': 'ice'}

        def deco(p):
            f = p.face('head', 'front')
            g = p.face('head', 'front', glow=True)
            if kind == 'eye':
                core = rgb(a.get('core', '#c060ff'))
                f.rect(0, 0, f.w - 1, f.h - 1, (226, 216, 232))
                f.rect(1, 1, f.w - 2, f.h - 2, core)
                f.rect(2, 2, f.w - 3, f.h - 3, (16, 8, 24))
                f.px(1, 1, lighten(core, 0.4))
                for (x, y) in ((0, 2), (0, 4), (5, 1), (5, 3)):
                    f.px(x, y, (200, 60, 80))
                g.rect(1, 1, g.w - 2, g.h - 2, glow or core)
                g.rect(2, 2, g.w - 3, g.h - 3, (40, 10, 60))
                for side in ('right', 'left', 'top', 'bottom', 'back'):
                    fc = p.face('head', side)
                    for y in range(fc.h):
                        for x in range(fc.w):
                            if (x + y * 2) % 5 == 0:
                                fc.px(x, y, (90, 40, 120))
            else:
                f.rect(1, 2, f.w - 2, 2, shade(rgb(a['base']), 0.5))
                f.px(1, 2, eye)
                f.px(f.w - 2, 2, eye)
                if glow:
                    g.px(1, 2, glow)
                    g.px(g.w - 2, 2, glow)
            for fc in p.all_faces('core', glow=True):
                fc.rect(0, 0, fc.w - 1, fc.h - 1, rgb(a.get('core', '#ffffff')))
        return Variant(m['id'], show=show, hide=hide, palette=pal, poses=poses, paint=deco, glow=True, extra=extra)
    if kind == 'wraith':
        ghost = {'base': a['base'], 'style': 'ghost'}
        pal = RF.pal(face=dict(ghost), arm=dict(ghost), forearm=dict(ghost), body=dict(ghost),
                     hood={'base': a['base'], 'style': 'ghost', 'ragged': False}, tail=dict(ghost), tail2=dict(ghost),
                     tail3=dict(ghost), claw='#e0e8f4', crown=a.get('crown', {'base': '#c8f0ff', 'style': 'crystal'}),
                     robe=dict(ghost))
        show = {'hood', 'ice_crown', 'tail1', 'claws_r', 'claws_l', 'robe_tatters'}

        def deco(p):
            f = p.face('head', 'front')
            f.rect(0, 0, f.w - 1, f.h - 1, shade(rgb(a['base']), 0.3))
            f.px(1, 2, eye)
            f.px(4, 2, eye)
            g = p.face('head', 'front', glow=True)
            g.px(1, 2, glow or eye)
            g.px(4, 2, glow or eye)
            hf = p.face('hood', 'front')
            for y in range(1, hf.h):
                for x in range(1, hf.w - 1):
                    hf.clear(x, y)
            for fc in p.all_faces('ice_crown', glow=True):
                fc.rect(0, 0, fc.w - 1, fc.h - 1, shade(glow or eye, 0.8))
            if a.get('embers'):
                for part in ('body', 'robe_tatters', 'tail1', 'right_forearm'):
                    for fc in p.all_faces(part, sides_only=True):
                        gl = p.face(part, fc.face, glow=True)
                        for y in range(fc.h):
                            for x in range(fc.w):
                                if (x * 7 + y * 3 + len(part)) % 6 == 0:
                                    fc.px(x, y, (255, 120, 40))
                                    gl.px(x, y, (255, 110, 30))
        extra = dict(scale=a.get('scale', 1.05), translucent=True)
        return Variant(m['id'], show=show, palette=pal, paint=deco, glow=True, extra=extra)
    if kind == 'flame':
        fire = {'base': a['base'], 'style': 'flame'}
        pal = RF.pal(face={'base': a['base'], 'style': 'skin'}, arm=dict(fire), forearm=dict(fire), body=dict(fire),
                     hair=dict(fire), tail=dict(fire), tail2=dict(fire), tail3=dict(fire), puff=dict(fire),
                     core={'base': a.get('core', '#fff0a0'), 'style': 'glowcore'})
        show = {'flame_hair', 'orbit', 'core', 'tail1'}

        def deco(p):
            f = p.face('head', 'front')
            f.rect(0, 0, f.w - 1, f.h - 1, lighten(rgb(a['base']), 0.25))
            if a.get('cyclops'):
                f.rect(1, 1, 4, 3, (255, 250, 230))
                f.rect(2, 1, 3, 3, (255, 140, 30))
                f.px(2, 2, (40, 10, 0))
                f.px(3, 2, (40, 10, 0))
            else:
                f.px(1, 2, eye)
                f.px(4, 2, eye)
                f.hline(2, 3, 4, shade(rgb(a['base']), 0.5))
            for part in ('head', 'body', 'flame_hair', 'tail1', 'tail2', 'tail3', 'right_arm', 'left_arm',
                         'right_forearm', 'left_forearm', 'orbit', 'core'):
                for fc in p.all_faces(part, glow=True):
                    src = p.face(part, fc.face)
                    for y in range(fc.h):
                        for x in range(fc.w):
                            if p.img[src.v + y, src.u + x, 3] > 0:
                                fc.px(x, y, shade(src.get(x, y), 0.8))
        return Variant(m['id'], show=show, palette=pal, paint=deco, glow=True, extra=extra)
    if kind == 'sentinel':
        metal = {'base': a['metal'], 'style': 'metal'}
        pal = RF.pal(face=dict(metal), arm=dict(metal), forearm=dict(metal),
                     body={'base': a['metal'], 'style': 'mech', 'panel': 3}, helm=dict(metal), fin=dict(metal),
                     wing={'base': a['metal'], 'style': 'metal', 'rivets': False},
                     wingtip={'base': _hex(shade(rgb(a['metal']), 0.9)), 'style': 'metal', 'rivets': False},
                     tail=dict(metal), tail2=dict(metal), tail3=dict(metal),
                     core={'base': a.get('core', '#a0d8ff'), 'style': 'glowcore'}, plate=dict(metal),
                     halo={'base': a.get('halo', '#ffe080'), 'style': 'glowcore'})
        show = {'helm', 'halo', 'right_wing', 'left_wing', 'plate_r', 'plate_l', 'core', 'tail1'}

        def deco(p):
            hf = p.face('helm', 'front')
            hf.hline(1, hf.w - 2, 3, (20, 20, 30))
            g = p.face('helm', 'front', glow=True)
            for x in range(1, g.w - 1):
                g.px(x, 3, eye)
            for fc in p.all_faces('halo', glow=True):
                fc.rect(0, 0, fc.w - 1, fc.h - 1, rgb(a.get('halo', '#ffe080')))
            for fc in p.all_faces('core', glow=True):
                fc.rect(0, 0, fc.w - 1, fc.h - 1, rgb(a.get('core', '#a0d8ff')))
            for part in ('right_wing', 'right_wing_tip'):
                for side in ('front', 'back'):
                    fc = p.face(part, side, glow=True)
                    for x in range(1, fc.w - 1, 3):
                        fc.px(x, fc.h // 2, rgb(a.get('core', '#a0d8ff')))
        return Variant(m['id'], show=show, palette=pal, paint=deco, glow=True, extra=extra)
    raise ValueError('unknown flyer kind ' + kind)
