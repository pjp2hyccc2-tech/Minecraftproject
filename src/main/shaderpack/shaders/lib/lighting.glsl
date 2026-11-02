// =====================================================================================
//  SounanShaders - surface lighting
//  Forward (every lit gbuffers program): block light (flames, sodium lamps), the cold moon
//  (shadow mapped through the canopy), sky ambient.
//  The hand lights (flashlight, phone, flare ...) are added once per pixel by the deferred
//  pass for opaque surfaces; translucents and particles call handLightsForward() instead.
// =====================================================================================

// 0 = indoors / underground / tunnel, 1 = under the open sky (smoothed sky light at the eye)
float eyeSkyFactor() {
#ifdef WORLD_MOUNTAIN
    // under the dense cedar canopy the sky light at the eye drops to ~6-10 (leaves dim it by
    // one per block): that is still outdoors. Only the tunnel and closed huts go to 0.
    return smoothstep(0.04, 0.40, float(eyeBrightnessSmooth.y) / 240.0);
#else
    return smoothstep(0.10, 0.70, float(eyeBrightnessSmooth.y) / 240.0);
#endif
}

// organic flame flicker, ~1.0 +- 0.12. Each region of the world gets its own phase so
// fires in different places do not breathe in unison.
float flameFlicker(vec3 worldPos) {
    float t = frameTimeCounter;
    float ph = vnoise2(worldPos.xz * 0.21 + vec2(worldPos.y * 0.13, 0.0)) * 37.0;
    float n = vnoise1(t * 3.1 + ph) * 0.50
            + vnoise1(t * 7.7 + ph * 1.7) * 0.32
            + vnoise1(t * 16.9 + ph * 2.9) * 0.18;
    float gutter = smoothstep(0.78, 1.0, vnoise1(t * 0.8 + ph * 0.41));
    float f = (n - 0.5) * 0.26 - gutter * 0.10;
#ifdef FLASH_REDUCTION
    f *= 0.5;
#endif
    return 1.0 + f;
}

// light level 0..1 -> brightness: a hot core on a long soft tail (vanilla light drops one
// level per block, so 15 * (1 - l) is roughly the distance to the flame)
float flameFalloff(float l) {
    const float k = 6.6;
    float core = max(exp(-k * (1.0 - l)) - exp(-k), 0.0) / (1.0 - exp(-k));
    float tail = l * sqrt(l) * smoothstep(0.0, 0.36, l);
    return 0.42 * core + 0.58 * tail;
}

vec3 flameColor(float level, float flick) {
    return mix(FLAME_DEEP, FLAME_HOT, saturate(level * 0.85 + (flick - 1.0) * 2.5));
}

// extinction of the fog the hand light crosses on its way out (per block)
float handLightFogSigma() {
#if defined WORLD_MOUNTAIN
    return 0.030 * FOG_DENSITY * mix(0.35, 1.0, eyeSkyFactor());
#elif defined WORLD_NETHER
    return 0.020 * FOG_DENSITY;
#else
    return 0.008 * FOG_DENSITY;
#endif
}

// ---- forward lighting ------------------------------------------------------------------
//  albedo   : linear colour            N     : world normal (normalised)
//  playerPos: camera-relative position lm    : normalised lightmap (x block, y sky)
//  ao       : vertex ambient occlusion emission: linear HDR colour emitted by the surface
//  mat      : block id (BLK_*), 0, or -1 for the hand
//  glow     : out, flame light reaching the surface (fog / bloom)
vec3 shadeSurface(vec3 albedo, vec3 N, vec3 playerPos, vec2 lm, float ao, vec3 emission,
                  int mat, float dither, bool receiveShadow, out float glow) {
    vec3 worldPos = playerPos + cameraPosition;
    vec3 V = -normalize(playerPos + vec3(0.0, 1e-4, 0.0));
    float flick = flameFlicker(worldPos);

    // ---- block light: flames, lanterns, sodium lamps ---------------------------------
    float level = saturate(lm.x * (0.95 + 0.05 * flick));
    float flameF = flameFalloff(level) * flick;
    glow = flameF;
    vec3 flame = flameColor(level, flick) * flameF * FLAME_GAIN;

    // ---- moon / sun --------------------------------------------------------------------
    vec3 direct = vec3(0.0);
    bool foliage = mat == BLK_PLANT || mat == BLK_LEAVES || mat == BLK_HANGING;
#ifdef HAS_CELESTIAL_LIGHT
    vec3 L = lightDirW();
    float NdotL = dot(N, L);
    float diff = foliage ? 0.30 + 0.70 * abs(NdotL) : max(NdotL, 0.0);
  #ifdef SHADOWS_ON
    float skyGate = smoothstep(0.04, 0.30, lm.y);
    float shadow = 1.0;
    if (diff > 0.0 && skyGate > 0.0) {
        float cover = shadowCoverage(length(playerPos));
        vec3 sN = foliage && NdotL < 0.0 ? -N : N;
        float s = receiveShadow ? shadowLookup(playerPos, sN, abs(NdotL), dither) : 1.0;
        // outside the shadow map only fully sky-lit surfaces get the light
        shadow = mix(smoothstep(0.80, 0.97, lm.y), s, cover);
    }
  #else
    float skyGate = smoothstep(0.55, 0.95, lm.y);
    float shadow = 1.0;
  #endif
    direct = celestialLight() * diff * shadow * skyGate;
#endif

    // ---- ambient -------------------------------------------------------------------------
    vec3 amb = MIN_LIGHT + worldAmbient();
#ifdef HAS_CELESTIAL_LIGHT
    amb += skyAmbient() * (lm.y * lm.y) * (0.62 + 0.38 * N.y);
#endif
#ifndef WORLD_MOUNTAIN
    amb += vec3(0.10, 0.11, 0.13) * nightVision;      // a night-vision potion elsewhere
#endif
    float aoC = ao * ao * (3.0 - 2.0 * ao);

    vec3 color = albedo * (direct + (flame + amb) * aoC);

    // ---- gloss: wet surfaces catch the flames and the moon -----------------------------
    vec2 gl = surfaceGloss(mat, toSrgb(albedo));
    if (gl.x > 0.0) {
        float spec = pow(saturate(dot(N, V)), 24.0) * flameF * 0.35;
        vec3 sp = FLAME_HOT * spec * FLAME_GAIN;
#ifdef HAS_CELESTIAL_LIGHT
        vec3 Hm = normalize(L + V);
        sp += direct * pow(saturate(dot(N, Hm)), exp2(4.0 + gl.y * 6.0)) * 6.0;
#endif
        color += sp * gl.x * 0.35;
    }
    return color + emission;
}

// ---- hand lights, forward (translucents, particles, the hand itself) --------------------
vec3 handLightsForward(vec3 albedo, vec3 P, vec3 N, float foliage, vec2 gloss) {
    vec3 sum = vec3(0.0);
    if (!anyHandLight()) return sum;
    float camMode = cameraMode();
    float sigma = handLightFogSigma();
    for (int i = 0; i < 2; i++) {
        HandLight L = handLight(i);
        if (L.type == LIGHT_NONE) continue;
        vec3 spec;
        vec3 diff = handLightSurface(L, P, N, foliage, gloss, spec);
        float T = exp(-sigma * length(P - L.pos));
        sum += (albedo * diff + spec) * T;
    }
    return sum;
}
