// =====================================================================================
//  MeikonShaders  MEIKON (meikon) shader pack  -  user options
//  Iris / Oculus / OptiFine discover "#define NAME value // [list]" and "#define FLAG".
//  Every option is shared by all dimension folders (root, meikon_kakuriyo, world-1, world1).
// =====================================================================================

// ---- image ---------------------------------------------------------------------------
#define BRIGHTNESS 1.0              // [0.6 0.7 0.8 0.9 1.0 1.15 1.3 1.5 1.75 2.0]
#define FILM_GRAIN 1.0              // [0.0 0.25 0.5 0.75 1.0 1.5 2.0]
#define VIGNETTE 1.0                // [0.0 0.25 0.5 0.75 1.0 1.25 1.5]
#define CHROMATIC_ABERRATION 1.0    // [0.0 0.5 1.0 1.5 2.0]
#define BLOOM_STRENGTH 1.0          // [0.0 0.5 0.75 1.0 1.25 1.5 2.0]
//#define FLASH_REDUCTION

// ---- atmosphere ----------------------------------------------------------------------
#define FOG_DENSITY 1.0             // [0.5 0.75 1.0 1.25 1.5 2.0]
#define VOLUMETRIC_FOG
#define VOLUMETRIC_SAMPLES 6        // [4 6 8 12 16]

// ---- light ---------------------------------------------------------------------------
#define SHADOWS
const int shadowMapResolution = 1536;   // [1024 1536 2048 3072 4096]
#define SHADOW_FILTER 2             // [1 2 3]
#define FLAME_FLICKER
#define HAND_LANTERN_LIGHT
#define WAVING_PLANTS

// =====================================================================================
//  pipeline directives (read by the shader loader)
// =====================================================================================
/*
const int colortex0Format = RGBA16F;    // HDR scene colour (forward lit)
const int colortex1Format = RGBA8;      // aux: emissive, block-light glow, class (hand/cloud), fog resistance
const int colortex2Format = RGBA16F;    // volumetric moonlight (composite -> composite1)
const int colortex3Format = RGBA16F;    // bloom
const int colortex4Format = RGBA16F;    // bloom (temp)
*/
const vec4 colortex0ClearColor = vec4(0.0, 0.0, 0.0, 0.0);
const vec4 colortex1ClearColor = vec4(0.0, 0.0, 0.0, 0.0);
const vec4 colortex2ClearColor = vec4(0.0, 0.0, 0.0, 0.0);
const vec4 colortex3ClearColor = vec4(0.0, 0.0, 0.0, 0.0);
const vec4 colortex4ClearColor = vec4(0.0, 0.0, 0.0, 0.0);
const bool shadowHardwareFiltering = true;
const int noiseTextureResolution = 256;
const float ambientOcclusionLevel = 1.0;
const float eyeBrightnessHalflife = 4.0;
const float wetnessHalflife = 250.0;
const float drynessHalflife = 120.0;

#ifdef MEIKON_KAKURIYO
    // the mansion is small and the fog swallows everything past ~30 blocks: spend the
    // whole shadow map on the courtyard. Kakuriyo is frozen at midnight, so the tilt alone
    // places the moon: -40 puts it 50 degrees up in the south, over the safe room roof. It
    // lights the torii and the wedding hall facade and the south wing throws a long shadow
    // across the courtyard. The mod's own sky (no shaders) draws its moon at the same spot.
    const float shadowDistance = 56.0;
    const float sunPathRotation = -40.0;
#else
    const float shadowDistance = 96.0;
    const float sunPathRotation = -25.0;
#endif

#define PI 3.14159265359
#define TAU 6.28318530718
