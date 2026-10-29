# HuntCraft 〜狩猟奇譚〜 — Developer Guide (read fully before coding)

Minecraft **Forge 1.20.1 (47.3.0), official Mojang mappings, Java 17**. Mod id `huntcraft`, base package `com.huntcraft`.
A full Monster-Hunter-style game system with ORIGINAL names/designs (never use Capcom names: no "Rathalos", "Felyne",
"Palico", "Aiボー" etc.). Player-facing text is Japanese-first (ja_jp) with English (en_us).

## 1. Ground rules for parallel work

* The repo is `/home/claude/minecraftproject` (branch `huntcraft`). **You work in your own git worktree on your own
  branch** `hc-<module>` and only push that branch. Never push `huntcraft`, never force-push, never touch other branches.
  ```bash
  git -C /home/claude/minecraftproject fetch -q origin huntcraft
  git -C /home/claude/minecraftproject worktree add /home/claude/wt/<module> -b hc-<module> origin/huntcraft
  cd /home/claude/wt/<module>
  ```
* **Only create/edit files inside your module's package and resource paths** (listed in your task). Everything else
  (core packages `registry/ core/ data/ network/ entity/ client/ item/ block/ gametest/`, `HuntCraft.java`,
  `build.gradle`, other modules) is read-only for you. If you truly need a core change, work around it in your module
  and describe the needed change in your final report.
* There is **no Minecraft jar locally** (network blocked). The only compiler is GitHub Actions. Run
  `bash tools/ci.sh "message"` inside your worktree: it commits, pushes your branch, waits for CI and prints compile
  errors + GameTest results (4–8 min). Iterate until `build=success gametest=success`.
  Write carefully to minimise iterations; javac reports up to 3000 errors at once, fix them all per round.
  If a git command fails with HTTP 429, just retry after a few seconds (the proxy allows 2 concurrent git ops).
* CI runs the GameTest server: `com.huntcraft.gametest.HCGameTests` spawns every monster registered in
  `MonsterRegistry`, forces every attack, kills it. Your code must not throw.
* Keep your final report SHORT: what you built, files, anything unfinished, any core change you need.

## 2. Module map (package → owner)

| Package | Content |
|---|---|
| `monster.ignivald` | Ignivald — **reference large monster (read it first)** |
| `monster.large.a` | Frostgarm 氷牙獣フロストガルム, Voltrix 雷鱗竜ヴォルトリクス |
| `monster.large.b` | Gravadon 岩槌竜グラヴァドン, Azragos 冥炎龍アズラゴス |
| `monster.small` | 10 small monsters |
| `gear.weapon` | 8 weapon types, weapon trees, sharpness, whetstone, weapon HUD |
| `gear.armor` | armor sets, skill calculation, charms, **all item icons for core items** |
| `village` | hunter village dimension + structure, NPCs, GUIs (smith/shop/kitchen/companion/notes/item box), Mofu companion |
| `quest` | quest definitions + flow, 3 quest map dimensions, gathering points, base camps, quest board GUI, quest HUD |

Each module has an entry class `XxxModule.init(IEventBus modBus)` (already called from `HuntCraft`). Register your own
`DeferredRegister`s there (`DeferredRegister.create(ForgeRegistries.X, HuntCraft.MODID)`; multiple registers per
registry are fine). Client code goes in `<module>.client` with
`@Mod.EventBusSubscriber(modid = HuntCraft.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)`.
Never reference client classes from common code (use `DistExecutor`/client-only subscribers).

## 3. Shared resources (fragment files — never edit another module's fragment)

* Lang: `src/main/lang/ja_jp/<module>.json` and `src/main/lang/en_us/<module>.json` → merged at build into
  `assets/huntcraft/lang/*.json`. Core keys are in `core.json` (all core materials, consumables, monster names
  `entity.huntcraft.<id>`, effects, skills `skill.huntcraft.*`, elements, weapon classes, messages).
* Sounds: `src/main/sounds/<module>.json` (sounds.json format, merged). You may reference vanilla files, e.g.
  `{"name": "minecraft:mob/ravager/roar1", "pitch": 0.7}`. Core events: see `registry/HCSounds.java`.
* Textures: `src/main/resources/assets/huntcraft/textures/{entity,item,block,gui,mob_effect}/...`.
  Item models: `assets/huntcraft/models/item/<id>.json`. Blockstates/block models as usual.
