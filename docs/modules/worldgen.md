# ワールド生成（`world.gen` / `world.forge` パッケージ）

異世界アストリアのディメンション `eternalrealm:astoria`。地形はフィールドマップ専用の生成器 `eternalrealm:fields`（海面 y=63、高さ -64〜319）で、
**地形・バイオームの配置・地表・装飾・建造物**をすべてこのモジュールが決める。

| パッケージ | 種別 | 内容 |
|---|---|---|
| `world.gen` | 純粋（Minecraft非依存） | エリアの輪（`AstoriaAreas`）、バイオーム配置（`AstoriaLayout`, `AstoriaBiome`）、ノイズ（`GenNoise`）、ダンジョン入口の座標（`LandmarkSites`）、王都のNPC位置（`HubSpot`）、登録名（`WorldgenIds`） |
| `world.forge` | Forge | バイオームソース、Feature 群（`world.forge.feature`）、王都・入口の建築、移動API、セーブデータ |
| `data/eternalrealm/` | データパック | `dimension/astoria.json`, `dimension_type/astoria.json`, `worldgen/biome`（24）, `worldgen/configured_feature`（49）, `worldgen/placed_feature`（74） |

データパックのJSONは `python3 tools/gen-worldgen-data.py` で生成する（手で編集しない）。生成器は全バイオームの Feature を
**生成ステップごとに1つの共通順序**で並べる（順序が食い違うと「Feature order cycle」でサーバーが起動しない）。
`WorldgenDataTest` が書き出し結果を検査する（順序の循環、参照切れ、バニラ敵対Mobの湧き、未登録の Feature 型・バリアント、翻訳キー）。

## 1. フィールドマップ（`world.field`）

アストリアは「アルセリア王国 周辺フィールドマップ」の通り、北中央の王都を囲む **10のフィールド** でできている（2.1で同心円の7地域から変更。
`FieldLayout.VERSION` = 2）。地形は `FieldChunkGenerator`（`eternalrealm:fields`）が純粋モジュール `FieldTerrain` の高さで列ごとに埋める
（ノイズルーターも洞窟もなし。どのサーバーでも同じ地図）。範囲は x −1700〜1700、z −1500〜1300（約3.4×2.8km）。北と西は越えられない山脈、
南と東は外海。範囲外へ出ると霧に押し戻される（`WorldService`）。古い版のワールドは起動時にアストリアのフォルダだけ作り直す
（`FieldMapMigration`。キャラクターや進行は保持、旧地形にいたプレイヤーは王都へ）。

| ID | slug（不変） | フィールド | 推奨Lv | 位置 | 地形 | バイオーム | ダンジョン |
|---|---|---|---|---|---|---|---|
| 1 | arcelia | はじまりの草原 | 5〜15 | 王都の南〜南西 | なだらかな丘 | arcelia_plains, arcelia_flower_meadow（王都とその周囲は royal_capital） | d1 (-640, 930) |
| 2 | elder_forest | 月影の森 | 10〜20 | 西 | 起伏の森、月影の池 (-780, 200) | elder_forest, elder_old_growth, elder_mushroom_glade | d2 (-1010, -60) |
| 3 | sandria_desert | 鉄鉱の鉱山 | 15〜25 | 西の果て | 尾根の岩山、段々の採石場 (-1330, 760)、鉱夫の野営地 | iron_hills, iron_quarry, dragonia_basalt_fields | d3 (-1330, 790) |
| 4 | nordia_snowfield | 聖なる滝 | 20〜30 | 北東 | 台地(y132)の垂直な崖から落ちる大瀑布 (612, -410)、滝壺、川 | celestia_meadow, celestia_cherry_grove | d4 (651, -362)、d5 台地上 (830, -540) |
| 5 | dragonia_volcano | 古代遺跡 | 30〜40 | 東 | 頂が平らな丘 (1300, -200) | ruin_hills, sandria_mesa | d6 (1300, -170) |
| 6 | celestia | 天翔の峰 | 40〜 | 北（王都の背後） | 北へ行くほど高い山脈（〜300）、雪線158前後 | alpine_meadow, nordia_taiga/snowfield/ice_spikes/crystal_hollow, celestia_white_woods | d7 頂上付近 (200, -1230) |
| 7 | tower_of_the_end | 終焉の森 | 50〜 | 北西 | 起伏の森と塔の基壇 | abyss_dead_forest, abyss_wastes, abyss_void_rift, abyss_spire（塔の周囲120） | d8 終焉の塔 (-1150, -980) |
| 8 | fertile_farm | 豊穣の農場 | 10〜20 | 東 | 平坦な畑、川 | arcelia_farmlands, golden_fields | — |
| 9 | lakeside | 湖畔のほとり | 15〜25 | 南東 | 湖（中心 360, 880、小島つき） | lakeside, astoria_lake | — |
| 10 | wind_harbour | 風の港 | 20〜30 | 南東の海岸 | 港町の段丘、海 | harbour_coast, sandria_dunes（海岸砂丘）, astoria_sea | — |

