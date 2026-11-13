# ART — テクスチャ生成ガイド

ETERNAL REALM の 2D アートはすべて `tools/art/` の Python スクリプトで生成したオリジナルのピクセルアートです
(既存作品のコピー・トレースは一切なし)。乱数は名前から求めた固定シードのみなので、何度実行しても同じファイルになります。

## 再生成

```sh
python3 tools/art/generate_all.py                      # 全アセット + tools/art/preview.png
python3 tools/art/generate_all.py --sections /tmp/prev # プレビューの各セクションも個別に出力
```

必要なもの: Python 3、Pillow、numpy。最後に自己チェック (ファイル数・サイズ・overrides の順序) が走り、失敗すると終了コード 1 になります。
確認用の一覧画像は `tools/art/preview.png` (拡大・ラベル付き) です。

## 生成物と命名 (`src/main/resources/assets/eternalrealm/` 以下)

| 種類 | テクスチャ | モデル / 定義 |
|---|---|---|
| 武器 32x32 (42 枚) | `textures/item/weapon/<family>_<n>.png` | `models/item/weapon/<family>_<n>.json` |
| 武器アイテム (tier 1) | `textures/item/<item_id>.png` (tier 1 と同じ絵) | `models/item/<item_id>.json` (overrides: `custom_model_data` 2..7) |
| 防具アイコン 16x16 | `textures/item/armor/<slot>_<n>.png`, `<slot>_job_<job>.png` | `models/item/armor_<slot>.json` + `models/item/armor/*.json` |
| 装着防具 64x32 | `textures/models/armor/er_tier<n>_layer_1/2.png`, `er_job_<job>_layer_1/2.png` | — |
| 素材 / 消耗品 / 金貨 | `textures/item/material/<id>.png`, `textures/item/consumable/<id>.png`, `textures/item/gold_coin.png` | `models/item/<id>.json` (item/generated) |
| スキルアイコン 16x16 | `textures/gui/skill/<skill_id>.png` | — |
| 職業エンブレム 16x16 | `textures/gui/job/<job>.png` | — |
| パーティクル | `textures/particle/<name>_<frame>.png` | `particles/<name>.json` |
| 状態異常アイコン 18x18 | `textures/mob_effect/{guard_up,haste,frozen,cursed}.png` | — |
| 転移門の画面（門番） | `textures/gui/gate/atlas.png`（256x256 のピクセルアート: 四隅の飾り・宝石・紋章9・錠前・月桂冠・メダル・札・リボン・ボタン・難易度の札・行の光）、`circle.png`（魔法陣 256x256）、`sealed.png`（鎖と錠前）、`d1`〜`d8.png`・`final.png`（472x200 の絵） | 絵・魔法陣・鎖は `.png.mcmeta` で `blur`（滑らかに拡大縮小） |

- family: `longsword` `greatsword` `twin_blades` `spear` `staff` `longbow`。item_id は `iron_longsword` `iron_greatsword` `iron_twin_blades` `iron_spear` `staff` `longbow`。
- tier n: 1 鉄 / 2 鋼 / 3 砂漠 / 4 氷 / 5 炎竜 / 6 天界 / 7 深淵。形 (シルエット) と装飾も tier ごとに変えています。
- 防具の slot: `helmet` `chestplate` `leggings` `boots`。`custom_model_data` 2..7 = tier、101..106 = 職業 (knight, assassin, mage, ranger, priest, spellblade の順)。
- 装着テクスチャはバニラの humanoid レイアウトです。layer_1 = 頭・胴・腕・ブーツ (脚の下部)、layer_2 = レギンス (胴の腰部分 + 脚)。帽子レイヤー (u 32..63, v 0..15) は透明のままです。
- スキルの枠の色: 騎士 青/銀、暗殺者 深紅/黒、魔導士 紫、狩人 緑、司祭 金/白、魔剣士 シアン、武器スキル ブロンズ。
- パーティクルのフレームは発生→消滅の順 (`setSpriteFromAge` 向け)。slash, rune, shadow, spark は 16x16、その他は 8x8。
- overrides は `custom_model_data` の昇順です (Minecraft は最後に一致したものを使うため)。元のアイテムモデルの `display` 設定は `models.py` に保持しています。

## スクリプト構成 (`tools/art/`)

| ファイル | 内容 |
|---|---|
| `generate_all.py` | 入口。全ファイル書き出し、プレビュー作成、自己チェック |
| `px.py` | キャンバス、アウトライン、シェーディング等の基本処理 |
| `palette.py` | 素材ごとの色ランプ (0 = 輪郭色 … 最後 = ハイライト) |
| `wbase.py`, `weapons.py` | 斜め配置の武器。軸座標 t (柄→切っ先) と s (幅方向) で形を定義 |
| `icon.py` | ASCII アートから 16x16 アイコンを描くレンダラ (自動ライティング + 輪郭) |
| `armor_icons.py`, `armor_worn.py` | 防具アイコンと装着テクスチャ (面ごとの ASCII をキューブの UV 位置へ配置) |
| `items.py`, `skills.py`, `effects.py` | 素材・消耗品・金貨、スキル/エンブレム、パーティクル/状態異常 |
| `models.py`, `preview.py`, `quickview.py` | JSON 出力、プレビュー作成、開発用の拡大表示 |
| `gate_screen.py`（`gate_scenes.py`・`gate_paint.py`） | 転移門の画面の素材。単独で実行: `python3 tools/art/gate_screen.py` → `textures/gui/gate/` と一覧 `tools/art/gate_screen_preview.png`。絵は numpy の小さな描画器（`gate_paint.py`: 形は4倍で描いて縮める、グラデーション・光・ノイズ・光のにじみ）で9つの場面（地下都市の灯籠と運河、古代樹の根のアーチ、夕日の神殿、氷の牢獄の通路、オーロラの氷の城、竜の頭の門と溶岩、雲の上の浮島と遺跡、紫の月と塔、光の環の前の玉座）。アトラスの配置は `ATLAS`（`GatekeeperScreen` の定数と同じ） |
| `chat_frame.py` | 小さなチャットの枠の素材（`textures/gui/chat/frame.png`、64×64 のピクセルアート）: 金の帯と四隅のサファイアの九分割の枠、上の辺の宝石の紋章（両側に渦巻き）、入力欄の枠（両端にサファイアの留め金）。`gate_screen.py` の金の色と浮き彫りを使う。一覧 `tools/art/chat_frame_preview.png`。配置は `ATLAS`（`ui.chat.ChatArt` の定数と同じ） |
| `phone.py` | 魔導スマホの素材（`textures/gui/phone/phone.png`、512×512）: 本体（黒檀に紫の艶・金の縁・四隅のサファイア・水晶のレンズ・ルーンのホームボタン。画面は透明）、壁紙（オーロラと月の夜の城）、アプリのアイコン5つ（フレンド・メッセージ・カメラ・つぶやき・お知らせ）、小さなアイコン12個、ボタン・タブ・吹き出し・カード・入力欄・バッジ・オンラインの点。`gate_paint.Canvas` で描く。一覧 `tools/art/phone_preview.png`。配置は `ATLAS`（`ui.phone.PhoneArt` の定数と同じ） |

絵を直すときは該当関数や ASCII を編集して `generate_all.py` を再実行し、`preview.png` で確認してください。
