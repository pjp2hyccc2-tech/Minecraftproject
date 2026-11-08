# パーティー・ギルド・経済（party / guild / economy）

Minecraft非依存の純粋モジュール。すべてサーバースレッド専用。時刻は引数で渡す（パーティー: サーバーtick、ギルド: `Instant`、
取引所: epochミリ秒）。ID生成の乱数は `RandomGenerator` を注入（テストで再現可能）。翻訳文は `docs/i18n/party.json`。

## 1. パーティー（`com.eternalrealm.party`）

| クラス | 役割 |
|---|---|
| `PartyService` | 全パーティーと招待。`create / invite / accept / decline / leave / kick / promote / disband / convertToRaid / expireInvites / partyOf / sameParty` |
| `Party`, `PartyMember`, `PartyInvite`, `PartyId` | 不変スナップショット（メンバーは参加順） |
| `PartyResult`, `PartyOutcome`, `PartyEvent` | 結果コード＋イベント（Created, Invited, InviteExpired, InviteDeclined, InviteCancelled, Joined, Left, Kicked, LeaderChanged, ConvertedToRaid, Disbanded） |
| `PartyRules` / `ExperienceShare`, `ShareRules` | 通常5人・レイド20人・招待1200tick ／ 経験値分配 |

- 1人1パーティー。招待・追放・リーダー譲渡・解散・レイド化はリーダーのみ。チェック順は各メソッドのJavadoc参照。
- 招待は「作成tick＋1200」の tick ちょうどで失効。招待はパーティーに属する（招待したリーダーが抜けても有効）。
- パーティーに入る（作成・参加）と他の招待は取り消し（InviteCancelled）。満員で承諾できなかった招待は期限まで残る。
- 各招待は Joined / InviteDeclined / InviteExpired / InviteCancelled のどれか**1つ**で必ず終わる（ランダム操作テストで検証）。
- リーダーが抜けると参加が最も古いメンバーが新リーダー。最後の1人が抜けると解散（Disbanded のみ）。レイド化は一方通行。
- **パーティーは保存しない**。再起動で全パーティー・招待が消える（組み直す）。オフラインのメンバーは残る（ログアウト時に抜けさせるかはForge層の判断）。

経験値分配（整数演算。合計は必ず total と一致）:
```
bonus% = min(40, 10 × (対象人数 − 1))      total = exp + floor(exp × bonus% / 100)
各自 = floor(total / 人数)、余りは止めを刺した人（対象に含める）。ソロは exp のまま
```

## 2. ギルド（`com.eternalrealm.guild`）

| クラス | 役割 |
|---|---|
| `GuildService` | ギルド台帳＋招待。`checkCreate / create / invite / accept / decline / leave / kick / promote / demote / transferMaster / disband / addContribution / expireInvites / guildOf / byName / byTag` |
| `Guild`, `GuildMember`, `GuildRank`, `GuildInvite` | 不変スナップショット（保存形式も兼ねる） |
| `GuildNames` / `GuildRules` | 名前・タグ規則 ／ レベル閾値・人数上限・招待期間（5分） |
| `GuildRepository` → `persistence.file.FileGuildRepository`, `GuildCodec` | 保存 |

- 名前: 2〜16文字（コードポイント数）の文字（日本語を含む全文字種）・数字・空白・`_`・`-`。先頭/末尾/連続の空白は不可。
  入力はNFC正規化。重複判定は NFKC＋小文字（全角/半角・大小文字を同一視）。タグ: 英数字2〜5文字、大文字で保存。
- 権限: 招待=OFFICER/MASTER。追放=MASTERは誰でも、OFFICERはMEMBERのみ。昇格/降格/譲渡/解散=MASTERのみ。
  MASTERは譲渡か解散をしないと脱退できない。譲渡すると旧MASTERはOFFICER。
- 貢献度はシーズン別に保存（飽和加算、`points > 0`）。レベルは全シーズン合計から:
  閾値 `0, 1000, 3000, 7000, 15000, 30000, 60000, 100000, 160000, 250000`（Lv1〜10、初期値・要調整）。
  人数上限 `min(50, 30 + 5 × (Lv − 1))`。上限を下げても既存メンバーは外さない（新規加入のみ不可）。
- **ギルドは永続**（シーズンをまたぐ）: `<dataRoot>/guilds.json` に全ギルドを1文書で保存（schema 1、原子的・前世代つき）。
  招待は保存しない。解散したギルドは削除（ランキング記録側で名前・タグを写しておくこと）。

## 3. 経済（`com.eternalrealm.economy`）

| クラス | 役割 |
|---|---|
| `Wallet`, `WalletResult` | 所持金 0〜9,999,999,999 G の不変値。`credit/debit` は新しい Wallet か INSUFFICIENT_FUNDS / OVERFLOW（負・桁あふれなし） |
| `TradeSession<T>`, `TradeOffer`, `TradeSettlement` | 直接取引（T＝統合側のアイテム参照、`equals` で同一判定） |
| `AuctionHouse`, `Listing`, `ItemInfo`, `MarketQuery`, `Page` | 取引所（出品・購入・取消・期限切れ・検索） |
| `Mailbox`, `MailItem`, `MailClaim` | メールボックス（AuctionHouse が保持） |
| `ShopCatalog`, `ShopEntry`, `ShopPurchase` | NPC商人（不変・状態なし） |
| `MarketRepository` → `persistence.file.FileMarketRepository`, `MarketCodec` | 取引所の保存 |

