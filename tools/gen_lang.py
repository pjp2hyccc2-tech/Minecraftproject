#!/usr/bin/env python3
"""Writes assets/reisatsu/lang/ja_jp.json and en_us.json for the game (owner: game).

Block names of docs/DESIGN.md §7 are listed here; the sound subtitles come from tools/audio/subtitles_{ja,en}.json
(sound worker) when present. Run: python3 tools/gen_lang.py
"""
import json
import re
import os

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.normpath(os.path.join(HERE, "..", "src", "main", "resources", "assets", "reisatsu", "lang"))
NS = "reisatsu"

ITEMS = {
    "reisha_camera": ("霊写機", "Reisha-ki (Spirit Camera)"),
    "reisha_camera_finder": ("霊写機（ファインダー）", "Reisha-ki (raised)"),
    "film_pack": ("フィルムパック", "Film Pack"),
    "evidence_photo": ("霊の写真", "Spirit Photograph"),
    "flashlight": ("懐中電灯", "Flashlight"),
    "flashlight_on": ("懐中電灯（点灯）", "Flashlight (on)"),
    "ofuda": ("お札", "Ofuda"),
    "shop_key": ("店の鍵", "Shop Key"),
    "office_key": ("事務所の鍵", "Office Key"),
}
ITEM_DESC = {
    "reisha_camera": ("右クリックで構える。左クリックで撮影、ホイールでズーム。", "Right-click to raise. Left-click: shutter. Scroll: zoom."),
    "film_pack": ("右クリックで霊写機に装填する（+5枚）。", "Right-click to load it into the camera (+5)."),
    "evidence_photo": ("霊媒師に渡せば、名を読んでくれる。", "Hand it to the medium: she will read the name."),
    "flashlight": ("右クリックで点灯／消灯。灯りは、あちらにも見える。", "Right-click: on / off. They can see the light too."),
    "ofuda": ("捕まった時、一度だけ身代わりになる。", "When they take you, it takes your place. Once."),
    "shop_key": ("入口横の鍵スイッチを回す鍵。裏口も開く。", "Turns the key switch by the entrance. Opens the back door too."),
    "office_key": ("「事務所」と書かれた札が付いている。", "The tag reads \"Office\"."),
}
CAMERA_FILM = ("のこり %s枚", "%s shots left")

ENTITIES = {
    "visitor": ("来店者", "The Visitor"), "waitress": ("給仕", "The Waitress"), "chef": ("料理長", "The Chef"),
    "child": ("かくれんぼの子", "The Hiding Child"), "mirror_woman": ("鏡の女", "The Woman in the Mirror"),
    "manager": ("店長", "The Manager"), "medium": ("霊媒師 八雲トキ", "Yakumo Toki, the Medium"), "medium_car": ("霊媒師の車", "The Medium's Car"),
}
TRUE_NAMES = {
    "visitor": ("三浦 健司", "Kenji Miura"), "waitress": ("小野寺 千夏", "Chinatsu Onodera"), "chef": ("権藤 剛", "Tsuyoshi Gondo"),
    "child": ("春日 ゆうか", "Yuka Kasuga"), "mirror_woman": ("白石 玲子", "Reiko Shiraishi"), "manager": ("森田 浩一", "Koichi Morita"),
}
AREAS = {
    "vestibule": ("風除室", "Vestibule"), "waiting": ("待合・レジ", "Waiting Area & Register"), "dining": ("客席", "Dining Hall"),
    "kitchen": ("厨房", "Kitchen"), "fridge": ("冷蔵室", "Walk-in Fridge"), "corridor": ("通路", "Back Corridor"),
    "staff": ("休憩室", "Staff Room"), "office": ("事務所", "Office"), "storage": ("倉庫", "Storeroom"),
    "mens": ("男子トイレ", "Men's Restroom"), "womens": ("女子トイレ", "Women's Restroom"), "cavity": ("鏡の向こう", "Behind the Mirror"),
}
CIRCUITS = {
    "dining": ("客席", "Dining"), "kitchen": ("厨房", "Kitchen"), "restroom": ("トイレ", "Restrooms"), "back": ("バックヤード", "Back"),
    "sign": ("看板", "Signs"), "cameras": ("防犯カメラ", "Cameras"), "door": ("自動ドア", "Auto Doors"),
}
RULES = [
    ("一、霊に気付かれてはならない。気付かれた霊は、写真に写らない。", "I. Do not let a spirit notice you. A spirit that has noticed you will not show in a photograph."),
    ("一、霊は光と音に敏い。灯りは控え、足音を殺すこと。", "II. They are keen to light and sound. Keep your light low and your steps silent."),
    ("一、ファインダー越しにしか見えないものがいる。", "III. Some can only be seen through the finder."),
    ("一、顔が写らなければ、名は浮かばない。", "IV. If the face is not in the photo, no name will rise."),
    ("一、気付かれたら、逃げなさい。隠れなさい。お札が身代わりになるのは三度まで。",
     "V. If they notice you: run. Hide. The ofuda will take your place three times, no more."),
]
PHENOMENA = {
    "door": ("施錠した自動ドアが、チャイムと共にひとりでに開く", "The locked automatic door opens by itself, with its chime"),
    "taps": ("誰もいない厨房やトイレで、水道から水が流れている", "Taps running in the empty kitchen and restrooms"),
    "menus": ("テーブルのメニューが、床一面にばらまかれている", "The menus found scattered all over the floor"),
    "call": ("誰もいない席の呼び出しボタンが鳴る（特に七番）", "Call buttons ringing at empty tables (table 7, mostly)"),
    "chair": ("椅子が動いている。倒れている", "Chairs moved, knocked over"),
    "lights": ("照明の明滅、割れる蛍光灯、落ちるブレーカー", "Flickering lights, bursting tubes, a tripping breaker"),
    "plate": ("無人の厨房で皿が割れる", "Plates smashing in the empty kitchen"),
    "music": ("止めたはずの店内の音楽が流れる", "The store music playing, though it was switched off"),
    "pa": ("店内放送のチャイムと、聞き取れない声", "The PA chime, and a voice nobody can make out"),
    "phone": ("レジの電話が鳴る。出ると「……二時に……一名で……」", "The register phone rings. On the line: \"...at two... a table for one...\""),
    "tv": ("休憩室のテレビが勝手につく", "The staff room TV switching itself on"),
    "dryer": ("誰もいないトイレで、ハンドドライヤーが鳴る", "Hand dryers roaring in empty restrooms"),
    "drink": ("ドリンクバーが勝手に注がれる", "The drink bar pouring by itself"),
    "knock": ("冷蔵室や、使用中の個室の中から、ノックの音", "Knocking from inside the walk-in fridge and the occupied stall"),
    "bear": ("キッズコーナーのぬいぐるみが、こちらを向いている", "The stuffed bear in the kids' corner, turned to face you"),
    "glass": ("誰もいないテーブルに、水の入ったグラス", "Glasses of water on empty tables"),
    "prints": ("入口から七番テーブルまで続く、濡れた足跡", "Wet footprints from the door to table 7"),
    "board": ("店の前の黒板に「まだ　いますか」", "The board outside: \"ARE YOU STILL HERE\""),
    "crash": ("午前二時、遠くでバイクの音。急ブレーキ。衝突音。静寂", "2 a.m.: a motorbike far away. Screeching tyres. An impact. Silence."),
}

