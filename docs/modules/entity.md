# 敵・ボスのエンティティ（`entity` / `client.entity` パッケージ）

雑魚4リグ・ボスのリグ（ダンジョンボスはそれぞれ専用、フィールドのボスは共有の5リグ）のエンティティ、モデル、キーフレームアニメーション、テクスチャ。戦闘の「いつ・何を」は
`boss`（BossBrain）とモンスターテンプレート側が決め、このモジュールは見た目・アニメーションと最小限のAIを提供する。

## 1. 構成

| パッケージ | 内容 |
|---|---|
| `entity`（共通） | `EREntityTypes`（DeferredRegister、`register(modBus)`）、`EREntityAttributes`（既定属性）、`ERMonster` と4リグ、`EnemyBolt`、`ERBoss`、`BossMoveControl`（巨体の旋回）、`BossAnimation` / `EnemyAnim`、`EnemyVariant`（振る舞い表）、`BossKind`、生成コード `RigData` |
| `client.entity`（クライアント専用） | `EREntityRenderers`（レイヤー・レンダラー登録）、`EnemyRenderer` / `BossRenderer`、`RigGlowLayer`（発光）、`BossShieldLayer`（シールド）、`BossAuraLayer`（形態のオーラ）、`BossTrailLayer`（武器の軌跡）、`model.*`、生成コード `model.gen.*` |
| `client.boss`（クライアント専用） | `BossBodyEffects`（足音・足元の土煙・巨体の振動、叩きつけのインパクトの揺れ）、`BossScreenEffects` |
| `tools/art_entities/generate.py` | **単一の情報源**: 形状・UV・アニメーション・テクスチャを定義し、Javaの形状/アニメーションクラス・`RigData`・`boss.BossRigData`・PNG・プレビューを生成（決定的。Pillow/numpy） |
| `tools/art_entities/eranim.py`, `boss_motion.py` | ボスのアニメーション制作の道具: キーごとのイージング、脚のIK・歩行、二次運動（ばね）、狙い・接地・床との衝突、溜めの時間合わせ（下記） |

生成物は手で編集しない（スクリプトを直して `python3 tools/art_entities/generate.py` を再実行）。UV表は `tools/art_entities/uv_map.txt`、
全バリアントのプレビューは `preview.png`、雑魚のアニメーションの連続コマは `preview_<rig>.png`（コミットしない）。ボスはコミットするプレビュー
`tools/art_entities/previews/anim_<rig>.png`（全外見×全アニメーションのコマ。固定カメラ、床の線、インパクトのコマは橙の枠と HIT）と
`previews/boss_sizes.png`（全ボス・全形態を1.8ブロックのプレイヤーと同じ縮尺で並べた図、2ブロックごとの目盛り）。

町のNPC（窓口・物語キャラ・町の人・衛兵）の体 `eternalrealm:npc` は同じパイプラインの別リグ（`generate_npc.py`）で、[npc.md](npc.md) に分けて記す。
伝説の旅人4人の体 `eternalrealm:traveller` は2倍の解像度の英雄リグ（`generate_hero.py`）で、[travellers.md](travellers.md) に記す。

## 2. エンティティとバリアント

| エンティティ | バリアント（`setVariant` のID） |
|---|---|
| `eternalrealm:humanoid_enemy` | bandit, ghoul, mummy, cultist★, prisoner, jailer, ice_knight, frost_mage★, fire_imp★, dark_knight, void_mage★, shade, crossbowman★, dryad★ |
| `eternalrealm:beast_enemy` | wolf, snow_wolf, cave_rat, frost_hound, abyss_hound, salamander, scorpion |
| `eternalrealm:golem_enemy` | treant, sand_golem, ice_golem, lava_golem, ruin_construct |
| `eternalrealm:flyer_enemy` | harpy, ice_wraith★, storm_spirit★, sky_sentinel, fire_spirit★ |
| `eternalrealm:enemy_bolt` | 遠隔攻撃の弾（★のバリアントが発射。属性: 炎/氷/虚無/呪い/雷/自然/矢） |
| `eternalrealm:boss_b1`..`boss_b8`, `boss_final` | 専用リグ: BANDIT: b1 / TREEGUARD: b2 / SANDTITAN: b3 / EXECUTIONER: b4 / ICEQUEEN: b5 / FIREDRAGON: b6 / SKYGOD: b7 / GATEKEEPER: b8 / ENDKING: final（共有の5リグ KING・TREANT・COLOSSUS・QUEEN・DRAGON は災厄・はぐれだけを描く。§3.2）。形態ごとの外見 `<id>_p1`〜`p3`（b1_p1, b2_p1〜p2, b3_p1〜p2, b4_p1〜p2, b5_p1〜p3, b6_p1〜p3, b7_p1〜p3, b8_p1〜p3, final_p1〜p3） |

