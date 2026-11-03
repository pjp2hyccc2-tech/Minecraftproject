// =====================================================================================
//  ReisatsuShaders - ids from block.properties / entity.properties, material codes,
//  surface response (gloss, metal, wetness) and the wind
//  (item ids live in lib/playerlight.glsl, emitter types in lib/emitters.glsl)
// =====================================================================================

// ---- block.properties (mc_Entity.x) -----------------------------------------------------
// vanilla light sources and nature
#define BLK_FLAME     10001   // torches, candles, campfires (bright texels glow)
#define BLK_LAMP      10002   // lanterns (the glass glows)
#define BLK_GLOW      10003   // glowstone, sea lantern, froglights ... (whole block glows)
#define BLK_LAVA      10004
#define BLK_PLANT     10010   // grass, flowers: top vertices sway
#define BLK_LEAVES    10011   // the hedges (and vanilla leaves): a slight sway
#define BLK_HANGING   10012   // vines: bottom vertices sway
#define BLK_SWAY_SOFT 10013   // bouquets, outdoor planters: the upper part stirs
#define BLK_WATER     10020
#define BLK_GLASS     10021   // vanilla glass, ice

// the restaurant's light sources: BLK_EMIT_BASE + emitter type (lib/emitters.glsl)
#define BLK_EMIT_BASE 11000
#define BLK_EMIT_MAX  11031

// the restaurant's surfaces
#define BLK_ASPHALT     12001   // asphalt, parking / road lines: wet, puddled
#define BLK_PAVER       12002   // sidewalk, curb: wet
#define BLK_PUDDLE      12003   // the 1 px water film: a black mirror
#define BLK_FLOOR_WOOD  12004   // dining floor (vinyl planks): glossy
#define BLK_FLOOR_TILE  12005   // kitchen quarry tiles: glossy, greasy
#define BLK_FLOOR_MOSAIC 12006  // restroom mosaic: glossy
#define BLK_FLOOR_CAVITY 12007  // the cavity behind the mirror: wet, black
#define BLK_GRANITE     12008   // vestibule floor, granite counter tops
#define BLK_STEEL       12009   // stainless: the kitchen line, sinks, tables, hood
#define BLK_METAL_OUT   12010   // painted / galvanised metal outside: guardrails, racks, dumpsters
#define BLK_WINDOW      12011   // shop windows: rain droplets, reflections
#define BLK_MIRROR      12012   // the dark mirror over the women's sinks
#define BLK_MIRROR_CRACKED 12013
#define BLK_MIRROR_PLAIN 12014  // an ordinary mirror (men's restroom)
#define BLK_GLASS_SMALL 12015   // glasses of water, frosted partitions, small glass
#define BLK_WALL_TILE   12016   // glossy white wall tiles
#define BLK_VINYL       12017   // booth vinyl, padded benches: a soft sheen
#define BLK_VARNISH     12018   // varnished wood: tables, counters, wainscot
#define BLK_MUD         12019   // the harvested rice paddies: wet mud
#define BLK_PLASTIC     12020   // laminated menus, call buttons, capsule machines: sheen
#define BLK_ROOF        12021   // glazed roof tiles: wet
#define BLK_AUTODOOR    12022   // the automatic door (block entity: aluminium + glass)
#define BLK_FROST       12023   // the walk-in fridge frost
#define BLK_STEEL_WALL  12024   // the stainless wall behind the cooking line (a wall)
#define BLK_WALL        12025   // plain walls (wallpaper, wainscot, facade, concrete, pillars)

// full wall blocks: they stop the lamps' light (lib/emitters.glsl wall tiles)
bool isWallBlock(int id) { return id == BLK_WALL || id == BLK_STEEL_WALL || id == BLK_WALL_TILE; }

// ---- entity.properties (entityId) -------------------------------------------------------
#define ENT_VISITOR    20001
#define ENT_WAITRESS   20002
#define ENT_CHEF       20003
#define ENT_CHILD      20004
#define ENT_MIRROR     20005
#define ENT_MANAGER    20006
#define ENT_MEDIUM     20010
#define ENT_CAR        20011
#define ENT_LIGHTNING  20020

bool isSpirit(int id) { return id >= ENT_VISITOR && id <= ENT_MANAGER; }

