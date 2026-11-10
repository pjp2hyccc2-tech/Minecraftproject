# 設計判断の記録（ADR）

仕様書は3つあり（開発指示書・最終企画書／技術設計書・2.0追加仕様書）、一部が矛盾します。ここに解釈と判断を記録します。
**「要確認」の項目はオーナーの判断で変更できます。**

| ID | 判断 | 理由 | 状態 |
|---|---|---|---|
| D-001 | 仕様の優先順位は **2.0追加仕様 ＞ 最終企画書 ＞ 開発指示書** | 2.0は既存方針を明示的に変更する後発の文書 | 確定 |
| D-002 | レベル上限は**初期50**（サーバー設定 `character.levelCap`）、データモデルの上限は100 | 2.0「初期レベル上限50」と指示書「最大レベル100」を両立 | 確定 |
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
