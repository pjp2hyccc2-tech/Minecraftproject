// =====================================================================================
//  HuntCraft Shaders - shadow map projection & soft PCF sampling
// =====================================================================================

// distortion factor for a shadow-clip-space xy (more resolution near the player)
float shadowDistortFactor(vec2 clipXY) {
    return mix(1.0, length(clipXY), SHADOW_DISTORT);
}

// shadow clip space -> distorted shadow clip space (shadow.vsh and sampling use the same mapping)
vec3 distortShadow(vec3 clipPos) {
    float f = shadowDistortFactor(clipPos.xy);
    clipPos.xy /= f;
    clipPos.z *= 0.2;
    return clipPos;
}

// player-relative world position -> shadow map uv + depth (0..1), returns distortion factor in w
vec4 toShadowSpace(vec3 playerPos) {
    vec4 sp = shadowProjection * (shadowModelView * vec4(playerPos, 1.0));
    float f = shadowDistortFactor(sp.xy);
    sp.xyz = distortShadow(sp.xyz);
    sp.xyz = sp.xyz * 0.5 + 0.5;
    return vec4(sp.xyz, f);
}

#if defined SHADOWS && !defined NO_SUN
float sampleShadowDepthTex(sampler2D tex, vec2 uv, float z) {
    return step(z, texture(tex, uv).r);
}

// soft shadow: returns rgb visibility (coloured by stained glass / water), 1 = lit
//  playerPos : position relative to the camera (world orientation)
//  normal    : world space normal (for normal-offset bias)
//  NdotL     : clamped cosine term
vec3 sampleShadow(vec3 playerPos, vec3 normal, float NdotL, float dither) {
    float dist = length(playerPos);
    float fade = smoothstep(shadowDistance * 0.80, shadowDistance * 0.98, dist);
    if (fade >= 1.0) return vec3(1.0);

    // normal offset bias grows with distance (texel size grows because of the distortion)
    float slope = sqrt(1.0 - NdotL * NdotL) / max(NdotL, 0.05);
    float offsetAmt = (0.035 + 0.0010 * dist) * (1.0 + min(slope, 4.0) * 0.5) * SHADOW_BIAS_MUL;
    vec3 biased = playerPos + normal * offsetAmt;

    vec4 sp = toShadowSpace(biased);
    if (sp.x < 0.0 || sp.x > 1.0 || sp.y < 0.0 || sp.y > 1.0) return vec3(1.0);

    float distortF = sp.w;
    float texel = 1.0 / float(shadowMapResolution);
    float radius = texel * SHADOW_SOFTNESS * 1.6 / max(distortF, 0.2) * (1.0 + dist * 0.004);
    float depthBias = 0.00012 * (1.0 + slope * 0.5) / max(distortF, 0.15);
    float z = sp.z - depthBias;

    // 8-tap rotated poisson disk
    float angle = dither * TAU;
    mat2 rot = rot2(angle);
    vec2 taps[8] = vec2[8](
        vec2(-0.7071, 0.7071), vec2(-0.0000, -0.8750), vec2(0.5303, 0.5303), vec2(-0.6250, -0.0000),
        vec2(0.3536, -0.3536), vec2(-0.0000, 0.3750), vec2(-0.1768, -0.1768), vec2(0.1250, 0.0000));

    float lit1 = 0.0;   // opaque only
    float lit0 = 0.0;   // everything (incl. translucents)
    vec3 tint = vec3(0.0);
    for (int i = 0; i < 8; i++) {
        vec2 o = rot * taps[i] * radius;
        vec2 uv = sp.xy + o;
        float s1 = sampleShadowDepthTex(shadowtex1, uv, z);
        float s0 = sampleShadowDepthTex(shadowtex0, uv, z);
        lit1 += s1;
        lit0 += s0;
#ifdef COLORED_SHADOWS
        vec4 col = texture(shadowcolor0, uv);
        tint += mix(vec3(1.0), col.rgb, col.a * 0.9) * (s1 - s0) + s0;
#else
        tint += vec3(s1);
#endif
    }
    lit1 *= 0.125;
    tint *= 0.125;
    vec3 shadow = min(tint, vec3(lit1));
    return mix(shadow, vec3(1.0), fade);
}

// cheap single-tap visibility for ray marching (volumetric light)
float shadowVisibility(vec3 playerPos) {
    vec4 sp = toShadowSpace(playerPos);
    if (sp.x < 0.0 || sp.x > 1.0 || sp.y < 0.0 || sp.y > 1.0) return 1.0;
    return step(sp.z - 0.0004 / max(sp.w, 0.2), texture(shadowtex1, sp.xy).r);
}
#else
vec3 sampleShadow(vec3 playerPos, vec3 normal, float NdotL, float dither) { return vec3(1.0); }
float shadowVisibility(vec3 playerPos) { return 1.0; }
#endif
