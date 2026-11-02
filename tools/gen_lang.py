#!/usr/bin/env python3
"""Writes assets/sounan/lang/ja_jp.json and en_us.json (merging the sound subtitles from tools/audio/)."""
import json
import os

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.normpath(os.path.join(HERE, "..", "src", "main", "resources", "assets", "sounan", "lang"))
NS = "sounan"

BLOCKS = {
    "cedar_log": ("杉の丸太", "Cedar Log"), "cedar_log_ribbon": ("目印テープの杉", "Cedar with Marking Tape"),
    "cedar_log_shimenawa": ("御神木", "Sacred Cedar"), "cedar_leaves": ("杉の葉", "Cedar Needles"),
    "forest_floor": ("杉の落ち葉", "Forest Floor"), "forest_dirt": ("腐葉土", "Forest Soil"), "moss_floor": ("苔", "Moss"),
    "trail": ("踏み跡", "Old Trail"), "mountain_rock": ("岩", "Mountain Rock"), "mossy_rock": ("苔むした岩", "Mossy Rock"),
    "river_stones": ("沢の石", "River Stones"), "fallen_leaves": ("落ち葉", "Fallen Leaves"),
    "weathered_planks": ("色褪せた板", "Weathered Planks"), "dark_planks": ("黒ずんだ床板", "Dark Planks"),
    "old_plaster": ("崩れた土壁", "Crumbling Plaster"), "tin_roof": ("錆びたトタン", "Rusted Tin"),
    "tin_roof_stairs": ("錆びたトタン屋根", "Rusted Tin Roof"), "tin_roof_slab": ("錆びたトタンのハーフ", "Rusted Tin Slab"),
    "blue_tarp": ("ブルーシート", "Blue Tarp"), "concrete_old": ("古いコンクリート", "Old Concrete"),
    "concrete_moss": ("苔むしたコンクリート", "Mossy Concrete"), "asphalt": ("ひび割れたアスファルト", "Cracked Asphalt"),
    "steel_lattice": ("鉄塔の鉄骨", "Pylon Lattice"), "tatami_old": ("古い畳", "Old Tatami"), "old_planks": ("古びた床板", "Old Floorboards"),
    "rotten_planks": ("朽ちた板", "Rotten Planks"), "dark_beam": ("黒い柱", "Dark Beam"), "plaster_wall": ("漆喰壁", "Plaster Wall"),
    "shoji_torn": ("破れ障子", "Torn Shoji"), "futon": ("湿った布団", "Damp Futon"), "stone_lantern": ("石灯籠", "Stone Lantern"),
    "blood_stain": ("血痕", "Blood Stain"), "footprints": ("裸足の足跡", "Bare Footprints"), "hair_strands": ("髪の毛", "Hair"),
    "shimenawa": ("注連縄", "Shimenawa"), "hut_door": ("小屋の戸", "Hut Door"), "house_door": ("古い引き戸", "Old Sliding Door"),
    "iron_gate": ("隧道の鉄門", "Tunnel Gate"), "lost_bag": ("失くしたバッグ", "Lost Backpack"), "memo": ("メモ", "Note"),
    "memo_wall": ("打ち付けられたメモ", "Nailed Note"), "logbook": ("登山者ノート", "Hikers' Logbook"),
    "notice_board": ("掲示板", "Notice Board"), "signpost": ("道標", "Trail Sign"), "jizo": ("地蔵", "Jizo Statue"),
    "hokora": ("山の祠", "Mountain Shrine"), "wara_ningyo": ("藁人形", "Straw Doll"), "rope_anchor": ("ロープ場の杭", "Climbing Stake"),
    "climbing_rope": ("垂らしたロープ", "Hanging Rope"), "public_phone": ("公衆電話", "Public Phone"), "bus_stop": ("バス停", "Bus Stop"),
    "tunnel_lamp": ("隧道の灯り", "Tunnel Lamp"), "guardrail": ("ガードレール", "Guardrail"), "key_hook": ("鍵掛け", "Key Board"),
    "car_door": ("自分の車", "Your Car"), "omiki_bottle": ("御神酒の徳利", "Sake Bottle"),
}

