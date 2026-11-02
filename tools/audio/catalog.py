"""The complete 遭難 ―SOUNAN― sound list (docs/DESIGN.md section 7, plus the
extra events listed at the end) with generator, level target, attenuation,
loop flag, Vorbis quality and subtitles for every event."""
from collections import namedtuple

import music as MU
import sfx_amb as A
import sfx_beds as B
import sfx_game as G
import sfx_monsters as MO
import sfx_phone as P
import sfx_player as PL

# event : sound event id (registered as sounan:<event>)
# path  : file path under assets/sounan/sounds (variants get _1.._n)
# count : number of variants
# ch    : 1 = mono (positional), 2 = stereo (beds / music / UI)
# fn    : generator(rng, index) -> numpy signal
# att   : attenuation_distance for positional (mono) sounds, None otherwise
# norm  : ('peak', dB[, max_crest_dB])     sample peak (true peak verified <= -1 dBTP)
#         ('lufs', LUFS, peak_cap_dB)       integrated loudness (BS.1770), brickwalled at the cap
#         ('dense', rms_dB, true_peak_dB)   maximised, true peak verified on the decoded OGG
# loop  : must loop seamlessly (built circularly; checked in report.txt)
# q     : Vorbis quality
# ja/en : subtitle (None = no subtitle: music, beds, UI ticks)
E = namedtuple("E", "event path count ch fn att norm loop q ja en extra")


def e(event, path, count, ch, fn, att, norm, ja, en, loop=False, q=4, **extra):
    return E(event, path, count, ch, fn, att, norm, loop, q, ja, en, extra)


