# 武器庫（名品の武器・防具セット・ボス武器）

オーナーの依頼「武器種ごとに＋30の武器と、防具を30種類追加」「敵、中ボス、ボスの武器も作って」
「ボスの武器は特殊な性能を持たせて、派手で個性的なスキルも」に対応するモジュール。
2026-11-11 の依頼「武器はレア度が増す毎に特殊性能の強さが上がったり、Zボタンの威力も上がったりするようにして、扱えるプレイヤーレベルなども
実装して」「ダンジョンボスや災厄、ヌシなどから0.1%ぐらいの確率で…お守り、アクセサリー…それらの枠は金枠として実装して」もここに含む
（レア度と性能・必要レベル・金枠の遺宝・装身具）。
名前・見た目はすべてオリジナル（他作品の名前を使わない。`ArmoryResourcesTest` が有名な武器名・技名の混入を検査する）。

## 中身

| 区分 | 数 | 内容 |
|---|---|---|
| 名品の武器 | 180 | 6武器種 × 30（片手剣・両手剣・双剣・槍・杖・弓）。10フィールド × 武器種ごとに3段階 |
| ボス武器 | 9 | ボス b1〜b8 と終焉の王（`final`）に1本ずつ。それぞれ専用の武技（Zキー）を持つ |
| 防具セット | 30（120部位） | 重鎧10・軽装10・法衣10。兜・胴・脚・靴の4部位、2点と4点のセット効果 |
| 金枠の遺宝 | 448 | 64の出どころ（ダンジョンのボス9・災厄10・ヌシ30・放浪する大物15）ごとに武器1・防具セット1組（4部位）・アクセサリー1・お守り1 |

- 定義: `item/Armory.java`（武器の表とセットの表）、`item/WeaponArts.java`（武技9種）、`item/Signatures.java`（落とす者）、
  `item/ArmoryDrops.java`（確率）、`item/Relics.java`＋`item/RelicTable.java`（金枠の遺宝）、`item/TrinketDesign.java`（装身具）。
  すべて Minecraft 非依存の純粋モジュール。
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

## レア度と性能

レア度が上がるほど、同じ装備でも特殊性能と武技（Zキー）が強くなり、必要レベルが上がる（`ItemRarity`）。

| レア度 | 付加効果 | 基本値 | 特殊性能・武技（`power`） | 必要Lv |
|---|---|---|---|---|
| コモン | 0 | ×1.00 | ×1.00 | +0 |
| アンコモン | 1 | ×1.05 | ×1.07 | +0 |
| レア | 2 | ×1.10 | ×1.14 | +1 |
| エピック | 3 | ×1.15 | ×1.22 | +3 |
| レジェンダリー | 4 | ×1.20 | ×1.30 | +5 |

- 特殊性能 ×`power`: 付加効果の値（同じ出目でもレジェンダリーなら1.3倍）、名品の武器の属性の発動率、固定の上乗せ（会心・攻撃速度・
  ダメージ%など）、特性（吸血・反撃・回避など）、防具の固定効果、装身具の能力。ツールチップの数値は倍率込み。
- 武技 ×`power`: 手に持つ武器のレア度で、その武器の武技のダメージと追加効果（状態異常・回復・バフなどの強さ）が上がる
  （`CombatDriver.artPower`。ほかの武器の武技やジョブのスキルには掛からない）。武器の基本値（×1.20）と合わせて、
  同じ武器のレジェンダリーの武技はコモンの約1.56倍。ツールチップに「武技の威力・効果 +30%」と出る。
- 釣り合い（`RarityScalingTest`）: レア度ごとに必ず強くなる。同じレア度ならボス武器の武技が名品の最良の武技より上。

## 必要レベル（扱えるプレイヤーレベル）

- 必要Lv = 名品は名品のLv、段階の装備は段階のLv（鉄1・鋼8・砂岩15・氷晶22・炎竜30・天空37・終焉44）＋レア度の加算（上の表）、上限100
  （D-LEVEL100）。金枠の遺宝は出どころのLv＋5ちょうどで、レア度の加算はない。
- 表示: ツールチップの「必要Lv」、装備画面の候補一覧の「Lv」（足りなければ灰色）。
- サーバーが判定する場所: 装備（カバン・装備画面の `bag.equip`。足りなければ「○○を装備するにはレベル55が必要です」）、
  手に持った武器の攻撃・ガード・武技（足りなければ受け付けず「○○を扱うにはレベル55が必要です」。クライアントも先に止める）、
  能力の合計（足りない防具・装身具・武器の能力は数えない。`Gear.sum`）、武器の攻撃力の上乗せ。
  キャラクターデータの無い相手（読み込み前など）は止めない。
- 既存の装備: レア・エピック・レジェンダリーの手持ちは必要Lvが1〜5上がる。足りない間は能力が数えられず、装備画面で灰色になる。

## 金枠の遺宝（レリック）

ダンジョンのボス（b1〜b8・終焉の王）・災厄10体・ヌシ30体・放浪する大物15体の64の出どころに、それぞれの姿をかたどった遺宝が7つずつある
（武器1・防具セット1組の4部位・アクセサリー1・お守り1、計448。下の一覧）。

- 入手: 討伐の報酬を受け取る人ごとに **0.1%**（サーバー設定 `relicDropChance`、既定 0.001）。当たると武器・アクセサリー・お守り・
  防具（各部位1/16）が1/4ずつ。ダンジョン（難易度を問わず）・終焉のレイド・ヌシ・災厄・放浪する大物の報酬に入る。