ITEMS = {
    "flashlight": ("懐中電灯", "Flashlight"), "flashlight_on": ("懐中電灯（点灯）", "Flashlight (on)"),
    "flashlight_low": ("懐中電灯（電池切れ間近）", "Flashlight (low battery)"), "phone": ("スマホ", "Smartphone"),
    "phone_light": ("スマホ（ライト）", "Smartphone (torch)"), "phone_camera": ("スマホ（カメラ）", "Smartphone (camera)"),
    "battery": ("予備電池", "Spare Batteries"), "power_bank": ("モバイルバッテリー", "Power Bank"), "bear_bell": ("熊鈴", "Bear Bell"),
    "rope": ("ロープ", "Rope"), "trail_map": ("登山地図", "Trail Map"), "flare": ("発煙筒", "Emergency Flare"),
    "flare_lit": ("発煙筒（燃焼中）", "Emergency Flare (burning)"), "omamori": ("お守り", "Omamori"), "hut_key": ("古い鍵", "Old Key"),
    "omiki": ("御神酒", "Offering Sake"), "memo": ("メモ", "Note"),
}
ITEM_DESC = {
    "flashlight": ("右クリックで点灯／消灯。電池は減っていく。灯りを向けてはいけないものもいる。",
                   "Right-click to switch on/off. The batteries drain. Some things must not be lit."),
    "phone": ("右クリックで開く（Pキーでも）。スニーク+右クリックでライト。圏外。",
              "Right-click (or P) to open. Sneak + right-click: torch. No signal."),
    "battery": ("右クリックで懐中電灯の電池を交換する。", "Right-click to put fresh batteries in the flashlight."),
    "power_bank": ("右クリックでスマホを充電する（+60%）。", "Right-click to charge the phone (+60%)."),
    "bear_bell": ("右クリックで鳴らす。覗くものは鈴の音を嫌い、背中のものも離れていく。大きな音が出る。",
                  "Right-click to ring. The peeking things hate it, the one behind you lets go. It is loud."),
    "rope": ("崖の杭に結べば、下へ降りられる。", "Tie it to the climbers' stake at the cliff to climb down."),
    "trail_map": ("右クリックで広げる。濡れて、端が破れている。", "Right-click to unfold. Wet, torn at one edge."),
    "flare": ("右クリックで点火。四十秒間、赤い火が燃える。御山様は火を嫌う。",
              "Right-click to ignite: forty seconds of red fire. The mountain hates fire."),
    "omamori": ("祖母がくれたお守り。一度だけ、身代わりになってくれる。", "Your grandmother's charm. It takes your place, once."),
    "hut_key": ("「旧隧道」と書かれた木札が付いている。", "A wooden tag reads \"Old Tunnel\"."),
    "omiki": ("祠に供える酒。", "Sake to offer at a shrine."),
    "memo": ("拾ったメモ。スマホのメモ（Jキー）で読み返せる。", "A note you found. Read it again in the phone's notes (J)."),
}
ENTITIES = {"nozoki": ("覗き", "Nozoki"), "yobigoe": ("呼び声", "Yobigoe"), "ushiro": ("後ろ", "Ushiro"),
            "koyanushi": ("小屋主", "Koyanushi"), "oyamasama": ("御山様", "Oyama-sama"), "utsurumono": ("写るもの", "Utsurumono")}
AREAS = {"rakuraku": ("滑落地点", "Where You Fell", "KATSURAKU"), "sawa": ("沢", "The Stream", "SAWA"),
         "gake": ("崖", "The Cliff", "GAKE"), "sugi": ("杉の森", "Cedar Forest", "SUGI NO MORI"),
         "haison": ("廃村 帰野", "Kaerino, Abandoned", "HAISON"), "hokora": ("山の祠", "Mountain Shrine", "HOKORA"),
         "tsuribashi": ("吊り橋", "Suspension Bridge", "TSURIBASHI"), "koya": ("避難小屋", "Emergency Hut", "HINAN-GOYA"),
         "tetto": ("鉄塔", "Power Pylon", "TETTŌ"), "tunnel": ("旧帰野隧道", "Old Kaerino Tunnel", "KYŪ KAERINO ZUIDŌ"),
         "tozanguchi": ("登山口", "Trailhead", "TOZANGUCHI")}

