#!/usr/bin/env python3
"""Generates src/main/lang/{ja_jp,en_us}/core.json (core items, effects, messages, skills...)."""
import json, os

ROOT = os.path.join(os.path.dirname(__file__), '..', '..')
ja, en = {}, {}

def put(key, j, e):
    ja[key] = j; en[key] = e

# ---------------------------------------------------------------- large monster materials
MON = {
  'ignivald': ('炎翼竜', 'Ignivald', [('scale','鱗','Scale'),('shell','甲殻','Shell'),('webbing','翼膜','Webbing'),('claw','爪','Claw'),('ruby','紅玉','Ruby')],
               {'scale':('上鱗','Scale+'),'shell':('堅殻','Carapace'),'webbing':('剛翼膜','Wing+'),'claw':('鋭爪','Claw+'),'ruby':('焔玉','Flame Orb')}),
  'frostgarm': ('氷牙獣', 'Frostgarm', [('fur','毛','Fur'),('hide','皮','Hide'),('fang','牙','Fang'),('claw','爪','Claw'),('frostgem','氷晶玉','Frost Gem')],
               {'fur':('上毛','Fur+'),'hide':('堅皮','Hardhide'),'fang':('鋭牙','Fang+'),'claw':('鋭爪','Claw+'),'frostgem':('天氷玉','Heaven Frost Gem')}),
  'voltrix': ('雷鱗竜', 'Voltrix', [('scale','鱗','Scale'),('shell','甲殻','Shell'),('electrode','蓄電殻','Electrode'),('fang','牙','Fang'),('thunderorb','雷玉','Thunder Orb')],
               {'scale':('上鱗','Scale+'),'shell':('堅殻','Carapace'),'electrode':('上蓄電殻','Electrode+'),'fang':('鋭牙','Fang+'),'thunderorb':('天雷玉','Heaven Thunder Orb')}),
  'gravadon': ('岩槌竜', 'Gravadon', [('plate','重殻','Plate'),('shell','甲殻','Shell'),('skull','頭殻','Skull'),('tusk','牙','Tusk'),('earthgem','大地玉','Earth Gem')],
               {'plate':('上重殻','Plate+'),'shell':('堅殻','Carapace'),'skull':('上頭殻','Skull+'),'tusk':('剛牙','Tusk+'),'earthgem':('天地玉','Heaven Earth Gem')}),
  'azragos': ('冥炎龍', 'Azragos', [('scale','龍鱗','Dragonscale'),('shell','龍殻','Dragonshell'),('horn','角','Horn'),('claw','爪','Claw'),('orb','宝玉','Orb')],
               {'scale':('上龍鱗','Dragonscale+'),'shell':('堅龍殻','Dragon Carapace'),'horn':('尖角','Horn+'),'claw':('鋭爪','Claw+'),'orb':('天宝玉','Heaven Orb')}),
}
for mid, (jp, ename, slots, plus) in MON.items():
    for sid, sj, se in slots:
        put(f'item.huntcraft.{mid}_{sid}', f'{jp}の{sj}', f'{ename} {se}')
        put(f'item.huntcraft.{mid}_{sid}.desc', f'{jp}から剥ぎ取れる素材。', f'A material carved from {ename}.')
        pj, pe = plus[sid]
        put(f'item.huntcraft.{mid}_{sid}_plus', f'{jp}の{pj}', f'{ename} {pe}')
        put(f'item.huntcraft.{mid}_{sid}_plus.desc', f'上位の{jp}から得られる上質な素材。', f'A superior material from high rank {ename}.')
    put(f'item.huntcraft.{mid}_tail', f'{jp}の尻尾', f'{ename} Tail')
    put(f'item.huntcraft.{mid}_tail.desc', f'切断した尻尾から剥ぎ取れる。', 'Carved from a severed tail.')

