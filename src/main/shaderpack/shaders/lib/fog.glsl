// =====================================================================================
//  SounanShaders - fog
//   fogDensity()      extinction per block (mountain: drifting 3D-noise height fog that
//                     pools below the eye - in hollows, gullies, the stream valley)
//   fogMarch()        the view ray through the fog: ambient fog light, moonlight shafts
//                     (shadow map), the flare's red glow, transmittance
//   beamMarch()       a hand light's cone clipped to the ray: the visible beam, dust motes
// =====================================================================================

// extinction per block. noiseAmt 0 = smooth (cheap), 1 = full drifting wisps
float fogDensity(vec3 wp, float noiseAmt) {
#if defined WORLD_MOUNTAIN
    float eyeSky = eyeSkyFactor();
    float feet = cameraPosition.y - 1.62;
    // pools in hollows: thick below the feet, thinning above head height
    float h = min(exp(-clamp(wp.y - feet - 1.5, -14.0, 40.0) * 0.095), 3.4);
    // the mountain itself: lower is mistier
    float alt = mix(1.25, 0.80, saturate((wp.y - 70.0) / 120.0));
    float d = 0.034 * FOG_DENSITY * h * alt * (1.0 + 0.30 * fear() + 0.25 * danger());
    float n = 1.0;
  #ifdef FOG_NOISE
    if (noiseAmt > 0.0) {
        vec3 wind = vec3(0.20, 0.015, 0.42) * frameTimeCounter;   // drifting down the mountain
        vec3 q = (wp + wind) * 0.085;
        float v = tnoise3(q) * 0.62 + tnoise3(q * 2.37 + vec3(17.0, 3.0, 11.0)) * 0.38;
        n = mix(1.0, 0.30 + 1.45 * smoothstep(0.22, 0.86, v), noiseAmt);
    }
  #endif
    // inside the tunnel / a hut: no mist, a little dust
    float dust = 0.010 * FOG_DENSITY * (0.55 + 0.45 * n);
    return mix(dust, d * n, eyeSky);
#elif defined WORLD_OVERWORLD
    float day = dayFactor();
    float k = mix(0.0150, 0.0068, day) * FOG_DENSITY;
    k *= 1.0 + rainAmount() * 2.4;
    k *= mix(0.55, 1.0, eyeSkyFactor());
    float height = exp(-max(wp.y - 63.0, 0.0) * 0.018);
    return k * (0.55 + 0.45 * height);
#elif defined WORLD_NETHER
    return 0.026 * FOG_DENSITY;
#else
    return 0.012 * FOG_DENSITY;
#endif
}

// light the fog scatters toward the eye without any shafts (moonlight diffused through
// the canopy, sky light). Radiance an infinitely deep fog bank converges to.
vec3 fogAmbientLight(vec3 rd) {
#if defined WORLD_MOUNTAIN
    vec3 c = mix(FOG_TUNNEL, FOG_AMBIENT, eyeSkyFactor());
    c *= 1.0 + 0.6 * pow(saturate(dot(rd, lightDirW())), 6.0);      // a little brighter toward the moon
    return c * (1.0 - 0.30 * fear());
#else
    return fogColorFor(rd);
#endif
}

// vanilla Darkness / Blindness: the world shrinks to a few blocks
float closeInTransmittance(float dist) {
    float closeIn = max(darknessFactor * 0.85, blindness);
    if (closeIn <= 0.0) return 1.0;
    float xr = dist / mix(64.0, 3.5, closeIn);
    return mix(1.0, exp(-xr * xr), closeIn);
}

// the edge of the loaded world always fades out (not in the mountain: the fog does it)
float farFade(float dist) {
#ifdef WORLD_MOUNTAIN
    return 1.0;
#else
    return 1.0 - smoothstep(far * 0.72, far * 0.98, dist);
#endif
}

