// =====================================================================================
//  MeikonShaders - ids from block.properties / item.properties / entity.properties,
//  emission masks and gentle waving
// =====================================================================================

// block.properties (mc_Entity.x)
#define BLK_FLAME     10001   // torches, candles, campfires, fire, shokudai candle_stand (bright texels glow)
#define BLK_LAMP      10002   // lanterns, andon (the paper / glass glows)
#define BLK_GLOW      10003   // glowstone, sea lantern, froglights, magma ... (whole block glows)
#define BLK_LAVA      10004
#define BLK_TORII     10005   // en-giri torii_gate portal (translucent, sacred glow)
#define BLK_BELL      10006   // suzu_dai: the golden bell glints in the dark
#define BLK_PLANT     10010   // ground plants: top vertices sway
#define BLK_LEAVES    10011   // leaves: whole block sways a little
#define BLK_HANGING   10012   // shimenawa, vines: bottom vertices sway
#define BLK_WATER     10020
#define BLK_GLASS     10021   // glass, ice, stained glass
#define BLK_WET       10030   // blood stains, wet footprints: glisten in lantern light
#define BLK_LACQUER   10031   // vermilion / black lacquer, butsudan, sake tray: glossy
#define BLK_PAPER     10032   // shoji: moonlight shines through the paper

// item.properties (heldItemId / currentRenderedItemId)
#define ITEM_CHOCHIN_LIT 10001  // meikon:chochin_lit (the lit paper lantern)
#define ITEM_HANDLIGHT   10002  // vanilla torches, lanterns, glowstone ...

// entity.properties (entityId)
#define ENT_BRIDE     20001   // hanayome (the bride) + maboroshi: a faint pale shape the fog cannot fully hide
#define ENT_SHADOW    20002   // kageboshi: two pale glowing eyes
#define ENT_LIGHTNING 20010

// emissive mask from texture colour: bright / saturated texels glow (flames, lit paper),
// dark handles, frames and wicks do not
float flameMask(vec3 albedoSrgb) {
    float l = luma(albedoSrgb);
    float sat = maxOf(albedoSrgb) - minOf(albedoSrgb);
    return smoothstep(0.42, 0.80, l + sat * 0.45);
}
float paperMask(vec3 albedoSrgb) {
    return smoothstep(0.30, 0.62, luma(albedoSrgb));
}
// gold texels (the suzu bell): yellow, bright, saturated
float goldMask(vec3 albedoSrgb) {
    float warm = albedoSrgb.r * 0.6 + albedoSrgb.g * 0.4 - albedoSrgb.b;
    return smoothstep(0.30, 0.55, warm) * smoothstep(0.35, 0.65, luma(albedoSrgb));
}

// ---- waving ----------------------------------------------------------------------------
#if defined VERTEX_SHADER && defined WAVING_PLANTS
vec3 windSway(vec3 wp, float amp) {
#ifdef MEIKON_KAKURIYO
    // no wind in the other world - only a slow, cold draft that comes and goes
    float t = frameTimeCounter * 0.55;
    float gust = smoothstep(0.35, 0.95, vnoise1(frameTimeCounter * 0.09 + wp.x * 0.013));
    amp *= 0.35 + 0.65 * gust;
#else
    float t = frameTimeCounter;
    amp *= 1.0 + rainStrength * 1.2;
#endif
    float a = sin(t * 1.30 + wp.x * 0.70 + wp.z * 0.45) * 0.6 + sin(t * 2.30 + wp.z * 1.30 - wp.x * 0.90) * 0.4;
    float b = sin(t * 1.05 - wp.z * 0.60 + wp.x * 0.30) * 0.6 + sin(t * 2.70 - wp.x * 1.10 + wp.y * 0.70) * 0.4;
    return vec3(a, 0.0, b * 0.7) * amp;
}

// wp: world position of the vertex, uv / midUv: atlas coordinates (top / bottom test)
vec3 wavingOffset(int id, vec3 wp, vec2 uv, vec2 midUv, float skyLight) {
    float exposed = 0.25 + 0.75 * skyLight;
    if (id == BLK_PLANT) {
        return uv.y < midUv.y ? windSway(wp, 0.07 * exposed) : vec3(0.0);
    }
    if (id == BLK_LEAVES) {
        return windSway(wp, 0.03 * exposed);
    }
    if (id == BLK_HANGING) {
        return uv.y > midUv.y ? windSway(wp, 0.05) : vec3(0.0);
    }
    return vec3(0.0);
}
#endif
