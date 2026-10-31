// ============================================================== TabiShaders settings (shown in the shader options menu)
#define SHADOW_SOFTNESS 1.0      // [0.5 0.75 1.0 1.5 2.0]
#define VOLUMETRIC_LIGHT         // light shafts through trees, clouds of mist and between buildings
#define VL_STRENGTH 1.0          // [0.0 0.25 0.5 0.75 1.0 1.5 2.0]
#define WATER_REFLECTIONS        // screen-space reflections: harbour lights on the water, buildings in the glass
#define WAVING_PLANTS            // rice, grass, leaves, noren and wind chimes move in the breeze
#define BLOOM_STRENGTH 1.0       // [0.0 0.5 0.75 1.0 1.25 1.5 2.0]
#define LENS_STREAKS             // soft horizontal streaks from bright lights at night
#define EXPOSURE 1.0             // [0.6 0.8 1.0 1.2 1.4 1.7 2.0]
#define FOG_DENSITY 1.0          // [0.0 0.5 0.75 1.0 1.5 2.0]
#define GRADING_STRENGTH 1.0     // [0.0 0.5 0.75 1.0 1.25 1.5]
#define FILM_GRAIN 1.0           // [0.0 0.5 1.0 2.0]
#define VIGNETTE_STRENGTH 1.0    // [0.0 0.5 1.0 1.5]
#define CHROMATIC_ABERRATION     // faint colour fringing near the edges of the screen
#define EMISSIVE_STRENGTH 1.0    // [0.5 0.75 1.0 1.5 2.0]

const int shadowMapResolution = 2048;   // [1024 2048 3072 4096]
const float shadowDistance = 160.0;     // [96.0 128.0 160.0 224.0]
const float sunPathRotation = -25.0;    // [-40.0 -30.0 -25.0 -15.0 0.0 15.0 25.0]
const bool shadowHardwareFiltering = true;
const float shadowDistanceRenderMul = 1.0;
const float ambientOcclusionLevel = 1.0;
const float eyeBrightnessHalflife = 4.0;
const float wetnessHalflife = 300.0;
const float drynessHalflife = 200.0;
