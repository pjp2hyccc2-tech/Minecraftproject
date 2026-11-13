# ネットワークプロトコル

## 1. チャンネル

| 項目 | 値 |
|---|---|
| チャンネル | `eternalrealm:main`（Forge SimpleChannel） |
| プロトコル版 | `"12"`（`ERNetwork.PROTOCOL_VERSION`）。Phase 2 で戦闘パケット追加のため 1→2、以後の追加を経て、スキルツリー（職業スキル枠5・6、職業武器の拒否、スナップショットのスキル枠）で 4→5、HUD 刷新（`UseItemPacket`、戦闘値の回復薬補正）で 5→6、カバンの取得表示（`PickupFeedPacket`）で 6→7、スキルのロックオン（`CombatActionPacket` に targetId）で 7→8、伝説の旅人の技の演出（`HeroFxPacket`）で 8→9、スキルの光（`SkillFxPacket`）で 9→10、ステータス％（`CharacterSnapshotPacket` の末尾に装備の％）で 10→11、見切り（`CombatStatsPacket` の末尾に evasion）で 11→12 |
| 互換性 | クライアント・サーバーとも**完全一致のみ受け入れ**。MOD未導入のクライアントは接続時に拒否 |
| 版上げ条件 | パケットの追加・削除・順序変更・フィールド変更のいずれか |

パケットIDは `ERNetwork.register()` の登録順で決まる（0から連番）。順序を変えたら必ず版を上げる。

## 2. パケット一覧

| ID | 名前 | 方向 | フィールド | 最大サイズ | 処理スレッド |
|---|---|---|---|---|---|
| 0 | `CharacterSnapshotPacket` | S→C | seasonId (UTF, ≤32), level (VarInt), levelCap (VarInt), experience (VarLong), experienceToNext (VarLong, 上限時0), unspentStatPoints (VarInt), allocated×6 (VarInt), total×6 (VarInt) | 512 B | クライアント |
| 1 | `AllocateStatPacket` | C→S | statNetworkId (VarInt: 0=STR 1=VIT 2=AGI 3=INT 4=DEX 5=LUK), amount (VarInt) | 10 B | サーバー |
| 2 | `CombatActionPacket` | C→S | requestId (VarInt: 0=通常攻撃 1=溜め開始 2=溜め解放 3=回避 4=武器スキル), sequence (VarInt, クライアント連番), targetId+1 (VarInt: 0=なし。長押しで狙ったスキルのロック先のエンティティID) | 15 B | サーバー |
| 3 | `CombatStatusPacket` | S→C | ackSequence (VarInt), answersRequest (bool), outcome (byte: 0=受理 1=先行入力 2=無視 3〜9=各種拒否), stamina (float), skillCooldown (VarInt tick), skillCooldownTotal (VarInt tick), defenseEvent (byte: 0=なし 1=回避 2=パリィ 3=ガード 4=ガードブレイク) | 32 B | クライアント |
| 4 | `CombatStatsPacket` | S→C | attackMultiplier, maxHealthBonus, damageReduction, actionSpeedFactor, critChance, critMultiplier, maxStamina, staminaRegenPerTick（すべて double。クライアントの予測が整数tickへ丸める値をサーバーと一致させるため） | 64 B | クライアント |
| 5 | `DamageNumberPacket` | S→C | entityId (VarInt), x/y/z (float), amount (float), flags (byte: 1=クリティカル 2=強攻撃 4=スキル) | 32 B | クライアント（攻撃した本人だけに送る） |
| 6〜9 | `UiActionPacket` `UiDataPacket` `PlayerActionPacket` `TelegraphPacket` | | （各モジュールの説明を参照） | | |
| 10 | `UseItemPacket` | C→S | slot (VarInt: インベントリの 0〜35)。クイック使用（1〜3キー）とカバンの「使う」。サーバーがスロット・アイテム・レベル・クールダウンを確認（`QuickUse.useSlot`）。範囲外のスロットは**違反** | 5 B | サーバー |
| 11 | `PickupFeedPacket` | S→C | stack (ItemStack: 1個。名前・アイコン・レア度), count (VarInt, 1〜1,000,000), flags (byte: 1=カバンが満杯で足元に落ちた) | 2 KB（ItemStack の NBT 込み） | クライアント（取得した本人だけに送る） |
| 12 | `HeroFxPacket` | S→C | entityId (VarInt: 伝説の旅人), action (byte: 0〜19 = 技 `Travellers.Move` の序数、100 = 加入の身振り、101 = 起き上がり。127 まで), x/y/z (float: 狙う位置), seed (int: 流れ星や斬撃の配置を全クライアントでそろえる) | 32 B | クライアント（その旅人を見ている全員に送る。体がその技のアニメーションを演じ、光の演出を描く。[travellers.md](modules/travellers.md)） |
| 13 | `SkillFxPacket` | S→C | kind (byte: 0=地面の領域 1=爆発), theme (byte: `SkillTheme` の序数), x/y/z (float: 領域の中心／爆発の位置), yaw (float: 術者の向き), radius (float: 0.5〜16), delay (short: 最初の脈動までの tick, 0〜600), pulses (byte: 1〜64), interval (short: 脈動の間隔 tick, 1〜600), flags (byte: 1=回復 2=ダメージ), seed (int: 細部の配置を全クライアントでそろえる) | 40 B | クライアント（その位置から 64 ブロック以内の全員。スキルの光 [skills.md §6.1](modules/skills.md)） |

