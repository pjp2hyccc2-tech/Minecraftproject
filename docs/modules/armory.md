# 武器庫（名品の武器・防具セット・ボス武器）

オーナーの依頼「武器種ごとに＋30の武器と、防具を30種類追加」「敵、中ボス、ボスの武器も作って」
「ボスの武器は特殊な性能を持たせて、派手で個性的なスキルも」に対応するモジュール。
名前・見た目はすべてオリジナル（他作品の名前を使わない。`ArmoryResourcesTest` が有名な武器名・技名の混入を検査する）。

## 中身

| 区分 | 数 | 内容 |
|---|---|---|
| 名品の武器 | 180 | 6武器種 × 30（片手剣・両手剣・双剣・槍・杖・弓）。10フィールド × 武器種ごとに3段階 |
| ボス武器 | 9 | ボス b1〜b8 と終焉の王（`final`）に1本ずつ。それぞれ専用の武技（Zキー）を持つ |
| 防具セット | 30（120部位） | 重鎧10・軽装10・法衣10。兜・胴・脚・靴の4部位、2点と4点のセット効果 |

- 定義: `item/Armory.java`（武器の表とセットの表）、`item/WeaponArts.java`（武技9種）、`item/Signatures.java`（落とす者）、
  `item/ArmoryDrops.java`（確率）。すべて Minecraft 非依存の純粋モジュール。
- 段階とレベル: フィールドごとに Lv の帯（1: 5/10/15、2: 10/15/20、3: 15/20/25、4: 20/25/30、5: 30/35/40、6: 40/45/50、7: 50、
  8: 10/15/20、9: 15/20/25、10: 20/25/30）。装備できるのはそのレベルから。
- 性能: 名品ごとに攻撃・会心・攻撃速度・間合い（`DesignProfiles` が武器の当たり判定を間合いに合わせて拡大縮小）・属性
  （炎・氷・雷・毒・聖・闇・血・地。攻撃時に確率で状態異常）・特性（ヌシ・災厄・ボスの武器）。
  入手元で強さに係数（フィールド・魔物 1.02、鍛冶 1.05、ダンジョン 1.08、ヌシ 1.10、災厄・ボス 1.12）。
  防具は型（重鎧: 防御高・体力高、軽装: 中、法衣: 防御低）と主題の属性でセット効果が決まる。

## 入手

| 入手元 | 対象 | 確率・条件 |
|---|---|---|
| 鍛冶屋 | 鍛冶製の武器53本・ボス武器9本・セット20組（80部位） | フィールド素材＋強化石＋ゴールド。ボス武器は魂（ボスの素材）で鍛える |
| フィールドの魔物 | そのフィールドの名品（魔物のLv＋5以下） | 装備ドロップの12%（強敵25%）が名品になる |
| 名品を持つ魔物 | 手にしている武器（下表） | 2%（強敵6%） |
| ヌシ（30体） | 1体に1本の得物 | 貢献者ごとに8%（最大貢献者25%） |
| 災厄（10体） | 1体に1本の武器と1組のセット（1部位ずつ） | 武器10%（最大貢献者30%）、防具20%（同50%） |
| ダンジョン | そのダンジョンの名品・セット | 報酬1回ごと NORMAL 15% / HARD 25% / EX 40% |
| ダンジョンのボス | ボス武器 | NORMAL 3% / HARD 8% / EX 15% |

名品を持つ魔物（`Signatures.WIELDED`、26種）: `bandit_chief`・`d1_bandit`・`forest_witch`・`d2_grove_priestess`・`sand_husk`・
`d3_sun_priest`・`frozen_dead`・`d4_jailer`・`d5_ice_knight`・`dragonkin_berserker`・`crater_blaze`・`celestial_paladin`・
`knight_of_the_end`・`d8_doom_guard`・`void_wanderer`・`doom_cultist`・`herald_of_ruin`・`scarecrow_dead`・`pirate_cutthroat`・
`pirate_captain` と、魔物図鑑で体がもともと剣か短剣を持つ `f1_fallen_squire`・`f3_rust_guard`・`f6_sky_knight`・`f7_doom_knight`・
`f10_ghost_sailor`・`f10_deck_pirate`。

ヌシは全30体に得物が1本ずつ（新しい10体: ドルンの牙槍・ルナールの月爪・鉱脈喰らいの節剣・オルドの甲羅杖・アグニスの神像剣・
ゼファーの雷羽・ニヒルの虚牙・ストロームの藁冠杖・ヌメルの大鰭・アンカラの大鋏）。体に武器を持つ手があるヌシ12体
（ガルム・ネブラ・ヴァルカ・アルヴィン・ゼノス・黒騎士・収穫泥棒の頭目・ストローム・メリュー・沈んだ漁師・バルバス・港の喧嘩屋）は
その得物を実際に手に持って戦う。弩の射手（フック・錆骨・峰の狙撃手）と、杖で戦うのに得物が弓のベラドンナは自分の武器のまま。
獣・巨像・飛行の体には手がないので持たない。

手に入れた名品はチャットに表示され、ボス武器は全員に告知される。

## ボス武器と武技（Zキー）

ボス武器を持つと、武器種の通常の武器スキルの代わりにその武器の武技が出る。武技は大きな予告（味方色の範囲表示）、
専用の効果音（`sounds/sfx/art/*.ogg`、`tools/audio` で生成）と粒子演出（`ArtVisuals`）を持つ。