// cheap analytic fog (no volumetrics): rgb in-scatter, a transmittance
vec4 fogAnalytic(vec3 rd, float dist) {
    float d = min(dist, 4096.0);
    float sigma = fogDensity(cameraPosition + rd * min(d, 12.0) * 0.5, 0.0);
#ifdef WORLD_MOUNTAIN
    // the height profile seen along the ray: looking down into a hollow = denser
    sigma = mix(sigma, fogDensity(cameraPosition + rd * min(d, 24.0), 0.0), 0.5);
#endif
    float T = exp(-sigma * d) * farFade(d) * closeInTransmittance(d);
    return vec4(fogAmbientLight(rd) * (1.0 - T), T);
}

#ifdef VOLUMETRICS_ON
// ---- the main march ----------------------------------------------------------------------
// rd: unit view direction (player space), dist: distance to the surface (large for sky)
// returns rgb in-scattered light, a transmittance
vec4 fogMarch(vec3 rd, float dist, float dither) {
#if defined WORLD_MOUNTAIN
    const float FAR_MARCH = 56.0;
#elif defined WORLD_OVERWORLD
    const float FAR_MARCH = 96.0;
#else
    const float FAR_MARCH = 48.0;
#endif
    float marchEnd = min(dist, FAR_MARCH);
    vec3 amb = fogAmbientLight(rd);

#ifdef LIGHT_SHAFTS_ON
    vec3 Lw = lightDirW();
    float vdl = dot(rd, Lw);
    float phase = mix(1.0, phaseHG(vdl, 0.55) * 4.0 * PI, 0.65);
  #if defined WORLD_MOUNTAIN
    vec3 shaftLight = celestialLight() * phase * 0.20 * eyeSkyFactor();
  #else
    vec3 shaftLight = celestialLight() * phase * mix(0.25, 0.08, dayFactor()) * (1.0 + rainAmount());
  #endif
#endif

    // point lights carried by the player (the flare): a glowing sphere of fog around the hand
    HandLight P0 = handLight(0);
    HandLight P1 = handLight(1);
    bool pt0 = P0.type == LIGHT_POINT;
    bool pt1 = P1.type == LIGHT_POINT;

    vec3 S = vec3(0.0);
    float T = 1.0;
    const float N = float(VOLUMETRIC_SAMPLES);
    float tPrev = 0.0;
    for (int i = 0; i < VOLUMETRIC_SAMPLES; i++) {
        float x1 = (float(i) + 1.0) / N;
        float tNext = marchEnd * pow(x1, 1.7);
        float t = mix(tPrev, tNext, dither);
        float ds = tNext - tPrev;
        tPrev = tNext;
        vec3 p = rd * t;
        vec3 wp = p + cameraPosition;
        float sigma = fogDensity(wp, 1.0);
        vec3 L = amb;
#ifdef LIGHT_SHAFTS_ON
        L += shaftLight * shadowTap(p);
#endif
        if (pt0) { vec3 l; float dd; L += handLightReach(P0, p, l, dd) * 0.08 * P0.fog; }
        if (pt1) { vec3 l; float dd; L += handLightReach(P1, p, l, dd) * 0.08 * P1.fog; }
        float st = exp(-sigma * ds);
        S += T * L * (1.0 - st);
        T *= st;
    }
    // beyond the march: smooth fog, no shafts
    if (dist > marchEnd) {
        float rest = min(dist, 4096.0) - marchEnd;
        float sigma = fogDensity(cameraPosition + rd * (marchEnd + min(rest, 32.0) * 0.5), 0.0);
        float st = exp(-sigma * rest);
        S += T * amb * (1.0 - st);
        T *= st;
    }
    float fade = farFade(dist) * closeInTransmittance(dist);
    S = mix(amb, S, fade);           // what fades out becomes fog
    T *= fade;
    return vec4(S, T);
}
#endif

