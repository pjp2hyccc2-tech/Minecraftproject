#!/usr/bin/env python3
"""Generates src/main/lang/{ja_jp,en_us}/weapons.json (weapon names, tooltips, messages, songs)."""
import json, os

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..')
ja, en = {}, {}


def put(key, j, e):
    ja[key] = j
    en[key] = e


CLASSES = ['great_sword', 'long_sword', 'sword_and_shield', 'dual_blades', 'hammer', 'lance', 'hunting_horn', 'bow']
CLASS_NOUN = {
    'great_sword': ('大剣', 'great sword'), 'long_sword': ('太刀', 'long sword'),
    'sword_and_shield': ('片手剣', 'sword and shield'), 'dual_blades': ('双剣', 'pair of dual blades'),
    'hammer': ('ハンマー', 'hammer'), 'lance': ('ランス', 'lance'), 'hunting_horn': ('狩猟笛', 'hunting horn'),
    'bow': ('弓', 'bow'),
}

# line -> class -> (japanese, english)
NAMES = {
    'iron': {
        'great_sword': ('鉄塊の大剣', 'Iron Slab Greatsword'), 'long_sword': ('鉄刀', 'Iron Katana'),
        'sword_and_shield': ('鉄の片手剣', 'Iron Hunting Sword'), 'dual_blades': ('鉄の双剣', 'Iron Twin Daggers'),
        'hammer': ('鉄塊の槌', 'Iron Mallet'), 'lance': ('鉄の騎槍', 'Iron Lance'),
        'hunting_horn': ('鉄の狩猟笛', 'Iron Horn'), 'bow': ('鉄の弓', 'Iron Bow'),
    },
    'bone': {
        'great_sword': ('骨断ち大剣', 'Bone Cleaver'), 'long_sword': ('骨太刀', 'Bone Tachi'),
        'sword_and_shield': ('骨鉈', 'Bone Machete'), 'dual_blades': ('骨双牙', 'Bone Twinfangs'),
        'hammer': ('骨砕きの槌', 'Bonecrusher'), 'lance': ('骨槍', 'Bone Pike'),
        'hunting_horn': ('骨の角笛', 'Bone Horn'), 'bow': ('骨弓', 'Bone Bow'),
    },
    'ignivald': {
        'great_sword': ('炎剣イグニス', 'Ignis Flamebrand'), 'long_sword': ('焔太刀・緋閃', 'Hisen, Crimson Flash'),
        'sword_and_shield': ('炎牙剣フレイムファング', 'Flamefang'), 'dual_blades': ('双炎刃イグニツイン', 'Ignis Twinflames'),
        'hammer': ('爆炎鎚イグニート', 'Ignite Maul'), 'lance': ('紅蓮槍イグニヴァル', 'Crimson Lance Ignival'),
        'hunting_horn': ('炎笛フレアホルン', 'Flare Horn'), 'bow': ('炎弓イグニスアロー', 'Ignis Flamebow'),
    },
    'frostgarm': {
        'great_sword': ('氷牙大剣ガルムエッジ', 'Garm Frostedge'), 'long_sword': ('凍刀・霜月', 'Shimotsuki, Frost Moon'),
        'sword_and_shield': ('氷爪剣フロストクロー', 'Frostclaw'), 'dual_blades': ('双氷牙リムファング', 'Rimefangs'),
        'hammer': ('凍砕鎚アイスブレイカー', 'Icebreaker'), 'lance': ('氷槍グレイシア', 'Glacia Lance'),
        'hunting_horn': ('氷笛ブリザードホルン', 'Blizzard Horn'), 'bow': ('霜弓ガルムボウ', 'Garm Rimebow'),
    },
    'voltrix': {
        'great_sword': ('雷剣ヴォルトブレイド', 'Voltblade'), 'long_sword': ('迅雷刀・紫電', 'Shiden, Violet Lightning'),
        'sword_and_shield': ('雷鱗剣スパークエッジ', 'Sparkedge'), 'dual_blades': ('双雷刃ツインボルト', 'Twin Bolts'),
        'hammer': ('轟雷鎚サンダークラップ', 'Thunderclap Maul'), 'lance': ('雷槍ヴォルトパイク', 'Voltpike'),
        'hunting_horn': ('雷笛サンダーホルン', 'Thunder Horn'), 'bow': ('雷弓ヴォルトアーク', 'Volt Arc'),
    },
    'gravadon': {
        'great_sword': ('岩激剣グラヴァ', 'Grava Torrentblade'), 'long_sword': ('激流刀・瀑布', 'Bakufu, Waterfall'),
        'sword_and_shield': ('岩水剣ロックタイド', 'Rocktide'), 'dual_blades': ('双岩牙アクアタスク', 'Aqua Tusks'),
        'hammer': ('大岩槌ガイアクラッシュ', 'Gaia Crusher'), 'lance': ('激流槍タイダルランス', 'Tidal Lance'),
        'hunting_horn': ('潮岩笛タイドホルン', 'Tide Horn'), 'bow': ('激流弓グラヴァボウ', 'Grava Torrentbow'),
    },
    'azragos': {
        'great_sword': ('冥龍剣アズラゴス', 'Azragos Doomblade'), 'long_sword': ('冥刀・夜叉', 'Yasha, Netherblade'),
        'sword_and_shield': ('冥龍剣ネクロファング', 'Necrofang'), 'dual_blades': ('双冥刃アズラツイン', 'Azra Twinblades'),
        'hammer': ('冥龍鎚ドゥームハンマー', 'Doom Maul'), 'lance': ('冥龍槍アビスランス', 'Abyss Lance'),
        'hunting_horn': ('冥笛レクイエム', 'Requiem Horn'), 'bow': ('冥龍弓アズラボウ', 'Azra Abyssbow'),
    },
}

