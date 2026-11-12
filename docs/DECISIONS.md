# 設計判断の記録（ADR）

仕様書は3つあり（開発指示書・最終企画書／技術設計書・2.0追加仕様書）、一部が矛盾します。ここに解釈と判断を記録します。
**「要確認」の項目はオーナーの判断で変更できます。**

| ID | 判断 | 理由 | 状態 |
|---|---|---|---|
| D-001 | 仕様の優先順位は **2.0追加仕様 ＞ 最終企画書 ＞ 開発指示書** | 2.0は既存方針を明示的に変更する後発の文書 | 確定 |
| D-002 | ~~レベル上限は初期50~~ → **レベル上限100**（サーバー設定 `character.maxLevel`、既定100＝データモデルの上限）。2026-11-11 オーナー指示「レベルの上限を100にして」で変更。旧キー `levelCap`（既定50）は廃止し、既存ワールドの設定に残る `levelCap = 50` が新しい既定値を上書きしないようキー名ごと替えた | 2.0「初期レベル上限50」と指示書「最大レベル100」→ オーナーが100に決定 | 確定（改訂） |
| D-003 | 攻略進行は**条件解放**（ボス討伐等）。DAY 1〜14は進行の目安。実時間での強制待ちはしない | 2.0「実時間で強制的に次の日まで進めない」。企画書の実時間スケジュールより優先 | 確定（詳細は SEASON_SYSTEM.md） |
| D-004 | 解放の単位は「サーバー全体（誰かが条件達成で全員に解放）」＋個人条件（推奨レベル等）の2層を提案 | 企画書の Shared 方針と 2.0 を両立 | **要確認** |
| D-005 | シーズン移行時のリセット範囲（レベル・装備など）は設定で選択 | 2.0 | 確定（Phase 10で実装） |
| D-006 | **season_id は Phase 1 からデータに含める**（保存先も `seasons/<id>/` で物理分離） | 後からの付与は全データ移行が必要になる。2.0「永続データとシーズン限定データを分離」 | 確定・実装済 |
| D-007 | フェーズ順は 2.0 の優先順位を採用（ROADMAP.md） | 2.0 | 確定 |
| D-008 | Phase 1 の保存先は**ファイル（JSON）**。PostgreSQLは同じポートの別実装として **Phase 1.5** で追加 | この作業環境にDBもドライバも無く検証できない。未検証の実装を完成扱いにしない（指示書）。単一サーバー・シングルプレイでは外部DB不要で動く利点もある | **要確認**（優先度を上げたい場合は Phase 2 の前に実施） |
| D-009 | 職業選択は **Phase 3（育成）** で実装。データ項目もPhase 3で追加 | 指示書の「最初に完成させる機能」に職業は含まれない／2.0でもレベル・装備・スキルと同列 | 確定 |
| D-010 | Javaパッケージ名は `class` → **`job`** | `class` は予約語 | 確定 |
| D-011 | ステータスの**戦闘への反映は Phase 2**。Phase 1 は保存・割り振り・表示・サーバー検証まで（画面に明記） | 戦闘システムと同時に設計しないと後で作り直しになる。未反映であることはUIで正直に示す | 確定 |
| D-012 | 未割り振りポイントは**保存せず導出**（`(level-1)×ポイント/Lv − 割り振り合計`） | 不整合・改ざんの余地を構造的に無くす。設定変更時も自動で正しくなる | 確定・実装済 |
| D-013 | 割り振りの取り消しは管理者コマンドのみ。プレイヤー向け再振り分けは Phase 3（コスト設計とセット） | 企画書「再振り分け機能」はコストや入手手段の設計が前提 | 確定 |
| D-014 | 暫定EXP源: バニラの敵対Mob撃破で `最大HP×係数` のEXP（設定でOFF可） | 戦闘・モンスター実装前に実プレイでレベルアップを検証するため。**TODOとして Phase 4 で置き換え** | 暫定 |
| D-015 | 読み込み失敗時は新規作成せず切断（障害IDを表示・ログ出力） | 新規データで上書きすると進行が消える | 確定・実装済 |
| D-016 | 保存失敗（3回再試行後）のデータは、それがそのプレイヤーの**最新の保存**である場合だけメモリに保持し、次回ログイン時はディスクより優先、オートセーブで再試行。判断は保存の通し番号で行う | 古いディスクデータでの復活を防ぐ。古い失敗が新しい成功を上書きする巻き戻り（独立レビューで指摘・再現）を防ぐ | 確定・実装済 |
| D-017 | データ形式は schema 番号つきJSON。新しい schema は読まずにログイン拒否（隔離もしない） | 古いビルドでの読み書きによる破損防止 | 確定・実装済 |
| D-018 | I/O完了通知は `MinecraftServer#execute` ではなく自前の `MainThreadQueue` | 停止中の `execute` は呼び出しスレッドで即時実行されるため（ARCHITECTURE.md 4.2） | 確定・実装済 |
| D-019 | Forge **47.4.0**（1.20.1 の4番目の推奨版）＋ `FMLJavaModLoadingContext` のコンストラクタ注入 | 公式MDKと同じ書き方。非推奨APIを避ける | 確定 |
| D-020 | テストは **JUnit 4.13.2** | ネットワーク制限下のこの環境でも実行できる唯一のテストランナー。Gradleでも同じテストが動く | 確定（JUnit 5移行は任意） |
| D-021 | MODはクライアント・サーバー両方に必須（プロトコル版一致必須） | HUD/UIと検証済みプロトコルを前提にできる | 確定 |
| D-022 | 「初期プレイアブル版」（企画書21章）はパーティー機能を含むため、2.0順序の Phase 7 から**最小限のパーティー機能のみ前倒し**を提案 | 2.0順序のままだとプレイアブル版の条件を満たすのが遅れる | **要確認** |
| D-023 | 「ファイルが無い」と判断するのは `NoSuchFileException` のときだけ。存在を判定できないエラーはログイン拒否 | `Files.exists` は判定不能でも false を返し、新規キャラクター作成→実データ上書きにつながる（独立レビューで指摘） | 確定・実装済 |
| D-024 | 前世代 `.prev` は改名ではなくコピーで作り、`X` は常に存在させる | 改名方式では保存途中のクラッシュで最新の完全な保存を捨てて1世代前に戻っていた | 確定・実装済 |
| D-025 | 停止時に保存できないデータは `emergency/` へ通常形式で退避（失敗時はログにJSON） | ログだけでは復旧に手作業が多い | 確定・実装済 |
| D-026 | レート制限で破棄した要求は違反に数えず、別枠のフラッド閾値（既定300/60秒）でのみ切断。クライアントも4回/秒に自制 | 連打やキーリピートで正規プレイヤーが切断されるのを防ぐ（独立レビューで指摘） | 確定・実装済 |
| D-027 | 戦闘はクライアントが**意図だけ**を送り、命中・ダメージ・CT・スタミナはサーバーtickで判定。クライアントは同じ状態マシン（純粋モジュール）で予測し、連番つきの確認で補正。先行入力は1つ | 操作レスポンスとサーバー権威の両立。遅延の揺らぎでコンボが失われない | 確定（Phase 2） |
| D-028 | 新しい戦闘とSTR/AGI/LUKの反映は **ETERNAL REALM の武器のみ**。バニラの剣・素手はバニラの戦闘のまま。VIT（最大HP・被ダメージ軽減）は武器に関係なく適用 | バニラ戦闘の挙動を壊さず、MMOの武器へ段階的に移行する。防御はキャラクター全体の性能 | 確定（Phase 2） |
| D-029 | ガードはバニラの「アイテム使用」（右クリック長押し）に乗せる。バニラの盾処理（`SHIELD_BLOCK`）は使わず、パリィ・軽減・角度は独自に判定 | ガード姿勢が追加パケットなしで全員に同期される。盾の5tick待ちや100%防御に縛られない | 確定（Phase 2） |
| D-030 | 武器・行動の定義は Phase 2 ではコードで定義（`WeaponProfiles`）。ステータス換算の係数だけをサーバー設定にし、派生値をサーバーから送る | クライアントの予測とサーバーの判定が必ず同じ時間になる。データパック化は装備システム（Phase 3）で同期の仕組みと一緒に行う | 確定（Phase 2） |
| D-031 | 最大HPの補正は**永続**の属性修飾子（固定UUID）で、キャラクター準備完了・ステータス変更・リスポーン時に毎回上書き | 一時修飾子だと再ログイン時にバニラの体力読込で20に切り捨てられる。値は毎回上書きするので古い値は残らない | 確定（Phase 2） |
| D-032 | ETERNAL REALM の攻撃では対象の無敵時間（バニラの10tick）を無視し、ノックバックは行動ごとの値に置き換える | 連撃が成立しない／敵が射程外へ飛ぶのを防ぐ | 確定（Phase 2） |
| D-033 | 回避の移動はクライアントで即時に行い（バニラの移動はクライアント権威）、サーバーは無敵時間とスタミナだけを検証する | サーバー起点の移動はRTT分遅れて操作感が悪い。移動自体はバニラの移動検証の対象 | 確定（Phase 2） |
| D-034 | VIT の被ダメージ軽減は攻撃者のいるダメージにだけ掛ける | 落下・溶岩などの環境ダメージを無効化しない | 確定（Phase 2） |
| D-035 | ガードは**サーバーだけが開始を判断**し、クライアントは予測しない（バニラが同期する「アイテム使用中」状態に追従） | 行動の境目ではクライアントとサーバーの判断が1tickずれ得る。予測すると「構えているのに守られていない」状態が残り、拒否の通知も後から来る許可と競合する（独立レビューで指摘）。シングルプレイでは遅延なし、オンラインでも保護の開始時刻は予測の有無で変わらない | 確定（Phase 2） |
| D-036 | 武器を持っている間の右クリックはガード専用。オフハンドのアイテムはしゃがみ＋右クリックで使う。他のアイテムを使用中（盾・食事・弓など）は攻撃・回避・スキルを受け付けない（サーバーとクライアントの両方） | ガードが拒否されたときにオフハンドの盾へ流れ、バニラの100%防御の裏で連撃できた（独立レビューで指摘） | 確定（Phase 2） |
| D-037 | パリィの受付はガード開始ごとに開くが、前回のガード開始から16tick未満なら開かない | 右クリックの連打で受付を出し続けられた（自己レビュー・独立レビュー双方で指摘） | 確定（Phase 2） |

