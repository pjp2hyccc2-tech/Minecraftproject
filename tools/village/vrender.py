"""Quick isometric / top-down preview renderer for vworld.World (approximate block colours and shapes)."""
import math

from PIL import Image, ImageDraw

C = {
    'grass_block': (96, 156, 64), 'dirt': (134, 96, 67), 'coarse_dirt': (119, 85, 59), 'rooted_dirt': (144, 104, 76),
    'dirt_path': (166, 140, 86), 'gravel': (132, 127, 126), 'sand': (219, 207, 163), 'podzol': (91, 63, 24),
    'moss_block': (89, 109, 45), 'mud': (60, 57, 60), 'clay': (160, 166, 179), 'packed_mud': (142, 107, 80),
    'stone': (125, 125, 125), 'cobblestone': (122, 122, 122), 'mossy_cobblestone': (110, 118, 94),
    'stone_bricks': (122, 121, 122), 'mossy_stone_bricks': (115, 121, 105), 'cracked_stone_bricks': (118, 117, 118),
    'chiseled_stone_bricks': (119, 118, 119), 'andesite': (136, 136, 137), 'polished_andesite': (132, 135, 134),
    'granite': (149, 103, 85), 'diorite': (188, 188, 188), 'tuff': (108, 109, 102), 'calcite': (223, 224, 220),
    'deepslate': (80, 80, 82), 'cobbled_deepslate': (77, 77, 80), 'deepslate_tiles': (54, 54, 55),
    'deepslate_bricks': (70, 70, 71), 'polished_deepslate': (72, 72, 73), 'blackstone': (42, 36, 41),
    'bricks': (150, 97, 83), 'mud_bricks': (137, 103, 79), 'smooth_stone': (158, 158, 158),
    'white_concrete': (207, 213, 214), 'red_concrete': (142, 32, 32), 'black_concrete': (8, 10, 15),
    'white_terracotta': (209, 178, 161), 'red_terracotta': (143, 61, 46), 'terracotta': (152, 94, 67),
    'brown_terracotta': (77, 51, 35), 'light_gray_terracotta': (135, 107, 98), 'smooth_quartz': (235, 229, 222),
    'quartz_block': (235, 229, 222), 'white_wool': (234, 236, 236), 'red_wool': (161, 39, 34),
    'snow_block': (249, 254, 254), 'snow': (249, 254, 254), 'ice': (145, 183, 253), 'packed_ice': (141, 180, 250),
    'water': (52, 94, 200), 'lava': (207, 92, 20), 'lava_cauldron': (207, 92, 20), 'water_cauldron': (70, 110, 200),
    'oak_planks': (162, 130, 78), 'spruce_planks': (114, 84, 48), 'dark_oak_planks': (66, 43, 20),
    'birch_planks': (192, 175, 121), 'jungle_planks': (160, 115, 80), 'acacia_planks': (168, 90, 50),
    'mangrove_planks': (117, 54, 48), 'cherry_planks': (226, 178, 172), 'bamboo_planks': (193, 173, 80),
    'bamboo_mosaic': (190, 170, 78), 'crimson_planks': (101, 48, 70),
    'oak_log': (109, 85, 50), 'spruce_log': (58, 37, 16), 'dark_oak_log': (60, 46, 26), 'birch_log': (216, 215, 210),
    'stripped_dark_oak_log': (72, 56, 36), 'stripped_spruce_log': (115, 89, 52), 'stripped_oak_log': (177, 144, 86),
    'cherry_log': (54, 33, 44), 'stripped_cherry_log': (215, 145, 148), 'bamboo_block': (127, 144, 58),
    'stripped_bamboo_block': (195, 176, 84), 'mangrove_log': (84, 66, 36),
    'oak_leaves': (60, 120, 40), 'spruce_leaves': (50, 85, 50), 'cherry_leaves': (232, 170, 205),
    'azalea_leaves': (90, 120, 40), 'flowering_azalea_leaves': (110, 120, 70), 'dark_oak_leaves': (50, 100, 30),
    'hay_block': (166, 139, 12), 'barrel': (132, 98, 54), 'bookshelf': (117, 94, 59), 'crafting_table': (140, 100, 60),
    'anvil': (68, 68, 68), 'smithing_table': (57, 58, 70), 'blast_furnace': (80, 80, 81), 'furnace': (110, 110, 110),
    'smoker': (90, 80, 70), 'grindstone': (140, 140, 140), 'cauldron': (60, 60, 64), 'campfire': (230, 140, 40),
    'lantern': (240, 190, 90), 'soul_lantern': (90, 200, 220), 'glowstone': (250, 210, 140), 'shroomlight': (240, 150, 70),
    'iron_block': (220, 220, 220), 'gold_block': (246, 208, 61), 'coal_block': (16, 16, 16), 'target': (226, 170, 157),
    'bell': (240, 200, 60), 'chain': (60, 60, 70), 'iron_bars': (110, 110, 110), 'ladder': (124, 96, 56),
    'composter': (110, 75, 40), 'loom': (150, 120, 90), 'lectern': (170, 130, 80), 'flower_pot': (124, 68, 52),
    'decorated_pot': (140, 80, 60), 'melon': (113, 146, 31), 'pumpkin': (198, 118, 24), 'dried_kelp_block': (50, 60, 40),
    'lily_pad': (32, 128, 48), 'piston': (150, 130, 100), 'item_box': (120, 70, 40), 'village_quest_board': (150, 110, 60),
    'paper_lantern': (230, 70, 50), 'sea_lantern': (170, 200, 190), 'ochre_froglight': (250, 240, 170),
}
KEYWORDS = [
    ('cherry_leaves', (232, 170, 205)), ('leaves', (60, 115, 45)), ('pink_petals', (240, 175, 205)),
    ('deepslate_tile', (54, 54, 55)), ('deepslate_brick', (70, 70, 71)), ('polished_deepslate', (72, 72, 73)),
    ('cobbled_deepslate', (77, 77, 80)), ('mossy_stone_brick', (115, 121, 105)), ('stone_brick', (122, 121, 122)),
    ('mossy_cobblestone', (110, 118, 94)), ('cobblestone', (122, 122, 122)), ('polished_andesite', (132, 135, 134)),
    ('andesite', (136, 136, 137)), ('smooth_stone', (158, 158, 158)), ('blackstone', (42, 36, 41)),
    ('mud_brick', (137, 103, 79)), ('brick', (150, 97, 83)), ('quartz', (235, 229, 222)), ('sandstone', (216, 203, 155)),
    ('dark_oak', (66, 43, 20)), ('spruce', (114, 84, 48)), ('oak', (162, 130, 78)), ('birch', (192, 175, 121)),
    ('cherry', (226, 178, 172)), ('bamboo', (193, 173, 80)), ('mangrove', (117, 54, 48)), ('crimson', (101, 48, 70)),
    ('acacia', (168, 90, 50)), ('jungle', (160, 115, 80)), ('warped', (43, 104, 99)),
    ('red_', (160, 40, 35)), ('white_', (230, 230, 230)), ('blue_', (50, 70, 160)), ('light_blue', (90, 150, 210)),
    ('yellow_', (230, 200, 50)), ('green_', (80, 110, 40)), ('lime_', (110, 180, 40)), ('orange_', (230, 120, 30)),
    ('black_', (25, 25, 30)), ('gray_', (70, 70, 75)), ('light_gray', (150, 150, 150)), ('brown_', (110, 75, 45)),
    ('purple_', (110, 40, 160)), ('magenta', (180, 60, 170)), ('cyan_', (30, 130, 140)), ('pink_', (230, 140, 170)),
    ('tall_grass', (90, 150, 60)), ('grass', (90, 150, 60)), ('fern', (80, 140, 60)), ('bamboo', (100, 150, 40)),
    ('poppy', (200, 30, 30)), ('rose', (200, 30, 40)), ('dandelion', (240, 220, 40)), ('cornflower', (80, 100, 220)),
    ('allium', (180, 110, 220)), ('bluet', (220, 230, 240)), ('daisy', (240, 240, 220)), ('lily_of', (240, 240, 240)),
    ('tulip', (230, 100, 60)), ('lilac', (200, 140, 210)), ('peony', (230, 170, 200)), ('azalea', (90, 130, 50)),
    ('lantern', (240, 190, 90)), ('torch', (250, 200, 80)), ('candle', (240, 220, 190)), ('sign', (114, 84, 48)),
    ('banner', (200, 50, 50)), ('carpet', (180, 60, 50)), ('bed', (180, 40, 40)), ('wheat', (200, 180, 70)),
    ('potted', (124, 68, 52)), ('snow', (249, 254, 254)), ('stone', (125, 125, 125)), ('wool', (220, 220, 220)),
    ('terracotta', (152, 94, 67)), ('concrete', (200, 200, 200)), ('glass', (200, 230, 240)),
]


