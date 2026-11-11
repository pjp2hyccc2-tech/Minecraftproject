# 屋敷（estates）: 宿屋の客室とギルドハウスの土台

オーナーの依頼（2026-11-11）:「プレイヤーハウスは宿屋、ファンタジーなマンションみたいにして、主に荷物の管理をする場所にする。
各々がその部屋にテレポートできるように設定する。」

**屋敷（estate）** は、専用の次元 `eternalrealm:estates` に建つ個人やギルドの場所。王都の宿屋の館の扉から入る **客室**（`room`）と、
ギルドの **ギルドハウス**（`guild`、別の担当が作る）が、同じ仕組み（この文書の「核」）の上に乗る。

| 場所 | 内容 |
|---|---|
| `estate`（純粋） | `EstateKey`、`EstatePlot` / `EstatePlots`（区画と割り当て）、`EstateRecord` / `EstateRegistry`（誰がどの区画）、`EstateData` / `EstateRepository`、`EstateStatus`、`EstateResult`、客室の `RoomDesign`（5室と値段）/ `RoomRules`（借りる・移る・引き払う）、`StorageLabel`（収納の札）/ `DepositPlanner`（しまい方）/ `Ledger`（台帳の集計と検索）、`RecallRules`（帰還の判定） |
| `persistence.file` | `EstateCodec`（`estates.json` schema 1）、`FileEstateRepository` |
| `estate.forge` | `EstateService`（API と次元の見張り）、`EstateManager`（建築・移転・解体の作業）、`EstateProtection`、`EstateContents`（保管物の移動）、`EstateWorldState`（建っている物と預かり品）、`EstateLevels`、`EstateBlueprints`、`RoomDoorBlock` / `RoomDoors`、`EstateCommands`、宿屋の `InnService`（女将・扉のメニュー）、`LedgerService`（仕分け机）、`RecallService`（帰還）、`StorageChestBlock`（札付き収納箱）/ `SortingDeskBlock`、`EstateItems`（物の種類） |
| `ui.estate` / `client.estate` | `InnScreen`（女将）、`LedgerScreen`（収納台帳）、`RoomExitScreen`（扉のメニュー）、`RecallOverlay`（帰還の詠唱バー）、`EstateClient` / `EstateInput`（帰還キー Home） |
| `world.forge.blueprint` | `Blueprint` / `BlueprintPlacer`: 王都と屋敷で共通のブループリント読み込みと段階的な設置（`CapitalBlueprint` / `CapitalBuilder` もこれを使う） |
| `tools/estates/` | 間取りの Python（王都のキット `tools/capital` を使う）: `kit.py`（保管家具・扉・スポット）、`rooms.py`（屋根裏部屋と共通部品）、`suites.py`（運河・書斎・星見の塔・スイート）、`blocks_ext.py`（収納箱・仕分け机をキットに登録）、`build.py`（`--thumbnails` で女将の画面の部屋の絵）、`check.py`、`views.py`（プレビュー） |
| `tools/art/estate_blocks.py` | 客室の扉・札付き収納箱（札8種の絵）・仕分け机のテクスチャ・モデル・ブロックステート・ルートテーブル（オリジナルのドット絵） |
| `tools/art_entities/npc_cast_folk.py` | 女将ロゼッタの姿（`innkeeper`、生成器 `generate_npc.py`）。地図の印は `tools/art/map_npc_icons.py`（寝台と星） |

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
**`facing` は扉の正面（部屋札のある側）が向く方向**（バニラの扉とは逆の意味）: 館の扉は廊下の側を向き、部屋の出口は部屋の内側を向く。
置くときは置く人の方を向く。
**開かない飾りの扉**（手でもレッドストーンでも開かない、壊れない）。王都で使うと、部屋を借りている人は鍵の音がして **自分の部屋** に入る
（どの扉からでも自分の部屋）。借りていない人には「宿屋の主人に話しかけよう」。屋敷の中では **出口のメニュー**（来た場所へ戻る／王都アストリアへ／部屋に残る）。見た目: 胡桃色の板張り、鉄の帯の蝶番、
琥珀色に灯る小窓、真鍮の取っ手・蹴り板・鍵穴、そして浮き彫りの **真鍮の部屋札**（鍵と月桂樹の彫り）。