## D-RARITY: rarity raises a piece's special performance and its art, and asks for a few more levels (2026-11-11)
The owner asked that the higher a weapon's rarity, the stronger its special performance and its Z art, and that pieces
have a level players need to handle them. Each rarity has a power (COMMON ×1.00, UNCOMMON ×1.07, RARE ×1.14, EPIC
×1.22, LEGENDARY ×1.30) that multiplies what makes a piece special rather than its plain numbers: affix values, a named
weapon's element chance, fixed bonuses and perks, an armor set's and a trinket's fixed bonuses, and the damage and
effect strength of the held weapon's own art (job skills and other weapons' arts are not touched). Together with the
existing base multiplier (×1.20 at LEGENDARY) a legendary piece's art lands about 1.56 times as hard as a common one of
the same weapon; at equal rarity boss arts stay above every named art (tested). The required level is the design's or
tier's level plus a rarity offset (+0/+0/+1/+3/+5, capped at 100): a rarer piece is worth a short wait, and the offset
stays small so a lucky drop is still usable soon. It applies to pieces players already own; until they reach the new
level such a piece gives no stats (as before for pieces above the level) and is greyed in the equipment screen. The
server enforces the level when equipping, when attacking, guarding or using the art with a held weapon (with a
message; the client refuses first), and in the stat totals. Players without loaded character data are not stopped.

