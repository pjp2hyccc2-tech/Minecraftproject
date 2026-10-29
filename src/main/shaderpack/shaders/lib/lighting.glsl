// =====================================================================================
//  HuntCraft Shaders - surface lighting (used by deferred and by forward programs)
// =====================================================================================

// block light (torches, lava, paper lanterns...) for a normalised lightmap x
vec3 blockLightColor(float lmx, vec3 playerPos) {
    vec3 bl = BLOCKLIGHT_COLOR * (pow(lmx, 2.4) * 1.9 + lmx * 0.12);
#ifdef DYNAMIC_HAND_LIGHT
    float hl = float(max(heldBlockLightValue, heldBlockLightValue2)) / 15.0;
    if (hl > 0.0) {
        float d = length(playerPos);
        float range = hl * 11.0;
        float att = pow2(saturate(1.0 - d / range));
        bl += BLOCKLIGHT_COLOR * hl * att * 1.4;
    }
#endif
    return bl;
}

// main lighting
//  albedo    : linear colour
//  N         : world space normal
//  playerPos : position relative to the camera (world orientation)
//  lm        : normalised lightmap (x = block, y = sky)
//  emissive  : 0..1   sss : 0..1 (foliage)   ao : vertex AO   ssao : screen space AO
//  receiveShadow : sample the shadow map
vec3 shadeSurfaceEx(vec3 albedo, vec3 N, vec3 playerPos, vec2 lm, int mat, float emissive, float sss,
                    float ao, float ssao, float dither, bool receiveShadow, out vec3 shadowOut, out vec3 directLightOut) {
    vec3 L = lightDirWorld();
    vec3 lightCol = shadowLightColor();
    float dist = length(playerPos);
    float NdotL = dot(N, L);
    float diffuse = saturate(NdotL);
    if (sss > 0.0) diffuse = mix(diffuse, saturate(abs(NdotL)) * 0.55 + 0.40, sss);
    if (mat == MAT_HAND) diffuse = saturate(NdotL) * 0.6 + 0.4;

    vec3 shadow = vec3(1.0);
#ifndef NO_SUN
    if (receiveShadow && diffuse > 0.0) {
        shadow = sampleShadow(playerPos, N, saturate(abs(NdotL)), dither);
    }
    // outside the shadow map / underground: fall back to the sky lightmap
    float fade = smoothstep(shadowDistance * 0.80, shadowDistance * 0.98, dist);
    float skyVis = mix(smoothstep(0.0, 0.30, lm.y), smoothstep(0.25, 0.90, lm.y), fade);
    if (mat == MAT_HAND) skyVis = smoothstep(0.0, 0.6, lm.y);
    vec3 direct = lightCol * diffuse * shadow * skyVis;
    shadowOut = shadow * skyVis;
    directLightOut = lightCol * shadow * skyVis;
#else
    vec3 direct = vec3(0.0);
    shadowOut = vec3(0.0);
    directLightOut = vec3(0.0);
#endif

    // wet surfaces catch a sun glint
#ifndef NO_SUN
    if (wetness > 0.01 && lm.y > 0.8 && (mat == MAT_TERRAIN || mat == MAT_SNOW)) {
        vec3 V = -normalize(playerPos);
        vec3 H = normalize(L + V);
        float spec = pow(saturate(dot(N, H)), 48.0) * 0.45 * wetness;
        direct += lightCol * spec * shadow * skyVis;
    }
#endif

    // ambient: sky dome + block light + minimum
    float skyLm = lm.y * lm.y * 0.85 + lm.y * 0.15;
    vec3 skyAmb = skyAmbientColor() * skyLm * (0.72 + 0.28 * N.y);
    vec3 blockLight = blockLightColor(lm.x, playerPos);
    vec3 minLight = vec3(0.0045, 0.0055, 0.0085) * NIGHT_BRIGHTNESS + vec3(0.25) * nightVision;
    float aoTotal = ao * ssao;
    vec3 ambient = (skyAmb + blockLight * (0.65 + 0.35 * ao)) * aoTotal + minLight;

    vec3 color = albedo * (direct + ambient);
    color += albedo * emissive * (2.6 * EMISSIVE_BOOST);
    color *= 1.0 - darknessFactor * 0.8;
    return color;
}

vec3 shadeSurface(vec3 albedo, vec3 N, vec3 playerPos, vec2 lm, int mat, float emissive, float sss,
                  float ao, float ssao, float dither, bool receiveShadow) {
    vec3 s;
    vec3 d;
    return shadeSurfaceEx(albedo, N, playerPos, lm, mat, emissive, sss, ao, ssao, dither, receiveShadow, s, d);
}