## 7. 宿屋の客室（星灯りの館）

王都の **星灯りの館** のフロント（スポット `npc.innkeeper`）に **女将ロゼッタ**（`NpcRole.INNKEEPER`、地図の印は寝台と星）。話しかけると
`InnScreen`: 5つの部屋を絵（`textures/gui/inn/<design>.png`、間取りのレンダリングから `build.py --thumbnails`）・説明・収納の数・値段で
見せる。お金の動く操作は2度押しで確定。すべてサーバー（`InnService`）が判定する（女将の近く 7 ブロック以内）。

| 部屋 | 値段 | 収納 | 眺め |
|---|---|---|---|
| 屋根裏部屋 `garret` | 1,000 G | 収納箱3・樽3・書棚2・防具立て1・額2 | 破風窓から通りと遠くの塔 |
| 運河の見える部屋 `canal` | 5,000 G | 収納箱5・樽4・書棚3・防具立て2・額4 | 出窓の下に大運河、向こう岸の家並み・街灯・橋・浮き灯籠 |
| 書斎の間 `library` | 12,000 G | 収納箱6・樽5・書棚12・防具立て2・額4 | 二層の吹き抜けと回廊、高窓から中庭の泉・花壇 |
| 星見の塔の部屋 `tower` | 25,000 G | 収納箱8・樽6・書棚12・防具立て3・額6 | 円塔の二層、星空の天井の観測室と望遠鏡、塔をめぐるバルコニー |
| 貴賓のスイート `grand` | 50,000 G | 収納箱12・樽10・書棚16・防具立て6・額10 | サロン・寝室・武具の間・宝物庫・運河のバルコニー |

- **借りる**: 1人1部屋。値段を払うと区画に建ち（数秒）、できたら知らせが届く。入るのは館の **どの客室の扉** からでも、帰還（9節）でも。
- **移る**: 差額を払う（安い部屋へは差額の半分が戻る）。収納は **同じ名前の収納へ枠ごと**、札もそのまま。小さい部屋にない収納の中身と床の物は
  空いている収納へ入り、入りきらない物は **預かり品**（女将から受け取る。画面で事前に数の見込みを表示）。
- **引き払う**: 部屋が空のときだけ。値段の半分が戻る。預かり品は部屋と別に残る。
- **出口**: 部屋の扉を使うとメニュー。「来た場所へ戻る」は入る前にいた場所（ダンジョンなど戻れない所なら王都のロビー `estate.lobby`）。

## 8. 収納と仕分け机

- **札付き収納箱** `eternalrealm:storage_chest`（27枠）: 正面の真鍮の札に絵（札なし・装備・薬・素材・食料・書物・貴重品・その他）。
  素手でしゃがんで使うと次の札へ。間取りでは番号ごとに既定の札（`kit.CHEST_LABELS`: 1 装備、2 薬、3 素材、4 食料、5 貴重品、6 その他…）。
  樽と書棚（チゼルドブックシェルフ、本だけ）は札なし。
- **仕分け机** `eternalrealm:sorting_desk`（各部屋に1つ）で **収納台帳**（`LedgerScreen`）: 部屋の収納の中身を物ごとに1行（数・どこにあるか）、
  種類のタブ、名前の検索（プレイヤーの言語で）、収納ごとの埋まり具合（クリックでその収納の中身だけ）。1スタック／全部を鞄へ取り出せる。
  **すべてしまう**: 鞄（枠1〜35、手の武器は触らない）を札に合わせて片付ける。同じ物の積み増し → 本は書棚 → その種類の札 → 札なし →
  「その他」の順（`DepositPlanner`）。回復薬・強壮剤・帰還の巻物は鞄に残す（種類のタブの「…をしまう」なら持っていく）。
  サーバーが毎回部屋を読み直して実行し、鞄に入った分だけ収納から減らす（失わない・増やさない）。物の種類は `EstateItems`。

## 9. 自室への帰還（Home キー・日誌の「自室へ」）

