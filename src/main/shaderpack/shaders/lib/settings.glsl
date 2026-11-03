// =====================================================================================
//  HitoriShaders  (HITORI - the family restaurant HIDAMARI at night)  -  user options
//  Iris / Oculus / OptiFine discover "#define NAME value // [list]" and "#define FLAG".
//  Every option is shared by all dimension folders (root, hitori_hidamari, world-1, world1).
// =====================================================================================

// ---- image ---------------------------------------------------------------------------
#define BRIGHTNESS 1.0              // [0.6 0.7 0.8 0.9 1.0 1.15 1.3 1.5 1.75 2.0]
#define FILM_GRAIN 1.0              // [0.0 0.25 0.5 0.75 1.0 1.5 2.0]
#define VIGNETTE 1.0                // [0.0 0.25 0.5 0.75 1.0 1.25 1.5]
#define CHROMATIC_ABERRATION 1.0    // [0.0 0.5 1.0 1.5 2.0]
#define BLOOM_STRENGTH 1.0          // [0.0 0.5 0.75 1.0 1.25 1.5 2.0]
#define FEAR_EFFECTS 1.0            // [0.0 0.25 0.5 0.75 1.0 1.25 1.5]
#define PAREIDOLIA
//#define FLASH_REDUCTION

// ---- the restaurant's lights -----------------------------------------------------------
#define COLORED_LIGHT
#define LIGHT_STRENGTH 1.0          // [0.5 0.75 1.0 1.25 1.5 2.0]
#define LIGHT_SHADOWS
#define LIGHT_FLICKER

// ---- reflections and wet surfaces --------------------------------------------------------
#define REFLECTIONS
#define SSR_STEPS 14                // [8 10 14 20 28]
#define WET_SURFACES
#define GLASS_DROPLETS

// ---- spirits and the finder ----------------------------------------------------------------
#define SPIRIT_HAZE
#define AFTERIMAGE

// ---- atmosphere ----------------------------------------------------------------------
#define FOG_DENSITY 1.0             // [0.5 0.75 1.0 1.25 1.5 2.0]
#define VOLUMETRIC_FOG
#define VOLUMETRIC_SAMPLES 10       // [6 8 10 12 16 24]
#define FOG_NOISE
#define DUST_MOTES

// ---- the flashlight ------------------------------------------------------------------
#define BEAM_STRENGTH 1.0           // [0.5 0.75 1.0 1.25 1.5 2.0]
#define BEAM_FOG 1.0                // [0.0 0.5 0.75 1.0 1.25 1.5 2.0]
#define BEAM_SAMPLES 8              // [4 6 8 12 16]
#define BEAM_SHADOWS
#define LENS_COOKIE

// ---- sun and shadow (other dimensions) ---------------------------------------------------
#define SHADOWS
#define SHADOW_FILTER 2             // [1 2 3]
#define WAVING_PLANTS

// =====================================================================================
//  pipeline directives (read by the shader loader)
// =====================================================================================
/*
const int colortex0Format = RGBA16F;    // HDR scene colour (forward lit; deferred adds the hand lights and the flash)
const int colortex1Format = RGBA8;      // aux: r emissive, g block-light share, b class (hand / cloud), a coverage
const int colortex2Format = RGBA16F;    // volumetrics: rgb in-scattered light, a transmittance (composite -> composite2)
const int colortex3Format = RGBA16F;    // bloom
const int colortex4Format = RGBA16F;    // bloom (temp)
const int colortex5Format = RGBA8;      // albedo (sRGB) + a: material code / 255 (0 = no deferred light)
const int colortex6Format = RGBA16;     // octahedral normal (rg), block light (b), sky light (a)
const int colortex7Format = RGBA32F;    // hitori:hidamari - the light table (prepare -> every lit program)
const int colortex8Format = RGBA8;      // spirits: r body, g finder glow, b eye alpha, a haze
const int colortex9Format = RGBA16F;    // the finder's afterimage (kept between frames)
*/
const vec4 colortex0ClearColor = vec4(0.0, 0.0, 0.0, 0.0);
const vec4 colortex1ClearColor = vec4(0.0, 0.0, 0.0, 0.0);
const vec4 colortex2ClearColor = vec4(0.0, 0.0, 0.0, 1.0);
const vec4 colortex3ClearColor = vec4(0.0, 0.0, 0.0, 0.0);
const vec4 colortex4ClearColor = vec4(0.0, 0.0, 0.0, 0.0);
const vec4 colortex5ClearColor = vec4(0.0, 0.0, 0.0, 0.0);
const vec4 colortex6ClearColor = vec4(0.5, 0.5, 0.0, 0.0);
const vec4 colortex7ClearColor = vec4(0.0, 0.0, 0.0, 0.0);
const vec4 colortex8ClearColor = vec4(0.0, 0.0, 0.0, 0.0);
const bool colortex9Clear = false;
const vec4 shadowcolor0ClearColor = vec4(0.0, 0.0, 0.0, 0.0);
const int noiseTextureResolution = 256;
const float ambientOcclusionLevel = 1.0;
const float eyeBrightnessHalflife = 3.0;
const float wetnessHalflife = 250.0;
const float drynessHalflife = 120.0;

#ifdef HITORI_HIDAMARI
    // The restaurant's sky is overcast: no moon light, no shadow map. The shadow pass draws
    // the top-down emitter map instead (lib/emitters.glsl): 4 x 4 tiles of 128 x 128 blocks
    // around the camera, one texel per block. Fixed size - not a user option here.
    const int shadowMapResolution = 512;
    const float shadowDistance = 64.0;
    const float shadowDistanceRenderMul = 1.0;
    const float sunPathRotation = 0.0;          // 18000: the "moon" straight up = a top-down view
    const bool shadowHardwareFiltering = false;
    const bool shadowcolor0Nearest = true;
    const bool shadowtex0Nearest = true;
#else
    const int shadowMapResolution = 2048;   // [1024 1536 2048 3072 4096]
    const float shadowDistance = 64.0;      // [32.0 40.0 48.0 64.0 80.0 96.0 128.0]
    const float shadowDistanceRenderMul = 1.0;   // only chunks inside shadowDistance go into the shadow map
    const float sunPathRotation = -25.0;
    const bool shadowHardwareFiltering = true;
#endif

#define PI 3.14159265359
#define TAU 6.28318530718
