// =====================================================================================
//  HuntCraft Shaders - final: heat haze, motion blur, bloom combine, tonemap, grading
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

const bool colortex3MipmapEnabled = true;

vec3 sampleScene(vec2 uv) {
    return texture(colortex0, uv).rgb;
}

void main() {
    vec2 uv = texcoord;
    float depth = texture(depthtex0, uv).r;

    // ---- heat haze (ember caldera / nether) --------------------------------------------
#ifdef HEAT_HAZE
    if (DIM_HEAT > 0.0 || isEyeInWater == 2) {
        float heat = max(DIM_HEAT, float(isEyeInWater == 2));
        vec2 n;
        n.x = noiseTex(uv * vec2(3.0, 6.0) + vec2(0.0, frameTimeCounter * 0.35)) - 0.5;
        n.y = noiseTex(uv * vec2(5.0, 3.0) + vec2(frameTimeCounter * 0.21, frameTimeCounter * 0.5)) - 0.5;
        float amt = 0.0025 * heat;
        // stronger far away and near the bottom of the screen
        amt *= 0.4 + 0.6 * (depth >= 1.0 ? 1.0 : saturate(linearDepth(depth) * far / 60.0));
        uv += n * amt;
        uv = clamp(uv, vec2(0.001), vec2(0.999));
    }
#endif

    vec3 color = sampleScene(uv);

    // ---- motion blur -------------------------------------------------------------------
#ifdef MOTION_BLUR
    if (depth > 0.56) {
        vec3 viewPos = screenToView(uv, depth);
        vec3 playerPos = viewToPlayer(viewPos);
        vec3 prevPlayer = playerPos + cameraPosition - previousCameraPosition;
        vec4 prevClip = gbufferPreviousProjection * (gbufferPreviousModelView * vec4(prevPlayer, 1.0));
        vec2 prevUv = (prevClip.xy / prevClip.w) * 0.5 + 0.5;
        vec2 vel = (uv - prevUv) * MOTION_BLUR_STRENGTH * 0.4;
        float vl = length(vel);
        if (vl > 1e-5) {
            vel *= min(vl, 0.03) / vl;
            float d = bayer8(gl_FragCoord.xy);
            vec3 acc = vec3(0.0);
            for (int i = 0; i < 6; i++) {
                vec2 o = vel * ((float(i) + d) / 6.0 - 0.5);
                acc += sampleScene(clamp(uv + o, vec2(0.001), vec2(0.999)));
            }
            color = acc / 6.0;
        }
    }
#endif

    // ---- bloom -------------------------------------------------------------------------
#ifdef BLOOM
    vec3 bloom = texture(colortex3, uv).rgb * 0.75 + textureLod(colortex3, uv, 3.0).rgb * 0.5;
    color += bloom * BLOOM_STRENGTH * 0.35;
#endif

    // ---- exposure & tonemap ------------------------------------------------------------
    float exposure = EXPOSURE;
    exposure *= mix(1.5, 1.0, dayFactor());
    exposure *= 1.0 - blindness * 0.9;
    color *= exposure;
    color = acesFitted(color);
    color = linearToSrgb(color);
    color = colorGrade(color);
    color = vignette(color, texcoord, 1.0);

    // dither to kill banding
    color += (bayer16(gl_FragCoord.xy) - 0.5) / 255.0;

    gl_FragData[0] = vec4(saturate3(color), 1.0);
}
#endif
