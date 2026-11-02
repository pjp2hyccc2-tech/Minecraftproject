# 冥婚 ―MEIKON― : design contract (read fully before working)

Minecraft **Forge 1.20.1 (47.3.0), official Mojang mappings, Java 17**. Mod id **`meikon`**, base package
`com.meikon`. Player-facing text is Japanese first (`ja_jp`), English (`en_us`) second.

A strategy-type ("攻略型") Japanese folk-horror game. Goal: genuinely frightening — dread, darkness, sound,
unpredictability — not gore for its own sake.

## 1. Story

Taisho era, a rich mountain family. Their daughter **小夜 (Sayo)** died in her white bridal kimono
(白無垢) on the night before her wedding after the groom ran away. The grieving mother commissioned a
**ムカサリ絵馬** (ghost-marriage votive painting) so Sayo could marry in the afterlife — and had a *living*
man painted as the groom. Ever since, Sayo takes a new groom every generation.

The player finds a **赤い封筒 (red envelope)** — a ghost-marriage invitation with the player's own name
written as the groom. Opening it pulls them into **隠り世 (Kakuriyo)**, an eternal-night copy of Sayo's
mansion, where the wedding is being prepared. To escape they must gather the **七つの鈴 (seven bells)** of
the wedding procession and ring them at the **縁切りの鳥居 (bond-severing torii)** in the courtyard, while
the bride hunts them.

Endings: **good** (sever the bond, wake up at dawn; the envelope crumbles to ash) and **bad** (drink from
the 三三九度 sake cups in the wedding hall → married forever).

## 2. Game flow / phases (driven by bells collected)

| Phase | Bells | Title card | What changes |
|---|---|---|---|
| 序 Prologue | – | 「招待」 | wake up in the safe room, find the lantern + first notes |
| 壱 | 0–1 | 「鈴の音」 | bride wanders slowly, humming かごめかごめ |
| 弐 | 2–3 | 「人形」 | Ichimatsu dolls wake up (weeping-angel), first shadows |
| 参 | 4–5 | 「影」 | andon lamps start dying, more shadows, bride faster |
| 肆 | 6 | 「祝言」 | gagaku wedding music, wedding hall opens, bride relentless |
| 終 | 7 | 「縁切り」 | torii activates in the courtyard; ritual = hold the bell 5 s at the gate while she rushes |

Being caught = jump scare + "fake death": fade to black, respawn in the safe room, bells kept, death count +1.

## 3. Mechanics

* **Fear 恐怖度 0–100** (server value, synced): rises in darkness, near/seeing the bride, on scares; falls in
  light and in the safe room. Drives heartbeat, vignette, grain, desaturation, shake, hallucinations; at 100
  the player's panicked breathing becomes audible to the bride.
* **Stamina**: sprinting drains it; empty = no sprint + loud exhausted breathing. Sprinting is noisy.
* **Lantern 提灯**: the only portable light (oil drains). Lit lantern = the bride sees you from far away;
  unlit = shadows (影法師) creep in and fear rises.
* **Noise**: sprint, doors, drawers, thrown marbles, bells jingling when you sprint with bells → the bride
  investigates.
* **Hiding**: 押入れ closets have slatted doors you can peek through. Hide inside, close the door, hold
  SNEAK to hold your breath when she is near. If she saw you enter, she opens the door.
* **Items**: 提灯 lantern, 菜種油 oil, 鈴 bell, 清め塩 salt (repels her once), 護符 ofuda (saves you once when
  caught), おはじき marbles (throw = noise decoy), 蔵の鍵 key, 人形の首 doll head, 燐寸 matches, 手記 notes,
  お守り omamori (less fear), 赤い封筒 red envelope (starts the game), 灰 ash (after the good ending).
* **Bell locations (7)**: 3 on bell pedestals in random rooms · 1 in the locked 蔵 storehouse (key) · 1 in
  the 仏間 (light the three candles in the order written in a note) · 1 in the doll room (put the head back
  on the headless doll) · 1 in the courtyard well.
* **AI director**: paces scares — creaks, knocks, footsteps above, whispers left/right, door slams, music box,
  crying, lamps flickering out, wet footprints appearing, bloody handprints on shoji, the scroll's painting
  turning to face you, phantom bride at the end of a corridor, a face right behind you when you turn around.

## 4. Registry ids

### Dimension
* `meikon:kakuriyo` — dimension, dimension_type and biome share this id. Fixed time 18000 (midnight), sky
  light on (moon lights the courtyard), custom effects `meikon:kakuriyo` (dark crimson fog, red moon).
  The mansion is generated around (0, 64, 0); floor y = 64.

