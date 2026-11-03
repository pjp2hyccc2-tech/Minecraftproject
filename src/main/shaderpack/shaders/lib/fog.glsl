// =====================================================================================
//  ReisatsuShaders - fog
//   fogDensity()      extinction per block. HIDAMARI: outside a blue-grey haze and a low,
//                     drifting mist over the wet lot and the fields (rain just stopped);
//                     inside (under a roof: the emitter map's cover tile) a little dust
//   fogMarch()        the view ray through the air: ambient fog light, every lamp's glow in
//                     the mist (analytic in-scatter of the light table), the flash
//   beamMarch()       the flashlight's cone clipped to the ray: the visible beam, dust motes
// =====================================================================================

// 1 = the point is under the open sky (outside), 0 = under a roof
float airOutside(vec3 wp) {
#ifdef WORLD_HIDAMARI
    return openSky(wp);
#else
    return 1.0;
#endif
}

// extinction per block. noiseAmt 0 = smooth (cheap), 1 = full drifting wisps
float fogDensityO(vec3 wp, float noiseAmt, float outside) {
#if defined WORLD_HIDAMARI
    // the mist hugs the wet ground (the lot is at y 65), thins out above the roofs
    float h = wp.y - 65.0;
    float mist = 0.060 * exp(-max(h, 0.0) * 0.42) * smoothstep(-6.0, -0.5, h + 0.5);
    float haze = 0.0125;
    float n = 1.0;
  #ifdef FOG_NOISE
    if (noiseAmt > 0.0) {
        vec3 wind = vec3(0.16, 0.01, 0.07) * frameTimeCounter;     // creeping across the lot
        vec3 q = (wp + wind) * vec3(0.10, 0.24, 0.10);
        float v = tnoise3(q) * 0.62 + tnoise3(q * 2.37 + vec3(17.0, 3.0, 11.0)) * 0.38;
        n = mix(1.0, 0.15 + 1.70 * smoothstep(0.25, 0.85, v), noiseAmt);
    }
  #endif
    float outdoor = (haze + mist * n) * (1.0 + 0.25 * fear() + 0.20 * danger());
    float indoor = 0.0040 * (0.6 + 0.4 * n);               // dust in the closed restaurant
    return mix(indoor, outdoor, outside) * FOG_DENSITY;
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
float fogDensity(vec3 wp, float noiseAmt) {
    return fogDensityO(wp, noiseAmt, airOutside(wp));
}

// light the air scatters toward the eye without the lamps: the cloud dome and the town glow
// outside, almost nothing inside. Radiance an infinitely deep fog bank converges to.
vec3 fogAmbientLightO(vec3 rd, float outside) {
#if defined WORLD_HIDAMARI
    return mix(FOG_INDOOR, fogColorFor(rd), outside);
#else
    return fogColorFor(rd);
#endif
}
vec3 fogAmbientLight(vec3 rd) {
    return fogAmbientLightO(rd, airOutside(cameraPosition));
}

// vanilla Darkness (being caught) - and Blindness outside the restaurant, where it is
// still the vanilla effect. In HIDAMARI blindness is the flash: never close in for it.
float closeInTransmittance(float dist) {
#ifdef WORLD_HIDAMARI
    float closeIn = darknessFactor * 0.85;
#else
    float closeIn = max(darknessFactor * 0.85, blindness);
#endif
    if (closeIn <= 0.0) return 1.0;
    float xr = dist / mix(64.0, 3.5, closeIn);
    return mix(1.0, exp(-xr * xr), closeIn);
}

// the edge of the loaded world always fades out
float farFade(float dist) {
    return 1.0 - smoothstep(far * 0.72, far * 0.98, dist);
}

// in-scatter of a point light (the flash, a held torch) on the segment [t0, t1] of the view
// ray: closed form of its 1 / (d^2 + r^2) falloff
vec3 pointLightInScatter(HandLight L, vec3 rd, float t0, float t1, float sigma) {
    float tc = dot(L.pos, rd);
    float h2 = max(dot(L.pos, L.pos) - tc * tc, 0.0);
    float r2 = L.type == LIGHT_FLASH ? 0.06 : L.halfDist * L.halfDist;
    float h = sqrt(h2 + r2);
    float I = (atan((min(t1, L.range) - tc) / h) - atan((t0 - tc) / h)) / h;
    float k = L.type == LIGHT_FLASH ? 1.0 : r2;
    return L.color * max(I, 0.0) * k * sigma * L.fog * 0.10;
}

// cheap analytic fog (no volumetrics): rgb in-scatter, a transmittance
vec4 fogAnalytic(vec3 rd, float dist) {
    float d = min(dist, 4096.0);
    vec3 mid = cameraPosition + rd * min(d, 12.0) * 0.5;
    float outside = airOutside(mid);
    float sigma = fogDensityO(mid, 0.0, outside);
    float T = exp(-sigma * d) * farFade(d) * closeInTransmittance(d);
    vec3 S = fogAmbientLightO(rd, outside) * (1.0 - T);
#ifdef EMITTERS_ON
    S += lampsInScatter(rd, 0.0, min(d, 40.0), sigma, 1.0 - outside) * 0.08;
#endif
    for (int i = 0; i < HAND_LIGHTS; i++) {
        HandLight L = handLight(i);
        if (L.type != LIGHT_POINT && L.type != LIGHT_FLASH) continue;
        S += pointLightInScatter(L, rd, 0.0, min(d, L.range), sigma);
    }
    return vec4(S, T);
}

#ifdef VOLUMETRICS_ON
// ---- the main march ----------------------------------------------------------------------
// rd: unit view direction (player space), dist: distance to the surface (large for sky)
// returns rgb in-scattered light, a transmittance
vec4 fogMarch(vec3 rd, float dist, float dither) {
#if defined WORLD_HIDAMARI
    const float FAR_MARCH = 56.0;
#elif defined WORLD_OVERWORLD
    const float FAR_MARCH = 96.0;
#else
    const float FAR_MARCH = 48.0;
#endif
    float marchEnd = min(dist, FAR_MARCH);

#ifdef LIGHT_SHAFTS_ON
    vec3 Lw = lightDirW();
    float vdl = dot(rd, Lw);
    float phase = mix(1.0, phaseHG(vdl, 0.55) * 4.0 * PI, 0.65);
    vec3 shaftLight = celestialLight() * phase * mix(0.25, 0.08, dayFactor()) * (1.0 + rainAmount());
#endif

    // point lights carried by the player (the flash, a torch)
    HandLight P0 = handLight(0);
    HandLight P1 = handLight(1);
    HandLight P2 = handLight(2);
    bool pt0 = P0.type == LIGHT_POINT;
    bool pt1 = P1.type == LIGHT_POINT;
    bool pt2 = P2.type == LIGHT_FLASH;

    vec3 S = vec3(0.0);
    float T = 1.0;
    const float N = float(VOLUMETRIC_SAMPLES);
    float tPrev = 0.0;
    float lastOutside = 1.0;
    for (int i = 0; i < VOLUMETRIC_SAMPLES; i++) {
        float x1 = (float(i) + 1.0) / N;
        float tNext = marchEnd * pow(x1, 1.7);
        float t = mix(tPrev, tNext, dither);
        float ds = tNext - tPrev;
        vec3 p = rd * t;
        vec3 wp = p + cameraPosition;
        float outside = airOutside(wp);
        lastOutside = outside;
        float sigma = fogDensityO(wp, 1.0, outside);
        vec3 L = fogAmbientLightO(rd, outside);
#ifdef LIGHT_SHAFTS_ON
        L += shaftLight * shadowTap(p);
#endif
        float st = exp(-sigma * ds);
        S += T * L * (1.0 - st);
        // light that the lamps / the flash scatter on this segment (already integrated)
        vec3 seg = vec3(0.0);
#ifdef EMITTERS_ON
        seg += lampsInScatter(rd, tPrev, tNext, sigma, 1.0 - outside) * 0.08;
#endif
        if (pt0) seg += pointLightInScatter(P0, rd, tPrev, tNext, sigma);
        if (pt1) seg += pointLightInScatter(P1, rd, tPrev, tNext, sigma);
        if (pt2) seg += pointLightInScatter(P2, rd, tPrev, tNext, sigma);
        S += T * seg * (0.5 + 0.5 * st);
        T *= st;
        tPrev = tNext;
    }
    vec3 amb = fogAmbientLightO(rd, lastOutside);
    // beyond the march: smooth fog
    if (dist > marchEnd) {
        float rest = min(dist, 4096.0) - marchEnd;
        vec3 q = cameraPosition + rd * (marchEnd + min(rest, 32.0) * 0.5);
        float sigma = fogDensityO(q, 0.0, lastOutside);
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
            if (lowFwd && highFwd) { t0 = tl; t1 = th; }
            else if (highFwd) { t0 = th; t1 = tMax; }
            else if (lowFwd) { t0 = 0.0; t1 = tl; }
            else return vec2(0.0);
        } else {
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
// dust floating in the beam (the closed restaurant) / fine droplets (the wet air outside):
// one speck per lattice cell, gently bobbing, visited with a 3D DDA along the ray
vec3 dustMotes(HandLight L, vec3 rd, float tA, float tB, float pixelAngle) {
    const float CELL = 0.55;
    tA = max(tA, 0.30);
    tB = min(tB, 6.5);
    if (tB <= tA) return vec3(0.0);
    float indoor = 1.0 - airOutside(cameraPosition);
    float fill = mix(0.06, 0.26, indoor);            // fraction of cells holding a speck
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
    return sum * mix(0.05, 0.12, indoor);
}
#endif

// what the air sees of a spot light: the near beam dominates (a cone coming out of the lens,
// low and to the side), plus a fainter shaft reaching out toward the hot spot
vec3 beamFogReach(HandLight L, vec3 P, out float dist) {
    vec3 d = P - L.pos;
    dist = length(d);
    if (dist > L.range) return vec3(0.0);
    float prof = spotProfileD(L, d / max(dist, 1e-4), false);
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
#ifdef WORLD_HIDAMARI
        // indoors the beam still shows in the dust (thin air, but a bright lamp)
        sigma = max(sigma, 0.0065 * FOG_DENSITY);
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
// motes of every hand spot light for one pixel (after the fog blur, so they stay pin-sharp)
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
