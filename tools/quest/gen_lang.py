#!/usr/bin/env python3
"""Quest module lang fragments: src/main/lang/{ja_jp,en_us}/quest.json (one table, both languages).
    python3 tools/quest/gen_lang.py"""
import json
import os

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..'))

Q = {}  # key -> (ja, en)


def k(key, ja, en):
    assert key not in Q, key
    Q[key] = (ja, en)


# ---------------------------------------------------------------- maps / areas / biomes
k('questmap.huntcraft.verdant_hills', '翠緑の丘陵', 'Verdant Hills')
k('questmap.huntcraft.frostpeak_range', '霜嶺山脈', 'Frostpeak Range')
k('questmap.huntcraft.ember_caldera', '熾火の大カルデラ', 'Ember Caldera')
AREAS = {
    'verdant.camp': ('ベースキャンプ', 'Base Camp'), 'verdant.meadow': ('草原の丘', 'Rolling Meadow'),
    'verdant.river': ('大河の浅瀬', 'River Shallows'), 'verdant.cliffs': ('瀑布の断崖', 'Waterfall Cliffs'),
    'verdant.ruins': ('苔むす遺跡', 'Mossy Ruins'), 'verdant.forest': ('古樹の森', 'Ancient Forest'),
    'verdant.plateau': ('飛竜の台地', 'Wyvern Plateau'), 'verdant.cave': ('風穴の洞窟', 'Wind Cave'),
    'verdant.lake': ('碧の湖畔', 'Azure Lakeside'),
    'frost.camp': ('ベースキャンプ', 'Base Camp'), 'frost.taiga': ('針葉樹の谷', 'Taiga Valley'),
    'frost.lake': ('凍てつく湖', 'Frozen Lake'), 'frost.snowfield': ('白銀の雪原', 'Silver Snowfield'),
    'frost.glacier': ('氷河の裂け目', 'Glacier Crevasses'), 'frost.pass': ('古の見張り塔', 'Old Watchtower Pass'),
    'frost.icecave': ('蒼氷の洞窟', 'Azure Ice Cavern'), 'frost.summit': ('霜嶺の頂', 'Frostpeak Summit'),
    'frost.hotspring': ('湯けむりの谷', 'Hot Spring Hollow'),
    'ember.camp': ('ベースキャンプ', 'Base Camp'), 'ember.ash': ('灰の荒野', 'Ash Wastes'),
    'ember.basalt': ('玄武の柱林', 'Basalt Pillar Forest'), 'ember.river': ('溶岩の河', 'Lava River'),
    'ember.lake': ('灼熱の溶岩湖', 'Magma Lake'), 'ember.tube': ('黒曜の溶岩洞', 'Obsidian Lava Tube'),
    'ember.graveyard': ('竜骨の墓場', 'Dragon Graveyard'), 'ember.fumaroles': ('噴気の台地', 'Fumarole Plateau'),
    'ember.altar': ('火口縁の祭壇', 'Rim Altar'),
    'fallback': ('狩猟地', 'Hunting Grounds'),
}
for a, (ja, en) in AREAS.items():
    k(f'area.huntcraft.{a}', ja, en)
BIOMES = {
    'verdant_meadow': ('翠緑の草原', 'Verdant Meadow'), 'verdant_forest': ('古樹の森', 'Ancient Forest'),
    'verdant_highlands': ('丘陵の高台', 'Verdant Highlands'), 'verdant_riverside': ('大河のほとり', 'Riverside'),
    'verdant_clearing': ('森の空き地', 'Clearing'), 'verdant_cave': ('風穴', 'Wind Cave'),
    'frostpeak_taiga': ('針葉樹林', 'Frostpeak Taiga'), 'frostpeak_snowfield': ('雪原', 'Snowfield'),
    'frostpeak_glacier': ('氷河', 'Glacier'), 'frostpeak_summit': ('霜嶺の頂', 'Frostpeak Summit'),
    'frostpeak_hot_spring': ('湯けむりの谷', 'Hot Spring Hollow'), 'frostpeak_ice_cave': ('氷窟', 'Ice Cavern'),
    'ember_outer_slopes': ('外輪山の斜面', 'Outer Slopes'), 'ember_ash_wastes': ('灰の荒野', 'Ash Wastes'),
    'ember_basalt_deltas': ('玄武岩地帯', 'Basalt Deltas'), 'ember_lava_lake': ('溶岩湖', 'Lava Lake'),
    'ember_fumaroles': ('噴気地帯', 'Fumaroles'), 'ember_lava_tube': ('溶岩洞', 'Lava Tube'),
}
for b, (ja, en) in BIOMES.items():
    k(f'biome.huntcraft.{b}', ja, en)

