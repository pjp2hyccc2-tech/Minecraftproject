# ひとりかくれんぼ ―HITORI KAKURENBO― : design contract (read fully before working)

Minecraft **Forge 1.20.1 (47.3.0), official Mojang mappings, Java 17**. Mod id **`hitori`**, base package
`com.hitori`. Player-facing text is Japanese first (`ja_jp`), English (`en_us`) second. Built on the systems of the
previous mods 霊撮 ―REISATSU― (branch `reisatsu`), 遭難 (`sounan`) and 冥婚 (`meikon`): `git show reisatsu:<path>`
shows any old file (package `com.reisatsu`, mod id `reisatsu`). Reuse freely — awareness/noise/hiding, the director,
dialogue/subtitles, the acoustics engine, the screenshot tour and self test, the shader pack — but nothing of the
restaurant may remain.

An **experiential first-person horror** about **ひとりかくれんぼ (one-person hide-and-seek)**, the Japanese internet
séance ritual: you perform it alone in your old apartment at 3 a.m., hide in the 押し入れ (closet) with salt water in
your mouth, and **watch your own apartment through fixed cameras (定点カメラ) you set up** while something that used
to be a stuffed toy searches for you. The user asked for the most effort on **the game systems, animation, graphics,
sound and the shader pack (影MOD)**. It must be genuinely frightening — silence, waiting, the thing you cannot see from
the closet, the camera feed that shows the bathtub empty — not gore.

## 1. Story and flow

You are a university student living alone in **コーポ白樺 203号室**, a 1970s two-storey wooden apartment
(木造アパート) on a quiet street. You read about ひとりかくれんぼ on an old forum thread and decide to try it — and to
record it with the action cameras you own, like the people who live-posted it. Night of 2 → 3 November, light rain.

The procedure (a printout on your desk: `howto` note) is the game. Each step is a real action in the world; the HUD
shows the current step as a checklist line, the printout shows the whole list.

**Phase 0 — preparation (from 2:20 a.m., the in-game clock runs ~4× fast until 3:00):**
1. 人形を用意する — take the stuffed toy (`plush`, a worn white rabbit with button eyes) from the western room's shelf.
2. 綿を抜く — cut it open with the scissors (sewing box, western room desk) at the low table (ちゃぶ台) in the
   tatami room: the stuffing spills out.
3. 米を詰める — fill it with rice (rice bag in the kitchen).
4. 爪を入れる — your nail clippings (nail clipper in the washroom; using it plays the clipping sound).
5. 赤い糸で縫う — sew it shut with the red thread (sewing box) and wind the rest around it → the item `ritual_doll`.
6. 風呂に水を張る — fill the bathtub (turn the bath tap; it fills over ~20 s).
7. 塩水を用意する — salt (kitchen) into a cup of water (cup at the sink) → `salt_water`, put it in the 押し入れ (or keep it).
8. 定点カメラを置く — place up to **4 cameras** (`fixed_camera`, from the bag in the western room) anywhere — the
   printout suggests: the bathtub, the hallway, the tatami room, the genkan. Open the camera app on the laptop
   (`laptop`) to check each feed.
9. テレビをつける — turn on the CRT TV in the tatami room (static: 砂嵐).
**Phase 1 — the ritual (at 3:00; the clock now runs in real time ×2):**
10. 名前をつける — name the doll (a small text field; default 「みーちゃん」) at the low table.
11. 「最初の鬼は〇〇（自分）だから」 ×3 — hold the doll and press the action key three times (your voice, subtitles).
12. 人形を風呂に入れる — put the doll into the full bathtub.
13. 家中の電気を消す — every light off (switches in each room, or the breaker in the genkan); the TV stays on.
14. 目を閉じて10数える — at the TV, eyes closed (screen black, your counting voice, something moves in the water).
15. 刃物を持って風呂場へ — take the kitchen knife (`knife`), go to the bathroom.
16. 「〇〇みつけた」 — and **stab the doll** (use the knife on it in the tub).
17. 「次は〇〇が鬼だから」 — leave it, run.
18. 塩水を口に含んで隠れる — take a mouthful of salt water (`salt_water`: the "mouth" gauge fills) and hide in the
    押し入れ, closing the fusuma.
**Phase 2 — hiding (3:15 → about 4:30):** the doll becomes **鬼 (oni)** and searches the apartment. You watch it on
the laptop's camera feeds from inside the closet; you peek through the gap of the fusuma; you hold your breath; you
listen. The apartment fills with 霊障 (§3.7): the TV changes channel, the intercom rings, your phone vibrates with a call
from your own number, knocking, wet footsteps, the faucet, the doll's giggling, a woman standing in a corner of one
camera feed, closer every time you look.
**Phase 3 — ending it (after 4:00, or earlier if you dare):** leave the closet with the salt water still in your
mouth, find the doll (it is no longer in the tub — use the cameras), **spit the salt water on it**, say
**「私の勝ち」 ×3**, and **burn it** (lighter, in the kitchen sink or the bathtub after draining). Then the lights go on
and it is morning.

