// ============================================================== vertex stage shared by the lit gbuffers programs
#include "/lib/settings.glsl"
#include "/lib/common.glsl"
#include "/lib/shadow.glsl"

uniform mat4 shadowModelView;
uniform mat4 shadowProjection;
#ifdef PROGRAM_TERRAIN
in vec4 mc_Entity;
in vec2 mc_midTexCoord;
#endif

out vec2 texcoord;
out vec2 lmcoord;
out vec4 glcolor;
out vec3 normalV;
out vec3 shadowClip;
out vec3 playerPos;
flat out int mat;

vec3 sway(vec3 wp, float amp, float speed) {
    float t = frameTimeCounter * speed;
    float w = sin(t + wp.x * 0.35 + wp.z * 0.21) * 0.6 + sin(t * 1.7 + wp.z * 0.5 - wp.x * 0.13) * 0.4;
    float gust = 0.6 + 0.4 * sin(frameTimeCounter * 0.35 + wp.x * 0.02 + wp.z * 0.013);
    return vec3(w, 0.0, w * 0.6) * amp * gust * (1.0 + rainStrength);
}

void main() {
    texcoord = (gl_TextureMatrix[0] * gl_MultiTexCoord0).xy;
    lmcoord = clamp(((gl_TextureMatrix[1] * gl_MultiTexCoord1).xy - 1.0 / 32.0) * 16.0 / 15.0, 0.0, 1.0);
    glcolor = gl_Color;
    vec3 n = gl_NormalMatrix * gl_Normal;
    normalV = dot(n, n) > 1e-5 ? normalize(n) : normalize(upPosition);
    mat = 0;
#ifdef PROGRAM_TERRAIN
    mat = int(mc_Entity.x + 0.5);
#endif
    vec4 viewPos = gl_ModelViewMatrix * gl_Vertex;
    playerPos = (gbufferModelViewInverse * viewPos).xyz;
#if defined PROGRAM_TERRAIN && defined WAVING_PLANTS
    vec3 wp = playerPos + cameraPosition;
    bool top = texcoord.y < mc_midTexCoord.y;
    if (mat == 20 && top) playerPos += sway(wp, 0.09, 1.6);
    else if (mat == 21) playerPos += sway(wp, 0.035, 1.2);
    else if (mat == 22 && !top) playerPos += sway(wp, 0.06, 2.1);
#endif
    vec3 nP = mat3(gbufferModelViewInverse) * normalV;
    vec4 sp = shadowProjection * (shadowModelView * vec4(playerPos + nP * 0.06, 1.0));
    shadowClip = sp.xyz;
#ifdef PROGRAM_HAND
    gl_Position = gl_ProjectionMatrix * viewPos;
#else
    gl_Position = gl_ProjectionMatrix * (gbufferModelView * vec4(playerPos, 1.0));
#endif
}
