// =====================================================================================
//  HuntCraft Shaders - sky programs
//   PROG_SKYBASIC   : vanilla sky disc / sunset fan / void plane / stars.
//                     The sky itself is drawn procedurally in deferred.glsl, so only the
//                     stars are kept here (written as an additive layer into colortex0).
//   PROG_SKYTEXTURED: custom resource pack skies (vanilla sun/moon are disabled).
//   PROG_CLOUDS     : vanilla clouds, lit by sun + sky (forward, after deferred).
//   PROG_WEATHER    : rain / snow, forward lit.
// =====================================================================================
#include "/lib/header.glsl"

#ifdef VERTEX_SHADER
out vec2 texcoord;
out vec2 lmcoord;
out vec4 glcolor;
out vec3 playerPos;

void main() {
    texcoord = (gl_TextureMatrix[0] * gl_MultiTexCoord0).xy;
    lmcoord = (gl_TextureMatrix[1] * gl_MultiTexCoord1).xy;
    glcolor = gl_Color;
    vec3 viewPos = (gl_ModelViewMatrix * gl_Vertex).xyz;
    playerPos = (gbufferModelViewInverse * vec4(viewPos, 1.0)).xyz;
    gl_Position = gl_ProjectionMatrix * vec4(viewPos, 1.0);
}
#endif

#ifdef FRAGMENT_SHADER
in vec2 texcoord;
in vec2 lmcoord;
in vec4 glcolor;
in vec3 playerPos;

void main() {
#ifdef PROG_SKYBASIC
    // keep only the stars; everything else is replaced by the procedural sky
    #ifdef MC_RENDER_STAGE_STARS
    if (renderStage != MC_RENDER_STAGE_STARS) discard;
    #else
    discard;
    #endif
    float night = 1.0 - dayFactor();
    vec3 star = glcolor.rgb * glcolor.a * 1.6 * night * (1.0 - rainFactor());
    /* RENDERTARGETS: 0 */
    gl_FragData[0] = vec4(star, 1.0);
#endif

#ifdef PROG_SKYTEXTURED
    vec4 tex = texture(gtexture, texcoord) * glcolor;
    if (tex.a < 0.004) discard;
    /* RENDERTARGETS: 0 */
    gl_FragData[0] = vec4(srgbToLinear(tex.rgb) * 0.8, tex.a);
#endif

#ifdef PROG_CLOUDS
    vec4 tex = texture(gtexture, texcoord) * glcolor;
    if (tex.a < 0.01) discard;
    vec3 dirW = normalize(playerPos);
    float dist = length(playerPos);
    vec3 lightCol = shadowLightColor();
    vec3 amb = skyAmbientColor();
    float vdl = saturate(dot(dirW, lightDirWorld()));
    vec3 cloud = amb * 1.6 + lightCol * (0.55 + 0.45 * pow(vdl, 3.0));
    cloud *= srgbToLinear(tex.rgb) * 1.2;
    cloud = mix(cloud, fogColorFor(dirW), saturate(1.0 - exp(-dist * baseFogDensity() * 0.8)));
    /* RENDERTARGETS: 0 */
    gl_FragData[0] = vec4(cloud, tex.a * 0.7);
#endif

#ifdef PROG_WEATHER
    vec4 tex = texture(gtexture, texcoord) * glcolor;
    if (tex.a < 0.01) discard;
    vec2 lm = normalizeLightmap(lmcoord);
    vec3 amb = skyAmbientColor() * (lm.y * 0.8 + 0.2) + blockLightColor(lm.x, playerPos) * 0.5;
    vec3 lightCol = shadowLightColor() * lm.y * 0.35;
    vec3 c = srgbToLinear(tex.rgb) * (amb * 1.8 + lightCol);
    /* RENDERTARGETS: 0 */
    gl_FragData[0] = vec4(c, tex.a * 0.55);
#endif
}
#endif
