// =====================================================================================
//  HitoriShaders - the light sources of room 203 and its street (hitori:shirakaba)
//
//  Vanilla only knows one light LEVEL per block. The pack finds out WHICH lamp lights a
//  surface - and so its colour, direction and flicker - like this:
//
//  1. shadow pass (program/shadow.glsl): the sky is overcast, there is no moon to cast
//     shadows, so the shadow map becomes a top-down EMITTER MAP around the camera: 4 x 4
//     tiles of 128 x 128 texels, one texel per block column. Tiles 0..3 hold the lit lamps
//     (block.properties ids 11001.., see EM_*) of 4 height layers, every face of a lamp
//     block snapped onto the block's footprint (at_midBlock); a TV's type gets the direction
//     its glowing screen faces. Tile 4 holds the height of the topmost surface of every column
//     (roof / sky cover); tiles 5 / 6 the full wall blocks at y 70 / 71 - room 203's walls,
//     shut doors and fusuma (a column is a wall where both are set: open doorways stay open);
//     tile 7 the lowest curtain of each column (the street lamp glows through drawn curtains);
//     tile 8 the closet floors (the enclosed space of the oshiire above them).
//  2. prepare  (TABLE_Z): per column, the best 4 lamps along z within reach (separable
//     Manhattan distance - exactly how vanilla light spreads)
//     prepare1 (TABLE_X): the same along x  ->  colortex7: the 4 lamps that can light each
//     block column, packed into one RGBA32F texel (24-bit integers in floats).
//  3. every lit program reads its column's 4 lamps: direct light with inverse-square falloff,
//     emission pattern (the TV's screen lobe), N.L, flicker (the TV's static) and GGX highlights,
//     gated by the vanilla light level (a lamp whose light would have to be brighter here than
//     it is must be behind a wall), plus a coloured bounce of the vanilla level for the light
//     that went round corners. The fog march does the same per step (analytic in-scatter).
// =====================================================================================

// ---- emitter types (block.properties: 11000 + type) -------------------------------------
#define EM_NONE         0
#define EM_TV           1    // 1..12 the CRT TV: EM_TV + 4 * channel + facing (block ids use facing 0)
#define EM_TV_LAST      12   //   channel 0 static (sunaarashi), 1 a dark room, 2 a face; facing 0 W 1 E 2 N 3 S
#define EM_RING         13   // fluorescent_ring lit: the circular tube, cold daylight white
#define EM_HANGING      14   // hanging_lamp lit: the tatami room's lamp, a paper shade
#define EM_HANGING_SWING 15  // hanging_lamp lit, swinging: the light sways with it
#define EM_DESK         16   // desk_lamp lit: a warm bulb under a shade
#define EM_STREET       17   // street_lamp_old lit: sodium, through the curtains and the frosted glass
#define EM_VENDING      18   // vending_machine_small: the lit front
#define EM_LAPTOP       19   // laptop_block on: the screen
#define EM_GAS          20   // gas_stove lit: blue flames
#define EM_FRIDGE       21   // fridge_old open: the little lamp inside
#define EM_INTERCOM     22   // intercom ringing: its small monitor
#define EM_LED          23   // a tiny LED (the breaker's indicator ...): glows, casts nothing
#define EM_SCREEN       24   // a tiny display: glows, casts nothing
#define EM_FLAME        25   // vanilla torches, candles, fire - the doll burning in the sink
#define EM_LANTERN      26   // vanilla lanterns
#define EM_GLOWBLOCK    27   // glowstone, sea lantern ...
#define EM_COUNT        28

bool emitterIsTv(int type) { return type >= EM_TV && type <= EM_TV_LAST; }
int tvChannel(int type) { return clamp((type - EM_TV) / 4, 0, 2); }
int tvFacing(int type) { return clamp((type - EM_TV) - 4 * tvChannel(type), 0, 3); }
// screens light only what they face, and may carry no vanilla light at all
bool emitterIsScreen(int type) { return emitterIsTv(type) || type == EM_LAPTOP || type == EM_INTERCOM; }
// horizontal facing index of a normal: 0 W (-x), 1 E (+x), 2 N (-z), 3 S (+z)
int facingOfNormal(vec3 n) {
    if (abs(n.x) >= abs(n.z)) return n.x < 0.0 ? 0 : 1;
    return n.z < 0.0 ? 2 : 3;
}
vec3 facingDir(int f) {
    if (f == 0) return vec3(-1.0, 0.0, 0.0);
    if (f == 1) return vec3(1.0, 0.0, 0.0);
    if (f == 2) return vec3(0.0, 0.0, -1.0);
    return vec3(0.0, 0.0, 1.0);
}

