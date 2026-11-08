# ストーリー（`story` パッケージ）

純粋モジュール（Minecraft非依存・不変・スレッド安全）。「どの出来事で、誰が、何を言うか」をデータとして持つ。表示は Forge 層が行う。
物語の内容は [docs/STORY.md](../STORY.md)、台詞の本文（日本語・英語、628キー）は [docs/i18n/story.json](../i18n/story.json)。

## 1. 主なクラス

| クラス | 役割 |
|---|---|
| `StoryCatalog` | `standard()` で取得。`dialogue(trigger)` → `Optional<Dialogue>`、`areaCard(areaId)`、`bossPhaseBark(bossId, phase)`、`ending()`、`questIds(npc)`、`byId(id)`、`translationKeys()` |
| `StoryTrigger` | sealed。`FirstLogin` / `AreaEntered(areaId)` / `DungeonEntered(dungeonId)` / `BossEncounter(bossId)` / `BossDefeated(bossId, firstServerKill)` / `AreaUnlocked(areaId)` / `NpcTalk(npcId, storyProgress)` / `QuestTalk(npcId, questId)` / `FinalPhase(1..4)` / `Ending` / `DayStarted(day, seasonLength, storyProgress)` / `SeasonEnded(kingDefeated)`。`kind()` と `audience()`（PLAYER / PARTY / SERVER） |
| `Dialogue(id, lines)` / `DialogueLine(speakerKey, textKey)` | 台詞。両方とも翻訳キー。`isNarration()` は話者名なしで出す行 |
| `Speaker` | 話者（語りを含む38）。表示名は `story.eternalrealm.speaker.<id>` |
| `StoryNpc` | 話しかけられる相手 22: 町の役割10（`NpcRole` のID）、物語キャラ5（lilia, gustav, nox, laszlo, wolfram）、クエストNPC7（quest モジュールのID） |
| `StoryArea` | 地域 1〜7 と world モジュールの slug、解放するボス |
| `Chapter` | 序章・第一〜八章・終章・エピローグ。題名と1行あらすじのキー。`ofProgress(進行度)` が現在の章 |
| `StoryProgress` | 進行度＝サーバーで初回撃破されたメインボス（b1〜b8, final）の数 0〜9。`of(撃破済みボスID)` |
| `EndingSequence` | エンディングの時間軸（`Beat(atTick, line, CHAT/TITLE)`）とスタッフロール（`Credit(HEADING/CREDIT/PLAYERS, key)`） |
| `AreaCard` | エリアに入ったときのタイトル（地域名＋副題） |

## 2. いつ呼ぶか

| トリガー | 呼ぶタイミング | 相手 | 表示 |
|---|---|---|---|
| `FirstLogin` | シーズンでの初ログイン（ワールドに入って約3秒後） | 本人 | 会話ウィンドウ（序章14行） |
| `AreaEntered(id)` | 地域に入った。カードは毎回、台詞はその地域が初めてのときだけ | 本人 | タイトル＋会話ボックス |
| `AreaUnlocked(id)` | 地域がサーバーに解放された（初回撃破の台詞の後）。地域1は台詞なし | 全員 | 会話ボックス |
| `DungeonEntered(id)` | `DungeonRunEvent.Started`。最終レイド（"final"）はメンバーを転送したとき | パーティー | 会話ボックス |
| `BossEncounter(id)` | ボス出現（`StartBoss`） | パーティー | 会話ボックス |
| `bossPhaseBark(id, k)` | `PhaseChanged`（index = k−1 ≥ 1）。b1〜b8 のみ | パーティー | 会話ボックス（1行） |
| `FinalPhase(k)` | 最終ボスの `PhaseChanged`（index = k−1。k=1 は戦闘開始） | レイド | 会話ボックス |
| `BossDefeated(id, first)` | ボス撃破。`first`＝サーバー初の撃破。first のときは全員にこれだけを出す（星の記憶） | first: 全員／他: パーティー | 会話ボックス |
| `Ending` | 最終ボスの初回撃破（`BossDefeated("final", true)` の後） | 全員 | `ending()` の時間軸 |
| `NpcTalk(npcId, 進行度)` | NPCを右クリック。クエストNPCは話すクエストがないとき | 本人 | 会話ウィンドウ（下記） |
| `QuestTalk(npcId, questId)` | クエストの受注時と TalkTo 目標の達成時 | 本人 | 会話ウィンドウ（読み終えてから受注・達成） |
| `DayStarted(day, 長さ, 進行度)` | `SeasonEvent` STARTED / DAY_CHANGED（既存の「DAY n」タイトルの後） | 全員 | 会話ボックス |
| `SeasonEnded(勝利したか)` | `SeasonEvent` ENDED。勝利時はスタッフロール直後にも流す | 全員 | 会話ボックス |

## 3. 表示のしかた

