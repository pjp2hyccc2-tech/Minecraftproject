# 町のNPCの体（`npc` / `npc.forge` / `client.entity` パッケージ）

王都の人々（窓口NPC10人、物語キャラ、クエストNPC、国王、町の人・衛兵・聖職者、ダンジョンの門番）が、バニラの村人ではなく専用の
エンティティ `eternalrealm:npc`（`NpcEntity`）と専用モデルで立つ。見た目は「アピアランス（appearance）」IDで切り替える。
会話や窓口の中身（`Npcs.onInteract` のハンドラ、`StoryNpcs`）は従来どおり `Mob` だけに依存し、体が変わっても影響しない。

## 1. 構成

| 場所 | 内容 |
|---|---|
| `npc`（純粋） | `NpcBodies`（生成コード: アピアランスID・縮尺・話す仕草の長さ）、`NpcLooks`（名前付きNPCの見た目）、`Townsfolk`（群衆・衛兵スポットの解釈、見た目の決め方、名前・挨拶のキー） |
| `npc.forge` | `NpcEntity`（体）、`NpcEntities`（エンティティ型・属性の登録、`register(modBus)`）、`Npcs`（窓口NPCの配置と会話、旧村人の置き換え）、`Crowds`（町の人・衛兵の配置と挨拶）、`StoryNpcs`、`TownSetup` |
| `client.entity` | `NpcRenderer`、`model.NpcModel`、`ERRigs.NPC`、`ERModelLayers.npc()`、生成コード `model.gen.NpcGeometry` / `NpcAnimations` |
| `tools/art_entities/generate_npc.py` | **単一の情報源**: `rig_npc.py`（形状・アニメーション）、`npc_cast*.py`（全アピアランス）、`npc_paint.py`（顔・髪・服の描画）から、テクスチャ・肖像画・Java・UV表・プレビューを生成（決定的。Pillow/numpy） |

```
python3 tools/art_entities/generate_npc.py              # 全部を生成（約15秒）
python3 tools/art_entities/generate_npc.py mina lilia   # 指定したアピアランスのプレビューだけ（/tmp/npcwork/preview_sel.png）
```

生成物は手で編集しない。`tools/art_entities/preview_npc.png`（全員の正面・斜め・背面）と `preview_npc_portraits.png`（肖像画）はコミットする。
`preview_npc_anim.png`（全アニメーションの連続コマ）は手元確認用（gitignore）。UV表は `uv_map_npc.txt`。

## 2. リグとアニメーション

- プレイヤーと同じ大きさ（32単位）の人型。肘・膝あり、141パーツ（大半は任意パーツ）: 髪型（ポニーテール、お団子、三つ編み、長髪）、
  ひげ、帽子類（兜、フード、麦わら帽子、シルクハット、ベレー、ターバン、司教冠、とんがり帽子、ティアラ、王冠、羽飾り、光輪）、眼鏡、
  襟・スカーフ・毛皮・マント・燕尾・エプロン・スカート・ローブ・ヴェール、持ち物（槌、帳簿と羽ペン、硬貨、皿、お玉、印章、木槌、メガホン、
  練習剣、角杯、斧槍、槍、ランタンの杖、薬瓶、燭台、王笏、杖、箒、熊手、籠、扇、長弓、凧形の盾、リュート、矢筒、背負い袋、袋、鳩、宙に浮く本、天球儀）。
- 顔は8×8。まばたき（`eyelids`）と口パク（`mouth`）は顔の内側に置いた板を数フレームだけ前に出す。
- スロット（`NpcModel` の順）: **idle**（常時。呼吸・体重移動・まばたき）、**walk**（町の人が歩くとき。移動距離で進む）、
  **work**（その人の仕事のループ。鍛冶の槌打ち、帳簿付け、皿洗い、掃き掃除、リュート、祈り…。プレイヤーが3.5ブロック以内にいる間・歩行中・会話中は
  約0.6秒かけて消える）、**talk**（話しかけられたときの仕草1回。お辞儀、豪快な笑い、敬礼、祝福…）。
- アニメーションの開始時刻はエンティティごとにずらす（群衆が一斉に同じ動きをしない）。

## 3. アピアランス（64）

