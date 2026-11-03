// =====================================================================================
//  ReisatsuShaders - final: lens, bloom, exposure, tone curve, grading and - in the
//  restaurant - everything the mod's channels do (docs/DESIGN.md section 9):
//    fear   : heartbeat-pulsed vignette (72 -> 180 bpm), tunnel vision (the periphery
//             smears and sinks), chromatic aberration, desaturation, and at high fear faint
//             faces in the peripheral dark (pareidolia)
//    danger : the image breathes (barrel pulse), the edges warp, deep shadows go red,
//             heavier grain, double vision
//    finder : (reishi) the picture through an old camera's finder: cool, a little desaturated,
//             heavy fine grain, an optical vignette, slight barrel distortion, focus
//             breathing, halation round the lights, the spirits glowing with a short
//             afterimage (colortex9)
//    flash  : lit in the scene (deferred / forward / the air); nothing here ever darkens
//    caught : vanilla Darkness crushes everything to black, the centre last
//  FLASH_REDUCTION removes the pulsing, the breathing and the jitter.
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

const bool colortex0MipmapEnabled = true;   // tunnel vision blur, focus breathing
const bool colortex3MipmapEnabled = true;   // wide bloom, halation

// one heartbeat: "lub" then a softer "dub"
float beat(float t, float bpm) {
    float x = fract(t * bpm / 60.0);
    return exp(-sq((x - 0.05) * 20.0)) + 0.65 * exp(-sq((x - 0.24) * 24.0));
}
// the rate follows fear (72 bpm calm .. 180 bpm panic). A handful of fixed rates are
// cross-faded: a rate that follows fear directly would make the phase jump every frame.
float heartbeat(float f) {
    float t = frameTimeCounter;
    float bpm = 72.0 + 108.0 * f;
    float sum = 0.0;
    float wsum = 0.0;
    for (int i = 0; i < 5; i++) {
        float r = 72.0 + 27.0 * float(i);            // 72 99 126 153 180
        float w = max(1.0 - abs(bpm - r) / 27.0, 0.0);
        sum += beat(t, r) * w;
        wsum += w;
    }
    return sum / max(wsum, 1e-3);
}

vec3 sampleScene(vec2 uv) {
    return texture(colortex0, clamp(uv, vec2(0.0005), vec2(0.9995))).rgb;
}
vec3 sampleSceneLod(vec2 uv, float lod) {
    return textureLod(colortex0, clamp(uv, vec2(0.0005), vec2(0.9995)), lod).rgb;
}
vec3 sceneWithAberration(vec2 uv, float ca, float lod) {
    vec2 d = (uv - 0.5) * ca;
    return vec3(sampleSceneLod(uv + d, lod).r, sampleSceneLod(uv, lod).g, sampleSceneLod(uv - d, lod).b);
}

// filmic curve (ACES fit): deep toe, soft shoulder. In the restaurant the toe is lifted a little
// (dark-adapted eyes): the deepest shadows keep their shapes - the booths and pillars of the dark
// dining hall stay barely readable - while the mid-tones and the lights stay as they were
vec3 toneCurve(vec3 x) {
    x = max(x, vec3(0.0));
    vec3 y = (x * (2.51 * x + 0.03)) / (x * (2.43 * x + 0.59) + 0.14);
#ifdef WORLD_HIDAMARI
    y += 0.30 * x * exp(-x / 0.03);
#endif
    return saturate(y);
}

