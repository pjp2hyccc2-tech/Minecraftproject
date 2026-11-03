#!/usr/bin/env python3
"""Writes assets/hitori/lang/ja_jp.json and en_us.json (owner: game).

Every key the game uses is here; the block names of docs/DESIGN.md §7 too (the world registers the blocks); the sound
subtitles come from tools/audio/subtitles_{ja,en}.json (sound worker) when present. Every registered block, item and
entity must have a name (checked against the registries). Run: python3 tools/gen_lang.py
"""
import json
import os
import re

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.normpath(os.path.join(HERE, "..", "src", "main", "resources", "assets", "hitori", "lang"))
NS = "hitori"

ITEMS = {
    "plush": ("うさぎのぬいぐるみ", "Stuffed Rabbit",
              "白いうさぎ。ボタンの目。子供の頃からある。", "A white rabbit with button eyes. You have had it since you were a child."),
    "plush_open": ("腹を裂いたぬいぐるみ", "Cut-Open Plush",
                   "綿を抜いた。中は空っぽだ。", "The stuffing is out. It is empty inside."),
    "plush_rice": ("米を詰めたぬいぐるみ", "Rice-Filled Plush",
                   "米が詰まって、ずしりと重い。", "Packed with rice, heavy in the hand."),
    "ritual_doll": ("人形", "The Doll",
                    "赤い糸で縫い、残りの糸を巻きつけた。", "Sewn shut with red thread, the rest wound round and round it."),
    "scissors": ("裁ちばさみ", "Scissors", "裁縫箱の中にあった。", "From the sewing box."),
    "rice_scoop": ("米", "Rice", "米びつから一合ほど。", "About a cup of rice from the bin."),
    "red_thread": ("赤い糸と針", "Red Thread and Needle", "赤い糸。縫い終わったら、残りを巻きつける。",
                   "Red thread. When it is sewn, wind the rest around it."),
    "nail_clipper": ("爪切り", "Nail Clipper", "右クリック：爪を切る（ぬいぐるみに入れる）。", "Right-click: clip your nails (into the plush)."),
    "knife": ("包丁", "Kitchen Knife", "台所の包丁。", "The kitchen knife."),
    "salt": ("塩", "Salt", "コップの水に溶かす。", "Dissolve it in a cup of water."),
    "cup_water": ("水の入ったコップ", "Cup of Water", "塩を入れれば塩水になる。", "Add salt to make salt water."),
    "salt_water": ("塩水", "Salt Water", "右クリック：口に含む。隠れている間は、出してはいけない。",
                   "Right-click: take a mouthful. While hiding, do not let it out."),
    "fixed_camera": ("定点カメラ", "Fixed Camera", "ブロックの面に右クリックで設置（向いている方を撮る）。三時までなら、スニーク＋右クリックで回収。",
                     "Right-click a block face to place it (it films where you look). Sneak + right-click picks it up, before 3:00."),
    "laptop": ("ノートパソコン", "Laptop", "右クリック／C：カメラアプリ。押し入れの中でも見られる。", "Right-click / C: the camera app. Usable inside the closet."),
    "phone": ("スマートフォン", "Phone", "右クリック：画面　スニーク＋右クリック：ライト　C：カメラ", "Right-click: screen   Sneak + right-click: light   C: cameras"),
    "phone_light": ("スマートフォン（ライト）", "Phone (light on)", "", ""),
    "lighter": ("ライター", "Lighter", "引き出しの奥にあった。", "From the back of the drawer."),
    "printout": ("手順書", "The Printout", "掲示板のまとめを印刷したもの。J でも読める。", "A printout of the forum thread. Also: J."),
}

ENTITIES = {"oni_doll": ("鬼", "The Oni"), "woman": ("女", "The Woman"), "camera": ("定点カメラ", "Fixed Camera")}

ROOMS = {
    "genkan": ("玄関", "Entrance"), "hall": ("廊下", "Hall"), "toilet": ("トイレ", "Toilet"), "bath": ("浴室", "Bathroom"),
    "washroom": ("脱衣所", "Washroom"), "dk": ("DK", "Kitchen"), "tatami": ("和室", "Tatami Room"), "western": ("洋室", "Western Room"),
    "closet": ("押し入れ", "Closet"), "corridor": ("外廊下", "Corridor"), "outside": ("外", "Outside"), "balcony": ("ベランダ", "Balcony"),
}

