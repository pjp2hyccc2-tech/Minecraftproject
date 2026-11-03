"""The complete ひとりかくれんぼ ―HITORI KAKURENBO― sound list (docs/DESIGN.md section 8): generator, level
target, attenuation, loop flag, Vorbis quality and subtitles for every event.  Generators are 'module:function'
strings, resolved when a file is rendered.

Level plan ("real dynamic range": the apartment at 3 a.m. is silent, then violence).  Minecraft can only turn a
sound down (volume <= 1), so every file is authored at its own absolute loudness:
  silence bed -52 LUFS, closet bed -45, bath -41, apartment -39, outside -33 (rain is louder out there)
  the CRT static -27 LUFS (mono, at the TV: the constant bed of the night), fridge / clock / rain on the glass -30s
  intimate ritual Foley peaks -14 .. -6 dBFS, the knife into the doll / dishes / the doll's shriek at -1 dBFS
  the player's own voice -20 LUFS, the score -36 (prep) .. -21 (chase), jump scares maximised to -1 dBTP."""
from collections import namedtuple
import importlib

# event : sound event id (registered as hitori:<event>)
# path  : file path under assets/hitori/sounds (variants get _1.._n)
# count : number of variants
# ch    : 1 = mono (positional), 2 = stereo (beds / music / UI / the player's own head)
# fn    : 'module:function', function(rng, index) -> numpy signal
# att   : attenuation_distance for positional (mono) sounds, None otherwise
# norm  : ('peak', dB[, max_crest_dB])     sample peak (true peak verified <= -1 dBTP)
#         ('lufs', LUFS, peak_cap_dB)       integrated loudness (BS.1770), brickwalled at the cap
#         ('dense', rms_dB, true_peak_dB)   maximised, true peak verified on the decoded OGG
# loop  : must loop seamlessly (built circularly; checked in report.txt)
# q     : Vorbis quality
# ja/en : subtitle text (lang keys subtitles.hitori.<event>)
# extra : sub=False -> sounds.json carries no "subtitle" (non-diegetic score / UI); seam='start' keeps the loop's
#         own start (rhythmic loops); preload=True; stream=False keeps a long loop a static buffer (Minecraft has
#         only 8 streaming channels: beds and the score need them)
E = namedtuple("E", "event path count ch fn att norm loop q ja en extra")


def e(event, count, ch, fn, att, norm, ja, en, loop=False, q=4, path=None, **extra):
    return E(event, path or event.replace(".", "/"), count, ch, fn, att, norm, loop, q, ja, en, extra)


A, R, VO, D, H, BO, G, MU, MB = ("sfx_amb", "sfx_ritual", "sfx_voice", "sfx_doll", "sfx_house", "sfx_body",
                                 "sfx_game", "music", "music_box")