def color_of(name):
    n = name.split(':')[-1]
    if n in C:
        return C[n]
    for k, c in KEYWORDS:
        if k in n:
            return c
    return (200, 0, 200)


def shape_of(name, props):
    n = name.split(':')[-1]
    if n == 'air':
        return None
    if n in ('water', 'lava'):
        return (0, 0, 0, 1, 0.88, 1)
    if n.endswith('_slab'):
        t = props.get('type', 'bottom')
        return (0, 0, 0, 1, 1, 1) if t == 'double' else ((0, 0.5, 0, 1, 1, 1) if t == 'top' else (0, 0, 0, 1, 0.5, 1))
    if n.endswith('carpet') or n in ('lily_pad', 'pink_petals', 'snow') or 'pressure_plate' in n:
        return (0, 0, 0, 1, 0.08, 1)
    if n.endswith('_fence') or n.endswith('_wall') or n.endswith('pane') or n in ('iron_bars', 'chain', 'end_rod', 'lightning_rod'):
        return (0.37, 0, 0.37, 0.63, 1, 0.63)
    if 'trapdoor' in n:
        if props.get('open') == 'true':
            f = props.get('facing', 'north')
            return {'north': (0, 0, 0.81, 1, 1, 1), 'south': (0, 0, 0, 1, 1, 0.19), 'east': (0, 0, 0, 0.19, 1, 1), 'west': (0.81, 0, 0, 1, 1, 1)}[f]
        return (0, 0.81, 0, 1, 1, 1) if props.get('half') == 'top' else (0, 0, 0, 1, 0.19, 1)
    if n.endswith('_door'):
        f = props.get('facing', 'north')
        return {'north': (0, 0, 0.81, 1, 1, 1), 'south': (0, 0, 0, 1, 1, 0.19), 'east': (0, 0, 0, 0.19, 1, 1), 'west': (0.81, 0, 0, 1, 1, 1)}[f]
    if 'lantern' in n and 'paper' not in n:
        return (0.3, 0.1 if props.get('hanging') == 'true' else 0, 0.3, 0.7, 0.6, 0.7)
    if 'paper_lantern' in n:
        return (0.2, 0.1, 0.2, 0.8, 0.9, 0.8)
    if 'torch' in n or 'candle' in n:
        return (0.42, 0, 0.42, 0.58, 0.6, 0.58)
    if n in ('grass', 'fern', 'tall_grass', 'large_fern') or 'flower' in n or 'tulip' in n or n in (
            'poppy', 'dandelion', 'cornflower', 'allium', 'azure_bluet', 'oxeye_daisy', 'lily_of_the_valley', 'rose_bush',
            'lilac', 'peony', 'sweet_berry_bush', 'wheat', 'carrots', 'potatoes'):
        return (0.25, 0, 0.25, 0.75, 0.8, 0.75)
    if n == 'bamboo':
        return (0.4, 0, 0.4, 0.6, 1, 0.6)
    if 'sign' in n or 'banner' in n:
        return (0.1, 0.1, 0.4, 0.9, 0.9, 0.6)
    if n.endswith('_bed'):
        return (0, 0, 0, 1, 0.56, 1)
    if n in ('campfire', 'soul_campfire'):
        return (0, 0, 0, 1, 0.45, 1)
    if 'potted' in n or n in ('flower_pot', 'decorated_pot'):
        return (0.3, 0, 0.3, 0.7, 0.45, 0.7)
    if n in ('anvil', 'grindstone'):
        return (0.15, 0, 0.15, 0.85, 1, 0.85)
    return (0, 0, 0, 1, 1, 1)