さらに魔物図鑑の130種（フィールドの魔物100＋ヌシ30、ID `f<フィールド>_<名前>` / `lord_<名前>`）が同じ4つのエンティティのバリアントとして加わる。
定義は `tools/art_entities/bestiary.py` の1か所（見た目＝パーツ・配色・姿勢・模様、行動＝弾の属性・粒子・音・火炎耐性・半透明・特技3つ）で、
`bestiary_gen.py` が Java とテキストを、`generate.py` がテクスチャ・モデル表を生成する。一覧は [bestiary.md](bestiary.md)。

★ = 遠隔（距離6〜14を保ち、見えていればキャストして `EnemyBolt` を撃つ。ダメージ = ATTACK_DAMAGE）。それ以外は近接（2種の攻撃をランダム）。
バリアントごとに縮尺（当たり判定も追従）、パーツ表示、テクスチャ、発光、音、粒子、火炎耐性（fire_imp, salamander, lava_golem, fire_spirit）、
半透明（shade, ice_wraith）が決まる。crossbowman・dryad・snow_wolf は d1/d2/d5 のテンプレート用に追加。

## 2.1 ボスの大きさ

ボスは高さ約6〜10ブロックの巨体。モデルの縮尺と当たり判定（幅×高さ、ブロック）は外見ごと（`RigData.scale/hitbox`）で、
後の形態ほどモデル・当たり判定とも1形態につき4%大きい（`BossRigData.FORM_GROWTH`、`ERBoss.formGrowth()`、`getDimensions` が追従）。
ボスのテクスチャは UV の2倍の解像度（`uv_map.txt` の見出し `xN`、`EntityAssetsTest`）。

| ボス | 縮尺 | 第1形態 | 第2形態 | 第3形態 | 第4形態 | 見た目 |
|---|---|---|---|---|---|---|
| b1 盗賊王（BANDIT） | 2.2 | 2.7×5.4 | 2.81×5.62 | — | — | 約5.5、王冠まで約6 |
| b2 古代樹の守護者（TREEGUARD） | 2.15 | 4.1×7.4 | 4.26×7.70 | 4.43×7.99 | — | 頭の苔まで約7.4、角の花まで約9 |
| b3 砂塵の巨神（SANDTITAN） | 2.6 | 5.6×10.0 | 5.82×10.40 | 6.05×10.80 | — | 約10、掲げた大槌まで約11 |
| b4 凍獄の処刑人（EXECUTIONER） | 2.6 | 3.0×7.6 | 3.12×7.90 | 3.24×8.21 | — | 兜まで約7.5、角まで約8 |
| b5 氷獄の女王（ICEQUEEN） | 2.3 | 2.6×6.8 | 2.70×7.07 | 2.81×7.34 | 2.91×7.62 | 浮遊して頭まで約7、氷の冠まで約8 |
| b6 灼炎の竜王（FIREDRAGON） | 2.6 | 6.4×6.1 | 6.66×6.34 | 6.91×6.59 | 7.17×6.83 | 鼻先から炎の尾まで約19、翼幅約13 |
| b7 天空の機械神（SKYGOD） | 2.9 | 5.4×9.8 | 5.62×10.19 | 5.83×10.58 | 6.05×10.98 | 浮遊して兜の飾りまで約11、背の光輪まで約14.5 |
| b8 終焉の門番（GATEKEEPER） | 2.6 | 3.8×8.0 | 3.95×8.32 | 4.10×8.64 | 4.26×8.96 | 兜まで約9、金の角飾りまで約10.5 |
| final 終焉の王（ENDKING） | 2.7 | 4.2×8.9 | 4.37×9.26 | 4.54×9.61 | 4.70×9.97 | 兜まで約9.5、王冠の尖塔まで約11.5（第2形態から浮かぶ王冠は約12.5） |

