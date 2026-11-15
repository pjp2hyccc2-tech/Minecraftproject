"""The second half of every guild house's grounds (owner, 2026-11-15: 「ギルドハウスを全種類2倍くらいの広さにして」
「ギルドハウス内のギミックでマイハウスみたいに一括でアイテムを管理できるギミックを入れて」).

Every design gets a second lobe of its island north of the first, about as large (``hkit.extend``), with the
storehouse (別館, ``hkit.annex``) in the theme's materials: the storage room with the sorting desk and the
storehouse's labelled chests, the council room above, a path from its door to the house, trees, boulders and plants
of the theme. ``hkit.House.finish`` builds it last, so nothing of the house is touched (the lobe only fills empty
ground; the storehouse asserts its place is free).

Placement by tier (the main islands are about ``rx`` x ``rz`` = 15.5 x 14 ... 30.5 x 30 round the origin): a lobe
6 % larger than the island, its centre 1.72 radii north of the origin (they overlap at a neck about a radius wide),
the storehouse a quarter radius north of the lobe's centre, its door to the south, the path from the door to the
main island's north edge with lamp posts, from tier 2 a pavilion on the lobe. The ground grows to about twice the
island's (measured: 1.9 - 2.1 times).
"""
from __future__ import annotations

import math

from capital import blocks as B

from . import hkit as K

F = B.full
PALM = "palm"


def _place(tier, rx, rz, cx=2):
    """Default lobe and storehouse for a main island of radii (rx, rz): the lobe a little larger than the island
    (the two overlap at the neck), its centre 1.72 radii north of the origin."""
    cz = -round(1.72 * rz)
    az = cz - round(0.25 * rz)
    return dict(lobe_at=(cx, cz, rx * 1.06, rz * 1.06), annex_at=(cx, az), facing="south",
                route=[(cx, -round(rz) + 1)])


# ====================================================================================== the twenty storehouses
STOREHOUSES = {}

STOREHOUSES["northern_longhouse"] = dict(
    **_place(1, 15.5, 14.0), style=dict(
        base="cobblestone", floor="spruce_planks", floor2="spruce_planks", wall=("spruce_planks",),
        post=B.log("dark_oak", "y"), band=B.log("dark_oak", "y", wood_block=True), frame=True,
        roof="shingle_roof_tiles", wood="spruce", door="spruce", trim="spruce", carpet="brown", cloth="white",
        banner=("cyan", (("ts", "purple"), ("cbo", "white"), ("mr", "purple"))),
        path=("cobblestone", "mossy_cobblestone", "gravel", "coarse_dirt")),
    surface=("grass_block", "podzol", "coarse_dirt", "moss_block"),
    underside=("stone", "andesite", "cobblestone", "tuff", "stone"), seed=11, trees=("spruce",) * 6, rocks=3,
    plants=("grass", "fern", "grass", "sweet_berry_bush", "dandelion", "lily_of_the_valley"),
    plants_on=("grass_block", "podzol"), density=0.2)

STOREHOUSES["mushroom_grove"] = dict(
    **_place(1, 15.5, 15.0, cx=3), style=dict(
        base="mossy_cobblestone", floor="spruce_planks", floor2="dark_oak_planks",
        wall=("mushroom_stem",), post=B.log("dark_oak", "y"), band=B.log("dark_oak", "y", wood_block=True),
        roof="dark_oak", roof_kind="cap", cap="red_mushroom_block", wood="dark_oak", door="dark_oak",
        trim="dark_oak", carpet="green", cloth="lime", window=B.pane("lime"),
        banner=("red", (("cs", "white"), ("bo", "white"))),
        path=("moss_block", "rooted_dirt", "mossy_cobblestone", "coarse_dirt")),
    surface=("mycelium", "moss_block", "podzol", "moss_block"), soil="dirt",
    underside=("stone", "tuff", "dirt", "andesite", "rooted_dirt"), seed=21, trees=("dark_oak",) * 3, rocks=2,
    rock_states=("mossy_cobblestone", "stone", "moss_block"),
    plants=("brown_mushroom", "red_mushroom", "fern", "moss_carpet", "grass"), plants_on=("mycelium", "moss_block",
                                                                                          "podzol"), density=0.25)

