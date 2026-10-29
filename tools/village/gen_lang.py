#!/usr/bin/env python3
"""Writes the village module's lang fragments (ja_jp first, en_us) and checks them.

Run from the repository root:  python3 tools/village/gen_lang.py
Outputs: src/main/lang/ja_jp/village.json, src/main/lang/en_us/village.json
Checks: same keys in both languages, same number of %s placeholders, every literal translation key used in the
village Java sources exists (dynamic prefixes are generated from the tables below).
"""
import json
import os
import re
import sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..'))
JA, EN = {}, {}


def t(key, ja, en):
    if key in JA:
        raise SystemExit('duplicate key ' + key)
    JA[key] = ja
    EN[key] = en


# ------------------------------------------------------------------ blocks / entities / items
t('block.huntcraft.item_box', 'アイテムボックス', 'Item Box')
t('block.huntcraft.village_quest_board', 'クエストボード', 'Quest Board')
t('block.huntcraft.paper_lantern', '赤提灯', 'Red Paper Lantern')
t('block.huntcraft.paper_lantern_white', '白提灯', 'White Paper Lantern')
t('container.huntcraft.item_box', 'アイテムボックス', 'Item Box')
t('entity.huntcraft.village_npc', '村人', 'Villager')
t('entity.huntcraft.mofu_npc', 'モフ', 'Mofu')
t('entity.huntcraft.mofu', 'お供モフ', 'Mofu Companion')
t('entity.huntcraft.mofu_bomb', 'モフ爆弾', 'Mofu Bomb')
t('entity.huntcraft.training_dummy', '訓練用カカシ', 'Training Dummy')
t('entity.huntcraft.training_dummy.stats', '§e%s§f  合計 %s  DPS %s (%s回)', '§e%s§f  Total %s  DPS %s (%s hits)')

TIERS = [('bamboo', '竹', 'Bamboo'), ('bone', '骨', 'Bone'), ('azurite', '蒼鉱', 'Azurite'), ('ignivald', '炎翼', 'Ignivald'),
         ('frostgarm', '氷牙', 'Frostgarm'), ('voltrix', '雷鱗', 'Voltrix'), ('gravadon', '岩槌', 'Gravadon'), ('azragos', '冥炎', 'Azragos')]
SLOTS = [('weapon', '武器', 'Weapon', 'お供武器', 'Companion Weapon'), ('helm', '兜', 'Helm', 'お供兜', 'Companion Helm'),
         ('armor', '鎧', 'Armor', 'お供鎧', 'Companion Mail')]
for sid, sja, sen, ija, ien in SLOTS:
    t('mofu.huntcraft.slot.' + sid, sja, sen)
    t('smith_group.huntcraft.mofu_' + sid, ija, ien + 's' if not ien.endswith('Mail') else ien)
    for tid, tja, ten in TIERS:
        t('item.huntcraft.mofu_%s_%s' % (sid, tid), '%sの%s' % (tja, ija), '%s %s' % (ten, ien))
for tid, tja, ten in TIERS:
    t('mofu.huntcraft.tier.' + tid, tja, ten)
t('tooltip.huntcraft.mofu_gear', 'お供モフ用の%s', 'Mofu companion %s')
t('tooltip.huntcraft.mofu_gear.attack', '攻撃力 +%s', 'Attack +%s')
t('tooltip.huntcraft.mofu_gear.element', '属性 %s %s', 'Element %s %s')
t('tooltip.huntcraft.mofu_gear.defense', '防御力 +%s', 'Defense +%s')
t('tooltip.huntcraft.mofu_gear.health', '体力 +%s', 'Health +%s')
t('tooltip.huntcraft.mofu_gear.equip', 'モフの寄合所で装備させよう', 'Equip it at the Mofu Lodge')

# ------------------------------------------------------------------ sounds
SUBS = [('mofu_meow', 'モフが鳴く', 'Mofu meows'), ('mofu_horn', '癒しの笛が響く', 'Healing horn plays'),
        ('mofu_bomb', 'モフ爆弾が炸裂', 'Mofu bomb explodes'), ('mofu_attack', 'モフが斬りかかる', 'Mofu slashes'),
        ('mofu_faint', 'モフが目を回す', 'Mofu faints'), ('meal', '料理が運ばれる', 'Meal served'),
        ('npc_greet', '村人のあいさつ', 'Villager greets'), ('anvil_tap', '槌を打つ音', 'Hammer strikes'),
        ('box_open', '箱が開く', 'Box opens'), ('ui_page', 'ページをめくる', 'Page turns'),
        ('dummy_hit', 'カカシを叩く', 'Dummy struck'), ('welcome', '風見の村の調べ', 'Kazami village melody')]
for sid, ja, en in SUBS:
    t('subtitles.huntcraft.village.' + sid, ja, en)

# ------------------------------------------------------------------ village / travel
t('village.huntcraft.title', '風見の村', 'Kazami Village')
t('village.huntcraft.subtitle', '～ハンターたちの集う谷～', '~ the valley where hunters gather ~')
t('village.huntcraft.intro.1', '【村長ソウゲン】よう来たのう、新しいハンター殿。ここは風見の村じゃ。',
  '[Chief Sougen] Welcome, new hunter. This is Kazami Village.')
t('village.huntcraft.intro.2', '北の高台に集会所、西に鍛冶屋とよろず屋、東にモフ亭とモフの寄合所、南に訓練場がある。',
  'The gathering hall stands on the northern terrace; the smithy and the store are to the west, the Mofu Diner and the Mofu Lodge to the east, the training ground to the south.')
t('village.huntcraft.intro.3', 'あんたの家は南西じゃ。アイテムボックスは家と集会所の入口にあるぞ。',
  'Your house is in the south-west. You will find item boxes there and at the gathering hall entrance.')
t('village.huntcraft.intro.4', 'まずは東の寄合所でモフ長老に会い、相棒のお供モフを選ぶとよい。わしの所にも顔を出しておくれ。',
  'First, visit the Mofu Elder at the lodge in the east and choose a companion. And do come and see me now and then.')