// colour of the light (linear, roughly unit luminance-ish)
const vec3 EM_COLOR[EM_COUNT] = vec3[EM_COUNT](
    vec3(0.0),
    vec3(0.58, 0.72, 1.00), vec3(0.58, 0.72, 1.00), vec3(0.58, 0.72, 1.00), vec3(0.58, 0.72, 1.00),   // static: cold blue-white
    vec3(0.45, 0.54, 0.80), vec3(0.45, 0.54, 0.80), vec3(0.45, 0.54, 0.80), vec3(0.45, 0.54, 0.80),   // a dark room
    vec3(0.72, 0.74, 0.86), vec3(0.72, 0.74, 0.86), vec3(0.72, 0.74, 0.86), vec3(0.72, 0.74, 0.86),   // a face
    vec3(0.80, 0.97, 0.92),     // ring fluorescent: cold daylight with the green spike
    vec3(1.00, 0.86, 0.66),     // hanging lamp: warm white through the paper shade
    vec3(1.00, 0.86, 0.66),
    vec3(1.00, 0.68, 0.40),     // desk lamp ~2700 K
    vec3(1.00, 0.50, 0.14),     // sodium
    vec3(0.85, 0.94, 1.00),     // vending machine: cold white
    vec3(0.66, 0.80, 1.00),     // laptop screen
    vec3(0.22, 0.42, 1.00),     // gas flames
    vec3(1.00, 0.94, 0.84),     // the fridge's lamp
    vec3(0.66, 0.84, 1.00),     // intercom monitor
    vec3(1.00, 0.06, 0.03),     // LED
    vec3(0.60, 0.85, 0.75),     // small display
    vec3(1.00, 0.58, 0.28),     // flames
    vec3(1.00, 0.62, 0.32),     // lanterns
    vec3(1.00, 0.80, 0.55));    // glowing blocks

// intensity I of the light cast: irradiance E = I / (d^2 + size^2) (before LIGHT_STRENGTH).
// The TV is the room's light after three: dim, but it reaches the closet across the tatami.
const float EM_GAIN[EM_COUNT] = float[EM_COUNT](
    0.0, 2.4, 2.4, 2.4, 2.4, 0.70, 0.70, 0.70, 0.70, 1.2, 1.2, 1.2, 1.2,
    11.0, 9.0, 9.0, 3.5, 26.0, 3.0, 0.35, 0.50, 0.90, 0.15, 0.0, 0.0, 1.4, 1.6, 2.5);

// radius of the glowing part: the soft core of the falloff, widens the highlights
const float EM_SIZE[EM_COUNT] = float[EM_COUNT](
    0.5, 0.30, 0.30, 0.30, 0.30, 0.30, 0.30, 0.30, 0.30, 0.30, 0.30, 0.30, 0.30,
    0.45, 0.40, 0.40, 0.20, 0.35, 0.70, 0.20, 0.25, 0.35, 0.10, 0.05, 0.10, 0.20, 0.20, 0.50);

// where the light leaves the block, relative to the block centre (y): the ring under the
// ceiling shines from its diffuser, the hanging shade below its cord, a street lamp head from below
const float EM_YOFF[EM_COUNT] = float[EM_COUNT](
    0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0,
    0.15, -0.15, -0.15, 0.0, -0.25, 0.0, -0.10, -0.30, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0);

// emission pattern: 0 omni, 1 ceiling fixture (cosine downwards, nothing above), 2 paper shade
// (mostly down, much to the sides), 3 street lamp (down-wide), 4 desk lamp (a cone down),
// 5 screen (a lobe along the type's facing)
const int EM_PATTERN[EM_COUNT] = int[EM_COUNT](
    0, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5,
    1, 2, 2, 4, 3, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);

// how bright the lamp's own glowing texels are (surface emission, x texture colour)
const float EM_SURF[EM_COUNT] = float[EM_COUNT](
    0.0, 1.6, 1.6, 1.6, 1.6, 0.9, 0.9, 0.9, 0.9, 1.2, 1.2, 1.2, 1.2,
    3.2, 2.6, 2.6, 3.0, 5.0, 1.6, 1.3, 3.0, 1.6, 1.2, 2.6, 1.0, 3.2, 1.8, 1.6);

// vanilla light levels of the design (used when the shadow pass cannot measure them; a screen
// never reaches less far than this)
const float EM_LEVEL[EM_COUNT] = float[EM_COUNT](
    0.0, 9.0, 9.0, 9.0, 9.0, 6.0, 6.0, 6.0, 6.0, 7.0, 7.0, 7.0, 7.0,
    14.0, 13.0, 13.0, 10.0, 15.0, 12.0, 5.0, 7.0, 8.0, 4.0, 0.0, 0.0, 14.0, 15.0, 15.0);

bool emitterCasts(int type) { return type > EM_NONE && type < EM_COUNT && type != EM_LED && type != EM_SCREEN; }
int emitterTypeOfBlock(int id) {
    if (id > BLK_EMIT_BASE && id <= BLK_EMIT_MAX) return id - BLK_EMIT_BASE;
    if (id == BLK_FLAME || id == BLK_CANDLE) return EM_FLAME;
    if (id == BLK_LAMP)  return EM_LANTERN;
    if (id == BLK_GLOW)  return EM_GLOWBLOCK;
    return EM_NONE;
}
// lamps that stand outside (their light belongs to the open air, the rain over the street)
bool emitterOutdoorType(int type) {
    return type == EM_STREET || type == EM_VENDING;
}

