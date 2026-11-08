# 開発ログ

## 2026-11-08 — Phase 0〜1（基盤とキャラクターデータ）

### 環境調査
- 作業環境: Linux、JDK 21（`--release 17` でJava 17 API に限定してコンパイル）、Gradle 8.14.3、Maven 3.9 がローカルに存在。
- **外部通信が遮断**（maven.minecraftforge.net、Maven Central ともに 403）。Forge/Minecraft の依存を取得できないため、
  Forge依存部分の実APIでのコンパイル・起動テストはこの環境では不可能。PostgreSQLサーバー・Dockerデーモンもなし。
- GitHubリポジトリ `Minecraftproject` は README のみ（既存実装なし）。作業はブランチ `feature/phase0-1-foundation`。
- Forge 1.20.1 の推奨版は 47.4.0（4番目の推奨ビルド）。公式 MinecraftForge リポジトリ（1.20.1ブランチ）から MDK と主要APIのソースを git で取得し、
  シグネチャ確認の根拠とした（`FMLJavaModLoadingContext` のコンストラクタ注入は 2024-09 のバックポートで 47.4.0 に含まれる）。

### 実装
- Phase 0: 公式MDK準拠のビルド構成（Gradle 8.8 wrapper、FG6、official mappings）、mods.toml、メインクラス、設定、ログ、Git、GitHub Actions。
- Phase 1: UUIDセッション、アカウント、キャラクター（レベル・EXP・6ステータス）、サーバー検証、JSON永続化（原子的書き込み・前世代・隔離）、
  スナップショットバックアップ、ネットワーク基盤（チャンネルv1、サイズ・送信者・レート・セッション検査、違反→切断）、HUD、キャラクター画面、
  コマンド、暫定EXP源、GameTest。

### 検証結果
- 純粋モジュール: `javac --release 17 -Xlint:all -Werror` で警告ゼロ。JUnit **101件すべて成功**（レビュー対応後）。
- 意図的なバグ混入（5種）でテストが失敗することを確認。世代チェック削除が検出されなかったため、テストを1件追加して検出を確認。
- ArchitectureTest がメインクラスのコメント中のパッケージ名を誤検出 → コメントを除外する方式に改善し、実コードの違反は引き続き検出されることを確認。
- MOD全体（Forge依存部分を含む）を、使用API のスタブに対して型チェック（警告ゼロ）。※スタブは自作であり実APIでの保証ではない。
- 設計中に発見: サーバー停止中の `MinecraftServer#execute` は呼び出しスレッドで即時実行されるため、I/O完了通知に使うと停止処理と競合する
  → 自前の `MainThreadQueue` に変更（D-018）。
- 設計中に発見: GameTest の `succeedWhen` 内で失敗を投げると「再試行」扱いになり、後段で誤って成功し得る → 失敗を保持して毎tick再送出する方式に修正。

### 独立コードレビュー（別エージェント、Forge 47.4.0 実ソース・パッチと照合）
結果: **BLOCKER（コンパイル不可・起動不可）なし**。Forge/バニラAPIの呼び出しは 47.4.0（commit `753d68d` "1.20.1 RB 4"）と整合。
データ安全性の指摘は以下の通りすべて修正し、回帰テストで「修正前のコードでは失敗する」ことを確認した。

| # | 重要度 | 指摘 | 対応 |
|---|---|---|---|
| 1 | MAJOR | 保存中のオートセーブAが失敗し、後続のログアウト保存Bが成功すると、Aが「未保存」として残り後でBを上書き（巻き戻り。レビュー側で再現） | 保存に通し番号を付与。最新の保存の失敗だけを保持し、成功時は番号以下の未保存を破棄（D-016） |
| 2 | MAJOR | `Files.exists` は判定不能でも false → 新規キャラクター作成 → 実データ上書きの恐れ | `NoSuchFileException` のみを「無し」とし、他は読み込みエラー（D-023） |
| 3 | MINOR | 改名方式の前世代管理で、保存途中のクラッシュ時に1世代前へ戻る | コピー方式に変更し `X` を常に存在させる（D-024） |
| 4 | MINOR | 停止時、リモートプレイヤーのログアウトイベントは確実には発火しない（コメントの前提が誤り） | コメント修正、停止開始時に保存を先行投入 |
| 5 | MINOR | コンテキスト破棄がサーバーに紐付いていない | 所有サーバーを確認して破棄 |
| 6 | MINOR | 連打・キーリピートでレート制限→違反→正規プレイヤー切断の恐れ | 破棄は違反扱いせずフラッド閾値で判定、クライアント側で4回/秒に制限（D-026） |
| 7 | MINOR | 停止中に失敗した最終保存がログ出力のみ | 同期的に1回再試行し、だめなら `emergency/` へ退避（D-025） |

追加で、レビュー対応のテストに含まれていた競合（ゲート設定順による間欠失敗）を発見して修正し、6回連続で安定を確認。

### 実Forgeでの検証（GitHub Actions、同日）
- ブランチ `feature/phase0-1-foundation` をpush。ワークフローは同リポジトリの別プロジェクトの `build.yml` とパスが重なっていたため `eternalrealm.yml`（ETERNAL REALM build）に改名。
- 1回目: **Forge 47.4.0 での全コードのコンパイル・ユニットテスト・jar生成は成功**。GameTestサーバーはMODのサービス起動まで成功したが、
  GameTest用構造物が見つからずクラッシュ。原因はログの `Missing metadata in pack mod:eternalrealm`：**`pack.mcmeta` が無いとForge 47.x はMODのリソース／データパックを丸ごと無効にする**
  （クライアントでは翻訳も読まれなくなる）。公式MDKの配布zipにはForgeのビルド時に生成される `pack.mcmeta` が含まれるが、Git上のMDKフォルダには無く、それを雛形にしたため欠落していた。
  → `pack.mcmeta`（pack_format 15）を追加し、再発防止に `ResourcesTest` を追加。
- 2回目: **成功**。`All 2 required tests passed`。実サーバー上でキャラクター作成→EXP 1000でLv4→ログアウト保存→再読込で一致、停止時フラッシュとスナップショット作成も確認。
- コンパイル警告1件（`ResourceLocation(String,String)` が削除予定の非推奨）→ 47.4.0 にバックポート済みの `ResourceLocation.fromNamespaceAndPath` に変更。
- 配布用jar（再難読化済み・SHA-256検証済み）をユーザーの CurseForge インスタンス（Minecraft 1.20.1 / Forge 47.4.28）の `mods` に配置。
  この作業環境はCI成果物の直接ダウンロードがプロキシで遮断されるため、CIログにbase64で出力したjarを復元しハッシュで照合した。

### 未実施・未実装（完成扱いにしていないもの）
- ✅ 実Forgeでの `./gradlew build`、`runGameTestServer`（上記）
- ⏳ クライアントでのプレイ確認、専用サーバー、マルチプレイテスト（TEST_PLAN.md 3章 #6〜#9）
- ⏳ PostgreSQL バックエンド（Phase 1.5、D-008 要確認）
- TODO(Phase 4): 暫定EXP源 `InterimKillExperience` の置き換え（D-014）
- TODO(Phase 3): 職業、再振り分け、プレイヤーデータ参加者インターフェース
- TODO(Phase 2): ステータスの戦闘反映

### 次の作業
1. 実環境（またはCI）で TEST_PLAN.md 3章 #4〜#9 を実施し、問題を修正
2. Phase 1 完了レビュー
3. Phase 2（戦闘）詳細設計