| ボス | 武器 | 武技 | 内容 |
|---|---|---|---|
| b1 | 盗賊王の影双刃 | 影法師の乱舞 | 煙に紛れて分身し6方向から斬る。回避上昇、最後の一撃で出血 |
| b2 | 古代樹の心核 | 茨根の大輪 | 狙った場所に茨の根。敵を縛って傷つけ、味方を癒す |
| b3 | 砂塵巨神の槍 | 砂塵大渦 | 三連突きのあと前方に砂の大渦。敵を引き込み脆くする |
| b4 | 凍獄の処刑刃 | 氷断の刑刃 | 印した場所に氷の刃。弱った敵ほど深く斬り（処刑）、凍らせる |
| b5 | 氷獄女王の王笏 | 永久凍獄 | 敵を氷に閉じ込め、しばらくして牢ごと砕く |
| b6 | 竜王の焔剣 | 竜星斬 | 燃える斬撃のあと流星が降る |
| b7 | 機械神の天弓 | 雷霆の連矢 | 貫通する扇状の矢、狙った場所に落雷が続く |
| b8 | 終焉門の斧槍 | 終焉門・開 | 前方に虚無の門。敵を引き込み、まとめて炸裂 |
| final | 終焉王の黒剣 | 黒剣の驟雨 | 周囲に黒剣の雨。当たった敵を脆くし、自身の攻撃力上昇 |

## 見た目

- アイテム: 名品は既存の段階アイテム（鉄の片手剣など）の上に作られ、スタックの `ERDesign` タグと CustomModelData で見た目を切り替える
  （武器 3000 + (フィールド−1)×18 + 武器種×3 + 段階、ボス武器 3180〜3188、防具 4000 + セット×4 + 部位）。
  `src/test/resources/armory/models.txt` が番号を固定する（公開後は変えない）。
- 生成: `python3 tools/art/armory_art.py`（アイコン・手持ち・着用テクスチャ・モデル・段階アイテムの上書き表。`--check` で欠けを検査）。
  形と素材は `tools/art/named_weapon_specs.py` と `tools/art/named_armor.py`、描画は `tools/art/named_weapons.py`。
  一覧: `tools/art/armory_preview.png`。双剣はインベントリでは2本、手には1本（`forge:separate_transforms`）。
- 魔物: バニラの体はそのまま手に描かれ、人型の体（`EnemyModel`）は元の武器を隠して `HeldWeaponLayer` で右手に描く。

## セーブ互換

装備データの `design` は省略可能な項目（コーデック v1 のまま）。知らない名品・土台の合わない名品は普通の装備として読み込む。
既存の装備・キャラクターデータはそのまま有効。

## 翻訳

`docs/i18n/armory.json`（名前・説明文・セット・武技・ツールチップ・メッセージ）を
`python3 /home/claude/scripts/add_lang.py . docs/i18n/armory.json` で言語ファイルへ取り込む。

## テスト

- `ArmoryTest`: 数と段階・レベル帯、ID とモデル番号の一意性と固定、性能の範囲、ヌシ30体・災厄10体・ボス9体・ダンジョンの紐付け、
  持つ魔物、確率、鍛冶のレシピ、コーデック、セット効果、武技。
- `ArmoryResourcesTest`: 全名品のアイコン・モデル・手持ち、段階アイテムの上書き、着用テクスチャ、武技のアイコン・音、日英の名前と説明、
  名前の重複と他作品の名前の混入。
- `ArmoryGameTests`（GameTest）: スタックの名品・モデル番号・武器の性能、ボス武器で武技が始まること、普通の武器は通常スキルのまま、
  ヌシ（ストローム）が得物を持ち獣（ドルン）は持たないこと、図鑑の騎士が名品の剣を持つこと。

## 未確認（実機が必要）

- 人型の魔物が手に持つ武器の位置と角度、着用した防具の見た目、武技の粒子演出と音の印象。
- プリーストの蘇生（`RevivalService.resurrect` 経由）の実戦での動き。

## 武技（Zキー）

Zキーはすべての武器で、その武器だけの武技になる（ボス武器の9つは下の表、名品180本と段階武器42種は武技の一覧）。
名品の武技は、その武器の名前・見た目・素材・属性・手触り・出どころから決めた: 例えば 薊の双牙は棘が弾けて毒に侵す乱れ咲き、蛾翅の短剣は鱗粉の雲で
足を鈍らせ目をくらませる、梟羽の槍は音もなく羽を投げ放ち姿を消す、海魔墨の杖は墨の触腕で縛って引き寄せる、羅針の杖は四方の風、迷い子のランタンは導きの灯、
海鳥の弓は旋回する矢、鶴嘴砕きは鉱石を砕く地揺れ、鉄脈の巨剣は地面から鉄の棘が突き出す。段階武器は素材の武技（鉄の素直な斬撃、鋼の連撃、砂岩の流砂、
氷晶の凍結、炎竜の炎の波、天空の星と光、深淵の吸収）。

- 定義: `tools/art/weapon_arts.py`（1か所。名前・説明・形・追加効果・見た目）→ `item.ArtTable`（生成）→ `item.GeneratedArts`／`item.ArtForge`。
  説明文は形・状態異常・追加効果から組み立てるので、実際の効果と食い違わない。
