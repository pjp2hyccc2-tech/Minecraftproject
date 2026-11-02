# 遭難 ―SOUNAN― : design contract (read fully before working)

Minecraft **Forge 1.20.1 (47.3.0), official Mojang mappings, Java 17**. Mod id **`sounan`**, base package
`com.sounan`. Player-facing text is Japanese first (`ja_jp`), English (`en_us`) second. Built on the systems of the
previous mod 冥婚 ―MEIKON― (branch `meikon`; `git show meikon:<path>` shows any old file).

A survival-horror game: **lost on a mountain at night**. The goal is to get off the mountain alive. The game must be
genuinely, sleep-losing frightening — dread, darkness, sound, unpredictability, the feeling of being watched — not gore.
**Top priority: atmosphere — the shader pack, screen effects, music, dissonance and psychoacoustically unpleasant
sound.** Everything else serves that.

## 1. Story

Present day, early November. The player went solo hiking on **帰らず山 (Kaerazu-yama, "the mountain you do not come
back from")**. At dusk, on the way down, they heard someone call from below the trail, stepped off the path, slipped
and fell down a steep slope. They wake up at night in a cedar forest, fog everywhere. Their backpack is gone.
In their hands: **a smartphone with no signal (圏外)** and **a flashlight (懐中電灯)**.

The locals' old rules (山の掟) explain what lives here: things that peek from behind trees, a light that calls to
you in a rescuer's voice, something that walks right behind you, the owners of the empty huts — and 御山様, the
mountain itself, which keeps the people who wander in at night (神隠し). The player keeps finding notes from a hiker
who got lost here years ago and never got out.

**Endings**
* **下山 (escape)**: reach your car at the trailhead (登山口) at dawn. The phone finally gets signal; dozens of messages
  arrive. The timestamps reveal how long you were really gone (one night if never captured — days, months, years
  otherwise). If you were captured at least once, something rides home with you (a face in the rear-view mirror).
* **神隠し (spirited away)**: captured too many times. You become one of the figures that only a camera can see.

## 2. The mountain (world)

* Dimension **`sounan:kaerazu`** (dimension, dimension_type and biome share the id; generator type
  **`sounan:mountain`**). Eternal night: fixed time 18000, sky light on (the moon is high, mostly hidden by the cedar
  canopy), no precipitation, no vanilla mob spawns, adventure mode (no breaking/placing).
* The terrain is designed offline (`tools/terrain/`) and baked into `data/sounan/terrain/terrain.bin.gz`
  (512×512 columns, X and Z from -256 to 255): surface height, surface type, tree density, water level and an
  **area id** per column. POI positions are in `data/sounan/terrain/pois.json`. Both the server (generation, AI
  director) and the client (area music, phone map) read them.
* North (−Z) is high (Y≈190), south (+Z) low (Y≈70). Outside the playable route corridor the slopes become
  impassable and the fog swallows you: wandering off loops you back (ループ) with a whisper.
* Forest: tall straight **cedars (杉)** — bare trunks up to 8–14 blocks, narrow dark cones of needles above. At eye
  level the forest is a field of trunks: perfect for things peeking from behind them. Ferns, fallen logs, mossy
  boulders, dead leaves. Pink **marking ribbons (目印テープ)** tied around trunks show the old trail.

### Areas (area id → name; the HUD shows the name when you first enter)

| id | key | name | what happens |
|---|---|---|---|
| 0 | `out` | — | outside the route: steep, fog, loops you back |
| 1 | `rakuraku` | 滑落地点 | start: bottom of the slope you fell down. Your hat, broken branches |
| 2 | `sawa` | 沢 | a mountain stream in a gully. **The lost bag** hangs on a fallen tree at the water. Checkpoint |
| 3 | `gake` | 崖 | the stream drops over a cliff. A climber's iron stake (ロープ場): use the **rope** to climb down |
| 4 | `sugi` | 杉の森 | dense cedar forest, faded trail with pink ribbons. 覗き territory. A dead end with old shoes and a straw doll |
| 5 | `haison` | 廃村 | abandoned hamlet: 5 ruined houses, bus stop, dead public phone (it rings), jizo row, notice board with **山の掟**. A house with a 小屋主 has the **御神酒** (offering). Checkpoint (bus shelter) |
| 6 | `hokora` | 祠 | stone steps up to a tiny shrine under a sacred cedar (shimenawa), straw dolls nailed to trees. The way on is a **fog wall** (loops you back) until you offer 御神酒. Checkpoint |
| 7 | `tsuribashi` | 吊り橋 | old suspension bridge over a gorge; planks creak, something under it |
| 8 | `koya` | 山小屋 | emergency hut with blue tarp and a dead campfire. A 小屋主 sleeps inside. The **tunnel gate key** hangs on the wall; the hikers' logbook (登山者ノート). Checkpoint after the key |
| 9 | `tetto` | 鉄塔 | optional ridge with a power pylon: **one bar of signal** → messages arrive, the GPS map downloads, a call to 119… |
| 10 | `tunnel` | 廃トンネル | chained gate (key), ~80 blocks of pitch-black abandoned road tunnel. The climax: 御山様 |
| 11 | `tozanguchi` | 登山口 | forest road down to the trailhead parking lot and your car → ending |

## 3. Mechanics

* **Heart rate 心拍数 (= fear 0–100)**, server value, synced. Shown as a small pulsing `♥ 98` (smartwatch) on the
  HUD; 72 bpm calm → 180 bpm panic. Rises in darkness, when watched, near monsters, on scares; falls in light, at
  checkpoints, when calm. Drives heartbeat sound (at the real bpm), breathing, vignette, tunnel vision, chromatic
  aberration, flashlight wobble, hallucinations. At ≥85 your breathing is loud enough for monsters to hear.
* **Danger 0–1**: how close and how hostile the nearest threat is (chase = 1). Drives the danger music layer,
  the flashlight flicker, the forest going silent.
* **Stamina**: sprinting drains; empty = panting (noise), no sprint.
* **Noise**: sprinting, jumping, doors, dropped items, ringing the bear bell, loud breathing. Sneaking is silent.
* **Flashlight 懐中電灯** (`flashlight` / `flashlight_on` / `flashlight_low`): right-click toggles. Battery 100 % ≈ 8
  min of light; < 15 % = `flashlight_low` (dim, flickering); 0 % = dead. **Spare batteries** restore it. Works in
  either hand. Flickers when something hostile is close. Shining it at some monsters provokes them.
* **Smartphone スマホ** (`phone` / `phone_light` / `phone_camera`): right-click opens the phone (GUI, game keeps
  running). Status bar: time (starts 2:13 AM), 圏外 or signal bars, battery (starts 31 %). Apps:
  * **地図**: GPS dot + heading + your breadcrumb trail on a blank grid ("地図データなし"); after the map is
    downloaded at the pylon it shows the terrain, the trail and the trailhead. The compass spins near monsters.
  * **カメラ**: switches the held phone to `phone_camera`: night-vision camcorder view (shader / overlay), REC, battery.
    Shows **写るもの** that the naked eye cannot see. Right-click = photo with flash (provokes 覗き, stuns briefly).
    Sneak + right-click = leave camera mode.
  * **ライト**: phone torch (`phone_light`): wide weak light, drains the phone fast.
  * **メッセージ**: old messages (mother: 「気をつけてね」 …). New ones arrive only with signal — the twist.
  * **電話**: 110 / 119. 「圏外です」 — until the pylon. The call does not go the way you hope.
  * **メモ**: the notes you found.
  Phone battery drains with screen/camera/light; **power bank** +60 %. 0 % = black screen.
* **The bag (失くしたバッグ)**: a block at the stream. Opening it gives: 予備電池 ×2 (`battery`), モバイルバッテリー
  (`power_bank`), 熊鈴 (`bear_bell`), ロープ (`rope`), 登山地図 (`trail_map`), 発煙筒 (`flare`), お守り (`omamori`).
* **熊鈴 bear bell**: ring it (right-click, 5 uses): 覗き retreat, a following 後ろ lets go. It is loud (noise).
* **Rope**: use on the climber's stake (`rope_anchor`) at the cliff → a climbing rope hangs down.
* **発煙筒 flare** (`flare` → `flare_lit`, 40 s, red light): 御山様 and the others will not come near the flame.
* **お守り omamori**: saves you once when caught (it bursts).
* **御神酒 omiki** (from a village house): offer at the shrine → the fog wall lifts.
* **山小屋の鍵 hut key** (`hut_key`): opens the chained tunnel gate.
* **Checkpoints**: bag site, bus shelter, shrine, hut, tunnel mouth. Being caught = jumpscare → black → "wake up" at
  the last checkpoint (flashlight ≥ 30 %). Each capture also makes **time jump** (the phone's date moves: +1 day,
  +9 days, +2 months, +3 years …). **5 captures = 神隠し ending.**
* **AI director**: paces events by area and fear: branch snaps, someone running past in the trees, a deer screaming
  like a woman, knocks on a trunk, a distant bear bell that is not yours, whispers in one ear, the insects suddenly
  falling silent, footsteps that stop when you stop, the phone vibrating in a dead zone (「みつけた」), a figure on a
  ridge in the fog, jizo statues that have turned to face you, the public phone ringing.

### The rules (山の掟, on the village notice board) — and the monsters behind them

一、夜の山で、灯りを持つ者に呼ばれても、決してついて行ってはならない。 → **呼び声**
一、木の陰から覗くものと、目を合わせてはならない。灯りを向けてもならない。 → **覗き**
一、背中に気配を感じたら、決して振り返らず、そのまま歩き続けること。 → **後ろ**
一、小屋の主を起こしてはならない。小屋では音を立てず、屈んで歩くこと。 → **小屋主**
一、祠の前を通る者は、御神酒を供えること。供えぬ者は霧に迷う。 → shrine fog wall
一、御山様に会ったなら、火を焚け。御山様は火を嫌う。 → **御山様** + flare

## 4. Monsters (entities)

All monsters extend vanilla `Mob` classes and implement `com.sounan.entity.Animated`:
```java
public interface Animated { int pose(); float poseTime(float partialTick); }   // pose id + ticks since it began
```
Models (`com.sounan.client.model.<Name>Model`, see §6) read `pose()`/`poseTime()` plus the usual limbSwing,
ageInTicks, netHeadYaw, headPitch. Eyes / lights are drawn by a second full-bright layer `<name>_eyes.png` (same UV
layout, only eye / lamp pixels opaque) with `RenderType.eyes` → the shader makes them **reflect in the flashlight
beam (eyeshine)**.

| id | name | size (w×h) | look | poses |
|---|---|---|---|---|
| `nozoki` | 覗き | 0.6×2.6 | far too tall and thin, grey-white skin, long neck, wet black hair to the waist, huge black eyes with pin-prick pupils, lipless wide mouth, arms to the knees, long fingers | 0 PEEK_LEFT, 1 PEEK_RIGHT, 2 STAND, 3 CRAWL, 4 RETREAT |
| `yobigoe` | 呼び声 | 0.6×1.9 | looks like a rescuer: faded orange rain jacket, hood, **headlamp** (eyes layer = lamp + beam glow), dark trousers. Its face is smooth skin with no features | 0 CALL, 1 IDLE, 2 TURN, 3 RUSH |
| `ushiro` | 後ろ | 0.6×2.2 | woman-like, bent forward over you; long black hair hanging forward, chalk-white face, too-wide grin, eyes far too close together, grey burial kimono | 0 LEAN, 1 GRAB |
| `koyanushi` | 小屋主 | 0.7×1.8 | emaciated old thing in filthy white underclothes, grey skin, bald with a few long grey hairs, sunken eye sockets with tiny eyes, long yellow nails | 0 SLEEP, 1 WAKE, 2 CHASE, 3 FACE_WALL, 4 STAND |
| `oyamasama` | 御山様 | 1.2×4.2 | giant, emaciated, dark bark-like skin; a white paper face cloth (顔布) with one painted red eye; cedar branches / antlers growing from the head; shimenawa rope with white shide around the neck; arms to the ankles | 0 STAND, 1 WALK, 2 CRAWL, 3 REACH, 4 LOOM |
| `utsurumono` | 写るもの | 0.6×1.8 | translucent pale hiker: faded rain jacket, hood up, face in shadow, one arm pointing the way. Rendered only through the phone camera | 0 POINT, 1 STAND, 2 LOOK |

Pose meanings: **PEEK_LEFT/RIGHT** = the 覗き stands behind a trunk facing you; only the head (tilted ~70° sideways
out past the trunk edge, displaced ~10 px to its left/right) and one hand gripping the trunk edge show. **RETREAT** =
the head slowly slides back behind the trunk. **CRAWL** = sprinting on all fours, head up, spider-like.
**CALL** = one arm waving slowly overhead. **TURN** = body away from you, head rotated 180° to look at you.
**RUSH** = sprint. **LEAN** = bent at the hips, face at the player's head height, hair hanging, arms dangling.
**GRAB** = arms up, mouth open. **SLEEP** = lying flat on its back along its facing. **WAKE** = sitting up over 40
ticks (poseTime) with jerky motion, head snapping sideways. **CHASE** = hunched run, arms forward. **FACE_WALL** =
sitting, facing away, rocking slowly. **REACH** = one arm forward. **LOOM** = bending down over you.
**POINT** = arm raised along its facing, head down. **LOOK** = head raised toward the viewer.

Behaviour summary (server): 覗き peeks; looking at it calmly makes it slide away, but **approaching within 7
blocks, keeping the light on it ~1.2 s or a flash photo** → shriek + crawl-charge. 呼び声 calls 「おーい」 with its
headlamp 30–50 blocks away; **walking toward it** (within 14) → the lamp goes out, it is suddenly beside you.
後ろ attaches behind you (matching footsteps, breath, 「ふりかえって」); **turning around quickly** = it is face to face
with you; walk on until it lets go (or ring the bell). 小屋主 sleeps in huts; **noise, light on its face or walking
upright next to it** wakes it (it sits up… slowly) and it chases you out. 御山様 hunts the final act; light from
the flashlight slows it, the flare repels it. 写るもの stand along the route facing the way out.

## 5. Registry ids

### Blocks (`assets/sounan/textures/block/<texture>.png`, 16×16 unless noted)

Nature / ground
| id | textures | notes |
|---|---|---|
| `cedar_log` | `cedar_log`, `cedar_log_top` | axis. Reddish-brown fibrous vertical bark (杉), dark |
| `cedar_log_ribbon` | `cedar_log_ribbon`, `cedar_log_top` | cedar bark with a faded **pink marking tape** band tied around (side texture) |
| `cedar_log_shimenawa` | `cedar_log_shimenawa`, `cedar_log_top` | sacred cedar: bark with a thick straw rope band + white zigzag shide |
| `cedar_leaves` | `cedar_leaves` | dense dark blue-green needles, cutout_mipped, no tint |
| `forest_floor` | `forest_floor_top`, `forest_floor_side` (bottom `forest_dirt`) | dead brown cedar needles, a few dull autumn leaves, moss specks |
| `forest_dirt` | `forest_dirt` | dark humus soil with roots |
| `moss_floor` | `moss_floor`, side `forest_floor_side` | damp moss carpet |
| `trail` | `trail_top`, side `forest_dirt` | packed path: brown dirt, exposed roots, pebbles |
| `mountain_rock` | `mountain_rock` | dark grey wet andesite, cracks, lichen |
| `mossy_rock` | `mossy_rock` | same rock, thick moss |
| `river_stones` | `river_stones` | rounded grey/brown pebbles, wet |
| `fallen_leaves` | `fallen_leaves` | transparent decal, dull autumn leaves (rust, ochre, brown) |

Buildings / road
| id | textures | notes |
|---|---|---|
| `weathered_planks` | `weathered_planks` | sun-bleached grey old wood |
| `dark_planks` | `dark_planks` | dark rotten interior boards |
| `old_plaster` | `old_plaster` | village wall: cracked mouldy plaster, exposed lath |
| `tin_roof` (+ `tin_roof_stairs`, `tin_roof_slab`) | `tin_roof` | rusted corrugated iron (トタン), ribs vertical |
| `blue_tarp` | `blue_tarp` | faded, dirty blue plastic sheet; carpet-height decal |
| `concrete_old` | `concrete_old` | tunnel lining: grey concrete, water streaks, cracks, efflorescence |
| `concrete_moss` | `concrete_moss` | tunnel portal: concrete with moss and dead ivy |
| `asphalt` | `asphalt` | cracked dark asphalt, weeds in cracks |
| `steel_lattice` | `steel_lattice` | galvanised pylon lattice, X-bracing, transparent gaps (cutout) |
| `tatami_old`, `old_planks`, `rotten_planks`, `dark_beam`, `plaster_wall`, `shoji_torn`, `futon`, `stone_lantern`, `blood_stain`, `footprints`, `hair_strands` | MEIKON textures (copied) | village house interiors and decals |

Doors (DoorBlock, `<id>_bottom`, `<id>_top`, item texture `<id>`)
| id | notes |
|---|---|
| `hut_door` | old plank door, rusty hinges |
| `house_door` | village house sliding door: dark wood frame, broken frosted glass panes |
| `iron_gate` | tunnel gate: rusty bars, heavy **chain and padlock** (locked until the key) |

Props (custom-shaped **block models** made by the art pass: `assets/sounan/models/block/<id>.json`, model faces
north; blockstates rotate by `facing`)
| id | textures | shape / notes |
|---|---|---|
| `lost_bag` | `lost_bag` | the player's backpack (red/orange, wet, muddy), lying on its side; state `open` (open model `lost_bag_open`) |
| `memo` | `memo` | a sheet of damp paper on the ground (decal) |
| `memo_wall` | `memo_wall` | paper nailed to a trunk/wall (wall decal) |
| `logbook` | `logbook` | an open notebook (登山者ノート) on a surface |
| `notice_board` | `notice_board` | wooden notice board with a little roof, papers pinned, on two posts (1×2 tall look inside 1 block? → keep inside one block, posts short) |
| `signpost` | `signpost` | wooden trail sign: post + arrow board with carved strokes (text shown in HUD) |
| `jizo` | `jizo` | small stone jizo statue with red bib and knitted cap, mossy |
| `hokora` | `hokora` | tiny wooden shrine on a stone base, gable roof, faded paper; state `offered` (sake bottle + lit candle in front, model `hokora_offered`) |
| `wara_ningyo` | `wara_ningyo` | straw doll nailed to a trunk (wall decal) |
| `rope_anchor` | `rope_anchor` | iron stake driven into rock with a ring; state `roped` (rope tied, model `rope_anchor_roped`) |
| `climbing_rope` | `climbing_rope` | hanging frayed rope, ladder-like climbable (transparent background) |
| `public_phone` | `public_phone` | old green public telephone on a small shelf |
| `bus_stop` | `bus_stop` | rusty round bus-stop sign on a pole (pole fills the block, sign at top) |
| `tunnel_lamp` | `tunnel_lamp`, `tunnel_lamp_lit` | dead sodium wall lamp; state `lit` (orange glow) |
| `guardrail` | `guardrail` | rusty road guardrail panel on a post |
| `key_hook` | `key_hook`, `key_hook_empty` | small wooden key board on the wall with one key (state `has_key`) |
| `car_door` | `car_door` | white car body panel with handle and dark window (the car at the trailhead) |

### Items (`assets/sounan/textures/item/<id>.png`, 16×16)
`flashlight`, `flashlight_on`, `flashlight_low`, `phone`, `phone_light`, `phone_camera`, `battery`, `power_bank`,
`bear_bell`, `rope`, `trail_map`, `flare`, `flare_lit`, `omamori`, `hut_key`, `omiki`, `memo`.
3D held models (made by the art pass, `forge:separate_transforms`: 3D model in hand, the 16×16 sprite in the GUI):
`flashlight`, `flashlight_on`, `flashlight_low`, `phone`, `phone_light`, `phone_camera`, `flare`, `flare_lit`.

### Entities — see §4. Textures `assets/sounan/textures/entity/<id>.png` + `<id>_eyes.png`.

### GUI (`assets/sounan/textures/gui/`)
`vignette`, `grain`, `paper`, `hands` (MEIKON copies) · `title_logo` 512×256 (「遭難」 brush calligraphy +
"SOUNAN") · `faces_overlay` 256² (transparent; barely visible faces in darkness toward the edges) ·
`jumpscare_nozoki`, `jumpscare_yobigoe`, `jumpscare_ushiro`, `jumpscare_koyanushi`, `jumpscare_oyamasama` 256²
(full-face horror close-ups on black, transparent-free) · `phone/frame` 160×320 (black modern smartphone, screen area
transparent at x 8..151, y 24..295) · `phone/wallpaper_0..2` 144×272 (lock-screen photo the player took at the summit
at noon: blue sky, ridges; `_1` the same photo with a pale figure among the distant trees; `_2` the figure much
closer, face visible) · `phone/icons` 192×32 (six 32×32 app icons: 地図, カメラ, ライト, メッセージ, 電話, メモ) ·
`trail_map` 512×512 (paper trail map, generated from the terrain by `tools/terrain`).

## 6. Code ownership / model contract

* **Models**: `src/main/java/com/sounan/client/model/<Name>Model.java` for `Nozoki`, `Yobigoe`, `Ushiro`,
  `Koyanushi`, `Oyamasama`, `Utsurumono`. Each: `public class NozokiModel<T extends Mob & Animated> extends
  HierarchicalModel<T>` (or `EntityModel<T>`), constructor `(ModelPart root)`, `public static final
  ModelLayerLocation LAYER = new ModelLayerLocation(new ResourceLocation("sounan", "nozoki"), "main");`,
  `public static LayerDefinition createBodyLayer()`. Authored at true scale (1 px = 1/16 block, feet at y = 24).
  The renderers (`client/render/*Renderer.java`) only add the eyes layer and shadow.
* Blockstates, simple cube models and generated item models: `tools/gen_assets.py` (it never overwrites the prop
  block models or the 3D item models listed above).
* Sounds: `tools/audio/catalog.py` → `assets/sounan/sounds/**`, `assets/sounan/sounds.json`,
  `tools/audio/subtitles_{ja,en}.json` (merged into the lang files by `tools/gen_lang.py`).
* Shaders: `src/main/shaderpack/shaders/**` ("SounanShaders"), `tools/shader/**`.

## 7. Sounds (`assets/sounan/sounds/<path>.ogg`, event id `sounan:<event>`)

OGG Vorbis 44.1 kHz. **Mono (M) for everything positional**, stereo (S) for beds, music, UI. `att` = attenuation
distance for positional sounds. Variants `name_1..n`. Long quiet stretches, then violence: the mix must have real
dynamic range (the forest at night is *quiet*).

**Psychoacoustic palette (use it everywhere):** binaural beats (stereo carriers 4–7 Hz apart → theta beating),
infrasound-like sub rumble (30–45 Hz with harmonics so small speakers carry it, slow AM ~5–8 Hz), **roughness**
(30–150 Hz amplitude modulation — the acoustic signature of screams that triggers alarm), **nonlinear phenomena**
(subharmonics, biphonation, deterministic chaos, sudden frequency jumps — like animal distress calls), **Shepard–
Risset glissandi** (endless rise / fall = unresolvable anxiety), tone clusters, minor seconds, tritones, micro-tonal
beating, sounds at the edge of audibility, dry ultra-close ear sounds (whispers, breath) contrasted with huge
reverbs, false risers that cut to silence, and **silence itself** (the insects stop).

Ambient beds (S, loops)
| event | files | description |
|---|---|---|
| `amb.forest` | `amb/forest` 60 s | night cedar forest: soft wind high in the canopy (slow swells), far stream, rare creak |
| `amb.insects` | `amb/insects` 40 s | autumn insects chorus (鈴虫, こおろぎ, かんたん); it **stops** when danger rises |
| `amb.wind_ridge` | `amb/wind_ridge` 40 s | strong cold wind on a ridge, whistling in steel lattice |
| `amb.tunnel` | `amb/tunnel` 40 s | inside a dead road tunnel: low hum, air movement, sub resonance, far drips |
| `amb.village` | `amb/village` 40 s | loose tin sheets rattling, a shutter, detuned wind chime far away |
| `amb.silence` | `amb/silence` 20 s | near-silence: room-tone hiss, faint high ringing, ear pressure (silence drops) |
| `amb.stream` | `amb/stream` 20 s (M, att 24, loop) | mountain stream over stones |

Music layers (S, loops unless noted; the client mixes them by fear / danger / area)
| event | files | description |
|---|---|---|
| `music.dread` | `music/dread` 64 s | base score: very low drone with a binaural theta beat, beating partials, sub swells, faint bowed metal |
| `music.tension` | `music/tension` 32 s | dissonant micro-tonal string clusters, irregular low heartbeat thumps (volume = fear) |
| `music.danger` | `music/danger` 16 s | endless Shepard rise + rough (AM) strings + ticking (volume = danger) |
| `music.chase` | `music/chase` 16 s | chase: pounding low drums ~150 bpm with irregular accents, screeching clusters, falling Shepard, metal scrapes |
| `music.safe` | `music/safe` 40 s | checkpoint: fragile sparse detuned piano/music-box tones, long reverb; sad, not comforting |
| `music.oyama` | `music/oyama` 32 s | final act: huge low brass-like clusters, distant taiko, shō-like dissonant chords, chant-like vowel drones |
| `music.binaural` | `music/binaural` 32 s | pure binaural theta bed (two sine carriers ~6 Hz apart + very low pink noise) under fear |
| `music.title` | `music/title` 48 s | title: wind, a distant bear bell, a lonely high tone, low drone, sparse piano |
| `music.ending_good` | `music/ending_good` 50 s (no loop) | dawn: warm pads breaking through, a hint of unease at the very end |
| `music.ending_bad` | `music/ending_bad` 30 s (no loop) | 神隠し: the safe motif on a dying music box, reversed swells, wind |

Ambient one-shots (M)
| event | files | att | description |
|---|---|---|---|
| `amb.branch_snap` | ×5 | 24 | dry branch snapping under a foot |
| `amb.footsteps_run` | ×3 (3–4 s) | 32 | someone running through leaves and undergrowth, passing |
| `amb.footsteps_walk` | ×3 (4–6 s) | 24 | slow steps in dry leaves, stopping abruptly |
| `amb.deer_scream` | ×3 (2–3 s) | 64 | sika deer call: a piercing descending scream-whistle, eerily human |
| `amb.fox_scream` | ×2 | 48 | fox scream: raspy shriek |
| `amb.owl` | ×2 | 64 | ural owl hooting far away |
| `amb.tree_knock` | ×3 | 40 | deliberate knocks on a trunk (2–4 hits) |
| `amb.rockfall` | ×2 | 40 | small rocks tumbling down a slope |
| `amb.creak_tree` | ×4 | 32 | deep groan of a cedar trunk bending |
| `amb.thud` | ×3 | 48 | something heavy falling in the distance |
| `amb.whisper` | ×6 (1.5–3 s) | 6 | unintelligible close whispers (placed at one ear) |
| `amb.whisper_name` | ×2 | 6 | a whispered 「ねえ…」 |
| `amb.giggle` | ×3 | 16 | soft breathy giggle in the dark |
| `amb.distant_bell` | ×3 (4–6 s) | 64 | someone else's bear bell jingling far away, slow walking rhythm |
| `amb.distant_scream` | ×2 | 96 | a human scream far away, cut off |
| `amb.distant_call` | ×3 | 96 | 「おーい」 very far away, reverberant |
| `amb.breath_behind` | ×3 | 4 | slow heavy breathing right behind you |
| `amb.drip` | ×4 | 16 | water drips (tunnel) |
| `amb.tin_bang` | ×2 | 32 | tin sheet banging in the wind |
| `amb.wind_chime` | ×2 | 24 | a single detuned 風鈴 ring |
| `amb.radio` | ×2 (3–5 s) | 24 | an old radio somewhere: static and fragments of a garbled voice |
| `amb.tinnitus` | `amb/tinnitus` 4 s (S) | – | ear ringing (after scares, silence drops) |
| `amb.ear_pressure` | `amb/ear_pressure` 3 s (S) | – | low pressure swell ending in an "ear pop" |

Monsters (M)
| event | files | att | description |
|---|---|---|---|
| `nozoki.breath` | ×3 | 12 | wet slow breathing |
| `nozoki.giggle` | ×2 | 20 | high broken giggle |
| `nozoki.shriek` | ×2 | 48 | provoked: horrible rough scream with chaos/subharmonics |
| `nozoki.crawl` | ×4 | 24 | fast limb thumps on leaves (charge) |
| `nozoki.creak` | ×2 | 12 | neck / joints cracking |
| `yobigoe.call` | ×4 | 96 | 「おーい」 in a man's voice, from far away, reverberant |
| `yobigoe.call_wrong` | ×2 | 96 | the call warped: pitch sinking, held far too long, glitching |
| `yobigoe.laugh` | ×2 | 32 | low laugh |
| `yobigoe.rush` | `yobigoe/rush` | 24 | whoosh + scream stinger as it appears beside you |
| `ushiro.step` | ×6 | 8 | footsteps in leaves matching yours |
| `ushiro.breath` | ×3 | 4 | breathing right at your ear, cold |
| `ushiro.whisper` | ×3 | 4 | whispered 「ふりかえって…」 |
| `ushiro.leave` | `ushiro/leave` | 6 | disappointed exhale fading away (「…つまらない」) |
| `ushiro.attack` | `ushiro/attack` | 8 | scream right in your ear |
| `koyanushi.sleep` | `koyanushi/sleep` 8 s (loop) | 12 | wet raspy sleeping breath / snore |
| `koyanushi.mumble` | ×3 | 12 | sleep-mumbling, unintelligible |
| `koyanushi.wake` | `koyanushi/wake` 2.5 s | 16 | bones cracking, sharp inhale, low groan |
| `koyanushi.scream` | `koyanushi/scream` | 40 | rage howl |
| `koyanushi.step` | ×4 | 16 | heavy fast bare feet on wooden floor |
| `oyama.step` | ×4 | 64 | huge heavy thump + wood creak |
| `oyama.groan` | ×3 | 96 | very low drawn-out groan, like a giant tree bending with a voice in it |
| `oyama.roar` | ×2 | 128 | massive layered roar with sub, rough |
| `oyama.near` | `oyama/near` 6 s (loop) | 32 | deep breathing / rumbling presence |
| `oyama.branch` | ×3 | 48 | branches breaking as it moves |
| `utsuru.hum` | `utsuru/hum` 6 s (loop) | 12 | faint radio-static whisper (only while in camera mode) |
| `utsuru.turn` | `utsuru/turn` | 12 | whispered gasp |

Player (M) and blocks
| event | files | description |
|---|---|---|
| `player.heartbeat` | ×1 (0.5 s) | one deep muffled lub-dub (played at the bpm) |
| `player.breath_fear` | ×3 | frightened trembling breath |
| `player.breath_run` | ×2 | panting |
| `player.gasp` | ×2 | sharp gasp |
| `player.fall` | `player/fall` 3 s | intro: tumbling down a slope, a heavy impact, ringing |
| `block.leaves_step` | ×6 | footstep on dry leaves and needles (forest_floor / fallen_leaves sound type) |

Items (M unless noted)
| event | files | description |
|---|---|---|
| `item.flashlight_on` / `item.flashlight_off` | ×1 each | switch click |
| `item.flashlight_flicker` | ×3 | electric buzz/stutter |
| `item.battery` | ×1 | battery cap off, batteries clack in, cap on |
| `item.bear_bell` | ×3 | brass bear bell ringing (bright, clapper) |
| `item.rope` | ×1 | rope uncoiling, knot pulled tight on iron |
| `item.map` | ×1 | paper map unfolding |
| `item.flare_ignite` | ×1 | strike + violent hiss |
| `item.flare_burn` | `item/flare_burn` 4 s loop | sputtering hiss |
| `item.omamori` | ×1 (S) | protective burst: shimmering bells + whoosh |
| `item.key` | ×1 | keys jingle |
| `item.unlock` | ×1 | padlock opening, chain falling |
| `item.offering` | ×1 | glass bottle set on wood + soft bell |
| `item.paper` | ×2 | paper rustle |
| `item.bag` | ×1 | zipper + rummaging |

Phone
| event | files | ch | description |
|---|---|---|---|
| `phone.tap` | ×2 | S | soft UI taps |
| `phone.unlock` | ×1 | S | unlock tick |
| `phone.vibrate` | ×1 (2 s) | M att 6 | buzz pattern in your pocket |
| `phone.notify` | ×1 | S | cheerful 3-note notification chime |
| `phone.notify_wrong` | ×1 | S | the same chime detuned, slowed, a note missing |
| `phone.signal` | ×1 (3 s) | S | a flurry of notification chimes as signal arrives |
| `phone.ringtone` | `phone/ringtone` 8 s loop | M att 24 | marimba-like ringtone (the public phone uses `phone.ring_old`) |
| `phone.ringtone_wrong` | `phone/ringtone_wrong` 8 s loop | S | the ringtone wrong: drifting pitch, reversed fragments |
| `phone.ring_old` | `phone/ring_old` 6 s loop | M att 32 | old public phone bell ringing |
| `phone.call_static` | `phone/call_static` 6 s loop | S | phone line static with garbled voice fragments |
| `phone.voice` | ×3 | S | garbled voice on the line (formant mumble, unintelligible) |
| `phone.hangup` | ×1 | S | busy beeps |
| `phone.shutter` | ×1 | S | camera shutter |
| `phone.low_battery` | ×1 | S | two descending beeps |
| `phone.dead` | ×1 | S | power-down tone |

Game (S)
| event | files | description |
|---|---|---|
| `game.jumpscare` | ×3 (1.5–2.5 s) | extremely loud: rough scream + distorted burst + sub drop |
| `game.stinger` | ×4 | dissonant cluster swell hit |
| `game.riser` | ×2 (4–6 s) | rising tension that cuts to silence |
| `game.capture` | ×1 (3 s) | being taken: violent rush, choir-like cluster, cut to silence |
| `game.wake` | ×1 (4 s) | waking up: heartbeat and ringing fading in |
| `game.checkpoint` | ×1 (3 s) | fragile chime + soft pad |
| `game.area` | ×1 (4 s) | area title: low boom + reverse swell |
| `game.fog_lift` | ×1 (5 s) | wind rushing away + shimmering bell |
| `game.loop` | ×1 (3 s) | looped back: reverse whoosh + dissonant swell + whisper |
| `game.car` | ×1 (6 s) | car door, engine struggling, then starting |
| `game.title_hit` | ×1 | title logo: deep boom |
| `game.kamikakushi` | ×1 (5 s) | 神隠し ending sting |

## 8. Shader contract ("SounanShaders", `src/main/shaderpack/shaders/`, Oculus / Iris / OptiFine)

* `dimension.properties`: `dimension.sounan_kaerazu=sounan:kaerazu` → folder `sounan_kaerazu/` holds stubs that
  `#define SOUNAN_MOUNTAIN` and include the shared programs. The root programs serve every other dimension.
* **Only inside `sounan:kaerazu`** the mod drives, every client tick: `rainStrength` = **fear** 0..1 (smoothed),
  `thunderStrength` = **danger** 0..1. No precipitation is drawn there (and the pack must not draw lightning/rain
  from these values). `worldTime` is always 18000. The vanilla Darkness effect (`darknessFactor`) is applied while
  being captured. `nightVision` > 0 while the phone camera is active (server applies Night Vision).
* `item.properties` (main hand → `heldItemId`, off hand → `heldItemId2`; **handle both hands**):
  `item.10001=sounan:flashlight_on` (main beam: warm white ~4300 K, bright hot spot, soft falloff ~25° half-angle,
  range ~30, volumetric in fog) · `item.10002=sounan:flashlight_low` (≈40 %, warmer, irregular stutter) ·
  `item.10003=sounan:phone_light` (wide ~40°, cool white, weak, range ~12) · `item.10004=sounan:phone_camera`
  (night-vision camcorder post effect + invisible IR "illuminator" cone that lights the scene in the camera image) ·
  `item.10005=sounan:flare_lit` (strong flickering red point light around the player, radius ~16, red-tinted fog).
  The beam originates slightly below and to the hand's side of the camera and points along the view direction.
* The flashlight must flicker when `thunderStrength` (danger) > ~0.35, and wobble slightly with fear (shaking hands).
* No block light from the flashlight is placed when a shader pack is active (the pack draws the beam). Without
  shaders the mod places light blocks at the beam's hit point.
* Entity eyes / headlamps use `RenderType.eyes` → `gbuffers_spidereyes`: make them **shine strongly inside the
  flashlight cone (eyeshine)** and stay faint outside. `utsurumono` renders translucent (entities_translucent).
* Blocks needing ids (`block.properties`): foliage sway (`sounan:cedar_leaves`, ferns, grass, dead bush), emissive
  (`sounan:tunnel_lamp:lit=true`, `sounan:hokora:offered=true`, campfires, candles, torches, lanterns), water, wet
  metal (`sounan:tin_roof*`, `sounan:guardrail`, `sounan:steel_lattice`, `sounan:iron_gate`).
* The look: cold blue-grey midnight, **moonlight shafts through gaps in the canopy (shadow-mapped volumetric light in
  ground fog)**, dense drifting height fog in the valleys, true darkness outside the beam (trunk silhouettes against
  slightly lighter fog — never pure black, never readable), the beam scattering in fog. **Fear**: heartbeat-pulsed
  vignette, tunnel vision, chromatic aberration, desaturation, faint pareidolia faces in the peripheral noise at high
  fear. **Danger**: the image "breathes" (subtle barrel distortion), edges warp, deep shadows shift red, heavier grain,
  double vision. **Camera**: green-grey night-vision camcorder, bright noise, bloom on lights, scanlines, lens
  distortion. **Tunnel** (no sky light): no moon, only the beam, dust motes in the beam.
* Performance: 60 fps on a mid GPU at 1080p by default; options in `shaders.properties` (+ `lang/ja_jp.lang`).
* The mod's own non-shader effects (fog, vignette, grain overlays) are reduced automatically while a pack is active.

## 9. Art direction

Late-autumn Japanese mountain at night: cedar bark reddish-brown going grey, dead needles brown, moss dark olive,
rock cold grey, fog blue-grey. Man-made things are rusted, bleached, mouldy, abandoned for decades (Showa-era signs,
tin roofs, a green public phone). Vivid colours only as alarms: the pink marking tape, the orange rescuer jacket,
the red flare, the jizo bibs, the bag. Pixel art that reads at 16×16 like vanilla, careful shading, a little noise.
Nothing clean or new. Monsters: real dread, not cartoon — wrong proportions, stillness, faces.
