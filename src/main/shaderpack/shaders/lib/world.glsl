// =====================================================================================
//  MeikonShaders - per-world palette, time of day, fear / danger
//  The stub folders define MEIKON_KAKURIYO / DIM_NETHER / DIM_END; nothing = overworld
//  (and any other dimension).
// =====================================================================================

#if defined MEIKON_KAKURIYO
    #define WORLD_KAKURIYO
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
    #ifdef SHADOWS_ON
        #define VOLUMETRICS_ON
    #endif
#endif

// ---- the mod's contract (docs/DESIGN.md section 6) --------------------------------------
// Only inside meikon:kakuriyo the mod drives rainStrength = fear and thunderStrength = danger.
#ifdef WORLD_KAKURIYO
float fear()       { return saturate(rainStrength); }
float danger()     { return saturate(thunderStrength); }
float rainAmount() { return 0.0; }
#else
float fear()       { return 0.0; }
float danger()     { return 0.0; }
float rainAmount() { return saturate(rainStrength); }
#endif

// ---- palette -------------------------------------------------------------------------
// warm flame light of lanterns, andon lamps and candles (linear)
const vec3 FLAME_HOT  = vec3(1.00, 0.66, 0.40);
const vec3 FLAME_DEEP = vec3(1.00, 0.46, 0.20);

#if defined WORLD_KAKURIYO
    // moonlit ground reads as cold blue-grey (~20% on screen), surfaces in the moon shadow
    // under the open sky stay just readable; roofed rooms get none of it
    const vec3  MOON_LIGHT        = vec3(0.30, 0.38, 0.56) * 0.80;   // cold, blue-grey
    const vec3  SKY_AMBIENT       = vec3(0.16, 0.19, 0.27) * 0.27;
    const vec3  MIN_LIGHT         = vec3(0.50, 0.58, 0.80) * 0.0120; // the dark is never quite black
    const vec3  SKY_ZENITH        = vec3(0.0026, 0.0004, 0.0007);
    const vec3  SKY_MID           = vec3(0.0130, 0.0019, 0.0026);
    const vec3  SKY_HORIZON       = vec3(0.0500, 0.0058, 0.0070);    // a dark crimson haze
    const vec3  FOG_INDOOR        = vec3(0.0034, 0.0011, 0.0015);    // crimson-black
    const vec3  FOG_OUTDOOR       = vec3(0.0105, 0.0052, 0.0068);    // moonlit, still crimson
    const float FOG_RANGE_INDOOR  = 13.0;   // exp^2 fog scale in blocks: corridors are black at ~24
    const float FOG_RANGE_OUTDOOR = 24.0;   // across the courtyard (~26 blocks) the torii is a shape
    const float FLAME_GAIN        = 1.00;
#elif defined WORLD_NETHER
    const vec3  MIN_LIGHT         = vec3(0.60, 0.40, 0.36) * 0.012;
    const float FLAME_GAIN        = 0.65;
#elif defined WORLD_END
    const vec3  MIN_LIGHT         = vec3(0.50, 0.46, 0.62) * 0.030;
    const float FLAME_GAIN        = 0.65;
#else
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
#if defined WORLD_KAKURIYO
    return MOON_LIGHT * (1.0 - 0.35 * fear());          // fear swallows the moonlight
#elif defined WORLD_OVERWORLD
    float h = sunDirW().y;
    float day = dayFactor();
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
#if defined WORLD_KAKURIYO
    return SKY_AMBIENT;
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
