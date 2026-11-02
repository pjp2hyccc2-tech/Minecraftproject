// =====================================================================================
//  MeikonShaders - composite: volumetric moonlight (light shafts in the fog) -> colortex2
//  A few dithered samples per pixel; composite1 blurs and adds them.
// =====================================================================================
#include "/lib/header.glsl"

#ifdef VERTEX_SHADER
out vec2 texcoord;
void main() {
    gl_Position = ftransform();
    texcoord = (gl_TextureMatrix[0] * gl_MultiTexCoord0).xy;
}
#endif

#ifdef FRAGMENT_SHADER
in vec2 texcoord;

void main() {
    vec3 vl = vec3(0.0);
#ifdef VOLUMETRICS_ON
    if (isEyeInWater == 0) {
        float depth = texture(depthtex0, texcoord).r;
        vec3 viewPos = screenToView(texcoord, depth);
        vec3 playerPos = viewToPlayer(viewPos);
        float dist = depth >= 1.0 ? 512.0 : length(playerPos);
        vec3 dirW = playerPos / max(length(playerPos), 1e-4);
        float dither = bayer4(gl_FragCoord.xy);
        vl = volumetricLight(dirW, dist, dither);
    }
#endif
    /* RENDERTARGETS: 2 */
    gl_FragData[0] = vec4(max(vl, vec3(0.0)), 1.0);
}
#endif
