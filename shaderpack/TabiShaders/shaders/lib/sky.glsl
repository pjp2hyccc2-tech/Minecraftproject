// ============================================================== analytic sky (also used for fog colour and reflections)
vec3 skyRadiance(vec3 dir, vec3 sunDir) {
    float h = sunDir.y;
    float hor = pow(1.0 - clamp(dir.y, 0.0, 1.0), 3.0);
    float cosS = dot(dir, sunDir);
    float toward = 0.5 + 0.5 * cosS;
    float day = smoothstep(-0.02, 0.30, h);
    float twi = exp(-pow((h - 0.03) / 0.17, 2.0));
    float blueH = tBlue(h);
    float night = tNight(h);

    // clear daytime sky: deep blue zenith, pale horizon
    vec3 c = mix(vec3(0.13, 0.33, 0.85), vec3(0.62, 0.77, 0.96), hor) * 1.55 * day;
    // golden hour / sunset: amber toward the sun, rose-violet opposite, indigo zenith
    vec3 setHor = mix(vec3(0.40, 0.27, 0.52), vec3(1.30, 0.50, 0.15), pow(toward, 2.4));
    c = mix(c, mix(vec3(0.14, 0.17, 0.40), setHor, hor), twi * 0.94);
    // blue hour: the magic moment over the harbour
    c = mix(c, mix(vec3(0.035, 0.07, 0.24), vec3(0.30, 0.22, 0.42), hor * (0.6 + 0.4 * toward)), blueH * 0.9);
    // night: navy with a faint horizon glow
    c = mix(c, mix(vec3(0.006, 0.011, 0.030), vec3(0.020, 0.030, 0.065), hor), night);
    // city skyglow over Kobe (sodium + LED haze on the horizon)
    c += vec3(0.30, 0.17, 0.08) * 0.07 * hor * hor * night * rKobe();

    // forward scattering around the sun
    float mie = pow(max(cosS, 0.0), 10.0) * (0.25 + 1.6 * twi) + pow(max(cosS, 0.0), 160.0) * 2.5;
    c += vec3(1.0, 0.60, 0.30) * mie * smoothstep(-0.12, 0.02, h) * (1.0 - 0.85 * rainStrength);
    // moon halo
    c += vec3(0.22, 0.30, 0.50) * pow(max(dot(dir, -sunDir), 0.0), 30.0) * 0.18 * night;

    // below the horizon: darker (sea / ground)
    c *= mix(1.0, 0.4, smoothstep(0.0, -0.3, dir.y));
    // overcast
    c = mix(c, vec3(luma(c)) * vec3(0.90, 0.95, 1.0) * 0.8, rainStrength * 0.85);
    return c;
}
