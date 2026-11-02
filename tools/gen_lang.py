#!/usr/bin/env python3
"""Writes assets/meikon/lang/ja_jp.json and en_us.json (merging the sound subtitles from tools/audio/)."""
import json
import os

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.normpath(os.path.join(HERE, "..", "src", "main", "resources", "assets", "meikon", "lang"))

BLOCKS = {
    "tatami": ("畳", "Tatami"), "old_planks": ("古びた床板", "Old Floorboards"), "engawa": ("縁側板", "Engawa Boards"),
    "rotten_planks": ("朽ちた板", "Rotten Planks"), "dark_beam": ("黒い柱", "Dark Beam"), "plaster_wall": ("漆喰壁", "Plaster Wall"),
    "plaster_wall_stained": ("染みのある漆喰壁", "Stained Plaster Wall"), "wainscot": ("腰板", "Wainscot"),
    "kura_wall": ("海鼠壁", "Namako Wall"), "shoji": ("障子", "Shoji"), "shoji_torn": ("破れ障子", "Torn Shoji"),
    "shoji_bloody": ("血の手形の障子", "Bloody Shoji"), "fusuma": ("襖", "Fusuma"), "ranma": ("欄間", "Ranma Transom"),
    "vermilion": ("朱塗りの柱", "Vermilion Pillar"), "black_lacquer": ("黒漆の梁", "Black Lacquer Beam"),
    "fusuma_door": ("襖戸", "Fusuma Door"), "shoji_door": ("障子戸", "Shoji Door"), "sealed_door": ("灯明の間の戸", "Lamplight Room Door"),
    "oshiire_door": ("押入れの戸", "Closet Door"), "kura_door": ("蔵の扉", "Storehouse Door"), "torii_gate": ("縁切りの結界", "Bond-Severing Veil"),
    "shimenawa": ("注連縄", "Shimenawa"), "blood_stain": ("血痕", "Blood Stain"), "hair_strands": ("髪の毛", "Hair"),
    "footprints": ("濡れた足跡", "Wet Footprints"), "futon": ("布団", "Futon"), "kakejiku": ("掛け軸", "Hanging Scroll"),
    "letter": ("落ちている手紙", "Fallen Letter"), "butsudan": ("仏壇", "Butsudan"), "candle_stand": ("燭台", "Candle Stand"),
    "andon": ("行灯", "Andon Lamp"), "tansu": ("箪笥", "Tansu"), "kamado": ("竈", "Kamado Stove"), "well": ("井戸", "Well"),
    "suzu_dai": ("鈴台", "Bell Pedestal"), "salt_pile": ("盛り塩", "Salt Ward"), "hina_dan": ("雛壇", "Doll Steps"),
    "headless_doll": ("首のない人形", "Headless Doll"), "sakazuki_table": ("三三九度の膳", "Wedding Sake Tray"),
    "old_clock": ("柱時計", "Old Clock"), "stone_lantern": ("石灯籠", "Stone Lantern"),
}

ITEMS = {
    "chochin": ("提灯", "Paper Lantern", "右クリックで灯す／消す。持っている間だけ辺りを照らす――あの方からも、よく見える。",
                "Right-click to light or put out. Lights the way while held - and shows you to her."),
    "chochin_lit": ("提灯（灯）", "Paper Lantern (lit)", "右クリックで灯す／消す。持っている間だけ辺りを照らす――あの方からも、よく見える。",
                    "Right-click to light or put out. Lights the way while held - and shows you to her."),
    "lamp_oil": ("菜種油", "Rapeseed Oil", "右クリックで提灯に油を足す。", "Right-click to refill the lantern."),
    "suzu": ("鈴", "Bell", "嫁入り行列の、七つの鈴のひとつ。", "One of the seven bells of the wedding procession."),
    "shio": ("清め塩", "Purifying Salt", "右クリックで投げつけ、あの方を退ける。床に向けて使えば盛り塩になる。",
             "Throw it at her to drive her back, or use it on the floor to heap a ward."),
    "ofuda": ("護符", "Ofuda", "持っていれば、一度だけ花嫁の手から逃れられる。", "Saves you from the bride's hands, once."),
    "ohajiki": ("おはじき", "Glass Marbles", "投げると落ちた場所で音が鳴る。あの方を音で誘える。",
                "Throw one: wherever it clatters, she goes to look."),
    "kura_key": ("蔵の鍵", "Storehouse Key", "中庭の蔵の錠前の鍵。", "The key to the storehouse in the courtyard."),
    "doll_head": ("人形の首", "Doll's Head", "どこかの人形の首。黒い瞳がこちらを見ている。", "A doll's head. Its black eyes follow you."),
    "matches": ("燐寸", "Matches", "燭台や行灯に火を灯す。", "Lights candle stands and paper lamps."),
    "note": ("手記", "Note", "右クリックで読む。Jキーで手記の一覧。", "Right-click to read. J opens your notes."),
    "red_envelope": ("赤い封筒", "Red Envelope", "差出人の名はない。拾ってはいけなかった気がする。",
                     "No sender. You feel you should not have picked it up."),
    "omamori": ("お守り", "Omamori", "持っていると、恐怖が和らぐ。", "Carrying it eases your fear."),
    "ash": ("灰", "Ash", "赤い封筒だったもの。", "What was once a red envelope."),
}

