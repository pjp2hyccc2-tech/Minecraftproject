// =====================================================================================
//  HitoriShaders - the lights the player carries, and the camera's IR illuminator
//    slot 0: main hand (heldItemId, drawn on the right)   slot 1: off hand (heldItemId2)
//    slot 2: the IR illuminator of the fixed camera whose feed the camera app shows
//            (hitori:shirakaba, nightVision > 0): the hand lights are then far away with the
//            player's body - only the camera's own IR LEDs light what it sees
//  The phone's flashlight is a weak, wide, cool LED cone from the hand at chest height; the
//  lighter a small warm flame that never holds still; the laptop's and the phone's screens a
//  soft cool glow toward the player (the closet's walls, the fusuma, the futon above). The same
//  functions light opaque surfaces (deferred), translucents (forward), the eyes layers and the
//  air (composite), so every pass sees the same light.
// =====================================================================================

// item.properties (heldItemId / heldItemId2 / currentRenderedItemId)
#define ITEM_PHONE        10001   // hitori:phone - its screen glows a little
#define ITEM_PHONE_LIGHT  10002   // hitori:phone_light - the flashlight on (the mod swaps the item)
#define ITEM_LIGHTER      10003   // hitori:lighter - a small warm flame
#define ITEM_LAPTOP       10004   // hitori:laptop - open: the screen glows
#define ITEM_KNIFE        10005   // hitori:knife - no light; the blade catches the light (hand)
#define ITEM_HANDLIGHT    10010   // vanilla torches / lanterns held in a hand

#define LIGHT_NONE   0
#define LIGHT_SPOT   1    // the phone's flashlight
#define LIGHT_POINT  2    // a flame
#define LIGHT_SCREEN 3    // a screen held toward the player (laptop, phone)
#define LIGHT_IR     4    // the fixed camera's IR LEDs round its lens

// colours (linear)
const vec3 PHONE_LED_COLOR = vec3(0.86, 0.93, 1.00);    // ~6000 K white LED
const vec3 SCREEN_COLOR    = vec3(0.64, 0.79, 1.00);    // a cold LCD
const vec3 IR_COLOR        = vec3(1.00, 1.00, 1.00);    // (monochrome on the feed)

struct HandLight {
    int   type;      // LIGHT_*
    vec3  pos;       // player space (camera-relative, world aligned)
    vec3  dir;       // beam axis / screen normal, player space, unit
    vec3  right;     // cone cross-section basis
    vec3  up;
    vec3  color;     // linear intensity incl. flicker
    float tanOuter;  // tan of the cone half angle
    float range;     // light ends here
    float halfDist;  // distance at which the light has fallen to one half
    float hot;       // hot spot width, fraction of the cone
    float fog;       // scattering strength in the air
};

bool isLightItem(int id) {
    return id == ITEM_PHONE || id == ITEM_PHONE_LIGHT || id == ITEM_LIGHTER || id == ITEM_LAPTOP || id == ITEM_HANDLIGHT;
}
bool anyHandLight() {
    if (cctv() > 0.5) return true;
    return isLightItem(heldItemId) || isLightItem(heldItemId2);
}

// ---- flicker --------------------------------------------------------------------------
// danger > ~0.35: the phone's LED drops out in bursts (the doll is near)
float dangerFlicker(float t) {
    float amount = smoothstep(0.35, 1.0, danger());
    if (amount <= 0.0) return 1.0;
    float burst = smoothstep(0.30, 0.70, vnoise1(t * 0.85 + 3.7));
    float gate = amount * (0.30 + 0.70 * burst);
    float slot = floor(t * 17.0);
    float drop = step(1.0 - 0.55 * gate, hash11(slot * 1.733 + 0.17));
    float level = 0.03 + 0.42 * hash11(slot * 4.13 + 1.3);
#ifdef FLASH_REDUCTION
    drop *= step(0.6, hash11(slot * 0.77 + 9.1));
    level = max(level, 0.45);
#endif
    return mix(1.0, level, drop);
}

// a lighter's flame: it leans, gutters, jumps with every breath
float lighterFlicker(float t) {
    float n = vnoise1(t * 9.1) * 0.45 + vnoise1(t * 23.0 + 4.0) * 0.30 + vnoise1(t * 3.3 + 9.0) * 0.25;
    float gutter = smoothstep(0.80, 1.0, vnoise1(t * 1.1 + 2.0)) * 0.35;
    float f = 0.72 + 0.48 * n - gutter;
#ifdef FLASH_REDUCTION
    f = mix(0.85, f, 0.45);
#endif
    return f;
}

// shaking hands: idle sway + a tremor that grows with fear (offset of the aim, radians)
vec2 handWobble(float side) {
    float t = frameTimeCounter;
    float F = fear();
    vec2 sway = vec2(sin(t * 0.83 + side), sin(t * 1.13 + 2.0 * side)) * 0.0045;
    vec2 tremor = vec2(vnoise1(t * 7.3 + side * 5.0), vnoise1(t * 6.1 + 11.0 + side * 3.0)) - 0.5;
    tremor += (vec2(vnoise1(t * 15.7 + 3.0), vnoise1(t * 13.9 + 7.0)) - 0.5) * 0.6;
    float amp = 0.002 + 0.030 * F * F;
#ifdef FLASH_REDUCTION
    amp *= 0.4;
#endif
    return sway * (1.0 + F) + tremor * amp;
}