STOREHOUSES["ancient_ruins"] = dict(
    **_place(1, 15.5, 15.0), style=dict(
        base="mossy_stone_bricks", floor="polished_andesite", floor2="spruce_planks",
        wall=("stone_bricks", "mossy_stone_bricks", "cracked_stone_bricks", "stone_bricks"),
        post=B.S("chiseled_stone_bricks"), band=B.S("smooth_stone"), roof="terracotta_roof_tiles", wood="spruce",
        door="spruce", trim="stone_brick", carpet="orange", cloth="white",
        banner=("orange", (("bo", "white"), ("flo", "white"))),
        path=("mossy_stone_bricks", "cracked_stone_bricks", "stone_bricks", "gravel")),
    surface=("grass_block", "moss_block", "coarse_dirt", "grass_block"),
    underside=("stone", "andesite", "mossy_cobblestone", "tuff"), seed=31, trees=("oak", "birch", "oak", "azalea"),
    rocks=3, rock_states=("mossy_cobblestone", "cracked_stone_bricks", "mossy_stone_bricks"),
    plants=("grass", "fern", "poppy", "azure_bluet", "oxeye_daisy"), density=0.22)

STOREHOUSES["pirate_ship"] = dict(
    **_place(1, 15.5, 15.5), style=dict(
        base="sandstone", floor="spruce_planks", floor2="dark_oak_planks",
        wall=("spruce_planks", "dark_oak_planks", "spruce_planks"), post=B.log("spruce", "y"),
        band=B.log("dark_oak", "y", wood_block=True), frame=True, roof="dark_oak", wood="spruce", door="spruce",
        trim="spruce", carpet="red", cloth="white", banner=("black", (("sku", "white"), ("bo", "white"))),
        path=("sand", "coarse_dirt", "spruce_planks", "sand")),
    surface=("sand", "grass_block", "sand", "coarse_dirt"), soil="sand",
    underside=("sandstone", "stone", "sandstone", "andesite"), seed=61, trees=(PALM,) * 5, rocks=2,
    rock_states=("sandstone", "stone", "smooth_sandstone"),
    plants=("dead_bush", "grass", "fern"), plants_on=("grass_block", "sand"), density=0.12,
    drips=("sandstone", "stone"))

STOREHOUSES["flower_glasshouse"] = dict(
    **_place(2, 18.5, 17.5), style=dict(
        base="smooth_quartz", floor="birch_planks", floor2="birch_planks", wall=("white_concrete", "smooth_quartz"),
        post=B.S("quartz_pillar", axis="y"), band=B.S("smooth_quartz"), window=B.pane("light_blue"),
        roof="verdigris_roof_tiles", wood="birch", door="birch", trim="smooth_quartz", carpet="pink",
        cloth="white", banner=("pink", (("flo", "white"), ("bo", "white"))),
        path=("smooth_quartz", "calcite", "moss_block", "smooth_quartz")),
    surface=("grass_block", "moss_block", "grass_block"), underside=("stone", "andesite", "calcite", "stone"),
    seed=71, trees=("cherry", "birch", "flowering_azalea", "cherry", "azalea"), rocks=1,
    plants=("poppy", "dandelion", "azure_bluet", "pink_tulip", "allium", "cornflower", "oxeye_daisy", "grass"),
    density=0.32)

STOREHOUSES["dwarven_forge"] = dict(
    **dict(_place(2, 18.5, 18.0), route=[(8, -22)]), style=dict(
        base="cobbled_deepslate", floor="polished_deepslate", floor2="spruce_planks",
        wall=("deepslate_bricks", "cracked_deepslate_bricks", "deepslate_bricks", "polished_deepslate"),
        post=B.S("polished_basalt"), band=B.S("polished_blackstone_bricks"), roof="slate_roof_tiles",
        wood="spruce", door="spruce", trim="deepslate_brick", carpet="red", cloth="orange",
        banner=("gray", (("mc", "orange"), ("bo", "orange"))),
        path=("cobbled_deepslate", "polished_deepslate", "gravel", "stone")),
    surface=("stone", "andesite", "coarse_dirt"), soil="stone",
    underside=("deepslate", "stone", "tuff", "cobbled_deepslate"), seed=81, trees=("spruce",) * 3, rocks=6,
    rock_states=("stone", "andesite", "cobbled_deepslate"), plants=("grass", "fern"),
    plants_on=("coarse_dirt",), density=0.15, drips=("deepslate", "stone", "tuff"))

