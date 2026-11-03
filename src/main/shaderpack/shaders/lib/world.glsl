// =====================================================================================
//  ReisatsuShaders - per-world palette, the mod's channels, celestial light
//  The stub folders define REISATSU_HIDAMARI / DIM_NETHER / DIM_END; nothing = overworld
//  (and any other dimension: mod dimensions, the lobby ...).
// =====================================================================================

#if defined REISATSU_HIDAMARI
    #define WORLD_HIDAMARI
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
    #ifdef WORLD_HIDAMARI
        #define EMITTERS_ON          // the light table of lib/emitters.glsl
        #ifdef LIGHT_SHADOWS
            #define LAMP_SHADOWS_ON  // screen-space shadows of each pixel's strongest lamp
        #endif
    #endif
#endif
#ifdef WORLD_HIDAMARI
    #ifdef SPIRIT_HAZE
        #define SPIRIT_HAZE_ON       // heat haze and colour split where a spirit stands
    #endif
    #ifdef AFTERIMAGE
        #define AFTERIMAGE_ON        // the spirits' trail in the finder
    #endif
#endif

// ---- the mod's contract (docs/DESIGN.md section 9) -----------------------------------------
// Only inside reisatsu:hidamari, every client tick:
//   rainStrength    = fear 0..1        thunderStrength = danger 0..1
//   nightVision > 0 = the camera finder is raised (also: holding reisha_camera_finder)
//   blindness       = the camera flash (1 on the shutter frame, ~0.25 s decay): it LIGHTS
//   darknessFactor  = being caught: everything goes black
// Nothing weather-like (rain, wetness, lightning, darker clouds) may come from them there,
// and nothing may darken for blindness.
#ifdef WORLD_HIDAMARI
float fear()       { return saturate(rainStrength); }
float danger()     { return saturate(thunderStrength); }
float rainAmount() { return 0.0; }
float flash()      { return saturate(blindness); }
#else
float fear()       { return 0.0; }
float danger()     { return 0.0; }
float rainAmount() { return saturate(rainStrength); }
float flash()      { return 0.0; }
#endif
float caught()     { return saturate(darknessFactor); }

// ---- palette (linear) ----------------------------------------------------------------
// warm light of flames, candles and lanterns
const vec3 FLAME_HOT  = vec3(1.00, 0.62, 0.34);
const vec3 FLAME_DEEP = vec3(1.00, 0.42, 0.16);

#if defined WORLD_HIDAMARI
    // An overcast night just after rain. The low clouds are a little lit from below by the
    // town (an orange glow on the horizon, strongest in the west where the road comes from).
    const vec3  SKY_AMBIENT  = vec3(0.115, 0.135, 0.175) * 0.030;   // the cloud dome over the lot
    const vec3  TOWN_GLOW    = vec3(1.00, 0.46, 0.17) * 0.020;      // light pollution on the horizon
    const vec3  TOWN_DIR     = vec3(-0.92, 0.0, 0.38);              // west-south-west
    const vec3  MIN_LIGHT    = vec3(0.40, 0.46, 0.60) * 0.0090;     // never quite black (dark-adapted eyes)
    const vec3  SKY_ZENITH   = vec3(0.0042, 0.0050, 0.0068);
    const vec3  SKY_HORIZON  = vec3(0.0110, 0.0118, 0.0140);
    // radiance of deep mist over the lot (the dark blue-grey of the design)
    const vec3  FOG_AMBIENT  = vec3(0.0105, 0.0122, 0.0158);
    const vec3  FOG_INDOOR   = vec3(0.0010, 0.0011, 0.0013);
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
    return vec3(0.0);       // HIDAMARI: overcast, no moon light at all
#endif
}

// ambient light from the sky dome for a fully sky-lit surface with world normal N (linear)
vec3 skyAmbientN(vec3 N) {
#if defined WORLD_HIDAMARI
    // the cloud dome from above, the town glow from the horizon (west-south-west)
    float up = 0.55 + 0.45 * N.y;
    float town = saturate(dot(N, TOWN_DIR) * 0.6 + 0.4) * (1.0 - 0.6 * max(N.y, 0.0));
    return SKY_AMBIENT * up + TOWN_GLOW * town * 0.5;
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
