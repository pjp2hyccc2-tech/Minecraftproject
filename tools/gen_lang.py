"""Merges the story and the story-mode UI strings into the language files (ja_jp is primary, en_us mirrors it).

Run from the repository root:  python3 tools/gen_lang.py
"""
import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import story_text as st  # noqa: E402

LANG = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources", "assets", "clockworkdiver", "lang")
NS = "clockworkdiver"

UI = {
    # clocks
    "clock.clockworkdiver.music_box": ("オルゴール時計", "Music Box Clock"),
    "clock.clockworkdiver.music_box.desc": ("踊り子の人形が回るオルゴール仕掛けの時計。櫛歯が奏でる旋律に、持ち主の思い出が宿る。",
                                            "A music box clock with a twirling dancer. Its owner's memories live in the melody of its comb."),
    "clock.clockworkdiver.grandfather": ("柱時計", "Grandfather Clock"),
    "clock.clockworkdiver.grandfather.desc": ("背の高い木の箱に長い振り子を収めた柱時計。家族の時を何代にもわたって刻んできた。",
                                              "A tall wooden case housing a long pendulum. It has kept a family's time for generations."),
    "clock.clockworkdiver.great_clock": ("ギアヘイヴン大時計", "The Great Clock of Gearhaven"),
    "clock.clockworkdiver.great_clock.desc": ("街の中心にそびえる大時計塔の時計。街じゅうの時計は、刻脈を通じてこの時計に繋がっている。",
                                              "The clock of the great tower at the heart of town. Every clock in town is bound to it by the time veins."),
    # defects and parts
    "defect.clockworkdiver.dust": ("埃詰まり", "Clogged with Dust"),
    "defect.clockworkdiver.dust.desc": ("時喰いの巣材の埃が歯車に詰まっている。中に潜り、レンチを右クリックの『蒸気爆発』で埃の山を吹き飛ばそう。埃蛾が埃を運び戻してくる。",
                                        "Time Eater nesting dust clogs the gears. Dive in and blow the dust heaps away with Steam Bursts (right click). Dust moths carry dust back."),
    "defect.clockworkdiver.tuning": ("調律狂い", "Out of Tune"),
    "defect.clockworkdiver.tuning.desc": ("鐘打ちの櫛が狂っている。四つの調律鐘が奏でる旋律を覚え、同じ順に、拍に合わせて右クリックで打ち返そう。",
                                          "The chime comb is out of tune. Memorise the melody of the four tuning bells, then strike them back in the same order, on the beat (right click)."),
    "defect.clockworkdiver.clue": ("手掛かり", "Clue"),
    "defect.clockworkdiver.clue.desc": ("時計の中に、何か奇妙なものが見える。", "Something strange is visible inside the clock."),
    "part.clockworkdiver.chime_comb": ("調律櫛", "Chime Comb"),
    "part.clockworkdiver.chime_hammer": ("鐘打ち槌", "Chime Hammer"),
    "part.clockworkdiver.clue": ("手掛かり", "Clue"),
    # dive twists
    "mod.clockworkdiver.timed": ("時間制限", "Time Limit"),
    "mod.clockworkdiver.timed.time": ("時間制限 %s:%s", "Time limit %s:%s"),
    "mod.clockworkdiver.fast": ("早鐘（拍が速い）", "Racing Beat"),
    "mod.clockworkdiver.offbeat": ("裏拍", "Off-beat"),
    "mod.clockworkdiver.dark": ("暗闇", "Darkness"),
    "mod.clockworkdiver.swarm": ("大群", "Swarm"),
    "mod.clockworkdiver.elite": ("強化個体", "Elite Vermin"),
    "mod.clockworkdiver.novents": ("蒸気口なし", "No Steam Vents"),
    "mod.clockworkdiver.precision": ("精密作業", "Precision Work"),
    "gui.clockworkdiver.twist.boss.1": ("ボス：黒殻の時喰い", "Boss: Black-Shelled Time Eater"),
    "gui.clockworkdiver.twist.boss.2": ("ボス：女王の影", "Boss: The Queen's Shade"),
    "gui.clockworkdiver.twist.boss.3": ("ボス：時喰いの女王", "Boss: Queen of Time Eaters"),
    "gui.clockworkdiver.twist.fragments": ("記憶の欠片 ×%s", "Memory fragments x%s"),
    "gui.clockworkdiver.found_clue": ("手掛かり発見！ %s", "Clue found! %s"),
    # objectives
    "objective.clockworkdiver.dive.dust": ("蒸気爆発（右クリック）で埃の山を吹き飛ばせ (%s/%s)", "Blow the dust heaps away with Steam Bursts (%s/%s)"),
    "objective.clockworkdiver.dive.tuning": ("鐘の旋律を覚え、拍に合わせて同じ順に打て (%s/%s)", "Learn the bells' melody and strike it back on the beat (%s/%s)"),
    "objective.clockworkdiver.dive.clue": ("手掛かりを調べよう", "Examine the clue"),
    "objective.clockworkdiver.dive.fragments_left": ("修理完了！ 残りの記憶の欠片を集めよう（Rで後にする）", "Repaired! Gather the remaining memory fragments (R to leave them)"),
    "objective.clockworkdiver.fragments": ("記憶の欠片 %s/%s", "Memory fragments %s/%s"),
    "objective.clockworkdiver.find_tower": ("大時計塔の扉の前で眼鏡のレンズを動かし、不良部品を探そう (%s/%s)",
                                            "At the tower gate, move the spectacle lens to find the faulty parts (%s/%s)"),
    "objective.clockworkdiver.talk_tower": ("時計塔前の広場で、依頼人に話しかけよう", "Talk to your client in the clock tower plaza"),
    "objective.clockworkdiver.deliver_tower": ("広場で待つ依頼人に報告しよう", "Report to your client waiting in the plaza"),
    "objective.clockworkdiver.return": ("工房へ戻ろう（工房の鍵）", "Return to the workshop (Workshop Key)"),
    "hud.clockworkdiver.story_line": ("第%s話「%s」", "Story %s: %s"),
    "hud.clockworkdiver.memory": ("記憶の欠片 (%s/%s)", "Memory fragment (%s/%s)"),
    "hud.clockworkdiver.offbeat": ("裏拍", "OFF-BEAT"),
    "hud.clockworkdiver.quest_no": ("第%s話", "Story %s"),
    "hud.clockworkdiver.quest_title": ("「%s」", "%s"),
    "hud.clockworkdiver.quest_client": ("依頼人：%s", "Client: %s"),
    "hud.clockworkdiver.chapter_no": ("第%s章", "Chapter %s"),
    "hud.clockworkdiver.chapter_end": ("第%s章　完", "End of Chapter %s"),
    "hud.clockworkdiver.chapter_kanji.1": ("一", "I"),
    "hud.clockworkdiver.chapter_kanji.2": ("二", "II"),
    "hud.clockworkdiver.chapter_kanji.3": ("三", "III"),
    "hud.clockworkdiver.chapter_kanji.4": ("四", "IV"),
    "hud.clockworkdiver.chapter_kanji.5": ("五", "V"),
    # titles
    "title.clockworkdiver.boss.sub": ("ジャスト・ティックで殻を砕け！", "Crack its shell with Just Ticks!"),
    "title.clockworkdiver.boss.shadow": ("黒殻の時喰い 出現！", "The Black-Shelled Time Eater!"),
    "title.clockworkdiver.boss.shadow.sub": ("闇に紛れて忍び寄る。足を止めるな！", "It slips through the dark. Keep moving!"),
    "title.clockworkdiver.boss.shade": ("女王の影 出現！", "The Queen's Shade!"),
    "title.clockworkdiver.boss.shade.sub": ("女王の力の欠片が、娘の記憶を喰らっている", "A shard of the Queen is devouring a girl's memories"),
    "title.clockworkdiver.boss.queen": ("時喰いの女王", "The Queen of Time Eaters"),
    "title.clockworkdiver.boss.queen.sub": ("大時計の時を、取り戻せ！", "Take back the Great Clock's time!"),
    "title.clockworkdiver.queen.2": ("女王が拍を早めた！", "The Queen races the beat!"),
    "title.clockworkdiver.queen.2.sub": ("速い拍に食らいつけ", "Keep up with the racing beat"),
    "title.clockworkdiver.queen.3": ("拍が裏返った！", "The beat turns inside out!"),
    "title.clockworkdiver.queen.3.sub": ("裏拍を狙え ― 振り子を信じろ", "Strike the off-beat. Trust the pendulum"),
    "entity.clockworkdiver.time_eater.shadow": ("黒殻の時喰い", "Black-Shelled Time Eater"),
    "entity.clockworkdiver.time_eater.shade": ("女王の影", "The Queen's Shade"),
    "entity.clockworkdiver.time_eater.queen": ("時喰いの女王", "Queen of Time Eaters"),
    # messages
    "message.clockworkdiver.time_up": ("時間切れ……！ 時計から弾き出された", "Time's up...! You were thrown out of the clock"),
    "message.clockworkdiver.time_left": ("残り %s 秒！", "%s seconds left!"),
    "message.clockworkdiver.precision_screw": ("精密作業失敗！ ネジがひと回り緩んでしまった", "Precision slip! The screw backed out a turn"),
    "message.clockworkdiver.precision_spring": ("精密作業失敗！ ゼンマイが緩んだ", "Precision slip! The spring slackened"),
    "message.clockworkdiver.precision_tune": ("精密作業失敗！ 揃えた旋律がひとつ崩れた", "Precision slip! One finished melody fell apart"),
    "message.clockworkdiver.dust_back": ("埃蛾が埃を運んできた！", "The dust moths carried dust back!"),
    "message.clockworkdiver.dust_cleared": ("埃を吹き飛ばした！ (%s)", "Dust blown away! (%s)"),
    "message.clockworkdiver.dust_soft": ("埃が舞っただけだ。蒸気爆発（右クリック）で吹き飛ばそう", "It only stirs the dust. Blow it away with a Steam Burst (right click)"),
    "message.clockworkdiver.tune_listen": ("鐘の旋律を聴け……", "Listen to the bells' melody..."),
    "message.clockworkdiver.tune_play": ("同じ順に、拍に合わせて鐘を打て！（%s音）", "Strike the same %s notes, on the beat!"),
    "message.clockworkdiver.tune_slow": ("旋律が途切れた……もう一度聴こう", "The melody faded... listen again"),
    "message.clockworkdiver.tune_wrong": ("音が違う！ もう一度聴こう", "Wrong note! Listen again"),
    "message.clockworkdiver.tune_offbeat": ("拍が外れた！ もう一度聴こう", "Off the beat! Listen again"),
    "message.clockworkdiver.tune_wait": ("まずは旋律を聴こう", "Listen to the melody first"),
    "message.clockworkdiver.tune_done": ("旋律が揃った！ (%s/%s)", "Melody restored! (%s/%s)"),
    "message.clockworkdiver.fragments_left": ("修理完了！ 記憶の欠片があと %s つ残っている", "Repaired! %s memory fragments remain"),
    "message.clockworkdiver.story_upgrade": ("【贈り物】%s が Lv.%s になった！", "[Gift] %s is now Lv.%s!"),
    "message.clockworkdiver.gate_locked": ("大時計塔の扉は固く閉ざされている", "The tower gate is firmly shut"),
    "message.clockworkdiver.gate_wait": ("扉の奥で大時計が軋んでいる……ピップの合図を待とう", "The Great Clock creaks beyond the gate... wait for Pip's signal"),
    "message.clockworkdiver.gate_peace": ("大時計は正しく時を刻んでいる", "The Great Clock keeps true time"),
    "message.clockworkdiver.customer_at_tower": ("%s が大時計塔の前の広場で待っている", "%s is waiting in the plaza before the clock tower"),
    # hints
    "hint.clockworkdiver.after.0": ("チュートリアル完了！ ここから物語が始まる。街の人々が、謎を秘めた時計を持ってやって来る。",
                                    "Tutorial complete! Now the story begins: townsfolk will bring clocks with secrets inside."),
    "hint.clockworkdiver.after.1": ("Jキーで『修理日誌』を開くと、物語の進み具合や受け取った思い出の品を確認できる。",
                                    "Press J to open the Repair Journal: story progress and the keepsakes you received."),
    "hint.clockworkdiver.after.2": ("報酬の真鍮貨は、修理台の「改良」タブで工具の強化に使える。物語が進むほど修理は難しくなる。",
                                    "Spend brass coins on tool upgrades in the bench's Upgrade tab. Repairs get harder as the story goes on."),
    "hint.clockworkdiver.story.0": ("物語の依頼人がやって来た！ 依頼ごとに、時計の中で思いがけない仕掛けが待っている。",
                                    "A story client has arrived! Every commission hides a surprising twist inside the clock."),
    "hint.clockworkdiver.story.1": ("修理台で部品を選ぶと、その部品の『⚠ 仕掛け』（時間制限・暗闇など）を事前に確認できる。",
                                    "Select a part at the bench to see its twists (time limit, darkness...) before you dive."),
    "hint.clockworkdiver.dust.0": ("埃の山はレンチで叩いても崩れない。右クリックの『蒸気爆発』で周りの埃をまとめて吹き飛ばそう。",
                                   "Dust heaps won't break under the wrench. Use a Steam Burst (right click) to blow away all the dust around you."),
    "hint.clockworkdiver.dust.1": ("蒸気が足りなければ蒸気口の上へ。埃蛾を放っておくと、埃を運び戻してくる。",
                                   "Out of steam? Stand on a vent. Leave the dust moths alone and they will bring the dust back."),
    "hint.clockworkdiver.tuning.0": ("鐘が順に光って旋律を奏でる。よく見て、よく聴いて覚えよう。", "The bells light up one by one to play a melody. Watch and listen closely."),
    "hint.clockworkdiver.tuning.1": ("合図が鳴ったら、同じ順に鐘をレンチで右クリック。拍を外すか音を間違えると、最初から聴き直しだ。",
                                     "At the chime, right-click the bells with the wrench in the same order. Miss the beat or a note and the melody plays again."),
    "hint.clockworkdiver.timed.0": ("この修理には時間制限がある！ 画面上の時計が0になると時計から弾き出される。",
                                    "This repair is timed! When the clock at the top of the screen reaches zero, you are thrown out."),
    "hint.clockworkdiver.offbeat.0": ("裏拍の時計だ！ チク・タクの音の「間」が叩きどき。振り子は裏拍に合わせてあるので、目を信じよう。",
                                      "An off-beat clock! Strike between the tick and the tock. The pendulum follows the off-beat, so trust your eyes."),
    "hint.clockworkdiver.dark.0": ("灯りの消えた時計だ。暗闇でも、時視の眼鏡は敵の輪郭を映し出す。", "This clock's lamps are out. Even in the dark, the spectacles outline your foes."),
    "hint.clockworkdiver.precision.0": ("精密作業：拍を外すと、修理が一段階戻ってしまう。焦らず、拍の中心を狙おう。",
                                        "Precision work: missing the beat undoes a step of the repair. Take your time and aim for the heart of the beat."),
    "hint.clockworkdiver.fragment.0": ("光る『記憶の欠片』が落ちている。触れると、持ち主の思い出が蘇る。全部集めるまで修理は終わらない。",
                                       "Glowing memory fragments lie around. Touch one to bring back its owner's memory. The repair ends once you have them all."),
    "hint.clockworkdiver.clue.0": ("紫に光る『手掛かり』を見つけた！ 選ぶと内容を読める。手掛かりには潜る必要はない。",
                                   "You found a glowing purple clue! Select it to read it. Clues don't need a dive."),
    "hint.clockworkdiver.tower.0": ("工房の向かいのアーチを抜けると、大時計塔の前の広場に出る。", "Go through the archway across the street to reach the clock tower plaza."),
    "hint.clockworkdiver.tower.1": ("大時計の修理台は塔の扉の前だ。扉を右クリックするか「修理台」ボタンで眼鏡をかけよう。",
                                    "The Great Clock is inspected at the tower gate. Right-click the gate or press the Repair Bench button."),
    "hint.clockworkdiver.postgame.0": ("物語はここで一区切り。これからも依頼人たちが、不思議な時計を持ってやって来る。",
                                       "The story ends here, for now. Clients will keep bringing mysterious clocks to your workshop."),
    "hint.clockworkdiver.postgame.1": ("修理日誌（Jキー）からエンディングをもう一度見られる。", "You can watch the ending again from the Repair Journal (J)."),
    # dialogue
    "dialogue.clockworkdiver.offer.story": ("（依頼：%1$s ― 不良部品 %2$s か所 ― 報酬 真鍮貨 %3$s 枚）",
                                            "(Commission: %1$s - %2$s faulty parts - reward %3$s brass coins)"),
    "dialogue.clockworkdiver.keepsake": ("（思い出の品『%s』を受け取った）", "(You received the keepsake \"%s\")"),
    "dialogue.clockworkdiver.chatter": ("……", "..."),
    "dialogue.clockworkdiver.pip.offer.0": ("時計師さん！ ピップです。今日はお客として来ました！", "Clockmaker! It's Pip. Today I'm here as a customer!"),
    "dialogue.clockworkdiver.pip.offer.1": ("塔の番小屋の時計が、またご機嫌ななめで……。見てもらえますか？", "The clock in the tower lodge is cranky again... Could you take a look?"),
    "dialogue.clockworkdiver.pip.wait": ("ぼく、時計師さんの仕事を見てるだけで楽しいです！", "Just watching you work is fun for me!"),
    "dialogue.clockworkdiver.pip.deliver": ("その音……もう直ったんですか!?", "That sound... is it fixed already?!"),
    "dialogue.clockworkdiver.pip.thanks.0": ("ありがとうございます！ やっぱり時計師さんはすごいや！", "Thank you! You really are amazing!"),
    "dialogue.clockworkdiver.pip.thanks.1": ("いつか、ぼくも時計の中に潜ってみたいな……！", "Someday I want to dive into a clock too...!"),
    "speaker.clockworkdiver.me": ("あなた", "You"),
    "speaker.clockworkdiver.note": ("手記", "Note"),
    # customers
    "customer.clockworkdiver.pip": ("鐘つきピップ", "Pip the Bell-Ringer"),
    "customer.clockworkdiver.stranger": ("灰色の紳士", "The Grey Gentleman"),
    "customer.clockworkdiver.viktor": ("時計師ヴィクトル", "Viktor the Clockmaker"),
    "customer.clockworkdiver.elias": ("師匠エリアス", "Master Elias"),
    # gui
    "gui.clockworkdiver.quest_tag": ("第%s話「%s」", "Story %s: %s"),
    "gui.clockworkdiver.ending": ("エンディング", "Ending"),
    "gui.clockworkdiver.ending.done": ("続ける", "Continue"),
    "gui.clockworkdiver.ending.thanks": ("Thank you for playing!", "Thank you for playing!"),
    "gui.clockworkdiver.ending.skip": ("クリックで早送り", "Click to fast-forward"),
    "gui.clockworkdiver.journal": ("修理日誌", "Repair Journal"),
    "gui.clockworkdiver.journal.ending": ("エンディングを見る", "Watch the ending"),
    "gui.clockworkdiver.journal.progress": ("物語 %s/%s", "Story %s/%s"),
    "gui.clockworkdiver.journal.tab": ("%s章", "Ch.%s"),
    "gui.clockworkdiver.journal.locked": ("第%s話　？？？", "Story %s: ???"),
    "gui.clockworkdiver.journal.keepsakes": ("思い出の品 %s/%s", "Keepsakes %s/%s"),
    "gui.clockworkdiver.journal.complete": ("完", "FIN"),
    "gui.clockworkdiver.journal.client": ("依頼人：%s", "Client: %s"),
    "gui.clockworkdiver.journal.clock": ("時計：%s", "Clock: %s"),
    "gui.clockworkdiver.journal.current": ("進行中の依頼。依頼人の話を聞き、時計を直して届けよう。", "In progress. Hear the client out, repair the clock and deliver it."),
    "key.clockworkdiver.journal": ("修理日誌を開く", "Open Repair Journal"),
    # blocks and items
    "item.clockworkdiver.keepsake": ("思い出の品", "Keepsake"),
    "block.clockworkdiver.tuning_bell": ("調律鐘", "Tuning Bell"),
    "block.clockworkdiver.dust_pile": ("埃の山", "Dust Heap"),
    "block.clockworkdiver.memory_cog": ("記憶の欠片", "Memory Fragment"),
    "block.clockworkdiver.tower_gate": ("大時計塔の扉", "Tower Gate"),
    "tooltip.clockworkdiver.keepsake.from": ("第%s話「%s」の思い出", "A memento of Story %s: %s"),
    # commands
    "command.clockworkdiver.story.done": ("物語は完結しています。修理日誌からエンディングを見られます。", "The story is complete. Watch the ending from the Repair Journal."),
    "command.clockworkdiver.story.status": ("物語：第%s話「%s」に挑戦中（%s/%s 話完了）", "Story: now on %s \"%s\" (%s/%s done)"),
    "command.clockworkdiver.story.set": ("物語を第%s話へ進めました", "Story moved to quest %s"),
    # sounds
    "subtitles.clockworkdiver.memory": ("記憶が蘇る", "A memory returns"),
    "subtitles.clockworkdiver.music_box": ("オルゴールが鳴る", "A music box plays"),
    "subtitles.clockworkdiver.story_card": ("物語の鐘", "A story bell rings"),
    "story.clockworkdiver.debug.frag": ("（デバッグ用の記憶の欠片）", "(A debug memory fragment)"),
}