# ---------------------------------------------------------------- blocks / items
k('block.huntcraft.gather_herb', '採取ポイント（草）', 'Gathering Point (Herbs)')
k('block.huntcraft.gather_mushroom', '採取ポイント（キノコ）', 'Gathering Point (Mushrooms)')
k('block.huntcraft.gather_bug', '虫あみポイント', 'Bug Spot')
k('block.huntcraft.gather_bone', '骨塚', 'Bone Pile')
k('block.huntcraft.gather_mining', '採掘ポイント', 'Mining Outcrop')
k('block.huntcraft.supply_box', '支給品ボックス', 'Supply Box')
k('block.huntcraft.delivery_box', '納品ボックス', 'Delivery Box')
k('block.huntcraft.quest_board', 'クエストボード', 'Quest Board')
k('block.huntcraft.camp_flag', 'ギルドの旗', 'Guild Banner')
k('block.huntcraft.camp_flag.desc', 'クエスト終了後に使うと村へ帰還する。クエスト中はスニーク＋使用でリタイア。',
  'Use after the quest to return to the village. Sneak + use during a quest to abandon it.')
k('item.huntcraft.quest_map', '地図', 'Map')
k('item.huntcraft.quest_map.desc', 'クエスト中に使うと、狩猟地の全体地図を開く。', 'Use during a quest to open the full map of the hunting grounds.')
k('container.huntcraft.supply_box', '支給品ボックス', 'Supply Box')

# ---------------------------------------------------------------- quest types / objectives
k('questtype.huntcraft.hunt', '狩猟', 'Hunt')
k('questtype.huntcraft.capture', '捕獲', 'Capture')
k('questtype.huntcraft.slay', '討伐', 'Slay')
k('questtype.huntcraft.gather', '納品', 'Gather')
k('quest.huntcraft.objective.hunt', '%1$s %2$s頭の狩猟', 'Hunt %2$s %1$s')
k('quest.huntcraft.objective.capture', '%1$s %2$s頭の捕獲', 'Capture %2$s %1$s')
k('quest.huntcraft.objective.slay', '%1$s %2$s頭の討伐', 'Slay %2$s %1$s')
k('quest.huntcraft.objective.gather', '%1$s %2$s個の納品', 'Deliver %2$s %1$s')