COMMON = {
    "itemGroup.sounan": ("遭難 ―SOUNAN―", "SOUNAN: Lost on the Mountain"),
    "key.categories.sounan": ("遭難", "SOUNAN"),
    "key.sounan.phone": ("スマホを開く", "Open phone"),
    "key.sounan.memo": ("メモを読む", "Read notes"),
    "gui.sounan.scroll": ("%s / %s　（ホイールでめくる）", "%s / %s (scroll)"),
    "sounan.shaders.installed": ("影MOD「SounanShaders」をインストールして有効にしました。（Oculus / Iris）",
                                 "Installed and enabled the SounanShaders shader pack (Oculus / Iris)."),
    "sounan.shaders.updated": ("SounanShaders を更新しました。", "SounanShaders was updated."),
    "sounan.shaders.optifine": ("SounanShaders を OptiFine に設定しました。次回の起動から有効になります。",
                                "SounanShaders was set for OptiFine; it will be active from the next launch."),
    # ---- intro
    "intro.sounan.1": ("十一月二日　午後四時四十分　帰らず山・下山道", "November 2nd, 4:40 p.m. — the trail down Kaerazu-yama"),
    "intro.sounan.2": ("――おーい", "— Hey! Over here —"),
    "intro.sounan.3": ("誰かに呼ばれた気がして、道を外れた", "Someone called. You stepped off the trail."),
    "intro.sounan.4": ("足元が、崩れた", "The ground gave way."),
    # ---- HUD
    "hud.sounan.checkpoint": ("――少しだけ、息が落ち着いた", "— your breathing slows, a little"),
    "hud.sounan.omamori": ("お守りが、身代わりになった", "The omamori took your place"),
    "hud.sounan.date": ("%s月%s日", "%s/%s"),
    "hud.sounan.camera_help": ("右クリック:撮影　スニーク+右クリック:戻る", "Right-click: photo   Sneak + right-click: back"),
    # ---- thoughts
    "thought.sounan.wake": ("……っ、痛……。ここ、どこだ……？　バッグがない", "...ugh. Where... am I? My bag is gone."),
    "thought.sounan.fog": ("霧が濃すぎる……前が見えない", "The fog is too thick... I can't see anything."),
    "thought.sounan.flash_dead": ("……電池が切れた", "...the batteries are dead."),
    "thought.sounan.flash_low": ("ライトが暗くなってきた……電池がもたない", "The light is dimming... the batteries won't last."),
    "thought.sounan.flash_empty": ("点かない。電池が空だ", "Nothing. The batteries are empty."),
    "thought.sounan.phone_dead": ("スマホの電源が……落ちた", "The phone... just died."),
    "thought.sounan.battery_full": ("まだ電池は残っている", "There is still charge left."),
    "thought.sounan.battery": ("電池を入れ替えた", "Fresh batteries."),
    "thought.sounan.charged": ("スマホを充電した", "Phone charged."),
    "thought.sounan.flare_out": ("発煙筒が燃え尽きた", "The flare has burned out."),
    "thought.sounan.signal": ("……電波が、入った！", "...one bar. Signal!"),
    "thought.sounan.old_bag": ("……自分のじゃない。随分古いバッグだ。中は空っぽだ", "...not mine. An old bag, long empty."),
    "thought.sounan.bag_empty": ("もう何も残っていない", "Nothing left in it."),
    "thought.sounan.bag": ("あった……！　落ちた時に転がっていったんだ", "There it is...! It must have rolled down when I fell."),
    "thought.sounan.hokora_done": ("霧が晴れている。通れそうだ", "The fog has cleared. I can get through."),
    "thought.sounan.fog_lift": ("霧が……晴れていく", "The fog... is lifting."),
    "thought.sounan.hokora_need": ("御神酒を供えないと……村に残っていたはずだ", "I need to offer sake... there was some in the village."),
    "thought.sounan.hokora": ("古い祠だ。何か供えるものがあれば……", "An old shrine. If only I had something to offer..."),
    "thought.sounan.roped": ("ロープを結んだ。これで降りられる", "Rope tied. I can climb down now."),
    "thought.sounan.need_rope": ("古い杭だ。ロープがあれば、ここから降りられそうだ", "An old stake. With a rope I could climb down here."),
    "thought.sounan.key": ("鍵だ……「旧隧道」と書いた札が付いている", "A key... the tag says \"Old Tunnel\"."),
    "thought.sounan.unlocked": ("開いた……", "It opened..."),
    "thought.sounan.locked": ("鎖と南京錠で閉ざされている。鍵が要る", "Chained and padlocked. I need a key."),
    "thought.sounan.omiki": ("お酒……御神酒だ。祠に供えるものかもしれない", "Sake... an offering. Maybe for a shrine."),
    "thought.sounan.payphone_dead": ("受話器からは、何も聞こえない", "Nothing on the line."),
    "thought.sounan.wake_1": ("……夢？　いや……", "...a dream? No..."),
    "thought.sounan.wake_2": ("また、ここに戻ってきた……", "Back here again..."),
    "thought.sounan.wake_3": ("何日経った……？　スマホの日付がおかしい", "How many days...? The date on the phone is wrong."),
    "thought.sounan.wake_4": ("もう、自分が誰だったか……思い出せなくなりそうだ", "I can barely remember who I was..."),
    "thought.sounan.ushiro": ("……振り返ってはいけない気がする", "...I feel I must not look back."),
    "thought.sounan.ushiro_gone": ("……気配が、消えた", "...it's gone."),
    "thought.sounan.light": ("あれは……救助の人？", "Is that... a rescuer?"),
    "thought.sounan.watched": ("……見られている", "...something is watching."),
    "thought.sounan.sleeper": ("奥で、誰かが寝ている……", "Someone is sleeping in there..."),
    "thought.sounan.tunnel": ("真っ暗だ……出口は、まだ見えない", "Pitch black... no sign of the exit."),
    "thought.sounan.run": ("――走れ！", "— RUN!"),
    "thought.sounan.car": ("車だ……！", "My car...!"),
    "thought.sounan.fall": ("……っ！　落ちたら、ただでは済まない", "...! A fall like that would kill me."),
    # ---- signs
    "sign.sounan.fork": ("↓ 帰野（廃村）2.1km\n← 近道　（後から手書きで書き足されている）",
                         "↓ Kaerino (abandoned) 2.1 km\n← Shortcut (written on later, by hand)"),
    "sign.sounan.shrine": ("← 山の祠\n祠の先　霧多し", "← Mountain shrine\nheavy fog beyond the shrine"),
    "sign.sounan.hut": ("→ 避難小屋\n← 鉄塔巡視路", "→ Emergency hut\n← Pylon patrol path"),
    "sign.sounan.tunnel": ("→ 旧帰野隧道\n登山口まで 1.2km", "→ Old Kaerino tunnel\n1.2 km to the trailhead"),
    "sign.sounan.start": ("この先　崖　立入禁止", "Cliffs ahead — keep out"),
    "sign.sounan.trailhead": ("帰らず山　登山口\n入山届は必ず提出してください", "Kaerazu-yama trailhead\nAlways file a climbing plan"),
    "sign.sounan.bus_stop": ("帰野　バス停\n帰野線　一日一往復　最終 17:10\n（平成十年　廃止）",
                             "Kaerino bus stop\none bus a day, last 17:10\n(discontinued 1998)"),
    "sign.sounan.generic": ("文字が掠れて読めない", "The writing has worn away."),
    # ---- phone
    "phone.sounan.title": ("スマホ", "Phone"),
    "phone.sounan.no_signal": ("圏外", "No signal"),
    "phone.sounan.date": ("%s月%s日(%s)", "%s/%s (%s)"),
    "phone.sounan.app.map": ("地図", "Map"), "phone.sounan.app.camera": ("カメラ", "Camera"),
    "phone.sounan.app.light": ("ライト", "Torch"), "phone.sounan.app.messages": ("メッセージ", "Messages"),
    "phone.sounan.app.call": ("電話", "Phone"), "phone.sounan.app.memo": ("メモ", "Notes"),
    "phone.sounan.help": ("右クリック / Backspace: 戻る　Esc: 閉じる", "Right-click / Backspace: back   Esc: close"),
    "phone.sounan.map.nodata": ("地図データがありません（オフライン）", "No map data (offline)"),
    "phone.sounan.poi.bag": ("沢", "Stream"), "phone.sounan.poi.village": ("帰野", "Kaerino"),
    "phone.sounan.poi.shrine": ("祠", "Shrine"), "phone.sounan.poi.hut": ("避難小屋", "Hut"),
    "phone.sounan.poi.pylon": ("鉄塔", "Pylon"), "phone.sounan.poi.tunnel_n": ("旧隧道", "Old tunnel"),
    "phone.sounan.poi.parking": ("登山口P", "Trailhead P"),
    "phone.sounan.who.mom": ("お母さん", "Mom"), "phone.sounan.who.kenta": ("健太", "Kenta"),
    "phone.sounan.who.work": ("佐藤課長", "Mr. Sato (work)"), "phone.sounan.who.unknown": ("非通知", "Unknown"),
    "phone.sounan.msg.mom.1": ("明日、帰らず山に行くんだって？気をつけてね", "You're going up Kaerazu-yama tomorrow? Be careful."),
    "phone.sounan.msg.mom.2": ("大丈夫、日帰りだから。夕方には下りるよ", "It's a day hike. I'll be down by evening."),
    "phone.sounan.msg.mom.3": ("いってらっしゃい。下りたら連絡してね", "Have a good hike. Text me when you're down."),
    "phone.sounan.msg.mom.4": ("まだ山？", "Still on the mountain?"),
    "phone.sounan.msg.mom.5": ("電話出て", "Pick up."),
    "phone.sounan.msg.mom.6": ("警察に相談しました。返事して", "I called the police. Answer me."),
    "phone.sounan.msg.mom.7": ("どこにいるの", "Where are you"),
    "phone.sounan.msg.mom.8": ("今朝から捜索隊が山に入ってくれています。お願い、返事して",
                               "A search team went up this morning. Please, answer."),
    "phone.sounan.msg.mom.9": ("捜索が打ち切られました。でも、お母さんは待ってるからね", "They called off the search. But I'm still waiting."),
    "phone.sounan.msg.mom.10": ("今日、あなたの部屋を少しだけ片付けました。ごめんね", "I tidied your room a little today. I'm sorry."),
    "phone.sounan.msg.mom.11": ("あれから三年。今日も山に向かって名前を呼びました。返事、してくれないね",
                                "Three years now. I called your name toward the mountain again today. You never answer."),
    "phone.sounan.msg.kenta.1": ("帰らず山ってマジ？あそこ地元じゃ夜は絶対入るなって言われてるぞ",
                                 "Kaerazu-yama, seriously? The locals say never go up there at night."),
    "phone.sounan.msg.kenta.2": ("迷信だろｗ", "superstition lol"),
    "phone.sounan.msg.kenta.3": ("名前呼ばれても返事すんなよ。マジで", "If something calls your name, don't answer. I mean it."),
    "phone.sounan.msg.kenta.4": ("おい、大丈夫か？おばさんから連絡きたぞ", "Hey, you okay? Your mom called me."),
    "phone.sounan.msg.kenta.5": ("今から車で向かう。登山口で待ってる", "Driving up now. I'll wait at the trailhead."),
    "phone.sounan.msg.kenta.6": ("登山口にお前の車、まだあった。ごめん。俺があのとき止めてれば",
                                 "Your car's still at the trailhead. I'm sorry. I should have stopped you."),
    "phone.sounan.msg.kenta.7": ("結婚するよ。お前にも来てほしかった", "I'm getting married. I wish you could come."),
    "phone.sounan.msg.work.1": ("明日の会議資料、どうなってる？", "Where are the slides for tomorrow's meeting?"),
    "phone.sounan.msg.work.2": ("ご家族から事情を伺いました。こちらのことは気にせず……", "Your family told us. Don't worry about work..."),
    "phone.sounan.msg.unknown.1": ("みつけた", "found you"),
    "phone.sounan.notification": ("通知", "Notification"),
    "phone.sounan.buzz.messages": ("新着メッセージがあります", "New messages"),
    "phone.sounan.buzz.found": ("非通知：みつけた", "Unknown: found you"),
    "phone.sounan.buzz.behind": ("非通知：うしろ", "Unknown: behind you"),
    "phone.sounan.buzz.come": ("非通知：こっちだよ", "Unknown: over here"),
    "phone.sounan.memo.empty": ("メモはまだない", "No notes yet"),
    "phone.sounan.call.ringing": ("呼び出し中", "Calling"),
    "phone.sounan.call.end": ("終了", "End"),
    "phone.sounan.call.no_signal": ("圏外です", "No service"),
    "phone.sounan.call.hint": ("電波のある場所でしか通話できません", "Calls only work where there is signal"),
    "call.sounan.110.1": ("はい、110番です。事件ですか、事故ですか", "Police emergency. What is the emergency?"),
    "call.sounan.110.2": ("山で遭難……ですね。今いる場所は分かりますか", "Lost on a mountain... Do you know where you are?"),
    "call.sounan.110.3": ("帰らず山……？　少々お待ちください", "Kaerazu-yama...? Please hold."),
    "call.sounan.110.4": ("…………", "............"),
    "call.sounan.110.5": ("……もしもし。今、どこにいますか", "...hello. Where are you right now?"),
    "call.sounan.110.6": ("もっと奥まで来られますか。こちらから、呼びますから", "Can you come further in? We'll call out to you."),
    "call.sounan.110.7": ("おーい", "Hey! Over here!"),
    "call.sounan.119.1": ("119番消防です。火事ですか、救急ですか", "Fire and ambulance. Fire, or medical?"),
    "call.sounan.119.2": ("遭難ですね。落ち着いてください。お名前は", "You're lost. Stay calm. Your name?"),
    "call.sounan.119.3": ("%sさん、ですね", "%s, right."),
    "call.sounan.119.4": ("そこから動かないでください。今、迎えが行きます", "Don't move. Someone is coming for you."),
    "call.sounan.119.5": ("……灯りを持った者が、迎えに行きます", "...someone with a light is coming for you."),
    "call.sounan.119.6": ("ついて来てくださいね", "Follow them, won't you."),
    "call.sounan.mom.1": ("……もしもし？　もしもし！？", "...hello? HELLO?"),
    "call.sounan.mom.2": ("%s？　あなたなの？　どこにいるの！？", "%s? Is that you? Where are you?!"),
    "call.sounan.mom.3": ("声が遠いの。ねえ、聞こえる？", "You sound so far away. Can you hear me?"),
    "call.sounan.mom.4": ("……ずっと、待ってたのに", "...I waited so long."),
    "call.sounan.mom.5": ("……ねえ。後ろ、見て", "...hey. Look behind you."),
    "call.sounan.mom.6": ("――つまらない", "— boring."),
    "call.sounan.900.1": ("……もしもし", "...hello"),
    "call.sounan.900.2": ("――%sさん？", "— %s?"),
    "call.sounan.900.3": ("やっと、繋がった", "Finally, we're connected."),
    "call.sounan.900.4": ("今、帰野にいるのね。……ああ、見えるよ", "You're in Kaerino now. ...yes, I can see you."),
    "call.sounan.900.5": ("ここの人たちは、みんな山に行ったよ", "Everyone here went up the mountain."),
    "call.sounan.900.6": ("あなたも、もうすぐ", "You too, soon."),
    # ---- endings
    "ending.sounan.title": ("遭難", "SOUNAN"),
    "ending.sounan.good.1": ("――木々の向こうに、街灯の明かりが見えた", "— past the trees, the light of a street lamp."),
    "ending.sounan.good.2": ("駐車場。見慣れた、自分の車", "The parking lot. Your own car."),
    "ending.sounan.good.3": ("エンジンが、かかった", "The engine starts."),
    "ending.sounan.good.4": ("スマホが震えた。電波が戻ったのだ", "The phone buzzes. Signal again."),
    "ending.sounan.good.5a": ("十一月三日　午前五時十二分。……一晩で、山を下りられた",
                              "November 3rd, 5:12 a.m. ...one night, and you made it down."),
    "ending.sounan.good.5b": ("画面の日付は　%s　を示していた", "The date on the screen read %s."),
    "ending.sounan.good.6a": ("東の空が、白み始めている", "The eastern sky is turning pale."),
    "ending.sounan.good.6b": ("あの山にいたのは、一晩だけのはずなのに", "It was supposed to be one night on that mountain."),
    "ending.sounan.good.7": ("……ふと、バックミラーを見た", "...you glance at the rear-view mirror."),
    "ending.sounan.date": ("%s年%s月%s日", "%3$s/%2$s/%1$s"),
    "ending.sounan.bad.1": ("霧が、すべてを包んでいく", "The fog swallows everything."),
    "ending.sounan.bad.2": ("誰かが呼んでいる。もう、怖くはなかった", "Someone is calling. You are not afraid anymore."),
    "ending.sounan.bad.3": ("――もう、どこにも行かなくていい", "— you never have to leave."),
    "ending.sounan.bad.4": ("数年後。ある登山者のスマホに、一枚の写真が残されていた",
                            "Years later, a hiker's phone held one strange photograph."),
    "ending.sounan.bad.5": ("霧の木立に立つ、見覚えのある人影", "A familiar figure, standing among the foggy cedars."),
    "ending.sounan.bad.6": ("行方不明者　%s　――捜索は打ち切られている", "Missing person: %s — the search was called off."),
    "ending.sounan.end_good": ("END 1 ― 下山", "END 1 — Down the Mountain"),
    "ending.sounan.end_good_company": ("END 2 ― 同行者", "END 2 — Company"),
    "ending.sounan.end_bad": ("END 3 ― 神隠し", "END 3 — Spirited Away"),
    "ending.sounan.continue": ("クリックで戻る", "Click to return"),
    # ---- config
    "sounan.configuration.title": ("遭難 設定", "SOUNAN settings"),
}

