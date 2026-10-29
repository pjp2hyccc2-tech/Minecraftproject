// =====================================================================================
//  HuntCraft Shaders - per-dimension palette
//  One of DIM_VILLAGE / DIM_HILLS / DIM_FROST / DIM_EMBER / DIM_NETHER / DIM_END is
//  defined by the per-dimension program stubs; nothing defined = overworld / unknown.
// =====================================================================================

#if defined DIM_VILLAGE
    // 拠点の村 : warm cherry-blossom village, late afternoon gold
    #define DIM_ID 1
    const vec3  DIM_SUN_NOON      = vec3(1.00, 0.90, 0.76) * 1.75;
    const vec3  DIM_SUN_LOW       = vec3(1.00, 0.56, 0.30) * 1.45;
    const vec3  DIM_SKY_ZENITH    = vec3(0.30, 0.47, 0.80);
    const vec3  DIM_SKY_HORIZON   = vec3(0.97, 0.80, 0.74);
    const vec3  DIM_AMBIENT       = vec3(0.66, 0.62, 0.72);
    const float DIM_AMBIENT_MUL   = 0.55;
    const vec3  DIM_FOG_TINT      = vec3(1.00, 0.88, 0.84);
    const float DIM_FOG_DENSITY   = 0.55;
    const float DIM_FOG_BASE      = 62.0;
    const float DIM_FOG_FALLOFF   = 0.045;
    const float DIM_VL            = 1.15;
    const vec3  DIM_GRADE_TINT    = vec3(1.04, 0.99, 0.95);
    const float DIM_GRADE_SAT     = 1.10;
    const float DIM_GRADE_CONTRAST= 1.02;
    const vec3  DIM_GRADE_LIFT    = vec3(0.020, 0.012, 0.016);
    const float DIM_HAZE          = 0.0;
    const float DIM_HEAT          = 0.0;
    const float DIM_ASH           = 0.0;
    const vec3  DIM_WATER_COLOR   = vec3(0.12, 0.32, 0.45);
#elif defined DIM_HILLS
    // 翠緑の丘陵 : lush green, golden sun, soft blue haze
    #define DIM_ID 2
    const vec3  DIM_SUN_NOON      = vec3(1.00, 0.95, 0.84) * 1.75;
    const vec3  DIM_SUN_LOW       = vec3(1.00, 0.52, 0.22) * 1.40;
    const vec3  DIM_SKY_ZENITH    = vec3(0.24, 0.48, 0.90);
    const vec3  DIM_SKY_HORIZON   = vec3(0.66, 0.78, 0.92);
    const vec3  DIM_AMBIENT       = vec3(0.55, 0.66, 0.86);
    const float DIM_AMBIENT_MUL   = 0.52;
    const vec3  DIM_FOG_TINT      = vec3(0.86, 0.93, 1.00);
    const float DIM_FOG_DENSITY   = 0.80;
    const float DIM_FOG_BASE      = 64.0;
    const float DIM_FOG_FALLOFF   = 0.035;
    const float DIM_VL            = 1.25;
    const vec3  DIM_GRADE_TINT    = vec3(1.00, 1.00, 0.96);
    const float DIM_GRADE_SAT     = 1.15;
    const float DIM_GRADE_CONTRAST= 1.06;
    const vec3  DIM_GRADE_LIFT    = vec3(0.012, 0.016, 0.012);
    const float DIM_HAZE          = 0.0;
    const float DIM_HEAT          = 0.0;
    const float DIM_ASH           = 0.0;
    const vec3  DIM_WATER_COLOR   = vec3(0.10, 0.35, 0.42);
#elif defined DIM_FROST
    // 霜嶺山脈 : cold blue, blizzard haze
    #define DIM_ID 3
    const vec3  DIM_SUN_NOON      = vec3(0.90, 0.95, 1.05) * 1.45;
    const vec3  DIM_SUN_LOW       = vec3(1.00, 0.70, 0.55) * 1.10;
    const vec3  DIM_SKY_ZENITH    = vec3(0.42, 0.58, 0.84);
    const vec3  DIM_SKY_HORIZON   = vec3(0.80, 0.87, 0.95);
    const vec3  DIM_AMBIENT       = vec3(0.62, 0.72, 0.92);
    const float DIM_AMBIENT_MUL   = 0.62;
    const vec3  DIM_FOG_TINT      = vec3(0.82, 0.89, 1.00);
    const float DIM_FOG_DENSITY   = 1.60;
    const float DIM_FOG_BASE      = 90.0;
    const float DIM_FOG_FALLOFF   = 0.025;
    const float DIM_VL            = 0.80;
    const vec3  DIM_GRADE_TINT    = vec3(0.94, 0.98, 1.07);
    const float DIM_GRADE_SAT     = 0.90;
    const float DIM_GRADE_CONTRAST= 1.00;
    const vec3  DIM_GRADE_LIFT    = vec3(0.020, 0.026, 0.040);
    const float DIM_HAZE          = 1.0;
    const float DIM_HEAT          = 0.0;
    const float DIM_ASH           = 0.0;
    const vec3  DIM_WATER_COLOR   = vec3(0.10, 0.30, 0.48);