**Endings**
* **終了 (good)**: ended properly. Morning light. You review the recordings the next afternoon on the laptop: the feeds
  play back… and in the last seconds of CAM 3, after the doll burned, the closet's fusuma slides open by itself.
  Text varies with how many rules you broke (0 = 「完璧な終わり」).
* **鬼 (bad)**: the doll found you (it opens the fusuma: jump scare) — 「つぎは、あなたがおに」. Black. The cameras keep
  recording an empty room… and a figure that is you, standing in the bathtub.
* **外へ (bad)**: you opened the front door during phases 1–3 (a rule): the outdoor corridor goes on forever; your phone's
  map shows you inside 203.
* **未完 (bad)**: dawn (5:00) comes and the ritual was never ended: the doll is gone. The last feed shows it on the
  balcony, looking in.

The rules (printout and HUD hint list; breaking one = penalty, see §3):
一、必ず一人で行うこと。
一、隠れている間は、塩水を口から出してはいけない。声を出してはいけない。
一、始めたら、家から出てはいけない。
一、始めたら、必ず終わらせること。（二時間以内）
一、終わったら、人形を燃やすこと。

## 2. The world

* Dimension **`hitori:shirakaba`** (dimension, dimension_type and biome share the id). Fixed time 18000 (the in-game
  clock on the HUD/laptop is the game's own, not the sky), overcast, **no vanilla rain** (rain is drawn by the shader
  on windows and by our sounds), no vanilla spawns, adventure mode.
* **The building** コーポ白樺: a two-storey wooden apartment, 4 units per floor, **X 0..43, Z 0..13**, outdoor corridor and
  stairs on the north side (Z −3..−1, stairs at the west end), balconies on the south side (Z 14..15). Ground floor feet
  level **y 65**, second floor feet level **y 70**. Only **203** (second floor, third from the west) is enterable; the
  other doors are locked (a TV murmurs behind 204; 202 has a 「入居者募集」 sign). Around it: a narrow street with a
  street lamp, a vending machine, a utility pole with wires, a bicycle shelter, neighbouring houses (dark, one lit
  window), a small park with a swing at the end of the street, rain on everything. Bounded by barriers.
* **Room 203** (2DK), outer walls X 22..33, Z 0..13; interior feet level y 70, **interior 3 high** (y 70..72, ceiling
  y 73) — low and close, on purpose.

```
      222222223333
      234567890123
z00   ##D#########
z01   #ggghh#t#bb#
z02   #ggghhdt#bb#
z03   ####hh###d##
z04   #kkkhh#wwww#
z05   #kkkhhdwwww#
z06   #kkkhh######
z07   #kkkhhdyyyy#
z08   ##ff#f#yyyy#
z09   #jjjjV#yyyy#
z10   #Ojjjj#yyyc#
z11   #Ojjjj#yyyc#
z12   #Ojjjj#yyyc#
z13   ##WWW###WW##
```
`D` front door (to the corridor at z −1) · `d` door · `f` fusuma · `W` sliding glass doors to the balcony ·
`V` the CRT TV on its low stand (faces west, toward the closet) · `O` **押し入れ**: 1 deep × 3 wide along the west wall
of the tatami room, a lower level the player hides in (2 high) and an upper level with futons, closed by two sliding
fusuma on its east face.

| key | name | box (x z, inclusive) | floor | acoustics | contents (minimum) |
|---|---|---|---|---|---|
| `genkan` | 玄関 | 23..25, 1..2 | `genkan_tile` (one step down, y 69) | `small_hard` | front door with peephole + chain, shoe cabinet, shoes, umbrella, **breaker panel** high on the wall, intercom handset |
| `hall` | 廊下 | 26..27, 1..7 | `old_flooring` | `corridor` | light switch panel, a calendar, a mirror |
| `toilet` | トイレ | 29, 1..2 | `bath_tile_floor` | `restroom` | old western toilet, small window, paper |
| `bath` | 浴室 | 31..32, 1..2 | `bath_tile_floor` | `restroom` | **deep square bathtub** (`bathtub`, 1×2, water level states) with lid, tap, shower, small frosted window, bucket and stool |
| `washroom` | 脱衣所 | 29..32, 4..5 | `vinyl_floor` | `small_hard` | washing machine, washbasin with mirror, **nail clipper**, towels, the bathroom's frosted glass door |
| `dk` | DK | 23..27, 4..7 | `vinyl_floor` | `small_hard` | sink, 2-burner gas stove, old fridge (humming), rice bag + rice cooker, **salt**, **cup**, **kitchen knife** on the rack, small dining table with 2 chairs, a microwave, a **lighter** in the drawer |
| `tatami` | 和室 | 23..27, 9..12 (minus the closet) | `tatami` | `small_soft` | **ちゃぶ台** (low table — the ritual table), **CRT TV** on a low stand, a hanging lamp with pull-cord, cushions (座布団), a small Buddhist-altar-like shelf (empty), curtains on the balcony doors, **押し入れ** |
| `western` | 洋室 | 29..32, 7..12 | `old_flooring` | `small_soft` | bed, **desk with the laptop + sewing box + the printout**, a shelf with the **plush**, the camera bag, a closet (`c`, hide-able but forbidden by the printout), a poster, a desk lamp |
| `closet` | 押し入れ | 23, 10..12 | `oshiire_floor` | `tiny_dead` | futons above, a cardboard box; the player hides on the lower level |
| `corridor` | 外廊下 | outside 203 | `concrete` | `outside` | the forbidden outside |