HandLight emptyLight() {
    HandLight L;
    L.type = LIGHT_NONE;
    L.pos = vec3(0.0);
    L.dir = vec3(0.0, 0.0, -1.0);
    L.right = vec3(1.0, 0.0, 0.0);
    L.up = vec3(0.0, 1.0, 0.0);
    L.color = vec3(0.0);
    L.tanOuter = 0.5;
    L.range = 1.0;
    L.halfDist = 1.0;
    L.hot = 0.3;
    L.fog = 0.0;
    return L;
}

// ---- the light held in one hand ---------------------------------------------------------
HandLight makeHandLight(int item, float side) {
    HandLight L = emptyLight();
    float t = frameTimeCounter;
    vec3 originV = vec3(0.0);
    float aim = 12.0;
    vec3 dirV = vec3(0.0, 0.0, -1.0);
    if (item == ITEM_PHONE_LIGHT) {
        // the phone held at chest height, its LED beside the camera lens: wide, soft, weak
        L.type = LIGHT_SPOT;
        originV = vec3(0.22 * side, -0.30, -0.32);
        aim = 9.0;
        L.tanOuter = 0.80;                      // ~39 degree half angle
        L.range = 16.0;
        L.halfDist = 2.0;
        L.hot = 0.46;
        L.fog = 1.0;
        L.color = PHONE_LED_COLOR * 2.6 * dangerFlicker(t) * BEAM_STRENGTH;
    } else if (item == ITEM_LIGHTER) {
        L.type = LIGHT_POINT;
        originV = vec3(0.24 * side, -0.20, -0.42);
        L.range = 7.0;
        L.halfDist = 0.95;
        L.fog = 0.35;
        L.color = vec3(1.00, 0.56, 0.24) * 1.35 * lighterFlicker(t) * FLAME_GAIN;
    } else if (item == ITEM_LAPTOP || item == ITEM_PHONE) {
        // a screen held in front, facing the player: its glow falls back toward the eye and
        // round the cramped space
        bool laptop = item == ITEM_LAPTOP;
        L.type = LIGHT_SCREEN;
        originV = laptop ? vec3(0.0, -0.40, -0.48) : vec3(0.20 * side, -0.30, -0.34);
        L.range = laptop ? 5.0 : 2.6;
        L.halfDist = laptop ? 0.85 : 0.45;
        L.fog = 0.15;
        float content = 0.92 + 0.08 * vnoise1(t * 2.7 + side);
        L.color = SCREEN_COLOR * (laptop ? 1.10 : 0.35) * content;
        dirV = normalize(vec3(0.0, 0.25, 1.0));  // toward the face
    } else if (item == ITEM_HANDLIGHT) {
        L.type = LIGHT_POINT;
        originV = vec3(0.30 * side, -0.32, -0.36);
        L.range = 11.0;
        L.halfDist = 1.6;
        L.fog = 0.4;
        float lvl = float(side > 0.0 ? heldBlockLightValue : heldBlockLightValue2) / 15.0;
        L.color = FLAME_HOT * 1.6 * max(lvl, 0.6) * FLAME_GAIN;
    } else {
        return L;
    }
    if (L.type == LIGHT_SPOT) {
        vec2 wob = handWobble(side);
        dirV = normalize(vec3(wob * aim, -aim) - originV);
    }
    vec3 rightV = normalize(cross(dirV, abs(dirV.y) > 0.95 ? vec3(1.0, 0.0, 0.0) : vec3(0.0, 1.0, 0.0)));
    vec3 upV = cross(rightV, dirV);
    mat3 toPlayer = mat3(gbufferModelViewInverse);
    L.pos = toPlayer * originV;
    L.dir = toPlayer * dirV;
    L.right = toPlayer * rightV;
    L.up = toPlayer * upV;
    return L;
}

// a camera at night: when its room is dark (the vanilla block light at the camera, smoothed like
// the eye's adaptation - Iris follows the camera entity) it drops its IR-cut filter, lights the
// room with its IR LEDs and sees in monochrome; with the lights on it sees in (dull) colour
float cctvIrMode() {
    return cctv() * (1.0 - smoothstep(8.5, 11.0, float(eyeBrightnessSmooth.x) / 16.0));
}

// the fixed camera's IR illuminator: a ring of LEDs round the lens - the scene lit from the
// viewpoint, bright close by, a hot spot in the middle, black beyond a few metres
HandLight makeIrLight() {
    HandLight L = emptyLight();
    if (cctvIrMode() < 0.02) return L;
    L.type = LIGHT_IR;
    mat3 toPlayer = mat3(gbufferModelViewInverse);
    L.pos = toPlayer * vec3(0.0, 0.03, 0.0);
    L.dir = toPlayer * vec3(0.0, 0.0, -1.0);
    L.right = toPlayer * vec3(1.0, 0.0, 0.0);
    L.up = toPlayer * vec3(0.0, 1.0, 0.0);
    L.tanOuter = 1.05;
    L.range = 16.0;
    L.halfDist = 2.6;
    L.hot = 0.55;
    L.fog = 0.0;
    L.color = IR_COLOR * 1.3 * cctvIrMode();
    return L;
}

