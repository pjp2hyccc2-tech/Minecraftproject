"""The complete MEIKON sound list (DESIGN.md section 5) with generator, level
target, attenuation and subtitles for every event."""
from collections import namedtuple

import music as MU
import sfx_ambient as A
import sfx_bride as B
import sfx_game as G
import sfx_misc as M

# event      : sound event id (registered as meikon:<event>)
# path       : file path under assets/meikon/sounds (variants get _1.._n)
# count      : number of variants
# ch         : 1 = mono (positional), 2 = stereo (UI / music)
# fn         : generator(rng, index) -> numpy signal
# att        : attenuation_distance for positional sounds (None for stereo)
# norm       : ('peak', dB[, max_crest_dB]) | ('rms', dB, peak_cap_dB) |
#              ('dense', rms_dB, true_peak_dB)  (maximised, peak verified on the decoded OGG)
# loop       : must loop seamlessly
# q          : vorbis quality
# ja / en    : subtitle text (None = no subtitle, e.g. menu music)
# extra      : additional per-sound json fields
E = namedtuple("E", "event path count ch fn att norm loop q ja en extra")


def e(event, path, count, ch, fn, att, norm, ja, en, loop=False, q=4, **extra):
    return E(event, path, count, ch, fn, att, norm, loop, q, ja, en, extra)


