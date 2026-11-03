// =====================================================================================
//  ReisatsuShaders - final: lens, bloom, exposure, tone curve, grading and - in the mountain -
//  everything fear (rainStrength), danger (thunderStrength), the camera and capture do:
//    fear   : heartbeat-pulsed vignette (rate 72 -> 180 bpm), tunnel vision (peripheral
//             blur + darkening), chromatic aberration, desaturation, and at high fear faint
//             faces in the peripheral darkness (pareidolia)
//    danger : the image breathes (barrel pulse), the edges warp, deep shadows go red,
//             heavier grain, double vision
//    camera : green-grey night-vision camcorder: fisheye + CA, sensor gain, foliage glows in
//             infrared, bright noise, blooming lights, scanlines, vignette (HUD draws REC)
//    capture: vanilla Darkness crushes everything but the centre
//  FLASH_REDUCTION removes the pulsing, the jitter and the camera interference.
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

const bool colortex0MipmapEnabled = true;   // tunnel vision blur, darkness
const bool colortex3MipmapEnabled = true;   // wide bloom

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
vec3 sceneWithAberration(vec2 uv, float ca) {
    vec2 d = (uv - 0.5) * ca;
    return vec3(sampleScene(uv + d).r, sampleScene(uv).g, sampleScene(uv - d).b);
}

// filmic curve (ACES fit): deep toe, soft shoulder
vec3 toneCurve(vec3 x) {
    x = max(x, vec3(0.0));
    return saturate((x * (2.51 * x + 0.03)) / (x * (2.43 * x + 0.59) + 0.14));
}

vec3 grade(vec3 c, float F, float D) {
#if defined WORLD_MOUNTAIN
    float sat = 0.80 * (1.0 - 0.62 * F * FEAR_EFFECTS);
    // cold blue-grey dark; danger drags the deep shadows toward red
    vec3 shadowTint = mix(vec3(0.86, 0.96, 1.15), vec3(1.32, 0.70, 0.64), saturate(D * 0.70));
    float contrast = 1.06 + 0.08 * F;
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
    // alarm colours (the pink tape, the orange jacket, the flare, jizo bibs) keep some colour
    float keep = smoothstep(0.58, 0.92, chroma(c)) * smoothstep(0.05, 0.16, maxOf(c));
    c = mix(vec3(l), c, mix(sat, max(sat, 0.95), keep));
#if defined WORLD_MOUNTAIN
    // only the deep shadows take the tint (blue-grey, red in danger); midtones stay neutral
    c *= mix(shadowTint, vec3(1.0), smoothstep(0.0, mix(0.42, 0.22, D), l));
#else
    c *= mix(shadowTint, vec3(1.0), smoothstep(0.0, 0.42, l));
#endif
    c = max((c - 0.40) * contrast + 0.40, vec3(0.0));
    return c;
}

// the floor of the darkness (added after the vignette: the corners never go pure black)
vec3 liftColor(float D) {
#if defined WORLD_MOUNTAIN
    return mix(vec3(0.015, 0.018, 0.024), vec3(0.020, 0.010, 0.010), D);
#elif defined WORLD_OVERWORLD
    return vec3(0.008, 0.009, 0.012);
#elif defined WORLD_NETHER
    return vec3(0.010, 0.007, 0.006);
#else
    return vec3(0.008, 0.007, 0.010);
#endif
}

#if defined WORLD_MOUNTAIN && defined PAREIDOLIA
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
    float cam = cameraMode();

    vec2 q = (texcoord - 0.5) * vec2(aspectRatio, 1.0);
    float r = length(q) / length(vec2(aspectRatio, 1.0) * 0.5);    // 0 centre .. 1 corner

    float pulse = 0.0;
