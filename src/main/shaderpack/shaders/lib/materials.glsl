// =====================================================================================
//  HitoriShaders - ids from block.properties / entity.properties, material codes,
//  surface response (gloss, metal, wetness) and the wind
//  (item ids live in lib/playerlight.glsl, emitter types in lib/emitters.glsl)
// =====================================================================================

// ---- block.properties (mc_Entity.x) -----------------------------------------------------
// vanilla light sources and nature
#define BLK_FLAME     10001   // torches, campfires, fire - the burning doll (bright texels glow)
#define BLK_LAMP      10002   // lanterns (the glass glows)
#define BLK_GLOW      10003   // glowstone, sea lantern, froglights ... (whole block glows)
#define BLK_LAVA      10004
#define BLK_CANDLE    10005   // candles: only the flame on the wick glows, not the white wax
#define BLK_PLANT     10010   // grass, flowers: top vertices sway
#define BLK_LEAVES    10011   // hedges, leaves: a slight sway
#define BLK_HANGING   10012   // vines: bottom vertices sway
#define BLK_SWAY_SOFT 10013   // potted plants on the balcony: the upper part stirs
#define BLK_WATER     10020
#define BLK_GLASS     10021   // vanilla glass, ice

// the apartment's light sources: BLK_EMIT_BASE + emitter type (lib/emitters.glsl)
#define BLK_EMIT_BASE 11000
#define BLK_EMIT_MAX  11031

// the apartment's and the street's surfaces
#define BLK_ASPHALT      12001   // the wet street: puddled, glossy
#define BLK_CONCRETE     12002   // corridor, concrete, utility poles out in the rain: wet
#define BLK_PUDDLE       12003   // the 1 px water film: a black mirror with rain rings
#define BLK_FLOOR_VARNISH 12004  // old varnished flooring: soft reflections
#define BLK_VINYL_FLOOR  12005   // the kitchen's cushion floor: a faint sheen
#define BLK_GENKAN_TILE  12006   // small dark tiles in the genkan: glossy
#define BLK_BATH_TILE    12007   // the bathroom's pale blue tiles: wet gloss (floor)
#define BLK_BATH_WALL    12008   // the bathroom's tiles on the walls: wet gloss, a wall
#define BLK_TATAMI       12009   // tatami: matte rush, a faint sheen along the weave
#define BLK_WALL         12010   // plaster, wallpaper, wainscot: walls (they stop the lamps' light)
#define BLK_CEILING      12011   // the old ceiling boards
#define BLK_OSHIIRE_FLOOR 12012  // the closet's bare plywood floor (marks the closet: emitter map tile 8)
#define BLK_SIDING       12013   // the building's corrugated siding: wet painted metal, a wall
#define BLK_METAL_OUT    12014   // painted / galvanised metal out in the rain: steel doors, rails, mailboxes
#define BLK_BLOCK_WALL   12015   // the concrete block garden wall: wet, a wall
#define BLK_ROOF         12016   // slate roof: wet
#define BLK_RAIN_GLASS   12017   // windows and the balcony doors: rain on the glass, reflections
#define BLK_FROSTED      12018   // frosted glass: the bathroom's folding door, small windows
#define BLK_MIRROR       12019   // the mirrors over the washbasin and in the hall
#define BLK_GLASS_SMALL  12020   // the cup, glass jars
#define BLK_VARNISH      12021   // varnished furniture: the low table, the desk, shelves
#define BLK_PLASTIC      12022   // plastic: switches, the laptop, the bucket, the stool
#define BLK_PORCELAIN    12023   // the toilet, the washbasin, the bathtub's enamel
#define BLK_ENAMEL       12024   // enamelled steel: the old fridge, the washing machine, the stove
#define BLK_STEEL        12025   // stainless: the kitchen sink, the knife rack
#define BLK_CHROME       12026   // chrome: taps, the shower
#define BLK_FABRIC       12027   // futon, cushions, the bed, the camera bag
#define BLK_PAPER        12028   // fusuma (open), the printout, posters, the calendar
#define BLK_CURTAIN_CLOSED 12029 // curtains drawn: the street lamp glows through their folds (tile 7)
#define BLK_CURTAIN_OPEN 12030
#define BLK_SHUT         12031   // closed doors and fusuma: a wall for the lamps' light (walls tiles)
#define BLK_BATH_WATER   12032   // the bathtub filled (water 1 / 2): dark still water
#define BLK_CRT_OFF      12033   // the TV switched off: dark curved glass
#define BLK_SHUT_PAPER   12035   // a shut fusuma (closet or room): a wall of paper and wood
#define BLK_INDICATOR    12036   // blocks with a small indicator light (breaker panel, rice cooker ...)
#define BLK_DARK_LED     12037   // an indicator switched off (the breaker off): a full-bright face that does not glow