t('village.huntcraft.starter', '風見の村があなたを待っています: %s', 'Kazami Village awaits you: %s')
t('village.huntcraft.starter_link', '[風見の村へ向かう]', '[Travel to Kazami Village]')
t('village.huntcraft.starter_hover', 'クリックで風見の村へ (ギルドの証でも行き来できます)', 'Click to travel (the Guild Pass works too)')

# ------------------------------------------------------------------ messages
M = [
    ('arrive_village', '風見の村に到着した', 'Arrived at Kazami Village'),
    ('village_missing', 'ハンターの村が見つかりません (データパックを確認してください)', 'The hunter village dimension is missing (check the data pack)'),
    ('village_edge', 'これより先は村の外だ。広場に戻った', 'Beyond here lies the wilderness. You return to the plaza'),
    ('bought', '%s を %s 個購入した (%s z)', 'Bought %s x%s (%s z)'),
    ('sold', '%s を %s 個売却した (+%s z)', 'Sold %s x%s (+%s z)'),
    ('meal_eaten', '%s を平らげた! ごちそうさま!', 'You finished the %s! Delicious!'),
    ('meal_skill', '食事スキル発動: %s', 'Meal skill: %s'),
    ('meal_expired', '食事の効果が切れた', 'Your meal wore off'),
    ('meal_guts', 'ど根性で踏みとどまった!', 'Guts! You hung on with 1 HP!'),
    ('mofu_first', '%s (%s) が相棒になった! よろしくニャ!', '%s (%s) is now your partner! Nice to meet you, nya!'),
    ('mofu_active', '%s がお供になった', '%s will accompany you'),
    ('mofu_back', '%s が戻ってきた!', '%s is back!'),
    ('mofu_fainted', '%s が目を回した… しばらく休憩するニャ', '%s fainted... resting for a while'),
    ('mofu_level', '%s のレベルが %s に上がった!', '%s reached level %s!'),
    ('mofu_skill', '%s が新しいスキル「%s」を覚えた!', '%s learned a new skill: %s!'),
    ('mofu_gathered', '%s が %s を拾ってきた!', '%s picked up %s!'),
    ('mofu_returned', '%s が %s の探索から戻った! (素材 %s 個をボックスへ)', '%s returned from %s! (%s materials sent to your item box)'),
    ('mofu_expedition', '%s は %s へ探索に出かけた', '%s set out to explore %s'),
    ('mofu_recalled', '%s を呼び戻した', 'You recalled %s'),
    ('mofu_need_level', 'お供のレベル %s 以上が必要', 'Requires companion level %s'),
    ('mofu_full', 'お供は %s 匹までしか雇えない', 'You can only have %s companions'),
    ('mofu_hired', '%s を雇った!', 'You hired %s!'),
    ('mofu_dismissed', '%s とお別れした… 装備はボックスに返された', 'You parted with %s... their gear went to your item box'),
    ('mofu_equipped', '%s に %s を装備させた', '%s equipped %s'),
    ('mofu_trained', '%s は修行に励んだ! (経験 +%s)', '%s trained hard! (+%s XP)'),
    ('mofu_retrained', '%s は %s に転向した!', '%s became a %s!'),
    ('reward_claimed', '村長から「%s」の褒美を受け取った! (%s z とアイテムはボックスへ)', 'Received the chief\'s reward "%s"! (%s z, items in your item box)'),
]
for k, ja, en in M:
    t('message.huntcraft.' + k, ja, en)

# ------------------------------------------------------------------ NPCs
NPCS = {
    'receptionist': ('受付嬢 ツバキ', 'Tsubaki, Guild Receptionist', 'ギルド受付', 'Guild Desk',
                     ['いらっしゃいませ! 本日はどのクエストに?', 'ハンターさん、今日も風が気持ちいいですね!', 'クエストの準備はお済みですか?'],
                     ['Welcome! Which quest will it be today?', 'Lovely breeze today, isn\'t it, hunter?', 'All set for your quest?'],
                     'ご武運をお祈りしています!', 'I will pray for your success!'),
    'blacksmith': ('鍛冶屋 ゲンテツ', 'Gentetsu, Master Smith', '鍛冶屋', 'Smithy',
                   ['おう、素材を持ってきたか!', '良い武具は良い狩りから生まれるもんだ', '槌の音が聞こえたら、わしの出番よ'],
                   ['Hah! Brought me some materials?', 'Good gear is born from good hunts.', 'When you hear the hammer, that\'s my cue.'],
                   '素材さえ揃えば、なんでも打ってやるぞ!', 'Bring the materials and I\'ll forge anything!'),
    'merchant': ('よろず屋 コハル', 'Koharu, General Store', 'よろず屋', 'General Store',
                 ['いらっしゃい! 今日の特売も見ていってね', '狩りの前の買い出しはお済み?', 'ランクが上がると、いい品も入荷するよ!'],
                 ['Welcome! Don\'t miss today\'s deals!', 'Stocked up before the hunt?', 'Better goods arrive as your rank rises!'],
                 '回復薬は多めに持って行くのが鉄則よ!', 'Rule number one: carry plenty of potions!'),
    'chef': ('料理長 ニボシ', 'Chef Niboshi', 'モフ亭', 'Mofu Diner',
             ['腹が減っては狩りはできぬニャ!', '今日のおすすめは絶品ニャ!', '食材の組み合わせで味も効果も変わるのニャ'],
             ['Can\'t hunt on an empty stomach, nya!', 'Today\'s special is superb, nya!', 'Different pairings, different flavours and effects, nya!'],
             '食材を二つ選ぶのニャ。組み合わせで料理が決まるニャ!', 'Pick two ingredients, nya. The pairing decides the dish!'),
    'elder': ('村長 ソウゲン', 'Village Chief Sougen', '風見の村 村長', 'Village Chief',
              ['風が騒いでおる… 気をつけるんじゃぞ', 'よう戻った。茶でも飲んでいくか?', 'わしの話を聞いていくかの?'],
              ['The wind is restless... take care.', 'Welcome back. Care for some tea?', 'Will you listen to an old man\'s tale?'],
              '若いの、焦らずひとつずつじゃ', 'One step at a time, young one.'),
    'mofu_elder': ('モフ長老 ワタゲ', 'Mofu Elder Watage', 'モフの寄合所', 'Mofu Lodge',
                   ['よく来たのう、ハンター殿ニャ', 'お供は家族も同然じゃニャ', '若いモフたちが活躍を待っておるニャ'],
                   ['Welcome, hunter, nya.', 'A companion is family, nya.', 'The young mofu are eager to prove themselves, nya.'],
                   'お供を大切にするんじゃニャ', 'Treasure your companions, nya.'),
}
for rid, (name_ja, name_en, title_ja, title_en, greet_ja, greet_en, line_ja, line_en) in NPCS.items():
    t('npc.huntcraft.' + rid, name_ja, name_en)
    t('npc.huntcraft.%s.title' % rid, title_ja, title_en)
    for i in range(3):
        t('npc.huntcraft.%s.greet%d' % (rid, i), '「%s」' % greet_ja[i], '"%s"' % greet_en[i])
    t('npc.huntcraft.%s.line' % rid, '「%s」' % line_ja, '"%s"' % line_en)

