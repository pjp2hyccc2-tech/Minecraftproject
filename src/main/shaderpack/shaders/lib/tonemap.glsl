// =====================================================================================
//  HuntCraft Shaders - tonemapping & colour grading
// =====================================================================================

// ACES filmic (Stephen Hill fit)
vec3 acesFitted(vec3 color) {
    const mat3 inM = mat3(
        0.59719, 0.07600, 0.02840,
        0.35458, 0.90834, 0.13383,
        0.04823, 0.01566, 0.83777);
    const mat3 outM = mat3(
        1.60475, -0.10208, -0.00327,
        -0.53108, 1.10813, -0.07276,
        -0.07367, -0.00605, 1.07602);
    vec3 v = inM * color;
    vec3 a = v * (v + 0.0245786) - 0.000090537;
    vec3 b = v * (0.983729 * v + 0.4329510) + 0.238081;
    v = a / b;
    return saturate3(outM * v);
}

vec3 colorGrade(vec3 c) {
#ifdef COLOR_GRADING
    // per hunting ground white balance / tint
    c *= DIM_GRADE_TINT;
    // lift (shadows get a tinted floor, like film)
    c = c * (1.0 - DIM_GRADE_LIFT) + DIM_GRADE_LIFT;
    // contrast around mid grey
    float contrast = CONTRAST * DIM_GRADE_CONTRAST;
    c = (c - 0.5) * contrast + 0.5;
    // saturation
    float l = luma(c);
    c = mix(vec3(l), c, SATURATION * DIM_GRADE_SAT);
#endif
    return saturate3(c);
}

vec3 vignette(vec3 c, vec2 uv, float strength) {
#ifdef VIGNETTE
    vec2 d = (uv - 0.5) * vec2(1.0, 1.0 / max(aspectRatio, 0.5)) * 1.25;
    float v = 1.0 - dot(d, d) * strength * VIGNETTE_STRENGTH;
    c *= saturate(v);
#endif
    return c;
}
