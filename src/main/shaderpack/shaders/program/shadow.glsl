// =====================================================================================
//  HuntCraft Shaders - shadow map pass
// =====================================================================================
#include "/lib/header.glsl"

#ifdef VERTEX_SHADER
in vec4 mc_Entity;
in vec4 mc_midTexCoord;

out vec2 texcoord;
out vec4 glcolor;
flat out int blockId;

void main() {
    texcoord = (gl_TextureMatrix[0] * gl_MultiTexCoord0).xy;
    glcolor = gl_Color;
    blockId = int(mc_Entity.x + 0.5);
    vec2 lm = normalizeLightmap((gl_TextureMatrix[1] * gl_MultiTexCoord1).xy);

    vec4 viewPos = gl_ModelViewMatrix * gl_Vertex;
    vec3 playerPos = (shadowModelViewInverse * viewPos).xyz;
    playerPos += wavingOffset(blockId, playerPos, texcoord, mc_midTexCoord.xy, lm.y);
    viewPos = shadowModelView * vec4(playerPos, 1.0);

    gl_Position = gl_ProjectionMatrix * viewPos;
    gl_Position.xyz = distortShadow(gl_Position.xyz);
}
#endif

#ifdef FRAGMENT_SHADER
in vec2 texcoord;
in vec4 glcolor;
flat in int blockId;

void main() {
    vec4 tex = texture(gtexture, texcoord);
    if (tex.a < 0.1) discard;
    vec4 color = vec4(tex.rgb * glcolor.rgb, tex.a);
    if (blockId == BLK_WATER) {
        color = vec4(vec3(0.45, 0.70, 0.95), 0.6);
    } else if (blockId == BLK_ICE) {
        color = vec4(vec3(0.6, 0.8, 1.0), 0.5);
    }
    /* RENDERTARGETS: 0 */
    gl_FragData[0] = color;
}
#endif