- 領域は重み付きボロノイ（フィールドごとに複数の種点）をノイズで歪めた座標で求めるので境界は自然に曲がる。地形は隣のフィールドと約100ブロックかけて混ざる。
- 水: 川4本（滝壺→王都の東→農場→湖、湖→港、月影の池→草原→湖、台地の上の流れ→滝）、湖、海。水面は海面63（池69、台地の流れ131）。
- 道: 王都の南門・西門・東門から各フィールドへ（`FieldLayout.roads()`、土の道）。転移碑の位置 `FieldLayout.waystones()`（各フィールドと王都の南門前）。
- 解放: 物語のフィールドは従来どおり（b1→月影の森, b2→鉱山, b3→滝, b5→遺跡, b6→峰, b7→終焉の森）。脇のフィールドは同じレベル帯の物語フィールドと同時に開く（b1→農場, b2→湖畔, b3→港）。
- 使われなくなったバイオーム（sandria_oasis, dragonia_ashlands, dragonia_caldera）は登録だけ残る（field 0）。

## 2. 地表と装飾（Feature）

| 登録名 `eternalrealm:` | バリアント | 内容 |
|---|---|---|
| `area_surface` | — | 各チャンクの最初の装飾。列ごとにバイオームで地表を塗り替える（砂/砂岩、テラコッタ縞、雪/氷、玄武岩/ブラックストーン/マグマ、方解石、深層岩/エンドストーン…）。砂丘の起伏、雪の吹きだまり、玄武岩の柱、カルデラの溶岩の川、火山の地表水→溶岩 |
| `rock_spire` | stone, sandstone, terracotta, ice, basalt, obsidian, calcite, dark | 傾いた尖塔・岩柱 |
| `boulder` | mossy, sandstone, snowy, basalt, calcite, deepslate | 半分埋まった岩（苔・雪付き） |
| `crystal_cluster` | amethyst, ice, magma, quartz, void | 結晶群（先端にアメジスト/光源） |
| `fallen_pillar` | stone, sandstone, quartz, blackstone, log | 倒れた柱と折れた基部／苔とキノコの倒木 |
| `ruin` | mossy, sandstone, frost, basalt, quartz, abyss | 崩れた小さな建物（アーチ、瓦礫、テーマ別の小物） |
| `astoria_tree` | giant, palm, white, cherry, charred, ashen, bleached | 手作りの木（巨木・ヤシ・白い木・桜・枯れ木3種） |
| `pool` | oasis, lava, frozen | 縁の最低点に水面を合わせた池（あふれない） |
| `magma_vent` | — | 噴気孔（溶岩の目＋のろし用焚き火で煙） |
| `floating_island` | meadow, cherry, crystal | 地表から20〜50上の浮島（草・花・木・結晶、45%で滝） |
| `void_crack` | — | 終焉の地割れ |
| `farm_plot` | — | 柵付きの畑 |
| `mushroom_ring` | — | 妖精の輪と小さな毒キノコ |
| `dark_tower` | — | 終焉の塔の外殻（エリア7の各チャンクで、そのチャンク内の部分だけを描く） |

