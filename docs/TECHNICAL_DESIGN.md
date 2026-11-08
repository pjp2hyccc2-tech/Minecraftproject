# 技術設計書

## 1. 技術スタック（確定値）

| 項目 | 値 |
|---|---|
| Minecraft | Java Edition 1.20.1 |
| Forge | 47.4.0（1.20.1 推奨版）、`mods.toml` の要求範囲 `[47.4,)` |
| ビルド | Gradle 8.8 wrapper（公式MDK由来）、ForgeGradle `[6.0,6.2)`、マッピング official 1.20.1 |
| Java | 17（toolchain）。ソースは Java 17 API のみ（オフライン検証は `javac --release 17`） |
| JSON | Gson（Minecraftに同梱） |
| ログ | SLF4J（Forge では Log4j2 に出力）。純粋モジュールは `LoggerFactory`、Forge層は `LogUtils` |
| テスト | JUnit 4.13.2、Forge GameTest |
| CI | GitHub Actions（`.github/workflows/build.yml`） |
| DB | Phase 1: ファイル（JSON）／Phase 1.5: PostgreSQL（予定） |

## 2. サーバー権威と信頼境界

- クライアント→サーバーは**意図のみ**（例: 「STRに1ポイント振りたい」）。値の計算・検証・適用はサーバー。
- 受信時の共通検査（`ServerPacketGuard`）: 送信者の存在（ペイロード内の識別子は使わない）→ レート制限 → セッションが `ACTIVE`。
- パケット固有の検査: 列挙値の範囲、数量の上下限、ゲームルール（ポイント残量など）。
- 「正規クライアントでは起こり得ない値」（未知のステータスID、範囲外の数量）は**違反**として記録し、一定数で切断。
  正規でも起こり得る競合（連打によるポイント不足）は違反にせず、メッセージと再同期のみ。
- サーバー→クライアントは自分のキャラクターのスナップショットのみ。クライアントは表示専用。

## 3. スレッドモデル

ARCHITECTURE.md 4.2 を参照。要点:
- ゲーム状態はサーバースレッドだけが触る。I/Oスレッドには不変レコードだけを渡す。
- プレイヤー単位でI/Oを直列化（`KeyedSerialExecutor`）。保存→読込の順序が常に保たれ、高速な再接続でも古いデータを読まない。
- I/O結果は `MainThreadQueue` 経由でtickごとにサーバースレッドで適用（1tickあたり最大10,000件）。
- メインスレッドの負荷: 変更のあったプレイヤー数に比例（オートセーブはスナップショット作成のみ、書き込みは別スレッド）。

## 4. 永続化

### 4.1 Phase 1: ファイルストア

保存先: `<ワールド>/eternalrealm/`（形式は DATABASE_SCHEMA.md）。

書き込み手順（`AtomicFileStore`）:
1. `X.tmp` に書いて `fsync`
2. 既存の `X` を `X.prev.tmp` へ**コピー**して `fsync` し、`X.prev` へ原子的に改名（前世代）
3. `X.tmp` で `X` を原子的に置き換え、ディレクトリを `fsync`（POSIX）

`X` 自体は一度も改名で退避しないため、初回保存以降は常に存在する。どの時点で停止しても `X` は旧版か新版のどちらかの完全な状態で、
`X.prev` は常に完全な1つ前の世代になる（独立レビューの指摘で「改名による退避」から変更）。

読み込み時の障害ポリシー（「ファイルが存在しない」のは `NoSuchFileException` の場合だけ）:

| 状況 | 動作 |
|---|---|
| ファイルなし（`NoSuchFileException`） | 新規（初回ログイン） |
| `X` 破損 | `X` を `quarantine/` へ移動し `X.prev` から復旧（WARNログ） |
| `X` 破損かつ `X.prev` なし／破損 | **ログイン拒否**（障害ID）。新規作成しない |
| 新しい schema | ログイン拒否（隔離もしない）。MOD更新を促す |
| その他のI/Oエラー（権限、ネットワークストレージの障害、存在を判定できない等） | ログイン拒否。新規扱いにも前世代へのフォールバックにもしない |
| 隔離（移動）自体の失敗 | ログイン拒否（破損ファイルが次の保存で前世代を上書きするのを防ぐ） |

保存失敗時の扱い:
- 各保存に**通し番号**を付ける。保存内容はサーバースレッドで順番に作るため、番号が大きい保存ほど新しい内容を含む。判断は番号で行い、時計は使わない。
- I/Oスレッドで3回まで再試行（100ms, 200ms）。
- それでも失敗した場合: キャラクターがオンラインならダーティのまま次のオートセーブで再試行。ログアウト済みで、**それがそのプレイヤーの最新の保存**ならメモリに保持し、
  次回ログイン時にディスクより優先・オートセーブで再試行。より新しい保存が待機中・成功済みなら古い失敗は捨てる（古いデータでの上書き＝巻き戻りを防ぐ）。
- 停止時: プレイヤーごとに最新の保存だけを確認し、失敗していれば同期的に1回再試行。それでも保存できない、または時間内に終わらないデータは
  `eternalrealm/emergency/<uuid>-<season>-<時刻>.json`（通常と同じ形式）へ退避し、それも失敗したらERRORログにJSONを出力する。

