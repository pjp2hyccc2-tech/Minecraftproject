// =====================================================================================
//  MeikonShaders - forward surface lighting (every lit gbuffers program uses this)
//  Block light = flames (lantern, andon, candles): warm, flickering, soft tight falloff.
//  Celestial light = the cold moon (or the pale sun in the overworld), shadow mapped.
// =====================================================================================

// organic flame flicker, ~1.0 +- 0.12. Each region of the world gets its own phase so
// lamps in different rooms do not breathe in unison.
float flameFlicker(vec3 worldPos) {
#ifdef FLAME_FLICKER
    float t = frameTimeCounter;
    float ph = vnoise2(worldPos.xz * 0.21 + vec2(worldPos.y * 0.13, 0.0)) * 37.0;
    float n = vnoise1(t * 3.1 + ph) * 0.50
            + vnoise1(t * 7.7 + ph * 1.7) * 0.32
            + vnoise1(t * 16.9 + ph * 2.9) * 0.18;
    // now and then the flame gutters for a moment
    float gutter = smoothstep(0.78, 1.0, vnoise1(t * 0.8 + ph * 0.41));
    float f = (n - 0.5) * 0.26 - gutter * 0.10;
  #ifdef FLASH_REDUCTION
    f *= 0.5;
  #endif
    return 1.0 + f;
#else
    return 1.0;
#endif
}

// light level 0..1 -> brightness. Vanilla light drops one level per block, so 15*(1-l) is
// roughly the distance to the flame. A hot core (exponential in that distance) sits on a
// long soft tail, so a room lit by the lantern stays readable 6-9 blocks out - the far wall
// dim but there - while true darkness (levels 0-2) stays black.
//   level: 15    12    10    8     7     6     5     4     3     2
//   value: 1.0   .53   .36   .25   .20   .16   .12   .07   .03   .009
float flameFalloff(float l) {
    const float k = 6.6;            // 15 / 2.27 blocks
    float core = max(exp(-k * (1.0 - l)) - exp(-k), 0.0) / (1.0 - exp(-k));
    float tail = l * sqrt(l) * smoothstep(0.0, 0.36, l);
    return 0.42 * core + 0.58 * tail;
}

// warm flame colour: deeper orange where the light is weak or the flame dips
vec3 flameColor(float level, float flick) {
    return mix(FLAME_DEEP, FLAME_HOT, saturate(level * 0.85 + (flick - 1.0) * 2.5));
}

// ---- hand-held light ---------------------------------------------------------------
// In meikon:kakuriyo the lit lantern is also a real block light (lightmap); this adds a
// smooth, swaying light around the hand so the pool does not jump block by block.
// Elsewhere it is a simple dynamic light for held torches / lanterns.
float heldLightLevel() {
#ifdef HAND_LANTERN_LIGHT
    float lantern = (heldItemId == ITEM_CHOCHIN_LIT || heldItemId2 == ITEM_CHOCHIN_LIT) ? 0.93 : 0.0;
    float vanilla = float(max(heldBlockLightValue, heldBlockLightValue2)) / 15.0;
    return max(lantern, vanilla);
#else
    return 0.0;
#endif
}

// position of the held light relative to the eye (player space): low, in front, swaying
vec3 heldLightPos() {
    float t = frameTimeCounter;
    vec3 sway = vec3(sin(t * 1.7) * 0.04, sin(t * 2.3 + 1.0) * 0.03, 0.0);
    return mat3(gbufferModelViewInverse) * (vec3(0.22, -0.38, -0.30) + sway);
}