## D-RELICS: gold-frame relics, about one kill in a thousand, from every boss, calamity, lord and wanderer (2026-11-11)
The owner asked for very rare (about 0.1 %) weapons, armor and two new kinds of item, charms and accessories, themed on
the dungeon bosses, calamities and lords, gold-framed. Every one of the 64 sources (9 dungeon bosses including the final
raid, 10 calamities, 30 lords and the 15 wanderers, which are field bosses too) got its own seven relics: a weapon, a
four-piece armor set, an accessory and a charm (448), all from one table (`tools/art/relics_data.py`) that writes the
Java rows, the texts, the art and the docs list. A relic always comes as LEGENDARY, asks for its source's level + 5
(no rarity offset) and is the strongest piece of its level (tested against what that level can wear, at any rarity:
more attack than every named, boss and tier weapon of its type; more defense than every named set of its style, and the
heavy and light sets also than every tier piece). Each
rewarded player rolls once per kill at `relicDropChance` (server config, default 0.001); a hit is the weapon, the
accessory, the charm or one armor piece, a quarter each. Accessories and charms got their own slots (two accessory, one
charm) because they should add a lot without replacing armor; the same trinket counts once. The slots live in the
player's saved data (like the vanilla armor slots, kept through death and dimension changes) and travel to the client
over the existing UI action and UI data channels: no new packet, no protocol bump. Gold frames are drawn by one client
helper wherever a relic is shown (bag, equipment slots and candidate list, tooltip border, pickup feed), and a drop is
announced to the whole server. Other modules filter with `EquipmentStacks.isGoldFrame`, `requiredLevel` and `rarity`.

## D-MOUNT-TRAITS: each horse and griffin is an individual; values with a floor stay hidden until bought (2026-11-11)
The owner asked for horses and griffins that differ one from another in colour and wing or mane shape, individual
values with a floor, four random offers to choose from at each purchase with the values shown only after buying (a
gamble), and an auction for the good ones. A mount is now an individual (`MountIndividual`: id, kind, look, values) and
a character keeps up to 12 of them; the arcbike is a machine and stays one, unvaried, per character. The three values
(speed, acceleration, lift) are rolled evenly between 30 and 100 and scale the species' numbers by 0.92 to 1.08 (65 is
the species itself): the floor means no mount is a dud and the narrow band keeps the gamble about bragging and small
edges rather than a mount twice as fast: the best horse (0.62 blocks a tick) stays well behind the arcbike (0.86). The
grade comes from the sum (C, B, A, S, SS: about 28/48/19/4.3/0.57 %), and an SS is announced. The stablemaster rolls
four looks per kind and keeps them (until bought or re-rolled for 5 % of the price), and their values never leave the
server until one is bought, so a modified client cannot read them. Coats are model textures and shapes scale model
parts, so all looks share one model. Mounts bought before this read as the classic look with standard values. A kept
mount can become a deed item (`er_mount` tag) to sell at the board or the hall and be taken into another player's
stable at the stablemaster, with the kind's level gate; deeds keep the mount's id, so one mount can never be kept
twice.

## D-AUCTION: two market counters — a fixed-price board and a live auction hall with five lots at once (2026-11-11)
The owner asked for an auction for high-value pieces (five lots at once on the server, run at the capital's hall, joined
from any field, "fine and delicate"), and for a board shop where common gear and materials trade cheaply and rare
pieces dearly. Both live in the existing season market (one file, one save): the board extends the earlier fixed-price
house (filters by words, category, rarity, level and gold frame; six sorts; 2 % listing fee capped at 100,000 G so rare
pieces are not taxed out; recent prices per item group beside the sell form), and the hall is a new pure model
(`AuctionHall`) whose every step is an event the server carries out: the seller registers a draft at the counter and
pays 1 % to start it; five lots run at once and later ones queue (a queued lot can be withdrawn with its fee back);
bids are held from the bidder's gold at once and refunded the moment they are beaten, so a winner can always pay; the
next bid must beat the highest by 5 % (10 G at least); a bid in the last minute pushes the end back a minute (15 minutes
at most) so last-second sniping does not decide lots; time stops while the server is down. Registering and starting
need the hall's clerk (12 blocks) because the owner placed the auction at the capital; bidding, buying outright,
withdrawing and the mailbox work anywhere. Nothing new crosses the network: the screens are fed by UI data channels and
send UI actions, and the server checks every argument and decides everything. Everything a player gets while away goes
to the mailbox; tax and fees leave the economy.