# HUD line (short), printout line (full)
STEPS = {
    "plush": ("人形を用意する", "Get the doll ready", "人形を用意する（洋室の棚のぬいぐるみ）", "Get the doll (the plush on the western room's shelf)"),
    "cut": ("綿を抜く", "Take out the stuffing", "ちゃぶ台で、はさみで腹を裂き、綿を全部抜く", "At the low table, cut it open with the scissors and pull out all the stuffing"),
    "rice": ("米を詰める", "Fill it with rice", "代わりに米を詰める（台所の米びつ）", "Fill it with rice instead (the rice bin in the kitchen)"),
    "nails": ("爪を入れる", "Put in your nails", "自分の爪を切って入れる（脱衣所の爪切り）", "Clip your own nails and put them in (the clipper in the washroom)"),
    "sew": ("赤い糸で縫う", "Sew it with red thread", "赤い糸で縫い合わせ、残りの糸を全部巻きつける", "Sew it shut with red thread and wind all the rest around it"),
    "bath": ("風呂に水を張る", "Fill the bathtub", "風呂桶に水を張る", "Fill the bathtub with water"),
    "salt": ("塩水を用意する", "Prepare salt water", "コップに塩水を作る。隠れ場所に置いておく", "Make a cup of salt water. Keep it where you will hide"),
    "cameras": ("定点カメラを置く", "Place the cameras", "（自分用）定点カメラを置いて、パソコンで映りを確認", "(my own) Place the fixed cameras, check each feed on the laptop"),
    "tv": ("テレビをつける", "Turn on the TV", "テレビをつけておく（砂嵐でいい）", "Turn on the TV (static is fine)"),
    "name": ("名前をつける", "Name the doll", "午前三時。人形に名前をつける", "At 3 a.m., give the doll a name"),
    "first_oni": ("「最初の鬼は…」×3", "\"The first oni is…\" ×3", "「最初の鬼は（自分の名前）だから」と三回言う", "Say three times: \"The first oni is (your name)\""),
    "tub": ("人形を風呂に入れる", "Put the doll in the bath", "人形を風呂の水に沈める", "Put the doll into the water in the bathtub"),
    "lights": ("家中の電気を消す", "Turn off every light", "家中の電気を消す。テレビはつけたまま", "Turn off every light in the house. Leave the TV on"),
    "count": ("目を閉じて10数える", "Close your eyes, count to 10", "テレビの前で目を閉じ、十数える", "In front of the TV, close your eyes and count to ten"),
    "knife": ("刃物を持って風呂場へ", "Take the knife to the bath", "刃物を持って風呂場へ行く", "Take the knife and go to the bathroom"),
    "stab": ("「〇〇みつけた」", "\"Found you\"", "「〇〇みつけた」と言って、刺す", "Say \"(name), found you\" and stab it"),
    "next_oni": ("「次は〇〇が鬼だから」", "\"Next, you are the oni\"", "「次は〇〇が鬼だから」と言って、その場を離れる", "Say \"Next, (name) is the oni\" and leave it there"),
    "hide": ("塩水を口に含んで隠れる", "Salt water in your mouth, hide", "塩水を口に含み、押し入れに隠れる", "Take a mouthful of salt water and hide in the closet"),
    "find": ("人形を探す", "Find the doll", "終わらせる時は、塩水を口に含んだまま人形を探す", "To end it: with the salt water still in your mouth, find the doll"),
    "spit": ("塩水をかける", "Spit the salt water on it", "人形に塩水を吹きかける", "Spit the salt water on the doll"),
    "win": ("「私の勝ち」×3", "\"I win\" ×3", "「私の勝ち」と三回言う", "Say \"I win\" three times"),
    "burn": ("人形を燃やす", "Burn the doll", "人形を燃やす（流しか、水を抜いた風呂で）", "Burn the doll (in the sink, or the drained tub)"),
}

RULES = [
    ("一、必ず一人で行うこと。", "I. Always do it alone."),
    ("一、隠れている間は、塩水を口から出してはいけない。声を出してはいけない。",
     "II. While hiding, do not let the salt water out of your mouth. Do not make a sound."),
    ("一、始めたら、家から出てはいけない。", "III. Once you begin, do not leave the house."),
    ("一、始めたら、必ず終わらせること。（二時間以内）", "IV. Once you begin, you must end it. (Within two hours.)"),
    ("一、終わったら、人形を燃やすこと。", "V. When it is over, burn the doll."),
]
RULE_SHORT = {
    "alone": ("一人ではなかった", "You were not alone"),
    "water": ("隠れている間に、塩水を出した", "The salt water left your mouth while hiding"),
    "door": ("家から出た", "You left the house"),
    "lights": ("電気をつけた", "You turned on a light"),
    "closet": ("押し入れ以外に隠れた", "You hid somewhere other than the closet"),
}