LINE_FLAVOR = {
    'iron': ('鍛えた鉄で作られた', '頑丈で扱いやすい、駆け出しハンターの相棒。', 'forged from tempered iron', 'Sturdy and forgiving: a rookie hunter\'s partner.'),
    'bone': ('竜骨を削り出して作られた', '荒々しくも確かな手応えがある。', 'carved from monster bone', 'Rough, but it hits with real weight.'),
    'ignivald': ('炎翼竜の素材で作られた', '振るうたびに刃が灼熱を帯びる。', 'made from Ignivald materials', 'Its edge burns hotter with every swing.'),
    'frostgarm': ('氷牙獣の素材で作られた', '触れたものを芯まで凍てつかせる。', 'made from Frostgarm materials', 'Freezes whatever it touches to the core.'),
    'voltrix': ('雷鱗竜の素材で作られた', '紫電が絶えずほとばしる。', 'made from Voltrix materials', 'Violet lightning crackles along it constantly.'),
    'gravadon': ('岩槌竜の素材で作られた', '岩をも砕く激流の力を秘める。', 'made from Gravadon materials', 'Holds the force of a torrent that splits stone.'),
    'azragos': ('冥炎龍の素材で作られた', '龍の怨念が宿るといわれる。', 'made from Azragos materials', 'They say a dragon\'s grudge dwells within.'),
}

for line, by_class in NAMES.items():
    pj, sj, pe, se = LINE_FLAVOR[line]
    for c in CLASSES:
        j, e = by_class[c]
        nj, ne = CLASS_NOUN[c]
        key = f'item.huntcraft.{line}_{c}'
        put(key, j, e)
        art = 'An' if ne[0] in 'aeiou' else 'A'
        put(key + '.desc', f'{pj}{nj}。{sj}', f'{art} {ne} {pe}. {se}')

# ---------------------------------------------------------------- other items / entity / key
put('item.huntcraft.whetstone', '砥石', 'Whetstone')
put('item.huntcraft.whetstone.desc', '何度でも使える砥石。約2秒で武器の斬れ味を回復する。', 'A reusable whetstone. Restores weapon sharpness in about 2 seconds.')
put('item.huntcraft.coating_power', '強撃ビン', 'Power Coating')
put('item.huntcraft.coating_power.desc', '矢の威力を高めるビン。1射ごとに1つ消費する。', 'Increases arrow power. One is used per shot.')
put('item.huntcraft.coating_poison', '毒ビン', 'Poison Coating')
put('item.huntcraft.coating_poison.desc', '矢に毒を塗るビン。1射ごとに1つ消費する。', 'Coats arrows with poison. One is used per shot.')
put('item.huntcraft.coating_para', '麻痺ビン', 'Para Coating')
put('item.huntcraft.coating_para.desc', '矢に麻痺毒を塗るビン。1射ごとに1つ消費する。', 'Coats arrows with paralytic venom. One is used per shot.')
put('entity.huntcraft.hunter_arrow', '矢', 'Arrow')
put('key.huntcraft.sharpen', '武器を研ぐ', 'Sharpen Weapon')