## D-LOCKON: aimed skills lock on to an enemy while their key is held (2026-11-11)
The owner found some skills hard to land and asked that holding the key aims at a character and hits it, with the
aimed enemy's outline glowing white; area attacks stay as they are. A skill is "aimed" when where it lands depends on
the facing (`job.SkillAiming`: projectiles, slashes, thrusts, discs ahead, areas placed ahead or where one looks, forward
dashes and leaps); area, heal and buff skills still cast on key down. For aimed skills the key casts at once when no
enemy is in reach at key-down and on release after a tap (under 4 ticks); holding picks the enemy nearest the crosshair
within the skill's reach and lights the game's own glowing outline on this client only (white: monsters have no team),
and releasing turns the player toward it and sends its entity id with the request (protocol 8). The server only trusts
the id after its own checks (valid target, in sight, within reach) and keeps one lock per action (another skill pressed
meanwhile neither drops nor steals it). While the locked action's start or a hit is carried out the player faces the
enemy (and gets their own rotation back afterwards, as the client only sends rotation when the mouse moves); the middle
projectile of a volley steers into it until it hits or passes it, falling shots drop on it and areas placed where one
looks land at its feet within the skill's reach. An independent review found these points before release. No mixin
was needed: the outline flag is set through `Entity`'s protected data accessor from a never-instantiated subclass.

## D-CALM: habitats twice as large; their monsters are calm until provoked and appear 4 blocks apart (2026-11-11)
The owner asked for spawn areas about twice as large, monsters that do not attack until the player attacks them or
stays near them, and about four blocks between spawns. Habitats got √2 × their radius (40/42/45 instead of 28/30/32,
twice the area) and were placed anew by `FieldSitesGenerator --keep-waystones`, which keeps the 44 named minor
waystones where they are; a habitat may now touch a shore with up to a quarter of its rim (nothing spawns in water) and
the rims of two habitats are 16 apart (was 24). The harbour, mostly sea, has no room for the second homes of its three
common kinds, whose first homes hold half again as many monsters instead (157 habitats). A new habitat monster appears
at least 4 blocks from any other monster and keeps near the spot it appeared at instead of bunching in the middle.
Habitat monsters are calm (`monster.Temper`, `MonsterTempers`): they take no player as a target until that player
hurts them or stays within 8 blocks for 8 seconds (they turn to look and growl halfway), deal no damage while calm
(no touch damage), fight only those who provoked them and settle down 10 s after they have nobody to fight. Vetoing the
target change (`LivingChangeTargetEvent`) covers every way a monster picks a target. Server config
`monsters.calmUntilProvoked` / `lingerSeconds` / `lingerRadius`. Lords, dungeon monsters and bosses are unchanged. A
wandering boss's arena may come up to a habitat's rim where a field would otherwise have fewer than three spots.

## D-CROWD: attacks pass through other players; crowd bosses have half again their health (2026-11-11)
The owner asked that, outside arena matches, attacks never hit other players (raids are crowded) and that every raid
boss have 1.5 × its health. The mod's attacks already skipped players; now the mod's arrows and spells never collide
with a player on the client either, vanilla projectiles a player shoots fly through other players (impact cancelled),
and a bare left click aimed at another player lands on the creature behind. "Raid bosses" was read as every boss
fought by a crowd: the final raid boss and the 25 field bosses (calamities and wanderers); dungeon bosses (parties of
1-5) are unchanged. Their enrage timers grew by the same factor (field 6 → 9 min, final raid 7½ → 11¼ min).

## D-LEVEL100: the level cap is 100 (2026-11-11)
The owner asked for a level cap of 100 (D-002 had 50). The server config key changed from `character.levelCap`
(default 50) to `character.maxLevel` (default 100) so that worlds whose config still says 50 pick up the new cap. The
skill point economy is unchanged: 218 points at level 100 master about 80 % of a tree (266), never all of it. Content
(areas, gear, bosses) still tops out around level 50-55; levels above that come from hunting, bosses and raids there.

## D-HABITATS: field monsters live in fixed habitats; five times as many waystones (2026-11-10)
The owner asked for a fixed spawn place for every monster of every field (to make levelling and farming materials
easier) and for five times as many waystones. Every normal monster of a field got a habitat (the three common ones two):
160 circles placed once by `FieldSitesGenerator` (tests) on dry, walkable land inside the field, clear of the capital,
the lords' territories, the dungeon entrances, the waystones, the set pieces and each other, the weaker kinds nearer the
field's shrine, and kept as a fixed table so the places never move. In アストリア monsters now appear only there (filled
while a player is near, a killed one back after about five seconds, now and then the field's elite) and stay near home.
44 minor waystones (4-5 per field, placed to be near the habitats, lords and dungeon entrances) join the 11 shrines; the
server builds each as a stone on a small platform when a player first comes near or travels there, so existing worlds get
them as well.