#elif defined DIM_EMBER
    // 熾火の大カルデラ : dark red-brown sky, hot orange light, ash and heat haze
    #define DIM_ID 4
    const vec3  DIM_SUN_NOON      = vec3(1.00, 0.58, 0.32) * 1.20;
    const vec3  DIM_SUN_LOW       = vec3(1.00, 0.38, 0.14) * 1.25;
    const vec3  DIM_SKY_ZENITH    = vec3(0.07, 0.03, 0.03);
    const vec3  DIM_SKY_HORIZON   = vec3(0.40, 0.12, 0.045);
    const vec3  DIM_AMBIENT       = vec3(0.66, 0.34, 0.22);
    const float DIM_AMBIENT_MUL   = 0.50;
    const vec3  DIM_FOG_TINT      = vec3(0.92, 0.42, 0.20);
    const float DIM_FOG_DENSITY   = 1.10;
    const float DIM_FOG_BASE      = 70.0;
    const float DIM_FOG_FALLOFF   = 0.030;
    const float DIM_VL            = 1.30;
    const vec3  DIM_GRADE_TINT    = vec3(1.08, 0.95, 0.86);
    const float DIM_GRADE_SAT     = 1.12;
    const float DIM_GRADE_CONTRAST= 1.12;
    const vec3  DIM_GRADE_LIFT    = vec3(0.024, 0.006, 0.003);
    const float DIM_HAZE          = 0.0;
    const float DIM_HEAT          = 1.0;
    const float DIM_ASH           = 1.0;
    const vec3  DIM_WATER_COLOR   = vec3(0.20, 0.30, 0.30);
#elif defined DIM_NETHER
    #define DIM_ID 5
    #define NO_SUN
    const vec3  DIM_SUN_NOON      = vec3(0.0);
    const vec3  DIM_SUN_LOW       = vec3(0.0);
    const vec3  DIM_SKY_ZENITH    = vec3(0.18, 0.05, 0.04);
    const vec3  DIM_SKY_HORIZON   = vec3(0.35, 0.10, 0.06);
    const vec3  DIM_AMBIENT       = vec3(0.70, 0.35, 0.25);
    const float DIM_AMBIENT_MUL   = 0.60;
    const vec3  DIM_FOG_TINT      = vec3(0.60, 0.20, 0.12);
    const float DIM_FOG_DENSITY   = 2.50;
    const float DIM_FOG_BASE      = 40.0;
    const float DIM_FOG_FALLOFF   = 0.0;
    const float DIM_VL            = 0.0;
    const vec3  DIM_GRADE_TINT    = vec3(1.05, 0.95, 0.90);
    const float DIM_GRADE_SAT     = 1.05;
    const float DIM_GRADE_CONTRAST= 1.05;
    const vec3  DIM_GRADE_LIFT    = vec3(0.02, 0.01, 0.01);
    const float DIM_HAZE          = 0.0;
    const float DIM_HEAT          = 0.6;
    const float DIM_ASH           = 0.0;
    const vec3  DIM_WATER_COLOR   = vec3(0.2, 0.3, 0.3);
#elif defined DIM_END
    #define DIM_ID 6
    #define NO_SUN
    const vec3  DIM_SUN_NOON      = vec3(0.0);
    const vec3  DIM_SUN_LOW       = vec3(0.0);
    const vec3  DIM_SKY_ZENITH    = vec3(0.03, 0.02, 0.06);
    const vec3  DIM_SKY_HORIZON   = vec3(0.10, 0.07, 0.14);
    const vec3  DIM_AMBIENT       = vec3(0.60, 0.50, 0.75);
    const float DIM_AMBIENT_MUL   = 0.55;
    const vec3  DIM_FOG_TINT      = vec3(0.30, 0.22, 0.40);
    const float DIM_FOG_DENSITY   = 1.00;
    const float DIM_FOG_BASE      = 50.0;
    const float DIM_FOG_FALLOFF   = 0.0;
    const float DIM_VL            = 0.0;
    const vec3  DIM_GRADE_TINT    = vec3(0.98, 0.96, 1.06);
    const float DIM_GRADE_SAT     = 1.00;
    const float DIM_GRADE_CONTRAST= 1.05;
    const vec3  DIM_GRADE_LIFT    = vec3(0.01, 0.01, 0.02);
    const float DIM_HAZE          = 0.0;
    const float DIM_HEAT          = 0.0;
    const float DIM_ASH           = 0.0;
    const vec3  DIM_WATER_COLOR   = vec3(0.1, 0.3, 0.4);
