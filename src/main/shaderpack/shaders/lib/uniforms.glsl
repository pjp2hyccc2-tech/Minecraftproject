// =====================================================================================
//  MeikonShaders - uniforms shared by every program
//  (shadowtex0 is declared as sampler2DShadow in lib/shadow.glsl, only where it is sampled)
// =====================================================================================

uniform sampler2D gtexture;
uniform sampler2D noisetex;
uniform sampler2D colortex0;
uniform sampler2D colortex1;
uniform sampler2D colortex2;
uniform sampler2D colortex3;
uniform sampler2D colortex4;
uniform sampler2D depthtex0;
uniform sampler2D depthtex1;

uniform mat4 gbufferModelView;
uniform mat4 gbufferModelViewInverse;
uniform mat4 gbufferProjection;
uniform mat4 gbufferProjectionInverse;
uniform mat4 shadowModelView;
uniform mat4 shadowModelViewInverse;
uniform mat4 shadowProjection;

uniform vec3 cameraPosition;
uniform vec3 sunPosition;
uniform vec3 moonPosition;
uniform vec3 shadowLightPosition;
uniform vec3 fogColor;

uniform float viewWidth;
uniform float viewHeight;
uniform float aspectRatio;
uniform float near;
uniform float far;
uniform float frameTimeCounter;
uniform float rainStrength;      // meikon:kakuriyo -> FEAR 0..1   (elsewhere: rain)
uniform float thunderStrength;   // meikon:kakuriyo -> DANGER 0..1 (elsewhere: thunder)
uniform float wetness;
uniform float nightVision;
uniform float blindness;
uniform float darknessFactor;
uniform float darknessLightFactor;

uniform int frameCounter;
uniform int isEyeInWater;
uniform int heldItemId;
uniform int heldItemId2;
uniform int heldBlockLightValue;
uniform int heldBlockLightValue2;
uniform int renderStage;
uniform int entityId;
uniform int currentRenderedItemId;
uniform int moonPhase;

uniform ivec2 eyeBrightnessSmooth;
uniform vec4 entityColor;