# ------------------------------------------------------------------ mofu companions
TYPES = [('fighter', '闘士', 'Fighter', '爪刀で素早く斬りかかる接近戦の名手。', 'A swift melee specialist with claw-blades.'),
         ('healer', '癒し手', 'Healer', '体力が減ると癒しの笛で回復してくれる。', 'Plays a healing horn when hunters are hurt.'),
         ('bomber', '爆弾屋', 'Bomber', '小タル爆弾を遠くから投げつける。', 'Lobs small barrel bombs from range.')]
for tid, ja, en, dja, den in TYPES:
    t('mofu.huntcraft.type.' + tid, ja, en)
    t('mofu.huntcraft.type.%s.desc' % tid, dja, den)
NAMES = [('タマ', 'Tama'), ('ミケ', 'Mike'), ('コテツ', 'Kotetsu'), ('モモ', 'Momo'), ('ハチ', 'Hachi'), ('ソラ', 'Sora'),
         ('キナコ', 'Kinako'), ('ダイフク', 'Daifuku'), ('ムギ', 'Mugi'), ('ポン', 'Pon'), ('チャチャ', 'Chacha'), ('ゴマ', 'Goma'),
         ('マロン', 'Marron'), ('コハク', 'Kohaku'), ('フク', 'Fuku'), ('ヨモギ', 'Yomogi'), ('ユズ', 'Yuzu'), ('シロ', 'Shiro'),
         ('クロ', 'Kuro'), ('トラ', 'Tora'), ('ナギ', 'Nagi'), ('ハク', 'Haku'), ('ミルク', 'Milk'), ('モチ', 'Mochi'),
         ('アズキ', 'Azuki'), ('コロ', 'Koro'), ('テン', 'Ten'), ('ヒナタ', 'Hinata'), ('サクラ', 'Sakura'), ('イブキ', 'Ibuki'),
         ('カボス', 'Kabosu'), ('ノリ', 'Nori')]
for i, (ja, en) in enumerate(NAMES):
    t('mofu.huntcraft.name.%d' % i, ja, en)
SKILLS = [
    ('combo', '二段斬り', 'Double Strike', '攻撃のあと、ときどき追撃する。', 'Sometimes follows an attack with a second strike.'),
    ('rampage', '逆境の牙', 'Desperate Fang', 'ハンターの体力が半分以下で攻撃力+20%。', '+20% attack while the hunter is below half health.'),
    ('blade_dance', '爪乱舞', 'Claw Dance', '一定時間ごとに怒涛の連続斬りを放つ。', 'Periodically unleashes a flurry of slashes.'),
    ('soothing', '癒しの音色', 'Soothing Tune', '笛の回復量が増え、毒や属性やられも治す。', 'Stronger healing; the horn also cures poison and blights.'),
    ('encore', '応援歌', 'Battle Hymn', '笛を聞いたハンターの攻撃力が30秒間+10%。', 'Hunters hearing the horn deal +10% damage for 30 s.'),
    ('revival', '起死回生の笛', 'Revival Horn', 'ハンターが瀕死のとき体力を全回復する (5分に1回)。', 'Fully heals a hunter in danger (once every 5 min).'),
    ('big_barrel', '大タル爆弾', 'Big Barrel', '爆弾の爆発範囲が広がる。', 'Wider bomb explosions.'),
    ('flash_barrel', '閃光タル', 'Flash Barrel', 'ときどき閃光タルでモンスターの目をくらませる。', 'Now and then blinds monsters with a flash barrel.'),
    ('carpet_bomb', '爆弾雨', 'Bomb Shower', 'ときどき爆弾を三つ同時に投げる。', 'Sometimes throws three bombs at once.'),
    ('guts', '不屈', 'Unyielding', '出撃ごとに一度だけ、力尽きる攻撃に耐える。', 'Once per sortie, survives a fainting blow.'),
    ('gatherer', '素材拾い', 'Scavenger', '大型モンスター狩猟後、素材をひとつ拾ってくる。', 'Picks up an extra material after each large monster hunt.'),
    ('veteran', '歴戦', 'Veteran', '全能力+10%。', 'All stats +10%.'),
]
for sid, ja, en, dja, den in SKILLS:
    t('mofu.huntcraft.skill.' + sid, ja, en)
    t('mofu.huntcraft.skill.%s.desc' % sid, dja, den)
DESTS = [('verdant_hills', '翠緑の丘', 'Verdant Hills', '薬草や虫、骨が見つかる穏やかな丘陵。', 'Gentle hills with herbs, bugs and bones.'),
         ('frostpeak_range', '霜嶺山脈', 'Frostpeak Range', '氷結晶や鉱石が眠る雪の山々。', 'Snowy peaks hiding ice crystals and ore.'),
         ('ember_caldera', '熾火の大釜', 'Ember Caldera', '火薬草や希少な鉱石が採れる灼熱の火口。', 'A scorching crater with fire herbs and rare ore.')]
for did, ja, en, dja, den in DESTS:
    t('mofu.huntcraft.dest.' + did, ja, en)
    t('mofu.huntcraft.dest.%s.desc' % did, dja, den)
