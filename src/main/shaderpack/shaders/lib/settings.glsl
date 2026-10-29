// =====================================================================================
//  HuntCraft Shaders 〜狩猟奇譚〜  -  settings / options
//  Every option below is discovered by Iris/Oculus/OptiFine ("// [a b c]" lists).
// =====================================================================================

// ---- shadows -------------------------------------------------------------------------
#define SHADOWS
const int shadowMapResolution = 2048; // [1024 2048 3072 4096]
const float shadowDistance = 160.0; // [96.0 128.0 160.0 192.0 256.0]
#define SHADOW_SOFTNESS 1.0 // [0.5 0.75 1.0 1.5 2.0 3.0]
#define COLORED_SHADOWS
const float sunPathRotation = -30.0; // [-45.0 -35.0 -30.0 -25.0 -20.0 -15.0 -10.0 0.0 10.0 20.0 30.0 45.0]

// ---- bloom ---------------------------------------------------------------------------
#define BLOOM
#define BLOOM_STRENGTH 1.0 // [0.25 0.5 0.75 1.0 1.25 1.5 2.0 3.0]
#define EMISSIVE_BOOST 1.0 // [0.5 0.75 1.0 1.5 2.0 3.0]

// ---- atmosphere ----------------------------------------------------------------------
#define VOLUMETRIC_LIGHT
#define VL_SAMPLES 12 // [6 8 12 16 24 32]
#define VL_STRENGTH 1.0 // [0.25 0.5 0.75 1.0 1.5 2.0]
#define FOG_DENSITY 1.0 // [0.0 0.25 0.5 0.75 1.0 1.5 2.0 3.0]
#define HEIGHT_FOG
#define WEATHER_HAZE

// ---- ambient occlusion ---------------------------------------------------------------
#define SSAO
#define SSAO_STRENGTH 1.0 // [0.5 0.75 1.0 1.5 2.0]

// ---- colour --------------------------------------------------------------------------
#define COLOR_GRADING
#define EXPOSURE 1.0 // [0.6 0.7 0.8 0.9 1.0 1.1 1.2 1.3 1.4 1.6 1.8]
#define SATURATION 1.1 // [0.7 0.8 0.9 1.0 1.1 1.2 1.3 1.4]
#define CONTRAST 1.05 // [0.9 0.95 1.0 1.05 1.1 1.15 1.2]
#define VIGNETTE
#define VIGNETTE_STRENGTH 0.5 // [0.25 0.5 0.75 1.0]
#define NIGHT_BRIGHTNESS 1.0 // [0.5 0.75 1.0 1.5 2.0 3.0]

// ---- world ---------------------------------------------------------------------------
#define WAVING_PLANTS
#define WAVING_LEAVES
#define WAVING_WATER
#define WATER_REFLECTIONS
#define SSR_STEPS 16 // [8 12 16 24 32]
#define WATER_WAVES 1.0 // [0.5 0.75 1.0 1.5 2.0]
#define DYNAMIC_HAND_LIGHT
#define LABPBR

// ---- camera --------------------------------------------------------------------------
//#define MOTION_BLUR
#define MOTION_BLUR_STRENGTH 0.5 // [0.25 0.5 0.75 1.0]
#define HEAT_HAZE

// =====================================================================================
//  render targets / pipeline constants (read by the shader loader, fragment shaders only)
// =====================================================================================
/*
const int colortex0Format = RGBA16F;   // albedo (gbuffer)  -> HDR scene colour
const int colortex1Format = RGBA16;    // encoded normal.xy, block light, sky light
const int colortex2Format = RGBA8;     // material id, emissive, subsurface, vertex AO
const int colortex3Format = RGBA16F;   // bloom
const int colortex4Format = RGBA16F;   // bloom temp
const int shadowcolor0Format = RGBA8;
*/
const vec4 colortex0ClearColor = vec4(0.0, 0.0, 0.0, 0.0);
const bool colortex1Clear = true;
const bool colortex2Clear = true;
const int noiseTextureResolution = 256;
const float ambientOcclusionLevel = 1.0;
const bool shadowHardwareFiltering = false;
const float shadowIntervalSize = 2.0;
const float entityShadowDistanceMul = 0.5;
const float wetnessHalflife = 300.0;
const float drynessHalflife = 100.0;
const float eyeBrightnessHalflife = 8.0;

// derived
#define SHADOW_DISTORT 0.85
#define SHADOW_BIAS_MUL 1.0
#define PI 3.14159265359
#define TAU 6.28318530718

#ifdef MC_NORMAL_MAP
    #ifdef LABPBR
        #define USE_NORMAL_MAP
    #endif
#endif
#ifdef MC_SPECULAR_MAP
    #ifdef LABPBR
        #define USE_SPECULAR_MAP
    #endif
#endif