- **書き込み範囲**: 全 Feature は配置チャンク±12ブロック以内だけに書く（`FeatureKit.Bounds`。ワールド生成領域は±1チャンクまで許可）。`area_surface` と `dark_tower` は自チャンク内のみ。
- 配置は `WORLD_SURFACE_WG`、Feature 内部の高さ参照は `OCEAN_FLOOR_WG` / `WORLD_SURFACE_WG`（生成中に有効なハイトマップ）。
- 生成ステップ: RAW_GENERATION=地表 → LAKES=池 → LOCAL_MODIFICATIONS=地割れ・尖塔・岩・結晶・噴気孔 → SURFACE_STRUCTURES=塔・遺跡・柱・畑・浮島 → … → VEGETAL_DECORATION=木・花・草 → TOP_LAYER=凍結。

## 3. フィールドの手作り建造物（Field set pieces）

風車・農家・港町・神殿跡・峠の関所などの**手作りの建造物（セットピース）**は、Python のビルダー（`tools/fields/`）が
本物のフィールド地形（純粋モジュール `FieldTerrain` を `tools/fields/TerrainProbe.java` で実行）の上に組み立てて書き出し、
`FieldChunkGenerator` がチャンク生成時にそのまま刻む。出力は決定的（同じ入力から同じバイト列）。

```
python3 tools/fields/build.py                        # 全ピースを組み立て → 書き出し → 検査（約10秒、地形はキャッシュ）
python3 tools/fields/build.py --previews             # + tools/fields/previews/ にプレビュー（遠近図・平面図・一覧・全体地図、約20分）
python3 tools/fields/build.py --only peaks_pass --previews   # 1ピースだけ（書き出さない。--export で書き出す）
```

- ピースは `tools/fields/pieces/` のモジュール（`build() -> list[Piece]`、ファイル名順）。部品は `kit.py`（整地・舗装・灯り・看板・旗・植物）、
  `build_kit.py`（家・納屋・風車・塔）、接続形状は `connect.py`（柵・板ガラス・塀・階段の形をバニラと同じ規則で事前計算）。
- **書き出し** `data/eternalrealm/fieldpieces/`: `manifest.json`（ピースごとのパレット、ブロックエンティティの SNBT、エンティティ、
  流体ティック、検査用プローブ、地形サンプル、転移碑）と `pieces/<id>.bin`（gzip、形式 ERP1: チャンク単位のタイル、列ごとのランレングス、
  パレット 0 = 地形のまま / 1 = 空気、列ごとの基礎ブロック）。87ピースで約 0.41 MiB（上限 8 MiB）。
- **検査** `tools/fields/check.py`（ビルドの最後に必ず実行）: 全デコード、パレットの正規形、BE の載る先、流体ティック、エンティティ、
  プローブ、王都から24以上（南門前の広場だけ8）、ダンジョンの門から20（終焉の塔30）と門の前（正面48×幅11）に背の高いものを置かない、
  フィールド名テストのチャンクに置かない、転移碑（下半分が `FieldLayout.waystones()` から3以内、上下2ブロック・向き・周囲2マスが歩ける・床）、
  ピース同士の食い違い、サイズ。
- **重なり**: 2つのピースが同じ列に書くと、順位の高い方がその列を取り、低い方は列ごと手を引く（転移碑の祠 > 橋・境界の門 > 道標 >
  建造物 > 道端の灯り・柵 > 花畑。同順位はビルド順）。柵の列は橋や祠の手前で止まる。

### Java 側

| クラス | 内容 |
|---|---|
| `world.field.FieldPieceData`（純粋） | 書き出しの読み込み（ERP1 のデコード、チャンク索引）、`covers(x, z)`、`near(x, z, r)`、`lowestBlock`、`ownsGround`、`waystoneNear` |
| `world.forge.FieldPieces` | サーバー起動時にデータパックから読み込む（不正なら「ピースなし」で続行しログ）。未知のブロックIDは1回だけ警告して飛ばす |
| `FieldChunkGenerator.fillFromNoise` → `FieldPieces.stampTerrain` | 地形の直後にブロック、地形が低い所の基礎、BE データ（id と座標付き）、流体ティック、エンティティ（トロッコ）、ワールド生成用ハイトマップ |
| `FieldChunkGenerator.applyBiomeDecoration` → `FieldPieces.restampAfterDecoration` | 装飾が上書きしたピースのブロックを戻す（BE を持つブロックは除く） |
| `AreaSurfaceFeature` | ピースが地面そのものを作る列（地形の高さ以下に書く列）は塗らない。地面に立つだけの列（柵・街灯・看板）は周りと同じに塗る |
| `FeatureKit.keepOut` | 岩・木・畑などの Feature はピースから8（浮島は14）以内に置かない |
| `CapitalBuilder` | 王都の周囲のなじませでピースの列に触れない（南門前広場） |

