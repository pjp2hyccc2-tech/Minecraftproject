# 敵・ボスのエンティティ（`entity` / `client.entity` パッケージ）

雑魚4リグ・ボス5リグのエンティティ、モデル、キーフレームアニメーション、テクスチャ。戦闘の「いつ・何を」は
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

## 2. エンティティとバリアント

| エンティティ | バリアント（`setVariant` のID） |
|---|---|
| `eternalrealm:humanoid_enemy` | bandit, ghoul, mummy, cultist★, prisoner, jailer, ice_knight, frost_mage★, fire_imp★, dark_knight, void_mage★, shade, crossbowman★, dryad★ |
| `eternalrealm:beast_enemy` | wolf, snow_wolf, cave_rat, frost_hound, abyss_hound, salamander, scorpion |
| `eternalrealm:golem_enemy` | treant, sand_golem, ice_golem, lava_golem, ruin_construct |
| `eternalrealm:flyer_enemy` | harpy, ice_wraith★, storm_spirit★, sky_sentinel, fire_spirit★ |
| `eternalrealm:enemy_bolt` | 遠隔攻撃の弾（★のバリアントが発射。属性: 炎/氷/虚無/呪い/雷/自然/矢） |
| `eternalrealm:boss_b1`..`boss_b8`, `boss_final` | KING: b1, b8, final / TREANT: b2 / COLOSSUS: b3, b7 / QUEEN: b5, b4 / DRAGON: b6。形態ごとの外見 `<id>_p1`〜`p3`（b1_p1, b2_p1〜p2, b3_p1〜p2, b4_p1〜p2, b5_p1〜p3, b6_p1〜p3, b7_p1〜p3, b8_p1〜p3, final_p1〜p3） |

★ = 遠隔（距離6〜14を保ち、見えていればキャストして `EnemyBolt` を撃つ。ダメージ = ATTACK_DAMAGE）。それ以外は近接（2種の攻撃をランダム）。
バリアントごとに縮尺（当たり判定も追従）、パーツ表示、テクスチャ、発光、音、粒子、火炎耐性（fire_imp, salamander, lava_golem, fire_spirit）、
半透明（shade, ice_wraith）が決まる。crossbowman・dryad・snow_wolf は d1/d2/d5 のテンプレート用に追加。

## 2.1 ボスの大きさ

ボスは高さ約6〜10ブロックの巨体。モデルの縮尺と当たり判定（幅×高さ、ブロック）は外見ごと（`RigData.scale/hitbox`）で、
後の形態ほどモデル・当たり判定とも1形態につき4%大きい（`BossRigData.FORM_GROWTH`、`ERBoss.formGrowth()`、`getDimensions` が追従）。
ボスのテクスチャは UV の2倍の解像度（`uv_map.txt` の見出し `xN`、`EntityAssetsTest`）。

| ボス | 縮尺 | 第1形態 | 第2形態 | 第3形態 | 第4形態 | 見た目 |
|---|---|---|---|---|---|---|
| b1 盗賊王（KING） | 1.9 | 2.7×5.4 | 2.81×5.62 | — | — | 約6ブロック |
| b2 古代樹の守護者（TREANT） | 2.4 | 4.1×7.4 | 4.26×7.70 | 4.43×7.99 | — | 樹冠まで約8 |
| b3 砂塵の巨神（COLOSSUS） | 3.1 | 5.6×10.0 | 5.82×10.40 | 6.05×10.80 | — | 約10 |
| b4 凍獄の処刑人（QUEEN/処刑人） | 2.7 | 3.0×7.6 | 3.12×7.90 | 3.24×8.21 | — | 約8（斧を含む） |
| b5 氷獄の女王（QUEEN/女王） | 2.4 | 2.6×6.8 | 2.70×7.07 | 2.81×7.34 | 2.91×7.62 | 浮遊して約7 |
| b6 灼炎の竜王（DRAGON） | 2.7 | 6.4×6.1 | 6.66×6.34 | 6.91×6.59 | 7.17×6.83 | 鼻先から尾まで約18、翼幅約13 |
| b7 天空の機械神（COLOSSUS） | 3.0 | 5.4×9.8 | 5.62×10.19 | 5.83×10.58 | 6.05×10.98 | 約10（アンテナ含む） |
| b8 終焉の門番（KING） | 2.9 | 3.8×8.0 | 3.95×8.32 | 4.10×8.64 | 4.26×8.96 | 約8〜9 |
| final 終焉の王（KING） | 3.2 | 4.2×8.9 | 4.37×9.26 | 4.54×9.61 | 4.70×9.97 | 約9、浮かぶ王冠まで約11〜12 |

大きさに合わせて: 叩きつけの届く距離（`meleeReach` = 叩きつけのインパクトで武器・拳・爪・顎が床に届く距離 `RigData.reach`×縮尺、体の縁+1以上）、
段差の乗り越え（`maxUpStep` = 0.6+0.12×高さ、1〜2ブロック）、旋回の速さ（KING/QUEEN 14°/tick、DRAGON 10、TREANT/COLOSSUS 8。`faceTowards` は自分の速さで振り向き、
`BossMoveControl` は歩きながらの急旋回を抑えて減速）、歩く速さ（属性: KING 0.294 ≈3.8ブロック/秒、DRAGON 0.286、QUEEN 0.282、TREANT 0.243、COLOSSUS 0.233 ≈2.4ブロック/秒。
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

