# 音楽・効果音（`sound` / `client.sound` パッケージ、`tools/audio`）

ETERNAL REALM の BGM・ジングル・効果音はすべて **このリポジトリの Python コードで作曲・合成したオリジナル** です。
サンプル音源・既存曲・外部ライブラリの音は一切使っていません（numpy / scipy で合成し、ffmpeg の libvorbis で OGG に変換）。
生成は決定的で、同じソースからは毎回バイト単位で同じ OGG ができます。

## 1. 曲一覧

全曲 44.1 kHz ステレオ、ラウドネス −14 LUFS（ダンジョン・エンディングは −15 LUFS）、トゥルーピーク −1 dBTP 以下。
ループ曲は **ファイル全体がそのまま継ぎ目なしの1周期** です（フェードなし。詳細は §5）。

| サウンドID | ファイル | 場面 | 調 | テンポ・拍子 | 長さ | 雰囲気・編成 |
|---|---|---|---|---|---|---|
| `music.title` | `music/title.ogg` | タイトル画面・拠点 **アルセリア王都** | ヘ長調 | 96 BPM 4/4 | 80.0 s ループ | 温かく勇壮。ホルンの主題 → ヴァイオリン＋ホルン対旋律と小太鼓の行進 → 木管のB部（下行バス）→ トランペット・合唱・グロッケン・ティンパニの全合奏 |
| `music.area_1` | `music/area_1.ogg` | ① **アルセリア王国** | ト長調 | 6/8（付点四分=64） | 75.0 s ループ | 明るい牧歌。オカリナ、リュートのアルペジオ、フルート＋クラリネット対旋律、弦の歌、フレームドラム・タンバリン |
| `music.area_2` | `music/area_2.ogg` | ② **エルダーフォレスト** | ニ・ドリア | 84 BPM 3/4 | 77.1 s ループ | 神秘的。息の多いフルートとハープ、チェレスタの「光」（Fmaj7♯11）、合唱「u」、深い森のホルン（B♭の借用）、葉ずれと風鈴 |
| `music.area_3` | `music/area_3.ogg` | ③ **サンドリア砂漠** | ホ・フリギア属音（ヒジャーズ: E F G♯ A B C D） | 100 BPM 4/4 | 76.8 s ループ | 異国的。F–G♯ の増2度を歌うネイ、和音に沿うウードのリフ、タンプーラ風ドローン、ダラブッカ（マクスーム D T – T D – T –）、ジル |
| `music.area_4` | `music/area_4.ogg` | ④ **ノルディア雪原** | ロ短調 | 72 BPM 4/4 | 80.0 s ループ | 冷たく透明。オルゴールの動機、チェレスタ、FM合成の氷のきらめき、ピアノの空虚五度、独奏ヴァイオリン、合唱、寒風（add9 / sus2 / maj7♯11 の開いた響き） |
| `music.area_5` | `music/area_5.ogg` | ⑤ **ドラゴニア火山** | ニ短調（フリギアの ♭II = E♭） | 132 BPM 4/4 | 72.7 s ループ | 重い太鼓。和太鼓アンサンブル（大太鼓・長胴・締太鼓）、低弦リフ、ホルン → トランペット → トロンボーンの3旋律、男声合唱の掛け声、金床、銅鑼 |
| `music.area_6` | `music/area_6.ogg` | ⑥ **セレスティア天空大陸** | ホ長調（リディア色: F♯/E） | 76 BPM 4/4 | 75.8 s ループ | 浮遊感。ソプラノ合唱の旋律（フルートが1オクターブ上）、ハープ、高弦、ベルと風鈴。太鼓なし |
| `music.area_7` | `music/area_7.ogg` | ⑦ **終焉の塔** | ハ短調（C保続音、三全音 G♭/C） | 60 BPM 4/4 | 80.0 s ループ | 暗く不吉。オルガンの保続音、弔鐘、遅い心音、高音弦のクラスター、擦弦金属、チェロの哀歌。男声合唱が **王国の主題を短調の葬送歌に変えて** 歌う |
| `music.dungeon` | `music/dungeon.ogg` | ダンジョン | イ短調 | 92 BPM 4/4 | 73.0 s ループ | 緊張。♭2 を含む低弦スピッカートのオスティナート、時計の刻み、水滴、導音にこだわるクラリネット、弱音ホルン、トレモロ、無音に近いブレイク |
| `music.boss` | `music/boss.ogg` | ボス戦 | ホ短調 | 168 BPM 4/4 | 68.6 s ループ | 速く激しい。ドラムセット＋和太鼓、16分音符の弦オスティナート、シンセベース、ホルン主題 → ヴァイオリン → 合唱とトロンボーンのハーフタイム → トランペットの頂点 |
| `music.final_boss` | `music/final_boss.ogg` | **終焉の王** | ニ短調 | 150 BPM 4/4 | 89.6 s ループ | 壮大。パイプオルガンのトッカータ、混声合唱が主題を短調で（D–E–A）、王の半音階動機、オルガン独奏、長調の主題が一瞬だけ戻る「希望」→ A7 で再び闇へ、鐘と銅鑼 |
| `music.ending` | `music/ending.ogg` | エンディング（スタッフロール） | ヘ長調 → ト長調 | 72 BPM 4/4 | 146.5 s（1回） | 感動的。ピアノ独奏 → ピアノで主題 → ヴァイオリンとチェロの対旋律 → 木管 → ♭VI–♭VII–V で転調 → ト長調の全合奏・合唱 → ピアノのコーダ（G add9） |