ステータスの並び順は `StatType` の宣言順（ネットワークIDは固定値で、再利用・並べ替え禁止）。

## 3. 受信時の検証

### C→S（すべてのパケット共通: `ServerPacketGuard`）
1. デコード前: ペイロードが最大サイズを超えたら `DecoderException`（接続切断）
2. 送信者（`NetworkEvent.Context#getSender`）が存在すること。ペイロード中のプレイヤー識別子は使わない
3. パケット種別ごとのトークンバケット（既定: バースト10、毎秒5）。超過分は破棄する。**違反には数えない**（連打は正当な操作のため）。
   破棄数はフラッド検知として別に数え、ウィンドウ内で `network.floodKickThreshold`（既定300）に達した場合のみ切断
4. セッションが `ACTIVE`（データ読込済み）でなければ破棄（違反にはしない）

クライアント側でも割り振り要求を最大4回/秒に抑える（キャラクター画面）。既定設定のサーバーでは正規クライアントの要求が破棄されることはない。

### AllocateStatPacket 固有
| 状況 | 扱い |
|---|---|
| 未知の statNetworkId | **違反** |
| amount < 1 または > 100（プロトコル上限） | **違反** |
| amount > サーバー設定の上限（≤100） | 違反にしない。メッセージ＋再同期 |
| ポイント不足（連打などの正当な競合） | 違反にしない。メッセージ＋再同期 |
| 成功 | tick終了時にスナップショット送信 |

### CombatActionPacket 固有
| 状況 | 扱い |
|---|---|
| 未知の requestId | **違反** |
| 職業で使えない武器での攻撃・スキル（回避以外） | 違反にしない。結果 12 = `REJECTED_JOB_WEAPON` を返す |
| 行動中で先行入力枠が埋まっている、スタミナ不足、クールダウン中、ひるみ中、ガード中、武器なし、騎乗中の回避 | 違反にしない。`CombatStatusPacket` で結果（拒否理由）を返す |
| 受理・先行入力 | `CombatStatusPacket` で結果を返す。命中・ダメージはサーバーtickで判定 |
| targetId（スキルのみ。他の要求では無視） | サーバーが検証（存在する・生きている・攻撃対象として正当＝プレイヤーは不可・32+4ブロック以内・視線が通る）。不正なら黙ってロックなしで実行（違反にしない）。ロックは そのスキルの行動が終わるまで（先行入力なら開始を待つ） |