NOTES = {
    "report": (
        ("業務報告書（閉店後）",
         "ファミリーレストラン ひだまり　国道店\n報告者：閉店作業担当（深夜）\n\n"
         "閉店作業中、および閉店後の店内で、下記の現象が繰り返し報告されている。防犯カメラの映像には、いずれも人の姿は映っていない。\n\n"
         "本部への報告は、これで三度目になる。対応をお願いします。\nもう、夜のシフトに入りたがる者はいません。"),
        ("Incident report (after closing)",
         "Family restaurant HIDAMARI, highway branch\nFiled by: closing crew (night)\n\n"
         "During and after closing, the following phenomena keep being reported inside the restaurant. In none of them does the "
         "security footage show a person.\n\nThis is our third report to head office. Please do something.\n"
         "Nobody wants the night shift any more.")),
    "handover": (
        ("引き継ぎノート（深夜帯）",
         "H21.11.5　7番の呼び出し、誰もいないのに鳴る。故障？　業者呼びます　――森田\n"
         "H21.12.2　業者さん「異常なし」とのこと\n"
         "H22.3.14　7番、また鳴った。2時ちょうど\n"
         "H24.8.1　新人さんへ：トイレの水が流れていても、気にしないでください\n"
         "H27.6.20　女子トイレ3番、使用中のまま開かない。中には誰もいない（はず）\n"
         "H27.7.3　鏡のこと、新人には言わないで\n"
         "H30.2.9　権藤さんの包丁、絶対動かさないこと。動かしても、次の日には元の場所に戻ってる\n"
         "R1.10.31　店長、今月お休みゼロです。誰か代わってあげて\n"
         "R1.11.2　店長が事務所で寝てる。起こさないでおきます\n"
         "R1.11.3　\n"
         "R2.4.1　本日より深夜営業を休止します。閉店は午前1時\n"
         "R4.11.3　閉店後、自動ドアが開いた。チャイムも鳴った。誰もいなかった\n"
         "R8.10.30　もう誰も、夜のシフトに入りたがらない"),
        ("Handover notebook (night shift)",
         "Nov 5, 2009 - Table 7's call button rings with nobody there. Faulty? Calling the repairman. - Morita\n"
         "Dec 2, 2009 - Repairman says: \"nothing wrong\"\n"
         "Mar 14, 2010 - Table 7 rang again. Two o'clock sharp.\n"
         "Aug 1, 2012 - To the new staff: if the restroom taps are running, don't worry about it.\n"
         "Jun 20, 2015 - Women's stall 3 stays \"occupied\" and won't open. Nobody is in there (I think).\n"
         "Jul 3, 2015 - Don't tell the new ones about the mirror.\n"
         "Feb 9, 2018 - NEVER move Mr. Gondo's knife. If you do, it is back in its place the next day.\n"
         "Oct 31, 2019 - The manager hasn't had a day off all month. Somebody cover for him.\n"
         "Nov 2, 2019 - The manager is asleep in the office. Letting him sleep.\n"
         "Nov 3, 2019 - \n"
         "Apr 1, 2020 - From today: no more night opening. We close at 1 a.m.\n"
         "Nov 3, 2022 - After closing the automatic door opened. The chime rang. Nobody was there.\n"
         "Oct 30, 2026 - Nobody wants the night shift any more.")),
    "clipping": (
        ("新聞の切り抜き",
         "国道で２人死亡　バイクと歩行者\n（平成21年11月4日　地域面）\n\n"
         "3日午前2時ごろ、国道の通称「魔のカーブ」で、バイクがガードレールに衝突し、運転していた三浦健司さん（24）が死亡した。"
         "事故を目撃し、近くのファミリーレストランから駆け寄った同店従業員の小野寺千夏さん（19）が、後続のトラックにはねられ、まもなく死亡した。\n\n"
         "現場は見通しの悪いカーブで、過去にも死亡事故が相次いでいる。三浦さんは、同店で待ち合わせをしていた交際相手に会いに向かう途中だった。"),
        ("Newspaper clipping",
         "Two dead on the highway - motorcyclist and pedestrian\n(regional page, November 4, 2009)\n\n"
         "At about 2 a.m. on the 3rd, a motorcycle hit the guardrail on the stretch of highway known locally as \"the devil's curve\", "
         "killing its rider, Kenji Miura (24). Chinatsu Onodera (19), an employee of the nearby family restaurant who saw the accident "
         "and ran out to help, was struck by a following truck and died shortly after.\n\n"
         "The curve has poor visibility and has seen a string of fatal accidents. Mr. Miura was on his way to meet his girlfriend, "
         "who was waiting for him at the restaurant.")),
    "reservation": (
        ("予約台帳",
         "11/2　19:00　4名様　タナカ様\n11/2　20:30　2名様　（キャンセル）\n11/3　2:00　1名様　ミウラ様\n\n"
         "（ページをめくる。どの日付にも、同じ一行がある）\n\n"
         "2:00　1名様　ミウラ様\n2:00　1名様　ミウラ様\n2:00　1名様　ミウラ様\n2:00　1名様　ミウラ様\n\n"
         "深夜営業をやめた年のページにも。今年のページにも。\n全部、同じ筆跡で。"),
        ("Reservation book",
         "11/2  19:00  party of 4  Tanaka\n11/2  20:30  party of 2  (cancelled)\n11/3  2:00  party of 1  Miura\n\n"
         "(You turn the pages. Every date has the same line.)\n\n"
         "2:00  party of 1  Miura\n2:00  party of 1  Miura\n2:00  party of 1  Miura\n2:00  party of 1  Miura\n\n"
         "On the pages from the year the night opening stopped. On this year's pages.\nAll in the same handwriting.")),
    "diary": (
        ("店長の業務日誌",
         "業務日誌　森田\n\n"
         "10/24　夜勤の欠員、今週も自分で埋める。三十六時間目\n"
         "10/27　本部に人を頼んだ。「検討します」\n"
         "10/29　眠い。モニターを見ていると、客席に誰かいる気がする。誰もいない\n"
         "10/31　7番の呼び出しが鳴る。誰もいない。いらっしゃいませ\n"
         "11/1　いらっしゃいませ　いらっしゃいませ　いらっしゃいませ\n"
         "11/2　閉店作業。閉店作業。まだ閉められない。お客様がまだいる\n"
         "11/3　本日の営業は、終了いたしました\n"
         "11/3　本日の営業は、終了いたしました\n"
         "11/3　ほんじつの　えいぎょうは　しゅうりょう　いたしました"),
        ("The manager's log",
         "Shift log - Morita\n\n"
         "10/24 - Covering the night gaps myself again this week. Hour thirty-six.\n"
         "10/27 - Asked head office for staff. \"We'll look into it.\"\n"
         "10/29 - Tired. Watching the monitors I keep thinking someone is in the dining hall. Nobody is.\n"
         "10/31 - Table 7's call button rings. Nobody there. Welcome.\n"
         "11/1 - welcome welcome welcome\n"
         "11/2 - Closing up. Closing up. I can't close yet. There are still customers.\n"
         "11/3 - We are now closed for the day.\n"
         "11/3 - We are now closed for the day.\n"
         "11/3 - we are now closed for the day")),
    "drawing": (
        ("子供の絵",
         "（クレヨンの絵。オレンジ色の屋根のお店。にこにこ顔のお日さま）\n\n"
         "（テーブルの下に、小さな女の子。黄色い帽子。両手で顔をかくしている）\n\n"
         "（女の子のまわりだけ、黒く塗りつぶされている）\n\n"
         "もういいよ\n\nゆうか　5さい\n\n（裏に、大人の字で）　見つけてあげて"),
        ("A child's drawing",
         "(Crayon. A shop with an orange roof. A smiling sun.)\n\n"
         "(Under a table, a little girl. A yellow hat. Both hands over her face.)\n\n"
         "(Only around the girl, everything is coloured black.)\n\n"
         "READY!\n\nyuka  age 5\n\n(On the back, in an adult's hand:) Please find her.")),
    "flyer": (
        ("迷子のお知らせ",
         "情報をお寄せください\n\n春日 ゆうか ちゃん（5歳）\n\n"
         "平成13年11月2日 午後8時ごろ、当店「ひだまり」の店内で行方がわからなくなりました。\n"
         "黄色い帽子、水色のスモック（名札「ゆうか」）、赤い靴。\nご家族と、かくれんぼをしていたとのことです。\n\n"
         "お心当たりの方は、店員またはお近くの交番まで。\n\n（色褪せて、角が破れている。誰も剥がそうとしなかったらしい）"),
        ("MISSING CHILD",
         "Have you seen her?\n\nYuka Kasuga (age 5)\n\n"
         "Went missing inside this restaurant, HIDAMARI, at about 8 p.m. on November 2, 2001.\n"
         "Yellow hat, pale blue smock (name tag \"Yuka\"), red shoes.\nShe was playing hide-and-seek with her family.\n\n"
         "Please tell our staff or the nearest police box.\n\n(Faded, a corner torn. Nobody seems to have wanted to take it down.)")),
    "fire": (
        ("消防点検記録",
         "消防用設備等点検結果報告書（控）\n\n"
         "平成8年10月　点検　厨房フライヤー上部の排気ダクト、油脂付着多量。要清掃\n"
         "平成8年10月　店長回答　繁忙のため次回対応\n"
         "平成8年11月3日　（赤字で）厨房にて火災。料理長1名死亡\n"
         "平成9年1月　点検　ダクト交換済。異常なし\n"
         "平成23年　点検　夜間、厨房でまな板を叩く音がするとの申し送りあり（設備とは無関係）\n"
         "令和元年　点検　異常なし。※料理長の包丁には触れないこと（店長）"),
        ("Fire inspection record",
         "Fire equipment inspection report (copy)\n\n"
         "Oct 1996 - inspection: exhaust duct above the fryer heavily coated with grease. Cleaning required.\n"
         "Oct 1996 - manager's reply: too busy, next time.\n"
         "Nov 3, 1996 - (in red) Kitchen fire. Head chef, 1 dead.\n"
         "Jan 1997 - inspection: duct replaced. No faults.\n"
         "2011 - inspection: staff report the sound of chopping in the kitchen at night (not equipment related).\n"
         "2019 - inspection: no faults. NB: do not touch the head chef's knife (manager).")),
    "cleaning": (
        ("清掃チェック表（女子トイレ）",
         "床／便器／洗面台／鏡／ペーパー補充\n\n"
         "11/1　済　済　済　済　済　（佐藤）\n"
         "11/2　済　済　済　鏡、拭いても曇る　済　（佐藤）\n"
         "11/3　済　済　水が止まらない　鏡、内側から曇っている　済　（佐藤）\n"
         "11/4　3番個室　開かない。中から鍵。ノックしても返事なし\n"
         "11/5　鏡に手の跡。内側から\n"
         "11/6　"),
        ("Cleaning checklist (women's restroom)",
         "Floor / toilets / sinks / mirror / paper\n\n"
         "11/1 - done done done done done (Sato)\n"
         "11/2 - done done done mirror: still fogged after wiping done (Sato)\n"
         "11/3 - done done the water won't stop mirror: fogged FROM THE INSIDE done (Sato)\n"
         "11/4 - stall 3 won't open. Locked from inside. No answer when I knock.\n"
         "11/5 - handprints on the mirror. On the inside.\n"
         "11/6 - ")),
    "medium": (
        ("霊媒師のメモ",
         "撮影の心得　――　八雲\n\n" + "\n".join(r[0] for r in RULES) + "\n\n"
         "写真は、私のところへ持ってくること。名を読んで、送ってあげます。\n"
         "息を止めれば（V）、気配は消せる。ただし、長くはもたないわ。\n"
         "店の中のことは、店の者が書き残しているはずよ。"),
        ("The medium's notes",
         "On photographing them - Yakumo\n\n" + "\n".join(r[1] for r in RULES) + "\n\n"
         "Bring the photographs to me. I will read their names and send them on.\n"
         "Hold your breath (V) and your presence fades. Not for long.\n"
         "The staff will have written down what happens in there. Read it.")),
}