ジングル（1回再生、MUSIC カテゴリ）:

| サウンドID | ファイル | 長さ | 内容 |
|---|---|---|---|
| `jingle.victory` | `jingle/victory.ogg` | 8.6 s | 勝利のファンファーレ。トランペットの三連の呼び声 → B♭–C–F、ティンパニのロールとシンバル |
| `jingle.level_up` | `jingle/level_up.ogg` | 3.0 s | ハープとチェレスタの上行 → 金管の F 長和音ときらめき |
| `jingle.quest_complete` | `jingle/quest_complete.ogg` | 5.2 s | ホルンで主題の冒頭（F–G–C）、弦のアーメン終止、ハープとベル |
| `jingle.discovery` | `jingle/discovery.ogg` | 6.5 s | 弦と合唱のうねり（Dm(add9) → B♭/D）が F 長和音に開け、ホルンの呼び声とハープのグリッサンド |

**主題（ライトモチーフ）**: タイトル曲冒頭の「F–G–C（付点のリズム、1–2–5 度）」。タイトル・エンディング・クエスト達成で長調のまま、
終焉の塔では短調の葬送歌、終焉の王では短調の合唱（D–E–A）として現れ、エンディングでト長調に転調して締めくくります。

## 2. 効果音一覧

全て 44.1 kHz **モノラル**（Minecraft は モノラル音だけを位置付けして減衰させる）。字幕キーは `subtitles.eternalrealm.<ID>`
（日英テキストは `docs/i18n/audio.json`。音楽は字幕なし、`ui.click` もバニラのボタン音と同じく字幕なし）。
複数ファイルの音は再生ごとにランダムに選ばれます。推奨 SoundSource は `ERSounds` の Javadoc と同じです。