- 専用のトークンバケット（`network.combatActionBurst` 既定20、`network.combatActionPerSecond` 既定20/秒）。超過分は破棄しフラッドとして数える。
- 正規クライアントは状態マシンの予測で許可された入力だけを送るため、1行動につき最大1〜2パケット（溜めは開始と解放）。

### 違反
- 対象は「正規クライアントでは起こり得ない要求」だけ（上表の **違反**）。
- `ViolationTracker`: 既定で60秒ウィンドウ内に20回で切断（`network.violationKickThreshold`、0で切断しない）。
- すべてWARNログ（名前・UUID・理由・回数）。レート制限による破棄はDEBUGログ、フラッド切断はWARNログ。

### S→C
- クライアント側でもデコード時に値域を検証（不正値は `DecoderException`）。
- クライアントは受信値を表示するだけで、ローカル計算はしない。ログアウト時に破棄。

## 4. 同期のタイミング

サーバーは1tick内の変更をまとめ、tick終了時に変更のあったプレイヤーへ1回だけスナップショットを送る（`SyncQueue`）。
送信契機: 読込完了、EXP獲得、割り振り、リセット、レベル変更、設定再読込、拒否時の再同期。

### 戦闘の同期（Phase 2）
- 要求ごとに即時に `CombatStatusPacket`（answersRequest=true）を返す。クライアントは自分の最新連番への回答で、拒否なら予測した行動を取り消し、
  スタミナ・クールダウンを採用する（未回答の要求が残っている間は自分の予測を維持）。
- スタミナ回復・クールダウンの補正として、変化中は最大20tickごとに `CombatStatusPacket`（answersRequest=false）。
- 回避・パリィ・ガード・ガードブレイクは発生時に即時送信。
- `CombatStatsPacket` はキャラクター準備完了・ステータス/レベル変更・設定再読込時。
- 他のプレイヤーへは、腕振り（バニラの `ServerboundSwingPacket` → 周囲へ配信）、ガード姿勢（バニラのアイテム使用状態）、
  行動開始・命中の効果音とパーティクル（サーバーから周囲へ配信）で同期する。追加の独自パケットはない。

## 5. 今後のパケット設計ルール（Phase 2 以降）

- C→S は「意図」のみ（例: 攻撃入力、スキル使用要求）。ダメージ・座標の確定値は送らせない。
- 時刻はサーバーtickで判定する。クライアント時刻は参考値としてのみ扱う。
- 高頻度パケットは専用のレート制限を設け、`ServerPacketGuard` を必ず通す。
- 他プレイヤーへの配信（戦闘演出等）は範囲を絞る（`PacketDistributor.TRACKING_ENTITY` 等）。

## 付録: スキルツリーでの変更（版 5）

- `CombatActionPacket` の requestId: 5〜8 = 職業スキル枠 1〜4（4 は必殺技）、**9・10 = 職業スキル枠 5・6**（`CombatRequest` のネットワークID。並べ替え・再利用禁止）。
- `CombatStatusPacket` の outcome: **12 = `REJECTED_JOB_WEAPON`**（職業で使えない武器）。CT は職業スキル枠 0〜6 の 7 つ。
- `CharacterSnapshotPacket`: スキル投資（最大128件）の後に**スキル枠**（skillId の列、空文字 = 空き。通常 6 件、受信側の上限 16 件）を追加。最大 12288 B。
- UI 操作（`UiActionPacket`）: `skill.invest {skill}`、`skill.slot {slot, skill}`（空文字で外す）、`skill.reset`。サーバーが `SkillTree` で再判定し、結果をメッセージとスナップショットで返す。

## 付録: 乗り物（パケット追加なし・版 7 のまま）

