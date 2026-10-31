#version 330 compatibility
// bloom bright-pass (mipmapped, read back in final)
#include "/lib/settings.glsl"
#include "/lib/common.glsl"

uniform sampler2D colortex0;
in vec2 texcoord;

/* RENDERTARGETS: 3 */
layout(location = 0) out vec4 outColor;

void main() {
    vec2 px = 1.0 / vec2(viewWidth, viewHeight);
    vec3 c = texture(colortex0, texcoord).rgb * 0.4;
    c += texture(colortex0, texcoord + px * vec2(1.0, 1.0)).rgb * 0.15;
    c += texture(colortex0, texcoord + px * vec2(-1.0, 1.0)).rgb * 0.15;
    c += texture(colortex0, texcoord + px * vec2(1.0, -1.0)).rgb * 0.15;
    c += texture(colortex0, texcoord + px * vec2(-1.0, -1.0)).rgb * 0.15;
    float h = sunDirW().y;
    float threshold = mix(2.2, 0.45, max(tNight(h), tBlue(h)));   // at night every lamp and window blooms
    float l = luma(c);
    float k = max(l - threshold, 0.0) / max(l, 1e-4);
    outColor = vec4(min(c * k, vec3(60.0)), 1.0);
}