- 必ずレジェンダリー（付加効果4つ）。必要Lvは出どころのLv＋5（Lv11〜60）。
- 強さ: その必要Lvでいちばん強い装備（`RelicCatalogTest`: その必要Lvで装備できる同じ武器種の名品・ボス武器・段階の武器より、
  どのレア度と比べても攻撃力が高い。防具は同じ型の名品セットより、重鎧と軽装は段階の防具よりも防御が高い）。
  武器は攻撃×1.30〜1.40、会心+3%、攻撃速度+2%、属性の発動率16%（属性型28%）、その武器種のダメージ+8%×(0.5＋Lv/50)、特性と
  スキル威力+6%、専用の武技（武器種スキルの2.05〜2.30倍。ボス武器の武技は1.95倍以上、名品は1.40〜1.85倍）。防具はセットの質×1.40と型の効果×1.5、主題の特性。
  アクセサリーは主能力 3＋0.30×Lv・副能力 2＋0.16×Lv と会心・会心ダメージ・与ダメージ・攻撃速度・移動速度・回復量のどれか。
  お守りは VIT 2＋0.2×Lv・LUK 1＋0.12×Lv・最大HP 0.6×Lv・防御 0.5×Lv と、瀕死の守り・撃破で回復・回避・反撃・吸血・最大HP% のどれか。
- 金枠: カバンのマス・装備画面の枠と候補一覧・ツールチップの枠・入手表示（専用の音）で金色の二重枠と光が回る。
  ツールチップは「金枠の遺宝・(種類)」と「(出どころ)の遺宝」。
- 告知: 落ちたときにサーバー全員へ「【金枠】(プレイヤー)が(出どころ)から(遺宝)を手に入れた！」、ほかの人には知らせの音。
- 売値は同じ段階・レア度の装備の10倍。
- ほかのモジュール向け（市場・取引掲示板の絞り込み）: `item.forge.EquipmentStacks.isGoldFrame(stack)`・`requiredLevel(stack)`・
  `rarity(stack)`。

## 装身具（アクセサリー・お守り）

- 枠: アクセサリー2つ（飾1・飾2）とお守り1つ（守）。装備画面は8枠（武器・頭・胴・脚・足・飾1・飾2・守）、能力の合計に入る。
- 種類: アクセサリーは指輪・首飾り・耳飾り・腕輪、お守りは護符・守り鈴・守り石・お守り袋（アイコンは種類と出どころの色）。
- 同じ装身具は一つだけ（2つ目は「同じ装身具は一つしか着けられません」。能力の合計でも1つと数える）。
- 付け外しは既存の UI 操作 `bag.equip`（`target`: `accessory_1`・`accessory_2`・`charm`）と `bag.unequip`。中身はプレイヤーの
  保存データ（`ERTrinkets`）にあり、死亡・リスポーン・ディメンション移動でも残る。クライアントへは UI データの `trinkets` で送る
  （新しいパケット・プロトコル版の変更なし）。
- 土台のアイテムは段階ごとの `<段階>_accessory`・`<段階>_charm`（遺宝の見た目は CustomModelData で切り替え）。

## 見た目

- アイテム: 名品は既存の段階アイテム（鉄の片手剣など）の上に作られ、スタックの `ERDesign` タグと CustomModelData で見た目を切り替える
  （武器 3000 + (フィールド−1)×18 + 武器種×3 + 段階、ボス武器 3180〜3188、防具 4000 + セット×4 + 部位、
  遺宝の武器 3500 + i、遺宝の防具 4500 + 4i + 部位、アクセサリー 5000 + 2i、お守り 5001 + 2i。i は遺宝の一覧の順）。
  `src/test/resources/armory/models.txt` が番号を固定する（公開後は変えない）。
- 生成: `python3 tools/art/armory_art.py`（アイコン・手持ち・着用テクスチャ・モデル・段階アイテムの上書き表。`--check` で欠けを検査）。
  形と素材は `tools/art/named_weapon_specs.py` と `tools/art/named_armor.py`、描画は `tools/art/named_weapons.py`。
  一覧: `tools/art/armory_preview.png`。双剣はインベントリでは2本、手には1本（`forge:separate_transforms`）。
- 遺宝: `python3 tools/art/relics.py`（`tools/art/relics_data.py` の1か所から `RelicTable.java`・`docs/i18n/relics.json`・
  下の一覧・アイコン・手持ち・着用テクスチャ・モデルを生成。`--check` で欠けと一覧の古さを検査）。武器は名品と同じ描画、防具は出どころの
  素材と紋章、装身具は `tools/art/trinket_icons.py`。一覧画像: `tools/art/relics_preview.png`。
- 魔物: バニラの体はそのまま手に描かれ、人型の体（`EnemyModel`）は元の武器を隠して `HeldWeaponLayer` で右手に描く。

## セーブ互換

装備データの `design` は省略可能な項目（コーデック v1 のまま）。知らない名品・土台の合わない名品は普通の装備として読み込む。
既存の装備・キャラクターデータはそのまま有効。装身具の枠は新しい保存項目（プレイヤーの `ERTrinkets`）で、無ければ空。
レア度の必要Lvの加算は既存の装備にも掛かる（上の「必要レベル」）。

## 翻訳

`docs/i18n/armory.json`（名前・説明文・セット・武技・ツールチップ・メッセージ）を
`python3 /home/claude/scripts/add_lang.py . docs/i18n/armory.json` で言語ファイルへ取り込む。
遺宝は `docs/i18n/relics.json`（生成）と `docs/i18n/relics_ui.json`（装身具・枠・告知・必要Lvの文言）。

## テスト

- `ArmoryTest`: 数と段階・レベル帯、ID とモデル番号の一意性と固定、性能の範囲、ヌシ30体・災厄10体・ボス9体・ダンジョンの紐付け、
  持つ魔物、確率、鍛冶のレシピ、コーデック、セット効果、武技。
- `ArmoryResourcesTest`: 全名品のアイコン・モデル・手持ち、段階アイテムの上書き、着用テクスチャ、武技のアイコン・音、日英の名前と説明、
  名前の重複と他作品の名前の混入。
- `ArmoryGameTests`（GameTest）: スタックの名品・モデル番号・武器の性能、ボス武器で武技が始まること、普通の武器は通常スキルのまま、
  ヌシ（ストローム）が得物を持ち獣（ドルン）は持たないこと、図鑑の騎士が名品の剣を持つこと。
