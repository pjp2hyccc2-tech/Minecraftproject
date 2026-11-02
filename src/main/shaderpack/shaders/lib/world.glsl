// =====================================================================================
//  SounanShaders - per-world palette, fear / danger, celestial light
//  The stub folders define SOUNAN_MOUNTAIN / DIM_NETHER / DIM_END; nothing = overworld
//  (and any other dimension: the lobby, mod dimensions ...).
// =====================================================================================

#if defined SOUNAN_MOUNTAIN
    #define WORLD_MOUNTAIN
    #define HAS_CELESTIAL_LIGHT
#elif defined DIM_NETHER
    #define WORLD_NETHER
#elif defined DIM_END
    #define WORLD_END
#else
    #define WORLD_OVERWORLD
    #define HAS_CELESTIAL_LIGHT
#endif

// SHADOWS / VOLUMETRIC_FOG are user options: keep the plain #ifdef form the loaders scan for
#ifdef SHADOWS
    #ifdef HAS_CELESTIAL_LIGHT
        #define SHADOWS_ON
    #endif
#endif
#ifdef VOLUMETRIC_FOG
    #define VOLUMETRICS_ON
    #ifdef SHADOWS_ON
        #define LIGHT_SHAFTS_ON
    #endif
#endif

// ---- the mod's contract (docs/DESIGN.md section 8) --------------------------------------
// Only inside sounan:kaerazu the mod drives rainStrength = fear and thunderStrength = danger.
// Nothing weather-like (rain, wetness, lightning, darker clouds) may come from them there.
#ifdef WORLD_MOUNTAIN
float fear()       { return saturate(rainStrength); }
float danger()     { return saturate(thunderStrength); }
float rainAmount() { return 0.0; }
#else
float fear()       { return 0.0; }
float danger()     { return 0.0; }
float rainAmount() { return saturate(rainStrength); }
#endif

// ---- palette (linear) ----------------------------------------------------------------
// warm light of flames, lanterns and the sodium tunnel lamps
const vec3 FLAME_HOT  = vec3(1.00, 0.62, 0.34);
const vec3 FLAME_DEEP = vec3(1.00, 0.42, 0.16);

#if defined WORLD_MOUNTAIN
    // Cold blue-grey midnight under a dense cedar canopy. The moon reaches the ground only
    // through gaps (shadow map): bright cold patches and shafts, everything else is very dark.
    const vec3  MOON_LIGHT   = vec3(0.30, 0.39, 0.56) * 0.70;
    const vec3  SKY_AMBIENT  = vec3(0.15, 0.19, 0.27) * 0.026;   // the sky dome, mostly blocked by needles
    const vec3  MIN_LIGHT    = vec3(0.42, 0.50, 0.70) * 0.0022;  // never quite black
    const vec3  SKY_ZENITH   = vec3(0.0030, 0.0040, 0.0064);
    const vec3  SKY_HORIZON  = vec3(0.0105, 0.0128, 0.0170);
    // radiance of deep fog lit by the moonlight that leaks through the canopy: a touch
    // brighter than the unlit trunks in front of it, so they read as silhouettes
    const vec3  FOG_AMBIENT  = vec3(0.0135, 0.0168, 0.0228);
    const vec3  FOG_TUNNEL   = vec3(0.0007, 0.0007, 0.0008);   // no sky light: black
    const float FLAME_GAIN   = 1.10;
#elif defined WORLD_NETHER
    const vec3  MIN_LIGHT    = vec3(0.60, 0.40, 0.36) * 0.012;
    const float FLAME_GAIN   = 0.65;
#elif defined WORLD_END
    const vec3  MIN_LIGHT    = vec3(0.50, 0.46, 0.62) * 0.030;
    const float FLAME_GAIN   = 0.65;
#else
    // the overworld (lobby, any other dimension): moody, overcast, but playable
    const vec3  SUN_NOON          = vec3(1.00, 0.96, 0.90) * 1.35;   // pale, joyless sun
    const vec3  SUN_LOW           = vec3(1.00, 0.55, 0.32) * 0.95;
    const vec3  MOON_LIGHT        = vec3(0.30, 0.38, 0.56) * 0.085;
    const vec3  AMBIENT_DAY       = vec3(0.52, 0.57, 0.64) * 0.34;
    const vec3  AMBIENT_NIGHT     = vec3(0.14, 0.17, 0.26) * 0.030;
    const vec3  MIN_LIGHT         = vec3(0.50, 0.58, 0.80) * 0.0055;
    const vec3  SKY_DAY_ZENITH    = vec3(0.20, 0.245, 0.31);
    const vec3  SKY_DAY_HORIZON   = vec3(0.46, 0.49, 0.53);
    const vec3  SKY_NIGHT_ZENITH  = vec3(0.0030, 0.0038, 0.0068);
    const vec3  SKY_NIGHT_HORIZON = vec3(0.0105, 0.0125, 0.0180);
    const float FLAME_GAIN        = 0.62;
#endif

// ---- celestial directions (world space) ------------------------------------------------
vec3 sunDirW()   { return viewDirToWorld(sunPosition); }
vec3 moonDirW()  { return viewDirToWorld(moonPosition); }
vec3 lightDirW() { return viewDirToWorld(shadowLightPosition); }

// 0 = night, 1 = day
float dayFactor() {
#ifdef WORLD_OVERWORLD
    return smoothstep(-0.08, 0.16, sunDirW().y);
#else
    return 0.0;
#endif
}

// colour of the light that casts the shadow map (linear)
vec3 celestialLight() {
#if defined WORLD_MOUNTAIN
    return MOON_LIGHT * (1.0 - 0.35 * fear());          // fear swallows the moonlight
#elif defined WORLD_OVERWORLD
    float h = sunDirW().y;
    vec3 sun = mix(SUN_LOW, SUN_NOON, smoothstep(0.05, 0.45, h));
    float moonPhaseMul = 1.0 - 0.55 * float(abs(moonPhase - 4) < 2);   // new moon: darker
    vec3 c = h > -0.03 ? sun * smoothstep(-0.03, 0.10, h) : MOON_LIGHT * moonPhaseMul * smoothstep(-0.03, -0.14, h);
    return c * (1.0 - rainAmount() * 0.85);
#else
    return vec3(0.0);
#endif
}

// ambient light from the sky dome for a fully sky-lit surface (linear)
vec3 skyAmbient() {
#if defined WORLD_MOUNTAIN
    return SKY_AMBIENT * (1.0 - 0.3 * fear());
#elif defined WORLD_OVERWORLD
    vec3 a = mix(AMBIENT_NIGHT, AMBIENT_DAY, dayFactor());
    return mix(a, vec3(luma(a)) * 0.8, rainAmount() * 0.6);
#else
    return vec3(0.0);
#endif
}

// ambient of the dimension itself (nether / end have no sky light)
vec3 worldAmbient() {
#if defined WORLD_NETHER
    return toLinear(fogColor) * 0.20 + vec3(0.018, 0.010, 0.008);
#elif defined WORLD_END
    return vec3(0.034, 0.030, 0.046);
#else
    return vec3(0.0);
#endif
}