// ---- flicker ---------------------------------------------------------------------------
// The CRT's light. Static (sunaarashi) is grey on average, but the room sees it breathe: the
// snow's brightness jumps from frame to frame, the picture's gain hunts slowly, a darker roll
// bar passes now and then, the signal drops out for an instant (more often near danger).
// Channel 1 - a dark room: dim and almost steady. Channel 2 - a face: a slow pale pulse.
float tvSignal(int ch, float h, float t) {
#ifdef LIGHT_FLICKER
    float D = danger();
    if (ch == 0) {
        float frame = floor(t * 30.0);
        float jit = hash11(frame * 1.37 + h * 91.0) - 0.5;
        float fast = vnoise1(t * 11.0 + h * 17.0) - 0.5;
        float slow = vnoise1(t * 0.55 + h * 5.0) - 0.5;
        float roll = smoothstep(0.82, 1.0, sin(t * 2.3 + h * 6.0) * 0.5 + 0.5);
        float drop = step(0.968 - 0.05 * D, hash11(floor(t * 7.0) * 2.1 + h * 31.0));
        float s = 0.80 + 0.11 * jit * (1.0 + D) + 0.10 * fast + 0.16 * slow - 0.12 * roll - 0.32 * drop;
      #ifdef FLASH_REDUCTION
        s = mix(0.80, s, 0.30);
      #endif
        return max(s, 0.05);
    }
    if (ch == 1) {
        return 0.86 + 0.07 * (vnoise1(t * 3.0 + h * 7.0) - 0.5) + 0.03 * (hash11(floor(t * 30.0) + h * 13.0) - 0.5);
    }
    float pulse = 0.5 + 0.5 * sin(t * 0.9 + h * 3.0);
    return 0.72 + 0.22 * pulse * (0.6 + 0.4 * vnoise1(t * 0.31 + h));
#else
    return 0.85;
#endif
}

// every lamp computes the same value for its own glowing surface (gbuffers) and for the
// light it casts (table), from its block position
float emitterFlicker(int type, vec3 blockPos, float t) {
    float h = hash13(floor(blockPos) * vec3(0.731, 1.37, 0.513) + 0.17);
    if (emitterIsTv(type)) return tvSignal(tvChannel(type), h, t);
#ifdef LIGHT_FLICKER
    if (type == EM_RING) {
        // an old tube: a faint shimmer, a rare tiny dip
        float dip = step(0.985, hash11(floor(t * 7.0) * 1.7 + h * 51.0)) * 0.22;
        return 1.0 - 0.03 * vnoise1(t * 13.0 + h * 40.0) - dip;
    }
    if (type == EM_STREET) {
        // the old sodium head buzzes; one in five is dying: now and then it fades, restrikes
        float f = 1.0 - 0.04 * vnoise1(t * 17.0 + h * 5.0);
        if (h > 0.80) {
            float c = fract(t / 53.0 + h * 3.0);
            f *= 1.0 - 0.85 * smoothstep(0.62, 0.70, c) * (1.0 - smoothstep(0.86, 0.97, c));
        }
        return f;
    }
    if (type == EM_GAS || type == EM_FLAME || type == EM_LANTERN) {
        float n = vnoise1(t * 7.3 + h * 37.0) * 0.6 + vnoise1(t * 17.1 + h * 11.0) * 0.4;
        return 0.86 + 0.24 * n;
    }
    if (type == EM_LAPTOP || type == EM_INTERCOM) {
        float a = type == EM_INTERCOM ? 0.70 + 0.30 * step(0.5, fract(t * 1.2)) : 1.0;
        return a * (0.94 + 0.06 * vnoise1(t * 4.0 + h * 21.0));
    }
    if (type == EM_VENDING) {
        return 1.0 - 0.04 * vnoise1(t * 11.0 + h * 9.0);
    }
#endif
    return 1.0;
}

