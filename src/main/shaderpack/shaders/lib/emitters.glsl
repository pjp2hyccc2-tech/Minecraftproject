// =====================================================================================
//  HitoriShaders - the restaurant's light sources (hitori:hidamari)
//
//  Vanilla only knows one light LEVEL per block. The pack finds out WHICH lamp lights a
//  surface - and so its colour, direction and flicker - like this:
//
//  1. shadow pass (program/shadow.glsl): the sky is overcast, there is no moon to cast
//     shadows, so the shadow map becomes a top-down EMITTER MAP around the camera: 4 x 4
//     tiles of 128 x 128 texels, one texel per block column. Tiles 0..3 hold the lit lamps
//     (block.properties ids 11001.., see EM_*) of 4 height layers, every face of a lamp
//     block snapped onto the block's footprint (at_midBlock); tile 4 holds the height of the
//     topmost surface of every column (roof / sky cover); tiles 5 / 6 the full wall blocks at
//     y 66 / 67 (a column is a wall where both are set: doorways stay open); tile 7 the
//     lowest window blind of each column (the light from the sign outside falls through slats).
//  2. prepare  (TABLE_Z): per column, the best 4 lamps along z within reach (separable
//     Manhattan distance - exactly how vanilla light spreads)
//     prepare1 (TABLE_X): the same along x  ->  colortex7: the 4 lamps that can light each
//     block column, packed into one RGBA32F texel (24-bit integers in floats).
//  3. every lit program reads its column's 4 lamps: direct light with inverse-square falloff,
//     emission pattern, N.L, flicker and GGX highlights, gated by the vanilla light level
//     (a lamp whose light would have to be brighter here than it is must be behind a wall),
//     plus a coloured bounce of the vanilla level for the light that went round corners.
//     The fog march does the same per step (analytic in-scatter integrals).
// =====================================================================================

// ---- emitter types (block.properties: 11000 + type) -------------------------------------
#define EM_NONE         0
#define EM_FLUO         1    // ceiling_light lit: cold white-green fluorescent panel
#define EM_FLUO_BROKEN  2    // ceiling_light lit, broken: the same, stuttering
#define EM_DOWN         3    // downlight: warm
#define EM_PENDANT      4    // pendant lamp over the booths: warm glass shade
#define EM_EXIT         5    // exit sign: green
#define EM_VENDING      6    // vending machine front: white
#define EM_POLE         7    // the pole sign by the road: warm orange box sign, flickering
#define EM_ROOFSIGN     8    // the roof letters
#define EM_SODIUM       9    // sodium street lamp
#define EM_TRAFFIC      10   // the blinking yellow traffic light
#define EM_DRINK        11   // drink bar standby panel
#define EM_CAKE         12   // cake case: cold white
#define EM_HEAT         13   // the pass heat lamp: red-orange
#define EM_GAS          14   // gas range flames: blue
#define EM_CRT          15   // CRT TV static
#define EM_MONITOR      16   // security monitors
#define EM_ALERT        17   // security monitors on alert: red REC frame
#define EM_PC           18   // the office PC screen
#define EM_CALLDISP     19   // the call display's red LEDs
#define EM_LED          20   // a tiny red LED (call buttons, camera "seen"): glows, casts nothing
#define EM_SCREEN       21   // a dim screen (the register): glows, casts nothing
#define EM_FLAME        22   // vanilla torches, candles, campfires
#define EM_LANTERN      23   // vanilla lanterns
#define EM_GLOWBLOCK    24   // glowstone, sea lantern ...
#define EM_COUNT        25

// colour of the light (linear, roughly unit luminance-ish)
const vec3 EM_COLOR[EM_COUNT] = vec3[EM_COUNT](
    vec3(0.0),
    vec3(0.74, 0.97, 0.88),     // fluorescent: cold white with the green spike
    vec3(0.74, 0.97, 0.88),
    vec3(1.00, 0.68, 0.40),     // downlight ~2700 K
    vec3(1.00, 0.63, 0.34),     // pendant: amber glass
    vec3(0.16, 1.00, 0.42),     // exit sign green
    vec3(0.82, 0.92, 1.00),     // vending machine: cold white
    vec3(1.00, 0.60, 0.26),     // the pole sign's warm orange
    vec3(1.00, 0.66, 0.36),     // roof sign
    vec3(1.00, 0.50, 0.14),     // sodium
    vec3(1.00, 0.70, 0.08),     // amber traffic light
    vec3(0.52, 0.80, 1.00),     // drink bar: cyan-white standby glow
    vec3(0.84, 0.93, 1.00),     // cake case: cold white
    vec3(1.00, 0.28, 0.08),     // heat lamp
    vec3(0.22, 0.42, 1.00),     // gas flames
    vec3(0.66, 0.78, 1.00),     // CRT static
    vec3(0.58, 0.74, 1.00),     // monitors
    vec3(1.00, 0.16, 0.10),     // monitors on alert
    vec3(0.62, 0.78, 1.00),     // PC
    vec3(1.00, 0.10, 0.04),     // call display LEDs
    vec3(1.00, 0.06, 0.03),     // LED
    vec3(0.60, 0.85, 0.75),     // register screen
    vec3(1.00, 0.58, 0.28),     // flames
    vec3(1.00, 0.62, 0.32),     // lanterns
    vec3(1.00, 0.80, 0.55));    // glowing blocks