### Entities
| id | what |
|---|---|
| `hanayome` | the bride 花嫁: ~2.4 blocks tall (hitbox 0.7×1.95), white 白無垢 + 綿帽子 hood, long black hair, too-long arms |
| `ichimatsu` | 市松人形 doll, 0.9 blocks: black bob hair, red kimono, porcelain face |
| `kageboshi` | 影法師 shadow: black translucent silhouette, two pale eyes |
| `maboroshi` | phantom bride (hallucination; renders like `hanayome`) |
| `ohajiki` | thrown marble projectile |

### Blocks (`assets/meikon/textures/block/<name>.png`, 16×16 unless noted)
| id | textures | notes |
|---|---|---|
| `tatami` | `tatami_top`, `tatami_side` | floor, axis property (weave runs along the axis) |
| `old_planks` | `old_planks` | dark aged floorboards |
| `engawa` | `engawa` | weathered veranda boards (lighter grey-brown) |
| `rotten_planks` | `rotten_planks` | broken, mouldy |
| `dark_beam` | `dark_beam_side`, `dark_beam_top` | pillar, axis |
| `plaster_wall` | `plaster_wall` | off-white aged plaster |
| `plaster_wall_stained` | `plaster_wall_stained` | water stains, mould, a faint smear |
| `wainscot` | `wainscot` | dark wooden lower-wall panelling |
| `kura_wall` | `kura_wall` | namako-kabe: white plaster with dark diagonal tile grid |
| `shoji` | `shoji` | white washi + thin wooden lattice |
| `shoji_torn` | `shoji_torn` | ragged holes = fully transparent pixels (cutout) |
| `shoji_bloody` | `shoji_bloody` | bloody handprints / smears |
| `fusuma` | `fusuma` | painted sliding panel, faded pine/crane on muted gold, damaged |
| `ranma` | `ranma` | carved transom lattice (cutout) |
| `fusuma_door` | `fusuma_door_bottom`, `fusuma_door_top`, item `fusuma_door` | DoorBlock |
| `shoji_door` | `shoji_door_bottom`, `shoji_door_top`, item `shoji_door` | DoorBlock |
| `oshiire_door` | `oshiire_door_bottom`, `oshiire_door_top`, item `oshiire_door` | closet door: dark frame + horizontal slats with transparent slits to peek through (cutout) |
| `kura_door` | `kura_door_bottom`, `kura_door_top`, item `kura_door` | heavy black storehouse door, iron studs, lock |
| `vermilion` | `vermilion`, `vermilion_top` | torii pillar lacquer, axis |
| `black_lacquer` | `black_lacquer` | torii top beam |
| `torii_gate` | `torii_gate` (animated strip 16×256 + mcmeta), `torii_gate_active` (animated) | exit portal, translucent |
| `shimenawa` | `shimenawa` | twisted straw rope + white zigzag shide paper, transparent bg (hanging plane) |
| `blood_stain` | `blood_stain` | transparent decal (carpet height) |
| `hair_strands` | `hair_strands` | transparent decal: long black hair |
| `footprints` | `footprints` | transparent decal: wet dark bare footprints |
| `futon` | `futon_top`, `futon_side` | old futon with stains (carpet height) |
| `kakejiku` | `kakejiku`, `kakejiku_face` | wall scroll (thin wall model); FACE state = the painted woman now stares at you |
| `butsudan` | `butsudan_front`, `butsudan_side`, `butsudan_top` | black-lacquer Buddhist altar, gold inside |
| `candle_stand` | `candle_wax`, `candle_gold`, `candle_silver`, `candle_black`, `candle_flame` | 燭台; COLOR (gold/silver/black) + LIT |
| `andon` | `andon_side`, `andon_side_off`, `andon_top` | paper floor lamp; LIT |
| `tansu` | `tansu_front`, `tansu_side`, `tansu_top` | chest of drawers (searchable) |
| `kamado` | `kamado_front`, `kamado_side`, `kamado_top` | clay stove with soot |
| `well` | `well_side`, `well_top` | stone well rim / dark water |
| `suzu_dai` | `suzu_dai`, `suzu_bell` | small lacquered bell pedestal with red cushion; HAS_BELL |
| `salt_pile` | `salt_pile` | 盛り塩 cone |
| `hina_dan` | `hina_dan` | red felt doll steps |
| `headless_doll` | `doll_kimono`, `doll_face` | doll display; HEAD state |
| `sakazuki_table` | `sakazuki_table` | red lacquer tray with three sake cups |
| `old_clock` | `old_clock_front`, `old_clock_side` | pendulum wall clock |
| `stone_lantern` | `stone_lantern` | 石灯籠 granite |