vec3 grade(vec3 c, float F, float D, float fm) {
#if defined WORLD_HIDAMARI
    // cold and dark; the lamps keep their colour (the green exit signs, the sodium orange);
    // fear drains the colour, danger drags the deep shadows toward red
    float sat = 0.86 * (1.0 - 0.60 * F) * mix(1.0, 0.62, fm);
    vec3 shadowTint = mix(vec3(0.90, 0.97, 1.12), vec3(1.34, 0.68, 0.62), saturate(D * 0.75));
    shadowTint = mix(shadowTint, vec3(0.86, 0.98, 1.16), fm * (1.0 - D * 0.5));
    float contrast = 1.06 + 0.08 * F + 0.04 * fm;
#elif defined WORLD_OVERWORLD
    float sat = 0.68 * (1.0 - 0.25 * rainAmount());
    vec3 shadowTint = vec3(0.93, 0.98, 1.08);
    float contrast = 1.04;
#elif defined WORLD_NETHER
    float sat = 0.72;
    vec3 shadowTint = vec3(1.04, 0.97, 0.95);
    float contrast = 1.05;
#else
    float sat = 0.66;
    vec3 shadowTint = vec3(0.98, 0.95, 1.06);
    float contrast = 1.05;
#endif
    float l = luma(c);
    // lamp colours (exit-sign green, sodium, the red LEDs) keep some colour even in fear
    float keep = smoothstep(0.55, 0.92, chroma(c)) * smoothstep(0.05, 0.18, maxOf(c));
    c = mix(vec3(l), c, mix(sat, max(sat, 0.92), keep * (1.0 - 0.5 * fm)));
#if defined WORLD_HIDAMARI
    c *= mix(shadowTint, vec3(1.0), smoothstep(0.0, mix(0.42, 0.24, D), l));
    // the finder: a cool cast over everything, not only the shadows
    c *= mix(vec3(1.0), vec3(0.90, 0.98, 1.06), fm);
#else
    c *= mix(shadowTint, vec3(1.0), smoothstep(0.0, 0.42, l));
#endif
    // contrast about 0.40 that keeps black at black: a straight line here clipped everything
    // under ~6/255 to zero and flattened the dark rooms (the booths and pillars must stay
    // barely readable against the dark)
    c = max(c, vec3(0.0));
    vec3 below = 0.40 * pow(c / 0.40, vec3(contrast));
    vec3 above = (c - 0.40) * contrast + 0.40;
    return mix(below, above, step(0.40, c));
}

// the floor of the darkness (added after the vignette: the corners never go pure black)
vec3 liftColor(float D) {
#if defined WORLD_HIDAMARI
    return mix(vec3(0.011, 0.013, 0.017), vec3(0.018, 0.009, 0.009), D);
#elif defined WORLD_OVERWORLD
    return vec3(0.008, 0.009, 0.012);
#elif defined WORLD_NETHER
    return vec3(0.010, 0.007, 0.006);
#else
    return vec3(0.008, 0.007, 0.010);
#endif
}

#if defined WORLD_HIDAMARI && defined PAREIDOLIA
// a face in the dark: a pale oval, two dark sockets, an open mouth, all warped by noise.
// > 0 paler "skin", < 0 dark holes. p in face units (oval radius ~1).
float faceShape(vec2 p, float seed, float t) {
    vec2 w = p + (vec2(vnoise2(p * 1.6 + seed + t * 0.05), vnoise2(p * 1.6 + seed + 5.3 - t * 0.04)) - 0.5) * 0.55;
    w += (vec2(vnoise2(p * 4.1 + seed * 1.3), vnoise2(p * 4.1 + seed * 0.7 + 2.0)) - 0.5) * 0.12;
    float head = 1.0 - smoothstep(0.70, 1.05, length(w * vec2(1.0, 0.76)));
    float eyeL = 1.0 - smoothstep(0.09, 0.23, length((w - vec2(-0.34, 0.16)) * vec2(1.0, 1.30)));
    float eyeR = 1.0 - smoothstep(0.09, 0.23, length((w - vec2(0.33, 0.19)) * vec2(1.0, 1.30)));
    float mouth = 1.0 - smoothstep(0.07, 0.22, length((w - vec2(0.02, -0.42)) * vec2(1.30, 0.62)));
    return head * (1.0 - eyeL - eyeR - mouth * 0.9) - (eyeL + eyeR + mouth) * 0.25;
}
// faces emerging from the peripheral darkness, fading in and out over many seconds
float pareidolia(vec2 q, float aspect, float t) {
    float sum = 0.0;
    for (int i = 0; i < 3; i++) {
        float fi = float(i);
        float period = 11.0 + 3.7 * fi;
        float cyc = (t + fi * 5.1) / period;
        float k = floor(cyc);
        float ph = fract(cyc);
        float env = smoothstep(0.05, 0.40, ph) * (1.0 - smoothstep(0.62, 0.95, ph));
        if (env <= 0.0) continue;
        float h1 = hash12(vec2(k, fi * 7.3 + 1.0));
        float h2 = hash12(vec2(k * 1.7, fi * 3.1 + 9.0));
        float a = h1 * TAU;
        vec2 c = vec2(cos(a) * (0.30 + 0.12 * h2) * aspect, sin(a) * (0.31 + 0.10 * h2));
        c += vec2(sin(t * 0.21 + fi), cos(t * 0.17 + fi * 2.0)) * 0.012;      // drifting, never still
        float scale = (0.075 + 0.05 * h2) * (1.0 + 0.04 * sin(t * 0.7 + fi));
        vec2 p = (q - c) / scale;
        if (dot(p, p) > 2.0) continue;
        sum += faceShape(p * vec2(1.0, -1.0), k * 3.1 + fi * 17.0, t) * env;
    }
    return sum;
}
#endif