# ---------------------------------------------------------------- other materials
MATS = [
 ('jagraptor_hide','ジャグラの皮','Jagraptor Hide'), ('jagraptor_fang','ジャグラの牙','Jagraptor Fang'),
 ('blizraptor_hide','ブリザラの皮','Blizraptor Hide'), ('blizraptor_crest','ブリザラの冠羽','Blizraptor Crest'),
 ('boarfang_hide','猪獣の皮','Boarfang Hide'), ('boarfang_tusk','猪獣の大牙','Boarfang Tusk'),
 ('grasica_antler','草鹿の角','Grasica Antler'), ('gamenos_shell','岩亀の甲羅','Gamenos Shell'),
 ('buzzling_stinger','毒蜂の針','Buzzling Stinger'), ('buzzling_wing','毒蜂の薄羽','Buzzling Wing'),
 ('yukimammo_fur','雪象の毛','Yukimammo Fur'), ('yukimammo_tusk','雪象の牙','Yukimammo Tusk'),
 ('heatgecko_scale','火蜥蜴の鱗','Heatgecko Scale'), ('ganseki_shell','岩蟹の殻','Gansekigani Shell'),
 ('ganseki_claw','岩蟹の鋏','Gansekigani Claw'), ('mofu_coin','ぬすっと小判','Thief Coin'),
 ('monster_fluid','モンスターの体液','Monster Fluid'), ('flame_sac','火炎袋','Flame Sac'), ('frost_sac','氷結袋','Frost Sac'),
 ('thunder_sac','電気袋','Thunder Sac'), ('aqua_sac','水袋','Aqua Sac'), ('poison_sac','毒袋','Poison Sac'),
 ('monster_bone_s','竜骨【小】','Monster Bone S'), ('monster_bone_m','竜骨【中】','Monster Bone M'), ('monster_bone_l','竜骨【大】','Monster Bone L'),
 ('raw_meat','生肉','Raw Meat'), ('herb','薬草','Herb'), ('blue_mushroom','アオキノコ','Blue Mushroom'), ('honey','ハチミツ','Honey'),
 ('antidote_herb','解毒草','Antidote Herb'), ('might_seed','怪力の種','Might Seed'), ('adamant_seed','忍耐の種','Adamant Seed'),
 ('sticky_grass','ネンチャク草','Sticky Grass'), ('spider_web','クモの巣','Spider Web'), ('flashbug','光蟲','Flashbug'),
 ('thunderbug','雷光虫','Thunderbug'), ('bitterbug','にが虫','Bitterbug'), ('fire_herb','火薬草','Fire Herb'),
 ('sleep_herb','ネムリ草','Sleep Herb'), ('numb_mushroom','マヒダケ','Numb Mushroom'), ('blast_nut','ハジケクルミ','Blast Nut'),
 ('ice_crystal','氷結晶','Ice Crystal'), ('hot_pepper','トウガラシ','Hot Pepper'), ('earth_crystal','大地の結晶','Earth Crystal'),
 ('azurite_ore','蒼鉱石','Azurite Ore'), ('crimsonite_ore','紅鋼石','Crimsonite Ore'), ('novacrystal','ノヴァクリスタル','Novacrystal'),
 ('lightcrystal','ライトクリスタル','Lightcrystal'), ('armor_sphere','鎧玉','Armor Sphere'), ('armor_sphere_plus','上鎧玉','Armor Sphere+'),
 ('hard_armor_sphere','堅鎧玉','Hard Armor Sphere'),
]
DESC = {
 'herb': ('調合に使う薬草。回復薬の材料。','Used to brew potions.'),
 'blue_mushroom': ('青いキノコ。回復薬の効果を高める。','Boosts potion brewing.'),
 'raw_meat': ('肉焼きセットでこんがり肉にできる。','Roast it on a BBQ spit.'),
 'armor_sphere': ('防具の強化に使う。','Used to upgrade armor.'),
 'armor_sphere_plus': ('防具を大きく強化できる。','Upgrades armor further.'),
 'hard_armor_sphere': ('上位防具の強化に使う。','Used for high rank armor upgrades.'),
}
for mid, j, e in MATS:
    put(f'item.huntcraft.{mid}', j, e)
    dj, de = DESC.get(mid, ('狩猟や採取で手に入る素材。', 'A material obtained by hunting or gathering.'))
    put(f'item.huntcraft.{mid}.desc', dj, de)

