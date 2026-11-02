// =====================================================================================
//  MeikonShaders - final: lens, bloom, exposure, tone curve, grading and - in Kakuriyo only -
//  everything fear (rainStrength) and danger (thunderStrength) do to the picture:
//    fear  : heartbeat vignette, desaturation that keeps the reds, film grain,
//            chromatic aberration + lens warp (high fear), glitch slices / frame jitter (> 0.8)
//    danger: red pulse, stronger aberration, tunnel-vision edge blur
//  FLASH_REDUCTION removes the glitches, the jitter and every pulsing element.
// =====================================================================================
#include "/lib/header.glsl"

#ifdef VERTEX_SHADER
out vec2 texcoord;
void main() {
    gl_Position = ftransform();
    texcoord = (gl_TextureMatrix[0] * gl_MultiTexCoord0).xy;
}
#endif

#ifdef FRAGMENT_SHADER
in vec2 texcoord;

const bool colortex0MipmapEnabled = true;   // blurred edges (danger / darkness)
const bool colortex3MipmapEnabled = true;   // wide bloom

// one heartbeat: "lub" then a softer "dub"
float beat(float t, float bpm) {
    float x = fract(t * bpm / 60.0);
    return exp(-sq((x - 0.05) * 20.0)) + 0.65 * exp(-sq((x - 0.24) * 24.0));
}
// the rate rises with fear. Rates are cross-faded instead of changing one rate, because a
// rate that follows fear would make the phase (time * rate) jump around every frame.
float heartbeat(float f) {
    float t = frameTimeCounter;
    float wSlow = 1.0 - smoothstep(0.20, 0.50, f);
    float wFast = smoothstep(0.55, 0.85, f);
    float wMid = max(1.0 - wSlow - wFast, 0.0);
    return beat(t, 64.0) * wSlow + beat(t, 92.0) * wMid + beat(t, 126.0) * wFast;
}

vec3 sceneWithAberration(vec2 uv, float ca) {
    vec2 d = (uv - 0.5) * ca;
    vec3 c;
    c.r = texture(colortex0, clamp(uv + d, vec2(0.0005), vec2(0.9995))).r;
    c.g = texture(colortex0, uv).g;
    c.b = texture(colortex0, clamp(uv - d, vec2(0.0005), vec2(0.9995))).b;
    return c;
}

// filmic curve (ACES fit): deep toe, soft shoulder
vec3 toneCurve(vec3 x) {
    x = max(x, vec3(0.0));
    return saturate((x * (2.51 * x + 0.03)) / (x * (2.43 * x + 0.59) + 0.14));
}

vec3 grade(vec3 c, float f) {
#if defined WORLD_KAKURIYO
    float sat = 0.86 * (1.0 - 0.72 * f);
    vec3 shadowTint = vec3(0.90, 0.97, 1.12);       // cold blue-grey dark
    float contrast = 1.05 + 0.10 * f;
    vec3 lift = vec3(0.011, 0.013, 0.018);
#elif defined WORLD_OVERWORLD
    float sat = 0.68 * (1.0 - 0.25 * rainAmount());
    vec3 shadowTint = vec3(0.93, 0.98, 1.08);
    float contrast = 1.04;
    vec3 lift = vec3(0.008, 0.009, 0.012);
#elif defined WORLD_NETHER
    float sat = 0.72;
    vec3 shadowTint = vec3(1.04, 0.97, 0.95);
    float contrast = 1.05;
    vec3 lift = vec3(0.010, 0.007, 0.006);
#else
    float sat = 0.66;
    vec3 shadowTint = vec3(0.98, 0.95, 1.06);
    float contrast = 1.05;
    vec3 lift = vec3(0.008, 0.007, 0.010);
#endif
    float l = luma(c);
    // desaturate, but blood, vermilion lacquer and the moon keep their red
    float keep = smoothstep(0.42, 0.80, redness(c)) * smoothstep(0.03, 0.12, maxOf(c));
    c = mix(vec3(l), c, mix(sat, max(sat, 1.0), keep));
    c *= mix(shadowTint, vec3(1.0), smoothstep(0.0, 0.45, l));
    c = max((c - 0.40) * contrast + 0.40, vec3(0.0));
    return c + lift * (1.0 - c);
}

