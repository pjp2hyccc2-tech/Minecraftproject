// =====================================================================================
//  HitoriShaders - per-world palette, the mod's channels, celestial light
//  The stub folders define HITORI_SHIRAKABA / DIM_NETHER / DIM_END; nothing = overworld
//  (and any other dimension: mod dimensions, the lobby ...).
// =====================================================================================

#if defined HITORI_SHIRAKABA
    #define WORLD_SHIRAKABA
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
#ifdef COLORED_LIGHT
    #ifdef WORLD_SHIRAKABA
        #define EMITTERS_ON          // the light table of lib/emitters.glsl
        #ifdef LIGHT_SHADOWS
            #define LAMP_SHADOWS_ON  // screen-space shadows of each pixel's strongest lamp
        #endif
    #endif
#endif
#ifdef WORLD_SHIRAKABA
    #ifdef APPARITION_HAZE
        #define APPARITION_HAZE_ON   // the air wavers round the woman on a camera feed
    #endif
    #ifdef RAIN_STREAKS
        #define RAIN_STREAKS_ON      // falling rain in the air outside, lit by the street lamp
    #endif
#endif

// ---- the mod's contract (docs/DESIGN.md section 9) -----------------------------------------
// Only inside hitori:shirakaba, every client tick:
//   rainStrength    = fear 0..1        thunderStrength = danger 0..1
//   nightVision > 0 = the camera app shows a fixed camera's feed (the CCTV / IR look)
//   blindness       = eyes closed (counting to ten; the jump scare cut): black, phosphenes,
//                     the TV's flicker red through the eyelids - never the vanilla blindness fog
//   darknessFactor  = being caught: everything goes black
// Nothing weather-like (rain, wetness, lightning, darker clouds) may come from them there.
// The night's light rain is the world's own constant (RAIN_LIGHT): wet streets, puddles with
// rings, drops running down the windows - whatever fear does.
#ifdef WORLD_SHIRAKABA
const float RAIN_LIGHT = 0.60;
float fear()       { return saturate(rainStrength); }
float danger()     { return saturate(thunderStrength); }
float rainAmount() { return RAIN_LIGHT; }
float cctv()       { return nightVision > 0.001 ? 1.0 : 0.0; }
float eyesClosed() { return saturate(blindness); }
#else
float fear()       { return 0.0; }
float danger()     { return 0.0; }
float rainAmount() { return saturate(rainStrength); }
float cctv()       { return 0.0; }
float eyesClosed() { return 0.0; }
#endif
float caught()     { return saturate(darknessFactor); }

// ---- palette (linear) ----------------------------------------------------------------
// warm light of flames, candles and lanterns
const vec3 FLAME_HOT  = vec3(1.00, 0.62, 0.34);
const vec3 FLAME_DEEP = vec3(1.00, 0.42, 0.16);

#if defined WORLD_SHIRAKABA
    // A suburb in light rain at 3 a.m.: a low overcast lit dull orange-grey from below by the
    // town (strongest toward the station, east-north-east), no moon, no stars.
    const vec3  SKY_AMBIENT  = vec3(0.120, 0.128, 0.150) * 0.026;   // the cloud dome over the street
    const vec3  TOWN_GLOW    = vec3(1.00, 0.52, 0.26) * 0.017;      // light pollution under the clouds
    const vec3  TOWN_DIR     = vec3(0.86, 0.0, -0.51);              // east-north-east
    const vec3  MIN_LIGHT    = vec3(0.40, 0.46, 0.60) * 0.0125;     // never quite black (dark-adapted eyes)
    const vec3  SKY_ZENITH   = vec3(0.0050, 0.0050, 0.0058);
    const vec3  SKY_HORIZON  = vec3(0.0128, 0.0118, 0.0122);
    // radiance of the rain haze over the street, and of the still air in the rooms
    const vec3  FOG_AMBIENT  = vec3(0.0115, 0.0118, 0.0135);
    const vec3  FOG_INDOOR   = vec3(0.0008, 0.0009, 0.0011);
    const float FLAME_GAIN   = 0.85;
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
#if defined WORLD_OVERWORLD
    float h = sunDirW().y;
    vec3 sun = mix(SUN_LOW, SUN_NOON, smoothstep(0.05, 0.45, h));
    float moonPhaseMul = 1.0 - 0.55 * float(abs(moonPhase - 4) < 2);   // new moon: darker
    vec3 c = h > -0.03 ? sun * smoothstep(-0.03, 0.10, h) : MOON_LIGHT * moonPhaseMul * smoothstep(-0.03, -0.14, h);
    return c * (1.0 - rainAmount() * 0.85);
#else
    return vec3(0.0);       // SHIRAKABA: overcast and raining, no moon light at all
#endif
}

// ambient light from the sky dome for a fully sky-lit surface with world normal N (linear)
vec3 skyAmbientN(vec3 N) {
#if defined WORLD_SHIRAKABA
    // the cloud dome from above, the town glow from the horizon
    float up = 0.55 + 0.45 * N.y;
    float town = saturate(dot(N, TOWN_DIR) * 0.6 + 0.4) * (1.0 - 0.6 * max(N.y, 0.0));
    return SKY_AMBIENT * up + TOWN_GLOW * (0.25 + 0.75 * town) * 0.5;
#elif defined WORLD_OVERWORLD
    vec3 a = mix(AMBIENT_NIGHT, AMBIENT_DAY, dayFactor());
    a = mix(a, vec3(luma(a)) * 0.8, rainAmount() * 0.6);
    return a * (0.62 + 0.38 * N.y);
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