// intensity I of the light cast: irradiance E = I / (d^2 + size^2) (before LIGHT_STRENGTH).
// Roughly photometric ratios: a sodium street lamp (I 15) lights the lot 7 blocks below as
// much as a fluorescent panel (12) a dining table 4 blocks below; an exit sign (0.4) is a dim
// green pool, the standby glow of the drink bar (0.45) a pale cyan wash.
const float EM_GAIN[EM_COUNT] = float[EM_COUNT](
    0.0, 12.0, 12.0, 8.0, 4.5, 0.40, 3.0, 9.0, 14.0, 50.0, 2.5, 0.45, 0.80, 1.6, 0.50, 0.35,
    0.30, 0.35, 0.22, 0.15, 0.0, 0.0, 1.2, 1.6, 2.5);

// radius of the glowing part: the soft core of the falloff, widens the highlights
const float EM_SIZE[EM_COUNT] = float[EM_COUNT](
    0.5, 0.60, 0.60, 0.35, 0.35, 0.35, 0.80, 0.70, 0.60, 0.40, 0.25, 0.60, 0.50, 0.30, 0.30, 0.35,
    0.45, 0.45, 0.30, 0.40, 0.05, 0.10, 0.15, 0.20, 0.50);

// where the light leaves the block, relative to the block centre (y): ceiling panels and
// downlights shine from their bottom face, a street lamp head from below its housing
const float EM_YOFF[EM_COUNT] = float[EM_COUNT](
    0.0, -0.46, -0.46, -0.46, -0.10, 0.0, 0.0, 0.0, 0.0, -0.30, 0.0, 0.0, 0.10, 0.0, -0.30, 0.0,
    0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0);

// emission pattern: 0 omni, 1 ceiling panel / downlight (cosine downwards, nothing above),
// 2 lamp shade (mostly down, a little spill up), 3 street lamp (down-wide), 4 flat glow
const int EM_PATTERN[EM_COUNT] = int[EM_COUNT](
    0, 1, 1, 1, 2, 0, 0, 0, 0, 3, 0, 0, 0, 2, 0, 0,
    0, 0, 0, 0, 0, 0, 0, 0, 0);

// how bright the lamp's own glowing texels are (surface emission, x texture colour)
const float EM_SURF[EM_COUNT] = float[EM_COUNT](
    0.0, 3.2, 3.2, 3.6, 3.0, 2.6, 2.0, 3.2, 3.0, 5.0, 4.0, 1.7, 1.2, 3.0, 3.0, 1.6,
    1.3, 1.6, 1.4, 2.6, 2.2, 0.9, 3.2, 1.8, 1.6);

// vanilla light levels of the design (used when the shadow pass cannot measure them)
const float EM_LEVEL[EM_COUNT] = float[EM_COUNT](
    0.0, 13.0, 13.0, 11.0, 12.0, 7.0, 12.0, 15.0, 12.0, 14.0, 8.0, 6.0, 9.0, 8.0, 9.0, 5.0,
    5.0, 5.0, 4.0, 4.0, 0.0, 0.0, 14.0, 15.0, 15.0);

bool emitterCasts(int type) { return type > EM_NONE && type < EM_COUNT && type != EM_LED && type != EM_SCREEN; }
int emitterTypeOfBlock(int id) {
    if (id > BLK_EMIT_BASE && id <= BLK_EMIT_MAX) return id - BLK_EMIT_BASE;
    if (id == BLK_FLAME) return EM_FLAME;
    if (id == BLK_LAMP)  return EM_LANTERN;
    if (id == BLK_GLOW)  return EM_GLOWBLOCK;
    return EM_NONE;
}
// lamps that stand outside (their light belongs to the open air, the mist over the lot)
bool emitterOutdoorType(int type) {
    return type == EM_POLE || type == EM_ROOFSIGN || type == EM_SODIUM || type == EM_TRAFFIC || type == EM_VENDING;
}