CATALOG = [
    # ------------------------------------------------------------ beds (stereo loops) + the CRT static (mono loop)
    e("amb.apartment", 1, 2, f"{A}:apartment", None, ("lufs", -39, -12), "雨が窓を打つ", "Rain on the window", loop=True, q=3),
    e("amb.closet", 1, 2, f"{A}:closet", None, ("lufs", -45, -18), "押し入れの中の静けさ", "Muffled quiet inside the closet", loop=True, q=3),
    e("amb.bath", 1, 2, f"{A}:bath", None, ("lufs", -41, -12), "浴槽に滴が落ちる", "Drips fall into the bathtub", loop=True, q=3),
    e("amb.outside", 1, 2, f"{A}:outside", None, ("lufs", -33, -9), "雨の夜の外廊下", "Rain on the outdoor corridor", loop=True, q=3),
    e("amb.tv_static", 1, 1, f"{A}:tv_static", 16, ("lufs", -27, -6), "テレビの砂嵐", "TV static hisses", loop=True, q=6,
      stream=False, preload=True),
    e("amb.silence", 1, 2, f"{A}:silence", None, ("lufs", -52, -30), "静寂", "Silence", loop=True, q=3, sub=False),
    # ------------------------------------------------------------ positional loops (mono)
    e("fixture.fridge", 1, 1, f"{A}:fridge", 8, ("lufs", -31, -10), "冷蔵庫が唸る", "Fridge hums", loop=True),
    e("fixture.clock", 1, 1, f"{A}:clock", 8, ("peak", -12), "時計が時を刻む", "Clock ticks", loop=True, seam="start"),
    e("fixture.tap_bath", 1, 1, f"{A}:tap_bath", 12, ("lufs", -21, -4), "浴槽に水が溜まっていく", "The bathtub fills", loop=True),
    e("fixture.tap", 1, 1, f"{A}:tap", 10, ("lufs", -23, -5), "蛇口から水が流れる", "Tap runs", loop=True),
    e("fixture.washing_machine", 1, 1, f"{A}:washing_machine", 14, ("lufs", -23, -5), "洗濯機が回っている", "Washing machine churns", loop=True, seam="start"),
    e("fixture.rain_window", 1, 1, f"{A}:rain_window", 10, ("lufs", -30, -9), "雨粒が窓ガラスを叩く", "Rain taps the glass", loop=True),
    e("doll.drag_loop", 1, 1, f"{D}:drag_loop", 12, ("lufs", -23, -4), "何かが床を這いずる", "Something drags itself across the floor", loop=True),
    e("camera.mic_hiss", 1, 2, f"{A}:mic_hiss", None, ("lufs", -37, -16), "カメラのマイクのノイズ", "Camera mic hiss", loop=True, sub=False),
    # ------------------------------------------------------------ music (stereo)
    e("music.dread", 1, 2, f"{MU}:dread", None, ("lufs", -31, -6), "不穏な音楽", "Dread", loop=True, q=6, sub=False),
    e("music.tension", 1, 2, f"{MU}:tension", None, ("lufs", -29, -5), "緊迫した音楽", "Tension", loop=True, q=5, sub=False),
    e("music.danger", 1, 2, f"{MU}:danger", None, ("lufs", -26, -4), "危険が迫る音楽", "Danger", loop=True, q=5, seam="start", sub=False),
    e("music.chase", 1, 2, f"{MU}:chase", None, ("lufs", -21, -1.5), "追われる音楽", "Chase", loop=True, q=5, seam="start", sub=False),
    e("music.title", 1, 2, f"{MB}:title", None, ("lufs", -27, -4), "オルゴールの音楽", "Music box", loop=True, q=5, sub=False),
    e("music.prep", 1, 2, f"{MU}:prep", None, ("lufs", -37, -12), "静かな音楽", "Quiet music", loop=True, q=6, sub=False),
    e("music.ending_good", 1, 2, f"{MB}:ending_good", None, ("lufs", -25, -2), "朝の音楽", "Morning", q=5, sub=False),
    e("music.ending_bad", 1, 2, f"{MB}:ending_bad", None, ("lufs", -28, -3), "止まりかけのオルゴール", "A music box winding down", q=5, sub=False),
    e("music.playback", 1, 2, f"{MU}:playback", None, ("lufs", -33, -10), "録画の再生", "Playback", loop=True, q=5, sub=False),
    # ------------------------------------------------------------ the ritual (mono, intimate Foley)
    e("ritual.cut", 1, 1, f"{R}:cut", 8, ("peak", -6), "鋏が布を裂く", "Scissors cut through fabric"),
    e("ritual.stuffing", 1, 1, f"{R}:stuffing", 8, ("peak", -9), "綿を引き抜く", "Stuffing is pulled out"),
    e("ritual.rice", 1, 1, f"{R}:rice", 8, ("peak", -9.5), "米が流し込まれる", "Rice pours into the doll"),
    e("ritual.clip", 1, 1, f"{R}:clip", 6, ("peak", -6), "爪を切る音", "Nail clipper snaps"),
    e("ritual.sew", 1, 1, f"{R}:sew", 6, ("peak", -14), "針と糸が布を通る", "Needle and thread pull through"),
    e("ritual.thread_wind", 1, 1, f"{R}:thread_wind", 8, ("peak", -13), "赤い糸を巻きつける", "Red thread is wound around"),
    e("ritual.splash", 1, 1, f"{R}:splash", 12, ("peak", -2), "人形が水に沈む", "The doll splashes into the water"),
    e("ritual.stab", 1, 1, f"{R}:stab", 12, ("peak", -1), "刃物が人形に突き刺さる", "The knife plunges into the doll", preload=True),
    e("ritual.salt_pour", 1, 1, f"{R}:salt_pour", 6, ("peak", -11), "塩が水に落ちる", "Salt pours into water"),
    e("ritual.spit", 1, 2, f"{R}:spit", None, ("peak", -3), "塩水を吹きかける", "Salt water is spat out"),
    e("ritual.burn_ignite", 1, 1, f"{R}:burn_ignite", 10, ("peak", -3), "火がつく", "Flames catch"),
    e("ritual.burn_loop", 1, 1, f"{R}:burn_loop", 10, ("lufs", -23, -3), "人形が燃えている", "The doll burns, crackling", loop=True),
    # ------------------------------------------------------------ the player's ritual lines (stereo: your own voice)
    e("voice.first_oni", 1, 2, f"{VO}:first_oni", None, ("lufs", -20, -3), "「最初の鬼は、わたしだから」", "\"The first 'it' is me.\""),
    e("voice.mitsuketa", 1, 2, f"{VO}:mitsuketa", None, ("lufs", -19, -3), "「みーちゃん、みつけた」", "\"Mii-chan... found you.\""),
    e("voice.next_oni", 1, 2, f"{VO}:next_oni", None, ("lufs", -20, -3), "「次は、みーちゃんが鬼だから」", "\"Next, Mii-chan is 'it'.\""),
    e("voice.watashi_kachi", 1, 2, f"{VO}:watashi_kachi", None, ("lufs", -19, -3), "「わたしの勝ち」", "\"I win.\""),
    e("voice.count", 1, 2, f"{VO}:count", None, ("lufs", -23, -4), "「いち、に、さん……じゅう」", "\"One, two, three... ten.\""),
    # ------------------------------------------------------------ doors (mono)
    e("door.front_open", 1, 1, f"{H}:front_open", 16, ("peak", -3), "玄関の鉄扉が開く", "Steel front door opens"),
    e("door.front_close", 1, 1, f"{H}:front_close", 16, ("peak", -1.5), "玄関の鉄扉が閉まる", "Steel front door slams shut"),
    e("door.interior_open", 1, 1, f"{H}:interior_open", 8, ("peak", -6), "ドアが開く", "Door opens"),
    e("door.interior_close", 1, 1, f"{H}:interior_close", 8, ("peak", -4), "ドアが閉まる", "Door closes"),
    e("door.fusuma", 3, 1, f"{H}:fusuma", 8, ("peak", -7), "襖が滑る", "Fusuma slides"),
    e("door.fusuma_crack", 1, 1, f"{H}:fusuma_crack", 4, ("peak", -14), "襖がわずかに開く", "Fusuma slides open a crack"),
    e("door.bath_fold", 1, 1, f"{H}:bath_fold", 8, ("peak", -5), "浴室の折れ戸", "Bathroom folding door"),
    e("door.locked", 1, 1, f"{H}:locked", 6, ("peak", -5), "鍵がかかっている", "Locked"),
    # ------------------------------------------------------------ fixtures (mono)
    e("fixture.switch", 2, 1, f"{H}:switch", 6, ("peak", -9), "スイッチの音", "Light switch clicks"),
    e("fixture.pull_cord", 1, 1, f"{H}:pull_cord", 6, ("peak", -9), "紐スイッチを引く", "Pull cord clicks"),
    e("fixture.breaker_off", 1, 1, f"{H}:breaker_off", 12, ("peak", -3), "ブレーカーが落ちる", "Breaker snaps off"),
    e("fixture.breaker_on", 1, 1, f"{H}:breaker_on", 12, ("peak", -3), "ブレーカーが上がる", "Breaker snaps on"),
    e("fixture.tv_on", 1, 1, f"{H}:tv_on", 12, ("peak", -4), "ブラウン管テレビがつく", "CRT TV thunks on"),
    e("fixture.tv_channel", 1, 1, f"{H}:tv_channel", 12, ("peak", -5), "チャンネルが変わる", "Channel changes"),
    e("fixture.tv_voice", 3, 1, f"{H}:tv_voice", 12, ("peak", -5), "テレビから声がする", "Voices from the TV"),
    e("fixture.intercom", 1, 1, f"{H}:intercom", 24, ("peak", -3), "インターホンが鳴る", "Intercom chimes", preload=True),
    e("fixture.knock_door", 3, 1, f"{H}:knock_door", 24, ("peak", -2), "玄関の扉を叩く音", "Knocking on the front door"),
    e("fixture.knock_closet", 3, 1, f"{H}:knock_closet", 8, ("peak", -3), "襖を叩く音", "Knocking on the closet"),
    e("fixture.thing_fall", 3, 1, f"{H}:thing_fall", 16, ("peak", -4), "台所で何かが落ちる", "Something falls in the kitchen"),
    e("fixture.dish_break", 1, 1, f"{H}:dish_break", 24, ("peak", -1), "皿が割れる", "A dish shatters"),
    e("fixture.lamp_swing", 1, 1, f"{H}:lamp_swing", 6, ("peak", -9), "吊り下げ灯が軋む", "Hanging lamp creaks"),
    e("fixture.water_slosh", 3, 1, f"{H}:water_slosh", 10, ("peak", -6), "浴槽の水が揺れる", "Water sloshes in the tub"),
    e("fixture.washing_start", 1, 1, f"{H}:washing_start", 12, ("peak", -4), "洗濯機が動き出す", "Washing machine starts"),
    e("fixture.creak", 4, 1, f"{H}:creak", 16, ("peak", -5), "家鳴り", "The house creaks"),
    e("fixture.footsteps_ceiling", 2, 1, f"{H}:footsteps_ceiling", 24, ("peak", -7), "天井裏の足音", "Footsteps above the ceiling"),
    # ------------------------------------------------------------ phone, camera, laptop, items
    e("phone.vibrate", 1, 1, f"{H}:phone_vibrate", 6, ("peak", -5), "スマホが震える", "Phone vibrates"),
    e("phone.ring_own", 1, 2, f"{H}:phone_ring_own", None, ("lufs", -20, -3), "着信音が鳴る", "Ringtone plays", loop=True, seam="start"),
    e("phone.notify", 1, 2, f"{H}:phone_notify", None, ("peak", -6), "メッセージの通知", "Message notification"),
    e("phone.tap", 1, 2, f"{H}:phone_tap", None, ("peak", -14), "画面をタップ", "Tap", sub=False),
    e("camera.place", 1, 1, f"{H}:camera_place", 6, ("peak", -6), "カメラを設置する", "Camera set down"),
    e("camera.app_open", 1, 2, f"{H}:camera_app_open", None, ("peak", -9), "カメラアプリを開く", "Camera app opens", sub=False),
    e("camera.app_close", 1, 2, f"{H}:camera_app_close", None, ("peak", -10), "カメラアプリを閉じる", "Camera app closes", sub=False),
    e("camera.switch", 1, 2, f"{H}:camera_switch", None, ("peak", -8), "カメラを切り替える", "Camera switches", sub=False),
    e("camera.glitch", 3, 2, f"{H}:camera_glitch", None, ("peak", -8), "映像が乱れる", "The feed glitches"),
    e("camera.fall", 1, 1, f"{H}:camera_fall", 8, ("peak", -4), "カメラが倒れる", "Camera falls over"),
    e("laptop.open", 1, 2, f"{H}:laptop_open", None, ("peak", -10), "ノートパソコンを開く", "Laptop opens"),
    e("laptop.fan", 1, 2, f"{H}:laptop_fan", None, ("lufs", -43, -20), "ファンが回る", "Laptop fan whirs", loop=True, sub=False),
    e("item.lighter", 1, 1, f"{H}:lighter", 6, ("peak", -7), "ライターの音", "Lighter flicks"),
    e("item.paper", 2, 2, f"{H}:paper", None, ("peak", -11, 20), "紙の音", "Paper rustles"),
    e("item.cup", 1, 1, f"{H}:cup", 6, ("peak", -7), "コップを置く", "Glass set down"),
    # ------------------------------------------------------------ the player's body
    e("player.heartbeat", 1, 1, f"{BO}:heartbeat", 8, ("peak", -2), "心臓の鼓動", "Heartbeat", preload=True),
    e("player.breath_fear", 3, 1, f"{BO}:breath_fear", 8, ("peak", -7), "震える息", "Trembling breath"),
    e("player.breath_hold", 1, 2, f"{BO}:breath_hold", None, ("peak", -7), "息を止める", "Breath held"),
    e("player.gasp", 2, 1, f"{BO}:gasp", 8, ("peak", -3), "息を吐き出す", "Gasp"),
    e("player.breath_mouth", 2, 1, f"{BO}:breath_mouth", 8, ("peak", -9), "口に水を含んだ息", "Breathing through a mouthful of water"),
    e("player.swallow", 1, 2, f"{BO}:swallow", None, ("peak", -8), "ごくりと飲み込む", "Gulp"),
    e("player.step_tatami", 4, 1, f"{BO}:step_tatami", 16, ("peak", -11, 22), "畳を踏む足音", "Footsteps on tatami"),
    e("player.step_floor", 4, 1, f"{BO}:step_floor", 16, ("peak", -8, 22), "床板の足音", "Footsteps on floorboards"),
    e("player.step_tile", 4, 1, f"{BO}:step_tile", 16, ("peak", -8, 22), "タイルの足音", "Footsteps on tiles"),
    e("player.step_wet", 4, 1, f"{BO}:step_wet", 16, ("peak", -8, 22), "濡れた足音", "Wet footsteps"),
    # ------------------------------------------------------------ the doll 鬼 (mono)
    e("doll.step", 4, 1, f"{D}:step", 10, ("peak", -6, 22), "濡れた柔らかい足音", "Soft, wet footsteps"),
    e("doll.crawl", 3, 1, f"{D}:crawl", 12, ("peak", -4), "何かが素早く這う", "Something crawls fast"),
    e("doll.drip", 3, 1, f"{D}:drip", 6, ("peak", -9), "水が滴る", "Water drips"),
    e("doll.rice", 3, 1, f"{D}:rice", 6, ("peak", -9), "中で米が擦れる", "Rice shifts inside it"),
    e("doll.giggle", 4, 1, f"{VO}:doll_giggle", 16, ("peak", -3), "くすくす笑う声", "Giggling"),
    e("doll.voice_mitsuketa", 1, 1, f"{VO}:doll_mitsuketa", 12, ("peak", -2), "「みつけた」", "\"Found you.\"", preload=True),
    e("doll.voice_doko", 2, 1, f"{VO}:doll_doko", 12, ("peak", -3), "「どこ？」", "\"Where are you?\""),
    e("doll.voice_tsugi", 1, 2, f"{VO}:doll_tsugi", None, ("peak", -1.5), "「つぎは、あなたがおに」", "\"Next... you are 'it'.\""),
    e("doll.knife_drag", 2, 1, f"{D}:knife_drag", 12, ("peak", -4), "刃物が床を擦る", "A knife scrapes the floor"),
    e("doll.knife_stab", 1, 1, f"{D}:knife_stab", 12, ("peak", -1), "刃物が襖を貫く", "A knife stabs through the fusuma", preload=True),
    e("doll.shriek", 1, 1, f"{D}:shriek", 24, ("peak", -1), "人形の絶叫", "The doll shrieks", preload=True),
    e("doll.breath", 2, 1, f"{D}:breath", 6, ("peak", -6), "襖の向こうの湿った息", "Wet breathing beyond the fusuma"),
    # ------------------------------------------------------------ the woman, the whispers
    e("woman.whisper", 3, 1, f"{VO}:woman_whisper", 6, ("peak", -4), "「……そこにいるの」", "\"...Are you there?\""),
    e("woman.breath", 1, 1, f"{D}:woman_breath", 4, ("peak", -6), "女の息遣い", "A woman's breath"),
    e("whisper.name", 2, 2, f"{VO}:whisper_name", None, ("peak", -4), "名前を囁く声", "Someone whispers your name"),
    # ------------------------------------------------------------ game (stereo)
    e("game.jumpscare", 2, 2, f"{G}:jumpscare", None, ("dense", -7.5, -1.0), "絶叫", "Scream", preload=True, post_rough=(0.55, 72.0, 0.5)),
    e("game.stinger", 4, 2, f"{G}:stinger", None, ("peak", -1.5), "戦慄", "Dread", preload=True),
    e("game.riser", 2, 2, f"{G}:riser", None, ("peak", -1.5), "高まる緊張", "Rising tension", sub=False),
    e("game.step_done", 1, 2, f"{G}:step_done", None, ("peak", -12), "手順を終えた", "Step done", sub=False),
    e("game.three_am", 1, 2, f"{G}:three_am", None, ("peak", -3), "時計が三時を打つ", "The clock strikes three"),
    e("game.title_hit", 1, 2, f"{G}:title_hit", None, ("peak", -1.5), "タイトル", "Title", sub=False),
    e("game.morning", 1, 2, f"{G}:morning", None, ("peak", -5), "朝が来る", "Morning comes"),
]

EXTRAS = []


def files(entry):
    if entry.count == 1:
        return [(entry.path, 1)]
    return [(f"{entry.path}_{i}", i) for i in range(1, entry.count + 1)]


_FN = {}


def resolve(entry):
    """The generator function of an entry."""
    if callable(entry.fn):
        return entry.fn
    f = _FN.get(entry.fn)
    if f is None:
        mod, name = entry.fn.split(":")
        f = getattr(importlib.import_module(mod), name)
        _FN[entry.fn] = f
    return f
