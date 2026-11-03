// =====================================================================================
//  ReisatsuShaders - gentle bloom blur
//   BLOOM_H (composite2): horizontal 9 taps of colortex3 at mip 2 -> colortex4
//   BLOOM_V (composite3): vertical 9 taps of colortex4            -> colortex3
// =====================================================================================
#include "/lib/header.glsl"

#ifdef VERTEX_SHADER
out vec2 texcoord;
void main() {
    gl_Position = ftransform();
    texcoord = (gl_TextureMatrix[0] * gl_MultiTexCoord0).xy;
}
#endif

#ifdef FRAGMENT_SHADER
in vec2 texcoord;

#ifdef BLOOM_H
const bool colortex3MipmapEnabled = true;
#endif

const float W0 = 0.2270270;
const float W1 = 0.1945946;
const float W2 = 0.1216216;
const float W3 = 0.0540540;
const float W4 = 0.0162162;

void main() {
#ifdef BLOOM_H
    vec2 px = vec2(4.0 / viewWidth, 0.0);
    vec3 s = textureLod(colortex3, texcoord, 2.0).rgb * W0;
    s += (textureLod(colortex3, texcoord + px, 2.0).rgb + textureLod(colortex3, texcoord - px, 2.0).rgb) * W1;
    s += (textureLod(colortex3, texcoord + px * 2.0, 2.0).rgb + textureLod(colortex3, texcoord - px * 2.0, 2.0).rgb) * W2;
    s += (textureLod(colortex3, texcoord + px * 3.0, 2.0).rgb + textureLod(colortex3, texcoord - px * 3.0, 2.0).rgb) * W3;
    s += (textureLod(colortex3, texcoord + px * 4.0, 2.0).rgb + textureLod(colortex3, texcoord - px * 4.0, 2.0).rgb) * W4;
    /* RENDERTARGETS: 4 */
    gl_FragData[0] = vec4(s, 1.0);
#endif
#ifdef BLOOM_V
    vec2 px = vec2(0.0, 4.0 / viewHeight);
    vec3 s = texture(colortex4, texcoord).rgb * W0;
    s += (texture(colortex4, texcoord + px).rgb + texture(colortex4, texcoord - px).rgb) * W1;
    s += (texture(colortex4, texcoord + px * 2.0).rgb + texture(colortex4, texcoord - px * 2.0).rgb) * W2;
    s += (texture(colortex4, texcoord + px * 3.0).rgb + texture(colortex4, texcoord - px * 3.0).rgb) * W3;
    s += (texture(colortex4, texcoord + px * 4.0).rgb + texture(colortex4, texcoord - px * 4.0).rgb) * W4;
    /* RENDERTARGETS: 3 */
    gl_FragData[0] = vec4(s, 1.0);
#endif
}
#endif
