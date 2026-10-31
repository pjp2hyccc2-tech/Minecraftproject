// ============================================================== fragment stage shared by the lit gbuffers programs
#include "/lib/settings.glsl"
#if !defined PROGRAM_HAND && !defined PROGRAM_TEXTURED && !defined PROGRAM_BASIC
#define SHADOW_SAMPLING
#endif
#include "/lib/common.glsl"
#include "/lib/shadow.glsl"
#include "/lib/sky.glsl"
#include "/lib/lighting.glsl"

uniform sampler2D gtexture;
#ifdef PROGRAM_ENTITIES
uniform vec4 entityColor;
#endif

in vec2 texcoord;
in vec2 lmcoord;
in vec4 glcolor;
in vec3 normalV;
in vec3 shadowClip;
in vec3 playerPos;
flat in int mat;

/* RENDERTARGETS: 0 */
layout(location = 0) out vec4 outColor;

void main() {
#ifdef PROGRAM_BASIC
    vec4 tex = glcolor;
#else
    vec4 tex = texture(gtexture, texcoord) * glcolor;
#endif
    if (tex.a < 0.1) discard;
#ifdef PROGRAM_ENTITIES
    tex.rgb = mix(tex.rgb, entityColor.rgb, entityColor.a);
#endif
    vec3 albedo = toLinear(tex.rgb);
    float em = emissiveFor(mat, albedo, lmcoord);
#if defined PROGRAM_ENTITIES || defined PROGRAM_BLOCK || defined PROGRAM_TEXTURED
    if (lmcoord.x > 0.99) em = max(em, 0.55);        // full-bright parts: neon text, LED boards, lit panels
#endif
    vec3 n = normalV;
#if defined PROGRAM_TEXTURED || defined PROGRAM_BASIC
    n = normalize(normalize(shadowLightPosition) + normalize(upPosition));
#endif
    vec3 c = shade(albedo, n, lmcoord, shadowClip, mat, em);
    outColor = vec4(c, tex.a);
}