THOUGHTS = {
    "plush": ("……小さい頃から、ずっと一緒だった", "...it has been with me since I was little."),
    "sewing_box": ("はさみと、赤い糸", "Scissors, and red thread."),
    "clipper": ("爪切り", "The nail clipper."),
    "rice": ("米を少し取った", "I took some rice."),
    "salt": ("塩", "Salt."),
    "cup": ("コップに水を汲んだ", "I filled a cup with water."),
    "knife_early": ("……まだ使わない", "...not yet."),
    "cameras": ("アクションカメラが四台", "Four action cameras."),
    "laptop": ("ノートパソコン。カメラの映像はこれで見られる", "The laptop. I can watch the cameras on it."),
    "lighter": ("ライター", "A lighter."),
    "table": ("ここで、はさみを使う", "This is where I use the scissors."),
    "need_plush": ("ぬいぐるみがない", "I don't have the plush."),
    "wait_three": ("三時を待つ", "Wait for three o'clock."),
    "tub_not_full": ("風呂の水が、まだ足りない", "The bath isn't full yet."),
    "need_salt": ("塩がいる", "I need salt."),
    "need_cup": ("水の入ったコップがいる", "I need a cup of water."),
    "nails_done": ("もう入れた", "They're already in it."),
    "nails_hint": ("米を詰めたぬいぐるみに入れる", "They go into the rice-filled plush."),
    "sew_hint": ("縫うのは、米と爪を入れてから", "Sew it after the rice and the nails are in."),
    "sew_nails_first": ("先に爪を入れる", "The nails go in first."),
    "rice_hint": ("腹を裂いたぬいぐるみに詰める", "It goes into the cut-open plush."),
    "count_hint": ("テレビの前で、R：目を閉じて数える", "In front of the TV, R: close your eyes and count."),
    "count_where": ("テレビの前で数える", "Count in front of the TV."),
    "tv_off": ("テレビがついていない", "The TV is off."),
    "tv_self": ("……テレビが、ついた", "...the TV came on."),
    "hold_doll": ("人形を手に持つ", "Hold the doll."),
    "run": ("――隠れなきゃ", "- I have to hide."),
    "hidden": ("……声を出しちゃいけない", "...I mustn't make a sound."),
    "need_water": ("塩水を、口に", "The salt water. In my mouth."),
    "mouth_empty": ("……塩水を、飲み込んでしまった", "...I swallowed the salt water."),
    "mouth_full": ("もう口の中にある", "My mouth is already full."),
    "cup_empty": ("コップが空だ", "The cup is empty."),
    "water_lost": ("塩水が――", "The salt water -"),
    "early_out": ("……まだ四時前だ。それでも、終わらせる", "...it's not four yet. I'll end it anyway."),
    "find": ("静かだ。……人形を探さないと", "It's quiet. ...I have to find the doll."),
    "spit_where": ("もっと近くで、人形を見て", "Closer. Look at the doll."),
    "spit_empty": ("口に塩水がない", "There's no salt water in my mouth."),
    "say_win": ("先に「私の勝ち」と言う（R）", "First say \"I win\" (R)."),
    "carry": ("……燃やさないと", "...I have to burn it."),
    "need_lighter": ("火がいる", "I need fire."),
    "need_doll": ("人形がない", "I don't have the doll."),
    "burn_where": ("流しか、水を抜いた風呂で", "In the sink, or the drained tub."),
    "burning": ("燃えていく", "It burns."),
    "draining": ("風呂の栓を抜いた", "I pulled the plug."),
    "drained": ("水が抜けた", "The water is gone."),
    "camera_placed": ("CAM%s　%s", "CAM%s  %s"),
    "cameras_fixed": ("三時を過ぎたら、カメラはもう動かせない", "After three, the cameras stay where they are."),
    "cameras_max": ("カメラは四台まで", "Four cameras, no more."),
    "no_cameras": ("まだカメラを置いていない", "I haven't placed any cameras yet."),
    "need_screen": ("パソコンかスマホがいる", "I need the laptop or the phone."),
    "hold_phone": ("スマホを手に持つ", "Hold the phone."),
    "peephole": ("……誰もいない", "...nobody."),
    "peephole_night": ("覗き穴の向こうは、真っ暗だ", "Beyond the peephole, only dark."),
    "intercom": ("インターホン。古い", "The intercom. Old."),
    "door_before": ("……まだ、やめられる", "...I could still stop."),
    "soto_1": ("外廊下。雨の音", "The corridor. The rain."),
    "soto_2": ("……さっきも、この扉の前を通った", "...I passed this door before."),
    "soto_locked": ("……開かない", "...it won't open."),
}

