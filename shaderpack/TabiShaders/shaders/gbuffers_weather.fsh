#version 330 compatibility
#include "/lib/settings.glsl"
#include "/lib/common.glsl"

uniform sampler2D gtexture;
in vec2 texcoord;
in vec2 lmcoord;
in vec4 glcolor;

/* RENDERTARGETS: 0 */
layout(location = 0) out vec4 outColor;

void main() {
    vec4 tex = texture(gtexture, texcoord) * glcolor;
    if (tex.a < 0.02) discard;
    vec3 c = toLinear(tex.rgb) * (ambientSky(sunDirW().y) * 1.3 + LAMP * pow(lmcoord.x, 3.0) * 1.6);
    outColor = vec4(c, tex.a * 0.42);
}