# ---------------------------------------------------------------- quests: id -> (title ja, title en, client ja, client en, desc ja, desc en)
CL = {
    'guild': ('ギルドマスター', 'Guildmaster'), 'watch': ('村の警備隊長', 'Village Watch Captain'), 'apoth': ('村の薬師', 'Village Apothecary'),
    'chef': ('食事処の料理長', 'Canteen Chef'), 'ranch': ('牧場主', 'Rancher'), 'farmer': ('若い農夫', 'Young Farmer'),
    'scholar': ('調査隊の学者', 'Research Scholar'), 'bee': ('養蜂家', 'Beekeeper'), 'guide': ('山の案内人', 'Mountain Guide'),
    'smith': ('加工屋の親方', 'Master Smith'), 'miner': ('鉱夫の頭領', 'Head Miner'),
}
QUESTS = [
    ('lr1_jagraptor', 'ジャグラプトル討伐', 'Jagraptor Purge', 'watch',
     '翠緑の丘陵でジャグラプトルの群れが増えすぎて、行商人が襲われている。8頭を討伐して街道の安全を取り戻してくれ。',
     'Packs of Jagraptors in the Verdant Hills have grown bold and ambush passing merchants. Slay 8 of them and make the trade road safe again.'),
    ('lr1_herb', '薬草の納品', 'Herb Delivery', 'apoth',
     '回復薬の材料が足りなくなってきたの。翠緑の丘陵で薬草を10個摘んで、ベースキャンプの納品ボックスに届けてちょうだい。',
     "I'm running out of potion ingredients. Pick 10 Herbs in the Verdant Hills and put them in the delivery box at the base camp."),
    ('lr1_mushroom', 'アオキノコの採取', 'Blue Mushroom Foraging', 'chef',
     '森の奥や洞窟に生えるアオキノコは、滋養たっぷりの最高の食材だ。6本集めて納品してくれ！',
     'Blue Mushrooms from the deep forest and the caves make the most nourishing dishes. Bring me 6!'),
    ('lr1_grasica', '草食獣の間引き', 'Thinning the Grasica Herd', 'ranch',
     'グラシカの群れが牧草を食べ尽くしてしまう。5頭狩って群れの数を減らしてほしい。肉はみんなで分けよう。',
     "The Grasica herds are eating the pastures bare. Hunt 5 to thin the herd - we'll share the meat."),
    ('lr1_boarfang', '暴れボアファング', 'Rampaging Boarfangs', 'farmer',
     '畑を荒らすボアファングに手を焼いている。突進は強烈だから、横に避けてから攻撃するといいらしいぞ。4頭頼む！',
     'Boarfangs keep wrecking my fields. They say you should sidestep the charge and strike from the flank. Please take down 4!'),
    ('lr2_ignivald', '炎翼竜、来たる', 'The Flame-Winged Wyvern', 'guild',
     '翠緑の丘陵の台地に炎翼竜イグニヴァルドが巣を構えた。空からの火炎ブレスは脅威だが、閃光玉で叩き落とせば勝機はある。狩猟してくれ。',
     'The flame-winged Ignivald has nested on the Verdant Hills plateau. Its fire breath from the sky is deadly, but a flash bomb will bring it down. Hunt it.'),
    ('lr2_voltrix', '雷鳴の鱗竜', 'Scales of Thunder', 'scholar',
     '丘陵の川辺で、雷をまとう鱗竜ヴォルトリクスが目撃された。帯電した体は危険だが、怯んだ隙を逃すな。狩猟を頼む。',
     'The thunder-clad Voltrix was sighted by the river in the hills. Its charged body is dangerous - strike whenever it flinches. Hunt it.'),
    ('lr2_buzzling', '羽音の群れ', 'The Buzzing Swarm', 'bee',
     'バズリングの大群が花畑に居座って、ミツバチが寄りつかない。毒針に気をつけて10匹退治してくれ。',
     'A Buzzling swarm has taken over the flower fields and scared off my bees. Mind the poison stingers and swat 10 of them.'),
    ('lr2_blizraptor', '雪原の狩人', 'Hunters of the Snowfield', 'guide',
     '霜嶺山脈の登山道にブリザラプトルが群れている。寒さで体力が削られるから、ホットドリンクを忘れずに。8頭討伐してくれ。',
     "Blizraptors are packing along the Frostpeak trails. The cold saps your stamina, so don't forget Hot Drinks. Slay 8."),
    ('lr2_ice_crystal', '氷結晶を求めて', 'In Search of Ice Crystals', 'smith',
     'クーラードリンクの冷却材に氷結晶が要る。霜嶺山脈の岩場や氷窟の採掘ポイントで6個掘ってきてくれ。',
     'I need Ice Crystals to chill Cool Drinks. Mine 6 at the rocky outcrops and the ice cavern of the Frostpeak Range.'),
    ('lr2_hot_pepper', '寒冷地の必需品', 'A Cold-Climate Necessity', 'chef',
     'ホットドリンクの材料、トウガラシが品薄でね。霜嶺山脈の針葉樹林に自生しているから、8個集めてきておくれ。',
     "We're short on Hot Peppers for Hot Drinks. They grow wild in the Frostpeak taiga - gather 8 for me."),
    ('lr3_frostgarm', '氷牙の咆哮', 'Roar of the Ice Fang', 'guild',
     '霜嶺の頂に氷牙獣フロストガルムが現れた。その咆哮は雪崩を呼ぶと恐れられている。頂への山道を登り、狩猟せよ。',
     'The ice-fanged Frostgarm has appeared at the summit of the Frostpeak. Its roar is said to call avalanches. Climb the mountain trail and hunt it.'),
    ('lr3_gravadon', '岩槌の巨竜', 'The Rock-Hammer Giant', 'miner',
     '熾火の大カルデラで岩槌竜グラヴァドンが暴れ、採掘場が潰されてしまった。灼熱の地だ、クーラードリンクを持って行け。',
     "The rock-hammer Gravadon is rampaging in the Ember Caldera and crushed our mine. It's scorching there - take Cool Drinks."),
    ('lr3_ignivald_capture', '炎翼竜の生け捕り', 'Capture the Flame Wing', 'scholar',
     'イグニヴァルドの生態を調べたい。弱って足を引きずり始めたら、罠にかけて捕獲用麻酔玉を2発当ててくれ。倒してしまうと失敗だ！',
     'We want to study Ignivald alive. When it starts limping, trap it and hit it with 2 tranq bombs. Slaying it fails the quest!'),
    ('lr3_heatgecko', '溶岩トカゲ掃討', 'Heat Gecko Sweep', 'miner',
     'カルデラの採掘道にヒートゲッコーが住みついた。熱い体当たりに注意して10匹退治してくれ。',
     "Heat Geckos have settled in the caldera's mining paths. Watch out for their scorching tackles and clear out 10."),
    ('lr3_earth_crystal', '大地の結晶', 'Crystals of the Earth', 'smith',
     '武具の研ぎ石に大地の結晶が欠かせない。熾火の大カルデラの採掘ポイントで8個集めてきてくれ。',
     'Earth Crystals are essential for whetstones. Mine 8 of them in the Ember Caldera.'),
    ('lr3_yukimammo', '雪原の巨獣', 'Giants of the Snowfield', 'guide',
     '興奮したユキマンモが雪原で暴れて、交易路が通れない。巨体の突進に気をつけて3頭狩猟してくれ。',
     'Agitated Yukimammo are rampaging on the snowfield and blocking the trade route. Beware their massive charges and hunt 3.'),
    ('lr3_urgent_azragos', '冥炎の龍', 'The Netherflame Dragon', 'guild',
     '大カルデラの火口縁に、古龍・冥炎龍アズラゴスが降り立った。村を守るため、ギルドは総力を挙げてこれを迎え撃つ。お前の力を見せてくれ！',
     "The ancient Netherflame Dragon Azragos has landed on the caldera's rim. The Guild stakes everything to protect the village. Show us your strength!"),
    ('hr4_ignivald', '灼熱の炎翼竜', 'Flame Wing of the Caldera', 'guild',
     '上位個体のイグニヴァルドが、カルデラの熱気を求めて飛来した。炎はより激しく、怒りも早い。心して挑め。',
     "A high-rank Ignivald has flown in, drawn by the caldera's heat. Its flames burn fiercer and its rage comes faster. Prepare well."),
    ('hr4_voltrix', '吹雪の雷鳴', 'Thunder in the Blizzard', 'scholar',
     '霜嶺山脈の吹雪の中に雷光がひらめく。上位のヴォルトリクスの仕業に違いない。狩猟を頼む。',
     'Lightning flashes through the Frostpeak blizzards - surely the work of a high-rank Voltrix. Hunt it.'),
    ('hr4_jagraptor', '群れの掃討作戦', 'Pack Sweep Operation', 'watch',
     '上位のジャグラプトルは連携が巧みで、新米ハンターでは歯が立たない。15頭を討伐して群れを壊滅させてくれ。',
     'High-rank Jagraptors hunt in coordinated packs that overwhelm new hunters. Slay 15 and break the pack.'),
    ('hr4_lightcrystal', '輝く結晶', 'Shining Crystals', 'smith',
     '上位の武具には輝晶が必要だ。翠緑の丘陵の奥深く、岩場や洞窟の採掘ポイントで3個見つけてきてくれ。',
     "High-rank gear needs Lightcrystals. Find 3 at the mining spots deep in the Verdant Hills' cliffs and cave."),
    ('hr4_gansekigani', '岩殻蟹の大移動', 'March of the Rock Crabs', 'miner',
     'ガンセキガニの群れが玄武の柱林に押し寄せている。硬い殻は打撃でなければ割りにくいぞ。8匹討伐してくれ。',
     'A horde of Gansekigani is swarming the basalt pillars. Their shells crack best under blunt blows. Slay 8.'),
    ('hr5_frostgarm', '霜嶺の主', 'Lord of the Frostpeak', 'guild',
     '上位のフロストガルムが霜嶺の頂を支配している。凍てつく息吹で多くのハンターが倒れた。万全の準備で挑め。',
     'A high-rank Frostgarm rules the Frostpeak summit. Its freezing breath has felled many hunters. Go fully prepared.'),
    ('hr5_gravadon_capture', '岩槌竜の捕獲作戦', 'Operation: Rock Hammer', 'scholar',
     '丘陵に迷い込んだグラヴァドンを生け捕りにして、生態を調べたい。弱ったら罠と麻酔玉で捕獲してくれ。',
     'A Gravadon has strayed into the hills, and we want it alive for study. Once it weakens, use traps and tranq bombs.'),
    ('hr5_blizraptor', '吹雪の群狼', 'Wolves of the Blizzard', 'guide',
     '上位のブリザラプトルが登山者を狙っている。氷のブレスに注意して15頭討伐してくれ。',
     'High-rank Blizraptors are stalking climbers. Mind their ice breath and slay 15.'),
    ('hr5_novacrystal', '灼熱の極光石', 'Novacrystals of the Inferno', 'smith',
     '伝説の武具には極光石が要る。熾火の大カルデラの溶岩洞や火口付近で3個掘り当ててくれ。',
     'Legendary gear calls for Novacrystals. Dig up 3 in the lava tube or near the craters of the Ember Caldera.'),
    ('hr6_double', '炎と雷の饗宴', 'Feast of Flame and Thunder', 'guild',
     '翠緑の丘陵にイグニヴァルドとヴォルトリクスが同時に現れた。2頭を相手にする覚悟はあるか？ 分断して各個撃破せよ。',
     'Ignivald and Voltrix have appeared in the Verdant Hills at once. Are you ready to face both? Split them up and take them down one by one.'),
    ('hr6_gravadon', '大地を砕く者', 'The Earthbreaker', 'miner',
     '最大級のグラヴァドンがカルデラの大地を砕いている。その一撃は岩をも割る。決して正面に立つな。',
     'The largest Gravadon ever seen is shattering the caldera floor. Its blows split boulders - never stand in front of it.'),
    ('hr6_frostgarm_capture', '氷牙獣の生け捕り', 'Taking the Ice Fang Alive', 'scholar',
     '上位のフロストガルムを捕獲できれば、寒冷地の研究は大きく進む。危険な依頼だが、頼めるのはお前だけだ。',
     "Capturing a high-rank Frostgarm would greatly advance our cold-climate research. It's dangerous, but you're the only one we can ask."),
    ('hr6_urgent_azragos', '冥炎の再臨', 'Return of the Netherflame', 'guild',
     '冥炎龍アズラゴスが、さらに強大な力を宿して再び現れた。カルデラ全体が炎に包まれつつある。これが最後の戦いだ、ハンター！',
     'Azragos has returned, burning with even greater power, and the whole caldera is catching fire. This is the final battle, hunter!'),
]
for (qid, tja, ten, cl, dja, den) in QUESTS:
    k(f'quest.huntcraft.{qid}', tja, ten)
    k(f'quest.huntcraft.{qid}.client', CL[cl][0], CL[cl][1])
    k(f'quest.huntcraft.{qid}.desc', dja, den)