| 区分 | ID |
|---|---|
| 窓口NPC（話者ID＝見た目ID） | mina（受付）, pim（道具屋）, goran（鍛冶師）, hagen（教官）, luce（競売人）, chico（配達人、鳩付き）, marta（登録係）, gant（王都の門衛）, zain（闘技場主）, chloe（記録官） |
| 物語キャラ | lilia（星門の間の王女。星の王笏）, gustav（ギルドマスター。眼帯とシチュー）, nox（星詠み。宙に浮く星図）, laszlo（皿洗い中の元盗賊王。足かせ付き）, wolfram（療養中の銀狼。包帯と銀狼の毛皮）, adel（騎士団長。大剣）, king（国王ベルトラン十四世。いつでも出撃できる胸甲と王冠、うれし涙） |
| クエストNPC | neris（エルフの賢者）, khalid（隊商長）, helga（北の族長）, ilsa（竜鍛冶）, selene（神託人形。浮遊）, gunter（塔守） |
| 町の人（12種×2色） | merchant, housewife, farmer, sailor, noble_lady, noble_gent, elder, apprentice, maid, bard, child, adventurer（各 `…2` が別の色・性別・髪型） |
| 町の人（1色の役目） | stable_master（厩舎長。農夫の体に青いつなぎ）, innkeeper（星灯りの館の女将。ワイン色のドレスと山吹色のショール） |
| 衛兵・聖職者 | guard（斧槍と青い陣羽織の門衛）, knight（全身鎧・盾・剣の王城騎士）, archer（長弓と矢筒の城壁弓兵）, priest（司祭）, nun（修道女） |
| ダンジョン入口の門番 | warden_d1〜warden_d8（地域の色: 王国の青、森の緑、砂漠の砂色と赤、凍獄の灰青と毛皮、氷城の水色と白い毛皮、火山の黒と橙、天空の白金、終焉の塔の黒紫）, warden（それ以外） |
| その他 | citizen（既定。不明なIDもこれ） |

伝説の旅人4人（[travellers.md](travellers.md)）はこの NPC リグではなく、2倍の解像度と戦いのアニメーションを持つ **英雄リグ**
（`tools/art_entities/generate_hero.py`、`textures/entity/hero/`、96×96 の肖像）で描く。

縮尺（当たり判定も追従）: 子ども0.68〜0.8、チコ0.8、イルザ0.92 … ゴラン1.04、グスタフ1.08。発光: リリア（王笏・ペンダント・ティアラ）、
ノクス（星図・ローブの星）、ネリス（杖のつぼみ）、セレネ（目・光輪・天球儀）、ギュンター（ランタン）、修道女（ろうそく）。

**肖像画**: `textures/gui/portrait/<id>.png`（48×48、テーマ色の背景、少しテキスト側を向いた胸像）。色違い（末尾が数字）以外の全アピアランス。
会話ウィンドウ（`ClientDialogue`）は話者キーの最後の区切り（`story.eternalrealm.speaker.mina` → `mina`）で探し、無ければ肖像なしで表示する。
町の人の挨拶は話者キー `npc.eternalrealm.folk.<種類>` なので、種類の肖像画（`merchant.png` など）が出る。

### 追加・変更の手順

1. `npc_cast_*.py` に関数を足し `cast()` に入れる（`look(id, builds, show, poses, pal, paint, work, talk, scale, portrait)`）。
   色は `palette(...)`（素材のスタイルも上書き可）、顔は `Face(...)`、服は `npc_cast.py` の模様（紋章、縞、鎧、陣羽織…）と `npc_paint.py`。
2. `python3 tools/art_entities/generate_npc.py` → `preview_npc.png` と肖像画を目で確認。
3. 町の人なら `Townsfolk` のプールと種類（名前・挨拶のグループ）に足し、`docs/i18n/npc_bodies.json` に名前を足す（テストが検出）。

## 4. エンティティ `NpcEntity`

- `PathfinderMob`。同期データ: アピアランスID。保存: アピアランス、`Mode`、持ち場（位置・向き）、配置スタンプ。
- 無敵（`/kill` などの無敵貫通ダメージ以外は `hurt` が false。戦闘の対象外 `isAttackable=false`）、押されない、流されない、リード不可、次元移動しない、
  デスポーンしない（ピースフルでも）、無音。名前札は従来どおり（窓口NPCは常時表示の金色、物語キャラは水色、町の人は見たときだけ）。
