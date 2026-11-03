// =====================================================================================
//  ReisatsuShaders - surface lighting
//  Forward (every lit gbuffers program): the restaurant's lamps (lib/emitters.glsl) or, in
//  other dimensions, warm block light and the sun / moon; sky ambient; wet surfaces.
//  The hand lights (flashlight) and the camera flash are added once per pixel by the
//  deferred pass for opaque surfaces; translucents and particles call handLightsForward().
// =====================================================================================

// 0 = indoors / underground, 1 = under the open sky (smoothed sky light at the eye)
float eyeSkyFactor() {
    return smoothstep(0.10, 0.70, float(eyeBrightnessSmooth.y) / 240.0);
}

// organic flame flicker, ~1.0 +- 0.12 (vanilla light sources in other dimensions)
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

// light level 0..1 -> brightness: a hot core on a long soft tail
float flameFalloff(float l) {
    const float k = 6.6;
    float core = max(exp(-k * (1.0 - l)) - exp(-k), 0.0) / (1.0 - exp(-k));
    float tail = l * sqrt(l) * smoothstep(0.0, 0.36, l);
    return 0.42 * core + 0.58 * tail;
}

vec3 flameColor(float level, float flick) {
    return mix(FLAME_DEEP, FLAME_HOT, saturate(level * 0.85 + (flick - 1.0) * 2.5));
}

// extinction of the air the hand light crosses on its way out (per block)
float handLightFogSigma() {
#if defined WORLD_HIDAMARI
    return 0.010 * FOG_DENSITY;
#elif defined WORLD_NETHER
    return 0.020 * FOG_DENSITY;
#else
    return 0.008 * FOG_DENSITY;
#endif
}

// ---- wet ground after the rain ---------------------------------------------------------------
// how wet a surface is: out under the open sky (sky light, and nothing above it in the emitter
// map's cover tile - the canopy and the eaves keep a dry band). wp: world position, N: normal
float surfaceWetness(vec3 wp, vec3 N, float skyLight, int mat) {
#if defined WORLD_HIDAMARI && defined WET_SURFACES
    if (mat == MAT_EMITTER || mat == MAT_NONE || mat == MAT_SPIRIT) return 0.0;
    float w = smoothstep(0.80, 0.95, skyLight);
    if (w <= 0.0) return 0.0;
    return w * openSky(wp + N * 0.3 + vec3(0.0, 0.05, 0.0));
#else
    return 0.0;
#endif
}

// 0..1: dry .. standing water, for a surface under the open sky. Puddles collect in shallow
// dips (slow noise); the rest is damp. Same value in gbuffers and composite.
float puddleAmount(vec3 wp, int mat, vec3 N) {
#ifdef WET_SURFACES
    if (N.y < 0.7) return 0.0;
    float base = 0.0;
    if (mat == MAT_ASPHALT) base = 0.62;
    else if (mat == MAT_PAVER) base = 0.50;
    else if (mat == MAT_MUD) base = 0.70;
    else if (mat == MAT_WET || mat == MAT_ROOF) base = 0.38;
    else return 0.0;
    vec2 p = wp.xz;
    float n = vnoise2(p * 0.115 + 3.1) * 0.62 + vnoise2(p * 0.37 + 11.7) * 0.28 + vnoise2(p * 1.3) * 0.10;
    return smoothstep(base, base + 0.10, n);
#else
    return 0.0;
#endif
}

// fine bumps of wet asphalt / pavers (world normal perturbation), flat where water stands
vec3 wetMicroNormal(vec3 N, vec3 wp, int mat, float puddle) {
    if (N.y < 0.7) return N;
    float s = mat == MAT_ASPHALT ? 0.10 : (mat == MAT_PAVER ? 0.06 : (mat == MAT_MUD ? 0.08 : 0.0));
    if (s <= 0.0) return N;
    // fine aggregate: high frequency, small slopes (breaks a reflection into streaks)
    vec2 p = wp.xz * 21.0;
    float h0 = vnoise2(p);
    float hx = vnoise2(p + vec2(0.35, 0.0));
    float hz = vnoise2(p + vec2(0.0, 0.35));
    vec3 n = normalize(N + vec3(h0 - hx, 0.0, h0 - hz) * s * (1.0 - puddle));
    return n;
}

// ripples of drips falling into standing water: rings expanding from random points
vec3 dripRipples(vec3 N, vec3 wp, float amount) {
    if (amount <= 0.0) return N;
    vec2 g = vec2(0.0);
    float t = frameTimeCounter;
    for (int i = 0; i < 2; i++) {
        vec2 cell = floor(wp.xz * 0.9 + float(i) * 0.5);
        vec2 h = hash22(cell + float(i) * 7.1);
        float period = 2.5 + 3.5 * h.y;
        float ph = fract(t / period + h.x);
        vec2 c = (cell + 0.2 + 0.6 * hash22(cell * 1.7 + floor(t / period + h.x) * 3.3)) / 0.9 - float(i) * 0.5 / 0.9;
        vec2 d = wp.xz - c;
        float r = length(d);
        float front = ph * 0.9;
        float ring = sin((r - front) * 38.0) * exp(-sq((r - front) * 9.0)) * (1.0 - ph) * (1.0 - ph);
        g += d / max(r, 1e-3) * ring;
    }
    return normalize(N + vec3(g.x, 0.0, g.y) * 0.10 * amount);
}

