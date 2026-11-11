# 屋敷（estates）: 宿屋の客室とギルドハウスの土台

オーナーの依頼（2026-11-11）:「プレイヤーハウスは宿屋、ファンタジーなマンションみたいにして、主に荷物の管理をする場所にする。
各々がその部屋にテレポートできるように設定する。」

**屋敷（estate）** は、専用の次元 `eternalrealm:estates` に建つ個人やギルドの場所。王都の宿屋の館の扉から入る **客室**（`room`）と、
ギルドの **ギルドハウス**（`guild`、別の担当が作る）が、同じ仕組み（この文書の「核」）の上に乗る。

| 場所 | 内容 |
|---|---|
| `estate`（純粋） | `EstateKey`、`EstatePlot` / `EstatePlots`（区画と割り当て）、`EstateRecord` / `EstateRegistry`（誰がどの区画）、`EstateData` / `EstateRepository`、`EstateStatus`、`EstateResult` |
| `persistence.file` | `EstateCodec`（`estates.json` schema 1）、`FileEstateRepository` |
| `estate.forge` | `EstateService`（API と次元の見張り）、`EstateManager`（建築・移転・解体の作業）、`EstateProtection`、`EstateContents`（保管物の移動）、`EstateWorldState`（建っている物と預かり品）、`EstateLevels`、`EstateBlueprints`、`RoomDoorBlock` / `RoomDoors`、`EstateCommands` |
| `world.forge.blueprint` | `Blueprint` / `BlueprintPlacer`: 王都と屋敷で共通のブループリント読み込みと段階的な設置（`CapitalBlueprint` / `CapitalBuilder` もこれを使う） |
| `tools/estates/` | 間取りの Python（王都のキット `tools/capital` を使う）: `kit.py`（保管家具・扉・スポット）、`rooms.py`（客室）、`build.py`、`check.py`、`views.py`（プレビュー） |
| `tools/art/estate_blocks.py` | 客室の扉のテクスチャ・モデル・ブロックステート（オリジナルのドット絵） |

## 1. 次元と区画

- **次元** `eternalrealm:estates`（`data/eternalrealm/dimension(_type)/estates.json`）: 空の虚空（`the_void`、地形なし・自然湧きなし）、
  y 0〜319、空は地上と同じ見え方、**時刻は夕方前で固定**（`fixed_time` 10000、太陽は西に約30°）、**天気なし**（地上の雨や雷が
  この次元に届いても、サーバーが毎 tick 雨と雷を 0 に戻し、中の人には晴れを送り直す）。ベッドで眠れない（夜が来ない）。
- **区画**（`EstatePlot`）: 1024×1024 の格子。種類ごとに別の**地域**（z 方向に 2,097,152 ずつ）: 客室は地域 0、ギルドハウスは地域 1、
  ほかの種類は名前から決まる地域 2〜7。区画の原点（ブループリントのローカル 0/0/0）は格子の中心の y = 100（`EstateLevels.BASE_Y`）。
  屋敷が使えるのは原点の ±256。隣の屋敷とは最低 512 ブロック離れるので、互いに見えない。地域あたり 131,072 区画。
- **割り当て**（`EstatePlots`）: 地域ごとに一番小さい空き番号。手放された区画は**解体が終わるまで**誰にも渡さない。

## 2. 間取り（ブループリント）

形式は王都と同じ（[capital.md](capital.md) §6）: `data/eternalrealm/estates/<kind>/<design>/manifest.json` と `tiles/*.bin`。
`tools/capital` の `World` と `export.export(w, out_dir)` で作る。新しい `World` は全部 void（そのまま残す）なので、原点の周りに建てる。

```
python3 tools/estates/build.py                 # すべての間取りを作って書き出し、検査する（数秒）
python3 tools/estates/build.py room/garret     # 指定したものだけ
python3 tools/estates/build.py --views garret_in --scale 0.6   # プレビュー（tools/estates/previews/）
```

- **必須のスポット**: `entry`（着く足元の位置と向き）と `exit`（出口。客室の扉 `eternalrealm:room_door` の前、内側）。ほかのスポットは自由。
- **保管家具には名前**（`tools/estates/kit.py`）: チェスト・樽・本棚（ブロック実体）と防具立て・額縁（エンティティ）は、永続データに
  `ForgeData:{er_slot:"chest.3"}` を持つ。模様替え（§4）で中身は **同じ名前の家具の同じ枠へ** 移る。だから **上の段の間取りは下の段の
  名前をすべて含む**（`rooms.check_storage` が検査）。防具立ては壊れず（`Invulnerable`）枠は空いている（`DisabledSlots:0`）、額縁は
  固定しない（`Fixed:0b`、`Invulnerable:1b`）。
- 検査（`tools/estates/check.py`）: パレットの正しさ、ブロック実体の載る先、`entry`/`exit` の床と頭上、出口の隣の扉、保管家具の
  名前の重複と載る先、絵画・階段（王都の検査を流用）、ブロック数。GameTest `estateBlueprintsAreValid` がゲーム側で同じことを確かめる。