t('mofu.huntcraft.length.short', '近場 (15分)', 'Short (15 min)')
t('mofu.huntcraft.length.long', '遠征 (45分)', 'Long (45 min)')

# ------------------------------------------------------------------ meals
CATS = [('grain', '穀物', 'Grain'), ('meat', '肉', 'Meat'), ('fish', '魚', 'Fish'), ('vegetable', '野菜', 'Vegetable'),
        ('mushroom', 'キノコ', 'Mushroom'), ('sweet', '甘味', 'Sweet'), ('spice', '香辛料', 'Spice')]
for cid, ja, en in CATS:
    t('meal.huntcraft.cat.' + cid, ja, en)
INGS = [
    ('kazami_rice', '風見米', 'Kazami Rice', '村の棚田で育った米。', 'Rice from the village terraces.'),
    ('golden_millet', '黄金粟', 'Golden Millet', '高原でしか実らない幻の穀物。', 'A legendary grain of the highlands.'),
    ('boar_meat', '猪肉', 'Boar Meat', '脂ののった山の恵み。', 'Rich meat from the mountains.'),
    ('marbled_meat', '霜降り肉', 'Marbled Meat', '口の中でとろける極上の肉。', 'Melt-in-the-mouth premium meat.'),
    ('river_trout', '清流マス', 'River Trout', '村の川で獲れた新鮮な魚。', 'Fresh fish from the village stream.'),
    ('king_tuna', '王様マグロ', 'King Tuna', '遠い海から届く大物。', 'A giant catch from distant seas.'),
    ('mountain_greens', '山菜', 'Mountain Greens', '春の香りがする山の青菜。', 'Fragrant wild greens.'),
    ('snow_radish', '雪大根', 'Snow Radish', '雪の下で甘く育った大根。', 'A radish sweetened under the snow.'),
    ('forest_shiitake', '森シイタケ', 'Forest Shiitake', '香り高い肉厚のキノコ。', 'Thick, aromatic mushrooms.'),
    ('honey_comb', 'ハチミツ', 'Honeycomb', '甘くて元気が出る。', 'Sweet and energizing.'),
    ('fire_pepper', '火炎トウガラシ', 'Fire Pepper', '火を吹くほど辛い。', 'Hot enough to breathe fire.'),
]
for iid, ja, en, dja, den in INGS:
    t('meal.huntcraft.ing.' + iid, ja, en)
    t('meal.huntcraft.ing.%s.desc' % iid, dja, den)
DISHES = [('donburi', '丼', 'Bowl'), ('sushi', '寿司', 'Sushi'), ('zosui', '雑炊', 'Porridge'), ('takikomi', '炊き込みご飯', 'Mixed Rice'),
          ('dango', '団子', 'Dumplings'), ('yakionigiri', '焼きおにぎり', 'Grilled Rice Ball'), ('hamayaki', '浜焼き', 'Seaside Grill'),
          ('nabe', '鍋', 'Hot Pot'), ('kushiyaki', '串焼き', 'Skewers'), ('kakuni', '角煮', 'Braise'), ('gekikara', '激辛炒め', 'Inferno Fry'),
          ('tempura', '天ぷら', 'Tempura'), ('dobinmushi', '土瓶蒸し', 'Teapot Broth'), ('kanroni', '甘露煮', 'Candied Simmer'),
          ('nanban', '南蛮漬け', 'Nanban Marinade'), ('shojin', '精進料理', 'Temple Platter'), ('shiraae', '白和え', 'Creamy Salad'),
          ('tsukemono', '漬物', 'Pickles'), ('mitarashi', 'みたらし', 'Glazed Skewers'), ('itame', '炒め物', 'Stir-fry'),
          ('karintou', 'かりんとう', 'Sweet Crisps')]
for did, ja, en in DISHES:
    t('meal.huntcraft.dish.' + did, ja, en)
t('meal.huntcraft.dish_name', '%sと%sの%s', '%s & %s %s')
FSK = [('carve', '剥ぎ取り上手', 'Carving Pro', '大型モンスターの剥ぎ取り回数+1', '+1 carve on large monsters'),
       ('guts', 'ど根性', 'Guts', '一度だけ力尽きる攻撃を体力1で耐える', 'Survive one fatal blow with 1 HP'),
       ('swift', '韋駄天', 'Swift Feet', '移動速度+8%', 'Movement speed +8%'),
       ('stamina', '健脚', 'Second Wind', 'スタミナの回復が早くなる', 'Faster stamina recovery'),
       ('regen', '自然回復', 'Regeneration', '体力が少しずつ回復する', 'Slowly regenerates health'),
       ('cheer', 'お供の応援', 'Mofu Cheer', 'お供モフの攻撃力+25%', 'Companions deal +25% damage'),
       ('power', '腕っぷし', 'Brawn', '攻撃力さらに+5%', 'Attack +5% more')]
for sid, ja, en, dja, den in FSK:
    t('meal.huntcraft.skill.' + sid, ja, en)
    t('meal.huntcraft.skill.%s.desc' % sid, dja, den)