## D-MOUNTS: mounts are bought per character, called with U, never fought from (2026-11-11)
The owner asked for the three mounts of the reference pictures, bought from Lv10 (horse), Lv30 (griffin) and Lv50
(bike), the griffin flying, the horse fast and the bike faster. Ownership is character data (extension `mounts`): it
never wears out, and like waystones and outfits it starts over with a new season's character, whose level gates and
gold start over too. The mount is a short-lived entity (not saved) called in one second where the player stands still,
outside dungeons, boss fights and combat, and it leaves when its rider gets off, logs out or travels. Movement is
driven by the rider's client like vanilla vehicles (`MountMotion`, pure) and watched by the server over 20-tick
windows (sustained speed or hovering above the flight ceiling sends the mount away). The griffin flies up to 32 blocks
above the highest ground around it (8 below the world's top); dungeons cannot be entered mounted, so no instance or
boss room can be flown over. Mounts take no damage and are never targets; big hits do not knock riders off, but a
rider's own blow does not land from the saddle: the rider gets off to fight. No new packets: two UI actions and one
view channel.

## D-HUD: one layout for the whole HUD; no hotbar, the main hand is slot 0 (2026-11-10)
The owner asked for no overlapping HUD parts, no vanilla hotbar and a bag and an equipment icon on the right with their
keys. Every part is placed by one pure layout (`hud.HudLayout`) computed per frame from the screen and what is showing,
and tested for overlaps; overlays draw only inside their rectangle. Without a hotbar the main hand is inventory slot 0,
which holds the equipped weapon (the client keeps the selection there, the server moves a weapon there at login); the
number keys 1-3 use items straight from the inventory (`UseItemPacket`, checked by the server like a use from the hand;
the return scroll channels on the server). Creative and spectator mode keep the vanilla HUD for building and testing.

## D-ARTS: every weapon's Z is its own weapon art, generated from one table under a damage budget (2026-11-11)
The owner asked for the Z attack to be each weapon's own skill, fitting its look and material, with its own effects.
The 180 named field weapons and the 42 tier weapons (7 materials × 6 types, the starters are iron) each get a unique
art; the 9 boss arts stay. Writing 222 arts by hand would drift, so they are composed: an attack form (weapon type ×
feel), the element's affliction, a few extras from the existing skill effects, and a look (particles, colours, shapes,
wind-up). One Python definition (`tools/art/weapon_arts.py`) writes the Java rows, the Japanese and English names and
descriptions (composed from the same choices, so the text always says what the art does) and the docs table. Damage
is normalised: every art's burst and burst per second of cooldown are set from the weapon type's own skill (tier
1.08–1.26×, named 1.40–1.85×, bosses ≥ 2×), so the arts stay balanced as a whole and tests can hold the bands. The
generic type skills stay in the code only as the yardstick. Looks are composed at runtime by one parametric drawer
(`ArtFx`) and checked for uniqueness by signature; vanilla particles (item, block and simple ones) are referenced by
id from the player's own game, so no game assets are copied.

## D-VITALS: no hunger and no natural regeneration; potions are the way to heal (2026-11-10)
The owner asked for no hunger gauge ("always full"), no automatic regeneration and potions as the basic way to heal.
The server keeps every player's food bar full each tick (sprinting is always allowed, nobody starves) and the client
never draws it; the `naturalRegeneration` game rule is switched off at server start (server config
`survival.naturalRegeneration`, default false), which also stops the peaceful-difficulty heal; the out-of-combat VIT
regeneration was removed. VIT now makes potions stronger (+0.4% per point, at most +50%) instead. So that topping up
after a fight is not a wait, the shared potion cooldown is 2 s out of combat and stays 15 s in combat (boss balance is
unchanged). Monsters drop the potion size of their tier (4%, elites 50% ×1-2) and the first job choice hands out ten
small potions with the starter weapon.

## D-RNG: only java.base random generators at runtime (2026-11-08)
Forge runs the game on a module layer in which the `jdk.random` module is not resolved, so
`RandomGenerator.of("L64X128MixRandom")`/`RandomGenerator.getDefault()` throw "No implementation of the random number
generator algorithm" at server start (seen in the GameTest run of 00c9b91). Pure modules take a `RandomGenerator`
parameter; the Forge layer passes `java.util.SplittableRandom` (or `java.util.Random`/`ThreadLocalRandom`), which live in
`java.base`. Never name an algorithm from `jdk.random`.

## D-ENUM: extensible enums are created at client setup, never lazily (2026-11-08)
Forge's `IExtensibleEnum.create` (e.g. `HumanoidModel.ArmPose`) adds a value at runtime, but every `switch` over that
enum compiled into vanilla code uses a switch map sized to `values()` when the map is first used. A value created after
`HumanoidModel` first posed an arm (our lazily created attack pose) had no slot and crashed every frame
(`ArrayIndexOutOfBoundsException: Index 10 out of bounds for length 10`). Create such values once in
`FMLClientSetupEvent#enqueueWork` (main thread, before any model renders) and fall back to a vanilla value otherwise.

## D-DIALOGUE: dialogues are read at the player's pace; follow-ups wait for "dialogue.done" (2026-11-08)
Timed chat lines (2 s apart) were too fast to read. The server now sends a whole dialogue to the client: conversations
(NPC/quest talks, prologue) open a window that waits for a click per line; ambient lines (areas, bosses, news) use a box
above the hotbar advanced with a key, so nothing blocks combat. What a conversation leads to (accepting the quest,
completing a talk objective, opening a shop) runs on the server when the client reports `dialogue.done {id}` (read or
skipped). A follow-up is dropped on logout or after 15 minutes: the player simply talks to the NPC again, so no quest
state can be lost or duplicated. Service NPCs talk once per story progress and session so shopping stays one click.