JA = {
    "itemGroup.meikon": "冥婚 ―MEIKON―",
    "key.meikon.journal": "手記を開く",
    "key.categories.meikon": "冥婚",
    "entity.meikon.hanayome": "花嫁",
    "entity.meikon.ichimatsu": "市松人形",
    "entity.meikon.kageboshi": "影法師",
    "entity.meikon.maboroshi": "幻",
    "entity.meikon.ohajiki": "おはじき",
    "item.meikon.hanayome_spawn_egg": "花嫁のスポーンエッグ",
    "item.meikon.ichimatsu_spawn_egg": "市松人形のスポーンエッグ",
    "item.meikon.kageboshi_spawn_egg": "影法師のスポーンエッグ",
    "item.meikon.chochin.oil": "油: %s%%",
    "item.meikon.suzu.desc2": "七つ揃えて、鳥居の下で鳴らせ。",
    "painting.meikon.mukasari.title": "ムカサリ絵馬",
    "painting.meikon.mukasari.author": "作者不明",
    "painting.meikon.sayo.title": "小夜",
    "painting.meikon.sayo.author": "作者不明",
    "painting.meikon.family.title": "家族写真",
    "painting.meikon.family.author": "大正十二年",
    "painting.meikon.mansion.title": "屋敷",
    "painting.meikon.mansion.author": "作者不明",
    "candle.meikon.0": "金の燭台",
    "candle.meikon.1": "銀の燭台",
    "candle.meikon.2": "黒の燭台",

    "intro.meikon.1": "――赤い封筒を、開けてしまった。",
    "intro.meikon.2": "中には一房の黒髪と、婚礼の招待状。",
    "intro.meikon.3": "新郎の欄には、「%s」と書かれていた。",

    "title.meikon.phase.0": "招待", "title.meikon.phase.0.sub": "灯明の間",
    "title.meikon.phase.1": "鈴の音", "title.meikon.phase.1.sub": "七つの鈴を集めよ",
    "title.meikon.phase.2": "人形", "title.meikon.phase.2.sub": "人形から、目を離すな",
    "title.meikon.phase.3": "影", "title.meikon.phase.3.sub": "灯りが、消えていく",
    "title.meikon.phase.4": "祝言", "title.meikon.phase.4.sub": "婚礼の支度が、整った",
    "title.meikon.phase.5": "縁切り", "title.meikon.phase.5.sub": "鳥居の下で、七つの鈴を鳴らせ",

    "death.meikon.taken": "――連れて行かれた",
    "death.meikon.count": "（%s 度目）",
    "death.meikon.hint.0": "灯りは、あの方からもよく見える。",
    "death.meikon.hint.1": "押入れの中では、Shift長押しで息を止められる。",
    "death.meikon.hint.2": "走れば足音が響く。鈴を持っていれば、なおさらだ。",
    "death.meikon.hint.3": "清め塩は、あの方を退ける。護符は一度だけ命を救う。",
    "death.meikon.hint.4": "おはじきを投げれば、あの方を音で誘い出せる。",

    "hud.meikon.objective.prologue": "提灯を手に取り、部屋を出よ",
    "hud.meikon.objective.bells": "鈴 %s / 7",
    "hud.meikon.objective.final": "鳥居へ――七つの鈴を鳴らせ",
    "hud.meikon.holding": "息を止めている……",
    "hud.meikon.hold_hint": "Shift長押しで息を止める",
    "hud.meikon.ritual": "鈴を鳴らし続けろ……",
    "hud.meikon.ritual_ready": "鈴を持って右クリック長押しで、縁を切る",

    "prompt.meikon.search": "［右クリック］調べる",
    "prompt.meikon.hide": "［右クリック］押入れに入り、戸を閉める",
    "prompt.meikon.leave_closet": "［右クリック］戸を開ける",
    "prompt.meikon.open": "［右クリック］開ける",
    "prompt.meikon.close": "［右クリック］閉める",
    "prompt.meikon.kura": "［右クリック］蔵の扉",
    "prompt.meikon.read": "［右クリック］読む",
    "prompt.meikon.bell": "［右クリック］鈴を取る",
    "prompt.meikon.light": "［燐寸を持って右クリック］火を灯す",
    "prompt.meikon.well": "［右クリック］縄を引く",
    "prompt.meikon.doll": "［人形の首を持って右クリック］首を戻す",
    "prompt.meikon.sakazuki": "［右クリック］盃……",

    "message.meikon.locked": "鍵がかかっている。",
    "message.meikon.unlocked": "蔵の錠前が外れた。",
    "message.meikon.need_fire": "火が要る。燐寸があれば……",
    "message.meikon.no_oil": "油が切れている。",
    "message.meikon.oil_out": "提灯の火が消えた。油が尽きた。",
    "message.meikon.no_lantern": "提灯がない。",
    "message.meikon.oil_full": "油はもう十分に入っている。",
    "message.meikon.oil_refilled": "提灯に油を足した（%s%%）",
    "message.meikon.bell": "鈴を手に入れた（%s / 7）",
    "message.meikon.butsuma_solved": "仏壇の前に、鈴が戻ってきた。",
    "message.meikon.butsuma_fail": "炎が一斉に掻き消えた。誰かが、すぐそばで息を吐いた。",
    "message.meikon.doll_solved": "人形たちが一斉にこちらを向いた気がした。……鈴の音がする。",
    "message.meikon.doll_headless": "首のない人形だ。首がどこかにあるはずだ。",
    "message.meikon.well_1": "縄を引いた。……重い。",
    "message.meikon.well_2": "何かが、上がってくる。",
    "message.meikon.well_done": "桶の中に、鈴と――長い黒髪が、絡みついていた。",
    "message.meikon.well_empty": "井戸の底は、真っ暗だ。",
    "message.meikon.empty": "何もない。",
    "message.meikon.found": "%s を見つけた。",
    "message.meikon.found_scare": "……指先に、冷たい何かが触れた。",
    "message.meikon.found_hair": "引き出しの中に、長い髪がびっしりと詰まっていた。",
    "message.meikon.salt_hit": "花嫁が悲鳴を上げて退いた！",
    "message.meikon.ofuda": "護符が燃え尽きた。……まだ、生きている。",
    "message.meikon.ritual_broken": "鈴の音が途切れた……",
    "message.meikon.sakazuki_idle": "空の盃が三つ、並んでいる。",
    "message.meikon.envelope_found": "足元に、赤い封筒が落ちている……（右クリックで開く）",
    "message.meikon.no_dimension": "隠り世への道が見つからない。（データの読み込みに失敗しました）",

    "gui.meikon.scroll": "%s / %s　（ホイールでめくる）",
    "gui.meikon.journal": "手記",
    "gui.meikon.journal.empty": "まだ何も見つけていない。",
    "gui.meikon.journal.hint.0": "灯明の間の中は安全だ。準備をしてから出よう。",
    "gui.meikon.journal.hint.1": "鈴の音を辿れ。走れば足音で気づかれる。",
    "gui.meikon.journal.hint.2": "人形は見ている間は動かない。背を向けるな。",
    "gui.meikon.journal.hint.3": "影法師は提灯の灯りを向けると消える。",
    "gui.meikon.journal.hint.4": "婚礼の支度が整った。あの方はもう、あなたの居場所を知っている。",
    "gui.meikon.journal.hint.5": "中庭の鳥居へ。鈴を手に、右クリックを長押しし続けろ。",
    "gui.meikon.warning": "※この作品には、大きな音・暗闇・点滅表現・ジャンプスケアが含まれます。設定ファイル config/meikon-client.toml で軽減できます。やめたくなったら /meikon leave でいつでも元の世界に戻れます。",
    "gui.meikon.envelope.go": "封を切り、中へ入る",
    "gui.meikon.envelope.close": "閉じる",
    "gui.meikon.sakazuki": "三三九度の盃",
    "gui.meikon.sakazuki.text1": "なみなみと注がれた、赤い酒。",
    "gui.meikon.sakazuki.text2": "盃を交わせば、もう二度と戻れない。",
    "gui.meikon.sakazuki.drink": "盃を交わす",
    "gui.meikon.sakazuki.refuse": "やめる",

    "ending.meikon.good.title": "縁切り",
    "ending.meikon.good.1": "七つ目の鈴が鳴り終わったとき、",
    "ending.meikon.good.2": "鳥居の向こうから、白い光があふれた。",
    "ending.meikon.good.3": "背後で、誰かが泣いていた。",
    "ending.meikon.good.4": "振り返ってはいけないと、なぜか分かっていた。",
    "ending.meikon.good.5": "",
    "ending.meikon.good.6": "――気がつくと、見慣れた景色の中に立っていた。",
    "ending.meikon.good.7": "東の空が、白みはじめている。",
    "ending.meikon.good.8": "手の中の赤い封筒は、灰になって崩れた。",
    "ending.meikon.good.9": "",
    "ending.meikon.good.10": "これで、終わったのだ。",
    "ending.meikon.good.11": "そう思った。",
    "ending.meikon.good.12": "",
    "ending.meikon.good.13": "その夜、%s は夢を見た。",
    "ending.meikon.good.14": "白無垢の女が、鈴をひとつ、大切そうに握りしめている夢を。",
    "ending.meikon.fin": "― 完 ―",
    "ending.meikon.click": "クリックで戻る",
    "ending.meikon.bad.title": "祝言",
    "ending.meikon.bad.1": "盃の酒は、ひどく甘かった。",
    "ending.meikon.bad.2": "どこからか、雅楽の音が聞こえる。",
    "ending.meikon.bad.3": "隣に座った花嫁が、綿帽子の下で微笑んだ。",
    "ending.meikon.bad.4": "その顔を、%s ははじめて見た。",
    "ending.meikon.bad.5": "",
    "ending.meikon.bad.6": "――それきり、%s を見た者はいない。",
    "ending.meikon.bad.7": "屋敷の奥に飾られたムカサリ絵馬には、",
    "ending.meikon.bad.8": "花婿の顔が、新しく描き加えられていたという。",
    "ending.meikon.bad.9": "",
    "ending.meikon.bad.fin": "― 婚礼は、成った ―",

    "meikon.shaders.installed": "影MOD「MeikonShaders」をインストールして有効にしました。（Oculus / Iris）",
    "meikon.shaders.updated": "MeikonShaders を更新しました。",
    "meikon.shaders.optifine": "MeikonShaders を OptiFine に設定しました。次回の起動から有効になります。",
}

