# 霊撮 ―REISATSU― : design contract (read fully before working)

Minecraft **Forge 1.20.1 (47.3.0), official Mojang mappings, Java 17**. Mod id **`reisatsu`**, base package
`com.reisatsu`. Player-facing text is Japanese first (`ja_jp`), English (`en_us`) second. Built on the systems of the
previous mods 遭難 ―SOUNAN― (branch `sounan`) and 冥婚 ―MEIKON― (branch `meikon`): `git show sounan:<path>` shows any
old file (paths there use `sounan` instead of `reisatsu`). Reuse freely, but nothing of the mountain may remain.

A **stealth photography horror** set in a Japanese family restaurant (ファミレス) at night. The user asked for the most
effort on: **the systems, the textures, the spirits' looks and animations, the restaurant's interior and exterior
detail, the shader pack (影MOD) and the sound (音MOD)**. It must be genuinely frightening — stillness, darkness, faces,
sound, the feeling of being watched — not gore.

## 1. Story

Present day, the night of 2 → 3 November. **ファミリーレストラン「ひだまり」** stands on a dark stretch of the national
highway (国道), right at a curve where people keep dying (魔のカーブ). Since the restaurant stopped running 24 hours, the
closing crew keeps reporting things: **the automatic door they locked opens by itself with its chime, the taps run with
nobody there, the menus on the tables are found scattered over the floor**, call buttons ring at empty tables, plates
break in the empty kitchen. The owner called in a medium, **八雲 トキ (Yakumo Toki)**, an old woman in a dark kimono.

She cannot go in: the spirits would hide from her. She needs their **faces and names**. She hands the player
(an investigator she hires) her **霊写機 (reisha-ki)**, an old instant camera that photographs what the eye cannot
see: *if a spirit's face is in the photo, its name rises in red on the white margin.* With the names she can send them
on (送る). **The spirits must not notice the camera** — a spirit that notices you will not show in a photo, and it will
try to take you with it.

The dead of HIDAMARI (their names appear on the photos; the medium tells their story when you hand her the photo):

| spirit | id | true name (photo margin) | story (told by the medium / found in notes) |
|---|---|---|---|
| 来店者 the visitor | `visitor` | 三浦 健司 (24) | 2009: rode his motorbike to meet his girlfriend here at 2:00 a.m.; died at the curve in front. Every night at two the door opens for him; he sits at **table 7**, presses the call button and waits. |
| 給仕 the waitress | `waitress` | 小野寺 千夏 (19) | the night-shift waitress who ran out onto the road to help him that night and was hit by a truck. She still works the night shift, serving water to customers nobody else can see. |
| 料理長 the chef | `chef` | 権藤 剛 (52) | head chef, died in a grease fire in the kitchen in 1996. Still chops at his board. His face is burnt black; embers glow in the cracks. |
| かくれんぼの子 the child | `child` | 春日 ゆうか (5) | went missing during hide-and-seek in the restaurant in 2001; never found. She still plays — and she is very good at hiding. |
| 鏡の女 the woman in the mirror | `mirror_woman` | 白石 玲子 (31) | a regular who collapsed in the women's restroom in 2015 and was found in the morning with the tap still running. She is on *the other side* of the mirror. |
| 店長 the manager | `manager` | 森田 浩一 (47) | ran the 24-hour store alone at night for years; died of overwork at his desk in 2019 (過労死). After that the store stopped opening at night. He still watches the security monitors — and he still closes the store. |

**Flow**
1. **Intro** (≈ 40 s, skippable after the first time): black, lines of text one by one (date, place, the report), then
   the parking lot: rain has just stopped, the pole sign flickers, the medium stands by her car. Dialogue (subtitles
   with her portrait), she hands over: 霊写機 (10 film), 懐中電灯, お札 ×3, 店の鍵. Objective appears.
2. **Investigation** (free order): photograph the five spirits — visitor, waitress, chef, child, mirror woman.
   Photos can be handed to the medium at any time (she reads each name and tells its story).
3. **Closing time** (after the fifth valid photo): the store PA chimes 「本日の営業は、終了いたしました」, every light in the
   store comes on by itself, the store music plays warped, the call display shows 「閉店」. The manager wakes and
   hunts with the security cameras. Photograph him.
4. **Back to the medium** with all six names → **除霊 (exorcism)**, an in-world sequence: she walks in to table 7,
   lays the six photos on the table between candles and chants; one by one each spirit stands at the table, its photo
   burns, it fades into light; the lights flicker on; the automatic door opens and closes one last time with its chime
   (the visitor leaving); 「……ありがとうございました」 in the waitress's voice. Dawn breaks. Ending text, credits.
5. **Endings**: **送り (good)** — all six sent; text varies with how many お札 you used (0 used = 「完璧な夜」).
   **相席 (bad)** — caught with no お札 left: you sit at table 7, the waitress puts a glass of water in front of you:
   「いらっしゃいませ。……お一人様ですね」. 「その夜から、ひだまりの七番テーブルの呼び出しボタンは、二度鳴るようになった。」

The medium's rules (撮影の心得; shown in the intro dialogue, in the HUD hint list and on her note):
一、霊に気付かれてはならない。気付かれた霊は、写真に写らない。
一、霊は光と音に敏い。灯りは控え、足音を殺すこと。
一、ファインダー越しにしか見えないものがいる。
一、顔が写らなければ、名は浮かばない。
一、気付かれたら、逃げなさい。隠れなさい。お札が身代わりになるのは三度まで。

## 2. The world

* Dimension **`reisatsu:hidamari`** (dimension, dimension_type and biome share the id). Fixed time **18000**, sky light
  on, overcast night (no moon visible), no precipitation (the rain has *just* stopped: wet ground, puddles, dripping —
  drawn by blocks, sounds and the shader), no vanilla mob spawns, adventure mode. Fog: dark blue-grey, mid distance.
* **Feet level `Y = 65`** everywhere (the restaurant floor and the parking lot surface are blocks at y = 64).
  Interior air y = 65..68 (4 high), suspended ceiling blocks at y = 69, roof above.
* **Building**: X 0..40, Z 0..30 (exterior walls on the border). Entrance on the south side; the parking lot is south
  (Z 31..48), the national road further south (Z ≈ 50..58), the curve to the east (X ≈ 60..90) with guardrails, chevron
  signs, a curve mirror and **flowers left at the guardrail (献花)**. North of the building: back yard (dumpsters, gas
  cylinders, A/C units, the kitchen exhaust, the staff bicycle). West: the staff path to the back door.
  Beyond: dark harvested rice paddies with puddles, utility poles with wires, a wooded hill, a few far houses with one lit
  window, a blinking yellow traffic light. The playable area ends at hedges, fences and guardrails with `barrier`
  blocks; the road vanishes into the fog both ways.

### Ground floor plan (one character = one block column; `#` exterior wall, `=` interior wall)

```
      00000000001111111111222222222233333333334
      01234567890123456789012345678901234567890
z00   #########################################
z01   #ssssss#oooooo#ffff=kkkkkkkkkkkkkkkkkkkk#
z02   #ssssss#oooooo#ffff=kkkkkkkkkkkkkkkkkkkk#
z03   #ssssss#oooooo#ffff=kkkkkkkkkkkkkkkkkkkk#
z04   #ssssss#oooooo#==F==kkkkkkkkkkkkkkkkkkkk#
z05   #ssssss#oooooo#kkkkkkkkkkkkkkkkkkkkkkkkk#
z06   #ssssss#oooooo#kkkkkkkkkkkkkkkkkkkkkkkkk#
z07   ###d###E#wwd###kkkkkkkkkkkkkkkkkkkkkkkkk#
z08   Bccccccccccccc#kkkkkkkkkkkkkkkkkkkkkkkkk#
z09   #cccccccccccccdkkkkkkkkkkkkkkkkkkkkkkkkk#
z10   ###d##################PPPPP##SS##########
z11   #ttttt#xxxxxxx#.........................#
z12   #ttttt#xxxxxxx#.........................W
z13   #ttttt#xxxxxxx#.........................W
z14   #ttttt#xxxxxxx#.........................W
z15   #ttttt#xxxxxxx#.........................W
z16   #########MMM###.........................W
z17   #mmmmm#rrrrrrr#.........................W
z18   #mmmmm#rrrrrrr#.........................W
z19   #mmmmm#rrrrrrr#.........................W
z20   #mmmmm#rrrrrrr#.........................W
z21   #mmmmm#rrrrrrr#.........................W
z22   ###d######d####.........................W
z23   #aaaaaaaaaaaaaa.........................W
z24   #aaaaaaaaaaaaaa.........................W
z25   #aaaaaaaaaaaaaa.........................W
z26   ###AA####aaaaaa.........................W
z27   #vvvvvvv#aaaaaa.........................W
z28   #vvvvvvv#aaaaaa.........................W
z29   #vvvvvvv#aaaaaa.........................W
z30   ###AA####WWWWWWWWWWWWWWWWWWWWWWWWWWWWWWW#
```
`A` automatic door panel (outer pair at z 30, inner pair at z 26) · `W` shop window · `w` interior window · `d` door ·
`B` back door · `F` walk-in fridge door · `S` kitchen swing doors · `P` pass window (デシャップ, counter-height opening) ·
`M` the mirror · `E` breaker panel (on the wall, used from the corridor).

### Rooms (`Layout.rooms()`; acoustics preset → §10; circuit → §4.9)

