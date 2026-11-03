// =====================================================================================
//  HitoriShaders - composite: the air -> colortex2 (rgb in-scattered light, a transmittance)
//   * fogMarch: the low mist over the wet lot, dust indoors (the emitter map's cover tile
//     tells which), the glow of every lamp in the air (the light table), the flash
//   * beamMarch per hand: the flashlight's cone clipped to the view ray, wisps drifting
//     through it (dust motes are added sharp in composite2)
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
    // through clear glass (shop windows, glasses) the air goes on behind it: march to the
    // opaque surface (depthtex1: before the translucents)
    int mat = int(floor(texture(colortex5, texcoord).a * 255.0 + 0.5));
    if (mat == MAT_WINDOW || mat == MAT_GLASS_SMALL) depth = texture(depthtex1, texcoord).r;
#ifdef WORLD_HIDAMARI
    vec4 sp = texture(colortex8, texcoord);
    float spiritVis = 1.0;
    float spiritDepth = depth;
    if (sp.r > 0.5 && sp.b < 0.99) {
        spiritVis = sp.b;
        depth = behindSpiritDepth(texcoord, depth);
    }
#endif
    if (isEyeInWater == 0 && !isHandDepth(depth)) {
        bool sky = depth >= 1.0;
        vec3 P = viewToPlayer(screenToView(texcoord, sky ? 1.0 : depth));
        float len = max(length(P), 1e-4);
        vec3 rd = P / len;
        float dist = sky ? 4096.0 : len;
#ifdef WORLD_HIDAMARI
        // a partly visible spirit: the air in front of it, mixed with the air behind it
        if (spiritVis < 1.0) {
            float ds = length(screenToView(texcoord, spiritDepth));
            dist = mix(dist, ds, spiritVis);
        }
#endif
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
