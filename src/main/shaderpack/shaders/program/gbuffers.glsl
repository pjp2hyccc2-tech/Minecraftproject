// =====================================================================================
//  ReisatsuShaders - every lit gbuffers program
//  The stub selects the variant:
//    PROG_TERRAIN  PROG_WATER (translucent terrain)  PROG_BLOCK  PROG_ENTITIES  PROG_HAND
//    PROG_TEXTURED (particles)  PROG_BASIC (lines)  PROG_EYES  PROG_GLINT  PROG_BEACON
//    PROG_DAMAGE  PROG_WEATHER  PROG_CLOUDS        (+ TRANSLUCENT for blended variants)
//  Opaque surfaces are lit forward here (the restaurant's lamps / sun, sky, wet ground)
//  and leave albedo, normal and light levels for the deferred pass, which adds the
//  flashlight and the camera flash once per pixel. Translucents (puddles, windows, the
//  mirror, glasses, spirits), particles and the hand light themselves.
//    colortex0  linear HDR colour
//    colortex1  r emissive, g lamp share (screen-space lamp shadows), b class (1 hand, 0.5 cloud), a coverage
//    colortex5  albedo (sRGB) + a: material code (MAT_*) / 255
//    colortex6  octahedral world normal (rg), block light (b), sky light (a)
//    colortex8  spirits: r body, g finder glow, b eye alpha, a haze
// =====================================================================================
#include "/lib/header.glsl"

#if (defined PROG_TERRAIN || defined PROG_BLOCK || defined PROG_ENTITIES) && !defined TRANSLUCENT
    #define WRITES_GBUFFER      // opaque: lit by the hand lights / flash in the deferred pass
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
flat out int matId;