void main() {
    vec2 uv = texcoord;
    float F = fear();
    float D = danger();
    float t = frameTimeCounter;

    vec2 q = (texcoord - 0.5) * vec2(aspectRatio, 1.0);
    float r = length(q) / length(vec2(aspectRatio, 1.0) * 0.5);    // 0 centre .. 1 corner

    float pulse = 0.0;
    float glitch = 0.0;
#ifdef WORLD_KAKURIYO
  #ifndef FLASH_REDUCTION
    pulse = heartbeat(F) * smoothstep(0.05, 0.35, F + D);
    // ---- glitch slices + frame jitter above ~0.8 fear ---------------------------------
    float gAmt = smoothstep(0.78, 1.0, F);
    if (gAmt > 0.0) {
        float slot = floor(t * 8.0);
        if (hash11(slot * 1.37 + 0.5) > 1.0 - (0.03 + 0.07 * gAmt)) {
            float band = floor(uv.y * 22.0 + hash11(slot) * 9.0);
            float on = step(0.72, hash12(vec2(band, slot)));
            uv.x += (hash12(vec2(band * 1.7, slot + 3.0)) - 0.5) * 0.045 * gAmt * on;
            uv.y += (hash11(slot * 3.1) - 0.5) * 0.010 * gAmt;
            glitch = on * gAmt;
        }
    }
  #endif
    // ---- lens warp: the middle of the view swells at high fear / in a chase -----------
    float warp = smoothstep(0.55, 1.0, F) * 0.10 + D * 0.05;
  #ifndef FLASH_REDUCTION
    warp *= 0.80 + 0.20 * sin(t * 1.1) + 0.15 * pulse;
  #endif
    vec2 dd = uv - 0.5;
    uv = 0.5 + dd * (1.0 - warp * (0.5 - dot(dd, dd)));
#endif
    uv = clamp(uv, vec2(0.0005), vec2(0.9995));

    // ---- chromatic aberration ----------------------------------------------------------
    float ca = 0.0016 * CHROMATIC_ABERRATION;
#ifdef WORLD_KAKURIYO
    ca += CHROMATIC_ABERRATION * (smoothstep(0.50, 1.0, F) * 0.0065 + D * 0.0080);
    ca += glitch * 0.012;
#endif
    vec3 color = ca > 0.00005 ? sceneWithAberration(uv, ca) : texture(colortex0, uv).rgb;

#ifdef WORLD_KAKURIYO
    // ---- tunnel vision: in a chase (and in the Darkness) the edges smear ---------------
    float smear = D * smoothstep(0.22, 0.95, r) + darknessFactor * 0.6 * smoothstep(0.30, 1.0, r);
    if (smear > 0.01) color = mix(color, textureLod(colortex0, uv, 3.0).rgb, saturate(smear));
#endif

    // ---- bloom -------------------------------------------------------------------------
    vec3 bloom = texture(colortex3, uv).rgb * 0.65 + textureLod(colortex3, uv, 3.0).rgb * 0.55;
    color += bloom * (BLOOM_STRENGTH * 0.42);

    // ---- exposure ----------------------------------------------------------------------
    float exposure = BRIGHTNESS;
#ifdef WORLD_OVERWORLD
    exposure *= mix(1.7, 1.0, dayFactor());
#endif
    // vanilla Darkness effect: the picture sinks into black with its pulse
    exposure *= 1.0 - 0.92 * saturate(darknessFactor * 0.55 + darknessLightFactor * 1.5);
    exposure *= 1.0 - 0.6 * blindness;
    color = min(color * exposure, vec3(64.0));

    color = toSrgb(toneCurve(color));
    color = grade(color, F);

    float vStrength = VIGNETTE * 0.35;
    float vInner = 0.50;
#ifdef WORLD_KAKURIYO
    // ---- danger: the picture pulses red -------------------------------------------------
    if (D > 0.0) {
  #ifdef FLASH_REDUCTION
        float p2 = 0.5;
  #else
        float p2 = 0.5 + 0.5 * sin(t * TAU * 1.6);
        p2 *= p2;
  #endif
        vec3 red = color * vec3(1.25, 0.36, 0.32) + vec3(0.030, 0.0, 0.004);
        color = mix(color, red, D * (0.30 + 0.30 * p2));
        color = mix(color, vec3(0.16, 0.0, 0.008), D * smoothstep(0.55, 1.15, r) * (0.35 + 0.30 * p2));
    }
    // ---- vignette that closes in with fear and beats with the heart --------------------
    vStrength = VIGNETTE * (0.45 + 0.55 * F + 0.25 * D + 0.60 * darknessFactor);
    vInner = 0.48 - 0.26 * F - pulse * 0.07 * (0.3 + F);
    color *= 1.0 - glitch * 0.22;
#endif
    float vig = smoothstep(vInner, vInner + 0.85, r);
    color *= 1.0 - saturate(vig * vStrength);

    // ---- film grain: stronger in the dark and with fear --------------------------------
    float seed = float(frameCounter % 1024);
    float n = hash12(gl_FragCoord.xy + seed * vec2(13.37, 7.91)) + hash12(gl_FragCoord.xy * 1.31 + seed * vec2(3.17, 11.31)) - 1.0;
    float grain = FILM_GRAIN * (0.018 + 0.045 * F + 0.015 * D);
#ifdef FLASH_REDUCTION
    grain *= 0.6;
#endif
    color += n * grain * (1.0 - 0.6 * luma(color));

    color += (bayer8(gl_FragCoord.xy) - 0.5) / 255.0;
    if (any(isnan(color))) color = vec3(0.0);
    gl_FragData[0] = vec4(saturate(color), 1.0);
}
#endif