STOREHOUSES["desert_palace"] = dict(
    **_place(2, 18.5, 18.0), style=dict(
        base="cut_sandstone", floor="smooth_sandstone", floor2="spruce_planks",
        wall=("smooth_sandstone",), post=B.S("chiseled_sandstone"), band=B.S("cut_sandstone"),
        window=B.pane("orange"), roof="smooth_sandstone", roof_kind="dome", dome="gilded_roof_tiles",
        wood="jungle", door="jungle", trim="smooth_sandstone", carpet="orange", cloth="yellow",
        banner=("yellow", (("bo", "orange"), ("mr", "orange"))),
        path=("smooth_sandstone", "cut_sandstone", "sandstone", "smooth_sandstone")),
    surface=("sand", "sand", "grass_block"), soil="sand",
    underside=("sandstone", "smooth_sandstone", "stone", "sandstone"), seed=91, trees=(PALM,) * 6, rocks=1,
    rock_states=("sandstone", "smooth_sandstone", "stone"), plants=("dead_bush", "grass"),
    plants_on=("sand", "grass_block"), density=0.08, drips=("sandstone", "smooth_sandstone"))

STOREHOUSES["observatory"] = dict(
    **_place(2, 18.5, 18.0), style=dict(
        base="polished_blackstone_bricks", floor="dark_oak_planks", floor2="dark_oak_planks",
        wall=("blue_terracotta",), post=B.S("polished_blackstone_bricks"), band=B.S("gilded_trim"),
        window=B.pane("light_blue"), roof="verdigris_roof_tiles", wood="dark_oak", door="dark_oak",
        trim="polished_blackstone_brick", carpet="blue", cloth="light_blue",
        banner=("blue", (("cr", "yellow"), ("bo", "yellow"))),
        path=("stone", "andesite", "calcite", "polished_andesite")),
    surface=("grass_block", "stone", "andesite", "moss_block"), underside=("stone", "andesite", "calcite", "diorite"),
    seed=101, trees=("spruce", "birch", "spruce"), rocks=3, rock_states=("stone", "andesite", "calcite"),
    plants=("grass", "fern", "azure_bluet", "cornflower"), density=0.2)

STOREHOUSES["japanese_mansion"] = dict(
    **_place(3, 22.5, 21.5), style=dict(
        base="stone_bricks", floor="spruce_planks", floor2="spruce_planks", wall=("white_plaster",),
        post=B.log("dark_oak", "y"), band=B.log("dark_oak", "y", wood_block=True), frame=True,
        roof="kawara_roof_tiles", wood="dark_oak", door="dark_oak", trim="dark_oak", carpet="green",
        cloth="red", banner=("red", (("ci", "white"), ("bo", "black"))),
        path=("gravel", "stone", "gravel", "andesite")),
    surface=("grass_block", "moss_block", "grass_block"), underside=("stone", "andesite", "tuff", "stone"),
    seed=111, trees=("cherry", "cherry", "spruce", "cherry", "dark_oak"), rocks=3,
    rock_states=("stone", "andesite", "mossy_cobblestone"),
    plants=("grass", "fern", "pink_petals", "grass", "lily_of_the_valley"), density=0.22)

STOREHOUSES["great_tree"] = dict(
    **_place(3, 22.5, 22.0), style=dict(
        base="mossy_cobblestone", floor="oak_planks", floor2="spruce_planks", wall=("oak_planks", "birch_planks"),
        post=B.log("oak", "y"), band=B.log("dark_oak", "y", wood_block=True), frame=True,
        roof="thatch_roof", wood="oak", door="oak", trim="oak", carpet="lime", cloth="green",
        banner=("green", (("flo", "lime"), ("bo", "brown"))),
        path=("moss_block", "coarse_dirt", "rooted_dirt", "podzol")),
    surface=("grass_block", "moss_block", "podzol", "grass_block"), underside=("stone", "andesite", "dirt", "tuff"),
    seed=121, trees=("oak", "azalea", "birch", "flowering_azalea", "oak", "dark_oak"), rocks=2,
    plants=("grass", "fern", "dandelion", "poppy", "moss_carpet", "azure_bluet"),
    plants_on=("grass_block", "moss_block", "podzol"), density=0.28)