// ---- forward lighting ------------------------------------------------------------------
//  albedo   : linear colour            N     : world normal (normalised)
//  playerPos: camera-relative position lm    : normalised lightmap (x block, y sky)
//  ao       : vertex ambient occlusion emission: linear HDR colour emitted by the surface
//  mat      : material code (MAT_*)    wet   : 0..1 wetness under the open sky
//  twoSided : 1 = thin leaves / particles   fillK : the fill for unexplained light (lampsAt)
//  glow     : out, share of the colour that comes from lamps (screen-space lamp shadows)
vec3 shadeSurface(vec3 albedo, vec3 N, vec3 playerPos, vec2 lm, float ao, vec3 emission,
                  int mat, float wet, float dither, bool receiveShadow, float twoSided, float fillK, out float glow) {
    vec3 worldPos = playerPos + cameraPosition;
    vec3 V = -normalize(playerPos + vec3(0.0, 1e-4, 0.0));
    float aoC = ao * ao * (3.0 - 2.0 * ao);
    vec4 surf = materialSurface(mat, wet);
    float f0 = surf.y;
    vec3 specTint = mix(vec3(1.0), albedo * 1.6, surf.z);
    glow = 0.0;

#ifdef EMITTERS_ON
    // ---- the restaurant: every lamp in its own colour ----------------------------------
    vec3 diffuse, spec, domPos;
    float domW;
    lampsAt(worldPos, N, V, lm.x * 15.0, surf.x, surf.z > 0.5 || surf.w > 0.0 ? f0 : -1.0, twoSided, fillK,
            diffuse, spec, domPos, domW);
    vec3 sky = skyAmbientN(N) * (lm.y * lm.y);
    vec3 lampPart = albedo * diffuse * aoC * (1.0 - surf.z * 0.85);
    vec3 color = lampPart + albedo * (sky + MIN_LIGHT) * aoC;
    color += spec * specTint * (0.4 + 0.6 * aoC);
    glow = saturate(luma(lampPart) / max(luma(color), 1e-5)) * saturate(domW);
    return color + emission;
#else
    // ---- warm vanilla block light ---------------------------------------------------------
    float flick = flameFlicker(worldPos);
    float level = saturate(lm.x * (0.95 + 0.05 * flick));
    float flameF = flameFalloff(level) * flick;
    vec3 flame = flameColor(level, flick) * flameF * FLAME_GAIN;
  #ifdef WORLD_HIDAMARI
    flame *= vec3(0.86, 0.92, 1.0);         // the restaurant without coloured light: colder
  #endif

    // ---- moon / sun --------------------------------------------------------------------
    vec3 direct = vec3(0.0);
    bool foliage = twoSided > 0.5;
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
        shadow = mix(smoothstep(0.80, 0.97, lm.y), s, cover);
    }
    #else
    float skyGate = smoothstep(0.55, 0.95, lm.y);
    float shadow = 1.0;
    #endif
    direct = celestialLight() * diff * shadow * skyGate;
  #endif

    vec3 amb = MIN_LIGHT + worldAmbient() + skyAmbientN(N) * (lm.y * lm.y);
  #ifndef WORLD_HIDAMARI
    amb += vec3(0.10, 0.11, 0.13) * nightVision;      // a night-vision potion elsewhere
  #endif
    vec3 color = albedo * (direct + (flame + amb) * aoC);
    glow = saturate(luma(albedo * flame) / max(luma(color), 1e-5));

    // ---- gloss: wet surfaces catch the flames and the sun --------------------------------
    if (surf.w > 0.0 || surf.z > 0.5) {
        float spec = pow(saturate(dot(N, V)), 24.0) * flameF * 0.35;
        vec3 sp = FLAME_HOT * spec * FLAME_GAIN;
  #ifdef HAS_CELESTIAL_LIGHT
        sp += direct * ggxSpec(N, V, L, surf.x, 0.01) * fresnelSchlick(f0, dot(N, V));
  #endif
        color += sp * specTint * 0.5;
    }
    return color + emission;
#endif
}

// ---- hand lights + flash, forward (translucents, particles, the hand itself) ---------------
vec3 handLightsForward(vec3 albedo, vec3 P, vec3 N, float foliage, vec2 gloss) {
    vec3 sum = vec3(0.0);
    if (!anyHandLight()) return sum;
    float sigma = handLightFogSigma();
    for (int i = 0; i < HAND_LIGHTS; i++) {
        HandLight L = handLight(i);
        if (L.type == LIGHT_NONE) continue;
  #ifdef PROG_HAND
        // the held items: never lit by the flashlight's own beam (its body sits right at the
        // beam's origin), only by the flash and a held flame
        if (L.type == LIGHT_SPOT) continue;
  #endif
        vec3 spec;
        vec3 diff = handLightSurface(L, P, N, foliage, gloss, spec);
        float T = exp(-sigma * length(P - L.pos));
        sum += (albedo * diff + spec) * T;
    }
    return sum;
}