### Items (`assets/meikon/textures/item/<name>.png`, 16×16)
`chochin`, `chochin_lit`, `lamp_oil`, `suzu`, `shio`, `ofuda`, `ohajiki`, `kura_key`, `doll_head`,
`matches`, `note`, `red_envelope`, `omamori`, `ash`.

### Paintings (`assets/meikon/textures/painting/`)
`mukasari` 32×32 (ghost-marriage votive: bride + groom whose face is scratched out), `sayo` 16×32 (the
bride in white, face hidden by hair), `family` 32×16 (family photo, faces scratched), `mansion` 32×16.

### GUI (`assets/meikon/textures/gui/`)
`vignette` 256² (black, alpha 0 centre → 255 corners), `grain` 256² (noise), `jumpscare_bride` 256²,
`jumpscare_doll` 256², `paper` 256² (aged washi), `title_logo` 512×256 (「冥婚」 + "MEIKON"),
`hud_bell` / `hud_bell_empty` 16², `hands` 256² (transparent, pale hands reaching in from the edges).

### Entity textures (`assets/meikon/textures/entity/`)
`hanayome.png`, `ichimatsu.png`, `kageboshi.png` (made together with the Java models).

### Environment
`assets/meikon/textures/environment/red_moon.png` 64×64.

## 5. Sounds (`assets/meikon/sounds/<path>.ogg`, event id = `meikon:<event>`)

OGG Vorbis 44.1 kHz. **Mono** for everything positional (marked M), stereo (S) only for UI/music.
Variants are listed as `name_1..n`. `sounds.json` lives at `assets/meikon/sounds.json`.

| event | files | ch | description |
|---|---|---|---|
| `ambient.drone` | `ambient/drone` (48 s loop) | S | deep dark drone, slow beating partials, faint metal shimmer |
| `ambient.drone_tense` | `ambient/drone_tense` (32 s loop) | S | dissonant cluster, slow pulse |
| `ambient.wind` | `ambient/wind` (30 s loop) | S | wind through gaps and paper screens, whistles |
| `ambient.koto` | `ambient/koto_1..4` (8–14 s) | S | sparse detuned koto plucks, In scale, long reverb |
| `ambient.creak` | `ambient/creak_1..6` | M | old wood creaks |
| `ambient.footsteps_above` | `ambient/footsteps_above_1..3` (4–6 s) | M | muffled slow footsteps on the floor above |
| `ambient.knock` | `ambient/knock_1..4` | M | knocks on wood, some slow and deliberate |
| `ambient.whisper` | `ambient/whisper_1..6` (1.5–3.5 s) | M | unintelligible close whispers |
| `ambient.door_slam` | `ambient/door_slam_1..3` | M | sliding door slammed far away |
| `ambient.music_box` | `ambient/music_box` (~20 s) | M | music box かごめかごめ, detuned, spring winding down |
| `ambient.crying` | `ambient/crying_1..2` (4–6 s) | M | distant woman sobbing |
| `ambient.clock_chime` | `ambient/clock_chime` (~8 s) | M | old clock striking, deep |
| `ambient.temple_bell` | `ambient/temple_bell` (~10 s) | M | distant bonshō |
| `ambient.drip` | `ambient/drip_1..4` | M | water drips |
| `ambient.scratch` | `ambient/scratch_1..3` | M | nails scratching wood / paper behind a wall |
| `ambient.thud` | `ambient/thud_1..3` | M | heavy muffled thud |
| `ambient.tinnitus` | `ambient/tinnitus` (4 s) | M | ear ringing |
| `bride.hum` | `bride/hum` (16–20 s loop) | M | eerie female humming of かごめかごめ |
| `bride.step` | `bride/step_1..6` | M | soft bare/tabi step on old wood |
| `bride.screech` | `bride/screech_1..2` (2–3 s) | M | horrifying scream when she spots you |
| `bride.breath` | `bride/breath_1..3` | M | slow raspy breathing |
| `bride.rustle` | `bride/rustle_1..3` | M | silk kimono rustle |
| `bride.laugh` | `bride/laugh_1..2` | M | low breathy chuckle |
| `bride.door` | `bride/door` | M | fusuma violently thrown open |
| `doll.move` | `doll/move_1..5` | M | short wooden/ceramic joint click "カタ" |
| `doll.head_turn` | `doll/head_turn_1..2` | M | slow wooden creak |
| `doll.attack` | `doll/attack` | M | shrill stinger + clatter |
| `shadow.whoosh` | `shadow/whoosh_1..3` | M | dark breathy whoosh |
| `shadow.drone` | `shadow/drone` (6 s loop) | M | low ominous hum |
| `player.heartbeat` | `player/heartbeat` | M | one deep muffled lub-dub (~0.5 s) |
| `player.breath_fear` | `player/breath_fear_1..3` | M | frightened trembling breathing |
| `player.gasp` | `player/gasp_1..2` | M | sharp gasp |
| `player.breath_exhausted` | `player/breath_exhausted_1..2` | M | panting |
| `game.bell_chime` | `game/bell_chime_1..3` | M | small suzu bell jingle, shimmering decay |
| `game.bell_get` | `game/bell_get` (4 s) | S | jingle + low sacred swell + reverse shimmer |
| `game.jumpscare` | `game/jumpscare_1..2` (1.5–2.5 s) | S | extremely loud: scream + distorted burst + sub hit |
| `game.stinger` | `game/stinger_1..3` | S | dissonant string-like cluster swell |
| `game.chase` | `game/chase` (16 s loop) | S | pulsing taiko-like drums, tremolo dissonance, scrapes |
| `game.phase` | `game/phase` (6 s) | S | deep boom + reversed swell + bell |
| `game.death` | `game/death` (5 s) | S | descending rumble, heartbeat stopping, ringing |
| `game.fusuma` | `game/fusuma_1..3` | M | sliding paper door |
| `game.closet` | `game/closet_1..2` | M | closet door creak/slide |
| `game.lantern_on` | `game/lantern_on` | M | match strike + flame |
| `game.lantern_off` | `game/lantern_off` | M | blow-out puff |
| `game.paper` | `game/paper_1..2` | M | paper rustle |
| `game.ofuda_burn` | `game/ofuda_burn` | M | paper flaring + mystical shimmer |
| `game.salt` | `game/salt` | M | thrown salt hiss |
| `game.marble` | `game/marble_1..3` | M | glass marble clicks/bounces on wood |
| `game.drawer` | `game/drawer_1..2` | M | wooden drawer sliding |
| `game.unlock` | `game/unlock` | M | heavy old lock |
| `game.candle` | `game/candle` | M | tiny ignition |
| `game.puzzle_fail` | `game/puzzle_fail` | M | hissing whisper burst + snuff |
| `game.well` | `game/well_1..2` | M | rope/pulley creak + bucket |
| `game.gate_open` | `game/gate_open` (6 s) | S | resonant drone + bells + rushing wind |
| `game.ritual` | `game/ritual` (~6 s) | S | bells ringing faster and faster |
| `music.title` | `music/title` (~40 s) | S | low drone, sparse koto, wind chimes |
| `music.gagaku` | `music/gagaku` (~40 s loop) | S | wrong, slowed gagaku wedding music (shō clusters, hichiriki-like reed) |
| `music.ending` | `music/ending` (~60 s) | S | melancholic music box / koto |
| `music.bad_ending` | `music/bad_ending` (~30 s) | S | distorted wedding music |