| サウンドID | ファイル数 | 内容 | 推奨 SoundSource | 届く距離 |
|---|---|---|---|---|
| `skill.slash` | 2 | 鋭い風切り＋刃の響き | PLAYERS | 16 |
| `skill.heavy_slash` | 1 | 低い風切り＋重い衝撃 | PLAYERS | 16 |
| `skill.fire_cast` | 1 | 炎が巻き上がる音とはぜる火の粉 | PLAYERS | 16 |
| `skill.fire_explosion` | 1 | 低い爆発音＋炎の余韻と破片 | PLAYERS | 32 |
| `skill.ice_cast` | 1 | 結晶のきらめき（FM）と凍りつく空気 | PLAYERS | 16 |
| `skill.ice_shatter` | 1 | ガラス質の破砕音 | PLAYERS | 16 |
| `skill.thunder` | 1 | 鋭い雷撃音と遠雷のうなり | PLAYERS | 48 |
| `skill.holy_chime` | 1 | 明るい鐘の和音 | PLAYERS | 16 |
| `skill.heal` | 1 | 上行するハープとやわらかな和音 | PLAYERS | 16 |
| `skill.buff` | 1 | 上昇スイープと金管の和音 | PLAYERS | 16 |
| `skill.shadow_whoosh` | 1 | 逆再生のうねり＋暗い風切りとうめき | PLAYERS | 16 |
| `skill.arrow_release` | 1 | 強い弦音と矢の飛翔 | PLAYERS | 16 |
| `skill.arrow_volley` | 1 | 7本の弦音と矢の群れ | PLAYERS | 16 |
| `skill.magic_bolt` | 1 | 下降する FM のザップと光の粒 | PLAYERS | 16 |
| `weapon.swing.one_handed_sword` | 2 | 軽く速い風切り | PLAYERS | 16 |
| `weapon.swing.two_handed_sword` | 2 | 遅く重い風切り | PLAYERS | 16 |
| `weapon.swing.dual_blades` | 2 | 二連の短い風切り | PLAYERS | 16 |
| `weapon.swing.spear` | 2 | 鋭い突きと柄の鳴り | PLAYERS | 16 |
| `weapon.swing.staff` | 2 | 木の風切り＋魔力のうなり | PLAYERS | 16 |
| `weapon.swing.bow` | 2 | 軽い弦音と矢の風切り | PLAYERS | 16 |
| `combat.guard` | 1 | 鈍い金属と木の衝撃（ガード成功） | PLAYERS | 16 |
| `combat.parry` | 1 | 高く響く金属音と火花 | PLAYERS | 16 |
| `combat.dodge` | 1 | 素早い布と空気の音 | PLAYERS | 16 |
| `boss.telegraph` | 1 | 逆再生のうねり＋三全音の警告パルス（予兆開始時） | HOSTILE | 48 |
| `boss.roar` | 1 | フォルマント合成の咆哮（3層） | HOSTILE | 64 |
| `boss.phase_change` | 1 | 重い衝撃＋三全音の鐘と低い合唱のうねり | HOSTILE | 64 |
| `dungeon.door_open` | 1 | 石扉がこすれる音と着地音 | BLOCKS | 32 |
| `dungeon.wave_start` | 1 | 低い角笛（A → E の5度上行） | HOSTILE | 64 |
| `loot.rare_drop` | 1 | きらめく鐘のアルペジオ | PLAYERS | 16 |
| `loot.legendary_drop` | 1 | 金管・鐘・合唱の小さなファンファーレ | PLAYERS | 32 |
| `loot.gold_pickup` | 3 | 硬貨の触れ合う音（2〜4枚） | PLAYERS | 16 |
| `enhance.success` | 1 | 金床の一撃＋上昇するきらめき | PLAYERS | 16 |
| `enhance.fail` | 1 | 金床の一撃＋ひび割れと半音ずつの下降 | PLAYERS | 16 |
| `ui.click` | 1 | 短く柔らかいクリック（字幕なし） | MASTER | — |
| `ui.journal_open` | 1 | 紙のこすれと本の音（ジャーナルを開く・ページをめくる） | MASTER | — |
| `ui.npc_talk` | 3 | 短い会話音（3種の高さ） | MASTER | — |

## 3. 使い方（API）

```java
// 共通（EternalRealm のコンストラクタ）: 登録だけ。サーバーでも安全
ERSounds.register(modBus);

// 効果音（サーバー側。近くのプレイヤー全員に位置付きで聞こえる）
level.playSound(null, x, y, z, ERSounds.SKILL_FIRE_EXPLOSION.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
level.playSound(null, x, y, z, ERSounds.weaponSwing(weaponType).get(), SoundSource.PLAYERS, 1.0F, 1.0F);
ERSounds.byId("boss.roar");           // Optional<RegistryObject<SoundEvent>>（ID から引く）

// クライアント専用コードから（ネットワークハンドラ。共通コードから直接参照しないこと）
MusicDirector.setContext("area_3");   // "none" / "hub" / "area_1".."area_7" / "dungeon" / "boss" / "final_boss"
MusicDirector.playJingle("victory");  // "victory" / "level_up" / "quest_complete" / "discovery" / "ending"
MusicDirector.stopJingle();           // スタッフロールのスキップなど
MusicDirector.setTitleMusicEnabled(false);  // タイトル画面で王都の曲を流さない
UiSounds.click(); UiSounds.journalOpen(); UiSounds.npcTalk();   // UI 音（MASTER）
```

