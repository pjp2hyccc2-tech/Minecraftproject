# 敵・ボスのエンティティ（`entity` / `client.entity` パッケージ）

雑魚4リグ・ボス5リグのエンティティ、モデル、キーフレームアニメーション、テクスチャ。戦闘の「いつ・何を」は
`boss`（BossBrain）とモンスターテンプレート側が決め、このモジュールは見た目・アニメーションと最小限のAIを提供する。

## 1. 構成

| パッケージ | 内容 |
|---|---|
| `entity`（共通） | `EREntityTypes`（DeferredRegister、`register(modBus)`）、`EREntityAttributes`（既定属性）、`ERMonster` と4リグ、`EnemyBolt`、`ERBoss`、`BossAnimation` / `EnemyAnim`、`EnemyVariant`（振る舞い表）、`BossKind`、生成コード `RigData` |
| `client.entity`（クライアント専用） | `EREntityRenderers`（レイヤー・レンダラー登録）、`EnemyRenderer` / `BossRenderer`、`RigGlowLayer`（発光）、`BossShieldLayer`（シールド）、`model.*`、生成コード `model.gen.*` |
| `tools/art_entities/generate.py` | **単一の情報源**: 形状・UV・アニメーション・テクスチャを定義し、Javaの形状/アニメーションクラス・`RigData`・PNG・プレビューを生成（決定的。Pillow/numpy） |

生成物は手で編集しない（スクリプトを直して `python3 tools/art_entities/generate.py` を再実行）。UV表は `tools/art_entities/uv_map.txt`、
全バリアントのプレビューは `preview.png`、アニメーションの連続コマは `preview_<rig>.png`。

## 2. エンティティとバリアント

| エンティティ | バリアント（`setVariant` のID） |
|---|---|
| `eternalrealm:humanoid_enemy` | bandit, ghoul, mummy, cultist★, prisoner, jailer, ice_knight, frost_mage★, fire_imp★, dark_knight, void_mage★, shade, crossbowman★, dryad★ |
| `eternalrealm:beast_enemy` | wolf, snow_wolf, cave_rat, frost_hound, abyss_hound, salamander, scorpion |
| `eternalrealm:golem_enemy` | treant, sand_golem, ice_golem, lava_golem, ruin_construct |
| `eternalrealm:flyer_enemy` | harpy, ice_wraith★, storm_spirit★, sky_sentinel, fire_spirit★ |
| `eternalrealm:enemy_bolt` | 遠隔攻撃の弾（★のバリアントが発射。属性: 炎/氷/虚無/呪い/雷/自然/矢） |
| `eternalrealm:boss_b1`..`boss_b8`, `boss_final` | KING: b1, b8, final（フェーズ外見 final_p1〜p3）/ TREANT: b2 / COLOSSUS: b3, b7 / QUEEN: b5, b4 / DRAGON: b6 |

★ = 遠隔（距離6〜14を保ち、見えていればキャストして `EnemyBolt` を撃つ。ダメージ = ATTACK_DAMAGE）。それ以外は近接（2種の攻撃をランダム）。
バリアントごとに縮尺（当たり判定も追従）、パーツ表示、テクスチャ、発光、音、粒子、火炎耐性（fire_imp, salamander, lava_golem, fire_spirit）、
半透明（shade, ice_wraith）が決まる。crossbowman・dryad・snow_wolf は d1/d2/d5 のテンプレート用に追加。

## 3. アニメーション

| 種類 | スロット |
|---|---|
| 雑魚 `EnemyAnim` | idle（呼吸・揺れ）, walk, attack1, attack2, cast, hurt, spawn（地面から出現）, death（約1.5秒、その後除去） |
| ボス `BossAnimation` | idle, walk, melee_slam, melee_sweep, cast, roar（予兆の溜めループ）, charge, summon, phase_change, stagger, death（3〜4秒） |

- 単発アニメーションはエンティティイベント（雑魚 64〜、ボス 80〜）でクライアントの `AnimationState` を開始。継続状態（バリアント、予兆、シールド、激昂、フェーズ）は SynchedEntityData。
- 攻撃の当たり・弾の発射はアニメーションの「インパクト時刻」（`RigData.impact`）に合わせてサーバーで発生。再生中は移動しない。
- 死亡: バニラの横倒し・赤色を使わず専用の死亡アニメーション → 煙（ボスは爆発・魂の粒子） → 除去。
- QUEEN は2セット（女王: 浮遊・滑走・詠唱 / 処刑人: 斧の振り下ろし・薙ぎ払い）。サソリは専用の攻撃（鋏・毒針）。

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
- `ERBoss`: `playAnimation(BossAnimation)`, `playingAnimation()`, `durationTicks/impactTicks`, `setTelegraphing/setShielded/setEnraged/setPhase`, `setAiFrozen`, `faceTowards(x, z)`, `bossId()`, `lookKey()`。
- シールドは見た目のみ（無敵は `BossBrain.isInvulnerable` を見て統合側が適用）。最大HPの既定は1024（バニラ上限）。
- 雑魚・ボスとも自然デスポーンしない（ピースフルでも消えない）。インスタンス終了時に統合側で除去する。
- ボス: 落下・窒息・溺死・圧死ダメージ無効、ノックバック耐性1.0、押されない、次元移動しない。
- 配線: `EternalRealm` のコンストラクタに `EREntityTypes.register(modBus);`（このブランチで追加済み）。属性・レンダラーは `@Mod.EventBusSubscriber` で自動。
- 名前: `docs/i18n/entity.json`（エンティティ名 `entity.eternalrealm.<type>`、バリアント名 `entity.eternalrealm.enemy.<variant>`）。

## 5. テスト

- GameTest `EntityGameTests`: 全バリアントの出現・縮尺・アニメーション・被弾・死亡アニメーション後の除去、近接/遠隔攻撃の命中、NBT保存、全ボスの全アニメーション・状態・耐性・死亡演出後の除去、ボスAIの叩きつけ命中。
- 単体 `EntityAssetsTest`: テクスチャ・名前・UV表サイズ・ボスとの対応。