CATALOG = [
    # ------------------------------------------------------------ ambient beds
    e("amb.forest", "amb/forest", 1, 2, B.forest, None, ("lufs", -31, -6), None, None, loop=True, q=4),
    e("amb.insects", "amb/insects", 1, 2, B.insects, None, ("lufs", -29, -6), None, None, loop=True, q=4),
    e("amb.wind_ridge", "amb/wind_ridge", 1, 2, B.wind_ridge, None, ("lufs", -26, -4), None, None, loop=True, q=4),
    e("amb.tunnel", "amb/tunnel", 1, 2, B.tunnel, None, ("lufs", -31, -6), None, None, loop=True, q=4),
    e("amb.village", "amb/village", 1, 2, B.village, None, ("lufs", -30, -4), None, None, loop=True, q=4),
    e("amb.silence", "amb/silence", 1, 2, B.silence, None, ("lufs", -45, -20), None, None, loop=True, q=4),
    e("amb.stream", "amb/stream", 1, 1, B.stream, 24, ("lufs", -25, -4), None, None, loop=True, q=4),
    # ------------------------------------------------------------ music layers
    e("music.dread", "music/dread", 1, 2, MU.dread, None, ("lufs", -28, -4), None, None, loop=True, q=6),
    e("music.tension", "music/tension", 1, 2, MU.tension, None, ("lufs", -28, -4), None, None, loop=True, q=5),
    e("music.danger", "music/danger", 1, 2, MU.danger, None, ("lufs", -26, -3), None, None, loop=True, q=5, seam="start"),
    e("music.chase", "music/chase", 1, 2, MU.chase, None, ("lufs", -20, -1.5), None, None, loop=True, q=5, seam="start"),
    e("music.safe", "music/safe", 1, 2, MU.safe, None, ("lufs", -29, -4), None, None, loop=True, q=5),
    e("music.oyama", "music/oyama", 1, 2, MU.oyama, None, ("lufs", -23, -2), None, None, loop=True, q=5),
    e("music.binaural", "music/binaural", 1, 2, MU.binaural, None, ("lufs", -32, -10), None, None, loop=True, q=5),
    e("music.title", "music/title", 1, 2, MU.title, None, ("lufs", -26, -4), None, None, loop=True, q=5),
    e("music.ending_good", "music/ending_good", 1, 2, MU.ending_good, None, ("lufs", -24, -2), None, None, q=5),
    e("music.ending_bad", "music/ending_bad", 1, 2, MU.ending_bad, None, ("lufs", -25, -2), None, None, q=5),
    # ------------------------------------------------------------ ambient one-shots
    e("amb.branch_snap", "amb/branch_snap", 5, 1, A.branch_snap, 24, ("peak", -2, 24), "枝の折れる音", "Branch snaps"),
    e("amb.footsteps_run", "amb/footsteps_run", 3, 1, A.footsteps_run, 32, ("peak", -3), "誰かが走り抜ける", "Someone runs past"),
    e("amb.footsteps_walk", "amb/footsteps_walk", 3, 1, A.footsteps_walk, 24, ("peak", -3, 20), "落ち葉を踏む足音", "Footsteps in the leaves"),
    e("amb.deer_scream", "amb/deer_scream", 3, 1, A.deer_scream, 64, ("peak", -2), "女の悲鳴のような鹿の声", "A deer screams like a woman"),
    e("amb.fox_scream", "amb/fox_scream", 2, 1, A.fox_scream, 48, ("peak", -2), "狐の金切り声", "Fox shrieks"),
    e("amb.owl", "amb/owl", 2, 1, A.owl, 64, ("peak", -3), "フクロウが鳴く", "Owl hoots"),
    e("amb.tree_knock", "amb/tree_knock", 3, 1, A.tree_knock, 40, ("peak", -2), "木の幹を叩く音", "Knocking on a tree"),
    e("amb.rockfall", "amb/rockfall", 2, 1, A.rockfall, 40, ("peak", -2), "落石の音", "Rocks tumble"),
    e("amb.creak_tree", "amb/creak_tree", 4, 1, A.creak_tree, 32, ("peak", -2), "杉の幹が軋む", "A cedar groans"),
    e("amb.thud", "amb/thud", 3, 1, A.thud, 48, ("peak", -2), "遠くで重い物が落ちる", "Something heavy falls"),
    e("amb.whisper", "amb/whisper", 6, 1, A.whisper, 6, ("peak", -4), "耳元で囁く声", "Something whispers"),
    e("amb.whisper_name", "amb/whisper_name", 2, 1, A.whisper_name, 6, ("peak", -4), "「ねえ…」", "\"Hey...\""),
    e("amb.giggle", "amb/giggle", 3, 1, A.giggle, 16, ("peak", -3), "くすくす笑う声", "Soft giggling"),
    e("amb.distant_bell", "amb/distant_bell", 3, 1, A.distant_bell, 64, ("peak", -3), "遠くで熊鈴が鳴る", "A distant bear bell"),
    e("amb.distant_scream", "amb/distant_scream", 2, 1, A.distant_scream, 96, ("peak", -2), "遠くで悲鳴", "A distant scream"),
    e("amb.distant_call", "amb/distant_call", 3, 1, A.distant_call, 96, ("peak", -2), "誰かが呼んでいる", "Someone calls out"),
    e("amb.breath_behind", "amb/breath_behind", 3, 1, A.breath_behind, 4, ("peak", -3), "背後で息遣い", "Breathing behind you"),
    e("amb.drip", "amb/drip", 4, 1, A.drip, 16, ("peak", -4), "水が滴る", "Water drips"),
    e("amb.tin_bang", "amb/tin_bang", 2, 1, A.tin_bang, 32, ("peak", -2), "トタンが鳴る", "Tin sheet bangs"),
    e("amb.wind_chime", "amb/wind_chime", 2, 1, A.wind_chime, 24, ("peak", -4), "風鈴が鳴る", "Wind chime rings"),
    e("amb.radio", "amb/radio", 2, 1, A.radio, 24, ("peak", -3), "古いラジオの雑音", "Old radio crackles"),
    e("amb.tinnitus", "amb/tinnitus", 1, 2, A.tinnitus, None, ("peak", -14), "耳鳴り", "Ears ring"),
    e("amb.ear_pressure", "amb/ear_pressure", 1, 2, A.ear_pressure, None, ("peak", -4), "耳が詰まる", "Ears pop"),
    # ------------------------------------------------------------ monsters
    e("nozoki.breath", "nozoki/breath", 3, 1, MO.nozoki_breath, 12, ("peak", -3), "湿った息遣い", "Wet breathing"),
    e("nozoki.giggle", "nozoki/giggle", 2, 1, MO.nozoki_giggle, 20, ("peak", -2), "甲高い壊れた笑い声", "High, broken giggle"),
    e("nozoki.shriek", "nozoki/shriek", 2, 1, MO.nozoki_shriek, 48, ("peak", -1), "おぞましい絶叫", "Horrible shriek", preload=True),
    e("nozoki.crawl", "nozoki/crawl", 4, 1, MO.nozoki_crawl, 24, ("peak", -2), "何かが這い寄る", "Something crawls closer"),
    e("nozoki.creak", "nozoki/creak", 2, 1, MO.nozoki_creak, 12, ("peak", -3, 20), "関節の鳴る音", "Joints crack"),
    e("yobigoe.call", "yobigoe/call", 4, 1, MO.yobigoe_call, 96, ("peak", -1.5), "「おーい」と呼ぶ声", "\"Hey!\" someone calls"),
    e("yobigoe.call_wrong", "yobigoe/call_wrong", 2, 1, MO.yobigoe_call_wrong, 96, ("peak", -1.5), "歪んだ呼び声", "The call warps"),
    e("yobigoe.laugh", "yobigoe/laugh", 2, 1, MO.yobigoe_laugh, 32, ("peak", -3), "低い笑い声", "Low laughter"),
    e("yobigoe.rush", "yobigoe/rush", 1, 1, MO.yobigoe_rush, 24, ("peak", -1), "何かが迫る", "Something rushes at you", preload=True),
    e("ushiro.step", "ushiro/step", 6, 1, MO.ushiro_step, 8, ("peak", -3, 18), "背後の足音", "Footsteps behind you"),
    e("ushiro.breath", "ushiro/breath", 3, 1, MO.ushiro_breath, 4, ("peak", -4), "耳元に冷たい息", "Cold breath on your ear"),
    e("ushiro.whisper", "ushiro/whisper", 3, 1, MO.ushiro_whisper, 4, ("peak", -3), "「ふりかえって…」", "\"Turn around...\""),
    e("ushiro.leave", "ushiro/leave", 1, 1, MO.ushiro_leave, 6, ("peak", -4), "「…つまらない」", "\"...Boring.\""),
    e("ushiro.attack", "ushiro/attack", 1, 1, MO.ushiro_attack, 8, ("peak", -1), "耳元で絶叫", "A scream in your ear", preload=True),
    e("koyanushi.sleep", "koyanushi/sleep", 1, 1, MO.koyanushi_sleep, 12, ("peak", -4), "湿ったいびき", "Wet snoring", loop=True),
    e("koyanushi.mumble", "koyanushi/mumble", 3, 1, MO.koyanushi_mumble, 12, ("peak", -4), "寝言", "Sleep-mumbling"),
    e("koyanushi.wake", "koyanushi/wake", 1, 1, MO.koyanushi_wake, 16, ("peak", -2), "骨の鳴る音", "Bones crack"),
    e("koyanushi.scream", "koyanushi/scream", 1, 1, MO.koyanushi_scream, 40, ("peak", -1), "怒りの咆哮", "Furious howl", preload=True),
    e("koyanushi.step", "koyanushi/step", 4, 1, MO.koyanushi_step, 16, ("peak", -2), "裸足の足音", "Bare feet on wood"),
    e("oyama.step", "oyama/step", 4, 1, MO.oyama_step, 64, ("peak", -1.5), "巨大な足音", "Huge footsteps"),
    e("oyama.groan", "oyama/groan", 3, 1, MO.oyama_groan, 96, ("peak", -1.5), "山が呻く", "The mountain groans"),
    e("oyama.roar", "oyama/roar", 2, 1, MO.oyama_roar, 128, ("peak", -1), "凄まじい咆哮", "Tremendous roar", preload=True),
    e("oyama.near", "oyama/near", 1, 1, MO.oyama_near, 32, ("peak", -3), "巨大な何かの息遣い", "Something huge breathes", loop=True),
    e("oyama.branch", "oyama/branch", 3, 1, MO.oyama_branch, 48, ("peak", -2), "枝がへし折れる", "Branches break"),
    e("utsuru.hum", "utsuru/hum", 1, 1, MO.utsuru_hum, 12, ("peak", -8), "微かな雑音と囁き", "Faint static whispers", loop=True),
    e("utsuru.turn", "utsuru/turn", 1, 1, MO.utsuru_turn, 12, ("peak", -4), "息を呑む囁き", "A whispered gasp"),
    # ------------------------------------------------------------ player & blocks
    e("player.heartbeat", "player/heartbeat", 1, 1, PL.heartbeat, 8, ("peak", -2), "心臓の鼓動", "Heartbeat", preload=True),
    e("player.breath_fear", "player/breath_fear", 3, 1, PL.breath_fear, 8, ("peak", -5), "震える息", "Trembling breath"),
    e("player.breath_run", "player/breath_run", 2, 1, PL.breath_run, 12, ("peak", -4), "荒い息", "Panting"),
    e("player.gasp", "player/gasp", 2, 1, PL.gasp, 8, ("peak", -3), "息を呑む", "Gasp"),
    e("player.fall", "player/fall", 1, 1, PL.fall, 16, ("peak", -1.5), "斜面を転がり落ちる", "Tumbling down the slope"),
    e("block.leaves_step", "block/leaves_step", 6, 1, PL.leaves_step, 16, ("peak", -2, 18), "落ち葉を踏む音", "Footsteps on dry leaves"),
    # ------------------------------------------------------------ items
    e("item.flashlight_on", "item/flashlight_on", 1, 1, PL.flashlight_on, 8, ("peak", -4), "懐中電灯をつける", "Flashlight clicks on"),
    e("item.flashlight_off", "item/flashlight_off", 1, 1, PL.flashlight_off, 8, ("peak", -4), "懐中電灯を消す", "Flashlight clicks off"),
    e("item.flashlight_flicker", "item/flashlight_flicker", 3, 1, PL.flashlight_flicker, 8, ("peak", -5), "懐中電灯が明滅する", "Flashlight flickers"),
    e("item.battery", "item/battery", 1, 1, PL.battery, 8, ("peak", -4), "電池を交換する", "Batteries swapped"),
    e("item.bear_bell", "item/bear_bell", 3, 1, PL.bear_bell, 48, ("peak", -2), "熊鈴が鳴る", "Bear bell rings"),
    e("item.rope", "item/rope", 1, 1, PL.rope, 12, ("peak", -3), "ロープを結ぶ", "Rope tied"),
    e("item.map", "item/map", 1, 1, PL.map_, 8, ("peak", -5), "地図を広げる", "Map unfolds"),
    e("item.flare_ignite", "item/flare_ignite", 1, 1, PL.flare_ignite, 24, ("peak", -2), "発煙筒に点火", "Flare ignites"),
    e("item.flare_burn", "item/flare_burn", 1, 1, PL.flare_burn, 16, ("peak", -5), "発煙筒が燃える", "Flare burns", loop=True),
    e("item.omamori", "item/omamori", 1, 2, PL.omamori, None, ("peak", -2), "お守りが弾ける", "Omamori bursts"),
    e("item.key", "item/key", 1, 1, PL.key, 8, ("peak", -5), "鍵の音", "Keys jingle"),
    e("item.unlock", "item/unlock", 1, 1, PL.unlock, 16, ("peak", -3), "錠が外れ、鎖が落ちる", "Lock opens, chain falls"),
    e("item.offering", "item/offering", 1, 1, PL.offering, 12, ("peak", -4), "御神酒を供える", "Offering placed"),
    e("item.paper", "item/paper", 2, 1, PL.paper, 8, ("peak", -6, 20), "紙の音", "Paper rustles"),
    e("item.bag", "item/bag", 1, 1, PL.bag, 8, ("peak", -4), "バッグを探る", "Rummaging in the bag"),
    # ------------------------------------------------------------ phone
    e("phone.tap", "phone/tap", 2, 2, P.tap, None, ("peak", -12), None, None),
    e("phone.unlock", "phone/unlock", 1, 2, P.unlock, None, ("peak", -10), None, None),
    e("phone.vibrate", "phone/vibrate", 1, 1, P.vibrate, 6, ("peak", -3), "スマホが震える", "Phone vibrates"),
    e("phone.notify", "phone/notify", 1, 2, P.notify, None, ("peak", -5), "通知音", "Notification chime"),
    e("phone.notify_wrong", "phone/notify_wrong", 1, 2, P.notify_wrong, None, ("peak", -5), "歪んだ通知音", "A warped notification"),
    e("phone.signal", "phone/signal", 1, 2, P.signal, None, ("peak", -4), "通知が次々と届く", "Notifications flood in"),
    e("phone.ringtone", "phone/ringtone", 1, 1, P.ringtone, 24, ("peak", -3), "着信音", "Phone rings", loop=True, q=5, seam="start"),
    e("phone.ringtone_wrong", "phone/ringtone_wrong", 1, 2, P.ringtone_wrong, None, ("peak", -4), "狂った着信音", "The ringtone warps", loop=True, q=5, seam="start"),
    e("phone.ring_old", "phone/ring_old", 1, 1, P.ring_old, 32, ("peak", -2), "公衆電話が鳴る", "Public phone rings", loop=True, seam="start"),
    e("phone.call_static", "phone/call_static", 1, 2, P.call_static, None, ("peak", -6), "受話口の雑音", "Static on the line", loop=True),
    e("phone.voice", "phone/voice", 3, 2, P.voice, None, ("peak", -4), "電話の向こうの声", "A voice on the line"),
    e("phone.hangup", "phone/hangup", 1, 2, P.hangup, None, ("peak", -8), "話し中の音", "Busy tone"),
    e("phone.shutter", "phone/shutter", 1, 2, P.shutter, None, ("peak", -5), "シャッター音", "Camera shutter"),
    e("phone.low_battery", "phone/low_battery", 1, 2, P.low_battery, None, ("peak", -8), "電池残量の警告音", "Low battery beep"),
    e("phone.dead", "phone/dead", 1, 2, P.dead, None, ("peak", -6), "スマホの電源が落ちる", "Phone dies"),
    # ------------------------------------------------------------ game (stereo)
    e("game.jumpscare", "game/jumpscare", 3, 2, G.jumpscare, None, ("dense", -7.5, -1.0), "絶叫", "Scream", preload=True, post_rough=(0.55, 72.0, 0.5)),
    e("game.stinger", "game/stinger", 4, 2, G.stinger, None, ("peak", -1.5), "戦慄", "Dread", preload=True),
    e("game.riser", "game/riser", 2, 2, G.riser, None, ("peak", -1.5), None, None),
    e("game.capture", "game/capture", 1, 2, G.capture, None, ("peak", -1.0), "連れ去られる", "You are taken"),
    e("game.wake", "game/wake", 1, 2, G.wake, None, ("peak", -3), "意識が戻る", "You come to"),
    e("game.checkpoint", "game/checkpoint", 1, 2, G.checkpoint, None, ("peak", -4), None, None),
    e("game.area", "game/area", 1, 2, G.area, None, ("peak", -2), None, None),
    e("game.fog_lift", "game/fog_lift", 1, 2, G.fog_lift, None, ("peak", -3), "霧が晴れていく", "The fog lifts"),
    e("game.loop", "game/loop", 1, 2, G.loop, None, ("peak", -3), "「…おかえり」", "\"...Welcome back.\""),
    e("game.car", "game/car", 1, 2, G.car, None, ("peak", -2), "車のエンジンがかかる", "The car starts"),
    e("game.title_hit", "game/title_hit", 1, 2, G.title_hit, None, ("peak", -1.5), None, None),
    e("game.kamikakushi", "game/kamikakushi", 1, 2, G.kamikakushi, None, ("peak", -1.5), None, None),
    # ------------------------------------------------------------ extras (not in DESIGN.md section 7)
    e("amb.stone_grind", "amb/stone_grind", 2, 1, A.stone_grind, 16, ("peak", -3), "石の擦れる音", "Stone grinds"),
    e("amb.bridge_creak", "amb/bridge_creak", 4, 1, A.bridge_creak, 16, ("peak", -3), "吊り橋が軋む", "The bridge creaks"),
    e("amb.under_bridge", "amb/under_bridge", 2, 1, A.under_bridge, 24, ("peak", -3), "橋の下で何かが動く", "Something moves under the bridge"),
    e("phone.mitsuketa", "phone/mitsuketa", 1, 2, P.mitsuketa, None, ("peak", -4), "スマホから「みつけた」", "The phone whispers: \"Found you\""),
    e("oyama.recoil", "oyama/recoil", 2, 1, MO.oyama_recoil, 64, ("peak", -1.5), "御山様が怯む", "It recoils from the fire"),
]

EXTRAS = ["amb.stone_grind", "amb.bridge_creak", "amb.under_bridge", "phone.mitsuketa", "oyama.recoil"]


def files(entry):
    if entry.count == 1:
        return [(entry.path, 1)]
    return [(f"{entry.path}_{i}", i) for i in range(1, entry.count + 1)]
