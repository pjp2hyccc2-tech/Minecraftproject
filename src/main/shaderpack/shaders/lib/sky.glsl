// =====================================================================================
//  ReisatsuShaders - sky gradient, analytic moon (reflections), fog colour
// =====================================================================================

vec3 skyGradient(vec3 dir) {
    float up = dir.y;
    float h = saturate(up);
#if defined WORLD_MOUNTAIN
    // a cold blue-grey night sky; the high moon lights the haze around it
    vec3 c = mix(SKY_HORIZON, SKY_ZENITH, smoothstep(0.0, 0.75, h));
    float m = saturate(dot(dir, moonDirW()));
    c += MOON_LIGHT * (pow(m, 5.0) * 0.010 + pow(m, 48.0) * 0.045 + pow(m, 600.0) * 0.20);
    c = mix(c, FOG_AMBIENT, smoothstep(0.05, -0.25, up));
    return c * (1.0 - 0.35 * fear());
#elif defined WORLD_OVERWORLD
    float day = dayFactor();
    float rain = rainAmount();
    float t = pow(h, 0.55);
    vec3 dayC = mix(SKY_DAY_HORIZON, SKY_DAY_ZENITH, t);
    vec3 nightC = mix(SKY_NIGHT_HORIZON, SKY_NIGHT_ZENITH, t);
    vec3 c = mix(nightC, dayC, day);
    vec3 sd = sunDirW();
    float toward = saturate(dot(dir, sd) * 0.5 + 0.5);
    float low = saturate(1.0 - abs(sd.y + 0.02) * 5.0);
    c += vec3(0.42, 0.17, 0.08) * pow(toward, 5.0) * low * (1.0 - h * 0.85) * (1.0 - rain * 0.8);
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

// colour of distant fog for a world-space view direction (reflections, fallbacks)
vec3 fogColorFor(vec3 dir) {
    float eyeSky = eyeSkyFactor();
#if defined WORLD_MOUNTAIN
    vec3 fc = mix(FOG_TUNNEL, FOG_AMBIENT, eyeSky);
    float vdl = saturate(dot(dir, lightDirW()));
    fc += MOON_LIGHT * pow(vdl, 4.0) * 0.010 * eyeSky;
    return fc * (1.0 - 0.30 * fear());
#elif defined WORLD_OVERWORLD
    vec3 d = normalize(vec3(dir.x, max(dir.y, 0.0) * 0.20 + 0.03, dir.z));
    vec3 fc = skyGradient(d) * 0.92;
    return mix(fc * 0.10 + MIN_LIGHT * 0.5, fc, mix(0.25, 1.0, eyeSky));
#elif defined WORLD_NETHER
    return toLinear(fogColor) * 0.30;
#else
    return vec3(0.010, 0.008, 0.014);
#endif
}

// analytic moon disc for reflections (the stream, puddles, glass)
vec3 moonReflection(vec3 dir) {
#ifdef HAS_CELESTIAL_LIGHT
    float m = dot(dir, moonDirW());
    float disc = smoothstep(0.99935, 0.99965, m);
    float halo = pow(saturate(m), 220.0);
  #ifdef WORLD_MOUNTAIN
    return vec3(0.78, 0.86, 1.00) * (disc * 2.0 + halo * 0.12) * (1.0 - 0.35 * fear());
  #else
    return vec3(0.70, 0.78, 0.95) * (disc * 1.6 + halo * 0.15) * (1.0 - dayFactor());
  #endif
#else
    return vec3(0.0);
#endif
}
