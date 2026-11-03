"""The complete 霊撮 ―REISATSU― sound list (docs/DESIGN.md section 8): generator, level target, attenuation,
loop flag, Vorbis quality and subtitles for every event.  Generators are 'module:function' strings, resolved
when a file is rendered."""
from collections import namedtuple
import importlib

# event : sound event id (registered as reisatsu:<event>)
# path  : file path under assets/reisatsu/sounds (variants get _1.._n)
# count : number of variants
# ch    : 1 = mono (positional), 2 = stereo (beds / music / UI)
# fn    : 'module:function', function(rng, index) -> numpy signal
# att   : attenuation_distance for positional (mono) sounds, None otherwise
# norm  : ('peak', dB[, max_crest_dB])     sample peak (true peak verified <= -1 dBTP)
#         ('lufs', LUFS, peak_cap_dB)       integrated loudness (BS.1770), brickwalled at the cap
#         ('dense', rms_dB, true_peak_dB)   maximised, true peak verified on the decoded OGG
# loop  : must loop seamlessly (built circularly; checked in report.txt)
# q     : Vorbis quality
# ja/en : subtitle text (lang keys subtitles.reisatsu.<event>)
# extra : sub=False -> sounds.json carries no "subtitle" (non-diegetic score); seam='start' keeps the loop's
#         own start (rhythmic loops); preload=True
E = namedtuple("E", "event path count ch fn att norm loop q ja en extra")


def e(event, count, ch, fn, att, norm, ja, en, loop=False, q=4, path=None, **extra):
    return E(event, path or event.replace(".", "/"), count, ch, fn, att, norm, loop, q, ja, en, extra)


B, LP, MU, MS, R, PL, SP, G = ("sfx_beds", "sfx_loops", "music", "music_store", "sfx_rest", "sfx_player",
                               "sfx_spirits", "sfx_game")