# ---------------------------------------------------------------- tooltips
T = 'tooltip.huntcraft.weapon.'
put(T + 'level', 'Lv %s/%s', 'Lv %s/%s')
put(T + 'attack', '攻撃力 %s', 'Attack %s')
put(T + 'element', '属性 ', 'Element ')
put(T + 'status', '状態異常 ', 'Status ')
put(T + 'affinity', '会心率 %s%%', 'Affinity %s%%')
put(T + 'sharpness', '斬れ味 ', 'Sharpness ')
put(T + 'guard', 'ガード性能 %s%%', 'Guard %s%%')
put(T + 'charge_mv', '溜め斬り威力 %s / %s / %s', 'Charged slam power %s / %s / %s')
put(T + 'spirit', '練気ゲージ: 攻撃で溜まり、気刃大回転斬りで練気レベル上昇', 'Spirit gauge: fills with attacks; the Spirit Roundslash raises the spirit level')
put(T + 'bash', 'シールドバッシュ: 打撃で気絶値を蓄積', 'Shield bash: blunt damage that builds KO')
put(T + 'quick_item', '納刀せずにオフハンドの道具を素早く使える (スニーク+右)', 'Uses the off-hand item quickly without sheathing (Sneak+RMB)')
put(T + 'demon', '鬼人化: 攻撃速度・威力上昇、スタミナを消費', 'Demon Mode: faster and stronger, drains stamina')
put(T + 'ko', '打撃: 頭を叩くと気絶値が大きく溜まる', 'Blunt: hits to the head build KO')
put(T + 'counter', 'ガード中に左クリックでカウンター突き', 'Left click while guarding: counter thrust')
put(T + 'songs', '旋律:', 'Songs:')
put(T + 'shot', '射撃タイプ: %s', 'Shot type: %s')
put(T + 'coating', 'ビン: %s', 'Coating: %s')
put(T + 'coat_none', 'なし', 'None')
put(T + 'whetstone_key', '武器を持って「武器を研ぐ」キーでも研げる', 'Or press the Sharpen Weapon key while holding a weapon')
HELP = {
    'great_sword': ('右長押し: 溜め斬り(3段階) / スニーク+右: ガード', 'Hold RMB: charged slam (3 levels) / Sneak+RMB: guard'),
    'long_sword': ('右: 気刃斬り / スニーク+右: 見切り斬り', 'RMB: Spirit Blade combo / Sneak+RMB: Foresight Slash'),
    'sword_and_shield': ('右長押し: ガード / スニーク+右: シールドバッシュ (オフハンドに道具があれば使用)', 'Hold RMB: guard / Sneak+RMB: shield bash (or use the off-hand item)'),
    'dual_blades': ('右: 鬼人化 / 鬼人化中にスニーク+右: 乱舞', 'RMB: Demon Mode / Sneak+RMB in Demon Mode: Blade Dance'),
    'hammer': ('右長押し: 溜め (Lv1 かち上げ / Lv2 横振り / Lv3 回転ビッグバン)', 'Hold RMB: charge (Lv1 upswing / Lv2 side smash / Lv3 spinning big bang)'),
    'lance': ('右長押し: ガード(左でカウンター突き) / スニーク+右: 突進', 'Hold RMB: guard (LMB: counter) / Sneak+RMB: charge dash'),
    'hunting_horn': ('左: 音符1 / 右: 音符2 / スニーク+左: 音符3 / スニーク+右: 演奏', 'LMB: note 1 / RMB: note 2 / Sneak+LMB: note 3 / Sneak+RMB: perform'),
    'bow': ('右長押し: 溜め撃ち / スニーク+右: ビン切り替え', 'Hold RMB: draw and shoot / Sneak+RMB: switch coating'),
}
for c, (j, e) in HELP.items():
    put(T + 'help.' + c, j, e)