全ボス・全外見で11スロットを作り直した（終焉の王は専用の死亡 `death_final`）。共通の作り:

- **イージング**: キーごとの曲線（3次/4次の ease-in/out、行き過ぎて戻る back-out、ホールド）。Java 側 `AnimKit` が同じ曲線を実装（`rot("ssIOb", ...)`）。
- **溜め→速い打撃→フォロースルー→戻り**: 攻撃は読める溜め（身を反らす・腕を振りかぶる・胸が膨らむ）、インパクトへ加速する4次の ease-in、行き過ぎと戻り。
- **脚のIK**: 二関節の脚を接地点から解き、立ち足は地面に固定。歩行（`Gait`）は接地の割合・持ち上げの弧・かかと/つま先の角度を持ち、
  `RigData.walkSpeed`（モデルで縮尺で割る）が歩く速さと一致するので足が滑らない。竜は4脚の側対歩。足先・膝・すねは床に沈まない（自動で持ち上げ）。
- **二次運動**: マント・腰布・髪・ガウン・裾・鎖・蔓・苔・尾・翼端・アンテナをばね（周波数・減衰・慣性・重力）で親の動きから遅れて揺らし、ループは継ぎ目なし。床にめり込まない（床との衝突）。
- **接地と狙い**: 叩きつけの刃・拳・爪はインパクトのコマでちょうど床に（その後も同じ所に）、薙ぎ払いの刃は水平、突きは前へ、詠唱の宝珠は標的へ。倒れる・膝をつく体は床に乗る。
- **溜めの時間合わせ**: 動作のインパクトを技の予兆に収める（`retime`: 溜めだけを縮める）。インパクト時刻はtick単位（0.05秒の倍数、生成時に検査）。

| リグ | idle（ループ） | walk | slam | sweep | cast | roar（ループ） | charge | summon | phase_change | stagger | death |
|---|---|---|---|---|---|---|---|---|---|---|---|
| KING（b1/b8/final） | 4.0 | 1.5 | 2.25 / **1.1** | 1.85 / **0.8** | 1.85 / **0.8** | 1.2 | 1.8 / **0.85** | 2.45 / **1.25** | 3.0 / **1.8** | 1.4 | 3.6 / **1.8**（final 5.6: 1.6 で膝、4.5 で倒れる） |
| TREANT（b2） | 4.6 | 2.4 | 2.5 / **1.2** | 2.15 / **0.9** | 2.05 / **0.8** | 1.4 | 2.0 / **1.0** | 2.6 / **1.3** | 3.2 / **1.8** | 1.5 | 4.0 / **1.8** |
| COLOSSUS（b3/b7） | 4.5 | 2.2 | 2.4 / **1.1** | 2.25 / **1.0** | 2.05 / **0.8** | 1.4 | 2.0 / **1.0** | 2.6 / **1.3** | 3.2 / **1.8** | 1.5 | 4.0 / **1.8** |
| QUEEN 女王（b5） | 3.2 | 1.8 | 2.1 / **1.0** | 1.9 / **0.9** | 1.85 / **0.8** | 1.2 | 1.6 / **0.8** | 2.2 / **1.1** | 3.0 / **1.8** | 1.3 | 3.6 / **1.8** |
| QUEEN 処刑人（b4） | 3.6 | 1.6 | 2.2 / **1.1** | 2.0 / **1.0** | 2.0 / **0.95** | 1.2 | 1.8 / **0.85** | 2.2 / **1.1** | 3.0 / **1.8** | 1.3 | 3.6 / **1.8** |
| DRAGON（b6） | 3.4 | 1.8 | 2.4 / **1.1** | 2.3 / **1.05** | 2.05 / **0.8** | 1.4 | 2.0 / **1.0** | 2.6 / **1.3** | 3.2 / **1.8** | 1.4 | 4.0 / **1.8** |

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
  巨体の移動（`boss_arena` 41×6×41、壁と中央の柱。`tools/gen-gametest-structures.py` で生成）: b2・b3・b6・final が柱を回り込んで対角の目標に届き叩きつける（当たり判定で前方を探って回り込む）、その間ずっと壁や柱にめり込まない。
  `bossesGrowWithTheirForms`: 最終形態で当たり判定とモデルが12%大きく、めり込まない。
- 単体 `EntityAssetsTest`: テクスチャ・名前・UV表サイズ（テクセル密度 `xN` を含む）・ボスとの対応。
- 生成時の検査（`generate.py`）: ボスのインパクトがtick単位、同じボスの形態どうしで動作の時間が同じ、Javaの1メソッドに収まるキー数。
- 単体 `ClientModelSmokeTest`（CIのみ。リフレクションのためオフラインでは自動スキップ）: 全リグの本体・シールド層をベイクし、
  実際の `EnemyModel` / `BossModel` で全バリアントの外見を適用、全アニメーションを複数時刻で評価（GameTestサーバーはクライアントクラスを読まないため）。