CATALOG = [
    # ------------------------------------------------------------ ambient beds (stereo loops)
    e("amb.night_road", 1, 2, f"{B}:night_road", None, ("lufs", -33, -8), "雨上がりの国道", "Wet road at night", loop=True, q=3),
    e("amb.dining", 1, 2, f"{B}:dining", None, ("lufs", -41, -14), "閉店後の客席", "Closed dining hall", loop=True, q=3),
    e("amb.kitchen", 1, 2, f"{B}:kitchen", None, ("lufs", -36, -10), "厨房の機械音", "Kitchen machines hum", loop=True, q=3),
    e("amb.restroom", 1, 2, f"{B}:restroom", None, ("lufs", -40, -14), "配管を水が流れる", "Water in the pipes", loop=True, q=3),
    e("amb.back", 1, 2, f"{B}:back", None, ("lufs", -39, -14), "安定器の唸り", "Ballasts hum", loop=True, q=3),
    e("amb.fridge", 1, 2, f"{B}:fridge", None, ("lufs", -29, -8), "冷蔵室の轟音", "Walk-in fridge roars", loop=True, q=3),
    e("amb.silence", 1, 2, f"{B}:silence", None, ("lufs", -48, -26), "静寂", "Silence", loop=True, q=3, sub=False),
    e("amb.closing", 1, 2, f"{B}:closing", None, ("lufs", -31, -8), "閉店の店内", "The store at closing time", loop=True, q=3),
    # ------------------------------------------------------------ positional loops (mono)
    e("amb.fluorescent", 1, 1, f"{LP}:fluorescent", 10, ("lufs", -27, -6), "蛍光灯が唸る", "Fluorescent tube buzzes", loop=True),
    e("amb.vending", 1, 1, f"{LP}:vending", 12, ("lufs", -27, -6), "自動販売機の唸り", "Vending machine hums", loop=True),
    e("amb.sign_buzz", 1, 1, f"{LP}:sign_buzz", 24, ("lufs", -25, -5), "看板が唸り、瞬く", "Sign buzzes and flickers", loop=True),
    e("amb.drip", 4, 1, f"{LP}:drip", 12, ("peak", -6), "水滴が落ちる", "Water drips"),
    e("amb.gutter", 1, 1, f"{LP}:gutter", 16, ("lufs", -25, -5), "雨樋を水が流れる", "Gutter trickles", loop=True),
    e("fixture.faucet_loop", 1, 1, f"{LP}:faucet_loop", 12, ("lufs", -21, -3), "水が流れ続ける", "Tap runs", loop=True),
    e("fixture.hand_dryer", 1, 1, f"{LP}:hand_dryer", 16, ("lufs", -14, -1.5), "ハンドドライヤーが轟く", "Hand dryer roars", loop=True),
    e("fixture.fryer_loop", 1, 1, f"{LP}:fryer_loop", 12, ("lufs", -23, -4), "油が煮え立つ", "Fryer oil bubbles", loop=True),
    e("fixture.gas_loop", 1, 1, f"{LP}:gas_loop", 12, ("lufs", -23, -4), "ガスの炎が燃える", "Gas flame roars", loop=True),
    e("fixture.dishwasher", 1, 1, f"{LP}:dishwasher", 14, ("lufs", -21, -3), "食洗機が動いている", "Dishwasher runs", loop=True),
    e("fixture.tv_static", 1, 1, f"{LP}:tv_static", 12, ("lufs", -22, -4), "テレビの砂嵐", "TV static hisses", loop=True),
    e("fixture.monitor_static", 1, 1, f"{LP}:monitor_static", 8, ("lufs", -28, -6), "監視モニターのノイズ", "Security monitors hiss", loop=True),
    e("fixture.phone_ring", 1, 1, f"{LP}:phone_ring", 24, ("lufs", -18, -4), "電話が鳴る", "Phone rings", loop=True, seam="start"),
    e("spirit.near", 1, 1, f"{LP}:spirit_near", 8, ("lufs", -26, -5), "空気が重く唸る", "The air presses in", loop=True),
    e("camera.sense", 1, 2, f"{LP}:camera_sense", None, ("lufs", -24, -5), "霊感計が反応する", "Spirit meter crackles", loop=True),
    # ------------------------------------------------------------ music (stereo)
    e("music.dread", 1, 2, f"{MU}:dread", None, ("lufs", -29, -5), "不穏な音楽", "Dread", loop=True, q=5, sub=False),
    e("music.tension", 1, 2, f"{MU}:tension", None, ("lufs", -29, -5), "緊迫した音楽", "Tension", loop=True, q=5, sub=False),
    e("music.danger", 1, 2, f"{MU}:danger", None, ("lufs", -27, -4), "危険が迫る音楽", "Danger", loop=True, q=5, seam="start", sub=False),
    e("music.chase", 1, 2, f"{MU}:chase", None, ("lufs", -20, -1.5), "追われる音楽", "Chase", loop=True, q=5, seam="start", sub=False),
    e("music.safe", 1, 2, f"{MU}:safe", None, ("lufs", -30, -5), "静かな音楽", "Quiet music", loop=True, q=5, sub=False),
    e("music.title", 1, 2, f"{MS}:title", None, ("lufs", -27, -4), "オルゴールの音楽", "Music box", loop=True, q=5, sub=False),
    e("music.store", 1, 2, f"{MS}:store", None, ("lufs", -24, -2), "店内BGMが流れる", "Store music plays", loop=True, q=5, seam="start"),
    e("music.store_wrong", 1, 2, f"{MS}:store_wrong", None, ("lufs", -25, -2), "歪んだ店内BGM", "Warped store music", loop=True, q=5),
    e("music.closing", 1, 2, f"{MS}:closing", None, ("lufs", -25, -3), "閉店の音楽が流れる", "Closing-time music plays", loop=True, q=5, seam="start"),
    e("music.exorcism", 1, 2, f"{MU}:exorcism", None, ("lufs", -24, -2), "除霊の調べ", "The rite", q=5, sub=False),
    e("music.ending_good", 1, 2, f"{MS}:ending_good", None, ("lufs", -25, -2), "夜明けの音楽", "Dawn", q=5, sub=False),
    e("music.ending_bad", 1, 2, f"{MS}:ending_bad", None, ("lufs", -27, -3), "壊れたオルゴール", "A dying music box", q=5, sub=False),
    # ------------------------------------------------------------ restaurant one-shots (mono)
    e("door.auto_open", 1, 1, f"{R}:auto_open", 16, ("peak", -3), "自動ドアが開く", "Automatic door opens"),
    e("door.auto_close", 1, 1, f"{R}:auto_close", 16, ("peak", -3), "自動ドアが閉まる", "Automatic door closes"),
    e("door.chime", 2, 1, f"{R}:chime", 32, ("peak", -2), "入店チャイム", "Entrance chime", preload=True),
    e("door.chime_wrong", 2, 1, f"{R}:chime_wrong", 32, ("peak", -5), "歪んだ入店チャイム", "The chime warps"),
    e("door.swing", 3, 1, f"{R}:swing", 12, ("peak", -3), "スイングドアが揺れる", "Swing doors flap"),
    e("door.open", 2, 1, f"{R}:door_open", 12, ("peak", -4), "ドアが開く", "Door opens"),
    e("door.close", 2, 1, f"{R}:door_close", 12, ("peak", -3), "ドアが閉まる", "Door closes"),
    e("door.fridge_open", 1, 1, f"{R}:fridge_open", 12, ("peak", -3), "冷蔵室の扉が開く", "Walk-in door opens"),
    e("door.fridge_close", 1, 1, f"{R}:fridge_close", 12, ("peak", -2), "冷蔵室の扉が閉まる", "Walk-in door thuds shut"),
    e("door.stall_bang", 2, 1, f"{R}:stall_bang", 16, ("peak", -1), "個室の扉が叩きつけられる", "Stall door slams"),
    e("door.locked", 1, 1, f"{R}:locked", 8, ("peak", -4), "鍵がかかっている", "Handle rattles: locked"),
    e("door.unlock", 1, 1, f"{R}:unlock", 8, ("peak", -4), "鍵が開く", "Lock turns"),
    e("door.switch", 1, 1, f"{R}:door_switch", 8, ("peak", -4), "キースイッチが回る", "Key switch clacks"),
    e("fixture.call_button", 2, 1, f"{R}:call_button", 24, ("peak", -5), "呼び出しボタンが鳴る", "Call button chimes", preload=True),
    e("fixture.call_display", 1, 1, f"{R}:call_display", 32, ("peak", -5), "呼び出し表示器のチャイム", "Call display chimes"),
    e("fixture.service_bell", 1, 1, f"{R}:service_bell", 24, ("peak", -4), "呼び鈴が鳴る", "Counter bell rings"),
    e("fixture.pass_bell", 1, 1, f"{R}:pass_bell", 24, ("peak", -2), "デシャップのベルが鳴る", "Pickup bell rings"),
    e("fixture.register_drawer", 1, 1, f"{R}:register_drawer", 12, ("peak", -3), "レジが開く", "Register drawer opens"),
    e("fixture.phone_voice", 2, 2, f"{R}:phone_voice", None, ("peak", -3), "「……二時に……一名で……」", "\"...at two... for one...\""),
    e("fixture.faucet_on", 1, 1, f"{R}:faucet_on", 12, ("peak", -3), "蛇口から水が出る", "Tap turns on"),
    e("fixture.faucet_off", 1, 1, f"{R}:faucet_off", 12, ("peak", -4), "水が止まる", "Tap turns off"),
    e("fixture.drink_pour", 2, 1, f"{R}:drink_pour", 12, ("peak", -3), "ドリンクバーが注ぐ", "Drink bar pours"),
    e("fixture.ice_drop", 2, 1, f"{R}:ice_drop", 12, ("peak", -3), "製氷機の氷が落ちる", "Ice drops"),
    e("fixture.gas_ignite", 1, 1, f"{R}:gas_ignite", 12, ("peak", -2), "ガスに火がつく", "Gas ignites"),
    e("fixture.breaker_off", 1, 1, f"{R}:breaker_off", 24, ("peak", -1), "ブレーカーが落ちる", "Breaker trips, power dies"),
    e("fixture.breaker_on", 1, 1, f"{R}:breaker_on", 24, ("peak", -1), "ブレーカーが入る", "Breaker on, tubes ping"),
    e("fixture.light_flicker", 3, 1, f"{R}:light_flicker", 12, ("peak", -4), "照明が明滅する", "Lights flicker"),
    e("fixture.light_burst", 2, 1, f"{R}:light_burst", 16, ("peak", -1), "蛍光灯が割れる", "Tube bursts"),
    e("fixture.time_card", 1, 1, f"{R}:time_card", 8, ("peak", -3), "タイムカードを打つ", "Time clock punches"),
    e("fixture.locker", 2, 1, f"{R}:locker", 8, ("peak", -3), "ロッカーの扉", "Locker door"),
    e("fixture.chair_scrape", 3, 1, f"{R}:chair_scrape", 16, ("peak", -2), "椅子が引きずられる", "Chair scrapes"),
    e("fixture.chair_fall", 2, 1, f"{R}:chair_fall", 20, ("peak", -1), "椅子が倒れる", "Chair falls over"),
    e("fixture.plate_break", 3, 1, f"{R}:plate_break", 32, ("peak", -1), "皿が割れる", "Plate smashes", preload=True),
    e("fixture.glass_set", 2, 1, f"{R}:glass_set", 10, ("peak", -4), "水の入ったグラスが置かれる", "A glass of water is set down"),
    e("fixture.menu_scatter", 2, 1, f"{R}:menu_scatter", 16, ("peak", -2), "メニューが散らばる", "Menus scatter"),
    e("fixture.pa_chime", 1, 2, f"{R}:pa_chime", None, ("lufs", -19, -4), "店内放送のチャイム", "PA chime"),
    e("fixture.pa_voice", 3, 2, f"{R}:pa_voice", None, ("peak", -3), "店内放送が流れる", "Garbled store announcement"),
    e("fixture.car_pass", 3, 1, f"{R}:car_pass", 48, ("peak", -2), "濡れた道を車が通る", "Car passes on the wet road"),
    e("fixture.motorbike", 1, 1, f"{R}:motorbike", 96, ("peak", -2), "遠くからバイクが近づく", "A motorbike approaches"),
    e("fixture.crash", 1, 1, f"{R}:crash", 96, ("peak", -1), "急ブレーキと衝突音", "Tyres scream, a crash"),
    e("fixture.toilet_flush", 1, 1, f"{R}:toilet_flush", 16, ("peak", -3), "トイレが流れる", "Toilet flushes"),
    e("fixture.stall_knock", 3, 1, f"{R}:stall_knock", 12, ("peak", -2), "個室の中からノック", "Knocking inside the stall"),
    e("fixture.fridge_knock", 3, 1, f"{R}:fridge_knock", 12, ("peak", -3), "冷蔵室の中から叩く音", "Knocking inside the walk-in"),
    e("fixture.ball_bounce", 1, 1, f"{R}:ball_bounce", 16, ("peak", -3), "ボールが弾む", "A ball bounces"),
    e("fixture.toy_squeak", 1, 1, f"{R}:toy_squeak", 8, ("peak", -4), "おもちゃが鳴る", "A toy squeaks"),
    # ------------------------------------------------------------ camera and items
    e("camera.raise", 1, 2, f"{PL}:camera_raise", None, ("peak", -6), "霊写機を構える", "Camera raised"),
    e("camera.lower", 1, 2, f"{PL}:camera_lower", None, ("peak", -6), "霊写機を下ろす", "Camera lowered"),
    e("camera.shutter", 2, 1, f"{PL}:camera_shutter", 10, ("peak", -1), "シャッターとフラッシュ", "Shutter and flash", preload=True),
    e("camera.charge", 1, 2, f"{PL}:camera_charge", None, ("lufs", -27, -12), "フラッシュが充電される", "Flash charges"),
    e("camera.eject", 1, 2, f"{PL}:camera_eject", None, ("lufs", -21, -6), "写真が出てくる", "Photo ejects"),
    e("camera.empty", 1, 2, f"{PL}:camera_empty", None, ("peak", -6), "空撮り", "Dry click: no film"),
    e("camera.zoom", 1, 2, f"{PL}:camera_zoom", None, ("peak", -17), "レンズが動く", "Lens whirs", sub=False),
    e("camera.load", 1, 2, f"{PL}:camera_load", None, ("peak", -5), "フィルムを装填する", "Film loaded"),
    e("photo.develop", 1, 2, f"{PL}:photo_develop", None, ("peak", -8), "写真が浮かび上がる", "The photo develops"),
    e("photo.name", 1, 2, f"{PL}:photo_name", None, ("peak", -4), "名前が浮かび上がる", "A name writes itself"),
    e("photo.fail", 1, 2, f"{PL}:photo_fail", None, ("peak", -5), "写真が歪む", "The photo smears"),
    e("item.flashlight_on", 1, 1, f"{PL}:flashlight_on", 8, ("peak", -5), "懐中電灯をつける", "Flashlight clicks on"),
    e("item.flashlight_off", 1, 1, f"{PL}:flashlight_off", 8, ("peak", -5), "懐中電灯を消す", "Flashlight clicks off"),
    e("item.flashlight_flicker", 3, 1, f"{PL}:flashlight_flicker", 8, ("peak", -6), "懐中電灯が明滅する", "Flashlight flickers"),
    e("item.ofuda_burn", 1, 2, f"{PL}:ofuda_burn", None, ("peak", -2), "お札が燃え上がる", "An ofuda burns up"),
    e("item.key", 1, 1, f"{PL}:key", 8, ("peak", -5), "鍵の音", "Keys jingle"),
    e("item.paper", 2, 2, f"{PL}:paper", None, ("peak", -7, 20), "紙の音", "Paper rustles"),
    # ------------------------------------------------------------ player (mono)
    e("player.heartbeat", 1, 1, f"{PL}:heartbeat", 8, ("peak", -2), "心臓の鼓動", "Heartbeat", preload=True),
    e("player.breath_fear", 3, 1, f"{PL}:breath_fear", 8, ("peak", -6), "震える息", "Trembling breath"),
    e("player.breath_hold", 1, 1, f"{PL}:breath_hold", 8, ("peak", -6), "息を止める", "Breath held"),
    e("player.gasp", 2, 1, f"{PL}:gasp", 8, ("peak", -3), "息を吐き出す", "Gasp"),
    e("player.step_tile", 4, 1, f"{PL}:step_tile", 16, ("peak", -6, 22), "タイルの足音", "Footsteps on tiles"),
    e("player.step_vinyl", 4, 1, f"{PL}:step_vinyl", 16, ("peak", -8, 22), "床の足音", "Footsteps"),
    e("player.step_kitchen", 4, 1, f"{PL}:step_kitchen", 16, ("peak", -6, 22), "厨房の床の足音", "Footsteps on the kitchen floor"),
    e("player.step_wet", 4, 1, f"{PL}:step_wet", 16, ("peak", -7, 22), "濡れた足音", "Footsteps on wet asphalt"),
    e("player.step_glass", 3, 1, f"{PL}:step_glass", 16, ("peak", -4), "破片を踏む", "Glass crunches underfoot"),
    e("player.step_paper", 2, 1, f"{PL}:step_paper", 16, ("peak", -6), "メニューを踏む", "Stepping on menus"),
    # ------------------------------------------------------------ spirits (mono)
    e("visitor.step", 4, 1, f"{SP}:visitor_step", 12, ("peak", -4, 22), "濡れた足を引きずる音", "Wet, dragging footsteps"),
    e("visitor.breath", 2, 1, f"{SP}:visitor_breath", 6, ("peak", -5), "ヘルメットの中の息", "Breathing inside a helmet"),
    e("visitor.visor", 1, 1, f"{SP}:visitor_visor", 8, ("peak", -1.5, 13), "バイザーがひび割れる", "Visor cracks"),
    e("visitor.drip", 2, 1, f"{SP}:visitor_drip", 6, ("peak", -6), "滴り落ちる水", "Water drips off something"),
    e("visitor.whisper", 2, 1, f"{SP}:visitor_whisper", 6, ("peak", -4), "「……まだかな」", "\"...Is she coming...\""),
    e("waitress.step", 3, 1, f"{SP}:waitress_step", 10, ("peak", -6, 22), "規則正しすぎる足音", "Too-regular footsteps"),
    e("waitress.greet", 3, 1, f"{SP}:waitress_greet", 24, ("peak", -2), "「いらっしゃいませ」", "\"Welcome!\""),
    e("waitress.water", 1, 1, f"{SP}:waitress_water", 12, ("peak", -3), "「お冷やを、どうぞ」", "\"Your water...\""),
    e("waitress.hum", 1, 1, f"{SP}:waitress_hum", 12, ("peak", -4), "店の歌を口ずさむ声", "Someone hums the store jingle"),
    e("waitress.tray", 1, 1, f"{SP}:waitress_tray", 16, ("peak", -2), "トレイとグラスの音", "Tray and glass clatter"),
    e("chef.chop", 2, 1, f"{SP}:chef_chop", 20, ("peak", -2), "包丁の音", "Cleaver chops", loop=True, seam="start"),
    e("chef.chop_stop", 1, 1, f"{SP}:chef_chop_stop", 20, ("peak", -1), "包丁の音が止まる", "The chopping stops"),
    e("chef.breath", 2, 1, f"{SP}:chef_breath", 8, ("peak", -4), "煙たい喘ぎ", "Smoky wheezing"),
    e("chef.sizzle", 1, 1, f"{SP}:chef_sizzle", 8, ("peak", -5), "焦げた肌が爆ぜる", "Burnt skin crackles"),
    e("chef.roar", 1, 1, f"{SP}:chef_roar", 32, ("peak", -1), "怒号と包丁の一撃", "A roar, the cleaver strikes", preload=True),
    e("chef.step", 3, 1, f"{SP}:chef_step", 16, ("peak", -3, 22), "重い足音", "Heavy footsteps"),
    e("chef.scrape", 1, 1, f"{SP}:chef_scrape", 12, ("peak", -3), "包丁が鉄を擦る", "Cleaver scrapes steel"),
    e("child.giggle", 4, 1, f"{SP}:child_giggle", 16, ("peak", -3), "子供のくすくす笑い", "A child giggles"),
    e("child.count", 1, 1, f"{SP}:child_count", 24, ("peak", -3), "「いーち、にーい、さーん……」", "\"One... two... three...\""),
    e("child.moui", 1, 1, f"{SP}:child_moui", 32, ("peak", -2), "「もういいかい」", "\"Ready or not?\""),
    e("child.madada", 1, 1, f"{SP}:child_madada", 24, ("peak", -2), "「まーだだよ」", "\"Not yet!\""),
    e("child.mitsuketa", 1, 1, f"{SP}:child_mitsuketa", 16, ("peak", -2), "「みーつけた」", "\"Found you!\"", preload=True),
    e("child.run", 3, 1, f"{SP}:child_run", 16, ("peak", -4), "小さな足が走る", "Small feet run"),
    e("child.hum", 1, 1, f"{SP}:child_hum", 12, ("peak", -4), "子供の鼻歌", "A child hums"),
    e("child.whisper", 1, 1, f"{SP}:child_whisper", 6, ("peak", -4), "「こっちだよ」", "\"Over here...\""),
    e("mirror.sob", 2, 1, f"{SP}:mirror_sob", 10, ("peak", -4), "ガラスの向こうで泣く声", "Sobbing behind glass"),
    e("mirror.whisper", 1, 1, f"{SP}:mirror_whisper", 6, ("peak", -4), "「……うしろ」", "\"...Behind you\""),
    e("mirror.knock", 2, 1, f"{SP}:mirror_knock", 12, ("peak", -2), "鏡の向こうから叩く音", "Knocking from inside the mirror"),
    e("mirror.squeak", 2, 1, f"{SP}:mirror_squeak", 10, ("peak", -8), "ガラスを擦る手", "Palms squeak on glass"),
    e("mirror.scream", 1, 1, f"{SP}:mirror_scream", 24, ("peak", -1), "絶叫、鏡が割れる", "A scream, the mirror cracks", preload=True),
    e("manager.step", 3, 1, f"{SP}:manager_step", 12, ("peak", -5, 22), "革靴の足音", "Leather shoes, measured"),
    e("manager.keys", 1, 1, f"{SP}:manager_keys", 10, ("peak", -4), "腰の鍵束が鳴る", "Keys jingle at a belt"),
    e("manager.welcome", 1, 1, f"{SP}:manager_welcome", 16, ("peak", -3), "「いらっしゃいませ」", "\"...Welcome.\""),
    e("manager.closed", 1, 1, f"{SP}:manager_closed", 32, ("peak", -2), "「本日の営業は、終了いたしました」", "\"We are now closed for the day.\""),
    e("manager.customer", 2, 1, f"{SP}:manager_customer", 16, ("peak", -3), "「お客様……」", "\"Excuse me, sir...\""),
    e("manager.sigh", 1, 1, f"{SP}:manager_sigh", 8, ("peak", -4), "疲れ切ったため息", "An exhausted sigh"),
    e("manager.typing", 1, 1, f"{SP}:manager_typing", 8, ("peak", -6), "キーボードを打つ音", "Typing", loop=True),
    e("manager.chair", 1, 1, f"{SP}:manager_chair", 8, ("peak", -4), "事務椅子が軋む", "Office chair creaks"),
    e("medium.voice", 6, 1, f"{SP}:medium_voice", 8, ("peak", -4), "霊媒師の呟き", "The medium murmurs"),
    e("medium.bell", 1, 1, f"{SP}:medium_bell", 24, ("peak", -6), "りんが鳴る", "A singing bowl rings"),
    e("medium.chant", 1, 1, f"{SP}:medium_chant", 16, ("peak", -3), "読経", "Chanting", loop=True),
    e("medium.clap", 1, 1, f"{SP}:medium_clap", 24, ("peak", -2), "柏手", "Two ritual claps"),
    e("spirit.whisper", 6, 1, f"{SP}:spirit_whisper", 6, ("peak", -4), "耳元の囁き", "Something whispers"),
    e("spirit.notice", 3, 1, f"{SP}:spirit_notice", 16, ("peak", -1), "気付かれた", "It noticed you!", preload=True),
    e("spirit.bound", 1, 1, f"{SP}:spirit_bound", 16, ("peak", -1), "霊が写真に吸い込まれる", "A spirit is pulled into the photo"),
    e("spirit.vanish", 1, 1, f"{SP}:spirit_vanish", 12, ("peak", -4), "気配が消える", "A presence fades"),
    # ------------------------------------------------------------ game (stereo)
    e("game.jumpscare", 3, 2, f"{G}:jumpscare", None, ("dense", -7.5, -1.0), "絶叫", "Scream", preload=True, post_rough=(0.55, 72.0, 0.5)),
    e("game.stinger", 4, 2, f"{G}:stinger", None, ("peak", -1.5), "戦慄", "Dread", preload=True),
    e("game.riser", 2, 2, f"{G}:riser", None, ("peak", -1.5), "高まる緊張", "Rising tension", sub=False),
    e("game.caught", 1, 2, f"{G}:caught", None, ("peak", -1.0), "連れ去られる", "You are taken"),
    e("game.wake", 1, 2, f"{G}:wake", None, ("peak", -3), "意識が戻る", "You come to"),
    e("game.captured", 1, 2, f"{G}:captured", None, ("peak", -6.5), "名前が浮かんだ", "A name appears"),
    e("game.closing", 1, 2, f"{G}:closing", None, ("peak", -1), "閉店の時間", "Closing time"),
    e("game.title_hit", 1, 2, f"{G}:title_hit", None, ("peak", -1.5), "タイトル", "Title", sub=False),
    e("game.release", 1, 2, f"{G}:release", None, ("peak", -2), "霊が光に還る", "A spirit is sent on"),
    e("game.dawn", 1, 2, f"{G}:dawn", None, ("peak", -5.5), "夜が明ける", "Dawn breaks"),
    e("game.ending_bad", 1, 2, f"{G}:ending_bad", None, ("peak", -2), "「いらっしゃいませ。……お一人様ですね」", "\"Welcome... Just one, is it?\""),
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
