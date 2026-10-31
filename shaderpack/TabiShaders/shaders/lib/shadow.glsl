// ============================================================== shadow map helpers
vec3 distortShadow(vec3 p) {
    float f = length(p.xy) * 0.88 + 0.12;
    return vec3(p.xy / f, p.z * 0.25);
}

#ifdef SHADOW_SAMPLING
uniform sampler2DShadow shadowtex0;

// soft PCF with a rotated Vogel disk (rotation from interleaved gradient noise)
float shadowSample(vec3 shadowClip, float NdotL) {
    vec3 d = distortShadow(shadowClip);
    vec3 sc = d * 0.5 + 0.5;
    if (sc.x <= 0.0 || sc.x >= 1.0 || sc.y <= 0.0 || sc.y >= 1.0 || sc.z >= 1.0) return 1.0;
    float distortF = length(shadowClip.xy) * 0.88 + 0.12;
    float texel = 1.0 / float(shadowMapResolution);
    float bias = (0.00012 + 0.0004 * (1.0 - clamp(NdotL, 0.0, 1.0))) * distortF * distortF * 4.0;
    float radius = texel * 1.6 * SHADOW_SOFTNESS / max(distortF, 0.2) * 0.35 + texel * 0.5;
    float rot = ign(gl_FragCoord.xy) * 2.0 * PI;
    float s = 0.0;
    for (int i = 0; i < 8; i++) {
        float r = sqrt((float(i) + 0.5) / 8.0);
        float a = float(i) * 2.39996 + rot;
        vec2 o = vec2(cos(a), sin(a)) * r * radius;
        s += texture(shadowtex0, vec3(sc.xy + o, sc.z - bias));
    }
    return s / 8.0;
}
#endif