GameTest `FieldPieceGameTests`: 書き出しの読み込み、11の転移碑、原点に近い6ピースのチャンクを生成してプローブのブロックが
書き出しどおりか。純粋テスト `FieldPieceDataTest`: 形式（小さな合成ピース、壊れたデータの拒否）、実際の書き出しの全デコード、
転移碑と祠、**地形のずれ検知**（書き出しに記録した地形サンプルが現在の `FieldTerrain` と一致すること。地形を変えたら
`build.py` で作り直す）、王都・門からの距離。

### 転移碑の祠（11）

| 転移碑 | 位置 (x, y, z) | 向き | 祠 |
|---|---|---|---|
| capital | 0, 71, 196 | 北（南門） | 南門前広場: 敷石と金の象嵌の輪、二灯の街灯4基、花壇、糸杉、生け垣とツツジの鉢、金の頂華の柱、王国旗2本、南街道・湖畔街道の道標 |
| meadow | -360, 75, 572 | 北 | 立石の環と苔の舗装、道からの灯り |
| moon_forest | -700, 76, 86 | 北 | 苔の石段、魂の灯籠、鳥居 |
| iron_mine | -1250, 88, 618 | 北 | 木組みの屋根と坑木 |
| sacred_falls | 530, 67, -290 | 東 | 白い石の壇（川に杭を打った張り出し）、川を渡る橋 |
| ancient_ruins | 1230, 117, -132 | 西 | 砂岩の壇と折れた柱 |
| soaring_peaks | 300, 143, -812 | 南 | 段々の壇と石段 |
| end_forest | -1000, 74, -604 | 東 | 黒曜石とプルプァ、アメジスト |
| farm | 752, 70, 248 | 北 | 柵で囲んだ丸石の広場 |
| lakeside | 282, 68, 676 | 北 | 睡蓮の堀に囲まれた壇 |
| harbour | 1100, 69, 876 | 北 | 板張りの桟橋、鎖と錨、鐘、港の旗 |

### フィールドごとのピース（中心 x, z）