### Markers (`Layout.marker(name)` / `Layout.markers(name)`, feet positions, facing = where it looks)
`spawn` (in the western room by the desk, facing it) · `door_front`, `peephole` · `breaker` · `tv` · `table` (ちゃぶ台) ·
`bathtub` (list: the 2 blocks) · `bath_tap` · `washbasin` · `kitchen_sink` · `rice`, `salt`, `cup`, `knife`,
`lighter`, `scissors`, `thread` (sewing box), `nail_clipper`, `plush_shelf`, `camera_bag`, `laptop` (desk) ·
`switch_<room>` (light switches) · `lights_<room>` (list of the room's lamps) · `closet_hide` (list: the 3 hide
positions inside the 押し入れ, facing east) · `closet_fusuma` (list) · `closet_peek` (the eye position at the gap) ·
`western_closet` · `cam_suggest` (list: 4 suggested camera spots, facing what they should watch) · `doll_route` (list:
the doll's search waypoints through every room) · `doll_hides` (spots where the doll may lurk/stop: under the table, in the
washing machine's shadow, behind the bathroom door, on the balcony…) · `woman_spots` (list: corners where the apparition
stands, each facing the nearest camera suggestion) · `balcony` · `intercom` · `phone` (your phone starts on the desk) ·
`morning` (where the good ending's playback sits: the desk chair).

## 3. Mechanics

| | |
|---|---|
| **Fear 恐怖 0..1** | synced; rises in darkness, alone in the closet, at 霊障, when the doll is close or out of sight too long; falls with light (only before the ritual). Drives breathing/heartbeat, vignette, grain, chromatic aberration, the shader (§9). |
| **Danger 0..1** | how close the doll is to finding you (its suspicion of your hiding place; 1 while it is at the fusuma). |
| **Mouth (塩水) 0..1** | salt water in your mouth: you cannot speak; your breathing is muffled (half noise). Holding your breath (key **V**) drains it slowly; **gasping or speaking empties it** — the doll's protection (§3.5) is gone. Refill only from the cup. |

### 3.1 The fixed cameras 定点カメラ (`fixed_camera` item → `camera` entity)
* Right-click a block face to place a camera (small action cam on a mini tripod, red REC LED); up to 4, numbered in
  placement order (CAM 1–4, the HUD names them after their room: 「CAM1 浴室」). Sneak + right-click picks one up
  (before 3:00 only).
* The **laptop** (`laptop`, or the phone `phone` with a smaller screen) opens the **camera app**: the client view
  switches to the selected camera (`Minecraft.setCameraEntity` on the camera entity; the player's body stays where it is),
  with a CCTV overlay (REC ●, CAM n + room, timestamp 「2026/11/03 03:21:07」, battery, a slow scanline, noise, IR
  night-vision monochrome when the room is dark). Keys: 1–4 / A–D cycle cameras, **ESC / right-click closes**. The sound
  listener follows the camera (you hear what its microphone hears) — and a muffled mix of what is near your body.
* Feeds glitch: noise bursts when the doll or the woman is in view, a frame freeze, **frame skipping when the doll moves
  (stop-motion)**, a blackout when something covers the lens, the timestamp jumping.
* Observed by a camera the doll moves stiffly in jerks; **directly seen by the player's own eyes it freezes** (and turns
  its head toward you if you look away and back).
* The cameras keep recording; the good ending plays back their footage (the game logs key events per camera with
  timestamps and replays them as a slideshow of real captured frames — capture a frame from the active feed every few
  seconds while watching and at key events, like REISATSU's photo capture).

### 3.2 Hiding (`closet` + the western closet + under the bed? no — only closed spaces count)
* Right-click the fusuma from outside: enter the 押し入れ (lower level; crouched camera height, the fusuma closes). Inside:
  look around a dark cramped space (futon edge above, a box, the gap of light).
* **Peek** (hold **right mouse** facing the fusuma): the fusuma opens a crack (4 px); view through the slit (overlay) — a
  slice of the tatami room with the TV's static light. Peeking makes a soft sliding noise (2) and **the doll sees light
  move** if it faces the gap.
* Leaving: right-click the fusuma (noisy: 6).
* A closed hiding place makes you safe from sight; the doll finds you only by **sound** (breathing, gasping, phone,
  moving, peeking) or by searching the closet when its suspicion is high (§3.4).

### 3.3 Noise (`NoiseSystem.emit(level, pos, radius, cause)`)
walking on tatami 3 / flooring 5 / tiles 6 / sprinting 12 / closet fusuma open-close 6 / peek 2 / door 8 / dropping an
item 6 / speaking (a ritual line) 10 / breathing at fear ≥ 0.8 with an empty mouth 4 (with salt water 2) / gasp 8 /
phone vibrating (if not silenced) 7 / the laptop app (screen light leaks through the fusuma gap; 0 noise but light).

### 3.4 The doll (鬼, entity `oni_doll`)
* Spawned into the bathtub as the ritual item `ritual_doll` turns into an entity at step 12; after step 17 it **wakes**:
  at first it stays in the tub (you can see it on CAM, floating face down) for ~1–3 min, then **it is not there any more**.
* Searches room by room along `doll_route`, pausing at `doll_hides`, investigating noises (moves toward the source),
  checking hiding places (the western closet, behind doors, the bathtub, the 押し入れ last unless it heard you). Its
  suspicion of the 押し入れ rises with your noises nearby and with light from the gap (peek, laptop) when it is in the
  tatami room; at **1.0 it opens the fusuma**: jump scare → 鬼 ending. Unless the salt water is still in your mouth:
  then it stops at the fusuma, stands there for a long time (you hear it breathing / rice rustling), and leaves. With the
  mouth empty it does not stop.
* Movement: crawling/dragging itself fast when unobserved, stiff stop-motion steps when on camera, frozen when you see it
  directly (§3.1). It carries **the knife** after it leaves the bath (it took it). Wet: drips, leaves damp marks.
* Phase 3: once you leave the closet it **stops searching and waits** somewhere (one of `doll_hides`, sitting, facing the
  wall); you must find it via the cameras and do the ending ritual. It moves only while you are not looking; if you take
  too long (> 6 min after leaving) it starts hunting you openly (approaching whenever unseen; touching you = 鬼 ending).
* Ending ritual: face it within 2 blocks → **spit (action key)**: the salt water hits it (it shrieks, falls limp);
  then press the action key 3× for 「私の勝ち」; then carry it (`ritual_doll` again) to the sink/drained tub and use the
  lighter: it burns (fire particles, smoke, a scream in the static) → good ending.

### 3.5 Rules and penalties
Speaking before 3:00 is fine. During phases 1–3: opening the front door = **外へ** ending; the salt water spat or
gasped out while hiding = no protection (the doll will open the fusuma if it reaches it); hiding anywhere but the
押し入れ = the doll's suspicion rises 2× faster; using the lights = it sees you from afar; after 5:00 = **未完**.

### 3.6 Phone and laptop
* `phone` (smartphone): the camera app (small view), a flashlight (weak), the time, a muted "silent" switch (the phone
  vibrates audibly unless silenced), messages from a friend 「ほんとにやるの？」「やめとけって」… and at 3:33 a call from
  **your own number**.
* `laptop` (on the desk, carry it into the closet): the camera app (large view), the printout as a PDF.

### 3.7 Director (霊障) — paced by phase, fear and time; quiet stretches matter
Before 3:00: almost nothing (a creak, the fridge, rain). Phase 1: the TV static swells when you count; water sloshing.
Phase 2: the TV changes channel by itself (a dark room… your own room from above), the intercom chimes (the corridor
camera view shows nobody… then someone very close to the peephole), the phone vibrates (your number), knocking on the
closet from the outside, wet small footsteps, the bath tap runs, the hanging lamp swings, a pull-cord clicks, the
washing machine starts, things fall in the kitchen, the doll's giggle from the wrong room, your name whispered at the
fusuma, the woman in a camera feed's corner (only on camera; each time you return to that feed she is closer to the
lens; once she is in the closet feed — if you placed one there — behind you).
Phase 3: silence except the TV and the rain.

### 3.8 HUD
Top left: the current step 「次：米を詰める」 / 「隠れる」 / 「人形を探す」. Top right: the clock 「03:21」. Bottom
right: the mouth gauge (a small cup icon) and breath. The step checklist on the printout (key **J**). Subtitles bottom
centre. In the camera app: the CCTV overlay only.

### 3.9 Controls
right-click: use/interact (cut, fill, sew, place a camera, open/enter the closet, peek (hold), stab, light) · left
click: — · **V** hold breath · **R** spit / speak the ritual line (context) · **C** open the camera app (laptop/phone
in hand) · **1–4** select camera · **J** printout/checklist · sneak: quiet.

## 4. (reserved)

## 5. Entities

All implement `com.hitori.entity.Animated` (pose, poseTime, glitch — same contract as REISATSU).

| id | name | size | look | poses |
|---|---|---|---|---|
| `oni_doll` | 鬼（人形） | 0.45×0.6 | the worn white plush rabbit, soaked grey, button eyes (one hanging by a thread), rice spilling from the knife wound sewn with **red thread wound round and round its body**, ears drooping, holding the kitchen knife (bigger than its arm) | 0 FLOAT (face down in water, limp), 1 SIT (slumped against something, head lolling), 2 CRAWL (dragging itself fast on its arms, legs trailing), 3 WALK (stiff marionette steps, swaying, knife dragging), 4 STAND (upright, perfectly still, head tilted 90°), 5 TURN (head rotates 180° to look at you), 6 PEEK (around a door frame, only head and one ear), 7 LUNGE (jump scare: springs at your face, knife raised), 8 LIMP (after the salt water: collapses), 9 BURN (twitching in the fire) |
| `woman` | 女 | 0.6×1.8 | camera-only apparition: long wet black hair hiding the face, a stained white nightdress, grey bare feet, too-long fingers, standing in a corner facing the wall… or the lens | 0 STAND (facing the wall), 1 FACE (turned to the lens, hair parted on one eye), 2 CLOSE (leaning toward the lens), 3 GONE |
| `camera` | 定点カメラ | 0.25×0.3 | a small black action camera on a mini tripod, red REC LED (eyes layer) | 0 IDLE, 1 FALLEN (knocked over by something) |
| `plush` (item model + block `plush_doll`) | the plush before the ritual | — | the same rabbit, clean, white, sitting | — |

The doll is **never fully visible in the dark** to the naked eye except within the lamp/phone light or the TV glow; on
the IR camera feed it shows as a pale blob with two glinting button eyes (eyes layer). Movement rules (§3.4) matter for
the animation: stop-motion frame skipping when seen by a camera (the model holds poses, then jumps), complete stillness
when seen by the player, frantic crawling when unseen.

## 6. (reserved)

## 7. Registry ids

### Blocks (`assets/hitori/textures/block/<texture>.png`, 16×16; prop models face north at rest; same model/state
naming rules as REISATSU DESIGN §7.1: boolean `p` → `<id>` / `<id>_<p>`; int → `<id>_<n>`; `half`/`part` first)

