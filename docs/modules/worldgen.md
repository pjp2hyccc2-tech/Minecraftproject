# ワールド生成（`world.gen` / `world.forge` パッケージ）

異世界アストリアのディメンション `eternalrealm:astoria`。地形の形はバニラのオーバーワールド（ノイズ設定 `minecraft:overworld`、
海面 y=63、高さ -64〜319）で、**バイオームの配置・地表・装飾・建造物**をこのモジュールが決める。

| パッケージ | 種別 | 内容 |
|---|---|---|
| `world.gen` | 純粋（Minecraft非依存） | エリアの輪（`AstoriaAreas`）、バイオーム配置（`AstoriaLayout`, `AstoriaBiome`）、ノイズ（`GenNoise`）、ダンジョン入口の座標（`LandmarkSites`）、王都のNPC位置（`HubSpot`）、登録名（`WorldgenIds`） |
| `world.forge` | Forge | バイオームソース、Feature 群（`world.forge.feature`）、王都・入口の建築、移動API、セーブデータ |
| `data/eternalrealm/` | データパック | `dimension/astoria.json`, `dimension_type/astoria.json`, `worldgen/biome`（24）, `worldgen/configured_feature`（49）, `worldgen/placed_feature`（74） |

データパックのJSONは `python3 tools/gen-worldgen-data.py` で生成する（手で編集しない）。生成器は全バイオームの Feature を
**生成ステップごとに1つの共通順序**で並べる（順序が食い違うと「Feature order cycle」でサーバーが起動しない）。
`WorldgenDataTest` が書き出し結果を検査する（順序の循環、参照切れ、バニラ敵対Mobの湧き、未登録の Feature 型・バリアント、翻訳キー）。

## 1. エリアとバイオーム

エリアは原点 (0, 0) からの水平距離で決まる（`AstoriaAreas.areaAt` = ゲームプレイ側 `world.AreaMap` と完全に同じ輪。テストで一致を確認）。
バイオームの境界は同じ輪に最大 ±8 ブロック（実測 ±6.6）の滑らかなゆらぎを足したもの（`AstoriaLayout.borderJitter`）。
サブバイオームは低周波ノイズ（約200ブロック規模）で選ぶ。配置は固定シード（ワールドシードに依存しない）なので、どのサーバーでも同じ地図になる。

| エリア | 距離 | バイオームID（`eternalrealm:` ＋） | 名前 | 主な景観 |
|---|---|---|---|---|
| 1 アルセリア王国 | 0〜300 | `royal_capital`（半径約96） | 王都の草原 | 王都周辺。木や岩なし、草花のみ |
| | | `arcelia_plains` | アルセリア平原 | 草原、オーク、苔むした岩・遺跡・倒れた柱 |
| | | `arcelia_flower_meadow` | 花咲く牧草地 | 牧草地の花、ひまわり、まばらな木 |
| | | `arcelia_farmlands` | 王国の田園 | 柵付きの畑（小麦・人参・じゃが芋・ビート、水路、案山子、干し草） |
| 2 エルダーフォレスト | 300〜600 | `elder_forest` | エルダーの森 | 白樺・オークの森、ポドゾル・苔、倒木、時々巨木 |
| | | `elder_old_growth` | 古代樹の原生林 | 巨木（2x2幹・根・枝・蔦・光る実）、暗い森、胞子の粒子 |
| | | `elder_mushroom_glade` | 茸の谷間 | 巨大キノコ、菌糸、妖精の輪、アメジスト |
| 3 サンドリア砂漠 | 600〜900 | `sandria_dunes` | サンドリア大砂丘 | 砂丘の起伏、砂岩の尖塔、遺跡、白骨の枯れ木、まれにオアシス |
| | | `sandria_mesa` | 赤岩の台地 | テラコッタの縞模様、縞の岩柱、赤砂 |
| | | `sandria_oasis` | 蜃気楼のオアシス | 泉（砂・草・サトウキビ・スイレン）、ヤシの木 |
| 4 ノルディア雪原 | 900〜1200 | `nordia_snowfield` | ノルディア雪原 | 雪原と吹きだまり、凍った池、氷の結晶 |
| | | `nordia_taiga` | 雪の針葉樹林 | 雪のトウヒ林 |
| | | `nordia_ice_spikes` | 氷柱の原 | 氷柱、氷の尖塔、雪の粒子 |
| | | `nordia_crystal_hollow` | 氷晶の窪地 | 氷とアメジストの結晶群、凍った湖 |
| 5 ドラゴニア火山 | 1200〜1500 | `dragonia_ashlands` | 灰の荒野 | 灰（凝灰岩・砂利・玄武岩）、焦げた枯れ木、灰の粒子 |
| | | `dragonia_basalt_fields` | 玄武岩の原野 | 玄武岩の柱と原、噴気孔（煙）、マグマ結晶 |
| | | `dragonia_caldera` | 灼熱のカルデラ | 溶岩の川・池、黒曜石の尖塔、地表の水はすべて溶岩 |
| 6 セレスティア天空大陸 | 1500〜1800 | `celestia_meadow` | 光の草原 | 淡い光の草原、方解石、多数の浮島（滝付き）、白い胞子 |
| | | `celestia_cherry_grove` | 天空の桜林 | 桜、花びら、桜の浮島 |
| | | `celestia_white_woods` | 白樹の森 | 白い幹と淡い葉の木（光る吊り飾り）、石英の結晶、結晶の浮島 |
| 7 終焉の塔 | 1800〜 | `abyss_wastes` | 終焉の荒野 | 深層岩・ブラックストーン・エンドストーン、闇の尖塔、灰 |
| | | `abyss_dead_forest` | 枯死の森 | 枯れ木の森（蜘蛛の巣）、ソウルソイル |
| | | `abyss_void_rift` | 虚無の裂け目 | 地割れ（黒曜石・泣く黒曜石の裂け目）、虚無の結晶、逆流する粒子 |
| | | `abyss_spire` | 塔の影 | 終焉の塔の周囲（半径約160） |

