// =====================================================================================
//  HuntCraft Shaders - generic gbuffer program
//  Selected by defines from the program stubs:
//    PROG_TERRAIN  PROG_BLOCK  PROG_ENTITIES  PROG_HAND  PROG_TEXTURED  PROG_SPIDEREYES
//    PROG_BEACON   PROG_GLINT  PROG_BASIC
//  FORWARD : the program runs after the deferred pass (translucents) and lights itself.
//  Without FORWARD it writes the gbuffer (albedo / normal+lightmap / material) for deferred.glsl
// =====================================================================================
#include "/lib/header.glsl"

#ifdef VERTEX_SHADER
#ifdef PROG_TERRAIN
in vec4 mc_Entity;
in vec4 mc_midTexCoord;
#endif
#ifdef USE_NORMAL_MAP
in vec4 at_tangent;
out vec4 tangentW;
#endif

out vec2 texcoord;
out vec2 lmcoord;
out vec4 glcolor;
out vec3 normalW;
out vec3 playerPos;
flat out int blockId;

void main() {
    texcoord = (gl_TextureMatrix[0] * gl_MultiTexCoord0).xy;
    lmcoord = (gl_TextureMatrix[1] * gl_MultiTexCoord1).xy;
    glcolor = gl_Color;
    vec3 viewPos = (gl_ModelViewMatrix * gl_Vertex).xyz;
    playerPos = (gbufferModelViewInverse * vec4(viewPos, 1.0)).xyz;
    blockId = 0;
#ifdef PROG_TERRAIN
    blockId = int(mc_Entity.x + 0.5);
    vec3 off = wavingOffset(blockId, playerPos, texcoord, mc_midTexCoord.xy, normalizeLightmap(lmcoord).y);
    playerPos += off;
    viewPos = (gbufferModelView * vec4(playerPos, 1.0)).xyz;
#endif
#ifdef PROG_TEXTURED
    normalW = normalize(mat3(gbufferModelViewInverse) * vec3(0.0, 0.0, 1.0));
#else
    normalW = normalize(mat3(gbufferModelViewInverse) * (gl_NormalMatrix * gl_Normal));
#endif
#ifdef USE_NORMAL_MAP
    vec3 tW = mat3(gbufferModelViewInverse) * (gl_NormalMatrix * at_tangent.xyz);
    tangentW = vec4(tW, at_tangent.w);
#endif
    gl_Position = gl_ProjectionMatrix * vec4(viewPos, 1.0);
}
#endif

#ifdef FRAGMENT_SHADER
in vec2 texcoord;
in vec2 lmcoord;
in vec4 glcolor;
in vec3 normalW;
in vec3 playerPos;
flat in int blockId;
#ifdef USE_NORMAL_MAP
in vec4 tangentW;
#endif