大きさに合わせて: 叩きつけの届く距離（`meleeReach` = 叩きつけのインパクトで武器・拳・爪・顎が床に届く距離 `RigData.reach`×縮尺、体の縁+1以上）、
段差の乗り越え（`maxUpStep` = 0.6+0.12×高さ、1〜2ブロック）、旋回の速さ（KING/QUEEN/BANDIT/EXECUTIONER/ICEQUEEN/GATEKEEPER/ENDKING 14°/tick、DRAGON/FIREDRAGON 10、TREANT/COLOSSUS/TREEGUARD/SANDTITAN/SKYGOD 8。`faceTowards` は自分の速さで振り向き、
`BossMoveControl` は歩きながらの急旋回を抑えて減速）、歩く速さ（属性: KING/BANDIT/GATEKEEPER/ENDKING 0.294 ≈3.8ブロック/秒、DRAGON/FIREDRAGON 0.286、QUEEN/EXECUTIONER/ICEQUEEN 0.282、TREANT/TREEGUARD 0.243、COLOSSUS/SANDTITAN/SKYGOD 0.233 ≈2.4ブロック/秒。専用リグは前のリグの値のまま。
大きいほど歩幅が長く足取りはゆっくり）、粒子の量と広がり、命中の演出、撃破時の倒れた体に沿う土煙、名前の高さ（当たり判定×1.15+0.5）、影、描画の範囲（翼・尾・剣）、
溶けて昇る高さ（体の高さの1/4）。既定AIはバニラの経路探索（幅の広い体に弱い）を使わず、目標へ向かいつつ自分の当たり判定で前方を探り、
柱や壁があれば近い方の空いた向きへ回り込み、抜けるまでその側を保つ（進めなければ反対側へ）。

## 3. アニメーション

| 種類 | スロット |
|---|---|
| 雑魚 `EnemyAnim` | idle（呼吸・揺れ）, walk, attack1, attack2, cast, hurt, spawn（地面から出現）, death（約1.5秒、その後除去） |
| ボス `BossAnimation` | idle, walk, melee_slam, melee_sweep, cast, roar（予兆の溜めループ）, charge, summon, phase_change, stagger, death（3〜4秒） |

- 単発アニメーションはエンティティイベント（雑魚 64〜、ボス 80〜）でクライアントの `AnimationState` を開始。継続状態（バリアント、予兆、シールド、激昂、フェーズ）は SynchedEntityData。
- 攻撃の当たり・弾の発射はアニメーションの「インパクト時刻」（`RigData.impact`）に合わせてサーバーで発生。再生中は移動しない。
- 死亡: バニラの横倒し・赤色を使わず専用の死亡アニメーション → 煙（ボスは爆発・魂の粒子） → 除去。
- ボス戦での撃破（`startDefeat(長さ, 溶け始め)`）: 体は残ったまま無敵・AI停止、死亡アニメーションを最後の姿勢で保持し、クライアントは光の粒子と白い輝き（オーラ層）を強め、
  溶け始め以降は縮みながら浮かぶ。体の除去は戦闘側（`BossFight`）が演出の最後に行う。
- 形態（`setPhase`）: `lookKey()` が `<id>_p<n>`（あるもののうち n 以下で最大、最大3）、モデルは形態ごとに4%大きく（当たり判定は不変）、
  オーラ層（`BossAuraLayer`: 膨らませたモデルを `boss_aura.png` の流れる模様で加算描画）が形態の色で強まり、発光色も形態色（`BossVisuals.formColor`）へ。
  形態が変わった直後は白い閃光（`transformFlash`）。形態1以上ではテーマ粒子が足元から立ち上る。
- QUEEN は2セット（女王: 浮遊・滑走・詠唱 / 処刑人: 斧の振り下ろし・薙ぎ払い）。サソリは専用の攻撃（鋏・毒針）。

### 3.1 ボスのアニメーション（`eranim.py` で制作）

全ボス・全外見で11スロットを作り直した（終焉の王の死亡は5.6秒の長いもの。KING リグの `death_final` は終焉の森の災厄が受け継ぐ）。共通の作り:

- **イージング**: キーごとの曲線（3次/4次の ease-in/out、行き過ぎて戻る back-out、ホールド）。Java 側 `AnimKit` が同じ曲線を実装（`rot("ssIOb", ...)`）。
- **溜め→速い打撃→フォロースルー→戻り**: 攻撃は読める溜め（身を反らす・腕を振りかぶる・胸が膨らむ）、インパクトへ加速する4次の ease-in、行き過ぎと戻り。
- **脚のIK**: 二関節の脚を接地点から解き、立ち足は地面に固定。歩行（`Gait`）は接地の割合・持ち上げの弧・かかと/つま先の角度を持ち、
  `RigData.walkSpeed`（モデルで縮尺で割る）が歩く速さと一致するので足が滑らない。竜は4脚の側対歩。足先・膝・すねは床に沈まない（自動で持ち上げ）。