Surfaces (plain cubes; world makes their models): `tatami` (6-mat pattern edges, axis), `tatami_worn`, `old_flooring`
(dark varnished planks, worn), `vinyl_floor` (beige cushion floor, kitchen), `genkan_tile` (small dark tiles),
`bath_tile_floor`, `bath_tile_wall` (pale blue small tiles, old grout), `plaster_wall` (old beige textured wall),
`plaster_wall_stained`, `wallpaper_old` (faded floral), `wood_wainscot`, `ceiling_board` (old wooden ceiling boards),
`ceiling_board_stained`, `oshiire_floor` (bare plywood), `apartment_siding` (grey corrugated siding of the building),
`apartment_door_metal` (outer face of the steel doors), `corridor_concrete`, `concrete`, `asphalt_wet`, `block_wall`
(concrete block garden wall), `roof_slate` (+ `_stairs`, `_slab`).
Doors (vanilla-style, world makes models; textures `<id>_bottom` / `_top`): `front_door` (steel door with peephole and
mail slot, 「203」), `interior_door` (cheap wood-grain door), `toilet_door` (with a small frosted window),
`bath_door` (frosted folding glass door, translucent), `fusuma` (sliding paper door: a block with `open` 0..2 and
`half` — art model), `oshiire_fusuma` (the closet's, with `open` 0 shut, 1 crack, 2 open), `balcony_door` (aluminium
sliding glass door, translucent, `open`).
Props (art models): `chabudai` (low round table), `zabuton`, `crt_tv` (`on`, `channel` 0 static / 1 dark room / 2 face —
emissive screen, animated textures), `tv_stand`, `hanging_lamp` (`lit`, pull-cord; swings: block entity or state
`swing`), `fluorescent_ring` (`lit`), `pull_switch`, `light_switch` (`on`), `curtain` (`closed`), `futon_folded`,
`cardboard_box`, `altar_shelf`, `bathtub` (`water` 0 empty / 1 half / 2 full, `lid`), `bath_tap` (`running`),
`shower`, `bath_stool`, `bucket`, `washing_machine` (`running`), `washbasin` (`running`), `mirror_small`, `toilet_seat`,
`kitchen_sink` (`running`), `gas_stove` (`lit`), `fridge_old` (`open`), `rice_bag`, `rice_cooker`, `salt_shaker`,
`cup_glass`, `knife_rack` (`has_knife`), `microwave`, `dining_table`, `dining_chair`, `drawer` (`open`),
`desk`, `desk_chair`, `laptop_block` (`open`, `on`), `sewing_box` (`open`), `printout` (paper on the desk),
`bed`, `shelf`, `plush_doll` (`present`), `camera_bag`, `poster`, `desk_lamp` (`lit`), `shoe_cabinet`, `shoes`,
`umbrella`, `breaker_panel` (`open`), `intercom` (`ringing`), `calendar`, `wall_clock`, `laundry_pole` (balcony),
`ac_outdoor_unit`, `vending_machine_small` (lit), `street_lamp_old` (`lit`), `utility_pole`, `mailboxes` (row of
apartment mailboxes), `bicycle`, `stairs_rail`, `nameplate` (`unit` 0..7: 101–204), `vacancy_sign`, `swing_set`,
`puddle`, `rain_window` (window glass with rain streaks, translucent).

