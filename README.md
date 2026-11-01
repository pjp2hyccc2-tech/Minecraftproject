# FROSTBOUND: THE LAST HEARTH

Minecraft 1.20.1 / Forge 47.4 向けの都市運営サバイバルMOD。極寒の世界で、人類最後の都市を100日間存続させる。

## 状態

BUILD 0（技術調査と設計）。このブランチはビルド経路を確かめる最小構成で、ゲーム機能はまだありません。

## 構成

| パス | 内容 |
| --- | --- |
| `core/` | シミュレーション本体（純Java、Minecraft非依存、JUnitで検証） |
| `src/main/` | Forge側（登録・ワールド・通信・描画）。`core` を内包してjarにする |
| `ci/` | CI設定（サーバー起動セルフテストなど） |
| `.github/workflows/frostbound.yml` | GitHub Actions のビルド定義 |

## ビルド

GitHub Actions が `frostbound` ブランチへの push ごとにビルドし、結果（jar・ログ・テスト結果）を `frostbound-ci` ブランチに出力します。

手元でビルドする場合は JDK 17 を入れて `./gradlew build` を実行します。jar は `build/libs/` にできます。

## 動作環境

- Minecraft Java Edition 1.20.1
- Forge 47.x（47.4.0 以上を推奨）
- Java 17
