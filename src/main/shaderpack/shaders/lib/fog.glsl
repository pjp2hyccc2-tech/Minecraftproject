// =====================================================================================
//  HuntCraft Shaders - atmospheric / height / fluid fog and volumetric light shafts
// =====================================================================================

// 0 underground / under a roof, 1 under the open sky (smoothed eye sky light)
float eyeSkyFactor() {
    return smoothstep(0.05, 0.55, float(eyeBrightnessSmooth.y) / 240.0);
}

// base (altitude independent) fog density per block
float baseFogDensity() {
    float d = DIM_FOG_DENSITY * FOG_DENSITY * 0.0014;
    d *= 1.0 + rainFactor() * 1.6;
    d *= mix(1.2, 1.0, dayFactor());
    // caves / interiors: the sky haze thins out so dark places stay readable
    d *= mix(0.30, 1.0, eyeSkyFactor());
    return d;
}

// height fog optical depth along a ray of length dist in direction dirW from the camera.
// Density falls off exponentially above DIM_FOG_BASE and stops growing below it, so valleys and
// low worlds (superflat, caves) are hazy but never a white wall.
float heightFogDepth(vec3 dirW, float dist) {
#ifdef HEIGHT_FOG
    float k = DIM_FOG_FALLOFF;
    if (k <= 0.0) return 0.0;
    float camY = max(cameraPosition.y, DIM_FOG_BASE - 8.0);
    float dens0 = baseFogDensity() * 1.3 * exp(-(camY - DIM_FOG_BASE) * k);
    float dirY = dirW.y;
    float a = dist * dirY * k;
    float integral = abs(a) > 1e-3 ? (1.0 - exp(-a)) / (dirY * k) : dist;
    integral = clamp(integral, 0.0, dist * 2.0);
    return dens0 * integral;
#else
    return 0.0;
#endif
}

// blizzard haze modulation (frostpeak) - slowly drifting density patches
float hazeMultiplier(vec3 dirW) {
#ifdef WEATHER_HAZE
    if (DIM_HAZE <= 0.0) return 1.0;
    vec2 p = (cameraPosition.xz + dirW.xz * 40.0) * 0.012 + vec2(frameTimeCounter * 0.07, frameTimeCounter * 0.03);
    float n = fbm(p);
    return 1.0 + DIM_HAZE * (0.35 + 0.9 * n);
#else
    return 1.0;
#endif
}

// apply atmospheric fog to a scene colour.
//  dirW : normalised world-space view direction, dist : distance to the surface (blocks)
//  isSky: only the horizon haze is applied (slant path through the fog layer)
vec3 applyAtmosphericFog(vec3 color, vec3 dirW, float dist, bool isSky) {
    float haze = hazeMultiplier(dirW);
    float depth = isSky ? 0.0 : dist * baseFogDensity() * haze;
    float hDepth = heightFogDepth(dirW, isSky ? 1500.0 : dist) * (isSky ? 0.5 : 1.0) * mix(1.0, haze, 0.5);
    float amount = saturate(1.0 - exp(-(depth + hDepth)));
    vec3 fogCol = fogColorFor(dirW);
    return mix(color, fogCol, amount);
}

// fog when the camera is inside water / lava / powder snow
vec3 applyFluidFog(vec3 color, float dist) {
    if (isEyeInWater == 0) return color;
    float k = isEyeInWater == 1 ? 0.075 : (isEyeInWater == 2 ? 0.9 : 0.5);
    float a = 1.0 - exp(-dist * k);
    vec3 fc = eyeFluidFogColor();
    if (isEyeInWater == 1) fc *= 0.6 + 0.4 * float(eyeBrightnessSmooth.y) / 240.0;
    return mix(color, fc, saturate(a));
}

#ifdef VOLUMETRIC_LIGHT
    #ifdef HC_SHADOWS_ON
        #define HC_VL_ON
    #endif
#endif

// volumetric light shafts: sun / moon light scattered along the view ray, occluded by the shadow map.
// Strongly forward-scattering (bright around the light, faint elsewhere) so it reads as god rays,
// not as a flat wash.
vec3 volumetricLight(vec3 dirW, float dist, float dither, bool isSky) {
#ifdef HC_VL_ON
    vec3 lightCol = shadowLightColor();
    if (luma(lightCol) < 0.002 || DIM_VL <= 0.0) return vec3(0.0);
    vec3 L = lightDirWorld();
    float vdl = dot(dirW, L);
    float phase = henyeyGreenstein(vdl, 0.62) * 2.0 + 0.05;
    float maxDist = min(dist, shadowDistance * 0.92);
    if (isSky) maxDist = shadowDistance * 0.92;
    float stepLen = maxDist / float(VL_SAMPLES);
    float k = DIM_FOG_FALLOFF;
    float camY = cameraPosition.y;
    float accum = 0.0;
    for (int i = 0; i < VL_SAMPLES; i++) {
        float t = (float(i) + dither) * stepLen;
        vec3 p = dirW * t;
        float vis = shadowVisibility(p);
        float dens = 1.0;
        if (k > 0.0) {
            float y = max(camY + p.y, DIM_FOG_BASE - 8.0);
            dens = 0.35 + 0.65 * exp(-(y - DIM_FOG_BASE) * k * 0.8);
        }
        if (isEyeInWater == 1) dens *= 1.6;
        accum += vis * dens * stepLen;
    }
    float strength = 0.0024 * VL_STRENGTH * DIM_VL * (1.0 + rainFactor() * 0.5) * mix(0.5, 1.0, eyeSkyFactor());
    float scatter = 1.0 - exp(-accum * strength);
    vec3 col = lightCol * phase * scatter;
    col = mix(col, col * vec3(0.5, 0.7, 1.0), float(isEyeInWater == 1) * 0.6);
    return col;
#else
    return vec3(0.0);
#endif
}
