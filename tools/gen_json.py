"""Writes the JSON resources of Clockwork Diver: blockstates, models, sounds, dimensions, biomes, loot, recipes, tags.

Run from the repository root:  python3 tools/gen_json.py
"""
import json
import os

NS = "clockworkdiver"
RES = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources")
ASSETS = os.path.join(RES, "assets", NS)
DATA = os.path.join(RES, "data", NS)


def write(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(obj, f, indent=2, ensure_ascii=False)
        f.write("\n")


def tex(name):
    return f"{NS}:block/{name}"


def face(uv, texture, cull=None):
    d = {"uv": uv, "texture": texture}
    if cull:
        d["cullface"] = cull
    return d


def box(frm, to, textures, uvs=None):
    """textures: dict face->texture ref; uvs optional dict face->uv."""
    faces = {}
    for f, t in textures.items():
        uv = (uvs or {}).get(f)
        if uv is None:
            if f in ("up", "down"):
                uv = [frm[0], frm[2], to[0], to[2]]
            elif f in ("north", "south"):
                uv = [frm[0], 16 - to[1], to[0], 16 - frm[1]]
            else:
                uv = [frm[2], 16 - to[1], to[2], 16 - frm[1]]
        faces[f] = {"uv": uv, "texture": t}
    return {"from": frm, "to": to, "faces": faces}


ALL6 = ("north", "south", "east", "west", "up", "down")


def all_faces(t):
    return {f: t for f in ALL6}


def block_models():
    m = os.path.join(ASSETS, "models", "block")
    for name in ("brass_block", "brass_plate", "brass_tiles", "rusty_plate", "jewel_bearing"):
        write(os.path.join(m, name + ".json"), {"parent": "minecraft:block/cube_all", "textures": {"all": tex(name)}})
    write(os.path.join(m, "steam_vent.json"), {"parent": "minecraft:block/cube_bottom_top", "textures": {
        "top": tex("steam_vent_top"), "bottom": tex("steam_vent_side"), "side": tex("steam_vent_side")}})
    write(os.path.join(m, "screw_head.json"), {"parent": "minecraft:block/cube_column", "textures": {
        "end": tex("screw_head_top"), "side": tex("screw_head_side")}})

    side, end = "#side", "#end"
    write(os.path.join(m, "copper_pipe.json"), {
        "parent": "minecraft:block/block",
        "textures": {"particle": tex("copper_pipe_side"), "side": tex("copper_pipe_side"), "end": tex("copper_pipe_end")},
        "elements": [
            box([4, 0, 4], [12, 16, 12], {"north": side, "south": side, "east": side, "west": side, "up": end, "down": end},
                {"north": [4, 0, 12, 16], "south": [4, 0, 12, 16], "east": [4, 0, 12, 16], "west": [4, 0, 12, 16],
                 "up": [4, 4, 12, 12], "down": [4, 4, 12, 12]}),
            box([3, 0, 3], [13, 2, 13], {"north": side, "south": side, "east": side, "west": side, "up": end, "down": end},
                {"north": [3, 0, 13, 2], "south": [3, 0, 13, 2], "east": [3, 0, 13, 2], "west": [3, 0, 13, 2],
                 "up": [3, 3, 13, 13], "down": [3, 3, 13, 13]}),
            box([3, 14, 3], [13, 16, 13], {"north": side, "south": side, "east": side, "west": side, "up": end, "down": end},
                {"north": [3, 14, 13, 16], "south": [3, 14, 13, 16], "east": [3, 14, 13, 16], "west": [3, 14, 13, 16],
                 "up": [3, 3, 13, 13], "down": [3, 3, 13, 13]}),
        ]})

    r = "#rust"
    write(os.path.join(m, "rust_cluster.json"), {
        "parent": "minecraft:block/block",
        "textures": {"particle": tex("rust_cluster"), "rust": tex("rust_cluster")},
        "elements": [
            box([2, 0, 2], [14, 7, 14], all_faces(r)),
            box([4, 7, 3], [11, 13, 10], all_faces(r)),
            box([9, 5, 8], [14, 11, 13], all_faces(r)),
            box([1, 3, 9], [6, 9, 14], all_faces(r)),
            box([6, 11, 6], [9, 14, 9], all_faces(r)),
        ]})

    for filled in (False, True):
        t = {"particle": tex("tooth_socket_side"), "side": tex("tooth_socket_side"),
             "top": tex("tooth_socket_top_filled" if filled else "tooth_socket_top")}
        write(os.path.join(m, "tooth_socket" + ("_filled" if filled else "") + ".json"), {
            "parent": "minecraft:block/block", "textures": t,
            "elements": [box([1, 0, 1], [15, 12, 15], {"north": "#side", "south": "#side", "east": "#side", "west": "#side",
                                                         "up": "#top", "down": "#side"},
                             {"up": [0, 0, 16, 16]})]})

    write(os.path.join(m, "winding_crank.json"), {
        "parent": "minecraft:block/block",
        "textures": {"particle": tex("winding_crank_side"), "side": tex("winding_crank_side"), "top": tex("winding_crank_top")},
        "elements": [
            box([2, 0, 2], [14, 4, 14], {"north": "#side", "south": "#side", "east": "#side", "west": "#side", "up": "#top", "down": "#side"},
                {"up": [2, 2, 14, 14]}),
            box([6, 4, 6], [10, 12, 10], {"north": "#side", "south": "#side", "east": "#side", "west": "#side", "up": "#top", "down": "#side"}),
            box([2, 12, 6], [14, 14, 10], {"north": "#side", "south": "#side", "east": "#side", "west": "#side", "up": "#side", "down": "#side"}),
            box([2, 14, 7], [4, 15, 9], all_faces("#top")),
        ]})

    t = {"particle": tex("repair_bench_side"), "top": tex("repair_bench_top"), "side": tex("repair_bench_side"),
         "front": tex("repair_bench_front"), "metal": tex("brass_block")}
    write(os.path.join(m, "repair_bench.json"), {
        "parent": "minecraft:block/block", "textures": t,
        "elements": [
            box([0, 12, 0], [16, 16, 16], {"north": "#front", "south": "#side", "east": "#side", "west": "#side", "up": "#top", "down": "#side"},
                {"north": [0, 0, 16, 4], "south": [0, 0, 16, 4], "east": [0, 0, 16, 4], "west": [0, 0, 16, 4], "up": [0, 0, 16, 16]}),
            box([1, 0, 1], [3, 12, 3], all_faces("#side")),
            box([13, 0, 1], [15, 12, 3], all_faces("#side")),
            box([1, 0, 13], [3, 12, 15], all_faces("#side")),
            box([13, 0, 13], [15, 12, 15], all_faces("#side")),
            box([2, 5, 2], [14, 11, 14], {"north": "#front", "south": "#side", "east": "#side", "west": "#side", "up": "#side", "down": "#side"},
                {"north": [2, 5, 14, 11]}),
            box([5, 16, 1], [11, 18, 3], all_faces("#metal")),
            box([13, 16, 12], [14, 25, 13], all_faces("#metal")),
            box([9, 24, 9], [14, 26, 14], all_faces("#metal")),
        ]})

    write(os.path.join(m, "mechanism.json"), {
        "parent": "minecraft:block/block",
        "textures": {"particle": tex("mechanism"), "hub": tex("mechanism")},
        "elements": [box([5.5, 5.5, 5.5], [10.5, 10.5, 10.5], all_faces("#hub"), {f: [3, 3, 13, 13] for f in ALL6})]})


def blockstates():
    b = os.path.join(ASSETS, "blockstates")
    for name in ("brass_block", "brass_plate", "brass_tiles", "rusty_plate", "jewel_bearing", "steam_vent", "screw_head", "rust_cluster",
                 "winding_crank", "mechanism"):
        write(os.path.join(b, name + ".json"), {"variants": {"": {"model": f"{NS}:block/{name}"}}})
    write(os.path.join(b, "copper_pipe.json"), {"variants": {
        "axis=y": {"model": f"{NS}:block/copper_pipe"},
        "axis=z": {"model": f"{NS}:block/copper_pipe", "x": 90},
        "axis=x": {"model": f"{NS}:block/copper_pipe", "x": 90, "y": 90}}})
    write(os.path.join(b, "tooth_socket.json"), {"variants": {
        "filled=false": {"model": f"{NS}:block/tooth_socket"},
        "filled=true": {"model": f"{NS}:block/tooth_socket_filled"}}})
    write(os.path.join(b, "repair_bench.json"), {"variants": {
        "facing=north": {"model": f"{NS}:block/repair_bench"},
        "facing=east": {"model": f"{NS}:block/repair_bench", "y": 90},
        "facing=south": {"model": f"{NS}:block/repair_bench", "y": 180},
        "facing=west": {"model": f"{NS}:block/repair_bench", "y": 270}}})


CLOCKS = ["pocket_watch", "cuckoo", "astronomical", "birdcage", "steam_tower", "orrery"]


def item_models():
    m = os.path.join(ASSETS, "models", "item")
    for name in ("brass_block", "brass_plate", "brass_tiles", "rusty_plate", "copper_pipe", "jewel_bearing", "steam_vent", "repair_bench",
                 "rust_cluster", "tooth_socket", "screw_head", "winding_crank"):
        write(os.path.join(m, name + ".json"), {"parent": f"{NS}:block/{name}"})
    for name in ("chrono_spectacles", "gear_tooth", "brass_coin", "workshop_key", "bolt"):
        write(os.path.join(m, name + ".json"), {"parent": "minecraft:item/generated", "textures": {"layer0": f"{NS}:item/{name}"}})
    write(os.path.join(m, "tinker_wrench.json"), {"parent": "minecraft:item/handheld", "textures": {"layer0": f"{NS}:item/tinker_wrench"}})
    for egg in ("customer", "rust_mite", "cog_gremlin", "dust_moth", "time_eater"):
        write(os.path.join(m, egg + "_spawn_egg.json"), {"parent": "minecraft:item/template_spawn_egg"})
    overrides = []
    for i, c in enumerate(CLOCKS):
        write(os.path.join(m, "broken_clock_" + c + ".json"), {"parent": "minecraft:item/generated",
                                                                "textures": {"layer0": f"{NS}:item/clock_{c}"}})
        if i > 0:
            overrides.append({"predicate": {f"{NS}:clock_type": round(i / 10.0 - 0.05, 2)}, "model": f"{NS}:item/broken_clock_{c}"})
    write(os.path.join(m, "broken_clock.json"), {"parent": "minecraft:item/generated", "textures": {"layer0": f"{NS}:item/clock_pocket_watch"},
                                                  "overrides": overrides})


SOUNDS = ["tick", "tock", "perfect", "steam_burst", "shrink", "grow", "discover", "repaired", "ratchet", "clunk", "shop_bell", "glasses",
          "mite", "gremlin", "moth", "boss_roar", "boss_pulse", "fanfare"]


def sounds():
    obj = {}
    for s in SOUNDS:
        obj[s] = {"subtitle": f"subtitles.{NS}.{s}", "sounds": [f"{NS}:{s}"]}
    write(os.path.join(ASSETS, "sounds.json"), obj)


def dimensions():
    write(os.path.join(DATA, "dimension_type", "workshop.json"), {
        "ultrawarm": False, "natural": False, "coordinate_scale": 1.0, "has_skylight": True, "has_ceiling": False,
        "ambient_light": 0.0, "fixed_time": 12700, "monster_spawn_light_level": 0, "monster_spawn_block_light_limit": 0,
        "piglin_safe": False, "bed_works": False, "respawn_anchor_works": False, "has_raids": False,
        "logical_height": 256, "min_y": 0, "height": 256, "infiniburn": "#minecraft:infiniburn_overworld",
        "effects": "minecraft:overworld"})
    write(os.path.join(DATA, "dimension_type", "clock_interior.json"), {
        "ultrawarm": False, "natural": False, "coordinate_scale": 1.0, "has_skylight": False, "has_ceiling": True,
        "ambient_light": 0.15, "fixed_time": 18000, "monster_spawn_light_level": 0, "monster_spawn_block_light_limit": 0,
        "piglin_safe": False, "bed_works": False, "respawn_anchor_works": False, "has_raids": False,
        "logical_height": 256, "min_y": 0, "height": 256, "infiniburn": "#minecraft:infiniburn_overworld",
        "effects": "minecraft:the_nether"})
    for name in ("workshop", "clock_interior"):
        write(os.path.join(DATA, "dimension", name + ".json"), {
            "type": f"{NS}:{name}",
            "generator": {"type": "minecraft:flat", "settings": {
                "biome": f"{NS}:{name}", "lakes": False, "features": False,
                "layers": [{"block": "minecraft:air", "height": 1}], "structure_overrides": []}}})
    write(os.path.join(DATA, "worldgen", "biome", "workshop.json"), {
        "has_precipitation": False, "temperature": 0.8, "downfall": 0.0,
        "effects": {"fog_color": 0xE8A070, "sky_color": 0x7BA4FF, "water_color": 0x3F76E4, "water_fog_color": 0x050533,
                    "mood_sound": {"sound": "minecraft:ambient.cave", "tick_delay": 6000, "block_search_extent": 8, "offset": 2.0}},
        "spawners": {}, "spawn_costs": {}, "carvers": {}, "features": []})
    write(os.path.join(DATA, "worldgen", "biome", "clock_interior.json"), {
        "has_precipitation": False, "temperature": 0.8, "downfall": 0.0,
        "effects": {"fog_color": 0x6B4E2A, "sky_color": 0x2A1E12, "water_color": 0x3F76E4, "water_fog_color": 0x050533,
                    "particle": {"options": {"type": "minecraft:white_ash"}, "probability": 0.006}},
        "spawners": {}, "spawn_costs": {}, "carvers": {}, "features": []})


def loot():
    for name in ("brass_block", "brass_plate", "brass_tiles", "rusty_plate", "copper_pipe", "jewel_bearing", "steam_vent", "repair_bench",
                 "tooth_socket", "screw_head", "winding_crank"):
        write(os.path.join(DATA, "loot_tables", "blocks", name + ".json"), {
            "type": "minecraft:block",
            "pools": [{"rolls": 1, "bonus_rolls": 0, "entries": [{"type": "minecraft:item", "name": f"{NS}:{name}"}],
                       "conditions": [{"condition": "minecraft:survives_explosion"}]}]})


def shaped(name, pattern, key, result, count=1):
    write(os.path.join(DATA, "recipes", name + ".json"), {
        "type": "minecraft:crafting_shaped", "category": "misc", "pattern": pattern,
        "key": {k: {"item": v} for k, v in key.items()}, "result": {"item": result, "count": count}})


def recipes():
    shaped("workshop_key", [" G ", "GCG", " I "], {"G": "minecraft:gold_ingot", "C": "minecraft:clock", "I": "minecraft:iron_ingot"},
           f"{NS}:workshop_key")
    shaped("tinker_wrench", ["  I", " C ", "C  "], {"I": "minecraft:iron_ingot", "C": "minecraft:copper_ingot"}, f"{NS}:tinker_wrench")
    shaped("chrono_spectacles", ["N N", "PGP"], {"N": "minecraft:gold_nugget", "P": "minecraft:glass_pane", "G": "minecraft:gold_ingot"},
           f"{NS}:chrono_spectacles")
    shaped("brass_block", ["CCC", "CGC", "CCC"], {"C": "minecraft:copper_ingot", "G": "minecraft:gold_ingot"}, f"{NS}:brass_block", 8)
    shaped("brass_plate", ["BB", "BB"], {"B": f"{NS}:brass_block"}, f"{NS}:brass_plate", 4)
    shaped("brass_tiles", ["PP", "PP"], {"P": f"{NS}:brass_plate"}, f"{NS}:brass_tiles", 4)
    shaped("copper_pipe", ["C", "C", "C"], {"C": "minecraft:copper_ingot"}, f"{NS}:copper_pipe", 6)
    shaped("jewel_bearing", [" R ", "RBR", " R "], {"R": "minecraft:redstone", "B": f"{NS}:brass_block"}, f"{NS}:jewel_bearing", 2)
    shaped("steam_vent", ["I I", "IBI"], {"I": "minecraft:iron_bars", "B": "minecraft:iron_block"}, f"{NS}:steam_vent", 2)
    shaped("repair_bench", ["ICI", "PPP", "P P"], {"I": "minecraft:iron_ingot", "C": "minecraft:clock", "P": "minecraft:spruce_planks"},
           f"{NS}:repair_bench")


def tags():
    pick = [f"{NS}:{n}" for n in ("brass_block", "brass_plate", "brass_tiles", "rusty_plate", "copper_pipe", "jewel_bearing", "steam_vent",
                                   "tooth_socket", "screw_head", "winding_crank", "mechanism")]
    write(os.path.join(RES, "data", "minecraft", "tags", "blocks", "mineable", "pickaxe.json"), {"replace": False, "values": pick})
    write(os.path.join(RES, "data", "minecraft", "tags", "blocks", "mineable", "axe.json"), {"replace": False, "values": [f"{NS}:repair_bench"]})


if __name__ == "__main__":
    block_models()
    blockstates()
    item_models()
    sounds()
    dimensions()
    loot()
    recipes()
    tags()
    print("json written")
