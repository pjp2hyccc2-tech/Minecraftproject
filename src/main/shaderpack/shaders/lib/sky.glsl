// =====================================================================================
//  MeikonShaders - sky gradient, analytic moon (reflections), fog colour
// =====================================================================================

// 0 = indoors / underground, 1 = under the open sky (smoothed sky light at the eye)
float eyeSkyFactor() {
    return smoothstep(0.10, 0.70, float(eyeBrightnessSmooth.y) / 240.0);
}

vec3 skyGradient(vec3 dir) {
    float up = dir.y;
    float h = saturate(up);
#if defined WORLD_KAKURIYO
    // Kakuriyo: dark crimson-black, a blood haze on the horizon, dark clouds drifting slowly
    vec3 c = mix(SKY_HORIZON, SKY_MID, smoothstep(0.0, 0.22, h));
    c = mix(c, SKY_ZENITH, smoothstep(0.16, 0.80, h));
    if (up > 0.0) {
        vec2 p = dir.xz / (up + 0.10) * 1.4 + vec2(frameTimeCounter * 0.0040, frameTimeCounter * 0.0016);
        float n = fbm2(p);
        float fade = smoothstep(0.0, 0.10, up) * (1.0 - 0.6 * smoothstep(0.40, 0.95, up));
        float cloud = smoothstep(0.46, 0.78, n) * fade;
        float rim = smoothstep(0.34, 0.48, n) * (1.0 - smoothstep(0.48, 0.62, n)) * fade;
        c = mix(c, c * 0.22, cloud);
        c += SKY_HORIZON * 0.30 * rim;
    }
    c = mix(c, FOG_OUTDOOR * 0.75, smoothstep(0.0, -0.20, up));
    // fear: the sky bleeds a little
    c *= 1.0 + vec3(0.55, 0.05, 0.05) * fear();
    return c;
#elif defined WORLD_OVERWORLD
    float day = dayFactor();
    float rain = rainAmount();
    float t = pow(h, 0.55);
    vec3 dayC = mix(SKY_DAY_HORIZON, SKY_DAY_ZENITH, t);
    vec3 nightC = mix(SKY_NIGHT_HORIZON, SKY_NIGHT_ZENITH, t);
    vec3 c = mix(nightC, dayC, day);
    vec3 sd = sunDirW();
    float toward = saturate(dot(dir, sd) * 0.5 + 0.5);
    // a dull, sickly sunset band
    float low = saturate(1.0 - abs(sd.y + 0.02) * 5.0);
    c += vec3(0.42, 0.17, 0.08) * pow(toward, 5.0) * low * (1.0 - h * 0.85) * (1.0 - rain * 0.8);
    // pale glare around the sun
    c += SUN_NOON * pow(saturate(dot(dir, sd)), 10.0) * 0.10 * day * (1.0 - rain * 0.85);
    c = mix(c, vec3(luma(c)) * 0.72, rain * 0.80);
    c = mix(c, mix(nightC, dayC, day) * 0.55, smoothstep(0.0, -0.30, up));
    return c;
#elif defined WORLD_NETHER
    return toLinear(fogColor) * 0.32;
#else
    // the End: almost black, a faint bruised purple near the horizon
    return mix(vec3(0.010, 0.008, 0.014), vec3(0.0025, 0.002, 0.004), h);
#endif
}

// colour the fog fades to, for a world-space view direction
vec3 fogColorFor(vec3 dir) {
    float eyeSky = eyeSkyFactor();
#if defined WORLD_KAKURIYO
    vec3 fc = mix(FOG_INDOOR, FOG_OUTDOOR, eyeSky);
    // a touch brighter toward the moon (forward scattering), and redder with fear
    float vdl = saturate(dot(dir, lightDirW()));
    fc += MOON_LIGHT * pow(vdl, 4.0) * 0.035 * eyeSky;
    fc *= 1.0 + vec3(0.60, 0.0, 0.0) * fear();
    return fc;
#elif defined WORLD_OVERWORLD
    vec3 d = normalize(vec3(dir.x, max(dir.y, 0.0) * 0.20 + 0.03, dir.z));
    vec3 fc = skyGradient(d) * 0.92;
    // caves and houses: the fog goes dark
    return mix(fc * 0.10 + MIN_LIGHT * 0.5, fc, mix(0.25, 1.0, eyeSky));
#elif defined WORLD_NETHER
    return toLinear(fogColor) * 0.30;
#else
    return vec3(0.010, 0.008, 0.014);
#endif
}

// analytic moon disc for reflections (water, the well): blood red in Kakuriyo
vec3 moonReflection(vec3 dir) {
#ifdef HAS_CELESTIAL_LIGHT
    float m = dot(dir, moonDirW());
    float disc = smoothstep(0.99935, 0.99965, m);
    float halo = pow(saturate(m), 220.0);
  #ifdef WORLD_KAKURIYO
    return vec3(1.00, 0.03, 0.02) * (disc * 2.4 + halo * 0.30);
  #else
    return vec3(0.70, 0.78, 0.95) * (disc * 1.6 + halo * 0.15) * (1.0 - dayFactor());
  #endif
#else
    return vec3(0.0);
#endif
}
