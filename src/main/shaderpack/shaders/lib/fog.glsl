// =====================================================================================
//  MeikonShaders - distance fog, lantern glow in the fog, volumetric moonlight
// =====================================================================================

// scale of the exp^2 fog in Kakuriyo (blocks); smaller = thicker
float kakuriyoFogRange() {
#ifdef WORLD_KAKURIYO
    float r = mix(FOG_RANGE_INDOOR, FOG_RANGE_OUTDOOR, eyeSkyFactor()) / FOG_DENSITY;
    r *= 1.0 - 0.22 * fear();             // the dark closes in
    r *= 1.0 - 0.18 * danger();
    return r;
#else
    return 64.0;
#endif
}

// fraction of the surface colour that survives the fog over dist blocks (1 = clear)
float fogTransmittance(float dist, vec3 dirW) {
    float T;
#if defined WORLD_KAKURIYO
    float x = dist / kakuriyoFogRange();
    T = exp(-x * x);
#elif defined WORLD_OVERWORLD
    float day = dayFactor();
    float k = mix(0.0150, 0.0068, day) * FOG_DENSITY;
    k *= 1.0 + rainAmount() * 2.4;
    k *= mix(0.55, 1.0, eyeSkyFactor());
    // thicker near the ground, thinner high up
    float camY = cameraPosition.y;
    float height = exp(-max(camY + dirW.y * min(dist, 64.0) * 0.5 - 63.0, 0.0) * 0.018);
    k *= 0.55 + 0.45 * height;
    T = exp(-dist * k);
    // the edge of the loaded world always fades out
    T *= 1.0 - smoothstep(far * 0.72, far * 0.98, dist);
#elif defined WORLD_NETHER
    T = exp(-dist * 0.026 * FOG_DENSITY);
    T *= 1.0 - smoothstep(far * 0.70, far * 0.98, dist);
#else
    T = exp(-dist * 0.012 * FOG_DENSITY);
    T *= 1.0 - smoothstep(far * 0.75, far * 0.98, dist);
#endif
    // vanilla Darkness / Blindness: the world shrinks to a few blocks
    float closeIn = max(darknessFactor * 0.85, blindness);
    if (closeIn > 0.0) {
        float xr = dist / mix(64.0, 3.5, closeIn);
        T = min(T, mix(1.0, exp(-xr * xr), closeIn));
    }
    return T;
}

// warm light of the player's own lantern / torch scattered by the fog around them: a faint
// warm veil (closed-form integral of an exponential falloff along the view ray).
vec3 lanternFogGlow(float dist) {
    float eyeBlock = float(eyeBrightnessSmooth.x) / 240.0;
    float lvl = max(eyeBlock, heldLightLevel() * 0.95);
    float I = flameFalloff(lvl) * flameFlicker(cameraPosition);
    const float R = 2.5;
    float along = 1.0 - exp(-dist / R);
#ifdef WORLD_KAKURIYO
    return FLAME_DEEP * I * along * 0.012;
#else
    return FLAME_DEEP * I * along * 0.008;
#endif
}

#ifdef VOLUMETRICS_ON
// moonlight / sunlight scattered in the fog, occluded by the shadow map ("light shafts").
// Average lit fraction along the ray (weighted by how much light makes it back through the
// fog) times how much fog the ray crosses: bounded, and independent of the sample count.
vec3 volumetricLight(vec3 dirW, float dist, float dither) {
#ifdef WORLD_KAKURIYO
    float maxD = min(dist, 32.0);
#else
    float maxD = min(dist, shadowDistance * 0.85);
#endif
    float stepL = maxD / float(VOLUMETRIC_SAMPLES);
    float t0 = frameTimeCounter;
    float lit = 0.0;
    float wsum = 1e-4;
    for (int i = 0; i < VOLUMETRIC_SAMPLES; i++) {
        float t = (float(i) + dither) * stepL;
        vec3 p = dirW * t;
        float vis = shadowTap(p);
        vec3 wp = p + cameraPosition;
        // slow drifting wisps of mist
        float wisp = vnoise2(wp.xz * 0.075 + vec2(t0 * 0.035, t0 * 0.013) + vec2(wp.y * 0.05));
        float w = fogTransmittance(t, dirW);
        lit += vis * (0.45 + 1.1 * wisp) * w;
        wsum += w;
    }
    float amount = 1.0 - fogTransmittance(maxD, dirW);
    vec3 L = lightDirW();
    float vdl = dot(dirW, L);
    float phase = mix(1.0, phaseHG(vdl, 0.35) * 4.0 * PI, 0.5);
    float eyeSky = eyeSkyFactor();
#ifdef WORLD_KAKURIYO
    float strength = 0.06;          // MOON_LIGHT is bright enough to light the ground: keep the mist thin
    // cold silver shafts, but the mist glows red toward the blood moon
    vec3 tint = mix(vec3(1.0), vec3(1.9, 0.30, 0.26), pow(saturate(vdl), 6.0) * 0.65);
#else
    float strength = mix(0.25, 0.08, dayFactor()) * (1.0 + rainAmount());
    vec3 tint = vec3(1.0);
#endif
    return celestialLight() * tint * phase * (lit / wsum) * amount * strength * mix(0.25, 1.0, eyeSky) * FOG_DENSITY;
}
#endif