NOTES_JA = {
    1: ("招待状",
        "謹啓\n\nこのたび、小夜（さよ）と %1$s 殿の婚礼を執り行う運びと相成りました。\n"
        "つきましては新郎として、御来臨を賜りたく、ここに御案内申し上げます。\n\n"
        "　日時　今宵　丑三つ時\n　場所　隠り世　小夜の屋敷\n\n"
        "――封筒の中には、一房の長い黒髪が入っていた。\n髪は、まだ湿っている。"),
    2: ("女中の日記　一",
        "大正十二年　皐月\n\n"
        "お嬢様の御婚礼が決まった。お相手は町の呉服問屋の若旦那様。\n"
        "お嬢様は毎晩、白無垢を眺めては、嬉しそうに鈴を鳴らしておいでだ。\n"
        "嫁入り行列の七つの鈴。お嬢様のお気に入り。\n\n"
        "このところ、夜になると廊下の行灯がよく消える。"),
    3: ("灯りの心得",
        "提灯の火を、決して絶やすな。\n闇には影法師が棲む。灯りを持たぬ者に寄ってきて、火を吸う。\n"
        "影法師には、灯りを向けよ。光の中では形を保てぬ。\n\n"
        "だが、忘れるな。\n灯りは、あの方からもよく見える。遠くからでも、灯りを持つ者は見つかる。\n"
        "あの方が近いと感じたら、火を消し、身を低くせよ。\n\n"
        "走れば足音が響く。鈴を持っていれば、なおさらだ。"),
    4: ("女中の日記　二",
        "祝言の前の晩、若旦那様が姿を消した。\n"
        "お嬢様は一晩じゅう、白無垢のまま門の前で待っておられた。\n"
        "夜が明けても、お嬢様はお戻りにならなかった。\n\n"
        "中庭の井戸の底で見つかったとき、お嬢様は綿帽子をかぶったまま、\nにっこりと笑っておられたという。\n\n"
        "あの日から、夜になると廊下を歩く音がする。\n「かごめ　かごめ」と歌う声がする。"),
    5: ("母の手記",
        "あの子が一人で逝くのは、あまりに不憫だ。\n"
        "古い習いに倣い、ムカサリ絵馬を描かせた。冥土で祝言を挙げさせるための絵だ。\n\n"
        "花婿の顔は――私は絵師に、生きている男の顔を描かせた。\nあの子を捨てた男の顔を。\n\n"
        "生きた者を絵馬に描けば、その者は連れて行かれる。\n知っていた。知っていて、描かせた。\n\n"
        "けれど、あの男が消えても、あの子は満足しなかった。\n"
        "絵の花婿の顔は、いつの間にか削り取られていた。\nあの子は今も、新しい花婿を探している。"),
    6: ("住職の書付",
        "縁切りの儀について\n\n"
        "花嫁の嫁入り行列には、七つの鈴があった。鈴は、花嫁と花婿を結ぶ縁の印である。\n\n"
        "屋敷に散った七つの鈴をすべて集め、中庭の鳥居の下で鳴らし続ければ、\n"
        "結ばれた縁は断ち切られ、生者は現世へ戻れよう。\n\n"
        "ただし、鈴の音はあの方を呼ぶ。\n鳴らし終えるまで、決して手を止めてはならぬ。\n\n"
        "蔵の鍵、仏間の燭台、首のない人形、そして井戸。\n残りの鈴は、そこに眠っている。"),
    7: ("人形のこと",
        "雛壇の市松人形には、決して背を向けてはならない。\n"
        "見ているあいだ、人形は動かない。目を離せば、少しずつ近づいてくる。\n"
        "灯りのない場所では、見ているつもりでも、見えてはいない。\n\n"
        "一番上の段の人形は、首を失くしている。\n"
        "お嬢様が癇癪を起こして、もいでしまわれたのだ。\n首は、子供部屋のどこかにあるはずだ。"),
    8: ("押入れの中で",
        "あの方に見つかったら、走れ。\n"
        "姿が見えなくなったら、押入れに入って戸を閉めろ。\n"
        "戸の隙間から外は見える。外からは、見えない。\n\n"
        "ただし、入るところを見られたら、もう終わりだ。\n\n"
        "あの方が戸の前に立ったら――息を止めろ。（Shiftを押し続ける）\n怯えた息の音は、戸越しにも聞こえる。"),
    9: ("仏間の作法",
        "仏間の燭台には、順に火を灯すこと。\n順を違えれば、ご先祖様がお怒りになる。\n\n"
        "灯す順は――\n\n　%2$s\n\n"
        "正しく灯せば、仏壇の前に鈴がお戻りになる。\n燐寸は台所の箪笥にある。"),
    10: ("塩とおはじき",
         "清め塩は、あの方を退ける。\nすぐそこまで来たら、顔に投げつけろ。（右クリック）\n"
         "床に向けて使えば盛り塩になる。しばらくの間、あの方は近寄れない。\n\n"
         "おはじきを投げれば、落ちた場所で音が鳴る。\nあの方は、音のする方へ行く。\n\n"
         "護符を持っていれば、一度だけ、あの方の手から逃れられる。"),
    11: ("盃を交わすな",
         "婚礼の間の三三九度の盃に、決して口をつけてはならない。\n"
         "盃を交わせば、婚礼は成る。成ってしまえば、二度と現世には戻れない。\n\n"
         "……それでも、あの方は待っている。\n毎晩、盃を用意して。"),
    12: ("子供の絵",
         "（クレヨンで描かれた絵。白い着物の女の人と、小さな子供。）\n\n"
         "おねえちゃんは　まいばん　ろうかを　あるいてる\n"
         "おねえちゃんの　かおを　みちゃだめ\n"
         "みたら　おねえちゃんが　およめさんに　してくれる\n\n\n"
         "おねえちゃんは　いま　あなたの　うしろに　いるよ"),
}