- UI 操作（`UiActionPacket`）: `mount.toggle`（引数なし。乗っていれば降りる・呼び出し中なら中止・それ以外は呼ぶ）、
  `mount.choose`（引数なし。持っている乗り物の画面を開く）。判定はすべてサーバー（`MountRules`）。
- ビューデータ（`UiDataPacket`）: チャンネル `mount_state` = `{"owned":["horse",...],"selected":"griffin","riding":""}`（種類）。
  変わったとき（購入・選択・乗る・降りる）と1秒ごとの確認で送る。HUD の乗り物アイコンが使う。
- 乗り物の移動はバニラの乗り物と同じ（乗り手のクライアントが `ServerboundMoveVehiclePacket` で送る）。サーバーは速さと高さを見張る
  （[modules/mounts.md](modules/mounts.md)）。

### 個体差と厩舎（2026-11-11 追加・パケット追加なし）
- UI 操作: `mount.stable.buy {kind, index}`（index 0〜3）、`mount.stable.reroll {kind}`、`mount.select {id, call?}`（id は UUID、
  call が true ならその場で呼ぶ）、`mount.deed {id}`、`mount.redeem {slot}`（カバンの枠 1〜35）。kind は `horse`/`griffin`/`bike`。
  型・範囲の違反は `UiArgs` が違反として扱う。buy・reroll・deed・redeem は厩舎長の8ブロック以内でだけ受け付ける。
- ビューデータ: チャンネル `mount.stable` = `{"open", "mode": "stable"|"choose", "atStable", "gold", "level", "capacity",
  "kinds": [{kind, level, price, flies, speed, varies, reroll, status, "offers": [{index, coat, shape, rare}]}],
  "mounts": [{id, kind, coat, shape, rare, varies, speed, accel, lift, total, grade, selected, riding, tradeable}],
  "deeds": [{…mounts と同じ, slot, redeem}], "reveal": {…買った直後の1頭}}`。**4頭（offers）は毛色と形だけで、個体値を含めない。**
- エンティティの同期データ（`MountEntity`）: 毛色と形（文字列 `coat/shape`）と、その1頭の個体値（3つを1つの int に詰める）。
  乗り手のクライアントが性能を計算するのに使う。
- 証書（アイテム `eternalrealm:mount_deed`）のタグ `er_mount` = `{id (UUID), species, coat, shape, speed, accel, lift, grade}`。
  読み込みは寛容（範囲外の値は丸め、種類が不明・二輪なら壊れた証書として扱う）。

### 品種（2026-11-11 追加・パケット追加なし）
- 種類（kind・species・`mount_state` の値）に品種の id が加わった: `horse_bloom`・`horse_tide`・`horse_gale`・`horse_blaze`・
  `horse_halo`・`griffin_gold`・`griffin_owl`・`griffin_storm`・`griffin_frost`・`griffin_void`（前からの `horse`/`griffin`/`bike` は同じ）。
- `mount.stable` の `kinds` の各項目に `family`（`horse`/`griffin`/`bike`）と `talent`（特性の id、なしは `none`）が加わった。

## 付録: 市場（パケット追加なし・版 8 のまま）
- S→C `UiDataPacket`: `market.board`（掲示板の検索結果 1 ページ・自分の出品・メール・規則。開くときは `open: true` と `tab`）、
  `market.prices`（カバンの 1 枠の品の相場）、`market.auction`（オークション会場: 開催中・待機列・自分のロット・結果・規則。
  画面を開いている人には変化のたびに送る）。どれも `UiDataPacket.MAX_JSON` に収まるよう、長い一覧は末尾から削って `trimmed` を付ける。