# ------------------------------------------------------------------ story
STORY = [
    ('arrival', '風の吹く村', 'A Village of Winds',
     'ここ風見の村は、山あいの谷にある小さな村じゃ。\n昔、ひとりのハンターと一匹のモフが、谷を吹き抜ける風を読み、モンスターの動きを知って村を守った。それが始まりじゃと伝わっておる。\n\n今では集会所にギルドの出張所があり、鍛冶屋、よろず屋、モフ亭、モフの寄合所がハンターを支えておる。\n\nあんたも今日から村の一員じゃ。困ったことがあれば、いつでもわしを訪ねてくるがよい。',
     'Kazami Village is a small settlement in a mountain valley.\nLong ago, a hunter and a mofu learned to read the winds that sweep through the valley. By knowing where the monsters roamed, they protected the people here - or so the old tales say.\n\nToday the guild keeps an office in the gathering hall, and the smithy, the general store, the Mofu Diner and the Mofu Lodge all support the hunters.\n\nFrom today you are one of us. Come to me whenever you need anything.'),
    ('first_hunt', '初めての狩り', 'The First Hunt',
     '大型モンスターを狩ったそうじゃな。見事じゃ!\nじゃが忘れてはならん。わしらは命をいただいて生きておる。剥ぎ取った素材は無駄にせず、ゲンテツに良い武具へ鍛えてもらうのじゃ。\n\n狩りとは、生き物と向き合うことじゃ。恐れず、侮らず、敬うこと。それが風見のハンターの心得よ。',
     'I hear you brought down a large monster. Splendid!\nBut never forget: we live by taking lives. Waste none of what you carve - let Gentetsu forge it into fine gear.\n\nTo hunt is to face a living creature. Without fear, without contempt, with respect. That is the way of Kazami\'s hunters.'),
    ('rumors', '北と南の噂', 'Rumours from North and South',
     '近ごろ、旅の行商人から妙な噂を耳にする。\n霜嶺の山々では白き牙の獣が吹雪を呼び、空では雷を纏う大蛇が稲妻を落とすという。南の火口では、岩のような竜が大地を揺らしておるそうじゃ。\n\n風が落ち着かんのは、そのせいかもしれん。腕を磨き、備えを怠らぬようにな。',
     'Lately the travelling merchants bring strange rumours.\nIn the Frostpeak mountains a white-fanged beast calls the blizzards, and in the skies a great serpent clad in lightning strikes the earth. In the southern crater, they say, a rock-like wyvern shakes the very ground.\n\nPerhaps that is why the wind is so restless. Hone your skills, and never neglect your preparations.'),
    ('ember', '燃える谷の伝説', 'Legend of the Burning Valley',
     '…昔話をしよう。\nはるか昔、冥き炎をまとう龍がこの谷を焼き尽くそうとした。村を興したハンターとモフは、風を読んで龍の通り道を見抜き、命がけでこれを退けたという。\n\n近ごろ、熾火の大釜の奥から黒い煙が上がっておる。あの龍が戻ってきたのでなければよいが…',
     '...Let me tell you an old story.\nAges ago, a dragon wreathed in dark flame tried to burn this valley to ashes. The hunter and the mofu who founded the village read the winds, foresaw the dragon\'s path, and drove it off at the risk of their lives.\n\nOf late, black smoke rises from deep within the Ember Caldera. I pray that dragon has not returned...'),
    ('high_rank', '上位ハンターの誇り', 'Pride of a High Rank Hunter',
     'ギルドがあんたを上位ハンターと認めたそうじゃな。おめでとう!\n上位の狩り場では、モンスターも段違いに手強い。じゃが、あんたにはもう風を読む力が備わっておる。\n\n村の皆があんたを頼りにしておる。どうか、無事に帰ってくるのじゃぞ。',
     'The guild has recognized you as a high rank hunter. Congratulations!\nIn high rank hunting grounds the monsters are fiercer by far. But you already know how to read the wind.\n\nThe whole village relies on you now. Please, always come home safe.'),
    ('dark_flame', '冥き炎の果てに', 'Beyond the Dark Flame',
     '冥炎龍を退けたか…! 谷を吹く風が、ようやく穏やかになった。\nあんたの名は、村の風見柱に刻まれるじゃろう。初代のハンターとモフのようにな。\n\nじゃが、狩りに終わりはない。風はまた新しい知らせを運んでくる。これからも風見の村を、よろしく頼むぞ。',
     'You defeated Azragos...! At last the wind in the valley is calm again.\nYour name will be carved on the village wind-pillar, just like the first hunter and mofu.\n\nBut the hunt never truly ends. The wind will bring new tidings. Please keep watching over Kazami Village.'),
]
for sid, tja, ten, xja, xen in STORY:
    t('story.huntcraft.%s.title' % sid, tja, ten)
    t('story.huntcraft.%s.text' % sid, xja, xen)
t('story.huntcraft.hint.always', 'いつでも読める', 'Always available')
t('story.huntcraft.hint.first_hunt', '大型モンスターを一頭狩猟すると聞ける', 'Hunt a large monster to hear this tale')
t('story.huntcraft.hint.hr3', 'HR3 になると聞ける', 'Reach HR 3 to hear this tale')
t('story.huntcraft.hint.hr5', 'HR5 になると聞ける', 'Reach HR 5 to hear this tale')
t('story.huntcraft.hint.hr8', 'HR8 (上位) になると聞ける', 'Reach HR 8 (high rank) to hear this tale')
t('story.huntcraft.hint.azragos', '冥炎龍アズラゴスを狩猟すると聞ける', 'Hunt Azragos to hear this tale')
REWARDS = [('first_large', '初めての大型狩猟', 'First Large Hunt'), ('hr2', 'HR2 到達祝い', 'Reached HR 2'), ('hr3', 'HR3 到達祝い', 'Reached HR 3'),
           ('hr5', 'HR5 到達祝い', 'Reached HR 5'), ('hr8', '上位ハンター認定祝い', 'High Rank Celebration'),
           ('all_large', '全ての大型モンスターを狩猟', 'Every Large Monster Hunted')]
for rid, ja, en in REWARDS:
    t('reward.huntcraft.' + rid, ja, en)

# ------------------------------------------------------------------ hunter notes
NCATS = [('flying_wyvern', '飛竜族', 'Flying Wyvern'), ('fanged_beast', '牙獣族', 'Fanged Beast'), ('leviathan', '海竜族', 'Leviathan'),
         ('brute_wyvern', '獣竜族', 'Brute Wyvern'), ('elder_dragon', '古竜族', 'Elder Dragon'), ('bird_wyvern', '鳥竜族', 'Bird Wyvern'),
         ('herbivore', '草食族', 'Herbivore'), ('neopteron', '甲虫族', 'Neopteron'), ('carapaceon', '甲殻族', 'Carapaceon'),
         ('small', '小型モンスター', 'Small Monster')]
for cid, ja, en in NCATS:
    t('notes.huntcraft.category.' + cid, ja, en)
