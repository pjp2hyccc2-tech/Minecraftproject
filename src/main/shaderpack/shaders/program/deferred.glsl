// =====================================================================================
//  SounanShaders - deferred (after opaque terrain / entities / the solid hand, before
//  translucents)
//   * the hand lights on every opaque surface, once per pixel: position from depth,
//     normal / albedo / gloss from colortex6 / colortex5. Flashlight: hot spot, soft edge,
//     reflector rings, distance falloff, N.L, wet glints; screen-space shadows of the beam
//     (the lamp sits below and beside the eye, so trunks throw thin shadow edges).
//   * the sky gradient behind everything (sky objects already sit in colortex0).
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

#ifdef BEAM_SHADOWS
// march from the surface toward the lamp through the depth buffer. Returns 1 = lit.
float beamShadow(vec3 viewP, vec3 lampView, float dither) {
    vec3 d = lampView - viewP;
    float len = length(d);
    vec3 dir = d / max(len, 1e-4);
    float maxLen = min(len - 0.30, 8.0);
    if (maxLen <= 0.05) return 1.0;
    const int STEPS = 12;
    float bias = 0.03 + 0.006 * -viewP.z;
    float occ = 0.0;
    for (int i = 0; i < STEPS; i++) {
        float s = (float(i) + dither) / float(STEPS);
        vec3 q = viewP + dir * (0.05 + s * s * maxLen);
        vec3 sc = viewToScreen(q);
        if (sc.x < 0.0 || sc.x > 1.0 || sc.y < 0.0 || sc.y > 1.0) break;
        float d0 = textureLod(depthtex0, sc.xy, 0.0).r;
        if (isHandDepth(d0) || d0 >= 1.0) continue;
        float delta = screenToView(sc.xy, d0).z - q.z;      // > 0: something in front of the sample
        occ = max(occ, smoothstep(bias, bias * 2.5, delta) * (1.0 - smoothstep(1.2, 1.8, delta)));
        if (occ > 0.99) break;
    }
    return 1.0 - occ;
}
#endif

void main() {
    vec3 color = texture(colortex0, texcoord).rgb;
    float depth = texture(depthtex0, texcoord).r;
    if (depth >= 1.0) {
        vec3 dirW = viewDirToWorld(screenToView(texcoord, 1.0));
        color = skyGradient(dirW) + max(color, vec3(0.0));
    } else if (anyHandLight() && !isHandDepth(depth)) {
        vec4 alb = texture(colortex5, texcoord);
        if (alb.a > 0.25) {
            vec4 nm = texture(colortex6, texcoord);
            vec3 N = octDecode(nm.rg);
            vec3 viewP = screenToView(texcoord, depth);
            vec3 P = viewToPlayer(viewP);
            vec3 albedo = toLinear(alb.rgb);
            float foliage = alb.a < 0.75 ? 1.0 : 0.0;
            vec2 gloss = vec2(nm.b * 2.0, nm.a);
            float dither = ign(gl_FragCoord.xy);
            float sigma = handLightFogSigma();
            vec3 Nv = mat3(gbufferModelView) * N;
            for (int i = 0; i < 2; i++) {
                HandLight L = handLight(i);
                if (L.type == LIGHT_NONE) continue;
                vec3 spec;
                vec3 diff = handLightSurface(L, P, N, foliage, gloss, spec);
                if (maxOf(diff) + maxOf(spec) <= 1e-6) continue;
                float sh = 1.0;
#ifdef BEAM_SHADOWS
                sh = beamShadow(viewP + Nv * 0.04, playerToView(L.pos), dither);
#endif
                float T = exp(-sigma * length(P - L.pos));      // the beam fades in the fog
                color += (albedo * diff + spec) * (T * sh);
            }
        }
    }
    /* RENDERTARGETS: 0 */
    gl_FragData[0] = vec4(color, 1.0);
}
#endif