- `RarityScalingTest`: レア度の倍率と必要Lvの加算、付加効果・属性・固定効果・特性・武技の伸び、同じレア度でボスの武技が上。
- `RelicCatalogTest`: 64の出どころすべてに7つずつ、必要Lv、その必要Lvでいちばん強いこと、約0.1%の確率と内訳、コーデック、名前と絵。
- `TrinketTest`: 装身具の枠と能力、レア度と強化での伸び、同じ装身具は1つと数えること。
- `RelicGameTests`（GameTest）: 必要Lvに届かない遺宝は装備・攻撃・ガード・武技を断られ、届けば武技が出ること、装身具が枠に入り
  能力が増えること・同じものは断られること・外すとカバンに戻ること、レジェンダリーの武技がコモンより強く当たること、
  確率を1に固定した遺宝の抽選（64の出どころすべて）で金枠・レジェンダリー・出どころ・必要Lvが正しいこと、448の遺宝がスタックになること。

## 未確認（実機が必要）

- 人型の魔物が手に持つ武器の位置と角度、着用した防具の見た目、武技の粒子演出と音の印象。
- 金枠の見え方（カバン・装備画面・ツールチップ・入手表示）、遺宝の武器・防具・装身具のアイコンと着用の見た目、告知の文と音。
- 討伐の報酬からの遺宝の抽選は、報酬の流れ（ダンジョン・ヌシ・災厄・放浪する大物）ごとには GameTest で通していない
  （抽選そのもの `RelicDrops.roll` は GameTest で確認）。
- プリーストの蘇生（`RevivalService.resurrect` 経由）の実戦での動き。

## 武技（Zキー）

Zキーはすべての武器で、その武器だけの武技になる（ボス武器の9つは下の表、名品180本と段階武器42種は武技の一覧）。
名品の武技は、その武器の名前・見た目・素材・属性・手触り・出どころから決めた: 例えば 薊の双牙は棘が弾けて毒に侵す乱れ咲き、蛾翅の短剣は鱗粉の雲で
足を鈍らせ目をくらませる、梟羽の槍は音もなく羽を投げ放ち姿を消す、海魔墨の杖は墨の触腕で縛って引き寄せる、羅針の杖は四方の風、迷い子のランタンは導きの灯、
海鳥の弓は旋回する矢、鶴嘴砕きは鉱石を砕く地揺れ、鉄脈の巨剣は地面から鉄の棘が突き出す。段階武器は素材の武技（鉄の素直な斬撃、鋼の連撃、砂岩の流砂、
氷晶の凍結、炎竜の炎の波、天空の星と光、深淵の吸収）。

- 定義: `tools/art/weapon_arts.py`（1か所。名前・説明・形・追加効果・見た目）→ `item.ArtTable`（生成）→ `item.GeneratedArts`／`item.ArtForge`。
  説明文は形・状態異常・追加効果から組み立てるので、実際の効果と食い違わない。
- 強さ: 武器種共通スキルとの比較で、段階 < 名品 < ボス・金枠の遺宝（`item.ArtBudget`、テストで固定）。COMBAT_SYSTEM.md 5.1。
  さらに手に持つ武器のレア度で ×1.00〜×1.30（上の「レア度と性能」）。
- 見た目: `combat.forge.ArtFx` が `item.ArtLook` を当たり判定に合わせて描く。同じ見た目の武技は無い（`WeaponArtCatalogTest`）。

### 武技の動きと光（2026-11-12）

オーナーの依頼「zボタンの技の視覚的効果と武器の動きが一緒すぎるのでもう少し各々派手にして」に対応。以前は武技の動きが5種類
（振り下ろし・突き・回転・詠唱・射撃）だけで、同じ武器種の武技はほとんど同じに見えていた。

- **振り付け**（`item.ArtChoreography`）: 武技の型（36種）ごとに、構え（溜めの姿勢）・一撃ごとの動き（ヒットの数だけ）・決めの姿勢を
  持つ。例: 片手剣の連撃は 右薙ぎ→左薙ぎ→回転斬り、突進は 低い構えから左右の連続斬りと斬り上げ、断ち割りは 振りかぶって振り下ろし、
  両手剣の大地割りは 跳ぶように振りかぶって地面へ叩きつけ、双剣の乱舞は 交差の構えから左右の刃が交互に斬って最後に回転、
  槍の三段突きは 短い突き2回と踏み込みの突き、投げ槍は 頭上に構えて投擲、杖は 掲げる・前に向ける・円を描く・横に払う・天から振り下ろす・
  光線を構える、弓は 速射・ためて放つ・扇に払いながら放つ・空へ放つ・低く構えて貫く。ボス武器の9武技と武器種共通スキルにも振り付けがある。
- 同じ型の武技は、名前（id）によって半分が左右逆に斬る（`ArtChoreography.forAction`）。色・形・溜めの演出も武技ごとに違う。
- **時間**（`item.ArtTimeline`）: 一撃はヒットの数ティック前に始まり、ヒットのティックで刃が届く（連続ヒットほど短く速い）。
  一撃の間は次の一撃の構えへ戻り、最後は決めの姿勢から元へ。
- **動き**（クライアント `client.ArtPoses`）: 三人称は腕と胴のひねり（肩も胴に合わせて動く）、一人称は武器の軌道。双剣は左右の刃が別々に動く。
  弓・杖の構えは視線に合わせて腕が向く。重い武技は溜めの間に小さく震える。
- **光の軌跡**（クライアント `client.ArtTrails`）: 一撃ごとに武技の色で光る（加算合成）: 横薙ぎ・大薙ぎ・斬り上げ・振り下ろし・袈裟の三日月、
  突きの光の筋と先端の衝撃の輪、回転の光の輪、叩きつけの地面を走る輪・ひび・光の柱、×字の交差、扇の筋、杖の回る魔法陣・空の魔法陣と
  降りる光・光線、弓の輪と矢の筋。溜めは武技ごとに 渦を巻いて集まる光・武器の輝き・足元の魔法陣・立ちのぼる光・回る輪・前方の魔法陣。
  重い武技の最後の一撃は自分の画面を揺らす。シェーダーパック使用中は描かない（粒子だけ）。
