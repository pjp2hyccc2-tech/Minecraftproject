// =====================================================================================
//  HitoriShaders - uniforms shared by every program
//  (shadowtex0 is declared as sampler2DShadow in lib/shadow.glsl, only where it is sampled)
// =====================================================================================

uniform sampler2D gtexture;
uniform sampler2D noisetex;
uniform sampler2D colortex0;
uniform sampler2D colortex1;
uniform sampler2D colortex2;
uniform sampler2D colortex3;
uniform sampler2D colortex4;
uniform sampler2D colortex5;
uniform sampler2D colortex6;
uniform sampler2D colortex7;
uniform sampler2D colortex8;
uniform sampler2D colortex9;
uniform sampler2D depthtex0;
uniform sampler2D depthtex1;
uniform sampler2D shadowcolor0;

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
uniform float frameTime;
uniform float rainStrength;      // hitori:hidamari -> FEAR 0..1   (elsewhere: rain)
uniform float thunderStrength;   // hitori:hidamari -> DANGER 0..1 (elsewhere: thunder)
uniform float nightVision;       // hitori:hidamari -> > 0 while the camera finder is raised
uniform float blindness;         // hitori:hidamari -> the camera FLASH 1..0 (never darkens)
uniform float darknessFactor;    // vanilla Darkness: being caught
uniform float darknessLightFactor;

uniform int frameCounter;
uniform int worldTime;
uniform int isEyeInWater;
uniform int heldItemId;          // main hand (item.properties)
uniform int heldItemId2;         // off hand
uniform int heldBlockLightValue;
uniform int heldBlockLightValue2;
uniform int renderStage;
uniform int entityId;
uniform int blockEntityId;
uniform int currentRenderedItemId;
uniform int moonPhase;

uniform ivec2 eyeBrightnessSmooth;
uniform vec4 entityColor;