// ---- flicker ---------------------------------------------------------------------------
// every lamp computes the same value for its own glowing surface (gbuffers) and for the
// light it casts (table), from its block position
float emitterFlicker(int type, vec3 blockPos, float t) {
    float h = hash13(floor(blockPos) * vec3(0.731, 1.37, 0.513) + 0.17);
#ifdef LIGHT_FLICKER
    if (type == EM_FLUO_BROKEN) {
        // the world's ticker switches a broken tube on and off (lit); while it is on, the tube
        // itself is unstable: a fast strobing shimmer, the brightness hunting, now and then a
        // sag where only the glowing ends are left
        float hunt = vnoise1(t * 3.7 + h * 31.0);
        float strobe = 0.5 + 0.5 * sin(t * TAU * (13.0 + 6.0 * h) + vnoise1(t * 2.3) * 6.0);
        float slot = floor(t * 9.0);
        float sag = step(0.86, hash11(slot * 1.31 + h * 97.0)) * (0.5 + 0.3 * hash11(slot + 3.0));
        float f = (0.80 + 0.20 * hunt) * (0.90 + 0.10 * strobe) * (1.0 - sag);
      #ifdef FLASH_REDUCTION
        f = mix(0.85, f, 0.35);
      #endif
        return f;
    }
    if (type == EM_FLUO) {
        // healthy tubes: a faint shimmer, a rare tiny dip
        float dip = step(0.985, hash11(floor(t * 7.0) * 1.7 + h * 51.0)) * 0.25;
        return 1.0 - 0.025 * vnoise1(t * 13.0 + h * 40.0) - dip;
    }
    if (type == EM_POLE || type == EM_ROOFSIGN) {
        // old box sign: the ticker makes its failing row stutter; here only the tubes' buzz
        return 1.0 - 0.05 * vnoise1(t * 17.0 + h * 5.0) - 0.03 * vnoise1(t * 41.0 + h * 13.0);
    }
    if (type == EM_TRAFFIC) {
        // the ticker blinks it (lit on / off every 15 ticks): steady while on
        return 1.0;
    }
    if (type == EM_SODIUM) {
        // one lamp in five is dying: now and then it fades out, glows red-dim, restrikes
        if (h > 0.80) {
            float c = fract(t / 47.0 + h * 3.0);
            float out_ = smoothstep(0.62, 0.70, c) * (1.0 - smoothstep(0.86, 0.97, c));
            return 1.0 - 0.88 * out_;
        }
        return 1.0;
    }
    if (type == EM_CRT) {
        return 0.70 + 0.30 * vnoise1(t * 21.0 + h * 13.0) + 0.10 * (hash11(floor(t * 30.0) + h) - 0.5);
    }
    if (type == EM_GAS || type == EM_FLAME || type == EM_LANTERN) {
        float n = vnoise1(t * 7.3 + h * 37.0) * 0.6 + vnoise1(t * 17.1 + h * 11.0) * 0.4;
        return 0.86 + 0.24 * n;
    }
    if (type == EM_MONITOR || type == EM_PC || type == EM_ALERT) {
        float a = type == EM_ALERT ? 0.55 + 0.45 * step(0.5, fract(t * 0.8)) : 1.0;
        return a * (0.94 + 0.06 * vnoise1(t * 9.0 + h * 21.0));
    }
    if (type == EM_VENDING) {
        return 1.0 - 0.04 * vnoise1(t * 11.0 + h * 9.0);
    }
#endif
    return 1.0;
}

// danger makes the restaurant's tubes stutter (the spirits are near)
float dangerStutter(int type, vec3 blockPos, float t) {
#ifdef LIGHT_FLICKER
    float D = smoothstep(0.45, 1.0, danger());
    if (D <= 0.0 || !(type == EM_FLUO || type == EM_FLUO_BROKEN || type == EM_DOWN || type == EM_PENDANT)) return 1.0;
    float h = hash13(floor(blockPos) + 3.7);
    float slot = floor(t * 14.0);
    float drop = step(1.0 - 0.35 * D, hash11(slot * 1.91 + h * 71.0));
  #ifdef FLASH_REDUCTION
    drop *= 0.3;
  #endif
    return 1.0 - 0.75 * drop;
#else
    return 1.0;
#endif
}

