# ランキング・シーズン管理（純粋モジュール `ranking` / `season`）

> 対象: Phase 6（ランキング）と Phase 10（シーズン）の Minecraft 非依存部分。Forge 層との統合は未実装。
> 元の設計は RANKING_SYSTEM.md / SEASON_SYSTEM.md。食い違う箇所は本書（実装）が優先。

## 1. 目的と原則
- 14日間シーズン（10〜50人）のランキングを**サーバーだけ**で集計する。値はすべてサーバーが観測したプレイイベントから作り、クライアント申告値は受け取らない（そのようなパケットを作らない）。
- スコアは `SeasonStats`（統計）からいつでも再計算できる純関数。再起動・設定変更後も同じ結果になる。報酬は見た目のみ。
- サーバースレッド専用（スレッドなし・静的な可変状態なし）。時刻は引数で注入（`Instant` / epoch ミリ秒 / サーバー tick）。

## 2. 主なクラス
| クラス | 役割 |
|---|---|
| `SeasonStats` / `SeasonCounter` | 1人分のシーズン統計（不変）。カウンタ14種＋ level / totalExperience（設定値） |
| `SeasonStatsTracker` | 統計の可変版（プレイヤーごと）。加算は飽和、負の加算は例外、`revision()` で変更検知 |
| `SeasonStatsCodec` | 統計の JSON（キャラクターファイルに埋め込む）。欠落・null は 0、未知キーは無視、型違いは破損 |
| `RankingCategory` | 9カテゴリ（id・翻訳キー・対象 PLAYER / GUILD / TIME_ATTACK） |
| `RankingRules` / `RankingCalculator` | 重み（設定）とスコア式 |
| `Leaderboard<K>` / `RankingEntry<K>` | 1カテゴリの順位表（K = UUID: プレイヤー、String: ギルド） |
| `RankingBoard` | シーズンの全ランキングの唯一の所有者。プレイヤー7種・ギルド・タイムアタック、凍結、保存用スナップショット、アーカイブ作成 |
| `TimeAttackRun` / `TimeAttackValidator` / `TimeAttackBoard` | ボスタイムアタックの記録・検証・順位 |
| `SeasonArchive` / `HeroRecord` / `HallOfFame` | 確定記録（上位N・タイムアタック最速・最終ボス初討伐）と英雄の記録（表彰台）。追記のみ |
| `SeasonSchedule` / `SeasonState` / `SeasonManager` / `SeasonEvent` | シーズンの暦・永続状態・状態遷移とイベント |
| `ResetPolicy` | 次シーズン移行時のリセット設定 |
| ポート `RankingRepository` / `HallOfFameRepository` / `SeasonStateRepository` | 実装は `persistence.file` の `File*Repository`（JSON は `SeasonJsonCodecs`） |

## 3. スコア式（`RankingCalculator`、既定値は `RankingRules.defaults()`）
```
LEVEL       = totalExperience
COMBAT      = 討伐数×10 + エリート討伐数×50 + floor(与ダメージ/10) + PvP勝利×30
EXPLORATION = 発見エリア数×100 + floor(移動ブロック/100)
BOSS = ボス討伐数   CRAFTING = 生産数   PARTY = 2人以上でのダンジョンクリア数
GUILD       = メンバー全員の guildContribution の合計（RankingCalculator.guildScore）
OVERALL     = Σ 重み(c) × floor(10000 × s(c) / (s(c) + 半分点(c)))   c = LEVEL, COMBAT, EXPLORATION, BOSS, CRAFTING, PARTY
```
- 半分点に達するとそのカテゴリの持ち点の半分。1カテゴリの寄与は「重み×10000」が上限なので、1つだけ稼いでも総合を独占できない（逓減）。他プレイヤーに依存せず1人分の統計で計算できる。
- 既定の重み LEVEL 2 / COMBAT 2 / EXPLORATION 1 / BOSS 2 / CRAFTING 1 / PARTY 1、半分点 200,000 EXP（既定曲線で Lv30前後）/ 50,000 / 5,000 / 10 / 100 / 10。「単位あたり点」の 0 はその項目を数えない。
- エリート討伐はモンスター討伐にも数える。ボスはモンスター討伐に含めず、参加者全員の bossesKilled に数える。与ダメージは 0.1 単位で保存。
- スコア 0 のカテゴリには載せない。演算はすべて飽和（オーバーフローで負にならない）。

