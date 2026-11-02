// =====================================================================================
//  MeikonShaders - composite1: fog, lantern glow in the fog, light shafts, fluids,
//  bloom bright-pass.   colortex0 -> colortex0 (fogged), colortex3 (bloom source)
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

// light shafts are noisy (few samples): depth-aware 5 tap blur
vec3 blurredVolumetrics(float depthCenter) {
#ifdef VOLUMETRICS_ON
    vec2 px = 1.0 / vec2(viewWidth, viewHeight);
    vec3 sum = texture(colortex2, texcoord).rgb;
    float wsum = 1.0;
    float lc = screenToView(texcoord, depthCenter).z;
    vec2 offs[4] = vec2[4](vec2(1.5, 0.5), vec2(-0.5, 1.5), vec2(-1.5, -0.5), vec2(0.5, -1.5));
    for (int i = 0; i < 4; i++) {
        vec2 uv = texcoord + offs[i] * px;
        float d = texture(depthtex0, uv).r;
        float lz = screenToView(uv, d).z;
        float w = 1.0 / (1.0 + abs(lz - lc) * 2.0);
        sum += texture(colortex2, uv).rgb * w;
        wsum += w;
    }
    return sum / wsum;
#else
    return vec3(0.0);
#endif
}

void main() {
    vec3 color = texture(colortex0, texcoord).rgb;
    vec4 aux = texture(colortex1, texcoord);
    float depth = texture(depthtex0, texcoord).r;
    bool sky = depth >= 1.0;
    bool hand = aux.b > 0.75;
    bool cloud = aux.b > 0.375 && aux.b <= 0.75;
    bool bride = aux.b > 0.125 && aux.b <= 0.375;

    vec3 viewPos = screenToView(texcoord, sky ? 1.0 : depth);
    vec3 playerPos = viewToPlayer(viewPos);
    float dist = sky ? 1024.0 : length(playerPos);
    vec3 dirW = playerPos / max(length(playerPos), 1e-4);

    if (isEyeInWater == 1) {
        // under water: dark, murky, the light dies within a few blocks
        vec3 deep = vec3(0.004, 0.012, 0.016) * (0.3 + 0.7 * eyeSkyFactor()) + FLAME_DEEP * 0.004 * float(heldLightLevel() > 0.0);
        color = mix(deep, color, exp(-dist * 0.16));
    } else if (isEyeInWater == 2) {
        color = mix(vec3(1.0, 0.30, 0.05) * 1.2, color, exp(-dist * 0.9));
    } else if (isEyeInWater == 3) {
        color = mix(vec3(0.30, 0.33, 0.38), color, exp(-dist * 0.6));
    } else if (!hand) {
        if (sky || cloud) {
            // the sky keeps its gradient; only the horizon sinks into the haze
#ifdef WORLD_OVERWORLD
            float haze = (1.0 - smoothstep(0.0, 0.35, dirW.y)) * (cloud ? 0.65 : 0.25) * (1.0 + rainAmount());
            color = mix(color, fogColorFor(dirW), saturate(haze));
#endif
#ifdef WORLD_KAKURIYO
            float closeIn = max(darknessFactor, blindness);
            color = mix(color, fogColorFor(dirW), saturate(closeIn * 0.85 + (cloud ? 0.35 : 0.0)));
#endif
        } else {
            float T = fogTransmittance(dist, dirW);
            if (bride) T = pow(T, 0.5);               // the fog cannot fully hide her
            T = mix(T, pow(T, 0.45), aux.r);          // lamps glow on, deep into the fog
            vec3 fogC = fogColorFor(dirW) + lanternFogGlow(dist);
            // light pools around lamps glow in the mist
            fogC += FLAME_DEEP * aux.g * aux.g * 0.020;
            color = mix(fogC, color, T);
        }
#ifdef WORLD_KAKURIYO
        const float SKY_SHAFTS = 0.12;            // keep the crimson-black sky dark
#else
        const float SKY_SHAFTS = 0.40;
#endif
        color += blurredVolumetrics(sky ? 1.0 : depth) * (sky || cloud ? SKY_SHAFTS : 1.0);
    }

    color = max(color, vec3(0.0));

    // ---- bloom source: emissive surfaces (lamps, flames, the moon, the torii) + hot spots
    float emissive = aux.r;
    vec3 bloom = color * emissive * 0.85 + max(color - vec3(1.1), vec3(0.0)) * 0.30;
    bloom = min(bloom, vec3(32.0));

    /* RENDERTARGETS: 0,3 */
    gl_FragData[0] = vec4(color, 1.0);
    gl_FragData[1] = vec4(bloom, 1.0);
}
#endif