STOREHOUSES["sage_library"] = dict(
    **_place(3, 22.5, 22.0), style=dict(
        base="stone_bricks", floor="polished_andesite", floor2="dark_oak_planks",
        wall=("stone_bricks", "stone_bricks", "mossy_stone_bricks"), post=B.S("chiseled_stone_bricks"),
        band=B.S("smooth_stone"), roof="slate_roof_tiles", wood="dark_oak", door="dark_oak", trim="stone_brick",
        carpet="blue", cloth="white", banner=("blue", (("bs", "white"), ("bo", "yellow"))),
        path=("stone_bricks", "polished_andesite", "stone_bricks", "andesite")),
    surface=("grass_block", "moss_block", "grass_block"), underside=("stone", "andesite", "stone_bricks", "stone"),
    seed=141, trees=("oak", "birch", "spruce", "oak"), rocks=2,
    plants=("grass", "fern", "lily_of_the_valley", "azure_bluet"), density=0.2)

STOREHOUSES["jungle_temple"] = dict(
    **_place(3, 22.5, 22.0), style=dict(
        base="mossy_cobblestone", floor="jungle_planks", floor2="jungle_planks",
        wall=("mossy_stone_bricks", "stone_bricks", "cracked_stone_bricks", "mossy_stone_bricks"),
        post=B.log("jungle", "y"), band=B.S("chiseled_stone_bricks"), roof="thatch_roof", wood="jungle",
        door="jungle", trim="jungle", carpet="lime", cloth="yellow",
        banner=("lime", (("cr", "yellow"), ("bo", "yellow"))),
        path=("mossy_cobblestone", "coarse_dirt", "mossy_stone_bricks", "podzol")),
    surface=("grass_block", "moss_block", "podzol", "grass_block"),
    underside=("stone", "mossy_cobblestone", "andesite", "dirt"), seed=151,
    trees=("jungle", "jungle", "acacia", "jungle", "oak"), rocks=2,
    rock_states=("mossy_cobblestone", "mossy_stone_bricks", "stone"),
    plants=("fern", "grass", "large_fern", "fern", "melon"), plants_on=("grass_block", "moss_block", "podzol"),
    density=0.3)

STOREHOUSES["gothic_cathedral"] = dict(
    **_place(4, 26.5, 25.5), style=dict(
        base="polished_blackstone_bricks", floor="polished_deepslate", floor2="dark_oak_planks",
        wall=("deepslate_bricks", "deepslate_bricks", "cracked_deepslate_bricks"),
        post=B.S("polished_blackstone_bricks"), band=B.S("polished_deepslate"), window=B.pane("purple"),
        roof="slate_roof_tiles", wood="dark_oak", door="dark_oak", trim="deepslate_brick", carpet="purple",
        cloth="black", soul=True, banner=("black", (("cr", "purple"), ("bo", "purple"))),
        path=("polished_deepslate", "deepslate_bricks", "cobbled_deepslate", "deepslate_tiles")),
    surface=("grass_block", "moss_block", "grass_block", "podzol"),
    underside=("deepslate", "tuff", "stone", "cobbled_deepslate"), seed=161,
    trees=("dark_oak", "spruce", "dark_oak", "spruce", "dark_oak"), rocks=3,
    rock_states=("cobbled_deepslate", "deepslate", "tuff"), plants=("grass", "fern", "lily_of_the_valley"),
    plants_on=("grass_block", "moss_block", "podzol"), density=0.2, drips=("deepslate", "tuff"))

