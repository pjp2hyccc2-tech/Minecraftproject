// =====================================================================================
//  HuntCraft Shaders - translucent terrain (water, stained glass, ice ...) - forward lit
//  writes: colortex0 (blended colour), colortex1 (normal + lightmap, no blend),
//          colortex2 (material, no blend) so composite can do reflections / water fog
// =====================================================================================
#include "/lib/header.glsl"

#ifdef VERTEX_SHADER
in vec4 mc_Entity;
in vec4 mc_midTexCoord;

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
    blockId = int(mc_Entity.x + 0.5);
    vec3 viewPos = (gl_ModelViewMatrix * gl_Vertex).xyz;
    playerPos = (gbufferModelViewInverse * vec4(viewPos, 1.0)).xyz;
    normalW = normalize(mat3(gbufferModelViewInverse) * (gl_NormalMatrix * gl_Normal));
    if (blockId == BLK_WATER && normalW.y > 0.5) {
        playerPos.y += waterWaveHeight(playerPos + cameraPosition, frameTimeCounter);
    }
    viewPos = (gbufferModelView * vec4(playerPos, 1.0)).xyz;
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

void main() {
    vec4 tex = texture(gtexture, texcoord);
    vec4 albedo = vec4(tex.rgb * glcolor.rgb, tex.a);
    vec2 lm = normalizeLightmap(lmcoord);
    vec3 N = normalize(normalW);
    vec3 V = -normalize(playerPos);
    float dither = bayer16(gl_FragCoord.xy);
    vec3 wp = playerPos + cameraPosition;
    int mat = MAT_GLASS;
    vec3 color;
    float alpha = albedo.a;

    if (blockId == BLK_WATER) {
        mat = MAT_WATER;
        if (abs(N.y) > 0.5) {
            vec3 wn = waterNormal(wp, frameTimeCounter, 1.0);
            N = normalize(vec3(wn.x, wn.y * sign(N.y), wn.z));
        }
        float NdotV = saturate(dot(N, V));
        float fresnel = 0.03 + 0.97 * pow(1.0 - NdotV, 5.0);
        vec3 waterCol = srgbToLinear(glcolor.rgb) * (0.55 + 0.45 * luma(tex.rgb)) * 0.85;
        vec3 shadow;
        vec3 direct;
        color = shadeSurfaceEx(waterCol, N, playerPos, lm, mat, 0.0, 0.0, 1.0, 1.0, dither, true, shadow, direct);
        // sun glints
        vec3 L = lightDirWorld();
        vec3 H = normalize(L + V);
        float spec = pow(saturate(dot(N, H)), 260.0) * 2.5 + pow(saturate(dot(N, H)), 32.0) * 0.12;
        color += direct * spec * (0.2 + 0.8 * fresnel);
        alpha = mix(0.50, 0.92, fresnel);
        if (isEyeInWater == 1) alpha = 0.35;
    } else {
        bool ice = blockId == BLK_ICE;
        if (ice) mat = MAT_ICE;
        vec3 lin = srgbToLinear(albedo.rgb);
        vec3 shadow;
        vec3 direct;
        color = shadeSurfaceEx(lin, N, playerPos, lm, mat, 0.0, ice ? 0.3 : 0.0, 1.0, 1.0, dither, true, shadow, direct);
        vec3 L = lightDirWorld();
        vec3 H = normalize(L + V);
        float NdotV = saturate(dot(N, V));
        float fresnel = 0.04 + 0.96 * pow(1.0 - NdotV, 5.0);
        float spec = pow(saturate(dot(N, H)), 180.0) * 1.2;
        color += direct * spec * fresnel;
        alpha = max(alpha, fresnel * 0.5);
    }
    if (alpha < 0.004) discard;

    /* RENDERTARGETS: 0,1,2 */
    gl_FragData[0] = vec4(color, alpha);
    gl_FragData[1] = vec4(encodeNormal(N), lm.x, lm.y);
    gl_FragData[2] = vec4(packMat(mat), 0.0, 0.0, 1.0);
}
#endif