# the intro's black screen: the first night's card (NIGHTS[0]), then these four lines
INTRO = [
    ("国道沿いのファミリーレストラン「ひだまり」\n店の前のカーブでは、事故が絶えない",
     "HIDAMARI, a family restaurant on the national highway.\nAt the curve in front of it, people keep dying."),
    ("深夜営業をやめてから、閉店作業の店員が\nおかしなものを見るようになった", "Since it stopped opening at night,\nthe closing crew keep seeing things."),
    ("閉めたはずの自動ドアが開く。誰もいない水道から水が流れる。\nテーブルのメニューが、床にばらまかれている",
     "The door they locked opens by itself. Taps run with nobody there.\nThe menus are found scattered across the floor."),
    ("霊媒師に雇われて、あなたはここに来た。\n――七日のあいだ、毎晩", "A medium hired you. You have come here.\n- every night, for seven nights."),
]

# the week (docs §1 Flow): title card, date, short label of each night
NIGHTS = [
    (("一日目の夜", "The First Night"), ("十一月三日（火）", "Tuesday, November 3"), ("一日目", "Night 1")),
    (("二日目の夜", "The Second Night"), ("十一月四日（水）", "Wednesday, November 4"), ("二日目", "Night 2")),
    (("三日目の夜", "The Third Night"), ("十一月五日（木）", "Thursday, November 5"), ("三日目", "Night 3")),
    (("四日目の夜", "The Fourth Night"), ("十一月六日（金）", "Friday, November 6"), ("四日目", "Night 4")),
    (("五日目の夜", "The Fifth Night"), ("十一月七日（土）", "Saturday, November 7"), ("五日目", "Night 5")),
    (("六日目の夜", "The Sixth Night"), ("十一月八日（日）", "Sunday, November 8"), ("六日目", "Night 6")),
    (("七日目の夜", "The Seventh Night"), ("十一月九日（月）", "Monday, November 9"), ("七日目", "Night 7")),
]

# what was seen: the HUD's 「記録：…」 line (night 1: the phenomena; later nights: the signs of the night's spirit)
WITNESS = {
    "door": ("閉めたはずの自動ドアが開いた", "The locked automatic door opened"),
    "taps": ("誰もいないのに、水が流れている", "Water running, with nobody there"),
    "menus": ("テーブルのメニューが、床に散らばった", "The menus flew off a table onto the floor"),
    "call": ("誰もいない席の呼び出しボタンが鳴った", "A call button rang at an empty table"),
    "chair": ("椅子が、ひとりでに動いた", "A chair moved by itself"),
    "lights": ("照明が、ひとりでに明滅した", "The lights flickered by themselves"),
    "plate": ("無人の厨房で、皿が割れた", "A plate smashed in the empty kitchen"),
    "music": ("止めたはずの店内音楽が流れた", "The store music came on by itself"),
    "pa": ("店内放送のチャイム。……声は聞き取れない", "The PA chimed. ...The voice can't be made out"),
    "phone": ("レジの電話が鳴った", "The register phone rang"),
    "tv": ("休憩室のテレビが、勝手についた", "The staff room TV switched itself on"),
    "dryer": ("誰もいないトイレで、ハンドドライヤーが鳴った", "A hand dryer roared in an empty restroom"),
    "drink": ("ドリンクバーが、ひとりでに注いだ", "The drink bar poured by itself"),
    "knock": ("中から、ノックの音がした", "Knocking - from the inside"),
    "bear": ("ぬいぐるみが、こちらを向いている", "The stuffed bear has turned to face you"),
    "glass": ("誰もいないテーブルに、お冷やが置かれた", "A glass of water, set at an empty table"),
    "prints": ("濡れた足跡が、店の奥へ続いている", "Wet footprints, leading inside"),
    "board": ("店の前の黒板の文字が、変わっている", "The board outside says something else now"),
    "crash": ("遠くでバイクの音。……衝突音。それから、静寂", "A motorbike far off. ...An impact. Then silence"),
    "greet": ("暗がりから「いらっしゃいませ」", "\"Welcome\" - from the dark"),
    "tray": ("どこかで、お盆が鳴った", "A tray clattered somewhere"),
    "giggle": ("すぐ後ろで、子供の笑い声", "A child's giggle, right behind you"),
    "ball": ("キッズコーナーで、何かが跳ねた", "Something bounced in the kids' corner"),
    "sob": ("鏡の方から、すすり泣く声", "Sobbing, from the mirror"),
    "gas": ("コンロに、ひとりでに火が点いた", "The gas range lit itself"),
    "chop": ("包丁の音。……近づくと、止んだ", "Chopping. ...It stopped as you came near"),
    "keys": ("通路の奥で、鍵束の鳴る音", "Keys jingling down the corridor"),
    "camera": ("防犯カメラのランプが、赤く点いた", "A security camera's light turned red"),
}

