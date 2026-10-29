// =====================================================================================
//  HuntCraft Shaders - deferred lighting of everything opaque + procedural sky + SSAO
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

#ifdef SSAO
float computeSSAO(vec3 viewPos, vec3 Nv, float dither) {
    float radius = 0.65 + length(viewPos) * 0.01;
    vec3 T = normalize(cross(Nv, abs(Nv.y) < 0.98 ? vec3(0.0, 1.0, 0.0) : vec3(1.0, 0.0, 0.0)));
    vec3 B = cross(Nv, T);
    float angle = dither * TAU;
    float occ = 0.0;
    float count = 0.0;
    for (int i = 0; i < 8; i++) {
        float a = angle + float(i) * 2.39996;
        float r = (float(i) + 0.5) / 8.0;
        float sr = sqrt(r);
        vec3 dir = T * (cos(a) * sr) + B * (sin(a) * sr) + Nv * sqrt(1.0 - r);
        vec3 s = viewPos + dir * radius * (0.25 + 0.75 * r);
        vec3 sp = viewToScreen(s);
        if (sp.x < 0.0 || sp.x > 1.0 || sp.y < 0.0 || sp.y > 1.0) continue;
        float sd = texture(depthtex2, sp.xy).r;
        vec3 sv = screenToView(sp.xy, sd);
        float dz = sv.z - s.z;
        float rangeCheck = smoothstep(0.0, 1.0, radius / max(abs(viewPos.z - sv.z), 1e-3));
        occ += (dz > 0.04 ? 1.0 : 0.0) * rangeCheck;
        count += 1.0;
    }
    if (count < 1.0) return 1.0;
    return saturate(1.0 - occ / count * 0.85 * SSAO_STRENGTH);
}
#endif

void main() {
    float depth = texture(depthtex0, texcoord).r;
    vec4 raw = texture(colortex0, texcoord);

    if (depth >= 1.0) {
        vec3 viewDir = normalize(screenToView(texcoord, 1.0));
        vec3 dirW = viewDirToWorld(viewDir);
        vec3 sky = getSky(dirW);
        sky += raw.rgb * 1.5;
#ifdef NO_SUN
        sky = skyGradient(dirW) + raw.rgb;
#endif
        /* RENDERTARGETS: 0 */
        gl_FragData[0] = vec4(sky, 1.0);
        return;
    }

    vec4 data1 = texture(colortex1, texcoord);
    vec4 data2 = texture(colortex2, texcoord);
    vec3 N = decodeNormal(data1.xy);
    vec2 lm = data1.zw;
    int mat = unpackMat(data2.r);
    float emissive = data2.g;
    float sss = data2.b;
    float ao = data2.a;

    vec3 viewPos = screenToView(texcoord, depth);
    vec3 playerPos = viewToPlayer(viewPos);
    float dither = bayer64(gl_FragCoord.xy);

    float ssao = 1.0;
#ifdef SSAO
    if (mat != MAT_HAND && mat != MAT_PARTICLE && mat != MAT_GLOW) {
        vec3 Nv = normalize(mat3(gbufferModelView) * N);
        ssao = computeSSAO(viewPos, Nv, dither);
    }
#endif

    vec3 albedo = srgbToLinear(raw.rgb);
    bool receiveShadow = (mat != MAT_HAND && mat != MAT_PARTICLE);
    vec3 color = shadeSurface(albedo, N, playerPos, lm, mat, emissive, sss, ao, ssao, dither, receiveShadow);

    /* RENDERTARGETS: 0 */
    gl_FragData[0] = vec4(color, 1.0);
}
#endif