- 空・霧・水・草・葉の色はエリアごとに調整（火山は赤褐色の空、終焉は紫黒の空、天空は明るい水色と白い葉）。環境音: 火山＝玄武岩の三角州、カルデラ＝真紅の森、終焉＝ソウルサンドの谷。
- 湧き: **monster は全バイオームで空**（MODが独自に湧かせる）。動物（羊・豚・鶏・牛・馬・兎・狼・狐・ムーシュルーム・ラクダ・ヤギ）、コウモリ、イカ・鮭・発光イカのみ。
- 鉱石・洞窟・ジオードはバニラと同じ（モンスタースポナー部屋は除外）。バニラ構造物（村・要塞など）はバイオームタグに含まれないため生成されない。

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

## 3. 王都 アルセリア王都（`HubBuilder` / `HubLayout`）

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

## 4. ダンジョン入口（`LandmarkBuilder` / `Landmarks`）

プレイヤーが中心から **80ブロック以内**に来て（王都は160以内）、必要なチャンクがすでに読み込まれていれば一度だけ建築する（毎秒判定。
サーバースレッドで地形生成をしないため）。40ブロック以内まで近づいた場合は未読み込みのチャンクも同期生成して建築する。
門は王都の方角を向く。床の高さは周囲のノイズ地形の中央値（d8は塔と同じ式）で、建築前から `Landmarks.*` で取得できる。
各入口: テーマ別の門（ポータル面・アーチ・装飾）、**入口プレート**（entrance）、**NPC位置**（柱ブロックの床と灯り）、**ウェイポイント**（5x5の台座、隣にロードストーンの道標）。

| ID | エリア | 中心 x, z | 距離 | 門の向き | entrance | npc | waypoint | テーマ |
|---|---|---|---|---|---|---|---|---|
| d1 失われた地下都市 | 1 | 230, 70 | 240 | 西 | 226, 70 | 224, 74 | 220, 70 | 苔むした石のアーチと地下への階段、鉄格子の封印 |
| d2 古代樹の迷宮 | 2 | -330, -300 | 446 | 東 | -326, -300 | -324, -304 | -320, -300 | 2本の古木の幹と根の門、葉の天蓋、蔦 |
| d3 砂塵の神殿 | 3 | 560, 470 | 731 | 西 | 556, 470 | 554, 474 | 550, 470 | 砂岩の神殿正面（破風・テラコッタ）、砂に半分埋没 |
| d4 凍てつく地下牢 | 4 | -720, 690 | 997 | 東 | -716, 690 | -714, 686 | -710, 690 | 鉄格子の牢門、鎖とソウルランタン、氷壁 |
| d5 氷結の城 | 4 | 840, -660 | 1068 | 西 | 836, -660 | 834, -656 | 830, -660 | 氷の塔2基に挟まれた城門 |
| d6 灼熱の火山洞窟 | 5 | -1010, -790 | 1282 | 東 | -1006, -790 | -1004, -794 | -1000, -790 | 玄武岩の山の洞口、両脇に溶岩の滝と受け皿 |
| d7 天空の古代遺跡 | 6 | 1290, 1010 | 1638 | 西 | 1286, 1010 | 1284, 1014 | 1280, 1010 | 石英のアーチ、折れた列柱、浮遊する石、光の棒 |
| d8 終焉の塔 | 7 | 0, -2100 | 2100 | 南 | 0, -2083 | 5, -2081 | 0, -2079 | 塔の南壁の大門（泣く黒曜石の枠、紫の封印、魂の篝火） |