DIALOGUE = {
    # the medium, in the parking lot
    "intro.1": ("……来てくれたのね。霊媒師の、八雲トキです", "...you came. I am Yakumo Toki. A medium."),
    "intro.2": ("この店には、帰れない人たちがいる", "There are people in there who cannot go home."),
    "intro.3": ("私が入れば、あの人たちは隠れてしまう。だから、あなたに頼むの", "If I go in, they will hide from me. That is why I need you."),
    "intro.4": ("これを。霊写機――目に見えないものを写す、古い写真機よ", "Take this. The reisha-ki - an old camera that photographs what the eye cannot see."),
    "intro.5": ("霊の顔が写れば、写真の白い余白に、その人の名が赤く浮かぶ", "If a spirit's face is in the photo, its name rises in red on the white margin."),
    "intro.6": ("名がわかれば、私が送ってあげられる。……でも、急いではだめ",
                "With the names I can send them on. ...But we must not hurry."),
    "intro.7": ("いいこと。霊に気付かれてはいけない。気付かれた霊は、写真に写らないの",
                "Listen. They must not notice you. A spirit that has noticed you will not show in a photograph."),
    "intro.8": ("お札を三枚。……身代わりになってくれるのは、三度まで。これから先、ずっとよ",
                "Three ofuda. ...They will take your place three times - for all the nights to come. No more."),
    "intro.9": ("懐中電灯と、店の鍵。入口の横の鍵スイッチを回せば、自動ドアが動くわ",
                "A flashlight, and the shop key. Turn the key switch by the entrance and the automatic doors will work."),
    "intro.10": ("今夜は、撮らなくていい。あの店で何が起きているのか……五つ、その目で確かめてきて",
                 "Tonight, photograph nothing. Go and see what happens in there... five things, with your own eyes."),
    # the start of each night (Week.OPENING: their durations)
    "open.1.1": ("フィルムは足しておいたわ", "I've topped up your film."),
    "open.1.2": ("今夜は、撮らなくていい。あの店で何が起きているのか、その目で確かめてきて",
                 "Tonight you needn't photograph anything. Go and see for yourself what happens in there."),
    "open.1.3": ("五つ見たら、戻ってらっしゃい", "When you have seen five things, come back to me."),
    "open.2.1": ("フィルムは足しておいたわ", "I've topped up your film."),
    "open.2.2": ("今夜は、二時に来るお客さん", "Tonight: the customer who comes at two."),
    "open.2.3": ("遠くでバイクの音がしたら、入口から目を離さないで", "When you hear a motorbike far away, keep your eyes on the entrance."),
    "open.2.4": ("濡れた足跡の先で、あの人は待っているわ", "At the end of the wet footprints, he is waiting."),
    "open.3.1": ("フィルムは足しておいたわ", "I've topped up your film."),
    "open.3.2": ("今夜は、あの店で働いていた子", "Tonight: the girl who worked there."),
    "open.3.3": ("誰もいないテーブルに、お冷やが出ていたら……あの子は近くにいる",
                 "If there is a glass of water on an empty table... she is close."),
    "open.3.4": ("扉が開くと、入口でお辞儀をするの。その間だけは、目を閉じているわ",
                 "When a door opens she bows at the entrance. Only then are her eyes closed."),
    "open.4.1": ("フィルムは足しておいたわ", "I've topped up your film."),
    "open.4.2": ("今夜は、小さな子。……かくれんぼの途中で、いなくなった子よ",
                 "Tonight: a little girl. ...She vanished in the middle of a game of hide-and-seek."),
    "open.4.3": ("笑い声が聞こえても、慌てないで。遊びが始まる合図だから", "If you hear giggling, don't panic. It means the game is about to begin."),
    "open.4.4": ("隠れている時は、両手で目を覆っているわ。……見つけてあげて", "When she hides, she covers her eyes with both hands. ...Find her."),
    "open.5.1": ("フィルムは足しておいたわ", "I've topped up your film."),
    "open.5.2": ("今夜は、鏡の向こうにいる人", "Tonight: the one on the other side of the mirror."),
    "open.5.3": ("水の音がしたら、女子トイレへ。俯いて泣いている間は、こちらが見えないの",
                 "When you hear water running, go to the women's restroom. While she bows and weeps, she cannot see you."),
    "open.5.4": ("顔を上げたら、鏡の前に立っていてはだめ。……息を殺して", "When she raises her head, don't be standing at the mirror. ...Hold your breath."),
    "open.6.1": ("フィルムは足しておいたわ", "I've topped up your film."),
    "open.6.2": ("今夜は、厨房。……この店の、料理長さん", "Tonight: the kitchen. ...The head chef."),
    "open.6.3": ("包丁の音が止まったら、あの人は振り返っている", "When the chopping stops, he is looking over his shoulder."),
    "open.6.4": ("仕上げの時だけ、下を向くの。デシャップの窓から、覗いてごらんなさい", "Only when he plates does he look down. Try the pass window."),
    "open.7.1": ("フィルムは足しておいたわ", "I've topped up your film."),
    "open.7.2": ("……最後の夜よ。今夜は、店長さん", "...The last night. Tonight: the manager."),
    "open.7.3": ("あの人は、まだ店を閉め続けている。防犯カメラが、あの人の目", "He is still closing the store. The security cameras are his eyes."),
    "open.7.4": ("カメラの電気を切れば、見えなくなるわ。……行ってらっしゃい", "Cut their power and he is blind. ...Go on."),
    # the end of each night: night 1 her explanation; nights 2-6 goodnight after the spirit's story
    "end.1.1": ("……見てきたのね", "...So you saw it."),
    "end.1.2": ("あの店にいるのは、六人。みんな、まだ帰れずにいる", "There are six of them in there. None of them can go home."),
    "end.1.3": ("一度に会おうとしては、だめ。一晩に、ひとりずつよ", "Don't try to meet them all at once. One a night."),
    "end.1.4": ("明日の夜は、二時に扉から入ってくる人。……今夜は、もうお休みなさい",
                "Tomorrow night: the one who comes through the door at two. ...Now go and rest."),
    "end.2": ("明日は、あの夜、彼を助けようとした子よ。……おやすみなさい", "Tomorrow: the girl who tried to help him that night. ...Good night."),
    "end.3": ("明日は、まだ隠れている子。……おやすみなさい", "Tomorrow: the child who is still hiding. ...Good night."),
    "end.4": ("明日は、鏡の向こうの人。……おやすみなさい", "Tomorrow: the one behind the mirror. ...Good night."),
    "end.5": ("明日は、厨房。まだ焦げた匂いのする人よ。……おやすみなさい", "Tomorrow: the kitchen. The one who still smells of smoke. ...Good night."),
    "end.6": ("明日で、最後。……店を閉める人が、まだ残っている。おやすみなさい",
              "Tomorrow is the last. ...The one who closes the store is still there. Good night."),
    # handing her the photos
    "submit.visitor.1": ("……三浦、健司さん。二十四歳", "...Kenji Miura. Twenty-four."),
    "submit.visitor.2": ("平成二十一年の、十一月三日。恋人に会いに、夜中の二時に、バイクでこの店へ向かっていた",
                         "November 3rd, 2009. He was riding here at two in the morning to meet his girlfriend."),
    "submit.visitor.3": ("店の前のカーブで……。それから毎晩二時に、扉が開くの", "At the curve out front... Since then, every night at two, the door opens."),
    "submit.visitor.4": ("七番テーブルで呼び出しボタンを押して……まだ、待っているのね", "He presses the call button at table 7... still waiting for her."),
    "submit.waitress.1": ("小野寺、千夏さん。十九歳", "Chinatsu Onodera. Nineteen."),
    "submit.waitress.2": ("あの夜の夜勤だった子。店の前で事故があって、助けようと道路へ飛び出した",
                          "She was on the night shift that night. When the crash happened she ran out onto the road to help."),
    "submit.waitress.3": ("……後ろから来たトラックに", "...the truck behind him."),
    "submit.waitress.4": ("今でも夜勤を続けているの。誰にも見えないお客様に、お冷やを出して",
                          "She is still working the night shift. Serving water to customers nobody else can see."),
    "submit.chef.1": ("権藤、剛さん。五十二歳。この店の料理長", "Tsuyoshi Gondo. Fifty-two. The head chef."),
    "submit.chef.2": ("平成八年。厨房の油火事で亡くなった。顔も、手も、焼けてしまって", "1996. He died in a grease fire in the kitchen. His face, his hands, all burnt."),
    "submit.chef.3": ("ダクトの掃除を、何度も頼んでいたそうよ", "He had asked them to clean the duct, again and again."),
    "submit.chef.4": ("それでもまだ、まな板の前に立っているのね", "And still he stands at his board."),
    "submit.child.1": ("春日、ゆうかちゃん。五歳", "Yuka Kasuga. Five years old."),
    "submit.child.2": ("平成十三年。この店で、かくれんぼをしている最中にいなくなった", "2001. She disappeared in this restaurant, in the middle of a game of hide-and-seek."),
    "submit.child.3": ("……見つからなかったの。誰にも", "...nobody ever found her."),
    "submit.child.4": ("あの子はまだ遊んでいる。上手に隠れて、誰かが見つけてくれるのを、待ってる",
                       "She is still playing. Hiding so well, waiting for someone to find her."),
    "submit.mirror_woman.1": ("白石、玲子さん。三十一歳", "Reiko Shiraishi. Thirty-one."),
    "submit.mirror_woman.2": ("平成二十七年。常連さんだった。女子トイレで倒れて、朝まで誰も気付かなかった",
                              "2015. A regular. She collapsed in the women's restroom, and nobody noticed until morning."),
    "submit.mirror_woman.3": ("見つかった時、蛇口は流れたままだったそうよ", "When they found her, the tap was still running."),
    "submit.mirror_woman.4": ("今は、鏡の向こう側にいる。……ずっと、誰かに気付いてほしかったのね",
                              "Now she is on the other side of the mirror. ...All she wanted was for someone to notice."),
    "submit.manager.1": ("森田、浩一さん。四十七歳。……店長さんね", "Koichi Morita. Forty-seven. ...The manager."),
    "submit.manager.2": ("何年も、夜をひとりで回していた。令和元年、事務所の机で", "For years he ran the nights alone. In 2019, at his desk in the office..."),
    "submit.manager.3": ("過労死だった。……それから、この店は夜を閉めるようになった", "Worked to death. ...After that, the store stopped opening at night."),
    "submit.manager.4": ("でもあの人は、まだ店を閉め続けているのね", "But he is still closing the store. Every night."),
    "ready.1": ("……六人、揃ったわね", "...All six. We have them all."),
    "ready.2": ("行きましょう。七番テーブルへ", "Come. To table 7."),
    # hints: the first night (what is left to see), before the night's spirit shows, then how to photograph it
    "hint.n1.left.1": ("あと、ひとつ", "One more."),
    "hint.n1.left.2": ("あと、ふたつ", "Two more."),
    "hint.n1.left.3": ("あと、三つ", "Three more."),
    "hint.n1.left.4": ("あと、四つ", "Four more."),
    "hint.n1.left.5": ("まだ、何も見ていないのね", "You haven't seen anything yet."),
    "hint.n1": ("耳を澄ませて、目を凝らして。……あの店は、ひとりでに動くわ", "Listen. Watch. ...That place moves by itself."),
    "hint.wait.visitor": ("まだ、二時になっていないわ。……入口から目を離さないで", "It isn't two yet. ...Keep your eyes on the entrance."),
    "hint.wait.waitress": ("誰もいないテーブルを見てごらんなさい。……お冷やが出ていたら、あの子はそばにいる",
                           "Look at the empty tables. ...Where there is a glass of water, she isn't far."),
    "hint.wait.child": ("笑い声がしたら、あの子は近くにいるわ。……まだ、隠れてもいないのね", "If you hear giggling, she is close. ...She hasn't even hidden yet."),
    "hint.wait.mirror_woman": ("女子トイレよ。……水の音を聞くまで、あの人は出てこない", "The women's restroom. ...She won't come until you have heard the water."),
    "hint.wait.chef": ("厨房よ。皿の割れる音、火の点く音。……包丁の音を探して", "The kitchen. Plates breaking, the gas catching. ...Listen for the chopping."),
    "hint.wait.manager": ("閉店の放送が流れたら……あの人が、起きる", "When the closing announcement plays... he wakes."),
    "hint.visitor": ("二時になると、扉が開く。七番のお客さんの横顔は、南の窓から見えるはずよ",
                     "At two the door opens. You should see table 7's customer in profile through the south window."),
    "hint.waitress": ("扉が開くと、あの子は入口でお辞儀をする。お辞儀の間は、目を閉じているわ",
                      "When a door opens she goes to bow at the entrance. While she bows, her eyes are closed."),
    "hint.chef": ("料理長は、仕上げの時に下を向く。デシャップの窓から覗いてごらんなさい",
                  "When the chef plates, he looks down. Try the pass window."),
    "hint.child": ("あの子は隠れている時、両手で目を覆っている。……見つけてあげて", "When she hides, she covers her eyes. ...Find her."),
    "hint.mirror_woman": ("女子トイレの鏡。水が流れている間、あの人は俯いて泣いている。息を殺して",
                          "The mirror in the women's restroom. While the water runs she bows and weeps. Hold your breath."),
    "hint.manager": ("店長さんは、防犯カメラで見ている。カメラの電気を切れば、見えなくなるわ",
                     "The manager watches through the security cameras. Cut their power and he is blind."),
    "hint.closing": ("店長さんが起きたわね。……閉店の時間よ", "The manager is awake. ...It is closing time."),
    "hint.return": ("写真を、私に", "The photograph. Give it to me."),
    # waking after being caught
    "wake.1": ("……間に合ってよかった", "...I got to you in time."),
    "wake.2": ("お札が燃えたわ。……気を付けて", "An ofuda has burnt. ...Be careful."),
    "wake.3": ("もう、お札はないわ。次は、助けられない", "There are no ofuda left. Next time I cannot save you."),
    # the exorcism
    "exorcism.begin": ("……始めましょう", "...Let us begin."),
    "release.visitor": ("三浦さん。……もう、待たなくていいのよ", "Mr. Miura. ...You don't have to wait any more."),
    "release.waitress": ("千夏さん。お仕事は、もうおしまい", "Chinatsu. Your shift is over."),
    "release.chef": ("権藤さん。火は、もう消えたわ", "Mr. Gondo. The fire is out now."),
    "release.child": ("ゆうかちゃん。……みーつけた", "Yuka. ...Found you."),
    "release.mirror_woman": ("玲子さん。ちゃんと、気付いたわよ", "Reiko. We noticed you. We did."),
    "release.manager": ("森田さん。……お疲れさまでした", "Mr. Morita. ...Thank you for all your hard work."),
    # other voices
    "waitress.greet": ("いらっしゃいませ", "Welcome."),
    "waitress.water": ("お冷やを、どうぞ", "Some water for you."),
    "waitress.thanks": ("……ありがとうございました", "...Thank you very much."),
    "waitress.single": ("いらっしゃいませ。……お一人様ですね", "Welcome. ...A table for one, isn't it."),
    "manager.closed": ("本日の営業は、終了いたしました", "We are now closed for the day."),
    "manager.customer": ("お客様……", "Excuse me... sir..."),
    "pa.closed": ("本日の営業は、終了いたしました", "We are now closed for the day."),
    "pa.mumble": ("（店内放送：聞き取れない）", "(PA: unintelligible)"),
    "phone.1": ("……ザ……二時に……", "...kssh... at two..."),
    "phone.2": ("……一名で……", "...a table for one..."),
    "child.moui": ("もういいかい", "Ready or not?"),
    "child.mitsuketa": ("みーつけた", "Found you!"),
    "child.count": ("いーち、にーい、さーん、しーい……", "One... two... three... four..."),
    "child.whisper": ("こっちだよ", "Over here."),
    "child.madada": ("まーだだよ", "Not yet!"),
    "mirror.whisper": ("……うしろ", "...behind you."),
    "manager.welcome": ("……いらっしゃいませ", "...welcome."),
    "visitor.whisper": ("……まだかな", "...is she coming?"),
}