PARTS = [('head', '頭部', 'Head'), ('tail', '尻尾', 'Tail'), ('neck', '首', 'Neck'), ('wing_l', '左翼', 'Left Wing'), ('wing_r', '右翼', 'Right Wing'),
         ('leg_l', '左脚', 'Left Leg'), ('leg_r', '右脚', 'Right Leg'), ('tail_base', '尻尾の付け根', 'Tail Base'), ('hindleg_l', '左後脚', 'Left Hindleg'),
         ('hindleg_r', '右後脚', 'Right Hindleg'), ('foreleg_l', '左前脚', 'Left Foreleg'), ('foreleg_r', '右前脚', 'Right Foreleg'),
         ('leg_fl', '左前脚', 'Front Left Leg'), ('leg_fr', '右前脚', 'Front Right Leg'), ('body', '胴体', 'Body'), ('back', '背中', 'Back'),
         ('shell', '甲殻', 'Shell'), ('rear', '腰', 'Rear'), ('face', '顔', 'Face'), ('claw_l', '左爪', 'Left Claw'), ('claw_r', '右爪', 'Right Claw'),
         ('arms', '腕', 'Arms'), ('abdomen', '腹部', 'Abdomen'), ('horn', '角', 'Horn'), ('horns', '角', 'Horns'), ('crest', 'トサカ', 'Crest'),
         ('jaw', '顎', 'Jaw'), ('chest', '胸', 'Chest'), ('belly', '腹', 'Belly')]
for pid, ja, en in PARTS:
    t('part.huntcraft.' + pid, ja, en)

