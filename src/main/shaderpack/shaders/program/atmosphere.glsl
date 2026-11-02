// =====================================================================================
//  SounanShaders - composite1: the fog goes onto the picture, fluids, bloom bright-pass
//   colortex2 (in-scatter, transmittance) is blurred with a 4x4 depth-aware filter that
//   cancels the Bayer dither of the march; colour = colour * T + in-scatter.
//   colortex0 -> colortex0 (fogged), colortex3 (bloom source)
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
vec4 blurredFog(float depthC) {
    vec2 px = 1.0 / vec2(viewWidth, viewHeight);
    float dc = viewDistance(texcoord, depthC);
    vec4 sum = vec4(0.0);
    float wsum = 0.0;
    vec2 offs[4] = vec2[4](vec2(-1.5, -1.5), vec2(0.5, -1.5), vec2(-1.5, 0.5), vec2(0.5, 0.5));
    for (int i = 0; i < 4; i++) {
        vec2 uv = texcoord + offs[i] * px;
        float d = texture(depthtex0, uv).r;
        float dd = viewDistance(uv, d);
        float w = 1.0 / (1.0 + abs(dd - dc) / max(dc * 0.06, 0.08));
        w *= isHandDepth(d) ? 0.0 : 1.0;
        sum += texture(colortex2, uv) * w;
        wsum += w;
    }
    vec4 center = texture(colortex2, texcoord);
    return wsum > 0.05 ? mix(center, sum / wsum, saturate(wsum * 0.4)) : center;
}

void main() {
    vec3 color = texture(colortex0, texcoord).rgb;
    vec4 aux = texture(colortex1, texcoord);
    float depth = texture(depthtex0, texcoord).r;
    bool sky = depth >= 1.0;
    bool hand = isHandDepth(depth) || aux.b > 0.75;
    bool resists = aux.b > 0.125 && aux.b <= 0.375;

    if (isEyeInWater == 1) {
        // under water: dark, murky, the light dies within a few blocks
        vec3 P = viewToPlayer(screenToView(texcoord, sky ? 1.0 : depth));
        float dist = sky ? 1024.0 : length(P);
        vec3 deep = vec3(0.003, 0.009, 0.012) * (0.3 + 0.7 * eyeSkyFactor());
        color = mix(deep, color, exp(-dist * 0.18));
    } else if (isEyeInWater == 2) {
        vec3 P = viewToPlayer(screenToView(texcoord, sky ? 1.0 : depth));
        color = mix(vec3(1.0, 0.30, 0.05) * 1.2, color, exp(-length(P) * 0.9));
    } else if (isEyeInWater == 3) {
        vec3 P = viewToPlayer(screenToView(texcoord, sky ? 1.0 : depth));
        color = mix(vec3(0.30, 0.33, 0.38), color, exp(-length(P) * 0.6));
    } else if (!hand) {
        vec4 fog = blurredFog(depth);
        float T = fog.a;
        if (resists) T = sqrt(T);                    // the camera ghost: the fog cannot fully hide it
        T = mix(T, pow(T, 0.45), aux.r);             // lamps glow on, deep into the fog
        color = color * T + fog.rgb;
#ifdef DUST_MOTES
        if (anyHandLight()) {
            vec3 P = viewToPlayer(screenToView(texcoord, sky ? 1.0 : depth));
            float len = max(length(P), 1e-4);
            color += handLightMotes(P / len, sky ? 4096.0 : len);
        }
#endif
    }
    color = max(color, vec3(0.0));

    // ---- bloom source: emissive surfaces (lamps, flames, eyes, the moon) + hot spots
    float cam = cameraMode();
    float thr = mix(1.10, 0.30, cam);
    vec3 bloom = color * aux.r * 0.85 + max(color - vec3(thr), vec3(0.0)) * mix(0.30, 0.70, cam);
    bloom = min(bloom, vec3(32.0));

    /* RENDERTARGETS: 0,3 */
    gl_FragData[0] = vec4(color, 1.0);
    gl_FragData[1] = vec4(bloom, 1.0);
}
#endif