## 4. 順位の規則
- 同点は「そのスコアに先に到達した方」が上、次に ID 順。スコアが変わらない更新では到達時刻を変えない（統計を送り直しても同点の順位を失わない）。カテゴリ別の到達時刻を保存するので再起動後も同じ順位。
- `Leaderboard` はソート済み配列＋ハッシュ索引。順位検索 O(log n)、上位k件 O(k)、更新 O(log n)＋配列シフト（数千件でも数µs）。

## 5. ボスタイムアタック
- 記録はサーバーが取る: 開始・撃破の tick とサーバー時刻、開始時のパーティー（UUID・名前・職業・レベル）、不正フラグ3種。順位は tick（ラグで遅くならない）。
- 却下（理由は翻訳キーで該当分すべて）: 未登録ボス、endTick ≤ startTick、ボス別最短 tick 未満（既定200）、実時間 < tick×50ms×90%（ラグで長いのは可、128bit で厳密比較）、参加者0人・21人以上・重複、クリエイティブ／スペクテイター、管理者コマンド、途中のメンバー変更、凍結後。
- 順位表はボス×難易度（NORMAL / HARD / EX）×人数枠（SOLO 1 / PARTY 2〜5 / RAID 6〜20）。速い順、同タイムは撃破時刻が早い方、次に runId。
- 同じパーティー（メンバー集合）は最速の1件だけ残す（周回で上位を埋めない）。個人ベストは必ずどれかのパーティーベストなので追加保存は不要。
- ボスごとの「シーズン初討伐」（受理された最も早い撃破）は別に保持。最終ボスの初討伐は英雄の記録に載る。
- 管理者の取り消し（`removeTimeAttackRun`）で個人ベストと初討伐を再計算（初討伐は残っている記録で最も早いもの）。

## 6. シーズンの状態とイベント
- DAY n = 開始時刻 + (n−1)×24時間（タイムゾーン非依存）。DAY は目安で、進行を待たせる仕組みではない（D-003）。
- 状態 PREPARING → ACTIVE → ENDED → ARCHIVED（逆行しない）。`tick(now)` が STARTED / DAY_CHANGED(n) / FINAL_DAY / ENDED / ARCHIVE_REQUIRED をこの順で返す。
- 告知イベントは発行と同時に `SeasonState` に記録されるので、**再起動を挟んでも1回だけ**（呼び出し側が変更のたびに状態を保存する前提）。
- 時刻が数日飛んだときは**最新の日だけ**告知する（途中の日はまとめて告知しない。日付で決まる処理は `dayOf` を見る）。終了時刻を過ぎていれば DAY 告知なしで ENDED。STARTED は必ず ENDED より前。時計が戻っても何もしない。
- ARCHIVE_REQUIRED は告知ではなく作業依頼: `markArchived` で確認されるまで、マネージャーのインスタンスごとに1回（＝再起動後にもう一度）。アーカイブ前のクラッシュで英雄の記録を失わないため。殿堂への追記は同じシーズンでは無視されるので繰り返しても安全。
- 管理者操作: `start(now)`（予定前なら今から開始し日程をずらす）、`forceEnd(now)`、`requestArchiveAgain()`。`recordFinalBossClear(now)` は ACTIVE 中の最初の1回だけ記録。
- 起動時は `SeasonManager.atStartup(保存状態, 設定の日程)`: 未開始なら設定に従う／開始済みなら保存状態（日程の変更は無視）／別シーズンへの切り替えは旧シーズンが ARCHIVED のときだけ（それ以外は例外）。