#ifdef WORLD_MOUNTAIN
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
    // ---- camera: wide-angle lens ---------------------------------------------------------
    if (cam > 0.5) {
        vec2 c = uv - 0.5;
        uv = 0.5 + c * (0.93 + 0.10 * dot(c * vec2(aspectRatio, 1.0), c * vec2(aspectRatio, 1.0)));
  #if defined WORLD_MOUNTAIN && !defined FLASH_REDUCTION
        // interference when something hostile is near: torn lines
        float slot = floor(t * 12.0);
        if (D > 0.2 && hash11(slot * 1.37) < D * 0.35) {
            float band = floor(uv.y * 40.0 + hash11(slot) * 13.0);
            uv.x += (hash12(vec2(band, slot)) - 0.5) * 0.04 * step(0.75, hash12(vec2(band * 1.3, slot + 2.0))) * D;
        }
  #endif
    }
    bool outside = uv.x < 0.0 || uv.x > 1.0 || uv.y < 0.0 || uv.y > 1.0;
    uv = clamp(uv, vec2(0.0005), vec2(0.9995));

    // ---- chromatic aberration ----------------------------------------------------------
    float ca = 0.0012 * CHROMATIC_ABERRATION;
#ifdef WORLD_MOUNTAIN
    ca += CHROMATIC_ABERRATION * (smoothstep(0.35, 1.0, F) * 0.0070 + D * 0.0060);
#endif
    ca += cam * 0.0045 * CHROMATIC_ABERRATION * r;
    vec3 color = ca > 0.00005 ? sceneWithAberration(uv, ca) : sampleScene(uv);

#ifdef WORLD_MOUNTAIN
    // ---- danger: double vision -----------------------------------------------------------
    if (D > 0.02) {
        vec2 off = vec2(0.011, 0.0035) * D;
  #ifndef FLASH_REDUCTION
        off *= 0.65 + 0.35 * sin(t * 0.9);
  #endif
        color = mix(color, sampleScene(uv + off), 0.32 * D);
    }
    // ---- fear: tunnel vision - the periphery smears and sinks ---------------------------
    float tunnel = smoothstep(0.30, 1.0, F) * smoothstep(0.25, 0.95, r) + darknessFactor * 0.7 * smoothstep(0.15, 0.8, r);
    if (tunnel > 0.01) {
        vec3 blurred = textureLod(colortex0, uv, 1.0 + 3.0 * saturate(tunnel)).rgb;
        color = mix(color, blurred, saturate(tunnel * 1.3));
    }
#endif

    // ---- bloom -------------------------------------------------------------------------
    vec3 bloom = texture(colortex3, uv).rgb * 0.65 + textureLod(colortex3, uv, 3.0).rgb * 0.55;
    color += bloom * (BLOOM_STRENGTH * mix(0.40, 1.30, cam));

    // ---- exposure ----------------------------------------------------------------------
    float exposure = BRIGHTNESS;
#ifdef WORLD_OVERWORLD
    exposure *= mix(1.7, 1.0, dayFactor());
#endif
#ifdef WORLD_MOUNTAIN
    exposure *= 1.0 + 0.25 * (1.0 - eyeSkyFactor());      // the eyes open a little in the tunnel
#endif
    exposure *= 1.0 - 0.92 * saturate(darknessFactor * 0.55 + darknessLightFactor * 1.5);
    exposure *= 1.0 - 0.6 * blindness;
    color = min(color * exposure, vec3(64.0));

    float seed = float(frameCounter % 1024);
    float n1 = hash12(gl_FragCoord.xy + seed * vec2(13.37, 7.91));
    float n2 = hash12(gl_FragCoord.xy * 1.31 + seed * vec2(3.17, 11.31));
    float grainN = n1 + n2 - 1.0;

    if (cam > 0.5) {
        // ---- the night-vision camcorder -------------------------------------------------
        float fol = texture(colortex5, uv).a;
        float leaf = (fol > 0.25 && fol < 0.75) ? 1.0 : 0.0;       // needles / leaves glow in infrared
        float lum = dot(color, vec3(0.30, 0.52, 0.18)) * (1.0 + 1.4 * leaf);
        lum *= 5.5;                                                // sensor gain
        lum = 1.0 - exp(-lum * 1.6);                               // hard, flat video curve
        // sensor noise: bright, coarse, crawling; worst in the dark
        float nb = hash12(floor(gl_FragCoord.xy * 0.5) + seed * vec2(5.1, 9.7)) - 0.5;
        lum += (grainN * 0.6 + nb * 0.9) * (0.06 + 0.16 * (1.0 - lum)) * max(FILM_GRAIN, 0.4);
        lum += step(0.9985, n1) * 0.5;                             // hot pixels sparkle
        // scanlines and a slow rolling band
        lum *= 0.92 + 0.08 * cos(gl_FragCoord.y * PI);
  #ifndef FLASH_REDUCTION
        lum *= 1.0 + 0.05 * smoothstep(0.92, 1.0, sin(uv.y * 3.0 - t * 1.3));
  #endif
        lum *= 1.0 - smoothstep(0.62, 1.12, r) * 0.85;             // lens vignette
        vec3 nv = mix(vec3(lum), lum * vec3(0.66, 1.0, 0.70), 0.70);
        color = outside ? vec3(0.0) : saturate(nv);
    } else {
        color = toSrgb(toneCurve(color));
        color = grade(color, F, D);
    }

    float vStrength = VIGNETTE * 0.35;
    float vInner = 0.50;
