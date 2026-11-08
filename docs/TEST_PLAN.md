# テスト計画

## 1. テストの層

| 層 | 対象 | 実行方法 | 自動化 |
|---|---|---|---|
| L1 ユニット | 純粋モジュール（ルール、計算、検証、コーデック、レート制限） | `./gradlew test` ／ `tools/offline-test.sh` | ○ |
| L2 統合（純Java） | `PlayerDataService` ＋実I/Oスレッド＋ファイル保存（再起動を模擬） | 同上 | ○ |
| L3 アーキテクチャ | モジュール依存ルール R1/R2 | 同上（`ArchitectureTest`） | ○ |
| L4 ゲーム内統合 | 実Minecraft+Forgeサーバーでの起動・配線・保存→再読込 | `./gradlew runGameTestServer`（CI） | ○ |
| L5 手動クライアント | HUD、画面、キー、メッセージ、コマンド | `./gradlew runClient` | 手動 |
| L6 マルチプレイ | 専用サーバー＋複数クライアント | `./gradlew runServer` ＋ クライアント2台以上 | 手動 |
| L7 負荷・長時間 | TPS、メモリ、I/O遅延、10人以上 | Phase 9/11 で計画 | — |

## 2. 自動テスト一覧（2026-11-08 時点: 95件）

| クラス | 主な検証内容 |
|---|---|
| `PlayerCharacterTest` | 複数レベル同時上昇、上限で停止・超過分破棄、巨大値のクランプ、割り振り検証、リセット、レベル変更時の返還、ルール変更時の修復、ダーティ管理 |
| `ExperienceCurveAndRulesTest` | 曲線の値・単調増加・範囲、ルールの導出値と検証 |
| `StatBlockTest` | 不変性、範囲、飽和加算、ID固定 |
| `JsonCodecsTest` | 往復変換、途中切れ・型違い・小数・未知/欠落ステータス・他人/他シーズンのファイル・新しいschema |
| `AtomicFileStoreTest` | 前世代の保持、破損時の隔離と復旧、両方破損・前世代なしでの例外、主ファイル欠落、新schemaを隔離しない、不正UTF-8、残骸tmp、書き込み失敗 |
| `FileRepositoriesTest` | 保存パス、シーズン間の分離、アカウント |
| `SnapshotBackupServiceTest` | zip内容（tmp除外）、世代削除、同一ミリ秒、非同期、配置検証 |
| `KeyedSerialExecutorTest` | 同一キーの順序・非重複、別キーの並行、失敗後の継続、待機、終了後の拒否、終了時の排出 |
| `MainThreadQueueTest` | 実行スレッド、失敗タスクの隔離、1回あたりの上限 |
| `RateLimitAndViolationTest` | バースト・補充・上限、キー独立、違反の閾値・ウィンドウ・無効化 |
| `PeriodicTriggerTest`, `SeasonIdTest` | 周期、パス安全なID形式 |
| `PlayerDataServiceTest` | 初回作成、既存読込、**読込失敗で新規作成しない**、ログアウト保存、読込中の離脱、**高速再接続で保存後に読込**、旧セッション結果の破棄、**保存失敗データのメモリ保持と優先**、オフライン分の再試行、変更分のみのオートセーブ、レベルアップ通知、割り振り検証、設定再読込、停止時フラッシュと失敗報告、**ファイル保存での再起動後の復元** |
| `ArchitectureTest` | R1/R2（コメントは除外） |
| GameTest `serverServicesAreRunning` | 実サーバーでServerContextが生成されデータディレクトリがある |
| GameTest `characterSurvivesLeaveAndRejoin` | 実サーバースレッド・実I/O・実ディスクで EXP→ログアウト→再ログインでレベルが一致 |

テストの有効性確認: 主要ロジックに意図的なバグ（順序保証の削除、未保存データの無視、破損を「データなし」扱い、世代チェック削除、依存ルール違反）を入れ、
いずれも対応するテストが失敗することを確認済み。

## 3. Phase 0 / 1 チェックリスト

凡例: ✅ 実施済み ／ ⏳ 未実施（実環境またはCIで実施が必要）

| # | 項目 | 方法 | 状態 |
|---|---|---|---|
| 1 | 純粋モジュールのコンパイル（Java 17 API、警告ゼロ） | `tools/offline-test.sh` | ✅ |
| 2 | ユニット・統合・アーキテクチャテスト 95件 | 同上 | ✅ |
| 3 | MOD全体の型整合（使用APIのスタブに対して） | 作業環境で実施（スタブは非同梱） | ✅（実APIではない） |
| 4 | 実Forge 47.4.0 でのコンパイル・jar生成 | `./gradlew build` | ⏳ |
| 5 | GameTestサーバーでの起動とGameTest 2件 | `./gradlew runGameTestServer` | ⏳ |
| 6 | クライアント起動、MOD一覧に ETERNAL REALM 0.1.0 | `./gradlew runClient` | ⏳ |
| 7 | 専用サーバー起動（`run/eula.txt` を `eula=true`） | `./gradlew runServer` | ⏳ |
| 8 | 手動テスト（下記 4章） | — | ⏳ |
| 9 | マルチプレイテスト（下記 5章） | — | ⏳ |

## 4. 手動テスト手順（Phase 1）

1. ワールド作成直後、左上HUDが「読み込み中…」→「Lv. 1」と EXPバーに変わる
2. ゾンビ等の敵対Mobを倒すと EXP が増える（ゾンビ=20）。レベルアップ時に金色のメッセージと効果音、HUDに未割り振りポイント表示
3. `K` でキャラクター画面。ポイントがある時だけ「+」が押せる。押すと数値が増え、ポイントが減る。Shift+クリックで最大10
4. コマンド: `/er character`、`/er admin exp add @s 5000`、`/er admin level set @s 10`、`/er admin stats reset @s`、`/er admin status`、`/er admin save`、`/er admin backup`
5. 「セーブして終了」→ 再度ワールドを開く → レベル・EXP・割り振りが同じ（**再ログイン後もデータが残る**）
6. `saves/<ワールド>/eternalrealm/` に JSON、`eternalrealm_backups/` に zip ができている
7. 破損テスト（ワールドを閉じた状態で）: キャラクターJSONを壊す → 開くと `.prev` から復旧し `quarantine/` に移動。`.prev` も壊すと障害ID付きで切断され、ログに同じIDが出る
8. `serverconfig/eternalrealm-server.toml` の `levelCap` をプレイ中に変更 → HUDの上限表示が変わる
9. MOD未導入のクライアントで専用サーバーに接続 → 拒否される

## 5. マルチプレイテスト（Phase 1）

1. 専用サーバー＋クライアント2台（オフラインモードなら別名）で接続
2. それぞれのHUDが自分のデータだけを表示する
3. 片方がレベルアップしても他方の表示は変わらない
4. 切断→即再接続でデータが巻き戻らない（ログアウト直後の再ログイン）
5. サーバー停止→起動後、両者のデータが残っている。停止ログに `Player data flushed` が出る
