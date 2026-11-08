# ネットワークプロトコル

## 1. チャンネル

| 項目 | 値 |
|---|---|
| チャンネル | `eternalrealm:main`（Forge SimpleChannel） |
| プロトコル版 | `"1"`（`ERNetwork.PROTOCOL_VERSION`） |
| 互換性 | クライアント・サーバーとも**完全一致のみ受け入れ**。MOD未導入のクライアントは接続時に拒否 |
| 版上げ条件 | パケットの追加・削除・順序変更・フィールド変更のいずれか |

パケットIDは `ERNetwork.register()` の登録順で決まる（0から連番）。順序を変えたら必ず版を上げる。

## 2. パケット一覧

| ID | 名前 | 方向 | フィールド | 最大サイズ | 処理スレッド |
|---|---|---|---|---|---|
| 0 | `CharacterSnapshotPacket` | S→C | seasonId (UTF, ≤32), level (VarInt), levelCap (VarInt), experience (VarLong), experienceToNext (VarLong, 上限時0), unspentStatPoints (VarInt), allocated×6 (VarInt), total×6 (VarInt) | 512 B | クライアント |
| 1 | `AllocateStatPacket` | C→S | statNetworkId (VarInt: 0=STR 1=VIT 2=AGI 3=INT 4=DEX 5=LUK), amount (VarInt) | 10 B | サーバー |

ステータスの並び順は `StatType` の宣言順（ネットワークIDは固定値で、再利用・並べ替え禁止）。

## 3. 受信時の検証

### C→S（すべてのパケット共通: `ServerPacketGuard`）
1. デコード前: ペイロードが最大サイズを超えたら `DecoderException`（接続切断）
2. 送信者（`NetworkEvent.Context#getSender`）が存在すること。ペイロード中のプレイヤー識別子は使わない
3. パケット種別ごとのトークンバケット（既定: バースト10、毎秒5）。超過は違反として記録
4. セッションが `ACTIVE`（データ読込済み）でなければ破棄（違反にはしない）

### AllocateStatPacket 固有
| 状況 | 扱い |
|---|---|
| 未知の statNetworkId | **違反** |
| amount < 1 または > 100（プロトコル上限） | **違反** |
| amount > サーバー設定の上限（≤100） | 違反にしない。メッセージ＋再同期 |
| ポイント不足（連打などの正当な競合） | 違反にしない。メッセージ＋再同期 |
| 成功 | tick終了時にスナップショット送信 |

### 違反
- `ViolationTracker`: 既定で60秒ウィンドウ内に20回で切断（`network.violationKickThreshold`、0で切断しない）。
- すべてWARNログ（名前・UUID・理由・回数）。

### S→C
- クライアント側でもデコード時に値域を検証（不正値は `DecoderException`）。
- クライアントは受信値を表示するだけで、ローカル計算はしない。ログアウト時に破棄。

## 4. 同期のタイミング

サーバーは1tick内の変更をまとめ、tick終了時に変更のあったプレイヤーへ1回だけスナップショットを送る（`SyncQueue`）。
送信契機: 読込完了、EXP獲得、割り振り、リセット、レベル変更、設定再読込、拒否時の再同期。

## 5. 今後のパケット設計ルール（Phase 2 以降）

- C→S は「意図」のみ（例: 攻撃入力、スキル使用要求）。ダメージ・座標の確定値は送らせない。
- 時刻はサーバーtickで判定する。クライアント時刻は参考値としてのみ扱う。
- 高頻度パケットは専用のレート制限を設け、`ServerPacketGuard` を必ず通す。
- 他プレイヤーへの配信（戦闘演出等）は範囲を絞る（`PacketDistributor.TRACKING_ENTITY` 等）。
