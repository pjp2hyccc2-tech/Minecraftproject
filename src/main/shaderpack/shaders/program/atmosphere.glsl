// =====================================================================================
//  HitoriShaders - composite2: the air goes onto the picture, the rain, the haze round the
//  woman on a feed, the frame history, bloom bright-pass
//   * colortex2 (in-scatter, transmittance) is blurred with a 4x4 depth-aware filter that
//     cancels the Bayer dither of the march; colour = colour * T + in-scatter.
//   * the dust in the phone's light (sharp, after the blur), the rain falling outside where the
//     street lamp catches it.
//   * on a camera feed the air wavers where the woman stands (colortex8) - only a camera sees her.
//   * the picture is kept for the next frame (colortex9) and last frame's is handed to final
//     (colortex2, free after this pass): the feed's interlaced fields and its lag.
//   colortex0 -> colortex0 (fogged), colortex3 (bloom source), colortex9 (this frame),
//   colortex2 (the previous frame)
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
    vec4 prevFrame = texture(colortex9, texcoord);

    // ---- the woman on a feed: the air round her wavers ---------------------------------------
#ifdef APPARITION_HAZE_ON
    float hazeAmt = 0.0;
    if (cctv() > 0.5) {
        vec4 s0 = texture(colortex8, uv);
        vec4 s1 = texture(colortex8, uv + vec2(7.0, 0.0) * px);
        vec4 s2 = texture(colortex8, uv - vec2(7.0, 0.0) * px);
        vec4 s3 = texture(colortex8, uv + vec2(0.0, 7.0) * px);
        vec4 s4 = texture(colortex8, uv - vec2(0.0, 7.0) * px);
        hazeAmt = max(s0.a, (s1.a + s2.a + s3.a + s4.a) * 0.22);
        if (hazeAmt > 0.0) {
            vec2 q = uv * vec2(aspectRatio, 1.0) * 30.0;
            vec2 w = vec2(vnoise2(q + vec2(0.0, -t * 1.9)), vnoise2(q * 1.3 + vec2(5.1, -t * 2.3))) - 0.5;
            uv += w * hazeAmt * 0.0050 * (1.0 + 0.6 * fear());
        }
    }
#endif

    vec3 color = texture(colortex0, uv).rgb;
    vec4 aux = texture(colortex1, uv);
    float depth = texture(depthtex0, uv).r;
    int frontMat = int(floor(texture(colortex5, uv).a * 255.0 + 0.5));
    if (frontMat == MAT_GLASS_RAIN || frontMat == MAT_GLASS_SMALL) depth = texture(depthtex1, uv).r;
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
        T = mix(T, pow(T, 0.45), aux.r);             // lamps glow on, deep into the haze
        color = color * T + fog.rgb;
        vec3 P = viewToPlayer(screenToView(uv, sky ? 1.0 : depth));
        float len = max(length(P), 1e-4);
        float dist = sky ? 4096.0 : len;
#ifdef DUST_MOTES
        if (anyHandLight()) color += handLightMotes(P / len, dist);
#endif
#ifdef RAIN_STREAKS_ON
        color += rainStreaks(P / len, dist) * mix(1.0, T, 0.5);
#endif
    }
    color = max(color, vec3(0.0));
    // the lighter's flame on its hood (the art's lighter has none)
    color += lighterFlameSprite(texcoord);

    // ---- bloom source: emissive surfaces (the TV's screen, the lamps, the REC LED) -------------
    float cam = cctv();
    float thr = mix(1.10, 0.55, cam);                // a camera's sensor blooms early
    vec3 bloom = color * aux.r * mix(0.85, 1.10, cam) + max(color - vec3(thr), vec3(0.0)) * mix(0.30, 0.55, cam);
    bloom = min(bloom, vec3(32.0));

    /* RENDERTARGETS: 0,3,9,2 */
    gl_FragData[0] = vec4(color, 1.0);
    gl_FragData[1] = vec4(bloom, 1.0);
    gl_FragData[2] = vec4(color, 1.0);
    gl_FragData[3] = prevFrame;
}
#endif