SAY = {
    "first_oni": ("「最初の鬼は、わたしだから」（%2$s/3）", "\"The first oni is me.\" (%2$s/3)"),
    "named": ("「……あなたの名前は、%s」", "\"...Your name is %s.\""),
    "mitsuketa": ("「%sちゃん、みつけた」", "\"%s, found you.\""),
    "next_oni": ("「次は、%sが鬼だから」", "\"Next, %s is the oni.\""),
    "count": ("「いち、に、さん、し、ご、ろく、しち、はち、きゅう……じゅう」", "\"One, two, three, four, five, six, seven, eight, nine... ten.\""),
    "win": ("「わたしの勝ち」（%s/3）", "\"I win.\" (%s/3)"),
    "three_am": ("午前三時", "3 a.m."),
    "tsugi": ("「つぎは、あなたがおに」", "\"Next, you are the oni.\""),
    "woman": ("「……そこにいるの」", "\"...are you there.\""),
    "whisper_name": ("「……%s」", "\"...%s.\""),
    "call.1": ("（……無音。誰かが、息をしている）", "(...silence. Someone is breathing.)"),
    "call.2": ("「……そこにいるの」", "\"...are you there.\""),
    "tv_voice.1": ("（テレビ）「……の、……ですか……」", "(TV) \"...the... is it...\""),
    "tv_voice.2": ("（テレビ）「……みつけた……」", "(TV) \"...found you...\""),
    "tv_voice.3": ("（テレビ）「……二〇三号室の……」", "(TV) \"...room two-oh-three...\""),
}

ACTIONS = {
    "cut": ("裂いている……", "Cutting..."), "rice": ("米を詰めている……", "Filling it with rice..."),
    "nails": ("爪を切っている……", "Clipping your nails..."), "sew": ("縫っている……", "Sewing..."),
    "salt": ("塩を溶かしている……", "Stirring in the salt..."),
}

MESSAGES = {
    1: ("ほんとにやるの？", "are you really doing it?"),
    2: ("やめとけって", "don't, seriously"),
    3: ("おーい", "hey"),
    4: ("既読つかないけど大丈夫？", "you haven't read anything. you ok?"),
    5: ("さっき電話かけてきた？無言だったけど", "did you just call me? nobody said anything"),
    6: ("心配だから今からそっち行く", "i'm worried, coming over now"),
    7: ("着いた。ドアの前にいる。開けて", "i'm here. outside your door. open up"),
    8: ("なんで開けないの", "why won't you open the door"),
}

PHONE = {
    "friend": ("ユウキ", "Yuki"),
    "missed_from": ("不在着信", "Missed call"),
    "incoming": ("着信", "Incoming call"),
    "on_call": ("通話中", "On the line"),
    "own_number": ("090-6127-0203（自分）", "090-6127-0203 (you)"),
    "answer_hint": ("右クリック：出る　画面から：拒否", "Right-click: answer   Screen: decline"),
    "call_declined": ("通話を拒否した", "Declined"),
    "call_ended": ("通話が切れた", "The call ended"),
    "date": ("11月3日（火）", "Tue, Nov 3"),
    "messages": ("メッセージ", "Messages"),
    "silent_on": ("マナーモード：オン", "Silent: on"),
    "silent_off": ("マナーモード：オフ", "Silent: off"),
    "screen_hint": ("右クリック：メニュー", "Right-click: menu"),
    "map_title": ("マップ　現在地", "Map: you are here"),
    "map_here": ("コーポ白樺 203号室", "Corpo Shirakaba, room 203"),
}

PRINTOUT = {
    "site": ("http://hon-kowa.net/occult/1031/  ― 2/6", "http://hon-kowa.net/occult/1031/  - 2/6"),
    "title": ("【検証】ひとりかくれんぼ やり方まとめ【閲覧注意】", "[TESTED] Hitori kakurenbo: how to do it [VIEWER DISCRETION]"),
    "post": ("1 名前：本当にあった怖い名無し　2026/10/31(土) 02:13", "1 Name: Anonymous  2026/10/31 (Sat) 02:13"),
    "need": ("■ 用意するもの", "■ What you need"),
    "need_list": ("手足のあるぬいぐるみ／米／自分の爪／赤い糸と針／刃物／塩水（コップ一杯）",
                  "A stuffed toy with arms and legs / rice / your own nails / red thread and a needle / a blade / salt water (one cup)"),
    "prep": ("■ 準備（三時までに）", "■ Preparation (before 3 a.m.)"),
    "prep_note": ("※携帯はマナーモードに。※隠れ場所は押し入れ。", "* Put your phone on silent. * Hide in the closet."),
    "ritual": ("■ 手順（午前三時に始める）", "■ The ritual (begin at 3 a.m.)"),
    "finish": ("■ 終わらせ方", "■ How to end it"),
    "rules": ("■ 守ること", "■ The rules"),
    "warning": ("途中でやめたら、終わらない。人形は、あなたを探し続ける。", "If you stop halfway, it does not end. The doll keeps looking for you."),
    "reply": ("2 名前：本当にあった怖い名無し\n＞＞1 やったけど浴室のドアが閉まってたのに開いてた\n3 名前：本当にあった怖い名無し\n＞＞2 それ以上書くな",
              "2 Name: Anonymous\n>>1 did it. the bathroom door was shut and then it was open\n3 Name: Anonymous\n>>2 stop posting"),
}