- 共通の行動: 近くのプレイヤーを見る（8ブロック）、ときどき辺りを見回す。話しかけられると相手の方を向き、話す仕草がエンティティイベント（101）で全クライアントに流れる。
- `Mode`:
  - **POST**（窓口NPC・物語キャラ・クエストNPC）: 持ち場に立ち、よそを見たあとは持ち場の向きに戻る。1.5ブロック以上ずれたら戻る。
  - **STROLL**（町の人）: スポットから6ブロック以内を `0.42` の速さでゆっくり歩き、立ち止まる（平均6秒）。水と扉は通らない（経路コスト −1）、
    目的地は乾いた地面だけ。**48ブロック以内にプレイヤーがいない間は経路探索を一切しない**（2秒ごとに確認）。離れすぎたら歩いて戻る／遠ければ戻す。
  - **WATCH**（衛兵）: スポットの向きを向いて立つ。頭はアニメーションで左右を見張り、5ブロック以内の人には時々目を向ける。
- **重複の解消**: 起動時の配置はチャンクのエンティティ読み込み（非同期）より先に走るため、同じ人の体が二つになりうる。`Npcs`/`Crowds` は配置した体に
  配置スタンプ（ゲーム時刻）を押し、各NPCは最初のtickと30秒ごとに48ブロック以内の「同じ人」（`Npcs.identity`: 役割＋キー、またはスポット名）を探し、
  一番新しく配置された体だけを残す（同じ人の旧村人も消す）。

## 5. 配置

- **窓口NPC・物語キャラ・クエストNPC**: 従来どおり `Npcs.ensure` / `StoryNpcs.ensure`（`RealmSetup`、`TownSetup`）。見た目は役割／キャラIDの話者ID
  （`NpcLooks`）、ダンジョン入口の門番（`Npcs.ENTRANCE` キー付き。名前は「転移門の番人」）はその地域の `warden_<dungeon>`（`NpcLooks.forEntrance`）。
- **旧バージョンの村人**: `TAG`（`eternalrealm_npc`）付きの村人が見つかると、その永続データ（ForgeData 全体）を引き継いだ `NpcEntity` に置き換える。
- **町の人と衛兵**（`RealmSetup.populateCapital` の最後、王都が完成しているとき）:
  - 王都ビルドの名前付きスポット `crowd.<area>.<n>` に町の人を1人、`guard.<post>.<n>` に衛兵を1人（スポットの yaw を向く）。
    城の `castle.gate_guard_w/e` も、2ブロック以内に `guard.*` が無ければ衛兵の持ち場になる。
  - 住人のいる名前付きスポット（`Townsfolk.residentSpots()`）: `castle.king` に国王（POST。名前は話者名「国王ベルトラン」を常時表示、
    挨拶は国王専用の4行、肖像は `king.png`。物語の会話で国王が話すときもこの肖像が出る）。
  - 見た目はスポット名から決定的に決める（`Townsfolk.appearanceFor`）。地区ごとのプール: plaza, market, street, harbour（船乗り）, guild（冒険者）,
    tavern, inn, cathedral（司祭・修道女）, residential, farm（農夫）, castle（侍女・貴族）。`harbour_east` のような名前は先頭一致。未知の地区は町の人一般。
    連番は地区ごとにずらした位置からプールを順に巡るので、隣どうしが違う人になる。
  - 衛兵: 持ち場名に gate → 門衛（斧槍）、wall / tower / rampart / battlement → 弓兵、castle / keep / throne / hall / palace / court → 騎士、それ以外 → 門衛。
  - 冪等: スポット名（永続データ `eternalrealm_spot`）で同じ人を探して戻す。王都の再建（`onCapitalCompleted`）では町の人も一度消して置き直す。
  - 町の人は窓口NPCではない（役割タグなし、地図に出ない）。右クリックで種類ごとの短い挨拶（`npc.eternalrealm.folk.say.<group>.<n>`、14グループ58行）が
    会話ボックス（ボスの叫びと同じアンビエント表示、名前と肖像付き）に出る。話し終えるまで次の挨拶はしない。

## 6. テスト

- 単体 `NpcAssetsTest`: 全アピアランスのテクスチャがUV表のサイズ、余計なテクスチャなし、発光テクスチャの対、全話者と全種類の肖像画（48×48）、縮尺と仕草の長さ。
- 単体 `TownsfolkTest`: スポット名の解釈、決定性と地区ごとの多様さ、地区に合う人、衛兵の種類、名前・肖像・挨拶の存在、翻訳の過不足、名前付きNPCの見た目。
- GameTest `NpcGameTests`: 村人からの置き換え（データ引き継ぎ）、見た目、無敵・押されない、再配置で重複しない、町の人と衛兵の配置と保存、重複の解消。
- 単体 `ClientModelSmokeTest`（CIのみ）: NPCリグをベイクし、全アピアランスの全アニメーションを評価。