- 強さ: 武器種共通スキルとの比較で、段階 < 名品 < ボス（`item.ArtBudget`、テストで固定）。COMBAT_SYSTEM.md 5.1。
- 見た目: `combat.forge.ArtFx` が `item.ArtLook` を当たり判定に合わせて描く。同じ見た目の武技は無い（`WeaponArtCatalogTest`）。

## 武技の一覧（Zキー）

<!-- ART TABLE BEGIN (tools/art/weapon_arts.py) -->

| 武器 | 武技 | 形 | 追加効果 | 見た目 |
|---|---|---|---|---|
| `meadowlark_blade` | ヒバリ舞いの三連斬（Larkdance Cut） | `sword_combo` | stamina | lark / crescent |
| `wayfarers_oath` | 誓いの一刺し（Oathpoint） | `sword_precise` | execute, crit_up | oath / line |
| `galewild_saber` | 野分の迅雷斬（Galewild Thunderdash） | `sword_dash` | speed | gale / spiral |
| `pasture_cleaver` | 牧草刈りの大振り（Pasture Reaping） | `great_slam` | knockback | hay / wave |
| `hillwarden_greatsword` | 丘守りの地這い（Hillwarden's Earthcrawl） | `great_sweep` | slow_field | hill / wave |
| `garms_maw` | ガルムの噛み砕き（Garm's Crushing Bite） | `great_slam` | lifesteal, knockback | garm / spikes |
| `undercity_kris` | 地下水路の影刺し（Undercanal Shadowstab） | `twin_assassinate` | vanish | undercity / line |
| `thistle_fangs` | 薊棘の乱れ咲き（Thistle Burst） | `twin_flurry` | zone | thistle / spikes |
| `hooks_reapers` | フックの鉤裂き（Hook's Rending Pull） | `twin_assassinate` | pull, execute | hook / chain |
| `herdsman_pike` | 牧人の追い突き（Herdsman's Drive） | `spear_thrusts` | knockback | herd / helix |
| `canal_partisan` | 水門開きの奔流突き（Sluicegate Torrent） | `spear_lance` | slow_field | canal / wave |
| `durns_tuskspear` | ドルンの猪突（Durn's Tusk Charge） | `spear_charge` | launch | tusk / comet |
| `sprout_wand` | 若芽の三つ弾（Sprouting Seeds） | `staff_orbs` | regen | sprout / orbs |
| `lantern_of_the_lost` | 迷い灯の導き（Guiding Wisplights） | `staff_orbs` | heal | wisp / orbs |
| `dandelion_staff` | 綿毛の旅立ち（Dandelion Drift） | `staff_volley` | speed | dandelion / scatter |
| `shepherds_bow` | 見張り台の扇射ち（Watchtower Spread） | `bow_fan` | evasion | watch / fan |
| `rogues_recurve` | 毒蛇の一矢（Viper Shot） | `bow_snipe` | execute | viper / line |
| `skylark_longbow` | 揚げ雲雀の囀り矢（Skylark Chorus） | `bow_volley` | speed | skylark / spiral |
| `moonleaf_blade` | 月葉の舞斬り（Moonleaf Waltz） | `sword_combo` | evasion | moonleaf / petals |
| `heartwood_edge` | 芯木の年輪斬り（Heartwood Rings） | `sword_combo` | defense_up | heartwood / ring |
| `eclipse_moonblade` | 月蝕の断ち（Eclipse Severance） | `sword_precise` | mark, execute | eclipse / crescent |
| `thornwall_greatsword` | 茨垣の隆起（Bramblewall Rise） | `great_slam` | zone, thorns | bramble / spikes |
| `rootbreaker` | 根砕きの地割れ（Rootbreaker Fissure） | `great_slam` | stun | root / wave |
| `nightbough_claymore` | 夜枝の大薙ぎ（Nightbough Sweep） | `great_sweep` | siphon | nightbough / fan |
| `silkcutter_pair` | 絹断ちの糸疾風（Silkcutter Gale） | `twin_flurry` | speed | silk / arc |
| `moth_wing_daggers` | 鱗粉の幻惑（Scale-Dust Daze） | `twin_cross` | slow_field, blind | moth / dome |
| `lunars_moonclaws` | ルナールの月牙（Lunar's Crescent Fangs） | `twin_flurry` | evasion | lunar / crescent |
| `vinewhip_spear` | 蔓鞭の絡め突き（Vinelash Snare） | `spear_lance` | pull | vine / chain |
| `owl_feather_lance` | 梟羽の静かな連矢（Silent Owlfeathers） | `twin_fan` | vanish | owl / fan |
| `grovewarden_glaive` | 森番の倒木払い（Grovewarden's Clearing） | `spear_thrusts` | knockback | grove / wave |
| `mosslight_staff` | 苔灯りの癒し玉（Mosslight Motes） | `staff_orbs` | field_heal | mossglow / orbs |
| `crescent_druid_staff` | 三日月の冷光槍（Crescent Frostlance） | `staff_lance` | execute | crescentfrost / line |
| `nebulas_starbranch` | ネブラの星降らし（Nebula's Starfall） | `staff_meteor` | cleanse | nebula / rain |
| `willow_shortbow` | 柳風の速射（Willowwind Rapid） | `bow_volley` | evasion | willow / arc |
| `silverbark_bow` | 銀樹皮の月光矢（Silverbark Moonshot） | `bow_fan` | crit_up | silverbark / fan |
| `belladonnas_nightshade` | 宵闇の毒花雨（Nightshade Rain） | `bow_rain` | zone | nightshade / rain |
| `ore_vein_sword` | 鉱脈なぞりの連斬（Orevein Trace） | `sword_combo` | defense_up | ore / crescent |
| `sunscarab_khopesh` | 日輪甲虫の鉤刃（Sunscarab Hook） | `sword_precise` | pull, execute | sunscarab / sigil |
| `vein_eaters_jointblade` | 鉱脈喰らいの節走り（Vein-Eater's Segment Run） | `sword_dash` | execute | centipede / chain |
| `pickaxe_breaker` | 鉱石砕きの地揺れ（Oreshatter Quake） | `great_slam` | stun | oreshatter / wave |
| `coalheart_greatsword` | 炭芯の熾火打ち（Coalheart Emberstrike） | `great_eruption` | imbue | coal / geyser |
| `colossal_ironvein` | 鉄脈の大棘（Ironvein Spikes） | `great_slam` | zone, knockback | ironspike / spikes |
| `ember_shivs` | 熾火の連打ち（Ember Patter） | `twin_flurry` | imbue | ember / scatter |
| `tombsand_fangs` | 墓砂の牙刺し（Tombsand Bite） | `twin_assassinate` | slow_field | tombsand / vortex |
| `cogwheel_daggers` | 歯車仕掛けの回転刃（Clockwork Whirl） | `twin_cross` | stamina | gear / ring |
| `minecart_pike` | 線路走りの一直突き（Railrun Thrust） | `spear_lance` | dash | rail / line |
| `veined_steel_lance` | 青筋鋼の三段突き（Bluevein Triple） | `spear_thrusts` | crit_up | bluevein / helix |
| `dolgas_tomb_halberd` | ドルガの墓守薙ぎ（Dolga's Gravesweep） | `great_sweep` | taunt | grave / fan |
| `geode_staff` | 晶洞の結晶弾（Geode Burst） | `staff_burst` | shield | geode / scatter |
| `sunpriest_sceptre` | 陽神官の日輪柱（Sunpriest's Pillar） | `staff_beam` | heal | sunpillar / pillar |
| `golem_core_rod` | 核心の雷光槍（Corelight Lance） | `staff_lance` | stun | golemcore / line |
| `cave_wren_bow` | 坑道ミソサザイの跳ね矢（Cave Wren Hop） | `bow_volley` | backstep | wren / arc |
| `firedamp_longbow` | 坑気の爆ぜ矢（Firedamp Blast） | `bow_impact` | zone | firedamp / burst |
| `rustbones_rattle` | 錆骨の鳴り矢（Rattlebone Shot） | `bow_snipe` | siphon | rattlebone / line |
| `rivermist_sword` | 川霧の流れ斬り（Rivermist Flow） | `sword_combo` | evasion | rivermist / wave |
| `pearlguard_blade` | 真珠守りの光輪（Pearlguard Halo） | `sword_burst` | shield | pearl / ring |
| `frostcastle_rapier` | 氷城の一点突き（Frostcastle Point） | `sword_precise` | execute | frostcastle / spikes |
| `cascade_greatsword` | 瀑布落とし（Cascade Drop） | `great_slam` | knockback | cascade / pillar |
| `jailers_verdict` | 看守の判決（Jailer's Sentence） | `great_guillotine` | mark, execute | jail / cage |
| `gouras_avalanche` | ゴウラの雪崩落とし（Goura's Avalanche） | `great_slam` | zone, stun | avalanche / wave |
| `icicle_pair` | 氷柱の連投げ（Icicle Volley） | `twin_fan` | — | icicle / scatter |
| `spirit_tear_daggers` | 滝霊の涙雨（Spirit Teardrops） | `twin_assassinate` | heal | teardrop / rain |
| `mistweavers_fangs` | 霧糸の毒網（Mistweaver's Venom Web） | `twin_flurry` | slow_field | web / cage |
| `whitewater_spear` | 白波の渡し突き（Whitewater Crossing） | `spear_lance` | knockback | whitewater / wave |
| `pilgrim_glaive` | 巡礼の祈り突き（Pilgrim's Prayer） | `spear_thrusts` | cleanse | pilgrim / sigil |
| `falls_sovereign_trident` | 滝主の三叉奔流（Sovereign Torrent） | `spear_lance` | zone, pull | sovereign / fan |
| `holy_dew_staff` | 聖露の慈雨（Holy Dew Shower） | `staff_orbs` | heal | holydew / rain |
| `handmaidens_snowbell` | 雪鈴の澄んだ響き（Snowbell Chime） | `staff_burst` | cleanse, shield | bell / ring |
| `ords_shellstaff` | オルドの甲羅の守り（Ord's Shellguard） | `staff_meteor` | shield | shell / dome |
| `heron_bow` | 白鷺の舞い矢（Heron Glide） | `bow_volley` | speed | heron / spiral |
| `frozen_chain_bow` | 凍鎖縛りの矢（Frostchain Bind） | `bow_snipe` | stun | frostchain / chain |
| `rainbow_spray_bow` | 虹飛沫の扇（Rainbow Spray） | `bow_fan` | heal | rainbow / fan |
| `cinder_rune_sword` | 燠文字の刻印（Cinder Glyph） | `sword_burst` | mark | cinderrune / sigil |
| `magma_vein_falchion` | 溶岩脈の三段斬り（Magma Vein Triple） | `sword_combo` | zone | magma / crescent |
| `agnis_idol_blade` | アグニスの神火落とし（Agnis's Sacred Flame） | `sword_cleave` | mark, attack_up | idolfire / pillar |
| `ruin_guardian_slab` | 石板落とし（Tablet Crush） | `great_slam` | defense_up | ruinstone / dome |
| `amber_sun_greatsword` | 琥珀陽の大薙ぎ（Ambersun Arc） | `great_sweep` | party_attack | amber / fan |
| `ashen_dragonfang` | 灰竜の咆炎（Ashen Dragon Roar） | `great_eruption` | zone, knockback | ashdragon / geyser |
| `ember_moth_blades` | 火蛾の羽ばたき（Embermoth Flutter） | `twin_cross` | evasion | embermoth / petals |
| `salamander_tongues` | 火蜥蜴の舌打ち（Salamander Lash） | `twin_chain` | imbue | salamander / chain |
| `glyphcut_daggers` | 碑文刻みの連刻（Glyphcut Engraving） | `twin_assassinate` | crit_up | glyph / sigil |
| `scorchpoint_spear` | 焦尖の三連突き（Scorchpoint Triple） | `spear_thrusts` | imbue | scorch / helix |
| `guardian_plate_halberd` | 守護板の城壁突き（Bulwark Thrust） | `spear_lance` | shield | guardian / line |
| `valkas_berserk_lance` | ヴァルカの狂乱突撃（Valka's Frenzy Charge） | `spear_charge` | attack_up, lifesteal | frenzy / comet |
| `kiln_staff` | 窯火の焼き玉（Kilnfire Orbs） | `staff_orbs` | zone | kiln / orbs |
| `lava_oracle_staff` | 溶岩巫女の託宣（Lava Oracle） | `staff_meteor` | mark | oracle / rain |
| `scorch_eyes_gaze` | 灼眼の睨み（Scorching Gaze） | `staff_beam` | stun | scorcheye / line |
| `sunstone_bow` | 陽石の光矢（Sunstone Ray） | `bow_fan` | crit_up | sunstone / star |
| `flamewing_bow` | 炎翼の三矢（Flamewing Trio） | `bow_volley` | imbue | flamewing / comet |
| `rune_tablet_longbow` | 石版の雷文字（Tablet Thunderscript） | `bow_snipe` | mark | runethunder / sigil |
| `cloudcutter` | 雲裂き一閃（Cloudcutter Flash） | `sword_dash` | evasion | cloud / arc |
| `skyforge_saber` | 天工の雷刃（Skyforge Thunderblade） | `sword_precise` | stun | skyforge / line |
| `alvins_oathblade` | アルヴィンの天誓（Alvin's Sky Oath） | `sword_combo` | shield, party_attack | skyoath / cross |
| `glacier_crest_claymore` | 氷嶺の大薙ぎ（Glacier Crest Sweep） | `great_sweep` | slow_field | glacier / fan |
| `thunderhorn_greatsword` | 雷角の突き上げ（Thunderhorn Uppercut） | `great_rend` | launch | horn / spikes |
| `thunderhead_greatblade` | 雷雲の落雷斬（Thunderhead Strike） | `great_guillotine` | zone, stun | thunderhead / pillar |
| `wyvern_talon_pair` | 飛竜の爪裂き（Wyvern Talonrake） | `twin_flurry` | lifesteal | talon / crescent |
| `sky_ruin_shards` | 天遺跡の光片（Skyruin Shardlight） | `twin_fan` | crit_up | skyshard / scatter |
| `zephyrs_thunderplumes` | ゼファーの雷羽嵐（Zephyr's Plumestorm） | `twin_flurry` | speed, zone | plumestorm / spiral |
| `summit_spear` | 頂の旗突き（Summit Banner） | `spear_lance` | party_attack | summit / line |
| `celestial_pillar_glaive` | 天柱の月弧（Celestial Crescent） | `great_sweep` | mark | skypillar / crescent |
| `lightning_rod_lance` | 避雷針の誘雷（Rodcall Lightning） | `spear_javelin` | mark | rod / pillar |
| `stormcaller_staff` | 嵐呼びの雷雲（Stormcall Cloud） | `staff_burst` | zone | stormcloud / vortex |
| `cirrus_rod` | 巻雲の氷羽（Cirrus Featherfrost） | `staff_volley` | speed | cirrus / fan |
| `starwell_staff` | 星井戸の汲み光（Starwell Draw） | `staff_lance` | stamina | starwell / star |
| `windrider_bow` | 風乗りの追い風矢（Tailwind Arrows） | `bow_volley` | speed | tailwind / arc |
| `tempest_recurve` | 大嵐の雷扇（Tempest Fan） | `bow_fan` | stun | tempest / fan |
| `peak_snipers_horizon` | 地平射ち（Horizon Shot） | `bow_snipe` | execute, crit_up | horizon / line |
| `hollowroot_blade` | 虚根の吸い上げ（Hollowroot Drain） | `sword_combo` | siphon | hollowroot / crescent |
| `oblivion_edge` | 忘却の一太刀（Oblivion Stroke） | `sword_precise` | vanish | oblivion / line |
| `last_dusk` | 終の黄昏斬り（Last Dusk Rend） | `sword_burst` | zone, siphon | dusk / ring |
| `nightmare_greatsword` | 悪夢の振り下ろし（Nightmare Descent） | `great_guillotine` | execute | nightmare / pillar |
| `towerfall_greatsword` | 塔崩しの瓦礫薙ぎ（Towerfall Rubble） | `great_sweep` | stun | rubble / wave |
| `black_knights_requiem` | 黒騎士の鎮魂斬（Black Knight's Requiem） | `great_spin` | lifesteal, taunt | requiem / ring |
| `shadowsilk_fangs` | 影絹の縫い留め（Shadowsilk Stitch） | `twin_flurry` | slow_field | shadowsilk / chain |
| `doomguard_shivs` | 滅衛の喉裂き（Doomguard Throatcut） | `twin_assassinate` | execute | doomguard / line |
| `voidwhisper_daggers` | 虚囁の刃投げ（Voidwhisper Fan） | `twin_fan` | vanish | voidwhisper / fan |
| `corrupt_bark_spear` | 腐皮の毒槍（Rotbark Lance） | `spear_lance` | zone | rotbark / spikes |
| `gatebreaker_halberd` | 門破りの叩き割り（Gatebreaker Smash） | `spear_impale` | knockback, stun | gate / dome |
| `nihils_voidfang` | ニヒルの虚喰み（Nihil's Void Devour） | `spear_pierce` | pull, siphon | voiddrake / vortex |
| `cultist_bonestaff` | 信徒の骨呪（Bonehex） | `staff_orbs` | siphon | bonehex / orbs |
| `abyssal_lantern` | 深淵灯の導き火（Abyssal Beacon） | `staff_burst` | heal, cleanse | beacon / pillar |
| `xenos_ruinsceptre` | ゼノスの破滅宣告（Xenos's Doom Decree） | `staff_meteor` | mark, execute | ruinfire / rain |
| `deadwood_bow` | 枯木の毒棘矢（Deadwood Thornshot） | `bow_fan` | slow_field | deadwood / spikes |
| `end_howl_longbow` | 終吠えの一矢（Endhowl Shot） | `bow_pierce` | stun | endhowl / wave |
| `oblivion_shard_bow` | 忘却片の降り矢（Oblivion Shardfall） | `bow_rain` | slow_field | shardfall / rain |
| `wheatstalk_sword` | 麦刈りの三振り（Wheatcut Triple） | `sword_combo` | stamina | wheat / crescent |
| `millstone_blade` | 石臼回し（Millstone Turn） | `sword_burst` | defense_up | millstone / ring |
| `harvest_moon_sabre` | 収穫月の一閃（Harvest Moon Flash） | `sword_precise` | heal | harvestmoon / crescent |
| `plowshare_greatsword` | 鋤起こし（Plowshare Upturn） | `great_eruption` | launch | plow / wave |
| `granary_gate_blade` | 穀倉門の閂薙ぎ（Granary Bar Sweep） | `great_sweep` | knockback | granary / fan |
| `brutus_horncleaver` | ブルータスの猛牛突進（Brutus's Bull Rush） | `great_rend` | dash, knockback | bull / spikes |
| `twin_sickles` | 双鎌の刈り取り（Twin Sickle Harvest） | `twin_cross` | stamina | sickle / crescent |
| `strawman_needles` | 藁人形の呪い針（Strawdoll Curse） | `twin_fan` | stun | strawdoll / scatter |
| `blight_harvest_scythes` | 枯穂の死神刈り（Blight Reaping） | `twin_flurry` | zone, siphon | blight / vortex |
| `pitchfork_spear` | 熊手の放り上げ（Pitchfork Toss） | `spear_lance` | launch | hayfork / line |
| `golden_ear_lance` | 黄金穂の祝い突き（Golden Ear Blessing） | `spear_thrusts` | party_attack | goldenear / helix |
| `harvest_boss_reaper` | 頭目の大刈り（Harvest Boss's Great Reap） | `great_sweep` | lifesteal | reaper / fan |
| `scarecrow_post` | かかしの睨み（Scarecrow Stare） | `staff_orbs` | stun | scarecrow / orbs |
| `sunflower_staff` | 向日葵の陽だまり（Sunflower Glow） | `staff_burst` | field_heal | sunflower / petals |
| `stroms_strawcrown` | ストロームの藁火の冠（Strom's Strawfire Crown） | `staff_meteor` | zone | strawfire / geyser |
| `crow_scaring_bow` | 鴉追いの散らし矢（Crowscatter） | `bow_volley` | knockback | crow / scatter |
| `cornsilk_bow` | とうもろこし糸の扇射ち（Cornsilk Fan） | `bow_fan` | stamina | cornsilk / fan |
| `bullhorn_recurve` | 牛角の重矢（Bullhorn Heavy Shot） | `bow_impact` | knockback | bullhorn / burst |
| `reedcutter` | 葦刈りの疾走斬（Reedcutter Rush） | `sword_dash` | speed | reed / arc |
| `lake_scale_sword` | 湖鱗の波紋斬り（Lakescale Ripple） | `sword_burst` | slow_field | ripple / ring |
| `mirror_surface_blade` | 水鏡の写し斬り（Mirrorwater Reflection） | `sword_precise` | mark, evasion | mirror / cross |
| `driftwood_greatsword` | 流木の打ち上げ（Driftwood Heave） | `great_slam` | launch | driftwood / wave |
| `marshking_greatsword` | 沼王の泥沼（Marshking's Mire） | `great_eruption` | slow_field | mire / geyser |
| `numels_greatfin` | ヌメルの大鰭薙ぎ（Numel's Finsweep） | `great_sweep` | pull | fin / wave |
| `minnow_knives` | 小魚群れの投げ刃（Minnow School） | `twin_fan` | speed | minnow / scatter |
| `gel_glass_daggers` | 沼玻璃の溶け刺し（Gelglass Dissolve） | `twin_assassinate` | siphon | gel / orbs |
| `kingfisher_pair` | 翡翠鳥の急降下（Kingfisher Dive） | `twin_flurry` | leap | kingfisher / comet |
| `rusted_harpoon` | 錆銛の引き寄せ（Rusted Harpoon Haul） | `spear_lance` | pull | rustharpoon / chain |
| `pearl_tide_spear` | 真珠潮の満ち引き（Pearltide Ebb） | `spear_thrusts` | backstep | pearltide / wave |
| `drowned_fishers_gaff` | 沈んだ漁師の釣り上げ（Drowned Fisher's Catch） | `spear_lance` | pull, launch | drowned / line |
| `lotus_staff` | 蓮華の開花（Lotus Unfolding） | `staff_orbs` | field_heal | lotus / petals |
| `lakebed_oracle` | 湖底の予言光（Lakebed Prophecy） | `staff_lance` | mark | prophecy / sigil |
| `melusines_mirror` | メリューの凍て鏡（Melu's Frozen Mirror） | `staff_burst` | stun, slow_field | frozenmirror / cage |
| `reed_bow` | 葦笛の連矢（Reedpipe Trill） | `bow_volley` | stamina | reedpipe / comet |
| `dragonfly_bow` | 蜻蛉返しの矢（Dragonfly Return） | `bow_snipe` | backstep | dragonfly / spiral |
| `spirit_pearl_bow` | 精霊真珠の光雨（Spiritpearl Rain） | `bow_rain` | heal | spiritpearl / rain |
| `deckhand_cutlass` | 甲板走りの切り込み（Deckrun Slash） | `sword_dash` | stamina | deck / arc |
| `doubloon_rapier` | 金貨弾きの突き（Doubloon Flick） | `sword_precise` | crit_up | coin / scatter |
| `barbas_tidecleaver` | バルバスの潮断ち（Barbas's Tidesplitter） | `sword_cleave` | zone | tide / wave |
| `anchor_greatsword` | 錨落とし（Anchor Drop） | `great_guillotine` | pull | anchor / chain |
| `stormsail_greatsword` | 嵐帆の大旋回（Stormsail Turn） | `great_spin` | speed | stormsail / vortex |
| `ankaras_great_pincer` | アンカラの挟み潰し（Ankara's Crushing Clamp） | `great_rend` | pull, stun | pincer / cross |
| `saltspray_daggers` | 潮飛沫の連斬（Saltspray Flurry） | `twin_flurry` | slow_field | saltspray / scatter |
| `inkstorm_blades` | 墨嵐の渦（Inkstorm Whirl） | `twin_fan` | zone | ink / vortex |
| `dock_brawlers_knuckleknives` | 喧嘩屋の拳打ち（Brawler's Knuckle Barrage） | `twin_maul` | stun, attack_up | brawl / burst |
| `boarding_pike` | 斬り込み突撃（Boarding Charge） | `spear_charge` | knockback | boarding / comet |
| `windlass_harpoon` | 巻き上げ銛の引き戻し（Windlass Reel） | `spear_javelin` | pull | windlass / chain |
| `maelstrom_trident` | 大渦の三叉（Maelstrom Trident） | `spear_impale` | zone, pull | maelstrom / vortex |
| `buoy_lantern_staff` | 浮標灯の灯り（Buoylight） | `staff_orbs` | shield | buoy / pillar |
| `compass_rose_staff` | 羅針の四方風（Fourfold Winds） | `staff_volley` | speed | compass / cross |
| `kraken_ink_rod` | 海魔墨の触腕（Krakenink Tentacles） | `staff_beam` | stun, pull | krakenink / chain |
| `gunwale_bow` | 舷側の一斉射（Broadside Volley） | `bow_fan` | knockback | broadside / fan |
| `seagull_bow` | 海鳥の旋回矢（Gullwheel Arrows） | `bow_volley` | evasion | gull / spiral |
| `trade_wind_longbow` | 貿易風の遠矢（Tradewind Longshot） | `bow_pierce` | speed | tradewind / line |
| `iron:ONE_HANDED_SWORD` | 鉄刃一閃（Iron Clean Cut） | `sword_combo` | — | iron_longsword / crescent |
| `iron:TWO_HANDED_SWORD` | 鉄塊打ち（Iron Mass Strike） | `great_slam` | — | iron_greatsword / spikes |
| `iron:DUAL_BLADES` | 鉄双刃の交差（Iron Crosscut） | `twin_cross` | — | iron_twin_blades / cross |
| `iron:SPEAR` | 鉄槍の三段突き（Iron Triple Thrust） | `spear_thrusts` | — | iron_spear / helix |
| `iron:STAFF` | 鉄杖の魔弾（Iron Rod Bolt） | `staff_burst` | — | iron_staff / burst |
| `iron:BOW` | 鉄鏃の扇射ち（Iron Arrowhead Fan） | `bow_fan` | — | iron_bow / fan |
| `steel:ONE_HANDED_SWORD` | 鋼の連閃（Honed Flurry） | `sword_dash` | — | steel_longsword / arc |
| `steel:TWO_HANDED_SWORD` | 鋼の旋回斬（Steel Whirl） | `great_spin` | — | steel_greatsword / ring |
| `steel:DUAL_BLADES` | 鋼の乱れ刃（Steel Blade Storm） | `twin_flurry` | — | steel_twin_blades / scatter |
| `steel:SPEAR` | 鋼穂の貫き（Steel Point Pierce） | `spear_pierce` | — | steel_spear / line |
| `steel:STAFF` | 鋼杖の三連弾（Steel Triple Bolt） | `staff_orbs` | — | steel_staff / orbs |
| `steel:BOW` | 鋼鏃の速射（Steel Rapid Fire） | `bow_volley` | — | steel_bow / comet |
| `sandstone:ONE_HANDED_SWORD` | 砂岩の砂塵輪（Sandstone Dust Ring） | `sword_burst` | slow_field | sandstone_longsword / burst |
| `sandstone:TWO_HANDED_SWORD` | 流砂起こし（Quicksand Upheaval） | `great_eruption` | slow_field | sandstone_greatsword / geyser |
| `sandstone:DUAL_BLADES` | 砂礫の投げ刃（Grit Throw） | `twin_fan` | slow_field | sandstone_twin_blades / fan |
| `sandstone:SPEAR` | 砂柱突き（Sandpillar Thrust） | `spear_impale` | slow_field | sandstone_spear / spikes |
| `sandstone:STAFF` | 砂嵐落とし（Sandstorm Fall） | `staff_meteor` | slow_field | sandstone_staff / rain |
| `sandstone:BOW` | 砂降らしの矢（Sandrain Arrows） | `bow_rain` | slow_field | sandstone_bow / rain |
| `frost:ONE_HANDED_SWORD` | 氷晶の斬り下ろし（Icecrystal Cleave） | `sword_cleave` | — | frost_longsword / crescent |
| `frost:TWO_HANDED_SWORD` | 氷柱林立（Icicle Forest） | `great_slam` | zone | frost_greatsword / spikes |
| `frost:DUAL_BLADES` | 氷鎖の連撃（Icechain Strikes） | `twin_chain` | — | frost_twin_blades / chain |
| `frost:SPEAR` | 氷槍の一直線（Icelance Line） | `spear_lance` | — | frost_spear / line |
| `frost:STAFF` | 氷晶の魔槍（Icecrystal Spear） | `staff_lance` | — | frost_staff / line |
| `frost:BOW` | 凍て矢（Freezing Arrow） | `bow_snipe` | — | frost_bow / line |
| `flame:ONE_HANDED_SWORD` | 炎の波斬り（Flamewave Slash） | `sword_wave` | — | flame_longsword / wave |
| `flame:TWO_HANDED_SWORD` | 炎渦の大薙ぎ（Flame Swirl Sweep） | `great_sweep` | — | flame_greatsword / fan |
| `flame:DUAL_BLADES` | 火輪の双撃（Firewheel Twinstrike） | `twin_maul` | — | flame_twin_blades / burst |
| `flame:SPEAR` | 火槍投げ（Firespear Throw） | `spear_javelin` | — | flame_spear / comet |
| `flame:STAFF` | 炎爆の魔弾（Flameburst Bolt） | `staff_burst` | zone | flame_staff / burst |
| `flame:BOW` | 火炎矢（Blazing Arrow） | `bow_impact` | — | flame_bow / burst |
| `celestial:ONE_HANDED_SWORD` | 天光の三連斬（Skylight Triple） | `sword_combo` | — | celestial_longsword / crescent |
| `celestial:TWO_HANDED_SWORD` | 星落とし（Starfall Strike） | `great_guillotine` | mark | celestial_greatsword / pillar |
| `celestial:DUAL_BLADES` | 星屑の投げ刃（Stardust Throw） | `twin_fan` | — | celestial_twin_blades / fan |
| `celestial:SPEAR` | 天光槍（Skylight Lance） | `spear_pierce` | — | celestial_spear / line |
| `celestial:STAFF` | 流星招き（Meteor Call） | `staff_meteor` | — | celestial_staff / rain |
| `celestial:BOW` | 星の雨（Star Shower） | `bow_rain` | — | celestial_bow / rain |
| `abyss:ONE_HANDED_SWORD` | 深淵の吸い斬り（Abyss Drain Cut） | `sword_precise` | siphon | abyss_longsword / line |
| `abyss:TWO_HANDED_SWORD` | 深淵の裂け目（Abyss Rift） | `great_eruption` | zone, siphon | abyss_greatsword / geyser |
| `abyss:DUAL_BLADES` | 虚影の刺突（Void Shade Stab） | `twin_assassinate` | vanish | abyss_twin_blades / line |
| `abyss:SPEAR` | 虚無の引き槍（Void Pull Lance） | `spear_lance` | pull | abyss_spear / line |
| `abyss:STAFF` | 深淵の光線（Abyss Ray） | `staff_beam` | siphon | abyss_staff / line |
| `abyss:BOW` | 虚無矢（Void Arrow） | `bow_pierce` | siphon | abyss_bow / line |

<!-- ART TABLE END -->
