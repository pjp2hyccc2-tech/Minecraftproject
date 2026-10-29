// =====================================================================================
//  HuntCraft Shaders - composite: water reflections, water depth tint, fog, light shafts
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

#ifdef WATER_REFLECTIONS
// screen space reflection against the opaque depth buffer. Returns rgb + hit confidence in a.
vec4 screenSpaceReflection(vec3 viewPos, vec3 Nv, float dither) {
    vec3 V = normalize(viewPos);
    vec3 R = normalize(reflect(V, Nv));
    float dist = length(viewPos);
    float stepLen = 0.15 + dist * 0.03;
    vec3 p = viewPos + Nv * 0.02 + R * stepLen * dither;
    for (int i = 0; i < SSR_STEPS; i++) {
        p += R * stepLen;
        vec3 sp = viewToScreen(p);
        if (sp.x < 0.0 || sp.x > 1.0 || sp.y < 0.0 || sp.y > 1.0 || sp.z >= 1.0) break;
        float sd = texture(depthtex1, sp.xy).r;
        if (sd >= 1.0) continue;
        vec3 sv = screenToView(sp.xy, sd);
        float dz = sv.z - p.z;
        if (dz > 0.0 && dz < stepLen * 2.5 + 0.1) {
            float edge = smoothstep(0.0, 0.12, min(sp.x, 1.0 - sp.x)) * smoothstep(0.0, 0.12, min(sp.y, 1.0 - sp.y));
            return vec4(texture(colortex0, sp.xy).rgb, edge);
        }
        stepLen *= 1.22;
    }
    return vec4(0.0);
}
#endif

void main() {
    vec3 color = texture(colortex0, texcoord).rgb;
    float depth0 = texture(depthtex0, texcoord).r;
    float depth1 = texture(depthtex1, texcoord).r;
    vec4 data2 = texture(colortex2, texcoord);
    int mat = unpackMat(data2.r);
    bool sky = depth0 >= 1.0;
    float dither = bayer64(gl_FragCoord.xy);

    vec3 viewPos = screenToView(texcoord, sky ? 1.0 : depth0);
    vec3 playerPos = viewToPlayer(viewPos);
    vec3 dirW = normalize(playerPos);
    float dist = sky ? 4000.0 : length(playerPos);

    // ---- water: reflections + depth tint ------------------------------------------------
    if (!sky && mat == MAT_WATER && isEyeInWater == 0) {
        vec4 data1 = texture(colortex1, texcoord);
        vec3 N = decodeNormal(data1.xy);
        float NdotV = saturate(dot(N, -dirW));
        float fresnel = 0.03 + 0.97 * pow(1.0 - NdotV, 5.0);

        // colour of what is below the surface, by how thick the water is
        vec3 behindPos = viewToPlayer(screenToView(texcoord, depth1));
        float thickness = max(length(behindPos) - dist, 0.0);
        vec3 deep = DIM_WATER_COLOR * skyAmbientColor() * 2.2 * (0.3 + 0.7 * data1.w);
        color = mix(color, deep, 1.0 - exp(-thickness * 0.10));

        vec3 Rw = reflect(dirW, N);
        Rw.y = max(Rw.y, 0.02);
        vec3 reflSky = fogColorFor(Rw) * 0.5 + skyGradient(Rw) * 0.5 + skyBodies(Rw) * 0.6;
        reflSky *= 0.35 + 0.65 * data1.w;
        vec3 refl = reflSky;
#ifdef WATER_REFLECTIONS
        vec3 Nv = normalize(mat3(gbufferModelView) * N);
        vec4 ssr = screenSpaceReflection(viewPos, Nv, dither);
        refl = mix(reflSky, ssr.rgb, ssr.a);
#endif
        color = mix(color, refl, fresnel * 0.9);
    }

    // ---- fog ---------------------------------------------------------------------------
    if (mat != MAT_HAND || sky) {
        if (isEyeInWater == 0) {
            color = applyAtmosphericFog(color, dirW, dist, sky);
        }
        color += volumetricLight(dirW, dist, dither, sky);
        color = applyFluidFog(color, dist);
    }

    /* RENDERTARGETS: 0 */
    gl_FragData[0] = vec4(color, 1.0);
}
#endif