# ---------------------------------------------------------------- titles / messages
k('title.huntcraft.quest.start', 'クエスト開始', 'Quest Start')
k('title.huntcraft.quest.clear', 'クエストクリア！', 'Quest Clear!')
k('title.huntcraft.quest.failed', 'クエスト失敗', 'Quest Failed')
k('title.huntcraft.quest.fainted', '力尽きた…', 'You fainted...')
k('title.huntcraft.quest.faints_left', 'あと%s回力尽きるとクエスト失敗', '%s more faint(s) and the quest fails')
M = 'message.huntcraft.quest.'
msgs = {
    'no_map': ('狩猟地が見つからない（データパックを確認してください）', 'The hunting grounds could not be found (check the data pack).'),
    'map_busy': ('現在この狩猟地は満員です。少し待ってから出発してください', 'These hunting grounds are full right now. Please depart a little later.'),
    'start_failed': ('クエストの開始に失敗しました', 'The quest could not be started.'),
    'started': ('クエスト「%s」開始！ 狩猟地: %s／制限時間 %s分', 'Quest "%s" started! Location: %s / Time limit: %s min'),
    'camp_hint': ('ベースキャンプの青い支給品ボックスから支給品を受け取ろう。テントのベッドで休むと体力が回復する。',
                  'Take your supplies from the blue Supply Box at the base camp. Rest in the tent bed to recover.'),
    'at_camp': ('ベースキャンプに戻った', 'Back at the base camp'),
    'time_left': ('残り時間 %s分！', '%s minute(s) left!'),
    'target_slain': ('%s を狩猟した！ (%s/%s)', '%s slain! (%s/%s)'),
    'target_captured': ('%s を捕獲した！ (%s/%s)', '%s captured! (%s/%s)'),
    'gather_target': ('%s 所持数 %s／納品 %s', '%s carried: %s / required: %s'),
    'fainted': ('%s が力尽きた！ (残り %s回)', '%s fainted! (%s left)'),
    'no_supplies': ('このクエストの支給品ボックスではない', 'This is not the supply box of your quest.'),
    'no_delivery': ('このクエストの納品ボックスではない', 'This is not the delivery box of your quest.'),
    'nothing_to_deliver': ('このクエストに納品物はない', 'This quest has nothing to deliver.'),
    'delivered': ('%s が %s を納品した (%s/%s)', '%s delivered %s (%s/%s)'),
    'need_delivery': ('%s があと%s個必要', '%2$s more %1$s needed'),
    'flag_hint': ('クエスト中です。スニークしながら使うとリタイアして村へ戻ります', 'Quest in progress. Sneak and use the banner to abandon it and return.'),
    'return_in': ('%s秒後に村へ帰還します（ギルドの旗ですぐに帰還）', 'Returning to the village in %s seconds (use the Guild Banner to return now).'),
    'fail_reason.time': ('時間切れ', 'Time ran out'),
    'fail_reason.faints': ('3回力尽きた', 'Fainted 3 times'),
    'fail_reason.abandon': ('リタイア', 'Abandoned'),
    'fail_reason.target_slain': ('捕獲対象を倒してしまった', 'The capture target was slain'),
    'fail_reason.admin': ('管理者により中止', 'Stopped by an admin'),
    'member_left': ('%s がクエストから離脱した', '%s left the quest'),
    'reward_zenny': ('報酬金 %sz と HRP %s を獲得した', 'Received %sz and %s HRP'),
    'reward_box': ('報酬素材 %s個をアイテムボックスに送った', '%s reward items were sent to your item box'),
    'hr_up': ('ハンターランクが %s に上がった！', 'Hunter Rank increased to %s!'),
    'high_rank_unlocked': ('上位クエストが解放された！', 'High Rank quests are now available!'),
    'star_unlocked': ('★%s のクエストが解放された', '★%s quests unlocked'),
    'urgent_appeared': ('緊急クエストが出現した！', 'An Urgent Quest has appeared!'),
    'border': ('これより先へは進めない', 'You cannot go any further.'),
    'cold': ('寒さで体力が奪われていく…（ホットドリンクを飲もう）', 'The cold is draining your stamina... (drink a Hot Drink)'),
    'hot': ('暑さで体力が削られていく…（クーラードリンクを飲もう）', 'The heat is wearing you down... (drink a Cool Drink)'),
    'claimed_box': ('%s個の報酬をアイテムボックスに送った', '%s rewards sent to the item box'),
    'claimed_inv': ('%s個の報酬を受け取った', 'Received %s rewards'),
    'locked': ('このクエストはまだ受注できない', 'This quest is not available yet.'),
    'already_on_quest': ('すでにクエスト中です', 'You are already on a quest.'),
    'accepted': ('クエスト「%s」を受注した（契約金 %sz）', 'Accepted "%s" (contract fee %sz)'),
    'party_full': ('このクエストは満員です', 'This quest party is full.'),
    'too_far': ('受注者から離れすぎている', 'You are too far from the quest leader.'),
    'joined': ('%s がクエスト「%s」に参加した', '%s joined the quest "%s"'),
    'cancelled': ('クエストを破棄した（契約金は返却）', 'Quest cancelled (contract fee refunded)'),
    'left_party': ('クエストから離脱した（契約金は返却）', 'You left the quest party (contract fee refunded)'),
    'not_leader': ('出発できるのは受注者だけです', 'Only the quest leader can depart.'),
    'left_behind': ('受注者が出発したが、離れていたため参加できなかった（契約金は返却）',
                    'The leader departed without you because you were too far away (contract fee refunded).'),
    'gather_empty': ('採り尽くされている。しばらくすると復活する', 'Depleted. It will regrow after a while.'),
    'gathered': ('%s を手に入れた (x%s)', 'Got %s (x%s)'),
    'gather_depleted': ('採取ポイントを採り尽くした', 'The gathering point is depleted.'),
    'rested': ('テントで休んで体力が回復した', 'You rest in the tent and recover.'),
    'map_no_quest': ('クエスト中でないと地図は使えない', 'The map can only be used during a quest.'),
}
for key, (ja, en) in msgs.items():
    k(M + key, ja, en)