## 6. Shader contract (Oculus / Iris / OptiFine pack "MeikonShaders", `src/main/shaderpack/shaders/`)

* `dimension.properties`: `dimension.meikon_kakuriyo=meikon:kakuriyo` → folder `meikon_kakuriyo/` holds stubs that
  `#define MEIKON_KAKURIYO` and include the shared programs; the root programs are the pack for every other
  dimension (still eerie: desaturated, foggy nights).
* **Only inside `meikon:kakuriyo`** the mod drives these vanilla values every client tick:
  `rainStrength` = **fear** 0..1 (smoothed), `thunderStrength` = **danger** 0..1 (bride chasing / very near).
  The biome has no precipitation, so no rain is drawn. `wetness` follows `rainStrength`.
  The vanilla Darkness effect (`darknessFactor`, `darknessLightFactor`) is applied while she is grabbing you.
* `worldTime` is always 18000 there. The moon is at the zenith; render it blood red there.
* Light: the moving lantern light is a real block light (lightmap), so block light = lantern/andon/candles.
* `item.properties`: `item.10001=meikon:chochin_lit`.
* Emissive / special blocks for `block.properties`: `meikon:andon:lit=true`, `meikon:candle_stand:lit=true`,
  `meikon:torii_gate`, `meikon:suzu_dai`, vanilla torches/lanterns/candles/campfires/glowstone/etc.
* The mod's own non-shader effects (fog, vignette, grain overlays) are reduced automatically while a shader
  pack is in use, so the pack owns fog, vignette, grain and colour grading.

## 7. Art direction

Faded, cold, desaturated Japanese house at night: aged wood (dark brown-grey), off-white plaster and paper
(dirty cream, never pure white), muted gold, lacquer black, vermilion only for sacred things, dark
brownish-red for blood (never bright cartoon red). Pixel art that reads at 16×16 like vanilla, with careful
shading and a little noise. Nothing should look clean or new.