// danger makes the room's lamps stutter (the doll is near)
float dangerStutter(int type, vec3 blockPos, float t) {
#ifdef LIGHT_FLICKER
    float D = smoothstep(0.45, 1.0, danger());
    if (D <= 0.0 || !(type == EM_RING || type == EM_HANGING || type == EM_HANGING_SWING || type == EM_DESK)) return 1.0;
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

// the hanging lamp swinging on its cord (a pendulum of ~1.6 s): offset of its light
vec3 lampSwing(vec3 blockPos, float t) {
    float h = hash13(floor(blockPos) + 9.1);
    float a = (0.22 + 0.06 * sin(t * 0.21)) * sin(t * TAU / 1.6 + h * 6.0);
    vec2 d = normalize(vec2(cos(h * 6.0), sin(h * 6.0)));
    return vec3(d.x * a, -0.18 * a * a, d.y * a);
}

// ---- the glowing surface of a lamp (gbuffers, shadow pass) ------------------------------------
// Which texels of a lamp model glow - never the whole model: the housings round the light (the
// TV's cabinet, the shade's frame, the vending machine's case) are as bright as any lamp in a
// texture, so brightness alone says little.
//  * marked: the art draws the glowing faces of its lamp models full-bright (forge_data
//    block_light / sky_light 15, baked into the quad): on such a face every lit texel glows (a
//    screen's picture, the shade's paper), the darkest ones do not.
//  * unmarked faces glow only where the colour is the lamp's own: its hue, saturated and
//    bright, or a white hotter than any housing. c: texture colour (sRGB), N: world normal
float emitterSurfaceMask(int type, vec3 c, vec3 N, bool marked) {
    float l = luma(c);
    float mx = maxOf(c);
    float ch = mx - minOf(c);           // absolute chroma: dark colours count less
    float strict;
    if (emitterIsTv(type)) {
        // the screen: the static's greys (or the picture), not the dark cabinet, not the chrome knobs
        strict = smoothstep(0.20, 0.32, l) * (1.0 - smoothstep(0.08, 0.14, ch)) * (1.0 - smoothstep(0.90, 0.97, l));
        return marked ? max(strict, smoothstep(0.03, 0.12, l)) : strict;
    } else if (type == EM_RING) {
        // the milky diffuser on the bottom face, not its rim or the ceiling round it
        strict = smoothstep(-0.4, -0.6, N.y) * smoothstep(0.78, 0.88, l) * (1.0 - smoothstep(0.05, 0.10, c.r - c.b));
    } else if (type == EM_HANGING || type == EM_HANGING_SWING) {
        // the paper shade, lit from inside (not the dark frame, the cord, the cap)
        strict = smoothstep(0.55, 0.72, l) * (1.0 - smoothstep(0.30, 0.45, ch));
    } else if (type == EM_DESK) {
        // the bulb under the shade, white-hot or warm and bright
        strict = max(smoothstep(0.88, 0.95, l), smoothstep(0.74, 0.84, l) * smoothstep(0.18, 0.28, ch)) * smoothstep(0.2, -0.3, N.y);
    } else if (type == EM_STREET) {
        // the lens: saturated and bright (the grey housing stays dark)
        strict = max(smoothstep(0.35, 0.55, ch) * smoothstep(0.75, 0.95, mx), smoothstep(0.92, 0.97, l));
    } else if (type == EM_VENDING) {
        // the cans and labels; the white display only where the art marks it (the cabinet is as white)
        strict = smoothstep(0.30, 0.50, ch) * smoothstep(0.45, 0.75, mx);
    } else if (type == EM_LAPTOP || type == EM_INTERCOM || type == EM_SCREEN) {
        // the screen's blue and its white, not the grey plastic
        strict = max(smoothstep(0.06, 0.14, c.b - c.r) * smoothstep(0.35, 0.55, l), smoothstep(0.88, 0.93, l));
    } else if (type == EM_GAS) {
        strict = smoothstep(0.30, 0.45, ch) * smoothstep(0.60, 0.85, mx) * step(c.r, c.b);
    } else if (type == EM_FRIDGE) {
        // the white light inside the open door, not the enamel outside
        strict = smoothstep(0.86, 0.93, l) * (1.0 - smoothstep(0.06, 0.12, ch));
    } else if (type == EM_LED) {
        // a red or green LED: pure and bright
        return max(smoothstep(0.50, 0.70, c.r - max(c.g, c.b)) * smoothstep(0.70, 0.90, c.r),
                   smoothstep(0.40, 0.60, c.g - max(c.r, c.b)) * smoothstep(0.60, 0.85, c.g));
    } else {
        // anything else: the bright texels
        strict = smoothstep(0.38, 0.72, l + ch * 0.30);
    }
    // a marked face: every lit texel glows by its own colour
    return marked ? max(strict, smoothstep(0.30, 0.65, l + 0.5 * ch)) : strict;
}

// the small indicator lights the art marks full-bright on blocks that are not lamps (the
// breaker's lamp, the rice cooker, the washing machine's panel): their coloured texels glow,
// the grey panels round them do not
float indicatorMask(vec3 c) {
    return max(smoothstep(0.35, 0.55, maxOf(c) - minOf(c)) * smoothstep(0.60, 0.85, maxOf(c)), smoothstep(0.92, 0.97, luma(c)));
}

// ---- shadow pass encoding (shadowcolor0, RGBA8; alpha is always 1, so neither an injected
//  alpha test nor blending of translucent geometry can change a value) -------------------------
//  lamp tiles   : r = type / 255, g = (block y - 32) / 255, b = (128 + measured level) / 255
//  cover tile   : r / g = height of the topmost surface in 1/16 blocks (hi / lo byte), b = 64 / 255
//  wall tiles   : b = 32 / 255
//  curtain tile : r = drawn, g = (block y - 32) / 255 of the lowest curtain, b = 16 / 255
//  closet tile  : g = (block y - 32) / 255 of the closet floor, b = 8 / 255
const int EMAP_TILE = 128;
const int EMAP_COVER = 4;          // tile index of the cover heights
const int EMAP_WALL_LO = 5;        // full wall blocks at y 70
const int EMAP_WALL_HI = 6;        // full wall blocks at y 71
const int EMAP_WALL_Y = 70;        // room 203: the second floor's feet level
const int EMAP_CURTAIN = 7;        // the lowest curtain of each column
const int EMAP_CLOSET = 8;         // closet floors

ivec2 emapOrigin() { return ivec2(floor(cameraPosition.xz)) - ivec2(EMAP_TILE / 2); }
ivec2 emapTileOffset(int tile) { return ivec2(tile % 4, tile / 4) * EMAP_TILE; }
// height layers of the lamp tiles: the street and the ground floor / the second floor's floor
// props (the TV on its stand) / the desk (desk lamp, laptop) / the ceiling and above
int emapLayer(int blockY) {
    if (blockY <= 67) return 0;
    if (blockY <= 70) return 1;
    if (blockY == 71) return 2;
    return 3;
}
float emapByte(float v) { return floor(v * 255.0 + 0.5); }
bool emapLampMarker(vec4 t) { float b = emapByte(t.b); return b >= 128.0 && b <= 143.0 && t.a > 0.5; }
bool emapCoverMarker(vec4 t) { return abs(t.b * 255.0 - 64.0) < 0.5 && t.a > 0.5; }
bool emapWallMarker(vec4 t) { return abs(t.b * 255.0 - 32.0) < 0.5 && t.a > 0.5; }
bool emapCurtainMarker(vec4 t) { return abs(t.b * 255.0 - 16.0) < 0.5 && t.a > 0.5; }
bool emapClosetMarker(vec4 t) { return abs(t.b * 255.0 - 8.0) < 0.5 && t.a > 0.5; }
int emapLampLevel(vec4 t) { return int(emapByte(t.b)) - 128; }
float emapCoverHeight(vec4 t) { return (emapByte(t.r) * 256.0 + emapByte(t.g)) / 16.0; }
bool emapInside(ivec2 c) { return c.x >= 0 && c.y >= 0 && c.x < EMAP_TILE && c.y < EMAP_TILE; }

// height of the topmost surface of the block column (world xz), -1e3 if unknown
float coverHeight(ivec2 column) {
    ivec2 c = column - emapOrigin();
    if (!emapInside(c)) return -1e3;
    vec4 t = texelFetch(shadowcolor0, emapTileOffset(EMAP_COVER) + c, 0);
    if (!emapCoverMarker(t)) return -1e3;
    return emapCoverHeight(t);
}
// 1 = under the open sky, 0 = something above (roof, eaves, the corridor's roof, a ceiling)
float openSky(vec3 worldPos) {
    float h = coverHeight(ivec2(floor(worldPos.xz)));
    return h < -999.0 ? 1.0 : smoothstep(h - 0.05, h + 0.25, worldPos.y);
}

// 1 inside a closet (the oshiire: the space above a closet floor, both its levels)
float closetAt(vec3 p) {
    ivec2 c = ivec2(floor(p.xz)) - emapOrigin();
    if (!emapInside(c)) return 0.0;
    vec4 t = texelFetch(shadowcolor0, emapTileOffset(EMAP_CLOSET) + c, 0);
    if (!emapClosetMarker(t)) return 0.0;
    float fy = emapByte(t.g) + 32.0;             // the floor block (its top at fy + 1)
    return step(fy + 0.9, p.y) * step(p.y, fy + 4.1);
}

// a wall stands in this column (map coordinates): full blocks at y 70 and 71
bool wallColumn(ivec2 c) {
    if (!emapInside(c)) return false;
    return emapWallMarker(texelFetch(shadowcolor0, emapTileOffset(EMAP_WALL_LO) + c, 0))
        && emapWallMarker(texelFetch(shadowcolor0, emapTileOffset(EMAP_WALL_HI) + c, 0));
}
// walk the columns between two (map coordinates): is there a wall in between?
bool wallBetween(ivec2 a, ivec2 b) {
    if (a == b) return false;
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

// light from outside through drawn curtains: walk from the surface toward the lamp until a
// column with a curtain; where the ray crosses it within the curtain's height the cloth lets a
// little through - a soft, warm glow, a little more where it hangs in thin folds. 1 = no
// curtain on the way (or one pushed aside). P, L: world positions.
float curtainTransmission(vec3 P, vec3 L) {
    ivec2 a = ivec2(floor(P.xz)) - emapOrigin();
    ivec2 b = ivec2(floor(L.xz)) - emapOrigin();
    if (a == b) return 1.0;
    vec2 d = L.xz - P.xz;
    vec2 stp = sign(d);
    vec2 inv = 1.0 / max(abs(d), vec2(1e-5));
    vec2 f = fract(P.xz);
    vec2 tMax = vec2(stp.x > 0.0 ? 1.0 - f.x : f.x, stp.y > 0.0 ? 1.0 - f.y : f.y) * inv;
    ivec2 c = a;
    for (int i = 0; i < 12; i++) {
        float tEnter = min(tMax.x, tMax.y);
        if (tMax.x < tMax.y) { c.x += int(stp.x); tMax.x += inv.x; }
        else { c.y += int(stp.y); tMax.y += inv.y; }
        if (tEnter > 1.0 || c == b) return 1.0;
        if (!emapInside(c)) return 1.0;
        vec4 t = texelFetch(shadowcolor0, emapTileOffset(EMAP_CURTAIN) + c, 0);
        if (!emapCurtainMarker(t)) continue;
        float tMid = clamp((tEnter + min(tMax.x, tMax.y)) * 0.5, 0.0, 1.0);
        vec3 X = mix(P, L, tMid);
        float yLow = emapByte(t.g) + 32.0;
        if (X.y < yLow - 0.05 || X.y > yLow + 3.0) return 1.0;   // under / above the curtain
        if (t.r < 0.5) return 1.0;                              // pushed aside
        float u = (X.x + X.z) * 3.1;
        float fold = 0.5 + 0.5 * sin(u * TAU + 1.7 * sin(u * 1.3));
        return 0.13 + 0.07 * fold;
    }
    return 1.0;
}

// ---- the light table (colortex7) ----------------------------------------------------------
//  texel (x, z) in [0, 128)^2 = block column emapOrigin() + (x, z); rgba = 4 lamps, each an
//  integer (stored doubled, tablePack): type (5 bits) | level (4) | y - tableRefY (5) | dx + 15 (5) | dz + 15 (5),
//  negative when a wall stands between the column and the lamp. 0 = empty.
//  The 32-block height band starts 12 below the camera, at most at y 58: the world's lamps stand
//  between y 64 and 76 and stay in the band wherever the camera is.
int tableRefY() { return min(int(floor(cameraPosition.y)) - 12, 58); }
const float TABLE_REACH = 15.0;
// the outdoor lamps of the air, one list for the whole frame (prepare1), in colortex7 row 0
// right of the table: texel AIR_LIST_X + i = (block - emap origin / table ref y as floats,
// type), texel AIR_LIST_X + AIR_OUT + i = (blocks merged into it, level, weight, 0)
#define AIR_LIST_X 128
#define AIR_OUT 12
// a column keeps a lamp while level - |dx| - |dz| - this > 0 (prepare.glsl): lamps far above
// or below the camera's storey reach fewer of the surfaces that are seen
float tableVerticalPenalty(float y) {
    float ref = cameraPosition.y - 0.6;
    return max(abs(y + 0.5 - ref) - 2.5, 0.0) * 0.6;
}
// Out in the open the street lamp and the vending machine light the wet street well beyond
// vanilla's 15 levels - a street faintly lit from lamp to lamp, not pools that end in a step:
// the light table keeps them for columns this much farther away (prepare.glsl) and open-air
// surfaces see them that much farther.
const float OUTDOOR_REACH = 7.0;

// The 24-bit code is stored doubled (an even integer below 2^25: exact in a float). Read back
// with int(x + 0.5): above 2^23 a float cannot hold x.5 and the sum rounds to even - an odd code
// stored as itself would gain one there (its type bits change), an even one comes back as it was.
// (Keep this exact decode expression: int(abs(v) + 0.25) made the CI's llvmpipe - Mesa 26 /
// LLVM 21 - hang at the first frame in REISATSU's world. Never decode floats any other way.)
float tablePack(int type, int level, int yrel, int dx, int dz, bool walled) {
    int c = type | (level << 5) | (yrel << 9) | ((dx + 15) << 14) | ((dz + 15) << 19);
    float f = float(c * 2);
    return walled ? -f : f;
}
// the packed integer back from its float
int tableCode(float v) {
    return int(abs(v) + 0.5) >> 1;
}

struct Lamp {
    vec3  pos;       // world position the light leaves from
    vec3  block;     // world position of the lamp block (min corner)
    int   type;
    float level;     // vanilla light level of the lamp
    bool  covered;   // indoors (its light belongs to indoor air)
    bool  walled;    // a wall stands between the lamp and the column it was read for
};

// where a lamp's light leaves it: a TV from its screen, a swinging lamp where it is right now
vec3 lampOrigin(int type, vec3 block) {
    int ti = clamp(type, 0, EM_COUNT - 1);
    vec3 p = block + vec3(0.5, 0.5 + EM_YOFF[ti], 0.5);
    if (emitterIsTv(type)) p += facingDir(tvFacing(type)) * 0.42;
    if (type == EM_HANGING_SWING) p += lampSwing(block, frameTimeCounter);
    return p;
}

Lamp tableUnpack(float v, ivec2 column) {
    Lamp L;
    int c = tableCode(v);
    L.type = c & 31;
    L.level = float((c >> 5) & 15);
    int yrel = (c >> 9) & 31;
    int dx = ((c >> 14) & 31) - 15;
    int dz = ((c >> 19) & 31) - 15;
    L.walled = v < 0.0;
    L.covered = !emitterOutdoorType(L.type);
    L.block = vec3(float(column.x + dx), float(tableRefY() + yrel), float(column.y + dz));
    L.pos = lampOrigin(L.type, L.block);
    return L;
}

// the 4 lamps of a block column (world xz). Empty slots: type 0
vec4 tableFetch(ivec2 column) {
    ivec2 c = column - emapOrigin();
    if (!emapInside(c)) return vec4(0.0);
    return texelFetch(colortex7, c, 0);
}

// ---- light of one lamp at a point ---------------------------------------------------------
// distance falloff: inverse square with a soft core of the lamp's size, gone at its reach
// (vanilla's light level ends there too); outdoor lamps over open ground reach farther
float lampFalloff(Lamp L, float d, bool open) {
    int ti = clamp(L.type, 0, EM_COUNT - 1);
    float s = EM_SIZE[ti];
    float reach = L.level + 1.0 + (open ? OUTDOOR_REACH : 0.0);
    return (1.0 - smoothstep(reach * 0.55, reach * 1.05, d)) / (d * d + s * s + 0.12);
}
float lampFalloff(Lamp L, float d) { return lampFalloff(L, d, false); }
// does the lamp reach the surface? X: the level the lamp alone would give here (vanilla:
// Manhattan distance), A: the vanilla level here. A lamp whose light would have to be brighter
// here than it is must be behind a wall. An outdoor lamp lighting an open-air surface (open) is
// stopped only by the walls the table knows (the building), not by vanilla's reach; a screen
// (the TV may carry no vanilla light at all) only by the walls, the shut fusuma and its reach.
float lampVis(float X, float A, bool open, bool screen) {
    if (open) return smoothstep(0.3, 1.6, X + OUTDOOR_REACH);
    if (screen) return smoothstep(0.3, 1.6, X);
    return (1.0 - smoothstep(A + 0.6, A + 2.2, X)) * smoothstep(0.3, 1.6, X);
}
// angular emission pattern. dir: unit vector from the lamp to the point
float lampPattern(int type, vec3 dir) {
    int p = EM_PATTERN[clamp(type, 0, EM_COUNT - 1)];
    if (p == 1) return pow(saturate(-dir.y), 0.8);                          // a ceiling fixture
    if (p == 2) return 0.40 + 0.60 * pow(saturate(-dir.y * 0.8 + 0.2), 1.2); // a paper shade
    if (p == 3) return 0.10 + 0.90 * pow(saturate(-dir.y * 0.9 + 0.1), 0.7); // a street lamp
    if (p == 4) return 0.06 + 0.94 * pow(saturate(-dir.y * 1.1 + 0.05), 2.2); // a desk lamp's cone
    if (p == 5) {
        // a screen: a broad lobe in front, nothing behind the cabinet
        float c = dot(dir, facingDir(tvFacing(type)));
        return 0.03 + 0.97 * pow(saturate(c * 0.85 + 0.15), 1.3);
    }
    return 1.0;
}
// the same pattern for the light in the air, with soft edges (no sharp domes in the haze)
float lampPatternAir(int type, vec3 dir) {
    int p = EM_PATTERN[clamp(type, 0, EM_COUNT - 1)];
    if (p == 1) return smoothstep(-0.15, 0.85, -dir.y);
    if (p == 2) return 0.40 + 0.60 * smoothstep(-0.45, 0.90, -dir.y);
    if (p == 3) return 0.08 + 0.92 * smoothstep(-0.35, 0.75, -dir.y);
    if (p == 4) return 0.05 + 0.95 * smoothstep(0.10, 0.95, -dir.y);
    if (p == 5) return 0.03 + 0.97 * smoothstep(-0.20, 0.90, dot(dir, facingDir(tvFacing(type))));
    return 1.0;
}
float lampGain(int type) {
    float g = EM_GAIN[clamp(type, 0, EM_COUNT - 1)];
    return emitterIsTv(type) ? g * TV_STRENGTH : g;
}
// colour of a lamp right now (flicker, danger stutter)
vec3 lampColorNow(Lamp L) {
    int ti = clamp(L.type, 0, EM_COUNT - 1);
    float f = emitterFlicker(L.type, L.block, frameTimeCounter) * dangerStutter(L.type, L.block, frameTimeCounter);
    return EM_COLOR[ti] * f;
}
// colour x strength of the light it casts
vec3 lampRadiance(Lamp L) {
    return lampColorNow(L) * lampGain(L.type) * LIGHT_STRENGTH;
}

// The lamps that light a surface.
//  wp: world position, N: world normal, V: unit vector to the eye, A: vanilla block light
//  level here (0..15, smooth), rough / f0: specular response (f0 < 0: no highlights)
//  twoSided: 1 = thin leaves / particles (lit from both sides)
//  backK: light from behind that comes through (curtains, frosted glass, paper)
//  fillK: the fill for light no lamp explains (0 on the art's full-bright faces, whose lightmap
//  was replaced: their real light level is unknown)
//  diffuse: out, irradiance (x albedo by the caller), spec: out, reflected light
//  domPos / domW: out, position of the strongest lamp and its share (screen-space shadows)
void lampsAt(vec3 wp, vec3 N, vec3 V, float A, float rough, float f0, float twoSided, float backK, float fillK,
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
    float Xmax = 0.0;                    // the brightest level the known lamps explain here
    bool openAir = openSky(cellP) > 0.5;
    // inside the closet the light only comes in through the gaps of the fusuma (deferred cuts
    // the lamps' light to the slit); no light goes round corners in there
    float enclosed = closetAt(cellP);
    for (int k = 0; k < 4; k++) {
        float v = tab[k];
        if (abs(v) < 0.5) continue;
        Lamp L = tableUnpack(v, column);
        // the level this lamp would give here on a free path (vanilla: Manhattan distance)
        vec3 cc = floor(cellP) + 0.5;
        vec3 bc = L.block + 0.5;
        vec3 md = abs(cc - bc);
        float X = L.level - (md.x + md.y + md.z);
        Xmax = max(Xmax, X);             // walled ones too: vanilla light goes round the walls
        if (!emitterCasts(L.type)) continue;
        vec3 colNow = lampColorNow(L);
        vec3 rad = colNow * lampGain(L.type) * LIGHT_STRENGTH;
        // the coloured bounce: the lamp that best explains the light level here
        float wb = exp(-abs(X - A) * 0.9) * step(A - 1.5, X) * step(0.5, X);
        bounceC += colNow * wb;
        bounceW += wb;
        // direct light: only if no wall stands in between and the vanilla level says the path is free
        if (L.walled) continue;
        bool open = openAir && !L.covered;
        float vis = lampVis(X, A, open, emitterIsScreen(L.type));
        if (vis <= 0.0) continue;
        vec3 d = L.pos - wp;
        float dist = length(d);
        vec3 l = d / max(dist, 1e-3);
        float fall = lampFalloff(L, dist, open) * lampPattern(L.type, -l) * vis;
        if (fall <= 0.0) continue;
        // the street lamp outside, glowing in through drawn curtains
        if (!L.covered && !openAir) fall *= curtainTransmission(wp + N * 0.05, L.pos);
        float ndl = dot(N, l);
        float front = ndl >= 0.0 ? ndl * 0.92 + 0.08 * saturate(ndl * 8.0) : backK * (-ndl);
        float lambert = mix(front, 0.35 + 0.65 * abs(ndl), twoSided);
        vec3 E = rad * fall;
        diffuse += E * lambert;
        if (f0 >= 0.0 && ndl > 0.0) {
            float sizeAngle = EM_SIZE[clamp(L.type, 0, EM_COUNT - 1)] / max(dist, 0.3);
            spec += E * ggxSpec(N, V, l, rough, sizeAngle) * fresnelSchlick(f0, dot(normalize(l + V), V));
        }
        float w = maxOf(E) * saturate(ndl);
        if (w > best) {
            best = w;
            domPos = L.pos;
        }
    }
    float keepBounce = 1.0 - 0.88 * enclosed;
    // light that went round corners and off the walls: coloured by the lamp it came from
    float lv = A / 15.0;
    float bounceF = 0.10 * lv * lv + 0.010 * lv;
    vec3 bcol = bounceW > 1e-3 ? bounceC / bounceW : vec3(0.80, 0.80, 0.78);
    diffuse += bcol * bounceF * LIGHT_STRENGTH * keepBounce;
    // light that no lamp explains: vanilla light from something the pack does not draw (an
    // invisible light block, the torch of a player without shaders): a soft fill from above,
    // as bright as its level says
    float unexplained = smoothstep(1.0, 3.0, A - Xmax);
    float la = A / 15.0;
    diffuse += vec3(1.00, 0.88, 0.74) * (0.75 * la * la) * unexplained * (0.70 + 0.30 * N.y) * LIGHT_STRENGTH * fillK * keepBounce;
    domW = best / max(maxOf(diffuse), 1e-4);
}

// ---- the lamps in the air -----------------------------------------------------------------
// Light scattered toward the eye by the air along a view ray (camera relative, unit rd).
// The lamps are gathered ONCE per ray: the outdoor ones from the frame's own list (prepare1:
// the same for every pixel - lists that change from ray to ray drew walls and wedges of light),
// the indoor ones from the table at eight columns along the ray, where walls are known, each
// faded out before it would drop out of those columns' lists. airLampsSegment() integrates the
// inverse-square falloff of every gathered lamp analytically over one segment.
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

// a sodium head is a small, hot source above the street: in the rain haze it makes a halo, it
// does not light up the whole sky (its in-scatter is scaled down from its surface light)
const float SODIUM_AIR = 0.12;
void airAdd(int slot, Lamp L, vec3 rd, float tEnd, float w, float gainMul) {
    int ti = clamp(L.type, 0, EM_COUNT - 1);
    vec3 o = L.pos - cameraPosition;
    float tc = dot(o, rd);
    vec3 toClose = rd * clamp(tc, 0.0, tEnd) - o;
    airBlock[slot] = L.block;
    airRad[slot] = lampRadiance(L) * gainMul * lampPatternAir(L.type, toClose / max(length(toClose), 1e-3))
                 * (L.type == EM_STREET ? SODIUM_AIR : 1.0);
    airW[slot] = w;
    airTc[slot] = tc;
    airH2[slot] = max(dot(o, o) - tc * tc, 0.0);
    airS2[slot] = sq(EM_SIZE[ti]) + 0.12 + (gainMul > 1.0 ? 0.6 : 0.0);
    airReach[slot] = L.level + 1.0 + (gainMul > 1.0 ? 2.0 : 0.0);
    airCovered[slot] = L.covered;
}

void airLampsGather(vec3 rd, float tEnd) {
    airN = 0;
    for (int i = 0; i < AIR_LAMPS; i++) {
        airBlock[i] = vec3(-1e4);
        airRad[i] = vec3(0.0);
        airW[i] = 0.0;
        airTc[i] = 0.0;
        airH2[i] = 1e4;
        airS2[i] = 1.0;
        airReach[i] = 0.0;
        airCovered[i] = true;
    }
    // ---- outdoors: the frame's list (the same for every pixel: no seams) ------------------
    ivec2 org = emapOrigin();
    float refY = float(tableRefY());
    for (int i = 0; i < AIR_OUT; i++) {
        vec4 a = texelFetch(colortex7, ivec2(AIR_LIST_X + i, 0), 0);
        if (a.w < 0.5) break;                            // packed: the rest is empty
        int type = int(a.w + 0.5);
        vec4 b = texelFetch(colortex7, ivec2(AIR_LIST_X + AIR_OUT + i, 0), 0);
        if (b.z <= 0.0 || airN >= AIR_LAMPS) continue;
        Lamp L;
        L.type = type;
        L.level = b.y;
        L.covered = false;
        L.walled = false;
        L.block = vec3(float(org.x) + a.x, refY + a.y, float(org.y) + a.z);
        L.pos = lampOrigin(type, L.block);
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
                float imp = w * lampGain(L.type) / (max(dot(o, o) - tc * tc, 0.0) + 1.0);
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

// irradiance of the frame's outdoor lamps at a point in the open air (the rain falling through
// the street lamp's light). wp: world position
vec3 outdoorLampsAt(vec3 wp) {
    vec3 S = vec3(0.0);
    ivec2 org = emapOrigin();
    float refY = float(tableRefY());
    for (int i = 0; i < AIR_OUT; i++) {
        vec4 a = texelFetch(colortex7, ivec2(AIR_LIST_X + i, 0), 0);
        if (a.w < 0.5) break;
        int type = int(a.w + 0.5);
        vec4 b = texelFetch(colortex7, ivec2(AIR_LIST_X + AIR_OUT + i, 0), 0);
        if (b.z <= 0.0) continue;
        Lamp L;
        L.type = type;
        L.level = b.y;
        L.covered = false;
        L.walled = false;
        L.block = vec3(float(org.x) + a.x, refY + a.y, float(org.y) + a.z);
        L.pos = lampOrigin(type, L.block);
        vec3 d = wp - L.pos;
        float dist = length(d);
        float reach = L.level + 1.0 + OUTDOOR_REACH;
        if (dist > reach) continue;
        S += lampRadiance(L) * max(b.x, 1.0) * b.z * lampPatternAir(type, d / max(dist, 1e-3))
           * (1.0 - smoothstep(reach * 0.5, reach, dist)) / (dist * dist + 0.4);
    }
    return S;
}