EN = {
    "itemGroup.meikon": "MEIKON: The Ghost Wedding",
    "key.meikon.journal": "Open notes",
    "key.categories.meikon": "MEIKON",
    "entity.meikon.hanayome": "Bride",
    "entity.meikon.ichimatsu": "Ichimatsu Doll",
    "entity.meikon.kageboshi": "Shadow",
    "entity.meikon.maboroshi": "Apparition",
    "entity.meikon.ohajiki": "Marble",
    "item.meikon.hanayome_spawn_egg": "Bride Spawn Egg",
    "item.meikon.ichimatsu_spawn_egg": "Ichimatsu Doll Spawn Egg",
    "item.meikon.kageboshi_spawn_egg": "Shadow Spawn Egg",
    "item.meikon.chochin.oil": "Oil: %s%%",
    "item.meikon.suzu.desc2": "Gather all seven and ring them under the torii.",
    "painting.meikon.mukasari.title": "Mukasari Ema", "painting.meikon.mukasari.author": "Unknown",
    "painting.meikon.sayo.title": "Sayo", "painting.meikon.sayo.author": "Unknown",
    "painting.meikon.family.title": "Family Photograph", "painting.meikon.family.author": "1923",
    "painting.meikon.mansion.title": "The Mansion", "painting.meikon.mansion.author": "Unknown",
    "candle.meikon.0": "the gold candle", "candle.meikon.1": "the silver candle", "candle.meikon.2": "the black candle",
    "intro.meikon.1": "- I opened the red envelope.",
    "intro.meikon.2": "Inside: a lock of black hair, and a wedding invitation.",
    "intro.meikon.3": "The groom's name was written as \"%s\".",
    "title.meikon.phase.0": "The Invitation", "title.meikon.phase.0.sub": "The Room of Lamplight",
    "title.meikon.phase.1": "The Bells", "title.meikon.phase.1.sub": "Gather the seven bells",
    "title.meikon.phase.2": "The Dolls", "title.meikon.phase.2.sub": "Do not take your eyes off them",
    "title.meikon.phase.3": "The Shadows", "title.meikon.phase.3.sub": "The lights are dying",
    "title.meikon.phase.4": "The Wedding", "title.meikon.phase.4.sub": "Everything is ready",
    "title.meikon.phase.5": "Severance", "title.meikon.phase.5.sub": "Ring the seven bells under the torii",
    "death.meikon.taken": "- You were taken",
    "death.meikon.count": "(time %s)",
    "death.meikon.hint.0": "She sees your lantern from far away.",
    "death.meikon.hint.1": "Inside a closet, hold Shift to hold your breath.",
    "death.meikon.hint.2": "Running is loud. Running with bells is louder.",
    "death.meikon.hint.3": "Salt drives her back. An ofuda saves you once.",
    "death.meikon.hint.4": "Throw a marble to lure her with sound.",
    "hud.meikon.objective.prologue": "Take the lantern and leave the room",
    "hud.meikon.objective.bells": "Bells %s / 7",
    "hud.meikon.objective.final": "To the torii - ring the seven bells",
    "hud.meikon.holding": "Holding your breath...",
    "hud.meikon.hold_hint": "Hold Shift to hold your breath",
    "hud.meikon.ritual": "Keep ringing...",
    "hud.meikon.ritual_ready": "Hold right-click with a bell to sever the bond",
    "prompt.meikon.search": "[Right-click] Search",
    "prompt.meikon.hide": "[Right-click] Hide inside and close the door",
    "prompt.meikon.leave_closet": "[Right-click] Open the door",
    "prompt.meikon.open": "[Right-click] Open",
    "prompt.meikon.close": "[Right-click] Close",
    "prompt.meikon.kura": "[Right-click] Storehouse door",
    "prompt.meikon.read": "[Right-click] Read",
    "prompt.meikon.bell": "[Right-click] Take the bell",
    "prompt.meikon.light": "[Right-click with matches] Light",
    "prompt.meikon.well": "[Right-click] Pull the rope",
    "prompt.meikon.doll": "[Right-click with the head] Give her back her head",
    "prompt.meikon.sakazuki": "[Right-click] The sake cups...",
    "message.meikon.locked": "It is locked.",
    "message.meikon.unlocked": "The storehouse lock falls open.",
    "message.meikon.need_fire": "You need fire. Matches, perhaps...",
    "message.meikon.no_oil": "The lantern has no oil.",
    "message.meikon.oil_out": "The lantern went out. The oil is gone.",
    "message.meikon.no_lantern": "You have no lantern.",
    "message.meikon.oil_full": "The lantern is full.",
    "message.meikon.oil_refilled": "You refill the lantern (%s%%)",
    "message.meikon.bell": "You took a bell (%s / 7)",
    "message.meikon.butsuma_solved": "A bell has returned before the altar.",
    "message.meikon.butsuma_fail": "Every flame dies at once. Someone exhales right beside you.",
    "message.meikon.doll_solved": "The dolls seem to turn toward you... a bell rings.",
    "message.meikon.doll_headless": "A headless doll. Its head must be somewhere.",
    "message.meikon.well_1": "You pull the rope. ...It is heavy.",
    "message.meikon.well_2": "Something is coming up.",
    "message.meikon.well_done": "In the bucket: a bell - tangled in long black hair.",
    "message.meikon.well_empty": "The bottom of the well is pitch black.",
    "message.meikon.empty": "Nothing.",
    "message.meikon.found": "You found: %s",
    "message.meikon.found_scare": "...Something cold touched your fingers.",
    "message.meikon.found_hair": "The drawer is packed full of long hair.",
    "message.meikon.salt_hit": "The bride shrieks and recoils!",
    "message.meikon.ofuda": "The ofuda burned away. ...You are still alive.",
    "message.meikon.ritual_broken": "The ringing stopped...",
    "message.meikon.sakazuki_idle": "Three empty sake cups.",
    "message.meikon.envelope_found": "A red envelope lies at your feet... (right-click to open)",
    "message.meikon.no_dimension": "There is no way into the hidden world (data failed to load).",
    "gui.meikon.scroll": "%s / %s (scroll)",
    "gui.meikon.journal": "Notes",
    "gui.meikon.journal.empty": "You have found nothing yet.",
    "gui.meikon.journal.hint.0": "The room of lamplight is safe. Prepare, then go.",
    "gui.meikon.journal.hint.1": "Follow the ringing. Running makes noise.",
    "gui.meikon.journal.hint.2": "The dolls do not move while watched. Never turn your back.",
    "gui.meikon.journal.hint.3": "Shine your lantern at a shadow and it tears apart.",
    "gui.meikon.journal.hint.4": "The wedding is ready. She knows where you are.",
    "gui.meikon.journal.hint.5": "Go to the torii. Hold right-click with a bell and do not let go.",
    "gui.meikon.warning": "This game contains loud sounds, darkness, flashing effects and jump scares. You can tone them down in config/meikon-client.toml. Use /meikon leave to go home at any time.",
    "gui.meikon.envelope.go": "Break the seal and go in",
    "gui.meikon.envelope.close": "Close",
    "gui.meikon.sakazuki": "The Three Sake Cups",
    "gui.meikon.sakazuki.text1": "Red sake, filled to the brim.",
    "gui.meikon.sakazuki.text2": "Drink with her, and you can never return.",
    "gui.meikon.sakazuki.drink": "Drink",
    "gui.meikon.sakazuki.refuse": "Refuse",
    "ending.meikon.good.title": "Severance",
    "ending.meikon.good.1": "When the seventh bell fell silent,",
    "ending.meikon.good.2": "white light poured through the torii.",
    "ending.meikon.good.3": "Behind you, someone was crying.",
    "ending.meikon.good.4": "Somehow you knew you must not look back.",
    "ending.meikon.good.5": "",
    "ending.meikon.good.6": "- When you came to, you stood somewhere familiar.",
    "ending.meikon.good.7": "The eastern sky was growing pale.",
    "ending.meikon.good.8": "The red envelope crumbled to ash in your hand.",
    "ending.meikon.good.9": "",
    "ending.meikon.good.10": "It was over.",
    "ending.meikon.good.11": "Or so you thought.",
    "ending.meikon.good.12": "",
    "ending.meikon.good.13": "That night, %s dreamed",
    "ending.meikon.good.14": "of a woman in white, holding a single bell close to her heart.",
    "ending.meikon.fin": "- The End -",
    "ending.meikon.click": "Click to return",
    "ending.meikon.bad.title": "The Wedding",
    "ending.meikon.bad.1": "The sake was terribly sweet.",
    "ending.meikon.bad.2": "Somewhere, wedding music began to play.",
    "ending.meikon.bad.3": "The bride beside you smiled under her hood.",
    "ending.meikon.bad.4": "For the first time, %s saw her face.",
    "ending.meikon.bad.5": "",
    "ending.meikon.bad.6": "- No one ever saw %s again.",
    "ending.meikon.bad.7": "They say the mukasari painting in the mansion",
    "ending.meikon.bad.8": "now has a groom's face, freshly painted.",
    "ending.meikon.bad.9": "",
    "ending.meikon.bad.fin": "- The wedding is complete -",
    "meikon.shaders.installed": "Installed and enabled the MeikonShaders shader pack (Oculus / Iris).",
    "meikon.shaders.updated": "MeikonShaders was updated.",
    "meikon.shaders.optifine": "MeikonShaders was set for OptiFine; it will be active from the next launch.",
}