#ifdef WORLD_MOUNTAIN
    // ---- vignette that closes in with fear and beats with the heart --------------------
    vStrength = VIGNETTE * (0.42 + 0.50 * F + 0.25 * D) * (cam > 0.5 ? 0.5 : 1.0);
    vInner = 0.50 - 0.22 * F - pulse * 0.075 * (0.35 + F);
    // the darkness does not close in as a clean circle: it creeps, in slow uneven tongues
    vec2 around = q / max(length(q), 1e-4);         // noise on the unit circle: no seam
    float creep = vnoise2(around * 1.6 + vec2(t * 0.11, 3.0)) * 0.6 + vnoise2(around * 3.9 + vec2(7.0, -t * 0.17)) * 0.4;
    vInner -= (creep - 0.5) * 0.16 * smoothstep(0.3, 1.0, F);
#endif
    float vig = smoothstep(vInner, vInner + 0.85, r);
    color *= 1.0 - saturate(vig * vStrength);
    if (cam < 0.5) color += liftColor(D) * (1.0 - color) * (1.0 - 0.9 * darknessFactor);

#ifdef WORLD_MOUNTAIN
    // ---- capture: everything but the centre is crushed -----------------------------------
    color *= 1.0 - darknessFactor * 0.97 * smoothstep(0.06, 0.50, r);
  #ifdef PAREIDOLIA
    // ---- faces in the peripheral darkness at high fear -----------------------------------
    float faceAmt = smoothstep(0.62, 0.95, fear()) * FEAR_EFFECTS;
    if (faceAmt > 0.0 && cam < 0.5 && !isHandDepth(texture(depthtex0, uv).r)) {   // never on your own hand
        float dark = 1.0 - smoothstep(0.03, 0.16, luma(color));
        float face = pareidolia(q, aspectRatio, t) * faceAmt * dark * smoothstep(0.30, 0.55, r);
        // they come out of the grain: the pale part is half texture, half noise
        float speck = 0.55 + 0.9 * hash12(gl_FragCoord.xy * 0.73 + seed * vec2(1.7, 4.3));
        color = max(color + face * vec3(0.024, 0.025, 0.028) * mix(1.0, speck, step(0.0, face)), vec3(0.0));
        grainN *= 1.0 + 1.4 * max(face, 0.0) / max(faceAmt, 0.05);
    }
  #endif
#endif

    // ---- film grain: stronger in the dark, with fear and danger ------------------------
    if (cam < 0.5) {
        float grain = FILM_GRAIN * (0.016 + 0.030 * F + 0.034 * D);
#ifdef FLASH_REDUCTION
        grain *= 0.6;
#endif
        color += grainN * grain * (1.0 - 0.6 * luma(color));
    }

    color += (bayer8(gl_FragCoord.xy) - 0.5) / 255.0;
    if (any(isnan(color))) color = vec3(0.0);
    gl_FragData[0] = vec4(saturate(color), 1.0);
}
#endif
