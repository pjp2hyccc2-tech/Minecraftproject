// =====================================================================================
//  HitoriShaders - every lit gbuffers program
//  The stub selects the variant:
//    PROG_TERRAIN  PROG_WATER (translucent terrain)  PROG_BLOCK  PROG_ENTITIES  PROG_HAND
//    PROG_TEXTURED (particles)  PROG_BASIC (lines)  PROG_EYES  PROG_GLINT  PROG_BEACON
//    PROG_DAMAGE  PROG_WEATHER  PROG_CLOUDS        (+ TRANSLUCENT for blended variants)
//  Opaque surfaces are lit forward here (the TV, the lamps, the street lamp, the sky, the
//  rain) and leave albedo, normal and light levels for the deferred pass, which adds the hand
//  lights and the camera's IR illuminator once per pixel. Translucents (windows with rain,
//  frosted glass, puddles, the bath water), particles and the hand light themselves.
//    colortex0  linear HDR colour
//    colortex1  r emissive, g lamp share (screen-space lamp shadows), b class (1 hand, 0.5 cloud), a coverage
//    colortex5  albedo (sRGB) + a: material code (MAT_*) / 255
//    colortex6  octahedral world normal (rg), block light (b), sky light (a)
//    colortex8  entities: r the doll, g the woman, b eye glint, a haze
// =====================================================================================
#include "/lib/header.glsl"

#if (defined PROG_TERRAIN || defined PROG_BLOCK || defined PROG_ENTITIES) && !defined TRANSLUCENT
    #define WRITES_GBUFFER      // opaque: lit by the hand lights / IR in the deferred pass
#endif
#if defined PROG_HAND && !defined TRANSLUCENT
    #define WRITES_GBUFFER_SKIP // the solid hand lights itself; deferred must leave it alone
#endif
#if defined PROG_WATER || (defined PROG_BLOCK && defined TRANSLUCENT)
    #define WRITES_SURFACE      // translucent surfaces own their pixel for the reflections
#endif

#ifdef VERTEX_SHADER
#if defined PROG_TERRAIN || defined PROG_WATER
in vec4 mc_Entity;
in vec4 mc_midTexCoord;
in vec3 at_midBlock;
#endif

out vec2 texcoord;
out vec2 lmcoord;
out vec4 glcolor;
out vec3 normalW;
out vec3 playerPos;
out vec3 blockCenter;           // world centre of the block the vertex belongs to (terrain)
flat out int matId;

void main() {
    texcoord = (gl_TextureMatrix[0] * gl_MultiTexCoord0).xy;
    lmcoord = normalizeLightmap((gl_TextureMatrix[1] * gl_MultiTexCoord1).xy);
    glcolor = gl_Color;
    vec4 viewPos = gl_ModelViewMatrix * gl_Vertex;
    playerPos = viewToPlayer(viewPos.xyz);
    matId = 0;
    blockCenter = vec3(0.0);
#if defined PROG_TERRAIN || defined PROG_WATER
    matId = int(mc_Entity.x + 0.5);
    bool hasMid = dot(at_midBlock, at_midBlock) > 1e-6;
    blockCenter = hasMid ? playerPos + cameraPosition + at_midBlock / 64.0 : floor(playerPos + cameraPosition) + 0.5;
  #if defined WAVING_PLANTS && defined PROG_TERRAIN
    float hIn = hasMid ? saturate(0.5 - at_midBlock.y / 64.0) : fract(playerPos.y + cameraPosition.y);
    vec3 off = wavingOffset(matId, playerPos + cameraPosition, texcoord, mc_midTexCoord.xy, lmcoord.y, hIn);
    if (dot(off, off) > 0.0) {
        playerPos += off;
        viewPos.xyz = (gbufferModelView * vec4(playerPos, 1.0)).xyz;
    }
  #endif
#endif
#ifdef PROG_BLOCK
    matId = blockEntityId;
#endif
#if defined PROG_ENTITIES && defined WORLD_SHIRAKABA
    if (entityId == ENT_WOMAN) {
        // she never holds quite still on the feed: a slow sway of the whole figure
        float t = frameTimeCounter;
        vec3 wp = playerPos + cameraPosition;
        vec3 w = vec3(vnoise1(t * 0.7 + wp.y * 1.7 + 3.0), 0.0, vnoise1(t * 0.6 + wp.y * 2.1 + 17.0)) - vec3(0.5, 0.0, 0.5);
        playerPos += w * 0.025;
        viewPos.xyz = (gbufferModelView * vec4(playerPos, 1.0)).xyz;
    }
#endif
    vec3 n = gl_NormalMatrix * gl_Normal;
    n = dot(n, n) > 1e-6 ? normalize(n) : vec3(0.0, 0.0, 1.0);
    normalW = mat3(gbufferModelViewInverse) * n;
    gl_Position = gl_ProjectionMatrix * viewPos;
}
#endif