NOTES_EN = {
    1: ("Invitation", "We respectfully announce the wedding of Sayo and %1$s.\nWe humbly request your presence as the groom.\n\n"
                      "  When: tonight, at the hour of the ox\n  Where: the hidden world, Sayo's mansion\n\n"
                      "- Inside the envelope is a lock of long black hair.\nIt is still damp."),
    2: ("Maid's Diary I", "May, 1923\n\nThe young mistress is to be married, to the son of a kimono merchant in town.\n"
                          "Every night she gazes at her white wedding kimono and rings her bells.\n"
                          "The seven bells of the wedding procession. Her favourites.\n\nLately the lamps in the corridor keep going out at night."),
    3: ("On Light", "Never let your lantern die.\nShadows live in the dark. They come to those without light, and drink their fire.\n"
                    "Turn your light on a shadow; it cannot keep its shape in the light.\n\n"
                    "But remember:\nShe sees the light too. Even from far away, the one who carries it is found.\n"
                    "When you feel her near, put out the flame and keep low.\n\nRunning is loud. Running with bells is louder."),
    4: ("Maid's Diary II", "The night before the wedding, the groom disappeared.\n"
                           "The mistress waited at the gate all night in her white kimono.\nWhen dawn came, she did not return.\n\n"
                           "They found her at the bottom of the well in the courtyard, still in her hood,\nsmiling.\n\n"
                           "Since that day, at night, there are footsteps in the corridor,\nand a voice singing \"kagome, kagome\"."),
    5: ("Mother's Note", "It was too cruel to let her go alone.\nFollowing the old custom, I had a mukasari ema painted: a wedding in the afterlife.\n\n"
                         "For the groom's face I had the painter paint a living man.\nThe man who abandoned her.\n\n"
                         "Paint the living on the ema, and they are taken.\nI knew. I knew, and I had it painted.\n\n"
                         "But when he vanished she was not satisfied.\nThe groom's face was scraped away from the painting.\n"
                         "She is still looking for a new groom."),
    6: ("The Priest's Note", "On severing the bond\n\nThe bride's procession carried seven bells, the tokens that bind bride and groom.\n\n"
                             "Gather all seven bells scattered through the mansion and keep ringing them under the torii in the courtyard,\n"
                             "and the bond will be cut; the living may return to their world.\n\n"
                             "But the ringing calls her.\nDo not stop until it is done.\n\n"
                             "The storehouse key, the altar candles, the headless doll, and the well.\nThe last bells sleep there."),
    7: ("About the Dolls", "Never turn your back on the Ichimatsu dolls.\nWhile you watch them they do not move. Look away, and they come closer.\n"
                           "Where there is no light, you are not really seeing them.\n\n"
                           "The doll on the top step has lost her head.\nThe mistress tore it off in a tantrum.\nIt must be somewhere in the child's room."),
    8: ("In the Closet", "If she finds you, run.\nWhen she loses sight of you, get into a closet and close the door.\n"
                         "You can see out through the slats. She cannot see in.\n\nBut if she sees you go in, it is over.\n\n"
                         "If she stands before the door - hold your breath. (keep holding Shift)\nFrightened breathing carries through the door."),
    9: ("Rites of the Altar Room", "Light the altar candles in order.\nThe wrong order angers the ancestors.\n\nThe order is:\n\n  %2$s\n\n"
                                   "Light them rightly and a bell returns before the altar.\nThere are matches in the kitchen drawers."),
    10: ("Salt and Marbles", "Purifying salt drives her away.\nWhen she is upon you, throw it in her face. (right-click)\n"
                             "Use it on the floor to heap a ward she will not cross for a while.\n\n"
                             "A thrown marble clatters where it lands.\nShe goes toward the sound.\n\nAn ofuda frees you from her hands, once."),
    11: ("Do Not Drink", "Never touch the three sake cups in the wedding hall.\nDrink with her and the wedding is done, and you will never go back.\n\n"
                         "...Still, she waits.\nEvery night, with the cups ready."),
    12: ("A Child's Drawing", "(A crayon drawing: a woman in a white kimono, and a small child.)\n\n"
                              "big sister walks the hallway every night\ndont look at big sisters face\nif you look she will make you her husband\n\n\n"
                              "big sister is behind you right now"),
}


def main():
    os.makedirs(OUT, exist_ok=True)
    ja, en = dict(JA), dict(EN)
    for k, (j, e) in BLOCKS.items():
        ja[f"block.meikon.{k}"] = j
        en[f"block.meikon.{k}"] = e
    for k, (j, e, dj, de) in ITEMS.items():
        ja[f"item.meikon.{k}"] = j
        en[f"item.meikon.{k}"] = e
        ja[f"item.meikon.{k}.desc"] = dj
        en[f"item.meikon.{k}.desc"] = de
    for i, (t, b) in NOTES_JA.items():
        ja[f"note.meikon.{i}.title"] = t
        ja[f"note.meikon.{i}.body"] = b
    for i, (t, b) in NOTES_EN.items():
        en[f"note.meikon.{i}.title"] = t
        en[f"note.meikon.{i}.body"] = b
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