| フィールド | ピース |
|---|---|
| 道（全域） | 石橋: 東街道×滝の川 (378, 46)・南街道×草原の小川 (-314, 536)、木橋: 湖畔街道×滝の川 (360, 680)、月影の池の木道 (-809, 171)。**境界の門** 10基（街道がフィールドの境を越える所: 王都側の西 (-214, 4)・東 (213, 4)、1→9 (162, 421)、9→10 (847, 809)、2→3 (-1083, 452)、2→7 (-884, -387)、8→5 (1025, 22)、8→10 (1030, 666)、8→4 (475, -96)、4→6 (349, -690)。王国の石の柱と屋根飾り、両側のフィールド色の旗・長旗、両方向を向く看板「ここより／フィールド名／推奨Lv」）。道標3（東街道と滝道 (426, 50)、月影の森の辻 (-692, 62)、農場の辻 (732, 238)）。街灯: 王都から約300までの4街道（鉄の灯柱）、月影の森の西街道・北の道（黒樫の柱に魂の灯り） |
| 1 はじまりの草原 | 風車の丘と粉屋 (-431, 690)、古の環状列石 (-270, 604)、羊飼いの小屋と囲い (-62, 425)、街道の井戸 (-186, 475)、打ち捨てられた荷車 (-556, 792)、花畑5、街道の柵 |
| 2 月影の森 | 月見の祠（池に杭で建つ東屋、橋と鳥居） (-750, 200)、狩人の小屋（燻製小屋・干し台・弓の的） (-748, 13)、妖精の輪と光る大毒キノコ (-670, 130)、小川の谷に架かる倒れた巨木（光る実の蔓） (-687, 270) |
| 3 鉄鉱の鉱山 | 鉱夫の野営地（家・煙の上がる鍛冶場・櫓・トロッコの線路・鉱石の山・坑口） (-1261, 632)、採石場（段ごとの足場と梯子、クレーン、線路） (-1332, 762) |
| 4 聖なる滝 | 滝脇の白い大階段の塔（崖の上まで二人幅の螺旋階段、台地への橋、展望台） (590, -423)、滝口の祈りの壇 (604, -428)、川沿いの白いアーチの参道と観瀑橋 (579, -331) |
| 5 古代遺跡 | 大神殿（基壇・大階段・列柱、正面の柱廊と崩れた破風、内陣、倒れた像） (1301, -240)、洞窟門前の広場と参道の列柱 (1279, -177)、道の凱旋門跡 (1194, -106)、円堂跡 (1300, -113) |
| 6 天翔の峰 | 段の付いた山道（転移碑の先から d7 の手前まで、上りごとに階段、切り通しと擁壁、ケルン、灯り） (256, -916)〜(212, -1160)、**天翔の関**（峠下の踊り場、屋根付きの2塔と門楼、上げた落とし格子、王国旗） (212, -1105)、山小屋 (283, -878)、物見櫓と谷を渡る吊り橋と展望台 (228, -1176) |
| 7 終焉の森 | 崩れた礼拝堂（鐘楼・後陣の祭壇・墓地） (-1014, -699)、枯れた巨木の林 (-966, -535)、水晶の露頭2 (-965, -656) (-1073, -879)、塔へ向かう光る裂け目3（塔の44手前で止まる） |
| 8 豊穣の農場 | 北の農家 (690, 174)、東の農家 (932, 469)、果樹園 (941, 532)、水路のある西の畑と2基目の風車 (666, 345)、東の畑 (921, 409)、放牧地 (636, 131) |
| 9 湖畔のほとり | 北岸の漁師の村 (320, 705)、小島への長い橋 (318, 779)、小島の四阿 (320, 899) |
| 10 風の港 | 岸壁・運河・石橋・桟橋・クレーン (1187, 939)、港町（倉庫・市場） (1180, 889)、漁師の家 (1139, 989)、灯台と防波堤 (1289, 869)、主桟橋の帆船 (1232, 960) |

看板の文字は翻訳キー（`area.eternalrealm.<slug>`、`sign.eternalrealm.*`、文言は `docs/i18n/fieldpieces.json`）。旗は `eternalrealm:flag`（kingdom / pennant / harbour 柄）。

道の変更（`FieldLayout.roads()`）: 滝道は滝の川の東岸を上って祠の橋で川を渡り、転移碑の岸 (524, -293) で終わる（旧: (500,-200)→(540,-300)）。
峰道はそこから始まる。高さは変わらず道の土の色だけが変わるので `FieldLayout.VERSION` は据え置き。

**既存のワールド**: すでに生成されたチャンクにはピースが入らない（新しく生成するチャンクだけ）。

## 4. 王都 アルセリア王都（`HubBuilder` / `HubLayout`）

原点を中心に一度だけ建築（`AstoriaWorldState` に built フラグと床の高さを保存）。床の高さ＝王都範囲のノイズ地形の中央値（海面+1〜110）。
建築前でも `HubLayout.of(level)` で同じ座標が得られる。所要時間は数秒（CIで約5秒、範囲のチャンク生成を含む）なので、
統合側はサーバー起動時（`ServerStartedEvent`）に `HubBuilder.ensureBuilt` を呼んでおくとプレイ中の停止を避けられる。

- ±46 を平らに整地し ±58 までなだらかに元の地形へ。範囲内の木は撤去。
- 城壁: ±38〜40、高さ8＋胸壁と歩廊。東西南北に門（アーチ・落とし格子）と門塔2基、四隅に青い尖り屋根の塔。門外に舗装の取り付け道。
- 十字の大通り（幅5、石レンガ舗装）と中央の円形広場（半径12）。噴水（水盤・石英の柱・流れ落ちる水・シーランタン）。
- 北: 王城（大広間・赤絨毯・石英の柱・玉座・シャンデリア・旗・裏門、四隅の塔と中央の天守、青い屋根）。前庭に花壇と木。
- 建物（内装つき、切妻屋根、窓、扉、煙突）: ギルド会館、栄誉の殿堂（石英）、職業ギルド、オークションハウス、道具屋、鍛冶屋（炉・溶岩の大釜・金床・煙突）、民家6棟。
- 市場の屋台8（縞の日よけ・商品）、闘技場（砂の楕円・壁・訓練用の的とかかし）、掲示板2（クエスト・ランキング）、郵便ポスト、女神像（蝋燭）、街灯、ベンチ、花壇、庭木。