// ---- the visible beam --------------------------------------------------------------------
// distance interval [t0, t1] where the view ray (from the camera, unit rd) is inside the
// light's cone widened to tan(half angle) = tanA. (0, 0) if it misses.
vec2 coneInterval(HandLight L, vec3 rd, float tanA, float tMax) {
    float c2 = 1.0 / (1.0 + tanA * tanA);           // cos^2 of the half angle
    vec3 o = -L.pos;                                // ray origin relative to the apex
    float da = dot(rd, L.dir);
    float oa = dot(o, L.dir);
    float a = da * da - c2;
    float b = 2.0 * (da * oa - c2 * dot(rd, o));
    float c = oa * oa - c2 * dot(o, o);
    float t0 = 0.0;
    float t1 = 0.0;
    if (abs(a) < 1e-5) {
        if (abs(b) < 1e-6) return vec2(0.0);
        float t = -c / b;
        if (oa + t * da < 0.0) return vec2(0.0);
        if (da > 0.0) { t0 = t; t1 = tMax; } else { t0 = 0.0; t1 = t; }
    } else {
        float disc = b * b - 4.0 * a * c;
        if (disc < 0.0) return vec2(0.0);
        float sq_ = sqrt(disc);
        float ta = (-b - sq_) / (2.0 * a);
        float tb = (-b + sq_) / (2.0 * a);
        float tl = min(ta, tb);
        float th = max(ta, tb);
        // keep the forward nappe of the double cone
        bool lowFwd = oa + tl * da >= 0.0;
        bool highFwd = oa + th * da >= 0.0;
        if (a < 0.0) {
            // the ray lies inside the cone between the roots (axis-parallel case)
            if (lowFwd && highFwd) { t0 = tl; t1 = th; }
            else if (highFwd) { t0 = th; t1 = tMax; }
            else if (lowFwd) { t0 = 0.0; t1 = tl; }
            else return vec2(0.0);
        } else {
            // inside outside the roots: (-inf, tl] and [th, inf)
            if (highFwd) { t0 = th; t1 = tMax; }
            else if (lowFwd) { t0 = 0.0; t1 = tl; }
            else return vec2(0.0);
        }
    }
    t0 = max(t0, 0.05);
    t1 = min(t1, tMax);
    return t1 > t0 ? vec2(t0, t1) : vec2(0.0);
}

#ifdef DUST_MOTES
// dust (tunnel, huts) / droplets (forest) floating in the beam: one speck per lattice cell,
// gently bobbing, visited with a 3D DDA along the ray between tA and tB
vec3 dustMotes(HandLight L, vec3 rd, float tA, float tB, float pixelAngle) {
    const float CELL = 0.55;
    tA = max(tA, 0.30);
    tB = min(tB, 6.5);
    if (tB <= tA) return vec3(0.0);
    float indoor = 1.0 - eyeSkyFactor();
    float fill = mix(0.05, 0.22, indoor);            // fraction of cells holding a speck
    float tm = frameTimeCounter;
    vec3 ro = cameraPosition;
    vec3 p = ro + rd * tA;
    vec3 cell = floor(p / CELL);
    vec3 stp = vec3(rd.x >= 0.0 ? 1.0 : -1.0, rd.y >= 0.0 ? 1.0 : -1.0, rd.z >= 0.0 ? 1.0 : -1.0);
    vec3 inv = 1.0 / max(abs(rd), vec3(1e-5));
    vec3 tDelta = CELL * inv;
    vec3 bound = (cell + max(stp, 0.0)) * CELL;
    vec3 tNext = tA + (bound - p) * stp * inv;
    vec3 sum = vec3(0.0);
    for (int i = 0; i < 28; i++) {
        vec3 h = hash33(cell + 0.37);
        if (h.x < fill) {
            vec3 m = (cell + 0.22 + 0.56 * hash33(cell * 1.31 + 7.7)) * CELL;
            m += vec3(sin(tm * 0.31 + h.y * 6.28), sin(tm * 0.23 + h.z * 6.28) * 0.7, sin(tm * 0.27 + h.x * 9.0)) * 0.10;
            vec3 rel = m - ro;
            float along = dot(rel, rd);
            float perp = length(rel - rd * along);
            const float R = 0.006;                    // speck radius in blocks
            float size = max(R, along * pixelAngle * 0.8);
            float spr = exp(-sq(perp / size)) * sq(R / size);
            if (spr > 1e-4) {
                vec3 l;
                float dd;
                vec3 E = handLightReach(L, rel, l, dd);
                float twinkle = 0.45 + 0.55 * sin(tm * (2.0 + 3.0 * h.y) + h.z * 40.0);
                sum += E * spr * twinkle;
            }
        }
        float tn = min(tNext.x, min(tNext.y, tNext.z));
        if (tn > tB) break;
        if (tNext.x <= tNext.y && tNext.x <= tNext.z) { cell.x += stp.x; tNext.x += tDelta.x; }
        else if (tNext.y <= tNext.z) { cell.y += stp.y; tNext.y += tDelta.y; }
        else { cell.z += stp.z; tNext.z += tDelta.z; }
    }
    return sum * mix(0.05, 0.10, indoor);
}
#endif