// ---- the glowing surface of a lamp (gbuffers) -----------------------------------------------
// which texels of the lamp's texture glow. albedoSrgb: texture colour, N: world normal
float emitterSurfaceMask(int type, vec3 albedoSrgb, vec3 N) {
    float l = luma(albedoSrgb);
    float c = chroma(albedoSrgb);
    if (type == EM_FLUO || type == EM_FLUO_BROKEN || type == EM_DOWN) {
        // only the panel / lens on the bottom face
        return smoothstep(-0.4, -0.6, N.y) * smoothstep(0.30, 0.62, l);
    }
    if (type == EM_EXIT || type == EM_CALLDISP || type == EM_LED || type == EM_TRAFFIC || type == EM_GAS || type == EM_HEAT) {
        // coloured lamps: the saturated (or white-hot) texels
        return smoothstep(0.42, 0.75, l + c * 0.55) * smoothstep(0.08, 0.22, maxOf(albedoSrgb));
    }
    if (type == EM_CRT || type == EM_MONITOR || type == EM_ALERT || type == EM_PC || type == EM_SCREEN) {
        return smoothstep(0.22, 0.50, l + c * 0.3);
    }
    // signs, panels, shades, cases: the bright texels
    return smoothstep(0.38, 0.72, l + c * 0.30);
}

// ---- shadow pass encoding (shadowcolor0, RGBA8; alpha is always 1, so neither an injected
//  alpha test nor blending of translucent geometry can change a value) -------------------------
//  lamp tiles : r = type / 255, g = (block y - 32) / 255, b = (128 + measured level) / 255
//  cover tile : r / g = height of the topmost surface in 1/16 blocks (hi / lo byte), b = 64 / 255
//  wall tiles : b = 32 / 255
//  blind tile : r = closed, g = (block y - 32) / 255 of the lowest blind, b = 16 / 255
const int EMAP_TILE = 128;
const int EMAP_COVER = 4;          // tile index of the cover heights
const int EMAP_WALL_LO = 5;        // full wall blocks at y 66
const int EMAP_WALL_HI = 6;        // full wall blocks at y 67
const int EMAP_WALL_Y = 66;
const int EMAP_BLIND = 7;          // the lowest window blind of each column

ivec2 emapOrigin() { return ivec2(floor(cameraPosition.xz)) - ivec2(EMAP_TILE / 2); }
ivec2 emapTileOffset(int tile) { return ivec2(tile % 4, tile / 4) * EMAP_TILE; }
// height layers of the lamp tiles: floor props / wall and hanging lamps / the ceiling / above
int emapLayer(int blockY) {
    if (blockY <= 66) return 0;
    if (blockY <= 68) return 1;
    if (blockY == 69) return 2;
    return 3;
}
bool emapLampMarker(vec4 t) { float b = floor(t.b * 255.0 + 0.5); return b >= 128.0 && b <= 143.0 && t.a > 0.5; }
bool emapCoverMarker(vec4 t) { return abs(t.b * 255.0 - 64.0) < 0.5 && t.a > 0.5; }
bool emapWallMarker(vec4 t) { return abs(t.b * 255.0 - 32.0) < 0.5 && t.a > 0.5; }
bool emapBlindMarker(vec4 t) { return abs(t.b * 255.0 - 16.0) < 0.5 && t.a > 0.5; }
int emapLampLevel(vec4 t) { return int(floor(t.b * 255.0 + 0.5)) - 128; }
float emapCoverHeight(vec4 t) { return (floor(t.r * 255.0 + 0.5) * 256.0 + floor(t.g * 255.0 + 0.5)) / 16.0; }

// height of the topmost surface of the block column (world xz), -1e3 if unknown
float coverHeight(ivec2 column) {
    ivec2 c = column - emapOrigin();
    if (c.x < 0 || c.y < 0 || c.x >= EMAP_TILE || c.y >= EMAP_TILE) return -1e3;
    vec4 t = texelFetch(shadowcolor0, emapTileOffset(EMAP_COVER) + c, 0);
    if (!emapCoverMarker(t)) return -1e3;
    return emapCoverHeight(t);
}
// 1 = under the open sky, 0 = something above (roof, canopy, ceiling)
float openSky(vec3 worldPos) {
    float h = coverHeight(ivec2(floor(worldPos.xz)));
    return h < -999.0 ? 1.0 : smoothstep(h - 0.05, h + 0.25, worldPos.y);
}

// a wall stands in this column (map coordinates): full blocks at y 66 and 67
bool wallColumn(ivec2 c) {
    if (c.x < 0 || c.y < 0 || c.x >= EMAP_TILE || c.y >= EMAP_TILE) return false;
    return emapWallMarker(texelFetch(shadowcolor0, emapTileOffset(EMAP_WALL_LO) + c, 0))
        && emapWallMarker(texelFetch(shadowcolor0, emapTileOffset(EMAP_WALL_HI) + c, 0));
}
// walk the columns between two (map coordinates): is there a wall in between?
bool wallBetween(ivec2 a, ivec2 b) {
    if (a == b) return false;
    vec2 p = vec2(a) + 0.5;
    vec2 d = vec2(b - a);
    vec2 stp = sign(d);
    vec2 inv = 1.0 / max(abs(d), vec2(1e-5));
    vec2 tMax = (0.5) * inv;                 // from a cell centre to its border: half a cell
    vec2 tDelta = inv;
    ivec2 c = a;
    for (int i = 0; i < 32; i++) {
        if (tMax.x < tMax.y) { c.x += int(stp.x); tMax.x += tDelta.x; }
        else { c.y += int(stp.y); tMax.y += tDelta.y; }
        if (c == b) return false;
        if (wallColumn(c)) return true;
        if (min(tMax.x, tMax.y) > 1.0 + 1e-4) return false;
    }
    return false;
}

