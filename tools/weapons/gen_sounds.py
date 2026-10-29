#!/usr/bin/env python3
"""Generates src/main/sounds/weapons.json (weapon sound events built from vanilla sound files)."""
import json, os

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..')

def snd(subtitle, files):
    return {"subtitle": f"subtitles.huntcraft.{subtitle}", "sounds": [
        {"name": f"minecraft:{f}", **({"pitch": p} if p != 1.0 else {}), **({"volume": v} if v != 1.0 else {})}
        for f, p, v in files]}

S = {
    "weapon.swing_heavy": snd("weapon.swing", [("entity/player/attack/sweep1", 0.6, 1.0), ("entity/player/attack/sweep2", 0.55, 1.0), ("entity/player/attack/sweep3", 0.65, 1.0)]),
    "weapon.swing_light": snd("weapon.swing", [("entity/player/attack/sweep4", 1.3, 0.8), ("entity/player/attack/sweep5", 1.4, 0.8), ("entity/player/attack/sweep6", 1.35, 0.8)]),
    "weapon.slam": snd("weapon.slam", [("random/explode1", 1.4, 0.55), ("random/explode2", 1.5, 0.55), ("random/explode3", 1.45, 0.55)]),
    "weapon.guard": snd("weapon.guard", [("item/shield/block1", 0.9, 1.0), ("item/shield/block2", 0.9, 1.0), ("item/shield/block3", 0.85, 1.0)]),
    "weapon.spirit_up": snd("weapon.spirit_up", [("block/amethyst/resonate1", 1.4, 1.0), ("block/amethyst/resonate2", 1.5, 1.0)]),
    "weapon.spirit_slash": snd("weapon.spirit_slash", [("entity/player/attack/sweep1", 1.6, 0.9), ("entity/player/attack/sweep2", 1.7, 0.9)]),
    "weapon.foresight": snd("weapon.foresight", [("item/trident/riptide1", 1.5, 0.6)]),
    "weapon.demon_on": snd("weapon.demon_on", [("mob/ghast/fireball4", 0.7, 0.8)]),
    "weapon.demon_off": snd("weapon.demon_off", [("random/fizz", 1.2, 0.6)]),
    "weapon.blade_dance": snd("weapon.blade_dance", [("entity/player/attack/sweep5", 1.8, 1.0), ("entity/player/attack/sweep6", 1.9, 1.0)]),
    "weapon.bash": snd("weapon.bash", [("random/anvil_land", 1.4, 0.45)]),
    "weapon.counter": snd("weapon.counter", [("item/trident/pierce1", 1.1, 1.0), ("item/trident/pierce2", 1.2, 1.0)]),
    "weapon.dash": snd("weapon.dash", [("mob/ravager/step1", 1.2, 0.8), ("mob/ravager/step2", 1.25, 0.8), ("mob/ravager/step3", 1.15, 0.8)]),
    "weapon.horn_note1": snd("weapon.horn", [("note/flute", 1.0, 1.0)]),
    "weapon.horn_note2": snd("weapon.horn", [("note/bell", 1.0, 1.0)]),
    "weapon.horn_note3": snd("weapon.horn", [("note/didgeridoo", 1.0, 1.0)]),
    "weapon.horn_song": snd("weapon.horn_song", [("note/icechime", 1.0, 1.0), ("note/pling", 1.0, 1.0)]),
    "weapon.bow_shot": snd("weapon.bow_shot", [("random/bow", 1.1, 1.0)]),
    "weapon.bow_draw": snd("weapon.bow_draw", [("item/crossbow/loading_middle1", 1.3, 0.8), ("item/crossbow/loading_middle2", 1.3, 0.8)]),
    "weapon.coat_switch": snd("weapon.coat_switch", [("item/crossbow/loading_end", 1.6, 0.7)]),
    "weapon.sharpen_done": snd("weapon.sharpen_done", [("random/anvil_use", 1.8, 0.5)]),
}

out = os.path.join(ROOT, 'src', 'main', 'sounds', 'weapons.json')
with open(out, 'w', encoding='utf-8') as f:
    json.dump(S, f, indent=1, ensure_ascii=False)
print('wrote', out, len(S), 'sounds')
