// =====================================================================================
//  ReisatsuShaders  (REISATSU - lost on Kaerazu-yama at night)  -  user options
//  Iris / Oculus / OptiFine discover "#define NAME value // [list]" and "#define FLAG".
//  Every option is shared by all dimension folders (root, reisatsu_kaerazu, world-1, world1).
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

// ---- light and shadow ----------------------------------------------------------------
#define SHADOWS
const int shadowMapResolution = 2048;   // [1024 1536 2048 3072 4096]
const float shadowDistance = 48.0;      // [32.0 40.0 48.0 64.0 80.0 96.0 128.0]
#define SHADOW_FILTER 2             // [1 2 3]
#define WAVING_PLANTS

// =====================================================================================
//  pipeline directives (read by the shader loader)
// =====================================================================================
/*
const int colortex0Format = RGBA16F;    // HDR scene colour (forward lit; deferred adds the hand lights)
const int colortex1Format = RGBA8;      // aux: r emissive, g block-light glow, b class (hand/cloud/ghost), a coverage
const int colortex2Format = RGBA16F;    // volumetrics: rgb in-scattered light, a transmittance (composite -> composite1)
const int colortex3Format = RGBA16F;    // bloom
const int colortex4Format = RGBA16F;    // bloom (temp)
const int colortex5Format = RGBA8;      // albedo (sRGB) + a: 0 no hand light, 0.5 foliage, 1 lit surface
const int colortex6Format = RGBA16;     // octahedral normal (rg), gloss (b), smoothness (a)
*/
const vec4 colortex0ClearColor = vec4(0.0, 0.0, 0.0, 0.0);
const vec4 colortex1ClearColor = vec4(0.0, 0.0, 0.0, 0.0);
const vec4 colortex2ClearColor = vec4(0.0, 0.0, 0.0, 1.0);
const vec4 colortex3ClearColor = vec4(0.0, 0.0, 0.0, 0.0);
const vec4 colortex4ClearColor = vec4(0.0, 0.0, 0.0, 0.0);
const vec4 colortex5ClearColor = vec4(0.0, 0.0, 0.0, 0.0);
const vec4 colortex6ClearColor = vec4(0.5, 0.5, 0.0, 0.0);
const bool shadowHardwareFiltering = true;
const float shadowDistanceRenderMul = 1.0;   // only chunks inside shadowDistance go into the shadow map
const int noiseTextureResolution = 256;
const float ambientOcclusionLevel = 1.0;
const float eyeBrightnessHalflife = 3.0;
const float wetnessHalflife = 250.0;
const float drynessHalflife = 120.0;

#ifdef REISATSU_HIDAMARI
    // Kaerazu-yama is frozen at 18000 (midnight), so the tilt alone places the moon: -30 puts
    // it 60 degrees up in the south (downhill, the way out). The canopy gaps let slanted shafts
    // through, and looking down the mountain they glow against the fog (forward scattering).
    const float sunPathRotation = -30.0;
#else
    const float sunPathRotation = -25.0;
#endif

#define PI 3.14159265359
#define TAU 6.28318530718
