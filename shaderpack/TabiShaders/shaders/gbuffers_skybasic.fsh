#version 330 compatibility
#include "/lib/settings.glsl"
#include "/lib/common.glsl"
#include "/lib/sky.glsl"

uniform int renderStage;
in vec4 glcolor;

/* RENDERTARGETS: 0 */
layout(location = 0) out vec4 outColor;

void main() {
#ifdef MC_RENDER_STAGE_STARS
    if (renderStage == MC_RENDER_STAGE_STARS) {
        float tw = 0.65 + 0.35 * sin(frameTimeCounter * 2.7 + hash12(floor(gl_FragCoord.xy * 0.5)) * 40.0);
        vec4 sp = gbufferProjectionInverse * vec4(gl_FragCoord.xy / vec2(viewWidth, viewHeight) * 2.0 - 1.0, 1.0, 1.0);
        float up = (mat3(gbufferModelViewInverse) * normalize(sp.xyz / sp.w)).y;
        float vis = tNight(sunDirW().y) * (1.0 - rainStrength) * smoothstep(0.0, 0.08, up);
        outColor = vec4(glcolor.rgb * vec3(0.85, 0.92, 1.15) * 2.4 * tw * vis, glcolor.a);
        return;
    }
#endif
#ifdef MC_RENDER_STAGE_SUNSET
    if (renderStage == MC_RENDER_STAGE_SUNSET) discard;   // the sky function paints its own sunset
#endif
    vec4 ndc = vec4(gl_FragCoord.xy / vec2(viewWidth, viewHeight) * 2.0 - 1.0, 1.0, 1.0);
    vec4 vp = gbufferProjectionInverse * ndc;
    vec3 dirW = normalize(mat3(gbufferModelViewInverse) * (vp.xyz / vp.w));
    outColor = vec4(skyRadiance(dirW, sunDirW()), 1.0);
}