既存のハンドラ方式（`ClientModEvents` で `ClientboundHandlers.setXxxHandler(...)` を設定する）にそのまま載せられます。
`setContext` / `playJingle` / `stopJingle` はどのスレッドから呼んでもよく、処理は次のクライアントtickで行われます。

### MusicDirector の動作

| 状況 | 動作 |
|---|---|
| コンテキスト変更（例: 王都 → エリア1） | 2.5 秒の等パワー・クロスフェード。同じ曲のままなら何もしない（途中から続く） |
| `boss` / `final_boss` へ | 即座に切替（前の曲 0.3 秒でフェードアウト、ボス曲 0.15 秒でイン） |
| `none` | 3 秒でフェードアウトし、その後はバニラの BGM に戻る（バニラ自身の間隔で再開） |
| 直前まで鳴っていた曲へ戻る | フェードアウト中の同じ曲をそのまま戻す（頭から始め直さない） |
| `level_up` / `quest_complete` / `discovery` | 音楽を 25% にダッキングし、終わると約1秒で戻す |
| `victory` / `ending` | 音楽を 0.5 秒で止めて置き換え、終了後にその時点のコンテキストの曲を 1.5 秒でフェードイン。勝利後は先に `setContext("dungeon")` などに戻しておく |
| ジングルの重なり | 優先度 ending > victory > discovery = quest_complete > level_up。上位は下位を中断し、同位以下は終わるまで待つ（10 秒で破棄） |
| タイトル画面（ワールドなし） | 王都の曲（`music.title`）。ログイン後に `hub` が来れば同じ曲が途切れずに続く |
| ログアウト | コンテキストを `none` に戻し、全て停止 |
| バニラ BGM | ETERNAL REALM の曲が必要な間は `PlaySoundEvent` で MUSIC カテゴリの他の音を止め、開始時に `MusicManager.stopPlaying()`。二重には鳴らない |
| 音量 | すべて SoundSource.MUSIC（MUSIC スライダーが効く）。スライダー0・リソース再読み込み・デバイス変更で止まった曲は自動で再開 |
| ポーズ | シングルプレイのポーズ中はエンジンと同じく停止し、フェードも止まる |

Forge 47（1.20.1）には `SelectMusicEvent` が無いため（Forge 1.20.1 ブランチで確認）、再生は `SoundManager` と
フェード可能な `AbstractTickableSoundInstance`（`MusicVoice`）で行います。ループ曲は delay 0 の looping インスタンスなので、
エンジンの LoopingAudioStream が途切れなく繰り返します（バニラ方式の「曲の後の数秒の無音」もありません）。

## 4. 再生成

```bash
python3 tools/audio/generate_all.py                  # 全て（2並列で約10分）
python3 tools/audio/generate_all.py --only music.boss sfx/skill/slash_1   # 一部（ID・ファイル名）
python3 tools/audio/generate_all.py --metadata       # sounds.json と docs/i18n/audio.json だけ
python3 tools/audio/generate_all.py --analyze        # build/audio-analysis/ にスペクトログラムPNGと report.json
```

必要なもの: Python 3.10 以上、numpy、scipy、matplotlib（`--analyze` のみ）、libvorbis 付きの ffmpeg。ネットワーク不要。

| ファイル | 役割 |
|---|---|
| `tools/audio/generate_all.py` | 入口。メタデータ生成 → ループ自己テスト → 描画・マスタリング・エンコード・検証 → 容量 |
| `eraudio/catalog.py` | 全サウンドのID・ファイル・字幕（`sounds.json` と `docs/i18n/audio.json` はここから生成。`ERSounds.java` と不一致ならエラー） |
| `eraudio/theory.py` | 音名・コード記号・声部連結（ボイスリーディング）・小節検査付きの旋律記法・和声チェック |
| `eraudio/instruments.py` / `drums.py` | 楽器（下記 §5） |
| `eraudio/song.py` / `arrange.py` | 小節・コード進行・バス（ミキサー）・センド・ループ構成、伴奏パターン |
| `eraudio/tracks/*.py` | 各曲の作曲（旋律・和声・編曲・ミックス） |
| `eraudio/sfx.py` | 効果音 |
| `eraudio/dsp.py` | フィルタ、合成残響、コーラス、コンプ、トゥルーピーク・リミッタ、BS.1770 ラウドネス |
| `eraudio/build.py` / `encode.py` / `analysis.py` / `selftest.py` | 描画・エンコード・検証 |