NOTES = {
    1: (("濡れた手帳の切れ端", "十月二十八日。午後、下山道で誰かに呼ばれた。「おーい、こっちだ」と。救助の人かと思って、道を外れた。\n"
                       "それが間違いだった。\n\nここには道がない。沢の音だけが聞こえる。\n\n――佐伯"),
        ("Torn page from a wet notebook", "October 28th. On the way down someone called me. \"Hey! Over here!\" I thought it was a "
                                          "rescuer, so I left the trail.\nThat was the mistake.\n\nThere is no trail here. Only the "
                                          "sound of the stream.\n\n— Saeki")),
    2: (("沢沿いの書き付け", "沢に沿って下れば、いつか里に出る。山ではそう教わった。\nでも、この沢は下るほど山の奥へ入っていく気がする。\n\n"
                     "背中のほうで、誰かが同じ歩幅で歩いている。\n振り返る勇気はない。"),
        ("Scribbled by the stream", "Follow a stream downhill and you reach a village. That is what they teach you.\nBut this stream "
                                    "seems to lead deeper into the mountain.\n\nSomeone behind me walks in step with me.\nI don't "
                                    "dare look back.")),
    3: (("崖下の紙片", "ロープ場の杭は古いが、まだ使える。降りたら、もう登っては戻れない。\n\n下に着いたとき、上から誰かが覗いていた。\n"
                  "私の顔をしていた。"),
        ("Scrap below the cliff", "The climbers' stake is old but it holds. Once down, there is no climbing back.\n\nWhen I reached the "
                                  "bottom someone was looking down at me from the top.\nIt had my face.")),
    4: (("テープのこと", "ピンクの目印テープを辿っている。古いもの、新しいもの。\n\n新しいテープは、誰が結んだのだろう。\nこの山には、私しかいないはずなのに。\n\n"
                   "テープが一本、私の手首に結ばれていた。\nいつ？"),
        ("About the tapes", "I am following the pink marking tapes. Old ones, new ones.\n\nWho tied the new ones?\nThere should be "
                            "no one on this mountain but me.\n\nThere was a tape tied around my wrist.\nWhen?")),
    5: (("行き止まり", "テープを辿ってはいけなかった。ここで道は終わっている。\n\n木の根元に、靴が並べてある。左右揃えて、きちんと。何足も。\n\n"
                  "私の靴も、ここに並べることになるのだろうか。"),
        ("Dead end", "I should not have followed the tapes. The path ends here.\n\nShoes are lined up at the foot of the tree. Neatly, in "
                     "pairs. Many pairs.\n\nWill mine be set here too?")),
    6: (("見られている", "木の陰から、誰かが覗いている。首を傾けて、片目だけで。\n灯りを向けると、笑った。だから、もう向けない。目も合わせない。\n\n"
                   "近づかなければ、何もしてこない。\n……今のところは。"),
        ("Watched", "Something peeks from behind the trees. Head tilted, one eye.\nWhen I shone my light on it, it smiled. So I don't "
                    "anymore. I don't meet its eye.\n\nIf I don't go near, it does nothing.\n...so far.")),
    7: (("子供の作文", "「やまのひと」　三ねん　やまだ はるか\n\nおばあちゃんが言ってた。夜の山で名前をよばれても、へんじしちゃだめ。\n"
                  "ライトをもった人によばれても、ついていっちゃだめ。\n\nやまのひとは、さみしいから、つれていくんだって。"),
        ("A child's composition", "\"The mountain people\" - Haruka Yamada, 3rd grade\n\nGrandma said if something calls your name on the "
                                  "mountain at night, don't answer.\nIf someone with a light calls you, don't follow.\n\nThe "
                                  "mountain people are lonely, so they take you with them.")),
    8: (("最後の村人", "この村の者は、皆、山に呼ばれていった。\n最初は年寄り。次に子供たち。夫は、灯りを持った誰かに呼ばれて出ていったきりだ。\n\n"
                  "今夜は私の番だ。外で、夫の声が私の名前を呼んでいる。\n答えてはいけない。答えては――"),
        ("The last villager", "Everyone in this village was called into the mountain.\nThe old ones first. Then the children. My husband "
                              "went out after someone with a light and never came back.\n\nTonight it is my turn. Outside, my "
                              "husband's voice is calling my name.\nI must not answer. I must not—")),
    9: (("祠のこと", "祠の先は、霧が晴れない。何度進んでも、気がつくと祠の前に戻っている。\n\n村の古い決まりを思い出した。\n"
                 "「祠の前を通る者は、御神酒を供えよ」\n\n村の家に、酒が残っていたはずだ。\n……あの家には、何かが寝ている。"),
        ("About the shrine", "Beyond the shrine the fog never clears. However far I walk, I end up back at the shrine.\n\nI remembered the "
                             "village rule:\n\"Whoever passes the shrine must offer sake.\"\n\nThere was sake left in one of the "
                             "houses.\n...something sleeps in that house.")),
    10: (("登山者ノート", "1987.10.3　雨。小屋に先客あり。奥で寝ているようなので、静かに出た。\n\n"
                    "1993.11.2　鍵は旧隧道の門のものらしい。誰か持っていってくれ。\n\n"
                    "1998.10.29　佐伯。小屋の奥で、何かが寝ている。息が湿っている。鍵を取る。起こしてはいけない。屈んで歩け。\n\n"
                    "2011.8.15　（文字が震えていて読めない）\n\n2026.11.3　%1$s　ここまで来た。"),
         ("Hikers' logbook", "1987.10.3 Rain. Someone already here, asleep at the back. Left quietly.\n\n1993.11.2 The key is for the old "
                             "tunnel gate, I think. Someone take it.\n\n1998.10.29 Saeki. Something is sleeping at the back. Its "
                             "breath is wet. Taking the key. Don't wake it. Crouch.\n\n2011.8.15 (the handwriting shakes too much to "
                             "read)\n\n2026.11.3 %1$s - made it this far.")),
    11: (("鉄塔の下で", "ここなら電波が入る。家に電話した。妻が出た。\n「どこにいるの」と泣いていた。山の名前を言った。\n\n"
                   "そのあと、受話器の向こうで妻が言った。\n「もう、来なくていいよ。ずっと、そこにいればいいのに」\n\n妻の声じゃなかった。"),
         ("Under the pylon", "There is signal here. I called home. My wife answered.\nShe was crying, asking where I was. I told her the "
                             "mountain's name.\n\nThen, on the line, she said:\n\"You don't have to come back. You could just stay "
                             "there forever.\"\n\nIt wasn't her voice.")),
    12: (("出口", "トンネルの向こうに、出口の光が見える。\nなのに、いくら歩いても、光は近づかない。\n\n後ろで、大きな何かが四つ足で這ってくる音がする。\n\n"
                "火を。火を焚け。御山様は、火を嫌う――"),
         ("The exit", "At the far end of the tunnel I can see the light of the exit.\nBut however far I walk, it gets no closer.\n\nBehind "
                      "me, something huge is crawling on all fours.\n\nFire. Light a fire. The mountain hates fire—")),
    13: (("帰らず山　入山の心得", "帰野地区自治会\n夜の帰らず山に立ち入る者は、以下を必ず守ること。\n\n"
                        "一、夜の山で、灯りを持つ者に呼ばれても、決してついて行ってはならない。\n\n"
                        "一、木の陰から覗くものと、目を合わせてはならない。灯りを向けてもならない。\n\n"
                        "一、背中に気配を感じたら、決して振り返らず、そのまま歩き続けること。\n\n"
                        "一、小屋の主を起こしてはならない。小屋では音を立てず、屈んで歩くこと。\n\n"
                        "一、祠の前を通る者は、御神酒を供えること。供えぬ者は霧に迷う。\n\n"
                        "一、御山様に会ったなら、火を焚け。御山様は火を嫌う。\n\n山に呼ばれた者は、帰らない。"),
         ("Kaerazu-yama: rules for entering the mountain", "Kaerino district association\nAnyone entering Kaerazu-yama at night must keep "
                                                           "these rules.\n\nI. If someone carrying a light calls you on the mountain at "
                                                           "night, never follow.\n\nII. Never meet the eyes of what peeks from behind the "
                                                           "trees. Never shine a light on it.\n\nIII. If you feel something at your back, "
                                                           "never turn around. Keep walking.\n\nIV. Never wake the master of a hut. Make no "
                                                           "sound inside, and crouch.\n\nV. Whoever passes the shrine must offer sake. "
                                                           "Those who do not lose their way in the fog.\n\nVI. If you meet Oyama-sama, "
                                                           "light a fire. Oyama-sama hates fire.\n\nThose the mountain calls do not "
                                                           "come back.")),
    14: (("遭難者慰霊", "帰らず山にて消息を絶った方々\n\n昭和六十二年　十月　（苔に覆われて読めない）\n平成五年　十一月　（同上）\n"
                   "平成十年　十月　佐伯 和也\n平成二十三年　八月　（名札が削られている）\n令和八年　十一月　%1$s\n\n――御霊の安らかならんことを"),
         ("In memory of the lost", "Those who disappeared on Kaerazu-yama\n\nOctober 1987 (covered in moss)\nNovember 1993 (likewise)\n"
                                   "October 1998 - Kazuya Saeki\nAugust 2011 (the name has been scratched off)\nNovember 2026 - %1$s\n\n"
                                   "- may they rest in peace")),
}


