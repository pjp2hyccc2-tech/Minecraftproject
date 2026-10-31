#version 330 compatibility
// water (waves, Fresnel sky reflection, sun glint), glass and other translucent blocks
#include "/lib/settings.glsl"
#define SHADOW_SAMPLING
#include "/lib/common.glsl"
#include "/lib/shadow.glsl"
#include "/lib/sky.glsl"
#include "/lib/lighting.glsl"

uniform sampler2D gtexture;
in vec2 texcoord;
in vec2 lmcoord;
in vec4 glcolor;
in vec3 normalV;
in vec3 shadowClip;
in vec3 playerPos;
flat in int mat;

/* RENDERTARGETS: 0,1 */
layout(location = 0) out vec4 outColor;
layout(location = 1) out vec4 outData;

float waveH(vec2 p, float t) {
    float h = sin(dot(p, vec2(0.62, 0.78)) * 0.9 + t * 1.2) * 0.50;
    h += sin(dot(p, vec2(-0.83, 0.55)) * 1.7 + t * 1.6) * 0.26;
    h += sin(dot(p, vec2(0.21, -0.98)) * 3.1 + t * 2.2) * 0.12;
    h += sin(dot(p, vec2(-0.45, -0.89)) * 5.3 + t * 3.0) * 0.06;
    h += sin(dot(p, vec2(0.97, 0.24)) * 8.9 + t * 3.9) * 0.03;
    return h;
}
vec3 waterNormal(vec2 p, float t) {
    const float e = 0.06;
    float h0 = waveH(p, t);
    float s = 0.11 * (1.0 + rainStrength);
    return normalize(vec3(-(waveH(p + vec2(e, 0.0), t) - h0) / e * s, 1.0, -(waveH(p + vec2(0.0, e), t) - h0) / e * s));
}

void main() {
    vec4 tex = texture(gtexture, texcoord) * glcolor;
    vec3 sd = sunDirW();
    float h = sd.y;
    vec3 V = normalize(playerPos);
    if (mat == 30) {
        vec3 nW = mat3(gbufferModelViewInverse) * normalV;
        if (nW.y > 0.5) nW = waterNormal(playerPos.xz + cameraPosition.xz, frameTimeCounter);
        float cosT = clamp(dot(-V, nW), 0.0, 1.0);
        float F = 0.02 + 0.98 * pow(1.0 - cosT, 5.0);
        vec3 R = reflect(V, nW);
        R.y = abs(R.y);
        vec3 refl = skyRadiance(R, sd) * smoothstep(0.15, 0.9, lmcoord.y);
        float sh = shadowSample(shadowClip, 1.0) * smoothstep(0.03, 0.30, lmcoord.y);
        float spec = pow(max(dot(nW, normalize(lightDirW() - V)), 0.0), 420.0) * 24.0;
        vec3 tint = toLinear(tex.rgb);
        vec3 body = tint * 0.35 * (ambientSky(h) * lmcoord.y * lmcoord.y * 0.6 + sunLight(h) * 0.08 * sh + LAMP * pow(lmcoord.x, 3.0) * 0.6);
        vec3 c = mix(body, refl, F) + sunLight(h) * spec * sh;
        outColor = vec4(c, mix(0.58, 0.97, F));
        outData = vec4(1.0, encodeNormal(mat3(gbufferModelView) * nW), 1.0);
        return;
    }
    if (tex.a < 0.02) discard;
    vec3 albedo = toLinear(tex.rgb);
    float em = emissiveFor(mat, albedo, lmcoord);
    vec3 c = shade(albedo, normalV, lmcoord, shadowClip, mat, em);
    float a = tex.a;
    float flag = 0.0;
    if (mat == 31) {           // glass: a faint reflection of the sky (+ SSR in composite)
        vec3 nW = mat3(gbufferModelViewInverse) * normalV;
        float F = 0.04 + 0.96 * pow(1.0 - clamp(dot(-V, nW), 0.0, 1.0), 5.0);
        c = mix(c, skyRadiance(reflect(V, nW), sd) * smoothstep(0.1, 0.9, lmcoord.y), F * 0.8);
        a = max(a, F * 0.7);
        flag = 0.5;
    }
    outColor = vec4(c, a);
    outData = vec4(flag, encodeNormal(normalV), 1.0);
}
