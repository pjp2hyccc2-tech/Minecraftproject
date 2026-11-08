# ETERNAL REALM

**14 DAYS TO ETERNITY.** — Minecraft Java Edition 1.20.1 / Forge 47.x 上で動く、オンライン・ファンタジーアクションRPG（MOD＋専用ディメンション）。

> 開発状況（0.5.0）: 企画書の主要システムを**すべて実装済み**。CI（ビルド・ユニットテスト約780件・ヘッドレスサーバーの GameTest）は合格。
> **実機クライアントでの目視・プレイ確認とマルチプレイ確認はまだ**です（見た目・音・手触りの調整が必要な箇所があり得ます）。
> 詳細は [docs/ROADMAP.md](docs/ROADMAP.md) と [docs/TEST_PLAN.md](docs/TEST_PLAN.md)。

## 遊べるもの

- **世界アストリア**: 専用ディメンション。王都アルセリアを中心に7地域（エルダーフォレスト、サンドリア砂漠、ノルディア雪原、ドラゴニア火山、
  セレスティア天空大陸、終焉の塔）が同心円状に広がり、24種のバイオームと14種の地形装飾。ボス撃破で次の地域が解放（シーズン単位）
- **6職業**: ナイト／アサシン／メイジ／レンジャー／プリースト／魔剣士。職業ごとに4スキル（スキルツリー・ポイント）、専用の衣装（職業衣装表示）
- **アクション戦闘**: 弱/強攻撃・コンボ・回避・ガード・パリィ・武器スキル・職業スキル。6武器種（片手剣・両手剣・双剣・槍・杖・弓）
- **装備**: 7ティア×10部位＝70種（専用の32px武器アート）、レア度5段階・付加効果・強化（+10）・厳選・製作
- **モンスター**: 地域ごとに62種（33種は専用アニメーション付きの体）、レベル帯・エリート・ドロップ表、LUKで良いドロップ
- **ダンジョン8つ＋最終レイド**: 自動生成のインスタンス（9テーマ）、ウェーブ・扉・ボス戦（予兆・フェーズ・シールド・召喚・激昂）、
  ノーマル/ハード/EX、タイムアタック
- **ボス9体**: 専用のアニメーション付きモデル。最終ボス「終焉の王」は10〜20人・4フェーズ（設定で少人数も可）
- **ストーリー**: プロローグ〜全9章〜エンディング（スタッフロール）。地域カード、ボスの台詞、町の人々の会話が進行で変化
- **クエスト41件**: メイン・サイド・デイリー。クエスト受注はクエストNPCとの会話または冒険手帳
- **町**: 道具屋・鍛冶屋・取引所（オークション）・郵便・ギルド受付・門番（ダンジョン）など
- **パーティー（5人）/レイド（20人）・ギルド・ランキング（9部門）・シーズン（14日）・英雄の記録**
- **音楽と効果音**: 地域・ダンジョン・ボス・最終ボス・エンディングのオリジナル曲、ジングル、47種の効果音
- **シェーダー**: Oculus＋Embeddium とシェーダーパックで動作する前提の見た目（粒子・発光は標準の描画経路のみ使用）

## 操作（初期設定）

| キー | 内容 |
|---|---|
| 左クリック / R | 弱攻撃（コンボ） / 強攻撃（長押しで溜め） |
| 右クリック長押し | ガード（タイミングよく受けるとパリィ） |
| 左Alt | 回避 |
| Z | 武器スキル |
| V / G / B / H | 職業スキル1〜4 |
| K | 冒険手帳（ステータス・職業・スキル・クエスト・パーティー・ギルド・ランキング） |
| クリック / Space / Enter | NPCとの会話を進める（Esc で飛ばす） |
| Enter | ホットバー上の会話ボックスを進める |

キー割り当てはオプション → 操作設定 → ETERNAL REALM で変更できます。

## 主なコマンド

- `/party invite|accept|leave|raid|...`, `/p <メッセージ>` — パーティー
- `/guild create <タグ> <名前>|invite|accept|leave|...`, `/g <メッセージ>` — ギルド
- `/auction`（取引所）, `/auction sell <価格>`（手に持ったアイテムを出品）, `/mail`（郵便受け）
- `/dungeon leave` — ダンジョンから離脱
- `/ranking [部門]`, `/season`
- `/eternalrealm outfit job|tier` — 防具を職業衣装／装備本来の外見で表示
- 管理者: `/eternalrealm setup town`（アストリアのない世界で町のNPCを配置）, `/er admin ...`

## 導入

1. Minecraft 1.20.1 + Forge 47.x（CurseForge のプロファイルなど）
2. `eternalrealm-<版>.jar` を `mods` フォルダへ
3. （任意）影MOD: CurseForge で **Oculus** と **Embeddium** を追加し、シェーダーパック（例: Complementary Shaders、BSL）を `shaderpacks` に入れる
4. サーバー設定: `config/eternalrealm-server.toml`（シーズン、モンスター出現、`keepInventory`、少人数でのダンジョン `ignorePartySize` など）

## ビルド

```bash
./gradlew build                 # コンパイル + ユニットテスト + build/libs/eternalrealm-<版>.jar
./gradlew runGameTestServer     # ヘッドレスサーバーでMODを起動し、ゲーム内テストを実行
./gradlew runClient             # 開発用クライアント
```

ネットワークが制限された環境では、Minecraft非依存部分だけを `tools/offline-test.sh` でコンパイル・テストできます。
アート・音楽・ワールド生成データは `tools/art`, `tools/art_entities`, `tools/audio`, `tools/gen-worldgen-data.py` などの生成スクリプトで再生成できます（出力は毎回同一）。

## 構成

```
src/main/java/com/eternalrealm/
  core/ season/ character/ player/ persistence/ network/ combat/ job/ item/ economy/ party/ guild/
  ranking/ quest/ world/ monster/ boss/ dungeon/ story/        … Minecraft非依存（JUnitで検証）
  */forge/ server/ bootstrap/ config/ menu/ npc/ entity/ sound/ gametest/   … Forge連携
  client/ ui/                                                                … クライアント専用
```

## ドキュメント

| 文書 | 内容 |
|---|---|
| [GAME_DESIGN](docs/GAME_DESIGN.md) | 統合ゲーム仕様（2.0反映） |
| [STORY](docs/STORY.md) / [ART](docs/ART.md) / [AUDIO](docs/AUDIO.md) | 物語・アート・音楽 |
| [ARCHITECTURE](docs/ARCHITECTURE.md) / [TECHNICAL_DESIGN](docs/TECHNICAL_DESIGN.md) | 構成・依存ルール・技術設計 |
| [COMBAT_SYSTEM](docs/COMBAT_SYSTEM.md) / [RANKING_SYSTEM](docs/RANKING_SYSTEM.md) / [SEASON_SYSTEM](docs/SEASON_SYSTEM.md) | 各システム |
| docs/modules/*.md | モジュールごとの設計（boss, entity, party, ranking, story, worldgen） |
| [TEST_PLAN](docs/TEST_PLAN.md) | テスト層・チェックリスト・手動手順 |
| [ROADMAP](docs/ROADMAP.md) / [DECISIONS](docs/DECISIONS.md) / [DEVELOPMENT_LOG](docs/DEVELOPMENT_LOG.md) | 計画・設計判断・作業記録 |

## ライセンス

All Rights Reserved. アート・音楽・モデルはすべてこのリポジトリの生成スクリプトによるオリジナルです（Minecraft のアセットは同梱していません）。