## 3. API（`EstateService`、サーバースレッド）

```java
EstateKey key = new EstateKey("guild", guildId, "hall");      // 種類・持ち主（プレイヤーかギルドの UUID）・間取り
EstateService.registerAccess("guild", (player, estate) -> isMember(player, estate.owner()));   // 起動時に1回
EstateService.onReady((level, estate) -> { ... });             // 建築（初回・模様替え）が終わって入れるようになったとき

EstateResult r = EstateService.claim(key);                     // 区画を取って建築開始（お金は呼ぶ側の仕事）
EstateStatus s = EstateService.status(key);                    // NONE / BUILDING(%) / READY / REBUILDING(%)
EstateService.ready(key);                                      // 入れるか
EstateService.enter(player, key);                              // 入口へ。来た場所をプレイヤーに記録。だめなら理由を表示して返す
EstateService.leave(player);                                   // 記録した場所へ（なければ王都の estate.lobby、なければ王都のスポーン）
EstateService.toCapital(player);                               // 王都の estate.lobby へ（記録は消える）
EstateService.redesign(key, "grand_hall");                     // 模様替え（§4）
EstateService.release(key);                                    // 手放す（中の人は外へ、区画は解体、残った物は預かり品へ）
EstateService.at(level, pos);  EstateService.at(player);       // その位置の屋敷のキー
EstateService.estate("guild", guildId);                        // いまのキー（間取り込み）
EstateService.estates("guild");                                // その種類のすべて
EstateService.mayAccess(player, key);                          // 持ち主・アクセス規則・クリエイティブの管理者
EstateService.spot(key, "npc.steward");                        // 間取りのスポットのワールド位置と向き（模様替え中は空）
EstateService.box(key);  EstateService.contents(key);          // 建物の範囲、保管物（EstateContents.Found: stacks()/isEmpty()）
EstateService.keepsafe(owner);  EstateService.claimKeepsafe(player, owner);   // 預かり品を見る・カバンに入る分だけ受け取る
```

- **キー**: `EstateKey(kind, owner, design)`。種類と持ち主が同じなら同じ屋敷（`sameEstate`、`id()` = `kind/owner`）。種類と持ち主
  ごとに屋敷は1つ。サービスは種類と持ち主で引き、渡されたキーの間取りは `claim` 以外では見ない。名前は `[a-z0-9_]{1,32}`。
- **結果**（`EstateResult`）: `OK`, `ALREADY_CLAIMED`, `NOT_CLAIMED`, `UNKNOWN_DESIGN`, `NO_PLOT`, `BUILDING`, `NOT_ALLOWED`,
  `UNAVAILABLE`（次元がない・データが読めない）, `SAME_DESIGN`, `NOT_EMPTY`, `BUSY`（模様替え中）。`messageKey()` が表示文。
- **アクセス**: 持ち主（プレイヤーの UUID が持ち主）と、クリエイティブモードの管理者（権限2）は常に入れる。ほかは種類ごとの
  `EstateAccess`。規則は軽く（中にいる人ごとに0.5秒ごとに呼ばれる）。
- **建築**: 1 tick 6 ms まで、1件ずつ（キュー）。区画のチャンクはチケットで読み込み、**エンティティの読み込みを待ってから** 作業する
  （防具立てや額縁の見落としを防ぐ）。設置は `BlueprintPlacer`（王都と同じ: タイル → 階段や柵の形 → ブロック実体のデータ →
  エンティティ → 液体）。終わると世界側の記録（`EstateWorldState`: 区画・間取り・ハッシュ・範囲・入口）に書く。

## 4. 模様替え・建て替えと保管物（物を失わない・増やさない）

- `redesign` は **別の区画に新しい間取りを建て**、その間も古い建物を使える（`REBUILDING`）。建ち終わったら **同じ tick の中で**:
  古い建物の保管物を新しい建物へ移す（名前が同じ家具の同じ枠へ。入りきらない物・名前のない保管物・床に落ちている物は新しい建物の
  空いているチェストや樽へ。それでも残れば持ち主の **預かり品**）→ 古い建物にいた人を新しい入口へ → 古い区画を解体キューへ。
- 移すときは必ず「元から取り出す → 新しい場所へ入れる」を1つの tick で行うので、同じ物が2か所にある瞬間も、どこにもない瞬間もない。
- **ブループリントの更新**: 間取りのハッシュが変わったら、次にその屋敷に入ったときに同じ仕組みで建て直す（中身はそのまま）。
- **解体**（手放したとき・模様替えの後）: 区画の中の人を外へ → 保管物をすべて持ち主の預かり品へ → エンティティを消す → ブロックを
  消す（空のセクションは飛ばす）→ 区画を空きに戻す。
