# ボス・ダンジョン（`boss` / `dungeon` パッケージ）

純粋モジュール（Minecraft非依存）。ボスが「いつ・何を」するか、ダンジョンの「形」と「進行」を決める。実際の描画・ダメージ・召喚・ブロック設置は Forge 層が行う。
時間はサーバーtick（`long`）、乱数は注入（`RandomGenerator` / レイアウトはシードから `java.util.Random`）。同じ入力とシードなら必ず同じ結果。サーバースレッド専用。

## 1. 主なクラス

| パッケージ | クラス | 役割 |
|---|---|---|
| boss | `BossDefinition` / `BossPhase` / `BossAbility` / `AbilityKind` / `DifficultyTuning` / `Difficulty` | 不変の定義（検証付きレコード） |
| | `BossBrain` | ボス1体ごとの状態マシン。`tick(now, hp割合, ボス位置, 対象一覧)` → `BossCommand` の列 |
| | `BossShape`（Circle/Cone/Line/Ring/Arena） | 絶対座標の攻撃範囲。`contains(x,y,z)` でプレイヤー（足元）を判定 |
| | `BossCommand` | PhaseChanged / AbilityStarted / Telegraph / Cancelled / Execute / Volley / Summon / Teleport / ShieldChanged / Enraged / Heal / Buffed |
| | `Bosses` | b1〜b8・final の内容 |
| dungeon | `DungeonDefinition` / `RaidDefinition` / `RoomPlan` / `Wave` / `MonsterGroup` | 定義（d1〜d8、最終レイド） |
| | `DungeonLayoutGenerator` → `DungeonLayout` | 決定的なレイアウト生成と建築計画（`BlockFill` の列） |
| | `DungeonRun` | インスタンス1つの状態マシン → `DungeonRunEvent` |
| | `InstanceAllocator` / `InstanceSlot` | 専用ディメンション内の区画（x = i×1024）の割り当て（既定16） |

## 2. ボスの規則

- **開始**: 対象が1人以上いる最初のtickで戦闘開始（PhaseChanged(0)、激昂タイマー開始）。対象がいない間は新しい技を始めない（予兆済みの技は解決する＝場外に出ても回避扱いにならない）。
- **技の時間軸**: 開始tick t に範囲を確定して `AbilityStarted` + `Telegraph`（範囲ごと）→ t+telegraphTicks に `Execute` 等（予兆と同じ範囲）→ さらに castTicks 硬直 → フェーズのグローバルCT。技ごとのCTは開始から数える。同時に1つだけ。技の実行中ボスは静止させる。
- **選択**: フェーズの「オープナー」（フェーズ開始直後に順に使う看板技、CT無視）→ 準備完了の技から weight による重み付き抽選。状況条件: SOAK は対象 ≥ minPlayers、HEAL は失ったHP ≥ 回復量、SHIELD/BUFF は効果中は不可。
- **対象規則**: NEAREST / FARTHEST（水平距離）/ RANDOM / HIGHEST_THREAT（同値は近い方）。対象は UUID 順に並べてから判定（呼び出し側の順序に依存しない）。
- **フェーズ**: HP割合がしきい値以下になると現在の技を取り消し（`Cancelled`）、`PhaseChanged`、60tick 無敵（ShieldChanged on → off）。フェーズは戻らない。複数しきい値を一度に越えたら最後のフェーズへ直行。
- **激昂（ソフト）**: 戦闘開始から enrageAfterTicks で `Enraged`。以後ダメージ×1.5、CT×0.6。
- **予兆の最低長**: ダメージ技 ≥10tick（反応＋遅延でも回避の無敵8tickが間に合う）、全体攻撃 ≥20、頭割り ≥30。倍率2.0以上の技は内容テストで ≥20tick。
- **防御フラグ**（`AbilityKind`）: 全体攻撃=回避のみ（ガード不可）。頭割り(SOAK)=回避・ガード不可、ダメージを範囲内人数で分割。その他=回避・ガード・パリィ可。
- **範囲判定**: 水平判定＋上下3ブロック以内。視線判定なし（柱に隠れても当たる＝予兆どおり）。yaw は Minecraft 準拠（0=+Z、90=-X、`combat.HitGeometry` と同じ）。
- **シールド技**: 実行時に取り巻き（`<bossId>_minion`）を召喚し、全滅かタイムアウトまで無敵。取り巻きの死亡ごとに `onAddKilled()`。
- **式**: 最大HP = baseHealth × (1 + 0.7 × (人数−1)) × 難易度HP倍率。1撃 = damage × 技倍率 × 難易度ダメージ倍率 × 激昂 × バフ。
  HARD: HP×1.4・ダメージ×1.3・CT×0.9。EX: HP×2.0・ダメージ×1.6・CT×0.8＋各フェーズに専用技を1つ追加。