## D-FIELDMAP: アストリア is the field map around the capital, with its own terrain generator (2026-11-10)
The owner asked for the world to follow the painted "Arcelia Kingdom Field Map" (ten fields around the capital with
the levels written on it) and to be wide. The seven concentric areas became regions of a weighted, noise-warped Voronoi
map (`world.field.FieldLayout`); the story keeps its order and its area ids/slugs (only the names changed), the old
themes live on as places inside the new fields (the sun temple under the mine, Nordia's ice castle above the falls, the
dragon realm under the ruins, the sky city above the peaks), and three side fields were added. Terrain comes from a
custom chunk generator over the pure `FieldTerrain` heights instead of the overworld noise: only that reproduces the
map (mountains north, sea south-east, the lake, the falls) identically on every server and lets the world map be
painted from the real land. There are no caves (dungeons are instanced). Worlds made with the rings regenerate
アストリア once; characters, items and progress are not touched.

## D-LORDROSTER: two lords of a field out at a time, a new line-up every 20 minutes (2026-11-11)
The owner found the lords too frequent and asked for at most two at a time per field, and for the ones not beaten to
leave every 20 minutes and different ones to appear. Each field keeps a line-up (`world.field.LordRoster`, pure): two of
its three lords; every 20 minutes the resting one always comes out and the other place goes to one of the two that
were out, at random, so each change shows a new face. A beaten lord rests until the next change (no separate respawn
timer any more), and one still fighting players (hit within 60 s) leaves only after the fight, never in a player's
face; a field never has more than two bodies, leaving ones included. The line-up is not saved: a restart deals a new
one, which costs nothing (lords are not persistent progress) and keeps world data unchanged.

## D-MINIMAPNPC: role icons and labels on the minimap inside the capital (2026-11-11)
The owner asked to see on the minimap, inside the kingdom, where each NPC is and what they do. The minimap already
receives the town people around the player (role and name in the map document), so no packet changed: inside the
capital the dots become small role icons (one atlas drawn by `tools/art/map_npc_icons.py`) with short labels placed by
a pure, tested routine (`world.map.NpcMarkers`: nearest first, at most eight, never overlapping or leaving the map).
Outside the capital the plain dots stay, where labels would only clutter the wild.

## D-CAPITALWALK: capital interiors are furnished from one kit and every staircase is checked (2026-11-11)
The owner found holes, a smithy entered from the wrong side, interiors too plain and the stair to the princess facing
the wrong way. The smithy was rebuilt to face its street; rooms are dressed by one kit (`districts/_furnish.py`) instead
of ad-hoc blocks, and paintings are entities hung only where the game will keep them (a full block behind, nothing in
front; `check.py` verifies it on the export). Looking for the reversed stair found more stairs that could not be
climbed (a step short at the top, a beam or pier in the head room, a first step that needed a jump), so `check.py`
now judges every staircase of the export by the rules a player's 1.8-block body imposes and fails the build on one
that cannot be used or rises against its facing. A voxel check cannot see everything (it does not walk, and it does
not judge looks), so the previews and section drawings are still looked at by eye.

## D-CANALTOWN: the capital becomes a canal town with an undercroft; the castle stays (2026-11-11)
The owner asked for a more intricate town after four pictures of a canal town (canals with bridges, back alleys and
passages, rooms over the lanes, an underground waterway for back-room trade and storage, an indoor shopping street)
and for the castle to stay as it is. The rebuild keeps the master plan (plaza, guild, civic quarter, cathedral,
walls, gates) and the castle, and changes what is between them: a Grand Canal along z = 43 that the terrain carves
and `canal.py` builds (water top −2, the same level as the lake and the river it comes from, so it needs no locks),
maze quarters from one kit (`_maze.py`) instead of plain rows, the inn mansion for the player rooms (星灯りの館), the
Galleria in place of the covered market hall, and an undercroft under the market square. The undercroft lies inside
the market district's columns (district clipping is by column, so an underground place belongs to the district above
it) and is reached only by hidden ways (the culvert and its fall, the cellar hatch, the old well, a secret door),
because the pictures asked for back-room trade, not a second street level. New block behaviour (bunting, shop signs,
secret doors, room doors) lives in custom blocks that the generator writes by id and the Java side draws, as with the
earlier flags and clocks, so the voxel build stays deterministic and the art stays original.

