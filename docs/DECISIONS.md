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

## D-SKILLFX: every job skill and ultimate is a show of light drawn by every client (2026-11-12)
The owner: 「この四人のスキルのように全部のアクティブスキルと必殺技の視覚的効果、エフェクトを作り直して。高クオリティで派手に
仕上げて」. The 187 actives and 33 ultimates had server particles only. Decisions:
- **Generated from the skill, hand-made for ultimates**: a pure engine (`job.fx`) turns each skill's own data (hit frames
  and their shapes, projectiles, effects, theme, job) into a show of light layers on the skill's timeline, so every
  active gets light that matches what it does, and a skill changed later changes its light with it. The 33 ultimates
  get hand-made finales (`Ultimates`). A test keeps any two skills of a job from looking alike.
- **No new data for a skill's own light**: every client already learns when a player's action starts
  (`PlayerActionPacket`, or its own prediction) and builds the same show from the same definitions; only what lands
  away from the caster is announced (`SkillFxPacket`, protocol 10: ground areas for their whole life, blasts of
  exploding spells) and skill projectiles carry their theme as entity data. The server's particles stay (and are all a
  shader pack sees).
- **Light that reads from where you stand**: a cut is a band in a plane through the caster; seen edge-on (the
  third-person camera straight behind, or the caster's own eye a hand above every cut) it is a thin line. Each client
  turns cut planes a few degrees towards its own camera (at most 48°) and pushes them off the caster's own eye in first
  person; light hugging the caster's body thins out near the camera. The same pure code draws the software preview, so
  every skill was looked at in third and first person before it reached the game.
- **Budgets**: one show at most 16,000 quads in a frame (tested at every tick), 48,000 for all skill light of a frame on
  a client (farther shows wait), drawn within 96 blocks; a client switch (`combat.skillEffects`).

## D-SPAWN35: habitats spread half as far again and hold 3.5 times as many monsters (2026-11-12)
The owner: 「フィールドの敵スポーン範囲を広くして、湧く数を今の３．５倍にして」. The 157 habitats keep their places (their home
grounds are fixed by D-HABITAT2X's tests), but their monsters now appear and roam over 1.5 times the radius
(`Habitats.SPREAD`, 40 → 60 blocks), only where `Habitats.spawnable` allows: in the habitat's own field, outside every
other habitat's home ground and clear of the capital, the waystones, the lords' territories, the calamities' lairs and
the dungeon entrances (the same things a home ground keeps away from). Every population is 3.5 times what it was
(5/6/9/13 → 18/21/32/46, `Habitats.CROWD`), a half-empty habitat fills 3.5 times as fast and a killed monster is back
after about 1.2-1.7 s instead of 4-6 s, and the server caps grow with them (252 + 95 per player). A monster never appears
more than 80 blocks from every player (it would be despawned at once). Without アストリア, the old around-the-player
spawns reach 56 blocks instead of 40 and hold 74 per player instead of 21. The map draws the wider ring faintly around
each habitat.

## D-STATPERCENT: primary stats grow by percentages from trinkets and armor; six trinket slots; a smithy catalogue (2026-11-13)
The owner: 「ステータスの伸びをもっと増やして欲しい、例えばお守りとアクセサリーを増やして、それも生成できるようにする。で、STR+10とか
じゃなくてSTR+10%とかにして、強化もできるようにする。工夫しだいでSTRを4000位まで伸ばせるようにしてほしい。防具にもその機能つけたり」.
A level-100 build topped out near STR 614 (×13 physical damage). Decisions:
- **Percentages after every flat source**: `final = min(9999, floor(flat × (1 + Σ%)))` (`StatPercents`, `ComposedStats`).
  Percentages add up instead of multiplying, so the reach is predictable and no single piece dominates; they apply to
  own + job + equipment flat stats alike, so levelling and allocation keep mattering.
- **Where percentages come from**: six new affix kinds (`STR_PERCENT`..`LUK_PERCENT`, 2-4 % on tier 1, 8-12 % on tier 7)
  that accessories, charms and armor roll — armor gives STR %, INT % and DEX % the same weight on every piece, plus each
  piece's own stats; weapons keep their flat stat and damage percentages — and the trinkets' own stats, now percentages
  (relics converted, ×1.25 of a catalogue trinket of their level).
- **Enhancement grows percentages faster**: +10 % of themselves per level (×2 at +10), where other affixes do not grow at
  all and base values grow 6 % per level. Enhancing a percent piece is the deliberate long-term sink.
- **More trinkets and more slots**: four accessories and two charms (the three new slots appended to the saved list, so
  older saves keep their places), and a smithy catalogue of 156 trinkets (13 grades from Lv1 to Lv100 × six focuses ×
  accessory and charm), forged from tier materials, past level 50 from abyss materials, field elites and the end soul;
  grades 1-8 also drop in the fields (10 %, elites 18 %, the killer's job stats twice as likely).
- **"工夫" is variety**: the same named trinket still counts once, so four STR accessories mean four different ones (the
  best grades of the catalogue, relics); a perfect level-100 STR build reaches STR 3,912 (+707 %) — test-pinned to
  3,600-4,800 — while a level-55 epic +5 build sits near 470.
- **Old pieces stay valid**: flat primary stat affixes on accessories and charms remain allowed ("legacy" kinds of the
  pool: they still count, never roll again); affix kinds are never removed (an unknown kind makes a stack unreadable).
- **Visible**: tooltips show counted percentages and how enhancement raised them, the character screen a gold "+707%"
  column, the equipment screen totals and comparisons percentage rows; the character snapshot carries the percentages
  (protocol 11) and is re-sent whenever gear changes the stats.
- **Not rebalanced (yet)**: monster health and damage are unchanged, so endgame damage rises steeply (STR 4,000 ≈ ×81
  physical damage). Scaling monsters is a separate decision for the owner.

### D-STATPERCENT, revised the same day: every specialised stat reaches about 4 000, and the capped stats overflow
The owner: 「STRだけじゃなくて、そのステータスに特化したらどれでもそれくらいいくようにして」. Two things stood in the way:
- **Sources were uneven**: armor rolled STR %, INT % and DEX % on every piece but AGI % and LUK % on one or two, and AGI
  and LUK sat flat on only two armor pieces. Now every armor piece rolls all six percentages (weight 1 each), AGI is
  added flat to the chest and LUK to the legs (three pieces each, like STR, INT and DEX; VIT on all four). A perfect
  build of any stat collects +706.7 %: STR, AGI, DEX, LUK 3,912, VIT 4,081 (a priest's staff rolls VIT), INT 4,711 for
  the mage whose job grows INT twice as fast (3,912 for a priest). `StatGrowthTest` checks all six.
- **Three stats stopped working long before 4 000**: max health is capped at +1,000 (Minecraft's attribute), damage
  reduction at 75 %, attack speed at ×0.6 action time, critical chance at 75 %. Instead of raising those caps (which
  would break the vanilla attribute, animations and invulnerability), what lies beyond a cap turns into something
  else, with diminishing returns and its own cap: excess critical chance becomes critical damage 1 : 1 (LUK, within ×10),
  excess attack speed becomes evasion up to 30 % (AGI; with trinket perks and the evasion effect at most 60 %), excess
  max health becomes damage reduction on top of the cap, up to +15 % (VIT, 90 % at most). Below the caps nothing changes.
  CombatStats carries the evasion (protocol 12).

## D-CALAMITYGROWTH: a field's calamity comes back one level stronger after every defeat; its relics drop at 1 % (2026-11-13)
The owner: 「各フィールドの災厄は倒される毎にレベルが1上がるようにして」「災厄のレアの報酬は0.1%から1%に変更」. Decisions:
- **Per field, saved with the miasma**: `CalamityGrowth` counts the defeats of each field's calamity; level = base + defeats,
  capped at 100 (the monster level cap). The counts live in Astoria's threat data next to the miasma, so they survive a
  restart; broken data starts over at zero instead of failing the load.
- **Grown like the rest of the world**: health × referenceLightHit(level) / referenceLightHit(base) — the same curve that
  sets a player's reference hit at that level, so the fight takes about as many hits at every level (Lv15 → 25 ×1.43,
  Lv15 → 100 ×4.7). Damage grows by 70 % of that (×3.6 at the extreme) so a grown calamity hurts more without becoming
  a one-shot; phases, abilities and armor do not change (`BossDefinition.atLevel`, which returns the definition itself
  when nothing changes).
- **The rewards follow the level**: experience and gold are computed at the calamity's current level (never below the
  reward template's minimum), so farming a grown calamity pays more. The level is shown wherever the calamity is
  (manifest announcement, boss HUD, bounty board, map) and announced after each defeat ("次に現れるときは Lv○○",
  or that it has reached its peak).
- **Relics from calamities at 1 %**: calamities are rare, server-wide events that need many players, so their relics
  (`Relics.SourceKind.CALAMITY`) drop at 1 % per rewarded player (server config `calamityRelicDropChance`, default 0.01).
  Dungeon bosses, lords and wanderers keep 0.1 % (`relicDropChance`).

## D-NAMEDTRINKETS: 48 named accessories and charms of the lords and dungeons, eight new kinds (2026-11-13)
The owner: 「アクセサリーとお守りの種類を増やして、ダンジョンの報酬、宝箱報酬、ヌシドロップ、生成、からも手に入るようにして」. Decisions:
- **More kinds**: eight new trinket kinds (brooch, circlet, anklet, medal; mirror, feather, beads, doll), appended to
  `TrinketKind` so saved stacks keep their kind; each with its own 16×16 icon pattern.
- **Named, not random**: one trinket per lord (30, accessories and charms alternating) and an accessory and a charm per
  dungeon and the final raid (18), at the level of their source (a lord's field cap + 2, a dungeon's recommended level).
  Each has two stats by percentage (main 1.1 × and second 0.4 × the catalogue trinket of its level) and one effect of its
  own (critical chance or damage, damage, attack speed, movement, healing, max health %, defense, life steal, counter,
  evasion, heal on kill, last stand, skill power, or an on-hit burn, slow, poison, bleed, stun or freeze), growing with
  the level and the rarity's power. They are the "story" pieces between the catalogue's plain grades and the relics.
- **Every source the owner listed**: lord drops (6 % per contributor, 15 % for the hero), dungeon clear rewards (the
  dungeon's own: NORMAL 12 %, HARD 20 %, EX 30 %), the bonus chest (a trinket card, weight 8 + 4 × the clear tier — half
  the dungeon's own, half the catalogue's), 15 % of every reward equipment piece of dungeons, lords and calamities turns
  into a catalogue trinket, and the smithy (each named trinket from its field's materials and boss soul).
- **One data file**: `tools/art/named_trinkets_data.py` generates the Java table, the names and lore in both languages,
  the icons, models and base-item overrides (like the relics), with a `--check` mode; ids `trinket_lord_*` /
  `trinket_dungeon_*` and models 5400+ are fixed so saved stacks stay readable.

## D-LIGHTWEIGHT: lighter without giving anything up — chunks made ahead, dungeons built in slices, idle monsters resting, a scrolling minimap, still flags on request (2026-11-13)
The owner asked to make the game lighter without dropping a single feature (「12チャンクごとくらいに自動読み込み(通知無し)で
入れて動作を軽くする…このMODの機能をひとつも捨てずに」), had the places to improve listed first, then approved four of the five
(「1と2と3と5を実装して設定で旗の揺れのアニメーションを消せるようにして、4は触らないでいい」; 4, drawing only the glowing parts
of the bodies, was left alone). Decisions:
- **Chunks made ahead (1)**: the map is fixed, so its ~44,000 chunks (with an 8-chunk margin) can be generated once; a chunk
  made earlier is only read from disk when a player arrives. First the band from just beyond each player's view out to 12
  chunks further, nearest first to where the player is heading (the owner's "12 chunks"), then the whole map ring by ring
  from the capital. Tickets instead of blocking loads; only while the average tick is below 35 ms; 2 chunks at once at first,
  adapting up to 6; nothing in the first minute, no messages. Chunks made in advance keep the land of the version that made
  them (as visited chunks always did): a change of the land raises `FieldLayout.VERSION`, which regenerates アストリア.
- **Dungeons in slices (2)**: building (6,000 blocks a tick, with neighbour shape updates) and clearing (12,000) made a few
  seconds heavy at every entry and exit. Sites now work until a time limit per tick (12 ms while a party waits, 3 ms in the
  background), set blocks without shape updates and only where they differ, fit the bars and fences once afterwards (like the
  capital's blueprint) and hold their chunks with tickets so no tick waits for a load. A site is built while a player walks up
  to the gate (or opens a gatekeeper's list after a run), so the run usually starts at once; members are free as soon as a run
  closes. The forest's leafy floor marks became persistent leaves (they could decay). Slots holding blocks are written
  down (dungeon and seed) with the dungeon level; what a stopped server left (a run, a prepared site, a half-cleared one)
  is rebuilt as a layout, reserved and cleared in the background at the next start before the slot is used again.
- **Idle monsters rest (3)**: the 3.5× habitats stay. A field monster that does nothing and has no player within 32 blocks
  runs one whole tick in four (spread by id); anything that changes — a player near, a hit, a push, a stroll it decides on —
  wakes it on the next tick. Dungeon waves, bosses and levels without players always run every tick (so GameTests are
  unaffected).
- **Scrolling minimap (5)**: the 256×256 texture wraps around (block (x, z) at texel (x mod 256, z mod 256)); a step paints
  the strip that comes into view, a new chunk colour just its chunk, and only those rectangles are uploaded (instead of
  65,536 texels and the whole texture up to ten times a second). Identical picture.
- **Still flags, on request**: client setting `scenery.flagAnimation` in a new in-game settings screen (pause menu, Mods
  list), which also switches every other on/off client option. Off: cloth flags hang still in a light breeze and keep their
  shape (no cloth computation), bunting stops swinging, the game's ~400 banners in the capital are held at their time-0
  angle by the game's own renderer (a partial tick that cancels the game time). On by default; nothing else changes.

## D-GATESCREEN: the gatekeeper opens a screen of its own (転移門) instead of a chest menu (2026-11-13)

The owner found the dungeon selection bland (「ダンジョンの選択画面のHUDをファンタジーなものに変更して、マイクラ本来のままだから
味気ない」): it was a vanilla chest with item icons, where the click type chose the difficulty. Decisions:
- **A screen of its own**, drawn from textures made by script (no vanilla widgets): a night-blue panel in a gold frame with jewelled
  corners and a red title ribbon, turning magic circles and rising motes behind; the list with gold medallion emblems; a painted
  picture per dungeon (nine, original, at two texels per GUI unit with smooth filtering) that drifts slowly, with the theme's
  motes over it and chains while sealed; gems for the difficulties; a crimson button. Everything the chest showed is kept
  (level, party size, sealed or not, the three difficulties) and more is shown: the boss, time limit, rooms, a few lines about
  the dungeon, what breaks a seal, what each difficulty does to the boss and pays, the player's best time, whether the
  dungeon's master has fallen this season, and why the player cannot set out. It shrinks to fit small windows.
- **The server decides, the screen asks**: the board comes from a pure `GateBoard`; setting out is a UI action that is only
  accepted from a screen a gatekeeper opened within 5 minutes, 8 blocks away at most (the chest menu was bound to the open
  container in the same way); `DungeonService.start` now returns its refusal so the screen can show it. The reward table
  (rarity floor, stones) moved to `DungeonRewards` so the reward and the screen read one table.
- **Opens where it makes sense**: an entrance's gatekeeper opens on its own dungeon; otherwise the last run, otherwise the
  hardest open dungeon the player's level is ready for. The difficulty chosen last stays chosen. The screen does not pause
  the game, so the dungeon prepared for the player keeps building while they read.

## D-READYCHECK: a party is asked whether everyone is ready before it sets out (2026-11-13)

The owner asked whether several parties opening the same dungeon at once would overlap (they do not: every run is built in a
slot of its own, 1,024 blocks apart, and a slot is reused only once cleared) and for a ready button per member
(「メンバーの準備ができてるかのボタンを置いて欲しい 準備中、準備OKって感じで」). Decisions:
- **Only for parties**: a player alone still sets out at once; a party of two or more led by the player at the gate gets a
  ready check. The leader is ready from the start (they asked); the others answer 準備OK / 準備中 and can change their mind.
- **Everyone ready → 3-second countdown → set out**, without the leader pressing again; anyone going back to 準備中 stops the
  countdown. The leader can call it off; a changed party (someone leaves, joins, goes offline, enters a dungeon, the leader
  changes) ends it; so do 2 minutes without everyone ready (the chat says who was not ready). `DungeonService.start` checks
  everything again when the countdown ends, and the dungeon's site is built while the party gets ready.
- **Answering from anywhere**: members may be in the field when the leader asks, so the check opens a screen only for those
  with no other screen up, and stays on the HUD (and on the party frames) while closed; the chat carries [準備OK] [準備中]
  [画面を開く] buttons (`/dungeon ready ...`). No new key binding.


## D-QUESTPATH: yellow dots lead to where the guided quest wants the player next (2026-11-13)

The owner asked for the way to a quest's place to be shown with yellow dots (「クエストの場所までの道を黄色い点々で表示してあげて欲しい。
入り組んでて分からない人多いと思うから」). Decisions:
- **The server knows where, the client knows the way**: the server picks the place from the quest (pure `QuestGuide`: report to
  the giver when ready, otherwise the first unfinished objective — an NPC, a field, the nearest haunt of a monster or of one that
  drops the item, a dungeon's gate) and sends only that point; the client finds the walkable way over the blocks it has loaded
  (pure `PathSearch`, an A* over standing cells: steps up one block, drops up to three, swims at a cost, never into lava or fire,
  through wooden doors and gates). The server never pathfinds for players, and the client needs no world it does not have.
- **A little each tick, never a hitch**: 1,500 cells per tick, within 96 blocks and 30,000 cells. A place farther than that, or
  in chunks not loaded yet, gets dots to where the route comes closest, searched again as the player walks (every 3 s after
  12 blocks) and at once when they stray 4 blocks from the dots. Dots walked past disappear.
- **One quest at a time, the player's choice**: the journal's quest page has a 案内 button per quest; the main quest is guided
  until the player picks another, and pressing the guided one again turns the guide off. The settings screen can hide the dots
  altogether. Quest NPCs that walk about are found where the server last saw them.
- **Readable, not loud**: small glowing discs on the ground with a slow wave of light running towards the place, fading out
  past 56 blocks; a pillar of golden light where the route reaches the place; the same dots, a golden star and the distance on
  the minimap. No depth writes, so dots behind walls stay hidden.

## D-FANTASYCHAT: a small gold-framed chat while playing, the vanilla chat in the same frame while typing (2026-11-13)

The owner asked for the chat to be shown small where it is not in the way, with a fantasy frame (「チャットの表示を邪魔に
ならない場所に小さく表示してそれもファンタジー風のHUDと枠を作って」). Decisions:
- **Where the chat already is, but smaller**: bottom left, on the vanilla chat's baseline (40 px above the bottom, clear of the
  skill bar), about a third of the screen wide (170–240 px instead of 320), text at 0.85 of the vanilla size, 5 lines (2–10 in
  the config) instead of about 10, shrinking to its content and gone 10 s after the last message. The HUD layout reserves only
  this, so the rest of the HUD gets the room back.
- **Our own drawing while playing, vanilla's while typing**: the small panel draws the messages itself (Forge's chat-received
  event, after every other mod), so it can wrap them at its own width and show players' lines as "名前: 本文". While typing the
  vanilla chat is left to draw itself, framed, so scrolling, clickable links and hover text keep working untouched. No
  reflection into the vanilla chat and no mixins: lines another mod writes straight into the chat on the client show only in
  the open chat, and so does the conversation log (it was just read).
- **Fantasy, quietly**: a night-blue translucent ground, a gold band with a sapphire at each corner and a jewelled crest on the
  top edge (pixel art by script), the typing line framed in gold. It follows the vanilla chat settings (hidden, opacity, size,
  line spacing), can be switched off, and switches itself off after a rendering error.

## D-PHONE: the 魔導スマホ — friends, messages, photos and a board, on the server, with a phone of its own (2026-11-13)

The owner asked for a fantasy-looking phone to keep in touch with friends, post screenshots and use an in-game social
network (「ファンタジー風の見た目のスマホをつくってフレンドと連絡を取り合ったり、スクショを投稿したり、ゲーム内SNSも作って」).
Decisions:
- **One phone, five apps, no new world objects**: a screen opened with P (vanilla's social interactions moves to F9) or from the
  journal — Friends, Messages, Camera, Board (つぶやき), News — drawn as an ebony-and-gold phone with a crystal screen and a
  moonlit wallpaper (original art by script). The game does not pause.
- **Everything lives on the server, in one document**: friends, requests, blocks, names, conversations, posts and the photo index in
  `<data>/phone.json` (atomic writes with a previous generation, saved off-thread every minute when changed). A file that cannot be
  read is never overwritten. The board is server-wide (everyone on the server), messages are between friends only.
- **Photos are real but small and checked**: the client shrinks a screenshot to 384×216 JPEG (≤ 96 KB) off the render thread and
  sends it in six 16 KB chunks at most (protocol 13); the server reads the size from the header before decoding, decodes within
  limits, re-encodes it itself (no metadata survives) and keeps it under a random 16-hex-digit name, never a client-chosen path.
  A photo can be used once; unused ones go after 15 minutes; at most 400 are kept. Players fetch only photos they may see.
- **Safety without a moderation team**: text is cleaned (no formatting codes, control or invisible bidi characters), lengths and
  rates are capped (posts 30 s apart and 40 a day, photos 15 s apart and 30 a day...), blocking hides each other entirely,
  operators (permission 2) can delete any post or comment in the phone or with `/phone admin delete`.
- **Live but quiet**: an open phone refreshes when friends write, like or comment; a closed one gets badges and, for messages,
  friend requests and comments, one line in chat with a button. Likes only change the badge.

## D-NOVANILLA: no stray vanilla items on the ground in アストリア and the dungeons (2026-11-14)

The owner: 「バニラのアイテムが落ちててそれを勝手に拾ってバッグがパンパンになるからバニラのアイテムは落ちないようにして」.
The owner's world had about 1,300 stacks lying in the fields where they fought — pink petals, seeds, flowers, crops, sticks —
knocked loose by sweeping attacks and trampling; our monsters' own drops were already cancelled.
Decisions:
- **Refuse them where they appear**, not at each source: an item entity joining アストリア or the dungeons is refused when its item is
  `minecraft:` and nobody's name is on it (no thrower, no owner, no keep mark). One rule covers plants, leaves, crops, vanilla mobs and
  whatever else breaks, and it also clears what already lies there when the chunk loads.
- **Never take what is someone's**: a player's tosses (Q, dragging out of a window, a full inventory pushing items out) are marked
  through `ItemTossEvent`, a dying player's drops through `LivingDropsEvent`, loot that does not fit the bag carries its owner, and what
  spills from a block holding things that a player breaks (a chest, a barrel, a furnace) is kept for that tick and the next.
- **Blocks can still be broken**; they just do not drop vanilla items there. Player rooms and guild houses keep vanilla rules, and no
  collect quest asks for a vanilla item (checked by the quest validation).

## D-TOCAPITAL: to the royal capital from anywhere, and rests of 30 s (2026-11-14)

The owner: 「どこからでも王国へテレポートできるようにして。自室へのテレポートのインターバルが600秒とか書かれてる時があったから最高でも40秒とかにして」.
Decisions:
- **The capital is a second destination of the same cast** as the way home (End key, the journal's 「王都へ」, the map's 「王都へ転移」): a
  3 s cast that moving or a hit breaks, refused in a fight, a dungeon, a boss fight, a PvP match or while fallen — so it is a way home,
  never an escape. It arrives where new players arrive. Dungeons keep their own way out.
- **Rests of 30 s, counted per destination**; the remaining time can never show more than that.
- **The 600 s was a stale clock**: rests were counted in server ticks, and the tick counter starts again at zero when a world is
  opened again in the same game while the old arrival stayed in memory. Arrivals are now forgotten when the server stops, and an
  arrival "after now" never counts.

