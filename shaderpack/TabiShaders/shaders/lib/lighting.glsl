// ============================================================== forward lighting for all lit gbuffers programs
// material ids (block.properties): 10 emissive, 11 partly emissive (bright pixels), 20 plants, 21 leaves, 22 hanging cloth/chimes,
// 30 water, 31 glass
float emissiveFor(int mat, vec3 albedoLin, vec2 lm) {
    if (mat == 10) return 1.0;
    if (mat == 11) return smoothstep(0.30, 0.75, max(albedoLin.r, max(albedoLin.g, albedoLin.b)));
    return 0.0;
}

vec3 shade(vec3 albedo, vec3 nV, vec2 lm, vec3 shadowClip, int mat, float emissive) {
    vec3 sd = sunDirW();
    float h = sd.y;
    vec3 Lv = normalize(shadowLightPosition);
    float NdotL = dot(nV, Lv);
    bool foliage = mat >= 20 && mat <= 22;
    float diff = foliage ? 0.55 + 0.45 * abs(NdotL) : max(NdotL, 0.0);
    float sh = 1.0;
#ifdef SHADOW_SAMPLING
    sh = diff > 0.0005 ? shadowSample(shadowClip, foliage ? 1.0 : NdotL) : 0.0;
#endif
    sh *= smoothstep(0.03, 0.30, lm.y);                 // no sunlight deep inside caves and houses
    vec3 direct = sunLight(h) * diff * sh;
    float up = dot(nV, normalize(upPosition)) * 0.5 + 0.5;
    vec3 amb = ambientSky(h) * (lm.y * lm.y) * (0.55 + 0.45 * up);
    float bl = lm.x;
    vec3 blk = LAMP * (pow(bl, 3.4) * 3.2 + bl * 0.06);  // warm pools of lamplight
    vec3 mn = vec3(0.010, 0.012, 0.018) + vec3(nightVision * 0.35);
    vec3 c = albedo * (direct + amb + blk + mn);
    c += albedo * emissive * 4.0 * EMISSIVE_STRENGTH;
    return c;
}