## D-ESTATES: player rooms and guild houses are private places in a dimension of their own (2026-11-11)
The owner asked for player houses (an inn, mainly for keeping luggage, with a teleport to one's room) and guild houses
reached through gates in the capital. Both are "estates" on one core (`docs/modules/estates.md`): a void dimension
`eternalrealm:estates` with a fixed late-afternoon sky, plots 1024 blocks apart (an estate never sees another), one
blueprint loader and placer shared with the capital, a registry saved with the world, and protection (nobody breaks
or places; storage, doors, frames and stands only for those allowed in). Putting them in the capital itself would have
meant fixed numbers of rooms and houses and strangers walking in; instancing them keeps the capital's look (the inn
mansion's doors and the twenty gates are the entrances), scales to any number of players and guilds, and lets a
design be swapped by building a fresh copy and moving every stored item into the same-named piece in one tick.

## D-INNROOMS: rooms are rented from the innkeeper; going home is a short cast that combat breaks (2026-11-11)
One room per player, five designs from a garret to a grand suite priced by size and storage, rented from Rosetta in the
Starlight Manor; any of its 43 room doors takes a renter home. Storage comes first: labelled chests, and a sorting desk
that lists everything in the room and puts the bag away by label, with every move done on the server (nothing lost or
doubled). The way home is a 3-second cast (Home key or the journal) broken by moving, damage or a fight and refused in
combat, dungeons, raids, boss fights and PvP, with a 60-second rest: a free escape from danger would undercut the
dungeons and the return scroll.
Details: `docs/modules/estates.md` §7–9 and its decisions section.

## D-GUILDHOUSES: one gate per design on a terrace below the castle; members only; the master buys (2026-11-11)
Twenty designs with no theme repeated, in five tiers by size and quality, each with its own gate on the Guild Gate
Terrace; a member walking through the gate of the guild's own design arrives in the guild's copy of that house, anyone
else is turned back. The guild master buys, switches and sells (the guild fund first, then the master's gold; switching
moves the stored items), members share chests and the officers' vaults with a log. Details:
`docs/modules/guildhouse.md` and its decisions section.

## D-MOUNTBREEDS: five horse and five griffin breeds, each with one talent, none faster than the bike (2026-11-11)
The owner asked for five more of each. Each breed has its own look (4 coats + 1 rare), level and price and one talent
(a leap, running on water, gliding, a surge, an air step; striding on land, hovering, a dive, high flight, the fastest
climb), on top of the existing individuality, grades and deeds. Talents change how a mount handles rather than only
adding speed, and no breed outruns the Lv50 bike even with its talent active, so the bike keeps its place. The old
`horse` and `griffin` ids stay the first breed of each family, so saves need no conversion. Details:
`docs/modules/mounts.md`.

## D-RANGEDKB: magic and bow attacks push nothing (2026-11-11)
The owner: 「魔法と弓の遠距離攻撃でのノックバックを0にして。敵が近寄れないから強すぎる」. Every hit of a staff or a bow
(light, heavy, weapon skill, weapon arts), every hit of the mage's and priest's skills (their close-range bursts
included: they too kept enemies away) and of the ranger's bow skills, and every projectile, blast and ground area of magic
or arrows now has knockback 0 (`combat.Knockback`, by damage type, applied to the frame in `CombatDriver.process` and to
non-melee hits in `CombatHits.deal`). Melee keeps its per-action knockback (D-032): the ranger's dual-blade skills
(physical) and the spellblade's sword skills, which deal magic damage but at a sword's length, where a push does not keep
anyone away. Texts that promised a push were rewritten, and the four bow arts whose
extra was "knocks foes far back" got another extra instead (blind, execute, stamina; the sandstorm relic bow: blind); the
art generator now refuses a knockback extra on a bow or staff art.


## D-TESTKIT: a test command that maxes a character and opens every room and guild house for touring (2026-11-11)
The owner: 「テスト用に、すべての部屋とギルドハウスの開放とお金をマックスとレベルとステータスポイント、スキルポイントをマックスにする
コマンドを実装して」. `/er admin test all [<player>]` (permission 2) = `max` + `unlock`. `max` sets the level to the
configured cap, the gold to the purse limit and refunds every stat and skill point, so all points of the cap are unspent
(297 and 218 by default); no points beyond the rules are created, and nothing counts as EXP earned (rankings). Since a
player rents one room and a guild owns one house, "unlocking every room and guild house" means being able to enter every
design: `unlock` hands out a test pass (the player's own persistent data, kept on respawn) and builds one showcase estate
per design (5 rooms, 20 guild houses) owned by a fixed name-based id per kind and design, so the same showcase is found
after a restart and never collides with a player or guild. Pass holders tour them through the innkeeper's "Tour" button,
through the guild gates of designs their guild does not own, or `/er admin test tour`; the pass, not the operator
permission, opens them, so operators walking the terrace see the normal gates. Showcases belong to no guild (no bell call,
vaults open to testers, no storage log) and are never torn down (nothing stored in them is lost); `lock` takes the pass
back. Details: `docs/modules/testkit.md`.

## D-AGGRO5: field monsters attack within 5 blocks; habitats hold half again as many, 5 blocks apart (2026-11-12)
The owner: 「フィールドの敵は周囲5ブロック以内に入ったら攻撃してくるようにして。敵の湧く間隔を5ブロックごとにしてもう少し
スポーンさせて」. This replaces the lingering rule of D-HABITAT2X (8 s within 8 blocks): a calm field monster now turns on
a player who comes within `monsters.aggroRadius` (5 blocks, in sight) at once, and on anyone who hurts it; within 3
blocks more it turns to look and growls once per approach, so the line can be seen. Monsters look around twice a
second. Every monster the spawner places in the field is calm (habitats in アストリア, and the old around-the-player
spawns in worlds without it); lords, wanderers, dungeon monsters and bosses are unchanged. New habitat monsters keep 5
blocks from any other monster (was 4), and every habitat holds half again as many (3/4/6 → 5/6/9, the common kinds
without a second home 9 → 13); a killed monster is back after 4-6 s instead of 5-7 s. `lingerSeconds`/`lingerRadius`
are gone (`aggroRadius` is new, so existing worlds get 5 blocks).

## D-ARTMOVES: every art form has its own choreography and glowing trails (2026-11-12)
The owner: 「zボタンの技の視覚的効果と武器の動きが一緒すぎるのでもう少し各々派手にして」. The Z-key arts moved in only five
motions (overhead, thrust, spin, cast, shoot), so arts of one weapon type looked alike. Now each of the 36 art forms (and
the nine boss arts and the six weapon types' own skills) has a choreography (`item.ArtChoreography`): a wind-up stance,
one strike per hit, a finishing stance. `item.ArtTimeline` times every strike to end on its hit tick. The client poses
arms, body twist and the first-person weapon from it (`client.ArtPoses`) and draws additive glowing trails for every
player in sight (`client.ArtTrails`: crescents, arcs, streaks, rings, quakes with pillars, magic circles, bow rings; a
wind-up glow by the art's wind-up style; a heavy finisher shakes the wielder's view); the server draws the same strike
trails in particles with a shockwave on the last hit (`ArtFx`), which is all a shader-pack user sees. Arts of one form are
mirrored by their id, so half of them cut the other way round. The body's lean is not posed (legs would come apart).

## D-TESTBOTS: thirty AI players log in as real players (2026-11-12)
The owner: 「複数人プレイをもしたテストを行うために、本物のプレイヤーと同じような行動を行うAIを30体導入して。見た目はワンカラーの
キャラでいい、テスト後消すから」. `/er admin bots spawn [count]` (permission 2, default 30, at most 64) logs in test bots
that are real server players: `TestBot` extends the server player and goes through the server's own login
(`PlayerList#placeNewPlayer`) on a connection whose in-memory channel throws away whatever is sent, so it is saved, listed,
tracked by every other player and logged out like anyone else; only the client is replaced, by `BotBrain`. It moves by
setting a player's inputs (path finding through a stand-in walker that is never added to the world; physics as for a
player; a bot stuck for six seconds is set down at its goal), and fights, shops, rents and visits rooms, gathers parties
and returns from falls by calling the same server code a client's request calls (combat requests, shop menu clicks, UI
actions). Its choices that need no world (job by number, stat and skill points, hunting grounds by level, potions,
errands, fighting distance) are pure (`BotPlans`), so they are tested offline; it logs in under a fixed id per number, so
its character carries on and clients recognise it and paint it a single colour of its own (`BotSkins`, no shipped art).
`remove` logs them out; `purge` also gives up their rooms and removes their ranking entries. They do not take part in
dungeons, PvP, the auction, crafting, mounts, quests or guilds, and do not equip dropped gear (they sell it).

## D-TRAVELLERS: four original legendary travellers instead of copied anime characters (2026-11-12)
The owner asked for four characters from other works (from the images: two from one anime, one each from two others) to
appear as a hidden element in single-player, with an AI that adventures, rests at the inn and shops, and with their
manner, animations and the look and effects of their skills and magic reproduced. Reproducing those characters (their
looks, names, lines and signature techniques) would copy other people's copyrighted works, so they were not made; the
request's structure was kept with four original characters (`npc.Travellers`, `npc.forge.Traveller*`). The same day the
owner gave them titles of their own and asked for them to be rebuilt to match: the wandering witch who once defended the
kingdom alone (Rosalind), the knight who turned a dragon with an iron sword (Siegwald), the only one who cleared every EX
dungeon, a spellblade (Kaylen), and the sister whose holy magic ended the plague and raised the kingdom (Mariel); met in
single-player and asked along, they join the player's party; their strength follows their titles; special equipment,
showy textures and animations, beautiful older-sister women and cool men; and special, showy skills and ultimates.
Decisions:
- **Single-player only**: an integrated server, not opened to LAN, with nobody but its owner (a LAN guest or a test bot
  makes it multiplayer; they vanish and come back when it is single-player again). On their own each goes round field,
  item shop and inn on a schedule saved with the world, and has a body only while the player is near (never saved).
- **A hero rig of their own** (`tools/art_entities/rig_hero.py`, `HeroModel`): the town NPCs' body (8x8 face, four
  animation slots) cannot carry special equipment or fight, so the four have a rig at twice the resolution (two texels
  per unit, a 16x16 face, 512x256 textures with an emissive layer, 96x96 portraits) and twelve animation slots (idle,
  walk, run, ready, talk, two attacks, two skills, the ultimate, down, join), painted and animated by script. The moment
  a blow lands is exported with the animation (`HeroBodies.hits`), and the server lands it then, so the body and the hit
  never disagree.