INTRO = [
    ("二〇二六年十一月三日（火）\n午前二時二十分", "Tuesday, 3 November 2026\n2:20 a.m."),
    ("コーポ白樺　二〇三号室", "Corpo Shirakaba, room 203"),
    ("古い掲示板で読んだ、あの遊びを\n今夜、ひとりで試す", "That game I read about on an old forum.\nTonight I try it. Alone."),
]

HUD = {
    "next": ("次：%s", "Next: %s"),
    "wait_three": ("待つ：午前三時", "Wait: 3 a.m."),
    "hiding": ("隠れる（V：息を止める）", "Hide (V: hold your breath)"),
    "prep_count": ("準備 %s/%s", "Preparation %s/%s"),
    "mouth": ("塩水", "Salt water"),
    "mouth_empty": ("口の中：なし", "Mouth: empty"),
    "rule_broken": ("――ルールを破った", "- a rule broken"),
    "three_am": ("午前三時", "3:00 a.m."),
    "three_am_sub": ("ひとりかくれんぼを、始める", "The game begins."),
    "no_signal": ("信号なし", "NO SIGNAL"),
    "app_keys": ("1–4 / A・D：カメラ切替　右クリック・ESC・C：閉じる", "1-4 / A, D: switch camera   Right-click / ESC / C: close"),
    "closet_keys": ("右クリック長押し：覗く　右クリック：出る　C：カメラ　V：息を止める", "Hold right-click: peek   Right-click: leave   C: cameras   V: hold breath"),
    "western_keys": ("右クリック：出る　C：カメラ　V：息を止める", "Right-click: leave   C: cameras   V: hold breath"),
}

HINT = {
    "controls": ("右クリック：使う・調べる　R：唱える／吹きかける\nC：カメラアプリ　J：手順書　V（長押し）：息を止める\nスニーク：足音を殺す",
                 "Right-click: use / interact   R: speak / spit\nC: camera app   J: printout   V (hold): hold your breath\nSneak: quiet steps"),
    "printout": ("手順書は J でいつでも読める", "J: read the printout any time"),
}

GUI = {
    "printout": ("手順書", "The Printout"),
    "printout_hint": ("%s　　←→・ホイール：めくる　J・ESC：閉じる", "%s    arrows / scroll: turn   J / ESC: close"),
    "naming": ("名前", "Name"),
    "naming_ok": ("決める", "Decide"),
    "naming_title": ("この子の名前は――", "This one's name is -"),
    "naming_sub": ("（人形に名前をつける）", "(name the doll)"),
    "phone": ("スマートフォン", "Phone"),
    "phone_camera": ("カメラアプリ", "Camera app"),
    "phone_light_on": ("ライトをつける", "Light on"),
    "phone_light_off": ("ライトを消す", "Light off"),
    "phone_silent": ("マナーモードにする", "Silent mode"),
    "phone_sound": ("マナーモードを解除", "Sound on"),
    "phone_decline": ("着信を拒否", "Decline the call"),
}