// full wall blocks: they stop the lamps' light (lib/emitters.glsl wall tiles at y 70 / 71)
// the world's two-sided walls (block.properties: 12100 + facing * 64 + inner * 8 + outer): one
// block between two different rooms, or a room and the outside
#define BLK_TWO_SIDED    12100
bool isTwoSidedWall(int id) {
    return id >= BLK_TWO_SIDED && id < BLK_TWO_SIDED + 256;
}
// the plain block a two-sided wall shows on the face with world normal N: the face toward its
// FACING has the inner texture, all the others the outer one. Faces (the world's Props.Face):
// plaster, stained plaster, wallpaper, wainscot, bath tiles, closet plywood, siding, concrete
int twoSidedFace(int id, vec3 N) {
    int c = clamp(id - BLK_TWO_SIDED, 0, 255);
    int f = c >> 6;
    vec3 dir = f == 0 ? vec3(0.0, 0.0, -1.0) : (f == 1 ? vec3(1.0, 0.0, 0.0) : (f == 2 ? vec3(0.0, 0.0, 1.0) : vec3(-1.0, 0.0, 0.0)));
    int kind = dot(N, dir) > 0.5 ? ((c >> 3) & 7) : (c & 7);
    if (kind == 4) return BLK_BATH_WALL;
    if (kind == 6) return BLK_SIDING;
    if (kind == 7) return BLK_CONCRETE;
    return BLK_WALL;
}

bool isWallBlock(int id) {
    return id == BLK_WALL || id == BLK_BATH_WALL || id == BLK_SIDING || id == BLK_BLOCK_WALL
        || id == BLK_SHUT || id == BLK_SHUT_PAPER || isTwoSidedWall(id);
}

// ---- entity.properties (entityId) -------------------------------------------------------
#define ENT_DOLL       30001   // the oni: the soaked plush rabbit with the knife
#define ENT_WOMAN      30002   // camera-only apparition
#define ENT_CAMERA     30003   // a fixed camera (its red REC LED: eyes layer)
#define ENT_LIGHTNING  30020

// ---- material codes (colortex5.a * 255): what deferred / composite know of a pixel --------
#define MAT_NONE        0     // sky, the hand, no deferred lighting
#define MAT_DIFFUSE     1
#define MAT_FOLIAGE     2
#define MAT_ASPHALT     3
#define MAT_CONCRETE    4
#define MAT_FLOOR_VARNISH 5
#define MAT_VINYL_FLOOR 6
#define MAT_GENKAN_TILE 7
#define MAT_BATH_TILE   8
#define MAT_STEEL       9
#define MAT_METAL_OUT   10
#define MAT_GLASS_RAIN  11
#define MAT_MIRROR      12
#define MAT_FROSTED     13
#define MAT_PUDDLE      14
#define MAT_PORCELAIN   15
#define MAT_EMITTER     16
#define MAT_DOLL        17
#define MAT_TATAMI      18
#define MAT_VARNISH     19
#define MAT_WOMAN       20
#define MAT_WET         21    // anything out under the open sky (walls, roofs, props): damp
#define MAT_CHROME      22
#define MAT_PLASTIC     23
#define MAT_GLASS_SMALL 24
#define MAT_ROOF        25
#define MAT_WATER       26    // the bath water
#define MAT_CRT_GLASS   27
#define MAT_CURTAIN     28
#define MAT_PAPER       29
#define MAT_ENAMEL      30
#define MAT_FABRIC      31
#define MAT_WALL        32

