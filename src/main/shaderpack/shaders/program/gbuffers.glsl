// =====================================================================================
//  SounanShaders - every lit gbuffers program
//  The stub selects the variant:
//    PROG_TERRAIN  PROG_WATER (translucent terrain)  PROG_BLOCK  PROG_ENTITIES  PROG_HAND
//    PROG_TEXTURED (particles)  PROG_BASIC (lines)  PROG_EYES  PROG_GLINT  PROG_BEACON
//    PROG_DAMAGE  PROG_WEATHER  PROG_CLOUDS        (+ TRANSLUCENT for blended variants)
//  Opaque surfaces are lit forward here (moon, sky, block light) and leave their albedo
//  and normal behind for the deferred pass, which adds the hand lights once per pixel.
//  Translucents (water, glass, the camera ghost), particles and the hand light themselves.
//    colortex0  linear HDR colour
//    colortex1  r emissive, g flame light, b class (1 hand, 0.5 cloud, 0.25 resists fog), a coverage
//    colortex5  albedo (sRGB) + a: 0 = no hand light here, 0.5 = foliage, 1 = lit surface
//    colortex6  octahedral world normal (rg), gloss strength (b), smoothness (a)
// =====================================================================================
#include "/lib/header.glsl"

#if (defined PROG_TERRAIN || defined PROG_BLOCK || defined PROG_ENTITIES) && !defined TRANSLUCENT
    #define WRITES_GBUFFER      // opaque: lit by the hand lights in the deferred pass
#endif
#if defined PROG_HAND && !defined TRANSLUCENT
    #define WRITES_GBUFFER_SKIP // the solid hand lights itself; deferred must leave it alone
#endif

#ifdef VERTEX_SHADER
#if defined PROG_TERRAIN || defined PROG_WATER
in vec4 mc_Entity;
in vec4 mc_midTexCoord;
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
    vec3 off = wavingOffset(matId, playerPos + cameraPosition, texcoord, mc_midTexCoord.xy, lmcoord.y);
    if (dot(off, off) > 0.0) {
        playerPos += off;
        viewPos.xyz = (gbufferModelView * vec4(playerPos, 1.0)).xyz;
    }
  #endif
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

// ripples of the mountain stream (world-space normal, +Y up): quick, broken, flowing
vec3 waterRipples(vec3 wp) {
    float t = frameTimeCounter;
#ifdef WORLD_MOUNTAIN
    float s = 0.060;
    vec2 flow = vec2(0.15, 0.55) * t;
#else
    float s = 0.045 * (1.0 + rainAmount());
    vec2 flow = vec2(0.0);
#endif
    vec2 p = wp.xz + flow;
    float dx = cos(p.x * 1.7 + t * 1.3) * 0.5 + cos(p.x * 3.3 - p.y * 1.9 + t * 2.1) * 0.3 + cos(p.x * 7.1 + p.y * 4.3 - t * 3.7) * 0.2;
    float dz = cos(p.y * 1.9 - t * 1.1) * 0.5 + cos(p.y * 3.1 + p.x * 1.4 + t * 1.8) * 0.3 + cos(p.y * 6.7 - p.x * 5.1 + t * 3.3) * 0.2;
    return normalize(vec3(-dx * s, 1.0, -dz * s));
}