# ---------------------------------------------------------------- consumables
TOOLS = [
 ('potion','回復薬','Potion','体力を少し回復する。','Restores a little health.'),
 ('mega_potion','回復薬グレート','Mega Potion','体力を大きく回復する。','Restores a lot of health.'),
 ('max_potion','秘薬','Max Potion','体力とスタミナを全快し、一時的に体力上限を上げる。','Fully restores health and stamina.'),
 ('first_aid_med','応急薬','First-aid Med','支給品の回復薬。','Supply potion.'),
 ('antidote','解毒薬','Antidote','毒を治す。','Cures poison.'),
 ('energy_drink','元気ドリンコ','Energy Drink','睡眠・麻痺を治しスタミナを回復する。','Cures sleep and paralysis, restores stamina.'),
 ('hot_drink','ホットドリンク','Hot Drink','寒さを無効にする。雪山の必需品。','Negates cold. Essential in snowy maps.'),
 ('cool_drink','クーラードリンク','Cool Drink','暑さを無効にする。火山の必需品。','Negates heat. Essential in volcanic maps.'),
 ('demondrug','鬼人薬','Demondrug','攻撃力を高める。','Raises attack.'),
 ('armorskin','硬化薬','Armorskin','防御力を高める。','Raises defense.'),
 ('ration','携帯食料','Ration','スタミナ上限を少し回復する。','Restores some max stamina.'),
 ('well_done_steak','こんがり肉','Well-done Steak','スタミナ上限を大きく回復する。','Restores a lot of max stamina.'),
 ('rare_steak','生焼け肉','Rare Steak','焼きが足りない肉。','Undercooked.'),
 ('burnt_meat','コゲ肉','Burnt Meat','焦げてしまった肉。','Overcooked.'),
 ('bbq_spit','肉焼きセット','BBQ Spit','生肉を持って長押し。曲に合わせて離すとこんがり肉!','Hold with raw meat, release at the right moment!'),
 ('paintball','ペイントボール','Paintball','モンスターに当てると位置が分かるようになる。','Mark a monster to track it.'),
 ('flash_bomb','閃光玉','Flash Bomb','強烈な光でモンスターの目をくらませる。飛んでいるモンスターを落とす。','Blinds monsters and knocks flyers down.'),
 ('dung_bomb','こやし玉','Dung Bomb','モンスターを別のエリアへ追い払う。','Drives a monster to another area.'),
 ('tranq_bomb','捕獲用麻酔玉','Tranq Bomb','罠にかかった弱ったモンスターに2回当てると捕獲できる。','Two hits capture a trapped, weakened monster.'),
 ('pitfall_trap','落とし穴','Pitfall Trap','地面に設置。大型モンスターを足止めする。','Place on ground to trap large monsters.'),
 ('shock_trap','シビレ罠','Shock Trap','地面に設置。大型モンスターを麻痺させる。','Place on ground to paralyze large monsters.'),
 ('farcaster','モドリ玉','Farcaster','ベースキャンプに戻る。','Return to base camp.'),
 ('guild_pass','ギルドの証','Guild Pass','ハンターの村と元の世界を行き来できる。','Travel between the hunter village and the overworld.'),
 ('hunter_notes','ハンターノート','Hunter\'s Notes','モンスター図鑑・ギルドカード・スキル確認。','Monster field guide, guild card and skills.'),
 ('barrel_bomb','大タル爆弾','Large Barrel Bomb','攻撃すると大爆発する。','Explodes when struck.'),
 ('barrel_bomb_plus','大タル爆弾G','Large Barrel Bomb+','強力な大タル爆弾。','A stronger barrel bomb.'),
]
for tid, j, e, dj, de in TOOLS:
    key = f'item.huntcraft.{tid}' if tid not in ('barrel_bomb', 'barrel_bomb_plus') else f'block.huntcraft.{tid}'
    put(key, j, e)
    put(f'item.huntcraft.{tid}.desc', dj, de)
put('block.huntcraft.barrel_bomb.desc', '攻撃すると大爆発する。', 'Explodes when struck.')

