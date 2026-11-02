// =====================================================================================
//  MeikonShaders - vanilla sky geometry
//   PROG_SKYBASIC   : sky disc / sunset fan / void / stars. The gradient is painted by the
//                     deferred pass for every empty pixel, so only (dim) stars are kept,
//                     and none at all in Kakuriyo.
//   PROG_SKYTEXTURED: sun and moon (and the End sky box). In Kakuriyo the moon - vanilla or
//                     drawn by the mod's own sky renderer - is turned blood red, made a
//                     little larger and flagged emissive so the bloom gives it a glow.
//  Sky objects are written into the black-cleared colortex0 and added to the gradient by
//  the deferred pass.
// =====================================================================================
#include "/lib/header.glsl"

#ifdef VERTEX_SHADER
out vec2 texcoord;
out vec4 glcolor;

#ifdef WORLD_KAKURIYO
const float MOON_SCALE = 1.35;
#endif

void main() {
    texcoord = (gl_TextureMatrix[0] * gl_MultiTexCoord0).xy;
    glcolor = gl_Color;
    vec4 viewPos = gl_ModelViewMatrix * gl_Vertex;
#if defined PROG_SKYTEXTURED && defined WORLD_KAKURIYO && defined MC_RENDER_STAGE_MOON
    // vanilla moon quad: grow it around its centre (the moon direction)
    if (renderStage == MC_RENDER_STAGE_MOON && dot(moonPosition, moonPosition) > 1.0) {
        vec3 m = normalize(moonPosition);
        float d = dot(viewPos.xyz, m);
        viewPos.xyz = m * d + (viewPos.xyz - m * d) * MOON_SCALE;
    }
#endif
    gl_Position = gl_ProjectionMatrix * viewPos;
}
#endif

#ifdef FRAGMENT_SHADER
in vec2 texcoord;
in vec4 glcolor;

void main() {
#ifdef PROG_SKYBASIC
    bool keep = false;
    vec3 star = vec3(0.0);
  #if defined WORLD_OVERWORLD && defined MC_RENDER_STAGE_STARS
    if (renderStage == MC_RENDER_STAGE_STARS) {
        keep = true;
        float night = 1.0 - dayFactor();
        star = glcolor.rgb * glcolor.a * vec3(0.80, 0.86, 1.0) * 0.55 * night * (1.0 - rainAmount());
    }
  #endif
    if (!keep) discard;
    /* RENDERTARGETS: 0 */
    gl_FragData[0] = vec4(star, 1.0);
#endif

#ifdef PROG_SKYTEXTURED
    vec4 tex = texture(gtexture, texcoord);
  #if defined WORLD_KAKURIYO
    // vanilla fades sun / moon by (1 - rain level) through the vertex colour alpha; here the
    // rain level is the fear value, so ignore it - the moon must stay, fear or not.
    vec3 lin = toLinear(tex.rgb * glcolor.rgb);
    // brightness from the strongest channel so an already red moon texture stays bright
    float lum = maxOf(lin) * 0.7 + luma(lin) * 0.3;
    float a = tex.a * smoothstep(0.002, 0.03, lum);   // black / empty texels drop out
    // blood red: dark maria stay dark, the bright face glows. Green / blue stay tiny in
    // linear light, otherwise the tone curve turns the red into salmon.
    vec3 moon = vec3(1.00, 0.028, 0.018) * pow(lum, 0.85) * 2.4 + vec3(1.0, 0.10, 0.04) * pow(lum, 4.0) * 0.5;
    moon *= 1.0 + 0.25 * fear();
    /* RENDERTARGETS: 0,1 */
    gl_FragData[0] = vec4(moon, a);
    gl_FragData[1] = vec4(1.0, 0.0, 0.0, a);
  #elif defined WORLD_END
    vec3 lin = toLinear(tex.rgb * glcolor.rgb);
    /* RENDERTARGETS: 0 */
    gl_FragData[0] = vec4(lin * 0.12, tex.a * glcolor.a);
  #else
    vec3 lin = toLinear(tex.rgb * glcolor.rgb);
    float gain = 1.6;
    vec3 tint = vec3(0.80, 0.86, 1.0);           // cold moon
    #ifdef MC_RENDER_STAGE_SUN
    if (renderStage == MC_RENDER_STAGE_SUN) {
        gain = 5.0;
        tint = vec3(1.0, 0.94, 0.86);           // pale, sickly sun
    }
    #endif
    lin = mix(vec3(luma(lin)), lin, 0.4) * tint * gain;
    /* RENDERTARGETS: 0 */
    gl_FragData[0] = vec4(lin, tex.a * glcolor.a);
  #endif
#endif
}
#endif