* Structures (.nbt): generate with `tools/nbt.py` (`Structure` class, DataVersion 3465) into
  `src/main/resources/data/huntcraft/structures/`.
* Python 3 + numpy + PIL are available for generating art. Put generator scripts under `tools/<module>/` and commit both
  scripts and generated assets.

## 4. Core API cheat-sheet (read the source for details)

* `HuntCraft.id(path)` → ResourceLocation. `HuntCraft.LOGGER`.
* `registry/HCItems` — all materials (`HCItems.IGNIVALD.scale/shell/special/tail/claw/gem` + `scalePlus/...`,
  same for `FROSTGARM, VOLTRIX, GRAVADON, AZRAGOS`; small monster mats; gathering mats; `ARMOR_SPHERE...`) and
  consumables (potions, drinks, steaks, BBQ spit, paintball, flash/dung/tranq bombs, pitfall/shock traps, farcaster,
  guild pass, hunter notes, barrel bombs). Lists: `HCItems.MATERIALS`, `HCItems.CONSUMABLES`.
* `registry/HCCreativeTabs` — `WEAPONS, ARMOR, MATERIALS, ITEMS, MONSTERS`. Add items via
  `BuildCreativeModeTabContentsEvent` (mod bus): `if (e.getTabKey().equals(HCCreativeTabs.WEAPONS.getKey())) e.accept(x)`.
* `registry/HCEffects` — blights (`FIREBLIGHT…DRAGONBLIGHT`), `POISON` (deadly), `PARALYSIS, SLEEP, STUN, ROAR`
  (movement-locking), buffs `COOL_DRINK, HOT_DRINK, DEMONDRUG, ARMORSKIN`.
* `registry/HCSounds` — generic monster/hunt/jingle sound events.
* `core/HCDimensions` — keys `HUNTER_VILLAGE, VERDANT_HILLS, FROSTPEAK_RANGE, EMBER_CALDERA`; `isVillage/isQuestMap`.
* `core/SafeZones` — `register(id, dim, center, radius)`; whole village is safe; hostiles can't spawn/enter/target.
* `core/Element, DamageKind, Rank, WeaponClass, Sharpness, SkillType, HunterSkills.level(player, skill)`.
* `core/IHunterWeapon` — implemented by weapons; `core/HunterDamage` applies the MH formula on every hit:
  `HunterDamage.hit(player, target, motionValue, ctx -> {...})`, `hitArea(...)`, `hitCone(...)` for special moves.
* `core/IUpgradeable` + `core/SmithingRegistry` (recipes: forge / upgrade; server actions `smith/forge`, `smith/levelup`).
* `core/ShopRegistry` (merchant stock; actions `shop/buy`, `shop/sell`).
* `core/HuntHooks` — cross-module hooks: `toVillage`, `toOverworld`, `toBaseCamp`, `questRank`, `addStarterKit`,
  `addExtraCarveProvider`, `addAttackMultiplier`.
* `core/HCEvents` (Forge bus) — `MonsterHuntedEvent(monster, player, captured)`, `MonsterPartBrokenEvent`, `GatherEvent`.
* `data/HunterData.get(player)` — zenny, HR/HRP, completed quests, hunt counts, size records, 108-slot `itemBox`,
  stamina, `getModuleTag("<yourmodule>")` for your own persistent data, `markDirty()` to sync to client.
* `network/HCNetwork` — string-keyed actions; **don't register packet classes**:
  server: `HCNetwork.registerServerAction("mod/thing", (ServerPlayer p, CompoundTag t) -> ...)`, client sends
  `HCNetwork.sendToServer("mod/thing", tag)`; server→client `HCNetwork.sendToPlayer(p, "mod/x", tag)` handled by
  `HCNetwork.registerClientAction("mod/x", tag -> ...)` (register in client setup). Prefix ids with your module name.
* `network/HCScreens` — `HCScreens.open(serverPlayer, HCScreens.QUEST_BOARD, data)` / `registerClient(id, opener)`.
* `client/hud/HunterHud` draws health/stamina at top-left (x=8,y=8); free area starts at `HunterHud.NEXT_Y` (32).
  Register your own overlays with `RegisterGuiOverlaysEvent` (`event.registerAbove(VanillaGuiOverlay.HOTBAR.id(), "name", overlay)`).
* `client/ClientState` — damage numbers, camera shake; `core/CameraShake.shake(serverLevel, pos, radius, intensity, ticks)`.

## 5. Monsters

