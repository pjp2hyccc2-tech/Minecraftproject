// =====================================================================================
//  ReisatsuShaders - common helpers: math, colour, hashes / noise, dithering, transforms
// =====================================================================================

float saturate(float x) { return clamp(x, 0.0, 1.0); }
vec2 saturate(vec2 x) { return clamp(x, vec2(0.0), vec2(1.0)); }
vec3 saturate(vec3 x) { return clamp(x, vec3(0.0), vec3(1.0)); }
float sq(float x) { return x * x; }
float luma(vec3 c) { return dot(c, vec3(0.2126, 0.7152, 0.0722)); }
vec3 toLinear(vec3 c) { return pow(max(c, vec3(0.0)), vec3(2.2)); }
vec3 toSrgb(vec3 c) { return pow(max(c, vec3(0.0)), vec3(1.0 / 2.2)); }
float maxOf(vec3 c) { return max(c.r, max(c.g, c.b)); }
float minOf(vec3 c) { return min(c.r, min(c.g, c.b)); }
// chroma 0..1 independent of brightness (alarm colours: tape, jacket, flare, bibs)
float chroma(vec3 c) { return saturate((maxOf(c) - minOf(c)) / max(maxOf(c), 1e-4)); }
// "how red" a colour is, 0..1, independent of brightness (the flare, blood)
float redness(vec3 c) { return saturate((c.r - max(c.g, c.b)) / max(c.r, 1e-4)); }

// ---- hashes ----------------------------------------------------------------------------
float hash11(float p) {
    p = fract(p * 0.1031);
    p *= p + 33.33;
    p *= p + p;
    return fract(p);
}
float hash12(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}
float hash13(vec3 p3) {
    p3 = fract(p3 * 0.1031);
    p3 += dot(p3, p3.zyx + 31.32);
    return fract((p3.x + p3.y) * p3.z);
}
vec3 hash33(vec3 p3) {
    p3 = fract(p3 * vec3(0.1031, 0.1030, 0.0973));
    p3 += dot(p3, p3.yxz + 33.33);
    return fract((p3.xxy + p3.yxx) * p3.zyx);
}
vec2 hash22(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * vec3(0.1031, 0.1030, 0.0973));
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.xx + p3.yz) * p3.zy);
}

// ---- value noise (ALU) ---------------------------------------------------------------
float vnoise1(float x) {
    float i = floor(x);
    float f = fract(x);
    f = f * f * (3.0 - 2.0 * f);
    return mix(hash11(i), hash11(i + 1.0), f);
}
float vnoise2(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    float a = hash12(i);
    float b = hash12(i + vec2(1.0, 0.0));
    float c = hash12(i + vec2(0.0, 1.0));
    float d = hash12(i + vec2(1.0, 1.0));
    return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
}
float fbm2(vec2 p) {
    float v = 0.0;
    float a = 0.5;
    for (int i = 0; i < 4; i++) {
        v += a * vnoise2(p);
        p = p * 2.07 + vec2(13.1, 7.7);
        a *= 0.5;
    }
    return v;
}

// ---- 3D value noise from the (linear filtered, repeating) noise texture -----------------
// Two z slices of the red channel, offset by (37, 17) texels per unit z, smooth weights
// folded into the bilinear lookup. Works with the loader's random noise texture.
float tnoise3(vec3 p) {
    vec3 i = floor(p);
    vec3 f = fract(p);
    vec3 u = f * f * (3.0 - 2.0 * f);
    vec2 uv = (i.xy + u.xy + i.z * vec2(37.0, 17.0) + 0.5) / float(noiseTextureResolution);
    float a = textureLod(noisetex, uv, 0.0).r;
    float b = textureLod(noisetex, uv + vec2(37.0, 17.0) / float(noiseTextureResolution), 0.0).r;
    return mix(a, b, u.z);
}

// ---- dithering -------------------------------------------------------------------------
float bayer2(vec2 a) { a = floor(a); return fract(a.x * 0.5 + a.y * a.y * 0.75); }
float bayer4(vec2 a) { return bayer2(0.5 * a) * 0.25 + bayer2(a); }
float bayer8(vec2 a) { return bayer4(0.5 * a) * 0.25 + bayer2(a); }
// interleaved gradient noise (static: no temporal crawl without TAA)
float ign(vec2 px) {
    return fract(52.9829189 * fract(dot(px, vec2(0.06711056, 0.00583715))));
}

// ---- normals ---------------------------------------------------------------------------
vec2 signNotZero(vec2 v) { return vec2(v.x >= 0.0 ? 1.0 : -1.0, v.y >= 0.0 ? 1.0 : -1.0); }
vec2 octEncode(vec3 n) {
    n /= max(abs(n.x) + abs(n.y) + abs(n.z), 1e-5);
    vec2 e = n.z >= 0.0 ? n.xy : (1.0 - abs(n.yx)) * signNotZero(n.xy);
    return e * 0.5 + 0.5;
}
vec3 octDecode(vec2 e) {
    e = e * 2.0 - 1.0;
    vec3 n = vec3(e, 1.0 - abs(e.x) - abs(e.y));
    if (n.z < 0.0) n.xy = (1.0 - abs(n.yx)) * signNotZero(n.xy);
    return normalize(n);
}

// ---- space transforms ------------------------------------------------------------------
vec3 screenToView(vec2 uv, float depth) {
    vec4 clip = vec4(vec3(uv, depth) * 2.0 - 1.0, 1.0);
    vec4 v = gbufferProjectionInverse * clip;
    return v.xyz / (abs(v.w) > 1e-6 ? v.w : 1e-6);
}
vec3 viewToScreen(vec3 viewPos) {
    vec4 clip = gbufferProjection * vec4(viewPos, 1.0);
    return (clip.xyz / max(abs(clip.w), 1e-6) * sign(clip.w)) * 0.5 + 0.5;
}
vec3 viewToPlayer(vec3 viewPos) { return mat3(gbufferModelViewInverse) * viewPos + gbufferModelViewInverse[3].xyz; }
vec3 playerToView(vec3 playerPos) { return mat3(gbufferModelView) * playerPos + gbufferModelView[3].xyz; }
vec3 viewDirToWorld(vec3 v) {
    vec3 w = mat3(gbufferModelViewInverse) * v;
    float l = length(w);
    return l > 1e-6 ? w / l : vec3(0.0, 1.0, 0.0);
}

// the hand is drawn with its depth squeezed toward the near plane (MC_HAND_DEPTH)
bool isHandDepth(float depth) { return depth < 0.56; }

// vanilla lightmap coordinate (1/32 .. 31/32) -> 0..1
vec2 normalizeLightmap(vec2 lm) {
    return saturate((lm - 1.0 / 32.0) * (16.0 / 15.0));
}

// Henyey-Greenstein phase function (normalised so the isotropic value is 1/(4pi))
float phaseHG(float cosTheta, float g) {
    float g2 = g * g;
    float d = max(1.0 + g2 - 2.0 * g * cosTheta, 1e-4);
    return (1.0 - g2) / (4.0 * PI * d * sqrt(d));
}