- **二次運動**: マント・腰布・髪・ガウン・裾・鎖・蔓・苔・尾・翼端・アンテナをばね（周波数・減衰・慣性・重力）で親の動きから遅れて揺らし、ループは継ぎ目なし。床にめり込まない（床との衝突）。
- **接地と狙い**: 叩きつけの刃・拳・爪はインパクトのコマでちょうど床に（その後も同じ所に）、薙ぎ払いの刃は水平、突きは前へ、詠唱の宝珠は標的へ。倒れる・膝をつく体は床に乗る。
- **溜めの時間合わせ**: 動作のインパクトを技の予兆に収める（`retime`: 溜めだけを縮める）。インパクト時刻はtick単位（0.05秒の倍数、生成時に検査）。

| リグ | idle（ループ） | walk | slam | sweep | cast | roar（ループ） | charge | summon | phase_change | stagger | death |
|---|---|---|---|---|---|---|---|---|---|---|---|
| KING・BANDIT（b1）・GATEKEEPER（b8）・ENDKING（final） | 4.0 | 1.5 | 2.25 / **1.1** | 1.85 / **0.8** | 1.85 / **0.8** | 1.2 | 1.8 / **0.85** | 2.45 / **1.25** | 3.0 / **1.8** | 1.4 | 3.6 / **1.8**（final 5.6: 1.6 で膝、4.5 で倒れる） |
| TREANT・TREEGUARD（b2） | 4.6 | 2.4 | 2.5 / **1.2** | 2.15 / **0.9** | 2.05 / **0.8** | 1.4 | 2.0 / **1.0** | 2.6 / **1.3** | 3.2 / **1.8** | 1.5 | 4.0 / **1.8** |
| COLOSSUS・SANDTITAN（b3）・SKYGOD（b7） | 4.5 | 2.2 | 2.4 / **1.1** | 2.25 / **1.0** | 2.05 / **0.8** | 1.4 | 2.0 / **1.0** | 2.6 / **1.3** | 3.2 / **1.8** | 1.5 | 4.0 / **1.8** |
| ICEQUEEN（b5） | 3.2 | 1.8 | 2.1 / **1.0** | 1.9 / **0.9** | 1.85 / **0.8** | 1.2 | 1.6 / **0.8** | 2.2 / **1.1** | 3.0 / **1.8** | 1.3 | 3.6 / **1.8** |
| EXECUTIONER（b4） | 3.6 | 1.6 | 2.2 / **1.1** | 2.0 / **1.0** | 2.0 / **0.95** | 1.2 | 1.8 / **0.85** | 2.2 / **1.1** | 3.0 / **1.8** | 1.3 | 3.6 / **1.8** |
| DRAGON・FIREDRAGON（b6） | 3.4 | 1.8 | 2.4 / **1.1** | 2.3 / **1.05** | 2.05 / **0.8** | 1.4 | 2.0 / **1.0** | 2.6 / **1.3** | 3.2 / **1.8** | 1.4 | 4.0 / **1.8** |

（秒。「長さ / **インパクト**」。death のインパクトは体が床に着く瞬間 = `DefeatSequence` の衝撃波の拍）

- **idle**: 呼吸（胸・肩の膨らみ）、重心の移動、頭の見回し、マント・髪・尾・蔓・翼端の揺れ、竜は鼻息で顎が開く。
- **walk**: 足の接地と蹴り出し、体の上下動と左右の揺れ、腕は脚と逆に振れる、二次運動が遅れて追う。女王は浮遊して滑る（足音なし）。
- **slam**: KING 大剣を頭上に振りかぶり床へ／TREANT 両腕の枝を樹冠の上から床へ／COLOSSUS 両拳を頭上から床へ（深く沈み込む）／処刑人 斧の振り下ろし／女王 王笏で床を突く／DRAGON 後脚で立ち上がり前脚を浮かせ、翼を広げ、前脚と顎で床に叩きつける。
- **sweep**: 体をひねって溜め、水平の薙ぎ払い（竜は尾の一撃）。**cast**: 胸に力を集め、手・宝珠・核・口から放つ（弾は `RigData.muzzle` から）。
  **charge**: 身を沈めて突進・突き。**summon**: 武器を床に突き立てる／腕を掲げる／翼のはばたき。**roar**: 予兆の溜めのループ（全身の震え）。
  **phase_change**: 全身で咆哮（1.8秒で頂点）。**stagger**: のけぞり→よろめき→戻る。**death**: よろめく→膝→倒れる（KING 前のめり、TREANT 横倒し、COLOSSUS 像のように後ろへ、DRAGON 横倒し、女王 ガウンが床に崩れる）。武器は床に落ちて床の上に横たわる。