// light from outside through the window blinds: walk from the surface toward the lamp until a
// column with blinds; where the ray crosses it, the blind's slats (closed: thin slits, open:
// wide gaps) cut the light into stripes. 1 = no blind on the way. P, L: world positions.
float blindTransmission(vec3 P, vec3 L) {
    ivec2 a = ivec2(floor(P.xz)) - emapOrigin();
    ivec2 b = ivec2(floor(L.xz)) - emapOrigin();
    if (a == b) return 1.0;
    vec2 d = L.xz - P.xz;
    float len = length(d);
    vec2 stp = sign(d);
    vec2 inv = 1.0 / max(abs(d), vec2(1e-5));
    vec2 f = fract(P.xz);
    vec2 tMax = vec2(stp.x > 0.0 ? 1.0 - f.x : f.x, stp.y > 0.0 ? 1.0 - f.y : f.y) * inv;
    ivec2 c = a;
    for (int i = 0; i < 10; i++) {
        float tEnter = min(tMax.x, tMax.y);
        if (tMax.x < tMax.y) { c.x += int(stp.x); tMax.x += inv.x; }
        else { c.y += int(stp.y); tMax.y += inv.y; }
        if (tEnter > 1.0 || c == b) return 1.0;
        if (c.x < 0 || c.y < 0 || c.x >= EMAP_TILE || c.y >= EMAP_TILE) return 1.0;
        vec4 t = texelFetch(shadowcolor0, emapTileOffset(EMAP_BLIND) + c, 0);
        if (!emapBlindMarker(t)) continue;
        // the height of the ray in the middle of the blind's column
        float tMid = clamp((tEnter + min(tMax.x, tMax.y)) * 0.5, 0.0, 1.0);
        float y = mix(P.y, L.y, tMid);
        float yLow = floor(t.g * 255.0 + 0.5) + 32.0;
        if (y < yLow || y > yLow + 2.0) return 1.0;     // below the blinds: through clear glass
        float s = fract(y * 5.0 + 0.25);
        if (t.r > 0.5) {
            // closed: the slats overlap, a thin bright slit between each
            return 0.04 + 0.55 * smoothstep(0.78, 0.84, s) * (1.0 - smoothstep(0.93, 0.99, s));
        }
        // open: wide gaps between thin slats
        return 0.10 + 0.80 * smoothstep(0.30, 0.38, s) * (1.0 - smoothstep(0.88, 0.96, s));
    }
    return 1.0;
}

// ---- the light table (colortex7) ----------------------------------------------------------
//  texel (x, z) in [0, 128)^2 = block column emapOrigin() + (x, z); rgba = 4 lamps, each an
//  integer in a float: type (5 bits) | level (4) | y - tableRefY (5) | dx + 15 (5) | dz + 15 (5),
//  negative when a wall stands between the column and the lamp. 0 = empty.
int tableRefY() { return int(floor(cameraPosition.y)) - 12; }
const float TABLE_REACH = 15.0;
// the outdoor lamps of the air, one list for the whole frame (prepare1), in colortex7 row 0
// right of the table: texel AIR_LIST_X + i = (block - emap origin / table ref y as floats,
// type), texel AIR_LIST_X + AIR_OUT + i = (blocks merged into it, level, weight, 0)
#define AIR_LIST_X 128
#define AIR_OUT 12
// a column keeps a lamp while level - |dx| - |dz| - this > 0 (prepare.glsl): lamps far above
// or below the camera's band reach fewer of the surfaces that are seen
float tableVerticalPenalty(float y) {
    float ref = cameraPosition.y - 0.6;
    return max(abs(y + 0.5 - ref) - 2.5, 0.0) * 0.6;
}

float tablePack(int type, int level, int yrel, int dx, int dz, bool walled) {
    int c = type | (level << 5) | (yrel << 9) | ((dx + 15) << 14) | ((dz + 15) << 19);
    return walled ? -float(c) : float(c);
}

struct Lamp {
    vec3  pos;       // world position the light leaves from
    vec3  block;     // world position of the lamp block (min corner)
    int   type;
    float level;     // vanilla light level of the lamp
    bool  covered;   // indoors (its light belongs to indoor air)
    bool  walled;    // a wall stands between the lamp and the column it was read for
};