put('shot.huntcraft.rapid', '連射', 'Rapid')
put('shot.huntcraft.spread', '拡散', 'Spread')
put('shot.huntcraft.pierce', '貫通', 'Pierce')

SONGS = {
    'self_improvement': ('自分強化', 'Self-improvement'),
    'attack_up': ('攻撃力UP', 'Attack Up'),
    'defense_up': ('防御力UP', 'Defense Up'),
    'health_s': ('体力回復【小】', 'Health Recovery (S)'),
    'health_l': ('体力回復【大】', 'Health Recovery (L)'),
    'stamina': ('スタミナ減少無効', 'Stamina Use Negated'),
    'earplugs': ('聴覚保護', 'Earplugs'),
}
for k, (j, e) in SONGS.items():
    put('song.huntcraft.' + k, j, e)

H = 'hud.huntcraft.weapon.'
put(H + 'overcharge', '溜めすぎ', 'Overcharged')
put(H + 'guard', 'ガード', 'Guard')
put(H + 'demon', '鬼人化', 'Demon')
put(H + 'archdemon', '鬼人強化', 'Archdemon')
put(H + 'sharpening', '研いでいます…', 'Sharpening...')

M = 'message.huntcraft.weapon.'
put(M + 'spirit_level.1', '練気レベル: 白', 'Spirit level: White')
put(M + 'spirit_level.2', '練気レベル: 黄', 'Spirit level: Yellow')
put(M + 'spirit_level.3', '練気レベル: 赤', 'Spirit level: Red')
put(M + 'foresight_success', '見切り成功! 練気ゲージ全快', 'Foresight! Spirit gauge refilled')
put(M + 'no_stamina', 'スタミナが足りない', 'Not enough stamina')
put(M + 'demon_on', '鬼人化!', 'Demon Mode!')
put(M + 'archdemon', '鬼人強化状態', 'Archdemon Mode')
put(M + 'no_song', '旋律が揃っていない', 'No song in the note queue')
put(M + 'song', '♪ %s', '♪ %s')
put(M + 'coating', 'ビン: %s', 'Coating: %s')
put(M + 'coating_empty', 'ビンが尽きた', 'Out of coatings')
put(M + 'no_whetstone', '砥石を持っていない', 'You have no whetstone')
put(M + 'nothing_to_sharpen', '研ぐ武器がない', 'No weapon to sharpen')
put(M + 'sharpened', '%s を研いだ', 'Sharpened %s')

SUB = {
    'weapon.swing': ('武器を振る', 'Weapon swings'), 'weapon.slam': ('叩きつけ', 'Weapon slams'),
    'weapon.guard': ('ガード', 'Guard'), 'weapon.spirit_up': ('練気上昇', 'Spirit rises'),
    'weapon.spirit_slash': ('気刃斬り', 'Spirit slash'), 'weapon.foresight': ('見切り', 'Foresight'),
    'weapon.demon_on': ('鬼人化', 'Demon Mode'), 'weapon.demon_off': ('鬼人化解除', 'Demon Mode ends'),
    'weapon.blade_dance': ('乱舞', 'Blade Dance'), 'weapon.bash': ('盾攻撃', 'Shield bash'),
    'weapon.counter': ('カウンター突き', 'Counter thrust'), 'weapon.dash': ('突進', 'Lance charge'),
    'weapon.horn': ('笛の音', 'Horn note'), 'weapon.horn_song': ('旋律演奏', 'Song performed'),
    'weapon.bow_shot': ('矢を放つ', 'Arrow shot'), 'weapon.bow_draw': ('弓を引く', 'Bow drawn'),
    'weapon.coat_switch': ('ビン切り替え', 'Coating switched'), 'weapon.sharpen_done': ('研ぎ完了', 'Weapon sharpened'),
}
for k, (j, e) in SUB.items():
    put('subtitles.huntcraft.' + k, j, e)

for lang, d in (('ja_jp', ja), ('en_us', en)):
    out = os.path.join(ROOT, 'src', 'main', 'lang', lang, 'weapons.json')
    with open(out, 'w', encoding='utf-8') as f:
        json.dump(d, f, ensure_ascii=False, indent=1, sort_keys=True)
    print('wrote', out, len(d))
