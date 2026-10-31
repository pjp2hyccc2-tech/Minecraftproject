#version 330 compatibility
// bloom + lens streaks, exposure, scene grading along the journey, ACES tone curve, film look
#include "/lib/settings.glsl"
#include "/lib/common.glsl"

/*
const bool colortex3MipmapEnabled = true;
*/

uniform sampler2D colortex0;
uniform sampler2D colortex3;
in vec2 texcoord;

/* RENDERTARGETS: 0 */
layout(location = 0) out vec4 outColor;

vec3 bloomTap(vec2 uv, float lod) {
    vec2 px = exp2(lod) / vec2(viewWidth, viewHeight);
    vec3 s = textureLod(colortex3, uv, lod).rgb * 4.0;
    s += (textureLod(colortex3, uv + vec2(px.x, 0.0), lod).rgb + textureLod(colortex3, uv - vec2(px.x, 0.0), lod).rgb) * 2.0;
    s += (textureLod(colortex3, uv + vec2(0.0, px.y), lod).rgb + textureLod(colortex3, uv - vec2(0.0, px.y), lod).rgb) * 2.0;
    s += textureLod(colortex3, uv + px, lod).rgb + textureLod(colortex3, uv - px, lod).rgb;
    s += textureLod(colortex3, uv + vec2(px.x, -px.y), lod).rgb + textureLod(colortex3, uv + vec2(-px.x, px.y), lod).rgb;
    return s / 16.0;
}

vec3 aces(vec3 x) {
    return clamp((x * (2.51 * x + 0.03)) / (x * (2.43 * x + 0.59) + 0.14), 0.0, 1.0);
}

void main() {
    vec2 uv = texcoord;
    float h = sunDirW().y;
    float night = tNight(h), golden = tGolden(h), blueH = tBlue(h), day = tDay(h);
    float kobe = rKobe(), vil = rVillage(), town = rTown(), mount = rMount(), sea = rSea();
#ifdef CHROMATIC_ABERRATION
    vec2 off = (uv - 0.5) * dot(uv - 0.5, uv - 0.5) * 0.012;
    vec3 col = vec3(texture(colortex0, uv + off).r, texture(colortex0, uv).g, texture(colortex0, uv - off).b);
#else
    vec3 col = texture(colortex0, uv).rgb;
#endif
    // wide soft bloom, stronger after dark
    vec3 bloom = vec3(0.0);
    float ws = 0.0;
    for (int i = 1; i <= 7; i++) {
        float w = pow(0.80, float(i));
        bloom += bloomTap(uv, float(i)) * w;
        ws += w;
    }
    col += bloom / ws * BLOOM_STRENGTH * mix(0.10, 0.36, max(night, blueH * 0.8));
#ifdef LENS_STREAKS
    vec3 streak = vec3(0.0);
    float sx = 12.0 / viewWidth;
    for (int i = -10; i <= 10; i++) streak += textureLod(colortex3, uv + vec2(float(i) * sx, 0.0), 3.0).rgb * exp(-abs(float(i)) * 0.25);
    col += streak * vec3(0.50, 0.72, 1.30) * 0.010 * BLOOM_STRENGTH * max(night, blueH);
#endif
    // exposure: opens up at night and indoors so lamplit streets and rooms read well
    float eyeSky = float(eyeBrightnessSmooth.y) / 240.0;
    col *= EXPOSURE * mix(2.8, 1.0, day * mix(0.55, 1.0, eyeSky)) * mix(1.0, 1.25, blueH);
    // white balance per scene and time of day (linear light, before the tone curve)
    vec3 wb = vec3(1.0);
    wb *= mix(vec3(1.0), vec3(1.07, 1.00, 0.88), vil);      // village: warm, nostalgic summer
    wb *= mix(vec3(1.0), vec3(1.03, 1.00, 0.95), town);     // old town: soft warmth
    wb *= mix(vec3(1.0), vec3(0.93, 1.00, 1.07), mount);    // mountains: cool and misty
    wb *= mix(vec3(1.0), vec3(0.97, 1.01, 1.05), sea);      // coast: clear blue
    wb *= mix(vec3(1.0), vec3(0.97, 0.99, 1.05), kobe);     // Kobe: crisp, cinematic
    wb *= mix(vec3(1.0), vec3(1.12, 0.98, 0.84), golden);   // golden hour
    wb *= mix(vec3(1.0), vec3(0.90, 0.95, 1.14), blueH);    // blue hour
    col *= mix(vec3(1.0), wb, GRADING_STRENGTH);
    col = toSRGB(aces(col));
    // split toning: cool shadows at night / in the city, warm highlights at dusk and in the village
    float l = luma(col);
    vec3 shadowTint = vec3(-0.012, 0.004, 0.032) * (night * 0.9 + kobe * 0.4 + blueH * 0.6 + mount * 0.4);
    vec3 highTint = vec3(0.030, 0.012, -0.020) * (golden + night * 0.5 + vil * 0.5);
    col += (shadowTint * (1.0 - l) + highTint * l) * GRADING_STRENGTH;
    float sat = 1.0 + 0.10 * sea + 0.06 * vil - 0.12 * mount + 0.12 * kobe * night + 0.08 * golden - 0.35 * rainStrength;
    col = mix(vec3(luma(col)), col, mix(1.0, sat, GRADING_STRENGTH));
    col = clamp(col, 0.0, 1.0);
    col = mix(col, col * col * (3.0 - 2.0 * col), 0.16 * GRADING_STRENGTH);  // gentle S-curve
    col = col * (1.0 - 0.035 * vil) + 0.025 * vil;                             // faded film blacks in the village
    // vignette + film grain
    vec2 q = (uv - 0.5) * vec2(viewWidth / viewHeight, 1.0);
    col *= 1.0 - VIGNETTE_STRENGTH * 0.28 * pow(dot(q, q) * 1.25, 1.3) * (1.0 + 0.5 * night);
    col += (hash12(gl_FragCoord.xy + float(frameCounter % 128) * 17.0) - 0.5) * 0.014 * FILM_GRAIN;
    outColor = vec4(clamp(col, 0.0, 1.0), 1.0);
}
