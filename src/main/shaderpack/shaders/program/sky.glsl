// =====================================================================================
//  HitoriShaders - vanilla sky geometry
//   PROG_SKYBASIC   : sky disc / sunset fan / void / stars. The gradient is painted by the
//                     deferred pass for every empty pixel, so only (dim) stars are kept -
//                     none over the overcast restaurant.
//   PROG_SKYTEXTURED: sun and moon. None over the restaurant (overcast, no moon disc);
//                     elsewhere a cold moon and a pale, joyless sun.
//  Sky objects are written into the black-cleared colortex0 and added to the gradient by
//  the deferred pass.
// =====================================================================================
#include "/lib/header.glsl"

#ifdef VERTEX_SHADER
out vec2 texcoord;
out vec4 glcolor;

void main() {
    texcoord = (gl_TextureMatrix[0] * gl_MultiTexCoord0).xy;
    glcolor = gl_Color;
    gl_Position = gl_ProjectionMatrix * (gl_ModelViewMatrix * gl_Vertex);
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
  #if defined WORLD_HIDAMARI
    // the clouds hide the moon (and there is no sun at 18000)
    discard;
  #endif
    vec4 tex = texture(gtexture, texcoord);
  #if defined WORLD_END
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