### Items (16×16, `assets/hitori/textures/item/<id>.png`)
`plush` (the clean toy), `plush_open` (cut open, stuffing out), `plush_rice` (filled), `ritual_doll` (sewn, red
thread), `scissors`, `rice_scoop`, `red_thread`, `nail_clipper`, `knife`, `salt`, `cup_water`, `salt_water`,
`fixed_camera`, `laptop`, `phone`, `lighter`, `printout`. 3D held models (`forge:separate_transforms`): `knife`,
`laptop` (open, screen glowing), `phone`, `salt_water`, `ritual_doll`, `lighter`, `fixed_camera`.

### Entities — §5. Textures `assets/hitori/textures/entity/<id>.png` + `<id>_eyes.png`.

### GUI (`assets/hitori/textures/gui/`)
`title_logo` 512×256 (「ひとりかくれんぼ」 in a scrawled, wet, red-thread-like hand + "HITORI KAKURENBO") ·
`cctv` (camera-app overlay frame: REC, corners, battery, transparent centre) · `laptop_frame` (the laptop screen bezel
and taskbar of an old OS, the camera app window) · `phone_frame` · `fusuma_slit` (the peek overlay: dark with a narrow
vertical gap) · `closet_dark` (inside-the-closet vignette with the futon edge) · `printout` 256×320 (the forum printout
with the procedure and the rules) · `icons` (mouth/cup, breath, camera numbers, REC) · `jumpscare_doll`,
`jumpscare_woman` 256² · `name_field` (the naming text field) · `vignette`, `grain` (copies).

