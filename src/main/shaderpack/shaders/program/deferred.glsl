// =====================================================================================
//  ReisatsuShaders - deferred (after opaque terrain / entities / the solid hand, before
//  translucent terrain)
//   * the flashlight and the camera FLASH on every opaque surface, once per pixel: position
//     from depth, normal / albedo / material from colortex5 / colortex6. Screen-space shadows
//     toward the lamp in the hand and toward the flash bulb (a hand above the lens: thin hard
//     shadows under every edge, like a real flash photo).
//   * reisatsu:hidamari: screen-space shadows of the strongest lamp of each pixel (the deep
//     shadows under the tables, chairs and the planters).
//   * the sky behind everything (sky objects already sit in colortex0).
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

// view-space z of a depth buffer value (perspective projection)
float viewZ(float depth) {
    return -gbufferProjection[3][2] / (depth * 2.0 - 1.0 + gbufferProjection[2][2]);
}
// march from the surface toward a light through the depth buffer. Returns 1 = lit.
float screenShadow(vec3 viewP, vec3 lightView, float dither, float maxLen, float thickness) {
    vec3 d = lightView - viewP;
    float len = length(d);
    vec3 dir = d / max(len, 1e-4);
    float marchLen = min(len - 0.25, maxLen);
    if (marchLen <= 0.05) return 1.0;
    const int STEPS = 12;
    float bias = 0.03 + 0.006 * -viewP.z;
    float occ = 0.0;
    for (int i = 0; i < STEPS; i++) {
        float s = (float(i) + dither) / float(STEPS);
        vec3 q = viewP + dir * (0.05 + s * s * marchLen);
        vec3 sc = viewToScreen(q);
        if (sc.x < 0.0 || sc.x > 1.0 || sc.y < 0.0 || sc.y > 1.0) break;
        float d0 = textureLod(depthtex0, sc.xy, 0.0).r;
        if (isHandDepth(d0) || d0 >= 1.0) continue;
        float delta = viewZ(d0) - q.z;                       // > 0: something in front of the sample
        occ = max(occ, smoothstep(bias, bias * 2.5, delta) * (1.0 - smoothstep(thickness, thickness * 1.5, delta)));
        if (occ > 0.99) break;
    }
    return 1.0 - occ;
}

#ifdef EMITTERS_ON
// the lamp that gives a surface most of its light (cheap version of lampsAt)
bool dominantLamp(vec3 wp, vec3 N, float A, out vec3 lampPos) {
    vec3 cellP = wp + N * 0.5;
    ivec2 column = ivec2(floor(cellP.xz));
    vec4 tab = tableFetch(column);
    float best = 0.0;
    lampPos = vec3(0.0);
    for (int k = 0; k < 4; k++) {
        float v = tab[k];
        if (abs(v) < 0.5) continue;
        Lamp L = tableUnpack(v, column);
        if (!emitterCasts(L.type)) continue;
        vec3 md = abs(floor(cellP) + 0.5 - (L.block + 0.5));
        float X = L.level - (md.x + md.y + md.z);
        float vis = (1.0 - smoothstep(A + 0.6, A + 2.2, X)) * smoothstep(-0.5, 0.8, X);
        vec3 d = L.pos - wp;
        float dist = length(d);
        float w = vis * lampFalloff(L, dist) * lampPattern(L.type, -d / max(dist, 1e-3)) * saturate(dot(N, d / max(dist, 1e-3)))
                * EM_GAIN[clamp(L.type, 0, EM_COUNT - 1)];
        if (w > best) {
            best = w;
            lampPos = L.pos;
        }
    }
    return best > 0.0;
}
#endif

void main() {
    vec3 color = texture(colortex0, texcoord).rgb;
    float depth = texture(depthtex0, texcoord).r;
    if (depth >= 1.0) {
        vec3 dirW = viewDirToWorld(screenToView(texcoord, 1.0));
        color = skyGradient(dirW) + max(color, vec3(0.0));
    } else if (!isHandDepth(depth)) {
        vec4 alb = texture(colortex5, texcoord);
        int mat = int(floor(alb.a * 255.0 + 0.5));
        if (mat != MAT_NONE && mat != MAT_SPIRIT) {
            vec4 nm = texture(colortex6, texcoord);
            vec3 N = octDecode(nm.rg);
            vec3 viewP = screenToView(texcoord, depth);
            vec3 P = viewToPlayer(viewP);
            vec3 Nv = mat3(gbufferModelView) * N;
            float dither = ign(gl_FragCoord.xy);
            vec4 spiritMask = texture(colortex8, texcoord);
            float behindSpirit = 1.0 - spiritMask.r * spiritMask.b;   // a faint spirit in front: most of the light reaches us

#ifdef LAMP_SHADOWS_ON
            // ---- screen-space shadow of the strongest lamp -----------------------------------
            float share = texture(colortex1, texcoord).g;
            if (share > 0.02) {
                vec3 lampPos;
                if (dominantLamp(P + cameraPosition, N, nm.b * 15.0, lampPos)) {
                    float sh = screenShadow(viewP + Nv * 0.03, playerToView(lampPos - cameraPosition), dither, 6.0, 1.4);
                    color *= 1.0 - (1.0 - sh) * share * 0.85;
                }
            }
#endif
            if (anyHandLight()) {
                vec3 albedo = toLinear(alb.rgb);
                vec3 wp = P + cameraPosition;
                float wet = 0.0;
#if defined WORLD_HIDAMARI && defined WET_SURFACES
                wet = smoothstep(0.80, 0.95, nm.a) * float(mat == MAT_ASPHALT || mat == MAT_PAVER || mat == MAT_MUD || mat == MAT_WET || mat == MAT_ROOF || mat == MAT_METAL_OUT);
#endif
                vec4 surf = materialSurface(mat, wet);
                float puddle = puddleAmount(wp, mat, N) * wet;
                vec2 gloss = vec2(mix(surf.x, 0.06, puddle), (surf.w > 0.0 || surf.z > 0.5) ? surf.y : -1.0);
                vec3 specTint = mix(vec3(1.0), albedo * 1.6, surf.z);
                float foliage = mat == MAT_FOLIAGE ? 1.0 : 0.0;
                float sigma = handLightFogSigma();
                for (int i = 0; i < HAND_LIGHTS; i++) {
                    HandLight L = handLight(i);
                    if (L.type == LIGHT_NONE) continue;
                    vec3 spec;
                    vec3 diff = handLightSurface(L, P, N, foliage, gloss, spec);
                    if (maxOf(diff) + maxOf(spec) <= 1e-6) continue;
                    float sh = 1.0;
#ifdef BEAM_SHADOWS
                    if (L.type == LIGHT_FLASH) sh = screenShadow(viewP + Nv * 0.02, playerToView(L.pos), dither, 3.0, 0.9);
                    else sh = screenShadow(viewP + Nv * 0.04, playerToView(L.pos), dither, 8.0, 1.2);
#endif
                    float T = exp(-sigma * length(P - L.pos));      // the beam fades in the air
                    color += (albedo * diff * (1.0 - surf.z * 0.8) + spec * specTint) * (T * sh * behindSpirit);
                }
            }
        }
    }
    /* RENDERTARGETS: 0 */
    gl_FragData[0] = vec4(color, 1.0);
}
#endif
