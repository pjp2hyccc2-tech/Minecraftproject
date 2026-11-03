// =====================================================================================
//  ReisatsuShaders - composite2: the air goes onto the picture, the spirits' traces,
//  the finder's afterimage, bloom bright-pass
//   * colortex2 (in-scatter, transmittance) is blurred with a 4x4 depth-aware filter that
//     cancels the Bayer dither of the march; colour = colour * T + in-scatter.
//   * where a spirit stands (colortex8) the picture wavers like heat haze - even when the
//     eye cannot see it (keep it subtle: the player should ALMOST see something) - and its
//     colours split.
//   * the finder's afterimage: the spirits' cold glow fades slowly in colortex9 (kept
//     between frames), so a moving spirit drags a short trail.
//   colortex0 -> colortex0 (fogged), colortex3 (bloom source), colortex9 (afterimage)
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

float viewDistance(vec2 uv, float d) {
    return d >= 1.0 ? 4096.0 : length(screenToView(uv, d));
}

// four bilinear taps exactly between texels = a 4x4 box (cancels the 4x4 Bayer dither),
// weighted by depth similarity
vec4 blurredFog(vec2 uv, float depthC) {
    vec2 px = 1.0 / vec2(viewWidth, viewHeight);
    float dc = viewDistance(uv, depthC);
    vec4 sum = vec4(0.0);
    float wsum = 0.0;
    vec2 offs[4] = vec2[4](vec2(-1.5, -1.5), vec2(0.5, -1.5), vec2(-1.5, 0.5), vec2(0.5, 0.5));
    for (int i = 0; i < 4; i++) {
        vec2 u = uv + offs[i] * px;
        float d = texture(depthtex0, u).r;
        float dd = viewDistance(u, d);
        float w = 1.0 / (1.0 + abs(dd - dc) / max(dc * 0.06, 0.08));
        w *= isHandDepth(d) ? 0.0 : 1.0;
        sum += texture(colortex2, u) * w;
        wsum += w;
    }
    vec4 center = texture(colortex2, uv);
    return wsum > 0.05 ? mix(center, sum / wsum, saturate(wsum * 0.4)) : center;
}

void main() {
    vec2 uv = texcoord;
    vec2 px = 1.0 / vec2(viewWidth, viewHeight);
    float t = frameTimeCounter;

    // ---- the spirits: heat haze where one stands, a colour split around it ----------------
    vec4 sp = texture(colortex8, uv);
    float hazeAmt = 0.0;
    float splitAmt = 0.0;
#ifdef SPIRIT_HAZE_ON
    // dilate a little: the air around the body wavers too
    vec4 s1 = texture(colortex8, uv + vec2(6.0, 0.0) * px);
    vec4 s2 = texture(colortex8, uv - vec2(6.0, 0.0) * px);
    vec4 s3 = texture(colortex8, uv + vec2(0.0, 6.0) * px);
    vec4 s4 = texture(colortex8, uv - vec2(0.0, 6.0) * px);
    hazeAmt = max(sp.a, (s1.a + s2.a + s3.a + s4.a) * 0.18) * max(max(sp.r, s1.r), max(s2.r, max(s3.r, s4.r)));
    if (hazeAmt > 0.0) {
        vec2 q = uv * vec2(aspectRatio, 1.0) * 26.0;
        vec2 w = vec2(vnoise2(q + vec2(0.0, -t * 1.7)), vnoise2(q * 1.3 + vec2(5.1, -t * 2.1))) - 0.5;
        uv += w * hazeAmt * 0.0042 * (1.0 + 0.6 * fear());
    }
    splitAmt = max(sp.r, max(max(s1.r, s2.r), max(s3.r, s4.r)));
#endif

    vec3 color = texture(colortex0, uv).rgb;
#ifdef SPIRIT_HAZE_ON
    if (splitAmt > 0.0) {
        // the spirit's colours do not quite line up (strongest at its edges, where the mask changes)
        vec2 dir = vec2(1.0, 0.25) * px * (2.5 + 2.5 * fear() + 3.0 * finderMode());
        float edge = abs(texture(colortex8, uv + dir * 2.0).r - texture(colortex8, uv - dir * 2.0).r);
        float k = splitAmt * (0.35 + 0.65 * edge) * (0.25 + 0.75 * max(sp.b, finderMode()));
        vec3 cr = texture(colortex0, uv + dir * 1.5).rgb;
        vec3 cb = texture(colortex0, uv - dir * 1.5).rgb;
        color = mix(color, vec3(cr.r, color.g, cb.b), saturate(k));
    }
#endif

    vec4 aux = texture(colortex1, uv);
    float depth = texture(depthtex0, uv).r;
    int frontMat = int(floor(texture(colortex5, uv).a * 255.0 + 0.5));
    if (frontMat == MAT_WINDOW || frontMat == MAT_GLASS_SMALL) depth = texture(depthtex1, uv).r;
    bool sky = depth >= 1.0;
    bool hand = isHandDepth(depth) || aux.b > 0.75;

    if (isEyeInWater == 1) {
        vec3 P = viewToPlayer(screenToView(uv, sky ? 1.0 : depth));
        float dist = sky ? 1024.0 : length(P);
        vec3 deep = vec3(0.003, 0.009, 0.012) * (0.3 + 0.7 * eyeSkyFactor());
        color = mix(deep, color, exp(-dist * 0.18));
    } else if (isEyeInWater == 2) {
        vec3 P = viewToPlayer(screenToView(uv, sky ? 1.0 : depth));
        color = mix(vec3(1.0, 0.30, 0.05) * 1.2, color, exp(-length(P) * 0.9));
    } else if (isEyeInWater == 3) {
        vec3 P = viewToPlayer(screenToView(uv, sky ? 1.0 : depth));
        color = mix(vec3(0.30, 0.33, 0.38), color, exp(-length(P) * 0.6));
    } else if (!hand) {
        vec4 fog = blurredFog(uv, depth);
        float T = fog.a;
        T = mix(T, pow(T, 0.45), aux.r);             // lamps glow on, deep into the mist
        color = color * T + fog.rgb;
#ifdef DUST_MOTES
        if (anyHandLight()) {
            vec3 P = viewToPlayer(screenToView(uv, sky ? 1.0 : depth));
            float len = max(length(P), 1e-4);
            color += handLightMotes(P / len, sky ? 4096.0 : len);
        }
#endif
    }
    color = max(color, vec3(0.0));

    // ---- the finder's afterimage --------------------------------------------------------
    vec4 hist = vec4(0.0);
#ifdef AFTERIMAGE_ON
    vec3 prev = texture(colortex9, texcoord).rgb;
    float decay = exp(-clamp(frameTime, 0.0, 0.25) / 0.11);
    vec3 now = vec3(0.55, 0.80, 1.00) * sp.g;
    hist = vec4(max(now, prev * decay * finderMode()), 1.0);
#endif

    // ---- bloom source: emissive surfaces (lamps, signs, embers, the spirits in the finder)
    float fm = finderMode();
    float thr = mix(1.10, 0.90, fm);
    vec3 bloom = color * aux.r * 0.85 + max(color - vec3(thr), vec3(0.0)) * 0.30;
    bloom = min(bloom, vec3(32.0));

    /* RENDERTARGETS: 0,3,9 */
    gl_FragData[0] = vec4(color, 1.0);
    gl_FragData[1] = vec4(bloom, 1.0);
    gl_FragData[2] = hist;
}
#endif