def _opaque(world, x, y, z):
    b = world.blocks.get((x, y, z))
    if b is None:
        return False
    n = b[0].split(':')[-1]
    if n == 'air' or n in ('water', 'lava'):
        return False
    s = shape_of(b[0], dict(b[1]))
    return s == (0, 0, 0, 1, 1, 1) and 'glass' not in n and 'leaves' not in n


def _rotated(world, rot):
    sx, sy, sz = world.size
    out = {}
    for (x, y, z), b in world.blocks.items():
        if b[0].endswith(':air'):
            continue
        for _ in range(rot):
            x, z = (sz - 1 - z), x
        out[(x, y, z)] = b
    return out


class _W:
    def __init__(self, blocks):
        self.blocks = blocks


def iso(world, path, rot=0, scale=4, ground_y=3, crop=None):
    """Isometric render looking from the +x/+z corner (rot rotates the world by 90 degree steps)."""
    blocks = _rotated(world, rot)
    w = _W(blocks)
    sx, sy, sz = world.size
    n = max(sx, sz)
    cw = math.cos(math.radians(30)) * scale
    ch = math.sin(math.radians(30)) * scale
    W = int(2 * n * cw) + 40
    H = int(2 * n * ch + sy * scale) + 40
    ox = n * cw + 20
    oy = sy * scale + 20
    img = Image.new('RGB', (W, H), (150, 190, 230))
    dr = ImageDraw.Draw(img)

    def P(x, y, z):
        return (ox + (x - z) * cw, oy + (x + z) * ch - y * scale)

    # ground plane
    pts = [P(0, ground_y + 1, 0), P(n, ground_y + 1, 0), P(n, ground_y + 1, n), P(0, ground_y + 1, n)]
    dr.polygon(pts, fill=(96, 150, 62))
    order = sorted(blocks.keys(), key=lambda k: (k[0] + k[2], k[1]))
    for (x, y, z) in order:
        name, props, _ = blocks[(x, y, z)]
        props = dict(props)
        sh = shape_of(name, props)
        if sh is None:
            continue
        c = color_of(name)
        if name.endswith('grass_block'):
            top_c, side_c = (96, 156, 64), (134, 96, 67)
        else:
            top_c = side_c = c
        x0, y0, z0, x1, y1, z1 = sh
        full = sh == (0, 0, 0, 1, 1, 1)
        if not (full and _opaque(w, x, y + 1, z)):
            q = [P(x + x0, y + y1, z + z0), P(x + x1, y + y1, z + z0), P(x + x1, y + y1, z + z1), P(x + x0, y + y1, z + z1)]
            dr.polygon(q, fill=tuple(int(v * 1.0) for v in top_c))
        if not (full and _opaque(w, x, y, z + 1)):
            q = [P(x + x0, y + y0, z + z1), P(x + x1, y + y0, z + z1), P(x + x1, y + y1, z + z1), P(x + x0, y + y1, z + z1)]
            dr.polygon(q, fill=tuple(int(v * 0.72) for v in side_c))
        if not (full and _opaque(w, x + 1, y, z)):
            q = [P(x + x1, y + y0, z + z0), P(x + x1, y + y0, z + z1), P(x + x1, y + y1, z + z1), P(x + x1, y + y1, z + z0)]
            dr.polygon(q, fill=tuple(int(v * 0.86) for v in side_c))
    if crop:
        img = img.crop(crop)
    img.save(path)
    return img


def top(world, path, scale=4, ground_y=3):
    sx, sy, sz = world.size
    hm = {}
    for (x, y, z), b in world.blocks.items():
        if b[0].endswith(':air'):
            continue
        if (x, z) not in hm or y > hm[(x, z)][0]:
            hm[(x, z)] = (y, b[0])
    img = Image.new('RGB', (sx * scale, sz * scale), (96, 150, 62))
    dr = ImageDraw.Draw(img)
    for (x, z), (y, name) in hm.items():
        c = color_of(name)
        k = 0.75 + 0.25 * min(1.0, max(0.0, (y - ground_y) / 30.0))
        c = tuple(int(min(255, v * k)) for v in c)
        dr.rectangle([x * scale, z * scale, x * scale + scale - 1, z * scale + scale - 1], fill=c)
    img.save(path)
    return img