# ------------------------------------------------------------------ screens: shared
SC = [
    # item box
    ('box.prev', '前のページ', 'Previous page'), ('box.next', '次のページ', 'Next page'),
    ('box.deposit', '手持ちを全部預ける (ホットバー以外)', 'Deposit inventory (except hotbar)'), ('box.sort', '整理する', 'Sort'),
    # smith
    ('smith.title', '鍛冶屋', 'Blacksmith'), ('smith.tab_weapon', '武器', 'Weapons'), ('smith.tab_armor', '防具', 'Armor'),
    ('smith.tab_companion', 'お供', 'Mofu'), ('smith.tab_levelup', '強化', 'Level Up'),
    ('smith.no_recipes', 'まだ作れる装備がない。', 'No recipes yet.'),
    ('smith.none_craftable', '今の素材で作れる装備はない。', 'Nothing can be forged with your materials.'),
    ('smith.no_gear', '強化できる装備を持っていない。', 'You carry no gear that can be levelled up.'),
    ('smith.pick_recipe', '左の一覧から作りたい装備を選ぼう。素材は手持ちとアイテムボックスの両方から使われる。',
     'Pick a recipe on the left. Materials are taken from your inventory and your item box.'),
    ('smith.pick_gear', '強化したい装備を選ぼう。', 'Pick the gear to level up.'),
    ('smith.filter_on', '作れる装備のみ表示中', 'Showing craftable only'), ('smith.filter_off', 'すべて表示中', 'Showing all'),
    ('smith.kind_forge', '生産', 'Forge'), ('smith.kind_upgrade', '派生強化', 'Upgrade'), ('smith.from', '強化元:', 'From:'),
    ('smith.materials', '必要素材 (所持/必要)', 'Materials (owned/needed)'), ('smith.do_forge', '生産する', 'Forge'),
    ('smith.do_upgrade', '強化する', 'Upgrade'), ('smith.do_levelup', 'レベルを上げる', 'Level up'),
    ('smith.level', 'Lv %s / %s', 'Lv %s / %s'), ('smith.next_level', 'Lv%s に必要なもの', 'Needed for Lv %s'),
    ('smith.max_level', '最大レベル!', 'Maximum level!'), ('smith.armor_set', '%s 装備', '%s Set'),
    ('smith.stat_attack', '攻撃力 %s', 'Attack %s'), ('smith.stat_affinity', '会心率 %s%%', 'Affinity %s%%'),
    ('smith.stat_element', '属性 %s %s', 'Element %s %s'), ('smith.stat_defense', '防御力 %s', 'Defense %s'),
    ('smith.for_mofu', 'お供モフの%sとして装備できる', 'Equips as a companion %s'),
    # shop
    ('shop.title', 'よろず屋', 'General Store'), ('shop.tab_buy', '買う', 'Buy'), ('shop.tab_sell', '売る', 'Sell'),
    ('shop.cat.deals', '本日の特売 (25%引き)', 'Today\'s deals (25% off)'), ('shop.cat.recovery', '回復薬', 'Recovery'),
    ('shop.cat.food', '食料', 'Food'), ('shop.cat.drink', '飲料', 'Drinks'), ('shop.cat.tool', '道具', 'Tools'),
    ('shop.cat.smithing', '鍛冶素材', 'Smithing'), ('shop.cat.material', '素材', 'Materials'),
    ('shop.cat.inventory', '手持ち', 'Inventory'), ('shop.cat.item_box', 'アイテムボックス', 'Item Box'),
    ('shop.empty', '品切れ中…', 'Sold out...'), ('shop.nothing_to_sell', '売れるものを持っていない。', 'Nothing to sell.'),
    ('shop.hint_buy', '欲しい品を選んでね。ハンターランクが上がると品揃えが増えるよ!', 'Pick an item. More goods appear as your hunter rank rises!'),
    ('shop.hint_sell', '手持ちかアイテムボックスから売りたい物を選んでね。', 'Pick something to sell from your inventory or item box.'),
    ('shop.deal', '%s z → %s z (特売!)', '%s z -> %s z (deal!)'), ('shop.owned', '所持数: %s', 'Owned: %s'),
    ('shop.locked', 'HR%s で入荷予定', 'Arrives at HR %s'), ('shop.max', '最大', 'Max'), ('shop.total', '合計 %s z', 'Total %s z'),
    ('shop.do_buy', '購入する', 'Buy'), ('shop.do_sell', '売却する', 'Sell'), ('shop.sell_price', '売値 %s z', 'Sells for %s z'),
    ('shop.from_box', 'アイテムボックスから売却', 'Selling from the item box'), ('shop.from_inventory', '手持ちから売却', 'Selling from your inventory'),
    # kitchen
    ('kitchen.title', 'モフ亭', 'Mofu Diner'), ('kitchen.special', '本日のおすすめ (3割引・効果アップ)', 'Today\'s special (30% off, stronger)'),
    ('kitchen.pick_special', '選ぶ', 'Pick'), ('kitchen.pick_first', '左の一覧から一つ目の食材を選ぼう', 'Pick the first ingredient on the left'),
    ('kitchen.pick_second', '違う種類の食材をもう一つ選ぼう', 'Pick a second ingredient of another kind'),
    ('kitchen.special_badge', '★本日のおすすめ', '★ Today\'s special'), ('kitchen.hp', '体力 +%s', 'Health +%s'),
    ('kitchen.stamina', 'スタミナ +%s', 'Stamina +%s'), ('kitchen.attack', '攻撃 +%s%%', 'Attack +%s%%'), ('kitchen.defense', '防御 +%s', 'Defense +%s'),
    ('kitchen.skills', '食事スキル', 'Meal skills'), ('kitchen.eat', 'いただきます!', 'Let\'s eat!'),
    ('kitchen.current', '食事中: %s', 'Current meal: %s'), ('kitchen.remaining', '残り %s:%s (クエストから戻るまで)', '%s:%s left (until you return from a quest)'),
    ('kitchen.no_meal', 'まだ何も食べていない', 'You have not eaten yet'), ('kitchen.book', '料理帳 %s / %s 品', 'Recipe book %s / %s dishes'),
    # mofu
    ('mofu.title', 'モフの寄合所', 'Mofu Lodge'), ('mofu.tab_roster', 'お供', 'Companions'), ('mofu.tab_gear', '装備', 'Gear'),
    ('mofu.tab_hire', '雇う', 'Hire'), ('mofu.tab_expedition', '探索', 'Expeditions'),
    ('mofu.first_intro', '「最初の相棒は、わしが特別に紹介するニャ。好きな子を選ぶがよい」', '"I will introduce your first partner myself, nya. Choose whoever you like."'),
    ('mofu.fur', '毛並み', 'Fur'), ('mofu.name', '名前', 'Name'), ('mofu.random_name', 'おまかせ', 'Random'),
    ('mofu.decide', 'この子に決めた!', 'This one!'), ('mofu.no_companions', 'お供がいない。「雇う」タブで新しいお供を探そう。', 'No companions. Find one in the Hire tab.'),
    ('mofu.empty_slot', '- 空き -', '- empty -'), ('mofu.stay_on', '村で待機中', 'Waiting in the village'), ('mofu.stay_off', 'お供が同行中', 'Companion follows'),
    ('mofu.stat_hp', '体力 %s', 'HP %s'), ('mofu.stat_def', '防御 %s', 'DEF %s'), ('mofu.stat_atk', '攻撃 %s', 'ATK %s'),
    ('mofu.hunts', '共に狩った大型: %s 頭', 'Large hunts together: %s'), ('mofu.skills', 'スキル', 'Skills'),
    ('mofu.skill_locked', '%s (Lv%s)', '%s (Lv %s)'), ('mofu.dismiss_confirm', '本当に %s とお別れする?', 'Really part with %s?'),
    ('mofu.yes', 'はい', 'Yes'), ('mofu.no', 'いいえ', 'No'), ('mofu.retrain_cost', '転向費用 %s z', 'Retraining costs %s z'),
    ('mofu.cancel', 'やめる', 'Cancel'), ('mofu.rest', '休ませる', 'Rest'), ('mofu.set_active', 'お供にする', 'Take along'),
    ('mofu.train', '修行', 'Train'), ('mofu.train_tip', '%s z で修行 (経験 +%s)', 'Train for %s z (+%s XP)'),
    ('mofu.retrain', '転向', 'Retrain'), ('mofu.dismiss', 'お別れ', 'Dismiss'),
    ('mofu.away', '探索に出かけている', 'Away on an expedition'), ('mofu.following', '同行中', 'Accompanying you'),
    ('mofu.none', 'なし', 'None'), ('mofu.owned_gear', '所持している%s (クリックで装備)', 'Your %s pieces (click to equip)'),
    ('mofu.no_gear', '装備がない。鍛冶屋の「お供」タブで作ろう。', 'No gear. Forge some in the smith\'s Mofu tab.'),
    ('mofu.hire_full', '「もう手一杯のようじゃニャ。誰かと別れてから来るのじゃ」', '"Your hands are full, nya. Part with someone first."'),
    ('mofu.hire_intro', '「どの子も腕は確かニャ。気に入った子を連れて行くとよい」', '"They are all capable, nya. Take whoever you like."'),
    ('mofu.hire', '雇う (%s z)', 'Hire (%s z)'), ('mofu.no_candidates', '今は紹介できる子がいない', 'No candidates right now'),
    ('mofu.refresh', '別の子を探す (%s z)', 'Find others (%s z)'),
    ('mofu.exp_at', '%s を探索中', 'Exploring %s'), ('mofu.exp_time', '帰還まで %s:%s', 'Back in %s:%s'),
    ('mofu.exp_hunts', '大型狩猟でも早く戻る: %s / %s', 'Large hunts also bring them back: %s / %s'),
    ('mofu.exp_note', '持ち帰った素材はアイテムボックスに届く。探索はオフライン中も進む。', 'Materials go to your item box. Expeditions continue while you are offline.'),
    ('mofu.recall', '呼び戻す (素材なし)', 'Recall (no loot)'), ('mofu.exp_req', 'お供Lv%s / HR%s 以上', 'Companion Lv %s / HR %s'),
    ('mofu.exp_loot', '見つかる素材', 'Possible finds'), ('mofu.send', '%s へ送り出す', 'Send to %s'), ('mofu.last_loot', '前回の成果', 'Last haul'),
    # notes
    ('notes.title', 'ハンターノート', 'Hunter\'s Notes'), ('notes.tab_monsters', 'モンスター', 'Monsters'), ('notes.tab_card', 'ギルドカード', 'Guild Card'),
    ('notes.none', 'モンスターの記録がない。', 'No monster records.'), ('notes.hunted', '討伐数 %s', 'Slain: %s'), ('notes.captured', '捕獲数 %s', 'Captured: %s'),
    ('notes.smallest', '最小 %s%%', 'Smallest %s%%'), ('notes.largest', '最大 %s%%', 'Largest %s%%'), ('notes.weakness', '弱点属性', 'Weaknesses'),
    ('notes.unknown', 'まだ狩猟したことがない。狩猟すると詳しい情報が記録される。', 'Not hunted yet. Hunt it to record its details.'),
    ('notes.breakable', '部位破壊: %s', 'Breakable: %s'), ('notes.materials', '剥ぎ取り素材 (下位)', 'Carves (low rank)'),
    ('notes.more_hunts', 'あと %s 回狩猟すると素材の情報が記録される', 'Hunt %s more to record its materials'),
    ('notes.hrp', 'HRP %s / %s', 'HRP %s / %s'), ('notes.card_zenny', '所持金: %s z', 'Zenny: %s z'),
    ('notes.card_quests', 'クリアしたクエスト: %s', 'Quests cleared: %s'), ('notes.card_hunts', '狩猟数: %s (大型 %s)', 'Hunts: %s (large %s)'),
    ('notes.card_captures', '捕獲数: %s', 'Captures: %s'), ('notes.card_favorite', 'よく狩るモンスター: %s (%s)', 'Most hunted: %s (%s)'),
    ('notes.card_mofu', 'お供: %s / %s', 'Companions: %s / %s'), ('notes.card_dishes', '料理帳: %s / %s', 'Recipe book: %s / %s'),
    ('notes.card_story', '村の話: %s / %s', 'Village tales: %s / %s'),
    # elder
    ('elder.title', '村長の家', 'Village Chief'), ('elder.tab_story', '村の話', 'Tales'), ('elder.tab_guide', '次の目標', 'Guidance'),
    ('elder.tab_rewards', '褒美', 'Rewards'), ('elder.locked', '- まだ聞けない -', '- not yet -'),
    ('elder.hrp', 'あと %s HRP で HR%s', '%s HRP to HR %s'), ('elder.goals', '村長のすすめ', 'The chief suggests'),
    ('elder.goal_mofu', '東のモフの寄合所でモフ長老に会い、最初のお供を選ぼう。', 'Meet the Mofu Elder at the lodge in the east and choose your first companion.'),
    ('elder.goal_meal', '狩りの前はモフ亭で腹ごしらえ。食材の組み合わせでスキルが付く。', 'Eat at the Mofu Diner before a hunt: ingredient pairings give skills.'),
    ('elder.goal_quest', '北の集会所の受付でクエストを受け、大型モンスターに挑もう。', 'Take a quest at the gathering hall desk in the north and face a large monster.'),
    ('elder.goal_hunt', '%s はまだ未狩猟じゃ。挑んでみるとよい。', 'You have not hunted %s yet. Give it a try.'),
    ('elder.goal_hunt_weak', '%s はまだ未狩猟じゃ。%s属性に弱いと聞くぞ。', 'You have not hunted %s yet. They say it is weak to %s.'),
    ('elder.goal_reward', '受け取っていない褒美が %s 件あるぞ。', 'You have %s reward(s) waiting.'),
    ('elder.goal_expedition', '手の空いたお供は探索に出すと素材を集めてくれる。', 'Send idle companions on expeditions to gather materials.'),
    ('elder.goal_free', '今は自由に狩りを楽しむとよい。風はあんたの味方じゃ。', 'Hunt as you please for now. The wind is with you.'),
    ('elder.next_unlocks', 'HR%s で解放されるもの', 'Unlocked at HR %s'), ('elder.no_unlocks', '特になし', 'Nothing new'),
    ('elder.unlock_shop', '商品: %s', 'Goods: %s'), ('elder.unlock_ingredient', '食材: %s', 'Ingredient: %s'),
    ('elder.unlock_expedition', '探索先: %s', 'Expedition: %s'), ('elder.unlock_gear', 'お供装備: %s', 'Mofu gear: %s'),
    ('elder.claim', '受け取る', 'Claim'), ('elder.claimed', '受取済み', 'Claimed'),
]
for k, ja, en in SC:
    t('screen.huntcraft.' + k, ja, en)