- **重ね方**（`BossModel`）: 歩行（縮尺で速さを合わせる）＋idle（動作中は弱める）＋roar（重み付きでフェードイン、次の動作の下で5tickかけてフェードアウト）＋動作。
- **体の効果**（クライアント）: 足が着くコマで足元のブロックの土煙と重い足音、幅2.8超の巨体は近くのプレイヤーのカメラを軽く揺らす（`BossBodyEffects`）。
  叩きつけ・突進・変身・召喚のインパクトのコマで揺れ（大きいほど強く遠くまで）。叩きつけ・薙ぎ払い・突進・召喚のインパクトの前後0.4秒、武器（大剣・斧・王笏・拳・爪・尾と前脚の爪）が
  形態の色の光の軌跡を残す（`BossTrailLayer`、`RigData.trailBones/trailPoints/trailChain`）。発光する目・紋様は従来どおり発光層。

### 3.2 ダンジョンボスの専用リグ（2026-11-15、オーナーの設定画から。DECISIONS D-BOSSRIGS）

オーナーの設定画（三面図・細部・武器・アクション）どおりに、ダンジョンボスごとに専用のリグを作り直した（`tools/art_entities/rig_<名前>.py`、
共通の塗りの道具 `boss_kit.py`）。戦いは変えない: 各動作の長さとインパクトのtick（`BossRigData` は同一のまま生成）、当たり判定、
歩く速さ・旋回は前のリグの値。新しい体が前の当たり判定を満たすよう描画の縮尺だけを決めた。
ただし叩きつけの届く距離（`meleeReach`）は従来どおり「叩きつけのインパクトで武器が床に着く位置」（`RigData.reach`×縮尺）なので、
新しい武器の長さに合わせて変わった（ブロック、前→今）: b1 5.5→4.2、b2 3.8→5.2、b3 7.0→6.4、b4 5.8→8.4、b5 2.3（同じ）、
b6 4.2（同じ）、b7 6.8→4.5、b8 8.4→11.2、final 9.3→12.3。共有の5リグはフィールドのボス（災厄・はぐれ）を
描き続け、前のダンジョンボスの外見はそれらの土台（`rig.bases`、テクスチャなし）として残る。