#ifdef FRAGMENT_SHADER
in vec2 texcoord;
in vec2 lmcoord;
in vec4 glcolor;
in vec3 normalW;
in vec3 playerPos;
in vec3 blockCenter;
flat in int matId;

// ripples of open water (vanilla water elsewhere): world-space normal, +Y up
vec3 waterRipples(vec3 wp) {
    float t = frameTimeCounter;
    float s = 0.045 * (1.0 + rainAmount());
    vec2 p = wp.xz;
    float dx = cos(p.x * 1.7 + t * 1.3) * 0.5 + cos(p.x * 3.3 - p.y * 1.9 + t * 2.1) * 0.3 + cos(p.x * 7.1 + p.y * 4.3 - t * 3.7) * 0.2;
    float dz = cos(p.y * 1.9 - t * 1.1) * 0.5 + cos(p.y * 3.1 + p.x * 1.4 + t * 1.8) * 0.3 + cos(p.y * 6.7 - p.x * 5.1 + t * 3.3) * 0.2;
    return normalize(vec3(-dx * s, 1.0, -dz * s));
}

// the bath water: almost still - the slow breathing of a full tub, a drip from the tap now and
// then, rings crossing the surface. wp: world position
vec3 bathRipples(vec3 wp) {
    float t = frameTimeCounter;
    vec2 p = wp.xz;
    vec2 g = vec2(cos(p.x * 2.3 + t * 0.35) + cos(p.y * 1.7 - t * 0.29), cos(p.y * 2.9 + t * 0.31) + cos(p.x * 1.9 + t * 0.41)) * 0.006;
#ifdef WATER_RIPPLES
    // a drip every few seconds somewhere on the tub
    for (int i = 0; i < 2; i++) {
        float period = 3.1 + 1.7 * float(i);
        float cyc = t / period + float(i) * 0.37;
        float ph = fract(cyc);
        vec2 c = floor(p) + 0.25 + 0.5 * hash22(floor(p) + floor(cyc) * 1.37 + float(i) * 5.1);
        vec2 d = p - c;
        float r = length(d);
        float front = ph * 0.9;
        float ring = sin((r - front) * 40.0) * exp(-sq((r - front) * 7.0)) * sq(1.0 - ph);
        g += d / max(r, 1e-3) * ring * 0.05;
    }
#endif
    return normalize(vec3(-g.x, 1.0, -g.y));
}

// in-plane coordinates of a vertical / horizontal surface (x horizontal, y up on walls)
vec2 planeCoords(vec3 wp, vec3 N) {
    vec3 a = abs(N);
    if (a.x > a.y && a.x > a.z) return vec2(wp.z, wp.y);
    if (a.z > a.y) return vec2(wp.x, wp.y);
    return wp.xz;
}