Lamp tableUnpack(float v, ivec2 column) {
    Lamp L;
    int c = int(abs(v) + 0.5);
    L.type = c & 31;
    L.level = float((c >> 5) & 15);
    int yrel = (c >> 9) & 31;
    int dx = ((c >> 14) & 31) - 15;
    int dz = ((c >> 19) & 31) - 15;
    L.walled = v < 0.0;
    L.covered = !emitterOutdoorType(L.type);
    L.block = vec3(float(column.x + dx), float(tableRefY() + yrel), float(column.y + dz));
    int ti = clamp(L.type, 0, EM_COUNT - 1);
    L.pos = L.block + vec3(0.5, 0.5 + EM_YOFF[ti], 0.5);
    return L;
}

// the 4 lamps of a block column (world xz). Empty slots: type 0
vec4 tableFetch(ivec2 column) {
    ivec2 c = column - emapOrigin();
    if (c.x < 0 || c.y < 0 || c.x >= EMAP_TILE || c.y >= EMAP_TILE) return vec4(0.0);
    return texelFetch(colortex7, c, 0);
}

// ---- light of one lamp at a point ---------------------------------------------------------
// distance falloff: inverse square with a soft core of the lamp's size, gone at its reach
// (vanilla's light level ends there too)
float lampFalloff(Lamp L, float d) {
    int ti = clamp(L.type, 0, EM_COUNT - 1);
    float s = EM_SIZE[ti];
    float reach = L.level + 1.0;
    return (1.0 - smoothstep(reach * 0.55, reach * 1.05, d)) / (d * d + s * s + 0.12);
}
// angular emission pattern. dir: unit vector from the lamp to the point
float lampPattern(int type, vec3 dir) {
    int p = EM_PATTERN[clamp(type, 0, EM_COUNT - 1)];
    if (p == 1) return pow(saturate(-dir.y), 0.8);                       // a panel in the ceiling
    if (p == 2) return 0.18 + 0.82 * pow(saturate(-dir.y * 0.8 + 0.2), 1.2); // a shade
    if (p == 3) return 0.10 + 0.90 * pow(saturate(-dir.y * 0.9 + 0.1), 0.7); // a street lamp
    return 1.0;
}
// the same pattern for the light in the air, with soft edges: in the mist a sharp cut-off
// draws a dome over the whole sky through the lamp's head
float lampPatternAir(int type, vec3 dir) {
    int p = EM_PATTERN[clamp(type, 0, EM_COUNT - 1)];
    if (p == 1) return smoothstep(-0.15, 0.85, -dir.y);
    if (p == 2) return 0.18 + 0.82 * smoothstep(-0.45, 0.90, -dir.y);
    if (p == 3) return 0.08 + 0.92 * smoothstep(-0.35, 0.75, -dir.y);
    return 1.0;
}
// colour of a lamp right now (flicker, danger stutter)
vec3 lampColorNow(Lamp L) {
    int ti = clamp(L.type, 0, EM_COUNT - 1);
    float f = emitterFlicker(L.type, L.block, frameTimeCounter) * dangerStutter(L.type, L.block, frameTimeCounter);
    return EM_COLOR[ti] * f;
}
// colour x strength of the light it casts
vec3 lampRadiance(Lamp L) {
    return lampColorNow(L) * EM_GAIN[clamp(L.type, 0, EM_COUNT - 1)] * LIGHT_STRENGTH;
}