// ---- material codes (colortex5.a * 255): what deferred / composite know of a pixel --------
#define MAT_NONE        0     // sky, the hand, no deferred lighting
#define MAT_DIFFUSE     1
#define MAT_FOLIAGE     2
#define MAT_ASPHALT     3
#define MAT_PAVER       4
#define MAT_FLOOR_WOOD  5
#define MAT_FLOOR_TILE  6
#define MAT_FLOOR_MOSAIC 7
#define MAT_GRANITE     8
#define MAT_STEEL       9
#define MAT_METAL_OUT   10
#define MAT_WINDOW      11
#define MAT_MIRROR      12
#define MAT_MIRROR_PLAIN 13
#define MAT_PUDDLE      14
#define MAT_WALL_TILE   15
#define MAT_EMITTER     16
#define MAT_SPIRIT      17
#define MAT_MUD         18
#define MAT_VINYL       19
#define MAT_VARNISH     20
#define MAT_WET         21    // anything out under the open sky (walls, roofs, props): damp
#define MAT_CAVITY      22
#define MAT_PLASTIC     23
#define MAT_GLASS_SMALL 24
#define MAT_ROOF        25
#define MAT_FROST       26

int materialOfBlock(int id) {
    if (id == BLK_ASPHALT)      return MAT_ASPHALT;
    if (id == BLK_PAVER)        return MAT_PAVER;
    if (id == BLK_PUDDLE || id == BLK_WATER) return MAT_PUDDLE;
    if (id == BLK_FLOOR_WOOD)   return MAT_FLOOR_WOOD;
    if (id == BLK_FLOOR_TILE)   return MAT_FLOOR_TILE;
    if (id == BLK_FLOOR_MOSAIC) return MAT_FLOOR_MOSAIC;
    if (id == BLK_FLOOR_CAVITY) return MAT_CAVITY;
    if (id == BLK_GRANITE)      return MAT_GRANITE;
    if (id == BLK_STEEL || id == BLK_STEEL_WALL) return MAT_STEEL;
    if (id == BLK_METAL_OUT)    return MAT_METAL_OUT;
    if (id == BLK_WINDOW || id == BLK_GLASS || id == BLK_AUTODOOR) return MAT_WINDOW;
    if (id == BLK_MIRROR || id == BLK_MIRROR_CRACKED) return MAT_MIRROR;
    if (id == BLK_MIRROR_PLAIN) return MAT_MIRROR_PLAIN;
    if (id == BLK_GLASS_SMALL)  return MAT_GLASS_SMALL;
    if (id == BLK_WALL_TILE)    return MAT_WALL_TILE;
    if (id == BLK_VINYL)        return MAT_VINYL;
    if (id == BLK_VARNISH)      return MAT_VARNISH;
    if (id == BLK_MUD)          return MAT_MUD;
    if (id == BLK_PLASTIC)      return MAT_PLASTIC;
    if (id == BLK_ROOF)         return MAT_ROOF;
    if (id == BLK_FROST)        return MAT_FROST;
    if (id == BLK_PLANT || id == BLK_LEAVES || id == BLK_HANGING || id == BLK_SWAY_SOFT) return MAT_FOLIAGE;
    if (id > BLK_EMIT_BASE && id <= BLK_EMIT_MAX) return MAT_EMITTER;
    return MAT_DIFFUSE;
}

// surface response of a material: x = roughness (0 mirror .. 1 matte), y = specular F0,
// z = metal (tints the reflection with the albedo), w = how much screen-space reflection
// is worth tracing (0 = none). wet: 0..1 how wet the surface is out under the open sky.
vec4 materialSurface(int m, float wet) {
    vec4 s = vec4(1.0, 0.0, 0.0, 0.0);
#ifndef WET_SURFACES
    wet = 0.0;
#endif
    // outside: rough and dull when dry, glossy black when wet
    if      (m == MAT_ASPHALT)     s = mix(vec4(0.80, 0.025, 0.0, 0.08), vec4(0.22, 0.030, 0.0, 0.90), wet);
    else if (m == MAT_PAVER)       s = mix(vec4(0.85, 0.025, 0.0, 0.05), vec4(0.30, 0.030, 0.0, 0.75), wet);
    else if (m == MAT_MUD)         s = mix(vec4(0.90, 0.020, 0.0, 0.00), vec4(0.26, 0.025, 0.0, 0.70), wet);
    else if (m == MAT_WET)         s = mix(vec4(0.85, 0.025, 0.0, 0.00), vec4(0.40, 0.030, 0.0, 0.40), wet);
    else if (m == MAT_ROOF)        s = mix(vec4(0.55, 0.040, 0.0, 0.15), vec4(0.30, 0.040, 0.0, 0.45), wet);
    else if (m == MAT_METAL_OUT)   s = mix(vec4(0.45, 0.060, 0.2, 0.20), vec4(0.25, 0.060, 0.2, 0.60), wet);
    else if (m == MAT_PUDDLE)      s = vec4(0.03, 0.020, 0.0, 1.00);
    // the restaurant's glossy floors and fittings
    else if (m == MAT_FLOOR_WOOD)  s = vec4(0.30, 0.040, 0.0, 0.75);
    else if (m == MAT_FLOOR_TILE)  s = vec4(0.27, 0.040, 0.0, 0.75);
    else if (m == MAT_FLOOR_MOSAIC) s = vec4(0.20, 0.045, 0.0, 0.80);
    else if (m == MAT_CAVITY)      s = vec4(0.10, 0.030, 0.0, 0.90);
    else if (m == MAT_GRANITE)     s = vec4(0.16, 0.045, 0.0, 0.85);
    else if (m == MAT_STEEL)       s = vec4(0.24, 0.600, 1.0, 0.80);
    else if (m == MAT_WINDOW)      s = vec4(0.03, 0.040, 0.0, 1.00);
    else if (m == MAT_MIRROR)      s = vec4(0.06, 0.400, 0.0, 1.00);   // smoked glass: a dark, neutral mirror
    else if (m == MAT_MIRROR_PLAIN) s = vec4(0.03, 0.850, 0.0, 1.00);
    else if (m == MAT_WALL_TILE)   s = vec4(0.16, 0.045, 0.0, 0.70);
    else if (m == MAT_VINYL)       s = vec4(0.45, 0.040, 0.0, 0.0);
    else if (m == MAT_VARNISH)     s = vec4(0.38, 0.035, 0.0, 0.45);
    else if (m == MAT_PLASTIC)     s = vec4(0.30, 0.045, 0.0, 0.0);
    else if (m == MAT_GLASS_SMALL) s = vec4(0.10, 0.040, 0.0, 0.40);
    else if (m == MAT_FROST)       s = vec4(0.55, 0.030, 0.0, 0.0);
    else if (m == MAT_EMITTER)     s = vec4(0.35, 0.040, 0.0, 0.0);
    else if (m == MAT_SPIRIT)      s = vec4(0.60, 0.030, 0.0, 0.0);
    else if (m == MAT_FOLIAGE)     s = vec4(0.70, 0.025, 0.0, 0.0);
    return s;
}