// slot 0 = main hand (right), 1 = off hand (left), 2 = the IR illuminator
HandLight handLight(int slot) {
    if (slot == 2) return makeIrLight();
    if (cctv() > 0.5) return emptyLight();      // the hands are with the body, not at the camera
    return slot == 0 ? makeHandLight(heldItemId, 1.0) : makeHandLight(heldItemId2, -1.0);
}
#define HAND_LIGHTS 3

// ---- beam shape -------------------------------------------------------------------------
// distance falloff: half intensity at L.halfDist, gone at L.range
float lightFalloff(HandLight L, float d) {
    float a = 1.0 / (1.0 + sq(d / L.halfDist));
    return a * (1.0 - smoothstep(L.range * 0.55, L.range, d));
}

// angular profile. toP: unit vector from the light to the point.
float spotProfile(HandLight L, vec3 toP) {
    float c = dot(toP, L.dir);
    if (L.type == LIGHT_SCREEN) {
        // the screen's own lobe toward the player, and what the walls of a small space send back
        return 0.30 + 0.70 * saturate(c * 0.8 + 0.2);
    }
    if (L.type != LIGHT_SPOT && L.type != LIGHT_IR) return 1.0;
    if (c <= 0.02) return 0.0;
    vec2 q = vec2(dot(toP, L.right), dot(toP, L.up)) / (c * L.tanOuter);   // 1 = cone edge
    float x = length(q);
    if (x > 1.9) return 0.0;
    float hot = exp(-sq(x / L.hot));
    float body = 1.0 - smoothstep(0.40, 1.0, x);
    float halo = 1.0 - smoothstep(0.9, 1.9, x);
    if (L.type == LIGHT_IR) return hot * 0.75 + body * 0.45 + halo * 0.06;
    // a phone LED: a soft hot spot, a broad body, a faint wide halo
    return hot * 0.70 + body * 0.40 + halo * 0.05;
}

// intensity (colour) of the light arriving at a point, before the surface cosine
vec3 handLightReach(HandLight L, vec3 P, out vec3 l, out float dist) {
    vec3 d = L.pos - P;
    dist = length(d);
    l = d / max(dist, 1e-4);
    if (L.type == LIGHT_NONE || dist > L.range) return vec3(0.0);
    float k = lightFalloff(L, dist) * spotProfile(L, -l);
    vec3 c = L.color;
    if (L.type == LIGHT_SPOT) {
        // the phosphor's faint yellow ring at the edge of a white LED's cone
        float edge = smoothstep(0.55, 0.95, length(vec2(dot(-l, L.right), dot(-l, L.up))) / max(dot(-l, L.dir) * L.tanOuter, 1e-3));
        c *= mix(vec3(1.0), vec3(1.06, 1.0, 0.82), edge);
    }
    return c * k;
}

// light reflected by a surface: diffuse (times albedo by the caller) and specular.
// N: world normal, foliage: 1 = thin two-sided leaves, gloss: x roughness, y f0 (< 0: none)
vec3 handLightSurface(HandLight L, vec3 P, vec3 N, float foliage, vec2 gloss, out vec3 spec) {
    spec = vec3(0.0);
    vec3 l;
    float dist;
    vec3 E = handLightReach(L, P, l, dist);
    if (maxOf(E) <= 0.0) return vec3(0.0);
    float ndl = dot(N, l);
    float diff = foliage > 0.5 ? 0.30 + 0.70 * abs(ndl) : max(ndl, 0.0);
    if (gloss.y >= 0.0 && ndl > 0.0) {
        vec3 V = -P / max(length(P), 1e-4);
        spec = E * ggxSpec(N, V, l, gloss.x, 0.02 / max(dist, 0.3)) * fresnelSchlick(gloss.y, dot(normalize(l + V), V));
    }
    return E * diff;
}

// retro-reflection of a beam in an eye (eyeshine, the doll's button eyes): how much light of the
// lights that shine from near the eye (the phone's LED, the IR ring) reaches P
vec3 handLightEyeshine(vec3 P) {
    vec3 sum = vec3(0.0);
    for (int i = 0; i < HAND_LIGHTS; i++) {
        HandLight L = handLight(i);
        if (L.type != LIGHT_SPOT && L.type != LIGHT_IR) continue;
        vec3 d = P - L.pos;
        float dist = length(d);
        float prof = spotProfile(L, d / max(dist, 1e-4));
        float fall = 1.0 / (1.0 + dist * 0.12);
        sum += L.color * prof * fall * (1.0 - smoothstep(L.range * 0.8, L.range * 1.6, dist));
    }
    return sum;
}
