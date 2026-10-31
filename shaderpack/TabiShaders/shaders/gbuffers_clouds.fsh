#version 330 compatibility
#include "/lib/settings.glsl"
#include "/lib/common.glsl"
#include "/lib/sky.glsl"

uniform sampler2D gtexture;
in vec2 texcoord;
in vec4 glcolor;
in vec3 normalV;
in vec3 playerPos;

/* RENDERTARGETS: 0 */
layout(location = 0) out vec4 outColor;

void main() {
    vec4 tex = texture(gtexture, texcoord);
    if (tex.a < 0.05) discard;
    vec3 sd = sunDirW();
    float h = sd.y;
    float diff = max(dot(normalV, normalize(shadowLightPosition)), 0.0) * 0.5 + 0.5;
    vec3 dirW = normalize(playerPos);
    vec3 c = sunLight(h) * diff * 0.42 + ambientSky(h) * 1.35;
    // sunset glow and silver lining from the sky around the cloud
    c += skyRadiance(normalize(vec3(dirW.x, 0.06, dirW.z)), sd) * 0.45;
    c += sunLight(h) * pow(max(dot(dirW, lightDirW()), 0.0), 6.0) * 0.6;
    float fade = 1.0 - smoothstep(far * 1.2, far * 3.2, length(playerPos.xz));
    outColor = vec4(c * mix(1.0, 0.55, rainStrength), tex.a * glcolor.a * 0.82 * fade);
}