NPC・スポーン位置（足元の座標。y = 床 + 1。yaw は Minecraft 準拠：0=南, 90=西, 180=北, 270=東）:

| `HubSpot` | x, z | yaw | 場所 |
|---|---|---|---|
| `QUEST_BOARD` | 10, -9 | 45 | 広場北東のクエスト掲示板の前 |
| `RANKING_BOARD` | -10, -9 | 315 | 広場北西のランキング掲示板の前 |
| `MAILBOX` | 10, 9 | 135 | 広場南東の郵便ポストの横 |
| `RESPAWN` | -9, 9 | 225 | 広場南西の女神像の前 |
| `SPAWN` | 0, 7 | 180 | 噴水の南（城を向く） |
| `ITEM_SHOP` | -29, 12 | 180 | 道具屋のカウンターの内側 |
| `BLACKSMITH` | 28, 11 | 180 | 鍛冶屋の金床の後ろ |
| `JOB_MASTER` | -30, -16 | 0 | 職業ギルドの書見台の後ろ |
| `AUCTION_HOUSE` | 30, -16 | 0 | オークションのカウンターの内側 |
| `GUILD_REGISTRAR` | -27, -32 | 0 | ギルド会館の机の内側 |
| `HALL_OF_FAME` | 27, -32 | 0 | 栄誉の殿堂の奥 |
| `GATEKEEPER` | 4, 35 | 90 | 南門（正門）の内側 |
| `ARENA_MASTER` | 24, 18 | 90 | 闘技場の入口の前 |

## 5. ダンジョン入口（`LandmarkBuilder` / `Landmarks`）

プレイヤーが中心から **80ブロック以内**に来て（王都は160以内）、必要なチャンクがすでに読み込まれていれば一度だけ建築する（毎秒判定。
サーバースレッドで地形生成をしないため）。40ブロック以内まで近づいた場合は未読み込みのチャンクも同期生成して建築する。
門は王都の方角を向く。床の高さは周囲のノイズ地形の中央値（d8は塔と同じ式）で、建築前から `Landmarks.*` で取得できる。
各入口: テーマ別の門（ポータル面・アーチ・装飾）、**入口プレート**（entrance）、**NPC位置**（柱ブロックの床と灯り）、**ウェイポイント**（5x5の台座、隣にロードストーンの道標）。

| ID | フィールド | 中心 x, z | テーマ |
|---|---|---|---|
| d1 失われた地下都市 | 1 はじまりの草原 | -640, 930 | 苔むした石のアーチと地下への階段、鉄格子の封印 |
| d2 古代樹の迷宮 | 2 月影の森 | -1010, -60 | 2本の古木の幹と根の門、葉の天蓋、蔦 |
| d3 砂塵の神殿 | 3 鉄鉱の鉱山 | -1330, 790 | 採石場で掘り当てられた砂岩の神殿正面 |
| d4 凍てつく地下牢 | 4 聖なる滝 | 651, -362 | 滝の脇の崖に開いた鉄格子の牢門 |
| d5 氷結の城 | 4 聖なる滝 | 830, -540 | 滝の上の台地にそびえる氷の城門 |
| d6 灼熱の火山洞窟 | 5 古代遺跡 | 1300, -170 | 遺跡の丘の下へ続く洞口、溶岩の滝 |
| d7 天空の古代遺跡 | 6 天翔の峰 | 200, -1230 | 頂近くの石英のアーチと浮遊する石 |
| d8 終焉の塔 | 7 終焉の森 | -1150, -980 | 塔の南壁の大門 |