THOUGHTS = {
    "film_loaded": ("フィルムを装填した", "Film loaded."),
    "film_found": ("フィルムパックだ", "A film pack."),
    "note_blank": ("……白紙だ。濡れて、波打っている", "...Blank. Wet, and warped."),
    "office_key": ("「事務所」の札が付いた鍵だ", "A key tagged \"Office\"."),
    "menu_board": ("黒板。「本日のおすすめ」……", "The board: \"Today's special\"..."),
    "menu_board_2": ("「まだ　いますか」", "\"ARE YOU STILL HERE\""),
    "time_recorder": ("タイムカード。森田、の欄だけ、毎日打刻されている", "Time cards. Only Morita's is punched. Every single day."),
    "fridge": ("……息が白い", "...your breath clouds."),
    "womens": ("水の音がする", "The sound of running water."),
    "kitchen": ("焦げた油の匂いが、まだ残っている", "The smell of burnt oil still hangs here."),
    "office": ("モニターの光だけが、部屋を照らしている", "Only the monitors light the room."),
    "quiet": ("……静かになった", "...it has gone quiet."),
    "observed": ("……これが、この店で起きていること", "...So this is what happens here."),
}

BLOCK_NAMES = {
    "dining_floor": ("客席の床", "Dining Floor"), "dining_floor_worn": ("すり減った客席の床", "Worn Dining Floor"),
    "two_sided_wall": ("間仕切り壁", "Partition Wall"), "power_wire": ("電線", "Power Line"), "kitchen_floor": ("厨房の床", "Kitchen Floor"), "restroom_floor": ("トイレの床", "Restroom Floor"),
    "restroom_floor_dark": ("黒ずんだタイル", "Stained Tiles"), "vestibule_floor": ("風除室の床", "Vestibule Floor"), "entrance_mat": ("玄関マット", "Entrance Mat"),
    "wallpaper": ("壁紙", "Wallpaper"), "wallpaper_stained": ("染みのある壁紙", "Stained Wallpaper"), "wainscot": ("腰板", "Wainscot"),
    "white_tile": ("白タイル", "White Tile"), "white_tile_dirty": ("汚れた白タイル", "Dirty White Tile"), "ceiling_tile": ("天井板", "Ceiling Tile"),
    "ceiling_tile_stained": ("染みのある天井板", "Stained Ceiling Tile"), "ceiling_light": ("天井照明", "Ceiling Light"), "downlight": ("ダウンライト", "Downlight"),
    "pendant_lamp": ("ペンダントライト", "Pendant Lamp"), "exit_sign": ("誘導灯", "Exit Sign"), "exterior_wall": ("外壁", "Exterior Wall"),
    "facade_brick": ("煉瓦", "Facade Brick"), "brick_sill": ("煉瓦の窓台", "Brick Sill"), "roof_tile": ("屋根瓦", "Roof Tiles"),
    "roof_tile_stairs": ("屋根瓦の階段", "Roof Tile Stairs"), "roof_tile_slab": ("屋根瓦のハーフ", "Roof Tile Slab"), "fascia": ("破風", "Fascia"),
    "shop_window": ("窓ガラス", "Shop Window"), "window_blind": ("ブラインド", "Window Blind"), "asphalt": ("アスファルト", "Asphalt"),
    "parking_line": ("駐車線", "Parking Line"), "road_line": ("センターライン", "Road Line"), "curb": ("縁石", "Curb"), "sidewalk": ("歩道", "Sidewalk"),
    "puddle": ("水たまり", "Puddle"), "rice_paddy": ("田んぼ", "Rice Paddy"), "hedge": ("生垣", "Hedge"), "concrete_wall": ("コンクリート壁", "Concrete Wall"),
    "granite_counter": ("御影石の天板", "Granite Counter"), "stainless": ("ステンレス", "Stainless Steel"), "pillar": ("柱", "Pillar"),
    "auto_door": ("自動ドア", "Automatic Door"), "door_switch": ("自動ドアの鍵スイッチ", "Door Key Switch"), "swing_door": ("スイングドア", "Swing Door"),
    "staff_door": ("従業員用扉", "Staff Door"), "office_door": ("事務所の扉", "Office Door"), "restroom_door_m": ("男子トイレの扉", "Men's Room Door"),
    "restroom_door_w": ("女子トイレの扉", "Women's Room Door"), "back_door": ("裏口", "Back Door"), "fridge_door": ("冷蔵室の扉", "Walk-in Door"),
    "stall_door": ("個室の扉", "Stall Door"), "booth_seat": ("ボックス席", "Booth Seat"), "booth_partition": ("仕切り", "Booth Partition"),
    "restaurant_table": ("テーブル", "Table"), "restaurant_chair": ("椅子", "Chair"), "call_button": ("呼び出しボタン", "Call Button"),
    "menu_stand": ("メニュースタンド", "Menu Stand"), "menu_scattered": ("散らばったメニュー", "Scattered Menus"), "water_glass": ("お冷や", "Glass of Water"),
    "broken_plate": ("割れた皿", "Broken Plate"), "register": ("レジ", "Register"), "service_bell": ("呼び鈴", "Service Bell"), "counter": ("カウンター", "Counter"),
    "cake_case": ("ケーキケース", "Cake Case"), "business_phone": ("店の電話", "Business Phone"), "reservation_book": ("予約台帳", "Reservation Book"),
    "capsule_machine": ("カプセルトイ", "Capsule Toys"), "waiting_bench": ("待合ベンチ", "Waiting Bench"), "magazine_rack": ("マガジンラック", "Magazine Rack"),
    "umbrella_stand": ("傘立て", "Umbrella Stand"), "notice_board": ("掲示板", "Notice Board"), "drink_bar": ("ドリンクバー", "Drink Bar"),
    "coffee_machine": ("コーヒーマシン", "Coffee Machine"), "ice_machine": ("製氷機", "Ice Machine"), "cup_shelf": ("カップ棚", "Cup Shelf"),
    "soup_warmer": ("スープジャー", "Soup Warmer"), "server_station": ("サービスステーション", "Server Station"),
    "call_display": ("呼び出し表示器", "Call Display"), "planter": ("プランター", "Planter"), "kids_mat": ("キッズマット", "Kids' Mat"),
    "toy_shelf": ("おもちゃ棚", "Toy Shelf"), "stuffed_bear": ("くまのぬいぐるみ", "Stuffed Bear"), "kids_fence": ("キッズフェンス", "Kids' Fence"),
    "high_chair": ("子供椅子", "High Chair"), "gas_range": ("ガスレンジ", "Gas Range"), "fryer": ("フライヤー", "Fryer"), "griddle": ("グリドル", "Griddle"),
    "kitchen_counter": ("調理台", "Kitchen Counter"), "exhaust_hood": ("排気フード", "Exhaust Hood"), "pot_shelf": ("鍋棚", "Pot Shelf"),
    "dish_rack": ("食器ラック", "Dish Rack"), "kitchen_trash": ("厨房のゴミ箱", "Kitchen Bin"), "prep_table": ("作業台", "Prep Table"),
    "cutting_board": ("まな板", "Cutting Board"), "kitchen_sink": ("シンク", "Kitchen Sink"), "dishwasher": ("食洗機", "Dishwasher"),
    "pass_shelf": ("デシャップ台", "Pass Shelf"), "pass_bell": ("デシャップベル", "Pass Bell"), "walkin_shelf": ("冷蔵室の棚", "Walk-in Shelf"),
    "frost": ("霜", "Frost"), "locker": ("ロッカー", "Locker"), "time_recorder": ("タイムレコーダー", "Time Clock"), "staff_table": ("休憩室のテーブル", "Staff Table"),
    "folding_chair": ("パイプ椅子", "Folding Chair"), "bulletin_board": ("掲示板（休憩室）", "Bulletin Board"), "cardboard_boxes": ("段ボール", "Cardboard Boxes"),
    "cleaning_cart": ("清掃カート", "Cleaning Cart"), "filing_cabinet": ("キャビネット", "Filing Cabinet"), "safe": ("金庫", "Safe"),
    "crt_tv": ("ブラウン管テレビ", "CRT TV"), "office_desk": ("事務机", "Office Desk"), "office_chair": ("事務椅子", "Office Chair"), "pc": ("パソコン", "PC"),
    "security_monitors": ("防犯モニター", "Security Monitors"), "security_camera": ("防犯カメラ", "Security Camera"), "breaker_panel": ("分電盤", "Breaker Panel"),
    "restroom_sink": ("洗面台", "Restroom Sink"), "mirror": ("鏡", "Mirror"), "restroom_mirror": ("鏡", "Mirror"), "toilet": ("便器", "Toilet"),
    "urinal": ("小便器", "Urinal"), "stall_wall": ("個室の壁", "Stall Wall"), "hand_dryer": ("ハンドドライヤー", "Hand Dryer"),
    "paper_towel": ("ペーパータオル", "Paper Towels"), "vending_machine": ("自動販売機", "Vending Machine"), "pole_sign": ("ポールサイン", "Pole Sign"),
    "roof_sign": ("屋上看板", "Roof Sign"), "menu_board": ("黒板", "Menu Board"), "bike_rack": ("駐輪場", "Bike Rack"), "trash_station": ("ゴミステーション", "Trash Station"),
    "ac_unit": ("室外機", "A/C Unit"), "gas_cylinder": ("ガスボンベ", "Gas Cylinder"), "dumpster": ("ゴミコンテナ", "Dumpster"), "wheel_stop": ("車止め", "Wheel Stop"),
    "outdoor_planter": ("植木鉢", "Planter"), "street_lamp": ("街灯", "Street Lamp"), "utility_pole": ("電柱", "Utility Pole"), "guardrail": ("ガードレール", "Guardrail"),
    "chevron_sign": ("矢印標識", "Chevron Sign"), "curve_mirror": ("カーブミラー", "Curve Mirror"), "traffic_light": ("信号機", "Traffic Light"),
    "flower_offering": ("献花", "Flowers"), "note_paper": ("メモ", "Note"),
}