Read `monster/ignivald/*` and `tools/models/ignivald.py` — copy that structure.

### 5.1 Entity
* Extend `entity.monster.LargeMonster` (large) or `SmallMonster` (small). Provide:
  * `public static final MonsterProfile PROFILE` (`MonsterProfile.builder(id, large)`): health LR/HR (≤1024),
    `scale(min,max)`, element & weakness stars, parts (`PartDef(name, side, up, forward, width, height, Hitzone)`,
    offsets in blocks at scale 1, side + = monster's RIGHT, up from feet, forward + = facing), breakable/severable parts
    with `hideBones` (bone names hidden in the model when broken), carve / tail / capture / break reward tables
    (low + high rank materials), quest reward. Small monsters: `carveCount` = drops on death.
  * `animationNames()` — every action animation name you play (attacks + "roar", "flinch").
  * `createAttacks()` — prefer `TimelineAttack.builder(name, ticks)` with timed events: `track, move, lunge, hit,
    hitSpan, hitFlags, sweep, projectile(ProjectileSpec), breath, sound, roar, tremor, wind, particles, hazard,
    flying, at/span (custom lambdas)`. Damage numbers are LOW-rank values (rank/enrage multipliers are automatic).
  * Optional overrides: `brainTick(target)` (special decisions e.g. flight, burrowing), `combatMove`, `customServerTick`,
    `getTurnSpeed`, `getAttackGap`, `roarSound`, `isHostile` (herbivores → false).
* Core gives you for free: multipart hitboxes, hitzones, part breaks/tail cut, rage (red glow pulse + roar),
  fatigue, poison/para/sleep/blast/KO, flinch, traps/tranq capture, flash blinding, paint, carving carcass (large),
  safe-zone avoidance, idle wandering around `homePos`, targeting, first-sight roar.
* `MonsterProjectile.Kind`: FIREBALL, ICE_SHARD, THUNDER_ORB, WATER_JET, DRAGON_ORB, ROCK, POISON_SPIT, MUD, SPIKE,
  FROST_ORB, LAVA_BLOB (textures provided by the armor/art module). `HazardEntity.Kind`: LIGHTNING, FIRE_POOL,
  ICE_FIELD, METEOR, ROCKFALL, GEYSER, DRAGON_PILLAR, POISON_CLOUD, ICE_SPIKES.
* Register in your module: `EntityType` (sized to the main body box), attributes via `EntityAttributeCreationEvent`
  (`HuntMonster.baseAttributes(hp, speed, knockbackResist)`), `ForgeSpawnEggItem` in `HCCreativeTabs.MONSTERS`
  (+ item model `{"parent":"minecraft:item/template_spawn_egg"}`), and **`MonsterRegistry.register(id, TYPE, PROFILE)`**.
  Natural spawning (quest-map biomes) is done by the quest module via `MonsterRegistry` — you also register
  `SpawnPlacementRegisterEvent` placements for small monsters (`SpawnPlacements.Type.ON_GROUND`, heightmap
  `MOTION_BLOCKING_NO_LEAVES`, predicate `Mob::checkMobSpawnRules`).
* Client: `HcModels.register(event, "<id>")` in `RegisterLayerDefinitions`, and
  `new HcMonsterRenderer<>(ctx, "<id>", shadowRadius)` in `RegisterRenderers`.

### 5.2 Model / texture / animation pipeline (tools/hcmodel)
* Write `tools/models/<id>.py` like `ignivald.py` and run `python3 tools/models/<id>.py --preview [anim ...]` from
  `tools/` → writes `assets/huntcraft/hcmodels/<id>.json`, `textures/entity/<id>.png` (+ `_glow.png` for emissive
  eyes/veins) and `tools/preview/<id>.png` (contact sheet). **Look at the preview images with the Read tool and iterate
  until it looks great** (silhouette, proportions, colours, animation poses). Previews reproduce Minecraft's renderer.
* Conventions: model pixels, **+Y is down, ground is y=24, model faces −Z**, entity right = −X.
  Bone pivot relative to parent; cube origin relative to bone. Use `mirror_tree()` for left/right copies (`_l` → `_r`).
* Materials (`hcmodel/paint.py`): ramps + patterns (`scales, plates, fur, membrane, horn, bone, rock, crystal, skin,
  veins, metal, cloth, wood`), automatic belly material on downward faces, 3D accents (stripes/spots), decals
  (`eye`, `eyes_front`, `mouth`, `teeth`, `nostrils`, `claws`, `tip`, `band`, `glow_spot`, `cutout` shapes for planes:
  `spike, spike_back, spike_front, spike_out, scallop, round, ragged`). Register new materials with
  `M('name', [6 hex colours dark→light], 'pattern', ...)` **in your own model script** (not in paint.py).
* Required animations (names are fixed): loops `idle` (always on, subtle), `walk`, `run` (+ `fly` for flyers);
  state poses `death` (holds last frame), `sleep`, `trapped`, `stun`; actions: `roar`, `flinch` + one per attack.
  Animation values are offsets ADDED to the rest pose (rotation degrees, position pixels +Y down, scale multiplier).
  `idle` and `walk` play together on the ground; actions are layered on top; state poses replace everything.
* Texel density 1 texel = 1/16 block (don't shrink models and upscale). Large monsters: 3–6 blocks tall,
  bounding box ~3×3; small monsters ~1–2 blocks. Use helpers in `hcmodel/parts.py` and `hcmodel/anim.py`.
* Model `config`: `head_bones` ([[bone, weight]…] for head look), `walk_speed`, `run_speed`, `walk_scale`.
* **HD textures** (`paint.TEXEL = 4`, default): the PNG has `texel_for(model)` texels per model pixel (4 for large
  monsters → 2048×1024, up to 6 for small models; the JSON keeps its 1× `texture_width/height`, entity UVs are
  normalised). Materials are painted with continuous HD patterns (overlapping scutes, rounded scales, tufted fur,
  veined membranes, ember cracks, facets…) and every face is tone-calibrated to the 1× pixel-art painter, so the
  colour identity of the old textures is kept when box-filtered. Glow rules unchanged (`*_glow.png`, emissive
  texels masked to opaque base texels; keep the lit ratio ≤ 0.15, `tools/hcmodel/hdcheck.py <script> [--export]`
  prints it and writes `tools/preview/<id>_hd.png` close-ups; `tools/hcmodel/swatches.py` renders a material sheet).
  Pixel-art post passes (`smallkit.stamp / stamp_eyes / accent_pass / pixel_pass`) are replayed onto the HD texture
  with rounded anti-aliased shapes (`hcmodel/hdup.py`). `model.hd_detail` overrides the feature-size factor;
  `paint.TEXEL = 1` restores the legacy painter. Always run scripts with `PYTHONHASHSEED=0` (cube seeds use `hash()`).
* **LabPBR maps** (`paint.PBR = True`): `<texture>_n.png` = RG tangent-space normal (OpenGL +Y up, 128 = flat),
  B = ambient occlusion (255 = none), A = height; `<texture>_s.png` = R perceptual smoothness, G F0 (≥ 230 = metal),
  B porosity (0–64) / subsurface (65–255), A emissive (255 = none, lower = lit). Written next to every monster,
  NPC / Mofu, dummy and armor layer texture (same size as the colour map); shader packs that do not use them ignore them.
* Other HD art: `tools/art/armor.py` (armor layers 512×256 via `platepaint.HDContext`, `--1x` for legacy; geometry
  Java unchanged), `tools/weapons/gen_art.py` (64×64 supersampled icons, `--1x`), `tools/art/icons.py` (32×32,
  `pix.HD`), `tools/art/sprites.py` / `tools/village/gen_blocks.py` (blocks 64×64, projectiles 64×64, effect icons
  36×36 through the edge-aware upscaler `tools/art/hdpost.py`; quest-module blocks: `python3 tools/art/hdpost.py <png>…`).

## 6. Items, weapons, armor conventions
* Item textures: 16×16 design, written HD as 32×32 (weapons 64×64; keep items ≤ 64×64 — bigger PNGs make heavy in-hand meshes), item model `{"parent":"minecraft:item/handheld","textures":{"layer0":"huntcraft:item/<id>"}}`
  (or `item/generated` for non-tools). Names in lang fragments `item.huntcraft.<id>` + `item.huntcraft.<id>.desc`.
* Weapons implement `IHunterWeapon` + `IUpgradeable` (level in NBT `HCLevel`); recipes go to `SmithingRegistry`.
* Armor items extend `ArmorItem`; skills reach the game through `HunterSkills.setProvider(...)` (armor module).

## 7. Minecraft 1.20.1 API reminders
* `entity.level()` (field is private), `level().isClientSide`, `player.serverLevel()`, `onGround()`.
* Damage: `entity.damageSources().mobAttack(e)`, `.playerAttack(p)`, `.generic()`, `.magic()`, `.explosion(a, b)`.
* Synched data: `defineSynchedData()` + `entityData.define(...)` (1.20.1 style).
* GUI: `GuiGraphics` (`fill`, `blit`, `drawString`, `drawCenteredString`, `renderItem`, `renderTooltip`),
  `Screen.renderBackground(GuiGraphics)` (one arg), `mouseScrolled(double, double, double)`,
  `Button.builder(Component, onPress).bounds(x, y, w, h).build()`.
* `BlockBehaviour.Properties.of().mapColor(...)` (no Material in 1.20). `SoundEvent.createVariableRangeEvent(rl)`.
* `SavedData`: `level.getDataStorage().computeIfAbsent(load, create, name)` (1.20.1 signature).
* Teleport across dimensions: `serverPlayer.teleportTo(ServerLevel, x, y, z, yaw, pitch)`.
* Creative tabs: `Registries.CREATIVE_MODE_TAB`; `ForgeSpawnEggItem(Supplier<EntityType>, bg, fg, props)`.

## 8. Hunter combat animations (client/player, gear/weapon/action, tools/models/hunter*.py)

* **Rig**: `tools/models/hunter.py` builds `hcmodels/hunter.json` + `hunter_slim.json` (vanilla 64x64 skin UVs on a
  14-bone rig with elbows/knees/wrists: `root > hips > (leg_r > leg_r_lower, leg_l > …, chest > (head, arm_r >
  arm_r_lower > hand_r, arm_l > …))`). `client/player/HunterRenderer` replaces the vanilla player renderer
  (RenderPlayerEvent.Pre) whenever a hunter weapon is held / sheathed, an action runs or the player rolls; the player's
  own skin is used (no texture to regenerate). `ArmorRig` re-parents armor geometry (ArmorGeometry JSON or vanilla
  boxes, split at elbows/knees/waist) onto the bones; `HeldItems` draws the main/off-hand items from the wrist bones
  and the sheathed weapon (`anim/sheath` packet) on the back/hip; `FirstPersonHands` draws the same posed arms in first
  person (RenderHandEvent); `WeaponTrail` draws the additive blade ribbon sampled from the grip/tip sprite pixels.
* **Animations** live in `tools/models/hunter_anims.py` (absolute poses via `hcmodel/rig.py`: `Pose`, IK for the
  arms/legs, `wk()` weapon keys = right hand position + blade direction, the left hand joins two-handed grips).
  `python3 tools/models/hunter.py --preview <anim…> [--views 3 --frames 8]` writes `tools/preview/hunter_<anim>.png`.
  Names: `idle_<cls>` / `charge_*` / `guard_*` / `ln_dash` (loops chosen by `HunterAnimator.stance`), `walk[_armed]`,
  `run[_armed]`, `crouch`, `dodge_f/b/l/r`, and one animation per action.
* **Actions** (`Attack(...)` in hunter_anims.py → `hunter_actions.json`): ticks, `hit(tick, mv, range, cone|area,
  kind, ko, hitstop, shake, fx, sound)`, `move(from, to, speed[, up])` root motion, `cancel(tick)`, `next(name)` for
  the left-click chain, `trail(from, to)`, `iframes(from, to)`, `stamina`, `tag(...)` (`special`, `nocancel`, `swap`,
  `onback`, `note`, `perform`, `charge`). The server (`gear/weapon/action/CombatActions`) runs the timeline each
  tick: damage at hit ticks through `HunterDamage`, sounds, i-frames, cancel window + input buffer, and mirrors the
  action to tracking clients (`anim/action`, `anim/impact` = hit-stop + sparks, `anim/stance`, `anim/sheath`).
  Weapons hook in through `HunterWeaponItem.comboStart / guardAction / onActionStart / onActionTick / onActionEnd /
  configureActionHit / onActionHitDone`.
* **Adding an attack**: author it in hunter_anims.py (`A = Attack(m, 'xx_name', cls, ticks)`, `A.pose/A.key…`,
  `A.hit(...)…build()`), run `python3 tools/models/hunter.py --preview xx_name`, look at the sheet, then start it from
  the weapon (`CombatActions.start(player, "xx_name")`) or chain it with `.next()`. No Java change is needed for the
  animation itself. The CI autotest photographs every weapon (`screenshots/combat_*.png`).
