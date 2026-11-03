// =====================================================================================
//  HitoriShaders - final: lens, bloom, exposure, tone curve, grading and - in the
//  apartment - everything the mod's channels do (docs/DESIGN.md section 9):
//    fear   : heartbeat-pulsed vignette (72 -> 180 bpm), tunnel vision (the periphery
//             smears and sinks), chromatic aberration, desaturation, the middle swelling, and
//             at high fear faint faces in the peripheral dark (pareidolia)
//    danger : the image breathes (barrel pulse), the edges warp, deep shadows go red,
//             heavier grain, double vision
//    camera : (nightVision) the fixed camera's feed: an action cam's slight fisheye, IR night
//             vision in cold grey-green when its room is dark (dull colour when lit), heavy
//             sensor noise, line jitter, interlaced fields (combing on whatever moves), a slow
//             rolling bar, bloom round the lights, lag; tearing and colour splitting when the
//             doll or the woman is on the picture (and with danger)
//    eyes   : (blindness) the eyes shut: black, faint phosphenes drifting, and the TV's
//             flicker - whatever light there is - glowing red through the eyelids
//    caught : vanilla Darkness crushes everything to black, the centre last
//    closet : inside the oshiire the eyes do not adapt to the dark: the slit of light in the
//             fusuma is the only bright thing
//  FLASH_REDUCTION removes the pulsing, the breathing, the jitter and the tearing.
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

const bool colortex0MipmapEnabled = true;   // tunnel vision blur, the eyelids, the camera's gain
const bool colortex3MipmapEnabled = true;   // wide bloom
const bool colortex8MipmapEnabled = true;   // how much of the doll / the woman a feed shows

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

vec3 sampleSceneLod(vec2 uv, float lod) {
    return textureLod(colortex0, clamp(uv, vec2(0.0005), vec2(0.9995)), lod).rgb;
}
vec3 sceneWithAberration(vec2 uv, float ca, float lod) {
    vec2 d = (uv - 0.5) * ca;
    return vec3(sampleSceneLod(uv + d, lod).r, sampleSceneLod(uv, lod).g, sampleSceneLod(uv - d, lod).b);
}

// filmic curve (ACES fit): deep toe, soft shoulder. In the apartment the toe is lifted a little
// (dark-adapted eyes): the deepest shadows keep their shapes - the furniture of a dark room stays
// barely readable - while the mid-tones and the lights stay as they were
vec3 toneCurve(vec3 x, float toe) {
    x = max(x, vec3(0.0));
    vec3 y = (x * (2.51 * x + 0.03)) / (x * (2.43 * x + 0.59) + 0.14);
    y += toe * x * exp(-x / 0.03);
    return saturate(y);
}

vec3 grade(vec3 c, float F, float D) {
#if defined WORLD_SHIRAKABA
    // a cold, dark night room; the lights keep their colour (the TV's blue-white, the sodium
    // through the curtains, the laptop); fear drains the colour, danger drags the deep
    // shadows toward red
    float sat = 0.82 * (1.0 - 0.60 * F);
    vec3 shadowTint = mix(vec3(0.88, 0.97, 1.12), vec3(1.34, 0.68, 0.62), saturate(D * 0.75));
    float contrast = 1.07 + 0.08 * F;
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
    // lamp colours keep some colour even in fear
    float keep = smoothstep(0.55, 0.92, chroma(c)) * smoothstep(0.05, 0.18, maxOf(c));
    c = mix(vec3(l), c, mix(sat, max(sat, 0.92), keep));
#if defined WORLD_SHIRAKABA
    c *= mix(shadowTint, vec3(1.0), smoothstep(0.0, mix(0.42, 0.24, D), l));
#else
    c *= mix(shadowTint, vec3(1.0), smoothstep(0.0, 0.42, l));
#endif
    // contrast about 0.40 that keeps black at black (a straight line clips the dark rooms)
    c = max(c, vec3(0.0));
    vec3 below = 0.40 * pow(c / 0.40, vec3(contrast));
    vec3 above = (c - 0.40) * contrast + 0.40;
    return mix(below, above, step(0.40, c));
}

// the floor of the darkness (added after the vignette: the corners never go pure black)
vec3 liftColor(float D) {
#if defined WORLD_SHIRAKABA
    return mix(vec3(0.010, 0.012, 0.016), vec3(0.017, 0.009, 0.009), D);
#elif defined WORLD_OVERWORLD
    return vec3(0.008, 0.009, 0.012);
#elif defined WORLD_NETHER
    return vec3(0.010, 0.007, 0.006);
#else
    return vec3(0.008, 0.007, 0.010);
#endif
}

