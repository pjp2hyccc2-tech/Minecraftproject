// =====================================================================================
//  HuntCraft Shaders - block ids (block.properties), entity ids, waving, emissives
// =====================================================================================

// block.properties ids
#define BLK_LEAVES      10001
#define BLK_PLANT       10002   // ground plants: only the top vertices wave
#define BLK_TALLPLANT   10003   // double-height plants
#define BLK_WATER       10004
#define BLK_LAVA        10005
#define BLK_MAGMA       10006
#define BLK_FIRELIGHT   10007   // torches, fire, lanterns, paper lanterns
#define BLK_GLOWBLOCK   10008   // glowstone, sea lantern, froglights ...
#define BLK_ICE         10009
#define BLK_GLASS       10010
#define BLK_SNOW        10011
#define BLK_GATHER      10012   // huntcraft gathering points (faint glow)
#define BLK_LILYPAD     10013
#define BLK_CANE        10014   // sugar cane, bamboo, kelp-like full wave
#define BLK_GLOWSOFT    10015   // amethyst, glow lichen, candles

// entity.properties ids
#define ENT_LIGHTNING   11000
#define ENT_PLAYER      11001
#define ENT_PROJECTILE  11002
#define ENT_HAZARD      11003

// item.properties ids
#define ITM_TORCHLIKE   12000
#define ITM_GLOWBLOCK   12001

bool isWaterId(int id) { return id == BLK_WATER; }
bool isTranslucentId(int id) { return id == BLK_GLASS || id == BLK_ICE; }
bool isFoliageId(int id) { return id == BLK_LEAVES || id == BLK_PLANT || id == BLK_TALLPLANT || id == BLK_CANE || id == BLK_LILYPAD; }

// strength of emission for a block id (0..1). Texture brightness is applied on top.
float blockEmissive(int id) {
    if (id == BLK_LAVA) return 1.0;
    if (id == BLK_FIRELIGHT) return 1.0;
    if (id == BLK_GLOWBLOCK) return 0.85;
    if (id == BLK_MAGMA) return 0.7;
    if (id == BLK_GATHER) return 0.5;
    if (id == BLK_GLOWSOFT) return 0.5;
    return 0.0;
}

// emissive mask derived from texture brightness (bright texels of a torch glow, the handle does not)
float textureEmissiveMask(vec3 albedo, float base) {
    float l = luma(albedo);
    float sat = max(albedo.r, max(albedo.g, albedo.b)) - min(albedo.r, min(albedo.g, albedo.b));
    float m = smoothstep(0.30, 0.75, l + sat * 0.5);
    return base * m;
}

// ---- waving -----------------------------------------------------------------------------
// returns an offset (blocks) for a vertex at world position wp
vec3 windOffset(vec3 wp, float strength, float time) {
    float t = time;
    float a = sin(t * 1.3 + wp.x * 0.9 + wp.z * 0.6) * 0.55
            + sin(t * 2.1 + wp.x * 1.8 - wp.z * 1.3) * 0.30
            + sin(t * 3.7 + wp.z * 2.9 + wp.y * 0.7) * 0.15;
    float b = sin(t * 1.1 - wp.z * 0.8 + wp.x * 0.4) * 0.55
            + sin(t * 2.6 - wp.x * 1.5 + wp.z * 2.0) * 0.30
            + sin(t * 3.1 + wp.y * 1.3) * 0.15;
    return vec3(a, (a + b) * 0.25, b) * strength;
}

// playerPos: vertex position relative to camera; texcoord/midTex: atlas coords (top vertex test)
vec3 wavingOffset(int id, vec3 playerPos, vec2 texcoord, vec2 midTex, float lightSky) {
    vec3 wp = playerPos + cameraPosition;
    float time = frameTimeCounter * (1.0 + rainStrength * 0.8);
    float amp = (1.0 + rainStrength * 1.2) * (0.35 + 0.65 * lightSky);
    vec3 off = vec3(0.0);
#ifdef WAVING_LEAVES
    if (id == BLK_LEAVES) {
        off = windOffset(wp, 0.035 * amp, time);
    }
#endif
#ifdef WAVING_PLANTS
    if (id == BLK_PLANT) {
        bool top = texcoord.y < midTex.y;
        if (top) off = windOffset(wp, 0.055 * amp, time);
    } else if (id == BLK_TALLPLANT || id == BLK_CANE) {
        bool top = texcoord.y < midTex.y;
        float f = top ? 1.0 : 0.6;
        off = windOffset(wp, 0.05 * amp * f, time);
        off.y *= 0.3;
    } else if (id == BLK_LILYPAD) {
        off = vec3(0.0, sin(time * 1.5 + wp.x * 1.3 + wp.z * 1.1) * 0.012, 0.0);
    }
#endif
    return off;
}

// water surface vertical wave (blocks)
float waterWaveHeight(vec3 wp, float time) {
#ifdef WAVING_WATER
    float h = sin(time * 1.6 + wp.x * 1.1 + wp.z * 0.7) * 0.55
            + sin(time * 2.3 - wp.x * 0.6 + wp.z * 1.7) * 0.30
            + sin(time * 3.9 + wp.x * 2.3 + wp.z * 2.1) * 0.15;
    return h * 0.022 * WATER_WAVES;
#else
    return 0.0;
#endif
}

// procedural water normal (world space, +Y up) from noise texture
vec3 waterNormal(vec3 wp, float time, float strength) {
    vec2 uv1 = wp.xz * 0.045 + vec2(time * 0.018, time * 0.011);
    vec2 uv2 = wp.xz * 0.110 - vec2(time * 0.024, -time * 0.017);
    vec2 uv3 = wp.xz * 0.260 + vec2(-time * 0.030, time * 0.040);
    float e = 0.004;
    float h00 = noiseTex(uv1) * 0.55 + noiseTex(uv2) * 0.30 + noiseTex(uv3) * 0.15;
    float h10 = noiseTex(uv1 + vec2(e * 0.045, 0.0)) * 0.55 + noiseTex(uv2 + vec2(e * 0.11, 0.0)) * 0.30 + noiseTex(uv3 + vec2(e * 0.26, 0.0)) * 0.15;
    float h01 = noiseTex(uv1 + vec2(0.0, e * 0.045)) * 0.55 + noiseTex(uv2 + vec2(0.0, e * 0.11)) * 0.30 + noiseTex(uv3 + vec2(0.0, e * 0.26)) * 0.15;
    float dx = (h10 - h00) / e;
    float dz = (h01 - h00) / e;
    float s = 0.12 * strength * WATER_WAVES;
    return normalize(vec3(-dx * s, 1.0, -dz * s));
}