# ------------------------------------------------------------------ checks + write
def placeholders(s):
    return len(re.findall(r'%(?:\d+\$)?s', s))


def check():
    bad = False
    for k in JA:
        if placeholders(JA[k]) != placeholders(EN[k]):
            print('placeholder mismatch', k, JA[k], '|', EN[k])
            bad = True
    src = os.path.join(ROOT, 'src', 'main', 'java', 'com', 'huntcraft', 'village')
    used = set()
    for dp, _, fs in os.walk(src):
        for f in fs:
            if f.endswith('.java'):
                used |= set(re.findall(r'"((?:screen|message|npc|mofu|meal|village|story|reward|tooltip|smith_group|notes|part|container)\.huntcraft\.[a-z0-9_.]+)"',
                                       open(os.path.join(dp, f), encoding='utf-8').read()))
    core = {}
    for lang in ('ja_jp', 'en_us'):
        for f in os.listdir(os.path.join(ROOT, 'src', 'main', 'lang', lang)):
            if f.endswith('.json') and f != 'village.json':
                core.update(json.load(open(os.path.join(ROOT, 'src', 'main', 'lang', lang, f), encoding='utf-8')))
    for k in sorted(used):
        if k.endswith('.'):
            continue
        if k not in JA and k not in core:
            print('MISSING key used in code:', k)
            bad = True
    for k in JA:
        if k in core:
            print('overrides a core key:', k)
    return not bad


if __name__ == '__main__':
    ok = check()
    for lang, d in (('ja_jp', JA), ('en_us', EN)):
        p = os.path.join(ROOT, 'src', 'main', 'lang', lang, 'village.json')
        with open(p, 'w', encoding='utf-8') as f:
            json.dump(dict(sorted(d.items())), f, ensure_ascii=False, indent=1)
    print('keys', len(JA), 'ok' if ok else 'PROBLEMS')
    sys.exit(0 if ok else 1)