ENDING = {
    "title": ("ひとりかくれんぼ", "HITORI KAKURENBO"),
    "end_good": ("終了　―END―", "OVER - THE END"),
    "end_oni": ("鬼　―END―", "ONI - THE END"),
    "end_soto": ("外へ　―END―", "OUTSIDE - THE END"),
    "end_mikan": ("未完　―END―", "UNFINISHED - THE END"),
    "continue": ("クリックで続ける", "Click to continue"),
    "good.1": ("カーテンの隙間から、朝の光が差し込んでいる", "Morning light comes in through the gap in the curtains."),
    "good.2": ("流しの中には、黒く焦げた糸と、米粒が残っていた", "In the sink: blackened thread, and grains of rice."),
    "good.3": ("――終わった", "- It's over."),
    "good.rules.0": ("完璧な終わり", "A perfect ending."),
    "good.rules.1": ("ひとつ、ルールを破った。……それでも、終わった", "One rule broken. ...Still, it ended."),
    "good.rules.2": ("いくつかルールを破った。本当に、終わったのだろうか", "Some rules broken. Did it really end?"),
    "good.rules.3": ("ルールをほとんど守れなかった。……終わったことに、しておこう", "Hardly a rule kept. ...Let's call it over."),
    "good.afternoon": ("その日の午後", "That afternoon"),
    "good.playback": ("昨夜の録画を、見返す", "Playing back last night's recordings."),
    "no_footage": ("（録画がない）", "(no footage)"),
    "oni.1": ("つぎは、あなたがおに", "next, you are the oni"),
    "oni.2": ("カメラは、誰もいない部屋を撮り続けた。……浴槽の中に、立っている人影まで", "The cameras kept filming the empty rooms. ...Even the figure standing in the bathtub."),
    "soto.1": ("外廊下は、どこまでも続いていた", "The outdoor corridor went on, and on."),
    "soto.2": ("携帯の地図では、わたしはまだ、二〇三号室の中にいる", "On my phone's map, I am still inside room 203."),
    "soto.3": ("扉の向こうで、テレビの砂嵐が鳴っている", "Behind the door, the TV's static hisses."),
    "mikan.1": ("朝になった。ひとりかくれんぼは、終わらなかった", "Morning came. The game never ended."),
    "mikan.2": ("人形は、どこにもいなかった", "The doll was nowhere."),
    "mikan.3": ("最後の映像には、ベランダからこちらを覗く、それが映っていた", "In the last footage, it is on the balcony, looking in."),
}

CREDITS = [
    ("ひとりかくれんぼ ―HITORI KAKURENBO―", "HITORI KAKURENBO"),
    ("", ""),
    ("コーポ白樺　二〇三号室", "Corpo Shirakaba, room 203"),
    ("", ""),
    ("この物語はフィクションです", "This story is fiction."),
    ("実在の儀式とは関係ありません", "It has nothing to do with any real ritual."),
    ("", ""),
    ("ひとりかくれんぼは、", "Please do not"),
    ("真似しないでください", "try this at home."),
    ("", ""),
    ("人形は、燃やしましたか", "Did you burn the doll?"),
    ("", ""),
    ("", ""),
    ("おやすみなさい", "Good night."),
]

