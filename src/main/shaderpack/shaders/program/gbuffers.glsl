// =====================================================================================
//  MeikonShaders - every lit gbuffers program (forward shading)
//  The stub selects the variant:
//    PROG_TERRAIN  PROG_WATER (translucent terrain)  PROG_BLOCK  PROG_ENTITIES  PROG_HAND
//    PROG_TEXTURED (particles)  PROG_BASIC (lines)  PROG_EYES  PROG_GLINT  PROG_BEACON
//    PROG_DAMAGE  PROG_WEATHER  PROG_CLOUDS        (+ TRANSLUCENT for blended variants)
//  Forward shading means it does not matter whether the loader draws a program before or
//  after the deferred pass: every pixel leaves here fully lit, in linear HDR.
//  colortex1 (aux): r emissive, g flame light reaching the surface,
//  b class (1 hand, 0.5 cloud, 0.25 the bride - resists the fog), a coverage (blending).
// =====================================================================================
#include "/lib/header.glsl"

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

// small slow ripples for water surfaces (world-space normal, +Y up)
vec3 waterRipples(vec3 wp) {
    float t = frameTimeCounter;
#ifdef WORLD_KAKURIYO
    float s = 0.018;     // the well is almost still
#else
    float s = 0.045 * (1.0 + rainAmount());
#endif
    float dx = cos(wp.x * 1.7 + t * 0.9) * 0.6 + cos(wp.x * 3.1 - wp.z * 1.3 + t * 1.4) * 0.4;
    float dz = cos(wp.z * 1.9 - t * 0.8) * 0.6 + cos(wp.z * 2.9 + wp.x * 1.1 + t * 1.2) * 0.4;
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
    // glowing eyes (spiders, endermen, any mod using the eyes render type)
    /* RENDERTARGETS: 0,1 */
    gl_FragData[0] = vec4(toLinear(albedo.rgb) * 2.2, albedo.a);
    gl_FragData[1] = vec4(1.0, 0.0, 0.0, albedo.a);
#elif defined PROG_BEACON
    /* RENDERTARGETS: 0,1 */
    gl_FragData[0] = vec4(toLinear(albedo.rgb) * 1.8, albedo.a);
    gl_FragData[1] = vec4(1.0, 0.0, 0.0, albedo.a);
#elif defined PROG_WEATHER
    // rain and snow - never in Kakuriyo (there rainStrength is fear, not rain)
  #ifdef WORLD_KAKURIYO
    discard;
  #endif
    vec3 lin = toLinear(albedo.rgb);
    vec3 light = skyAmbient() * (0.25 + 0.75 * lmcoord.y) * 1.6 + FLAME_DEEP * flameFalloff(lmcoord.x) * 0.6 + MIN_LIGHT;
    /* RENDERTARGETS: 0 */
    gl_FragData[0] = vec4(lin * light, albedo.a * 0.40);
#elif defined PROG_CLOUDS
    vec3 dirW = normalize(playerPos);
    vec3 lin = toLinear(albedo.rgb);
  #ifdef WORLD_KAKURIYO
    // dark silhouettes against the crimson haze, a faint cold rim toward the moon
    vec3 c = SKY_HORIZON * 0.25 + MOON_LIGHT * pow(saturate(dot(dirW, lightDirW())), 6.0) * 0.05;
  #else
    vec3 c = lin * (skyAmbient() * 1.5 + celestialLight() * 0.35 * (0.5 + 0.5 * saturate(dot(dirW, lightDirW()))));
    c = mix(c, vec3(luma(c)), 0.35);
  #endif
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
        emissive = paperMask(albedo.rgb) * 0.80;
        emission = lin * FLAME_HOT * emissive * 1.6;          // warm paper glow
    } else if (mat == BLK_GLOW) {
        emissive = 0.30 + 0.55 * luma(albedo.rgb);
        emission = lin * emissive * 1.6;
    } else if (mat == BLK_LAVA) {
        emissive = 0.90;
        emission = lin * 2.0;
    } else if (mat == BLK_TORII) {
        emissive = 0.25 + 0.75 * smoothstep(0.12, 0.60, luma(albedo.rgb));
        emission = lin * emissive * 0.9;
    } else if (mat == BLK_BELL) {
        emissive = goldMask(albedo.rgb) * 0.30;
        emission = lin * emissive * 2.0;
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
    if (entityId == ENT_BRIDE) {
        cls = 0.25;                                              // the fog cannot fully hide her
        emission = lin * vec3(0.80, 0.86, 1.0) * 0.04;           // a faint pale shape even in total darkness
    } else if (entityId == ENT_SHADOW) {
        emissive = smoothstep(0.55, 0.80, luma(albedo.rgb));     // two pale eyes
        emission = lin * emissive * 3.0;
    } else if (entityId == ENT_LIGHTNING) {
        emissive = 1.0;
        emission = lin * 2.5;
    }
  #endif
  #ifdef PROG_HAND
    cls = 1.0;
    mat = -1;                                                    // lit by the lightmap only, not by its own lantern
    receiveShadow = false;
    if (currentRenderedItemId == ITEM_CHOCHIN_LIT) {
        emissive = paperMask(albedo.rgb) * 0.85;
        emission = lin * FLAME_HOT * emissive * 1.1;
    } else if (currentRenderedItemId == ITEM_HANDLIGHT) {
        emissive = flameMask(albedo.rgb);
        emission = lin * emissive * 2.4;
    }
  #endif

    float dither = ign(gl_FragCoord.xy);
    float glow = 0.0;
    vec3 color;

  #ifdef PROG_WATER
    if (mat == BLK_WATER) {
        vec3 wp = playerPos + cameraPosition;
        if (N.y > 0.5) N = waterRipples(wp);
        vec3 V = -normalize(playerPos);
        float NdotV = saturate(dot(N, V));
        float F = 0.02 + 0.98 * pow(1.0 - NdotV, 5.0);
        vec3 R = reflect(-V, N);
        R.y = abs(R.y);
        float skyVis = smoothstep(0.30, 0.90, lm.y);
        vec3 refl = (fogColorFor(R) * 0.5 + skyGradient(R) * 0.5 + moonReflection(R)) * skyVis;
        // dark body: a little of the water colour, lit like any surface
        vec3 body = shadeSurface(lin * 0.30, N, playerPos, lm, 1.0, vec3(0.0), mat, dither, true, glow);
        color = mix(body, refl, F);
        alpha = mix(0.72, 0.96, F);
        if (isEyeInWater == 1) alpha = 0.45;
    } else
  #endif
    {
        color = shadeSurface(lin, N, playerPos, lm, ao, emission, mat, dither, receiveShadow, glow);
  #ifdef PROG_WATER
        if (mat == BLK_GLASS) {
            vec3 V = -normalize(playerPos);
            float F = 0.04 + 0.96 * pow(1.0 - saturate(dot(N, V)), 5.0);
            vec3 R = reflect(-V, N);
            color = mix(color, (fogColorFor(R) + moonReflection(R)) * smoothstep(0.3, 0.9, lm.y), F * 0.6);
            alpha = max(alpha, F * 0.5);
        }
  #endif
    }

    /* RENDERTARGETS: 0,1 */
    gl_FragData[0] = vec4(color, alpha);
    gl_FragData[1] = vec4(saturate(emissive), saturate(glow), cls, alpha);
#endif
}
#endif