k('command.huntcraft.quest.complete', 'クエストを強制クリアしました', 'Quest force-completed')
k('command.huntcraft.quest.fail', 'クエストを強制失敗させました', 'Quest force-failed')
k('command.huntcraft.quest.none', 'クエスト中ではありません', 'Not on a quest')
k('command.huntcraft.quest.unknown', '不明なクエスト: %s', 'Unknown quest: %s')

# ---------------------------------------------------------------- screens
k('key.huntcraft.quest_map', '地図を開く（クエスト中）', 'Open quest map')
k('screen.huntcraft.quest_board', 'クエストボード', 'Quest Board')
k('screen.huntcraft.quest_result', 'クエスト結果', 'Quest Results')
k('screen.huntcraft.quest_map', '地図', 'Map')
k('screen.huntcraft.quest_map.legend', '■ベースキャンプ ■仲間 ■ペイント済みの標的　[M] 閉じる', '■ Base camp  ■ Party  ■ Painted target   [M] close')
B = 'board.huntcraft.'
board = {
    'all': ('全て', 'All'), 'loading': ('依頼を確認中…', 'Loading quests...'), 'empty': ('表示できるクエストがありません', 'No quests to show'),
    'hr_zenny': ('HR %s　所持金 %sz', 'HR %s   %sz'), 'locked': ('未解放のクエスト', 'Locked quest'),
    'locked_hint': ('★%s のキークエストをすべてクリアすると出現する', 'Appears once every key quest of ★%s is cleared'),
    'locked_hr': ('上位ハンターへの昇格が必要（下位の緊急クエストをクリア）', 'Requires promotion to High Rank (clear the Low Rank urgent quest)'),
    'urgent': ('【緊急】', '[URGENT] '), 'client': ('依頼主: %s', 'Client: %s'), 'objective': ('目的', 'Objective'),
    'map': ('狩猟地: %s', 'Location: %s'), 'time': ('制限時間 %s分', 'Time limit %s min'), 'reward': ('報酬金 %sz　HRP %s', 'Reward %sz  HRP %s'),
    'fee': ('契約金 %sz', 'Fee %sz'), 'need_hr': ('HR%s以上が必要', 'Requires HR %s'), 'hrreq': ('受注条件 HR%s以上', 'Requirement: HR %s+'),
    'cleared': ('クリア済み', 'Cleared'), 'climate.cold': ('寒冷地：ホットドリンク推奨', 'Cold climate: bring Hot Drinks'),
    'climate.hot': ('灼熱地：クーラードリンク推奨', 'Hot climate: bring Cool Drinks'), 'subrewards': ('サブ報酬', 'Sub rewards'),
    'in_quest': ('クエスト中', 'On a quest'), 'lobby_title': ('受注中のクエスト', 'Accepted quest'), 'members': ('メンバー: %s', 'Party: %s'),
    'joinable': ('募集中のクエスト', 'Open quests nearby'), 'no_joinable': ('近くで募集中のクエストはありません。受注して仲間を待とう。',
                                                                 'No open quests nearby. Accept one and wait for companions.'),
}
for key, (ja, en) in board.items():
    k(B + key, ja, en)