## 7. 保存（`persistence.file`、AtomicFileStore・schema 1・新しい schema は読まない）
| ファイル | 内容 |
|---|---|
| `seasons/<id>/rankings.json` | 全プレイヤーの統計＋カテゴリ別スコアと到達時刻、ギルドスコア、タイムアタック（パーティーベスト＋初討伐）、凍結フラグ |
| `season_state.json` | 現在のシーズンの `SeasonState` |
| `hall_of_fame.json` | 全シーズンの `SeasonArchive`（追記のみ） |
- 開催中はプレイヤースコアを読み込み時に統計から再計算（設定変更に追従、到達時刻は保存値）。凍結後は保存済みスコアを確定値としてそのまま復元する。起動時にキャラクターファイルを全部読む必要はない。
- ギルドスコアはメンバー構成がギルドモジュールの持ち物なので値として保存。
- 殿堂への追記は毎回ファイルを読み直して書く（メモリ上の古いコピーで履歴を消さない）。読めないとき（破損・新しい schema・I/O エラー）は何も書かずに失敗する。前世代のない破損ファイルは quarantine/ に移されるので、次のアーカイブの前にそこかバックアップから戻すこと。
- rankings.json が読めない場合は空のボードで始めない（次の保存で上書きになる）。復旧できなければ、キャラクターの統計から `PlayerStanding`（スコアなし）を作って `rebuild` する（タイムアタックとギルドは失われる）。

## 8. Forge 層がすること
1. 起動: `SeasonStateRepository.load()` → `SeasonManager.atStartup(...)`（例外ならシーズン機能を止めて ERROR）→ 状態を保存。`RankingRepository.load(id)` → `RankingBoard.rebuild(snapshot, rules)`（無ければ `new RankingBoard`）。ENDED / ARCHIVED なら `board.freeze()`。
2. 毎秒: `seasonManager.tick(now)` → 変化があれば状態を保存 → STARTED / DAY_CHANGED / FINAL_DAY は告知（引数: シーズンID・日・日数）、ENDED は `board.freeze()` して保存、ARCHIVE_REQUIRED は `board.archive(now, 100)` → I/O で `append` → 成功したら `markArchived(now)` → 保存。
3. プレイイベント → `SeasonStatsTracker`（キャラクターと一緒にロード・保存、`isRankingOpen()` の間だけ数える）: キル（エリート判定）、適用後ダメージ、ボス討伐（参加者全員）、死亡、ダンジョンクリア（人数）、クエスト、エリア発見、移動距離、生産、PvP、ギルド貢献、EXP 変化時の `setProgress`。
4. `tracker.revision()` が変わったプレイヤーだけ、1秒に1回程度とログイン・ログアウト時に `board.updatePlayer(...)`。ギルド貢献・構成の変更時に `updateGuild(id, 名前, guildScore(メンバー統計), now)`。
5. ボス撃破 → `TimeAttackRun` を組み立てて `board.submitTimeAttack(run, rules)` → 却下理由・初討伐・新記録・自己ベストを通知（翻訳キー定数は `TimeAttackSubmission`）。最終ボスなら `recordFinalBossClear(now)`。
6. オートセーブ: `board.isDirty()` なら `snapshot()` を I/O スレッドで保存 → 成功後 `markSaved(revision)`。
7. 設定: `RankingRules`、`TimeAttackRules`（ボス一覧・ボス別最短 tick・実時間許容率）、`ResetPolicy`、開始日時と日数。リロード時は `board.setRules(...)`。
8. 翻訳: `docs/i18n/ranking.json` を lang ファイルに統合する。

## 9. 決定事項・未実装
- `ResetPolicy` に「コスメ・殿堂を残す」のスイッチは置かない。恒久データ（アカウント・コスメ・称号・実績・殿堂）はシーズンディレクトリの外にあり常に保持する。レベルをリセットするならステータス割り振りも必ずリセット。
- RANKING_SYSTEM.md のスコアイベント方式（idempotency_key）ではなく、統計からの再計算方式を採用した。二重加算の防止は呼び出し側（1つの出来事につき1回だけ加算）。
- 疑わしいタイムアタックは「保留」ではなく却下（管理者の承認フローは未実装）。
- 未実装: 役割評価（タンク・ヒーラー等）、同一行動の反復の逓減、談合検知、PvP・実績カテゴリ、報酬の付与。
