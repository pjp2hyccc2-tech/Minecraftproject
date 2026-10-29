#!/usr/bin/env python3
"""Generates src/main/lang/{ja_jp,en_us}/armor.json (armor pieces, set names, bonuses, charms, tooltips)."""
import json
import os

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..')
ja, en = {}, {}


def put(key, j, e):
    ja[key] = j
    en[key] = e


PIECES = [('helm', 'ヘルム', 'Helm'), ('mail', 'メイル', 'Mail'), ('coil', 'フォールド', 'Coil'), ('greaves', 'グリーヴ', 'Greaves')]

# id: (jp series prefix, en prefix, jp set name, en set name, jp bonus, en bonus, jp desc, en desc)
SETS = {
    'hunter': ('ハンター', 'Hunter', 'ハンターシリーズ', 'Hunter Series', '狩人の心得', "Hunter's Wisdom",
               '駆け出しハンターのための丈夫な革の防具。', 'Sturdy leather gear for fledgling hunters.'),
    'bone': ('ボーン', 'Bone', 'ボーンシリーズ', 'Bone Series', '荒骨の矜持', 'Bone Pride',
             '竜骨を荒々しく組み上げた防具。', 'Rugged armor lashed together from monster bones.'),
    'jagraptor': ('ジャグラ', 'Jagra', 'ジャグラシリーズ', 'Jagra Series', '狩竜の俊足', 'Raptor Stride',
                  'ジャグラプトルの青い皮を用いた軽快な防具。', 'Light armor made from blue Jagraptor hide.'),
    'ignivald': ('イグニス', 'Ignis', 'イグニスシリーズ【炎翼竜】', 'Ignis Series (Ignivald)', '炎翼竜の猛火', "Ignivald's Fury",
                 '炎翼竜の甲殻を鍛えた、猛き炎の防具。', 'Armor forged from Ignivald shell, burning with fury.'),
    'frostgarm': ('ガルム', 'Garm', 'ガルムシリーズ【氷牙獣】', 'Garm Series (Frostgarm)', '氷牙獣の毛皮', "Frostgarm's Pelt",
                  '氷牙獣の毛皮で仕立てた、極寒にも耐える防具。', 'Thick Frostgarm fur that shrugs off any blizzard.'),
    'voltrix': ('ヴォルト', 'Volt', 'ヴォルトシリーズ【雷鱗竜】', 'Volt Series (Voltrix)', '雷鱗竜の迅雷', "Voltrix's Swiftness",
                '雷鱗竜の蓄電殻がかすかに帯電している防具。', 'Voltrix electrodes keep this armor faintly charged.'),
    'gravadon': ('グラヴァ', 'Grava', 'グラヴァシリーズ【岩槌竜】', 'Grava Series (Gravadon)', '岩槌竜の剛殻', "Gravadon's Bulwark",
                 '岩槌竜の重殻を削り出した、鉄壁の防具。', 'Carved from Gravadon plates - an unbreakable wall.'),
    'azragos': ('アズラ', 'Azra', 'アズラシリーズ【冥炎龍】', 'Azra Series (Azragos)', '冥炎龍の威光', "Azragos' Dominion",
                '冥炎龍の龍鱗に禍々しい紋様が浮かぶ、伝説の防具。', 'Legendary armor of Azragos scales etched with baleful runes.'),
}

for sid, (jp, enp, jset, eset, jb, eb, jd, ed) in SETS.items():
    put(f'armorset.huntcraft.{sid}', jset, eset)
    put(f'armorset.huntcraft.{sid}.bonus', jb, eb)
    for pid, pj, pe in PIECES:
        put(f'item.huntcraft.{sid}_{pid}', jp + pj, f'{enp} {pe}')
        put(f'item.huntcraft.{sid}_{pid}.desc', jd, ed)

put('item.huntcraft.charm', '護石', 'Charm')
put('item.huntcraft.charm.tier1', '古びた護石', 'Worn Charm')
put('item.huntcraft.charm.tier2', '輝く護石', 'Shining Charm')
put('item.huntcraft.charm.tier3', '龍玉の護石', 'Dragon Charm')
put('item.huntcraft.charm.desc', '不思議な力を秘めた石。', 'A stone holding a mysterious power.')

put('tooltip.huntcraft.armor.level', 'Lv %s / %s', 'Lv %s / %s')
put('tooltip.huntcraft.armor.defense', '防御力 %s', 'Defense %s')
put('tooltip.huntcraft.armor.res', '耐性:', 'Resist:')
put('tooltip.huntcraft.armor.skills', 'スキル:', 'Skills:')
put('tooltip.huntcraft.armor.bonus', 'シリーズスキル【%s】', 'Set bonus [%s]')
put('tooltip.huntcraft.armor.pieces', '%s部位', '%s pcs')
put('tooltip.huntcraft.armor.next', '次の強化 Lv%s:', 'Next Lv%s:')
put('tooltip.huntcraft.armor.hr', 'HR%s必要', 'needs HR%s')
put('tooltip.huntcraft.charm.slots', 'スロット:', 'Slots:')
put('tooltip.huntcraft.charm.hint', 'オフハンドかホットバーで発動(1つまで)', 'Active in offhand or hotbar (only one)')
put('tooltip.huntcraft.charm.unknown', '鑑定前の護石。手に取ると効果が判明する。', 'Unidentified. Its power is revealed once carried.')
put('message.huntcraft.charm_found', '採掘中に %s を見つけた!', 'You dug up a %s!')

for lang, d in (('ja_jp', ja), ('en_us', en)):
    out = os.path.join(ROOT, 'src', 'main', 'lang', lang, 'armor.json')
    os.makedirs(os.path.dirname(out), exist_ok=True)
    with open(out, 'w', encoding='utf-8') as f:
        json.dump(dict(sorted(d.items())), f, ensure_ascii=False, indent=1)
        f.write('\n')
print('armor lang:', len(ja), 'keys')