COMMON = {
    "itemGroup.reisatsu": ("霊撮 ―REISATSU―", "REISATSU"),
    "key.categories.reisatsu": ("霊撮", "REISATSU"),
    "key.reisatsu.breath": ("息を止める（長押し）", "Hold your breath"),
    "key.reisatsu.album": ("霊撮帳", "Album"),
    "reisatsu.shaders.installed": ("影MOD「ReisatsuShaders」をインストールして有効にしました。（Oculus / Iris）",
                                   "Installed and enabled the ReisatsuShaders shader pack (Oculus / Iris)."),
    "reisatsu.shaders.updated": ("ReisatsuShaders を更新しました。", "ReisatsuShaders was updated."),
    "reisatsu.shaders.optifine": ("ReisatsuShaders を OptiFine に設定しました。次回の起動から有効になります。",
                                  "ReisatsuShaders was set for OptiFine; it will be active from the next launch."),
    "objective.reisatsu.observe": ("霊障を確かめる　%s/%s", "Witness the hauntings  %s/%s"),
    "objective.reisatsu.signs": ("異変を探る", "Look for the signs"),
    "objective.reisatsu.photo.visitor": ("来店者を撮影する", "Photograph the visitor"),
    "objective.reisatsu.photo.waitress": ("給仕を撮影する", "Photograph the waitress"),
    "objective.reisatsu.photo.child": ("かくれんぼの子を撮影する", "Photograph the hiding child"),
    "objective.reisatsu.photo.mirror_woman": ("鏡の女を撮影する", "Photograph the woman in the mirror"),
    "objective.reisatsu.photo.chef": ("料理長を撮影する", "Photograph the chef"),
    "objective.reisatsu.photo.manager": ("店長を撮影する", "Photograph the manager"),
    "objective.reisatsu.return": ("霊媒師のもとへ戻る", "Return to the medium"),
    "objective.reisatsu.exorcism": ("除霊を見届ける", "Witness the exorcism"),
    "objective.reisatsu.hand_in": ("写真を霊媒師に渡せる（%s枚）", "Photos to give the medium: %s"),
    "hud.reisatsu.film_left": ("のこり %s枚", "%s left"),
    "hud.reisatsu.no_film": ("フィルムがない", "No film"),
    "hud.reisatsu.meter": ("霊感計", "SPIRIT"),
    "hud.reisatsu.closed": ("閉店", "CLOSED"),
    "hud.reisatsu.run": ("――逃げて　%s", "- RUN  %s"),
    "hud.reisatsu.ofuda_burnt": ("お札が、燃えた　（残り %s枚）", "An ofuda burnt  (%s left)"),
    "hud.reisatsu.skip": ("SPACE：スキップ", "SPACE: skip"),
    "hud.reisatsu.witness": ("記録：%s", "Noted: %s"),
    "hint.reisatsu.controls": ("右クリック：霊写機を構える／下ろす\n構えて左クリック：撮影　ホイール：ズーム\nスニーク：足音を殺す　V（長押し）：息を止める\nJ：霊撮帳　右クリック：調べる",
                               "Right-click: raise / lower the camera\nRaised: left-click = shutter, scroll = zoom\nSneak: silent steps   V (hold): hold your breath\nJ: album   Right-click: interact"),
    "gui.reisatsu.album": ("霊撮帳", "Spirit Album"),
    "gui.reisatsu.tab_shots": ("写真", "Photos"),
    "gui.reisatsu.tab_notes": ("メモ", "Notes"),
    "gui.reisatsu.tab_rules": ("心得", "Rules"),
    "gui.reisatsu.tab_nights": ("七夜", "Week"),
    "gui.reisatsu.sent": ("送った", "sent on"),
    "gui.reisatsu.night_observed": ("霊障を確かめた", "Witnessed the hauntings"),
    "gui.reisatsu.night_tonight": ("今夜", "Tonight"),
    "gui.reisatsu.night_tonight_observe": ("今夜：霊障を確かめる", "Tonight: witness the hauntings"),
    "gui.reisatsu.no_shots": ("まだ一枚も撮っていない", "No photos yet."),
    "gui.reisatsu.no_notes": ("まだ何も見つけていない", "Nothing found yet."),
    "gui.reisatsu.rules_title": ("撮影の心得", "On photographing them"),
    "gui.reisatsu.photo": ("写真", "Photograph"),
    "gui.reisatsu.photo_failed": ("……ぶれて、何も写っていない。気付かれていた", "...a smear. It had noticed you."),
    "gui.reisatsu.photo_empty": ("何も写っていない……はず", "Nothing in it... surely."),
    "gui.reisatsu.breaker": ("分電盤", "Breaker Panel"),
    "gui.reisatsu.scroll": ("%s / %s　（ホイールでめくる）", "%s / %s (scroll)"),
    "note.reisatsu.report.list": ("報告されている現象（☑＝自分で確かめたもの）", "Reported phenomena (☑ = seen for yourself)"),
    "speaker.reisatsu.medium": ("八雲", "Yakumo"),
    "speaker.reisatsu.pa": ("店内放送", "PA"),
    "speaker.reisatsu.phone": ("電話", "Phone"),
    "ending.reisatsu.title": ("霊撮", "REISATSU"),
    "ending.reisatsu.good.1": ("東の空が、白んでいく", "The eastern sky turns pale."),
    "ending.reisatsu.good.2": ("国道を、始発のトラックが通り過ぎていく", "The first trucks of the morning roll by on the highway."),
    "ending.reisatsu.good.3": ("ひだまりの看板の灯りが、静かに消えた", "The light of the HIDAMARI sign quietly goes out."),
    "ending.reisatsu.good.ofuda.0": ("――完璧な夜。一枚のお札も燃やさずに、六人を送った", "- A perfect night. Six sent on, and not one ofuda burnt."),
    "ending.reisatsu.good.ofuda.1": ("お札が一枚、灰になった。それでも、六人は帰っていった", "One ofuda turned to ash. Still, all six went home."),
    "ending.reisatsu.good.ofuda.2": ("お札が二枚、灰になった。……危ない夜だった", "Two ofuda turned to ash. ...It was a close night."),
    "ending.reisatsu.good.ofuda.3": ("最後のお札まで燃えた。朝を迎えられたのは、運が良かっただけかもしれない",
                                     "Every last ofuda burnt. Maybe you only saw the morning by luck."),
    "ending.reisatsu.good.5": ("その日から、誰もいないのに自動ドアのチャイムが鳴ることは、なくなった",
                               "From that day on, the entrance chime never rang for nobody again."),
    "ending.reisatsu.bad.1": ("……気が付くと、七番テーブルに座っていた", "...when you come to, you are sitting at table 7."),
    "ending.reisatsu.bad.2": ("「いらっしゃいませ。……お一人様ですね」", "\"Welcome. ...A table for one, isn't it.\""),
    "ending.reisatsu.bad.3": ("目の前に、氷の入った水が置かれる", "A glass of ice water is set down in front of you."),
    "ending.reisatsu.bad.4": ("その夜から、ひだまりの七番テーブルの呼び出しボタンは、二度鳴るようになった。",
                              "From that night on, the call button at table 7 of HIDAMARI rings twice."),
    "ending.reisatsu.end_good": ("送り　―完―", "SENT ON - THE END"),
    "ending.reisatsu.end_bad": ("相席　―完―", "SHARED TABLE - THE END"),
    "ending.reisatsu.continue": ("クリックで続ける", "Click to continue"),
    "credits.reisatsu.1": ("霊撮 ―REISATSU―", "REISATSU"),
    "credits.reisatsu.2": ("", ""),
    "credits.reisatsu.3": ("ファミリーレストラン ひだまり　国道店", "Family restaurant HIDAMARI, highway branch"),
    "credits.reisatsu.4": ("", ""),
    "credits.reisatsu.5": ("来店者　三浦 健司", "The visitor - Kenji Miura"),
    "credits.reisatsu.6": ("給仕　小野寺 千夏", "The waitress - Chinatsu Onodera"),
    "credits.reisatsu.7": ("料理長　権藤 剛", "The chef - Tsuyoshi Gondo"),
    "credits.reisatsu.8": ("かくれんぼの子　春日 ゆうか", "The hiding child - Yuka Kasuga"),
    "credits.reisatsu.9": ("鏡の女　白石 玲子", "The woman in the mirror - Reiko Shiraishi"),
    "credits.reisatsu.10": ("店長　森田 浩一", "The manager - Koichi Morita"),
    "credits.reisatsu.11": ("霊媒師　八雲 トキ", "The medium - Yakumo Toki"),
    "credits.reisatsu.12": ("", ""),
    "credits.reisatsu.13": ("この物語はフィクションです", "This story is fiction."),
    "credits.reisatsu.14": ("夜の国道では、どうか安全運転を", "On the highway at night, please drive safely."),
    "credits.reisatsu.15": ("", ""),
    "credits.reisatsu.16": ("ご来店、ありがとうございました", "Thank you for dining with us."),
}