#if defined WORLD_SHIRAKABA && defined PAREIDOLIA
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
        sum += faceShape(p * vec2(1.0, -1.0), k * 3.1 + fi * 17.0, t);
    }
    return sum;
}
#endif

#ifdef WORLD_SHIRAKABA
// ---- the camera app: a fixed camera's feed --------------------------------------------------
// how much of the doll and the woman the feed shows right now (the entity mask, averaged)
float feedPresence() {
    float s = 0.0;
    for (int i = 0; i < 5; i++) {
        vec2 p = i == 0 ? vec2(0.5) : vec2((i & 1) == 0 ? 0.25 : 0.75, i < 3 ? 0.25 : 0.75);
        vec4 m = textureLod(colortex8, p, 7.0);
        s += m.r + m.g * 1.6;
    }
    return saturate(s * 1.1);
}

vec3 cctvFeed(vec2 uv0, float F, float D, out bool outside) {
    float t = frameTimeCounter;
    float frame = float(frameCounter);
    float ir = cctvIrMode();
    // ---- glitches: bursts while the doll / the woman is on the picture, and near danger ----
    float presence = feedPresence();
    float burstSlot = floor(t * 5.0);
    float burst = step(0.62, hash11(burstSlot * 1.37 + 0.5)) * (0.4 + 0.6 * hash11(burstSlot + 7.0));
    float glitch = saturate(presence * 1.2 * (0.25 + 0.75 * burst) + D * 0.45 * burst);
#ifndef CCTV_GLITCH
    glitch *= 0.15;
#endif
#ifdef FLASH_REDUCTION
    glitch *= 0.15;
#endif
    // ---- the action cam's lens: slight fisheye, the corners kept in the picture -----------
    vec2 c = uv0 - 0.5;
    vec2 ca2 = c * vec2(aspectRatio, 1.0);
    float rMax2 = 0.25 * (aspectRatio * aspectRatio + 1.0);
    float k = 0.20 * CCTV_FISHEYE;
    vec2 uv = 0.5 + c * (1.0 + k * dot(ca2, ca2)) / (1.0 + k * rMax2);
    // ---- the picture's lines: 2 px lines, interlaced fields, sync jitter, tearing ---------
    float lineH = max(floor(viewHeight / 540.0 + 0.5), 1.0) * 2.0;
    float line = floor(gl_FragCoord.y / lineH);
    float jitter = (hash12(vec2(line, frame)) - 0.5) * 0.0009;
    // tearing: bands of lines torn sideways
    float bandSeed = floor(t * 9.0);
    float b0 = hash11(bandSeed * 1.9 + 3.0);
    float bandY = fract(uv.y * 1.0 - b0);
    float inBand = step(bandY, 0.06 + 0.10 * hash11(bandSeed + 1.0));
    float tear = inBand * (hash12(vec2(floor(gl_FragCoord.y / (lineH * 3.0)), bandSeed)) - 0.5) * 0.06 * glitch;
    uv.x += jitter + tear;
    // the slow rolling bar of a cheap feed: a band creeping up the picture, a slight warp in it
    float roll = fract(uv.y * 0.9 - t * 0.07);
    float rollBand = exp(-sq((roll - 0.5) * 9.0));
    uv.x += rollBand * 0.0012 * sin(uv.y * 80.0 + t * 3.0);
    outside = uv.x < 0.0 || uv.x > 1.0 || uv.y < 0.0 || uv.y > 1.0;
    uv = clamp(uv, vec2(0.001), vec2(0.999));
    // interlace: every other line comes from the previous field (the last frame): whatever
    // moved shows combed edges
    bool oldField = false;
#ifdef CCTV_INTERLACE
    oldField = mod(line + frame, 2.0) > 0.5;
#endif
    float lod = 0.55;
    vec3 now = textureLod(colortex0, uv, lod).rgb;
    vec3 prev = textureLod(colortex2, uv, 0.0).rgb;
    vec3 col = oldField ? mix(now, prev, 0.85) : now;
    // lag (the sensor's noise reduction): moving things drag a faint ghost
    col = mix(col, prev, 0.18);
    // colour split on glitches
    if (glitch > 0.01) {
        vec2 sh = vec2(0.006 * glitch, 0.0);
        col.r = mix(col.r, textureLod(colortex0, uv + sh, lod).r, 0.8);
        col.b = mix(col.b, textureLod(colortex0, uv - sh, lod).b, 0.8);
    }
    // a cheap camera's sharpening: halos round edges
    vec3 soft = textureLod(colortex0, uv, 2.0).rgb;
    col = max(col + (col - soft) * 0.45, vec3(0.0));
    // bloom: the sensor bleeds round the lights (the TV's screen burns)
    vec3 b0b = texture(colortex3, uv).rgb;
    vec3 b1 = textureLod(colortex3, uv, 3.0).rgb;
    col += (b0b * 0.8 + b1 * 0.9) * (0.55 * BLOOM_STRENGTH);
    // ---- gain: the camera opens up the dark - and the darker, the noisier ------------------
    float avg = luma(textureLod(colortex0, vec2(0.5), 8.0).rgb);
    float gain = clamp(0.11 / max(avg, 1e-4), 1.2, 9.0) * BRIGHTNESS;
    col *= gain;
    float noisy = smoothstep(1.5, 9.0, gain);
    // ---- the sensor: IR monochrome / dull colour --------------------------------------------
    float L = dot(col, vec3(0.40, 0.45, 0.15));
    vec3 mono = vec3(L);
    vec3 dull = mix(vec3(luma(col)), col, 0.55);
    col = mix(dull, mono, ir);
    col = 1.0 - exp(-col * 1.15);                      // soft clip
    col = pow(col, vec3(0.92));
    // ---- noise: grain (heavier in the dark and at high gain), line noise, sparkles ---------
    float n1 = hash12(gl_FragCoord.xy + frame * vec2(13.37, 7.91)) - 0.5;
    float n2 = hash12(gl_FragCoord.xy * 1.31 + frame * vec2(3.17, 11.31)) - 0.5;
    float lum = luma(col);
    float amp = CCTV_NOISE * (0.040 + 0.085 * noisy + 0.05 * glitch) * (1.0 - 0.55 * lum);
    col += (n1 + n2) * amp;
    if (ir < 0.5) col += vec3(n2, -n1, n1 * 0.5) * amp * 0.5;   // chroma noise in colour mode
    col += (hash12(vec2(line, frame * 0.5)) - 0.5) * 0.018 * CCTV_NOISE;
    float spark = step(0.99985 - 0.0002 * glitch, hash12(gl_FragCoord.xy * 0.77 + frame * vec2(1.7, 9.3)));
    col += spark * 0.30;
    // a few noise blocks on the hardest glitches
    if (glitch > 0.6) {
        vec2 blk = floor(gl_FragCoord.xy / vec2(32.0, lineH * 4.0));
        float on = step(0.985, hash12(blk + bandSeed * 3.1)) * step(0.75, burst);
        col = mix(col, vec3(hash12(gl_FragCoord.xy + frame)), on * 0.55);
    }
    // ---- scanlines, the rolling bar's glow ---------------------------------------------------
#ifdef CCTV_SCANLINES
    float sl = fract(gl_FragCoord.y / lineH);
    col *= 0.90 + 0.10 * smoothstep(0.0, 0.5, sl) * (1.0 - smoothstep(0.5, 1.0, sl)) * 2.0 - 0.05;
#endif
    col *= 1.0 + 0.045 * rollBand;
    // ---- the lens: a deep vignette, the IR illuminator's hot centre --------------------------
    float r = length(ca2) / sqrt(rMax2);
    col *= 1.0 - 0.55 * smoothstep(0.35, 1.05, r);
    // ---- night vision's cold grey-green; colour mode a little green-cyan ----------------------
    vec3 irTint = vec3(0.80, 1.00, 0.88);
    col = mix(col * vec3(0.94, 1.0, 0.97), col * irTint, ir);
    col = mix(col, vec3(luma(col)), 0.25 * F);         // fear drains it further
    return max(col, vec3(0.0));
}