# ---------------------------------------------------------------- monsters
MONSTERS = {
 'ignivald': ('炎翼竜イグニヴァルド', 'Ignivald, the Blaze Wyvern'),
 'frostgarm': ('氷牙獣フロストガルム', 'Frostgarm, the Rime Fang'),
 'voltrix': ('雷鱗竜ヴォルトリクス', 'Voltrix, the Storm Serpent'),
 'gravadon': ('岩槌竜グラヴァドン', 'Gravadon, the Rock Maul'),
 'azragos': ('冥炎龍アズラゴス', 'Azragos, the Abyssal Flame'),
 'jagraptor': ('ジャグラプトル', 'Jagraptor'), 'blizraptor': ('ブリザラプトル', 'Blizraptor'),
 'boarfang': ('ボアファング', 'Boarfang'), 'grasica': ('グラシカ', 'Grasica'), 'gamenos': ('ガメノス', 'Gamenos'),
 'buzzling': ('バズリング', 'Buzzling'), 'yukimammo': ('ユキマンモ', 'Yukimammo'), 'heatgecko': ('ヒートゲッコー', 'Heatgecko'),
 'gansekigani': ('ガンセキガニ', 'Gansekigani'), 'nussutto_mofu': ('ぬすっとモフ', 'Thief Mofu'),
}
for mid, (j, e) in MONSTERS.items():
    put(f'entity.huntcraft.{mid}', j, e)
put('entity.huntcraft.trap', '罠', 'Trap')
put('entity.huntcraft.thrown_item', '投擲アイテム', 'Thrown Item')
put('entity.huntcraft.monster_projectile', 'ブレス', 'Breath')
put('entity.huntcraft.hazard', '危険地帯', 'Hazard')

# ---------------------------------------------------------------- effects
EFF = [('fireblight','火属性やられ','Fireblight'),('iceblight','氷属性やられ','Iceblight'),('thunderblight','雷属性やられ','Thunderblight'),
       ('waterblight','水属性やられ','Waterblight'),('dragonblight','龍属性やられ','Dragonblight'),('deadly_poison','猛毒','Deadly Poison'),
       ('paralysis','麻痺','Paralysis'),('sleep','睡眠','Sleep'),('stun','気絶','Stun'),('roar','咆哮','Roar'),
       ('cool_drink','暑さ無効','Heat Immunity'),('hot_drink','寒さ無効','Cold Immunity'),('demondrug','鬼人薬','Demondrug'),('armorskin','硬化薬','Armorskin')]
for k, j, e in EFF:
    put(f'effect.huntcraft.{k}', j, e)

# ---------------------------------------------------------------- misc
ELEM = [('none','無','None'),('fire','火','Fire'),('water','水','Water'),('thunder','雷','Thunder'),('ice','氷','Ice'),('dragon','龍','Dragon'),
        ('poison','毒','Poison'),('paralysis','麻痺','Paralysis'),('sleep','睡眠','Sleep'),('blast','爆破','Blast')]
for k, j, e in ELEM: put(f'element.huntcraft.{k}', j, e)
WC = [('great_sword','大剣','Great Sword'),('long_sword','太刀','Long Sword'),('sword_and_shield','片手剣','Sword & Shield'),
      ('dual_blades','双剣','Dual Blades'),('hammer','ハンマー','Hammer'),('lance','ランス','Lance'),('hunting_horn','狩猟笛','Hunting Horn'),('bow','弓','Bow')]