int materialOfBlock(int id) {
    if (id == BLK_ASPHALT)       return MAT_ASPHALT;
    if (id == BLK_CONCRETE || id == BLK_BLOCK_WALL) return MAT_CONCRETE;
    if (id == BLK_PUDDLE || id == BLK_WATER) return MAT_PUDDLE;
    if (id == BLK_FLOOR_VARNISH) return MAT_FLOOR_VARNISH;
    if (id == BLK_VINYL_FLOOR)   return MAT_VINYL_FLOOR;
    if (id == BLK_GENKAN_TILE)   return MAT_GENKAN_TILE;
    if (id == BLK_BATH_TILE || id == BLK_BATH_WALL) return MAT_BATH_TILE;
    if (id == BLK_TATAMI)        return MAT_TATAMI;
    if (id == BLK_WALL || id == BLK_CEILING || id == BLK_OSHIIRE_FLOOR || id == BLK_SHUT) return MAT_WALL;
    if (id == BLK_SIDING || id == BLK_METAL_OUT) return MAT_METAL_OUT;
    if (id == BLK_ROOF)          return MAT_ROOF;
    if (id == BLK_RAIN_GLASS || id == BLK_GLASS) return MAT_GLASS_RAIN;
    if (id == BLK_FROSTED)       return MAT_FROSTED;
    if (id == BLK_MIRROR)        return MAT_MIRROR;
    if (id == BLK_GLASS_SMALL)   return MAT_GLASS_SMALL;
    if (id == BLK_VARNISH)       return MAT_VARNISH;
    if (id == BLK_PLASTIC || id == BLK_INDICATOR || id == BLK_DARK_LED) return MAT_PLASTIC;
    if (id == BLK_PORCELAIN)     return MAT_PORCELAIN;
    if (id == BLK_ENAMEL)        return MAT_ENAMEL;
    if (id == BLK_STEEL)         return MAT_STEEL;
    if (id == BLK_CHROME)        return MAT_CHROME;
    if (id == BLK_FABRIC)        return MAT_FABRIC;
    if (id == BLK_PAPER || id == BLK_SHUT_PAPER) return MAT_PAPER;
    if (id == BLK_CURTAIN_CLOSED || id == BLK_CURTAIN_OPEN) return MAT_CURTAIN;
    if (id == BLK_BATH_WATER)    return MAT_PORCELAIN;   // the tub; its water surface is found in gbuffers
    if (id == BLK_CRT_OFF)       return MAT_CRT_GLASS;
    if (id == BLK_PLANT || id == BLK_LEAVES || id == BLK_HANGING || id == BLK_SWAY_SOFT) return MAT_FOLIAGE;
    if (id > BLK_EMIT_BASE && id <= BLK_EMIT_MAX) return MAT_EMITTER;
    return MAT_DIFFUSE;
}