STOREHOUSES["coral_hall"] = dict(
    **_place(4, 26.5, 25.5), style=dict(
        base="dark_prismarine", floor="prismarine_bricks", floor2="birch_planks",
        wall=("prismarine_bricks", "prismarine", "prismarine_bricks"), post=B.S("dark_prismarine"),
        band=B.S("sea_lantern"), window=B.pane("cyan"), roof="dark_prismarine", wood="birch", door="birch",
        trim="prismarine", carpet="cyan", cloth="white", lamp=B.lantern(hanging=True, soul=True), soul=True,
        banner=("cyan", (("flo", "white"), ("bo", "light_blue"))),
        path=("smooth_sandstone", "sand", "prismarine", "smooth_sandstone")),
    surface=("sand", "sand", "smooth_sandstone"), soil="sand",
    underside=("prismarine", "stone", "sandstone", "dark_prismarine"), seed=171, trees=(PALM,) * 7, rocks=2,
    rock_states=("prismarine", "dark_prismarine", "sandstone"), plants=("dead_bush", "grass"),
    plants_on=("sand",), density=0.06, drips=("prismarine", "sandstone"))

STOREHOUSES["volcanic_fortress"] = dict(
    **_place(4, 26.5, 26.0), style=dict(
        base="blackstone", floor="polished_blackstone", floor2="crimson_planks",
        wall=("polished_blackstone_bricks", "cracked_polished_blackstone_bricks", "blackstone"),
        post=B.S("polished_basalt"), band=B.S("gilded_blackstone"), window=B.pane("orange"),
        roof="polished_blackstone_brick", roof_kind="flat", wood="crimson", door="crimson",
        trim="polished_blackstone_brick", carpet="red", cloth="black", soul=False,
        banner=("red", (("mr", "black"), ("bo", "orange"))),
        path=("polished_blackstone", "basalt", "blackstone", "smooth_basalt")),
    surface=("blackstone", "basalt", "smooth_basalt", "tuff"), soil="blackstone",
    underside=("blackstone", "basalt", "tuff", "deepslate"), seed=181, trees=(), rocks=7,
    rock_states=("blackstone", "basalt", "magma_block"), plants=None, drips=("blackstone", "basalt"))

STOREHOUSES["ice_castle"] = dict(
    **_place(4, 26.5, 26.0), style=dict(
        base="packed_ice", floor="spruce_planks", floor2="spruce_planks", wall=("packed_ice", "snow_block"),
        post=B.S("blue_ice"), band=B.S("blue_ice"), window=B.pane("light_blue"), roof="smooth_quartz",
        wood="spruce", door="spruce", trim="smooth_quartz", carpet="light_blue", cloth="white", soul=True,
        banner=("light_blue", (("flo", "white"), ("bo", "white"))),
        path=("packed_ice", "snow_block", "packed_ice", "calcite")),
    surface=("snow_block", "snow_block", "packed_ice", "snow_block"), soil="snow_block",
    underside=("stone", "packed_ice", "andesite", "calcite"), seed=191, trees=("spruce",) * 6, rocks=3,
    rock_states=("packed_ice", "snow_block", "calcite"), plants=None, drips=("packed_ice", "stone"))

STOREHOUSES["dragon_lair"] = dict(
    **_place(5, 30.5, 30.0), style=dict(
        base="blackstone", floor="polished_blackstone", floor2="dark_oak_planks",
        wall=("blackstone", "polished_blackstone_bricks", "basalt"), post=B.S("polished_basalt"),
        band=B.S("gilded_blackstone"), window=B.pane("red"), roof="blackstone", roof_kind="flat",
        wood="dark_oak", door="dark_oak", trim="blackstone", carpet="red", cloth="black",
        banner=("black", (("mr", "red"), ("bo", "red"))),
        path=("polished_blackstone", "blackstone", "basalt", "tuff")),
    surface=("blackstone", "basalt", "tuff", "coarse_dirt"), soil="blackstone",
    underside=("blackstone", "basalt", "tuff", "deepslate"), seed=201, trees=(), rocks=9,
    rock_states=("blackstone", "basalt", "bone_block"), plants=("dead_bush",), plants_on=("coarse_dirt",),
    density=0.1, drips=("blackstone", "basalt"))

