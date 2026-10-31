#version 330 compatibility
#include "/lib/settings.glsl"
#include "/lib/common.glsl"

uniform sampler2D gtexture;
uniform int renderStage;
in vec2 texcoord;
in vec4 glcolor;

/* RENDERTARGETS: 0 */
layout(location = 0) out vec4 outColor;

void main() {
    vec4 c = texture(gtexture, texcoord) * glcolor;
    vec3 lin = toLinear(c.rgb);
    float k = 1.5;
#ifdef MC_RENDER_STAGE_SUN
    if (renderStage == MC_RENDER_STAGE_SUN) {
        float h = sunDirW().y;
        lin *= mix(vec3(1.0, 0.55, 0.25), vec3(1.0, 0.95, 0.85), smoothstep(0.0, 0.3, h));
        k = 10.0;                                       // HDR sun disc -> bloom + glare
    }
#endif
#ifdef MC_RENDER_STAGE_MOON
    if (renderStage == MC_RENDER_STAGE_MOON) {
        lin *= vec3(0.85, 0.92, 1.1);
        k = 2.4;
    }
#endif
    outColor = vec4(lin * k * (1.0 - rainStrength * 0.9), c.a);
}