終焉の塔: 八角形の基壇（半径22）、本体（半径13→上部7、高さ約200、黒石レンガ・黒曜石の稜・紫の窓・内部の床）、尖塔（20）。
ワールド生成でチャンクごとに描かれるため遠くから見え、入口建築時には門だけを切り開く（基壇の内側は整地しない）。

## 5. API

| クラス | メソッド | 説明 |
|---|---|---|
| `AstoriaTravel` | `sendToHub(ServerPlayer)` | 王都のスポーン位置へテレポート（未建築なら建築、チャンクは必要に応じ生成）。ディメンションが無ければ false |
| | `sendToWaypoint(ServerPlayer, String dungeonId)` | 入口のウェイポイントへ（未建築なら建築） |
| | `teleport(ServerPlayer, ServerLevel, BlockPos, float yaw)` | アストリア内の任意の足元位置へ |
| | `isInAstoria(Entity)` / `areaAt(double x, double z)` / `astoria(MinecraftServer)` | 判定・エリア(1〜7)・レベル取得 |
| `HubBuilder` | `ensureBuilt(ServerLevel)` → `HubLayout` / `isBuilt(ServerLevel)` | 王都の建築（1回だけ） |
| `HubLayout` | `of(ServerLevel)`, `position(HubSpot)`, `yaw(HubSpot)`, `positions()`, `spawnPoint()`, `respawnPoint()`, `questBoard()` ほか各施設, `groundY()` | NPC・スポーン位置 |
| `Landmarks` | `entrance(id)`, `npc(id)`, `waypoint(id)`（各 `ServerLevel` 版あり）, `gateFacingYaw(id)`, `npcYaw(id)`, `area(id)`, `dungeonIds()` | 入口の座標（建築前も有効） |
| `LandmarkBuilder` | `ensureBuilt(ServerLevel, id)`, `isBuilt(ServerLevel, id)`, `onServerTick`（EternalRealm で登録済み） | 入口の建築 |
| `AstoriaDimension` | `LEVEL`, `biomeKey(AstoriaBiome)`, `biome(Holder<Biome>)` | レジストリキー |
| `AstoriaAreas`（純粋） | `areaAt`, `areaAtDistance`, `innerRadius`, `outerRadius`, `titleKey`, `subtitleKey` | エリアの輪（キーは `area.eternalrealm.<slug>` / `.desc`、文言は docs/i18n/world.json） |

すべてサーバースレッド専用。ログイン・リスポーン時の転送は統合側で `AstoriaTravel.sendToHub` / `HubLayout.respawnPoint()` を使って配線する。

## 6. テスト

- 純粋: `AstoriaLayoutTest`（輪の境界、world モジュールとの一致、境界のゆらぎ幅、全サブバイオームの出現、入口が各エリア内で王都を向く、HubSpot の重複なし）、
  `WorldgenDataTest`（上記のデータ検査、翻訳キー）。
- GameTest `WorldGameTests`: ディメンションとバイオームソースの存在、サンプル距離でのバイオーム→エリア、各エリアのチャンク生成と地表テーマ、
  王都の建築（全 HubSpot が立てる・噴水・城壁・2回目は再建築しない）、全入口の建築（entrance/npc/waypoint が立てる・建築前後で座標不変）。

翻訳キー（ja/en）: `docs/i18n/worldgen.json`（バイオーム名 `biome.eternalrealm.<id>`、`dimension.eternalrealm.astoria`、`hub.eternalrealm.<spot>`、`landmark.eternalrealm.<id>`）。

## 7. 既知の制限

- 地形はバニラのオーバーワールドノイズ。王都や入口が海・山に当たると、土台を埋め立て／切り崩して整地する（周囲は8〜12ブロックでなだらかに戻す）。
- 建物・看板の文字（Sign）は無い。掲示板の「紙」は旗で表現。NPC は統合側が配置する。
- 溶岩の川・池は流れ出さない位置にだけ置くが、プレイヤーが縁を壊すと流れる。浮島の滝は地面に落ちて広がる（通常の水）。
- 宝箱・樽は装飾のみ（中身なし）。
