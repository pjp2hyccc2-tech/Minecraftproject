// =====================================================================================
//  HuntCraft Shaders - sky dome, sun / moon, fog colour
// =====================================================================================

// base sky gradient for a world-space direction (no sun disc)
vec3 skyGradient(vec3 dir) {
    float up = dir.y;
    float day = dayFactor();
    float low = sunLowFactor();
    float rain = rainFactor();

    vec3 zenithDay = DIM_SKY_ZENITH;
    vec3 horizonDay = DIM_SKY_HORIZON;
    // warm sunrise / sunset band
    horizonDay = mix(horizonDay, vec3(1.0, 0.55, 0.30), low * 0.75);
    zenithDay = mix(zenithDay, vec3(0.30, 0.30, 0.55), low * 0.35);

    vec3 zenithNight = vec3(0.012, 0.022, 0.050) * NIGHT_BRIGHTNESS;
    vec3 horizonNight = vec3(0.045, 0.055, 0.095) * NIGHT_BRIGHTNESS;

    vec3 zenith = mix(zenithNight, zenithDay, day);
    vec3 horizon = mix(horizonNight, horizonDay, day);

    // rain / overcast
    zenith = mix(zenith, vec3(luma(zenith)) * 0.55, rain * 0.85);
    horizon = mix(horizon, vec3(luma(horizon)) * 0.70, rain * 0.85);

    float t = pow(saturate(up), 0.55);
    vec3 sky = mix(horizon, zenith, t);

    // below the horizon: darker ground haze
    float below = smoothstep(0.0, -0.35, up);
    sky = mix(sky, horizon * 0.55, below);

#ifndef NO_SUN
    // sun-side glow near the horizon
    vec3 sunDir = sunDirWorld();
    float sunDot = saturate(dot(dir, sunDir));
    vec3 glowCol = mix(vec3(1.0, 0.75, 0.45), vec3(1.0, 0.45, 0.18), low);
    float glow = pow(sunDot, 6.0) * (0.10 + 0.55 * low) * day * (1.0 - rain * 0.8);
    glow *= 1.0 - saturate(up) * 0.5;
    sky += glowCol * glow;
#endif
    return sky;
}

// sun & moon discs (HDR, meant to bloom)
vec3 skyBodies(vec3 dir) {
    vec3 c = vec3(0.0);
#ifndef NO_SUN
    float rain = rainFactor();
    vec3 sunDir = sunDirWorld();
    float sunDot = dot(dir, sunDir);
    float disc = smoothstep(0.99930, 0.99965, sunDot);
    float corona = pow(saturate(sunDot), 300.0);
    vec3 sunCol = mix(DIM_SUN_NOON, DIM_SUN_LOW, sunLowFactor());
    c += sunCol * (disc * 9.0 + corona * 1.2) * dayFactor() * (1.0 - rain * 0.9);

    vec3 moonDir = moonDirWorld();
    float moonDot = dot(dir, moonDir);
    float moonDisc = smoothstep(0.99955, 0.99975, moonDot);
    float moonGlow = pow(saturate(moonDot), 400.0);
    float phase = 1.0 - 0.75 * float(abs(moonPhase - 4) < 2);
    c += vec3(0.85, 0.90, 1.00) * (moonDisc * 2.2 + moonGlow * 0.4) * (1.0 - dayFactor()) * phase * (1.0 - rain * 0.9);
#endif
    return c;
}

vec3 getSky(vec3 dir) {
    return skyGradient(dir) + skyBodies(dir);
}

// colour of the atmospheric fog for a view direction
vec3 fogColorFor(vec3 dir) {
    vec3 d = normalize(vec3(dir.x, max(dir.y, 0.0) * 0.25 + 0.02, dir.z));
    vec3 c = skyGradient(d) * DIM_FOG_TINT;
#ifndef NO_SUN
    // in-scattering towards the sun
    float vdl = saturate(dot(dir, sunDirWorld()));
    c += sunLightColor() * pow(vdl, 5.0) * 0.08;
#endif
    return c;
}

// underwater / lava / powder-snow fog colour (linear)
vec3 eyeFluidFogColor() {
    if (isEyeInWater == 1) return DIM_WATER_COLOR * (0.25 + 0.75 * dayFactor()) * 0.35;
    if (isEyeInWater == 2) return vec3(1.0, 0.30, 0.05) * 1.2;
    if (isEyeInWater == 3) return vec3(0.75, 0.80, 0.90);
    return vec3(0.0);
}
