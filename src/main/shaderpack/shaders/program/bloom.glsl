// =====================================================================================
//  HuntCraft Shaders - bloom
//   BLOOM_EXTRACT (composite1): bright pass + emissive mask -> colortex3
//   BLOOM_BLUR_H  (composite2): horizontal blur of colortex3 (mip 2) -> colortex4
//   BLOOM_BLUR_V  (composite3): vertical blur of colortex4 -> colortex3
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

#ifdef BLOOM_BLUR_H
const bool colortex3MipmapEnabled = true;
#endif

const float BLUR_W0 = 0.2270270;
const float BLUR_W1 = 0.1945946;
const float BLUR_W2 = 0.1216216;
const float BLUR_W3 = 0.0540540;
const float BLUR_W4 = 0.0162162;

void main() {
#ifdef BLOOM_EXTRACT
    vec3 c = texture(colortex0, texcoord).rgb;
    vec4 data2 = texture(colortex2, texcoord);
    float l = luma(c);
    vec3 bright = c * smoothstep(0.9, 3.0, l) * 0.8;
    bright += c * data2.g * 1.6 * EMISSIVE_BOOST;
    bright += max(c - vec3(2.5), vec3(0.0)) * 0.5;
    /* RENDERTARGETS: 3 */
    gl_FragData[0] = vec4(bright, 1.0);
#endif

#ifdef BLOOM_BLUR_H
    vec2 px = vec2(1.0 / viewWidth, 0.0) * 6.0;
    vec3 sum = textureLod(colortex3, texcoord, 2.0).rgb * BLUR_W0;
    sum += (textureLod(colortex3, texcoord + px * 1.0, 2.0).rgb + textureLod(colortex3, texcoord - px * 1.0, 2.0).rgb) * BLUR_W1;
    sum += (textureLod(colortex3, texcoord + px * 2.0, 2.0).rgb + textureLod(colortex3, texcoord - px * 2.0, 2.0).rgb) * BLUR_W2;
    sum += (textureLod(colortex3, texcoord + px * 3.0, 2.0).rgb + textureLod(colortex3, texcoord - px * 3.0, 2.0).rgb) * BLUR_W3;
    sum += (textureLod(colortex3, texcoord + px * 4.0, 2.0).rgb + textureLod(colortex3, texcoord - px * 4.0, 2.0).rgb) * BLUR_W4;
    /* RENDERTARGETS: 4 */
    gl_FragData[0] = vec4(sum, 1.0);
#endif

#ifdef BLOOM_BLUR_V
    vec2 px = vec2(0.0, 1.0 / viewHeight) * 6.0;
    vec3 sum = texture(colortex4, texcoord).rgb * BLUR_W0;
    sum += (texture(colortex4, texcoord + px * 1.0).rgb + texture(colortex4, texcoord - px * 1.0).rgb) * BLUR_W1;
    sum += (texture(colortex4, texcoord + px * 2.0).rgb + texture(colortex4, texcoord - px * 2.0).rgb) * BLUR_W2;
    sum += (texture(colortex4, texcoord + px * 3.0).rgb + texture(colortex4, texcoord - px * 3.0).rgb) * BLUR_W3;
    sum += (texture(colortex4, texcoord + px * 4.0).rgb + texture(colortex4, texcoord - px * 4.0).rgb) * BLUR_W4;
    /* RENDERTARGETS: 3 */
    gl_FragData[0] = vec4(sum, 1.0);
#endif
}
#endif