BLOCK_NAMES = {
    "tatami": ("畳", "Tatami"), "tatami_worn": ("すり切れた畳", "Worn Tatami"), "old_flooring": ("古いフローリング", "Old Flooring"),
    "vinyl_floor": ("クッションフロア", "Cushion Floor"), "genkan_tile": ("玄関タイル", "Entrance Tile"), "bath_tile_floor": ("浴室の床", "Bath Floor Tile"),
    "bath_tile_wall": ("浴室のタイル", "Bath Wall Tile"), "plaster_wall": ("砂壁", "Plaster Wall"), "plaster_wall_stained": ("染みのある砂壁", "Stained Plaster Wall"),
    "wallpaper_old": ("色褪せた壁紙", "Faded Wallpaper"), "wood_wainscot": ("腰板", "Wainscot"), "ceiling_board": ("天井板", "Ceiling Board"),
    "ceiling_board_stained": ("染みのある天井板", "Stained Ceiling Board"), "oshiire_floor": ("押し入れの床板", "Closet Floorboard"),
    "apartment_siding": ("トタンの外壁", "Corrugated Siding"), "apartment_door_metal": ("鉄の扉", "Steel Door Panel"),
    "corridor_concrete": ("外廊下の床", "Corridor Concrete"), "concrete": ("コンクリート", "Concrete"), "asphalt_wet": ("濡れたアスファルト", "Wet Asphalt"),
    "block_wall": ("ブロック塀", "Block Wall"), "roof_slate": ("屋根", "Roof Slate"), "roof_slate_stairs": ("屋根の階段", "Roof Slate Stairs"),
    "roof_slate_slab": ("屋根のハーフ", "Roof Slate Slab"), "front_door": ("玄関ドア", "Front Door"), "interior_door": ("室内ドア", "Interior Door"),
    "toilet_door": ("トイレのドア", "Toilet Door"), "bath_door": ("浴室の折れ戸", "Bathroom Folding Door"), "fusuma": ("襖", "Fusuma"),
    "oshiire_fusuma": ("押し入れの襖", "Closet Fusuma"), "balcony_door": ("掃き出し窓", "Balcony Sliding Door"),
    "chabudai": ("ちゃぶ台", "Low Table"), "zabuton": ("座布団", "Floor Cushion"), "crt_tv": ("ブラウン管テレビ", "CRT TV"), "tv_stand": ("テレビ台", "TV Stand"),
    "hanging_lamp": ("吊り下げ照明", "Hanging Lamp"), "fluorescent_ring": ("丸型蛍光灯", "Ring Fluorescent Lamp"), "pull_switch": ("引き紐スイッチ", "Pull Cord"),
    "light_switch": ("電気のスイッチ", "Light Switch"), "curtain": ("カーテン", "Curtain"), "futon_folded": ("布団", "Folded Futon"),
    "cardboard_box": ("段ボール箱", "Cardboard Box"), "altar_shelf": ("小さな棚", "Small Shelf"), "bathtub": ("浴槽", "Bathtub"), "bath_tap": ("風呂の蛇口", "Bath Tap"),
    "shower": ("シャワー", "Shower"), "bath_stool": ("風呂椅子", "Bath Stool"), "bucket": ("風呂桶", "Bath Bucket"), "washing_machine": ("洗濯機", "Washing Machine"),
    "washbasin": ("洗面台", "Washbasin"), "mirror_small": ("鏡", "Mirror"), "toilet_seat": ("便器", "Toilet"), "kitchen_sink": ("流し", "Kitchen Sink"),
    "gas_stove": ("ガスコンロ", "Gas Stove"), "fridge_old": ("古い冷蔵庫", "Old Fridge"), "rice_bag": ("米びつ", "Rice Bin"), "rice_cooker": ("炊飯器", "Rice Cooker"),
    "salt_shaker": ("塩", "Salt"), "cup_glass": ("コップ", "Glass"), "knife_rack": ("包丁立て", "Knife Rack"), "microwave": ("電子レンジ", "Microwave"),
    "dining_table": ("食卓", "Dining Table"), "dining_chair": ("椅子", "Chair"), "drawer": ("引き出し", "Drawer"), "desk": ("机", "Desk"),
    "desk_chair": ("机の椅子", "Desk Chair"), "laptop_block": ("ノートパソコン", "Laptop"), "sewing_box": ("裁縫箱", "Sewing Box"), "printout": ("手順書", "Printout"),
    "bed": ("ベッド", "Bed"), "shelf": ("棚", "Shelf"), "plush_doll": ("うさぎのぬいぐるみ", "Stuffed Rabbit"), "camera_bag": ("カメラバッグ", "Camera Bag"),
    "poster": ("ポスター", "Poster"), "desk_lamp": ("デスクライト", "Desk Lamp"), "shoe_cabinet": ("下駄箱", "Shoe Cabinet"), "shoes": ("靴", "Shoes"),
    "umbrella": ("傘", "Umbrella"), "breaker_panel": ("ブレーカー", "Breaker Panel"), "intercom": ("インターホン", "Intercom"), "calendar": ("カレンダー", "Calendar"),
    "wall_clock": ("掛け時計", "Wall Clock"), "laundry_pole": ("物干し竿", "Laundry Pole"), "ac_outdoor_unit": ("室外機", "A/C Unit"),
    "vending_machine_small": ("自動販売機", "Vending Machine"), "street_lamp_old": ("街灯", "Street Lamp"), "utility_pole": ("電柱", "Utility Pole"),
    "mailboxes": ("集合ポスト", "Mailboxes"), "bicycle": ("自転車", "Bicycle"), "stairs_rail": ("階段の手すり", "Stair Rail"), "nameplate": ("表札", "Nameplate"),
    "vacancy_sign": ("入居者募集", "Vacancy Sign"), "swing_set": ("ブランコ", "Swing"), "puddle": ("水たまり", "Puddle"), "rain_window": ("窓", "Window"),
}

COMMON = {
    "itemGroup.hitori": ("ひとりかくれんぼ", "Hitori Kakurenbo"),
    "key.categories.hitori": ("ひとりかくれんぼ", "Hitori Kakurenbo"),
    "key.hitori.breath": ("息を止める（長押し）", "Hold your breath"),
    "key.hitori.action": ("唱える／吹きかける", "Speak / spit"),
    "key.hitori.camera": ("カメラアプリ", "Camera app"),
    "key.hitori.printout": ("手順書", "Printout"),
    "hitori.shaders.installed": ("影MOD「HitoriShaders」をインストールして有効にしました。（Oculus / Iris）",
                                 "Installed and enabled the HitoriShaders shader pack (Oculus / Iris)."),
    "hitori.shaders.updated": ("HitoriShaders を更新しました。", "HitoriShaders was updated."),
    "hitori.shaders.optifine": ("HitoriShaders を OptiFine に設定しました。次回の起動から有効になります。",
                                "HitoriShaders was set for OptiFine; it will be active from the next launch."),
    "item.hitori.ritual_doll.named": ("人形「%s」", "The doll \"%s\""),
    "item.hitori.plush_rice.nails": ("爪が入っている", "Your nail clippings are in it"),
    "item.hitori.salt_water.sips": ("のこり %s口", "%s mouthfuls left"),
}