def story_entries():
    out = {}
    for qid, q in st.QUESTS.items():
        k = f"story.{NS}.{qid}."
        out[k + "title"] = q["title"]
        out[k + "clock"] = q["clock"]
        out[k + "clock.desc"] = q["desc"]
        for i, line in enumerate(q["offer"]):
            out[k + f"offer.{i}"] = line
        out[k + "wait"] = q["wait"]
        out[k + "deliver"] = q["deliver"]
        for i, line in enumerate(q["thanks"]):
            out[k + f"thanks.{i}"] = line
        out[k + "summary"] = q["summary"]
        out[k + "keepsake"] = q["keepsake"]
        out[k + "keepsake.desc"] = q["keepsake_desc"]
        for i, line in enumerate(q["frags"]):
            out[k + f"frag.{i}"] = line
        if q["clue"]:
            out[k + "clue"] = q["clue"][0]
            out[k + "clue.desc"] = q["clue"][1]
    for c, (title, sub, summary) in st.CHAPTERS.items():
        k = f"story.{NS}.chapter.{c}"
        out[k] = title
        out[k + ".sub"] = sub
        out[k + ".summary"] = summary
    for scene, lines in st.SCENES.items():
        for who, line in lines.items():
            out[f"story.{NS}.scene.{scene}.{who}"] = line
    for i, line in enumerate(st.ENDING):
        out[f"story.{NS}.ending.{i}"] = line
    return out


def main():
    entries = dict(UI)
    entries.update(story_entries())
    for code, idx in (("ja_jp", 0), ("en_us", 1)):
        path = os.path.join(LANG, code + ".json")
        with open(path, encoding="utf-8") as f:
            data = json.load(f)
        # Drop stale story keys so removed lines do not linger.
        data = {k: v for k, v in data.items() if not k.startswith(f"story.{NS}.")}
        for key, pair in entries.items():
            data[key] = pair[idx]
        with open(path, "w", encoding="utf-8") as f:
            json.dump(data, f, indent=2, ensure_ascii=False)
            f.write("\n")
        print(code, len(data), "keys")


if __name__ == "__main__":
    main()
