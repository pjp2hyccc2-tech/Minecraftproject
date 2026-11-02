// =====================================================================================
//  SounanShaders - ids from block.properties / entity.properties, emission masks,
//  surface gloss and the wind in the canopy
//  (item ids live in lib/playerlight.glsl)
// =====================================================================================

// block.properties (mc_Entity.x)
#define BLK_FLAME     10001   // torches, candles, campfires, the hokora candle (bright texels glow)
#define BLK_LAMP      10002   // lanterns, the lit sodium tunnel lamp (the glass glows)
#define BLK_GLOW      10003   // glowstone, sea lantern, froglights, magma ... (whole block glows)
#define BLK_LAVA      10004
#define BLK_PLANT     10010   // ferns, grass, dead bushes: top vertices sway
#define BLK_LEAVES    10011   // cedar needles (and vanilla leaves): the canopy sways
#define BLK_HANGING   10012   // vines: bottom vertices sway
#define BLK_WATER     10020
#define BLK_GLASS     10021   // glass, ice, stained glass
#define BLK_WET       10030   // blood stains, footprints: wet gloss
#define BLK_METAL     10031   // wet tin roofs, guardrail, steel lattice, iron gate, the car: glossy
#define BLK_ROCK      10032   // wet andesite, river stones, tunnel concrete, asphalt: faint gloss
#define BLK_RIBBON    10033   // pink marking tape around trunks: plastic sheen on the pink texels
#define BLK_TARP      10034   // blue tarp: plastic sheen

// entity.properties (entityId)
#define ENT_NOZOKI     20001
#define ENT_YOBIGOE    20002   // the "rescuer": its eyes layer is a headlamp
#define ENT_USHIRO     20003
#define ENT_KOYANUSHI  20004
#define ENT_OYAMA      20005
#define ENT_UTSURU     20006   // translucent hiker, only the phone camera sees it
#define ENT_LIGHTNING  20010

// emissive mask from texture colour: bright / saturated texels glow (flames, lit glass),
// dark handles, frames and wicks do not
float flameMask(vec3 albedoSrgb) {
    float l = luma(albedoSrgb);
    float sat = maxOf(albedoSrgb) - minOf(albedoSrgb);
    return smoothstep(0.42, 0.80, l + sat * 0.45);
}
float paperMask(vec3 albedoSrgb) {
    return smoothstep(0.30, 0.62, luma(albedoSrgb));
}
// the faded pink marking tape (high red, mid blue, lower green)
float pinkMask(vec3 albedoSrgb) {
    float pink = min(albedoSrgb.r - albedoSrgb.g, albedoSrgb.b - albedoSrgb.g * 0.85);
    return smoothstep(0.04, 0.16, pink) * smoothstep(0.25, 0.45, albedoSrgb.r);
}

// specular response of a surface to the hand lights: x = strength, y = smoothness (0..1)
vec2 surfaceGloss(int mat, vec3 albedoSrgb) {
    if (mat == BLK_WET)    return vec2(1.00, 0.85);
    if (mat == BLK_METAL)  return vec2(0.70, 0.70);
    if (mat == BLK_ROCK)   return vec2(0.32, 0.55);
    if (mat == BLK_RIBBON) return vec2(0.55 * pinkMask(albedoSrgb), 0.50);
    if (mat == BLK_TARP)   return vec2(0.35, 0.45);
    if (mat == BLK_LEAVES) return vec2(0.10, 0.35);   // damp needles: a faint sheen in the beam
    return vec2(0.0);
}

// ---- wind ------------------------------------------------------------------------------
#if defined VERTEX_SHADER && defined WAVING_PLANTS
// slow swells of wind high in the canopy; low plants only stir
float windSwell(vec3 wp) {
#ifdef WORLD_MOUNTAIN
    float s = vnoise1(frameTimeCounter * 0.075 + wp.x * 0.006 + wp.z * 0.004);
    return 0.25 + 0.75 * smoothstep(0.25, 0.85, s);
#else
    return 1.0 + rainAmount() * 1.2;
#endif
}

vec3 windSway(vec3 wp, float amp) {
    float t = frameTimeCounter;
    amp *= windSwell(wp);
    // the wind comes down the mountain (from the north, -Z)
    float a = sin(t * 1.10 + wp.x * 0.60 + wp.z * 0.40) * 0.55 + sin(t * 2.10 + wp.z * 1.20 - wp.x * 0.80) * 0.30 + 0.35;
    float b = sin(t * 0.90 - wp.z * 0.55 + wp.x * 0.30) * 0.60 + sin(t * 2.60 - wp.x * 1.10 + wp.y * 0.70) * 0.40;
    return vec3(b * 0.6, 0.0, a) * amp;
}

// wp: world position of the vertex, uv / midUv: atlas coordinates (top / bottom test)
vec3 wavingOffset(int id, vec3 wp, vec2 uv, vec2 midUv, float skyLight) {
    float exposed = 0.25 + 0.75 * skyLight;
    if (id == BLK_PLANT) {
        return uv.y < midUv.y ? windSway(wp, 0.06 * exposed) : vec3(0.0);
    }
    if (id == BLK_LEAVES) {
        return windSway(wp, 0.045 * exposed);
    }
    if (id == BLK_HANGING) {
        return uv.y > midUv.y ? windSway(wp, 0.05) : vec3(0.0);
    }
    return vec3(0.0);
}
#endif