for k, j, e in WC: put(f'weapon_class.huntcraft.{k}', j, e)
put('rank.huntcraft.low', '下位', 'Low Rank'); put('rank.huntcraft.high', '上位', 'High Rank')
SK = [('attack_boost','攻撃','Attack Boost'),('critical_eye','見切り','Critical Eye'),('weakness_exploit','弱点特効','Weakness Exploit'),
      ('critical_boost','超会心','Critical Boost'),('element_attack','属性攻撃強化','Element Attack'),('health_boost','体力増強','Health Boost'),
      ('defense_boost','防御','Defense Boost'),('constitution','体術','Constitution'),('stamina_surge','スタミナ急速回復','Stamina Surge'),
      ('evade_window','回避性能','Evade Window'),('guard','ガード性能','Guard'),('earplugs','耳栓','Earplugs'),('fire_res','火耐性','Fire Resistance'),
      ('water_res','水耐性','Water Resistance'),('thunder_res','雷耐性','Thunder Resistance'),('ice_res','氷耐性','Ice Resistance'),
      ('dragon_res','龍耐性','Dragon Resistance'),('handicraft','匠','Handicraft'),('razor_sharp','業物','Razor Sharp'),
      ('speed_sharpening','砥石高速化','Speed Sharpening'),('focus','集中','Focus'),('heroics','火事場力','Heroics'),('agitator','挑戦者','Agitator'),
      ('recovery_up','回復量UP','Recovery Up'),('gathering','採取+','Gathering+'),('carving','剥ぎ取り+','Carving+'),('stun_res','気絶耐性','Stun Resistance'),
      ('poison_res','毒耐性','Poison Resistance'),('tremor_res','耐震','Tremor Resistance'),('windproof','風圧耐性','Windproof'),('heat_cold_res','暑さ寒さ無効','Heat/Cold Immunity')]
for k, j, e in SK: put(f'skill.huntcraft.{k}', j, e)
TABS = [('weapons','HuntCraft 武器','HuntCraft Weapons'),('armor','HuntCraft 防具','HuntCraft Armor'),('materials','HuntCraft 素材','HuntCraft Materials'),
        ('items','HuntCraft アイテム','HuntCraft Items'),('monsters','HuntCraft モンスター','HuntCraft Monsters')]
for k, j, e in TABS: put(f'itemGroup.huntcraft.{k}', j, e)
put('key.categories.huntcraft', 'HuntCraft 狩猟', 'HuntCraft')
put('key.huntcraft.dodge', '回避(前転)', 'Dodge Roll')
put('tooltip.huntcraft.rarity', 'レア度 %s', 'Rarity %s')
MSG = [
 ('carve_done','これ以上剥ぎ取れない','Nothing more to carve'),
 ('carved','%s を剥ぎ取った (x%s)','Carved %s (x%s)'),
 ('painted','%s にペイントした!','Painted %s!'),
 ('repelled','モンスターが逃げていった!','The monster fled!'),
 ('captured','%s の捕獲に成功した!','Captured %s!'),
 ('trap_near','近くにすでに罠がある','Another trap is too close'),
 ('need_meat','生肉が必要だ','You need raw meat'),
 ('meat_rare','生焼け肉ができた…','Rare steak...'),
 ('meat_done','絶妙な焼き加減! こんがり肉ができた!','Perfect! Well-done steak!'),
 ('meat_burnt','コゲ肉になってしまった…','Burnt meat...'),
 ('pass_in_quest','クエスト中は使えない','Cannot be used during a quest'),
 ('farcaster_no_quest','クエスト中にしか使えない','Only usable during a quest'),
 ('need_hr','ハンターランク %s が必要','Requires Hunter Rank %s'),
 ('need_zenny','ゼニーが足りない','Not enough zenny'),
 ('need_materials','素材が足りない','Not enough materials'),
 ('need_base_item','強化元の装備を持っていない','You need the base equipment'),
 ('forged','%s を生産した!','Forged %s!'),
 ('upgraded','%s を強化した! (Lv%s)','Upgraded %s! (Lv%s)'),
 ('welcome','§6HuntCraft へようこそ! §fギルドの証を使うとハンターの村へ行けます。','§6Welcome to HuntCraft! §fUse the Guild Pass to visit the hunter village.'),
]
for k, j, e in MSG: put(f'message.huntcraft.{k}', j, e)

for lang, data in (('ja_jp', ja), ('en_us', en)):
    out = os.path.join(ROOT, 'src', 'main', 'lang', lang, 'core.json')
    os.makedirs(os.path.dirname(out), exist_ok=True)
    with open(out, 'w', encoding='utf-8') as f:
        json.dump(dict(sorted(data.items())), f, ensure_ascii=False, indent=1)
print(len(ja), 'keys')