def main():
    os.makedirs(OUT, exist_ok=True)
    ja, en = {}, {}
    for k, (j, e) in COMMON.items():
        ja[k], en[k] = j, e
    for k, (j, e) in BLOCKS.items():
        ja[f"block.{NS}.{k}"], en[f"block.{NS}.{k}"] = j, e
    for k, (j, e) in ITEMS.items():
        ja[f"item.{NS}.{k}"], en[f"item.{NS}.{k}"] = j, e
    for k, (j, e) in ITEM_DESC.items():
        ja[f"item.{NS}.{k}.desc"], en[f"item.{NS}.{k}.desc"] = j, e
    for k, (j, e) in ENTITIES.items():
        ja[f"entity.{NS}.{k}"], en[f"entity.{NS}.{k}"] = j, e
        ja[f"item.{NS}.{k}_spawn_egg"] = f"{j}のスポーンエッグ"
        en[f"item.{NS}.{k}_spawn_egg"] = f"{e} Spawn Egg"
    for k, (j, e, roman) in AREAS.items():
        ja[f"area.{NS}.{k}"], en[f"area.{NS}.{k}"] = j, e
        ja[f"area.{NS}.{k}.sub"] = roman
        en[f"area.{NS}.{k}.sub"] = roman
    for i, ((tj, bj), (te, be)) in NOTES.items():
        ja[f"note.{NS}.{i}.title"], ja[f"note.{NS}.{i}.body"] = tj, bj
        en[f"note.{NS}.{i}.title"], en[f"note.{NS}.{i}.body"] = te, be
    for lang, d in (("ja", ja), ("en", en)):
        sub = os.path.join(HERE, "audio", f"subtitles_{lang}.json")
        if os.path.exists(sub):
            with open(sub, encoding="utf-8") as f:
                d.update(json.load(f))
    with open(os.path.join(OUT, "ja_jp.json"), "w", encoding="utf-8") as f:
        json.dump(dict(sorted(ja.items())), f, ensure_ascii=False, indent=2)
    with open(os.path.join(OUT, "en_us.json"), "w", encoding="utf-8") as f:
        json.dump(dict(sorted(en.items())), f, ensure_ascii=False, indent=2)
    missing = sorted(set(ja) ^ set(en))
    print(f"ja {len(ja)} keys, en {len(en)} keys, mismatched: {missing[:10]}")


if __name__ == "__main__":
    main()