約3秒の詠唱（`RecallRules.CAST_TICKS` = 60 tick、HUD に真鍮の鍵が回るバー）。動く（0.35 ブロック）・攻撃を受ける・戦闘になると中断、
もう一度押すとやめる。戦闘中・ダンジョン／レイド・ボス戦・対人戦・倒れている間・部屋がない／準備中・自分の部屋の中では使えず、
着いてから 60 秒は休み。着くと戻り先が記録され、扉のメニューで元の場所へ戻れる（館の扉から入ったときと同じ）。

## 10. 管理者コマンド

`/eternalrealm estate status | info <player> | visit <player> | leave | release <player>`（権限2）。

## 11. テスト

- 純粋: `EstatePlotsTest`（地域の分離・距離・位置からの区画・割り当てと再利用）、`EstateRegistryTest`（1種類1つ・模様替えの区画・
  中止・手放し・孤児・整合性）、`EstateCodecTest`（往復・壊れたデータ・新しすぎる schema・前の世代への退避）。
- 純粋（客室・帰還）: `RoomRulesTest`（借りる・移る・差額と返金・引き払い）、`RecallRulesTest`（判定の順・中断・休み）、
  `DepositPlannerTest`（札・積み増し・本は書棚・あふれ）、`LedgerTest`（集計・検索）。
- GameTest `EstateGameTests`: 全間取りの妥当性（5室すべて）、借りる → 建つ → 入る（入口・位置から屋敷）→ 出る（元の場所）→ 手放す、
  他人は入れない・中に入れられても外へ出される・壊せない置けない、部屋のない人の扉。
- GameTest `InnGameTests`: 女将の一覧（5室・収納は増える一方）、借りる（1,000 G）→ 中身・札・防具立て・額を入れて運河の部屋へ
  （4,000 G）→ すべて同じ所に → 入りきらないほど詰めて屋根裏へ戻る（2,000 G 戻る、失わない・増えない、あふれは預かり品）→
  受け取る → 空にして引き払う（500 G）。仕分け机（札どおりに片付く・薬は残る・取り出し・種類だけしまう）。帰還（部屋なし・戦闘中は
  不可、動くと中断、着く、部屋の中では不可、扉はメニュー、休み）。

## 決定事項（Decisions）

- **専用の次元・遠く離れた区画**: 屋敷同士が見えず、王都の地形と干渉しない。種類ごとに地域を分け、ギルドハウスの大きさが客室に響かない。
- **保管物は世界の中の本物の家具**（チェスト・樽・防具立て・額縁）: 見て分かり、バニラの操作そのまま。模様替えは「別区画に建てて
  1 tick で移す」方式にし、建築中も部屋が使え、途中で落ちても中身は古い区画に残る（起動時の照合で移し直す）。
- **持ち主と区画は `estates.json`、建っている物と預かり品は世界側**（チャンクと同じ保存）、**戻り先はプレイヤーの永続データ**
  （位置と同じファイル）。それぞれ一緒に保存されるべき物と同じ所に置いた。クラッシュ時の一貫性はバニラのチェストと同じ水準
  （1つの tick の中の移動は、次の自動保存でまとめて保存される）。
- **ログアウトした場所に戻る**（MMO の家と同じ）。ただし入ってよい・建っている場合だけ。
- **客室は5室の固定の間取り**（自由建築にしない）: 荷物の管理が主目的なので、収納の名前を部屋の格ごとに増やす（大きい部屋は小さい部屋の
  名前をすべて持つ: `rooms.check_storage`）。移っても中身が同じ収納に残り、迷わない。
- **札は収納箱のブロック状態**（見た目に出る）、移るときは名前の同じ箱へ札も移す。仕分けは **その場の部屋だけ**（遠隔の倉庫にしない:
  部屋に帰る理由になる）。本の書棚・札なしの樽は「その他」より先。
- **返金は半分**（借り直しで得をしない）。**引き払いは空のときだけ**（物を失わない）。
- **帰還は詠唱・休みつき**（戦闘からの逃げ道にしない）。理由ごとの短い文で断る。
- **女将の場所は王都のスポット** `npc.innkeeper`、客室の扉は王都の書き出しにある 43 枚（どれも自分の部屋）。
- **天気なし・時刻固定**: 窓からの眺めがいつも夕方前の光（西の窓から差し込む）。雨は次元ごとの雨量を 0 に保つ方式（バニラは地上の
  雨の開始を全次元に知らせるため、中の人には晴れを送り直す）。