void main() {
    vec2 uv = texcoord;
    float F = fear() * FEAR_EFFECTS;
    float D = danger() * FEAR_EFFECTS;
    float t = frameTimeCounter;
    float fm = finderMode();
    float C = caught();

    vec2 q = (texcoord - 0.5) * vec2(aspectRatio, 1.0);
    float r = length(q) / length(vec2(aspectRatio, 1.0) * 0.5);    // 0 centre .. 1 corner

    float pulse = 0.0;
    float lod = 0.0;
#ifdef WORLD_HIDAMARI
  #ifndef FLASH_REDUCTION
    pulse = heartbeat(fear()) * smoothstep(0.05, 0.40, F + D);
  #endif
    // ---- danger: the image breathes (barrel pulse), the edges warp ---------------------
    float breath = D * 0.050;
  #ifndef FLASH_REDUCTION
    breath *= 0.55 + 0.45 * sin(t * TAU * 0.27) + 0.25 * pulse;
  #endif
    // ---- fear: the middle swells a little at high fear --------------------------------
    breath += smoothstep(0.55, 1.0, F) * 0.035;
    vec2 dd = uv - 0.5;
    uv = 0.5 + dd * (1.0 - breath * (0.5 - dot(dd, dd)));
    if (D > 0.0) {
        float edge = smoothstep(0.35, 1.0, r);
        vec2 n = vec2(vnoise2(uv * 4.0 + vec2(t * 0.35, 0.0)), vnoise2(uv * 4.0 + vec2(7.1, -t * 0.30))) - 0.5;
        uv += n * edge * D * 0.014;
    }
    // ---- the finder: barrel distortion, focus breathing --------------------------------
    if (fm > 0.5) {
        // normalised so the corners stay inside the picture (the photo is grabbed from it)
        vec2 c = uv - 0.5;
        vec2 ca2 = c * vec2(aspectRatio, 1.0);
        float rMax2 = 0.25 * (aspectRatio * aspectRatio + 1.0);
        uv = 0.5 + c * (1.0 + 0.085 * dot(ca2, ca2)) / (1.0 + 0.085 * rMax2 + 0.004);
        float hunt = 0.0;
    #ifndef FLASH_REDUCTION
        // an old lens hunting for focus: the picture swells and softens, settles, swells
        hunt = sin(t * 1.15) * 0.6 + sin(t * 0.47 + 1.3) * 0.4;
        uv = 0.5 + (uv - 0.5) * (1.0 - 0.0045 * hunt);
    #endif
        lod = 0.25 + 0.55 * smoothstep(0.2, 1.0, abs(hunt)) + 0.6 * smoothstep(0.55, 1.0, r);
    }
#endif
    bool outside = uv.x < 0.0 || uv.x > 1.0 || uv.y < 0.0 || uv.y > 1.0;
    uv = clamp(uv, vec2(0.0005), vec2(0.9995));

    // ---- chromatic aberration ----------------------------------------------------------
    float ca = 0.0012 * CHROMATIC_ABERRATION;
#ifdef WORLD_HIDAMARI
    ca += CHROMATIC_ABERRATION * (smoothstep(0.35, 1.0, F) * 0.0070 + D * 0.0060 + fm * 0.0030 * r);
#endif
    vec3 color = ca > 0.00005 ? sceneWithAberration(uv, ca, lod) : sampleSceneLod(uv, lod);

#ifdef WORLD_HIDAMARI
    // ---- danger: double vision -----------------------------------------------------------
    if (D > 0.02) {
        vec2 off = vec2(0.011, 0.0035) * D;
  #ifndef FLASH_REDUCTION
        off *= 0.65 + 0.35 * sin(t * 0.9);
  #endif
        color = mix(color, sampleSceneLod(uv + off, lod), 0.32 * D);
    }
    // ---- fear: tunnel vision - the periphery smears and sinks ---------------------------
    float tunnel = smoothstep(0.30, 1.0, F) * smoothstep(0.25, 0.95, r) + C * 0.8 * smoothstep(0.10, 0.7, r);
    if (tunnel > 0.01) {
        vec3 blurred = textureLod(colortex0, uv, 1.0 + 3.0 * saturate(tunnel)).rgb;
        color = mix(color, blurred, saturate(tunnel * 1.3));
    }
#endif

    // ---- bloom (and in the finder: halation, a warm fringe round every light) -------------
    vec3 b0 = texture(colortex3, uv).rgb;
    vec3 b1 = textureLod(colortex3, uv, 3.0).rgb;
    vec3 bloom = b0 * 0.65 + b1 * 0.55;
    color += bloom * (BLOOM_STRENGTH * 0.45);
#ifdef WORLD_HIDAMARI
    if (fm > 0.5) {
        // halation: a wide, warm, soft fringe - around the lights, never over them (the wide
        // blur minus the core: where the light itself is, there is nothing to add)
        vec3 b2 = textureLod(colortex3, uv, 4.5).rgb;
        color += max(b2 - b0 * 0.6, vec3(0.0)) * vec3(1.0, 0.52, 0.40) * 0.16 * BLOOM_STRENGTH;
    }
  #ifdef AFTERIMAGE_ON
    // the spirits' cold glow lingers in the finder: a faint outline where they just were
    if (fm > 0.5) {
        vec3 trail = texture(colortex9, uv).rgb;
        float here = texture(colortex8, uv).g;
        color += max(trail - vec3(0.55, 0.80, 1.00) * here, vec3(0.0)) * 0.09;
    }
  #endif
#endif

    // ---- exposure ----------------------------------------------------------------------
    float exposure = BRIGHTNESS;
#ifdef WORLD_OVERWORLD
    exposure *= mix(1.7, 1.0, dayFactor());
#endif
#ifdef WORLD_HIDAMARI
    // the eyes open in the dark restaurant, close a little under the sign and the lamps
    float eyeBlock = float(eyeBrightnessSmooth.x) / 240.0;
    exposure *= mix(1.45, 0.95, smoothstep(0.10, 0.80, eyeBlock));
#endif
    exposure *= 1.0 - 0.92 * saturate(darknessFactor * 0.55 + darknessLightFactor * 1.5);
#ifndef WORLD_HIDAMARI
    exposure *= 1.0 - 0.6 * blindness;             // vanilla blindness outside the restaurant
#endif
    color = min(color * exposure, vec3(64.0));
#ifdef WORLD_HIDAMARI
    // the finder: a bright optical finder, the eye pressed to it - it opens up the dark, the
    // lights keep their faces (a boost that fades out over the highlights; still monotonic)
    if (fm > 0.0) {
        float lum = luma(color);
        color *= mix(1.0, mix(1.65, 1.0, smoothstep(0.04, 0.60, lum)), fm);
    }
#endif

    float seed = float(frameCounter % 1024);
    float n1 = hash12(gl_FragCoord.xy + seed * vec2(13.37, 7.91));
    float n2 = hash12(gl_FragCoord.xy * 1.31 + seed * vec2(3.17, 11.31));
    float grainN = n1 + n2 - 1.0;

    color = toSrgb(toneCurve(color));
    color = grade(color, F, D, fm);

    float vStrength = VIGNETTE * 0.35;
    float vInner = 0.50;
#ifdef WORLD_HIDAMARI
    // ---- vignette that closes in with fear and beats with the heart --------------------
    vStrength = VIGNETTE * (0.40 + 0.50 * F + 0.25 * D);
    vInner = 0.50 - 0.22 * F - pulse * 0.075 * (0.35 + F);
    // the darkness does not close in as a clean circle: it creeps, in slow uneven tongues
    vec2 around = q / max(length(q), 1e-4);         // noise on the unit circle: no seam
    float creep = vnoise2(around * 1.6 + vec2(t * 0.11, 3.0)) * 0.6 + vnoise2(around * 3.9 + vec2(7.0, -t * 0.17)) * 0.4;
    vInner -= (creep - 0.5) * 0.16 * smoothstep(0.3, 1.0, F);
    if (fm > 0.5) {
        // the finder's optics: a deep, even fall-off toward the frame
        vStrength = VIGNETTE * (0.72 + 0.25 * F);
        vInner = 0.28 - pulse * 0.03;
    }
#endif
    float vig = smoothstep(vInner, vInner + 0.85, r);
    color *= 1.0 - saturate(vig * vStrength);
    color += liftColor(D) * (1.0 - color) * (1.0 - 0.9 * darknessFactor) * (1.0 - 0.5 * fm);
    if (outside) color *= 0.0;

#ifdef WORLD_HIDAMARI
  #ifdef PAREIDOLIA
    // ---- faces in the peripheral darkness at high fear -----------------------------------
    float faceAmt = smoothstep(0.62, 0.95, fear()) * FEAR_EFFECTS * (1.0 - fm);
    if (faceAmt > 0.0 && !isHandDepth(texture(depthtex0, uv).r)) {   // never on your own hand
        float dark = 1.0 - smoothstep(0.03, 0.16, luma(color));
        float face = pareidolia(q, aspectRatio, t) * faceAmt * dark * smoothstep(0.30, 0.55, r);
        // they come out of the grain: the pale part is half texture, half noise
        float speck = 0.55 + 0.9 * hash12(gl_FragCoord.xy * 0.73 + seed * vec2(1.7, 4.3));
        color = max(color + face * vec3(0.024, 0.025, 0.028) * mix(1.0, speck, step(0.0, face)), vec3(0.0));
        grainN *= 1.0 + 1.4 * max(face, 0.0) / max(faceAmt, 0.05);
    }
  #endif
    // ---- caught: everything goes black, the centre last ------------------------------------
    color *= 1.0 - C * smoothstep(0.55 * (1.0 - C), 0.55 * (1.0 - C) + 0.25, r);
    color *= 1.0 - smoothstep(0.55, 1.0, C);
#endif

    // ---- film grain: stronger in the dark, with fear and danger; heavy in the finder --------
    float grain = FILM_GRAIN * (0.016 + 0.030 * F + 0.034 * D);
#ifdef WORLD_HIDAMARI
    if (fm > 0.5) {
        // fine and heavy: the finder's ground glass and the night
        float g2 = hash12(gl_FragCoord.xy * 0.97 + seed * vec2(7.7, 2.9)) - 0.5;
        grainN = grainN * 0.7 + g2 * 0.9;
        grain = FILM_GRAIN * (0.050 + 0.020 * F + 0.025 * D);
    }
#endif
#ifdef FLASH_REDUCTION
    grain *= 0.6;
#endif
    color += grainN * grain * (1.0 - 0.6 * luma(color));
    // the grain must not dig holes into the floor of the darkness either
    float floorK = 0.5 * (1.0 - 0.9 * darknessFactor);
#ifdef WORLD_HIDAMARI
    floorK *= 1.0 - smoothstep(0.55, 1.0, C);
#endif
    color = max(color, liftColor(D) * floorK);

    color += (bayer8(gl_FragCoord.xy) - 0.5) / 255.0;
    if (any(isnan(color))) color = vec3(0.0);
    gl_FragData[0] = vec4(saturate(color), 1.0);
}
#endif