// The lamps that light a surface.
//  wp: world position, N: world normal, V: unit vector to the eye, A: vanilla block light
//  level here (0..15, smooth), rough / f0: specular response (f0 < 0: no highlights)
//  diffuse: out, irradiance (x albedo by the caller), spec: out, reflected light
//  domDir / domW: out, direction to the strongest lamp and its share (screen-space shadows)
void lampsAt(vec3 wp, vec3 N, vec3 V, float A, float rough, float f0, float twoSided,
             out vec3 diffuse, out vec3 spec, out vec3 domPos, out float domW) {
    diffuse = vec3(0.0);
    spec = vec3(0.0);
    domPos = vec3(0.0);
    domW = 0.0;
    // the light cell of the surface: the air block in front of it
    vec3 cellP = wp + N * 0.5;
    ivec2 column = ivec2(floor(cellP.xz));
    vec4 tab = tableFetch(column);
    vec3 bounceC = vec3(0.0);
    float bounceW = 0.0;
    float best = 0.0;
    for (int k = 0; k < 4; k++) {
        float v = tab[k];
        if (abs(v) < 0.5) continue;
        Lamp L = tableUnpack(v, column);
        if (!emitterCasts(L.type)) continue;
        // the level this lamp would give here on a free path (vanilla: Manhattan distance)
        vec3 cc = floor(cellP) + 0.5;
        vec3 bc = L.block + 0.5;
        vec3 md = abs(cc - bc);
        float X = L.level - (md.x + md.y + md.z);
        vec3 colNow = lampColorNow(L);
        vec3 rad = colNow * EM_GAIN[clamp(L.type, 0, EM_COUNT - 1)] * LIGHT_STRENGTH;
        // the coloured bounce: the lamp that best explains the light level here
        float wb = exp(-abs(X - A) * 0.9) * step(A - 1.5, X) * step(0.5, X);
        bounceC += colNow * wb;
        bounceW += wb;
        // direct light: only if no wall stands in between and the vanilla level says the path is free
        if (L.walled) continue;
        float vis = (1.0 - smoothstep(A + 0.6, A + 2.2, X)) * smoothstep(0.3, 1.6, X);
        if (vis <= 0.0) continue;
        vec3 d = L.pos - wp;
        float dist = length(d);
        vec3 l = d / max(dist, 1e-3);
        float fall = lampFalloff(L, dist) * lampPattern(L.type, -l) * vis;
        if (fall <= 0.0) continue;
        // the sign outside, falling in through the blinds
        if (!L.covered && wp.y < 69.5) fall *= blindTransmission(wp + N * 0.05, L.pos);
        float ndl = dot(N, l);
        float lambert = mix(saturate(ndl * 0.92 + 0.08), 0.35 + 0.65 * abs(ndl), twoSided);
        vec3 E = rad * fall;
        diffuse += E * lambert;
        if (f0 >= 0.0) {
            float sizeAngle = EM_SIZE[clamp(L.type, 0, EM_COUNT - 1)] / max(dist, 0.3);
            spec += E * ggxSpec(N, V, l, rough, sizeAngle) * fresnelSchlick(f0, dot(normalize(l + V), V));
        }
        float w = maxOf(E) * saturate(ndl);
        if (w > best) {
            best = w;
            domPos = L.pos;
        }
    }
    // light that went round corners and off the walls: coloured by the lamp it came from
    float lv = A / 15.0;
    float bounceF = 0.10 * lv * lv + 0.010 * lv;
    vec3 bc = bounceW > 1e-3 ? bounceC / bounceW : vec3(0.80, 0.80, 0.78);
    diffuse += bc * bounceF * LIGHT_STRENGTH;
    domW = best / max(maxOf(diffuse), 1e-4);
}

// ---- the lamps in the mist -----------------------------------------------------------------
// Light scattered toward the eye by the air along a view ray (camera relative, unit rd).
// The lamps are gathered ONCE per ray (looking them up per march segment made rings wherever a
// lamp's closest approach crossed a segment boundary): the outdoor ones from the frame's own
// list (prepare1: the same for every pixel, signs merged - lists that change from ray to ray
// drew walls and wedges of light across the sky), the indoor ones from the table at eight
// columns along the ray, where walls are known, each faded out before it would drop out of
// those columns' lists. airLampsSegment() integrates the inverse-square falloff of every
// gathered lamp analytically over one segment (no shadows: the mist is thin indoors, the lamps
// outside stand in the open).
#define AIR_LAMPS 18
#define AIR_COLUMNS 8
int   airN = 0;
vec3  airBlock[AIR_LAMPS];
vec3  airRad[AIR_LAMPS];      // radiance x emission pattern toward the ray
float airW[AIR_LAMPS];        // fade-out (before a lamp leaves the lists)
float airTc[AIR_LAMPS];       // where along the ray it comes closest
float airH2[AIR_LAMPS];       // squared distance of the ray from it
float airS2[AIR_LAMPS];       // squared soft core
float airReach[AIR_LAMPS];
bool  airCovered[AIR_LAMPS];

void airAdd(int slot, Lamp L, vec3 rd, float tEnd, float w, float gainMul) {
    int ti = clamp(L.type, 0, EM_COUNT - 1);
    vec3 o = L.pos - cameraPosition;
    float tc = dot(o, rd);
    vec3 toClose = rd * clamp(tc, 0.0, tEnd) - o;
    airBlock[slot] = L.block;
    airRad[slot] = lampRadiance(L) * gainMul * lampPatternAir(L.type, toClose / max(length(toClose), 1e-3));
    airW[slot] = w;
    airTc[slot] = tc;
    airH2[slot] = max(dot(o, o) - tc * tc, 0.0);
    airS2[slot] = sq(EM_SIZE[ti]) + 0.12 + (gainMul > 1.0 ? 0.6 : 0.0);
    airReach[slot] = L.level + 1.0 + (gainMul > 1.0 ? 2.0 : 0.0);
    airCovered[slot] = L.covered;
}

