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
- 純粋モジュール: `javac --release 17 -Xlint:all -Werror` で警告ゼロ。JUnit **95件すべて成功**。
- 意図的なバグ混入（5種）でテストが失敗することを確認。世代チェック削除が検出されなかったため、テストを1件追加して検出を確認。
- ArchitectureTest がメインクラスのコメント中のパッケージ名を誤検出 → コメントを除外する方式に改善し、実コードの違反は引き続き検出されることを確認。
- MOD全体（Forge依存部分を含む）を、使用API のスタブに対して型チェック（警告ゼロ）。※スタブは自作であり実APIでの保証ではない。
- 設計中に発見: サーバー停止中の `MinecraftServer#execute` は呼び出しスレッドで即時実行されるため、I/O完了通知に使うと停止処理と競合する
  → 自前の `MainThreadQueue` に変更（D-018）。
- 設計中に発見: GameTest の `succeedWhen` 内で失敗を投げると「再試行」扱いになり、後段で誤って成功し得る → 失敗を保持して毎tick再送出する方式に修正。

### 未実施・未実装（完成扱いにしていないもの）
- ⏳ 実Forgeでの `./gradlew build`、`runGameTestServer`、`runClient`、`runServer`、マルチプレイテスト（TEST_PLAN.md 3章 #4〜#9）
- ⏳ PostgreSQL バックエンド（Phase 1.5、D-008 要確認）
- TODO(Phase 4): 暫定EXP源 `InterimKillExperience` の置き換え（D-014）
- TODO(Phase 3): 職業、再振り分け、プレイヤーデータ参加者インターフェース
- TODO(Phase 2): ステータスの戦闘反映

### 次の作業
1. 実環境（またはCI）で TEST_PLAN.md 3章 #4〜#9 を実施し、問題を修正
2. Phase 1 完了レビュー
3. Phase 2（戦闘）詳細設計
