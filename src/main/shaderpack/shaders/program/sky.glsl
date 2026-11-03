// =====================================================================================
//  ReisatsuShaders - vanilla sky geometry
//   PROG_SKYBASIC   : sky disc / sunset fan / void / stars. The gradient is painted by the
//                     deferred pass for every empty pixel, so only (dim) stars are kept,
//                     and none at all over the mountain (the fog and the canopy hide them).
//   PROG_SKYTEXTURED: sun and moon. Over the mountain the moon - vanilla or drawn by the mod -
//                     is a cold, pale full moon, a little larger, flagged emissive so the
//                     bloom gives it a halo when it shows through a gap in the canopy.
//  Sky objects are written into the black-cleared colortex0 and added to the gradient by
//  the deferred pass.
// =====================================================================================
#include "/lib/header.glsl"

#ifdef VERTEX_SHADER
out vec2 texcoord;
out vec4 glcolor;

#ifdef WORLD_MOUNTAIN
const float MOON_SCALE = 1.35;
#endif

void main() {
    texcoord = (gl_TextureMatrix[0] * gl_MultiTexCoord0).xy;
    glcolor = gl_Color;
    vec4 viewPos = gl_ModelViewMatrix * gl_Vertex;
#if defined PROG_SKYTEXTURED && defined WORLD_MOUNTAIN && defined MC_RENDER_STAGE_MOON
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
  #if defined WORLD_MOUNTAIN && defined MC_RENDER_STAGE_MOON
    #ifdef MC_RENDER_STAGE_SUN
    if (renderStage == MC_RENDER_STAGE_SUN) discard;      // midnight forever: no sun
    #endif
    if (renderStage == MC_RENDER_STAGE_MOON) {
        // Always a full moon (the vanilla texture follows the moon phase of the day count):
        // a soft cold face with darker maria, a brighter limb and a pale halo.
        vec2 p = fract(texcoord * vec2(4.0, 2.0)) - 0.5;     // inside the phase cell
        float r = length(p);
        const float R = 0.215;
        float aa = fwidth(r) * 1.5 + 0.002;
        float disc = 1.0 - smoothstep(R - aa, R + aa, r);
        vec2 sp = p / R;
        float maria = smoothstep(0.42, 0.72, fbm2(sp * 2.3 + vec2(3.1, 7.7)));
        float crater = smoothstep(0.62, 0.80, vnoise2(sp * 9.0 + vec2(1.3, 2.9)));
        float limb = sqrt(saturate(1.0 - dot(sp, sp)));
        float face = (0.60 + 0.40 * limb) * (1.0 - 0.42 * maria) * (1.0 - 0.15 * crater);
        float halo = exp(-max(r - R, 0.0) * 22.0) * smoothstep(0.50, 0.30, r) * (1.0 - disc);
        vec3 moon = vec3(0.78, 0.86, 1.00) * face * 1.6 * disc
                  + vec3(0.55, 0.65, 0.85) * halo * 0.22;
        moon *= 1.0 - 0.35 * fear();
        float a = saturate(disc + halo);
        /* RENDERTARGETS: 0,1 */
        gl_FragData[0] = vec4(moon, a);
        gl_FragData[1] = vec4(disc, 0.0, 0.0, a);
        return;
    }
  #endif
  #if defined WORLD_MOUNTAIN
    // a custom sky renderer's moon: vanilla fades sun / moon by (1 - rain level) through the
    // vertex alpha - here the rain level is fear, so ignore it: the moon stays.
    vec3 lin = toLinear(tex.rgb * glcolor.rgb);
    float lum = maxOf(lin) * 0.7 + luma(lin) * 0.3;
    float a = tex.a * smoothstep(0.002, 0.03, lum);
    vec3 moon = vec3(0.78, 0.86, 1.00) * pow(lum, 0.9) * 1.6 * (1.0 - 0.35 * fear());
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