| リグ | ボス | 体と細部 | 二次運動（ばね） | 動作 |
|---|---|---|---|---|
| BANDIT | b1 地下都市の盗賊王 | 人の体つき。銀髪（前髪・横髪・はね毛）、傾いた金の王冠と紫水晶、赤い瞳と傷、白い毛皮の肩掛けと襟、金の鎖とルビーのペンダント、膝丈の黒革のコート（ベルト・金の留め金・ポーチ）、手首・膝・ブーツの金の帯、紅から紫の裾の破れたマント、赤い刻みの銀の短剣2本（金の鍔と柄頭に紫水晶）。第2形態は刃と宝石が紫に光る | マント3段、コートの裾3枚、後ろ髪、ペンダント、ポーチ | slam 跳び上がって両短剣を床へ突き立てる／sweep 頭上で交差させた刃をX字に引き裂く／cast 腰の投げナイフを扇に投げる／charge 低く溜めて突き／summon 指笛で手下を呼び短剣を天へ／roar 腕を広げて高笑い／phase 地団駄を踏み叫ぶ／death 膝をつき前へ崩れ、王冠が転がる |
| TREEGUARD | b2 古代樹の守護者 | 樹皮の積み木の巨体。光る黄緑の目と重い眉、頭の苔に枝分かれした角と紫・白の花、胸・肩・前腕・すね・腿に光る結び目の紋（胸の紋は詠唱で膨らむ板）、上面すべてに垂れる苔、2関節4本の根の指、根の足。第2形態で背の紫の結晶、第3形態で結晶の棘と紫の紋・秋の苔 | 蔦13本（角・頭・肩・胸・前腕）、角、頭の苔 | slam 両拳を角の上から床へ／sweep 右腕で低く薙ぐ／cast 腕を広げ紋が光る／charge 右手を床へ突き込む（棘の列）／summon 両手を床に沈める／phase 角の花が開く／death 後ろへ倒れる |
| SANDTITAN | b3 砂塵の巨神 | 砂岩の煉瓦の巨人。深い眼窩の琥珀の目、金の額飾りとトルコ石、金の飾り、白い石の髪（前・横・背）、肩・前腕・膝の光る四角い渦の紋、金の胸飾りとトルコ石のベルト、赤い腰布（前後）と肩の赤い布（金の縁、裾が破れる）、つま先の光る筋。右手に巨大な大槌（各面に紋、金の帯、金の柄頭）。第2形態で琥珀の亀裂、第3形態で周りに浮かぶ砂岩の塊 | 石の髪9房、腰布前後、肩の布4本 | slam 大槌を頭上から床へ／sweep 大槌を水平に／cast 大槌を立てて左手から砂／charge 肩から突進／summon 大槌を床に突き立てる／phase 大槌を掲げて咆哮／death 大槌を落とし膝から前へ倒れる |
| EXECUTIONER | b4 凍獄の処刑人 | 霜に覆われた黒鉄の大鎧。格子の面頬と青く光る目の兜、霜とつららの骨の角、雪の積もった四角い肩当てとつらら、胸に斜めの鎖・腰に鎖（垂れた端が揺れる）、裂けた深紅のマントと前垂れ。右手に長柄の大斧（刃は黒鉄に氷の縁、青く光るひび、背の棘、刃から垂れる鎖）。第2形態で角の先に氷の角と光るひび、第3形態で氷柱の冠と光る鎖 | マント3段、前垂れ2段、腰の鎖3節、斧の鎖2節 | slam 両手で振りかぶり床へ斬り下ろす（霜の環）／sweep 処刑の大振り／cast 腰の鎖を左手で鞭のように放つ／charge 斧を突き出して踏み込む（鎖引き）／summon 柄を床に突き左手を掲げる／roar 斧を構えて咆哮／phase 両手で斧を頭上に掲げ氷が弾ける／death 膝をつき前に倒れ、斧が床に転がる |
| ICEQUEEN | b5 氷獄の女王 | 床から少し浮く女王。青く光る大きな瞳、床まで届く銀白の髪（前髪・前の房・背の流れ）、氷晶の冠と大きなダイヤ、毛皮と氷の高い襟、白と紺の胴着（ダイヤのブローチと宝石の列）、裾の広がる袖、白と紺の三段の釣鐘のドレス（光る紺の前垂れに氷と宝石、裾の周りに氷片）、背に氷のマント。右手に雪の結晶の杖（六本の腕とダイヤ）。第2形態で冠が大きく、第3形態で氷の翼、第4形態でより冷たい青 | ドレス3段（床に当たっても回さない）、後ろ髪3段、氷のマント3段、前の房、袖口 | slam 杖を掲げて床を突く／sweep 結晶で薙ぐ（吹雪）／cast 杖を向けて結晶から放つ／charge 杖を槍のように滑り込む／summon 両腕と杖を掲げ氷が昇る／roar 腕を広げて怒る／phase 一回転してドレスが広がり杖を天へ／death ドレスに沈み横へ崩れ、杖が落ちる |
| FIREDRAGON | b6 灼炎の竜王 | 四つ脚の火山の竜。玄武岩のブロックの鱗とその継ぎ目・ひびに光る溶岩、クリーム色の腹と喉の板、後ろへ反って巻き上がる大きな角、頭の棘・頬の棘、燃える目と細い瞳、歯の列と口の中の炎、背と尾の棘（縁が光る）、炎の舌になった尾の先、骨と指の筋の間に光る橙の膜の大きな翼と関節の爪、クリーム色の爪。第2形態で燃え、第3形態で角が大きく、第4形態で鱗が溶岩に | 尾4節、翼の先、尾の炎 | 共有の DRAGON リグと同じ骨格と動作（4脚IKの側対歩、後脚で立ち上がり前脚で叩きつけ、尾の薙ぎ払い、火の息、翼の突風、横倒しの死）に、尾の炎の揺らめきを加えた |
| SKYGOD | b7 天空の機械神 | 床から浮く白い石と金の天使の機械。金の鶏冠とひれの小さな兜に青く光る面、背に浮かぶ金の光輪（青い宝石8つと棘4本）、白と金の大きな胸に金の十字枠の青い核、金の縁の段になった白い大きな肩当て、金の関節の節の腕と黒い機械の爪（3本の指と親指）、左右5枚ずつの刃の羽（根元は金、青い光の溝）、白い板の長い裾（金の縁と青い線）と白い石の脚。第2形態で光が強まり、第3形態で二つ目の光輪と広げた翼、第4形態は過負荷で赤く燃える | 裾の板7枚（光輪の揺れと羽の呼吸はキー） | slam 舞い上がって両爪を床へ突き立てる／sweep 体をひねり核の光線で薙ぐ／cast 翼を広げ核からミサイル／charge 構えて前へ光線（反動）／summon 腕と翼を掲げ光輪が輝く／roar すべてを広げて警報／phase 停止して再起動し光輪が広がる／death 光輪が落ち翼が垂れ、膝から前へ崩れる |
| GATEKEEPER | b8 終焉の門番 | 黒曜石・深層岩・石レンガのブロックに金の縁と紫水晶の光の、古い騎士の構造体（全ブロック面が設定画の色のモザイク）。面頬に紫の光の目の切れ目2本、金の角飾り4本（外の2本は高く外へ反る）、胸に紫水晶の星の核、金の縁の石レンガの段になった巨大な肩当て、金の帯の籠手と拳、紫水晶の金のベルト、前に淡い樹の紋の深紅の旗、背に暗い深紅のマント、金の膝当ての太い脚とブロックの靴。右手に門番の大剣（黒曜石と紫の石の幅広い刃に光るひびの紫水晶の芯、ブロックの端の金の鍔、金の柄頭）。第2形態で胸の紫水晶の筋が増え、第3形態で角飾りが光り周りに紫水晶の欠片が浮かび、第4形態で全身に光るひびと大きな欠片 | マント3段、旗2段 | slam 両手で頭上から振り下ろし床を砕く／sweep 大きく水平に薙ぐ／cast 左の拳に闇を集めて放つ／charge 前の床へ刃を突き立て衝撃波／summon 大剣を突き立て柄頭に両手、影が昇る／roar 吼える／phase 核が燃え門の咆哮／death 突き立てた剣の前で膝をつき、前へ倒れる |
| ENDKING | final 終焉の王 | 金の尖塔9本に紫の宝石の高い王冠（正面に紫の宝石）、紫に燃える目の顔のない兜、金の縁と狭間の段になった巨大な石ブロックの肩当て、金の十字の板に紫の星の核、金の胸当ての縁とベルト（紫の宝石）、腰から床へ円錐に広がる12枚の帯の長衣（深紅・紫・鉄、金の縁、裾が破れる）、翼のように左右へ広がる深紅の大きなマント、金の帯の籠手。右手に終焉の大剣（淡い紫の水晶の刃に光る溝とルーン、金の鍔に紫の宝石）、上げた左手の上に破滅の球（紫のブロックの球と揺れる金の輪）。脚は見せず長衣で滑る。第2形態で王冠が浮かび、第3形態で鎧のひびが紫に光り球が大きく、第4形態で背に黒い刃の光輪 | 長衣の帯12本（各2節）、マント3段、マントの翼左右2節ずつ | slam 大剣を頭上に掲げて床へ斬り下ろす（終焉の斬撃）／sweep 大きく水平に薙ぐ／cast 球を高く掲げて振り下ろす（虚無の柱、弾は球から）／charge 大剣を槍のように構えて滑り突く／summon 大剣を前の床に突き立て球を王冠の上へ／roar 腕を広げて命じる／phase 球を核に押し当て、力が弾けて腕が開く／death 球が消え、膝をつき突き立てた剣にもたれ（1.6秒）、4.5秒で前へ倒れ王冠が転がる |