void main() {
#ifdef PROG_BASIC
    vec4 albedo = glcolor;
#else
    vec4 albedo = texture(gtexture, texcoord);
    albedo.rgb *= glcolor.rgb;
    #ifndef PROG_TERRAIN
    albedo.a *= glcolor.a;
    #endif
#endif
#ifdef PROG_ENTITIES
    albedo.rgb = mix(albedo.rgb, entityColor.rgb, entityColor.a);
#endif
#if defined PROG_SPIDEREYES || defined PROG_GLINT || defined PROG_BEACON
    if (albedo.a < 0.004) discard;
#else
    if (albedo.a < max(alphaTestRef, 0.01)) discard;
#endif

    vec2 lm = normalizeLightmap(lmcoord);
    vec3 N = normalize(normalW);
    float ao = 1.0;
    int mat = MAT_TERRAIN;
    float emissive = 0.0;
    float sss = 0.0;

#ifdef PROG_TERRAIN
    ao = glcolor.a;
    if (isFoliageId(blockId)) {
        mat = MAT_FOLIAGE;
        sss = (blockId == BLK_LEAVES) ? 0.5 : 0.75;
    } else if (blockId == BLK_SNOW) {
        mat = MAT_SNOW;
        sss = 0.15;
    } else if (blockId == BLK_LAVA) {
        mat = MAT_LAVA;
        emissive = 0.85 + 0.15 * luma(albedo.rgb);
    } else {
        float be = blockEmissive(blockId);
        if (be > 0.0) {
            mat = MAT_EMISSIVE;
            emissive = (blockId == BLK_GLOWBLOCK) ? be * (0.45 + 0.55 * luma(albedo.rgb)) : textureEmissiveMask(albedo.rgb, be);
        }
    }
#endif
#ifdef PROG_BLOCK
    mat = MAT_TERRAIN;
    if (lm.x > 0.985 && lm.y > 0.985) emissive = 0.6 * luma(albedo.rgb);
#endif
#ifdef PROG_ENTITIES
    mat = MAT_ENTITY;
    if (entityId == ENT_LIGHTNING) {
        mat = MAT_GLOW;
        emissive = 1.0;
        albedo.rgb = vec3(0.80, 0.88, 1.0);
        albedo.a = max(albedo.a, 0.7);
    } else if (entityId == ENT_PROJECTILE) {
        emissive = 0.9;
    } else if (lm.x > 0.985 && lm.y > 0.985) {
        emissive = 0.7 * (0.3 + 0.7 * luma(albedo.rgb));
    }
    #ifdef USE_SPECULAR_MAP
    {
        vec4 spec = texture(specular, texcoord);
        float e = spec.a < 1.0 ? spec.a * (255.0 / 254.0) : 0.0;
        emissive = max(emissive, e);
    }
    #endif
#endif
#ifdef PROG_HAND
    mat = MAT_HAND;
    if (currentRenderedItemId == ITM_TORCHLIKE || currentRenderedItemId == ITM_GLOWBLOCK) {
        emissive = textureEmissiveMask(albedo.rgb, 1.0);
    }
    #ifdef USE_SPECULAR_MAP
    {
        vec4 spec = texture(specular, texcoord);
        float e = spec.a < 1.0 ? spec.a * (255.0 / 254.0) : 0.0;
        emissive = max(emissive, e);
    }
    #endif
#endif
#ifdef PROG_TEXTURED
    mat = MAT_PARTICLE;
    if (lm.x > 0.985) emissive = 0.9 * (0.35 + 0.65 * luma(albedo.rgb));
#endif
#ifdef PROG_SPIDEREYES
    mat = MAT_GLOW;
    emissive = 1.0;
#endif
#ifdef PROG_BEACON
    mat = MAT_GLOW;
    emissive = 1.0;
#endif
#ifdef PROG_GLINT
    mat = MAT_ENTITY;
    emissive = 0.6;
#endif

#if defined USE_NORMAL_MAP && (defined PROG_TERRAIN || defined PROG_ENTITIES || defined PROG_HAND || defined PROG_BLOCK)
    if (dot(tangentW.xyz, tangentW.xyz) > 0.01) {
        vec3 nm = texture(normals, texcoord).xyz * 2.0 - 1.0;
        nm.z = sqrt(saturate(1.0 - dot(nm.xy, nm.xy)));
        vec3 T = normalize(tangentW.xyz);
        vec3 B = normalize(cross(N, T)) * (tangentW.w < 0.0 ? -1.0 : 1.0);
        mat3 tbn = mat3(T, B, N);
        vec3 mapped = normalize(tbn * nm);
        if (!any(isnan(mapped))) N = mapped;
    }
#endif

#ifdef FORWARD
    vec3 lin = srgbToLinear(albedo.rgb);
    float dither = bayer16(gl_FragCoord.xy);
    vec3 color = shadeSurface(lin, N, playerPos, lm, mat, emissive, sss, ao, 1.0, dither, mat != MAT_HAND);
    /* RENDERTARGETS: 0 */
    gl_FragData[0] = vec4(color, albedo.a);
#else
    #if defined PROG_GLINT
    /* RENDERTARGETS: 0 */
    gl_FragData[0] = vec4(albedo.rgb * 0.6, albedo.a);
    #else
    /* RENDERTARGETS: 0,1,2 */
    gl_FragData[0] = albedo;
    gl_FragData[1] = vec4(encodeNormal(N), lm.x, lm.y);
    gl_FragData[2] = vec4(packMat(mat), emissive, sss, ao);
    #endif
#endif
}
#endif