CATALOG = [
    # ---------------------------------------------------------------- ambient
    e("ambient.drone", "ambient/drone", 1, 2, A.drone, None, ("rms", -20, -4), "不気味な低い響き", "Eerie drone", loop=True, q=6),
    e("ambient.drone_tense", "ambient/drone_tense", 1, 2, A.drone_tense, None, ("rms", -19, -3), "張り詰めた響き", "Tense drone", loop=True, q=6),
    e("ambient.wind", "ambient/wind", 1, 2, A.wind, None, ("rms", -21, -3), "隙間風が鳴る", "Wind whistles through gaps", loop=True),
    e("ambient.koto", "ambient/koto", 4, 2, A.koto_phrase, None, ("peak", -4), "琴の音", "Koto plays"),
    e("ambient.creak", "ambient/creak", 6, 1, A.creak, 16, ("peak", -3), "床が軋む", "Floorboards creak"),
    e("ambient.footsteps_above", "ambient/footsteps_above", 3, 1, A.footsteps_above, 20, ("peak", -3), "頭上で足音がする", "Footsteps overhead"),
    e("ambient.knock", "ambient/knock", 4, 1, A.knock, 16, ("peak", -3), "戸を叩く音", "Knocking"),
    e("ambient.whisper", "ambient/whisper", 6, 1, A.whisper, 6, ("peak", -8), "耳元で囁く声", "Something whispers"),
    e("ambient.door_slam", "ambient/door_slam", 3, 1, A.door_slam, 32, ("peak", -3), "遠くで戸が閉まる", "Distant door slams"),
    e("ambient.music_box", "ambient/music_box", 1, 1, A.music_box, 24, ("peak", -4), "オルゴールが鳴る", "Music box plays"),
    e("ambient.crying", "ambient/crying", 2, 1, A.crying, 24, ("peak", -5), "すすり泣く声", "Woman sobs"),
    e("ambient.clock_chime", "ambient/clock_chime", 1, 1, A.clock_chime, 32, ("peak", -3), "柱時計が鳴る", "Clock strikes"),
    e("ambient.temple_bell", "ambient/temple_bell", 1, 1, A.temple_bell, 64, ("peak", -3), "遠くで梵鐘が鳴る", "Distant temple bell"),
    e("ambient.drip", "ambient/drip", 4, 1, A.drip, 16, ("peak", -5), "水が滴る", "Water drips"),
    e("ambient.scratch", "ambient/scratch", 3, 1, A.scratch, 16, ("peak", -4), "壁の向こうで引っ掻く音", "Scratching behind the wall"),
    e("ambient.thud", "ambient/thud", 3, 1, A.thud, 20, ("peak", -3), "鈍い物音", "Heavy thud"),
    e("ambient.tinnitus", "ambient/tinnitus", 1, 1, A.tinnitus, 16, ("peak", -14), "耳鳴り", "Ears ring"),
    # ---------------------------------------------------------------- bride
    e("bride.hum", "bride/hum", 1, 1, B.hum, 24, ("rms", -17, -3), "花嫁のハミング", "Bride hums", loop=True),
    e("bride.step", "bride/step", 6, 1, B.step, 16, ("peak", -5), "花嫁の足音", "Bride's footsteps"),
    e("bride.screech", "bride/screech", 2, 1, B.screech, 48, ("peak", -1), "花嫁の絶叫", "Bride shrieks", preload=True),
    e("bride.breath", "bride/breath", 3, 1, B.breath, 8, ("peak", -8, 20), "掠れた息遣い", "Raspy breathing"),
    e("bride.rustle", "bride/rustle", 3, 1, B.rustle, 12, ("peak", -9), "衣擦れの音", "Silk rustles"),
    e("bride.laugh", "bride/laugh", 2, 1, B.laugh, 16, ("peak", -5), "花嫁の含み笑い", "Bride chuckles"),
    e("bride.door", "bride/door", 1, 1, B.door, 24, ("peak", -1.5), "襖が乱暴に開く", "Door flung open"),
    # ---------------------------------------------------------------- doll / shadow
    e("doll.move", "doll/move", 5, 1, M.doll_move, 12, ("peak", -4), "人形がカタリと鳴る", "Doll clicks"),
    e("doll.head_turn", "doll/head_turn", 2, 1, M.doll_head_turn, 12, ("peak", -5), "人形の首が軋む", "Doll's head creaks"),
    e("doll.attack", "doll/attack", 1, 1, M.doll_attack, 12, ("peak", -1.5), "人形が襲いかかる", "Doll lunges"),
    e("shadow.whoosh", "shadow/whoosh", 3, 1, M.shadow_whoosh, 12, ("peak", -4), "影がよぎる", "Shadow sweeps past"),
    e("shadow.drone", "shadow/drone", 1, 1, M.shadow_drone, 12, ("rms", -20, -6), "影の唸り", "Shadow hums", loop=True, q=6),
    # ---------------------------------------------------------------- player
    e("player.heartbeat", "player/heartbeat", 1, 1, M.heartbeat, 8, ("peak", -3), "心臓の鼓動", "Heartbeat", preload=True),
    e("player.breath_fear", "player/breath_fear", 3, 1, M.breath_fear, 8, ("peak", -8), "震える息", "Trembling breath"),
    e("player.gasp", "player/gasp", 2, 1, M.gasp, 8, ("peak", -5), "息を呑む", "Gasp"),
    e("player.breath_exhausted", "player/breath_exhausted", 2, 1, M.breath_exhausted, 12, ("peak", -6), "荒い息", "Panting"),
    # ---------------------------------------------------------------- game
    e("game.bell_chime", "game/bell_chime", 3, 1, G.bell_chime, 24, ("peak", -3), "鈴が鳴る", "Bell jingles"),
    e("game.bell_get", "game/bell_get", 1, 2, G.bell_get, None, ("peak", -2), "鈴を手に入れた", "Bell obtained"),
    e("game.jumpscare", "game/jumpscare", 2, 2, G.jumpscare, None, ("dense", -8.0, -0.5), "絶叫", "Scream", preload=True),
    e("game.stinger", "game/stinger", 3, 2, G.stinger, None, ("peak", -1.5), "戦慄", "Dread", preload=True),
    e("game.chase", "game/chase", 1, 2, G.chase, None, ("rms", -15, -2), "何かが追ってくる", "Something is chasing you", loop=True),
    e("game.phase", "game/phase", 1, 2, G.phase, None, ("peak", -2), "何かが変わった", "Something has changed"),
    e("game.death", "game/death", 1, 2, G.death, None, ("peak", -2), "意識が遠のく", "You black out"),
    e("game.fusuma", "game/fusuma", 3, 1, G.fusuma, 16, ("peak", -4), "襖が滑る", "Sliding door"),
    e("game.closet", "game/closet", 2, 1, G.closet, 16, ("peak", -4), "押入れの戸が軋む", "Closet door"),
    e("game.lantern_on", "game/lantern_on", 1, 1, G.lantern_on, 12, ("peak", -5, 20), "提灯に火が灯る", "Lantern lit"),
    e("game.lantern_off", "game/lantern_off", 1, 1, G.lantern_off, 12, ("peak", -7), "火を吹き消す", "Lantern blown out"),
    e("game.paper", "game/paper", 2, 1, G.paper, 12, ("peak", -7, 20), "紙をめくる音", "Paper rustles"),
    e("game.ofuda_burn", "game/ofuda_burn", 1, 1, G.ofuda_burn, 16, ("peak", -4, 20), "護符が燃え上がる", "Ofuda burns"),
    e("game.salt", "game/salt", 1, 1, G.salt, 12, ("peak", -5, 22), "清め塩を撒く", "Salt scattered"),
    e("game.marble", "game/marble", 3, 1, G.marble, 16, ("peak", -4), "おはじきが跳ねる", "Marble bounces"),
    e("game.drawer", "game/drawer", 2, 1, G.drawer, 12, ("peak", -4), "引き出しを開ける", "Drawer slides"),
    e("game.unlock", "game/unlock", 1, 1, G.unlock, 16, ("peak", -3), "錠が外れる", "Lock clunks open"),
    e("game.candle", "game/candle", 1, 1, G.candle, 8, ("peak", -8, 20), "蝋燭が灯る", "Candle lit"),
    e("game.puzzle_fail", "game/puzzle_fail", 1, 1, G.puzzle_fail, 16, ("peak", -3), "火が吹き消される", "Candles snuffed out"),
    e("game.well", "game/well", 2, 1, G.well, 16, ("peak", -4), "井戸の滑車が軋む", "Well pulley creaks"),
    e("game.gate_open", "game/gate_open", 1, 2, G.gate_open, None, ("peak", -2), "鳥居が目覚める", "Torii awakens"),
    e("game.ritual", "game/ritual", 1, 2, G.ritual, None, ("peak", -2), "鈴が鳴り響く", "Bells ring faster"),
    # ---------------------------------------------------------------- music
    e("music.title", "music/title", 1, 2, MU.title, None, ("rms", -20, -3), None, None, loop=True, q=5),
    e("music.gagaku", "music/gagaku", 1, 2, MU.gagaku, None, ("rms", -20, -3), "雅楽が聞こえる", "Wedding music plays", loop=True, q=5),
    e("music.ending", "music/ending", 1, 2, MU.ending, None, ("peak", -3), None, None, q=5),
    e("music.bad_ending", "music/bad_ending", 1, 2, MU.bad_ending, None, ("peak", -2), None, None, q=5),
]


def files(entry):
    if entry.count == 1:
        return [(entry.path, 1)]
    return [(f"{entry.path}_{i}", i) for i in range(1, entry.count + 1)]