門は王都の方角を向き、entrance・npc・waypoint は門の前に並ぶ（`LandmarkSites`）。

終焉の塔: 八角形の基壇（半径22）、本体（半径13→上部7、高さ約200、黒石レンガ・黒曜石の稜・紫の窓・内部の床）、尖塔（20）。
ワールド生成でチャンクごとに描かれるため遠くから見え、入口建築時には門だけを切り開く（基壇の内側は整地しない）。

## 6. API

| クラス | メソッド | 説明 |
|---|---|---|
| `AstoriaTravel` | `sendToHub(ServerPlayer)` | 王都のスポーン位置へテレポート（未建築なら建築、チャンクは必要に応じ生成）。ディメンションが無ければ false |
| | `sendToWaypoint(ServerPlayer, String dungeonId)` | 入口のウェイポイントへ（未建築なら建築） |
| | `teleport(ServerPlayer, ServerLevel, BlockPos, float yaw)` | アストリア内の任意の足元位置へ |
| | `isInAstoria(Entity)` / `areaAt(double x, double z)` / `astoria(MinecraftServer)` | 判定・フィールド(1〜10)・レベル取得 |
| `HubBuilder` | `ensureBuilt(ServerLevel)` → `HubLayout` / `isBuilt(ServerLevel)` | 王都の建築（1回だけ） |
| `HubLayout` | `of(ServerLevel)`, `position(HubSpot)`, `yaw(HubSpot)`, `positions()`, `spawnPoint()`, `respawnPoint()`, `questBoard()` ほか各施設, `groundY()` | NPC・スポーン位置 |
| `Landmarks` | `entrance(id)`, `npc(id)`, `waypoint(id)`（各 `ServerLevel` 版あり）, `gateFacingYaw(id)`, `npcYaw(id)`, `area(id)`, `dungeonIds()` | 入口の座標（建築前も有効） |
| `LandmarkBuilder` | `ensureBuilt(ServerLevel, id)`, `isBuilt(ServerLevel, id)`, `onServerTick`（EternalRealm で登録済み） | 入口の建築 |
| `AstoriaDimension` | `LEVEL`, `biomeKey(AstoriaBiome)`, `biome(Holder<Biome>)` | レジストリキー |
| `AstoriaAreas`（純粋） | `areaAt`, `areaAtDistance`, `innerRadius`, `outerRadius`, `titleKey`, `subtitleKey` | エリアの輪（キーは `area.eternalrealm.<slug>` / `.desc`、文言は docs/i18n/world.json） |

すべてサーバースレッド専用。ログイン・リスポーン時の転送は統合側で `AstoriaTravel.sendToHub` / `HubLayout.respawnPoint()` を使って配線する。

## 7. テスト

- 純粋: `AstoriaLayoutTest`（輪の境界、world モジュールとの一致、境界のゆらぎ幅、全サブバイオームの出現、入口が各エリア内で王都を向く、HubSpot の重複なし）、
  `WorldgenDataTest`（上記のデータ検査、翻訳キー）。
- GameTest `WorldGameTests`: ディメンションとバイオームソースの存在、サンプル距離でのバイオーム→エリア、各エリアのチャンク生成と地表テーマ、
  王都の建築（全 HubSpot が立てる・噴水・城壁・2回目は再建築しない）、全入口の建築（entrance/npc/waypoint が立てる・建築前後で座標不変）。

翻訳キー（ja/en）: `docs/i18n/worldgen.json`（バイオーム名 `biome.eternalrealm.<id>`、`dimension.eternalrealm.astoria`、`hub.eternalrealm.<spot>`、`landmark.eternalrealm.<id>`）。

## 8. 既知の制限

- 地形はバニラのオーバーワールドノイズ。王都や入口が海・山に当たると、土台を埋め立て／切り崩して整地する（周囲は8〜12ブロックでなだらかに戻す）。
- 建物・看板の文字（Sign）は無い。掲示板の「紙」は旗で表現。NPC は統合側が配置する。
- 溶岩の川・池は流れ出さない位置にだけ置くが、プレイヤーが縁を壊すと流れる。浮島の滝は地面に落ちて広がる（通常の水）。
- 宝箱・樽は装飾のみ（中身なし）。
