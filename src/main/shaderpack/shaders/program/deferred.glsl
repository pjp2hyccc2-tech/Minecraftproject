// =====================================================================================
//  HitoriShaders - deferred (after opaque terrain / entities / the solid hand, before
//  translucent terrain)
//   * hitori:shirakaba: screen-space shadows of the strongest lamp of each pixel - the TV's
//     shadows behind the low table, the cushions, the doll, flickering with it.
//   * the closet: in there the lamps' light comes in only through the gap of the fusuma - a
//     shadow ray toward the strongest lamp (the TV) at full strength; whatever the picture
//     cannot confirm stays dark. When the fusuma is cracked, the slit of light falls across
//     the futon and the floor, and it is the only bright thing.
//   * the phone's LED, the lighter, a screen in the hand, and on a camera feed the camera's IR
//     illuminator (IR reflectance: plush, paper and tatami pale; glass, water and screens dark)
//     on every opaque surface, once per pixel, with screen-space shadows.
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
// strict: a ray that leaves the picture or turns behind the camera counts as blocked
float screenShadow(vec3 viewP, vec3 lightView, float dither, float maxLen, float thickness, bool strict) {
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
        vec3 q = viewP + dir * (0.04 + s * s * marchLen);
        if (q.z > -near * 1.2) { if (strict) occ = 1.0; break; }
        vec3 sc = viewToScreen(q);
        if (sc.x < 0.0 || sc.x > 1.0 || sc.y < 0.0 || sc.y > 1.0) { if (strict) occ = 1.0; break; }
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
    bool openAir = openSky(cellP) > 0.5;
    for (int k = 0; k < 4; k++) {
        float v = tab[k];
        if (abs(v) < 0.5) continue;
        Lamp L = tableUnpack(v, column);
        if (!emitterCasts(L.type) || L.walled) continue;
        vec3 md = abs(floor(cellP) + 0.5 - (L.block + 0.5));
        float X = L.level - (md.x + md.y + md.z);
        bool open = openAir && !L.covered;
        float vis = lampVis(X, A, open, emitterIsScreen(L.type));
        vec3 d = L.pos - wp;
        float dist = length(d);
        float w = vis * lampFalloff(L, dist, open) * lampPattern(L.type, -d / max(dist, 1e-3)) * saturate(dot(N, d / max(dist, 1e-3)))
                * lampGain(L.type);
        if (w > best) {
            best = w;
            lampPos = L.pos;
        }
    }
    return best > 0.0;
}
#endif

// how much of the IR illuminator's light a material gives back (the feed is monochrome: only
// this counts). Cloth, plush, paper, tatami and plaster are pale in IR; dark paint less dark
// than it looks; glass, water, screens and the wet street mirror rather than glow.
float irAlbedo(vec3 albedo, int mat) {
    float l = luma(albedo);
    float k = 0.55;
    if (mat == MAT_FABRIC || mat == MAT_CURTAIN || mat == MAT_PAPER || mat == MAT_TATAMI || mat == MAT_DOLL) k = 0.85;
    else if (mat == MAT_WALL || mat == MAT_WOMAN || mat == MAT_PORCELAIN || mat == MAT_BATH_TILE) k = 0.70;
    else if (mat == MAT_VARNISH || mat == MAT_FLOOR_VARNISH || mat == MAT_VINYL_FLOOR) k = 0.48;
    else if (mat == MAT_GLASS_RAIN || mat == MAT_WATER || mat == MAT_PUDDLE || mat == MAT_EMITTER || mat == MAT_CRT_GLASS || mat == MAT_MIRROR) k = 0.06;
    return mix(l, k, 0.55);
}

void main() {
    vec3 color = texture(colortex0, texcoord).rgb;
    float depth = texture(depthtex0, texcoord).r;
    if (depth >= 1.0) {
        vec3 dirW = viewDirToWorld(screenToView(texcoord, 1.0));
        color = skyGradient(dirW) + max(color, vec3(0.0));
    } else if (!isHandDepth(depth)) {
        vec4 alb = texture(colortex5, texcoord);
        int mat = int(floor(alb.a * 255.0 + 0.5));
        if (mat != MAT_NONE) {
            vec4 nm = texture(colortex6, texcoord);
            vec3 N = octDecode(nm.rg);
            vec3 viewP = screenToView(texcoord, depth);
            vec3 P = viewToPlayer(viewP);
            vec3 Nv = mat3(gbufferModelView) * N;
            float dither = ign(gl_FragCoord.xy);

#ifdef EMITTERS_ON
            // ---- screen-space shadow of the strongest lamp; the closet's slit ------------------
            float share = texture(colortex1, texcoord).g;
            float enclosed = closetAt(P + cameraPosition + N * 0.45);
  #ifdef LAMP_SHADOWS_ON
            bool doShadow = share > 0.02;
  #else
            bool doShadow = share > 0.02 && enclosed > 0.5;
  #endif
            if (doShadow) {
                vec3 lampPos;
                if (dominantLamp(P + cameraPosition, N, nm.b * 15.0, lampPos)) {
                    vec3 lv = playerToView(lampPos - cameraPosition);
                    if (enclosed > 0.5) {
                        float sh = screenShadow(viewP + Nv * 0.02, lv, dither, 3.0, 2.0, true);
                        color *= 1.0 - (1.0 - sh) * saturate(share * 1.15);
                    } else {
                        float sh = screenShadow(viewP + Nv * 0.03, lv, dither, 6.0, 1.4, false);
                        color *= 1.0 - (1.0 - sh) * share * 0.85;
                    }
                }
            }
#endif
            if (anyHandLight()) {
                vec3 albedo = toLinear(alb.rgb);
                vec3 wp = P + cameraPosition;
                float wet = surfaceWetness(wp, N, nm.a, mat);
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
                    if (L.type == LIGHT_SPOT || L.type == LIGHT_POINT)
                        sh = screenShadow(viewP + Nv * 0.04, playerToView(L.pos), dither, 8.0, 1.2, false);
#endif
                    float T = exp(-sigma * length(P - L.pos));      // the light fades in the air
                    vec3 a = L.type == LIGHT_IR ? vec3(irAlbedo(albedo, mat)) : albedo;
                    color += (a * diff * (1.0 - surf.z * 0.8) + spec * specTint) * (T * sh);
                }
            }
        }
    }
    /* RENDERTARGETS: 0 */
    gl_FragData[0] = vec4(color, 1.0);
}
#endif