void airLampsGather(vec3 rd, float tEnd) {
    airN = 0;
    // ---- outdoors: the frame's list (the same for every pixel: no seams) ------------------
    ivec2 org = emapOrigin();
    float refY = float(tableRefY());
    for (int i = 0; i < AIR_OUT; i++) {
        vec4 a = texelFetch(colortex7, ivec2(AIR_LIST_X + i, 0), 0);
        int type = int(a.w + 0.5);
        if (type <= 0) break;                            // packed: the rest is empty
        vec4 b = texelFetch(colortex7, ivec2(AIR_LIST_X + AIR_OUT + i, 0), 0);
        if (b.z <= 0.0 || airN >= AIR_LAMPS) continue;
        Lamp L;
        L.type = type;
        L.level = b.y;
        L.covered = false;
        L.walled = false;
        L.block = vec3(float(org.x) + a.x, refY + a.y, float(org.y) + a.z);
        L.pos = L.block + vec3(0.5, 0.5 + EM_YOFF[clamp(type, 0, EM_COUNT - 1)], 0.5);
        airAdd(airN, L, rd, tEnd, b.z, max(b.x, 1.0));
        airN++;
    }
    // ---- indoors: along the ray, from the columns it crosses (walls respected) ------------
    int nOut = airN;
    for (int k = 0; k < AIR_COLUMNS; k++) {
        vec3 p = cameraPosition + rd * (tEnd * pow((float(k) + 0.5) / float(AIR_COLUMNS), 1.5));
        ivec2 column = ivec2(floor(p.xz));
        vec4 tab = tableFetch(column);
        for (int j = 0; j < 4; j++) {
            float v = tab[j];
            if (v < 0.5) continue;                       // empty, or walled off from here
            Lamp L = tableUnpack(v, column);
            if (!L.covered || !emitterCasts(L.type)) continue;
            vec2 dc = abs(L.block.xz + 0.5 - p.xz);
            float w = smoothstep(0.0, 3.0, L.level - dc.x - dc.y - tableVerticalPenalty(L.block.y));
            if (w <= 0.0) continue;
            int slot = -1;
            for (int i = 0; i < AIR_LAMPS; i++) {
                if (i >= nOut && i < airN && all(lessThan(abs(airBlock[i] - L.block), vec3(0.5)))) slot = i;
            }
            if (slot >= 0) { airW[slot] = max(airW[slot], w); continue; }
            if (airN >= AIR_LAMPS) {
                // full: replace the weakest indoor lamp if this one matters more
                vec3 o = L.pos - cameraPosition;
                float tc = dot(o, rd);
                float imp = w * EM_GAIN[clamp(L.type, 0, EM_COUNT - 1)] / (max(dot(o, o) - tc * tc, 0.0) + 1.0);
                int weakest = -1;
                float wi = 1e9;
                for (int i = 0; i < AIR_LAMPS; i++) {
                    if (i < nOut) continue;
                    float im = airW[i] * luma(airRad[i]) / (airH2[i] + 1.0);
                    if (im < wi) { wi = im; weakest = i; }
                }
                if (weakest < 0 || imp <= wi) continue;
                slot = weakest;
            } else {
                slot = airN;
                airN++;
            }
            airAdd(slot, L, rd, tEnd, w, 1.0);
        }
    }
}

// the gathered lamps' light scattered on the segment [t0, t1]; sigma: scattering per block
vec3 airLampsSegment(float t0, float t1, float sigma, float pointCovered) {
    vec3 S = vec3(0.0);
    for (int i = 0; i < AIR_LAMPS; i++) {
        if (i >= airN) break;
        // indoor lamps light indoor air, outdoor lamps the open air
        float same = airCovered[i] ? pointCovered : 1.0 - pointCovered;
        if (same <= 0.01) continue;
        float tc = airTc[i];
        // the reach of the lamp: fade with the distance of the closest point of the segment
        float dmin2 = airH2[i] + sq(max(max(t0 - tc, tc - t1), 0.0));
        float reach = airReach[i];
        if (dmin2 >= reach * reach) continue;            // out of its reach (most of them)
        float fade = 1.0 - smoothstep(reach * 0.45, reach, sqrt(dmin2));
        float h = sqrt(airH2[i] + airS2[i]);
        // integral of I / (d^2 + s^2) along the segment
        float I = (atan((t1 - tc) / h) - atan((t0 - tc) / h)) / h;
        S += airRad[i] * (airW[i] * max(I, 0.0) * fade * same);
    }
    return S * sigma;
}