### Particles
`rice_grain`, `stuffing`, `water_drip`, `salt_spray`, `ember`, `smoke`, `tv_static_mote`.

## 8. Sounds (`assets/hitori/sounds/<path>.ogg`, event id `hitori:<event>`)

OGG Vorbis 44.1 kHz, **mono for positional**, stereo for beds/music/UI; `att` = attenuation. Variants `name_1..n`.
**Real dynamic range**: the apartment at 3 a.m. is *silent* — fridge hum, the clock, rain on the window, the CRT's
whine and static — and then violence. SOUNAN/REISATSU psychoacoustic palette (binaural theta, sub with harmonics,
roughness, nonlinear voice chaos, Shepard–Risset, clusters, dry close whispers vs reverbs, false risers, silence). New:
**the claustrophobic closet** (your own muffled breathing and heartbeat loud and close, the world outside filtered), the
CRT TV's static as the constant bed, and the **camera microphone** sound (feeds sound thin, compressed, with mic hiss).
All melodies original. Voice lines (formant synthesis + processing; subtitles carry the words): the player's ritual
lines 「最初の鬼は、わたしだから」, 「みーちゃん、みつけた」, 「次は、みーちゃんが鬼だから」, 「わたしの勝ち」, counting
「いち、に、さん…じゅう」; the doll 「みつけた」, giggles, 「どこ？」, 「つぎは、あなたがおに」; the woman's whisper
「……そこにいるの」; your name whispered.