#ifdef RAIN_ON_GLASS
// ---- rain on a window -------------------------------------------------------------------------
// Drops that sit, and drops that run: one lane of running drops per cell - each holds, then
// slides down in fits, wiggling, leaving a wet trail of tiny droplets behind it; a fine layer
// of still droplets that come and go. p: in-plane coordinates in blocks (y up), t: time.
// Returns the slope of the water surface in the plane (xy: refraction, highlights) and its
// coverage (z).
vec3 runningDrops(vec2 p, float t, float lanes, float seed) {
    vec2 cs = vec2(1.0, 3.2) / lanes;                // a cell: one lane wide, 3.2 lanes tall
    vec2 q = p / cs;
    vec2 id = floor(q);
    vec2 f = fract(q) - 0.5;
    vec3 res = vec3(0.0);
    float n = hash12(id + seed);
    if (n < 0.30) return res;                        // an empty lane
    float n2 = hash12(id * 1.37 + seed + 4.1);
    // stick-slip: the drop holds, then slides a stretch, holds again ... down its cell
    float tt = t * (0.11 + 0.10 * n2) + n * 9.0;
    float s = fract(tt);
    float y = 0.42 - 0.84 * (s + 0.16 * sin(TAU * s * 2.0 + n * 3.0)) ;
    float x = (n2 - 0.5) * 0.45 + sin(s * 11.0 + n * 6.0) * 0.06;
    vec2 dp = (f - vec2(x, y)) * vec2(1.0, 3.2);     // in lane widths
    float r = 0.17 + 0.08 * n2;
    dp.y *= 0.85;                                    // a drop sags into a pear
    float dist = length(dp);
    if (dist < r) {
        float z = sqrt(max(1.0 - sq(dist / r), 0.0));
        res = vec3(dp / r / max(z, 0.3), smoothstep(r, r * 0.72, dist));
    }
    // the trail: a thin wet line above it and a string of tiny droplets it left behind
    float above = (f.y - y) * 3.2;
    if (above > 0.0 && above < 2.6 * (1.0 - s) + 0.4) {
        float fadeT = 1.0 - smoothstep(0.0, 2.6, above);
        float wx = f.x - x - sin(s * 11.0 + n * 6.0 + above * 0.5) * 0.02;
        float line = smoothstep(0.035, 0.0, abs(wx));
        res.z = max(res.z, line * 0.35 * fadeT);
        // tiny droplets every so often along the trail
        float k = above * 5.0;
        vec2 sd = vec2(wx * 1.0, (fract(k) - 0.5) / 5.0) * vec2(1.0, 1.0);
        float rr = 0.035 * (0.6 + 0.4 * hash11(floor(k) + n * 31.0)) * fadeT;
        float sdist = length(sd);
        if (sdist < rr && hash11(floor(k) * 1.7 + n * 13.0) > 0.35) {
            float z = sqrt(max(1.0 - sq(sdist / rr), 0.0));
            res = vec3(sd / rr / max(z, 0.3) * 0.7, max(res.z, smoothstep(rr, rr * 0.6, sdist)));
        }
    }
    return res;
}
vec3 stillDroplets(vec2 p, float t, float scale, float seed) {
    vec2 q = p * scale;
    vec2 id = floor(q);
    vec2 f = fract(q);
    vec3 res = vec3(0.0);
    vec2 h = hash22(id + seed);
    if (h.x < 0.35) return res;
    vec2 c = 0.2 + 0.6 * hash22(id + seed + 5.7);
    // each droplet comes (a drop lands) and goes (it runs off or merges) on its own clock
    float life = fract(t * (0.05 + 0.07 * h.y) + h.x * 7.0);
    float a = smoothstep(0.0, 0.05, life) * (1.0 - smoothstep(0.7, 1.0, life));
    float r = (0.16 + 0.20 * h.y) * a;
    vec2 d = f - c;
    float dist = length(d);
    if (r > 0.02 && dist < r) {
        float z = sqrt(max(1.0 - sq(dist / r), 0.0));
        res = vec3(d / r / max(z, 0.3), smoothstep(r, r * 0.7, dist));
    }
    return res;
}
vec3 rainOnGlass(vec2 p) {
    float t = frameTimeCounter;
    vec3 a = runningDrops(p, t, 9.0, 3.1);
    vec3 b = runningDrops(p + vec2(0.37, 0.11), t * 0.8, 15.0, 11.7);
    vec3 c = stillDroplets(p, t, 26.0, 1.9);
    vec3 d = stillDroplets(p + 0.5, t * 1.3, 47.0, 7.3);
    vec3 res = a;
    if (b.z > res.z) res = b;
    if (c.z > res.z) res = c;
    if (d.z > res.z) res = d;
    return res;
}
#endif

// ---- the CRT's static (sunaarashi) on its screen ------------------------------------------------
// The screen face drawn as a CRT: white noise in cells far finer than the 16 x 16 texture, new
// every frame (30 fps), scanlines, a darker roll bar wandering down, the overall level following
// the same signal as the light the TV casts (lib/emitters.glsl tvSignal). wp: world position, N:
// the screen's normal, h: the TV's hash, sig: its signal now.
float crtStatic(vec3 wp, vec3 N, float sig) {
    vec3 tng = normalize(vec3(-N.z, 0.0, N.x) + vec3(1e-4, 0.0, 0.0));
    float u = dot(wp, tng);
    float v = wp.y;
    float t = frameTimeCounter;
    float frame = floor(t * 30.0);
    float snow = hash13(vec3(floor(u * 96.0), floor(v * 120.0), frame));
    // streaks of noise: the horizontal sync tears the snow into short dashes
    float dash = hash13(vec3(floor(u * 24.0), floor(v * 120.0), frame + 7.0));
    snow = mix(snow, dash, 0.35);
    float lines = 0.72 + 0.28 * sin(v * TAU * 110.0);
    float bar = fract(-t * 0.23 + v * 1.4);
    float roll = 1.0 - 0.35 * smoothstep(0.0, 0.12, bar) * (1.0 - smoothstep(0.12, 0.30, bar));
    return (0.18 + 1.05 * snow * snow) * lines * roll * sig;
}

