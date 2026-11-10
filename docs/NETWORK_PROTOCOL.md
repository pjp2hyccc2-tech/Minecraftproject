# ネットワークプロトコル

## 1. チャンネル

| 項目 | 値 |
|---|---|
| チャンネル | `eternalrealm:main`（Forge SimpleChannel） |
| プロトコル版 | `"7"`（`ERNetwork.PROTOCOL_VERSION`）。Phase 2 で戦闘パケット追加のため 1→2、以後の追加を経て、スキルツリー（職業スキル枠5・6、職業武器の拒否、スナップショットのスキル枠）で 4→5、HUD 刷新（`UseItemPacket`、戦闘値の回復薬補正）で 5→6、カバンの取得表示（`PickupFeedPacket`）で 6→7 |
| 互換性 | クライアント・サーバーとも**完全一致のみ受け入れ**。MOD未導入のクライアントは接続時に拒否 |
| 版上げ条件 | パケットの追加・削除・順序変更・フィールド変更のいずれか |

パケットIDは `ERNetwork.register()` の登録順で決まる（0から連番）。順序を変えたら必ず版を上げる。

## 2. パケット一覧

| ID | 名前 | 方向 | フィールド | 最大サイズ | 処理スレッド |
|---|---|---|---|---|---|
| 0 | `CharacterSnapshotPacket` | S→C | seasonId (UTF, ≤32), level (VarInt), levelCap (VarInt), experience (VarLong), experienceToNext (VarLong, 上限時0), unspentStatPoints (VarInt), allocated×6 (VarInt), total×6 (VarInt) | 512 B | クライアント |
| 1 | `AllocateStatPacket` | C→S | statNetworkId (VarInt: 0=STR 1=VIT 2=AGI 3=INT 4=DEX 5=LUK), amount (VarInt) | 10 B | サーバー |
| 2 | `CombatActionPacket` | C→S | requestId (VarInt: 0=通常攻撃 1=溜め開始 2=溜め解放 3=回避 4=武器スキル), sequence (VarInt, クライアント連番) | 10 B | サーバー |
| 3 | `CombatStatusPacket` | S→C | ackSequence (VarInt), answersRequest (bool), outcome (byte: 0=受理 1=先行入力 2=無視 3〜9=各種拒否), stamina (float), skillCooldown (VarInt tick), skillCooldownTotal (VarInt tick), defenseEvent (byte: 0=なし 1=回避 2=パリィ 3=ガード 4=ガードブレイク) | 32 B | クライアント |
| 4 | `CombatStatsPacket` | S→C | attackMultiplier, maxHealthBonus, damageReduction, actionSpeedFactor, critChance, critMultiplier, maxStamina, staminaRegenPerTick（すべて double。クライアントの予測が整数tickへ丸める値をサーバーと一致させるため） | 64 B | クライアント |
| 5 | `DamageNumberPacket` | S→C | entityId (VarInt), x/y/z (float), amount (float), flags (byte: 1=クリティカル 2=強攻撃 4=スキル) | 32 B | クライアント（攻撃した本人だけに送る） |
| 6〜9 | `UiActionPacket` `UiDataPacket` `PlayerActionPacket` `TelegraphPacket` | | （各モジュールの説明を参照） | | |
| 10 | `UseItemPacket` | C→S | slot (VarInt: インベントリの 0〜35)。クイック使用（1〜3キー）とカバンの「使う」。サーバーがスロット・アイテム・レベル・クールダウンを確認（`QuickUse.useSlot`）。範囲外のスロットは**違反** | 5 B | サーバー |
| 11 | `PickupFeedPacket` | S→C | stack (ItemStack: 1個。名前・アイコン・レア度), count (VarInt, 1〜1,000,000), flags (byte: 1=カバンが満杯で足元に落ちた) | 2 KB（ItemStack の NBT 込み） | クライアント（取得した本人だけに送る） |

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