Beds (S, loops): `amb.apartment` (60 s: rain on the window and gutter, fridge cycling, far trains/cars, house creaks),
`amb.closet` (40 s: inside the closet: muffled outside, fabric, your space), `amb.bath` (30 s: drips into a full tub,
tiled resonance), `amb.outside` (60 s: the corridor/street in rain), `amb.tv_static` (M loop, att 16: the CRT static +
line whine — the most important sound in the game), `amb.silence` (20 s).
Positional loops (M): `fixture.fridge` (8), `fixture.clock` (8), `fixture.tap_bath` (12, filling), `fixture.tap`
(10), `fixture.washing_machine` (14), `fixture.rain_window` (10), `doll.drag_loop` (12, dragging on tatami/flooring),
`camera.mic_hiss` (S, the feed's microphone noise).
Music (S): `music.dread` (64 s), `music.tension` (32 s), `music.danger` (16 s), `music.chase` (16 s),
`music.title` (48 s: a detuned toy music box playing an original かくれんぼ-like tune over rain), `music.prep` (40 s:
near nothing, a low pad, the clock), `music.ending_good` (50 s, no loop: morning, birds, a sour note at the end),
`music.ending_bad` (30 s, no loop: the music box winding down), `music.playback` (40 s: the laptop playback, VHS hum).
One-shots (M unless noted; att): `ritual.cut` (scissors through fabric, 8), `ritual.stuffing` (pulling stuffing, 8),
`ritual.rice` (rice poured into fabric, 8), `ritual.clip` (nail clipper ×3 clicks, 6), `ritual.sew` (needle and
thread pulled, 6), `ritual.thread_wind` (8), `ritual.splash` (doll into the bath, 12), `ritual.stab` (knife into a
wet plush with rice, 12), `ritual.salt_pour` (6), `ritual.spit` (S, spitting), `ritual.burn_ignite` (lighter + whoosh,
10), `ritual.burn_loop` (crackling fabric and rice, 10), `voice.first_oni` ×1 (S), `voice.mitsuketa` (S),
`voice.next_oni` (S), `voice.watashi_kachi` (S), `voice.count` (S, 10 s), `door.front_open` / `door.front_close`
(steel apartment door, chain, 16), `door.interior_open` / `door.interior_close` (8), `door.fusuma` ×3 (paper door sliding, 8),
`door.fusuma_crack` (2 px slide, 4), `door.bath_fold` (folding glass door, 8), `door.locked` (6), `fixture.switch` ×2
(light switch click, 6), `fixture.pull_cord` (6), `fixture.breaker_off` / `fixture.breaker_on` (12),
`fixture.tv_on` (CRT thunk + rising whine, 12), `fixture.tv_channel` (channel click + burst, 12), `fixture.tv_voice`
×3 (garbled voices from the TV, 12), `fixture.intercom` (apartment intercom chime "pin-pon", original, 24),
`fixture.knock_door` ×3 (on the steel door, 24), `fixture.knock_closet` ×3 (on the fusuma, 8), `fixture.thing_fall`
×3 (16), `fixture.dish_break` (24), `fixture.lamp_swing` (creak, 6), `fixture.water_slosh` ×3 (in the tub, 10),
`fixture.washing_start` (12), `fixture.creak` ×4 (wood house creaks, 16), `fixture.footsteps_ceiling` ×2 (from above,
24), `phone.vibrate` (M, 6), `phone.ring_own` (S loop, a ringtone wrong), `phone.notify` (S), `phone.tap` (S),
`camera.place` (6), `camera.app_open` / `camera.app_close` (S), `camera.switch` (S, a click and static burst),
`camera.glitch` ×3 (S, feed glitch noise), `camera.fall` (8), `laptop.open` (S), `laptop.fan` (S loop, quiet),
`item.lighter` (6), `item.paper` ×2 (S), `item.cup` (6), `player.heartbeat`, `player.breath_fear` ×3,
`player.breath_hold` (S), `player.gasp` ×2, `player.breath_mouth` ×2 (breathing with water in the mouth, gurgling),
`player.swallow` (S), `player.step_tatami` ×4, `player.step_floor` ×4, `player.step_tile` ×4, `player.step_wet` ×4,
`doll.step` ×4 (soft wet plush thumps, 10), `doll.crawl` ×3 (fast dragging, 12), `doll.drip` ×3 (6),
`doll.rice` ×3 (rice rustling inside it, 6), `doll.giggle` ×4 (16), `doll.voice_mitsuketa` (12),
`doll.voice_doko` ×2 (12), `doll.voice_tsugi` (S: 「つぎは、あなたがおに」), `doll.knife_drag` ×2 (12),
`doll.knife_stab` (into the fusuma, 12), `doll.shriek` (salt water, 24), `doll.breath` ×2 (at the fusuma, 6),
`woman.whisper` ×3 (6), `woman.breath` (4), `whisper.name` ×2 (S), `game.jumpscare` ×2 (S), `game.stinger` ×4 (S),
`game.riser` ×2 (S), `game.step_done` (S, soft tick when a ritual step completes), `game.three_am` (S: the clock
striking 3, a deep bell-like tone), `game.title_hit` (S), `game.morning` (S).

## 9. Shader contract ("HitoriShaders", `src/main/shaderpack/shaders/`)

* `dimension.properties`: `dimension.hitori_shirakaba=hitori:shirakaba` → folder `hitori_shirakaba/` (define
  `HITORI_SHIRAKABA`). Root programs serve every other dimension.
* Channels (mod-driven, only in `hitori:shirakaba`): `rainStrength` = fear, `thunderStrength` = danger,
  **`nightVision` > 0 = the camera app is open (CCTV/IR look)**, `blindness` > 0 = **eyes closed** while counting (and
  the jump scare cut), `darknessFactor` = being caught. `heldItemId`: `item.10001=hitori:phone` (weak cool light when
  its flashlight is on — the mod swaps the item id to `hitori:phone_light` for that: `item.10002`), `item.10003=hitori:lighter`
  (small warm flame light), `item.10004=hitori:laptop` (screen glow on the face/closet when open in the closet).
* `entity.properties`: `entity.30001=hitori:oni_doll`, `30002 hitori:woman` (camera-only: the mod renders her only to
  cameras; under the IR look she glows faintly), `30003 hitori:camera` (its REC LED).
* `block.properties`: **the CRT TV** (`crt_tv:on=true`) is the main light source after 3:00: a flickering cool-blue
  light whose intensity follows a noise signal (static) and lights the tatami room, washing over walls, with flicker
  visible through the fusuma gap; `hanging_lamp:lit=true`, `fluorescent_ring:lit=true` (cold), `desk_lamp`,
  `street_lamp_old` (sodium through curtains), `vending_machine_small`, `laptop_block:on=true`, the camera LEDs, the
  breaker panel's indicator; **water** (`bathtub` water states: still dark water with reflections, ripples), wet
  asphalt and puddles outside (reflections), **rain on windows** (`rain_window`, `balcony_door`: streaks and drops running
  down, refraction of the street light), tatami (soft matte), old varnished flooring (soft reflections), bath tiles
  (wet gloss).
* **The look**: a dark Showa apartment at 3 a.m.: everything near black except the TV's flicker, the street lamp leaking
  through curtains and frosted glass, the laptop screen; deep shadows; dust in the phone light. **CCTV (camera app)**: IR
  night-vision monochrome (cool grey-green), heavy noise, slight fisheye, scanline, interlace, the doll's button eyes
  and the woman glinting, chromatic tearing on glitches. **Eyes closed**: black with faint phosphenes and the TV's
  flicker through the eyelids (reddish). **Fear/danger**: vignette, tunnel vision, chromatic aberration, desaturation,
  the image breathing. **Closet**: when the player is in the 押し入れ (fear + darkness), the slit of light is the only
  bright thing.
* Performance: 60 fps on a mid GPU; quality profiles; **every loop bounded** (REISATSU's pack once froze a client with
  an unbounded loop — never again); must compile under Iris/Oculus and OptiFine and pass `tools/shader/lint.py`.

## 10. Acoustics (音MOD)
Reuse REISATSU's `client/sound/Acoustics*` (EFX room reverb + occlusion) with presets for the rooms above; add
**`closet`**: when the player is inside the 押し入れ everything outside it is low-passed hard (−18 dB HF) and the
player's own sounds are dry and close; **camera listening**: while the camera app is open the listener is the camera,
and the feed audio gets a band-limited "mic" filter (high-pass 200 Hz, low-pass 6 kHz) + `camera.mic_hiss`.

## 11. Art direction
A 1970s Japanese wooden apartment, lived in by a student: yellowed tatami with frayed edges, faded fusuma paper with a
pale pattern, varnished dark flooring, beige textured walls with stains, a cramped kitchen with an old fridge and
2-burner stove, pale blue tiles in a tiny bathroom with a deep square tub, laundry and clutter, a CRT TV on a low stand,
cheap posters, a laptop with stickers. At night only the TV and the street lamp. Outside: grey corrugated siding,
rusty outdoor stairs, a mailbox row, wet asphalt, one street lamp, rain. Pixel art that reads at 16×16 like vanilla,
careful shading, signs of life and neglect. **The doll**: a cheap white plush rabbit made horrible by what you did to
it — wet, grey, rice bulging, red thread biting into it, one button eye hanging, the knife. Stillness and stop-motion.

## 12. Code ownership and contracts (same model as REISATSU)

| owner | paths |
|---|---|
| **world** | `world/**` (dimension, generator, the building + 203 + street, `Layout`, `Fixtures`, `Circuit`), `block/**` + block entities, `client/render/block/**`, `client/WorldClientSetup.java`, `client/WorldAmbience.java`, `registry/ModBlocks|ModBlockEntities|ModDimensions`, `data/**`, `tools/gen_assets.py`, `debug/WorldTour.java`, `debug/WorldCheck.java` |
| **game** | everything else in `src/main/java` (ritual flow, items, cameras + camera app, hiding/peek, the doll's and the woman's AI, director, phone/laptop, HUD, network, commands, config, `client/**` except models/world/acoustics), `registry/ModItems|ModEntities|ModParticles|ModCreativeTab|ModKeys`, `debug/**` except the world's, lang + `tools/gen_lang.py`, `README.md` |
| **models** (art) | `client/model/**` (`OniDollModel`, `WomanModel`, `CameraModel`), `textures/entity/**`, `gui/jumpscare_*`, `tools/art/ent_*` |
| **art** | all other textures, prop block models + 3D item models, particle JSONs, GUI (except jumpscares), `tools/art/**` except `ent_*` |
| **sound** | `assets/hitori/sounds/**`, `sounds.json`, `registry/ModSounds.java` (generated from §8 by `tools/gen_sounds_java.py`: field = event id upper-cased, `.`→`_`), `tools/audio/**`, `client/sound/Acoustics*.java` |
| **shader** | `src/main/shaderpack/**`, `tools/shader/**` |

Contracts in the base commit (compiling stubs): `world/Layout` (`DIMENSION`, `FEET_Y` = 70 for 203, markers, rooms),
`world/Fixtures` (lights/switches/breaker, TV on/channel, taps/bath water level, intercom, fridge/washing machine, doors
incl. fusuma open states, knife rack, plush shelf, items' world presence, `resetAll`, `ensureBuilt`),
`world/FixtureEvents` (interactions with keys, noises, hide/unhide/peek, the front door opened, notes),
`entity/Animated`, `registry/ModSounds`.
Testing: builds only on GitHub Actions (`bash tools/ci.sh "msg"` on your own branch `hk-*`); the `build` job runs the
self test (`debug/SelfTest`, `hitori.selftest`) and the vanilla client tour (`client/AutoShots`, `hitori.shots`), the
`shaders` job the same tour under Oculus + the pack. Results on `ci-<branch>` / `ci-<branch>-shaders`.