- C→S `UiActionPacket`: `board.query {text?, category?, rarity?, minLevel?, maxLevel?, gold?, sort?, page?}`、
  `board.buy {id, …検索条件}`、`board.list {slot, count, price}`、`board.cancel {id}`、`board.prices {slot}`、`board.open`、
  `mail.claim {id?}`（id なしは預かり金）、`mail.claim_all`、`auction.open`、`auction.close`、`auction.bid {id, amount}`、
  `auction.buyout {id}`、`auction.register {slot, count, start, buyout, minutes}`、`auction.start {id}`、`auction.withdraw {id}`。
  slot はカバンの 1〜35、価格は 1〜9,999,999,999、個数は 1〜9,999、分類・並びは既知の値だけ。範囲外は違反（`UiArgs`）。

## 付録: 金枠の遺宝と装身具（パケット追加なし・版は変えない）

- UI 操作（`UiActionPacket`）: 既存の `bag.equip {slot}` に省略可能な `target`（`accessory_1`・`accessory_2`・`charm`。装身具を入れる枠）、
  `bag.unequip {target}` の `target` に同じ3つを追加。レベル・同じ装身具の二重装備はサーバーが判定（`BagService`）。
- ビューデータ（`UiDataPacket`）: チャンネル `trinkets` = `{"stacks":["<SNBT>","",""]}`（飾1・飾2・守の順、空文字 = 空き）。
  ログイン・リスポーン・ディメンション移動・付け外しのたびに本人へ送る。装備画面が使う。
- 必要レベルの足りない武器での攻撃・ガード・武技は既存の `CombatStatusPacket` の outcome `REJECTED_NOT_ALLOWED` で断り、理由はメッセージで送る
  （[modules/armory.md](modules/armory.md)）。

## 付録: 伝説の旅人（版 9: `HeroFxPacket` を追加）

- S→C `HeroFxPacket`（ID 12、上の表）: 旅人が技・加入の身振り・起き上がりを始めたとき、その旅人を見ている全員へ。クライアントは
  体のアニメーション（`TravellerEntity.startAction`）と光の演出（`client.HeroFx`）を始める。ダメージ・回復・効果はすべてサーバーが
  決め、このパケットは見た目だけ。デコード時に action（0〜127）と座標（有限の数）を検証する。
- S→C `UiDataPacket` の `dialogue` に省略可能な `choices: ["<翻訳キー>", ...]`（最大4つ。問いかけ。[modules/story.md](modules/story.md)）。
- S→C `UiDataPacket` の `party` の `members` に仲間の行: `{id, companion: "<rosalind|siegwald|kaylen|mariel>", name, nameKey,
  titleKey, leader: false, online: true, hp, maxHp, down, level: 100, job}`。ほかのプレイヤーとパーティーを組んでいなくても、仲間がいれば
  本人の行と仲間の行の `party` を送る。
- C→S `UiActionPacket`: `dialogue.choice {id, choice}`（id は 1 以上、choice は 0〜3。範囲外は違反。問いが無い・期限切れの id は無視）、
  `traveller.dismiss {id}`（id は旅人の id、32文字まで。仲間でなければ何もしない）。

## 付録: スキルの光（版 10: `SkillFxPacket` を追加）

- S→C `SkillFxPacket`（ID 13、上の表）: 職業スキルの地面の領域（`SkillZones.create`。武技の領域は従来どおり武技の演出）ができたとき、
  またはスキルの魔法・矢が爆発したとき（`CombatHits.explosion`）、その位置から 64 ブロック（領域は＋半径）以内の全員へ。クライアントは
  領域の一生分の光（`job.fx.Fields.zone`）か爆発の光（`Fields.blast`）を描く（`client.SkillFx`）。見た目だけで、ダメージ・回復・効果は
  サーバーが決める。デコード時に kind・theme・座標（有限・ワールド内）・yaw・半径・tick・脈動数・flags を検証し、外れれば切断。
- スキルの行動そのものの光は既存の `PlayerActionPacket`（行動ID・時間）から各クライアントが組み立てる（新しいデータなし）。
- スキルの魔法弾（`MagicProjectile`）と矢（`SkillArrow`）にエンティティ同期データ `THEME`（byte、-1=なし）を追加。クライアントが飛ぶ光の
  テーマに使う。