旋律は記法（例 `"F4:e. G4:s C5:h. | D5:q C5:e Bb4:e A4:q G4:q"`）で書き、各小節の拍数が合わないとエラーになります。
強拍の長い音がコードと短9度でぶつかると警告が出ます（作曲ミスの検出）。

## 5. 合成とミックスの方法

- **弦・金管・木管・合唱・オルガン**: 音ごとに絶対周波数のスペクトル包絡（胴鳴り、フォルマント、ストップ構成）から
  帯域制限ウェーブテーブルを作り、デチューンしたアンサンブル（3〜6声）、遅れて入るビブラート、ゆらぎ、発音時の明るさ（金管の
  「ブラッ」）、レガート（重なりとポルタメント）、フレーズの抑揚で鳴らす。合唱は母音フォルマント（a/o/u/e）を持つ声帯音源。
- **ハープ・リュート・ウード・箏・ピッツィカート・ピアノ・鐘・グロッケン・オルゴール**: モーダル合成（減衰する部分音の和。
  撥弦位置、非調和性、周波数ごとの減衰、ピアノは2〜3本の弦のうなりと二段減衰・ダンパー）。氷の音は2オペレータ FM。
- **打楽器**: 膜の固有振動（ティンパニ・和太鼓・ダラブッカ・フレームドラム、打撃時の音程降下）、金属は非調和モード
  （金床・ジル・トライアングル・銅鑼）、シンバル・シェーカーは整形ノイズ。
- **空間**: 帯域ごとに残響時間の違う合成インパルス応答（初期反射＋拡散残響）の畳み込み、コーラス、ディレイ、定パワーのパン。
- **ループ**: ループ曲は1周期の長さのバッファに書き、ループ終端を越えた音（余韻・残響・ディレイ・コーラス）は先頭に回り込ませる。
  フィルタやコンプは自分の終端から助走させ、変調 LFO は1周期に整数回。結果としてファイルは「永遠に繰り返す曲の1周期」そのもので、
  継ぎ目もフェードもない。`selftest.py` が毎回これを証明する（ループ描画と、4周期を直線的に描画した3周期目の差が 1e-7 未満）。
- **マスタリング**: バスごとにラウドネスで音量を決め（旋律 0 LU、対旋律 −5、パッド −9 など）、マスターEQ、
  ITU-R BS.1770-4 で −14 LUFS に正規化、オーバーサンプリングで測るトゥルーピーク −1 dBTP のリミッタ（ループは周回で処理）。
- **エンコード**: OGG Vorbis（音楽 q2 ≈ 85 kbps ステレオ、ジングル q3、効果音 q4 モノラル）。ffmpeg の bitexact 指定で決定的。

## 6. 検証（試聴できない環境での確認方法）

`generate_all.py` が毎回次を確認し、問題があれば終了コード≠0で止まります。

- ループ自己テスト（上記）。
- 全ファイル: トゥルーピーク ≤ −1 dBTP、デコード後もクリップなし。
- ループ曲: デコード後のサンプル数が元と一致（Vorbis の終端で隙間が出ない）、ループ点のサンプル段差が曲中の段差分布の範囲内、
  コーデック誤差がファイルの先頭・末尾で他の場所より大きくない。
- 容量: 音楽＋ジングル ≤ 12 MB、効果音 ≤ 2 MB。
- `--analyze` でスペクトログラムと音量推移の PNG、`report.json`（LUFS、ピーク、帯域バランス、ステレオ幅、継ぎ目の値）。

JUnit の `SoundAssetsTest`（`./gradlew build` と `tools/offline-test.sh` で実行）は、`ERSounds` の全IDに `sounds.json` の項目があること、
全ファイルの存在と未使用ファイルが無いこと、音楽がストリーミングのステレオで効果音がモノラル（Vorbis ヘッダで確認）であること、
字幕の日英テキスト、容量予算を確認します。

## 7. 容量

| 区分 | ファイル数 | 合計 | 予算 |
|---|---|---|---|
| 音楽＋ジングル | 16 | 11.08 MB | 12 MB |
| 効果音 | 47 | 0.56 MB | 2 MB |