#else
    // overworld / any other dimension
    #define DIM_ID 0
    const vec3  DIM_SUN_NOON      = vec3(1.00, 0.95, 0.87) * 1.65;
    const vec3  DIM_SUN_LOW       = vec3(1.00, 0.50, 0.22) * 1.35;
    const vec3  DIM_SKY_ZENITH    = vec3(0.22, 0.45, 0.86);
    const vec3  DIM_SKY_HORIZON   = vec3(0.64, 0.76, 0.91);
    const vec3  DIM_AMBIENT       = vec3(0.56, 0.66, 0.86);
    const float DIM_AMBIENT_MUL   = 0.52;
    const vec3  DIM_FOG_TINT      = vec3(1.00, 1.00, 1.00);
    const float DIM_FOG_DENSITY   = 1.00;
    const float DIM_FOG_BASE      = 64.0;
    const float DIM_FOG_FALLOFF   = 0.030;
    const float DIM_VL            = 1.00;
    const vec3  DIM_GRADE_TINT    = vec3(1.00, 1.00, 1.00);
    const float DIM_GRADE_SAT     = 1.05;
    const float DIM_GRADE_CONTRAST= 1.02;
    const vec3  DIM_GRADE_LIFT    = vec3(0.012, 0.012, 0.014);
    const float DIM_HAZE          = 0.0;
    const float DIM_HEAT          = 0.0;
    const float DIM_ASH           = 0.0;
    const vec3  DIM_WATER_COLOR   = vec3(0.10, 0.33, 0.45);
#endif

const vec3 BLOCKLIGHT_COLOR = vec3(1.00, 0.62, 0.32);
const vec3 MOONLIGHT_COLOR  = vec3(0.30, 0.40, 0.62);

// ---- time of day ----------------------------------------------------------------------
#ifdef NO_SUN
    float sunElevation() { return 0.5; }
    float dayFactor() { return 0.0; }
    float sunLowFactor() { return 0.0; }
    bool lightIsSun() { return false; }
#else
    float sunElevation() { return sunDirWorld().y; }
    // 0 = night, 1 = full day (soft transition around the horizon)
    float dayFactor() { return smoothstep(-0.10, 0.14, sunElevation()); }
    // 1 when the sun sits on the horizon (sunrise / sunset), 0 when high
    float sunLowFactor() { return saturate(1.0 - abs(sunElevation()) * 1.8); }
    bool lightIsSun() { return sunAngle < 0.5; }
#endif

float rainFactor() { return rainStrength; }

// colour of the direct sun light (linear, HDR)
vec3 sunLightColor() {
    float day = dayFactor();
    float low = sunLowFactor();
    vec3 c = mix(DIM_SUN_NOON, DIM_SUN_LOW, low * low);
    c *= day;
    c *= 1.0 - rainFactor() * 0.85;
    return c;
}

// colour of the moon light (linear)
vec3 moonLightColor() {
    float night = 1.0 - dayFactor();
    float phase = 1.0 - 0.6 * float(abs(moonPhase - 4) < 2);
    return MOONLIGHT_COLOR * 0.28 * night * phase * NIGHT_BRIGHTNESS * (1.0 - rainFactor() * 0.8);
}

// light colour of whatever casts shadows right now
vec3 shadowLightColor() {
    return lightIsSun() ? sunLightColor() : moonLightColor();
}

// ambient light coming from the sky dome (linear)
vec3 skyAmbientColor() {
    float day = dayFactor();
    vec3 dayAmb = DIM_AMBIENT * DIM_AMBIENT_MUL;
    dayAmb = mix(dayAmb, vec3(0.85, 0.55, 0.40) * 0.45, sunLowFactor() * 0.5);
    vec3 nightAmb = vec3(0.12, 0.16, 0.28) * 0.20 * NIGHT_BRIGHTNESS;
    vec3 amb = mix(nightAmb, dayAmb, day);
    amb = mix(amb, vec3(luma(amb)) * 0.75, rainFactor() * 0.7);
#ifdef NO_SUN
    amb = DIM_AMBIENT * DIM_AMBIENT_MUL;
#endif
    return amb;
}