// ---- eyes closed ----------------------------------------------------------------------------
// the eyelids: black, eigengrau, slowly drifting phosphenes - and the light of the room
// (the TV's flicker) glowing red through the skin
vec3 eyelids(vec2 q, float exposure) {
    float t = frameTimeCounter;
    vec3 avg = textureLod(colortex0, vec2(0.5), 7.0).rgb * 0.6 + textureLod(colortex0, vec2(0.5), 9.0).rgb * 0.4;
    float through = luma(avg) * exposure;
    // blood-red transmission (a few per cent, almost only red), brighter toward the middle of the lid
    vec3 lid = vec3(1.0, 0.085, 0.040) * (through / (1.0 + through * 1.5)) * 0.16 * (1.0 - 0.45 * dot(q, q));
    vec3 c = toSrgb(toneCurve(lid, 0.0));
    c += vec3(0.010, 0.009, 0.012);                      // eigengrau
#ifdef PHOSPHENES
    // phosphenes: soft blotches drifting, swelling, fading - violet, green, a dull gold
    vec2 p = q * 2.6;
    float n1 = vnoise2(p + vec2(t * 0.06, -t * 0.04));
    float n2 = vnoise2(p * 1.9 + vec2(-t * 0.05, t * 0.07) + 7.3);
    float n3 = vnoise2(p * 0.7 + vec2(t * 0.03, t * 0.02) + 3.1);
    float blob = smoothstep(0.55, 0.85, n1 * 0.6 + n3 * 0.4);
    float ring = smoothstep(0.08, 0.0, abs(n2 - 0.62)) * smoothstep(0.4, 0.7, n3);
    vec3 hue = mix(vec3(0.55, 0.40, 0.85), vec3(0.45, 0.75, 0.50), smoothstep(0.3, 0.7, n2));
    hue = mix(hue, vec3(0.85, 0.70, 0.35), smoothstep(0.6, 0.9, n3));
    c += hue * (blob * 0.024 + ring * 0.014);
    // now and then a spark
    vec2 cell = floor(q * 14.0);
    float h = hash12(cell + floor(t * 1.5) * 0.37);
    float sp = step(0.994, h) * (1.0 - smoothstep(0.0, 0.25, length(fract(q * 14.0) - 0.5)));
    c += vec3(0.6, 0.6, 0.7) * sp * 0.05;
#endif
    return c;
}
#endif

