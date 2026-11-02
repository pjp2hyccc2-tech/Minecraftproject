// =====================================================================================
//  SounanShaders - shadow map distortion + soft hardware-PCF lookups (the moon)
//  shadow.vsh and every lookup use the same distortion (more texels near the player).
// =====================================================================================

const float SHADOW_DISTORT = 0.85;

float shadowDistortFactor(vec2 clipXY) {
    return length(clipXY) * SHADOW_DISTORT + (1.0 - SHADOW_DISTORT);
}
vec3 distortShadowClip(vec3 clipPos) {
    return vec3(clipPos.xy / shadowDistortFactor(clipPos.xy), clipPos.z * 0.25);
}

#if defined SHADOWS_ON && !defined PROGRAM_SHADOW
uniform sampler2DShadow shadowtex0;

// Vogel disks (r = sqrt((i + 0.5) / n), golden-angle steps), rotated per pixel
#if SHADOW_FILTER == 1
    #define SHADOW_TAPS 4
    const vec2 SHADOW_DISK[4] = vec2[4](
        vec2(0.3536, 0.0000), vec2(-0.4515, 0.4137), vec2(0.0691, -0.7875), vec2(0.5691, 0.7423));
#elif SHADOW_FILTER == 3
    #define SHADOW_TAPS 12
    const vec2 SHADOW_DISK[12] = vec2[12](
        vec2(0.2041, 0.0000), vec2(-0.2607, 0.2388), vec2(0.0399, -0.4547), vec2(0.3286, 0.4286),
        vec2(-0.6030, -0.1067), vec2(0.5712, -0.3634), vec2(-0.1910, 0.7108), vec2(-0.3644, -0.7016),
        vec2(0.7906, 0.2887), vec2(-0.8224, 0.3395), vec2(0.3964, -0.8472), vec2(0.2930, 0.9341));
#else
    #define SHADOW_TAPS 8
    const vec2 SHADOW_DISK[8] = vec2[8](
        vec2(0.2500, 0.0000), vec2(-0.3193, 0.2925), vec2(0.0489, -0.5569), vec2(0.4024, 0.5249),
        vec2(-0.7385, -0.1306), vec2(0.6996, -0.4450), vec2(-0.2340, 0.8705), vec2(-0.4463, -0.8593));
#endif

// 1 inside the covered shadow area, fading to 0 at its edge
float shadowCoverage(float dist) {
    return 1.0 - smoothstep(shadowDistance * 0.78, shadowDistance * 0.94, dist);
}

// soft moon / sun shadow. playerPos: camera-relative world position, N: world normal,
// NdotL: |cosine| to the light, dither: 0..1 per pixel. Returns 1 = lit.
float shadowLookup(vec3 playerPos, vec3 N, float NdotL, float dither) {
    float dist = length(playerPos);
    // normal-offset bias, grows with distance (the distortion makes texels bigger far away)
    float slope = sqrt(saturate(1.0 - NdotL * NdotL)) / max(NdotL, 0.08);
    vec3 biased = playerPos + N * (0.03 + dist * 0.0016) * (1.0 + min(slope, 3.0) * 0.35);

    vec4 sp = shadowProjection * (shadowModelView * vec4(biased, 1.0));
    float f = shadowDistortFactor(sp.xy);
    vec3 s = distortShadowClip(sp.xyz) * 0.5 + 0.5;
    if (s.x <= 0.002 || s.x >= 0.998 || s.y <= 0.002 || s.y >= 0.998 || s.z >= 1.0) return 1.0;

    // penumbra in world units -> shadow uv (uv = world / (2 * shadowDistance * f))
    float softWorld = 0.06 + dist * 0.0040;
    float texel = 1.0 / float(shadowMapResolution);
    float radius = max(softWorld / (2.0 * shadowDistance * f), texel * 0.75);
    float z = s.z - 0.00006;

    float a = dither * TAU;
    mat2 rot = mat2(cos(a), sin(a), -sin(a), cos(a)) * radius;
    float acc = 0.0;
    for (int i = 0; i < SHADOW_TAPS; i++) {
        acc += textureLod(shadowtex0, vec3(s.xy + rot * SHADOW_DISK[i], z), 0.0);
    }
    return acc / float(SHADOW_TAPS);
}

// single tap (ray marching). 0 outside the shadow area so far interiors never light up.
float shadowTap(vec3 playerPos) {
    vec4 sp = shadowProjection * (shadowModelView * vec4(playerPos, 1.0));
    vec3 s = distortShadowClip(sp.xyz) * 0.5 + 0.5;
    if (s.x <= 0.0 || s.x >= 1.0 || s.y <= 0.0 || s.y >= 1.0) return 0.0;
    if (s.z >= 1.0) return 1.0;
    return textureLod(shadowtex0, vec3(s.xy, s.z - 0.0002), 0.0);
}
#elif defined SHADOWS_ON
// the shadow pass never samples the map: stubs so the shared lighting code compiles
float shadowCoverage(float dist) { return 0.0; }
float shadowLookup(vec3 playerPos, vec3 N, float NdotL, float dither) { return 1.0; }
float shadowTap(vec3 playerPos) { return 1.0; }
#endif