STOREHOUSES["arcane_tower"] = dict(
    **_place(5, 30.5, 30.0), style=dict(
        base="purpur_block", floor="dark_oak_planks", floor2="dark_oak_planks", wall=("purpur_block",),
        post=B.S("purpur_pillar", axis="y"), band=B.S("amethyst_block"), window=B.pane("magenta"),
        roof="gilded_roof_tiles", wood="dark_oak", door="dark_oak", trim="purpur_block", carpet="purple",
        cloth="magenta", soul=True, banner=("purple", (("cr", "magenta"), ("bo", "yellow"))),
        path=("calcite", "purpur_block", "smooth_quartz", "calcite")),
    surface=("grass_block", "moss_block", "grass_block", "calcite"),
    underside=("stone", "calcite", "amethyst_block", "tuff", "stone"), seed=221,
    trees=("cherry", "flowering_azalea", "cherry", "birch", "cherry", "azalea"), rocks=3,
    rock_states=("amethyst_block", "calcite", "purpur_block"),
    plants=("allium", "grass", "lilac", "pink_tulip", "fern"), density=0.22)

STOREHOUSES["marble_palace"] = dict(
    **_place(5, 30.5, 30.0), style=dict(
        base="smooth_quartz", floor="polished_diorite", floor2="birch_planks", wall=("quartz_bricks",),
        post=B.S("quartz_pillar", axis="y"), band=B.S("gilded_trim"), window=B.pane("yellow"),
        roof="smooth_quartz", roof_kind="dome", dome="gilded_roof_tiles", wood="birch", door="birch",
        trim="smooth_quartz", carpet="yellow", cloth="white", banner=("white", (("bo", "yellow"), ("cr", "yellow"))),
        path=("smooth_quartz", "polished_diorite", "calcite", "smooth_quartz")),
    surface=("grass_block", "grass_block", "moss_block"), underside=("calcite", "stone", "diorite", "andesite"),
    seed=251, trees=("cypress", "cypress", "oak", "cypress", "birch", "cypress"), rocks=0,
    plants=("grass", "poppy", "oxeye_daisy", "azure_bluet"), density=0.18)


def _sky_bridge(w, cells):
    """Sky temple: the storehouse island floats free; an arched bridge of white marble with gilded posts joins it
    to the main island (like the temple's other islands)."""
    q, gold = F("smooth_quartz"), F("gilded_trim")
    x0, z0, x1, z1 = -12, -16, -16, -27
    n = max(abs(x1 - x0), abs(z1 - z0))
    for i in range(n + 1):
        x = x0 + (x1 - x0) * i // n
        z = z0 + (z1 - z0) * i // n
        arch = int(round(2.0 * math.sin(math.pi * i / n)))
        for dw in (-1, 0, 1):
            w.set(x + dw, arch, z, q)
            w.set(x + dw, arch - 1, z, q)
            for k in (1, 2, 3):
                if w.get(x + dw, arch + k, z).split("[")[0].split(":")[-1] in K.SOFT:
                    w.set(x + dw, arch + k, z, K.AIR)
        for dw in (-2, 2):
            w.set(x + dw, arch + 1, z, B.wall("diorite") if i % 4 else gold)
    del cells


STOREHOUSES["sky_temple"] = dict(
    lobe_at=(-30, -46, 26.0, 22.0), annex_at=(-31, -48), facing="east", route=[(-14, -40), (-16, -27)],
    style=dict(
        base="smooth_quartz", floor="polished_diorite", floor2="birch_planks", wall=("smooth_quartz", "calcite"),
        post=B.S("quartz_pillar", axis="y"), band=B.S("gilded_trim"), window=B.pane("yellow"),
        roof="gilded_roof_tiles", wood="birch", door="birch", trim="smooth_quartz", carpet="yellow",
        cloth="white", banner=("white", (("bo", "yellow"), ("mc", "yellow"))),
        path=("smooth_quartz", "calcite", "polished_diorite", "smooth_quartz")),
    surface=("grass_block", "grass_block", "moss_block"), underside=("calcite", "stone", "diorite", "calcite"),
    depth=10, seed=231, trees=("birch", "cherry", "birch", "flowering_azalea"), rocks=0,
    plants=("grass", "oxeye_daisy", "azure_bluet", "lily_of_the_valley"), density=0.2,
    drips=("calcite", "diorite"), dress=_sky_bridge)


def build(h, w):
    """The storehouse of ``h``'s design (from ``House.finish``)."""
    cfg = dict(STOREHOUSES[h.theme])
    return K.extend(h, w, **cfg)
