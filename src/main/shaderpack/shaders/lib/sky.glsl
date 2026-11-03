// =====================================================================================
//  HitoriShaders - sky gradient, analytic moon (reflections), fog colour
//  SHIRAKABA: 3 a.m., light rain over a quiet suburb - a low ceiling of rain clouds, dull
//  orange-grey from the town's lights under them (strongest toward the station), no moon, no
//  stars; the rain veils the horizon.
// =====================================================================================

#ifdef WORLD_SHIRAKABA
// the low rain clouds: slow drifting fbm on a plane ~200 blocks up
float cloudDeck(vec3 dir) {
    if (dir.y <= 0.0) return 0.5;
    vec2 p = dir.xz / (dir.y + 0.06) * 2.0 + vec2(0.030, 0.010) * frameTimeCounter;
    float n = fbm2(p * 0.9) * 0.70 + fbm2(p * 2.7 + 7.3) * 0.30;
    return n;
}
#endif

vec3 skyGradient(vec3 dir) {
    float up = dir.y;
    float h = saturate(up);
#if defined WORLD_SHIRAKABA
    vec3 c = mix(SKY_HORIZON, SKY_ZENITH, smoothstep(0.0, 0.55, h));
    // the town under the clouds: a dull orange glow low all round, more toward the station
    vec3 hd = normalize(vec3(dir.x, 0.0, dir.z) + 1e-5);
    float toward = saturate(dot(hd, TOWN_DIR) * 0.5 + 0.5);
    float band = exp(-max(up, 0.0) * 7.0) * smoothstep(-0.12, 0.02, up);
    vec3 glow = TOWN_GLOW * (0.55 + 1.45 * pow(toward, 3.0)) * band;
    // the cloud base catches the glow: brighter where the clouds hang lower (dense)
    float cl = cloudDeck(dir);
    float lit = smoothstep(0.35, 0.75, cl);
    c *= 0.82 + 0.40 * lit;
    c += TOWN_GLOW * (0.4 + 0.6 * pow(toward, 2.0)) * lit * exp(-max(up, 0.0) * 3.0) * 0.40;
    c += glow;
    // below the horizon: the dark town, the rain haze
    c = mix(c, FOG_AMBIENT * 0.8, smoothstep(0.02, -0.20, up));
    return c * (1.0 - 0.25 * fear());
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
#if defined WORLD_SHIRAKABA
    vec3 hd = normalize(vec3(dir.x, 0.0, dir.z) + 1e-5);
    float toward = saturate(dot(hd, TOWN_DIR) * 0.5 + 0.5);
    vec3 fc = FOG_AMBIENT + TOWN_GLOW * (0.3 + 0.7 * pow(toward, 3.0)) * 0.45 * exp(-max(dir.y, 0.0) * 4.0);
    return fc * (1.0 - 0.25 * fear());
#elif defined WORLD_OVERWORLD
    float eyeSky = eyeSkyFactor();
    vec3 d = normalize(vec3(dir.x, max(dir.y, 0.0) * 0.20 + 0.03, dir.z));
    vec3 fc = skyGradient(d) * 0.92;
    return mix(fc * 0.10 + MIN_LIGHT * 0.5, fc, mix(0.25, 1.0, eyeSky));
#elif defined WORLD_NETHER
    return toLinear(fogColor) * 0.30;
#else
    return vec3(0.010, 0.008, 0.014);
#endif
}

// analytic moon disc for reflections (water, glass) - none over the rainy town
vec3 moonReflection(vec3 dir) {
#ifdef HAS_CELESTIAL_LIGHT
    float m = dot(dir, moonDirW());
    float disc = smoothstep(0.99935, 0.99965, m);
    float halo = pow(saturate(m), 220.0);
    return vec3(0.70, 0.78, 0.95) * (disc * 1.6 + halo * 0.15) * (1.0 - dayFactor());
#else
    return vec3(0.0);
#endif
}
