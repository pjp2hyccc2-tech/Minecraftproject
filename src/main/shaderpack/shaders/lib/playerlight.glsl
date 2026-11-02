// =====================================================================================
//  SounanShaders - the lights the player carries, in either hand
//    heldItemId  (main hand, drawn on the right)   heldItemId2 (off hand, on the left)
//  flashlight / low flashlight / phone light / phone camera IR: spot lights from the hand,
//  slightly below and to the side of the eye, aimed along the view; the flare: a red point
//  light. The same functions light opaque surfaces (deferred), translucents (forward), the
//  entity eyes (eyeshine) and the fog (composite), so every pass sees the same beam.
// =====================================================================================

// item.properties (heldItemId / heldItemId2 / currentRenderedItemId)
#define ITEM_FLASHLIGHT   10001   // sounan:flashlight_on
#define ITEM_FLASH_LOW    10002   // sounan:flashlight_low   (dying battery: dim, orange, stutters)
#define ITEM_PHONE_LIGHT  10003   // sounan:phone_light      (wide, weak, cold)
#define ITEM_CAMERA       10004   // sounan:phone_camera     (night-vision camcorder + IR illuminator)
#define ITEM_FLARE        10005   // sounan:flare_lit        (flickering red point light)
#define ITEM_HANDLIGHT    10010   // vanilla torches / lanterns held in a hand

#define LIGHT_NONE  0
#define LIGHT_SPOT  1
#define LIGHT_POINT 2

// colours (linear)
const vec3 FLASHLIGHT_COLOR = vec3(1.00, 0.83, 0.66);   // ~4300 K: warm white
const vec3 FLASH_LOW_COLOR  = vec3(1.00, 0.52, 0.20);   // a dying battery: dim, orange
const vec3 PHONE_COLOR      = vec3(0.80, 0.89, 1.00);   // cold white LED
const vec3 FLARE_COLOR      = vec3(1.00, 0.085, 0.030); // road flare
const vec3 IR_COLOR         = vec3(1.00, 1.00, 1.00);   // only exists in the camera image

struct HandLight {
    int   type;      // LIGHT_NONE / LIGHT_SPOT / LIGHT_POINT
    vec3  pos;       // player space (camera-relative, world aligned)
    vec3  dir;       // beam axis, player space, unit
    vec3  right;     // cone cross-section basis (lens pattern)
    vec3  up;
    vec3  color;     // linear intensity incl. flicker
    float tanOuter;  // tan of the cone half angle
    float range;     // light ends here
    float halfDist;  // distance at which the light has fallen to one half
    float hot;       // hot spot width, fraction of the cone
    float cookie;    // lens / reflector pattern strength
    float fog;       // scattering strength in the fog
};

// ---- camera mode ----------------------------------------------------------------------
bool holdsCamera() { return heldItemId == ITEM_CAMERA || heldItemId2 == ITEM_CAMERA; }
float cameraMode() {
#ifdef WORLD_MOUNTAIN
    return (holdsCamera() || nightVision > 0.0) ? 1.0 : 0.0;
#else
    return holdsCamera() ? 1.0 : 0.0;
#endif
}

bool isLightItem(int id) {
    return id == ITEM_FLASHLIGHT || id == ITEM_FLASH_LOW || id == ITEM_PHONE_LIGHT
        || id == ITEM_CAMERA || id == ITEM_FLARE || id == ITEM_HANDLIGHT;
}
bool anyHandLight() { return isLightItem(heldItemId) || isLightItem(heldItemId2); }