- **預かり品**（keepsafe）: 世界側の記録（`EstateWorldState`、チャンクと同じ世界の保存で書かれる）に持ち主ごとに保管。
  `claimKeepsafe` はカバンに空きがある分だけ渡し、残りは預かったまま。
- **起動時の照合**: 世界に建っているのにどの屋敷のものでもない区画は解体（中身は最後の持ち主の預かり品へ）、記録にあるのに建っていない
  屋敷は建て直す、途中で止まった模様替えは移す作業からやり直す。

## 5. 中のルール（`EstateProtection`）

- **壊す・置く・バケツは不可**（クリエイティブの管理者を除く）。ブロックへのアイテム使用も不可（火打石・骨粉・斧で皮をはぐ等）。
- 使えるブロック: 保管（ブロック実体が `Container` のものすべて）、扉・トラップドア・柵の門、ボタン・レバー、作業台・金床・鍛冶台・
  砥石・石切台・機織り機・製図台・エンチャント台、本の載った書見台、`EstateProtection.UsableInEstates` を実装したブロック
  （客室の扉など）。ほかは何も起きない。ベッドは飾り（「日が暮れない」と表示）。
- **額縁**: 置く・回すは右クリック、左クリックで中身がカバンへ（額縁は壊れない）。**防具立て**は着せる・脱がせるだけ（壊れない）。
  どちらもその屋敷に入ってよい人だけ。
- **プレイヤー同士は戦えない**。エンダーパールとコーラスフルーツで抜け出せない。魔物と召喚した乗り物は入ってこない。
  **落とした物は消えない**（時間で消えない）。
- **見張り**（0.5秒ごと）: 入ってよくない屋敷・屋敷のない場所にいる人は外へ（`leave`）。建物から落ちた人は入口へ戻す。
  クリエイティブの管理者と観戦者は自由。
- **ログアウト**: 来た場所の記録はプレイヤー自身の永続データ（`eternalrealm_estate_return`）にあり、位置と一緒に保存される。
  ログインしたとき、入ってよい屋敷の建物の中ならそのまま。そうでなければ（手放された・追い出された・建て直し中など）`leave` と同じく
  外へ（記録した場所が使えなければ王都）。

## 6. 客室の扉（`eternalrealm:room_door`）

王都の宿屋の館の廊下に並ぶ扉（`tools/capital/blocks.py` で登録済み、バニラの扉と同じ状態: facing, half, hinge, open, powered）。
**開かない飾りの扉**（手でもレッドストーンでも開かない、壊れない）。王都で使うと、部屋を借りている人は鍵の音がして **自分の部屋** に入る
（どの扉からでも自分の部屋）。借りていない人には「宿屋の主人に話しかけよう」。屋敷の中では出口。見た目: 胡桃色の板張り、鉄の帯の蝶番、
琥珀色に灯る小窓、真鍮の取っ手・蹴り板・鍵穴、そして浮き彫りの **真鍮の部屋札**（鍵と月桂樹の彫り）。

## 7. 管理者コマンド

`/eternalrealm estate status | info <player> | visit <player> | leave | release <player>`（権限2）。

## 8. テスト

- 純粋: `EstatePlotsTest`（地域の分離・距離・位置からの区画・割り当てと再利用）、`EstateRegistryTest`（1種類1つ・模様替えの区画・
  中止・手放し・孤児・整合性）、`EstateCodecTest`（往復・壊れたデータ・新しすぎる schema・前の世代への退避）。
- GameTest `EstateGameTests`: 全間取りの妥当性、借りる → 建つ → 入る（入口・位置から屋敷）→ 出る（元の場所）→ 手放す、
  他人は入れない・中に入れられても外へ出される・壊せない置けない、部屋のない人の扉。

## 決定事項（Decisions）

- **専用の次元・遠く離れた区画**: 屋敷同士が見えず、王都の地形と干渉しない。種類ごとに地域を分け、ギルドハウスの大きさが客室に響かない。
- **保管物は世界の中の本物の家具**（チェスト・樽・防具立て・額縁）: 見て分かり、バニラの操作そのまま。模様替えは「別区画に建てて
  1 tick で移す」方式にし、建築中も部屋が使え、途中で落ちても中身は古い区画に残る（起動時の照合で移し直す）。
- **持ち主と区画は `estates.json`、建っている物と預かり品は世界側**（チャンクと同じ保存）、**戻り先はプレイヤーの永続データ**
  （位置と同じファイル）。それぞれ一緒に保存されるべき物と同じ所に置いた。クラッシュ時の一貫性はバニラのチェストと同じ水準
  （1つの tick の中の移動は、次の自動保存でまとめて保存される）。
- **ログアウトした場所に戻る**（MMO の家と同じ）。ただし入ってよい・建っている場合だけ。
- **天気なし・時刻固定**: 窓からの眺めがいつも夕方前の光（西の窓から差し込む）。雨は次元ごとの雨量を 0 に保つ方式（バニラは地上の
  雨の開始を全次元に知らせるため、中の人には晴れを送り直す）。