- **プレイヤーが自分のペースで読む**（0.5.2〜）: サーバーは台詞を1行ずつ時間で流さず、`StoryDirector` が会話をまるごと
  UIデータ `dialogue`（`{id, modal, lines: [{speaker?, portrait?, text}]}`、キーは翻訳キー）でクライアントへ送る。
  - **会話ウィンドウ**（`modal`）: NPCとの会話（`NpcTalk`・`QuestTalk`）と序章。画面下に、話者の顔（`textures/gui/portrait/<speaker>.png`
    があれば）・名前札・1文字ずつ表示される本文。クリック／Space／Enter で「行を最後まで表示 → 次の行」、Esc で残りを飛ばす。
    ゲームは止めない。ほかの画面が開いている間は待つ。
  - **会話ボックス**（それ以外）: ホットバーの上の小さな枠。会話キー（初期 Enter）で次へ。戦闘中の台詞は後から読める。
    `eternalrealm-client.toml` の `dialogue.autoAdvanceAmbient` で自動送りにもできる（会話ウィンドウは常に待つ）。文字速度は `dialogue.textSpeed`。
  - 読み終えた（または飛ばした）会話はチャット履歴に残す（`[話者] 本文`、語りは灰色斜体）。
  - **読み終えてから起きること**: クライアントが `dialogue.done {id}` を送ると、その会話に結びついた処理を実行する
    （クエストの受注、TalkTo 目標の達成、店・鍛冶屋などのメニューを開く）。ログアウトで破棄（もう一度話しかければよい）。15分で期限切れ。
- **店などのNPC**: 進行度ごとに、そのセッションで最初に話したときだけ台詞を会話ウィンドウで見せ、読み終えたらメニューを開く。
  2回目以降はすぐメニュー。しゃがみ＋右クリックで「話すだけ」（メニューは開かない）。
- **タイトル（エリアカード）**: title＝`titleKey`、subtitle＝`subtitleKey`、fadeIn 10 / stay 60 / fadeOut 20 tick。同じ地域は5分以内に出し直さない。
- **エンディング**: `beats` を `atTick` どおりに全員へ（`TITLE` は字幕にも出す）。最後の行から `HOLD_TICKS` 後に `staffRoll` を `creditIntervalTicks`
  ごとに表示（HEADING は金色太字、CREDIT は白、PLAYERS は見出しの後に勝利したレイドのメンバー名を数名ずつ）。続けて `SeasonEnded(true)` の台詞
  （ポストクレジット）。後から入った人向けに再生コマンドを用意してもよい。
- **DAY**: 進行度9（勝利後）は穏やかな朝の台詞、最終日は別れの台詞になる。シーズンが14日でない場合も、最終日と前夜には専用の台詞が出る。

## 4. 進行度と物語のNPC

- 進行度は `StoryProgress.of(サーバーで初回撃破されたボスID)`。シーズンのワールド状態に保存する。`Chapter.ofProgress` が冒険の書の「現在の章」。
- 物語キャラ（lilia, gustav, nox, laszlo, wolfram）は `NpcRole.STORY` の村人に `StoryNpc` のIDを別タグで付けて王都に置く。
  `isPresentAt(進行度)` が false の間（ラズロは b1、ヴォルフラムは b4 の初回撃破まで）は置かない。
- 町の役割10人と物語キャラは進行度ごとに必ず違う台詞を持つ。クエストNPCは要所だけ（それ以外は直前の台詞、地域が開く前は最初の台詞）。

## 5. キーと追加の手順

- 台詞の行: `story.eternalrealm.<dialogueId>.<n>`（n は1から）。dialogueId: `prologue`、`area.<slug>.enter|unlocked`、`dungeon.<id>.enter`、
  `boss.<id>.intro|defeat|first_defeat`、`final.phase<k>`、`ending`、`epilogue.victory|unfinished`、`day.<1〜12>|eve|final|extra|peace|farewell`、
  `npc.<npcId>.<進行度>`。クエストは quest モジュールの形式 `dialogue.eternalrealm.<npcId>.<questId>.<n>`（dialogueId は `quest.<npcId>.<questId>`）。
- 1つのキー: 話者 `speaker.<id>`、章 `chapter.<id>.title|synopsis`、エリアカード `area.<slug>.title|subtitle`、叫び `boss.<id>.phase<k>`、
  スタッフ `staff.<id>`（いずれも `story.eternalrealm.` の後）。
- 追加・変更: `StoryScript` に1行（話者の並び）＋ `docs/i18n/story.json` に同じキーの ja/en。テストが、キーの欠落と余り、1行60字超え、
  英語の未翻訳、ボスのフェーズ数とのずれ、全トリガーの網羅、NPCの台詞の抜けを検出する（`StoryTranslationsTest`, `StoryCatalogTest`,
  `StoryTriggerTest`, `StoryContentConsistencyTest`）。

## 6. 前提

- 地域の解放順は world モジュールに合わせている（2←b1、3←b2、4←b3、5←b5、6←b6、7←b7）。解放の台詞はこの順番を前提に書いてある。
- サイドクエスト（s1〜s6）とデイリーは、その地域のクエストNPCが渡す前提でキーを付けている（例 `dialogue.eternalrealm.arcelia_knight_captain.s1_slime_gel.1`）。
- Forge 層（キュー、タイトル、NPCのタグ、エンディングの再生）は未実装。