void main() {
    vec2 uv = texcoord;
    float F = fear() * FEAR_EFFECTS;
    float D = danger() * FEAR_EFFECTS;
    float t = frameTimeCounter;
    float C = caught();

    vec2 q = (texcoord - 0.5) * vec2(aspectRatio, 1.0);
    float r = length(q) / length(vec2(aspectRatio, 1.0) * 0.5);    // 0 centre .. 1 corner
    float seed = float(frameCounter % 1024);

#ifdef WORLD_SHIRAKABA
    // ---- the camera app shows a fixed camera's feed ------------------------------------------
    if (cctv() > 0.5) {
        bool outsideFeed;
        vec3 feed = cctvFeed(uv, fear(), danger(), outsideFeed);
        if (outsideFeed) feed = vec3(0.0);
        feed *= 1.0 - smoothstep(0.55, 1.0, C);
        feed += (bayer8(gl_FragCoord.xy) - 0.5) / 255.0;
        if (any(isnan(feed))) feed = vec3(0.0);
        gl_FragData[0] = vec4(saturate(feed), 1.0);
        return;
    }
    float closet = closetAt(cameraPosition);
#endif

    float pulse = 0.0;
    float lod = 0.0;
#ifdef WORLD_SHIRAKABA
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
#endif
    bool outside = uv.x < 0.0 || uv.x > 1.0 || uv.y < 0.0 || uv.y > 1.0;
    uv = clamp(uv, vec2(0.0005), vec2(0.9995));

    // ---- chromatic aberration ----------------------------------------------------------
    float ca = 0.0012 * CHROMATIC_ABERRATION;
#ifdef WORLD_SHIRAKABA
    ca += CHROMATIC_ABERRATION * (smoothstep(0.35, 1.0, F) * 0.0070 + D * 0.0060);
#endif
    vec3 color = ca > 0.00005 ? sceneWithAberration(uv, ca, lod) : sampleSceneLod(uv, lod);

#ifdef WORLD_SHIRAKABA
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

    // ---- bloom --------------------------------------------------------------------------
    vec3 b0 = texture(colortex3, uv).rgb;
    vec3 b1 = textureLod(colortex3, uv, 3.0).rgb;
    vec3 bloom = b0 * 0.65 + b1 * 0.55;
    color += bloom * (BLOOM_STRENGTH * 0.45);

    // ---- exposure ----------------------------------------------------------------------
    float exposure = BRIGHTNESS;
#ifdef WORLD_OVERWORLD
    exposure *= mix(1.7, 1.0, dayFactor());
#endif
#ifdef WORLD_SHIRAKABA
    // the eyes open in the dark apartment, close a little under the lamps; shut in the closet they
    // are wide open - whatever leaks through the fusuma's gap glares (the closet itself is lightless:
    // the vanilla light level in there says nothing, the fusuma may let it through)
    float eyeBlock = float(eyeBrightnessSmooth.x) / 240.0;
    exposure *= mix(mix(1.50, 0.95, smoothstep(0.10, 0.80, eyeBlock)), 2.10, closet);
#endif
    exposure *= 1.0 - 0.92 * saturate(darknessFactor * 0.55 + darknessLightFactor * 1.5);
#ifndef WORLD_SHIRAKABA
    exposure *= 1.0 - 0.6 * blindness;             // vanilla blindness outside the apartment's world
#endif
    color = min(color * exposure, vec3(64.0));

    float n1 = hash12(gl_FragCoord.xy + seed * vec2(13.37, 7.91));
    float n2 = hash12(gl_FragCoord.xy * 1.31 + seed * vec2(3.17, 11.31));
    float grainN = n1 + n2 - 1.0;

#ifdef WORLD_SHIRAKABA
    color = toSrgb(toneCurve(color, 0.30 * (1.0 - 0.7 * closet)));
#else
    color = toSrgb(toneCurve(color, 0.0));
#endif
    color = grade(color, F, D);

    float vStrength = VIGNETTE * 0.35;
    float vInner = 0.50;
#ifdef WORLD_SHIRAKABA
    // ---- vignette that closes in with fear and beats with the heart --------------------
    vStrength = VIGNETTE * (0.40 + 0.50 * F + 0.25 * D + 0.25 * closet);
    vInner = 0.50 - 0.22 * F - pulse * 0.075 * (0.35 + F) - 0.12 * closet;
    // the darkness does not close in as a clean circle: it creeps, in slow uneven tongues
    vec2 around = q / max(length(q), 1e-4);         // noise on the unit circle: no seam
    float creep = vnoise2(around * 1.6 + vec2(t * 0.11, 3.0)) * 0.6 + vnoise2(around * 3.9 + vec2(7.0, -t * 0.17)) * 0.4;
    vInner -= (creep - 0.5) * 0.16 * smoothstep(0.3, 1.0, F);
#endif
    float vig = smoothstep(vInner, vInner + 0.85, r);
    color *= 1.0 - saturate(vig * vStrength);
    vec3 lift = liftColor(D);
#ifdef WORLD_SHIRAKABA
    lift *= 1.0 - 0.6 * closet;
#endif
    color += lift * (1.0 - color) * (1.0 - 0.9 * darknessFactor);
    if (outside) color *= 0.0;

#ifdef WORLD_SHIRAKABA
  #ifdef PAREIDOLIA
    // ---- faces in the peripheral darkness at high fear -----------------------------------
    float faceAmt = smoothstep(0.62, 0.95, fear()) * FEAR_EFFECTS;
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

    // ---- film grain: stronger in the dark, with fear and danger ------------------------------
    float grain = FILM_GRAIN * (0.016 + 0.030 * F + 0.034 * D);
#ifdef FLASH_REDUCTION
    grain *= 0.6;
#endif
    color += grainN * grain * (1.0 - 0.6 * luma(color));
    // the grain must not dig holes into the floor of the darkness either
    float floorK = 0.5 * (1.0 - 0.9 * darknessFactor);
#ifdef WORLD_SHIRAKABA
    floorK *= (1.0 - smoothstep(0.55, 1.0, C)) * (1.0 - 0.6 * closet);
#endif
    color = max(color, liftColor(D) * floorK);

#ifdef WORLD_SHIRAKABA
    // ---- eyes closed: the lids, the red glow of the room through them -------------------------
    float E = eyesClosed();
    if (E > 0.0) {
        vec3 lids = eyelids(q, exposure);
        lids *= 1.0 - smoothstep(0.55, 1.0, C);
        color = mix(color, lids, E);
    }
#endif

    color += (bayer8(gl_FragCoord.xy) - 0.5) / 255.0;
    if (any(isnan(color))) color = vec3(0.0);
    gl_FragData[0] = vec4(saturate(color), 1.0);
}
#endif