// ---- main ------------------------------------------------------------------------------
//  albedo   : linear colour            N     : world normal (normalised)
//  playerPos: camera-relative position lm    : normalised lightmap (x block, y sky)
//  ao       : vertex ambient occlusion emission: linear HDR colour emitted by the surface
//  mat      : block id (BLK_*), 0, or -1 for the hand (not lit by its own lantern)
//  glow     : out, flame light reaching the surface (for the fog / bloom)
vec3 shadeSurface(vec3 albedo, vec3 N, vec3 playerPos, vec2 lm, float ao, vec3 emission,
                  int mat, float dither, bool receiveShadow, out float glow) {
    vec3 worldPos = playerPos + cameraPosition;
    vec3 V = -normalize(playerPos + vec3(0.0, 1e-4, 0.0));
    float flick = flameFlicker(worldPos);

    // ---- flames: lightmap block light and the held lantern -------------------------
    float level = saturate(lm.x * (0.95 + 0.05 * flick));     // the pool breathes
    float flameF = flameFalloff(level);
    float held = heldLightLevel();
    vec3 toHeld = vec3(0.0, 1.0, 0.0);
    float heldF = 0.0;
    if (held > 0.0 && mat >= 0) {
        vec3 d = heldLightPos() - playerPos;
        float dl = length(d);
        toHeld = d / max(dl, 1e-3);
        float hl = saturate(held - dl / 15.0);
        heldF = flameFalloff(hl) * (0.40 + 0.60 * saturate(dot(N, toHeld)));
        level = max(level, hl);
    }
    flameF = max(flameF, heldF) * flick;
    glow = flameF;
    vec3 flame = flameColor(level, flick) * flameF * FLAME_GAIN;

    // ---- moon / sun ------------------------------------------------------------------
    vec3 direct = vec3(0.0);
#ifdef HAS_CELESTIAL_LIGHT
    vec3 L = lightDirW();
    float NdotL = dot(N, L);
    float diff = max(NdotL, 0.0);
    vec3 shadowN = N;
    if (mat == BLK_PAPER && NdotL < 0.0) {
        diff = -NdotL * 0.50;           // moonlight shining through the paper
        shadowN = -N;                    // test the lit side of the panel
    }
    if (mat == BLK_PLANT || mat == BLK_LEAVES || mat == BLK_HANGING) diff = 0.35 + 0.65 * abs(NdotL);
  #ifdef SHADOWS_ON
    float skyGate = smoothstep(0.04, 0.30, lm.y);
    if (mat == BLK_PAPER) skyGate = max(skyGate, 0.65);
    float shadow = 1.0;
    if (diff > 0.0 && skyGate > 0.0) {
        float cover = shadowCoverage(length(playerPos));
        float s = receiveShadow ? shadowLookup(playerPos, shadowN, abs(NdotL), dither) : 1.0;
        // outside the shadow map only fully sky-lit surfaces get the light
        shadow = mix(smoothstep(0.80, 0.97, lm.y), s, cover);
    }
  #else
    float skyGate = smoothstep(0.55, 0.95, lm.y);
    float shadow = 1.0;
  #endif
    direct = celestialLight() * diff * shadow * skyGate;
#endif

    // ---- ambient ---------------------------------------------------------------------
    vec3 amb = MIN_LIGHT + worldAmbient();
#ifdef HAS_CELESTIAL_LIGHT
    amb += skyAmbient() * (lm.y * lm.y) * (0.62 + 0.38 * N.y);
#endif
    amb += vec3(0.10, 0.11, 0.13) * nightVision;
    float aoC = ao * ao * (3.0 - 2.0 * ao);      // deeper corners

    vec3 color = albedo * (direct + (flame + amb) * aoC);

    // ---- gloss: wet blood and lacquer catch the lantern and the moon ------------------
    float gloss = mat == BLK_WET ? 1.0 : (mat == BLK_LACQUER || mat == BLK_BELL ? 0.55 : 0.0);
    if (gloss > 0.0) {
        float spec = 0.0;
        if (heldF > 0.0) {
            vec3 H = normalize(toHeld + V);
            spec += pow(saturate(dot(N, H)), 20.0) * heldF * 2.5;
        }
        // static lamps: highlight toward the viewer, scaled by the light already there
        spec += pow(saturate(dot(N, V)), 24.0) * flameF * 0.35;
        vec3 sp = FLAME_HOT * spec * FLAME_GAIN;
#ifdef HAS_CELESTIAL_LIGHT
        vec3 Hm = normalize(L + V);
        sp += direct * pow(saturate(dot(N, Hm)), 64.0) * 6.0;
#endif
        color += sp * gloss * 0.35;
    }

    return color + emission;
}