// a material that darkens when wet (porous): asphalt, pavers, mud, concrete, brick ...
float wetDarkening(int m) {
    if (m == MAT_ASPHALT || m == MAT_PAVER || m == MAT_MUD) return 0.72;
    if (m == MAT_WET || m == MAT_ROOF) return 0.75;
    if (m == MAT_DIFFUSE) return 0.78;
    return 1.0;
}

// ---- emissive masks from texture colour --------------------------------------------------
float flameMask(vec3 albedoSrgb) {
    float l = luma(albedoSrgb);
    float sat = maxOf(albedoSrgb) - minOf(albedoSrgb);
    return smoothstep(0.42, 0.80, l + sat * 0.45);
}
float paperMask(vec3 albedoSrgb) {
    return smoothstep(0.30, 0.62, luma(albedoSrgb));
}

// ---- wind ------------------------------------------------------------------------------
#if defined VERTEX_SHADER && defined WAVING_PLANTS
// After the rain the air is almost still: slow, faint swells
float windSwell(vec3 wp) {
#ifdef WORLD_HIDAMARI
    float s = vnoise1(frameTimeCounter * 0.11 + wp.x * 0.02 + wp.z * 0.015);
    return 0.35 + 0.65 * smoothstep(0.30, 0.90, s);
#else
    return 1.0 + rainAmount() * 1.2;
#endif
}

vec3 windSway(vec3 wp, float amp) {
    float t = frameTimeCounter;
    amp *= windSwell(wp);
    float a = sin(t * 1.10 + wp.x * 0.60 + wp.z * 0.40) * 0.55 + sin(t * 2.10 + wp.z * 1.20 - wp.x * 0.80) * 0.30 + 0.35;
    float b = sin(t * 0.90 - wp.z * 0.55 + wp.x * 0.30) * 0.60 + sin(t * 2.60 - wp.x * 1.10 + wp.y * 0.70) * 0.40;
    return vec3(b * 0.6, 0.0, a) * amp;
}

// wp: world position of the vertex, uv / midUv: atlas coordinates (top / bottom test),
// hInBlock: height of the vertex inside its block 0..1 (at_midBlock)
vec3 wavingOffset(int id, vec3 wp, vec2 uv, vec2 midUv, float skyLight, float hInBlock) {
    float exposed = 0.15 + 0.85 * skyLight;
#ifdef WORLD_HIDAMARI
    const float K = 0.45;          // still air after the rain: "very slight"
#else
    const float K = 1.0;
#endif
    if (id == BLK_PLANT) {
        return uv.y < midUv.y ? windSway(wp, 0.06 * exposed * K) : vec3(0.0);
    }
    if (id == BLK_LEAVES) {
        return windSway(wp, 0.035 * exposed * K);
    }
    if (id == BLK_HANGING) {
        return uv.y > midUv.y ? windSway(wp, 0.05 * K) : vec3(0.0);
    }
    if (id == BLK_SWAY_SOFT) {
        return windSway(wp, 0.035 * exposed * K * smoothstep(0.2, 1.0, hInBlock));
    }
    return vec3(0.0);
}
#endif
