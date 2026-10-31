#version 330 compatibility
// screen-space reflections on water / glass, aerial-perspective fog and mist, volumetric light shafts
#include "/lib/settings.glsl"
#define SHADOW_SAMPLING
#include "/lib/common.glsl"
#include "/lib/shadow.glsl"
#include "/lib/sky.glsl"

/*
const int colortex0Format = RGBA16F;
const int colortex1Format = RGBA16;
const int colortex3Format = RGBA16F;
const bool colortex3MipmapEnabled = true;
const vec4 colortex1ClearColor = vec4(0.0, 0.0, 0.0, 0.0);
*/

uniform sampler2D colortex0;
uniform sampler2D colortex1;
uniform sampler2D depthtex0;
uniform sampler2D depthtex1;
uniform mat4 shadowModelView;
uniform mat4 shadowProjection;
uniform float near;

in vec2 texcoord;

/* RENDERTARGETS: 0 */
layout(location = 0) out vec4 outColor;

vec3 screenToView(vec2 uv, float depth) {
    vec4 p = gbufferProjectionInverse * vec4(vec3(uv, depth) * 2.0 - 1.0, 1.0);
    return p.xyz / p.w;
}
vec3 viewToScreen(vec3 vp) {
    vec4 p = gbufferProjection * vec4(vp, 1.0);
    return (p.xyz / p.w) * 0.5 + 0.5;
}

#ifdef WATER_REFLECTIONS
vec4 ssr(vec3 vp, vec3 nV, float dither) {
    vec3 rd = normalize(reflect(normalize(vp), nV));
    if (rd.z > 0.3) return vec4(0.0);
    float stepL = 0.35 + length(vp) * 0.025;
    vec3 dv = rd * stepL;
    vec3 p = vp + nV * 0.05 + dv * dither;
    for (int i = 0; i < 28; i++) {
        p += dv;
        vec3 s = viewToScreen(p);
        if (s.x < 0.0 || s.x > 1.0 || s.y < 0.0 || s.y > 1.0 || s.z >= 1.0 || p.z > -near) break;
        float d = texture(depthtex1, s.xy).r;
        float diff = screenToView(s.xy, d).z - p.z;
        if (diff > 0.0 && d < 1.0) {
            if (diff > length(dv) * 3.0 + 1.0) {        // behind a thin object: keep marching
                dv *= 1.15;
                continue;
            }
            vec3 a = p - dv, b = p;
            for (int j = 0; j < 5; j++) {
                vec3 m = (a + b) * 0.5;
                vec3 ms = viewToScreen(m);
                if (screenToView(ms.xy, texture(depthtex1, ms.xy).r).z - m.z > 0.0) b = m; else a = m;
            }
            vec3 hs = viewToScreen(b);
            vec2 e = smoothstep(vec2(0.0), vec2(0.08), hs.xy) * (1.0 - smoothstep(vec2(0.92), vec2(1.0), hs.xy));
            return vec4(texture(colortex0, hs.xy).rgb, e.x * e.y * (1.0 - float(i) / 56.0));
        }
        dv *= 1.12;
    }
    return vec4(0.0);
}
#endif

float fogDensity(float h) {
    float d = 0.0009 + 0.0022 * rMount() + 0.0005 * rSea();
    d += 0.0040 * rMount() * tGolden(h);                    // morning / evening mist in the misty valley
    d *= mix(1.0, 0.55, tNight(h));                         // crisp night air keeps city lights sharp
    d += 0.010 * rainStrength;
    return d * FOG_DENSITY;
}

vec3 applyFog(vec3 col, vec3 pPos, vec3 sd) {
    float dist = length(pPos);
    vec3 dir = pPos / max(dist, 1e-3);
    float wy = pPos.y + cameraPosition.y;
    float hf = exp(-max(wy - 66.0, 0.0) * 0.045);          // mist hugs the valleys and the sea
    float amount = 1.0 - exp(-dist * fogDensity(sd.y) * (0.6 + 0.9 * hf));
    float edge = smoothstep(far * 0.80, far * 1.0, dist);  // fade the edge of the loaded world into the sky
    vec3 fc = skyRadiance(dir, sd);
    fc += sunLight(sd.y) * pow(max(dot(dir, lightDirW()), 0.0), 8.0) * 0.12;
    return mix(col, fc, clamp(max(amount, edge), 0.0, 1.0));
}

#ifdef VOLUMETRIC_LIGHT
vec3 volumetric(vec3 pPos, float dither, vec3 sd) {
    float h = sd.y;
    float dist = min(length(pPos), shadowDistance * 0.8);
    vec3 dir = normalize(pPos);
    float acc = 0.0;
    for (int i = 0; i < 10; i++) {
        vec3 p = dir * dist * ((float(i) + dither) / 10.0);
        vec4 sp = shadowProjection * (shadowModelView * vec4(p, 1.0));
        vec3 sc = distortShadow(sp.xyz) * 0.5 + 0.5;
        acc += texture(shadowtex0, vec3(sc.xy, sc.z - 0.0006));
    }
    acc *= 0.1;
    float g = 0.72;
    float cosT = dot(dir, lightDirW());
    float phase = (1.0 - g * g) / pow(1.0 + g * g - 2.0 * g * cosT, 1.5) / (4.0 * PI);
    float dens = 0.020 + 0.050 * tGolden(h) + 0.035 * rMount() + 0.020 * rainStrength;
    dens *= 1.0 - tNight(h) * 0.6;
    float eyeSky = float(eyeBrightnessSmooth.y) / 240.0;
    return sunLight(h) * acc * (phase * 6.0 + 0.15) * dens * (1.0 - exp(-dist * 0.03)) * VL_STRENGTH * (0.35 + 0.65 * eyeSky);
}
#endif

void main() {
    vec3 col = texture(colortex0, texcoord).rgb;
    float d0 = texture(depthtex0, texcoord).r;
    vec3 vp = screenToView(texcoord, d0);
    vec3 pPos = mat3(gbufferModelViewInverse) * vp + gbufferModelViewInverse[3].xyz;
    vec3 sd = sunDirW();
    float dither = ign(gl_FragCoord.xy);
#ifdef WATER_REFLECTIONS
    vec4 data = texture(colortex1, texcoord);
    if (data.r > 0.25 && d0 < 1.0 && isEyeInWater == 0) {
        vec3 nV = decodeNormal(data.gb);
        vec4 r = ssr(vp, nV, dither);
        if (r.a > 0.0) {
            float f0 = data.r > 0.75 ? 0.02 : 0.04;
            float F = f0 + (1.0 - f0) * pow(1.0 - clamp(dot(-normalize(vp), nV), 0.0, 1.0), 5.0);
            col = mix(col, r.rgb, clamp(F * r.a * (data.r > 0.75 ? 1.0 : 0.6), 0.0, 1.0));
        }
    }
#endif
    if (isEyeInWater == 1) {
        float dist = length(pPos);
        vec3 deep = vec3(0.01, 0.06, 0.07) * (ambientSky(sd.y) + 0.05);
        col = mix(col, deep, 1.0 - exp(-dist * 0.09));
    } else if (d0 < 1.0) {
        col = applyFog(col, pPos, sd);
    }
#ifdef VOLUMETRIC_LIGHT
    if (isEyeInWater == 0) col += volumetric(pPos, dither, sd);
#endif
    outColor = vec4(col, 1.0);
}