- **バランス**（`BossesContentTest`）: 適正Lvの1撃 = 8〜45（Lv1〜50）、実効1.5撃/秒、バニラ防具式で軽減。NORMAL で1人≈3分、5人≈2.3分（最終ボスは10〜20人で≈3.6分）。全員2〜4分に収まることをテスト。

## 3. ボス一覧（名前は ja/en を docs/i18n/boss.json に）

| ID | 名前 | ベース | Lv | HP(1人) | 主なギミック（EX追加技） |
|---|---|---|---|---|---|
| b1 | 地下都市の盗賊王 | vindicator | 6 | 3100 | 扇薙ぎ・突進・煙玉(散開)、P2 旋風斧・手下召集（投げナイフ扇） |
| b2 | 古代樹の守護者 | iron_golem | 13 | 4300 | 根撃(周囲)・茨の直線・枝薙ぎ、P2 胞子(散開)・若木召喚、P3 樹皮の守り(取り巻きシールド)・再生（蔓の環） |
| b3 | 砂塵の巨神 | ravager | 20 | 5750 | 震脚・突進・砂の吐息、P2 流砂・砂塵の環(内側安置)、P3 大砂嵐(全体)（岩石投擲） |
| b4 | 凍獄の処刑人 | piglin_brute | 26 | 6800 | 断罪(タンク狙い)・鎖・氷の枷、P2 追跡(瞬間移動)・看守召集、P3 処刑宣告(頭割り)・氷獄の裁き(全体)（氷鎖の環） |
| b5 | 氷獄の女王 | evoker | 29 | 7600 | 氷槍(弾)・凍結領域・吹雪、P2 氷晶の障壁(取り巻き)・転移・氷河の環、P3 絶対零度(全体)・凍てつく心臓(頭割り)（雹嵐） |
| b6 | 灼炎の竜王 | blaze | 35 | 8650 | 吐息・火球・隕石、P2 竜王の怒り(バフ)・獄炎の環・召喚、P3 大噴火(全体)・落陽(頭割り)（炎翼突撃） |
| b7 | 天空の機械神 | iron_golem | 42 | 9500 | 光線・誘導弾・腕薙ぎ・砲撃、P2 防衛機の障壁・転移、P3 全域浄化(全体)・審判の光(3人頭割り)・自己修復（軌道爆撃） |
| b8 | 終焉の門番 | warden | 47 | 10600 | 粉砕撃・衝撃波・闇の柱・虚空歩、P2 門の封印(取り巻き)・召喚、P3 終焉の鼓動(全体)＋憤怒（崩落） |
| final | 終焉の王 | wither_skeleton | 50 | 18400 | 4フェーズ、各フェーズの最初に全体攻撃（終焉の波動→蝕→忘却→万物の終焉）。5人以上必要な頭割り「破滅の刻印」、取り巻き6体を倒すまで無敵の「近衛の守護」(P2・P4)、亡者の軍勢、影渡り、崩壊の環（虚無の裂け目） |

推奨人数: b1〜b8 は1〜5人、final は10〜20人。召喚テンプレート: `b1_minion`〜`b8_minion`, `final_minion`。

## 4. ダンジョン

| ID | 名前 | エリア | Lv | 部屋 | テーマ | モンスター（雑魚/群れ/遠隔/精鋭） | 制限 |
|---|---|---|---|---|---|---|---|
| d1 | 失われた地下都市 | 1 | 6 | 3 | underground_city | d1_bandit / d1_cave_rat / d1_crossbowman / d1_ghoul | 30分 |
| d2 | 古代樹の迷宮 | 2 | 13 | 4 | forest | d2_spider / d2_wolf / d2_dryad / d2_treant | 30分 |
| d3 | 砂塵の神殿 | 3 | 20 | 4 | desert_temple | d3_mummy / d3_scorpion / d3_cultist / d3_sand_golem | 30分 |
| d4 | 凍てつく地下牢 | 4 | 26 | 5 | ice_prison | d4_prisoner / d4_frost_hound / d4_ice_wraith / d4_jailer | 35分 |
| d5 | 氷結の城 | 4 | 29 | 5 | ice_castle | d5_ice_knight / d5_snow_wolf / d5_frost_mage / d5_ice_golem | 35分 |
| d6 | 灼熱の火山洞窟 | 5 | 35 | 5 | volcano | d6_salamander / d6_magma_slime / d6_fire_imp / d6_lava_golem | 35分 |
| d7 | 天空の古代遺跡 | 6 | 42 | 6 | sky_ruins | d7_sky_sentinel / d7_harpy / d7_storm_spirit / d7_ruin_construct | 40分 |
| d8 | 終焉の塔 | 7 | 47 | 6 | dark_tower | d8_shade / d8_abyss_hound / d8_void_mage / d8_dark_knight | 40分 |
| final（レイド） | 終焉の玉座 | 7 | 50 | 0 | end_throne | — | 30分 |