void main() {
    float ao = 1.0;
#ifdef PROG_BASIC
    vec4 albedo = glcolor;
    float texAlpha = 1.0;
#else
    vec4 tex = texture(gtexture, texcoord);
    float texAlpha = tex.a;
    vec4 albedo = tex;
  #if defined PROG_TERRAIN || defined PROG_WATER
    albedo.rgb *= glcolor.rgb;
    ao = glcolor.a;                    // separateAo: vertex AO lives in the alpha channel
  #else
    albedo *= glcolor;
  #endif
#endif
#ifdef PROG_ENTITIES
    albedo.rgb = mix(albedo.rgb, entityColor.rgb, entityColor.a);
#endif
    if (albedo.a < 0.004) discard;
#if defined PROG_ENTITIES && !defined TRANSLUCENT
    if (albedo.a < 0.1) discard;
#endif

    // ---- programs that only overlay something --------------------------------------
#if defined PROG_DAMAGE
    /* RENDERTARGETS: 0 */
    gl_FragData[0] = albedo;
#elif defined PROG_GLINT
    /* RENDERTARGETS: 0 */
    gl_FragData[0] = vec4(toLinear(albedo.rgb) * 0.30, albedo.a);
#elif defined PROG_EYES
    // Glowing details (RenderType.eyes): the doll's button eyes, a camera's REC LED. Additive.
    vec3 lin = toLinear(albedo.rgb);
    vec3 eyes = lin * 1.8;
  #ifdef WORLD_SHIRAKABA
    float cam = cctv();
    float t = frameTimeCounter;
    if (entityId == ENT_DOLL) {
        // plastic buttons: they do not glow - they catch light. A glint of the TV, of the
        // phone's LED; on the IR feed the illuminator makes them burn white
        vec3 wp = playerPos + cameraPosition;
        vec3 lampGlint = vec3(0.0);
      #ifdef EMITTERS_ON
        vec3 dfs, spc, dp;
        float dw;
        vec3 V = -normalize(playerPos);
        lampsAt(wp, V, V, lmcoord.x * 15.0, 0.1, 0.05, 0.0, 0.0, 0.0, dfs, spc, dp, dw);
        lampGlint = dfs * 0.35;
      #endif
        vec3 shine = handLightEyeshine(playerPos);
        eyes = mix(lin, vec3(0.9), 0.6) * (lampGlint + shine * 0.30 * (1.0 + 2.5 * cam));
    } else if (entityId == ENT_CAMERA) {
        // REC: a red LED blinking once a second
        float on = step(fract(t * 1.0 + 0.13), 0.62);
        eyes = lin * vec3(1.0, 0.25, 0.20) * 3.2 * on;
    } else if (entityId == ENT_WOMAN) {
        // only a camera sees her; her eyes - if any show through the hair - barely
        eyes = lin * 0.35 * cam + mix(lin, vec3(0.9), 0.5) * handLightEyeshine(playerPos) * 0.15;
    }
    eyes *= glcolor.a;
  #else
    eyes = lin * 2.2 + mix(lin, vec3(0.9), 0.5) * handLightEyeshine(playerPos) * 0.4;
  #endif
    /* RENDERTARGETS: 0,1 */
    gl_FragData[0] = vec4(eyes, albedo.a);
    gl_FragData[1] = vec4(saturate(maxOf(eyes) * 2.0), 0.0, 0.0, albedo.a);
#elif defined PROG_BEACON
    /* RENDERTARGETS: 0,1 */
    gl_FragData[0] = vec4(toLinear(albedo.rgb) * 1.8, albedo.a);
    gl_FragData[1] = vec4(1.0, 0.0, 0.0, albedo.a);
#elif defined PROG_WEATHER
    // rain and snow - never vanilla rain in the apartment's world (rainStrength is fear there;
    // the night's rain is the pack's own: streaks in the air, drops on the windows)
  #ifdef WORLD_SHIRAKABA
    discard;
  #endif
    vec3 lin = toLinear(albedo.rgb);
    vec3 light = skyAmbientN(vec3(0.0, 1.0, 0.0)) * (0.25 + 0.75 * lmcoord.y) * 1.6 + FLAME_DEEP * flameFalloff(lmcoord.x) * 0.6 + MIN_LIGHT;
    /* RENDERTARGETS: 0 */
    gl_FragData[0] = vec4(lin * light, albedo.a * 0.40);
#elif defined PROG_CLOUDS
    // the rain clouds are painted by the sky (lib/sky.glsl): no vanilla clouds there
  #ifdef WORLD_SHIRAKABA
    discard;
  #endif
    vec3 dirW = normalize(playerPos);
    vec3 lin = toLinear(albedo.rgb);
    vec3 c = lin * (skyAmbientN(vec3(0.0, 1.0, 0.0)) * 1.5 + celestialLight() * 0.35 * (0.5 + 0.5 * saturate(dot(dirW, lightDirW()))));
    c = mix(c, vec3(luma(c)), 0.35);
    /* RENDERTARGETS: 0,1 */
    gl_FragData[0] = vec4(c, albedo.a * 0.78);
    gl_FragData[1] = vec4(0.0, 0.0, 0.5, 1.0);
#else
    // ---- lit surfaces ----------------------------------------------------------------
    vec3 N = normalize(normalW);
    vec3 Ngeo = N;
    vec2 lm = lmcoord;
    int id = matId;
    int mat = MAT_DIFFUSE;
    float emissive = 0.0;
    float cls = 0.0;
    bool receiveShadow = true;
    float alpha = albedo.a;
    float twoSided = 0.0;
    float backK = 0.0;
    vec3 wp = playerPos + cameraPosition;
    vec3 V = -normalize(playerPos + vec3(0.0, 1e-4, 0.0));

    vec3 lin = toLinear(albedo.rgb);
    vec3 emission = vec3(0.0);       // linear HDR light emitted by the surface itself
    float fillK = 1.0;               // the fill for light no lamp explains (lampsAt)
    vec4 entOut = vec4(0.0);
    bool bathWater = false;

  #if defined PROG_TERRAIN || defined PROG_WATER || defined PROG_BLOCK
    mat = materialOfBlock(id);
    int etype = emitterTypeOfBlock(id);
    // the art's glowing faces are drawn full-bright (forge_data block_light / sky_light 15,
    // baked into the quad): indoors nothing else has a full lightmap. That lightmap says
    // nothing about the light round the face - it gets its lamp's own level (a middling one on
    // a block that is not a lamp) and the sky only out in the open
    bool marked = false;
    #ifdef WORLD_SHIRAKABA
    marked = lm.x > 0.985 && lm.y > 0.985 && id != BLK_FLAME && id != BLK_CANDLE && id != BLK_LAMP && id != BLK_GLOW && id != BLK_LAVA;
    if (marked) {
        float own = etype > EM_NONE ? max(EM_LEVEL[clamp(etype, 0, EM_COUNT - 1)] - 1.0, 0.0) : 12.0;
        lm = vec2(own / 15.0, openSky(wp + Ngeo * 0.3));
        fillK = 0.0;
    }
    #endif
    if (id == BLK_FLAME) {
        emissive = flameMask(albedo.rgb);
        emission = lin * emissive * 3.2;
    } else if (id == BLK_CANDLE) {
        emissive = candleMask(albedo.rgb);
        emission = lin * emissive * 3.2;
    } else if (id == BLK_LAMP) {
        emissive = paperMask(albedo.rgb) * 0.85;
        emission = lin * FLAME_HOT * emissive * 1.8;
    } else if (id == BLK_GLOW) {
        emissive = 0.30 + 0.55 * luma(albedo.rgb);
        emission = lin * emissive * 1.6;
    } else if (id == BLK_LAVA) {
        emissive = 0.90;
        emission = lin * 2.0;
    } else if (etype > EM_NONE) {
        // the apartment's lamps: the glowing texels in the lamp's colour, flickering with it
    #ifdef PROG_BLOCK
        vec3 blockPos = floor(wp - Ngeo * 0.02);     // block entities carry no at_midBlock
    #else
        vec3 blockPos = floor(blockCenter);
    #endif
        float m = emitterSurfaceMask(etype, albedo.rgb, N, marked);
        float f = emitterFlicker(etype, blockPos, frameTimeCounter) * dangerStutter(etype, blockPos, frameTimeCounter);
        int ti = clamp(etype, 0, EM_COUNT - 1);
        vec3 tint = mix(lin, lin * EM_COLOR[ti] * 1.25, 0.45);
        float gain = EM_SURF[ti] * LIGHT_STRENGTH;
        if (emitterIsTv(etype)) {
            gain *= TV_STRENGTH;
            vec3 pic = mix(vec3(luma(lin)), lin, 0.6) * vec3(0.84, 0.92, 1.06);
            if (tvChannel(etype) == 0) {
        #ifdef TV_STATIC
                // the snow itself, finer than any texture, in the screen's cold blue-white
                tint = vec3(0.70, 0.80, 1.00) * crtStatic(wp, Ngeo, 1.0) * (0.35 + 0.65 * smoothstep(0.1, 0.5, luma(albedo.rgb)) + 0.25);
        #else
                tint = pic;
        #endif
            } else {
                // a picture (a dark room, a face): the art's frames, with scanlines and a little snow
                float lines = 0.80 + 0.20 * sin(wp.y * TAU * 110.0);
                float snow = hash13(vec3(floor(planeCoords(wp, Ngeo) * 96.0), floor(frameTimeCounter * 30.0)));
                tint = pic * lines * (0.88 + 0.24 * snow);
            }
        }
        emissive = m * saturate(f * 1.2);
        emission = tint * m * gain * f;
        mat = m > 0.4 ? MAT_EMITTER : mat;
    } else if (marked || id == BLK_INDICATOR) {
        // an indicator light on a block that is not a lamp: the breaker's lamp, the rice
        // cooker's "keep warm", the washing machine's panel
        float m = indicatorMask(albedo.rgb);
        emissive = m;
        emission = lin * m * 1.6 * LIGHT_STRENGTH;
        mat = m > 0.4 ? MAT_EMITTER : mat;
    }
    #ifdef WORLD_SHIRAKABA
    // the bathtub, filled: the water surface is the face looking up from inside the tub,
    // below its rim (the rim's top is at the top of the block)
    if (id == BLK_BATH_WATER && Ngeo.y > 0.9) {
      #ifdef PROG_BLOCK
        float hIn = fract(wp.y - 1e-3);
      #else
        float hIn = wp.y - floor(blockCenter.y - 0.5 + 1e-3);
      #endif
        if (hIn > 0.22 && hIn < 0.94) bathWater = true;
    }
    // the street's asphalt drawn near-black for vanilla's bright light: under the street lamp
    // it needs the albedo of real old asphalt (~0.035) for the sodium pool to show
    if (mat == MAT_ASPHALT) lin *= 1.8;
    // the closet: a little light from behind for paper and cloth (the fusuma glow faintly
    // where the TV lights their other side), the frosted glass and the curtains much more
    if (mat == MAT_PAPER) backK = 0.03;
    if (mat == MAT_CURTAIN) {
        float u = (wp.x + wp.z) * 3.1;
        float fold = 0.5 + 0.5 * sin(u * TAU + 1.7 * sin(u * 1.3));
        backK = 0.45 * (0.6 + 0.8 * fold);
        N = normalize(N + vec3(cos(u * TAU), 0.0, sin(u * TAU)) * 0.25 * (1.0 - abs(N.y)));
    }
    if (mat == MAT_FROSTED) backK = 0.65;
    #endif
  #endif
  #if defined PROG_TEXTURED || defined PROG_BASIC
    // particles / lines are camera-facing: light them from every side
    N = normalize(-playerPos + vec3(0.0, 0.5, 0.0) * length(playerPos));
    receiveShadow = false;
    twoSided = 1.0;
    mat = MAT_NONE;
    if (lm.x > 0.97) {                                           // full-bright particles: embers, static motes
        emissive = max(flameMask(albedo.rgb), 0.5) * 0.8;
        emission = lin * emissive * 2.5;
    }
  #endif
  #ifdef PROG_ENTITIES
    mat = MAT_DIFFUSE;
    if (entityId == ENT_LIGHTNING) {
        emissive = 1.0;
        emission = lin * 2.5;
    }
    #ifdef WORLD_SHIRAKABA
    if (entityId == ENT_DOLL) {
        // soaked through: darker, a dull wet sheen in the TV's light
        mat = MAT_DOLL;
        lin *= 0.82;
        entOut = vec4(1.0, 0.0, 0.0, 0.0);
    } else if (entityId == ENT_WOMAN) {
        mat = MAT_WOMAN;
        // on the IR feed she gives back the illuminator's light too evenly - a faint, flat glow
        emission = mix(vec3(luma(lin)), lin, 0.3) * vec3(0.80, 0.90, 1.0) * 0.030 * cctv();
        emissive = 0.2 * cctv();
        entOut = vec4(0.0, 1.0, 0.0, 0.55 * cctv());
    } else if (entityId == ENT_CAMERA) {
        mat = MAT_PLASTIC;
    }
    #endif
  #endif
  #ifdef PROG_HAND
    cls = 1.0;
    mat = MAT_NONE;
    receiveShadow = false;
    int item = currentRenderedItemId;
    bool lit = lm.x > 0.985 && lm.y > 0.985;            // drawn full-bright by the art (forge_data)
    vec3 Nv = mat3(gbufferModelView) * N;
    if (item == ITEM_PHONE || item == ITEM_PHONE_LIGHT || item == ITEM_LAPTOP) {
        // the screen glows - the marked face, or the bright cold texels of the face toward us
        float scr = lit ? smoothstep(0.04, 0.20, luma(albedo.rgb))
                        : smoothstep(0.45, 0.70, luma(albedo.rgb)) * smoothstep(0.02, 0.10, albedo.b - albedo.r + 0.04) * smoothstep(0.3, 0.7, Nv.z);
        float k = item == ITEM_LAPTOP ? 1.25 : 0.95;
        emissive = scr;
        emission = lin * scr * k * vec3(0.90, 0.96, 1.05) * (0.94 + 0.06 * vnoise1(frameTimeCounter * 2.7));
        if (lit) lm = vec2(0.25, 0.0);                  // its own light says nothing of the room's
        if (item == ITEM_PHONE_LIGHT) {
            // the LED itself, a hot white point
            float led = smoothstep(0.88, 0.97, luma(albedo.rgb)) * smoothstep(0.4, 0.8, -Nv.z);
            emission += lin * led * PHONE_LED_COLOR * 4.0;
            emissive = max(emissive, led);
        }
    } else if (item == ITEM_LIGHTER) {
        float fl = flameMask(albedo.rgb) * step(albedo.b, albedo.r + 0.02);
        emissive = fl;
        emission = lin * fl * vec3(1.0, 0.80, 0.55) * 3.0 * lighterFlicker(frameTimeCounter);
        if (lit) lm = vec2(0.35, 0.0);
    } else if (item == ITEM_KNIFE) {
        // the blade: bright grey steel - a cold sheen catching the TV
        float blade = smoothstep(0.35, 0.60, luma(albedo.rgb)) * (1.0 - smoothstep(0.06, 0.14, chroma(albedo.rgb)));
        if (blade > 0.5) mat = MAT_STEEL;
    } else if (item == ITEM_HANDLIGHT) {
        emissive = flameMask(albedo.rgb);
        emission = lin * emissive * 2.4;
    }
  #endif
    if (mat == MAT_FOLIAGE) twoSided = 1.0;

    // ---- out in the rain ------------------------------------------------------------------
    float wet = 0.0;
    float puddle = 0.0;
  #if defined WORLD_SHIRAKABA && defined WET_SURFACES && (defined PROG_TERRAIN || defined PROG_BLOCK || defined PROG_ENTITIES)
    if (mat != MAT_EMITTER && mat != MAT_NONE && !bathWater) {
        wet = surfaceWetness(wp, Ngeo, lm.y, mat);
        if (wet > 0.0) {
            if ((mat == MAT_DIFFUSE || mat == MAT_WALL || mat == MAT_FABRIC || mat == MAT_PAPER) && wet > 0.5) mat = MAT_WET;
            puddle = puddleAmount(wp, mat, Ngeo) * wet;
            lin *= mix(1.0, wetDarkening(mat), wet);
            lin *= 1.0 - 0.25 * puddle;
            N = wetMicroNormal(N, wp, mat, puddle);
            N = rainRipples(N, wp, puddle * rainAmount(), 1.6);
        }
    }
  #endif

    float dither = ign(gl_FragCoord.xy);
    float glow = 0.0;
    vec3 color;

  #if defined PROG_WATER || defined PROG_TERRAIN || defined PROG_BLOCK
    if (id == BLK_WATER || id == BLK_PUDDLE || bathWater) {
        // puddles (black mirrors, rain falling into them), vanilla water, the bath water (dark,
        // still); the reflection itself is traced in composite1 - here the dark body, the lamps'
        // glints and the rings
        if (N.y > 0.5) {
            if (bathWater) N = bathRipples(wp);
            else if (id == BLK_PUDDLE) N = rainRipples(vec3(0.0, 1.0, 0.0), wp, rainAmount() * 1.4, 2.2);
            else N = rainRipples(waterRipples(wp), wp, rainAmount() * openSky(wp + vec3(0.0, 0.3, 0.0)), 2.0);
        }
        float NdotV = saturate(dot(N, V));
        float F = 0.02 + 0.98 * pow(1.0 - NdotV, 5.0);
        float BODY = id == BLK_PUDDLE ? 0.05 : (bathWater ? 0.10 : 0.18);
        vec3 body = shadeSurface(lin * BODY, N, playerPos, lm, 1.0, vec3(0.0), MAT_PUDDLE, 1.0, dither, true, 0.0, 0.0, fillK, glow);
        vec3 glints = vec3(0.0);
    #if defined PROG_WATER || defined TRANSLUCENT
        body += handLightsForward(lin * BODY, playerPos, vec3(0.0, 1.0, 0.0), 0.0, vec2(1.0, -1.0));
        glints = handLightsForward(vec3(0.0), playerPos, N, 0.0, vec2(0.05, 0.02));
    #endif
        color = body + glints;
    #ifndef REFLECTIONS
        vec3 R = reflect(-V, N);
        vec3 refl = (fogColorFor(R) * 0.7 + skyGradient(R) * 0.3) * smoothstep(0.30, 0.90, lm.y);
        color = mix(body, refl, F * 0.6) + glints;
    #endif
        alpha = id == BLK_PUDDLE ? mix(0.82, 0.97, F) : mix(0.66, 0.96, F);
        if (bathWater) alpha = max(alpha, mix(0.86, 0.98, F));
        if (isEyeInWater == 1) alpha = 0.45;
        emissive = saturate(maxOf(glints) * 0.5);
        mat = bathWater ? MAT_WATER : MAT_PUDDLE;
    } else
  #endif
    {
  #if defined PROG_WATER || (defined PROG_BLOCK && defined TRANSLUCENT)
        if (mat == MAT_GLASS_RAIN) {
            // ---- windows at night: dark glass, the rain running down it, reflections (composite1)
            vec2 pc = planeCoords(wp, Ngeo);
            float cover = 0.0;
    #ifdef RAIN_ON_GLASS
            if (abs(Ngeo.y) < 0.5) {
                vec3 dr = rainOnGlass(pc);
                cover = dr.z;
                vec3 a = abs(Ngeo);
                vec3 tu = a.x > a.z ? vec3(0.0, 0.0, 1.0) : vec3(1.0, 0.0, 0.0);
                N = normalize(Ngeo + (tu * dr.x + vec3(0.0, 1.0, 0.0) * dr.y) * 0.60 * cover);
            }
    #endif
            float NdotV = saturate(abs(dot(N, V)));
            float F = 0.04 + 0.96 * pow(1.0 - NdotV, 5.0);
            vec3 tint = mix(vec3(0.80, 0.88, 0.86), lin, 0.35);
            vec3 Nf = dot(N, V) < 0.0 ? -N : N;          // the side we look at
            color = shadeSurface(tint * 0.05, Nf, playerPos, lm, 1.0, vec3(0.0), MAT_GLASS_RAIN, 0.0, dither, true, 0.0, 0.0, fillK, glow);
            color += handLightsForward(vec3(0.0), playerPos, Nf, 0.0, vec2(0.04, 0.04)) * (0.3 + cover * 1.5);
    #if defined RAIN_ON_GLASS && defined EMITTERS_ON
            if (cover > 0.01) {
                // a drop is a lens: it gathers the light falling on the glass from the far side (the
                // street lamp outside, the room's light seen from out there) into a bright crescent
                vec3 gf = Ngeo * sign(dot(Ngeo, -V));    // the glass, facing away from us
                vec3 dfs, spc, dp;
                float dw;
                lampsAt(wp, -gf, V, lm.x * 15.0, 0.5, -1.0, 0.0, 0.0, 0.0, dfs, spc, dp, dw);
                float lens = smoothstep(0.15, 0.9, cover) * (0.6 + 0.4 * saturate(-N.y * 3.0 + 0.5));
                color += dfs * lens * 0.55;
                emissive = max(emissive, saturate(luma(dfs * lens) * 3.0));
            }
    #endif
            alpha = clamp(max(albedo.a * 0.40, 0.08) + F * 0.5 + cover * 0.10, 0.0, 0.85);
        } else if (mat == MAT_FROSTED) {
            // ---- frosted glass: milky, glowing with what lights it from behind -------------
            vec3 Nf = dot(N, V) < 0.0 ? -N : N;
            color = shadeSurface(lin * 0.85, Nf, playerPos, lm, 1.0, vec3(0.0), MAT_FROSTED, 0.0, dither, true, 0.0, backK, fillK, glow);
            color += handLightsForward(lin, playerPos, Nf, 1.0, vec2(0.45, 0.04)) * 0.7;
            alpha = clamp(max(albedo.a, 0.55) + 0.15, 0.0, 0.92);
        } else if (mat == MAT_GLASS_SMALL || mat == MAT_MIRROR) {
            float NdotV = saturate(abs(dot(N, V)));
            float F = 0.04 + 0.96 * pow(1.0 - NdotV, 5.0);
            color = shadeSurface(lin, N, playerPos, lm, ao, emission, mat, 0.0, dither, true, 0.0, 0.0, fillK, glow);
            color += handLightsForward(lin, playerPos, N, 0.0, vec2(0.08, 0.04));
            alpha = clamp(albedo.a + F * 0.4, 0.0, 0.95);
        } else
  #endif
        {
            vec4 surf = materialSurface(mat, wet);
            vec2 gloss = vec2(mix(surf.x, 0.06, puddle), surf.w > 0.0 || surf.z > 0.5 ? surf.y : -1.0);
            color = shadeSurface(lin, N, playerPos, lm, ao, emission, mat, wet, dither, receiveShadow, twoSided, backK, fillK, glow);
  #if defined TRANSLUCENT || defined PROG_WATER || defined PROG_TEXTURED || defined PROG_BASIC || defined PROG_HAND
            // no deferred pass for these: the hand lights and the IR illuminator are added here
    #ifdef PROG_HAND
            color += handLightsForward(lin, playerPos, N, 0.0, vec2(gloss.x, mat == MAT_STEEL ? 0.6 : -1.0)) * 0.35;
    #else
            color += handLightsForward(lin, playerPos, N, twoSided, gloss);
    #endif
  #endif
        }
    }

  #if defined WRITES_GBUFFER
    /* RENDERTARGETS: 0,1,5,6,8 */
    gl_FragData[0] = vec4(color, alpha);
    gl_FragData[1] = vec4(saturate(emissive), saturate(glow), cls, alpha);
    gl_FragData[2] = vec4(toSrgb(lin), float(mat) / 255.0);
    gl_FragData[3] = vec4(octEncode(N), lm.x, lm.y);
    gl_FragData[4] = entOut;
  #elif defined WRITES_GBUFFER_SKIP
    /* RENDERTARGETS: 0,1,5,6,8 */
    gl_FragData[0] = vec4(color, alpha);
    gl_FragData[1] = vec4(saturate(emissive), 0.0, cls, alpha);
    gl_FragData[2] = vec4(0.0);
    gl_FragData[3] = vec4(octEncode(N), 0.0, 0.0);
    gl_FragData[4] = vec4(0.0);
  #elif defined WRITES_SURFACE
    /* RENDERTARGETS: 0,1,5,6 */
    gl_FragData[0] = vec4(color, alpha);
    gl_FragData[1] = vec4(saturate(emissive), saturate(glow), cls, alpha);
    gl_FragData[2] = vec4(toSrgb(lin), float(mat) / 255.0);
    gl_FragData[3] = vec4(octEncode(N), lm.x, lm.y);
  #elif defined PROG_ENTITIES
    // translucent entities: leave the background's material and normal alone
    /* RENDERTARGETS: 0,1,8 */
    gl_FragData[0] = vec4(color, alpha);
    gl_FragData[1] = vec4(saturate(emissive), saturate(glow), cls, alpha);
    gl_FragData[2] = entOut;
  #else
    /* RENDERTARGETS: 0,1 */
    gl_FragData[0] = vec4(color, alpha);
    gl_FragData[1] = vec4(saturate(emissive), saturate(glow), cls, alpha);
  #endif
#endif
}
#endif