## 4. API（統合側）

```java
HumanoidEnemy e = EREntityTypes.HUMANOID_ENEMY.get().create(level);
e.setVariant("ghoul");                      // 不明なIDは IllegalArgumentException。ERMonster.variantsOf(EnemyRig.HUMANOID)
e.getAttribute(Attributes.MAX_HEALTH).setBaseValue(80); // 属性は生成側で設定（既定値あり）
e.moveTo(x, y, z, yaw, 0); level.addFreshEntity(e);

ERBoss boss = EREntityTypes.boss("b3").get().create(level);
boss.setAiFrozen(true);                     // スクリプト技の間は静止
boss.setTelegraphing(true);                 // 予兆: roar ループ＋発光の脈動＋テーマ粒子
boss.playAnimation(BossAnimation.MELEE_SLAM); boss.animationTicksLeft();
boss.setShielded(true); boss.setEnraged(true); boss.setPhase(2); boss.bossId();
```

- `ERMonster`: `setVariant/getVariant`, `variantsOf(rig)`, `startMeleeAttack/startRangedAttack(target)`, `playAnimation(EnemyAnim)`, `isActing()`。
- `ERBoss`: `playAnimation(BossAnimation)`, `playingAnimation()`, `durationTicks/impactTicks`, `setTelegraphing/setShielded/setEnraged/setPhase`, `setAiFrozen`,
  `faceTowards(x, z)`（自分の旋回の速さで向く）/ `faceTowardsNow(x, z)`（即座）, `meleeReach()`, `formGrowth()`, `modelScale()`, `bossId()`, `lookKey()`,
  `lookFor(kind, phase)`, `startDefeat(長さ, 溶け始め)` / `isDefeatPlaying()`、クライアント用 `transformFlash/defeatProgress/dissolve/roarWeight/idleWeight(partialTick)`、`actionTicks(動作)`。