直接取引: 各側9アイテム＋ゴールド。`OPEN`（各側 交渉→LOCK、両者LOCK後に CONFIRM）→ 両者CONFIRMで `SETTLING` →
統合側が移動して `complete()`、できなければ何も動かさず `fail(reason)`。どちらかの提示が変わると両者のLOCK/CONFIRMを解除し
`revision` を上げる。`lock/confirm` にはクライアントが表示していた revision を付け、違えば OFFER_CHANGED（すり替え詐欺対策）。

取引所:
- 出品手数料 `max(1, floor(価格 × 2%))`（返金なし）、販売税 `floor(価格 × 5%)`。どちらもゴールドの消滅（基準点で設定）。
- 1人10件まで。48時間で期限切れ→出品者のメールボックスへ。自分の出品は購入不可。売上（価格−税）と返却品はメールボックスへ。
- 受け取りは財布の空き分だけ（残りはメールボックスに残る）。アイテムは `maxItems`（空きスロット数）まで古い順。
- 検索: アイテムID部分一致（大小無視）、価格昇順/降順/新着（同値は古い順）、ページング。期限切れは表示しない。
- ランダム操作テストで「ゴールドは手数料・税以外で増減しない」「アイテムは常にちょうど1か所に存在する」を検証。

**保存場所の判断**: 取引所（出品＋全員のメールボックス）は `<dataRoot>/seasons/<seasonId>/market.json`（シーズン単位）。
出品中のアイテムと未受取のゴールドは、キャラクターの財布・所持品と同じ**シーズン経済**に属するため。シーズンリセットで一緒に
リセットされ、旧シーズン分は消さずに残る。引き継ぎ設定の場合はシーズン移行処理がキャラクターと一緒に移す（出品は先に
メールボックスへ返却）。出品とメールボックスを1文書にしたのは、「出品を消す＋メールボックスに入れる」を原子的に保存するため。

## 4. Forge層がやること

1. 起動: I/Oスレッドで `FileGuildRepository.loadAll()` / `FileMarketRepository.load(season)`（無ければ `MarketData.empty`）→
   `GuildService` / `AuctionHouse` を生成。**読み込み失敗時は空で始めない**（次の保存で全ギルド・出品が消える）。機能を無効化しログ。
2. 保存: `isDirty()` なら `guilds()` / `snapshot()` と `revision()` をサーバースレッドで取り、I/Oスレッドで保存、成功後 `markSaved(rev)`。
   オートセーブ・停止時に加え、**取引所の取引直後**とその当事者のプレイヤーデータも速やかに保存する。
3. tick: `PartyService.expireInvites(tick)` 毎tick、`GuildService.expireInvites(now)` と `AuctionHouse.expireListings(ms)` は数秒ごと。
   返ったイベントを `PartyMessages` / `GuildMessages` / `EconomyMessages` のキーでチャット送信し、HUD・画面を同期する。
4. コマンド・パケット: /party, /guild, 取引ウィンドウ, 取引所・メールボックス・ショップ画面。失敗は `outcome.messageKey()` を表示。
5. 経験値: Mob撃破時に対象メンバー（生存・同ディメンション・距離内）を選び `ExperienceShare.split` → 各自にEXP付与。
6. 直接取引: 同じ処理の中で「所持品・ゴールドの再確認 → 空き確認（`applyGold`）→ 移動 → `complete()`」。距離・死亡・ディメンション
   変更で `cancel(TOO_FAR)`、ログアウトで `playerDisconnected`。取引の申し込みと「誰が誰と取引中か」の管理は統合側。
7. 出品: `checkListing` → 所持品からアイテムを外す → `list` → 返った Wallet を保存。購入: `buy` → Wallet 保存 → アイテム付与
   （満杯なら `sendItem(..., AUCTION_PURCHASE)`）。受取: `claimAll(player, wallet, 空きスロット数)`。
8. Wallet の保存はキャラクターのシーズンデータ側に追加する（character の schema 更新）。ギルド設立費用を取るなら `checkCreate` → 引き落とし → `create`。
9. ショップの「本日の購入数」はプレイヤー×商品×日（リセット時刻は運営設定）で統合側が数えて `purchase` に渡す。

## 5. 既知の制限・未実装

- market.json とプレイヤーデータ（財布・所持品）は別ファイルのため、クラッシュの瞬間によっては片方だけが保存され得る
  （取引直後の即時保存で窓を小さくする）。完全な原子性は PostgreSQL（Phase 1.5）の単一トランザクション＋監査ログで実現する。
- 監査は slf4j の INFO ログ（出品・売買・取消・期限切れ・郵送・受取）のみ。`economy_logs` テーブルは未実装。
- 取引申し込みの管理、ギルド倉庫・チャット・称号、メンバー個人の貢献度、管理者による出品削除、NPCへの売却は未実装。
- ギルド名は異なる文字種の似た文字（ラテン文字 A とキリル文字 А）を別の文字として扱う。