void main() {
    texcoord = (gl_TextureMatrix[0] * gl_MultiTexCoord0).xy;
    lmcoord = normalizeLightmap((gl_TextureMatrix[1] * gl_MultiTexCoord1).xy);
    glcolor = gl_Color;
    vec4 viewPos = gl_ModelViewMatrix * gl_Vertex;
    playerPos = viewToPlayer(viewPos.xyz);
    matId = 0;
#if defined PROG_TERRAIN || defined PROG_WATER
    matId = int(mc_Entity.x + 0.5);
  #if defined WAVING_PLANTS && defined PROG_TERRAIN
    float hIn = dot(at_midBlock, at_midBlock) > 1e-6 ? saturate(0.5 - at_midBlock.y / 64.0) : fract(playerPos.y + cameraPosition.y);
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
#if defined PROG_ENTITIES && defined WORLD_HIDAMARI
    if (isSpirit(entityId)) {
        // the spirits never hold quite still: a slow waver of the whole body
        float t = frameTimeCounter;
        vec3 wp = playerPos + cameraPosition;
        vec3 w = vec3(vnoise1(t * 0.9 + wp.y * 1.7 + 3.0), vnoise1(t * 0.7 + wp.y * 1.3 + 9.0), vnoise1(t * 0.8 + wp.y * 2.1 + 17.0)) - 0.5;
        w += (vec3(vnoise1(t * 6.3 + wp.y * 9.0), 0.0, vnoise1(t * 5.7 + wp.y * 8.0 + 4.0)) - 0.5) * 0.25 * (0.3 + danger());
        playerPos += w * (0.030 + 0.025 * fear());
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

// in-plane coordinates of a vertical / horizontal surface (droplets, smudges)
vec2 planeCoords(vec3 wp, vec3 N) {
    vec3 a = abs(N);
    if (a.x > a.y && a.x > a.z) return vec2(wp.z, wp.y);
    if (a.z > a.y) return vec2(wp.x, wp.y);
    return wp.xz;
}

#ifdef GLASS_DROPLETS
// rain left on a window: round drops of many sizes, a few that ran and left a trail.
// Returns the drop's slope in the plane (xy) and its coverage (z).
vec3 rainDroplets(vec2 p) {
    vec3 res = vec3(0.0);
    for (int layer = 0; layer < 2; layer++) {
        float sc = layer == 0 ? 14.0 : 31.0;
        vec2 q = p * sc + float(layer) * 17.3;
        vec2 cell = floor(q);
        vec2 f = fract(q);
        for (int j = -1; j <= 1; j++) {
            for (int i = -1; i <= 1; i++) {
                vec2 o = vec2(float(i), float(j));
                vec2 h = hash22(cell + o + float(layer) * 31.0);
                if (h.x < 0.25) continue;                       // empty cell
                vec2 c = o + 0.15 + 0.7 * hash22(cell + o + 5.7);
                float r = (layer == 0 ? 0.20 : 0.26) * (0.35 + 0.65 * h.y);
                vec2 d = f - c;
                d.y *= 0.85 + 0.3 * h.x;                         // drops sag a little
                float dist = length(d);
                if (dist < r) {
                    float z = sqrt(1.0 - sq(dist / r));
                    vec2 slope = d / r / max(z, 0.25);
                    res = vec3(slope, max(res.z, smoothstep(r, r * 0.75, dist)));
                }
                // the trail of a drop that ran down: a thin wet line above it
                if (layer == 0 && h.y > 0.78) {
                    float trailW = r * 0.32;
                    float above = f.y - c.y;
                    if (above > 0.0 && above < 2.4 && abs(f.x - c.x) < trailW) {
                        float tz = 1.0 - sq((f.x - c.x) / trailW);
                        res.z = max(res.z, 0.55 * tz * (1.0 - above / 2.4));
                        res.xy += vec2((f.x - c.x) / trailW * 0.6, 0.0) * (1.0 - above / 2.4);
                    }
                }
            }
        }
    }
    return res;
}
#endif

// smudges and wiped streaks on a mirror: 0 clean .. 1 smeared
float mirrorSmudge(vec2 p) {
    float n = fbm2(p * 3.1 + 4.0) * 0.6 + vnoise2(p * vec2(11.0, 2.2) + 9.0) * 0.4;
    float wipe = smoothstep(0.45, 0.75, vnoise2(vec2(p.x * 0.8 + p.y * 1.7, p.y * 0.3) * 2.0));
    return saturate(smoothstep(0.40, 0.80, n) * 0.8 + wipe * 0.35);
}

// cracks of the broken mirror: radial lines from the impact plus a ring. 1 on a crack.
float mirrorCracks(vec2 p, vec2 impact) {
    vec2 d = p - impact;
    float r = length(d);
    float a = atan(d.y, d.x);
    float k = 0.0;
    for (int i = 0; i < 9; i++) {
        float ai = hash11(float(i) * 3.7 + 1.0) * TAU;
        float wob = (vnoise1(r * 7.0 + float(i) * 5.0) - 0.5) * 0.35;
        float da = abs(mod(a - ai - wob + PI, TAU) - PI);
        k = max(k, smoothstep(0.035, 0.0, da * r) * smoothstep(1.4, 0.05, r));
    }
    k = max(k, smoothstep(0.012, 0.0, abs(r - 0.22 - 0.03 * sin(a * 7.0))) * 0.8);
    k = max(k, smoothstep(0.010, 0.0, abs(r - 0.47 - 0.05 * sin(a * 5.0 + 1.0))) * 0.5);
    return k * smoothstep(0.0, 0.03, r);
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
    bool spirit = false;
#if defined PROG_ENTITIES && defined WORLD_HIDAMARI
    spirit = isSpirit(entityId);
#endif
    // a spirit the eye cannot see still leaves its fragments: the heat haze needs them
    if (spirit ? texAlpha < 0.1 : albedo.a < 0.004) discard;
#if defined PROG_ENTITIES && !defined TRANSLUCENT
    if (!spirit && albedo.a < 0.1) discard;
#endif

    // ---- programs that only overlay something --------------------------------------
#if defined PROG_DAMAGE
    /* RENDERTARGETS: 0 */
    gl_FragData[0] = albedo;
#elif defined PROG_GLINT
    /* RENDERTARGETS: 0 */
    gl_FragData[0] = vec4(toLinear(albedo.rgb) * 0.30, albedo.a);
#elif defined PROG_EYES
    // Glowing details (RenderType.eyes): the chef's embers, the visor glint, eyes. Additive.
    vec3 lin = toLinear(albedo.rgb);
    vec3 eyes;
  #ifdef WORLD_HIDAMARI
    float fm = finderMode();
    if (entityId == ENT_CHEF) {
        // embers in the cracks of burnt skin: breathing, flaring
        float t = frameTimeCounter;
        vec3 wp = playerPos + cameraPosition;
        float n = vnoise1(t * 2.3 + wp.y * 9.0) * 0.6 + vnoise1(t * 7.9 + wp.x * 13.0) * 0.4;
        eyes = lin * vec3(1.0, 0.55, 0.22) * (1.6 + 2.2 * n);
    } else if (isSpirit(entityId)) {
        eyes = lin * 1.6 + mix(lin, vec3(0.9), 0.5) * handLightEyeshine(playerPos) * 0.25;
    } else {
        eyes = lin * 1.8;
    }
    eyes *= glcolor.a * (1.0 + 1.2 * fm);
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
    // rain and snow - never in the restaurant's world (rainStrength is fear there)
  #ifdef WORLD_HIDAMARI
    discard;
  #endif
    vec3 lin = toLinear(albedo.rgb);
    vec3 light = skyAmbientN(vec3(0.0, 1.0, 0.0)) * (0.25 + 0.75 * lmcoord.y) * 1.6 + FLAME_DEEP * flameFalloff(lmcoord.x) * 0.6 + MIN_LIGHT;
    /* RENDERTARGETS: 0 */
    gl_FragData[0] = vec4(lin * light, albedo.a * 0.40);
#elif defined PROG_CLOUDS
    // the restaurant's overcast is painted by the sky (lib/sky.glsl): no vanilla clouds
  #ifdef WORLD_HIDAMARI
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
    vec3 wp = playerPos + cameraPosition;
    vec3 V = -normalize(playerPos + vec3(0.0, 1e-4, 0.0));

    vec3 lin = toLinear(albedo.rgb);
    vec3 emission = vec3(0.0);       // linear HDR light emitted by the surface itself
    vec4 spiritOut = vec4(0.0);

  #if defined PROG_TERRAIN || defined PROG_WATER || defined PROG_BLOCK
    mat = materialOfBlock(id);
    int etype = emitterTypeOfBlock(id);
    if (id == BLK_FLAME) {
        emissive = flameMask(albedo.rgb);
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
        // the restaurant's lamps: the glowing texels in the lamp's colour, flickering with it
        vec3 blockPos = floor(wp - N * 0.02);
        float m = emitterSurfaceMask(etype, albedo.rgb, N);
        float f = emitterFlicker(etype, blockPos, frameTimeCounter) * dangerStutter(etype, blockPos, frameTimeCounter);
        int ti = clamp(etype, 0, EM_COUNT - 1);
        vec3 tint = mix(lin, lin * EM_COLOR[ti] * 1.25, 0.45);
        if (etype == EM_CRT) {
            // static on the screen
            float sn = hash12(floor(texcoord * 4096.0) + floor(frameTimeCounter * 24.0) * 7.31);
            tint *= 0.35 + 1.1 * sn;
        }
        if (etype == EM_FLUO_BROKEN) {
            // a dying tube: its ends keep a dim pink-orange glow when the middle is out
            tint = mix(tint * vec3(1.0, 0.55, 0.45), tint, smoothstep(0.15, 0.6, f));
        }
        emissive = m * saturate(f * 1.2);
        emission = tint * m * EM_SURF[ti] * f * LIGHT_STRENGTH;
        mat = m > 0.4 ? MAT_EMITTER : mat;
    }
  #endif
  #if defined PROG_TEXTURED || defined PROG_BASIC
    // particles / lines are camera-facing: light them from every side
    N = normalize(-playerPos + vec3(0.0, 0.5, 0.0) * length(playerPos));
    receiveShadow = false;
    twoSided = 1.0;
    mat = MAT_NONE;
    if (lm.x > 0.97) {                                           // full-bright particles: embers, motes
        emissive = max(flameMask(albedo.rgb), 0.5) * 0.8;
        emission = lin * emissive * 2.5;
    }
  #endif
  #ifdef PROG_ENTITIES
    mat = MAT_DIFFUSE;
    if (entityId == ENT_LIGHTNING) {
        emissive = 1.0;
        emission = lin * 2.5;
    } else if (entityId == ENT_CAR) {
        mat = MAT_METAL_OUT;                                     // the wet old kei car: glossy paint
    }
  #endif
  #ifdef PROG_HAND
    cls = 1.0;
    mat = MAT_NONE;
    receiveShadow = false;
    int item = currentRenderedItemId;
    if (item == ITEM_FLASHLIGHT) {
        // the lens glows
        float lens = smoothstep(0.55, 0.85, luma(albedo.rgb) + 0.25 * chroma(albedo.rgb));
        emissive = lens;
        emission = lin * lens * FLASHLIGHT_COLOR * 3.0;
    } else if (item == ITEM_HANDLIGHT) {
        emissive = flameMask(albedo.rgb);
        emission = lin * emissive * 2.4;
    }
  #endif
    if (mat == MAT_FOLIAGE) twoSided = 1.0;

    // ---- wet ground after the rain ------------------------------------------------------
    float wet = 0.0;
    float puddle = 0.0;
  #if defined WORLD_HIDAMARI && defined WET_SURFACES && (defined PROG_TERRAIN || defined PROG_BLOCK || defined PROG_ENTITIES)
    #ifdef PROG_ENTITIES
    if (entityId == ENT_CAR) {
    #else
    if (mat != MAT_EMITTER && mat != MAT_NONE) {
    #endif
        wet = surfaceWetness(wp, Ngeo, lm.y, mat);
        if (wet > 0.0) {
            if (mat == MAT_DIFFUSE && wet > 0.5) mat = MAT_WET;
            puddle = puddleAmount(wp, mat, Ngeo) * wet;
            lin *= mix(1.0, wetDarkening(mat), wet);
            lin *= 1.0 - 0.25 * puddle;
            N = wetMicroNormal(N, wp, mat, puddle);
            N = dripRipples(N, wp, puddle);
        }
    }
  #endif

    float dither = ign(gl_FragCoord.xy);
    float glow = 0.0;
    vec3 color;

  #ifdef PROG_WATER
    if (id == BLK_WATER || id == BLK_PUDDLE) {
        // the puddles on the lot (and vanilla water): black mirrors; the reflection itself is
        // traced in composite1, here the dark body, the lamps' glints and the drip rings
        if (N.y > 0.5) {
            N = id == BLK_PUDDLE ? dripRipples(vec3(0.0, 1.0, 0.0), wp, 1.0) : waterRipples(wp);
        }
        float NdotV = saturate(dot(N, V));
        float F = 0.02 + 0.98 * pow(1.0 - NdotV, 5.0);
        float BODY = id == BLK_PUDDLE ? 0.05 : 0.18;
        vec3 body = shadeSurface(lin * BODY, N, playerPos, lm, 1.0, vec3(0.0), MAT_PUDDLE, 1.0, dither, true, 0.0, glow);
        body += handLightsForward(lin * BODY, playerPos, vec3(0.0, 1.0, 0.0), 0.0, vec2(1.0, -1.0));
        vec3 glints = handLightsForward(vec3(0.0), playerPos, N, 0.0, vec2(0.05, 0.02));
        color = body + glints;
    #ifndef REFLECTIONS
        vec3 R = reflect(-V, N);
        vec3 refl = (fogColorFor(R) * 0.7 + skyGradient(R) * 0.3 + moonReflection(R)) * smoothstep(0.30, 0.90, lm.y);
        color = mix(body, refl, F * 0.6) + glints;
    #endif
        alpha = id == BLK_PUDDLE ? mix(0.82, 0.97, F) : mix(0.66, 0.96, F);
        if (isEyeInWater == 1) alpha = 0.45;
        emissive = saturate(maxOf(glints) * 0.5);
        mat = MAT_PUDDLE;
    } else
  #endif
    {
  #if defined PROG_WATER || (defined PROG_BLOCK && defined TRANSLUCENT)
        if (mat == MAT_WINDOW) {
            // ---- shop windows: dark glass, rain drops, reflections (composite1) ----------
            vec2 pc = planeCoords(wp, Ngeo);
            float cover = 0.0;
    #ifdef GLASS_DROPLETS
            if (abs(Ngeo.y) < 0.5) {
                vec3 dr = rainDroplets(pc);
                cover = dr.z;
                vec3 a = abs(Ngeo);
                vec3 tu = a.x > a.z ? vec3(0.0, 0.0, 1.0) : vec3(1.0, 0.0, 0.0);
                N = normalize(Ngeo + (tu * dr.x + vec3(0.0, 1.0, 0.0) * dr.y) * 0.55 * cover);
            }
    #endif
            float NdotV = saturate(abs(dot(N, V)));
            float F = 0.04 + 0.96 * pow(1.0 - NdotV, 5.0);
            vec3 tint = mix(vec3(0.80, 0.88, 0.86), lin, 0.35);
            color = shadeSurface(tint * 0.05, N, playerPos, lm, 1.0, vec3(0.0), MAT_WINDOW, 0.0, dither, true, 0.0, glow);
            color += handLightsForward(vec3(0.0), playerPos, N, 0.0, vec2(0.04, 0.04)) * (0.3 + cover * 1.5);
            alpha = clamp(max(albedo.a * 0.55, 0.10) + F * 0.5 + cover * 0.12, 0.0, 0.85);
        } else if (mat == MAT_MIRROR) {
            // ---- the dark mirror: smoked glass over the cavity, smudges, cracks ------------
            vec2 pc = planeCoords(wp, Ngeo);
            float smudge = mirrorSmudge(pc);
            if (id == BLK_MIRROR_CRACKED) {
                // one impact in the middle of the mirror (3 wide over the sink counter)
                vec2 impact = vec2(floor(pc.x / 3.0) * 3.0 + 1.5, floor(pc.y / 2.0) * 2.0 + 1.15);
                float k = mirrorCracks(pc, impact);
                N = normalize(Ngeo + vec3(hash12(floor(pc * 23.0)) - 0.5, hash12(floor(pc * 23.0) + 3.1) - 0.5, 0.0) * 0.25 * k);
                emission += vec3(0.55, 0.62, 0.70) * k * 0.010;
            }
            vec3 tint = vec3(0.30, 0.36, 0.40);
            color = shadeSurface(tint * 0.10 * (1.0 + smudge), N, playerPos, lm, 1.0, emission, MAT_MIRROR, 0.0, dither, true, 0.0, glow);
            color += handLightsForward(vec3(0.0), playerPos, N, 0.0, vec2(0.06 + 0.4 * smudge, 0.40)) * 0.6;
            alpha = 0.50 + 0.12 * smudge;
        } else if (mat == MAT_GLASS_SMALL || id == BLK_GLASS) {
            float NdotV = saturate(abs(dot(N, V)));
            float F = 0.04 + 0.96 * pow(1.0 - NdotV, 5.0);
            color = shadeSurface(lin, N, playerPos, lm, ao, emission, mat, 0.0, dither, true, 0.0, glow);
            color += handLightsForward(lin, playerPos, N, 0.0, vec2(0.08, 0.04));
            alpha = clamp(albedo.a + F * 0.4, 0.0, 0.95);
        } else
  #endif
        {
            vec4 surf = materialSurface(mat, wet);
            vec2 gloss = vec2(mix(surf.x, 0.06, puddle), surf.w > 0.0 || surf.z > 0.5 ? surf.y : -1.0);
            color = shadeSurface(lin, N, playerPos, lm, ao, emission, mat, wet, dither, receiveShadow, twoSided, glow);
  #if defined TRANSLUCENT || defined PROG_WATER || defined PROG_TEXTURED || defined PROG_BASIC || defined PROG_HAND
            // no deferred pass for these: the hand lights and the flash are added right here
    #ifdef PROG_HAND
            color += handLightsForward(lin, playerPos, N, 0.0, vec2(1.0, -1.0)) * 0.35;
    #else
            color += handLightsForward(lin, playerPos, N, twoSided, gloss);
    #endif
  #endif
        }
    }

  #if defined PROG_ENTITIES && defined WORLD_HIDAMARI
    if (spirit) {
        // ---- the spirits -------------------------------------------------------------------
        // translucent (the renderer's alpha: what the naked eye sees), a pale rim, the edges
        // slowly dissolving; in the finder a cold white-blue glow
        float fm = finderMode();
        float vis = glcolor.a;
        float t = frameTimeCounter;
        float ndv = abs(dot(Ngeo, V));
        float rim = pow(1.0 - ndv, 2.2);
        float n = tnoise3(wp * 5.5 + vec3(0.0, -t * 0.35, t * 0.12)) * 0.65 + tnoise3(wp * 13.0 + t * 0.2) * 0.35;
        float edge = 0.18 + rim * 0.75;
        float keep = smoothstep(edge - 0.10, edge + 0.10, n + 0.25);
        float front = smoothstep(edge - 0.12, edge, n + 0.25) * (1.0 - keep);   // the dissolving edge
        vec3 pale = vec3(0.62, 0.72, 0.86);
        vec3 cold = vec3(0.55, 0.78, 1.00);
        // a ghost gives back little of the light that falls on it: the beam goes half through
        color *= 0.45;
        emission = pale * (rim * 0.030 + front * 0.06) * (0.4 + 0.6 * vis);
        if (fm > 0.5) {
            // the finder sees them: a cold glow strongest at the outline and the dissolving
            // edges, the figure itself pale and cold, its features still readable
            float g = rim * 0.85 + front * 1.2;
            vec3 inner = mix(lin, vec3(luma(lin)), 0.65) * cold;
            emission += cold * g * 0.30 + inner * (0.10 + 0.05 * sin(t * 1.7 + wp.y * 3.0)) + cold * 0.012;
            // in the finder the glow is what you see: light falling on it barely adds
            color = color * vec3(0.70, 0.85, 1.10) * 0.45;
        }
        color += emission;
    #ifndef TRANSLUCENT
        color += handLightsForward(lin, playerPos, Ngeo, 0.0, vec2(0.6, 0.03)) * 0.45;
    #endif
        alpha = vis * keep * (fm > 0.5 ? 1.0 : 0.92 + 0.08 * rim);
        emissive = saturate(maxOf(emission) * 3.0) * fm;
        mat = MAT_SPIRIT;
        // the mask for composite: haze where it stands (even unseen), chroma split, the trail
        float glowAmt = fm * (0.06 + 0.94 * rim * rim + front) * keep;
        spiritOut = vec4(1.0, glowAmt, vis, (1.0 - vis) * 0.85 + 0.15);
    }
  #endif

  #if defined WRITES_GBUFFER
    /* RENDERTARGETS: 0,1,5,6,8 */
    gl_FragData[0] = vec4(color, alpha);
    gl_FragData[1] = vec4(saturate(emissive), saturate(glow), cls, alpha);
    gl_FragData[2] = vec4(toSrgb(lin), float(mat) / 255.0);
    gl_FragData[3] = vec4(octEncode(N), lm.x, lm.y);
    gl_FragData[4] = spiritOut;
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
    // translucent entities (the spirits): leave the background's material and normal alone -
    // deferred still lights what is behind a faint spirit (scaled by its eye alpha)
    /* RENDERTARGETS: 0,1,8 */
    gl_FragData[0] = vec4(color, alpha);
    gl_FragData[1] = vec4(saturate(emissive), saturate(glow), cls, alpha);
    gl_FragData[2] = spiritOut;
  #else
    /* RENDERTARGETS: 0,1 */
    gl_FragData[0] = vec4(color, alpha);
    gl_FragData[1] = vec4(saturate(emissive), saturate(glow), cls, alpha);
  #endif
#endif
}
#endif