for key, ja, en in [('accept', '受注', 'Accept'), ('cancel', '破棄', 'Cancel'), ('leave', '離脱', 'Leave'), ('depart', '出発', 'Depart'),
                    ('join', '参加', 'Join'), ('claim', '報酬を受け取る', 'Take rewards'), ('abandon', 'リタイア', 'Abandon quest'),
                    ('abandon_confirm', '本当にリタイアする？', 'Really abandon?')]:
    k(f'button.huntcraft.quest.{key}', ja, en)
H = 'hud.huntcraft.quest.'
for key, ja, en in [('target_area', '[エリア%s]', '[Area %s]'), ('clear', 'クエストクリア！ 帰還まで %s', 'Quest clear! Return in %s'),
                    ('failed', 'クエスト失敗… 帰還まで %s', 'Quest failed... Return in %s'), ('time', '残り時間 %s', 'Time left %s'),
                    ('camp', 'ベースキャンプ', 'Base Camp'), ('area', 'エリア%s', 'Area %s'), ('cold', '寒冷', 'Cold'), ('hot', '灼熱', 'Hot')]:
    k(H + key, ja, en)
R = 'result.huntcraft.'
for key, ja, en in [('clear', 'クエストクリア！', 'Quest Clear!'), ('failed', 'クエスト失敗…', 'Quest Failed...'),
                    ('no_reward', '報酬はありません', 'No rewards'), ('time', 'クリアタイム %s', 'Clear time %s'),
                    ('faints', '力尽きた回数 %s', 'Faints %s'), ('zenny', '報酬金 %sz', 'Reward %sz'), ('hrp', 'HRP +%s', 'HRP +%s'),
                    ('hr_up', 'ハンターランクが %s に上がった！', 'Hunter Rank up! HR %s'), ('parts', '部位破壊 %s箇所', 'Parts broken: %s'),
                    ('rewards', '報酬', 'Rewards'), ('to_box', '報酬素材 %s個をアイテムボックスに送りました', '%s reward items were sent to your item box')]:
    k(R + key, ja, en)


def main():
    for lang, i in (('ja_jp', 0), ('en_us', 1)):
        out = {key: v[i] for key, v in sorted(Q.items())}
        path = os.path.join(ROOT, 'src', 'main', 'lang', lang, 'quest.json')
        os.makedirs(os.path.dirname(path), exist_ok=True)
        with open(path, 'w', encoding='utf-8') as f:
            json.dump(out, f, ensure_ascii=False, indent=2)
            f.write('\n')
    print(f'{len(Q)} keys written')


if __name__ == '__main__':
    main()