def main():
    ja, en = {}, {}

    def put(key, pair):
        ja[key], en[key] = pair

    for k, v in COMMON.items():
        put(k, v)
    for k, v in ITEMS.items():
        put(f"item.{NS}.{k}", v)
    for k, v in ITEM_DESC.items():
        put(f"item.{NS}.{k}.desc", v)
    put(f"item.{NS}.reisha_camera.film", CAMERA_FILM)
    put(f"item.{NS}.flashlight.desc", ITEM_DESC["flashlight"])
    for k, v in ENTITIES.items():
        put(f"entity.{NS}.{k}", v)
    for k, (j, e) in TRUE_NAMES.items():
        put(f"spirit.{NS}.{k}.name", (j, e))
        put(f"speaker.{NS}.{k}", ("", ""))
    for k, v in AREAS.items():
        put(f"area.{NS}.{k}", v)
    for k, v in CIRCUITS.items():
        put(f"circuit.{NS}.{k}", v)
    for i, r in enumerate(RULES):
        put(f"rule.{NS}.{i + 1}", r)
    for k, v in PHENOMENA.items():
        put(f"phenomenon.{NS}.{k}", v)
    for k, ((tj, bj), (te, be)) in NOTES.items():
        put(f"note.{NS}.{k}.title", (tj, te))
        put(f"note.{NS}.{k}.body", (bj, be))
    for i, v in enumerate(INTRO):
        put(f"intro.{NS}.{i + 1}", v)
    for i, (title, date, short) in enumerate(NIGHTS):
        put(f"night.{NS}.{i + 1}.title", title)
        put(f"night.{NS}.{i + 1}.date", date)
        put(f"night.{NS}.{i + 1}.short", short)
    for k, v in WITNESS.items():
        put(f"witness.{NS}.{k}", v)
    for k, v in DIALOGUE.items():
        put(f"dialogue.{NS}.{k}", v)
    for k, v in THOUGHTS.items():
        put(f"thought.{NS}.{k}", v)
    for k, v in BLOCK_NAMES.items():
        put(f"block.{NS}.{k}", v)
    for lang, d in (("ja", ja), ("en", en)):
        sub = os.path.join(HERE, "audio", f"subtitles_{lang}.json")
        if os.path.exists(sub):
            with open(sub, encoding="utf-8") as f:
                d.update(json.load(f))
    # every registered block, item and entity needs a name (the world registers blocks too)
    src = os.path.join(HERE, "..", "src", "main", "java", "com", "reisatsu", "registry")
    unnamed = []
    for fname, prefix, pattern in (("ModBlocks.java", "block", r'=\s*(?:block|cube|litProp|prop)\(\s*"([a-z0-9_]+)"'),
                                   ("ModItems.java", "item", r'(?:ITEMS\.register|game)\(\s*"([a-z0-9_]+)"'),
                                   ("ModEntities.java", "entity", r'(?:ENTITIES\.register|register|type)\(\s*"([a-z0-9_]+)"')):
        path = os.path.join(src, fname)
        if not os.path.exists(path):
            continue
        with open(path, encoding="utf-8") as f:
            for rid in sorted(set(re.findall(pattern, f.read()))):
                if f"{prefix}.{NS}.{rid}" not in en:
                    unnamed.append(f"{prefix}.{NS}.{rid}")
    if unnamed:
        raise SystemExit("no name for: " + ", ".join(unnamed))
    os.makedirs(OUT, exist_ok=True)
    for lang, d in (("ja_jp", ja), ("en_us", en)):
        with open(os.path.join(OUT, f"{lang}.json"), "w", encoding="utf-8") as f:
            json.dump(dict(sorted(d.items())), f, ensure_ascii=False, indent=2)
    missing = sorted(set(ja) ^ set(en))
    print(f"ja {len(ja)} keys, en {len(en)} keys, mismatched: {missing[:10]}")


if __name__ == "__main__":
    main()