### 4.2 Phase 1.5: PostgreSQL（予定）

`CharacterRepository` / `AccountRepository` のJDBC実装。接続プールはI/Oスレッド数に合わせる。ドライバはForgeの jarJar で同梱。
テストは GitHub Actions の `services: postgres` で同じリポジトリ契約テストを実行。スキーマ案は DATABASE_SCHEMA.md。

## 5. バックアップと復旧

| 手段 | 内容 |
|---|---|
| 前世代 | 全データファイルに `.prev`（直前の保存） |
| 隔離 | 破損ファイルは `quarantine/<名前>.<UTC時刻>.corrupt` に保存（削除しない） |
| 緊急退避 | 停止時に保存できなかったデータは `emergency/` へ。停止中に `seasons/<id>/characters/<uuid>.json` へコピーすれば復元できる |
| スナップショット | `<ワールド>/eternalrealm_backups/eternalrealm-<UTC時刻>.zip`。既定30分ごと・停止時・`/er admin backup`。既定48世代保持 |

**復旧手順（サーバー停止中に実施）**
1. サーバーを停止する
2. `<ワールド>/eternalrealm` を別名に退避する（例: `eternalrealm.broken-20261108`）
3. 目的のzipを `<ワールド>/eternalrealm` に展開する
4. サーバーを起動し、`/er admin status` と該当プレイヤーのログインで確認する
個別プレイヤーだけ戻す場合は、zip内の `seasons/<id>/characters/<uuid>.json` だけを差し替える。

## 6. 設定

サーバー（ワールドごと）: `<ワールド>/serverconfig/eternalrealm-server.toml`

| キー | 既定 | 再読込 |
|---|---|---|
| `character.levelCap` | 50 | 可 |
| `character.statPointsPerLevel` | 3 | 可 |
| `character.baseStatValue` | 5 | 可 |
| `character.maxPointsPerRequest` | 10 | 可 |
| `character.experience.curveBase` / `curveExponent` | 100 / 1.5 | 可 |
| `character.interimMobKillExp.enabled` / `expPerMaxHealth` | true / 1.0 | 可（暫定機能） |
| `season.currentSeasonId` | `s1` | 再起動 |
| `persistence.autosaveIntervalSeconds` | 60 | 可 |
| `persistence.ioThreads` | 2 | 再起動 |
| `persistence.shutdownFlushTimeoutSeconds` | 30 | 可 |
| `backup.intervalMinutes`（0で無効） / `keepSnapshots` / `snapshotOnShutdown` | 30 / 48 / true | 間隔のみ可 |
| `network.allocateStatBurst` / `allocateStatPerSecond` | 10 / 5.0 | 再起動 |
| `network.violationKickThreshold`（0で無効） / `violationWindowSeconds` | 20 / 60 | 再起動 |
| `network.floodKickThreshold`（0で無効。レート制限で破棄した要求数） | 300 | 再起動 |

ルールの再読込時は、オンライン中の全キャラクターを新ルールで再検証（はみ出した割り振りは返還）し、全員に再同期する。

クライアント: `config/eternalrealm-client.toml` — `hud.enabled`, `hud.x`, `hud.y`

## 7. ログと障害ID

- 読み込み失敗などプレイヤーに影響する障害は8桁の障害ID（例 `3FA2C91B`）を発行し、切断メッセージとサーバーログの両方に出す。
- 保存できなかったデータは `UNSAVED STATE:` を含むERRORログに全項目を出力する。停止時の最終手段は `emergency/` への退避（ログに復元手順を出力）。
- プロトコル違反はプレイヤー名・UUID・理由・ウィンドウ内回数をWARNで出力する。

## 8. エラー処理方針

- tick処理はサブシステムごとに例外を捕捉し、1つの不具合で他（オートセーブ等）を止めない。
- データ不整合はロード時に `reconcile` で修復（はみ出しは返還、進行は奪わない）し、修復した場合は保存対象にする。
- 起動時にデータディレクトリを作れない場合はサーバー起動を中止する（保存できない状態で遊ばせない）。

## 9. ビルド・テスト

| コマンド | 内容 |
|---|---|
| `./gradlew build` | コンパイル、JUnit、MOD jar（reobf） |
| `./gradlew runGameTestServer` | ヘッドレスサーバーを起動し全GameTestを実行して終了（失敗時は非0） |
| `./gradlew runClient` / `runServer` | 手動テスト（`run/` に作業ディレクトリ。サーバーは `run/eula.txt` の同意が必要） |
| `tools/offline-test.sh` | Forgeなしで純粋モジュールをコンパイル・テスト（ネットワーク制限環境用） |

## 10. この作業環境の制約（2026-11-08 時点）

- Bash からの外部通信が遮断されており、Forge/Mojang の maven から依存を取得できない。
  → Forge依存コードは**実APIでのコンパイル・起動が未実施**。実環境またはCIでの確認が必要（TEST_PLAN.md）。
- 代替として、Forge 1.20.1 ブランチの実ソース（git経由で取得）で Forge API のシグネチャを確認し、
  使用APIのスタブに対する型チェック（MOD全体、`-Xlint:all` 警告なし）を実施した。スタブはリポジトリに含めていない。