def main():
    ja, en = {}, {}

    def put(key, pair):
        ja[key], en[key] = pair

    for k, v in COMMON.items():
        put(k, v)
    for k, (nj, ne, dj, de) in ITEMS.items():
        put(f"item.{NS}.{k}", (nj, ne))
        put(f"item.{NS}.{k}.desc", (dj, de))
    for k, v in ENTITIES.items():
        put(f"entity.{NS}.{k}", v)
    for k, v in ROOMS.items():
        put(f"room.{NS}.{k}", v)
    for k, (hj, he, pj, pe) in STEPS.items():
        put(f"step.{NS}.{k}", (hj, he))
        put(f"printout.{NS}.step.{k}", (pj, pe))
    for i, r in enumerate(RULES):
        put(f"rule.{NS}.{i + 1}", r)
    for k, v in RULE_SHORT.items():
        put(f"rule.{NS}.short.{k}", v)
    for k, v in THOUGHTS.items():
        put(f"thought.{NS}.{k}", v)
    for k, v in SAY.items():
        put(f"say.{NS}.{k}", v)
    for k, v in ACTIONS.items():
        put(f"action.{NS}.{k}", v)
    for k, v in MESSAGES.items():
        put(f"msg.{NS}.{k}", v)
    put(f"msg.{NS}.missed", ("不在着信：090-6127-0203（自分）", "Missed call: 090-6127-0203 (you)"))
    for k, v in PHONE.items():
        put(f"phone.{NS}.{k}", v)
    for k, v in PRINTOUT.items():
        put(f"printout.{NS}.{k}", v)
    for i, v in enumerate(INTRO):
        put(f"intro.{NS}.{i + 1}", v)
    for k, v in HUD.items():
        put(f"hud.{NS}.{k}", v)
    for k, v in HINT.items():
        put(f"hint.{NS}.{k}", v)
    for k, v in GUI.items():
        put(f"gui.{NS}.{k}", v)
    for k, v in ENDING.items():
        put(f"ending.{NS}.{k}", v)
    for i, v in enumerate(CREDITS):
        put(f"credits.{NS}.{i + 1}", v)
    for k, v in BLOCK_NAMES.items():
        put(f"block.{NS}.{k}", v)
    for lang, d in (("ja", ja), ("en", en)):
        sub = os.path.join(HERE, "audio", f"subtitles_{lang}.json")
        if os.path.exists(sub):
            with open(sub, encoding="utf-8") as f:
                extra = json.load(f)
            # the sound worker's subtitles; anything left over from an earlier mod is skipped
            for k, v in extra.items():
                if k.startswith("subtitles.hitori.") or k.startswith("subtitles." + NS):
                    d[k] = v
    # every registered block, item and entity needs a name (the world registers blocks too)
    src = os.path.join(HERE, "..", "src", "main", "java", "com", "hitori", "registry")
    unnamed = []
    for fname, prefix, pattern in (("ModBlocks.java", "block", r'(?:block|cube|litProp|prop|door|BLOCKS\.register)\(\s*"([a-z0-9_]+)"'),
                                   ("ModItems.java", "item", r'(?:ITEMS\.register|game)\(\s*"([a-z0-9_]+)"'),
                                   ("ModEntities.java", "entity", r'(?:ENTITIES\.register)\(\s*"([a-z0-9_]+)"')):
        path = os.path.join(src, fname)
        if not os.path.exists(path):
            continue
        with open(path, encoding="utf-8") as f:
            for rid in sorted(set(re.findall(pattern, f.read()))):
                if f"{prefix}.{NS}.{rid}" not in en:
                    unnamed.append(f"{prefix}.{NS}.{rid}")
    if unnamed:
        print("WARNING no name for: " + ", ".join(unnamed))
    os.makedirs(OUT, exist_ok=True)
    for lang, d in (("ja_jp", ja), ("en_us", en)):
        with open(os.path.join(OUT, f"{lang}.json"), "w", encoding="utf-8") as f:
            json.dump(dict(sorted(d.items())), f, ensure_ascii=False, indent=2)
    missing = sorted(set(ja) ^ set(en))
    print(f"ja {len(ja)} keys, en {len(en)} keys, mismatched: {missing[:10]}")


if __name__ == "__main__":
    main()