// what the fog sees of a spot light: the near beam dominates (a cone coming out of the lens,
// low and to the side), plus a fainter shaft reaching out toward the hot spot
vec3 beamFogReach(HandLight L, vec3 P, out float dist) {
    vec3 d = P - L.pos;
    dist = length(d);
    if (dist > L.range) return vec3(0.0);
    float prof = spotProfile(L, d / max(dist, 1e-4));
    float nearF = 1.0 / (1.0 + sq(dist / 1.1));
    float farF = lightFalloff(L, dist);
    return L.color * prof * (nearF * 0.85 + farF * 0.07);
}

// the beam of one spot light along the view ray up to dist: rgb in-scattered light
vec3 beamMarch(HandLight L, vec3 rd, float dist, float dither) {
    if (L.type != LIGHT_SPOT || BEAM_FOG <= 0.0) return vec3(0.0);
    vec2 iv = coneInterval(L, rd, L.tanOuter * 1.45, min(dist, L.range));
    if (iv.y <= iv.x) return vec3(0.0);
    float sigma0 = handLightFogSigma();
    vec3 S = vec3(0.0);
    const int M = BEAM_SAMPLES;
    float len = iv.y - iv.x;
    for (int i = 0; i < M; i++) {
        // denser toward the near end, where the beam is brightest
        float x0 = float(i) / float(M);
        float x1 = float(i + 1) / float(M);
        float ta = iv.x + len * x0 * x0;
        float tb = iv.x + len * x1 * x1;
        float t = mix(ta, tb, dither);
        vec3 p = rd * t;
        float dl;
        vec3 E = beamFogReach(L, p, dl);
        if (maxOf(E) <= 0.0) continue;
#ifdef VOLUMETRICS_ON
        float sigma = fogDensity(p + cameraPosition, 1.0);
  #ifdef FOG_NOISE
        // fine wisps drifting through the beam
        vec3 wq = (p + cameraPosition) * 0.55 + vec3(0.11, 0.05, 0.27) * frameTimeCounter;
        sigma *= 0.50 + 0.90 * smoothstep(0.25, 0.80, tnoise3(wq));
  #endif
#else
        float sigma = fogDensity(p + cameraPosition, 0.0);
#endif
        // light lost on the way out of the lamp and back to the eye
        float T = exp(-sigma0 * (t + dl));
        S += E * sigma * T * (tb - ta);
    }
    S *= 0.40 * L.fog * BEAM_FOG;
    // a dense wisp right at the lens must not blow the picture out: soft limit
    return S / (1.0 + maxOf(S) * 0.8);
}

#ifdef DUST_MOTES
// motes of every hand spot light for one pixel (composite1: after the fog blur, so they stay
// pin-sharp). Faint droplets in the forest, dust in the tunnel and the huts.
vec3 handLightMotes(vec3 rd, float dist) {
    vec3 sum = vec3(0.0);
    float pixelAngle = 2.0 / (gbufferProjection[1][1] * viewHeight);
    for (int i = 0; i < 2; i++) {
        HandLight L = handLight(i);
        if (L.type != LIGHT_SPOT) continue;
        vec2 iv = coneInterval(L, rd, L.tanOuter * 1.1, min(dist - 0.05, 6.5));
        if (iv.y > iv.x) sum += dustMotes(L, rd, iv.x, iv.y, pixelAngle) * L.fog;
    }
    return sum;
}
#endif