void main() {
    float ao = 1.0;
#ifdef PROG_BASIC
    vec4 albedo = glcolor;
#else
    vec4 albedo = texture(gtexture, texcoord);
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

    // ---- programs that only overlay something --------------------------------------
#if defined PROG_DAMAGE
    /* RENDERTARGETS: 0 */
    gl_FragData[0] = albedo;
#elif defined PROG_GLINT
    /* RENDERTARGETS: 0 */
    gl_FragData[0] = vec4(toLinear(albedo.rgb) * 0.30, albedo.a);
#elif defined PROG_EYES
    // Entity eyes / headlamps (RenderType.eyes). Additive. In the mountain they are only a
    // faint glint in the dark - until the beam finds them: retro-reflection (eyeshine).
    vec3 lin = toLinear(albedo.rgb);
  #ifdef WORLD_MOUNTAIN
    vec3 eyes;
    if (entityId == ENT_YOBIGOE) {
        eyes = lin * vec3(1.0, 0.93, 0.82) * 5.0;              // the "rescuer"'s headlamp: a real lamp
    } else {
        vec3 tint = mix(lin, vec3(0.80, 0.95, 0.74), 0.62);     // the tapetum, not the texture, colours it
        vec3 shine = handLightEyeshine(playerPos);
        eyes = tint * (0.12 + shine * 0.55);
        eyes += tint * cameraMode() * 0.55;                     // IR: eyes blaze in night-vision footage
    }
  #else
    vec3 eyes = lin * 2.2 + mix(lin, vec3(0.9), 0.5) * handLightEyeshine(playerPos) * 0.4;
  #endif
    /* RENDERTARGETS: 0,1 */
    gl_FragData[0] = vec4(eyes, albedo.a);
    gl_FragData[1] = vec4(saturate(maxOf(eyes) * 2.0), 0.0, 0.0, albedo.a);
#elif defined PROG_BEACON
    /* RENDERTARGETS: 0,1 */
    gl_FragData[0] = vec4(toLinear(albedo.rgb) * 1.8, albedo.a);
    gl_FragData[1] = vec4(1.0, 0.0, 0.0, albedo.a);
#elif defined PROG_WEATHER
    // rain and snow - never in the mountain (there rainStrength is fear, not rain)
  #ifdef WORLD_MOUNTAIN
    discard;
  #endif
    vec3 lin = toLinear(albedo.rgb);
    vec3 light = skyAmbient() * (0.25 + 0.75 * lmcoord.y) * 1.6 + FLAME_DEEP * flameFalloff(lmcoord.x) * 0.6 + MIN_LIGHT;
    /* RENDERTARGETS: 0 */
    gl_FragData[0] = vec4(lin * light, albedo.a * 0.40);
#elif defined PROG_CLOUDS
    // no clouds over Kaerazu-yama: the sky is the canopy
  #ifdef WORLD_MOUNTAIN
    discard;
  #endif
    vec3 dirW = normalize(playerPos);
    vec3 lin = toLinear(albedo.rgb);
    vec3 c = lin * (skyAmbient() * 1.5 + celestialLight() * 0.35 * (0.5 + 0.5 * saturate(dot(dirW, lightDirW()))));
    c = mix(c, vec3(luma(c)), 0.35);
    /* RENDERTARGETS: 0,1 */
    gl_FragData[0] = vec4(c, albedo.a * 0.78);
    gl_FragData[1] = vec4(0.0, 0.0, 0.5, 1.0);
#else
    // ---- lit surfaces ----------------------------------------------------------------
    vec3 N = normalize(normalW);
    vec2 lm = lmcoord;
    int mat = matId;
    float emissive = 0.0;
    float cls = 0.0;
    bool receiveShadow = true;
    float alpha = albedo.a;

    vec3 lin = toLinear(albedo.rgb);
    vec3 emission = vec3(0.0);       // linear HDR light emitted by the surface itself

  #if defined PROG_TERRAIN || defined PROG_WATER
    if (mat == BLK_FLAME) {
        emissive = flameMask(albedo.rgb);
        emission = lin * emissive * 3.2;
    } else if (mat == BLK_LAMP) {
        emissive = paperMask(albedo.rgb) * 0.85;
        emission = lin * FLAME_HOT * emissive * 1.8;          // warm glass / sodium glow
    } else if (mat == BLK_GLOW) {
        emissive = 0.30 + 0.55 * luma(albedo.rgb);
        emission = lin * emissive * 1.6;
    } else if (mat == BLK_LAVA) {
        emissive = 0.90;
        emission = lin * 2.0;
    }
  #endif
  #if defined PROG_TEXTURED || defined PROG_BASIC
    // particles / lines are camera-facing: light them from every side
    N = normalize(-playerPos + vec3(0.0, 0.5, 0.0) * length(playerPos));
    receiveShadow = false;
    if (lm.x > 0.97) {                                           // flame / lava particles
        emissive = flameMask(albedo.rgb) * 0.8;
        emission = lin * emissive * 2.5;
    }
  #endif
  #ifdef PROG_ENTITIES
    if (entityId == ENT_UTSURU) {
        // the hiker only the camera sees: pale, faintly luminous, the fog cannot hide it
        cls = 0.25;
        float cam = cameraMode();
        emission = mix(vec3(luma(lin)), lin, 0.3) * vec3(0.85, 0.92, 1.0) * (0.03 + 0.20 * cam);
        emissive = 0.3 * cam;
    } else if (entityId == ENT_LIGHTNING) {
        emissive = 1.0;
        emission = lin * 2.5;
    }
  #endif
  #ifdef PROG_HAND
    cls = 1.0;
    mat = -1;
    receiveShadow = false;
    int item = currentRenderedItemId;
    if (item == ITEM_FLASHLIGHT || item == ITEM_FLASH_LOW || item == ITEM_PHONE_LIGHT) {
        // the lens / LED glows
        float lens = smoothstep(0.55, 0.85, luma(albedo.rgb) + 0.25 * chroma(albedo.rgb));
        float on = item == ITEM_FLASH_LOW ? 0.35 : 1.0;
        emissive = lens;
        emission = lin * lens * (item == ITEM_PHONE_LIGHT ? PHONE_COLOR * 2.0 : FLASHLIGHT_COLOR * 3.0 * on);
    } else if (item == ITEM_FLARE) {
        float hotT = flameMask(albedo.rgb) * max(redness(albedo.rgb), 0.35);
        emissive = hotT;
        emission = lin * hotT * vec3(1.0, 0.35, 0.25) * 4.0 * flareFlicker(frameTimeCounter);
    } else if (item == ITEM_CAMERA) {
        float scr = smoothstep(0.35, 0.70, luma(albedo.rgb));
        emissive = scr * 0.5;
        emission = lin * scr * 0.6;
    } else if (item == ITEM_HANDLIGHT) {
        emissive = flameMask(albedo.rgb);
        emission = lin * emissive * 2.4;
    }
  #endif

    float dither = ign(gl_FragCoord.xy);
    float glow = 0.0;
    vec3 color;
    vec2 gloss = surfaceGloss(mat, albedo.rgb);
    float foliage = (mat == BLK_PLANT || mat == BLK_LEAVES || mat == BLK_HANGING) ? 1.0 : 0.0;

  #ifdef PROG_WATER
    if (mat == BLK_WATER) {
        // the mountain stream: dark, cold, the beam glints on its ripples
        vec3 wp = playerPos + cameraPosition;
        if (N.y > 0.5) N = waterRipples(wp);
        vec3 V = -normalize(playerPos);
        float NdotV = saturate(dot(N, V));
        float F = 0.02 + 0.98 * pow(1.0 - NdotV, 5.0);
        vec3 R = reflect(-V, N);
        R.y = abs(R.y);
        float skyVis = smoothstep(0.30, 0.90, lm.y);
        vec3 refl = (fogColorFor(R) * 0.6 + skyGradient(R) * 0.4 + moonReflection(R)) * skyVis;
        vec3 body = shadeSurface(lin * 0.22, N, playerPos, lm, 1.0, vec3(0.0), mat, dither, true, glow);
        body += handLightsForward(lin * 0.22, playerPos, vec3(0.0, 1.0, 0.0), 0.0, vec2(0.0));
        // two specular lobes of the hand lights on the moving surface: sharp glints + sheen
        vec3 glints = handLightsForward(vec3(0.0), playerPos, N, 0.0, vec2(1.6, 0.92))
                    + handLightsForward(vec3(0.0), playerPos, N, 0.0, vec2(0.45, 0.55));
        color = mix(body, refl, F) + glints;
        alpha = mix(0.66, 0.94, F);
        if (isEyeInWater == 1) alpha = 0.45;
        emissive = saturate(maxOf(glints) * 0.5);
    } else
  #endif
    {
        color = shadeSurface(lin, N, playerPos, lm, ao, emission, mat, dither, receiveShadow, glow);
  #if defined TRANSLUCENT || defined PROG_WATER || defined PROG_TEXTURED || defined PROG_BASIC || defined PROG_HAND
        // no deferred pass for these: the hand lights are added right here
    #ifdef PROG_HAND
        // the hand holding the light only gets its spill (and a lit flare in the other hand)
        color += handLightsForward(lin, playerPos, N, 0.0, vec2(0.0)) * 0.35;
    #else
        color += handLightsForward(lin, playerPos, N, foliage, gloss);
    #endif
  #endif
  #ifdef PROG_WATER
        if (mat == BLK_GLASS) {
            vec3 V = -normalize(playerPos);
            float F = 0.04 + 0.96 * pow(1.0 - saturate(dot(N, V)), 5.0);
            vec3 R = reflect(-V, N);
            color = mix(color, (fogColorFor(R) + moonReflection(R)) * smoothstep(0.3, 0.9, lm.y), F * 0.6);
            color += handLightsForward(vec3(0.0), playerPos, N, 0.0, vec2(1.0, 0.95));
            alpha = max(alpha, F * 0.5);
        }
  #endif
    }

  #if defined WRITES_GBUFFER
    /* RENDERTARGETS: 0,1,5,6 */
    gl_FragData[0] = vec4(color, alpha);
    gl_FragData[1] = vec4(saturate(emissive), saturate(glow), cls, alpha);
    gl_FragData[2] = vec4(albedo.rgb, foliage > 0.5 ? 0.5 : 1.0);
    gl_FragData[3] = vec4(octEncode(N), saturate(gloss.x * 0.5), gloss.y);
  #elif defined WRITES_GBUFFER_SKIP
    /* RENDERTARGETS: 0,1,5,6 */
    gl_FragData[0] = vec4(color, alpha);
    gl_FragData[1] = vec4(saturate(emissive), saturate(glow), cls, alpha);
    gl_FragData[2] = vec4(0.0);
    gl_FragData[3] = vec4(octEncode(N), 0.0, 0.0);
  #else
    /* RENDERTARGETS: 0,1 */
    gl_FragData[0] = vec4(color, alpha);
    gl_FragData[1] = vec4(saturate(emissive), saturate(glow), cls, alpha);
  #endif
#endif
}
#endif