## 付録: ステータス％（版 11: `CharacterSnapshotPacket` に装備の％）

- `CharacterSnapshotPacket`: スキル枠の後に**装備のステータス％**（STR・VIT・AGI・INT・DEX・LUK の順に VarInt 6つ、千分率 =
  0.1% 単位。0〜100,000）。直前の「職業と装備の上乗せ」（bonusStats）は固定値と％の上乗せを合わせた値になった。
- 送るとき: 従来どおりキャラクターが変わったとき（`CharacterSync`）に加えて、装備で能力が変わったとき（`CombatDriver` の能力の
  再計算。装身具・防具・武器の付け外し、鍛冶屋の強化・再錬成）にも送る。
- UI データ `trinkets`: `stacks` が6つに（`TrinketSlots.Slot` の保存順: 飾1・飾2・守1・飾3・飾4・守2）。古いクライアントは無い
  （版が違えばログインできない）。
- UI 操作: `bag.equip` / `bag.unequip` の `target` に `accessory_3`・`accessory_4`・`charm_2`、`smith.enhance` / `smith.reroll` の
  `slot` に 41〜46（身に着けた装身具）。

## 付録: 見切り（版 12: `CombatStatsPacket` に evasion）

- `CombatStatsPacket`: 22個の double の後に **evasion**（double、0〜0.30。攻撃速度の上限を超えた分から。`CombatStats.evasion`）。
  最大 23×8 = 184 B。

## 付録: 転移門の画面（2026-11-13・パケット追加なし・版は変えない）

門番のチェストのメニュー（バニラのコンテナ）をやめ、既存の `UiDataPacket` / `UiActionPacket` で専用の画面にした
（[modules/boss.md](modules/boss.md) §4「門番の画面」）。

- S→C UI データ `gatekeeper`（門番に話しかけたとき）: `{focus, level, inParty, leader, partySize, conquered, dungeons,
  rewards: [{rarity, color, stones} ×3（normal, hard, ex）], entries: [{id, name, area, boss, level, min, max, minutes, rooms,
  raid, open, conquered, unlock, refusal, hp: [×3], dmg: [×3], best: [×3]} ×9]}`。`name`・`area`・`boss`・`unlock`・`rarity` は
  翻訳キー、`refusal` は `""`／`sealed`／`leader`／`party_size`、`best` は自己ベストの tick（なければ -1）。約4 KB。
- C→S UI 操作 `gate.start`: `{id: "d1".."d8"|"final", difficulty: "normal"|"hard"|"ex"}`。どちらも `UiArgs.id` で検証し、
  ダンジョンでない id・知らない難易度は違反。門番が開いた画面（5分以内・8ブロック以内・同じ次元）からだけ受け付ける。
- S→C UI データ `gatekeeper.result`: `{ok}`、断ったときは `message`（コンポーネントの JSON）。

### 出発の準備（2026-11-13・パケット追加なし）

- 転移門の `gate.start` が2人以上のパーティのリーダーから来ると、すぐには始めず出発の準備を始める（`ok` の返事はそのまま）。
- S→C UI データ `ready_check`: 確認中は `{active: true, open, id, name, difficulty, phase: "waiting"|"countdown", left, leader,
  members: [{id, name, ready, leader}]}`（`open` は画面を開くとき、`left` は残りの tick）。答えが変わる・秒読みが始まる／止まる
  たびに全員に送る。終わると `{active: false, outcome: "go"|"cancelled"|"changed"|"timeout"|"failed", message}`。
- C→S UI 操作 `ready.set`（`{ready: true|false}`）、`ready.cancel`（リーダーだけ）、`ready.open`（画面をもう一度）。確認中でない人や
  リーダーでない人の中止は違反ではなく、アクションバーに理由。同じことをチャットのボタンが `/dungeon ready ok|wait|open|cancel` で行う。