// ---- flicker --------------------------------------------------------------------------
// danger > ~0.35: electrical dropouts in bursts, a faint buzz
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
    float f = mix(1.0, level, drop);
    return f * (1.0 - 0.10 * gate * (0.5 + 0.5 * sin(t * TAU * 29.0)));
}
// a dying battery: the light sags and stutters irregularly, sometimes nearly dies
float lowBatteryStutter(float t) {
    float sag = 0.70 + 0.30 * vnoise1(t * 1.3);
    float slot = floor(t * 11.0);
    float dip = step(0.80, hash11(slot * 2.17 + 5.0)) * (0.35 + 0.55 * hash11(slot * 3.3 + 1.0));
    float outage = smoothstep(0.84, 0.93, vnoise1(t * 0.45 + 9.0));
#ifdef FLASH_REDUCTION
    dip *= 0.4;
    outage *= 0.5;
#endif
    return sag * (1.0 - dip) * (1.0 - 0.88 * outage);
}
// the flare: violent, fast sputter
float flareFlicker(float t) {
    float n = vnoise1(t * 9.0) * 0.45 + vnoise1(t * 23.0 + 4.0) * 0.30 + vnoise1(t * 3.1 + 8.0) * 0.25;
#ifdef FLASH_REDUCTION
    n = 0.5 + (n - 0.5) * 0.4;
#endif
    return 0.70 + 0.60 * n;
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

// ---- the light held in one hand ---------------------------------------------------------
HandLight makeHandLight(int item, float side) {
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
    L.cookie = 0.0;
    L.fog = 0.0;

    float t = frameTimeCounter;
    vec3 originV = vec3(0.0);
    float aim = 12.0;
    if (item == ITEM_FLASHLIGHT || item == ITEM_FLASH_LOW) {
        L.type = LIGHT_SPOT;
        originV = vec3(0.30 * side, -0.27, -0.16);
        aim = 14.0;
        L.tanOuter = 0.466;                     // ~25 degree half angle
        L.range = 30.0;
        L.halfDist = 6.0;
        L.hot = 0.34;
        L.cookie = 1.0;
        L.fog = 1.0;
        float f = dangerFlicker(t);
        if (item == ITEM_FLASHLIGHT) {
            L.color = FLASHLIGHT_COLOR * 14.0 * f;
        } else {
            L.color = FLASH_LOW_COLOR * 5.0 * f * lowBatteryStutter(t);
            L.hot = 0.27;
            L.range = 18.0;
            L.halfDist = 3.2;
        }
        L.color *= BEAM_STRENGTH;
    } else if (item == ITEM_PHONE_LIGHT) {
        L.type = LIGHT_SPOT;
        originV = vec3(0.20 * side, -0.21, -0.30);
        aim = 8.0;
        L.tanOuter = 0.84;                      // ~40 degrees: wide and weak
        L.range = 12.0;
        L.halfDist = 1.7;
        L.hot = 0.65;
        L.fog = 0.7;
        L.color = PHONE_COLOR * 3.2 * mix(1.0, dangerFlicker(t), 0.6) * BEAM_STRENGTH;
    } else if (item == ITEM_CAMERA) {
        L.type = LIGHT_SPOT;                    // the invisible infrared flood of the camera
        originV = vec3(0.16 * side, -0.14, -0.34);
        aim = 10.0;
        L.tanOuter = 0.90;                      // ~42 degrees
        L.range = 26.0;
        L.halfDist = 3.4;
        L.hot = 0.75;
        L.fog = 0.55;
        L.color = IR_COLOR * 2.6 * mix(1.0, dangerFlicker(t), 0.5);
    } else if (item == ITEM_FLARE) {
        L.type = LIGHT_POINT;
        originV = vec3(0.34 * side, -0.40, -0.42);
        L.range = 16.0;
        L.halfDist = 2.3;
        L.fog = 1.0;
        L.color = FLARE_COLOR * 7.0 * flareFlicker(t);
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
    vec2 wob = handWobble(side);
    vec3 dirV = normalize(vec3(wob * aim, -aim) - originV);
    vec3 rightV = normalize(cross(dirV, vec3(0.0, 1.0, 0.0)));
    vec3 upV = cross(rightV, dirV);
    mat3 toPlayer = mat3(gbufferModelViewInverse);
    L.pos = toPlayer * originV;
    L.dir = toPlayer * dirV;
    L.right = toPlayer * rightV;
    L.up = toPlayer * upV;
    return L;
}

// slot 0 = main hand (right), slot 1 = off hand (left)
HandLight handLight(int slot) {
    return slot == 0 ? makeHandLight(heldItemId, 1.0) : makeHandLight(heldItemId2, -1.0);
}

// ---- beam shape -------------------------------------------------------------------------
// distance falloff: half intensity at L.halfDist, gone at L.range
float lightFalloff(HandLight L, float d) {
    float a = 1.0 / (1.0 + sq(d / L.halfDist));
    return a * (1.0 - smoothstep(L.range * 0.55, L.range, d));
}

// angular profile of a spot. toP: unit vector from the light to the point.
// Hot spot, soft body, faint spill; the flashlight adds its reflector rings and (detail)
// lens dirt.
float spotProfileD(HandLight L, vec3 toP, bool detail) {
    float c = dot(toP, L.dir);
    if (c <= 0.02) return 0.0;
    vec2 q = vec2(dot(toP, L.right), dot(toP, L.up)) / (c * L.tanOuter);   // 1 = cone edge
    float x = length(q);
    if (x > 1.8) return 0.0;
    float hot = exp(-sq(x / L.hot));
    float body = 1.0 - smoothstep(0.45, 1.0, x);
    float halo = 1.0 - smoothstep(0.9, 1.8, x);
    float p = hot * 0.85 + body * 0.32 + halo * 0.03;
#ifdef LENS_COOKIE
    if (L.cookie > 0.0) {
        // a darker ring around the hot spot, a brighter rim where the reflector ends,
        // fine concentric ripples and a smudge of dirt on the lens
        float rings = -0.32 * exp(-sq((x - 0.42) * 8.0)) + 0.24 * exp(-sq((x - 0.88) * 11.0))
                    + 0.05 * sin(x * 37.0);
        float dirt = detail ? vnoise2(q * 3.2 + 7.0) - 0.5 : 0.0;
        p *= 1.0 + L.cookie * (rings * (1.0 - smoothstep(0.75, 1.15, x)) + dirt * 0.24);
    }
#endif
    return max(p, 0.0);
}
float spotProfile(HandLight L, vec3 toP) { return spotProfileD(L, toP, true); }

// intensity (colour) of the light arriving at a point, before the surface cosine
vec3 handLightReach(HandLight L, vec3 P, out vec3 l, out float dist) {
    vec3 d = L.pos - P;
    dist = length(d);
    l = d / max(dist, 1e-4);
    if (L.type == LIGHT_NONE || dist > L.range) return vec3(0.0);
    float k = lightFalloff(L, dist);
    if (L.type == LIGHT_SPOT) k *= spotProfile(L, -l);
    return L.color * k;
}

// light reflected by a surface: diffuse (times albedo by the caller) and specular.
// N: world normal, foliage: 1 = thin two-sided needles / leaves, gloss: surfaceGloss()
vec3 handLightSurface(HandLight L, vec3 P, vec3 N, float foliage, vec2 gloss, out vec3 spec) {
    spec = vec3(0.0);
    vec3 l;
    float dist;
    vec3 E = handLightReach(L, P, l, dist);
    if (maxOf(E) <= 0.0) return vec3(0.0);
    float ndl = dot(N, l);
    float diff = foliage > 0.5 ? 0.30 + 0.70 * abs(ndl) : max(ndl, 0.0);
    if (gloss.x > 0.0 && ndl > 0.0) {
        vec3 V = -P / max(length(P), 1e-4);
        vec3 H = normalize(l + V);
        float e = exp2(4.0 + gloss.y * 7.0);                  // 16 .. 2048
        float s = pow(saturate(dot(N, H)), e) * 0.04 * (e + 2.0) / 8.0;
        spec = E * s * gloss.x * saturate(ndl * 3.0);
    }
    return E * diff;
}

// retro-reflection of a beam in an eye (eyeshine): how much beam light reaches P,
// with a gentle distance falloff (a retro-reflector sends the light back to its source)
vec3 handLightEyeshine(vec3 P) {
    vec3 sum = vec3(0.0);
    for (int i = 0; i < 2; i++) {
        HandLight L = handLight(i);
        if (L.type != LIGHT_SPOT) continue;
        vec3 d = P - L.pos;
        float dist = length(d);
        float prof = spotProfile(L, d / max(dist, 1e-4));
        float fall = 1.0 / (1.0 + dist * 0.06);
        sum += L.color * prof * fall * (1.0 - smoothstep(L.range * 0.8, L.range * 1.6, dist));
    }
    return sum;
}