- **粒子**（サーバー `ArtFx`）: 同じ一撃の軌跡を粒子でも描く（三日月の点と斬撃の印、突きの筋と輪、回転の輪、着地の輪・地面の欠片・光の柱、
  魔法陣、弓の輪）。最後の一撃は衝撃波の輪が3段で広がり、重い武技は爆風も出る。

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
| `crow_scaring_bow` | 鴉追いの散らし矢（Crowscatter） | `bow_volley` | blind | crow / scatter |
| `cornsilk_bow` | とうもろこし糸の扇射ち（Cornsilk Fan） | `bow_fan` | stamina | cornsilk / fan |
| `bullhorn_recurve` | 牛角の重矢（Bullhorn Heavy Shot） | `bow_impact` | execute | bullhorn / burst |
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
| `gunwale_bow` | 舷側の一斉射（Broadside Volley） | `bow_fan` | stamina | broadside / fan |
| `seagull_bow` | 海鳥の旋回矢（Gullwheel Arrows） | `bow_volley` | evasion | gull / spiral |
| `trade_wind_longbow` | 貿易風の遠矢（Tradewind Longshot） | `bow_pierce` | speed | tradewind / line |
| `relic_b1_weapon` | 簒奪の三閃（Usurper's Triple Flash） | `sword_precise` | vanish, execute | relic_b1 / line |
| `relic_b2_weapon` | 千年輪の大震（Thousand-Ring Quake） | `great_slam` | stun, zone | relic_b2 / wave |
| `relic_b3_weapon` | 砂嵐の巨弾（Sandstorm Titanshot） | `bow_impact` | blind, slow_field | relic_b3 / burst |
| `relic_b4_weapon` | 凍獄の断罪（Frozen Verdict） | `spear_impale` | execute, mark | relic_b4 / pillar |
| `relic_b5_weapon` | 氷晶の舞踏（Crystal Ballroom） | `twin_flurry` | slow_field, evasion | relic_b5 / scatter |
| `relic_b6_weapon` | 竜王の焔穿（Dragonking Flamepierce） | `spear_charge` | imbue, launch | relic_b6 / comet |
| `relic_b7_weapon` | 機神雷断（Machina Thundercut） | `great_guillotine` | stun, mark | relic_b7 / pillar |
| `relic_b8_weapon` | 封印の大門（Sealing Gate） | `staff_meteor` | pull, zone | relic_b8 / rain |
| `relic_final_weapon` | 終焉の黙示（Apocalypse of the End） | `bow_rain` | zone, lifesteal | relic_final / rain |
| `relic_c_meadow_weapon` | 影呑みの顎（Shadow-Swallow Jaws） | `twin_assassinate` | vanish, siphon | relic_c_meadow / line |
| `relic_c_moon_forest_weapon` | 月蝕の輪光（Eclipse Halo） | `staff_burst` | heal, cleanse | relic_c_moon_forest / burst |
| `relic_c_iron_mine_weapon` | 坑底崩落（Pit Collapse） | `great_eruption` | stun, knockback | relic_c_iron_mine / geyser |
| `relic_c_sacred_falls_weapon` | 堕滝の逆流（Fallen Backflow） | `spear_lance` | pull, slow_field | relic_c_sacred_falls / wave |
| `relic_c_ancient_ruins_weapon` | 焦遺の祭火（Ruinflame Rite） | `sword_burst` | imbue, zone | relic_c_ancient_ruins / burst |
| `relic_c_soaring_peaks_weapon` | 貫天の雷矢（Skypiercing Bolt） | `bow_pierce` | stun, crit_up | relic_c_soaring_peaks / line |
| `relic_c_end_forest_weapon` | 虚空の招来（Void Summons） | `staff_lance` | siphon, mark | relic_c_end_forest / line |
| `relic_c_farm_weapon` | 枯死の刈り取り（Withering Reap） | `twin_maul` | zone, lifesteal | relic_c_farm / burst |
| `relic_c_lakeside_weapon` | 湖底の大顎（Lakebed Maw） | `sword_cleave` | knockback, slow_field | relic_c_lakeside / crescent |
| `relic_c_harbour_weapon` | 嵐呼びの雷雨（Stormcall Downpour） | `staff_volley` | stun, speed | relic_c_harbour / fan |
| `relic_l_garm_weapon` | 街道裂きの突進（Roadripper Rush） | `spear_charge` | lifesteal, knockback | relic_l_garm / comet |
| `relic_l_hook_weapon` | 赤目の一射（Red-Eye Shot） | `bow_snipe` | execute, mark | relic_l_hook / line |
| `relic_l_durn_weapon` | 大猪の地響き（Boar Rumble） | `sword_combo` | knockback, defense_up | relic_l_durn / crescent |
| `relic_l_nebula_weapon` | 星読みの扇射（Stargazer's Fan） | `bow_fan` | heal, crit_up | relic_l_nebula / fan |
| `relic_l_belladonna_weapon` | 毒華の一刺（Poisonbloom Lance） | `staff_lance` | slow_field, execute | relic_l_belladonna / line |
| `relic_l_lunar_weapon` | 銀月の旋風（Silvermoon Whirl） | `great_spin` | speed, slow_field | relic_l_lunar / ring |
| `relic_l_dolga_weapon` | 墓守の地穿ち（Warden's Groundbreaker） | `spear_impale` | stun, defense_up | relic_l_dolga / spikes |
| `relic_l_rustbone_weapon` | 錆骨の連刺（Rustbone Sting） | `twin_cross` | slow_field, execute | relic_l_rustbone / cross |
| `relic_l_vein_eater_weapon` | 鉱脈喰いの連射（Veineater Volley） | `bow_volley` | speed, lifesteal | relic_l_vein_eater / comet |
| `relic_l_goura_weapon` | 瀑布の大断（Cascade Cleave） | `great_sweep` | knockback, slow_field | relic_l_goura / wave |
| `relic_l_mistweaver_weapon` | 霧糸縫い（Mistweave Stitch） | `twin_chain` | pull, slow_field | relic_l_mistweaver / chain |
| `relic_l_ord_weapon` | 甲羅の守り珠（Shellguard Orbs） | `staff_orbs` | shield, defense_up | relic_l_ord / orbs |
| `relic_l_valka_weapon` | 血風の乱撃（Bloodgale Frenzy） | `great_rend` | lifesteal, attack_up | relic_l_valka / crescent |
| `relic_l_scorch_eye_weapon` | 灼眼の焦視（Searing Stare） | `staff_burst` | imbue, mark | relic_l_scorch_eye / burst |
| `relic_l_agnis_weapon` | 神像の聖連突（Idol's Sacred Thrusts） | `spear_thrusts` | heal, defense_up | relic_l_agnis / helix |
| `relic_l_alvin_weapon` | 天誓の聖斬（Skyoath Strike） | `sword_combo` | shield, party_attack | relic_l_alvin / crescent |
| `relic_l_peak_sniper_weapon` | 天穿の一矢（Heavenbore Arrow） | `bow_snipe` | crit_up, execute | relic_l_peak_sniper / line |
| `relic_l_zephyr_weapon` | 雷翼の乱舞（Thunderwing Flurry） | `twin_flurry` | speed, stun | relic_l_zephyr / scatter |
| `relic_l_xenos_weapon` | 破滅の予兆（Omen of Ruin） | `staff_meteor` | mark, siphon | relic_l_xenos / rain |
| `relic_l_black_knight_weapon` | 黒騎士の断罪（Black Verdict） | `sword_cleave` | execute, defense_up | relic_l_black_knight / crescent |
| `relic_l_nihil_weapon` | 虚無の喰らい牙（Void Gnaw） | `spear_pierce` | siphon, vanish | relic_l_nihil / line |
| `relic_l_brutus_weapon` | 暴れ牛の踏み砕き（Bull Stomp） | `great_slam` | knockback, launch | relic_l_brutus / spikes |
| `relic_l_harvest_boss_weapon` | 早取りの連射（Quickpick Volley） | `bow_volley` | speed, evasion | relic_l_harvest_boss / comet |
| `relic_l_strom_weapon` | 見張りの光線（Watchman's Beam） | `staff_beam` | heal, slow_field | relic_l_strom / line |
| `relic_l_melusine_weapon` | 水鏡の疾走（Mirrorwater Dash） | `sword_dash` | slow_field, vanish | relic_l_melusine / arc |
| `relic_l_drowned_fisher_weapon` | 溺れ漁師の網打ち（Drowning Net） | `spear_lance` | pull, slow_field | relic_l_drowned_fisher / line |
| `relic_l_numel_weapon` | ぬめりの大泡（Slick Bubble Burst） | `staff_burst` | slow_field, regen | relic_l_numel / burst |
| `relic_l_barbas_weapon` | 隻眼の一閃（One-Eyed Flash） | `sword_precise` | execute, crit_up | relic_l_barbas / line |
| `relic_l_dock_brawler_weapon` | 喧嘩屋の乱打（Brawler's Barrage） | `twin_cross` | knockback, stamina | relic_l_dock_brawler / cross |
| `relic_l_ankara_weapon` | 錨喰らいの大鋏（Anchor-Eater Snap） | `great_guillotine` | execute, defense_up | relic_l_ankara / pillar |
| `relic_w_bramble_knight_weapon` | 茨槍の突撃（Thornlance Charge） | `spear_lance` | zone, thorns | relic_w_bramble_knight / comet |
| `relic_w_moonmoth_witch_weapon` | 鱗粉の月雨（Moondust Rain） | `bow_rain` | zone, evasion | relic_w_moonmoth_witch / rain |
| `relic_w_slag_titan_weapon` | 熔鉄の噴出（Molten Upheaval） | `great_eruption` | imbue, zone | relic_w_slag_titan / geyser |
| `relic_w_cascade_wyvern_weapon` | 瀑流の急降下（Cascade Dive） | `spear_charge` | leap, slow_field | relic_w_cascade_wyvern / comet |
| `relic_w_mist_hermit_weapon` | 霞練りの珠（Haze-Kneaded Pearls） | `staff_orbs` | heal, regen | relic_w_mist_hermit / orbs |
| `relic_w_walking_glyph_weapon` | 碑文の波動（Inscription Wave） | `sword_wave` | mark, stun | relic_w_walking_glyph / wave |
| `relic_w_ash_herald_weapon` | 燼の扇（Cinder Spread） | `bow_fan` | imbue, slow_field | relic_w_ash_herald / fan |
| `relic_w_thunder_roc_weapon` | 天雷の投槍（Skybolt Javelin） | `spear_javelin` | stun, mark | relic_w_thunder_roc / comet |
| `relic_w_glacier_bearer_weapon` | 氷河の崩落（Glacier Calving） | `great_slam` | stun, slow_field | relic_w_glacier_bearer / spikes |
| `relic_w_hollow_walker_weapon` | 虚ろの行脚（Hollow Pilgrimage） | `sword_dash` | vanish, siphon | relic_w_hollow_walker / arc |
| `relic_w_gravebloom_weapon` | 散華の舞（Petalfall Dance） | `twin_fan` | zone, siphon | relic_w_gravebloom / fan |
| `relic_w_scarecrow_king_weapon` | 熊手の連突き（Hayfork Flurry） | `spear_thrusts` | imbue, knockback | relic_w_scarecrow_king / helix |
| `relic_w_reed_serpent_weapon` | 大蛇の一咬み（Serpent's Bite） | `bow_snipe` | execute, slow_field | relic_w_reed_serpent / line |
| `relic_w_sunken_captain_weapon` | 錨落としの大旋回（Anchorfall Swing） | `great_rend` | knockback, pull | relic_w_sunken_captain / crescent |
| `relic_w_tide_singer_weapon` | 潮騒の歌（Song of the Surf） | `staff_volley` | heal, slow_field | relic_w_tide_singer / fan |
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