| key | name | box (inclusive, x z) | floor | acoustics | circuit | contents (minimum) |
|---|---|---|---|---|---|---|
| `vestibule` | 風除室 | 1..7, 27..29 | `vestibule_floor`, `entrance_mat` | `small_hard` | dining | umbrella stand, notice board (lost-child flyer 2001), capsule toy machines, the key switch of the auto door outside at x 5, z 31 |
| `waiting` | 待合・レジ | 1..14, 23..25 + 9..14, 26..29 | `dining_floor` | `medium` | dining | waiting bench, magazine rack, **register counter** (register, service bell, reservation book, business phone, cake case), call display high on the wall, kids' corner at x 9..14 z 27..29 by the window (mat, toy shelf, stuffed bear, low fence, **the pillar** where the child counts) |
| `dining` | 客席 | 15..39, 11..29 | `dining_floor` | `dining` | dining | south window booths (tables 1–7, **table 7 = booth with its table at x 34, z 28..29 against the south window**), east window booths (tables 8–11), centre four-seat tables (12–17) separated by a long planter divider and pillars, drink bar on the north wall (x 15..20), server station by the swing doors, booth partitions (frosted glass, 2 high) so a crouching player can hide |
| `kitchen` | 厨房 | 15..39, 1..9 (minus fridge) | `kitchen_floor` | `kitchen` | kitchen | cooking line on the north wall (gas ranges, fryer, griddle, hood), **prep island with the chef's cutting board facing north at x 25, z 5**, pass shelf with heat lamp and pass bell on the pass window, dish pit with big sink and dishwasher in the east (x 33..39), pot shelves, trash |
| `fridge` | 冷蔵室 | 15..18, 1..3 | `kitchen_floor` | `tiny_dead` | kitchen | wire shelves, boxes, frost; a film pack; knocking from inside |
| `corridor` | 通路 | 1..13, 8..9 | `kitchen_floor` | `corridor` | back | breaker panel, mop bucket, cardboard boxes; back door (x 0, z 8) |
| `staff` | 休憩室 | 1..6, 1..6 | `dining_floor` (worn) | `small_soft` | back | **lockers (hiding)**, time recorder with cards, table + folding chairs, CRT TV, bulletin board (news clipping 2009), handover notebook |
| `office` | 事務所 | 8..13, 1..6 | `dining_floor` (worn) | `small_soft` | back | **manager's desk facing west with the bank of security monitors on the west wall (x 8)**; PC, filing cabinet, safe, shift schedule; the interior window (x 9..10, z 7) shows his left profile from the corridor |
| `storage` | 倉庫 | 1..5, 11..15 | `kitchen_floor` | `small_hard` | back | shelves with stock, cleaning cart, spare chairs, the old high chair |
| `mens` | 男子トイレ | 1..5, 17..21 | `restroom_floor` | `restroom` | restroom | sink + mirror (normal), urinals, one stall, hand dryer |
| `womens` | 女子トイレ | 7..13, 17..21 | `restroom_floor` | `restroom` | restroom | **sink counter along the north wall under the mirror (x 9..11, z 16)**, three stalls along the south (one always 使用中, knocking), hand dryer, paper towels |
| `cavity` | 鏡の向こう | 7..13, 11..15 | `restroom_floor_dark` | `restroom` | none | sealed. A dark, decayed, mirrored copy of the women's restroom seen through the mirror (stained tiles, a dead sink, wet floor). The mirror woman stands here |

* The exterior must be as detailed as the interior: brick base + beige walls, big shop windows with interior blinds,
  an entrance canopy with the logo, a terracotta hip roof with a lit roof sign 「ひだまり」, a ramp with handrails,
  flower planters, an A-frame menu board, two vending machines (lit, humming), a trash station, the bicycle rack,
  the **pole sign** by the road (≈ 12 high, a lit box sign with the sun logo, flickering), parking lines and wheel stops,
  puddles, sodium street lamps, utility poles, the curve with the 献花, the curve mirror, the blinking traffic light.
  The medium's car (a dark old kei car, entity `medium_car`) is parked at the marker `medium_car`.

### Markers (`Layout.marker(name)` / `Layout.markers(name)`; positions are feet positions, facing = where it looks)

`spawn` (player start in the lot) · `medium` · `medium_car` · `wake` (where you wake after being caught: beside the
car) · `door_outer`, `door_inner` (centre of each auto-door pair) · `door_switch` · `table_<n>` for n = 1..17 (table
centre) · `button_<n>` (call button block of table n) · `seat_7` (the visitor's seat on the west bench of table 7,
facing east) · `call_display` · `register` · `phone` · `visitor_path` (list: outside the door → vestibule → waiting →
aisle → `seat_7`) · `visitor_spawn` (on the lot near the road) · `waitress_route` (list: patrol loop through the dining
aisles, ≥ 10 points) · `waitress_station` · `waitress_bow` (facing the inner door) · `child_count` (beside the pillar,
facing it) · `child_hides` (list, ≥ 6: under the booth table of table 3, behind the toy shelf, behind the register
counter, in the drink-bar alcove, behind the planter divider, in the storage room, …) · `chef_board`, `chef_stove`,
`chef_pass` (facing the pass window), `pass_bell` · `mirror_woman` (in the cavity, facing south toward the mirror) ·
`mirror` (list of the mirror blocks) · `sink_w` (list, women's taps), `sink_m`, `sink_kitchen` · `manager_desk` (seated,
facing the monitors) · `manager_route` (list: office → corridor → kitchen → dining → waiting → back) · `breaker` ·
`cameras` (list: every security camera block, facing = its view) · `monitors` · `lockers` (list, hiding spots) ·
`stall_occupied` · `tv` · `hand_dryer_w`, `hand_dryer_m` · `drink_bar` · `fryer` · `gas_range` · `dishwasher` ·
`fridge_door` · `stuffed_bear` · `menu_board` · `curve_mirror` · `flowers` · `film_<n>` (n = 1..3 film pack spots:
the register drawer, a staff locker, the walk-in fridge) · `key_office` (in the staff room) · `note_<id>` (§4.11) ·
`exorcism_*` (the medium's standing spot at table 7, the six spirit spots around it, the player's viewing spot).

## 3. Mechanics overview

| | |
|---|---|
| **Fear 恐怖 0..1** | server value, synced. Rises in darkness, near spirits (even unseen), at poltergeist events, when a spirit's awareness of you rises; falls in light, outside near the medium. Drives heartbeat/breathing sound, vignette, grain, chromatic aberration, the shader (§9). |
| **Danger 0..1** | max over spirits of their awareness of you (1 while hunted). Drives the danger music layer, the flashlight flicker and the shader. |
| **霊障 level 0..3** | +1 each time you are caught. The director gets busier, spirits notice 15 % faster per level. |

### 3.1 The camera 霊写機 (`reisha_camera` / `reisha_camera_finder`)
* Right-click: raise / lower the camera. Raising swaps the stack to `reisha_camera_finder` (film count kept in NBT
  `film`) and the server applies Night Vision (ambient, hidden) — the shader keys on both. The hand is hidden.
* **Finder view** (HUD + shader): an optical finder frame with parallax marks, a centre focus circle, `のこり 8枚`,
  a **霊感計 (spirit meter)**: a needle that swings toward the strength of spirit presence in the frame, crackling.
  Zoom with the scroll wheel (FOV 70 → 30). **In the finder every spirit is fully visible** (§5 says what the naked eye
  sees). The focus circle turns red when a photographable face is inside it.
* **Shutter**: left click in the finder (attack key, intercepted). Costs 1 film; no film = dry click. The flash always
  fires: the photographed frame is lit by it (shader: `blindness` channel, §9; no shader: the photo is brightened in
  post), and **every other spirit within 14 blocks with line of sight to you gets awareness +0.6**.
* **The photo**: the client grabs the rendered frame (world only, no HUD/hand) at the flash, crops it to the instant
  film window (4:3), shrinks to 320×240 and processes it like old instant film (warm shift, faded blacks, flash
  falloff toward the edges, vignette, grain, slight blur, light leak). It is saved to
  `<game dir>/reisatsu/photos/<world>/<photo id>.png` and shown as an **ejecting instant photo** sliding out of the
  camera that develops over ~3 s. If the shot is valid, the spirit's **name writes itself in red on the margin**
  (art: `textures/gui/photo_name_<spirit>.png`). Invalid shots of a spirit that had noticed you come out as a smeared
  blur with a face-like shape. Empty shots occasionally carry a faint face/hand (心霊写真 overlay, `textures/gui/photo_ghost_<n>.png`).
