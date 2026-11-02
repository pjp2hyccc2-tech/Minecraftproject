// =====================================================================================
//  MeikonShaders - shadow map pass (moon / sun). Translucents are not drawn here
//  (shadowTranslucent=false), so water, glass and the torii portal cast no shadow.
// =====================================================================================
#define PROGRAM_SHADOW
#include "/lib/header.glsl"

#ifdef VERTEX_SHADER
in vec4 mc_Entity;
in vec4 mc_midTexCoord;

out vec2 texcoord;
out vec4 glcolor;

void main() {
    texcoord = (gl_TextureMatrix[0] * gl_MultiTexCoord0).xy;
    glcolor = gl_Color;
    vec4 viewPos = gl_ModelViewMatrix * gl_Vertex;
#ifdef WAVING_PLANTS
    int id = int(mc_Entity.x + 0.5);
    if (id == BLK_PLANT || id == BLK_LEAVES || id == BLK_HANGING) {
        vec3 playerPos = (shadowModelViewInverse * viewPos).xyz;
        float sky = normalizeLightmap((gl_TextureMatrix[1] * gl_MultiTexCoord1).xy).y;
        playerPos += wavingOffset(id, playerPos + cameraPosition, texcoord, mc_midTexCoord.xy, sky);
        viewPos = shadowModelView * vec4(playerPos, 1.0);
    }
#endif
    gl_Position = gl_ProjectionMatrix * viewPos;
    gl_Position.xyz = distortShadowClip(gl_Position.xyz);
}
#endif

#ifdef FRAGMENT_SHADER
in vec2 texcoord;
in vec4 glcolor;

void main() {
    vec4 c = texture(gtexture, texcoord) * glcolor;
    if (c.a < 0.1) discard;
    /* RENDERTARGETS: 0 */
    gl_FragData[0] = c;
}
#endif