// surface response of a material: x = roughness (0 mirror .. 1 matte), y = specular F0,
// z = metal (tints the reflection with the albedo), w = how much screen-space reflection
// is worth tracing (0 = none). wet: 0..1 how wet the surface is out in the rain.
vec4 materialSurface(int m, float wet) {
    vec4 s = vec4(1.0, 0.0, 0.0, 0.0);
#ifndef WET_SURFACES
    wet = 0.0;
#endif
    // outside: rough and dull when dry, glossy black in the rain
    if      (m == MAT_ASPHALT)     s = mix(vec4(0.80, 0.025, 0.0, 0.08), vec4(0.20, 0.030, 0.0, 0.92), wet);
    else if (m == MAT_CONCRETE)    s = mix(vec4(0.85, 0.025, 0.0, 0.05), vec4(0.30, 0.030, 0.0, 0.75), wet);
    else if (m == MAT_WET)         s = mix(vec4(0.85, 0.025, 0.0, 0.00), vec4(0.40, 0.030, 0.0, 0.40), wet);
    else if (m == MAT_ROOF)        s = mix(vec4(0.55, 0.040, 0.0, 0.15), vec4(0.28, 0.040, 0.0, 0.50), wet);
    else if (m == MAT_METAL_OUT)   s = mix(vec4(0.45, 0.060, 0.2, 0.20), vec4(0.22, 0.060, 0.2, 0.65), wet);
    else if (m == MAT_PUDDLE)      s = vec4(0.03, 0.020, 0.0, 1.00);
    // the apartment: matte tatami and walls, soft old varnish, wet bathroom tiles
    else if (m == MAT_TATAMI)      s = vec4(0.62, 0.035, 0.0, 0.10);
    else if (m == MAT_FLOOR_VARNISH) s = vec4(0.30, 0.045, 0.0, 0.70);
    else if (m == MAT_VINYL_FLOOR) s = vec4(0.40, 0.040, 0.0, 0.40);
    else if (m == MAT_GENKAN_TILE) s = vec4(0.18, 0.045, 0.0, 0.80);
    else if (m == MAT_BATH_TILE)   s = vec4(0.10, 0.050, 0.0, 0.85);
    else if (m == MAT_PORCELAIN)   s = vec4(0.10, 0.050, 0.0, 0.65);
    else if (m == MAT_ENAMEL)      s = vec4(0.22, 0.050, 0.0, 0.45);
    else if (m == MAT_STEEL)       s = vec4(0.24, 0.600, 1.0, 0.80);
    else if (m == MAT_CHROME)      s = vec4(0.08, 0.800, 1.0, 0.85);
    else if (m == MAT_WATER)       s = vec4(0.02, 0.020, 0.0, 1.00);
    else if (m == MAT_CRT_GLASS)   s = vec4(0.06, 0.050, 0.0, 0.90);
    else if (m == MAT_MIRROR)      s = vec4(0.03, 0.850, 0.0, 1.00);
    else if (m == MAT_GLASS_RAIN)  s = vec4(0.03, 0.040, 0.0, 1.00);
    else if (m == MAT_FROSTED)     s = vec4(0.45, 0.040, 0.0, 0.25);
    else if (m == MAT_VARNISH)     s = vec4(0.38, 0.035, 0.0, 0.40);
    else if (m == MAT_PLASTIC)     s = vec4(0.32, 0.045, 0.0, 0.0);
    else if (m == MAT_GLASS_SMALL) s = vec4(0.10, 0.040, 0.0, 0.40);
    else if (m == MAT_EMITTER)     s = vec4(0.35, 0.040, 0.0, 0.0);
    else if (m == MAT_DOLL)        s = vec4(0.55, 0.030, 0.0, 0.0);   // soaked plush: a dull wet sheen
    else if (m == MAT_FOLIAGE)     s = vec4(0.70, 0.025, 0.0, 0.0);
    return s;
}

// a material that darkens when wet (porous): asphalt, concrete, block walls ...
float wetDarkening(int m) {
    if (m == MAT_ASPHALT || m == MAT_CONCRETE) return 0.70;
    if (m == MAT_WET || m == MAT_ROOF) return 0.75;
    if (m == MAT_DIFFUSE || m == MAT_WALL) return 0.78;
    return 1.0;
}

// ---- emissive masks from texture colour --------------------------------------------------
float flameMask(vec3 albedoSrgb) {
    float l = luma(albedoSrgb);
    float sat = maxOf(albedoSrgb) - minOf(albedoSrgb);
    return smoothstep(0.42, 0.80, l + sat * 0.45);
}
// a candle: the warm, saturated wick flame - the wax is white and must stay wax
float candleMask(vec3 albedoSrgb) {
    float sat = maxOf(albedoSrgb) - minOf(albedoSrgb);
    return smoothstep(0.45, 0.70, maxOf(albedoSrgb)) * smoothstep(0.30, 0.50, sat) * step(albedoSrgb.b, albedoSrgb.r);
}
float paperMask(vec3 albedoSrgb) {
    return smoothstep(0.30, 0.62, luma(albedoSrgb));
}

// ---- wind ------------------------------------------------------------------------------
#if defined VERTEX_SHADER && defined WAVING_PLANTS
// a wet night with a little wind in the rain: slow swells
float windSwell(vec3 wp) {
#ifdef WORLD_SHIRAKABA
    float s = vnoise1(frameTimeCounter * 0.13 + wp.x * 0.02 + wp.z * 0.015);
    return 0.45 + 0.75 * smoothstep(0.30, 0.90, s);
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
#ifdef WORLD_SHIRAKABA
    const float K = 0.60;          // a little wind in the rain
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