## 遺宝の一覧（金枠）

出どころ・必要Lv・武器（武器種・属性）・防具セット（型）・アクセサリー（主能力・副能力・効果）・お守り（効果）。

<!-- RELIC TABLE BEGIN (tools/art/relics.py) -->

| 出どころ | 必要Lv | 武器 | 防具セット | アクセサリー | お守り |
|---|---|---|---|---|---|
| `b1` ダンジョンのボス・盗賊王 | 11 | 簒奪者の細剣（片手剣・闇） | 盗賊王の遺装（軽装） | 盗賊王の指輪（AGI・LUK・会心率） | 盗賊王のお守り袋（回避） |
| `b2` ダンジョンのボス・古代樹 | 18 | 千年樹の剛剣（両手剣・地） | 古代樹の遺鎧（重鎧） | 古代樹の腕輪（VIT・STR・与ダメージ） | 古代樹の守り石（瀕死の守り） |
| `b3` ダンジョンのボス・砂塵巨神 | 25 | 巨神の砂嵐弓（弓・地） | 砂塵巨神の遺鎧（重鎧） | 砂塵巨神の首飾り（STR・VIT・会心ダメージ） | 砂塵巨神の護符（反撃） |
| `b4` ダンジョンのボス・凍獄の処刑人 | 31 | 氷獄の断頭斧槍（槍・氷） | 凍獄の処刑人の遺鎧（重鎧） | 凍獄の処刑人の耳飾り（STR・VIT・会心ダメージ） | 凍獄の処刑人の守り鈴（瀕死の守り） |
| `b5` ダンジョンのボス・氷獄の女王 | 34 | 氷后の双晶刃（双剣・氷） | 氷獄の女王の遺衣（法衣） | 氷獄の女王の首飾り（INT・AGI・会心率） | 氷獄の女王の守り石（最大HP%） |
| `b6` ダンジョンのボス・竜王 | 40 | 竜王の焔牙槍（槍・炎） | 竜王の遺鎧（重鎧） | 竜王の指輪（STR・VIT・与ダメージ） | 竜王の護符（吸血） |
| `b7` ダンジョンのボス・機械神 | 47 | 機神の雷断剣（両手剣・雷） | 機械神の遺鎧（重鎧） | 機械神の腕輪（DEX・INT・攻撃速度） | 機械神の守り鈴（回避） |
| `b8` ダンジョンのボス・終焉の門番 | 52 | 門番の封鍵杖（杖・闇） | 終焉の門番の遺鎧（重鎧） | 終焉の門番の耳飾り（INT・VIT・与ダメージ） | 終焉の門番の守り石（瀕死の守り） |
| `final` ダンジョンのボス・終焉王 | 55 | 終焉王の黙示弓（弓・血） | 終焉王の遺鎧（重鎧） | 終焉王の首飾り（LUK・VIT・会心ダメージ） | 終焉王の護符（吸血） |
| `c_meadow` 災厄・呑影 | 20 | 呑影の双爪（双剣・闇） | 呑影の遺装（軽装） | 呑影の耳飾り（AGI・DEX・会心率） | 呑影のお守り袋（回避） |
| `c_moon_forest` 災厄・月蝕樹 | 25 | 月蝕樹の枝杖（杖・聖） | 月蝕樹の遺衣（法衣） | 月蝕樹の首飾り（INT・VIT・回復量） | 月蝕樹の守り鈴（撃破で回復） |
| `c_iron_mine` 災厄・坑底獣 | 30 | 坑底の破砕大剣（両手剣・地） | 坑底獣の遺鎧（重鎧） | 坑底獣の腕輪（STR・VIT・与ダメージ） | 坑底獣の守り石（反撃） |
| `c_sacred_falls` 災厄・堕ちた滝守 | 35 | 堕滝の水鏡槍（槍・氷） | 堕ちた滝守の遺装（軽装） | 堕ちた滝守の指輪（DEX・INT・会心率） | 堕ちた滝守の守り鈴（最大HP%） |
| `c_ancient_ruins` 災厄・焦遺の焔 | 45 | 焦遺焔の祭剣（片手剣・炎） | 焦遺の焔の遺衣（法衣） | 焦遺の焔の耳飾り（INT・STR・与ダメージ） | 焦遺の焔の護符（撃破で回復） |
| `c_soaring_peaks` 災厄・峰砕き | 55 | 峰砕きの貫天弓（弓・雷） | 峰砕きの遺鎧（重鎧） | 峰砕きの腕輪（DEX・STR・会心ダメージ） | 峰砕きの守り石（瀕死の守り） |
| `c_end_forest` 災厄・終焉の来訪者 | 60 | 来訪者の虚空杖（杖・闇） | 終焉の来訪者の遺衣（法衣） | 終焉の来訪者の首飾り（INT・LUK・会心ダメージ） | 終焉の来訪者の護符（吸血） |
| `c_farm` 災厄・枯れ根 | 25 | 枯れ根の双鎌（双剣・毒） | 枯れ根の遺装（軽装） | 枯れ根の指輪（STR・AGI・与ダメージ） | 枯れ根のお守り袋（反撃） |
| `c_lakeside` 災厄・湖底獣 | 30 | 湖底獣の殻断剣（片手剣・氷） | 湖底獣の遺鎧（重鎧） | 湖底獣の腕輪（VIT・STR・与ダメージ） | 湖底獣の守り石（最大HP%） |
| `c_harbour` 災厄・嵐呼び | 35 | 嵐呼びの呪笛杖（杖・雷） | 嵐呼びの遺衣（法衣） | 嵐呼びの耳飾り（INT・DEX・攻撃速度） | 嵐呼びの守り鈴（回避） |
| `l_garm` ヌシ・ガルム | 20 | ガルムの街道裂き（槍・血） | ガルムの遺装（軽装） | ガルムの耳飾り（STR・AGI・会心ダメージ） | ガルムのお守り袋（吸血） |
| `l_hook` ヌシ・赤目のフック | 20 | 赤目の狙い鉤弓（弓・闇） | 赤目のフックの遺装（軽装） | 赤目のフックの指輪（DEX・LUK・会心率） | 赤目のフックの護符（回避） |
| `l_durn` ヌシ・大猪ドルン | 20 | 大猪の牙剣（片手剣・地） | 大猪ドルンの遺鎧（重鎧） | 大猪ドルンの腕輪（STR・VIT・会心ダメージ） | 大猪ドルンの守り石（反撃） |
| `l_nebula` ヌシ・古老ネブラ | 25 | 古老の星読み弓（弓・聖） | 古老ネブラの遺衣（法衣） | 古老ネブラの首飾り（INT・LUK・回復量） | 古老ネブラの守り鈴（撃破で回復） |
| `l_belladonna` ヌシ・宵闇の魔女 | 25 | 宵闇の毒華杖（杖・毒） | 宵闇の魔女の遺衣（法衣） | 宵闇の魔女の耳飾り（INT・AGI・会心率） | 宵闇の魔女のお守り袋（回避） |
| `l_lunar` ヌシ・銀狼王 | 25 | 狼王の銀月大剣（両手剣・氷） | 銀狼王の遺装（軽装） | 銀狼王の指輪（AGI・STR・攻撃速度） | 銀狼王の守り石（回避） |
| `l_dolga` ヌシ・墓守ドルガ | 30 | 墓守の鶴嘴槍（槍・地） | 墓守ドルガの遺鎧（重鎧） | 墓守ドルガの腕輪（VIT・STR・会心ダメージ） | 墓守ドルガの守り石（瀕死の守り） |
| `l_rustbone` ヌシ・錆骨 | 30 | 錆骨の双刺（双剣・毒） | 錆骨の遺装（軽装） | 錆骨の耳飾り（DEX・AGI・会心率） | 錆骨の護符（反撃） |
| `l_vein_eater` ヌシ・鉱脈喰らい | 30 | 鉱脈喰らいの節弓（弓・地） | 鉱脈喰らいの遺装（軽装） | 鉱脈喰らいの指輪（DEX・STR・与ダメージ） | 鉱脈喰らいのお守り袋（最大HP%） |
| `l_goura` ヌシ・暴君ゴウラ | 35 | 暴君の瀑布大剣（両手剣・氷） | 暴君ゴウラの遺鎧（重鎧） | 暴君ゴウラの腕輪（STR・VIT・与ダメージ） | 暴君ゴウラの守り石（反撃） |
| `l_mistweaver` ヌシ・霧糸 | 35 | 霧糸の毒牙双刃（双剣・毒） | 霧糸の遺装（軽装） | 霧糸の首飾り（AGI・DEX・会心率） | 霧糸のお守り袋（回避） |
| `l_ord` ヌシ・大亀オルド | 35 | 大亀の甲羅杖（杖・地） | 大亀オルドの遺鎧（重鎧） | 大亀オルドの首飾り（VIT・INT・回復量） | 大亀オルドの守り石（瀕死の守り） |
| `l_valka` ヌシ・狂戦士ヴァルカ | 45 | 狂戦士の血風大剣（両手剣・血） | 狂戦士ヴァルカの遺鎧（重鎧） | 狂戦士ヴァルカの指輪（STR・VIT・会心ダメージ） | 狂戦士ヴァルカの護符（吸血） |
| `l_scorch_eye` ヌシ・灼眼 | 45 | 灼眼の焔瞳杖（杖・炎） | 灼眼の遺衣（法衣） | 灼眼の耳飾り（INT・DEX・与ダメージ） | 灼眼の守り鈴（撃破で回復） |
| `l_agnis` ヌシ・神像アグニス | 45 | 神像の聖槍（槍・聖） | 神像アグニスの遺鎧（重鎧） | 神像アグニスの腕輪（STR・VIT・攻撃速度） | 神像アグニスの守り石（最大HP%） |
| `l_alvin` ヌシ・聖騎士アルヴィン | 55 | 聖騎士の天誓剣（片手剣・聖） | 聖騎士アルヴィンの遺鎧（重鎧） | 聖騎士アルヴィンの指輪（STR・INT・会心率） | 聖騎士アルヴィンの守り鈴（瀕死の守り） |
| `l_peak_sniper` ヌシ・峰の狙撃手 | 55 | 狙撃手の天穿弓（弓・雷） | 峰の狙撃手の遺装（軽装） | 峰の狙撃手の耳飾り（DEX・LUK・会心ダメージ） | 峰の狙撃手の護符（回避） |
| `l_zephyr` ヌシ・雷翼ゼファー | 55 | 雷翼の疾風双刃（双剣・雷） | 雷翼ゼファーの遺装（軽装） | 雷翼ゼファーの腕輪（AGI・DEX・攻撃速度） | 雷翼ゼファーの守り鈴（回避） |
| `l_xenos` ヌシ・先触れゼノス | 55 | 破滅の先触れの終杖（杖・闇） | 先触れゼノスの遺衣（法衣） | 先触れゼノスの首飾り（INT・LUK・与ダメージ） | 先触れゼノスの護符（瀕死の守り） |
| `l_black_knight` ヌシ・黒騎士 | 55 | 黒騎士の終剣（片手剣・闇） | 黒騎士の遺鎧（重鎧） | 黒騎士の指輪（STR・VIT・会心ダメージ） | 黒騎士の守り石（反撃） |
| `l_nihil` ヌシ・虚無竜ニヒル | 55 | 虚無竜の喰らい槍（槍・闇） | 虚無竜ニヒルの遺装（軽装） | 虚無竜ニヒルの耳飾り（AGI・STR・会心率） | 虚無竜ニヒルのお守り袋（吸血） |
| `l_brutus` ヌシ・暴れ牛ブルータス | 25 | 暴れ牛の角断剣（両手剣・地） | 暴れ牛ブルータスの遺鎧（重鎧） | 暴れ牛ブルータスの腕輪（STR・VIT・与ダメージ） | 暴れ牛ブルータスの守り石（反撃） |
| `l_harvest_boss` ヌシ・収穫泥棒 | 25 | 収穫泥棒の早取り弓（弓・毒） | 収穫泥棒の遺装（軽装） | 収穫泥棒の指輪（DEX・LUK・会心率） | 収穫泥棒のお守り袋（回避） |
| `l_strom` ヌシ・案山子王ストローム | 25 | 案山子王の見張り杖（杖・地） | 案山子王ストロームの遺衣（法衣） | 案山子王ストロームの首飾り（INT・VIT・回復量） | 案山子王ストロームの護符（撃破で回復） |
| `l_melusine` ヌシ・湖の魔女メリュー | 30 | 湖の魔女の水鏡剣（片手剣・氷） | 湖の魔女メリューの遺衣（法衣） | 湖の魔女メリューの耳飾り（INT・DEX・会心率） | 湖の魔女メリューの守り鈴（最大HP%） |
| `l_drowned_fisher` ヌシ・溺れ漁師 | 30 | 溺れ漁師の銛（槍・氷） | 溺れ漁師の遺装（軽装） | 溺れ漁師の腕輪（DEX・STR・与ダメージ） | 溺れ漁師のお守り袋（吸血） |
| `l_numel` ヌシ・湖底の主ヌメル | 30 | 湖底の主のぬめり杖（杖・毒） | 湖底の主ヌメルの遺衣（法衣） | 湖底の主ヌメルの首飾り（INT・VIT・与ダメージ） | 湖底の主ヌメルの守り石（撃破で回復） |
| `l_barbas` ヌシ・隻眼の船長 | 35 | 隻眼船長の海賊刀（片手剣・血） | 隻眼の船長の遺装（軽装） | 隻眼の船長の耳飾り（STR・LUK・会心ダメージ） | 隻眼の船長の護符（吸血） |
| `l_dock_brawler` ヌシ・港の喧嘩屋 | 35 | 喧嘩屋の拳刃（双剣・地） | 港の喧嘩屋の遺鎧（重鎧） | 港の喧嘩屋の腕輪（STR・AGI・攻撃速度） | 港の喧嘩屋の守り石（瀕死の守り） |
| `l_ankara` ヌシ・錨喰らいの大ガニ | 35 | 錨喰らいの鋏大剣（両手剣・氷） | 錨喰らいの大ガニの遺鎧（重鎧） | 錨喰らいの大ガニの指輪（VIT・STR・会心ダメージ） | 錨喰らいの大ガニの守り石（反撃） |
| `w_bramble_knight` 放浪する大物・野茨騎士 | 17 | 野茨騎士の茨槍（槍・毒） | 野茨騎士の遺鎧（重鎧） | 野茨騎士の指輪（STR・VIT・与ダメージ） | 野茨騎士の守り石（反撃） |
| `w_moonmoth_witch` 放浪する大物・月光蛾 | 23 | 月光蛾の鱗粉弓（弓・聖） | 月光蛾の遺衣（法衣） | 月光蛾の耳飾り（DEX・INT・会心率） | 月光蛾の守り鈴（回避） |
| `w_slag_titan` 放浪する大物・鉱滓巨人 | 28 | 鉱滓巨人の熔鉄剣（両手剣・炎） | 鉱滓巨人の遺鎧（重鎧） | 鉱滓巨人の腕輪（STR・VIT・会心ダメージ） | 鉱滓巨人の守り石（瀕死の守り） |
| `w_cascade_wyvern` 放浪する大物・瀑流飛竜 | 33 | 瀑流飛竜の翼槍（槍・氷） | 瀑流飛竜の遺装（軽装） | 瀑流飛竜の耳飾り（AGI・STR・移動速度） | 瀑流飛竜の守り鈴（回避） |
| `w_mist_hermit` 放浪する大物・霧仙 | 35 | 霧仙の霞杖（杖・聖） | 霧仙の遺衣（法衣） | 霧仙の首飾り（INT・VIT・回復量） | 霧仙の護符（撃破で回復） |
| `w_walking_glyph` 放浪する大物・歩く碑文 | 41 | 碑文の刻文剣（片手剣・地） | 歩く碑文の遺鎧（重鎧） | 歩く碑文の首飾り（INT・STR・会心率） | 歩く碑文の守り石（瀕死の守り） |
| `w_ash_herald` 放浪する大物・灰の先触れ | 43 | 灰の先触れの燼弓（弓・炎） | 灰の先触れの遺装（軽装） | 灰の先触れの指輪（DEX・INT・与ダメージ） | 灰の先触れの護符（反撃） |
| `w_thunder_roc` 放浪する大物・雷鳴の巨鳥 | 50 | 雷鳥の天雷槍（槍・雷） | 雷鳴の巨鳥の遺装（軽装） | 雷鳴の巨鳥の腕輪（DEX・AGI・攻撃速度） | 雷鳴の巨鳥の守り鈴（回避） |
| `w_glacier_bearer` 放浪する大物・氷河負い | 53 | 氷河負いの氷塊剣（両手剣・氷） | 氷河負いの遺鎧（重鎧） | 氷河負いの指輪（VIT・STR・与ダメージ） | 氷河負いの守り石（最大HP%） |
| `w_hollow_walker` 放浪する大物・虚ろな巡礼者 | 57 | 虚ろ巡礼の行脚剣（片手剣・闇） | 虚ろな巡礼者の遺衣（法衣） | 虚ろな巡礼者の首飾り（AGI・INT・会心率） | 虚ろな巡礼者の守り鈴（撃破で回復） |
| `w_gravebloom` 放浪する大物・墓花 | 59 | 墓花の散華双刃（双剣・毒） | 墓花の遺装（軽装） | 墓花の耳飾り（AGI・INT・与ダメージ） | 墓花のお守り袋（吸血） |
| `w_scarecrow_king` 放浪する大物・渡りの案山子王 | 22 | 案山子王の干し草熊手（槍・炎） | 渡りの案山子王の遺装（軽装） | 渡りの案山子王の指輪（STR・LUK・会心ダメージ） | 渡りの案山子王の護符（反撃） |
| `w_reed_serpent` 放浪する大物・葦原の大蛇 | 27 | 葦原大蛇の毒牙弓（弓・毒） | 葦原の大蛇の遺装（軽装） | 葦原の大蛇の耳飾り（DEX・AGI・会心率） | 葦原の大蛇のお守り袋（回避） |
| `w_sunken_captain` 放浪する大物・沈まぬ船長 | 32 | 沈まぬ船長の錨大剣（両手剣・雷） | 沈まぬ船長の遺鎧（重鎧） | 沈まぬ船長の首飾り（STR・LUK・会心ダメージ） | 沈まぬ船長の守り石（瀕死の守り） |
| `w_tide_singer` 放浪する大物・潮呼びの巫女 | 34 | 潮呼びの潮騒杖（杖・聖） | 潮呼びの巫女の遺衣（法衣） | 潮呼びの巫女の首飾り（INT・VIT・回復量） | 潮呼びの巫女の守り鈴（撃破で回復） |

<!-- RELIC TABLE END -->