* **Validity (server decides; the client sends the shot with its zoom)** — a spirit is captured when all hold:
  1. its head is inside the central 80 % of the finder frame (projected with the shot's FOV),
  2. distance ≤ 12 blocks (≤ 20 at full zoom),
  3. line of sight from the camera to its head (glass, the mirror and the pass window do not block),
  4. **its face shows**: the angle between its facing and the direction to the camera ≤ 110°,
  5. **it has not noticed you** (awareness < 1 and not hunting / not in its 30 s after-hunt),
  6. it is in a photographable state (§5),
  7. it is not already captured.
  Quality ★1..★3 from centring, distance and how frontal the face is.
* A captured spirit is **bound into the photo**: a scream sucked backwards, the spirit is pulled into the lens and
  vanishes (it no longer acts). The photo becomes the item `evidence_photo` (NBT: `photo`, `spirit`, `stars`, `time`)
  and is also kept in the album.

### 3.2 Album 霊撮帳 (key **J**)
A notebook GUI: six silhouette slots (filled with the photo and the red name once captured), all shots taken (empty and
failed ones too), the notes found, the medium's rules. Clicking a photo shows it large.

### 3.3 Awareness (stealth)
Every spirit keeps an awareness 0..1 of the player:
* **Sight**: in its view cone (half-angle per spirit, typically 60°), line of sight (`ClipContext.Block.VISUAL`, glass is
  see-through; booth partitions, counters, shelves and doors block), within range. Range: 6 blocks if you stand in the
  dark, 14 if lit (block light ≥ 8 at your eyes, or your flashlight is on and points within 60° of the spirit), ×0.6
  while sneaking, ×1.15 per 霊障 level. Rate ∝ closeness × centrality (dead centre of its view ≈ 1.2 s to notice at half
  range).
* **Hearing**: noise events (§3.4) add awareness by distance and turn the spirit toward the sound; most spirits walk
  over to investigate the position (INVESTIGATE state) before going back to their routine.
* Awareness decays slowly when nothing is perceived (−0.1/s after 2 s).
* Feedback: the HUD **eye** near the crosshair (closed → half-open → open, red when ≥ 0.8), whispers rising, the
  spirit turning its head toward you; the danger music layer.
* **Noticed** (awareness reaches 1): the spirit's NOTICE animation + `spirit.notice` sting, then **HUNT**: it comes for
  you (speed per spirit, mostly a little below your sprint). Touch = **caught**. Breaking line of sight and staying
  out of it for 5 s (a locker, a closed stall, another room) loses it; it goes back to its routine but stays wary
  (photos of it fail for 30 s). Some spirits react differently (§5).

### 3.4 Noise (`NoiseSystem.emit(level, pos, radius, cause)`)
| cause | radius |
|---|---|
| sprinting footsteps | 14 | walking footsteps on tile / kitchen / glass shards / scattered menus | 6 / 7 / 10 / 8 |
| walking on carpet / mat | 3 | sneaking | 0 (glass shards 3) |
| jump landing | 8 | kitchen swing doors (passing) | 9 |
| door open/close (not sneaking / sneaking) | 8 / 3 | locker | 6 |
| knocking a chair (walking into it) | 10 | the camera shutter + flash | 10 |
| flashlight click | 3 | gasp after holding breath > 4 s | 9 |
| heavy breathing at fear ≥ 0.85 | 4 | pressing a call button, ringing the bell, the phone | 16 |

### 3.5 Breath (key **V**, hold)
Hold your breath: no breathing sound, the heartbeat gets loud, a thin gauge drains over 10 s (tighter at high fear).
Releasing after more than 4 s = gasp (noise 9). Some spirits react to breathing nearby (the mirror woman, the child).

### 3.6 Hiding
* **Lockers** (staff room): right-click to step in / out. View through the slits (overlay). A spirit that did not see
  you go in cannot find you. The door rattles if you move the camera too fast (noise 4) — keep still.
* **Restroom stalls**: close the door; the manager and the waitress check stalls only if they heard you.
* Crouching behind booth partitions, counters, the planter divider and the register counter breaks line of sight.

### 3.7 Flashlight 懐中電灯 (`flashlight` / `flashlight_on`)
Right-click toggles. No battery limit (this game's pressure is attention, not resources). Its beam makes you visible
far away (§3.3) and flickers when danger is high. Usable in the off hand while the camera is raised (the beam then
lights the finder image — and makes you conspicuous).

### 3.8 Caught, お札 and the medium
Caught = the spirit's jump scare (full-screen face, 0.6 s, `game.jumpscare`) → black → `game.caught` → you wake at
`wake` beside the car; the medium has burnt an お札 (`item.ofuda_burn`, 「……間に合ってよかった」). お札 −1, 霊障 +1, the
spirit returns to its routine. **Caught with no お札 left = 相席 ending.** The HUD shows the お札 count.

### 3.9 Power: the breaker panel 分電盤 (`breaker_panel`)
Right-click opens a small GUI with switches (big clunks): `dining` (dining + waiting + vestibule lights), `kitchen`,
`restroom`, `back` (corridor/staff/office/storage), `sign` (pole sign, roof sign, lot lamps), `cameras` (security
cameras + monitors), `door` (automatic doors). Start: dining OFF (only the green exit signs, the drink bar's standby
glow, the cake case, a few downlights over the register on a night circuit), kitchen OFF (pilot lights, the heat lamp
off), restroom ON (flickering), back ON (one corridor tube flickering), sign ON, cameras ON, door ON. Lights on make
everything easier to see — for you and for them. Poltergeist events can trip a circuit (`fixture.breaker_off`).

### 3.10 Keys and doors
* **店の鍵** (`shop_key`): turns the key switch at `door_switch` → the automatic doors work (they open for anything
  that comes near — you, a spirit, or nothing). Also opens the back door.
* **事務所の鍵** (`office_key`): in the staff room (marker `key_office`), opens the office.
* Staff doors open normally; the walk-in fridge door is heavy and loud unless you sneak.

### 3.11 Notes (found by right-clicking paper blocks; read in a paper GUI, kept in the album)
`report` 業務報告書 (given at the start: the phenomena list) · `handover` 引き継ぎノート (staff room; years of entries:
「7番の呼び出し、誰もいないのに鳴る」「鏡のこと、新人には言わないで」「権藤さんの包丁、絶対動かさないこと」…) ·
`clipping` 新聞の切り抜き (2009 crash; the waitress) · `reservation` 予約台帳 (register: every night 「2:00　1名様
ミウラ様」 in the same hand, for years) · `diary` 店長の業務日誌 (office; exhaustion; the last entries go wrong) ·
`drawing` 子供の絵 (kids' corner: crayon drawing of the restaurant and a girl hiding, 「もういいよ」) · `flyer` 迷子の
お知らせ (vestibule board, 2001) · `fire` 消防点検記録 (kitchen; the 1996 fire) · `cleaning` 清掃チェック表 (women's
restroom: 「鏡　拭いても曇る」「3番個室　開かない」) · `medium` 霊媒師のメモ (the rules, given at the start).

### 3.12 Director (poltergeist, 霊障)
Server-side pacing by area, fear, 霊障 level and time since the last event; never spam, never two big events within 20
s, quiet stretches matter. Events (each also feeds the fear and the HUD's witness list in the report note):
the outer auto door opens by itself with the chime · taps turn on (kitchen, restrooms) · **menus fly off a table and
scatter** (flutter particles, `menu_scattered` decals) · call buttons ring at empty tables (the call display shows the
number) · chairs slide / fall · lights flicker, a tube bursts, a circuit trips · the store music starts (warped) · the
PA chimes and mumbles · the register phone rings (answer: static and 「……二時に……一名で……」) · the staff TV turns
on to static · the hand dryer roars in an empty restroom · the drink bar pours by itself · a plate smashes in the
kitchen · knocking from inside the walk-in fridge / the occupied stall · the stuffed bear has turned to face you ·
glasses of water appear on empty tables · wet footprints from the door to table 7 · the menu board outside reads
「まだ　いますか」 · a car passes on the wet road · 2:00: a motorbike far away, tyres screaming, an impact, silence —
then the door opens (the visitor arrives).

### 3.13 HUD
Bottom right: film left, お札 left (icons). Top left: objective (`霊を撮影する 2/5`, later `店長を撮影する`, `霊媒師の
もとへ戻る`). The awareness eye. The breath gauge while holding. Subtitles for voices and important sounds (bottom
centre). Area name when entering a room the first time (small, fading). Controls hint after the intro.

### 3.14 Controls
| key | |
|---|---|
| right-click (camera) | raise / lower the camera |
| left-click (finder) | shutter |
| scroll (finder) | zoom |
| right-click (flashlight) | on / off |
| right-click | interact: doors, lockers, the breaker, notes, the phone, the medium |
| sneak | silent steps, quiet doors |
| **V** (hold) | hold your breath |
| **J** | 霊撮帳 (album, notes) |

## 4. (reserved)

## 5. Spirits (entities)

All spirits extend vanilla `PathfinderMob` and implement `com.reisatsu.entity.Animated`:
```java
public interface Animated { int pose(); float poseTime(float partialTick); default float glitch(float partialTick) { return 0; } }
```
`pose()` = pose id, `poseTime()` = ticks since the pose began, `glitch()` = 0..1 strength of the unnatural stutter
(frame skips, jitter, wrong-way joints) that the model applies. Models (§12) read these plus limbSwing, ageInTicks,
netHeadYaw, headPitch. Glow details (eyes, embers, the visor glint) are a second full-bright layer `<id>_eyes.png`
(`RenderType.eyes`). **Spirits render translucent** (`entityTranslucent`, alpha decided by the renderer): naked-eye alpha
per spirit below, **always 1.0 in the finder**, plus brief flickers to higher alpha at high fear.

| id | name | size (w×h) | naked eye | look | poses |
|---|---|---|---|---|---|
| `visitor` | 来店者 | 0.6×1.9 | invisible (wet footprints, the door, the chair pulled out, breathing) | rider in a black leather jacket scraped raw on one side, a **full-face helmet with a cracked visor** (behind the cracks: one eye, a faint glint), right arm hanging broken the wrong way, jeans dark with water, dripping | 0 WALK (slow limp, broken arm swinging dead), 1 SIT (seated, hands flat on the table, head bowed), 2 STAND (waiting, head down), 3 PRESS (reaching for the call button), 4 NOTICE (helmet snaps toward you, visor cracks wider), 5 LUNGE |
| `waitress` | 給仕 | 0.6×1.8 | faint (alpha 0.3) | HIDAMARI uniform: brown dress, orange apron, white collar, name badge 「小野寺」, hair in a net; skin grey-white, **a fixed polite smile too wide**, eyes black hollows; one shoe missing; a round tray with a glass of water | 0 WALK (tray at the shoulder, too smooth, head tilted), 1 SERVE (bends to set a glass), 2 BOW (deep 90° bow, hair falls forward), 3 STAND (tray before her, head tilted 30°), 4 NOTICE (head rotates sideways 90°, mouth opens), 5 CHASE (fast stiff walk, arms out, tray gone) |
| `chef` | 料理長 | 0.8×2.2 | visible (alpha 0.75) | big man in a stained white chef coat and a tall toque, apron charred; **face and hands burnt black and cracked, embers glowing in the cracks and the eyes** (eyes layer); a heavy cleaver | 0 CHOP (back to us, cleaver arm chopping), 1 STOVE (tending flames, leaning in), 2 PLATE (at the pass, head bowed, arranging), 3 LOOK (head turns slowly over the shoulder, body still), 4 WALK, 5 CHASE (charge, cleaver raised), 6 IDLE |
| `child` | かくれんぼの子 | 0.45×1.1 | invisible (giggles, running feet, things move) | 5-year-old girl: yellow kindergarten hat, pale blue smock with a name tag 「ゆうか」, red shoes, black bob covering the eyes; too still | 0 RUN (skipping), 1 COUNT (face to the pillar, forearm over the eyes), 2 HIDE (crouched small, hands over the eyes), 3 PEEK (hands slide down, head up, looking), 4 FOUND (points at you, mouth wide), 5 STAND (head tilted, hands behind), 6 GIGGLE (shoulders shaking, hand over mouth) |
| `mirror_woman` | 鏡の女 | 0.6×1.8 | only *through the mirror* and faint (alpha 0.25) | white blouse, dark skirt, soaked; long wet black hair over the face, **one eye showing**; grey skin, blue lips; water running off her fingers | 0 BOWED (leaning over the dead sink, face in her hands, crying), 1 STARE (upright, staring through the glass), 2 PRESS (both palms flat on the glass), 3 SCREAM (head back, mouth too wide), 4 STAND |
| `manager` | 店長 | 0.6×1.85 | invisible while seated in the office; visible (alpha 0.85) while hunting | white shirt with sweat stains, loosened tie, name badge 「店長　森田」, keys on the belt; grey face with deep black rings, **glasses with one lens cracked**, thinning hair, slouched | 0 SIT (at the desk facing the monitors, one hand on the mouse), 1 STAND, 2 WALK (stiff, head forward), 3 BOW (deep bow, head hanging), 4 LOOK (scanning left/right), 5 CHASE (fast power walk), 6 REACH |
| `medium` | 霊媒師 八雲トキ | 0.6×1.6 | normal living person (alpha 1, not a spirit) | old woman, grey hair in a bun, dark purple kimono with a black haori, white tabi, prayer beads (数珠) | 0 IDLE (hands together), 1 TALK (gestures), 2 PRAY (beads raised, chanting), 3 WALK, 4 KNEEL, 5 BOW |
| `medium_car` | (the car) | 1.6×1.5 | normal | old dark-green kei car (軽自動車), wet, a dim dome light inside, a small omamori hanging from the mirror | static (no AI); 0 CLOSED, 1 DOOR_OPEN |

### Behaviour (server) and photo windows
* **visitor** — arrives every 3–5 min (first at 2:00 with the motorbike/crash sound cue): spawns at `visitor_spawn`,
  walks the `visitor_path` (the outer and inner auto doors open with the chime), sits at `seat_7` (SIT), presses
  `button_7` (PRESS) every 30–60 s, waits 2–3 min, fades; the chair is left pulled out and the footprints dry slowly.
  Sight cone 50°, range ×0.8. Noticed → the auto doors lock, he stands and LUNGEs (fast, short range 8). Photo window:
  walking in (face visible from the front/side) and **seated at table 7 — his profile is visible through the south
  window from the parking lot.**
* **waitress** — walks `waitress_route` serving water: SERVE at tables (a `water_glass` appears), back to
  `waitress_station`. **When an auto door opens she goes to `waitress_bow` and BOWs (2 s, eyes closed: blind)**
  「いらっしゃいませ」. Sight cone 60°, range ×1.0; while SERVE her cone is 25° downward. Noticed → CHASE across the
  customer area (speed 0.9 × your sprint), checks stalls if she heard you. Photo windows: the bow, SERVE, from the side.
* **chef** — cycle: CHOP at `chef_board` (25–40 s, rhythmic chopping; back to the pass window; every 6–12 s the
  chopping stops and he LOOKs over his shoulder for 2–3 s), STOVE at `chef_stove` (8 s, flames roar: deaf, cone 20°),
  PLATE at `chef_pass` (6 s, head bowed: cone 30° downward, range 3, rings the pass bell). Hearing ×1.5 (noise in the
  kitchen). Noticed → CHASE inside the kitchen and corridor only (fast), roars, slams the cleaver. Photo window:
  **through the pass window from the dining room while he PLATEs**, or from the side at the stove.
* **child** — when you first enter the customer area: 「もういいかい」 from nowhere. Plays hide-and-seek: picks one of
  `child_hides`, runs there (running feet, a chair scrapes), HIDEs. Every 6–10 s she PEEKs for 2 s (cone 70°, range 9):
  **if she sees you during a peek: FOUND 「みーつけた」** — not a capture: she giggles, runs to a new hide and **calls the
  nearest other spirit to your position** (its awareness +0.5). Every few minutes she COUNTs at `child_count` (blind,
  facing the pillar). She hears breathing within 3 blocks. Photo window: **while hiding with her hands over her eyes**
  (face counts as visible).
* **mirror_woman** — lives in the cavity behind the mirror; never leaves it. Cycle: the women's taps run (`sink_w`),
  she is BOWED crying (cannot see; hears breath within 4 blocks) for 30–50 s; then **the taps stop by themselves**, she
  straightens, STAREs through the mirror for 8–12 s (sees everything in front of the mirror, cone 70°), PRESSes her
  palms on the glass (squeak, prints of hands appear), back to BOWED. Noticed → SCREAM, the mirror cracks
  (`mirror` state `cracked`), the restroom lights burst; **leave the restroom within 3 s or be caught.** Photo window:
  **BOWED, through the mirror** (from in front of the mirror).
* **manager** — phase 1: SIT at `manager_desk`, unseen; **he sees through the security cameras**: standing in any
  camera's cone (≤ 10 blocks, light or not) raises his awareness; the camera's LED turns red when it sees you; the
  office monitors show the view. Noticed in phase 1 → the PA 「お客様……」 and he comes out to hunt. With the `cameras`
  circuit off he is blind, gets up, walks to the breaker and turns it back on (STAND/WALK, LOOK). Photo window (phase
  1): his left profile through the office window, or at the breaker. Phase 2 (closing time): he patrols
  `manager_route` (WALK, LOOK, BOW at the entrance 「本日の営業は、終了いたしました」), cameras still his eyes, hearing ×1.3;
  photo window: BOW, LOOK away, or catching him at the breaker after you cut the cameras.
* **medium** — stands at `medium`, faces the player, TALKs when right-clicked: hints for the next objective, accepts
  photos (reads the name, tells the story: §1), PRAYs during the exorcism.

## 6. (reserved)

## 7. Registry ids

### Blocks (`assets/reisatsu/textures/block/<texture>.png`, 16×16 unless noted; prop models in
`assets/reisatsu/models/block/<id>.json` face **north** at rest, blockstates rotate by `facing`)

Surfaces and structure (simple cubes unless noted)
| id | notes |
|---|---|
| `dining_floor` | wood-look vinyl planks, warm brown, worn paths |
| `kitchen_floor` | red-brown quarry tiles (4 per block), dark grout, grease |
| `restroom_floor` | small grey-white mosaic tiles |
| `restroom_floor_dark` | the cavity: same mosaic, stained black, wet |
| `vestibule_floor` | dark grey granite-look tiles |
| `entrance_mat` | thin (1 px) dark rubber mat with the sun logo, facing |
| `wallpaper` / `wallpaper_stained` | cream wallpaper, faint vertical stripe / with a brown water stain and mould |
| `wainscot` | dark wood panelling with a chair rail (lower wall band) |
| `white_tile` / `white_tile_dirty` | glossy white wall tiles / with grease, cracks, black grout |
| `ceiling_tile` / `ceiling_tile_stained` | acoustic ceiling tile with pinholes and grid / with a water stain |
| `ceiling_light` | recessed square fluorescent panel. states `lit` (bool), `broken` (bool → flickers). light 13 when lit |
| `downlight` | round recessed downlight, warm; `lit`; light 11 |
| `pendant_lamp` | warm glass shade on a cord over booth tables (model in the upper half); `lit`; light 12 |
| `exit_sign` | green 誘導灯 (running man + arrow), facing, wall or ceiling (`hanging`); always light 7 |
| `exterior_wall` | beige stucco siding |
| `facade_brick` | brown brick (base of the façade) |
| `brick_sill` | brick with a concrete window sill top (slab-like, 10 px) |
| `roof_tile` (+ `_stairs`, `_slab`) | terracotta roof tiles |
| `fascia` | roof edge band: orange with a cream stripe |
| `shop_window` | big glass panel with a thin aluminium frame, translucent; condensation / droplets left by the rain |
| `window_blind` | interior blind panel (2 px) against a window, facing; `closed` (bool) |
| `asphalt` | dark wet asphalt, fine aggregate |
| `parking_line` | asphalt with a white line along `axis` (x/z) |
| `road_line` | asphalt with a yellow centre line along `axis` |
| `curb` | concrete curb (slab 8 px) |
| `sidewalk` | grey interlocking pavers, wet |
| `puddle` | 1 px translucent water film on the ground (shader: reflective water) |
| `rice_paddy` | dark harvested paddy mud with stubble |
| `hedge` | dark trimmed hedge (leaves-like, cutout) |
| `concrete_wall` | plain grey concrete (back yard wall, foundation) |
| `granite_counter` | dark speckled granite countertop |
| `stainless` | brushed stainless steel panel (kitchen walls behind the line) |
| `pillar` | square column clad in dark wood |

Doors
| id | notes |
|---|---|
| `auto_door` | automatic sliding glass door panel, 3 high. states `facing`, `side` (left/right: slides that way), `part` (bottom/middle/top), `open` (bool). Block entity on the bottom part animates the slide (≈ 0.7 s) and is rendered by a BER (aluminium frame, glass, the sun-logo decal and 「自動ドア」 sticker at hand height); no collision while open |
| `door_switch` | key switch box beside the door (自動 / 開放 / 閉鎖), facing, `on` (bool) |
| `swing_door` | kitchen swing door with a round porthole, 2 high (`half`), `facing`, `side`, `swing` (0 shut, 1 open in, 2 open out) — swings open when an entity walks through, flaps shut |
| `staff_door` | grey steel door with a 「STAFF ONLY」 plate (DoorBlock) |
| `office_door` | wood door with a small window and a 「事務所」 plate; locked until `office_key` |
| `restroom_door_m` / `restroom_door_w` | wood doors with pictogram plates |
| `back_door` | heavy steel back door with a push bar |
| `fridge_door` | white insulated walk-in door with a big chrome handle |
| `stall_door` | toilet stall door (beige laminate) with the vacant/occupied indicator; `occupied` (bool: red 使用中 and locked) |

Furniture and fixtures (custom block models; `facing` unless noted)
| id | notes |
|---|---|
| `booth_seat` | red-brown vinyl booth bench with a back (back 16 px tall, seat at 7 px) |
| `booth_partition` | frosted glass panel in a dark wood frame, sits on top of a booth back (2-high divider); blocks sight |
| `restaurant_table` | dark wood top on a central pedestal; adjacent tables read as one long table |
| `restaurant_chair` | wooden chair with a vinyl seat; `fallen` (bool → lying on its back) |
| `call_button` | table call button (round, white, a red LED); `lit` (bool) |
| `menu_stand` | laminated menus in a stand + condiments + napkin box (the table top set) |
| `menu_scattered` | thin decal of scattered laminated menus; `variant` 0..3 |
| `water_glass` | glass of ice water; `glasses` 1..4 |
| `broken_plate` | thin decal of plate shards (step = crunch) |
| `register` | POS register with a dim screen, on the counter; `open` (drawer) |
| `service_bell` | the counter bell (呼び鈴) |
| `counter` | register counter body (wood front, granite top) |
| `cake_case` | refrigerated glass case with cakes, lit (light 9) |
| `business_phone` | beige office telephone; `ringing` (bool) |
| `reservation_book` | open reservation book (note `reservation`) |
| `capsule_machine` | two stacked gacha capsule machines |
| `waiting_bench` | padded bench |
| `magazine_rack`, `umbrella_stand`, `notice_board` | waiting / vestibule props (`notice_board` is a wall decal with flyers) |
| `drink_bar` | soda fountain with a lit panel of (fictional) drinks and nozzles; `pouring` (bool); light 6 |
| `coffee_machine`, `ice_machine`, `cup_shelf`, `soup_warmer` | drink bar props |
| `server_station` | cabinet with cutlery bins, water pitchers, trays |
| `call_display` | wall/ceiling call display (呼び出し表示器); `number` 0..17 (0 blank) + `closed` (bool: shows 「閉店」 in red); light 4 |
| `planter` | long planter box with artificial foliage (divider, 1.5 high model) |
| `kids_mat`, `toy_shelf`, `stuffed_bear`, `kids_fence`, `high_chair` | kids' corner |
| `gas_range` | 4-burner range; `lit` (blue flames, light 9) |
| `fryer` | oil vat; `on` (bubbling) |
| `griddle`, `kitchen_counter`, `exhaust_hood`, `pot_shelf`, `dish_rack`, `kitchen_trash` | kitchen props (`exhaust_hood` hangs from the ceiling) |
| `prep_table` | stainless prep table |
| `cutting_board` | board with a cleaver and chopped vegetables (on a prep table) |
| `kitchen_sink` | deep stainless sink with a tall faucet; `running` (bool) |
| `dishwasher` | hood dishwasher; `running` |
| `pass_shelf` | stainless pass shelf with a heat lamp; `lit` (red-orange, light 8) |
| `pass_bell` | the pickup bell |
| `walkin_shelf` | wire shelf with boxes and frozen goods, frost |
| `frost` | frost decal for walls/floor |
| `locker` | grey steel staff locker, 2 high (`half`), `open` (bool); hiding spot |
| `time_recorder` | time clock with a card rack |
| `staff_table`, `folding_chair`, `bulletin_board`, `cardboard_boxes`, `cleaning_cart`, `filing_cabinet`, `safe` | back-of-house props |
| `crt_tv` | old CRT TV on a stand; `on` (static, light 5) |
| `office_desk`, `office_chair`, `pc` | office (`pc`: monitor glow light 4) |
| `security_monitors` | bank of four monitors; `on` (dim camera views + static, light 5), `alert` (red REC frame) |
| `security_camera` | ceiling dome camera, facing = view; `on`, `seen` (red LED) |
| `breaker_panel` | grey electrical panel; `open` |
| `restroom_sink` | counter sink with faucet; `running` |
| `mirror` | the dark mirror over the women's sinks (translucent smoked glass; the cavity shows through); `cracked` |
| `restroom_mirror` | an ordinary opaque mirror (men's) |
| `toilet`, `urinal`, `stall_wall`, `hand_dryer` (`running`), `paper_towel` | restroom props |
| `vending_machine` | drinks vending machine, 2 high (`half`), lit front (light 12) |
| `pole_sign` | the road sign: `part` = `pole` or a `tile` 0..15 of the 4×4 box face (one 64×64 texture `pole_sign_face` split in tiles) + `lit` (light 15) |
| `roof_sign` | the 「ひだまり」 roof letters, `tile` 0..7 of an 8×1 strip (`roof_sign_face` 128×16), `lit` |
| `menu_board` | A-frame chalk board; `message` 0 = 「本日のおすすめ」, 1 = 「まだ　いますか」 |
| `bike_rack`, `trash_station`, `ac_unit`, `gas_cylinder`, `dumpster`, `wheel_stop`, `outdoor_planter` | exterior props |
| `street_lamp` | sodium street lamp head on a pole (`part` pole/head), `lit` (orange, light 14) |
| `utility_pole` | concrete utility pole with a transformer / crossarm part (`part`) |
| `guardrail`, `chevron_sign`, `curve_mirror`, `traffic_light` (`lit`, blinking yellow) | road props |
| `flower_offering` | wrapped bouquet, a can of coffee and a small toy motorbike at the guardrail |
| `note_paper` | a sheet of paper (decal; note id from the block entity / marker) |

### 7.1 Who makes which block model, and their names

* **world** (`tools/gen_assets.py`) writes every **blockstate** and the models of the plain shapes: full cubes
  (`cube_all` / `cube_column` / `cube_bottom_top` / `orientable`), vanilla-parented shapes (slab, stairs, carpet for the
  1 px layers `entrance_mat` and `puddle`, leaves for `hedge`, doors) and every block **item** model. That covers:
  all of "Surfaces and structure" except `pendant_lamp`, `exit_sign`, `window_blind` (art), and the vanilla-style
  doors `staff_door`, `office_door`, `restroom_door_m`, `restroom_door_w`, `back_door`, `fridge_door`, `stall_door`
  (textures `<id>_bottom` / `<id>_top`, and `stall_door_occupied_bottom` / `_top`).
  `ceiling_light` / `downlight`: cubes whose bottom face is the panel (`<id>` / `<id>_lit` textures; `ceiling_tile` on
  the other faces).
* **art** writes every other block model (`models/block/<name>.json`, facing **north**). Names for the visual states
  (the blockstate maps to them; provide every name even if two are identical — a child model with a `parent` is fine):
  boolean property `p` → `<id>` (false) and `<id>_<p>` (true); several booleans in the order of §7 → `<id>_<p1>_<p2>`
  for the true ones (`security_monitors`, `security_monitors_on`, `security_monitors_on_alert`; `security_camera`,
  `security_camera_on`, `security_camera_on_seen`); integer property → `<id>_<value>` (`water_glass_1..4`,
  `menu_scattered_0..3`, `menu_board_0`/`_1`, `call_display_0..17` plus `call_display_closed`); `half` / `part` →
  `<id>_<part>` first, then boolean suffixes (`locker_lower`, `locker_upper_open`, `vending_machine_lower`,
  `street_lamp_head_lit`, `utility_pole_pole`, `utility_pole_top`, `pole_sign_pole`, `pole_sign_tile_<t>` /
  `pole_sign_tile_<t>_lit`, `roof_sign_tile_<t>` / `_lit`); `exit_sign` / `exit_sign_hanging`; `mirror` /
  `mirror_cracked`; `restaurant_chair` / `restaurant_chair_fallen`.
* The two moving doors are drawn by the world's block entity renderers: `auto_door` (texture `auto_door_panel`
  16×48: aluminium frame, glass (translucent), the sun-logo decal and the 「自動ドア」 sticker; static block model
  `auto_door_rail` for the top part's rail/header, art) and `swing_door` (texture `swing_door` 16×32 with the
  porthole; block model `swing_door_frame`, art).

### Items (`assets/reisatsu/textures/item/<id>.png`, 16×16)
`reisha_camera`, `reisha_camera_finder`, `film_pack`, `evidence_photo`, `flashlight`, `flashlight_on`, `ofuda`,
`shop_key`, `office_key`. 3D held models (`forge:separate_transforms`): `reisha_camera` (boxy old instant camera,
brown leather and chrome, flash bulb on top), `reisha_camera_finder` (the same, raised: mostly not visible),
`flashlight`, `flashlight_on`.

### Entities — §5. Textures `assets/reisatsu/textures/entity/<id>.png` + `<id>_eyes.png`.

### GUI (`assets/reisatsu/textures/gui/`)
`title_logo` 512×256 (「霊撮」 brush calligraphy, a red seal stamp, "REISATSU") · `finder` 512×288 (transparent
optical-finder frame: dark rounded mask, bright frame lines, parallax marks, centre circle; drawn stretched to the
screen) · `finder_meter` 128×64 (霊感計 dial; needle drawn by code) · `photo_frame` 128×160 (instant film: white
frame, photo window 112×84 at 8,8, wide bottom margin) · `photo_name_<spirit>` 128×32 ×6 (the true name in red
brush ink on transparent) · `photo_blur` 256×192 (the smeared ghost blur for failed photos) · `photo_ghost_1..3`
256×192 (faint transparent face/hand overlays) · `album` 512×320 (open notebook) · `icons` 128×32 (film, お札, eye
closed / half / open / alert, breath) · `jumpscare_<spirit>` 256² ×6 (full-face close-ups on black) ·
`medium_portrait` 64×64 · `paper` 256² (note paper) · `vignette` 256², `grain` 256² (MEIKON/SOUNAN copies) ·
`locker_slits` 256×144 (locker vents overlay) · `logo_sun` 64×64 (the HIDAMARI smiling sun, for decals and GUIs).

### Particles (`reisatsu:<id>`)
`menu_flutter` (a flying laminated menu, tumbling), `water_drop` (tap water), `wet_print` (footprint, flat on the
ground, fades), `ember` (the chef), `spirit_mote` (pale motes when a spirit is bound / at the exorcism),
`frost_breath` (your breath in the walk-in fridge), `paper_ash` (burning photos / お札).

## 8. Sounds (`assets/reisatsu/sounds/<path>.ogg`, event id `reisatsu:<event>`)

OGG Vorbis 44.1 kHz. **Mono (M) for everything positional**, stereo (S) for beds, music and UI. `att` = attenuation
distance. Variants `name_1..n`. **The mix needs real dynamic range**: the closed restaurant at night is *quiet* —
compressor hum, a ticking clock, drips — and then violence. Reuse the SOUNAN psychoacoustic palette (binaural theta
beats, infrasound-like sub with harmonics, roughness 30–150 Hz AM, nonlinear vocal phenomena, Shepard–Risset
glissandi, clusters, beating, dry close whispers vs huge reverbs, false risers, silence). New for this game: **the
banal sounds of a family restaurant made wrong** — the entrance chime, the call-button ping, 「いらっしゃいませ」,
cheerful store music — detuned, slowed, too late, too many. All melodies must be **original** (no real chimes or
songs). Japanese voice lines may be formant-synthesised and processed (SOUNAN `tools/audio/voice.py`); subtitles carry
the words.

Ambient beds (S, loops)
| event | files | description |
|---|---|---|
| `amb.night_road` | 60 s | outside after rain: wet road hiss, far traffic swells, gutters dripping, a far dog, wind in wires |
| `amb.dining` | 40 s | closed dining hall: room tone, the drink bar / cake case compressors cycling, exit-sign buzz, a wall clock ticking |
| `amb.kitchen` | 40 s | kitchen: big compressor hum, exhaust fan idling, a drip in the sink, stainless ticking as it cools |
| `amb.restroom` | 30 s | tiled room tone, water in pipes, slow drip, ventilation fan |
| `amb.back` | 30 s | back corridor / office: 50 Hz ballast hum with harmonics, PC fans, breaker buzz |
| `amb.fridge` | 20 s | walk-in fridge: loud compressor and fans, icy crackle |
| `amb.silence` | 20 s | near-silence (room-tone hiss, high faint ringing) |
| `amb.closing` | 40 s | closing time: all the lights' hum at once, the store music bleeding through walls, PA hiss |

Positional loops (M) — `att` in brackets
`amb.fluorescent` (10, flickering tube buzz) · `amb.vending` (12) · `amb.sign_buzz` (24, transformer buzz with flicker
ticks) · `amb.drip` (one-shots ×4, 12) · `amb.gutter` (16, gutter running after rain) · `fixture.faucet_loop` (12) ·
`fixture.hand_dryer` (16, 5 s roar) · `fixture.fryer_loop` (12) · `fixture.gas_loop` (12) · `fixture.dishwasher` (14) ·
`fixture.tv_static` (12) · `fixture.monitor_static` (8) · `fixture.phone_ring` (24, 6 s business phone ring) ·
`spirit.near` (8, pressure hum near a spirit) · `camera.sense` (S, the 霊感計 crackle/whine, volume by meter)

Music (S, loops unless noted)
| event | files | description |
|---|---|---|
| `music.dread` | 64 s | base score: very low drone with a binaural theta beat, beating partials, faint bowed metal |
| `music.tension` | 32 s | micro-tonal string clusters, irregular low thumps (volume = fear) |
| `music.danger` | 16 s | Shepard rise + rough strings + ticking (volume = danger) |
| `music.chase` | 16 s | hunted: pounding low drums with irregular accents, screeching clusters, falling Shepard |
| `music.safe` | 40 s | outside with the medium: fragile sparse music-box/piano, rain-dripping, sad |
| `music.title` | 48 s | title: a music-box version of the store jingle slowly detuning, drips, a far entrance chime, low drone |
| `music.store` | 32 s | the store's cheerful in-store music (original easy-listening/bossa loop, slightly lo-fi) |
| `music.store_wrong` | 32 s | the same, slowed and detuned, tape warble, dropouts, a voice humming along under it |
| `music.closing` | 32 s | closing time: the jingle in a minor key on organ, PA hiss, low clusters, a ticking clock pulse |
| `music.exorcism` | 60 s (no loop) | the ritual: singing bowl strikes, low chant drones, rising light (shimmer), a release |
| `music.ending_good` | 50 s (no loop) | dawn: warm pads, birds starting, the jingle's melody resolved gently |
| `music.ending_bad` | 30 s (no loop) | 相席: the store music on a dying music box, the call button, silence |

Restaurant one-shots (M)
| event | files | att | description |
|---|---|---|---|
| `door.auto_open` / `door.auto_close` | 1 / 1 | 16 | motor whirr + glass slide / slide + rubber seal thump |
| `door.chime` | 2 | 32 | entrance chime: an original bright two-tone "pin-poon" |
| `door.chime_wrong` | 2 | 32 | the chime too slow, detuned, a third tone that should not be there |
| `door.swing` | 3 | 12 | kitchen swing doors flapping |
| `door.open` / `door.close` | 2 / 2 | 12 | staff / office / restroom doors |
| `door.fridge_open` / `door.fridge_close` | 1 / 1 | 12 | seal pop, heavy hinge / heavy thud |
| `door.stall_bang` | 2 | 16 | stall door slammed |
| `door.locked` | 1 | 8 | handle rattle |
| `door.unlock` | 1 | 8 | key turning |
| `door.switch` | 1 | 8 | the auto-door key switch clack |
| `fixture.call_button` | 2 | 24 | table call "pin-pon" (original electronic two-tone) |
| `fixture.call_display` | 1 | 32 | the call display's chime |
| `fixture.service_bell` / `fixture.pass_bell` | 1 / 1 | 24 | counter bell "chin" / brighter pickup bell |
| `fixture.register_drawer` | 1 | 12 | drawer slides out, coins |
| `fixture.phone_voice` | 2 | S | the line: static, a garbled voice 「……二時に……一名で……」 |
| `fixture.faucet_on` / `fixture.faucet_off` | 1 / 1 | 12 | tap squeak + water start / stop + drips |
| `fixture.drink_pour` | 2 | 12 | soda pouring + ice |
| `fixture.ice_drop` | 2 | 12 | ice machine dropping cubes |
| `fixture.gas_ignite` | 1 | 12 | click-click-whoomph |
| `fixture.breaker_off` / `fixture.breaker_on` | 1 / 1 | 24 | big clunk + power-down whine / clunk + tubes pinging on |
| `fixture.light_flicker` | 3 | 12 | ballast ticks and buzz stutter |
| `fixture.light_burst` | 2 | 16 | tube pop + glass tinkle |
| `fixture.time_card` | 1 | 8 | time clock punch |
| `fixture.locker` | 2 | 8 | locker door open/close |
| `fixture.chair_scrape` | 3 | 16 | chair dragged on vinyl |
| `fixture.chair_fall` | 2 | 20 | chair falling over |
| `fixture.plate_break` | 3 | 32 | plate smashing on tile |
| `fixture.glass_set` | 2 | 10 | a glass of water set down, ice clinks |
| `fixture.menu_scatter` | 2 | 16 | laminated menus flapping and sliding over the floor |
| `fixture.pa_chime` | 1 | S | store PA four-tone chime (original) |
| `fixture.pa_voice` | 3 | S | garbled announcement; one says 「本日の営業は、終了いたしました」 |
| `fixture.car_pass` | 3 | 48 | a car passing on the wet road (hiss, doppler) |
| `fixture.motorbike` | 1 | 96 | a motorbike approaching far away (8 s) |
| `fixture.crash` | 1 | 96 | tyres screaming, impact, glass, silence |
| `fixture.toilet_flush` | 1 | 16 | flush |
| `fixture.stall_knock` | 3 | 12 | knocking from inside the stall |
| `fixture.fridge_knock` | 3 | 12 | knocking from inside the walk-in fridge, muffled |
| `fixture.ball_bounce` | 1 | 16 | a ball bouncing to a stop in the kids' corner |
| `fixture.toy_squeak` | 1 | 8 | a squeaky toy |

Camera and items
| event | files | ch | description |
|---|---|---|---|
| `camera.raise` / `camera.lower` | 1 / 1 | S | leather strap rustle + click |
| `camera.shutter` | 2 | M att 10 | heavy mechanical shutter + flash pop |
| `camera.charge` | 1 | S | flash capacitor charging whine |
| `camera.eject` | 1 | S | motor whirr, the instant photo sliding out |
| `camera.empty` | 1 | S | dry click |
| `camera.zoom` | 1 | S | lens motor tick |
| `camera.load` | 1 | S | opening the back, film pack in, closing |
| `photo.develop` | 1 | S | soft sizzle swell as the image appears |
| `photo.name` | 1 | S | a whisper and a pen scratching as the red name writes itself |
| `photo.fail` | 1 | S | a moan smeared under a distorted shutter |
| `item.flashlight_on` / `item.flashlight_off` | 1 / 1 | M | switch click |
| `item.flashlight_flicker` | 3 | M | electric stutter |
| `item.ofuda_burn` | 1 | S | paper flaring up, small bells, a whoosh |
| `item.key` | 1 | M | keys jingle |
| `item.paper` | 2 | S | paper rustle |

Player (M)
`player.heartbeat` (one muffled lub-dub), `player.breath_fear` ×3, `player.breath_hold` (inhale, then strained
silence), `player.gasp` ×2, `player.step_tile` ×4, `player.step_vinyl` ×4, `player.step_kitchen` ×4 (slightly sticky),
`player.step_wet` ×4 (wet asphalt), `player.step_glass` ×3 (shards), `player.step_paper` ×2 (menus) — the blocks'
sound types use these.

Spirits (M)
| event | files | att | description |
|---|---|---|---|
| `visitor.step` | 4 | 12 | wet squelching steps, one foot dragging |
| `visitor.breath` | 2 | 6 | breathing inside a helmet, muffled, fogging |
| `visitor.visor` | 1 | 8 | visor cracking |
| `visitor.drip` | 2 | 6 | water dripping off him |
| `visitor.whisper` | 2 | 6 | 「……まだかな」 inside the helmet |
| `waitress.step` | 3 | 10 | soft shoe steps, too regular |
| `waitress.greet` | 3 | 24 | 「いらっしゃいませ」: one almost normal, one slowed, one with too many syllables |
| `waitress.water` | 1 | 12 | 「お冷やを、どうぞ」 |
| `waitress.hum` | 1 | 12 | humming the store jingle, off key |
| `waitress.tray` | 1 | 16 | tray clatter + glass |
| `chef.chop` | 2 | 20 | cleaver on board, a 4 s rhythmic loop segment |
| `chef.chop_stop` | 1 | 20 | the last heavy chop, then nothing |
| `chef.breath` | 2 | 8 | wheezing smoky breath with crackles |
| `chef.sizzle` | 1 | 8 | burning skin crackle |
| `chef.roar` | 1 | 32 | furious roar + cleaver slammed into steel |
| `chef.step` | 3 | 16 | heavy steps on tile |
| `chef.scrape` | 1 | 12 | cleaver scraped along steel |
| `child.giggle` | 4 | 16 | small giggles (one right behind you) |
| `child.count` | 1 | 24 | 「いーち、にーい、さーん……」 sing-song, 8 s, the last numbers wrong |
| `child.moui` | 1 | 32 | 「もういいかい」 |
| `child.madada` | 1 | 24 | 「まーだだよ」 |
| `child.mitsuketa` | 1 | 16 | 「みーつけた」 (close, delighted) |
| `child.run` | 3 | 16 | small feet running on vinyl |
| `child.hum` | 1 | 12 | humming an (original) nursery tune |
| `child.whisper` | 1 | 6 | 「こっちだよ」 |
| `mirror.sob` | 2 | 10 | crying behind glass |
| `mirror.whisper` | 1 | 6 | 「……うしろ」 |
| `mirror.knock` | 2 | 12 | knocking on the glass from the other side |
| `mirror.squeak` | 2 | 10 | palms dragged on glass |
| `mirror.scream` | 1 | 24 | scream, glass cracking |
| `manager.step` | 3 | 12 | leather shoes, measured |
| `manager.keys` | 1 | 10 | keys jingling at the belt |
| `manager.welcome` | 1 | 16 | 「いらっしゃいませ」 tired monotone |
| `manager.closed` | 1 | 32 | 「本日の営業は、終了いたしました」 |
| `manager.customer` | 2 | 16 | 「お客様……」 |
| `manager.sigh` | 1 | 8 | exhausted sigh |
| `manager.typing` | 1 | 8 | keyboard typing (loopable 6 s) |
| `manager.chair` | 1 | 8 | office chair creak |
| `medium.voice` | 6 | 8 | short soft murmurs under her dialogue lines (no words) |
| `medium.bell` | 1 | 24 | singing bowl (りん) |
| `medium.chant` | 1 | 16 | chant drone loop for the exorcism (8 s) |
| `medium.clap` | 1 | 24 | two ritual claps |
| `spirit.whisper` | 6 | 6 | unintelligible close whispers |
| `spirit.notice` | 3 | 16 | the universal "noticed" sting: sharp rough breath-scream |
| `spirit.bound` | 1 | 16 | being pulled into the photo: reversed scream + shimmer |
| `spirit.vanish` | 1 | 12 | fading out (soft reversed swell) |

Game (S)
`game.jumpscare` ×3 (extremely loud: rough scream + distorted burst + sub drop) · `game.stinger` ×4 · `game.riser`
×2 (rising tension that cuts to silence) · `game.caught` (being taken: rush, choir-like cluster, cut) · `game.wake`
(waking at the car: ringing fades, the medium's bell) · `game.captured` (a spirit's name appears: soft chime + low
swell) · `game.closing` (closing time begins: PA chime, all lights clunking on, a deep boom) · `game.title_hit` ·
`game.release` (a spirit sent on at the exorcism: bright shimmer swell) · `game.dawn` (warm swell, first birds) ·
`game.ending_bad`.

Subtitles: every event has `subtitles.reisatsu.<event>` in both languages (`tools/audio/subtitles_{ja,en}.json`).
Voice lines show their words as subtitles.

## 9. Shader contract ("ReisatsuShaders", `src/main/shaderpack/shaders/`, Oculus / Iris / OptiFine)

* `dimension.properties`: `dimension.reisatsu_hidamari=reisatsu:hidamari` → folder `reisatsu_hidamari/` holds stubs
  that `#define REISATSU_HIDAMARI` and include the shared programs. The root programs serve every other dimension.
* **Channels driven by the mod, only inside `reisatsu:hidamari`, every client tick**: `rainStrength` = **fear** 0..1,
  `thunderStrength` = **danger** 0..1 (no precipitation is drawn there; the pack must not draw rain/lightning from
  them), `nightVision` > 0 while the **finder** is raised, **`blindness` = camera flash** (1.0 on the shutter frame,
  decaying to 0 over ≈ 0.25 s; the pack must never darken for it — it must light the scene like a flash: a very bright
  light at the camera, falloff ∝ 1/d², slightly cool white), `darknessFactor` (vanilla Darkness) while being caught.
  `worldTime` is always 18000.
* `item.properties` (main hand → `heldItemId`, off hand → `heldItemId2`; handle both):
  `item.10001=reisatsu:flashlight_on` (warm white beam ~4300 K, bright hot spot, ~25° half-angle, range ~30, dust in
  the beam indoors) · `item.10004=reisatsu:reisha_camera_finder` (the **finder look**, below).
* `entity.properties`: `entity.20001=reisatsu:visitor`, `20002 waitress`, `20003 chef`, `20004 child`,
  `20005 mirror_woman`, `20006 manager` (spirits), `20010 reisatsu:medium`. **Spirits**: translucent; pale rim glow,
  slow noise dissolve at the edges, a slight vertex waver, chromatic split; a faint heat-haze refraction of the scene
  where a spirit stands even when it is invisible to the eye (keep it subtle: the player should *almost* see
  something); in the finder they glow cold white-blue with a short **afterimage trail**.
* `block.properties` ids (with states where they matter): emissive lights (`ceiling_light:lit=true`, `downlight`,
  `pendant_lamp`, `exit_sign` (green), `vending_machine`, `pole_sign:lit=true`, `roof_sign`, `street_lamp:lit=true`
  (sodium orange), `traffic_light:lit=true` (yellow), `drink_bar`, `cake_case`, `pass_shelf:lit=true` (red-orange),
  `gas_range:lit=true` (blue), `crt_tv:on=true`, `security_monitors`, `pc`, `call_display`, `call_button:lit=true`,
  `security_camera:seen=true` (red LED)); **wet / reflective**: `asphalt`, `parking_line`, `road_line`, `sidewalk`,
  `curb` (wet, screen-space reflections of the sign, the vending machines and lamps), `puddle` (water: mirror-like),
  glossy floors (`dining_floor`, `kitchen_floor`, `restroom_floor`, `vestibule_floor`: soft reflections of lights),
  metal (`stainless`, `kitchen_sink`, `prep_table`, `exhaust_hood`, `kitchen_counter`: sharp specular), glass
  (`shop_window`: droplets and streaks, reflections of the lit interior from outside and of the dark lot from inside;
  `mirror`: dark mirror — ~40 % screen-space reflection over the cavity seen through it, cold tint, smudges; `cake_case`,
  `water_glass`), foliage (`hedge`, plants: very slight sway).
* **The look**: inside — dark; pools of light from the green exit signs, the drink bar's standby glow, the cake case's
  cold white, the street light and the sign leaking orange through the blinds; fluorescent tubes (when on) cold
  white-green, flickering; glossy floors reflecting every light; deep shadows under tables; dust in the beam. Outside —
  wet asphalt mirroring the pole sign, vending machines and sodium lamps; low mist; overcast sky with an orange town
  glow on the horizon; drips. **Fear**: heartbeat-pulsed vignette, tunnel vision, chromatic aberration, desaturation,
  faint faces in the peripheral noise at high fear. **Danger**: subtle barrel "breathing", shadows shifting red, heavier
  grain, double vision. **Finder (霊視)**: the image through an old camera finder — slightly desaturated cool tone,
  heavy fine grain, vignette, slight barrel distortion, focus breathing, a soft halation on lights, spirits glowing
  (above). **Flash**: above. **Caught**: `darknessFactor` crushes to black.
* Performance: 60 fps on a mid GPU at 1080p by default; profiles in `shaders.properties` (+ `lang/ja_jp.lang`).
  The mod's own non-shader effects (fog, vignette, grain) are reduced while a pack is active.

## 10. Acoustics engine (音MOD) — `com.reisatsu.client.sound.Acoustics`

A built-in, lightweight version of what sound-physics mods do, via OpenAL EFX (LWJGL `EXTEfx`), listening to Forge's
`PlaySoundSourceEvent` / `PlayStreamingSourceEvent` (both fire on the sound thread with the `Channel`; read its source
id reflectively: the non-static `int` field of `com.mojang.blaze3d.audio.Channel` for which `AL10.alIsSource` is true).
* **Room reverb**: one auxiliary effect slot with an EAX reverb whose preset follows the listener's room
  (`Layout.roomAt`): `dining` (medium room, soft), `kitchen` (bright, hard, short), `restroom` (very reflective tiles),
  `small_hard`, `small_soft`, `corridor`, `tiny_dead` (walk-in fridge: almost none), `outside` (none + a faint slap
  from the building façade), cross-fading over 0.5 s when you move. Sounds started inside a different room than the
  listener get less send.
* **Occlusion**: each new positional source gets a low-pass direct filter from a ray march between listener and source
  (count solid, non-glass blocks; doors count half; each block −3 dB HF, cap −24 dB), so a spirit's steps behind a wall
  are muffled and through an open door are clear. Update looping/moving sources every 4 ticks.
* Must fail safe: no EFX → do nothing. A client config switch `acoustics` (default on).
* Footsteps: the blocks' `SoundType`s use the `player.step_*` sounds (§8).

## 11. Art direction

A Japanese suburban family restaurant from around 1990, refitted in the 2000s, now tired: warm wood-look floors,
cream wallpaper, dark wood wainscot, red-brown vinyl booths, frosted-glass partitions, orange/brown brand colours with
the smiling sun logo, yellowed ceiling tiles with water stains, stainless kitchen with old grease, tired restrooms.
**At night everything is cold and dark except the light sources.** Outside: wet black asphalt, white parking lines,
sodium orange, vending-machine white, the sign's warm orange — and darkness beyond. The cheerful brand is what makes it
creepy. Pixel art that reads at 16×16 like vanilla, careful shading, a little noise, signs of use everywhere (scuffs,
stains, chipped corners). **Spirits**: J-horror — stillness, wrong proportions, faces half hidden, wet hair, burnt skin,
the polite smile; no gore. Their animations matter as much as their looks: stutter (missing frames), heads that turn
too far or too slowly, bows that go too deep, the stillness between movements.

## 12. Code ownership and contracts

Five parallel workers + the integrator. Stay inside your paths; when you need something from another owner, use the
contract below or ask the integrator.

| owner | paths |
|---|---|
| **world** | `world/**` (dimension, chunk generator, restaurant + exterior builder, `Layout`, `Fixtures`, `Circuits`), `block/**` (all block classes and block entities), `client/render/block/**` (block entity renderers), `client/WorldClientSetup.java`, `registry/ModBlocks.java`, `registry/ModBlockEntities.java`, `registry/ModDimensions.java`, `data/**`, `tools/gen_assets.py` (blockstates, simple models, block items), `debug/WorldTour.java`, `debug/WorldCheck.java` |
| **game** | everything else in `src/main/java` (`Reisatsu`, config, `entity/**` AI, `item/**`, `game/**`, `network/**`, `client/**` except `client/model/**`, `client/render/block/**`, `client/sound/Acoustics*.java`, `client/WorldClientSetup.java`), `registry/ModItems|ModEntities|ModParticles|ModCreativeTab|ModKeys`, `command/**`, `debug/**` except the world's two files, `assets/reisatsu/lang/**` + `tools/gen_lang.py`, `README.md` |
| **spirits** (art) | `client/model/**` (the seven entity models + the car), `assets/reisatsu/textures/entity/**`, `textures/gui/jumpscare_*`, `textures/gui/medium_portrait.png`, `tools/art/ent_*.py` |
| **art** | all other textures (`textures/block|item|gui|particle/**`), prop block models and 3D item models in `assets/reisatsu/models/**` (never the simple ones made by `gen_assets.py`), particle JSONs, `tools/art/**` except `ent_*` |
| **sound** | `assets/reisatsu/sounds/**`, `assets/reisatsu/sounds.json`, `registry/ModSounds.java` (generated by `tools/gen_sounds_java.py` from the catalog: **field names are the event ids upper-cased with `.` → `_`**, e.g. `DOOR_CHIME`), `tools/audio/**`, `client/sound/Acoustics*.java` |
| **shader** | `src/main/shaderpack/**`, `tools/shader/**` |

**Contracts (already in the base commit as compiling stubs; the world fills them in, everybody calls them):**
* `com.reisatsu.world.Layout` — `DIMENSION` key, `FEET_Y`, `marker(String)`, `markers(String)`, `rooms()`,
  `roomAt(BlockPos)`, `Room(key, nameJa, box, acoustics)`.
* `com.reisatsu.world.Fixtures` — what the game's director and spirits do to the restaurant (doors, taps, buttons,
  menus, glasses, chairs, lights, circuits, devices, mirror, footprints, reset).
* `com.reisatsu.world.FixtureEvents` — what the restaurant tells the game: the game sets `FixtureEvents.LISTENER`;
  the world's blocks call it for interactions (`interact(player, key, pos)`), noises (`noise(level, pos, radius,
  cause)`), the breaker, lockers (hide / leave), notes (`note(player, id)`), call buttons, the phone, the key switch.
* `com.reisatsu.entity.Animated` — §5. Models never import game classes other than `Animated` (and vanilla).
* `com.reisatsu.registry.ModSounds` — one `RegistryObject<SoundEvent>` per event of §8.

Testing: builds run only on GitHub Actions (`bash tools/ci.sh "msg"` on your own branch). The `build` job runs the
dedicated-server self test (`debug/SelfTest`, system property `reisatsu.selftest`) and a vanilla client screenshot tour
(`client/AutoShots`, `reisatsu.shots`); the `shaders` job runs the same tour under Oculus + Embeddium with the pack.
Results: branches `ci-<branch>` / `ci-<branch>-shaders` (screenshots, logs). The tour starts with
`debug/WorldTour.shots()` (a list of named camera positions) and then the game's shots.
