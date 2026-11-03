// =====================================================================================
//  HitoriShaders - composite: the air -> colortex2 (rgb in-scattered light, a transmittance)
//   * fogMarch: the rain haze over the street, the still dusty air of the rooms (the emitter
//     map's cover tile tells which), the glow of every lamp in the air (the light table: the
//     TV's cold flicker in the dust, the sodium halo in the rain), a held flame or screen
//   * beamMarch per hand: the phone's LED cone clipped to the view ray (dust motes are added
//     sharp in composite2)
//  Dithered (4x4 Bayer); composite2 blurs it with a matching 4x4 depth-aware filter.
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
    vec4 res = vec4(0.0, 0.0, 0.0, 1.0);
    float depth = texture(depthtex0, texcoord).r;
    // through clear glass (windows, the cup) the air goes on behind it: march to the opaque
    // surface (depthtex1: before the translucents)
    int mat = int(floor(texture(colortex5, texcoord).a * 255.0 + 0.5));
    if (mat == MAT_GLASS_RAIN || mat == MAT_GLASS_SMALL) depth = texture(depthtex1, texcoord).r;
    if (isEyeInWater == 0 && !isHandDepth(depth)) {
        bool sky = depth >= 1.0;
        vec3 P = viewToPlayer(screenToView(texcoord, sky ? 1.0 : depth));
        float len = max(length(P), 1e-4);
        vec3 rd = P / len;
        float dist = sky ? 4096.0 : len;
        float dither = bayer4(gl_FragCoord.xy);
#ifdef VOLUMETRICS_ON
        res = fogMarch(rd, dist, dither);
#else
        res = fogAnalytic(rd, dist);
#endif
        if (anyHandLight()) {
            for (int i = 0; i < 2; i++) {
                HandLight L = handLight(i);
                if (L.type != LIGHT_SPOT) continue;
                res.rgb += beamMarch(L, rd, dist, fract(dither + 0.5 * float(i) + 0.25));
            }
        }
    }
    /* RENDERTARGETS: 2 */
    gl_FragData[0] = vec4(max(res.rgb, vec3(0.0)), saturate(res.a));
}
#endif
