# ETERNAL REALM

**14 DAYS TO ETERNITY.** — Minecraft Java Edition 1.20.1 / Forge 上で動く、本格オンラインファンタジーアクションRPG（MOD＋専用ワールド）。

> 開発状況: **Phase 0（基盤）〜 Phase 1（キャラクターデータ）を実装済み、実環境での起動確認待ち**。
> 詳細は [docs/ROADMAP.md](docs/ROADMAP.md) と [docs/TEST_PLAN.md](docs/TEST_PLAN.md)。

## 現在できること（Phase 1）

- プレイヤーごと（UUID基準）のキャラクター: レベル（上限50・設定可）、EXP、6ステータス（STR/VIT/AGI/INT/DEX/LUK）
- 敵対Mob撃破でEXP獲得（暫定）、レベルアップ演出、ステータスポイントの割り振り（`K` キーのキャラクター画面）
- 左上HUD（レベル・EXPバー・未割り振りポイント）
- サーバー権威: 割り振りはサーバーが検証。不正なリクエストはレート制限・違反カウントで切断
- 保存: 原子的書き込み＋前世代、破損ファイルの隔離と自動復旧、オートセーブ、停止時フラッシュ、zipスナップショット
- 管理コマンド: `/er character`, `/er admin exp add|level set|stats reset|status|save|backup`

ステータスの戦闘効果は Phase 2（戦闘システム）で有効になります。

## 必要環境

- JDK 17（Gradle toolchain が自動取得することもあります）
- 初回ビルド時にインターネット接続（Forge・Minecraft の取得）
- メモリ 4GB 以上推奨

## ビルドと実行

```bash
./gradlew build                 # コンパイル + ユニットテスト + build/libs/eternalrealm-0.1.0.jar
./gradlew runGameTestServer     # ヘッドレスサーバーでMODを起動し、ゲーム内テストを実行
./gradlew runClient             # 開発用クライアント
./gradlew runServer             # 開発用専用サーバー（初回は run/eula.txt を eula=true に）
```

ネットワークが制限された環境では、Minecraft非依存部分だけを `tools/offline-test.sh` でコンパイル・テストできます。

## 構成

```
src/main/java/com/eternalrealm/
  core/ season/ character/ player/ persistence/ network/   … Minecraft非依存（JUnitで検証）
  */forge/ server/ bootstrap/ config/ gametest/             … Forge連携
  client/ ui/                                               … クライアント専用
```

## ドキュメント

| 文書 | 内容 |
|---|---|
| [GAME_DESIGN](docs/GAME_DESIGN.md) | 統合ゲーム仕様（2.0反映） |
| [ARCHITECTURE](docs/ARCHITECTURE.md) | モジュール構成・依存ルール・スレッド・フロー |
| [TECHNICAL_DESIGN](docs/TECHNICAL_DESIGN.md) | 技術スタック・永続化・バックアップ/復旧・設定 |
| [DATABASE_SCHEMA](docs/DATABASE_SCHEMA.md) | データ分類・ファイル形式・PostgreSQL案 |
| [NETWORK_PROTOCOL](docs/NETWORK_PROTOCOL.md) | パケット仕様と検証 |
| [COMBAT_SYSTEM](docs/COMBAT_SYSTEM.md) / [SEASON_SYSTEM](docs/SEASON_SYSTEM.md) / [RANKING_SYSTEM](docs/RANKING_SYSTEM.md) | 今後のフェーズの設計 |
| [TEST_PLAN](docs/TEST_PLAN.md) | テスト層・チェックリスト・手動手順 |
| [ROADMAP](docs/ROADMAP.md) / [DECISIONS](docs/DECISIONS.md) / [DEVELOPMENT_LOG](docs/DEVELOPMENT_LOG.md) | 計画・設計判断・作業記録 |

## ライセンス

All Rights Reserved.