- 部屋ごとに2〜4ウェーブ。数 = 基本数 + round(1人増えるごとの追加 × (人数−1))。後の部屋ほど多く、最後の部屋に精鋭ウェーブ。
- **レイアウト**: 256×256×24 の区画を 36 ブロック×7×7 のセルに分け、自己回避ランダムウォークで「入口ホール→部屋→…→ボス部屋」のセルを選ぶ。各空間はセル内に余白付きで置き（重ならない）、セル中央帯を必ず覆うので隣同士は直線の通路（内幅3×高さ4）で壁の角を避けて接続できる。部屋 15〜25（奇数）、ボス部屋 31×31（床29×29）、最終レイド 53×53（床51×51、通路内幅5）。
- 建築計画は役割（FLOOR/WALL/PILLAR/LIGHT/ACCENT/DOOR/AIR）付きの直方体の列。順に適用（外殻→内部の空気→床→装飾→開口部）。扉は部屋の出口の壁にあり、クリアまで DOOR で封鎖。入口扉は開始まで封鎖。湧き点は床上（y=1）で、柱と開口部前の通路帯を避ける。
- テストでは建築計画を 256×24×256 のボクセルに適用して探索: 外部（未建築空間）への穴がない、扉を閉じると各部屋が独立、順に開けると次の部屋→ボス・出口に到達できる。

## 5. DungeonRun の状態遷移

`CREATED → startBuilding() → BUILDING → onBuilt() → READY → onStart(now) → IN_PROGRESS(部屋, ウェーブ) → BOSS → onBossDefeated(now) → CLEARED → close() → CLOSED`。
失敗 `FAILED`: 制限時間（開始＋制限ちょうどのtick、期限ちょうどのボス撃破も失敗）、全員不在が猶予（既定60秒）を超える、死亡数が上限（既定は無制限）、未完了での `close()`（ABORTED）。設定は `FailurePolicy`。
メンバーは生成時に固定（重複不可、人数は定義の範囲内）。ウェーブ全滅で次のウェーブ、最後のウェーブで `OpenDoor(部屋)` と次の部屋の `SpawnWave`、最後の部屋で `StartBoss`。レイドは開始と同時に `StartBoss`。
違う部屋・余分な撃破・終了後の入力は無視（イベントなし）。ライフサイクルの誤用は `IllegalStateException`。

## 6. Forge 層がやること

1. **ボス**: `BossBrain(definition, 人数, 難易度, new Random(seed))` を生成。MAX_HEALTH=`maxHealth()`、ARMOR=`armor()`、攻撃力=`meleeDamage(now)`、移動速度×`movementSpeedMultiplier()`（PhaseChanged 時）。バニラの特殊攻撃（エヴォーカーの魔法、ブレイズの火球、ウォーデンのソニックブーム等）は外し、移動と近接だけ残す。ピグリンブルートはゾンビ化無効。
2. 毎tick `tick(now, hp/maxHp, BossPosition(足元の床の高さ・yaw), 対象)`。対象 = アリーナ内の生存プレイヤー（観戦者除外）、threat は与ダメ・回復・挑発から算出。
3. `currentAbility()` がある間はボスを静止させる。`Telegraph` を duration の間パーティクル表示、`Cancelled` で消す。`Execute` は範囲内の全員に damage を適用（`dodgeable`=回避の無敵で無効、`guardable`=ガード/パリィ可、`splitsDamage`=人数で割る）。`Volley` は yaw ごとに投射物、`Summon` は位置に召喚（塞がっていれば近くの空き地）、`shieldAdds` の取り巻きは死亡・召喚失敗ごとに `onAddKilled()`。`Teleport`/`Heal`/`Buffed`/`Enraged` を実行・演出。
4. ダメージイベントで `isInvulnerable(now)` なら無効化。ShieldChanged・PhaseChanged・Enraged のメッセージキーは各レコードの定数。
5. **ダンジョン**: `InstanceAllocator.allocate()`（満員なら `FULL_MESSAGE_KEY`）→ `DungeonLayoutGenerator.generate(def, seed)`（レイドは `raidArena`）→ `startBuilding()` → 建築計画を数tickに分けて設置（原点 = スロット原点＋基準高さ）→ `onBuilt()` → メンバーを入口へ → `onStart`。毎tick `run.tick(now)`。
6. ウェーブのモンスターに（インスタンス, 部屋）タグを付け、死亡・消滅ごとに `onMonsterKilled(部屋)`。湧き点は順番に使い回す。`OpenDoor` で扉の箱を空気に。`StartBoss` でボス部屋中央に召喚。死亡/退出/再接続を通知。
7. 終了時: メンバーを外へ → `close()` → `occupiedBoxes()` を空気で消去・エンティティ除去 → `release(slot)`。再起動後は前回の内容が不明なので、ディメンションのリセットか区画全体の消去を行う。

## 7. 未実装・今後

ドロップ・報酬・討伐記録（ランキング連携は `Cleared` の時間を使う）、ボスごとの専用演出、宝箱・仕掛け・中ボス、ダンジョン難易度別のウェーブ倍率、レイドの役割UI。