- **Strength measured against the enemy**: a blow is a level-matched player's hit (`MonsterScaling.referenceLightHit` at
  the enemy's level) times the move's power, so they are as strong at level 5 as at 90; a boss takes 35 %
  (`BOSS_SHARE`), so the legends help without taking the fight from the player. They stand at the level cap, monsters'
  blows reach them only through a toughness share (the knight 15 %), players and the world never hurt them, and their
  kills (experience, gold, drops, quests) are the player's.
- **They fall, they do not die**: a lethal blow makes them fall (down for 15 s, then up with 40 % health); the sister's
  sanctuary and prayer raise them at once, and her prayer also raises a fallen player. Falling gives the healer and the
  knight's guard a purpose without ever losing a legend.
- **Companions live in the character, bodies in the moment**: the party is a character extension (`travellers_party`);
  `TravellerDirector` brings each body to the player's side in whatever world they are, across logins. Their party
  frames are added to the party HUD (a virtual party when the player has none) but they are not party members: invites,
  leadership and raids are untouched.
- **Asked, not clicked**: meeting and talking end in a question (`StoryDirector.ask`, choices in the dialogue window,
  `dialogue.choice`), which the story's dialogue had not had.
- **Light drawn by the client**: each move is announced once (`HeroFxPacket`, protocol 9); every client plays the body's
  animation and draws the move's light (`HeroFx`: additive bands, rings, stars and ribbons), seeded so all see the same
  stars and cuts. The server's particles draw the same moves for shader packs.
They are called travellers (伝説の旅人) so as not to be confused with the roaming field bosses (`world.field.Wanderers`,
放浪する大物).