- 形態の外見は `tools/art_entities/rig_*.py` の `Variant('<id>_p<n>', ...)`（追加パーツ: 樹人の結晶・棘、巨神の胸の結晶・肩の砲台、女王の氷の翼・氷の角）。装飾の共通処理は `boss_forms.py`（光る脈・亀裂・発光）。
  QUEEN の形態は `SETS` で元のアニメーション組を使う。
- シールドは見た目のみ（無敵は `BossBrain.isInvulnerable` を見て統合側が適用）。最大HPの既定は1024（バニラ上限）。
- 雑魚・ボスとも自然デスポーンしない（ピースフルでも消えない）。インスタンス終了時に統合側で除去する。
- ボス: 落下・窒息・溺死・圧死ダメージ無効、ノックバック耐性1.0、押されない、次元移動しない。
- 配線: `EternalRealm` のコンストラクタに `EREntityTypes.register(modBus);`（このブランチで追加済み）。属性・レンダラーは `@Mod.EventBusSubscriber` で自動。
- 名前: `docs/i18n/entity.json`（エンティティ名 `entity.eternalrealm.<type>`、バリアント名 `entity.eternalrealm.enemy.<variant>`）。
- `EREntityTypes.createEnemy(level, "ghoul")`: バリアントIDからリグを判定して生成（レベルへの追加は呼び出し側）。`rigOf(id)` も可。

ボスの取り巻きテンプレート（`<bossId>_minion`）の推奨バリアント:

| テンプレート | バリアント | テンプレート | バリアント |
|---|---|---|---|
| b1_minion | bandit / crossbowman | b6_minion | salamander |
| b2_minion | dryad | b7_minion | sky_sentinel |
| b3_minion | mummy | b8_minion | shade |
| b4_minion | jailer | final_minion | dark_knight |
| b5_minion | ice_wraith | | |

## 5. テスト

- GameTest `EntityGameTests`: 全バリアントの出現・縮尺・アニメーション・被弾・死亡アニメーション後の除去、近接/遠隔攻撃の命中、NBT保存、全ボスの全アニメーション・状態・耐性・死亡演出後の除去、ボスAIの叩きつけ命中。
  巨体の移動（`boss_arena` 41×15×41、壁と屋根で閉じた石の広間に中央の柱。`tools/gen-gametest-structures.py` で生成）: b2・b3・b6・final が柱を回り込んで対角の目標に届き叩きつける（当たり判定で前方を探って回り込む）、その間、壁や柱にめり込んだままにならない（めり込みを検出したらブロック・近くの落下ブロックと位置をログに出し、10tick続けば失敗）。
  体にブロックが重なったとき（上から落ちてきた砂利など）は、既定AIがまず重なりの反対側へ体をずらして抜け出す（探り針が「すでに重なっている」ブロックで全方向をふさがれて立ち往生しないように）。
  `bossesGrowWithTheirForms`: 最終形態で当たり判定とモデルが12%大きく、めり込まない。
- 単体 `EntityAssetsTest`: テクスチャ・名前・UV表サイズ（テクセル密度 `xN` を含む）・ボスとの対応。
- 生成時の検査（`generate.py`）: ボスのインパクトがtick単位、同じボスの形態どうしで動作の時間が同じ、Javaの1メソッドに収まるキー数。
- 単体 `ClientModelSmokeTest`（CIのみ。リフレクションのためオフラインでは自動スキップ）: 全リグの本体・シールド層をベイクし、
  実際の `EnemyModel` / `BossModel` で全バリアントの外見を適用、全アニメーションを複数時刻で評価（GameTestサーバーはクライアントクラスを読まないため）。
